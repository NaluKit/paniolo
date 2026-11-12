/*
 * Copyright © 2026 Frank Hossfeld
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.nalukit.paniolo.validation;

import io.github.nalukit.paniolo.xml.util.XmlDocuments;
import net.sf.saxon.Configuration;
import net.sf.saxon.TransformerFactoryImpl;
import net.sf.saxon.lib.Feature;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.trans.XPathException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.ls.DOMImplementationLS;
import org.xml.sax.Attributes;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.XMLConstants;
import javax.xml.transform.Source;
import javax.xml.transform.Templates;
import javax.xml.transform.TransformerException;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXResult;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reusable, thread-safe XSD and Schematron validator. Compiles artifacts once per instance.
 * Malformed and invalid invoices produce findings; broken rule configurations throw ValidationException.
 * Compiled Schematron XSLT is supported; raw .sch sources must be compiled by their publisher/toolchain.
 * Created with {@link #InvoiceValidator(ValidationProfile, boolean)} and {@code true}, every result
 * also carries the {@link ValidationStatistics} of the Schematron stylesheets.
 */
public final class InvoiceValidator {
  private static final System.Logger       LOGGER = System.getLogger(InvoiceValidator.class.getName());
  private static final String              SVRL   = "http://purl.oclc.org/dsdl/svrl";
  private final        ValidationProfile   profile;
  private final        Schema              schema;
  private final        List<Templates>     rules;
  private final        List<String>        ruleNames;
  private final        boolean             ruleStatistics;
  private final        LocalResources      resources;
  private final        Map<Path, NodeInfo> documents;
  private final        Configuration       configuration;

  /**
   * Loads and compiles the schema and stylesheets of a profile. The validator is reusable and
   * thread-safe; create it once per profile.
   *
   * @param profile the validation profile
   * @throws ValidationException if a schema or stylesheet cannot be loaded or compiled
   */
  public InvoiceValidator(ValidationProfile profile) {
    this(profile,
         false);
  }

  /**
   * Loads and compiles the schema and stylesheets of a profile; with {@code ruleStatistics} every
   * result carries {@link ValidationStatistics} with the active patterns, fired rules, failed
   * asserts, and successful reports of each Schematron stylesheet.
   *
   * <p>Statistics cost time: the stylesheets then report every matched rule context, which the
   * validator otherwise removes before compiling. Use them for audits and diagnosis, not for the
   * regular processing of incoming documents.
   *
   * @param profile        the validation profile
   * @param ruleStatistics whether results carry rule execution statistics
   * @throws ValidationException if a schema or stylesheet cannot be loaded or compiled
   */
  public InvoiceValidator(ValidationProfile profile,
                          boolean ruleStatistics) {
    this.profile        = java.util.Objects.requireNonNull(profile,
                                                           "profile");
    this.ruleStatistics = ruleStatistics;
    long start = System.nanoTime();
    try {
      resources = new LocalResources(profile.resourceRoot());
      var schemaFactory = SchemaFactory.newDefaultInstance();
      schemaFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING,
                               true);
      schemaFactory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD,
                                "");
      schemaFactory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA,
                                "");
      schemaFactory.setResourceResolver((type, ns, publicId, systemId, base) -> {
        try {
          var path = resources.resolve(systemId,
                                       base);
          var implementation = (DOMImplementationLS) XmlDocuments.create()
                                                                 .getImplementation()
                                                                 .getFeature("LS",
                                                                             "3.0");
          var input = implementation.createLSInput();
          input.setSystemId(path.toUri()
                                .toString());
          input.setBaseURI(path.toUri()
                               .toString());
          input.setStringData(java.nio.file.Files.readString(path));
          return input;
        } catch (IOException e) {
          throw new ValidationException("Cannot resolve schema import",
                                        e);
        }
      });
      schema = profile.schema() == null ?
               null :
               schemaFactory.newSchema(resources.source(profile.schema()));
      var factory = new TransformerFactoryImpl();
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING,
                         true);
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD,
                           "");
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET,
                           "");
      factory.getConfiguration()
             .setConfigurationProperty(Feature.ALLOW_EXTERNAL_FUNCTIONS,
                                       false);
      factory.getConfiguration()
             .setUnparsedTextURIResolver((uri, encoding, config, ignored) -> {
               throw new XPathException("External text access is disabled");
             });
      factory.getConfiguration()
             .setCollectionFinder((context, uri) -> {
               throw new XPathException("External collection access is disabled");
             });
      factory.setURIResolver((href, base) -> {
        try {
          return resources.source(resources.resolve(href,
                                                    base));
        } catch (Exception e) {
          throw new javax.xml.transform.TransformerException("Cannot resolve stylesheet import",
                                                             e);
        }
      });
      var compiled = new ArrayList<Templates>();
      var names    = new ArrayList<String>();
      for (var stylesheet : profile.schematronStylesheets()) {
        var     source = resources.source(stylesheet);
        Element root   = ((Document) ((DOMSource) source).getNode()).getDocumentElement();
        if (!"http://www.w3.org/1999/XSL/Transform".equals(root.getNamespaceURI())) {
          throw new IllegalArgumentException("Expected compiled Schematron XSLT: " + stylesheet);
        }
        streamline(root,
                   ruleStatistics);
        compiled.add(factory.newTemplates(source));
        names.add(stylesheet.getFileName()
                            .toString());
      }
      rules     = List.copyOf(compiled);
      ruleNames = List.copyOf(names);
      // Lookup documents are parsed once into immutable Saxon trees, which threads can share.
      var lookups = new HashMap<Path, NodeInfo>();
      for (var document : profile.documents()) {
        Path safe = resources.resolve(document.toUri()
                                              .toString(),
                                      null);
        lookups.put(safe,
                    factory.getConfiguration()
                           .buildDocumentTree(resources.source(safe))
                           .getRootNode());
      }
      documents     = Map.copyOf(lookups);
      configuration = factory.getConfiguration();
      LOGGER.log(System.Logger.Level.DEBUG,
                 "Compiled validation profile {0} ({1} Schematron stylesheets) in {2} ms",
                 profile.name(),
                 rules.size(),
                 (System.nanoTime() - start) / 1_000_000);
    } catch (Exception e) {
      throw new ValidationException("Cannot compile validation profile: " + profile.name(),
                                    e);
    }
  }

  /**
   * Drops SVRL output that is never read, before compiling: the namespace declarations copied
   * into every report element, and, unless rule statistics count it, svrl:fired-rule, which is
   * written for every matched rule context (thousands per large invoice). Asserts, reports and
   * their texts are unchanged.
   */
  private static void streamline(Element stylesheet,
                                 boolean keepFiredRules) {
    stylesheet.setAttributeNS(null,
                              "exclude-result-prefixes",
                              "#all");
    if (keepFiredRules) {
      return;
    }
    var fired = stylesheet.getElementsByTagNameNS(SVRL,
                                                  "fired-rule");
    // The node list is live, so remove from the end.
    for (int i = fired.getLength() - 1; i >= 0; i--) {
      var element = fired.item(i);
      element.getParentNode()
             .removeChild(element);
    }
  }

  private static String location(SAXParseException e) {
    return "line " + e.getLineNumber() + ", column " + e.getColumnNumber();
  }

  /**
   * Validates XML text; the encoding named in the XML declaration is ignored. XML that is not
   * well-formed is reported as fatal finding.
   *
   * @param xml the XML document
   * @return the findings with the measured duration
   * @throws ValidationException if the validation cannot be executed
   */
  public ValidationResult validate(String xml) {
    return parseAndValidate(() -> XmlDocuments.parse(new StringReader(xml)));
  }

  /**
   * Validates encoded XML. XML that is not well-formed is reported as fatal finding.
   *
   * @param xml the XML document
   * @return the findings with the measured duration
   * @throws ValidationException if the validation cannot be executed
   */
  public ValidationResult validate(byte[] xml) {
    return validate(new ByteArrayInputStream(xml));
  }

  /**
   * Validates XML from a stream. XML that is not well-formed is reported as fatal finding. The
   * caller retains ownership of the input stream.
   *
   * @param input the XML document; not closed
   * @return the findings with the measured duration
   * @throws ValidationException if the stream cannot be read or the validation cannot be executed
   */
  public ValidationResult validate(InputStream input) {
    return parseAndValidate(() -> XmlDocuments.parse(input));
  }

  private ValidationResult parseAndValidate(InvoiceSource source) {
    long start = System.nanoTime();
    return check(source).withDuration(Duration.ofNanos(System.nanoTime() - start));
  }

  private ValidationResult check(InvoiceSource source) {
    var            messages = new ArrayList<ValidationMessage>();
    final Document document;
    try {
      document = source.read();
    } catch (SAXException e) {
      String location = e instanceof SAXParseException p ?
                        location(p) :
                        "";
      messages.add(new ValidationMessage("XML-PARSE",
                                         Severity.FATAL,
                                         e.getMessage(),
                                         location,
                                         ValidationMessage.Stage.XML));
      return result(messages,
                    List.of());
    } catch (IOException e) {
      throw new ValidationException("Cannot read invoice XML",
                                    e);
    }
    if (schema != null) {
      var validator = schema.newValidator();
      validator.setErrorHandler(new ErrorHandler() {
        @Override
        public void warning(SAXParseException e) {
          add(e,
              Severity.WARNING);
        }

        @Override
        public void error(SAXParseException e) {
          add(e,
              Severity.ERROR);
        }

        @Override
        public void fatalError(SAXParseException e) {
          add(e,
              Severity.FATAL);
        }

        private void add(SAXParseException e,
                         Severity severity) {
          messages.add(new ValidationMessage("XSD",
                                             severity,
                                             e.getMessage(),
                                             location(e),
                                             ValidationMessage.Stage.XSD));
        }
      });
      try {
        validator.validate(new DOMSource(document));
      } catch (SAXException e) {
        if (messages.stream()
                    .noneMatch(m -> m.severity() == Severity.ERROR || m.severity() == Severity.FATAL)) {
          messages.add(new ValidationMessage("XSD",
                                             Severity.FATAL,
                                             e.getMessage(),
                                             "",
                                             ValidationMessage.Stage.XSD));
        }
      } catch (IOException e) {
        throw new ValidationException("Cannot validate XML schema",
                                      e);
      }
    }
    var statistics = new ArrayList<SchematronStatistics>();
    if (!result(messages,
                statistics).isValid() || rules.isEmpty()) {
      return result(messages,
                    statistics);
    }
    // One Saxon tree for all stylesheets; Saxon evaluates DOM input through a slow wrapper.
    final NodeInfo invoice;
    try {
      invoice = configuration.buildDocumentTree(new DOMSource(document))
                             .getRootNode();
    } catch (XPathException e) {
      throw new ValidationException("Cannot prepare invoice XML for Schematron",
                                    e);
    }
    for (int index = 0; index < rules.size(); index++) {
      Templates rule = rules.get(index);
      try {
        var transformer = rule.newTransformer();
        transformer.setURIResolver(this::lookupDocument);
        var report = new SvrlHandler();
        transformer.transform(invoice,
                              new SAXResult(report));
        if (!report.isSvrl()) {
          throw new IllegalStateException("Schematron stylesheet did not return an SVRL report");
        }
        statistics.add(new SchematronStatistics(ruleNames.get(index),
                                                report.activePatterns,
                                                report.firedRules,
                                                report.failedAsserts,
                                                report.successfulReports));
        for (var finding : report.findings()) {
          Severity flagged = finding.report() &&
                             finding.flag()
                                    .isEmpty() ?
                             profile.severityOverrides()
                                    .getOrDefault(ValidationProfile.UNFLAGGED_REPORTS,
                                                  Severity.fromFlag(finding.flag())) :
                             Severity.fromFlag(finding.flag());
          Severity severity = profile.severityOverrides()
                                     .getOrDefault(finding.id(),
                                                   flagged);
          messages.add(new ValidationMessage(finding.id(),
                                             severity,
                                             finding.text(),
                                             finding.location(),
                                             ValidationMessage.Stage.SCHEMATRON));
        }
      } catch (Exception e) {
        throw new ValidationException("Schematron execution failed for " + profile.name(),
                                      e);
      }
    }
    return result(messages,
                  statistics);
  }

  /**
   * Serves only the lookup documents declared in the profile; every other document is refused.
   */
  private Source lookupDocument(String href,
                                String base)
      throws TransformerException {
    try {
      var document = documents.get(resources.resolve(href,
                                                     base));
      if (document != null) {
        return document;
      }
    } catch (IOException e) {
      throw new TransformerException("External document access is disabled",
                                     e);
    }
    throw new TransformerException("External document access is disabled: " + href);
  }

  private ValidationResult result(List<ValidationMessage> messages,
                                  List<SchematronStatistics> statistics) {
    var result = new ValidationResult(profile.name(),
                                      messages,
                                      null,
                                      ruleStatistics ?
                                      new ValidationStatistics(statistics,
                                                               List.of()) :
                                      null);
    // Rule IDs and counts only; messages may quote invoice content.
    LOGGER.log(System.Logger.Level.TRACE,
               () -> "Validated against " +
                     profile.name() +
                     ": valid=" +
                     result.isValid() +
                     ", findings=" +
                     messages.size() +
                     " " +
                     messages.stream()
                             .map(ValidationMessage::ruleId)
                             .distinct()
                             .toList());
    return result;
  }

  @FunctionalInterface
  private interface InvoiceSource {
    Document read()
        throws IOException, SAXException;
  }



  private record Finding(String id, String flag, String text, String location, boolean report) {
  }



  /**
   * Collects failed asserts and successful reports while the SVRL report is written, without
   * building a tree. The text of a finding is its first svrl:text, trimmed.
   */
  private static final class SvrlHandler
      extends DefaultHandler {
    private final List<Finding> findings = new ArrayList<>();
    private       int           activePatterns;
    private       int           firedRules;
    private       int           failedAsserts;
    private       int           successfulReports;
    private       Boolean       svrl;
    private       Attributes    finding;
    private       StringBuilder text;
    private       boolean       hasText;
    private       int           textDepth;

    private static String value(Attributes attributes,
                                String name) {
      String value = attributes.getValue(name);
      return value == null ?
             "" :
             value;
    }

    boolean isSvrl() {
      return Boolean.TRUE.equals(svrl);
    }

    List<Finding> findings() {
      return findings;
    }

    @Override
    public void startElement(String uri,
                             String localName,
                             String qName,
                             Attributes attributes) {
      if (svrl == null) {
        svrl = SVRL.equals(uri) && "schematron-output".equals(localName);
        return;
      }
      if (textDepth > 0) {
        textDepth++;
        return;
      }
      if (!SVRL.equals(uri)) {
        return;
      }
      switch (localName) {
        case "active-pattern" -> activePatterns++;
        case "fired-rule" -> firedRules++;
        case "failed-assert" -> failedAsserts++;
        case "successful-report" -> successfulReports++;
        default -> {
        }
      }
      if (finding == null && ("failed-assert".equals(localName) || "successful-report".equals(localName))) {
        finding = new org.xml.sax.helpers.AttributesImpl(attributes);
        text    = new StringBuilder();
        hasText = false;
      } else if (finding != null && !hasText && "text".equals(localName)) {
        hasText   = true;
        textDepth = 1;
      }
    }

    @Override
    public void characters(char[] ch,
                           int start,
                           int length) {
      if (textDepth > 0) {
        text.append(ch,
                    start,
                    length);
      }
    }

    @Override
    public void endElement(String uri,
                           String localName,
                           String qName) {
      if (textDepth > 0) {
        textDepth--;
        return;
      }
      if (finding != null && SVRL.equals(uri) && ("failed-assert".equals(localName) || "successful-report".equals(localName))) {
        String flag = finding.getIndex("flag") >= 0 ?
                      finding.getValue("flag") :
                      value(finding,
                            "role");
        findings.add(new Finding(value(finding,
                                       "id"),
                                 flag,
                                 text.toString()
                                     .trim(),
                                 value(finding,
                                       "location"),
                                 "successful-report".equals(localName)));
        finding = null;
      }
    }
  }
}

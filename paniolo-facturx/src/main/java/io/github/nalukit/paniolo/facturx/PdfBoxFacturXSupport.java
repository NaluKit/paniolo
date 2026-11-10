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
package io.github.nalukit.paniolo.facturx;

import io.github.nalukit.paniolo.config.AfRelationship;
import io.github.nalukit.paniolo.config.FacturXProfile;
import io.github.nalukit.paniolo.config.PdfAttachment;
import io.github.nalukit.paniolo.config.PdfProducer;
import io.github.nalukit.paniolo.model.order.OrderTypeCode;
import io.github.nalukit.paniolo.model.order.OrderXProfile;
import io.github.nalukit.paniolo.spi.FacturXSupport;
import io.github.nalukit.paniolo.xml.util.XmlDocuments;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentCatalog;
import org.apache.pdfbox.pdmodel.PDDocumentNameDictionary;
import org.apache.pdfbox.pdmodel.PDEmbeddedFilesNameTreeNode;
import org.apache.pdfbox.pdmodel.common.PDMetadata;
import org.apache.pdfbox.pdmodel.common.filespecification.PDComplexFileSpecification;
import org.apache.pdfbox.pdmodel.common.filespecification.PDEmbeddedFile;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.pdmodel.graphics.color.PDOutputIntent;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import java.awt.color.ColorSpace;
import java.awt.color.ICC_Profile;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Factur-X / ZUGFeRD implementation based on Apache PDFBox, registered for
 * {@link java.util.ServiceLoader}. Use it through {@code Paniolo#toFacturX}, {@code Paniolo#read}
 * and {@code Paniolo#validate}. Stateless and thread-safe.
 *
 * <p>Embedded files are stored Flate-compressed and the PDF is saved with object streams, which
 * PDF/A-3 allows; the streams of the given PDF are taken as they are.
 */
public final class PdfBoxFacturXSupport
    implements FacturXSupport {
  private static final System.Logger LOGGER        = System.getLogger(PdfBoxFacturXSupport.class.getName());
  private static final String        FX            = "urn:factur-x:pdfa:CrossIndustryDocument:invoice:1p0#";
  /**
   * XMP namespace of ZUGFeRD 1.0, whose PDFs embed {@code ZUGFeRD-invoice.xml}.
   */
  private static final String        ZF1           = "urn:ferd:pdfa:CrossIndustryDocument:invoice:1p0#";
  /**
   * XMP namespace of Order-X documents and the name of the embedded order.
   */
  private static final String        OX            = "urn:factur-x:pdfa:CrossIndustryDocument:1p0#";
  private static final String        ORDER_FILE    = "order-x.xml";
  /**
   * XMP namespace of despatch advices and the name of the embedded CIDA XML, as the Mustang project
   * writes them after the AWV specification of the digital delivery note.
   */
  private static final String        DX            = "urn:factur-x:pdfa:CrossIndustryDocument:despatchadvice:1p0#";
  private static final String        DESPATCH_FILE = "cida.xml";
  private static final String        PDFAID        = "http://www.aiim.org/pdfa/ns/id/";
  private static final COSName       AF            = COSName.getPDFName("AF");
  private static final COSName       RELATIONSHIP  = COSName.getPDFName("AFRelationship");
  /**
   * Embedded invoice XML larger than this is rejected when reading.
   */
  private static final int           MAX_XML       = 20 * 1024 * 1024;
  /**
   * Embedded attachments larger than this are rejected when reading.
   */
  private static final int           MAX_FILE      = 100 * 1024 * 1024;
  /**
   * File names of embedded invoices, including ZUGFeRD 1.0.
   */
  private static final List<String>  INVOICE_NAMES = List.of("factur-x.xml",
                                                             "xrechnung.xml",
                                                             "zugferd-invoice.xml",
                                                             "ZUGFeRD-invoice.xml");

  /**
   * Creates the implementation; {@link java.util.ServiceLoader} calls it.
   */
  public PdfBoxFacturXSupport() {
  }

  private static PDDocument load(byte[] pdf)
      throws IOException {
    try {
      return Loader.loadPDF(pdf);
    } catch (InvalidPasswordException e) {
      throw new IllegalArgumentException("Password protected PDF documents are not supported",
                                         e);
    } catch (IOException e) {
      throw new IllegalArgumentException("Not a readable PDF document",
                                         e);
    }
  }

  /**
   * Returns the PDF/A conformance letter (A, B or U) of the input; rejects documents that do not
   * declare PDF/A, because the visual part must already be archivable.
   */
  private static String pdfaConformance(PDDocumentCatalog catalog) {
    Document xmp = parseXmp(catalog.getMetadata());
    String part = xmp == null ?
                  null :
                  value(xmp,
                        PDFAID,
                        "part");
    if (part == null || !List.of("1",
                                 "2",
                                 "3")
                             .contains(part)) {
      throw new IllegalArgumentException("The PDF is not a PDF/A document (no pdfaid:part 1-3 in its XMP metadata); " + "convert it to PDF/A before embedding the invoice");
    }
    String conformance = value(xmp,
                               PDFAID,
                               "conformance");
    return conformance == null ?
           "B" :
           conformance.toUpperCase(java.util.Locale.ROOT);
  }

  /**
   * Embeds a file as PDF/A-3 associated file: in the name tree and the catalog's {@code /AF} array,
   * with MIME type, size, dates, and relationship.
   */
  private static void embedFile(PDDocument document,
                                PDDocumentCatalog catalog,
                                String name,
                                byte[] content,
                                String mimeType,
                                String relationship,
                                String description,
                                Calendar now)
      throws IOException {
    var embedded = new PDEmbeddedFile(document,
                                      new ByteArrayInputStream(content),
                                      COSName.FLATE_DECODE);
    embedded.setSubtype(mimeType);
    embedded.setSize(content.length);
    embedded.setCreationDate(now);
    embedded.setModDate(now);

    var file = new PDComplexFileSpecification();
    file.setFile(name);
    file.setFileUnicode(name);
    file.setFileDescription(description);
    file.setEmbeddedFile(embedded);
    file.setEmbeddedFileUnicode(embedded);
    file.getCOSObject()
        .setName(RELATIONSHIP,
                 relationship);

    PDDocumentNameDictionary names = catalog.getNames();
    if (names == null) {
      names = new PDDocumentNameDictionary(catalog);
    }
    // Rebuild a flat name tree that keeps all existing attachments.
    Map<String, PDComplexFileSpecification> files = new java.util.TreeMap<>(embeddedFiles(names.getEmbeddedFiles()));
    files.put(name,
              file);
    var tree = new PDEmbeddedFilesNameTreeNode();
    tree.setNames(files);
    names.setEmbeddedFiles(tree);
    catalog.setNames(names);

    COSArray associated = catalog.getCOSObject()
                                 .getCOSArray(AF);
    if (associated == null) {
      associated = new COSArray();
      catalog.getCOSObject()
             .setItem(AF,
                      associated);
    }
    associated.add(file);
  }

  /**
   * Removes embedded invoice files from the name tree and the catalog's {@code /AF} array.
   */
  private static void removeInvoiceFiles(PDDocumentCatalog catalog)
      throws IOException {
    var names = catalog.getNames();
    if (names != null && names.getEmbeddedFiles() != null) {
      Map<String, PDComplexFileSpecification> files = new java.util.TreeMap<>(embeddedFiles(names.getEmbeddedFiles()));
      files.keySet()
           .removeIf(INVOICE_NAMES::contains);
      var tree = new PDEmbeddedFilesNameTreeNode();
      tree.setNames(files);
      names.setEmbeddedFiles(tree);
      catalog.setNames(names);
    }
    COSArray associated = catalog.getCOSObject()
                                 .getCOSArray(AF);
    if (associated != null) {
      for (int i = associated.size() - 1; i >= 0; i--) {
        if (associated.getObject(i) instanceof COSDictionary dictionary && INVOICE_NAMES.contains(fileName(new PDComplexFileSpecification(dictionary)))) {
          associated.remove(i);
        }
      }
    }
  }

  private static void addSrgbOutputIntent(PDDocument document,
                                          PDDocumentCatalog catalog)
      throws IOException {
    byte[] icc = ICC_Profile.getInstance(ColorSpace.CS_sRGB)
                            .getData();
    var intent = new PDOutputIntent(document,
                                    new ByteArrayInputStream(icc));
    intent.setInfo("sRGB IEC61966-2.1");
    intent.setOutputCondition("sRGB IEC61966-2.1");
    intent.setOutputConditionIdentifier("sRGB IEC61966-2.1");
    intent.setRegistryName("http://www.color.org");
    catalog.addOutputIntent(intent);
  }

  /**
   * Embedded invoice files by name, from the associated files and the name tree.
   */
  private static Map<String, PDComplexFileSpecification> invoiceFiles(PDDocumentCatalog catalog)
      throws IOException {
    var                                     all      = allFiles(catalog);
    Map<String, PDComplexFileSpecification> invoices = new LinkedHashMap<>();
    for (String name : INVOICE_NAMES) {
      all.forEach((fileName, file) -> {
        if (name.equals(fileName)) {
          invoices.put(fileName,
                       file);
        }
      });
    }
    return invoices;
  }

  /**
   * All embedded files by name, from the associated files and the name tree.
   */
  private static Map<String, PDComplexFileSpecification> allFiles(PDDocumentCatalog catalog)
      throws IOException {
    Map<String, PDComplexFileSpecification> all = new LinkedHashMap<>();
    COSArray associated = catalog.getCOSObject()
                                 .getCOSArray(AF);
    if (associated != null) {
      for (int i = 0; i < associated.size(); i++) {
        // getObject dereferences indirect references, which /AF entries usually are.
        if (associated.getObject(i) instanceof COSDictionary dictionary) {
          var file = new PDComplexFileSpecification(dictionary);
          all.putIfAbsent(fileName(file),
                          file);
        }
      }
    }
    if (catalog.getNames() != null) {
      embeddedFiles(catalog.getNames()
                           .getEmbeddedFiles()).forEach(all::putIfAbsent);
    }
    return all;
  }

  private static Map<String, PDComplexFileSpecification> embeddedFiles(org.apache.pdfbox.pdmodel.common.PDNameTreeNode<PDComplexFileSpecification> node)
      throws IOException {
    Map<String, PDComplexFileSpecification> result = new LinkedHashMap<>();
    if (node == null) {
      return result;
    }
    if (node.getNames() != null) {
      result.putAll(node.getNames());
    }
    if (node.getKids() != null) {
      for (var kid : node.getKids()) {
        result.putAll(embeddedFiles(kid));
      }
    }
    return result;
  }

  private static String fileName(PDComplexFileSpecification file) {
    return file.getFileUnicode() != null ?
           file.getFileUnicode() :
           file.getFile();
  }

  private static byte[] read(PDComplexFileSpecification file)
      throws IOException {
    return read(file,
                MAX_XML);
  }

  private static byte[] read(PDComplexFileSpecification file,
                             int limit)
      throws IOException {
    PDEmbeddedFile embedded = embeddedFile(file);
    if (embedded == null) {
      throw new IllegalArgumentException("Embedded file " + fileName(file) + " has no content");
    }
    try (InputStream input = embedded.createInputStream()) {
      byte[] content = input.readNBytes(limit + 1);
      if (content.length > limit) {
        throw new IllegalArgumentException("Embedded file " + fileName(file) + " exceeds " + limit + " bytes");
      }
      return content;
    }
  }

  private static PDEmbeddedFile embeddedFile(PDComplexFileSpecification file) {
    return file.getEmbeddedFileUnicode() != null ?
           file.getEmbeddedFileUnicode() :
           file.getEmbeddedFile();
  }

  private static Document parseXmp(PDMetadata metadata) {
    if (metadata == null) {
      return null;
    }
    try (InputStream input = metadata.createInputStream()) {
      return XmlDocuments.parse(input);
    } catch (IOException | SAXException e) {
      LOGGER.log(System.Logger.Level.DEBUG,
                 "Cannot parse XMP metadata",
                 e);
      return null;
    }
  }

  private static String fxValue(Document xmp,
                                String property) {
    return value(xmp,
                 FX,
                 property);
  }

  /**
   * XMP property as element or as attribute of rdf:Description.
   */
  private static String value(Document xmp,
                              String namespace,
                              String property) {
    var elements = xmp.getElementsByTagNameNS(namespace,
                                              property);
    if (elements.getLength() > 0) {
      return elements.item(0)
                     .getTextContent()
                     .strip();
    }
    var descriptions = xmp.getElementsByTagNameNS("http://www.w3.org/1999/02/22-rdf-syntax-ns#",
                                                  "Description");
    for (int i = 0; i < descriptions.getLength(); i++) {
      String attribute = ((Element) descriptions.item(i)).getAttributeNS(namespace,
                                                                         property);
      if (!attribute.isBlank()) {
        return attribute.strip();
      }
    }
    return null;
  }

  /**
   * XMP packet for PDF/A-3 with the Factur-X extension schema. Document information is mirrored
   * from the info dictionary, as PDF/A requires both to match.
   */
  private static String xmp(org.apache.pdfbox.pdmodel.PDDocumentInformation info,
                            String conformance,
                            Hybrid hybrid) {
    var xml = new StringBuilder();
    xml.append("<?xpacket begin=\"﻿\" id=\"W5M0MpCehiHzreSzNTczkc9d\"?>\n")
       .append("<x:xmpmeta xmlns:x=\"adobe:ns:meta/\">\n")
       .append(" <rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\">\n")
       .append("  <rdf:Description rdf:about=\"\" xmlns:pdfaid=\"")
       .append(PDFAID)
       .append("\">\n")
       .append("   <pdfaid:part>3</pdfaid:part>\n")
       .append("   <pdfaid:conformance>")
       .append(conformance)
       .append("</pdfaid:conformance>\n")
       .append("  </rdf:Description>\n");

    xml.append("  <rdf:Description rdf:about=\"\" xmlns:dc=\"http://purl.org/dc/elements/1.1/\">\n")
       .append("   <dc:format>application/pdf</dc:format>\n");
    if (info.getTitle() != null) {
      xml.append("   <dc:title><rdf:Alt><rdf:li xml:lang=\"x-default\">")
         .append(escape(info.getTitle()))
         .append("</rdf:li></rdf:Alt></dc:title>\n");
    }
    if (info.getAuthor() != null) {
      xml.append("   <dc:creator><rdf:Seq><rdf:li>")
         .append(escape(info.getAuthor()))
         .append("</rdf:li></rdf:Seq></dc:creator>\n");
    }
    if (info.getSubject() != null) {
      xml.append("   <dc:description><rdf:Alt><rdf:li xml:lang=\"x-default\">")
         .append(escape(info.getSubject()))
         .append("</rdf:li></rdf:Alt></dc:description>\n");
    }
    xml.append("  </rdf:Description>\n");

    xml.append("  <rdf:Description rdf:about=\"\" xmlns:xmp=\"http://ns.adobe.com/xap/1.0/\">\n");
    optional(xml,
             "xmp:CreatorTool",
             info.getCreator());
    optional(xml,
             "xmp:CreateDate",
             date(info.getCreationDate()));
    optional(xml,
             "xmp:ModifyDate",
             date(info.getModificationDate()));
    optional(xml,
             "xmp:MetadataDate",
             date(info.getModificationDate()));
    xml.append("  </rdf:Description>\n");

    xml.append("  <rdf:Description rdf:about=\"\" xmlns:pdf=\"http://ns.adobe.com/pdf/1.3/\">\n");
    optional(xml,
             "pdf:Producer",
             info.getProducer());
    optional(xml,
             "pdf:Keywords",
             info.getKeywords());
    xml.append("  </rdf:Description>\n");

    xml.append("  <rdf:Description rdf:about=\"\" xmlns:fx=\"")
       .append(hybrid.namespace())
       .append("\">\n")
       .append("   <fx:DocumentType>")
       .append(hybrid.documentType())
       .append("</fx:DocumentType>\n")
       .append("   <fx:DocumentFileName>")
       .append(hybrid.fileName())
       .append("</fx:DocumentFileName>\n")
       .append("   <fx:Version>")
       .append(hybrid.version())
       .append("</fx:Version>\n")
       .append("   <fx:ConformanceLevel>")
       .append(hybrid.conformanceLevel())
       .append("</fx:ConformanceLevel>\n")
       .append("  </rdf:Description>\n");

    xml.append("  <rdf:Description rdf:about=\"\"")
       .append(" xmlns:pdfaExtension=\"http://www.aiim.org/pdfa/ns/extension/\"")
       .append(" xmlns:pdfaSchema=\"http://www.aiim.org/pdfa/ns/schema#\"")
       .append(" xmlns:pdfaProperty=\"http://www.aiim.org/pdfa/ns/property#\">\n")
       .append("   <pdfaExtension:schemas><rdf:Bag><rdf:li rdf:parseType=\"Resource\">\n")
       .append("    <pdfaSchema:schema>")
       .append(hybrid.schemaName())
       .append("</pdfaSchema:schema>\n")
       .append("    <pdfaSchema:namespaceURI>")
       .append(hybrid.namespace())
       .append("</pdfaSchema:namespaceURI>\n")
       .append("    <pdfaSchema:prefix>fx</pdfaSchema:prefix>\n")
       .append("    <pdfaSchema:property><rdf:Seq>\n");
    extensionProperty(xml,
                      "DocumentFileName",
                      "The name of the embedded XML document");
    extensionProperty(xml,
                      "DocumentType",
                      "The type of the hybrid document in capital letters, e.g. INVOICE or ORDER");
    extensionProperty(xml,
                      "Version",
                      "The actual version of the standard applying to the embedded XML document");
    extensionProperty(xml,
                      "ConformanceLevel",
                      "The conformance level of the embedded XML document");
    xml.append("    </rdf:Seq></pdfaSchema:property>\n")
       .append("   </rdf:li></rdf:Bag></pdfaExtension:schemas>\n")
       .append("  </rdf:Description>\n")
       .append(" </rdf:RDF>\n")
       .append("</x:xmpmeta>\n")
       .append("<?xpacket end=\"w\"?>");
    return xml.toString();
  }

  private static void extensionProperty(StringBuilder xml,
                                        String name,
                                        String description) {
    xml.append("     <rdf:li rdf:parseType=\"Resource\">")
       .append("<pdfaProperty:name>")
       .append(name)
       .append("</pdfaProperty:name>")
       .append("<pdfaProperty:valueType>Text</pdfaProperty:valueType>")
       .append("<pdfaProperty:category>external</pdfaProperty:category>")
       .append("<pdfaProperty:description>")
       .append(description)
       .append("</pdfaProperty:description></rdf:li>\n");
  }

  private static void optional(StringBuilder xml,
                               String element,
                               String value) {
    if (value != null) {
      xml.append("   <")
         .append(element)
         .append('>')
         .append(escape(value))
         .append("</")
         .append(element)
         .append(">\n");
    }
  }

  private static String date(Calendar calendar) {
    if (calendar == null) {
      return null;
    }
    ZoneId zone = calendar.getTimeZone()
                          .toZoneId();
    return ZonedDateTime.ofInstant(calendar.toInstant(),
                                   zone)
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX"));
  }

  private static String escape(String value) {
    var result = new StringBuilder(value.length());
    for (char c : value.toCharArray()) {
      switch (c) {
        case '&' -> result.append("&amp;");
        case '<' -> result.append("&lt;");
        case '>' -> result.append("&gt;");
        case '"' -> result.append("&quot;");
        default -> {
          if (c >= 0x20 || c == '\t' || c == '\n' || c == '\r') {
            result.append(c);
          }
        }
      }
    }
    return result.toString();
  }

  private static void checkFile(List<io.github.nalukit.paniolo.validation.ValidationMessage> findings,
                                PDDocumentCatalog catalog,
                                String name,
                                PDComplexFileSpecification file)
      throws IOException {
    boolean associated = false;
    COSArray array = catalog.getCOSObject()
                            .getCOSArray(AF);
    if (array != null) {
      for (int i = 0; i < array.size(); i++) {
        if (array.getObject(i) instanceof COSDictionary dictionary && dictionary.equals(file.getCOSObject())) {
          associated = true;
        }
      }
    }
    if (!associated) {
      finding(findings,
              "FX-NOT-ASSOCIATED",
              io.github.nalukit.paniolo.validation.Severity.ERROR,
              name + " must be referenced from the /AF array of the document catalog",
              "/Root/AF");
    }
    String relationship = file.getCOSObject()
                              .getNameAsString(RELATIONSHIP);
    if (!List.of("Alternative",
                 "Data",
                 "Source")
             .contains(relationship)) {
      finding(findings,
              "FX-AF-RELATIONSHIP",
              io.github.nalukit.paniolo.validation.Severity.ERROR,
              "AFRelationship of " + name + " must be Alternative, Data or Source, but is " + relationship,
              "/Root/AF/AFRelationship");
    }
    PDEmbeddedFile embedded = file.getEmbeddedFileUnicode() != null ?
                              file.getEmbeddedFileUnicode() :
                              file.getEmbeddedFile();
    if (embedded == null) {
      finding(findings,
              "FX-NO-CONTENT",
              io.github.nalukit.paniolo.validation.Severity.FATAL,
              name + " has no embedded file stream",
              "/Root/Names/EmbeddedFiles/EF");
      return;
    }
    if (!"text/xml".equals(embedded.getSubtype())) {
      finding(findings,
              "FX-MIME-TYPE",
              io.github.nalukit.paniolo.validation.Severity.ERROR,
              "The MIME type of " + name + " must be text/xml, but is " + embedded.getSubtype(),
              "/Root/Names/EmbeddedFiles/EF/Subtype");
    }
    if (embedded.getModDate() == null) {
      finding(findings,
              "FX-MOD-DATE",
              io.github.nalukit.paniolo.validation.Severity.WARNING,
              "The embedded file " + name + " should state its modification date (Params/ModDate)",
              "/Root/Names/EmbeddedFiles/EF/Params");
    }
  }

  private static void checkProfile(List<io.github.nalukit.paniolo.validation.ValidationMessage> findings,
                                   Document xmp,
                                   String level,
                                   String name,
                                   PDComplexFileSpecification file)
      throws IOException {
    boolean xrechnung = "XRECHNUNG".equals(level);
    String expected = xrechnung ?
                      "xrechnung.xml" :
                      "factur-x.xml";
    if (!expected.equals(name) && !"zugferd-invoice.xml".equals(name)) {
      finding(findings,
              "FX-FILE-NAME",
              io.github.nalukit.paniolo.validation.Severity.ERROR,
              "Profile " + level + " requires the embedded file name " + expected + ", but it is " + name,
              "/Root/Names/EmbeddedFiles");
    }
    String version = fxValue(xmp,
                             "Version");
    if (version == null) {
      finding(findings,
              "FX-VERSION",
              io.github.nalukit.paniolo.validation.Severity.ERROR,
              "fx:Version is missing",
              "XMP fx:Version");
    } else if (!xrechnung && !"1.0".equals(version)) {
      finding(findings,
              "FX-VERSION",
              io.github.nalukit.paniolo.validation.Severity.WARNING,
              "fx:Version of Factur-X profiles is 1.0, but is " + version,
              "XMP fx:Version");
    }
    // Germany requires Alternative, except Data for the profiles MINIMUM and BASIC WL.
    String relationship = file.getCOSObject()
                              .getNameAsString(RELATIONSHIP);
    String german = "MINIMUM".equals(level) || "BASIC WL".equals(level) ?
                    "Data" :
                    "Alternative";
    if (relationship != null && !german.equals(relationship)) {
      finding(findings,
              "FX-AF-RELATIONSHIP-DE",
              io.github.nalukit.paniolo.validation.Severity.WARNING,
              "For profile " + level + " Germany requires AFRelationship " + german + ", the document uses " + relationship,
              "/Root/AF/AFRelationship");
    }
    String guideline = guideline(read(file));
    // Also accepts the ZUGFeRD 2.0 identifiers and every XRechnung version.
    boolean matches = FacturXProfile.ofConformanceLevel(level) == FacturXProfile.ofSpecificationId(guideline);
    if (!matches) {
      finding(findings,
              "FX-GUIDELINE",
              io.github.nalukit.paniolo.validation.Severity.ERROR,
              "fx:ConformanceLevel " + level + " does not match the specification identifier (BT-24) " + guideline + " of the embedded invoice",
              "/rsm:CrossIndustryInvoice/rsm:ExchangedDocumentContext/ram:GuidelineSpecifiedDocumentContextParameter/ram:ID");
    }
  }

  private static boolean hasExtensionSchema(Document xmp) {
    return hasExtensionSchema(xmp,
                              FX);
  }

  private static boolean hasExtensionSchema(Document xmp,
                                            String namespace) {
    var uris = xmp.getElementsByTagNameNS("http://www.aiim.org/pdfa/ns/schema#",
                                          "namespaceURI");
    for (int i = 0; i < uris.getLength(); i++) {
      if (namespace.equals(uris.item(i)
                               .getTextContent()
                               .strip())) {
        return true;
      }
    }
    return false;
  }

  /**
   * The XMP metadata of a ZUGFeRD 1.0 PDF: its own namespace and the conformance levels BASIC,
   * COMFORT, and EXTENDED; the Factur-X profile checks do not apply.
   */
  private static void checkZugferd1Xmp(List<io.github.nalukit.paniolo.validation.ValidationMessage> findings,
                                       Document xmp,
                                       String name) {
    if (!"INVOICE".equals(value(xmp,
                                ZF1,
                                "DocumentType"))) {
      finding(findings,
              "FX-DOCUMENT-TYPE",
              io.github.nalukit.paniolo.validation.Severity.ERROR,
              "zf:DocumentType must be INVOICE, but is " + value(xmp,
                                                                  ZF1,
                                                                  "DocumentType"),
              "XMP zf:DocumentType");
    }
    String declared = value(xmp,
                            ZF1,
                            "DocumentFileName");
    if (!name.equals(declared)) {
      finding(findings,
              "FX-DOCUMENT-FILE-NAME",
              io.github.nalukit.paniolo.validation.Severity.ERROR,
              "zf:DocumentFileName must name the embedded file " + name + ", but is " + declared,
              "XMP zf:DocumentFileName");
    }
    String level = value(xmp,
                         ZF1,
                         "ConformanceLevel");
    if (level == null || !java.util.Set.of("BASIC",
                                           "COMFORT",
                                           "EXTENDED")
                                       .contains(level)) {
      finding(findings,
              "FX-CONFORMANCE-LEVEL",
              io.github.nalukit.paniolo.validation.Severity.ERROR,
              "zf:ConformanceLevel must be BASIC, COMFORT or EXTENDED, but is " + level,
              "XMP zf:ConformanceLevel");
    }
    if (xmp.getDocumentElement() == null || !hasExtensionSchema(xmp,
                                                                ZF1)) {
      finding(findings,
              "FX-EXTENSION-SCHEMA",
              io.github.nalukit.paniolo.validation.Severity.ERROR,
              "The XMP metadata must declare the ZUGFeRD 1.0 namespace " + ZF1 + " in a PDF/A extension schema",
              "XMP pdfaExtension:schemas");
    }
  }

  /**
   * BT-24 of an embedded CII invoice, or null.
   */
  private static String guideline(byte[] xml) {
    try {
      var document = XmlDocuments.parse(new ByteArrayInputStream(xml));
      var ids = document.getElementsByTagNameNS("urn:un:unece:uncefact:data:standard:ReusableAggregateBusinessInformationEntity:100",
                                                "GuidelineSpecifiedDocumentContextParameter");
      if (ids.getLength() == 0) {
        return null;
      }
      var id = ((Element) ids.item(0)).getElementsByTagNameNS("urn:un:unece:uncefact:data:standard:ReusableAggregateBusinessInformationEntity:100",
                                                              "ID");
      return id.getLength() == 0 ?
             null :
             id.item(0)
               .getTextContent()
               .strip();
    } catch (IOException | SAXException e) {
      return null;
    }
  }

  private static void finding(List<io.github.nalukit.paniolo.validation.ValidationMessage> findings,
                              String rule,
                              io.github.nalukit.paniolo.validation.Severity severity,
                              String message,
                              String location) {
    findings.add(new io.github.nalukit.paniolo.validation.ValidationMessage(rule,
                                                                            severity,
                                                                            message,
                                                                            location,
                                                                            io.github.nalukit.paniolo.validation.ValidationMessage.Stage.HYBRID));
  }

  @Override
  public byte[] embed(byte[] pdf,
                      byte[] xml,
                      FacturXProfile profile) {
    return embed(pdf,
                 xml,
                 profile,
                 List.of());
  }

  @Override
  public byte[] embed(byte[] pdf,
                      byte[] xml,
                      FacturXProfile profile,
                      List<PdfAttachment> attachments) {
    return embed(pdf,
                 xml,
                 profile,
                 attachments,
                 false);
  }

  @Override
  public byte[] replaceInvoice(byte[] pdf,
                               byte[] xml,
                               FacturXProfile profile) {
    return embed(pdf,
                 xml,
                 profile,
                 List.of(),
                 true);
  }

  /**
   * Embeds the invoice; with {@code replace} an embedded invoice XML, e.g. a ZUGFeRD 1.0 one, is
   * removed first, otherwise it is rejected.
   */
  private byte[] embed(byte[] pdf,
                       byte[] xml,
                       FacturXProfile profile,
                       List<PdfAttachment> attachments,
                       boolean replace) {
    Objects.requireNonNull(profile,
                           "profile");
    return embed(pdf,
                 xml,
                 new Hybrid(FX,
                            "Factur-X PDFA Extension Schema",
                            "INVOICE",
                            profile.getFileName(),
                            profile.getXmpVersion(),
                            profile.getConformanceLevel(),
                            profile.getAfRelationship(),
                            "Factur-X invoice (" + profile.getConformanceLevel() + ")"),
                 attachments,
                 replace);
  }

  @Override
  public byte[] embedOrder(byte[] pdf,
                           byte[] xml,
                           OrderXProfile profile,
                           OrderTypeCode type,
                           List<PdfAttachment> attachments) {
    Objects.requireNonNull(profile,
                           "profile");
    Objects.requireNonNull(type,
                           "type");
    return embed(pdf,
                 xml,
                 new Hybrid(OX,
                            "Order-X PDFA Extension Schema",
                            type.getDocumentType(),
                            ORDER_FILE,
                            "1.0",
                            profile.getConformanceLevel(),
                            AfRelationship.ALTERNATIVE.getPdfName(),
                            "Order-X " +
                            type.getDocumentType()
                                .toLowerCase(java.util.Locale.ROOT) +
                            " (" +
                            profile.getConformanceLevel() +
                            ")"),
                 attachments,
                 false);
  }

  @Override
  public byte[] extractOrder(byte[] pdf) {
    Objects.requireNonNull(pdf,
                           "pdf");
    try (PDDocument document = load(pdf)) {
      var file = allFiles(document.getDocumentCatalog()).get(ORDER_FILE);
      if (file == null) {
        throw new IllegalArgumentException("The PDF contains no Order-X document (" + ORDER_FILE + ")");
      }
      return read(file);
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot read Order-X document",
                                     e);
    }
  }

  @Override
  public byte[] embedDespatchAdvice(byte[] pdf,
                                    byte[] xml,
                                    List<PdfAttachment> attachments) {
    return embed(pdf,
                 xml,
                 new Hybrid(DX,
                            "Despatch Advice PDFA Extension Schema",
                            "DESPATCHADVICE",
                            DESPATCH_FILE,
                            "1.0",
                            "PILOT",
                            AfRelationship.ALTERNATIVE.getPdfName(),
                            "Delivery note (CIDA)"),
                 attachments,
                 false);
  }

  @Override
  public byte[] extractDespatchAdvice(byte[] pdf) {
    Objects.requireNonNull(pdf,
                           "pdf");
    try (PDDocument document = load(pdf)) {
      var file = allFiles(document.getDocumentCatalog()).get(DESPATCH_FILE);
      if (file == null) {
        throw new IllegalArgumentException("The PDF contains no despatch advice (" + DESPATCH_FILE + ")");
      }
      return read(file);
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot read despatch advice",
                                     e);
    }
  }

  private byte[] embed(byte[] pdf,
                       byte[] xml,
                       Hybrid hybrid,
                       List<PdfAttachment> attachments,
                       boolean replace) {
    Objects.requireNonNull(attachments,
                           "attachments");
    Objects.requireNonNull(pdf,
                           "pdf");
    Objects.requireNonNull(xml,
                           "xml");
    try (PDDocument document = load(pdf)) {
      if (document.isEncrypted()) {
        throw new IllegalArgumentException("Encrypted PDF documents cannot be Factur-X documents");
      }
      PDDocumentCatalog catalog     = document.getDocumentCatalog();
      String            conformance = pdfaConformance(catalog);
      if (replace) {
        removeInvoiceFiles(catalog);
      } else if (!invoiceFiles(catalog).isEmpty() || allFiles(catalog).containsKey(ORDER_FILE) || allFiles(catalog).containsKey(DESPATCH_FILE)) {
        throw new IllegalArgumentException("The PDF already contains an embedded invoice, order, or despatch advice XML");
      }
      Calendar now = new GregorianCalendar();
      now.set(Calendar.MILLISECOND,
              0);
      var names = new java.util.HashSet<>(allFiles(catalog).keySet());
      for (var attachment : attachments) {
        if (!names.add(attachment.fileName())) {
          throw new IllegalArgumentException("The PDF already contains a file named " + attachment.fileName());
        }
      }
      embedFile(document,
                catalog,
                hybrid.fileName(),
                xml,
                "text/xml",
                hybrid.relationship(),
                hybrid.description(),
                now);
      for (var attachment : attachments) {
        embedFile(document,
                  catalog,
                  attachment.fileName(),
                  attachment.content(),
                  attachment.mimeType(),
                  attachment.relationship()
                            .getPdfName(),
                  attachment.description(),
                  now);
      }
      if (catalog.getOutputIntents()
                 .isEmpty()) {
        addSrgbOutputIntent(document,
                            catalog);
      }
      var info = document.getDocumentInformation();
      info.setModificationDate(now);
      // The XMP pdf:Producer below is written from the document information.
      info.setProducer(PdfProducer.sign(info.getProducer()));
      if (document.getVersion() < 1.7f) {
        document.setVersion(1.7f);
      }
      var metadata = new PDMetadata(document);
      metadata.importXMPMetadata(xmp(info,
                                     conformance,
                                     hybrid).getBytes(java.nio.charset.StandardCharsets.UTF_8));
      catalog.setMetadata(metadata);
      var out = new ByteArrayOutputStream(pdf.length + xml.length + 8192);
      document.save(out);
      LOGGER.log(System.Logger.Level.DEBUG,
                 "Embedded {0} ({1}) into PDF/A-3{2}",
                 hybrid.fileName(),
                 hybrid.conformanceLevel(),
                 conformance);
      return out.toByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot create Factur-X document",
                                     e);
    }
  }

  @Override
  public PdfProducer producer(byte[] pdf) {
    Objects.requireNonNull(pdf,
                           "pdf");
    try (PDDocument document = load(pdf)) {
      var    info     = document.getDocumentInformation();
      String producer = info.getProducer();
      String creator  = info.getCreator();
      // The XMP metadata fills in what the document information lacks.
      Document xmp = parseXmp(document.getDocumentCatalog()
                                      .getMetadata());
      if (xmp != null) {
        if (producer == null || producer.isBlank()) {
          producer = value(xmp,
                           "http://ns.adobe.com/pdf/1.3/",
                           "Producer");
        }
        if (creator == null || creator.isBlank()) {
          creator = value(xmp,
                          "http://ns.adobe.com/xap/1.0/",
                          "CreatorTool");
        }
      }
      return new PdfProducer(producer,
                             creator,
                             PdfProducer.recognize(producer,
                                                   creator,
                                                   pdf));
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot read the PDF document",
                                     e);
    }
  }

  @Override
  public List<PdfAttachment> attachments(byte[] pdf) {
    Objects.requireNonNull(pdf,
                           "pdf");
    try (PDDocument document = load(pdf)) {
      var result = new java.util.ArrayList<PdfAttachment>();
      for (var entry : allFiles(document.getDocumentCatalog()).entrySet()) {
        // Files of other producers may use paths, other cases, or no valid MIME type.
        String name = entry.getKey()
                           .replaceAll(".*[/\\\\]",
                                       "");
        if (name.isBlank() || PdfAttachment.INVOICE_FILE_NAMES.contains(name.toLowerCase(java.util.Locale.ROOT))) {
          continue;
        }
        var file     = entry.getValue();
        var embedded = embeddedFile(file);
        String mimeType = embedded == null ||
                          embedded.getSubtype() == null ||
                          !embedded.getSubtype()
                                   .matches("[^/\\s]+/[^/\\s]+") ?
                          "application/octet-stream" :
                          embedded.getSubtype();
        result.add(new PdfAttachment(name,
                                     read(file,
                                          MAX_FILE),
                                     mimeType,
                                     AfRelationship.ofPdfName(file.getCOSObject()
                                                                  .getNameAsString(RELATIONSHIP)),
                                     file.getFileDescription()));
      }
      return List.copyOf(result);
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot read the attachments of the PDF document",
                                     e);
    }
  }

  @Override
  public <T> T probeInvoice(byte[] pdf,
                            Function<InputStream, T> probe) {
    Objects.requireNonNull(pdf,
                           "pdf");
    Objects.requireNonNull(probe,
                           "probe");
    try (PDDocument document = load(pdf)) {
      // No XMP: the first invoice file in the order of INVOICE_NAMES is enough to answer.
      var files = invoiceFiles(document.getDocumentCatalog());
      if (files.isEmpty()) {
        return null;
      }
      PDEmbeddedFile embedded = embeddedFile(files.values()
                                                  .iterator()
                                                  .next());
      if (embedded == null) {
        return null;
      }
      try (InputStream input = embedded.createInputStream()) {
        return probe.apply(input);
      }
    } catch (IllegalArgumentException | IOException e) {
      return null;
    }
  }

  @Override
  public EmbeddedInvoice extract(byte[] pdf) {
    Objects.requireNonNull(pdf,
                           "pdf");
    try (PDDocument document = load(pdf)) {
      PDDocumentCatalog catalog = document.getDocumentCatalog();
      Document          xmp     = parseXmp(catalog.getMetadata());
      String declared = xmp == null ?
                        null :
                        fxValue(xmp,
                                "DocumentFileName");
      var files = invoiceFiles(catalog);
      if (files.isEmpty()) {
        throw new IllegalArgumentException("The PDF contains no Factur-X / ZUGFeRD invoice XML");
      }
      String name = declared != null && files.containsKey(declared) ?
                    declared :
                    files.keySet()
                         .iterator()
                         .next();
      byte[] xml = read(files.get(name));
      FacturXProfile profile = xmp == null ?
                               null :
                               FacturXProfile.ofConformanceLevel(fxValue(xmp,
                                                                         "ConformanceLevel"));
      if (profile == null) {
        profile = FacturXProfile.ofFileName(name);
      }
      // Without XMP the specification identifier of the invoice tells the profile.
      if (profile == null) {
        profile = FacturXProfile.ofSpecificationId(guideline(xml));
      }
      return new EmbeddedInvoice(name,
                                 profile,
                                 xml);
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot read Factur-X document",
                                     e);
    }
  }

  @Override
  public List<io.github.nalukit.paniolo.validation.ValidationMessage> checkContainer(byte[] pdf) {
    Objects.requireNonNull(pdf,
                           "pdf");
    var findings = new java.util.ArrayList<io.github.nalukit.paniolo.validation.ValidationMessage>();
    try (PDDocument document = load(pdf)) {
      if (document.isEncrypted()) {
        finding(findings,
                "FX-ENCRYPTED",
                io.github.nalukit.paniolo.validation.Severity.ERROR,
                "Factur-X documents must not be encrypted",
                "/Encrypt");
      }
      PDDocumentCatalog catalog = document.getDocumentCatalog();
      var               files   = invoiceFiles(catalog);
      if (files.isEmpty()) {
        finding(findings,
                "FX-NO-INVOICE",
                io.github.nalukit.paniolo.validation.Severity.FATAL,
                "The PDF contains no embedded invoice XML (factur-x.xml, xrechnung.xml or zugferd-invoice.xml)",
                "/Root/Names/EmbeddedFiles");
        return List.copyOf(findings);
      }
      if (files.size() > 1) {
        finding(findings,
                "FX-MULTIPLE-INVOICES",
                io.github.nalukit.paniolo.validation.Severity.ERROR,
                "Only one invoice XML may be embedded, found " + files.keySet(),
                "/Root/Names/EmbeddedFiles");
      }
      String name = files.keySet()
                         .iterator()
                         .next();
      var file = files.get(name);
      checkFile(findings,
                catalog,
                name,
                file);
      checkXmp(findings,
               catalog,
               name,
               file);
    } catch (IllegalArgumentException e) {
      finding(findings,
              "FX-NOT-PDF",
              io.github.nalukit.paniolo.validation.Severity.FATAL,
              e.getMessage(),
              "");
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot read Factur-X document",
                                     e);
    }
    return List.copyOf(findings);
  }

  private void checkXmp(List<io.github.nalukit.paniolo.validation.ValidationMessage> findings,
                        PDDocumentCatalog catalog,
                        String name,
                        PDComplexFileSpecification file)
      throws IOException {
    Document xmp = parseXmp(catalog.getMetadata());
    if (xmp == null) {
      finding(findings,
              "FX-XMP",
              io.github.nalukit.paniolo.validation.Severity.ERROR,
              "The document has no readable XMP metadata",
              "/Root/Metadata");
      return;
    }
    if (!"3".equals(value(xmp,
                          PDFAID,
                          "part"))) {
      finding(findings,
              "FX-PDFA-PART",
              io.github.nalukit.paniolo.validation.Severity.ERROR,
              "Factur-X requires PDF/A-3, but pdfaid:part is " +
              value(xmp,
                    PDFAID,
                    "part"),
              "XMP pdfaid:part");
    }
    if ("ZUGFeRD-invoice.xml".equalsIgnoreCase(name)) {
      checkZugferd1Xmp(findings,
                       xmp,
                       name);
      return;
    }
    if (!"INVOICE".equals(fxValue(xmp,
                                  "DocumentType"))) {
      finding(findings,
              "FX-DOCUMENT-TYPE",
              io.github.nalukit.paniolo.validation.Severity.ERROR,
              "fx:DocumentType must be INVOICE, but is " +
              fxValue(xmp,
                      "DocumentType"),
              "XMP fx:DocumentType");
    }
    String declared = fxValue(xmp,
                              "DocumentFileName");
    if (!name.equals(declared)) {
      finding(findings,
              "FX-DOCUMENT-FILE-NAME",
              io.github.nalukit.paniolo.validation.Severity.ERROR,
              "fx:DocumentFileName must name the embedded file " + name + ", but is " + declared,
              "XMP fx:DocumentFileName");
    }
    String level = fxValue(xmp,
                           "ConformanceLevel");
    if (FacturXProfile.ofConformanceLevel(level) == null) {
      finding(findings,
              "FX-CONFORMANCE-LEVEL",
              io.github.nalukit.paniolo.validation.Severity.ERROR,
              "fx:ConformanceLevel must be MINIMUM, BASIC WL, BASIC, EN 16931, EXTENDED, EXTENDED-CTC-FR or XRECHNUNG, but is " + level,
              "XMP fx:ConformanceLevel");
    } else {
      checkProfile(findings,
                   xmp,
                   level,
                   name,
                   file);
    }
    if (xmp.getDocumentElement() == null || !hasExtensionSchema(xmp)) {
      finding(findings,
              "FX-EXTENSION-SCHEMA",
              io.github.nalukit.paniolo.validation.Severity.ERROR,
              "The XMP metadata must declare the Factur-X namespace " + FX + " in a PDF/A extension schema",
              "XMP pdfaExtension:schemas");
    }
  }

  /**
   * What a hybrid PDF embeds and declares in its XMP metadata.
   */
  private record Hybrid(String namespace, String schemaName, String documentType, String fileName, String version, String conformanceLevel, String relationship, String description) {
  }

}

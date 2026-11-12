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

import io.github.nalukit.paniolo.Paniolo;
import io.github.nalukit.paniolo.config.FacturXProfile;
import io.github.nalukit.paniolo.validation.Severity;
import io.github.nalukit.paniolo.validation.ValidationMessage;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDEmbeddedFilesNameTreeNode;
import org.apache.pdfbox.pdmodel.common.PDMetadata;
import org.apache.pdfbox.pdmodel.common.filespecification.PDComplexFileSpecification;
import org.apache.pdfbox.pdmodel.common.filespecification.PDEmbeddedFile;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.TreeMap;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Generation detection of the XMP metadata: ZUGFeRD 1.0, ZUGFeRD 2.0 (namespace
 * {@code urn:zugferd:pdfa:CrossIndustryDocument:invoice:2p0#}), and Factur-X, with the files in
 * {@code src/test/resources/legacy-pdf} (see the README there).
 */
class LegacyXmpTest {
  private static final Paniolo              PANIOLO  = Paniolo.create();
  private static final PdfBoxFacturXSupport SUPPORT  = new PdfBoxFacturXSupport();
  private static final String               ZF2      = "urn:zugferd:pdfa:CrossIndustryDocument:invoice:2p0#";
  private static final String               FX       = "urn:factur-x:pdfa:CrossIndustryDocument:invoice:1p0#";
  private static final String               ORIGINAL = "attributeBasedXMP_zugferd_2p0_EN16931_Einfach.pdf";
  private static final String               VERSION  = "attributeBasedXMP_zugferd_2p0_EN16931_Einfach-version-2p0.pdf";
  /**
   * The rules wrongly reported for the ZUGFeRD 2.0 files before.
   */
  private static final List<String>         WRONG    = List.of("FX-DOCUMENT-TYPE",
                                                               "FX-DOCUMENT-FILE-NAME",
                                                               "FX-CONFORMANCE-LEVEL",
                                                               "FX-EXTENSION-SCHEMA");
  static byte[] sample(String name) {
    try (InputStream input = LegacyXmpTest.class.getResourceAsStream("/legacy-pdf/" + name)) {
      return input.readAllBytes();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static String xmp(byte[] pdf) {
    try (PDDocument document = Loader.loadPDF(pdf)) {
      return new String(document.getDocumentCatalog()
                                .getMetadata()
                                .toByteArray(),
                        StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * The PDF with its XMP metadata changed; the change must apply.
   */
  private static byte[] withXmp(byte[] pdf,
                                UnaryOperator<String> change) {
    try (PDDocument document = Loader.loadPDF(pdf)) {
      String xmp     = new String(document.getDocumentCatalog()
                                          .getMetadata()
                                          .toByteArray(),
                                  StandardCharsets.UTF_8);
      String changed = change.apply(xmp);
      assertFalse(xmp.equals(changed),
                  "The XMP change did not apply");
      var metadata = new PDMetadata(document);
      metadata.importXMPMetadata(changed.getBytes(StandardCharsets.UTF_8));
      document.getDocumentCatalog()
              .setMetadata(metadata);
      return save(document);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * The PDF with the embedded invoice renamed in the name tree, /F and /UF.
   */
  private static byte[] withFileName(byte[] pdf,
                                     String from,
                                     String to) {
    try (PDDocument document = Loader.loadPDF(pdf)) {
      var                        tree  = document.getDocumentCatalog()
                                                 .getNames()
                                                 .getEmbeddedFiles();
      var                        files = new TreeMap<>(tree.getNames());
      PDComplexFileSpecification file  = files.remove(from);
      file.setFile(to);
      file.setFileUnicode(to);
      files.put(to,
                file);
      var renamed = new PDEmbeddedFilesNameTreeNode();
      renamed.setNames(files);
      document.getDocumentCatalog()
              .getNames()
              .setEmbeddedFiles(renamed);
      return save(document);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * The PDF with other content for the embedded zugferd-invoice.xml.
   */
  private static byte[] withXml(byte[] pdf,
                                byte[] xml) {
    try (PDDocument document = Loader.loadPDF(pdf)) {
      PDComplexFileSpecification file = document.getDocumentCatalog()
                                                .getNames()
                                                .getEmbeddedFiles()
                                                .getNames()
                                                .get("zugferd-invoice.xml");
      var embedded = new PDEmbeddedFile(document,
                                        new ByteArrayInputStream(xml),
                                        COSName.FLATE_DECODE);
      embedded.setSubtype("text/xml");
      embedded.setModDate(file.getEmbeddedFile()
                              .getModDate());
      file.setEmbeddedFile(embedded);
      file.setEmbeddedFileUnicode(embedded);
      return save(document);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static byte[] save(PDDocument document)
      throws IOException {
    var out = new ByteArrayOutputStream();
    document.save(out);
    return out.toByteArray();
  }

  private static List<ValidationMessage> findings(byte[] pdf) {
    return SUPPORT.checkContainer(pdf);
  }

  private static List<String> errors(byte[] pdf) {
    return findings(pdf).stream()
                        .filter(message -> message.severity() == Severity.ERROR || message.severity() == Severity.FATAL)
                        .map(ValidationMessage::ruleId)
                        .toList();
  }

  /**
   * Rule, severity, and location of the findings; the wording may name values.
   */
  private static List<String> signature(byte[] pdf) {
    return findings(pdf).stream()
                        .map(message -> message.ruleId() + " " + message.severity() + " " + message.location())
                        .toList();
  }

  @Test
  void keepsTheRegressionFilesUnchanged()
      throws Exception {
    var sha256 = MessageDigest.getInstance("SHA-256");
    assertEquals("29842457f57143aba5da78fdf390aba773c965e60b1caab93dfff3e9d4c802f4",
                 HexFormat.of()
                          .formatHex(sha256.digest(sample(ORIGINAL))));
    assertEquals("d7b24adaebdc620bdbc5cb5909232d3a26a23eeebe331a993eb16941dee79f91",
                 HexFormat.of()
                          .formatHex(sha256.digest(sample(VERSION))));
    assertEquals("a0046f55cab4b1ec2909e83a4b6970a37a5905e9a8ce469b4e75e2ab1cabc59e",
                 HexFormat.of()
                          .formatHex(sha256.digest(sample("validXRechnung.pdf"))));
    assertEquals("b4dedee2f42a49b3ae77d0220e1e2a48df381fcd873db9bf90549b52623adbef",
                 HexFormat.of()
                          .formatHex(sha256.digest(sample("validXRechnung-xrechnung-xml.pdf"))));
    // The original carries the ZUGFeRD 2.0 properties as attributes of rdf:Description.
    String xmp = xmp(sample(ORIGINAL));
    assertTrue(xmp.contains("xmlns:fx=\"" + ZF2 + "\"") && xmp.contains("fx:Version=\"1.0\""),
               xmp);
  }

  /**
   * The original states Version 1.0 instead of 2p0: no missing ZUGFeRD 1.0 properties, but the
   * warning FX-VERSION of the compatibility rule.
   */
  @Test
  void readsTheZugferd2MetadataOfTheOriginal() {
    byte[] pdf      = sample(ORIGINAL);
    var    findings = findings(pdf);
    assertEquals(List.of(),
                 errors(pdf),
                 findings::toString);
    var version = findings.stream()
                          .filter(message -> "FX-VERSION".equals(message.ruleId()))
                          .toList();
    assertEquals(1,
                 version.size(),
                 findings::toString);
    assertEquals(Severity.WARNING,
                 version.get(0)
                        .severity());
    assertTrue(version.get(0)
                      .message()
                      .contains("2p0") && version.get(0)
                                                 .message()
                                                 .contains("1.0"),
               version.get(0)::message);

    var invoice = SUPPORT.extract(pdf);
    assertEquals("zugferd-invoice.xml",
                 invoice.fileName());
    assertEquals(FacturXProfile.EN16931,
                 invoice.profile());
  }

  @Test
  void acceptsTheZugferd2ControlWithVersion2p0() {
    byte[] pdf = sample(VERSION);
    assertEquals(List.of(),
                 findings(pdf).stream()
                              .filter(message -> message.severity() != Severity.INFO)
                              .toList());
    assertEquals(FacturXProfile.EN16931,
                 SUPPORT.extract(pdf)
                        .profile());
  }

  /**
   * The whole validation: container and invoice XML. The original keeps its version warning.
   */
  @Test
  void validatesBothZugferd2Files() {
    for (String name : List.of(ORIGINAL,
                               VERSION)) {
      var result = PANIOLO.validate(sample(name));
      assertTrue(result.isValid(),
                 () -> name + ": " + result.messages());
      assertTrue(result.messages()
                       .stream()
                       .noneMatch(message -> WRONG.contains(message.ruleId())),
                 () -> name + ": " + result.messages());
      // The invoice XML is validated, too: its profile follows the container's.
      assertTrue(result.profileName()
                       .startsWith("Factur-X EN 16931 / "),
                 result::profileName);
      assertEquals(ORIGINAL.equals(name),
                   result.messages()
                         .stream()
                         .anyMatch(message -> "FX-VERSION".equals(message.ruleId()) && message.severity() == Severity.WARNING),
                   () -> name + ": " + result.messages());
    }
    assertEquals(PANIOLO.read(sample(ORIGINAL)),
                 PANIOLO.read(sample(VERSION)));
  }

  /**
   * The values are really read: wrong ones are reported with their value.
   */
  @Test
  void reportsTheValuesItReads() {
    byte[] pdf = withXmp(sample(VERSION),
                         xmp -> xmp.replace("fx:DocumentType=\"INVOICE\"",
                                            "fx:DocumentType=\"ORDER\"")
                                   .replace("fx:ConformanceLevel=\"EN 16931\"",
                                            "fx:ConformanceLevel=\"COMFORT\"")
                                   .replace("fx:Version=\"2p0\"",
                                            "fx:Version=\"3.0\""));
    var findings = findings(pdf);
    assertTrue(findings.stream()
                       .anyMatch(message -> "FX-DOCUMENT-TYPE".equals(message.ruleId()) && message.message()
                                                                                                  .contains("ORDER")),
               findings::toString);
    assertTrue(findings.stream()
                       .anyMatch(message -> "FX-CONFORMANCE-LEVEL".equals(message.ruleId()) && message.message()
                                                                                                      .contains("ZUGFeRD 2.0") && message.message()
                                                                                                                                         .contains("COMFORT")),
               findings::toString);
    assertTrue(findings.stream()
                       .anyMatch(message -> "FX-VERSION".equals(message.ruleId()) && message.severity() == Severity.ERROR && message.message()
                                                                                                                                    .contains("3.0")),
               findings::toString);
  }

  /**
   * Properties as elements or as attributes of rdf:Description are the same.
   */
  @Test
  void treatsElementsAndAttributesAlike() {
    String elements = """
                      <rdf:Description xmlns:fx="urn:zugferd:pdfa:CrossIndustryDocument:invoice:2p0#" rdf:about="">
                         <fx:ConformanceLevel>EN 16931</fx:ConformanceLevel>
                         <fx:DocumentFileName>zugferd-invoice.xml</fx:DocumentFileName>
                         <fx:DocumentType>INVOICE</fx:DocumentType>
                         <fx:Version>1.0</fx:Version>
                      </rdf:Description>""";
    byte[] pdf = withXmp(sample(ORIGINAL),
                         xmp -> xmp.replaceFirst("<rdf:Description xmlns:fx=\"urn:zugferd[^>]*/>",
                                                 elements));
    assertFalse(xmp(pdf).contains("fx:Version=\""));
    assertEquals(signature(sample(ORIGINAL)),
                 signature(pdf));
    assertEquals(FacturXProfile.EN16931,
                 SUPPORT.extract(pdf)
                        .profile());
  }

  /**
   * Prefixes are no identity: other prefixes give the same result.
   */
  @Test
  void ignoresTheNamespacePrefix() {
    byte[] pdf = withXmp(sample(ORIGINAL),
                         xmp -> xmp.replace("xmlns:fx=\"" + ZF2 + "\"",
                                            "xmlns:legacy=\"" + ZF2 + "\"")
                                   .replace("fx:ConformanceLevel=",
                                            "legacy:ConformanceLevel=")
                                   .replace("fx:DocumentFileName=",
                                            "legacy:DocumentFileName=")
                                   .replace("fx:DocumentType=",
                                            "legacy:DocumentType=")
                                   .replace("fx:Version=",
                                            "legacy:Version=")
                                   .replace("<pdfaSchema:prefix>fx<",
                                            "<pdfaSchema:prefix>legacy<"));
    assertEquals(signature(sample(ORIGINAL)),
                 signature(pdf));
  }

  /**
   * The local names alone do not count: properties of an unknown namespace are missing ones.
   */
  @Test
  void rejectsAnUnknownNamespace() {
    byte[] pdf = withXmp(sample(VERSION),
                         xmp -> xmp.replace("xmlns:fx=\"" + ZF2 + "\"",
                                            "xmlns:fx=\"urn:example:pdfa:CrossIndustryDocument:invoice:2p0#\""));
    var rules = errors(pdf);
    for (String rule : List.of("FX-DOCUMENT-TYPE",
                               "FX-DOCUMENT-FILE-NAME",
                               "FX-CONFORMANCE-LEVEL",
                               "FX-VERSION")) {
      assertTrue(rules.contains(rule),
                 () -> rule + " in " + rules);
    }
    // The extension schema still declares 2p0, so the missing properties are the ZUGFeRD 2.0 ones.
    assertTrue(findings(pdf).stream()
                            .anyMatch(message -> "FX-DOCUMENT-TYPE".equals(message.ruleId()) && message.message()
                                                                                                       .contains("ZUGFeRD 2.0")),
               () -> findings(pdf).toString());
  }

  @Test
  void rejectsAMissingProperty() {
    byte[] pdf = withXmp(sample(VERSION),
                         xmp -> xmp.replace("fx:DocumentType=\"INVOICE\"",
                                            ""));
    assertEquals(List.of("FX-DOCUMENT-TYPE"),
                 errors(pdf));
  }

  @Test
  void rejectsAnExtensionSchemaOfAnotherGeneration() {
    byte[] pdf = withXmp(sample(VERSION),
                         xmp -> xmp.replace("<pdfaSchema:namespaceURI>" + ZF2,
                                            "<pdfaSchema:namespaceURI>" + FX));
    assertEquals(List.of("FX-EXTENSION-SCHEMA"),
                 errors(pdf));
    assertTrue(findings(pdf).stream()
                            .anyMatch(message -> message.message()
                                                        .contains(ZF2)),
               () -> findings(pdf).toString());
  }

  /**
   * File names are compared exactly: ZUGFeRD 2.0 metadata with the ZUGFeRD 1.0 name
   * {@code ZUGFeRD-invoice.xml} stay ZUGFeRD 2.0 and are rejected.
   */
  @Test
  void rejectsTheFileNameOfAnotherGeneration() {
    byte[] renamed = withFileName(sample(VERSION),
                                  "zugferd-invoice.xml",
                                  "ZUGFeRD-invoice.xml");
    assertEquals(List.of("FX-DOCUMENT-FILE-NAME",
                         "FX-FILE-NAME"),
                 errors(renamed));
    byte[] consistent = withXmp(renamed,
                                xmp -> xmp.replace("fx:DocumentFileName=\"zugferd-invoice.xml\"",
                                                   "fx:DocumentFileName=\"ZUGFeRD-invoice.xml\""));
    assertEquals(List.of("FX-FILE-NAME"),
                 errors(consistent));
  }

  /**
   * ZUGFeRD 2.0 metadata with a ZUGFeRD 1.0 invoice XML.
   */
  @Test
  void rejectsAnXmlRootOfAnotherGeneration() {
    byte[] pdf = withXml(sample(VERSION),
                         Zugferd1Test.sample("comfort.xml"));
    var rules = errors(pdf);
    assertTrue(rules.contains("FX-XML-ROOT"),
               rules::toString);
    // ZUGFeRD 1.0 XML has no CII specification identifier for EN 16931.
    assertTrue(rules.contains("FX-GUIDELINE"),
               rules::toString);
  }

  @Test
  void rejectsALevelThatDoesNotMatchTheInvoice() {
    byte[] pdf = withXmp(sample(VERSION),
                         xmp -> xmp.replace("fx:ConformanceLevel=\"EN 16931\"",
                                            "fx:ConformanceLevel=\"EXTENDED\""));
    assertEquals(List.of("FX-GUIDELINE"),
                 errors(pdf));
    // ZUGFeRD 2.0 has no XRECHNUNG level; Factur-X has.
    byte[] xrechnung = withXmp(sample(VERSION),
                               xmp -> xmp.replace("fx:ConformanceLevel=\"EN 16931\"",
                                                  "fx:ConformanceLevel=\"XRECHNUNG\""));
    assertEquals(List.of("FX-CONFORMANCE-LEVEL"),
                 errors(xrechnung));
  }

  /**
   * Properties of two generations in one XMP packet are an error; the one whose file name is
   * embedded is checked.
   */
  @Test
  void rejectsSeveralGenerationsInOnePacket() {
    String factur = """
                    <rdf:Description xmlns:x2="urn:factur-x:pdfa:CrossIndustryDocument:invoice:1p0#" rdf:about=""
                                     x2:ConformanceLevel="BASIC" x2:DocumentFileName="factur-x.xml"
                                     x2:DocumentType="INVOICE" x2:Version="1.0"/>
                    </rdf:RDF>""";
    byte[] pdf = withXmp(sample(VERSION),
                         xmp -> xmp.replace("</rdf:RDF>",
                                            factur));
    var findings = findings(pdf);
    assertEquals(List.of("FX-XMP-GENERATIONS"),
                 errors(pdf),
                 findings::toString);
    // The extraction picks the same generation: its level, not BASIC of the other namespace.
    assertEquals(FacturXProfile.EN16931,
                 SUPPORT.extract(pdf)
                        .profile());
  }

  /**
   * A level in a foreign namespace is not taken as profile of the embedded invoice.
   */
  @Test
  void takesTheProfileOnlyFromTheDeclaredNamespace() {
    byte[] pdf = withXmp(sample(VERSION),
                         xmp -> xmp.replace("</rdf:RDF>",
                                            "<rdf:Description xmlns:other=\"urn:example:invoice#\" rdf:about=\"\" other:ConformanceLevel=\"EXTENDED\"/></rdf:RDF>"));
    assertEquals(List.of(),
                 errors(pdf));
    assertEquals(FacturXProfile.EN16931,
                 SUPPORT.extract(pdf)
                        .profile());
  }

  /**
   * The profile XRECHNUNG keeps its own file name rule.
   */
  @Test
  void keepsTheFileNameRuleOfXrechnung() {
    var original = findings(sample("validXRechnung.pdf"));
    assertEquals(List.of("FX-FILE-NAME"),
                 errors(sample("validXRechnung.pdf")),
                 original::toString);
    var corrected = findings(sample("validXRechnung-xrechnung-xml.pdf"));
    assertEquals(List.of(),
                 errors(sample("validXRechnung-xrechnung-xml.pdf")),
                 corrected::toString);
    // Apart from the file name the findings are the same.
    assertEquals(original.stream()
                         .filter(message -> !"FX-FILE-NAME".equals(message.ruleId()))
                         .map(message -> message.ruleId() + " " + message.severity())
                         .toList(),
                 corrected.stream()
                          .map(message -> message.ruleId() + " " + message.severity())
                          .toList());
    assertEquals(FacturXProfile.XRECHNUNG,
                 SUPPORT.extract(sample("validXRechnung.pdf"))
                        .profile());
  }
}

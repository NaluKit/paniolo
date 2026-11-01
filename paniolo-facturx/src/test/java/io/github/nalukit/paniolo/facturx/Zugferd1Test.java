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
import io.github.nalukit.paniolo.config.PdfAttachment;
import io.github.nalukit.paniolo.model.common.CashDiscount;
import io.github.nalukit.paniolo.model.common.ProcessControl;
import io.github.nalukit.paniolo.validation.Severity;
import io.github.nalukit.paniolo.xml.ReadMode;
import io.github.nalukit.paniolo.xml.ReadWarning;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDEmbeddedFilesNameTreeNode;
import org.apache.pdfbox.pdmodel.common.PDMetadata;
import org.apache.pdfbox.pdmodel.common.filespecification.PDComplexFileSpecification;
import org.junit.jupiter.api.Test;
import org.verapdf.pdfa.flavours.PDFAFlavour;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Zugferd1Test {
  private static final Paniolo              PANIOLO = Paniolo.create();
  private static final PdfBoxFacturXSupport SUPPORT = new PdfBoxFacturXSupport();

  static byte[] sample(String name) {
    try (InputStream input = Zugferd1Test.class.getResourceAsStream("/zugferd1/" + name)) {
      return input.readAllBytes();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * A ZUGFeRD 1.0 PDF: the XML embedded as {@code ZUGFeRD-invoice.xml} next to another attachment.
   */
  private static byte[] zugferd1Pdf(byte[] xml)
      throws Exception {
    byte[] embedded = SUPPORT.embed(TestPdfs.pdfA2b(),
                                    xml,
                                    FacturXProfile.EN16931,
                                    List.of(PdfAttachment.of("hinweis.txt",
                                                             "Hinweis".getBytes(StandardCharsets.UTF_8),
                                                             "text/plain")));
    try (PDDocument document = Loader.loadPDF(embedded)) {
      var catalog = document.getDocumentCatalog();
      var tree = catalog.getNames()
                        .getEmbeddedFiles();
      var                        files = new TreeMap<>(tree.getNames());
      PDComplexFileSpecification file  = files.remove("factur-x.xml");
      file.setFile("ZUGFeRD-invoice.xml");
      file.setFileUnicode("ZUGFeRD-invoice.xml");
      files.put("ZUGFeRD-invoice.xml",
                file);
      var renamed = new PDEmbeddedFilesNameTreeNode();
      renamed.setNames(files);
      catalog.getNames()
             .setEmbeddedFiles(renamed);
      var out = new ByteArrayOutputStream();
      document.save(out);
      return out.toByteArray();
    }
  }

  @Test
  void readsZugferd1ComfortAsEn16931() {
    var invoice = PANIOLO.read(sample("comfort.xml"));
    assertEquals(ProcessControl.EN16931,
                 invoice.processControl()
                        .specificationId());
    assertEquals("Z1-2014-001",
                 invoice.number());
    assertEquals("Vielen Dank für Ihren Auftrag.\nLieferung frei Haus.",
                 invoice.notes()
                        .get(0)
                        .text());
    assertEquals(2,
                 invoice.lines()
                        .size());
    assertEquals(new BigDecimal("10.00"),
                 invoice.lines()
                        .get(0)
                        .priceDiscount());
    // The logistics service charge becomes a document charge outside EXTENDED.
    assertTrue(invoice.adjustments()
                      .stream()
                      .anyMatch(adjustment -> adjustment.charge() && "Versandkosten".equals(adjustment.reason())));
    assertEquals(List.of(CashDiscount.of(14,
                                         new BigDecimal("2.00"))),
                 invoice.cashDiscounts());
    assertEquals(new BigDecimal("404.60"),
                 invoice.totals()
                        .payableAmount());
    assertEquals("Erika Muster",
                 invoice.seller()
                        .contact()
                        .name());
  }

  @Test
  void migratesXmlIntoTheCorrespondingProfiles() {
    byte[] comfort = PANIOLO.migrateZugferd1(sample("comfort.xml"));
    var en16931 = PANIOLO.validate(comfort,
                                   FacturXProfile.EN16931);
    assertTrue(en16931.isValid(),
               () -> en16931.messages()
                            .toString());
    assertEquals(PANIOLO.read(sample("comfort.xml")),
                 PANIOLO.read(comfort));

    byte[] basic = PANIOLO.migrateZugferd1(sample("basic.xml"));
    assertTrue(new String(basic,
                          StandardCharsets.UTF_8).contains(ProcessControl.FACTURX_BASIC));
    var result = PANIOLO.validate(basic);
    assertTrue(result.isValid(),
               () -> result.messages()
                           .toString());
    // A BASIC line with only its amount gets one price for the whole quantity.
    var line = PANIOLO.read(basic)
                      .lines()
                      .get(0);
    assertEquals(new BigDecimal("120.00"),
                 line.netAmount());
  }

  @Test
  void migratesIntoAChosenProfile() {
    byte[] extended = PANIOLO.migrateZugferd1(sample("comfort.xml"),
                                              FacturXProfile.EXTENDED);
    var invoice = PANIOLO.read(extended);
    assertEquals(ProcessControl.FACTURX_EXTENDED,
                 invoice.processControl()
                        .specificationId());
    assertEquals(1,
                 invoice.extended()
                        .logisticsServiceCharges()
                        .size());
    assertEquals(1,
                 invoice.extended()
                        .paymentTerms()
                        .size());
    assertEquals(new BigDecimal("404.60"),
                 invoice.totals()
                        .payableAmount());

    String penalty = new String(sample("comfort.xml"),
                                StandardCharsets.UTF_8).replace("ApplicableTradePaymentDiscountTerms",
                                                                "ApplicableTradePaymentPenaltyTerms");
    var error = assertThrows(IllegalArgumentException.class,
                             () -> PANIOLO.migrateZugferd1(penalty.getBytes(StandardCharsets.UTF_8)));
    assertTrue(error.getMessage()
                    .contains("EXTENDED"),
               error::getMessage);
    assertNotNull(PANIOLO.migrateZugferd1(penalty.getBytes(StandardCharsets.UTF_8),
                                          FacturXProfile.EXTENDED));
    assertThrows(IllegalArgumentException.class,
                 () -> PANIOLO.migrateZugferd1(sample("comfort.xml"),
                                               FacturXProfile.XRECHNUNG));
    assertThrows(IllegalArgumentException.class,
                 () -> PANIOLO.migrateZugferd1(FacturXValidationTest.sample("minimum.xml")));
  }

  @Test
  void validatesZugferd1XmlAgainstItsSchemaAndSchematron() {
    for (String name : List.of("basic.xml",
                               "comfort.xml")) {
      var result = PANIOLO.validate(sample(name));
      assertTrue(result.isValid(),
                 () -> name + ": " + result.messages());
      assertEquals("ZUGFeRD 1.0",
                   result.profileName());
      // ZUGFeRD 1.0 is outdated: a warning, the migration is the way out.
      assertTrue(result.messages()
                       .stream()
                       .anyMatch(message -> "FX-ZUGFERD-1".equals(message.ruleId()) && message.severity() == Severity.WARNING),
                 () -> result.messages()
                             .toString());
    }
    // The Schematron finds errors: an amount without currency.
    String broken = new String(sample("comfort.xml"),
                               StandardCharsets.UTF_8).replaceFirst("(<ram:LineTotalAmount) currencyID=\"EUR\"",
                                                                    "$1");
    var invalid = PANIOLO.validate(broken.getBytes(StandardCharsets.UTF_8));
    assertFalse(invalid.isValid(),
                () -> invalid.messages()
                             .toString());
  }

  @Test
  void migratesZugferd1Pdf()
      throws Exception {
    byte[] old = zugferd1Pdf(sample("comfort.xml"));
    assertEquals(PANIOLO.read(sample("comfort.xml")),
                 PANIOLO.read(old));
    var outdated = PANIOLO.validate(old);
    assertTrue(outdated.messages()
                       .stream()
                       .anyMatch(message -> "FX-ZUGFERD-1".equals(message.ruleId())),
               () -> outdated.messages()
                             .toString());

    byte[] migrated = PANIOLO.migrateZugferd1(old);
    assertEquals(List.of(),
                 TestPdfs.pdfaFailures(migrated,
                                       PDFAFlavour.PDFA_3_B));
    assertEquals(List.of(),
                 SUPPORT.checkContainer(migrated));
    var result = PANIOLO.validate(migrated);
    assertTrue(result.isValid(),
               () -> result.messages()
                           .toString());
    assertEquals(PANIOLO.read(sample("comfort.xml")),
                 PANIOLO.read(migrated));
    assertEquals(List.of("hinweis.txt"),
                 PANIOLO.attachments(migrated)
                        .stream()
                        .map(PdfAttachment::fileName)
                        .toList());
    try (PDDocument document = Loader.loadPDF(migrated)) {
      COSArray associated = document.getDocumentCatalog()
                                    .getCOSObject()
                                    .getCOSArray(COSName.getPDFName("AF"));
      var names = new java.util.ArrayList<String>();
      for (int i = 0; i < associated.size(); i++) {
        names.add(new PDComplexFileSpecification((COSDictionary) associated.getObject(i)).getFileUnicode());
      }
      assertEquals(List.of("factur-x.xml",
                           "hinweis.txt"),
                   names.stream()
                        .sorted()
                        .toList());
    }
  }

  /**
   * ZUGFeRD 1.0 EXTENDED allows lines with a text only and lines without identifier, as the
   * official examples of the ZUGFeRD corpus use them (Mustang issues #594 and #572).
   */
  @Test
  void readsTextLinesAndLinesWithoutIdentifier() {
    String xml = new String(sample("comfort.xml"),
                            StandardCharsets.UTF_8).replace("<ram:LineID>1</ram:LineID>",
                                                            "")
                                                   .replace("<ram:LineID>2</ram:LineID>",
                                                            "")
                                                   .replaceFirst("<ram:IncludedSupplyChainTradeLineItem>",
                                                                 "<ram:IncludedSupplyChainTradeLineItem><ram:AssociatedDocumentLineDocument><ram:IncludedNote><ram:Content>Lieferung frei Haus</ram:Content></ram:IncludedNote></ram:AssociatedDocumentLineDocument><ram:SpecifiedSupplyChainTradeSettlement/></ram:IncludedSupplyChainTradeLineItem><ram:IncludedSupplyChainTradeLineItem>");
    var strict = assertThrows(IllegalArgumentException.class,
                              () -> PANIOLO.read(xml,
                                                 ReadMode.STRICT));
    assertTrue(strict.getMessage()
                     .contains("Line 1 has a text only") && strict.getMessage()
                                                                  .contains("Lieferung frei Haus"),
               strict::getMessage);
    var lenient = PANIOLO.read(xml,
                               ReadMode.LENIENT);
    // The text becomes an invoice note; the lines are numbered by their position.
    assertEquals(List.of("1",
                         "2"),
                 lenient.invoice()
                        .lines()
                        .stream()
                        .map(line -> line.id())
                        .toList());
    assertTrue(lenient.invoice()
                      .notes()
                      .stream()
                      .anyMatch(note -> "Lieferung frei Haus".equals(note.text())),
               () -> lenient.invoice()
                            .notes()
                            .toString());
    assertTrue(lenient.warnings()
                      .stream()
                      .anyMatch(warning -> warning.code()
                                                  .equals(ReadWarning.TEXT_LINE)),
               lenient.warnings()::toString);
  }

  /**
   * A ZUGFeRD 1.0 PDF declares its own XMP namespace and conformance levels; the Factur-X
   * properties do not apply.
   */
  @Test
  void checksTheXmpOfAZugferd1Pdf()
      throws Exception {
    byte[] pdf = zugferd1Pdf(sample("comfort.xml"));
    // The Factur-X XMP of a ZUGFeRD 1.0 file is reported.
    var factur = SUPPORT.checkContainer(pdf)
                        .stream()
                        .map(message -> message.ruleId())
                        .toList();
    assertTrue(factur.contains("FX-EXTENSION-SCHEMA"),
               factur::toString);
    byte[] zugferd1;
    try (PDDocument document = Loader.loadPDF(pdf)) {
      var    metadata = document.getDocumentCatalog()
                                .getMetadata();
      String xmp      = new String(metadata.toByteArray(),
                                   StandardCharsets.UTF_8).replace("urn:factur-x:pdfa:CrossIndustryDocument:invoice:1p0#",
                                                                   "urn:ferd:pdfa:CrossIndustryDocument:invoice:1p0#")
                                                          .replace("factur-x.xml",
                                                                   "ZUGFeRD-invoice.xml")
                                                          .replace(">EN 16931<",
                                                                   ">COMFORT<");
      var replaced = new PDMetadata(document);
      replaced.importXMPMetadata(xmp.getBytes(StandardCharsets.UTF_8));
      document.getDocumentCatalog()
              .setMetadata(replaced);
      var out = new ByteArrayOutputStream();
      document.save(out);
      zugferd1 = out.toByteArray();
    }
    var findings = SUPPORT.checkContainer(zugferd1)
                          .stream()
                          .map(message -> message.ruleId() + " " + message.message())
                          .toList();
    for (String rule : List.of("FX-DOCUMENT-TYPE",
                               "FX-DOCUMENT-FILE-NAME",
                               "FX-CONFORMANCE-LEVEL",
                               "FX-EXTENSION-SCHEMA")) {
      assertFalse(findings.stream()
                          .anyMatch(finding -> finding.startsWith(rule)),
                  findings::toString);
    }
  }
}

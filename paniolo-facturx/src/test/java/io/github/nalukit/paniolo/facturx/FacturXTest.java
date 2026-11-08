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
import io.github.nalukit.paniolo.model.common.value.InvoiceTypeCode;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.verapdf.pdfa.flavours.PDFAFlavour;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FacturXTest {
  private static final Paniolo PANIOLO = Paniolo.create();

  @Test
  void testInputIsValidPdfA2b() {
    assertEquals(java.util.List.of(),
                 TestPdfs.pdfaFailures(TestPdfs.pdfA2b(),
                                       PDFAFlavour.PDFA_2_B));
    // Guards against a validator that silently accepts everything.
    assertFalse(TestPdfs.pdfaFailures(TestPdfs.plainPdf(),
                                      PDFAFlavour.PDFA_2_B)
                        .isEmpty());
  }

  @ParameterizedTest
  @CsvSource({ "BASIC, CODE_380",
               "BASIC, CODE_381",
               "EN16931, CODE_380",
               "EN16931, CODE_381",
               "XRECHNUNG, CODE_380",
               "XRECHNUNG, CODE_381" })
  void createsValidFacturXAndReadsItBack(FacturXProfile profile,
                                         InvoiceTypeCode type)
      throws Exception {
    var invoice = FacturXInvoices.invoice(profile,
                                          type)
                                 .build();
    byte[] pdf = PANIOLO.toFacturX(invoice,
                                   TestPdfs.pdfA2b(),
                                   profile);

    assertTrue(Paniolo.isPdf(pdf));
    assertEquals(java.util.List.of(),
                 TestPdfs.pdfaFailures(pdf,
                                       PDFAFlavour.PDFA_3_B));
    var result = PANIOLO.validate(pdf);
    assertTrue(result.isValid(),
               () -> result.messages()
                           .toString());
    assertFalse(result.hasWarnings(),
                () -> result.messages()
                            .toString());
    assertEquals(invoice,
                 PANIOLO.read(pdf));

    try (PDDocument document = Loader.loadPDF(pdf)) {
      COSArray associated = document.getDocumentCatalog()
                                    .getCOSObject()
                                    .getCOSArray(COSName.getPDFName("AF"));
      assertEquals(1,
                   associated.size());
      var file = (COSDictionary) associated.getObject(0);
      assertEquals(profile.getFileName(),
                   file.getString(COSName.UF));
      assertEquals("Alternative",
                   file.getNameAsString(COSName.getPDFName("AFRelationship")));
      String xmp = new String(document.getDocumentCatalog()
                                      .getMetadata()
                                      .toByteArray(),
                              StandardCharsets.UTF_8);
      assertTrue(xmp.contains("<fx:ConformanceLevel>" + profile.getConformanceLevel() + "</fx:ConformanceLevel>"));
      assertTrue(xmp.contains("<fx:DocumentFileName>" + profile.getFileName() + "</fx:DocumentFileName>"));
      assertTrue(xmp.contains("<pdfaid:part>3</pdfaid:part>"));
    }
  }

  @Test
  void en16931ProfileIsNotCheckedAgainstGermanRules() {
    var invoice = FacturXInvoices.invoice(FacturXProfile.EN16931,
                                          InvoiceTypeCode.CODE_380)
                                 .buyerReference(null)
                                 .build();
    byte[] pdf = PANIOLO.toFacturX(invoice,
                                   TestPdfs.pdfA2b(),
                                   FacturXProfile.EN16931);
    var result = PANIOLO.validate(pdf);
    assertTrue(result.isValid(),
               () -> result.messages()
                           .toString());
    // The extracted XML states plain EN 16931: the German rules are information only.
    var xml = PANIOLO.validate(PANIOLO.extractXml(pdf));
    assertTrue(xml.isValid(),
               () -> xml.messages()
                        .toString());
    assertTrue(xml.messages()
                  .stream()
                  .anyMatch(message -> message.ruleId()
                                              .equals("BR-DE-15") && message.severity() == io.github.nalukit.paniolo.validation.Severity.INFO),
               () -> xml.messages()
                        .toString());
  }

  @Test
  void basicProfileIsCheckedAgainstItsOwnRulesNotTheGermanOnes() {
    var invoice = FacturXInvoices.invoice(FacturXProfile.BASIC,
                                          InvoiceTypeCode.CODE_380)
                                 .buyerReference(null)
                                 .build();
    var result = PANIOLO.validate(PANIOLO.toFacturX(invoice,
                                                    TestPdfs.pdfA2b(),
                                                    FacturXProfile.BASIC));
    assertTrue(result.isValid(),
               () -> result.messages()
                           .toString());
    assertTrue(result.profileName()
                     .startsWith("Factur-X BASIC / Factur-X BASIC"),
               result::profileName);
  }

  @Test
  void basicProfileRejectsContentItDoesNotAllow() {
    // The EN 16931 test invoice carries a seller contact and an account name.
    var invoice = FacturXInvoices.invoice(FacturXProfile.EN16931,
                                          InvoiceTypeCode.CODE_380)
                                 .processControl(io.github.nalukit.paniolo.model.common.ProcessControl.facturXBasic())
                                 .build();
    var error = assertThrows(IllegalArgumentException.class,
                             () -> PANIOLO.toFacturX(invoice,
                                                     TestPdfs.pdfA2b(),
                                                     FacturXProfile.BASIC));
    assertTrue(error.getMessage()
                    .contains("seller contact (BG-6)"),
               error::getMessage);
    assertTrue(error.getMessage()
                    .contains("payment account name (BT-85)"),
               error::getMessage);
  }

  @ParameterizedTest
  @EnumSource(value = FacturXProfile.class,
              names = { "MINIMUM",
                        "BASIC_WL",
                        "EXTENDED_CTC_FR" },
              mode = EnumSource.Mode.EXCLUDE)
  void rejectsInvoiceWhoseSpecificationIdentifierDoesNotMatch(FacturXProfile profile) {
    var other = profile == FacturXProfile.EN16931 ?
                FacturXProfile.XRECHNUNG :
                FacturXProfile.EN16931;
    var invoice = FacturXInvoices.invoice(other,
                                          InvoiceTypeCode.CODE_380)
                                 .build();
    assertThrows(IllegalArgumentException.class,
                 () -> PANIOLO.toFacturX(invoice,
                                         TestPdfs.pdfA2b(),
                                         profile));
  }

  @Test
  void rejectsUnsuitableInput() {
    var invoice = FacturXInvoices.invoice(FacturXProfile.EN16931,
                                          InvoiceTypeCode.CODE_380)
                                 .build();
    var notPdfA = assertThrows(IllegalArgumentException.class,
                               () -> PANIOLO.toFacturX(invoice,
                                                       TestPdfs.plainPdf(),
                                                       FacturXProfile.EN16931));
    assertTrue(notPdfA.getMessage()
                      .contains("PDF/A"));
    assertThrows(IllegalArgumentException.class,
                 () -> PANIOLO.toFacturX(invoice,
                                         TestPdfs.encryptedPdf(),
                                         FacturXProfile.EN16931));
    byte[] facturX = PANIOLO.toFacturX(invoice,
                                       TestPdfs.pdfA2b(),
                                       FacturXProfile.EN16931);
    assertThrows(IllegalArgumentException.class,
                 () -> PANIOLO.toFacturX(invoice,
                                         facturX,
                                         FacturXProfile.EN16931));
    assertThrows(IllegalArgumentException.class,
                 () -> PANIOLO.toFacturX(invoice,
                                         "%PDF-1.7 broken".getBytes(StandardCharsets.US_ASCII),
                                         FacturXProfile.EN16931));
  }

  @Test
  void rejectsPdfWithoutInvoice() {
    var error = assertThrows(IllegalArgumentException.class,
                             () -> PANIOLO.read(TestPdfs.pdfA2b()));
    assertTrue(error.getMessage()
                    .contains("no Factur-X"));
    // Validation reports the missing invoice as finding instead of failing.
    var result = PANIOLO.validate(TestPdfs.pdfA2b());
    assertFalse(result.isValid());
    assertTrue(result.messages()
                     .stream()
                     .anyMatch(message -> "FX-NO-INVOICE".equals(message.ruleId())),
               () -> result.messages()
                           .toString());
  }

  @Test
  void findsInvoiceThroughAssociatedFilesOnly()
      throws Exception {
    var invoice = FacturXInvoices.invoice(FacturXProfile.EN16931,
                                          InvoiceTypeCode.CODE_380)
                                 .build();
    byte[] pdf = PANIOLO.toFacturX(invoice,
                                   TestPdfs.pdfA2b(),
                                   FacturXProfile.EN16931);
    // Without the name tree the reader must follow the (indirect) /AF references.
    byte[] afOnly;
    try (PDDocument document = Loader.loadPDF(pdf)) {
      document.getDocumentCatalog()
              .getCOSObject()
              .removeItem(COSName.NAMES);
      document.getDocumentCatalog()
              .getCOSObject()
              .getCOSArray(COSName.getPDFName("AF"))
              .setNeedToBeUpdated(true);
      var out = new java.io.ByteArrayOutputStream();
      document.save(out);
      afOnly = out.toByteArray();
    }
    assertEquals(invoice,
                 PANIOLO.read(afOnly));
  }

  @Test
  void detectsPdfHeader() {
    assertTrue(Paniolo.isPdf("%PDF-1.7".getBytes(StandardCharsets.US_ASCII)));
    assertFalse(Paniolo.isPdf("<Invoice/>".getBytes(StandardCharsets.US_ASCII)));
  }
}

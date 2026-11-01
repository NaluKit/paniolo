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
import io.github.nalukit.paniolo.config.InvoiceSyntax;
import io.github.nalukit.paniolo.config.PdfAttachment;
import io.github.nalukit.paniolo.model.common.value.InvoiceTypeCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.verapdf.pdfa.flavours.PDFAFlavour;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Existing XML embedded byte for byte with {@link Paniolo#embedXml(byte[], byte[], FacturXProfile)}.
 */
class EmbedXmlTest {
  private static final Paniolo PANIOLO = Paniolo.create();

  /**
   * CII of another producer: a comment, other indentation and a byte order mark, none of which
   * survives reading into the model and writing again.
   */
  private static byte[] foreignXml(FacturXProfile profile) {
    String xml = PANIOLO.toXml(FacturXInvoices.invoice(profile,
                                                       InvoiceTypeCode.CODE_380)
                                              .build(),
                               InvoiceSyntax.CII)
                        .replace("<rsm:ExchangedDocument>",
                                 "<!-- produced by another system -->\n\t\t<rsm:ExchangedDocument>");
    byte[] bytes = xml.getBytes(StandardCharsets.UTF_8);
    byte[] withBom = new byte[bytes.length + 3];
    withBom[0] = (byte) 0xEF;
    withBom[1] = (byte) 0xBB;
    withBom[2] = (byte) 0xBF;
    System.arraycopy(bytes,
                     0,
                     withBom,
                     3,
                     bytes.length);
    return withBom;
  }

  @ParameterizedTest
  @EnumSource(value = FacturXProfile.class,
              names = { "BASIC",
                        "EN16931",
                        "EXTENDED",
                        "XRECHNUNG" })
  void embedsXmlByteForByteAndValidatesTheResult(FacturXProfile profile) {
    byte[] xml    = foreignXml(profile);
    var    result = PANIOLO.embedXml(TestPdfs.pdfA2b(),
                                     xml,
                                     profile);
    assertTrue(result.isValid(),
               () -> result.validation()
                           .messages()
                           .toString());
    assertArrayEquals(xml,
                      PANIOLO.extractXml(result.pdf()));
    assertEquals(List.of(),
                 TestPdfs.pdfaFailures(result.pdf(),
                                       PDFAFlavour.PDFA_3_B));
    // The same validation as for any received PDF.
    assertEquals(PANIOLO.validate(result.pdf())
                        .messages(),
                 result.validation()
                       .messages());
  }

  @Test
  void embedsInvalidXmlUnchangedAndReportsIt() {
    // Amounts the model cannot reproduce: toFacturX could not even read this document.
    String xml = new String(foreignXml(FacturXProfile.EN16931),
                            StandardCharsets.UTF_8).replace("<ram:DuePayableAmount>",
                                                            "<ram:DuePayableAmount>1");
    byte[] bytes = xml.getBytes(StandardCharsets.UTF_8);
    assertThrows(IllegalArgumentException.class,
                 () -> PANIOLO.read(bytes));
    var result = PANIOLO.embedXml(TestPdfs.pdfA2b(),
                                  bytes,
                                  FacturXProfile.EN16931,
                                  List.of(PdfAttachment.of("timesheet.txt",
                                                           "8h".getBytes(StandardCharsets.UTF_8),
                                                           "text/plain")));
    assertFalse(result.isValid());
    assertFalse(result.validation()
                      .messages(io.github.nalukit.paniolo.validation.ValidationMessage.Stage.SCHEMATRON)
                      .isEmpty(),
                () -> result.validation()
                            .messages()
                            .toString());
    assertArrayEquals(bytes,
                      PANIOLO.extractXml(result.pdf()));
    assertEquals("timesheet.txt",
                 PANIOLO.attachments(result.pdf())
                        .get(0)
                        .fileName());
  }

  @Test
  void rejectsProfileConflicts() {
    byte[] en16931 = foreignXml(FacturXProfile.EN16931);
    var conflict = assertThrows(IllegalArgumentException.class,
                                () -> PANIOLO.embedXml(TestPdfs.pdfA2b(),
                                                       en16931,
                                                       FacturXProfile.BASIC));
    assertTrue(conflict.getMessage()
                       .contains("BASIC") && conflict.getMessage()
                                                     .contains(FacturXProfile.EN16931.getSpecificationId()),
               conflict.getMessage());
    assertThrows(IllegalArgumentException.class,
                 () -> PANIOLO.embedXml(TestPdfs.pdfA2b(),
                                        en16931,
                                        FacturXProfile.EXTENDED_CTC_FR));
    var ubl = assertThrows(IllegalArgumentException.class,
                           () -> PANIOLO.embedXml(TestPdfs.pdfA2b(),
                                                  PANIOLO.toBytes(FacturXInvoices.invoice(FacturXProfile.XRECHNUNG,
                                                                                          InvoiceTypeCode.CODE_380)
                                                                                 .build(),
                                                                  InvoiceSyntax.UBL),
                                                  FacturXProfile.XRECHNUNG));
    assertTrue(ubl.getMessage()
                  .contains("UBL_INVOICE"),
               ubl.getMessage());
    assertThrows(IllegalArgumentException.class,
                 () -> PANIOLO.embedXml(TestPdfs.pdfA2b(),
                                        "<unclosed>".getBytes(StandardCharsets.UTF_8),
                                        FacturXProfile.EN16931));
  }

  @Test
  void rejectsUnsuitablePdfs() {
    byte[] xml = foreignXml(FacturXProfile.EN16931);
    for (byte[] pdf : List.of("no pdf".getBytes(StandardCharsets.UTF_8),
                              TestPdfs.plainPdf(),
                              TestPdfs.encryptedPdf(),
                              PANIOLO.embedXml(TestPdfs.pdfA2b(),
                                               xml,
                                               FacturXProfile.EN16931)
                                     .pdf())) {
      var rejected = assertThrows(IllegalArgumentException.class,
                                  () -> PANIOLO.embedXml(pdf,
                                                         xml,
                                                         FacturXProfile.EN16931));
      assertFalse(rejected.getMessage()
                          .isBlank());
    }
  }
}

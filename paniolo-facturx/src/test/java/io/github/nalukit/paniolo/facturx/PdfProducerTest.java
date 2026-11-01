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
import io.github.nalukit.paniolo.config.PdfProducer;
import io.github.nalukit.paniolo.model.common.value.InvoiceTypeCode;
import io.github.nalukit.paniolo.validation.Severity;
import org.apache.pdfbox.Loader;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reads the producing software of PDFs and reports it in the validation.
 */
class PdfProducerTest {
  private static final Paniolo PANIOLO = Paniolo.create();

  /**
   * Sets producer and creator of the document information; null removes them.
   */
  private static byte[] withInfo(byte[] pdf,
                                 String producer,
                                 String creator)
      throws IOException {
    try (var document = Loader.loadPDF(pdf)) {
      var info = document.getDocumentInformation();
      info.setProducer(producer);
      info.setCreator(creator);
      var out = new ByteArrayOutputStream();
      document.save(out);
      return out.toByteArray();
    }
  }

  @Test
  void recognizesKnownSoftware() {
    byte[] empty = new byte[0];
    assertEquals("Mustang",
                 PdfProducer.recognize("ACME ERP (via mustangproject.org 2.27.0)",
                                       null,
                                       empty));
    assertEquals("Intarsys",
                 PdfProducer.recognize("intarsys PDF/A Live!",
                                       "intarsys ZUGFeRD Toolkit 2.1",
                                       empty));
    assertEquals("Lexware office",
                 PdfProducer.recognize(null,
                                       "lexoffice",
                                       empty));
    // Signatures may also sit in comments or uncompressed streams, e.g. of Ghostscript.
    assertEquals("Ghostscript",
                 PdfProducer.recognize("GPL Ghostscript 10.03",
                                       null,
                                       "%PDF-1.7\n%%Invocation: gs -sDEVICE=pdfwrite\n".getBytes(StandardCharsets.US_ASCII)));
    assertNull(PdfProducer.recognize("Microsoft Word",
                                     " ",
                                     "%PDF-1.7".getBytes(StandardCharsets.US_ASCII)));
    var unknown = new PdfProducer(" ",
                                  null,
                                  null);
    assertTrue(unknown.isUnknown());
    assertEquals("unknown software",
                 unknown.describe());
    assertEquals("Konik (producer \"Konik Library\", creator \"Billing\")",
                 new PdfProducer("Konik Library",
                                 "Billing",
                                 "Konik").describe());
  }

  @Test
  void signsThePdfsPanioloCreates()
      throws IOException {
    assertEquals("Paniolo (via Paniolo)",
                 PdfProducer.sign(null));
    assertEquals("Word (via Paniolo)",
                 PdfProducer.sign(" Word "));
    assertEquals("Word (via Paniolo)",
                 PdfProducer.sign("Word (via Paniolo)"));
    var invoice = FacturXInvoices.invoice(FacturXProfile.EN16931,
                                          InvoiceTypeCode.CODE_380)
                                 .build();
    byte[] withoutProducer = withInfo(TestPdfs.pdfA2b(),
                                      null,
                                      null);
    byte[] pdf = PANIOLO.toFacturX(invoice,
                                   withoutProducer,
                                   FacturXProfile.EN16931);
    assertEquals(new PdfProducer("Paniolo (via Paniolo)",
                                 null,
                                 "Paniolo"),
                 PANIOLO.producer(pdf));
    assertEquals(java.util.List.of(),
                 TestPdfs.pdfaFailures(pdf,
                                       org.verapdf.pdfa.flavours.PDFAFlavour.PDFA_3_B));
    // Orders and delivery notes are signed the same way.
    assertEquals("Paniolo",
                 PANIOLO.producer(PANIOLO.toOrderXPdf(OrderSamples.basic(io.github.nalukit.paniolo.model.order.OrderTypeCode.ORDER)
                                                                  .build(),
                                                      withoutProducer))
                        .generator());
    assertEquals("Paniolo",
                 PANIOLO.producer(PANIOLO.toDespatchAdvicePdf(DespatchAdvicePdfTest.advice(),
                                                              withoutProducer))
                        .generator());
  }

  @Test
  void readsTheProducerFromInfoAndXmp()
      throws IOException {
    byte[] source = withInfo(TestPdfs.pdfA2b(),
                             "intarsys PDF/A Live!",
                             "Rechnungsprogramm 3");
    assertEquals(new PdfProducer("intarsys PDF/A Live!",
                                 "Rechnungsprogramm 3",
                                 "Intarsys"),
                 PANIOLO.producer(source));
    // The Factur-X document keeps the producer of the source and signs it, in the document
    // information and in XMP.
    byte[] facturX = PANIOLO.toFacturX(FacturXInvoices.invoice(FacturXProfile.EN16931,
                                                               InvoiceTypeCode.CODE_380)
                                                      .build(),
                                       source,
                                       FacturXProfile.EN16931);
    assertEquals(new PdfProducer("intarsys PDF/A Live! (via Paniolo)",
                                 "Rechnungsprogramm 3",
                                 "Paniolo"),
                 PANIOLO.producer(facturX));
    // Without document information the XMP metadata is used.
    var fromXmp = PANIOLO.producer(withInfo(facturX,
                                            null,
                                            null));
    assertEquals("intarsys PDF/A Live! (via Paniolo)",
                 fromXmp.producer());
    assertEquals("Rechnungsprogramm 3",
                 fromXmp.creator());
    assertThrows(IllegalArgumentException.class,
                 () -> PANIOLO.producer("not a PDF".getBytes(StandardCharsets.US_ASCII)));
  }

  @Test
  void reportsTheProducerInTheValidation()
      throws IOException {
    byte[] pdf = PANIOLO.toFacturX(FacturXInvoices.invoice(FacturXProfile.EN16931,
                                                           InvoiceTypeCode.CODE_380)
                                                  .build(),
                                   withInfo(TestPdfs.pdfA2b(),
                                            "ACME ERP (via mustangproject.org 2.27.0)",
                                            null),
                                   FacturXProfile.EN16931);
    var result = PANIOLO.validate(pdf);
    assertTrue(result.isValid(),
               () -> result.messages()
                           .toString());
    var producer = result.messages()
                         .stream()
                         .filter(message -> "FX-PRODUCER".equals(message.ruleId()))
                         .findFirst()
                         .orElseThrow();
    assertEquals(Severity.INFO,
                 producer.severity());
    // The latest processing wins: Paniolo signs the producer of a Mustang PDF.
    assertEquals("Produced by Paniolo (producer \"ACME ERP (via mustangproject.org 2.27.0) (via Paniolo)\")",
                 producer.message());
    assertTrue(result.toXml()
                     .contains("rule=\"FX-PRODUCER\""),
               result::toXml);
  }
}

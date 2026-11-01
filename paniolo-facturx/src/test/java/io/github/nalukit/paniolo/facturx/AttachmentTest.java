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
import io.github.nalukit.paniolo.config.AfRelationship;
import io.github.nalukit.paniolo.config.FacturXProfile;
import io.github.nalukit.paniolo.config.PdfAttachment;
import io.github.nalukit.paniolo.model.common.Attachment;
import io.github.nalukit.paniolo.model.common.value.InvoiceTypeCode;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentNameDictionary;
import org.apache.pdfbox.pdmodel.PDEmbeddedFilesNameTreeNode;
import org.apache.pdfbox.pdmodel.common.filespecification.PDComplexFileSpecification;
import org.apache.pdfbox.pdmodel.common.filespecification.PDEmbeddedFile;
import org.junit.jupiter.api.Test;
import org.verapdf.pdfa.flavours.PDFAFlavour;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AttachmentTest {
  private static final Paniolo PANIOLO = Paniolo.create();

  private static final PdfAttachment TIME_SHEET = new PdfAttachment("stundenzettel.pdf",
                                                                    TestPdfs.pdfA2b(),
                                                                    "application/pdf",
                                                                    AfRelationship.SUPPLEMENT,
                                                                    "Stundenzettel Oktober");
  private static final PdfAttachment SOURCE     = new PdfAttachment("leistungen.csv",
                                                                    "Datum;Stunden\n2026-10-26;2\n".getBytes(StandardCharsets.UTF_8),
                                                                    "text/csv",
                                                                    AfRelationship.SOURCE,
                                                                    null);

  /**
   * A PDF/A document that already carries an attachment, as some PDF producers create it.
   */
  private static byte[] pdfWithAttachment(String name)
      throws Exception {
    try (PDDocument document = Loader.loadPDF(TestPdfs.pdfA2b())) {
      var embedded = new PDEmbeddedFile(document,
                                        new ByteArrayInputStream("Hinweis".getBytes(StandardCharsets.UTF_8)),
                                        COSName.FLATE_DECODE);
      embedded.setSubtype("text/plain");
      var file = new PDComplexFileSpecification();
      file.setFile(name);
      file.setFileUnicode(name);
      file.setEmbeddedFile(embedded);
      var tree = new PDEmbeddedFilesNameTreeNode();
      tree.setNames(Map.of(name,
                           file));
      var names = new PDDocumentNameDictionary(document.getDocumentCatalog());
      names.setEmbeddedFiles(tree);
      document.getDocumentCatalog()
              .setNames(names);
      var out = new ByteArrayOutputStream();
      document.save(out);
      return out.toByteArray();
    }
  }

  @Test
  void embedsAndReadsAttachmentsNextToTheInvoice() {
    var invoice = FacturXInvoices.invoice(FacturXProfile.EN16931,
                                          InvoiceTypeCode.CODE_380)
                                 .build();
    byte[] pdf = PANIOLO.toFacturX(invoice,
                                   TestPdfs.pdfA2b(),
                                   FacturXProfile.EN16931,
                                   List.of(TIME_SHEET,
                                           SOURCE));

    assertEquals(List.of(),
                 TestPdfs.pdfaFailures(pdf,
                                       PDFAFlavour.PDFA_3_B));
    assertEquals(List.of(),
                 new PdfBoxFacturXSupport().checkContainer(pdf));
    var result = PANIOLO.validate(pdf);
    assertTrue(result.isValid(),
               () -> result.messages()
                           .toString());
    assertEquals(invoice,
                 PANIOLO.read(pdf));
    assertEquals(List.of(SOURCE,
                         TIME_SHEET),
                 PANIOLO.attachments(pdf)
                        .stream()
                        .sorted(java.util.Comparator.comparing(PdfAttachment::fileName))
                        .toList());
    // Without attachments only the invoice is embedded.
    assertEquals(List.of(),
                 PANIOLO.attachments(PANIOLO.toFacturX(invoice,
                                                       TestPdfs.pdfA2b(),
                                                       FacturXProfile.EN16931)));
  }

  @Test
  void keepsAttachmentsOfTheInputAndRejectsTheirNames()
      throws Exception {
    var invoice = FacturXInvoices.invoice(FacturXProfile.EN16931,
                                          InvoiceTypeCode.CODE_380)
                                 .build();
    byte[] input = pdfWithAttachment("hinweis.txt");
    byte[] pdf = PANIOLO.toFacturX(invoice,
                                   input,
                                   FacturXProfile.EN16931,
                                   List.of(SOURCE));
    assertEquals(List.of("hinweis.txt",
                         "leistungen.csv"),
                 PANIOLO.attachments(pdf)
                        .stream()
                        .map(PdfAttachment::fileName)
                        .sorted()
                        .toList());
    var existing = PANIOLO.attachments(pdf)
                          .stream()
                          .filter(attachment -> attachment.fileName()
                                                          .equals("hinweis.txt"))
                          .findFirst()
                          .orElseThrow();
    assertEquals(AfRelationship.UNSPECIFIED,
                 existing.relationship());
    assertEquals("text/plain",
                 existing.mimeType());

    var duplicate = assertThrows(IllegalArgumentException.class,
                                 () -> PANIOLO.toFacturX(invoice,
                                                         input,
                                                         FacturXProfile.EN16931,
                                                         List.of(PdfAttachment.of("hinweis.txt",
                                                                                  new byte[] { 1 },
                                                                                  "text/plain"))));
    assertTrue(duplicate.getMessage()
                        .contains("hinweis.txt"),
               duplicate::getMessage);
    assertThrows(IllegalArgumentException.class,
                 () -> PANIOLO.toFacturX(invoice,
                                         TestPdfs.pdfA2b(),
                                         FacturXProfile.EN16931,
                                         List.of(SOURCE,
                                                 SOURCE)));
  }

  @Test
  void checksAttachmentNamesAndTypes() {
    for (String name : List.of("factur-x.xml",
                               "XRechnung.xml",
                               "ordner/datei.pdf",
                               " ")) {
      assertThrows(IllegalArgumentException.class,
                   () -> PdfAttachment.of(name,
                                          new byte[0],
                                          "application/pdf"),
                   name);
    }
    assertThrows(IllegalArgumentException.class,
                 () -> PdfAttachment.of("datei.pdf",
                                        new byte[0],
                                        "pdf"));
    // Content is copied in and out.
    byte[] content = { 1,
                       2,
                       3 };
    var attachment = PdfAttachment.of("daten.bin",
                                      content,
                                      "application/octet-stream");
    content[0] = 9;
    assertEquals(1,
                 attachment.content()[0]);
  }

  @Test
  void takesSupportingDocumentAttachments() {
    var supporting = new Attachment(TestPdfs.pdfA2b(),
                                    "application/pdf",
                                    "lieferschein.pdf");
    var attachment = PdfAttachment.of(supporting);
    assertEquals("lieferschein.pdf",
                 attachment.fileName());
    assertEquals(AfRelationship.SUPPLEMENT,
                 attachment.relationship());
    assertTrue(java.util.Arrays.equals(supporting.content(),
                                       attachment.content()));
  }
}

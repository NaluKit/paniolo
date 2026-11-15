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
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.filespecification.PDComplexFileSpecification;
import org.apache.pdfbox.pdmodel.common.filespecification.PDEmbeddedFile;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Two file specifications named like the invoice XML are a conflicting container when their content
 * differs; the same specification reached from /AF and the name tree is one invoice.
 */
class ConflictingInvoiceFilesTest {
  private static final Paniolo PANIOLO = Paniolo.create();

  private static byte[] pdf() {
    return PANIOLO.toFacturX(FacturXInvoices.invoice(FacturXProfile.EN16931,
                                                     InvoiceTypeCode.CODE_380)
                                            .build(),
                             TestPdfs.pdfA2b(),
                             FacturXProfile.EN16931);
  }

  private static byte[] withExtraFile(byte[] pdf,
                                      Consumer<COSArray> afChange)
      throws Exception {
    try (PDDocument document = Loader.loadPDF(pdf)) {
      afChange.accept(document.getDocumentCatalog()
                              .getCOSObject()
                              .getCOSArray(COSName.AF));
      var out = new ByteArrayOutputStream();
      document.save(out);
      return out.toByteArray();
    }
  }

  private static PDComplexFileSpecification file(PDDocument document,
                                                 String content)
      throws Exception {
    var embedded = new PDEmbeddedFile(document,
                                      new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)),
                                      COSName.FLATE_DECODE);
    embedded.setSubtype("text/xml");
    var file = new PDComplexFileSpecification();
    file.setFile("factur-x.xml");
    file.setFileUnicode("factur-x.xml");
    file.setEmbeddedFile(embedded);
    return file;
  }

  private static boolean reportsMultipleInvoices(byte[] pdf) {
    return PANIOLO.validate(pdf)
                  .messages()
                  .stream()
                  .anyMatch(message -> message.ruleId()
                                              .equals("FX-MULTIPLE-INVOICES"));
  }

  private byte[] withSecondFile(String content)
      throws Exception {
    byte[] source = pdf();
    try (PDDocument document = Loader.loadPDF(source)) {
      var af = document.getDocumentCatalog()
                       .getCOSObject()
                       .getCOSArray(COSName.AF);
      af.add(file(document,
                  content).getCOSObject());
      var out = new ByteArrayOutputStream();
      document.save(out);
      return out.toByteArray();
    }
  }

  @Test
  void theSameSpecificationFromAfAndNameTreeIsOneInvoice() {
    assertFalse(reportsMultipleInvoices(pdf()));
  }

  @Test
  void theSameSpecificationTwiceInAfIsOneInvoice()
      throws Exception {
    byte[] repeated = withExtraFile(pdf(),
                                    af -> af.add(af.getObject(0)));
    assertFalse(reportsMultipleInvoices(repeated));
  }

  @Test
  void aDifferentSpecificationWithTheSameNameAndContentIsHarmless()
      throws Exception {
    byte[] source = pdf();
    String xml    = new String(PANIOLO.extractXml(source),
                               StandardCharsets.UTF_8);
    assertFalse(reportsMultipleInvoices(withSecondFile(xml)));
  }

  @Test
  void aDifferentSpecificationWithOtherContentIsReported()
      throws Exception {
    byte[] conflicting = withSecondFile("<different-invoice/>");
    assertTrue(reportsMultipleInvoices(conflicting));
    assertFalse(PANIOLO.validate(conflicting)
                       .isValid());
  }
}

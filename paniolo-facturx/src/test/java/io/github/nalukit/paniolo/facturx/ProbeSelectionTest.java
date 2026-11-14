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
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.common.filespecification.PDComplexFileSpecification;
import org.apache.pdfbox.pdmodel.common.filespecification.PDEmbeddedFile;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * With several invoice files the quick probe, the extraction, and the container check agree: the
 * probe asks the file the XMP metadata names and then the others, so an empty answer never hides an
 * invoice that extraction and reading find; the container check still objects.
 */
class ProbeSelectionTest {
  private static final Paniolo PANIOLO = Paniolo.create();

  /**
   * An XRechnung PDF (xrechnung.xml, named in the XMP) with a second file factur-x.xml, which comes
   * first in the fixed order of names, holding something else than an invoice.
   */
  private static byte[] pdfWithOtherFirstFile()
      throws Exception {
    byte[] source = PANIOLO.toFacturX(FacturXInvoices.invoice(FacturXProfile.XRECHNUNG,
                                                              InvoiceTypeCode.CODE_380)
                                                     .build(),
                                      TestPdfs.pdfA2b(),
                                      FacturXProfile.XRECHNUNG);
    try (var document = Loader.loadPDF(source)) {
      var embedded = new PDEmbeddedFile(document,
                                        new ByteArrayInputStream("<not-an-invoice/>".getBytes(StandardCharsets.UTF_8)),
                                        COSName.FLATE_DECODE);
      embedded.setSubtype("text/xml");
      var file = new PDComplexFileSpecification();
      file.setFile("factur-x.xml");
      file.setFileUnicode("factur-x.xml");
      file.setEmbeddedFile(embedded);
      document.getDocumentCatalog()
              .getCOSObject()
              .getCOSArray(COSName.AF)
              .add(file.getCOSObject());
      var out = new ByteArrayOutputStream();
      document.save(out);
      return out.toByteArray();
    }
  }

  @Test
  void theProbeFindsTheInvoiceThatExtractionReads()
      throws Exception {
    byte[] pdf = pdfWithOtherFirstFile();
    assertTrue(new String(PANIOLO.extractXml(pdf),
                          StandardCharsets.UTF_8).contains("CrossIndustryInvoice"));
    assertTrue(PANIOLO.probe(pdf)
                      .isPresent());
    assertTrue(PANIOLO.isEInvoice(pdf));
  }

  @Test
  void theContainerCheckStillObjectsToSeveralInvoiceFiles()
      throws Exception {
    var result = PANIOLO.validate(pdfWithOtherFirstFile());
    assertFalse(result.isValid());
    assertTrue(result.messages()
                     .stream()
                     .anyMatch(message -> message.ruleId()
                                                 .equals("FX-MULTIPLE-INVOICES")));
  }

  @Test
  void aPdfWithoutAnyInvoiceStaysEmpty()
      throws Exception {
    assertEquals(java.util.Optional.empty(),
                 PANIOLO.probe(TestPdfs.pdfA2b()));
  }
}

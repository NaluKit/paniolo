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
package io.github.nalukit.paniolo.pdfa;

import io.github.nalukit.paniolo.Paniolo;
import io.github.nalukit.paniolo.config.FacturXProfile;
import io.github.nalukit.paniolo.config.PdfLayout;
import io.github.nalukit.paniolo.config.PdfProfile;
import io.github.nalukit.paniolo.model.common.value.InvoiceTypeCode;
import io.github.nalukit.paniolo.validation.ValidationMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import org.apache.pdfbox.Loader;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The profiles of {@code toPdf}: preview, PDF/A-1b as visual part for {@code toFacturX}, PDF/A-3b.
 */
class PdfProfileTest {
  private static final Paniolo         PANIOLO = Paniolo.create();
  private static final VeraPdfaSupport PDFA    = new VeraPdfaSupport();

  private static io.github.nalukit.paniolo.model.Invoice invoice() {
    return FacturXInvoices.invoice(FacturXProfile.EN16931,
                                   InvoiceTypeCode.CODE_380)
                          .build();
  }

  private static byte[] transparentLogo()
      throws IOException {
    var image = new BufferedImage(120,
                                  40,
                                  BufferedImage.TYPE_INT_ARGB);
    for (int x = 0; x < 120; x++) {
      for (int y = 0; y < 40; y++) {
        image.setRGB(x,
                     y,
                     x < 60 ?
                     0x80019ABC :
                     0x00000000);
      }
    }
    var out = new ByteArrayOutputStream();
    ImageIO.write(image,
                  "png",
                  out);
    return out.toByteArray();
  }

  private static List<String> pdfA1Findings(byte[] pdf) {
    return PDFA.validate(pdf)
               .stream()
               .map(ValidationMessage::ruleId)
               .filter(rule -> !rule.equals("PDFA-PART"))
               .toList();
  }

  @Test
  void pdfA1bConformsToPart1() {
    byte[] pdf = PANIOLO.toPdf(invoice(),
                               PdfLayout.defaults()
                                        .withProfile(PdfProfile.PDF_A_1B));
    assertTrue(new String(pdf,
                          StandardCharsets.ISO_8859_1).startsWith("%PDF-1.4"));
    assertEquals(List.of(),
                 pdfA1Findings(pdf));
    assertTrue(PDFA.validate(pdf)
                   .stream()
                   .anyMatch(m -> m.ruleId()
                                   .equals("PDFA-PART")));
  }

  @Test
  void pdfA1bWithWatermarkAndTransparentLogoConformsToPart1()
      throws IOException {
    byte[] pdf = PANIOLO.toPdf(invoice(),
                               PdfLayout.defaults()
                                        .withLogo(transparentLogo())
                                        .withShowLogo(true)
                                        .withWatermark("ENTWURF")
                                        .withProfile(PdfProfile.PDF_A_1B));
    assertEquals(List.of(),
                 pdfA1Findings(pdf));
  }

  @Test
  void pdfA1bIsAcceptedByToFacturX() {
    var invoice = invoice();
    byte[] a1 = PANIOLO.toPdf(invoice,
                              PdfLayout.defaults()
                                       .withProfile(PdfProfile.PDF_A_1B));
    byte[] facturX = PANIOLO.toFacturX(invoice,
                                       a1,
                                       FacturXProfile.EN16931);
    var result = PANIOLO.validate(facturX);
    assertTrue(result.isValid(),
               () -> result.messages()
                           .toString());
    assertEquals(List.of(),
                 result.messages(ValidationMessage.Stage.PDF_A));
  }

  @Test
  void defaultProfileIsPdfA3b() {
    assertEquals(PdfProfile.PDF_A_3B,
                 PdfLayout.defaults()
                          .profile());
    var pdf = PANIOLO.toPdf(invoice());
    assertEquals(List.of(),
                 PDFA.validate(pdf));
  }

  @Test
  void previewIsAPlainPdf()
      throws IOException {
    byte[] pdf = PANIOLO.toPdf(invoice(),
                               PdfLayout.defaults()
                                        .withWatermark("PREVIEW")
                                        .withProfile(PdfProfile.PREVIEW));
    String text = new String(pdf,
                             StandardCharsets.ISO_8859_1);
    try (var document = Loader.loadPDF(pdf)) {
      assertEquals(1.7f,
                   document.getVersion());
      assertEquals(0,
                   document.getDocumentCatalog()
                           .getOutputIntents()
                           .size());
    }
    assertFalse(text.contains("pdfaid"));
    assertTrue(PDFA.validate(pdf)
                   .stream()
                   .anyMatch(m -> m.ruleId()
                                   .equals("PDFA-NOT-DECLARED")));
  }

  @Test
  void preview20IsAPlainPdf20()
      throws IOException {
    byte[] pdf = PANIOLO.toPdf(invoice(),
                               PdfLayout.defaults()
                                        .withWatermark("PREVIEW")
                                        .withProfile(PdfProfile.PREVIEW_2_0));
    assertTrue(new String(pdf,
                          StandardCharsets.ISO_8859_1).startsWith("%PDF-2.0"));
    try (var document = Loader.loadPDF(pdf)) {
      assertEquals(2.0f,
                   document.getVersion());
      assertEquals(1,
                   document.getNumberOfPages());
      assertEquals(2.0f,
                   document.getDocumentCatalog()
                           .getVersion() == null ?
                   0f :
                   Float.parseFloat(document.getDocumentCatalog()
                                            .getVersion()));
    }
  }

  @ParameterizedTest
  @EnumSource(value = PdfProfile.class,
              names = { "PREVIEW",
                        "PREVIEW_2_0" })
  void hybridMethodsRejectThePreview(PdfProfile profile) {
    var layout = PdfLayout.defaults()
                          .withProfile(profile);
    assertThrows(IllegalArgumentException.class,
                 () -> PANIOLO.toFacturX(invoice(),
                                         FacturXProfile.EN16931,
                                         layout,
                                         List.of()));
  }
}

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
package io.github.nalukit.paniolo.pdflayout;

import io.github.nalukit.paniolo.Paniolo;
import io.github.nalukit.paniolo.config.PdfLayout;
import io.github.nalukit.paniolo.model.common.InvoiceLine;
import io.github.nalukit.paniolo.model.common.TaxCategory;
import io.github.nalukit.paniolo.model.quotation.Quotation;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.verapdf.gf.foundry.VeraGreenfieldFoundryProvider;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuotationLayoutTest {
  private static final Paniolo     PANIOLO = Paniolo.create();
  private static final Path        OUT     = Path.of("target",
                                                     "rendered");
  private static final TaxCategory VAT     = TaxCategory.standard(new BigDecimal("19"));

  @BeforeAll
  static void setUp()
      throws IOException {
    VeraGreenfieldFoundryProvider.initialise();
    Files.createDirectories(OUT);
  }

  private static String text(byte[] pdf) {
    try (var document = Loader.loadPDF(pdf)) {
      return new PDFTextStripper().getText(document)
                                  .replace(' ',
                                           ' ')
                                  .replace(' ',
                                           ' ');
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private static InvoiceLine line(String id,
                                  String name,
                                  String price) {
    return InvoiceLine.create()
                      .id(id)
                      .name(name)
                      .quantity(new BigDecimal("2"))
                      .unitCode("HUR")
                      .netUnitPrice(new BigDecimal(price))
                      .tax(VAT)
                      .build();
  }

  private static Quotation quotation() {
    return Quotation.create()
                    .number("ANG-2026-007")
                    .issueDate(LocalDate.of(2026,
                                            11,
                                            2))
                    .validUntil(LocalDate.of(2026,
                                             12,
                                             2))
                    .seller(TestInvoices.seller())
                    .buyer(TestInvoices.buyer())
                    .buyerReference("ANF-12")
                    .subject("Beratung Release 5")
                    .deliveryTerms("Beginn in KW 48")
                    .paymentTerms("14 Tage netto")
                    .addNote("Preise freibleibend")
                    .addLine(line("1",
                                  "Beratung",
                                  "100"))
                    .addOptionalLine(line("2",
                                          "Schulung",
                                          "50"))
                    .build();
  }

  @Test
  @DisplayName("renders a quotation as PDF/A-3b with validity, optional lines, and without payment")
  void rendersQuotation()
      throws IOException {
    byte[] pdf = PANIOLO.toPdf(quotation());
    Files.write(OUT.resolve("quotation.pdf"),
                pdf);
    assertEquals(List.of(),
                 PdfLayoutTest.pdfaFailures(pdf));
    String text = text(pdf);
    assertTrue(text.contains("Angebot"),
               text);
    assertTrue(text.contains("Angebotsnummer ANG-2026-007"),
               text);
    assertTrue(text.contains("Angebotsdatum"),
               text);
    assertTrue(text.contains("Gültig bis"),
               text);
    assertTrue(text.contains("Beratung Release 5"),
               text);
    assertTrue(text.contains("Optionale Positionen"),
               text);
    assertTrue(text.contains("Schulung"),
               text);
    assertTrue(text.contains("Beginn in KW 48"),
               text);
    assertTrue(text.contains("14 Tage netto"),
               text);
    // 2 x 100 = 200 net; the optional line is not in the total: 238.00 gross.
    assertTrue(text.contains("238,00"),
               text);
    assertFalse(text.contains("297,50"),
                text);
    assertFalse(text.contains("Rechnungsnummer"),
                text);
    assertFalse(text.contains("Zahlbetrag"),
                text);
    assertFalse(text.contains("IBAN"),
                text);
  }

  @Test
  @DisplayName("renders a quotation in English")
  void rendersEnglish()
      throws IOException {
    byte[] pdf = PANIOLO.toPdf(quotation(),
                               PdfLayout.of(Locale.ENGLISH));
    String text = text(pdf);
    assertTrue(text.contains("Quotation no. ANG-2026-007"),
               text);
    assertTrue(text.contains("Valid until"),
               text);
  }
}

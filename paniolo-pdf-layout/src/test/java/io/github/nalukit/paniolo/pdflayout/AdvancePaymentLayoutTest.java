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
import io.github.nalukit.paniolo.model.Invoice;
import io.github.nalukit.paniolo.model.common.PrecedingInvoiceReference;
import io.github.nalukit.paniolo.model.common.ProcessControl;
import io.github.nalukit.paniolo.model.extended.AdvancePayment;
import io.github.nalukit.paniolo.model.extended.ExtendedInformation;
import io.github.nalukit.paniolo.model.extended.PaymentTerm;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.verapdf.gf.foundry.VeraGreenfieldFoundryProvider;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * EXTENDED advance payments and further payment terms appear in the payment section, apart from
 * preceding invoices, in every language (Mustang issues #880, #956, and #529).
 */
class AdvancePaymentLayoutTest {
  private static final Paniolo PANIOLO = Paniolo.create();

  @BeforeAll
  static void verapdf() {
    VeraGreenfieldFoundryProvider.initialise();
  }

  private static String text(byte[] pdf) {
    try (var document = Loader.loadPDF(pdf)) {
      return new PDFTextStripper().getText(document)
                                  .replace(' ',
                                           ' ')
                                  .replace(' ',
                                           ' ');
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static Invoice invoice() {
    return TestInvoices.simple("V-1")
                       .processControl(ProcessControl.facturXExtended())
                       .addPrecedingInvoiceReference(new PrecedingInvoiceReference("VR-0",
                                                                                   LocalDate.of(2026,
                                                                                                8,
                                                                                                1)))
                       .extended(ExtendedInformation.create()
                                                    .addAdvancePayment(new AdvancePayment(new BigDecimal("1000.00"),
                                                                                          LocalDate.of(2026,
                                                                                                       9,
                                                                                                       1),
                                                                                          TestInvoices.VAT_19,
                                                                                          new BigDecimal("159.66"),
                                                                                          "AZ-1"))
                                                    .addAdvancePayment(new AdvancePayment(new BigDecimal("500.00"),
                                                                                          LocalDate.of(2026,
                                                                                                       10,
                                                                                                       1),
                                                                                          TestInvoices.VAT_19,
                                                                                          new BigDecimal("79.83"),
                                                                                          "AZ-2"))
                                                    .addPaymentTerm(new PaymentTerm("Rate 1",
                                                                                    LocalDate.of(2026,
                                                                                                 11,
                                                                                                 10),
                                                                                    new BigDecimal("100.00"),
                                                                                    null,
                                                                                    null))
                                                    .addPaymentTerm(new PaymentTerm("Rate 2",
                                                                                    LocalDate.of(2026,
                                                                                                 12,
                                                                                                 10),
                                                                                    new BigDecimal("138.00"),
                                                                                    null,
                                                                                    null))
                                                    .build())
                       .build();
  }

  @Test
  void showsAdvancePaymentsAndFurtherTermsInEveryLanguage() {
    var invoice = invoice();
    Map<Locale, List<String>> expected = Map.of(Locale.GERMAN,
                                                List.of("Vorauszahlung",
                                                        "Weitere Zahlungsbedingung",
                                                        "1.000,00",
                                                        "01.09.2026",
                                                        "Rechnung AZ-1",
                                                        "darin USt",
                                                        "fällig am 10.12.2026"),
                                                Locale.ENGLISH,
                                                List.of("Advance payment",
                                                        "Further payment term",
                                                        "invoice AZ-2",
                                                        "Rate 2"),
                                                Locale.FRENCH,
                                                List.of("Acompte",
                                                        "Autre condition de paiement",
                                                        "facture AZ-1",
                                                        "Rate 1"));
    expected.forEach((locale, texts) -> {
      String text = text(PANIOLO.toPdf(invoice,
                                       PdfLayout.of(locale)));
      for (String expectedText : texts) {
        assertTrue(text.contains(expectedText),
                   () -> locale + ": " + expectedText + " in\n" + text);
      }
      // Two advance payments; the preceding invoice stays a reference and is no payment.
      String label = texts.getFirst();
      var payments = text.lines()
                         .filter(line -> line.startsWith(label))
                         .toList();
      assertEquals(2,
                   payments.size(),
                   text);
      assertFalse(payments.stream()
                          .anyMatch(line -> line.contains("VR-0")),
                  text);
      assertTrue(text.contains("VR-0"),
                 text);
    });
  }

  /**
   * Manufacturer item number, delivery date, and delivery note of an EXTENDED line (Mustang issue
   * #1331); a line without them shows none.
   */
  @Test
  void showsTheExtendedDetailsOfALine() {
    var line = io.github.nalukit.paniolo.model.common.InvoiceLine.create()
                                                                .id("3")
                                                                .name("Kaffeemaschine")
                                                                .quantity(BigDecimal.ONE)
                                                                .unitCode("H87")
                                                                .netUnitPrice(new BigDecimal("50.00"))
                                                                .tax(TestInvoices.VAT_19)
                                                                .extended(io.github.nalukit.paniolo.model.extended.LineExtension.create()
                                                                                                                                 .industryItemId("HX-77")
                                                                                                                                 .actualDeliveryDate(LocalDate.of(2026,
                                                                                                                                                                  10,
                                                                                                                                                                  24))
                                                                                                                                 .deliveryNoteReference(new io.github.nalukit.paniolo.model.extended.DocumentReference("LS-9",
                                                                                                                                                                                                                       null,
                                                                                                                                                                                                                       null))
                                                                                                                                 .build())
                                                                .build();
    String text = text(PANIOLO.toPdf(TestInvoices.simple("V-2")
                                                 .processControl(ProcessControl.facturXExtended())
                                                 .addLine(line)
                                                 .build(),
                                     PdfLayout.of(Locale.GERMAN)));
    for (String expected : List.of("Hersteller-Art.-Nr. HX-77",
                                   "Geliefert 24.10.2026",
                                   "Lieferschein LS-9")) {
      assertTrue(text.contains(expected),
                 () -> expected + " in\n" + text);
    }
    String plain = text(PANIOLO.toPdf(invoice(),
                                      PdfLayout.of(Locale.GERMAN)));
    assertFalse(plain.contains("Hersteller-Art.-Nr.") || plain.contains("Lieferschein"),
                plain);
  }
}

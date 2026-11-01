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
package io.github.nalukit.paniolo;

import io.github.nalukit.paniolo.config.InvoiceSyntax;
import io.github.nalukit.paniolo.model.Invoice;
import io.github.nalukit.paniolo.model.common.DocumentAdjustment;
import io.github.nalukit.paniolo.model.common.InvoiceLine;
import io.github.nalukit.paniolo.model.common.LineAdjustment;
import io.github.nalukit.paniolo.model.common.TaxCategory;
import io.github.nalukit.paniolo.model.common.VatBreakdown;
import io.github.nalukit.paniolo.model.common.value.InvoiceTypeCode;
import io.github.nalukit.paniolo.xml.ReadMode;
import io.github.nalukit.paniolo.xml.ReadWarning;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Percentages of allowances and charges are written with the base amount they refer to
 * (PEPPOL-EN16931-R041), never without it (Mustang issues #1316 and #640).
 */
class AdjustmentPercentageTest {
  private static final Paniolo     PANIOLO = Paniolo.create();
  private static final TaxCategory VAT_19  = TaxCategory.standard(new BigDecimal("19"));
  private static final TaxCategory VAT_7   = TaxCategory.standard(new BigDecimal("7"));

  private static byte[] fixture(String name) throws Exception {
    try (InputStream input = AdjustmentPercentageTest.class.getResourceAsStream("/regression/mustang/" + name)) {
      return input.readAllBytes();
    }
  }

  private static void valid(Invoice invoice) {
    for (var syntax : List.of(InvoiceSyntax.UBL,
                              InvoiceSyntax.CII)) {
      var result = PANIOLO.validate(PANIOLO.toXml(invoice,
                                                  syntax));
      assertTrue(result.isValid(),
                 () -> syntax + ": " + result.messages());
    }
  }

  private static DocumentAdjustment adjustment(boolean charge,
                                               String amount,
                                               String base,
                                               String percent) {
    return new DocumentAdjustment(charge,
                                  new BigDecimal(amount),
                                  base == null ?
                                  null :
                                  new BigDecimal(base),
                                  percent == null ?
                                  null :
                                  new BigDecimal(percent),
                                  charge ?
                                  "Zuschlag" :
                                  "Rabatt",
                                  null,
                                  VAT_19);
  }

  @Test
  void writesThePercentageWithItsBaseAmount() throws Exception {
    // The archived control invoice without allowance is valid in both syntaxes.
    valid(PANIOLO.read(fixture("base.xml")));
    for (boolean charge : List.of(false,
                                  true)) {
      var invoice = TestInvoices.builder(InvoiceTypeCode.CODE_380,
                                         "P-1")
                                .addAdjustment(adjustment(charge,
                                                          "20.00",
                                                          "200.00",
                                                          "10"))
                                .build();
      for (var syntax : List.of(InvoiceSyntax.UBL,
                                InvoiceSyntax.CII)) {
        String xml = PANIOLO.toXml(invoice,
                                   syntax);
        assertTrue(xml.contains("200.00") && xml.contains(">10<"),
                   xml);
      }
      valid(invoice);
    }
  }

  @Test
  void rejectsAPercentageWithoutBaseAmount() {
    for (boolean charge : List.of(false,
                                  true)) {
      var document = assertThrows(IllegalArgumentException.class,
                                  () -> adjustment(charge,
                                                   "20.00",
                                                   null,
                                                   "10"));
      assertTrue(document.getMessage()
                         .contains("BT-93"),
                 document::getMessage);
      var line = assertThrows(IllegalArgumentException.class,
                              () -> new LineAdjustment(charge,
                                                       new BigDecimal("20.00"),
                                                       null,
                                                       new BigDecimal("10"),
                                                       "Rabatt",
                                                       null));
      assertTrue(line.getMessage()
                     .contains("BT-137"),
                 line::getMessage);
    }
  }

  @Test
  void readsAPercentageWithoutBaseAmountOnlyLeniently() throws Exception {
    for (String name : List.of("percent-no-base-CII.xml",
                               "percent-no-base-UBL.xml")) {
      byte[] xml = fixture(name);
      var strict = assertThrows(IllegalArgumentException.class,
                                () -> PANIOLO.read(xml,
                                                   ReadMode.STRICT));
      assertTrue(strict.getMessage()
                       .contains("percentage 10 has no base amount"),
                 strict::getMessage);
      var lenient = PANIOLO.read(xml,
                                 ReadMode.LENIENT);
      var warning = lenient.warnings()
                           .stream()
                           .filter(each -> each.code()
                                               .equals(ReadWarning.PERCENTAGE))
                           .findFirst()
                           .orElseThrow();
      assertTrue(warning.message()
                        .contains("percentage 10"),
                 warning::message);
      var adjustment = lenient.invoice()
                              .adjustments()
                              .getFirst();
      // The stated amount is kept; the percentage is reported, not written without base.
      assertEquals(0,
                   new BigDecimal("20.00").compareTo(adjustment.amount()));
      assertNull(adjustment.percentage());
      valid(lenient.invoice());
    }
  }

  @Test
  void changesOnlyTheTaxBaseOfItsCategory() {
    var invoice = TestInvoices.builder(InvoiceTypeCode.CODE_380,
                                       "P-2")
                              .addLine(InvoiceLine.create()
                                                  .id("2")
                                                  .name("Fachbuch")
                                                  .quantity(new BigDecimal("1"))
                                                  .unitCode("H87")
                                                  .netUnitPrice(new BigDecimal("100.00"))
                                                  .tax(VAT_7)
                                                  .build())
                              .addAdjustment(adjustment(false,
                                                        "20.00",
                                                        "200.00",
                                                        "10"))
                              .build();
    var breakdowns = invoice.totals()
                            .vatBreakdowns();
    // 10 % of the 19 % lines only; the 7 % line keeps its base.
    assertEquals(0,
                 new BigDecimal("180.00").compareTo(breakdown(breakdowns,
                                                              "19").taxableAmount()));
    assertEquals(0,
                 new BigDecimal("100.00").compareTo(breakdown(breakdowns,
                                                              "7").taxableAmount()));
    valid(invoice);
  }

  private static VatBreakdown breakdown(List<VatBreakdown> breakdowns,
                                        String rate) {
    return breakdowns.stream()
                     .filter(each -> each.tax()
                                         .rate()
                                         .compareTo(new BigDecimal(rate)) == 0)
                     .findFirst()
                     .orElseThrow();
  }

  @Test
  void keepsZeroAndNegativePercentagesWithTheirBase() {
    var zero = adjustment(false,
                          "0.00",
                          "200.00",
                          "0");
    assertEquals(0,
                 BigDecimal.ZERO.compareTo(zero.percentage()));
    // A correction states a negative amount of a negative base.
    var correction = adjustment(false,
                                "-20.00",
                                "-200.00",
                                "10");
    assertEquals(0,
                 new BigDecimal("-200.00").compareTo(correction.baseAmount()));
    var line = new LineAdjustment(false,
                                  new BigDecimal("20.00"),
                                  new BigDecimal("200.00"),
                                  new BigDecimal("10"),
                                  "Rabatt",
                                  null);
    var invoice = TestInvoices.builder(InvoiceTypeCode.CODE_380,
                                       "P-3")
                              .addLine(InvoiceLine.create()
                                                  .id("2")
                                                  .name("Wartung")
                                                  .quantity(new BigDecimal("2"))
                                                  .unitCode("HUR")
                                                  .netUnitPrice(new BigDecimal("100.00"))
                                                  .tax(VAT_19)
                                                  .addAdjustment(line)
                                                  .build())
                              .addAdjustment(zero)
                              .build();
    valid(invoice);
  }
}

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
import io.github.nalukit.paniolo.model.common.InvoiceTotals;
import io.github.nalukit.paniolo.model.common.TaxCategory;
import io.github.nalukit.paniolo.model.common.VatBreakdown;
import io.github.nalukit.paniolo.model.common.value.InvoiceTypeCode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An invoice with one line at 19 % and one at 7 % VAT, without document level allowances and
 * charges, with a 10 % charge, with a 10 % allowance, and with a 20 % charge plus a 15 %
 * allowance. A document level allowance or charge has exactly one VAT category (BT-95 / BT-102),
 * so each percentage is stated once per VAT rate with the line net sum of that rate as base
 * amount (BT-93 / BT-100). Charge and allowance both refer to that base; they are not applied one
 * after the other. The expected values are documented in docs/testcases/mixed-vat-adjustments.md.
 */
class MixedVatAdjustmentsTest {
  private static final Paniolo     PANIOLO = Paniolo.create();
  private static final TaxCategory VAT_19  = TaxCategory.standard(new BigDecimal("19"));
  private static final TaxCategory VAT_7   = TaxCategory.standard(new BigDecimal("7"));

  /**
   * Line 1 (from {@link TestInvoices}): 2 HUR consulting at 100.00, 19 % = 200.00 net.
   * Line 2: 1 H87 technical book at 100.00, 7 % = 100.00 net.
   */
  private static Invoice.Builder invoice(String number) {
    return TestInvoices.builder(InvoiceTypeCode.CODE_380,
                                number)
                       .addLine(InvoiceLine.create()
                                           .id("2")
                                           .name("Fachbuch")
                                           .quantity(new BigDecimal("1"))
                                           .unitCode("H87")
                                           .netUnitPrice(new BigDecimal("100.00"))
                                           .tax(VAT_7)
                                           .build());
  }

  /**
   * One allowance or charge per VAT rate: the percentage of the line net sum of that rate.
   */
  private static Invoice.Builder percent(Invoice.Builder invoice,
                                         boolean charge,
                                         String percent) {
    return invoice.addAdjustment(adjustment(charge,
                                            "200.00",
                                            percent,
                                            VAT_19))
                  .addAdjustment(adjustment(charge,
                                            "100.00",
                                            percent,
                                            VAT_7));
  }

  private static DocumentAdjustment adjustment(boolean charge,
                                               String base,
                                               String percent,
                                               TaxCategory tax) {
    var baseAmount = new BigDecimal(base);
    var percentage = new BigDecimal(percent);
    return new DocumentAdjustment(charge,
                                  baseAmount.multiply(percentage)
                                            .movePointLeft(2),
                                  baseAmount,
                                  percentage,
                                  charge ?
                                  "Zuschlag " + percent + " %" :
                                  "Nachlass " + percent + " %",
                                  null,
                                  tax);
  }

  @Test
  void withoutAllowancesAndCharges() {
    check(invoice("MV-1").build(),
          "300.00",
          "0.00",
          "0.00",
          "200.00",
          "38.00",
          "100.00",
          "7.00",
          "300.00",
          "45.00",
          "345.00");
  }

  @Test
  void withTenPercentCharge() {
    check(percent(invoice("MV-2"),
                  true,
                  "10").build(),
          "300.00",
          "0.00",
          "30.00",
          "220.00",
          "41.80",
          "110.00",
          "7.70",
          "330.00",
          "49.50",
          "379.50");
  }

  @Test
  void withTenPercentAllowance() {
    check(percent(invoice("MV-3"),
                  false,
                  "10").build(),
          "300.00",
          "30.00",
          "0.00",
          "180.00",
          "34.20",
          "90.00",
          "6.30",
          "270.00",
          "40.50",
          "310.50");
  }

  @Test
  void withTwentyPercentChargeAndFifteenPercentAllowance() {
    check(percent(percent(invoice("MV-4"),
                          true,
                          "20"),
                  false,
                  "15").build(),
          "300.00",
          "45.00",
          "60.00",
          "210.00",
          "39.90",
          "105.00",
          "7.35",
          "315.00",
          "47.25",
          "362.25");
  }

  private static void check(Invoice invoice,
                            String lineTotal,
                            String allowanceTotal,
                            String chargeTotal,
                            String taxable19,
                            String tax19,
                            String taxable7,
                            String tax7,
                            String net,
                            String tax,
                            String gross) {
    assertTotals(invoice.totals(),
                 lineTotal,
                 allowanceTotal,
                 chargeTotal,
                 taxable19,
                 tax19,
                 taxable7,
                 tax7,
                 net,
                 tax,
                 gross);
    for (var syntax : List.of(InvoiceSyntax.UBL,
                              InvoiceSyntax.CII)) {
      String xml = PANIOLO.toXml(invoice,
                                 syntax);
      // XML schema, EN 16931 and XRechnung rules (KoSIT) and the calculation check.
      var result = PANIOLO.validate(xml);
      assertTrue(result.profileName()
                       .contains("XRechnung"),
                 result::profileName);
      assertTrue(result.isValid() && result.messages()
                                           .isEmpty(),
                 () -> syntax + ": " + result.messages());
      // The written totals are read back unchanged.
      assertTotals(PANIOLO.read(xml)
                          .totals(),
                   lineTotal,
                   allowanceTotal,
                   chargeTotal,
                   taxable19,
                   tax19,
                   taxable7,
                   tax7,
                   net,
                   tax,
                   gross);
    }
  }

  private static void assertTotals(InvoiceTotals totals,
                                   String lineTotal,
                                   String allowanceTotal,
                                   String chargeTotal,
                                   String taxable19,
                                   String tax19,
                                   String taxable7,
                                   String tax7,
                                   String net,
                                   String tax,
                                   String gross) {
    assertAmount(lineTotal,
                 totals.lineTotal(),
                 "line total (BT-106)");
    assertAmount(allowanceTotal,
                 totals.allowanceTotal(),
                 "allowance total (BT-107)");
    assertAmount(chargeTotal,
                 totals.chargeTotal(),
                 "charge total (BT-108)");
    assertEquals(2,
                 totals.vatBreakdowns()
                       .size());
    assertAmount(taxable19,
                 breakdown(totals,
                           "19").taxableAmount(),
                 "19 % taxable amount (BT-116)");
    assertAmount(tax19,
                 breakdown(totals,
                           "19").taxAmount(),
                 "19 % tax amount (BT-117)");
    assertAmount(taxable7,
                 breakdown(totals,
                           "7").taxableAmount(),
                 "7 % taxable amount (BT-116)");
    assertAmount(tax7,
                 breakdown(totals,
                           "7").taxAmount(),
                 "7 % tax amount (BT-117)");
    assertAmount(net,
                 totals.taxExclusiveAmount(),
                 "net (BT-109)");
    assertAmount(tax,
                 totals.taxAmount(),
                 "VAT (BT-110)");
    assertAmount(gross,
                 totals.taxInclusiveAmount(),
                 "gross (BT-112)");
    assertAmount(gross,
                 totals.payableAmount(),
                 "amount due (BT-115)");
  }

  private static void assertAmount(String expected,
                                   BigDecimal actual,
                                   String name) {
    assertEquals(0,
                 new BigDecimal(expected).compareTo(actual),
                 () -> name + ": expected " + expected + " but was " + actual);
  }

  private static VatBreakdown breakdown(InvoiceTotals totals,
                                        String rate) {
    return totals.vatBreakdowns()
                 .stream()
                 .filter(each -> each.tax()
                                     .rate()
                                     .compareTo(new BigDecimal(rate)) == 0)
                 .findFirst()
                 .orElseThrow();
  }
}

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
package io.github.nalukit.paniolo.model.common;

import io.github.nalukit.paniolo.model.common.value.UnitCode;
import io.github.nalukit.paniolo.model.extended.LineExtension;
import io.github.nalukit.paniolo.model.extended.LineSubtype;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InvoiceLineTest {
  private static final TaxCategory VAT = TaxCategory.standard(new BigDecimal("19"));

  private static InvoiceLine.Builder line() {
    return InvoiceLine.create()
                      .id("1")
                      .name("Artikel")
                      .quantity(new BigDecimal("3"))
                      .unitCode(UnitCode.H87)
                      .netUnitPrice(new BigDecimal("10"))
                      .tax(VAT);
  }

  private static InvoiceLine priced(String quantity,
                                    String baseQuantity) {
    return line().quantity(new BigDecimal(quantity))
                 .netUnitPrice(BigDecimal.ONE)
                 .priceBaseQuantity(new BigDecimal(baseQuantity))
                 .build();
  }

  @Test
  void theNetAmountIsRoundedOnceAtTheHalfCent() {
    // exactly 0.004999999997500000001249..: below the half cent, although a first rounding to ten
    // decimals would give 0.0050000000
    assertEquals(new BigDecimal("0.00"),
                 priced("1",
                        "200.0000001").netAmount());
    assertEquals(new BigDecimal("0.01"),
                 priced("1",
                        "200").netAmount());
    assertEquals(new BigDecimal("0.01"),
                 priced("1",
                        "199.9999999").netAmount());
    assertEquals(new BigDecimal("0.00"),
                 priced("-1",
                        "200.0000001").netAmount());
    assertEquals(new BigDecimal("-0.01"),
                 priced("-1",
                        "200").netAmount());
    assertEquals(new BigDecimal("-0.01"),
                 priced("-1",
                        "199.9999999").netAmount());
  }

  @Test
  void adjustmentsAreAddedBeforeTheOnlyRounding() {
    var charge = LineAdjustment.createCharge()
                               .amount(new BigDecimal("0.01"))
                               .reason("Verpackung")
                               .build();
    var allowance = LineAdjustment.createAllowance()
                                  .amount(new BigDecimal("0.01"))
                                  .reason("Rabatt")
                                  .build();
    var base = priced("1",
                      "200.0000001");
    assertEquals(new BigDecimal("0.01"),
                 base.toBuilder()
                     .addAdjustment(charge)
                     .build()
                     .netAmount());
    assertEquals(new BigDecimal("-0.01"),
                 base.toBuilder()
                     .addAdjustment(allowance)
                     .build()
                     .netAmount());
  }

  @Test
  void theToleranceFollowsTheDecimalsOfTheNetPrice() {
    // A price stated with two decimals is rounded to cents: 20 x 2.77 may be off by 20 half cents.
    var cents = line().quantity(new BigDecimal("20"))
                      .netUnitPrice(new BigDecimal("2.77"))
                      .build();
    assertEquals(new BigDecimal("55.46"),
                 cents.withNetAmount(new BigDecimal("55.46"))
                      .netAmount());
    // The same price stated with four decimals is not rounded to cents: 55.46 is not explained.
    var precise = line().quantity(new BigDecimal("20"))
                        .netUnitPrice(new BigDecimal("2.7700"))
                        .build();
    assertEquals(new BigDecimal("55.40"),
                 precise.withNetAmount(new BigDecimal("55.40"))
                        .netAmount());
    assertThrows(IllegalArgumentException.class,
                 () -> precise.withNetAmount(new BigDecimal("55.46")));
    // A large quantity keeps a rounding of the price at the last decimal: 804878.94 x 0.01146 = 9223.9..
    var large = line().quantity(new BigDecimal("804878.94"))
                      .netUnitPrice(new BigDecimal("0.01146"))
                      .build();
    assertEquals(new BigDecimal("9223.92"),
                 large.withNetAmount(new BigDecimal("9223.92"))
                      .netAmount());
    assertThrows(IllegalArgumentException.class,
                 () -> large.withNetAmount(new BigDecimal("9229.00")));
  }

  @Test
  void theCalculatedAmountIgnoresTheStatedAmount() {
    var base = priced("3",
                      "1");
    assertEquals(new BigDecimal("3.00"),
                 base.withNetAmount(new BigDecimal("3.01"))
                     .calculatedNetAmount());
    var subtotal = line().quantity(null)
                         .unitCode((UnitCode) null)
                         .netUnitPrice(null)
                         .tax(null)
                         .extended(LineExtension.create()
                                                .subtype(LineSubtype.GROUP)
                                                .build())
                         .netAmount(new BigDecimal("1000"))
                         .build();
    assertNull(subtotal.calculatedNetAmount());
  }

  @Test
  void aStatedAmountKeepsItsToleranceCheck() {
    var base = priced("3",
                      "1");
    assertEquals(new BigDecimal("3.00"),
                 base.withNetAmount(new BigDecimal("3.00"))
                     .netAmount());
    assertEquals(new BigDecimal("3.01"),
                 base.withNetAmount(new BigDecimal("3.01"))
                     .netAmount());
    assertThrows(IllegalArgumentException.class,
                 () -> base.withNetAmount(new BigDecimal("4")));
  }

  @Test
  void netAmountIncludesLineAllowancesAndCharges() {
    var built = line().addAdjustment(LineAdjustment.createAllowance()
                                                   .amount(new BigDecimal("2.50"))
                                                   .reason("Rabatt")
                                                   .build())
                      .addAdjustment(LineAdjustment.createCharge()
                                                   .amount(new BigDecimal("1"))
                                                   .reason("Verpackung")
                                                   .build())
                      .build();
    assertEquals(new BigDecimal("28.50"),
                 built.netAmount());
    assertEquals(built,
                 built.toBuilder()
                      .build());
    // A stated amount must still be explainable by rounding the net price.
    assertEquals(new BigDecimal("28.51"),
                 built.withNetAmount(new BigDecimal("28.51"))
                      .netAmount());
    assertThrows(IllegalArgumentException.class,
                 () -> built.withNetAmount(new BigDecimal("30.00")));
  }

  @Test
  void enforcesLineRules() {
    assertThrows(IllegalArgumentException.class,
                 () -> line().priceDiscount(BigDecimal.ONE)
                             .build());
    // BR-27 and BR-28 depend on the profile; the invoice checks them (see InvoiceTest).
    assertEquals(new BigDecimal("-1"),
                 line().grossPrice(new BigDecimal("-1"))
                       .build()
                       .grossPrice());
    assertThrows(IllegalArgumentException.class,
                 () -> line().standardItemId("4012345678901",
                                             null)
                             .build());
    assertThrows(IllegalArgumentException.class,
                 () -> line().objectId(null,
                                       "AWV")
                             .build());
    assertThrows(IllegalArgumentException.class,
                 () -> new ItemClassification("123",
                                              null,
                                              null));
    assertThrows(IllegalArgumentException.class,
                 () -> ItemAttribute.of("Farbe",
                                        " "));
    assertThrows(IllegalArgumentException.class,
                 () -> LineAdjustment.createAllowance()
                                     .amount(BigDecimal.ONE)
                                     .build());
    assertThrows(IllegalArgumentException.class,
                 () -> LineAdjustment.createAllowance()
                                     .amount(BigDecimal.ONE)
                                     .reasonCode("XX")
                                     .build());
    assertNull(line().grossPrice(BigDecimal.TEN)
                     .priceDiscount(BigDecimal.ZERO)
                     .build()
                     .priceDiscount());
  }

  @Test
  void copiesListsDefensively() {
    var built = line().addAttribute("Farbe",
                                    "blau")
                      .build();
    assertThrows(UnsupportedOperationException.class,
                 () -> built.attributes()
                            .clear());
  }

  /**
   * An EXTENDED subtotal line without price may leave out quantity and unit, as the sub-invoice
   * (GROUP) lines of a French multi-vendor invoice do; every other line keeps both.
   */
  @Test
  void onlyASubtotalLineWithoutPriceMayOmitTheQuantity() {
    var group = LineExtension.create()
                             .subtype(LineSubtype.GROUP)
                             .build();
    var subtotal = line().quantity(null)
                         .unitCode((UnitCode) null)
                         .netUnitPrice(null)
                         .tax(null)
                         .extended(group)
                         .netAmount(new BigDecimal("1000"))
                         .build();
    assertNull(subtotal.quantity());
    assertEquals(new BigDecimal("1000.00"),
                 subtotal.netAmount());
    assertThrows(NullPointerException.class,
                 () -> line().quantity(null)
                             .unitCode((UnitCode) null)
                             .build());
    assertThrows(NullPointerException.class,
                 () -> line().quantity(null)
                             .unitCode((UnitCode) null)
                             .extended(group)
                             .build());
    // The unit is required for the lines of an invoice, which the invoice checks (BR-23); a
    // sub-line of the XRechnung extension may state a quantity without it.
    assertNull(line().unitCode((UnitCode) null)
                     .build()
                     .unitCode());
  }

  /**
   * A line with sub-lines of the XRechnung extension states no quantity and unit; its amount is that
   * of its sub-lines (BR-DEX-02).
   */
  @Test
  void aLineWithSubLinesMayOmitQuantityAndUnit() {
    var sub = line().id("1.1")
                    .build();
    var parent = line().quantity(null)
                       .unitCode((UnitCode) null)
                       .addSubLine(sub)
                       .build();
    assertNull(parent.quantity());
    assertEquals(sub.netAmount(),
                 parent.netAmount());
  }
}

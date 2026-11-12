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
    assertThrows(NullPointerException.class,
                 () -> line().unitCode((UnitCode) null)
                             .netUnitPrice(null)
                             .tax(null)
                             .extended(group)
                             .build());
  }
}

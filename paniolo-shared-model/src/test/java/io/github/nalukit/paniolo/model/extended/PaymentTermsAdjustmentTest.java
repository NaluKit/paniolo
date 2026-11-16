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
package io.github.nalukit.paniolo.model.extended;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PaymentTermsAdjustmentTest {
  private static PaymentTermsAdjustment adjustment(String base,
                                                   String percent) {
    return new PaymentTermsAdjustment(null,
                                      14,
                                      base == null ?
                                      null :
                                      new BigDecimal(base),
                                      percent == null ?
                                      null :
                                      new BigDecimal(percent),
                                      null);
  }

  @Test
  void calculatesTheDiscountFromBaseAndPercent() {
    assertEquals(new BigDecimal("35.70"),
                 adjustment("1190.00",
                            "3").withComputedAmount()
                                .amount());
  }

  @Test
  void roundsHalfUpToCents() {
    assertEquals(new BigDecimal("0.03"),
                 adjustment("1.00",
                            "2.5").withComputedAmount()
                                  .amount());
    assertEquals(new BigDecimal("0.02"),
                 adjustment("1.00",
                            "2.49").withComputedAmount()
                                   .amount());
    assertEquals(new BigDecimal("33.33"),
                 adjustment("100.00",
                            "33.333").withComputedAmount()
                                     .amount());
  }

  @Test
  void zeroPercentGivesZero() {
    assertEquals(new BigDecimal("0.00"),
                 adjustment("1190.00",
                            "0").withComputedAmount()
                                .amount());
  }

  @Test
  void keepsAnExplicitAmount() {
    var explicit = new PaymentTermsAdjustment(null,
                                              14,
                                              new BigDecimal("1190.00"),
                                              new BigDecimal("3"),
                                              new BigDecimal("30.00"));
    assertSame(explicit,
               explicit.withComputedAmount());
    assertSame(explicit,
               explicit.withComputedAmount(new BigDecimal("5000.00")));
  }

  @Test
  void takesTheBaseFromTheContextOnlyWhenGiven() {
    var withContext = adjustment(null,
                                 "2").withComputedAmount(new BigDecimal("500.00"));
    assertEquals(new BigDecimal("10.00"),
                 withContext.amount());
    assertNull(withContext.basisAmount());
    // The own base amount takes precedence.
    assertEquals(new BigDecimal("35.70"),
                 adjustment("1190.00",
                            "3").withComputedAmount(new BigDecimal("500.00"))
                                .amount());
  }

  @Test
  void reportsAMissingBasis() {
    var e = assertThrows(IllegalStateException.class,
                         () -> adjustment(null,
                                          "3").withComputedAmount());
    assertEquals("The amount cannot be calculated: no base amount is given",
                 e.getMessage());
  }

  @Test
  void paymentTermCalculatesOnlyTheDiscount() {
    var due     = LocalDate.of(2026,
                               12,
                               10);
    var penalty = adjustment("100.00",
                             "5");
    var term = new PaymentTerm("Skonto",
                               due,
                               new BigDecimal("1190.00"),
                               penalty,
                               adjustment(null,
                                          "3"));
    var result = term.withComputedDiscount(new BigDecimal("1190.00"));
    assertEquals(new BigDecimal("35.70"),
                 result.discount()
                       .amount());
    assertEquals(due,
                 result.dueDate());
    assertEquals(new BigDecimal("1190.00"),
                 result.partialAmount());
    assertSame(penalty,
               result.penalty());
    assertNull(result.penalty()
                     .amount());
    var noDiscount = new PaymentTerm("Text",
                                     null,
                                     null,
                                     null,
                                     null);
    assertSame(noDiscount,
               noDiscount.withComputedDiscount(null));
  }
}

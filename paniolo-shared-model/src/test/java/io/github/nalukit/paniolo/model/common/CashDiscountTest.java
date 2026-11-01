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

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CashDiscountTest {
  @Test
  void writesTheXrechnungFormat() {
    assertEquals("#SKONTO#TAGE=14#PROZENT=2.00#",
                 CashDiscount.of(14,
                                 new BigDecimal("2"))
                             .toPaymentTermsLine());
    assertEquals("#SKONTO#TAGE=7#PROZENT=3.50#BASISBETRAG=100.00#",
                 new CashDiscount(7,
                                  new BigDecimal("3.5"),
                                  new BigDecimal("100")).toPaymentTermsLine());
    assertEquals("Zahlbar sofort.\n#SKONTO#TAGE=14#PROZENT=2.00#\n",
                 CashDiscount.paymentTerms("Zahlbar sofort.",
                                           List.of(CashDiscount.of(14,
                                                                   new BigDecimal("2")))));
    assertNull(CashDiscount.paymentTerms(null,
                                         List.of()));
  }

  @Test
  void splitsTextAndDiscounts() {
    var terms = CashDiscount.parse("Zahlbar innerhalb 30 Tagen.\r\n#SKONTO#TAGE=14#PROZENT=2.00#\n#SKONTO#TAGE=7#PROZENT=3.00#BASISBETRAG=50.00#\n");
    assertEquals("Zahlbar innerhalb 30 Tagen.",
                 terms.text());
    assertEquals(List.of(CashDiscount.of(14,
                                         new BigDecimal("2.00")),
                         new CashDiscount(7,
                                          new BigDecimal("3"),
                                          new BigDecimal("50"))),
                 terms.discounts());
    // Lines that do not follow the format stay text.
    assertEquals("#SKONTO#TAGE=14#PROZENT=2#",
                 CashDiscount.parse("#SKONTO#TAGE=14#PROZENT=2#")
                             .text());
  }

  @Test
  void rejectsInvalidValues() {
    assertThrows(IllegalArgumentException.class,
                 () -> CashDiscount.of(0,
                                       BigDecimal.ONE));
    assertThrows(IllegalArgumentException.class,
                 () -> CashDiscount.of(14,
                                       new BigDecimal("-1")));
  }
}

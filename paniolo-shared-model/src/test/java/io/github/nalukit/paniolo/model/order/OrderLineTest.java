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
package io.github.nalukit.paniolo.model.order;

import io.github.nalukit.paniolo.model.common.LineAdjustment;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OrderLineTest {
  private static OrderLine.Builder line(String quantity,
                                        String baseQuantity) {
    return OrderLine.create()
                    .id("1")
                    .name("Artikel")
                    .quantity(new BigDecimal(quantity))
                    .unitCode("H87")
                    .netUnitPrice(BigDecimal.ONE)
                    .priceBaseQuantity(new BigDecimal(baseQuantity));
  }

  @Test
  void theNetAmountIsRoundedOnceAtTheHalfCent() {
    assertEquals(new BigDecimal("0.00"),
                 line("1",
                      "200.0000001").build()
                                   .netAmount());
    assertEquals(new BigDecimal("0.01"),
                 line("1",
                      "200").build()
                            .netAmount());
    assertEquals(new BigDecimal("0.01"),
                 line("1",
                      "199.9999999").build()
                                   .netAmount());
    assertEquals(new BigDecimal("0.00"),
                 line("-1",
                      "200.0000001").build()
                                   .netAmount());
    assertEquals(new BigDecimal("-0.01"),
                 line("-1",
                      "200").build()
                            .netAmount());
  }

  @Test
  void adjustmentsAndTheAgreedQuantityUseTheSameRounding() {
    var charge = LineAdjustment.createCharge()
                               .amount(new BigDecimal("0.01"))
                               .reason("Verpackung")
                               .build();
    assertEquals(new BigDecimal("0.01"),
                 line("1",
                      "200.0000001").addAdjustment(charge)
                                   .build()
                                   .netAmount());
    // the amount of the agreed quantity, not of the ordered one
    assertEquals(new BigDecimal("0.00"),
                 line("5",
                      "200.0000001").agreedQuantity(BigDecimal.ONE)
                                   .build()
                                   .netAmount());
  }
}

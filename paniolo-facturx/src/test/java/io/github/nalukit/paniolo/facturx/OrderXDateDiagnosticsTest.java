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
package io.github.nalukit.paniolo.facturx;

import io.github.nalukit.paniolo.Paniolo;
import io.github.nalukit.paniolo.model.order.OrderTypeCode;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Order-X dates and date-times of a line are read in the formats 102, 203, and 204; anything else
 * is rejected with its text and the location of its element, including the position of the line.
 */
class OrderXDateDiagnosticsTest {
  private static final Paniolo PANIOLO = Paniolo.create();
  /**
   * The requested delivery date of the first line; the order has two lines.
   */
  private static final String  LINE_DATE = "<udt:DateTimeString format=\"102\">20261110</udt:DateTimeString>";
  private static final String  LOCATION  = "ram:IncludedSupplyChainTradeLineItem[1]/ram:SpecifiedLineTradeDelivery/ram:RequestedDeliverySupplyChainEvent/ram:OccurrenceDateTime/udt:DateTimeString";

  private static String withLineDate(String replacement) {
    String xml = PANIOLO.toOrderX(OrderSamples.comfort(OrderTypeCode.ORDER)
                                              .build());
    assertTrue(xml.contains(LINE_DATE),
               xml);
    return xml.replace(LINE_DATE,
                       replacement);
  }

  private static byte[] bytes(String xml) {
    return xml.getBytes(StandardCharsets.UTF_8);
  }

  private static void rejected(String replacement,
                               String message) {
    var error = assertThrows(IllegalArgumentException.class,
                             () -> PANIOLO.readOrder(bytes(withLineDate(replacement)),
                                                     true));
    assertTrue(error.getMessage()
                    .contains(message) && error.getMessage()
                                               .contains(LOCATION),
               error::getMessage);
  }

  @Test
  void readsTheDateOfALineDateTime() {
    var order = PANIOLO.readOrder(bytes(withLineDate("<udt:DateTimeString format=\"204\">20261110123456</udt:DateTimeString>")),
                                  true);
    assertEquals(LocalDate.of(2026,
                              11,
                              10),
                 order.lines()
                      .get(0)
                      .requestedDeliveryDate());
  }

  @Test
  void namesTextAndLocationOfAnInvalidLineDate() {
    rejected("<udt:DateTimeString format=\"102\">20261131</udt:DateTimeString>",
             "Invalid date \"20261131\"");
    rejected("<udt:DateTimeString format=\"203\">20261110</udt:DateTimeString>",
             "Invalid date \"20261110\"");
    rejected("<udt:DateTimeString format=\"610\">202611</udt:DateTimeString>",
             "Unsupported date format 610");
  }
}

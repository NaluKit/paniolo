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
import io.github.nalukit.paniolo.model.common.InvoiceLine;
import io.github.nalukit.paniolo.model.common.TaxCategory;
import io.github.nalukit.paniolo.model.common.value.InvoiceTypeCode;
import io.github.nalukit.paniolo.validation.ValidationMessage;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The calculation check judges every line on its own: a line whose net price was recalculated does
 * not hide the price finding of another line that states the same identifier.
 */
class RepeatedLineIdCalculationTest {
  private static final Paniolo PANIOLO = Paniolo.builder()
                                                .calculationCheck(true)
                                                .build();

  private static String xml(InvoiceSyntax syntax,
                            String firstId,
                            String secondId,
                            boolean firstWrong,
                            boolean secondWrong) {
    var first = InvoiceLine.create()
                           .id(firstId)
                           .name("Eins")
                           .quantity(new BigDecimal("8"))
                           .unitCode("C62")
                           .netUnitPrice(new BigDecimal("46.00"))
                           .tax(TaxCategory.standard(new BigDecimal("19")))
                           .build();
    var second = InvoiceLine.create()
                            .id(secondId)
                            .name("Zwei")
                            .quantity(new BigDecimal("1"))
                            .unitCode("C62")
                            .netUnitPrice(new BigDecimal("9.00"))
                            .grossPrice(new BigDecimal("10.00"))
                            .priceDiscount(new BigDecimal("1.00"))
                            .tax(TaxCategory.standard(new BigDecimal("19")))
                            .build();
    var invoice = TestInvoices.builder(InvoiceTypeCode.CODE_380,
                                       "DUP-1")
                              .addLine(first)
                              .addLine(second)
                              .build();
    String text = PANIOLO.toXml(invoice,
                                syntax);
    boolean ubl = syntax == InvoiceSyntax.UBL;
    if (firstWrong) {
      // The stated amount 378.00 is not 8 x 46.00: the lenient read recalculates the price.
      text = text.replace(ubl ?
                          ">368.00<" :
                          "<ram:LineTotalAmount>368.00<",
                          ubl ?
                          ">378.00<" :
                          "<ram:LineTotalAmount>378.00<");
    }
    if (secondWrong) {
      // The discount 2.00 does not lead from the gross price 10.00 to the net price 9.00.
      text = text.replace(ubl ?
                          "<cbc:Amount currencyID=\"EUR\">1.00<" :
                          "<ram:ActualAmount>1.00<",
                          ubl ?
                          "<cbc:Amount currencyID=\"EUR\">2.00<" :
                          "<ram:ActualAmount>2.00<");
    }
    return text;
  }

  private static List<String> netPriceLines(String xml) {
    return PANIOLO.validate(xml.getBytes(StandardCharsets.UTF_8))
                  .messages(ValidationMessage.Stage.CALCULATION)
                  .stream()
                  .filter(message -> CalculationCheck.NET_PRICE.equals(message.ruleId()))
                  .map(ValidationMessage::message)
                  .toList();
  }

  @Test
  void findsThePriceOfTheSecondLineWhateverTheIdentifiers() {
    for (var syntax : InvoiceSyntax.values()) {
      for (boolean firstWrong : new boolean[] {false, true}) {
        var same = netPriceLines(xml(syntax,
                                     "DUP",
                                     "DUP",
                                     firstWrong,
                                     true));
        var other = netPriceLines(xml(syntax,
                                      "DUP",
                                      "OTHER",
                                      firstWrong,
                                      true));
        assertEquals(1,
                     same.size(),
                     syntax + " " + firstWrong + " " + same);
        assertEquals(1,
                     other.size(),
                     syntax + " " + firstWrong + " " + other);
        assertEquals(same.get(0).replace("DUP",
                                         "X"),
                     other.get(0).replace("OTHER",
                                          "X"));
      }
    }
  }

  @Test
  void aRecalculatedLineAloneIsNoPriceFinding() {
    for (var syntax : InvoiceSyntax.values()) {
      assertTrue(netPriceLines(xml(syntax,
                                   "DUP",
                                   "DUP",
                                   true,
                                   false)).isEmpty(),
                 syntax.toString());
    }
  }
}

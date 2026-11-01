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
import io.github.nalukit.paniolo.model.common.CashDiscount;
import io.github.nalukit.paniolo.model.common.value.InvoiceTypeCode;
import io.github.nalukit.paniolo.xml.ReadMode;
import io.github.nalukit.paniolo.xml.ReadWarning;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cash discount markers in the payment terms (BT-20) that cannot be read as cash discounts stay
 * text and are reported; they never stop reading (Mustang issue #1260).
 */
class CashDiscountMarkerTest {
  private static final Paniolo      PANIOLO    = Paniolo.create();
  private static final List<String> UNREADABLE = List.of("#SKONTO#TAGE=#PROZENT=#",
                                                         "#SKONTO#TAGE=99999999999999999999#PROZENT=2.00#",
                                                         "#SKONTO#TAGE=0#PROZENT=2.00#");

  @Test
  void keepsUnreadableMarkersAsText() {
    var terms = CashDiscount.parse("Zahlbar innerhalb von 30 Tagen\n" + String.join("\n",
                                                                                    UNREADABLE) + "\n#SKONTO#TAGE=14#PROZENT=2.00#\n");
    assertEquals(List.of(CashDiscount.of(14,
                                         new BigDecimal("2"))),
                 terms.discounts());
    assertEquals(UNREADABLE,
                 terms.unreadable()
                      .stream()
                      .map(CashDiscount.Unreadable::line)
                      .toList());
    for (String line : UNREADABLE) {
      assertTrue(terms.text()
                      .contains(line),
                 terms::text);
    }
    assertTrue(terms.unreadable()
                    .get(1)
                    .reason()
                    .contains("too large"));
  }

  @Test
  void readsInvoicesWithUnreadableMarkersInBothSyntaxes() {
    var invoice = TestInvoices.builder(InvoiceTypeCode.CODE_380,
                                       "S-1")
                              .paymentTerms("Zahlbar innerhalb von 30 Tagen\n" + String.join("\n",
                                                                                             UNREADABLE))
                              .cashDiscounts(List.of(CashDiscount.of(14,
                                                                     new BigDecimal("2"))))
                              .build();
    for (var syntax : List.of(InvoiceSyntax.CII,
                              InvoiceSyntax.UBL)) {
      String xml = PANIOLO.toXml(invoice,
                                 syntax);
      for (var mode : ReadMode.values()) {
        var result = PANIOLO.read(xml,
                                  mode);
        // The valid marker is still a cash discount.
        assertEquals(List.of(CashDiscount.of(14,
                                             new BigDecimal("2"))),
                     result.invoice()
                           .cashDiscounts(),
                     () -> syntax + " " + mode);
        var warnings = result.warnings()
                             .stream()
                             .filter(warning -> warning.code()
                                                       .equals(ReadWarning.CASH_DISCOUNT))
                             .map(ReadWarning::message)
                             .toList();
        assertEquals(3,
                     warnings.size(),
                     warnings::toString);
        for (String line : UNREADABLE) {
          assertTrue(warnings.stream()
                             .anyMatch(message -> message.contains(line)),
                     () -> line + " in " + warnings);
          assertTrue(result.invoice()
                           .paymentTerms()
                           .contains(line));
        }
      }
      // The official rules judge the text on their own; reading warnings are not validation messages.
      var validation = PANIOLO.validate(xml);
      assertTrue(validation.messages()
                           .stream()
                           .noneMatch(message -> ReadWarning.CASH_DISCOUNT.equals(message.ruleId())));
    }
  }
}

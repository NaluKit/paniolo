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
import io.github.nalukit.paniolo.model.common.DocumentAdjustment;
import io.github.nalukit.paniolo.model.common.TaxCategory;
import io.github.nalukit.paniolo.model.common.value.InvoiceTypeCode;
import io.github.nalukit.paniolo.xml.ReadMode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The charge indicator is read as {@code xs:boolean}: true, false, 1, 0, with white space around it.
 * Another text is refused with its position and never becomes the opposite business case.
 */
class BooleanLexicalTest {
  private static final Paniolo PANIOLO = Paniolo.create();
  private static final Pattern UBL     = Pattern.compile("<cbc:ChargeIndicator>true</cbc:ChargeIndicator>");
  private static final Pattern CII     = Pattern.compile("<udt:Indicator>true</udt:Indicator>");

  private static String xml(InvoiceSyntax syntax) {
    return PANIOLO.toXml(TestInvoices.builder(InvoiceTypeCode.CODE_380,
                                              "B-1")
                                     .addAdjustment(DocumentAdjustment.createCharge()
                                                                      .amount(new BigDecimal("25.00"))
                                                                      .reason("Fracht")
                                                                      .tax(TaxCategory.standard(new BigDecimal("19")))
                                                                      .build())
                                     .build(),
                         syntax);
  }

  private static String with(String xml,
                             Pattern pattern,
                             String value) {
    Matcher matcher = pattern.matcher(xml);
    assertTrue(matcher.find(),
               "the document has a charge");
    return matcher.replaceFirst(Matcher.quoteReplacement(matcher.group()
                                                                .replace("true",
                                                                         value)));
  }

  private static BigDecimal payable(String xml,
                                    ReadMode mode) {
    return PANIOLO.read(xml,
                        mode)
                  .invoice()
                  .totals()
                  .payableAmount();
  }

  @Test
  void numericAndPaddedLexicalFormsKeepTheMeaning() {
    for (var syntax : InvoiceSyntax.values()) {
      String xml     = xml(syntax);
      var    pattern = syntax == InvoiceSyntax.UBL ?
                       UBL :
                       CII;
      BigDecimal charged = payable(xml,
                                   ReadMode.STRICT);
      for (String value : new String[] { "1",
                                         " true ",
                                         "\n1\n" }) {
        assertEquals(charged,
                     payable(with(xml,
                                  pattern,
                                  value),
                             ReadMode.LENIENT),
                     syntax + " " + value);
      }
      BigDecimal allowed = payable(with(xml,
                                        pattern,
                                        "0"),
                                   ReadMode.LENIENT);
      assertTrue(allowed.compareTo(charged) < 0,
                 "0 is an allowance: " + allowed + " / " + charged);
    }
  }

  @Test
  void otherTextsAreRefusedWithThePositionInBothModes() {
    for (var syntax : InvoiceSyntax.values()) {
      String xml     = xml(syntax);
      var    pattern = syntax == InvoiceSyntax.UBL ?
                       UBL :
                       CII;
      for (String value : new String[] { "TRUE",
                                         "yes",
                                         "2",
                                         "",
                                         "t" }) {
        if (value.isEmpty()) {
          continue;
        }
        String changed = with(xml,
                              pattern,
                              value);
        for (var mode : ReadMode.values()) {
          var e = assertThrows(IllegalArgumentException.class,
                               () -> PANIOLO.read(changed,
                                                  mode),
                               syntax + " " + mode + " " + value);
          assertTrue(e.getMessage()
                      .contains("Invalid boolean \"" + value + "\""),
                     e.getMessage());
        }
      }
    }
  }
}

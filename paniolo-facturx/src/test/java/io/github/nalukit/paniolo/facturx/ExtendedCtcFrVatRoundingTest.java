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
import io.github.nalukit.paniolo.config.InvoiceSyntax;
import io.github.nalukit.paniolo.validation.Severity;
import io.github.nalukit.paniolo.validation.ValidationMessage;
import io.github.nalukit.paniolo.xml.ExportVariant;
import io.github.nalukit.paniolo.xml.ReadMode;
import io.github.nalukit.paniolo.xml.ReadWarning;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A stated VAT amount of the French multi-vendor example UC10 (EXTENDED-CTC-FR) that differs from
 * the amount rounded once per breakdown (task V08, Mustang issues #1262 and #641). The 20 %
 * breakdown of sub-invoice F202600004 has two lines, so BR-FXEXT-S-09 accepts 0.02: 199.99 instead
 * of 200.00 is kept, with BT-110 649.99 and the amount due 6149.99; 199.90 is no rounding.
 */
class ExtendedCtcFrVatRoundingTest {
  private static final Paniolo PANIOLO = Paniolo.create();
  private static final String  UC10    = "UC10_F202600004_MULTI-VENDEUR_EXTENDED-CTC-FR_CII_Commentee.xml";

  private static String example()
      throws IOException {
    try (InputStream input = ExtendedCtcFrVatRoundingTest.class.getResourceAsStream("/extended-ctc-fr/" + UC10)) {
      assertNotNull(input);
      return new String(input.readAllBytes(),
                        StandardCharsets.UTF_8);
    }
  }

  /**
   * UC10 with the VAT of the 20 % breakdown and its sub-invoice line lowered by the difference.
   */
  private static byte[] lowered(String vat,
                                String difference)
      throws IOException {
    BigDecimal delta = new BigDecimal(difference);
    String     xml   = example();
    for (String[] change : new String[][] { { "<ram:CalculatedAmount>200.00</ram:CalculatedAmount>", "<ram:CalculatedAmount>" + vat + "</ram:CalculatedAmount>" },
                                            { "<ram:TaxTotalAmount currencyID=\"EUR\">200.00</ram:TaxTotalAmount>", "<ram:TaxTotalAmount currencyID=\"EUR\">" + vat + "</ram:TaxTotalAmount>" },
                                            { "<ram:GrandTotalAmount>1200.00</ram:GrandTotalAmount>", "<ram:GrandTotalAmount>" + new BigDecimal("1200.00").subtract(delta) + "</ram:GrandTotalAmount>" },
                                            { "<ram:TaxTotalAmount currencyID=\"EUR\">650.00</ram:TaxTotalAmount>", "<ram:TaxTotalAmount currencyID=\"EUR\">" + new BigDecimal("650.00").subtract(delta) + "</ram:TaxTotalAmount>" },
                                            { "<ram:GrandTotalAmount>6150.00</ram:GrandTotalAmount>", "<ram:GrandTotalAmount>" + new BigDecimal("6150.00").subtract(delta) + "</ram:GrandTotalAmount>" },
                                            { "<ram:DuePayableAmount>6150.00</ram:DuePayableAmount>", "<ram:DuePayableAmount>" + new BigDecimal("6150.00").subtract(delta) + "</ram:DuePayableAmount>" } }) {
      assertEquals(1,
                   xml.split(java.util.regex.Pattern.quote(change[0]),
                             -1).length - 1,
                   change[0]);
      xml = xml.replace(change[0],
                        change[1]);
    }
    return xml.getBytes(StandardCharsets.UTF_8);
  }

  private static List<ValidationMessage> problems(byte[] xml) {
    return PANIOLO.validate(xml)
                  .messages()
                  .stream()
                  .filter(message -> message.severity() != Severity.INFO)
                  .toList();
  }

  @Test
  void keepsTheVatAmountOfASubInvoiceRoundedPerLine()
      throws IOException {
    byte[] xml = lowered("199.99",
                         "0.01");
    assertEquals(List.of(),
                 problems(xml));
    for (var mode : ReadMode.values()) {
      var imported = PANIOLO.importInvoice(xml,
                                           mode);
      assertEquals(List.of(),
                   imported.warnings(),
                   mode::toString);
      assertEquals(List.of(),
                   imported.deviations(),
                   mode::toString);
      var vatAmounts = imported.invoice()
                               .vatAmounts();
      assertEquals(1,
                   vatAmounts.size());
      assertEquals("#F202600004#",
                   vatAmounts.get(0)
                             .tax()
                             .exemptionReason());
      assertEquals(new BigDecimal("6149.99"),
                   imported.invoice()
                           .totals()
                           .payableAmount());
      assertArrayEquals(xml,
                        PANIOLO.export(imported,
                                       ExportVariant.ORIGINAL,
                                       null));
      byte[] normalized = PANIOLO.export(imported,
                                         ExportVariant.NORMALIZED,
                                         InvoiceSyntax.CII);
      assertTrue(new String(normalized,
                            StandardCharsets.UTF_8).contains("<ram:DuePayableAmount>6149.99</ram:DuePayableAmount>"));
      assertEquals(List.of(),
                   problems(normalized),
                   mode::toString);
    }
  }

  @Test
  void reportsADifferenceThatIsNoRounding()
      throws IOException {
    byte[] xml = lowered("199.90",
                         "0.10");
    assertTrue(problems(xml).stream()
                            .anyMatch(message -> message.ruleId()
                                                        .startsWith("BR-FXEXT-S-09")),
               () -> problems(xml).toString());
    assertThrows(IllegalArgumentException.class,
                 () -> PANIOLO.importInvoice(xml,
                                             ReadMode.STRICT));
    var lenient = PANIOLO.importInvoice(xml,
                                        ReadMode.LENIENT);
    assertTrue(lenient.warnings()
                      .stream()
                      .anyMatch(warning -> ReadWarning.PAYABLE_AMOUNT.equals(warning.code())));
    assertEquals(List.of(),
                 lenient.invoice()
                        .vatAmounts());
  }
}

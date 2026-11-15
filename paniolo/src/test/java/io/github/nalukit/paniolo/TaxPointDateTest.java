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
import io.github.nalukit.paniolo.xml.ReadMode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The tax point date (BT-7) is one document value: with several VAT breakdowns CII carries it once
 * (CII-SR-461), and it survives the export in both syntaxes.
 */
class TaxPointDateTest {
  private static final Paniolo PANIOLO = Paniolo.create();

  private static io.github.nalukit.paniolo.model.Invoice invoice() {
    return TestInvoices.builder(InvoiceTypeCode.CODE_380,
                                "T-1")
                       .taxPointDate(LocalDate.of(2026,
                                                  10,
                                                  26))
                       .addLine(InvoiceLine.create()
                                           .id("2")
                                           .name("Buch")
                                           .quantity(new BigDecimal("1"))
                                           .unitCode("C62")
                                           .netUnitPrice(new BigDecimal("50.00"))
                                           .tax(TaxCategory.standard(new BigDecimal("7")))
                                           .build())
                       .build();
  }

  @Test
  void ciiWritesTheTaxPointDateOnceAndKeepsItInEverySyntax() {
    var invoice = invoice();
    for (var syntax : List.of(InvoiceSyntax.CII,
                              InvoiceSyntax.UBL)) {
      String xml = PANIOLO.toXml(invoice,
                                 syntax);
      if (syntax == InvoiceSyntax.CII) {
        assertEquals(2,
                     xml.split("<ram:ApplicableTradeTax>",
                               -1).length - 1 - lineTaxBlocks(xml),
                     "two VAT breakdowns");
        assertEquals(1,
                     xml.split("<ram:TaxPointDate>",
                               -1).length - 1);
      }
      var result = PANIOLO.validate(xml);
      assertTrue(result.isValid(),
                 () -> syntax + ": " + result.messages());
      assertEquals(LocalDate.of(2026,
                                10,
                                26),
                   PANIOLO.read(xml,
                                ReadMode.STRICT)
                          .invoice()
                          .taxPointDate());
    }
  }

  private static int lineTaxBlocks(String xml) {
    return xml.split("<ram:SpecifiedLineTradeSettlement>",
                     -1).length - 1;
  }
}

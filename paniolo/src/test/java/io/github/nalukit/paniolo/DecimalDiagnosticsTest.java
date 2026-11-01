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
import io.github.nalukit.paniolo.model.Invoice;
import io.github.nalukit.paniolo.model.common.InvoiceLine;
import io.github.nalukit.paniolo.model.common.TaxCategory;
import io.github.nalukit.paniolo.model.common.value.InvoiceTypeCode;
import io.github.nalukit.paniolo.xml.ReadMode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A number that is no decimal of the syntax is rejected in both read modes with its text and the
 * location of its element; nothing is guessed (Mustang issue #1159).
 */
class DecimalDiagnosticsTest {
  private static final Paniolo PANIOLO = Paniolo.create();

  private static Invoice invoice() {
    return TestInvoices.builder(InvoiceTypeCode.CODE_380,
                                "D-1")
                       .addLine(InvoiceLine.create()
                                           .id("2")
                                           .name("Wartung")
                                           .quantity(new BigDecimal("3"))
                                           .unitCode("HUR")
                                           .netUnitPrice(new BigDecimal("80.00"))
                                           .tax(TaxCategory.standard(new BigDecimal("19")))
                                           .build())
                       .build();
  }

  private static void rejected(String xml,
                               String text,
                               String location) {
    for (var mode : ReadMode.values()) {
      var error = assertThrows(IllegalArgumentException.class,
                               () -> PANIOLO.read(xml,
                                                  mode));
      assertTrue(error.getMessage()
                      .contains("\"" + text + "\"") && error.getMessage()
                                                            .contains(location),
                 error::getMessage);
    }
  }

  @Test
  void namesTextAndLocationOfAnInvalidDecimal() {
    String xml = PANIOLO.toXml(invoice(),
                               InvoiceSyntax.CII);
    String line = "ram:IncludedSupplyChainTradeLineItem[2]/ram:SpecifiedLineTradeAgreement/ram:NetPriceProductTradePrice/ram:ChargeAmount";
    // Decimal comma in the second line, not the first one.
    rejected(xml.replace("<ram:ChargeAmount>80.00</ram:ChargeAmount>",
                         "<ram:ChargeAmount>80,00</ram:ChargeAmount>"),
             "80,00",
             line);
    // Thousands separator at the document level.
    rejected(xml.replace("<ram:LineTotalAmount>440.00</ram:LineTotalAmount>",
                         "<ram:LineTotalAmount>4,40.00</ram:LineTotalAmount>"),
             "4,40.00",
             "ram:SpecifiedTradeSettlementHeaderMonetarySummation/ram:LineTotalAmount");
    // A unit appended to the quantity.
    rejected(xml.replace("<ram:BilledQuantity unitCode=\"HUR\">3</ram:BilledQuantity>",
                         "<ram:BilledQuantity unitCode=\"HUR\">3 HUR</ram:BilledQuantity>"),
             "3 HUR",
             "ram:IncludedSupplyChainTradeLineItem[2]/ram:SpecifiedLineTradeDelivery/ram:BilledQuantity");

    String ubl = PANIOLO.toXml(invoice(),
                               InvoiceSyntax.UBL);
    rejected(ubl.replace("<cbc:PriceAmount currencyID=\"EUR\">80.00</cbc:PriceAmount>",
                         "<cbc:PriceAmount currencyID=\"EUR\">80,00</cbc:PriceAmount>"),
             "80,00",
             "cac:InvoiceLine[2]/cac:Price/cbc:PriceAmount");
  }

  @Test
  void acceptsSurroundingWhitespace() {
    String xml = PANIOLO.toXml(invoice(),
                               InvoiceSyntax.CII)
                        .replace("<ram:ChargeAmount>80.00</ram:ChargeAmount>",
                                 "<ram:ChargeAmount> 80.00\n</ram:ChargeAmount>");
    assertEquals(0,
                 new BigDecimal("80.00").compareTo(PANIOLO.read(xml)
                                                          .lines()
                                                          .get(1)
                                                          .netUnitPrice()));
  }
}

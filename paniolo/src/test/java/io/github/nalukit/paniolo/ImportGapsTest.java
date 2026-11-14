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
import io.github.nalukit.paniolo.model.common.DirectDebit;
import io.github.nalukit.paniolo.model.common.InvoiceLine;
import io.github.nalukit.paniolo.model.common.TaxCategory;
import io.github.nalukit.paniolo.model.common.value.Country;
import io.github.nalukit.paniolo.model.common.value.InvoiceTypeCode;
import io.github.nalukit.paniolo.xml.ReadMode;
import io.github.nalukit.paniolo.xml.ReadWarning;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Valid documents of other senders that the model used to reject: addresses with the country only,
 * an empty optional delivery block, a price discount without gross price, and partial direct debit
 * data. Nothing is invented: what the document does not state stays empty or is derived and
 * reported.
 */
class ImportGapsTest {
  private static final Paniolo PANIOLO = Paniolo.create();

  private static String xml(InvoiceSyntax syntax) {
    return PANIOLO.toXml(TestInvoices.invoice(InvoiceTypeCode.CODE_380,
                                              "GAP-1"),
                         syntax);
  }

  @ParameterizedTest
  @EnumSource(InvoiceSyntax.class)
  void readsAndWritesAnAddressWithTheCountryOnly(InvoiceSyntax syntax) {
    String xml = xml(syntax);
    String countryOnly = syntax == InvoiceSyntax.CII ?
                         xml.replaceAll("(?s)(<ram:SellerTradeParty>.*?<ram:PostalTradeAddress>).*?(<ram:CountryID>)",
                                        "$1$2") :
                         xml.replaceAll("(?s)(<cac:AccountingSupplierParty>.*?<cac:PostalAddress>).*?(<cac:Country>)",
                                        "$1$2");
    assertTrue(countryOnly.length() < xml.length());
    for (var mode : ReadMode.values()) {
      var seller = PANIOLO.read(countryOnly,
                                mode)
                          .invoice()
                          .seller();
      assertTrue(seller.address()
                       .isCountryOnly(),
                 mode::toString);
      assertEquals(Country.DE,
                   seller.address()
                         .country());
      // Written again, the address states the country only; no street or city is made up.
      String written = PANIOLO.toXml(PANIOLO.read(countryOnly,
                                                  mode)
                                            .invoice(),
                                     syntax);
      assertTrue(!written.contains("Musterstraße"),
                 written);
    }
  }

  @Test
  void anEmptyShipToPartyIsNoDelivery() {
    String xml = xml(InvoiceSyntax.CII).replaceAll("(?s)<ram:ApplicableHeaderTradeDelivery>.*?</ram:ApplicableHeaderTradeDelivery>",
                                                   "<ram:ApplicableHeaderTradeDelivery><ram:ShipToTradeParty><ram:ID/><ram:Name/></ram:ShipToTradeParty></ram:ApplicableHeaderTradeDelivery>");
    for (var mode : ReadMode.values()) {
      assertNull(PANIOLO.read(xml,
                              mode)
                        .invoice()
                        .delivery(),
                 mode::toString);
    }
  }

  @Test
  void aPriceDiscountWithoutGrossPriceGetsADerivedGrossPriceInLenientReading() {
    var invoice = TestInvoices.builder(InvoiceTypeCode.CODE_380,
                                       "GAP-2")
                              .lines(List.of(InvoiceLine.create()
                                                        .id("1")
                                                        .name("Beratung")
                                                        .quantity(new BigDecimal("2"))
                                                        .unitCode("HUR")
                                                        .netUnitPrice(new BigDecimal("100.00"))
                                                        .grossPrice(new BigDecimal("125.00"))
                                                        .priceDiscount(new BigDecimal("25.00"))
                                                        .tax(TaxCategory.standard(new BigDecimal("19")))
                                                        .build()))
                              .build();
    String xml = PANIOLO.toXml(invoice,
                               InvoiceSyntax.UBL);
    String withoutGross = xml.replaceAll("<cbc:BaseAmount currencyID=\"EUR\">125.00</cbc:BaseAmount>",
                                         "");
    assertTrue(withoutGross.length() < xml.length(),
               xml);
    var lenient = PANIOLO.read(withoutGross,
                               ReadMode.LENIENT);
    assertEquals(0,
                 new BigDecimal("125.00").compareTo(lenient.invoice()
                                                           .lines()
                                                           .get(0)
                                                           .grossPrice()));
    assertEquals(List.of(ReadWarning.GROSS_PRICE),
                 lenient.warnings()
                        .stream()
                        .map(ReadWarning::code)
                        .toList());
    var error = assertThrows(IllegalArgumentException.class,
                             () -> PANIOLO.read(withoutGross,
                                                ReadMode.STRICT));
    assertTrue(error.getMessage()
                    .contains("Line 1: priceDiscount (BT-147) 25.00 requires grossPrice (BT-148)"),
               error::getMessage);
  }

  @Test
  void aDirectDebitStatesOneOfItsValuesAtLeast() {
    assertEquals("C",
                 DirectDebit.create()
                            .creditorId("C")
                            .build()
                            .creditorId());
    assertEquals("D",
                 DirectDebit.create()
                            .debitedAccountId("D")
                            .build()
                            .debitedAccountId());
    assertThrows(IllegalArgumentException.class,
                 () -> DirectDebit.create()
                                  .build());
    assertThrows(IllegalArgumentException.class,
                 () -> DirectDebit.create()
                                  .mandateReference(" ")
                                  .build());
  }
}

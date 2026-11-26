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
import io.github.nalukit.paniolo.model.common.Party;
import io.github.nalukit.paniolo.model.common.value.InvoiceTypeCode;
import io.github.nalukit.paniolo.xml.ReadMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The VAT tax scheme of a UBL party is recognised regardless of its case (Mustang issue #1355).
 * Before, {@code vat} or {@code Vat} sent the VAT identifier into the tax number and dropped the
 * real tax number of the seller.
 */
class VatSchemeCaseTest {
  private static final Paniolo PANIOLO = Paniolo.create();

  private static final String VAT_ID = "DE123456788";
  private static final String TAX_ID = "12/345/67890";

  private static final Pattern TAX_SCHEME = Pattern.compile("<cac:PartyTaxScheme>.*?</cac:PartyTaxScheme>",
                                                            Pattern.DOTALL);

  private static String ubl() {
    var seller = Party.create()
                      .name("Muster GmbH")
                      .address(TestInvoices.invoice(InvoiceTypeCode.CODE_380,
                                                    "X")
                                           .seller()
                                           .address())
                      .vatId(VAT_ID)
                      .taxId(TAX_ID)
                      .electronicAddress(TestInvoices.invoice(InvoiceTypeCode.CODE_380,
                                                              "X")
                                                     .seller()
                                                     .electronicAddress())
                      .build();
    var base = TestInvoices.invoice(InvoiceTypeCode.CODE_380,
                                    "VAT-CASE-1");
    var buyer = Party.create()
                     .name("Kunde GmbH")
                     .address(base.buyer()
                                  .address())
                     .vatId("DE987654321")
                     .electronicAddress(base.buyer()
                                            .electronicAddress())
                     .build();
    return PANIOLO.toXml(TestInvoices.builder(InvoiceTypeCode.CODE_380,
                                              "VAT-CASE-1")
                                     .seller(seller)
                                     .buyer(buyer)
                                     .build(),
                         InvoiceSyntax.UBL);
  }

  /**
   * Gives the VAT scheme of every party the spelling, and optionally puts the seller's tax number
   * entry before its VAT entry.
   */
  private static String variant(String xml,
                                String spelling,
                                boolean taxFirst) {
    var blocks  = new ArrayList<String>();
    var matcher = TAX_SCHEME.matcher(xml);
    while (matcher.find()) {
      blocks.add(matcher.group());
    }
    // Seller: VAT, FC; buyer: VAT.
    assertEquals(3,
                 blocks.size(),
                 xml);
    String sellerVat = blocks.get(0)
                             .replace("<cbc:ID>VAT</cbc:ID>",
                                      "<cbc:ID>" + spelling + "</cbc:ID>");
    String sellerTax = blocks.get(1);
    String buyerVat = blocks.get(2)
                            .replace("<cbc:ID>VAT</cbc:ID>",
                                     "<cbc:ID>" + spelling + "</cbc:ID>");
    String between = xml.substring(xml.indexOf(blocks.get(0)) + blocks.get(0)
                                                                      .length(),
                                   xml.indexOf(sellerTax));
    String sellerEntries = taxFirst ?
                           sellerTax + between + sellerVat :
                           sellerVat + between + sellerTax;
    return xml.replace(blocks.get(0) + between + sellerTax,
                       sellerEntries)
              .replace(blocks.get(2),
                       buyerVat);
  }

  @ParameterizedTest
  @ValueSource(strings = { "VAT", "vat", "Vat" })
  void keepsVatIdAndTaxIdInEveryModeAndOrder(String spelling) {
    for (boolean taxFirst : new boolean[] { false, true }) {
      String xml = variant(ubl(),
                           spelling,
                           taxFirst);
      for (var mode : ReadMode.values()) {
        var read = PANIOLO.read(xml,
                                mode);
        var seller = read.invoice()
                         .seller();
        var buyer = read.invoice()
                        .buyer();
        String context = spelling + " " + mode + " taxFirst=" + taxFirst;
        assertEquals(VAT_ID,
                     seller.vatId(),
                     context);
        assertEquals(TAX_ID,
                     seller.taxId(),
                     context);
        assertEquals("DE987654321",
                     buyer.vatId(),
                     context);
        assertNull(buyer.taxId(),
                   context);
        assertEquals(List.of(),
                     read.warnings(),
                     context);
      }
    }
  }

  @ParameterizedTest
  @ValueSource(strings = { "VAT", "vat", "Vat" })
  void survivesRoundTripThroughCii(String spelling) {
    String xml = variant(ubl(),
                         spelling,
                         false);
    var invoice = PANIOLO.read(xml,
                               ReadMode.STRICT)
                         .invoice();
    var back = PANIOLO.read(PANIOLO.toXml(invoice,
                                          InvoiceSyntax.CII),
                            ReadMode.STRICT)
                      .invoice();
    var again = PANIOLO.read(PANIOLO.toXml(back,
                                           InvoiceSyntax.UBL),
                             ReadMode.STRICT)
                       .invoice();
    assertEquals(VAT_ID,
                 again.seller()
                      .vatId());
    assertEquals(TAX_ID,
                 again.seller()
                      .taxId());
    assertEquals(invoice,
                 again);
  }

  @ParameterizedTest
  @ValueSource(strings = { "TAX", "LOC", "FC" })
  void otherSchemesStayTheSellerTaxNumber(String scheme) {
    String xml = ubl().replace("<cbc:ID>FC</cbc:ID>",
                               "<cbc:ID>" + scheme + "</cbc:ID>");
    var seller = PANIOLO.read(xml,
                              ReadMode.LENIENT)
                        .invoice()
                        .seller();
    assertEquals(VAT_ID,
                 seller.vatId());
    assertEquals(TAX_ID,
                 seller.taxId());
  }
}

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
package io.github.nalukit.paniolo.validation;

import io.github.nalukit.paniolo.model.Invoice;
import io.github.nalukit.paniolo.model.common.CashDiscount;
import io.github.nalukit.paniolo.model.common.Party;
import io.github.nalukit.paniolo.model.common.Payee;
import io.github.nalukit.paniolo.model.common.value.InvoiceTypeCode;
import io.github.nalukit.paniolo.rules.xrechnung.XrechnungRules;
import io.github.nalukit.paniolo.xml.InvoiceXmlReader;
import io.github.nalukit.paniolo.xml.InvoiceXmlWriter;
import io.github.nalukit.paniolo.xml.cii.CiiInvoiceReader;
import io.github.nalukit.paniolo.xml.cii.CiiInvoiceWriter;
import io.github.nalukit.paniolo.xml.ubl.UblInvoiceReader;
import io.github.nalukit.paniolo.xml.ubl.UblInvoiceWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Seller and buyer legal details (BT-28 to BT-47) and structured cash discounts in BT-20.
 */
class PartyAndPaymentTermsTest {
  static Invoice invoice(InvoiceTypeCode type) {
    var sample = InvoiceSamples.invoice()
                               .addLine(InvoiceSamples.line("1",
                                                            "100",
                                                            InvoiceSamples.standard()))
                               .build();
    var seller = Party.create()
                      .name(sample.seller()
                                  .name())
                      .tradingName("Muster Shop")
                      .address(sample.seller()
                                     .address())
                      .vatId(sample.seller()
                                   .vatId())
                      .electronicAddress(sample.seller()
                                               .electronicAddress())
                      .contact(sample.seller()
                                     .contact())
                      .identifier("4000001000005",
                                  "0088")
                      .legalRegistrationId("HRB 12345 B")
                      .additionalLegalInformation("Geschäftsführer: Erika Muster, Amtsgericht Charlottenburg")
                      .build();
    var buyer = Party.create()
                     .name(sample.buyer()
                                 .name())
                     .tradingName("Kunde Filiale Nord")
                     .address(sample.buyer()
                                    .address())
                     .vatId(sample.buyer()
                                  .vatId())
                     .electronicAddress(sample.buyer()
                                              .electronicAddress())
                     .contact(sample.buyer()
                                    .contact())
                     .identifier("K-4711")
                     .legalRegistrationId("4000001000012",
                                          "0088")
                     .build();
    return InvoiceSamples.invoice()
                         .typeCode(type)
                         .seller(seller)
                         .buyer(buyer)
                         .paymentTerms("Zahlbar innerhalb von 30 Tagen netto.")
                         .addCashDiscount(CashDiscount.of(14,
                                                          new BigDecimal("2")))
                         .addCashDiscount(new CashDiscount(7,
                                                           new BigDecimal("3"),
                                                           new BigDecimal("100.00")))
                         .addLine(InvoiceSamples.line("1",
                                                      "100",
                                                      InvoiceSamples.standard()))
                         .build();
  }

  private static InvoiceXmlWriter writer(Syntax syntax) {
    return syntax == Syntax.CII ?
           new CiiInvoiceWriter() :
           new UblInvoiceWriter();
  }

  private static InvoiceXmlReader reader(Syntax syntax) {
    return syntax == Syntax.CII ?
           new CiiInvoiceReader() :
           new UblInvoiceReader();
  }

  @ParameterizedTest
  @EnumSource(Syntax.class)
  void legalDetailsAndCashDiscountsPassXrechnungAndReadBack(Syntax syntax) {
    var invoice = invoice(syntax == Syntax.UBL_CREDIT_NOTE ?
                          InvoiceTypeCode.CODE_381 :
                          InvoiceTypeCode.CODE_380);
    String xml = writer(syntax).toXml(invoice);
    assertTrue(xml.contains("#SKONTO#TAGE=14#PROZENT=2.00#"),
               xml);
    assertTrue(xml.contains("#SKONTO#TAGE=7#PROZENT=3.00#BASISBETRAG=100.00#"),
               xml);
    var profile = ValidationProfile.xrechnung(XrechnungRules.bundle(),
                                              ValidationProfile.Syntax.valueOf(syntax.name()));
    var result = new InvoiceValidator(profile).validate(xml);
    assertTrue(result.isValid(),
               () -> result.messages() + "\n" + xml);
    assertFalse(result.hasWarnings(),
                () -> result.messages()
                            .toString());
    assertEquals(invoice,
                 reader(syntax).fromXml(xml));
  }

  static Payee payeeWithSchemes() {
    return Payee.create()
                .name("Factoring AG")
                .identifier("4000001000029",
                            "0088")
                .legalRegistrationId("123456782",
                                     "0002")
                .build();
  }

  @ParameterizedTest
  @EnumSource(Syntax.class)
  void payeeIdentifierSchemesPassXrechnungAndReadBack(Syntax syntax) {
    // The SEPA creditor identifier (BT-90) shares the payee party in UBL; both must survive.
    var invoice = InvoiceSamples.directDebit(InvoiceSamples.invoice())
                                .typeCode(syntax == Syntax.UBL_CREDIT_NOTE ?
                                          InvoiceTypeCode.CODE_381 :
                                          InvoiceTypeCode.CODE_380)
                                .payee(payeeWithSchemes())
                                .build();
    String xml = writer(syntax).toXml(invoice);
    if (syntax == Syntax.CII) {
      assertTrue(xml.contains("<ram:GlobalID schemeID=\"0088\">4000001000029</ram:GlobalID>"),
                 xml);
      assertTrue(xml.contains("<ram:ID schemeID=\"0002\">123456782</ram:ID>"),
                 xml);
    } else {
      assertTrue(xml.contains("<cbc:ID schemeID=\"0088\">4000001000029</cbc:ID>"),
                 xml);
      assertTrue(xml.contains("<cbc:CompanyID schemeID=\"0002\">123456782</cbc:CompanyID>"),
                 xml);
      assertTrue(xml.contains("<cbc:ID schemeID=\"SEPA\">DE98ZZZ09999999999</cbc:ID>"),
                 xml);
    }
    var profile = ValidationProfile.xrechnung(XrechnungRules.bundle(),
                                              ValidationProfile.Syntax.valueOf(syntax.name()));
    var result = new InvoiceValidator(profile).validate(xml);
    assertTrue(result.isValid(),
               () -> result.messages() + "\n" + xml);
    assertFalse(result.hasWarnings(),
                () -> result.messages()
                            .toString());
    var read = reader(syntax).fromXml(xml);
    assertEquals(invoice,
                 read);
    assertEquals(payeeWithSchemes(),
                 read.payee());
    assertEquals(xml,
                 writer(syntax).toXml(read));
  }

  @Test
  void payeeSchemesRequireTheirIdentifiers() {
    assertThrows(IllegalArgumentException.class,
                 () -> new Payee("Factoring AG",
                                 null,
                                 "0088",
                                 null,
                                 null));
    assertThrows(IllegalArgumentException.class,
                 () -> new Payee("Factoring AG",
                                 null,
                                 null,
                                 null,
                                 "0002"));
    assertThrows(IllegalArgumentException.class,
                 () -> Payee.create()
                            .name("Factoring AG")
                            .identifier("DE98ZZZ09999999999",
                                        "SEPA")
                            .build());
    // The constructor without schemes is kept for existing callers.
    assertEquals(Payee.create()
                      .name("Factoring AG")
                      .identifier("PAYEE-1")
                      .legalRegistrationId("HRB 1")
                      .build(),
                 new Payee("Factoring AG",
                           "PAYEE-1",
                           "HRB 1"));
  }

  @Test
  void malformedCashDiscountLineIsReportedByXrechnung() {
    // Cash discount lines written by hand must follow BR-DE-18 (two decimals for the percentage).
    var invoice = InvoiceSamples.invoice()
                                .paymentTerms("#SKONTO#TAGE=14#PROZENT=2#\n")
                                .addLine(InvoiceSamples.line("1",
                                                             "100",
                                                             InvoiceSamples.standard()))
                                .build();
    var result = new InvoiceValidator(ValidationProfile.xrechnung(XrechnungRules.bundle(),
                                                                  ValidationProfile.Syntax.CII)).validate(new CiiInvoiceWriter().toXml(invoice));
    assertTrue(result.messages()
                     .stream()
                     .anyMatch(message -> message.ruleId()
                                                 .equals("BR-DE-18")),
               () -> result.messages()
                           .toString());
  }

  @Test
  void buyerHasNoAdditionalLegalInformation() {
    var invoice = invoice(InvoiceTypeCode.CODE_380);
    var buyer = Party.create()
                     .name("Kunde GmbH")
                     .address(invoice.buyer()
                                     .address())
                     .additionalLegalInformation("nicht erlaubt")
                     .build();
    var withBuyer = InvoiceSamples.invoice()
                                  .buyer(buyer)
                                  .addLine(InvoiceSamples.line("1",
                                                               "100",
                                                               InvoiceSamples.standard()))
                                  .build();
    assertThrows(IllegalArgumentException.class,
                 () -> new UblInvoiceWriter().toXml(withBuyer));
    assertThrows(IllegalArgumentException.class,
                 () -> new CiiInvoiceWriter().toXml(withBuyer));
  }

  enum Syntax {
    UBL_INVOICE,
    UBL_CREDIT_NOTE,
    CII
  }
}

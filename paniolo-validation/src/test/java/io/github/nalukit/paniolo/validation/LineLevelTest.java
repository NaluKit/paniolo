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
import io.github.nalukit.paniolo.model.common.InvoiceLine;
import io.github.nalukit.paniolo.model.common.InvoicePeriod;
import io.github.nalukit.paniolo.model.common.ItemClassification;
import io.github.nalukit.paniolo.model.common.LineAdjustment;
import io.github.nalukit.paniolo.model.common.value.AllowanceChargeIdentification;
import io.github.nalukit.paniolo.model.common.value.Country;
import io.github.nalukit.paniolo.model.common.value.InvoiceTypeCode;
import io.github.nalukit.paniolo.rules.xrechnung.XrechnungRules;
import io.github.nalukit.paniolo.xml.InvoiceXmlReader;
import io.github.nalukit.paniolo.xml.InvoiceXmlWriter;
import io.github.nalukit.paniolo.xml.cii.CiiInvoiceReader;
import io.github.nalukit.paniolo.xml.cii.CiiInvoiceWriter;
import io.github.nalukit.paniolo.xml.ubl.UblInvoiceReader;
import io.github.nalukit.paniolo.xml.ubl.UblInvoiceWriter;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * All invoice line business terms (BG-25 to BG-32) in both syntaxes against the official rules.
 */
class LineLevelTest {
  static Invoice invoice(InvoiceTypeCode type) {
    var tax = InvoiceSamples.standard();
    return InvoiceSamples.invoice()
                         .typeCode(type)
                         .addLine(InvoiceLine.create()
                                             .id("1")
                                             .name("Schraube M6")
                                             .description("Edelstahl, Kopf DIN 912")
                                             .quantity(new BigDecimal("2"))
                                             .unitCode("H87")
                                             .grossPrice(new BigDecimal("120.00"))
                                             .priceDiscount(new BigDecimal("20.00"))
                                             .netUnitPrice(new BigDecimal("100.00"))
                                             .tax(tax)
                                             .note("Lieferung in zwei Kartons")
                                             .objectId("ZAEHLER-4711",
                                                       "AWV")
                                             .orderLineReference("10")
                                             .buyerAccountingReference("KST-4711")
                                             .period(InvoicePeriod.create()
                                                                  .startDate(LocalDate.of(2026,
                                                                                          10,
                                                                                          1))
                                                                  .endDate(LocalDate.of(2026,
                                                                                        10,
                                                                                        31))
                                                                  .build())
                                             .addAdjustment(LineAdjustment.createAllowance()
                                                                          .baseAmount(new BigDecimal("200.00"))
                                                                          .percentage(new BigDecimal("5"))
                                                                          .amount(new BigDecimal("10.00"))
                                                                          .reasonCode(AllowanceChargeIdentification.CODE_95)
                                                                          .reason("Mengenrabatt")
                                                                          .build())
                                             .addAdjustment(LineAdjustment.createCharge()
                                                                          .amount(new BigDecimal("5.00"))
                                                                          .reasonCode("FC")
                                                                          .reason("Fracht")
                                                                          .build())
                                             .sellerItemId("ART-6")
                                             .buyerItemId("K-6")
                                             .standardItemId("4012345678901",
                                                             "0160")
                                             .addClassification(new ItemClassification("73181568",
                                                                                       "HS",
                                                                                       "2022"))
                                             .originCountry(Country.DE)
                                             .addAttribute("Farbe",
                                                           "silber")
                                             .addAttribute("Länge",
                                                           "30 mm")
                                             .build())
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
  void allLineTermsPassXrechnungAndReadBack(Syntax syntax) {
    var invoice = invoice(syntax == Syntax.UBL_CREDIT_NOTE ?
                          InvoiceTypeCode.CODE_381 :
                          InvoiceTypeCode.CODE_380);
    var line = invoice.lines()
                      .get(0);
    assertEquals(new BigDecimal("195.00"),
                 line.netAmount());
    assertEquals(new BigDecimal("10.00"),
                 line.allowanceTotal());
    assertEquals(new BigDecimal("5.00"),
                 line.chargeTotal());

    String xml = writer(syntax).toXml(invoice);
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

  enum Syntax {
    UBL_INVOICE,
    UBL_CREDIT_NOTE,
    CII
  }
}

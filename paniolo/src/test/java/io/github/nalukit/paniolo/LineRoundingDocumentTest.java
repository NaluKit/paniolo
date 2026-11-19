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
import io.github.nalukit.paniolo.model.common.LineAdjustment;
import io.github.nalukit.paniolo.model.common.TaxCategory;
import io.github.nalukit.paniolo.model.common.value.InvoiceTypeCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.w3c.dom.Element;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The line amount at the half cent (quantity 1, net price 1, price base quantity 200.0000001): the
 * amount of the line is 0.00, and the model, the written XML in UBL and CII, and the model read back
 * state the same line amount and the same document totals. The expected values are fixed numbers, not
 * calculated by the code under test.
 *
 * <p>The invoice has a normal line of 10.00 and the line under test, both at 19 % VAT.
 */
class LineRoundingDocumentTest {
  private static final Paniolo     PANIOLO = Paniolo.create();
  private static final TaxCategory VAT_19  = TaxCategory.standard(new BigDecimal("19"));

  private static final String UBL_CBC = "urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2";
  private static final String CII_RAM = "urn:un:unece:uncefact:data:standard:ReusableAggregateBusinessInformationEntity:100";

  private static InvoiceLine normal() {
    return InvoiceLine.create()
                      .id("1")
                      .name("Beratung")
                      .quantity(BigDecimal.ONE)
                      .unitCode("HUR")
                      .netUnitPrice(new BigDecimal("10.00"))
                      .tax(VAT_19)
                      .build();
  }

  private static InvoiceLine.Builder boundary(String quantity,
                                              String priceBase) {
    return InvoiceLine.create()
                      .id("2")
                      .name("Grenzfall")
                      .quantity(new BigDecimal(quantity))
                      .unitCode("H87")
                      .netUnitPrice(BigDecimal.ONE)
                      .priceBaseQuantity(new BigDecimal(priceBase))
                      .tax(VAT_19);
  }

  private static Invoice invoice(InvoiceLine boundary) {
    return TestInvoices.builder(InvoiceTypeCode.CODE_380,
                                "LR-1")
                       .lines(List.of(normal(),
                                      boundary))
                       .build();
  }

  /**
   * The values of the elements with a name below a parent with a name, in document order.
   */
  private static List<String> values(String xml,
                                     String namespace,
                                     String name,
                                     String parent) throws Exception {
    var factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(true);
    var document = factory.newDocumentBuilder()
                          .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    var nodes  = document.getElementsByTagNameNS(namespace,
                                                 name);
    var result = new ArrayList<String>();
    for (int i = 0; i < nodes.getLength(); i++) {
      var element = (Element) nodes.item(i);
      if (parent.equals(element.getParentNode()
                               .getLocalName())) {
        result.add(element.getTextContent()
                          .trim());
      }
    }
    return result;
  }

  /**
   * The expectation for one case: the amount of the line under test and the document totals, with the
   * normal line of 10.00.
   */
  private record Expected(String line, String net, String tax, String gross) {
  }

  private void check(InvoiceLine line,
                     InvoiceSyntax syntax,
                     Expected expected) throws Exception {
    Invoice invoice = invoice(line);

    // the model
    assertEquals(new BigDecimal(expected.line()),
                 invoice.lines()
                        .get(1)
                        .netAmount());
    var totals = invoice.totals();
    assertEquals(new BigDecimal(expected.net()),
                 totals.taxExclusiveAmount());
    assertEquals(new BigDecimal(expected.tax()),
                 totals.taxAmount());
    assertEquals(new BigDecimal(expected.gross()),
                 totals.taxInclusiveAmount());

    // the written XML
    String xml = PANIOLO.toXml(invoice,
                               syntax);
    if (syntax == InvoiceSyntax.UBL) {
      assertEquals(List.of("10.00",
                           expected.line()),
                   values(xml,
                          UBL_CBC,
                          "LineExtensionAmount",
                          "InvoiceLine"));
      assertEquals(List.of(expected.net()),
                   values(xml,
                          UBL_CBC,
                          "TaxExclusiveAmount",
                          "LegalMonetaryTotal"));
      assertEquals(List.of(expected.gross()),
                   values(xml,
                          UBL_CBC,
                          "TaxInclusiveAmount",
                          "LegalMonetaryTotal"));
      assertEquals(List.of(expected.gross()),
                   values(xml,
                          UBL_CBC,
                          "PayableAmount",
                          "LegalMonetaryTotal"));
      assertEquals(List.of(expected.tax()),
                   values(xml,
                          UBL_CBC,
                          "TaxAmount",
                          "TaxTotal"));
    } else {
      assertEquals(List.of("10.00",
                           expected.line()),
                   values(xml,
                          CII_RAM,
                          "LineTotalAmount",
                          "SpecifiedTradeSettlementLineMonetarySummation"));
      assertEquals(List.of(expected.net()),
                   values(xml,
                          CII_RAM,
                          "TaxBasisTotalAmount",
                          "SpecifiedTradeSettlementHeaderMonetarySummation"));
      assertEquals(List.of(expected.gross()),
                   values(xml,
                          CII_RAM,
                          "GrandTotalAmount",
                          "SpecifiedTradeSettlementHeaderMonetarySummation"));
      assertEquals(List.of(expected.tax()),
                   values(xml,
                          CII_RAM,
                          "TaxTotalAmount",
                          "SpecifiedTradeSettlementHeaderMonetarySummation"));
    }

    // the model read back
    Invoice back = PANIOLO.read(xml);
    assertEquals(new BigDecimal(expected.line()),
                 back.lines()
                     .get(1)
                     .netAmount());
    assertEquals(new BigDecimal(expected.net()),
                 back.totals()
                     .taxExclusiveAmount());
    assertEquals(new BigDecimal(expected.tax()),
                 back.totals()
                     .taxAmount());
    assertEquals(new BigDecimal(expected.gross()),
                 back.totals()
                     .taxInclusiveAmount());
  }

  @ParameterizedTest
  @EnumSource(InvoiceSyntax.class)
  void justBelowTheHalfCentTheLineIsZero(InvoiceSyntax syntax) throws Exception {
    check(boundary("1",
                   "200.0000001").build(),
          syntax,
          new Expected("0.00",
                       "10.00",
                       "1.90",
                       "11.90"));
  }

  @ParameterizedTest
  @EnumSource(InvoiceSyntax.class)
  void atAndAboveTheHalfCentTheLineIsRoundedUp(InvoiceSyntax syntax) throws Exception {
    var expected = new Expected("0.01",
                                "10.01",
                                "1.90",
                                "11.91");
    check(boundary("1",
                   "200").build(),
          syntax,
          expected);
    check(boundary("1",
                   "199.9999999").build(),
          syntax,
          expected);
  }

  @ParameterizedTest
  @EnumSource(InvoiceSyntax.class)
  void aNegativeQuantityFollowsTheSameRule(InvoiceSyntax syntax) throws Exception {
    check(boundary("-1",
                   "200.0000001").build(),
          syntax,
          new Expected("0.00",
                       "10.00",
                       "1.90",
                       "11.90"));
    check(boundary("-1",
                   "200").build(),
          syntax,
          new Expected("-0.01",
                       "9.99",
                       "1.90",
                       "11.89"));
  }

  @ParameterizedTest
  @EnumSource(InvoiceSyntax.class)
  void aChargeOrAllowanceIsAddedBeforeTheOnlyRounding(InvoiceSyntax syntax) throws Exception {
    check(boundary("1",
                   "200.0000001").addAdjustment(LineAdjustment.createCharge()
                                                              .amount(new BigDecimal("0.01"))
                                                              .reason("Verpackung")
                                                              .build())
                                 .build(),
          syntax,
          new Expected("0.01",
                       "10.01",
                       "1.90",
                       "11.91"));
    check(boundary("1",
                   "200.0000001").addAdjustment(LineAdjustment.createAllowance()
                                                              .amount(new BigDecimal("0.01"))
                                                              .reason("Rabatt")
                                                              .build())
                                 .build(),
          syntax,
          new Expected("-0.01",
                       "9.99",
                       "1.90",
                       "11.89"));
  }

  @Test
  void aStatedLineAmountStillNeedsToBeExplainedByTheNetPrice() {
    // a stated amount within the rounding of the price is kept, a different one is refused
    var base = boundary("1",
                        "200.0000001").build();
    assertEquals(new BigDecimal("0.01"),
                 base.withNetAmount(new BigDecimal("0.01"))
                     .netAmount());
    org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                                                  () -> base.withNetAmount(new BigDecimal("1.00")));
  }
}

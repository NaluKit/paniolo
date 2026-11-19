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
import io.github.nalukit.paniolo.model.common.LineAdjustment;
import io.github.nalukit.paniolo.model.order.Order;
import io.github.nalukit.paniolo.model.order.OrderLine;
import io.github.nalukit.paniolo.model.order.OrderTypeCode;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The line amount of an order at the half cent (quantity 1, net price 1, price base quantity
 * 200.0000001) is 0.00: the model, the written Order-X XML, and the order read back state the same
 * line amount and the same totals. The sample order has the lines 1000.00 and 62.50; the expected
 * values are fixed numbers, not calculated by the code under test.
 */
class OrderLineRoundingTest {
  private static final Paniolo PANIOLO = Paniolo.create();

  private static final String RAM = "urn:un:unece:uncefact:data:standard:ReusableAggregateBusinessInformationEntity:128";

  private static OrderLine.Builder boundary(String priceBase) {
    return OrderLine.create()
                    .id("3")
                    .name("Grenzfall")
                    .quantity(BigDecimal.ONE)
                    .unitCode("H87")
                    .netUnitPrice(BigDecimal.ONE)
                    .priceBaseQuantity(new BigDecimal(priceBase));
  }

  private static List<String> values(String xml,
                                     String name,
                                     String parent) throws Exception {
    var factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(true);
    var document = factory.newDocumentBuilder()
                          .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    var nodes  = document.getElementsByTagNameNS(RAM,
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

  private void check(OrderLine line,
                     String lineAmount,
                     String total) throws Exception {
    Order order = OrderSamples.basic(OrderTypeCode.ORDER)
                              .addLine(line)
                              .build();

    // the model
    assertEquals(new BigDecimal(lineAmount),
                 order.lines()
                      .get(2)
                      .netAmount());
    assertEquals(new BigDecimal(total),
                 order.totals()
                      .lineTotal());
    assertEquals(new BigDecimal(total),
                 order.totals()
                      .taxBasisTotal());

    // the written XML
    String xml = PANIOLO.toOrderX(order);
    assertEquals(List.of("1000.00",
                         "62.50",
                         lineAmount),
                 values(xml,
                        "LineTotalAmount",
                        "SpecifiedTradeSettlementLineMonetarySummation"));
    assertEquals(List.of(total),
                 values(xml,
                        "LineTotalAmount",
                        "SpecifiedTradeSettlementHeaderMonetarySummation"));
    assertEquals(List.of(total),
                 values(xml,
                        "TaxBasisTotalAmount",
                        "SpecifiedTradeSettlementHeaderMonetarySummation"));

    // the order read back
    Order back = PANIOLO.readOrder(xml);
    assertEquals(new BigDecimal(lineAmount),
                 back.lines()
                     .get(2)
                     .netAmount());
    assertEquals(new BigDecimal(total),
                 back.totals()
                     .lineTotal());
    assertEquals(new BigDecimal(total),
                 back.totals()
                     .taxBasisTotal());
  }

  @Test
  void justBelowTheHalfCentTheLineIsZero() throws Exception {
    check(boundary("200.0000001").build(),
          "0.00",
          "1062.50");
  }

  @Test
  void atAndAboveTheHalfCentTheLineIsRoundedUp() throws Exception {
    check(boundary("200").build(),
          "0.01",
          "1062.51");
    check(boundary("199.9999999").build(),
          "0.01",
          "1062.51");
  }

  /**
   * The COMFORT sample (lines 1000.00 and 60.00 after a line allowance, a document allowance of 20.00,
   * 19 % VAT) with the line under test: allowances and charges of a line need this profile.
   */
  private void checkComfort(OrderLine line,
                            String lineAmount,
                            String lineTotal,
                            String taxBasis,
                            String tax,
                            String grand) throws Exception {
    Order order = OrderSamples.comfort(OrderTypeCode.ORDER)
                              .addLine(line)
                              .build();

    assertEquals(new BigDecimal(lineAmount),
                 order.lines()
                      .get(2)
                      .netAmount());
    var totals = order.totals();
    assertEquals(new BigDecimal(lineTotal),
                 totals.lineTotal());
    assertEquals(new BigDecimal(taxBasis),
                 totals.taxBasisTotal());
    assertEquals(new BigDecimal(tax),
                 totals.taxAmount());
    assertEquals(new BigDecimal(grand),
                 totals.grandTotal());

    String xml = PANIOLO.toOrderX(order);
    assertEquals(List.of("1000.00",
                         "60.00",
                         lineAmount),
                 values(xml,
                        "LineTotalAmount",
                        "SpecifiedTradeSettlementLineMonetarySummation"));
    assertEquals(List.of(lineTotal),
                 values(xml,
                        "LineTotalAmount",
                        "SpecifiedTradeSettlementHeaderMonetarySummation"));
    assertEquals(List.of(taxBasis),
                 values(xml,
                        "TaxBasisTotalAmount",
                        "SpecifiedTradeSettlementHeaderMonetarySummation"));
    assertEquals(List.of(tax),
                 values(xml,
                        "TaxTotalAmount",
                        "SpecifiedTradeSettlementHeaderMonetarySummation"));
    assertEquals(List.of(grand),
                 values(xml,
                        "GrandTotalAmount",
                        "SpecifiedTradeSettlementHeaderMonetarySummation"));

    Order back = PANIOLO.readOrder(xml);
    assertEquals(new BigDecimal(lineAmount),
                 back.lines()
                     .get(2)
                     .netAmount());
    assertEquals(totals,
                 back.totals());
  }

  @Test
  void aChargeOrAllowanceIsAddedBeforeTheOnlyRounding() throws Exception {
    var vat = io.github.nalukit.paniolo.model.common.TaxCategory.standard(new BigDecimal("19"));
    checkComfort(boundary("200.0000001").tax(vat)
                                        .addAdjustment(LineAdjustment.createCharge()
                                                                     .amount(new BigDecimal("0.01"))
                                                                     .reason("Verpackung")
                                                                     .build())
                                        .build(),
                 "0.01",
                 "1060.01",
                 "1040.01",
                 "197.60",
                 "1237.61");
    checkComfort(boundary("200.0000001").tax(vat)
                                        .addAdjustment(LineAdjustment.createAllowance()
                                                                     .amount(new BigDecimal("0.01"))
                                                                     .reason("Rabatt")
                                                                     .build())
                                        .build(),
                 "-0.01",
                 "1059.99",
                 "1039.99",
                 "197.60",
                 "1237.59");
    // without the adjustment the line is 0.00
    checkComfort(boundary("200.0000001").tax(vat)
                                        .build(),
                 "0.00",
                 "1060.00",
                 "1040.00",
                 "197.60",
                 "1237.60");
  }
}

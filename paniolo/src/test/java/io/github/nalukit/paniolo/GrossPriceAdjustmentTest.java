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

import io.github.nalukit.paniolo.config.FacturXProfile;
import io.github.nalukit.paniolo.config.InvoiceSyntax;
import io.github.nalukit.paniolo.model.common.InvoiceLine;
import io.github.nalukit.paniolo.model.common.TaxCategory;
import io.github.nalukit.paniolo.model.extended.GrossPriceAdjustment;
import io.github.nalukit.paniolo.model.extended.LineExtension;
import io.github.nalukit.paniolo.validation.ValidationMessage;
import io.github.nalukit.paniolo.validation.ValidationResult;
import io.github.nalukit.paniolo.validation.Severity;
import io.github.nalukit.paniolo.xml.ReadMode;
import io.github.nalukit.paniolo.xml.ReadWarning;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Several allowances and charges on the gross price of an EXTENDED line: the model keeps each of
 * them, the price discount (BT-147) is their sum, and a new export writes all of them again.
 */
class GrossPriceAdjustmentTest {
  private static final Paniolo PANIOLO = Paniolo.builder()
                                                .calculationCheck(true)
                                                .build();

  private static final String ALLOWANCE = "<ram:AppliedTradeAllowanceCharge>";

  /**
   * The gross price 20.13 has two allowances of 1.00 and 2.00; the net price is 17.13.
   */
  private static String multipleDiscounts() {
    try (InputStream input = GrossPriceAdjustmentTest.class.getResourceAsStream("/price-adjustments/multiple-discounts.xml")) {
      return new String(input.readAllBytes(),
                        StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static List<ValidationMessage> problems(ValidationResult result) {
    return result.messages()
                 .stream()
                 .filter(message -> message.severity() != Severity.INFO)
                 .toList();
  }

  @Test
  void keepsEveryDiscountOfTheGrossPrice() {
    String input = multipleDiscounts();
    assertTrue(PANIOLO.validate(input.getBytes(StandardCharsets.UTF_8),
                                FacturXProfile.EXTENDED)
                      .isValid());

    var line = PANIOLO.read(input)
                      .lines()
                      .getFirst();
    assertEquals(0,
                 new BigDecimal("20.13").compareTo(line.grossPrice()));
    assertEquals(0,
                 new BigDecimal("3.00").compareTo(line.priceDiscount()));
    assertEquals(0,
                 new BigDecimal("17.13").compareTo(line.netUnitPrice()));
    assertEquals(List.of(GrossPriceAdjustment.allowance(new BigDecimal("1.00")),
                         GrossPriceAdjustment.allowance(new BigDecimal("2.00"))),
                 line.extended()
                     .priceAdjustments());

    String exported = PANIOLO.toXml(PANIOLO.read(input),
                                    InvoiceSyntax.CII);
    assertEquals(2,
                 exported.split(ALLOWANCE,
                                -1).length - 1,
                 exported);
    assertTrue(exported.contains("<ram:ActualAmount>1.00</ram:ActualAmount>") && exported.contains("<ram:ActualAmount>2.00</ram:ActualAmount>"),
               exported);
    // Both validators accept the export, and the price relation 20.13 - 1.00 - 2.00 = 17.13 holds.
    var result = PANIOLO.validate(exported.getBytes(StandardCharsets.UTF_8),
                                  FacturXProfile.EXTENDED);
    assertEquals(List.of(),
                 problems(result));
    assertEquals(List.of(),
                 PANIOLO.validate(input.getBytes(StandardCharsets.UTF_8),
                                  FacturXProfile.EXTENDED)
                        .messages(ValidationMessage.Stage.CALCULATION));
    assertEquals(PANIOLO.read(input),
                 PANIOLO.read(exported));
  }

  @Test
  void writesAndReadsChargesAndReasons() {
    String input = multipleDiscounts().replace("<ram:ChargeAmount>20.13</ram:ChargeAmount>",
                                               "<ram:ChargeAmount>19.63</ram:ChargeAmount>")
                                      .replace("<ram:ActualAmount>2.00</ram:ActualAmount></ram:AppliedTradeAllowanceCharge>",
                                               "<ram:ActualAmount>2.00</ram:ActualAmount><ram:ReasonCode>95</ram:ReasonCode><ram:Reason>Aktion</ram:Reason></ram:AppliedTradeAllowanceCharge>" +
                                               "<ram:AppliedTradeAllowanceCharge><ram:ChargeIndicator><udt:Indicator>true</udt:Indicator></ram:ChargeIndicator><ram:ActualAmount>0.50</ram:ActualAmount><ram:Reason>Pfand</ram:Reason></ram:AppliedTradeAllowanceCharge>");
    var invoice = PANIOLO.read(input);
    var line    = invoice.lines()
                         .getFirst();
    // 1.00 + 2.00 - 0.50
    assertEquals(0,
                 new BigDecimal("2.50").compareTo(line.priceDiscount()));
    assertEquals(List.of(GrossPriceAdjustment.allowance(new BigDecimal("1.00")),
                         new GrossPriceAdjustment(false,
                                                  new BigDecimal("2.00"),
                                                  "95",
                                                  "Aktion"),
                         new GrossPriceAdjustment(true,
                                                  new BigDecimal("0.50"),
                                                  null,
                                                  "Pfand")),
                 line.extended()
                     .priceAdjustments());
    String exported = PANIOLO.toXml(invoice,
                                    InvoiceSyntax.CII);
    assertTrue(exported.replaceAll(">\\s+<",
                                   "><")
                       .contains("<udt:Indicator>true</udt:Indicator></ram:ChargeIndicator><ram:ActualAmount>0.50</ram:ActualAmount><ram:Reason>Pfand</ram:Reason>"),
               exported);
    assertEquals(invoice,
                 PANIOLO.read(exported));
    assertEquals(List.of(),
                 problems(PANIOLO.validate(exported.getBytes(StandardCharsets.UTF_8),
                                           FacturXProfile.EXTENDED)));
  }

  @Test
  void aSingleDiscountStaysTheEn16931Discount() {
    String input = multipleDiscounts().replace("<ram:ChargeAmount>20.13</ram:ChargeAmount>",
                                               "<ram:ChargeAmount>18.13</ram:ChargeAmount>")
                                      .replace("<ram:AppliedTradeAllowanceCharge><ram:ChargeIndicator><udt:Indicator>false</udt:Indicator></ram:ChargeIndicator><ram:ActualAmount>2.00</ram:ActualAmount></ram:AppliedTradeAllowanceCharge>",
                                               "");
    var line = PANIOLO.read(input)
                      .lines()
                      .getFirst();
    assertEquals(0,
                 BigDecimal.ONE.compareTo(line.priceDiscount()));
    assertNull(line.extended());
  }

  @Test
  void theDiscountMustBeTheSumOfTheAdjustments() {
    var line = InvoiceLine.create()
                          .id("1")
                          .name("Prüfposition")
                          .quantity(BigDecimal.ONE)
                          .unitCode("HUR")
                          .grossPrice(new BigDecimal("20.13"))
                          .priceDiscount(new BigDecimal("1.00"))
                          .netUnitPrice(new BigDecimal("19.13"))
                          .tax(TaxCategory.standard(new BigDecimal("7")))
                          .extended(LineExtension.create()
                                                 .addPriceAdjustment(GrossPriceAdjustment.allowance(new BigDecimal("1.00")))
                                                 .addPriceAdjustment(GrossPriceAdjustment.allowance(new BigDecimal("2.00")))
                                                 .build());
    var wrong = assertThrows(IllegalArgumentException.class,
                             line::build);
    assertTrue(wrong.getMessage()
                    .contains("priceDiscount (BT-147) 1.00 is not the allowances minus the charges of the gross price adjustments, 3.00"),
               wrong::getMessage);
    assertThrows(IllegalArgumentException.class,
                 () -> line.grossPrice(null)
                           .priceDiscount(null)
                           .build());
    assertThrows(IllegalArgumentException.class,
                 () -> GrossPriceAdjustment.charge(new BigDecimal("-1")));
  }

  @Test
  void theDiscountPartsAreExtendedInformation() {
    // An EN 16931 export cannot carry the parts; it fails instead of dropping one of them.
    String en16931 = multipleDiscounts().replace("urn:cen.eu:en16931:2017#conformant#urn:factur-x.eu:1p0:extended",
                                                 "urn:cen.eu:en16931:2017");
    var invoice = PANIOLO.read(en16931);
    assertTrue(invoice.hasExtendedInformation());
    assertThrows(IllegalArgumentException.class,
                 () -> PANIOLO.toXml(invoice,
                                     InvoiceSyntax.CII));
    assertThrows(IllegalArgumentException.class,
                 () -> PANIOLO.toXml(invoice,
                                     InvoiceSyntax.UBL));
  }

  @Test
  void aRecalculatedPriceDropsTheParts() {
    // 2 x 17.13 = 34.26; the stated 34.00 makes lenient reading recalculate the net price as 17.00.
    String input = multipleDiscounts().replace("<ram:LineTotalAmount>34.26</ram:LineTotalAmount>",
                                               "<ram:LineTotalAmount>34.00</ram:LineTotalAmount>");
    var result = PANIOLO.read(input,
                              ReadMode.LENIENT);
    var line = result.invoice()
                     .lines()
                     .getFirst();
    assertEquals(0,
                 new BigDecimal("17.00").compareTo(line.netUnitPrice()));
    assertEquals(0,
                 new BigDecimal("3.13").compareTo(line.priceDiscount()));
    assertNull(line.extended());
    assertTrue(result.warnings()
                     .stream()
                     .anyMatch(warning -> warning.code()
                                                 .equals(ReadWarning.LINE_NET_PRICE)),
               () -> result.warnings()
                           .toString());
  }
}

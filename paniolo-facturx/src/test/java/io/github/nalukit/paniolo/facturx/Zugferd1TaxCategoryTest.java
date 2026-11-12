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
import io.github.nalukit.paniolo.model.Invoice;
import io.github.nalukit.paniolo.xml.ReadMode;
import io.github.nalukit.paniolo.xml.ReadWarning;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * VAT blocks of ZUGFeRD 1.0 documents that state less than the model needs (task C04, Mustang
 * issue #938). ZUGFeRD 1.0 does not require the VAT category code of a block, nor a VAT block at a
 * line, allowance, charge, or logistics service charge. Reading failed with
 * {@code NullPointerException: tax category code} at the VAT breakdown, and, after the category was
 * added, with {@code NullPointerException: tax} at the first charge.
 *
 * <p>What the document leaves no doubt about is taken and reported as {@code READ-TAX-CATEGORY}: a
 * rate above zero is the standard rate (S), and an element without VAT block belongs to the only
 * VAT breakdown. A rate of zero, or several breakdowns to choose from, is rejected with the location;
 * no category is guessed. The cases are derived from the ZUGFeRD 1.0 COMFORT sample, which has one
 * VAT breakdown (S, 19 %), an allowance, a logistics service charge, and two lines.
 */
class Zugferd1TaxCategoryTest {
  private static final Paniolo PANIOLO = Paniolo.create();

  private static String comfort() {
    return new String(Zugferd1Test.sample("comfort.xml"),
                      StandardCharsets.UTF_8);
  }

  private static byte[] bytes(String xml) {
    return xml.getBytes(StandardCharsets.UTF_8);
  }

  private static String withoutCategoryCodes() {
    String xml = comfort().replaceAll("\\s*<ram:CategoryCode>S</ram:CategoryCode>",
                                      "");
    assertTrue(!xml.contains("CategoryCode"),
               xml);
    return xml;
  }

  private static String withoutVatBlocksOfElements() {
    String xml = comfort().replaceAll("(?s)\\s*<ram:CategoryTradeTax>.*?</ram:CategoryTradeTax>",
                                      "")
                          .replaceAll("(?s)\\s*<ram:AppliedTradeTax>.*?</ram:AppliedTradeTax>",
                                      "")
                          .replaceAll("(?s)(<ram:SpecifiedSupplyChainTradeSettlement>)\\s*<ram:ApplicableTradeTax>.*?</ram:ApplicableTradeTax>",
                                      "$1");
    assertEquals(1,
                 xml.split("<ram:ApplicableTradeTax>",
                           -1).length - 1,
                 "only the VAT breakdown of the document is left");
    return xml;
  }

  /**
   * The VAT breakdown S 7 % next to S 19 %.
   */
  private static String withSecondBreakdown(String xml) {
    String block = "<ram:ApplicableTradeTax><ram:CalculatedAmount currencyID=\"EUR\">0.00</ram:CalculatedAmount><ram:TypeCode>VAT</ram:TypeCode>" +
                   "<ram:BasisAmount currencyID=\"EUR\">0.00</ram:BasisAmount><ram:CategoryCode>S</ram:CategoryCode><ram:ApplicablePercent>7.00</ram:ApplicablePercent></ram:ApplicableTradeTax>";
    int end = xml.indexOf("</ram:ApplicableTradeTax>") + "</ram:ApplicableTradeTax>".length();
    return xml.substring(0,
                         end) + block + xml.substring(end);
  }

  private static Invoice original(ReadMode mode) {
    var imported = PANIOLO.importInvoice(bytes(comfort()),
                                         mode);
    assertEquals(List.of(),
                 imported.warnings(),
                 "the sample states every category");
    return imported.invoice();
  }

  private static List<String> messages(List<ReadWarning> warnings) {
    assertTrue(warnings.stream()
                       .allMatch(warning -> ReadWarning.TAX_CATEGORY.equals(warning.code())),
               warnings::toString);
    return warnings.stream()
                   .map(ReadWarning::message)
                   .toList();
  }

  @Test
  void takesTheStandardRateWhereTheCategoryCodeIsMissing() {
    for (var mode : ReadMode.values()) {
      var imported = PANIOLO.importInvoice(bytes(withoutCategoryCodes()),
                                           mode);
      assertEquals(original(mode),
                   imported.invoice(),
                   mode::toString);
      var messages = messages(imported.warnings());
      assertEquals(5,
                   messages.size(),
                   messages::toString);
      assertEquals("VAT breakdown 1 states no VAT category code; the rate 19.00 % is the standard rate (S)",
                   messages.get(0));
      assertTrue(messages.stream()
                         .anyMatch(message -> message.startsWith("Document allowance 1 ")),
                 messages::toString);
      assertTrue(messages.stream()
                         .anyMatch(message -> message.startsWith("Logistics service charge 1 ")),
                 messages::toString);
      assertEquals(List.of("1",
                           "2"),
                   imported.warnings()
                           .stream()
                           .map(ReadWarning::lineId)
                           .filter(java.util.Objects::nonNull)
                           .toList());
      // The migration is a separate step: the same invoice is written as Factur-X and is valid.
      assertTrue(PANIOLO.validate(bytes(PANIOLO.toXml(imported.invoice(),
                                                      InvoiceSyntax.CII)))
                        .isValid());
    }
  }

  @Test
  void assignsAnElementWithoutVatBlockToTheOnlyBreakdown() {
    for (var mode : ReadMode.values()) {
      var imported = PANIOLO.importInvoice(bytes(withoutVatBlocksOfElements()),
                                           mode);
      assertEquals(original(mode),
                   imported.invoice(),
                   mode::toString);
      var messages = messages(imported.warnings());
      assertEquals(4,
                   messages.size(),
                   messages::toString);
      assertTrue(messages.stream()
                         .allMatch(message -> message.contains(" states no VAT block; the only VAT breakdown of the document (S 19) applies")),
                 messages::toString);
      assertEquals("Line 1 states no VAT block; the only VAT breakdown of the document (S 19) applies",
                   messages.stream()
                           .filter(message -> message.startsWith("Line 1 "))
                           .findFirst()
                           .orElseThrow());
    }
  }

  @Test
  void rejectsAnElementWithoutVatBlockWhenSeveralBreakdownsExist() {
    // Lines: the document states S 19 % and S 7 %, and line 1 has no VAT block.
    String line = withSecondBreakdown(comfort().replaceFirst("(?s)(<ram:SpecifiedSupplyChainTradeSettlement>)\\s*<ram:ApplicableTradeTax>.*?</ram:ApplicableTradeTax>",
                                                             "$1"));
    // An allowance: the same for a document allowance without VAT block.
    String allowance = withSecondBreakdown(comfort().replaceAll("(?s)\\s*<ram:CategoryTradeTax>.*?</ram:CategoryTradeTax>",
                                                                ""));
    for (var mode : ReadMode.values()) {
      var forLine = assertThrows(IllegalArgumentException.class,
                                 () -> PANIOLO.importInvoice(bytes(line),
                                                             mode),
                                 mode::toString);
      assertTrue(forLine.getMessage()
                        .startsWith("Line 1 states no VAT block (/rsm:CrossIndustryDocument/rsm:SpecifiedSupplyChainTradeTransaction/ram:IncludedSupplyChainTradeLineItem[1]) and the document has 2 VAT breakdowns (S 19, S 7);"),
                 forLine::getMessage);
      assertTrue(forLine.getMessage()
                        .endsWith("the model needs its VAT category, which is not guessed"),
                 forLine::getMessage);
      var forAllowance = assertThrows(IllegalArgumentException.class,
                                      () -> PANIOLO.importInvoice(bytes(allowance),
                                                                  mode),
                                      mode::toString);
      assertTrue(forAllowance.getMessage()
                             .startsWith("Document allowance 1 states no VAT block ("),
                 forAllowance::getMessage);
      assertTrue(forAllowance.getMessage()
                             .contains("the document has 2 VAT breakdowns (S 19, S 7)"),
                 forAllowance::getMessage);
    }
  }

  @Test
  void rejectsABlockWhoseRateDoesNotTellTheCategory() {
    // The VAT breakdown without category: rate 0 may be Z, E, AE, K, G, or O.
    String zero = comfort().replaceFirst("(?s)<ram:CategoryCode>S</ram:CategoryCode>(\\s*)<ram:ApplicablePercent>19.00</ram:ApplicablePercent>",
                                         "<ram:ApplicablePercent>0.00</ram:ApplicablePercent>");
    String missing = comfort().replaceFirst("(?s)<ram:CategoryCode>S</ram:CategoryCode>\\s*<ram:ApplicablePercent>19.00</ram:ApplicablePercent>",
                                            "");
    for (var mode : ReadMode.values()) {
      var forZero = assertThrows(IllegalArgumentException.class,
                                 () -> PANIOLO.importInvoice(bytes(zero),
                                                             mode),
                                 mode::toString);
      assertEquals("VAT breakdown 1 states no VAT category code, and the rate 0.00 % does not tell it " +
                   "(/rsm:CrossIndustryDocument/rsm:SpecifiedSupplyChainTradeTransaction/ram:ApplicableSupplyChainTradeSettlement/ram:ApplicableTradeTax); the category is not guessed",
                   forZero.getMessage());
      var forMissing = assertThrows(IllegalArgumentException.class,
                                    () -> PANIOLO.importInvoice(bytes(missing),
                                                                mode),
                                    mode::toString);
      assertTrue(forMissing.getMessage()
                           .startsWith("VAT breakdown 1 states no VAT category code, and no rate does not tell it ("),
                 forMissing::getMessage);
    }
  }

  /**
   * Charges on the gross price are a separate restriction of the migration and stay visible: they
   * are not hidden by the VAT fix, and the message names the line and the element.
   */
  @Test
  void keepsRejectingChargesOnTheGrossPriceWithTheirLocation() {
    // Line 1 states an allowance on the gross price; Mustang states such discounts as charges.
    String xml = comfort().replaceFirst("(?s)(<ram:GrossPriceProductTradePrice>.*?<udt:Indicator>)false(</udt:Indicator>)",
                                        "$1true$2");
    assertTrue(!xml.equals(comfort()));
    for (var mode : ReadMode.values()) {
      var error = assertThrows(IllegalArgumentException.class,
                               () -> PANIOLO.importInvoice(bytes(xml),
                                                           mode),
                               mode::toString);
      assertEquals("Line 1: ZUGFeRD 1.0 charges on the gross price cannot be migrated " +
                   "(/rsm:CrossIndustryDocument/rsm:SpecifiedSupplyChainTradeTransaction/ram:IncludedSupplyChainTradeLineItem[1]/ram:SpecifiedSupplyChainTradeAgreement/ram:GrossPriceProductTradePrice/ram:AppliedTradeAllowanceCharge)",
                   error.getMessage());
    }
  }
}

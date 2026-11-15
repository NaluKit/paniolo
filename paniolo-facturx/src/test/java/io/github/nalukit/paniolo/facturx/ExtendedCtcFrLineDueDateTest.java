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
import io.github.nalukit.paniolo.validation.Severity;
import io.github.nalukit.paniolo.validation.ValidationMessage;
import io.github.nalukit.paniolo.xml.ExportVariant;
import io.github.nalukit.paniolo.xml.ReadMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The VAT block of a subtotal (GROUP) or information line that states only the code of the date its
 * VAT becomes due (task C02, Mustang issue #1216, Factur-X 1.07.3). Such a block has no type and no
 * VAT category. It made both read modes fail with {@code NullPointerException: tax category code}.
 * The line keeps the code, writes it again at the same line, and never turns it into the tax point
 * of the document.
 *
 * <p>The cases are derived from the official example UC12: line 1 is a GROUP line, line 4 an
 * INFORMATION line, lines 2 and 3 are DETAIL lines. The code is {@code 5}, as in the VAT breakdown
 * of the example: CII-SR-462 allows one code in the whole document.
 */
class ExtendedCtcFrLineDueDateTest {
  private static final Paniolo PANIOLO = Paniolo.create();
  private static final String  UC12    = "UC12_F202600025_SOUS-LIGNE_EXTENDED-CTC-FR_CII_Commentee.xml";
  private static final String  ONLY    = "<ram:ApplicableTradeTax><ram:DueDateTypeCode>5</ram:DueDateTypeCode></ram:ApplicableTradeTax>";
  private static final String  FULL    = "<ram:ApplicableTradeTax><ram:TypeCode>VAT</ram:TypeCode><ram:CategoryCode>S</ram:CategoryCode>" +
                                         "<ram:DueDateTypeCode>5</ram:DueDateTypeCode><ram:RateApplicablePercent>20.00</ram:RateApplicablePercent></ram:ApplicableTradeTax>";

  private static String example()
      throws IOException {
    try (InputStream input = ExtendedCtcFrLineDueDateTest.class.getResourceAsStream("/extended-ctc-fr/" + UC12)) {
      assertNotNull(input);
      return new String(input.readAllBytes(),
                        StandardCharsets.UTF_8);
    }
  }

  /**
   * UC12 with a VAT block as first element of the settlement of line {@code number}.
   */
  private static byte[] withBlock(int number,
                                  String block)
      throws IOException {
    String xml  = example();
    int    item = -1;
    for (int count = 0; count < number; count++) {
      item = xml.indexOf("<ram:IncludedSupplyChainTradeLineItem>",
                         item + 1);
    }
    String marker = "<ram:SpecifiedLineTradeSettlement>";
    int    at     = xml.indexOf(marker,
                                item) + marker.length();
    assertTrue(at > marker.length());
    return (xml.substring(0,
                          at) + block + xml.substring(at)).getBytes(StandardCharsets.UTF_8);
  }

  private static List<String> errors(byte[] xml) {
    return PANIOLO.validate(xml)
                  .messages()
                  .stream()
                  .filter(message -> message.severity() == Severity.ERROR || message.severity() == Severity.FATAL)
                  .map(ValidationMessage::ruleId)
                  .toList();
  }

  private static Invoice read(byte[] xml,
                              ReadMode mode) {
    return PANIOLO.importInvoice(xml,
                                 mode)
                  .invoice();
  }

  @ParameterizedTest(name = "line {0} with only the code")
  @ValueSource(ints = { 1, 4 })
  void keepsTheCodeOfASubtotalOrInformationLine(int number)
      throws IOException {
    byte[] xml = withBlock(number,
                           ONLY);
    assertEquals(List.of(),
                 errors(xml));
    var original = read(example().getBytes(StandardCharsets.UTF_8),
                        ReadMode.STRICT);
    for (var mode : ReadMode.values()) {
      var imported = PANIOLO.importInvoice(xml,
                                           mode);
      assertEquals(List.of(),
                   imported.warnings(),
                   mode::toString);
      var line = imported.invoice()
                         .lines()
                         .get(number - 1);
      assertNull(line.tax(),
                 mode::toString);
      assertEquals("5",
                   line.extended()
                       .taxDueDateTypeCode());
      // Only that line states a code, and the tax point of the document stays as stated.
      assertEquals(1,
                   imported.invoice()
                           .lines()
                           .stream()
                           .filter(each -> each.extended() != null && each.extended()
                                                                          .taxDueDateTypeCode() != null)
                           .count());
      assertEquals(original.taxPointDateCode(),
                   imported.invoice()
                           .taxPointDateCode());
      assertArrayEquals(xml,
                        PANIOLO.export(imported,
                                       ExportVariant.ORIGINAL,
                                       null));
      byte[] written = PANIOLO.export(imported,
                                      ExportVariant.NORMALIZED,
                                      InvoiceSyntax.CII);
      assertEquals(List.of(),
                   errors(written),
                   mode::toString);
      // The code stands at the same line, again as a block without type and category.
      var again = read(written,
                       ReadMode.STRICT);
      assertEquals(imported.invoice(),
                   again);
      assertNull(again.lines()
                      .get(number - 1)
                      .tax());
      assertTrue(new String(written,
                            StandardCharsets.UTF_8).replaceAll(">\\s+<",
                                                               "><")
                                                   .contains(ONLY));
    }
  }

  @ParameterizedTest(name = "line {0} with a complete block")
  @ValueSource(ints = { 1, 4 })
  void keepsTheCodeNextToTheCategory(int number)
      throws IOException {
    byte[] xml = withBlock(number,
                           FULL);
    for (var mode : ReadMode.values()) {
      var line = read(xml,
                      mode).lines()
                           .get(number - 1);
      assertNotNull(line.tax(),
                    mode::toString);
      assertEquals("S",
                   line.tax()
                       .code()
                       .getCode());
      assertEquals("5",
                   line.extended()
                       .taxDueDateTypeCode());
    }
  }

  @Test
  void readsALineWithoutBlockAsBefore()
      throws IOException {
    for (var mode : ReadMode.values()) {
      var invoice = read(example().getBytes(StandardCharsets.UTF_8),
                         mode);
      assertNull(invoice.lines()
                        .get(0)
                        .tax());
      assertNull(invoice.lines()
                        .get(0)
                        .extended()
                        .taxDueDateTypeCode());
    }
  }

  /**
   * A detail line needs its VAT category (BT-151): the profile rules reject it, and reading names it
   * instead of failing with a null access.
   */
  @Test
  void stillRequiresTheCategoryOfADetailLine()
      throws IOException {
    String xml  = example();
    int    item = xml.indexOf("<ram:LineID>2</ram:LineID>");
    int    from = xml.lastIndexOf("<ram:ApplicableTradeTax>",
                                  xml.indexOf("<ram:CategoryCode>",
                                              item));
    int    to   = xml.indexOf("</ram:ApplicableTradeTax>",
                              from) + "</ram:ApplicableTradeTax>".length();
    byte[] changed = (xml.substring(0,
                                    from) + ONLY + xml.substring(to)).getBytes(StandardCharsets.UTF_8);
    assertFalse(errors(changed).isEmpty());
    for (var mode : ReadMode.values()) {
      var error = assertThrows(IllegalArgumentException.class,
                               () -> read(changed,
                                          mode));
      assertEquals("Line 2: VAT category code (BT-151) is missing",
                   error.getMessage(),
                   mode::toString);
    }
  }
}

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
import io.github.nalukit.paniolo.xml.ReadWarning;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exemption reasons of the French multi-vendor example UC10 that the model cannot keep (task V02,
 * Mustang issues #1101 and #1104). The VAT breakdown of a sub-invoice is selected by the sub-invoice
 * number at the start of the exemption reason (BR-FR-MV-08); a further text after it is a conflict
 * when it differs from the breakdown. The unchanged official examples read without warning.
 */
class ExtendedCtcFrExemptionReasonTest {
  private static final Paniolo PANIOLO = Paniolo.create();
  private static final String  UC10    = "UC10_F202600004_MULTI-VENDEUR_EXTENDED-CTC-FR_CII_Commentee.xml";
  private static final String  REASON  = "<ram:ExemptionReason>#F202600004#</ram:ExemptionReason>";
  private static final String  OTHER   = "<ram:ExemptionReason>#F202600004# autre</ram:ExemptionReason>";

  private static String example(String name)
      throws IOException {
    try (InputStream input = ExtendedCtcFrExemptionReasonTest.class.getResourceAsStream("/extended-ctc-fr/" + name)) {
      assertNotNull(input,
                    name);
      return new String(input.readAllBytes(),
                        StandardCharsets.UTF_8);
    }
  }

  /**
   * Line 2 states {@code #F202600004# autre}; line 3 and the breakdown keep {@code #F202600004#}.
   */
  private static String differentSuffix()
      throws IOException {
    String xml = example(UC10);
    int    at  = xml.indexOf(REASON);
    assertTrue(at > 0 && at < xml.indexOf("<ram:ApplicableHeaderTradeSettlement>"));
    return xml.substring(0,
                         at) + OTHER + xml.substring(at + REASON.length());
  }

  /**
   * As {@link #differentSuffix()}, with the 20 % breakdown of sub-invoice F202600004 split into one
   * of line 3 (400.00) and one of line 2 (600.00) with the further text.
   */
  private static String twoReasons()
      throws IOException {
    String xml   = differentSuffix();
    int    start = xml.indexOf("<ram:ApplicableTradeTax>",
                               xml.indexOf("<ram:ApplicableHeaderTradeSettlement>"));
    int    end   = xml.indexOf("</ram:ApplicableTradeTax>",
                               start) + "</ram:ApplicableTradeTax>".length();
    String breakdown = xml.substring(start,
                                     end);
    assertTrue(breakdown.contains(REASON) && breakdown.contains("<ram:BasisAmount>1000.00</ram:BasisAmount>"),
               breakdown);
    String line3 = breakdown.replace("<ram:CalculatedAmount>200.00</ram:CalculatedAmount>",
                                     "<ram:CalculatedAmount>80.00</ram:CalculatedAmount>")
                            .replace("<ram:BasisAmount>1000.00</ram:BasisAmount>",
                                     "<ram:BasisAmount>400.00</ram:BasisAmount>");
    String line2 = breakdown.replace("<ram:CalculatedAmount>200.00</ram:CalculatedAmount>",
                                     "<ram:CalculatedAmount>120.00</ram:CalculatedAmount>")
                            .replace("<ram:BasisAmount>1000.00</ram:BasisAmount>",
                                     "<ram:BasisAmount>600.00</ram:BasisAmount>")
                            .replace(REASON,
                                     OTHER);
    return xml.substring(0,
                         start) + line3 + line2 + xml.substring(end);
  }

  private static List<String> reasons(Invoice invoice) {
    return invoice.lines()
                  .stream()
                  .filter(line -> line.tax() != null)
                  .map(line -> line.id() + ":" + line.tax()
                                                     .exemptionReason())
                  .toList();
  }

  private static List<String> ruleIds(byte[] xml) {
    return PANIOLO.validate(xml)
                  .messages()
                  .stream()
                  .map(message -> message.severity() + ":" + message.ruleId())
                  .toList();
  }

  private static List<ReadWarning> conflicts(List<ReadWarning> warnings) {
    return warnings.stream()
                   .filter(warning -> ReadWarning.TAX_EXEMPTION.equals(warning.code()))
                   .toList();
  }

  private static final List<String> EXPECTED = List.of("2:#F202600004#",
                                                       "3:#F202600004#",
                                                       "5:#111111118_F202600004#",
                                                       "6:#111111118_F202600004#");

  @ParameterizedTest
  @ValueSource(strings = { UC10,
                           "UC11_F202600022_MULTI-VENDEUR_EXTENDED-CTC-FR_CII_Commentee.xml",
                           "UC12_F202600025_SOUS-LIGNE_EXTENDED-CTC-FR_CII_Commentee.xml" })
  void readsTheOfficialExamplesWithoutConflict(String name)
      throws IOException {
    byte[] xml = example(name).getBytes(StandardCharsets.UTF_8);
    var    imported = PANIOLO.importInvoice(xml,
                                            ReadMode.STRICT);
    assertEquals(List.of(),
                 imported.warnings());
    assertEquals(List.of(),
                 PANIOLO.validate(PANIOLO.export(imported,
                                                 ExportVariant.NORMALIZED,
                                                 InvoiceSyntax.CII))
                        .messages()
                        .stream()
                        .filter(message -> message.severity() != Severity.INFO)
                        .map(ValidationMessage::toString)
                        .toList());
  }

  @Test
  void reportsALineWithAFurtherText()
      throws IOException {
    byte[] xml = differentSuffix().getBytes(StandardCharsets.UTF_8);
    // The official rules warn about the different text, but accept the document.
    assertEquals(List.of("WARNING:BR-FXEXT-S-08rev"),
                 ruleIds(xml));
    var strict = assertThrows(IllegalArgumentException.class,
                              () -> PANIOLO.importInvoice(xml,
                                                          ReadMode.STRICT));
    assertEquals("Line 2: VAT S 20 %, exemption reason '#F202600004# autre', code - differs from the VAT breakdown (S 20 %, exemption reason '#F202600004#', code -)",
                 strict.getMessage());
    var imported = PANIOLO.importInvoice(xml,
                                         ReadMode.LENIENT);
    var conflicts = conflicts(imported.warnings());
    assertEquals(List.of("2"),
                 conflicts.stream()
                          .map(ReadWarning::lineId)
                          .toList());
    assertEquals(EXPECTED,
                 reasons(imported.invoice()));
    assertArrayEquals(xml,
                      PANIOLO.export(imported,
                                     ExportVariant.ORIGINAL,
                                     null));
    assertTrue(PANIOLO.validate(PANIOLO.export(imported,
                                               ExportVariant.NORMALIZED,
                                               InvoiceSyntax.CII))
                      .isValid());
  }

  @Test
  void reportsTwoReasonsOfOneSubInvoice()
      throws IOException {
    byte[] xml = twoReasons().getBytes(StandardCharsets.UTF_8);
    assertEquals(List.of("WARNING:BR-FXEXT-S-08ini",
                         "WARNING:BR-FXEXT-S-08ini"),
                 ruleIds(xml));
    var strict = assertThrows(IllegalArgumentException.class,
                              () -> PANIOLO.importInvoice(xml,
                                                          ReadMode.STRICT));
    assertTrue(strict.getMessage()
                     .startsWith("VAT breakdown 2 (S 20 %, exemption reason '#F202600004# autre', code -) repeats category and rate of VAT breakdown 1"),
               strict::getMessage);
    var imported = PANIOLO.importInvoice(xml,
                                         ReadMode.LENIENT);
    // The breakdown conflict and line 2; line 3 states the text of the kept breakdown.
    assertEquals(List.of("-",
                         "2"),
                 conflicts(imported.warnings()).stream()
                                               .map(warning -> Objects.requireNonNullElse(warning.lineId(),
                                                                                          "-"))
                                               .toList());
    assertEquals(EXPECTED,
                 reasons(imported.invoice()));
    assertArrayEquals(xml,
                      PANIOLO.export(imported,
                                     ExportVariant.ORIGINAL,
                                     null));
    assertTrue(PANIOLO.validate(PANIOLO.export(imported,
                                               ExportVariant.NORMALIZED,
                                               InvoiceSyntax.CII))
                      .isValid());
  }
}

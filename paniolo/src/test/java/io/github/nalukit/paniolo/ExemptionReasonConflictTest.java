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
import io.github.nalukit.paniolo.model.common.ProcessControl;
import io.github.nalukit.paniolo.model.common.TaxCategory;
import io.github.nalukit.paniolo.model.common.value.DutyTaxFeeCategory;
import io.github.nalukit.paniolo.model.common.value.InvoiceTypeCode;
import io.github.nalukit.paniolo.validation.Severity;
import io.github.nalukit.paniolo.validation.ValidationMessage;
import io.github.nalukit.paniolo.validation.ValidationResult;
import io.github.nalukit.paniolo.xml.ExportVariant;
import io.github.nalukit.paniolo.xml.ReadMode;
import io.github.nalukit.paniolo.xml.ReadWarning;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exemption reasons the model cannot keep next to each other (task V02, Mustang issues #1101, #1104
 * and #1342): a further VAT breakdown of the same category and rate with another exemption reason,
 * and an EXTENDED line stating another exemption reason than its breakdown. Strict reading rejects
 * them, lenient reading reports stated and kept value. Outside EXTENDED a line exemption reason is no
 * business term, and the breakdown applies without report.
 *
 * <p>The cases are derived from a Paniolo invoice: line 1 standard rated, lines 2 and 3 exempt from
 * VAT (100.00 and 50.00) with the reason {@value #FIRST}.
 */
class ExemptionReasonConflictTest {
  private static final Paniolo PANIOLO = Paniolo.create();

  private static final String FIRST  = "Steuerbefreit gemäß § 4 Nr. 10 Buchst. a UStG";
  private static final String SECOND = "Steuerbefreit gemäß § 4 Nr. 8 Buchst. g UStG";

  private static String invoice(ProcessControl processControl) {
    var exempt = new TaxCategory(DutyTaxFeeCategory.E,
                                 BigDecimal.ZERO,
                                 FIRST,
                                 null);
    var invoice = TestInvoices.builder(InvoiceTypeCode.CODE_380,
                                       "EX-1")
                              .processControl(processControl)
                              .addLine(InvoiceLine.create()
                                                  .id("2")
                                                  .name("Versicherungsprämie A")
                                                  .quantity(BigDecimal.ONE)
                                                  .unitCode("C62")
                                                  .netUnitPrice(new BigDecimal("100.00"))
                                                  .tax(exempt)
                                                  .build())
                              .addLine(InvoiceLine.create()
                                                  .id("3")
                                                  .name("Versicherungsprämie B")
                                                  .quantity(BigDecimal.ONE)
                                                  .unitCode("C62")
                                                  .netUnitPrice(new BigDecimal("50.00"))
                                                  .tax(exempt)
                                                  .build())
                              .build();
    return PANIOLO.toXml(invoice,
                         InvoiceSyntax.CII);
  }

  private static int line(String xml,
                          int number) {
    int at = -1;
    for (int count = 0; count < number; count++) {
      at = xml.indexOf("<ram:IncludedSupplyChainTradeLineItem>",
                       at + 1);
    }
    return at;
  }

  /**
   * Line 3 states the second reason; the VAT breakdown stays as written.
   */
  private static String lineDiffers(ProcessControl processControl) {
    String xml = invoice(processControl);
    int    at  = line(xml,
                      3);
    if (!processControl.isExtended()) {
      // Outside EXTENDED the writer states no exemption reason of lines; the line gets one here.
      return xml.substring(0,
                           at) + xml.substring(at)
                                    .replaceFirst("<ram:TypeCode>VAT</ram:TypeCode>",
                                                  "<ram:TypeCode>VAT</ram:TypeCode><ram:ExemptionReason>" + SECOND + "</ram:ExemptionReason>");
    }
    assertTrue(xml.indexOf(FIRST,
                           at) > 0,
               xml);
    return xml.substring(0,
                         at) + xml.substring(at)
                                  .replaceFirst(FIRST,
                                                SECOND);
  }

  /**
   * Line 3 states the second reason, and the exempt breakdown is split into one per reason.
   */
  private static String twoBreakdowns() {
    String xml   = lineDiffers(ProcessControl.facturXExtended());
    int    start = xml.indexOf("<ram:ApplicableTradeTax>",
                               xml.indexOf("<ram:ApplicableHeaderTradeSettlement>"));
    int    exempt = xml.indexOf("<ram:CategoryCode>E</ram:CategoryCode>",
                                start);
    start = xml.lastIndexOf("<ram:ApplicableTradeTax>",
                            exempt);
    int end = xml.indexOf("</ram:ApplicableTradeTax>",
                          exempt) + "</ram:ApplicableTradeTax>".length();
    String breakdown = xml.substring(start,
                                     end);
    assertTrue(breakdown.contains("<ram:BasisAmount>150.00</ram:BasisAmount>"),
               breakdown);
    String first = breakdown.replace("<ram:BasisAmount>150.00</ram:BasisAmount>",
                                     "<ram:BasisAmount>100.00</ram:BasisAmount>");
    String second = breakdown.replace("<ram:BasisAmount>150.00</ram:BasisAmount>",
                                      "<ram:BasisAmount>50.00</ram:BasisAmount>")
                             .replace(FIRST,
                                      SECOND);
    return xml.substring(0,
                         start) + first + second + xml.substring(end);
  }

  private static byte[] bytes(String xml) {
    return xml.getBytes(StandardCharsets.UTF_8);
  }

  private static List<ValidationMessage> errors(ValidationResult result) {
    return result.messages()
                 .stream()
                 .filter(message -> message.severity() == Severity.ERROR || message.severity() == Severity.FATAL)
                 .toList();
  }

  private static List<ReadWarning> conflicts(List<ReadWarning> warnings) {
    return warnings.stream()
                   .filter(warning -> ReadWarning.TAX_EXEMPTION.equals(warning.code()))
                   .toList();
  }

  private static List<String> reasons(io.github.nalukit.paniolo.model.Invoice invoice) {
    return invoice.lines()
                  .stream()
                  .map(line -> line.id() + ":" + line.tax()
                                                     .exemptionReason())
                  .toList();
  }

  @Test
  void readsTheConsistentControlCaseWithoutWarning() {
    String xml = invoice(ProcessControl.facturXExtended());
    assertEquals(List.of(),
                 errors(PANIOLO.validate(bytes(xml),
                                         FacturXProfile.EXTENDED)));
    var read = PANIOLO.read(xml,
                            ReadMode.STRICT);
    assertEquals(List.of(),
                 read.warnings());
    assertEquals(List.of("1:null",
                         "2:" + FIRST,
                         "3:" + FIRST),
                 reasons(read.invoice()));
  }

  @Test
  void reportsAnExtendedLineWithAnotherReason() {
    String xml = lineDiffers(ProcessControl.facturXExtended());
    // The official rules warn about it (BR-FXEXT-E-08rev) but accept the document.
    var validation = PANIOLO.validate(bytes(xml),
                                      FacturXProfile.EXTENDED);
    assertTrue(validation.isValid(),
               validation::toString);
    var strict = assertThrows(IllegalArgumentException.class,
                              () -> PANIOLO.read(xml,
                                                 ReadMode.STRICT));
    assertEquals("Line 3: VAT E 0 %, exemption reason '" + SECOND + "', code - differs from the VAT breakdown (E 0 %, exemption reason '" + FIRST + "', code -)",
                 strict.getMessage());
    var imported = PANIOLO.importInvoice(bytes(xml),
                                         ReadMode.LENIENT);
    var conflicts = conflicts(imported.warnings());
    assertEquals(1,
                 conflicts.size(),
                 conflicts::toString);
    assertEquals("3",
                 conflicts.get(0)
                          .lineId());
    assertTrue(conflicts.get(0)
                        .message()
                        .endsWith("; the text of the breakdown is kept"),
               () -> conflicts.get(0)
                              .message());
    assertEquals(List.of("1:null",
                         "2:" + FIRST,
                         "3:" + FIRST),
                 reasons(imported.invoice()));
    assertTrue(imported.isNormalized());
    assertArrayEquals(bytes(xml),
                      PANIOLO.export(imported,
                                     ExportVariant.ORIGINAL,
                                     InvoiceSyntax.CII));
    assertEquals(List.of(),
                 errors(PANIOLO.validate(PANIOLO.export(imported,
                                                        ExportVariant.NORMALIZED,
                                                        InvoiceSyntax.CII),
                                         FacturXProfile.EXTENDED)));
  }

  @Test
  void reportsTwoBreakdownsOfOneCategoryAndRateWithDifferentReasons() {
    String xml = twoBreakdowns();
    var validation = PANIOLO.validate(bytes(xml),
                                      FacturXProfile.EXTENDED);
    assertTrue(validation.isValid(),
               validation::toString);
    var strict = assertThrows(IllegalArgumentException.class,
                              () -> PANIOLO.read(xml,
                                                 ReadMode.STRICT));
    assertEquals("VAT breakdown 3 (E 0 %, exemption reason '" + SECOND + "', code -) repeats category and rate of VAT breakdown 2 (E 0 %, exemption reason '" + FIRST +
                 "', code -); the model keeps one breakdown per category and rate",
                 strict.getMessage());
    var imported = PANIOLO.importInvoice(bytes(xml),
                                         ReadMode.LENIENT);
    var conflicts = conflicts(imported.warnings());
    assertEquals(2,
                 conflicts.size(),
                 conflicts::toString);
    assertEquals(null,
                 conflicts.get(0)
                          .lineId());
    assertTrue(conflicts.get(0)
                        .message()
                        .endsWith("; the text of VAT breakdown 2 is kept and the amounts are merged"),
               () -> conflicts.get(0)
                              .message());
    assertEquals("3",
                 conflicts.get(1)
                          .lineId());
    // The first breakdown of the category and rate wins; no text moves between lines unreported.
    assertEquals(List.of("1:null",
                         "2:" + FIRST,
                         "3:" + FIRST),
                 reasons(imported.invoice()));
    var exempt = imported.invoice()
                         .totals()
                         .vatBreakdowns()
                         .stream()
                         .filter(breakdown -> breakdown.tax()
                                                       .code() == DutyTaxFeeCategory.E)
                         .toList();
    assertEquals(1,
                 exempt.size());
    assertEquals(0,
                 new BigDecimal("150.00").compareTo(exempt.get(0)
                                                          .taxableAmount()));
  }

  @Test
  void appliesTheBreakdownOutsideExtendedWithoutReport() {
    // EN 16931 has no exemption reason of lines; the official KoSIT case 01.21a states one anyway.
    String xml = lineDiffers(ProcessControl.xrechnung());
    var    read = PANIOLO.read(xml,
                               ReadMode.STRICT);
    assertEquals(List.of(),
                 read.warnings());
    assertEquals(List.of("1:null",
                         "2:" + FIRST,
                         "3:" + FIRST),
                 reasons(read.invoice()));
  }
}

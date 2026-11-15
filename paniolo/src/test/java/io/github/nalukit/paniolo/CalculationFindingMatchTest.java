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
import io.github.nalukit.paniolo.model.common.DocumentAdjustment;
import io.github.nalukit.paniolo.model.common.InvoiceLine;
import io.github.nalukit.paniolo.model.common.LineAdjustment;
import io.github.nalukit.paniolo.model.common.TaxCategory;
import io.github.nalukit.paniolo.model.common.value.InvoiceTypeCode;
import io.github.nalukit.paniolo.validation.Severity;
import io.github.nalukit.paniolo.validation.ValidationMessage;
import io.github.nalukit.paniolo.xml.ReadMode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A calculation finding is lowered to information only when an official finding names the same line,
 * allowance, or charge; another occurrence of the same rule does not count, and a location that does
 * not say so reliably does not either.
 */
class CalculationFindingMatchTest {
  private static final Paniolo PANIOLO = Paniolo.create();
  private static final String  UBL     = "/Q{urn:oasis:names:specification:ubl:schema:xsd:Invoice-2}Invoice[1]/Q{urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2}";

  private static InvoiceLine line(String id) {
    return InvoiceLine.create()
                      .id(id)
                      .name("Item " + id)
                      .quantity(new BigDecimal("2"))
                      .unitCode("C62")
                      .netUnitPrice(new BigDecimal("100.00"))
                      .tax(TaxCategory.standard(new BigDecimal("19")))
                      .build();
  }

  /**
   * Three lines whose stated net amounts (200.00) are changed to 150.00 in the first and the second.
   */
  private static io.github.nalukit.paniolo.xml.ReadResult lines() {
    Invoice invoice = TestInvoices.builder(InvoiceTypeCode.CODE_380,
                                           "M-1")
                                  .addLine(line("2"))
                                  .addLine(line("3"))
                                  .build();
    String xml = PANIOLO.toXml(invoice,
                               InvoiceSyntax.UBL);
    // lines 1 and 2 get a wrong stated net amount
    int first  = xml.indexOf("<cbc:LineExtensionAmount currencyID=\"EUR\">200.00</cbc:LineExtensionAmount>");
    int second = xml.indexOf("<cbc:LineExtensionAmount currencyID=\"EUR\">200.00</cbc:LineExtensionAmount>",
                             first + 10);
    String changed = replaceAt(replaceAt(xml,
                                         first),
                               second);
    return PANIOLO.read(changed,
                        ReadMode.LENIENT);
  }

  private static String replaceAt(String xml,
                                  int index) {
    String old = "<cbc:LineExtensionAmount currencyID=\"EUR\">200.00</cbc:LineExtensionAmount>";
    return xml.substring(0,
                         index) + "<cbc:LineExtensionAmount currencyID=\"EUR\">150.00</cbc:LineExtensionAmount>" + xml.substring(index + old.length());
  }

  private static ValidationMessage official(String location) {
    return new ValidationMessage("PEPPOL-EN16931-R120",
                                 Severity.ERROR,
                                 "line net amount",
                                 location,
                                 ValidationMessage.Stage.SCHEMATRON);
  }

  private static List<ValidationMessage> lineFindings(List<ValidationMessage> findings) {
    return findings.stream()
                   .filter(finding -> finding.ruleId()
                                             .equals(CalculationCheck.LINE_NET_AMOUNT))
                   .toList();
  }

  private static Severity severityOf(List<ValidationMessage> findings,
                                     String line) {
    return findings.stream()
                   .filter(finding -> finding.location()
                                             .equals("BT-131[line=" + line + "]"))
                   .findFirst()
                   .orElseThrow()
                   .severity();
  }

  @Test
  void anOfficialFindingOfAnotherLineDoesNotCoverThisOne() {
    var read     = lines();
    var findings = lineFindings(CalculationCheck.check(read,
                                                       List.of(official(UBL + "InvoiceLine[1]"))));
    assertEquals(2,
                 findings.size(),
                 findings::toString);
    assertEquals(Severity.INFO,
                 severityOf(findings,
                            "1"));
    assertEquals(Severity.WARNING,
                 severityOf(findings,
                            "2"));
    assertTrue(findings.stream()
                       .filter(finding -> finding.location()
                                                 .equals("BT-131[line=2]"))
                       .noneMatch(finding -> finding.message()
                                                    .contains("also reported by")));
  }

  @Test
  void eachLineIsCoveredByItsOwnOfficialFinding() {
    var findings = lineFindings(CalculationCheck.check(lines(),
                                                       List.of(official(UBL + "InvoiceLine[2]"),
                                                               official(UBL + "InvoiceLine[1]"))));
    assertEquals(Severity.INFO,
                 severityOf(findings,
                            "1"));
    assertEquals(Severity.INFO,
                 severityOf(findings,
                            "2"));
  }

  @Test
  void aLocationWithoutALineDoesNotCoverALineFinding() {
    for (String location : new String[] { "",
                                          UBL + "LegalMonetaryTotal[1]",
                                          UBL + "InvoiceLine[1]/Q{urn:x}SubInvoiceLine[1]" }) {
      var findings = lineFindings(CalculationCheck.check(lines(),
                                                         List.of(official(location))));
      assertEquals(Severity.WARNING,
                   severityOf(findings,
                              "1"),
                   location);
    }
  }

  @Test
  void aLineFindingDoesNotCoverADocumentTotalAndTheReverse() {
    // a line step in the official location names a line, not the document total
    var read = PANIOLO.read(PANIOLO.toXml(TestInvoices.builder(InvoiceTypeCode.CODE_380,
                                                               "M-2")
                                                      .build(),
                                          InvoiceSyntax.UBL)
                                   .replace("<cbc:PayableAmount currencyID=\"EUR\">238.00</cbc:PayableAmount>",
                                            "<cbc:PayableAmount currencyID=\"EUR\">1.00</cbc:PayableAmount>"),
                            ReadMode.LENIENT);
    var covering = new ValidationMessage("BR-CO-16",
                                         Severity.ERROR,
                                         "payable",
                                         UBL + "InvoiceLine[1]",
                                         ValidationMessage.Stage.SCHEMATRON);
    var onDocument = new ValidationMessage("BR-CO-16",
                                           Severity.ERROR,
                                           "payable",
                                           UBL + "LegalMonetaryTotal[1]",
                                           ValidationMessage.Stage.SCHEMATRON);
    var total = CalculationCheck.check(read,
                                       List.of(covering))
                                .stream()
                                .filter(finding -> finding.ruleId()
                                                          .equals(CalculationCheck.TOTAL))
                                .toList();
    assertTrue(total.stream()
                    .allMatch(finding -> finding.severity() == Severity.WARNING),
               total::toString);
    var covered = CalculationCheck.check(read,
                                         List.of(onDocument))
                                  .stream()
                                  .filter(finding -> finding.ruleId()
                                                            .equals(CalculationCheck.TOTAL))
                                  .toList();
    assertTrue(covered.stream()
                      .anyMatch(finding -> finding.severity() == Severity.INFO && finding.message()
                                                                                         .contains("also reported by BR-CO-16")),
               covered::toString);
  }

  @Test
  void anAllowanceIsMatchedByItsPositionAmongSeveralAllowancesAndCharges() {
    // two document adjustments with amounts that do not match their percentage: the 2nd is covered only
    var wrong = TestInvoices.builder(InvoiceTypeCode.CODE_380,
                                     "M-3")
                            .addAdjustment(DocumentAdjustment.createAllowance()
                                                             .amount(new BigDecimal("10.00"))
                                                             .baseAmount(new BigDecimal("200.00"))
                                                             .percentage(new BigDecimal("5.00"))
                                                             .reason("a")
                                                             .tax(TaxCategory.standard(new BigDecimal("19")))
                                                             .build())
                            .addAdjustment(DocumentAdjustment.createCharge()
                                                             .amount(new BigDecimal("30.00"))
                                                             .baseAmount(new BigDecimal("200.00"))
                                                             .percentage(new BigDecimal("5.00"))
                                                             .reason("c")
                                                             .tax(TaxCategory.standard(new BigDecimal("19")))
                                                             .build())
                            .build();
    var read = PANIOLO.read(PANIOLO.toXml(wrong,
                                          InvoiceSyntax.UBL),
                            ReadMode.LENIENT);
    var r040 = new ValidationMessage("PEPPOL-EN16931-R040",
                                     Severity.ERROR,
                                     "allowance amount",
                                     UBL + "AllowanceCharge[2]",
                                     ValidationMessage.Stage.SCHEMATRON);
    var findings = CalculationCheck.check(read,
                                          List.of(r040))
                                   .stream()
                                   .filter(finding -> finding.ruleId()
                                                             .equals(CalculationCheck.ALLOWANCE_CHARGE))
                                   .toList();
    assertEquals(1,
                 findings.size(),
                 findings::toString);
    // the 10.00 allowance (1st) is 5 % of 200; the 2nd, the charge of 30.00, is not
    assertEquals(Severity.INFO,
                 findings.get(0)
                         .severity());
    assertTrue(findings.get(0)
                       .message()
                       .contains("charge 30.00"),
               findings.get(0)::toString);
    // the official finding of the 1st allowance or charge does not cover the 2nd
    var other = CalculationCheck.check(read,
                                       List.of(new ValidationMessage("PEPPOL-EN16931-R040",
                                                                     Severity.ERROR,
                                                                     "allowance amount",
                                                                     UBL + "AllowanceCharge[1]",
                                                                     ValidationMessage.Stage.SCHEMATRON)))
                                .stream()
                                .filter(finding -> finding.ruleId()
                                                          .equals(CalculationCheck.ALLOWANCE_CHARGE))
                                .toList();
    assertEquals(Severity.WARNING,
                 other.get(0)
                      .severity());
  }
}

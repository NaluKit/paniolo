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

import io.github.nalukit.paniolo.model.common.InvoiceLine;
import io.github.nalukit.paniolo.validation.Severity;
import io.github.nalukit.paniolo.validation.ValidationMessage;
import io.github.nalukit.paniolo.xml.AmountDeviation;
import io.github.nalukit.paniolo.xml.ReadResult;
import io.github.nalukit.paniolo.xml.ReadWarning;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * The optional calculation check of Paniolo: recalculates the amounts of an invoice with the invoice
 * model and reports what does not add up as findings of the stage
 * {@link ValidationMessage.Stage#CALCULATION}. Enable it with
 * {@link Paniolo.Builder#calculationCheck(boolean)}.
 *
 * <p>The findings are hints in addition to the official rules, never a replacement: their rule
 * identifiers start with {@code PANIOLO-CALC-}, and their severity is {@link Severity#WARNING}, so
 * they do not change {@link io.github.nalukit.paniolo.validation.ValidationResult#isValid()}. If an
 * official rule of the same result already reports the same relationship, the finding is lowered
 * to {@link Severity#INFO} and names that rule, so a duplicate can be told apart from an additional
 * finding. A total VAT amount (and the totals that follow from it) that differs by no more than one
 * cent per VAT category is reported as {@link Severity#INFO} as well: the official rules accept such
 * a rounding of the VAT amount, the model rounds once per category.
 *
 * <table>
 *   <caption>Findings and the official rules that check the same</caption>
 *   <tr><th>Rule</th><th>Relationship</th><th>Official counterpart</th></tr>
 *   <tr><td>{@link #LINE_NET_AMOUNT}</td><td>BT-131 = BT-129 &times; BT-146 / BT-149 + line charges
 *       &minus; line allowances</td><td>PEPPOL-EN16931-R120 (XRechnung only)</td></tr>
 *   <tr><td>{@link #NET_PRICE}</td><td>BT-146 = BT-148 &minus; BT-147</td>
 *       <td>PEPPOL-EN16931-R046 (XRechnung only)</td></tr>
 *   <tr><td>{@link #ALLOWANCE_CHARGE}</td><td>amount = base amount &times; percentage / 100 of
 *       document and line allowances and charges</td><td>PEPPOL-EN16931-R040 (XRechnung only)</td></tr>
 *   <tr><td>{@link #TOTAL}</td><td>document totals BT-106 to BT-115 as calculated from lines,
 *       allowances, charges and VAT breakdown</td><td>BR-CO-10 to BR-CO-17, BR-xx-08 and BR-xx-09
 *       (EN 16931)</td></tr>
 *   <tr><td>{@link #NOT_CHECKED}</td><td>the amounts could not be recalculated, e.g. because sub-lines
 *       do not add up (BR-DEX-02) or the structure is not supported; if the document states a tax
 *       other than VAT and AAM, only BT-110, BT-112 and BT-115 are not checked and named so</td>
 *       <td>&ndash;</td></tr>
 * </table>
 */
public final class CalculationCheck {
  /**
   * The line net amount (BT-131) is not quantity &times; net price plus line charges minus line
   * allowances.
   */
  public static final String LINE_NET_AMOUNT  = "PANIOLO-CALC-LINE-NET-AMOUNT";
  /**
   * The item net price (BT-146) is not the gross price (BT-148) minus the price discount (BT-147).
   */
  public static final String NET_PRICE        = "PANIOLO-CALC-NET-PRICE";
  /**
   * The amount of an allowance or charge is not its base amount &times; percentage / 100.
   */
  public static final String ALLOWANCE_CHARGE = "PANIOLO-CALC-ALLOWANCE-CHARGE";
  /**
   * A stated document total (BT-106 to BT-115) differs from the calculated one.
   */
  public static final String TOTAL            = "PANIOLO-CALC-TOTAL";
  /**
   * The amounts could not be recalculated.
   */
  public static final String NOT_CHECKED      = "PANIOLO-CALC-NOT-CHECKED";

  private static final BigDecimal                     CENT       = new BigDecimal("0.01");
  private static final List<String>                   OTHER_TAX_TOTALS = List.of("BT-110",
                                                                                 "BT-112",
                                                                                 "BT-115");
  private static final Set<String>                    ROUNDED    = Set.of("BT-110",
                                                                          "BT-112",
                                                                          "BT-115");
  private static final Pattern                        VAT_AMOUNT = Pattern.compile("BR-[A-Z]{1,2}-0[89]");
  private static final Map<String, Predicate<String>> OFFICIAL   = Map.of(LINE_NET_AMOUNT,
                                                                          "PEPPOL-EN16931-R120"::equals,
                                                                          NET_PRICE,
                                                                          "PEPPOL-EN16931-R046"::equals,
                                                                          ALLOWANCE_CHARGE,
                                                                          "PEPPOL-EN16931-R040"::equals,
                                                                          "BT-106",
                                                                          "BR-CO-10"::equals,
                                                                          "BT-107",
                                                                          "BR-CO-11"::equals,
                                                                          "BT-108",
                                                                          "BR-CO-12"::equals,
                                                                          "BT-109",
                                                                          "BR-CO-13"::equals,
                                                                          "BT-110",
                                                                          id -> id.equals("BR-CO-14") || id.equals("BR-CO-17") || VAT_AMOUNT.matcher(id)
                                                                                                                                              .matches(),
                                                                          "BT-112",
                                                                          "BR-CO-15"::equals,
                                                                          "BT-115",
                                                                          "BR-CO-16"::equals);

  private CalculationCheck() {
  }

  /**
   * Checks the amounts of an invoice read in {@link io.github.nalukit.paniolo.xml.ReadMode#LENIENT}.
   *
   * @param imported the invoice as read, with its warnings and deviations
   * @param official the findings of the official rules, to mark duplicates
   * @return the findings of the calculation stage
   */
  static List<ValidationMessage> check(ReadResult imported,
                                       List<ValidationMessage> official) {
    var findings = new ArrayList<ValidationMessage>();
    var lines = new ArrayList<InvoiceLine>();
    flatten(imported.invoice()
                    .lines(),
            lines);
    var topLevel = imported.invoice()
                           .lines();
    for (ReadWarning warning : imported.warnings()) {
      if (ReadWarning.LINE_NET_PRICE.equals(warning.code())) {
        findings.add(finding(LINE_NET_AMOUNT,
                             LINE_NET_AMOUNT,
                             warning.message(),
                             "BT-131[line=" + warning.lineId() + "]",
                             official,
                             lineTarget(lines,
                                        topLevel,
                                        warning.lineId(),
                                        null)));
      }
    }
    for (var line : lines) {
      // A line whose net price was recalculated keeps gross price minus discount equal to it, so the
      // check never fires for it; it must not be skipped by identifier, which several lines may share.
      if (line.grossPrice() != null && line.netUnitPrice() != null) {
        BigDecimal discount = line.priceDiscount() == null ?
                              BigDecimal.ZERO :
                              line.priceDiscount();
        BigDecimal expected = line.grossPrice()
                                  .subtract(discount);
        if (expected.compareTo(line.netUnitPrice()) != 0) {
          findings.add(finding(NET_PRICE,
                               NET_PRICE,
                               "Line " +
                               line.id() +
                               ": net price " +
                               line.netUnitPrice()
                                   .toPlainString() +
                               " is not gross price " +
                               line.grossPrice()
                                   .toPlainString() +
                               " minus price discount " +
                               discount.toPlainString() +
                               " = " +
                               expected.toPlainString(),
                               "BT-146[line=" + line.id() + "]",
                               official,
                               lineTarget(lines,
                                          topLevel,
                                          line,
                                          null)));
        }
      }
      int adjustmentNumber = 0;
      for (var adjustment : line.adjustments()) {
        adjustmentNumber++;
        allowanceCharge(findings,
                        adjustment.charge(),
                        adjustment.amount(),
                        adjustment.baseAmount(),
                        adjustment.percentage(),
                        "Line " + line.id() + ": ",
                        (adjustment.charge() ?
                         "BG-28" :
                         "BG-27") + "[line=" + line.id() + "]",
                        official,
                        lineTarget(lines,
                                   topLevel,
                                   line,
                                   adjustmentNumber));
      }
    }
    int documentAdjustment = 0;
    for (var adjustment : imported.invoice()
                                  .adjustments()) {
      documentAdjustment++;
      allowanceCharge(findings,
                      adjustment.charge(),
                      adjustment.amount(),
                      adjustment.baseAmount(),
                      adjustment.percentage(),
                      "",
                      adjustment.charge() ?
                      "BG-21" :
                      "BG-20",
                      official,
                      new Target(false,
                                 0,
                                 0,
                                 documentAdjustment));
    }
    // The official rules accept a VAT amount rounded by up to one cent per VAT category (BR-CO-17).
    BigDecimal tolerance = CENT.multiply(BigDecimal.valueOf(Math.max(1,
                                                                     imported.invoice()
                                                                             .totals()
                                                                             .vatBreakdowns()
                                                                             .size())));
    // Taxes of another type than VAT and the insurance tax AAM are not in the model: the totals that include them
    // cannot be recalculated, so a difference there is no finding about the sender.
    var otherTaxes = imported.warnings()
                             .stream()
                             .filter(warning -> ReadWarning.OTHER_TAX.equals(warning.code()))
                             .map(ReadWarning::message)
                             .toList();
    if (!otherTaxes.isEmpty()) {
      findings.add(new ValidationMessage(NOT_CHECKED,
                                         Severity.INFO,
                                         "The amounts BT-110, BT-112 and BT-115 were not recalculated: the document states taxes the model does not support. " + String.join("; ",
                                                                                                                                                          otherTaxes),
                                         String.join(", ",
                                                     OTHER_TAX_TOTALS),
                                         ValidationMessage.Stage.CALCULATION));
    }
    for (AmountDeviation deviation : imported.deviations()) {
      if (!otherTaxes.isEmpty() && OTHER_TAX_TOTALS.contains(deviation.businessTerm())) {
        continue;
      }
      if (deviation.lineId() == null) {
        boolean rounding = ROUNDED.contains(deviation.businessTerm()) && deviation.stated()
                                                                                 .subtract(deviation.calculated() == null ?
                                                                                           BigDecimal.ZERO :
                                                                                           deviation.calculated())
                                                                                 .abs()
                                                                                 .compareTo(tolerance) <= 0;
        var finding = finding(TOTAL,
                             deviation.businessTerm(),
                             "Stated " +
                             deviation.businessTerm() +
                             " " +
                             deviation.stated()
                                      .toPlainString() +
                             " differs from the calculated " +
                             (deviation.calculated() == null ?
                              "0" :
                              deviation.calculated()
                                       .toPlainString()),
                             deviation.businessTerm(),
                             official,
                             DOCUMENT);
        findings.add(rounding && finding.severity() == Severity.WARNING ?
                     new ValidationMessage(finding.ruleId(),
                                           Severity.INFO,
                                           finding.message() + "; within the rounding of the VAT amount that the official rules accept",
                                           finding.location(),
                                           finding.stage()) :
                     finding);
      }
    }
    return findings;
  }

  /**
   * The finding when the amounts cannot be recalculated.
   *
   * @param reason why the invoice could not be read
   * @return the finding
   */
  static ValidationMessage notChecked(String reason) {
    return new ValidationMessage(NOT_CHECKED,
                                 Severity.INFO,
                                 "The amounts were not recalculated: " + reason,
                                 "",
                                 ValidationMessage.Stage.CALCULATION);
  }

  private static void allowanceCharge(List<ValidationMessage> findings,
                                      boolean charge,
                                      BigDecimal amount,
                                      BigDecimal base,
                                      BigDecimal percentage,
                                      String prefix,
                                      String location,
                                      List<ValidationMessage> official,
                                      Target target) {
    if (amount == null || base == null || percentage == null) {
      return;
    }
    BigDecimal expected = base.multiply(percentage)
                              .divide(BigDecimal.valueOf(100),
                                      2,
                                      RoundingMode.HALF_UP);
    // The amount may be rounded once more than the expected value.
    if (amount.subtract(expected)
              .abs()
              .compareTo(CENT) > 0) {
      findings.add(finding(ALLOWANCE_CHARGE,
                           ALLOWANCE_CHARGE,
                           prefix +
                           (charge ?
                            "charge " :
                            "allowance ") +
                           amount.toPlainString() +
                           " is not base amount " +
                           base.toPlainString() +
                           " x " +
                           percentage.toPlainString() +
                           " % = " +
                           expected.toPlainString(),
                           location,
                           official,
                           target));
    }
  }

  /**
   * Where the amount of a finding is in the document, as far as the official findings can be matched
   * to it: a line (by its position among the top-level lines for UBL and among all lines for CII) with
   * the position of an allowance or charge in it, or the document itself. A line whose identifier
   * repeats cannot be told apart and has no target.
   *
   * @param line        a line, false for document level
   * @param topIndex    1-based position among the top-level lines; 0 for none
   * @param flatIndex   1-based position among all lines in document order; 0 for none
   * @param adjustment  1-based position of the allowance or charge in its line or the document; 0 for none
   */
  private record Target(boolean line, int topIndex, int flatIndex, int adjustment) {
  }

  private static final Target DOCUMENT = new Target(false,
                                                    0,
                                                    0,
                                                    0);

  private static final Pattern LINE_STEP       = Pattern.compile("(?<![A-Za-z])(?:InvoiceLine|CreditNoteLine)\\[(\\d+)\\]");
  private static final Pattern CII_LINE_STEP   = Pattern.compile("IncludedSupplyChainTradeLineItem\\[(\\d+)\\]");
  private static final Pattern ADJUSTMENT_STEP = Pattern.compile("AllowanceCharge\\[(\\d+)\\]");

  /**
   * The target of a line instance: its position tells it apart from other lines with the same
   * identifier.
   */
  private static Target lineTarget(List<InvoiceLine> flat,
                                   List<InvoiceLine> topLevel,
                                   InvoiceLine line,
                                   Integer adjustment) {
    int found = -1;
    for (int i = 0; i < flat.size(); i++) {
      if (flat.get(i) == line) {
        found = i;
        break;
      }
    }
    if (found < 0) {
      return null;
    }
    int top = 0;
    for (int i = 0; i < topLevel.size(); i++) {
      if (topLevel.get(i) == line) {
        top = i + 1;
      }
    }
    return new Target(true,
                      top,
                      found + 1,
                      adjustment == null ?
                      0 :
                      adjustment);
  }

  private static Target lineTarget(List<InvoiceLine> flat,
                                   List<InvoiceLine> topLevel,
                                   String lineId,
                                   Integer adjustment) {
    int found = -1;
    for (int i = 0; i < flat.size(); i++) {
      if (java.util.Objects.equals(flat.get(i)
                                       .id(),
                                   lineId)) {
        if (found >= 0) {
          return null;
        }
        found = i;
      }
    }
    if (found < 0) {
      return null;
    }
    int top = 0;
    for (int i = 0; i < topLevel.size(); i++) {
      if (topLevel.get(i) == flat.get(found)) {
        top = i + 1;
      }
    }
    return new Target(true,
                      top,
                      found + 1,
                      adjustment == null ?
                      0 :
                      adjustment);
  }

  /**
   * Whether the location of an official finding names this very line, allowance, or charge. A location
   * that does not say so reliably does not count: the finding is then no duplicate.
   */
  private static boolean refersTo(String location,
                                  Target target) {
    if (target == null) {
      return false;
    }
    String where = location == null ?
                   "" :
                   location;
    var ubl = LINE_STEP.matcher(where);
    var cii = CII_LINE_STEP.matcher(where);
    int number = -1;
    boolean lineStep = false;
    if (where.contains("SubInvoiceLine[")) {
      return false;
    }
    while (ubl.find()) {
      lineStep = true;
      number   = Integer.parseInt(ubl.group(1));
      if (target.line() && number != target.topIndex()) {
        return false;
      }
    }
    while (cii.find()) {
      lineStep = true;
      number   = Integer.parseInt(cii.group(1));
      if (target.line() && number != target.flatIndex()) {
        return false;
      }
    }
    if (target.line() != lineStep) {
      return false;
    }
    if (target.adjustment() > 0) {
      var step = ADJUSTMENT_STEP.matcher(where);
      boolean seen = false;
      while (step.find()) {
        seen = true;
        if (Integer.parseInt(step.group(1)) != target.adjustment()) {
          return false;
        }
      }
      return seen;
    }
    return true;
  }

  private static ValidationMessage finding(String ruleId,
                                           String relationship,
                                           String message,
                                           String location,
                                           List<ValidationMessage> official,
                                           Target target) {
    Predicate<String> counterpart = OFFICIAL.get(relationship);
    Set<String> duplicates = new TreeSet<>();
    if (counterpart != null) {
      for (var finding : official) {
        // An official finding reported as information only, e.g. PEPPOL-EN16931-R120 of a document
        // that does not state XRechnung, does not replace the warning. It must also name this line or
        // this allowance or charge, not another one of the same rule.
        if (finding.ruleId() != null && finding.stage() != ValidationMessage.Stage.CALCULATION && finding.severity() != Severity.INFO && counterpart.test(finding.ruleId()) && refersTo(finding.location(),
                                                                                                                                                                                  target)) {
          duplicates.add(finding.ruleId());
        }
      }
    }
    return duplicates.isEmpty() ?
           new ValidationMessage(ruleId,
                                 Severity.WARNING,
                                 message,
                                 location,
                                 ValidationMessage.Stage.CALCULATION) :
           new ValidationMessage(ruleId,
                                 Severity.INFO,
                                 message + "; also reported by " + String.join(", ",
                                                                               duplicates),
                                 location,
                                 ValidationMessage.Stage.CALCULATION);
  }

  private static void flatten(List<InvoiceLine> lines,
                              List<InvoiceLine> target) {
    for (var line : lines) {
      target.add(line);
      flatten(line.subLines(),
              target);
    }
  }
}

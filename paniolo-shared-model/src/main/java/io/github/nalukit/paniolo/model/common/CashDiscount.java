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
package io.github.nalukit.paniolo.model.common;

import io.github.nalukit.paniolo.model.common.validation.ModelChecks;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Cash discount (Skonto) for payment within a number of days. It is transmitted in the payment
 * terms (BT-20) in the structured XRechnung form {@code #SKONTO#TAGE=14#PROZENT=2.00#}, optionally
 * with {@code BASISBETRAG} when the discount applies only to part of the amount due.
 *
 * @param days       payment period in days
 * @param percent    discount percentage, written with two decimals
 * @param baseAmount amount the discount applies to; null for the amount due (BT-115)
 */
public record CashDiscount(int days, BigDecimal percent, BigDecimal baseAmount) {
  /**
   * The XRechnung form of a cash discount line (BR-DE-18).
   */
  private static final Pattern LINE = Pattern.compile("#SKONTO#TAGE=([0-9]+)#PROZENT=([0-9]+\\.[0-9]{2})(?:#BASISBETRAG=(-?[0-9]+\\.[0-9]{2}))?#");

  /**
   * Applies the checks of the model.
   *
   * @throws NullPointerException     if a required value is null
   * @throws IllegalArgumentException if a value violates the checks of the model
   */
  public CashDiscount {
    if (days <= 0) {
      throw new IllegalArgumentException("Cash discount days must be positive");
    }
    percent = ModelChecks.nonNegative(Objects.requireNonNull(percent,
                                                             "percent"),
                                      "cash discount percent")
                         .setScale(2,
                                   RoundingMode.HALF_UP);
    if (baseAmount != null) {
      baseAmount = ModelChecks.money(baseAmount);
    }
  }

  /**
   * A cash discount on the amount due, e.g. 2 % within 14 days.
   *
   * @param days    payment period in days; must be positive
   * @param percent discount percentage
   * @return the cash discount without base amount
   * @throws IllegalArgumentException if the days are not positive
   */
  public static CashDiscount of(int days,
                                BigDecimal percent) {
    return new CashDiscount(days,
                            percent,
                            null);
  }

  /**
   * Combines free payment terms text and cash discounts into the payment terms (BT-20): the text
   * first, then one line per discount, each followed by a line break as BR-DE-18 requires.
   *
   * @param text      free payment terms text, or null
   * @param discounts the cash discounts
   * @return the payment terms, or null if both are empty
   */
  public static String paymentTerms(String text,
                                    List<CashDiscount> discounts) {
    var result = new StringBuilder();
    if (text != null && !text.isBlank()) {
      result.append(text);
    }
    for (CashDiscount discount : discounts) {
      if (!result.isEmpty() && result.charAt(result.length() - 1) != '\n') {
        result.append('\n');
      }
      result.append(discount.toPaymentTermsLine())
            .append('\n');
    }
    return result.isEmpty() ?
           null :
           result.toString();
  }

  /**
   * Splits payment terms (BT-20) into the free text and the structured cash discount lines. A line
   * that starts like a cash discount but cannot be read as one, e.g. with empty values, zero days, or
   * a number of days beyond {@code int}, stays in the free text unchanged and is listed in
   * {@link Terms#unreadable()}; no discount is guessed and nothing is thrown.
   *
   * @param paymentTerms the payment terms, or null
   * @return the free text, the cash discounts, and the unreadable cash discount lines
   */
  public static Terms parse(String paymentTerms) {
    if (paymentTerms == null) {
      return new Terms(null,
                       List.of());
    }
    var text       = new ArrayList<String>();
    var discounts  = new ArrayList<CashDiscount>();
    var unreadable = new ArrayList<Unreadable>();
    for (String line : paymentTerms.split("\\r?\\n")) {
      var matcher = LINE.matcher(line.strip());
      if (matcher.matches()) {
        try {
          discounts.add(new CashDiscount(Integer.parseInt(matcher.group(1)),
                                         new BigDecimal(matcher.group(2)),
                                         matcher.group(3) == null ?
                                         null :
                                         new BigDecimal(matcher.group(3))));
          continue;
        } catch (IllegalArgumentException e) {
          // NumberFormatException included: days beyond int.
          unreadable.add(new Unreadable(line.strip(),
                                        e instanceof NumberFormatException ?
                                        "the number of days " + matcher.group(1) + " is too large" :
                                        e.getMessage()));
        }
      } else if (line.strip()
                     .startsWith("#SKONTO#")) {
        unreadable.add(new Unreadable(line.strip(),
                                      "not of the form #SKONTO#TAGE=n#PROZENT=n.nn# (BR-DE-18)"));
      }
      text.add(line);
    }
    String joined = String.join("\n",
                                text)
                          .strip();
    return new Terms(joined.isEmpty() ?
                     null :
                     joined,
                     discounts,
                     unreadable);
  }

  /**
   * The structured payment terms line, e.g. {@code #SKONTO#TAGE=14#PROZENT=2.00#}.
   *
   * @return the payment terms line
   */
  public String toPaymentTermsLine() {
    return "#SKONTO#TAGE=" +
           days +
           "#PROZENT=" +
           percent.toPlainString() +
           "#" +
           (baseAmount == null ?
            "" :
            "BASISBETRAG=" + baseAmount.toPlainString() + "#");
  }

  /**
   * Payment terms split into free text and cash discounts.
   *
   * @param text       free payment terms text, or null; it keeps unreadable cash discount lines
   * @param discounts  structured cash discounts
   * @param unreadable lines that start like a cash discount but cannot be read as one
   */
  public record Terms(String text, List<CashDiscount> discounts, List<Unreadable> unreadable) {
    /**
     * Copies the lists.
     */
    public Terms {
      discounts  = List.copyOf(discounts);
      unreadable = List.copyOf(unreadable);
    }

    /**
     * Payment terms without unreadable cash discount lines.
     *
     * @param text      free payment terms text, or null
     * @param discounts structured cash discounts
     */
    public Terms(String text,
                 List<CashDiscount> discounts) {
      this(text,
           discounts,
           List.of());
    }
  }

  /**
   * A payment terms line that starts like a cash discount but cannot be read as one.
   *
   * @param line   the line as stated
   * @param reason why it is not a cash discount
   */
  public record Unreadable(String line, String reason) {
  }
}

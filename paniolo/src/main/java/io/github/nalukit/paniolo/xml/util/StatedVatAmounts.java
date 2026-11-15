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
package io.github.nalukit.paniolo.xml.util;

import io.github.nalukit.paniolo.model.Invoice;
import io.github.nalukit.paniolo.model.common.InvoiceTotals;
import io.github.nalukit.paniolo.model.common.TaxCategory;
import io.github.nalukit.paniolo.model.common.VatAmount;
import io.github.nalukit.paniolo.model.common.VatBreakdown;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

/**
 * Keeps the VAT category tax amounts (BT-117) a document states when they differ from the amounts
 * the model rounds once per category only by the rounding of their lines, allowances, and charges,
 * e.g. because the seller rounds the VAT of every line (BR-FXEXT-S-09). The model then states the
 * same VAT and the same amount due as the document. A larger difference is left to the comparison
 * of the totals: strict reading rejects it, lenient reading reports it.
 */
public final class StatedVatAmounts {
  private final List<TaxCategory> categories = new ArrayList<>();
  private final List<BigDecimal>  amounts    = new ArrayList<>();

  /**
   * Adds the stated amount of a VAT breakdown of the document.
   *
   * @param category the VAT category of the breakdown as read
   * @param amount   the stated VAT category tax amount, or null
   */
  public void add(TaxCategory category,
                  BigDecimal amount) {
    if (category != null && amount != null) {
      categories.add(category);
      amounts.add(amount);
    }
  }

  /**
   * Builds the invoice and keeps every stated VAT amount within the rounding tolerance of its
   * category.
   *
   * @param builder the invoice read so far
   * @return the invoice, with the stated VAT amounts it keeps
   */
  public Invoice build(Invoice.Builder builder) {
    var invoice = builder.build();
    if (categories.isEmpty() || invoice.hasStatedTotals()) {
      return invoice;
    }
    var breakdowns = invoice.totals()
                            .vatBreakdowns();
    // Several stated breakdowns may fall into one breakdown of the model; their amounts add up.
    var stated = new LinkedHashMap<VatBreakdown, BigDecimal>();
    for (int index = 0; index < categories.size(); index++) {
      var breakdown = breakdownOf(breakdowns,
                                  categories.get(index));
      if (breakdown == null) {
        return invoice;
      }
      stated.merge(breakdown,
                   amounts.get(index),
                   BigDecimal::add);
    }
    boolean kept = false;
    for (var entry : stated.entrySet()) {
      var        breakdown = entry.getKey();
      BigDecimal amount    = entry.getValue();
      if (amount.compareTo(breakdown.taxAmount()) != 0 && breakdown.tax()
                                                                   .rate() != null && breakdown.tax()
                                                                                              .rate()
                                                                                              .signum() > 0 && amount.subtract(breakdown.taxAmount())
                                                                                                                     .abs()
                                                                                                                     .compareTo(InvoiceTotals.roundingTolerance(invoice,
                                                                                                                                                                breakdown.tax())) <= 0) {
        builder.addVatAmount(new VatAmount(breakdown.tax(),
                                           amount));
        kept = true;
      }
    }
    return kept ?
           builder.build() :
           invoice;
  }

  /**
   * The breakdown of the model with the same category, rate, and exemption, or else the only one
   * with the same category and rate.
   */
  private static VatBreakdown breakdownOf(List<VatBreakdown> breakdowns,
                                          TaxCategory category) {
    for (var breakdown : breakdowns) {
      if (breakdown.tax()
                   .equals(category)) {
        return breakdown;
      }
    }
    var same = breakdowns.stream()
                         .filter(breakdown -> breakdown.tax()
                                                       .code() == category.code() && Objects.equals(rate(breakdown.tax()),
                                                                                                    rate(category)))
                         .toList();
    return same.size() == 1 ?
           same.get(0) :
           null;
  }

  private static BigDecimal rate(TaxCategory category) {
    return category.rate() == null ?
           null :
           category.rate()
                   .stripTrailingZeros();
  }
}

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
package io.github.nalukit.paniolo.model.order;

import io.github.nalukit.paniolo.model.common.TaxCategory;
import io.github.nalukit.paniolo.model.common.validation.ModelChecks;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Sums of an Order-X document. Order-X has no VAT breakdown; VAT is calculated per category and
 * rate only if every line has a VAT category, otherwise {@code taxAmount} and {@code grandTotal}
 * are null.
 *
 * @param lineTotal      sum of the line net amounts
 * @param allowanceTotal sum of the document allowances
 * @param chargeTotal    sum of the document charges
 * @param taxBasisTotal  line total minus allowances plus charges
 * @param taxAmount      VAT, or null
 * @param grandTotal     tax basis plus VAT, or null
 */
public record OrderTotals(BigDecimal lineTotal, BigDecimal allowanceTotal, BigDecimal chargeTotal, BigDecimal taxBasisTotal, BigDecimal taxAmount, BigDecimal grandTotal) {
  /**
   * Calculates the totals of an order from its lines, allowances, and charges; VAT only if every
   * line has a VAT category.
   *
   * @param order the order
   * @return the calculated totals
   */
  public static OrderTotals calculate(Order order) {
    Map<String, BigDecimal>  bases      = new LinkedHashMap<>();
    Map<String, TaxCategory> categories = new LinkedHashMap<>();
    boolean taxed = !order.lines()
                          .isEmpty();
    BigDecimal lines = BigDecimal.ZERO;
    for (var line : order.lines()) {
      lines = lines.add(line.netAmount());
      if (line.tax() == null) {
        taxed = false;
      } else {
        merge(bases,
              categories,
              line.tax(),
              line.netAmount());
      }
    }
    BigDecimal allowances = BigDecimal.ZERO;
    BigDecimal charges    = BigDecimal.ZERO;
    for (var adjustment : order.adjustments()) {
      if (adjustment.charge()) {
        charges = charges.add(adjustment.amount());
      } else {
        allowances = allowances.add(adjustment.amount());
      }
      merge(bases,
            categories,
            adjustment.tax(),
            adjustment.charge() ?
            adjustment.amount() :
            adjustment.amount()
                      .negate());
    }
    BigDecimal basis = ModelChecks.money(lines.subtract(allowances)
                                              .add(charges));
    BigDecimal tax = null;
    if (taxed) {
      tax = BigDecimal.ZERO;
      for (var entry : bases.entrySet()) {
        var category = categories.get(entry.getKey());
        if (category.rate() != null) {
          tax = tax.add(ModelChecks.money(entry.getValue()
                                               .multiply(category.rate())
                                               .movePointLeft(2)));
        }
      }
      tax = ModelChecks.money(tax);
    }
    return new OrderTotals(ModelChecks.money(lines),
                           ModelChecks.money(allowances),
                           ModelChecks.money(charges),
                           basis,
                           tax,
                           tax == null ?
                           null :
                           basis.add(tax));
  }

  private static void merge(Map<String, BigDecimal> bases,
                            Map<String, TaxCategory> categories,
                            TaxCategory tax,
                            BigDecimal amount) {
    String key = tax.code()
                    .getCode() +
                 ":" +
                 (tax.rate() == null ?
                  "" :
                  tax.rate()
                     .stripTrailingZeros()
                     .toPlainString());
    categories.putIfAbsent(key,
                           tax);
    bases.merge(key,
                amount,
                BigDecimal::add);
  }
}

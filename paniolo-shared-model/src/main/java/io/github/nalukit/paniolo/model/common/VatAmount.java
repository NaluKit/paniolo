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
import java.util.Objects;

/**
 * A stated VAT category tax amount (BT-117) that replaces the amount the model rounds once per
 * category, e.g. because the seller rounds the VAT of every line. It may differ from the rounded
 * amount only by the rounding of the amounts of the category: 0.01 per invoice line, document
 * allowance, document charge, and EXTENDED logistics service charge of that category, the tolerance
 * of BR-FXEXT-S-09. The totals BT-110, BT-112, and BT-115 follow from it; the taxable amounts are
 * always calculated.
 *
 * @param tax    VAT category and rate of the breakdown; the rate must be above zero
 * @param amount the VAT category tax amount, rounded to cents
 */
public record VatAmount(TaxCategory tax, BigDecimal amount) {
  /**
   * Applies the checks of the model.
   *
   * @throws NullPointerException     if a value is null
   * @throws IllegalArgumentException if the category has no rate above zero
   */
  public VatAmount {
    Objects.requireNonNull(tax,
                           "tax");
    if (tax.rate() == null || tax.rate()
                                 .signum() <= 0) {
      throw new IllegalArgumentException("A stated VAT amount requires a VAT rate above zero, not category " + tax.code()
                                                                                                                 .getCode() + " " + (tax.rate() == null ?
                                                                                                                                     "without rate" :
                                                                                                                                     tax.rate()
                                                                                                                                        .toPlainString() + " %"));
    }
    amount = ModelChecks.money(Objects.requireNonNull(amount,
                                                      "amount"));
  }
}

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
package io.github.nalukit.paniolo.model.extended;

import io.github.nalukit.paniolo.model.common.validation.ModelChecks;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * Structured cash discount or late payment penalty of a payment term in the Factur-X profile
 * EXTENDED. At least the percentage or the amount is required.
 *
 * @param basisDate   date the period starts from, or null for the invoice date
 * @param periodDays  period in days, or null
 * @param basisAmount amount the percentage applies to, or null
 * @param percent     discount or penalty percentage, or null
 * @param amount      discount or penalty amount, or null
 */
public record PaymentTermsAdjustment(LocalDate basisDate, Integer periodDays, BigDecimal basisAmount, BigDecimal percent, BigDecimal amount) {
  /**
   * Applies the checks of the model.
   *
   * @throws IllegalArgumentException if a value violates the checks of the model
   */
  public PaymentTermsAdjustment {
    if (periodDays != null && periodDays <= 0) {
      throw new IllegalArgumentException("Payment terms period days must be positive");
    }
    if (percent == null && amount == null) {
      throw new IllegalArgumentException("A cash discount or penalty requires a percentage or an amount");
    }
    if (percent != null) {
      ModelChecks.nonNegative(percent,
                              "payment terms percentage");
    }
    if (basisAmount != null) {
      basisAmount = ModelChecks.money(basisAmount);
    }
    if (amount != null) {
      amount = ModelChecks.money(amount);
    }
  }

  /**
   * Percentage for payment within a number of days, e.g. 2 % within 14 days.
   *
   * @param periodDays the period in days
   * @param percent    the discount or penalty percentage
   * @return the adjustment
   */
  public static PaymentTermsAdjustment of(int periodDays,
                                          BigDecimal percent) {
    return new PaymentTermsAdjustment(null,
                                      periodDays,
                                      null,
                                      percent,
                                      null);
  }

  /**
   * Returns this adjustment with the amount calculated from the base amount and the percentage,
   * rounded half up to cents. The base amount of this adjustment is used; without one, no
   * calculation basis exists. An amount that is already set is kept unchanged. Neither the due
   * date nor an invoice total is affected.
   *
   * @return this adjustment if it has an amount, otherwise a copy with the calculated amount
   * @throws IllegalStateException if the amount is missing and no base amount is set
   */
  public PaymentTermsAdjustment withComputedAmount() {
    return withComputedAmount(null);
  }

  /**
   * Returns this adjustment with the amount calculated from the base amount and the percentage,
   * rounded half up to cents. The base amount of this adjustment takes precedence; the given base
   * amount from the calling context is used only if this adjustment has none. The base amount of
   * the result stays as it is: a base amount taken from the context is not stored. An amount that
   * is already set is kept unchanged. Neither the due date nor an invoice total is affected.
   *
   * @param contextBaseAmount the base amount from the calling context, e.g. the amount due, or null
   * @return this adjustment if it has an amount, otherwise a copy with the calculated amount
   * @throws IllegalStateException if the amount is missing and neither a base amount nor a
   *                               context base amount is available
   */
  public PaymentTermsAdjustment withComputedAmount(BigDecimal contextBaseAmount) {
    if (amount != null) {
      return this;
    }
    BigDecimal base = basisAmount != null ?
                      basisAmount :
                      contextBaseAmount;
    if (base == null) {
      throw new IllegalStateException("The amount cannot be calculated: no base amount is given");
    }
    return new PaymentTermsAdjustment(basisDate,
                                      periodDays,
                                      basisAmount,
                                      percent,
                                      base.multiply(percent)
                                          .divide(BigDecimal.valueOf(100),
                                                  2,
                                                  RoundingMode.HALF_UP));
  }
}

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
}

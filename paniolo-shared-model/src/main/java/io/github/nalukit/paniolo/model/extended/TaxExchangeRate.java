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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Exchange rate from the invoice currency (BT-5) to the VAT accounting currency (BT-6) in the
 * Factur-X profile EXTENDED.
 *
 * @param rate units of the VAT accounting currency per unit of the invoice currency
 * @param date date of the rate, or null
 */
public record TaxExchangeRate(BigDecimal rate, LocalDate date) {
  /**
   * Applies the checks of the model.
   *
   * @throws NullPointerException     if a required value is null
   * @throws IllegalArgumentException if a value violates the checks of the model
   */
  public TaxExchangeRate {
    Objects.requireNonNull(rate,
                           "exchange rate");
    if (rate.signum() <= 0) {
      throw new IllegalArgumentException("Exchange rate must be positive");
    }
  }
}

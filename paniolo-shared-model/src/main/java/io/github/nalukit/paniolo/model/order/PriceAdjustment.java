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

import io.github.nalukit.paniolo.model.common.validation.ModelChecks;

import java.math.BigDecimal;

/**
 * Allowance or charge on the gross price of an Order-X line (COMFORT), e.g. a discount or a
 * recycling charge per unit.
 *
 * @param charge     true for a charge, false for an allowance
 * @param amount     amount per price base quantity; not negative
 * @param reasonCode UNTDID 5189 (allowance) or 7161 (charge) reason code, or null
 * @param reason     reason as text, or null
 */
public record PriceAdjustment(boolean charge, BigDecimal amount, String reasonCode, String reason) {
  /**
   * Applies the checks of the model.
   *
   * @throws IllegalArgumentException if a value violates the checks of the model
   */
  public PriceAdjustment {
    ModelChecks.nonNegative(amount,
                            "price adjustment amount");
  }
}

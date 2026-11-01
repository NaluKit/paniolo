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
import java.util.Objects;

/**
 * Financial adjustment of the Factur-X profile EXTENDED, e.g. a deduction agreed after invoicing.
 * It changes the amount due for payment (BT-115) without changing the VAT (BR-FXEXT-CO-16).
 *
 * @param reason reason of the adjustment
 * @param amount amount added to the amount due; negative for a deduction
 */
public record FinancialAdjustment(String reason, BigDecimal amount) {
  /**
   * Applies the checks of the model.
   *
   * @throws NullPointerException     if a required value is null
   * @throws IllegalArgumentException if a value violates the checks of the model
   */
  public FinancialAdjustment {
    ModelChecks.required(reason,
                         "financial adjustment reason");
    amount = ModelChecks.money(Objects.requireNonNull(amount,
                                                      "financial adjustment amount"));
  }
}

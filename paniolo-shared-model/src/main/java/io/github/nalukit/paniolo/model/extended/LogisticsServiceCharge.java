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

import io.github.nalukit.paniolo.model.common.TaxCategory;
import io.github.nalukit.paniolo.model.common.validation.ModelChecks;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Logistics service charge of the Factur-X profile EXTENDED, e.g. freight or packaging. It counts
 * like a document level charge: it is part of the sum of charges (BT-108), the amount without VAT
 * (BT-109), and the VAT breakdown of its category.
 *
 * @param description service description
 * @param amount      charge amount without VAT
 * @param tax         VAT category and rate
 */
public record LogisticsServiceCharge(String description, BigDecimal amount, TaxCategory tax) {
  /**
   * Applies the checks of the model.
   *
   * @throws NullPointerException     if a required value is null
   * @throws IllegalArgumentException if a value violates the checks of the model
   */
  public LogisticsServiceCharge {
    ModelChecks.required(description,
                         "logistics service charge description");
    amount = ModelChecks.money(Objects.requireNonNull(amount,
                                                      "logistics service charge amount"));
    Objects.requireNonNull(tax,
                           "tax");
  }
}

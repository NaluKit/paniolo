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
 * Third party payment of the XRechnung extension (BG-DEX-09), e.g. a payment the buyer makes to a
 * third party on behalf of the seller. Its amount is added to the amount due (BR-DEX-09). Written
 * as UBL {@code cac:PrepaidPayment} with the specification identifier
 * {@link ProcessControl#XRECHNUNG_3_EXTENSION}.
 *
 * @param type        third party payment type (BT-DEX-001)
 * @param amount      third party payment amount (BT-DEX-002)
 * @param description third party payment description (BT-DEX-003)
 */
public record ThirdPartyPayment(String type, BigDecimal amount, String description) {
  /**
   * Applies the checks of the model.
   *
   * @throws NullPointerException     if a required value is null
   * @throws IllegalArgumentException if a value violates the checks of the model
   */
  public ThirdPartyPayment {
    // BR-DEX-10, BR-DEX-11, BR-DEX-12: all three are required; BR-DEX-13: two decimals.
    ModelChecks.required(type,
                         "third party payment type");
    amount = ModelChecks.money(Objects.requireNonNull(amount,
                                                      "third party payment amount"));
    ModelChecks.required(description,
                         "third party payment description");
  }
}

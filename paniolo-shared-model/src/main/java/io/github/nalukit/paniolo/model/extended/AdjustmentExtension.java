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
import io.github.nalukit.paniolo.model.common.value.UnitCode;

import java.math.BigDecimal;

/**
 * Details of a document allowance or charge in the Factur-X profile EXTENDED.
 *
 * @param sequenceNumber   order in which the allowance or charge is applied ({@code SequenceNumeric}), or null
 * @param baseQuantity     quantity the allowance or charge refers to ({@code BasisQuantity}), or null
 * @param baseQuantityUnit unit of the base quantity, or null
 */
public record AdjustmentExtension(Integer sequenceNumber, BigDecimal baseQuantity, UnitCode baseQuantityUnit) {
  /**
   * Applies the checks of the model.
   *
   * @throws IllegalArgumentException if a value violates the checks of the model
   */
  public AdjustmentExtension {
    if (sequenceNumber == null && baseQuantity == null) {
      throw new IllegalArgumentException("EXTENDED allowance or charge details require a sequence number or a base quantity");
    }
    if (sequenceNumber != null && sequenceNumber < 1) {
      throw new IllegalArgumentException("allowance or charge sequence number must be positive: " + sequenceNumber);
    }
    if (baseQuantity != null) {
      ModelChecks.nonNegative(baseQuantity,
                              "allowance or charge base quantity");
    }
    if (baseQuantityUnit != null && baseQuantity == null) {
      throw new IllegalArgumentException("A base quantity unit requires the base quantity");
    }
  }

  /**
   * Details with the sequence number only.
   *
   * @param sequenceNumber order in which the allowance or charge is applied; must be positive
   * @return the details
   * @throws IllegalArgumentException if the sequence number is not positive
   */
  public static AdjustmentExtension sequence(int sequenceNumber) {
    return new AdjustmentExtension(sequenceNumber,
                                   null,
                                   null);
  }

  /**
   * Details with the base quantity only.
   *
   * @param baseQuantity quantity the allowance or charge refers to; must not be negative
   * @param unit         unit of the base quantity, or null
   * @return the details
   * @throws IllegalArgumentException if the base quantity is negative
   */
  public static AdjustmentExtension baseQuantity(BigDecimal baseQuantity,
                                                 UnitCode unit) {
    return new AdjustmentExtension(null,
                                   baseQuantity,
                                   unit);
  }
}

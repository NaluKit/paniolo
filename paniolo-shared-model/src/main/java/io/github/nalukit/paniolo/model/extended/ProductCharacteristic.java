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
 * Product characteristic of the Factur-X profile EXTENDED with type code or measured value, e.g.
 * a weight of 12.5 kg. Plain name and value pairs are item attributes (BG-32) of the line.
 *
 * @param typeCode    characteristic type, or null
 * @param description characteristic name
 * @param value       characteristic value as text, or null
 * @param measure     measured value, or null
 * @param measureUnit unit of the measured value, required with it
 */
public record ProductCharacteristic(String typeCode, String description, String value, BigDecimal measure, UnitCode measureUnit) {
  /**
   * Applies the checks of the model.
   *
   * @throws IllegalArgumentException if a value violates the checks of the model
   */
  public ProductCharacteristic {
    ModelChecks.required(description,
                         "product characteristic description");
    if (value == null && measure == null) {
      throw new IllegalArgumentException("A product characteristic requires a value or a measure");
    }
    if ((measure == null) != (measureUnit == null)) {
      throw new IllegalArgumentException("A product characteristic measure and its unit must be set together");
    }
    if (typeCode == null && measure == null) {
      throw new IllegalArgumentException("Use an item attribute for a characteristic without type code or measure");
    }
  }
}

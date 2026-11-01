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
import io.github.nalukit.paniolo.model.common.value.UnitCode;

import java.math.BigDecimal;

/**
 * Width, length, and height of the packaging of an Order-X line item (COMFORT), each with its unit.
 *
 * @param width      width, or null
 * @param widthUnit  unit of the width, required with it
 * @param length     length, or null
 * @param lengthUnit unit of the length, required with it
 * @param height     height, or null
 * @param heightUnit unit of the height, required with it
 */
public record PackageDimensions(BigDecimal width, UnitCode widthUnit, BigDecimal length, UnitCode lengthUnit, BigDecimal height, UnitCode heightUnit) {
  /**
   * Applies the checks of the model.
   *
   * @throws IllegalArgumentException if a value violates the checks of the model
   */
  public PackageDimensions {
    if (width == null && length == null && height == null) {
      throw new IllegalArgumentException("Package dimensions need a width, length, or height");
    }
    check(width,
          widthUnit,
          "width");
    check(length,
          lengthUnit,
          "length");
    check(height,
          heightUnit,
          "height");
  }

  private static void check(BigDecimal value,
                            UnitCode unit,
                            String name) {
    if ((value == null) != (unit == null)) {
      throw new IllegalArgumentException("The package " + name + " and its unit must be set together");
    }
    if (value != null) {
      ModelChecks.nonNegative(value,
                              "package " + name);
    }
  }
}

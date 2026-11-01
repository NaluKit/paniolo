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
package io.github.nalukit.paniolo.model.common.validation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Shared structural checks; business rules are checked by the XML validator.
 */
public final class ModelChecks {
  private ModelChecks() {
  }

  /**
   * Rejects a null or blank text.
   *
   * @param value the text
   * @param name  name of the value for the error message
   * @return the text
   * @throws IllegalArgumentException if the text is null or blank
   */
  public static String required(String value,
                                String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
    return value;
  }

  /**
   * Rejects a null or negative number.
   *
   * @param value the number
   * @param name  name of the value for the error message
   * @return the number
   * @throws NullPointerException     if the number is null
   * @throws IllegalArgumentException if the number is negative
   */
  public static BigDecimal nonNegative(BigDecimal value,
                                       String name) {
    Objects.requireNonNull(value,
                           name);
    if (value.signum() < 0) {
      throw new IllegalArgumentException(name + " must not be negative");
    }
    return value;
  }

  /**
   * Rounds an amount half up to cents.
   *
   * @param value the amount; must not be null
   * @return the amount with two decimal places
   */
  public static BigDecimal money(BigDecimal value) {
    return value.setScale(2,
                          RoundingMode.HALF_UP);
  }
}

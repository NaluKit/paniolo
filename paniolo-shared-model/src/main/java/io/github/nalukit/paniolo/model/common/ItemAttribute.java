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

/**
 * Item attribute (BG-32) with name (BT-160) and value (BT-161), e.g. color = blue.
 *
 * @param name  item attribute name (BT-160); must not be blank
 * @param value item attribute value (BT-161); must not be blank
 */
public record ItemAttribute(String name, String value) {
  /**
   * Applies the checks of the model.
   *
   * @throws IllegalArgumentException if a value violates the checks of the model
   */
  public ItemAttribute {
    ModelChecks.required(name,
                         "item attribute name");
    ModelChecks.required(value,
                         "item attribute value");
  }

  /**
   * Creates an item attribute.
   *
   * @param name  the attribute name (BT-160)
   * @param value the attribute value (BT-161)
   * @return the item attribute
   * @throws IllegalArgumentException if name or value is blank
   */
  public static ItemAttribute of(String name,
                                 String value) {
    return new ItemAttribute(name,
                             value);
  }
}

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
 * Item classification identifier (BT-158) with its scheme (BT-158-1, UNTDID 7143, e.g. "STI" for
 * CPV or "HS") and optional scheme version (BT-158-2). The class name belongs to the Factur-X
 * profile EXTENDED.
 *
 * @param code          item classification identifier (BT-158); must not be blank
 * @param schemeId      UNTDID 7143 scheme identifier (BT-158-1); must not be blank
 * @param schemeVersion classification scheme version (BT-158-2), or null
 * @param className     classification name for Factur-X EXTENDED, or null
 */
public record ItemClassification(String code, String schemeId, String schemeVersion, String className) {
  /**
   * Applies the checks of the model.
   *
   * @throws IllegalArgumentException if a value violates the checks of the model
   */
  public ItemClassification {
    ModelChecks.required(code,
                         "item classification code");
    // BR-65: the classification scheme is mandatory.
    ModelChecks.required(schemeId,
                         "item classification scheme");
  }

  /**
   * A classification without class name.
   *
   * @param code          item classification identifier (BT-158)
   * @param schemeId      UNTDID 7143 scheme identifier (BT-158-1)
   * @param schemeVersion classification scheme version (BT-158-2), or null
   * @throws IllegalArgumentException if code or scheme is blank
   */
  public ItemClassification(String code,
                            String schemeId,
                            String schemeVersion) {
    this(code,
         schemeId,
         schemeVersion,
         null);
  }

  /**
   * A classification without scheme version and class name.
   *
   * @param code     item classification identifier (BT-158)
   * @param schemeId UNTDID 7143 scheme identifier (BT-158-1)
   * @return the classification
   * @throws IllegalArgumentException if code or scheme is blank
   */
  public static ItemClassification of(String code,
                                      String schemeId) {
    return new ItemClassification(code,
                                  schemeId,
                                  null,
                                  null);
  }
}

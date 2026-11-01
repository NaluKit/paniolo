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

/**
 * A further invoiced object identifier of a line (BT-128) with its scheme, after the one the
 * line states itself; a French multi-vendor invoice names, for example, the sub-invoice (AFL)
 * next to the billed object (AVV).
 *
 * @param id       object identifier (BT-128); required
 * @param schemeId scheme of the identifier (BT-128-1, UNTDID 1153), e.g. {@code AFL}, or null
 */
public record ObjectIdentifier(String id, String schemeId) {
  /**
   * Applies the checks of the model.
   *
   * @throws IllegalArgumentException if a value violates the checks of the model
   */
  public ObjectIdentifier {
    ModelChecks.required(id,
                         "object identifier");
  }
}

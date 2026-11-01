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
 * A further identifier of a party next to {@link Party#identifier()}, e.g. a second seller
 * identifier (BT-29 is repeatable) or a local identifier next to a global one.
 *
 * @param id       the identifier
 * @param schemeId its ISO 6523 scheme, e.g. "0088" for GLN, or null for a local identifier
 */
public record PartyIdentifier(String id, String schemeId) {
  /**
   * Applies the checks of the model.
   *
   * @throws IllegalArgumentException if the identifier is blank
   */
  public PartyIdentifier {
    ModelChecks.required(id,
                         "party identifier");
  }
}

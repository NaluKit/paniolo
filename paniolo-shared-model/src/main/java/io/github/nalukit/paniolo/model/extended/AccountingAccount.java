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
 * A further buyer accounting account of the Factur-X profile EXTENDED, next to the buyer accounting
 * reference of the document (BT-19) or of a line (BT-133); EXTENDED repeats
 * {@code ReceivableSpecifiedTradeAccountingAccount}.
 *
 * @param id       the accounting reference
 * @param typeCode its UNTDID 4437 type, or null
 */
public record AccountingAccount(String id, String typeCode) {
  /**
   * Applies the checks of the model.
   *
   * @throws IllegalArgumentException if the reference is blank
   */
  public AccountingAccount {
    ModelChecks.required(id,
                         "accounting account");
  }
}

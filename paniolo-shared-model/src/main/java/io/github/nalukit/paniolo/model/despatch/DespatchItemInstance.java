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
package io.github.nalukit.paniolo.model.despatch;

import java.time.LocalDate;

/**
 * Despatched item instance: serial number, lot, or dates of one or more delivered items.
 *
 * @param serialId        serial number, or null
 * @param lotNumber       lot or batch number, or null
 * @param expiryDate      expiry date of the lot, or null
 * @param bestBeforeDate  best before date, or null
 * @param manufactureDate manufacture date, or null
 */
public record DespatchItemInstance(String serialId, String lotNumber, LocalDate expiryDate, LocalDate bestBeforeDate, LocalDate manufactureDate) {
  /**
   * Applies the checks of the model.
   *
   * @throws IllegalArgumentException if a value violates the checks of the model
   */
  public DespatchItemInstance {
    if (serialId == null && lotNumber == null && expiryDate == null && bestBeforeDate == null && manufactureDate == null) {
      throw new IllegalArgumentException("An item instance needs a serial number, lot, or date");
    }
    if (expiryDate != null && lotNumber == null) {
      throw new IllegalArgumentException("An expiry date belongs to a lot number");
    }
  }

  /**
   * An item instance with serial number only.
   *
   * @param serialId the serial number
   * @return the item instance
   * @throws IllegalArgumentException if the serial number is null
   */
  public static DespatchItemInstance serial(String serialId) {
    return new DespatchItemInstance(serialId,
                                    null,
                                    null,
                                    null,
                                    null);
  }

  /**
   * An item instance of a lot.
   *
   * @param lotNumber  the lot number
   * @param expiryDate expiry date of the lot, or null
   * @return the item instance
   * @throws IllegalArgumentException if the lot number is null
   */
  public static DespatchItemInstance lot(String lotNumber,
                                         LocalDate expiryDate) {
    return new DespatchItemInstance(null,
                                    lotNumber,
                                    expiryDate,
                                    null,
                                    null);
  }
}

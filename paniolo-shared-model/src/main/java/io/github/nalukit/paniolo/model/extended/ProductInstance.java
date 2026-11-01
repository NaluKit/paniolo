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

/**
 * Individual product instance of an invoice line in the Factur-X profile EXTENDED.
 *
 * @param batchId  batch or lot number, or null
 * @param serialId serial number assigned by the supplier, or null
 */
public record ProductInstance(String batchId, String serialId) {
  /**
   * Applies the checks of the model.
   *
   * @throws IllegalArgumentException if a value violates the checks of the model
   */
  public ProductInstance {
    if ((batchId == null || batchId.isBlank()) && (serialId == null || serialId.isBlank())) {
      throw new IllegalArgumentException("A product instance requires a batch or serial number");
    }
  }
}

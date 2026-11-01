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

/**
 * Product the seller offers instead of the ordered one, e.g. in an order response (Order-X COMFORT).
 *
 * @param name                 product name, or null
 * @param sellerItemId         seller's item identifier, or null
 * @param buyerItemId          buyer's item identifier, or null
 * @param standardItemId       standard item identifier, e.g. a GTIN; or null
 * @param standardItemSchemeId scheme of the standard item identifier, required with it
 * @param description          product description, or null
 */
public record SubstitutedProduct(String name, String sellerItemId, String buyerItemId, String standardItemId, String standardItemSchemeId, String description) {
  /**
   * Applies the checks of the model.
   *
   * @throws IllegalArgumentException if a value violates the checks of the model
   */
  public SubstitutedProduct {
    if (name == null && sellerItemId == null && buyerItemId == null && standardItemId == null) {
      throw new IllegalArgumentException("A substituted product needs a name or identifier");
    }
    if ((standardItemId == null) != (standardItemSchemeId == null)) {
      throw new IllegalArgumentException("A substituted product's standard identifier and scheme must be set together");
    }
  }
}

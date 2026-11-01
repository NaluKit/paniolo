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
import io.github.nalukit.paniolo.model.common.value.UnitCode;

import java.math.BigDecimal;

/**
 * Product included in the item of an invoice line in the Factur-X profile EXTENDED, e.g. a part of
 * a set.
 *
 * @param name           product name
 * @param id             product identifier, or null
 * @param sellerItemId   seller's identifier, or null
 * @param buyerItemId    buyer's identifier, or null
 * @param industryItemId manufacturer's or industry identifier, or null
 * @param description    product description, or null
 * @param quantity       quantity included per item, or null
 * @param unitCode       unit of the quantity, required with it
 */
public record IncludedProduct(String name, String id, String sellerItemId, String buyerItemId, String industryItemId, String description, BigDecimal quantity, UnitCode unitCode) {
  /**
   * Applies the checks of the model.
   *
   * @throws IllegalArgumentException if a value violates the checks of the model
   */
  public IncludedProduct {
    ModelChecks.required(name,
                         "included product name");
    if ((quantity == null) != (unitCode == null)) {
      throw new IllegalArgumentException("An included product quantity and its unit must be set together");
    }
  }

  /**
   * An included product with name and quantity only.
   *
   * @param name     product name
   * @param quantity quantity included per item, or null
   * @param unitCode unit of the quantity, required with it
   * @return the included product
   * @throws IllegalArgumentException if the name is blank or only one of quantity and unit is set
   */
  public static IncludedProduct of(String name,
                                   BigDecimal quantity,
                                   UnitCode unitCode) {
    return new IncludedProduct(name,
                               null,
                               null,
                               null,
                               null,
                               null,
                               quantity,
                               unitCode);
  }
}

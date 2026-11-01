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
package io.github.nalukit.paniolo.mustang;

import io.github.nalukit.paniolo.model.Invoice;

import java.util.List;
import java.util.Objects;

/**
 * Result of {@link MustangInvoiceAdapter#map}: the Paniolo invoice and every value of the Mustang
 * invoice that it does not carry.
 *
 * @param invoice the mapped invoice
 * @param losses  the values not carried over, in mapping order; copied
 */
public record MustangMapping(Invoice invoice, List<MappingLoss> losses) {
  /**
   * Applies the checks of the model and copies the list.
   *
   * @throws NullPointerException if a value is null
   */
  public MustangMapping {
    Objects.requireNonNull(invoice,
                           "invoice");
    losses = List.copyOf(losses);
  }

  /**
   * Returns true if every value of the Mustang invoice was carried over.
   *
   * @return true without losses
   */
  public boolean isLossless() {
    return losses.isEmpty();
  }
}

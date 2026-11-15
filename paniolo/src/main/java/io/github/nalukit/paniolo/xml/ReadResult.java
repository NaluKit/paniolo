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
package io.github.nalukit.paniolo.xml;

import io.github.nalukit.paniolo.model.Invoice;

import java.util.List;
import java.util.Objects;

/**
 * An invoice read from a document, with the deviations accepted in {@link ReadMode#LENIENT} and
 * the values the model does not keep.
 *
 * @param invoice    the invoice
 * @param warnings   accepted deviations ({@link ReadMode#LENIENT} only) and, in both modes, values
 *                   the model does not keep, e.g. {@link ReadWarning#IDENTIFIER} or
 *                   {@link ReadWarning#SPECIFICATION_VERSION}
 * @param stated     the amounts as the document states them
 * @param deviations the stated amounts that differ from the invoice
 */
public record ReadResult(Invoice invoice, List<ReadWarning> warnings, StatedAmounts stated, List<AmountDeviation> deviations) {
  /**
   * Creates the result and copies the warnings and deviations.
   *
   * @throws NullPointerException if an argument is null
   */
  public ReadResult {
    Objects.requireNonNull(invoice,
                           "invoice");
    Objects.requireNonNull(stated,
                           "stated");
    warnings   = List.copyOf(warnings);
    deviations = List.copyOf(deviations);
  }

  /**
   * Creates a result without stated amounts.
   *
   * @param invoice  the invoice
   * @param warnings accepted deviations
   * @throws NullPointerException if invoice or warnings is null
   */
  public ReadResult(Invoice invoice,
                    List<ReadWarning> warnings) {
    this(invoice,
         warnings,
         StatedAmounts.NONE,
         List.of());
  }

  /**
   * Whether the invoice reproduces the document without deviations or values not kept.
   *
   * @return true if there are no warnings
   */
  public boolean isExact() {
    return warnings.isEmpty();
  }
}

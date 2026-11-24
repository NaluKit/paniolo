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
 * An invoice read from a document, with import warnings, stated amounts and differences from the
 * calculation. Warnings and amount deviations can occur in every {@link ReadMode}, including
 * {@link ReadMode#STRICT}; neither strict reading nor {@link #isExact()} guarantees lossless import.
 *
 * @param invoice    the invoice
 * @param warnings   accepted deviations, conversions and values the model does not keep, e.g.
 *                   {@link ReadWarning#IDENTIFIER} or {@link ReadWarning#SPECIFICATION_VERSION}
 * @param stated     the amounts as the document states them
 * @param deviations stated amounts that differ from the calculation or retained model values;
 *                   includes a retained BT-131 that differs from quantity x net price
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
   * Whether reading produced no warnings. This does not compare every field or the XML bytes;
   * stated amount differences can still be listed in {@link #deviations()}.
   *
   * @return true if there are no warnings
   */
  public boolean isExact() {
    return warnings.isEmpty();
  }

  /**
   * Requires that {@link #deviations()} is empty, for a caller that must review any difference
   * between stated and calculated amounts before writing the invoice again. A lenient read can
   * recalculate a net price or keep calculated totals. Strict reading can also list differences,
   * including a stated BT-131 retained within the price-rounding tolerance; this method rejects
   * that difference too, even though the invoice retains the stated line amount.
   *
   * <p>This does not check warnings or XML fidelity; use {@link ImportResult#originalXml()} to
   * retain the original XML.
   *
   * @return this result
   * @throws IllegalArgumentException if deviations are listed; the message lists
   *                                  the business terms with the stated and the calculated amount
   */
  public ReadResult requireNoDeviations() {
    if (deviations.isEmpty()) {
      return this;
    }
    var message = new StringBuilder("The invoice states other amounts than the document:");
    for (AmountDeviation deviation : deviations) {
      message.append(' ')
             .append(deviation.businessTerm());
      if (deviation.lineId() != null) {
        message.append(" (line ")
               .append(deviation.lineId())
               .append(')');
      }
      message.append(": stated ")
             .append(deviation.stated()
                              .toPlainString())
             .append(", invoice ")
             .append(deviation.calculated() == null ?
                     "none" :
                     deviation.calculated()
                              .toPlainString())
             .append(';');
    }
    throw new IllegalArgumentException(message.toString());
  }
}

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
package io.github.nalukit.paniolo.model.quotation;

import io.github.nalukit.paniolo.model.common.InvoiceLine;

import java.util.Objects;

/**
 * Line of a {@link Quotation}: an invoice line with the information whether it is optional. An
 * optional line is offered to the buyer but does not count in the totals until the buyer accepts
 * it.
 *
 * @param line     the offered item, quantity, price, and VAT; required
 * @param optional true for an optional line, which is not part of the standard offer
 */
public record QuotationLine(InvoiceLine line, boolean optional) {
  /**
   * Applies the checks of the model.
   *
   * @throws NullPointerException if the line is null
   */
  public QuotationLine {
    Objects.requireNonNull(line,
                           "line");
  }

  /**
   * A line that is part of the standard offer.
   *
   * @param line the offered line
   * @return the quotation line
   */
  public static QuotationLine standard(InvoiceLine line) {
    return new QuotationLine(line,
                             false);
  }

  /**
   * An optional line, offered in addition to the standard offer.
   *
   * @param line the offered line
   * @return the quotation line
   */
  public static QuotationLine optional(InvoiceLine line) {
    return new QuotationLine(line,
                             true);
  }
}

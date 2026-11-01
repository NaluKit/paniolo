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

import java.time.LocalDate;

/**
 * Invoicing period (BG-14); at least one date is required.
 *
 * @param startDate period start (BT-73), or null if the end date is set
 * @param endDate   period end (BT-74), or null if the start date is set; must not precede the start
 */
public record InvoicePeriod(LocalDate startDate, LocalDate endDate) {
  /**
   * Applies the checks of the model.
   *
   * @throws IllegalArgumentException if a value violates the checks of the model
   */
  public InvoicePeriod {
    if (startDate == null && endDate == null) {
      throw new IllegalArgumentException("An invoicing period requires a start or end date");
    }
    if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
      throw new IllegalArgumentException("Invoicing period end date must not be before its start date");
    }
  }

  /**
   * Starts named construction of a {@link InvoicePeriod} using the builder defaults.
   *
   * @return a new builder; not shared with other calls
   */
  public static Builder create() {
    return new Builder();
  }

  /**
   * Named construction of an immutable invoicing period.
   *
   * <p>Mutable construction state for {@link InvoicePeriod}. Use a separate builder per operation;
   * builders are not thread-safe. {@link #build()} constructs a new immutable value and applies
   * the checks of the model. XML schema and business rule validation are separate operations.
   */
  public static final class Builder {
    private LocalDate startDate;
    private LocalDate endDate;

    /**
     * Creates a builder with the defaults of the model; usually obtained with {@link InvoicePeriod#create()}.
     */
    public Builder() {
    }

    /**
     * Sets {@link InvoicePeriod#startDate() startDate}.
     *
     * @param startDate period start (BT-73), or null if the end date is set
     * @return this builder
     */
    public Builder startDate(LocalDate startDate) {
      this.startDate = startDate;
      return this;
    }

    /**
     * Sets {@link InvoicePeriod#endDate() endDate}.
     *
     * @param endDate period end (BT-74), or null if the start date is set; must not precede the start
     * @return this builder
     */
    public Builder endDate(LocalDate endDate) {
      this.endDate = endDate;
      return this;
    }

    /**
     * Constructs a new {@link InvoicePeriod} from the current values.
     *
     * @return the constructed value after the model checks succeed
     */
    public InvoicePeriod build() {
      return new InvoicePeriod(startDate,
                               endDate);
    }
  }
}

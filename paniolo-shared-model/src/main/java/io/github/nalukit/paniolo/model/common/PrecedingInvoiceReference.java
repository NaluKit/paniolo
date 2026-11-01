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

import io.github.nalukit.paniolo.model.common.validation.ModelChecks;

import java.time.LocalDate;

/**
 * Reference to a previously issued invoice (BG-3), e.g. the invoice corrected by a credit note.
 *
 * @param number    preceding invoice identifier (BT-25); must not be blank
 * @param issueDate preceding invoice issue date (BT-26), or null
 */
public record PrecedingInvoiceReference(String number, LocalDate issueDate) {
  /**
   * Applies the checks of the model.
   *
   * @throws IllegalArgumentException if a value violates the checks of the model
   */
  public PrecedingInvoiceReference {
    ModelChecks.required(number,
                         "preceding invoice number");
  }

  /**
   * Starts named construction of a {@link PrecedingInvoiceReference} using the builder defaults.
   *
   * @return a new builder; not shared with other calls
   */
  public static Builder create() {
    return new Builder();
  }

  /**
   * Named construction of an immutable preceding invoice reference.
   *
   * <p>Mutable construction state for {@link PrecedingInvoiceReference}. Use a separate builder per operation;
   * builders are not thread-safe. {@link #build()} constructs a new immutable value and applies
   * the checks of the model. XML schema and business rule validation are separate operations.
   */
  public static final class Builder {
    private String    number;
    private LocalDate issueDate;

    /**
     * Creates a builder with the defaults of the model; usually obtained with {@link PrecedingInvoiceReference#create()}.
     */
    public Builder() {
    }

    /**
     * Sets {@link PrecedingInvoiceReference#number() number}.
     *
     * @param number preceding invoice identifier (BT-25); must not be blank
     * @return this builder
     */
    public Builder number(String number) {
      this.number = number;
      return this;
    }

    /**
     * Sets {@link PrecedingInvoiceReference#issueDate() issueDate}.
     *
     * @param issueDate preceding invoice issue date (BT-26), or null
     * @return this builder
     */
    public Builder issueDate(LocalDate issueDate) {
      this.issueDate = issueDate;
      return this;
    }

    /**
     * Constructs a new {@link PrecedingInvoiceReference} from the current values.
     *
     * @return the constructed value after the model checks succeed
     */
    public PrecedingInvoiceReference build() {
      return new PrecedingInvoiceReference(number,
                                           issueDate);
    }
  }
}

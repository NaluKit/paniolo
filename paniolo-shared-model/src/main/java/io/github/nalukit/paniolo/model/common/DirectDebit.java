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

/**
 * SEPA direct debit (BG-19); XRechnung requires all three identifiers.
 *
 * @param mandateReference mandate reference (BT-89); must not be blank
 * @param creditorId       bank-assigned creditor identifier (BT-90); must not be blank
 * @param debitedAccountId debited account identifier (BT-91); must not be blank
 */
public record DirectDebit(String mandateReference, String creditorId, String debitedAccountId) {
  /**
   * Applies the checks of the model.
   *
   * @throws IllegalArgumentException if a value violates the checks of the model
   */
  public DirectDebit {
    ModelChecks.required(mandateReference,
                         "mandate reference");
    ModelChecks.required(creditorId,
                         "bank assigned creditor identifier");
    ModelChecks.required(debitedAccountId,
                         "debited account identifier");
  }

  /**
   * Starts named construction of a {@link DirectDebit} using the builder defaults.
   *
   * @return a new builder; not shared with other calls
   */
  public static Builder create() {
    return new Builder();
  }

  /**
   * Named construction of an immutable direct debit.
   *
   * <p>Mutable construction state for {@link DirectDebit}. Use a separate builder per operation;
   * builders are not thread-safe. {@link #build()} constructs a new immutable value and applies
   * the checks of the model. XML schema and business rule validation are separate operations.
   */
  public static final class Builder {
    private String mandateReference;
    private String creditorId;
    private String debitedAccountId;

    /**
     * Creates a builder with the defaults of the model; usually obtained with {@link DirectDebit#create()}.
     */
    public Builder() {
    }

    /**
     * Mandate reference identifier (BT-89).
     *
     * @param mandateReference mandate reference (BT-89); must not be blank
     * @return this builder
     */
    public Builder mandateReference(String mandateReference) {
      this.mandateReference = mandateReference;
      return this;
    }

    /**
     * Bank assigned creditor identifier (BT-90).
     *
     * @param creditorId bank-assigned creditor identifier (BT-90); must not be blank
     * @return this builder
     */
    public Builder creditorId(String creditorId) {
      this.creditorId = creditorId;
      return this;
    }

    /**
     * Debited account identifier (BT-91).
     *
     * @param debitedAccountId debited account identifier (BT-91); must not be blank
     * @return this builder
     */
    public Builder debitedAccountId(String debitedAccountId) {
      this.debitedAccountId = debitedAccountId;
      return this;
    }

    /**
     * Constructs a new {@link DirectDebit} from the current values.
     *
     * @return the constructed value after the model checks succeed
     */
    public DirectDebit build() {
      return new DirectDebit(mandateReference,
                             creditorId,
                             debitedAccountId);
    }
  }
}

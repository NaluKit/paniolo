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
 * Payment card information (BG-18); only the last 4 to 6 digits of the card number may be sent.
 *
 * @param primaryAccountNumber masked card number (BT-87), containing only the last four to six digits; must not be blank
 * @param holderName           card holder name (BT-88), or null
 */
public record PaymentCardInformation(String primaryAccountNumber, String holderName) {
  /**
   * Applies the checks of the model.
   *
   * @throws IllegalArgumentException if a value violates the checks of the model
   */
  public PaymentCardInformation {
    ModelChecks.required(primaryAccountNumber,
                         "primary account number");
  }

  /**
   * Starts named construction of a {@link PaymentCardInformation} using the builder defaults.
   *
   * @return a new builder; not shared with other calls
   */
  public static Builder create() {
    return new Builder();
  }

  /**
   * Named construction of immutable payment card information.
   *
   * <p>Mutable construction state for {@link PaymentCardInformation}. Use a separate builder per operation;
   * builders are not thread-safe. {@link #build()} constructs a new immutable value and applies
   * the checks of the model. XML schema and business rule validation are separate operations.
   */
  public static final class Builder {
    private String primaryAccountNumber;
    private String holderName;

    /**
     * Creates a builder with the defaults of the model; usually obtained with {@link PaymentCardInformation#create()}.
     */
    public Builder() {
    }

    /**
     * Sets {@link PaymentCardInformation#primaryAccountNumber() primaryAccountNumber}.
     *
     * @param primaryAccountNumber masked card number (BT-87), containing only the last four to six digits; must not be blank
     * @return this builder
     */
    public Builder primaryAccountNumber(String primaryAccountNumber) {
      this.primaryAccountNumber = primaryAccountNumber;
      return this;
    }

    /**
     * Sets {@link PaymentCardInformation#holderName() holderName}.
     *
     * @param holderName card holder name (BT-88), or null
     * @return this builder
     */
    public Builder holderName(String holderName) {
      this.holderName = holderName;
      return this;
    }

    /**
     * Constructs a new {@link PaymentCardInformation} from the current values.
     *
     * @return the constructed value after the model checks succeed
     */
    public PaymentCardInformation build() {
      return new PaymentCardInformation(primaryAccountNumber,
                                        holderName);
    }
  }
}

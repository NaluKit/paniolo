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
 * Electronic address with an EAS scheme identifier, e.g. EM for email.
 *
 * <p>Invoices and despatch advices require the scheme (BT-34-1 / BT-49-1); Order-X allows an
 * address without scheme.
 *
 * @param value    electronic address (BT-34 / BT-49); must not be blank
 * @param schemeId EAS address scheme identifier, e.g. EM for email; null only in Order-X
 */
public record ElectronicAddress(String value, String schemeId) {

  /**
   * Applies the checks of the model.
   *
   * @throws IllegalArgumentException if a value violates the checks of the model
   */
  public ElectronicAddress {
    ModelChecks.required(value,
                         "electronic address");
    if (schemeId != null) {
      ModelChecks.required(schemeId,
                           "electronic address scheme");
    }
  }

  /**
   * Starts named construction of a {@link ElectronicAddress} using the builder defaults.
   *
   * @return a new builder; not shared with other calls
   */
  public static Builder create() {
    return new Builder();
  }

  /**
   * Named construction of an immutable electronic address.
   *
   * <p>Mutable construction state for {@link ElectronicAddress}. Use a separate builder per operation;
   * builders are not thread-safe. {@link #build()} constructs a new immutable value and applies
   * the checks of the model. XML schema and business rule validation are separate operations.
   */
  public static final class Builder {
    private String value;
    private String schemeId;

    /**
     * Creates a builder with the defaults of the model; usually obtained with {@link ElectronicAddress#create()}.
     */
    public Builder() {
    }

    /**
     * Sets {@link ElectronicAddress#value() value}.
     *
     * @param value electronic address (BT-34 / BT-49); must not be blank
     * @return this builder
     */
    public Builder value(String value) {
      this.value = value;
      return this;
    }

    /**
     * Sets {@link ElectronicAddress#schemeId() schemeId}.
     *
     * @param schemeId EAS address scheme identifier, e.g. EM for email; null only in Order-X
     * @return this builder
     */
    public Builder schemeId(String schemeId) {
      this.schemeId = schemeId;
      return this;
    }

    /**
     * Constructs a new {@link ElectronicAddress} from the current values.
     *
     * @return the constructed value after the model checks succeed
     */
    public ElectronicAddress build() {
      return new ElectronicAddress(value,
                                   schemeId);
    }
  }
}

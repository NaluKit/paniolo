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
 * Credit transfer account (BG-17).
 *
 * @param iban        payment account identifier (BT-84), usually an IBAN; must not be blank
 * @param accountName payment account name (BT-85), or null
 * @param bic         payment service provider identifier (BT-86), usually a BIC; or null
 */
public record CreditTransfer(String iban, String accountName, String bic) {
  /**
   * Applies the checks of the model.
   *
   * @throws IllegalArgumentException if a value violates the checks of the model
   */
  public CreditTransfer {
    ModelChecks.required(iban,
                         "iban");
  }

  /**
   * Starts named construction of a {@link CreditTransfer} using the builder defaults.
   *
   * @return a new builder; not shared with other calls
   */
  public static Builder create() {
    return new Builder();
  }

  /**
   * Named construction of an immutable credit transfer account.
   *
   * <p>Mutable construction state for {@link CreditTransfer}. Use a separate builder per operation;
   * builders are not thread-safe. {@link #build()} constructs a new immutable value and applies
   * the checks of the model. XML schema and business rule validation are separate operations.
   */
  public static final class Builder {
    private String iban;
    private String accountName;
    private String bic;

    /**
     * Creates a builder with the defaults of the model; usually obtained with {@link CreditTransfer#create()}.
     */
    public Builder() {
    }

    /**
     * Payment account identifier (BT-84), usually an IBAN.
     *
     * @param iban payment account identifier (BT-84), usually an IBAN; must not be blank
     * @return this builder
     */
    public Builder iban(String iban) {
      this.iban = iban;
      return this;
    }

    /**
     * Sets {@link CreditTransfer#accountName() accountName}.
     *
     * @param accountName payment account name (BT-85), or null
     * @return this builder
     */
    public Builder accountName(String accountName) {
      this.accountName = accountName;
      return this;
    }

    /**
     * Sets {@link CreditTransfer#bic() bic}.
     *
     * @param bic payment service provider identifier (BT-86), usually a BIC; or null
     * @return this builder
     */
    public Builder bic(String bic) {
      this.bic = bic;
      return this;
    }

    /**
     * Constructs a new {@link CreditTransfer} from the current values.
     *
     * @return the constructed value after the model checks succeed
     */
    public CreditTransfer build() {
      return new CreditTransfer(iban,
                                accountName,
                                bic);
    }
  }
}

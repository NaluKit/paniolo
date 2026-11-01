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

import java.util.Objects;

/**
 * Seller tax representative (BG-11).
 *
 * @param name    tax representative name (BT-62); must not be blank
 * @param vatId   tax representative VAT identifier (BT-63); must not be blank
 * @param address tax representative postal address (BG-12); required
 */
public record SellerTaxRepresentativeParty(String name, String vatId, Address address) {
  /**
   * Applies the checks of the model.
   *
   * @throws NullPointerException     if a required value is null
   * @throws IllegalArgumentException if a value violates the checks of the model
   */
  public SellerTaxRepresentativeParty {
    ModelChecks.required(name,
                         "tax representative name");
    ModelChecks.required(vatId,
                         "tax representative VAT identifier");
    Objects.requireNonNull(address,
                           "address");
  }

  /**
   * Starts named construction of a {@link SellerTaxRepresentativeParty} using the builder defaults.
   *
   * @return a new builder; not shared with other calls
   */
  public static Builder create() {
    return new Builder();
  }

  /**
   * Named construction of an immutable tax representative.
   *
   * <p>Mutable construction state for {@link SellerTaxRepresentativeParty}. Use a separate builder per operation;
   * builders are not thread-safe. {@link #build()} constructs a new immutable value and applies
   * the checks of the model. XML schema and business rule validation are separate operations.
   */
  public static final class Builder {
    private String  name;
    private String  vatId;
    private Address address;

    /**
     * Creates a builder with the defaults of the model; usually obtained with {@link SellerTaxRepresentativeParty#create()}.
     */
    public Builder() {
    }

    /**
     * Sets {@link SellerTaxRepresentativeParty#name() name}.
     *
     * @param name tax representative name (BT-62); must not be blank
     * @return this builder
     */
    public Builder name(String name) {
      this.name = name;
      return this;
    }

    /**
     * Sets {@link SellerTaxRepresentativeParty#vatId() vatId}.
     *
     * @param vatId tax representative VAT identifier (BT-63); must not be blank
     * @return this builder
     */
    public Builder vatId(String vatId) {
      this.vatId = vatId;
      return this;
    }

    /**
     * Sets {@link SellerTaxRepresentativeParty#address() address}.
     *
     * @param address tax representative postal address (BG-12); required
     * @return this builder
     */
    public Builder address(Address address) {
      this.address = address;
      return this;
    }

    /**
     * Constructs a new {@link SellerTaxRepresentativeParty} from the current values.
     *
     * @return the constructed value after the model checks succeed
     */
    public SellerTaxRepresentativeParty build() {
      return new SellerTaxRepresentativeParty(name,
                                              vatId,
                                              address);
    }
  }
}

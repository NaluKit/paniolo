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

/**
 * Seller or buyer contact (BG-6 / BG-9).
 *
 * @param name      contact name or department (BT-41 / BT-56), or null
 * @param telephone contact telephone number (BT-42 / BT-57), or null
 * @param email     contact email address (BT-43 / BT-58), or null
 */
public record Contact(String name, String telephone, String email) {

  /**
   * Starts named construction of a {@link Contact} using the builder defaults.
   *
   * @return a new builder; not shared with other calls
   */
  public static Builder create() {
    return new Builder();
  }

  /**
   * Named construction of an immutable contact.
   *
   * <p>Mutable construction state for {@link Contact}. Use a separate builder per operation;
   * builders are not thread-safe. {@link #build()} constructs a new immutable value and applies
   * the checks of the model. XML schema and business rule validation are separate operations.
   */
  public static final class Builder {
    private String name;
    private String telephone;
    private String email;

    /**
     * Creates a builder with the defaults of the model; usually obtained with {@link Contact#create()}.
     */
    public Builder() {
    }

    /**
     * Sets {@link Contact#name() name}.
     *
     * @param name contact name or department (BT-41 / BT-56), or null
     * @return this builder
     */
    public Builder name(String name) {
      this.name = name;
      return this;
    }

    /**
     * Sets {@link Contact#telephone() telephone}.
     *
     * @param telephone contact telephone number (BT-42 / BT-57), or null
     * @return this builder
     */
    public Builder telephone(String telephone) {
      this.telephone = telephone;
      return this;
    }

    /**
     * Sets {@link Contact#email() email}.
     *
     * @param email contact email address (BT-43 / BT-58), or null
     * @return this builder
     */
    public Builder email(String email) {
      this.email = email;
      return this;
    }

    /**
     * Constructs a new {@link Contact} from the current values.
     *
     * @return the constructed value after the model checks succeed
     */
    public Contact build() {
      return new Contact(name,
                         telephone,
                         email);
    }
  }
}

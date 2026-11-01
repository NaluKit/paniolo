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
 * Payee (BG-10), only used when the payee differs from the seller.
 *
 * @param name                      payee name (BT-59); must not be blank
 * @param identifier                payee identifier (BT-60), or null
 * @param identifierSchemeId        scheme of the payee identifier, ISO 6523 ICD (BT-60-1), or null
 * @param legalRegistrationId       payee legal registration identifier (BT-61), or null
 * @param legalRegistrationSchemeId scheme of the legal registration identifier, ISO 6523 ICD
 *                                  (BT-61-1), or null
 */
public record Payee(String name, String identifier, String identifierSchemeId, String legalRegistrationId, String legalRegistrationSchemeId) {
  /**
   * Applies the checks of the model.
   *
   * @throws IllegalArgumentException if a value violates the checks of the model
   */
  public Payee {
    ModelChecks.required(name,
                         "payee name");
    if (identifierSchemeId != null && identifier == null) {
      throw new IllegalArgumentException("identifierSchemeId requires identifier");
    }
    // In UBL the scheme SEPA marks the creditor identifier (BT-90) of the payee party.
    if ("SEPA".equals(identifierSchemeId)) {
      throw new IllegalArgumentException("Payee identifier scheme SEPA is reserved for the bank assigned creditor identifier (BT-90)");
    }
    if (legalRegistrationSchemeId != null && legalRegistrationId == null) {
      throw new IllegalArgumentException("legalRegistrationSchemeId requires legalRegistrationId");
    }
  }

  /**
   * Creates a payee without identifier schemes.
   *
   * @param name                payee name (BT-59); must not be blank
   * @param identifier          payee identifier (BT-60), or null
   * @param legalRegistrationId payee legal registration identifier (BT-61), or null
   * @throws IllegalArgumentException if a value violates the checks of the model
   */
  public Payee(String name,
               String identifier,
               String legalRegistrationId) {
    this(name,
         identifier,
         null,
         legalRegistrationId,
         null);
  }

  /**
   * Starts named construction of a {@link Payee} using the builder defaults.
   *
   * @return a new builder; not shared with other calls
   */
  public static Builder create() {
    return new Builder();
  }

  /**
   * Named construction of an immutable payee.
   *
   * <p>Mutable construction state for {@link Payee}. Use a separate builder per operation;
   * builders are not thread-safe. {@link #build()} constructs a new immutable value and applies
   * the checks of the model. XML schema and business rule validation are separate operations.
   */
  public static final class Builder {
    private String name;
    private String identifier;
    private String identifierSchemeId;
    private String legalRegistrationId;
    private String legalRegistrationSchemeId;

    /**
     * Creates a builder with the defaults of the model; usually obtained with {@link Payee#create()}.
     */
    public Builder() {
    }

    /**
     * Sets {@link Payee#name() name}.
     *
     * @param name payee name (BT-59); must not be blank
     * @return this builder
     */
    public Builder name(String name) {
      this.name = name;
      return this;
    }

    /**
     * Payee identifier (BT-60).
     *
     * @param identifier payee identifier (BT-60), or null
     * @return this builder
     */
    public Builder identifier(String identifier) {
      this.identifier = identifier;
      return this;
    }

    /**
     * Payee identifier (BT-60) with its ISO 6523 scheme, e.g. "0088" for GLN.
     *
     * @param identifier the payee identifier
     * @param schemeId   the ISO 6523 scheme (BT-60-1), or null
     * @return this builder
     */
    public Builder identifier(String identifier,
                              String schemeId) {
      this.identifier         = identifier;
      this.identifierSchemeId = schemeId;
      return this;
    }

    /**
     * Payee legal registration identifier (BT-61).
     *
     * @param legalRegistrationId payee legal registration identifier (BT-61), or null
     * @return this builder
     */
    public Builder legalRegistrationId(String legalRegistrationId) {
      this.legalRegistrationId = legalRegistrationId;
      return this;
    }

    /**
     * Payee legal registration identifier (BT-61) with its ISO 6523 scheme, e.g. "0002" for SIRENE.
     *
     * @param legalRegistrationId the legal registration identifier
     * @param schemeId            the ISO 6523 scheme (BT-61-1), or null
     * @return this builder
     */
    public Builder legalRegistrationId(String legalRegistrationId,
                                       String schemeId) {
      this.legalRegistrationId       = legalRegistrationId;
      this.legalRegistrationSchemeId = schemeId;
      return this;
    }

    /**
     * Constructs a new {@link Payee} from the current values.
     *
     * @return the constructed value after the model checks succeed
     */
    public Payee build() {
      return new Payee(name,
                       identifier,
                       identifierSchemeId,
                       legalRegistrationId,
                       legalRegistrationSchemeId);
    }
  }
}

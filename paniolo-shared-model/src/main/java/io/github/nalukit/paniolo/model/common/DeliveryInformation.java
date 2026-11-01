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
 * Delivery information (BG-13) with an optional delivery address (BG-15).
 *
 * @param partyName          deliver-to party name (BT-70), or null
 * @param locationId         deliver-to location identifier (BT-71), or null
 * @param actualDeliveryDate actual delivery date (BT-72), or null
 * @param address            deliver-to address (BG-15), or null; at least one delivery field must be provided
 * @param locationSchemeId   scheme of the location identifier (BT-71-1), e.g. "0088" for GLN, or null
 */
public record DeliveryInformation(String partyName, String locationId, LocalDate actualDeliveryDate, Address address, String locationSchemeId) {
  /**
   * Applies the checks of the model.
   *
   * @throws IllegalArgumentException if a value violates the checks of the model
   */
  public DeliveryInformation {
    if (partyName == null && locationId == null && actualDeliveryDate == null && address == null) {
      throw new IllegalArgumentException("Delivery information must not be empty");
    }
    if (locationSchemeId != null && locationId == null) {
      throw new IllegalArgumentException("locationSchemeId requires locationId");
    }
  }

  /**
   * Delivery information with a location identifier without scheme.
   *
   * @param partyName          deliver-to party name (BT-70), or null
   * @param locationId         deliver-to location identifier (BT-71), or null
   * @param actualDeliveryDate actual delivery date (BT-72), or null
   * @param address            deliver-to address (BG-15), or null
   * @throws IllegalArgumentException if all values are null
   */
  public DeliveryInformation(String partyName,
                             String locationId,
                             LocalDate actualDeliveryDate,
                             Address address) {
    this(partyName,
         locationId,
         actualDeliveryDate,
         address,
         null);
  }

  /**
   * Starts named construction of a {@link DeliveryInformation} using the builder defaults.
   *
   * @return a new builder; not shared with other calls
   */
  public static Builder create() {
    return new Builder();
  }

  /**
   * Named construction of immutable delivery information.
   *
   * <p>Mutable construction state for {@link DeliveryInformation}. Use a separate builder per operation;
   * builders are not thread-safe. {@link #build()} constructs a new immutable value and applies
   * the checks of the model. XML schema and business rule validation are separate operations.
   */
  public static final class Builder {
    private String    partyName;
    private String    locationId;
    private LocalDate actualDeliveryDate;
    private Address   address;
    private String    locationSchemeId;

    /**
     * Creates a builder with the defaults of the model; usually obtained with {@link DeliveryInformation#create()}.
     */
    public Builder() {
    }

    /**
     * Deliver to party name (BT-70).
     *
     * @param partyName deliver-to party name (BT-70), or null
     * @return this builder
     */
    public Builder partyName(String partyName) {
      this.partyName = partyName;
      return this;
    }

    /**
     * Deliver to location identifier (BT-71).
     *
     * @param locationId deliver-to location identifier (BT-71), or null
     * @return this builder
     */
    public Builder locationId(String locationId) {
      this.locationId = locationId;
      return this;
    }

    /**
     * Deliver to location identifier (BT-71) with its scheme (BT-71-1), e.g. "0088" for GLN.
     *
     * @param locationId the deliver-to location identifier, or null
     * @param schemeId   its scheme, or null
     * @return this builder
     */
    public Builder locationId(String locationId,
                              String schemeId) {
      this.locationId       = locationId;
      this.locationSchemeId = schemeId;
      return this;
    }

    /**
     * Actual delivery date (BT-72).
     *
     * @param actualDeliveryDate actual delivery date (BT-72), or null
     * @return this builder
     */
    public Builder actualDeliveryDate(LocalDate actualDeliveryDate) {
      this.actualDeliveryDate = actualDeliveryDate;
      return this;
    }

    /**
     * Sets {@link DeliveryInformation#address() address}.
     *
     * @param address deliver-to address (BG-15), or null; at least one delivery field must be provided
     * @return this builder
     */
    public Builder address(Address address) {
      this.address = address;
      return this;
    }

    /**
     * Constructs a new {@link DeliveryInformation} from the current values.
     *
     * @return the constructed value after the model checks succeed
     */
    public DeliveryInformation build() {
      return new DeliveryInformation(partyName,
                                     locationId,
                                     actualDeliveryDate,
                                     address,
                                     locationSchemeId);
    }
  }
}

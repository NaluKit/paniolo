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
import io.github.nalukit.paniolo.model.common.value.Country;

import java.util.Objects;

/**
 * Postal address (BG-5 / BG-8 / BG-12 / BG-15). EN 16931 requires the country only (BR-09, BR-11,
 * BR-20, BR-57); what a profile requires beyond it, e.g. city and post code of seller and buyer
 * in XRechnung (BR-DE-3, BR-DE-4, BR-DE-8, BR-DE-9), is checked by validation. A value that is
 * set must not be blank. The Factur-X profile MINIMUM uses an address with the country only for
 * the seller.
 *
 * @param street             address line 1 (BT-35 / BT-50 / BT-64 / BT-75), or null
 * @param additionalStreet   address line 2 (BT-36 / BT-51 / BT-65 / BT-76), or null
 * @param additionalLine     address line 3 (BT-162 / BT-163 / BT-164 / BT-165), or null
 * @param city               city (BT-37 / BT-52 / BT-66 / BT-77), or null
 * @param postalCode         post code (BT-38 / BT-53 / BT-67 / BT-78), or null
 * @param countrySubdivision country subdivision (BT-39 / BT-54 / BT-68 / BT-79), or null
 * @param country            country code (BT-40 / BT-55 / BT-69 / BT-80); required
 */
public record Address(String street, String additionalStreet, String additionalLine, String city, String postalCode, String countrySubdivision, Country country) {
  /**
   * Applies the checks of the model.
   *
   * @throws NullPointerException     if a required value is null
   * @throws IllegalArgumentException if a value violates the checks of the model
   */
  public Address {
    // No value is invented for a missing one, and a blank one is none.
    notBlank(street,
             "street");
    notBlank(additionalStreet,
             "additionalStreet");
    notBlank(additionalLine,
             "additionalLine");
    notBlank(city,
             "city");
    notBlank(postalCode,
             "postalCode");
    notBlank(countrySubdivision,
             "countrySubdivision");
    Objects.requireNonNull(country,
                           "country");
  }

  private static void notBlank(String value,
                               String name) {
    if (value != null) {
      ModelChecks.required(value,
                           name);
    }
  }

  /**
   * An address with street, city, post code, and country only.
   *
   * @param street     address line 1 (BT-35 / BT-50)
   * @param city       city (BT-37 / BT-52)
   * @param postalCode post code (BT-38 / BT-53)
   * @param country    country (BT-40 / BT-55)
   * @throws IllegalArgumentException if street, city, or post code is set but blank
   * @throws NullPointerException     if the country is null
   */
  public Address(String street,
                 String city,
                 String postalCode,
                 Country country) {
    this(street,
         null,
         null,
         city,
         postalCode,
         null,
         country);
  }

  /**
   * An address with the country only (BT-40), as in the Factur-X profile MINIMUM.
   *
   * @param country the country
   * @return the address
   * @throws NullPointerException if the country is null
   */
  public static Address ofCountry(Country country) {
    return new Address(null,
                       null,
                       null,
                       null,
                       null,
                       null,
                       country);
  }

  /**
   * Starts named construction of a {@link Address} using the builder defaults.
   *
   * @return a new builder; not shared with other calls
   */
  public static Builder create() {
    return new Builder();
  }

  /**
   * Returns true if the address carries only the country.
   *
   * @return true if only the country is set
   */
  public boolean isCountryOnly() {
    return street == null && additionalStreet == null && additionalLine == null && city == null && postalCode == null && countrySubdivision == null;
  }



  /**
   * Named construction of an immutable address.
   *
   * <p>Mutable construction state for {@link Address}. Use a separate builder per operation;
   * builders are not thread-safe. {@link #build()} constructs a new immutable value and applies
   * the checks of the model. XML schema and business rule validation are separate operations.
   */
  public static final class Builder {
    private String  street;
    private String  additionalStreet;
    private String  additionalLine;
    private String  city;
    private String  postalCode;
    private String  countrySubdivision;
    private Country country;

    /**
     * Creates a builder with the defaults of the model; usually obtained with {@link Address#create()}.
     */
    public Builder() {
    }

    /**
     * Sets {@link Address#street() street}.
     *
     * @param street address line 1 (BT-35 / BT-50 / BT-64 / BT-75); required except for a country-only address
     * @return this builder
     */
    public Builder street(String street) {
      this.street = street;
      return this;
    }

    /**
     * Address line 2 (BT-36 / BT-51 / BT-65 / BT-76).
     *
     * @param additionalStreet address line 2 (BT-36 / BT-51 / BT-65 / BT-76), or null
     * @return this builder
     */
    public Builder additionalStreet(String additionalStreet) {
      this.additionalStreet = additionalStreet;
      return this;
    }

    /**
     * Address line 3 (BT-162 / BT-163 / BT-164 / BT-165).
     *
     * @param additionalLine address line 3 (BT-162 / BT-163 / BT-164 / BT-165), or null
     * @return this builder
     */
    public Builder additionalLine(String additionalLine) {
      this.additionalLine = additionalLine;
      return this;
    }

    /**
     * Sets {@link Address#city() city}.
     *
     * @param city city (BT-37 / BT-52 / BT-66 / BT-77); required except for a country-only address
     * @return this builder
     */
    public Builder city(String city) {
      this.city = city;
      return this;
    }

    /**
     * Sets {@link Address#postalCode() postalCode}.
     *
     * @param postalCode post code (BT-38 / BT-53 / BT-67 / BT-78); required except for a country-only address
     * @return this builder
     */
    public Builder postalCode(String postalCode) {
      this.postalCode = postalCode;
      return this;
    }

    /**
     * Sets {@link Address#countrySubdivision() countrySubdivision}.
     *
     * @param countrySubdivision country subdivision (BT-39 / BT-54 / BT-68 / BT-79), or null
     * @return this builder
     */
    public Builder countrySubdivision(String countrySubdivision) {
      this.countrySubdivision = countrySubdivision;
      return this;
    }

    /**
     * Sets {@link Address#country() country}.
     *
     * @param country country code (BT-40 / BT-55 / BT-69 / BT-80); required
     * @return this builder
     */
    public Builder country(Country country) {
      this.country = country;
      return this;
    }

    /**
     * Constructs a new {@link Address} from the current values.
     *
     * @return the constructed value after the model checks succeed
     */
    public Address build() {
      return new Address(street,
                         additionalStreet,
                         additionalLine,
                         city,
                         postalCode,
                         countrySubdivision,
                         country);
    }
  }
}

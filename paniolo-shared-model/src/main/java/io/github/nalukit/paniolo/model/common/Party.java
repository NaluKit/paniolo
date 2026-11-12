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
import io.github.nalukit.paniolo.model.extended.PartyExtension;

import java.util.ArrayList;
import java.util.List;

/**
 * Seller (BG-4) or buyer (BG-7); VAT and national tax identifiers are independent.
 *
 * @param name                       seller / buyer name (BT-27 / BT-44); null only for a party identified by its legal registration or identifier
 * @param address                    postal address (BG-5 / BG-8); null only for the buyer of a Factur-X MINIMUM
 * @param vatId                      VAT identifier (BT-31 / BT-48)
 * @param taxId                      seller tax registration identifier (BT-32); buyers have none
 * @param electronicAddress          electronic address (BT-34 / BT-49)
 * @param contact                    contact (BG-6 / BG-9)
 * @param identifier                 seller / buyer identifier (BT-29 / BT-46)
 * @param identifierSchemeId         scheme of the identifier, ISO 6523 ICD (BT-29-1 / BT-46-1)
 * @param tradingName                trading name (BT-28 / BT-45)
 * @param legalRegistrationId        legal registration identifier, e.g. the commercial register number (BT-30 / BT-47)
 * @param legalRegistrationSchemeId  scheme of the legal registration identifier (BT-30-1 / BT-47-1)
 * @param additionalLegalInformation seller additional legal information, e.g. share capital (BT-33); buyers have none
 * @param extended                   party information of the Factur-X profile EXTENDED, or null
 * @param additionalIdentifiers      further identifiers next to {@code identifier}, e.g. a second
 *                                   seller identifier (BT-29 is repeatable); EN 16931 has one buyer
 *                                   identifier (BT-46), so writers accept further buyer identifiers in
 *                                   the Factur-X profile EXTENDED only
 */
public record Party(String name, Address address, String vatId, String taxId, ElectronicAddress electronicAddress, Contact contact, String identifier, String identifierSchemeId, String tradingName, String legalRegistrationId,
                    String legalRegistrationSchemeId, String additionalLegalInformation, PartyExtension extended, List<PartyIdentifier> additionalIdentifiers) {

  /**
   * A party with identifier, without scheme, trading name, legal registration, and EXTENDED
   * information.
   *
   * @param name              seller / buyer name (BT-27 / BT-44)
   * @param address           postal address (BG-5 / BG-8)
   * @param vatId             VAT identifier (BT-31 / BT-48), or null
   * @param taxId             seller tax registration identifier (BT-32), or null
   * @param electronicAddress electronic address (BT-34 / BT-49), or null
   * @param contact           contact (BG-6 / BG-9), or null
   * @param identifier        seller / buyer identifier (BT-29 / BT-46), or null
   * @throws IllegalArgumentException if the name is blank
   */
  public Party(String name,
               Address address,
               String vatId,
               String taxId,
               ElectronicAddress electronicAddress,
               Contact contact,
               String identifier) {
    this(name,
         address,
         vatId,
         taxId,
         electronicAddress,
         contact,
         identifier,
         null,
         null,
         null,
         null,
         null,
         null);
  }

  /**
   * A party without identifier, trading name, legal registration, and EXTENDED information.
   *
   * @param name              seller / buyer name (BT-27 / BT-44)
   * @param address           postal address (BG-5 / BG-8)
   * @param vatId             VAT identifier (BT-31 / BT-48), or null
   * @param taxId             seller tax registration identifier (BT-32), or null
   * @param electronicAddress electronic address (BT-34 / BT-49), or null
   * @param contact           contact (BG-6 / BG-9), or null
   * @throws IllegalArgumentException if the name is blank
   */
  public Party(String name,
               Address address,
               String vatId,
               String taxId,
               ElectronicAddress electronicAddress,
               Contact contact) {
    this(name,
         address,
         vatId,
         taxId,
         electronicAddress,
         contact,
         null);
  }

  /**
   * Applies the checks of the model.
   *
   * @throws IllegalArgumentException if a value violates the checks of the model
   */
  public Party {
    // Only a party identified by its legal registration or identifier may leave out the name, as
    // the item seller of a sub-invoice line of a French multi-vendor invoice does; the documents
    // require the name of their parties (see requireAddress(Party, String)).
    if (name != null || legalRegistrationId == null && identifier == null) {
      ModelChecks.required(name,
                           "party name");
    }
    if (identifierSchemeId != null && identifier == null) {
      throw new IllegalArgumentException("identifierSchemeId requires identifier");
    }
    if (legalRegistrationSchemeId != null && legalRegistrationId == null) {
      throw new IllegalArgumentException("legalRegistrationSchemeId requires legalRegistrationId");
    }
    // Empty EXTENDED information is the same as none, so readers and writers agree.
    if (extended != null && extended.isEmpty()) {
      extended = null;
    }
    // CII allows one contact per party (CII-SR-465).
    if (contact != null && extended != null && extended.contact() != null) {
      throw new IllegalArgumentException("Set either Party.contact or the EXTENDED contact, not both");
    }
    additionalIdentifiers = additionalIdentifiers == null ?
                            List.of() :
                            List.copyOf(additionalIdentifiers);
    if (!additionalIdentifiers.isEmpty() && identifier == null) {
      throw new IllegalArgumentException("additionalIdentifiers require identifier");
    }
  }

  /**
   * A party without further identifiers.
   *
   * @param name                       seller / buyer name (BT-27 / BT-44)
   * @param address                    postal address (BG-5 / BG-8)
   * @param vatId                      VAT identifier (BT-31 / BT-48), or null
   * @param taxId                      seller tax registration identifier (BT-32), or null
   * @param electronicAddress          electronic address (BT-34 / BT-49), or null
   * @param contact                    contact (BG-6 / BG-9), or null
   * @param identifier                 seller / buyer identifier (BT-29 / BT-46), or null
   * @param identifierSchemeId         scheme of the identifier (BT-29-1 / BT-46-1), or null
   * @param tradingName                trading name (BT-28 / BT-45), or null
   * @param legalRegistrationId        legal registration identifier (BT-30 / BT-47), or null
   * @param legalRegistrationSchemeId  scheme of the legal registration identifier, or null
   * @param additionalLegalInformation seller additional legal information (BT-33), or null
   * @param extended                   party information of the Factur-X profile EXTENDED, or null
   * @throws IllegalArgumentException if a value violates the checks of the model
   */
  public Party(String name,
               Address address,
               String vatId,
               String taxId,
               ElectronicAddress electronicAddress,
               Contact contact,
               String identifier,
               String identifierSchemeId,
               String tradingName,
               String legalRegistrationId,
               String legalRegistrationSchemeId,
               String additionalLegalInformation,
               PartyExtension extended) {
    this(name,
         address,
         vatId,
         taxId,
         electronicAddress,
         contact,
         identifier,
         identifierSchemeId,
         tradingName,
         legalRegistrationId,
         legalRegistrationSchemeId,
         additionalLegalInformation,
         extended,
         List.of());
  }

  /**
   * A party without EXTENDED information.
   *
   * @param name                       seller / buyer name (BT-27 / BT-44)
   * @param address                    postal address (BG-5 / BG-8)
   * @param vatId                      VAT identifier (BT-31 / BT-48), or null
   * @param taxId                      seller tax registration identifier (BT-32), or null
   * @param electronicAddress          electronic address (BT-34 / BT-49), or null
   * @param contact                    contact (BG-6 / BG-9), or null
   * @param identifier                 seller / buyer identifier (BT-29 / BT-46), or null
   * @param identifierSchemeId         scheme of the identifier (BT-29-1 / BT-46-1), or null
   * @param tradingName                trading name (BT-28 / BT-45), or null
   * @param legalRegistrationId        legal registration identifier (BT-30 / BT-47), or null
   * @param legalRegistrationSchemeId  scheme of the legal registration identifier, or null
   * @param additionalLegalInformation seller additional legal information (BT-33), or null
   * @throws IllegalArgumentException if a value violates the checks of the model
   */
  public Party(String name,
               Address address,
               String vatId,
               String taxId,
               ElectronicAddress electronicAddress,
               Contact contact,
               String identifier,
               String identifierSchemeId,
               String tradingName,
               String legalRegistrationId,
               String legalRegistrationSchemeId,
               String additionalLegalInformation) {
    this(name,
         address,
         vatId,
         taxId,
         electronicAddress,
         contact,
         identifier,
         identifierSchemeId,
         tradingName,
         legalRegistrationId,
         legalRegistrationSchemeId,
         additionalLegalInformation,
         null);
  }

  /**
   * Rejects a party without name or without a postal address beyond the country, which all
   * documents except the Factur-X profile MINIMUM state; null parties are allowed. Which address
   * values a profile requires, e.g. city and post code in XRechnung, is checked by validation.
   *
   * @param party the party, or null
   * @param role  role of the party for the error message, e.g. {@code seller}
   * @return the party
   * @throws IllegalArgumentException if the name is missing or the address is missing or incomplete
   */
  public static Party requireAddress(Party party,
                                     String role) {
    requireName(party,
                role);
    if (party != null &&
        (party.address() == null ||
         party.address()
              .isCountryOnly())) {
      throw new IllegalArgumentException("The " + role + " requires a postal address with more than the country, e.g. city and post code");
    }
    return party;
  }

  /**
   * Rejects a party without name; null parties are allowed.
   *
   * @param party the party, or null
   * @param role  role of the party for the error message, e.g. {@code seller}
   * @return the party
   * @throws IllegalArgumentException if the name is missing
   */
  public static Party requireName(Party party,
                                  String role) {
    if (party != null && party.name() == null) {
      throw new IllegalArgumentException("The " + role + " requires a name");
    }
    return party;
  }

  /**
   * Checks that the electronic address of the party, if any, has a scheme, as invoices (BT-34-1 /
   * BT-49-1) and despatch advices require; Order-X allows an address without scheme.
   *
   * @param party the party, or null
   * @param role  role of the party for the error message, e.g. {@code seller}
   * @return the party
   * @throws IllegalArgumentException if the electronic address has no scheme
   */
  public static Party requireElectronicAddressScheme(Party party,
                                                     String role) {
    if (party != null &&
        party.electronicAddress() != null &&
        party.electronicAddress()
             .schemeId() == null) {
      throw new IllegalArgumentException("The electronic address of the " + role + " requires a scheme");
    }
    return party;
  }

  /**
   * Starts named construction of a {@link Party} using the builder defaults.
   *
   * @return a new builder; not shared with other calls
   */
  public static Builder create() {
    return new Builder();
  }

  /**
   * Named construction of an immutable party.
   *
   * <p>Mutable construction state for {@link Party}. Use a separate builder per operation;
   * builders are not thread-safe. {@link #build()} constructs a new immutable value and applies
   * the checks of the model. XML schema and business rule validation are separate operations.
   */
  public static final class Builder {
    private String            name;
    private Address           address;
    private String            vatId;
    private String            taxId;
    private ElectronicAddress electronicAddress;
    private Contact           contact;
    private String            identifier;
    private String            identifierSchemeId;
    private String            tradingName;
    private String            legalRegistrationId;
    private String            legalRegistrationSchemeId;
    private String            additionalLegalInformation;
    private PartyExtension    extended;
    private List<PartyIdentifier> additionalIdentifiers = new ArrayList<>();

    /**
     * Creates a builder with the defaults of the model; usually obtained with {@link Party#create()}.
     */
    public Builder() {
    }

    /**
     * Sets {@link Party#name() name}.
     *
     * @param name seller / buyer name (BT-27 / BT-44); null only for a party identified by its legal
     *             registration or identifier
     * @return this builder
     */
    public Builder name(String name) {
      this.name = name;
      return this;
    }

    /**
     * Sets {@link Party#address() address}.
     *
     * @param address postal address (BG-5 / BG-8); null only for the buyer of a Factur-X MINIMUM
     * @return this builder
     */
    public Builder address(Address address) {
      this.address = address;
      return this;
    }

    /**
     * Sets {@link Party#vatId() vatId}.
     *
     * @param vatId VAT identifier (BT-31 / BT-48)
     * @return this builder
     */
    public Builder vatId(String vatId) {
      this.vatId = vatId;
      return this;
    }

    /**
     * Sets {@link Party#taxId() taxId}.
     *
     * @param taxId seller tax registration identifier (BT-32); buyers have none
     * @return this builder
     */
    public Builder taxId(String taxId) {
      this.taxId = taxId;
      return this;
    }

    /**
     * Sets {@link Party#electronicAddress() electronicAddress}.
     *
     * @param electronicAddress electronic address (BT-34 / BT-49)
     * @return this builder
     */
    public Builder electronicAddress(ElectronicAddress electronicAddress) {
      this.electronicAddress = electronicAddress;
      return this;
    }

    /**
     * Sets {@link Party#contact() contact}.
     *
     * @param contact contact (BG-6 / BG-9)
     * @return this builder
     */
    public Builder contact(Contact contact) {
      this.contact = contact;
      return this;
    }

    /**
     * Seller / buyer identifier (BT-29 / BT-46) without scheme.
     *
     * @param identifier seller / buyer identifier (BT-29 / BT-46)
     * @return this builder
     */
    public Builder identifier(String identifier) {
      this.identifier = identifier;
      return this;
    }

    /**
     * Seller / buyer identifier (BT-29 / BT-46) with its ISO 6523 scheme, e.g. "0088" for GLN.
     *
     * @param identifier the seller / buyer identifier
     * @param schemeId   the ISO 6523 scheme (BT-29-1 / BT-46-1), or null
     * @return this builder
     */
    public Builder identifier(String identifier,
                              String schemeId) {
      this.identifier         = identifier;
      this.identifierSchemeId = schemeId;
      return this;
    }

    /**
     * Trading name (BT-28 / BT-45), if different from the legal name.
     *
     * @param tradingName trading name (BT-28 / BT-45)
     * @return this builder
     */
    public Builder tradingName(String tradingName) {
      this.tradingName = tradingName;
      return this;
    }

    /**
     * Legal registration identifier (BT-30 / BT-47), e.g. "HRB 12345" of the commercial register.
     *
     * @param legalRegistrationId legal registration identifier, e.g. the commercial register number (BT-30 / BT-47)
     * @return this builder
     */
    public Builder legalRegistrationId(String legalRegistrationId) {
      this.legalRegistrationId = legalRegistrationId;
      return this;
    }

    /**
     * Legal registration identifier (BT-30 / BT-47) with its ISO 6523 scheme.
     *
     * @param legalRegistrationId the legal registration identifier
     * @param schemeId            the ISO 6523 scheme (BT-30-1 / BT-47-1), or null
     * @return this builder
     */
    public Builder legalRegistrationId(String legalRegistrationId,
                                       String schemeId) {
      this.legalRegistrationId       = legalRegistrationId;
      this.legalRegistrationSchemeId = schemeId;
      return this;
    }

    /**
     * Seller additional legal information (BT-33), e.g. "Managing director: Erika Muster".
     *
     * @param additionalLegalInformation seller additional legal information, e.g. share capital (BT-33); buyers have none
     * @return this builder
     */
    public Builder additionalLegalInformation(String additionalLegalInformation) {
      this.additionalLegalInformation = additionalLegalInformation;
      return this;
    }

    /**
     * Party information of the Factur-X / ZUGFeRD profile EXTENDED, e.g. role code or further contacts.
     *
     * @param extended party information of the Factur-X profile EXTENDED, or null
     * @return this builder
     */
    public Builder extended(PartyExtension extended) {
      this.extended = extended;
      return this;
    }

    /**
     * Adds a further identifier next to {@link #identifier(String, String)}, e.g. a second seller
     * identifier (BT-29) or a local identifier next to a global one.
     *
     * @param value the further identifier
     * @return this builder
     */
    public Builder addAdditionalIdentifier(PartyIdentifier value) {
      this.additionalIdentifiers.add(value);
      return this;
    }

    /**
     * Constructs a new {@link Party} from the current values.
     *
     * @return the constructed value after the model checks succeed
     */
    public Party build() {
      return new Party(name,
                       address,
                       vatId,
                       taxId,
                       electronicAddress,
                       contact,
                       identifier,
                       identifierSchemeId,
                       tradingName,
                       legalRegistrationId,
                       legalRegistrationSchemeId,
                       additionalLegalInformation,
                       extended,
                       additionalIdentifiers);
    }
  }
}

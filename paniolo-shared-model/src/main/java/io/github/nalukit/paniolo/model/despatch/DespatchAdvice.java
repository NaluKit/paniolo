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
package io.github.nalukit.paniolo.model.despatch;

import io.github.nalukit.paniolo.model.common.AdditionalSupportingDocument;
import io.github.nalukit.paniolo.model.common.Address;
import io.github.nalukit.paniolo.model.common.InvoicePeriod;
import io.github.nalukit.paniolo.model.common.Party;
import io.github.nalukit.paniolo.model.common.validation.ModelChecks;
import io.github.nalukit.paniolo.model.common.value.UnitCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * Immutable despatch advice (delivery note) after Peppol BIS Despatch Advice 3, written as UBL 2.1
 * DespatchAdvice or as UN/CEFACT Cross Industry Despatch Advice (CIDA). With the identifiers of
 * 1Lieferschein ({@link Builder#oneLieferschein()}) it is written as UBL DespatchAdvice without the
 * Peppol restrictions. It shares parties, addresses, units, and classifications with the invoice
 * model.
 *
 * @param number                  despatch advice number
 * @param issueDate               issue date
 * @param note                    note, or null
 * @param orderReferences         orders the despatch belongs to
 * @param additionalDocuments     further documents, e.g. certificates
 * @param despatchSupplier        party despatching the goods; its electronic address is the Peppol endpoint
 * @param deliveryCustomer        party receiving the goods; its electronic address is the Peppol endpoint
 * @param buyer                   buyer
 * @param seller                  seller, if not the despatch supplier; or null
 * @param originator              party that ordered within the buyer, or null
 * @param shipmentId              shipment identifier, required by UBL with any shipment detail; or null
 * @param shipmentInformation     shipment information, or null
 * @param grossWeight             gross weight, or null
 * @param grossWeightUnit         unit of the gross weight, e.g. KGM; or null
 * @param grossVolume             gross volume, or null
 * @param grossVolumeUnit         unit of the gross volume, e.g. MTQ; or null
 * @param handlingUnitCount       number of transport handling units, e.g. pallets; or null
 * @param transportModeCode       UN/ECE Rec. 19 transport mode, or null
 * @param trackingId              tracking number of the carrier, or null
 * @param estimatedDeliveryPeriod estimated delivery period, or null
 * @param actualDespatchDate      date the goods were despatched, or null
 * @param actualDeliveryDate      date the goods were delivered, or null; not in Peppol and CIDA
 * @param despatchAddress         address the goods were despatched from, or null
 * @param lines                   despatch lines
 * @param customizationId         customization identifier; Peppol BIS Despatch Advice 3 by default,
 *                                {@link #ONE_LIEFERSCHEIN_CUSTOMIZATION} for 1Lieferschein
 * @param profileId               profile identifier; Peppol BIS Despatch Advice 3 by default,
 *                                {@link #ONE_LIEFERSCHEIN_PROFILE} for 1Lieferschein
 */
public record DespatchAdvice(String number, LocalDate issueDate, String note, List<String> orderReferences, List<AdditionalSupportingDocument> additionalDocuments, Party despatchSupplier, Party deliveryCustomer, Party buyer, Party seller, Party originator,
                             String shipmentId, String shipmentInformation, BigDecimal grossWeight, UnitCode grossWeightUnit, BigDecimal grossVolume, UnitCode grossVolumeUnit, BigDecimal handlingUnitCount, String transportModeCode, String trackingId,
                             InvoicePeriod estimatedDeliveryPeriod, LocalDate actualDespatchDate, LocalDate actualDeliveryDate, Address despatchAddress, List<DespatchLine> lines, String customizationId, String profileId) {
  /**
   * Customization identifier of Peppol BIS Despatch Advice 3.
   */
  public static final String PEPPOL_CUSTOMIZATION           = "urn:fdc:peppol.eu:poacc:trns:despatch_advice:3";
  /**
   * Profile identifier of Peppol BIS Despatch Advice 3.
   */
  public static final String PEPPOL_PROFILE                 = "urn:fdc:peppol.eu:poacc:bis:despatch_advice:3";
  /**
   * Customization identifier of 1Lieferschein, as the Mustang project writes it.
   */
  public static final String ONE_LIEFERSCHEIN_CUSTOMIZATION = "1Lieferschein";
  /**
   * Profile identifier of 1Lieferschein, as the Mustang project writes it.
   */
  public static final String ONE_LIEFERSCHEIN_PROFILE       = "ubl-xml-only";

  /**
   * Applies the checks of the model and copies the lists.
   *
   * @throws NullPointerException     if a required value is null
   * @throws IllegalArgumentException if a value violates the checks of the model
   */
  public DespatchAdvice {
    ModelChecks.required(number,
                         "despatch advice number");
    Objects.requireNonNull(issueDate,
                           "issueDate");
    Party.requireAddress(Objects.requireNonNull(despatchSupplier,
                                                "despatchSupplier"),
                         "despatch supplier");
    Party.requireAddress(Objects.requireNonNull(deliveryCustomer,
                                                "deliveryCustomer"),
                         "delivery customer");
    Party.requireAddress(Objects.requireNonNull(buyer,
                                                "buyer"),
                         "buyer");
    Party.requireAddress(seller,
                         "seller");
    Party.requireAddress(originator,
                         "originator");
    for (Party party : new Party[] { despatchSupplier,
                                     deliveryCustomer,
                                     buyer,
                                     seller,
                                     originator }) {
      Party.requireElectronicAddressScheme(party,
                                           "despatch advice party");
    }
    ModelChecks.required(customizationId,
                         "customizationId");
    ModelChecks.required(profileId,
                         "profileId");
    if (lines == null || lines.isEmpty()) {
      throw new IllegalArgumentException("A despatch advice requires at least one line");
    }
    var ids = new HashSet<String>();
    for (DespatchLine line : lines) {
      if (!ids.add(line.id())) {
        throw new IllegalArgumentException("Duplicate despatch line id: " + line.id());
      }
    }
    if ((grossWeight == null) != (grossWeightUnit == null) || (grossVolume == null) != (grossVolumeUnit == null)) {
      throw new IllegalArgumentException("Gross weight and volume need their unit");
    }
    orderReferences     = orderReferences == null ?
                          List.of() :
                          List.copyOf(orderReferences);
    additionalDocuments = additionalDocuments == null ?
                          List.of() :
                          List.copyOf(additionalDocuments);
    lines               = lines == null ?
                          List.of() :
                          List.copyOf(lines);
  }

  /**
   * Starts named construction of a {@link DespatchAdvice} using the builder defaults.
   *
   * @return a new builder; not shared with other calls
   */
  public static Builder create() {
    return new Builder();
  }

  /**
   * Whether the advice follows Peppol BIS Despatch Advice 3, judged by its customization identifier.
   *
   * @return true for Peppol BIS Despatch Advice 3
   */
  public boolean isPeppol() {
    return PEPPOL_CUSTOMIZATION.equals(customizationId);
  }

  /**
   * Whether the advice is a 1Lieferschein, judged by its customization identifier.
   *
   * @return true for 1Lieferschein
   */
  public boolean isOneLieferschein() {
    return ONE_LIEFERSCHEIN_CUSTOMIZATION.equals(customizationId);
  }

  /**
   * Whether the advice carries shipment details besides the shipment identifier. UBL writes them
   * in a shipment, which requires the shipment identifier.
   *
   * @return true if shipment details are set
   */
  public boolean hasShipmentDetails() {
    return shipmentInformation != null ||
           grossWeight != null ||
           grossVolume != null ||
           handlingUnitCount != null ||
           transportModeCode != null ||
           trackingId != null ||
           estimatedDeliveryPeriod != null ||
           actualDespatchDate != null ||
           actualDeliveryDate != null ||
           despatchAddress != null;
  }

  /**
   * Named construction of an immutable despatch advice.
   *
   * <p>Mutable construction state for {@link DespatchAdvice}. Use a separate builder per operation;
   * builders are not thread-safe. {@link #build()} constructs a new immutable value and applies
   * the checks of the model. XML schema and business rule validation are separate operations.
   */
  public static final class Builder {
    private String                             number;
    private LocalDate                          issueDate;
    private String                             note;
    private List<String>                       orderReferences     = new ArrayList<>();
    private List<AdditionalSupportingDocument> additionalDocuments = new ArrayList<>();
    private Party                              despatchSupplier;
    private Party                              deliveryCustomer;
    private Party                              buyer;
    private Party                              seller;
    private Party                              originator;
    private String                             shipmentId;
    private String                             shipmentInformation;
    private BigDecimal                         grossWeight;
    private UnitCode                           grossWeightUnit;
    private BigDecimal                         grossVolume;
    private UnitCode                           grossVolumeUnit;
    private BigDecimal                         handlingUnitCount;
    private String                             transportModeCode;
    private String                             trackingId;
    private InvoicePeriod                      estimatedDeliveryPeriod;
    private LocalDate                          actualDespatchDate;
    private LocalDate                          actualDeliveryDate;
    private Address                            despatchAddress;
    private List<DespatchLine>                 lines               = new ArrayList<>();
    private String                             customizationId     = PEPPOL_CUSTOMIZATION;
    private String                             profileId           = PEPPOL_PROFILE;

    /**
     * Creates a builder with the defaults of the model; usually obtained with {@link DespatchAdvice#create()}.
     */
    public Builder() {
    }

    /**
     * Sets {@link DespatchAdvice#number() number}.
     *
     * @param number despatch advice number
     * @return this builder
     */
    public Builder number(String number) {
      this.number = number;
      return this;
    }

    /**
     * Sets {@link DespatchAdvice#issueDate() issueDate}.
     *
     * @param issueDate issue date
     * @return this builder
     */
    public Builder issueDate(LocalDate issueDate) {
      this.issueDate = issueDate;
      return this;
    }

    /**
     * Sets {@link DespatchAdvice#note() note}.
     *
     * @param note note, or null
     * @return this builder
     */
    public Builder note(String note) {
      this.note = note;
      return this;
    }

    /**
     * Adds a value to {@link DespatchAdvice#orderReferences() orderReferences}.
     *
     * @param value the string to add
     * @return this builder
     */
    public Builder addOrderReference(String value) {
      this.orderReferences.add(value);
      return this;
    }

    /**
     * Adds a value to {@link DespatchAdvice#additionalDocuments() additionalDocuments}.
     *
     * @param value the additional supporting document to add
     * @return this builder
     */
    public Builder addAdditionalDocument(AdditionalSupportingDocument value) {
      this.additionalDocuments.add(value);
      return this;
    }

    /**
     * Sets {@link DespatchAdvice#despatchSupplier() despatchSupplier}.
     *
     * @param despatchSupplier party despatching the goods; its electronic address is the Peppol endpoint
     * @return this builder
     */
    public Builder despatchSupplier(Party despatchSupplier) {
      this.despatchSupplier = despatchSupplier;
      return this;
    }

    /**
     * Sets {@link DespatchAdvice#deliveryCustomer() deliveryCustomer}.
     *
     * @param deliveryCustomer party receiving the goods; its electronic address is the Peppol endpoint
     * @return this builder
     */
    public Builder deliveryCustomer(Party deliveryCustomer) {
      this.deliveryCustomer = deliveryCustomer;
      return this;
    }

    /**
     * Sets {@link DespatchAdvice#buyer() buyer}.
     *
     * @param buyer buyer
     * @return this builder
     */
    public Builder buyer(Party buyer) {
      this.buyer = buyer;
      return this;
    }

    /**
     * Sets {@link DespatchAdvice#seller() seller}.
     *
     * @param seller seller, if not the despatch supplier; or null
     * @return this builder
     */
    public Builder seller(Party seller) {
      this.seller = seller;
      return this;
    }

    /**
     * Sets {@link DespatchAdvice#originator() originator}.
     *
     * @param originator party that ordered within the buyer, or null
     * @return this builder
     */
    public Builder originator(Party originator) {
      this.originator = originator;
      return this;
    }

    /**
     * Sets {@link DespatchAdvice#shipmentId() shipmentId}.
     *
     * @param shipmentId shipment identifier, required by UBL with any shipment detail; or null
     * @return this builder
     */
    public Builder shipmentId(String shipmentId) {
      this.shipmentId = shipmentId;
      return this;
    }

    /**
     * Sets {@link DespatchAdvice#shipmentInformation() shipmentInformation}.
     *
     * @param shipmentInformation shipment information, or null
     * @return this builder
     */
    public Builder shipmentInformation(String shipmentInformation) {
      this.shipmentInformation = shipmentInformation;
      return this;
    }

    /**
     * Sets {@link DespatchAdvice#grossWeight() grossWeight}.
     *
     * @param grossWeight gross weight, or null
     * @return this builder
     */
    public Builder grossWeight(BigDecimal grossWeight) {
      this.grossWeight = grossWeight;
      return this;
    }

    /**
     * Sets {@link DespatchAdvice#grossWeightUnit() grossWeightUnit}.
     *
     * @param grossWeightUnit unit of the gross weight, e.g. KGM; or null
     * @return this builder
     */
    public Builder grossWeightUnit(UnitCode grossWeightUnit) {
      this.grossWeightUnit = grossWeightUnit;
      return this;
    }

    /**
     * Sets {@link DespatchAdvice#grossVolume() grossVolume}.
     *
     * @param grossVolume gross volume, or null
     * @return this builder
     */
    public Builder grossVolume(BigDecimal grossVolume) {
      this.grossVolume = grossVolume;
      return this;
    }

    /**
     * Sets {@link DespatchAdvice#grossVolumeUnit() grossVolumeUnit}.
     *
     * @param grossVolumeUnit unit of the gross volume, e.g. MTQ; or null
     * @return this builder
     */
    public Builder grossVolumeUnit(UnitCode grossVolumeUnit) {
      this.grossVolumeUnit = grossVolumeUnit;
      return this;
    }

    /**
     * Sets {@link DespatchAdvice#handlingUnitCount() handlingUnitCount}.
     *
     * @param handlingUnitCount number of transport handling units, e.g. pallets; or null
     * @return this builder
     */
    public Builder handlingUnitCount(BigDecimal handlingUnitCount) {
      this.handlingUnitCount = handlingUnitCount;
      return this;
    }

    /**
     * Sets {@link DespatchAdvice#transportModeCode() transportModeCode}.
     *
     * @param transportModeCode UN/ECE Rec. 19 transport mode, or null
     * @return this builder
     */
    public Builder transportModeCode(String transportModeCode) {
      this.transportModeCode = transportModeCode;
      return this;
    }

    /**
     * Sets {@link DespatchAdvice#trackingId() trackingId}.
     *
     * @param trackingId tracking number of the carrier, or null
     * @return this builder
     */
    public Builder trackingId(String trackingId) {
      this.trackingId = trackingId;
      return this;
    }

    /**
     * Sets {@link DespatchAdvice#estimatedDeliveryPeriod() estimatedDeliveryPeriod}.
     *
     * @param estimatedDeliveryPeriod estimated delivery period, or null
     * @return this builder
     */
    public Builder estimatedDeliveryPeriod(InvoicePeriod estimatedDeliveryPeriod) {
      this.estimatedDeliveryPeriod = estimatedDeliveryPeriod;
      return this;
    }

    /**
     * Sets {@link DespatchAdvice#actualDespatchDate() actualDespatchDate}.
     *
     * @param actualDespatchDate date the goods were despatched, or null
     * @return this builder
     */
    public Builder actualDespatchDate(LocalDate actualDespatchDate) {
      this.actualDespatchDate = actualDespatchDate;
      return this;
    }

    /**
     * Sets {@link DespatchAdvice#actualDeliveryDate() actualDeliveryDate}.
     *
     * @param actualDeliveryDate date the goods were delivered, or null; not in Peppol and CIDA
     * @return this builder
     */
    public Builder actualDeliveryDate(LocalDate actualDeliveryDate) {
      this.actualDeliveryDate = actualDeliveryDate;
      return this;
    }

    /**
     * Sets {@link DespatchAdvice#despatchAddress() despatchAddress}.
     *
     * @param despatchAddress address the goods were despatched from, or null
     * @return this builder
     */
    public Builder despatchAddress(Address despatchAddress) {
      this.despatchAddress = despatchAddress;
      return this;
    }

    /**
     * Adds a value to {@link DespatchAdvice#lines() lines}.
     *
     * @param value the despatch line to add
     * @return this builder
     */
    public Builder addLine(DespatchLine value) {
      this.lines.add(value);
      return this;
    }

    /**
     * Sets {@link DespatchAdvice#customizationId() customizationId}.
     *
     * @param customizationId customization identifier; Peppol BIS Despatch Advice 3 by default, {@link #ONE_LIEFERSCHEIN_CUSTOMIZATION} for 1Lieferschein
     * @return this builder
     */
    public Builder customizationId(String customizationId) {
      this.customizationId = customizationId;
      return this;
    }

    /**
     * Sets {@link DespatchAdvice#profileId() profileId}.
     *
     * @param profileId profile identifier; Peppol BIS Despatch Advice 3 by default, {@link #ONE_LIEFERSCHEIN_PROFILE} for 1Lieferschein
     * @return this builder
     */
    public Builder profileId(String profileId) {
      this.profileId = profileId;
      return this;
    }

    /**
     * Sets the customization and profile identifiers of 1Lieferschein instead of Peppol.
     *
     * @return this builder
     */
    public Builder oneLieferschein() {
      this.customizationId = ONE_LIEFERSCHEIN_CUSTOMIZATION;
      this.profileId       = ONE_LIEFERSCHEIN_PROFILE;
      return this;
    }

    /**
     * Constructs a new {@link DespatchAdvice} from the current values.
     *
     * @return the constructed value after the model checks succeed
     */
    public DespatchAdvice build() {
      return new DespatchAdvice(number,
                                issueDate,
                                note,
                                orderReferences,
                                additionalDocuments,
                                despatchSupplier,
                                deliveryCustomer,
                                buyer,
                                seller,
                                originator,
                                shipmentId,
                                shipmentInformation,
                                grossWeight,
                                grossWeightUnit,
                                grossVolume,
                                grossVolumeUnit,
                                handlingUnitCount,
                                transportModeCode,
                                trackingId,
                                estimatedDeliveryPeriod,
                                actualDespatchDate,
                                actualDeliveryDate,
                                despatchAddress,
                                lines,
                                customizationId,
                                profileId);
    }
  }
}

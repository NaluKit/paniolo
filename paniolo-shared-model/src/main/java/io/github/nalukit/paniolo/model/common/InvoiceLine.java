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
import io.github.nalukit.paniolo.model.common.value.UnitCode;
import io.github.nalukit.paniolo.model.extended.LineExtension;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Invoice line (BG-25) with price details (BG-29), line allowances and charges (BG-27, BG-28),
 * line period (BG-26) and item information (BG-31, BG-32).
 *
 * <p>The line net amount (BT-131) is quantity x net price / price base quantity (BT-149) plus line
 * charges minus line allowances. A stated amount replaces the calculation when the document shows
 * a net price rounded to cents; the difference must be explainable by that rounding.
 *
 * @param id                       line identifier (BT-126)
 * @param name                     item name (BT-153)
 * @param quantity                 invoiced quantity (BT-129); null only for an EXTENDED subtotal or
 *                                 information line without net price, together with the unit
 * @param unitCode                 unit of measure (BT-130); null together with the quantity
 * @param netUnitPrice             item net price (BT-146); null only for a line with sub-lines of
 *                                 the XRechnung extension, whose net amount is that of its sub-lines,
 *                                 or for an EXTENDED subtotal or information line
 * @param priceBaseQuantity       item price base quantity (BT-149)
 * @param tax                      VAT category and rate of the line (BG-30); null only for an
 *                                 EXTENDED subtotal (GROUP) or information line
 * @param statedNetAmount          stated line net amount (BT-131) or null to calculate it
 * @param note                     line note (BT-127)
 * @param objectId                 line object identifier (BT-128); required if the EXTENDED
 *                                 information names further object identifiers
 * @param objectSchemeId           scheme of the line object identifier (BT-128-1)
 * @param orderLineReference       referenced purchase order line (BT-132)
 * @param buyerAccountingReference buyer accounting reference of the line (BT-133)
 * @param period                   line period (BG-26)
 * @param adjustments              line allowances and charges (BG-27, BG-28)
 * @param grossPrice               item gross price (BT-148)
 * @param priceDiscount            item price discount (BT-147); net price = gross price - discount;
 *                                 with EXTENDED gross price adjustments their allowances minus charges
 * @param description              item description (BT-154)
 * @param sellerItemId             item seller's identifier (BT-155)
 * @param buyerItemId              item buyer's identifier (BT-156)
 * @param standardItemId           item standard identifier, e.g. GTIN (BT-157)
 * @param standardItemSchemeId     scheme of the standard identifier, e.g. "0160" for GTIN (BT-157-1)
 * @param classifications          item classification identifiers (BT-158)
 * @param originCountry            item country of origin (BT-159)
 * @param attributes               item attributes (BG-32)
 * @param extended                 line information of the Factur-X profile EXTENDED, or null
 * @param subLines                 sub invoice lines of the XRechnung extension (BG-DEX-01); their
 *                                 net amounts add up to the net amount of this line (BR-DEX-02)
 */
public record InvoiceLine(String id, String name, BigDecimal quantity, UnitCode unitCode, BigDecimal netUnitPrice, BigDecimal priceBaseQuantity, TaxCategory tax, BigDecimal statedNetAmount, String note, String objectId, String objectSchemeId,
                          String orderLineReference, String buyerAccountingReference, InvoicePeriod period, List<LineAdjustment> adjustments, BigDecimal grossPrice, BigDecimal priceDiscount, String description, String sellerItemId, String buyerItemId,
                          String standardItemId, String standardItemSchemeId, List<ItemClassification> classifications, Country originCountry, List<ItemAttribute> attributes, LineExtension extended, List<InvoiceLine> subLines) {
  /**
   * Half a cent: the largest error of a net price rounded to two decimals.
   */
  private static final BigDecimal HALF_CENT = new BigDecimal("0.005");

  /**
   * Applies the checks of the model and copies the lists.
   *
   * @throws NullPointerException     if a required value is null
   * @throws IllegalArgumentException if a value violates the checks of the model
   */
  public InvoiceLine {
    ModelChecks.required(id,
                         "line id");
    ModelChecks.required(name,
                         "item name");
    // BR-26: the item net price is required. A line with sub-lines of the XRechnung extension may
    // omit it, as BR-26 applies to invoice lines only: its amount is that of its sub-lines. EXTENDED
    // subtotal and information lines, which do not count, may omit it as well. Its sign depends on
    // the profile (BR-27), which the invoice checks.
    boolean structural = extended != null && extended.subtype() != null && !extended.subtype()
                                                                                   .counts();
    // Such a line without price may omit the quantity too, as the sub-invoice (GROUP) lines of a
    // French multi-vendor invoice do; quantity and unit come together.
    if (!structural || netUnitPrice != null || quantity != null || unitCode != null) {
      Objects.requireNonNull(unitCode,
                             "unitCode");
      Objects.requireNonNull(quantity,
                             "quantity");
    }
    if (netUnitPrice == null && !structural && (subLines == null || subLines.isEmpty())) {
      throw new NullPointerException("netUnitPrice");
    }
    if (ModelChecks.nonNegative(priceBaseQuantity,
                                "priceBaseQuantity")
                   .signum() == 0) {
      throw new IllegalArgumentException("priceBaseQuantity must be positive");
    }
    // BR-FXEXT-CO-04: EXTENDED subtotal (GROUP) and information lines need no VAT category; they
    // structure lines whose own categories count.
    if (tax == null && (extended == null || extended.subtype() == null || extended.subtype()
                                                                                .counts())) {
      throw new NullPointerException("tax");
    }
    if (objectSchemeId != null && objectId == null) {
      throw new IllegalArgumentException("Line " + id + ": objectSchemeId requires objectId");
    }
    // Further invoiced objects follow the one of the line, which a reader takes as BT-128.
    if (objectId == null && extended != null && !extended.furtherObjectIdentifiers()
                                                         .isEmpty()) {
      throw new IllegalArgumentException("Line " + id + ": further object identifiers require objectId (BT-128)");
    }
    adjustments     = adjustments == null ?
                      List.of() :
                      List.copyOf(adjustments);
    classifications = classifications == null ?
                      List.of() :
                      List.copyOf(classifications);
    attributes      = attributes == null ?
                      List.of() :
                      List.copyOf(attributes);
    subLines        = subLines == null ?
                      List.of() :
                      List.copyOf(subLines);
    // Empty EXTENDED information is the same as none, so readers and writers agree.
    if (extended != null && extended.isEmpty()) {
      extended = null;
    }
    if (extended != null && extended.accountingTypeCode() != null && buyerAccountingReference == null) {
      throw new IllegalArgumentException("Line " + id + ": an accounting type code requires the line accounting reference (BT-133)");
    }
    if (extended != null && !extended.additionalAccountingAccounts()
                                     .isEmpty() && buyerAccountingReference == null) {
      throw new IllegalArgumentException("Line " + id + ": further accounting accounts require the line accounting reference (BT-133)");
    }
    // EXTENDED states a line note by its text, its content code, or both (BR-FXEXT-01).
    if (extended != null && extended.noteSubjectCode() != null && note == null && extended.noteContentCode() == null) {
      throw new IllegalArgumentException("Line " + id + ": a note subject code requires a line note (BT-127) or a note content code");
    }
    // BR-28 (gross price not negative) depends on the profile as well; the invoice checks it.
    // A zero discount carries no information; UBL needs an amount whenever a gross price is given.
    if (priceDiscount != null && priceDiscount.signum() == 0) {
      priceDiscount = null;
    }
    if (priceDiscount != null && grossPrice == null) {
      throw new IllegalArgumentException("Line " + id + ": priceDiscount (BT-147) requires grossPrice (BT-148)");
    }
    if (extended != null && !extended.priceAdjustments()
                                     .isEmpty()) {
      if (grossPrice == null) {
        throw new IllegalArgumentException("Line " + id + ": gross price adjustments require grossPrice (BT-148)");
      }
      // The EXTENDED parts and the EN 16931 discount describe the same price reduction.
      BigDecimal stated = extended.priceAdjustmentDiscount();
      if (stated.compareTo(priceDiscount == null ?
                           BigDecimal.ZERO :
                           priceDiscount) != 0) {
        throw new IllegalArgumentException("Line " + id + ": priceDiscount (BT-147) " + (priceDiscount == null ?
                                                                                          "0" :
                                                                                          priceDiscount.toPlainString()) +
                                           " is not the allowances minus the charges of the gross price adjustments, " + stated.toPlainString());
      }
    }
    // BR-64: a standard identifier needs its scheme.
    if (standardItemId != null && (standardItemSchemeId == null || standardItemSchemeId.isBlank())) {
      throw new IllegalArgumentException("Line " + id + ": standardItemId requires standardItemSchemeId");
    }
    if (statedNetAmount != null) {
      statedNetAmount = ModelChecks.money(statedNetAmount);
    }
    // Without net price the sub-lines explain the stated amount (BR-DEX-02 below).
    if (statedNetAmount != null && netUnitPrice != null) {
      BigDecimal exact = exactNetAmount(quantity,
                                        netUnitPrice,
                                        priceBaseQuantity,
                                        adjustments);
      BigDecimal tolerance = quantity.abs()
                                     .divide(priceBaseQuantity,
                                             10,
                                             RoundingMode.HALF_UP)
                                     .multiply(HALF_CENT)
                                     .add(HALF_CENT);
      if (statedNetAmount.subtract(exact)
                         .abs()
                         .compareTo(tolerance) > 0) {
        throw new IllegalArgumentException("Line " + id + ": stated net amount " + statedNetAmount.toPlainString() + " differs from quantity x net price by more than the rounding of the net price explains");
      }
    }
    if (!subLines.isEmpty()) {
      // BR-DEX-02: the net amount of a line is the sum of the net amounts of its direct sub-lines.
      BigDecimal sum = subLineTotal(subLines);
      BigDecimal net = statedNetAmount != null ?
                       statedNetAmount :
                       netUnitPrice == null ?
                       sum :
                       ModelChecks.money(exactNetAmount(quantity,
                                                        netUnitPrice,
                                                        priceBaseQuantity,
                                                        adjustments));
      if (net.compareTo(sum) != 0) {
        throw new IllegalArgumentException("Line " + id + ": net amount " + net.toPlainString() + " differs from the sum of its sub-lines " + sum.toPlainString() + " (BR-DEX-02)");
      }
      if (extended != null) {
        throw new IllegalArgumentException("Line " + id + ": sub-lines of the XRechnung extension and EXTENDED line information cannot be combined");
      }
    }
  }

  /**
   * A line with the required fields and a price base quantity.
   *
   * @param id                line identifier (BT-126)
   * @param name              item name (BT-153)
   * @param quantity          invoiced quantity (BT-129)
   * @param unitCode          unit of the quantity (BT-130)
   * @param netUnitPrice      item net price (BT-146)
   * @param priceBaseQuantity item price base quantity (BT-149)
   * @param tax               VAT category and rate (BT-151 / BT-152)
   * @throws IllegalArgumentException if a value violates the checks of the model
   * @throws NullPointerException     if a required value is null
   */
  public InvoiceLine(String id,
                     String name,
                     BigDecimal quantity,
                     UnitCode unitCode,
                     BigDecimal netUnitPrice,
                     BigDecimal priceBaseQuantity,
                     TaxCategory tax) {
    this(id,
         name,
         quantity,
         unitCode,
         netUnitPrice,
         priceBaseQuantity,
         tax,
         null,
         null,
         null,
         null,
         null,
         null,
         null,
         List.of(),
         null,
         null,
         null,
         null,
         null,
         null,
         null,
         List.of(),
         null,
         List.of(),
         null,
         List.of());
  }

  /**
   * A line with the required fields and price base quantity 1.
   *
   * @param id           line identifier (BT-126)
   * @param name         item name (BT-153)
   * @param quantity     invoiced quantity (BT-129)
   * @param unitCode     unit of the quantity (BT-130)
   * @param netUnitPrice item net price (BT-146)
   * @param tax          VAT category and rate (BT-151 / BT-152)
   * @throws IllegalArgumentException if a value violates the checks of the model
   * @throws NullPointerException     if a required value is null
   */
  public InvoiceLine(String id,
                     String name,
                     BigDecimal quantity,
                     UnitCode unitCode,
                     BigDecimal netUnitPrice,
                     TaxCategory tax) {
    this(id,
         name,
         quantity,
         unitCode,
         netUnitPrice,
         BigDecimal.ONE,
         tax);
  }

  /**
   * Starts named construction of a {@link InvoiceLine} using the builder defaults.
   *
   * @return a new builder; not shared with other calls
   */
  public static Builder create() {
    return new Builder();
  }

  private static BigDecimal subLineTotal(List<InvoiceLine> subLines) {
    return subLines.stream()
                   .map(InvoiceLine::netAmount)
                   .reduce(BigDecimal.ZERO,
                           BigDecimal::add);
  }

  private static BigDecimal exactNetAmount(BigDecimal quantity,
                                           BigDecimal netUnitPrice,
                                           BigDecimal priceBaseQuantity,
                                           List<LineAdjustment> adjustments) {
    BigDecimal amount = quantity.multiply(netUnitPrice)
                                .divide(priceBaseQuantity,
                                        10,
                                        RoundingMode.HALF_UP);
    for (LineAdjustment adjustment : adjustments) {
      amount = adjustment.charge() ?
               amount.add(adjustment.amount()) :
               amount.subtract(adjustment.amount());
    }
    return amount;
  }

  /**
   * Line net amount (BT-131): the stated amount if given, otherwise quantity x net price / base
   * quantity plus line charges minus line allowances, rounded to cents.
   *
   * @return the line net amount
   */
  public BigDecimal netAmount() {
    if (statedNetAmount != null) {
      return statedNetAmount;
    }
    if (netUnitPrice == null) {
      // Without price and stated amount an EXTENDED information line states no amount.
      return subLineTotal(subLines);
    }
    return ModelChecks.money(exactNetAmount(quantity,
                                            netUnitPrice,
                                            priceBaseQuantity,
                                            adjustments));
  }

  /**
   * Sum of the line allowances (BG-27).
   *
   * @return the sum of the line allowances
   */
  public BigDecimal allowanceTotal() {
    return sum(false);
  }

  /**
   * Sum of the line charges (BG-28).
   *
   * @return the sum of the line charges
   */
  public BigDecimal chargeTotal() {
    return sum(true);
  }

  /**
   * Returns this line with the given net amount as stated amount, or this line if the amount equals
   * the calculated one or is null. Used by readers to keep amounts from the document.
   *
   * @param netAmount the stated line net amount (BT-131), or null
   * @return the line with the stated amount, or this line
   * @throws IllegalArgumentException if quantity x net price does not explain the amount
   */
  public InvoiceLine withNetAmount(BigDecimal netAmount) {
    if (netAmount == null || netAmount.compareTo(netAmount()) == 0) {
      return this;
    }
    return toBuilder().netAmount(netAmount)
                      .build();
  }

  /**
   * Returns a builder initialised with all values of this line.
   *
   * @return a new builder; not shared with other calls
   */
  public Builder toBuilder() {
    var builder = new Builder();
    builder.id                       = id;
    builder.name                     = name;
    builder.quantity                 = quantity;
    builder.unitCode                 = unitCode;
    builder.netUnitPrice             = netUnitPrice;
    builder.priceBaseQuantity        = priceBaseQuantity;
    builder.tax                      = tax;
    builder.statedNetAmount          = statedNetAmount;
    builder.note                     = note;
    builder.objectId                 = objectId;
    builder.objectSchemeId           = objectSchemeId;
    builder.orderLineReference       = orderLineReference;
    builder.buyerAccountingReference = buyerAccountingReference;
    builder.period                   = period;
    builder.adjustments              = new ArrayList<>(adjustments);
    builder.grossPrice               = grossPrice;
    builder.priceDiscount            = priceDiscount;
    builder.description              = description;
    builder.sellerItemId             = sellerItemId;
    builder.buyerItemId              = buyerItemId;
    builder.standardItemId           = standardItemId;
    builder.standardItemSchemeId     = standardItemSchemeId;
    builder.classifications          = new ArrayList<>(classifications);
    builder.originCountry            = originCountry;
    builder.attributes               = new ArrayList<>(attributes);
    builder.extended                 = extended;
    builder.subLines                 = new ArrayList<>(subLines);
    return builder;
  }

  private BigDecimal sum(boolean charge) {
    return ModelChecks.money(adjustments.stream()
                                        .filter(adjustment -> adjustment.charge() == charge)
                                        .map(LineAdjustment::amount)
                                        .reduce(BigDecimal.ZERO,
                                                BigDecimal::add));
  }

  /**
   * Named construction of an immutable invoice line.
   *
   * <p>Mutable construction state for {@link InvoiceLine}. Use a separate builder per operation;
   * builders are not thread-safe. {@link #build()} constructs a new immutable value and applies
   * the checks of the model. XML schema and business rule validation are separate operations.
   */
  public static final class Builder {
    private String                   id;
    private String                   name;
    private BigDecimal               quantity;
    private UnitCode                 unitCode;
    private BigDecimal               netUnitPrice;
    private BigDecimal               priceBaseQuantity = BigDecimal.ONE;
    private TaxCategory              tax;
    private BigDecimal               statedNetAmount;
    private String                   note;
    private String                   objectId;
    private String                   objectSchemeId;
    private String                   orderLineReference;
    private String                   buyerAccountingReference;
    private InvoicePeriod            period;
    private List<LineAdjustment>     adjustments       = new ArrayList<>();
    private BigDecimal               grossPrice;
    private BigDecimal               priceDiscount;
    private String                   description;
    private String                   sellerItemId;
    private String                   buyerItemId;
    private String                   standardItemId;
    private String                   standardItemSchemeId;
    private List<ItemClassification> classifications   = new ArrayList<>();
    private Country                  originCountry;
    private List<ItemAttribute>      attributes        = new ArrayList<>();
    private LineExtension            extended;
    private List<InvoiceLine>        subLines          = new ArrayList<>();

    /**
     * Creates a builder with the defaults of the model; usually obtained with {@link InvoiceLine#create()}.
     */
    public Builder() {
    }

    /**
     * Sets {@link InvoiceLine#id() id}.
     *
     * @param id line identifier (BT-126)
     * @return this builder
     */
    public Builder id(String id) {
      this.id = id;
      return this;
    }

    /**
     * Sets {@link InvoiceLine#name() name}.
     *
     * @param name item name (BT-153)
     * @return this builder
     */
    public Builder name(String name) {
      this.name = name;
      return this;
    }

    /**
     * Sets {@link InvoiceLine#quantity() quantity}.
     *
     * @param quantity invoiced quantity (BT-129); null only for an EXTENDED subtotal or information
     *                 line without net price, together with the unit
     * @return this builder
     */
    public Builder quantity(BigDecimal quantity) {
      this.quantity = quantity;
      return this;
    }

    /**
     * Sets {@link InvoiceLine#unitCode() unitCode}.
     *
     * @param unitCode unit of measure (BT-130); null together with the quantity
     * @return this builder
     */
    public Builder unitCode(UnitCode unitCode) {
      this.unitCode = unitCode;
      return this;
    }

    /**
     * Sets the unit by its UN/ECE code, e.g. "HUR".
     *
     * @param unitCode unit of measure (BT-130); null together with the quantity
     * @return this builder
     * @throws IllegalArgumentException if EN 16931 does not permit the code
     */
    public Builder unitCode(String unitCode) {
      this.unitCode = unitCode == null ?
                      null :
                      java.util.Optional.ofNullable(UnitCode.of(unitCode))
                                        .orElseThrow(() -> new IllegalArgumentException("Unknown unit code: " + unitCode));
      return this;
    }

    /**
     * Sets {@link InvoiceLine#netUnitPrice() netUnitPrice}.
     *
     * @param netUnitPrice item net price (BT-146); null only for a line with sub-lines of the XRechnung
     *                     extension, whose net amount is that of its sub-lines, or for an EXTENDED
     *                     subtotal or information line
     * @return this builder
     */
    public Builder netUnitPrice(BigDecimal netUnitPrice) {
      this.netUnitPrice = netUnitPrice;
      return this;
    }

    /**
     * Sets {@link InvoiceLine#priceBaseQuantity() priceBaseQuantity}.
     *
     * @param priceBaseQuantity item price base quantity (BT-149)
     * @return this builder
     */
    public Builder priceBaseQuantity(BigDecimal priceBaseQuantity) {
      this.priceBaseQuantity = priceBaseQuantity;
      return this;
    }

    /**
     * Sets {@link InvoiceLine#tax() tax}.
     *
     * @param tax VAT category and rate of the line (BG-30); null only for an EXTENDED subtotal (GROUP)
     *            or information line
     * @return this builder
     */
    public Builder tax(TaxCategory tax) {
      this.tax = tax;
      return this;
    }

    /**
     * Stated line net amount (BT-131) when the net price is shown rounded; see {@link InvoiceLine}.
     *
     * @param netAmount the stated line net amount, or null
     * @return this builder
     */
    public Builder netAmount(BigDecimal netAmount) {
      this.statedNetAmount = netAmount;
      return this;
    }

    /**
     * Line note (BT-127).
     *
     * @param note line note (BT-127)
     * @return this builder
     */
    public Builder note(String note) {
      this.note = note;
      return this;
    }

    /**
     * Line object identifier (BT-128), e.g. a meter number, with its scheme (BT-128-1, UNTDID 1153).
     *
     * @param objectId the line object identifier
     * @param schemeId the scheme identifier, or null
     * @return this builder
     */
    public Builder objectId(String objectId,
                            String schemeId) {
      this.objectId       = objectId;
      this.objectSchemeId = schemeId;
      return this;
    }

    /**
     * Referenced purchase order line (BT-132).
     *
     * @param orderLineReference referenced purchase order line (BT-132)
     * @return this builder
     */
    public Builder orderLineReference(String orderLineReference) {
      this.orderLineReference = orderLineReference;
      return this;
    }

    /**
     * Buyer accounting reference of the line (BT-133).
     *
     * @param buyerAccountingReference buyer accounting reference of the line (BT-133)
     * @return this builder
     */
    public Builder buyerAccountingReference(String buyerAccountingReference) {
      this.buyerAccountingReference = buyerAccountingReference;
      return this;
    }

    /**
     * Line period (BG-26).
     *
     * @param period line period (BG-26)
     * @return this builder
     */
    public Builder period(InvoicePeriod period) {
      this.period = period;
      return this;
    }

    /**
     * Sets {@link InvoiceLine#adjustments() adjustments}.
     *
     * @param adjustments line allowances and charges (BG-27, BG-28)
     * @return this builder
     */
    public Builder adjustments(List<LineAdjustment> adjustments) {
      this.adjustments = new ArrayList<>(adjustments);
      return this;
    }

    /**
     * Adds a line allowance (BG-27) or charge (BG-28).
     *
     * @param adjustment the allowance or charge to add
     * @return this builder
     */
    public Builder addAdjustment(LineAdjustment adjustment) {
      this.adjustments.add(adjustment);
      return this;
    }

    /**
     * Item gross price (BT-148).
     *
     * @param grossPrice item gross price (BT-148)
     * @return this builder
     */
    public Builder grossPrice(BigDecimal grossPrice) {
      this.grossPrice = grossPrice;
      return this;
    }

    /**
     * Item price discount (BT-147); requires the gross price.
     *
     * @param priceDiscount item price discount (BT-147); net price = gross price - discount; with EXTENDED gross price adjustments their allowances minus charges
     * @return this builder
     */
    public Builder priceDiscount(BigDecimal priceDiscount) {
      this.priceDiscount = priceDiscount;
      return this;
    }

    /**
     * Item description (BT-154).
     *
     * @param description item description (BT-154)
     * @return this builder
     */
    public Builder description(String description) {
      this.description = description;
      return this;
    }

    /**
     * Item seller's identifier (BT-155).
     *
     * @param sellerItemId item seller's identifier (BT-155)
     * @return this builder
     */
    public Builder sellerItemId(String sellerItemId) {
      this.sellerItemId = sellerItemId;
      return this;
    }

    /**
     * Item buyer's identifier (BT-156).
     *
     * @param buyerItemId item buyer's identifier (BT-156)
     * @return this builder
     */
    public Builder buyerItemId(String buyerItemId) {
      this.buyerItemId = buyerItemId;
      return this;
    }

    /**
     * Item standard identifier (BT-157) with its ISO 6523 scheme (BT-157-1), e.g. "0160" for GTIN.
     *
     * @param standardItemId the item standard identifier
     * @param schemeId       the ISO 6523 scheme
     * @return this builder
     */
    public Builder standardItemId(String standardItemId,
                                  String schemeId) {
      this.standardItemId       = standardItemId;
      this.standardItemSchemeId = schemeId;
      return this;
    }

    /**
     * Sets {@link InvoiceLine#classifications() classifications}.
     *
     * @param classifications item classification identifiers (BT-158)
     * @return this builder
     */
    public Builder classifications(List<ItemClassification> classifications) {
      this.classifications = new ArrayList<>(classifications);
      return this;
    }

    /**
     * Adds an item classification identifier (BT-158).
     *
     * @param classification the classification to add
     * @return this builder
     */
    public Builder addClassification(ItemClassification classification) {
      this.classifications.add(classification);
      return this;
    }

    /**
     * Item country of origin (BT-159).
     *
     * @param originCountry item country of origin (BT-159)
     * @return this builder
     */
    public Builder originCountry(Country originCountry) {
      this.originCountry = originCountry;
      return this;
    }

    /**
     * Sets {@link InvoiceLine#attributes() attributes}.
     *
     * @param attributes item attributes (BG-32)
     * @return this builder
     */
    public Builder attributes(List<ItemAttribute> attributes) {
      this.attributes = new ArrayList<>(attributes);
      return this;
    }

    /**
     * Adds an item attribute (BG-32).
     *
     * @param name  the attribute name (BT-160)
     * @param value the attribute value (BT-161)
     * @return this builder
     */
    public Builder addAttribute(String name,
                                String value) {
      this.attributes.add(ItemAttribute.of(name,
                                           value));
      return this;
    }

    /**
     * Line information of the Factur-X / ZUGFeRD profile EXTENDED, e.g. sub-lines or product details.
     *
     * @param extended line information of the Factur-X profile EXTENDED, or null
     * @return this builder
     */
    public Builder extended(LineExtension extended) {
      this.extended = extended;
      return this;
    }

    /**
     * Adds a sub invoice line of the XRechnung extension (BG-DEX-01), written as UBL
     * {@code cac:SubInvoiceLine} with the specification identifier
     * {@link ProcessControl#XRECHNUNG_3_EXTENSION}.
     *
     * @param subLine the sub invoice line to add
     * @return this builder
     */
    public Builder addSubLine(InvoiceLine subLine) {
      this.subLines.add(subLine);
      return this;
    }

    /**
     * Constructs a new {@link InvoiceLine} from the current values.
     *
     * @return the constructed value after the model checks succeed
     */
    public InvoiceLine build() {
      return new InvoiceLine(id,
                             name,
                             quantity,
                             unitCode,
                             netUnitPrice,
                             priceBaseQuantity,
                             tax,
                             statedNetAmount,
                             note,
                             objectId,
                             objectSchemeId,
                             orderLineReference,
                             buyerAccountingReference,
                             period,
                             adjustments,
                             grossPrice,
                             priceDiscount,
                             description,
                             sellerItemId,
                             buyerItemId,
                             standardItemId,
                             standardItemSchemeId,
                             classifications,
                             originCountry,
                             attributes,
                             extended,
                             subLines);
    }
  }
}

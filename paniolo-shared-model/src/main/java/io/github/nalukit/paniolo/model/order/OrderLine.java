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
package io.github.nalukit.paniolo.model.order;

import io.github.nalukit.paniolo.model.common.ItemAttribute;
import io.github.nalukit.paniolo.model.common.ItemClassification;
import io.github.nalukit.paniolo.model.common.LineAdjustment;
import io.github.nalukit.paniolo.model.common.TaxCategory;
import io.github.nalukit.paniolo.model.common.validation.ModelChecks;
import io.github.nalukit.paniolo.model.common.value.Country;
import io.github.nalukit.paniolo.model.common.value.UnitCode;
import io.github.nalukit.paniolo.model.extended.DocumentReference;
import io.github.nalukit.paniolo.model.extended.ProductCharacteristic;
import io.github.nalukit.paniolo.model.extended.ProductInstance;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Line of an Order-X order, order change, or order response. The line net amount is quantity x
 * net price / price base quantity plus line charges minus line allowances, rounded to cents.
 * Fields marked COMFORT are rejected by the writer for the profile BASIC.
 *
 * @param id                       line identifier
 * @param name                     item name, or null if an item identifier is given
 * @param quantity                 requested quantity
 * @param agreedQuantity           quantity agreed by the seller, e.g. in an order response; or null.
 *                                 If present, the line amount is calculated from it, as in Order-X
 *                                 responses
 * @param unitCode                 unit of the quantities
 * @param netUnitPrice             net price per price base quantity
 * @param priceBaseQuantity        quantity the net price refers to; 1 by default
 * @param grossPrice               gross price (COMFORT), or null
 * @param tax                      VAT category and rate without exemption (COMFORT), or null
 * @param adjustments              line allowances and charges (COMFORT)
 * @param note                     line note, or null
 * @param statusCode               UNTDID 1229 line status, e.g. in an order response; or null
 * @param partialDeliveryAllowed   whether partial deliveries are allowed, or null
 * @param sellerItemId             seller's item identifier, or null
 * @param buyerItemId              buyer's item identifier, or null
 * @param standardItemId           standard item identifier, e.g. a GTIN; or null
 * @param standardItemSchemeId     scheme of the standard item identifier, or null
 * @param description              item description (COMFORT), or null
 * @param requestedDeliveryDate    requested delivery date of the line (COMFORT), or null
 * @param buyerAccountingReference buyer accounting reference of the line (COMFORT), or null
 * @param priceDiscount            allowance on the gross price (COMFORT), or null
 * @param substitutedProduct       product offered instead (COMFORT), or null
 * @param batchId                  batch number (COMFORT), or null
 * @param brandName                brand name (COMFORT), or null
 * @param attributes               item attributes as name and value (COMFORT)
 * @param characteristics          characteristics with type code and text value (COMFORT)
 * @param classifications          item classifications with optional class name (COMFORT)
 * @param instances                product instances with batch or serial number (COMFORT)
 * @param packagingTypeCode        UN/ECE Rec. 21 package type (COMFORT), or null
 * @param originCountry            country of origin (COMFORT), or null
 * @param buyerOrderReference      buyer's order line, e.g. in an order response; or null
 * @param quotationReference       quotation line (COMFORT), or null
 * @param catalogueReference       catalogue (COMFORT), or null
 * @param blanketOrderReference    blanket order line, or null
 * @param packageQuantity          number of packages (COMFORT), or null
 * @param perPackageUnitQuantity   quantity per package (COMFORT), or null
 * @param requestedDespatchDate    requested despatch date of the line (COMFORT), or null
 * @param details                  further Order-X line information, or null
 */
public record OrderLine(String id, String name, BigDecimal quantity, BigDecimal agreedQuantity, UnitCode unitCode, BigDecimal netUnitPrice, BigDecimal priceBaseQuantity, BigDecimal grossPrice, TaxCategory tax, List<LineAdjustment> adjustments, String note,
                        String statusCode, Boolean partialDeliveryAllowed, String sellerItemId, String buyerItemId, String standardItemId, String standardItemSchemeId, String description, LocalDate requestedDeliveryDate, String buyerAccountingReference,
                        BigDecimal priceDiscount, SubstitutedProduct substitutedProduct, String batchId, String brandName, List<ItemAttribute> attributes, List<ProductCharacteristic> characteristics, List<ItemClassification> classifications,
                        List<ProductInstance> instances, String packagingTypeCode, Country originCountry, DocumentReference buyerOrderReference, DocumentReference quotationReference, DocumentReference catalogueReference,
                        DocumentReference blanketOrderReference, BigDecimal packageQuantity, BigDecimal perPackageUnitQuantity, LocalDate requestedDespatchDate, OrderLineDetails details) {
  /**
   * Applies the checks of the model and copies the lists.
   *
   * @throws NullPointerException     if a required value is null
   * @throws IllegalArgumentException if a value violates the checks of the model
   */
  public OrderLine {
    ModelChecks.required(id,
                         "line id");
    Objects.requireNonNull(quantity,
                           "quantity");
    Objects.requireNonNull(unitCode,
                           "unitCode");
    ModelChecks.nonNegative(netUnitPrice,
                            "netUnitPrice");
    if (ModelChecks.nonNegative(priceBaseQuantity,
                                "priceBaseQuantity")
                   .signum() == 0) {
      throw new IllegalArgumentException("priceBaseQuantity must be positive");
    }
    // A line of a response may name only the product offered instead.
    if (name == null && sellerItemId == null && buyerItemId == null && standardItemId == null && substitutedProduct == null) {
      throw new IllegalArgumentException("Line " + id + ": an order line needs an item name, an identifier, or a substituted product");
    }
    if (standardItemId != null && (standardItemSchemeId == null || standardItemSchemeId.isBlank())) {
      throw new IllegalArgumentException("Line " + id + ": standardItemId requires standardItemSchemeId");
    }
    if (grossPrice != null) {
      ModelChecks.nonNegative(grossPrice,
                              "grossPrice");
    }
    // Order-X knows VAT category and rate only.
    if (tax != null && (tax.exemptionReason() != null || tax.exemptionReasonCode() != null)) {
      throw new IllegalArgumentException("Line " + id + ": Order-X has no VAT exemption reason");
    }
    if (priceDiscount != null && grossPrice == null) {
      throw new IllegalArgumentException("Line " + id + ": priceDiscount requires grossPrice");
    }
    // Order-X characteristics carry a text value, no measure.
    if (characteristics != null &&
        characteristics.stream()
                       .anyMatch(characteristic -> characteristic.measure() != null)) {
      throw new IllegalArgumentException("Line " + id + ": Order-X product characteristics have no measured value");
    }
    for (DocumentReference reference : new DocumentReference[] { buyerOrderReference,
                                                                 quotationReference,
                                                                 catalogueReference,
                                                                 blanketOrderReference }) {
      if (reference != null && reference.issueDate() != null) {
        throw new IllegalArgumentException("Line " + id + ": Order-X references carry no date: " + reference.id());
      }
    }
    if (details != null && details.isEmpty()) {
      details = null;
    }
    if (details != null) {
      if (details.noteSubjectCode() != null && note == null) {
        throw new IllegalArgumentException("Line " + id + ": a note subject code requires the note");
      }
      if (!details.priceAdjustments()
                  .isEmpty() && (grossPrice == null || priceDiscount != null)) {
        throw new IllegalArgumentException("Line " + id + ": price adjustments require grossPrice and replace priceDiscount");
      }
      if (details.packageQuantityUnit() != null && packageQuantity == null) {
        throw new IllegalArgumentException("Line " + id + ": a package quantity unit requires the package quantity");
      }
    }
    adjustments     = adjustments == null ?
                      List.of() :
                      List.copyOf(adjustments);
    attributes      = attributes == null ?
                      List.of() :
                      List.copyOf(attributes);
    characteristics = characteristics == null ?
                      List.of() :
                      List.copyOf(characteristics);
    classifications = classifications == null ?
                      List.of() :
                      List.copyOf(classifications);
    instances       = instances == null ?
                      List.of() :
                      List.copyOf(instances);
  }

  /**
   * Starts named construction of a {@link OrderLine} using the builder defaults.
   *
   * @return a new builder; not shared with other calls
   */
  public static Builder create() {
    return new Builder();
  }

  /**
   * Line net amount: quantity (the agreed quantity if present) x net price / price base quantity
   * plus line charges minus line allowances, rounded to cents.
   *
   * @return the line net amount
   */
  public BigDecimal netAmount() {
    // A response states the amount of the agreed quantity.
    BigDecimal amount = (agreedQuantity == null ?
                         quantity :
                         agreedQuantity).multiply(netUnitPrice)
                                        .divide(priceBaseQuantity,
                                                10,
                                                RoundingMode.HALF_UP);
    for (LineAdjustment adjustment : adjustments) {
      amount = adjustment.charge() ?
               amount.add(adjustment.amount()) :
               amount.subtract(adjustment.amount());
    }
    return ModelChecks.money(amount);
  }

  /**
   * Named construction of an immutable order line.
   *
   * <p>Mutable construction state for {@link OrderLine}. Use a separate builder per operation;
   * builders are not thread-safe. {@link #build()} constructs a new immutable value and applies
   * the checks of the model. XML schema and business rule validation are separate operations.
   */
  public static final class Builder {
    private String                      id;
    private String                      name;
    private BigDecimal                  quantity;
    private BigDecimal                  agreedQuantity;
    private UnitCode                    unitCode;
    private BigDecimal                  netUnitPrice;
    private BigDecimal                  priceBaseQuantity = BigDecimal.ONE;
    private BigDecimal                  grossPrice;
    private TaxCategory                 tax;
    private List<LineAdjustment>        adjustments       = new ArrayList<>();
    private String                      note;
    private String                      statusCode;
    private Boolean                     partialDeliveryAllowed;
    private String                      sellerItemId;
    private String                      buyerItemId;
    private String                      standardItemId;
    private String                      standardItemSchemeId;
    private String                      description;
    private LocalDate                   requestedDeliveryDate;
    private String                      buyerAccountingReference;
    private BigDecimal                  priceDiscount;
    private SubstitutedProduct          substitutedProduct;
    private String                      batchId;
    private String                      brandName;
    private List<ItemAttribute>         attributes        = new ArrayList<>();
    private List<ProductCharacteristic> characteristics   = new ArrayList<>();
    private List<ItemClassification>    classifications   = new ArrayList<>();
    private List<ProductInstance>       instances         = new ArrayList<>();
    private String                      packagingTypeCode;
    private Country                     originCountry;
    private DocumentReference           buyerOrderReference;
    private DocumentReference           quotationReference;
    private DocumentReference           catalogueReference;
    private DocumentReference           blanketOrderReference;
    private BigDecimal                  packageQuantity;
    private BigDecimal                  perPackageUnitQuantity;
    private LocalDate                   requestedDespatchDate;
    private OrderLineDetails            details;

    /**
     * Creates a builder with the defaults of the model; usually obtained with {@link OrderLine#create()}.
     */
    public Builder() {
    }

    /**
     * Sets {@link OrderLine#id() id}.
     *
     * @param id line identifier
     * @return this builder
     */
    public Builder id(String id) {
      this.id = id;
      return this;
    }

    /**
     * Sets {@link OrderLine#name() name}.
     *
     * @param name item name, or null if an item identifier is given
     * @return this builder
     */
    public Builder name(String name) {
      this.name = name;
      return this;
    }

    /**
     * Sets {@link OrderLine#quantity() quantity}.
     *
     * @param quantity requested quantity
     * @return this builder
     */
    public Builder quantity(BigDecimal quantity) {
      this.quantity = quantity;
      return this;
    }

    /**
     * Sets {@link OrderLine#agreedQuantity() agreedQuantity}.
     *
     * @param agreedQuantity quantity agreed by the seller, e.g. in an order response; or null. If
     *                       present, the line amount is calculated from it, as in Order-X responses
     * @return this builder
     */
    public Builder agreedQuantity(BigDecimal agreedQuantity) {
      this.agreedQuantity = agreedQuantity;
      return this;
    }

    /**
     * Sets {@link OrderLine#unitCode() unitCode}.
     *
     * @param unitCode unit of the quantities
     * @return this builder
     */
    public Builder unitCode(UnitCode unitCode) {
      this.unitCode = unitCode;
      return this;
    }

    /**
     * Sets {@link OrderLine#unitCode() unitCode}.
     *
     * @param unitCode unit of the quantities
     * @return this builder
     */
    public Builder unitCode(String unitCode) {
      this.unitCode = UnitCode.of(unitCode);
      if (this.unitCode == null) {
        throw new IllegalArgumentException("Unknown unit code: " + unitCode);
      }
      return this;
    }

    /**
     * Sets {@link OrderLine#netUnitPrice() netUnitPrice}.
     *
     * @param netUnitPrice net price per price base quantity
     * @return this builder
     */
    public Builder netUnitPrice(BigDecimal netUnitPrice) {
      this.netUnitPrice = netUnitPrice;
      return this;
    }

    /**
     * Sets {@link OrderLine#priceBaseQuantity() priceBaseQuantity}.
     *
     * @param priceBaseQuantity quantity the net price refers to; 1 by default
     * @return this builder
     */
    public Builder priceBaseQuantity(BigDecimal priceBaseQuantity) {
      this.priceBaseQuantity = priceBaseQuantity;
      return this;
    }

    /**
     * Sets {@link OrderLine#grossPrice() grossPrice}.
     *
     * @param grossPrice gross price (COMFORT), or null
     * @return this builder
     */
    public Builder grossPrice(BigDecimal grossPrice) {
      this.grossPrice = grossPrice;
      return this;
    }

    /**
     * Sets {@link OrderLine#tax() tax}.
     *
     * @param tax VAT category and rate without exemption (COMFORT), or null
     * @return this builder
     */
    public Builder tax(TaxCategory tax) {
      this.tax = tax;
      return this;
    }

    /**
     * Adds a value to {@link OrderLine#adjustments() adjustments}.
     *
     * @param value the line adjustment to add
     * @return this builder
     */
    public Builder addAdjustment(LineAdjustment value) {
      this.adjustments.add(value);
      return this;
    }

    /**
     * Sets {@link OrderLine#note() note}.
     *
     * @param note line note, or null
     * @return this builder
     */
    public Builder note(String note) {
      this.note = note;
      return this;
    }

    /**
     * Sets {@link OrderLine#statusCode() statusCode}.
     *
     * @param statusCode UNTDID 1229 line status, e.g. in an order response; or null
     * @return this builder
     */
    public Builder statusCode(String statusCode) {
      this.statusCode = statusCode;
      return this;
    }

    /**
     * Sets {@link OrderLine#partialDeliveryAllowed() partialDeliveryAllowed}.
     *
     * @param partialDeliveryAllowed whether partial deliveries are allowed, or null
     * @return this builder
     */
    public Builder partialDeliveryAllowed(Boolean partialDeliveryAllowed) {
      this.partialDeliveryAllowed = partialDeliveryAllowed;
      return this;
    }

    /**
     * Sets {@link OrderLine#sellerItemId() sellerItemId}.
     *
     * @param sellerItemId seller's item identifier, or null
     * @return this builder
     */
    public Builder sellerItemId(String sellerItemId) {
      this.sellerItemId = sellerItemId;
      return this;
    }

    /**
     * Sets {@link OrderLine#buyerItemId() buyerItemId}.
     *
     * @param buyerItemId buyer's item identifier, or null
     * @return this builder
     */
    public Builder buyerItemId(String buyerItemId) {
      this.buyerItemId = buyerItemId;
      return this;
    }

    /**
     * Sets {@link OrderLine#standardItemId() standardItemId}.
     *
     * @param standardItemId standard item identifier, e.g. a GTIN; or null
     * @return this builder
     */
    public Builder standardItemId(String standardItemId) {
      this.standardItemId = standardItemId;
      return this;
    }

    /**
     * Standard item identifier with its ISO 6523 scheme, e.g. "0160" for GTIN.
     *
     * @param standardItemId the standard item identifier
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
     * Sets {@link OrderLine#description() description}.
     *
     * @param description item description (COMFORT), or null
     * @return this builder
     */
    public Builder description(String description) {
      this.description = description;
      return this;
    }

    /**
     * Sets {@link OrderLine#requestedDeliveryDate() requestedDeliveryDate}.
     *
     * @param requestedDeliveryDate requested delivery date of the line (COMFORT), or null
     * @return this builder
     */
    public Builder requestedDeliveryDate(LocalDate requestedDeliveryDate) {
      this.requestedDeliveryDate = requestedDeliveryDate;
      return this;
    }

    /**
     * Sets {@link OrderLine#buyerAccountingReference() buyerAccountingReference}.
     *
     * @param buyerAccountingReference buyer accounting reference of the line (COMFORT), or null
     * @return this builder
     */
    public Builder buyerAccountingReference(String buyerAccountingReference) {
      this.buyerAccountingReference = buyerAccountingReference;
      return this;
    }

    /**
     * Sets {@link OrderLine#priceDiscount() priceDiscount}.
     *
     * @param priceDiscount allowance on the gross price (COMFORT), or null
     * @return this builder
     */
    public Builder priceDiscount(BigDecimal priceDiscount) {
      this.priceDiscount = priceDiscount;
      return this;
    }

    /**
     * Sets {@link OrderLine#substitutedProduct() substitutedProduct}.
     *
     * @param substitutedProduct product offered instead (COMFORT), or null
     * @return this builder
     */
    public Builder substitutedProduct(SubstitutedProduct substitutedProduct) {
      this.substitutedProduct = substitutedProduct;
      return this;
    }

    /**
     * Sets {@link OrderLine#batchId() batchId}.
     *
     * @param batchId batch number (COMFORT), or null
     * @return this builder
     */
    public Builder batchId(String batchId) {
      this.batchId = batchId;
      return this;
    }

    /**
     * Sets {@link OrderLine#brandName() brandName}.
     *
     * @param brandName brand name (COMFORT), or null
     * @return this builder
     */
    public Builder brandName(String brandName) {
      this.brandName = brandName;
      return this;
    }

    /**
     * Adds a value to {@link OrderLine#attributes() attributes}.
     *
     * @param value the item attribute to add
     * @return this builder
     */
    public Builder addAttribute(ItemAttribute value) {
      this.attributes.add(value);
      return this;
    }

    /**
     * Adds an item attribute with name and value to {@link OrderLine#attributes() attributes}.
     *
     * @param name  the attribute name
     * @param value the attribute value
     * @return this builder
     */
    public Builder addAttribute(String name,
                                String value) {
      return addAttribute(new ItemAttribute(name,
                                            value));
    }

    /**
     * Adds a value to {@link OrderLine#characteristics() characteristics}.
     *
     * @param value the product characteristic to add
     * @return this builder
     */
    public Builder addCharacteristic(ProductCharacteristic value) {
      this.characteristics.add(value);
      return this;
    }

    /**
     * Adds a value to {@link OrderLine#classifications() classifications}.
     *
     * @param value the item classification to add
     * @return this builder
     */
    public Builder addClassification(ItemClassification value) {
      this.classifications.add(value);
      return this;
    }

    /**
     * Adds a value to {@link OrderLine#instances() instances}.
     *
     * @param value the product instance to add
     * @return this builder
     */
    public Builder addInstance(ProductInstance value) {
      this.instances.add(value);
      return this;
    }

    /**
     * Sets {@link OrderLine#packagingTypeCode() packagingTypeCode}.
     *
     * @param packagingTypeCode UN/ECE Rec. 21 package type (COMFORT), or null
     * @return this builder
     */
    public Builder packagingTypeCode(String packagingTypeCode) {
      this.packagingTypeCode = packagingTypeCode;
      return this;
    }

    /**
     * Sets {@link OrderLine#originCountry() originCountry}.
     *
     * @param originCountry country of origin (COMFORT), or null
     * @return this builder
     */
    public Builder originCountry(Country originCountry) {
      this.originCountry = originCountry;
      return this;
    }

    /**
     * Sets {@link OrderLine#buyerOrderReference() buyerOrderReference}.
     *
     * @param buyerOrderReference buyer's order line, e.g. in an order response; or null
     * @return this builder
     */
    public Builder buyerOrderReference(DocumentReference buyerOrderReference) {
      this.buyerOrderReference = buyerOrderReference;
      return this;
    }

    /**
     * Sets {@link OrderLine#quotationReference() quotationReference}.
     *
     * @param quotationReference quotation line (COMFORT), or null
     * @return this builder
     */
    public Builder quotationReference(DocumentReference quotationReference) {
      this.quotationReference = quotationReference;
      return this;
    }

    /**
     * Sets {@link OrderLine#catalogueReference() catalogueReference}.
     *
     * @param catalogueReference catalogue (COMFORT), or null
     * @return this builder
     */
    public Builder catalogueReference(DocumentReference catalogueReference) {
      this.catalogueReference = catalogueReference;
      return this;
    }

    /**
     * Sets {@link OrderLine#blanketOrderReference() blanketOrderReference}.
     *
     * @param blanketOrderReference blanket order line, or null
     * @return this builder
     */
    public Builder blanketOrderReference(DocumentReference blanketOrderReference) {
      this.blanketOrderReference = blanketOrderReference;
      return this;
    }

    /**
     * Sets {@link OrderLine#packageQuantity() packageQuantity}.
     *
     * @param packageQuantity number of packages (COMFORT), or null
     * @return this builder
     */
    public Builder packageQuantity(BigDecimal packageQuantity) {
      this.packageQuantity = packageQuantity;
      return this;
    }

    /**
     * Sets {@link OrderLine#perPackageUnitQuantity() perPackageUnitQuantity}.
     *
     * @param perPackageUnitQuantity quantity per package (COMFORT), or null
     * @return this builder
     */
    public Builder perPackageUnitQuantity(BigDecimal perPackageUnitQuantity) {
      this.perPackageUnitQuantity = perPackageUnitQuantity;
      return this;
    }

    /**
     * Sets {@link OrderLine#requestedDespatchDate() requestedDespatchDate}.
     *
     * @param requestedDespatchDate requested despatch date of the line (COMFORT), or null
     * @return this builder
     */
    public Builder requestedDespatchDate(LocalDate requestedDespatchDate) {
      this.requestedDespatchDate = requestedDespatchDate;
      return this;
    }

    /**
     * Sets {@link OrderLine#details() details}.
     *
     * @param details further Order-X line information, or null
     * @return this builder
     */
    public Builder details(OrderLineDetails details) {
      this.details = details;
      return this;
    }

    /**
     * Constructs a new {@link OrderLine} from the current values.
     *
     * @return the constructed value after the model checks succeed
     */
    public OrderLine build() {
      return new OrderLine(id,
                           name,
                           quantity,
                           agreedQuantity,
                           unitCode,
                           netUnitPrice,
                           priceBaseQuantity,
                           grossPrice,
                           tax,
                           adjustments,
                           note,
                           statusCode,
                           partialDeliveryAllowed,
                           sellerItemId,
                           buyerItemId,
                           standardItemId,
                           standardItemSchemeId,
                           description,
                           requestedDeliveryDate,
                           buyerAccountingReference,
                           priceDiscount,
                           substitutedProduct,
                           batchId,
                           brandName,
                           attributes,
                           characteristics,
                           classifications,
                           instances,
                           packagingTypeCode,
                           originCountry,
                           buyerOrderReference,
                           quotationReference,
                           catalogueReference,
                           blanketOrderReference,
                           packageQuantity,
                           perPackageUnitQuantity,
                           requestedDespatchDate,
                           details);
    }
  }
}

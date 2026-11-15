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
package io.github.nalukit.paniolo.model.extended;

import io.github.nalukit.paniolo.model.common.Party;
import io.github.nalukit.paniolo.model.common.validation.ModelChecks;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Line information of the Factur-X / ZUGFeRD profile EXTENDED that EN 16931 does not know: line
 * hierarchy, product details, and line level references and delivery details. Only the CII writer
 * with the EXTENDED specification identifier writes it.
 *
 * @param parentLineId                    identifier of the parent line (BT-X-304); requires a subtype
 * @param subtype                         line subtype (BT-X-8), or null for a regular line
 * @param statusCode                      line status code (UNTDID 1229), or null
 * @param productId                       product identifier of the seller's system, or null
 * @param industryItemId                  manufacturer's or industry item identifier, or null
 * @param modelId                         model identifier, or null
 * @param modelName                       model name, or null
 * @param brandName                       brand name, or null
 * @param batchIds                        batch numbers of the item
 * @param characteristics                 characteristics with type code or measure
 * @param instances                       individual product instances with batch or serial number
 * @param manufacturer                    manufacturer, or null
 * @param includedProducts                products included in the item
 * @param deliveryTermsCode               delivery terms of the line, e.g. an Incoterms code, or null
 * @param sellerOrderReference            seller's order (line), or null
 * @param quotationReference              quotation (line), or null
 * @param contractReference               contract (line), or null
 * @param itemSeller                      seller of the item, if it differs from the seller, or null;
 *                                        the invoice requires its name and complete address, except
 *                                        in a French multi-vendor invoice
 * @param ultimateCustomerOrderReferences orders of the ultimate customer
 * @param chargeFreeQuantity              quantity delivered free of charge, in the line unit, or null
 * @param packageQuantity                 number of packages, or null
 * @param perPackageUnitQuantity          quantity per package, in the line unit, or null
 * @param shipTo                          deliver-to party of the line, or null
 * @param ultimateShipTo                  final recipient of the line, or null
 * @param actualDeliveryDate              delivery date of the line, or null
 * @param despatchAdviceReference         despatch advice (line), or null
 * @param receivingAdviceReference        receiving advice (line), or null
 * @param deliveryNoteReference           delivery note (line), or null
 * @param precedingInvoiceReference       preceding invoice (line), or null
 * @param noteContentCode                 content code of the line note (BT-127), or null
 * @param noteSubjectCode                 UNTDID 4451 subject code of the line note, or null
 * @param additionalReferences            further documents referenced by the line
 * @param accountingTypeCode              UNTDID 4437 type of the line accounting reference (BT-133), or null
 * @param taxTotal                        stated VAT amount of the line, or null
 * @param grossTotal                      stated line amount with VAT; with {@code taxTotal} it is the
 *                                        line net amount plus that VAT amount; or null
 * @param priceAdjustments                allowances and charges on the gross price (BT-148) in the
 *                                        order stated; their allowances minus their charges are
 *                                        the price discount of the line (BT-147)
 * @param additionalAccountingAccounts    further accounting accounts after the line accounting
 *                                        reference (BT-133) and its type, in document order
 * @param furtherObjectIdentifiers        further invoiced object identifiers (BT-128) after the one
 *                                        of the line, in document order, e.g. the sub-invoice (AFL)
 *                                        of a French multi-vendor invoice next to the object (AVV)
 * @param taxDueDateTypeCode              code of the date the VAT of the line becomes due (UNTDID 2475,
 *                                        e.g. {@code 5}, {@code 29}, {@code 72}) as the line states it,
 *                                        or null; a subtotal or information line may state it without
 *                                        a VAT category
 */
public record LineExtension(String parentLineId, LineSubtype subtype, String statusCode, String productId, String industryItemId, String modelId, String modelName, String brandName, List<String> batchIds, List<ProductCharacteristic> characteristics,
                            List<ProductInstance> instances, Party manufacturer, List<IncludedProduct> includedProducts, String deliveryTermsCode, DocumentReference sellerOrderReference, DocumentReference quotationReference,
                            DocumentReference contractReference, Party itemSeller, List<DocumentReference> ultimateCustomerOrderReferences, BigDecimal chargeFreeQuantity, BigDecimal packageQuantity, BigDecimal perPackageUnitQuantity, Party shipTo,
                            Party ultimateShipTo, LocalDate actualDeliveryDate, DocumentReference despatchAdviceReference, DocumentReference receivingAdviceReference, DocumentReference deliveryNoteReference, DocumentReference precedingInvoiceReference,
                            String noteContentCode, String noteSubjectCode, List<TypedDocumentReference> additionalReferences, String accountingTypeCode, BigDecimal taxTotal, BigDecimal grossTotal,
                            List<GrossPriceAdjustment> priceAdjustments, List<AccountingAccount> additionalAccountingAccounts,
                            List<ObjectIdentifier> furtherObjectIdentifiers, String taxDueDateTypeCode) {
  /**
   * Applies the checks of the model and copies the lists.
   *
   * @throws IllegalArgumentException if a value violates the checks of the model
   */
  public LineExtension {
    if (taxDueDateTypeCode != null && taxDueDateTypeCode.isBlank()) {
      throw new IllegalArgumentException("taxDueDateTypeCode must not be blank");
    }
    Party.requireAddress(manufacturer,
                         "manufacturer");
    // The invoice checks the item seller, which a French multi-vendor invoice states partially.
    Party.requireAddress(shipTo,
                         "shipTo");
    Party.requireAddress(ultimateShipTo,
                         "ultimateShipTo");
    if (parentLineId != null) {
      ModelChecks.required(parentLineId,
                           "parent line id");
      // BR-FXEXT-06: a sub-line states its subtype.
      if (subtype == null) {
        throw new IllegalArgumentException("A line with parent line id " + parentLineId + " requires a subtype");
      }
    }
    for (BigDecimal quantity : new BigDecimal[] { chargeFreeQuantity,
                                                  packageQuantity,
                                                  perPackageUnitQuantity }) {
      if (quantity != null) {
        ModelChecks.nonNegative(quantity,
                                "line delivery quantity");
      }
    }
    if (taxTotal != null) {
      taxTotal = ModelChecks.money(taxTotal);
    }
    if (grossTotal != null) {
      grossTotal = ModelChecks.money(grossTotal);
    }
    batchIds                        = List.copyOf(batchIds);
    characteristics                 = List.copyOf(characteristics);
    instances                       = List.copyOf(instances);
    includedProducts                = List.copyOf(includedProducts);
    ultimateCustomerOrderReferences = List.copyOf(ultimateCustomerOrderReferences);
    additionalReferences            = List.copyOf(additionalReferences);
    // Missing in JSON written before the gross price adjustments were added.
    priceAdjustments = priceAdjustments == null ?
                       List.of() :
                       List.copyOf(priceAdjustments);
    additionalAccountingAccounts = additionalAccountingAccounts == null ?
                                   List.of() :
                                   List.copyOf(additionalAccountingAccounts);
    furtherObjectIdentifiers = furtherObjectIdentifiers == null ?
                               List.of() :
                               List.copyOf(furtherObjectIdentifiers);
  }

  /**
   * Starts named construction of a {@link LineExtension} using the builder defaults.
   *
   * @return a new builder; not shared with other calls
   */
  public static Builder create() {
    return new Builder();
  }

  /**
   * Returns true if no EXTENDED line information is set.
   *
   * @return true if no information is set
   */
  public boolean isEmpty() {
    return equals(create().build());
  }

  /**
   * Returns the price discount (BT-147) the gross price adjustments state: their allowances minus
   * their charges.
   *
   * @return the sum of the adjustments, or null without adjustments
   */
  public BigDecimal priceAdjustmentDiscount() {
    return priceAdjustments.isEmpty() ?
           null :
           priceAdjustments.stream()
                           .map(GrossPriceAdjustment::discount)
                           .reduce(BigDecimal.ZERO,
                                   BigDecimal::add);
  }

  /**
   * Returns this line information without the gross price adjustments, e.g. after the price of the
   * line was recalculated.
   *
   * @return a copy without gross price adjustments
   */
  public LineExtension withoutPriceAdjustments() {
    return new LineExtension(parentLineId,
                             subtype,
                             statusCode,
                             productId,
                             industryItemId,
                             modelId,
                             modelName,
                             brandName,
                             batchIds,
                             characteristics,
                             instances,
                             manufacturer,
                             includedProducts,
                             deliveryTermsCode,
                             sellerOrderReference,
                             quotationReference,
                             contractReference,
                             itemSeller,
                             ultimateCustomerOrderReferences,
                             chargeFreeQuantity,
                             packageQuantity,
                             perPackageUnitQuantity,
                             shipTo,
                             ultimateShipTo,
                             actualDeliveryDate,
                             despatchAdviceReference,
                             receivingAdviceReference,
                             deliveryNoteReference,
                             precedingInvoiceReference,
                             noteContentCode,
                             noteSubjectCode,
                             additionalReferences,
                             accountingTypeCode,
                             taxTotal,
                             grossTotal,
                             List.of(),
                             additionalAccountingAccounts,
                             furtherObjectIdentifiers,
                             taxDueDateTypeCode);
  }

  /**
   * Returns true if the line counts in the totals: regular lines without or with subtype DETAIL.
   *
   * @return true if the line counts in the totals
   */
  public boolean counts() {
    return subtype == null || subtype.counts();
  }

  /**
   * Named construction of immutable EXTENDED line information.
   *
   * <p>Mutable construction state for {@link LineExtension}. Use a separate builder per operation;
   * builders are not thread-safe. {@link #build()} constructs a new immutable value and applies
   * the checks of the model. XML schema and business rule validation are separate operations.
   */
  public static final class Builder {
    private String                       parentLineId;
    private LineSubtype                  subtype;
    private String                       statusCode;
    private String                       productId;
    private String                       industryItemId;
    private String                       modelId;
    private String                       modelName;
    private String                       brandName;
    private List<String>                 batchIds                        = new ArrayList<>();
    private List<ProductCharacteristic>  characteristics                 = new ArrayList<>();
    private List<ProductInstance>        instances                       = new ArrayList<>();
    private Party                        manufacturer;
    private List<IncludedProduct>        includedProducts                = new ArrayList<>();
    private String                       deliveryTermsCode;
    private DocumentReference            sellerOrderReference;
    private DocumentReference            quotationReference;
    private DocumentReference            contractReference;
    private Party                        itemSeller;
    private List<DocumentReference>      ultimateCustomerOrderReferences = new ArrayList<>();
    private BigDecimal                   chargeFreeQuantity;
    private BigDecimal                   packageQuantity;
    private BigDecimal                   perPackageUnitQuantity;
    private Party                        shipTo;
    private Party                        ultimateShipTo;
    private LocalDate                    actualDeliveryDate;
    private DocumentReference            despatchAdviceReference;
    private DocumentReference            receivingAdviceReference;
    private DocumentReference            deliveryNoteReference;
    private DocumentReference            precedingInvoiceReference;
    private String                       noteContentCode;
    private String                       noteSubjectCode;
    private List<TypedDocumentReference> additionalReferences            = new ArrayList<>();
    private String                       accountingTypeCode;
    private BigDecimal                   taxTotal;
    private BigDecimal                   grossTotal;
    private List<GrossPriceAdjustment>   priceAdjustments                = new ArrayList<>();
    private List<AccountingAccount>      additionalAccountingAccounts    = new ArrayList<>();
    private List<ObjectIdentifier>       furtherObjectIdentifiers        = new ArrayList<>();
    private String                       taxDueDateTypeCode;

    /**
     * Creates a builder with the defaults of the model; usually obtained with {@link LineExtension#create()}.
     */
    public Builder() {
    }

    /**
     * Sets {@link LineExtension#parentLineId() parentLineId}.
     *
     * @param parentLineId identifier of the parent line (BT-X-304); requires a subtype
     * @return this builder
     */
    public Builder parentLineId(String parentLineId) {
      this.parentLineId = parentLineId;
      return this;
    }

    /**
     * Sets {@link LineExtension#subtype() subtype}.
     *
     * @param subtype line subtype (BT-X-8), or null for a regular line
     * @return this builder
     */
    public Builder subtype(LineSubtype subtype) {
      this.subtype = subtype;
      return this;
    }

    /**
     * Sets {@link LineExtension#statusCode() statusCode}.
     *
     * @param statusCode line status code (UNTDID 1229), or null
     * @return this builder
     */
    public Builder statusCode(String statusCode) {
      this.statusCode = statusCode;
      return this;
    }

    /**
     * Sets {@link LineExtension#productId() productId}.
     *
     * @param productId product identifier of the seller's system, or null
     * @return this builder
     */
    public Builder productId(String productId) {
      this.productId = productId;
      return this;
    }

    /**
     * Sets {@link LineExtension#industryItemId() industryItemId}.
     *
     * @param industryItemId manufacturer's or industry item identifier, or null
     * @return this builder
     */
    public Builder industryItemId(String industryItemId) {
      this.industryItemId = industryItemId;
      return this;
    }

    /**
     * Sets {@link LineExtension#modelId() modelId}.
     *
     * @param modelId model identifier, or null
     * @return this builder
     */
    public Builder modelId(String modelId) {
      this.modelId = modelId;
      return this;
    }

    /**
     * Sets {@link LineExtension#modelName() modelName}.
     *
     * @param modelName model name, or null
     * @return this builder
     */
    public Builder modelName(String modelName) {
      this.modelName = modelName;
      return this;
    }

    /**
     * Sets {@link LineExtension#brandName() brandName}.
     *
     * @param brandName brand name, or null
     * @return this builder
     */
    public Builder brandName(String brandName) {
      this.brandName = brandName;
      return this;
    }

    /**
     * Adds a value to {@link LineExtension#batchIds() batchIds}.
     *
     * @param value the string to add
     * @return this builder
     */
    public Builder addBatchId(String value) {
      this.batchIds.add(value);
      return this;
    }

    /**
     * Adds a value to {@link LineExtension#characteristics() characteristics}.
     *
     * @param value the product characteristic to add
     * @return this builder
     */
    public Builder addCharacteristic(ProductCharacteristic value) {
      this.characteristics.add(value);
      return this;
    }

    /**
     * Adds a value to {@link LineExtension#instances() instances}.
     *
     * @param value the product instance to add
     * @return this builder
     */
    public Builder addInstance(ProductInstance value) {
      this.instances.add(value);
      return this;
    }

    /**
     * Sets {@link LineExtension#manufacturer() manufacturer}.
     *
     * @param manufacturer manufacturer, or null
     * @return this builder
     */
    public Builder manufacturer(Party manufacturer) {
      this.manufacturer = manufacturer;
      return this;
    }

    /**
     * Adds a value to {@link LineExtension#includedProducts() includedProducts}.
     *
     * @param value the included product to add
     * @return this builder
     */
    public Builder addIncludedProduct(IncludedProduct value) {
      this.includedProducts.add(value);
      return this;
    }

    /**
     * Sets {@link LineExtension#deliveryTermsCode() deliveryTermsCode}.
     *
     * @param deliveryTermsCode delivery terms of the line, e.g. an Incoterms code, or null
     * @return this builder
     */
    public Builder deliveryTermsCode(String deliveryTermsCode) {
      this.deliveryTermsCode = deliveryTermsCode;
      return this;
    }

    /**
     * Sets {@link LineExtension#sellerOrderReference() sellerOrderReference}.
     *
     * @param sellerOrderReference seller's order (line), or null
     * @return this builder
     */
    public Builder sellerOrderReference(DocumentReference sellerOrderReference) {
      this.sellerOrderReference = sellerOrderReference;
      return this;
    }

    /**
     * Sets {@link LineExtension#quotationReference() quotationReference}.
     *
     * @param quotationReference quotation (line), or null
     * @return this builder
     */
    public Builder quotationReference(DocumentReference quotationReference) {
      this.quotationReference = quotationReference;
      return this;
    }

    /**
     * Sets {@link LineExtension#contractReference() contractReference}.
     *
     * @param contractReference contract (line), or null
     * @return this builder
     */
    public Builder contractReference(DocumentReference contractReference) {
      this.contractReference = contractReference;
      return this;
    }

    /**
     * Sets {@link LineExtension#itemSeller() itemSeller}.
     *
     * @param itemSeller seller of the item, if it differs from the seller, or null; the invoice
     *                   requires its name and complete address, except in a French multi-vendor
     *                   invoice
     * @return this builder
     */
    public Builder itemSeller(Party itemSeller) {
      this.itemSeller = itemSeller;
      return this;
    }

    /**
     * Adds a value to {@link LineExtension#ultimateCustomerOrderReferences() ultimateCustomerOrderReferences}.
     *
     * @param value the document reference to add
     * @return this builder
     */
    public Builder addUltimateCustomerOrderReference(DocumentReference value) {
      this.ultimateCustomerOrderReferences.add(value);
      return this;
    }

    /**
     * Sets {@link LineExtension#chargeFreeQuantity() chargeFreeQuantity}.
     *
     * @param chargeFreeQuantity quantity delivered free of charge, in the line unit, or null
     * @return this builder
     */
    public Builder chargeFreeQuantity(BigDecimal chargeFreeQuantity) {
      this.chargeFreeQuantity = chargeFreeQuantity;
      return this;
    }

    /**
     * Sets {@link LineExtension#packageQuantity() packageQuantity}.
     *
     * @param packageQuantity number of packages, or null
     * @return this builder
     */
    public Builder packageQuantity(BigDecimal packageQuantity) {
      this.packageQuantity = packageQuantity;
      return this;
    }

    /**
     * Sets {@link LineExtension#perPackageUnitQuantity() perPackageUnitQuantity}.
     *
     * @param perPackageUnitQuantity quantity per package, in the line unit, or null
     * @return this builder
     */
    public Builder perPackageUnitQuantity(BigDecimal perPackageUnitQuantity) {
      this.perPackageUnitQuantity = perPackageUnitQuantity;
      return this;
    }

    /**
     * Sets {@link LineExtension#shipTo() shipTo}.
     *
     * @param shipTo deliver-to party of the line, or null
     * @return this builder
     */
    public Builder shipTo(Party shipTo) {
      this.shipTo = shipTo;
      return this;
    }

    /**
     * Sets {@link LineExtension#ultimateShipTo() ultimateShipTo}.
     *
     * @param ultimateShipTo final recipient of the line, or null
     * @return this builder
     */
    public Builder ultimateShipTo(Party ultimateShipTo) {
      this.ultimateShipTo = ultimateShipTo;
      return this;
    }

    /**
     * Sets {@link LineExtension#actualDeliveryDate() actualDeliveryDate}.
     *
     * @param actualDeliveryDate delivery date of the line, or null
     * @return this builder
     */
    public Builder actualDeliveryDate(LocalDate actualDeliveryDate) {
      this.actualDeliveryDate = actualDeliveryDate;
      return this;
    }

    /**
     * Sets {@link LineExtension#despatchAdviceReference() despatchAdviceReference}.
     *
     * @param despatchAdviceReference despatch advice (line), or null
     * @return this builder
     */
    public Builder despatchAdviceReference(DocumentReference despatchAdviceReference) {
      this.despatchAdviceReference = despatchAdviceReference;
      return this;
    }

    /**
     * Sets {@link LineExtension#receivingAdviceReference() receivingAdviceReference}.
     *
     * @param receivingAdviceReference receiving advice (line), or null
     * @return this builder
     */
    public Builder receivingAdviceReference(DocumentReference receivingAdviceReference) {
      this.receivingAdviceReference = receivingAdviceReference;
      return this;
    }

    /**
     * Sets {@link LineExtension#deliveryNoteReference() deliveryNoteReference}.
     *
     * @param deliveryNoteReference delivery note (line), or null
     * @return this builder
     */
    public Builder deliveryNoteReference(DocumentReference deliveryNoteReference) {
      this.deliveryNoteReference = deliveryNoteReference;
      return this;
    }

    /**
     * Sets {@link LineExtension#precedingInvoiceReference() precedingInvoiceReference}.
     *
     * @param precedingInvoiceReference preceding invoice (line), or null
     * @return this builder
     */
    public Builder precedingInvoiceReference(DocumentReference precedingInvoiceReference) {
      this.precedingInvoiceReference = precedingInvoiceReference;
      return this;
    }

    /**
     * Sets {@link LineExtension#noteContentCode() noteContentCode}.
     *
     * @param noteContentCode content code of the line note (BT-127), or null
     * @return this builder
     */
    public Builder noteContentCode(String noteContentCode) {
      this.noteContentCode = noteContentCode;
      return this;
    }

    /**
     * Sets {@link LineExtension#noteSubjectCode() noteSubjectCode}.
     *
     * @param noteSubjectCode UNTDID 4451 subject code of the line note, or null
     * @return this builder
     */
    public Builder noteSubjectCode(String noteSubjectCode) {
      this.noteSubjectCode = noteSubjectCode;
      return this;
    }

    /**
     * Adds a value to {@link LineExtension#additionalReferences() additionalReferences}.
     *
     * @param value the typed document reference to add
     * @return this builder
     */
    public Builder addAdditionalReference(TypedDocumentReference value) {
      this.additionalReferences.add(value);
      return this;
    }

    /**
     * Sets {@link LineExtension#accountingTypeCode() accountingTypeCode}.
     *
     * @param accountingTypeCode UNTDID 4437 type of the line accounting reference (BT-133), or null
     * @return this builder
     */
    public Builder accountingTypeCode(String accountingTypeCode) {
      this.accountingTypeCode = accountingTypeCode;
      return this;
    }

    /**
     * Stated VAT amount of the line ({@code TaxTotalAmount} of the line summation).
     *
     * @param taxTotal stated VAT amount of the line, or null
     * @return this builder
     */
    public Builder taxTotal(BigDecimal taxTotal) {
      this.taxTotal = taxTotal;
      return this;
    }

    /**
     * Stated line amount with VAT ({@code GrandTotalAmount} of the line summation).
     *
     * @param grossTotal stated line amount with VAT; with {@code taxTotal} it is the line net amount plus that VAT amount; or null
     * @return this builder
     */
    public Builder grossTotal(BigDecimal grossTotal) {
      this.grossTotal = grossTotal;
      return this;
    }

    /**
     * Adds a value to {@link LineExtension#priceAdjustments() priceAdjustments}. The price discount
     * of the line (BT-147) must equal the allowances minus the charges.
     *
     * @param value the allowance or charge on the gross price to add
     * @return this builder
     */
    public Builder addPriceAdjustment(GrossPriceAdjustment value) {
      this.priceAdjustments.add(value);
      return this;
    }

    /**
     * Adds a further accounting account after the line accounting reference (BT-133).
     *
     * @param value the accounting account to add
     * @return this builder
     */
    public Builder addAdditionalAccountingAccount(AccountingAccount value) {
      this.additionalAccountingAccounts.add(value);
      return this;
    }

    /**
     * Adds a further invoiced object identifier (BT-128) after the one of the line.
     *
     * @param value the object identifier to add
     * @return this builder
     */
    public Builder addFurtherObjectIdentifier(ObjectIdentifier value) {
      this.furtherObjectIdentifiers.add(value);
      return this;
    }

    /**
     * Constructs a new {@link LineExtension} from the current values.
     *
     * @return the constructed value after the model checks succeed
     */
    public LineExtension build() {
      return new LineExtension(parentLineId,
                               subtype,
                               statusCode,
                               productId,
                               industryItemId,
                               modelId,
                               modelName,
                               brandName,
                               batchIds,
                               characteristics,
                               instances,
                               manufacturer,
                               includedProducts,
                               deliveryTermsCode,
                               sellerOrderReference,
                               quotationReference,
                               contractReference,
                               itemSeller,
                               ultimateCustomerOrderReferences,
                               chargeFreeQuantity,
                               packageQuantity,
                               perPackageUnitQuantity,
                               shipTo,
                               ultimateShipTo,
                               actualDeliveryDate,
                               despatchAdviceReference,
                               receivingAdviceReference,
                               deliveryNoteReference,
                               precedingInvoiceReference,
                               noteContentCode,
                               noteSubjectCode,
                               additionalReferences,
                               accountingTypeCode,
                               taxTotal,
                               grossTotal,
                               priceAdjustments,
                               additionalAccountingAccounts,
                               furtherObjectIdentifiers,
                               taxDueDateTypeCode);
    }

    /**
     * Sets {@link LineExtension#taxDueDateTypeCode() taxDueDateTypeCode}.
     *
     * @param taxDueDateTypeCode code of the date the VAT of the line becomes due (UNTDID 2475), or null
     * @return this builder
     */
    public Builder taxDueDateTypeCode(String taxDueDateTypeCode) {
      this.taxDueDateTypeCode = taxDueDateTypeCode;
      return this;
    }
  }
}

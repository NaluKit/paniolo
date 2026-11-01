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

import io.github.nalukit.paniolo.model.common.ItemAttribute;
import io.github.nalukit.paniolo.model.common.ItemClassification;
import io.github.nalukit.paniolo.model.common.validation.ModelChecks;
import io.github.nalukit.paniolo.model.common.value.UnitCode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Line of a despatch advice (Peppol BIS Despatch Advice 3): the delivered quantity of an ordered item.
 *
 * @param id                   line identifier
 * @param note                 line note, or null
 * @param deliveredQuantity    delivered quantity
 * @param unitCode             unit of the quantities
 * @param outstandingQuantity  quantity still to be delivered, or null
 * @param outstandingReason    reason for the outstanding quantity, or null
 * @param orderLineId          line of the order, required by UBL; or null
 * @param salesOrderLineId     line of the seller's order, or null
 * @param orderReference       order the line belongs to, if not the order of the document; or null
 * @param itemName             item name
 * @param buyerItemId          buyer's item identifier, or null
 * @param sellerItemId         seller's item identifier, or null
 * @param standardItemId       standard item identifier, e.g. a GTIN; or null
 * @param standardItemSchemeId scheme of the standard item identifier, or null
 * @param classifications      item classifications
 * @param attributes           item properties as name and value
 * @param instances            serial numbers, lots, and dates of the delivered items
 * @param handlingUnitId       transport handling unit, e.g. the SSCC of a pallet; or null
 * @param handlingUnitTypeCode UN/ECE Rec. 21 type of the handling unit, or null
 */
public record DespatchLine(String id, String note, BigDecimal deliveredQuantity, UnitCode unitCode, BigDecimal outstandingQuantity, String outstandingReason, String orderLineId, String salesOrderLineId, String orderReference, String itemName,
                           String buyerItemId, String sellerItemId, String standardItemId, String standardItemSchemeId, List<ItemClassification> classifications, List<ItemAttribute> attributes, List<DespatchItemInstance> instances, String handlingUnitId,
                           String handlingUnitTypeCode) {
  /**
   * Applies the checks of the model and copies the lists.
   *
   * @throws NullPointerException     if a required value is null
   * @throws IllegalArgumentException if a value violates the checks of the model
   */
  public DespatchLine {
    ModelChecks.required(id,
                         "line id");
    Objects.requireNonNull(deliveredQuantity,
                           "deliveredQuantity");
    Objects.requireNonNull(unitCode,
                           "unitCode");
    if (orderLineId != null) {
      ModelChecks.required(orderLineId,
                           "order line id");
    }
    ModelChecks.required(itemName,
                         "item name");
    if (standardItemId != null && (standardItemSchemeId == null || standardItemSchemeId.isBlank())) {
      throw new IllegalArgumentException("Line " + id + ": standardItemId requires standardItemSchemeId");
    }
    if (outstandingReason != null && outstandingQuantity == null) {
      throw new IllegalArgumentException("Line " + id + ": an outstanding reason requires the outstanding quantity");
    }
    if (handlingUnitTypeCode != null && handlingUnitId == null) {
      throw new IllegalArgumentException("Line " + id + ": a handling unit type requires the handling unit id");
    }
    classifications = classifications == null ?
                      List.of() :
                      List.copyOf(classifications);
    attributes      = attributes == null ?
                      List.of() :
                      List.copyOf(attributes);
    instances       = instances == null ?
                      List.of() :
                      List.copyOf(instances);
  }

  /**
   * Starts named construction of a {@link DespatchLine} using the builder defaults.
   *
   * @return a new builder; not shared with other calls
   */
  public static Builder create() {
    return new Builder();
  }

  /**
   * Named construction of an immutable despatch line.
   *
   * <p>Mutable construction state for {@link DespatchLine}. Use a separate builder per operation;
   * builders are not thread-safe. {@link #build()} constructs a new immutable value and applies
   * the checks of the model. XML schema and business rule validation are separate operations.
   */
  public static final class Builder {
    private String                     id;
    private String                     note;
    private BigDecimal                 deliveredQuantity;
    private UnitCode                   unitCode;
    private BigDecimal                 outstandingQuantity;
    private String                     outstandingReason;
    private String                     orderLineId;
    private String                     salesOrderLineId;
    private String                     orderReference;
    private String                     itemName;
    private String                     buyerItemId;
    private String                     sellerItemId;
    private String                     standardItemId;
    private String                     standardItemSchemeId;
    private List<ItemClassification>   classifications = new ArrayList<>();
    private List<ItemAttribute>        attributes      = new ArrayList<>();
    private List<DespatchItemInstance> instances       = new ArrayList<>();
    private String                     handlingUnitId;
    private String                     handlingUnitTypeCode;

    /**
     * Creates a builder with the defaults of the model; usually obtained with {@link DespatchLine#create()}.
     */
    public Builder() {
    }

    /**
     * Sets {@link DespatchLine#id() id}.
     *
     * @param id line identifier
     * @return this builder
     */
    public Builder id(String id) {
      this.id = id;
      return this;
    }

    /**
     * Sets {@link DespatchLine#note() note}.
     *
     * @param note line note, or null
     * @return this builder
     */
    public Builder note(String note) {
      this.note = note;
      return this;
    }

    /**
     * Sets {@link DespatchLine#deliveredQuantity() deliveredQuantity}.
     *
     * @param deliveredQuantity delivered quantity
     * @return this builder
     */
    public Builder deliveredQuantity(BigDecimal deliveredQuantity) {
      this.deliveredQuantity = deliveredQuantity;
      return this;
    }

    /**
     * Sets {@link DespatchLine#unitCode() unitCode}.
     *
     * @param unitCode unit of the quantities
     * @return this builder
     */
    public Builder unitCode(UnitCode unitCode) {
      this.unitCode = unitCode;
      return this;
    }

    /**
     * Sets {@link DespatchLine#unitCode() unitCode}.
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
     * Sets {@link DespatchLine#outstandingQuantity() outstandingQuantity}.
     *
     * @param outstandingQuantity quantity still to be delivered, or null
     * @return this builder
     */
    public Builder outstandingQuantity(BigDecimal outstandingQuantity) {
      this.outstandingQuantity = outstandingQuantity;
      return this;
    }

    /**
     * Sets {@link DespatchLine#outstandingReason() outstandingReason}.
     *
     * @param outstandingReason reason for the outstanding quantity, or null
     * @return this builder
     */
    public Builder outstandingReason(String outstandingReason) {
      this.outstandingReason = outstandingReason;
      return this;
    }

    /**
     * Sets {@link DespatchLine#orderLineId() orderLineId}.
     *
     * @param orderLineId line of the order, required by UBL; or null
     * @return this builder
     */
    public Builder orderLineId(String orderLineId) {
      this.orderLineId = orderLineId;
      return this;
    }

    /**
     * Sets {@link DespatchLine#salesOrderLineId() salesOrderLineId}.
     *
     * @param salesOrderLineId line of the seller's order, or null
     * @return this builder
     */
    public Builder salesOrderLineId(String salesOrderLineId) {
      this.salesOrderLineId = salesOrderLineId;
      return this;
    }

    /**
     * Sets {@link DespatchLine#orderReference() orderReference}.
     *
     * @param orderReference order the line belongs to, if not the order of the document; or null
     * @return this builder
     */
    public Builder orderReference(String orderReference) {
      this.orderReference = orderReference;
      return this;
    }

    /**
     * Sets {@link DespatchLine#itemName() itemName}.
     *
     * @param itemName item name
     * @return this builder
     */
    public Builder itemName(String itemName) {
      this.itemName = itemName;
      return this;
    }

    /**
     * Sets {@link DespatchLine#buyerItemId() buyerItemId}.
     *
     * @param buyerItemId buyer's item identifier, or null
     * @return this builder
     */
    public Builder buyerItemId(String buyerItemId) {
      this.buyerItemId = buyerItemId;
      return this;
    }

    /**
     * Sets {@link DespatchLine#sellerItemId() sellerItemId}.
     *
     * @param sellerItemId seller's item identifier, or null
     * @return this builder
     */
    public Builder sellerItemId(String sellerItemId) {
      this.sellerItemId = sellerItemId;
      return this;
    }

    /**
     * Sets {@link DespatchLine#standardItemId() standardItemId}.
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
     * Adds a value to {@link DespatchLine#classifications() classifications}.
     *
     * @param value the item classification to add
     * @return this builder
     */
    public Builder addClassification(ItemClassification value) {
      this.classifications.add(value);
      return this;
    }

    /**
     * Adds a value to {@link DespatchLine#attributes() attributes}.
     *
     * @param value the item attribute to add
     * @return this builder
     */
    public Builder addAttribute(ItemAttribute value) {
      this.attributes.add(value);
      return this;
    }

    /**
     * Adds an item attribute with name and value to {@link DespatchLine#attributes() attributes}.
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
     * Adds a value to {@link DespatchLine#instances() instances}.
     *
     * @param value the despatch item instance to add
     * @return this builder
     */
    public Builder addInstance(DespatchItemInstance value) {
      this.instances.add(value);
      return this;
    }

    /**
     * Sets {@link DespatchLine#handlingUnitId() handlingUnitId}.
     *
     * @param handlingUnitId transport handling unit, e.g. the SSCC of a pallet; or null
     * @return this builder
     */
    public Builder handlingUnitId(String handlingUnitId) {
      this.handlingUnitId = handlingUnitId;
      return this;
    }

    /**
     * Sets {@link DespatchLine#handlingUnitTypeCode() handlingUnitTypeCode}.
     *
     * @param handlingUnitTypeCode UN/ECE Rec. 21 type of the handling unit, or null
     * @return this builder
     */
    public Builder handlingUnitTypeCode(String handlingUnitTypeCode) {
      this.handlingUnitTypeCode = handlingUnitTypeCode;
      return this;
    }

    /**
     * Constructs a new {@link DespatchLine} from the current values.
     *
     * @return the constructed value after the model checks succeed
     */
    public DespatchLine build() {
      return new DespatchLine(id,
                              note,
                              deliveredQuantity,
                              unitCode,
                              outstandingQuantity,
                              outstandingReason,
                              orderLineId,
                              salesOrderLineId,
                              orderReference,
                              itemName,
                              buyerItemId,
                              sellerItemId,
                              standardItemId,
                              standardItemSchemeId,
                              classifications,
                              attributes,
                              instances,
                              handlingUnitId,
                              handlingUnitTypeCode);
    }
  }
}

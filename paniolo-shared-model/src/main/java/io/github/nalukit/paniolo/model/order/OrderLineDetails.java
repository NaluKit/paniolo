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

import io.github.nalukit.paniolo.model.common.value.UnitCode;

import java.util.ArrayList;
import java.util.List;

/**
 * Further information of an Order-X line: the subject of the line note, allowances and charges on
 * the gross price, requested periods, packaging, and referenced documents. Fields marked COMFORT are
 * rejected by the writer for the profile BASIC.
 *
 * @param noteSubjectCode         UNTDID 4451 subject of the line note, requires the note; or null
 * @param priceAdjustments        allowances and charges on the gross price (COMFORT); instead of priceDiscount
 * @param requestedDeliveryPeriod requested delivery period of the line (COMFORT), or null
 * @param requestedDespatchPeriod requested despatch period of the line (COMFORT), or null
 * @param packageQuantityUnit     unit of the package quantity (COMFORT), requires it; or null
 * @param packageDimensions       dimensions of the packaging (COMFORT), or null
 * @param documents               documents referenced by the line (COMFORT)
 * @param productDocuments        documents referenced by the product (COMFORT)
 */
public record OrderLineDetails(String noteSubjectCode, List<PriceAdjustment> priceAdjustments, OrderPeriod requestedDeliveryPeriod, OrderPeriod requestedDespatchPeriod, UnitCode packageQuantityUnit, PackageDimensions packageDimensions,
                               List<OrderDocument> documents, List<OrderDocument> productDocuments) {
  /**
   * Copies the lists; null lists become empty lists.
   */
  public OrderLineDetails {
    priceAdjustments = priceAdjustments == null ?
                       List.of() :
                       List.copyOf(priceAdjustments);
    documents        = documents == null ?
                       List.of() :
                       List.copyOf(documents);
    productDocuments = productDocuments == null ?
                       List.of() :
                       List.copyOf(productDocuments);
  }

  /**
   * Starts named construction of a {@link OrderLineDetails} using the builder defaults.
   *
   * @return a new builder; not shared with other calls
   */
  public static Builder create() {
    return new Builder();
  }

  /**
   * Returns true if no detail is set; the line then carries no details.
   *
   * @return true if no detail is set
   */
  public boolean isEmpty() {
    return noteSubjectCode == null && priceAdjustments.isEmpty() && requestedDeliveryPeriod == null && requestedDespatchPeriod == null && packageQuantityUnit == null && packageDimensions == null && documents.isEmpty() && productDocuments.isEmpty();
  }

  /**
   * Named construction of immutable {@link OrderLineDetails}.
   *
   * <p>Mutable construction state for {@link OrderLineDetails}. Use a separate builder per operation;
   * builders are not thread-safe. {@link #build()} constructs a new immutable value and applies
   * the checks of the model.
   */
  public static final class Builder {
    private String                noteSubjectCode;
    private List<PriceAdjustment> priceAdjustments = new ArrayList<>();
    private OrderPeriod           requestedDeliveryPeriod;
    private OrderPeriod           requestedDespatchPeriod;
    private UnitCode              packageQuantityUnit;
    private PackageDimensions     packageDimensions;
    private List<OrderDocument>   documents        = new ArrayList<>();
    private List<OrderDocument>   productDocuments = new ArrayList<>();

    private Builder() {
    }

    /**
     * Sets {@link OrderLineDetails#noteSubjectCode() noteSubjectCode}.
     *
     * @param noteSubjectCode UNTDID 4451 subject of the line note, requires the note; or null
     * @return this builder
     */
    public Builder noteSubjectCode(String noteSubjectCode) {
      this.noteSubjectCode = noteSubjectCode;
      return this;
    }

    /**
     * Sets {@link OrderLineDetails#priceAdjustments() priceAdjustments}.
     *
     * @param priceAdjustments allowances and charges on the gross price (COMFORT); instead of priceDiscount
     * @return this builder
     */
    public Builder priceAdjustments(List<PriceAdjustment> priceAdjustments) {
      this.priceAdjustments = new ArrayList<>(priceAdjustments);
      return this;
    }

    /**
     * Adds one entry to {@link OrderLineDetails#priceAdjustments() priceAdjustments}.
     *
     * @param priceAdjustment entry to add
     * @return this builder
     */
    public Builder addPriceAdjustment(PriceAdjustment priceAdjustment) {
      this.priceAdjustments.add(priceAdjustment);
      return this;
    }

    /**
     * Sets {@link OrderLineDetails#requestedDeliveryPeriod() requestedDeliveryPeriod}.
     *
     * @param requestedDeliveryPeriod requested delivery period of the line (COMFORT), or null
     * @return this builder
     */
    public Builder requestedDeliveryPeriod(OrderPeriod requestedDeliveryPeriod) {
      this.requestedDeliveryPeriod = requestedDeliveryPeriod;
      return this;
    }

    /**
     * Sets {@link OrderLineDetails#requestedDespatchPeriod() requestedDespatchPeriod}.
     *
     * @param requestedDespatchPeriod requested despatch period of the line (COMFORT), or null
     * @return this builder
     */
    public Builder requestedDespatchPeriod(OrderPeriod requestedDespatchPeriod) {
      this.requestedDespatchPeriod = requestedDespatchPeriod;
      return this;
    }

    /**
     * Sets {@link OrderLineDetails#packageQuantityUnit() packageQuantityUnit}.
     *
     * @param packageQuantityUnit unit of the package quantity (COMFORT), requires it; or null
     * @return this builder
     */
    public Builder packageQuantityUnit(UnitCode packageQuantityUnit) {
      this.packageQuantityUnit = packageQuantityUnit;
      return this;
    }

    /**
     * Sets {@link OrderLineDetails#packageDimensions() packageDimensions}.
     *
     * @param packageDimensions dimensions of the packaging (COMFORT), or null
     * @return this builder
     */
    public Builder packageDimensions(PackageDimensions packageDimensions) {
      this.packageDimensions = packageDimensions;
      return this;
    }

    /**
     * Sets {@link OrderLineDetails#documents() documents}.
     *
     * @param documents documents referenced by the line (COMFORT)
     * @return this builder
     */
    public Builder documents(List<OrderDocument> documents) {
      this.documents = new ArrayList<>(documents);
      return this;
    }

    /**
     * Adds one entry to {@link OrderLineDetails#documents() documents}.
     *
     * @param document entry to add
     * @return this builder
     */
    public Builder addDocument(OrderDocument document) {
      this.documents.add(document);
      return this;
    }

    /**
     * Sets {@link OrderLineDetails#productDocuments() productDocuments}.
     *
     * @param productDocuments documents referenced by the product (COMFORT)
     * @return this builder
     */
    public Builder productDocuments(List<OrderDocument> productDocuments) {
      this.productDocuments = new ArrayList<>(productDocuments);
      return this;
    }

    /**
     * Adds one entry to {@link OrderLineDetails#productDocuments() productDocuments}.
     *
     * @param productDocument entry to add
     * @return this builder
     */
    public Builder addProductDocument(OrderDocument productDocument) {
      this.productDocuments.add(productDocument);
      return this;
    }

    /**
     * Constructs a new {@link OrderLineDetails} from the current values.
     *
     * @return the immutable value
     */
    public OrderLineDetails build() {
      return new OrderLineDetails(noteSubjectCode,
                                  priceAdjustments,
                                  requestedDeliveryPeriod,
                                  requestedDespatchPeriod,
                                  packageQuantityUnit,
                                  packageDimensions,
                                  documents,
                                  productDocuments);
    }
  }
}

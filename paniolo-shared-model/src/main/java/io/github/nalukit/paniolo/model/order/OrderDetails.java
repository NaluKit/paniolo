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

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Further header information of an Order-X order: indicators, business process, document name,
 * times of day, requested periods, delivery terms details, referenced documents, and the stated
 * totals of a response without lines. Fields marked COMFORT are rejected by the writer for the
 * profile BASIC.
 *
 * @param testIndicator             true for a test document, or null
 * @param copyIndicator             true for a copy, or null
 * @param businessProcessId         business process, e.g. A1; or null
 * @param documentName              name of the document, or null
 * @param issueTime                 time of day of the issue date, or null
 * @param requestedDeliveryTime     time of day of the requested delivery date, requires it; or null
 * @param requestedDespatchTime     time of day of the requested despatch date, requires it; or null
 * @param requestedDeliveryPeriod   requested delivery period, or null
 * @param requestedDespatchPeriod   requested despatch or pick-up period, or null
 * @param deliveryTermsDescription  description of the delivery terms (COMFORT), or null
 * @param deliveryTermsFunctionCode UNTDID 4055 delivery terms function, e.g. 7; or null
 * @param deliveryTermsLocationId   location of the delivery terms (COMFORT), or null
 * @param deliveryTermsLocationName name of that location (COMFORT), or null
 * @param referencedDocuments       further referenced documents with type codes (COMFORT)
 * @param statedTotals              totals stated by a response without lines, or null
 */
public record OrderDetails(Boolean testIndicator, Boolean copyIndicator, String businessProcessId, String documentName, LocalTime issueTime, LocalTime requestedDeliveryTime, LocalTime requestedDespatchTime, OrderPeriod requestedDeliveryPeriod,
                           OrderPeriod requestedDespatchPeriod, String deliveryTermsDescription, String deliveryTermsFunctionCode, String deliveryTermsLocationId, String deliveryTermsLocationName, List<OrderDocument> referencedDocuments,
                           OrderTotals statedTotals) {
  /**
   * Copies the list; a null list becomes an empty list.
   */
  public OrderDetails {
    referencedDocuments = referencedDocuments == null ?
                          List.of() :
                          List.copyOf(referencedDocuments);
  }

  /**
   * Starts named construction of a {@link OrderDetails} using the builder defaults.
   *
   * @return a new builder; not shared with other calls
   */
  public static Builder create() {
    return new Builder();
  }

  /**
   * Returns true if no detail is set; the order then carries no details.
   *
   * @return true if no detail is set
   */
  public boolean isEmpty() {
    return testIndicator == null &&
           copyIndicator == null &&
           businessProcessId == null &&
           documentName == null &&
           issueTime == null &&
           requestedDeliveryTime == null &&
           requestedDespatchTime == null &&
           requestedDeliveryPeriod == null &&
           requestedDespatchPeriod == null &&
           deliveryTermsDescription == null &&
           deliveryTermsFunctionCode == null &&
           deliveryTermsLocationId == null &&
           deliveryTermsLocationName == null &&
           referencedDocuments.isEmpty() &&
           statedTotals == null;
  }

  /**
   * Named construction of immutable {@link OrderDetails}.
   *
   * <p>Mutable construction state for {@link OrderDetails}. Use a separate builder per operation;
   * builders are not thread-safe. {@link #build()} constructs a new immutable value and applies
   * the checks of the model.
   */
  public static final class Builder {
    private Boolean             testIndicator;
    private Boolean             copyIndicator;
    private String              businessProcessId;
    private String              documentName;
    private LocalTime           issueTime;
    private LocalTime           requestedDeliveryTime;
    private LocalTime           requestedDespatchTime;
    private OrderPeriod         requestedDeliveryPeriod;
    private OrderPeriod         requestedDespatchPeriod;
    private String              deliveryTermsDescription;
    private String              deliveryTermsFunctionCode;
    private String              deliveryTermsLocationId;
    private String              deliveryTermsLocationName;
    private List<OrderDocument> referencedDocuments = new ArrayList<>();
    private OrderTotals         statedTotals;

    private Builder() {
    }

    /**
     * Sets {@link OrderDetails#testIndicator() testIndicator}.
     *
     * @param testIndicator true for a test document, or null
     * @return this builder
     */
    public Builder testIndicator(Boolean testIndicator) {
      this.testIndicator = testIndicator;
      return this;
    }

    /**
     * Sets {@link OrderDetails#copyIndicator() copyIndicator}.
     *
     * @param copyIndicator true for a copy, or null
     * @return this builder
     */
    public Builder copyIndicator(Boolean copyIndicator) {
      this.copyIndicator = copyIndicator;
      return this;
    }

    /**
     * Sets {@link OrderDetails#businessProcessId() businessProcessId}.
     *
     * @param businessProcessId business process, e.g. A1; or null
     * @return this builder
     */
    public Builder businessProcessId(String businessProcessId) {
      this.businessProcessId = businessProcessId;
      return this;
    }

    /**
     * Sets {@link OrderDetails#documentName() documentName}.
     *
     * @param documentName name of the document, or null
     * @return this builder
     */
    public Builder documentName(String documentName) {
      this.documentName = documentName;
      return this;
    }

    /**
     * Sets {@link OrderDetails#issueTime() issueTime}.
     *
     * @param issueTime time of day of the issue date, or null
     * @return this builder
     */
    public Builder issueTime(LocalTime issueTime) {
      this.issueTime = issueTime;
      return this;
    }

    /**
     * Sets {@link OrderDetails#requestedDeliveryTime() requestedDeliveryTime}.
     *
     * @param requestedDeliveryTime time of day of the requested delivery date, requires it; or null
     * @return this builder
     */
    public Builder requestedDeliveryTime(LocalTime requestedDeliveryTime) {
      this.requestedDeliveryTime = requestedDeliveryTime;
      return this;
    }

    /**
     * Sets {@link OrderDetails#requestedDespatchTime() requestedDespatchTime}.
     *
     * @param requestedDespatchTime time of day of the requested despatch date, requires it; or null
     * @return this builder
     */
    public Builder requestedDespatchTime(LocalTime requestedDespatchTime) {
      this.requestedDespatchTime = requestedDespatchTime;
      return this;
    }

    /**
     * Sets {@link OrderDetails#requestedDeliveryPeriod() requestedDeliveryPeriod}.
     *
     * @param requestedDeliveryPeriod requested delivery period, or null
     * @return this builder
     */
    public Builder requestedDeliveryPeriod(OrderPeriod requestedDeliveryPeriod) {
      this.requestedDeliveryPeriod = requestedDeliveryPeriod;
      return this;
    }

    /**
     * Sets {@link OrderDetails#requestedDespatchPeriod() requestedDespatchPeriod}.
     *
     * @param requestedDespatchPeriod requested despatch or pick-up period, or null
     * @return this builder
     */
    public Builder requestedDespatchPeriod(OrderPeriod requestedDespatchPeriod) {
      this.requestedDespatchPeriod = requestedDespatchPeriod;
      return this;
    }

    /**
     * Sets {@link OrderDetails#deliveryTermsDescription() deliveryTermsDescription}.
     *
     * @param deliveryTermsDescription description of the delivery terms (COMFORT), or null
     * @return this builder
     */
    public Builder deliveryTermsDescription(String deliveryTermsDescription) {
      this.deliveryTermsDescription = deliveryTermsDescription;
      return this;
    }

    /**
     * Sets {@link OrderDetails#deliveryTermsFunctionCode() deliveryTermsFunctionCode}.
     *
     * @param deliveryTermsFunctionCode UNTDID 4055 delivery terms function, e.g. 7; or null
     * @return this builder
     */
    public Builder deliveryTermsFunctionCode(String deliveryTermsFunctionCode) {
      this.deliveryTermsFunctionCode = deliveryTermsFunctionCode;
      return this;
    }

    /**
     * Sets {@link OrderDetails#deliveryTermsLocationId() deliveryTermsLocationId}.
     *
     * @param deliveryTermsLocationId location of the delivery terms (COMFORT), or null
     * @return this builder
     */
    public Builder deliveryTermsLocationId(String deliveryTermsLocationId) {
      this.deliveryTermsLocationId = deliveryTermsLocationId;
      return this;
    }

    /**
     * Sets {@link OrderDetails#deliveryTermsLocationName() deliveryTermsLocationName}.
     *
     * @param deliveryTermsLocationName name of that location (COMFORT), or null
     * @return this builder
     */
    public Builder deliveryTermsLocationName(String deliveryTermsLocationName) {
      this.deliveryTermsLocationName = deliveryTermsLocationName;
      return this;
    }

    /**
     * Sets {@link OrderDetails#referencedDocuments() referencedDocuments}.
     *
     * @param referencedDocuments further referenced documents with type codes (COMFORT)
     * @return this builder
     */
    public Builder referencedDocuments(List<OrderDocument> referencedDocuments) {
      this.referencedDocuments = new ArrayList<>(referencedDocuments);
      return this;
    }

    /**
     * Adds one entry to {@link OrderDetails#referencedDocuments() referencedDocuments}.
     *
     * @param referencedDocument entry to add
     * @return this builder
     */
    public Builder addReferencedDocument(OrderDocument referencedDocument) {
      this.referencedDocuments.add(referencedDocument);
      return this;
    }

    /**
     * Sets {@link OrderDetails#statedTotals() statedTotals}.
     *
     * @param statedTotals totals stated by a response without lines, or null
     * @return this builder
     */
    public Builder statedTotals(OrderTotals statedTotals) {
      this.statedTotals = statedTotals;
      return this;
    }

    /**
     * Constructs a new {@link OrderDetails} from the current values.
     *
     * @return the immutable value
     */
    public OrderDetails build() {
      return new OrderDetails(testIndicator,
                              copyIndicator,
                              businessProcessId,
                              documentName,
                              issueTime,
                              requestedDeliveryTime,
                              requestedDespatchTime,
                              requestedDeliveryPeriod,
                              requestedDespatchPeriod,
                              deliveryTermsDescription,
                              deliveryTermsFunctionCode,
                              deliveryTermsLocationId,
                              deliveryTermsLocationName,
                              referencedDocuments,
                              statedTotals);
    }
  }
}

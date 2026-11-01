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

/**
 * Optional document references BT-11 to BT-19; the buyer reference BT-10 lives on the invoice.
 *
 * @param projectReference         project reference (BT-11), or null
 * @param contractReference        contract reference (BT-12), or null
 * @param purchaseOrderReference   purchase order reference (BT-13), or null
 * @param salesOrderReference      sales order reference (BT-14), or null
 * @param receivingAdviceReference receiving advice reference (BT-15), or null
 * @param despatchAdviceReference  despatch advice reference (BT-16), or null
 * @param tenderOrLotReference     tender or lot reference (BT-17), or null
 * @param invoicedObjectId         invoiced object identifier (BT-18), or null
 * @param invoicedObjectSchemeId   invoiced object identifier scheme (BT-18-1), or null
 * @param buyerAccountingReference buyer accounting reference (BT-19), or null
 */
public record References(String projectReference, String contractReference, String purchaseOrderReference, String salesOrderReference, String receivingAdviceReference, String despatchAdviceReference, String tenderOrLotReference, String invoicedObjectId,
                         String invoicedObjectSchemeId, String buyerAccountingReference) {
  /**
   * Applies the checks of the model.
   *
   * @throws IllegalArgumentException if a value violates the checks of the model
   */
  public References {
    if (invoicedObjectSchemeId != null && invoicedObjectId == null) {
      throw new IllegalArgumentException("invoicedObjectSchemeId requires invoicedObjectId");
    }
  }

  /**
   * Starts named construction of a {@link References} using the builder defaults.
   *
   * @return a new builder; not shared with other calls
   */
  public static Builder create() {
    return new Builder();
  }

  /**
   * Named construction of immutable references.
   *
   * <p>Mutable construction state for {@link References}. Use a separate builder per operation;
   * builders are not thread-safe. {@link #build()} constructs a new immutable value and applies
   * the checks of the model. XML schema and business rule validation are separate operations.
   */
  public static final class Builder {
    private String projectReference;
    private String contractReference;
    private String purchaseOrderReference;
    private String salesOrderReference;
    private String receivingAdviceReference;
    private String despatchAdviceReference;
    private String tenderOrLotReference;
    private String invoicedObjectId;
    private String invoicedObjectSchemeId;
    private String buyerAccountingReference;

    /**
     * Creates a builder with the defaults of the model; usually obtained with {@link References#create()}.
     */
    public Builder() {
    }

    /**
     * BT-11.
     *
     * @param projectReference project reference (BT-11), or null
     * @return this builder
     */
    public Builder projectReference(String projectReference) {
      this.projectReference = projectReference;
      return this;
    }

    /**
     * BT-12.
     *
     * @param contractReference contract reference (BT-12), or null
     * @return this builder
     */
    public Builder contractReference(String contractReference) {
      this.contractReference = contractReference;
      return this;
    }

    /**
     * BT-13.
     *
     * @param purchaseOrderReference purchase order reference (BT-13), or null
     * @return this builder
     */
    public Builder purchaseOrderReference(String purchaseOrderReference) {
      this.purchaseOrderReference = purchaseOrderReference;
      return this;
    }

    /**
     * BT-14.
     *
     * @param salesOrderReference sales order reference (BT-14), or null
     * @return this builder
     */
    public Builder salesOrderReference(String salesOrderReference) {
      this.salesOrderReference = salesOrderReference;
      return this;
    }

    /**
     * BT-15.
     *
     * @param receivingAdviceReference receiving advice reference (BT-15), or null
     * @return this builder
     */
    public Builder receivingAdviceReference(String receivingAdviceReference) {
      this.receivingAdviceReference = receivingAdviceReference;
      return this;
    }

    /**
     * BT-16.
     *
     * @param despatchAdviceReference despatch advice reference (BT-16), or null
     * @return this builder
     */
    public Builder despatchAdviceReference(String despatchAdviceReference) {
      this.despatchAdviceReference = despatchAdviceReference;
      return this;
    }

    /**
     * BT-17.
     *
     * @param tenderOrLotReference tender or lot reference (BT-17), or null
     * @return this builder
     */
    public Builder tenderOrLotReference(String tenderOrLotReference) {
      this.tenderOrLotReference = tenderOrLotReference;
      return this;
    }

    /**
     * BT-18.
     *
     * @param invoicedObjectId invoiced object identifier (BT-18), or null
     * @return this builder
     */
    public Builder invoicedObjectId(String invoicedObjectId) {
      this.invoicedObjectId = invoicedObjectId;
      return this;
    }

    /**
     * BT-18-1, UNTDID 1153.
     *
     * @param invoicedObjectSchemeId invoiced object identifier scheme (BT-18-1), or null
     * @return this builder
     */
    public Builder invoicedObjectSchemeId(String invoicedObjectSchemeId) {
      this.invoicedObjectSchemeId = invoicedObjectSchemeId;
      return this;
    }

    /**
     * BT-19.
     *
     * @param buyerAccountingReference buyer accounting reference (BT-19), or null
     * @return this builder
     */
    public Builder buyerAccountingReference(String buyerAccountingReference) {
      this.buyerAccountingReference = buyerAccountingReference;
      return this;
    }

    /**
     * Constructs a new {@link References} from the current values.
     *
     * @return the constructed value after the model checks succeed
     */
    public References build() {
      return new References(projectReference,
                            contractReference,
                            purchaseOrderReference,
                            salesOrderReference,
                            receivingAdviceReference,
                            despatchAdviceReference,
                            tenderOrLotReference,
                            invoicedObjectId,
                            invoicedObjectSchemeId,
                            buyerAccountingReference);
    }
  }
}

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

import io.github.nalukit.paniolo.model.common.AdditionalSupportingDocument;
import io.github.nalukit.paniolo.model.common.DocumentAdjustment;
import io.github.nalukit.paniolo.model.common.InvoiceNote;
import io.github.nalukit.paniolo.model.common.InvoicePeriod;
import io.github.nalukit.paniolo.model.common.Party;
import io.github.nalukit.paniolo.model.common.validation.ModelChecks;
import io.github.nalukit.paniolo.model.common.value.CurrencyCode;
import io.github.nalukit.paniolo.model.common.value.PaymentMeansCode;
import io.github.nalukit.paniolo.model.extended.DocumentReference;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * Immutable Order-X order, order change, or order response. It shares parties, addresses, units,
 * and VAT categories with the invoice model. Fields marked COMFORT are rejected by the writer for
 * the profile BASIC; references carry no date, because Order-X has none.
 *
 * @param number                         order number
 * @param issueDate                      issue date
 * @param typeCode                       order, order change, or order response; order by default
 * @param profile                        Order-X profile; BASIC by default
 * @param purposeCode                    UNTDID 1225 message function, e.g. 9 original or 1 cancellation; or null
 * @param requestedResponseTypeCode      UNTDID 4343 response type the buyer asks for, e.g. AC; or null
 * @param statusCode                     UNTDID 4343 response status of an order response, e.g. AP accepted; or null
 * @param currencyCode                   order currency; EUR by default
 * @param buyerReference                 buyer reference, or null
 * @param seller                         seller
 * @param buyer                          buyer
 * @param invoicee                       party to invoice (COMFORT), or null
 * @param shipTo                         deliver-to party, or null
 * @param shipFrom                       ship-from party, or null
 * @param deliveryTermsCode              delivery terms, e.g. an Incoterms code; or null
 * @param sellerOrderReference           seller's order number (COMFORT), or null
 * @param buyerOrderReference            buyer's order, e.g. answered by an order response; or null
 * @param quotationReference             quotation, or null
 * @param contractReference              contract, or null
 * @param blanketOrderReference          blanket order, or null
 * @param previousOrderReference         previous order, e.g. of an order change; or null
 * @param previousOrderChangeReference   previous order change, or null
 * @param previousOrderResponseReference previous order response, or null
 * @param requestedDeliveryDate          requested delivery date, or null
 * @param requestedDespatchDate          requested despatch date, or null
 * @param notes                          notes with optional subject code
 * @param paymentMeansCode               payment means (COMFORT), or null
 * @param paymentTerms                   payment terms (COMFORT), or null
 * @param adjustments                    document allowances and charges without exemption (COMFORT)
 * @param buyerAccountingReference       buyer accounting reference, or null
 * @param lines                          order lines; an order response may have none
 * @param effectivePeriod                period the order is effective (COMFORT), or null
 * @param buyerRequisitioner             party requesting the goods within the buyer (COMFORT), or null
 * @param requisitionReference           requisition (COMFORT), or null
 * @param catalogueReference             catalogue (COMFORT), or null
 * @param additionalDocuments            further documents, written with type code 916 (COMFORT)
 * @param projectReference               project identifier (COMFORT), with projectName; or null
 * @param projectName                    project name (COMFORT), with projectReference; or null
 * @param paymentMeansText               payment means text (COMFORT), or null
 * @param details                        further Order-X header information, or null
 */
public record Order(String number, LocalDate issueDate, OrderTypeCode typeCode, OrderXProfile profile, String purposeCode, String requestedResponseTypeCode, String statusCode, CurrencyCode currencyCode, String buyerReference, Party seller, Party buyer,
                    Party invoicee, Party shipTo, Party shipFrom, String deliveryTermsCode, DocumentReference sellerOrderReference, DocumentReference buyerOrderReference, DocumentReference quotationReference, DocumentReference contractReference,
                    DocumentReference blanketOrderReference, DocumentReference previousOrderReference, DocumentReference previousOrderChangeReference, DocumentReference previousOrderResponseReference, LocalDate requestedDeliveryDate,
                    LocalDate requestedDespatchDate, List<InvoiceNote> notes, PaymentMeansCode paymentMeansCode, String paymentTerms, List<DocumentAdjustment> adjustments, String buyerAccountingReference, List<OrderLine> lines,
                    InvoicePeriod effectivePeriod, Party buyerRequisitioner, DocumentReference requisitionReference, DocumentReference catalogueReference, List<AdditionalSupportingDocument> additionalDocuments, String projectReference, String projectName,
                    String paymentMeansText, OrderDetails details) {
  /**
   * Applies the checks of the model and copies the lists.
   *
   * @throws NullPointerException     if a required value is null
   * @throws IllegalArgumentException if a value violates the checks of the model
   */
  public Order {
    ModelChecks.required(number,
                         "order number");
    Objects.requireNonNull(issueDate,
                           "issueDate");
    Objects.requireNonNull(typeCode,
                           "typeCode");
    Objects.requireNonNull(profile,
                           "profile");
    Objects.requireNonNull(currencyCode,
                           "currencyCode");
    Party.requireAddress(Objects.requireNonNull(seller,
                                                "seller"),
                         "seller");
    Party.requireAddress(Objects.requireNonNull(buyer,
                                                "buyer"),
                         "buyer");
    Party.requireAddress(invoicee,
                         "invoicee");
    Party.requireAddress(shipTo,
                         "ship-to party");
    Party.requireAddress(shipFrom,
                         "ship-from party");
    Party.requireAddress(buyerRequisitioner,
                         "buyer requisitioner");
    for (DocumentReference reference : new DocumentReference[] { sellerOrderReference,
                                                                 buyerOrderReference,
                                                                 quotationReference,
                                                                 contractReference,
                                                                 blanketOrderReference,
                                                                 previousOrderReference,
                                                                 previousOrderChangeReference,
                                                                 previousOrderResponseReference,
                                                                 requisitionReference,
                                                                 catalogueReference }) {
      if (reference != null && reference.issueDate() != null) {
        throw new IllegalArgumentException("Order-X references carry no date: " + reference.id());
      }
    }
    if (adjustments != null) {
      for (DocumentAdjustment adjustment : adjustments) {
        if (adjustment.tax()
                      .exemptionReason() != null ||
            adjustment.tax()
                      .exemptionReasonCode() != null) {
          throw new IllegalArgumentException("Order-X has no VAT exemption reason");
        }
      }
    }
    if (notes != null &&
        notes.stream()
             .anyMatch(note -> note.contentCode() != null)) {
      throw new IllegalArgumentException("Order-X notes have no content code");
    }
    var ids = new HashSet<String>();
    for (OrderLine line : lines == null ?
                          List.<OrderLine>of() :
                          lines) {
      if (!ids.add(line.id())) {
        throw new IllegalArgumentException("Duplicate order line id: " + line.id());
      }
    }
    if ((projectReference == null) != (projectName == null)) {
      throw new IllegalArgumentException("Order-X needs project reference and project name together");
    }
    if (paymentMeansText != null && paymentMeansCode == null) {
      throw new IllegalArgumentException("A payment means text requires the payment means code");
    }
    if (details != null && details.isEmpty()) {
      details = null;
    }
    if (details != null) {
      if (details.requestedDeliveryTime() != null && requestedDeliveryDate == null) {
        throw new IllegalArgumentException("A requested delivery time requires the requested delivery date");
      }
      if (details.requestedDespatchTime() != null && requestedDespatchDate == null) {
        throw new IllegalArgumentException("A requested despatch time requires the requested despatch date");
      }
      // Totals are calculated from the lines; only a response without lines states its own.
      if (details.statedTotals() != null && lines != null && !lines.isEmpty()) {
        throw new IllegalArgumentException("Stated totals are only allowed for an order without lines");
      }
    }
    notes               = notes == null ?
                          List.of() :
                          List.copyOf(notes);
    adjustments         = adjustments == null ?
                          List.of() :
                          List.copyOf(adjustments);
    lines               = lines == null ?
                          List.of() :
                          List.copyOf(lines);
    additionalDocuments = additionalDocuments == null ?
                          List.of() :
                          List.copyOf(additionalDocuments);
  }

  /**
   * Starts named construction of a {@link Order} using the builder defaults.
   *
   * @return a new builder; not shared with other calls
   */
  public static Builder create() {
    return new Builder();
  }

  /**
   * Sums of lines, allowances, charges, and, if every line has a VAT category, VAT.
   *
   * @return the totals calculated from the order
   */
  public OrderTotals totals() {
    return OrderTotals.calculate(this);
  }

  /**
   * Named construction of an immutable order.
   *
   * <p>Mutable construction state for {@link Order}. Use a separate builder per operation;
   * builders are not thread-safe. {@link #build()} constructs a new immutable value and applies
   * the checks of the model. XML schema and business rule validation are separate operations.
   */
  public static final class Builder {
    private String                             number;
    private LocalDate                          issueDate;
    private OrderTypeCode                      typeCode            = OrderTypeCode.ORDER;
    private OrderXProfile                      profile             = OrderXProfile.BASIC;
    private String                             purposeCode;
    private String                             requestedResponseTypeCode;
    private String                             statusCode;
    private CurrencyCode                       currencyCode        = CurrencyCode.EUR;
    private String                             buyerReference;
    private Party                              seller;
    private Party                              buyer;
    private Party                              invoicee;
    private Party                              shipTo;
    private Party                              shipFrom;
    private String                             deliveryTermsCode;
    private DocumentReference                  sellerOrderReference;
    private DocumentReference                  buyerOrderReference;
    private DocumentReference                  quotationReference;
    private DocumentReference                  contractReference;
    private DocumentReference                  blanketOrderReference;
    private DocumentReference                  previousOrderReference;
    private DocumentReference                  previousOrderChangeReference;
    private DocumentReference                  previousOrderResponseReference;
    private LocalDate                          requestedDeliveryDate;
    private LocalDate                          requestedDespatchDate;
    private List<InvoiceNote>                  notes               = new ArrayList<>();
    private PaymentMeansCode                   paymentMeansCode;
    private String                             paymentTerms;
    private List<DocumentAdjustment>           adjustments         = new ArrayList<>();
    private String                             buyerAccountingReference;
    private List<OrderLine>                    lines               = new ArrayList<>();
    private InvoicePeriod                      effectivePeriod;
    private Party                              buyerRequisitioner;
    private DocumentReference                  requisitionReference;
    private DocumentReference                  catalogueReference;
    private List<AdditionalSupportingDocument> additionalDocuments = new ArrayList<>();
    private String                             projectReference;
    private String                             projectName;
    private String                             paymentMeansText;
    private OrderDetails                       details;

    /**
     * Creates a builder with the defaults of the model; usually obtained with {@link Order#create()}.
     */
    public Builder() {
    }

    /**
     * Sets {@link Order#number() number}.
     *
     * @param number order number
     * @return this builder
     */
    public Builder number(String number) {
      this.number = number;
      return this;
    }

    /**
     * Sets {@link Order#issueDate() issueDate}.
     *
     * @param issueDate issue date
     * @return this builder
     */
    public Builder issueDate(LocalDate issueDate) {
      this.issueDate = issueDate;
      return this;
    }

    /**
     * Sets {@link Order#typeCode() typeCode}.
     *
     * @param typeCode order, order change, or order response; order by default
     * @return this builder
     */
    public Builder typeCode(OrderTypeCode typeCode) {
      this.typeCode = typeCode;
      return this;
    }

    /**
     * Sets {@link Order#profile() profile}.
     *
     * @param profile Order-X profile; BASIC by default
     * @return this builder
     */
    public Builder profile(OrderXProfile profile) {
      this.profile = profile;
      return this;
    }

    /**
     * Sets {@link Order#purposeCode() purposeCode}.
     *
     * @param purposeCode UNTDID 1225 message function, e.g. 9 original or 1 cancellation; or null
     * @return this builder
     */
    public Builder purposeCode(String purposeCode) {
      this.purposeCode = purposeCode;
      return this;
    }

    /**
     * Sets {@link Order#requestedResponseTypeCode() requestedResponseTypeCode}.
     *
     * @param requestedResponseTypeCode UNTDID 4343 response type the buyer asks for, e.g. AC; or null
     * @return this builder
     */
    public Builder requestedResponseTypeCode(String requestedResponseTypeCode) {
      this.requestedResponseTypeCode = requestedResponseTypeCode;
      return this;
    }

    /**
     * Sets {@link Order#statusCode() statusCode}.
     *
     * @param statusCode UNTDID 4343 response status of an order response, e.g. AP accepted; or null
     * @return this builder
     */
    public Builder statusCode(String statusCode) {
      this.statusCode = statusCode;
      return this;
    }

    /**
     * Sets {@link Order#currencyCode() currencyCode}.
     *
     * @param currencyCode order currency; EUR by default
     * @return this builder
     */
    public Builder currencyCode(CurrencyCode currencyCode) {
      this.currencyCode = currencyCode;
      return this;
    }

    /**
     * Sets {@link Order#buyerReference() buyerReference}.
     *
     * @param buyerReference buyer reference, or null
     * @return this builder
     */
    public Builder buyerReference(String buyerReference) {
      this.buyerReference = buyerReference;
      return this;
    }

    /**
     * Sets {@link Order#seller() seller}.
     *
     * @param seller seller
     * @return this builder
     */
    public Builder seller(Party seller) {
      this.seller = seller;
      return this;
    }

    /**
     * Sets {@link Order#buyer() buyer}.
     *
     * @param buyer buyer
     * @return this builder
     */
    public Builder buyer(Party buyer) {
      this.buyer = buyer;
      return this;
    }

    /**
     * Sets {@link Order#invoicee() invoicee}.
     *
     * @param invoicee party to invoice (COMFORT), or null
     * @return this builder
     */
    public Builder invoicee(Party invoicee) {
      this.invoicee = invoicee;
      return this;
    }

    /**
     * Sets {@link Order#shipTo() shipTo}.
     *
     * @param shipTo deliver-to party, or null
     * @return this builder
     */
    public Builder shipTo(Party shipTo) {
      this.shipTo = shipTo;
      return this;
    }

    /**
     * Sets {@link Order#shipFrom() shipFrom}.
     *
     * @param shipFrom ship-from party, or null
     * @return this builder
     */
    public Builder shipFrom(Party shipFrom) {
      this.shipFrom = shipFrom;
      return this;
    }

    /**
     * Sets {@link Order#deliveryTermsCode() deliveryTermsCode}.
     *
     * @param deliveryTermsCode delivery terms, e.g. an Incoterms code; or null
     * @return this builder
     */
    public Builder deliveryTermsCode(String deliveryTermsCode) {
      this.deliveryTermsCode = deliveryTermsCode;
      return this;
    }

    /**
     * Sets {@link Order#sellerOrderReference() sellerOrderReference}.
     *
     * @param sellerOrderReference seller's order number (COMFORT), or null
     * @return this builder
     */
    public Builder sellerOrderReference(DocumentReference sellerOrderReference) {
      this.sellerOrderReference = sellerOrderReference;
      return this;
    }

    /**
     * Sets {@link Order#buyerOrderReference() buyerOrderReference}.
     *
     * @param buyerOrderReference buyer's order, e.g. answered by an order response; or null
     * @return this builder
     */
    public Builder buyerOrderReference(DocumentReference buyerOrderReference) {
      this.buyerOrderReference = buyerOrderReference;
      return this;
    }

    /**
     * Sets {@link Order#quotationReference() quotationReference}.
     *
     * @param quotationReference quotation, or null
     * @return this builder
     */
    public Builder quotationReference(DocumentReference quotationReference) {
      this.quotationReference = quotationReference;
      return this;
    }

    /**
     * Sets {@link Order#contractReference() contractReference}.
     *
     * @param contractReference contract, or null
     * @return this builder
     */
    public Builder contractReference(DocumentReference contractReference) {
      this.contractReference = contractReference;
      return this;
    }

    /**
     * Sets {@link Order#blanketOrderReference() blanketOrderReference}.
     *
     * @param blanketOrderReference blanket order, or null
     * @return this builder
     */
    public Builder blanketOrderReference(DocumentReference blanketOrderReference) {
      this.blanketOrderReference = blanketOrderReference;
      return this;
    }

    /**
     * Sets {@link Order#previousOrderReference() previousOrderReference}.
     *
     * @param previousOrderReference previous order, e.g. of an order change; or null
     * @return this builder
     */
    public Builder previousOrderReference(DocumentReference previousOrderReference) {
      this.previousOrderReference = previousOrderReference;
      return this;
    }

    /**
     * Sets {@link Order#previousOrderChangeReference() previousOrderChangeReference}.
     *
     * @param previousOrderChangeReference previous order change, or null
     * @return this builder
     */
    public Builder previousOrderChangeReference(DocumentReference previousOrderChangeReference) {
      this.previousOrderChangeReference = previousOrderChangeReference;
      return this;
    }

    /**
     * Sets {@link Order#previousOrderResponseReference() previousOrderResponseReference}.
     *
     * @param previousOrderResponseReference previous order response, or null
     * @return this builder
     */
    public Builder previousOrderResponseReference(DocumentReference previousOrderResponseReference) {
      this.previousOrderResponseReference = previousOrderResponseReference;
      return this;
    }

    /**
     * Sets {@link Order#requestedDeliveryDate() requestedDeliveryDate}.
     *
     * @param requestedDeliveryDate requested delivery date, or null
     * @return this builder
     */
    public Builder requestedDeliveryDate(LocalDate requestedDeliveryDate) {
      this.requestedDeliveryDate = requestedDeliveryDate;
      return this;
    }

    /**
     * Sets {@link Order#requestedDespatchDate() requestedDespatchDate}.
     *
     * @param requestedDespatchDate requested despatch date, or null
     * @return this builder
     */
    public Builder requestedDespatchDate(LocalDate requestedDespatchDate) {
      this.requestedDespatchDate = requestedDespatchDate;
      return this;
    }

    /**
     * Adds a value to {@link Order#notes() notes}.
     *
     * @param value the invoice note to add
     * @return this builder
     */
    public Builder addNote(InvoiceNote value) {
      this.notes.add(value);
      return this;
    }

    /**
     * Sets {@link Order#paymentMeansCode() paymentMeansCode}.
     *
     * @param paymentMeansCode payment means (COMFORT), or null
     * @return this builder
     */
    public Builder paymentMeansCode(PaymentMeansCode paymentMeansCode) {
      this.paymentMeansCode = paymentMeansCode;
      return this;
    }

    /**
     * Sets {@link Order#paymentTerms() paymentTerms}.
     *
     * @param paymentTerms payment terms (COMFORT), or null
     * @return this builder
     */
    public Builder paymentTerms(String paymentTerms) {
      this.paymentTerms = paymentTerms;
      return this;
    }

    /**
     * Adds a value to {@link Order#adjustments() adjustments}.
     *
     * @param value the document adjustment to add
     * @return this builder
     */
    public Builder addAdjustment(DocumentAdjustment value) {
      this.adjustments.add(value);
      return this;
    }

    /**
     * Sets {@link Order#buyerAccountingReference() buyerAccountingReference}.
     *
     * @param buyerAccountingReference buyer accounting reference, or null
     * @return this builder
     */
    public Builder buyerAccountingReference(String buyerAccountingReference) {
      this.buyerAccountingReference = buyerAccountingReference;
      return this;
    }

    /**
     * Adds a value to {@link Order#lines() lines}.
     *
     * @param value the order line to add
     * @return this builder
     */
    public Builder addLine(OrderLine value) {
      this.lines.add(value);
      return this;
    }

    /**
     * Sets {@link Order#effectivePeriod() effectivePeriod}.
     *
     * @param effectivePeriod period the order is effective (COMFORT), or null
     * @return this builder
     */
    public Builder effectivePeriod(InvoicePeriod effectivePeriod) {
      this.effectivePeriod = effectivePeriod;
      return this;
    }

    /**
     * Sets {@link Order#buyerRequisitioner() buyerRequisitioner}.
     *
     * @param buyerRequisitioner party requesting the goods within the buyer (COMFORT), or null
     * @return this builder
     */
    public Builder buyerRequisitioner(Party buyerRequisitioner) {
      this.buyerRequisitioner = buyerRequisitioner;
      return this;
    }

    /**
     * Sets {@link Order#requisitionReference() requisitionReference}.
     *
     * @param requisitionReference requisition (COMFORT), or null
     * @return this builder
     */
    public Builder requisitionReference(DocumentReference requisitionReference) {
      this.requisitionReference = requisitionReference;
      return this;
    }

    /**
     * Sets {@link Order#catalogueReference() catalogueReference}.
     *
     * @param catalogueReference catalogue (COMFORT), or null
     * @return this builder
     */
    public Builder catalogueReference(DocumentReference catalogueReference) {
      this.catalogueReference = catalogueReference;
      return this;
    }

    /**
     * Adds a value to {@link Order#additionalDocuments() additionalDocuments}.
     *
     * @param value the additional supporting document to add
     * @return this builder
     */
    public Builder addAdditionalDocument(AdditionalSupportingDocument value) {
      this.additionalDocuments.add(value);
      return this;
    }

    /**
     * Sets {@link Order#projectReference() projectReference}.
     *
     * @param projectReference project identifier (COMFORT), with projectName; or null
     * @return this builder
     */
    public Builder projectReference(String projectReference) {
      this.projectReference = projectReference;
      return this;
    }

    /**
     * Sets {@link Order#projectName() projectName}.
     *
     * @param projectName project name (COMFORT), with projectReference; or null
     * @return this builder
     */
    public Builder projectName(String projectName) {
      this.projectName = projectName;
      return this;
    }

    /**
     * Sets {@link Order#paymentMeansText() paymentMeansText}.
     *
     * @param paymentMeansText payment means text (COMFORT), or null
     * @return this builder
     */
    public Builder paymentMeansText(String paymentMeansText) {
      this.paymentMeansText = paymentMeansText;
      return this;
    }

    /**
     * Sets {@link Order#details() details}.
     *
     * @param details further Order-X header information, or null
     * @return this builder
     */
    public Builder details(OrderDetails details) {
      this.details = details;
      return this;
    }

    /**
     * Constructs a new {@link Order} from the current values.
     *
     * @return the constructed value after the model checks succeed
     */
    public Order build() {
      return new Order(number,
                       issueDate,
                       typeCode,
                       profile,
                       purposeCode,
                       requestedResponseTypeCode,
                       statusCode,
                       currencyCode,
                       buyerReference,
                       seller,
                       buyer,
                       invoicee,
                       shipTo,
                       shipFrom,
                       deliveryTermsCode,
                       sellerOrderReference,
                       buyerOrderReference,
                       quotationReference,
                       contractReference,
                       blanketOrderReference,
                       previousOrderReference,
                       previousOrderChangeReference,
                       previousOrderResponseReference,
                       requestedDeliveryDate,
                       requestedDespatchDate,
                       notes,
                       paymentMeansCode,
                       paymentTerms,
                       adjustments,
                       buyerAccountingReference,
                       lines,
                       effectivePeriod,
                       buyerRequisitioner,
                       requisitionReference,
                       catalogueReference,
                       additionalDocuments,
                       projectReference,
                       projectName,
                       paymentMeansText,
                       details);
    }
  }
}

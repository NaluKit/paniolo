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
import io.github.nalukit.paniolo.model.common.value.Country;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Header information of the Factur-X / ZUGFeRD profile EXTENDED that EN 16931 does not know.
 * Only the CII writer with the EXTENDED specification identifier writes it; other profiles and
 * UBL reject an invoice that carries it.
 *
 * @param salesAgent                      sales agent of the seller
 * @param buyerAgent                      agent buying on behalf of the buyer
 * @param buyerTaxRepresentative          tax representative of the buyer, with VAT identifier
 * @param productEndUser                  end user of the products
 * @param shipFrom                        party the goods are shipped from
 * @param ultimateShipTo                  final recipient, when the deliver-to party forwards the goods
 * @param invoicer                        party issuing the invoice on behalf of the seller
 * @param invoicee                        party receiving the invoice on behalf of the buyer
 * @param payer                           party paying the invoice on behalf of the buyer
 * @param deliveryTermsCode               delivery terms, e.g. an Incoterms code such as {@code FCA}
 * @param quotationReference              quotation the invoice is based on
 * @param deliveryNoteReference           delivery note
 * @param ultimateCustomerOrderReferences orders of the ultimate customer
 * @param paymentTerms                    additional payment terms after the payment terms of the invoice (BT-20, BT-9)
 * @param logisticsServiceCharges         freight, packaging, and similar charges
 * @param advancePayments                 advance payments already received
 * @param documentName                    document name, e.g. "Invoice", or null
 * @param languageId                      language of the document, ISO 639-2 code such as {@code deu}, or null
 * @param copyIndicator                   true if the document is a copy, or null
 * @param testIndicator                   true if the document is a test, or null
 * @param effectiveDate                   date the document is effective, or null
 * @param invoiceIssuerReference          invoice number assigned by the invoicer, or null
 * @param transportModeCode               UN/ECE Rec. 19 transport mode, or null
 * @param deliveryTermsLocationName       place of the delivery terms, or null
 * @param deliveryTermsLocationCountry    country of the delivery terms, or null
 * @param billingPeriodDescription        description of the invoicing period (BG-14), or null
 * @param buyerAccountingTypeCode         UNTDID 4437 type of the buyer accounting reference (BT-19), or null
 * @param payerAccountName                name of the debited account of a direct debit, or null
 * @param payerBic                        BIC of the debited account of a direct debit, or null
 * @param taxExchangeRate                 rate from the invoice to the VAT accounting currency, or null
 * @param financialAdjustments            adjustments of the amount due
 * @param additionalAccountingAccounts    further buyer accounting accounts after the buyer
 *                                        accounting reference (BT-19) and its type, in document order
 */
public record ExtendedInformation(Party salesAgent, Party buyerAgent, Party buyerTaxRepresentative, Party productEndUser, Party shipFrom, Party ultimateShipTo, Party invoicer, Party invoicee, Party payer, String deliveryTermsCode,
                                  DocumentReference quotationReference, DocumentReference deliveryNoteReference, List<DocumentReference> ultimateCustomerOrderReferences, List<PaymentTerm> paymentTerms, List<LogisticsServiceCharge> logisticsServiceCharges,
                                  List<AdvancePayment> advancePayments, String documentName, String languageId, Boolean copyIndicator, Boolean testIndicator, LocalDate effectiveDate, String invoiceIssuerReference, String transportModeCode,
                                  String deliveryTermsLocationName, Country deliveryTermsLocationCountry, String billingPeriodDescription, String buyerAccountingTypeCode, String payerAccountName, String payerBic, TaxExchangeRate taxExchangeRate,
                                  List<FinancialAdjustment> financialAdjustments, List<AccountingAccount> additionalAccountingAccounts) {
  /**
   * Applies the checks of the model and copies the lists.
   *
   * @throws IllegalArgumentException if a value violates the checks of the model
   */
  public ExtendedInformation {
    Party.requireAddress(salesAgent,
                         "salesAgent");
    Party.requireAddress(buyerAgent,
                         "buyerAgent");
    Party.requireAddress(buyerTaxRepresentative,
                         "buyerTaxRepresentative");
    Party.requireAddress(productEndUser,
                         "productEndUser");
    Party.requireAddress(shipFrom,
                         "shipFrom");
    Party.requireAddress(ultimateShipTo,
                         "ultimateShipTo");
    Party.requireAddress(invoicer,
                         "invoicer");
    Party.requireAddress(invoicee,
                         "invoicee");
    Party.requireAddress(payer,
                         "payer");
    if (buyerTaxRepresentative != null && buyerTaxRepresentative.vatId() == null) {
      throw new IllegalArgumentException("The buyer's tax representative requires a VAT identifier");
    }
    if (deliveryTermsCode != null) {
      ModelChecks.required(deliveryTermsCode,
                           "delivery terms code");
    }
    ultimateCustomerOrderReferences = List.copyOf(ultimateCustomerOrderReferences);
    paymentTerms                    = List.copyOf(paymentTerms);
    logisticsServiceCharges         = List.copyOf(logisticsServiceCharges);
    advancePayments                 = List.copyOf(advancePayments);
    financialAdjustments            = List.copyOf(financialAdjustments);
    // Missing in JSON written before further accounting accounts were added.
    additionalAccountingAccounts = additionalAccountingAccounts == null ?
                                   List.of() :
                                   List.copyOf(additionalAccountingAccounts);
  }

  /**
   * Starts named construction of a {@link ExtendedInformation} using the builder defaults.
   *
   * @return a new builder; not shared with other calls
   */
  public static Builder create() {
    return new Builder();
  }

  /**
   * Returns true if no EXTENDED information is set.
   *
   * @return true if no information is set
   */
  public boolean isEmpty() {
    return equals(create().build());
  }

  /**
   * Named construction of immutable EXTENDED information.
   *
   * <p>Mutable construction state for {@link ExtendedInformation}. Use a separate builder per operation;
   * builders are not thread-safe. {@link #build()} constructs a new immutable value and applies
   * the checks of the model. XML schema and business rule validation are separate operations.
   */
  public static final class Builder {
    private Party                        salesAgent;
    private Party                        buyerAgent;
    private Party                        buyerTaxRepresentative;
    private Party                        productEndUser;
    private Party                        shipFrom;
    private Party                        ultimateShipTo;
    private Party                        invoicer;
    private Party                        invoicee;
    private Party                        payer;
    private String                       deliveryTermsCode;
    private DocumentReference            quotationReference;
    private DocumentReference            deliveryNoteReference;
    private List<DocumentReference>      ultimateCustomerOrderReferences = new ArrayList<>();
    private List<PaymentTerm>            paymentTerms                    = new ArrayList<>();
    private List<LogisticsServiceCharge> logisticsServiceCharges         = new ArrayList<>();
    private List<AdvancePayment>         advancePayments                 = new ArrayList<>();
    private String                       documentName;
    private String                       languageId;
    private Boolean                      copyIndicator;
    private Boolean                      testIndicator;
    private LocalDate                    effectiveDate;
    private String                       invoiceIssuerReference;
    private String                       transportModeCode;
    private String                       deliveryTermsLocationName;
    private Country                      deliveryTermsLocationCountry;
    private String                       billingPeriodDescription;
    private String                       buyerAccountingTypeCode;
    private String                       payerAccountName;
    private String                       payerBic;
    private TaxExchangeRate              taxExchangeRate;
    private List<FinancialAdjustment>    financialAdjustments            = new ArrayList<>();
    private List<AccountingAccount>      additionalAccountingAccounts    = new ArrayList<>();

    /**
     * Creates a builder with the defaults of the model; usually obtained with {@link ExtendedInformation#create()}.
     */
    public Builder() {
    }

    /**
     * Sets {@link ExtendedInformation#salesAgent() salesAgent}.
     *
     * @param salesAgent sales agent of the seller
     * @return this builder
     */
    public Builder salesAgent(Party salesAgent) {
      this.salesAgent = salesAgent;
      return this;
    }

    /**
     * Sets {@link ExtendedInformation#buyerAgent() buyerAgent}.
     *
     * @param buyerAgent agent buying on behalf of the buyer
     * @return this builder
     */
    public Builder buyerAgent(Party buyerAgent) {
      this.buyerAgent = buyerAgent;
      return this;
    }

    /**
     * Sets {@link ExtendedInformation#buyerTaxRepresentative() buyerTaxRepresentative}.
     *
     * @param buyerTaxRepresentative tax representative of the buyer, with VAT identifier
     * @return this builder
     */
    public Builder buyerTaxRepresentative(Party buyerTaxRepresentative) {
      this.buyerTaxRepresentative = buyerTaxRepresentative;
      return this;
    }

    /**
     * Sets {@link ExtendedInformation#productEndUser() productEndUser}.
     *
     * @param productEndUser end user of the products
     * @return this builder
     */
    public Builder productEndUser(Party productEndUser) {
      this.productEndUser = productEndUser;
      return this;
    }

    /**
     * Sets {@link ExtendedInformation#shipFrom() shipFrom}.
     *
     * @param shipFrom party the goods are shipped from
     * @return this builder
     */
    public Builder shipFrom(Party shipFrom) {
      this.shipFrom = shipFrom;
      return this;
    }

    /**
     * Sets {@link ExtendedInformation#ultimateShipTo() ultimateShipTo}.
     *
     * @param ultimateShipTo final recipient, when the deliver-to party forwards the goods
     * @return this builder
     */
    public Builder ultimateShipTo(Party ultimateShipTo) {
      this.ultimateShipTo = ultimateShipTo;
      return this;
    }

    /**
     * Sets {@link ExtendedInformation#invoicer() invoicer}.
     *
     * @param invoicer party issuing the invoice on behalf of the seller
     * @return this builder
     */
    public Builder invoicer(Party invoicer) {
      this.invoicer = invoicer;
      return this;
    }

    /**
     * Sets {@link ExtendedInformation#invoicee() invoicee}.
     *
     * @param invoicee party receiving the invoice on behalf of the buyer
     * @return this builder
     */
    public Builder invoicee(Party invoicee) {
      this.invoicee = invoicee;
      return this;
    }

    /**
     * Sets {@link ExtendedInformation#payer() payer}.
     *
     * @param payer party paying the invoice on behalf of the buyer
     * @return this builder
     */
    public Builder payer(Party payer) {
      this.payer = payer;
      return this;
    }

    /**
     * Sets {@link ExtendedInformation#deliveryTermsCode() deliveryTermsCode}.
     *
     * @param deliveryTermsCode delivery terms, e.g. an Incoterms code such as {@code FCA}
     * @return this builder
     */
    public Builder deliveryTermsCode(String deliveryTermsCode) {
      this.deliveryTermsCode = deliveryTermsCode;
      return this;
    }

    /**
     * Sets {@link ExtendedInformation#quotationReference() quotationReference}.
     *
     * @param quotationReference quotation the invoice is based on
     * @return this builder
     */
    public Builder quotationReference(DocumentReference quotationReference) {
      this.quotationReference = quotationReference;
      return this;
    }

    /**
     * Sets {@link ExtendedInformation#deliveryNoteReference() deliveryNoteReference}.
     *
     * @param deliveryNoteReference delivery note
     * @return this builder
     */
    public Builder deliveryNoteReference(DocumentReference deliveryNoteReference) {
      this.deliveryNoteReference = deliveryNoteReference;
      return this;
    }

    /**
     * Adds a value to {@link ExtendedInformation#ultimateCustomerOrderReferences() ultimateCustomerOrderReferences}.
     *
     * @param value the document reference to add
     * @return this builder
     */
    public Builder addUltimateCustomerOrderReference(DocumentReference value) {
      this.ultimateCustomerOrderReferences.add(value);
      return this;
    }

    /**
     * Adds a value to {@link ExtendedInformation#paymentTerms() paymentTerms}.
     *
     * @param value the payment term to add
     * @return this builder
     */
    public Builder addPaymentTerm(PaymentTerm value) {
      this.paymentTerms.add(value);
      return this;
    }

    /**
     * Adds a value to {@link ExtendedInformation#logisticsServiceCharges() logisticsServiceCharges}.
     *
     * @param value the logistics service charge to add
     * @return this builder
     */
    public Builder addLogisticsServiceCharge(LogisticsServiceCharge value) {
      this.logisticsServiceCharges.add(value);
      return this;
    }

    /**
     * Adds a value to {@link ExtendedInformation#advancePayments() advancePayments}.
     *
     * @param value the advance payment to add
     * @return this builder
     */
    public Builder addAdvancePayment(AdvancePayment value) {
      this.advancePayments.add(value);
      return this;
    }

    /**
     * Sets {@link ExtendedInformation#documentName() documentName}.
     *
     * @param documentName document name, e.g. "Invoice", or null
     * @return this builder
     */
    public Builder documentName(String documentName) {
      this.documentName = documentName;
      return this;
    }

    /**
     * Sets {@link ExtendedInformation#languageId() languageId}.
     *
     * @param languageId language of the document, ISO 639-2 code such as {@code deu}, or null
     * @return this builder
     */
    public Builder languageId(String languageId) {
      this.languageId = languageId;
      return this;
    }

    /**
     * Sets {@link ExtendedInformation#copyIndicator() copyIndicator}.
     *
     * @param copyIndicator true if the document is a copy, or null
     * @return this builder
     */
    public Builder copyIndicator(Boolean copyIndicator) {
      this.copyIndicator = copyIndicator;
      return this;
    }

    /**
     * Sets {@link ExtendedInformation#testIndicator() testIndicator}.
     *
     * @param testIndicator true if the document is a test, or null
     * @return this builder
     */
    public Builder testIndicator(Boolean testIndicator) {
      this.testIndicator = testIndicator;
      return this;
    }

    /**
     * Sets {@link ExtendedInformation#effectiveDate() effectiveDate}.
     *
     * @param effectiveDate date the document is effective, or null
     * @return this builder
     */
    public Builder effectiveDate(LocalDate effectiveDate) {
      this.effectiveDate = effectiveDate;
      return this;
    }

    /**
     * Sets {@link ExtendedInformation#invoiceIssuerReference() invoiceIssuerReference}.
     *
     * @param invoiceIssuerReference invoice number assigned by the invoicer, or null
     * @return this builder
     */
    public Builder invoiceIssuerReference(String invoiceIssuerReference) {
      this.invoiceIssuerReference = invoiceIssuerReference;
      return this;
    }

    /**
     * Sets {@link ExtendedInformation#transportModeCode() transportModeCode}.
     *
     * @param transportModeCode UN/ECE Rec. 19 transport mode, or null
     * @return this builder
     */
    public Builder transportModeCode(String transportModeCode) {
      this.transportModeCode = transportModeCode;
      return this;
    }

    /**
     * Sets {@link ExtendedInformation#deliveryTermsLocationName() deliveryTermsLocationName}.
     *
     * @param deliveryTermsLocationName place of the delivery terms, or null
     * @return this builder
     */
    public Builder deliveryTermsLocationName(String deliveryTermsLocationName) {
      this.deliveryTermsLocationName = deliveryTermsLocationName;
      return this;
    }

    /**
     * Sets {@link ExtendedInformation#deliveryTermsLocationCountry() deliveryTermsLocationCountry}.
     *
     * @param deliveryTermsLocationCountry country of the delivery terms, or null
     * @return this builder
     */
    public Builder deliveryTermsLocationCountry(Country deliveryTermsLocationCountry) {
      this.deliveryTermsLocationCountry = deliveryTermsLocationCountry;
      return this;
    }

    /**
     * Sets {@link ExtendedInformation#billingPeriodDescription() billingPeriodDescription}.
     *
     * @param billingPeriodDescription description of the invoicing period (BG-14), or null
     * @return this builder
     */
    public Builder billingPeriodDescription(String billingPeriodDescription) {
      this.billingPeriodDescription = billingPeriodDescription;
      return this;
    }

    /**
     * Sets {@link ExtendedInformation#buyerAccountingTypeCode() buyerAccountingTypeCode}.
     *
     * @param buyerAccountingTypeCode UNTDID 4437 type of the buyer accounting reference (BT-19), or null
     * @return this builder
     */
    public Builder buyerAccountingTypeCode(String buyerAccountingTypeCode) {
      this.buyerAccountingTypeCode = buyerAccountingTypeCode;
      return this;
    }

    /**
     * Sets {@link ExtendedInformation#payerAccountName() payerAccountName}.
     *
     * @param payerAccountName name of the debited account of a direct debit, or null
     * @return this builder
     */
    public Builder payerAccountName(String payerAccountName) {
      this.payerAccountName = payerAccountName;
      return this;
    }

    /**
     * Sets {@link ExtendedInformation#payerBic() payerBic}.
     *
     * @param payerBic BIC of the debited account of a direct debit, or null
     * @return this builder
     */
    public Builder payerBic(String payerBic) {
      this.payerBic = payerBic;
      return this;
    }

    /**
     * Sets {@link ExtendedInformation#taxExchangeRate() taxExchangeRate}.
     *
     * @param taxExchangeRate rate from the invoice to the VAT accounting currency, or null
     * @return this builder
     */
    public Builder taxExchangeRate(TaxExchangeRate taxExchangeRate) {
      this.taxExchangeRate = taxExchangeRate;
      return this;
    }

    /**
     * Adds a value to {@link ExtendedInformation#financialAdjustments() financialAdjustments}.
     *
     * @param value the financial adjustment to add
     * @return this builder
     */
    public Builder addFinancialAdjustment(FinancialAdjustment value) {
      this.financialAdjustments.add(value);
      return this;
    }

    /**
     * Adds a further buyer accounting account after the buyer accounting reference (BT-19).
     *
     * @param value the accounting account to add
     * @return this builder
     */
    public Builder addAdditionalAccountingAccount(AccountingAccount value) {
      this.additionalAccountingAccounts.add(value);
      return this;
    }

    /**
     * Constructs a new {@link ExtendedInformation} from the current values.
     *
     * @return the constructed value after the model checks succeed
     */
    public ExtendedInformation build() {
      return new ExtendedInformation(salesAgent,
                                     buyerAgent,
                                     buyerTaxRepresentative,
                                     productEndUser,
                                     shipFrom,
                                     ultimateShipTo,
                                     invoicer,
                                     invoicee,
                                     payer,
                                     deliveryTermsCode,
                                     quotationReference,
                                     deliveryNoteReference,
                                     ultimateCustomerOrderReferences,
                                     paymentTerms,
                                     logisticsServiceCharges,
                                     advancePayments,
                                     documentName,
                                     languageId,
                                     copyIndicator,
                                     testIndicator,
                                     effectiveDate,
                                     invoiceIssuerReference,
                                     transportModeCode,
                                     deliveryTermsLocationName,
                                     deliveryTermsLocationCountry,
                                     billingPeriodDescription,
                                     buyerAccountingTypeCode,
                                     payerAccountName,
                                     payerBic,
                                     taxExchangeRate,
                                     financialAdjustments,
                                     additionalAccountingAccounts);
    }
  }
}

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
package io.github.nalukit.paniolo.model;

import io.github.nalukit.paniolo.model.common.AdditionalSupportingDocument;
import io.github.nalukit.paniolo.model.common.Attachment;
import io.github.nalukit.paniolo.model.common.CashDiscount;
import io.github.nalukit.paniolo.model.common.DeliveryInformation;
import io.github.nalukit.paniolo.model.common.DocumentAdjustment;
import io.github.nalukit.paniolo.model.common.DocumentTotals;
import io.github.nalukit.paniolo.model.common.InvoiceLine;
import io.github.nalukit.paniolo.model.common.InvoiceNote;
import io.github.nalukit.paniolo.model.common.InvoicePeriod;
import io.github.nalukit.paniolo.model.common.InvoiceTotals;
import io.github.nalukit.paniolo.model.common.Party;
import io.github.nalukit.paniolo.model.common.Payee;
import io.github.nalukit.paniolo.model.common.PaymentInstruction;
import io.github.nalukit.paniolo.model.common.PrecedingInvoiceReference;
import io.github.nalukit.paniolo.model.common.ProcessControl;
import io.github.nalukit.paniolo.model.common.References;
import io.github.nalukit.paniolo.model.common.SellerTaxRepresentativeParty;
import io.github.nalukit.paniolo.model.common.TaxCategory;
import io.github.nalukit.paniolo.model.common.ThirdPartyPayment;
import io.github.nalukit.paniolo.model.common.VatAmount;
import io.github.nalukit.paniolo.model.common.validation.ModelChecks;
import io.github.nalukit.paniolo.model.common.value.CurrencyCode;
import io.github.nalukit.paniolo.model.common.value.DateTimeQualifier;
import io.github.nalukit.paniolo.model.common.value.InvoiceTypeCode;
import io.github.nalukit.paniolo.model.extended.ExtendedInformation;
import io.github.nalukit.paniolo.model.extended.LineSubtype;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable, syntax-independent invoice. Monetary totals are derived from its lines. An invoice
 * without lines, as in the Factur-X profiles MINIMUM and BASIC WL, states its totals in
 * {@code statedTotals} instead. {@code thirdPartyPayments} belong to the XRechnung extension
 * (BG-DEX-09) and raise the amount due.
 *
 * @param number                     invoice identifier (BT-1); must not be blank
 * @param issueDate                  invoice issue date (BT-2); required
 * @param typeCode                   document type (BT-3); the builder defaults to commercial invoice 380
 * @param currencyCode               invoice currency (BT-5); the builder defaults to EUR
 * @param taxCurrencyCode            VAT accounting currency (BT-6), or null; set together with taxAmountInTaxCurrency
 * @param taxAmountInTaxCurrency     VAT total in the accounting currency (BT-111), or null; set together with taxCurrencyCode
 * @param processControl             business process and specification identifier (BT-23 / BT-24); the builder defaults to XRechnung
 * @param buyerReference             buyer reference (BT-10), e.g. the Leitweg-ID; or null
 * @param references                 document and project references (BT-11 to BT-19), or null
 * @param precedingInvoiceReferences references to earlier invoices (BG-3); an empty list if none
 * @param dueDate                    payment due date (BT-9), or null; UBL CreditNote represents it in payment terms
 * @param taxPointDate               VAT point date (BT-7), or null; mutually exclusive with taxPointDateCode
 * @param taxPointDateCode           VAT point date code (BT-8), or null; only 3, 35 and 432 are accepted
 * @param invoicePeriod              invoicing period (BG-14), or null
 * @param delivery                   delivery information (BG-13), or null
 * @param paymentTerms               payment terms text (BT-20), or null
 * @param seller                     seller (BG-4); required, with name
 * @param buyer                      buyer (BG-7); required, with name
 * @param payee                      payee (BG-10) if different from the seller, or null
 * @param taxRepresentative          seller tax representative (BG-11), or null
 * @param paymentInstruction         payment instructions (BG-16), or null
 * @param notes                      invoice notes (BG-1); an empty list if none
 * @param lines                      invoice lines (BG-25), with unique identifiers; empty only when statedTotals is provided
 * @param adjustments                document allowances and charges (BG-20 / BG-21); an empty list if none
 * @param supportingDocuments        additional supporting documents (BG-24); an empty list if none
 * @param paidAmount                 paid amount (BT-113); required, zero by default in the builder
 * @param roundingAmount             rounding adjustment (BT-114), or null
 * @param cashDiscounts              structured cash discounts represented in payment terms (BT-20); null is treated as an empty list
 * @param extended                   Factur-X EXTENDED information, or null; an empty extension is treated as absent
 * @param statedTotals               explicit totals for a line-free MINIMUM or BASIC WL invoice, or null for totals derived from lines
 * @param thirdPartyPayments         third party payments (BG-DEX-09) of the XRechnung extension; null is treated as an empty list
 * @param vatAmounts                 stated VAT category tax amounts (BT-117) within the rounding of their amounts, see
 *                                   {@link VatAmount}; null is treated as an empty list
 */
public record Invoice(String number, LocalDate issueDate, InvoiceTypeCode typeCode, CurrencyCode currencyCode, CurrencyCode taxCurrencyCode, BigDecimal taxAmountInTaxCurrency, ProcessControl processControl, String buyerReference, References references,
                      List<PrecedingInvoiceReference> precedingInvoiceReferences, LocalDate dueDate, LocalDate taxPointDate, DateTimeQualifier taxPointDateCode, InvoicePeriod invoicePeriod, DeliveryInformation delivery, String paymentTerms, Party seller,
                      Party buyer, Payee payee, SellerTaxRepresentativeParty taxRepresentative, PaymentInstruction paymentInstruction, List<InvoiceNote> notes, List<InvoiceLine> lines, List<DocumentAdjustment> adjustments,
                      List<AdditionalSupportingDocument> supportingDocuments, BigDecimal paidAmount, BigDecimal roundingAmount, List<CashDiscount> cashDiscounts, ExtendedInformation extended, DocumentTotals statedTotals,
                      List<ThirdPartyPayment> thirdPartyPayments, List<VatAmount> vatAmounts) {
  private static final String NEGATIVE_PRICES = "only the Factur-X profiles EXTENDED and EXTENDED-CTC-FR allow negative prices";

  /**
   * Applies the checks of the model and copies the lists.
   *
   * @throws NullPointerException     if a required value is null
   * @throws IllegalArgumentException if a value violates the checks of the model
   */
  public Invoice {
    ModelChecks.required(number,
                         "invoice number");
    Objects.requireNonNull(issueDate,
                           "issueDate");
    // InvoiceTypeCode contains exactly the EN 16931 codes; XRechnung restricts them further (BR-DE-17).
    Objects.requireNonNull(typeCode,
                           "typeCode");
    Objects.requireNonNull(currencyCode,
                           "currencyCode");
    if ((taxCurrencyCode == null) != (taxAmountInTaxCurrency == null)) {
      throw new IllegalArgumentException("taxCurrencyCode and taxAmountInTaxCurrency must be set together");
    }
    if (taxAmountInTaxCurrency != null) {
      taxAmountInTaxCurrency = ModelChecks.money(taxAmountInTaxCurrency);
    }
    Objects.requireNonNull(processControl,
                           "processControl");
    precedingInvoiceReferences = List.copyOf(precedingInvoiceReferences);
    if (taxPointDate != null && taxPointDateCode != null) {
      throw new IllegalArgumentException("Use either taxPointDate (BT-7) or taxPointDateCode (BT-8)");
    }
    if (taxPointDateCode != null && taxPointDateCode != DateTimeQualifier.CODE_3 && taxPointDateCode != DateTimeQualifier.CODE_35 && taxPointDateCode != DateTimeQualifier.CODE_432) {
      throw new IllegalArgumentException("taxPointDateCode must be 3, 35 or 432");
    }
    Party.requireElectronicAddressScheme(Objects.requireNonNull(seller,
                                                                "seller"),
                                         "seller");
    Party.requireElectronicAddressScheme(Objects.requireNonNull(buyer,
                                                                "buyer"),
                                         "buyer");
    Party.requireName(seller,
                      "seller");
    Party.requireName(buyer,
                      "buyer");
    notes = List.copyOf(notes);
    // Only Factur-X EXTENDED knows a note with a content code instead of its text (BR-FXEXT-01).
    if (!processControl.isExtended() && notes.stream()
                                             .anyMatch(note -> note.text() == null)) {
      throw new IllegalArgumentException("note must not be blank; only the Factur-X profiles EXTENDED and EXTENDED-CTC-FR allow a note with a content code only");
    }
    lines = List.copyOf(lines);
    // The sub-invoice lines of a French multi-vendor invoice name their item seller by its legal
    // registration only, the sub-invoice line by name, identifiers, and country (BR-FR-MV-03).
    if (!processControl.isFrenchMultiVendor()) {
      for (InvoiceLine line : lines) {
        if (line.extended() != null) {
          Party.requireAddress(line.extended()
                                   .itemSeller(),
                               "itemSeller");
        }
      }
    }
    if (statedTotals == null) {
      if (lines.isEmpty()) {
        throw new IllegalArgumentException("An invoice requires at least one line, or stated totals for the Factur-X profiles MINIMUM and BASIC WL");
      }
      Party.requireAddress(seller,
                           "seller");
      Party.requireAddress(buyer,
                           "buyer");
    } else if (!lines.isEmpty()) {
      throw new IllegalArgumentException("Stated totals are only allowed for an invoice without lines");
    } else if (seller.address() == null) {
      // BR-08 applies to MINIMUM as well.
      throw new IllegalArgumentException("The seller requires a postal address (BG-5)");
    }
    var ids = new HashSet<String>();
    for (InvoiceLine line : lines) {
      if (!ids.add(line.id())) {
        throw new IllegalArgumentException("Duplicate invoice line id: " + line.id());
      }
      // BR-26: only sub-lines of the XRechnung extension and EXTENDED subtotal and information
      // lines may omit the item net price.
      if (line.extended() == null || line.extended()
                                         .counts()) {
        Objects.requireNonNull(line.netUnitPrice(),
                               "netUnitPrice");
      }
    }
    checkLineHierarchy(lines,
                       ids);
    checkLineAmounts(lines);
    // Factur-X EXTENDED and EXTENDED-CTC-FR have no BR-27 and BR-28: negative prices are valid there.
    if (!processControl.isExtended()) {
      checkPrices(lines);
    }
    adjustments         = List.copyOf(adjustments);
    supportingDocuments = List.copyOf(supportingDocuments);
    cashDiscounts       = cashDiscounts == null ?
                          List.of() :
                          List.copyOf(cashDiscounts);
    // EN 16931 does not restrict the sign of BT-113; corrections state negative paid amounts.
    paidAmount = ModelChecks.money(Objects.requireNonNull(paidAmount,
                                                          "paidAmount"));
    if (roundingAmount != null) {
      roundingAmount = ModelChecks.money(roundingAmount);
    }
    vatAmounts = vatAmounts == null ?
                 List.of() :
                 List.copyOf(vatAmounts);
    if (!vatAmounts.isEmpty() && statedTotals != null) {
      throw new IllegalArgumentException("Stated VAT amounts belong to an invoice with lines; stated totals carry their own VAT breakdown");
    }
    if (vatAmounts.stream()
                  .map(VatAmount::tax)
                  .distinct()
                  .count() != vatAmounts.size()) {
      throw new IllegalArgumentException("One stated VAT amount per VAT category and rate");
    }
    thirdPartyPayments = thirdPartyPayments == null ?
                         List.of() :
                         List.copyOf(thirdPartyPayments);
    // Empty EXTENDED information is the same as none, so readers and writers agree.
    if (extended != null && extended.isEmpty()) {
      extended = null;
    }
    if (statedTotals != null) {
      checkStatedTotals(statedTotals,
                        adjustments,
                        extended);
    }
    if (paymentInstruction != null && paymentInstruction.hasCardWithCreditTransfer() && !ProcessControl.FACTURX_EXTENDED_CTC_FR.equals(processControl.specificationId())) {
      throw new IllegalArgumentException("Credit transfer requires at least one account and no card or direct debit");
    }
    if (extended != null) {
      if (extended.taxExchangeRate() != null && taxCurrencyCode == null) {
        throw new IllegalArgumentException("A tax exchange rate requires the VAT accounting currency (BT-6)");
      }
      if ((extended.payerAccountName() != null || extended.payerBic() != null) && (paymentInstruction == null || paymentInstruction.directDebit() == null)) {
        throw new IllegalArgumentException("Payer account name and BIC require a direct debit");
      }
      if (extended.billingPeriodDescription() != null && invoicePeriod == null) {
        throw new IllegalArgumentException("A billing period description requires the invoicing period (BG-14)");
      }
      if (extended.buyerAccountingTypeCode() != null && (references == null || references.buyerAccountingReference() == null)) {
        throw new IllegalArgumentException("An accounting type code requires the buyer accounting reference (BT-19)");
      }
      if (!extended.additionalAccountingAccounts()
                   .isEmpty() && (references == null || references.buyerAccountingReference() == null)) {
        throw new IllegalArgumentException("Further accounting accounts require the buyer accounting reference (BT-19)");
      }
    }
  }

  /**
   * Checks the stated totals of an invoice without lines against its document allowances and
   * charges (BR-CO-13).
   */
  private static void checkStatedTotals(DocumentTotals totals,
                                        List<DocumentAdjustment> adjustments,
                                        ExtendedInformation extended) {
    if (extended != null) {
      throw new IllegalArgumentException("EXTENDED information requires invoice lines");
    }
    if (totals.lineTotal() == null) {
      if (!adjustments.isEmpty()) {
        throw new IllegalArgumentException("Document allowances and charges require the sum of line net amounts (BT-106)");
      }
      return;
    }
    BigDecimal expected = totals.lineTotal();
    for (DocumentAdjustment adjustment : adjustments) {
      expected = adjustment.charge() ?
                 expected.add(adjustment.amount()) :
                 expected.subtract(adjustment.amount());
    }
    if (ModelChecks.money(expected)
                   .compareTo(totals.taxBasisTotal()) != 0) {
      throw new IllegalArgumentException("Total amount without VAT " +
                                         totals.taxBasisTotal()
                                               .toPlainString() +
                                         " differs from line total minus allowances plus charges " +
                                         ModelChecks.money(expected)
                                                    .toPlainString() +
                                         " (BR-CO-13)");
    }
  }

  /**
   * Checks the stated EXTENDED line amounts: the amount with VAT is the net amount plus the VAT
   * amount.
   */
  /**
   * BR-27 and BR-28: the item net price and the item gross price shall not be negative, outside the
   * Factur-X profiles EXTENDED and EXTENDED-CTC-FR. Sub-lines are checked as well.
   */
  private static void checkPrices(List<InvoiceLine> lines) {
    for (InvoiceLine line : lines) {
      if (line.netUnitPrice() != null && line.netUnitPrice()
                                             .signum() < 0) {
        throw new IllegalArgumentException("Line " + line.id() + ": netUnitPrice must not be negative (BR-27); " + NEGATIVE_PRICES);
      }
      if (line.grossPrice() != null && line.grossPrice()
                                           .signum() < 0) {
        throw new IllegalArgumentException("Line " + line.id() + ": grossPrice must not be negative (BR-28); " + NEGATIVE_PRICES);
      }
      checkPrices(line.subLines());
    }
  }

  private static void checkLineAmounts(List<InvoiceLine> lines) {
    for (InvoiceLine line : lines) {
      var extended = line.extended();
      if (extended != null &&
          extended.taxTotal() != null &&
          extended.grossTotal() != null &&
          line.netAmount()
              .add(extended.taxTotal())
              .compareTo(extended.grossTotal()) != 0) {
        throw new IllegalArgumentException("Line " +
                                           line.id() +
                                           ": amount with VAT " +
                                           extended.grossTotal()
                                                   .toPlainString() +
                                           " differs from net amount plus VAT amount " +
                                           line.netAmount()
                                               .add(extended.taxTotal())
                                               .toPlainString());
      }
    }
  }

  /**
   * Checks the EXTENDED sub-lines: parents exist (BR-FXEXT-11), and a subtotal line equals the sum
   * of its regular and subtotal sub-lines (BR-FXEXT-08).
   */
  private static void checkLineHierarchy(List<InvoiceLine> lines,
                                         Set<String> ids) {
    var children = new HashMap<String, BigDecimal>();
    for (InvoiceLine line : lines) {
      var extended = line.extended();
      if (extended == null || extended.parentLineId() == null) {
        continue;
      }
      if (!ids.contains(extended.parentLineId()) ||
          extended.parentLineId()
                  .equals(line.id())) {
        throw new IllegalArgumentException("Line " + line.id() + ": parent line " + extended.parentLineId() + " does not exist");
      }
      if (extended.subtype() != LineSubtype.INFORMATION) {
        children.merge(extended.parentLineId(),
                       line.netAmount(),
                       BigDecimal::add);
      }
    }
    for (InvoiceLine line : lines) {
      var extended = line.extended();
      if (extended != null &&
          extended.subtype() == LineSubtype.GROUP &&
          children.containsKey(line.id()) &&
          children.get(line.id())
                  .compareTo(line.netAmount()) != 0) {
        throw new IllegalArgumentException("Line " +
                                           line.id() +
                                           ": subtotal " +
                                           line.netAmount()
                                               .toPlainString() +
                                           " differs from the sum of its sub-lines " +
                                           children.get(line.id())
                                                   .toPlainString());
      }
    }
  }

  /**
   * Starts named construction of a {@link Invoice} using the builder defaults.
   *
   * @return a new builder; not shared with other calls
   */
  public static Builder create() {
    return new Builder();
  }

  /**
   * Totals of the invoice: the stated totals of an invoice without lines, otherwise the totals
   * calculated from lines, allowances, and charges.
   *
   * @return the totals
   */
  public InvoiceTotals totals() {
    return statedTotals == null ?
           InvoiceTotals.calculate(this) :
           InvoiceTotals.stated(this);
  }

  /**
   * Returns true if the invoice has no lines and states its totals, as in the Factur-X profiles
   * MINIMUM and BASIC WL.
   *
   * @return true if the invoice has no lines and stated totals
   */
  public boolean hasStatedTotals() {
    return statedTotals != null;
  }

  /**
   * Returns true if the invoice carries information of the XRechnung extension: sub invoice lines,
   * third party payments, or attachments with a MIME code of the extension. Only the UBL Invoice
   * writer with the specification identifier {@link ProcessControl#XRECHNUNG_3_EXTENSION} writes
   * it.
   *
   * @return true if the invoice carries XRechnung extension information
   */
  public boolean hasXrechnungExtensionInformation() {
    if (!thirdPartyPayments.isEmpty()) {
      return true;
    }
    if (supportingDocuments.stream()
                           .anyMatch(document -> document.attachment() != null &&
                                                 Attachment.EXTENSION_MIME_CODES.contains(document.attachment()
                                                                                                  .mimeCode()))) {
      return true;
    }
    return lines.stream()
                .anyMatch(line -> !line.subLines()
                                       .isEmpty());
  }

  /**
   * Returns true if the invoice carries information of the Factur-X profile EXTENDED: header, line,
   * or party information, note content codes, or classification names. Only the CII writer with
   * the EXTENDED specification identifier writes it.
   *
   * @return true if the invoice carries EXTENDED information
   */
  public boolean hasExtendedInformation() {
    if (extended != null || extendedParty(seller) || extendedParty(buyer)) {
      return true;
    }
    if (adjustments.stream()
                   .anyMatch(adjustment -> adjustment.extended() != null)) {
      return true;
    }
    if (notes.stream()
             .anyMatch(note -> note.contentCode() != null)) {
      return true;
    }
    return lines.stream()
                .anyMatch(line -> line.extended() != null ||
                                  line.classifications()
                                      .stream()
                                      .anyMatch(classification -> classification.className() != null));
  }

  /**
   * A contact with department only is CII content of every profile, not of EXTENDED.
   */
  private static boolean extendedParty(Party party) {
    return party.extended() != null && !party.extended()
                                            .isDepartmentContactOnly();
  }

  /**
   * Named construction of an immutable invoice.
   *
   * <p>Mutable construction state for {@link Invoice}. Use a separate builder per operation;
   * builders are not thread-safe. {@link #build()} constructs a new immutable value and applies
   * the checks of the model. XML schema and business rule validation are separate operations.
   */
  public static final class Builder {
    private String                             number;
    private LocalDate                          issueDate;
    private InvoiceTypeCode                    typeCode                   = InvoiceTypeCode.CODE_380;
    private CurrencyCode                       currencyCode               = CurrencyCode.EUR;
    private CurrencyCode                       taxCurrencyCode;
    private BigDecimal                         taxAmountInTaxCurrency;
    private ProcessControl                     processControl             = ProcessControl.xrechnung();
    private String                             buyerReference;
    private References                         references;
    private List<PrecedingInvoiceReference>    precedingInvoiceReferences = new ArrayList<>();
    private LocalDate                          dueDate;
    private LocalDate                          taxPointDate;
    private DateTimeQualifier                  taxPointDateCode;
    private InvoicePeriod                      invoicePeriod;
    private DeliveryInformation                delivery;
    private String                             paymentTerms;
    private Party                              seller;
    private Party                              buyer;
    private Payee                              payee;
    private SellerTaxRepresentativeParty       taxRepresentative;
    private PaymentInstruction                 paymentInstruction;
    private List<InvoiceNote>                  notes                      = new ArrayList<>();
    private List<InvoiceLine>                  lines                      = new ArrayList<>();
    private List<DocumentAdjustment>           adjustments                = new ArrayList<>();
    private List<AdditionalSupportingDocument> supportingDocuments        = new ArrayList<>();
    private List<CashDiscount>                 cashDiscounts              = new ArrayList<>();
    private BigDecimal                         paidAmount                 = BigDecimal.ZERO;
    private BigDecimal                         roundingAmount;
    private ExtendedInformation                extended;
    private DocumentTotals                     statedTotals;
    private List<ThirdPartyPayment>            thirdPartyPayments         = new ArrayList<>();
    private List<VatAmount>                    vatAmounts                 = new ArrayList<>();

    /**
     * Creates a builder with the defaults of the model; usually obtained with {@link Invoice#create()}.
     */
    public Builder() {
    }

    /**
     * Sets {@link Invoice#number() number}.
     *
     * @param number invoice identifier (BT-1); must not be blank
     * @return this builder
     */
    public Builder number(String number) {
      this.number = number;
      return this;
    }

    /**
     * Sets {@link Invoice#issueDate() issueDate}.
     *
     * @param issueDate invoice issue date (BT-2); required
     * @return this builder
     */
    public Builder issueDate(LocalDate issueDate) {
      this.issueDate = issueDate;
      return this;
    }

    /**
     * Sets {@link Invoice#typeCode() typeCode}.
     *
     * @param typeCode document type (BT-3); the builder defaults to commercial invoice 380
     * @return this builder
     */
    public Builder typeCode(InvoiceTypeCode typeCode) {
      this.typeCode = typeCode;
      return this;
    }

    /**
     * Sets {@link Invoice#currencyCode() currencyCode}.
     *
     * @param currencyCode invoice currency (BT-5); the builder defaults to EUR
     * @return this builder
     */
    public Builder currencyCode(CurrencyCode currencyCode) {
      this.currencyCode = currencyCode;
      return this;
    }

    /**
     * VAT accounting currency (BT-6); requires {@link #taxAmountInTaxCurrency}.
     *
     * @param taxCurrencyCode VAT accounting currency (BT-6), or null; set together with taxAmountInTaxCurrency
     * @return this builder
     */
    public Builder taxCurrencyCode(CurrencyCode taxCurrencyCode) {
      this.taxCurrencyCode = taxCurrencyCode;
      return this;
    }

    /**
     * Total VAT amount in the VAT accounting currency (BT-111).
     *
     * @param taxAmountInTaxCurrency VAT total in the accounting currency (BT-111), or null; set together with taxCurrencyCode
     * @return this builder
     */
    public Builder taxAmountInTaxCurrency(BigDecimal taxAmountInTaxCurrency) {
      this.taxAmountInTaxCurrency = taxAmountInTaxCurrency;
      return this;
    }

    /**
     * Sets {@link Invoice#processControl() processControl}.
     *
     * @param processControl business process and specification identifier (BT-23 / BT-24); the builder defaults to XRechnung
     * @return this builder
     */
    public Builder processControl(ProcessControl processControl) {
      this.processControl = processControl;
      return this;
    }

    /**
     * Sets {@link Invoice#buyerReference() buyerReference}.
     *
     * @param buyerReference buyer reference (BT-10), e.g. the Leitweg-ID; or null
     * @return this builder
     */
    public Builder buyerReference(String buyerReference) {
      this.buyerReference = buyerReference;
      return this;
    }

    /**
     * Sets {@link Invoice#references() references}.
     *
     * @param references document and project references (BT-11 to BT-19), or null
     * @return this builder
     */
    public Builder references(References references) {
      this.references = references;
      return this;
    }

    /**
     * Sets {@link Invoice#precedingInvoiceReferences() precedingInvoiceReferences}.
     *
     * @param precedingInvoiceReferences references to earlier invoices (BG-3); an empty list if none
     * @return this builder
     */
    public Builder precedingInvoiceReferences(List<PrecedingInvoiceReference> precedingInvoiceReferences) {
      this.precedingInvoiceReferences = new ArrayList<>(precedingInvoiceReferences);
      return this;
    }

    /**
     * Sets {@link Invoice#dueDate() dueDate}.
     *
     * @param dueDate payment due date (BT-9), or null; UBL CreditNote represents it in payment terms
     * @return this builder
     */
    public Builder dueDate(LocalDate dueDate) {
      this.dueDate = dueDate;
      return this;
    }

    /**
     * Value added tax point date (BT-7).
     *
     * @param taxPointDate VAT point date (BT-7), or null; mutually exclusive with taxPointDateCode
     * @return this builder
     */
    public Builder taxPointDate(LocalDate taxPointDate) {
      this.taxPointDate = taxPointDate;
      return this;
    }

    /**
     * Value added tax point date code (BT-8): 3, 35 or 432.
     *
     * @param taxPointDateCode VAT point date code (BT-8), or null; only 3, 35 and 432 are accepted
     * @return this builder
     */
    public Builder taxPointDateCode(DateTimeQualifier taxPointDateCode) {
      this.taxPointDateCode = taxPointDateCode;
      return this;
    }

    /**
     * Sets {@link Invoice#invoicePeriod() invoicePeriod}.
     *
     * @param invoicePeriod invoicing period (BG-14), or null
     * @return this builder
     */
    public Builder invoicePeriod(InvoicePeriod invoicePeriod) {
      this.invoicePeriod = invoicePeriod;
      return this;
    }

    /**
     * Sets {@link Invoice#delivery() delivery}.
     *
     * @param delivery delivery information (BG-13), or null
     * @return this builder
     */
    public Builder delivery(DeliveryInformation delivery) {
      this.delivery = delivery;
      return this;
    }

    /**
     * Sets {@link Invoice#paymentTerms() paymentTerms}.
     *
     * @param paymentTerms payment terms text (BT-20), or null
     * @return this builder
     */
    public Builder paymentTerms(String paymentTerms) {
      this.paymentTerms = paymentTerms;
      return this;
    }

    /**
     * Sets {@link Invoice#seller() seller}.
     *
     * @param seller seller (BG-4); required, with name
     * @return this builder
     */
    public Builder seller(Party seller) {
      this.seller = seller;
      return this;
    }

    /**
     * Sets {@link Invoice#buyer() buyer}.
     *
     * @param buyer buyer (BG-7); required, with name
     * @return this builder
     */
    public Builder buyer(Party buyer) {
      this.buyer = buyer;
      return this;
    }

    /**
     * Sets {@link Invoice#payee() payee}.
     *
     * @param payee payee (BG-10) if different from the seller, or null
     * @return this builder
     */
    public Builder payee(Payee payee) {
      this.payee = payee;
      return this;
    }

    /**
     * Sets {@link Invoice#taxRepresentative() taxRepresentative}.
     *
     * @param taxRepresentative seller tax representative (BG-11), or null
     * @return this builder
     */
    public Builder taxRepresentative(SellerTaxRepresentativeParty taxRepresentative) {
      this.taxRepresentative = taxRepresentative;
      return this;
    }

    /**
     * Sets {@link Invoice#paymentInstruction() paymentInstruction}.
     *
     * @param paymentInstruction payment instructions (BG-16), or null
     * @return this builder
     */
    public Builder paymentInstruction(PaymentInstruction paymentInstruction) {
      this.paymentInstruction = paymentInstruction;
      return this;
    }

    /**
     * Sets {@link Invoice#notes() notes}.
     *
     * @param notes invoice notes (BG-1); an empty list if none
     * @return this builder
     */
    public Builder notes(List<InvoiceNote> notes) {
      this.notes = new ArrayList<>(notes);
      return this;
    }

    /**
     * Sets {@link Invoice#lines() lines}.
     *
     * @param lines invoice lines (BG-25), with unique identifiers; empty only when statedTotals is provided
     * @return this builder
     */
    public Builder lines(List<InvoiceLine> lines) {
      this.lines = new ArrayList<>(lines);
      return this;
    }

    /**
     * Sets {@link Invoice#adjustments() adjustments}.
     *
     * @param adjustments document allowances and charges (BG-20 / BG-21); an empty list if none
     * @return this builder
     */
    public Builder adjustments(List<DocumentAdjustment> adjustments) {
      this.adjustments = new ArrayList<>(adjustments);
      return this;
    }

    /**
     * Sets {@link Invoice#supportingDocuments() supportingDocuments}.
     *
     * @param supportingDocuments additional supporting documents (BG-24); an empty list if none
     * @return this builder
     */
    public Builder supportingDocuments(List<AdditionalSupportingDocument> supportingDocuments) {
      this.supportingDocuments = new ArrayList<>(supportingDocuments);
      return this;
    }

    /**
     * Sets {@link Invoice#paidAmount() paidAmount}.
     *
     * @param paidAmount paid amount (BT-113); required, zero by default in the builder
     * @return this builder
     */
    public Builder paidAmount(BigDecimal paidAmount) {
      this.paidAmount = paidAmount;
      return this;
    }

    /**
     * Rounding amount (BT-114), added to the amount due for payment.
     *
     * @param roundingAmount rounding adjustment (BT-114), or null
     * @return this builder
     */
    public Builder roundingAmount(BigDecimal roundingAmount) {
      this.roundingAmount = roundingAmount;
      return this;
    }

    /**
     * Adds a value to {@link Invoice#lines() lines}.
     *
     * @param value the invoice line to add
     * @return this builder
     */
    public Builder addLine(InvoiceLine value) {
      this.lines.add(value);
      return this;
    }

    /**
     * Adds a value to {@link Invoice#notes() notes}.
     *
     * @param value the invoice note to add
     * @return this builder
     */
    public Builder addNote(InvoiceNote value) {
      this.notes.add(value);
      return this;
    }

    /**
     * Adds a note without subject code to {@link Invoice#notes() notes}.
     *
     * @param value the note text (BT-22)
     * @return this builder
     */
    public Builder addNote(String value) {
      return addNote(InvoiceNote.of(value));
    }

    /**
     * Adds a value to {@link Invoice#adjustments() adjustments}.
     *
     * @param value the document adjustment to add
     * @return this builder
     */
    public Builder addAdjustment(DocumentAdjustment value) {
      this.adjustments.add(value);
      return this;
    }

    /**
     * Adds a value to {@link Invoice#precedingInvoiceReferences() precedingInvoiceReferences}.
     *
     * @param value the preceding invoice reference to add
     * @return this builder
     */
    public Builder addPrecedingInvoiceReference(PrecedingInvoiceReference value) {
      this.precedingInvoiceReferences.add(value);
      return this;
    }

    /**
     * Adds a value to {@link Invoice#supportingDocuments() supportingDocuments}.
     *
     * @param value the additional supporting document to add
     * @return this builder
     */
    public Builder addSupportingDocument(AdditionalSupportingDocument value) {
      this.supportingDocuments.add(value);
      return this;
    }

    /**
     * Cash discounts (Skonto), transmitted in the payment terms (BT-20) in the XRechnung format.
     *
     * @param cashDiscounts structured cash discounts represented in payment terms (BT-20); must not be null
     * @return this builder
     */
    public Builder cashDiscounts(List<CashDiscount> cashDiscounts) {
      this.cashDiscounts = new ArrayList<>(cashDiscounts);
      return this;
    }

    /**
     * Adds a cash discount (Skonto), e.g. {@code CashDiscount.of(14, new BigDecimal("2"))}.
     *
     * @param value the cash discount to add
     * @return this builder
     */
    public Builder addCashDiscount(CashDiscount value) {
      this.cashDiscounts.add(value);
      return this;
    }

    /**
     * Information of the Factur-X / ZUGFeRD profile EXTENDED, e.g. further parties, logistics
     * service charges, or structured payment terms.
     *
     * @param extended Factur-X EXTENDED information, or null; an empty extension is treated as absent
     * @return this builder
     */
    public Builder extended(ExtendedInformation extended) {
      this.extended = extended;
      return this;
    }

    /**
     * Totals of an invoice without lines (Factur-X MINIMUM and BASIC WL).
     *
     * @param statedTotals explicit totals for a line-free MINIMUM or BASIC WL invoice, or null for totals derived from lines
     * @return this builder
     */
    public Builder statedTotals(DocumentTotals statedTotals) {
      this.statedTotals = statedTotals;
      return this;
    }

    /**
     * Adds a third party payment of the XRechnung extension (BG-DEX-09).
     *
     * @param value the third party payment to add
     * @return this builder
     */
    public Builder addThirdPartyPayment(ThirdPartyPayment value) {
      this.thirdPartyPayments.add(value);
      return this;
    }

    /**
     * Constructs a new {@link Invoice} from the current values.
     *
     * @return the constructed value after the model checks succeed
     */
    public Invoice build() {
      var invoice = new Invoice(number,
                         issueDate,
                         typeCode,
                         currencyCode,
                         taxCurrencyCode,
                         taxAmountInTaxCurrency,
                         processControl,
                         buyerReference,
                         references,
                         precedingInvoiceReferences,
                         dueDate,
                         taxPointDate,
                         taxPointDateCode,
                         invoicePeriod,
                         delivery,
                         paymentTerms,
                         seller,
                         buyer,
                         payee,
                         taxRepresentative,
                         paymentInstruction,
                         notes,
                         lines,
                         adjustments,
                         supportingDocuments,
                         paidAmount,
                         roundingAmount,
                         cashDiscounts,
                         extended,
                         statedTotals,
                         thirdPartyPayments,
                         vatAmounts);
      // Stated VAT amounts are checked against the rounding of their categories right away.
      if (!vatAmounts.isEmpty()) {
        invoice.totals();
      }
      return invoice;
    }

    /**
     * States the VAT category tax amount (BT-117) of a breakdown instead of the amount the model rounds
     * once per category, e.g. when the seller rounds the VAT of every line. It may differ from the
     * rounded amount by 0.01 per line, allowance, and charge of the category (BR-FXEXT-S-09); the totals
     * follow from it. See {@link VatAmount}.
     *
     * @param tax    the VAT category and rate of the breakdown, as the lines state it
     * @param amount the VAT amount of the category
     * @return this builder
     */
    public Builder vatAmount(TaxCategory tax,
                             BigDecimal amount) {
      return addVatAmount(new VatAmount(tax,
                                        amount));
    }

    /**
     * Adds a stated VAT category tax amount; see {@link #vatAmount(TaxCategory, BigDecimal)}.
     *
     * @param value the stated VAT amount
     * @return this builder
     */
    public Builder addVatAmount(VatAmount value) {
      this.vatAmounts.add(value);
      return this;
    }
  }
}

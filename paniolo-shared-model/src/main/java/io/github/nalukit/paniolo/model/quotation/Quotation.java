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
package io.github.nalukit.paniolo.model.quotation;

import io.github.nalukit.paniolo.model.Invoice;
import io.github.nalukit.paniolo.model.common.DeliveryInformation;
import io.github.nalukit.paniolo.model.common.DocumentAdjustment;
import io.github.nalukit.paniolo.model.common.InvoiceLine;
import io.github.nalukit.paniolo.model.common.InvoiceNote;
import io.github.nalukit.paniolo.model.common.InvoiceTotals;
import io.github.nalukit.paniolo.model.common.Party;
import io.github.nalukit.paniolo.model.common.validation.ModelChecks;
import io.github.nalukit.paniolo.model.common.value.CurrencyCode;
import io.github.nalukit.paniolo.model.extended.DocumentReference;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable quotation (German "Angebot"): the offer of a seller to a buyer, which can become an
 * {@link Invoice} with {@link #toInvoice(String, LocalDate)}. A quotation is no e-invoice
 * document: no standard defines an XML syntax for it, so Paniolo neither writes nor validates
 * one. It shares parties, lines, allowances and charges, and the total calculation with the
 * invoice, so everything it states converts without loss.
 *
 * <p>Optional lines are offered next to the standard offer and do not count in the totals until
 * they are accepted.
 *
 * @param number          quotation number; must not be blank
 * @param issueDate       date of the quotation; required
 * @param validUntil      last day the offer is binding, or null; not before the issue date
 * @param currencyCode    currency of the prices; the builder defaults to EUR
 * @param seller          the offering party; required, with name and country
 * @param buyer           the party the offer is made to; required, with name and country
 * @param buyerReference  reference of the buyer, e.g. the number of its inquiry, or null
 * @param subject         short subject line of the quotation, or null
 * @param delivery        delivery information, or null
 * @param deliveryTerms   free text on delivery time and conditions, or null
 * @param paymentTerms    free text on the payment terms, or null
 * @param notes           notes of the quotation; an empty list if none
 * @param lines           offered lines, with unique identifiers; at least one must not be optional
 * @param adjustments     document allowances and charges; an empty list if none
 */
public record Quotation(String number, LocalDate issueDate, LocalDate validUntil, CurrencyCode currencyCode, Party seller, Party buyer, String buyerReference, String subject, DeliveryInformation delivery, String deliveryTerms, String paymentTerms,
                        List<InvoiceNote> notes, List<QuotationLine> lines, List<DocumentAdjustment> adjustments) {
  /**
   * Applies the checks of the model and copies the lists. The checks make sure that every quotation
   * converts to an invoice.
   *
   * @throws NullPointerException     if a required value is null
   * @throws IllegalArgumentException if a value violates the checks of the model
   */
  public Quotation {
    ModelChecks.required(number,
                         "quotation number");
    Objects.requireNonNull(issueDate,
                           "issueDate");
    if (validUntil != null && validUntil.isBefore(issueDate)) {
      throw new IllegalArgumentException("validUntil " + validUntil + " is before the issue date " + issueDate);
    }
    Objects.requireNonNull(currencyCode,
                           "currencyCode");
    Party.requireElectronicAddressScheme(Objects.requireNonNull(seller,
                                                                "seller"),
                                         "seller");
    Party.requireElectronicAddressScheme(Objects.requireNonNull(buyer,
                                                                "buyer"),
                                         "buyer");
    Party.requireCountryAddress(seller,
                                "seller");
    Party.requireCountryAddress(buyer,
                                "buyer");
    notes       = List.copyOf(notes);
    lines       = List.copyOf(lines);
    adjustments = List.copyOf(adjustments);
    if (lines.stream()
             .allMatch(QuotationLine::optional)) {
      throw new IllegalArgumentException("A quotation requires at least one line that is not optional");
    }
    var ids = new HashSet<String>();
    for (QuotationLine line : lines) {
      if (!ids.add(line.line()
                       .id())) {
        throw new IllegalArgumentException("Duplicate quotation line id: " + line.line()
                                                                                .id());
      }
    }
  }

  /**
   * Starts named construction of a {@link Quotation} using the builder defaults.
   *
   * @return a new builder; not shared with other calls
   */
  public static Builder create() {
    return new Builder();
  }

  /**
   * Totals of the standard offer: the lines that are not optional, with the document allowances
   * and charges. The total amount with VAT is in {@link InvoiceTotals#taxInclusiveAmount()}.
   *
   * @return the totals, calculated as for the invoice
   */
  public InvoiceTotals totals() {
    return totals(Set.of());
  }

  /**
   * Totals of the standard offer plus the accepted optional lines.
   *
   * @param acceptedOptionalLineIds identifiers of the optional lines that count; may be empty
   * @return the totals, calculated as for the invoice
   * @throws IllegalArgumentException if an identifier does not name an optional line
   */
  public InvoiceTotals totals(Collection<String> acceptedOptionalLineIds) {
    return toInvoiceBuilder(number,
                            issueDate,
                            acceptedOptionalLineIds).build()
                                                    .totals();
  }

  /**
   * Reference to this quotation for the invoice, with its number and date. The invoice states it
   * in the extended information of the Factur-X profile EXTENDED, see
   * {@link io.github.nalukit.paniolo.model.extended.ExtendedInformation#quotationReference()}.
   *
   * @return the reference
   */
  public DocumentReference reference() {
    return new DocumentReference(number,
                                 null,
                                 issueDate);
  }

  /**
   * Creates the invoice for the standard offer: the lines that are not optional.
   *
   * @param invoiceNumber    number of the new invoice; must not be blank
   * @param invoiceIssueDate issue date of the new invoice
   * @return the invoice, with the builder defaults for type and process (see {@link #toInvoiceBuilder})
   * @throws IllegalArgumentException if the invoice number is blank
   */
  public Invoice toInvoice(String invoiceNumber,
                           LocalDate invoiceIssueDate) {
    return toInvoice(invoiceNumber,
                     invoiceIssueDate,
                     Set.of());
  }

  /**
   * Creates the invoice for the standard offer plus the accepted optional lines.
   *
   * @param invoiceNumber           number of the new invoice; must not be blank
   * @param invoiceIssueDate        issue date of the new invoice
   * @param acceptedOptionalLineIds identifiers of the optional lines the buyer accepted; may be empty
   * @return the invoice
   * @throws IllegalArgumentException if the invoice number is blank or an identifier does not name an optional line
   */
  public Invoice toInvoice(String invoiceNumber,
                           LocalDate invoiceIssueDate,
                           Collection<String> acceptedOptionalLineIds) {
    return toInvoiceBuilder(invoiceNumber,
                            invoiceIssueDate,
                            acceptedOptionalLineIds).build();
  }

  /**
   * Prepares the invoice for the standard offer plus the accepted optional lines, so that the
   * caller can complete it before building: the invoice has the number and date given, the
   * type commercial invoice 380, and the defaults of the invoice builder for the process
   * (XRechnung). It takes over the parties, buyer reference, delivery, notes, payment terms text,
   * allowances and charges, and the lines of the offer with their identifiers. The subject and
   * the delivery terms text are not taken over; an invoice has no field for them.
   *
   * <p>The quotation does not know what the invoice needs besides: the caller adds the payment
   * instruction, the due date, the invoice period, and, for the Factur-X profile EXTENDED, the
   * {@link #reference() quotation reference}.
   *
   * @param invoiceNumber           number of the new invoice; must not be blank
   * @param invoiceIssueDate        issue date of the new invoice
   * @param acceptedOptionalLineIds identifiers of the optional lines the buyer accepted; may be empty
   * @return a new builder; not shared with other calls
   * @throws IllegalArgumentException if an identifier does not name an optional line
   */
  public Invoice.Builder toInvoiceBuilder(String invoiceNumber,
                                          LocalDate invoiceIssueDate,
                                          Collection<String> acceptedOptionalLineIds) {
    Set<String> accepted = new HashSet<>(acceptedOptionalLineIds);
    for (String id : accepted) {
      if (lines.stream()
               .noneMatch(line -> line.optional() && line.line()
                                                          .id()
                                                          .equals(id))) {
        throw new IllegalArgumentException("Line " + id + " is not an optional line of the quotation");
      }
    }
    var builder = Invoice.create()
                         .number(invoiceNumber)
                         .issueDate(invoiceIssueDate)
                         .currencyCode(currencyCode)
                         .seller(seller)
                         .buyer(buyer)
                         .buyerReference(buyerReference)
                         .delivery(delivery)
                         .paymentTerms(paymentTerms)
                         .notes(notes)
                         .adjustments(adjustments);
    for (QuotationLine line : lines) {
      if (!line.optional() || accepted.contains(line.line()
                                                    .id())) {
        builder.addLine(line.line());
      }
    }
    return builder;
  }

  /**
   * Named construction of an immutable quotation.
   *
   * <p>Mutable construction state for {@link Quotation}. Use a separate builder per operation;
   * builders are not thread-safe. {@link #build()} constructs a new immutable value and applies
   * the checks of the model.
   */
  public static final class Builder {
    private String                   number;
    private LocalDate                issueDate;
    private LocalDate                validUntil;
    private CurrencyCode             currencyCode = CurrencyCode.EUR;
    private Party                    seller;
    private Party                    buyer;
    private String                   buyerReference;
    private String                   subject;
    private DeliveryInformation      delivery;
    private String                   deliveryTerms;
    private String                   paymentTerms;
    private List<InvoiceNote>        notes        = new ArrayList<>();
    private List<QuotationLine>      lines        = new ArrayList<>();
    private List<DocumentAdjustment> adjustments  = new ArrayList<>();

    /**
     * Creates a builder with the defaults: currency EUR, no lines.
     */
    public Builder() {
    }

    /**
     * Sets {@link Quotation#number() number}.
     *
     * @param number quotation number
     * @return this builder
     */
    public Builder number(String number) {
      this.number = number;
      return this;
    }

    /**
     * Sets {@link Quotation#issueDate() issueDate}.
     *
     * @param issueDate date of the quotation
     * @return this builder
     */
    public Builder issueDate(LocalDate issueDate) {
      this.issueDate = issueDate;
      return this;
    }

    /**
     * Sets {@link Quotation#validUntil() validUntil}.
     *
     * @param validUntil last day the offer is binding
     * @return this builder
     */
    public Builder validUntil(LocalDate validUntil) {
      this.validUntil = validUntil;
      return this;
    }

    /**
     * Sets {@link Quotation#currencyCode() currencyCode}.
     *
     * @param currencyCode currency of the prices
     * @return this builder
     */
    public Builder currencyCode(CurrencyCode currencyCode) {
      this.currencyCode = currencyCode;
      return this;
    }

    /**
     * Sets {@link Quotation#seller() seller}.
     *
     * @param seller the offering party
     * @return this builder
     */
    public Builder seller(Party seller) {
      this.seller = seller;
      return this;
    }

    /**
     * Sets {@link Quotation#buyer() buyer}.
     *
     * @param buyer the party the offer is made to
     * @return this builder
     */
    public Builder buyer(Party buyer) {
      this.buyer = buyer;
      return this;
    }

    /**
     * Sets {@link Quotation#buyerReference() buyerReference}.
     *
     * @param buyerReference reference of the buyer
     * @return this builder
     */
    public Builder buyerReference(String buyerReference) {
      this.buyerReference = buyerReference;
      return this;
    }

    /**
     * Sets {@link Quotation#subject() subject}.
     *
     * @param subject short subject line
     * @return this builder
     */
    public Builder subject(String subject) {
      this.subject = subject;
      return this;
    }

    /**
     * Sets {@link Quotation#delivery() delivery}.
     *
     * @param delivery delivery information
     * @return this builder
     */
    public Builder delivery(DeliveryInformation delivery) {
      this.delivery = delivery;
      return this;
    }

    /**
     * Sets {@link Quotation#deliveryTerms() deliveryTerms}.
     *
     * @param deliveryTerms free text on delivery time and conditions
     * @return this builder
     */
    public Builder deliveryTerms(String deliveryTerms) {
      this.deliveryTerms = deliveryTerms;
      return this;
    }

    /**
     * Sets {@link Quotation#paymentTerms() paymentTerms}.
     *
     * @param paymentTerms free text on the payment terms
     * @return this builder
     */
    public Builder paymentTerms(String paymentTerms) {
      this.paymentTerms = paymentTerms;
      return this;
    }

    /**
     * Sets {@link Quotation#notes() notes}.
     *
     * @param notes notes of the quotation
     * @return this builder
     */
    public Builder notes(List<InvoiceNote> notes) {
      this.notes = new ArrayList<>(notes);
      return this;
    }

    /**
     * Adds a note.
     *
     * @param value the note
     * @return this builder
     */
    public Builder addNote(InvoiceNote value) {
      this.notes.add(value);
      return this;
    }

    /**
     * Adds a note with the given text.
     *
     * @param value text of the note
     * @return this builder
     */
    public Builder addNote(String value) {
      return addNote(InvoiceNote.of(value));
    }

    /**
     * Sets {@link Quotation#lines() lines}.
     *
     * @param lines the offered lines
     * @return this builder
     */
    public Builder lines(List<QuotationLine> lines) {
      this.lines = new ArrayList<>(lines);
      return this;
    }

    /**
     * Adds a line of the standard offer.
     *
     * @param value the offered line
     * @return this builder
     */
    public Builder addLine(InvoiceLine value) {
      this.lines.add(QuotationLine.standard(value));
      return this;
    }

    /**
     * Adds an optional line.
     *
     * @param value the offered line
     * @return this builder
     */
    public Builder addOptionalLine(InvoiceLine value) {
      this.lines.add(QuotationLine.optional(value));
      return this;
    }

    /**
     * Sets {@link Quotation#adjustments() adjustments}.
     *
     * @param adjustments document allowances and charges
     * @return this builder
     */
    public Builder adjustments(List<DocumentAdjustment> adjustments) {
      this.adjustments = new ArrayList<>(adjustments);
      return this;
    }

    /**
     * Adds a document allowance or charge.
     *
     * @param value the allowance or charge
     * @return this builder
     */
    public Builder addAdjustment(DocumentAdjustment value) {
      this.adjustments.add(value);
      return this;
    }

    /**
     * Constructs a new {@link Quotation} from the current values.
     *
     * @return the constructed value after the model checks succeed
     */
    public Quotation build() {
      return new Quotation(number,
                           issueDate,
                           validUntil,
                           currencyCode,
                           seller,
                           buyer,
                           buyerReference,
                           subject,
                           delivery,
                           deliveryTerms,
                           paymentTerms,
                           notes,
                           lines,
                           adjustments);
    }
  }
}

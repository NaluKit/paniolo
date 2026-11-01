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
package io.github.nalukit.paniolo.pdflayout;

import io.github.nalukit.paniolo.config.PdfLayout;
import io.github.nalukit.paniolo.model.Invoice;
import io.github.nalukit.paniolo.model.common.CashDiscount;
import io.github.nalukit.paniolo.model.common.CreditTransfer;
import io.github.nalukit.paniolo.model.common.DocumentAdjustment;
import io.github.nalukit.paniolo.model.common.InvoiceLine;
import io.github.nalukit.paniolo.model.common.InvoiceNote;
import io.github.nalukit.paniolo.model.common.InvoiceTotals;
import io.github.nalukit.paniolo.model.common.LineAdjustment;
import io.github.nalukit.paniolo.model.common.Party;
import io.github.nalukit.paniolo.model.common.PrecedingInvoiceReference;
import io.github.nalukit.paniolo.model.common.TaxCategory;
import io.github.nalukit.paniolo.model.common.ThirdPartyPayment;
import io.github.nalukit.paniolo.model.common.VatBreakdown;
import io.github.nalukit.paniolo.model.common.value.CurrencyCode;
import io.github.nalukit.paniolo.model.extended.AccountingAccount;
import io.github.nalukit.paniolo.model.extended.AdvancePayment;
import io.github.nalukit.paniolo.model.extended.DocumentReference;
import io.github.nalukit.paniolo.model.extended.ExtendedInformation;
import io.github.nalukit.paniolo.model.extended.FinancialAdjustment;
import io.github.nalukit.paniolo.model.extended.GrossPriceAdjustment;
import io.github.nalukit.paniolo.model.extended.IncludedProduct;
import io.github.nalukit.paniolo.model.extended.LogisticsServiceCharge;
import io.github.nalukit.paniolo.model.extended.ObjectIdentifier;
import io.github.nalukit.paniolo.model.extended.PaymentTerm;
import io.github.nalukit.paniolo.model.extended.PaymentTermsAdjustment;
import io.github.nalukit.paniolo.model.extended.ProductCharacteristic;
import io.github.nalukit.paniolo.model.extended.ProductInstance;
import io.github.nalukit.paniolo.model.extended.TypedDocumentReference;
import io.github.nalukit.paniolo.pdflayout.Canvas.Align;
import io.github.nalukit.paniolo.pdflayout.Canvas.Cell;
import io.github.nalukit.paniolo.pdflayout.Canvas.Column;
import io.github.nalukit.paniolo.pdflayout.Canvas.Row;
import org.apache.pdfbox.pdmodel.PDDocument;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Lays out an invoice or credit note: document data, lines, totals, VAT notes, payment, references,
 * and supporting documents, with the seller as sender. EXTENDED content adds the document status,
 * further references, delivery terms and parties, line details, logistics service charges, and
 * financial adjustments; third party payments of the XRechnung extension appear in the totals.
 */
final class InvoiceRenderer
    extends DocumentRenderer {
  private final Invoice invoice;

  InvoiceRenderer(Invoice invoice,
                  PdfLayout layout) {
    super(layout);
    this.invoice = invoice;
  }

  static String nullToEmpty(String value) {
    return value == null ?
           "" :
           value;
  }

  @Override
  Party sender() {
    return invoice.seller();
  }

  @Override
  Party recipient() {
    return invoice.buyer();
  }

  @Override
  CurrencyCode currency() {
    return invoice.currencyCode();
  }

  @Override
  String documentTitle() {
    return join(" ",
                t.title(invoice.typeCode()),
                invoice.number());
  }

  @Override
  List<String[]> facts() {
    var rows     = new ArrayList<String[]>();
    var extended = invoice.extended();
    fact(rows,
         "number",
         invoice.number());
    if (extended != null) {
      // EXTENDED: a test or copy must not be mistaken for the original invoice.
      fact(rows,
           "documentStatus",
           join(" · ",
                Boolean.TRUE.equals(extended.testIndicator()) ?
                t.label("documentStatus.test") :
                null,
                Boolean.TRUE.equals(extended.copyIndicator()) ?
                t.label("documentStatus.copy") :
                null));
      fact(rows,
           "documentName",
           extended.documentName());
    }
    fact(rows,
         "issueDate",
         t.date(invoice.issueDate()));
    fact(rows,
         "dueDate",
         t.date(invoice.dueDate()));
    if (invoice.invoicePeriod() != null) {
      fact(rows,
           "period",
           join(" · ",
                t.period(invoice.invoicePeriod()
                                .startDate(),
                         invoice.invoicePeriod()
                                .endDate()),
                extended == null ?
                null :
                extended.billingPeriodDescription()));
    }
    if (extended != null) {
      fact(rows,
           "effectiveDate",
           t.date(extended.effectiveDate()));
    }
    if (invoice.delivery() != null) {
      fact(rows,
           "deliveryDate",
           t.date(invoice.delivery()
                         .actualDeliveryDate()));
    }
    fact(rows,
         "taxPointDate",
         t.date(invoice.taxPointDate()));
    fact(rows,
         "buyerReference",
         invoice.buyerReference());
    if (invoice.references() != null) {
      fact(rows,
           "order",
           invoice.references()
                  .purchaseOrderReference());
    }
    Party buyer = invoice.buyer();
    if (buyer != null) {
      fact(rows,
           "customerNumber",
           buyer.identifier());
      fact(rows,
           "buyerVatId",
           buyer.vatId());
    }
    return rows;
  }

  // -- lines --------------------------------------------------------------------------------------

  @Override
  void content(PDDocument document) {
    notes(invoice.notes()
                 .stream()
                 .map(InvoiceNote::text)
                 .toList());
    lines();
    totals();
    vatNotes();
    payment();
    references();
    attachments();
  }

  private void lines() {
    float fixed = 26 + 42 + 50 + 62 + 32 + 66;
    var columns = List.of(new Column(26,
                                     Align.LEFT),
                          new Column(canvas.width() - fixed,
                                     Align.LEFT),
                          new Column(42,
                                     Align.RIGHT),
                          new Column(50,
                                     Align.LEFT),
                          new Column(62,
                                     Align.RIGHT),
                          new Column(32,
                                     Align.RIGHT),
                          new Column(66,
                                     Align.RIGHT));
    var rows = new ArrayList<Row>();
    // EXTENDED states sub-lines flat with the identifier of their parent; indent them like nested
    // sub-lines of the XRechnung extension.
    var parents = new HashMap<String, String>();
    for (InvoiceLine line : invoice.lines()) {
      if (line.extended() != null && line.extended()
                                         .parentLineId() != null) {
        parents.put(line.id(),
                    line.extended()
                        .parentLineId());
      }
    }
    for (InvoiceLine line : invoice.lines()) {
      int    depth  = 0;
      String parent = parents.get(line.id());
      while (parent != null && depth < 10) {
        depth++;
        parent = parents.get(parent);
      }
      rows(rows,
           line,
           depth);
    }
    table(columns,
          header("lines.pos",
                 "lines.item",
                 "lines.quantity",
                 "lines.unit",
                 "lines.price",
                 "lines.vat",
                 "lines.amount"),
          rows);
  }

  private void rows(List<Row> rows,
                    InvoiceLine line,
                    int depth) {
    float indent = depth * 8;
    var item = new Cell().add(line.name(),
                              depth == 0 ?
                              bodyBold :
                              body,
                              indent)
                         .add(line.description(),
                              small.color(TEXT),
                              indent)
                         .add(details(List.of(nullToEmpty(prefixed("line.sellerItemId",
                                                                   line.sellerItemId())),
                                              nullToEmpty(prefixed("line.buyerItemId",
                                                                   line.buyerItemId())),
                                              nullToEmpty(prefixed("line.standardItemId",
                                                                   line.standardItemId())),
                                              line.originCountry() == null ?
                                              "" :
                                              t.label("line.origin") + " " + t.country(line.originCountry()),
                                              line.period() == null ?
                                              "" :
                                              t.label("line.period") +
                                              " " +
                                              t.period(line.period()
                                                           .startDate(),
                                                       line.period()
                                                           .endDate()),
                                              nullToEmpty(prefixed("line.orderLine",
                                                                   line.orderLineReference())),
                                              nullToEmpty(prefixed("line.object",
                                                                   line.objectId())),
                                              nullToEmpty(prefixed("line.accountingReference",
                                                                   line.buyerAccountingReference())),
                                              lineExtension(line)),
                                      line.attributes()),
                              small,
                              indent);
    if (line.grossPrice() != null && nonZero(line.priceDiscount())) {
      item.add(t.label("line.grossPrice",
                       money(line.grossPrice())) +
               " " +
               t.label("line.priceDiscount",
                       money(line.priceDiscount())),
               small,
               indent);
    }
    for (LineAdjustment adjustment : line.adjustments()) {
      item.add(adjustmentText("line",
                              adjustment.charge(),
                              adjustment.reason(),
                              adjustment.percentage(),
                              adjustment.baseAmount()) +
               ": " +
               money(adjustment.charge() ?
                     adjustment.amount() :
                     adjustment.amount()
                               .negate()),
               small,
               indent);
    }
    item.add(line.note(),
             small,
             indent);

    var price = new Cell().add(money(line.netUnitPrice()),
                               body);
    if (line.priceBaseQuantity() != null &&
        line.priceBaseQuantity()
            .compareTo(BigDecimal.ONE) != 0) {
      price.add(t.label("line.perQuantity",
                        t.number(line.priceBaseQuantity()),
                        t.unit(line.unitCode())),
                small);
    }
    rows.add(new Row(List.of(new Cell().add(line.id(),
                                            depth == 0 ?
                                            body :
                                            small),
                             item,
                             new Cell().add(t.number(line.quantity()),
                                            body),
                             new Cell().add(t.unit(line.unitCode()),
                                            body),
                             price,
                             new Cell().add(rate(line.tax()),
                                            body),
                             new Cell().add(money(line.netAmount()),
                                            depth == 0 ?
                                            bodyBold :
                                            body)),
                     line.subLines()
                         .isEmpty()));
    for (InvoiceLine sub : line.subLines()) {
      rows(rows,
           sub,
           depth + 1);
    }
  }

  private String rate(TaxCategory tax) {
    if (tax == null) {
      return "";
    }
    return tax.rate() != null ?
           t.percent(tax.rate()) :
           tax.code() == null ?
           "" :
           tax.code()
              .getCode();
  }

  // -- totals -------------------------------------------------------------------------------------

  private void totals() {
    InvoiceTotals totals = invoice.totals();
    var           rows   = new ArrayList<TotalRow>();
    if (!invoice.lines()
                .isEmpty() || totals.lineTotal() != null) {
      rows.add(plain(t.label("totals.lines"),
                     money(totals.lineTotal())));
    }
    for (DocumentAdjustment adjustment : invoice.adjustments()) {
      String text = adjustmentText("totals",
                                   adjustment.charge(),
                                   adjustment.reason(),
                                   adjustment.percentage(),
                                   adjustment.baseAmount());
      if (adjustment.tax() != null &&
          adjustment.tax()
                    .rate() != null) {
        text += " (" + t.label("lines.vat") + " " + t.percent(adjustment.tax()
                                                                        .rate()) + ")";
      }
      rows.add(plain(text,
                     money(adjustment.charge() ?
                           adjustment.amount() :
                           adjustment.amount()
                                     .negate())));
    }
    var extended = invoice.extended();
    if (extended != null) {
      // EXTENDED logistics service charges count like document level charges.
      for (LogisticsServiceCharge service : extended.logisticsServiceCharges()) {
        rows.add(plain(t.label("totals.logistics",
                               service.description()) + vatOf(service.tax()),
                       money(service.amount())));
      }
    }
    rows.add(strong(t.label("totals.net"),
                    money(totals.taxExclusiveAmount())));
    for (VatBreakdown vat : totals.vatBreakdowns()) {
      TaxCategory tax = vat.tax();
      String text = tax != null && nonZero(tax.rate()) ?
                    t.label("totals.vat",
                            t.percent(tax.rate()),
                            money(vat.taxableAmount())) :
                    t.label("totals.vatCategory",
                            tax == null || tax.code() == null ?
                            "" :
                            t.vatCategory(tax.code()),
                            money(vat.taxableAmount()));
      rows.add(plain(text,
                     money(vat.taxAmount())));
    }
    rows.add(strong(t.label("totals.gross"),
                    money(totals.taxInclusiveAmount())));
    if (nonZero(totals.paidAmount())) {
      rows.add(plain(t.label("totals.paid"),
                     money(totals.paidAmount()
                                 .negate())));
    }
    if (nonZero(totals.roundingAmount())) {
      rows.add(plain(t.label("totals.rounding"),
                     money(totals.roundingAmount())));
    }
    // The amount due includes EXTENDED financial adjustments and XRechnung third party payments;
    // each gets a row so that the rows add up to the amount due.
    if (extended != null) {
      for (FinancialAdjustment adjustment : extended.financialAdjustments()) {
        rows.add(plain(adjustment.reason() == null ?
                       t.label("totals.financialAdjustment") :
                       adjustment.reason(),
                       money(adjustment.amount())));
      }
    }
    for (ThirdPartyPayment payment : invoice.thirdPartyPayments()) {
      rows.add(plain(join(": ",
                          t.label("totals.thirdParty"),
                          join(" · ",
                               payment.type(),
                               payment.description())),
                     money(payment.amount())));
    }
    rows.add(grand(t.label("totals.payable"),
                   money(totals.payableAmount())));
    if (invoice.taxCurrencyCode() != null && invoice.taxAmountInTaxCurrency() != null) {
      rows.add(new TotalRow(t.label("totals.vatInTaxCurrency",
                                    invoice.taxCurrencyCode()
                                           .getCode()),
                            t.money(invoice.taxAmountInTaxCurrency(),
                                    invoice.taxCurrencyCode()),
                            small,
                            small,
                            0,
                            0));
      if (extended != null && extended.taxExchangeRate() != null) {
        rows.add(new TotalRow(t.label("totals.exchangeRate"),
                              join(" · ",
                                   t.number(extended.taxExchangeRate()
                                                    .rate()),
                                   t.date(extended.taxExchangeRate()
                                                  .date())),
                              small,
                              small,
                              0,
                              0));
      }
    }
    totals(rows);
  }

  /**
   * The VAT rate of an allowance, charge, or logistics service charge in brackets, or nothing.
   */
  private String vatOf(TaxCategory tax) {
    return tax == null || tax.rate() == null ?
           "" :
           " (" + t.label("lines.vat") + " " + t.percent(tax.rate()) + ")";
  }

  // -- VAT notes, payment, references -------------------------------------------------------------

  private void vatNotes() {
    Set<String> notes = new LinkedHashSet<>();
    for (VatBreakdown vat : invoice.totals()
                                   .vatBreakdowns()) {
      TaxCategory tax = vat.tax();
      if (tax == null || tax.code() == null) {
        continue;
      }
      String code = tax.code()
                       .getCode();
      if (tax.exemptionReason() != null || tax.exemptionReasonCode() != null) {
        notes.add(join(" ",
                       tax.exemptionReason(),
                       tax.exemptionReasonCode() == null ?
                       null :
                       "(" + tax.exemptionReasonCode() + ")"));
      } else if (t.optional("vat.note." + code) != null) {
        notes.add(t.label("vat.note." + code));
      }
    }
    if (notes.isEmpty()) {
      return;
    }
    section("vat.notes");
    for (String note : notes) {
      canvas.paragraph(note,
                       body,
                       canvas.left,
                       canvas.width());
    }
    canvas.space(6);
  }

  private void payment() {
    var rows        = new ArrayList<String[]>();
    var instruction = invoice.paymentInstruction();
    if (instruction != null) {
      if (instruction.meansCode() != null) {
        row(rows,
            "payment.means",
            instruction.meansText() != null &&
            !instruction.meansText()
                        .isBlank() ?
            instruction.meansText() :
            t.paymentMeans(instruction.meansCode()));
      } else {
        row(rows,
            "payment.means",
            instruction.meansText());
      }
      for (CreditTransfer transfer : instruction.creditTransfers()) {
        row(rows,
            "payment.iban",
            Texts.iban(transfer.iban()));
        row(rows,
            "payment.bic",
            transfer.bic());
        row(rows,
            "payment.accountName",
            transfer.accountName());
      }
      if (instruction.paymentCard() != null) {
        row(rows,
            "payment.card",
            join(" · ",
                 instruction.paymentCard()
                            .primaryAccountNumber(),
                 instruction.paymentCard()
                            .holderName()));
      }
      row(rows,
          "payment.remittance",
          instruction.remittanceInformation());
    }
    row(rows,
        "payment.dueDate",
        t.date(invoice.dueDate()));
    CashDiscount.Terms terms = CashDiscount.parse(invoice.paymentTerms());
    row(rows,
        "payment.terms",
        terms.text());
    var discounts = new LinkedHashSet<CashDiscount>(terms.discounts());
    discounts.addAll(invoice.cashDiscounts());
    for (CashDiscount discount : discounts) {
      String until = invoice.issueDate() == null ?
                     "" :
                     t.date(invoice.issueDate()
                                   .plusDays(discount.days()));
      rows.add(new String[] { "",
                              discount.baseAmount() == null ?
                              t.label("payment.discount",
                                      t.percent(discount.percent()),
                                      String.valueOf(discount.days()),
                                      until) :
                              t.label("payment.discountBase",
                                      t.percent(discount.percent()),
                                      String.valueOf(discount.days()),
                                      until,
                                      money(discount.baseAmount())) });
    }
    if (instruction != null && instruction.directDebit() != null) {
      var debit = instruction.directDebit();
      rows.add(new String[] { "",
                              debit.debitedAccountId() == null ?
                              t.label("payment.directDebitNoAccount") :
                              t.label("payment.directDebit",
                                      Texts.iban(debit.debitedAccountId())) });
      row(rows,
          "payment.mandate",
          debit.mandateReference());
      row(rows,
          "payment.creditorId",
          debit.creditorId());
      if (invoice.extended() != null) {
        row(rows,
            "payment.payerAccountName",
            invoice.extended()
                   .payerAccountName());
        row(rows,
            "payment.payerBic",
            invoice.extended()
                   .payerBic());
      }
    }
    if (invoice.payee() != null) {
      row(rows,
          "payment.payee",
          invoice.payee()
                 .name());
    }
    var extended = invoice.extended();
    if (extended != null) {
      // EXTENDED: further payment terms and advance payments, apart from preceding invoices.
      for (PaymentTerm term : extended.paymentTerms()) {
        row(rows,
            "payment.additionalTerm",
            join(" · ",
                 term.description(),
                 term.dueDate() == null ?
                 null :
                 t.label("payment.termDue",
                         t.date(term.dueDate())),
                 term.partialAmount() == null ?
                 null :
                 t.label("payment.partialAmount",
                         money(term.partialAmount())),
                 adjustment("payment.termDiscount",
                            term.discount()),
                 adjustment("payment.penalty",
                            term.penalty())));
      }
      for (AdvancePayment advance : extended.advancePayments()) {
        row(rows,
            "payment.advance",
            join(" · ",
                 money(advance.amount()),
                 advance.receivedDate() == null ?
                 null :
                 t.date(advance.receivedDate()),
                 advance.invoiceReference() == null ?
                 null :
                 t.label("payment.advanceInvoice",
                         advance.invoiceReference()),
                 advance.taxAmount() == null ?
                 null :
                 t.label("payment.advanceVat",
                         advance.tax()
                                .rate() == null ?
                         advance.tax()
                                .code()
                                .name() :
                         t.percent(advance.tax()
                                          .rate()),
                         money(advance.taxAmount()))));
      }
    }
    factsSection("payment",
                 rows);
  }

  /**
   * EXTENDED line details: kind of line, product, quantities, parties, references, and the stated
   * VAT and gross amounts of the line. Codes that only qualify another value (note content and
   * subject codes, accounting type codes, line status codes) are not shown.
   */
  private String lineExtension(InvoiceLine line) {
    var extended = line.extended();
    if (extended == null) {
      return "";
    }
    String unit = t.unit(line.unitCode());
    var    parts = new ArrayList<String>();
    if (extended.subtype() != null && !extended.subtype()
                                               .counts()) {
      parts.add(t.label("line.subtype." + extended.subtype()
                                                  .name()));
    }
    parts.add(prefixed("line.productId",
                       extended.productId()));
    parts.add(prefixed("line.industryItemId",
                       extended.industryItemId()));
    parts.add(prefixed("line.model",
                       join(" ",
                            extended.modelName(),
                            extended.modelId())));
    parts.add(prefixed("line.brand",
                       extended.brandName()));
    parts.add(prefixed("line.manufacturer",
                       extended.manufacturer() == null ?
                       null :
                       extended.manufacturer()
                               .name()));
    parts.add(prefixed("line.batch",
                       String.join(", ",
                                   extended.batchIds())));
    for (ProductInstance instance : extended.instances()) {
      parts.add(join(" ",
                     prefixed("line.batch",
                              instance.batchId()),
                     prefixed("line.serial",
                              instance.serialId())));
    }
    for (ProductCharacteristic characteristic : extended.characteristics()) {
      parts.add(join(": ",
                     characteristic.description(),
                     join(" ",
                          characteristic.value(),
                          characteristic.measure() == null ?
                          null :
                          t.number(characteristic.measure()),
                          t.unit(characteristic.measureUnit()))));
    }
    for (IncludedProduct included : extended.includedProducts()) {
      parts.add(prefixed("line.includes",
                         join(" ",
                              included.quantity() == null ?
                              null :
                              t.number(included.quantity()),
                              t.unit(included.unitCode()),
                              included.name(),
                              included.id())));
    }
    parts.add(extended.chargeFreeQuantity() == null ?
              null :
              t.label("line.chargeFree",
                      t.number(extended.chargeFreeQuantity()),
                      unit));
    parts.add(extended.packageQuantity() == null ?
              null :
              t.label("line.packages",
                      t.number(extended.packageQuantity()),
                      extended.perPackageUnitQuantity() == null ?
                      "" :
                      t.number(extended.perPackageUnitQuantity()) + " " + unit));
    parts.add(prefixed("line.deliveryTerms",
                       extended.deliveryTermsCode()));
    parts.add(prefixed("line.itemSeller",
                       join(" · ",
                            extended.itemSeller() == null ?
                            null :
                            extended.itemSeller()
                                    .name(),
                            extended.itemSeller() == null ?
                            null :
                            extended.itemSeller()
                                    .legalRegistrationId(),
                            extended.itemSeller() == null ?
                            null :
                            extended.itemSeller()
                                    .vatId())));
    for (Object[] party : new Object[][] { { "line.shipTo",
                                             extended.shipTo() },
                                           { "line.ultimateShipTo",
                                             extended.ultimateShipTo() } }) {
      Party value = (Party) party[1];
      parts.add(value == null ?
                null :
                prefixed((String) party[0],
                         join(", ",
                              value.name(),
                              value.address() == null ?
                              null :
                              join(" ",
                                   value.address()
                                        .postalCode(),
                                   value.address()
                                        .city()))));
    }
    parts.add(extended.actualDeliveryDate() == null ?
              null :
              t.label("line.deliveryDate") + " " + t.date(extended.actualDeliveryDate()));
    for (Object[] reference : new Object[][] { { "line.salesOrder",
                                                 extended.sellerOrderReference() },
                                               { "line.quotation",
                                                 extended.quotationReference() },
                                               { "line.contract",
                                                 extended.contractReference() },
                                               { "line.deliveryNote",
                                                 extended.deliveryNoteReference() },
                                               { "line.despatchAdvice",
                                                 extended.despatchAdviceReference() },
                                               { "line.receivingAdvice",
                                                 extended.receivingAdviceReference() },
                                               { "line.precedingInvoice",
                                                 extended.precedingInvoiceReference() } }) {
      parts.add(prefixed((String) reference[0],
                         reference((DocumentReference) reference[1])));
    }
    for (DocumentReference order : extended.ultimateCustomerOrderReferences()) {
      parts.add(prefixed("line.ultimateCustomerOrder",
                         reference(order)));
    }
    for (TypedDocumentReference reference : extended.additionalReferences()) {
      parts.add(prefixed("line.reference",
                         join(" ",
                              reference.id(),
                              reference.referenceTypeCode() == null ?
                              null :
                              "(" + reference.referenceTypeCode() + ")")));
    }
    for (ObjectIdentifier object : extended.furtherObjectIdentifiers()) {
      parts.add(prefixed("line.object",
                         object.id()));
    }
    for (AccountingAccount account : extended.additionalAccountingAccounts()) {
      parts.add(prefixed("line.accountingReference",
                         account.id()));
    }
    for (GrossPriceAdjustment adjustment : extended.priceAdjustments()) {
      parts.add(join(": ",
                     adjustment.reason() == null ?
                     t.label(adjustment.charge() ?
                             "line.chargeDefault" :
                             "line.allowanceDefault") :
                     adjustment.reason(),
                     money(adjustment.charge() ?
                           adjustment.amount() :
                           adjustment.amount()
                                     .negate())));
    }
    parts.add(extended.taxTotal() == null ?
              null :
              t.label("line.lineVat",
                      money(extended.taxTotal())));
    parts.add(extended.grossTotal() == null ?
              null :
              t.label("line.lineGross",
                      money(extended.grossTotal())));
    return nullToEmpty(join(" · ",
                            parts.toArray(String[]::new)));
  }

  /**
   * A discount or penalty of a payment term: percentage and amount as stated.
   */
  private String adjustment(String key,
                            PaymentTermsAdjustment adjustment) {
    if (adjustment == null || adjustment.percent() == null && adjustment.amount() == null) {
      return null;
    }
    return t.label(key,
                   join(" / ",
                        adjustment.percent() == null ?
                        null :
                        t.percent(adjustment.percent()),
                        adjustment.amount() == null ?
                        null :
                        money(adjustment.amount())));
  }

  private void references() {
    var rows = new ArrayList<String[]>();
    var refs = invoice.references();
    if (refs != null) {
      row(rows,
          "salesOrder",
          refs.salesOrderReference());
      row(rows,
          "contract",
          refs.contractReference());
      row(rows,
          "project",
          refs.projectReference());
      row(rows,
          "despatchAdvice",
          refs.despatchAdviceReference());
      row(rows,
          "receivingAdvice",
          refs.receivingAdviceReference());
      row(rows,
          "tender",
          refs.tenderOrLotReference());
      row(rows,
          "object",
          refs.invoicedObjectId());
      row(rows,
          "accountingReference",
          refs.buyerAccountingReference());
    }
    for (PrecedingInvoiceReference preceding : invoice.precedingInvoiceReferences()) {
      row(rows,
          "precedingInvoice",
          preceding.issueDate() == null ?
          preceding.number() :
          t.label("precedingInvoiceOf",
                  preceding.number(),
                  t.date(preceding.issueDate())));
    }
    var delivery = invoice.delivery();
    if (delivery != null && (delivery.partyName() != null || delivery.address() != null || delivery.locationId() != null)) {
      var address = new ArrayList<String>();
      add(address,
          delivery.partyName());
      address.addAll(addressLines(delivery.address(),
                                  true));
      add(address,
          delivery.locationId());
      row(rows,
          "deliverTo",
          String.join("\n",
                      address));
    }
    var representative = invoice.taxRepresentative();
    if (representative != null) {
      var lines = new ArrayList<String>();
      add(lines,
          representative.name());
      lines.addAll(addressLines(representative.address(),
                                true));
      add(lines,
          prefixed("seller.vatId",
                   representative.vatId()));
      row(rows,
          "taxRepresentative",
          String.join("\n",
                      lines));
    }
    var extended = invoice.extended();
    if (extended != null) {
      extendedReferences(rows,
                         extended);
    }
    factsSection("references",
                 rows);
  }

  /**
   * EXTENDED references, delivery terms, and further parties of the document.
   */
  private void extendedReferences(List<String[]> rows,
                                  ExtendedInformation extended) {
    row(rows,
        "quotation",
        reference(extended.quotationReference()));
    row(rows,
        "deliveryNote",
        reference(extended.deliveryNoteReference()));
    row(rows,
        "ultimateCustomerOrder",
        join(", ",
             extended.ultimateCustomerOrderReferences()
                     .stream()
                     .map(this::reference)
                     .toArray(String[]::new)));
    row(rows,
        "invoiceIssuerReference",
        extended.invoiceIssuerReference());
    row(rows,
        "furtherAccountingReferences",
        join(", ",
             extended.additionalAccountingAccounts()
                     .stream()
                     .map(AccountingAccount::id)
                     .toArray(String[]::new)));
    row(rows,
        "deliveryTerms",
        join(" · ",
             extended.deliveryTermsCode(),
             extended.deliveryTermsLocationName(),
             extended.deliveryTermsLocationCountry() == null ?
             null :
             t.country(extended.deliveryTermsLocationCountry())));
    if (extended.transportModeCode() != null) {
      String name = t.optional("transportMode." + extended.transportModeCode());
      row(rows,
          "transportMode",
          name == null ?
          extended.transportModeCode() :
          name + " (" + extended.transportModeCode() + ")");
    }
    for (Object[] party : new Object[][] { { "party.salesAgent",
                                             extended.salesAgent() },
                                           { "party.buyerAgent",
                                             extended.buyerAgent() },
                                           { "party.buyerTaxRepresentative",
                                             extended.buyerTaxRepresentative() },
                                           { "party.productEndUser",
                                             extended.productEndUser() },
                                           { "party.shipFrom",
                                             extended.shipFrom() },
                                           { "party.ultimateShipTo",
                                             extended.ultimateShipTo() },
                                           { "party.invoicer",
                                             extended.invoicer() },
                                           { "party.invoicee",
                                             extended.invoicee() },
                                           { "party.payer",
                                             extended.payer() } }) {
      row(rows,
          (String) party[0],
          party((Party) party[1]));
    }
  }

  /**
   * A further party in three lines: name, address, and identifiers.
   */
  private String party(Party party) {
    if (party == null) {
      return null;
    }
    var lines = new ArrayList<String>();
    add(lines,
        party.name());
    add(lines,
        String.join(", ",
                    addressLines(party.address(),
                                 true)));
    add(lines,
        join(" · ",
             prefixed("seller.vatId",
                      party.vatId()),
             prefixed("seller.legal",
                      party.legalRegistrationId()),
             party.identifier()));
    return String.join("\n",
                       lines);
  }

  /**
   * A referenced document: number, line, and date.
   */
  private String reference(DocumentReference reference) {
    if (reference == null) {
      return null;
    }
    return join(" · ",
                reference.id(),
                prefixed("referenceLine",
                         reference.lineId()),
                t.date(reference.issueDate()));
  }

  private void attachments() {
    supportingDocuments(invoice.supportingDocuments());
  }
}

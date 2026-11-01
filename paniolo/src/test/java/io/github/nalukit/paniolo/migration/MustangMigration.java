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
package io.github.nalukit.paniolo.migration;

import io.github.nalukit.paniolo.migration.MustangInterfaces.AllowanceCharge;
import io.github.nalukit.paniolo.migration.MustangInterfaces.ExportableTransaction;
import io.github.nalukit.paniolo.migration.MustangInterfaces.Item;
import io.github.nalukit.paniolo.migration.MustangInterfaces.TradeParty;
import io.github.nalukit.paniolo.model.Invoice;
import io.github.nalukit.paniolo.model.common.AdditionalSupportingDocument;
import io.github.nalukit.paniolo.model.common.Address;
import io.github.nalukit.paniolo.model.common.Attachment;
import io.github.nalukit.paniolo.model.common.CashDiscount;
import io.github.nalukit.paniolo.model.common.Contact;
import io.github.nalukit.paniolo.model.common.CreditTransfer;
import io.github.nalukit.paniolo.model.common.DeliveryInformation;
import io.github.nalukit.paniolo.model.common.DirectDebit;
import io.github.nalukit.paniolo.model.common.DocumentAdjustment;
import io.github.nalukit.paniolo.model.common.ElectronicAddress;
import io.github.nalukit.paniolo.model.common.InvoiceLine;
import io.github.nalukit.paniolo.model.common.InvoiceNote;
import io.github.nalukit.paniolo.model.common.InvoicePeriod;
import io.github.nalukit.paniolo.model.common.LineAdjustment;
import io.github.nalukit.paniolo.model.common.Party;
import io.github.nalukit.paniolo.model.common.Payee;
import io.github.nalukit.paniolo.model.common.PaymentInstruction;
import io.github.nalukit.paniolo.model.common.PrecedingInvoiceReference;
import io.github.nalukit.paniolo.model.common.ProcessControl;
import io.github.nalukit.paniolo.model.common.References;
import io.github.nalukit.paniolo.model.common.TaxCategory;
import io.github.nalukit.paniolo.model.common.value.Country;
import io.github.nalukit.paniolo.model.common.value.CurrencyCode;
import io.github.nalukit.paniolo.model.common.value.DateTimeQualifier;
import io.github.nalukit.paniolo.model.common.value.DutyTaxFeeCategory;
import io.github.nalukit.paniolo.model.common.value.InvoiceTypeCode;
import io.github.nalukit.paniolo.model.common.value.PaymentMeansCode;
import io.github.nalukit.paniolo.model.extended.DocumentReference;
import io.github.nalukit.paniolo.model.extended.ExtendedContact;
import io.github.nalukit.paniolo.model.extended.ExtendedInformation;
import io.github.nalukit.paniolo.model.extended.LineExtension;
import io.github.nalukit.paniolo.model.extended.LogisticsServiceCharge;
import io.github.nalukit.paniolo.model.extended.PartyExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Migrates an integration that implements Mustang's export interfaces to the Paniolo model. The
 * mapping is explicit: {@link #FIELDS} states for every getter whether it is mapped, lost, or needs
 * a decision, and {@link Result#lost()} names the values of one document that could not be carried
 * over.
 *
 * <p>This is sample code for the migration guide, not part of Paniolo: copy and adapt it.
 */
final class MustangMigration {
  /**
   * Every getter of the interfaces in {@link MustangInterfaces}, with its Paniolo counterpart.
   */
  static final List<FieldMapping> FIELDS = List.of(
      // Document
      FieldMapping.mapped("ExportableTransaction.getDocumentCode", "BT-3 Invoice.typeCode", "InvoiceTypeCode.of(code)"),
      FieldMapping.decision("ExportableTransaction.getDocumentName", "ExtendedInformation.documentName", "EXTENDED only; other profiles derive the name from the type code"),
      FieldMapping.decision("ExportableTransaction.getTestIndicator", "ExtendedInformation.testIndicator", "EXTENDED only"),
      FieldMapping.mapped("ExportableTransaction.getNumber", "BT-1 Invoice.number", ""),
      FieldMapping.mapped("ExportableTransaction.getIssueDate", "BT-2 Invoice.issueDate", "java.util.Date to LocalDate in the time zone of the application"),
      FieldMapping.mapped("ExportableTransaction.getDueDate", "BT-9 Invoice.dueDate", "PaymentTerms.getDueDate if not set"),
      FieldMapping.mapped("ExportableTransaction.getDeliveryDate", "BT-72 DeliveryInformation.actualDeliveryDate", ""),
      FieldMapping.mapped("ExportableTransaction.getCurrency", "BT-5 Invoice.currencyCode", "CurrencyCode.of(code)"),
      FieldMapping.mapped("ExportableTransaction.getBusinessProcessId", "BT-23 ProcessControl.businessProcessId", "if null, the one of the target profile (XRechnung requires BT-23); BT-24 comes from the target profile"),
      FieldMapping.mapped("ExportableTransaction.getReferenceNumber", "BT-10 Invoice.buyerReference", "Leitweg-ID for XRechnung"),
      // Parties
      FieldMapping.mapped("ExportableTransaction.getSender", "BG-4 Invoice.seller", "see TradeParty"),
      FieldMapping.mapped("ExportableTransaction.getRecipient", "BG-7 Invoice.buyer", "see TradeParty"),
      FieldMapping.mapped("ExportableTransaction.getPayee", "BG-10 Invoice.payee", "name, identifier and legal registration only; EN 16931 has no payee address or contact"),
      FieldMapping.mapped("ExportableTransaction.getDeliveryAddress", "BG-13 DeliveryInformation", "name, location identifier and address; tax and contact data of the ship-to party are lost"),
      FieldMapping.decision("ExportableTransaction.getInvoicee", "ExtendedInformation.invoicee", "EXTENDED only"),
      FieldMapping.mapped("TradeParty.getName", "BT-27 / BT-44 Party.name", ""),
      FieldMapping.mapped("TradeParty.getStreet", "BT-35 / BT-50 Address.street", ""),
      FieldMapping.mapped("TradeParty.getAdditionalAddress", "BT-36 / BT-51 Address.additionalStreet", ""),
      FieldMapping.mapped("TradeParty.getZIP", "BT-38 / BT-53 Address.postalCode", ""),
      FieldMapping.mapped("TradeParty.getLocation", "BT-37 / BT-52 Address.city", ""),
      FieldMapping.mapped("TradeParty.getCountry", "BT-40 / BT-55 Address.country", "Country.of(code)"),
      FieldMapping.mapped("TradeParty.getVATID", "BT-31 / BT-48 Party.vatId", ""),
      FieldMapping.mapped("TradeParty.getTaxID", "BT-32 Party.taxId", "seller only; a buyer tax number is lost"),
      FieldMapping.decision("TradeParty.getID", "BT-29 / BT-46 Party.identifier", "Paniolo has one party identifier: the global identifier wins, a second ID is lost"),
      FieldMapping.mapped("TradeParty.getGlobalID", "BT-29 / BT-46 Party.identifier", "with scheme"),
      FieldMapping.mapped("TradeParty.getGlobalIDScheme", "BT-29-1 / BT-46-1 Party.identifierSchemeId", ""),
      FieldMapping.mapped("TradeParty.getUriUniversalCommunicationID", "BT-34 / BT-49 ElectronicAddress.value", ""),
      FieldMapping.mapped("TradeParty.getUriUniversalCommunicationIDScheme", "BT-34-1 / BT-49-1 ElectronicAddress.schemeId", ""),
      FieldMapping.mapped("TradeParty.getContact", "BG-6 / BG-9 Party.contact", "see Contact"),
      FieldMapping.mapped("TradeParty.getLegalOrganisation", "BT-30 / BT-47, BT-28 / BT-45", "see LegalOrganisation"),
      FieldMapping.mapped("TradeParty.getDescription", "BT-33 Party.additionalLegalInformation", "seller only; a buyer description is lost"),
      FieldMapping.mapped("Contact.getName", "BT-41 / BT-56 Contact.name", ""),
      FieldMapping.mapped("Contact.getPhone", "BT-42 / BT-57 Contact.telephone", ""),
      FieldMapping.mapped("Contact.getEMail", "BT-43 / BT-58 Contact.email", ""),
      FieldMapping.decision("Contact.getFax", "PartyExtension.contact (ExtendedContact.fax)", "EXTENDED only, which then carries the whole contact"),
      FieldMapping.mapped("LegalOrganisation.getSchemedID", "BT-30 / BT-47 Party.legalRegistrationId", "see SchemedID"),
      FieldMapping.mapped("LegalOrganisation.getTradingBusinessName", "BT-28 / BT-45 Party.tradingName", ""),
      FieldMapping.mapped("SchemedID.getScheme", "BT-30-1 / BT-47-1 Party.legalRegistrationSchemeId", ""),
      FieldMapping.mapped("SchemedID.getID", "BT-30 / BT-47 Party.legalRegistrationId", ""),
      // Lines
      FieldMapping.mapped("ExportableTransaction.getZFItems", "BG-25 Invoice.lines", "see Item"),
      FieldMapping.mapped("Item.getId", "BT-126 InvoiceLine.id", "Mustang numbers lines itself if null; Paniolo needs the ID"),
      FieldMapping.mapped("Item.getProduct", "BG-31 item information", "see Product"),
      FieldMapping.mapped("Item.getPrice", "BT-148 InvoiceLine.grossPrice, BT-146 netUnitPrice", "Mustang's price is the gross price; net price = price - product allowances"),
      FieldMapping.mapped("Item.getQuantity", "BT-129 InvoiceLine.quantity", ""),
      FieldMapping.mapped("Item.getBasisQuantity", "BT-149 InvoiceLine.priceBaseQuantity", ""),
      FieldMapping.mapped("Item.getItemAllowances", "BG-27 InvoiceLine.adjustments", "line level allowances (LineAdjustment)"),
      FieldMapping.mapped("Item.getItemCharges", "BG-28 InvoiceLine.adjustments", "line level charges (LineAdjustment)"),
      FieldMapping.decision("Item.getNotes", "BT-127 InvoiceLine.note", "EN 16931 has one line note: several notes are joined with line breaks"),
      FieldMapping.mapped("Item.getDetailedDeliveryPeriodFrom", "BT-134 InvoiceLine.period", ""),
      FieldMapping.mapped("Item.getDetailedDeliveryPeriodTo", "BT-135 InvoiceLine.period", ""),
      FieldMapping.mapped("Item.getBuyerOrderReferencedDocumentLineID", "BT-132 InvoiceLine.orderLineReference", ""),
      FieldMapping.mapped("Item.getAccountingReference", "BT-133 InvoiceLine.buyerAccountingReference", ""),
      FieldMapping.decision("Item.getLineTotalAmount", "BT-131 InvoiceLine.netAmount", "Paniolo calculates the amount; a stated amount must agree up to the rounding of the net price, see ImportResult (G01) for received documents"),
      FieldMapping.decision("Item.getParentLineID", "LineExtension.parentLineId", "EXTENDED only; XRechnung sub-lines are nested lines (InvoiceLine.addSubLine) instead"),
      FieldMapping.mapped("Product.getName", "BT-153 InvoiceLine.name", ""),
      FieldMapping.mapped("Product.getDescription", "BT-154 InvoiceLine.description", ""),
      FieldMapping.mapped("Product.getUnit", "BT-130 InvoiceLine.unitCode", "UN/ECE Rec 20 code"),
      FieldMapping.mapped("Product.getVATPercent", "BT-152 TaxCategory.rate", "no rate for category O"),
      FieldMapping.mapped("Product.getTaxCategoryCode", "BT-151 TaxCategory.code", "DutyTaxFeeCategory.of(code)"),
      FieldMapping.mapped("Product.getTaxExemptionReason", "BT-120 TaxCategory.exemptionReason", ""),
      FieldMapping.mapped("Product.getTaxExemptionReasonCode", "BT-121 TaxCategory.exemptionReasonCode", ""),
      FieldMapping.mapped("Product.getSellerAssignedID", "BT-155 InvoiceLine.sellerItemId", ""),
      FieldMapping.mapped("Product.getBuyerAssignedID", "BT-156 InvoiceLine.buyerItemId", ""),
      FieldMapping.mapped("Product.getGlobalID", "BT-157 InvoiceLine.standardItemId", ""),
      FieldMapping.mapped("Product.getGlobalIDScheme", "BT-157-1 InvoiceLine.standardItemSchemeId", ""),
      FieldMapping.mapped("Product.getCountryOfOrigin", "BT-159 InvoiceLine.originCountry", ""),
      FieldMapping.mapped("Product.getAttributes", "BG-32 InvoiceLine.attributes", ""),
      FieldMapping.mapped("Product.getAllowances", "BT-147 InvoiceLine.priceDiscount", "sum of the absolute amounts per unit; reasons and percentages are lost"),
      FieldMapping.decision("Product.getCharges", "BT-146 InvoiceLine.netUnitPrice", "EN 16931 has no price level charge (PEPPOL-EN16931-R044): added to the net price"),
      // Allowances and charges
      FieldMapping.mapped("ExportableTransaction.getZFAllowances", "BG-20 Invoice.adjustments", "DocumentAdjustment.createAllowance()"),
      FieldMapping.mapped("ExportableTransaction.getZFCharges", "BG-21 Invoice.adjustments", "DocumentAdjustment.createCharge()"),
      FieldMapping.decision("ExportableTransaction.getZFLogisticsServiceCharges", "ExtendedInformation.logisticsServiceCharges", "EXTENDED; other profiles carry them as document charges"),
      FieldMapping.mapped("AllowanceCharge.getTotalAmount", "BT-92 / BT-99 / BT-136 / BT-141 amount", ""),
      FieldMapping.mapped("AllowanceCharge.getPercent", "BT-94 / BT-101 / BT-138 / BT-143 percentage", ""),
      FieldMapping.mapped("AllowanceCharge.getBasisAmount", "BT-93 / BT-100 / BT-137 / BT-142 baseAmount", ""),
      FieldMapping.mapped("AllowanceCharge.getReason", "BT-97 / BT-104 / BT-139 / BT-144 reason", ""),
      FieldMapping.mapped("AllowanceCharge.getReasonCode", "BT-98 / BT-105 / BT-140 / BT-145 reasonCode", "UNTDID 5189 for allowances, 7161 for charges"),
      FieldMapping.mapped("AllowanceCharge.getTaxPercent", "BT-96 / BT-103 TaxCategory.rate", "document level only; a line adjustment takes the VAT of its line"),
      FieldMapping.mapped("AllowanceCharge.getCategoryCode", "BT-95 / BT-102 TaxCategory.code", "document level only"),
      FieldMapping.mapped("AllowanceCharge.isCharge", "DocumentAdjustment.charge / LineAdjustment.charge", ""),
      // Payment
      FieldMapping.mapped("ExportableTransaction.getTradeSettlementPayment", "BG-16 PaymentInstruction, BG-17 CreditTransfer", "one payment means per invoice; every account becomes a CreditTransfer"),
      FieldMapping.decision("ExportableTransaction.getTradeSettlement", "BG-19 DirectDebit", "used if there is no credit transfer; EN 16931 has one payment means code per invoice"),
      FieldMapping.mapped("ExportableTransaction.getCreditorReferenceID", "BT-90 DirectDebit.creditorId", "only together with a direct debit"),
      FieldMapping.mapped("ExportableTransaction.getPaymentReference", "BT-83 PaymentInstruction.remittanceInformation", ""),
      FieldMapping.mapped("ExportableTransaction.getPaymentTermDescription", "BT-20 Invoice.paymentTerms", ""),
      FieldMapping.mapped("ExportableTransaction.getPaymentTerms", "BT-20, BT-9, Invoice.cashDiscounts", "see PaymentTerms"),
      FieldMapping.mapped("ExportableTransaction.getCashDiscounts", "Invoice.cashDiscounts", "parsed from the XRechnung text with CashDiscount.parse"),
      FieldMapping.mapped("ExportableTransaction.getTotalPrepaidAmount", "BT-113 Invoice.paidAmount", ""),
      FieldMapping.mapped("ExportableTransaction.getRoundingAmount", "BT-114 Invoice.roundingAmount", ""),
      FieldMapping.mapped("TradeSettlementPayment.getPaymentMeansCode", "BT-81 PaymentInstruction.meansCode", "PaymentMeansCode.of(code)"),
      FieldMapping.mapped("TradeSettlementPayment.getPaymentMeansInformation", "BT-82 PaymentInstruction.meansText", ""),
      FieldMapping.mapped("TradeSettlementPayment.getOwnIBAN", "BT-84 CreditTransfer.iban", ""),
      FieldMapping.mapped("TradeSettlementPayment.getOwnBIC", "BT-86 CreditTransfer.bic", ""),
      FieldMapping.mapped("TradeSettlementPayment.getAccountName", "BT-85 CreditTransfer.accountName", ""),
      FieldMapping.decision("TradeSettlementPayment.getOwnPaymentInfoText", "BT-82 PaymentInstruction.meansText", "same business term as getPaymentMeansInformation: used only if that is empty"),
      FieldMapping.mapped("TradeSettlementDebit.getPaymentMeansCode", "BT-81 PaymentInstruction.meansCode", ""),
      FieldMapping.mapped("TradeSettlementDebit.getIBAN", "BT-91 DirectDebit.debitedAccountId", ""),
      FieldMapping.mapped("TradeSettlementDebit.getMandate", "BT-89 DirectDebit.mandateReference", ""),
      FieldMapping.mapped("PaymentTerms.getDescription", "BT-20 Invoice.paymentTerms", "if getPaymentTermDescription is empty"),
      FieldMapping.mapped("PaymentTerms.getDueDate", "BT-9 Invoice.dueDate", "if getDueDate is empty"),
      FieldMapping.mapped("PaymentTerms.getDiscountTerms", "Invoice.cashDiscounts", "see PaymentDiscountTerms"),
      FieldMapping.mapped("PaymentDiscountTerms.getCalculationPercentage", "CashDiscount.percent", ""),
      FieldMapping.decision("PaymentDiscountTerms.getBaseDate", "-", "the discount period counts from the issue date (XRechnung #SKONTO#); another base date is lost"),
      FieldMapping.mapped("PaymentDiscountTerms.getBasePeriodMeasure", "CashDiscount.days", ""),
      FieldMapping.decision("PaymentDiscountTerms.getBasePeriodUnitCode", "CashDiscount.days", "only days (DAY); other units are lost"),
      FieldMapping.mapped("CashDiscount.getAsXRechnung", "Invoice.cashDiscounts", "CashDiscount.parse"),
      FieldMapping.lost("CashDiscount.getAsCII", "-", "the same discount as text; not needed"),
      // References
      FieldMapping.mapped("ExportableTransaction.getBuyerOrderReferencedDocumentID", "BT-13 References.purchaseOrderReference", ""),
      FieldMapping.lost("ExportableTransaction.getBuyerOrderReferencedDocumentIssueDateTime", "-", "EN 16931 has no date for BT-13"),
      FieldMapping.mapped("ExportableTransaction.getSellerOrderReferencedDocumentID", "BT-14 References.salesOrderReference", ""),
      FieldMapping.mapped("ExportableTransaction.getContractReferencedDocument", "BT-12 References.contractReference", ""),
      FieldMapping.mapped("ExportableTransaction.getSpecifiedProcuringProjectID", "BT-11 References.projectReference", ""),
      FieldMapping.lost("ExportableTransaction.getSpecifiedProcuringProjectName", "-", "EN 16931 has no project name; CII writes a placeholder"),
      FieldMapping.mapped("ExportableTransaction.getDespatchAdviceReferencedDocumentID", "BT-16 References.despatchAdviceReference", ""),
      FieldMapping.decision("ExportableTransaction.getDeliveryNoteReferencedDocumentID", "ExtendedInformation.deliveryNoteReference", "EXTENDED only"),
      FieldMapping.decision("ExportableTransaction.getDeliveryNoteReferencedDocumentDate", "ExtendedInformation.deliveryNoteReference", "EXTENDED only"),
      FieldMapping.mapped("ExportableTransaction.getInvoiceReferencedDocumentID", "BT-25 PrecedingInvoiceReference.number", ""),
      FieldMapping.mapped("ExportableTransaction.getInvoiceReferencedIssueDate", "BT-26 PrecedingInvoiceReference.issueDate", ""),
      FieldMapping.mapped("ExportableTransaction.getDetailedDeliveryPeriodFrom", "BT-73 Invoice.invoicePeriod", ""),
      FieldMapping.mapped("ExportableTransaction.getDetailedDeliveryPeriodTo", "BT-74 Invoice.invoicePeriod", ""),
      FieldMapping.mapped("ExportableTransaction.getVATDueDateTypeCode", "BT-8 Invoice.taxPointDateCode", "CII code 5, 29, 72 to UNTDID 2005 code 3, 35, 432"),
      // Notes and attachments
      FieldMapping.mapped("ExportableTransaction.getNotes", "BG-1 Invoice.notes", "without subject code"),
      FieldMapping.mapped("ExportableTransaction.getNotesWithSubjectCode", "BG-1 InvoiceNote", "BT-21 subject code"),
      FieldMapping.mapped("ExportableTransaction.getSubjectNote", "BG-1 Invoice.notes", ""),
      FieldMapping.mapped("ExportableTransaction.getOwnOrganisationFullPlaintextInfo", "BG-1 InvoiceNote with subject code REG", "as Mustang writes it"),
      FieldMapping.mapped("ExportableTransaction.getAdditionalReferencedDocuments", "BG-24 Invoice.supportingDocuments", "see FileAttachment; alternatively PdfAttachment next to the XML in the PDF"),
      FieldMapping.mapped("IncludedNote.getSubjectCode", "BT-21 InvoiceNote.subjectCode", ""),
      FieldMapping.mapped("IncludedNote.getContent", "BT-22 InvoiceNote.text", ""),
      FieldMapping.mapped("FileAttachment.getFilename", "BT-122 reference, BT-125-2 Attachment.filename", "the file name is the reference"),
      FieldMapping.mapped("FileAttachment.getMimetype", "BT-125-1 Attachment.mimeCode", "EN 16931 allows PDF, PNG, JPEG, CSV, XLSX and ODS only; other files only as PdfAttachment"),
      FieldMapping.decision("FileAttachment.getRelation", "PdfAttachment.relationship", "embedded in the XML (BG-24) the relationship is lost; as PDF attachment it is kept"),
      FieldMapping.mapped("FileAttachment.getData", "BT-125 Attachment.content", ""),
      FieldMapping.mapped("FileAttachment.getDescription", "BT-123 AdditionalSupportingDocument.description", ""));

  private final boolean       extended;
  private final ZoneId        zone;
  private final List<String>  lost = new ArrayList<>();

  private MustangMigration(boolean extended,
                           ZoneId zone) {
    this.extended = extended;
    this.zone     = zone;
  }

  /**
   * Maps a transaction to an invoice of the given profile.
   *
   * @param transaction    the existing integration
   * @param processControl the target profile, e.g. {@code ProcessControl.facturXExtended()}
   * @param zone           the time zone the application's dates refer to
   * @return the invoice and the values that could not be carried over
   */
  static Result migrate(ExportableTransaction transaction,
                        ProcessControl processControl,
                        ZoneId zone) {
    var migration = new MustangMigration(ProcessControl.FACTURX_EXTENDED.equals(processControl.specificationId()),
                                         zone);
    var invoice = migration.invoice(transaction,
                                    processControl);
    return new Result(invoice,
                      List.copyOf(migration.lost));
  }

  private Invoice invoice(ExportableTransaction t,
                          ProcessControl processControl) {
    var extendedInformation = ExtendedInformation.create();
    var builder = Invoice.create()
                         .number(t.getNumber())
                         .issueDate(date(t.getIssueDate()))
                         .typeCode(InvoiceTypeCode.of(t.getDocumentCode()))
                         .currencyCode(CurrencyCode.of(t.getCurrency()))
                         .processControl(new ProcessControl(t.getBusinessProcessId() != null ?
                                                            t.getBusinessProcessId() :
                                                            processControl.businessProcessId(),
                                                            processControl.specificationId()))
                         .buyerReference(t.getReferenceNumber())
                         .seller(party(t.getSender(),
                                       true))
                         .buyer(party(t.getRecipient(),
                                      false));
    onlyExtended("ExportableTransaction.getDocumentName",
                 t.getDocumentName(),
                 () -> extendedInformation.documentName(t.getDocumentName()));
    onlyExtended("ExportableTransaction.getTestIndicator",
                 t.getTestIndicator() ?
                 Boolean.TRUE :
                 null,
                 () -> extendedInformation.testIndicator(true));
    if (t.getPayee() != null) {
      builder.payee(payee(t.getPayee()));
    }
    builder.delivery(delivery(t.getDeliveryAddress(),
                              date(t.getDeliveryDate())));
    onlyExtended("ExportableTransaction.getInvoicee",
                 t.getInvoicee(),
                 () -> extendedInformation.invoicee(party(t.getInvoicee(),
                                                          false)));

    for (Item item : t.getZFItems()) {
      builder.addLine(line(item));
    }
    for (AllowanceCharge allowance : list(t.getZFAllowances())) {
      builder.addAdjustment(adjustment(allowance));
    }
    for (AllowanceCharge charge : list(t.getZFCharges())) {
      builder.addAdjustment(adjustment(charge));
    }
    for (AllowanceCharge logistics : list(t.getZFLogisticsServiceCharges())) {
      if (extended) {
        extendedInformation.addLogisticsServiceCharge(new LogisticsServiceCharge(logistics.getReason(),
                                                                                 logistics.getTotalAmount(),
                                                                                 tax(logistics.getCategoryCode(),
                                                                                     logistics.getTaxPercent(),
                                                                                     null,
                                                                                     null)));
      } else {
        builder.addAdjustment(adjustment(logistics));
      }
    }

    builder.paymentInstruction(payment(t));
    terms(t,
          builder);
    if (t.getTotalPrepaidAmount() != null) {
      builder.paidAmount(t.getTotalPrepaidAmount());
    }
    builder.roundingAmount(t.getRoundingAmount());

    var references = References.create()
                               .purchaseOrderReference(t.getBuyerOrderReferencedDocumentID())
                               .salesOrderReference(t.getSellerOrderReferencedDocumentID())
                               .contractReference(t.getContractReferencedDocument())
                               .projectReference(t.getSpecifiedProcuringProjectID())
                               .despatchAdviceReference(t.getDespatchAdviceReferencedDocumentID())
                               .build();
    builder.references(references.equals(References.create()
                                                   .build()) ?
                       null :
                       references);
    lost("ExportableTransaction.getBuyerOrderReferencedDocumentIssueDateTime",
         t.getBuyerOrderReferencedDocumentIssueDateTime(),
         "EN 16931 has no date for the purchase order reference");
    lost("ExportableTransaction.getSpecifiedProcuringProjectName",
         t.getSpecifiedProcuringProjectName(),
         "EN 16931 has no project name");
    onlyExtended("ExportableTransaction.getDeliveryNoteReferencedDocumentID",
                 t.getDeliveryNoteReferencedDocumentID(),
                 () -> extendedInformation.deliveryNoteReference(new DocumentReference(t.getDeliveryNoteReferencedDocumentID(),
                                                                                       null,
                                                                                       date(t.getDeliveryNoteReferencedDocumentDate()))));
    if (t.getInvoiceReferencedDocumentID() != null) {
      builder.addPrecedingInvoiceReference(new PrecedingInvoiceReference(t.getInvoiceReferencedDocumentID(),
                                                                         date(t.getInvoiceReferencedIssueDate())));
    }
    if (t.getDetailedDeliveryPeriodFrom() != null || t.getDetailedDeliveryPeriodTo() != null) {
      builder.invoicePeriod(new InvoicePeriod(date(t.getDetailedDeliveryPeriodFrom()),
                                              date(t.getDetailedDeliveryPeriodTo())));
    }
    if (t.getVATDueDateTypeCode() != null) {
      builder.taxPointDateCode(switch (t.getVATDueDateTypeCode()) {
        case "5" -> DateTimeQualifier.CODE_3;
        case "29" -> DateTimeQualifier.CODE_35;
        case "72" -> DateTimeQualifier.CODE_432;
        default -> DateTimeQualifier.of(t.getVATDueDateTypeCode());
      });
    }

    for (String note : list(t.getNotes())) {
      builder.addNote(note);
    }
    for (var note : list(t.getNotesWithSubjectCode())) {
      builder.addNote(new InvoiceNote(note.getSubjectCode(),
                                      note.getContent(),
                                      null));
    }
    if (t.getSubjectNote() != null) {
      builder.addNote(t.getSubjectNote());
    }
    if (t.getOwnOrganisationFullPlaintextInfo() != null) {
      builder.addNote(new InvoiceNote("REG",
                                      t.getOwnOrganisationFullPlaintextInfo(),
                                      null));
    }
    for (var file : list(t.getAdditionalReferencedDocuments())) {
      builder.addSupportingDocument(AdditionalSupportingDocument.create()
                                                                .reference(file.getFilename())
                                                                .description(file.getDescription())
                                                                .attachment(Attachment.create()
                                                                                      .content(file.getData())
                                                                                      .mimeCode(file.getMimetype())
                                                                                      .filename(file.getFilename())
                                                                                      .build())
                                                                .build());
      lost("FileAttachment.getRelation",
           file.getRelation(),
           "embedded in the XML, the attachment " + file.getFilename() + " has no relationship; embed it as PdfAttachment to keep it");
    }
    if (extended) {
      builder.extended(extendedInformation.build());
    }
    return builder.build();
  }

  private Party party(TradeParty party,
                      boolean seller) {
    var builder = Party.create()
                       .name(party.getName())
                       .address(Address.create()
                                       .street(party.getStreet())
                                       .additionalStreet(party.getAdditionalAddress())
                                       .postalCode(party.getZIP())
                                       .city(party.getLocation())
                                       .country(Country.of(party.getCountry()))
                                       .build())
                       .vatId(party.getVATID());
    if (seller) {
      builder.taxId(party.getTaxID())
             .additionalLegalInformation(party.getDescription());
    } else {
      lost("TradeParty.getTaxID",
           party.getTaxID(),
           "EN 16931 has no tax number for " + party.getName());
      lost("TradeParty.getDescription",
           party.getDescription(),
           "EN 16931 has no additional legal information for " + party.getName());
    }
    if (party.getGlobalID() != null) {
      builder.identifier(party.getGlobalID(),
                         party.getGlobalIDScheme());
      lost("TradeParty.getID",
           party.getID(),
           "the global identifier of " + party.getName() + " is used; Paniolo keeps one identifier");
    } else {
      builder.identifier(party.getID());
    }
    if (party.getUriUniversalCommunicationID() != null) {
      builder.electronicAddress(new ElectronicAddress(party.getUriUniversalCommunicationID(),
                                                      party.getUriUniversalCommunicationIDScheme()));
    }
    var contact = party.getContact();
    if (contact != null) {
      if (contact.getFax() != null && extended) {
        builder.extended(new PartyExtension(null,
                                            null,
                                            new ExtendedContact(contact.getName(),
                                                                null,
                                                                null,
                                                                contact.getPhone(),
                                                                contact.getFax(),
                                                                contact.getEMail()),
                                            null,
                                            null));
      } else {
        lost("Contact.getFax",
             contact.getFax(),
             "a fax number needs the profile EXTENDED");
        builder.contact(new Contact(contact.getName(),
                                    contact.getPhone(),
                                    contact.getEMail()));
      }
    }
    var legal = party.getLegalOrganisation();
    if (legal != null) {
      if (legal.getSchemedID() != null) {
        builder.legalRegistrationId(legal.getSchemedID()
                                         .getID(),
                                    legal.getSchemedID()
                                         .getScheme());
      }
      builder.tradingName(legal.getTradingBusinessName());
    }
    return builder.build();
  }

  private Payee payee(TradeParty party) {
    var legal = party.getLegalOrganisation();
    return Payee.create()
                .name(party.getName())
                .identifier(party.getGlobalID() != null ?
                            party.getGlobalID() :
                            party.getID(),
                            party.getGlobalID() != null ?
                            party.getGlobalIDScheme() :
                            null)
                .legalRegistrationId(legal == null || legal.getSchemedID() == null ?
                                     null :
                                     legal.getSchemedID()
                                          .getID(),
                                     legal == null || legal.getSchemedID() == null ?
                                     null :
                                     legal.getSchemedID()
                                          .getScheme())
                .build();
  }

  private DeliveryInformation delivery(TradeParty shipTo,
                                       LocalDate deliveryDate) {
    var builder = DeliveryInformation.create()
                                     .actualDeliveryDate(deliveryDate);
    if (shipTo != null) {
      builder.partyName(shipTo.getName())
             .locationId(shipTo.getGlobalID() != null ?
                         shipTo.getGlobalID() :
                         shipTo.getID())
             .address(Address.create()
                             .street(shipTo.getStreet())
                             .additionalStreet(shipTo.getAdditionalAddress())
                             .postalCode(shipTo.getZIP())
                             .city(shipTo.getLocation())
                             .country(Country.of(shipTo.getCountry()))
                             .build());
      lost("ExportableTransaction.getDeliveryAddress",
           shipTo.getVATID() != null || shipTo.getContact() != null ?
           shipTo.getName() :
           null,
           "VAT ID and contact of the ship-to party have no EN 16931 counterpart");
    }
    return builder.build();
  }

  private InvoiceLine line(Item item) {
    var        product  = item.getProduct();
    BigDecimal discount = BigDecimal.ZERO;
    for (AllowanceCharge allowance : list(product.getAllowances())) {
      discount = discount.add(allowance.getTotalAmount());
    }
    BigDecimal surcharge = BigDecimal.ZERO;
    for (AllowanceCharge charge : list(product.getCharges())) {
      surcharge = surcharge.add(charge.getTotalAmount());
      lost("Product.getCharges",
           charge.getReason(),
           "price level charge " + charge.getReason() + " of line " + item.getId() + " is added to the net price");
    }
    var tax = tax(product.getTaxCategoryCode(),
                  product.getVATPercent(),
                  product.getTaxExemptionReason(),
                  product.getTaxExemptionReasonCode());
    var builder = InvoiceLine.create()
                             .id(item.getId())
                             .name(product.getName())
                             .description(product.getDescription())
                             .quantity(item.getQuantity())
                             .unitCode(product.getUnit())
                             .netUnitPrice(item.getPrice()
                                               .subtract(discount)
                                               .add(surcharge))
                             .priceBaseQuantity(item.getBasisQuantity())
                             .tax(tax)
                             .sellerItemId(product.getSellerAssignedID())
                             .buyerItemId(product.getBuyerAssignedID())
                             .orderLineReference(item.getBuyerOrderReferencedDocumentLineID())
                             .buyerAccountingReference(item.getAccountingReference())
                             .netAmount(item.getLineTotalAmount());
    if (discount.signum() != 0) {
      builder.grossPrice(item.getPrice())
             .priceDiscount(discount);
    }
    if (product.getGlobalID() != null) {
      builder.standardItemId(product.getGlobalID(),
                             product.getGlobalIDScheme());
    }
    if (product.getCountryOfOrigin() != null) {
      builder.originCountry(Country.of(product.getCountryOfOrigin()));
    }
    Map<String, String> attributes = product.getAttributes();
    if (attributes != null) {
      attributes.forEach(builder::addAttribute);
    }
    var notes = list(item.getNotes());
    if (!notes.isEmpty()) {
      builder.note(String.join("\n",
                               notes));
    }
    if (item.getDetailedDeliveryPeriodFrom() != null || item.getDetailedDeliveryPeriodTo() != null) {
      builder.period(new InvoicePeriod(date(item.getDetailedDeliveryPeriodFrom()),
                                       date(item.getDetailedDeliveryPeriodTo())));
    }
    for (AllowanceCharge adjustment : list(item.getItemAllowances())) {
      builder.addAdjustment(lineAdjustment(adjustment));
    }
    for (AllowanceCharge adjustment : list(item.getItemCharges())) {
      builder.addAdjustment(lineAdjustment(adjustment));
    }
    onlyExtended("Item.getParentLineID",
                 item.getParentLineID(),
                 () -> builder.extended(LineExtension.create()
                                                     .parentLineId(item.getParentLineID())
                                                     .build()));
    return builder.build();
  }

  private static LineAdjustment lineAdjustment(AllowanceCharge adjustment) {
    return new LineAdjustment(adjustment.isCharge(),
                              adjustment.getTotalAmount(),
                              adjustment.getBasisAmount(),
                              adjustment.getPercent(),
                              adjustment.getReason(),
                              adjustment.getReasonCode());
  }

  private static DocumentAdjustment adjustment(AllowanceCharge adjustment) {
    return DocumentAdjustment.create()
                             .charge(adjustment.isCharge())
                             .amount(adjustment.getTotalAmount())
                             .baseAmount(adjustment.getBasisAmount())
                             .percentage(adjustment.getPercent())
                             .reason(adjustment.getReason())
                             .reasonCode(adjustment.getReasonCode())
                             .tax(tax(adjustment.getCategoryCode(),
                                      adjustment.getTaxPercent(),
                                      null,
                                      null))
                             .build();
  }

  private static TaxCategory tax(String code,
                                 BigDecimal rate,
                                 String exemptionReason,
                                 String exemptionReasonCode) {
    var category = DutyTaxFeeCategory.of(code);
    return new TaxCategory(category,
                           category == DutyTaxFeeCategory.O ?
                           null :
                           rate,
                           exemptionReason,
                           exemptionReasonCode);
  }

  private PaymentInstruction payment(ExportableTransaction t) {
    var payments = list(t.getTradeSettlementPayment());
    var debits   = list(t.getTradeSettlement());
    if (payments.isEmpty() && debits.isEmpty()) {
      return null;
    }
    var builder = PaymentInstruction.create()
                                    .remittanceInformation(t.getPaymentReference());
    if (!payments.isEmpty()) {
      var first = payments.get(0);
      builder.meansCode(PaymentMeansCode.of(first.getPaymentMeansCode()))
             .meansText(first.getPaymentMeansInformation() != null ?
                        first.getPaymentMeansInformation() :
                        first.getOwnPaymentInfoText());
      for (var payment : payments) {
        builder.addCreditTransfer(new CreditTransfer(payment.getOwnIBAN(),
                                                     payment.getAccountName(),
                                                     payment.getOwnBIC()));
        if (payment.getPaymentMeansInformation() != null) {
          lost("TradeSettlementPayment.getOwnPaymentInfoText",
               payment.getOwnPaymentInfoText(),
               "BT-82 already carries the payment means information");
        }
      }
      for (var debit : debits) {
        lost("ExportableTransaction.getTradeSettlement",
             debit.getMandate(),
             "the invoice has a credit transfer; EN 16931 has one payment means per invoice");
      }
      lost("ExportableTransaction.getCreditorReferenceID",
           t.getCreditorReferenceID(),
           "a creditor identifier needs a direct debit");
      return builder.build();
    }
    var debit = debits.get(0);
    return builder.meansCode(PaymentMeansCode.of(debit.getPaymentMeansCode()))
                  .directDebit(new DirectDebit(debit.getMandate(),
                                               t.getCreditorReferenceID(),
                                               debit.getIBAN()))
                  .build();
  }

  private void terms(ExportableTransaction t,
                     Invoice.Builder builder) {
    var    terms       = t.getPaymentTerms();
    String description = t.getPaymentTermDescription() != null || terms == null ?
                         t.getPaymentTermDescription() :
                         terms.getDescription();
    builder.paymentTerms(description)
           .dueDate(date(t.getDueDate() != null || terms == null ?
                         t.getDueDate() :
                         terms.getDueDate()));
    var discounts = new ArrayList<CashDiscount>();
    if (terms != null && terms.getDiscountTerms() != null) {
      var discount = terms.getDiscountTerms();
      if ("DAY".equals(discount.getBasePeriodUnitCode())) {
        discounts.add(CashDiscount.of(discount.getBasePeriodMeasure(),
                                      discount.getCalculationPercentage()));
      } else {
        lost("PaymentDiscountTerms.getBasePeriodUnitCode",
             discount.getBasePeriodUnitCode(),
             "cash discount periods are counted in days");
      }
      lost("PaymentDiscountTerms.getBaseDate",
           discount.getBaseDate() == null ?
           null :
           date(discount.getBaseDate()).toString(),
           "the cash discount period counts from the issue date");
    }
    for (var discount : list(t.getCashDiscounts())) {
      discounts.addAll(CashDiscount.parse(discount.getAsXRechnung())
                                   .discounts());
    }
    builder.cashDiscounts(discounts);
  }

  private void onlyExtended(String getter,
                            Object value,
                            Runnable mapping) {
    if (value == null) {
      return;
    }
    if (extended) {
      mapping.run();
    } else {
      lost(getter,
           value,
           "needs the profile EXTENDED");
    }
  }

  private void lost(String getter,
                    Object value,
                    String reason) {
    if (value != null) {
      lost.add(getter + ": " + reason);
    }
  }

  private LocalDate date(Date date) {
    return date == null ?
           null :
           date.toInstant()
               .atZone(zone)
               .toLocalDate();
  }

  private static <T> List<T> list(T[] values) {
    return values == null ?
           List.of() :
           Arrays.asList(values);
  }

  /**
   * How a getter is carried over.
   */
  enum Status {
    /**
     * Mapped to the given Paniolo field.
     */
    MAPPED,
    /**
     * Needs a decision of the migration, e.g. the profile EXTENDED or how to combine values.
     */
    DECISION,
    /**
     * Not carried over.
     */
    LOST
  }

  /**
   * The mapping of one getter.
   *
   * @param getter the Mustang interface and getter, e.g. {@code Item.getPrice}
   * @param status mapped, lost, or decision
   * @param target the EN 16931 business term and Paniolo field
   * @param note   conversion, condition, or the decision to take
   */
  record FieldMapping(String getter, Status status, String target, String note) {
    FieldMapping {
      Objects.requireNonNull(getter);
      Objects.requireNonNull(status);
    }

    static FieldMapping mapped(String getter,
                               String target,
                               String note) {
      return new FieldMapping(getter,
                              Status.MAPPED,
                              target,
                              note);
    }

    static FieldMapping decision(String getter,
                                 String target,
                                 String note) {
      return new FieldMapping(getter,
                              Status.DECISION,
                              target,
                              note);
    }

    static FieldMapping lost(String getter,
                             String target,
                             String note) {
      return new FieldMapping(getter,
                              Status.LOST,
                              target,
                              note);
    }
  }

  /**
   * The migrated invoice and the values of the document that could not be carried over.
   *
   * @param invoice the invoice
   * @param lost    one entry per lost value: getter and reason
   */
  record Result(Invoice invoice, List<String> lost) {
  }
}

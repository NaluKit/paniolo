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
package io.github.nalukit.paniolo.xml.cii;

import io.github.nalukit.paniolo.model.Invoice;
import io.github.nalukit.paniolo.model.common.Address;
import io.github.nalukit.paniolo.model.common.CashDiscount;
import io.github.nalukit.paniolo.model.common.CreditTransfer;
import io.github.nalukit.paniolo.model.common.InvoiceLine;
import io.github.nalukit.paniolo.model.common.Party;
import io.github.nalukit.paniolo.model.common.PartyIdentifier;
import io.github.nalukit.paniolo.model.common.Payee;
import io.github.nalukit.paniolo.model.common.PaymentInstruction;
import io.github.nalukit.paniolo.model.common.ProcessControl;
import io.github.nalukit.paniolo.model.common.References;
import io.github.nalukit.paniolo.model.common.TaxCategory;
import io.github.nalukit.paniolo.model.common.VatBreakdown;
import io.github.nalukit.paniolo.model.common.value.DateTimeQualifier;
import io.github.nalukit.paniolo.model.extended.AccountingAccount;
import io.github.nalukit.paniolo.model.extended.DocumentReference;
import io.github.nalukit.paniolo.model.extended.ExtendedInformation;
import io.github.nalukit.paniolo.model.extended.PaymentTermsAdjustment;
import io.github.nalukit.paniolo.xml.InvoiceXmlWriter;
import io.github.nalukit.paniolo.xml.util.SpecificationGenerations;
import io.github.nalukit.paniolo.xml.util.XmlBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Writes the invoice model as UN/CEFACT CrossIndustryInvoice XML, including the supported
 * Factur-X profiles and EXTENDED fields. The specification identifier selects the profile;
 * content not supported by that profile is rejected instead of silently discarded.
 *
 * <p>Amounts are derived from the model. Writing does not run XSD or Schematron validation;
 * validate the resulting document separately before transmission. The base syntax is CII D16B;
 * some EXTENDED fields require the Factur-X schemas. Stateless and thread-safe.
 *
 * @see io.github.nalukit.paniolo.xml.InvoiceXmlWriter
 */
public final class CiiInvoiceWriter
    implements InvoiceXmlWriter {
  /**
   * Specification identifiers (BT-24) of MINIMUM and BASIC WL in ZUGFeRD 2.0 and 2.1.
   */
  private static final String ZUGFERD_2_MINIMUM  = "urn:zugferd.de:2p0:minimum";
  private static final String ZUGFERD_2_BASIC_WL = "urn:zugferd.de:2p0:basicwl";

  /**
   * Creates a writer; it has no state.
   */
  public CiiInvoiceWriter() {
  }

  private static void agreement(XmlBuilder xml,
                                Invoice invoice,
                                References references) {
    xml.start("ram:ApplicableHeaderTradeAgreement")
       .element("ram:BuyerReference",
                invoice.buyerReference());
    party(xml,
          "ram:SellerTradeParty",
          invoice.seller(),
          true);
    party(xml,
          "ram:BuyerTradeParty",
          invoice.buyer(),
          false);
    var extended = invoice.extended();
    if (extended != null) {
      extendedParty(xml,
                    "ram:SalesAgentTradeParty",
                    extended.salesAgent());
      extendedParty(xml,
                    "ram:BuyerTaxRepresentativeTradeParty",
                    extended.buyerTaxRepresentative());
    }
    var representative = invoice.taxRepresentative();
    if (representative != null) {
      xml.start("ram:SellerTaxRepresentativeTradeParty")
         .element("ram:Name",
                  representative.name());
      address(xml,
              representative.address());
      partyTax(xml,
               representative.vatId(),
               "VA");
      xml.end();
    }
    if (extended != null) {
      extendedParty(xml,
                    "ram:ProductEndUserTradeParty",
                    extended.productEndUser());
      if (extended.deliveryTermsCode() != null) {
        xml.start("ram:ApplicableTradeDeliveryTerms")
           .element("ram:DeliveryTypeCode",
                    extended.deliveryTermsCode());
        if (extended.deliveryTermsLocationName() != null || extended.deliveryTermsLocationCountry() != null) {
          xml.start("ram:RelevantTradeLocation")
             .element("ram:CountryID",
                      extended.deliveryTermsLocationCountry() == null ?
                      null :
                      extended.deliveryTermsLocationCountry()
                              .name())
             .element("ram:Name",
                      extended.deliveryTermsLocationName())
             .end();
        }
        xml.end();
      }
    }
    referencedDocument(xml,
                       "ram:SellerOrderReferencedDocument",
                       references.salesOrderReference());
    referencedDocument(xml,
                       "ram:BuyerOrderReferencedDocument",
                       references.purchaseOrderReference());
    if (extended != null) {
      referencedDocument(xml,
                         "ram:QuotationReferencedDocument",
                         extended.quotationReference());
    }
    referencedDocument(xml,
                       "ram:ContractReferencedDocument",
                       references.contractReference());
    for (var document : invoice.supportingDocuments()) {
      xml.start("ram:AdditionalReferencedDocument")
         .element("ram:IssuerAssignedID",
                  document.reference())
         .element("ram:URIID",
                  document.externalLocation())
         .element("ram:TypeCode",
                  "916")
         .element("ram:Name",
                  document.description());
      var attachment = document.attachment();
      if (attachment != null) {
        xml.element("ram:AttachmentBinaryObject",
                    Base64.getEncoder()
                          .encodeToString(attachment.content()),
                    "mimeCode",
                    attachment.mimeCode(),
                    "filename",
                    attachment.filename());
      }
      xml.end();
    }
    if (references.tenderOrLotReference() != null) {
      xml.start("ram:AdditionalReferencedDocument")
         .element("ram:IssuerAssignedID",
                  references.tenderOrLotReference())
         .element("ram:TypeCode",
                  "50")
         .end();
    }
    if (references.invoicedObjectId() != null) {
      xml.start("ram:AdditionalReferencedDocument")
         .element("ram:IssuerAssignedID",
                  references.invoicedObjectId())
         .element("ram:TypeCode",
                  "130")
         .element("ram:ReferenceTypeCode",
                  references.invoicedObjectSchemeId())
         .end();
    }
    if (extended != null) {
      extendedParty(xml,
                    "ram:BuyerAgentTradeParty",
                    extended.buyerAgent());
    }
    if (references.projectReference() != null) {
      // The project name is mandatory in CII but has no EN 16931 counterpart.
      xml.start("ram:SpecifiedProcuringProject")
         .element("ram:ID",
                  references.projectReference())
         .element("ram:Name",
                  "Project reference")
         .end();
    }
    if (extended != null) {
      for (var order : extended.ultimateCustomerOrderReferences()) {
        referencedDocument(xml,
                           "ram:UltimateCustomerOrderReferencedDocument",
                           order);
      }
    }
    xml.end();
  }

  private static void delivery(XmlBuilder xml,
                               Invoice invoice,
                               References references) {
    xml.start("ram:ApplicableHeaderTradeDelivery");
    if (invoice.extended() != null &&
        invoice.extended()
               .transportModeCode() != null) {
      xml.start("ram:RelatedSupplyChainConsignment")
         .start("ram:SpecifiedLogisticsTransportMovement")
         .element("ram:ModeCode",
                  invoice.extended()
                         .transportModeCode())
         .end()
         .end();
    }
    var delivery = invoice.delivery();
    if (delivery != null) {
      if (delivery.partyName() != null || delivery.locationId() != null || delivery.address() != null) {
        xml.start("ram:ShipToTradeParty");
        // A location identifier with scheme (BT-71-1) is a global identifier in CII.
        if (delivery.locationSchemeId() == null) {
          xml.element("ram:ID",
                      delivery.locationId());
        } else {
          xml.element("ram:GlobalID",
                      delivery.locationId(),
                      "schemeID",
                      delivery.locationSchemeId());
        }
        xml.element("ram:Name",
                    delivery.partyName());
        if (delivery.address() != null) {
          address(xml,
                  delivery.address());
        }
        xml.end();
      }
    }
    var extended = invoice.extended();
    if (extended != null) {
      extendedParty(xml,
                    "ram:UltimateShipToTradeParty",
                    extended.ultimateShipTo());
      extendedParty(xml,
                    "ram:ShipFromTradeParty",
                    extended.shipFrom());
    }
    if (delivery != null) {
      if (delivery.actualDeliveryDate() != null) {
        xml.start("ram:ActualDeliverySupplyChainEvent");
        date(xml,
             "ram:OccurrenceDateTime",
             delivery.actualDeliveryDate());
        xml.end();
      }
    }
    referencedDocument(xml,
                       "ram:DespatchAdviceReferencedDocument",
                       references.despatchAdviceReference());
    referencedDocument(xml,
                       "ram:ReceivingAdviceReferencedDocument",
                       references.receivingAdviceReference());
    if (extended != null) {
      referencedDocument(xml,
                         "ram:DeliveryNoteReferencedDocument",
                         extended.deliveryNoteReference());
    }
    xml.end();
  }

  private static void settlement(XmlBuilder xml,
                                 Invoice invoice,
                                 References references,
                                 boolean extendedProfile) {
    var totals = invoice.totals();
    String currency = invoice.currencyCode()
                             .name();
    xml.start("ram:ApplicableHeaderTradeSettlement");
    var payment = invoice.paymentInstruction();
    var directDebit = payment == null ?
                      null :
                      payment.directDebit();
    if (directDebit != null) {
      xml.element("ram:CreditorReferenceID",
                  directDebit.creditorId());
    }
    if (payment != null) {
      xml.element("ram:PaymentReference",
                  payment.remittanceInformation());
    }
    xml.element("ram:TaxCurrencyCode",
                invoice.taxCurrencyCode() == null ?
                null :
                invoice.taxCurrencyCode()
                       .name())
       .element("ram:InvoiceCurrencyCode",
                currency);
    var extended = invoice.extended();
    if (extended != null) {
      xml.element("ram:InvoiceIssuerReference",
                  extended.invoiceIssuerReference());
      extendedParty(xml,
                    "ram:InvoicerTradeParty",
                    extended.invoicer());
      extendedParty(xml,
                    "ram:InvoiceeTradeParty",
                    extended.invoicee());
    }
    payee(xml,
          invoice.payee());
    if (extended != null) {
      extendedParty(xml,
                    "ram:PayerTradeParty",
                    extended.payer());
      var exchange = extended.taxExchangeRate();
      if (exchange != null) {
        xml.start("ram:TaxApplicableTradeCurrencyExchange")
           .element("ram:SourceCurrencyCode",
                    currency)
           .element("ram:TargetCurrencyCode",
                    invoice.taxCurrencyCode()
                           .name())
           .element("ram:ConversionRate",
                    exchange.rate());
        date(xml,
             "ram:ConversionRateDateTime",
             exchange.date());
        xml.end();
      }
    }
    if (payment != null) {
      paymentMeans(xml,
                   payment,
                   extended);
    }
    boolean adjusted = !invoice.adjustments()
                               .isEmpty() ||
                       extended != null &&
                       !extended.logisticsServiceCharges()
                                .isEmpty();
    for (var breakdown : totals.vatBreakdowns()) {
      xml.start("ram:ApplicableTradeTax");
      tax(xml,
          breakdown.tax(),
          true,
          breakdown.taxableAmount(),
          breakdown.taxAmount(),
          invoice,
          extendedProfile && adjusted ?
          breakdown :
          null);
      xml.end();
    }
    var period = invoice.invoicePeriod();
    if (period != null) {
      xml.start("ram:BillingSpecifiedPeriod");
      if (extended != null) {
        xml.element("ram:Description",
                    extended.billingPeriodDescription());
      }
      date(xml,
           "ram:StartDateTime",
           period.startDate());
      date(xml,
           "ram:EndDateTime",
           period.endDate());
      xml.end();
    }
    for (var adjustment : invoice.adjustments()) {
      var details = adjustment.extended();
      xml.start("ram:SpecifiedTradeAllowanceCharge")
         .start("ram:ChargeIndicator")
         .element("udt:Indicator",
                  adjustment.charge())
         .end()
         .element("ram:SequenceNumeric",
                  details == null ?
                  null :
                  details.sequenceNumber())
         .element("ram:CalculationPercent",
                  adjustment.percentage())
         .element("ram:BasisAmount",
                  adjustment.baseAmount())
         .element("ram:BasisQuantity",
                  details == null ?
                  null :
                  details.baseQuantity(),
                  "unitCode",
                  details == null || details.baseQuantityUnit() == null ?
                  null :
                  details.baseQuantityUnit()
                         .getCode())
         .element("ram:ActualAmount",
                  adjustment.amount())
         .element("ram:ReasonCode",
                  adjustment.reasonCode())
         .element("ram:Reason",
                  adjustment.reason())
         .start("ram:CategoryTradeTax");
      tax(xml,
          adjustment.tax(),
          extendedProfile,
          null,
          null,
          null);
      xml.end()
         .end();
    }
    if (extended != null) {
      for (var service : extended.logisticsServiceCharges()) {
        xml.start("ram:SpecifiedLogisticsServiceCharge")
           .element("ram:Description",
                    service.description())
           .element("ram:AppliedAmount",
                    service.amount())
           .start("ram:AppliedTradeTax");
        tax(xml,
            service.tax(),
            extendedProfile,
            null,
            null,
            null);
        xml.end()
           .end();
      }
    }
    String terms = CashDiscount.paymentTerms(invoice.paymentTerms(),
                                             invoice.cashDiscounts());
    if (terms != null || invoice.dueDate() != null || directDebit != null) {
      xml.start("ram:SpecifiedTradePaymentTerms")
         .element("ram:Description",
                  terms);
      date(xml,
           "ram:DueDateDateTime",
           invoice.dueDate());
      xml.element("ram:DirectDebitMandateID",
                  directDebit == null ?
                  null :
                  directDebit.mandateReference())
         .end();
    }
    if (extended != null) {
      for (var term : extended.paymentTerms()) {
        xml.start("ram:SpecifiedTradePaymentTerms")
           .element("ram:Description",
                    term.description());
        date(xml,
             "ram:DueDateDateTime",
             term.dueDate());
        xml.element("ram:PartialPaymentAmount",
                    term.partialAmount());
        paymentTermsAdjustment(xml,
                               "ram:ApplicableTradePaymentPenaltyTerms",
                               "ram:ActualPenaltyAmount",
                               term.penalty());
        paymentTermsAdjustment(xml,
                               "ram:ApplicableTradePaymentDiscountTerms",
                               "ram:ActualDiscountAmount",
                               term.discount());
        payee(xml,
              term.payee());
        xml.end();
      }
    }
    xml.start("ram:SpecifiedTradeSettlementHeaderMonetarySummation")
       .element("ram:LineTotalAmount",
                totals.lineTotal())
       .element("ram:ChargeTotalAmount",
                totals.chargeTotal())
       .element("ram:AllowanceTotalAmount",
                totals.allowanceTotal())
       .element("ram:TaxBasisTotalAmount",
                totals.taxExclusiveAmount())
       .element("ram:TaxTotalAmount",
                totals.taxAmount(),
                "currencyID",
                currency);
    if (invoice.taxCurrencyCode() != null) {
      xml.element("ram:TaxTotalAmount",
                  invoice.taxAmountInTaxCurrency(),
                  "currencyID",
                  invoice.taxCurrencyCode()
                         .name());
    }
    xml.element("ram:RoundingAmount",
                totals.roundingAmount())
       .element("ram:GrandTotalAmount",
                totals.taxInclusiveAmount())
       .element("ram:TotalPrepaidAmount",
                totals.paidAmount())
       .element("ram:DuePayableAmount",
                totals.payableAmount())
       .end();
    if (extended != null) {
      for (var adjustment : extended.financialAdjustments()) {
        xml.start("ram:SpecifiedFinancialAdjustment")
           .element("ram:Reason",
                    adjustment.reason())
           .element("ram:ActualAmount",
                    adjustment.amount())
           .end();
      }
    }
    for (var preceding : invoice.precedingInvoiceReferences()) {
      xml.start("ram:InvoiceReferencedDocument")
         .element("ram:IssuerAssignedID",
                  preceding.number());
      if (preceding.issueDate() != null) {
        xml.start("ram:FormattedIssueDateTime")
           .element("qdt:DateTimeString",
                    preceding.issueDate()
                             .format(DateTimeFormatter.BASIC_ISO_DATE),
                    "format",
                    "102")
           .end();
      }
      xml.end();
    }
    if (references.buyerAccountingReference() != null) {
      xml.start("ram:ReceivableSpecifiedTradeAccountingAccount")
         .element("ram:ID",
                  references.buyerAccountingReference())
         .element("ram:TypeCode",
                  extended == null ?
                  null :
                  extended.buyerAccountingTypeCode())
         .end();
      accountingAccounts(xml,
                         extended == null ?
                         List.of() :
                         extended.additionalAccountingAccounts());
    }
    if (extended != null) {
      for (var advance : extended.advancePayments()) {
        xml.start("ram:SpecifiedAdvancePayment")
           .element("ram:PaidAmount",
                    advance.amount());
        formattedDate(xml,
                      "ram:FormattedReceivedDateTime",
                      advance.receivedDate());
        xml.start("ram:IncludedTradeTax");
        tax(xml,
            advance.tax(),
            false,
            null,
            advance.taxAmount(),
            null);
        xml.end();
        if (advance.invoiceReference() != null) {
          xml.start("ram:InvoiceSpecifiedReferencedDocument")
             .element("ram:IssuerAssignedID",
                      advance.invoiceReference())
             .end();
        }
        xml.end();
      }
    }
    xml.end();
  }

  private static void payee(XmlBuilder xml,
                            Payee payee) {
    if (payee == null) {
      return;
    }
    xml.start("ram:PayeeTradeParty");
    // An identifier with scheme (BT-60-1) is a global identifier in CII.
    if (payee.identifierSchemeId() == null) {
      xml.element("ram:ID",
                  payee.identifier());
    } else {
      xml.element("ram:GlobalID",
                  payee.identifier(),
                  "schemeID",
                  payee.identifierSchemeId());
    }
    xml.element("ram:Name",
                payee.name());
    if (payee.legalRegistrationId() != null) {
      xml.start("ram:SpecifiedLegalOrganization")
         .element("ram:ID",
                  payee.legalRegistrationId(),
                  "schemeID",
                  payee.legalRegistrationSchemeId())
         .end();
    }
    xml.end();
  }

  /**
   * EXTENDED splits the VAT category taxable amount into the line amounts of the category and its
   * share of document allowances, charges, and logistics service charges.
   */
  private static void extendedBasis(XmlBuilder xml,
                                    Invoice invoice,
                                    VatBreakdown breakdown) {
    BigDecimal lines = BigDecimal.ZERO;
    for (var line : invoice.lines()) {
      if ((line.extended() == null ||
           line.extended()
               .counts()) &&
          line.tax()
              .equals(breakdown.tax())) {
        lines = lines.add(line.netAmount());
      }
    }
    xml.element("ram:LineTotalBasisAmount",
                lines)
       .element("ram:AllowanceChargeBasisAmount",
                breakdown.taxableAmount()
                         .subtract(lines));
  }

  /**
   * Writes an invoiced object reference of a line (BT-128).
   */
  private static void objectReference(XmlBuilder xml,
                                      String id,
                                      String schemeId) {
    xml.start("ram:AdditionalReferencedDocument")
       .element("ram:IssuerAssignedID",
                id)
       .element("ram:TypeCode",
                "130")
       .element("ram:ReferenceTypeCode",
                schemeId)
       .end();
  }

  /**
   * Writes the line delivery with the billed quantity (BT-129) and the EXTENDED delivery details.
   */
  private static void lineDelivery(XmlBuilder xml,
                                   InvoiceLine line,
                                   String unit) {
    var extended = line.extended();
    xml.start("ram:SpecifiedLineTradeDelivery")
       .element("ram:BilledQuantity",
                line.quantity(),
                "unitCode",
                unit);
    if (extended != null) {
      xml.element("ram:ChargeFreeQuantity",
                  extended.chargeFreeQuantity(),
                  "unitCode",
                  unit)
         .element("ram:PackageQuantity",
                  extended.packageQuantity(),
                  "unitCode",
                  "XPK")
         .element("ram:PerPackageUnitQuantity",
                  extended.perPackageUnitQuantity(),
                  "unitCode",
                  unit);
      extendedParty(xml,
                    "ram:ShipToTradeParty",
                    extended.shipTo());
      extendedParty(xml,
                    "ram:UltimateShipToTradeParty",
                    extended.ultimateShipTo());
      if (extended.actualDeliveryDate() != null) {
        xml.start("ram:ActualDeliverySupplyChainEvent");
        date(xml,
             "ram:OccurrenceDateTime",
             extended.actualDeliveryDate());
        xml.end();
      }
      referencedDocument(xml,
                         "ram:DespatchAdviceReferencedDocument",
                         extended.despatchAdviceReference());
      referencedDocument(xml,
                         "ram:ReceivingAdviceReferencedDocument",
                         extended.receivingAdviceReference());
      referencedDocument(xml,
                         "ram:DeliveryNoteReferencedDocument",
                         extended.deliveryNoteReference());
    }
    xml.end();
  }

  private static void paymentTermsAdjustment(XmlBuilder xml,
                                             String name,
                                             String amountName,
                                             PaymentTermsAdjustment adjustment) {
    if (adjustment == null) {
      return;
    }
    xml.start(name);
    date(xml,
         "ram:BasisDateTime",
         adjustment.basisDate());
    xml.element("ram:BasisPeriodMeasure",
                adjustment.periodDays() == null ?
                null :
                BigDecimal.valueOf(adjustment.periodDays()),
                "unitCode",
                "DAY")
       .element("ram:BasisAmount",
                adjustment.basisAmount())
       .element("ram:CalculationPercent",
                adjustment.percent())
       .element(amountName,
                adjustment.amount())
       .end();
  }

  /**
   * Writes a party of the profile EXTENDED; like the seller, it may carry a tax number (FC).
   */
  private static void extendedParty(XmlBuilder xml,
                                    String role,
                                    Party party) {
    if (party != null) {
      party(xml,
            role,
            party,
            true);
    }
  }

  private static void referencedDocument(XmlBuilder xml,
                                         String name,
                                         DocumentReference reference) {
    if (reference != null) {
      xml.start(name)
         .element("ram:IssuerAssignedID",
                  reference.id())
         .element("ram:LineID",
                  reference.lineId());
      formattedDate(xml,
                    "ram:FormattedIssueDateTime",
                    reference.issueDate());
      xml.end();
    }
  }

  private static void formattedDate(XmlBuilder xml,
                                    String name,
                                    LocalDate value) {
    if (value != null) {
      xml.start(name)
         .element("qdt:DateTimeString",
                  value.format(DateTimeFormatter.BASIC_ISO_DATE),
                  "format",
                  "102")
         .end();
    }
  }

  private static void paymentMeans(XmlBuilder xml,
                                   PaymentInstruction payment,
                                   ExtendedInformation extended) {
    // One payment means per credit transfer account; card and mandate details use a single element.
    var accounts = payment.creditTransfers()
                          .isEmpty() ?
                   Collections.<CreditTransfer>singletonList(null) :
                   payment.creditTransfers();
    for (var account : accounts) {
      xml.start("ram:SpecifiedTradeSettlementPaymentMeans")
         .element("ram:TypeCode",
                  payment.meansCode()
                         .getCode())
         .element("ram:Information",
                  payment.meansText());
      var card = payment.paymentCard();
      if (card != null) {
        xml.start("ram:ApplicableTradeSettlementFinancialCard")
           .element("ram:ID",
                    card.primaryAccountNumber())
           .element("ram:CardholderName",
                    card.holderName())
           .end();
      }
      if (payment.directDebit() != null) {
        xml.start("ram:PayerPartyDebtorFinancialAccount")
           .element("ram:IBANID",
                    payment.directDebit()
                           .debitedAccountId())
           .element("ram:AccountName",
                    extended == null ?
                    null :
                    extended.payerAccountName())
           .end();
      }
      if (account != null) {
        xml.start("ram:PayeePartyCreditorFinancialAccount")
           .element("ram:IBANID",
                    account.iban())
           .element("ram:AccountName",
                    account.accountName())
           .end();
      }
      if (extended != null && extended.payerBic() != null && payment.directDebit() != null) {
        xml.start("ram:PayerSpecifiedDebtorFinancialInstitution")
           .element("ram:BICID",
                    extended.payerBic())
           .end();
      }
      if (account != null) {
        if (account.bic() != null) {
          xml.start("ram:PayeeSpecifiedCreditorFinancialInstitution")
             .element("ram:BICID",
                      account.bic())
             .end();
        }
      }
      xml.end();
    }
  }

  private static void referencedDocument(XmlBuilder xml,
                                         String name,
                                         String id) {
    if (id != null) {
      xml.start(name)
         .element("ram:IssuerAssignedID",
                  id)
         .end();
    }
  }

  /**
   * Writes a trade line item (BG-25) in the element order of the CII D16B schema.
   */
  private static void line(XmlBuilder xml,
                           InvoiceLine line,
                           boolean extendedProfile,
                           boolean ctcFr,
                           String currency) {
    // An EXTENDED subtotal line may state no quantity and unit.
    String unit = line.unitCode() == null ?
                  null :
                  line.unitCode()
                      .getCode();
    var extended = line.extended();
    xml.start("ram:IncludedSupplyChainTradeLineItem")
       .start("ram:AssociatedDocumentLineDocument")
       .element("ram:LineID",
                line.id());
    if (extended != null) {
      xml.element("ram:ParentLineID",
                  extended.parentLineId())
         .element("ram:LineStatusCode",
                  extended.statusCode())
         .element("ram:LineStatusReasonCode",
                  extended.subtype() == null ?
                  null :
                  extended.subtype()
                          .name());
    }
    if (line.note() != null || extended != null && extended.noteContentCode() != null) {
      xml.start("ram:IncludedNote")
         .element("ram:ContentCode",
                  extended == null ?
                  null :
                  extended.noteContentCode())
         .element("ram:Content",
                  line.note())
         .element("ram:SubjectCode",
                  extended == null ?
                  null :
                  extended.noteSubjectCode())
         .end();
    }
    xml.end()
       .start("ram:SpecifiedTradeProduct");
    if (extended != null) {
      xml.element("ram:ID",
                  extended.productId());
    }
    xml.element("ram:GlobalID",
                line.standardItemId(),
                "schemeID",
                line.standardItemSchemeId())
       .element("ram:SellerAssignedID",
                line.sellerItemId())
       .element("ram:BuyerAssignedID",
                line.buyerItemId());
    if (extended != null) {
      xml.element("ram:IndustryAssignedID",
                  extended.industryItemId())
         .element("ram:ModelID",
                  extended.modelId());
    }
    xml.element("ram:Name",
                line.name())
       .element("ram:Description",
                line.description());
    if (extended != null) {
      for (var batch : extended.batchIds()) {
        xml.element("ram:BatchID",
                    batch);
      }
      xml.element("ram:BrandName",
                  extended.brandName())
         .element("ram:ModelName",
                  extended.modelName());
      for (var characteristic : extended.characteristics()) {
        xml.start("ram:ApplicableProductCharacteristic")
           .element("ram:TypeCode",
                    characteristic.typeCode())
           .element("ram:Description",
                    characteristic.description())
           .element("ram:ValueMeasure",
                    characteristic.measure(),
                    "unitCode",
                    characteristic.measureUnit() == null ?
                    null :
                    characteristic.measureUnit()
                                  .getCode())
           .element("ram:Value",
                    characteristic.value())
           .end();
      }
    }
    for (var attribute : line.attributes()) {
      xml.start("ram:ApplicableProductCharacteristic")
         .element("ram:Description",
                  attribute.name())
         .element("ram:Value",
                  attribute.value())
         .end();
    }
    for (var classification : line.classifications()) {
      xml.start("ram:DesignatedProductClassification")
         .element("ram:ClassCode",
                  classification.code(),
                  "listID",
                  classification.schemeId(),
                  "listVersionID",
                  classification.schemeVersion())
         .element("ram:ClassName",
                  classification.className())
         .end();
    }
    if (extended != null) {
      for (var instance : extended.instances()) {
        xml.start("ram:IndividualTradeProductInstance")
           .element("ram:BatchID",
                    instance.batchId())
           .element("ram:SupplierAssignedSerialID",
                    instance.serialId())
           .end();
      }
    }
    if (line.originCountry() != null) {
      xml.start("ram:OriginTradeCountry")
         .element("ram:ID",
                  line.originCountry()
                      .name())
         .end();
    }
    if (extended != null) {
      extendedParty(xml,
                    "ram:ManufacturerTradeParty",
                    extended.manufacturer());
      for (var product : extended.includedProducts()) {
        xml.start("ram:IncludedReferencedProduct")
           .element("ram:ID",
                    product.id())
           .element("ram:SellerAssignedID",
                    product.sellerItemId())
           .element("ram:BuyerAssignedID",
                    product.buyerItemId())
           .element("ram:IndustryAssignedID",
                    product.industryItemId())
           .element("ram:Name",
                    product.name())
           .element("ram:Description",
                    product.description())
           .element("ram:UnitQuantity",
                    product.quantity(),
                    "unitCode",
                    product.unitCode() == null ?
                    null :
                    product.unitCode()
                           .getCode())
           .end();
      }
    }
    xml.end()
       .start("ram:SpecifiedLineTradeAgreement");
    if (extended != null) {
      if (extended.deliveryTermsCode() != null) {
        xml.start("ram:ApplicableTradeDeliveryTerms")
           .element("ram:DeliveryTypeCode",
                    extended.deliveryTermsCode())
           .end();
      }
      referencedDocument(xml,
                         "ram:SellerOrderReferencedDocument",
                         extended.sellerOrderReference());
    }
    if (line.orderLineReference() != null) {
      xml.start("ram:BuyerOrderReferencedDocument")
         .element("ram:LineID",
                  line.orderLineReference())
         .end();
    }
    if (extended != null) {
      referencedDocument(xml,
                         "ram:QuotationReferencedDocument",
                         extended.quotationReference());
      referencedDocument(xml,
                         "ram:ContractReferencedDocument",
                         extended.contractReference());
      for (var reference : extended.additionalReferences()) {
        xml.start("ram:AdditionalReferencedDocument")
           .element("ram:IssuerAssignedID",
                    reference.id())
           .element("ram:TypeCode",
                    reference.typeCode())
           .element("ram:ReferenceTypeCode",
                    reference.referenceTypeCode())
           .end();
      }
    }
    if (line.grossPrice() != null) {
      xml.start("ram:GrossPriceProductTradePrice")
         .element("ram:ChargeAmount",
                  line.grossPrice())
         .element("ram:BasisQuantity",
                  line.priceBaseQuantity(),
                  "unitCode",
                  unit);
      if (extended != null && !extended.priceAdjustments()
                                       .isEmpty()) {
        // EXTENDED states the parts of the discount (BT-147); the model ensures that they add up.
        for (var adjustment : extended.priceAdjustments()) {
          xml.start("ram:AppliedTradeAllowanceCharge")
             .start("ram:ChargeIndicator")
             .element("udt:Indicator",
                      adjustment.charge())
             .end()
             .element("ram:ActualAmount",
                      adjustment.amount())
             .element("ram:ReasonCode",
                      adjustment.reasonCode())
             .element("ram:Reason",
                      adjustment.reason())
             .end();
        }
      } else if (line.priceDiscount() != null) {
        xml.start("ram:AppliedTradeAllowanceCharge")
           .start("ram:ChargeIndicator")
           .element("udt:Indicator",
                    false)
           .end()
           .element("ram:ActualAmount",
                    line.priceDiscount())
           .end();
      }
      xml.end();
    }
    // An EXTENDED subtotal or information line may have no price.
    if (line.netUnitPrice() != null) {
      xml.start("ram:NetPriceProductTradePrice")
         .element("ram:ChargeAmount",
                  line.netUnitPrice())
         .element("ram:BasisQuantity",
                  line.priceBaseQuantity(),
                  "unitCode",
                  unit)
         .end();
    }
    if (extended != null) {
      extendedParty(xml,
                    "ram:ItemSellerTradeParty",
                    extended.itemSeller());
      for (var order : extended.ultimateCustomerOrderReferences()) {
        referencedDocument(xml,
                           "ram:UltimateCustomerOrderReferencedDocument",
                           order);
      }
    }
    xml.end();
    // A subtotal line without quantity, such as the sub-invoice line of a French multi-vendor
    // invoice, states no delivery: its delivery information would need the billed quantity.
    if (line.quantity() == null) {
      if (extended != null && (extended.chargeFreeQuantity() != null || extended.packageQuantity() != null || extended.perPackageUnitQuantity() != null || extended.shipTo() != null || extended.ultimateShipTo() != null ||
                               extended.actualDeliveryDate() != null || extended.despatchAdviceReference() != null || extended.receivingAdviceReference() != null || extended.deliveryNoteReference() != null)) {
        throw new IllegalArgumentException("Line " + line.id() + ": delivery information requires the invoiced quantity (BT-129)");
      }
    } else {
      lineDelivery(xml,
                   line,
                   unit);
    }
    xml.start("ram:SpecifiedLineTradeSettlement");
    // An EXTENDED subtotal or information line may have no VAT category (BR-FXEXT-CO-04).
    String dueDateTypeCode = extended == null ?
                             null :
                             extended.taxDueDateTypeCode();
    if (line.tax() != null) {
      xml.start("ram:ApplicableTradeTax");
      // EXTENDED states the exemption reason of lines, allowances, and charges as well (BT-X-96,
      // BT-X-97), which BR-FXEXT-E-08rev and BR-FXEXT-IC-08rev compare with the VAT breakdown.
      tax(xml,
          line.tax(),
          extendedProfile,
          null,
          null,
          null,
          null,
          dueDateTypeCode);
      xml.end();
    } else if (dueDateTypeCode != null) {
      // A subtotal or information line may state when its VAT becomes due without a VAT category.
      xml.start("ram:ApplicableTradeTax")
         .element("ram:DueDateTypeCode",
                  dueDateTypeCode)
         .end();
    }
    if (line.period() != null) {
      xml.start("ram:BillingSpecifiedPeriod");
      date(xml,
           "ram:StartDateTime",
           line.period()
               .startDate());
      date(xml,
           "ram:EndDateTime",
           line.period()
               .endDate());
      xml.end();
    }
    for (var adjustment : line.adjustments()) {
      xml.start("ram:SpecifiedTradeAllowanceCharge")
         .start("ram:ChargeIndicator")
         .element("udt:Indicator",
                  adjustment.charge())
         .end()
         .element("ram:CalculationPercent",
                  adjustment.percentage())
         .element("ram:BasisAmount",
                  adjustment.baseAmount())
         .element("ram:ActualAmount",
                  adjustment.amount())
         .element("ram:ReasonCode",
                  adjustment.reasonCode())
         .element("ram:Reason",
                  adjustment.reason())
         .end();
    }
    xml.start("ram:SpecifiedTradeSettlementLineMonetarySummation")
       .element("ram:LineTotalAmount",
                line.netAmount());
    // EXTENDED states the sums of the line charges and allowances as well; EXTENDED-CTC-FR does not.
    if (extendedProfile &&
        !ctcFr &&
        !line.adjustments()
             .isEmpty()) {
      xml.element("ram:ChargeTotalAmount",
                  line.chargeTotal())
         .element("ram:AllowanceTotalAmount",
                  line.allowanceTotal());
    }
    if (extended != null) {
      xml.element("ram:TaxTotalAmount",
                  extended.taxTotal(),
                  "currencyID",
                  currency)
         .element("ram:GrandTotalAmount",
                  extended.grossTotal());
    }
    xml.end();
    if (extended != null) {
      referencedDocument(xml,
                         "ram:InvoiceReferencedDocument",
                         extended.precedingInvoiceReference());
    }
    if (line.objectId() != null) {
      objectReference(xml,
                      line.objectId(),
                      line.objectSchemeId());
      // EXTENDED repeats the invoiced object, e.g. the sub-invoice (AFL) next to the object (AVV).
      if (extended != null) {
        for (var object : extended.furtherObjectIdentifiers()) {
          objectReference(xml,
                          object.id(),
                          object.schemeId());
        }
      }
    }
    if (line.buyerAccountingReference() != null) {
      xml.start("ram:ReceivableSpecifiedTradeAccountingAccount")
         .element("ram:ID",
                  line.buyerAccountingReference())
         .element("ram:TypeCode",
                  extended == null ?
                  null :
                  extended.accountingTypeCode())
         .end();
      accountingAccounts(xml,
                         extended == null ?
                         List.of() :
                         extended.additionalAccountingAccounts());
    }
    xml.end()
       .end();
  }

  private static void date(XmlBuilder xml,
                           String name,
                           LocalDate value) {
    if (value != null) {
      xml.start(name)
         .element("udt:DateTimeString",
                  value.format(DateTimeFormatter.BASIC_ISO_DATE),
                  "format",
                  "102")
         .end();
    }
  }

  /**
   * Writes a trade tax; the invoice is only passed for document level breakdowns carrying BT-7 / BT-8.
   */
  private static void tax(XmlBuilder xml,
                          TaxCategory tax,
                          boolean exemption,
                          BigDecimal basis,
                          BigDecimal amount,
                          Invoice invoice) {
    tax(xml,
        tax,
        exemption,
        basis,
        amount,
        invoice,
        null);
  }

  /**
   * Writes a tax; with an EXTENDED breakdown also the line and allowance/charge base amounts.
   */
  private static void tax(XmlBuilder xml,
                          TaxCategory tax,
                          boolean exemption,
                          BigDecimal basis,
                          BigDecimal amount,
                          Invoice invoice,
                          VatBreakdown extendedBreakdown) {
    tax(xml,
        tax,
        exemption,
        basis,
        amount,
        invoice,
        extendedBreakdown,
        null);
  }

  /**
   * Writes a tax; a line may state the code of the date its VAT becomes due (UNTDID 2475).
   */
  private static void tax(XmlBuilder xml,
                          TaxCategory tax,
                          boolean exemption,
                          BigDecimal basis,
                          BigDecimal amount,
                          Invoice invoice,
                          VatBreakdown extendedBreakdown,
                          String lineDueDateTypeCode) {
    xml.element("ram:CalculatedAmount",
                amount)
       .element("ram:TypeCode",
                "VAT");
    if (exemption) {
      xml.element("ram:ExemptionReason",
                  tax.exemptionReason());
    }
    xml.element("ram:BasisAmount",
                basis);
    if (extendedBreakdown != null) {
      extendedBasis(xml,
                    invoice,
                    extendedBreakdown);
    }
    xml.element("ram:CategoryCode",
                tax.code()
                   .getCode());
    if (exemption) {
      xml.element("ram:ExemptionReasonCode",
                  tax.exemptionReasonCode());
    }
    if (invoice != null) {
      if (invoice.taxPointDate() != null) {
        xml.start("ram:TaxPointDate")
           .element("udt:DateString",
                    invoice.taxPointDate()
                           .format(DateTimeFormatter.BASIC_ISO_DATE),
                    "format",
                    "102")
           .end();
      }
      xml.element("ram:DueDateTypeCode",
                  dueDateTypeCode(invoice.taxPointDateCode()));
    }
    xml.element("ram:DueDateTypeCode",
                lineDueDateTypeCode);
    xml.element("ram:RateApplicablePercent",
                exemption && invoice != null && tax.rate() == null ?
                BigDecimal.ZERO :
                tax.rate());
  }

  /**
   * CII expresses BT-8 with UNTDID 2475 instead of UNTDID 2005.
   */
  private static String dueDateTypeCode(DateTimeQualifier code) {
    if (code == null) {
      return null;
    }
    return switch (code) {
      case CODE_3 -> "5";
      case CODE_35 -> "29";
      case CODE_432 -> "72";
      default -> throw new IllegalArgumentException("Unsupported tax point date code: " + code.getCode());
    };
  }

  private static void address(XmlBuilder xml,
                              Address address) {
    // Only the buyer of a Factur-X MINIMUM invoice may have no address.
    if (address == null) {
      return;
    }
    xml.start("ram:PostalTradeAddress")
       .element("ram:PostcodeCode",
                address.postalCode())
       .element("ram:LineOne",
                address.street())
       .element("ram:LineTwo",
                address.additionalStreet())
       .element("ram:LineThree",
                address.additionalLine())
       .element("ram:CityName",
                address.city())
       .element("ram:CountryID",
                address.country()
                       .name())
       .element("ram:CountrySubDivisionName",
                address.countrySubdivision())
       .end();
  }

  /**
   * Further accounting accounts of the profile EXTENDED, which repeats the accounting account.
   */
  private static void accountingAccounts(XmlBuilder xml,
                                         List<AccountingAccount> accounts) {
    for (var account : accounts) {
      xml.start("ram:ReceivableSpecifiedTradeAccountingAccount")
         .element("ram:ID",
                  account.id())
         .element("ram:TypeCode",
                  account.typeCode())
         .end();
    }
  }

  private static void party(XmlBuilder xml,
                            String role,
                            Party party,
                            boolean seller) {
    if (!seller && party.taxId() != null) {
      throw new IllegalArgumentException("Buyer national tax identifier has no EN 16931 mapping");
    }
    if (!seller && party.additionalLegalInformation() != null) {
      throw new IllegalArgumentException("Buyer additional legal information has no EN 16931 mapping");
    }
    xml.start(role);
    // An identifier with scheme (BT-29-1 / BT-46-1) is a global identifier in CII; the schema
    // orders all ram:ID before all ram:GlobalID.
    var identifiers = new ArrayList<PartyIdentifier>();
    if (party.identifier() != null) {
      identifiers.add(new PartyIdentifier(party.identifier(),
                                          party.identifierSchemeId()));
    }
    identifiers.addAll(party.additionalIdentifiers());
    for (var identifier : identifiers) {
      if (identifier.schemeId() == null) {
        xml.element("ram:ID",
                    identifier.id());
      }
    }
    for (var identifier : identifiers) {
      if (identifier.schemeId() != null) {
        xml.element("ram:GlobalID",
                    identifier.id(),
                    "schemeID",
                    identifier.schemeId());
      }
    }
    var extended = party.extended();
    if (extended != null && extended.globalId() != null) {
      xml.element("ram:GlobalID",
                  extended.globalId(),
                  "schemeID",
                  extended.globalIdSchemeId());
    }
    xml.element("ram:Name",
                party.name())
       .element("ram:RoleCode",
                extended == null ?
                null :
                extended.roleCode())
       .element("ram:Description",
                party.additionalLegalInformation());
    var legalAddress = extended == null ?
                       null :
                       extended.legalAddress();
    if (party.legalRegistrationId() != null || party.tradingName() != null || legalAddress != null) {
      xml.start("ram:SpecifiedLegalOrganization")
         .element("ram:ID",
                  party.legalRegistrationId(),
                  "schemeID",
                  party.legalRegistrationSchemeId())
         .element("ram:TradingBusinessName",
                  party.tradingName());
      if (legalAddress != null) {
        address(xml,
                legalAddress);
      }
      xml.end();
    }
    var extendedContact = extended == null ?
                          null :
                          extended.contact();
    if (extendedContact != null) {
      xml.start("ram:DefinedTradeContact")
         .element("ram:PersonName",
                  extendedContact.name())
         .element("ram:DepartmentName",
                  extendedContact.department())
         .element("ram:TypeCode",
                  extendedContact.typeCode());
      communication(xml,
                    "ram:TelephoneUniversalCommunication",
                    "ram:CompleteNumber",
                    extendedContact.telephone());
      communication(xml,
                    "ram:FaxUniversalCommunication",
                    "ram:CompleteNumber",
                    extendedContact.fax());
      communication(xml,
                    "ram:EmailURIUniversalCommunication",
                    "ram:URIID",
                    extendedContact.email());
      xml.end();
    }
    if (party.contact() != null) {
      xml.start("ram:DefinedTradeContact")
         .element("ram:PersonName",
                  party.contact()
                       .name());
      if (party.contact()
               .telephone() != null) {
        xml.start("ram:TelephoneUniversalCommunication")
           .element("ram:CompleteNumber",
                    party.contact()
                         .telephone())
           .end();
      }
      if (party.contact()
               .email() != null) {
        xml.start("ram:EmailURIUniversalCommunication")
           .element("ram:URIID",
                    party.contact()
                         .email())
           .end();
      }
      xml.end();
    }

    address(xml,
            party.address());
    if (party.electronicAddress() != null) {
      xml.start("ram:URIUniversalCommunication")
         .element("ram:URIID",
                  party.electronicAddress()
                       .value(),
                  "schemeID",
                  party.electronicAddress()
                       .schemeId())
         .end();
    }
    partyTax(xml,
             party.vatId(),
             "VA");
    if (seller) {
      partyTax(xml,
               party.taxId(),
               "FC");
    }
    xml.end();
  }

  private static void communication(XmlBuilder xml,
                                    String name,
                                    String valueName,
                                    String value) {
    if (value != null) {
      xml.start(name)
         .element(valueName,
                  value)
         .end();
    }
  }

  private static void partyTax(XmlBuilder xml,
                               String id,
                               String scheme) {
    if (id != null && !id.isBlank()) {
      xml.start("ram:SpecifiedTaxRegistration")
         .element("ram:ID",
                  id,
                  "schemeID",
                  scheme)
         .end();
    }
  }

  private static XmlBuilder document() {
    return new XmlBuilder("rsm:CrossIndustryInvoice",
                          Map.of("rsm",
                                 "urn:un:unece:uncefact:data:standard:CrossIndustryInvoice:100",
                                 "ram",
                                 "urn:un:unece:uncefact:data:standard:ReusableAggregateBusinessInformationEntity:100",
                                 "qdt",
                                 "urn:un:unece:uncefact:data:standard:QualifiedDataType:100",
                                 "udt",
                                 "urn:un:unece:uncefact:data:standard:UnqualifiedDataType:100"));
  }

  /**
   * Writes the Factur-X profile MINIMUM; {@link FacturXMinimumRestrictions} has rejected all
   * other content. A paid amount is not written but reduces the amount due (BT-115).
   */
  private static String minimum(Invoice invoice) {
    var totals = invoice.totals();
    String currency = invoice.currencyCode()
                             .name();
    var xml = document();
    xml.start("rsm:ExchangedDocumentContext");
    if (invoice.processControl()
               .businessProcessId() != null) {
      xml.start("ram:BusinessProcessSpecifiedDocumentContextParameter")
         .element("ram:ID",
                  invoice.processControl()
                         .businessProcessId())
         .end();
    }
    xml.start("ram:GuidelineSpecifiedDocumentContextParameter")
       .element("ram:ID",
                invoice.processControl()
                       .specificationId())
       .end()
       .end()
       .start("rsm:ExchangedDocument")
       .element("ram:ID",
                invoice.number())
       .element("ram:TypeCode",
                invoice.typeCode()
                       .getCode());
    date(xml,
         "ram:IssueDateTime",
         invoice.issueDate());
    xml.end()
       .start("rsm:SupplyChainTradeTransaction")
       .start("ram:ApplicableHeaderTradeAgreement")
       .element("ram:BuyerReference",
                invoice.buyerReference());
    minimumParty(xml,
                 "ram:SellerTradeParty",
                 invoice.seller(),
                 true);
    minimumParty(xml,
                 "ram:BuyerTradeParty",
                 invoice.buyer(),
                 false);
    referencedDocument(xml,
                       "ram:BuyerOrderReferencedDocument",
                       invoice.references() == null ?
                       null :
                       invoice.references()
                              .purchaseOrderReference());
    xml.end()
       .start("ram:ApplicableHeaderTradeDelivery")
       .end()
       .start("ram:ApplicableHeaderTradeSettlement")
       .element("ram:InvoiceCurrencyCode",
                currency)
       .start("ram:SpecifiedTradeSettlementHeaderMonetarySummation")
       .element("ram:TaxBasisTotalAmount",
                totals.taxExclusiveAmount())
       .element("ram:TaxTotalAmount",
                totals.taxAmount(),
                "currencyID",
                currency)
       .element("ram:GrandTotalAmount",
                totals.taxInclusiveAmount())
       .element("ram:DuePayableAmount",
                totals.payableAmount())
       .end()
       .end()
       .end();
    return xml.toXml();
  }

  private static void minimumParty(XmlBuilder xml,
                                   String role,
                                   Party party,
                                   boolean seller) {
    if (!seller && party.taxId() != null) {
      throw new IllegalArgumentException("Buyer national tax identifier has no EN 16931 mapping");
    }
    xml.start(role)
       .element("ram:Name",
                party.name());
    if (party.legalRegistrationId() != null) {
      xml.start("ram:SpecifiedLegalOrganization")
         .element("ram:ID",
                  party.legalRegistrationId(),
                  "schemeID",
                  party.legalRegistrationSchemeId())
         .end();
    }
    if (party.address() != null) {
      xml.start("ram:PostalTradeAddress")
         .element("ram:CountryID",
                  party.address()
                       .country()
                       .name())
         .end();
    }
    partyTax(xml,
             party.vatId(),
             "VA");
    if (seller) {
      partyTax(xml,
               party.taxId(),
               "FC");
    }
    xml.end();
  }

  @Override
  public String toXml(Invoice invoice) {
    Objects.requireNonNull(invoice,
                           "invoice");
    // The syntax of EN 16931:2017 under an identifier of EN 16931:2026 would claim a conformance it lacks.
    if (SpecificationGenerations.isEn16931Of2026(invoice.processControl()
                                                        .specificationId())) {
      throw new IllegalArgumentException(SpecificationGenerations.NOT_SUPPORTED + "; Paniolo writes EN 16931:2017, e.g. with ProcessControl.xrechnung()");
    }
    boolean extendedProfile = invoice.processControl()
                                     .isExtended();
    if (invoice.precedingInvoiceReferences()
               .size() > 1 && !extendedProfile) {
      throw new IllegalArgumentException("CII D16B supports only one preceding invoice reference outside the Factur-X profiles EXTENDED and EXTENDED-CTC-FR");
    }
    if (invoice.hasExtendedInformation() && !extendedProfile) {
      throw new IllegalArgumentException("EXTENDED information requires the specification identifier of the Factur-X profile EXTENDED (ProcessControl.facturXExtended()) or EXTENDED-CTC-FR (ProcessControl.facturXExtendedCtcFr(...))");
    }
    // KoSIT validates sub invoice lines, third party payments, and their MIME codes for UBL only (BR-DEX-15).
    if (invoice.hasXrechnungExtensionInformation()) {
      throw new IllegalArgumentException("Sub invoice lines, third party payments, and XML attachments of the XRechnung extension are defined for UBL only; write UBL instead");
    }
    String specificationId = invoice.processControl()
                                    .specificationId();
    boolean minimum = ProcessControl.FACTURX_MINIMUM.equals(specificationId) || ZUGFERD_2_MINIMUM.equals(specificationId);
    boolean basicWl = ProcessControl.FACTURX_BASIC_WL.equals(specificationId) || ZUGFERD_2_BASIC_WL.equals(specificationId);
    if (invoice.hasStatedTotals() != (minimum || basicWl)) {
      throw new IllegalArgumentException(invoice.hasStatedTotals() ?
                                         "An invoice without lines can only be written in the Factur-X profiles MINIMUM (ProcessControl.facturXMinimum()) and BASIC WL (ProcessControl.facturXBasicWl())" :
                                         "The Factur-X profiles MINIMUM and BASIC WL carry no lines; use stated totals (Invoice.Builder.statedTotals) instead of lines");
    }
    if (minimum) {
      FacturXMinimumRestrictions.check(invoice);
      return minimum(invoice);
    }
    if (basicWl &&
        (invoice.statedTotals()
                .lineTotal() == null ||
         invoice.statedTotals()
                .vatBreakdowns()
                .isEmpty())) {
      throw new IllegalArgumentException("Factur-X profile BASIC WL requires the sum of line net amounts (BT-106) and the VAT breakdown (BG-23)");
    }
    if (FacturXBasicRestrictions.applies(invoice)) {
      FacturXBasicRestrictions.check(invoice);
    }
    if (ExtendedCtcFrRestrictions.applies(invoice)) {
      ExtendedCtcFrRestrictions.check(invoice);
    }
    References references = invoice.references() == null ?
                            References.create()
                                      .build() :
                            invoice.references();
    var xml      = document();
    var extended = invoice.extended();
    xml.start("rsm:ExchangedDocumentContext");
    if (extended != null && extended.testIndicator() != null) {
      xml.start("ram:TestIndicator")
         .element("udt:Indicator",
                  extended.testIndicator())
         .end();
    }
    // BT-23 is optional, e.g. in the Factur-X profile EN 16931.
    if (invoice.processControl()
               .businessProcessId() != null) {
      xml.start("ram:BusinessProcessSpecifiedDocumentContextParameter")
         .element("ram:ID",
                  invoice.processControl()
                         .businessProcessId())
         .end();
    }
    xml.start("ram:GuidelineSpecifiedDocumentContextParameter")
       .element("ram:ID",
                invoice.processControl()
                       .specificationId())
       .end()
       .end();
    xml.start("rsm:ExchangedDocument")
       .element("ram:ID",
                invoice.number());
    if (extended != null) {
      xml.element("ram:Name",
                  extended.documentName());
    }
    xml.element("ram:TypeCode",
                invoice.typeCode()
                       .getCode());
    date(xml,
         "ram:IssueDateTime",
         invoice.issueDate());
    if (extended != null) {
      if (extended.copyIndicator() != null) {
        xml.start("ram:CopyIndicator")
           .element("udt:Indicator",
                    extended.copyIndicator())
           .end();
      }
      xml.element("ram:LanguageID",
                  extended.languageId());
    }
    invoice.notes()
           .forEach(note -> xml.start("ram:IncludedNote")
                               .element("ram:ContentCode",
                                        note.contentCode())
                               .element("ram:Content",
                                        note.text())
                               .element("ram:SubjectCode",
                                        note.subjectCode())
                               .end());
    if (extended != null && extended.effectiveDate() != null) {
      xml.start("ram:EffectiveSpecifiedPeriod");
      date(xml,
           "ram:CompleteDateTime",
           extended.effectiveDate());
      xml.end();
    }
    xml.end()
       .start("rsm:SupplyChainTradeTransaction");
    for (var line : invoice.lines()) {
      line(xml,
           line,
           extendedProfile,
           ExtendedCtcFrRestrictions.applies(invoice),
           invoice.currencyCode()
                  .name());
    }
    agreement(xml,
              invoice,
              references);
    delivery(xml,
             invoice,
             references);
    settlement(xml,
               invoice,
               references,
               extendedProfile);
    xml.end();
    return xml.toXml();
  }
}

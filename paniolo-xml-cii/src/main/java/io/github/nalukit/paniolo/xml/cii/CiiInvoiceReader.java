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
import io.github.nalukit.paniolo.model.common.AdditionalSupportingDocument;
import io.github.nalukit.paniolo.model.common.Address;
import io.github.nalukit.paniolo.model.common.Attachment;
import io.github.nalukit.paniolo.model.common.CashDiscount;
import io.github.nalukit.paniolo.model.common.Contact;
import io.github.nalukit.paniolo.model.common.CreditTransfer;
import io.github.nalukit.paniolo.model.common.DeliveryInformation;
import io.github.nalukit.paniolo.model.common.DirectDebit;
import io.github.nalukit.paniolo.model.common.DocumentAdjustment;
import io.github.nalukit.paniolo.model.common.DocumentTotals;
import io.github.nalukit.paniolo.model.common.ElectronicAddress;
import io.github.nalukit.paniolo.model.common.InvoiceLine;
import io.github.nalukit.paniolo.model.common.InvoiceNote;
import io.github.nalukit.paniolo.model.common.InvoicePeriod;
import io.github.nalukit.paniolo.model.common.ItemClassification;
import io.github.nalukit.paniolo.model.common.LineAdjustment;
import io.github.nalukit.paniolo.model.common.Party;
import io.github.nalukit.paniolo.model.common.PartyIdentifier;
import io.github.nalukit.paniolo.model.common.Payee;
import io.github.nalukit.paniolo.model.common.PaymentCardInformation;
import io.github.nalukit.paniolo.model.common.PaymentInstruction;
import io.github.nalukit.paniolo.model.common.PrecedingInvoiceReference;
import io.github.nalukit.paniolo.model.common.ProcessControl;
import io.github.nalukit.paniolo.model.common.References;
import io.github.nalukit.paniolo.model.common.SellerTaxRepresentativeParty;
import io.github.nalukit.paniolo.model.common.TaxCategory;
import io.github.nalukit.paniolo.model.common.value.Country;
import io.github.nalukit.paniolo.model.common.value.CurrencyCode;
import io.github.nalukit.paniolo.model.common.value.DateTimeQualifier;
import io.github.nalukit.paniolo.model.common.value.DutyTaxFeeCategory;
import io.github.nalukit.paniolo.model.common.value.InvoiceTypeCode;
import io.github.nalukit.paniolo.model.common.value.PaymentMeansCode;
import io.github.nalukit.paniolo.model.common.value.UnitCode;
import io.github.nalukit.paniolo.model.extended.AccountingAccount;
import io.github.nalukit.paniolo.model.extended.AdjustmentExtension;
import io.github.nalukit.paniolo.model.extended.AdvancePayment;
import io.github.nalukit.paniolo.model.extended.DocumentReference;
import io.github.nalukit.paniolo.model.extended.ExtendedContact;
import io.github.nalukit.paniolo.model.extended.ExtendedInformation;
import io.github.nalukit.paniolo.model.extended.FinancialAdjustment;
import io.github.nalukit.paniolo.model.extended.GrossPriceAdjustment;
import io.github.nalukit.paniolo.model.extended.IncludedProduct;
import io.github.nalukit.paniolo.model.extended.LineExtension;
import io.github.nalukit.paniolo.model.extended.LineSubtype;
import io.github.nalukit.paniolo.model.extended.LogisticsServiceCharge;
import io.github.nalukit.paniolo.model.extended.ObjectIdentifier;
import io.github.nalukit.paniolo.model.extended.PartyExtension;
import io.github.nalukit.paniolo.model.extended.PaymentTerm;
import io.github.nalukit.paniolo.model.extended.PaymentTermsAdjustment;
import io.github.nalukit.paniolo.model.extended.ProductCharacteristic;
import io.github.nalukit.paniolo.model.extended.ProductInstance;
import io.github.nalukit.paniolo.model.extended.TaxExchangeRate;
import io.github.nalukit.paniolo.model.extended.TypedDocumentReference;
import io.github.nalukit.paniolo.xml.InvoiceXmlReader;
import io.github.nalukit.paniolo.xml.ReadMode;
import io.github.nalukit.paniolo.xml.ReadWarning;
import io.github.nalukit.paniolo.xml.ReadResult;
import io.github.nalukit.paniolo.xml.StatedAmounts;
import io.github.nalukit.paniolo.xml.util.InvoiceConsistency;
import io.github.nalukit.paniolo.xml.util.ReadContext;
import io.github.nalukit.paniolo.xml.util.XmlElement;
import org.w3c.dom.Document;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Reads UN/CEFACT CII invoices into the syntax-independent invoice model, including the supported
 * Factur-X profiles and EXTENDED fields. ZUGFeRD 1.0 has a different structure and is handled by
 * {@link ZugferdV1InvoiceReader}.
 *
 * <p>The inherited convenience methods use strict reading by default. Explicit lenient reading
 * returns the accepted amount deviations with the model; it does not preserve the original XML.
 * Reading performs model and amount checks, not XSD or Schematron validation. Stateless and
 * thread-safe; input DOM documents must not be modified concurrently.
 *
 * @see io.github.nalukit.paniolo.xml.InvoiceXmlReader
 * @see io.github.nalukit.paniolo.xml.ReadMode
 */
public final class CiiInvoiceReader
    implements InvoiceXmlReader {
  private static final String              RSM        = "urn:un:unece:uncefact:data:standard:CrossIndustryInvoice:100";
  private static final Map<String, String> NAMESPACES = Map.of("rsm",
                                                               RSM,
                                                               "ram",
                                                               "urn:un:unece:uncefact:data:standard:ReusableAggregateBusinessInformationEntity:100",
                                                               "qdt",
                                                               "urn:un:unece:uncefact:data:standard:QualifiedDataType:100",
                                                               "udt",
                                                               "urn:un:unece:uncefact:data:standard:UnqualifiedDataType:100");

  /**
   * Creates a reader; it has no state.
   */
  public CiiInvoiceReader() {
  }

  /**
   * BT-7 and BT-8 are repeated on every VAT breakdown; the first occurrence is used.
   */
  private static void taxPoint(Invoice.Builder builder,
                               List<XmlElement> taxes) {
    for (var tax : taxes) {
      var    date = tax.basicDate("ram:TaxPointDate/udt:DateString");
      String code = tax.text("ram:DueDateTypeCode");
      if (date != null || code != null) {
        builder.taxPointDate(date)
               .taxPointDateCode(taxPointDateCode(code));
        return;
      }
    }
  }

  /**
   * CII expresses BT-8 with UNTDID 2475 instead of UNTDID 2005.
   */
  private static DateTimeQualifier taxPointDateCode(String code) {
    if (code == null) {
      return null;
    }
    return switch (code) {
      case "5" -> DateTimeQualifier.CODE_3;
      case "29" -> DateTimeQualifier.CODE_35;
      case "72" -> DateTimeQualifier.CODE_432;
      default -> throw new IllegalArgumentException("Unsupported VAT due date type code: " + code);
    };
  }

  private static PaymentInstruction paymentInstruction(List<XmlElement> means,
                                                       XmlElement settlement,
                                                       String mandate,
                                                       ReadContext context) {
    var first = means.get(0);
    var builder = PaymentInstruction.create()
                                    .meansCode(XmlElement.code(first.text("ram:TypeCode"),
                                                               PaymentMeansCode::of,
                                                               "payment means code"))
                                    .meansText(first.text("ram:Information"))
                                    .remittanceInformation(settlement.text("ram:PaymentReference"));
    for (int index = 0; index < means.size(); index++) {
      var mean    = means.get(index);
      var account = mean.find("ram:PayeePartyCreditorFinancialAccount");
      // Further blocks add credit transfer accounts (BG-17) of the same payment means only.
      if (index > 0 && (!java.util.Objects.equals(mean.text("ram:TypeCode"),
                                                  first.text("ram:TypeCode")) || mean.text("ram:Information") != null && !mean.text("ram:Information")
                                                                                                                               .equals(first.text("ram:Information")) || mean.find("ram:ApplicableTradeSettlementFinancialCard") != null ||
                        mean.find("ram:PayerPartyDebtorFinancialAccount") != null)) {
        context.otherPaymentMeans("Payment means " + (index + 1) + " (code " + mean.text("ram:TypeCode") + ", text " + mean.text("ram:Information") + ", account " + (account == null ?
                                                                                                                                                       null :
                                                                                                                                                       Objects.requireNonNullElse(account.text("ram:IBANID"),
                                                                                                                                                                                  account.text("ram:ProprietaryID"))) + ", BIC " +
                                  mean.text("ram:PayeeSpecifiedCreditorFinancialInstitution/ram:BICID") + ", card " + mean.text("ram:ApplicableTradeSettlementFinancialCard/ram:ID") + ", debited account " +
                                  mean.text("ram:PayerPartyDebtorFinancialAccount/ram:IBANID") + ") differs from payment means 1 (code " + first.text("ram:TypeCode") + ")");
        continue;
      }
      if (account != null) {
        String id = account.text("ram:IBANID");
        builder.addCreditTransfer(new CreditTransfer(id == null ?
                                                     account.text("ram:ProprietaryID") :
                                                     id,
                                                     account.text("ram:AccountName"),
                                                     mean.text("ram:PayeeSpecifiedCreditorFinancialInstitution/ram:BICID")));
      }
    }
    var card = first.find("ram:ApplicableTradeSettlementFinancialCard");
    if (card != null) {
      builder.paymentCard(new PaymentCardInformation(card.text("ram:ID"),
                                                     card.text("ram:CardholderName")));
    }
    String debited = first.text("ram:PayerPartyDebtorFinancialAccount/ram:IBANID");
    if (mandate != null || debited != null) {
      builder.directDebit(new DirectDebit(mandate,
                                          settlement.text("ram:CreditorReferenceID"),
                                          debited));
    }
    return builder.build();
  }

  /**
   * The invoiced object references (type code 130, BT-128) of a line in document order.
   */
  private static List<XmlElement> objectReferences(XmlElement lineSettlement) {
    return lineSettlement.children("ram:AdditionalReferencedDocument")
                         .stream()
                         .filter(referenced -> "130".equals(referenced.text("ram:TypeCode")))
                         .toList();
  }

  /**
   * The accounting accounts after the first one, which carries the buyer accounting reference
   * (BT-19 / BT-133); the profile EXTENDED repeats them.
   */
  private static List<AccountingAccount> furtherAccounts(XmlElement settlement) {
    var accounts = settlement.children("ram:ReceivableSpecifiedTradeAccountingAccount");
    return accounts.stream()
                   .skip(1)
                   .filter(account -> account.text("ram:ID") != null)
                   .map(account -> new AccountingAccount(account.text("ram:ID"),
                                                         account.text("ram:TypeCode")))
                   .toList();
  }

  private static DeliveryInformation delivery(XmlElement delivery,
                                              ReadContext context) {
    var shipTo = delivery.find("ram:ShipToTradeParty");
    var date   = delivery.basicDate("ram:ActualDeliverySupplyChainEvent/ram:OccurrenceDateTime/udt:DateTimeString");
    if (shipTo == null && date == null) {
      return null;
    }
    var address = shipTo == null ?
                  null :
                  shipTo.find("ram:PostalTradeAddress");
    // BT-71 is one location identifier: ram:ID, or ram:GlobalID with its scheme (BT-71-1).
    var    local    = shipTo == null ?
                      null :
                      shipTo.findWithText("ram:ID");
    var    global   = shipTo == null ?
                      null :
                      shipTo.findWithText("ram:GlobalID");
    var    location = local != null ?
                      local :
                      global;
    if (local != null && global != null) {
      context.lost(ReadWarning.IDENTIFIER,
                   "Deliver-to location (BT-71) has the identifier " + local.text() + "; the global identifier " + global.text() + " (scheme " + global.attribute("schemeID") + ") is not kept");
    }
    return new DeliveryInformation(shipTo == null ?
                                   null :
                                   shipTo.text("ram:Name"),
                                   location == null ?
                                   null :
                                   location.text(),
                                   date,
                                   address == null ?
                                   null :
                                   address(address),
                                   location == null || location == local ?
                                   null :
                                   location.attribute("schemeID"));
  }

  static Party party(XmlElement party) {
    return party(party,
                 false);
  }

  /**
   * Reads a party. For Order-X ({@code orderX}) a global identifier next to {@code ram:ID} is kept in
   * the party extension.
   */
  static Party party(XmlElement party,
                     boolean orderX) {
    if (party == null) {
      return null;
    }
    String vatId = null;
    String taxId = null;
    for (var registration : party.children("ram:SpecifiedTaxRegistration")) {
      var id = registration.find("ram:ID");
      if (id != null && "VA".equals(id.attribute("schemeID"))) {
        vatId = id.text();
      } else if (id != null && "FC".equals(id.attribute("schemeID"))) {
        taxId = id.text();
      }
    }
    var uri     = party.findWithText("ram:URIUniversalCommunication/ram:URIID");
    var contact = party.find("ram:DefinedTradeContact");
    // A contact with department, type, or fax is an EXTENDED contact.
    ExtendedContact extendedContact = null;
    if (contact != null && (contact.find("ram:DepartmentName") != null || contact.find("ram:TypeCode") != null || contact.find("ram:FaxUniversalCommunication") != null)) {
      extendedContact = new ExtendedContact(contact.text("ram:PersonName"),
                                            contact.text("ram:DepartmentName"),
                                            contact.text("ram:TypeCode"),
                                            contact.text("ram:TelephoneUniversalCommunication/ram:CompleteNumber"),
                                            contact.text("ram:FaxUniversalCommunication/ram:CompleteNumber"),
                                            contact.text("ram:EmailURIUniversalCommunication/ram:URIID"));
      contact         = null;
    }
    // Empty identifier elements count as absent.
    var localId  = party.findWithText("ram:ID");
    var globalId = party.findWithText("ram:GlobalID");
    var legalId  = party.findWithText("ram:SpecifiedLegalOrganization/ram:ID");
    var builder = Party.create()
                       .name(party.text("ram:Name"))
                       .additionalLegalInformation(party.text("ram:Description"))
                       .tradingName(party.text("ram:SpecifiedLegalOrganization/ram:TradingBusinessName"))
                       .address(address(party.find("ram:PostalTradeAddress")))
                       .vatId(vatId)
                       .taxId(taxId)
                       .electronicAddress(uri == null ?
                                          null :
                                          new ElectronicAddress(uri.text(),
                                                                uri.attribute("schemeID")))
                       .contact(contact == null ?
                                null :
                                new Contact(contact.text("ram:PersonName"),
                                            contact.text("ram:TelephoneUniversalCommunication/ram:CompleteNumber"),
                                            contact.text("ram:EmailURIUniversalCommunication/ram:URIID")))
                       .extended(new PartyExtension(party.text("ram:RoleCode"),
                                                    address(party.find("ram:SpecifiedLegalOrganization/ram:PostalTradeAddress")),
                                                    extendedContact,
                                                    // A global identifier next to ram:ID.
                                                    !orderX || localId == null || globalId == null ?
                                                    null :
                                                    globalId.text(),
                                                    !orderX || localId == null || globalId == null ?
                                                    null :
                                                    globalId.attribute("schemeID")))
                       .legalRegistrationId(legalId == null ?
                                            null :
                                            legalId.text(),
                                            legalId == null ?
                                            null :
                                            legalId.attribute("schemeID"));
    // An identifier without scheme is ram:ID, one with scheme ram:GlobalID.
    if (localId != null) {
      builder.identifier(localId.text());
    } else if (globalId != null) {
      builder.identifier(globalId.text(),
                         globalId.attribute("schemeID"));
    }
    if (!orderX) {
      // Further identifiers, e.g. a second seller identifier (BT-29 is repeatable) or a global
      // identifier next to the local one; Order-X keeps the latter in its EXTENDED information.
      var further = new ArrayList<PartyIdentifier>();
      for (var id : party.children("ram:ID")) {
        if (id.text() != null) {
          further.add(new PartyIdentifier(id.text(),
                                          null));
        }
      }
      for (var id : party.children("ram:GlobalID")) {
        if (id.text() != null) {
          further.add(new PartyIdentifier(id.text(),
                                          id.attribute("schemeID")));
        }
      }
      further.subList(0,
                      Math.min(1,
                               further.size()))
             .clear();
      further.forEach(builder::addAdditionalIdentifier);
    }
    return builder.build();
  }

  static Address address(XmlElement address) {
    if (address == null) {
      return null;
    }
    return new Address(address.text("ram:LineOne"),
                       address.text("ram:LineTwo"),
                       address.text("ram:LineThree"),
                       address.text("ram:CityName"),
                       address.text("ram:PostcodeCode"),
                       address.text("ram:CountrySubDivisionName"),
                       XmlElement.code(address.text("ram:CountryID"),
                                       Country::of,
                                       "country"));
  }

  private static TaxCategory tax(XmlElement tax) {
    if (tax == null) {
      return null;
    }
    var code = XmlElement.code(tax.text("ram:CategoryCode"),
                               DutyTaxFeeCategory::of,
                               "VAT category");
    // Outside-scope breakdowns state a zero rate for XRechnung; the model keeps no rate.
    BigDecimal rate = code == DutyTaxFeeCategory.O ?
                      null :
                      tax.decimal("ram:RateApplicablePercent");
    return new TaxCategory(code,
                           rate,
                           tax.text("ram:ExemptionReason"),
                           tax.text("ram:ExemptionReasonCode"));
  }

  /**
   * Reads the totals and the VAT breakdown of a document without lines.
   */
  private static DocumentTotals statedTotals(XmlElement settlement,
                                             XmlElement summation,
                                             BigDecimal statedTax) {
    var totals = DocumentTotals.create()
                               .lineTotal(summation.decimal("ram:LineTotalAmount"))
                               .taxBasisTotal(summation.decimal("ram:TaxBasisTotalAmount"))
                               .taxTotal(statedTax == null ?
                                         BigDecimal.ZERO :
                                         statedTax);
    for (var tax : settlement.children("ram:ApplicableTradeTax")) {
      totals.addVatBreakdown(tax(tax),
                             tax.decimal("ram:BasisAmount"),
                             tax.decimal("ram:CalculatedAmount"));
    }
    return totals.build();
  }

  /**
   * Reads the header information of the profile EXTENDED except the payment terms.
   */
  private static void extended(ExtendedInformation.Builder extended,
                               XmlElement context,
                               XmlElement header,
                               XmlElement agreement,
                               XmlElement delivery,
                               XmlElement settlement,
                               Map<String, TaxCategory> categories) {
    String test = context.text("ram:TestIndicator/udt:Indicator");
    String copy = header.text("ram:CopyIndicator/udt:Indicator");
    extended.testIndicator(test == null ?
                           null :
                           Boolean.parseBoolean(test))
            .documentName(header.text("ram:Name"))
            .copyIndicator(copy == null ?
                           null :
                           Boolean.parseBoolean(copy))
            .languageId(header.text("ram:LanguageID"))
            .effectiveDate(header.basicDate("ram:EffectiveSpecifiedPeriod/ram:CompleteDateTime/udt:DateTimeString"))
            .deliveryTermsLocationName(agreement.text("ram:ApplicableTradeDeliveryTerms/ram:RelevantTradeLocation/ram:Name"))
            .deliveryTermsLocationCountry(XmlElement.code(agreement.text("ram:ApplicableTradeDeliveryTerms/ram:RelevantTradeLocation/ram:CountryID"),
                                                          Country::of,
                                                          "country"))
            .invoiceIssuerReference(settlement.text("ram:InvoiceIssuerReference"))
            .billingPeriodDescription(settlement.text("ram:BillingSpecifiedPeriod/ram:Description"))
            .buyerAccountingTypeCode(settlement.text("ram:ReceivableSpecifiedTradeAccountingAccount/ram:TypeCode"))
            .payerAccountName(settlement.text("ram:SpecifiedTradeSettlementPaymentMeans/ram:PayerPartyDebtorFinancialAccount/ram:AccountName"))
            .payerBic(settlement.text("ram:SpecifiedTradeSettlementPaymentMeans/ram:PayerSpecifiedDebtorFinancialInstitution/ram:BICID"));
    furtherAccounts(settlement).forEach(extended::addAdditionalAccountingAccount);
    var exchange = settlement.find("ram:TaxApplicableTradeCurrencyExchange");
    if (exchange != null) {
      extended.taxExchangeRate(new TaxExchangeRate(exchange.decimal("ram:ConversionRate"),
                                                   exchange.basicDate("ram:ConversionRateDateTime/udt:DateTimeString")));
    }
    for (var adjustment : settlement.children("ram:SpecifiedFinancialAdjustment")) {
      extended.addFinancialAdjustment(new FinancialAdjustment(adjustment.text("ram:Reason"),
                                                              adjustment.decimal("ram:ActualAmount")));
    }
    if (delivery != null) {
      extended.transportModeCode(delivery.text("ram:RelatedSupplyChainConsignment/ram:SpecifiedLogisticsTransportMovement/ram:ModeCode"));
    }
    extended.salesAgent(party(agreement.find("ram:SalesAgentTradeParty")))
            .buyerTaxRepresentative(party(agreement.find("ram:BuyerTaxRepresentativeTradeParty")))
            .productEndUser(party(agreement.find("ram:ProductEndUserTradeParty")))
            .buyerAgent(party(agreement.find("ram:BuyerAgentTradeParty")))
            .deliveryTermsCode(agreement.text("ram:ApplicableTradeDeliveryTerms/ram:DeliveryTypeCode"))
            .quotationReference(documentReference(agreement.find("ram:QuotationReferencedDocument")))
            .invoicer(party(settlement.find("ram:InvoicerTradeParty")))
            .invoicee(party(settlement.find("ram:InvoiceeTradeParty")))
            .payer(party(settlement.find("ram:PayerTradeParty")));
    for (var order : agreement.children("ram:UltimateCustomerOrderReferencedDocument")) {
      extended.addUltimateCustomerOrderReference(documentReference(order));
    }
    if (delivery != null) {
      extended.ultimateShipTo(party(delivery.find("ram:UltimateShipToTradeParty")))
              .shipFrom(party(delivery.find("ram:ShipFromTradeParty")))
              .deliveryNoteReference(documentReference(delivery.find("ram:DeliveryNoteReferencedDocument")));
    }
    for (var service : settlement.children("ram:SpecifiedLogisticsServiceCharge")) {
      extended.addLogisticsServiceCharge(new LogisticsServiceCharge(service.text("ram:Description"),
                                                                    service.decimal("ram:AppliedAmount"),
                                                                    InvoiceConsistency.complete(tax(service.find("ram:AppliedTradeTax")),
                                                                                                categories)));
    }
    for (var advance : settlement.children("ram:SpecifiedAdvancePayment")) {
      var tax = advance.find("ram:IncludedTradeTax");
      extended.addAdvancePayment(new AdvancePayment(advance.decimal("ram:PaidAmount"),
                                                    advance.basicDate("ram:FormattedReceivedDateTime/qdt:DateTimeString"),
                                                    tax(tax),
                                                    tax == null ?
                                                    null :
                                                    tax.decimal("ram:CalculatedAmount"),
                                                    advance.text("ram:InvoiceSpecifiedReferencedDocument/ram:IssuerAssignedID")));
    }
  }

  private static DocumentReference documentReference(XmlElement document) {
    if (document == null) {
      return null;
    }
    return new DocumentReference(document.text("ram:IssuerAssignedID"),
                                 document.text("ram:LineID"),
                                 document.basicDate("ram:FormattedIssueDateTime/qdt:DateTimeString"));
  }

  private static PaymentTermsAdjustment paymentTermsAdjustment(XmlElement terms,
                                                               String amountName) {
    if (terms == null) {
      return null;
    }
    BigDecimal days = terms.decimal("ram:BasisPeriodMeasure");
    return new PaymentTermsAdjustment(terms.basicDate("ram:BasisDateTime/udt:DateTimeString"),
                                      days == null ?
                                      null :
                                      days.intValueExact(),
                                      terms.decimal("ram:BasisAmount"),
                                      terms.decimal("ram:CalculationPercent"),
                                      terms.decimal(amountName));
  }

  /**
   * Reads a trade line item (BG-25) with its price, allowances, charges and item details.
   */
  private static InvoiceLine invoiceLine(XmlElement item,
                                         Map<String, TaxCategory> categories,
                                         ReadContext context) {
    var quantity   = item.find("ram:SpecifiedLineTradeDelivery/ram:BilledQuantity");
    var price      = item.find("ram:SpecifiedLineTradeAgreement/ram:NetPriceProductTradePrice");
    var gross      = item.find("ram:SpecifiedLineTradeAgreement/ram:GrossPriceProductTradePrice");
    var settlement = item.find("ram:SpecifiedLineTradeSettlement");
    var product    = item.find("ram:SpecifiedTradeProduct");
    BigDecimal baseQuantity = price == null ?
                              null :
                              price.decimal("ram:BasisQuantity");
    var builder = InvoiceLine.create()
                             .id(item.text("ram:AssociatedDocumentLineDocument/ram:LineID"))
                             .note(item.text("ram:AssociatedDocumentLineDocument/ram:IncludedNote/ram:Content"))
                             .quantity(quantity == null ?
                                       null :
                                       quantity.decimal())
                             .unitCode(quantity == null ?
                                       null :
                                       XmlElement.code(quantity.attribute("unitCode"),
                                                       UnitCode::of,
                                                       "unit code"))
                             .netUnitPrice(price == null ?
                                           null :
                                           price.decimal("ram:ChargeAmount"))
                             .priceBaseQuantity(baseQuantity == null ?
                                                BigDecimal.ONE :
                                                baseQuantity)
                             .tax(InvoiceConsistency.complete(tax(item.find("ram:SpecifiedLineTradeSettlement/ram:ApplicableTradeTax")),
                                                              categories))
                             .orderLineReference(item.text("ram:SpecifiedLineTradeAgreement/ram:BuyerOrderReferencedDocument/ram:LineID"));
    if (product != null) {
      builder.name(product.text("ram:Name"))
             .description(product.text("ram:Description"))
             .sellerItemId(product.text("ram:SellerAssignedID"))
             .buyerItemId(product.text("ram:BuyerAssignedID"))
             .originCountry(XmlElement.code(product.text("ram:OriginTradeCountry/ram:ID"),
                                            Country::of,
                                            "country"));
      var standard = product.findWithText("ram:GlobalID");
      if (standard != null) {
        builder.standardItemId(standard.text(),
                               standard.attribute("schemeID"));
      }
      for (var attribute : product.children("ram:ApplicableProductCharacteristic")) {
        // Characteristics with type code or measure belong to the profile EXTENDED.
        if (attribute.find("ram:TypeCode") == null && attribute.find("ram:ValueMeasure") == null) {
          builder.addAttribute(attribute.text("ram:Description"),
                               attribute.text("ram:Value"));
        }
      }
      for (var classification : product.children("ram:DesignatedProductClassification")) {
        var code = classification.find("ram:ClassCode");
        if (code != null) {
          builder.addClassification(new ItemClassification(code.text(),
                                                           code.attribute("listID"),
                                                           code.attribute("listVersionID"),
                                                           classification.text("ram:ClassName")));
        }
      }
    }
    if (gross != null) {
      // EN 16931 knows one discount (BT-147); EXTENDED may state several allowances and charges,
      // which together make up that discount.
      BigDecimal discount = null;
      for (var adjustment : grossPriceAdjustments(gross)) {
        discount = (discount == null ?
                    BigDecimal.ZERO :
                    discount).add(adjustment.discount());
      }
      builder.grossPrice(gross.decimal("ram:ChargeAmount"))
             .priceDiscount(discount);
    }
    if (settlement != null) {
      var period = settlement.find("ram:BillingSpecifiedPeriod");
      if (period != null) {
        builder.period(new InvoicePeriod(period.basicDate("ram:StartDateTime/udt:DateTimeString"),
                                         period.basicDate("ram:EndDateTime/udt:DateTimeString")));
      }
      for (var adjustment : settlement.children("ram:SpecifiedTradeAllowanceCharge")) {
        boolean charge = Boolean.parseBoolean(adjustment.text("ram:ChargeIndicator/udt:Indicator"));
        builder.addAdjustment(new LineAdjustment(charge,
                                                 adjustment.decimal("ram:ActualAmount"),
                                                 adjustment.decimal("ram:BasisAmount"),
                                                 context.percentage(charge,
                                                                    adjustment.decimal("ram:CalculationPercent"),
                                                                    adjustment.decimal("ram:BasisAmount"),
                                                                    item.text("ram:AssociatedDocumentLineDocument/ram:LineID")),
                                                 adjustment.text("ram:Reason"),
                                                 context.reasonCode(charge,
                                                                    adjustment.text("ram:ReasonCode"),
                                                                    adjustment.text("ram:Reason"),
                                                                    item.text("ram:AssociatedDocumentLineDocument/ram:LineID"))));
      }
      // The first invoiced object is BT-128; further ones are EXTENDED line information.
      var objects = objectReferences(settlement);
      if (!objects.isEmpty()) {
        builder.objectId(objects.getFirst()
                                .text("ram:IssuerAssignedID"),
                         objects.getFirst()
                                .text("ram:ReferenceTypeCode"));
      }
      builder.buyerAccountingReference(settlement.text("ram:ReceivableSpecifiedTradeAccountingAccount/ram:ID"));
    }
    builder.extended(lineExtension(item));
    return context.netAmount(builder.build(),
                             item.decimal("ram:SpecifiedLineTradeSettlement/ram:SpecifiedTradeSettlementLineMonetarySummation/ram:LineTotalAmount"));
  }

  /**
   * Reads the allowances and charges on the gross price in the order stated.
   */
  private static List<GrossPriceAdjustment> grossPriceAdjustments(XmlElement gross) {
    var adjustments = new ArrayList<GrossPriceAdjustment>();
    for (var adjustment : gross.children("ram:AppliedTradeAllowanceCharge")) {
      adjustments.add(new GrossPriceAdjustment(Boolean.parseBoolean(adjustment.text("ram:ChargeIndicator/udt:Indicator")),
                                               adjustment.decimal("ram:ActualAmount"),
                                               adjustment.text("ram:ReasonCode"),
                                               adjustment.text("ram:Reason")));
    }
    return adjustments;
  }

  /**
   * Reads the line information of the profile EXTENDED.
   */
  private static LineExtension lineExtension(XmlElement item) {
    var document = item.find("ram:AssociatedDocumentLineDocument");
    var product  = item.find("ram:SpecifiedTradeProduct");
    var agree    = item.find("ram:SpecifiedLineTradeAgreement");
    var delivery = item.find("ram:SpecifiedLineTradeDelivery");
    var settle   = item.find("ram:SpecifiedLineTradeSettlement");
    var extended = LineExtension.create();
    if (document != null) {
      String subtype = document.text("ram:LineStatusReasonCode");
      extended.parentLineId(document.text("ram:ParentLineID"))
              .statusCode(document.text("ram:LineStatusCode"))
              .noteContentCode(document.text("ram:IncludedNote/ram:ContentCode"))
              .noteSubjectCode(document.text("ram:IncludedNote/ram:SubjectCode"))
              .subtype(subtype == null ?
                       null :
                       XmlElement.code(subtype,
                                       LineSubtype::of,
                                       "line subtype"));
    }
    if (product != null) {
      extended.productId(product.text("ram:ID"))
              .industryItemId(product.text("ram:IndustryAssignedID"))
              .modelId(product.text("ram:ModelID"))
              .modelName(product.text("ram:ModelName"))
              .brandName(product.text("ram:BrandName"))
              .manufacturer(party(product.find("ram:ManufacturerTradeParty")));
      for (var batch : product.children("ram:BatchID")) {
        extended.addBatchId(batch.text());
      }
      for (var characteristic : product.children("ram:ApplicableProductCharacteristic")) {
        var measure = characteristic.find("ram:ValueMeasure");
        if (characteristic.find("ram:TypeCode") != null || measure != null) {
          extended.addCharacteristic(new ProductCharacteristic(characteristic.text("ram:TypeCode"),
                                                               characteristic.text("ram:Description"),
                                                               characteristic.text("ram:Value"),
                                                               measure == null ?
                                                               null :
                                                               measure.decimal(),
                                                               measure == null ?
                                                               null :
                                                               XmlElement.code(measure.attribute("unitCode"),
                                                                               UnitCode::of,
                                                                               "unit code")));
        }
      }
      for (var instance : product.children("ram:IndividualTradeProductInstance")) {
        extended.addInstance(new ProductInstance(instance.text("ram:BatchID"),
                                                 instance.text("ram:SupplierAssignedSerialID")));
      }
      for (var included : product.children("ram:IncludedReferencedProduct")) {
        var quantity = included.find("ram:UnitQuantity");
        extended.addIncludedProduct(new IncludedProduct(included.text("ram:Name"),
                                                        included.text("ram:ID"),
                                                        included.text("ram:SellerAssignedID"),
                                                        included.text("ram:BuyerAssignedID"),
                                                        included.text("ram:IndustryAssignedID"),
                                                        included.text("ram:Description"),
                                                        quantity == null ?
                                                        null :
                                                        quantity.decimal(),
                                                        quantity == null ?
                                                        null :
                                                        XmlElement.code(quantity.attribute("unitCode"),
                                                                        UnitCode::of,
                                                                        "unit code")));
      }
    }
    if (agree != null) {
      extended.deliveryTermsCode(agree.text("ram:ApplicableTradeDeliveryTerms/ram:DeliveryTypeCode"))
              .sellerOrderReference(documentReference(agree.find("ram:SellerOrderReferencedDocument")))
              .quotationReference(documentReference(agree.find("ram:QuotationReferencedDocument")))
              .contractReference(documentReference(agree.find("ram:ContractReferencedDocument")))
              .itemSeller(party(agree.find("ram:ItemSellerTradeParty")));
      var gross = agree.find("ram:GrossPriceProductTradePrice");
      if (gross != null) {
        var adjustments = grossPriceAdjustments(gross);
        // A single allowance without reason is the EN 16931 discount (BT-147) and needs no parts.
        if (adjustments.size() > 1 || adjustments.stream()
                                                 .anyMatch(adjustment -> adjustment.charge() || adjustment.reasonCode() != null || adjustment.reason() != null)) {
          adjustments.forEach(extended::addPriceAdjustment);
        }
      }
      for (var order : agree.children("ram:UltimateCustomerOrderReferencedDocument")) {
        extended.addUltimateCustomerOrderReference(documentReference(order));
      }
      for (var reference : agree.children("ram:AdditionalReferencedDocument")) {
        extended.addAdditionalReference(new TypedDocumentReference(reference.text("ram:IssuerAssignedID"),
                                                                   reference.text("ram:TypeCode"),
                                                                   reference.text("ram:ReferenceTypeCode")));
      }
    }
    if (delivery != null) {
      extended.chargeFreeQuantity(delivery.decimal("ram:ChargeFreeQuantity"))
              .packageQuantity(delivery.decimal("ram:PackageQuantity"))
              .perPackageUnitQuantity(delivery.decimal("ram:PerPackageUnitQuantity"))
              .shipTo(party(delivery.find("ram:ShipToTradeParty")))
              .ultimateShipTo(party(delivery.find("ram:UltimateShipToTradeParty")))
              .actualDeliveryDate(delivery.basicDate("ram:ActualDeliverySupplyChainEvent/ram:OccurrenceDateTime/udt:DateTimeString"))
              .despatchAdviceReference(documentReference(delivery.find("ram:DespatchAdviceReferencedDocument")))
              .receivingAdviceReference(documentReference(delivery.find("ram:ReceivingAdviceReferencedDocument")))
              .deliveryNoteReference(documentReference(delivery.find("ram:DeliveryNoteReferencedDocument")));
    }
    if (settle != null) {
      furtherAccounts(settle).forEach(extended::addAdditionalAccountingAccount);
      extended.precedingInvoiceReference(documentReference(settle.find("ram:InvoiceReferencedDocument")))
              .accountingTypeCode(settle.text("ram:ReceivableSpecifiedTradeAccountingAccount/ram:TypeCode"))
              .taxTotal(settle.decimal("ram:SpecifiedTradeSettlementLineMonetarySummation/ram:TaxTotalAmount"))
              .grossTotal(settle.decimal("ram:SpecifiedTradeSettlementLineMonetarySummation/ram:GrandTotalAmount"));
      objectReferences(settle).stream()
                              .skip(1)
                              .forEach(object -> extended.addFurtherObjectIdentifier(new ObjectIdentifier(object.text("ram:IssuerAssignedID"),
                                                                                                          object.text("ram:ReferenceTypeCode"))));
    }
    return extended.build();
  }

  private static Payee payee(XmlElement payee) {
    if (payee == null) {
      return null;
    }
    // An identifier without scheme is ram:ID, one with scheme ram:GlobalID.
    var globalId = payee.text("ram:ID") == null ?
                   payee.findWithText("ram:GlobalID") :
                   null;
    var legalId = payee.findWithText("ram:SpecifiedLegalOrganization/ram:ID");
    return Payee.create()
                .name(payee.text("ram:Name"))
                .identifier(globalId == null ?
                            payee.text("ram:ID") :
                            globalId.text(),
                            globalId == null ?
                            null :
                            globalId.attribute("schemeID"))
                .legalRegistrationId(legalId == null ?
                                     null :
                                     legalId.text(),
                                     legalId == null ?
                                     null :
                                     legalId.attribute("schemeID"))
                .build();
  }

  /**
   * Sequence number and base quantity of an EXTENDED document allowance or charge, or null.
   */
  private static AdjustmentExtension adjustmentExtension(XmlElement adjustment) {
    String sequence = adjustment.text("ram:SequenceNumeric");
    var    quantity = adjustment.find("ram:BasisQuantity");
    if (sequence == null && quantity == null) {
      return null;
    }
    try {
      return new AdjustmentExtension(sequence == null ?
                                     null :
                                     Integer.valueOf(sequence),
                                     quantity == null ?
                                     null :
                                     quantity.decimal(),
                                     quantity == null ?
                                     null :
                                     XmlElement.code(quantity.attribute("unitCode"),
                                                     UnitCode::of,
                                                     "unit code"));
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("Invalid sequence number or base quantity of an allowance or charge: " + e.getMessage(),
                                         e);
    }
  }

  @Override
  public ReadResult read(Document document,
                         ReadMode mode) {
    Objects.requireNonNull(document,
                           "document");
    var readContext = new ReadContext(mode);
    var root = XmlElement.of(document.getDocumentElement(),
                             NAMESPACES);
    if (!"CrossIndustryInvoice".equals(root.localName()) || !RSM.equals(root.namespace())) {
      throw new IllegalArgumentException("Not a CII CrossIndustryInvoice: {" + root.namespace() + "}" + root.localName());
    }
    var context     = root.find("rsm:ExchangedDocumentContext");
    var header      = root.find("rsm:ExchangedDocument");
    var transaction = root.find("rsm:SupplyChainTradeTransaction");
    if (context == null || header == null || transaction == null) {
      throw new IllegalArgumentException("CII document context, header or transaction is missing");
    }
    var agreement  = transaction.find("ram:ApplicableHeaderTradeAgreement");
    var delivery   = transaction.find("ram:ApplicableHeaderTradeDelivery");
    var settlement = transaction.find("ram:ApplicableHeaderTradeSettlement");
    if (agreement == null || settlement == null) {
      throw new IllegalArgumentException("CII header trade agreement or settlement is missing");
    }
    String currency = settlement.text("ram:InvoiceCurrencyCode");
    var builder = Invoice.create()
                         .number(header.text("ram:ID"))
                         .issueDate(header.basicDate("ram:IssueDateTime/udt:DateTimeString"))
                         .typeCode(XmlElement.code(header.text("ram:TypeCode"),
                                                   InvoiceTypeCode::of,
                                                   "invoice type code"))
                         .currencyCode(XmlElement.code(currency,
                                                       CurrencyCode::of,
                                                       "currency"))
                         .processControl(new ProcessControl(context.text("ram:BusinessProcessSpecifiedDocumentContextParameter/ram:ID"),
                                                            context.text("ram:GuidelineSpecifiedDocumentContextParameter/ram:ID")))
                         .buyerReference(agreement.text("ram:BuyerReference"));
    for (var note : header.children("ram:IncludedNote")) {
      builder.addNote(new InvoiceNote(note.text("ram:SubjectCode"),
                                      note.text("ram:Content"),
                                      note.text("ram:ContentCode")));
    }

    var references = References.create()
                               .salesOrderReference(agreement.text("ram:SellerOrderReferencedDocument/ram:IssuerAssignedID"))
                               .purchaseOrderReference(agreement.text("ram:BuyerOrderReferencedDocument/ram:IssuerAssignedID"))
                               .contractReference(agreement.text("ram:ContractReferencedDocument/ram:IssuerAssignedID"))
                               .projectReference(agreement.text("ram:SpecifiedProcuringProject/ram:ID"))
                               .buyerAccountingReference(settlement.text("ram:ReceivableSpecifiedTradeAccountingAccount/ram:ID"));
    for (var referenced : agreement.children("ram:AdditionalReferencedDocument")) {
      String id = referenced.text("ram:IssuerAssignedID");
      switch (Objects.requireNonNullElse(referenced.text("ram:TypeCode"),
                                         "")) {
        case "50" -> references.tenderOrLotReference(id);
        case "130" -> references.invoicedObjectId(id)
                                .invoicedObjectSchemeId(referenced.text("ram:ReferenceTypeCode"));
        default -> {
          var embedded = referenced.find("ram:AttachmentBinaryObject");
          builder.addSupportingDocument(new AdditionalSupportingDocument(id,
                                                                         referenced.text("ram:Name"),
                                                                         referenced.text("ram:URIID"),
                                                                         // An empty binary object carries no attachment.
                                                                         embedded == null || embedded.text() == null ?
                                                                         null :
                                                                         new Attachment(Base64.getMimeDecoder()
                                                                                              .decode(embedded.text()),
                                                                                        embedded.attribute("mimeCode"),
                                                                                        embedded.attribute("filename"))));
        }
      }
    }
    if (delivery != null) {
      references.despatchAdviceReference(delivery.text("ram:DespatchAdviceReferencedDocument/ram:IssuerAssignedID"))
                .receivingAdviceReference(delivery.text("ram:ReceivingAdviceReferencedDocument/ram:IssuerAssignedID"));
      builder.delivery(delivery(delivery,
                                readContext));
    }
    var builtReferences = references.build();
    builder.references(builtReferences.equals(References.create()
                                                        .build()) ?
                       null :
                       builtReferences);

    builder.seller(party(agreement.find("ram:SellerTradeParty")))
           .buyer(party(agreement.find("ram:BuyerTradeParty")));
    var representative = agreement.find("ram:SellerTaxRepresentativeTradeParty");
    if (representative != null) {
      builder.taxRepresentative(new SellerTaxRepresentativeParty(representative.text("ram:Name"),
                                                                 representative.text("ram:SpecifiedTaxRegistration/ram:ID"),
                                                                 address(representative.find("ram:PostalTradeAddress"))));
    }
    builder.payee(payee(settlement.find("ram:PayeeTradeParty")));

    String     taxCurrency = settlement.text("ram:TaxCurrencyCode");
    var        summation   = settlement.find("ram:SpecifiedTradeSettlementHeaderMonetarySummation");
    BigDecimal statedTax   = null;
    if (summation != null) {
      for (var amount : summation.children("ram:TaxTotalAmount")) {
        String amountCurrency = amount.attribute("currencyID");
        if (taxCurrency != null && taxCurrency.equals(amountCurrency) && !taxCurrency.equals(currency)) {
          builder.taxAmountInTaxCurrency(amount.decimal());
        } else if (amountCurrency == null || amountCurrency.equals(currency)) {
          statedTax = amount.decimal();
        }
      }
      BigDecimal prepaid = summation.decimal("ram:TotalPrepaidAmount");
      builder.paidAmount(prepaid == null ?
                         BigDecimal.ZERO :
                         prepaid)
             .roundingAmount(summation.decimal("ram:RoundingAmount"));
    }
    builder.taxCurrencyCode(XmlElement.code(taxCurrency,
                                            CurrencyCode::of,
                                            "currency"));

    var breakdowns = new ArrayList<TaxCategory>();
    for (var tax : settlement.children("ram:ApplicableTradeTax")) {
      breakdowns.add(tax(tax));
    }
    taxPoint(builder,
             settlement.children("ram:ApplicableTradeTax"));
    var categories = InvoiceConsistency.byCategoryAndRate(breakdowns);
    var period     = settlement.find("ram:BillingSpecifiedPeriod");
    if (period != null) {
      builder.invoicePeriod(new InvoicePeriod(period.basicDate("ram:StartDateTime/udt:DateTimeString"),
                                              period.basicDate("ram:EndDateTime/udt:DateTimeString")));
    }
    for (var adjustment : settlement.children("ram:SpecifiedTradeAllowanceCharge")) {
      boolean charge = Boolean.parseBoolean(adjustment.text("ram:ChargeIndicator/udt:Indicator"));
      builder.addAdjustment(new DocumentAdjustment(charge,
                                                   adjustment.decimal("ram:ActualAmount"),
                                                   adjustment.decimal("ram:BasisAmount"),
                                                   readContext.percentage(charge,
                                                                          adjustment.decimal("ram:CalculationPercent"),
                                                                          adjustment.decimal("ram:BasisAmount"),
                                                                          null),
                                                   adjustment.text("ram:Reason"),
                                                   readContext.reasonCode(charge,
                                                                          adjustment.text("ram:ReasonCode"),
                                                                          adjustment.text("ram:Reason"),
                                                                          null),
                                                   InvoiceConsistency.complete(tax(adjustment.find("ram:CategoryTradeTax")),
                                                                               categories),
                                                   adjustmentExtension(adjustment)));
    }
    // Terms with a partial amount, penalty, or discount are EXTENDED terms; the first plain term
    // carries BT-20, BT-9 and the mandate reference, further plain terms are EXTENDED terms too.
    var        extended = ExtendedInformation.create();
    XmlElement terms    = null;
    for (var term : settlement.children("ram:SpecifiedTradePaymentTerms")) {
      if (term.find("ram:PartialPaymentAmount") != null || term.find("ram:ApplicableTradePaymentPenaltyTerms") != null || term.find("ram:ApplicableTradePaymentDiscountTerms") != null || term.find("ram:PayeeTradeParty") != null) {
        extended.addPaymentTerm(new PaymentTerm(term.text("ram:Description"),
                                                term.basicDate("ram:DueDateDateTime/udt:DateTimeString"),
                                                term.decimal("ram:PartialPaymentAmount"),
                                                paymentTermsAdjustment(term.find("ram:ApplicableTradePaymentPenaltyTerms"),
                                                                       "ram:ActualPenaltyAmount"),
                                                paymentTermsAdjustment(term.find("ram:ApplicableTradePaymentDiscountTerms"),
                                                                       "ram:ActualDiscountAmount"),
                                                payee(term.find("ram:PayeeTradeParty"))));
      } else if (terms == null) {
        terms = term;
      } else if (term.text("ram:Description") != null || term.find("ram:DueDateDateTime") != null) {
        // A repeated mandate reference (as Mustang writes it in every term) belongs to the first term.
        extended.addPaymentTerm(new PaymentTerm(term.text("ram:Description"),
                                                term.basicDate("ram:DueDateDateTime/udt:DateTimeString"),
                                                null,
                                                null,
                                                null));
      }
    }
    String mandate = null;
    if (terms != null) {
      var parsed = readContext.paymentTerms(terms.text("ram:Description"));
      builder.paymentTerms(parsed.text())
             .cashDiscounts(parsed.discounts())
             .dueDate(terms.basicDate("ram:DueDateDateTime/udt:DateTimeString"));
      mandate = terms.text("ram:DirectDebitMandateID");
    }
    var means = settlement.children("ram:SpecifiedTradeSettlementPaymentMeans");
    if (!means.isEmpty()) {
      builder.paymentInstruction(paymentInstruction(means,
                                                    settlement,
                                                    mandate,
                                                    readContext));
    }
    for (var preceding : settlement.children("ram:InvoiceReferencedDocument")) {
      builder.addPrecedingInvoiceReference(new PrecedingInvoiceReference(preceding.text("ram:IssuerAssignedID"),
                                                                         preceding.basicDate("ram:FormattedIssueDateTime/qdt:DateTimeString")));
    }

    var items = transaction.children("ram:IncludedSupplyChainTradeLineItem");
    for (var item : items) {
      builder.addLine(invoiceLine(item,
                                  categories,
                                  readContext));
    }
    if (items.isEmpty()) {
      // Factur-X MINIMUM and BASIC WL carry no lines but state their totals.
      if (summation == null) {
        throw new IllegalArgumentException("CII monetary summation is missing");
      }
      builder.statedTotals(statedTotals(settlement,
                                        summation,
                                        statedTax));
      if (summation.find("ram:TotalPrepaidAmount") == null) {
        // MINIMUM has no paid amount (BT-113); a lower amount due implies one.
        BigDecimal grand = summation.decimal("ram:GrandTotalAmount");
        BigDecimal due   = summation.decimal("ram:DuePayableAmount");
        if (grand != null && due != null && grand.compareTo(due) > 0) {
          builder.paidAmount(grand.subtract(due));
        }
      }
    }
    extended(extended,
             context,
             header,
             agreement,
             delivery,
             settlement,
             categories);
    builder.extended(extended.build());
    return readContext.result(builder.build(),
                              statedAmounts(summation,
                                            statedTax));
  }

  /**
   * Returns the totals of a CII or ZUGFeRD 1.0 monetary summation as they are stated.
   *
   * @param summation the monetary summation, or null
   * @param statedTax the stated total VAT amount in the invoice currency, or null
   * @return the stated totals
   */
  static StatedAmounts statedAmounts(XmlElement summation,
                                     BigDecimal statedTax) {
    if (summation == null) {
      return StatedAmounts.NONE;
    }
    return new StatedAmounts(summation.decimal("ram:LineTotalAmount"),
                             summation.decimal("ram:AllowanceTotalAmount"),
                             summation.decimal("ram:ChargeTotalAmount"),
                             summation.decimal("ram:TaxBasisTotalAmount"),
                             statedTax,
                             summation.decimal("ram:GrandTotalAmount"),
                             summation.decimal("ram:TotalPrepaidAmount"),
                             summation.decimal("ram:RoundingAmount"),
                             summation.decimal("ram:DuePayableAmount"),
                             List.of());
  }
}

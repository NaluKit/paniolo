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
package io.github.nalukit.paniolo.xml.ubl;

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
import io.github.nalukit.paniolo.model.common.ThirdPartyPayment;
import io.github.nalukit.paniolo.model.common.value.Country;
import io.github.nalukit.paniolo.model.common.value.CurrencyCode;
import io.github.nalukit.paniolo.model.common.value.DateTimeQualifier;
import io.github.nalukit.paniolo.model.common.value.DutyTaxFeeCategory;
import io.github.nalukit.paniolo.model.common.value.InvoiceTypeCode;
import io.github.nalukit.paniolo.model.common.value.PaymentMeansCode;
import io.github.nalukit.paniolo.model.common.value.UnitCode;
import io.github.nalukit.paniolo.xml.InvoiceXmlReader;
import io.github.nalukit.paniolo.xml.ReadMode;
import io.github.nalukit.paniolo.xml.ReadResult;
import io.github.nalukit.paniolo.xml.StatedAmounts;
import io.github.nalukit.paniolo.xml.util.InvoiceConsistency;
import io.github.nalukit.paniolo.xml.util.ReadContext;
import io.github.nalukit.paniolo.xml.util.StatedVatAmounts;
import io.github.nalukit.paniolo.xml.util.XmlElement;
import org.w3c.dom.Document;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Reads UBL 2.1 Invoice and CreditNote documents into the syntax-independent invoice model,
 * including the supported XRechnung extension fields for UBL Invoice.
 *
 * <p>The inherited convenience methods use strict reading by default. Explicit lenient reading
 * returns the accepted amount deviations with the model; it does not preserve the original XML.
 * Reading performs model and amount checks, not XSD or Schematron validation. Stateless and
 * thread-safe; input DOM documents must not be modified concurrently.
 *
 * @see io.github.nalukit.paniolo.xml.InvoiceXmlReader
 * @see io.github.nalukit.paniolo.xml.ReadMode
 */
public final class UblInvoiceReader
    implements InvoiceXmlReader {
  private static final String              UBL                  = "urn:oasis:names:specification:ubl:schema:xsd:";
  private static final Map<String, String> NAMESPACES           = Map.of("cbc",
                                                                         UBL + "CommonBasicComponents-2",
                                                                         "cac",
                                                                         UBL + "CommonAggregateComponents-2");
  private static final Pattern             NOTE_SUBJECT         = Pattern.compile("#([A-Z0-9]{1,3})#(.*)",
                                                                                  Pattern.DOTALL);
  private static final Pattern             CREDIT_NOTE_DUE_DATE = Pattern.compile("(.*?)\\s*Due date: (\\d{4}-\\d{2}-\\d{2})",
                                                                                  Pattern.DOTALL);

  /**
   * Creates a reader; it has no state.
   */
  public UblInvoiceReader() {
  }

  private static InvoiceNote note(String text) {
    var matcher = NOTE_SUBJECT.matcher(text == null ?
                                       "" :
                                       text);
    if (matcher.matches()) {
      return new InvoiceNote(matcher.group(1),
                             matcher.group(2));
    }
    return InvoiceNote.of(text);
  }

  private static References references(XmlElement root,
                                       Invoice.Builder builder,
                                       boolean credit) {
    var references = References.create()
                               .contractReference(root.text("cac:ContractDocumentReference/cbc:ID"))
                               .receivingAdviceReference(root.text("cac:ReceiptDocumentReference/cbc:ID"))
                               .despatchAdviceReference(root.text("cac:DespatchDocumentReference/cbc:ID"))
                               .tenderOrLotReference(root.text("cac:OriginatorDocumentReference/cbc:ID"))
                               .buyerAccountingReference(root.text("cbc:AccountingCost"))
                               .projectReference(root.text("cac:ProjectReference/cbc:ID"));
    String order      = root.text("cac:OrderReference/cbc:ID");
    String salesOrder = root.text("cac:OrderReference/cbc:SalesOrderID");
    // "NA" is the placeholder written when only a sales order reference exists.
    references.purchaseOrderReference("NA".equals(order) && salesOrder != null ?
                                      null :
                                      order)
              .salesOrderReference(salesOrder);
    for (var document : root.children("cac:AdditionalDocumentReference")) {
      var    id   = document.find("cbc:ID");
      String type = document.text("cbc:DocumentTypeCode");
      if ("130".equals(type)) {
        references.invoicedObjectId(id.text())
                  .invoicedObjectSchemeId(id.attribute("schemeID"));
      } else if ("50".equals(type) && credit) {
        references.projectReference(id.text());
      } else {
        var embedded = document.find("cac:Attachment/cbc:EmbeddedDocumentBinaryObject");
        builder.addSupportingDocument(new AdditionalSupportingDocument(id.text(),
                                                                       document.text("cbc:DocumentDescription"),
                                                                       document.text("cac:Attachment/cac:ExternalReference/cbc:URI"),
                                                                       // An empty binary object carries no attachment.
                                                                       embedded == null || embedded.text() == null ?
                                                                       null :
                                                                       new Attachment(embedded.base64(),
                                                                                      embedded.attribute("mimeCode"),
                                                                                      embedded.attribute("filename"))));
      }
    }
    var result = references.build();
    return result.equals(References.create()
                                   .build()) ?
           null :
           result;
  }

  private static void paymentTerms(Invoice.Builder builder,
                                   CashDiscount.Terms parsed,
                                   boolean credit) {
    builder.cashDiscounts(parsed.discounts());
    String terms = parsed.text();
    // The writer expresses a credit note due date as a suffix of the payment terms.
    var matcher = terms == null || !credit ?
                  null :
                  CREDIT_NOTE_DUE_DATE.matcher(terms);
    if (matcher != null && matcher.matches()) {
      builder.dueDate(LocalDate.parse(matcher.group(2)));
      terms = matcher.group(1)
                     .isBlank() ?
              null :
              matcher.group(1);
    }
    builder.paymentTerms(terms);
  }

  private static PaymentInstruction paymentInstruction(java.util.List<XmlElement> means,
                                                       String creditorId,
                                                       ReadContext context) {
    var first = means.get(0);
    var code  = first.find("cbc:PaymentMeansCode");
    var builder = PaymentInstruction.create()
                                    .meansCode(XmlElement.code(code == null ?
                                                               null :
                                                               code.text(),
                                                               PaymentMeansCode::of,
                                                               "payment means code"))
                                    .meansText(code == null ?
                                               null :
                                               code.attribute("name"))
                                    .remittanceInformation(first.text("cbc:PaymentID"));
    for (int index = 0; index < means.size(); index++) {
      var mean    = means.get(index);
      var account = mean.find("cac:PayeeFinancialAccount");
      // Further blocks add credit transfer accounts (BG-17) of the same payment means only.
      var other = mean.find("cbc:PaymentMeansCode");
      if (index > 0 && (!java.util.Objects.equals(other == null ?
                                                  null :
                                                  other.text(),
                                                  code == null ?
                                                  null :
                                                  code.text()) || other != null && other.attribute("name") != null && !other.attribute("name")
                                                                                                                           .equals(code == null ?
                                                                                                                                   null :
                                                                                                                                   code.attribute("name")) || mean.find("cac:CardAccount") != null ||
                        mean.find("cac:PaymentMandate") != null)) {
        context.otherPaymentMeans("Payment means " + (index + 1) + " (code " + (other == null ?
                                                                               null :
                                                                               other.text()) + ", account " + (account == null ?
                                                                                                               null :
                                                                                                               account.text("cbc:ID")) + ", BIC " + (account == null ?
                                                                                                                                                     null :
                                                                                                                                                     account.text("cac:FinancialInstitutionBranch/cbc:ID")) + ", card " +
                                  mean.text("cac:CardAccount/cbc:PrimaryAccountNumberID") + ", mandate " + mean.text("cac:PaymentMandate/cbc:ID") + ") differs from payment means 1 (code " + (code == null ?
                                                                                                                                                                                                null :
                                                                                                                                                                                                code.text()) + ")");
        continue;
      }
      if (account != null) {
        builder.addCreditTransfer(new CreditTransfer(account.text("cbc:ID"),
                                                     account.text("cbc:Name"),
                                                     account.text("cac:FinancialInstitutionBranch/cbc:ID")));
      }
    }
    var card = first.find("cac:CardAccount");
    if (card != null) {
      builder.paymentCard(new PaymentCardInformation(card.text("cbc:PrimaryAccountNumberID"),
                                                     card.text("cbc:HolderName")));
    }
    var mandate = first.find("cac:PaymentMandate");
    if (mandate != null) {
      builder.directDebit(new DirectDebit(mandate.text("cbc:ID"),
                                          creditorId,
                                          mandate.text("cac:PayerFinancialAccount/cbc:ID")));
    }
    return builder.build();
  }

  private static String creditorId(XmlElement party) {
    if (party == null) {
      return null;
    }
    return party.children("cac:PartyIdentification")
                .stream()
                .map(id -> id.findWithText("cbc:ID"))
                .filter(id -> id != null && "SEPA".equals(id.attribute("schemeID")))
                .map(XmlElement::text)
                .findFirst()
                .orElse(null);
  }

  /**
   * Returns the party identifier (BT-29 / BT-46 / BT-60), the first one that is no SEPA creditor
   * identifier, or null.
   */
  private static XmlElement identifier(XmlElement party) {
    var identifiers = identifiers(party);
    return identifiers.isEmpty() ?
           null :
           identifiers.getFirst();
  }

  /**
   * Returns every party identifier that is no SEPA creditor identifier, in document order; empty
   * identifier elements count as absent.
   */
  private static List<XmlElement> identifiers(XmlElement party) {
    return party.children("cac:PartyIdentification")
                .stream()
                .map(id -> id.findWithText("cbc:ID"))
                .filter(id -> id != null && !"SEPA".equals(id.attribute("schemeID")))
                .toList();
  }

  private static Party party(XmlElement party) {
    if (party == null) {
      return null;
    }
    var    endpoint = party.findWithText("cbc:EndpointID");
    String vatId    = null;
    String taxId    = null;
    for (var scheme : party.children("cac:PartyTaxScheme")) {
      String type = scheme.text("cac:TaxScheme/cbc:ID");
      if ("VAT".equals(type)) {
        vatId = scheme.text("cbc:CompanyID");
      } else if ("FC".equals(type)) {
        taxId = scheme.text("cbc:CompanyID");
      }
    }
    var contact = party.find("cac:Contact");
    // BT-27 / BT-44 is the registration name, BT-28 / BT-45 the party name.
    String legalName = party.text("cac:PartyLegalEntity/cbc:RegistrationName");
    String partyName = party.text("cac:PartyName/cbc:Name");
    String name = legalName == null ?
                  partyName :
                  legalName;
    var identifiers = identifiers(party);
    var identifier  = identifiers.isEmpty() ?
                      null :
                      identifiers.getFirst();
    var legalId     = party.findWithText("cac:PartyLegalEntity/cbc:CompanyID");
    var builder     = Party.create();
    // Further identifiers, e.g. a second seller identifier (BT-29 is repeatable).
    for (var further : identifiers.subList(Math.min(1,
                                                    identifiers.size()),
                                           identifiers.size())) {
      builder.addAdditionalIdentifier(new PartyIdentifier(further.text(),
                                                          further.attribute("schemeID")));
    }
    return builder
                .name(name)
                .tradingName(partyName == null || partyName.equals(name) ?
                             null :
                             partyName)
                .address(address(party.find("cac:PostalAddress")))
                .vatId(vatId)
                .taxId(taxId)
                .electronicAddress(endpoint == null ?
                                   null :
                                   new ElectronicAddress(endpoint.text(),
                                                         endpoint.attribute("schemeID")))
                .contact(contact == null ?
                         null :
                         new Contact(contact.text("cbc:Name"),
                                     contact.text("cbc:Telephone"),
                                     contact.text("cbc:ElectronicMail")))
                .identifier(identifier == null ?
                            null :
                            identifier.text(),
                            identifier == null ?
                            null :
                            identifier.attribute("schemeID"))
                .legalRegistrationId(legalId == null ?
                                     null :
                                     legalId.text(),
                                     legalId == null ?
                                     null :
                                     legalId.attribute("schemeID"))
                .additionalLegalInformation(party.text("cac:PartyLegalEntity/cbc:CompanyLegalForm"))
                .build();
  }

  private static Payee payee(XmlElement payee) {
    if (payee == null) {
      return null;
    }
    var identifier = identifier(payee);
    var legalId    = payee.findWithText("cac:PartyLegalEntity/cbc:CompanyID");
    return Payee.create()
                .name(payee.text("cac:PartyName/cbc:Name"))
                .identifier(identifier == null ?
                            null :
                            identifier.text(),
                            identifier == null ?
                            null :
                            identifier.attribute("schemeID"))
                .legalRegistrationId(legalId == null ?
                                     null :
                                     legalId.text(),
                                     legalId == null ?
                                     null :
                                     legalId.attribute("schemeID"))
                .build();
  }

  private static SellerTaxRepresentativeParty taxRepresentative(XmlElement party) {
    return party == null ?
           null :
           new SellerTaxRepresentativeParty(party.text("cac:PartyName/cbc:Name"),
                                            party.text("cac:PartyTaxScheme/cbc:CompanyID"),
                                            address(party.find("cac:PostalAddress")));
  }

  private static DeliveryInformation delivery(XmlElement delivery) {
    if (delivery == null) {
      return null;
    }
    var location = delivery.find("cac:DeliveryLocation/cac:Address");
    var locationId = delivery.findWithText("cac:DeliveryLocation/cbc:ID");
    return new DeliveryInformation(delivery.text("cac:DeliveryParty/cac:PartyName/cbc:Name"),
                                   locationId == null ?
                                   null :
                                   locationId.text(),
                                   delivery.isoDate("cbc:ActualDeliveryDate"),
                                   location == null ?
                                   null :
                                   address(location),
                                   locationId == null ?
                                   null :
                                   locationId.attribute("schemeID"));
  }

  static Address address(XmlElement address) {
    if (address == null) {
      return null;
    }
    return new Address(address.text("cbc:StreetName"),
                       address.text("cbc:AdditionalStreetName"),
                       address.text("cac:AddressLine/cbc:Line"),
                       address.text("cbc:CityName"),
                       address.text("cbc:PostalZone"),
                       address.text("cbc:CountrySubentity"),
                       XmlElement.code(address.text("cac:Country/cbc:IdentificationCode"),
                                       Country::of,
                                       "country"));
  }

  /**
   * Whether a tax category belongs to VAT; a category without tax scheme counts as VAT.
   */
  private static boolean isVat(XmlElement category) {
    String scheme = category == null ?
                    null :
                    category.text("cac:TaxScheme/cbc:ID");
    return scheme == null || "VAT".equals(scheme.strip());
  }

  /**
   * Returns the first VAT category of a line item and reports categories of other tax schemes,
   * which are not read.
   */
  private static XmlElement lineTax(XmlElement line,
                                    ReadContext context) {
    XmlElement vat    = null;
    String     lineId = line.text("cbc:ID");
    var        taxes  = line.all("cac:Item/cac:ClassifiedTaxCategory");
    for (int index = 0; index < taxes.size(); index++) {
      var category = taxes.get(index);
      if (!isVat(category)) {
        context.otherTax(category.text("cac:TaxScheme/cbc:ID")
                                 .strip(),
                         lineId,
                         "line " + lineId + " tax " + (index + 1),
                         category.text("cbc:ID"),
                         category.decimal("cbc:Percent"),
                         null,
                         null);
      } else if (vat == null) {
        vat = category;
      }
    }
    return vat;
  }

  private static TaxCategory tax(XmlElement category) {
    if (category == null) {
      return null;
    }
    var code = XmlElement.code(category.text("cbc:ID"),
                               DutyTaxFeeCategory::of,
                               "VAT category");
    // Outside-scope breakdowns state a zero rate for XRechnung; the model keeps no rate.
    BigDecimal rate = code == DutyTaxFeeCategory.O ?
                      null :
                      category.decimal("cbc:Percent");
    return new TaxCategory(code,
                           rate,
                           category.text("cbc:TaxExemptionReason"),
                           category.text("cbc:TaxExemptionReasonCode"));
  }

  /**
   * Reads an invoice or credit note line (BG-25) with its price, allowances, charges and item details.
   */
  private static InvoiceLine invoiceLine(XmlElement line,
                                         boolean credit,
                                         Map<String, TaxCategory> categories,
                                         ReadContext context) {
    var quantity = line.find(credit ?
                             "cbc:CreditedQuantity" :
                             "cbc:InvoicedQuantity");
    BigDecimal baseQuantity = line.decimal("cac:Price/cbc:BaseQuantity");
    var builder = InvoiceLine.create()
                             .id(line.text("cbc:ID"))
                             .name(line.text("cac:Item/cbc:Name"))
                             .quantity(quantity == null ?
                                       null :
                                       quantity.decimal())
                             .unitCode(quantity == null ?
                                       null :
                                       XmlElement.code(quantity.attribute("unitCode"),
                                                       UnitCode::of,
                                                       "unit code"))
                             .netUnitPrice(line.decimal("cac:Price/cbc:PriceAmount"))
                             .priceBaseQuantity(baseQuantity == null ?
                                                BigDecimal.ONE :
                                                baseQuantity)
                             .tax(InvoiceConsistency.complete(tax(lineTax(line,
                                                                          context)),
                                                              categories))
                             .note(line.text("cbc:Note"))
                             .buyerAccountingReference(line.text("cbc:AccountingCost"))
                             .orderLineReference(line.text("cac:OrderLineReference/cbc:LineID"))
                             .description(line.text("cac:Item/cbc:Description"))
                             .buyerItemId(line.text("cac:Item/cac:BuyersItemIdentification/cbc:ID"))
                             .sellerItemId(line.text("cac:Item/cac:SellersItemIdentification/cbc:ID"))
                             .originCountry(XmlElement.code(line.text("cac:Item/cac:OriginCountry/cbc:IdentificationCode"),
                                                            Country::of,
                                                            "country"));
    var period = line.find("cac:InvoicePeriod");
    if (period != null) {
      builder.period(new InvoicePeriod(period.isoDate("cbc:StartDate"),
                                       period.isoDate("cbc:EndDate")));
    }
    var object = line.findWithText("cac:DocumentReference/cbc:ID");
    if (object != null) {
      builder.objectId(object.text(),
                       object.attribute("schemeID"));
    }
    var standard = line.findWithText("cac:Item/cac:StandardItemIdentification/cbc:ID");
    if (standard != null) {
      builder.standardItemId(standard.text(),
                             standard.attribute("schemeID"));
    }
    for (var classification : line.all("cac:Item/cac:CommodityClassification/cbc:ItemClassificationCode")) {
      builder.addClassification(new ItemClassification(classification.text(),
                                                       classification.attribute("listID"),
                                                       classification.attribute("listVersionID")));
    }
    for (var attribute : line.all("cac:Item/cac:AdditionalItemProperty")) {
      builder.addAttribute(attribute.text("cbc:Name"),
                           attribute.text("cbc:Value"));
    }
    for (var adjustment : line.children("cac:AllowanceCharge")) {
      boolean charge = Boolean.parseBoolean(adjustment.text("cbc:ChargeIndicator"));
      builder.addAdjustment(new LineAdjustment(charge,
                                               adjustment.decimal("cbc:Amount"),
                                               adjustment.decimal("cbc:BaseAmount"),
                                               context.percentage(charge,
                                                                  adjustment.decimal("cbc:MultiplierFactorNumeric"),
                                                                  adjustment.decimal("cbc:BaseAmount"),
                                                                  line.text("cbc:ID")),
                                               adjustment.text("cbc:AllowanceChargeReason"),
                                               context.reasonCode(charge,
                                                                  adjustment.text("cbc:AllowanceChargeReasonCode"),
                                                                  adjustment.text("cbc:AllowanceChargeReason"),
                                                                  line.text("cbc:ID"))));
    }
    // UBL carries the gross price (BT-148) as base amount of the price discount (BT-147).
    var discount = line.find("cac:Price/cac:AllowanceCharge");
    if (discount != null) {
      builder.grossPrice(discount.decimal("cbc:BaseAmount"))
             .priceDiscount(discount.decimal("cbc:Amount"));
    }
    // XRechnung extension: sub invoice lines (BG-DEX-01) with the structure of an invoice line.
    for (var subLine : line.children("cac:SubInvoiceLine")) {
      builder.addSubLine(invoiceLine(subLine,
                                     credit,
                                     categories,
                                     context));
    }
    return context.netAmount(builder.build(),
                             line.decimal("cbc:LineExtensionAmount"));
  }

  @Override
  public ReadResult read(Document document,
                         ReadMode mode) {
    Objects.requireNonNull(document,
                           "document");
    var readContext = new ReadContext(mode);
    var root = XmlElement.of(document.getDocumentElement(),
                             NAMESPACES);
    boolean credit = "CreditNote".equals(root.localName());
    if (!credit && !"Invoice".equals(root.localName()) || !(UBL + root.localName() + "-2").equals(root.namespace())) {
      throw new IllegalArgumentException("Not a UBL Invoice or CreditNote: {" + root.namespace() + "}" + root.localName());
    }
    String currency = root.text("cbc:DocumentCurrencyCode");
    var builder = Invoice.create()
                         .number(root.text("cbc:ID"))
                         .issueDate(root.isoDate("cbc:IssueDate"))
                         .typeCode(XmlElement.code(root.text(credit ?
                                                             "cbc:CreditNoteTypeCode" :
                                                             "cbc:InvoiceTypeCode"),
                                                   InvoiceTypeCode::of,
                                                   "invoice type code"))
                         .currencyCode(XmlElement.code(currency,
                                                       CurrencyCode::of,
                                                       "currency"))
                         .processControl(new ProcessControl(root.text("cbc:ProfileID"),
                                                            root.text("cbc:CustomizationID")))
                         .buyerReference(root.text("cbc:BuyerReference"))
                         .dueDate(root.isoDate("cbc:DueDate"))
                         .taxPointDate(root.isoDate("cbc:TaxPointDate"));
    for (var note : root.children("cbc:Note")) {
      builder.addNote(note(note.text()));
    }
    String taxCurrency = root.text("cbc:TaxCurrencyCode");
    if (taxCurrency != null) {
      builder.taxCurrencyCode(XmlElement.code(taxCurrency,
                                              CurrencyCode::of,
                                              "currency"));
      root.children("cac:TaxTotal")
          .stream()
          .map(total -> total.find("cbc:TaxAmount"))
          .filter(amount -> amount != null && taxCurrency.equals(amount.attribute("currencyID")))
          .findFirst()
          .ifPresent(amount -> builder.taxAmountInTaxCurrency(amount.decimal()));
    }
    var period = root.find("cac:InvoicePeriod");
    if (period != null) {
      if (period.text("cbc:StartDate") != null || period.text("cbc:EndDate") != null) {
        builder.invoicePeriod(new InvoicePeriod(period.isoDate("cbc:StartDate"),
                                                period.isoDate("cbc:EndDate")));
      }
      builder.taxPointDateCode(XmlElement.code(period.text("cbc:DescriptionCode"),
                                               DateTimeQualifier::of,
                                               "tax point date code"));
    }
    for (var preceding : root.all("cac:BillingReference/cac:InvoiceDocumentReference")) {
      builder.addPrecedingInvoiceReference(new PrecedingInvoiceReference(preceding.text("cbc:ID"),
                                                                         preceding.isoDate("cbc:IssueDate")));
    }
    builder.references(references(root,
                                  builder,
                                  credit));

    var    payment          = root.children("cac:PaymentMeans");
    String sellerCreditorId = creditorId(root.find("cac:AccountingSupplierParty/cac:Party"));
    String payeeCreditorId  = creditorId(root.find("cac:PayeeParty"));
    builder.seller(party(root.find("cac:AccountingSupplierParty/cac:Party")))
           .buyer(party(root.find("cac:AccountingCustomerParty/cac:Party")))
           .payee(payee(root.find("cac:PayeeParty")))
           .taxRepresentative(taxRepresentative(root.find("cac:TaxRepresentativeParty")))
           .delivery(delivery(root.find("cac:Delivery")))
           .paymentInstruction(payment.isEmpty() ?
                               null :
                               paymentInstruction(payment,
                                                  payeeCreditorId != null ?
                                                  payeeCreditorId :
                                                  sellerCreditorId,
                                                  readContext));
    paymentTerms(builder,
                 readContext.paymentTerms(root.text("cac:PaymentTerms/cbc:Note")),
                 credit);

    var breakdowns = new ArrayList<TaxCategory>();
    var statedVat  = new StatedVatAmounts();
    var taxTotal = root.children("cac:TaxTotal")
                       .stream()
                       .filter(total -> total.find("cbc:TaxAmount") != null &&
                                        Objects.equals(currency,
                                                       total.find("cbc:TaxAmount")
                                                            .attribute("currencyID")))
                       .findFirst()
                       .orElse(null);
    if (taxTotal != null) {
      var subtotals = taxTotal.children("cac:TaxSubtotal");
      for (int index = 0; index < subtotals.size(); index++) {
        var subtotal = subtotals.get(index);
        var category = subtotal.find("cac:TaxCategory");
        if (category == null) {
          continue;
        }
        if (isVat(category)) {
          breakdowns.add(tax(category));
          statedVat.add(tax(category),
                        subtotal.decimal("cbc:TaxAmount"));
        } else {
          readContext.otherTax(category.text("cac:TaxScheme/cbc:ID"),
                               null,
                               "header tax " + (index + 1),
                               category.text("cbc:ID"),
                               category.decimal("cbc:Percent"),
                               subtotal.decimal("cbc:TaxableAmount"),
                               subtotal.decimal("cbc:TaxAmount"));
        }
      }
    }
    var categories = InvoiceConsistency.byCategoryAndRate(breakdowns);
    for (var adjustment : root.children("cac:AllowanceCharge")) {
      boolean charge = Boolean.parseBoolean(adjustment.text("cbc:ChargeIndicator"));
      builder.addAdjustment(new DocumentAdjustment(charge,
                                                   adjustment.decimal("cbc:Amount"),
                                                   adjustment.decimal("cbc:BaseAmount"),
                                                   readContext.percentage(charge,
                                                                          adjustment.decimal("cbc:MultiplierFactorNumeric"),
                                                                          adjustment.decimal("cbc:BaseAmount"),
                                                                          null),
                                                   adjustment.text("cbc:AllowanceChargeReason"),
                                                   readContext.reasonCode(charge,
                                                                          adjustment.text("cbc:AllowanceChargeReasonCode"),
                                                                          adjustment.text("cbc:AllowanceChargeReason"),
                                                                          null),
                                                   InvoiceConsistency.complete(tax(adjustment.find("cac:TaxCategory")),
                                                                               categories)));
    }
    // XRechnung extension: third party payments (BG-DEX-09).
    for (var thirdParty : root.children("cac:PrepaidPayment")) {
      builder.addThirdPartyPayment(new ThirdPartyPayment(thirdParty.text("cbc:ID"),
                                                         thirdParty.decimal("cbc:PaidAmount"),
                                                         thirdParty.text("cbc:InstructionID")));
    }
    for (var line : root.children(credit ?
                                  "cac:CreditNoteLine" :
                                  "cac:InvoiceLine")) {
      builder.addLine(invoiceLine(line,
                                  credit,
                                  categories,
                                  readContext));
    }
    var monetary = root.find("cac:LegalMonetaryTotal");
    if (monetary != null) {
      BigDecimal prepaid = monetary.decimal("cbc:PrepaidAmount");
      builder.paidAmount(prepaid == null ?
                         BigDecimal.ZERO :
                         prepaid)
             .roundingAmount(monetary.decimal("cbc:PayableRoundingAmount"));
    }
    BigDecimal statedTax = taxTotal == null ?
                           null :
                           taxTotal.decimal("cbc:TaxAmount");
    return readContext.result(statedVat.build(builder),
                              monetary == null ?
                              new StatedAmounts(null,
                                                null,
                                                null,
                                                null,
                                                statedTax,
                                                null,
                                                null,
                                                null,
                                                null,
                                                List.of()) :
                              new StatedAmounts(monetary.decimal("cbc:LineExtensionAmount"),
                                                monetary.decimal("cbc:AllowanceTotalAmount"),
                                                monetary.decimal("cbc:ChargeTotalAmount"),
                                                monetary.decimal("cbc:TaxExclusiveAmount"),
                                                statedTax,
                                                monetary.decimal("cbc:TaxInclusiveAmount"),
                                                monetary.decimal("cbc:PrepaidAmount"),
                                                monetary.decimal("cbc:PayableRoundingAmount"),
                                                monetary.decimal("cbc:PayableAmount"),
                                                List.of()));
  }
}

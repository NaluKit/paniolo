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
import io.github.nalukit.paniolo.model.common.Address;
import io.github.nalukit.paniolo.model.common.CashDiscount;
import io.github.nalukit.paniolo.model.common.CreditTransfer;
import io.github.nalukit.paniolo.model.common.InvoiceLine;
import io.github.nalukit.paniolo.model.common.InvoiceTotals;
import io.github.nalukit.paniolo.model.common.Party;
import io.github.nalukit.paniolo.model.common.PaymentInstruction;
import io.github.nalukit.paniolo.model.common.References;
import io.github.nalukit.paniolo.model.common.TaxCategory;
import io.github.nalukit.paniolo.xml.InvoiceXmlWriter;
import io.github.nalukit.paniolo.xml.util.SpecificationGenerations;
import io.github.nalukit.paniolo.xml.util.XmlBuilder;

import java.math.BigDecimal;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;

/**
 * Writes the invoice model as UBL 2.1 Invoice or CreditNote XML. The invoice type selects the
 * root element. Supported XRechnung extension fields are written for UBL Invoice only;
 * Factur-X EXTENDED content has no mapping in this adapter and is rejected.
 *
 * <p>Amounts are derived from the model. Writing does not run XSD or Schematron validation;
 * validate the resulting document separately before transmission. Stateless and thread-safe.
 *
 * @see io.github.nalukit.paniolo.xml.InvoiceXmlWriter
 */
public final class UblInvoiceWriter
    implements InvoiceXmlWriter {
  private static final String UBL = "urn:oasis:names:specification:ubl:schema:xsd:";

  /**
   * Creates a writer; it has no state.
   */
  public UblInvoiceWriter() {
  }

  private static void additionalDocuments(XmlBuilder xml,
                                          Invoice invoice,
                                          References references,
                                          boolean credit) {
    if (references.invoicedObjectId() != null) {
      xml.start("cac:AdditionalDocumentReference")
         .element("cbc:ID",
                  references.invoicedObjectId(),
                  "schemeID",
                  references.invoicedObjectSchemeId())
         .element("cbc:DocumentTypeCode",
                  "130")
         .end();
    }
    for (var document : invoice.supportingDocuments()) {
      xml.start("cac:AdditionalDocumentReference")
         .element("cbc:ID",
                  document.reference())
         .element("cbc:DocumentDescription",
                  document.description());
      var attachment = document.attachment();
      if (attachment != null || document.externalLocation() != null) {
        xml.start("cac:Attachment");
        if (attachment != null) {
          xml.element("cbc:EmbeddedDocumentBinaryObject",
                      Base64.getEncoder()
                            .encodeToString(attachment.content()),
                      "mimeCode",
                      attachment.mimeCode(),
                      "filename",
                      attachment.filename());
        }
        if (document.externalLocation() != null) {
          xml.start("cac:ExternalReference")
             .element("cbc:URI",
                      document.externalLocation())
             .end();
        }
        xml.end();
      }
      xml.end();
    }
    // The CreditNote schema has no ProjectReference; EN 16931 maps BT-11 to document type 50 there.
    if (credit && references.projectReference() != null) {
      xml.start("cac:AdditionalDocumentReference")
         .element("cbc:ID",
                  references.projectReference())
         .element("cbc:DocumentTypeCode",
                  "50")
         .end();
    }
  }

  private static void delivery(XmlBuilder xml,
                               Invoice invoice) {
    var delivery = invoice.delivery();
    if (delivery == null) {
      return;
    }
    xml.start("cac:Delivery")
       .element("cbc:ActualDeliveryDate",
                delivery.actualDeliveryDate());
    if (delivery.locationId() != null || delivery.address() != null) {
      xml.start("cac:DeliveryLocation")
         .element("cbc:ID",
                  delivery.locationId(),
                  "schemeID",
                  delivery.locationSchemeId());
      if (delivery.address() != null) {
        address(xml,
                "cac:Address",
                delivery.address());
      }
      xml.end();
    }
    if (delivery.partyName() != null) {
      xml.start("cac:DeliveryParty")
         .start("cac:PartyName")
         .element("cbc:Name",
                  delivery.partyName())
         .end()
         .end();
    }
    xml.end();
  }

  private static void paymentMeans(XmlBuilder xml,
                                   PaymentInstruction payment) {
    // One PaymentMeans per credit transfer account; card and mandate details use a single element.
    // The payment means text may occur only once (UBL-SR-46).
    var accounts = payment.creditTransfers()
                          .isEmpty() ?
                   java.util.Collections.<CreditTransfer>singletonList(null) :
                   payment.creditTransfers();
    boolean first = true;
    for (var account : accounts) {
      xml.start("cac:PaymentMeans")
         .element("cbc:PaymentMeansCode",
                  payment.meansCode()
                         .getCode(),
                  "name",
                  first ?
                  payment.meansText() :
                  null)
         .element("cbc:PaymentID",
                  payment.remittanceInformation());
      first = false;
      var card = payment.paymentCard();
      if (card != null) {
        // NetworkID is mandatory in UBL but has no EN 16931 counterpart.
        xml.start("cac:CardAccount")
           .element("cbc:PrimaryAccountNumberID",
                    card.primaryAccountNumber())
           .element("cbc:NetworkID",
                    "NA")
           .element("cbc:HolderName",
                    card.holderName())
           .end();
      }
      if (account != null) {
        xml.start("cac:PayeeFinancialAccount")
           .element("cbc:ID",
                    account.iban())
           .element("cbc:Name",
                    account.accountName());
        if (account.bic() != null) {
          xml.start("cac:FinancialInstitutionBranch")
             .element("cbc:ID",
                      account.bic())
             .end();
        }
        xml.end();
      }
      var directDebit = payment.directDebit();
      if (directDebit != null) {
        xml.start("cac:PaymentMandate")
           .element("cbc:ID",
                    directDebit.mandateReference())
           .start("cac:PayerFinancialAccount")
           .element("cbc:ID",
                    directDebit.debitedAccountId())
           .end()
           .end();
      }
      xml.end();
    }
  }

  private static void documentReference(XmlBuilder xml,
                                        String name,
                                        String id) {
    if (id != null) {
      xml.start(name)
         .element("cbc:ID",
                  id)
         .end();
    }
  }

  /**
   * Writes an invoice or credit note line (BG-25) in the element order of the UBL schema.
   */
  private static void line(XmlBuilder xml,
                           InvoiceLine line,
                           boolean credit,
                           String currency) {
    line(xml,
         line,
         credit,
         currency,
         credit ?
         "cac:CreditNoteLine" :
         "cac:InvoiceLine");
  }

  /**
   * Writes an invoice line, or a sub invoice line of the XRechnung extension with the same
   * structure, followed by its own sub-lines.
   */
  private static void line(XmlBuilder xml,
                           InvoiceLine line,
                           boolean credit,
                           String currency,
                           String element) {
    // An EXTENDED subtotal line may state no quantity, which UBL requires (BT-129).
    if (line.quantity() == null) {
      throw new IllegalArgumentException("Line " + line.id() + ": UBL requires the invoiced quantity (BT-129)");
    }
    String unit = line.unitCode()
                      .getCode();
    xml.start(element)
       .element("cbc:ID",
                line.id())
       .element("cbc:Note",
                line.note())
       .element(credit ?
                "cbc:CreditedQuantity" :
                "cbc:InvoicedQuantity",
                line.quantity(),
                "unitCode",
                unit);
    money(xml,
          "cbc:LineExtensionAmount",
          line.netAmount(),
          currency);
    xml.element("cbc:AccountingCost",
                line.buyerAccountingReference());
    if (line.period() != null) {
      xml.start("cac:InvoicePeriod")
         .element("cbc:StartDate",
                  line.period()
                      .startDate())
         .element("cbc:EndDate",
                  line.period()
                      .endDate())
         .end();
    }
    if (line.orderLineReference() != null) {
      xml.start("cac:OrderLineReference")
         .element("cbc:LineID",
                  line.orderLineReference())
         .end();
    }
    if (line.objectId() != null) {
      xml.start("cac:DocumentReference")
         .element("cbc:ID",
                  line.objectId(),
                  "schemeID",
                  line.objectSchemeId())
         .element("cbc:DocumentTypeCode",
                  "130")
         .end();
    }
    for (var adjustment : line.adjustments()) {
      xml.start("cac:AllowanceCharge")
         .element("cbc:ChargeIndicator",
                  adjustment.charge())
         .element("cbc:AllowanceChargeReasonCode",
                  adjustment.reasonCode())
         .element("cbc:AllowanceChargeReason",
                  adjustment.reason())
         .element("cbc:MultiplierFactorNumeric",
                  adjustment.percentage());
      money(xml,
            "cbc:Amount",
            adjustment.amount(),
            currency);
      money(xml,
            "cbc:BaseAmount",
            adjustment.baseAmount(),
            currency);
      xml.end();
    }
    xml.start("cac:Item")
       .element("cbc:Description",
                line.description())
       .element("cbc:Name",
                line.name());
    identification(xml,
                   "cac:BuyersItemIdentification",
                   line.buyerItemId(),
                   null);
    identification(xml,
                   "cac:SellersItemIdentification",
                   line.sellerItemId(),
                   null);
    identification(xml,
                   "cac:StandardItemIdentification",
                   line.standardItemId(),
                   line.standardItemSchemeId());
    if (line.originCountry() != null) {
      xml.start("cac:OriginCountry")
         .element("cbc:IdentificationCode",
                  line.originCountry()
                      .name())
         .end();
    }
    for (var classification : line.classifications()) {
      xml.start("cac:CommodityClassification")
         .element("cbc:ItemClassificationCode",
                  classification.code(),
                  "listID",
                  classification.schemeId(),
                  "listVersionID",
                  classification.schemeVersion())
         .end();
    }
    xml.start("cac:ClassifiedTaxCategory");
    tax(xml,
        line.tax(),
        false);
    xml.end();
    for (var attribute : line.attributes()) {
      xml.start("cac:AdditionalItemProperty")
         .element("cbc:Name",
                  attribute.name())
         .element("cbc:Value",
                  attribute.value())
         .end();
    }
    xml.end();
    // A line with sub-lines of the XRechnung extension may have no price.
    if (line.netUnitPrice() != null) {
      xml.start("cac:Price");
      money(xml,
            "cbc:PriceAmount",
            line.netUnitPrice(),
            currency);
      xml.element("cbc:BaseQuantity",
                  line.priceBaseQuantity(),
                  "unitCode",
                  unit);
      if (line.grossPrice() != null) {
        // UBL expresses the gross price (BT-148) as base amount of the price discount (BT-147).
        xml.start("cac:AllowanceCharge")
           .element("cbc:ChargeIndicator",
                    false);
        money(xml,
              "cbc:Amount",
              line.priceDiscount() == null ?
              BigDecimal.ZERO.setScale(2) :
              line.priceDiscount(),
              currency);
        money(xml,
              "cbc:BaseAmount",
              line.grossPrice(),
              currency);
        xml.end();
      }
      xml.end();
    }
    for (var subLine : line.subLines()) {
      line(xml,
           subLine,
           credit,
           currency,
           "cac:SubInvoiceLine");
    }
    xml.end();
  }

  private static void identification(XmlBuilder xml,
                                     String name,
                                     String id,
                                     String scheme) {
    if (id != null) {
      xml.start(name)
         .element("cbc:ID",
                  id,
                  "schemeID",
                  scheme)
         .end();
    }
  }

  private static void money(XmlBuilder xml,
                            String name,
                            BigDecimal amount,
                            String currency) {
    xml.element(name,
                amount,
                "currencyID",
                currency);
  }

  private static void tax(XmlBuilder xml,
                          TaxCategory tax,
                          boolean exemption) {
    xml.element("cbc:ID",
                tax.code()
                   .getCode())
       .element("cbc:Percent",
                exemption && tax.rate() == null ?
                BigDecimal.ZERO :
                tax.rate());
    if (exemption) {
      xml.element("cbc:TaxExemptionReasonCode",
                  tax.exemptionReasonCode())
         .element("cbc:TaxExemptionReason",
                  tax.exemptionReason());
    }
    xml.start("cac:TaxScheme")
       .element("cbc:ID",
                "VAT")
       .end();
  }

  private static void address(XmlBuilder xml,
                              String name,
                              Address address) {
    xml.start(name)
       .element("cbc:StreetName",
                address.street())
       .element("cbc:AdditionalStreetName",
                address.additionalStreet())
       .element("cbc:CityName",
                address.city())
       .element("cbc:PostalZone",
                address.postalCode())
       .element("cbc:CountrySubentity",
                address.countrySubdivision());
    if (address.additionalLine() != null) {
      xml.start("cac:AddressLine")
         .element("cbc:Line",
                  address.additionalLine())
         .end();
    }
    xml.start("cac:Country")
       .element("cbc:IdentificationCode",
                address.country()
                       .name())
       .end()
       .end();
  }

  private static void party(XmlBuilder xml,
                            String role,
                            Party party,
                            boolean seller,
                            String creditorId) {
    if (!seller && party.additionalLegalInformation() != null) {
      throw new IllegalArgumentException("Buyer additional legal information has no EN 16931 mapping");
    }
    xml.start(role)
       .start("cac:Party");
    if (party.electronicAddress() != null) {
      xml.element("cbc:EndpointID",
                  party.electronicAddress()
                       .value(),
                  "schemeID",
                  party.electronicAddress()
                       .schemeId());
    }
    partyIdentification(xml,
                        party.identifier(),
                        party.identifierSchemeId());
    for (var identifier : party.additionalIdentifiers()) {
      partyIdentification(xml,
                          identifier.id(),
                          identifier.schemeId());
    }
    partyIdentification(xml,
                        creditorId,
                        "SEPA");
    // BT-28 / BT-45 trading name; the legal name (BT-27 / BT-44) is the registration name below.
    if (party.tradingName() != null) {
      xml.start("cac:PartyName")
         .element("cbc:Name",
                  party.tradingName())
         .end();
    }
    address(xml,
            "cac:PostalAddress",
            party.address());
    partyTax(xml,
             party.vatId(),
             "VAT");
    if (seller) {
      partyTax(xml,
               party.taxId(),
               "FC");
    } else if (party.taxId() != null) {
      throw new IllegalArgumentException("Buyer national tax identifier has no EN 16931 mapping");
    }
    xml.start("cac:PartyLegalEntity")
       .element("cbc:RegistrationName",
                party.name())
       .element("cbc:CompanyID",
                party.legalRegistrationId(),
                "schemeID",
                party.legalRegistrationSchemeId())
       .element("cbc:CompanyLegalForm",
                party.additionalLegalInformation())
       .end();
    if (party.contact() != null) {
      xml.start("cac:Contact")
         .element("cbc:Name",
                  party.contact()
                       .name())
         .element("cbc:Telephone",
                  party.contact()
                       .telephone())
         .element("cbc:ElectronicMail",
                  party.contact()
                       .email())
         .end();
    }
    xml.end()
       .end();
  }

  private static void partyIdentification(XmlBuilder xml,
                                          String id,
                                          String scheme) {
    if (id != null) {
      xml.start("cac:PartyIdentification")
         .element("cbc:ID",
                  id,
                  "schemeID",
                  scheme)
         .end();
    }
  }

  private static void partyTax(XmlBuilder xml,
                               String id,
                               String scheme) {
    if (id != null && !id.isBlank()) {
      xml.start("cac:PartyTaxScheme")
         .element("cbc:CompanyID",
                  id)
         .start("cac:TaxScheme")
         .element("cbc:ID",
                  scheme)
         .end()
         .end();
    }
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
    if (invoice.hasStatedTotals()) {
      throw new IllegalArgumentException("UBL requires invoice lines; invoices with stated totals exist only in the Factur-X profiles MINIMUM and BASIC WL, write CII instead");
    }
    // UBL has no department of a contact; CII states it as ram:DepartmentName.
    for (var party : java.util.List.of(invoice.seller(),
                                       invoice.buyer())) {
      if (party.extended() != null && party.extended()
                                           .isDepartmentContactOnly()) {
        throw new IllegalArgumentException("UBL has no department of a contact; the department " + party.extended()
                                                                                                         .contact()
                                                                                                         .department() + " of " + party.name() + " needs CII");
      }
    }
    // UBL-SR-16: the buyer identifier occurs at most once; CII states ram:ID and ram:GlobalID.
    if (!invoice.buyer()
                .additionalIdentifiers()
                .isEmpty()) {
      throw new IllegalArgumentException("UBL has one buyer identifier (UBL-SR-16); the further buyer identifiers " + invoice.buyer()
                                                                                                                          .additionalIdentifiers() + " need CII");
    }
    if (invoice.hasExtendedInformation()) {
      throw new IllegalArgumentException("EXTENDED information is part of the Factur-X profile EXTENDED and has no UBL mapping; write CII instead");
    }
    boolean credit = invoice.typeCode()
                            .isCreditNote();
    if (invoice.hasXrechnungExtensionInformation()) {
      if (!invoice.processControl()
                  .isXrechnungExtension()) {
        throw new IllegalArgumentException("Sub invoice lines, third party payments, and XML attachments require the specification identifier of the XRechnung extension (ProcessControl.xrechnungExtension())");
      }
      if (credit) {
        throw new IllegalArgumentException("The XRechnung extension covers UBL Invoice only; a credit note cannot carry sub invoice lines, third party payments, or XML attachments");
      }
    }
    String root = credit ?
                  "CreditNote" :
                  "Invoice";
    String currency = invoice.currencyCode()
                             .name();
    InvoiceTotals totals = invoice.totals();
    References references = invoice.references() == null ?
                            References.create()
                                      .build() :
                            invoice.references();
    var xml = new XmlBuilder(root,
                             Map.of("",
                                    UBL + root + "-2",
                                    "cbc",
                                    UBL + "CommonBasicComponents-2",
                                    "cac",
                                    UBL + "CommonAggregateComponents-2"));
    xml.element("cbc:CustomizationID",
                invoice.processControl()
                       .specificationId())
       .element("cbc:ProfileID",
                invoice.processControl()
                       .businessProcessId())
       .element("cbc:ID",
                invoice.number())
       .element("cbc:IssueDate",
                invoice.issueDate());
    // The CreditNote schema orders TaxPointDate before the type code and has no DueDate.
    if (credit) {
      xml.element("cbc:TaxPointDate",
                  invoice.taxPointDate());
    } else {
      xml.element("cbc:DueDate",
                  invoice.dueDate());
    }
    xml.element(credit ?
                "cbc:CreditNoteTypeCode" :
                "cbc:InvoiceTypeCode",
                invoice.typeCode()
                       .getCode());
    invoice.notes()
           .forEach(note -> xml.element("cbc:Note",
                                        note.subjectCode() == null ?
                                        note.text() :
                                        "#" + note.subjectCode() + "#" + note.text()));
    if (!credit) {
      xml.element("cbc:TaxPointDate",
                  invoice.taxPointDate());
    }
    xml.element("cbc:DocumentCurrencyCode",
                currency)
       .element("cbc:TaxCurrencyCode",
                invoice.taxCurrencyCode() == null ?
                null :
                invoice.taxCurrencyCode()
                       .name())
       .element("cbc:AccountingCost",
                references.buyerAccountingReference())
       .element("cbc:BuyerReference",
                invoice.buyerReference());
    var period = invoice.invoicePeriod();
    if (period != null || invoice.taxPointDateCode() != null) {
      xml.start("cac:InvoicePeriod");
      if (period != null) {
        xml.element("cbc:StartDate",
                    period.startDate())
           .element("cbc:EndDate",
                    period.endDate());
      }
      xml.element("cbc:DescriptionCode",
                  invoice.taxPointDateCode() == null ?
                  null :
                  invoice.taxPointDateCode()
                         .getCode())
         .end();
    }
    if (references.purchaseOrderReference() != null || references.salesOrderReference() != null) {
      // OrderReference/ID is mandatory in UBL; "NA" marks a missing purchase order (Peppol convention).
      xml.start("cac:OrderReference")
         .element("cbc:ID",
                  references.purchaseOrderReference() == null ?
                  "NA" :
                  references.purchaseOrderReference())
         .element("cbc:SalesOrderID",
                  references.salesOrderReference())
         .end();
    }
    for (var preceding : invoice.precedingInvoiceReferences()) {
      xml.start("cac:BillingReference")
         .start("cac:InvoiceDocumentReference")
         .element("cbc:ID",
                  preceding.number())
         .element("cbc:IssueDate",
                  preceding.issueDate())
         .end()
         .end();
    }
    documentReference(xml,
                      "cac:DespatchDocumentReference",
                      references.despatchAdviceReference());
    documentReference(xml,
                      "cac:ReceiptDocumentReference",
                      references.receivingAdviceReference());
    if (!credit) {
      documentReference(xml,
                        "cac:OriginatorDocumentReference",
                        references.tenderOrLotReference());
    }
    documentReference(xml,
                      "cac:ContractDocumentReference",
                      references.contractReference());
    additionalDocuments(xml,
                        invoice,
                        references,
                        credit);
    if (credit) {
      documentReference(xml,
                        "cac:OriginatorDocumentReference",
                        references.tenderOrLotReference());
    } else {
      documentReference(xml,
                        "cac:ProjectReference",
                        references.projectReference());
    }
    var payment = invoice.paymentInstruction();
    String creditorId = payment == null || payment.directDebit() == null ?
                        null :
                        payment.directDebit()
                               .creditorId();
    party(xml,
          "cac:AccountingSupplierParty",
          invoice.seller(),
          true,
          invoice.payee() == null ?
          creditorId :
          null);
    party(xml,
          "cac:AccountingCustomerParty",
          invoice.buyer(),
          false,
          null);
    var payee = invoice.payee();
    if (payee != null) {
      xml.start("cac:PayeeParty");
      partyIdentification(xml,
                          payee.identifier(),
                          payee.identifierSchemeId());
      partyIdentification(xml,
                          creditorId,
                          "SEPA");
      xml.start("cac:PartyName")
         .element("cbc:Name",
                  payee.name())
         .end();
      if (payee.legalRegistrationId() != null) {
        xml.start("cac:PartyLegalEntity")
           .element("cbc:CompanyID",
                    payee.legalRegistrationId(),
                    "schemeID",
                    payee.legalRegistrationSchemeId())
           .end();
      }
      xml.end();
    }
    var representative = invoice.taxRepresentative();
    if (representative != null) {
      xml.start("cac:TaxRepresentativeParty")
         .start("cac:PartyName")
         .element("cbc:Name",
                  representative.name())
         .end();
      address(xml,
              "cac:PostalAddress",
              representative.address());
      partyTax(xml,
               representative.vatId(),
               "VAT");
      xml.end();
    }
    delivery(xml,
             invoice);
    if (payment != null) {
      paymentMeans(xml,
                   payment);
    }
    // UBL CreditNote has no DueDate element; express an optional due date in the terms text.
    String terms = invoice.paymentTerms();
    if (credit && invoice.dueDate() != null) {
      terms = (terms == null ?
               "" :
               terms + " ") + "Due date: " + invoice.dueDate();
    }
    terms = CashDiscount.paymentTerms(terms,
                                      invoice.cashDiscounts());
    if (terms != null) {
      xml.start("cac:PaymentTerms")
         .element("cbc:Note",
                  terms)
         .end();
    }
    // XRechnung extension: third party payments (BG-DEX-09).
    for (var thirdParty : invoice.thirdPartyPayments()) {
      xml.start("cac:PrepaidPayment")
         .element("cbc:ID",
                  thirdParty.type());
      money(xml,
            "cbc:PaidAmount",
            thirdParty.amount(),
            currency);
      xml.element("cbc:InstructionID",
                  thirdParty.description())
         .end();
    }
    for (var adjustment : invoice.adjustments()) {
      xml.start("cac:AllowanceCharge")
         .element("cbc:ChargeIndicator",
                  adjustment.charge())
         .element("cbc:AllowanceChargeReasonCode",
                  adjustment.reasonCode())
         .element("cbc:AllowanceChargeReason",
                  adjustment.reason())
         .element("cbc:MultiplierFactorNumeric",
                  adjustment.percentage());
      money(xml,
            "cbc:Amount",
            adjustment.amount(),
            currency);
      money(xml,
            "cbc:BaseAmount",
            adjustment.baseAmount(),
            currency);
      xml.start("cac:TaxCategory");
      tax(xml,
          adjustment.tax(),
          false);
      xml.end()
         .end();
    }
    xml.start("cac:TaxTotal");
    money(xml,
          "cbc:TaxAmount",
          totals.taxAmount(),
          currency);
    for (var breakdown : totals.vatBreakdowns()) {
      xml.start("cac:TaxSubtotal");
      money(xml,
            "cbc:TaxableAmount",
            breakdown.taxableAmount(),
            currency);
      money(xml,
            "cbc:TaxAmount",
            breakdown.taxAmount(),
            currency);
      xml.start("cac:TaxCategory");
      tax(xml,
          breakdown.tax(),
          true);
      xml.end()
         .end();
    }
    xml.end();
    if (invoice.taxCurrencyCode() != null) {
      xml.start("cac:TaxTotal");
      money(xml,
            "cbc:TaxAmount",
            invoice.taxAmountInTaxCurrency(),
            invoice.taxCurrencyCode()
                   .name());
      xml.end();
    }
    xml.start("cac:LegalMonetaryTotal");
    money(xml,
          "cbc:LineExtensionAmount",
          totals.lineTotal(),
          currency);
    money(xml,
          "cbc:TaxExclusiveAmount",
          totals.taxExclusiveAmount(),
          currency);
    money(xml,
          "cbc:TaxInclusiveAmount",
          totals.taxInclusiveAmount(),
          currency);
    money(xml,
          "cbc:AllowanceTotalAmount",
          totals.allowanceTotal(),
          currency);
    money(xml,
          "cbc:ChargeTotalAmount",
          totals.chargeTotal(),
          currency);
    money(xml,
          "cbc:PrepaidAmount",
          totals.paidAmount(),
          currency);
    money(xml,
          "cbc:PayableRoundingAmount",
          totals.roundingAmount(),
          currency);
    money(xml,
          "cbc:PayableAmount",
          totals.payableAmount(),
          currency);
    xml.end();
    for (var line : invoice.lines()) {
      line(xml,
           line,
           credit,
           currency);
    }
    return xml.toXml();
  }
}

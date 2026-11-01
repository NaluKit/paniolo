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
import io.github.nalukit.paniolo.model.common.CashDiscount;
import io.github.nalukit.paniolo.model.common.Contact;
import io.github.nalukit.paniolo.model.common.CreditTransfer;
import io.github.nalukit.paniolo.model.common.DeliveryInformation;
import io.github.nalukit.paniolo.model.common.DocumentAdjustment;
import io.github.nalukit.paniolo.model.common.InvoiceLine;
import io.github.nalukit.paniolo.model.common.InvoiceNote;
import io.github.nalukit.paniolo.model.common.InvoicePeriod;
import io.github.nalukit.paniolo.model.common.ItemClassification;
import io.github.nalukit.paniolo.model.common.LineAdjustment;
import io.github.nalukit.paniolo.model.common.Party;
import io.github.nalukit.paniolo.model.common.Payee;
import io.github.nalukit.paniolo.model.common.PaymentInstruction;
import io.github.nalukit.paniolo.model.common.ProcessControl;
import io.github.nalukit.paniolo.model.common.References;
import io.github.nalukit.paniolo.model.common.TaxCategory;
import io.github.nalukit.paniolo.model.common.value.AllowanceChargeIdentification;
import io.github.nalukit.paniolo.model.common.value.Country;
import io.github.nalukit.paniolo.model.common.value.CurrencyCode;
import io.github.nalukit.paniolo.model.common.value.DutyTaxFeeCategory;
import io.github.nalukit.paniolo.model.common.value.InvoiceTypeCode;
import io.github.nalukit.paniolo.model.common.value.PaymentMeansCode;
import io.github.nalukit.paniolo.model.common.value.UnitCode;
import io.github.nalukit.paniolo.model.extended.ExtendedInformation;
import io.github.nalukit.paniolo.model.extended.LogisticsServiceCharge;
import io.github.nalukit.paniolo.model.extended.PaymentTerm;
import io.github.nalukit.paniolo.model.extended.PaymentTermsAdjustment;
import io.github.nalukit.paniolo.xml.InvoiceXmlReader;
import io.github.nalukit.paniolo.xml.ReadMode;
import io.github.nalukit.paniolo.xml.ReadResult;
import io.github.nalukit.paniolo.xml.util.InvoiceConsistency;
import io.github.nalukit.paniolo.xml.util.ReadContext;
import io.github.nalukit.paniolo.xml.util.XmlElement;
import org.w3c.dom.Document;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Reads ZUGFeRD 1.0 documents ({@code rsm:CrossIndustryDocument}, a customised CII 2013b) into the
 * invoice model, e.g. to migrate them to ZUGFeRD 2 / Factur-X with the CII writer. The profiles map
 * to Factur-X as BASIC to BASIC, COMFORT to EN 16931, and EXTENDED to EXTENDED.
 *
 * <p>Outside the target profile EXTENDED, logistics service charges become document charges and
 * cash discount terms become {@link CashDiscount}s. Content the model cannot carry, such as late
 * payment penalties outside EXTENDED or direct debits without mandate reference, is rejected.
 */
public final class ZugferdV1InvoiceReader
    implements InvoiceXmlReader {
  /**
   * Namespace of the ZUGFeRD 1.0 root element.
   */
  public static final String RSM = "urn:ferd:CrossIndustryDocument:invoice:1p0";

  private static final Map<String, String> NAMESPACES = Map.of("rsm",
                                                               RSM,
                                                               "ram",
                                                               "urn:un:unece:uncefact:data:standard:ReusableAggregateBusinessInformationEntity:12",
                                                               "qdt",
                                                               "urn:un:unece:uncefact:data:standard:QualifiedDataType:12",
                                                               "udt",
                                                               "urn:un:unece:uncefact:data:standard:UnqualifiedDataType:15");

  /**
   * ZUGFeRD 1.0 guideline identifiers and their Factur-X counterparts.
   */
  private static final Map<String, String> PROFILES = Map.of(RSM + ":basic",
                                                             ProcessControl.FACTURX_BASIC,
                                                             RSM + ":comfort",
                                                             ProcessControl.EN16931,
                                                             RSM + ":extended",
                                                             ProcessControl.FACTURX_EXTENDED);

  private final String targetSpecificationId;

  /**
   * Maps the ZUGFeRD 1.0 profile to the corresponding Factur-X profile.
   */
  public ZugferdV1InvoiceReader() {
    this(null);
  }

  private ZugferdV1InvoiceReader(String targetSpecificationId) {
    this.targetSpecificationId = targetSpecificationId;
  }

  /**
   * Reads into the given specification identifier (BT-24) instead of the mapped profile, e.g.
   * {@code ProcessControl.EN16931} to migrate a ZUGFeRD 1.0 BASIC document to Factur-X EN 16931.
   *
   * @param specificationId the specification identifier of the invoices read
   * @return the reader
   */
  public static ZugferdV1InvoiceReader withSpecificationId(String specificationId) {
    return new ZugferdV1InvoiceReader(Objects.requireNonNull(specificationId,
                                                             "specificationId"));
  }

  /**
   * Returns the Factur-X specification identifier of a ZUGFeRD 1.0 guideline identifier, or null.
   *
   * @param zugferd1GuidelineId the ZUGFeRD 1.0 guideline identifier, or null
   * @return the specification identifier, or null if the guideline is unknown
   */
  public static String specificationIdOf(String zugferd1GuidelineId) {
    return zugferd1GuidelineId == null ?
           null :
           PROFILES.get(zugferd1GuidelineId.strip());
  }

  /**
   * Keeps allowance reason codes only if they are UNTDID 5189 codes, as EN 16931 requires.
   */
  private static String reasonCode(boolean charge,
                                   String code) {
    return code == null || charge || AllowanceChargeIdentification.of(code) != null ?
           code :
           null;
  }

  private static TaxCategory tax(XmlElement tax) {
    if (tax == null) {
      return null;
    }
    var code = XmlElement.code(tax.text("ram:CategoryCode"),
                               DutyTaxFeeCategory::of,
                               "VAT category");
    return new TaxCategory(code,
                           code == DutyTaxFeeCategory.O ?
                           null :
                           tax.decimal("ram:ApplicablePercent"),
                           tax.text("ram:ExemptionReason"),
                           null);
  }

  /**
   * ZUGFeRD 1.0 allows several content elements per note; CII D16B has one.
   */
  private static String content(XmlElement note) {
    String text = note.children("ram:Content")
                      .stream()
                      .map(XmlElement::text)
                      .filter(Objects::nonNull)
                      .collect(Collectors.joining("\n"));
    return text.isBlank() ?
           null :
           text;
  }

  private String specificationId(String guideline) {
    if (targetSpecificationId != null) {
      return targetSpecificationId;
    }
    String mapped = specificationIdOf(guideline);
    if (mapped == null) {
      throw new IllegalArgumentException("Unknown ZUGFeRD 1.0 profile: " + guideline);
    }
    return mapped;
  }

  @Override
  public ReadResult read(Document document,
                         ReadMode mode) {
    Objects.requireNonNull(document,
                           "document");
    var root = XmlElement.of(document.getDocumentElement(),
                             NAMESPACES);
    if (!"CrossIndustryDocument".equals(root.localName()) || !RSM.equals(root.namespace())) {
      throw new IllegalArgumentException("Not a ZUGFeRD 1.0 CrossIndustryDocument: {" + root.namespace() + "}" + root.localName());
    }
    var context     = root.find("rsm:SpecifiedExchangedDocumentContext");
    var header      = root.find("rsm:HeaderExchangedDocument");
    var transaction = root.find("rsm:SpecifiedSupplyChainTradeTransaction");
    if (context == null || header == null || transaction == null) {
      throw new IllegalArgumentException("ZUGFeRD 1.0 document context, header or transaction is missing");
    }
    return new Reading(specificationId(context.text("ram:GuidelineSpecifiedDocumentContextParameter/ram:ID")),
                       new ReadContext(mode)).read(header,
                                                   transaction);
  }

  /**
   * State of one read; the reader itself stays immutable and thread-safe.
   */
  private final class Reading {
    private final String      specificationId;
    private final boolean     extended;
    private final ReadContext context;

    private Reading(String specificationId,
                    ReadContext context) {
      this.specificationId = specificationId;
      this.extended        = ProcessControl.FACTURX_EXTENDED.equals(specificationId);
      this.context         = context;
    }

    private static PaymentTermsAdjustment adjustment(XmlElement terms,
                                                     String amountName) {
      if (terms == null) {
        return null;
      }
      var days = terms.decimal("ram:BasisPeriodMeasure");
      return new PaymentTermsAdjustment(terms.basicDate("ram:BasisDateTime/udt:DateTimeString"),
                                        days == null ?
                                        null :
                                        days.intValueExact(),
                                        terms.decimal("ram:BasisAmount"),
                                        terms.decimal("ram:CalculationPercent"),
                                        terms.decimal(amountName));
    }

    private static PaymentInstruction paymentInstruction(List<XmlElement> means,
                                                         XmlElement settlement) {
      var first = means.get(0);
      if (first.find("ram:PayerPartyDebtorFinancialAccount") != null) {
        throw new IllegalArgumentException("ZUGFeRD 1.0 direct debits carry no mandate reference (BT-89) and cannot be migrated");
      }
      String information = first.children("ram:Information")
                                .stream()
                                .map(XmlElement::text)
                                .filter(Objects::nonNull)
                                .collect(Collectors.joining(" "));
      var builder = PaymentInstruction.create()
                                      .meansCode(XmlElement.code(first.text("ram:TypeCode"),
                                                                 PaymentMeansCode::of,
                                                                 "payment means code"))
                                      .meansText(information.isBlank() ?
                                                 null :
                                                 information)
                                      .remittanceInformation(settlement.text("ram:PaymentReference"));
      for (var mean : means) {
        var account = mean.find("ram:PayeePartyCreditorFinancialAccount");
        if (account != null) {
          String id = account.text("ram:IBANID");
          builder.addCreditTransfer(new CreditTransfer(id == null ?
                                                       account.text("ram:ProprietaryID") :
                                                       id,
                                                       account.text("ram:AccountName"),
                                                       mean.text("ram:PayeeSpecifiedCreditorFinancialInstitution/ram:BICID")));
        }
      }
      return builder.build();
    }

    /**
     * Reads a party; a contact with department or fax becomes an EN 16931 contact unless the
     * target is EXTENDED, where it is kept.
     */
    private Party party(XmlElement element) {
      var party = CiiInvoiceReader.party(element);
      if (party == null || party.extended() == null || extended) {
        return party;
      }
      var contact = party.extended()
                         .contact();
      return new Party(party.name(),
                       party.address(),
                       party.vatId(),
                       party.taxId(),
                       party.electronicAddress(),
                       contact == null ?
                       party.contact() :
                       new Contact(contact.name() == null ?
                                   contact.department() :
                                   contact.name(),
                                   contact.telephone(),
                                   contact.email()),
                       party.identifier(),
                       party.identifierSchemeId(),
                       party.tradingName(),
                       party.legalRegistrationId(),
                       party.legalRegistrationSchemeId(),
                       party.additionalLegalInformation());
    }

    private ReadResult read(XmlElement header,
                            XmlElement transaction) {
      var agreement  = transaction.find("ram:ApplicableSupplyChainTradeAgreement");
      var delivery   = transaction.find("ram:ApplicableSupplyChainTradeDelivery");
      var settlement = transaction.find("ram:ApplicableSupplyChainTradeSettlement");
      if (agreement == null || settlement == null) {
        throw new IllegalArgumentException("ZUGFeRD 1.0 trade agreement or settlement is missing");
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
                           .processControl(new ProcessControl(null,
                                                              specificationId))
                           .buyerReference(agreement.text("ram:BuyerReference"));
      for (var note : header.children("ram:IncludedNote")) {
        String text = content(note);
        if (text != null) {
          builder.addNote(new InvoiceNote(note.text("ram:SubjectCode"),
                                          text));
        }
      }

      var references = References.create()
                                 .purchaseOrderReference(agreement.text("ram:BuyerOrderReferencedDocument/ram:ID"))
                                 .contractReference(agreement.text("ram:ContractReferencedDocument/ram:ID"))
                                 .buyerAccountingReference(settlement.text("ram:ReceivableSpecifiedTradeAccountingAccount/ram:ID") == null ?
                                                           settlement.text("ram:SpecifiedTradeAccountingAccount/ram:ID") :
                                                           settlement.text("ram:ReceivableSpecifiedTradeAccountingAccount/ram:ID"));
      for (var referenced : agreement.children("ram:AdditionalReferencedDocument")) {
        String id = referenced.text("ram:ID");
        if (id == null) {
          continue;
        }
        if ("130".equals(referenced.text("ram:TypeCode"))) {
          references.invoicedObjectId(id)
                    .invoicedObjectSchemeId(referenced.text("ram:ReferenceTypeCode"));
        } else if ("50".equals(referenced.text("ram:TypeCode"))) {
          references.tenderOrLotReference(id);
        } else {
          builder.addSupportingDocument(new AdditionalSupportingDocument(id,
                                                                         null,
                                                                         null,
                                                                         null));
        }
      }
      if (delivery != null) {
        references.despatchAdviceReference(delivery.text("ram:DespatchAdviceReferencedDocument/ram:ID"))
                  .receivingAdviceReference(delivery.text("ram:ReceivingAdviceReferencedDocument/ram:ID"));
        var shipTo = delivery.find("ram:ShipToTradeParty");
        var date   = delivery.basicDate("ram:ActualDeliverySupplyChainEvent/ram:OccurrenceDateTime/udt:DateTimeString");
        if (shipTo != null || date != null) {
          builder.delivery(new DeliveryInformation(shipTo == null ?
                                                   null :
                                                   shipTo.text("ram:Name"),
                                                   shipTo == null ?
                                                   null :
                                                   shipTo.text("ram:ID"),
                                                   date,
                                                   shipTo == null ?
                                                   null :
                                                   CiiInvoiceReader.address(shipTo.find("ram:PostalTradeAddress"))));
        }
      }
      var builtReferences = references.build();
      builder.references(builtReferences.equals(References.create()
                                                          .build()) ?
                         null :
                         builtReferences);
      builder.seller(party(agreement.find("ram:SellerTradeParty")))
             .buyer(party(agreement.find("ram:BuyerTradeParty")));
      var payee = settlement.find("ram:PayeeTradeParty");
      if (payee != null) {
        var globalId = payee.text("ram:ID") == null ?
                       payee.find("ram:GlobalID") :
                       null;
        builder.payee(Payee.create()
                           .name(payee.text("ram:Name"))
                           .identifier(globalId == null ?
                                       payee.text("ram:ID") :
                                       globalId.text(),
                                       globalId == null ?
                                       null :
                                       globalId.attribute("schemeID"))
                           .build());
      }

      var breakdowns = new ArrayList<TaxCategory>();
      for (var tax : settlement.children("ram:ApplicableTradeTax")) {
        breakdowns.add(tax(tax));
      }
      var categories = InvoiceConsistency.byCategoryAndRate(breakdowns);
      var period     = settlement.find("ram:BillingSpecifiedPeriod");
      if (period != null) {
        builder.invoicePeriod(new InvoicePeriod(period.basicDate("ram:StartDateTime/udt:DateTimeString"),
                                                period.basicDate("ram:EndDateTime/udt:DateTimeString")));
      }
      for (var adjustment : settlement.children("ram:SpecifiedTradeAllowanceCharge")) {
        boolean charge = Boolean.parseBoolean(adjustment.text("ram:ChargeIndicator/udt:Indicator"));
        String  reason = adjustment.text("ram:Reason");
        String code = reasonCode(charge,
                                 adjustment.text("ram:ReasonCode"));
        builder.addAdjustment(new DocumentAdjustment(charge,
                                                     adjustment.decimal("ram:ActualAmount"),
                                                     adjustment.decimal("ram:BasisAmount"),
                                                     context.percentage(charge,
                                                                        adjustment.decimal("ram:CalculationPercent"),
                                                                        adjustment.decimal("ram:BasisAmount"),
                                                                        null),
                                                     // EN 16931 requires a reason; ZUGFeRD 1.0 did not.
                                                     reason == null && code == null ?
                                                     (charge ?
                                                      "Charge" :
                                                      "Allowance") :
                                                     reason,
                                                     code,
                                                     InvoiceConsistency.complete(tax(adjustment.find("ram:CategoryTradeTax")),
                                                                                 categories)));
      }
      var extendedInformation = ExtendedInformation.create();
      for (var service : settlement.children("ram:SpecifiedLogisticsServiceCharge")) {
        var tax = InvoiceConsistency.complete(tax(service.find("ram:AppliedTradeTax")),
                                              categories);
        if (extended) {
          extendedInformation.addLogisticsServiceCharge(new LogisticsServiceCharge(service.text("ram:Description"),
                                                                                   service.decimal("ram:AppliedAmount"),
                                                                                   tax));
        } else {
          builder.addAdjustment(new DocumentAdjustment(true,
                                                       service.decimal("ram:AppliedAmount"),
                                                       service.text("ram:Description"),
                                                       tax));
        }
      }
      paymentTerms(builder,
                   extendedInformation,
                   settlement.children("ram:SpecifiedTradePaymentTerms"));
      var means = settlement.children("ram:SpecifiedTradeSettlementPaymentMeans");
      if (!means.isEmpty()) {
        builder.paymentInstruction(paymentInstruction(means,
                                                      settlement));
      }

      BigDecimal statedTax = null;
      var        summation = settlement.find("ram:SpecifiedTradeSettlementMonetarySummation");
      if (summation != null) {
        for (var amount : summation.children("ram:TaxTotalAmount")) {
          String amountCurrency = amount.attribute("currencyID");
          if (amountCurrency == null || amountCurrency.equals(currency)) {
            statedTax = amount.decimal();
          }
        }
        BigDecimal prepaid = summation.decimal("ram:TotalPrepaidAmount");
        builder.paidAmount(prepaid == null ?
                           BigDecimal.ZERO :
                           prepaid);
      }
      var items    = transaction.children("ram:IncludedSupplyChainTradeLineItem");
      int position = 0;
      for (int index = 0; index < items.size(); index++) {
        var item = items.get(index);
        // ZUGFeRD 1.0 EXTENDED allows lines with a text only, without item and quantity.
        if (item.find("ram:SpecifiedSupplyChainTradeDelivery/ram:BilledQuantity") == null && item.find("ram:SpecifiedTradeProduct") == null) {
          var texts = item.all("ram:AssociatedDocumentLineDocument/ram:IncludedNote/ram:Content")
                          .stream()
                          .map(XmlElement::text)
                          .filter(java.util.Objects::nonNull)
                          .toList();
          if (!texts.isEmpty()) {
            String text = String.join("\n",
                                      texts);
            context.textLine(index + 1,
                             text);
            builder.addNote(text);
            continue;
          }
        }
        builder.addLine(line(item,
                             ++position,
                             categories));
      }
      builder.extended(extendedInformation.build());
      return context.result(builder.build(),
                            CiiInvoiceReader.statedAmounts(summation,
                                                           statedTax));
    }

    /**
     * The first plain term carries BT-20 and BT-9; cash discounts become {@link CashDiscount}s,
     * or EXTENDED payment terms when the target is EXTENDED.
     */
    private void paymentTerms(Invoice.Builder builder,
                              ExtendedInformation.Builder extendedInformation,
                              List<XmlElement> terms) {
      var texts = new ArrayList<String>();
      for (var term : terms) {
        var discount = term.find("ram:ApplicableTradePaymentDiscountTerms");
        var penalty  = term.find("ram:ApplicableTradePaymentPenaltyTerms");
        var partial  = term.decimal("ram:PartialPaymentAmount");
        String description = term.children("ram:Description")
                                 .stream()
                                 .map(XmlElement::text)
                                 .filter(Objects::nonNull)
                                 .collect(Collectors.joining("\n"));
        var due = term.basicDate("ram:DueDateDateTime/udt:DateTimeString");
        if (discount == null && penalty == null && partial == null) {
          if (!description.isBlank()) {
            texts.add(description);
          }
          if (due != null) {
            builder.dueDate(due);
          }
          continue;
        }
        if (extended) {
          extendedInformation.addPaymentTerm(new PaymentTerm(description.isBlank() ?
                                                             null :
                                                             description,
                                                             due,
                                                             partial,
                                                             adjustment(penalty,
                                                                        "ram:ActualPenaltyAmount"),
                                                             adjustment(discount,
                                                                        "ram:ActualDiscountAmount")));
          continue;
        }
        if (penalty != null || partial != null) {
          throw new IllegalArgumentException("ZUGFeRD 1.0 late payment penalties and partial payments need the target profile EXTENDED");
        }
        var days    = discount.decimal("ram:BasisPeriodMeasure");
        var percent = discount.decimal("ram:CalculationPercent");
        if (days == null || percent == null) {
          throw new IllegalArgumentException("ZUGFeRD 1.0 cash discount without period or percentage needs the target profile EXTENDED");
        }
        builder.addCashDiscount(new CashDiscount(days.intValueExact(),
                                                 percent,
                                                 discount.decimal("ram:BasisAmount")));
        if (!description.isBlank()) {
          texts.add(description);
        }
        if (due != null) {
          builder.dueDate(due);
        }
      }
      if (!texts.isEmpty()) {
        builder.paymentTerms(String.join("\n",
                                         texts));
      }
    }

    private InvoiceLine line(XmlElement item,
                             int position,
                             Map<String, TaxCategory> categories) {
      // ZUGFeRD 1.0 does not require a line identifier; the position in the document stands in.
      String lineId = Objects.requireNonNullElse(item.text("ram:AssociatedDocumentLineDocument/ram:LineID"),
                                                 String.valueOf(position));
      var quantity   = item.find("ram:SpecifiedSupplyChainTradeDelivery/ram:BilledQuantity");
      var agreement  = item.find("ram:SpecifiedSupplyChainTradeAgreement");
      var settlement = item.find("ram:SpecifiedSupplyChainTradeSettlement");
      var product    = item.find("ram:SpecifiedTradeProduct");
      var price = agreement == null ?
                  null :
                  agreement.find("ram:NetPriceProductTradePrice");
      var gross = agreement == null ?
                  null :
                  agreement.find("ram:GrossPriceProductTradePrice");
      BigDecimal amount = settlement == null ?
                          null :
                          settlement.decimal("ram:SpecifiedTradeSettlementMonetarySummation/ram:LineTotalAmount");
      if (quantity == null) {
        throw new IllegalArgumentException("ZUGFeRD 1.0 line without billed quantity");
      }
      var count = quantity.decimal();
      var builder = InvoiceLine.create()
                               .id(lineId)
                               .quantity(count)
                               .unitCode(XmlElement.code(quantity.attribute("unitCode"),
                                                         UnitCode::of,
                                                         "unit code"))
                               .tax(InvoiceConsistency.complete(tax(settlement == null ?
                                                                    null :
                                                                    settlement.find("ram:ApplicableTradeTax")),
                                                                categories));
      var note = item.find("ram:AssociatedDocumentLineDocument/ram:IncludedNote");
      if (note != null) {
        builder.note(content(note));
      }
      if (price != null) {
        BigDecimal base = price.decimal("ram:BasisQuantity");
        builder.netUnitPrice(price.decimal("ram:ChargeAmount"))
               .priceBaseQuantity(base == null ?
                                  BigDecimal.ONE :
                                  base);
      } else if (amount != null && count.signum() != 0) {
        // ZUGFeRD 1.0 BASIC lines may carry only the line amount: one price for the whole quantity.
        builder.netUnitPrice(amount.abs())
               .priceBaseQuantity(count.abs());
      } else {
        throw new IllegalArgumentException("ZUGFeRD 1.0 line without net price and line amount");
      }
      if (gross != null) {
        BigDecimal discount = BigDecimal.ZERO;
        for (var allowance : gross.children("ram:AppliedTradeAllowanceCharge")) {
          if (Boolean.parseBoolean(allowance.text("ram:ChargeIndicator/udt:Indicator"))) {
            throw new IllegalArgumentException("ZUGFeRD 1.0 charges on the gross price cannot be migrated");
          }
          discount = discount.add(allowance.decimal("ram:ActualAmount"));
        }
        builder.grossPrice(gross.decimal("ram:ChargeAmount"))
               .priceDiscount(discount);
      }
      if (agreement != null) {
        builder.orderLineReference(agreement.text("ram:BuyerOrderReferencedDocument/ram:LineID"));
      }
      if (product != null) {
        builder.name(product.text("ram:Name"))
               .description(product.text("ram:Description"))
               .sellerItemId(product.text("ram:SellerAssignedID"))
               .buyerItemId(product.text("ram:BuyerAssignedID"))
               .originCountry(XmlElement.code(product.text("ram:OriginTradeCountry/ram:ID"),
                                              Country::of,
                                              "country"));
        var standard = product.find("ram:GlobalID");
        if (standard != null) {
          builder.standardItemId(standard.text(),
                                 standard.attribute("schemeID"));
        }
        for (var attribute : product.children("ram:ApplicableProductCharacteristic")) {
          if (attribute.text("ram:Description") != null && attribute.text("ram:Value") != null) {
            builder.addAttribute(attribute.text("ram:Description"),
                                 attribute.text("ram:Value"));
          }
        }
        for (var classification : product.children("ram:DesignatedProductClassification")) {
          var code = classification.find("ram:ClassCode");
          if (code != null) {
            builder.addClassification(new ItemClassification(code.text(),
                                                             code.attribute("listID"),
                                                             code.attribute("listVersionID")));
          }
        }
      }
      if (settlement != null) {
        var period = settlement.find("ram:BillingSpecifiedPeriod");
        if (period != null) {
          builder.period(new InvoicePeriod(period.basicDate("ram:StartDateTime/udt:DateTimeString"),
                                           period.basicDate("ram:EndDateTime/udt:DateTimeString")));
        }
        for (var adjustment : settlement.children("ram:SpecifiedTradeAllowanceCharge")) {
          boolean charge = Boolean.parseBoolean(adjustment.text("ram:ChargeIndicator/udt:Indicator"));
          String  reason = adjustment.text("ram:Reason");
          String code = reasonCode(charge,
                                   adjustment.text("ram:ReasonCode"));
          builder.addAdjustment(new LineAdjustment(charge,
                                                   adjustment.decimal("ram:ActualAmount"),
                                                   adjustment.decimal("ram:BasisAmount"),
                                                   context.percentage(charge,
                                                                      adjustment.decimal("ram:CalculationPercent"),
                                                                      adjustment.decimal("ram:BasisAmount"),
                                                                      lineId),
                                                   reason == null && code == null ?
                                                   (charge ?
                                                    "Charge" :
                                                    "Allowance") :
                                                   reason,
                                                   code));
        }
        String account = settlement.text("ram:SpecifiedTradeAccountingAccount/ram:ID");
        builder.buyerAccountingReference(account);
      }
      return context.netAmount(builder.build(),
                               amount);
    }
  }
}

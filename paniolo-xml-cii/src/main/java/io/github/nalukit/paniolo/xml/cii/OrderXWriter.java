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

import io.github.nalukit.paniolo.model.common.Address;
import io.github.nalukit.paniolo.model.common.Party;
import io.github.nalukit.paniolo.model.common.TaxCategory;
import io.github.nalukit.paniolo.model.common.value.UnitCode;
import io.github.nalukit.paniolo.model.extended.DocumentReference;
import io.github.nalukit.paniolo.model.order.Order;
import io.github.nalukit.paniolo.model.order.OrderDetails;
import io.github.nalukit.paniolo.model.order.OrderDocument;
import io.github.nalukit.paniolo.model.order.OrderLine;
import io.github.nalukit.paniolo.model.order.OrderLineDetails;
import io.github.nalukit.paniolo.model.order.OrderPeriod;
import io.github.nalukit.paniolo.model.order.OrderXProfile;
import io.github.nalukit.paniolo.xml.util.XmlBuilder;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Writes Order-X 1.0 documents (UN/CEFACT SCRDM CIO, D20B) for orders, order changes, and order
 * responses. Content of the profile COMFORT is rejected for BASIC; all fields of the model are
 * part of COMFORT and EXTENDED. Stateless and thread-safe.
 */
public final class OrderXWriter {
  /**
   * Namespace of the Order-X root element {@code rsm:SCRDMCCBDACIOMessageStructure}.
   */
  public static final String RSM = "urn:un:unece:uncefact:data:SCRDMCCBDACIOMessageStructure:100";

  static final Map<String, String> NAMESPACES = Map.of("rsm",
                                                       RSM,
                                                       "ram",
                                                       "urn:un:unece:uncefact:data:standard:ReusableAggregateBusinessInformationEntity:128",
                                                       "qdt",
                                                       "urn:un:unece:uncefact:data:standard:QualifiedDataType:128",
                                                       "udt",
                                                       "urn:un:unece:uncefact:data:standard:UnqualifiedDataType:128");

  /**
   * Creates a writer; it has no state.
   */
  public OrderXWriter() {
  }

  /**
   * Names every field the profile BASIC does not allow.
   */
  private static void checkBasic(Order order) {
    var found = new ArrayList<String>();
    add(found,
        order.invoicee(),
        "invoicee");
    add(found,
        order.sellerOrderReference(),
        "seller order reference");
    add(found,
        order.paymentMeansCode(),
        "payment means");
    add(found,
        order.paymentTerms(),
        "payment terms");
    if (!order.adjustments()
              .isEmpty()) {
      found.add("document allowances and charges");
    }
    add(found,
        order.effectivePeriod(),
        "effective period");
    add(found,
        order.buyerRequisitioner(),
        "buyer requisitioner");
    add(found,
        order.requisitionReference(),
        "requisition reference");
    add(found,
        order.catalogueReference(),
        "catalogue reference");
    add(found,
        order.projectReference(),
        "project");
    add(found,
        order.paymentMeansText(),
        "payment means text");
    if (order.details() != null) {
      add(found,
          order.details()
               .deliveryTermsDescription(),
          "delivery terms description");
      add(found,
          order.details()
               .deliveryTermsLocationId() == null ?
          order.details()
               .deliveryTermsLocationName() :
          order.details()
               .deliveryTermsLocationId(),
          "delivery terms location");
      if (!order.details()
                .referencedDocuments()
                .isEmpty()) {
        found.add("referenced documents");
      }
    }
    if (!order.additionalDocuments()
              .isEmpty()) {
      found.add("additional documents");
    }
    for (var party : new Party[] { order.seller(),
                                   order.buyer(),
                                   order.shipTo(),
                                   order.shipFrom() }) {
      if (party != null && party.additionalLegalInformation() != null) {
        found.add(party.name() + " description");
      }
      if (party != null && party.vatId() != null && party.taxId() != null) {
        found.add(party.name() + " second tax registration");
      }
      if (party != null &&
          party.extended() != null &&
          party.extended()
               .contact() != null &&
          party.extended()
               .contact()
               .typeCode() != null) {
        found.add(party.name() + " contact type");
      }
    }
    for (var line : order.lines()) {
      String prefix = "line " + line.id() + " ";
      add(found,
          line.grossPrice(),
          prefix + "gross price");
      add(found,
          line.tax(),
          prefix + "VAT category");
      add(found,
          line.description(),
          prefix + "item description");
      add(found,
          line.requestedDeliveryDate(),
          prefix + "requested delivery date");
      add(found,
          line.buyerAccountingReference(),
          prefix + "accounting reference");
      if (!line.adjustments()
               .isEmpty()) {
        found.add(prefix + "allowances and charges");
      }
      for (Object[] comfort : new Object[][] { { line.priceDiscount(),
                                                 "gross price discount" },
                                               // BASIC allows the substituted product, but not its description.
                                               { line.substitutedProduct() == null ?
                                                 null :
                                                 line.substitutedProduct()
                                                     .description(),
                                                 "substituted product description" },
                                               { line.batchId(),
                                                 "batch" },
                                               { line.brandName(),
                                                 "brand" },
                                               { line.packagingTypeCode(),
                                                 "packaging" },
                                               { line.originCountry(),
                                                 "country of origin" },
                                               { line.quotationReference(),
                                                 "quotation reference" },
                                               { line.catalogueReference(),
                                                 "catalogue reference" },
                                               { line.packageQuantity(),
                                                 "package quantity" },
                                               { line.perPackageUnitQuantity(),
                                                 "quantity per package" },
                                               { line.requestedDespatchDate(),
                                                 "requested despatch date" } }) {
        add(found,
            comfort[0],
            prefix + comfort[1]);
      }
      if (!line.attributes()
               .isEmpty() ||
          !line.characteristics()
               .isEmpty()) {
        found.add(prefix + "item attributes");
      }
      if (!line.classifications()
               .isEmpty()) {
        found.add(prefix + "classifications");
      }
      if (!line.instances()
               .isEmpty()) {
        found.add(prefix + "product instances");
      }
      var details = line.details();
      if (details != null) {
        if (!details.priceAdjustments()
                    .isEmpty()) {
          found.add(prefix + "gross price allowances and charges");
        }
        add(found,
            details.requestedDeliveryPeriod(),
            prefix + "requested delivery period");
        add(found,
            details.requestedDespatchPeriod(),
            prefix + "requested despatch period");
        add(found,
            details.packageDimensions(),
            prefix + "package dimensions");
        if (!details.documents()
                    .isEmpty() ||
            !details.productDocuments()
                    .isEmpty()) {
          found.add(prefix + "referenced documents");
        }
      }
    }
    if (!found.isEmpty()) {
      throw new IllegalArgumentException("Order-X profile BASIC does not allow: " +
                                         String.join(", ",
                                                     found));
    }
  }

  private static String code(UnitCode unit) {
    return unit == null ?
           null :
           unit.getCode();
  }

  private static void add(List<String> found,
                          Object value,
                          String name) {
    if (value != null) {
      found.add(name);
    }
  }

  static void date(XmlBuilder xml,
                   String name,
                   LocalDate value) {
    dateTime(xml,
             name,
             value,
             null);
  }

  /**
   * A date in format 102, with time of day in format 203, or 204 if the time has seconds.
   */
  static void dateTime(XmlBuilder xml,
                       String name,
                       LocalDate date,
                       LocalTime time) {
    if (date == null) {
      return;
    }
    String text   = date.format(DateTimeFormatter.BASIC_ISO_DATE);
    String format = "102";
    if (time != null) {
      boolean seconds = time.getSecond() != 0 || time.getNano() != 0;
      text   = text + time.format(DateTimeFormatter.ofPattern(seconds ?
                                                              "HHmmss" :
                                                              "HHmm"));
      format = seconds ?
               "204" :
               "203";
    }
    xml.start(name)
       .element("udt:DateTimeString",
                text,
                "format",
                format)
       .end();
  }

  static void event(XmlBuilder xml,
                    String name,
                    LocalDate value) {
    event(xml,
          name,
          value,
          null,
          null);
  }

  /**
   * A requested event with a date (and time of day) and a period, both optional.
   */
  static void event(XmlBuilder xml,
                    String name,
                    LocalDate date,
                    LocalTime time,
                    OrderPeriod period) {
    if (date == null && period == null) {
      return;
    }
    xml.start(name);
    dateTime(xml,
             "ram:OccurrenceDateTime",
             date,
             time);
    if (period != null) {
      xml.start("ram:OccurrenceSpecifiedPeriod");
      dateTime(xml,
               "ram:StartDateTime",
               period.startDate(),
               period.startTime());
      dateTime(xml,
               "ram:EndDateTime",
               period.endDate(),
               period.endTime());
      xml.end();
    }
    xml.end();
  }

  /**
   * A further referenced document; its elements in the order of ReferencedDocumentType.
   */
  private static void document(XmlBuilder xml,
                               String name,
                               OrderDocument document) {
    xml.start(name)
       .element("ram:IssuerAssignedID",
                document.id())
       .element("ram:URIID",
                document.uri())
       .element("ram:LineID",
                document.lineId())
       .element("ram:TypeCode",
                document.typeCode())
       .element("ram:Name",
                document.name())
       .element("ram:ReferenceTypeCode",
                document.referenceTypeCode())
       .end();
  }

  static void reference(XmlBuilder xml,
                        String name,
                        DocumentReference reference) {
    if (reference != null) {
      xml.start(name)
         .element("ram:IssuerAssignedID",
                  reference.id())
         .element("ram:LineID",
                  reference.lineId())
         .end();
    }
  }

  private static void tax(XmlBuilder xml,
                          String name,
                          TaxCategory tax) {
    if (tax != null) {
      xml.start(name)
         .element("ram:TypeCode",
                  "VAT")
         .element("ram:CategoryCode",
                  tax.code()
                     .getCode())
         .element("ram:RateApplicablePercent",
                  tax.rate())
         .end();
    }
  }

  static void party(XmlBuilder xml,
                    String role,
                    Party party) {
    if (party == null) {
      return;
    }
    var extended = party.extended();
    if (extended != null &&
        (extended.roleCode() != null ||
         extended.legalAddress() != null ||
         extended.contact() != null &&
         extended.contact()
                 .fax() != null)) {
      throw new IllegalArgumentException("Party " + party.name() + ": role code, legal address, and fax have no Order-X mapping");
    }
    xml.start(role);
    if (party.identifierSchemeId() == null) {
      xml.element("ram:ID",
                  party.identifier());
    } else {
      xml.element("ram:GlobalID",
                  party.identifier(),
                  "schemeID",
                  party.identifierSchemeId());
    }
    if (extended != null && extended.globalId() != null) {
      xml.element("ram:GlobalID",
                  extended.globalId(),
                  "schemeID",
                  extended.globalIdSchemeId());
    }
    xml.element("ram:Name",
                party.name())
       .element("ram:Description",
                party.additionalLegalInformation());
    if (party.legalRegistrationId() != null || party.tradingName() != null) {
      xml.start("ram:SpecifiedLegalOrganization")
         .element("ram:ID",
                  party.legalRegistrationId(),
                  "schemeID",
                  party.legalRegistrationSchemeId())
         .element("ram:TradingBusinessName",
                  party.tradingName())
         .end();
    }
    var contact = party.contact();
    var detailed = extended == null ?
                   null :
                   extended.contact();
    if (contact != null || detailed != null) {
      xml.start("ram:DefinedTradeContact")
         .element("ram:PersonName",
                  contact == null ?
                  detailed.name() :
                  contact.name())
         .element("ram:DepartmentName",
                  detailed == null ?
                  null :
                  detailed.department())
         .element("ram:TypeCode",
                  detailed == null ?
                  null :
                  detailed.typeCode());
      String telephone = contact == null ?
                         detailed.telephone() :
                         contact.telephone();
      String email = contact == null ?
                     detailed.email() :
                     contact.email();
      if (telephone != null) {
        xml.start("ram:TelephoneUniversalCommunication")
           .element("ram:CompleteNumber",
                    telephone)
           .end();
      }
      if (email != null) {
        xml.start("ram:EmailURIUniversalCommunication")
           .element("ram:URIID",
                    email)
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
    registration(xml,
                 party.vatId(),
                 "VA");
    registration(xml,
                 party.taxId(),
                 "FC");
    xml.end();
  }

  private static void registration(XmlBuilder xml,
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

  private static void address(XmlBuilder xml,
                              Address address) {
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

  private static void line(XmlBuilder xml,
                           OrderLine line) {
    String unit = line.unitCode()
                      .getCode();
    xml.start("ram:IncludedSupplyChainTradeLineItem")
       .start("ram:AssociatedDocumentLineDocument")
       .element("ram:LineID",
                line.id())
       .element("ram:LineStatusCode",
                line.statusCode());
    var details = line.details() == null ?
                  OrderLineDetails.create()
                                  .build() :
                  line.details();
    if (line.note() != null) {
      xml.start("ram:IncludedNote")
         .element("ram:Content",
                  line.note())
         .element("ram:SubjectCode",
                  details.noteSubjectCode())
         .end();
    }
    xml.end()
       .start("ram:SpecifiedTradeProduct")
       .element("ram:GlobalID",
                line.standardItemId(),
                "schemeID",
                line.standardItemSchemeId())
       .element("ram:SellerAssignedID",
                line.sellerItemId())
       .element("ram:BuyerAssignedID",
                line.buyerItemId())
       .element("ram:Name",
                line.name())
       .element("ram:Description",
                line.description())
       .element("ram:BatchID",
                line.batchId())
       .element("ram:BrandName",
                line.brandName());
    for (var characteristic : line.characteristics()) {
      xml.start("ram:ApplicableProductCharacteristic")
         .element("ram:TypeCode",
                  characteristic.typeCode())
         .element("ram:Description",
                  characteristic.description())
         .element("ram:Value",
                  characteristic.value())
         .end();
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
    for (var instance : line.instances()) {
      xml.start("ram:IndividualTradeProductInstance")
         .element("ram:BatchID",
                  instance.batchId())
         .element("ram:SerialID",
                  instance.serialId())
         .end();
    }
    var dimensions = details.packageDimensions();
    if (line.packagingTypeCode() != null || dimensions != null) {
      xml.start("ram:ApplicableSupplyChainPackaging")
         .element("ram:TypeCode",
                  line.packagingTypeCode());
      if (dimensions != null) {
        xml.start("ram:LinearSpatialDimension")
           .element("ram:WidthMeasure",
                    dimensions.width(),
                    "unitCode",
                    code(dimensions.widthUnit()))
           .element("ram:LengthMeasure",
                    dimensions.length(),
                    "unitCode",
                    code(dimensions.lengthUnit()))
           .element("ram:HeightMeasure",
                    dimensions.height(),
                    "unitCode",
                    code(dimensions.heightUnit()))
           .end();
      }
      xml.end();
    }
    if (line.originCountry() != null) {
      xml.start("ram:OriginTradeCountry")
         .element("ram:ID",
                  line.originCountry()
                      .name())
         .end();
    }
    for (var document : details.productDocuments()) {
      document(xml,
               "ram:AdditionalReferenceReferencedDocument",
               document);
    }
    xml.end();
    var substitute = line.substitutedProduct();
    if (substitute != null) {
      xml.start("ram:SubstitutedReferencedProduct")
         .element("ram:GlobalID",
                  substitute.standardItemId(),
                  "schemeID",
                  substitute.standardItemSchemeId())
         .element("ram:SellerAssignedID",
                  substitute.sellerItemId())
         .element("ram:BuyerAssignedID",
                  substitute.buyerItemId())
         .element("ram:Name",
                  substitute.name())
         .element("ram:Description",
                  substitute.description())
         .end();
    }
    xml.start("ram:SpecifiedLineTradeAgreement");
    reference(xml,
              "ram:BuyerOrderReferencedDocument",
              line.buyerOrderReference());
    reference(xml,
              "ram:QuotationReferencedDocument",
              line.quotationReference());
    for (var document : details.documents()) {
      document(xml,
               "ram:AdditionalReferencedDocument",
               document);
    }
    if (line.grossPrice() != null) {
      xml.start("ram:GrossPriceProductTradePrice")
         .element("ram:ChargeAmount",
                  line.grossPrice())
         .element("ram:BasisQuantity",
                  line.priceBaseQuantity(),
                  "unitCode",
                  unit);
      if (line.priceDiscount() != null) {
        xml.start("ram:AppliedTradeAllowanceCharge")
           .start("ram:ChargeIndicator")
           .element("udt:Indicator",
                    false)
           .end()
           .element("ram:ActualAmount",
                    line.priceDiscount())
           .end();
      }
      for (var adjustment : details.priceAdjustments()) {
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
      xml.end();
    }
    xml.start("ram:NetPriceProductTradePrice")
       .element("ram:ChargeAmount",
                line.netUnitPrice())
       .element("ram:BasisQuantity",
                line.priceBaseQuantity(),
                "unitCode",
                unit)
       .end();
    reference(xml,
              "ram:CatalogueReferencedDocument",
              line.catalogueReference());
    reference(xml,
              "ram:BlanketOrderReferencedDocument",
              line.blanketOrderReference());
    xml.end()
       .start("ram:SpecifiedLineTradeDelivery");
    if (line.partialDeliveryAllowed() != null) {
      xml.start("ram:PartialDeliveryAllowedIndicator")
         .element("udt:Indicator",
                  line.partialDeliveryAllowed())
         .end();
    }
    xml.element("ram:RequestedQuantity",
                line.quantity(),
                "unitCode",
                unit)
       .element("ram:AgreedQuantity",
                line.agreedQuantity(),
                "unitCode",
                unit)
       .element("ram:PackageQuantity",
                line.packageQuantity(),
                "unitCode",
                details.packageQuantityUnit() == null ?
                "XPK" :
                details.packageQuantityUnit()
                       .getCode())
       .element("ram:PerPackageUnitQuantity",
                line.perPackageUnitQuantity(),
                "unitCode",
                unit);
    event(xml,
          "ram:RequestedDespatchSupplyChainEvent",
          line.requestedDespatchDate(),
          null,
          details.requestedDespatchPeriod());
    event(xml,
          "ram:RequestedDeliverySupplyChainEvent",
          line.requestedDeliveryDate(),
          null,
          details.requestedDeliveryPeriod());
    xml.end()
       .start("ram:SpecifiedLineTradeSettlement");
    tax(xml,
        "ram:ApplicableTradeTax",
        line.tax());
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
                line.netAmount())
       .end();
    if (line.buyerAccountingReference() != null) {
      xml.start("ram:ReceivableSpecifiedTradeAccountingAccount")
         .element("ram:ID",
                  line.buyerAccountingReference())
         .end();
    }
    xml.end()
       .end();
  }

  /**
   * Writes the order.
   *
   * @param order the order
   * @return the XML document
   * @throws IllegalArgumentException if the order contains content this syntax cannot carry
   */
  public String toXml(Order order) {
    Objects.requireNonNull(order,
                           "order");
    if (order.profile() == OrderXProfile.BASIC) {
      checkBasic(order);
    }
    if (order.adjustments()
             .stream()
             .anyMatch(adjustment -> adjustment.extended() != null)) {
      throw new IllegalArgumentException("Sequence numbers and base quantities of allowances and charges have no Order-X mapping");
    }
    var totals = order.totals();
    String currency = order.currencyCode()
                           .name();
    var xml = new XmlBuilder("rsm:SCRDMCCBDACIOMessageStructure",
                             NAMESPACES);
    var details = order.details() == null ?
                  OrderDetails.create()
                              .build() :
                  order.details();
    xml.start("rsm:ExchangedDocumentContext");
    if (details.testIndicator() != null) {
      xml.start("ram:TestIndicator")
         .element("udt:Indicator",
                  details.testIndicator())
         .end();
    }
    if (details.businessProcessId() != null) {
      xml.start("ram:BusinessProcessSpecifiedDocumentContextParameter")
         .element("ram:ID",
                  details.businessProcessId())
         .end();
    }
    xml.start("ram:GuidelineSpecifiedDocumentContextParameter")
       .element("ram:ID",
                order.profile()
                     .getSpecificationId())
       .end()
       .end()
       .start("rsm:ExchangedDocument")
       .element("ram:ID",
                order.number())
       .element("ram:Name",
                details.documentName())
       .element("ram:TypeCode",
                order.typeCode()
                     .getCode())
       .element("ram:StatusCode",
                order.statusCode());
    dateTime(xml,
             "ram:IssueDateTime",
             order.issueDate(),
             details.issueTime());
    if (details.copyIndicator() != null) {
      xml.start("ram:CopyIndicator")
         .element("udt:Indicator",
                  details.copyIndicator())
         .end();
    }
    xml.element("ram:PurposeCode",
                order.purposeCode())
       .element("ram:RequestedResponseTypeCode",
                order.requestedResponseTypeCode());
    for (var note : order.notes()) {
      xml.start("ram:IncludedNote")
         .element("ram:Content",
                  note.text())
         .element("ram:SubjectCode",
                  note.subjectCode())
         .end();
    }
    if (order.effectivePeriod() != null) {
      xml.start("ram:EffectiveSpecifiedPeriod");
      date(xml,
           "ram:StartDateTime",
           order.effectivePeriod()
                .startDate());
      date(xml,
           "ram:EndDateTime",
           order.effectivePeriod()
                .endDate());
      xml.end();
    }
    xml.end()
       .start("rsm:SupplyChainTradeTransaction");
    for (var line : order.lines()) {
      line(xml,
           line);
    }
    xml.start("ram:ApplicableHeaderTradeAgreement")
       .element("ram:BuyerReference",
                order.buyerReference());
    party(xml,
          "ram:SellerTradeParty",
          order.seller());
    party(xml,
          "ram:BuyerTradeParty",
          order.buyer());
    party(xml,
          "ram:BuyerRequisitionerTradeParty",
          order.buyerRequisitioner());
    if (order.deliveryTermsCode() != null || details.deliveryTermsDescription() != null || details.deliveryTermsFunctionCode() != null || details.deliveryTermsLocationId() != null || details.deliveryTermsLocationName() != null) {
      xml.start("ram:ApplicableTradeDeliveryTerms")
         .element("ram:DeliveryTypeCode",
                  order.deliveryTermsCode())
         .element("ram:Description",
                  details.deliveryTermsDescription())
         .element("ram:FunctionCode",
                  details.deliveryTermsFunctionCode());
      if (details.deliveryTermsLocationId() != null || details.deliveryTermsLocationName() != null) {
        xml.start("ram:RelevantTradeLocation")
           .element("ram:ID",
                    details.deliveryTermsLocationId())
           .element("ram:Name",
                    details.deliveryTermsLocationName())
           .end();
      }
      xml.end();
    }
    reference(xml,
              "ram:SellerOrderReferencedDocument",
              order.sellerOrderReference());
    reference(xml,
              "ram:BuyerOrderReferencedDocument",
              order.buyerOrderReference());
    reference(xml,
              "ram:QuotationReferencedDocument",
              order.quotationReference());
    reference(xml,
              "ram:ContractReferencedDocument",
              order.contractReference());
    reference(xml,
              "ram:RequisitionReferencedDocument",
              order.requisitionReference());
    for (var document : order.additionalDocuments()) {
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
    for (var document : details.referencedDocuments()) {
      document(xml,
               "ram:AdditionalReferencedDocument",
               document);
    }
    reference(xml,
              "ram:CatalogueReferencedDocument",
              order.catalogueReference());
    reference(xml,
              "ram:BlanketOrderReferencedDocument",
              order.blanketOrderReference());
    reference(xml,
              "ram:PreviousOrderReferencedDocument",
              order.previousOrderReference());
    reference(xml,
              "ram:PreviousOrderChangeReferencedDocument",
              order.previousOrderChangeReference());
    reference(xml,
              "ram:PreviousOrderResponseReferencedDocument",
              order.previousOrderResponseReference());
    if (order.projectReference() != null) {
      xml.start("ram:SpecifiedProcuringProject")
         .element("ram:ID",
                  order.projectReference())
         .element("ram:Name",
                  order.projectName())
         .end();
    }
    xml.end()
       .start("ram:ApplicableHeaderTradeDelivery");
    party(xml,
          "ram:ShipToTradeParty",
          order.shipTo());
    party(xml,
          "ram:ShipFromTradeParty",
          order.shipFrom());
    event(xml,
          "ram:RequestedDeliverySupplyChainEvent",
          order.requestedDeliveryDate(),
          details.requestedDeliveryTime(),
          details.requestedDeliveryPeriod());
    event(xml,
          "ram:RequestedDespatchSupplyChainEvent",
          order.requestedDespatchDate(),
          details.requestedDespatchTime(),
          details.requestedDespatchPeriod());
    xml.end()
       .start("ram:ApplicableHeaderTradeSettlement")
       .element("ram:OrderCurrencyCode",
                currency);
    party(xml,
          "ram:InvoiceeTradeParty",
          order.invoicee());
    if (order.paymentMeansCode() != null) {
      xml.start("ram:SpecifiedTradeSettlementPaymentMeans")
         .element("ram:TypeCode",
                  order.paymentMeansCode()
                       .getCode())
         .element("ram:Information",
                  order.paymentMeansText())
         .end();
    }
    for (var adjustment : order.adjustments()) {
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
                  adjustment.reason());
      tax(xml,
          "ram:CategoryTradeTax",
          adjustment.tax());
      xml.end();
    }
    if (order.paymentTerms() != null) {
      xml.start("ram:SpecifiedTradePaymentTerms")
         .element("ram:Description",
                  order.paymentTerms())
         .end();
    }
    boolean adjusted = !order.adjustments()
                             .isEmpty();
    // A response without lines states the totals of the order it answers.
    var stated = details.statedTotals();
    if (stated != null) {
      totals   = stated;
      adjusted = true;
    }
    xml.start("ram:SpecifiedTradeSettlementHeaderMonetarySummation")
       .element("ram:LineTotalAmount",
                totals.lineTotal())
       .element("ram:ChargeTotalAmount",
                adjusted ?
                totals.chargeTotal() :
                null)
       .element("ram:AllowanceTotalAmount",
                adjusted ?
                totals.allowanceTotal() :
                null)
       .element("ram:TaxBasisTotalAmount",
                totals.taxBasisTotal())
       .element("ram:TaxTotalAmount",
                totals.taxAmount(),
                "currencyID",
                currency)
       .element("ram:GrandTotalAmount",
                totals.grandTotal())
       .end();
    if (order.buyerAccountingReference() != null) {
      xml.start("ram:ReceivableSpecifiedTradeAccountingAccount")
         .element("ram:ID",
                  order.buyerAccountingReference())
         .end();
    }
    xml.end()
       .end();
    return xml.toXml();
  }

  /**
   * Writes the order as UTF-8 encoded XML.
   *
   * @param order the order
   * @return the UTF-8 encoded XML document
   * @throws IllegalArgumentException if the order contains content this syntax cannot carry
   */
  public byte[] toBytes(Order order) {
    return toXml(order).getBytes(StandardCharsets.UTF_8);
  }
}

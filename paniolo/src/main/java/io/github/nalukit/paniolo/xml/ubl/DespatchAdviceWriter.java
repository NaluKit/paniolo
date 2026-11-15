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

import io.github.nalukit.paniolo.model.common.Address;
import io.github.nalukit.paniolo.model.common.Contact;
import io.github.nalukit.paniolo.model.common.Party;
import io.github.nalukit.paniolo.model.despatch.DespatchAdvice;
import io.github.nalukit.paniolo.model.despatch.DespatchLine;
import io.github.nalukit.paniolo.xml.util.XmlBuilder;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Writes despatch advices as UBL 2.1 DespatchAdvice after Peppol BIS Despatch Advice 3, or with
 * other identifiers such as those of 1Lieferschein. Party details Peppol has no place for, such as
 * VAT identifiers, are rejected; content only Peppol forbids, such as the actual delivery date, is
 * written for other customizations. Stateless and thread-safe.
 */
public final class DespatchAdviceWriter {
  static final String UBL = "urn:oasis:names:specification:ubl:schema:xsd:";

  /**
   * Namespace of the root element {@code DespatchAdvice}.
   */
  public static final String NAMESPACE = UBL + "DespatchAdvice-2";

  /**
   * Creates a writer; it has no state.
   */
  public DespatchAdviceWriter() {
  }

  private static void checkParty(List<String> found,
                                 String role,
                                 Party party,
                                 Role kind) {
    if (party == null) {
      return;
    }
    String prefix = role + " ";
    for (Object[] unsupported : new Object[][] { { party.vatId(),
                                                   "VAT identifier" },
                                                 { party.taxId(),
                                                   "tax number" },
                                                 { party.tradingName(),
                                                   "trading name" },
                                                 { party.legalRegistrationId(),
                                                   "legal registration identifier" },
                                                 { party.additionalLegalInformation(),
                                                   "additional legal information" },
                                                 { party.extended(),
                                                   "EXTENDED party information" } }) {
      if (unsupported[0] != null) {
        found.add(prefix + unsupported[1]);
      }
    }
    if (!kind.endpoint && party.electronicAddress() != null) {
      found.add(prefix + "electronic address");
    }
    if (!kind.contact && party.contact() != null) {
      found.add(prefix + "contact");
    }
  }

  private static void party(XmlBuilder xml,
                            String role,
                            Party party,
                            Role kind) {
    if (party == null) {
      return;
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
    if (party.identifier() != null) {
      xml.start("cac:PartyIdentification")
         .element("cbc:ID",
                  party.identifier(),
                  "schemeID",
                  party.identifierSchemeId())
         .end();
    }
    if (kind == Role.OTHER) {
      xml.start("cac:PartyName")
         .element("cbc:Name",
                  party.name())
         .end();
    }
    address(xml,
            "cac:PostalAddress",
            party.address());
    if (kind != Role.OTHER) {
      xml.start("cac:PartyLegalEntity")
         .element("cbc:RegistrationName",
                  party.name())
         .end();
    }
    if (kind == Role.SUPPLIER) {
      contact(xml,
              "cac:Contact",
              party.contact());
    }
    xml.end();
    if (kind == Role.CUSTOMER) {
      contact(xml,
              "cac:DeliveryContact",
              party.contact());
    }
    xml.end();
  }

  private static void contact(XmlBuilder xml,
                              String name,
                              Contact contact) {
    if (contact != null) {
      xml.start(name)
         .element("cbc:Name",
                  contact.name())
         .element("cbc:Telephone",
                  contact.telephone())
         .element("cbc:ElectronicMail",
                  contact.email())
         .end();
    }
  }

  static void address(XmlBuilder xml,
                      String name,
                      Address address) {
    if (address == null) {
      return;
    }
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

  private static void shipment(XmlBuilder xml,
                               DespatchAdvice advice) {
    if (advice.shipmentId() == null) {
      return;
    }
    xml.start("cac:Shipment")
       .element("cbc:ID",
                advice.shipmentId())
       .element("cbc:Information",
                advice.shipmentInformation())
       .element("cbc:GrossWeightMeasure",
                advice.grossWeight(),
                "unitCode",
                advice.grossWeightUnit() == null ?
                null :
                advice.grossWeightUnit()
                      .getCode())
       .element("cbc:GrossVolumeMeasure",
                advice.grossVolume(),
                "unitCode",
                advice.grossVolumeUnit() == null ?
                null :
                advice.grossVolumeUnit()
                      .getCode())
       .element("cbc:TotalTransportHandlingUnitQuantity",
                advice.handlingUnitCount());
    if (advice.transportModeCode() != null) {
      xml.start("cac:ShipmentStage")
         .element("cbc:TransportModeCode",
                  advice.transportModeCode())
         .end();
    }
    var period = advice.estimatedDeliveryPeriod();
    if (advice.actualDeliveryDate() != null || advice.trackingId() != null || period != null || advice.actualDespatchDate() != null || advice.despatchAddress() != null) {
      xml.start("cac:Delivery")
         .element("cbc:ActualDeliveryDate",
                  advice.actualDeliveryDate())
         .element("cbc:TrackingID",
                  advice.trackingId());
      if (period != null) {
        xml.start("cac:EstimatedDeliveryPeriod")
           .element("cbc:StartDate",
                    period.startDate())
           .element("cbc:EndDate",
                    period.endDate())
           .end();
      }
      if (advice.actualDespatchDate() != null || advice.despatchAddress() != null) {
        xml.start("cac:Despatch")
           .element("cbc:ActualDespatchDate",
                    advice.actualDespatchDate());
        address(xml,
                "cac:DespatchAddress",
                advice.despatchAddress());
        xml.end();
      }
      xml.end();
    }
    xml.end();
  }

  private static void line(XmlBuilder xml,
                           DespatchLine line,
                           boolean peppol) {
    String unit = line.unitCode()
                      .getCode();
    xml.start("cac:DespatchLine")
       .element("cbc:ID",
                line.id())
       .element("cbc:Note",
                line.note())
       .element("cbc:DeliveredQuantity",
                line.deliveredQuantity(),
                "unitCode",
                unit)
       .element("cbc:OutstandingQuantity",
                line.outstandingQuantity(),
                "unitCode",
                unit)
       .element("cbc:OutstandingReason",
                line.outstandingReason())
       .start("cac:OrderLineReference")
       .element("cbc:LineID",
                line.orderLineId())
       .element("cbc:SalesOrderLineID",
                line.salesOrderLineId());
    if (line.orderReference() != null) {
      xml.start("cac:OrderReference")
         .element("cbc:ID",
                  line.orderReference())
         .end();
    }
    xml.end()
       .start("cac:Item")
       .element("cbc:Name",
                line.itemName());
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
    for (var classification : line.classifications()) {
      xml.start("cac:CommodityClassification")
         .element("cbc:ItemClassificationCode",
                  classification.code(),
                  "listID",
                  classification.schemeId(),
                  "listVersionID",
                  classification.schemeVersion(),
                  "name",
                  classification.className())
         .end();
    }
    for (var attribute : line.attributes()) {
      xml.start("cac:AdditionalItemProperty")
         .element("cbc:Name",
                  attribute.name())
         .element("cbc:Value",
                  attribute.value())
         .end();
    }
    for (var instance : line.instances()) {
      xml.start("cac:ItemInstance")
         .element("cbc:ManufactureDate",
                  instance.manufactureDate())
         .element("cbc:BestBeforeDate",
                  instance.bestBeforeDate())
         .element("cbc:SerialID",
                  instance.serialId());
      if (instance.lotNumber() != null) {
        xml.start("cac:LotIdentification")
           .element("cbc:LotNumberID",
                    instance.lotNumber())
           .element("cbc:ExpiryDate",
                    instance.expiryDate())
           .end();
      }
      xml.end();
    }
    xml.end();
    // Peppol requires the line shipment; its identifier is fixed to NA. Other customizations write
    // it for the transport handling unit only.
    if (peppol || line.handlingUnitId() != null) {
      xml.start("cac:Shipment")
         .element("cbc:ID",
                  "NA");
      if (line.handlingUnitId() != null) {
        xml.start("cac:TransportHandlingUnit")
           .element("cbc:ID",
                    line.handlingUnitId())
           .element("cbc:TransportHandlingUnitTypeCode",
                    line.handlingUnitTypeCode())
           .end();
      }
      xml.end();
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

  /**
   * Writes the despatch advice.
   *
   * @param advice the despatch advice
   * @return the XML document
   * @throws IllegalArgumentException if the despatch advice contains content this syntax cannot carry
   */
  public String toXml(DespatchAdvice advice) {
    Objects.requireNonNull(advice,
                           "advice");
    var found = new ArrayList<String>();
    checkParty(found,
               "despatch supplier",
               advice.despatchSupplier(),
               Role.SUPPLIER);
    checkParty(found,
               "delivery customer",
               advice.deliveryCustomer(),
               Role.CUSTOMER);
    checkParty(found,
               "buyer",
               advice.buyer(),
               Role.OTHER);
    checkParty(found,
               "seller",
               advice.seller(),
               Role.OTHER);
    checkParty(found,
               "originator",
               advice.originator(),
               Role.OTHER);
    if (advice.isPeppol() && advice.actualDeliveryDate() != null) {
      found.add("actual delivery date");
    }
    if (advice.hasShipmentDetails() && advice.shipmentId() == null) {
      found.add("shipment details without the shipment id");
    }
    for (var line : advice.lines()) {
      if (line.orderLineId() == null) {
        found.add("line " + line.id() + " without the order line id");
      }
    }
    if (!found.isEmpty()) {
      throw new IllegalArgumentException((advice.isPeppol() ?
                                          "A Peppol despatch advice has no place for: " :
                                          "A UBL despatch advice has no place for: ") +
                                         String.join(", ",
                                                     found));
    }
    var xml = new XmlBuilder("DespatchAdvice",
                             Map.of("",
                                    NAMESPACE,
                                    "cbc",
                                    UBL + "CommonBasicComponents-2",
                                    "cac",
                                    UBL + "CommonAggregateComponents-2"));
    xml.element("cbc:CustomizationID",
                advice.customizationId())
       .element("cbc:ProfileID",
                advice.profileId())
       .element("cbc:ID",
                advice.number())
       .element("cbc:IssueDate",
                advice.issueDate())
       .element("cbc:Note",
                advice.note());
    for (String order : advice.orderReferences()) {
      xml.start("cac:OrderReference")
         .element("cbc:ID",
                  order)
         .end();
    }
    for (var document : advice.additionalDocuments()) {
      xml.start("cac:AdditionalDocumentReference")
         .element("cbc:ID",
                  document.reference())
         .element("cbc:DocumentType",
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
    party(xml,
          "cac:DespatchSupplierParty",
          advice.despatchSupplier(),
          Role.SUPPLIER);
    party(xml,
          "cac:DeliveryCustomerParty",
          advice.deliveryCustomer(),
          Role.CUSTOMER);
    party(xml,
          "cac:BuyerCustomerParty",
          advice.buyer(),
          Role.OTHER);
    party(xml,
          "cac:SellerSupplierParty",
          advice.seller(),
          Role.OTHER);
    party(xml,
          "cac:OriginatorCustomerParty",
          advice.originator(),
          Role.OTHER);
    shipment(xml,
             advice);
    for (var line : advice.lines()) {
      line(xml,
           line,
           advice.isPeppol());
    }
    return xml.toXml();
  }

  /**
   * Writes the despatch advice as UTF-8 encoded XML.
   *
   * @param advice the despatch advice
   * @return the UTF-8 encoded XML document
   * @throws IllegalArgumentException if the despatch advice contains content this syntax cannot carry
   */
  public byte[] toBytes(DespatchAdvice advice) {
    return toXml(advice).getBytes(StandardCharsets.UTF_8);
  }

  /**
   * Party roles and the details each can carry.
   */
  private enum Role {
    SUPPLIER(true,
             true),
    CUSTOMER(true,
             true),
    OTHER(false,
          false);

    private final boolean endpoint;
    private final boolean contact;

    Role(boolean endpoint,
         boolean contact) {
      this.endpoint = endpoint;
      this.contact  = contact;
    }
  }
}

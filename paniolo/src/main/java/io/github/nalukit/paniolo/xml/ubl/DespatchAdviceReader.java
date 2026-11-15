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

import io.github.nalukit.paniolo.model.common.AdditionalSupportingDocument;
import io.github.nalukit.paniolo.model.common.Attachment;
import io.github.nalukit.paniolo.model.common.Contact;
import io.github.nalukit.paniolo.model.common.ElectronicAddress;
import io.github.nalukit.paniolo.model.common.InvoicePeriod;
import io.github.nalukit.paniolo.model.common.ItemClassification;
import io.github.nalukit.paniolo.model.common.Party;
import io.github.nalukit.paniolo.model.common.value.UnitCode;
import io.github.nalukit.paniolo.model.despatch.DespatchAdvice;
import io.github.nalukit.paniolo.model.despatch.DespatchItemInstance;
import io.github.nalukit.paniolo.model.despatch.DespatchLine;
import io.github.nalukit.paniolo.xml.util.ContentComparison;
import io.github.nalukit.paniolo.xml.util.XmlDocuments;
import io.github.nalukit.paniolo.xml.util.XmlElement;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;

/**
 * Reads UBL 2.1 DespatchAdvice documents into the despatch advice model. Reading is strict by
 * default: content the model would lose is rejected with its path; {@link #lenient()} ignores it.
 * Stateless and thread-safe.
 */
public final class DespatchAdviceReader {
  private static final Map<String, String> NAMESPACES = Map.of("da",
                                                               DespatchAdviceWriter.NAMESPACE,
                                                               "cbc",
                                                               DespatchAdviceWriter.UBL + "CommonBasicComponents-2",
                                                               "cac",
                                                               DespatchAdviceWriter.UBL + "CommonAggregateComponents-2");

  private final boolean strict;

  /**
   * A strict reader.
   */
  public DespatchAdviceReader() {
    this(true);
  }

  private DespatchAdviceReader(boolean strict) {
    this.strict = strict;
  }

  /**
   * A reader that ignores content the despatch advice model does not support.
   *
   * @return the lenient reader
   */
  public static DespatchAdviceReader lenient() {
    return new DespatchAdviceReader(false);
  }

  private static Party party(XmlElement role) {
    if (role == null) {
      return null;
    }
    var party = role.find("cac:Party");
    if (party == null) {
      return null;
    }
    var    endpoint = party.find("cbc:EndpointID");
    var    id       = party.find("cac:PartyIdentification/cbc:ID");
    String name     = party.text("cac:PartyLegalEntity/cbc:RegistrationName");
    var    contact  = party.find("cac:Contact");
    if (contact == null) {
      contact = role.find("cac:DeliveryContact");
    }
    var builder = Party.create()
                       .name(name == null ?
                             party.text("cac:PartyName/cbc:Name") :
                             name)
                       .address(UblInvoiceReader.address(party.find("cac:PostalAddress")))
                       .electronicAddress(endpoint == null ?
                                          null :
                                          new ElectronicAddress(endpoint.text(),
                                                                endpoint.attribute("schemeID")))
                       .contact(contact == null ?
                                null :
                                new Contact(contact.text("cbc:Name"),
                                            contact.text("cbc:Telephone"),
                                            contact.text("cbc:ElectronicMail")));
    if (id != null) {
      builder.identifier(id.text(),
                         id.attribute("schemeID"));
    }
    return builder.build();
  }

  private static UnitCode unit(XmlElement quantity) {
    return quantity == null ?
           null :
           XmlElement.code(quantity.attribute("unitCode"),
                           UnitCode::of,
                           "unit code");
  }

  private static DespatchLine line(XmlElement line) {
    var delivered   = line.find("cbc:DeliveredQuantity");
    var outstanding = line.find("cbc:OutstandingQuantity");
    var item        = line.find("cac:Item");
    if (delivered == null || item == null) {
      throw new IllegalArgumentException("Despatch line without delivered quantity or item");
    }
    var standard = item.find("cac:StandardItemIdentification/cbc:ID");
    var builder = DespatchLine.create()
                              .id(line.text("cbc:ID"))
                              .note(line.text("cbc:Note"))
                              .deliveredQuantity(delivered.decimal())
                              .unitCode(unit(delivered))
                              .outstandingQuantity(outstanding == null ?
                                                   null :
                                                   outstanding.decimal())
                              .outstandingReason(line.text("cbc:OutstandingReason"))
                              .orderLineId(line.text("cac:OrderLineReference/cbc:LineID"))
                              .salesOrderLineId(line.text("cac:OrderLineReference/cbc:SalesOrderLineID"))
                              .orderReference(line.text("cac:OrderLineReference/cac:OrderReference/cbc:ID"))
                              .itemName(item.text("cbc:Name"))
                              .buyerItemId(item.text("cac:BuyersItemIdentification/cbc:ID"))
                              .sellerItemId(item.text("cac:SellersItemIdentification/cbc:ID"))
                              .handlingUnitId(line.text("cac:Shipment/cac:TransportHandlingUnit/cbc:ID"))
                              .handlingUnitTypeCode(line.text("cac:Shipment/cac:TransportHandlingUnit/cbc:TransportHandlingUnitTypeCode"));
    if (standard != null) {
      builder.standardItemId(standard.text(),
                             standard.attribute("schemeID"));
    }
    for (var code : item.all("cac:CommodityClassification/cbc:ItemClassificationCode")) {
      builder.addClassification(new ItemClassification(code.text(),
                                                       code.attribute("listID"),
                                                       code.attribute("listVersionID"),
                                                       code.attribute("name")));
    }
    for (var property : item.children("cac:AdditionalItemProperty")) {
      builder.addAttribute(property.text("cbc:Name"),
                           property.text("cbc:Value"));
    }
    for (var instance : item.children("cac:ItemInstance")) {
      builder.addInstance(new DespatchItemInstance(instance.text("cbc:SerialID"),
                                                   instance.text("cac:LotIdentification/cbc:LotNumberID"),
                                                   instance.isoDate("cac:LotIdentification/cbc:ExpiryDate"),
                                                   instance.isoDate("cbc:BestBeforeDate"),
                                                   instance.isoDate("cbc:ManufactureDate")));
    }
    return builder.build();
  }

  /**
   * Writes the despatch advice again and rejects the document if that loses content.
   */
  private static void checkComplete(Document document,
                                    DespatchAdvice advice) {
    Document copy;
    try {
      copy = XmlDocuments.parse(new StringReader(new DespatchAdviceWriter().toXml(advice)));
    } catch (IOException | SAXException e) {
      throw new IllegalStateException("Cannot compare the despatch advice with the document",
                                      e);
    }
    var missing = ContentComparison.missing(document,
                                            copy,
                                            10);
    if (!missing.isEmpty()) {
      throw new IllegalArgumentException("Despatch advice content not supported by the Paniolo model (read with DespatchAdviceReader.lenient() to ignore it): " +
                                         String.join("; ",
                                                     missing));
    }
  }

  /**
   * Reads XML text; the encoding named in the XML declaration is ignored.
   *
   * @param xml the XML document
   * @return the despatch advice
   * @throws IllegalArgumentException if the XML is not well-formed, not a UBL DespatchAdvice, or, when
   *                                  reading strictly, contains content the model does not carry
   */
  public DespatchAdvice fromXml(String xml) {
    try {
      return read(XmlDocuments.parse(new StringReader(xml)));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } catch (SAXException e) {
      throw new IllegalArgumentException("Despatch advice XML is not well-formed",
                                         e);
    }
  }

  /**
   * Reads encoded XML.
   *
   * @param xml the XML document
   * @return the despatch advice
   * @throws IllegalArgumentException if the XML is not well-formed, not a UBL DespatchAdvice, or, when
   *                                  reading strictly, contains content the model does not carry
   */
  public DespatchAdvice fromBytes(byte[] xml) {
    try {
      return read(XmlDocuments.parse(new ByteArrayInputStream(xml)));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } catch (SAXException e) {
      throw new IllegalArgumentException("Despatch advice XML is not well-formed",
                                         e);
    }
  }

  /**
   * Reads a parsed document.
   *
   * @param document the namespace-aware DOM document; must not be modified concurrently
   * @return the despatch advice
   * @throws IllegalArgumentException if the document is not a UBL DespatchAdvice, or, when reading
   *                                  strictly, contains content the model does not carry
   */
  public DespatchAdvice read(Document document) {
    Objects.requireNonNull(document,
                           "document");
    var root = XmlElement.of(document.getDocumentElement(),
                             NAMESPACES);
    if (!"DespatchAdvice".equals(root.localName()) || !DespatchAdviceWriter.NAMESPACE.equals(root.namespace())) {
      throw new IllegalArgumentException("Not a UBL DespatchAdvice: {" + root.namespace() + "}" + root.localName());
    }
    var builder = DespatchAdvice.create()
                                .customizationId(root.text("cbc:CustomizationID"))
                                .profileId(root.text("cbc:ProfileID"))
                                .number(root.text("cbc:ID"))
                                .issueDate(root.isoDate("cbc:IssueDate"))
                                .note(root.text("cbc:Note"))
                                .despatchSupplier(party(root.find("cac:DespatchSupplierParty")))
                                .deliveryCustomer(party(root.find("cac:DeliveryCustomerParty")))
                                .buyer(party(root.find("cac:BuyerCustomerParty")))
                                .seller(party(root.find("cac:SellerSupplierParty")))
                                .originator(party(root.find("cac:OriginatorCustomerParty")));
    for (var order : root.children("cac:OrderReference")) {
      builder.addOrderReference(order.text("cbc:ID"));
    }
    for (var reference : root.children("cac:AdditionalDocumentReference")) {
      var embedded = reference.find("cac:Attachment/cbc:EmbeddedDocumentBinaryObject");
      builder.addAdditionalDocument(new AdditionalSupportingDocument(reference.text("cbc:ID"),
                                                                     reference.text("cbc:DocumentType"),
                                                                     reference.text("cac:Attachment/cac:ExternalReference/cbc:URI"),
                                                                     embedded == null || embedded.text() == null ?
                                                                     null :
                                                                     new Attachment(embedded.base64(),
                                                                                    embedded.attribute("mimeCode"),
                                                                                    embedded.attribute("filename"))));
    }
    var shipment = root.find("cac:Shipment");
    if (shipment != null) {
      var weight   = shipment.find("cbc:GrossWeightMeasure");
      var volume   = shipment.find("cbc:GrossVolumeMeasure");
      var delivery = shipment.find("cac:Delivery");
      builder.shipmentId(shipment.text("cbc:ID"))
             .shipmentInformation(shipment.text("cbc:Information"))
             .grossWeight(weight == null ?
                          null :
                          weight.decimal())
             .grossWeightUnit(unit(weight))
             .grossVolume(volume == null ?
                          null :
                          volume.decimal())
             .grossVolumeUnit(unit(volume))
             .handlingUnitCount(shipment.decimal("cbc:TotalTransportHandlingUnitQuantity"))
             .transportModeCode(shipment.text("cac:ShipmentStage/cbc:TransportModeCode"));
      if (delivery != null) {
        var period = delivery.find("cac:EstimatedDeliveryPeriod");
        builder.actualDeliveryDate(delivery.isoDate("cbc:ActualDeliveryDate"))
               .trackingId(delivery.text("cbc:TrackingID"))
               .estimatedDeliveryPeriod(period == null ?
                                        null :
                                        new InvoicePeriod(period.isoDate("cbc:StartDate"),
                                                          period.isoDate("cbc:EndDate")))
               .actualDespatchDate(delivery.isoDate("cac:Despatch/cbc:ActualDespatchDate"))
               .despatchAddress(UblInvoiceReader.address(delivery.find("cac:Despatch/cac:DespatchAddress")));
      }
    }
    for (var line : root.children("cac:DespatchLine")) {
      builder.addLine(line(line));
    }
    var advice = builder.build();
    if (strict) {
      checkComplete(document,
                    advice);
    }
    return advice;
  }
}

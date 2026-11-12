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
 * Writes despatch advices as UN/CEFACT Cross Industry Despatch Advice (CIDA,
 * {@code SCRDMCCBDACIDAMessageStructure}), the XML of the digital delivery note of the AWV pilot
 * that the Mustang project embeds as {@code cida.xml}. No schema of CIDA is published; namespaces,
 * guideline identifier, and structure follow the Mustang project, the elements beyond its subset
 * follow Order-X (SCRDM D20B).
 *
 * <p>CIDA carries the document header, the parties, the order reference, supporting documents, the
 * despatch date, and the lines with quantity, item, and serial and lot numbers. Content without a
 * CIDA element, such as shipment details or outstanding quantities, is rejected and named instead
 * of being dropped. Stateless and thread-safe.
 */
public final class CiiDespatchAdviceWriter {
  /**
   * Namespace of the root element {@code SCRDMCCBDACIDAMessageStructure}.
   */
  public static final String RSM       = "urn:un:unece:uncefact:data:standard:SCRDMCCBDACIDAMessageStructure:1";
  /**
   * Guideline identifier of the AWV pilot of the digital delivery note.
   */
  public static final String GUIDELINE = "urn:awv-net.de:CIDA:1.0:pilot";
  /**
   * UNTDID 1001 document type of a despatch advice.
   */
  static final        String TYPE_CODE = "351";

  static final Map<String, String> NAMESPACES = Map.of("rsm",
                                                       RSM,
                                                       "ram",
                                                       "urn:un:unece:uncefact:data:standard:ReusableAggregateBusinessInformationEntity:101",
                                                       "udt",
                                                       "urn:un:unece:uncefact:data:standard:UnqualifiedDataType:25");

  /**
   * Creates a writer; it has no state.
   */
  public CiiDespatchAdviceWriter() {
  }

  /**
   * Names every field CIDA has no element for.
   */
  private static void check(DespatchAdvice advice) {
    var found = new ArrayList<String>();
    if (!DespatchAdvice.PEPPOL_CUSTOMIZATION.equals(advice.customizationId()) || !DespatchAdvice.PEPPOL_PROFILE.equals(advice.profileId())) {
      found.add("customization and profile identifiers other than Peppol (UBL only)");
    }
    if (advice.orderReferences()
              .size() > 1) {
      found.add("more than one order reference");
    }
    for (Object[] unsupported : new Object[][] { { advice.shipmentId(),
                                                   "shipment id" },
                                                 { advice.shipmentInformation(),
                                                   "shipment information" },
                                                 { advice.grossWeight(),
                                                   "gross weight" },
                                                 { advice.grossVolume(),
                                                   "gross volume" },
                                                 { advice.handlingUnitCount(),
                                                   "handling unit count" },
                                                 { advice.transportModeCode(),
                                                   "transport mode" },
                                                 { advice.trackingId(),
                                                   "tracking id" },
                                                 { advice.estimatedDeliveryPeriod(),
                                                   "estimated delivery period" },
                                                 { advice.actualDeliveryDate(),
                                                   "actual delivery date" },
                                                 { advice.despatchAddress(),
                                                   "despatch address" } }) {
      add(found,
          unsupported[0],
          (String) unsupported[1]);
    }
    for (var line : advice.lines()) {
      String prefix = "line " + line.id() + " ";
      add(found,
          line.outstandingQuantity(),
          prefix + "outstanding quantity");
      add(found,
          line.salesOrderLineId(),
          prefix + "sales order line");
      add(found,
          line.handlingUnitId(),
          prefix + "handling unit");
      for (var instance : line.instances()) {
        if (instance.expiryDate() != null || instance.bestBeforeDate() != null || instance.manufactureDate() != null) {
          found.add(prefix + "item instance dates");
        }
      }
    }
    if (!found.isEmpty()) {
      throw new IllegalArgumentException("A CIDA despatch advice has no place for: " +
                                         String.join(", ",
                                                     found.stream()
                                                          .distinct()
                                                          .toList()));
    }
  }

  private static void add(List<String> found,
                          Object value,
                          String name) {
    if (value != null) {
      found.add(name);
    }
  }

  private static void line(XmlBuilder xml,
                           DespatchLine line) {
    xml.start("ram:IncludedSupplyChainTradeLineItem")
       .start("ram:AssociatedDocumentLineDocument")
       .element("ram:LineID",
                line.id());
    if (line.note() != null) {
      xml.start("ram:IncludedNote")
         .element("ram:Content",
                  line.note())
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
                line.itemName());
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
                  instance.lotNumber())
         .element("ram:SerialID",
                  instance.serialId())
         .end();
    }
    xml.end();
    if (line.orderLineId() != null || line.orderReference() != null) {
      xml.start("ram:SpecifiedLineTradeAgreement")
         .start("ram:BuyerOrderReferencedDocument")
         .element("ram:IssuerAssignedID",
                  line.orderReference())
         .element("ram:LineID",
                  line.orderLineId())
         .end()
         .end();
    }
    xml.start("ram:SpecifiedLineTradeDelivery")
       .element("ram:DespatchedQuantity",
                line.deliveredQuantity(),
                "unitCode",
                line.unitCode()
                    .getCode())
       .end()
       .end();
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
    check(advice);
    var xml = new XmlBuilder("rsm:SCRDMCCBDACIDAMessageStructure",
                             NAMESPACES);
    // The Mustang project declares the guideline as business process.
    xml.start("rsm:ExchangedDocumentContext")
       .start("ram:BusinessProcessSpecifiedDocumentContextParameter")
       .element("ram:ID",
                GUIDELINE)
       .end()
       .end()
       .start("rsm:ExchangedDocument")
       .element("ram:ID",
                advice.number())
       .element("ram:TypeCode",
                TYPE_CODE);
    OrderXWriter.date(xml,
                      "ram:IssueDateTime",
                      advice.issueDate());
    if (advice.note() != null) {
      xml.start("ram:IncludedNote")
         .element("ram:Content",
                  advice.note())
         .end();
    }
    xml.end()
       .start("rsm:SupplyChainTradeTransaction");
    for (var line : advice.lines()) {
      line(xml,
           line);
    }
    // The seller is the despatch supplier unless another seller is named; the despatch supplier
    // then is the ship-from party.
    xml.start("ram:ApplicableHeaderTradeAgreement");
    OrderXWriter.party(xml,
                       "ram:SellerTradeParty",
                       advice.seller() == null ?
                       advice.despatchSupplier() :
                       advice.seller());
    OrderXWriter.party(xml,
                       "ram:BuyerTradeParty",
                       advice.buyer());
    OrderXWriter.party(xml,
                       "ram:BuyerRequisitionerTradeParty",
                       advice.originator());
    if (!advice.orderReferences()
               .isEmpty()) {
      xml.start("ram:BuyerOrderReferencedDocument")
         .element("ram:IssuerAssignedID",
                  advice.orderReferences()
                        .get(0))
         .end();
    }
    for (var document : advice.additionalDocuments()) {
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
    xml.end()
       .start("ram:ApplicableHeaderTradeDelivery");
    OrderXWriter.party(xml,
                       "ram:ShipToTradeParty",
                       advice.deliveryCustomer());
    if (advice.seller() != null) {
      OrderXWriter.party(xml,
                         "ram:ShipFromTradeParty",
                         advice.despatchSupplier());
    }
    OrderXWriter.event(xml,
                       "ram:ActualDespatchSupplyChainEvent",
                       advice.actualDespatchDate());
    xml.end()
       .end();
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
}

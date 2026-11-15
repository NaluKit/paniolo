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

import io.github.nalukit.paniolo.model.common.AdditionalSupportingDocument;
import io.github.nalukit.paniolo.model.common.Attachment;
import io.github.nalukit.paniolo.model.common.ItemClassification;
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
import java.util.Objects;

/**
 * Reads UN/CEFACT Cross Industry Despatch Advice (CIDA) documents into the despatch advice model.
 * The root element may also be unqualified, as the Mustang project writes it. By default reading is
 * strict: the advice is written again and compared with the document; content the model does not
 * carry, such as line amounts, is rejected and named. {@link #lenient()} skips that comparison.
 * Stateless and thread-safe.
 */
public final class CiiDespatchAdviceReader {
  private static final String ROOT = "SCRDMCCBDACIDAMessageStructure";

  private final boolean strict;

  /**
   * A strict reader.
   */
  public CiiDespatchAdviceReader() {
    this(true);
  }

  private CiiDespatchAdviceReader(boolean strict) {
    this.strict = strict;
  }

  /**
   * A reader that ignores content the despatch advice model does not support.
   *
   * @return the lenient reader
   */
  public static CiiDespatchAdviceReader lenient() {
    return new CiiDespatchAdviceReader(false);
  }

  /**
   * Whether the document element is a CIDA root, qualified or unqualified.
   *
   * @param namespace namespace URI of the document element, or null if unqualified
   * @param localName local name of the document element
   * @return true for a CIDA root
   */
  public static boolean isCida(String namespace,
                               String localName) {
    return ROOT.equals(localName) && (namespace == null || CiiDespatchAdviceWriter.RSM.equals(namespace));
  }

  /**
   * Writes the advice again and rejects the document if that loses content.
   */
  private static void checkComplete(Document document,
                                    DespatchAdvice advice) {
    Document copy;
    try {
      copy = XmlDocuments.parse(new StringReader(new CiiDespatchAdviceWriter().toXml(advice)));
    } catch (IOException | SAXException e) {
      throw new IllegalStateException("Cannot compare the despatch advice with the document",
                                      e);
    }
    var missing = ContentComparison.missing(document,
                                            copy,
                                            10);
    if (!missing.isEmpty()) {
      throw new IllegalArgumentException("CIDA content not supported by the Paniolo despatch advice model (read with CiiDespatchAdviceReader.lenient() to ignore it): " +
                                         String.join("; ",
                                                     missing));
    }
  }

  private static DespatchLine line(XmlElement item) {
    String id       = item.text("ram:AssociatedDocumentLineDocument/ram:LineID");
    var    quantity = item.find("ram:SpecifiedLineTradeDelivery/ram:DespatchedQuantity");
    var    order    = item.find("ram:SpecifiedLineTradeAgreement/ram:BuyerOrderReferencedDocument");
    if (quantity == null || quantity.text() == null) {
      throw new IllegalArgumentException("CIDA line " + id + " without despatched quantity");
    }
    var builder = DespatchLine.create()
                              .id(id)
                              .note(item.text("ram:AssociatedDocumentLineDocument/ram:IncludedNote/ram:Content"))
                              .deliveredQuantity(quantity.decimal())
                              .unitCode(XmlElement.code(quantity.attribute("unitCode"),
                                                        UnitCode::of,
                                                        "unit code"))
                              .orderLineId(order == null ?
                                           null :
                                           order.text("ram:LineID"))
                              .orderReference(order == null ?
                                              null :
                                              order.text("ram:IssuerAssignedID"));
    var product = item.find("ram:SpecifiedTradeProduct");
    if (product != null) {
      var standard = product.find("ram:GlobalID");
      builder.itemName(product.text("ram:Name"))
             .sellerItemId(product.text("ram:SellerAssignedID"))
             .buyerItemId(product.text("ram:BuyerAssignedID"));
      if (standard != null) {
        builder.standardItemId(standard.text(),
                               standard.attribute("schemeID"));
      }
      for (var characteristic : product.children("ram:ApplicableProductCharacteristic")) {
        builder.addAttribute(characteristic.text("ram:Description"),
                             characteristic.text("ram:Value"));
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
      for (var instance : product.children("ram:IndividualTradeProductInstance")) {
        builder.addInstance(new DespatchItemInstance(instance.text("ram:SerialID"),
                                                     instance.text("ram:BatchID"),
                                                     null,
                                                     null,
                                                     null));
      }
    }
    return builder.build();
  }

  /**
   * Reads XML text; the encoding named in the XML declaration is ignored.
   *
   * @param xml the XML document
   * @return the despatch advice
   * @throws IllegalArgumentException if the XML is not well-formed, not a CIDA despatch advice, or, when
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
   * @throws IllegalArgumentException if the XML is not well-formed, not a CIDA despatch advice, or, when
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
   * @throws IllegalArgumentException if the document is not a CIDA despatch advice, or, when reading
   *                                  strictly, contains content the model does not carry
   */
  public DespatchAdvice read(Document document) {
    Objects.requireNonNull(document,
                           "document");
    var root = XmlElement.of(document.getDocumentElement(),
                             CiiDespatchAdviceWriter.NAMESPACES);
    if (!isCida(root.namespace(),
                root.localName())) {
      throw new IllegalArgumentException("Not a CIDA despatch advice: {" + root.namespace() + "}" + root.localName());
    }
    var header      = root.find("rsm:ExchangedDocument");
    var transaction = root.find("rsm:SupplyChainTradeTransaction");
    if (header == null || transaction == null) {
      throw new IllegalArgumentException("CIDA header or transaction is missing");
    }
    var agreement = transaction.find("ram:ApplicableHeaderTradeAgreement");
    var delivery  = transaction.find("ram:ApplicableHeaderTradeDelivery");
    if (agreement == null) {
      throw new IllegalArgumentException("CIDA header trade agreement is missing");
    }
    var seller = CiiInvoiceReader.party(agreement.find("ram:SellerTradeParty"));
    var buyer  = CiiInvoiceReader.party(agreement.find("ram:BuyerTradeParty"));
    var shipTo = delivery == null ?
                 null :
                 CiiInvoiceReader.party(delivery.find("ram:ShipToTradeParty"));
    var shipFrom = delivery == null ?
                   null :
                   CiiInvoiceReader.party(delivery.find("ram:ShipFromTradeParty"));
    // Without a ship-to party, as in documents of the Mustang project, the buyer receives the goods.
    var builder = DespatchAdvice.create()
                                .number(header.text("ram:ID"))
                                .issueDate(header.basicDate("ram:IssueDateTime/udt:DateTimeString"))
                                .note(header.text("ram:IncludedNote/ram:Content"))
                                .despatchSupplier(shipFrom == null ?
                                                  seller :
                                                  shipFrom)
                                .seller(shipFrom == null ?
                                        null :
                                        seller)
                                .buyer(buyer)
                                .deliveryCustomer(shipTo == null ?
                                                  buyer :
                                                  shipTo)
                                .originator(CiiInvoiceReader.party(agreement.find("ram:BuyerRequisitionerTradeParty")));
    String order = agreement.text("ram:BuyerOrderReferencedDocument/ram:IssuerAssignedID");
    if (order != null) {
      builder.addOrderReference(order);
    }
    for (var referenced : agreement.children("ram:AdditionalReferencedDocument")) {
      var embedded = referenced.find("ram:AttachmentBinaryObject");
      builder.addAdditionalDocument(new AdditionalSupportingDocument(referenced.text("ram:IssuerAssignedID"),
                                                                     referenced.text("ram:Name"),
                                                                     referenced.text("ram:URIID"),
                                                                     embedded == null || embedded.text() == null ?
                                                                     null :
                                                                     new Attachment(embedded.base64(),
                                                                                    embedded.attribute("mimeCode"),
                                                                                    embedded.attribute("filename"))));
    }
    if (delivery != null) {
      builder.actualDespatchDate(delivery.basicDate("ram:ActualDespatchSupplyChainEvent/ram:OccurrenceDateTime/udt:DateTimeString"));
    }
    for (var item : transaction.children("ram:IncludedSupplyChainTradeLineItem")) {
      builder.addLine(line(item));
    }
    var advice = builder.build();
    if (strict) {
      checkComplete(document,
                    advice);
    }
    return advice;
  }
}

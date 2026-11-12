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
import io.github.nalukit.paniolo.model.common.DocumentAdjustment;
import io.github.nalukit.paniolo.model.common.InvoiceNote;
import io.github.nalukit.paniolo.model.common.InvoicePeriod;
import io.github.nalukit.paniolo.model.common.ItemClassification;
import io.github.nalukit.paniolo.model.common.LineAdjustment;
import io.github.nalukit.paniolo.model.common.Party;
import io.github.nalukit.paniolo.model.common.TaxCategory;
import io.github.nalukit.paniolo.model.common.value.Country;
import io.github.nalukit.paniolo.model.common.value.CurrencyCode;
import io.github.nalukit.paniolo.model.common.value.DutyTaxFeeCategory;
import io.github.nalukit.paniolo.model.common.value.PaymentMeansCode;
import io.github.nalukit.paniolo.model.common.value.UnitCode;
import io.github.nalukit.paniolo.model.extended.DocumentReference;
import io.github.nalukit.paniolo.model.extended.ProductCharacteristic;
import io.github.nalukit.paniolo.model.extended.ProductInstance;
import io.github.nalukit.paniolo.model.order.Order;
import io.github.nalukit.paniolo.model.order.OrderDetails;
import io.github.nalukit.paniolo.model.order.OrderDocument;
import io.github.nalukit.paniolo.model.order.OrderLine;
import io.github.nalukit.paniolo.model.order.OrderLineDetails;
import io.github.nalukit.paniolo.model.order.OrderPeriod;
import io.github.nalukit.paniolo.model.order.OrderTotals;
import io.github.nalukit.paniolo.model.order.OrderTypeCode;
import io.github.nalukit.paniolo.model.order.OrderXProfile;
import io.github.nalukit.paniolo.model.order.PackageDimensions;
import io.github.nalukit.paniolo.model.order.PriceAdjustment;
import io.github.nalukit.paniolo.model.order.SubstitutedProduct;
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
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * Reads Order-X 1.0 documents into the order model. By default reading is strict: the order is
 * written again and compared with the document; content the model does not carry, such as an
 * unsupported element or a stated line amount that differs from quantity x price, is rejected and
 * named, instead of being lost silently. {@link #lenient()} skips that comparison. Stateless and
 * thread-safe.
 */
public final class OrderXReader {
  private final boolean strict;

  /**
   * A strict reader.
   */
  public OrderXReader() {
    this(true);
  }

  private OrderXReader(boolean strict) {
    this.strict = strict;
  }

  /**
   * A reader that ignores content the order model does not support.
   *
   * @return the lenient reader
   */
  public static OrderXReader lenient() {
    return new OrderXReader(false);
  }

  private static TaxCategory tax(XmlElement tax) {
    if (tax == null) {
      return null;
    }
    var code = XmlElement.code(tax.text("ram:CategoryCode"),
                               DutyTaxFeeCategory::of,
                               "VAT category");
    return new TaxCategory(code,
                           tax.decimal("ram:RateApplicablePercent"),
                           null,
                           null);
  }

  /**
   * A party with a global identifier next to {@code ram:ID} and, as Order-X allows, an electronic
   * address without scheme.
   */
  private static Party party(XmlElement party) {
    return CiiInvoiceReader.party(party,
                                  true);
  }

  private static DocumentReference reference(XmlElement reference) {
    // A line may reference a line of another document without its number.
    if (reference == null || reference.text("ram:IssuerAssignedID") == null && reference.text("ram:LineID") == null) {
      return null;
    }
    return new DocumentReference(reference.text("ram:IssuerAssignedID"),
                                 reference.text("ram:LineID"),
                                 null);
  }

  private static Boolean indicator(XmlElement element,
                                   String path) {
    String value = element.text(path);
    return value == null ?
           null :
           Boolean.parseBoolean(value);
  }

  /**
   * Writes the order again and rejects the document if that loses content.
   */
  private static void checkComplete(Document document,
                                    Order order) {
    Document copy;
    try {
      copy = XmlDocuments.parse(new StringReader(new OrderXWriter().toXml(order)));
    } catch (IOException | SAXException e) {
      throw new IllegalStateException("Cannot compare the order with the document",
                                      e);
    }
    var missing = ContentComparison.missing(document,
                                            copy,
                                            10);
    if (!missing.isEmpty()) {
      throw new IllegalArgumentException("Order-X content not supported by the Paniolo order model (read with OrderXReader.lenient() to ignore it): " +
                                         String.join("; ",
                                                     missing));
    }
  }

  private static DateTime dateTime(XmlElement element,
                                   String path) {
    var found = element == null ?
                null :
                element.find(path);
    LocalDate date = element == null ?
                     null :
                     element.dateOfDateTime(path);
    if (date == null) {
      return new DateTime(null,
                          null);
    }
    String text = found.text();
    LocalTime time = text.length() <= 8 ?
                     null :
                     LocalTime.parse(text.substring(8),
                                     DateTimeFormatter.ofPattern(text.length() == 12 ?
                                                                 "HHmm" :
                                                                 "HHmmss"));
    return new DateTime(date,
                        time);
  }

  private static OrderPeriod period(XmlElement event) {
    var period = event == null ?
                 null :
                 event.find("ram:OccurrenceSpecifiedPeriod");
    if (period == null) {
      return null;
    }
    var start = dateTime(period,
                         "ram:StartDateTime/udt:DateTimeString");
    var end = dateTime(period,
                       "ram:EndDateTime/udt:DateTimeString");
    return start.date() == null && end.date() == null ?
           null :
           new OrderPeriod(start.date(),
                           start.time(),
                           end.date(),
                           end.time());
  }

  private static OrderDocument document(XmlElement document) {
    return new OrderDocument(document.text("ram:IssuerAssignedID"),
                             document.text("ram:URIID"),
                             document.text("ram:LineID"),
                             document.text("ram:TypeCode"),
                             document.text("ram:Name"),
                             document.text("ram:ReferenceTypeCode"));
  }

  private static OrderLine line(XmlElement item) {
    var product    = item.find("ram:SpecifiedTradeProduct");
    var agreement  = item.find("ram:SpecifiedLineTradeAgreement");
    var delivery   = item.find("ram:SpecifiedLineTradeDelivery");
    var settlement = item.find("ram:SpecifiedLineTradeSettlement");
    var quantity = delivery == null ?
                   null :
                   delivery.find("ram:RequestedQuantity");
    var price = agreement == null ?
                null :
                agreement.find("ram:NetPriceProductTradePrice");
    if (quantity == null || price == null) {
      throw new IllegalArgumentException("Order-X line without requested quantity or net price");
    }
    BigDecimal base = price.decimal("ram:BasisQuantity");
    var details = OrderLineDetails.create()
                                  .noteSubjectCode(item.text("ram:AssociatedDocumentLineDocument/ram:IncludedNote/ram:SubjectCode"))
                                  .requestedDeliveryPeriod(period(delivery.find("ram:RequestedDeliverySupplyChainEvent")))
                                  .requestedDespatchPeriod(period(delivery.find("ram:RequestedDespatchSupplyChainEvent")));
    var packageQuantity = delivery.find("ram:PackageQuantity");
    // XPK is the unit the writer uses by default.
    if (packageQuantity != null && packageQuantity.attribute("unitCode") != null && !"XPK".equals(packageQuantity.attribute("unitCode"))) {
      details.packageQuantityUnit(XmlElement.code(packageQuantity.attribute("unitCode"),
                                                  UnitCode::of,
                                                  "unit code"));
    }
    for (var document : agreement.children("ram:AdditionalReferencedDocument")) {
      details.addDocument(document(document));
    }
    var builder = OrderLine.create()
                           .id(item.text("ram:AssociatedDocumentLineDocument/ram:LineID"))
                           .statusCode(item.text("ram:AssociatedDocumentLineDocument/ram:LineStatusCode"))
                           .note(item.text("ram:AssociatedDocumentLineDocument/ram:IncludedNote/ram:Content"))
                           .quantity(quantity.decimal())
                           .unitCode(XmlElement.code(quantity.attribute("unitCode"),
                                                     UnitCode::of,
                                                     "unit code"))
                           .agreedQuantity(delivery.decimal("ram:AgreedQuantity"))
                           .partialDeliveryAllowed(indicator(delivery,
                                                             "ram:PartialDeliveryAllowedIndicator/udt:Indicator"))
                           .requestedDeliveryDate(delivery.dateOfDateTime("ram:RequestedDeliverySupplyChainEvent/ram:OccurrenceDateTime/udt:DateTimeString"))
                           .netUnitPrice(price.decimal("ram:ChargeAmount"))
                           .priceBaseQuantity(base == null ?
                                              BigDecimal.ONE :
                                              base)
                           .grossPrice(agreement.decimal("ram:GrossPriceProductTradePrice/ram:ChargeAmount"));
    if (product != null) {
      var standard = product.find("ram:GlobalID");
      builder.name(product.text("ram:Name"))
             .description(product.text("ram:Description"))
             .sellerItemId(product.text("ram:SellerAssignedID"))
             .buyerItemId(product.text("ram:BuyerAssignedID"))
             .batchId(product.text("ram:BatchID"))
             .brandName(product.text("ram:BrandName"))
             .packagingTypeCode(product.text("ram:ApplicableSupplyChainPackaging/ram:TypeCode"))
             .originCountry(XmlElement.code(product.text("ram:OriginTradeCountry/ram:ID"),
                                            Country::of,
                                            "country"));
      if (standard != null) {
        builder.standardItemId(standard.text(),
                               standard.attribute("schemeID"));
      }
      for (var characteristic : product.children("ram:ApplicableProductCharacteristic")) {
        if (characteristic.text("ram:TypeCode") == null) {
          builder.addAttribute(characteristic.text("ram:Description"),
                               characteristic.text("ram:Value"));
        } else {
          builder.addCharacteristic(new ProductCharacteristic(characteristic.text("ram:TypeCode"),
                                                              characteristic.text("ram:Description"),
                                                              characteristic.text("ram:Value"),
                                                              null,
                                                              null));
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
      for (var instance : product.children("ram:IndividualTradeProductInstance")) {
        builder.addInstance(new ProductInstance(instance.text("ram:BatchID"),
                                                instance.text("ram:SerialID")));
      }
      var dimension = product.find("ram:ApplicableSupplyChainPackaging/ram:LinearSpatialDimension");
      if (dimension != null) {
        details.packageDimensions(new PackageDimensions(dimension.decimal("ram:WidthMeasure"),
                                                        unit(dimension.find("ram:WidthMeasure")),
                                                        dimension.decimal("ram:LengthMeasure"),
                                                        unit(dimension.find("ram:LengthMeasure")),
                                                        dimension.decimal("ram:HeightMeasure"),
                                                        unit(dimension.find("ram:HeightMeasure"))));
      }
      for (var document : product.children("ram:AdditionalReferenceReferencedDocument")) {
        details.addProductDocument(document(document));
      }
    }
    var substitute = item.find("ram:SubstitutedReferencedProduct");
    if (substitute != null) {
      var standard = substitute.find("ram:GlobalID");
      builder.substitutedProduct(new SubstitutedProduct(substitute.text("ram:Name"),
                                                        substitute.text("ram:SellerAssignedID"),
                                                        substitute.text("ram:BuyerAssignedID"),
                                                        standard == null ?
                                                        null :
                                                        standard.text(),
                                                        standard == null ?
                                                        null :
                                                        standard.attribute("schemeID"),
                                                        substitute.text("ram:Description")));
    }
    builder.buyerOrderReference(reference(agreement.find("ram:BuyerOrderReferencedDocument")))
           .quotationReference(reference(agreement.find("ram:QuotationReferencedDocument")))
           .catalogueReference(reference(agreement.find("ram:CatalogueReferencedDocument")))
           .blanketOrderReference(reference(agreement.find("ram:BlanketOrderReferencedDocument")))
           .priceDiscount(priceDiscount(agreement,
                                        details))
           .packageQuantity(delivery.decimal("ram:PackageQuantity"))
           .perPackageUnitQuantity(delivery.decimal("ram:PerPackageUnitQuantity"))
           .requestedDespatchDate(delivery.dateOfDateTime("ram:RequestedDespatchSupplyChainEvent/ram:OccurrenceDateTime/udt:DateTimeString"));
    if (settlement != null) {
      builder.tax(tax(settlement.find("ram:ApplicableTradeTax")))
             .buyerAccountingReference(settlement.text("ram:ReceivableSpecifiedTradeAccountingAccount/ram:ID"));
      for (var adjustment : settlement.children("ram:SpecifiedTradeAllowanceCharge")) {
        builder.addAdjustment(new LineAdjustment(Boolean.parseBoolean(adjustment.text("ram:ChargeIndicator/udt:Indicator")),
                                                 adjustment.decimal("ram:ActualAmount"),
                                                 adjustment.decimal("ram:BasisAmount"),
                                                 adjustment.decimal("ram:CalculationPercent"),
                                                 adjustment.text("ram:Reason"),
                                                 adjustment.text("ram:ReasonCode")));
      }
    }
    return builder.details(details.build())
                  .build();
  }

  private static UnitCode unit(XmlElement measure) {
    return measure == null ?
           null :
           XmlElement.code(measure.attribute("unitCode"),
                           UnitCode::of,
                           "unit code");
  }

  /**
   * A single allowance on the gross price with amount only is the price discount; any other
   * allowances and charges are price adjustments of the details.
   */
  private static BigDecimal priceDiscount(XmlElement agreement,
                                          OrderLineDetails.Builder details) {
    var applied = agreement.all("ram:GrossPriceProductTradePrice/ram:AppliedTradeAllowanceCharge");
    if (applied.size() == 1 &&
        !Boolean.parseBoolean(applied.get(0)
                                     .text("ram:ChargeIndicator/udt:Indicator")) &&
        applied.get(0)
               .text("ram:ReasonCode") == null &&
        applied.get(0)
               .text("ram:Reason") == null) {
      return applied.get(0)
                    .decimal("ram:ActualAmount");
    }
    for (var adjustment : applied) {
      details.addPriceAdjustment(new PriceAdjustment(Boolean.parseBoolean(adjustment.text("ram:ChargeIndicator/udt:Indicator")),
                                                     adjustment.decimal("ram:ActualAmount"),
                                                     adjustment.text("ram:ReasonCode"),
                                                     adjustment.text("ram:Reason")));
    }
    return null;
  }

  /**
   * Reads XML text; the encoding named in the XML declaration is ignored.
   *
   * @param xml the XML document
   * @return the order
   * @throws IllegalArgumentException if the XML is not well-formed, not an Order-X document, or, when
   *                                  reading strictly, contains content the model does not carry
   */
  public Order fromXml(String xml) {
    try {
      return read(XmlDocuments.parse(new StringReader(xml)));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } catch (SAXException e) {
      throw new IllegalArgumentException("Order XML is not well-formed",
                                         e);
    }
  }

  /**
   * Reads encoded XML.
   *
   * @param xml the XML document
   * @return the order
   * @throws IllegalArgumentException if the XML is not well-formed, not an Order-X document, or, when
   *                                  reading strictly, contains content the model does not carry
   */
  public Order fromBytes(byte[] xml) {
    try {
      return read(XmlDocuments.parse(new ByteArrayInputStream(xml)));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } catch (SAXException e) {
      throw new IllegalArgumentException("Order XML is not well-formed",
                                         e);
    }
  }

  /**
   * Reads a parsed document.
   *
   * @param document the namespace-aware DOM document; must not be modified concurrently
   * @return the order
   * @throws IllegalArgumentException if the document is not an Order-X document, or, when reading
   *                                  strictly, contains content the model does not carry
   */
  public Order read(Document document) {
    Objects.requireNonNull(document,
                           "document");
    var root = XmlElement.of(document.getDocumentElement(),
                             OrderXWriter.NAMESPACES);
    if (!"SCRDMCCBDACIOMessageStructure".equals(root.localName()) || !OrderXWriter.RSM.equals(root.namespace())) {
      throw new IllegalArgumentException("Not an Order-X document: {" + root.namespace() + "}" + root.localName());
    }
    var context     = root.find("rsm:ExchangedDocumentContext");
    var header      = root.find("rsm:ExchangedDocument");
    var transaction = root.find("rsm:SupplyChainTradeTransaction");
    if (context == null || header == null || transaction == null) {
      throw new IllegalArgumentException("Order-X document context, header or transaction is missing");
    }
    var agreement  = transaction.find("ram:ApplicableHeaderTradeAgreement");
    var delivery   = transaction.find("ram:ApplicableHeaderTradeDelivery");
    var settlement = transaction.find("ram:ApplicableHeaderTradeSettlement");
    if (agreement == null || settlement == null) {
      throw new IllegalArgumentException("Order-X header trade agreement or settlement is missing");
    }
    String guideline = context.text("ram:GuidelineSpecifiedDocumentContextParameter/ram:ID");
    var    profile   = OrderXProfile.ofSpecificationId(guideline);
    if (profile == null) {
      throw new IllegalArgumentException("Unknown Order-X profile: " + guideline);
    }
    var issue = dateTime(header,
                         "ram:IssueDateTime/udt:DateTimeString");
    var details = OrderDetails.create()
                              .testIndicator(indicator(context,
                                                       "ram:TestIndicator/udt:Indicator"))
                              .copyIndicator(indicator(header,
                                                       "ram:CopyIndicator/udt:Indicator"))
                              .businessProcessId(context.text("ram:BusinessProcessSpecifiedDocumentContextParameter/ram:ID"))
                              .documentName(header.text("ram:Name"))
                              .issueTime(issue.time())
                              .deliveryTermsDescription(agreement.text("ram:ApplicableTradeDeliveryTerms/ram:Description"))
                              .deliveryTermsFunctionCode(agreement.text("ram:ApplicableTradeDeliveryTerms/ram:FunctionCode"))
                              .deliveryTermsLocationId(agreement.text("ram:ApplicableTradeDeliveryTerms/ram:RelevantTradeLocation/ram:ID"))
                              .deliveryTermsLocationName(agreement.text("ram:ApplicableTradeDeliveryTerms/ram:RelevantTradeLocation/ram:Name"));
    var builder = Order.create()
                       .number(header.text("ram:ID"))
                       .issueDate(issue.date())
                       .typeCode(XmlElement.code(header.text("ram:TypeCode"),
                                                 OrderTypeCode::of,
                                                 "order type code"))
                       .profile(profile)
                       .statusCode(header.text("ram:StatusCode"))
                       .purposeCode(header.text("ram:PurposeCode"))
                       .requestedResponseTypeCode(header.text("ram:RequestedResponseTypeCode"))
                       .currencyCode(XmlElement.code(settlement.text("ram:OrderCurrencyCode"),
                                                     CurrencyCode::of,
                                                     "currency"))
                       .buyerReference(agreement.text("ram:BuyerReference"))
                       .seller(party(agreement.find("ram:SellerTradeParty")))
                       .buyer(party(agreement.find("ram:BuyerTradeParty")))
                       .invoicee(party(settlement.find("ram:InvoiceeTradeParty")))
                       .deliveryTermsCode(agreement.text("ram:ApplicableTradeDeliveryTerms/ram:DeliveryTypeCode"))
                       .sellerOrderReference(reference(agreement.find("ram:SellerOrderReferencedDocument")))
                       .buyerOrderReference(reference(agreement.find("ram:BuyerOrderReferencedDocument")))
                       .quotationReference(reference(agreement.find("ram:QuotationReferencedDocument")))
                       .contractReference(reference(agreement.find("ram:ContractReferencedDocument")))
                       .blanketOrderReference(reference(agreement.find("ram:BlanketOrderReferencedDocument")))
                       .previousOrderReference(reference(agreement.find("ram:PreviousOrderReferencedDocument")))
                       .previousOrderChangeReference(reference(agreement.find("ram:PreviousOrderChangeReferencedDocument")))
                       .previousOrderResponseReference(reference(agreement.find("ram:PreviousOrderResponseReferencedDocument")))
                       .paymentMeansCode(XmlElement.code(settlement.text("ram:SpecifiedTradeSettlementPaymentMeans/ram:TypeCode"),
                                                         PaymentMeansCode::of,
                                                         "payment means code"))
                       .paymentMeansText(settlement.text("ram:SpecifiedTradeSettlementPaymentMeans/ram:Information"))
                       .buyerRequisitioner(party(agreement.find("ram:BuyerRequisitionerTradeParty")))
                       .requisitionReference(reference(agreement.find("ram:RequisitionReferencedDocument")))
                       .catalogueReference(reference(agreement.find("ram:CatalogueReferencedDocument")))
                       .projectReference(agreement.text("ram:SpecifiedProcuringProject/ram:ID"))
                       .projectName(agreement.text("ram:SpecifiedProcuringProject/ram:Name"))
                       .paymentTerms(settlement.text("ram:SpecifiedTradePaymentTerms/ram:Description"))
                       .buyerAccountingReference(settlement.text("ram:ReceivableSpecifiedTradeAccountingAccount/ram:ID"));
    var effective = header.find("ram:EffectiveSpecifiedPeriod");
    if (effective != null) {
      builder.effectivePeriod(new InvoicePeriod(effective.dateOfDateTime("ram:StartDateTime/udt:DateTimeString"),
                                                effective.dateOfDateTime("ram:EndDateTime/udt:DateTimeString")));
    }
    for (var referenced : agreement.children("ram:AdditionalReferencedDocument")) {
      // The model's further documents are supporting documents (916); others keep their codes.
      String type = referenced.text("ram:TypeCode");
      if (type != null && !"916".equals(type) || referenced.text("ram:ReferenceTypeCode") != null || referenced.text("ram:LineID") != null) {
        details.addReferencedDocument(document(referenced));
        continue;
      }
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
    for (var note : header.children("ram:IncludedNote")) {
      builder.addNote(new InvoiceNote(note.text("ram:SubjectCode"),
                                      note.text("ram:Content")));
    }
    if (delivery != null) {
      var deliveryDate = dateTime(delivery,
                                  "ram:RequestedDeliverySupplyChainEvent/ram:OccurrenceDateTime/udt:DateTimeString");
      var despatchDate = dateTime(delivery,
                                  "ram:RequestedDespatchSupplyChainEvent/ram:OccurrenceDateTime/udt:DateTimeString");
      builder.shipTo(party(delivery.find("ram:ShipToTradeParty")))
             .shipFrom(party(delivery.find("ram:ShipFromTradeParty")))
             .requestedDeliveryDate(deliveryDate.date())
             .requestedDespatchDate(despatchDate.date());
      details.requestedDeliveryTime(deliveryDate.time())
             .requestedDespatchTime(despatchDate.time())
             .requestedDeliveryPeriod(period(delivery.find("ram:RequestedDeliverySupplyChainEvent")))
             .requestedDespatchPeriod(period(delivery.find("ram:RequestedDespatchSupplyChainEvent")));
    }
    for (var adjustment : settlement.children("ram:SpecifiedTradeAllowanceCharge")) {
      builder.addAdjustment(new DocumentAdjustment(Boolean.parseBoolean(adjustment.text("ram:ChargeIndicator/udt:Indicator")),
                                                   adjustment.decimal("ram:ActualAmount"),
                                                   adjustment.decimal("ram:BasisAmount"),
                                                   adjustment.decimal("ram:CalculationPercent"),
                                                   adjustment.text("ram:Reason"),
                                                   adjustment.text("ram:ReasonCode"),
                                                   tax(adjustment.find("ram:CategoryTradeTax"))));
    }
    for (var item : transaction.children("ram:IncludedSupplyChainTradeLineItem")) {
      builder.addLine(line(item));
    }
    var summation = settlement.find("ram:SpecifiedTradeSettlementHeaderMonetarySummation");
    if (summation != null &&
        transaction.children("ram:IncludedSupplyChainTradeLineItem")
                   .isEmpty()) {
      // A response without lines states the totals of the order it answers.
      var statedTotals = new OrderTotals(summation.decimal("ram:LineTotalAmount"),
                                         summation.decimal("ram:AllowanceTotalAmount"),
                                         summation.decimal("ram:ChargeTotalAmount"),
                                         summation.decimal("ram:TaxBasisTotalAmount"),
                                         summation.decimal("ram:TaxTotalAmount"),
                                         summation.decimal("ram:GrandTotalAmount"));
      if (!statedTotals.equals(builder.details(details.build())
                                      .build()
                                      .totals())) {
        details.statedTotals(statedTotals);
      }
    }
    var order = builder.details(details.build())
                       .build();
    BigDecimal stated = summation == null ||
                        order.details() != null &&
                        order.details()
                             .statedTotals() != null ?
                        null :
                        summation.decimal("ram:TaxBasisTotalAmount");
    // Lenient reading accepts stated totals the model does not reproduce, e.g. of a response without
    // lines or of allowances a BASIC document cannot carry.
    if (strict && stated != null && stated.compareTo(order.totals()
                                                          .taxBasisTotal()) != 0) {
      throw new IllegalArgumentException("Stated tax basis total " +
                                         stated.toPlainString() +
                                         " differs from calculated " +
                                         order.totals()
                                              .taxBasisTotal()
                                              .toPlainString() +
                                         "; the document may contain content the Paniolo order model does not support");
    }
    if (strict) {
      checkComplete(document,
                    order);
    }
    return order;
  }



  /**
   * Date and time of day of a date-time in format 102, 203, or 204; the time is null for 102.
   */
  private record DateTime(LocalDate date, LocalTime time) {
  }
}

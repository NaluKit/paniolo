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
package io.github.nalukit.paniolo.pdflayout;

import io.github.nalukit.paniolo.config.PdfLayout;
import io.github.nalukit.paniolo.model.common.DocumentAdjustment;
import io.github.nalukit.paniolo.model.common.InvoiceNote;
import io.github.nalukit.paniolo.model.common.LineAdjustment;
import io.github.nalukit.paniolo.model.common.Party;
import io.github.nalukit.paniolo.model.common.value.CurrencyCode;
import io.github.nalukit.paniolo.model.extended.DocumentReference;
import io.github.nalukit.paniolo.model.extended.ProductCharacteristic;
import io.github.nalukit.paniolo.model.extended.ProductInstance;
import io.github.nalukit.paniolo.model.order.Order;
import io.github.nalukit.paniolo.model.order.OrderLine;
import io.github.nalukit.paniolo.model.order.OrderPeriod;
import io.github.nalukit.paniolo.model.order.OrderTotals;
import io.github.nalukit.paniolo.model.order.OrderTypeCode;
import io.github.nalukit.paniolo.pdflayout.Canvas.Align;
import io.github.nalukit.paniolo.pdflayout.Canvas.Cell;
import io.github.nalukit.paniolo.pdflayout.Canvas.Column;
import io.github.nalukit.paniolo.pdflayout.Canvas.Row;
import org.apache.pdfbox.pdmodel.PDDocument;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Lays out an Order-X order, order change, or order response. The buyer sends orders and changes
 * to the seller; the seller sends the response to the buyer. A response shows the ordered and the
 * confirmed quantity and the status of each line.
 */
final class OrderRenderer
    extends DocumentRenderer {
  private final Order   order;
  private final boolean response;

  OrderRenderer(Order order,
                PdfLayout layout) {
    super(layout);
    this.order    = order;
    this.response = order.typeCode() == OrderTypeCode.ORDER_RESPONSE;
  }

  @Override
  Party sender() {
    return response ?
           order.seller() :
           order.buyer();
  }

  @Override
  Party recipient() {
    return response ?
           order.buyer() :
           order.seller();
  }

  @Override
  CurrencyCode currency() {
    return order.currencyCode();
  }

  @Override
  String documentTitle() {
    OrderTypeCode type = order.typeCode() == null ?
                         OrderTypeCode.ORDER :
                         order.typeCode();
    return join(" ",
                t.label("order.title." + type.getCode()),
                order.number());
  }

  @Override
  List<String[]> facts() {
    var rows = new ArrayList<String[]>();
    fact(rows,
         "order.number",
         order.number());
    fact(rows,
         "order.issueDate",
         t.date(order.issueDate()));
    if (response) {
      fact(rows,
           "order.order",
           reference(order.buyerOrderReference()));
      fact(rows,
           "order.status",
           t.optional("status.order." + order.statusCode()) != null ?
           code("status.order.",
                order.statusCode()) :
           code("status.line.",
                order.statusCode()));
    }
    fact(rows,
         "buyerReference",
         order.buyerReference());
    fact(rows,
         "order.sellerOrder",
         reference(order.sellerOrderReference()));
    fact(rows,
         "order.requestedDelivery",
         requested(order.requestedDeliveryDate(),
                   order.details() == null ?
                   null :
                   order.details()
                        .requestedDeliveryPeriod()));
    fact(rows,
         "order.requestedDespatch",
         requested(order.requestedDespatchDate(),
                   order.details() == null ?
                   null :
                   order.details()
                        .requestedDespatchPeriod()));
    fact(rows,
         "order.quotation",
         reference(order.quotationReference()));
    fact(rows,
         "order.contract",
         reference(order.contractReference()));
    Party buyer = order.buyer();
    if (buyer != null && !response) {
      fact(rows,
           "customerNumber",
           buyer.identifier());
    }
    return rows;
  }

  @Override
  void content(PDDocument document) {
    notes(order.notes()
               .stream()
               .map(InvoiceNote::text)
               .toList());
    lines();
    totals();
    parties(List.of(new Object[] { "party.shipTo",
                                   order.shipTo() },
                    new Object[] { "party.shipFrom",
                                   order.shipFrom() },
                    new Object[] { "party.invoicee",
                                   order.invoicee() },
                    new Object[] { "party.requisitioner",
                                   order.buyerRequisitioner() }));
    details();
    supportingDocuments(order.additionalDocuments());
  }

  // -- lines --------------------------------------------------------------------------------------

  private void lines() {
    var rows = new ArrayList<Row>();
    for (OrderLine line : order.lines()) {
      rows.add(row(line));
    }
    if (response) {
      float fixed = 26 + 42 + 46 + 44 + 62 + 66;
      table(List.of(new Column(26,
                               Align.LEFT),
                    new Column(canvas.width() - fixed,
                               Align.LEFT),
                    new Column(42,
                               Align.RIGHT),
                    new Column(46,
                               Align.RIGHT),
                    new Column(44,
                               Align.LEFT),
                    new Column(62,
                               Align.RIGHT),
                    new Column(66,
                               Align.RIGHT)),
            header("lines.pos",
                   "lines.item",
                   "lines.ordered",
                   "lines.confirmed",
                   "lines.unit",
                   "lines.price",
                   "lines.amount"),
            rows);
    } else {
      float fixed = 26 + 42 + 50 + 62 + 32 + 66;
      table(List.of(new Column(26,
                               Align.LEFT),
                    new Column(canvas.width() - fixed,
                               Align.LEFT),
                    new Column(42,
                               Align.RIGHT),
                    new Column(50,
                               Align.LEFT),
                    new Column(62,
                               Align.RIGHT),
                    new Column(32,
                               Align.RIGHT),
                    new Column(66,
                               Align.RIGHT)),
            header("lines.pos",
                   "lines.item",
                   "lines.quantity",
                   "lines.unit",
                   "lines.price",
                   "lines.vat",
                   "lines.amount"),
            rows);
    }
  }

  private Row row(OrderLine line) {
    var parts = new ArrayList<String>();
    add(parts,
        prefixed("line.sellerItemId",
                 line.sellerItemId()));
    add(parts,
        prefixed("line.buyerItemId",
                 line.buyerItemId()));
    add(parts,
        prefixed("line.standardItemId",
                 line.standardItemId()));
    add(parts,
        prefixed("line.brand",
                 line.brandName()));
    add(parts,
        prefixed("line.batch",
                 line.batchId()));
    if (line.originCountry() != null) {
      add(parts,
          t.label("line.origin") + " " + t.country(line.originCountry()));
    }
    for (ProductCharacteristic characteristic : line.characteristics()) {
      add(parts,
          join(": ",
               characteristic.description(),
               join(" ",
                    characteristic.value(),
                    characteristic.measure() == null ?
                    null :
                    t.number(characteristic.measure()) + " " + t.unit(characteristic.measureUnit()))));
    }
    for (ProductInstance instance : line.instances()) {
      add(parts,
          prefixed("line.batch",
                   instance.batchId()));
      add(parts,
          prefixed("line.serial",
                   instance.serialId()));
    }
    var delivery = new ArrayList<String>();
    add(delivery,
        prefixed("line.requestedDelivery",
                 t.date(line.requestedDeliveryDate())));
    add(delivery,
        prefixed("line.requestedDespatch",
                 t.date(line.requestedDespatchDate())));
    if (line.partialDeliveryAllowed() != null) {
      delivery.add(t.label(line.partialDeliveryAllowed() ?
                           "line.partialDelivery" :
                           "line.noPartialDelivery"));
    }
    if (line.packageQuantity() != null && line.perPackageUnitQuantity() != null) {
      delivery.add(t.label("line.packages",
                           t.number(line.packageQuantity()),
                           t.number(line.perPackageUnitQuantity()) + " " + t.unit(line.unitCode())));
    }
    add(delivery,
        prefixed("line.quotation",
                 reference(line.quotationReference())));
    add(delivery,
        prefixed("line.accountingReference",
                 line.buyerAccountingReference()));

    var item = new Cell().add(line.name(),
                              bodyBold)
                         .add(line.description(),
                              small.color(TEXT))
                         .add(details(parts,
                                      line.attributes()),
                              small)
                         .add(String.join(" · ",
                                          delivery),
                              small);
    if (line.statusCode() != null) {
      item.add(t.label("line.status") +
               ": " +
               code("status.line.",
                    line.statusCode()),
               line.statusCode()
                   .equals("7") ?
               smallStrong() :
               small.color(TEXT));
    }
    if (line.substitutedProduct() != null) {
      var substitute = line.substitutedProduct();
      item.add(t.label("line.substitute",
                       join(" · ",
                            substitute.name(),
                            substitute.sellerItemId(),
                            substitute.standardItemId())),
               small.color(TEXT));
    }
    if (line.grossPrice() != null && nonZero(line.priceDiscount())) {
      item.add(t.label("line.grossPrice",
                       money(line.grossPrice())) +
               " " +
               t.label("line.priceDiscount",
                       money(line.priceDiscount())),
               small);
    }
    for (LineAdjustment adjustment : line.adjustments()) {
      item.add(adjustmentText("line",
                              adjustment.charge(),
                              adjustment.reason(),
                              adjustment.percentage(),
                              adjustment.baseAmount()) +
               ": " +
               money(adjustment.charge() ?
                     adjustment.amount() :
                     adjustment.amount()
                               .negate()),
               small);
    }
    item.add(line.note(),
             small);

    var price = new Cell().add(money(line.netUnitPrice()),
                               body);
    if (line.priceBaseQuantity() != null &&
        line.priceBaseQuantity()
            .compareTo(BigDecimal.ONE) != 0) {
      price.add(t.label("line.perQuantity",
                        t.number(line.priceBaseQuantity()),
                        t.unit(line.unitCode())),
                small);
    }
    var cells = new ArrayList<Cell>();
    cells.add(new Cell().add(line.id(),
                             body));
    cells.add(item);
    cells.add(new Cell().add(t.number(line.quantity()),
                             body));
    if (response) {
      cells.add(new Cell().add(t.number(line.agreedQuantity()),
                               bodyBold));
    }
    cells.add(new Cell().add(t.unit(line.unitCode()),
                             body));
    cells.add(price);
    if (!response) {
      cells.add(new Cell().add(line.tax() == null ||
                               line.tax()
                                   .rate() == null ?
                               "" :
                               t.percent(line.tax()
                                             .rate()),
                               body));
    }
    cells.add(new Cell().add(money(line.netAmount()),
                             bodyBold));
    return new Row(cells,
                   true);
  }

  private Canvas.Style smallStrong() {
    return new Canvas.Style(bodyBold.font(),
                            small.size(),
                            0xB42318);
  }

  // -- totals and details -------------------------------------------------------------------------

  private void totals() {
    OrderTotals totals = order.details() != null &&
                         order.details()
                              .statedTotals() != null ?
                         order.details()
                              .statedTotals() :
                         order.totals();
    var rows = new ArrayList<TotalRow>();
    rows.add(plain(t.label("totals.lines"),
                   money(totals.lineTotal())));
    for (DocumentAdjustment adjustment : order.adjustments()) {
      rows.add(plain(adjustmentText("totals",
                                    adjustment.charge(),
                                    adjustment.reason(),
                                    adjustment.percentage(),
                                    adjustment.baseAmount()),
                     money(adjustment.charge() ?
                           adjustment.amount() :
                           adjustment.amount()
                                     .negate())));
    }
    BigDecimal net = totals.taxBasisTotal() != null ?
                     totals.taxBasisTotal() :
                     totals.lineTotal();
    if (totals.taxAmount() == null) {
      rows.add(grand(t.label("totals.net"),
                     money(net)));
    } else {
      rows.add(strong(t.label("totals.net"),
                      money(net)));
      rows.add(plain(t.label("order.totals.vat"),
                     money(totals.taxAmount())));
      rows.add(grand(t.label("totals.gross"),
                     money(totals.grandTotal() != null ?
                           totals.grandTotal() :
                           net.add(totals.taxAmount()))));
    }
    totals(rows);
  }

  private void details() {
    var rows = new ArrayList<String[]>();
    if (!response) {
      row(rows,
          "order.previousOrder",
          reference(order.previousOrderReference()));
    }
    row(rows,
        "order.previousChange",
        reference(order.previousOrderChangeReference()));
    row(rows,
        "order.previousResponse",
        reference(order.previousOrderResponseReference()));
    row(rows,
        "order.blanketOrder",
        reference(order.blanketOrderReference()));
    row(rows,
        "order.requisition",
        reference(order.requisitionReference()));
    row(rows,
        "order.catalogue",
        reference(order.catalogueReference()));
    row(rows,
        "order.project",
        join(" – ",
             order.projectReference(),
             order.projectName()));
    if (order.effectivePeriod() != null) {
      row(rows,
          "order.validity",
          t.period(order.effectivePeriod()
                        .startDate(),
                   order.effectivePeriod()
                        .endDate()));
    }
    row(rows,
        "order.deliveryTerms",
        join(" – ",
             order.deliveryTermsCode(),
             order.details() == null ?
             null :
             order.details()
                  .deliveryTermsDescription(),
             order.details() == null ?
             null :
             order.details()
                  .deliveryTermsLocationName()));
    row(rows,
        "payment.means",
        order.paymentMeansText() != null ?
        order.paymentMeansText() :
        order.paymentMeansCode() == null ?
        null :
        t.paymentMeans(order.paymentMeansCode()));
    row(rows,
        "payment.terms",
        order.paymentTerms());
    row(rows,
        "order.accountingReference",
        order.buyerAccountingReference());
    if (order.purposeCode() != null && !"9".equals(order.purposeCode())) {
      row(rows,
          "order.purpose",
          code("purpose.",
               order.purposeCode()));
    }
    factsSection("order.details",
                 rows);
  }

  // -- helpers ------------------------------------------------------------------------------------

  private String reference(DocumentReference reference) {
    if (reference == null || reference.id() == null) {
      return null;
    }
    String text = reference.lineId() == null ?
                  reference.id() :
                  reference.id() + " / " + reference.lineId();
    return reference.issueDate() == null ?
           text :
           t.label("precedingInvoiceOf",
                   text,
                   t.date(reference.issueDate()));
  }

  private String requested(java.time.LocalDate date,
                           OrderPeriod period) {
    if (date != null) {
      return t.date(date);
    }
    if (period != null) {
      return t.period(period.startDate(),
                      period.endDate());
    }
    return null;
  }

  private String code(String prefix,
                      String code) {
    if (code == null) {
      return null;
    }
    String text = t.optional(prefix + code);
    return text == null ?
           code :
           text;
  }
}

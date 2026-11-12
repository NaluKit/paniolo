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
import io.github.nalukit.paniolo.model.common.Party;
import io.github.nalukit.paniolo.model.common.value.CurrencyCode;
import io.github.nalukit.paniolo.model.despatch.DespatchAdvice;
import io.github.nalukit.paniolo.model.despatch.DespatchItemInstance;
import io.github.nalukit.paniolo.model.despatch.DespatchLine;
import io.github.nalukit.paniolo.pdflayout.Canvas.Align;
import io.github.nalukit.paniolo.pdflayout.Canvas.Cell;
import io.github.nalukit.paniolo.pdflayout.Canvas.Column;
import io.github.nalukit.paniolo.pdflayout.Canvas.Row;
import org.apache.pdfbox.pdmodel.PDDocument;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static io.github.nalukit.paniolo.pdflayout.Canvas.MM;

/**
 * Lays out a despatch advice as delivery note: the despatching party sends it to the delivery
 * customer; lines show delivered and outstanding quantities, batches and serial numbers; the last
 * page has a receipt confirmation to sign.
 */
final class DespatchAdviceRenderer
    extends DocumentRenderer {
  private final DespatchAdvice advice;

  DespatchAdviceRenderer(DespatchAdvice advice,
                         PdfLayout layout) {
    super(layout);
    this.advice = advice;
  }

  private static boolean same(Party a,
                              Party b) {
    return a == null || b != null && Objects.equals(a.name(),
                                                    b.name()) && Objects.equals(a.address(),
                                                                                b.address());
  }

  @Override
  Party sender() {
    return advice.despatchSupplier() != null ?
           advice.despatchSupplier() :
           advice.seller();
  }

  @Override
  Party recipient() {
    return advice.deliveryCustomer() != null ?
           advice.deliveryCustomer() :
           advice.buyer();
  }

  @Override
  CurrencyCode currency() {
    return null;
  }

  @Override
  String documentTitle() {
    return join(" ",
                t.label("despatch.title"),
                advice.number());
  }

  @Override
  List<String[]> facts() {
    var rows = new ArrayList<String[]>();
    fact(rows,
         "despatch.number",
         advice.number());
    fact(rows,
         "despatch.issueDate",
         t.date(advice.issueDate()));
    fact(rows,
         "despatch.despatchDate",
         t.date(advice.actualDespatchDate()));
    fact(rows,
         "despatch.deliveryDate",
         t.date(advice.actualDeliveryDate()));
    if (advice.estimatedDeliveryPeriod() != null) {
      fact(rows,
           "despatch.estimatedDelivery",
           t.period(advice.estimatedDeliveryPeriod()
                          .startDate(),
                    advice.estimatedDeliveryPeriod()
                          .endDate()));
    }
    fact(rows,
         "despatch.order",
         String.join(", ",
                     advice.orderReferences()));
    fact(rows,
         "despatch.shipment",
         advice.shipmentId());
    fact(rows,
         "despatch.tracking",
         advice.trackingId());
    return rows;
  }

  @Override
  void content(PDDocument document) {
    notes(advice.note() == null ?
          List.of() :
          List.of(advice.note()));
    lines();
    shipment();
    var recipient = recipient();
    parties(List.of(new Object[] { "party.buyer",
                                   same(advice.buyer(),
                                        recipient) ?
                                   null :
                                   advice.buyer() },
                    new Object[] { "party.seller",
                                   same(advice.seller(),
                                        sender()) ?
                                   null :
                                   advice.seller() },
                    new Object[] { "party.originator",
                                   advice.originator() }));
    supportingDocuments(advice.additionalDocuments());
    receipt();
  }

  private void lines() {
    float fixed = 26 + 46 + 50 + 50 + 52;
    var   rows  = new ArrayList<Row>();
    for (DespatchLine line : advice.lines()) {
      rows.add(row(line));
    }
    table(List.of(new Column(26,
                             Align.LEFT),
                  new Column(canvas.width() - fixed,
                             Align.LEFT),
                  new Column(46,
                             Align.RIGHT),
                  new Column(50,
                             Align.LEFT),
                  new Column(50,
                             Align.RIGHT),
                  new Column(52,
                             Align.RIGHT)),
          header("lines.pos",
                 "lines.item.despatch",
                 "lines.delivered",
                 "lines.unit",
                 "lines.outstanding",
                 "lines.orderLine"),
          rows);
  }

  private Row row(DespatchLine line) {
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
        prefixed("line.order",
                 line.orderReference()));
    add(parts,
        prefixed("line.salesOrderLine",
                 line.salesOrderLineId()));
    add(parts,
        prefixed("line.handlingUnit",
                 join(" ",
                      line.handlingUnitId(),
                      line.handlingUnitTypeCode() == null ?
                      null :
                      "(" + line.handlingUnitTypeCode() + ")")));
    var item = new Cell().add(line.itemName(),
                              bodyBold)
                         .add(details(parts,
                                      line.attributes()),
                              small);
    for (DespatchItemInstance instance : line.instances()) {
      var values = new ArrayList<String>();
      add(values,
          prefixed("line.serial",
                   instance.serialId()));
      add(values,
          prefixed("line.batch",
                   instance.lotNumber()));
      add(values,
          prefixed("line.expiry",
                   t.date(instance.expiryDate())));
      add(values,
          prefixed("line.bestBefore",
                   t.date(instance.bestBeforeDate())));
      add(values,
          prefixed("line.manufactured",
                   t.date(instance.manufactureDate())));
      item.add(String.join(" · ",
                           values),
               small.color(TEXT));
    }
    if (line.outstandingReason() != null) {
      item.add(t.label("line.outstandingReason",
                       line.outstandingReason()),
               small.color(TEXT));
    }
    item.add(line.note(),
             small);
    return new Row(List.of(new Cell().add(line.id(),
                                          body),
                           item,
                           new Cell().add(t.number(line.deliveredQuantity()),
                                          bodyBold),
                           new Cell().add(t.unit(line.unitCode()),
                                          body),
                           new Cell().add(t.number(line.outstandingQuantity()),
                                          body),
                           new Cell().add(line.orderLineId(),
                                          body)),
                   true);
  }

  private void shipment() {
    var rows = new ArrayList<String[]>();
    if (advice.grossWeight() != null) {
      row(rows,
          "despatch.grossWeight",
          t.number(advice.grossWeight()) + " " + t.unit(advice.grossWeightUnit()));
    }
    if (advice.grossVolume() != null) {
      row(rows,
          "despatch.grossVolume",
          t.number(advice.grossVolume()) + " " + t.unit(advice.grossVolumeUnit()));
    }
    row(rows,
        "despatch.handlingUnits",
        t.number(advice.handlingUnitCount()));
    if (advice.transportModeCode() != null) {
      String mode = t.optional("transport." + advice.transportModeCode());
      row(rows,
          "despatch.transport",
          mode == null ?
          advice.transportModeCode() :
          mode);
    }
    row(rows,
        "despatch.information",
        advice.shipmentInformation());
    if (advice.despatchAddress() != null) {
      row(rows,
          "party.despatchAddress",
          String.join("\n",
                      addressLines(advice.despatchAddress(),
                                   true)));
    }
    factsSection("despatch.shipmentSection",
                 rows);
  }

  private void receipt() {
    float height = heading.leading() + body.leading() + 18 * MM;
    canvas.ensure(height);
    section("receipt.title");
    canvas.paragraph(t.label("receipt.text"),
                     body,
                     canvas.left,
                     canvas.width());
    canvas.space(14 * MM);
    float half = (canvas.width() - 10 * MM) / 2;
    canvas.rule(canvas.left,
                canvas.left + half * 0.6f,
                canvas.y,
                0.5f,
                TEXT);
    canvas.rule(canvas.left + half + 10 * MM,
                canvas.right,
                canvas.y,
                0.5f,
                TEXT);
    canvas.space(small.leading());
    canvas.text(t.label("receipt.date"),
                small,
                canvas.left,
                canvas.y + small.descent());
    canvas.text(t.label("receipt.signature"),
                small,
                canvas.left + half + 10 * MM,
                canvas.y + small.descent());
  }
}

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
import io.github.nalukit.paniolo.model.common.CashDiscount;
import io.github.nalukit.paniolo.model.common.DeliveryInformation;
import io.github.nalukit.paniolo.model.common.InvoiceLine;
import io.github.nalukit.paniolo.model.common.InvoiceNote;
import io.github.nalukit.paniolo.model.common.Party;
import io.github.nalukit.paniolo.model.quotation.Quotation;
import io.github.nalukit.paniolo.model.quotation.QuotationLine;
import org.apache.pdfbox.pdmodel.PDDocument;

import java.util.ArrayList;
import java.util.List;

/**
 * Lays out a quotation: the letterhead of the seller, the validity, the lines of the standard offer
 * with totals, the optional lines apart from them, and the terms. It shares the lines and totals
 * with the invoice but shows no payment instruction and no amount due.
 */
final class QuotationRenderer
    extends InvoiceRenderer {
  private final Quotation quotation;

  QuotationRenderer(Quotation quotation,
                    PdfLayout layout) {
    super(quotation.toInvoice(quotation.number(),
                              quotation.issueDate()),
          layout);
    this.quotation = quotation;
  }

  @Override
  String documentTitle() {
    return join(" ",
                t.label("quotation.title"),
                quotation.number());
  }

  @Override
  String headingTitle() {
    return t.label("quotation.title");
  }

  @Override
  boolean showsAmountDue() {
    return false;
  }

  @Override
  List<String[]> facts() {
    var rows = new ArrayList<String[]>();
    fact(rows,
         "quotation.number",
         quotation.number());
    fact(rows,
         "quotation.issueDate",
         t.date(quotation.issueDate()));
    fact(rows,
         "quotation.validUntil",
         t.date(quotation.validUntil()));
    fact(rows,
         "buyerReference",
         quotation.buyerReference());
    Party buyer = quotation.buyer();
    fact(rows,
         "customerNumber",
         buyer.identifier());
    fact(rows,
         "buyerVatId",
         buyer.vatId());
    return rows;
  }

  @Override
  void content(PDDocument document) {
    if (quotation.subject() != null && !quotation.subject()
                                                 .isBlank()) {
      canvas.paragraph(quotation.subject(),
                       bodyBold,
                       canvas.left,
                       canvas.width());
      canvas.space(3);
    }
    notes(quotation.notes()
                   .stream()
                   .map(InvoiceNote::text)
                   .toList());
    lineTable(invoice.lines());
    totals();
    vatNotes();
    List<InvoiceLine> optional = quotation.lines()
                                          .stream()
                                          .filter(QuotationLine::optional)
                                          .map(QuotationLine::line)
                                          .toList();
    if (!optional.isEmpty()) {
      section("quotation.optional");
      lineTable(optional);
    }
    terms();
  }

  private void terms() {
    var rows = new ArrayList<String[]>();
    row(rows,
        "quotation.delivery",
        delivery(quotation.delivery()));
    row(rows,
        "quotation.deliveryTerms",
        quotation.deliveryTerms());
    row(rows,
        "payment.terms",
        quotation.paymentTerms() == null ?
        null :
        CashDiscount.parse(quotation.paymentTerms())
                    .text());
    factsSection("quotation.terms",
                 rows);
  }

  private String delivery(DeliveryInformation delivery) {
    if (delivery == null) {
      return null;
    }
    return join(", ",
                delivery.partyName(),
                delivery.address() == null ?
                null :
                String.join(", ",
                            addressLines(delivery.address(),
                                         false)),
                t.date(delivery.actualDeliveryDate()));
  }
}

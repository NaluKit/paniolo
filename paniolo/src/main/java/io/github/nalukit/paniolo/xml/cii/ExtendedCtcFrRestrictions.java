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
import io.github.nalukit.paniolo.model.common.InvoiceLine;
import io.github.nalukit.paniolo.model.common.Party;
import io.github.nalukit.paniolo.model.common.ProcessControl;
import io.github.nalukit.paniolo.model.extended.ExtendedInformation;

import java.util.ArrayList;
import java.util.List;

/**
 * Rejects content the French profile EXTENDED-CTC-FR does not allow, instead of writing content
 * the rules of FNFE-MPE reject or ask recipients to ignore. EXTENDED-CTC-FR is a subset of the
 * Factur-X profile EXTENDED; the content follows the EXTENDED-CTC-FR Schematron for CII of
 * FNFE-MPE 1.4.0.04, which reports everything rejected here as "should not be present" or "shall
 * not be used".
 */
final class ExtendedCtcFrRestrictions {
  private ExtendedCtcFrRestrictions() {
  }

  static boolean applies(Invoice invoice) {
    return ProcessControl.FACTURX_EXTENDED_CTC_FR.equals(invoice.processControl()
                                                                .specificationId());
  }

  static void check(Invoice invoice) {
    var found = new ArrayList<String>();
    party(found,
          "seller",
          invoice.seller());
    party(found,
          "buyer",
          invoice.buyer());
    for (var note : invoice.notes()) {
      add(found,
          note.contentCode(),
          "content code of an invoice note");
    }
    for (var adjustment : invoice.adjustments()) {
      if (adjustment.extended() != null) {
        found.add("sequence number and base quantity of document allowances and charges");
      }
    }
    var extended = invoice.extended();
    if (extended != null) {
      extended(found,
               extended);
    }
    for (var line : invoice.lines()) {
      line(found,
           line);
    }
    if (!found.isEmpty()) {
      throw new IllegalArgumentException("Profile EXTENDED-CTC-FR does not allow: " +
                                         String.join(", ",
                                                     found.stream()
                                                          .distinct()
                                                          .toList()));
    }
  }

  private static void extended(List<String> found,
                               ExtendedInformation extended) {
    for (var party : new Object[][] { { extended.salesAgent(),
                                        "sales agent" },
                                      { extended.buyerAgent(),
                                        "buyer agent" },
                                      { extended.buyerTaxRepresentative(),
                                        "buyer tax representative" },
                                      { extended.invoicer(),
                                        "invoicer" },
                                      { extended.invoicee(),
                                        "invoicee" },
                                      { extended.payer(),
                                        "payer" } }) {
      if (party[0] != null) {
        party(found,
              (String) party[1],
              (Party) party[0]);
      }
    }
    for (Object[] unsupported : new Object[][] { { extended.productEndUser(),
                                                   "product end user" },
                                                 { extended.shipFrom(),
                                                   "ship-from party" },
                                                 { extended.ultimateShipTo(),
                                                   "ultimate ship-to party" },
                                                 { extended.quotationReference(),
                                                   "quotation reference" },
                                                 { extended.deliveryNoteReference(),
                                                   "delivery note reference" },
                                                 { extended.documentName(),
                                                   "document name" },
                                                 { extended.languageId(),
                                                   "language" },
                                                 { extended.copyIndicator(),
                                                   "copy indicator" },
                                                 { extended.testIndicator(),
                                                   "test indicator" },
                                                 { extended.effectiveDate(),
                                                   "effective date" },
                                                 { extended.invoiceIssuerReference(),
                                                   "invoice issuer reference" },
                                                 { extended.transportModeCode(),
                                                   "transport mode" },
                                                 { extended.billingPeriodDescription(),
                                                   "description of the invoicing period" },
                                                 { extended.buyerAccountingTypeCode(),
                                                   "type of the buyer accounting reference" },
                                                 { extended.payerAccountName(),
                                                   "payer account name" },
                                                 { extended.payerBic(),
                                                   "payer BIC" } }) {
      add(found,
          unsupported[0],
          (String) unsupported[1]);
    }
    for (Object[] unsupported : new Object[][] { { extended.ultimateCustomerOrderReferences(),
                                                   "ultimate customer orders" },
                                                 { extended.paymentTerms(),
                                                   "additional structured payment terms" },
                                                 { extended.logisticsServiceCharges(),
                                                   "logistics service charges" },
                                                 { extended.advancePayments(),
                                                   "advance payments" },
                                                 { extended.financialAdjustments(),
                                                   "financial adjustments" } }) {
      if (!((List<?>) unsupported[0]).isEmpty()) {
        found.add((String) unsupported[1]);
      }
    }
  }

  private static void party(List<String> found,
                            String role,
                            Party party) {
    var extended = party.extended();
    if (extended == null) {
      return;
    }
    add(found,
        extended.roleCode(),
        role + " role code");
    add(found,
        extended.legalAddress(),
        role + " legal address");
    var contact = extended.contact();
    if (contact != null) {
      add(found,
          contact.typeCode(),
          role + " contact type");
      add(found,
          contact.fax(),
          role + " contact fax");
    }
  }

  private static void line(List<String> found,
                           InvoiceLine line) {
    String prefix = "line " + line.id() + " ";
    for (var classification : line.classifications()) {
      add(found,
          classification.className(),
          prefix + "classification name");
    }
    var extended = line.extended();
    if (extended == null) {
      return;
    }
    for (Object[] unsupported : new Object[][] { { extended.productId(),
                                                   "product identifier" },
                                                 { extended.brandName(),
                                                   "brand name" },
                                                 { extended.manufacturer(),
                                                   "manufacturer" },
                                                 { extended.quotationReference(),
                                                   "quotation reference" },
                                                 { extended.contractReference(),
                                                   "contract reference" },
                                                 { extended.chargeFreeQuantity(),
                                                   "charge-free quantity" },
                                                 { extended.packageQuantity(),
                                                   "package quantity" },
                                                 { extended.ultimateShipTo(),
                                                   "ultimate ship-to party" },
                                                 { extended.noteContentCode(),
                                                   "note content code" },
                                                 { extended.accountingTypeCode(),
                                                   "type of the accounting reference" } }) {
      add(found,
          unsupported[0],
          prefix + unsupported[1]);
    }
    for (Object[] unsupported : new Object[][] { { extended.instances(),
                                                   "product instances" },
                                                 { extended.includedProducts(),
                                                   "included products" },
                                                 { extended.ultimateCustomerOrderReferences(),
                                                   "ultimate customer orders" },
                                                 { extended.additionalReferences(),
                                                   "further referenced documents" } }) {
      if (!((List<?>) unsupported[0]).isEmpty()) {
        found.add(prefix + unsupported[1]);
      }
    }
    for (Object[] party : new Object[][] { { extended.itemSeller(),
                                             "item seller" },
                                           { extended.shipTo(),
                                             "ship-to party" } }) {
      if (party[0] != null) {
        party(found,
              prefix + party[1],
              (Party) party[0]);
      }
    }
  }

  private static void add(List<String> found,
                          Object value,
                          String name) {
    if (value != null) {
      found.add(name);
    }
  }
}

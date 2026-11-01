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

import java.util.ArrayList;
import java.util.List;

/**
 * Rejects content the Factur-X / ZUGFeRD profiles BASIC and BASIC WL do not allow, instead of
 * dropping it silently. The list follows the elements of the EN 16931 schema that are missing in
 * the BASIC schema of Factur-X 1.09; BASIC WL has the same header without lines.
 */
final class FacturXBasicRestrictions {
  private FacturXBasicRestrictions() {
  }

  static boolean applies(Invoice invoice) {
    String id = invoice.processControl()
                       .specificationId();
    return ProcessControl.FACTURX_BASIC.equals(id) || ProcessControl.FACTURX_BASIC_WL.equals(id) || "urn:zugferd.de:2p0:basicwl".equals(id);
  }

  static void check(Invoice invoice) {
    var found = new ArrayList<String>();
    party(found,
          "seller",
          invoice.seller());
    party(found,
          "buyer",
          invoice.buyer());
    var references = invoice.references();
    if (references != null) {
      add(found,
          references.projectReference(),
          "project reference (BT-11)");
      add(found,
          references.salesOrderReference(),
          "sales order reference (BT-14)");
      add(found,
          references.receivingAdviceReference(),
          "receiving advice reference (BT-15)");
      add(found,
          references.tenderOrLotReference(),
          "tender or lot reference (BT-17)");
      add(found,
          references.invoicedObjectId(),
          "invoiced object identifier (BT-18)");
    }
    if (!invoice.supportingDocuments()
                .isEmpty()) {
      found.add("additional supporting documents (BG-24)");
    }
    add(found,
        invoice.taxPointDate(),
        "VAT point date (BT-7)");
    add(found,
        invoice.roundingAmount(),
        "rounding amount (BT-114)");
    var payment = invoice.paymentInstruction();
    if (payment != null) {
      add(found,
          payment.meansText(),
          "payment means text (BT-82)");
      add(found,
          payment.paymentCard(),
          "payment card information (BG-18)");
      for (var account : payment.creditTransfers()) {
        add(found,
            account.accountName(),
            "payment account name (BT-85)");
        add(found,
            account.bic(),
            "payment service provider identifier (BT-86)");
      }
    }
    for (var line : invoice.lines()) {
      line(found,
           line);
    }
    if (!found.isEmpty()) {
      throw new IllegalArgumentException("Factur-X profile " +
                                         (invoice.hasStatedTotals() ?
                                          "BASIC WL" :
                                          "BASIC") +
                                         " does not allow: " +
                                         String.join(", ",
                                                     found.stream()
                                                          .distinct()
                                                          .toList()));
    }
  }

  private static void party(List<String> found,
                            String role,
                            Party party) {
    add(found,
        party.contact(),
        role +
        " contact (" +
        ("seller".equals(role) ?
         "BG-6" :
         "BG-9") +
        ")");
    add(found,
        party.additionalLegalInformation(),
        role + " additional legal information (BT-33)");
  }

  private static void line(List<String> found,
                           InvoiceLine line) {
    String prefix = "line " + line.id() + " ";
    add(found,
        line.objectId(),
        prefix + "object identifier (BT-128)");
    add(found,
        line.orderLineReference(),
        prefix + "order line reference (BT-132)");
    add(found,
        line.buyerAccountingReference(),
        prefix + "buyer accounting reference (BT-133)");
    add(found,
        line.description(),
        prefix + "item description (BT-154)");
    add(found,
        line.sellerItemId(),
        prefix + "seller's item identifier (BT-155)");
    add(found,
        line.buyerItemId(),
        prefix + "buyer's item identifier (BT-156)");
    if (!line.classifications()
             .isEmpty()) {
      found.add(prefix + "item classification (BT-158)");
    }
    add(found,
        line.originCountry(),
        prefix + "item country of origin (BT-159)");
    if (!line.attributes()
             .isEmpty()) {
      found.add(prefix + "item attributes (BG-32)");
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

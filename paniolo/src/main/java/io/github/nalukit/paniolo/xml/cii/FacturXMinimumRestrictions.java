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
import io.github.nalukit.paniolo.model.common.Party;

import java.util.ArrayList;
import java.util.List;

/**
 * Rejects content the Factur-X / ZUGFeRD profile MINIMUM does not allow, instead of dropping it
 * silently. MINIMUM carries the document identification, seller and buyer with name, legal
 * registration identifier, country, and tax registrations, the buyer reference (BT-10), the
 * purchase order reference (BT-13), the currency, and the totals BT-109, BT-110, BT-112, and
 * BT-115, following the MINIMUM schema of Factur-X 1.09.
 */
final class FacturXMinimumRestrictions {
  private FacturXMinimumRestrictions() {
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
          references.contractReference(),
          "contract reference (BT-12)");
      add(found,
          references.salesOrderReference(),
          "sales order reference (BT-14)");
      add(found,
          references.receivingAdviceReference(),
          "receiving advice reference (BT-15)");
      add(found,
          references.despatchAdviceReference(),
          "despatch advice reference (BT-16)");
      add(found,
          references.tenderOrLotReference(),
          "tender or lot reference (BT-17)");
      add(found,
          references.invoicedObjectId(),
          "invoiced object identifier (BT-18)");
      add(found,
          references.buyerAccountingReference(),
          "buyer accounting reference (BT-19)");
    }
    if (!invoice.notes()
                .isEmpty()) {
      found.add("invoice notes (BG-1)");
    }
    if (!invoice.precedingInvoiceReferences()
                .isEmpty()) {
      found.add("preceding invoice reference (BG-3)");
    }
    add(found,
        invoice.taxCurrencyCode(),
        "VAT accounting currency (BT-6)");
    add(found,
        invoice.taxPointDate(),
        "VAT point date (BT-7)");
    add(found,
        invoice.taxPointDateCode(),
        "VAT point date code (BT-8)");
    add(found,
        invoice.dueDate(),
        "payment due date (BT-9)");
    add(found,
        invoice.paymentTerms(),
        "payment terms (BT-20)");
    if (!invoice.cashDiscounts()
                .isEmpty()) {
      found.add("cash discounts (BT-20)");
    }
    add(found,
        invoice.payee(),
        "payee (BG-10)");
    add(found,
        invoice.taxRepresentative(),
        "seller tax representative (BG-11)");
    add(found,
        invoice.delivery(),
        "delivery information (BG-13)");
    add(found,
        invoice.invoicePeriod(),
        "invoicing period (BG-14)");
    add(found,
        invoice.paymentInstruction(),
        "payment instructions (BG-16)");
    if (!invoice.supportingDocuments()
                .isEmpty()) {
      found.add("additional supporting documents (BG-24)");
    }
    add(found,
        invoice.roundingAmount(),
        "rounding amount (BT-114)");
    var totals = invoice.statedTotals();
    add(found,
        totals.lineTotal(),
        "sum of line net amounts (BT-106)");
    if (!totals.vatBreakdowns()
               .isEmpty()) {
      found.add("VAT breakdown (BG-23)");
    }
    if (!found.isEmpty()) {
      throw new IllegalArgumentException("Factur-X profile MINIMUM does not allow: " +
                                         String.join(", ",
                                                     found.stream()
                                                          .distinct()
                                                          .toList()));
    }
  }

  private static void party(List<String> found,
                            String role,
                            Party party) {
    boolean seller = "seller".equals(role);
    if (party.address() != null &&
        !party.address()
              .isCountryOnly()) {
      found.add(role +
                " postal address other than the country (" +
                (seller ?
                 "BG-5" :
                 "BG-8") +
                ")");
    }
    add(found,
        party.electronicAddress(),
        role +
        " electronic address (" +
        (seller ?
         "BT-34" :
         "BT-49") +
        ")");
    add(found,
        party.contact(),
        role +
        " contact (" +
        (seller ?
         "BG-6" :
         "BG-9") +
        ")");
    add(found,
        party.identifier(),
        role +
        " identifier (" +
        (seller ?
         "BT-29" :
         "BT-46") +
        ")");
    add(found,
        party.tradingName(),
        role +
        " trading name (" +
        (seller ?
         "BT-28" :
         "BT-45") +
        ")");
    add(found,
        party.additionalLegalInformation(),
        role + " additional legal information (BT-33)");
  }

  private static void add(List<String> found,
                          Object value,
                          String name) {
    if (value != null) {
      found.add(name);
    }
  }
}

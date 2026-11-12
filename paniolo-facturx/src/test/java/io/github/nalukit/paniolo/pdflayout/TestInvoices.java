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

import io.github.nalukit.paniolo.model.Invoice;
import io.github.nalukit.paniolo.model.common.Address;
import io.github.nalukit.paniolo.model.common.CashDiscount;
import io.github.nalukit.paniolo.model.common.Contact;
import io.github.nalukit.paniolo.model.common.CreditTransfer;
import io.github.nalukit.paniolo.model.common.DeliveryInformation;
import io.github.nalukit.paniolo.model.common.DocumentAdjustment;
import io.github.nalukit.paniolo.model.common.DocumentTotals;
import io.github.nalukit.paniolo.model.common.ElectronicAddress;
import io.github.nalukit.paniolo.model.common.InvoiceLine;
import io.github.nalukit.paniolo.model.common.InvoiceNote;
import io.github.nalukit.paniolo.model.common.InvoicePeriod;
import io.github.nalukit.paniolo.model.common.LineAdjustment;
import io.github.nalukit.paniolo.model.common.Party;
import io.github.nalukit.paniolo.model.common.PaymentInstruction;
import io.github.nalukit.paniolo.model.common.ProcessControl;
import io.github.nalukit.paniolo.model.common.References;
import io.github.nalukit.paniolo.model.common.TaxCategory;
import io.github.nalukit.paniolo.model.common.value.Country;
import io.github.nalukit.paniolo.model.common.value.InvoiceTypeCode;
import io.github.nalukit.paniolo.model.common.value.PaymentMeansCode;

import java.math.BigDecimal;
import java.time.LocalDate;

final class TestInvoices {
  static final TaxCategory VAT_19 = TaxCategory.standard(new BigDecimal("19"));
  static final TaxCategory VAT_7  = TaxCategory.standard(new BigDecimal("7"));

  private TestInvoices() {
  }

  static Party seller() {
    return Party.create()
                .name("Muster GmbH")
                .address(Address.create()
                                .street("Musterstraße 1")
                                .city("Berlin")
                                .postalCode("10115")
                                .country(Country.DE)
                                .build())
                .vatId("DE123456788")
                .taxId("27/123/45678")
                .legalRegistrationId("HRB 12345 B")
                .electronicAddress(ElectronicAddress.create()
                                                    .value("rechnung@example.org")
                                                    .schemeId("EM")
                                                    .build())
                .contact(Contact.create()
                                .name("Erika Muster")
                                .telephone("+49 30 123456")
                                .email("rechnung@example.org")
                                .build())
                .build();
  }

  static Party buyer() {
    return Party.create()
                .name("Kunde GmbH")
                .address(Address.create()
                                .street("Kundenweg 2")
                                .city("Hamburg")
                                .postalCode("20095")
                                .country(Country.DE)
                                .build())
                .identifier("K-4711")
                .electronicAddress(ElectronicAddress.create()
                                                    .value("einkauf@example.org")
                                                    .schemeId("EM")
                                                    .build())
                .build();
  }

  /**
   * An XRechnung with two VAT rates and a few lines.
   */
  static Invoice.Builder simple(String number) {
    return Invoice.create()
                  .number(number)
                  .typeCode(InvoiceTypeCode.CODE_380)
                  .issueDate(LocalDate.of(2026,
                                          10,
                                          27))
                  .dueDate(LocalDate.of(2026,
                                        11,
                                        10))
                  .buyerReference("04011000-12345-03")
                  .seller(seller())
                  .buyer(buyer())
                  .delivery(DeliveryInformation.create()
                                               .actualDeliveryDate(LocalDate.of(2026,
                                                                                10,
                                                                                26))
                                               .build())
                  .paymentInstruction(PaymentInstruction.create()
                                                        .meansCode(PaymentMeansCode.CODE_58)
                                                        .addCreditTransfer(CreditTransfer.create()
                                                                                         .iban("DE89370400440532013000")
                                                                                         .accountName("Muster GmbH")
                                                                                         .bic("COBADEFFXXX")
                                                                                         .build())
                                                        .remittanceInformation(number)
                                                        .build())
                  .paymentTerms("Zahlbar innerhalb von 14 Tagen ohne Abzug.")
                  .addLine(InvoiceLine.create()
                                      .id("1")
                                      .name("Beratung")
                                      .description("Workshop zur Einführung der E-Rechnung")
                                      .quantity(new BigDecimal("2"))
                                      .unitCode("HUR")
                                      .netUnitPrice(new BigDecimal("100.00"))
                                      .tax(VAT_19)
                                      .build())
                  .addLine(InvoiceLine.create()
                                      .id("2")
                                      .name("Fachbuch E-Rechnung")
                                      .quantity(new BigDecimal("3"))
                                      .unitCode("H87")
                                      .netUnitPrice(new BigDecimal("39.90"))
                                      .tax(VAT_7)
                                      .build());
  }

  /**
   * An invoice with everything the layout shows, and many lines for page breaks.
   */
  static Invoice rich(int lineCount) {
    var builder = simple("R-2026-0815").invoicePeriod(InvoicePeriod.create()
                                                                   .startDate(LocalDate.of(2026,
                                                                                           10,
                                                                                           1))
                                                                   .endDate(LocalDate.of(2026,
                                                                                         10,
                                                                                         31))
                                                                   .build())
                                       .references(References.create()
                                                             .purchaseOrderReference("PO-2026-17")
                                                             .contractReference("V-99")
                                                             .projectReference("Digitalisierung 2026")
                                                             .build())
                                       .addNote(InvoiceNote.of("Vielen Dank für Ihren Auftrag. Wir berechnen Ihnen die folgenden Leistungen."))
                                       .addAdjustment(DocumentAdjustment.createAllowance()
                                                                        .amount(new BigDecimal("10.00"))
                                                                        .reason("Treuerabatt")
                                                                        .tax(VAT_19)
                                                                        .build())
                                       .addAdjustment(DocumentAdjustment.createCharge()
                                                                        .amount(new BigDecimal("4.90"))
                                                                        .reason("Versand")
                                                                        .tax(VAT_19)
                                                                        .build())
                                       .addCashDiscount(CashDiscount.of(10,
                                                                        new BigDecimal("2")));
    for (int i = 3; i < lineCount + 3; i++) {
      var line = InvoiceLine.create()
                            .id(String.valueOf(i))
                            .name("Leistung " + i)
                            .description(i % 3 == 0 ?
                                         "Ausführliche Beschreibung der Leistung mit mehreren Sätzen, damit der Text in der Spalte umbricht. " + "Sie enthält auch Umlaute wie äöüß und Sonderzeichen wie € und – sowie ✓ 漢字." :
                                         null)
                            .sellerItemId("A-" + i)
                            .quantity(new BigDecimal(i % 5 + 1))
                            .unitCode(i % 2 == 0 ?
                                      "C62" :
                                      "KGM")
                            .netUnitPrice(new BigDecimal("12.50"))
                            .addAttribute("Farbe",
                                          i % 2 == 0 ?
                                          "blau" :
                                          "rot")
                            .tax(VAT_19);
      if (i % 4 == 0) {
        line.grossPrice(new BigDecimal("15.00"))
            .priceDiscount(new BigDecimal("2.50"));
      }
      if (i % 7 == 0) {
        line.addAdjustment(LineAdjustment.createAllowance()
                                         .amount(new BigDecimal("1.00"))
                                         .reason("Mengenrabatt")
                                         .build());
      }
      builder.addLine(line.build());
    }
    return builder.build();
  }

  /**
   * Factur-X MINIMUM: no lines, stated totals.
   */
  static Invoice minimum() {
    return Invoice.create()
                  .number("M-1")
                  .issueDate(LocalDate.of(2026,
                                          10,
                                          27))
                  .processControl(ProcessControl.facturXMinimum())
                  .buyerReference("04011000-12345-03")
                  .seller(Party.create()
                               .name("Muster GmbH")
                               .address(Address.ofCountry(Country.DE))
                               .vatId("DE123456788")
                               .legalRegistrationId("HRB 12345 B")
                               .build())
                  .buyer(Party.create()
                              .name("Kunde GmbH")
                              .build())
                  .statedTotals(DocumentTotals.create()
                                              .taxBasisTotal(new BigDecimal("200"))
                                              .taxTotal(new BigDecimal("38"))
                                              .build())
                  .build();
  }
}

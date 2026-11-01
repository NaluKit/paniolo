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
package io.github.nalukit.paniolo.facturx;

import io.github.nalukit.paniolo.model.common.Address;
import io.github.nalukit.paniolo.model.common.Contact;
import io.github.nalukit.paniolo.model.common.DocumentAdjustment;
import io.github.nalukit.paniolo.model.common.LineAdjustment;
import io.github.nalukit.paniolo.model.common.Party;
import io.github.nalukit.paniolo.model.common.TaxCategory;
import io.github.nalukit.paniolo.model.common.value.Country;
import io.github.nalukit.paniolo.model.common.value.PaymentMeansCode;
import io.github.nalukit.paniolo.model.extended.DocumentReference;
import io.github.nalukit.paniolo.model.order.Order;
import io.github.nalukit.paniolo.model.order.OrderLine;
import io.github.nalukit.paniolo.model.order.OrderTypeCode;
import io.github.nalukit.paniolo.model.order.OrderXProfile;

import java.math.BigDecimal;
import java.time.LocalDate;

final class OrderSamples {
  private OrderSamples() {
  }

  private static Party party(String name,
                             String street,
                             String city,
                             String postalCode) {
    return Party.create()
                .name(name)
                .address(Address.create()
                                .street(street)
                                .city(city)
                                .postalCode(postalCode)
                                .country(Country.DE)
                                .build())
                .build();
  }

  private static OrderLine.Builder device() {
    return OrderLine.create()
                    .id("1")
                    .name("Gerät")
                    .sellerItemId("G-100")
                    .standardItemId("4012345678901",
                                    "0160")
                    .quantity(new BigDecimal("10"))
                    .unitCode("H87")
                    .netUnitPrice(new BigDecimal("100.00"))
                    .partialDeliveryAllowed(true);
  }

  private static OrderLine.Builder cable() {
    return OrderLine.create()
                    .id("2")
                    .name("Kabel")
                    .quantity(new BigDecimal("25"))
                    .unitCode("MTR")
                    .netUnitPrice(new BigDecimal("2.50"));
  }

  /**
   * An order of the profile BASIC.
   */
  static Order.Builder basic(OrderTypeCode type) {
    return header(type).addLine(device().build())
                       .addLine(cable().build());
  }

  private static Order.Builder header(OrderTypeCode type) {
    var buyer = Party.create()
                     .name("Kunde GmbH")
                     .address(Address.create()
                                     .street("Kundenweg 2")
                                     .city("Hamburg")
                                     .postalCode("20095")
                                     .country(Country.DE)
                                     .build())
                     .vatId("DE987654321")
                     .contact(Contact.create()
                                     .name("Max Einkauf")
                                     .email("einkauf@example.org")
                                     .build())
                     .build();
    return Order.create()
                .number("B-2026-17")
                .issueDate(LocalDate.of(2026,
                                        10,
                                        28))
                .typeCode(type)
                .purposeCode("9")
                .buyerReference("K-4711")
                .seller(party("Muster GmbH",
                              "Musterstraße 1",
                              "Berlin",
                              "10115"))
                .buyer(buyer)
                .shipTo(party("Lager Nord",
                              "Hafenstraße 5",
                              "Hamburg",
                              "20457"))
                .deliveryTermsCode("FCA")
                .quotationReference(DocumentReference.of("ANG-7"))
                .requestedDeliveryDate(LocalDate.of(2026,
                                                    11,
                                                    15))
                .addNote(io.github.nalukit.paniolo.model.common.InvoiceNote.of("Bitte Lieferscheinnummer angeben"));
  }

  /**
   * An order of the profile COMFORT with VAT, allowances, and payment information.
   */
  static Order.Builder comfort(OrderTypeCode type) {
    var standard = TaxCategory.standard(new BigDecimal("19"));
    return header(type).profile(OrderXProfile.COMFORT)
                       .invoicee(party("Buchhaltung Kunde",
                                       "Kundenweg 2",
                                       "Hamburg",
                                       "20095"))
                       .sellerOrderReference(DocumentReference.of("AB-5"))
                       .paymentMeansCode(PaymentMeansCode.CODE_58)
                       .paymentMeansText("SEPA-Überweisung")
                       .effectivePeriod(new io.github.nalukit.paniolo.model.common.InvoicePeriod(LocalDate.of(2026,
                                                                                                              11,
                                                                                                              1),
                                                                                                 LocalDate.of(2026,
                                                                                                              12,
                                                                                                              31)))
                       .buyerRequisitioner(Party.create()
                                                .name("Werkstatt Nord")
                                                .address(Address.create()
                                                                .street("Hafenstraße 5")
                                                                .city("Hamburg")
                                                                .postalCode("20457")
                                                                .country(Country.DE)
                                                                .build())
                                                .extended(new io.github.nalukit.paniolo.model.extended.PartyExtension(null,
                                                                                                                      null,
                                                                                                                      new io.github.nalukit.paniolo.model.extended.ExtendedContact("Jana Werk",
                                                                                                                                                                                   "Instandhaltung",
                                                                                                                                                                                   "PD",
                                                                                                                                                                                   "+49 40 123",
                                                                                                                                                                                   null,
                                                                                                                                                                                   "werkstatt@example.org")))
                                                .build())
                       .requisitionReference(DocumentReference.of("BANF-12"))
                       .catalogueReference(DocumentReference.of("KAT-2026"))
                       .projectReference("PRJ-9")
                       .projectName("Hallenumbau")
                       .addAdditionalDocument(new io.github.nalukit.paniolo.model.common.AdditionalSupportingDocument("SPEC-1",
                                                                                                                      "Leistungsbeschreibung",
                                                                                                                      "https://example.org/spec.pdf",
                                                                                                                      null))
                       .paymentTerms("30 Tage netto")
                       .addAdjustment(DocumentAdjustment.createAllowance()
                                                        .amount(new BigDecimal("20.00"))
                                                        .reason("Rahmenrabatt")
                                                        .tax(standard)
                                                        .build())
                       .addLine(device().tax(standard)
                                        .grossPrice(new BigDecimal("110.00"))
                                        .priceDiscount(new BigDecimal("10.00"))
                                        .description("Gerät mit Zubehör")
                                        .batchId("CH-7")
                                        .brandName("Marke")
                                        .addAttribute("Farbe",
                                                      "grau")
                                        .addCharacteristic(new io.github.nalukit.paniolo.model.extended.ProductCharacteristic("SPE",
                                                                                                                              "Spannung",
                                                                                                                              "230 V",
                                                                                                                              null,
                                                                                                                              null))
                                        .addClassification(new io.github.nalukit.paniolo.model.common.ItemClassification("8471",
                                                                                                                         "HS",
                                                                                                                         null,
                                                                                                                         "Datenverarbeitung"))
                                        .addInstance(new io.github.nalukit.paniolo.model.extended.ProductInstance("CH-7",
                                                                                                                  "SN-1"))
                                        .packagingTypeCode("CT")
                                        .originCountry(Country.DE)
                                        .substitutedProduct(new io.github.nalukit.paniolo.model.order.SubstitutedProduct("Gerät Plus",
                                                                                                                         "G-101",
                                                                                                                         null,
                                                                                                                         null,
                                                                                                                         null,
                                                                                                                         "Nachfolgemodell"))
                                        .quotationReference(DocumentReference.ofLine("ANG-7",
                                                                                     "1"))
                                        .catalogueReference(DocumentReference.of("KAT-2026"))
                                        .blanketOrderReference(DocumentReference.ofLine("RV-3",
                                                                                        "4"))
                                        .packageQuantity(new BigDecimal("2"))
                                        .perPackageUnitQuantity(new BigDecimal("5"))
                                        .requestedDespatchDate(LocalDate.of(2026,
                                                                            11,
                                                                            8))
                                        .requestedDeliveryDate(LocalDate.of(2026,
                                                                            11,
                                                                            10))
                                        .build())
                       .addLine(cable().tax(standard)
                                       .addAdjustment(LineAdjustment.createAllowance()
                                                                    .amount(new BigDecimal("2.50"))
                                                                    .reason("Restmenge")
                                                                    .build())
                                       .build());
  }
}

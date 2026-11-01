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

import io.github.nalukit.paniolo.config.FacturXProfile;
import io.github.nalukit.paniolo.model.Invoice;
import io.github.nalukit.paniolo.model.common.Address;
import io.github.nalukit.paniolo.model.common.Contact;
import io.github.nalukit.paniolo.model.common.CreditTransfer;
import io.github.nalukit.paniolo.model.common.DeliveryInformation;
import io.github.nalukit.paniolo.model.common.ElectronicAddress;
import io.github.nalukit.paniolo.model.common.InvoiceLine;
import io.github.nalukit.paniolo.model.common.Party;
import io.github.nalukit.paniolo.model.common.Payee;
import io.github.nalukit.paniolo.model.common.PaymentInstruction;
import io.github.nalukit.paniolo.model.common.ProcessControl;
import io.github.nalukit.paniolo.model.common.TaxCategory;
import io.github.nalukit.paniolo.model.common.value.Country;
import io.github.nalukit.paniolo.model.common.value.InvoiceTypeCode;
import io.github.nalukit.paniolo.model.common.value.PaymentMeansCode;
import io.github.nalukit.paniolo.model.extended.AdvancePayment;
import io.github.nalukit.paniolo.model.extended.DocumentReference;
import io.github.nalukit.paniolo.model.extended.ExtendedInformation;
import io.github.nalukit.paniolo.model.extended.FinancialAdjustment;
import io.github.nalukit.paniolo.model.extended.LogisticsServiceCharge;
import io.github.nalukit.paniolo.model.extended.PaymentTerm;
import io.github.nalukit.paniolo.model.extended.PaymentTermsAdjustment;

import java.math.BigDecimal;
import java.time.LocalDate;

final class FacturXInvoices {
  private FacturXInvoices() {
  }

  private static Party party(String name,
                             String city) {
    return Party.create()
                .name(name)
                .address(Address.create()
                                .street("Hauptstraße 3")
                                .city(city)
                                .postalCode("50667")
                                .country(Country.DE)
                                .build())
                .build();
  }

  /**
   * All header information of the profile EXTENDED that Paniolo supports.
   */
  static ExtendedInformation extended() {
    var standard = TaxCategory.standard(new BigDecimal("19"));
    return ExtendedInformation.create()
                              .salesAgent(party("Vertrieb Nord GmbH",
                                                "Bremen"))
                              .buyerAgent(party("Einkaufsbüro Süd",
                                                "München"))
                              .buyerTaxRepresentative(Party.create()
                                                           .name("Steuerkanzlei Kunde")
                                                           .address(party("Steuerkanzlei Kunde",
                                                                          "Hamburg").address())
                                                           .vatId("DE987654321")
                                                           .build())
                              .productEndUser(party("Endkunde AG",
                                                    "Kiel"))
                              .shipFrom(party("Lager West",
                                              "Dortmund"))
                              .ultimateShipTo(party("Filiale Ost",
                                                    "Dresden"))
                              .invoicer(party("Abrechnungsstelle GmbH",
                                              "Köln"))
                              .invoicee(party("Rechnungseingang Kunde",
                                              "Hamburg"))
                              .payer(party("Konzernmutter Kunde",
                                           "Frankfurt am Main"))
                              .deliveryTermsCode("FCA")
                              .quotationReference(new DocumentReference("ANG-7",
                                                                        LocalDate.of(2026,
                                                                                     10,
                                                                                     1)))
                              .deliveryNoteReference(new DocumentReference("LS-42",
                                                                           LocalDate.of(2026,
                                                                                        10,
                                                                                        26)))
                              .addUltimateCustomerOrderReference(DocumentReference.of("EK-1"))
                              .addUltimateCustomerOrderReference(DocumentReference.of("EK-2"))
                              .addPaymentTerm(PaymentTerm.create()
                                                         .description("2 % Skonto bei Zahlung innerhalb von 10 Tagen")
                                                         .discount(PaymentTermsAdjustment.of(10,
                                                                                             new BigDecimal("2")))
                                                         // EXTENDED payee of a term, with identifier schemes (BT-60-1, BT-61-1).
                                                         .payee(Payee.create()
                                                                     .name("Factoring GmbH")
                                                                     .identifier("4000001000036",
                                                                                 "0088")
                                                                     .legalRegistrationId("HRB 999",
                                                                                          "0002")
                                                                     .build())
                                                         .build())
                              .addPaymentTerm(PaymentTerm.create()
                                                         .description("Verzugszinsen nach 30 Tagen")
                                                         .penalty(new PaymentTermsAdjustment(null,
                                                                                             30,
                                                                                             null,
                                                                                             new BigDecimal("5"),
                                                                                             null))
                                                         .build())
                              .addLogisticsServiceCharge(new LogisticsServiceCharge("Versand",
                                                                                    new BigDecimal("10"),
                                                                                    standard))
                              .addAdvancePayment(AdvancePayment.create()
                                                               .amount(new BigDecimal("50"))
                                                               .receivedDate(LocalDate.of(2026,
                                                                                          10,
                                                                                          2))
                                                               .tax(standard)
                                                               .taxAmount(new BigDecimal("7.98"))
                                                               .invoiceReference("AZ-1")
                                                               .build())
                              .documentName("Rechnung")
                              .languageId("deu")
                              .copyIndicator(false)
                              .testIndicator(true)
                              .effectiveDate(LocalDate.of(2026,
                                                          10,
                                                          1))
                              .invoiceIssuerReference("AS-2026-17")
                              .transportModeCode("3")
                              .deliveryTermsLocationName("Hamburg")
                              .deliveryTermsLocationCountry(Country.DE)
                              .addFinancialAdjustment(new FinancialAdjustment("Gutschrift Reklamation",
                                                                              new BigDecimal("-5")))
                              .build();
  }

  static Invoice.Builder invoice(FacturXProfile profile,
                                 InvoiceTypeCode type) {
    boolean basic = profile == FacturXProfile.BASIC;
    var seller = Party.create()
                      .name("Muster GmbH")
                      .address(Address.create()
                                      .street("Musterstraße 1")
                                      .city("Berlin")
                                      .postalCode("10115")
                                      .country(Country.DE)
                                      .build())
                      .vatId("DE123456788")
                      .electronicAddress(ElectronicAddress.create()
                                                          .value("rechnung@example.org")
                                                          .schemeId("EM")
                                                          .build())
                      // Profile BASIC has no seller contact (BG-6).
                      .contact(basic ?
                               null :
                               Contact.create()
                                      .name("Erika Muster")
                                      .telephone("+49 30 123456")
                                      .email("rechnung@example.org")
                                      .build())
                      .build();
    var buyer = Party.create()
                     .name("Kunde GmbH")
                     .address(Address.create()
                                     .street("Kundenweg 2")
                                     .city("Hamburg")
                                     .postalCode("20095")
                                     .country(Country.DE)
                                     .build())
                     .electronicAddress(ElectronicAddress.create()
                                                         .value("einkauf@example.org")
                                                         .schemeId("EM")
                                                         .build())
                     .build();
    return Invoice.create()
                  .number("R-1")
                  .typeCode(type)
                  .processControl(switch (profile) {
                    case BASIC -> ProcessControl.facturXBasic();
                    case EN16931 -> ProcessControl.en16931();
                    case EXTENDED -> ProcessControl.facturXExtended();
                    case XRECHNUNG -> ProcessControl.xrechnung();
                    case EXTENDED_CTC_FR -> ProcessControl.facturXExtendedCtcFr("B1");
                    case MINIMUM, BASIC_WL -> throw new IllegalArgumentException("Paniolo does not write " + profile);
                  })
                  .issueDate(LocalDate.of(2026,
                                          10,
                                          27))
                  .dueDate(LocalDate.of(2026,
                                        11,
                                        10))
                  .buyerReference("04011000-12345-03")
                  .seller(seller)
                  .buyer(buyer)
                  .delivery(DeliveryInformation.create()
                                               .actualDeliveryDate(LocalDate.of(2026,
                                                                                10,
                                                                                26))
                                               .build())
                  .paymentInstruction(PaymentInstruction.create()
                                                        .meansCode(PaymentMeansCode.CODE_58)
                                                        .addCreditTransfer(CreditTransfer.create()
                                                                                         .iban("DE89370400440532013000")
                                                                                         .accountName(basic ?
                                                                                                      null :
                                                                                                      "Muster GmbH")
                                                                                         .build())
                                                        .remittanceInformation("R-1")
                                                        .build())
                  .addLine(InvoiceLine.create()
                                      .id("1")
                                      .name("Beratung")
                                      .quantity(new BigDecimal("2"))
                                      .unitCode("HUR")
                                      .netUnitPrice(new BigDecimal("100.00"))
                                      .tax(TaxCategory.standard(new BigDecimal("19")))
                                      .build());
  }
}

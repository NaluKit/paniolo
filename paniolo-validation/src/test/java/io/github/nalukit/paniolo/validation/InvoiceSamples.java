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
package io.github.nalukit.paniolo.validation;

import io.github.nalukit.paniolo.model.Invoice;
import io.github.nalukit.paniolo.model.common.AdditionalSupportingDocument;
import io.github.nalukit.paniolo.model.common.Address;
import io.github.nalukit.paniolo.model.common.Attachment;
import io.github.nalukit.paniolo.model.common.Contact;
import io.github.nalukit.paniolo.model.common.CreditTransfer;
import io.github.nalukit.paniolo.model.common.DeliveryInformation;
import io.github.nalukit.paniolo.model.common.DirectDebit;
import io.github.nalukit.paniolo.model.common.DocumentAdjustment;
import io.github.nalukit.paniolo.model.common.ElectronicAddress;
import io.github.nalukit.paniolo.model.common.InvoiceLine;
import io.github.nalukit.paniolo.model.common.InvoiceNote;
import io.github.nalukit.paniolo.model.common.InvoicePeriod;
import io.github.nalukit.paniolo.model.common.Party;
import io.github.nalukit.paniolo.model.common.Payee;
import io.github.nalukit.paniolo.model.common.PaymentCardInformation;
import io.github.nalukit.paniolo.model.common.PaymentInstruction;
import io.github.nalukit.paniolo.model.common.PrecedingInvoiceReference;
import io.github.nalukit.paniolo.model.common.References;
import io.github.nalukit.paniolo.model.common.SellerTaxRepresentativeParty;
import io.github.nalukit.paniolo.model.common.TaxCategory;
import io.github.nalukit.paniolo.model.common.value.AllowanceChargeIdentification;
import io.github.nalukit.paniolo.model.common.value.Country;
import io.github.nalukit.paniolo.model.common.value.CurrencyCode;
import io.github.nalukit.paniolo.model.common.value.DateTimeQualifier;
import io.github.nalukit.paniolo.model.common.value.DutyTaxFeeCategory;
import io.github.nalukit.paniolo.model.common.value.PaymentMeansCode;
import io.github.nalukit.paniolo.model.common.value.TextSubjectQualifier;

import java.math.BigDecimal;
import java.time.LocalDate;

final class InvoiceSamples {
  static Invoice.Builder invoice() {
    var seller = Party.create()
                      .name("Muster & Söhne GmbH")
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
                      .contact(Contact.create()
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
                     .vatId("DE987654321")
                     .electronicAddress(ElectronicAddress.create()
                                                         .value("einkauf@example.org")
                                                         .schemeId("EM")
                                                         .build())
                     .contact(Contact.create()
                                     .name("Max Kunde")
                                     .telephone("+49 40 123456")
                                     .email("einkauf@example.org")
                                     .build())
                     .build();
    return Invoice.create()
                  .number("R-2026-001")
                  .issueDate(LocalDate.of(2026,
                                          10,
                                          27))
                  .delivery(DeliveryInformation.create()
                                               .actualDeliveryDate(LocalDate.of(2026,
                                                                                10,
                                                                                26))
                                               .build())
                  .dueDate(LocalDate.of(2026,
                                        11,
                                        10))
                  .seller(seller)
                  .buyer(buyer)
                  .buyerReference("04011000-12345-03")
                  .paymentInstruction(PaymentInstruction.create()
                                                        .meansCode(PaymentMeansCode.CODE_58)
                                                        .addCreditTransfer(CreditTransfer.create()
                                                                                         .iban("DE89370400440532013000")
                                                                                         .accountName("Muster & Söhne GmbH")
                                                                                         .bic("COBADEFFXXX")
                                                                                         .build())
                                                        .remittanceInformation("R-2026-001")
                                                        .build());
  }

  static InvoiceLine line(String id,
                          String price,
                          TaxCategory tax) {
    return InvoiceLine.create()
                      .id(id)
                      .name("Beratung <XML> & Einführung")
                      .quantity(new BigDecimal("2"))
                      .unitCode("HUR")
                      .netUnitPrice(new BigDecimal(price))
                      .tax(tax)
                      .build();
  }

  static TaxCategory standard() {
    return TaxCategory.standard(new BigDecimal("19"));
  }

  static Invoice.Builder basic(Invoice.Builder builder) {
    var tax = standard();
    return builder.addLine(line("1",
                                "100",
                                tax))
                  .addLine(line("2",
                                "25",
                                TaxCategory.standard(new BigDecimal("7"))))
                  .addAdjustment(DocumentAdjustment.createAllowance()
                                                   .amount(new BigDecimal("10"))
                                                   .reason("Treuerabatt")
                                                   .tax(tax)
                                                   .build())
                  .addAdjustment(DocumentAdjustment.createCharge()
                                                   .amount(new BigDecimal("5"))
                                                   .reason("Versand")
                                                   .tax(tax)
                                                   .build())
                  .paidAmount(new BigDecimal("20"))
                  .addNote("Vielen Dank für Ihren Auftrag.");
  }

  static Invoice.Builder exempt(Invoice.Builder builder) {
    var tax = TaxCategory.create()
                         .code(DutyTaxFeeCategory.E)
                         .rate(BigDecimal.ZERO)
                         .exemptionReason("Steuerbefreit")
                         .exemptionReasonCode("VATEX-EU-132")
                         .build();
    return builder.addLine(line("1",
                                "100",
                                tax));
  }

  static Invoice.Builder outsideScope(Invoice.Builder builder) {
    var tax = TaxCategory.create()
                         .code(DutyTaxFeeCategory.O)
                         .exemptionReason("Not subject to VAT")
                         .exemptionReasonCode("VATEX-EU-O")
                         .build();
    var sample = invoice().addLine(line("1",
                                        "1",
                                        standard()))
                          .build();
    return builder.seller(Party.create()
                               .name(sample.seller()
                                           .name())
                               .address(sample.seller()
                                              .address())
                               .taxId("123/456/78901")
                               .electronicAddress(sample.seller()
                                                        .electronicAddress())
                               .contact(sample.seller()
                                              .contact())
                               .identifier("SELLER-001")
                               .build())
                  .buyer(Party.create()
                              .name(sample.buyer()
                                          .name())
                              .address(sample.buyer()
                                             .address())
                              .electronicAddress(sample.buyer()
                                                       .electronicAddress())
                              .build())
                  .addLine(line("1",
                                "100",
                                tax));
  }

  static Invoice.Builder documentLevelTerms(Invoice.Builder builder) {
    var tax = standard();
    var address = Address.create()
                         .street("Lieferstraße 5")
                         .additionalStreet("Hinterhof")
                         .additionalLine("Tor 3")
                         .city("Köln")
                         .postalCode("50667")
                         .countrySubdivision("NRW")
                         .country(Country.DE)
                         .build();
    return builder.addNote(InvoiceNote.create()
                                      .subjectCode(TextSubjectQualifier.AAI)
                                      .text("Allgemeine Information")
                                      .build())
                  .references(References.create()
                                        .projectReference("PRJ-1")
                                        .contractReference("V-7")
                                        .purchaseOrderReference("PO-42")
                                        .salesOrderReference("SO-9")
                                        .receivingAdviceReference("WE-1")
                                        .despatchAdviceReference("LS-1")
                                        .tenderOrLotReference("LOS-2")
                                        .invoicedObjectId("ZAEHLER-77")
                                        .invoicedObjectSchemeId("AWV")
                                        .buyerAccountingReference("KST-4711")
                                        .build())
                  .addPrecedingInvoiceReference(PrecedingInvoiceReference.create()
                                                                         .number("R-2026-000")
                                                                         .issueDate(LocalDate.of(2026,
                                                                                                 9,
                                                                                                 30))
                                                                         .build())
                  .invoicePeriod(InvoicePeriod.create()
                                              .startDate(LocalDate.of(2026,
                                                                      10,
                                                                      1))
                                              .endDate(LocalDate.of(2026,
                                                                    10,
                                                                    31))
                                              .build())
                  .taxPointDate(LocalDate.of(2026,
                                             10,
                                             26))
                  .delivery(DeliveryInformation.create()
                                               .partyName("Lager Köln")
                                               .locationId("LOC-1")
                                               .actualDeliveryDate(LocalDate.of(2026,
                                                                                10,
                                                                                26))
                                               .address(address)
                                               .build())
                  .taxCurrencyCode(CurrencyCode.USD)
                  .taxAmountInTaxCurrency(new BigDecimal("22.10"))
                  .payee(Payee.create()
                              .name("Factoring AG")
                              .identifier("PAYEE-1")
                              .legalRegistrationId("HRB 1")
                              .build())
                  .taxRepresentative(SellerTaxRepresentativeParty.create()
                                                                 .name("Steuer GmbH")
                                                                 .vatId("DE111111125")
                                                                 .address(address)
                                                                 .build())
                  .paymentInstruction(PaymentInstruction.create()
                                                        .meansCode(PaymentMeansCode.CODE_58)
                                                        .meansText("Überweisung")
                                                        .remittanceInformation("R-2026-001")
                                                        .addCreditTransfer(CreditTransfer.create()
                                                                                         .iban("DE89370400440532013000")
                                                                                         .bic("COBADEFFXXX")
                                                                                         .build())
                                                        .addCreditTransfer(CreditTransfer.create()
                                                                                         .iban("DE02120300000000202051")
                                                                                         .accountName("Zweitkonto")
                                                                                         .build())
                                                        .build())
                  .addSupportingDocument(AdditionalSupportingDocument.create()
                                                                     .reference("Stundenzettel")
                                                                     .description("Stunden Oktober")
                                                                     .attachment(Attachment.create()
                                                                                           .content("%PDF-1.4".getBytes())
                                                                                           .mimeCode("application/pdf")
                                                                                           .filename("stunden.pdf")
                                                                                           .build())
                                                                     .build())
                  .addSupportingDocument(AdditionalSupportingDocument.create()
                                                                     .reference("Lieferschein")
                                                                     .externalLocation("https://example.org/ls-1.pdf")
                                                                     .build())
                  .addLine(line("1",
                                "100",
                                tax))
                  .addAdjustment(DocumentAdjustment.createAllowance()
                                                   .baseAmount(new BigDecimal("100"))
                                                   .percentage(new BigDecimal("10"))
                                                   .amount(new BigDecimal("10"))
                                                   .reasonCode(AllowanceChargeIdentification.CODE_95)
                                                   .reason("Rabatt")
                                                   .tax(tax)
                                                   .build())
                  .addAdjustment(DocumentAdjustment.createCharge()
                                                   .amount(new BigDecimal("5"))
                                                   .reasonCode("FC")
                                                   .reason("Fracht")
                                                   .tax(tax)
                                                   .build())
                  .roundingAmount(new BigDecimal("0.01"));
  }

  static Invoice.Builder directDebit(Invoice.Builder builder) {
    return builder.paymentInstruction(PaymentInstruction.create()
                                                        .meansCode(PaymentMeansCode.CODE_59)
                                                        .remittanceInformation("R-2026-001")
                                                        .directDebit(DirectDebit.create()
                                                                                .mandateReference("MANDAT-1")
                                                                                .creditorId("DE98ZZZ09999999999")
                                                                                .debitedAccountId("DE02120300000000202051")
                                                                                .build())
                                                        .build())
                  .addLine(line("1",
                                "100",
                                standard()));
  }

  static Invoice.Builder paymentCardAndTaxPointDateCode(Invoice.Builder builder) {
    return builder.taxPointDateCode(DateTimeQualifier.CODE_35)
                  .paymentInstruction(PaymentInstruction.create()
                                                        .meansCode(PaymentMeansCode.CODE_54)
                                                        .paymentCard(PaymentCardInformation.create()
                                                                                           .primaryAccountNumber("1234")
                                                                                           .holderName("Max Kunde")
                                                                                           .build())
                                                        .build())
                  .addLine(line("1",
                                "100",
                                standard()));
  }
}

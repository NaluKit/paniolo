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
package io.github.nalukit.paniolo.model;

import io.github.nalukit.paniolo.model.common.Address;
import io.github.nalukit.paniolo.model.common.Attachment;
import io.github.nalukit.paniolo.model.common.Contact;
import io.github.nalukit.paniolo.model.common.CreditTransfer;
import io.github.nalukit.paniolo.model.common.DirectDebit;
import io.github.nalukit.paniolo.model.common.DocumentAdjustment;
import io.github.nalukit.paniolo.model.common.DocumentTotals;
import io.github.nalukit.paniolo.model.common.ElectronicAddress;
import io.github.nalukit.paniolo.model.common.InvoiceLine;
import io.github.nalukit.paniolo.model.common.InvoicePeriod;
import io.github.nalukit.paniolo.model.common.Party;
import io.github.nalukit.paniolo.model.common.PaymentCardInformation;
import io.github.nalukit.paniolo.model.common.PaymentInstruction;
import io.github.nalukit.paniolo.model.common.ProcessControl;
import io.github.nalukit.paniolo.model.common.TaxCategory;
import io.github.nalukit.paniolo.model.common.value.AllowanceChargeIdentification;
import io.github.nalukit.paniolo.model.common.value.Country;
import io.github.nalukit.paniolo.model.common.value.CurrencyCode;
import io.github.nalukit.paniolo.model.common.value.DateTimeQualifier;
import io.github.nalukit.paniolo.model.common.value.DutyTaxFeeCategory;
import io.github.nalukit.paniolo.model.common.value.InvoiceTypeCode;
import io.github.nalukit.paniolo.model.common.value.PaymentMeansCode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InvoiceTest {
  private static final TaxCategory VAT = TaxCategory.standard(new BigDecimal("19"));

  private Invoice.Builder invoice() {
    var party = Party.create()
                     .name("Test GmbH")
                     .address(Address.create()
                                     .street("Weg 1")
                                     .city("Berlin")
                                     .postalCode("10115")
                                     .country(Country.DE)
                                     .build())
                     .build();
    return Invoice.create()
                  .number("R-1")
                  .issueDate(LocalDate.of(2026,
                                          10,
                                          27))
                  .seller(party)
                  .buyer(party);
  }

  private InvoiceLine line(String id,
                           String quantity,
                           String price,
                           TaxCategory tax) {
    return InvoiceLine.create()
                      .id(id)
                      .name("Beratung")
                      .quantity(new BigDecimal(quantity))
                      .unitCode("HUR")
                      .netUnitPrice(new BigDecimal(price))
                      .tax(tax)
                      .build();
  }

  @Test
  void calculatesAllowancesChargesVatAndPrepayment() {
    var invoice = invoice().addLine(line("1",
                                         "2",
                                         "100",
                                         VAT))
                           .addAdjustment(DocumentAdjustment.createAllowance()
                                                            .amount(new BigDecimal("10"))
                                                            .reason("Rabatt")
                                                            .tax(VAT)
                                                            .build())
                           .addAdjustment(DocumentAdjustment.createCharge()
                                                            .amount(new BigDecimal("5"))
                                                            .reason("Versand")
                                                            .tax(VAT)
                                                            .build())
                           .paidAmount(new BigDecimal("20"))
                           .build();
    assertEquals(new BigDecimal("195.00"),
                 invoice.totals()
                        .taxExclusiveAmount());
    assertEquals(new BigDecimal("37.05"),
                 invoice.totals()
                        .taxAmount());
    assertEquals(new BigDecimal("212.05"),
                 invoice.totals()
                        .payableAmount());
  }

  @Test
  void roundsLinesBeforeGroupingVatAndNormalizesRateScale() {
    var invoice = invoice().addLine(line("1",
                                         "1",
                                         "0.335",
                                         VAT))
                           .addLine(line("2",
                                         "1",
                                         "0.335",
                                         TaxCategory.standard(new BigDecimal("19.00"))))
                           .build();
    assertEquals(new BigDecimal("0.68"),
                 invoice.totals()
                        .lineTotal());
    assertEquals(new BigDecimal("0.13"),
                 invoice.totals()
                        .taxAmount());
    assertEquals(1,
                 invoice.totals()
                        .vatBreakdowns()
                        .size());
  }

  @Test
  void separatesVatRatesAndHandlesPriceBaseQuantity() {
    var invoice = invoice().addLine(InvoiceLine.create()
                                               .id("1")
                                               .name("Schrauben")
                                               .quantity(new BigDecimal("250"))
                                               .unitCode("H87")
                                               .netUnitPrice(new BigDecimal("10"))
                                               .priceBaseQuantity(new BigDecimal("100"))
                                               .tax(VAT)
                                               .build())
                           .addLine(line("2",
                                         "1",
                                         "100",
                                         TaxCategory.standard(new BigDecimal("7"))))
                           .build();
    assertEquals(new BigDecimal("125.00"),
                 invoice.totals()
                        .lineTotal());
    assertEquals(new BigDecimal("11.75"),
                 invoice.totals()
                        .taxAmount());
    assertEquals(2,
                 invoice.totals()
                        .vatBreakdowns()
                        .size());
  }

  @Test
  void snapshotsBuilderAndCallerLists() {
    var lines = new ArrayList<InvoiceLine>();
    lines.add(line("1",
                   "1",
                   "100",
                   VAT));
    var builder = invoice().lines(lines);
    var invoice = builder.build();
    lines.clear();
    builder.addLine(line("2",
                         "1",
                         "10",
                         VAT));
    assertEquals(1,
                 invoice.lines()
                        .size());
    assertThrows(UnsupportedOperationException.class,
                 () -> invoice.lines()
                              .clear());
  }

  @Test
  void rejectsMissingAndDuplicateLinesAndInvalidPriceBase() {
    assertThrows(IllegalArgumentException.class,
                 () -> invoice().build());
    assertThrows(IllegalArgumentException.class,
                 () -> invoice().addLine(line("1",
                                              "1",
                                              "1",
                                              VAT))
                                .addLine(line("1",
                                              "2",
                                              "2",
                                              VAT))
                                .build());
    assertThrows(IllegalArgumentException.class,
                 () -> InvoiceLine.create()
                                  .id("1")
                                  .name("Test")
                                  .quantity(BigDecimal.ONE)
                                  .unitCode("H87")
                                  .netUnitPrice(BigDecimal.ONE)
                                  .priceBaseQuantity(BigDecimal.ZERO)
                                  .tax(VAT)
                                  .build());
  }

  @Test
  void creditNoteCodesAreNotMistakenForInvoices() {
    assertTrue(InvoiceTypeCode.CODE_381.isCreditNote());
    assertFalse(InvoiceTypeCode.CODE_381.isInvoice());
    assertTrue(InvoiceTypeCode.CODE_383.isDebitNote());
    assertFalse(InvoiceTypeCode.CODE_383.isCreditNote());
  }

  @Test
  void buildersMatchRecordConstructorsAndApplyDefaults() {
    assertEquals(new InvoiceLine("1",
                                 "Beratung",
                                 BigDecimal.TEN,
                                 io.github.nalukit.paniolo.model.common.value.UnitCode.HUR,
                                 BigDecimal.ONE,
                                 VAT),
                 InvoiceLine.create()
                            .id("1")
                            .name("Beratung")
                            .quantity(BigDecimal.TEN)
                            .unitCode("HUR")
                            .netUnitPrice(BigDecimal.ONE)
                            .tax(VAT)
                            .build());
    assertEquals(new Party("A",
                           new Address("S",
                                       "C",
                                       "1",
                                       Country.DE),
                           "DE1",
                           "T",
                           null,
                           new Contact("N",
                                       "T",
                                       "E"),
                           "ID"),
                 Party.create()
                      .name("A")
                      .address(Address.create()
                                      .street("S")
                                      .city("C")
                                      .postalCode("1")
                                      .country(Country.DE)
                                      .build())
                      .vatId("DE1")
                      .taxId("T")
                      .contact(Contact.create()
                                      .name("N")
                                      .telephone("T")
                                      .email("E")
                                      .build())
                      .identifier("ID")
                      .build());
    assertEquals(new PaymentInstruction(PaymentMeansCode.CODE_58,
                                        null,
                                        "Ref",
                                        List.of(new CreditTransfer("IBAN",
                                                                   "Name",
                                                                   "BIC")),
                                        null,
                                        null),
                 PaymentInstruction.create()
                                   .meansCode(PaymentMeansCode.CODE_58)
                                   .remittanceInformation("Ref")
                                   .addCreditTransfer(CreditTransfer.create()
                                                                    .iban("IBAN")
                                                                    .accountName("Name")
                                                                    .bic("BIC")
                                                                    .build())
                                   .build());
    assertEquals(new ElectronicAddress("a@b.de",
                                       "EM"),
                 ElectronicAddress.create()
                                  .value("a@b.de")
                                  .schemeId("EM")
                                  .build());
    assertEquals(new TaxCategory(DutyTaxFeeCategory.E,
                                 BigDecimal.ZERO,
                                 "Frei",
                                 "VATEX-EU-132"),
                 TaxCategory.create()
                            .code(DutyTaxFeeCategory.E)
                            .rate(BigDecimal.ZERO)
                            .exemptionReason("Frei")
                            .exemptionReasonCode("VATEX-EU-132")
                            .build());
    assertTrue(DocumentAdjustment.createCharge()
                                 .amount(BigDecimal.ONE)
                                 .reason("R")
                                 .tax(VAT)
                                 .build()
                                 .charge());
    assertFalse(DocumentAdjustment.createAllowance()
                                  .amount(BigDecimal.ONE)
                                  .reason("R")
                                  .tax(VAT)
                                  .build()
                                  .charge());
    assertEquals(ProcessControl.xrechnung(),
                 ProcessControl.create()
                               .build());
    assertEquals("x",
                 ProcessControl.create()
                               .specificationId("x")
                               .build()
                               .specificationId());
  }

  @Test
  void invoiceWithoutLinesUsesStatedTotals() {
    var invoice = invoice().processControl(ProcessControl.facturXBasicWl())
                           .addAdjustment(DocumentAdjustment.createAllowance()
                                                            .amount(new BigDecimal("10"))
                                                            .reason("Rabatt")
                                                            .tax(VAT)
                                                            .build())
                           .paidAmount(new BigDecimal("50"))
                           .statedTotals(DocumentTotals.create()
                                                       .lineTotal(new BigDecimal("110"))
                                                       .addVatBreakdown(VAT,
                                                                        new BigDecimal("100"),
                                                                        new BigDecimal("19"))
                                                       .build())
                           .build();
    assertTrue(invoice.hasStatedTotals());
    var totals = invoice.totals();
    assertEquals(new BigDecimal("110.00"),
                 totals.lineTotal());
    assertEquals(new BigDecimal("10.00"),
                 totals.allowanceTotal());
    assertEquals(new BigDecimal("100.00"),
                 totals.taxExclusiveAmount());
    assertEquals(new BigDecimal("119.00"),
                 totals.taxInclusiveAmount());
    assertEquals(new BigDecimal("69.00"),
                 totals.payableAmount());
    assertEquals(1,
                 totals.vatBreakdowns()
                       .size());

    // MINIMUM: country-only seller address, buyer without address, no line total.
    var minimum = invoice().processControl(ProcessControl.facturXMinimum())
                           .seller(Party.create()
                                        .name("Muster GmbH")
                                        .address(Address.ofCountry(Country.DE))
                                        .vatId("DE123456788")
                                        .build())
                           .buyer(Party.create()
                                       .name("Kunde GmbH")
                                       .build())
                           .statedTotals(DocumentTotals.create()
                                                       .taxBasisTotal(new BigDecimal("100"))
                                                       .taxTotal(new BigDecimal("19"))
                                                       .build())
                           .build();
    assertNull(minimum.totals()
                      .lineTotal());
    assertEquals(new BigDecimal("119.00"),
                 minimum.totals()
                        .payableAmount());
  }

  @Test
  void rejectsInconsistentStatedTotals() {
    var totals = DocumentTotals.create()
                               .lineTotal(new BigDecimal("100"))
                               .addVatBreakdown(VAT,
                                                new BigDecimal("100"),
                                                new BigDecimal("19"))
                               .build();
    // BR-CO-14 and the sum of the taxable amounts.
    assertThrows(IllegalArgumentException.class,
                 () -> DocumentTotals.create()
                                     .taxTotal(new BigDecimal("20"))
                                     .addVatBreakdown(VAT,
                                                      new BigDecimal("100"),
                                                      new BigDecimal("19"))
                                     .build());
    assertThrows(IllegalArgumentException.class,
                 () -> DocumentTotals.create()
                                     .taxBasisTotal(new BigDecimal("90"))
                                     .addVatBreakdown(VAT,
                                                      new BigDecimal("100"),
                                                      new BigDecimal("19"))
                                     .build());
    // BR-CO-13: line total minus allowances plus charges.
    assertThrows(IllegalArgumentException.class,
                 () -> invoice().statedTotals(totals)
                                .addAdjustment(DocumentAdjustment.createCharge()
                                                                 .amount(BigDecimal.ONE)
                                                                 .reason("Versand")
                                                                 .tax(VAT)
                                                                 .build())
                                .build());
    // Lines and stated totals exclude each other; no lines need stated totals.
    assertThrows(IllegalArgumentException.class,
                 () -> invoice().statedTotals(totals)
                                .addLine(line("1",
                                              "1",
                                              "100",
                                              VAT))
                                .build());
    assertThrows(IllegalArgumentException.class,
                 () -> invoice().build());
    // Invoices with lines need complete addresses of seller and buyer.
    assertThrows(IllegalArgumentException.class,
                 () -> invoice().buyer(Party.create()
                                            .name("Kunde")
                                            .address(Address.ofCountry(Country.DE))
                                            .build())
                                .addLine(line("1",
                                              "1",
                                              "100",
                                              VAT))
                                .build());
    // BR-08: the seller always needs an address.
    assertThrows(IllegalArgumentException.class,
                 () -> invoice().seller(Party.create()
                                             .name("Muster")
                                             .build())
                                .statedTotals(totals)
                                .build());
  }

  @Test
  void buildersValidateOnBuild() {
    assertThrows(IllegalArgumentException.class,
                 () -> Address.create()
                              .street("S")
                              .city("C")
                              .build());
    // A party without address exists only as buyer of a Factur-X MINIMUM invoice.
    assertThrows(IllegalArgumentException.class,
                 () -> Party.requireAddress(Party.create()
                                                 .name("A")
                                                 .build(),
                                            "buyer"));
    assertThrows(NullPointerException.class,
                 () -> PaymentInstruction.create()
                                         .build());
  }

  @Test
  void roundingAmountIsAddedToPayableAmount() {
    var invoice = invoice().addLine(line("1",
                                         "1",
                                         "100",
                                         VAT))
                           .roundingAmount(new BigDecimal("-0.004"))
                           .build();
    assertEquals(new BigDecimal("0.00"),
                 invoice.roundingAmount());
    assertEquals(new BigDecimal("118.99"),
                 invoice().addLine(line("1",
                                        "1",
                                        "100",
                                        VAT))
                          .roundingAmount(new BigDecimal("-0.01"))
                          .build()
                          .totals()
                          .payableAmount());
    assertNull(invoice().addLine(line("1",
                                      "1",
                                      "100",
                                      VAT))
                        .build()
                        .totals()
                        .roundingAmount());
  }

  @Test
  void rejectsInconsistentDocumentLevelTerms() {
    var builder = invoice().addLine(line("1",
                                         "1",
                                         "100",
                                         VAT));
    assertThrows(IllegalArgumentException.class,
                 () -> builder.taxCurrencyCode(CurrencyCode.USD)
                              .build());
    builder.taxCurrencyCode(null);
    assertThrows(IllegalArgumentException.class,
                 () -> builder.taxPointDate(LocalDate.of(2026,
                                                         10,
                                                         1))
                              .taxPointDateCode(DateTimeQualifier.CODE_35)
                              .build());
    builder.taxPointDate(null);
    assertThrows(IllegalArgumentException.class,
                 () -> builder.taxPointDateCode(DateTimeQualifier.CODE_2)
                              .build());
    assertThrows(IllegalArgumentException.class,
                 () -> InvoicePeriod.create()
                                    .build());
    assertThrows(IllegalArgumentException.class,
                 () -> InvoicePeriod.create()
                                    .startDate(LocalDate.of(2026,
                                                            10,
                                                            2))
                                    .endDate(LocalDate.of(2026,
                                                          10,
                                                          1))
                                    .build());
    assertThrows(IllegalArgumentException.class,
                 () -> DocumentAdjustment.createAllowance()
                                         .amount(BigDecimal.ONE)
                                         .tax(VAT)
                                         .build());
    assertThrows(IllegalArgumentException.class,
                 () -> DocumentAdjustment.createAllowance()
                                         .amount(BigDecimal.ONE)
                                         .reasonCode("XX")
                                         .tax(VAT)
                                         .build());
    assertEquals("95",
                 DocumentAdjustment.createAllowance()
                                   .amount(BigDecimal.ONE)
                                   .reasonCode(AllowanceChargeIdentification.CODE_95)
                                   .tax(VAT)
                                   .build()
                                   .reasonCode());
  }

  @Test
  void paymentMeansRequireMatchingPaymentGroup() {
    var account = CreditTransfer.create()
                                .iban("DE89370400440532013000")
                                .build();
    var card = PaymentCardInformation.create()
                                     .primaryAccountNumber("1234")
                                     .build();
    var mandate = DirectDebit.create()
                             .mandateReference("M")
                             .creditorId("C")
                             .debitedAccountId("D")
                             .build();
    assertThrows(IllegalArgumentException.class,
                 () -> PaymentInstruction.create()
                                         .meansCode(PaymentMeansCode.CODE_58)
                                         .build());
    // A card next to the account is left to the invoice, which allows it in EXTENDED-CTC-FR only.
    assertTrue(PaymentInstruction.create()
                                 .meansCode(PaymentMeansCode.CODE_58)
                                 .addCreditTransfer(account)
                                 .paymentCard(card)
                                 .build()
                                 .hasCardWithCreditTransfer());
    assertThrows(IllegalArgumentException.class,
                 () -> PaymentInstruction.create()
                                         .meansCode(PaymentMeansCode.CODE_58)
                                         .addCreditTransfer(account)
                                         .directDebit(mandate)
                                         .build());
    assertThrows(IllegalArgumentException.class,
                 () -> PaymentInstruction.create()
                                         .meansCode(PaymentMeansCode.CODE_54)
                                         .build());
    assertThrows(IllegalArgumentException.class,
                 () -> PaymentInstruction.create()
                                         .meansCode(PaymentMeansCode.CODE_59)
                                         .build());
    assertThrows(IllegalArgumentException.class,
                 () -> PaymentInstruction.create()
                                         .meansCode(PaymentMeansCode.CODE_10)
                                         .addCreditTransfer(account)
                                         .build());
    assertNotNull(PaymentInstruction.create()
                                    .meansCode(PaymentMeansCode.CODE_54)
                                    .paymentCard(card)
                                    .build());
    assertNotNull(PaymentInstruction.create()
                                    .meansCode(PaymentMeansCode.CODE_59)
                                    .directDebit(mandate)
                                    .build());
    assertNotNull(PaymentInstruction.create()
                                    .meansCode(PaymentMeansCode.CODE_10)
                                    .build());
    // Payment to bank account may name the account (EN 16931 requires it for 30 and 58 only).
    assertEquals(List.of(account),
                 PaymentInstruction.create()
                                   .meansCode(PaymentMeansCode.CODE_42)
                                   .addCreditTransfer(account)
                                   .build()
                                   .creditTransfers());
    assertNotNull(PaymentInstruction.create()
                                    .meansCode(PaymentMeansCode.CODE_42)
                                    .build());
    assertThrows(IllegalArgumentException.class,
                 () -> PaymentInstruction.create()
                                         .meansCode(PaymentMeansCode.CODE_42)
                                         .addCreditTransfer(account)
                                         .directDebit(mandate)
                                         .build());
  }

  @Test
  void attachmentsCopyContentAndCompareByValue() {
    var bytes = new byte[] { 1,
                             2,
                             3 };
    var attachment = Attachment.create()
                               .content(bytes)
                               .mimeCode("application/pdf")
                               .filename("a.pdf")
                               .build();
    bytes[0]                = 9;
    attachment.content()[1] = 9;
    assertArrayEquals(new byte[] { 1,
                                   2,
                                   3 },
                      attachment.content());
    assertEquals(attachment,
                 new Attachment(new byte[] { 1,
                                             2,
                                             3 },
                                "application/pdf",
                                "a.pdf"));
    assertThrows(IllegalArgumentException.class,
                 () -> new Attachment(bytes,
                                      "application/zip",
                                      "a.zip"));
  }
}

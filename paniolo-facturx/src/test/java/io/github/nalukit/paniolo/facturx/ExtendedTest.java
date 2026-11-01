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

import io.github.nalukit.paniolo.Paniolo;
import io.github.nalukit.paniolo.config.FacturXProfile;
import io.github.nalukit.paniolo.config.InvoiceSyntax;
import io.github.nalukit.paniolo.model.common.Address;
import io.github.nalukit.paniolo.model.common.DirectDebit;
import io.github.nalukit.paniolo.model.common.DocumentAdjustment;
import io.github.nalukit.paniolo.model.common.InvoiceLine;
import io.github.nalukit.paniolo.model.common.InvoiceNote;
import io.github.nalukit.paniolo.model.common.InvoicePeriod;
import io.github.nalukit.paniolo.model.common.ItemClassification;
import io.github.nalukit.paniolo.model.common.LineAdjustment;
import io.github.nalukit.paniolo.model.common.Party;
import io.github.nalukit.paniolo.model.common.PaymentInstruction;
import io.github.nalukit.paniolo.model.common.PrecedingInvoiceReference;
import io.github.nalukit.paniolo.model.common.ProcessControl;
import io.github.nalukit.paniolo.model.common.References;
import io.github.nalukit.paniolo.model.common.TaxCategory;
import io.github.nalukit.paniolo.model.common.value.Country;
import io.github.nalukit.paniolo.model.common.value.CurrencyCode;
import io.github.nalukit.paniolo.model.common.value.InvoiceTypeCode;
import io.github.nalukit.paniolo.model.common.value.PaymentMeansCode;
import io.github.nalukit.paniolo.model.common.value.UnitCode;
import io.github.nalukit.paniolo.model.extended.AdjustmentExtension;
import io.github.nalukit.paniolo.model.extended.DocumentReference;
import io.github.nalukit.paniolo.model.extended.ExtendedContact;
import io.github.nalukit.paniolo.model.extended.ExtendedInformation;
import io.github.nalukit.paniolo.model.extended.IncludedProduct;
import io.github.nalukit.paniolo.model.extended.LineExtension;
import io.github.nalukit.paniolo.model.extended.LineSubtype;
import io.github.nalukit.paniolo.model.extended.PartyExtension;
import io.github.nalukit.paniolo.model.extended.PaymentTerm;
import io.github.nalukit.paniolo.model.extended.ProductCharacteristic;
import io.github.nalukit.paniolo.model.extended.ProductInstance;
import io.github.nalukit.paniolo.model.extended.TaxExchangeRate;
import io.github.nalukit.paniolo.model.extended.TypedDocumentReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.verapdf.pdfa.flavours.PDFAFlavour;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExtendedTest {
  private static final Paniolo PANIOLO = Paniolo.create();

  static io.github.nalukit.paniolo.model.Invoice.Builder invoice(InvoiceTypeCode type) {
    return FacturXInvoices.invoice(FacturXProfile.EXTENDED,
                                   type)
                          .addPrecedingInvoiceReference(PrecedingInvoiceReference.create()
                                                                                 .number("R-0")
                                                                                 .build())
                          .addPrecedingInvoiceReference(PrecedingInvoiceReference.create()
                                                                                 .number("R-00")
                                                                                 .build())
                          .extended(FacturXInvoices.extended());
  }

  /**
   * Adds a document allowance and a charge with EXTENDED sequence number and base quantity.
   */
  static io.github.nalukit.paniolo.model.Invoice.Builder withAdjustmentDetails(io.github.nalukit.paniolo.model.Invoice.Builder builder) {
    return builder.addAdjustment(DocumentAdjustment.createAllowance()
                                                   .amount(new BigDecimal("4.00"))
                                                   .reason("Mengenrabatt")
                                                   .tax(TaxCategory.standard(new BigDecimal("19")))
                                                   .extended(new AdjustmentExtension(1,
                                                                                     new BigDecimal("2"),
                                                                                     UnitCode.of("HUR")))
                                                   .build())
                  .addAdjustment(DocumentAdjustment.createCharge()
                                                   .amount(new BigDecimal("3.00"))
                                                   .reason("Verpackung")
                                                   .tax(TaxCategory.standard(new BigDecimal("19")))
                                                   .extended(AdjustmentExtension.sequence(2))
                                                   .build());
  }

  private static InvoiceLine.Builder line(String id,
                                          String name,
                                          String price) {
    return InvoiceLine.create()
                      .id(id)
                      .name(name)
                      .quantity(BigDecimal.ONE)
                      .unitCode("H87")
                      .netUnitPrice(new BigDecimal(price))
                      .tax(TaxCategory.standard(new BigDecimal("19")));
  }

  private static Party party(String name) {
    return Party.create()
                .name(name)
                .address(Address.create()
                                .street("Werkstraße 9")
                                .city("Stuttgart")
                                .postalCode("70173")
                                .country(Country.DE)
                                .build())
                .build();
  }

  /**
   * Adds a subtotal line with two sub-lines, one of them with all EXTENDED line information, and
   * an information line.
   */
  static io.github.nalukit.paniolo.model.Invoice.Builder withSubLines(io.github.nalukit.paniolo.model.Invoice.Builder builder) {
    var detail = LineExtension.create()
                              .parentLineId("G1")
                              .subtype(LineSubtype.DETAIL)
                              .statusCode("1")
                              .productId("P-100")
                              .industryItemId("HERST-100")
                              .modelId("M-7")
                              .modelName("Modell Sieben")
                              .brandName("Marke")
                              .addBatchId("CH-1")
                              .addCharacteristic(new ProductCharacteristic(null,
                                                                           "Gewicht",
                                                                           null,
                                                                           new BigDecimal("12.5"),
                                                                           UnitCode.of("KGM")))
                              .addInstance(new ProductInstance("CH-1",
                                                               "SN-0001"))
                              .manufacturer(party("Hersteller AG"))
                              .addIncludedProduct(IncludedProduct.of("Netzteil",
                                                                     BigDecimal.ONE,
                                                                     UnitCode.of("H87")))
                              .deliveryTermsCode("DAP")
                              .sellerOrderReference(DocumentReference.ofLine("AB-5",
                                                                             "3"))
                              .quotationReference(DocumentReference.ofLine("ANG-7",
                                                                           "2"))
                              .contractReference(DocumentReference.of("V-1"))
                              .itemSeller(party("Händler GmbH"))
                              .addUltimateCustomerOrderReference(DocumentReference.ofLine("EK-1",
                                                                                          "4"))
                              .chargeFreeQuantity(BigDecimal.ONE)
                              .packageQuantity(new BigDecimal("2"))
                              .perPackageUnitQuantity(BigDecimal.ONE)
                              .shipTo(party("Baustelle Nord"))
                              .ultimateShipTo(party("Endkunde Nord"))
                              .actualDeliveryDate(LocalDate.of(2026,
                                                               10,
                                                               20))
                              .despatchAdviceReference(DocumentReference.ofLine("LA-1",
                                                                                "1"))
                              .receivingAdviceReference(DocumentReference.ofLine("WE-1",
                                                                                 "1"))
                              .deliveryNoteReference(DocumentReference.ofLine("LS-42",
                                                                              "1"))
                              .precedingInvoiceReference(DocumentReference.ofLine("R-0",
                                                                                  "1"))
                              .noteContentCode("ST1")
                              .noteSubjectCode("AAI")
                              .addAdditionalReference(new TypedDocumentReference("DOC-9",
                                                                                 "916",
                                                                                 null))
                              .accountingTypeCode("1")
                              // 100.00 net plus 19 % VAT.
                              .taxTotal(new BigDecimal("19.00"))
                              .grossTotal(new BigDecimal("119.00"))
                              .build();
    return builder.addLine(line("G1",
                                "Montagepaket",
                                "150.00").extended(LineExtension.create()
                                                                .subtype(LineSubtype.GROUP)
                                                                .build())
                                         .build())
                  .addLine(line("G1.1",
                                "Gerät",
                                "100.00").extended(detail)
                                         .note("Mit Garantieverlängerung")
                                         .grossPrice(new BigDecimal("100.00"))
                                         .period(new InvoicePeriod(LocalDate.of(2026,
                                                                                10,
                                                                                1),
                                                                   LocalDate.of(2026,
                                                                                10,
                                                                                31)))
                                         .buyerAccountingReference("4711")
                                         .addClassification(new ItemClassification("8471",
                                                                                   "HS",
                                                                                   null,
                                                                                   "Datenverarbeitungsmaschinen"))
                                         .build())
                  .addLine(line("G1.2",
                                "Montage",
                                "55.00").extended(LineExtension.create()
                                                               .parentLineId("G1")
                                                               .subtype(LineSubtype.DETAIL)
                                                               .build())
                                        .addAdjustment(LineAdjustment.createAllowance()
                                                                     .amount(new BigDecimal("5.00"))
                                                                     .reason("Rabatt")
                                                                     .build())
                                        .build())
                  .addLine(line("I1",
                                "Hinweis: Altgerät wird mitgenommen",
                                "0.00").extended(LineExtension.create()
                                                              .parentLineId("G1")
                                                              .subtype(LineSubtype.INFORMATION)
                                                              .build())
                                       .build());
  }

  /**
   * Adds the EXTENDED party information to the seller, a note content code, and the VAT accounting
   * currency with its exchange rate.
   */
  static io.github.nalukit.paniolo.model.Invoice.Builder withPartyAndCurrency(io.github.nalukit.paniolo.model.Invoice.Builder builder) {
    var draft  = builder.build();
    var seller = draft.seller();
    var extendedSeller = new Party(seller.name(),
                                   seller.address(),
                                   seller.vatId(),
                                   seller.taxId(),
                                   seller.electronicAddress(),
                                   null,
                                   seller.identifier(),
                                   seller.identifierSchemeId(),
                                   seller.tradingName(),
                                   seller.legalRegistrationId(),
                                   seller.legalRegistrationSchemeId(),
                                   seller.additionalLegalInformation(),
                                   new PartyExtension("SE",
                                                      seller.address(),
                                                      // BT-41 is a person or a department (CII-SR-465).
                                                      new ExtendedContact(null,
                                                                          "Buchhaltung",
                                                                          "IC",
                                                                          "+49 30 654321",
                                                                          "+49 30 654322",
                                                                          "buchhaltung@example.org")));
    var rate     = new BigDecimal("1.0800");
    var extended = draft.extended();
    return builder.seller(extendedSeller)
                  .invoicePeriod(new InvoicePeriod(LocalDate.of(2026,
                                                                10,
                                                                1),
                                                   LocalDate.of(2026,
                                                                10,
                                                                31)))
                  .references(References.create()
                                        .buyerAccountingReference("8400")
                                        .build())
                  .addNote(InvoiceNote.create()
                                      .contentCode("ST2")
                                      .text("Bitte Rechnungsnummer angeben")
                                      .build())
                  .taxCurrencyCode(CurrencyCode.USD)
                  .taxAmountInTaxCurrency(draft.totals()
                                               .taxAmount()
                                               .multiply(rate))
                  .extended(new ExtendedInformation(extended.salesAgent(),
                                                    extended.buyerAgent(),
                                                    extended.buyerTaxRepresentative(),
                                                    extended.productEndUser(),
                                                    extended.shipFrom(),
                                                    extended.ultimateShipTo(),
                                                    extended.invoicer(),
                                                    extended.invoicee(),
                                                    extended.payer(),
                                                    extended.deliveryTermsCode(),
                                                    extended.quotationReference(),
                                                    extended.deliveryNoteReference(),
                                                    extended.ultimateCustomerOrderReferences(),
                                                    extended.paymentTerms(),
                                                    extended.logisticsServiceCharges(),
                                                    extended.advancePayments(),
                                                    extended.documentName(),
                                                    extended.languageId(),
                                                    extended.copyIndicator(),
                                                    extended.testIndicator(),
                                                    extended.effectiveDate(),
                                                    extended.invoiceIssuerReference(),
                                                    extended.transportModeCode(),
                                                    extended.deliveryTermsLocationName(),
                                                    extended.deliveryTermsLocationCountry(),
                                                    "Leistungszeitraum Oktober",
                                                    "1",
                                                    null,
                                                    null,
                                                    new TaxExchangeRate(rate,
                                                                        LocalDate.of(2026,
                                                                                     10,
                                                                                     27)),
                                                    extended.financialAdjustments(),
                                                    extended.additionalAccountingAccounts()));
  }

  /**
   * A direct debit with the payer account name and BIC of the profile EXTENDED.
   */
  static io.github.nalukit.paniolo.model.Invoice.Builder directDebit(InvoiceTypeCode type) {
    return FacturXInvoices.invoice(FacturXProfile.EXTENDED,
                                   type)
                          .paymentInstruction(PaymentInstruction.create()
                                                                .meansCode(PaymentMeansCode.CODE_59)
                                                                .remittanceInformation("R-1")
                                                                .directDebit(DirectDebit.create()
                                                                                        .mandateReference("MANDAT-1")
                                                                                        .creditorId("DE98ZZZ09999999999")
                                                                                        .debitedAccountId("DE02120300000000202051")
                                                                                        .build())
                                                                .build())
                          .extended(ExtendedInformation.create()
                                                       .payerAccountName("Kunde GmbH")
                                                       .payerBic("BYLADEM1001")
                                                       .build());
  }

  @ParameterizedTest
  @EnumSource(value = InvoiceTypeCode.class,
              names = { "CODE_380",
                        "CODE_381" })
  void writesAndReadsAllExtendedHeaderInformation(InvoiceTypeCode type)
      throws Exception {
    var invoice = withPartyAndCurrency(withSubLines(invoice(type))).build();
    byte[] xml = PANIOLO.toBytes(invoice,
                                 InvoiceSyntax.CII);
    String text = new String(xml,
                             StandardCharsets.UTF_8);
    for (String element : List.of("SalesAgentTradeParty",
                                  "BuyerTaxRepresentativeTradeParty",
                                  "ProductEndUserTradeParty",
                                  "ApplicableTradeDeliveryTerms",
                                  "QuotationReferencedDocument",
                                  "BuyerAgentTradeParty",
                                  "UltimateCustomerOrderReferencedDocument",
                                  "UltimateShipToTradeParty",
                                  "ShipFromTradeParty",
                                  "DeliveryNoteReferencedDocument",
                                  "InvoicerTradeParty",
                                  "InvoiceeTradeParty",
                                  "PayerTradeParty",
                                  "SpecifiedLogisticsServiceCharge",
                                  "ApplicableTradePaymentDiscountTerms",
                                  "ApplicableTradePaymentPenaltyTerms",
                                  "SpecifiedAdvancePayment",
                                  "ParentLineID",
                                  "LineStatusReasonCode",
                                  "IndustryAssignedID",
                                  "IndividualTradeProductInstance",
                                  "ManufacturerTradeParty",
                                  "IncludedReferencedProduct",
                                  "ItemSellerTradeParty",
                                  "ChargeFreeQuantity",
                                  "PackageQuantity",
                                  "TestIndicator",
                                  "CopyIndicator",
                                  "LanguageID",
                                  "EffectiveSpecifiedPeriod",
                                  "ContentCode",
                                  "RoleCode",
                                  "DepartmentName",
                                  "FaxUniversalCommunication",
                                  "RelatedSupplyChainConsignment",
                                  "RelevantTradeLocation",
                                  "InvoiceIssuerReference",
                                  "TaxApplicableTradeCurrencyExchange",
                                  "SpecifiedFinancialAdjustment",
                                  "ClassName",
                                  "AllowanceTotalAmount")) {
      assertTrue(text.contains("<ram:" + element + ">") || text.contains("<ram:" + element + " "),
                 element);
    }
    assertEquals(invoice,
                 PANIOLO.read(xml));

    byte[] pdf = PANIOLO.toFacturX(invoice,
                                   TestPdfs.pdfA2b(),
                                   FacturXProfile.EXTENDED);
    assertEquals(List.of(),
                 TestPdfs.pdfaFailures(pdf,
                                       PDFAFlavour.PDFA_3_B));
    assertEquals(invoice,
                 PANIOLO.read(pdf));
    // The official EXTENDED rules accept what CII D16B and EN 16931 lack: a second preceding
    // invoice, the advance payment invoice reference, and logistics service charges in the VAT
    // breakdown.
    var full = PANIOLO.validate(pdf);
    assertTrue(full.isValid(),
               () -> full.messages()
                         .toString());
    assertTrue(full.messages()
                   .stream()
                   .noneMatch(message -> "FX-EXTENDED-NOT-CHECKED".equals(message.ruleId())),
               () -> full.messages()
                         .toString());
    var extended = FacturXInvoices.extended();
    var d16b = invoice(type).precedingInvoiceReferences(List.of(PrecedingInvoiceReference.create()
                                                                                         .number("R-0")
                                                                                         .build()))
                            .extended(io.github.nalukit.paniolo.model.extended.ExtendedInformation.create()
                                                                                                  .invoicer(extended.invoicer())
                                                                                                  .deliveryTermsCode("FCA")
                                                                                                  .addPaymentTerm(extended.paymentTerms()
                                                                                                                          .get(0))
                                                                                                  .build())
                            .build();
    var result = PANIOLO.validate(PANIOLO.toFacturX(d16b,
                                                    TestPdfs.pdfA2b(),
                                                    FacturXProfile.EXTENDED));
    assertTrue(result.isValid(),
               () -> result.messages()
                           .toString());
  }

  @Test
  void onlyRegularLinesCountInTheTotals() {
    var totals = withSubLines(invoice(InvoiceTypeCode.CODE_380)).build()
                                                                .totals();
    // 200.00 consulting plus the sub-lines 100.00 and 50.00; subtotal and information do not count.
    assertEquals(new BigDecimal("350.00"),
                 totals.lineTotal());
    assertEquals(new BigDecimal("360.00"),
                 totals.taxExclusiveAmount());
    // The financial adjustment of -5.00 changes only the amount due.
    assertEquals(totals.taxInclusiveAmount()
                       .subtract(new BigDecimal("5.00")),
                 totals.payableAmount());
  }

  @Test
  void checksTheLineHierarchy() {
    var wrongSubtotal = assertThrows(IllegalArgumentException.class,
                                     () -> invoice(InvoiceTypeCode.CODE_380).addLine(line("G1",
                                                                                          "Paket",
                                                                                          "99.00").extended(LineExtension.create()
                                                                                                                         .subtype(LineSubtype.GROUP)
                                                                                                                         .build())
                                                                                                  .build())
                                                                            .addLine(line("G1.1",
                                                                                          "Teil",
                                                                                          "100.00").extended(LineExtension.create()
                                                                                                                          .parentLineId("G1")
                                                                                                                          .subtype(LineSubtype.DETAIL)
                                                                                                                          .build())
                                                                                                   .build())
                                                                            .build());
    assertTrue(wrongSubtotal.getMessage()
                            .contains("subtotal"),
               wrongSubtotal::getMessage);
    assertThrows(IllegalArgumentException.class,
                 () -> invoice(InvoiceTypeCode.CODE_380).addLine(line("X",
                                                                      "Waise",
                                                                      "1.00").extended(LineExtension.create()
                                                                                                    .parentLineId("unknown")
                                                                                                    .subtype(LineSubtype.DETAIL)
                                                                                                    .build())
                                                                             .build())
                                                        .build());
    assertThrows(IllegalArgumentException.class,
                 () -> LineExtension.create()
                                    .parentLineId("G1")
                                    .build());
    // Line information also requires the profile EXTENDED.
    var en16931 = withSubLines(FacturXInvoices.invoice(FacturXProfile.EN16931,
                                                       InvoiceTypeCode.CODE_380)).build();
    assertThrows(IllegalArgumentException.class,
                 () -> PANIOLO.toXml(en16931,
                                     InvoiceSyntax.CII));
    assertThrows(IllegalArgumentException.class,
                 () -> PANIOLO.toXml(en16931,
                                     InvoiceSyntax.UBL));
  }

  @Test
  void writesThePayerAccountOfADirectDebit() {
    var invoice = directDebit(InvoiceTypeCode.CODE_380).build();
    String xml = PANIOLO.toXml(invoice,
                               InvoiceSyntax.CII);
    assertTrue(xml.contains("<ram:BICID>BYLADEM1001</ram:BICID>"),
               xml);
    assertEquals(invoice,
                 PANIOLO.read(xml));
    // Without a direct debit there is no payer account.
    assertThrows(IllegalArgumentException.class,
                 () -> FacturXInvoices.invoice(FacturXProfile.EXTENDED,
                                               InvoiceTypeCode.CODE_380)
                                      .extended(ExtendedInformation.create()
                                                                   .payerBic("BYLADEM1001")
                                                                   .build())
                                      .build());
  }

  @Test
  void rejectsNoteContentCodesAndClassNamesOutsideTheProfile() {
    var note = FacturXInvoices.invoice(FacturXProfile.EN16931,
                                       InvoiceTypeCode.CODE_380)
                              .addNote(InvoiceNote.create()
                                                  .contentCode("ST1")
                                                  .text("Hinweis")
                                                  .build())
                              .build();
    assertTrue(note.hasExtendedInformation());
    assertThrows(IllegalArgumentException.class,
                 () -> PANIOLO.toXml(note,
                                     InvoiceSyntax.CII));
    assertThrows(IllegalArgumentException.class,
                 () -> PANIOLO.toXml(note,
                                     InvoiceSyntax.UBL));
    var builder = FacturXInvoices.invoice(FacturXProfile.EN16931,
                                          InvoiceTypeCode.CODE_380);
    var line = builder.build()
                      .lines()
                      .get(0);
    var classified = builder.lines(List.of(line.toBuilder()
                                               .addClassification(new ItemClassification("8471",
                                                                                         "HS",
                                                                                         null,
                                                                                         "Datenverarbeitungsmaschinen"))
                                               .build()))
                            .build();
    assertTrue(classified.hasExtendedInformation());
    assertThrows(IllegalArgumentException.class,
                 () -> PANIOLO.toXml(classified,
                                     InvoiceSyntax.CII));
  }

  @Test
  void logisticsServiceChargesCountAsCharges() {
    var totals = invoice(InvoiceTypeCode.CODE_380).build()
                                                  .totals();
    // 2 h x 100.00 plus 10.00 shipping, both at 19 %.
    assertEquals(new BigDecimal("200.00"),
                 totals.lineTotal());
    assertEquals(new BigDecimal("10.00"),
                 totals.chargeTotal());
    assertEquals(new BigDecimal("210.00"),
                 totals.taxExclusiveAmount());
    assertEquals(new BigDecimal("39.90"),
                 totals.taxAmount());
    assertEquals(new BigDecimal("210.00"),
                 totals.vatBreakdowns()
                       .get(0)
                       .taxableAmount());
  }

  @Test
  void rejectsExtendedInformationOutsideTheProfile() {
    var invoice = invoice(InvoiceTypeCode.CODE_380).build();
    var ubl = assertThrows(IllegalArgumentException.class,
                           () -> PANIOLO.toXml(invoice,
                                               InvoiceSyntax.UBL));
    assertTrue(ubl.getMessage()
                  .contains("EXTENDED"),
               ubl::getMessage);
    var en16931 = invoice(InvoiceTypeCode.CODE_380).processControl(ProcessControl.en16931())
                                                   .build();
    assertThrows(IllegalArgumentException.class,
                 () -> PANIOLO.toXml(en16931,
                                     InvoiceSyntax.CII));
    assertThrows(IllegalArgumentException.class,
                 () -> PANIOLO.toFacturX(invoice,
                                         TestPdfs.pdfA2b(),
                                         FacturXProfile.EN16931));
    // An empty extended object is the same as none.
    assertEquals(null,
                 FacturXInvoices.invoice(FacturXProfile.EN16931,
                                         InvoiceTypeCode.CODE_380)
                                .extended(io.github.nalukit.paniolo.model.extended.ExtendedInformation.create()
                                                                                                      .build())
                                .build()
                                .extended());
  }

  @Test
  void writesAndReadsTheExtendedAmountDetails()
      throws Exception {
    var invoice = withAdjustmentDetails(withSubLines(invoice(InvoiceTypeCode.CODE_380))).build();
    String xml = PANIOLO.toXml(invoice,
                               InvoiceSyntax.CII);
    // 350.00 from the regular lines; -4.00 + 3.00 adjustments and 10.00 logistics service charge.
    for (String expected : List.of("<ram:SequenceNumeric>1</ram:SequenceNumeric>",
                                   "<ram:SequenceNumeric>2</ram:SequenceNumeric>",
                                   "<ram:BasisQuantity unitCode=\"HUR\">2</ram:BasisQuantity>",
                                   "<ram:BasisAmount>359.00</ram:BasisAmount>",
                                   "<ram:LineTotalBasisAmount>350.00</ram:LineTotalBasisAmount>",
                                   "<ram:AllowanceChargeBasisAmount>9.00</ram:AllowanceChargeBasisAmount>",
                                   "<ram:TaxTotalAmount currencyID=\"EUR\">19.00</ram:TaxTotalAmount>",
                                   "<ram:GrandTotalAmount>119.00</ram:GrandTotalAmount>",
                                   "<ram:Name>Factoring GmbH</ram:Name>")) {
      assertTrue(xml.contains(expected),
                 () -> expected + " in " + xml);
    }
    assertEquals(invoice,
                 PANIOLO.read(xml));
    byte[] pdf = PANIOLO.toFacturX(invoice,
                                   TestPdfs.pdfA2b(),
                                   FacturXProfile.EXTENDED);
    assertEquals(List.of(),
                 TestPdfs.pdfaFailures(pdf,
                                       PDFAFlavour.PDFA_3_B));
    assertEquals(invoice,
                 PANIOLO.read(pdf));

    // Without document allowances, charges, and logistics service charges the split is not written.
    var plain = FacturXInvoices.invoice(FacturXProfile.EXTENDED,
                                        InvoiceTypeCode.CODE_380)
                               .build();
    String plainXml = PANIOLO.toXml(plain,
                                    InvoiceSyntax.CII);
    assertTrue(!plainXml.contains("LineTotalBasisAmount"),
               plainXml);
  }

  @Test
  void keepsFurtherPlainPaymentTerms() {
    // Instalments with description and due date only, as Mustang writes its extended payment terms.
    var invoice = FacturXInvoices.invoice(FacturXProfile.EXTENDED,
                                          InvoiceTypeCode.CODE_380)
                                 .paymentTerms("Erste Rate")
                                 .extended(ExtendedInformation.create()
                                                              .addPaymentTerm(new PaymentTerm("Zweite Rate",
                                                                                              LocalDate.of(2026,
                                                                                                           12,
                                                                                                           10),
                                                                                              null,
                                                                                              null,
                                                                                              null))
                                                              .addPaymentTerm(new PaymentTerm(null,
                                                                                              LocalDate.of(2027,
                                                                                                           1,
                                                                                                           10),
                                                                                              null,
                                                                                              null,
                                                                                              null))
                                                              .build())
                                 .build();
    String xml = PANIOLO.toXml(invoice,
                               InvoiceSyntax.CII);
    assertEquals(3,
                 xml.split("<ram:SpecifiedTradePaymentTerms>",
                           -1).length - 1,
                 xml);
    assertTrue(PANIOLO.validate(xml.getBytes(StandardCharsets.UTF_8),
                                FacturXProfile.EXTENDED)
                      .isValid());
    assertEquals(invoice,
                 PANIOLO.read(xml));
    assertThrows(IllegalArgumentException.class,
                 () -> new PaymentTerm(null,
                                       null,
                                       null,
                                       null,
                                       null));
  }

  @Test
  void checksTheExtendedAmountDetails() {
    var wrongGross = assertThrows(IllegalArgumentException.class,
                                  () -> FacturXInvoices.invoice(FacturXProfile.EXTENDED,
                                                                InvoiceTypeCode.CODE_380)
                                                       .addLine(line("X1",
                                                                     "Gerät",
                                                                     "100.00").extended(LineExtension.create()
                                                                                                     .taxTotal(new BigDecimal("19"))
                                                                                                     .grossTotal(new BigDecimal("120"))
                                                                                                     .build())
                                                                              .build())
                                                       .build());
    assertTrue(wrongGross.getMessage()
                         .contains("amount with VAT 120.00 differs from net amount plus VAT amount 119.00"),
               wrongGross::getMessage);
    assertThrows(IllegalArgumentException.class,
                 () -> new AdjustmentExtension(null,
                                               null,
                                               null));
    assertThrows(IllegalArgumentException.class,
                 () -> AdjustmentExtension.sequence(0));
    assertThrows(IllegalArgumentException.class,
                 () -> new AdjustmentExtension(null,
                                               null,
                                               UnitCode.of("HUR")));
    // Sequence numbers and base quantities belong to the profile EXTENDED.
    var en16931 = withAdjustmentDetails(FacturXInvoices.invoice(FacturXProfile.EN16931,
                                                                InvoiceTypeCode.CODE_380)).build();
    assertTrue(en16931.hasExtendedInformation());
    assertThrows(IllegalArgumentException.class,
                 () -> PANIOLO.toXml(en16931,
                                     InvoiceSyntax.CII));
    assertThrows(IllegalArgumentException.class,
                 () -> PANIOLO.toXml(en16931,
                                     InvoiceSyntax.UBL));
    var order = OrderSamples.comfort(io.github.nalukit.paniolo.model.order.OrderTypeCode.ORDER)
                            .addAdjustment(DocumentAdjustment.createCharge()
                                                             .amount(BigDecimal.ONE)
                                                             .reason("Verpackung")
                                                             .tax(TaxCategory.standard(new BigDecimal("19")))
                                                             .extended(AdjustmentExtension.sequence(1))
                                                             .build())
                            .build();
    assertThrows(IllegalArgumentException.class,
                 () -> PANIOLO.toOrderX(order));
  }
}

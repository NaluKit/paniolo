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
import io.github.nalukit.paniolo.model.common.CreditTransfer;
import io.github.nalukit.paniolo.model.common.Party;
import io.github.nalukit.paniolo.model.common.PaymentCardInformation;
import io.github.nalukit.paniolo.model.common.PaymentInstruction;
import io.github.nalukit.paniolo.model.common.ProcessControl;
import io.github.nalukit.paniolo.model.common.TaxCategory;
import io.github.nalukit.paniolo.model.common.VatBreakdown;
import io.github.nalukit.paniolo.model.common.value.Country;
import io.github.nalukit.paniolo.model.common.value.DutyTaxFeeCategory;
import io.github.nalukit.paniolo.model.common.value.InvoiceTypeCode;
import io.github.nalukit.paniolo.model.extended.ExtendedInformation;
import io.github.nalukit.paniolo.model.extended.LineExtension;
import io.github.nalukit.paniolo.model.extended.ObjectIdentifier;
import io.github.nalukit.paniolo.validation.ValidationMessage;
import io.github.nalukit.paniolo.xml.ReadMode;
import io.github.nalukit.paniolo.validation.ValidationResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The French profile EXTENDED-CTC-FR with the official examples of FNFE-MPE (see
 * {@code src/test/resources/extended-ctc-fr/README.md}).
 */
class ExtendedCtcFrTest {
  private static final Paniolo PANIOLO = Paniolo.create();
  private static final String  EXAMPLE = "UC13_F202600037_EXTENDED-CTC-FR_CII_Commentee.xml";

  private static byte[] example(String name)
      throws IOException {
    try (InputStream input = ExtendedCtcFrTest.class.getResourceAsStream("/extended-ctc-fr/" + name)) {
      assertNotNull(input,
                    name);
      return input.readAllBytes();
    }
  }

  private static List<String> ruleIds(ValidationResult result) {
    return result.messages()
                 .stream()
                 .map(ValidationMessage::ruleId)
                 .toList();
  }

  @ParameterizedTest
  @ValueSource(strings = { "UC10_F202600004_MULTI-VENDEUR_EXTENDED-CTC-FR_CII_Commentee.xml",
                           "UC11_F202600022_MULTI-VENDEUR_EXTENDED-CTC-FR_CII_Commentee.xml",
                           "UC12_F202600025_SOUS-LIGNE_EXTENDED-CTC-FR_CII_Commentee.xml",
                           "UC13_AUTOF202600035_BIDIRECTIONNELLE_EXTENDED-CTC-FR_CII_Commentee.xml",
                           EXAMPLE })
  void validatesTheOfficialExamples(String name)
      throws IOException {
    // The examples start with a byte order mark, which must not hide the specification identifier.
    var result = PANIOLO.validate(example(name));
    assertTrue(result.isValid(),
               () -> result.messages()
                           .toString());
    assertEquals("EXTENDED-CTC-FR with BR-FR",
                 result.profileName());
    assertEquals(List.of(),
                 result.messages());
  }

  @Test
  void reportsTheFrenchRules()
      throws IOException {
    String xml = new String(example(EXAMPLE),
                            StandardCharsets.UTF_8);
    // BR-FR-08: the billing framework (BT-23) is mandatory between French parties.
    var withoutFramework = PANIOLO.validate(xml.replace("<ram:ID>B1</ram:ID>",
                                                        "<ram:ID>X1</ram:ID>")
                                               .getBytes(StandardCharsets.UTF_8));
    assertFalse(withoutFramework.isValid());
    assertTrue(ruleIds(withoutFramework).contains("BR-FR-08_BT-23"),
               () -> withoutFramework.messages()
                                     .toString());
    // CII-DT-071: EXTENDED-CTC-FR has no description of the invoicing period, EXTENDED has.
    String described = xml.replace("<ram:BillingSpecifiedPeriod>",
                                   "<ram:BillingSpecifiedPeriod><ram:Description>Mai</ram:Description>");
    var ctcFr = PANIOLO.validate(described.getBytes(StandardCharsets.UTF_8));
    assertTrue(ruleIds(ctcFr).contains("CII-DT-071"),
               () -> ctcFr.messages()
                          .toString());
    // A buyer outside France: only the EXTENDED-CTC-FR rules apply, not BR-FR.
    String foreign = xml.replace("<ram:ID>B1</ram:ID>",
                                 "<ram:ID>X1</ram:ID>")
                        .replaceFirst("(?s)(<ram:BuyerTradeParty>.*?<ram:CountryID>)FR(</ram:CountryID>)",
                                      "$1DE$2");
    var abroad = PANIOLO.validate(foreign.getBytes(StandardCharsets.UTF_8));
    assertEquals("EXTENDED-CTC-FR",
                 abroad.profileName());
    assertFalse(ruleIds(abroad).contains("BR-FR-08_BT-23"),
                () -> abroad.messages()
                            .toString());
    assertTrue(ruleIds(abroad).contains("BR-FR-NOT-APPLICABLE"));
  }

  @Test
  void readsAndWritesTheOfficialExample()
      throws IOException {
    var invoice = PANIOLO.read(example(EXAMPLE));
    assertEquals(ProcessControl.FACTURX_EXTENDED_CTC_FR,
                 invoice.processControl()
                        .specificationId());
    assertEquals("B1",
                 invoice.processControl()
                        .businessProcessId());
    String xml = PANIOLO.toXml(invoice,
                               InvoiceSyntax.CII);
    var result = PANIOLO.validate(xml);
    assertTrue(result.isValid(),
               () -> result.messages()
                           .toString());
    // The exemption reason of the lines (BT-X-96) is written, as BR-FXEXT-E-08rev expects.
    assertEquals(List.of(),
                 result.messages());
    assertEquals(invoice,
                 PANIOLO.read(xml));
  }

  /**
   * UC12: a subtotal line without VAT category and an information line without price and VAT
   * category (BR-FXEXT-CO-04) are read and written again (Mustang issue #1275).
   */
  @Test
  void readsAndWritesTheSubLineExample()
      throws IOException {
    var invoice = PANIOLO.read(example("UC12_F202600025_SOUS-LIGNE_EXTENDED-CTC-FR_CII_Commentee.xml"));
    var group   = invoice.lines()
                         .get(0);
    var info    = invoice.lines()
                         .get(3);
    assertEquals(null,
                 group.tax());
    assertEquals(null,
                 info.tax());
    assertEquals(null,
                 info.netUnitPrice());
    String xml = PANIOLO.toXml(invoice,
                               InvoiceSyntax.CII);
    var result = PANIOLO.validate(xml);
    assertTrue(result.isValid(),
               () -> result.messages()
                           .toString());
    assertEquals(invoice,
                 PANIOLO.read(xml));
  }

  /**
   * UC10 and UC11, French multi-vendor invoices: sub-invoice (GROUP) lines without quantity, item
   * sellers named partially (Mustang issue #1103), and in UC10 a card next to the credit transfer
   * account (Mustang issues #583, #795).
   */
  @ParameterizedTest
  @ValueSource(strings = { "UC10_F202600004_MULTI-VENDEUR_EXTENDED-CTC-FR_CII_Commentee.xml",
                           "UC11_F202600022_MULTI-VENDEUR_EXTENDED-CTC-FR_CII_Commentee.xml" })
  void readsTheMultiVendorExamples(String name)
      throws IOException {
    for (var mode : ReadMode.values()) {
      var read = PANIOLO.read(example(name),
                              mode);
      var invoice = read.invoice();
      assertTrue(invoice.processControl()
                        .isFrenchMultiVendor());
      assertEquals(null,
                   invoice.lines()
                          .getFirst()
                          .quantity());
      assertEquals(List.of(),
                   read.warnings());
      String xml = PANIOLO.toXml(invoice,
                                 InvoiceSyntax.CII);
      assertEquals(invoice,
                   PANIOLO.read(xml));
      // Each GROUP line keeps the billed object (AVV) and the sub-invoice (AFL), and every line
      // its sub-invoice (Mustang issues #1101, #1103).
      String original = new String(example(name),
                                   StandardCharsets.UTF_8);
      for (String scheme : List.of(">AVV<",
                                   ">AFL<")) {
        assertEquals(original.split(scheme,
                                    -1).length,
                     xml.split(scheme,
                               -1).length,
                     scheme);
      }
      // The French rules including BR-FR-MV-03/08/09/10/11 accept the written invoice.
      var result = PANIOLO.validate(xml);
      assertTrue(result.isValid(),
                 () -> mode + " " + result.messages());
      assertEquals(List.of(),
                   result.messages());
    }
  }

  /**
   * BR-FR-MV-09 checks the VAT amount of a GROUP line against the VAT breakdowns of its
   * sub-invoice, which Paniolo keeps apart (Mustang issue #1101); a changed amount is reported.
   */
  @Test
  void reportsAMultiVendorVatMismatch()
      throws IOException {
    String xml = PANIOLO.toXml(PANIOLO.read(example("UC10_F202600004_MULTI-VENDEUR_EXTENDED-CTC-FR_CII_Commentee.xml")),
                               InvoiceSyntax.CII);
    String changed = xml.replaceFirst("<ram:TaxTotalAmount currencyID=\"EUR\">200.00</ram:TaxTotalAmount>",
                                      "<ram:TaxTotalAmount currencyID=\"EUR\">210.00</ram:TaxTotalAmount>");
    assertFalse(xml.equals(changed));
    var result = PANIOLO.validate(changed);
    assertTrue(ruleIds(result).contains("BR-FR-MV-09_EXT-FR-FE-181"),
               () -> result.messages()
                           .toString());
  }

  /**
   * Further invoiced objects of a line (BT-128) are kept in order; profiles without a place for
   * them reject the invoice instead of dropping them.
   */
  @Test
  void keepsFurtherObjectIdentifiers() {
    var base = FacturXInvoices.invoice(FacturXProfile.EXTENDED,
                                       InvoiceTypeCode.CODE_380)
                              .build()
                              .lines()
                              .getFirst();
    var line = base.toBuilder()
                   .objectId("AVV-FIRST",
                             "AVV")
                   .extended(LineExtension.create()
                                          .addFurtherObjectIdentifier(new ObjectIdentifier("AFL-SECOND",
                                                                                           "AFL"))
                                          .build())
                   .build();
    var invoice = FacturXInvoices.invoice(FacturXProfile.EXTENDED,
                                          InvoiceTypeCode.CODE_380)
                                 .lines(List.of(line))
                                 .build();
    String xml = PANIOLO.toXml(invoice,
                               InvoiceSyntax.CII);
    assertTrue(xml.indexOf("AVV-FIRST") < xml.indexOf("AFL-SECOND"),
               xml);
    for (var mode : ReadMode.values()) {
      var read = PANIOLO.read(xml,
                              mode);
      assertEquals(List.of(),
                   read.warnings());
      assertEquals(invoice,
                   read.invoice());
    }
    var en16931 = FacturXInvoices.invoice(FacturXProfile.EN16931,
                                          InvoiceTypeCode.CODE_380)
                                 .lines(List.of(line))
                                 .build();
    assertThrows(IllegalArgumentException.class,
                 () -> PANIOLO.toXml(en16931,
                                     InvoiceSyntax.CII));
    assertThrows(IllegalArgumentException.class,
                 () -> PANIOLO.toXml(en16931,
                                     InvoiceSyntax.UBL));
    assertThrows(IllegalArgumentException.class,
                 () -> base.toBuilder()
                           .extended(line.extended())
                           .build());
  }

  /**
   * The payment means block of UC10 states a card next to the credit transfer account, which
   * EXTENDED-CTC-FR allows (Mustang issues #583, #795, #1103); here in the single-vendor UC13.
   */
  @Test
  void keepsCardAndAccountOfOnePaymentMeansBlock()
      throws IOException {
    String block = new String(example("UC10_F202600004_MULTI-VENDEUR_EXTENDED-CTC-FR_CII_Commentee.xml"),
                              StandardCharsets.UTF_8).replaceFirst("(?s).*?(<ram:SpecifiedTradeSettlementPaymentMeans>.*?</ram:SpecifiedTradeSettlementPaymentMeans>).*",
                                                                   "$1");
    String xml = new String(example(EXAMPLE),
                            StandardCharsets.UTF_8).replaceFirst("(?s)<ram:SpecifiedTradeSettlementPaymentMeans>.*?</ram:SpecifiedTradeSettlementPaymentMeans>",
                                                                 java.util.regex.Matcher.quoteReplacement(block));
    for (var mode : ReadMode.values()) {
      var read = PANIOLO.read(xml.getBytes(StandardCharsets.UTF_8),
                              mode);
      assertEquals(List.of(),
                   read.warnings());
      var payment = read.invoice()
                        .paymentInstruction();
      assertEquals("30",
                   payment.meansCode()
                          .getCode());
      assertEquals("Virement",
                   payment.meansText());
      assertEquals(List.of(new CreditTransfer("FR20 1254 2547 2569 8542 5874 698",
                                              "MON COMPTE BANCAIRE",
                                              "BIC_MONCOMPTE")),
                   payment.creditTransfers());
      assertEquals(new PaymentCardInformation("4970xx0265",
                                              "MON NOM MA CARTE"),
                   payment.paymentCard());
      String written = PANIOLO.toXml(read.invoice(),
                                     InvoiceSyntax.CII);
      // One block with card and account, as in the input.
      assertEquals(1,
                   written.split("<ram:SpecifiedTradeSettlementPaymentMeans>",
                                 -1).length - 1);
      var result = PANIOLO.validate(written);
      assertTrue(result.isValid(),
                 () -> result.messages()
                             .toString());
      assertEquals(read.invoice(),
                   PANIOLO.read(written));
    }
    // Other profiles keep a credit transfer without card.
    var en16931 = FacturXInvoices.invoice(FacturXProfile.EN16931,
                                          InvoiceTypeCode.CODE_380);
    var instruction = en16931.build()
                             .paymentInstruction();
    var withCard = PaymentInstruction.create()
                                     .meansCode(instruction.meansCode())
                                     .creditTransfers(instruction.creditTransfers())
                                     .paymentCard(new PaymentCardInformation("4970xx0265",
                                                                             "MON NOM MA CARTE"))
                                     .build();
    var error = assertThrows(IllegalArgumentException.class,
                             () -> en16931.paymentInstruction(withCard)
                                          .build());
    assertEquals("Credit transfer requires at least one account and no card or direct debit",
                 error.getMessage());
  }

  /**
   * The lines of a French multi-vendor invoice name their item seller partially: a sub-invoice
   * line by name, identifiers, and country, its detail lines by legal registration only
   * (BR-FR-MV-03, Mustang issue #1103). Other invoices require a complete item seller.
   */
  @Test
  void keepsThePartialItemSellersOfAMultiVendorInvoice() {
    var subInvoiceSeller = Party.create()
                                .name("VENDEUR XXX")
                                .identifier("11111111800019",
                                            "0009")
                                .legalRegistrationId("111111118",
                                                     "0002")
                                .vatId("FR44111111118")
                                .address(Address.create()
                                                .country(Country.FR)
                                                .build())
                                .build();
    var detailSeller = Party.create()
                            .legalRegistrationId("111111118",
                                                 "0002")
                            .build();
    var base = FacturXInvoices.invoice(FacturXProfile.EXTENDED_CTC_FR,
                                       InvoiceTypeCode.CODE_380)
                              .build()
                              .lines()
                              .getFirst();
    var lines = List.of(base.toBuilder()
                            .extended(LineExtension.create()
                                                   .itemSeller(subInvoiceSeller)
                                                   .build())
                            .build(),
                        base.toBuilder()
                            .id("2")
                            .extended(LineExtension.create()
                                                   .itemSeller(detailSeller)
                                                   .build())
                            .build());
    var invoice = FacturXInvoices.invoice(FacturXProfile.EXTENDED_CTC_FR,
                                          InvoiceTypeCode.CODE_380)
                                 .processControl(ProcessControl.facturXExtendedCtcFr("S8"))
                                 .lines(lines)
                                 .build();
    String xml = PANIOLO.toXml(invoice,
                               InvoiceSyntax.CII);
    for (var mode : ReadMode.values()) {
      var read = PANIOLO.read(xml,
                              mode);
      assertEquals(invoice,
                   read.invoice());
    }
    // Outside the multi-vendor framework the item seller stays a complete party.
    var single = FacturXInvoices.invoice(FacturXProfile.EXTENDED_CTC_FR,
                                         InvoiceTypeCode.CODE_380)
                                .lines(lines);
    var error = assertThrows(IllegalArgumentException.class,
                             single::build);
    assertEquals("The itemSeller requires a postal address with street, city, and post code",
                 error.getMessage());
    var nameless = FacturXInvoices.invoice(FacturXProfile.EXTENDED_CTC_FR,
                                           InvoiceTypeCode.CODE_380)
                                  .lines(List.of(lines.get(1)));
    assertEquals("The itemSeller requires a name",
                 assertThrows(IllegalArgumentException.class,
                              nameless::build).getMessage());
    // The seller and buyer of every invoice keep their required name, and a party without
    // name needs an identification.
    var anonymous = FacturXInvoices.invoice(FacturXProfile.EXTENDED_CTC_FR,
                                            InvoiceTypeCode.CODE_380)
                                   .processControl(ProcessControl.facturXExtendedCtcFr("S8"))
                                   .seller(detailSeller);
    assertEquals("The seller requires a name",
                 assertThrows(IllegalArgumentException.class,
                              anonymous::build).getMessage());
    assertThrows(IllegalArgumentException.class,
                 () -> Party.create()
                            .vatId("FR44111111118")
                            .build());
  }

  /**
   * Two sub-invoices of a French multi-vendor invoice with the same category and rate keep a VAT
   * breakdown each, told apart by the sub-invoice number in the exemption reason (BR-FR-MV-08,
   * BR-FR-MV-09; Mustang issues #1101 and #1104).
   */
  @Test
  void keepsTheVatBreakdownOfEachSubInvoice() {
    var base = FacturXInvoices.invoice(FacturXProfile.EXTENDED_CTC_FR,
                                       InvoiceTypeCode.CODE_380)
                              .build()
                              .lines()
                              .getFirst();
    var first = TaxCategory.create()
                           .code(DutyTaxFeeCategory.S)
                           .rate(new BigDecimal("20"))
                           .exemptionReason("#F202600004#")
                           .build();
    var second = TaxCategory.create()
                            .code(DutyTaxFeeCategory.S)
                            .rate(new BigDecimal("20"))
                            .exemptionReason("#111111118_F202600004#")
                            .build();
    var lines = List.of(base.toBuilder()
                            .tax(first)
                            .build(),
                        base.toBuilder()
                            .id("2")
                            .netUnitPrice(new BigDecimal("450.00"))
                            .tax(second)
                            .build());
    var invoice = FacturXInvoices.invoice(FacturXProfile.EXTENDED_CTC_FR,
                                          InvoiceTypeCode.CODE_380)
                                 .processControl(ProcessControl.facturXExtendedCtcFr("S8"))
                                 .lines(lines)
                                 .build();
    var totals = invoice.totals();
    assertEquals(List.of(new VatBreakdown(first,
                                          new BigDecimal("200.00"),
                                          new BigDecimal("40.00")),
                         new VatBreakdown(second,
                                          new BigDecimal("900.00"),
                                          new BigDecimal("180.00"))),
                 totals.vatBreakdowns());
    assertEquals(new BigDecimal("1320.00"),
                 totals.taxInclusiveAmount());
    String xml = PANIOLO.toXml(invoice,
                               InvoiceSyntax.CII);
    var read = PANIOLO.read(xml);
    // Each line finds the breakdown of its own sub-invoice again.
    assertEquals(invoice,
                 read);
    assertEquals(totals,
                 read.totals());
    // Outside the multi-vendor framework one category and rate keeps a single breakdown, and
    // differing exemption reasons stay a conflict.
    for (var control : List.of(ProcessControl.facturXExtendedCtcFr("S1"),
                               ProcessControl.facturXExtended())) {
      var single = FacturXInvoices.invoice(FacturXProfile.EXTENDED_CTC_FR,
                                           InvoiceTypeCode.CODE_380)
                                  .processControl(control)
                                  .lines(lines)
                                  .build();
      var conflict = assertThrows(IllegalArgumentException.class,
                                  single::totals);
      assertEquals("Conflicting exemption information for VAT category/rate",
                   conflict.getMessage());
    }
    // Two different exemption reasons of one sub-invoice remain a conflict as well.
    var sameSubInvoice = FacturXInvoices.invoice(FacturXProfile.EXTENDED_CTC_FR,
                                                 InvoiceTypeCode.CODE_380)
                                        .processControl(ProcessControl.facturXExtendedCtcFr("S8"))
                                        .lines(List.of(lines.get(0),
                                                       lines.get(1)
                                                            .toBuilder()
                                                            .tax(TaxCategory.create()
                                                                            .code(DutyTaxFeeCategory.S)
                                                                            .rate(new BigDecimal("20"))
                                                                            .exemptionReason("#F202600004# autre")
                                                                            .build())
                                                            .build()))
                                        .build();
    assertThrows(IllegalArgumentException.class,
                 sameSubInvoice::totals);
  }

  @Test
  void writesTheContentTheProfileAllows() {
    var sample = FacturXInvoices.extended();
    var invoice = FacturXInvoices.invoice(FacturXProfile.EXTENDED_CTC_FR,
                                          InvoiceTypeCode.CODE_380)
                                 .extended(ExtendedInformation.create()
                                                              .salesAgent(sample.salesAgent())
                                                              .invoicer(sample.invoicer())
                                                              .deliveryTermsCode("FCA")
                                                              .build())
                                 .build();
    String xml = PANIOLO.toXml(invoice,
                               InvoiceSyntax.CII);
    assertTrue(xml.contains("<ram:ID>" + ProcessControl.FACTURX_EXTENDED_CTC_FR + "</ram:ID>"),
               xml);
    var result = PANIOLO.validate(xml);
    assertTrue(result.isValid(),
               () -> result.messages()
                           .toString());
    // Seller and buyer are German, so the French rules BR-FR do not apply.
    assertEquals(List.of("BR-FR-NOT-APPLICABLE"),
                 ruleIds(result));
    assertEquals(invoice,
                 PANIOLO.read(xml));
  }

  @Test
  void rejectsWhatTheProfileDoesNotAllow() {
    // The EXTENDED test invoice carries content EXTENDED-CTC-FR has no place for.
    var invoice = ExtendedTest.invoice(InvoiceTypeCode.CODE_380)
                              .processControl(ProcessControl.facturXExtendedCtcFr("B1"))
                              .build();
    var error = assertThrows(IllegalArgumentException.class,
                             () -> PANIOLO.toXml(invoice,
                                                 InvoiceSyntax.CII));
    for (String content : List.of("Profile EXTENDED-CTC-FR does not allow:",
                                  "test indicator",
                                  "logistics service charges",
                                  "financial adjustments",
                                  "quotation reference")) {
      assertTrue(error.getMessage()
                      .contains(content),
                 error::getMessage);
    }
    var lines = assertThrows(IllegalArgumentException.class,
                             () -> PANIOLO.toXml(ExtendedTest.withSubLines(FacturXInvoices.invoice(FacturXProfile.EXTENDED_CTC_FR,
                                                                                                   InvoiceTypeCode.CODE_380))
                                                             .build(),
                                                 InvoiceSyntax.CII));
    assertTrue(lines.getMessage()
                    .contains("brand name"),
               lines::getMessage);
    // EXTENDED-CTC-FR is exchanged as CII XML, not as Factur-X PDF.
    var allowed = FacturXInvoices.invoice(FacturXProfile.EXTENDED_CTC_FR,
                                          InvoiceTypeCode.CODE_380)
                                 .build();
    var pdf = assertThrows(IllegalArgumentException.class,
                           () -> PANIOLO.toFacturX(allowed,
                                                   TestPdfs.pdfA2b(),
                                                   FacturXProfile.EXTENDED_CTC_FR));
    assertTrue(pdf.getMessage()
                  .contains("CII XML"),
               pdf::getMessage);
  }

  @Test
  void recognisesTheProfile() {
    assertEquals(FacturXProfile.EXTENDED_CTC_FR,
                 FacturXProfile.ofSpecificationId(ProcessControl.FACTURX_EXTENDED_CTC_FR));
    // The conformance level the Mustang project writes into the XMP metadata.
    assertEquals(FacturXProfile.EXTENDED_CTC_FR,
                 FacturXProfile.ofConformanceLevel("EXTENDED-CTC-FR"));
    assertFalse(FacturXProfile.EXTENDED_CTC_FR.isPdfWritable());
    assertTrue(ProcessControl.facturXExtendedCtcFr("S1")
                             .isExtended());
  }
}

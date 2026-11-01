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
package io.github.nalukit.paniolo.mustang;

import io.github.nalukit.paniolo.Paniolo;
import io.github.nalukit.paniolo.config.FacturXProfile;
import io.github.nalukit.paniolo.config.InvoiceSyntax;
import io.github.nalukit.paniolo.model.common.CashDiscount;
import io.github.nalukit.paniolo.model.common.PartyIdentifier;
import io.github.nalukit.paniolo.model.common.ProcessControl;
import io.github.nalukit.paniolo.model.extended.TaxExchangeRate;
import io.github.nalukit.paniolo.validation.Severity;
import org.junit.jupiter.api.Test;
import org.mustangproject.Allowance;
import org.mustangproject.Charge;
import org.mustangproject.Contact;
import org.mustangproject.FileAttachment;
import org.mustangproject.IncludedNote;
import org.mustangproject.Invoice;
import org.mustangproject.Item;
import org.mustangproject.PaymentTerms;
import org.mustangproject.Product;
import org.mustangproject.ReferencedDocument;
import org.mustangproject.SchemedID;
import org.mustangproject.TradeParty;
import org.mustangproject.ZUGFeRD.IZUGFeRDPaymentDiscountTerms;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static io.github.nalukit.paniolo.mustang.MustangInvoiceAdapterTest.date;
import static io.github.nalukit.paniolo.mustang.MustangInvoiceAdapterTest.mustangInvoice;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Values without counterpart in the target profile: the mapping keeps each original value with
 * what it belongs to, and {@code toInvoice} refuses the invoice; values that EXTENDED can carry are
 * mapped there. One test per task of the list P01.
 */
class OriginalValuesTest {
  private static final Paniolo PANIOLO = Paniolo.create();
  private static final ZoneId  ZONE    = ZoneId.of("Europe/Berlin");

  private static MustangInvoiceAdapter plain() {
    return MustangInvoiceAdapter.builder()
                                .zone(ZONE)
                                .build();
  }

  private static MustangInvoiceAdapter extended() {
    return MustangInvoiceAdapter.builder()
                                .processControl(ProcessControl.facturXExtended())
                                .zone(ZONE)
                                .build();
  }

  /**
   * The only loss of the getter for the subject.
   */
  private static MappingLoss loss(MustangMapping mapping,
                                  String getter,
                                  String subject) {
    var found = mapping.losses()
                       .stream()
                       .filter(loss -> loss.getter()
                                           .equals(getter) && subject.equals(loss.subject()))
                       .toList();
    if (found.size() != 1) {
      fail(getter + " of " + subject + " in " + mapping.losses());
    }
    return found.getFirst();
  }

  /**
   * Asserts the original value of every getter for the subject.
   */
  private static void lost(MustangMapping mapping,
                           String subject,
                           Map<String, Object> values) {
    values.forEach((getter, value) -> assertEquals(value,
                                                   loss(mapping,
                                                        getter,
                                                        subject).value(),
                                                   getter));
  }

  private static void refused(MustangInvoiceAdapter adapter,
                              Invoice invoice,
                              String getter) {
    var refused = assertThrows(IllegalArgumentException.class,
                               () -> adapter.toInvoice(invoice));
    assertTrue(refused.getMessage()
                      .contains(getter),
               refused::getMessage);
  }

  private static void validExtended(io.github.nalukit.paniolo.model.Invoice invoice) {
    var result = PANIOLO.validate(PANIOLO.toBytes(invoice,
                                                  InvoiceSyntax.CII),
                                  FacturXProfile.EXTENDED);
    assertEquals(List.of(),
                 result.messages()
                       .stream()
                       .filter(message -> message.severity() != Severity.INFO)
                       .toList(),
                 result::toString);
  }

  private static IZUGFeRDPaymentDiscountTerms discount(String percent,
                                                       int period,
                                                       String unit,
                                                       Date base) {
    return new IZUGFeRDPaymentDiscountTerms() {
      @Override
      public BigDecimal getCalculationPercentage() {
        return new BigDecimal(percent);
      }

      @Override
      public Date getBaseDate() {
        return base;
      }

      @Override
      public int getBasePeriodMeasure() {
        return period;
      }

      @Override
      public String getBasePeriodUnitCode() {
        return unit;
      }
    };
  }

  private static TradeParty buyer(Invoice invoice) {
    return (TradeParty) invoice.getRecipient();
  }

  private static Product book(Invoice invoice) {
    return (Product) invoice.getZFItems()[1].getProduct();
  }

  /**
   * P01-04: the date of the conversion rate.
   */
  @Test
  void keepsTheDateOfTheConversionRate() {
    Invoice mustang = mustangInvoice().setTaxCurrency("USD")
                                      .setTaxConversionRate(new BigDecimal("1.0850"))
                                      .setTaxConversionRateDateTime(date(2026,
                                                                         10,
                                                                         24));
    var mapping = plain().map(mustang);
    var loss = loss(mapping,
                    "IExportableTransaction.getTaxConversionRateDateTime",
                    "the conversion rate 1.0850 from EUR to USD");
    assertEquals(LocalDate.of(2026,
                              10,
                              24),
                 loss.value());
    refused(plain(),
            mustang,
            "getTaxConversionRateDateTime");

    var kept = extended().map(mustang);
    assertTrue(kept.isLossless(),
               kept.losses()::toString);
    assertEquals(new TaxExchangeRate(new BigDecimal("1.0850"),
                                     LocalDate.of(2026,
                                                  10,
                                                  24)),
                 kept.invoice()
                     .extended()
                     .taxExchangeRate());
    validExtended(kept.invoice());
  }

  /**
   * P01-05: an identifier next to the global identifier.
   */
  @Test
  void keepsBothIdentifiersOfAParty() {
    Invoice mustang = mustangInvoice();
    buyer(mustang).setID("K-4711")
                  .addGlobalID(new SchemedID("0088",
                                             "4000001000005"));
    var mapping = plain().map(mustang);
    var buyer   = mapping.invoice()
                         .buyer();
    assertEquals("4000001000005",
                 buyer.identifier());
    assertEquals("0088",
                 buyer.identifierSchemeId());
    assertEquals("K-4711",
                 loss(mapping,
                      "IZUGFeRDExportableTradeParty.getID",
                      "the buyer Kunde GmbH").value());
    refused(plain(),
            mustang,
            "IZUGFeRDExportableTradeParty.getID");

    var kept = extended().map(mustang);
    assertTrue(kept.isLossless(),
               kept.losses()::toString);
    var extendedBuyer = kept.invoice()
                            .buyer();
    assertEquals("K-4711",
                 extendedBuyer.identifier());
    assertNull(extendedBuyer.identifierSchemeId());
    assertEquals(List.of(new PartyIdentifier("4000001000005",
                                             "0088")),
                 extendedBuyer.additionalIdentifiers());
    validExtended(kept.invoice());
    // Both identifiers survive writing and reading again.
    assertEquals(extendedBuyer,
                 PANIOLO.read(PANIOLO.toBytes(kept.invoice(),
                                              InvoiceSyntax.CII))
                        .buyer());

    // The seller identifier (BT-29) is repeatable: the seller keeps both in every profile.
    Invoice seller = mustangInvoice();
    ((TradeParty) seller.getSender()).setID("S-1")
                                     .addGlobalID(new SchemedID("0088",
                                                                "4000001000005"));
    var sellerMapping = plain().map(seller);
    assertTrue(sellerMapping.isLossless(),
               sellerMapping.losses()::toString);
    assertEquals("S-1",
                 sellerMapping.invoice()
                              .seller()
                              .identifier());
    assertEquals(List.of(new PartyIdentifier("4000001000005",
                                             "0088")),
                 sellerMapping.invoice()
                              .seller()
                              .additionalIdentifiers());
    var sellerResult = PANIOLO.validate(PANIOLO.toBytes(sellerMapping.invoice(),
                                                        InvoiceSyntax.UBL));
    assertTrue(sellerResult.isValid(),
               sellerResult::toString);

    // One identifier only, or the same value twice, is no loss.
    Invoice local = mustangInvoice();
    buyer(local).setID("K-1");
    assertEquals("K-1",
                 plain().toInvoice(local)
                        .buyer()
                        .identifier());
    Invoice global = mustangInvoice();
    buyer(global).addGlobalID(new SchemedID("0088",
                                            "4000001000005"));
    assertEquals("0088",
                 plain().toInvoice(global)
                        .buyer()
                        .identifierSchemeId());
    Invoice same = mustangInvoice();
    buyer(same).setID("4000001000005")
               .addGlobalID(new SchemedID("0088",
                                          "4000001000005"));
    assertTrue(plain().map(same)
                      .isLossless());
  }

  /**
   * P01-06: address, VAT ID, and identifier of a contact.
   */
  @Test
  void keepsEveryValueOfAContact() {
    Invoice mustang = mustangInvoice();
    buyer(mustang).setContact(new Contact("Max Einkauf",
                                          "+49 40 1",
                                          "max@example.org",
                                          "Kontaktweg 3",
                                          "20097",
                                          "Hamburg-Mitte",
                                          "AT") {
      @Override
      public String getAdditionalAddress() {
        return "Haus B";
      }

      @Override
      public String getVATID() {
        return "ATU12345678";
      }

      @Override
      public String getID() {
        return "C-77";
      }
    });
    var mapping = plain().map(mustang);
    lost(mapping,
         "the contact Max Einkauf of the buyer Kunde GmbH",
         Map.of("IZUGFeRDExportableContact.getStreet",
                "Kontaktweg 3",
                "IZUGFeRDExportableContact.getZIP",
                "20097",
                "IZUGFeRDExportableContact.getLocation",
                "Hamburg-Mitte",
                "IZUGFeRDExportableContact.getCountry",
                "AT",
                "IZUGFeRDExportableContact.getAdditionalAddress",
                "Haus B",
                "IZUGFeRDExportableContact.getVATID",
                "ATU12345678",
                "IZUGFeRDExportableContact.getID",
                "C-77"));
    var contact = mapping.invoice()
                         .buyer()
                         .contact();
    assertEquals("Max Einkauf",
                 contact.name());
    assertEquals("max@example.org",
                 contact.email());
    refused(extended(),
            mustang,
            "IZUGFeRDExportableContact.getStreet");
  }

  /**
   * P01-07: the subject codes of several line notes.
   */
  @Test
  void keepsTheSubjectCodeOfEveryLineNote() {
    var regulatory = IncludedNote.regulatoryNote("Prüfbescheinigung liegt bei");
    var customs    = IncludedNote.customsNote("Zolltarif 8471");
    for (var adapter : List.of(plain(),
                               extended())) {
      Invoice mustang = mustangInvoice();
      ((Item) mustang.getZFItems()[0]).addNote(regulatory)
                                      .addNote(customs);
      var mapping = adapter.map(mustang);
      for (var note : List.of(regulatory,
                              customs)) {
        assertEquals(note.getSubjectCode()
                         .name(),
                     loss(mapping,
                          "IZUGFeRDExportableItem.getNotesWithSubjectCode",
                          "the note \"" + note.getContent() + "\" of line 1").value());
      }
      var line = mapping.invoice()
                        .lines()
                        .getFirst();
      assertEquals("Prüfbescheinigung liegt bei\nZolltarif 8471",
                   line.note());
      // No code is given to the joined text.
      assertTrue(line.extended() == null || line.extended()
                                                .noteSubjectCode() == null);
    }
    // One coded note keeps its code in EXTENDED.
    Invoice single = mustangInvoice();
    ((Item) single.getZFItems()[0]).addNote(customs);
    var line = extended().toInvoice(single)
                         .lines()
                         .getFirst();
    assertEquals(customs.getSubjectCode()
                        .name(),
                 line.extended()
                     .noteSubjectCode());
  }

  /**
   * P01-08: several price allowances summed up to the price discount.
   */
  @Test
  void keepsEveryPriceAllowance() {
    Invoice mustang = mustangInvoice();
    book(mustang).addAllowance(new Allowance(new BigDecimal("1.00")).setReason("Aktion")
                                                                   .setReasonCode("95"));
    var mapping = plain().map(mustang);
    var line    = mapping.invoice()
                         .lines()
                         .get(1);
    assertEquals(0,
                 new BigDecimal("3.50").compareTo(line.priceDiscount()));
    assertEquals(0,
                 new BigDecimal("36.40").compareTo(line.netUnitPrice()));
    assertEquals(0,
                 new BigDecimal("2.50").compareTo((BigDecimal) loss(mapping,
                                                                    "IZUGFeRDExportableProduct.getAllowances",
                                                                    "the price allowance 1 of line 2").value()));
    lost(mapping,
         "the price allowance 2 of line 2",
         Map.of("IZUGFeRDAllowanceCharge.getReason",
                "Aktion",
                "IZUGFeRDAllowanceCharge.getReasonCode",
                "95"));
    assertEquals(0,
                 new BigDecimal("1.00").compareTo((BigDecimal) loss(mapping,
                                                                    "IZUGFeRDExportableProduct.getAllowances",
                                                                    "the price allowance 2 of line 2").value()));
    refused(plain(),
            mustang,
            "IZUGFeRDExportableProduct.getAllowances");
  }

  /**
   * P01-09: a price charge that becomes part of the net price.
   */
  @Test
  void keepsAPriceCharge() {
    Invoice mustang = mustangInvoice();
    book(mustang).addCharge(new Charge(new BigDecimal("1.20")).setReason("Kleinmengenzuschlag")
                                                             .setReasonCode("SH"));
    var mapping = plain().map(mustang);
    var line    = mapping.invoice()
                         .lines()
                         .get(1);
    // 39.90 - 2.50 + 1.20, three pieces.
    assertEquals(0,
                 new BigDecimal("38.60").compareTo(line.netUnitPrice()));
    assertEquals(0,
                 new BigDecimal("115.80").compareTo(line.netAmount()));
    assertEquals(0,
                 new BigDecimal("1.20").compareTo((BigDecimal) loss(mapping,
                                                                    "IZUGFeRDExportableProduct.getCharges",
                                                                    "the price charge 1 of line 2").value()));
    lost(mapping,
         "the price charge 1 of line 2",
         Map.of("IZUGFeRDAllowanceCharge.getReason",
                "Kleinmengenzuschlag",
                "IZUGFeRDAllowanceCharge.getReasonCode",
                "SH"));
    refused(plain(),
            mustang,
            "IZUGFeRDExportableProduct.getCharges");
  }

  /**
   * P01-10: the percentage of a document allowance or charge without base amount.
   */
  @Test
  void keepsThePercentageWithoutBaseAmount() {
    Invoice mustang = mustangInvoice();
    mustang.addAllowance(new Allowance().setPercent(new BigDecimal("5"))
                                        .setReason("Treuerabatt")
                                        .setTaxPercent(new BigDecimal("19"))
                                        .setCategoryCode("S"));
    mustang.addCharge(new Charge().setPercent(new BigDecimal("2"))
                                  .setReason("Expresszuschlag")
                                  .setTaxPercent(new BigDecimal("19"))
                                  .setCategoryCode("S"));
    var mapping = plain().map(mustang);
    assertEquals(new BigDecimal("5"),
                 loss(mapping,
                      "IZUGFeRDAllowanceCharge.getPercent",
                      "the document allowance Treuerabatt").value());
    assertEquals(new BigDecimal("2"),
                 loss(mapping,
                      "IZUGFeRDAllowanceCharge.getPercent",
                      "the document charge Expresszuschlag").value());
    for (var adjustment : mapping.invoice()
                                 .adjustments()) {
      if (adjustment.reason()
                    .equals("Versand")) {
        continue;
      }
      // The calculated amount is kept; no base amount is invented.
      assertTrue(adjustment.amount()
                           .signum() > 0);
      assertNull(adjustment.baseAmount());
      assertNull(adjustment.percentage());
    }
    refused(plain(),
            mustang,
            "IZUGFeRDAllowanceCharge.getPercent");
  }

  /**
   * P01-11: a cash discount period in months.
   */
  @Test
  void keepsACashDiscountPeriodInMonths() {
    Invoice mustang = mustangInvoice();
    mustang.addPaymentTerms(new PaymentTerms("Zahlbar innerhalb von 14 Tagen",
                                             date(2026,
                                                  11,
                                                  10),
                                             discount("2",
                                                      1,
                                                      "MON",
                                                      null)));
    var mapping = plain().map(mustang);
    lost(mapping,
         "the cash discount of the payment terms",
         Map.of("IZUGFeRDPaymentDiscountTerms.getBasePeriodUnitCode",
                "MON",
                "IZUGFeRDPaymentDiscountTerms.getBasePeriodMeasure",
                1,
                "IZUGFeRDPaymentDiscountTerms.getCalculationPercentage",
                new BigDecimal("2")));
    // A month is not converted into a number of days.
    assertEquals(List.of(),
                 mapping.invoice()
                        .cashDiscounts());
    refused(plain(),
            mustang,
            "getBasePeriodUnitCode");

    // Without period the percentage has no counterpart either.
    Invoice open = mustangInvoice();
    open.addPaymentTerms(new PaymentTerms("Zahlbar innerhalb von 14 Tagen",
                                          date(2026,
                                               11,
                                               10),
                                          discount("3",
                                                   0,
                                                   null,
                                                   null)));
    assertEquals(new BigDecimal("3"),
                 loss(plain().map(open),
                      "IZUGFeRDPaymentDiscountTerms.getCalculationPercentage",
                      "the cash discount of the payment terms").value());
  }

  /**
   * P01-12: a cash discount period from another base date than the issue date.
   */
  @Test
  void keepsTheEndOfACashDiscountPeriodFromAnotherBaseDate() {
    Invoice mustang = mustangInvoice();
    // 14 days from 20 October end on 3 November: 7 days from the issue date 27 October.
    mustang.addPaymentTerms(new PaymentTerms("Zahlbar innerhalb von 14 Tagen",
                                             null,
                                             discount("2",
                                                      14,
                                                      "DAY",
                                                      date(2026,
                                                           10,
                                                           20))));
    var mapping = plain().map(mustang);
    assertEquals(List.of(CashDiscount.of(7,
                                         new BigDecimal("2"))),
                 mapping.invoice()
                        .cashDiscounts());
    var loss = loss(mapping,
                    "IZUGFeRDPaymentDiscountTerms.getBaseDate",
                    "the cash discount of the payment terms");
    assertEquals(LocalDate.of(2026,
                              10,
                              20),
                 loss.value());
    assertTrue(loss.reason()
                   .contains("2026-11-03"),
               loss::reason);
    refused(plain(),
            mustang,
            "getBaseDate");

    // A period that ends before the issue date cannot count from it.
    Invoice early = mustangInvoice();
    early.addPaymentTerms(new PaymentTerms("Zahlbar sofort",
                                           null,
                                           discount("2",
                                                    14,
                                                    "DAY",
                                                    date(2026,
                                                         10,
                                                         1))));
    var expired = plain().map(early);
    assertEquals(List.of(),
                 expired.invoice()
                        .cashDiscounts());
    lost(expired,
         "the cash discount of the payment terms",
         Map.of("IZUGFeRDPaymentDiscountTerms.getBaseDate",
                LocalDate.of(2026,
                             10,
                             1),
                "IZUGFeRDPaymentDiscountTerms.getBasePeriodMeasure",
                14,
                "IZUGFeRDPaymentDiscountTerms.getCalculationPercentage",
                new BigDecimal("2")));
  }

  /**
   * P01-13: the date of the purchase order.
   */
  @Test
  void keepsTheDateOfThePurchaseOrder() {
    Invoice mustang = mustangInvoice();
    mustang.setBuyerOrderReferencedDocument(new ReferencedDocument("B-2026-17",
                                                                   date(2026,
                                                                        10,
                                                                        1)));
    var mapping = plain().map(mustang);
    assertEquals("B-2026-17",
                 mapping.invoice()
                        .references()
                        .purchaseOrderReference());
    assertEquals(LocalDate.of(2026,
                              10,
                              1),
                 loss(mapping,
                      "IExportableTransaction.getBuyerOrderReferencedDocument",
                      "the purchase order reference B-2026-17").value());
    refused(extended(),
            mustang,
            "getBuyerOrderReferencedDocument");
  }

  /**
   * P01-14: the project name next to the project reference.
   */
  @Test
  void keepsTheProjectName() {
    Invoice mustang = mustangInvoice();
    mustang.setSpecifiedProcuringProjectID("P-77")
           .setSpecifiedProcuringProjectName("Projekt Nord");
    var mapping = plain().map(mustang);
    assertEquals("P-77",
                 mapping.invoice()
                        .references()
                        .projectReference());
    assertEquals("Projekt Nord",
                 loss(mapping,
                      "IExportableTransaction.getSpecifiedProcuringProjectName",
                      "the project P-77").value());
    refused(extended(),
            mustang,
            "getSpecifiedProcuringProjectName");
  }

  /**
   * P01-15: the relationship of an attachment embedded in the XML.
   */
  @Test
  void keepsTheRelationshipOfAnAttachment() {
    byte[] content = "<aufmass/>".getBytes(StandardCharsets.UTF_8);
    Invoice mustang = mustangInvoice();
    mustang.embedFileInXML(new FileAttachment("aufmass.xml",
                                              "application/xml",
                                              "Data",
                                              content).setDescription("Aufmaß"));
    var mapping    = plain().map(mustang);
    var attachment = mapping.invoice()
                            .supportingDocuments()
                            .getFirst()
                            .attachment();
    assertArrayEquals(content,
                      attachment.content());
    assertEquals("aufmass.xml",
                 attachment.filename());
    assertEquals("application/xml",
                 attachment.mimeCode());
    assertEquals("Data",
                 loss(mapping,
                      "FileAttachment.getRelation",
                      "the attachment aufmass.xml").value());
    refused(plain(),
            mustang,
            "FileAttachment.getRelation");
  }

  /**
   * P01-16: address, VAT ID, and contact of the payee.
   */
  @Test
  void keepsTheValuesOfThePayee() {
    Invoice mustang = mustangInvoice();
    mustang.setPayee(new TradeParty("Factoring AG",
                                    "Bankweg 1",
                                    "60311",
                                    "Frankfurt",
                                    "DE").setID("PAY-1")
                                         .setVATID("DE999999999")
                                         .setContact(new Contact("Paula Payee",
                                                                 "+49 69 1",
                                                                 "payee@example.org")));
    var mapping = plain().map(mustang);
    var payee   = mapping.invoice()
                         .payee();
    assertEquals("Factoring AG",
                 payee.name());
    assertEquals("PAY-1",
                 payee.identifier());
    lost(mapping,
         "the payee Factoring AG",
         Map.of("IZUGFeRDExportableTradeParty.getStreet",
                "Bankweg 1",
                "IZUGFeRDExportableTradeParty.getZIP",
                "60311",
                "IZUGFeRDExportableTradeParty.getLocation",
                "Frankfurt",
                "IZUGFeRDExportableTradeParty.getCountry",
                "DE",
                "IZUGFeRDExportableTradeParty.getVATID",
                "DE999999999"));
    lost(mapping,
         "the contact Paula Payee of the payee Factoring AG",
         Map.of("IZUGFeRDExportableContact.getName",
                "Paula Payee",
                "IZUGFeRDExportableContact.getPhone",
                "+49 69 1",
                "IZUGFeRDExportableContact.getEMail",
                "payee@example.org"));
    refused(extended(),
            mustang,
            "IZUGFeRDExportableTradeParty.getVATID");
  }

  /**
   * P01-17: identifiers, tax number, and contact of the tax representative.
   */
  @Test
  void keepsTheValuesOfTheTaxRepresentative() {
    Invoice mustang = mustangInvoice();
    mustang.setTaxRepresentative(new TradeParty("Steuer Vertretung GmbH",
                                                "Steuerweg 5",
                                                "1010",
                                                "Wien",
                                                "AT").setVATID("ATU99999999")
                                                     .setTaxID("123/456/789")
                                                     .setID("TR-1")
                                                     .addGlobalID(new SchemedID("0088",
                                                                                "4000001000012"))
                                                     .setContact(new Contact("Tom Tax",
                                                                             "+43 1 2",
                                                                             "tax@example.org")));
    var mapping        = plain().map(mustang);
    var representative = mapping.invoice()
                                .taxRepresentative();
    assertEquals("ATU99999999",
                 representative.vatId());
    assertEquals("DE123456788",
                 mapping.invoice()
                        .seller()
                        .vatId());
    lost(mapping,
         "the tax representative Steuer Vertretung GmbH",
         Map.of("IZUGFeRDExportableTradeParty.getTaxID",
                "123/456/789",
                "IZUGFeRDExportableTradeParty.getID",
                "TR-1",
                "IZUGFeRDExportableTradeParty.getGlobalID",
                "4000001000012",
                "IZUGFeRDExportableTradeParty.getGlobalIDScheme",
                "0088"));
    lost(mapping,
         "the contact Tom Tax of the tax representative Steuer Vertretung GmbH",
         Map.of("IZUGFeRDExportableContact.getName",
                "Tom Tax",
                "IZUGFeRDExportableContact.getPhone",
                "+43 1 2",
                "IZUGFeRDExportableContact.getEMail",
                "tax@example.org"));
    refused(plain(),
            mustang,
            "IZUGFeRDExportableTradeParty.getTaxID");
  }

  /**
   * P01-18: VAT ID, tax number, and contact of the ship-to party.
   */
  @Test
  void keepsTheValuesOfTheShipToParty() {
    Invoice mustang = mustangInvoice();
    mustang.setDeliveryAddress(new TradeParty("Lager Nord",
                                              "Lagerweg 9",
                                              "22113",
                                              "Hamburg",
                                              "DE").setVATID("DE111111111")
                                                   .setTaxID("22/333/44444")
                                                   .setContact(new Contact("Lars Lager",
                                                                           "+49 40 9",
                                                                           "lager@example.org")));
    var mapping  = plain().map(mustang);
    var delivery = mapping.invoice()
                          .delivery();
    assertEquals("Lager Nord",
                 delivery.partyName());
    assertEquals("Lagerweg 9",
                 delivery.address()
                         .street());
    assertNull(mapping.invoice()
                      .buyer()
                      .vatId());
    lost(mapping,
         "the ship-to party Lager Nord",
         Map.of("IZUGFeRDExportableTradeParty.getVATID",
                "DE111111111",
                "IZUGFeRDExportableTradeParty.getTaxID",
                "22/333/44444"));
    lost(mapping,
         "the contact Lars Lager of the ship-to party Lager Nord",
         Map.of("IZUGFeRDExportableContact.getName",
                "Lars Lager",
                "IZUGFeRDExportableContact.getPhone",
                "+49 40 9",
                "IZUGFeRDExportableContact.getEMail",
                "lager@example.org"));
    refused(extended(),
            mustang,
            "IZUGFeRDExportableTradeParty.getTaxID");
  }

  /**
   * P01-19 and P01-20: tax number and additional legal information of other parties than the
   * seller.
   */
  @Test
  void keepsTaxNumberAndLegalInformationOfEveryParty() {
    Invoice mustang = mustangInvoice();
    buyer(mustang).setTaxID("11/111/11111")
                  .setDescription("Amtsgericht Hamburg HRB 1");
    mustang.setInvoicer(new TradeParty("Rechnungssteller AG",
                                       "Postweg 4",
                                       "50667",
                                       "Köln",
                                       "DE").setTaxID("22/222/22222")
                                            .setDescription("Amtsgericht Köln HRB 2"));
    var mapping = extended().map(mustang);
    lost(mapping,
         "the buyer Kunde GmbH",
         Map.of("IZUGFeRDExportableTradeParty.getTaxID",
                "11/111/11111",
                "IZUGFeRDExportableTradeParty.getDescription",
                "Amtsgericht Hamburg HRB 1"));
    lost(mapping,
         "the invoicer Rechnungssteller AG",
         Map.of("IZUGFeRDExportableTradeParty.getTaxID",
                "22/222/22222",
                "IZUGFeRDExportableTradeParty.getDescription",
                "Amtsgericht Köln HRB 2"));
    var invoice = mapping.invoice();
    // No tax number becomes a VAT ID or moves to another party.
    assertNull(invoice.buyer()
                      .taxId());
    assertNull(invoice.buyer()
                      .vatId());
    assertNull(invoice.seller()
                      .taxId());
    assertNull(invoice.extended()
                      .invoicer()
                      .taxId());
    refused(extended(),
            mustang,
            "IZUGFeRDExportableTradeParty.getDescription");
  }

  /**
   * P01-21: the date of the contract reference.
   */
  @Test
  void keepsTheDateOfTheContract() {
    Invoice mustang = mustangInvoice();
    mustang.setContractReferencedDocument(new ReferencedDocument("V-2026-3",
                                                                 date(2026,
                                                                      1,
                                                                      15)));
    var mapping = plain().map(mustang);
    assertEquals("V-2026-3",
                 mapping.invoice()
                        .references()
                        .contractReference());
    assertEquals(LocalDate.of(2026,
                              1,
                              15),
                 loss(mapping,
                      "IExportableTransaction.getContractReferencedDocument",
                      "the contract reference V-2026-3").value());
    refused(extended(),
            mustang,
            "IExportableTransaction.getContractReferencedDocument");
  }

  /**
   * P01-22: the percentages of price allowances and charges in EXTENDED.
   */
  @Test
  void keepsThePercentagesOfPriceAdjustmentsInExtended() {
    Invoice mustang = mustangInvoice();
    book(mustang).addAllowance(new Allowance().setPercent(new BigDecimal("5"))
                                              .setReason("Staffel"))
                 .addCharge(new Charge().setPercent(new BigDecimal("3"))
                                        .setReason("Eilzuschlag"));
    var mapping = extended().map(mustang);
    assertEquals(new BigDecimal("5"),
                 loss(mapping,
                      "IZUGFeRDAllowanceCharge.getPercent",
                      "the price allowance 2 of line 2").value());
    assertEquals(new BigDecimal("3"),
                 loss(mapping,
                      "IZUGFeRDAllowanceCharge.getPercent",
                      "the price charge 1 of line 2").value());
    // The calculated amounts are kept: 5 % and 3 % of 39.90.
    var amounts = mapping.invoice()
                         .lines()
                         .get(1)
                         .extended()
                         .priceAdjustments()
                         .stream()
                         .map(adjustment -> adjustment.amount()
                                                      .stripTrailingZeros()
                                                      .toPlainString())
                         .toList();
    assertEquals(List.of("2.5",
                         "1.995",
                         "1.197"),
                 amounts);
    refused(extended(),
            mustang,
            "IZUGFeRDAllowanceCharge.getPercent");
  }
}

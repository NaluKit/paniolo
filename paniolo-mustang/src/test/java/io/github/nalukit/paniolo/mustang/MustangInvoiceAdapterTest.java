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
import io.github.nalukit.paniolo.model.common.ProcessControl;
import io.github.nalukit.paniolo.model.extended.GrossPriceAdjustment;
import io.github.nalukit.paniolo.model.extended.LineSubtype;
import io.github.nalukit.paniolo.validation.Severity;
import io.github.nalukit.paniolo.validation.ValidationMessage;
import io.github.nalukit.paniolo.validation.ValidationResult;
import org.junit.jupiter.api.Test;
import org.mustangproject.Allowance;
import org.mustangproject.BankDetails;
import org.mustangproject.Charge;
import org.mustangproject.Contact;
import org.mustangproject.Invoice;
import org.mustangproject.Item;
import org.mustangproject.LegalOrganisation;
import org.mustangproject.Product;
import org.mustangproject.TradeParty;
import org.mustangproject.ZUGFeRD.IExportableTransaction;
import org.mustangproject.ZUGFeRD.IZUGFeRDAllowanceCharge;
import org.mustangproject.ZUGFeRD.IZUGFeRDExportableContact;
import org.mustangproject.ZUGFeRD.IZUGFeRDExportableItem;
import org.mustangproject.ZUGFeRD.IZUGFeRDExportableProduct;
import org.mustangproject.ZUGFeRD.IZUGFeRDExportableTradeParty;
import org.mustangproject.ZUGFeRD.IZUGFeRDLegalOrganisation;
import org.mustangproject.ZUGFeRD.IZUGFeRDPaymentDiscountTerms;
import org.mustangproject.ZUGFeRD.IZUGFeRDPaymentTerms;
import org.mustangproject.ZUGFeRD.IZUGFeRDTradeSettlementDebit;
import org.mustangproject.ZUGFeRD.IZUGFeRDTradeSettlementPayment;
import org.mustangproject.ZUGFeRD.Profiles;
import org.mustangproject.ZUGFeRD.ZUGFeRD2PullProvider;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Maps invoices built with the real classes of Mustang 2.26.0 and compares the result with
 * Mustang's own XML export.
 */
class MustangInvoiceAdapterTest {
  private static final Paniolo PANIOLO = Paniolo.create();
  private static final ZoneId  ZONE    = ZoneId.of("Europe/Berlin");

  static Date date(int year,
                           int month,
                           int day) {
    return Date.from(LocalDate.of(year,
                                  month,
                                  day)
                              .atStartOfDay(ZONE)
                              .toInstant());
  }

  private static TradeParty seller() {
    return new TradeParty("Muster GmbH",
                          "Musterstraße 1",
                          "10115",
                          "Berlin",
                          "DE").setVATID("DE123456788")
                               .setEmail("rechnung@example.org")
                               .setContact(new Contact("Erika Muster",
                                                       "+49 30 123456",
                                                       "rechnung@example.org"))
                               .setLegalOrganisation(new LegalOrganisation("HRB 12345",
                                                                           "0002"))
                               .addBankDetails(new BankDetails("DE89370400440532013000",
                                                               "COBADEFFXXX").setAccountName("Muster GmbH"));
  }

  /**
   * An XRechnung as a Mustang integration builds it: two VAT rates, a price allowance, a line
   * allowance in percent, a document charge, notes, and payment by credit transfer.
   */
  static Invoice mustangInvoice() {
    var consulting = new Item(new Product("Beratung",
                                          "Beratung vor Ort",
                                          "HUR",
                                          new BigDecimal("19")),
                              new BigDecimal("100.00"),
                              new BigDecimal("2"));
    consulting.addAllowance(new Allowance().setPercent(new BigDecimal("10"))
                                           .setReason("Projektrabatt")
                                           .setReasonCode("95"));
    var book = new Product("Fachbuch",
                           null,
                           "H87",
                           new BigDecimal("7"));
    book.addAllowance(new Allowance(new BigDecimal("2.50")));
    var books = new Item(book,
                         new BigDecimal("39.90"),
                         new BigDecimal("3"));
    return new Invoice().setNumber("R-2026-0815")
                        .setIssueDate(date(2026,
                                           10,
                                           27))
                        .setDueDate(date(2026,
                                         11,
                                         10))
                        .setDeliveryDate(date(2026,
                                              10,
                                              26))
                        .setReferenceNumber("04011000-12345-03")
                        .setSender(seller())
                        .setRecipient(new TradeParty("Kunde GmbH",
                                                     "Kundenweg 2",
                                                     "20095",
                                                     "Hamburg",
                                                     "DE").setEmail("einkauf@example.org"))
                        .setPaymentTermDescription("Zahlbar innerhalb von 14 Tagen")
                        .addNote("Vielen Dank für Ihren Auftrag.")
                        .addItem(consulting)
                        .addItem(books)
                        .addCharge(new Charge(new BigDecimal("4.90")).setReason("Versand")
                                                                     .setTaxPercent(new BigDecimal("19"))
                                                                     .setCategoryCode("S"));
  }

  private static List<ValidationMessage> problems(ValidationResult result) {
    return result.messages()
                 .stream()
                 .filter(message -> message.severity() != Severity.INFO)
                 .toList();
  }

  @Test
  void mapsAMustangXrechnungToAValidPanioloInvoice() {
    var mapping = MustangInvoiceAdapter.builder()
                                       .zone(ZONE)
                                       .build()
                                       .map(mustangInvoice());
    assertTrue(mapping.isLossless(),
               mapping.losses()::toString);
    var invoice = mapping.invoice();
    assertEquals("R-2026-0815",
                 invoice.number());
    assertEquals(LocalDate.of(2026,
                              10,
                              27),
                 invoice.issueDate());
    assertEquals(ProcessControl.XRECHNUNG_3,
                 invoice.processControl()
                        .specificationId());
    assertEquals("HRB 12345",
                 invoice.seller()
                        .legalRegistrationId());
    assertEquals("DE89370400440532013000",
                 invoice.paymentInstruction()
                        .creditTransfers()
                        .getFirst()
                        .iban());
    var books = invoice.lines()
                       .get(1);
    assertEquals("2",
                 books.id());
    assertEquals(0,
                 new BigDecimal("2.50").compareTo(books.priceDiscount()));
    assertEquals(0,
                 new BigDecimal("37.40").compareTo(books.netUnitPrice()));
    for (InvoiceSyntax syntax : InvoiceSyntax.values()) {
      var result = PANIOLO.validate(PANIOLO.toBytes(invoice,
                                                    syntax));
      assertEquals(List.of(),
                   problems(result),
                   syntax + ": " + result);
    }
  }

  @Test
  void hasTheAmountsOfMustangsOwnExport()
      throws Exception {
    Invoice mustang = mustangInvoice();
    var provider = new ZUGFeRD2PullProvider();
    provider.setProfile(Profiles.getByName("XRechnung"));
    provider.generateXML(mustang);
    var exported = PANIOLO.read(provider.getXML());

    // Mustang writes dates in the JVM default zone, so the adapter has to use the same zone here.
    var mapped = MustangInvoiceAdapter.builder()
                                      .zone(ZoneId.systemDefault())
                                      .build()
                                      .toInvoice(mustangInvoice());
    assertEquals(exported.lines()
                         .size(),
                 mapped.lines()
                       .size());
    for (int i = 0; i < mapped.lines()
                              .size(); i++) {
      var expected = exported.lines()
                             .get(i);
      var actual   = mapped.lines()
                           .get(i);
      assertEquals(0,
                   expected.netAmount()
                           .compareTo(actual.netAmount()),
                   "line " + actual.id() + ": " + expected.netAmount() + " / " + actual.netAmount());
      assertEquals(0,
                   expected.netUnitPrice()
                           .compareTo(actual.netUnitPrice()),
                   "net price of line " + actual.id());
    }
    var expectedTotals = exported.totals();
    var actualTotals   = mapped.totals();
    for (var amount : List.of(new BigDecimal[] { expectedTotals.lineTotal(),
                                                 actualTotals.lineTotal() },
                              new BigDecimal[] { expectedTotals.chargeTotal(),
                                                 actualTotals.chargeTotal() },
                              new BigDecimal[] { expectedTotals.taxAmount(),
                                                 actualTotals.taxAmount() },
                              new BigDecimal[] { expectedTotals.payableAmount(),
                                                 actualTotals.payableAmount() })) {
      assertEquals(0,
                   amount[0].compareTo(amount[1]),
                   amount[0] + " / " + amount[1]);
    }
    assertEquals(exported.buyerReference(),
                 mapped.buyerReference());
    assertEquals(exported.dueDate(),
                 mapped.dueDate());
  }

  @Test
  void writesPercentagePriceAllowancesWithoutMustangsInternalScale() {
    Invoice mustang = mustangInvoice();
    var line = new Item(new Product("Lizenz",
                                    null,
                                    "C62",
                                    new BigDecimal("19")).addAllowance(new Allowance().setPercent(new BigDecimal("10"))),
                        new BigDecimal("39.90"),
                        BigDecimal.ONE);
    mustang.addItem(line);
    var mapping = MustangInvoiceAdapter.builder()
                                       .zone(ZONE)
                                       .build()
                                       .map(mustang);
    // EN 16931 knows the amount of a price discount, not its percentage.
    assertEquals(List.of("IZUGFeRDExportableProduct.getAllowances",
                         "IZUGFeRDAllowanceCharge.getPercent"),
                 mapping.losses()
                        .stream()
                        .map(MappingLoss::getter)
                        .toList());
    var invoice = mapping.invoice();
    var mapped  = invoice.lines()
                         .get(2);
    assertEquals("3.99",
                 mapped.priceDiscount()
                       .toPlainString());
    assertEquals("35.91",
                 mapped.netUnitPrice()
                       .toPlainString());
    String xml = new String(PANIOLO.toBytes(invoice,
                                            InvoiceSyntax.CII),
                            java.nio.charset.StandardCharsets.UTF_8);
    assertFalse(xml.contains("000000"),
                xml);
    assertEquals(List.of(),
                 problems(PANIOLO.validate(xml)));
  }

  @Test
  void reportsEveryValueWithoutCounterpart() {
    Invoice mustang = mustangInvoice();
    mustang.getSender()
           .setContact(new Contact("Erika Muster",
                                   "+49 30 123456",
                                   "rechnung@example.org").setFax("+49 30 123457"));
    mustang.setInvoicee(new TradeParty("Zentrale Rechnungsstelle",
                                       "Postfach 1",
                                       "20095",
                                       "Hamburg",
                                       "DE"));
    ((Product) mustang.getZFItems()[1].getProduct()).addAllowance(new Allowance(new BigDecimal("1.00")).setReason("Aktion"));
    mustang.setSpecifiedProcuringProjectName("Projekt Nord");

    var mapping = MustangInvoiceAdapter.builder()
                                       .zone(ZONE)
                                       .build()
                                       .map(mustang);
    var getters = mapping.losses()
                         .stream()
                         .map(MappingLoss::getter)
                         .toList();
    assertTrue(getters.containsAll(List.of("IZUGFeRDExportableContact.getFax",
                                           "IExportableTransaction.getInvoicee",
                                           "IZUGFeRDExportableProduct.getAllowances",
                                           "IExportableTransaction.getSpecifiedProcuringProjectName")),
               mapping.losses()::toString);
    // The amounts are not lost: the two price allowances are summed up.
    assertEquals(0,
                 new BigDecimal("3.50").compareTo(mapping.invoice()
                                                         .lines()
                                                         .get(1)
                                                         .priceDiscount()));
    var refused = assertThrows(IllegalArgumentException.class,
                               () -> MustangInvoiceAdapter.create()
                                                          .toInvoice(mustang));
    assertTrue(refused.getMessage()
                      .contains("IZUGFeRDExportableContact.getFax"),
               refused::getMessage);
  }

  @Test
  void keepsExtendedContentForTheProfileExtended() {
    Invoice mustang = mustangInvoice();
    mustang.getSender()
           .setContact(new Contact("Erika Muster",
                                   "+49 30 123456",
                                   "rechnung@example.org").setFax("+49 30 123457"));
    mustang.setInvoicee(new TradeParty("Zentrale Rechnungsstelle",
                                       "Postfach 1",
                                       "20095",
                                       "Hamburg",
                                       "DE"));
    var book = (Product) mustang.getZFItems()[1].getProduct();
    book.addAllowance(new Allowance(new BigDecimal("1.00")).setReason("Aktion"));
    var mapping = MustangInvoiceAdapter.builder()
                                       .processControl(ProcessControl.facturXExtended())
                                       .zone(ZONE)
                                       .build()
                                       .map(mustang);
    assertTrue(mapping.isLossless(),
               mapping.losses()::toString);
    var invoice = mapping.invoice();
    assertEquals("+49 30 123457",
                 invoice.seller()
                        .extended()
                        .contact()
                        .fax());
    assertEquals("Zentrale Rechnungsstelle",
                 invoice.extended()
                        .invoicee()
                        .name());
    assertEquals(List.of(GrossPriceAdjustment.allowance(new BigDecimal("2.50")),
                         new GrossPriceAdjustment(false,
                                                  new BigDecimal("1.00"),
                                                  null,
                                                  "Aktion")),
                 invoice.lines()
                        .get(1)
                        .extended()
                        .priceAdjustments());
    var result = PANIOLO.validate(PANIOLO.toBytes(invoice,
                                                  InvoiceSyntax.CII),
                                  FacturXProfile.EXTENDED);
    assertEquals(List.of(),
                 problems(result),
                 result::toString);
  }

  @Test
  void mapsTheLineHierarchyOnlyInExtended() {
    Invoice mustang = mustangInvoice();
    var items = mustang.getZFItems();
    ((Item) items[0]).setId("1");
    ((Item) items[1]).setId("1.1")
                     .setParentLineID("1");
    ((Item) items[1]).setLineStatusReasonCode("DETAIL");
    var plain = MustangInvoiceAdapter.create()
                                     .map(mustang);
    assertTrue(plain.losses()
                    .stream()
                    .anyMatch(loss -> loss.getter()
                                          .equals("IZUGFeRDExportableItem.getParentLineID")),
               plain.losses()::toString);
    var line = MustangInvoiceAdapter.builder()
                                    .processControl(ProcessControl.facturXExtended())
                                    .build()
                                    .toInvoice(mustang)
                                    .lines()
                                    .get(1);
    assertEquals("1",
                 line.extended()
                     .parentLineId());
    assertEquals(LineSubtype.DETAIL,
                 line.extended()
                     .subtype());
  }

  @Test
  void documentsEveryGetterOfMustangsExportInterfaces() {
    var fields = MustangInvoiceAdapter.FIELDS;
    // XML fragments of Mustang's own export are not invoice data.
    Set<String> xml = Set.of("getSettlementXML",
                             "getPaymentXML",
                             "getAsCII");
    var missing = new ArrayList<String>();
    for (Class<?> type : List.of(IExportableTransaction.class,
                                 IZUGFeRDExportableTradeParty.class,
                                 IZUGFeRDExportableContact.class,
                                 IZUGFeRDLegalOrganisation.class,
                                 IZUGFeRDExportableItem.class,
                                 IZUGFeRDExportableProduct.class,
                                 IZUGFeRDAllowanceCharge.class,
                                 IZUGFeRDTradeSettlementPayment.class,
                                 IZUGFeRDTradeSettlementDebit.class,
                                 IZUGFeRDPaymentTerms.class,
                                 IZUGFeRDPaymentDiscountTerms.class)) {
      for (Method method : type.getDeclaredMethods()) {
        String name = method.getName();
        if (method.isSynthetic() || xml.contains(name)) {
          continue;
        }
        String qualified = type.getSimpleName() + "." + name;
        // Its own entry, or named in the note of an entry of the same interface or naming it.
        boolean listed = fields.stream()
                               .anyMatch(field -> field.getter()
                                                       .equals(qualified) || field.note()
                                                                                  .contains(name) && (field.getter()
                                                                                                           .startsWith(type.getSimpleName() + ".") || field.note()
                                                                                                                                                         .contains(type.getSimpleName())));
        if (!listed) {
          missing.add(qualified);
        }
      }
    }
    assertEquals(List.of(),
                 missing);
    assertFalse(MustangInvoiceAdapter.FIELDS.isEmpty());
    assertNotNull(Arrays.stream(FieldMapping.Status.values())
                        .toList());
  }
}

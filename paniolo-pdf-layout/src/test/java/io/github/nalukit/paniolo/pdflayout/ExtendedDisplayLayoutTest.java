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

import io.github.nalukit.paniolo.Paniolo;
import io.github.nalukit.paniolo.config.PdfLayout;
import io.github.nalukit.paniolo.model.Invoice;
import io.github.nalukit.paniolo.model.common.Address;
import io.github.nalukit.paniolo.model.common.DirectDebit;
import io.github.nalukit.paniolo.model.common.InvoiceLine;
import io.github.nalukit.paniolo.model.common.InvoicePeriod;
import io.github.nalukit.paniolo.model.common.Party;
import io.github.nalukit.paniolo.model.common.PaymentInstruction;
import io.github.nalukit.paniolo.model.common.ProcessControl;
import io.github.nalukit.paniolo.model.common.References;
import io.github.nalukit.paniolo.model.common.ThirdPartyPayment;
import io.github.nalukit.paniolo.model.common.value.Country;
import io.github.nalukit.paniolo.model.common.value.CurrencyCode;
import io.github.nalukit.paniolo.model.common.value.PaymentMeansCode;
import io.github.nalukit.paniolo.model.common.value.UnitCode;
import io.github.nalukit.paniolo.model.extended.AccountingAccount;
import io.github.nalukit.paniolo.model.extended.DocumentReference;
import io.github.nalukit.paniolo.model.extended.ExtendedInformation;
import io.github.nalukit.paniolo.model.extended.FinancialAdjustment;
import io.github.nalukit.paniolo.model.extended.GrossPriceAdjustment;
import io.github.nalukit.paniolo.model.extended.IncludedProduct;
import io.github.nalukit.paniolo.model.extended.LineExtension;
import io.github.nalukit.paniolo.model.extended.LineSubtype;
import io.github.nalukit.paniolo.model.extended.LogisticsServiceCharge;
import io.github.nalukit.paniolo.model.extended.ObjectIdentifier;
import io.github.nalukit.paniolo.model.extended.ProductCharacteristic;
import io.github.nalukit.paniolo.model.extended.ProductInstance;
import io.github.nalukit.paniolo.model.extended.TaxExchangeRate;
import io.github.nalukit.paniolo.model.extended.TypedDocumentReference;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.verapdf.gf.foundry.VeraGreenfieldFoundryProvider;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The EXTENDED information the visual invoice shows (task R05, Mustang issue #1331): further
 * parties, logistics service charges, financial adjustments, document, product, and reference
 * details, in German, English, and French, with totals that add up to the amount due.
 */
class ExtendedDisplayLayoutTest {
  private static final Paniolo PANIOLO = Paniolo.create();

  @BeforeAll
  static void verapdf() {
    VeraGreenfieldFoundryProvider.initialise();
  }

  private static String text(byte[] pdf) {
    try (var document = Loader.loadPDF(pdf)) {
      return new PDFTextStripper().getText(document)
                                  .replace(' ',
                                           ' ')
                                  .replace(' ',
                                           ' ');
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static int pages(byte[] pdf) {
    try (var document = Loader.loadPDF(pdf)) {
      return document.getNumberOfPages();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static Party party(String name,
                             String city) {
    return Party.create()
                .name(name)
                .address(Address.create()
                                .street("Weg 1")
                                .city(city)
                                .postalCode("10115")
                                .country(Country.DE)
                                .build())
                .vatId("DE111111111")
                .build();
  }

  private static InvoiceLine detailedLine(String id,
                                          String token) {
    return InvoiceLine.create()
                      .id(id)
                      .name("Maschine " + token)
                      .quantity(new BigDecimal("2"))
                      .unitCode("H87")
                      .grossPrice(new BigDecimal("110.00"))
                      .priceDiscount(new BigDecimal("10.00"))
                      .netUnitPrice(new BigDecimal("100.00"))
                      .tax(TestInvoices.VAT_19)
                      .objectId("OBJ-1",
                                null)
                      .buyerAccountingReference("KST-1")
                      .extended(LineExtension.create()
                                             .productId(token)
                                             .industryItemId("HERST-1")
                                             .modelName("Pro")
                                             .modelId("M-2")
                                             .brandName("Acme")
                                             .manufacturer(party("Acme Werke",
                                                                 "Stuttgart"))
                                             .addBatchId("B-7")
                                             .addInstance(new ProductInstance(null,
                                                                              "S-123"))
                                             .addCharacteristic(new ProductCharacteristic("COL",
                                                                                          "Farbe",
                                                                                          "blau",
                                                                                          null,
                                                                                          null))
                                             .addIncludedProduct(new IncludedProduct("Handbuch",
                                                                                     null,
                                                                                     null,
                                                                                     null,
                                                                                     null,
                                                                                     null,
                                                                                     BigDecimal.ONE,
                                                                                     UnitCode.C62))
                                             .chargeFreeQuantity(BigDecimal.ONE)
                                             .packageQuantity(new BigDecimal("2"))
                                             .perPackageUnitQuantity(BigDecimal.ONE)
                                             .deliveryTermsCode("EXW")
                                             .itemSeller(party("Unterverkäufer GmbH",
                                                               "Köln"))
                                             .shipTo(party("Lager Süd",
                                                           "München"))
                                             .sellerOrderReference(new DocumentReference("AU-5",
                                                                                         null,
                                                                                         null))
                                             .contractReference(new DocumentReference("V-8",
                                                                                      null,
                                                                                      null))
                                             .receivingAdviceReference(new DocumentReference("WE-3",
                                                                                             null,
                                                                                             null))
                                             .precedingInvoiceReference(new DocumentReference("R-0",
                                                                                              null,
                                                                                              null))
                                             .addUltimateCustomerOrderReference(new DocumentReference("EK-10",
                                                                                                      null,
                                                                                                      null))
                                             .addAdditionalReference(new TypedDocumentReference("Z-1",
                                                                                                "916",
                                                                                                null))
                                             .addFurtherObjectIdentifier(new ObjectIdentifier("OBJ-2",
                                                                                              null))
                                             .addAdditionalAccountingAccount(new AccountingAccount("KST-2",
                                                                                                   null))
                                             .addPriceAdjustment(new GrossPriceAdjustment(false,
                                                                                          new BigDecimal("10.00"),
                                                                                          null,
                                                                                          "Mengenrabatt"))
                                             .taxTotal(new BigDecimal("38.00"))
                                             .grossTotal(new BigDecimal("238.00"))
                                             .build())
                      .build();
  }

  /**
   * Lines 200.00 (19 %) and 119.70 (7 %, below a subtotal line), freight 25.00 (19 %): net 344.70,
   * VAT 42.75 + 8.38, gross 395.83, financial adjustment -10.00, amount due 385.83.
   */
  static Invoice invoice() {
    var simple = TestInvoices.simple("X-1")
                             .build();
    var book = simple.lines()
                     .get(1);
    return TestInvoices.simple("X-1")
                       .processControl(ProcessControl.facturXExtended())
                       .invoicePeriod(new InvoicePeriod(LocalDate.of(2026,
                                                                     10,
                                                                     1),
                                                        LocalDate.of(2026,
                                                                     10,
                                                                     31)))
                       .references(References.create()
                                             .buyerAccountingReference("4711-A")
                                             .build())
                       .taxCurrencyCode(CurrencyCode.of("USD"))
                       .taxAmountInTaxCurrency(new BigDecimal("56.25"))
                       .paymentInstruction(PaymentInstruction.create()
                                                             .meansCode(PaymentMeansCode.CODE_59)
                                                             .directDebit(new DirectDebit("M-1",
                                                                                          "DE98ZZZ09999999999",
                                                                                          "DE02120300000000202051"))
                                                             .build())
                       .lines(List.of(detailedLine("1",
                                                   "P-100"),
                                      InvoiceLine.create()
                                                 .id("10")
                                                 .name("Baugruppe Bücher")
                                                 .netAmount(new BigDecimal("119.70"))
                                                 .extended(LineExtension.create()
                                                                        .subtype(LineSubtype.GROUP)
                                                                        .build())
                                                 .build(),
                                      book.toBuilder()
                                          .id("11")
                                          .extended(LineExtension.create()
                                                                 .parentLineId("10")
                                                                 .subtype(LineSubtype.DETAIL)
                                                                 .build())
                                          .build()))
                       .extended(ExtendedInformation.create()
                                                    .documentName("Rechnungskopie Projekt A")
                                                    .testIndicator(true)
                                                    .copyIndicator(true)
                                                    .effectiveDate(LocalDate.of(2026,
                                                                                10,
                                                                                2))
                                                    .billingPeriodDescription("Oktober-Abrechnung")
                                                    .quotationReference(new DocumentReference("ANG-77",
                                                                                              null,
                                                                                              LocalDate.of(2026,
                                                                                                           9,
                                                                                                           15)))
                                                    .deliveryNoteReference(new DocumentReference("LS-1",
                                                                                                 null,
                                                                                                 null))
                                                    .addUltimateCustomerOrderReference(new DocumentReference("EK-9",
                                                                                                             null,
                                                                                                             null))
                                                    .invoiceIssuerReference("RS-REF-3")
                                                    .addAdditionalAccountingAccount(new AccountingAccount("4711-B",
                                                                                                          null))
                                                    .deliveryTermsCode("FCA")
                                                    .deliveryTermsLocationName("Hamburg Hafen")
                                                    .deliveryTermsLocationCountry(Country.DE)
                                                    .transportModeCode("3")
                                                    .salesAgent(party("Vertrieb Nord GmbH",
                                                                      "Kiel"))
                                                    .buyerAgent(party("Einkaufsbüro West",
                                                                      "Essen"))
                                                    .buyerTaxRepresentative(party("Steuer AG",
                                                                                  "Bonn"))
                                                    .productEndUser(party("Endnutzer KG",
                                                                          "Ulm"))
                                                    .shipFrom(party("Zentrallager",
                                                                    "Kassel"))
                                                    .ultimateShipTo(party("Baustelle 9",
                                                                          "Jena"))
                                                    .invoicer(party("Abrechnungsstelle",
                                                                    "Halle"))
                                                    .invoicee(party("Rechnungsprüfung",
                                                                    "Gera"))
                                                    .payer(party("Zahlstelle AG",
                                                                 "Suhl"))
                                                    .payerAccountName("Kunde GmbH Konto")
                                                    .payerBic("BYLADEM1001")
                                                    .taxExchangeRate(new TaxExchangeRate(new BigDecimal("1.1"),
                                                                                         LocalDate.of(2026,
                                                                                                      10,
                                                                                                      27)))
                                                    .addLogisticsServiceCharge(new LogisticsServiceCharge("Fracht",
                                                                                                          new BigDecimal("25.00"),
                                                                                                          TestInvoices.VAT_19))
                                                    .addFinancialAdjustment(new FinancialAdjustment("Korrektur Vorperiode",
                                                                                                    new BigDecimal("-10.00")))
                                                    .build())
                       .build();
  }

  @Test
  void totalsAddUpToTheAmountDue() {
    var totals = invoice().totals();
    assertEquals(new BigDecimal("344.70"),
                 totals.taxExclusiveAmount());
    assertEquals(new BigDecimal("395.83"),
                 totals.taxInclusiveAmount());
    assertEquals(new BigDecimal("385.83"),
                 totals.payableAmount());
    String text = text(PANIOLO.toPdf(invoice(),
                                     PdfLayout.of(Locale.GERMAN)));
    // Each row of the totals, in order: every amount is explained by a row above it.
    for (String expected : List.of("""
                                       Summe Positionen 319,70 €
                                       zzgl. Fracht (USt 19 %) 25,00 €
                                       Gesamtbetrag netto 344,70 €
                                       zuzüglich USt 19 % auf 225,00 € 42,75 €
                                       zuzüglich USt 7 % auf 119,70 € 8,38 €
                                       Gesamtbetrag brutto 395,83 €
                                       Korrektur Vorperiode -10,00 €
                                       Zahlbetrag 385,83 €
                                       USt in USD 56,25 $
                                       Umrechnungskurs 1,1 · 27.10.2026
                                       """)) {
      assertTrue(text.contains(expected),
                 () -> expected + " in\n" + text);
    }
  }

  @Test
  void showsTheExtendedInformationInEveryLanguage() {
    var invoice = invoice();
    // Values independent of the language, then the labels of each language.
    List<String> values = List.of("Rechnungskopie Projekt A",
                                  "Oktober-Abrechnung",
                                  "ANG-77",
                                  "LS-1",
                                  "EK-9",
                                  "RS-REF-3",
                                  "4711-B",
                                  "FCA · Hamburg Hafen",
                                  "Vertrieb Nord GmbH",
                                  "Einkaufsbüro West",
                                  "Steuer AG",
                                  "Endnutzer KG",
                                  "Zentrallager",
                                  "Baustelle 9",
                                  "Abrechnungsstelle",
                                  "Rechnungsprüfung",
                                  "Zahlstelle AG",
                                  "Kunde GmbH Konto",
                                  "BYLADEM1001",
                                  "P-100",
                                  "HERST-1",
                                  "Pro M-2",
                                  "Acme Werke",
                                  "B-7",
                                  "S-123",
                                  "Farbe: blau",
                                  "Handbuch",
                                  "EXW",
                                  "Unterverkäufer GmbH",
                                  "Lager Süd",
                                  "AU-5",
                                  "V-8",
                                  "WE-3",
                                  "R-0",
                                  "EK-10",
                                  "Z-1",
                                  "OBJ-2",
                                  "KST-2",
                                  "Mengenrabatt",
                                  "Baugruppe Bücher");
    Map<Locale, List<String>> labels = Map.of(Locale.GERMAN,
                                              List.of("Testdokument, nicht zahlen · Kopie",
                                                      "Dokumentname",
                                                      "Gültig ab",
                                                      "02.10.2026",
                                                      "Bestellung des Endkunden",
                                                      "Straße (3)",
                                                      "Handelsvertreter",
                                                      "Zahlungspflichtiger",
                                                      "Umrechnungskurs",
                                                      "Zwischensumme",
                                                      "Produkt-ID",
                                                      "Hersteller",
                                                      "1 Stk. kostenlos",
                                                      "USt 38,00",
                                                      "Brutto 238,00"),
                                              Locale.ENGLISH,
                                              List.of("Test document, not for payment · Copy",
                                                      "Document name",
                                                      "Effective date",
                                                      "Ultimate customer order",
                                                      "Road (3)",
                                                      "Sales agent",
                                                      "Payer",
                                                      "Exchange rate",
                                                      "Subtotal",
                                                      "Product ID",
                                                      "Manufacturer",
                                                      "VAT €38.00",
                                                      "total with VAT €238.00"),
                                              Locale.FRENCH,
                                              List.of("Document de test, ne pas payer · Copie",
                                                      "Nom du document",
                                                      "Date d’effet",
                                                      "Commande du client final",
                                                      "Routier (3)",
                                                      "Agent commercial",
                                                      "Payeur",
                                                      "Taux de change",
                                                      "Sous-total",
                                                      "ID produit",
                                                      "Fabricant",
                                                      "TVA 38,00",
                                                      "TTC 238,00"));
    labels.forEach((locale, texts) -> {
      byte[] pdf = PANIOLO.toPdf(invoice,
                                 PdfLayout.of(locale));
      assertEquals(List.of(),
                   PdfLayoutTest.pdfaFailures(pdf));
      String text = text(pdf)
          // Wrapped details: compare without line breaks.
          .replace('\n',
                   ' ')
          .replace("  ",
                   " ");
      for (String expected : new ArrayList<String>() {{
        addAll(values);
        addAll(texts);
      }}) {
        assertTrue(text.contains(expected),
                   () -> locale + ": " + expected + " in\n" + text);
      }
    });
  }

  @Test
  void showsNothingOfItWithoutExtendedInformation() {
    String text = text(PANIOLO.toPdf(TestInvoices.simple("X-2")
                                                 .processControl(ProcessControl.facturXExtended())
                                                 .build(),
                                     PdfLayout.of(Locale.ENGLISH)));
    for (String absent : List.of("Status",
                                 "Document name",
                                 "Effective date",
                                 "Transport mode",
                                 "Delivery terms",
                                 "Sales agent",
                                 "Exchange rate",
                                 "Subtotal",
                                 "Financial adjustment",
                                 "Product ID")) {
      assertFalse(text.contains(absent),
                  () -> absent + " in\n" + text);
    }
  }

  /**
   * Sixty detailed lines span several pages; every line keeps its details.
   */
  @Test
  void keepsTheDetailsOfEveryLineAcrossPages() {
    var lines = new ArrayList<InvoiceLine>();
    for (int i = 1; i <= 60; i++) {
      lines.add(detailedLine(String.valueOf(i),
                             "QX" + i + "Z"));
    }
    byte[] pdf = PANIOLO.toPdf(TestInvoices.simple("X-3")
                                           .processControl(ProcessControl.facturXExtended())
                                           .lines(lines)
                                           .build(),
                               PdfLayout.of(Locale.GERMAN));
    assertTrue(pages(pdf) > 3,
               () -> "pages: " + pages(pdf));
    String text = text(pdf);
    for (int i = 1; i <= 60; i++) {
      // The product identifier is in the details, the name in the first row of the line.
      assertTrue(text.contains("QX" + i + "Z") && text.contains("Maschine QX" + i + "Z"),
                 "line " + i);
    }
  }

  /**
   * Third party payments of the XRechnung extension are part of the amount due and get a row.
   */
  @Test
  void showsThirdPartyPaymentsInTheTotals() {
    var invoice = TestInvoices.simple("X-4")
                              .processControl(ProcessControl.xrechnungExtension())
                              .addThirdPartyPayment(new ThirdPartyPayment("Kurtaxe",
                                                                          new BigDecimal("12.00"),
                                                                          "Gemeinde Musterbad"))
                              .build();
    String text = text(PANIOLO.toPdf(invoice,
                                     PdfLayout.of(Locale.ENGLISH))).replace('\n',
                                                                            ' ');
    // Lines 319.70 net, VAT 38.00 + 8.38: gross 366.08, plus 12.00 paid on behalf of a third party.
    // The label wraps; its second line follows the amount.
    for (String expected : List.of("Paid on behalf of a third party: Kurtaxe · €12.00 Gemeinde Musterbad",
                                   "Amount due €378.08")) {
      assertTrue(text.contains(expected),
                 () -> expected + " in\n" + text);
    }
  }
}

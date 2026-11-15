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
import io.github.nalukit.paniolo.model.common.value.DutyTaxFeeCategory;
import io.github.nalukit.paniolo.model.common.value.PaymentMeansCode;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.verapdf.gf.foundry.VeraGreenfieldFoundryProvider;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Invoice, order and delivery note with French labels: no English fallback, correct accents.
 */
class FrenchLayoutTest {
  private static final Paniolo   PANIOLO = Paniolo.create();
  private static final PdfLayout FRENCH  = PdfLayout.of(Locale.FRENCH);

  @BeforeAll
  static void verapdf() {
    VeraGreenfieldFoundryProvider.initialise();
  }

  private static Properties labels(String suffix) {
    var properties = new Properties();
    try (InputStream input = FrenchLayoutTest.class.getResourceAsStream("/io/github/nalukit/paniolo/pdflayout/Labels" + suffix + ".properties")) {
      properties.load(new java.io.InputStreamReader(input,
                                                    java.nio.charset.StandardCharsets.UTF_8));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return properties;
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

  /**
   * English texts that must not appear in a French document: the English labels of more than one
   * word without placeholders, and the English code list texts of common VAT categories and payment
   * means.
   */
  private static Set<String> english() {
    var english = labels("");
    var french  = labels("_fr");
    var result  = new TreeSet<String>();
    for (String key : english.stringPropertyNames()) {
      String value = english.getProperty(key);
      if (value.contains(" ") && !value.contains("{") && value.length() >= 8 && !value.equals(french.getProperty(key))) {
        result.add(value);
      }
    }
    for (var category : List.of(DutyTaxFeeCategory.S,
                                DutyTaxFeeCategory.Z,
                                DutyTaxFeeCategory.E,
                                DutyTaxFeeCategory.AE,
                                DutyTaxFeeCategory.K)) {
      result.add(category.getDescription(Locale.ENGLISH));
    }
    for (var means : List.of(PaymentMeansCode.CODE_30,
                             PaymentMeansCode.CODE_58,
                             PaymentMeansCode.CODE_59)) {
      result.add(means.getDescription(Locale.ENGLISH));
    }
    return result;
  }

  private static long questionMarks(String text) {
    return text.chars()
               .filter(c -> c == '?')
               .count();
  }

  private static void assertFrench(String name,
                                   byte[] pdf,
                                   byte[] german,
                                   List<String> expected) {
    assertEquals(List.of(),
                 PdfLayoutTest.pdfaFailures(pdf),
                 name);
    String text = text(pdf);
    for (String label : expected) {
      assertTrue(text.contains(label),
                 () -> name + ": " + label + " in\n" + text);
    }
    var found = new ArrayList<String>();
    for (String english : english()) {
      if (text.contains(english)) {
        found.add(english);
      }
    }
    assertEquals(List.of(),
                 found,
                 () -> name + ":\n" + text);
    // A glyph missing in the font is printed as '?'; the French labels add none to the document data.
    assertEquals(questionMarks(text(german)),
                 questionMarks(text),
                 () -> name + ":\n" + text);
  }

  @Test
  void frenchLabelsCoverEveryKey() {
    var english = labels("").stringPropertyNames();
    var french  = new HashSet<>(labels("_fr").stringPropertyNames());
    var missing = new TreeSet<>(english);
    missing.removeAll(french);
    assertEquals(Set.of(),
                 missing);
  }

  @Test
  void rendersAnInvoiceInFrench() {
    var invoice = TestInvoices.rich(12);
    assertFrench("invoice",
                 PANIOLO.toPdf(invoice,
                               FRENCH),
                 PANIOLO.toPdf(invoice),
                 List.of("Facture R-2026-0815",
                         "Désignation Qté Unité Prix unitaire TVA Montant",
                         "Total HT",
                         "Total TTC",
                         "Net à payer",
                         "Échéance 10 nov. 2026",
                         "Page 1 sur",
                         "N° fiscal",
                         "Allemagne",
                         "100,00 €"));
  }

  @Test
  void rendersAnOrderInFrench()
      throws IOException {
    try (InputStream input = FrenchLayoutTest.class.getResourceAsStream("/orderx/ORDER-X_EX01_ORDER_FULL_DATA-COMFORT.xml")) {
      var order = PANIOLO.readOrder(input.readAllBytes(),
                                    true);
      assertFrench("order",
                   PANIOLO.toPdf(order,
                                 FRENCH),
                   PANIOLO.toPdf(order),
                   List.of("Bon de commande",
                           "N° de commande",
                           "Date de commande",
                           "Désignation"));
    }
  }

  @Test
  void rendersADeliveryNoteInFrench()
      throws IOException {
    for (String name : List.of("despatch-advice-ubl.xml",
                               "despatch-advice-cida.xml")) {
      try (InputStream input = FrenchLayoutTest.class.getResourceAsStream("/samples/" + name)) {
        var advice = PANIOLO.readDespatchAdvice(input.readAllBytes(),
                                                true);
        assertFrench(name,
                     PANIOLO.toPdf(advice,
                                   FRENCH),
                     PANIOLO.toPdf(advice),
                     List.of("Bon de livraison",
                             "Livré",
                             "Accusé de réception",
                             "Marchandise reçue complète et en bon état."));
      }
    }
  }

  @Test
  void otherLanguagesStillFallBackToEnglish() {
    String text = text(PANIOLO.toPdf(TestInvoices.rich(1),
                                     PdfLayout.of(Locale.forLanguageTag("es"))));
    assertTrue(text.contains("Amount due"),
               text);
  }
}

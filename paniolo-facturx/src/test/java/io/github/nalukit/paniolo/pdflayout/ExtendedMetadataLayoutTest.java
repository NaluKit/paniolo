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
import io.github.nalukit.paniolo.model.common.InvoiceLine;
import io.github.nalukit.paniolo.model.common.InvoiceNote;
import io.github.nalukit.paniolo.model.common.ProcessControl;
import io.github.nalukit.paniolo.model.common.References;
import io.github.nalukit.paniolo.model.extended.ExtendedInformation;
import io.github.nalukit.paniolo.model.extended.LineExtension;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The remaining EXTENDED metadata in the PDF (task V05). Codes that only qualify another value are
 * not shown: note subject codes, a note content code next to the note text, accounting type codes,
 * the line status code, and the document language. A note stated by its content code only shows
 * that code, so it does not vanish.
 */
class ExtendedMetadataLayoutTest {
  private static final Paniolo PANIOLO = Paniolo.create();

  private static Invoice invoice() {
    return TestInvoices.simple("META-1")
                       .processControl(ProcessControl.facturXExtended())
                       .references(References.create()
                                             .buyerAccountingReference("KST-1")
                                             .build())
                       .addNote(new InvoiceNote("AAI",
                                                null,
                                                "ZQ7"))
                       .addNote(new InvoiceNote("REG",
                                                "Hinweistext",
                                                "ZQ8"))
                       .extended(ExtendedInformation.create()
                                                    .languageId("deu")
                                                    .buyerAccountingTypeCode("4")
                                                    .build())
                       .addLine(InvoiceLine.create()
                                           .id("90")
                                           .name("Wartung")
                                           .quantity(BigDecimal.ONE)
                                           .unitCode("C62")
                                           .netUnitPrice(new BigDecimal("50.00"))
                                           .tax(TestInvoices.VAT_19)
                                           .buyerAccountingReference("KST-2")
                                           .extended(LineExtension.create()
                                                                  .noteContentCode("ZQ9")
                                                                  .noteSubjectCode("AAI")
                                                                  .statusCode("74")
                                                                  .accountingTypeCode("7")
                                                                  .build())
                                           .build())
                       .build();
  }

  private static String text(byte[] pdf) {
    try (var document = Loader.loadPDF(pdf)) {
      return new PDFTextStripper().getText(document)
                                  .replace(' ',
                                           ' ')
                                  .replace(' ',
                                           ' ');
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @ParameterizedTest
  @CsvSource({ "de,Notizcode",
               "en,Note code",
               "fr,Code de note" })
  void showsANoteThatHasACodeOnly(String language,
                                  String label) {
    String text = text(PANIOLO.toPdf(invoice(),
                                     PdfLayout.of(Locale.forLanguageTag(language))));
    // Document note and line note stated by their content code only.
    for (String shown : List.of(label + " ZQ7",
                                label + " ZQ9",
                                "Hinweistext",
                                "KST-1",
                                "KST-2")) {
      assertTrue(text.contains(shown),
                 () -> shown + " missing in\n" + text);
    }
    // Codes that only qualify another value.
    for (String absent : List.of("ZQ8",
                                 "AAI",
                                 "REG",
                                 "deu",
                                 " 74")) {
      assertFalse(text.contains(absent),
                  () -> absent + " in\n" + text);
    }
  }
}

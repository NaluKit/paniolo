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
import io.github.nalukit.paniolo.model.despatch.DespatchAdvice;
import io.github.nalukit.paniolo.model.order.Order;
import io.github.nalukit.paniolo.model.order.OrderTypeCode;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.verapdf.gf.foundry.VeraGreenfieldFoundryProvider;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderAndDespatchLayoutTest {
  private static final Paniolo PANIOLO = Paniolo.create();
  private static final Path    OUT     = Path.of("target",
                                                 "rendered");

  @BeforeAll
  static void setUp()
      throws IOException {
    VeraGreenfieldFoundryProvider.initialise();
    Files.createDirectories(OUT);
  }

  private static String firstLine(byte[] pdf) {
    return text(pdf).lines()
                    .findFirst()
                    .orElse("");
  }

  private static String text(byte[] pdf) {
    try (var document = Loader.loadPDF(pdf)) {
      return new PDFTextStripper().getText(document)
                                  .replace(' ',
                                           ' ')
                                  .replace(' ',
                                           ' ');
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }



  @Nested
  @DisplayName("orders")
  class Orders {
    @Test
    @DisplayName("renders all official Order-X COMFORT examples as PDF/A-3b")
    void rendersOfficialExamples()
        throws IOException, URISyntaxException {
      Path directory = Path.of(OrderAndDespatchLayoutTest.class.getResource("/orderx")
                                                               .toURI());
      try (var files = Files.list(directory)) {
        for (Path example : files.filter(f -> f.toString()
                                               .endsWith(".xml"))
                                 .sorted()
                                 .toList()) {
          Order order = PANIOLO.readOrder(Files.readAllBytes(example),
                                          true);
          byte[] pdf = PANIOLO.toPdf(order);
          String name = example.getFileName()
                               .toString()
                               .replace(".xml",
                                        "");
          Files.write(OUT.resolve(name + ".pdf"),
                      pdf);
          assertEquals(List.of(),
                       PdfLayoutTest.pdfaFailures(pdf),
                       name);
          String text = text(pdf);
          assertTrue(text.contains(order.number()),
                     name);
          String title = switch (order.typeCode()) {
            case ORDER -> "Bestellung";
            case ORDER_CHANGE -> "Bestelländerung";
            case ORDER_RESPONSE -> "Auftragsbestätigung";
          };
          assertTrue(text.contains(title + " " + order.number()),
                     name + ": " + text);
        }
      }
    }

    @Test
    @DisplayName("shows the buyer as sender of an order and the seller as sender of a response")
    void choosesSender()
        throws IOException {
      Order order    = read("ORDER-X_EX01_ORDER_FULL_DATA-COMFORT.xml");
      Order response = read("ORDER-X_EX10_RESPONSE_ADDED_SUBSTITUTED-COMFORT.xml");
      assertEquals(OrderTypeCode.ORDER_RESPONSE,
                   response.typeCode());
      assertTrue(firstLine(PANIOLO.toPdf(order)).contains(order.buyer()
                                                               .name()));
      assertTrue(firstLine(PANIOLO.toPdf(response)).contains(response.seller()
                                                                     .name()));
      String text = text(PANIOLO.toPdf(response));
      assertTrue(text.contains("Bestellt"),
                 text);
      assertTrue(text.contains("Bestätigt"),
                 text);
      assertTrue(text.contains("Status:"),
                 text);
    }

    @Test
    @DisplayName("creates an Order-X PDF without a PDF of its own")
    void createsOrderXPdf()
        throws IOException {
      Order order = read("ORDER-X_EX01_ORDER_FULL_DATA-COMFORT.xml");
      byte[] pdf = PANIOLO.toOrderXPdf(order,
                                       PdfLayout.of(Locale.ENGLISH),
                                       List.of());
      Files.write(OUT.resolve("order-x.pdf"),
                  pdf);
      var result = PANIOLO.validateOrder(pdf);
      assertTrue(result.isValid(),
                 result.messages()
                       .toString());
      assertEquals(order.number(),
                   PANIOLO.readOrder(pdf,
                                     true)
                          .number());
      assertTrue(text(pdf).contains("Purchase order " + order.number()));
    }

    @Test
    @DisplayName("rejects a layout with watermark for an Order-X PDF")
    void rejectsWatermark()
        throws IOException {
      Order order = read("ORDER-X_EX01_ORDER_FULL_DATA-COMFORT.xml");
      assertThrows(IllegalArgumentException.class,
                   () -> PANIOLO.toOrderXPdf(order,
                                             PdfLayout.defaults()
                                                      .withWatermark("PREVIEW"),
                                             List.of()));
      try (var document = Loader.loadPDF(PANIOLO.toPdf(order,
                                                       PdfLayout.defaults()
                                                                .withWatermark("PREVIEW")))) {
        assertEquals("PREVIEW",
                     PdfLayoutTest.rotatedText(document,
                                               1));
      }
    }

    private Order read(String name)
        throws IOException {
      try (InputStream input = OrderAndDespatchLayoutTest.class.getResourceAsStream("/orderx/" + name)) {
        return PANIOLO.readOrder(input.readAllBytes(),
                                 true);
      }
    }
  }



  @Nested
  @DisplayName("despatch advices")
  class DespatchAdvices {
    @Test
    @DisplayName("renders UBL and CIDA despatch advices as delivery notes")
    void rendersDeliveryNotes()
        throws IOException {
      for (String name : List.of("despatch-advice-ubl.xml",
                                 "despatch-advice-cida.xml")) {
        DespatchAdvice advice = read(name);
        byte[]         pdf    = PANIOLO.toPdf(advice);
        Files.write(OUT.resolve(name.replace(".xml",
                                             ".pdf")),
                    pdf);
        assertEquals(List.of(),
                     PdfLayoutTest.pdfaFailures(pdf),
                     name);
        String text = text(pdf);
        assertTrue(text.contains("Lieferschein LS-2026-42"),
                   text);
        assertTrue(text.contains("B-2026-17"),
                   text);
        assertTrue(text.contains("SN-1"),
                   text);
        assertTrue(text.contains("Empfangsbestätigung"),
                   text);
      }
      String ubl = text(PANIOLO.toPdf(read("despatch-advice-ubl.xml")));
      assertTrue(ubl.contains("Ausstehend: Restmenge folgt in KW 47"),
                 ubl);
      assertTrue(ubl.contains("L-2026-11"),
                 ubl);
    }

    @Test
    @DisplayName("creates a CIDA delivery note PDF without a PDF of its own")
    void createsCidaPdf()
        throws IOException {
      DespatchAdvice advice = read("despatch-advice-cida.xml");
      byte[]         pdf    = PANIOLO.toDespatchAdvicePdf(advice);
      Files.write(OUT.resolve("cida.pdf"),
                  pdf);
      assertEquals(List.of(),
                   PdfLayoutTest.pdfaFailures(pdf));
      assertEquals(advice.number(),
                   PANIOLO.readDespatchAdvice(pdf)
                          .number());
    }

    @Test
    @DisplayName("rejects a layout with watermark for a CIDA delivery note PDF")
    void rejectsWatermark()
        throws IOException {
      DespatchAdvice advice = read("despatch-advice-cida.xml");
      assertThrows(IllegalArgumentException.class,
                   () -> PANIOLO.toDespatchAdvicePdf(advice,
                                                     PdfLayout.defaults()
                                                              .withWatermark("PREVIEW"),
                                                     List.of()));
      byte[] pdf = PANIOLO.toPdf(advice,
                                 PdfLayout.defaults()
                                          .withWatermark("ENTWURF"));
      assertEquals(List.of(),
                   PdfLayoutTest.pdfaFailures(pdf));
      try (var document = Loader.loadPDF(pdf)) {
        assertEquals("ENTWURF",
                     PdfLayoutTest.rotatedText(document,
                                               1));
      }
    }

    private DespatchAdvice read(String name)
        throws IOException {
      try (InputStream input = OrderAndDespatchLayoutTest.class.getResourceAsStream("/samples/" + name)) {
        return PANIOLO.readDespatchAdvice(input.readAllBytes(),
                                          true);
      }
    }
  }
}

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
import io.github.nalukit.paniolo.config.FacturXProfile;
import io.github.nalukit.paniolo.config.PdfAttachment;
import io.github.nalukit.paniolo.config.PdfLayout;
import io.github.nalukit.paniolo.model.Invoice;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.contentstream.PDFGraphicsStreamEngine;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.graphics.image.PDImage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.verapdf.gf.foundry.VeraGreenfieldFoundryProvider;
import org.verapdf.pdfa.Foundries;
import org.verapdf.pdfa.flavours.PDFAFlavour;
import org.verapdf.pdfa.results.TestAssertion;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PdfLayoutTest {
  private static final Paniolo PANIOLO = Paniolo.create();
  private static final Path    OUT     = Path.of("target",
                                                 "rendered");

  @BeforeAll
  static void setUp()
      throws IOException {
    VeraGreenfieldFoundryProvider.initialise();
    Files.createDirectories(OUT);
  }

  private static byte[] logo()
      throws IOException {
    var image = new BufferedImage(300,
                                  90,
                                  BufferedImage.TYPE_INT_ARGB);
    var graphics = image.createGraphics();
    graphics.setColor(new Color(0x01244F));
    graphics.fillRoundRect(0,
                           0,
                           300,
                           90,
                           40,
                           40);
    graphics.dispose();
    var png = new ByteArrayOutputStream();
    ImageIO.write(image,
                  "png",
                  png);
    return png.toByteArray();
  }

  private static int images(byte[] pdf)
      throws IOException {
    try (var document = Loader.loadPDF(pdf)) {
      int count = 0;
      var resources = document.getPage(0)
                              .getResources();
      for (var name : resources.getXObjectNames()) {
        if (resources.isImageXObject(name)) {
          count++;
        }
      }
      return count;
    }
  }

  /**
   * The characters of a page that are not written horizontally: the watermark.
   */
  static String rotatedText(PDDocument document,
                            int page)
      throws IOException {
    var rotated = new StringBuilder();
    var stripper = new PDFTextStripper() {
      @Override
      protected void processTextPosition(org.apache.pdfbox.text.TextPosition text) {
        if (Math.abs(text.getTextMatrix()
                         .getShearY()) > 0.1f) {
          rotated.append(text.getUnicode());
        }
      }
    };
    stripper.setStartPage(page);
    stripper.setEndPage(page);
    stripper.getText(document);
    return rotated.toString();
  }

  /**
   * X position of the first image on page 1: the logo.
   */
  private static float logoX(byte[] pdf)
      throws IOException {
    try (var document = Loader.loadPDF(pdf)) {
      var page   = document.getPage(0);
      var engine = new LogoFinder(page);
      engine.processPage(page);
      return engine.x;
    }
  }

  /**
   * X position of the first "Muster GmbH" on page 1: the sender's name in the letterhead.
   */
  private static float senderNameX(byte[] pdf)
      throws IOException {
    try (var document = Loader.loadPDF(pdf)) {
      float[] x = { -1 };
      var stripper = new PDFTextStripper() {
        @Override
        protected void writeString(String text,
                                   List<org.apache.pdfbox.text.TextPosition> positions)
            throws IOException {
          if (x[0] < 0 && text.startsWith("Muster GmbH") && !text.contains("·")) {
            x[0] = positions.get(0)
                            .getXDirAdj();
          }
          super.writeString(text,
                            positions);
        }
      };
      stripper.setStartPage(1);
      stripper.setEndPage(1);
      stripper.getText(document);
      return x[0];
    }
  }

  private static byte[] render(String name,
                               Invoice invoice,
                               PdfLayout layout)
      throws IOException {
    byte[] pdf = PANIOLO.toPdf(invoice,
                               layout);
    Files.write(OUT.resolve(name + ".pdf"),
                pdf);
    return pdf;
  }

  private static byte[] sample(String name)
      throws IOException {
    try (InputStream input = PdfLayoutTest.class.getResourceAsStream("/samples/" + name)) {
      return input.readAllBytes();
    }
  }

  private static String text(byte[] pdf) {
    try (var document = Loader.loadPDF(pdf)) {
      // amounts are formatted with no-break spaces
      return new PDFTextStripper().getText(document)
                                  .replace('\u00A0',
                                           ' ')
                                  .replace('\u202F',
                                           ' ');
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  static List<String> pdfaFailures(byte[] pdf) {
    try (var parser = Foundries.defaultInstance()
                               .createParser(new ByteArrayInputStream(pdf),
                                             PDFAFlavour.PDFA_3_B)) {
      var result = Foundries.defaultInstance()
                            .createValidator(PDFAFlavour.PDFA_3_B,
                                             false)
                            .validate(parser);
      return result.getTestAssertions()
                   .stream()
                   .filter(assertion -> assertion.getStatus() == TestAssertion.Status.FAILED)
                   .map(assertion -> assertion.getRuleId() + ": " + assertion.getMessage())
                   .distinct()
                   .toList();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }



  private static final class LogoFinder
      extends PDFGraphicsStreamEngine {
    private float x = -1;

    private LogoFinder(PDPage page) {
      super(page);
    }

    @Override
    public void drawImage(PDImage image) {
      if (x < 0) {
        x = getGraphicsState().getCurrentTransformationMatrix()
                              .getTranslateX();
      }
    }

    @Override
    public void appendRectangle(Point2D p0,
                                Point2D p1,
                                Point2D p2,
                                Point2D p3) {
    }

    @Override
    public void clip(int windingRule) {
    }

    @Override
    public void moveTo(float x,
                       float y) {
    }

    @Override
    public void lineTo(float x,
                       float y) {
    }

    @Override
    public void curveTo(float x1,
                        float y1,
                        float x2,
                        float y2,
                        float x3,
                        float y3) {
    }

    @Override
    public Point2D getCurrentPoint() {
      return new Point2D.Float();
    }

    @Override
    public void closePath() {
    }

    @Override
    public void endPath() {
    }

    @Override
    public void strokePath() {
    }

    @Override
    public void fillPath(int windingRule) {
    }

    @Override
    public void fillAndStrokePath(int windingRule) {
    }

    @Override
    public void shadingFill(COSName shadingName) {
    }
  }



  @Nested
  @DisplayName("toPdf")
  class ToPdf {
    @Test
    @DisplayName("renders a PDF/A-3b document with the invoice data")
    void rendersPdfA3b()
        throws IOException {
      byte[] pdf = render("simple",
                          TestInvoices.simple("R-2026-001")
                                      .build(),
                          PdfLayout.defaults());
      assertEquals(List.of(),
                   pdfaFailures(pdf));
      String text = text(pdf);
      assertTrue(text.contains("Rechnung R-2026-001"),
                 text);
      assertTrue(text.contains("Muster GmbH"),
                 text);
      assertTrue(text.contains("Kunde GmbH"),
                 text);
      assertTrue(text.contains("04011000-12345-03"),
                 text);
      assertTrue(text.contains("DE89 3704 0044 0532 0130 00"),
                 text);
      // 2 x 100.00 at 19 % and 3 x 39.90 at 7 %
      assertTrue(text.contains("319,70 €"),
                 text);
      assertTrue(text.contains("zuzüglich USt 19 % auf 200,00 €"),
                 text);
      assertTrue(text.contains("Zahlbetrag"),
                 text);
      assertTrue(text.contains("Seite 1 von 1"),
                 text);
    }

    @Test
    @DisplayName("breaks long invoices over pages and repeats the table header")
    void breaksPages()
        throws IOException {
      byte[] pdf = render("rich",
                          TestInvoices.rich(80),
                          PdfLayout.defaults()
                                   .withFooter("Geschäftsführung: Erika Muster · Amtsgericht Berlin-Charlottenburg HRB 12345 B"));
      assertEquals(List.of(),
                   pdfaFailures(pdf));
      try (var document = Loader.loadPDF(pdf)) {
        int pages = document.getNumberOfPages();
        assertTrue(pages >= 3,
                   "pages: " + pages);
        var stripper = new PDFTextStripper();
        for (int p = 1; p <= pages; p++) {
          stripper.setStartPage(p);
          stripper.setEndPage(p);
          String page = stripper.getText(document);
          assertTrue(page.contains("Seite " + p + " von " + pages),
                     page);
          assertTrue(page.contains("Amtsgericht Berlin-Charlottenburg"),
                     page);
          if (p > 1) {
            assertTrue(page.contains("Rechnung R-2026-0815 · Muster GmbH"),
                       page);
          }
        }
      }
      String text = text(pdf);
      for (int i = 3; i < 83; i++) {
        assertTrue(text.contains("Leistung " + i + "\n") || text.contains("Leistung " + i + " "),
                   "line " + i);
      }
      assertTrue(text.contains("abzüglich Treuerabatt"),
                 text);
      assertTrue(text.contains("zuzüglich Versand"),
                 text);
      assertTrue(text.contains("2 % Skonto bei Zahlung innerhalb von 10 Tagen"),
                 text);
      assertTrue(text.contains("abzüglich Mengenrabatt"),
                 text);
      // characters without glyph in Liberation Sans are replaced
      assertTrue(text.contains("äöüß"),
                 text);
    }

    @Test
    @DisplayName("prints the watermark semi-transparent across every page")
    void rendersWatermark()
        throws IOException {
      byte[] pdf = render("watermark",
                          TestInvoices.rich(80),
                          PdfLayout.defaults()
                                   .withWatermark(" PREVIEW "));
      assertEquals(List.of(),
                   pdfaFailures(pdf));
      try (var document = Loader.loadPDF(pdf)) {
        int pages = document.getNumberOfPages();
        assertTrue(pages >= 3,
                   "pages: " + pages);
        for (int p = 1; p <= pages; p++) {
          assertEquals("PREVIEW",
                       rotatedText(document,
                                   p));
          var resources = document.getPage(p - 1)
                                  .getResources();
          boolean transparent = false;
          for (var name : resources.getExtGStateNames()) {
            Float alpha = resources.getExtGState(name)
                                   .getNonStrokingAlphaConstant();
            transparent |= alpha != null && alpha < 1;
          }
          assertTrue(transparent,
                     "page " + p);
        }
        assertTrue(document.getDocumentCatalog()
                           .getNames() == null ||
                   document.getDocumentCatalog()
                           .getNames()
                           .getEmbeddedFiles() == null);
      }
    }

    @Test
    @DisplayName("renders no watermark by default")
    void rendersNoWatermarkByDefault()
        throws IOException {
      byte[] pdf = PANIOLO.toPdf(TestInvoices.simple("R-2026-006")
                                             .build());
      try (var document = Loader.loadPDF(pdf)) {
        assertEquals("",
                     rotatedText(document,
                                 1));
      }
    }

    @Test
    @DisplayName("renders English labels")
    void rendersEnglish()
        throws IOException {
      String text = text(render("english",
                                TestInvoices.simple("R-2026-002")
                                            .build(),
                                PdfLayout.of(Locale.ENGLISH)));
      assertTrue(text.contains("Invoice R-2026-002"),
                 text);
      assertTrue(text.contains("Amount due"),
                 text);
      assertTrue(text.contains("€319.70"),
                 text);
    }

    @Test
    @DisplayName("renders invoices without lines (Factur-X MINIMUM)")
    void rendersWithoutLines()
        throws IOException {
      byte[] pdf = render("minimum",
                          TestInvoices.minimum(),
                          PdfLayout.defaults());
      assertEquals(List.of(),
                   pdfaFailures(pdf));
      String text = text(pdf);
      assertTrue(text.contains("238,00 €"),
                 text);
    }

    @Test
    @DisplayName("renders sub-lines indented below their parent")
    void rendersSubLines()
        throws IOException {
      var parent = io.github.nalukit.paniolo.model.common.InvoiceLine.create()
                                                                     .id("1")
                                                                     .name("Montagepaket")
                                                                     .quantity(java.math.BigDecimal.ONE)
                                                                     .unitCode("C62")
                                                                     .netUnitPrice(new java.math.BigDecimal("150.00"))
                                                                     .tax(TestInvoices.VAT_19)
                                                                     .addSubLine(io.github.nalukit.paniolo.model.common.InvoiceLine.create()
                                                                                                                                   .id("1.1")
                                                                                                                                   .name("Anfahrt")
                                                                                                                                   .quantity(java.math.BigDecimal.ONE)
                                                                                                                                   .unitCode("LS")
                                                                                                                                   .netUnitPrice(new java.math.BigDecimal("50.00"))
                                                                                                                                   .tax(TestInvoices.VAT_19)
                                                                                                                                   .build())
                                                                     .addSubLine(io.github.nalukit.paniolo.model.common.InvoiceLine.create()
                                                                                                                                   .id("1.2")
                                                                                                                                   .name("Montage")
                                                                                                                                   .quantity(new java.math.BigDecimal("2"))
                                                                                                                                   .unitCode("HUR")
                                                                                                                                   .netUnitPrice(new java.math.BigDecimal("50.00"))
                                                                                                                                   .tax(TestInvoices.VAT_19)
                                                                                                                                   .build())
                                                                     .build();
      var invoice = TestInvoices.simple("R-2026-006")
                                .processControl(io.github.nalukit.paniolo.model.common.ProcessControl.xrechnungExtension())
                                .lines(List.of(parent))
                                .build();
      byte[] pdf = render("sub-lines",
                          invoice,
                          PdfLayout.defaults());
      assertEquals(List.of(),
                   pdfaFailures(pdf));
      String text = text(pdf);
      assertTrue(text.contains("1.1 Anfahrt"),
                 text);
      assertTrue(text.contains("1.2 Montage"),
                 text);
      assertTrue(text.contains("pauschal"),
                 text);
    }

    @Test
    @DisplayName("shows the logo only when switched on, on the left or right")
    void rendersLogo()
        throws IOException {
      byte[] png = logo();
      var invoice = TestInvoices.simple("R-2026-003")
                                .build();

      byte[] hidden = render("logo-hidden",
                             invoice,
                             PdfLayout.defaults()
                                      .withLogo(png));
      assertEquals(0,
                   images(hidden));

      byte[] left = render("logo",
                           invoice,
                           PdfLayout.defaults()
                                    .withLogo(png)
                                    .withShowLogo(true)
                                    .withAccentColor(0x01244F));
      assertEquals(List.of(),
                   pdfaFailures(left));
      assertEquals(1,
                   images(left));
      assertTrue(senderNameX(left) > 300,
                 "sender address on the right");

      byte[] right = render("logo-right",
                            invoice,
                            PdfLayout.defaults()
                                     .withLogo(png)
                                     .withShowLogo(true)
                                     .withLogoPosition(PdfLayout.LogoPosition.RIGHT));
      assertEquals(List.of(),
                   pdfaFailures(right));
      assertEquals(1,
                   images(right));
      assertTrue(senderNameX(right) < 100,
                 "sender address on the left");
    }

    @Test
    @DisplayName("hides the sender's address in the letterhead")
    void hidesSenderAddress()
        throws IOException {
      var invoice = TestInvoices.simple("R-2026-007")
                                .build();
      List<String> shown = text(render("sender-address",
                                       invoice,
                                       PdfLayout.defaults())).lines()
                                                             .toList();
      List<String> hidden = text(render("no-sender-address",
                                        invoice,
                                        PdfLayout.defaults()
                                                 .withShowSenderAddress(false))).lines()
                                                                                .toList();
      assertTrue(shown.contains("+49 30 123456"),
                 shown.toString());
      assertTrue(!hidden.contains("+49 30 123456"),
                 hidden.toString());
      // sender line in the window and footer remain
      assertTrue(hidden.contains("Muster GmbH · Musterstraße 1 · 10115 Berlin"),
                 hidden.toString());
      assertTrue(hidden.stream()
                       .anyMatch(line -> line.contains("USt-IdNr. DE123456788")),
                 hidden.toString());
    }

    @Test
    @DisplayName("shows only the logo, on the left or right")
    void rendersOnlyLogo()
        throws IOException {
      byte[] png = logo();
      var invoice = TestInvoices.simple("R-2026-008")
                                .build();
      for (var position : PdfLayout.LogoPosition.values()) {
        byte[] pdf = render("logo-only-" +
                            position.name()
                                    .toLowerCase(Locale.ROOT),
                            invoice,
                            PdfLayout.defaults()
                                     .withShowSenderAddress(false)
                                     .withLogo(png)
                                     .withShowLogo(true)
                                     .withLogoPosition(position));
        assertEquals(List.of(),
                     pdfaFailures(pdf));
        assertEquals(1,
                     images(pdf));
        assertTrue(!text(pdf).lines()
                             .toList()
                             .contains("+49 30 123456"),
                   position.name());
        float x = logoX(pdf);
        assertTrue(position == PdfLayout.LogoPosition.LEFT ?
                   x < 100 :
                   x > 300,
                   position + ": logo at " + x);
      }
    }

    @Test
    @DisplayName("renders received documents")
    void rendersReceivedDocuments()
        throws IOException {
      for (String name : List.of("invoice-cii.xml",
                                 "zugferd1-comfort.xml")) {
        Invoice invoice = PANIOLO.read(sample(name));
        byte[] pdf = render(name.replace(".xml",
                                         ""),
                            invoice,
                            PdfLayout.defaults());
        assertEquals(List.of(),
                     pdfaFailures(pdf),
                     name);
        assertTrue(text(pdf).contains(invoice.number()),
                   name);
      }
    }

    @Test
    @DisplayName("rejects unreadable logos and fonts")
    void rejectsBrokenResources() {
      var invoice = TestInvoices.simple("R-1")
                                .build();
      assertThrows(IllegalArgumentException.class,
                   () -> PANIOLO.toPdf(invoice,
                                       PdfLayout.defaults()
                                                .withLogo("no image".getBytes(StandardCharsets.UTF_8))
                                                .withShowLogo(true)));
      assertThrows(IllegalArgumentException.class,
                   () -> PANIOLO.toPdf(invoice,
                                       PdfLayout.defaults()
                                                .withFonts("no font".getBytes(StandardCharsets.UTF_8),
                                                           null)));
    }
  }



  @Nested
  @DisplayName("toFacturX")
  class ToFacturX {
    @Test
    @DisplayName("creates a valid Factur-X invoice without a PDF of its own")
    void createsFacturX()
        throws IOException {
      Invoice invoice = TestInvoices.simple("R-2026-004")
                                    .build();
      byte[] pdf = PANIOLO.toFacturX(invoice,
                                     FacturXProfile.XRECHNUNG,
                                     PdfLayout.defaults(),
                                     List.of(PdfAttachment.of("note.txt",
                                                              "Stundenzettel".getBytes(StandardCharsets.UTF_8),
                                                              "text/plain")));
      Files.write(OUT.resolve("facturx.pdf"),
                  pdf);
      var result = PANIOLO.validate(pdf);
      assertTrue(result.isValid(),
                 result.messages()
                       .toString());
      assertEquals(invoice.totals()
                          .payableAmount(),
                   PANIOLO.read(pdf)
                          .totals()
                          .payableAmount());
      assertEquals(1,
                   PANIOLO.attachments(pdf)
                          .size());
      assertTrue(text(pdf).contains("Rechnung R-2026-004"));
    }

    @Test
    @DisplayName("rejects a layout with watermark")
    void rejectsWatermark() {
      Invoice invoice = TestInvoices.simple("R-2026-007")
                                    .build();
      var exception = assertThrows(IllegalArgumentException.class,
                                   () -> PANIOLO.toFacturX(invoice,
                                                           FacturXProfile.XRECHNUNG,
                                                           PdfLayout.defaults()
                                                                    .withWatermark("PREVIEW"),
                                                           List.of()));
      assertTrue(exception.getMessage()
                          .contains("watermark"),
                 exception.getMessage());
    }

    @Test
    @DisplayName("checks the profile before rendering")
    void checksProfile() {
      Invoice invoice = TestInvoices.simple("R-2026-005")
                                    .build();
      assertThrows(IllegalArgumentException.class,
                   () -> PANIOLO.toFacturX(invoice,
                                           FacturXProfile.EN16931));
    }
  }



  @Nested
  @DisplayName("PdfLayout")
  class Layout {
    @Test
    @DisplayName("copies byte arrays and compares by value")
    void isImmutable() {
      byte[] logo = { 1,
                      2,
                      3 };
      var layout = PdfLayout.defaults()
                            .withLogo(logo);
      logo[0] = 9;
      assertEquals(1,
                   layout.logo()[0]);
      assertEquals(layout,
                   PdfLayout.defaults()
                            .withLogo(new byte[] { 1,
                                                   2,
                                                   3 }));
    }

    @Test
    @DisplayName("rejects invalid settings")
    void rejectsInvalid() {
      assertThrows(IllegalArgumentException.class,
                   () -> PdfLayout.defaults()
                                  .withAccentColor(0x1000000));
      assertThrows(IllegalArgumentException.class,
                   () -> new PdfLayout(Locale.GERMAN,
                                       true,
                                       null,
                                       false,
                                       PdfLayout.LogoPosition.LEFT,
                                       null,
                                       new byte[] { 1 },
                                       0,
                                       null,
                                       null));
    }

    @Test
    @DisplayName("strips the watermark, treats a blank one as none, and compares it")
    void normalizesWatermark() {
      assertEquals("PREVIEW",
                   PdfLayout.defaults()
                            .withWatermark("  PREVIEW\n")
                            .watermark());
      assertNull(PdfLayout.defaults()
                          .withWatermark(" ")
                          .watermark());
      assertEquals(PdfLayout.defaults(),
                   PdfLayout.defaults()
                            .withWatermark(""));
      assertNotEquals(PdfLayout.defaults(),
                      PdfLayout.defaults()
                               .withWatermark("PREVIEW"));
      assertEquals("PREVIEW",
                   PdfLayout.defaults()
                            .withWatermark("PREVIEW")
                            .withFooter("Footer")
                            .withAccentColor(0x01244F)
                            .watermark());
    }
  }
}

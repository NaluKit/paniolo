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

import io.github.nalukit.paniolo.config.PdfLayout;
import io.github.nalukit.paniolo.model.common.AdditionalSupportingDocument;
import io.github.nalukit.paniolo.model.common.Address;
import io.github.nalukit.paniolo.model.common.ItemAttribute;
import io.github.nalukit.paniolo.model.common.Party;
import io.github.nalukit.paniolo.model.common.value.CurrencyCode;
import io.github.nalukit.paniolo.pdflayout.Canvas.Cell;
import io.github.nalukit.paniolo.pdflayout.Canvas.Font;
import io.github.nalukit.paniolo.pdflayout.Canvas.Row;
import io.github.nalukit.paniolo.pdflayout.Canvas.Style;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.apache.pdfbox.util.Matrix;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static io.github.nalukit.paniolo.pdflayout.Canvas.MM;
import static io.github.nalukit.paniolo.pdflayout.Canvas.PAGE;

/**
 * The parts all business documents share, on A4 pages after DIN 5008 form B: letterhead of the
 * sender, sender line and recipient in the window, document data on the right, title, sections,
 * tables, totals, and the sender's details in the footer of every page. Subclasses add the content
 * of their document type. One instance renders one document.
 */
abstract class DocumentRenderer {
  /**
   * Liberation Sans (SIL Open Font License), packaged with PDFBox.
   */
  static final String DEFAULT_FONT = "/org/apache/pdfbox/resources/ttf/LiberationSans-Regular.ttf";

  static final int TEXT      = 0x1B1F24;
  static final int MUTED     = 0x5B6573;
  static final int LIGHT     = 0xD5DAE1;
  static final int WATERMARK = 0x8A94A3;

  final PdfLayout layout;
  final Texts     t;
  final int       accent;

  Canvas canvas;
  Style  body;
  Style  bodyBold;
  Style  small;
  Style  tiny;
  Style  label;
  Style  columnHead;
  Style  heading;
  Style  title;
  Style  total;
  Style  senderName;

  DocumentRenderer(PdfLayout layout) {
    this.layout = layout;
    this.t      = new Texts(layout.locale());
    this.accent = layout.accentColor();
  }

  private static PDType0Font font(PDDocument document,
                                  byte[] data,
                                  String name) {
    try {
      return PDType0Font.load(document,
                              new ByteArrayInputStream(data));
    } catch (IOException | RuntimeException e) {
      throw new IllegalArgumentException("The " + name + " font of the layout is no readable TrueType font",
                                         e);
    }
  }

  static boolean sameCountry(Address a,
                             Address b) {
    return a != null && b != null && a.country() != null && Objects.equals(a.country(),
                                                                           b.country());
  }

  static boolean nonZero(BigDecimal value) {
    return value != null && value.signum() != 0;
  }

  static void add(List<String> lines,
                  String value) {
    if (value != null && !value.isBlank()) {
      lines.add(value);
    }
  }

  static String join(String separator,
                     String... parts) {
    var values = new ArrayList<String>();
    for (String part : parts) {
      add(values,
          part);
    }
    return values.isEmpty() ?
           null :
           String.join(separator,
                       values);
  }

  /**
   * The party whose letterhead and footer the document carries.
   */
  abstract Party sender();

  /**
   * The party in the address window.
   */
  abstract Party recipient();

  // -- fonts --------------------------------------------------------------------------------------

  /**
   * Title, e.g. "Invoice R-1".
   */
  abstract String documentTitle();

  /**
   * Label and value rows next to the window.
   */
  abstract List<String[]> facts();

  // -- page 1 -------------------------------------------------------------------------------------

  /**
   * Everything below the title.
   */
  abstract void content(PDDocument document);

  /**
   * Currency of the amounts, or null.
   */
  abstract CurrencyCode currency();

  byte[] render() {
    try (var document = new PDDocument()) {
      fonts(document);
      float footerHeight = footerHeight();
      canvas = new Canvas(document,
                          25 * MM,
                          PAGE.getWidth() - 20 * MM,
                          PAGE.getHeight() - 15 * MM,
                          12 * MM + footerHeight + 5 * MM);
      canvas.continuation(this::continuationHeader);
      canvas.newPage();
      letterhead(document);
      if (addressWindow()) {
        addressAndFacts();
        heading();
      } else {
        heading();
        topFacts();
      }
      content(document);
      canvas.close();
      footers(document);
      watermarks(document);
      Party recipient = recipient();
      PdfA.finish(document,
                  documentTitle(),
                  sender() == null ?
                  null :
                  sender().name(),
                  documentTitle() +
                  (recipient == null || recipient.name() == null ?
                   "" :
                   " – " + recipient.name()),
                  layout.locale());
      var out = new ByteArrayOutputStream(64 * 1024);
      document.save(out);
      return out.toByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot render the document as PDF",
                                     e);
    }
  }

  private void fonts(PDDocument document)
      throws IOException {
    PDType0Font regular;
    PDType0Font bold = null;
    if (layout.regularFont() == null) {
      try (InputStream input = PDDocument.class.getResourceAsStream(DEFAULT_FONT)) {
        if (input == null) {
          throw new IllegalStateException("Font not found: " + DEFAULT_FONT);
        }
        regular = PDType0Font.load(document,
                                   input);
      }
    } else {
      regular = font(document,
                     layout.regularFont(),
                     "regular");
      if (layout.boldFont() != null) {
        bold = font(document,
                    layout.boldFont(),
                    "bold");
      }
    }
    var normal = new Font(regular,
                          false);
    var strong = bold == null ?
                 new Font(regular,
                          true) :
                 new Font(bold,
                          false);
    body       = new Style(normal,
                           9f,
                           TEXT);
    bodyBold   = new Style(strong,
                           9f,
                           TEXT);
    small      = new Style(normal,
                           7.8f,
                           MUTED);
    tiny       = new Style(normal,
                           6.8f,
                           MUTED);
    label      = new Style(normal,
                           7.6f,
                           MUTED);
    columnHead = new Style(strong,
                           7.2f,
                           MUTED);
    heading    = new Style(strong,
                           9.5f,
                           accent);
    title      = new Style(strong,
                           15f,
                           TEXT);
    total      = new Style(strong,
                           10.5f,
                           TEXT);
    senderName = new Style(strong,
                           11f,
                           TEXT);
  }

  // -- building blocks of the content -------------------------------------------------------------

  /**
   * Logo and sender's address at the top of page 1: the logo on its side, the address on the other
   * side, aligned to the outer margin. Either may be switched off in the layout.
   */
  private void letterhead(PDDocument document) {
    float   top       = canvas.top;
    float   bottom    = top;
    boolean logo      = layout.hasVisibleLogo();
    boolean logoRight = layout.logoPosition() == PdfLayout.LogoPosition.RIGHT;
    if (logo) {
      PDImageXObject image;
      try {
        image = PDImageXObject.createFromByteArray(document,
                                                   layout.logo(),
                                                   "logo");
      } catch (IOException | RuntimeException e) {
        throw new IllegalArgumentException("The logo of the layout is no readable PNG or JPEG image",
                                           e);
      }
      float scale = Math.min(20 * MM / image.getHeight(),
                             60 * MM / image.getWidth());
      float w = image.getWidth() * scale;
      float h = image.getHeight() * scale;
      canvas.image(image,
                   logoRight ?
                   canvas.right - w :
                   canvas.left,
                   top - h,
                   w,
                   h);
      bottom = top - h;
    }
    Party sender = sender();
    if (sender != null && layout.showSenderAddress()) {
      boolean right = !(logo && logoRight);
      var lines = new ArrayList<String>(addressLines(sender.address(),
                                                     true));
      if (sender.contact() != null) {
        add(lines,
            sender.contact()
                  .telephone());
        add(lines,
            sender.contact()
                  .email());
      }
      float y = top - senderName.size();
      letterheadLine(sender.name(),
                     senderName,
                     right,
                     y);
      for (String line : lines) {
        y -= small.leading();
        letterheadLine(line,
                       small,
                       right,
                       y);
      }
      bottom = Math.min(bottom,
                        y - small.descent());
    }
    canvas.y = bottom;
  }

  private void letterheadLine(String text,
                              Style style,
                              boolean right,
                              float baseline) {
    if (right) {
      canvas.textRight(text,
                       style,
                       canvas.right,
                       baseline);
    } else {
      canvas.text(text,
                  style,
                  canvas.left,
                  baseline);
    }
  }

  private void addressAndFacts() {
    float windowTop = PAGE.getHeight() - 47 * MM;
    Party sender    = sender();
    if (sender != null) {
      var parts = new ArrayList<String>();
      add(parts,
          sender.name());
      if (sender.address() != null) {
        add(parts,
            sender.address()
                  .street());
        add(parts,
            join(" ",
                 sender.address()
                       .postalCode(),
                 sender.address()
                       .city()));
      }
      String line = String.join(" · ",
                                parts);
      while (tiny.width(line) > 85 * MM && line.contains(" · ")) {
        line = line.substring(0,
                              line.lastIndexOf(" · "));
      }
      canvas.text(line,
                  tiny,
                  canvas.left,
                  windowTop);
      canvas.rule(canvas.left,
                  canvas.left +
                  Math.min(85 * MM,
                           tiny.width(line)),
                  windowTop - 1.5f,
                  0.3f,
                  LIGHT);
    }
    float recipientY = windowTop - 7 * MM;
    Party recipient  = recipient();
    if (recipient != null) {
      var lines = new ArrayList<String>();
      add(lines,
          recipient.name());
      if (recipient.contact() != null &&
          recipient.contact()
                   .name() != null) {
        add(lines,
            recipient.contact()
                     .name());
      }
      lines.addAll(addressLines(recipient.address(),
                                !sameCountry(recipient.address(),
                                             sender == null ?
                                             null :
                                             sender.address())));
      for (int i = 0; i < lines.size(); i++) {
        Style style = i == 0 ?
                      bodyBold :
                      body;
        for (String wrapped : style.wrap(lines.get(i),
                                         80 * MM)) {
          recipientY -= body.leading() + 0.6f;
          canvas.text(wrapped,
                      style,
                      canvas.left,
                      recipientY);
        }
      }
    }
    float factsHeight = canvas.facts(facts(),
                                     label,
                                     small.color(TEXT),
                                     canvas.right - 72 * MM,
                                     30 * MM,
                                     42 * MM,
                                     windowTop + 3 * MM);
    canvas.y = Math.min(Math.min(recipientY,
                                 windowTop + 3 * MM - factsHeight),
                        PAGE.getHeight() - 95 * MM) - 6 * MM;
  }

  /**
   * Whether page 1 has the address window of a letter; documents without recipient, such as
   * reports, start with the title and show their facts below it.
   */
  boolean addressWindow() {
    return true;
  }

  /**
   * The facts below the title, for documents without address window.
   */
  private void topFacts() {
    float labelWidth = 38 * MM;
    canvas.space(canvas.facts(facts(),
                              label,
                              body,
                              canvas.left,
                              labelWidth,
                              canvas.width() - labelWidth,
                              canvas.y) + 4);
  }

  private void heading() {
    canvas.ensure(title.leading() + 8);
    canvas.space(title.leading());
    canvas.text(documentTitle(),
                title,
                canvas.left,
                canvas.y + title.descent());
    canvas.space(3);
    canvas.rule(canvas.left,
                canvas.right,
                canvas.y,
                1.2f,
                accent);
    canvas.space(6);
  }

  void notes(List<String> notes) {
    for (String note : notes) {
      if (note != null && !note.isBlank()) {
        canvas.paragraph(note,
                         body,
                         canvas.left,
                         canvas.width());
        canvas.space(3);
      }
    }
    canvas.space(4);
  }

  Row header(String... keys) {
    var cells = new ArrayList<Cell>();
    for (String key : keys) {
      cells.add(new Cell().add(key == null ?
                               null :
                               t.label(key),
                               columnHead));
    }
    return new Row(cells,
                   false);
  }

  void table(List<Canvas.Column> columns,
             Row header,
             List<Row> rows) {
    if (rows.isEmpty()) {
      return;
    }
    canvas.table(columns,
                 header,
                 rows,
                 LIGHT,
                 TEXT);
    canvas.rule(canvas.left,
                canvas.right,
                canvas.y,
                0.8f,
                TEXT);
    canvas.space(4);
  }

  /**
   * Item details of a line, joined with middle dots: item numbers, origin, attributes, ...
   */
  String details(List<String> parts,
                 List<ItemAttribute> attributes) {
    var all = new ArrayList<>(parts);
    for (ItemAttribute attribute : attributes) {
      all.add(join(": ",
                   attribute.name(),
                   attribute.value()));
    }
    var values = new ArrayList<String>();
    for (String part : all) {
      add(values,
          part);
    }
    return String.join(" · ",
                       values);
  }

  TotalRow plain(String text,
                 String value) {
    return new TotalRow(text,
                        value,
                        body,
                        body,
                        0,
                        0);
  }

  TotalRow strong(String text,
                  String value) {
    return new TotalRow(text,
                        value,
                        bodyBold,
                        bodyBold,
                        0.6f,
                        LIGHT);
  }

  TotalRow grand(String text,
                 String value) {
    return new TotalRow(text,
                        value,
                        total,
                        total,
                        1.6f,
                        accent);
  }

  /**
   * Draws label and amount rows on the right 60 % of the page, kept together.
   */
  void totals(List<TotalRow> rows) {
    if (rows.isEmpty()) {
      return;
    }
    float x          = canvas.left + canvas.width() * 0.40f;
    float valueWidth = 30 * MM;
    float labelWidth = canvas.right - x - valueWidth;
    float height     = 0;
    for (TotalRow row : rows) {
      height += row.labelStyle()
                   .wrap(row.label(),
                         labelWidth)
                   .size() *
                row.labelStyle()
                   .leading() +
                (row.ruleAbove() > 0 ?
                 3 :
                 0) +
                1;
    }
    canvas.ensure(height + 4);
    canvas.space(2);
    for (TotalRow row : rows) {
      if (row.ruleAbove() > 0) {
        canvas.space(1.5f);
        canvas.rule(x,
                    canvas.right,
                    canvas.y,
                    row.ruleAbove(),
                    row.ruleRgb());
        canvas.space(1.5f);
      }
      List<String> lines = row.labelStyle()
                              .wrap(row.label(),
                                    labelWidth);
      for (int i = 0; i < lines.size(); i++) {
        canvas.space(row.labelStyle()
                        .leading());
        float baseline = canvas.y +
                         row.labelStyle()
                            .descent();
        canvas.text(lines.get(i),
                    row.labelStyle(),
                    x,
                    baseline);
        if (i == 0) {
          canvas.textRight(row.value(),
                           row.valueStyle(),
                           canvas.right,
                           baseline);
        }
      }
      canvas.space(1);
    }
    canvas.space(8);
  }

  String adjustmentText(String prefix,
                        boolean charge,
                        String reason,
                        BigDecimal percentage,
                        BigDecimal base) {
    String name = reason != null && !reason.isBlank() ?
                  reason :
                  t.label(prefix +
                          (charge ?
                           ".chargeDefault" :
                           ".allowanceDefault"));
    String text = t.label(prefix +
                          (charge ?
                           ".charge" :
                           ".allowance"),
                          name);
    if (percentage != null && base != null) {
      text += " (" + t.label("adjustment.percentOf",
                             t.percent(percentage),
                             money(base)) + ")";
    }
    return text;
  }

  void section(String key) {
    canvas.ensure(heading.leading() + 3 * body.leading());
    canvas.space(heading.leading());
    canvas.text(t.label(key),
                heading,
                canvas.left,
                canvas.y + heading.descent());
    canvas.space(2);
  }

  /**
   * A section with label and value rows; nothing if there are no rows.
   */
  void factsSection(String key,
                    List<String[]> rows) {
    if (rows.isEmpty()) {
      return;
    }
    section(key);
    float labelWidth = 38 * MM;
    for (String[] row : rows) {
      int lines = Math.max(label.wrap(row[0],
                                      labelWidth - 4)
                                .size(),
                           body.wrap(row[1],
                                     canvas.width() - labelWidth)
                               .size());
      canvas.ensure(lines * body.leading());
      canvas.space(canvas.facts(List.<String[]>of(row),
                                label,
                                body,
                                canvas.left,
                                labelWidth,
                                canvas.width() - labelWidth,
                                canvas.y));
    }
    canvas.space(6);
  }

  /**
   * Parties side by side in two columns, each with label, name, address, and identifiers.
   */
  void parties(List<Object[]> labelled) {
    var blocks = new ArrayList<List<String[]>>();
    for (Object[] entry : labelled) {
      Party party = (Party) entry[1];
      if (party == null) {
        continue;
      }
      var lines = new ArrayList<String[]>();
      lines.add(new String[] { "label",
                               t.label((String) entry[0]) });
      if (party.name() != null) {
        lines.add(new String[] { "name",
                                 party.name() });
      }
      for (String line : addressLines(party.address(),
                                      true)) {
        lines.add(new String[] { "text",
                                 line });
      }
      String contact = party.contact() == null ?
                       null :
                       join(" · ",
                            party.contact()
                                 .name(),
                            party.contact()
                                 .telephone(),
                            party.contact()
                                 .email());
      if (contact != null) {
        lines.add(new String[] { "small",
                                 contact });
      }
      String ids = join(" · ",
                        party.identifier(),
                        prefixed("seller.vatId",
                                 party.vatId()));
      if (ids != null) {
        lines.add(new String[] { "small",
                                 ids });
      }
      blocks.add(lines);
    }
    float columnWidth = (canvas.width() - 8 * MM) / 2;
    for (int i = 0; i < blocks.size(); i += 2) {
      float height = Math.max(blockHeight(blocks.get(i),
                                          columnWidth),
                              i + 1 < blocks.size() ?
                              blockHeight(blocks.get(i + 1),
                                          columnWidth) :
                              0);
      canvas.ensure(height + 4);
      float top = canvas.y - 4;
      drawBlock(blocks.get(i),
                canvas.left,
                columnWidth,
                top);
      if (i + 1 < blocks.size()) {
        drawBlock(blocks.get(i + 1),
                  canvas.left + columnWidth + 8 * MM,
                  columnWidth,
                  top);
      }
      canvas.y = top - height - 4;
    }
  }

  private Style blockStyle(String kind) {
    return switch (kind) {
      case "label" -> heading;
      case "name" -> bodyBold;
      case "small" -> small;
      default -> body;
    };
  }

  private float blockHeight(List<String[]> lines,
                            float width) {
    float height = 0;
    for (String[] line : lines) {
      Style style = blockStyle(line[0]);
      height += style.wrap(line[1],
                           width)
                     .size() * style.leading();
    }
    return height;
  }

  // -- header and footer of every page ------------------------------------------------------------

  private void drawBlock(List<String[]> lines,
                         float x,
                         float width,
                         float top) {
    float y = top;
    for (String[] line : lines) {
      Style style = blockStyle(line[0]);
      for (String wrapped : style.wrap(line[1],
                                       width)) {
        y -= style.leading();
        canvas.text(wrapped,
                    style,
                    x,
                    y + style.descent());
      }
    }
  }

  void supportingDocuments(List<AdditionalSupportingDocument> documents) {
    var lines = new ArrayList<String>();
    for (AdditionalSupportingDocument document : documents) {
      add(lines,
          join(" – ",
               document.reference(),
               document.description(),
               document.attachment() == null ?
               document.externalLocation() :
               document.attachment()
                       .filename()));
    }
    if (lines.isEmpty()) {
      return;
    }
    section("attachments");
    for (String line : lines) {
      canvas.paragraph("• " + line,
                       body,
                       canvas.left,
                       canvas.width());
    }
  }

  void row(List<String[]> rows,
           String key,
           String value) {
    if (value != null && !value.isBlank()) {
      rows.add(new String[] { t.label(key),
                              value });
    }
  }

  private void continuationHeader(Canvas c) {
    String text = join(" · ",
                       documentTitle(),
                       sender() == null ?
                       null :
                       sender().name());
    c.text(text,
           small,
           c.left,
           c.top - small.size());
    c.rule(c.left,
           c.right,
           c.top - small.size() - 4,
           0.4f,
           LIGHT);
    c.y = c.top - small.size() - 10;
  }

  private List<List<String>> footerColumns() {
    Party sender = sender();
    var   first  = new ArrayList<String>();
    var   second = new ArrayList<String>();
    var   third  = new ArrayList<String>();
    if (sender != null) {
      add(first,
          sender.name());
      first.addAll(addressLines(sender.address(),
                                true));
      if (sender.contact() != null) {
        add(second,
            prefixed("seller.phone",
                     sender.contact()
                           .telephone()));
        add(second,
            prefixed("seller.email",
                     sender.contact()
                           .email()));
      }
      if (sender.electronicAddress() != null) {
        add(second,
            prefixed("seller.electronicAddress",
                     sender.electronicAddress()
                           .value()));
      }
      add(third,
          prefixed("seller.vatId",
                   sender.vatId()));
      add(third,
          prefixed("seller.taxId",
                   sender.taxId()));
      add(third,
          prefixed("seller.legal",
                   sender.legalRegistrationId()));
      add(third,
          sender.additionalLegalInformation());
    }
    return List.of(first,
                   second,
                   third);
  }

  // -- helpers ------------------------------------------------------------------------------------

  private float footerWidth() {
    return (PAGE.getWidth() - 45 * MM) / 3f;
  }

  private float footerHeight() {
    int lines = 0;
    for (List<String> column : footerColumns()) {
      int count = 0;
      for (String line : column) {
        count += tiny.wrap(line,
                           footerWidth() - 6)
                     .size();
      }
      lines = Math.max(lines,
                       count);
    }
    if (layout.footer() != null) {
      lines += tiny.wrap(layout.footer(),
                         PAGE.getWidth() - 45 * MM)
                   .size();
    }
    return lines * tiny.leading() + 6;
  }

  private void footers(PDDocument document)
      throws IOException {
    List<PDPage> pages = canvas.pages();
    float        left  = 25 * MM;
    float        right = PAGE.getWidth() - 20 * MM;
    float        top   = 12 * MM + footerHeight();
    for (int p = 0; p < pages.size(); p++) {
      try (var stream = new PDPageContentStream(document,
                                                pages.get(p),
                                                PDPageContentStream.AppendMode.APPEND,
                                                true,
                                                true)) {
        String page = t.label("page",
                              String.valueOf(p + 1),
                              String.valueOf(pages.size()));
        Canvas.text(stream,
                    page,
                    tiny,
                    right - tiny.width(page),
                    top + 3);
        Canvas.rule(stream,
                    left,
                    right,
                    top,
                    0.4f,
                    LIGHT);
        float              bottomOfColumns = top;
        List<List<String>> columns         = footerColumns();
        for (int c = 0; c < columns.size(); c++) {
          float y = top - 2;
          for (String line : columns.get(c)) {
            for (String wrapped : tiny.wrap(line,
                                            footerWidth() - 6)) {
              y -= tiny.leading();
              Canvas.text(stream,
                          wrapped,
                          tiny,
                          left + c * footerWidth(),
                          y + tiny.descent());
            }
          }
          bottomOfColumns = Math.min(bottomOfColumns,
                                     y);
        }
        if (layout.footer() != null) {
          float y = bottomOfColumns;
          for (String wrapped : tiny.wrap(layout.footer(),
                                          right - left)) {
            y -= tiny.leading();
            Canvas.text(stream,
                        wrapped,
                        tiny,
                        left,
                        y + tiny.descent());
          }
        }
      }
    }
  }

  /**
   * The watermark of the layout across every page, along the diagonal from bottom left to top right,
   * as large as fits about two thirds of the diagonal, at most 110 pt; semi-transparent, so the
   * content below stays readable.
   */
  private void watermarks(PDDocument document)
      throws IOException {
    String text = layout.watermark();
    if (text == null) {
      return;
    }
    float diagonal = (float) Math.hypot(PAGE.getWidth(),
                                        PAGE.getHeight());
    // Filled only: a simulated bold outline would overlap the fill and darken the edges.
    var   font     = new Font(bodyBold.font().pdf,
                              false);
    float width    = font.width(text,
                                1f);
    var style = new Style(font,
                          Math.min(110f,
                                   diagonal * 0.65f / Math.max(width,
                                                               0.01f)),
                          WATERMARK);
    var transparent = new PDExtendedGraphicsState();
    transparent.setNonStrokingAlphaConstant(0.35f);
    transparent.setStrokingAlphaConstant(0.35f);
    for (PDPage page : canvas.pages()) {
      try (var stream = new PDPageContentStream(document,
                                                page,
                                                PDPageContentStream.AppendMode.APPEND,
                                                true,
                                                true)) {
        stream.saveGraphicsState();
        stream.setGraphicsStateParameters(transparent);
        stream.transform(Matrix.getRotateInstance(Math.atan2(PAGE.getHeight(),
                                                             PAGE.getWidth()),
                                                  PAGE.getWidth() / 2,
                                                  PAGE.getHeight() / 2));
        Canvas.text(stream,
                    text,
                    style,
                    -style.width(text) / 2,
                    -style.size() * 0.35f);
        stream.restoreGraphicsState();
      }
    }
  }

  String money(BigDecimal value) {
    return t.money(value,
                   currency());
  }

  String prefixed(String key,
                  String value) {
    return value == null || value.isBlank() ?
           null :
           t.label(key) + " " + value;
  }

  void fact(List<String[]> rows,
            String key,
            String value) {
    if (value != null && !value.isBlank()) {
      rows.add(new String[] { t.label(key),
                              value });
    }
  }

  List<String> addressLines(Address address,
                            boolean withCountry) {
    var lines = new ArrayList<String>();
    if (address == null) {
      return lines;
    }
    add(lines,
        address.street());
    add(lines,
        address.additionalStreet());
    add(lines,
        address.additionalLine());
    add(lines,
        join(" ",
             address.postalCode(),
             address.city()));
    add(lines,
        address.countrySubdivision());
    if (withCountry && address.country() != null) {
      add(lines,
          t.country(address.country()));
    }
    return lines;
  }



  record TotalRow(String label, String value, Style labelStyle, Style valueStyle, float ruleAbove, int ruleRgb) {
  }
}

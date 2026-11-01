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

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.graphics.state.RenderingMode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * A small flow layout on A4 pages: text with word wrap, rules, images, and tables whose rows are
 * kept together when possible, split line by line otherwise, and whose header is repeated on each
 * new page. Coordinates are PDF points with the origin at the bottom left; {@link #y} is the
 * current top of the flow. One instance renders one document and is confined to one thread.
 */
final class Canvas {
  static final float       MM   = 72f / 25.4f;
  static final PDRectangle PAGE = PDRectangle.A4;
  static final float CELL_PADDING_X = 4;
  static final float CELL_PADDING_Y = 4;
  final PDDocument document;
  final float      left;
  final float      right;
  final float      top;
  private final List<PDPage>        pages        = new ArrayList<>();
  float bottom;
  float y;
  private       PDPageContentStream content;
  private       Consumer<Canvas>    continuation = canvas -> {
  };

  Canvas(PDDocument document,
         float left,
         float right,
         float top,
         float bottom) {
    this.document = document;
    this.left     = left;
    this.right    = right;
    this.top      = top;
    this.bottom   = bottom;
  }

  static void text(PDPageContentStream stream,
                   String text,
                   Style style,
                   float x,
                   float baseline) {
    if (text == null || text.isEmpty()) {
      return;
    }
    try {
      stream.beginText();
      stream.setFont(style.font.pdf,
                     style.size);
      color(stream,
            style.rgb);
      if (style.font.embolden) {
        stream.setRenderingMode(RenderingMode.FILL_STROKE);
        stream.setLineWidth(style.size * 0.022f);
      }
      stream.newLineAtOffset(x,
                             baseline);
      stream.showText(style.font.printable(text));
      stream.endText();
      if (style.font.embolden) {
        stream.setRenderingMode(RenderingMode.FILL);
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  static void rule(PDPageContentStream stream,
                   float x1,
                   float x2,
                   float atY,
                   float lineWidth,
                   int rgb) {
    try {
      stream.setStrokingColor(((rgb >> 16) & 0xFF) / 255f,
                              ((rgb >> 8) & 0xFF) / 255f,
                              (rgb & 0xFF) / 255f);
      stream.setLineWidth(lineWidth);
      stream.moveTo(x1,
                    atY);
      stream.lineTo(x2,
                    atY);
      stream.stroke();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static void color(PDPageContentStream stream,
                            int rgb)
      throws IOException {
    float r = ((rgb >> 16) & 0xFF) / 255f;
    float g = ((rgb >> 8) & 0xFF) / 255f;
    float b = (rgb & 0xFF) / 255f;
    stream.setNonStrokingColor(r,
                               g,
                               b);
    stream.setStrokingColor(r,
                            g,
                            b);
  }

  private static float height(List<List<Line>> cells) {
    float max = 0;
    for (List<Line> lines : cells) {
      float h = 0;
      for (Line line : lines) {
        h += line.style()
                 .leading();
      }
      max = Math.max(max,
                     h);
    }
    return max;
  }

  private static int[] fitting(List<List<Line>> cells,
                               int[] from,
                               float available) {
    int[] counts = new int[cells.size()];
    for (int c = 0; c < cells.size(); c++) {
      float h = 0;
      for (int i = from[c]; i <
                            cells.get(c)
                                 .size(); i++) {
        h += cells.get(c)
                  .get(i)
                  .style()
                  .leading();
        if (h > available) {
          break;
        }
        counts[c]++;
      }
    }
    return counts;
  }

  float width() {
    return right - left;
  }

  // -- primitives ---------------------------------------------------------------------------------

  List<PDPage> pages() {
    return pages;
  }

  /**
   * Sets what is drawn at the top of every page after the first, e.g. a short header.
   */
  void continuation(Consumer<Canvas> header) {
    this.continuation = header;
  }

  void newPage() {
    close();
    var page = new PDPage(PAGE);
    document.addPage(page);
    pages.add(page);
    try {
      content = new PDPageContentStream(document,
                                        page);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    y = top;
    if (pages.size() > 1) {
      continuation.accept(this);
    }
  }

  void close() {
    if (content != null) {
      try {
        content.close();
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
      content = null;
    }
  }

  boolean fits(float height) {
    return y - height >= bottom;
  }

  void ensure(float height) {
    if (!fits(height)) {
      newPage();
    }
  }

  void space(float height) {
    y -= height;
  }

  void text(String text,
            Style style,
            float x,
            float baseline) {
    text(content,
         text,
         style,
         x,
         baseline);
  }

  // -- flow text ----------------------------------------------------------------------------------

  void textRight(String text,
                 Style style,
                 float xRight,
                 float baseline) {
    text(text,
         style,
         xRight - style.width(text),
         baseline);
  }

  void rule(float x1,
            float x2,
            float atY,
            float lineWidth,
            int rgb) {
    rule(content,
         x1,
         x2,
         atY,
         lineWidth,
         rgb);
  }

  // -- tables -------------------------------------------------------------------------------------

  void fill(float x,
            float yBottom,
            float w,
            float h,
            int rgb) {
    try {
      color(content,
            rgb);
      content.addRect(x,
                      yBottom,
                      w,
                      h);
      content.fill();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  void image(PDImageXObject image,
             float x,
             float yBottom,
             float w,
             float h) {
    try {
      content.drawImage(image,
                        x,
                        yBottom,
                        w,
                        h);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * Draws wrapped text at the current position and moves down; breaks the page between lines.
   */
  void paragraph(String text,
                 Style style,
                 float x,
                 float w) {
    for (String line : style.wrap(text,
                                  w)) {
      ensure(style.leading());
      y -= style.leading();
      text(line,
           style,
           x,
           y + style.descent());
    }
  }

  /**
   * Draws label and value rows in two columns at a fixed position, without page breaks; returns the
   * height used.
   */
  float facts(List<String[]> rows,
              Style labelStyle,
              Style valueStyle,
              float x,
              float labelWidth,
              float valueWidth,
              float startY) {
    float cursor = startY;
    for (String[] row : rows) {
      List<String> labels = labelStyle.wrap(row[0],
                                            labelWidth - 4);
      List<String> values = valueStyle.wrap(row[1],
                                            valueWidth);
      int count = Math.max(labels.size(),
                           values.size());
      for (int i = 0; i < count; i++) {
        cursor -= valueStyle.leading();
        if (i < labels.size()) {
          text(labels.get(i),
               labelStyle,
               x,
               cursor + valueStyle.descent());
        }
        if (i < values.size()) {
          text(values.get(i),
               valueStyle,
               x + labelWidth,
               cursor + valueStyle.descent());
        }
      }
    }
    return startY - cursor;
  }

  /**
   * Draws a table at the full width from the current position; repeats the header on new pages.
   */
  void table(List<Column> columns,
             Row header,
             List<Row> rows,
             int ruleRgb,
             int headerRuleRgb) {
    Runnable drawHeader = () -> {
      float height = drawRow(columns,
                             layout(columns,
                                    header),
                             0,
                             Integer.MAX_VALUE,
                             y);
      y -= height;
      rule(left,
           right,
           y,
           0.8f,
           headerRuleRgb);
    };
    float headerHeight = height(layout(columns,
                                       header)) + 2 * CELL_PADDING_Y;
    ensure(headerHeight + 24);
    drawHeader.run();
    for (int r = 0; r < rows.size(); r++) {
      Row row = rows.get(r);
      List<List<Line>> cells = layout(columns,
                                      row);
      float height = height(cells) + 2 * CELL_PADDING_Y;
      if (!fits(height) && height <= top - bottom - headerHeight) {
        newPage();
        drawHeader.run();
      }
      int[] from = new int[cells.size()];
      while (true) {
        int[] counts = fitting(cells,
                               from,
                               y - bottom - 2 * CELL_PADDING_Y);
        boolean any = false;
        for (int c = 0; c < counts.length; c++) {
          any |= counts[c] > 0;
        }
        if (!any) {
          newPage();
          drawHeader.run();
          continue;
        }
        float used = 0;
        for (int c = 0; c < cells.size(); c++) {
          used = Math.max(used,
                          drawCell(columns,
                                   c,
                                   cells.get(c),
                                   from[c],
                                   counts[c],
                                   y));
        }
        y -= used + 2 * CELL_PADDING_Y;
        boolean done = true;
        for (int c = 0; c < cells.size(); c++) {
          from[c] += counts[c];
          done &= from[c] >=
                  cells.get(c)
                       .size();
        }
        if (done) {
          break;
        }
        newPage();
        drawHeader.run();
      }
      if (row.separator() && r < rows.size() - 1) {
        rule(left,
             right,
             y,
             0.4f,
             ruleRgb);
      }
    }
  }

  private List<List<Line>> layout(List<Column> columns,
                                  Row row) {
    var cells = new ArrayList<List<Line>>();
    for (int c = 0; c < columns.size(); c++) {
      Cell cell = c <
                  row.cells()
                     .size() ?
                  row.cells()
                     .get(c) :
                  new Cell();
      cells.add(cell.lines(columns.get(c)
                                  .width() - 2 * CELL_PADDING_X));
    }
    return cells;
  }

  private float drawRow(List<Column> columns,
                        List<List<Line>> cells,
                        int from,
                        int count,
                        float rowTop) {
    float used = 0;
    for (int c = 0; c < cells.size(); c++) {
      used = Math.max(used,
                      drawCell(columns,
                               c,
                               cells.get(c),
                               from,
                               Math.min(count,
                                        cells.get(c)
                                             .size()),
                               rowTop));
    }
    return used + 2 * CELL_PADDING_Y;
  }

  private float drawCell(List<Column> columns,
                         int column,
                         List<Line> lines,
                         int from,
                         int count,
                         float rowTop) {
    float x = left;
    for (int c = 0; c < column; c++) {
      x += columns.get(c)
                  .width();
    }
    Column definition = columns.get(column);
    float  cursor     = rowTop - CELL_PADDING_Y;
    for (int i = from; i < from + count; i++) {
      Line line = lines.get(i);
      cursor -= line.style()
                    .leading();
      if (definition.align() == Align.RIGHT) {
        textRight(line.text(),
                  line.style(),
                  x + definition.width() - CELL_PADDING_X,
                  cursor +
                  line.style()
                      .descent());
      } else {
        text(line.text(),
             line.style(),
             x + CELL_PADDING_X + line.indent(),
             cursor +
             line.style()
                 .descent());
      }
    }
    return rowTop - CELL_PADDING_Y - cursor;
  }

  enum Align {
    LEFT,
    RIGHT
  }



  record Column(float width, Align align) {
  }



  /**
   * One text line of a cell, wrapped already, with an indent from the cell start.
   */
  record Line(String text, Style style, float indent) {
  }



  /**
   * Content of a cell: paragraphs that are wrapped to the column width.
   */
  static final class Cell {
    private final List<Object[]> parts = new ArrayList<>();

    Cell add(String text,
             Style style) {
      return add(text,
                 style,
                 0);
    }

    Cell add(String text,
             Style style,
             float indent) {
      if (text != null && !text.isBlank()) {
        parts.add(new Object[] { text,
                                 style,
                                 indent });
      }
      return this;
    }

    List<Line> lines(float width) {
      var lines = new ArrayList<Line>();
      for (Object[] part : parts) {
        Style style  = (Style) part[1];
        float indent = (float) part[2];
        for (String line : style.wrap((String) part[0],
                                      width - indent)) {
          lines.add(new Line(line,
                             style,
                             indent));
        }
      }
      return lines;
    }
  }



  record Row(List<Cell> cells, boolean separator) {
  }

  // -- fonts and styles ---------------------------------------------------------------------------



  /**
   * An embedded font; characters it has no glyph for are replaced, so any text can be shown.
   */
  static final class Font {
    final PDType0Font pdf;
    final boolean     embolden;

    private final Map<Integer, Boolean> glyphs = new HashMap<>();

    Font(PDType0Font pdf,
         boolean embolden) {
      this.pdf      = pdf;
      this.embolden = embolden;
    }

    String printable(String text) {
      var out = new StringBuilder(text.length());
      text.codePoints()
          .forEach(cp -> {
            if (Character.isISOControl(cp)) {
              out.append(' ');
            } else if (glyphs.computeIfAbsent(cp,
                                              this::hasGlyph)) {
              out.appendCodePoint(cp);
            } else if (Character.isSpaceChar(cp)) {
              out.append(' ');
            } else if (cp == '−' && glyphs.computeIfAbsent((int) '-',
                                                           this::hasGlyph)) {
              out.append('-');
            } else {
              out.append('?');
            }
          });
      return out.toString();
    }

    private boolean hasGlyph(int codePoint) {
      try {
        pdf.encode(new String(Character.toChars(codePoint)));
        return true;
      } catch (IllegalArgumentException | IOException e) {
        return false;
      }
    }

    float width(String text,
                float size) {
      try {
        return pdf.getStringWidth(printable(text)) / 1000f * size;
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
  }



  /**
   * Font, size, and color of text.
   */
  record Style(Font font, float size, int rgb) {
    float leading() {
      return size * 1.3f;
    }

    float descent() {
      return size * 0.3f;
    }

    float width(String text) {
      return font.width(text,
                        size);
    }

    Style color(int other) {
      return new Style(font,
                       size,
                       other);
    }

    /**
     * Wraps text at spaces to the width; words longer than the width are broken; line breaks in the
     * text are kept.
     */
    List<String> wrap(String text,
                      float width) {
      var lines = new ArrayList<String>();
      if (text == null) {
        return lines;
      }
      for (String paragraph : text.replace("\r\n",
                                           "\n")
                                  .replace('\r',
                                           '\n')
                                  .split("\n",
                                         -1)) {
        Deque<String> words = new ArrayDeque<>(List.of(paragraph.trim()
                                                                .split("[ \\t]+")));
        var line = new StringBuilder();
        while (!words.isEmpty()) {
          String word = words.poll();
          String candidate = line.isEmpty() ?
                             word :
                             line + " " + word;
          if (width(candidate) <= width) {
            line.setLength(0);
            line.append(candidate);
          } else if (line.isEmpty()) {
            int cut = word.length() - 1;
            while (cut > 1 && width(word.substring(0,
                                                   cut)) > width) {
              cut--;
            }
            lines.add(word.substring(0,
                                     cut));
            words.push(word.substring(cut));
          } else {
            lines.add(line.toString());
            line.setLength(0);
            words.push(word);
          }
        }
        if (!line.isEmpty() || !lines.isEmpty() && paragraph.isBlank()) {
          lines.add(line.toString());
        }
      }
      return lines;
    }
  }
}

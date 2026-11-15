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
import io.github.nalukit.paniolo.model.common.Party;
import io.github.nalukit.paniolo.model.common.value.CurrencyCode;
import io.github.nalukit.paniolo.pdflayout.Canvas.Align;
import io.github.nalukit.paniolo.pdflayout.Canvas.Cell;
import io.github.nalukit.paniolo.pdflayout.Canvas.Column;
import io.github.nalukit.paniolo.pdflayout.Canvas.Row;
import io.github.nalukit.paniolo.pdflayout.Canvas.Style;
import io.github.nalukit.paniolo.spi.PdfLayoutSupport.Report;
import io.github.nalukit.paniolo.validation.Severity;
import io.github.nalukit.paniolo.validation.ValidationMessage;
import io.github.nalukit.paniolo.validation.ValidationMessage.Stage;
import io.github.nalukit.paniolo.validation.ValidationResult;
import org.apache.pdfbox.pdmodel.PDDocument;

import java.io.IOException;
import java.io.InputStream;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import static io.github.nalukit.paniolo.pdflayout.Canvas.MM;

/**
 * Lays out a validation report as conformance report: a verdict at the top (conform, conform with
 * warnings, not conform) in the color of the result, the number of findings per severity, the
 * validated document and the rules applied, an overview of the validation stages with their result
 * and statistics, and every finding as a block with severity, rule, message and location, grouped
 * by stage like the XML report. A report without findings shows the conform verdict and the stages
 * that were run.
 */
final class ValidationReportRenderer
    extends DocumentRenderer {
  static final int OK         = 0x12805C;
  static final int OK_TINT    = 0xE6F4EE;
  static final int WARN       = 0xB06A00;
  static final int WARN_TINT  = 0xFDF2DE;
  static final int ERROR      = 0xC8322B;
  static final int ERROR_TINT = 0xFBEAE8;
  static final int FATAL      = 0x8E1B16;
  static final int INFO_TINT  = 0xE6F5F9;
  static final int NEUTRAL    = 0xF3F5F7;

  private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'")
                                                                 .withZone(ZoneOffset.UTC);
  private static final List<Severity> SEVERITIES = List.of(Severity.FATAL,
                                                           Severity.ERROR,
                                                           Severity.WARNING,
                                                           Severity.INFO);
  private static final float BAR = 2.6f;
  private static final float PAD_X = 3 * MM;
  private static final float PAD_Y = 2.2f * MM;

  private final Report report;

  ValidationReportRenderer(Report report,
                           PdfLayout layout) {
    super(layout);
    this.report = report;
  }

  /**
   * The state of findings: 0 conform, 1 conform with warnings, 2 not conform.
   */
  static int state(List<ValidationMessage> messages) {
    int state = 0;
    for (var message : messages) {
      if (message.severity() == Severity.ERROR || message.severity() == Severity.FATAL) {
        return 2;
      }
      if (message.severity() == Severity.WARNING) {
        state = 1;
      }
    }
    return state;
  }

  private static String version() {
    try (InputStream input = ValidationResult.class.getResourceAsStream("paniolo-validation.properties")) {
      if (input == null) {
        return null;
      }
      var properties = new Properties();
      properties.load(input);
      String version = properties.getProperty("version");
      return version == null || version.startsWith("${") ?
             null :
             version;
    } catch (IOException e) {
      return null;
    }
  }

  @Override
  boolean addressWindow() {
    return false;
  }

  @Override
  Party sender() {
    return null;
  }

  @Override
  Party recipient() {
    return null;
  }

  @Override
  CurrencyCode currency() {
    return null;
  }

  @Override
  String documentTitle() {
    return join(" – ",
                t.label("report.title"),
                report.fileName());
  }

  @Override
  List<String[]> facts() {
    return List.of();
  }

  // -- content ------------------------------------------------------------------------------------

  @Override
  void content(PDDocument document) {
    var result = report.result();
    int state  = state(result.messages());

    verdict(t.label("report.verdict"),
            t.label("report.verdict." + stateKey(state),
                    result.count(Severity.FATAL) + result.count(Severity.ERROR),
                    result.count(Severity.WARNING)),
            state);
    canvas.space(3 * MM);
    tiles();

    var summary = new ArrayList<String[]>();
    fact(summary,
         "report.result",
         t.label(result.isValid() ?
                 "report.valid" :
                 "report.invalid"));
    fact(summary,
         "report.validatedAt",
         TIME.format(report.validatedAt()
                           .truncatedTo(ChronoUnit.SECONDS)));
    if (result.duration() != null) {
      fact(summary,
           "report.duration",
           result.duration()
                 .toMillis() + " ms");
    }
    String version = version();
    fact(summary,
         "report.software",
         version == null ?
         "Paniolo" :
         "Paniolo " + version);
    factsSection("report.summary",
                 summary);

    var subject = new ArrayList<String[]>();
    fact(subject,
         "report.file",
         report.fileName());
    fact(subject,
         "report.sha256",
         report.sha256());
    if (report.size() >= 0) {
      fact(subject,
           "report.size",
           t.label("report.bytes",
                   String.valueOf(report.size())));
    }
    fact(subject,
         "report.profile",
         result.profileName());
    fact(subject,
         "report.rules",
         report.rules());
    factsSection("report.document",
                 subject);

    stages();
    statistics();
    findings();
    notes(List.of(t.label("report.note")));
  }

  /**
   * Number of findings per severity in four tiles side by side, tinted in the severity color when
   * there are findings of it.
   */
  private void tiles() {
    var   result = report.result();
    float gap    = 3 * MM;
    float width  = (canvas.width() - 3 * gap) / 4;
    var   number = title;
    float height = PAD_Y * 2 + number.leading() + small.leading();
    canvas.ensure(height);
    float top = canvas.y;
    for (int i = 0; i < SEVERITIES.size(); i++) {
      Severity severity = SEVERITIES.get(i);
      long     count    = result.count(severity);
      int      color    = count == 0 ?
                          MUTED :
                          color(severity);
      float    x        = canvas.left + i * (width + gap);
      canvas.fill(x,
                  top - height,
                  width,
                  height,
                  count == 0 ?
                  NEUTRAL :
                  tint(severity));
      canvas.fill(x,
                  top - height,
                  BAR,
                  height,
                  count == 0 ?
                  LIGHT :
                  color);
      float baseline = top - PAD_Y - number.size();
      canvas.text(String.valueOf(count),
                  number.color(count == 0 ?
                               MUTED :
                               color),
                  x + PAD_X,
                  baseline);
      canvas.text(severityLabel(severity) + " (" + severity.name() + ")",
                  small.color(count == 0 ?
                              MUTED :
                              TEXT),
                  x + PAD_X,
                  baseline - small.leading() - 1);
    }
    canvas.y = top - height - 2 * MM;
  }

  /**
   * The stages that were run: those with findings and those the statistics name, each with the
   * number of findings per severity and its result.
   */
  private void stages() {
    var result = report.result();
    Set<Stage> run = EnumSet.noneOf(Stage.class);
    for (var message : result.messages()) {
      run.add(message.stage());
    }
    if (result.statistics() != null) {
      if (!result.statistics()
                 .schematron()
                 .isEmpty()) {
        run.add(Stage.SCHEMATRON);
      }
      if (!result.statistics()
                 .pdfa()
                 .isEmpty()) {
        run.add(Stage.PDF_A);
      }
    }
    if (run.isEmpty()) {
      return;
    }
    section("report.stages");
    float count = 20 * MM;
    float stateWidth = 34 * MM;
    float stageWidth = canvas.width() - 3 * count - stateWidth;
    var   rows  = new ArrayList<Row>();
    for (Stage stage : run) {
      var messages = result.messages(stage);
      int state    = state(messages);
      rows.add(new Row(List.of(new Cell().add(stage.name(),
                                              bodyBold.color(TEXT))
                                         .add(t.label("report.stage." + stage.name()),
                                              small),
                               countCell(messages,
                                         Severity.ERROR,
                                         Severity.FATAL),
                               countCell(messages,
                                         Severity.WARNING),
                               countCell(messages,
                                         Severity.INFO),
                               new Cell().add(t.label("report.state." + stateKey(state)),
                                              bodyBold.color(stateColor(state)))),
                       true));
    }
    table(List.of(new Column(stageWidth,
                             Align.LEFT),
                  new Column(count,
                             Align.RIGHT),
                  new Column(count,
                             Align.RIGHT),
                  new Column(count,
                             Align.RIGHT),
                  new Column(stateWidth,
                             Align.LEFT)),
          header("report.stage",
                 "report.errors",
                 "report.warnings",
                 "report.infos",
                 "report.result"),
          rows);
  }

  private Cell countCell(List<ValidationMessage> messages,
                         Severity... severities) {
    long count = messages.stream()
                         .filter(message -> List.of(severities)
                                                .contains(message.severity()))
                         .count();
    return new Cell().add(String.valueOf(count),
                          count == 0 ?
                          small :
                          bodyBold.color(color(severities[0])));
  }

  /**
   * Schematron and PDF/A statistics, if the validation collected them.
   */
  private void statistics() {
    var statistics = report.result()
                           .statistics();
    if (statistics == null || statistics.schematron()
                                        .isEmpty() && statistics.pdfa()
                                                                .isEmpty()) {
      return;
    }
    section("report.statistics");
    float number = 24 * MM;
    if (!statistics.schematron()
                   .isEmpty()) {
      var rows = new ArrayList<Row>();
      for (var schematron : statistics.schematron()) {
        rows.add(new Row(List.of(new Cell().add(schematron.stylesheet(),
                                                small.color(TEXT)),
                                 numberCell(schematron.activePatterns()),
                                 numberCell(schematron.firedRules()),
                                 numberCell(schematron.failedAsserts()),
                                 numberCell(schematron.successfulReports())),
                         true));
      }
      table(List.of(new Column(canvas.width() - 4 * number,
                               Align.LEFT),
                    new Column(number,
                               Align.RIGHT),
                    new Column(number,
                               Align.RIGHT),
                    new Column(number,
                               Align.RIGHT),
                    new Column(number,
                               Align.RIGHT)),
            header("report.stat.stylesheet",
                   "report.stat.activePatterns",
                   "report.stat.firedRules",
                   "report.stat.failedAsserts",
                   "report.stat.successfulReports"),
            rows);
    }
    if (!statistics.pdfa()
                   .isEmpty()) {
      var rows = new ArrayList<Row>();
      for (var pdfa : statistics.pdfa()) {
        int state = pdfa.compliant() ?
                    0 :
                    2;
        rows.add(new Row(List.of(new Cell().add(join(" · ",
                                                     pdfa.flavour(),
                                                     pdfa.profile()),
                                                small.color(TEXT)),
                                 numberCell(pdfa.checks()),
                                 numberCell(pdfa.failedChecks()),
                                 new Cell().add(t.label("report.state." + stateKey(state)),
                                                bodyBold.color(stateColor(state)))),
                         true));
      }
      table(List.of(new Column(canvas.width() - 2 * number - 34 * MM,
                               Align.LEFT),
                    new Column(number,
                               Align.RIGHT),
                    new Column(number,
                               Align.RIGHT),
                    new Column(34 * MM,
                               Align.LEFT)),
            header("report.stat.pdfa",
                   "report.stat.checks",
                   "report.stat.failedChecks",
                   "report.result"),
            rows);
    }
  }

  private Cell numberCell(int value) {
    return new Cell().add(String.valueOf(value),
                          small.color(TEXT));
  }

  /**
   * Every finding as a block, grouped by stage in the order the stages first occur, like the XML
   * report; numbered through the whole report.
   */
  private void findings() {
    var result = report.result();
    section("report.findings");
    if (result.messages()
              .isEmpty()) {
      verdict(t.label("report.findings"),
              t.label("report.none"),
              0);
      canvas.space(4 * MM);
      return;
    }
    Map<Stage, List<ValidationMessage>> byStage = new LinkedHashMap<>();
    for (var message : result.messages()) {
      byStage.computeIfAbsent(message.stage(),
                              stage -> new ArrayList<>())
             .add(message);
    }
    int number = 0;
    for (var entry : byStage.entrySet()) {
      var messages = entry.getValue();
      int state    = state(messages);
      canvas.ensure(bodyBold.leading() + 30 * MM);
      canvas.space(2 * MM + bodyBold.leading());
      float baseline = canvas.y + bodyBold.descent();
      canvas.text(entry.getKey()
                       .name() + " – " + t.label("report.stage." +
                                                 entry.getKey()
                                                      .name()),
                  bodyBold,
                  canvas.left,
                  baseline);
      canvas.textRight(t.label("report.state." + stateKey(state)),
                       bodyBold.color(stateColor(state)),
                       canvas.right,
                       baseline);
      canvas.space(1.5f);
      canvas.rule(canvas.left,
                  canvas.right,
                  canvas.y,
                  0.6f,
                  LIGHT);
      canvas.space(2 * MM);
      for (var message : messages) {
        number++;
        finding(number,
                message);
        canvas.space(2 * MM);
      }
    }
  }

  private void finding(int number,
                       ValidationMessage message) {
    Severity severity = message.severity();
    int      color    = color(severity);
    var      lines    = new ArrayList<BlockLine>();
    lines.add(new BlockLine(null,
                            null,
                            number + " · " + t.label("report.finding." + severity.name()) + " (" + severity.name() + ")",
                            bodyBold.color(color)));
    lines.add(new BlockLine(null,
                            null,
                            null,
                            tiny));
    rows(lines,
         t.label("report.rule"),
         message.ruleId(),
         small.color(TEXT));
    rows(lines,
         t.label("report.message"),
         message.message(),
         body);
    if (message.location() != null && !message.location()
                                              .isBlank()) {
      rows(lines,
           t.label("report.position"),
           message.location(),
           small);
    }
    block(lines,
          color,
          tint(severity));
  }

  private void rows(List<BlockLine> lines,
                    String labelText,
                    String value,
                    Style style) {
    float labelWidth = 24 * MM;
    float valueWidth = canvas.width() - 2 * PAD_X - BAR - labelWidth;
    var   labels     = label.wrap(labelText,
                                  labelWidth - 4);
    var   values     = style.wrap(value == null || value.isBlank() ?
                                  "–" :
                                  value,
                                  valueWidth);
    for (int i = 0; i < Math.max(labels.size(),
                                 values.size()); i++) {
      lines.add(new BlockLine(i < labels.size() ?
                              labels.get(i) :
                              null,
                              label,
                              i < values.size() ?
                              values.get(i) :
                              null,
                              style));
    }
  }

  /**
   * Draws lines in a tinted box with a bar in the color on the left; the box moves to the next page
   * as a whole if it fits there, a longer one continues there with its own box.
   */
  private void block(List<BlockLine> lines,
                     int color,
                     int background) {
    float total = 2 * PAD_Y;
    for (BlockLine line : lines) {
      total += line.leading();
    }
    if (!canvas.fits(total) && total <= canvas.top - canvas.bottom - 30 * MM) {
      canvas.newPage();
    }
    float labelWidth = 24 * MM;
    int   from       = 0;
    while (from < lines.size()) {
      float available = canvas.y - canvas.bottom - 2 * PAD_Y;
      float height    = 0;
      int   to        = from;
      while (to < lines.size() && height + lines.get(to)
                                                .leading() <= available) {
        height += lines.get(to)
                       .leading();
        to++;
      }
      if (to == from || to - from < Math.min(3,
                                              lines.size() - from)) {
        canvas.newPage();
        continue;
      }
      float top = canvas.y;
      float h   = height + 2 * PAD_Y;
      canvas.fill(canvas.left,
                  top - h,
                  canvas.width(),
                  h,
                  background);
      canvas.fill(canvas.left,
                  top - h,
                  BAR,
                  h,
                  color);
      float cursor = top - PAD_Y;
      float x      = canvas.left + BAR + PAD_X;
      for (int i = from; i < to; i++) {
        BlockLine line = lines.get(i);
        cursor -= line.leading();
        float baseline = cursor + line.valueStyle()
                                      .descent();
        if (line.label() != null) {
          canvas.text(line.label(),
                      line.labelStyle(),
                      x,
                      baseline);
        }
        if (line.value() != null) {
          canvas.text(line.value(),
                      line.valueStyle(),
                      line.labelStyle() == null ?
                      x :
                      x + labelWidth,
                      baseline);
        }
      }
      canvas.y = top - h;
      from     = to;
      if (from < lines.size()) {
        canvas.newPage();
      }
    }
  }

  /**
   * A full-width bar with a label on the left and a wrapped text on the right, tinted and with a
   * left bar in the color of the state.
   */
  private void verdict(String labelText,
                       String text,
                       int state) {
    float labelWidth = 38 * MM;
    float textWidth  = canvas.width() - BAR - 2 * PAD_X - labelWidth;
    var   lines      = body.wrap(text,
                                 textWidth);
    float height     = 2 * PAD_Y + Math.max(1,
                                            lines.size()) * body.leading();
    canvas.ensure(height);
    float top   = canvas.y;
    int   color = stateColor(state);
    canvas.fill(canvas.left,
                top - height,
                canvas.width(),
                height,
                stateTint(state));
    canvas.fill(canvas.left,
                top - height,
                BAR,
                height,
                color);
    float cursor = top - PAD_Y - body.leading();
    canvas.text(labelText,
                bodyBold.color(color),
                canvas.left + BAR + PAD_X,
                cursor + body.descent());
    for (String line : lines) {
      canvas.text(line,
                  body,
                  canvas.left + BAR + PAD_X + labelWidth,
                  cursor + body.descent());
      cursor -= body.leading();
    }
    canvas.y = top - height;
  }

  // -- helpers ------------------------------------------------------------------------------------

  private String severityLabel(Severity severity) {
    return t.label("report.severity." + severity.name());
  }

  private static String stateKey(int state) {
    return switch (state) {
      case 0 -> "valid";
      case 1 -> "warnings";
      default -> "invalid";
    };
  }

  private static int stateColor(int state) {
    return switch (state) {
      case 0 -> OK;
      case 1 -> WARN;
      default -> ERROR;
    };
  }

  private static int stateTint(int state) {
    return switch (state) {
      case 0 -> OK_TINT;
      case 1 -> WARN_TINT;
      default -> ERROR_TINT;
    };
  }

  private int color(Severity severity) {
    return switch (severity) {
      case FATAL -> FATAL;
      case ERROR -> ERROR;
      case WARNING -> WARN;
      case INFO -> accent;
    };
  }

  private static int tint(Severity severity) {
    return switch (severity) {
      case FATAL, ERROR -> ERROR_TINT;
      case WARNING -> WARN_TINT;
      case INFO -> INFO_TINT;
    };
  }

  /**
   * One line of a finding block: label and value, either may be null; without label style the value
   * starts at the left of the block.
   */
  private record BlockLine(String label, Style labelStyle, String value, Style valueStyle) {
    float leading() {
      return valueStyle.leading();
    }
  }
}

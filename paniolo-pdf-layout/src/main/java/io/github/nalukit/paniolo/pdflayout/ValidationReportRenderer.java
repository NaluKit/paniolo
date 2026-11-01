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
import io.github.nalukit.paniolo.spi.PdfLayoutSupport.Report;
import io.github.nalukit.paniolo.validation.Severity;
import io.github.nalukit.paniolo.validation.ValidationMessage;
import org.apache.pdfbox.pdmodel.PDDocument;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import static io.github.nalukit.paniolo.pdflayout.Canvas.MM;

/**
 * Lays out a validation report: validated document, rule releases, overall result, counts per
 * severity and stage, and every finding with its complete rule identifier in a table that repeats
 * its header on each page. The findings are the ones of the machine-readable report, in the order
 * of {@code ValidationResult.messages()}.
 */
final class ValidationReportRenderer
    extends DocumentRenderer {
  private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'")
                                                                 .withZone(ZoneOffset.UTC);

  private final Report report;

  ValidationReportRenderer(Report report,
                           PdfLayout layout) {
    super(layout);
    this.report = report;
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
    var result = report.result();
    var rows   = new ArrayList<String[]>();
    fact(rows,
         "report.result",
         t.label(result.isValid() ?
                 "report.valid" :
                 "report.invalid"));
    fact(rows,
         "report.validatedAt",
         TIME.format(report.validatedAt()
                           .truncatedTo(ChronoUnit.SECONDS)));
    if (result.duration() != null) {
      fact(rows,
           "report.duration",
           result.duration()
                 .toMillis() + " ms");
    }
    return rows;
  }

  @Override
  void content(PDDocument document) {
    var result = report.result();
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

    section("report.summary");
    var severities = new ArrayList<Row>();
    for (Severity severity : List.of(Severity.FATAL,
                                     Severity.ERROR,
                                     Severity.WARNING,
                                     Severity.INFO)) {
      severities.add(row(severity.name(),
                         String.valueOf(result.count(severity))));
    }
    table(List.of(new Column(40 * MM,
                             Align.LEFT),
                  new Column(canvas.width() - 40 * MM,
                             Align.LEFT)),
          header("report.severity",
                 "report.count"),
          severities);
    var stages = new LinkedHashMap<ValidationMessage.Stage, int[]>();
    for (var message : result.messages()) {
      int[] counts = stages.computeIfAbsent(message.stage(),
                                            stage -> new int[2]);
      counts[0]++;
      if (message.severity() == Severity.FATAL || message.severity() == Severity.ERROR) {
        counts[1]++;
      }
    }
    if (!stages.isEmpty()) {
      var rows = new ArrayList<Row>();
      stages.forEach((stage, counts) -> rows.add(row(stage.name(),
                                                     counts[0] +
                                                     " (" +
                                                     t.label(counts[1] == 0 ?
                                                             "report.valid" :
                                                             "report.invalid") +
                                                     ")")));
      table(List.of(new Column(40 * MM,
                               Align.LEFT),
                    new Column(canvas.width() - 40 * MM,
                               Align.LEFT)),
            header("report.stage",
                   "report.count"),
            rows);
    }

    section("report.findings");
    if (result.messages()
              .isEmpty()) {
      notes(List.of(t.label("report.none")));
      return;
    }
    var   ruleStyle = small.color(TEXT);
    // The rule column is as wide as the longest identifier, so no identifier is broken.
    float ruleWidth = columnHead.width(t.label("report.rule"));
    for (var message : result.messages()) {
      if (message.ruleId() != null) {
        ruleWidth = Math.max(ruleWidth,
                             ruleStyle.width(message.ruleId()));
      }
    }
    ruleWidth = Math.min(ruleWidth + 2 * Canvas.CELL_PADDING_X + 1,
                         70 * MM);
    float numberWidth   = 11 * MM;
    float severityWidth = 18 * MM;
    float stageWidth    = 24 * MM;
    var   rows          = new ArrayList<Row>();
    int   number        = 0;
    for (var message : result.messages()) {
      number++;
      var text = new Cell().add(message.message(),
                                small.color(TEXT));
      if (message.location() != null && !message.location()
                                                .isBlank()) {
        text.add(t.label("report.location") + " " + message.location(),
                 tiny);
      }
      rows.add(new Row(List.of(new Cell().add(String.valueOf(number),
                                              small),
                               new Cell().add(message.severity()
                                                     .name(),
                                              message.severity() == Severity.FATAL || message.severity() == Severity.ERROR ?
                                              bodyBold.color(TEXT) :
                                              small.color(TEXT)),
                               new Cell().add(message.stage()
                                                     .name(),
                                              small),
                               new Cell().add(message.ruleId(),
                                              ruleStyle),
                               text),
                       false));
    }
    table(List.of(new Column(numberWidth,
                             Align.RIGHT),
                  new Column(severityWidth,
                             Align.LEFT),
                  new Column(stageWidth,
                             Align.LEFT),
                  new Column(ruleWidth,
                             Align.LEFT),
                  new Column(canvas.width() - numberWidth - severityWidth - stageWidth - ruleWidth,
                             Align.LEFT)),
          header("report.no",
                 "report.severity",
                 "report.stage",
                 "report.rule",
                 "report.message"),
          rows);
    notes(List.of(t.label("report.note")));
  }

  private Row row(String label,
                  String value) {
    return new Row(List.of(new Cell().add(label,
                                          small.color(TEXT)),
                           new Cell().add(value,
                                          small.color(TEXT))),
                   false);
  }
}

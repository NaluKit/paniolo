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
import io.github.nalukit.paniolo.validation.PdfaStatistics;
import io.github.nalukit.paniolo.validation.SchematronStatistics;
import io.github.nalukit.paniolo.validation.Severity;
import io.github.nalukit.paniolo.validation.ValidationMessage;
import io.github.nalukit.paniolo.validation.ValidationResult;
import io.github.nalukit.paniolo.validation.ValidationStatistics;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.verapdf.gf.foundry.VeraGreenfieldFoundryProvider;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The validation report as PDF: verdict for errors, warnings and no findings, page breaks,
 * complete rule identifiers, and the same findings as the XML report.
 */
class ValidationReportPdfTest {
  private static final Paniolo PANIOLO = Paniolo.create();

  @BeforeAll
  static void verapdf() {
    VeraGreenfieldFoundryProvider.initialise();
  }

  private static final List<String> RULES = List.of("BR-DE-15",
                                                    "PEPPOL-EN16931-R120",
                                                    "PANIOLO-CALC-ALLOWANCE-CHARGE",
                                                    "BR-CO-10",
                                                    "FX-NO-INVOICE",
                                                    "veraPDF-6.2.11.4.1-1");

  /**
   * 240 findings of all severities and stages, with messages over several lines and long locations.
   */
  static ValidationResult longResult() {
    var messages = new ArrayList<ValidationMessage>();
    var stages   = ValidationMessage.Stage.values();
    var levels   = Severity.values();
    for (int i = 0; i < 240; i++) {
      String rule = RULES.get(i % RULES.size());
      messages.add(new ValidationMessage(rule,
                                         levels[i % levels.length],
                                         "Finding " +
                                         (i + 1) +
                                         ": " +
                                         "the element does not satisfy the rule ".repeat(1 + i % 4) +
                                         "(end of message " +
                                         (i + 1) +
                                         ")",
                                         i % 3 == 0 ?
                                         "" :
                                         "/Q{urn:un:unece:uncefact:data:standard:CrossIndustryInvoice:100}CrossIndustryInvoice[1]" +
                                         "/Q{urn:un:unece:uncefact:data:standard:CrossIndustryInvoice:100}SupplyChainTradeTransaction[1]/line[" +
                                         (i + 1) +
                                         "]",
                                         stages[i % stages.length]));
    }
    return new ValidationResult("EN16931 XRechnung (CII)",
                                messages);
  }

  private static List<String> pages(byte[] pdf) {
    try (var document = Loader.loadPDF(pdf)) {
      var pages    = new ArrayList<String>();
      var stripper = new PDFTextStripper();
      for (int page = 1; page <= document.getNumberOfPages(); page++) {
        stripper.setStartPage(page);
        stripper.setEndPage(page);
        pages.add(stripper.getText(document));
      }
      return pages;
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  @Test
  void longReportBreaksPagesAndKeepsEveryFinding()
      throws Exception {
    var    result   = longResult();
    byte[] document = "<invoice/>".getBytes(StandardCharsets.UTF_8);
    byte[] pdf      = PANIOLO.toReportPdf(result,
                                          "inbox/R-2026-001.xml",
                                          document,
                                          PdfLayout.of(Locale.ENGLISH));
    assertEquals(List.of(),
                 PdfLayoutTest.pdfaFailures(pdf));
    var pages = pages(pdf);
    assertTrue(pages.size() > 5,
               "pages: " + pages.size());
    String all = String.join("\n",
                             pages);

    // Header: document, checksum, profile, rule releases, result, counts.
    assertTrue(all.contains("Validation report – inbox/R-2026-001.xml"),
               all);
    assertTrue(all.contains(HexFormat.of()
                                     .formatHex(MessageDigest.getInstance("SHA-256")
                                                             .digest(document))),
               all);
    assertTrue(all.contains("EN16931 XRechnung (CII)"),
               all);
    assertTrue(all.contains("KoSIT XRechnung"),
               all);
    assertTrue(all.contains("invalid"),
               all);
    assertTrue(Pattern.compile("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2} UTC")
                      .matcher(all)
                      .find(),
               all);
    assertTrue(all.contains("not conform"),
               all);
    assertTrue(all.contains("It is recommended to reject the document."),
               all);
    for (Severity severity : Severity.values()) {
      assertTrue(all.contains(result.count(severity) + "\n"),
                 severity + " in " + all);
      assertTrue(all.contains("(" + severity.name() + ")"),
                 severity + " in " + all);
    }
    // Every stage of the findings in the overview.
    for (var stage : ValidationMessage.Stage.values()) {
      assertTrue(all.contains(stage.name()),
                 stage + " in " + all);
    }

    // Every finding grouped by stage like the XML report, numbered through: header with number and
    // severity, then the complete rule identifier.
    var xml = Pattern.compile("<message[^>]*?\\srule=\"([^\"]+)\"")
                     .matcher(result.toXml())
                     .results()
                     .map(match -> match.group(1))
                     .toList();
    var byStage = new LinkedHashMap<ValidationMessage.Stage, List<ValidationMessage>>();
    for (var message : result.messages()) {
      byStage.computeIfAbsent(message.stage(),
                              stage -> new ArrayList<>())
             .add(message);
    }
    var grouped = byStage.values()
                         .stream()
                         .flatMap(List::stream)
                         .toList();
    assertEquals(xml,
                 grouped.stream()
                        .map(ValidationMessage::ruleId)
                        .toList());
    var lines = all.lines()
                   .toList();
    int from = 0;
    for (int i = 0; i < grouped.size(); i++) {
      var    message = grouped.get(i);
      String header  = (i + 1) + " · ";
      String code    = "(" + message.severity() + ")";
      int    found   = -1;
      for (int l = from; l < lines.size() - 1; l++) {
        if (lines.get(l)
                 .startsWith(header) &&
            lines.get(l)
                 .endsWith(code)) {
          found = l;
          break;
        }
      }
      assertTrue(found >= 0,
                 () -> "finding " + header + code + " not found");
      assertEquals("Rule " + message.ruleId(),
                   lines.get(found + 1),
                   "rule of finding " + (i + 1));
      from = found + 1;
    }
    // Messages are wrapped, not cut: every message is in the report completely.
    String flowing = all.replaceAll("\\s+",
                                    " ");
    for (var message : result.messages()) {
      assertTrue(flowing.contains(message.message()),
                 message.message());
    }

    // Every page is numbered.
    for (int p = 0; p < pages.size(); p++) {
      assertTrue(pages.get(p)
                      .contains("Page " + (p + 1) + " of " + pages.size()),
                 "number of page " + (p + 1));
    }
  }

  @Test
  void rendersARealValidationInGerman() {
    byte[] xml = "<Invoice xmlns=\"urn:oasis:names:specification:ubl:schema:xsd:Invoice-2\"/>".getBytes(StandardCharsets.UTF_8);
    var result = PANIOLO.validate(xml);
    byte[] pdf = PANIOLO.toReportPdf(result,
                                     "leer.xml",
                                     xml);
    assertEquals(List.of(),
                 PdfLayoutTest.pdfaFailures(pdf));
    String all = String.join("\n",
                             pages(pdf));
    assertTrue(all.contains("Prüfbericht – leer.xml"),
               all);
    assertTrue(all.contains("ungültig"),
               all);
    assertTrue(all.contains("nicht konform"),
               all);
    assertTrue(all.contains("XSD – XML-Schema"),
               all);
    for (var message : result.messages()) {
      assertTrue(all.contains(message.ruleId()),
                 message.ruleId());
    }
  }

  @Test
  void reportWithoutFindings() {
    String all = String.join("\n",
                             pages(PANIOLO.toReportPdf(new ValidationResult("Factur-X EN16931",
                                                                            List.of()),
                                                       null,
                                                       null,
                                                       PdfLayout.of(Locale.ENGLISH))))
                       .replaceAll("\\s+",
                                   " ");
    assertTrue(all.contains("No findings."),
               all);
    assertTrue(all.contains("The validated document conforms to the rules applied. No errors and no warnings were found."),
               all);
    assertTrue(all.contains("valid"),
               all);
    assertFalse(all.contains("invalid"),
                all);
    assertTrue(all.contains("Factur-X 1."),
               all);
  }

  @Test
  void warningsOnlyAreConformWithWarnings() {
    var result = new ValidationResult("EN16931 XRechnung (CII)",
                                      List.of(new ValidationMessage("BR-DE-21",
                                                                    Severity.WARNING,
                                                                    "Die Spezifikationskennung sollte der XRechnung entsprechen.",
                                                                    "/rsm:CrossIndustryInvoice[1]",
                                                                    ValidationMessage.Stage.SCHEMATRON),
                                              new ValidationMessage("PANIOLO-INFO",
                                                                    Severity.INFO,
                                                                    "Hinweis zur Rechnung.",
                                                                    "",
                                                                    ValidationMessage.Stage.CALCULATION)));
    byte[] pdf = PANIOLO.toReportPdf(result,
                                     "warnung.xml",
                                     null);
    assertEquals(List.of(),
                 PdfLayoutTest.pdfaFailures(pdf));
    String all = String.join("\n",
                             pages(pdf))
                       .replaceAll("\\s+",
                                   " ");
    assertTrue(all.contains("enthält aber Warnungen (1), die geprüft werden sollten."),
               all);
    assertTrue(all.contains("konform, Warnungen"),
               all);
    assertTrue(all.contains("gültig"),
               all);
    assertFalse(all.contains("ungültig"),
                all);
    assertTrue(all.contains("1 · Warnung (WARNING)"),
               all);
    assertTrue(all.contains("2 · Hinweis (INFO)"),
               all);
  }

  @Test
  void showsStatisticsOfTheValidation() {
    var result = new ValidationResult("Factur-X EN16931",
                                      List.of()).withStatistics(new ValidationStatistics(List.of(new SchematronStatistics("FACTUR-X_EN16931.xslt",
                                                                                                                                12,
                                                                                                                                345,
                                                                                                                                0,
                                                                                                                                2)),
                                                                                         List.of(new PdfaStatistics("3b",
                                                                                                                    "PDF/A-3B",
                                                                                                                    true,
                                                                                                                    1234,
                                                                                                                    0,
                                                                                                                    Map.of()))));
    String all = String.join("\n",
                             pages(PANIOLO.toReportPdf(result,
                                                       "factur-x.pdf",
                                                       new byte[] { 1, 2, 3 },
                                                       PdfLayout.of(Locale.ENGLISH))));
    assertTrue(all.contains("SCHEMATRON"),
               all);
    assertTrue(all.contains("PDF_A"),
               all);
    assertTrue(all.contains("FACTUR-X_EN16931.xslt 12 345 0 2"),
               all);
    assertTrue(all.contains("1234 0 conform"),
               all);
  }

  /**
   * Long German words, URLs, and paths without spaces keep every character; a break inside them only
   * adds whitespace (Mustang issue #562).
   */
  @Test
  void keepsEveryCharacterOfLongTexts() {
    var texts = List.of("Die Rechnungsempfängerumsatzsteueridentifikationsnummernprüfung schlägt fehl; Größe, Maß und Straße bleiben erhalten.",
                        "Siehe https://www.xrechnung-bund.de/regelwerk/version-3.0.2/geschaeftsregeln/BR-DE-15-leitweg-identifikationsnummer-beschreibung.html",
                        "/Q{urn:un:unece:uncefact:data:standard:CrossIndustryInvoice:100}CrossIndustryInvoice[1]/Q{urn:un:unece:uncefact:data:standard:ReusableAggregateBusinessInformationEntity:100}ApplicableHeaderTradeSettlement[1]");
    var messages = texts.stream()
                        .map(text -> new ValidationMessage("BR-DE-15",
                                                           Severity.ERROR,
                                                           text,
                                                           text.startsWith("/") ?
                                                           text :
                                                           "",
                                                           ValidationMessage.Stage.SCHEMATRON))
                        .toList();
    String all = String.join("\n",
                             pages(PANIOLO.toReportPdf(new ValidationResult("EN16931 XRechnung (CII)",
                                                                            messages),
                                                       null,
                                                       null,
                                                       PdfLayout.of(Locale.GERMAN))))
                       .replaceAll("\\s",
                                   "");
    for (String text : texts) {
      assertTrue(all.contains(text.replaceAll("\\s",
                                              "")),
                 () -> text + " in " + all);
    }
  }
}

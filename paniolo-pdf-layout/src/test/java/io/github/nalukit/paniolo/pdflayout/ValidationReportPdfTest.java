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
import io.github.nalukit.paniolo.validation.Severity;
import io.github.nalukit.paniolo.validation.ValidationMessage;
import io.github.nalukit.paniolo.validation.ValidationResult;
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
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The validation report as PDF: page breaks, complete rule identifiers, and the same findings as
 * the XML report.
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
    for (Severity severity : Severity.values()) {
      assertTrue(all.contains(severity.name() + " " + result.count(severity)),
                 severity + " in " + all);
    }

    // Every finding in the order of the XML report: number, severity, stage, complete rule id.
    var xml = Pattern.compile("<message[^>]*?\\srule=\"([^\"]+)\"")
                     .matcher(result.toXml())
                     .results()
                     .toList();
    assertEquals(result.messages()
                       .size(),
                 xml.size());
    var lines = all.lines()
                   .toList();
    int from = 0;
    for (int i = 0; i < result.messages()
                              .size(); i++) {
      var    message = result.messages()
                             .get(i);
      String row     = (i + 1) + " " + message.severity() + " " + message.stage() + " " + message.ruleId();
      int    found   = -1;
      for (int l = from; l < lines.size(); l++) {
        if (lines.get(l)
                 .startsWith(row + " ")) {
          found = l;
          break;
        }
      }
      assertTrue(found >= 0,
                 () -> "row " + row + " not found");
      from = found + 1;
    }
    // The XML report groups the findings by stage; within each stage the order is the same.
    var byStage = new java.util.LinkedHashMap<ValidationMessage.Stage, List<String>>();
    for (var message : result.messages()) {
      byStage.computeIfAbsent(message.stage(),
                              stage -> new ArrayList<>())
             .add(message.ruleId());
    }
    assertEquals(byStage.values()
                        .stream()
                        .flatMap(List::stream)
                        .toList(),
                 xml.stream()
                    .map(match -> match.group(1))
                    .toList());
    // Messages are wrapped, not cut: every message is in the report completely.
    String flowing = all.replaceAll("\\s+",
                                    " ");
    for (var message : result.messages()) {
      assertTrue(flowing.contains(message.message()),
                 message.message());
    }

    // The table header is repeated on every page with findings, and every page is numbered.
    int pagesWithRows = 0;
    for (int p = 0; p < pages.size(); p++) {
      String page = pages.get(p);
      if (Pattern.compile("(?m)^\\d+ (FATAL|ERROR|WARNING|INFO) ")
                 .matcher(page)
                 .find()) {
        pagesWithRows++;
        assertTrue(page.contains("No. Severity Stage Rule Message"),
                   "header on page " + (p + 1));
      }
      assertTrue(page.contains("Page " + (p + 1) + " of " + pages.size()),
                 "number of page " + (p + 1));
    }
    assertTrue(pagesWithRows >= pages.size() - 1,
               "pages with findings: " + pagesWithRows);
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
                                                       PdfLayout.of(Locale.ENGLISH))));
    assertTrue(all.contains("No findings."),
               all);
    assertTrue(all.contains("valid"),
               all);
    assertTrue(all.contains("Factur-X 1."),
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

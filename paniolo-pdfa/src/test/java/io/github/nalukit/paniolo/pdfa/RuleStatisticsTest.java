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
package io.github.nalukit.paniolo.pdfa;

import io.github.nalukit.paniolo.Paniolo;
import io.github.nalukit.paniolo.config.FacturXProfile;
import io.github.nalukit.paniolo.config.InvoiceSyntax;
import io.github.nalukit.paniolo.model.common.value.InvoiceTypeCode;
import io.github.nalukit.paniolo.validation.PdfaStatistics;
import io.github.nalukit.paniolo.validation.SchematronStatistics;
import io.github.nalukit.paniolo.validation.ValidationMessage;
import io.github.nalukit.paniolo.validation.ValidationResult;
import org.junit.jupiter.api.Test;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;
import java.io.StringReader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Rule execution statistics of the facade: counted from the official Schematron and veraPDF runs,
 * without changing the findings.
 */
class RuleStatisticsTest {
  private static final Paniolo PANIOLO    = Paniolo.create();
  private static final Paniolo STATISTICS = Paniolo.builder()
                                                   .ruleStatistics(true)
                                                   .build();

  private static String xml() {
    return PANIOLO.toXml(FacturXInvoices.invoice(FacturXProfile.EN16931,
                                                 InvoiceTypeCode.CODE_380)
                                        .build(),
                         InvoiceSyntax.CII);
  }

  private static void assertConformsToReportSchema(ValidationResult result)
      throws Exception {
    var schema = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
                              .newSchema(ValidationResult.class.getResource(ValidationResult.REPORT_SCHEMA));
    String report = result.toXml();
    assertTrue(report.contains("<statistics>"),
               report);
    assertDoesNotThrow(() -> schema.newValidator()
                                   .validate(new StreamSource(new StringReader(report))),
                       report);
  }

  @Test
  void countsTheSchematronRunsWithoutChangingTheFindings()
      throws Exception {
    // Without the option nothing is collected; the stylesheets drop fired rules as before.
    assertNull(PANIOLO.validate(xml())
                      .statistics());

    String invalid = xml().replaceAll("<ram:LineTotalAmount>([^<]*)</ram:LineTotalAmount>",
                                      "<ram:LineTotalAmount>999.99</ram:LineTotalAmount>");
    for (String document : List.of(xml(),
                                   invalid)) {
      var plain  = PANIOLO.validate(document);
      var result = STATISTICS.validate(document);
      assertEquals(plain.messages(),
                   result.messages());
      var stylesheets = result.statistics()
                              .schematron();
      assertFalse(stylesheets.isEmpty(),
                  result::toString);
      for (SchematronStatistics stylesheet : stylesheets) {
        assertTrue(stylesheet.activePatterns() > 0 && stylesheet.firedRules() > 0,
                   stylesheet::toString);
      }
      // Every Schematron finding is one failed assert or successful report, apart from the notes
      // of Paniolo about the rule selection, e.g. XR-NOT-STATED for plain EN 16931.
      assertEquals(result.messages(ValidationMessage.Stage.SCHEMATRON)
                         .stream()
                         .filter(message -> !message.ruleId()
                                                    .startsWith("XR-"))
                         .count(),
                   stylesheets.stream()
                              .mapToInt(stylesheet -> stylesheet.failedAsserts() + stylesheet.successfulReports())
                              .sum(),
                   result::toString);
      assertTrue(result.statistics()
                       .pdfa()
                       .isEmpty());
      assertConformsToReportSchema(result);
    }
    assertFalse(STATISTICS.validate(invalid)
                          .isValid());
  }

  @Test
  void countsThePdfaChecksOfAHybridInvoice()
      throws Exception {
    byte[] pdf = PANIOLO.toFacturX(FacturXInvoices.invoice(FacturXProfile.EN16931,
                                                           InvoiceTypeCode.CODE_380)
                                                  .build(),
                                   TestPdfs.pdfA2b(),
                                   FacturXProfile.EN16931);
    var result = STATISTICS.validate(pdf);
    assertTrue(result.isValid(),
               result::toString);
    assertEquals(1,
                 result.statistics()
                       .pdfa()
                       .size());
    PdfaStatistics pdfa = result.statistics()
                                .pdfa()
                                .getFirst();
    assertEquals("3b",
                 pdfa.flavour());
    assertTrue(pdfa.compliant() && pdfa.checks() > 0 && pdfa.failedChecks() == 0 && pdfa.failedRules()
                                                                                         .isEmpty(),
               pdfa::toString);
    // The embedded XML was validated as well.
    assertTrue(result.statistics()
                     .firedRules() > 0);
    assertConformsToReportSchema(result);
  }

  @Test
  void countsEveryFailureOfAPdfaRule()
      throws Exception {
    // No PDF/A identification: checked as PDF/A-3b, which fails.
    var result = STATISTICS.validate(TestPdfs.plainPdf());
    var pdfa = result.statistics()
                     .pdfa()
                     .getFirst();
    assertFalse(pdfa.compliant());
    assertTrue(pdfa.failedChecks() > 0,
               pdfa::toString);
    assertEquals(pdfa.failedChecks(),
                 pdfa.failedRules()
                     .values()
                     .stream()
                     .mapToInt(Integer::intValue)
                     .sum());
    // The findings name each failed rule once; the statistics count its occurrences.
    for (String rule : pdfa.failedRules()
                           .keySet()) {
      assertTrue(result.messages(ValidationMessage.Stage.PDF_A)
                       .stream()
                       .anyMatch(message -> ("PDFA-3B-" + rule).equals(message.ruleId())),
                 () -> rule + " in " + result.messages());
    }
    assertConformsToReportSchema(result);
  }
}

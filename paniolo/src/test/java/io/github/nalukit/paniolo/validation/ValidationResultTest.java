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
package io.github.nalukit.paniolo.validation;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;
import java.io.StringReader;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValidationResultTest {
  private static final ValidationResult RESULT = new ValidationResult("Factur-X EN16931",
                                                                      List.of(new ValidationMessage("FX-PRODUCER",
                                                                                                    Severity.INFO,
                                                                                                    "Produced by ACME",
                                                                                                    "XMP pdf:Producer",
                                                                                                    ValidationMessage.Stage.HYBRID),
                                                                              new ValidationMessage("BR-CO-10",
                                                                                                    Severity.FATAL,
                                                                                                    "Sum of line net amounts <> total",
                                                                                                    "/rsm:CrossIndustryInvoice",
                                                                                                    ValidationMessage.Stage.SCHEMATRON),
                                                                              new ValidationMessage("PEPPOL-R008",
                                                                                                    Severity.WARNING,
                                                                                                    "",
                                                                                                    null,
                                                                                                    ValidationMessage.Stage.SCHEMATRON),
                                                                              new ValidationMessage(null,
                                                                                                    Severity.ERROR,
                                                                                                    "PDF/A violation",
                                                                                                    "",
                                                                                                    ValidationMessage.Stage.PDF_A)),
                                                                      Duration.ofNanos(42_345_678));

  private static Document parse(String xml)
      throws Exception {
    var factory = DocumentBuilderFactory.newDefaultInstance();
    factory.setNamespaceAware(true);
    return factory.newDocumentBuilder()
                  .parse(new org.xml.sax.InputSource(new StringReader(xml)));
  }

  private static List<Element> children(Element parent,
                                        String name) {
    var nodes = parent.getElementsByTagNameNS(ValidationResult.REPORT_NAMESPACE,
                                              name);
    return java.util.stream.IntStream.range(0,
                                            nodes.getLength())
                                     .mapToObj(i -> (Element) nodes.item(i))
                                     .toList();
  }

  @Test
  void reportConformsToSchema()
      throws Exception {
    var schemaUrl = ValidationResult.class.getResource(ValidationResult.REPORT_SCHEMA);
    assertNotNull(schemaUrl);
    var factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
    var schema  = factory.newSchema(schemaUrl);
    var statistics = RESULT.withStatistics(new ValidationStatistics(List.of(new SchematronStatistics("EN16931-CII-validation.xslt",
                                                                                                 3,
                                                                                                 1200,
                                                                                                 1,
                                                                                                 1)),
                                                                     List.of(new PdfaStatistics("3b",
                                                                                                "PDF/A-3B validation profile",
                                                                                                false,
                                                                                                5000,
                                                                                                3,
                                                                                                java.util.Map.of("6.2.11.4.1-1",
                                                                                                                 3)))));
    for (var result : List.of(RESULT,
                              statistics,
                              new ValidationResult("",
                                                   List.of()))) {
      String xml = result.toXml();
      assertDoesNotThrow(() -> schema.newValidator()
                                     .validate(new StreamSource(new StringReader(xml))),
                         xml);
    }
  }

  @Test
  void reportStatesTheStatisticsAfterTheSummary()
      throws Exception {
    assertFalse(RESULT.toXml()
                      .contains("<statistics"));
    var withStatistics = RESULT.withStatistics(ValidationStatistics.merge(null,
                                                                          new ValidationStatistics(List.of(new SchematronStatistics("a.xslt",
                                                                                                                                    1,
                                                                                                                                    7,
                                                                                                                                    0,
                                                                                                                                    0)),
                                                                                                   List.of()),
                                                                          new ValidationStatistics(List.of(new SchematronStatistics("b.xslt",
                                                                                                                                    2,
                                                                                                                                    5,
                                                                                                                                    1,
                                                                                                                                    0)),
                                                                                                   List.of())));
    assertEquals(12,
                 withStatistics.statistics()
                               .firedRules());
    var report     = parse(withStatistics.toXml()).getDocumentElement();
    var statistics = children(report,
                              "statistics").getFirst();
    var previous = statistics.getPreviousSibling();
    while (!(previous instanceof Element)) {
      previous = previous.getPreviousSibling();
    }
    assertEquals("summary",
                 previous.getLocalName());
    var stylesheets = children(statistics,
                               "schematron");
    assertEquals(List.of("a.xslt",
                         "b.xslt"),
                 stylesheets.stream()
                            .map(element -> element.getAttribute("stylesheet"))
                            .toList());
    assertEquals("5",
                 stylesheets.get(1)
                            .getAttribute("firedRules"));
    // Statistics survive the copies of the facade.
    assertEquals(withStatistics.statistics(),
                 withStatistics.withDuration(Duration.ZERO)
                               .withMessages(List.of())
                               .statistics());
    assertEquals(null,
                 ValidationStatistics.merge(null,
                                            null));
  }

  @Test
  void reportHasMetadataAndSummary()
      throws Exception {
    Instant before = Instant.now()
                            .minusSeconds(1);
    var report = parse(RESULT.toXml()).getDocumentElement();
    assertTrue(report.getAttribute("generator")
                     .startsWith("Paniolo "),
               report.getAttribute("generator"));
    assertFalse(report.getAttribute("generator")
                      .contains("${"),
                report.getAttribute("generator"));
    assertTrue(Instant.parse(report.getAttribute("created"))
                      .isAfter(before));
    assertEquals("PT0.042S",
                 report.getAttribute("duration"));
    assertEquals("Factur-X EN16931",
                 children(report,
                          "profile").getFirst()
                                    .getTextContent());
    var summary = children(report,
                           "summary").getFirst();
    assertEquals("false",
                 summary.getAttribute("valid"));
    assertEquals(List.of("1",
                         "1",
                         "1",
                         "1"),
                 List.of(summary.getAttribute("fatals"),
                         summary.getAttribute("errors"),
                         summary.getAttribute("warnings"),
                         summary.getAttribute("infos")));
  }

  @Test
  void reportGroupsFindingsByStageInReportedOrder()
      throws Exception {
    var report = parse(RESULT.toXml()).getDocumentElement();
    var stages = children(report,
                          "stage");
    assertEquals(List.of("HYBRID:true",
                         "SCHEMATRON:false",
                         "PDF_A:false"),
                 stages.stream()
                       .map(stage -> stage.getAttribute("name") + ":" + stage.getAttribute("valid"))
                       .toList());
    var schematron = children(stages.get(1),
                              "message");
    assertEquals(List.of("BR-CO-10",
                         "PEPPOL-R008"),
                 schematron.stream()
                           .map(message -> message.getAttribute("rule"))
                           .toList());
    assertEquals("FATAL",
                 schematron.getFirst()
                           .getAttribute("severity"));
    assertEquals("/rsm:CrossIndustryInvoice",
                 schematron.getFirst()
                           .getAttribute("location"));
    // Blank texts are written as "-", missing rules and locations are left out.
    assertEquals("-",
                 schematron.get(1)
                           .getTextContent());
    assertFalse(schematron.get(1)
                          .hasAttribute("location"));
    assertFalse(children(stages.get(2),
                         "message").getFirst()
                                   .hasAttribute("rule"));
  }

  @Test
  void reportOfValidResultWithoutDuration()
      throws Exception {
    var report = parse(new ValidationResult("Test",
                                            List.of()).toXml()).getDocumentElement();
    assertFalse(report.hasAttribute("duration"));
    assertEquals("true",
                 children(report,
                          "summary").getFirst()
                                    .getAttribute("valid"));
    assertTrue(children(report,
                        "stage").isEmpty());
  }
}

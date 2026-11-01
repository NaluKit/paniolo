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
package io.github.nalukit.paniolo;

import io.github.nalukit.paniolo.config.InvoiceSyntax;
import io.github.nalukit.paniolo.model.common.value.InvoiceTypeCode;
import io.github.nalukit.paniolo.validation.Severity;
import io.github.nalukit.paniolo.validation.ValidationMessage;
import io.github.nalukit.paniolo.validation.ValidationResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The calculation corpus in {@code src/test/resources/calculation-corpus}: for each counterexample
 * and target, the official findings, the findings of the calculation check, their severity, and
 * whether an official rule already reports the same ({@code expected.csv}).
 */
class CalculationCorpusTest {
  private static final Paniolo PANIOLO = Paniolo.builder()
                                                .calculationCheck(true)
                                                .build();
  private static final Paniolo OFFICIAL = Paniolo.create();

  private static byte[] resource(String name) {
    try (InputStream input = CalculationCorpusTest.class.getResourceAsStream("/calculation-corpus/" + name)) {
      if (input == null) {
        throw new IllegalStateException("Missing corpus file " + name);
      }
      return input.readAllBytes();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  static Stream<Arguments> corpus() {
    return new String(resource("expected.csv"),
                      StandardCharsets.UTF_8).lines()
                                             .filter(line -> !line.isBlank() && !line.startsWith("#"))
                                             .map(line -> Arrays.copyOf(line.split(";",
                                                                                   -1),
                                                                        3))
                                             .map(columns -> Arguments.of(columns[0],
                                                                          columns[1],
                                                                          columns[2]));
  }

  static String official(ValidationResult result) {
    return result.messages()
                 .stream()
                 .filter(m -> m.stage() != ValidationMessage.Stage.CALCULATION)
                 .filter(m -> m.severity() != Severity.INFO)
                 .map(m -> m.ruleId() + ":" + m.severity())
                 .distinct()
                 .sorted()
                 .collect(Collectors.joining(" "));
  }

  static String calculation(ValidationResult result) {
    return result.messages(ValidationMessage.Stage.CALCULATION)
                 .stream()
                 .sorted(Comparator.comparing(ValidationMessage::ruleId)
                                   .thenComparing(ValidationMessage::location))
                 .map(m -> m.ruleId() + "@" + m.location() + ":" + m.severity())
                 .collect(Collectors.joining(" "));
  }

  private static String target(String file) {
    return file.substring(file.indexOf('.') + 1,
                          file.lastIndexOf('.'));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("corpus")
  void reportsTheExpectedFindings(String file,
                                  String expectedOfficial,
                                  String expectedCalculation) {
    byte[] xml    = resource(file);
    var    result = CalculationCorpus.validate(PANIOLO,
                                               xml,
                                               target(file));
    assertEquals(expectedOfficial,
                 official(result),
                 () -> result.messages()
                             .toString());
    assertEquals(expectedCalculation,
                 calculation(result),
                 () -> result.messages()
                             .toString());
    // The calculation check only adds findings: the official result and the validity stay the same.
    var official = CalculationCorpus.validate(OFFICIAL,
                                              xml,
                                              target(file));
    assertEquals(official.messages(),
                 result.messages()
                       .stream()
                       .filter(m -> m.stage() != ValidationMessage.Stage.CALCULATION)
                       .toList());
    assertEquals(official.isValid(),
                 result.isValid());
    for (var finding : result.messages(ValidationMessage.Stage.CALCULATION)) {
      assertTrue(finding.ruleId()
                        .startsWith("PANIOLO-CALC-"),
                 finding::toString);
      // A duplicate names the official rule; an additional finding is a warning.
      assertEquals(finding.severity() == Severity.INFO,
                   finding.message()
                          .contains("also reported by") || finding.message()
                                                                  .contains("within the rounding"),
                   finding::toString);
    }
  }

  @Test
  void corpusFilesAreUpToDate() {
    // The committed files are the output of CalculationCorpus; regenerate them with its main method.
    for (String name : CalculationCorpus.CASES.keySet()) {
      for (String target : CalculationCorpus.TARGETS) {
        assertArrayEquals(CalculationCorpus.xml(OFFICIAL,
                                                name,
                                                target),
                          resource(name + "." + target + ".xml"),
                          name + "." + target);
      }
    }
    assertEquals(CalculationCorpus.CASES.size() * CalculationCorpus.TARGETS.size(),
                 corpus().count());
  }

  @Test
  void addsNothingToCorrectInvoices() {
    var invoice = TestInvoices.invoice(InvoiceTypeCode.CODE_380,
                                       "R-1");
    for (var syntax : InvoiceSyntax.values()) {
      var result = PANIOLO.validate(invoice,
                                    syntax);
      assertEquals(List.of(),
                   result.messages(ValidationMessage.Stage.CALCULATION));
    }
    // Disabled by default.
    var xml = new String(resource("line-net-amount.facturx-en16931.xml"),
                         StandardCharsets.UTF_8);
    assertEquals(List.of(),
                 OFFICIAL.validate(xml.getBytes(StandardCharsets.UTF_8))
                         .messages(ValidationMessage.Stage.CALCULATION));
    // String input is checked like bytes.
    assertEquals(calculation(PANIOLO.validate(xml.getBytes(StandardCharsets.UTF_8))),
                 calculation(PANIOLO.validate(xml)));
  }

  @Test
  void reportsWhenTheAmountsCannotBeRecalculated() {
    // A negative line amount for a positive quantity cannot be explained by any net price (BR-27).
    var xml = new String(resource("valid.xrechnung-cii.xml"),
                         StandardCharsets.UTF_8).replaceFirst("<ram:LineTotalAmount>200.00<",
                                                              "<ram:LineTotalAmount>-200.00<");
    var result = PANIOLO.validate(xml.getBytes(StandardCharsets.UTF_8));
    var findings = result.messages(ValidationMessage.Stage.CALCULATION);
    assertEquals(1,
                 findings.size(),
                 findings::toString);
    assertEquals(CalculationCheck.NOT_CHECKED,
                 findings.get(0)
                         .ruleId());
    assertEquals(Severity.INFO,
                 findings.get(0)
                         .severity());
  }
}

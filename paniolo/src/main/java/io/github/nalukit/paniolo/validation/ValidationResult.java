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

import io.github.nalukit.paniolo.xml.util.XmlBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.stream.Collectors;

/**
 * Immutable findings for one explicitly selected validation profile, or for several validation
 * stages of one document combined with {@link #combine(String, List)}.
 *
 * @param duration    time the validation took, or null if not measured
 * @param profileName name of the selected profile or combined validation
 * @param messages    findings in reporting order; copied to an immutable list
 * @param statistics  execution statistics of the rules, or null if not collected (the default)
 */
public record ValidationResult(String profileName, List<ValidationMessage> messages, Duration duration, ValidationStatistics statistics) {
  /**
   * Namespace of the XML report written by {@link #toXml()}.
   */
  public static final String REPORT_NAMESPACE = "urn:io:github:nalukit:paniolo:validation-report:1";
  /**
   * Classpath resource of the XSD that describes the XML report, for
   * {@code ValidationResult.class.getResource(REPORT_SCHEMA)}.
   */
  public static final String REPORT_SCHEMA    = "/io/github/nalukit/paniolo/validation/validation-report-1.xsd";

  /**
   * Creates the result and copies the findings.
   *
   * @throws NullPointerException if messages is null
   */
  public ValidationResult {
    messages = List.copyOf(messages);
  }

  /**
   * Result without statistics.
   *
   * @param profileName name of the selected profile or combined validation
   * @param messages    findings in reporting order
   * @param duration    time the validation took, or null if not measured
   */
  public ValidationResult(String profileName,
                          List<ValidationMessage> messages,
                          Duration duration) {
    this(profileName,
         messages,
         duration,
         null);
  }

  /**
   * Result without measured duration.
   *
   * @param profileName name of the selected profile or combined validation
   * @param messages    findings in reporting order
   */
  public ValidationResult(String profileName,
                          List<ValidationMessage> messages) {
    this(profileName,
         messages,
         null);
  }

  /**
   * Combines the findings of several stages, e.g. PDF/A, container and XML, into one result.
   * Statistics of the stages are combined separately with {@link ValidationStatistics#merge} and
   * {@link #withStatistics(ValidationStatistics)}.
   *
   * @param profileName name of the combined validation
   * @param messages    findings of all stages in reporting order
   * @return the combined result without measured duration and without statistics
   */
  public static ValidationResult combine(String profileName,
                                         List<ValidationMessage> messages) {
    return new ValidationResult(profileName,
                                messages);
  }

  /**
   * Copy of this result with the given duration.
   *
   * @param duration time the validation took, or null
   * @return the copy
   */
  public ValidationResult withDuration(Duration duration) {
    return new ValidationResult(profileName,
                                messages,
                                duration,
                                statistics);
  }

  /**
   * Copy of this result with other findings, e.g. with an added information; profile name,
   * duration, and statistics are kept.
   *
   * @param messages the findings in reporting order
   * @return the copy
   */
  public ValidationResult withMessages(List<ValidationMessage> messages) {
    return new ValidationResult(profileName,
                                messages,
                                duration,
                                statistics);
  }

  /**
   * Copy of this result with the given statistics.
   *
   * @param statistics execution statistics, or null
   * @return the copy
   */
  public ValidationResult withStatistics(ValidationStatistics statistics) {
    return new ValidationResult(profileName,
                                messages,
                                duration,
                                statistics);
  }

  /**
   * Returns true if no finding is an error or fatal error; warnings and information are allowed.
   *
   * @return true if the document is valid
   */
  public boolean isValid() {
    return messages.stream()
                   .noneMatch(m -> m.severity() == Severity.ERROR || m.severity() == Severity.FATAL);
  }

  /**
   * Returns true if a finding is a warning.
   *
   * @return true if there are warnings
   */
  public boolean hasWarnings() {
    return messages.stream()
                   .anyMatch(m -> m.severity() == Severity.WARNING);
  }

  /**
   * Findings of one stage.
   *
   * @param stage the stage
   * @return the findings of the stage in reporting order
   */
  public List<ValidationMessage> messages(ValidationMessage.Stage stage) {
    Objects.requireNonNull(stage,
                           "stage");
    return messages.stream()
                   .filter(message -> message.stage() == stage)
                   .toList();
  }

  /**
   * Number of findings with the given severity.
   *
   * @param severity the severity
   * @return the number of findings
   */
  public long count(Severity severity) {
    Objects.requireNonNull(severity,
                           "severity");
    return messages.stream()
                   .filter(message -> message.severity() == severity)
                   .count();
  }

  /**
   * Writes the result as XML report, e.g. for archiving or for systems that process findings. The
   * findings are grouped by stage in the order the stages reported them; stages without findings
   * are left out. The report is described by the XSD {@link #REPORT_SCHEMA}.
   *
   * <pre>{@code
   * <report xmlns="urn:io:github:nalukit:paniolo:validation-report:1"
   *         generator="Paniolo 1.0.0" created="2026-10-29T10:15:30.123Z" duration="PT0.042S">
   *   <profile>EN16931 XRechnung (CII)</profile>
   *   <summary valid="false" fatals="1" errors="0" warnings="0" infos="0"/>
   *   <stage name="SCHEMATRON" valid="false">
   *     <message severity="FATAL" rule="BR-DE-15" location="/...">text</message>
   *   </stage>
   * </report>
   * }</pre>
   *
   * <p>{@code created} is the time the report is written, {@code duration} the time the validation
   * took; it is left out if not measured. Collected {@link #statistics() statistics} follow the
   * summary as element {@code statistics}.
   *
   * @return the XML report
   */
  public String toXml() {
    var xml = new XmlBuilder("report",
                             Map.of("",
                                    REPORT_NAMESPACE));
    xml.attribute("generator",
                  "Paniolo " + Version.VALUE)
       .attribute("created",
                  Instant.now()
                         .truncatedTo(ChronoUnit.MILLIS))
       .attribute("duration",
                  duration == null ?
                  null :
                  duration.truncatedTo(ChronoUnit.MILLIS))
       .element("profile",
                profileName == null || profileName.isBlank() ?
                "-" :
                profileName)
       .start("summary")
       .attribute("valid",
                  isValid())
       .attribute("fatals",
                  count(Severity.FATAL))
       .attribute("errors",
                  count(Severity.ERROR))
       .attribute("warnings",
                  count(Severity.WARNING))
       .attribute("infos",
                  count(Severity.INFO))
       .end();
    if (statistics != null) {
      statistics(xml);
    }
    var stages = messages.stream()
                         .collect(Collectors.groupingBy(ValidationMessage::stage,
                                                        LinkedHashMap::new,
                                                        Collectors.toList()));
    stages.forEach((stage, findings) -> {
      xml.start("stage")
         .attribute("name",
                    stage.name())
         .attribute("valid",
                    findings.stream()
                            .noneMatch(m -> m.severity() == Severity.ERROR || m.severity() == Severity.FATAL));
      for (var message : findings) {
        xml.element("message",
                    message.message() == null ||
                    message.message()
                           .isBlank() ?
                    "-" :
                    message.message(),
                    "severity",
                    message.severity()
                           .name(),
                    "rule",
                    message.ruleId(),
                    "location",
                    message.location() == null ||
                    message.location()
                           .isBlank() ?
                    null :
                    message.location());
      }
      xml.end();
    });
    return xml.toXml();
  }

  private void statistics(XmlBuilder xml) {
    xml.start("statistics");
    for (var schematron : statistics.schematron()) {
      xml.start("schematron")
         .attribute("stylesheet",
                    schematron.stylesheet())
         .attribute("activePatterns",
                    schematron.activePatterns())
         .attribute("firedRules",
                    schematron.firedRules())
         .attribute("failedAsserts",
                    schematron.failedAsserts())
         .attribute("successfulReports",
                    schematron.successfulReports())
         .end();
    }
    for (var pdfa : statistics.pdfa()) {
      xml.start("pdfa")
         .attribute("flavour",
                    pdfa.flavour())
         .attribute("profile",
                    pdfa.profile())
         .attribute("compliant",
                    pdfa.compliant())
         .attribute("checks",
                    pdfa.checks())
         .attribute("failedChecks",
                    pdfa.failedChecks());
      pdfa.failedRules()
          .forEach((rule, count) -> xml.start("failedRule")
                                       .attribute("rule",
                                                  rule)
                                       .attribute("count",
                                                  count)
                                       .end());
      xml.end();
    }
    xml.end();
  }

  /**
   * Paniolo version for the report, from the build.
   */
  private static final class Version {
    private static final String VALUE = load();

    private static String load() {
      try (InputStream input = ValidationResult.class.getResourceAsStream("paniolo-validation.properties")) {
        if (input == null) {
          return "unknown";
        }
        var properties = new Properties();
        properties.load(input);
        return properties.getProperty("version",
                                      "unknown");
      } catch (IOException e) {
        return "unknown";
      }
    }
  }
}

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
package io.github.nalukit.paniolo.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.nalukit.paniolo.Paniolo;
import io.github.nalukit.paniolo.config.PdfLayout;
import io.github.nalukit.paniolo.validation.AcceptancePolicy;
import io.github.nalukit.paniolo.validation.AcceptancePolicy.AcceptanceDecision;
import io.github.nalukit.paniolo.validation.Severity;
import io.github.nalukit.paniolo.validation.ValidationMessage;
import io.github.nalukit.paniolo.validation.ValidationResult;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Spec;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * Options and output of the validation commands, shared by invoices, orders, and despatch advices.
 *
 * <p>Directories are expanded into the files they contain, with {@code --recursive} including
 * sub-directories, filtered by {@code --include} and {@code --exclude}. A file that cannot be read
 * or is not a supported document is reported as an error of its own; the remaining files are still
 * validated.
 *
 * <p>With {@code --expect} or {@code --expectations}, a file is checked against its expected
 * result instead: {@code valid} expects the document to be accepted, {@code invalid} expects it to
 * be invalid or rejected by the policy. This serves regression tests of positive and negative
 * corpora.
 *
 * <p>Exit code: {@code 3} if at least one file could not be processed or an expected file is
 * missing, otherwise {@code 1} if at least one file with expectation does not meet it or one file
 * without expectation is invalid or not accepted by the policy of {@code --reject-rule} and
 * {@code --fail-on-warnings}, otherwise {@code 0}.
 */
final class ReportOptions {
  private static final ObjectMapper JSON = new ObjectMapper();

  @Spec(Spec.Target.MIXEE)
  CommandSpec spec;

  @Mixin
  FileSelection selection;

  @Option(names = { "-f",
                    "--format" }, defaultValue = "text", description = "Output format: ${COMPLETION-CANDIDATES} (default: ${DEFAULT-VALUE}). " + "json writes one object per line; xml writes the Paniolo validation report, pdf renders it as PDF/A (needs paniolo-pdf-layout); both accept one document only.")
  Format format;

  @Option(names = { "-l",
                    "--lang" }, paramLabel = "LANG", defaultValue = "de", description = "Language of the pdf report: de, en, or fr; default: ${DEFAULT-VALUE}.")
  String language;

  @Option(names = "--severity", defaultValue = "WARNING", description = "Lowest severity of the findings listed in text format: ${COMPLETION-CANDIDATES} (default: ${DEFAULT-VALUE}).")
  Severity severity;

  @Option(names = { "-q",
                    "--quiet" }, description = "Print nothing; report the result through the exit code only.")
  boolean quiet;

  @Option(names = "--fail-on-warnings", description = "Exit with 1 if a document has warnings, even if it is valid.")
  boolean failOnWarnings;

  @Option(names = "--reject-rule", paramLabel = "RULE", description = "Do not accept a valid document with a finding of this rule, e.g. PEPPOL-EN16931-R120; repeatable. The document stays valid; it is reported as rejected by policy and the exit code is 1.")
  List<String> rejectRules;

  /**
   * The acceptance policy of the options: errors, the rules of --reject-rule, and with
   * --fail-on-warnings every warning.
   */
  AcceptancePolicy policy() {
    var policy = AcceptancePolicy.officialRules();
    if (rejectRules != null && !rejectRules.isEmpty()) {
      policy = policy.rejectOn(rejectRules.toArray(String[]::new));
    }
    return failOnWarnings ?
           policy.rejectOnWarnings() :
           policy;
  }

  @Option(names = { "-o",
                    "--output" }, paramLabel = "FILE", description = "Write the report to FILE instead of standard output.")
  Path output;

  @Option(names = "--expect", paramLabel = "RESULT", description = "Expected result of every file without entry in --expectations: ${COMPLETION-CANDIDATES}. valid expects the document to be accepted, invalid expects it to be invalid or rejected by policy; the exit code is 1 if a file does not meet its expectation.")
  Expectation expect;

  @Option(names = "--expectations", paramLabel = "FILE", description = "Expected result per file: lines 'valid PATH' or 'invalid PATH', paths relative to FILE; # starts a comment. A listed file that is not validated is reported as missing (exit code 3).")
  Path expectations;

  @Option(names = "--summary", description = "Append a summary: files, valid, invalid, and files that could not be processed (text: last lines; json: a last object with \"summary\": true).")
  boolean summary;

  private static String json(Path file,
                             String error) {
    ObjectNode node = JSON.createObjectNode();
    node.put("file",
             file.toString());
    node.put("valid",
             false);
    node.put("error",
             error);
    return node.toString();
  }

  private static String json(Path file,
                             ValidationResult result,
                             AcceptanceDecision decision,
                             Expectation expectation,
                             Boolean asExpected) {
    ObjectNode node = JSON.createObjectNode();
    node.put("file",
             file.toString());
    node.put("profile",
             result.profileName());
    node.put("valid",
             result.isValid());
    node.put("accepted",
             decision.accepted());
    if (expectation != null) {
      node.put("expected",
               expectation.name());
      node.put("asExpected",
               asExpected);
    }
    if (decision.rejectedByPolicy()) {
      var blocking = node.putArray("blocking");
      decision.blockingRules()
              .forEach(blocking::add);
    }
    node.put("fatals",
             result.count(Severity.FATAL));
    node.put("errors",
             result.count(Severity.ERROR));
    node.put("warnings",
             result.count(Severity.WARNING));
    node.put("infos",
             result.count(Severity.INFO));
    if (result.duration() != null) {
      node.put("durationMillis",
               result.duration()
                     .toMillis());
    }
    if (result.statistics() != null) {
      var statistics = node.putObject("statistics");
      var schematron = statistics.putArray("schematron");
      for (var stylesheet : result.statistics()
                                  .schematron()) {
        schematron.addObject()
                  .put("stylesheet",
                       stylesheet.stylesheet())
                  .put("activePatterns",
                       stylesheet.activePatterns())
                  .put("firedRules",
                       stylesheet.firedRules())
                  .put("failedAsserts",
                       stylesheet.failedAsserts())
                  .put("successfulReports",
                       stylesheet.successfulReports());
      }
      var pdfa = statistics.putArray("pdfa");
      for (var check : result.statistics()
                             .pdfa()) {
        var entry = pdfa.addObject()
                        .put("flavour",
                             check.flavour())
                        .put("profile",
                             check.profile())
                        .put("compliant",
                             check.compliant())
                        .put("checks",
                             check.checks())
                        .put("failedChecks",
                             check.failedChecks());
        var rules = entry.putObject("failedRules");
        check.failedRules()
             .forEach(rules::put);
      }
    }
    var messages = node.putArray("messages");
    for (ValidationMessage message : result.messages()) {
      ObjectNode entry = messages.addObject();
      entry.put("severity",
                message.severity()
                       .name());
      entry.put("stage",
                message.stage()
                       .name());
      entry.put("rule",
                message.ruleId());
      entry.put("message",
                message.message());
      if (message.location() != null) {
        entry.put("location",
                  message.location());
      }
    }
    return node.toString();
  }

  /**
   * Validates each file, prints the results and returns the exit code.
   */
  int run(Io io,
          List<Path> files,
          Function<byte[], ValidationResult> validation) {
    return run(io,
               files,
               validation,
               null);
  }

  /**
   * Validates each file like {@link #run(Io, List, Function)}; {@code pdf} renders the report of
   * {@code --format pdf}, or null if the command has none.
   */
  int run(Io io,
          List<Path> files,
          Function<byte[], ValidationResult> validation,
          PdfReport pdf) {
    if (format == Format.pdf && pdf == null) {
      throw new ParameterException(spec.commandLine(),
                                   "--format pdf is not available for this command");
    }
    var inputs = selection.inputs(files);
    if (inputs.isEmpty()) {
      throw new ParameterException(spec.commandLine(),
                                   "No files to validate in " + files);
    }
    if ((format == Format.xml || format == Format.pdf) && (inputs.size() > 1 || summary)) {
      throw new ParameterException(spec.commandLine(),
                                   "--format " + format + " accepts one document only and no --summary, but got " + inputs.size() + " files");
    }
    Map<Path, Expectation> expected = expectations();
    byte[] binary = null;
    var report   = new StringBuilder();
    var policy   = policy();
    int valid    = 0;
    int invalid  = 0;
    int rejected = 0;
    int errors   = 0;
    int met      = 0;
    int notMet   = 0;
    // Files without expectation that are invalid or rejected; they fail as without --expect.
    int failed = 0;
    for (FileSelection.Input input : inputs) {
      Expectation expectation = expected.remove(key(input.path()));
      if (expectation == null) {
        expectation = expect;
      }
      ValidationResult result  = null;
      byte[]           content = null;
      String           error   = input.error();
      if (error == null) {
        try {
          content = io.read(input.path());
          result  = validation.apply(content);
        } catch (RuntimeException e) {
          // One unreadable or unsupported file does not stop the others; xml and pdf report one file.
          if (format == Format.xml || format == Format.pdf) {
            throw e;
          }
          error = message(e);
        }
      }
      if (error != null) {
        errors++;
        error(report,
              input.path(),
              error);
        continue;
      }
      var decision = policy.evaluate(result);
      if (!result.isValid()) {
        invalid++;
      } else if (decision.rejectedByPolicy()) {
        rejected++;
      } else {
        valid++;
      }
      Boolean asExpected = null;
      if (expectation != null) {
        asExpected = expectation.metBy(decision);
        if (asExpected) {
          met++;
        } else {
          notMet++;
        }
      } else if (!decision.accepted()) {
        failed++;
      }
      switch (format) {
        case text -> text(report,
                          input.path(),
                          result,
                          decision,
                          expectation,
                          asExpected);
        case json -> report.append(json(input.path(),
                                        result,
                                        decision,
                                        expectation,
                                        asExpected))
                           .append('\n');
        case xml -> report.append(result.toXml());
        case pdf -> binary = pdf.render(result,
                                        input.path(),
                                        content,
                                        Locale.forLanguageTag(language));
      }
    }
    // Listed in the expectations, but not among the validated files: a corpus file is missing.
    for (Path missing : expected.keySet()) {
      errors++;
      error(report,
            missing,
            "expected file was not validated: " + missing);
    }
    boolean expecting = expect != null || expectations != null;
    if (summary) {
      int total = inputs.size();
      if (format == Format.json) {
        ObjectNode node = JSON.createObjectNode();
        node.put("summary",
                 true);
        node.put("files",
                 total);
        node.put("valid",
                 valid);
        node.put("invalid",
                 invalid);
        node.put("rejected",
                 rejected);
        node.put("errors",
                 errors);
        if (expecting) {
          node.put("asExpected",
                   met);
          node.put("notAsExpected",
                   notMet);
        }
        report.append(node)
              .append('\n');
      } else {
        report.append("Summary: ")
              .append(total)
              .append(" files, ")
              .append(valid)
              .append(" valid, ")
              .append(invalid)
              .append(" invalid, ");
        if (policy != AcceptancePolicy.officialRules()) {
          report.append(rejected)
                .append(" rejected by policy, ");
        }
        if (expecting) {
          report.append(met)
                .append(" as expected, ")
                .append(notMet)
                .append(" not as expected, ");
        }
        report.append(errors)
              .append(" not processed\n");
      }
    }
    if (!quiet) {
      if (binary != null) {
        io.write(output,
                 binary);
      } else {
        io.write(output,
                 report.toString());
      }
    }
    if (errors > 0) {
      return PanioloCli.EXIT_ERROR;
    }
    return notMet + failed > 0 ?
           PanioloCli.EXIT_INVALID :
           PanioloCli.EXIT_OK;
  }

  /**
   * Reports a file that could not be processed: on standard error, as for every processing error,
   * and in the report.
   */
  private void error(StringBuilder report,
                     Path file,
                     String error) {
    spec.commandLine()
        .getErr()
        .println("paniolo: " + (error.contains(file.toString()) ?
                                error :
                                file + ": " + error));
    switch (format) {
      case text -> report.append(file)
                         .append(": error (")
                         .append(error)
                         .append(")\n");
      case json -> report.append(json(file,
                                      error))
                         .append('\n');
      case xml, pdf -> throw new IllegalStateException(error);
    }
  }

  /**
   * The expected results of --expectations by normalized absolute path; empty without the option.
   */
  private Map<Path, Expectation> expectations() {
    var result = new LinkedHashMap<Path, Expectation>();
    if (expectations == null) {
      return result;
    }
    Path base = expectations.toAbsolutePath()
                            .getParent();
    String text;
    try {
      text = Files.readString(expectations,
                              StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new ParameterException(spec.commandLine(),
                                   "Cannot read --expectations " + expectations + ": " + e.getMessage());
    }
    int number = 0;
    for (String line : text.lines()
                           .toList()) {
      number++;
      String entry = line.strip();
      if (entry.isEmpty() || entry.startsWith("#")) {
        continue;
      }
      String[] parts = entry.split("\\s+",
                                   2);
      Expectation expectation;
      try {
        expectation = Expectation.valueOf(parts[0].toLowerCase(Locale.ROOT));
      } catch (IllegalArgumentException e) {
        expectation = null;
      }
      if (expectation == null || parts.length < 2) {
        throw new ParameterException(spec.commandLine(),
                                     expectations + ":" + number + ": expected 'valid PATH' or 'invalid PATH', but got: " + entry);
      }
      if (result.put(key(base.resolve(parts[1].strip())),
                     expectation) != null) {
        throw new ParameterException(spec.commandLine(),
                                     expectations + ":" + number + ": " + parts[1].strip() + " is listed twice");
      }
    }
    return result;
  }

  private static Path key(Path file) {
    return file.toAbsolutePath()
               .normalize();
  }

  private static String message(Throwable exception) {
    Throwable cause = exception;
    while (cause.getMessage() == null && cause.getCause() != null) {
      cause = cause.getCause();
    }
    return cause.getMessage() == null ?
           cause.getClass()
                .getName() :
           cause.getMessage();
  }

  private void text(StringBuilder report,
                    Path file,
                    ValidationResult result,
                    AcceptanceDecision decision,
                    Expectation expectation,
                    Boolean asExpected) {
    report.append(file)
          .append(": ")
          .append(result.isValid() ?
                  "valid" :
                  "invalid");
    if (decision.rejectedByPolicy()) {
      report.append(", rejected by policy: ")
            .append(String.join(", ",
                                decision.blockingRules()));
    }
    if (expectation != null) {
      report.append(asExpected ?
                    ", as expected" :
                    ", NOT AS EXPECTED: expected " + expectation);
    }
    report
          .append(" (")
          .append(result.profileName())
          .append(", ")
          .append(result.count(Severity.FATAL) + result.count(Severity.ERROR))
          .append(" errors, ")
          .append(result.count(Severity.WARNING))
          .append(" warnings");
    if (result.duration() != null) {
      report.append(", ")
            .append(result.duration()
                          .toMillis())
            .append(" ms");
    }
    report.append(")\n");
    if (result.statistics() != null) {
      for (var stylesheet : result.statistics()
                                  .schematron()) {
        report.append("  statistics ")
              .append(stylesheet.stylesheet())
              .append(": ")
              .append(stylesheet.activePatterns())
              .append(" patterns, ")
              .append(stylesheet.firedRules())
              .append(" fired rules, ")
              .append(stylesheet.failedAsserts())
              .append(" failed asserts, ")
              .append(stylesheet.successfulReports())
              .append(" successful reports\n");
      }
      for (var check : result.statistics()
                             .pdfa()) {
        report.append("  statistics PDF/A-")
              .append(check.flavour())
              .append(": ")
              .append(check.checks())
              .append(" checks, ")
              .append(check.failedChecks())
              .append(" failed")
              .append(check.compliant() ?
                      ", compliant" :
                      ", not compliant");
        if (!check.failedRules()
                  .isEmpty()) {
          report.append(" (");
          var first = true;
          for (var rule : check.failedRules()
                               .entrySet()) {
            report.append(first ?
                          "" :
                          ", ")
                  .append(rule.getKey())
                  .append(" x")
                  .append(rule.getValue());
            first = false;
          }
          report.append(')');
        }
        report.append('\n');
      }
    }
    for (ValidationMessage message : result.messages()) {
      if (message.severity()
                 .compareTo(severity) < 0) {
        continue;
      }
      report.append("  ")
            .append(message.severity())
            .append(" [")
            .append(message.stage())
            .append("] ")
            .append(message.ruleId())
            .append(": ")
            .append(message.message());
      if (message.location() != null &&
          !message.location()
                  .isBlank()) {
        report.append("\n    at ")
              .append(message.location());
      }
      report.append('\n');
    }
  }

  /**
   * Expected result of a file of --expect and --expectations.
   */
  enum Expectation {
    valid,
    invalid;

    /**
     * Returns true if the decision meets the expectation: valid expects an accepted document,
     * invalid one that is invalid or rejected by the policy.
     */
    boolean metBy(AcceptanceDecision decision) {
      return decision.accepted() == (this == valid);
    }
  }

  enum Format {
    text,
    json,
    xml,
    pdf
  }

  /**
   * The PDF report of the facade, with the file name of the validated file.
   */
  static PdfReport pdfReport(Paniolo paniolo) {
    return (result, file, content, locale) -> paniolo.toReportPdf(result,
                                                                  file.getFileName() == null ?
                                                                  file.toString() :
                                                                  file.getFileName()
                                                                      .toString(),
                                                                  content,
                                                                  PdfLayout.of(locale));
  }

  /**
   * Renders the report of one validated file as PDF.
   */
  @FunctionalInterface
  interface PdfReport {
    byte[] render(ValidationResult result,
                  Path file,
                  byte[] content,
                  Locale locale);
  }
}

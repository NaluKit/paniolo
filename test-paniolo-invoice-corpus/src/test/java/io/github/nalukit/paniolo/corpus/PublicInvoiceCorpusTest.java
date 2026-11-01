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
package io.github.nalukit.paniolo.corpus;

import io.github.nalukit.paniolo.Paniolo;
import io.github.nalukit.paniolo.config.UnknownDocumentHandling;
import io.github.nalukit.paniolo.validation.Severity;
import io.github.nalukit.paniolo.validation.InvoiceValidator;
import io.github.nalukit.paniolo.validation.ValidationProfile;
import io.github.nalukit.paniolo.rules.xrechnung.XrechnungRules;
import io.github.nalukit.paniolo.xml.ReadMode;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Locale;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises the public facade against immutable third-party documents, without network access. */
@Execution(ExecutionMode.SAME_THREAD)
class PublicInvoiceCorpusTest {
  private static final Path CORPUS = Path.of("src/test/resources/corpus");
  private static final Path REPORT = Path.of("target/corpus-report");
  private static final Paniolo PANIOLO = Paniolo.builder()
      .unknownDocuments(UnknownDocumentHandling.REPORT_INVALID).build();
  private static final EnumMap<ValidationProfile.Syntax, InvoiceValidator> EN16931 =
      new EnumMap<>(ValidationProfile.Syntax.class);
  private static final List<String> RESULTS = new ArrayList<>();

  record Sample(String file, String source, String sha256, String valid,
                String requiredErrors, String readable, String readError, String automaticValid, String note) {
    @Override public String toString() { return file; }
  }

  static Stream<Sample> samples() throws IOException {
    return Files.readAllLines(CORPUS.resolve("manifest.tsv")).stream().skip(1)
        .map(line -> line.split("\t", -1))
        .map(c -> new Sample(c[0], c[1], c[2], c[3], c[4], c[5], c[6], c[7], c[8]));
  }

  @Test
  void manifestCoversEveryFixtureExactlyOnce() throws IOException {
    var samples = samples().toList();
    for (Sample sample : samples) {
      assertTrue(Set.of("true", "false").contains(sample.valid()), "Invalid validity expectation");
      assertTrue(Set.of("true", "false").contains(sample.automaticValid()), "Invalid automatic expectation");
      assertTrue(Set.of("true", "false", "skipped-invalid").contains(sample.readable()), "Invalid read expectation");
      if (sample.readable().equals("false")) {
        assertFalse(sample.readError().isBlank(), "Missing known read exception");
        assertFalse(sample.note().isBlank(), "Missing known limitation note");
      }
    }
    var entries = samples.stream().map(Sample::file).toList();
    var files = new TreeSet<String>();
    try (var paths = Files.walk(CORPUS)) {
      paths.filter(Files::isRegularFile)
          .filter(p -> {
            String name = p.toString().toLowerCase(Locale.ROOT);
            return name.endsWith(".xml") || name.endsWith(".pdf");
          })
          .forEach(p -> files.add(CORPUS.relativize(p).toString().replace('\\', '/')));
    }
    assertEquals(entries.size(), new TreeSet<>(entries).size(), "Duplicate manifest entries");
    assertEquals(files, new TreeSet<>(entries), "Unlisted or missing invoice fixtures");
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("samples")
  void matchesDocumentedValidationAndReadBaseline(Sample sample) throws Exception {
    byte[] bytes = Files.readAllBytes(CORPUS.resolve(sample.file()));
    assertEquals(sample.sha256(), HexFormat.of().formatHex(
        MessageDigest.getInstance("SHA-256").digest(bytes)), "Fixture changed: " + sample.file());
    Path report = REPORT.resolve(sample.file() + ".report.xml");
    Files.createDirectories(report.getParent());
    var automatic = PANIOLO.validate(bytes);
    var result = sample.file().startsWith("en16931/")
        ? EN16931.computeIfAbsent(PANIOLO.detect(bytes), syntax -> new InvoiceValidator(
            ValidationProfile.en16931(XrechnungRules.bundle(), syntax))).validate(bytes)
        : automatic;
    Files.writeString(report.resolveSibling(report.getFileName() + ".automatic.xml"),
        automatic.toXml(), StandardCharsets.UTF_8);
    Files.writeString(report, result.toXml(), StandardCharsets.UTF_8);
    Set<String> errors = result.messages().stream()
        .filter(m -> m.severity() == Severity.ERROR || m.severity() == Severity.FATAL)
        .map(m -> m.ruleId()).collect(Collectors.toCollection(TreeSet::new));
    var warnings = result.messages().stream().filter(m -> m.severity() == Severity.WARNING)
        .map(m -> m.ruleId()).collect(Collectors.toCollection(TreeSet::new));
    String read = "skipped-invalid";
    String readDetail = "";
    if (result.isValid()) {
      try {
        assertNotNull(PANIOLO.read(bytes, ReadMode.LENIENT));
        read = "true";
      } catch (RuntimeException e) {
        read = "false";
        readDetail = e.getClass().getSimpleName() + ": " + e.getMessage();
      }
    }
    RESULTS.add(String.join("\t", sample.file(), Boolean.toString(result.isValid()),
        String.join(",", errors), String.join(",", warnings), read,
        clean(readDetail), clean(result.profileName()), Boolean.toString(automatic.isValid())));
    // Expectations are curated in the manifest, never rewritten by the test run.
    String actualRead = read;
    String actualReadDetail = clean(readDetail);
    assertAll(sample.file(),
        () -> assertEquals(sample.valid(), Boolean.toString(result.isValid()), errors.toString()),
        () -> {
          for (String rule : sample.requiredErrors().split(",")) {
            if (!rule.isEmpty()) assertTrue(errors.contains(rule), "Missing error " + rule);
          }
          if (!result.isValid()) assertFalse(sample.requiredErrors().isEmpty(),
              "Every negative sample needs an independently reviewed error expectation");
        },
        () -> assertEquals(sample.readable(), actualRead, "LENIENT read: " + sample.file()),
        () -> assertEquals(sample.readError(), actualReadDetail, "Known read limitation changed"),
        () -> assertEquals(sample.automaticValid(), Boolean.toString(automatic.isValid()),
            "Automatic facade profile selection changed"));
  }

  private static String clean(String value) {
    return value == null ? "" : value.replace('\t', ' ').replace('\r', ' ').replace('\n', ' ');
  }

  @AfterAll
  static void writeSummary() throws IOException {
    Files.createDirectories(REPORT);
    var lines = new ArrayList<String>();
    lines.add("file\tvalid\terrors\twarnings\treadable\treadDetail\tprofile\tautomaticValid");
    lines.addAll(RESULTS.stream().sorted().toList());
    Files.write(REPORT.resolve("results.tsv"), lines, StandardCharsets.UTF_8);
    long limitations = RESULTS.stream().filter(line -> line.split("\t", -1)[4].equals("false")).count();
    System.out.println("Invoice corpus: " + RESULTS.size() + " documents; " + limitations
        + " documented read limitations (see manifest notes and corpus-report/results.tsv).");
  }
}

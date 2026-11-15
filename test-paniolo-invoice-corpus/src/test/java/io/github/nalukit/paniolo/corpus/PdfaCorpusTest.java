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

import io.github.nalukit.paniolo.pdfa.VeraPdfaSupport;
import io.github.nalukit.paniolo.validation.Severity;
import io.github.nalukit.paniolo.validation.ValidationMessage;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * PDF/A of the corpus PDFs, checked with veraPDF through {@link VeraPdfaSupport}. It runs as its
 * own Maven execution ({@code pdfa-corpus}) so that {@link PublicInvoiceCorpusTest} keeps its
 * baseline of container and XML without PDF/A.
 *
 * <p>The PDFs come from the test resources of another project, among them deliberately broken
 * files and files made before the rules of today; they need not conform. Their findings are
 * recorded in {@code pdfa-expectations.tsv}: the rule identifiers veraPDF and Paniolo report as
 * errors, with the reason a file does not conform. A changed finding fails the test and needs a
 * reviewed update of the expectation.
 */
class PdfaCorpusTest {
  private static final Path CORPUS       = Path.of("src/test/resources/corpus");
  private static final Path EXPECTATIONS = CORPUS.resolve("pdfa-expectations.tsv");
  private static final Path REPORT       = Path.of("target/corpus-report/pdfa-results.tsv");

  private static Map<String, String[]> expectations() throws IOException {
    var map = new LinkedHashMap<String, String[]>();
    for (String line : Files.readAllLines(EXPECTATIONS)) {
      String[] c = line.split("\t",
                              -1);
      if (!line.startsWith("file\t") && c.length >= 3) {
        map.put(c[0],
                c);
      }
    }
    return map;
  }

  @Test
  void pdfaFindingsOfTheCorpusPdfsAreAsReviewed() throws IOException {
    var expected = expectations();
    var pdfs = Files.readAllLines(CORPUS.resolve("manifest.tsv"))
                    .stream()
                    .skip(1)
                    .map(line -> line.split("\t",
                                            -1)[0])
                    .filter(file -> file.endsWith(".pdf"))
                    .toList();
    assertEquals(new TreeSet<>(pdfs),
                 new TreeSet<>(expected.keySet()),
                 "Every corpus PDF needs a PDF/A expectation");
    var support = new VeraPdfaSupport();
    var results = new ArrayList<String>();
    results.add("file\tconforms\terrors\tfindings");
    var failures = new ArrayList<String>();
    for (String file : pdfs) {
      var findings = support.validate(Files.readAllBytes(CORPUS.resolve(file)));
      var errors = findings.stream()
                           .filter(message -> message.severity() == Severity.ERROR || message.severity() == Severity.FATAL)
                           .map(ValidationMessage::ruleId)
                           .collect(Collectors.toCollection(TreeSet::new));
      boolean conforms = errors.isEmpty();
      // The report lists every finding with its severity; only the errors are compared.
      results.add(file + "\t" + conforms + "\t" + String.join(",",
                                                              errors) + "\t" + findings.stream()
                                                                                       .map(message -> message.severity() + " " + message.ruleId() + " " + message.message()
                                                                                                                                                                  .replace('\t',
                                                                                                                                                                           ' '))
                                                                                       .collect(Collectors.joining(" | ")));
      var want = expected.get(file);
      if (!Boolean.toString(conforms)
                  .equals(want[1]) || !String.join(",",
                                                   errors)
                                              .equals(want[2])) {
        failures.add(file + ": expected " + want[1] + " [" + want[2] + "] but was " + conforms + " [" + String.join(",",
                                                                                                                    errors) + "]");
      }
      if (!conforms && (want.length < 4 || want[3].isBlank())) {
        failures.add(file + ": a PDF that does not conform needs the reason in the note");
      }
    }
    Files.createDirectories(REPORT.getParent());
    Files.write(REPORT,
                results,
                StandardCharsets.UTF_8);
    assertEquals(java.util.List.of(),
                 failures);
    assertFalse(pdfs.isEmpty());
  }
}

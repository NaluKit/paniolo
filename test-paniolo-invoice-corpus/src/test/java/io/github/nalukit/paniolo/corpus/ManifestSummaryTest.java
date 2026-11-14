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

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Keeps the numbers of the corpus README and the notes of the manifest in line with the manifest,
 * which is the reviewed source of the baseline.
 */
class ManifestSummaryTest {
  private static final Path CORPUS = Path.of("src/test/resources/corpus/manifest.tsv");
  private static final Path README = Path.of("README.md");

  private static List<String[]> rows() throws IOException {
    return Files.readAllLines(CORPUS)
                .stream()
                .skip(1)
                .map(line -> line.split("\t",
                                        -1))
                .toList();
  }

  private static int number(String readme,
                            String regex) {
    var matcher = Pattern.compile(regex)
                         .matcher(readme.replace('\n',
                                                 ' '));
    assertTrue(matcher.find(),
               "README lacks: " + regex);
    return Integer.parseInt(matcher.group(1));
  }

  @Test
  void readmeNumbersMatchTheManifest() throws IOException {
    var    rows   = rows();
    String readme = Files.readString(README,
                                     StandardCharsets.UTF_8);
    long valid   = rows.stream()
                       .filter(c -> c[3].equals("true"))
                       .count();
    long invalid = rows.stream()
                       .filter(c -> c[3].equals("false"))
                       .count();
    long unreadable = rows.stream()
                          .filter(c -> c[5].equals("false"))
                          .count();
    assertEquals(rows.size(),
                 number(readme,
                        "(\\d+) unchanged invoice files"));
    assertEquals(valid,
                 number(readme,
                        "(\\d+) files pass the selected validation profile"));
    assertEquals(invalid,
                 number(readme,
                        "(\\d+) are rejected"));
    assertEquals(unreadable,
                 number(readme,
                        "cannot read (\\d+) valid files? into"));
    assertEquals(rows.size() + 1,
                 number(readme,
                        "the (\\d+) corpus checks"));
  }

  @Test
  void aFixedCaseKeepsNoStaleErrorNote() throws IOException {
    for (var c : rows()) {
      boolean readable = !c[5].equals("false");
      assertEquals(readable,
                   c[6].isEmpty(),
                   "Read error and read result disagree: " + c[0]);
      if (readable) {
        assertFalse(c[8].startsWith("Known Paniolo model/read limitation"),
                    "Stale limitation note: " + c[0]);
      }
    }
  }
}

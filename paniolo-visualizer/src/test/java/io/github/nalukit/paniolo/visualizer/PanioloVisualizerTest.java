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
package io.github.nalukit.paniolo.visualizer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PanioloVisualizerTest {
  private static final PanioloVisualizer VISUALIZER = PanioloVisualizer.create();

  private static byte[] sample(String name)
      throws IOException {
    try (InputStream input = PanioloVisualizerTest.class.getResourceAsStream("/samples/" + name)) {
      return input.readAllBytes();
    }
  }

  private static int count(String text,
                           String part) {
    int count = 0;
    for (int i = text.indexOf(part); i >= 0; i = text.indexOf(part,
                                                              i + 1)) {
      count++;
    }
    return count;
  }

  @Test
  @DisplayName("each instance reads the script once")
  void instanceKeepsScript() {
    assertSame(VISUALIZER.script(),
               VISUALIZER.script());
    assertEquals(VISUALIZER.script(),
                 new String(VISUALIZER.script()
                                      .getBytes(StandardCharsets.UTF_8),
                            StandardCharsets.UTF_8));
  }



  /**
   * The remaining EXTENDED metadata (task V05): the script reads them and labels them in German,
   * English, and French. Their display was checked in a browser; this test keeps the reading and the
   * labels from getting lost.
   */
  @Test
  @DisplayName("reads and labels the remaining EXTENDED metadata")
  void readsTheRemainingExtendedMetadata() {
    String script = VISUALIZER.script();
    for (String element : new String[] { "'LanguageID'",
                                         "txt(n, 'ContentCode')",
                                         "'IncludedNote/SubjectCode'",
                                         "'IncludedNote/ContentCode'",
                                         "'LineStatusCode'",
                                         "txt(a, 'TypeCode')" }) {
      assertTrue(script.contains(element),
                 element);
    }
    for (String label : new String[] { "language:",
                                       "noteSubject:",
                                       "noteCode:",
                                       "lineStatusCode:",
                                       "accountType:" }) {
      assertEquals(3,
                   count(script,
                         " " + label + " '"),
                   label);
    }
    // Code list 52 in every language.
    assertEquals(3,
                 count(script,
                       "'7': '"),
                 "account types");
  }

  @Nested
  @DisplayName("script")
  class Script {
    @Test
    @DisplayName("is the ES module that defines the web component")
    void definesTheWebComponent() {
      String script = VISUALIZER.script();
      assertTrue(script.contains("customElements.define(TAG_NAME, PanioloViewer)"));
      assertTrue(script.contains("const TAG_NAME = '" + PanioloVisualizer.TAG_NAME + "'"));
      assertTrue(script.contains("export async function parse("));
    }

    @Test
    @DisplayName("can be inlined in HTML without escaping")
    void canBeInlined() {
      String script = VISUALIZER.script()
                                .toLowerCase(Locale.ROOT);
      assertFalse(script.contains("</script"));
      assertFalse(script.contains("<!--"));
    }

    @Test
    @DisplayName("is served from META-INF/resources")
    void isAServletResource() {
      assertTrue(PanioloVisualizer.SCRIPT_RESOURCE.startsWith("META-INF/resources/"));
      assertTrue(PanioloVisualizer.class.getClassLoader()
                                        .getResource(PanioloVisualizer.SCRIPT_RESOURCE) != null);
    }
  }



  @Nested
  @DisplayName("toHtml")
  class ToHtml {
    @Test
    @DisplayName("embeds script and document")
    void embedsScriptAndDocument()
        throws IOException {
      byte[] document = sample("invoice-facturx.pdf");
      String html     = VISUALIZER.toHtml(document);
      assertTrue(html.startsWith("<!doctype html>"));
      assertTrue(html.contains("<html lang=\"de\">"));
      assertTrue(html.contains("<paniolo-viewer lang=\"de\"></paniolo-viewer>"));
      assertTrue(html.contains(Base64.getEncoder()
                                     .encodeToString(document)));
      assertTrue(html.contains(VISUALIZER.script()));
      assertFalse(html.contains("id=\"paniolo-report\""));
      assertEquals(1,
                   count(html,
                         "<script type=\"module\">"));
    }

    @Test
    @DisplayName("embeds a validation report")
    void embedsReport()
        throws IOException {
      byte[] report = sample("validation-report.xml");
      String html = VISUALIZER.toHtml(sample("invoice-invalid-cii.xml"),
                                      report,
                                      Locale.ENGLISH);
      assertTrue(html.contains("<html lang=\"en\">"));
      assertTrue(html.contains("id=\"paniolo-report\">" +
                               Base64.getEncoder()
                                     .encodeToString(report) +
                               "</script>"));
    }

    @Test
    @DisplayName("falls back to English for other languages")
    void fallsBackToEnglish()
        throws IOException {
      assertTrue(VISUALIZER.toHtml(sample("invoice-ubl.xml"),
                                   Locale.forLanguageTag("fr-CA"))
                           .contains("<html lang=\"fr\">"));
      assertTrue(VISUALIZER.toHtml(sample("invoice-ubl.xml"),
                                   Locale.JAPANESE)
                           .contains("<html lang=\"en\">"));
    }

    @Test
    @DisplayName("needs no network access")
    void needsNoNetwork()
        throws IOException {
      String html = VISUALIZER.toHtml(sample("invoice-ubl.xml"));
      assertFalse(Pattern.compile("(src|href)=\"https?:")
                         .matcher(html)
                         .find());
      assertFalse(html.contains("<link"));
    }

    @Test
    @DisplayName("rejects null")
    void rejectsNull() {
      assertThrows(NullPointerException.class,
                   () -> VISUALIZER.toHtml(null));
      assertThrows(NullPointerException.class,
                   () -> VISUALIZER.toHtml(new byte[0],
                                           null));
    }
  }
}

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

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Gives access to the Paniolo web component {@code <paniolo-viewer>} and writes standalone HTML
 * pages that show a document with it.
 *
 * <p>The web component is a dependency-free ES module that runs in the browser. It accepts the
 * document as bytes (UBL, CII, ZUGFeRD 1.0, a Factur-X / ZUGFeRD PDF, Order-X, or a despatch
 * advice), reads it in the browser, and renders it. The module is packaged as
 * {@value #SCRIPT_RESOURCE}, so servlet containers, Spring Boot, and Quarkus serve it as
 * {@code /paniolo-visualizer/paniolo-visualizer.js} without configuration:
 *
 * <pre>{@code
 * <script type="module" src="/paniolo-visualizer/paniolo-visualizer.js"></script>
 * <paniolo-viewer lang="de" src="/invoices/4711"></paniolo-viewer>
 * }</pre>
 *
 * <p>{@link #toHtml(byte[], byte[], Locale)} writes one HTML file with the script and the
 * document, e.g. to archive a readable copy or to send it by e-mail; it needs no server and no
 * network access.
 *
 * <p>Instances are immutable and thread-safe; create one and share it.
 */
public final class PanioloVisualizer {
  /**
   * Class path resource of the web component.
   */
  public static final String SCRIPT_RESOURCE = "META-INF/resources/paniolo-visualizer/paniolo-visualizer.js";

  /**
   * Tag name of the web component.
   */
  public static final String TAG_NAME = "paniolo-viewer";

  private static final Set<String> LANGUAGES = Set.of("de",
                                                      "en",
                                                      "fr");

  private final String script;

  private PanioloVisualizer(String script) {
    this.script = script;
  }

  /**
   * Creates a visualizer and loads the web component from the class path.
   *
   * @return the visualizer
   * @throws UncheckedIOException if the script cannot be read
   */
  public static PanioloVisualizer create() {
    try (InputStream input = PanioloVisualizer.class.getClassLoader()
                                                    .getResourceAsStream(SCRIPT_RESOURCE)) {
      if (input == null) {
        throw new IllegalStateException("resource not found: " + SCRIPT_RESOURCE);
      }
      return new PanioloVisualizer(new String(input.readAllBytes(),
                                              StandardCharsets.UTF_8));
    } catch (IOException e) {
      throw new UncheckedIOException("cannot read " + SCRIPT_RESOURCE,
                                     e);
    }
  }

  private static String escapeScript(String source) {
    return source.replace("</script",
                          "<\\/script")
                 .replace("<!--",
                          "<\\!--");
  }

  /**
   * Returns the source of the web component, an ES module, e.g. to serve it from an own endpoint.
   *
   * @return the JavaScript source
   */
  public String script() {
    return script;
  }

  /**
   * Writes a standalone HTML page that shows the document in German.
   *
   * @param document the document as bytes: XML or PDF
   * @return the HTML page
   */
  public String toHtml(byte[] document) {
    return toHtml(document,
                  null,
                  Locale.GERMAN);
  }

  /**
   * Writes a standalone HTML page that shows the document.
   *
   * @param document the document as bytes: XML or PDF
   * @param locale   the language of the labels; German, English, and French are supported, other
   *                 languages fall back to English
   * @return the HTML page
   */
  public String toHtml(byte[] document,
                       Locale locale) {
    return toHtml(document,
                  null,
                  locale);
  }

  /**
   * Writes a standalone HTML page that shows the document and, optionally, a validation report.
   *
   * <p>The page contains the script and the Base64 encoded document and needs no network access.
   * The document is read in the browser when the page is opened; a document the component cannot
   * read is shown as an error message.
   *
   * @param document         the document as bytes: XML or PDF
   * @param validationReport the Paniolo validation report ({@code ValidationResult.toXml()}) or an
   *                         SVRL report as bytes, or {@code null}
   * @param locale           the language of the labels; German, English, and French are supported,
   *                         other languages fall back to English
   * @return the HTML page
   */
  public String toHtml(byte[] document,
                       byte[] validationReport,
                       Locale locale) {
    Objects.requireNonNull(document,
                           "document");
    Objects.requireNonNull(locale,
                           "locale");
    String language = LANGUAGES.contains(locale.getLanguage()) ?
                      locale.getLanguage() :
                      "en";
    var html = new StringBuilder(script.length() + document.length * 4 / 3 + 2048);
    html.append("<!doctype html>\n")
        .append("<html lang=\"")
        .append(language)
        .append("\">\n")
        .append("<head>\n")
        .append("<meta charset=\"utf-8\">\n")
        .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n")
        .append("<meta name=\"generator\" content=\"Paniolo\">\n")
        .append("<title>Paniolo</title>\n")
        .append("<style>html,body{margin:0;background:Canvas;color:CanvasText}")
        .append("body{padding:16px}@media print{body{padding:0}}</style>\n")
        .append("</head>\n")
        .append("<body>\n")
        .append("<")
        .append(TAG_NAME)
        .append(" lang=\"")
        .append(language)
        .append("\"></")
        .append(TAG_NAME)
        .append(">\n")
        .append("<script type=\"application/octet-stream\" id=\"paniolo-document\">")
        .append(Base64.getEncoder()
                      .encodeToString(document))
        .append("</script>\n");
    if (validationReport != null) {
      html.append("<script type=\"application/octet-stream\" id=\"paniolo-report\">")
          .append(Base64.getEncoder()
                        .encodeToString(validationReport))
          .append("</script>\n");
    }
    html.append("<script type=\"module\">\n")
        .append(escapeScript(script))
        .append("\n")
        .append("const bytes = id => {\n")
        .append("  const data = document.getElementById(id);\n")
        .append("  return data ? Uint8Array.from(atob(data.textContent.trim()), c => c.charCodeAt(0)) : null;\n")
        .append("};\n")
        .append("const viewer = document.querySelector('")
        .append(TAG_NAME)
        .append("');\n")
        .append("viewer.addEventListener('paniolo-load', e => { document.title = e.detail.title; });\n")
        .append("viewer.load(bytes('paniolo-document'), { report: bytes('paniolo-report') }).catch(() => {});\n")
        .append("</script>\n")
        .append("</body>\n")
        .append("</html>\n");
    return html.toString();
  }
}

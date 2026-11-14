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
package io.github.nalukit.paniolo.visualizer.browser;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Download;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
                         import java.nio.file.Files;
import java.util.regex.Matcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the real parser and the web component {@code <paniolo-viewer>} in a browser (Playwright):
 * UBL, CII, and PDF documents load; sub-lines, attachments, and the validation report are shown; the
 * language switches; text from an invoice is rendered as text; broken input ends in the error view.
 * Compiled and run with {@code -P browser-tests} only, see the pom.
 */
class ViewerBrowserTest {
  private static final String SCRIPT = "/META-INF/resources/paniolo-visualizer/paniolo-visualizer.js";
  private static final String PAGE   = """
      <!doctype html><html lang="de"><meta charset="utf-8"><body>
      <paniolo-viewer id="v" lang="de" dropzone></paniolo-viewer>
      <script type="module">
        import '/paniolo-visualizer.js';
        await customElements.whenDefined('paniolo-viewer');
        const v = document.getElementById('v');
        window.events = [];
        v.addEventListener('paniolo-error', e => window.events.push('error:' + e.detail.code));
        v.addEventListener('paniolo-load', e => window.events.push('load:' + e.detail.model.number));
        window.fetchBytes = async url => new Uint8Array(await (await fetch(url)).arrayBuffer());
        window.loadUrl = async (url, reportUrl) => {
          try {
            await v.load(await window.fetchBytes(url), reportUrl ? {report: await window.fetchBytes(reportUrl)} : undefined);
            return 'ok';
          } catch (e) {
            return 'error:' + (e.code || e.message);
          }
        };
        window.realFetch = window.fetch.bind(window);
        // answers of fetch under the control of the test: nothing arrives before window.answer or window.fail
        window.stubFetch = () => {
          window.pending = {};
          window.fetchCalls = [];
          window.fetch = url => new Promise((resolve, reject) => {
            window.fetchCalls.push(String(url));
            window.pending[url] = {resolve, reject};
          });
        };
        window.answer = (url, bytes) => window.pending[url].resolve(new Response(bytes));
        window.answerStatus = (url, status) => window.pending[url].resolve(new Response('', {status}));
        window.fail = url => window.pending[url].reject(new TypeError('network down'));
        window.invoice = async number => {
          const text = await (await window.realFetch('/samples/invoice-ubl.xml')).text();
          return new TextEncoder().encode(text.replace('<cbc:ID>000008.24.06</cbc:ID>', '<cbc:ID>' + number + '</cbc:ID>'));
        };
        window.reportOf = async profile => {
          const text = await (await window.realFetch('/samples/validation-report.xml')).text();
          return new TextEncoder().encode(text.replace('<profile>EN16931 XRechnung (CII)</profile>', '<profile>' + profile + '</profile>'));
        };
        window.later = bytes => {
          let release;
          const response = new Response('');
          const body = new Promise(resolve => release = () => resolve(bytes.buffer));
          response.arrayBuffer = () => body;
          return {response, release};
        };
        window.tick = () => new Promise(resolve => setTimeout(resolve, 50));
        window.ready = true;
      </script></body></html>
      """;

  private static HttpServer server;
  private static String     base;
  private static Playwright  playwright;
  private static Browser     browser;

  private Page         page;
  private List<String> problems;

  @BeforeAll
  static void start() throws IOException {
    server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(),
                                                     0),
                               0);
    server.createContext("/",
                         exchange -> {
                           String path = exchange.getRequestURI()
                                                 .getPath();
                           byte[] body;
                           String type;
                           if (path.equals("/favicon.ico")) {
                             exchange.sendResponseHeaders(204,
                                                          -1);
                             exchange.close();
                             return;
                           }
                           if (path.equals("/")) {
                             body = PAGE.getBytes(StandardCharsets.UTF_8);
                             type = "text/html; charset=utf-8";
                           } else if (path.equals("/paniolo-visualizer.js")) {
                             body = resource(SCRIPT);
                             type = "text/javascript; charset=utf-8";
                           } else if (path.equals("/samples/xss.xml")) {
                             body = untrusted();
                             type = "application/xml";
                           } else if (path.startsWith("/pdf/")) {
                             body = pdfVariant(path.substring(5));
                             type = "application/pdf";
                           } else if (path.startsWith("/enc/")) {
                             body = encoded(path.substring(5));
                             type = "application/octet-stream";
                           } else if (path.startsWith("/formats/")) {
                             body = formats(path.substring(9));
                             type = "application/xml";
                           } else if (path.startsWith("/bad/")) {
                             body = bad(path.substring(5));
                             type = "application/octet-stream";
                           } else if (path.startsWith("/samples/") && !path.contains("..")) {
                             body = resource(path);
                             type = "application/octet-stream";
                           } else {
                             body = null;
                             type = null;
                           }
                           if (body == null) {
                             exchange.sendResponseHeaders(404,
                                                          -1);
                           } else {
                             exchange.getResponseHeaders()
                                     .add("Content-Type",
                                          type);
                             exchange.sendResponseHeaders(200,
                                                          body.length);
                             exchange.getResponseBody()
                                     .write(body);
                           }
                           exchange.close();
                         });
    server.start();
    base       = "http://127.0.0.1:" + server.getAddress()
                                             .getPort();
    playwright = Playwright.create();
    var options = new BrowserType.LaunchOptions();
    String channel = System.getProperty("paniolo.browser.channel");
    if (channel != null && !channel.isBlank() && !channel.startsWith("${")) {
      options.setChannel(channel);
    }
    browser = playwright.chromium()
                        .launch(options);
  }

  @AfterAll
  static void stop() {
    if (browser != null) {
      browser.close();
    }
    if (playwright != null) {
      playwright.close();
    }
    if (server != null) {
      server.stop(0);
    }
  }

  @BeforeEach
  void open() {
    problems = new ArrayList<>();
    page     = browser.newPage();
    page.onPageError(error -> problems.add("page error: " + error));
    page.onConsoleMessage(message -> {
      if ("error".equals(message.type())) {
        problems.add("console: " + message.text());
      }
    });
    page.navigate(base + "/");
    page.waitForFunction("window.ready === true");
  }

  @AfterEach
  void close() {
    page.close();
  }

  private static byte[] resource(String name) throws IOException {
    try (InputStream input = ViewerBrowserTest.class.getResourceAsStream(name)) {
      return input == null ?
             null :
             input.readAllBytes();
    }
  }

  /**
   * An invoice whose names carry markup and a script; as text they must stay text.
   */
  private static byte[] untrusted() throws IOException {
    String xml = new String(resource("/samples/invoice-ubl.xml"),
                            StandardCharsets.UTF_8);
    return xml.replace("Coder's Heaven Wear GmbH",
                       "&lt;img src=x onerror=&quot;window.__xss=1&quot;&gt;Evil &amp; Co")
              .replaceFirst("(?s)(<cac:Item>.*?<cbc:Name>).*?(</cbc:Name>)",
                            "$1&lt;script&gt;window.__xss=2&lt;/script&gt;$2")
              .getBytes(StandardCharsets.UTF_8);
  }

  /**
   * The Factur-X sample with a harmless /Encrypt text, and minimal PDFs that do declare encryption in
   * the trailer or in a cross-reference stream.
   */
  private static byte[] pdfVariant(String kind) throws IOException {
    String pdf = new String(resource("/samples/invoice-facturx.pdf"),
                            StandardCharsets.ISO_8859_1);
    String changed = switch (kind) {
      case "comment" -> pdf.substring(0,
                                      pdf.lastIndexOf("%%EOF")) + "% harmless comment: /Encrypt 12 0 R\n%%EOF\n";
      case "comment-trailer" -> pdf.substring(0,
                                              pdf.lastIndexOf("%%EOF")) + "% trailer << /Encrypt 12 0 R >>\n%%EOF\n";
      case "trailer" -> "%PDF-1.7\n1 0 obj\n<</Type/Catalog/Pages 2 0 R>>\nendobj\n2 0 obj\n<</Type/Pages/Kids[]/Count 0>>\nendobj\n" +
                        "trailer\n<</Root 1 0 R/Size 8/Encrypt 7 0 R>>\n%%EOF\n";
      case "xref-stream" -> "%PDF-1.7\n1 0 obj\n<</Type/Catalog/Pages 2 0 R>>\nendobj\n2 0 obj\n<</Type/Pages/Kids[]/Count 0>>\nendobj\n" +
                            "5 0 obj\n<</Type/XRef/Size 8/W[1 2 1]/Root 1 0 R/Encrypt 7 0 R/Length 0>>\nstream\n\nendstream\nendobj\nstartxref\n0\n%%EOF\n";
      default -> null;
    };
    return changed == null ?
           null :
           changed.getBytes(StandardCharsets.ISO_8859_1);
  }

  private static final String NAME = "Müller &amp; Söhne GmbH";

  /**
   * One invoice (or report) with a declared encoding, as bytes of exactly that encoding.
   */
  private static byte[] encoded(String kind) throws IOException {
    String ubl = new String(resource("/samples/invoice-ubl.xml"),
                            StandardCharsets.UTF_8).replace("Coder's Heaven Wear GmbH",
                                                            NAME);
    String report = new String(resource("/samples/validation-report.xml"),
                               StandardCharsets.UTF_8).replace("<profile>EN16931 XRechnung (CII)</profile>",
                                                               "<profile>Prüfung &amp; Bericht</profile>");
    return switch (kind) {
      case "latin1" -> declared(ubl,
                                "ISO-8859-1").getBytes(StandardCharsets.ISO_8859_1);
      case "utf16le" -> declared(ubl,
                                 "UTF-16LE").getBytes(StandardCharsets.UTF_16LE);
      case "utf16be" -> declared(ubl,
                                 "UTF-16BE").getBytes(StandardCharsets.UTF_16BE);
      case "utf8" -> ubl.getBytes(StandardCharsets.UTF_8);
      case "unsupported" -> declared(ubl,
                                     "x-not-an-encoding").getBytes(StandardCharsets.ISO_8859_1);
      case "report-latin1" -> declared(report,
                                       "ISO-8859-1").getBytes(StandardCharsets.ISO_8859_1);
      case "report-utf16le" -> declared(report,
                                        "UTF-16LE").getBytes(StandardCharsets.UTF_16LE);
      // PDF strings: \200 and \240 are bullet and euro in PDFDocEncoding, neither is Latin-1
      case "pdf-latin1" -> pdfWith("(\\200-\\240-x.xml)",
                                   encoded("latin1"));
      case "pdf-utf16" -> pdfWith("<FEFF20AC002D2022002E0078006D006C>",
                                  encoded("utf8"));
      case "bad-utf8" -> corrupt(ubl.getBytes(StandardCharsets.UTF_8),
                                 0xff);
      case "bad-utf16" -> {
        // a lone high surrogate, little endian
        yield corrupt(declared(ubl,
                               "UTF-16LE").getBytes(StandardCharsets.UTF_16LE),
                      0x00,
                      0xd8);
      }
      case "bad-bom" -> {
        byte[] bytes = corrupt(ubl.getBytes(StandardCharsets.UTF_8),
                               0xc3,
                               0x28);
        byte[] bom = new byte[bytes.length + 3];
        bom[0] = (byte) 0xef;
        bom[1] = (byte) 0xbb;
        bom[2] = (byte) 0xbf;
        System.arraycopy(bytes,
                         0,
                         bom,
                         3,
                         bytes.length);
        yield bom;
      }
      case "fffd" -> ubl.replace(NAME,
                                 "M\ufffdller &amp; S\u00f6hne GmbH")
                        .getBytes(StandardCharsets.UTF_8);
      case "report-bad" -> corrupt(report.getBytes(StandardCharsets.UTF_8),
                                   0xff);
      case "pdf-bad" -> pdfWith("(x.xml)",
                                encoded("bad-utf8"));
      // streams: direct length, indirect length defined later, and the word endstream in the data
      case "pdf-direct" -> pdfWith("(x.xml)",
                                   endstreamInvoice(),
                                   null);
      case "pdf-indirect" -> pdfWith("(x.xml)",
                                     endstreamInvoice(),
                                     "6 0 R");
      case "pdf-binary" -> pdfWith("(x.xml)",
                                   endstreamInvoice(),
                                   "6 0 R",
                                   "a endstream b\nendstream\u0000".getBytes(StandardCharsets.ISO_8859_1));
      case "pdf-wrong-length" -> pdfWith("(x.xml)",
                                         endstreamInvoice(),
                                         "99999");
      case "pdf-no-end" -> {
        byte[] pdf = pdfWith("(x.xml)",
                             endstreamInvoice(),
                             "99999");
        String text = new String(pdf,
                                 StandardCharsets.ISO_8859_1);
        yield text.replace("endstream",
                           "endstrea_")
                  .getBytes(StandardCharsets.ISO_8859_1);
      }
      default -> null;
    };
  }

  private static byte[] endstreamInvoice() throws IOException {
    return new String(resource("/samples/invoice-ubl.xml"),
                      StandardCharsets.UTF_8).replace("Coder's Heaven Wear GmbH",
                                                      "A endstream B")
                                             .getBytes(StandardCharsets.UTF_8);
  }

  /**
   * Overwrites the bytes at the first "ller" (in the buyer name) or, in the report, after "<profile>".
   */
  private static byte[] corrupt(byte[] bytes,
                                int... replacement) {
    int at = indexOf(bytes,
                     "ller".getBytes(StandardCharsets.ISO_8859_1));
    if (at < 0) {
      at = indexOf(bytes,
                   "ller".getBytes(StandardCharsets.UTF_16LE));
    }
    if (at < 0) {
      at = indexOf(bytes,
                   "<profile>".getBytes(StandardCharsets.ISO_8859_1)) + 9;
    }
    byte[] out = bytes.clone();
    for (int i = 0; i < replacement.length; i++) {
      out[at + i] = (byte) replacement[i];
    }
    return out;
  }

  private static int indexOf(byte[] data,
                             byte[] pattern) {
    outer:
    for (int i = 0; i + pattern.length <= data.length; i++) {
      for (int j = 0; j < pattern.length; j++) {
        if (data[i + j] != pattern[j]) {
          continue outer;
        }
      }
      return i;
    }
    return -1;
  }

  private static String declared(String xml,
                                 String encoding) {
    return xml.replace("encoding=\"UTF-8\"",
                       "encoding=\"" + encoding + "\"");
  }

  /**
   * A minimal PDF with one embedded file whose file name is the given PDF string.
   */
  private static byte[] pdfWith(String fileName,
                                byte[] data) throws IOException {
    return pdfWith(fileName,
                   data,
                   null);
  }

  private static byte[] pdfWith(String fileName,
                                byte[] data,
                                String length) throws IOException {
    return pdfWith(fileName,
                   data,
                   length,
                   null);
  }

  /**
   * A PDF with the invoice as embedded file; length is null (direct, exact), "6 0 R" (an integer
   * object after the stream), or the text given. With binary, a second file follows.
   */
  private static byte[] pdfWith(String fileName,
                                byte[] data,
                                String length,
                                byte[] binary) throws IOException {
    boolean indirect = length != null && length.endsWith(" R");
    var     out      = new java.io.ByteArrayOutputStream();
    String names = binary == null ?
                   "(a) 4 0 R" :
                   "(a) 4 0 R (b) 7 0 R";
    out.write(("%PDF-1.7\n1 0 obj\n<</Type/Catalog/Pages 2 0 R/Names<</EmbeddedFiles<</Names[" + names + "]>>>>>>\nendobj\n" +
               "2 0 obj\n<</Type/Pages/Kids[]/Count 0>>\nendobj\n" +
               "4 0 obj\n<</Type/Filespec/F " + fileName + "/UF " + fileName + "/EF<</F 5 0 R>>>>\nendobj\n" +
               "5 0 obj\n<</Type/EmbeddedFile/Subtype/text#2Fxml/Length " + (length == null ?
                                                                              data.length :
                                                                              length) + ">>\nstream\n").getBytes(StandardCharsets.ISO_8859_1));
    out.write(data);
    out.write("\nendstream\nendobj\n".getBytes(StandardCharsets.ISO_8859_1));
    if (binary != null) {
      out.write(("7 0 obj\n<</Type/Filespec/F (blob.bin)/UF (blob.bin)/EF<</F 8 0 R>>>>\nendobj\n" +
                 "8 0 obj\n<</Type/EmbeddedFile/Subtype/application#2Foctet-stream/Length 9 0 R>>\nstream\n").getBytes(StandardCharsets.ISO_8859_1));
      out.write(binary);
      out.write(("\nendstream\nendobj\n9 0 obj\n" + binary.length + "\nendobj\n").getBytes(StandardCharsets.ISO_8859_1));
    }
    if (indirect) {
      out.write(("6 0 obj\n" + data.length + "\nendobj\n").getBytes(StandardCharsets.ISO_8859_1));
    }
    out.write("trailer\n<</Root 1 0 R/Size 10>>\n%%EOF\n".getBytes(StandardCharsets.ISO_8859_1));
    return out.toByteArray();
  }

  /**
   * The sample invoices with calendar values and decimals that the display must not change.
   */
  private static byte[] formats(String kind) throws IOException {
    String ubl = new String(resource("/samples/invoice-ubl.xml"),
                            StandardCharsets.UTF_8);
    String cii = new String(resource("/samples/invoice-cii.xml"),
                            StandardCharsets.UTF_8);
    String xml = switch (kind) {
      // 30 February, and year 26: shown as written, not as 2 March 2026 or 1 January 1926
      case "invalid-dates" -> ubl.replace("<cbc:IssueDate>2024-08-28</cbc:IssueDate>",
                                         "<cbc:IssueDate>2026-02-30</cbc:IssueDate>")
                                 .replace("<cbc:DueDate>2024-09-27</cbc:DueDate>",
                                          "<cbc:DueDate>0026-01-01</cbc:DueDate>");
      case "leap-day" -> ubl.replace("<cbc:IssueDate>2024-08-28</cbc:IssueDate>",
                                     "<cbc:IssueDate>2028-02-29</cbc:IssueDate>")
                            .replace("<cbc:DueDate>2024-09-27</cbc:DueDate>",
                                     "<cbc:DueDate>2027-02-29</cbc:DueDate>");
      case "cii-204" -> cii.replaceFirst("<udt:DateTimeString format=\"102\">20240828</udt:DateTimeString>",
                                         "<udt:DateTimeString format=\"204\">20261114153045</udt:DateTimeString>");
      case "cii-203" -> cii.replaceFirst("<udt:DateTimeString format=\"102\">20240828</udt:DateTimeString>",
                                         "<udt:DateTimeString format=\"203\">202611141530</udt:DateTimeString>");
      case "cii-bad-hour" -> cii.replaceFirst("<udt:DateTimeString format=\"102\">20240828</udt:DateTimeString>",
                                              "<udt:DateTimeString format=\"204\">20261114253045</udt:DateTimeString>");
      case "indicators-numeric" -> display("1");
      case "indicators-zero" -> display("0");
      case "indicators-bad" -> display("TRUE");
      case "charge-numeric" -> charge("1");
      case "charge-bad" -> charge("yes");
      case "sublines-ubl" -> {
        String sub = "<cac:SubInvoiceLine><cbc:ID>X</cbc:ID><cbc:LineExtensionAmount currencyID=\"EUR\">1.00</cbc:LineExtensionAmount>" +
                     "<cac:Item><cbc:Name>Sub %s</cbc:Name></cac:Item>%s</cac:SubInvoiceLine>";
        String nested = sub.formatted("2.1",
                                      "");
        yield ubl.replaceFirst("</cac:InvoiceLine>",
                               Matcher.quoteReplacement(sub.formatted("1",
                                                                      "") + sub.formatted("2",
                                                                                          nested) + "</cac:InvoiceLine>"));
      }
      case "sublines-report" -> """
          <report xmlns="urn:io:github:nalukit:paniolo:validation-report:1" created="2026-11-14T10:00:00Z" generator="test">
            <profile>test</profile>
            <summary errors="3" fatals="0" infos="0" valid="false" warnings="0"/>
            <stage name="SCHEMATRON" valid="false">
              <message location="/Invoice/InvoiceLine[1]/SubInvoiceLine[2]/Item[1]" rule="X-SUB" severity="ERROR">finding of the second sub-line</message>
              <message location="/Q{urn:x}Invoice[1]/Q{urn:y}InvoiceLine[1]/Q{urn:y}SubInvoiceLine[2]/Q{urn:y}SubInvoiceLine[1]" rule="X-SUBSUB" severity="ERROR">finding of the nested sub-line</message>
              <message location="/Invoice/InvoiceLine[2]" rule="X-TOP" severity="ERROR">finding of the second line</message>
            </stage>
          </report>
          """;
      case "hierarchy-ok" -> subLines();
      case "hierarchy-self" -> subLines().replaceFirst("<ram:ParentLineID>1</ram:ParentLineID>",
                                                       "<ram:ParentLineID>2</ram:ParentLineID>");
      case "hierarchy-cycle" -> subLines().replaceFirst("<ram:LineID>1</ram:LineID>",
                                                        "<ram:LineID>1</ram:LineID><ram:ParentLineID>2</ram:ParentLineID>");
      case "hierarchy-missing" -> subLines().replaceFirst("<ram:ParentLineID>1</ram:ParentLineID>",
                                                          "<ram:ParentLineID>99</ram:ParentLineID>");
      case "hierarchy-ambiguous" -> subLines().replaceFirst("<ram:LineID>4</ram:LineID>",
                                                            "<ram:LineID>1</ram:LineID>");
      case "decimals" -> ubl.replace("<cbc:PriceAmount currencyID=\"EUR\">46.0000</cbc:PriceAmount>",
                                     "<cbc:PriceAmount currencyID=\"EUR\">46.000000000000000000000001</cbc:PriceAmount>")
                            .replace("<cbc:InvoicedQuantity unitCode=\"HUR\">8.0000</cbc:InvoicedQuantity>",
                                     "<cbc:InvoicedQuantity unitCode=\"HUR\">8.123456789012345678901234</cbc:InvoicedQuantity>")
                            .replace("<cbc:PriceAmount currencyID=\"EUR\">125.0000</cbc:PriceAmount>",
                                     "<cbc:PriceAmount currencyID=\"EUR\">12345678901234567890123.45</cbc:PriceAmount>");
      default -> null;
    };
    return xml == null ?
           null :
           xml.getBytes(StandardCharsets.UTF_8);
  }

  private static String subLines() throws IOException {
    return new String(resource("/samples/invoice-extended-sub-lines.xml"),
                      StandardCharsets.UTF_8);
  }

  private static String charge(String value) throws IOException {
    return new String(resource("/samples/invoice-extended-display.xml"),
                      StandardCharsets.UTF_8).replaceFirst("(<ram:ChargeIndicator>\\s*<udt:Indicator>)false(</udt:Indicator>)",
                                                           "$1" + value + "$2");
  }

  private static String display(String value) throws IOException {
    return new String(resource("/samples/invoice-extended-display.xml"),
                      StandardCharsets.UTF_8).replace("<udt:Indicator>true</udt:Indicator>",
                                                      "<udt:Indicator>" + value + "</udt:Indicator>");
  }

  private static byte[] bad(String kind) {
    return switch (kind) {
      case "empty" -> new byte[0];
      case "notxml" -> "this is <not xml".getBytes(StandardCharsets.UTF_8);
      case "unknown" -> "<foo><bar/></foo>".getBytes(StandardCharsets.UTF_8);
      case "pdf" -> "%PDF-1.4\n1 0 obj\n<< /Type /Catalog >>\nendobj\ntrailer\n<< /Root 1 0 R >>\n%%EOF\n".getBytes(StandardCharsets.UTF_8);
      default -> null;
    };
  }

  private String load(String sample) {
    return (String) page.evaluate("url => window.loadUrl(url)",
                                  base + "/samples/" + sample);
  }

  private String viewerText() {
    return (String) page.evaluate("() => document.getElementById('v').shadowRoot.textContent");
  }

  private void assertNoProblems() {
    assertEquals(List.of(),
                 problems);
  }

  @Test
  void loadsUblCiiAndPdfWithTheRealParser() {
    var expected = Map.of("invoice-ubl.xml",
                          "Coder's Heaven Wear GmbH",
                          "invoice-cii.xml",
                          "Test Mandant 03 - 01",
                          "invoice-facturx.pdf",
                          "");
    for (var sample : expected.keySet()) {
      assertEquals("ok",
                   load(sample),
                   sample);
      assertFalse(((String) page.evaluate("() => document.getElementById('v').model.number || ''")).isBlank(),
                  sample);
      long lines = ((Number) page.evaluate("() => document.getElementById('v').model.lines.length")).longValue();
      assertTrue(lines > 0,
                 sample + " has lines");
      assertTrue(page.locator("paniolo-viewer [role=tab]")
                     .count() >= 3,
                 sample);
      String number = (String) page.evaluate("() => document.getElementById('v').model.number");
      assertTrue(viewerText().contains(number),
                 sample + " shows its number " + number);
    }
    assertTrue(viewerText().length() > 100);
    assertTrue(((Boolean) page.evaluate("() => window.events.filter(e => e.startsWith('load:')).length === 3")));
    assertNoProblems();
  }

  @Test
  void showsSubLinesAndTheirHierarchy() {
    assertEquals("ok",
                 load("invoice-extended-sub-lines.xml"));
    page.locator("paniolo-viewer [role=tab][data-tab=lines]")
        .click();
    long rows = ((Number) page.evaluate("() => document.getElementById('v').shadowRoot.querySelectorAll('#panel-lines tbody tr, #panel-lines .card').length")).longValue();
    long lines = ((Number) page.evaluate("() => document.getElementById('v').model.lines.length")).longValue();
    assertTrue(lines > 1,
               "lines " + lines);
    assertTrue(rows >= lines,
               "rows " + rows + " for " + lines + " lines");
    assertTrue(((Boolean) page.evaluate("() => document.getElementById('v').model.lines.some(l => l.parentId || l.parent || (l.subLines && l.subLines.length) || l.depth > 0 || l.level > 0)")),
               "a line states its place in the hierarchy");
    assertNoProblems();
  }

  @Test
  void showsAttachmentsOfThePdfAndOfTheXml() {
    assertEquals("ok",
                 load("invoice-facturx.pdf"));
    long attachments = ((Number) page.evaluate("() => document.getElementById('v').model.attachments.length")).longValue();
    assertTrue(attachments >= 1,
               "attachments " + attachments);
    page.locator("paniolo-viewer [role=tab][data-tab=attachments]")
        .click();
    assertTrue(page.locator("paniolo-viewer #panel-attachments")
                   .isVisible());
    assertFalse(page.locator("paniolo-viewer #panel-attachments")
                    .innerText()
                    .isBlank());
    assertNoProblems();
  }

  @Test
  void showsTheValidationReport() {
    assertEquals("ok",
                 page.evaluate("([a, b]) => window.loadUrl(a, b)",
                               List.of(base + "/samples/invoice-invalid-cii.xml",
                                       base + "/samples/validation-report.xml")));
    page.locator("paniolo-viewer [role=tab][data-tab=validation]")
        .click();
    String panel = page.locator("paniolo-viewer #panel-validation")
                       .innerText();
    assertTrue(panel.contains("SCHEMATRON") || panel.toLowerCase()
                                                  .contains("schematron") || panel.contains("BR-"),
               panel);
    // The report of the sample states 19 findings in its stages; the tab counts them.
    long findings = ((Number) page.evaluate("() => document.getElementById('v').report.stages.reduce((n, s) => n + s.messages.length, 0)")).longValue();
    assertEquals(19,
                 findings);
    assertEquals(Boolean.FALSE,
                 page.evaluate("() => document.getElementById('v').report.valid"));
    assertTrue(page.locator("paniolo-viewer [role=tab][data-tab=validation]")
                   .innerText()
                   .contains("19"));
    assertNoProblems();
  }

  @Test
  void switchesTheLanguage() {
    assertEquals("ok",
                 load("invoice-ubl.xml"));
    var tabs = page.locator("paniolo-viewer [role=tab][data-tab=lines]");
    assertEquals("Positionen",
                 tabs.innerText()
                     .replaceAll("\\d+$",
                                 "")
                     .strip());
    page.evaluate("() => document.getElementById('v').setAttribute('lang', 'en')");
    assertTrue(tabs.innerText()
                   .startsWith("Lines"),
               tabs.innerText());
    page.evaluate("() => document.getElementById('v').setAttribute('lang', 'fr')");
    assertFalse(tabs.innerText()
                    .startsWith("Lines") || tabs.innerText()
                                                .startsWith("Positionen"),
                tabs.innerText());
    page.evaluate("() => document.getElementById('v').setAttribute('lang', 'de')");
    assertTrue(tabs.innerText()
                   .startsWith("Positionen"));
    assertNoProblems();
  }

  @Test
  void rendersUntrustedTextAsText() {
    assertEquals("ok",
                 load("xss.xml"));
    page.locator("paniolo-viewer [role=tab][data-tab=lines]")
        .click();
    page.waitForTimeout(200);
    assertTrue(viewerText().contains("<img src=x onerror=\"window.__xss=1\">Evil & Co"),
               "the seller name is shown as text");
    assertTrue(viewerText().contains("<script>window.__xss=2</script>"),
               "the item name is shown as text");
    assertEquals(0,
                 ((Number) page.evaluate("() => document.getElementById('v').shadowRoot.querySelectorAll('img[src=x], script').length")).intValue());
    assertEquals(Boolean.TRUE,
                 page.evaluate("() => typeof window.__xss === 'undefined'"));
    assertNoProblems();
  }

  private String shown(String kind) {
    assertEquals("ok",
                 page.evaluate("url => window.loadUrl(url)",
                               "/formats/" + kind));
    return (String) page.evaluate("() => document.getElementById('v').shadowRoot.textContent");
  }

  @Test
  void showsCalendarValuesAsWrittenWhenTheyDoNotExist() {
    String text = shown("invalid-dates");
    assertTrue(text.contains("2026-02-30"),
               "30 February stays as written");
    assertFalse(text.contains("02.03.2026") || text.contains("2. März 2026"),
                "no shift to March");
    assertTrue(text.contains("0026-01-01"),
               "year 26 stays as written");
    assertFalse(text.contains("1926"),
                "no shift to 1926");
    assertNoProblems();
  }

  @Test
  void formatsRealLeapDaysAndRejectsTheNonLeapOne() {
    String text = shown("leap-day");
    assertTrue(text.contains("29.02.2028") || text.contains("29. Feb. 2028"),
               "29 February 2028 exists");
    assertTrue(text.contains("2027-02-29"),
               "29 February 2027 does not exist and stays as written");
    assertNoProblems();
  }

  @Test
  void readsAndFormatsTheDateTimeFormats203And204() {
    String withSeconds = shown("cii-204");
    assertTrue(withSeconds.contains("14.11.2026") && withSeconds.contains("15:30:45"),
               "format 204 with seconds");
    String withoutSeconds = shown("cii-203");
    assertTrue(withoutSeconds.contains("14.11.2026") && withoutSeconds.contains("15:30") && !withoutSeconds.contains("15:30:"),
               "format 203");
    String bad = shown("cii-bad-hour");
    assertTrue(bad.contains("2026-11-14T25:30:45"),
               "hour 25 stays as written");
    assertNoProblems();
  }

  @Test
  void keepsEveryDigitOfLongDecimals() {
    String text = shown("decimals");
    assertTrue(text.contains("46,000000000000000000000001"),
               "price with 24 decimals");
    assertTrue(text.contains("8,123456789012345678901234"),
               "quantity with 24 decimals");
    assertTrue(text.contains("12.345.678.901.234.567.890.123,45"),
               "large integer part");
    assertNoProblems();
  }

  @Test
  void numericIndicatorsKeepTheirMeaningAndOtherTextsAreRefused() {
    String shown = shown("indicators-numeric");
    assertTrue(((Boolean) page.evaluate("() => document.getElementById('v').model.testDocument === true && document.getElementById('v').model.copy === true")),
               "1 is true for the test and the copy indicator");
    assertTrue(shown.contains("Testdokument") && shown.contains("Kopie"),
               "the warnings stay visible in the viewer");
    assertEquals("ok",
                 page.evaluate("url => window.loadUrl(url)",
                               "/formats/indicators-zero"));
    assertTrue(((Boolean) page.evaluate("() => document.getElementById('v').model.testDocument === false && document.getElementById('v').model.copy === false")),
               "0 is false");
    assertEquals("error:invalidBoolean",
                 page.evaluate("url => window.loadUrl(url)",
                               "/formats/indicators-bad"));
    assertEquals("ok",
                 page.evaluate("url => window.loadUrl(url)",
                               "/formats/charge-numeric"));
    assertTrue(((Boolean) page.evaluate("() => JSON.stringify(document.getElementById('v').model).includes('\"charge\":true')")),
               "1 is a charge");
    assertEquals("error:invalidBoolean",
                 page.evaluate("url => window.loadUrl(url)",
                               "/formats/charge-bad"));
  }

  @Test
  void aDocumentReadsTheSameFromTextBytesBlobAndResponse() {
    for (String kind : new String[] { "latin1", "utf16le", "utf16be", "utf8" }) {
      assertEquals("true,true,true,true",
                   page.evaluate("""
                                 async url => {
                                   const v = document.getElementById('v');
                                   const wanted = 'M\\u00fcller & S\\u00f6hne GmbH';
                                   const has = () => JSON.stringify(v.model).includes(wanted);
                                   const bytes = await window.fetchBytes(url);
                                   const seen = [];
                                   await v.load(bytes);
                                   seen.push(has());
                                   await v.load(new Blob([bytes]));
                                   seen.push(has());
                                   await v.load(bytes.buffer.slice(0));
                                   seen.push(has());
                                   await v.load(await fetch(url));
                                   seen.push(has());
                                   return seen.join();
                                 }
                                 """,
                                 base + "/enc/" + kind),
                   kind);
    }
    // text that is decoded already is not read again by its declaration
    assertEquals(Boolean.TRUE,
                 page.evaluate("""
                               async url => {
                                 const v = document.getElementById('v');
                                 const text = new TextDecoder('iso-8859-1').decode(await window.fetchBytes(url));
                                 await v.load(text);
                                 return JSON.stringify(v.model).includes('M\\u00fcller & S\\u00f6hne GmbH');
                               }
                               """,
                               base + "/enc/latin1"));
    assertNoProblems();
  }

  @Test
  void anUnsupportedEncodingIsRefusedAndReportsAndPdfXmlUseTheirDeclaration() {
    assertEquals("error:xml",
                 page.evaluate("url => window.loadUrl(url)",
                               base + "/enc/unsupported"));
    for (String kind : new String[] { "report-latin1", "report-utf16le" }) {
      assertEquals(Boolean.TRUE,
                   page.evaluate("""
                                 async url => {
                                   const v = document.getElementById('v');
                                   await v.load(await window.fetchBytes('/samples/invoice-cii.xml'));
                                   await v.loadReport(await window.fetchBytes(url));
                                   return JSON.stringify(v.report).includes('Pr\\u00fcfung & Bericht');
                                 }
                                 """,
                                 base + "/enc/" + kind),
                   kind);
    }
    assertEquals("ok",
                 page.evaluate("url => window.loadUrl(url)",
                               base + "/enc/pdf-latin1"));
    assertEquals(Boolean.TRUE,
                 page.evaluate("() => JSON.stringify(document.getElementById('v').model).includes('M\\u00fcller & S\\u00f6hne GmbH')"),
                 "the XML inside the PDF is decoded with its declaration");
  }

  @Test
  void pdfTextStringsUsePdfDocEncodingAndUnicodeMarks() {
    assertEquals("ok",
                 page.evaluate("url => window.loadUrl(url)",
                               base + "/enc/pdf-latin1"));
    assertEquals("•-€-x.xml",
                 page.evaluate("() => document.getElementById('v').model.xmlFileName"));
    assertEquals("ok",
                 page.evaluate("url => window.loadUrl(url)",
                               base + "/enc/pdf-utf16"));
    assertEquals("€-•.xml",
                 page.evaluate("() => document.getElementById('v').model.xmlFileName"));
    Download download = page.waitForDownload(() -> page.locator("paniolo-viewer [data-action=download-xml]")
                                                       .first()
                                                       .click());
    assertEquals("€-•.xml",
                 download.suggestedFilename());
  }

  @Test
  void damagedUnicodeSequencesAreRefusedButAGenuineReplacementCharacterIsNot() {
    for (String kind : new String[] { "bad-utf8", "bad-utf16", "bad-bom", "pdf-bad" }) {
      assertEquals("error:xml",
                   page.evaluate("url => window.loadUrl(url)",
                                 base + "/enc/" + kind),
                   kind);
    }
    assertEquals("ok",
                 page.evaluate("url => window.loadUrl(url)",
                               base + "/enc/fffd"));
    assertEquals(Boolean.TRUE,
                 page.evaluate("() => JSON.stringify(document.getElementById('v').model).includes('M\\ufffdller')"));
    assertEquals("xml",
                 page.evaluate("""
                               async url => {
                                 const v = document.getElementById('v');
                                 await v.load(await window.fetchBytes('/samples/invoice-cii.xml'));
                                 try {
                                   await v.loadReport(await window.fetchBytes(url));
                                   return 'loaded';
                                 } catch (e) {
                                   return e.code;
                                 }
                               }
                               """,
                               base + "/enc/report-bad"));
  }

  @Test
  void pdfStreamLengthsAreResolvedBeforeTheWordEndstreamIsTrusted() {
    for (String kind : new String[] { "pdf-direct", "pdf-indirect", "pdf-binary" }) {
      assertEquals("ok",
                   page.evaluate("url => window.loadUrl(url)",
                                 base + "/enc/" + kind),
                   kind);
      assertEquals(Boolean.TRUE,
                   page.evaluate("() => JSON.stringify(document.getElementById('v').model).includes('A endstream B')"),
                   kind);
    }
    // the binary file keeps every byte, also those that look like the end of the stream
    assertEquals("a endstream b\nendstream\u0000".length(),
                 ((Number) page.evaluate("() => document.getElementById('v').model.attachments.find(a => a.name === 'blob.bin').data.length")).intValue());
    assertEquals("error:pdf",
                 page.evaluate("url => window.loadUrl(url)",
                               base + "/enc/pdf-no-end"));
  }

  @Test
  void anOlderDocumentRequestNeverReplacesTheNewerChoice() {
    assertEquals("INVOICE-B|INVOICE-B|0 errors|true",
                 page.evaluate("""
                               async () => {
                                 window.stubFetch();
                                 const w = document.createElement('paniolo-viewer');
                                 const errors = [];
                                 w.addEventListener('paniolo-error', e => errors.push(e.detail.code));
                                 document.body.appendChild(w);
                                 w.setAttribute('src', '/A');
                                 w.setAttribute('src', '/B');
                                 window.answer('/B', await window.invoice('INVOICE-B'));
                                 await window.tick();
                                 const first = w.model.number;
                                 window.answer('/A', await window.invoice('INVOICE-A'));
                                 await window.tick();
                                 // a late failure of the older request shows nothing either
                                 w.setAttribute('src', '/C');
                                 w.setAttribute('src', '/D');
                                 window.answer('/D', await window.invoice('INVOICE-B'));
                                 await window.tick();
                                 window.fail('/C');
                                 await window.tick();
                                 return [first, w.model.number, errors.length + ' errors',
                                         w.shadowRoot.textContent.includes('INVOICE-B')].join('|');
                               }
                               """));
    assertEquals("empty|false",
                 page.evaluate("""
                               async () => {
                                 window.stubFetch();
                                 const w = document.createElement('paniolo-viewer');
                                 document.body.appendChild(w);
                                 w.setAttribute('src', '/E');
                                 w.clear();
                                 window.answer('/E', await window.invoice('INVOICE-E'));
                                 await window.tick();
                                 return [w.model === null ? 'empty' : 'model', w.shadowRoot.textContent.includes('INVOICE-E')].join('|');
                               }
                               """));
    assertNoProblems();
  }

  @Test
  void aReportBelongsToTheDocumentItWasStartedFor() {
    assertEquals("INVOICE-B|null",
                 page.evaluate("""
                               async () => {
                                 const w = document.getElementById('v');
                                 await w.load(await window.invoice('INVOICE-A'));
                                 const slow = window.later(await window.reportOf('REPORT-A'));
                                 const pending = w.loadReport(slow.response).catch(e => e);
                                 await w.load(await window.invoice('INVOICE-B'));
                                 slow.release();
                                 await pending;
                                 await window.tick();
                                 return w.model.number + '|' + (w.report && JSON.stringify(w.report));
                               }
                               """));
    assertEquals("REPORT-2",
                 page.evaluate("""
                               async () => {
                                 const w = document.getElementById('v');
                                 await w.load(await window.invoice('INVOICE-C'));
                                 const one = window.later(await window.reportOf('REPORT-1'));
                                 const two = window.later(await window.reportOf('REPORT-2'));
                                 const first = w.loadReport(one.response);
                                 const second = w.loadReport(two.response);
                                 two.release();
                                 await second;
                                 one.release();
                                 await first;
                                 await window.tick();
                                 return JSON.stringify(w.report).includes('REPORT-2') && !JSON.stringify(w.report).includes('REPORT-1') ?
                                        'REPORT-2' : JSON.stringify(w.report);
                               }
                               """));
    // clear() and a removed report make the late answer ineffective, and so does its late failure
    assertEquals("none|none|0",
                 page.evaluate("""
                               async () => {
                                 const w = document.getElementById('v');
                                 const errors = [];
                                 w.addEventListener('paniolo-error', e => errors.push(e.detail.code));
                                 await w.load(await window.invoice('INVOICE-D'));
                                 const slow = window.later(await window.reportOf('REPORT-D'));
                                 const pending = w.loadReport(slow.response);
                                 w.clear();
                                 slow.release();
                                 await pending;
                                 const afterClear = w.report;
                                 await w.load(await window.invoice('INVOICE-E'));
                                 const bad = window.later(new TextEncoder().encode('<nonsense'));
                                 const failing = w.loadReport(bad.response).catch(e => e);
                                 await w.loadReport(null);
                                 bad.release();
                                 await failing;
                                 await window.tick();
                                 return [afterClear === null ? 'none' : 'report', w.report === null ? 'none' : 'report', errors.length].join('|');
                               }
                               """));
  }

  @Test
  void initialSourceAndReportSourceAreLoadedInAnyOrderAndAfterwardsToo() {
    for (String order : new String[] { "doc-first", "report-first" }) {
      assertEquals("INVOICE-S|REPORT-S",
                   page.evaluate("""
                                 async order => {
                                   window.stubFetch();
                                   const w = document.createElement('paniolo-viewer');
                                   w.setAttribute('src', '/doc');
                                   w.setAttribute('report-src', '/report');
                                   document.body.appendChild(w);
                                   const answers = {
                                     'doc-first': ['/doc', '/report'],
                                     'report-first': ['/report', '/doc']
                                   }[order];
                                   for (const url of answers) {
                                     window.answer(url, url === '/doc' ? await window.invoice('INVOICE-S') : await window.reportOf('REPORT-S'));
                                     await window.tick();
                                   }
                                   return w.model.number + '|' + (JSON.stringify(w.report).includes('REPORT-S') ? 'REPORT-S' : 'none');
                                 }
                                 """,
                                 order),
                   order);
    }
    // complete markup, reconnecting, and a later change of the attribute
    assertEquals("INVOICE-M|REPORT-M|2|REPORT-N",
                 page.evaluate("""
                               async () => {
                                 window.stubFetch();
                                 const host = document.createElement('div');
                                 host.innerHTML = '<paniolo-viewer src="/doc" report-src="/report"></paniolo-viewer>';
                                 document.body.appendChild(host);
                                 const w = host.firstElementChild;
                                 window.answer('/doc', await window.invoice('INVOICE-M'));
                                 window.answer('/report', await window.reportOf('REPORT-M'));
                                 await window.tick();
                                 const shown = w.model.number + '|' + (JSON.stringify(w.report).includes('REPORT-M') ? 'REPORT-M' : 'none');
                                 host.removeChild(w);
                                 host.appendChild(w);
                                 await window.tick();
                                 const calls = window.fetchCalls.length;
                                 w.setAttribute('report-src', '/report2');
                                 window.answer('/report2', await window.reportOf('REPORT-N'));
                                 await window.tick();
                                 return shown + '|' + calls + '|' + (JSON.stringify(w.report).includes('REPORT-N') ? 'REPORT-N' : 'none');
                               }
                               """));
    assertNoProblems();
  }

  @Test
  void downloadedXmlCanBeReadAgainWithItsDeclaration() throws IOException {
    for (String kind : new String[] { "latin1", "utf16le", "utf16be", "utf8", "pdf-latin1" }) {
      assertEquals("ok",
                   page.evaluate("url => window.loadUrl(url)",
                                 base + "/enc/" + kind),
                   kind);
      Download download = page.waitForDownload(() -> page.locator("paniolo-viewer [data-action=download-xml]")
                                                         .first()
                                                         .click());
      String saved = Files.readString(download.path(),
                                      StandardCharsets.UTF_8);
      assertTrue(saved.contains("Müller &amp; Söhne GmbH"),
                 kind + " keeps the name");
      assertTrue(saved.startsWith("<?xml") && saved.contains("encoding=\"UTF-8\""),
                 kind + " declares what it is: " + saved.substring(0,
                                                                   60));
      // the saved file is a document again, with the same buyer
      assertEquals(Boolean.TRUE,
                   page.evaluate("""
                                 async text => {
                                   const v = document.getElementById('v');
                                   await v.load(new TextEncoder().encode(text));
                                   return JSON.stringify(v.model).includes('M\\u00fcller & S\\u00f6hne GmbH');
                                 }
                                 """,
                                 saved),
                   kind);
    }
  }

  @Test
  void anOlderFileSelectionNeverReplacesANewerChoice() {
    String setup = """
        window.fileOf = (name, bytes, slow) => {
          const file = new File([bytes], name);
          let release = () => {};
          if (slow) {
            const body = new Promise(resolve => release = () => resolve(bytes.buffer));
            file.arrayBuffer = () => body;
          }
          return {file, release};
        };
        window.drop = (v, ...files) => {
          const transfer = new DataTransfer();
          files.forEach(f => transfer.items.add(f));
          v.dispatchEvent(new DragEvent('drop', {dataTransfer: transfer, bubbles: true, cancelable: true}));
        };
        """;
    page.evaluate("() => {" + setup + "}");
    // two drops, the first one is read last
    assertEquals("INVOICE-B",
                 page.evaluate("""
                               async () => {
                                 const v = document.getElementById('v');
                                 const slow = window.fileOf('slow.xml', await window.invoice('INVOICE-A'), true);
                                 const fast = window.fileOf('fast.xml', await window.invoice('INVOICE-B'), false);
                                 window.drop(v, slow.file);
                                 window.drop(v, fast.file);
                                 await window.tick();
                                 slow.release();
                                 await window.tick();
                                 return v.model.number;
                               }
                               """));
    // clear() and a document chosen by the API win over a file that is still read
    assertEquals("empty|INVOICE-API",
                 page.evaluate("""
                               async () => {
                                 const v = document.getElementById('v');
                                 const one = window.fileOf('one.xml', await window.invoice('INVOICE-F'), true);
                                 window.drop(v, one.file);
                                 v.clear();
                                 one.release();
                                 await window.tick();
                                 const cleared = v.model === null ? 'empty' : v.model.number;
                                 const two = window.fileOf('two.xml', await window.invoice('INVOICE-G'), true);
                                 window.drop(v, two.file);
                                 await v.load(await window.invoice('INVOICE-API'));
                                 two.release();
                                 await window.tick();
                                 return cleared + '|' + v.model.number;
                               }
                               """));
    // the file input behaves in the same way, and a document with its report still works together
    assertEquals("INVOICE-I|REPORT-I|INVOICE-I|REPORT-J",
                 page.evaluate("""
                               async () => {
                                 const v = document.getElementById('v');
                                 v.clear();
                                 const choose = (...files) => {
                                   // the picker exists while nothing is shown; later selections take the same path
                                   const input = v.shadowRoot.querySelector('input[type=file]');
                                   if (!input) {
                                     return v._loadFiles(files);
                                   }
                                   const transfer = new DataTransfer();
                                   files.forEach(f => transfer.items.add(f));
                                   input.files = transfer.files;
                                   input.dispatchEvent(new Event('change', {bubbles: true}));
                                 };
                                 const old = window.fileOf('old.xml', await window.invoice('INVOICE-H'), true);
                                 choose(old.file);
                                 const doc = window.fileOf('doc.xml', await window.invoice('INVOICE-I'), false);
                                 const report = window.fileOf('report.xml', await window.reportOf('REPORT-I'), false);
                                 choose(doc.file, report.file);
                                 await window.tick();
                                 old.release();
                                 await window.tick();
                                 const both = v.model.number + '|' + (JSON.stringify(v.report).includes('REPORT-I') ? 'REPORT-I' : 'none');
                                 // only a report: it goes to the shown document
                                 const next = window.fileOf('next.xml', await window.reportOf('REPORT-J'), false);
                                 choose(next.file);
                                 await window.tick();
                                 return both + '|' + v.model.number + '|' + (JSON.stringify(v.report).includes('REPORT-J') ? 'REPORT-J' : 'none');
                               }
                               """));
    assertNoProblems();
  }

  @Test
  void aCorrectReportOrItsRemovalEndsTheErrorOfAFailedReport() {
    assertEquals("INVOICE-R|error|GOOD-REPORT|INVOICE-R|false",
                 page.evaluate("""
                               async () => {
                                 const v = document.getElementById('v');
                                 await v.load(await window.invoice('INVOICE-R'));
                                 await v.loadReport('<broken').catch(() => {});
                                 const failed = v.shadowRoot.querySelector('.state.error') ? 'error' : 'no error';
                                 const report = await v.loadReport(await window.reportOf('GOOD-REPORT'));
                                 return [v.model.number, failed, report.profile, v.shadowRoot.textContent.includes('INVOICE-R') ? v.model.number : 'missing',
                                         !!v.shadowRoot.querySelector('.state.error')].join('|');
                               }
                               """));
    assertEquals("error|INVOICE-S|false",
                 page.evaluate("""
                               async () => {
                                 const v = document.getElementById('v');
                                 await v.load(await window.invoice('INVOICE-S'));
                                 await v.loadReport('<broken').catch(() => {});
                                 const failed = v.shadowRoot.querySelector('.state.error') ? 'error' : 'no error';
                                 await v.loadReport(null);
                                 return [failed, v.shadowRoot.textContent.includes('INVOICE-S') ? v.model.number : 'missing',
                                         !!v.shadowRoot.querySelector('.state.error')].join('|');
                               }
                               """));
    // a document that cannot be read stays an error, also when a report is removed or loaded
    assertEquals("error|error",
                 page.evaluate("""
                               async () => {
                                 const v = document.getElementById('v');
                                 await v.load('<nonsense/>').catch(() => {});
                                 await v.loadReport(null);
                                 const one = v.shadowRoot.querySelector('.state.error') ? 'error' : 'gone';
                                 await v.loadReport(await window.reportOf('X')).catch(() => {});
                                 return one + '|' + (v.shadowRoot.querySelector('.state.error') ? 'error' : 'gone');
                               }
                               """));
  }

  @Test
  void aSupersededReportOfTheCombinedLoadNeitherFailsNorReplacesAnything() {
    for (String body : new String[] { "<broken", "good" }) {
      assertEquals("INVOICE-K|NEW-REPORT|0",
                   page.evaluate("""
                                 async body => {
                                   const v = document.getElementById('v');
                                   const errors = [];
                                   v.addEventListener('paniolo-error', e => errors.push(e.detail.code));
                                   const bytes = body === 'good' ? await window.reportOf('OLD-REPORT') : new TextEncoder().encode(body);
                                   const slow = window.later(bytes);
                                   const loading = v.load(await window.invoice('INVOICE-K'), {report: slow.response});
                                   await window.tick();
                                   await v.loadReport(await window.reportOf('NEW-REPORT'));
                                   slow.release();
                                   await loading;
                                   await window.tick();
                                   return [v.model && v.model.number, JSON.stringify(v.report).includes('NEW-REPORT') ? 'NEW-REPORT' : 'other',
                                           errors.length].join('|');
                                 }
                                 """,
                                 body),
                   body);
    }
  }

  @Test
  void failuresOfAReportRequestEndWithACorrectReport() {
    // network failure, HTTP failure, and an unreadable report: each is an error of the report only
    for (String kind : new String[] { "network", "http", "syntax" }) {
      assertEquals("error|INVOICE-L|GOOD|false",
                   page.evaluate("""
                                 async kind => {
                                   window.stubFetch();
                                   const w = document.createElement('paniolo-viewer');
                                   w.setAttribute('src', '/doc');
                                   w.setAttribute('report-src', '/report');
                                   document.body.appendChild(w);
                                   window.answer('/doc', await window.invoice('INVOICE-L'));
                                   await window.tick();
                                   if (kind === 'network') {
                                     window.fail('/report');
                                   } else if (kind === 'http') {
                                     window.answerStatus('/report', 503);
                                   } else {
                                     window.answer('/report', new TextEncoder().encode('<broken'));
                                   }
                                   await window.tick();
                                   const failed = w.shadowRoot.querySelector('.state.error') ? 'error' : 'no error';
                                   await w.loadReport(await window.reportOf('GOOD'));
                                   return [failed, w.model.number, JSON.stringify(w.report).includes('GOOD') ? 'GOOD' : 'none',
                                           !!w.shadowRoot.querySelector('.state.error')].join('|');
                                 }
                                 """,
                                 kind),
                   kind);
    }
  }

  @Test
  void onlyTheTrailerDeclaresEncryption() {
    assertEquals("ok",
                 page.evaluate("url => window.loadUrl(url)",
                               "/samples/invoice-facturx.pdf"));
    assertEquals("ok",
                 page.evaluate("url => window.loadUrl(url)",
                               "/pdf/comment"),
                 "a comment with /Encrypt");
    assertEquals("ok",
                 page.evaluate("url => window.loadUrl(url)",
                               "/pdf/comment-trailer"),
                 "a comment with trailer and /Encrypt");
    assertEquals("error:pdfEncrypted",
                 page.evaluate("url => window.loadUrl(url)",
                               "/pdf/trailer"),
                 "classic trailer");
    assertEquals("error:pdfEncrypted",
                 page.evaluate("url => window.loadUrl(url)",
                               "/pdf/xref-stream"),
                 "cross-reference stream");
  }

  @Test
  void aBrokenLineHierarchyIsRefusedAndNeverDropsALine() {
    assertEquals("ok",
                 page.evaluate("url => window.loadUrl(url)",
                               "/formats/hierarchy-ok"));
    // 4 lines in the file, 2 of them below line 1: all 4 are in the model, 2 on the top level
    assertEquals(4L,
                 ((Number) page.evaluate("() => document.getElementById('v').model.lines.reduce((n, l) => n + 1 + (l.subLines || []).length, 0)")).longValue());
    for (String kind : new String[] { "self",
                                      "cycle",
                                      "missing",
                                      "ambiguous" }) {
      assertEquals("error:lineHierarchy",
                   page.evaluate("url => window.loadUrl(url)",
                                 "/formats/hierarchy-" + kind),
                   kind);
    }
  }

  @Test
  void findingsOfSubLinesMarkTheirOwnRowAndNavigateThere() {
    assertEquals("ok",
                 page.evaluate("([a, b]) => window.loadUrl(a, b)",
                               new String[] { "/formats/sublines-ubl",
                                              "/formats/sublines-report" }));
    // line 1 has two sub-lines that share the business id X, the second has a sub-line again
    String marked = (String) page.evaluate("""
        () => [...document.getElementById('v').shadowRoot.querySelectorAll('tr.line')]
            .filter(r => r.classList.contains('has-findings')).map(r => r.dataset.line).sort().join(',')
        """);
    assertEquals("1/2,1/2/1,2",
                 marked,
                 "only the lines that the findings name are marked, not the top-level line 1");
    // the finding of the nested sub-line leads to that very row
    page.evaluate("""
        () => document.getElementById('v').shadowRoot.querySelector('button[data-action="goto-line"][data-line="1/2/1"]').click()
        """);
    assertTrue(((Boolean) page.evaluate("""
        () => { const row = document.getElementById('v').shadowRoot.querySelector('tr.line[data-line="1/2/1"]');
                return !!row && row.classList.contains('flash'); }
        """)),
               "the link flashes the nested sub-line");
    assertFalse(((Boolean) page.evaluate("""
        () => document.getElementById('v').shadowRoot.querySelector('tr.line[data-line="1"]').classList.contains('flash')
        """)),
                "not the top-level line");
    assertNoProblems();
  }

  @Test
  void brokenInputEndsInTheErrorView() {
    var expected = Map.of("empty",
                          "empty",
                          "notxml",
                          "xml",
                          "unknown",
                          "unknownRoot",
                          "pdf",
                          "pdf");
    for (var entry : expected.entrySet()) {
      Object result = page.evaluate("url => window.loadUrl(url)",
                                    base + "/bad/" + entry.getKey());
      String text = String.valueOf(result);
      assertTrue(text.startsWith("error:"),
                 entry.getKey() + " -> " + text);
      assertTrue(page.locator("paniolo-viewer [role=alert]")
                     .isVisible(),
                 entry.getKey());
      assertFalse(page.locator("paniolo-viewer [role=alert]")
                      .innerText()
                      .isBlank(),
                  entry.getKey());
      assertTrue(((Boolean) page.evaluate("() => window.events.some(e => e.startsWith('error:'))")),
                 entry.getKey());
    }
    // The viewer recovers: a good document loads after the errors.
    assertEquals("ok",
                 load("invoice-ubl.xml"));
    assertEquals(0L,
                 page.locator("paniolo-viewer [role=alert]")
                     .count());
    assertNoProblems();
  }
}

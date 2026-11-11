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
import io.github.nalukit.paniolo.Paniolo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the commands in process with the samples in {@code src/test/resources/samples}: a Factur-X
 * EN 16931 invoice, an Order-X BASIC order and a CIDA despatch advice as JSON, and a PDF/A-2b page.
 */
class PanioloCliTest {
  @TempDir
  static Path dir;

  static Path invoiceJson;
  static Path orderJson;
  static Path adviceJson;
  static Path visualPdf;

  @BeforeAll
  static void samples()
      throws IOException {
    invoiceJson = copy("invoice.json");
    orderJson   = copy("order.json");
    adviceJson  = copy("despatch-advice.json");
    visualPdf   = copy("visual.pdf");
  }

  private static Path copy(String name)
      throws IOException {
    Path target = dir.resolve(name);
    try (InputStream input = PanioloCliTest.class.getResourceAsStream("/samples/" + name)) {
      Files.write(target,
                  input.readAllBytes());
    }
    return target;
  }

  private static Result run(String... args) {
    return run(new ByteArrayInputStream(new byte[0]),
               args);
  }

  private static Result run(InputStream in,
                            String... args) {
    var out = new ByteArrayOutputStream();
    var err = new ByteArrayOutputStream();
    int exitCode = PanioloCli.execute(in,
                                      new PrintStream(out,
                                                      true,
                                                      StandardCharsets.UTF_8),
                                      new PrintStream(err,
                                                      true,
                                                      StandardCharsets.UTF_8),
                                      args);
    return new Result(exitCode,
                      out.toString(StandardCharsets.UTF_8),
                      err.toString(StandardCharsets.UTF_8));
  }

  @Test
  void printsHelpAndVersion() {
    var help = run("--help");
    assertEquals(PanioloCli.EXIT_OK,
                 help.exitCode());
    assertTrue(help.out()
                   .contains("validate"),
               help.out());
    var version = run("--version");
    assertEquals(PanioloCli.EXIT_OK,
                 version.exitCode());
    assertTrue(version.out()
                      .startsWith("Paniolo "),
               version.out());
  }

  @Test
  void writesValidatesAndReadsInvoiceXml()
      throws IOException {
    Path xml = dir.resolve("invoice-cii.xml");
    assertEquals(PanioloCli.EXIT_OK,
                 run("to-xml",
                     invoiceJson.toString(),
                     "--syntax",
                     "cii",
                     "-o",
                     xml.toString()).exitCode());
    assertEquals("CII\n",
                 run("detect",
                     xml.toString()).out());

    var valid = run("validate",
                    "--profile",
                    "EN16931",
                    xml.toString());
    assertEquals(PanioloCli.EXIT_OK,
                 valid.exitCode(),
                 valid.out());
    assertTrue(valid.out()
                    .contains(": valid ("),
               valid.out());

    var json = run("read",
                   xml.toString());
    assertEquals(PanioloCli.EXIT_OK,
                 json.exitCode(),
                 json.err());
    assertEquals(new ObjectMapper().readTree(invoiceJson.toFile()),
                 new ObjectMapper().readTree(json.out()));
  }

  /**
   * A specification identifier that states XRechnung but is not supported makes the document
   * invalid (Mustang issue 1343), which the exit code and the XML report show.
   */
  @Test
  void reportsAnUnsupportedXrechnungIdentifierThroughTheExitCode()
      throws IOException {
    for (var syntax : new String[] { "UBL", "CII" }) {
      var written = run("to-xml",
                        invoiceJson.toString(),
                        "-s",
                        syntax);
      assertEquals(PanioloCli.EXIT_OK,
                   written.exitCode(),
                   written.err());
      Path valid = dir.resolve("en16931-" + syntax + ".xml");
      Files.writeString(valid,
                        written.out());
      assertEquals(PanioloCli.EXIT_OK,
                   run("validate",
                       "-q",
                       valid.toString()).exitCode());
      for (var id : new String[] { "urn:cen.eu:en16931:2017#compliant#urn:xoev-de:kosit:standard:xrechnung_3.0",
                                   "urn:cen.eu:en16931:2017#compliant#urn:xeinkauf.de:kosit:xrechnung_9.9",
                                   "urn:cen.eu:en16931:2017#compliant#urn:xeinkauf.de:kosit:xrechnung_3.0#garbage" }) {
        Path invalid = dir.resolve("unsupported-" + syntax + ".xml");
        Files.writeString(invalid,
                          written.out()
                                 .replace(">urn:cen.eu:en16931:2017<",
                                          ">" + id + "<"));
        var xml = run("validate",
                      "-f",
                      "xml",
                      invalid.toString());
        assertEquals(PanioloCli.EXIT_INVALID,
                     xml.exitCode(),
                     xml.out());
        assertTrue(xml.out()
                      .contains("rule=\"XR-SPECIFICATION-UNSUPPORTED\""),
                   xml.out());
        assertTrue(xml.out()
                      .contains(id),
                   xml.out());
      }
    }
  }

  @Test
  void convertsBetweenSyntaxesThroughStandardStreams() {
    var ubl = run("to-xml",
                  invoiceJson.toString(),
                  "-s",
                  "UBL");
    assertEquals(PanioloCli.EXIT_OK,
                 ubl.exitCode(),
                 ubl.err());
    var cii = run(new ByteArrayInputStream(ubl.out()
                                              .getBytes(StandardCharsets.UTF_8)),
                  "to-xml",
                  "-s",
                  "CII",
                  "-");
    assertEquals(PanioloCli.EXIT_OK,
                 cii.exitCode(),
                 cii.err());
    assertTrue(cii.out()
                  .contains("CrossIndustryInvoice"),
               cii.out());
  }

  @Test
  void reportsInvalidDocumentsThroughTheExitCode()
      throws IOException {
    Path broken = dir.resolve("broken.xml");
    Files.writeString(broken,
                      "<rsm:CrossIndustryInvoice xmlns:rsm=\"urn:un:unece:uncefact:data:standard:CrossIndustryInvoice:100\"/>");
    var quiet = run("validate",
                    "-q",
                    broken.toString());
    assertEquals(PanioloCli.EXIT_INVALID,
                 quiet.exitCode());
    assertEquals("",
                 quiet.out());

    var json = run("validate",
                   "--format",
                   "json",
                   broken.toString());
    assertEquals(PanioloCli.EXIT_INVALID,
                 json.exitCode());
    var report = new ObjectMapper().readTree(json.out());
    assertEquals(false,
                 report.get("valid")
                       .asBoolean());
    assertTrue(report.get("messages")
                     .size() > 0);

    var xml = run("validate",
                  "-f",
                  "xml",
                  broken.toString());
    assertTrue(xml.out()
                  .contains("urn:io:github:nalukit:paniolo:validation-report:1"),
               xml.out());
  }

  @Test
  void createsAndInspectsFacturXPdf()
      throws IOException {
    Path pdf = dir.resolve("factur-x.pdf");
    var created = run("to-facturx",
                      invoiceJson.toString(),
                      "--pdf",
                      visualPdf.toString(),
                      "--profile",
                      "en16931",
                      "--attach",
                      orderJson.toString(),
                      "-o",
                      pdf.toString());
    assertEquals(PanioloCli.EXIT_OK,
                 created.exitCode(),
                 created.err());
    assertTrue(Paniolo.isPdf(Files.readAllBytes(pdf)));

    var valid = run("validate",
                    pdf.toString());
    assertEquals(PanioloCli.EXIT_OK,
                 valid.exitCode(),
                 valid.out());
    assertEquals("PDF\n",
                 run("detect",
                     pdf.toString()).out());
    assertTrue(run("extract-xml",
                   pdf.toString()).out()
                                  .contains("CrossIndustryInvoice"));
    assertTrue(run("producer",
                   pdf.toString()).out()
                                  .contains("Paniolo"));

    Path extracted = dir.resolve("attachments");
    var attachments = run("attachments",
                          pdf.toString(),
                          "--extract",
                          extracted.toString());
    assertTrue(attachments.out()
                          .startsWith("order.json\t"),
               attachments.out());
    assertArrayEquals(Files.readAllBytes(orderJson),
                      Files.readAllBytes(extracted.resolve("order.json")));
  }

  @Test
  void embedsExistingXmlUnchanged()
      throws IOException {
    Path xml = dir.resolve("received.xml");
    var written = run("to-xml",
                      invoiceJson.toString(),
                      "--syntax",
                      "CII",
                      "-o",
                      xml.toString());
    assertEquals(PanioloCli.EXIT_OK,
                 written.exitCode(),
                 written.err());
    // XML of another producer: the comment would be lost by to-facturx.
    Files.writeString(xml,
                      Files.readString(xml)
                           .replace("<rsm:ExchangedDocument>",
                                    "<!-- other producer --><rsm:ExchangedDocument>"));
    Path pdf = dir.resolve("embedded.pdf");
    var embedded = run("embed-xml",
                       xml.toString(),
                       "--pdf",
                       visualPdf.toString(),
                       "--profile",
                       "en16931",
                       "-o",
                       pdf.toString());
    assertEquals(PanioloCli.EXIT_OK,
                 embedded.exitCode(),
                 embedded.err());
    assertArrayEquals(Files.readAllBytes(xml),
                      Paniolo.create()
                             .extractXml(Files.readAllBytes(pdf)));

    // A profile conflict is rejected before anything is written.
    var conflict = run("embed-xml",
                       xml.toString(),
                       "--pdf",
                       visualPdf.toString(),
                       "--profile",
                       "basic",
                       "-o",
                       dir.resolve("conflict.pdf")
                          .toString());
    assertEquals(PanioloCli.EXIT_ERROR,
                 conflict.exitCode());
    assertTrue(conflict.err()
                       .contains("requires specification identifier"),
               conflict.err());
    assertTrue(Files.notExists(dir.resolve("conflict.pdf")));

    // An invalid XML is embedded, but reported with exit code 1.
    Path invalid = dir.resolve("invalid.xml");
    Files.writeString(invalid,
                      Files.readString(xml)
                           .replace("<ram:DuePayableAmount>",
                                    "<ram:DuePayableAmount>1"));
    var reported = run("embed-xml",
                       invalid.toString(),
                       "--pdf",
                       visualPdf.toString(),
                       "--profile",
                       "en16931",
                       "-o",
                       dir.resolve("invalid.pdf")
                          .toString());
    assertEquals(PanioloCli.EXIT_INVALID,
                 reported.exitCode(),
                 reported.err());
    assertTrue(reported.err()
                       .contains("error "),
               reported.err());
  }

  @Test
  void reportsCalculationFindingsOnRequest()
      throws IOException {
    // Factur-X EN 16931 has no rule for the line net amount; the calculation check finds it.
    Path xml = copy("calculation-line-net-amount.xml");
    var plain = run("validate",
                    xml.toString(),
                    "--profile",
                    "en16931");
    assertEquals(PanioloCli.EXIT_OK,
                 plain.exitCode(),
                 plain.out());
    assertTrue(!plain.out()
                     .contains("PANIOLO-CALC"),
               plain.out());
    var checked = run("validate",
                      xml.toString(),
                      "--profile",
                      "en16931",
                      "--calculation-check");
    // A warning of the calculation check does not make the document invalid.
    assertEquals(PanioloCli.EXIT_OK,
                 checked.exitCode(),
                 checked.out());
    assertTrue(checked.out()
                      .contains("PANIOLO-CALC-LINE-NET-AMOUNT"),
               checked.out());
  }

  @Test
  void writesReadsAndValidatesOrders() {
    var xml = run("order",
                  "to-xml",
                  orderJson.toString());
    assertEquals(PanioloCli.EXIT_OK,
                 xml.exitCode(),
                 xml.err());
    var stdin = new ByteArrayInputStream(xml.out()
                                            .getBytes(StandardCharsets.UTF_8));
    var read = run(stdin,
                   "order",
                   "read",
                   "-");
    assertTrue(read.out()
                   .contains("\"number\""),
               read.out());

    Path pdf = dir.resolve("order-x.pdf");
    assertEquals(PanioloCli.EXIT_OK,
                 run("order",
                     "to-pdf",
                     orderJson.toString(),
                     "--pdf",
                     visualPdf.toString(),
                     "-o",
                     pdf.toString()).exitCode());
    var valid = run("order",
                    "validate",
                    pdf.toString());
    assertEquals(PanioloCli.EXIT_OK,
                 valid.exitCode(),
                 valid.out());
  }

  @Test
  void writesReadsAndValidatesDespatchAdvices() {
    var cida = run("despatch-advice",
                   "to-xml",
                   "--syntax",
                   "cida",
                   adviceJson.toString());
    assertEquals(PanioloCli.EXIT_OK,
                 cida.exitCode(),
                 cida.err());
    assertTrue(cida.out()
                   .contains("CIDA"),
               cida.out());

    Path pdf = dir.resolve("delivery-note.pdf");
    assertEquals(PanioloCli.EXIT_OK,
                 run("despatch-advice",
                     "to-pdf",
                     adviceJson.toString(),
                     "--pdf",
                     visualPdf.toString(),
                     "-o",
                     pdf.toString()).exitCode());
    assertEquals(PanioloCli.EXIT_OK,
                 run("despatch-advice",
                     "validate",
                     pdf.toString()).exitCode());
    assertTrue(run("despatch-advice",
                   "read",
                   pdf.toString()).out()
                                  .contains("\"lines\""));
  }

  @Test
  void validatesADirectoryTreeCompletely()
      throws IOException {
    Path tree = dir.resolve("tree");
    Path sub  = Files.createDirectories(tree.resolve("sub/deeper"))
                     .getParent();
    Files.createDirectories(tree.resolve("archive"));
    String valid = Files.readString(copy("xrechnung.xml"));
    String invalid = valid.replaceAll("<ram:BuyerReference>[^<]*</ram:BuyerReference>",
                                      "");
    Files.writeString(tree.resolve("valid.xml"),
                      valid);
    Files.copy(visualPdf,
               tree.resolve("no-invoice.pdf"));
    Files.writeString(tree.resolve("notes.txt"),
                      "not a document");
    Files.writeString(sub.resolve("invalid.xml"),
                      invalid);
    Files.writeString(sub.resolve("unsupported.xml"),
                      "<Order xmlns=\"urn:example\"/>");
    Files.writeString(sub.resolve("deeper/broken.xml"),
                      "<unclosed>");
    Files.writeString(tree.resolve("archive/old.xml"),
                      invalid);
    Path locked = Files.writeString(sub.resolve("locked.xml"),
                                    valid);
    boolean unreadable = locked.toFile()
                               .setReadable(false) && !Files.isReadable(locked);
    try {
      var text = run("validate",
                     tree.toString(),
                     "--recursive",
                     "--exclude",
                     "archive/**",
                     "--summary");
      // Not processed: broken, unsupported and, if the file system denies reading, locked.
      int errors = unreadable ?
                   3 :
                   2;
      assertEquals(PanioloCli.EXIT_ERROR,
                   text.exitCode(),
                   text.out());
      var lines = text.out()
                      .lines()
                      .filter(line -> line.startsWith(tree.toString()))
                      .toList();
      assertEquals(6,
                   lines.size(),
                   text.out());
      assertTrue(lines.stream()
                      .noneMatch(line -> line.contains("notes.txt") || line.contains("archive")),
                 text.out());
      long validLines = lines.stream()
                             .filter(line -> line.contains(": valid ("))
                             .count();
      long invalidLines = lines.stream()
                               .filter(line -> line.contains(": invalid ("))
                               .count();
      long errorLines = lines.stream()
                             .filter(line -> line.contains(": error ("))
                             .count();
      assertEquals(errors,
                   errorLines,
                   text.out());
      assertTrue(text.out()
                     .contains("Summary: 6 files, " + validLines + " valid, " + invalidLines + " invalid, " + errorLines + " not processed"),
                 text.out());
      assertTrue(text.err()
                     .contains("broken.xml") && text.err()
                                                    .contains("unsupported.xml"),
                 text.err());

      // JSON Lines: one object per file and a summary that adds up.
      var json = run("validate",
                     tree.toString(),
                     "-r",
                     "--exclude",
                     "archive/**",
                     "--summary",
                     "-f",
                     "json");
      var mapper = new ObjectMapper();
      var objects = new java.util.ArrayList<com.fasterxml.jackson.databind.JsonNode>();
      for (String line : json.out()
                             .lines()
                             .toList()) {
        objects.add(mapper.readTree(line));
      }
      var total = objects.remove(objects.size() - 1);
      assertTrue(total.get("summary")
                      .asBoolean());
      assertEquals(objects.size(),
                   total.get("files")
                        .asInt());
      assertEquals(objects.stream()
                          .filter(o -> o.has("error"))
                          .count(),
                   total.get("errors")
                        .asLong());
      assertEquals(objects.stream()
                          .filter(o -> !o.has("error") && o.get("valid")
                                                           .asBoolean())
                          .count(),
                   total.get("valid")
                        .asLong());
      assertEquals(objects.stream()
                          .filter(o -> !o.has("error") && !o.get("valid")
                                                            .asBoolean())
                          .count(),
                   total.get("invalid")
                        .asLong());
      assertEquals(validLines,
                   total.get("valid")
                        .asLong());

      // Without --recursive only the files directly in the directory; --include narrows them.
      var flat = run("validate",
                     tree.toString(),
                     "--include",
                     "*.xml");
      assertEquals(PanioloCli.EXIT_OK,
                   flat.exitCode(),
                   flat.out());
      assertEquals(1,
                   flat.out()
                       .lines()
                       .filter(line -> line.startsWith(tree.toString()))
                       .count(),
                   flat.out());
      // Only invalid documents, no processing errors: exit code 1.
      assertEquals(PanioloCli.EXIT_INVALID,
                   run("validate",
                       tree.resolve("archive")
                           .toString(),
                       sub.resolve("invalid.xml")
                          .toString()).exitCode());
      assertEquals(2,
                   run("validate",
                       Files.createDirectories(dir.resolve("empty"))
                            .toString()).exitCode());
    } finally {
      locked.toFile()
            .setReadable(true);
    }
  }

  @Test
  void writesTheValidationReportAsPdf()
      throws IOException {
    Path xml = copy("xrechnung.xml");
    Path pdf = dir.resolve("report.pdf");
    var written = run("validate",
                      xml.toString(),
                      "-f",
                      "pdf",
                      "--lang",
                      "en",
                      "-o",
                      pdf.toString());
    assertEquals(PanioloCli.EXIT_OK,
                 written.exitCode(),
                 written.err());
    assertTrue(Paniolo.isPdf(Files.readAllBytes(pdf)));
    // One document per report, like --format xml.
    assertEquals(2,
                 run("validate",
                     xml.toString(),
                     xml.toString(),
                     "-f",
                     "pdf").exitCode());
  }

  @Test
  void rejectsValidDocumentsByRule()
      throws IOException {
    // PEPPOL-EN16931-R120 is a warning in XRechnung: valid, but the receiving process may reject it.
    // The case of G13: the corpus file that Mustang rejects and Paniolo accepts with a warning.
    Path xml = copy("line-net-amount.xrechnung-ubl.xml");
    var plain = run("validate",
                    xml.toString());
    assertTrue(plain.out()
                    .contains("PEPPOL-EN16931-R120"),
               plain.out());
    assertEquals(PanioloCli.EXIT_OK,
                 plain.exitCode(),
                 plain.out());
    var rejected = run("validate",
                       xml.toString(),
                       "--reject-rule",
                       "PEPPOL-EN16931-R120",
                       "--summary");
    assertEquals(PanioloCli.EXIT_INVALID,
                 rejected.exitCode(),
                 rejected.out());
    assertTrue(rejected.out()
                       .contains(": valid, rejected by policy: PEPPOL-EN16931-R120 ("),
               rejected.out());
    assertTrue(rejected.out()
                       .contains("Summary: 1 files, 0 valid, 0 invalid, 1 rejected by policy, 0 not processed"),
               rejected.out());
    var json = new ObjectMapper().readTree(run("validate",
                                               xml.toString(),
                                               "--reject-rule",
                                               "PEPPOL-EN16931-R120",
                                               "-f",
                                               "json").out());
    assertTrue(json.get("valid")
                   .asBoolean());
    assertEquals(false,
                 json.get("accepted")
                     .asBoolean());
    assertEquals("PEPPOL-EN16931-R120",
                 json.get("blocking")
                     .get(0)
                     .asText());
  }

  @Test
  void separatesUsageAndProcessingErrors() {
    var missing = run("validate",
                      dir.resolve("missing.xml")
                         .toString());
    assertEquals(PanioloCli.EXIT_ERROR,
                 missing.exitCode());
    assertTrue(missing.err()
                      .startsWith("paniolo: File not found: "),
               missing.err());

    assertEquals(2,
                 run("validate").exitCode());
    assertEquals(2,
                 run("validate",
                     "--syntax",
                     "CII",
                     "--profile",
                     "BASIC",
                     visualPdf.toString()).exitCode());
    assertEquals(2,
                 run("validate",
                     "-f",
                     "xml",
                     visualPdf.toString(),
                     visualPdf.toString()).exitCode());
  }

  @Test
  void rendersPdfAndFacturXWithoutVisualPdf()
      throws IOException {
    Path pdf = dir.resolve("rendered.pdf");
    var rendered = run("to-pdf",
                       "--lang",
                       "en",
                       "--accent",
                       "01244F",
                       "-o",
                       pdf.toString(),
                       invoiceJson.toString());
    assertEquals(PanioloCli.EXIT_OK,
                 rendered.exitCode(),
                 rendered.err());
    assertTrue(Paniolo.isPdf(Files.readAllBytes(pdf)));

    Path facturX = dir.resolve("rendered-facturx.pdf");
    var hybrid = run("to-facturx",
                     "--profile",
                     "EN16931",
                     "-o",
                     facturX.toString(),
                     invoiceJson.toString());
    assertEquals(PanioloCli.EXIT_OK,
                 hybrid.exitCode(),
                 hybrid.err());
    var valid = run("validate",
                    facturX.toString());
    assertEquals(PanioloCli.EXIT_OK,
                 valid.exitCode(),
                 valid.out());

    assertEquals(PanioloCli.EXIT_ERROR,
                 run("to-pdf",
                     "--accent",
                     "blue",
                     invoiceJson.toString()).exitCode());

    Path preview = dir.resolve("preview.pdf");
    var watermarked = run("to-pdf",
                          "--watermark",
                          "PREVIEW",
                          "-o",
                          preview.toString(),
                          invoiceJson.toString());
    assertEquals(PanioloCli.EXIT_OK,
                 watermarked.exitCode(),
                 watermarked.err());
    assertTrue(Paniolo.isPdf(Files.readAllBytes(preview)));
    var rejected = run("to-facturx",
                       "--profile",
                       "EN16931",
                       "--watermark",
                       "PREVIEW",
                       "-o",
                       dir.resolve("preview-facturx.pdf")
                          .toString(),
                       invoiceJson.toString());
    assertEquals(PanioloCli.EXIT_ERROR,
                 rejected.exitCode());
    assertTrue(rejected.err()
                       .contains("watermark"),
               rejected.err());

    Path orderPdf = dir.resolve("rendered-order.pdf");
    var order = run("order",
                    "to-pdf",
                    "--lang",
                    "en",
                    "-o",
                    orderPdf.toString(),
                    orderJson.toString());
    assertEquals(PanioloCli.EXIT_OK,
                 order.exitCode(),
                 order.err());
    assertEquals(PanioloCli.EXIT_OK,
                 run("order",
                     "validate",
                     orderPdf.toString()).exitCode());

    Path notePdf = dir.resolve("rendered-delivery-note.pdf");
    var note = run("despatch-advice",
                   "to-pdf",
                   "-o",
                   notePdf.toString(),
                   adviceJson.toString());
    assertEquals(PanioloCli.EXIT_OK,
                 note.exitCode(),
                 note.err());
    assertEquals(PanioloCli.EXIT_OK,
                 run("despatch-advice",
                     "read",
                     notePdf.toString()).exitCode());
  }

  @Test
  void writesStandaloneHtml()
      throws IOException {
    Path xml = dir.resolve("invoice-visualize.xml");
    assertEquals(PanioloCli.EXIT_OK,
                 run("to-xml",
                     invoiceJson.toString(),
                     "--syntax",
                     "cii",
                     "-o",
                     xml.toString()).exitCode());
    Path html = dir.resolve("invoice.html");
    var result = run("visualize",
                     "--validate",
                     "--lang",
                     "en",
                     "-o",
                     html.toString(),
                     xml.toString());
    assertEquals(PanioloCli.EXIT_OK,
                 result.exitCode(),
                 result.err());
    String page = Files.readString(html);
    assertTrue(page.startsWith("<!doctype html>"),
               page);
    assertTrue(page.contains("<paniolo-viewer lang=\"en\">"),
               page);
    assertTrue(page.contains("id=\"paniolo-report\""),
               page);

    var plain = run("visualize",
                    xml.toString());
    assertEquals(PanioloCli.EXIT_OK,
                 plain.exitCode(),
                 plain.err());
    assertTrue(plain.out()
                    .contains("<html lang=\"de\">"));
    assertTrue(!plain.out()
                     .contains("id=\"paniolo-report\""));

    assertEquals(2,
                 run("visualize",
                     "--validate",
                     "--report",
                     xml.toString(),
                     xml.toString()).exitCode());
  }



  @Test
  void handlesUnknownDocumentsAsConfigured()
      throws IOException {
    Path folder = Files.createDirectories(dir.resolve("unknown-documents"));
    Files.copy(copy("xrechnung.xml"),
               folder.resolve("valid.xml"),
               java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    Path unknown = Files.writeString(folder.resolve("unknown.xml"),
                                     "<Order xmlns=\"urn:example\"/>");

    // Default: not an invoice is a processing error.
    var thrown = run("validate",
                     folder.toString(),
                     "--summary");
    assertEquals(PanioloCli.EXIT_ERROR,
                 thrown.exitCode(),
                 thrown.out());
    assertTrue(thrown.out()
                     .contains("unknown.xml: error (") && thrown.out()
                                                               .contains("1 valid, 0 invalid, 1 not processed"),
               thrown.out());

    // Compatibility mode: an invalid result with a FATAL finding, as Mustang reports it.
    var reported = run("--unknown-documents",
                       "report_invalid",
                       "validate",
                       folder.toString(),
                       "--summary");
    assertEquals(PanioloCli.EXIT_INVALID,
                 reported.exitCode(),
                 reported.out() + reported.err());
    assertTrue(reported.out()
                       .contains("unknown.xml: invalid (Unknown document, 1 errors") && reported.out()
                                                                                         .contains("PANIOLO-UNKNOWN-DOCUMENT") && reported.out()
                                                                                                                                         .contains("1 valid, 1 invalid, 0 not processed"),
               reported.out());
    assertTrue(reported.err()
                       .isEmpty(),
               reported.err());
    // An expected negative result is met in that mode.
    assertEquals(PanioloCli.EXIT_OK,
                 run("validate",
                     unknown.toString(),
                     "--unknown-documents",
                     "REPORT_INVALID",
                     "--expect",
                     "invalid").exitCode());
  }

  @Test
  void reportsRuleStatistics()
      throws IOException {
    Path xml = copy("xrechnung.xml");
    var plain = run("validate",
                    xml.toString());
    assertTrue(!plain.out()
                     .contains("statistics"),
               plain.out());
    var text = run("--rule-statistics",
                   "validate",
                   xml.toString());
    assertEquals(PanioloCli.EXIT_OK,
                 text.exitCode(),
                 text.out());
    assertTrue(text.out()
                   .lines()
                   .anyMatch(line -> line.startsWith("  statistics ") && line.contains(" fired rules, 0 failed asserts")),
               text.out());
    var json = new ObjectMapper().readTree(run("validate",
                                               xml.toString(),
                                               "--rule-statistics",
                                               "-f",
                                               "json").out());
    assertTrue(json.get("statistics")
                   .get("schematron")
                   .get(0)
                   .get("firedRules")
                   .asInt() > 0,
               json::toString);
    assertTrue(run("validate",
                   xml.toString(),
                   "--rule-statistics",
                   "-f",
                   "xml").out()
                         .contains("<statistics>"));
  }

  @Test
  void checksExpectedResults()
      throws IOException {
    Path corpus   = Files.createDirectories(dir.resolve("corpus"));
    Path positive = Files.createDirectories(corpus.resolve("positive"));
    Path negative = Files.createDirectories(corpus.resolve("negative"));
    String valid = Files.readString(copy("xrechnung.xml"));
    String invalid = valid.replaceAll("<ram:BuyerReference>[^<]*</ram:BuyerReference>",
                                      "");
    Files.writeString(positive.resolve("a.xml"),
                      valid);
    Files.writeString(negative.resolve("missing-buyer-reference.xml"),
                      invalid);

    // A positive and a negative corpus, each with its expectation.
    var ok = run("validate",
                 positive.toString(),
                 "--expect",
                 "valid",
                 "--summary");
    assertEquals(PanioloCli.EXIT_OK,
                 ok.exitCode(),
                 ok.out());
    assertTrue(ok.out()
                 .contains(": valid, as expected (") && ok.out()
                                                          .contains("1 as expected, 0 not as expected"),
               ok.out());
    var negatives = run("validate",
                        negative.toString(),
                        "--expect",
                        "invalid");
    assertEquals(PanioloCli.EXIT_OK,
                 negatives.exitCode(),
                 negatives.out());
    assertTrue(negatives.out()
                        .contains(": invalid, as expected ("),
               negatives.out());

    // A negative file in the positive corpus fails the run.
    Files.writeString(positive.resolve("b.xml"),
                      invalid);
    var regression = run("validate",
                         positive.toString(),
                         "--expect",
                         "valid",
                         "--summary",
                         "-f",
                         "json");
    assertEquals(PanioloCli.EXIT_INVALID,
                 regression.exitCode(),
                 regression.out());
    var mapper = new ObjectMapper();
    var lines  = regression.out()
                           .lines()
                           .toList();
    var b = mapper.readTree(lines.get(1));
    assertEquals("valid",
                 b.get("expected")
                  .asText());
    assertTrue(!b.get("asExpected")
                 .asBoolean(),
               regression.out());
    var summary = mapper.readTree(lines.get(2));
    assertEquals(1,
                 summary.get("asExpected")
                        .asInt());
    assertEquals(1,
                 summary.get("notAsExpected")
                        .asInt());

    // The expectation compares the acceptance: a document rejected by policy meets "invalid".
    for (String expectation : new String[] { "valid",
                                             "invalid" }) {
      var policy = mapper.readTree(run("validate",
                                       positive.resolve("a.xml")
                                               .toString(),
                                       "--fail-on-warnings",
                                       "--expect",
                                       expectation,
                                       "-f",
                                       "json").out());
      assertEquals(policy.get("accepted")
                         .asBoolean() == expectation.equals("valid"),
                   policy.get("asExpected")
                         .asBoolean(),
                   policy::toString);
    }

    // Per file expectations: the file wins over --expect; a listed file that is missing is an error.
    Path expectations = Files.writeString(corpus.resolve("expected.txt"),
                                          """
                                          # expected results of the corpus
                                          valid    positive/a.xml
                                          invalid  positive/b.xml
                                          invalid  negative/missing-buyer-reference.xml
                                          """);
    var mixed = run("validate",
                    corpus.toString(),
                    "-r",
                    "--expectations",
                    expectations.toString(),
                    "--summary");
    assertEquals(PanioloCli.EXIT_OK,
                 mixed.exitCode(),
                 mixed.out() + mixed.err());
    assertTrue(mixed.out()
                    .contains("3 as expected, 0 not as expected"),
               mixed.out());
    Files.writeString(expectations,
                      "valid positive/a.xml\nvalid positive/gone.xml\n");
    var missing = run("validate",
                      positive.resolve("a.xml")
                              .toString(),
                      "--expectations",
                      expectations.toString());
    assertEquals(PanioloCli.EXIT_ERROR,
                 missing.exitCode(),
                 missing.out());
    assertTrue(missing.out()
                      .contains("expected file was not validated") && missing.err()
                                                                           .contains("gone.xml"),
               missing.out() + missing.err());
    Files.writeString(expectations,
                      "probably positive/a.xml\n");
    assertEquals(2,
                 run("validate",
                     positive.toString(),
                     "--expectations",
                     expectations.toString()).exitCode());
  }

  @Test
  void listsTheInventoryOfADocumentStock()
      throws IOException {
    Path stock = Files.createDirectories(dir.resolve("stock/sub"))
                      .getParent();
    String xrechnung = Files.readString(copy("xrechnung.xml"));
    Files.writeString(stock.resolve("xrechnung.xml"),
                      xrechnung);
    Files.writeString(stock.resolve("sub/invalid.xml"),
                      xrechnung.replaceAll("<ram:BuyerReference>[^<]*</ram:BuyerReference>",
                                           ""));
    Files.writeString(stock.resolve("unknown.xml"),
                      "<Order xmlns=\"urn:example\"/>");
    Files.writeString(stock.resolve("broken.xml"),
                      "<unclosed>");
    Files.copy(visualPdf,
               stock.resolve("plain.pdf"));
    assertEquals(PanioloCli.EXIT_OK,
                 run("to-facturx",
                     invoiceJson.toString(),
                     "--pdf",
                     visualPdf.toString(),
                     "--profile",
                     "EN16931",
                     "-o",
                     stock.resolve("sub/factur-x.pdf")
                          .toString()).exitCode());
    assertEquals(PanioloCli.EXIT_OK,
                 run("order",
                     "to-xml",
                     orderJson.toString(),
                     "-o",
                     stock.resolve("order.xml")
                          .toString()).exitCode());

    var text = run("inventory",
                   stock.toString(),
                   "-r",
                   "--validate");
    assertEquals(PanioloCli.EXIT_OK,
                 text.exitCode(),
                 text.out() + text.err());
    String out = text.out();
    assertTrue(out.contains("xrechnung.xml: CII, XRECHNUNG 3.0 (urn:cen.eu:en16931:2017#compliant#urn:xeinkauf.de:kosit:xrechnung_3.0), type 380, valid"),
               out);
    assertTrue(out.contains("invalid.xml: CII, XRECHNUNG 3.0") && out.contains(", invalid\n"),
               out);
    assertTrue(out.contains("factur-x.pdf: PDF/CII, EN16931") && out.contains("producer Paniolo"),
               out);
    assertTrue(out.contains("order.xml: ORDER_X, BASIC"),
               out);
    assertTrue(out.contains("unknown.xml: UNKNOWN, not validated, root element {urn:example}Order"),
               out);
    assertTrue(out.lines()
                  // the parser message after the position is localized by the JDK, so only the locale-independent part is checked
                  .anyMatch(line -> line.contains("broken.xml: MALFORMED, not validated, ParseError at [row,col]:[1,11]")),
               out);
    assertTrue(out.contains("plain.pdf: PDF, producer"),
               out);
    assertTrue(out.contains("Summary: 7 files, 0 not readable") && out.contains("  Formats: CII 2, MALFORMED 1, ORDER_X 1, PDF 1, PDF/CII 1, UNKNOWN 1") &&
               out.contains("  Validation: 3 valid, 1 invalid, 3 not validated"),
               out);

    // JSON Lines with a summary that adds up to the entries.
    var json = run("inventory",
                   stock.toString(),
                   "-r",
                   "-f",
                   "json");
    var mapper  = new ObjectMapper();
    var entries = new java.util.ArrayList<com.fasterxml.jackson.databind.JsonNode>();
    for (String line : json.out()
                           .lines()
                           .toList()) {
      entries.add(mapper.readTree(line));
    }
    var summary = entries.remove(entries.size() - 1);
    assertEquals(entries.size(),
                 summary.get("files")
                        .asInt());
    int counted = 0;
    for (var count : summary.get("formats")
                            .properties()) {
      counted += count.getValue()
                      .asInt();
    }
    assertEquals(entries.size(),
                 counted);
    assertEquals(2,
                 summary.get("profiles")
                        .get("XRECHNUNG 3.0")
                        .asInt());

    var only = run("inventory",
                   stock.toString(),
                   "--summary-only");
    assertTrue(only.out()
                   .startsWith("Summary: 5 files"),
               only.out());
    assertEquals(PanioloCli.EXIT_ERROR,
                 run("inventory",
                     dir.resolve("does-not-exist.xml")
                        .toString()).exitCode());
  }



  record Result(int exitCode, String out, String err) {
  }
}

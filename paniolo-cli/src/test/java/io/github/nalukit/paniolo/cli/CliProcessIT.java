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

import io.github.nalukit.paniolo.Paniolo;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentNameDictionary;
import org.apache.pdfbox.pdmodel.PDEmbeddedFilesNameTreeNode;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.filespecification.PDComplexFileSpecification;
import org.apache.pdfbox.pdmodel.common.filespecification.PDEmbeddedFile;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Starts the packaged executable JAR ({@code *-all.jar}) as a real process: manifest, merged
 * ServiceLoader entries, package resources, exit codes, and the separation of standard output and
 * standard error, as the artifact is distributed. The child process gets the JAR as its only class
 * path; test code and project classes are not available to it. Runs after {@code package} through
 * maven-failsafe.
 */
class CliProcessIT {
  /**
   * Time budget of one process; a process that exceeds it is destroyed and the test fails.
   */
  private static final long TIMEOUT_SECONDS = 60;

  private static final String VALID_STAGE_PDF_A = "PDF_A";

  private static Path   jar;
  private static String version;
  private static Path   work;
  private static byte[] hybrid;

  @TempDir
  static Path temp;

  /**
   * Result of one process.
   */
  record Run(int exitCode, byte[] out, String err) {
    String outText() {
      return new String(out,
                        StandardCharsets.UTF_8);
    }
  }

  @BeforeAll
  static void prepare() throws IOException {
    String path = System.getProperty("paniolo.cli.shadedJar");
    assertNotNull(path,
                  "system property paniolo.cli.shadedJar is not set");
    jar = Path.of(path);
    assertTrue(Files.isRegularFile(jar),
               "shaded JAR not found: " + jar);
    version = System.getProperty("paniolo.cli.version");
    assertNotNull(version,
                  "system property paniolo.cli.version is not set");
    work = temp;
    for (String sample : List.of("invoice.json",
                                 "visual.pdf",
                                 "xrechnung.xml")) {
      try (InputStream input = CliProcessIT.class.getResourceAsStream("/samples/" + sample)) {
        Files.write(work.resolve(sample),
                    input.readAllBytes());
      }
    }
    // The hybrid PDF is made by the artifact itself.
    Path pdf = work.resolve("hybrid.pdf");
    Run created = runWith(null,
                      null,
                      "to-facturx",
                      work.resolve("invoice.json")
                          .toString(),
                      "--pdf",
                      work.resolve("visual.pdf")
                          .toString(),
                      "--profile",
                      "en16931",
                      "-o",
                      pdf.toString());
    assertEquals(0,
                 created.exitCode(),
                 created.err());
    hybrid = Files.readAllBytes(pdf);
  }

  /**
   * Runs the JAR with {@code java -jar}. The standard input comes from a file, so that the process
   * never waits for a pipe that nobody feeds; standard output and standard error go to files, so that
   * neither can block the process. The Java of the test JVM is used. The process is destroyed if it
   * exceeds the time budget, and never outlives the call.
   *
   * @param stdin  bytes for standard input, or null for an empty input
   * @param stdout where standard output goes, or null for a file that is read back
   */
  private static Run runWith(byte[] stdin,
                         ProcessBuilder.Redirect stdout,
                         String... args) throws IOException {
    return runWith(List.of(),
                   stdin,
                   stdout,
                   args);
  }

  private static Run runWith(List<String> jvmOptions,
                         byte[] stdin,
                         ProcessBuilder.Redirect stdout,
                         String... args) throws IOException {
    Path in  = Files.createTempFile(temp,
                                    "stdin",
                                    ".bin");
    Path out = Files.createTempFile(temp,
                                    "stdout",
                                    ".bin");
    Path err = Files.createTempFile(temp,
                                    "stderr",
                                    ".txt");
    Files.write(in,
                stdin == null ?
                new byte[0] :
                stdin);
    var command = new ArrayList<String>();
    command.add(Path.of(System.getProperty("java.home"),
                        "bin",
                        "java")
                    .toString());
    command.addAll(jvmOptions);
    command.add("-jar");
    command.add(jar.toString());
    command.addAll(List.of(args));
    var builder = new ProcessBuilder(command).redirectInput(in.toFile())
                                             .redirectOutput(stdout == null ?
                                                             ProcessBuilder.Redirect.to(out.toFile()) :
                                                             stdout)
                                             .redirectError(err.toFile());
    // Options of the build environment must not add text to standard error.
    builder.environment()
           .remove("JAVA_TOOL_OPTIONS");
    Process process = builder.start();
    try {
      if (!process.waitFor(TIMEOUT_SECONDS,
                           TimeUnit.SECONDS)) {
        process.destroyForcibly();
        throw new AssertionError("no exit within " + TIMEOUT_SECONDS + " s: " + String.join(" ",
                                                                                           command));
      }
    } catch (InterruptedException e) {
      Thread.currentThread()
            .interrupt();
      throw new AssertionError(e);
    } finally {
      if (process.isAlive()) {
        process.destroyForcibly();
      }
    }
    return new Run(process.exitValue(),
                   Files.readAllBytes(out),
                   Files.readString(err));
  }

  private static Run run(String... args) throws IOException {
    return runWith(null,
                   null,
                   args);
  }

  private static Path file(String name) {
    return work.resolve(name);
  }

  private static JsonNode json(Run run) {
    return new ObjectMapper().readTree(run.outText());
  }

  @Test
  void versionIsResolvedFromTheBuild() throws IOException {
    var run = run("--version");

    assertEquals(0,
                 run.exitCode(),
                 run.err());
    assertTrue(run.outText()
                  .startsWith("Paniolo " + version + "\n"),
               run.outText());
    assertFalse(run.outText()
                   .contains("${"),
                run.outText());
    assertEquals("",
                 run.err());
  }

  @Test
  void aValidInvoiceHasExitCodeZero() throws IOException {
    var run = run("validate",
                  file("xrechnung.xml").toString());

    assertEquals(0,
                 run.exitCode(),
                 run.err());
    assertTrue(run.outText()
                  .contains(": valid ("),
               run.outText());
    assertEquals("",
                 run.err());
  }

  @Test
  void aWellFormedButInvalidInvoiceHasExitCodeOneAndAFinding() throws IOException {
    Path broken = file("broken.xml");
    Files.writeString(broken,
                      "<rsm:CrossIndustryInvoice xmlns:rsm=\"urn:un:unece:uncefact:data:standard:CrossIndustryInvoice:100\"/>");

    var run = run("validate",
                  "--format",
                  "json",
                  broken.toString());

    assertEquals(1,
                 run.exitCode(),
                 run.err());
    var report = json(run);
    assertFalse(report.get("valid")
                      .asBoolean());
    assertTrue(report.get("errors")
                     .asInt() > 0);
    assertEquals("",
                 run.err());
  }

  @Test
  void usageErrorsHaveExitCodeTwo() throws IOException {
    var unknown = run("--no-such-option");
    assertEquals(2,
                 unknown.exitCode());
    assertEquals("",
                 unknown.outText());
    assertTrue(unknown.err()
                      .contains("Unknown option"),
               unknown.err());

    var missing = run("validate");
    assertEquals(2,
                 missing.exitCode());
    assertEquals("",
                 missing.outText());
    assertTrue(missing.err()
                      .contains("Missing required parameter"),
               missing.err());
  }

  @Test
  void aMissingInputFileHasExitCodeThreeAndTheMessageOnStandardError() throws IOException {
    var run = run("extract-xml",
                  file("does-not-exist.pdf").toString());

    assertEquals(3,
                 run.exitCode());
    assertEquals("",
                 run.outText());
    assertTrue(run.err()
                  .startsWith("paniolo: "),
               run.err());
  }

  @Test
  void validateReportsAMissingFileInItsReportWithExitCodeThree() throws IOException {
    var run = run("validate",
                  file("does-not-exist.xml").toString());

    assertEquals(3,
                 run.exitCode());
    assertTrue(run.outText()
                  .contains("File not found"),
               run.outText());
  }

  @Test
  void extractXmlFromStandardInputWritesExactlyTheEmbeddedXml() throws IOException {
    byte[] expected = Paniolo.create()
                             .extractXml(hybrid);

    var run = runWith(hybrid,
                  null,
                  "extract-xml",
                  "-");

    assertEquals(0,
                 run.exitCode(),
                 run.err());
    assertArrayEquals(expected,
                      run.out());
    assertEquals("",
                 run.err());
  }

  @Test
  void extractXmlRefusesDataThatIsNoPdfWithoutOutput() throws IOException {
    var run = runWith("%PDF-1.4\nthis is not a pdf".getBytes(StandardCharsets.ISO_8859_1),
                  null,
                  "extract-xml",
                  "-");

    assertEquals(3,
                 run.exitCode());
    assertEquals(0,
                 run.out().length);
    assertTrue(run.err()
                  .startsWith("paniolo: "),
               run.err());
  }

  /**
   * The hybrid PDF without its output intent: container and invoice XML stay valid, only PDF/A
   * conformance fails.
   */
  private Path withoutOutputIntent() throws IOException {
    Path target = file("no-output-intent.pdf");
    try (var document = Loader.loadPDF(hybrid)) {
      document.getDocumentCatalog()
              .getCOSObject()
              .removeItem(COSName.getPDFName("OutputIntents"));
      document.save(target.toFile());
    }
    return target;
  }

  @Test
  void pdfAConformanceIsCheckedThroughTheServiceLoaders() throws IOException {
    var control = run("validate",
                      "--format",
                      "json",
                      file("hybrid.pdf").toString());
    // control: the same PDF with its output intent is accepted, so the finding below does not come
    // from a general import problem
    Files.write(file("hybrid.pdf"),
                hybrid);
    assertEquals(0,
                 control.exitCode(),
                 control.outText() + control.err());
    assertTrue(json(control).get("valid")
                            .asBoolean());

    var broken = run("validate",
                     "--format",
                     "json",
                     withoutOutputIntent().toString());

    assertEquals(1,
                 broken.exitCode(),
                 broken.outText() + broken.err());
    var report = json(broken);
    assertFalse(report.get("valid")
                      .asBoolean());
    boolean pdfaFinding = false;
    for (JsonNode message : report.get("messages")) {
      String rule = message.get("rule")
                           .asString();
      assertFalse(rule.equals("PDFA-NOT-CHECKED"),
                  "the PDF/A stage did not run");
      pdfaFinding |= VALID_STAGE_PDF_A.equals(message.get("stage")
                                                    .asString()) && "ERROR".equals(message.get("severity")
                                                                                          .asString()) && rule.startsWith("PDFA-");
    }
    assertTrue(pdfaFinding,
               broken.outText());
    assertEquals(0,
                 report.get("fatals")
                       .asInt());
  }

  @Test
  void aPdfWithTooManyAttachmentsIsRefusedWithExitCodeThree() throws IOException {
    var args = new ArrayList<String>(List.of("to-facturx",
                                             file("invoice.json").toString(),
                                             "--pdf",
                                             file("visual.pdf").toString(),
                                             "--profile",
                                             "en16931"));
    for (int i = 0; i < 1_001; i++) {
      Path attachment = file("many-" + i + ".txt");
      Files.writeString(attachment,
                        "x");
      args.add("--attach");
      args.add(attachment.toString());
    }
    Path pdf = file("many.pdf");
    args.add("-o");
    args.add(pdf.toString());
    assertEquals(0,
                 run(args.toArray(String[]::new)).exitCode());

    var run = run("attachments",
                  pdf.toString());

    assertEquals(3,
                 run.exitCode());
    assertEquals(0,
                 run.out().length);
    assertTrue(run.err()
                  .contains("more than 1000 attachments"),
               run.err());
  }

  /**
   * A small PDF (about 1 MiB) with one embedded file that inflates to 1 GiB of zeros.
   */
  private static Path highlyCompressed(String name) throws IOException {
    Path target = file("compressed-" + name + ".pdf");
    try (var document = new PDDocument()) {
      document.addPage(new PDPage());
      InputStream zeros = new InputStream() {
        private long left = 1L << 30;

        @Override
        public int read() {
          return left-- > 0 ?
                 0 :
                 -1;
        }

        @Override
        public int read(byte[] b,
                        int off,
                        int len) {
          if (left <= 0) {
            return -1;
          }
          int n = (int) Math.min(len,
                                 left);
          java.util.Arrays.fill(b,
                                off,
                                off + n,
                                (byte) 0);
          left -= n;
          return n;
        }
      };
      var embedded = new PDEmbeddedFile(document,
                                        zeros,
                                        COSName.FLATE_DECODE);
      embedded.setSubtype("application/octet-stream");
      var spec = new PDComplexFileSpecification();
      spec.setFile(name);
      spec.setFileUnicode(name);
      spec.setEmbeddedFile(embedded);
      var tree = new PDEmbeddedFilesNameTreeNode();
      tree.setNames(java.util.Map.of(name,
                                     spec));
      var names = new PDDocumentNameDictionary(document.getDocumentCatalog());
      names.setEmbeddedFiles(tree);
      document.getDocumentCatalog()
              .setNames(names);
      document.save(target.toFile());
    }
    assertTrue(Files.size(target) < 4 << 20);
    return target;
  }

  @Test
  void aHighlyCompressedFileIsRefusedWithinAHeapThatCannotHoldItsContent() throws IOException {
    // The expanded content (1 GiB) is far above the heap of the process; the limit stops the decoder
    // before that, so the process ends regularly with exit code 3 and no OutOfMemoryError.
    var heap = List.of("-Xmx300m");

    var attachment = runWith(heap,
                             null,
                             null,
                             "attachments",
                             highlyCompressed("big.bin").toString());
    assertEquals(3,
                 attachment.exitCode(),
                 attachment.err());
    assertEquals(0,
                 attachment.out().length);
    assertTrue(attachment.err()
                         .contains("exceeds"),
               attachment.err());
    assertFalse(attachment.err()
                          .contains("OutOfMemory"),
                attachment.err());

    var xml = runWith(heap,
                      null,
                      null,
                      "extract-xml",
                      highlyCompressed("factur-x.xml").toString());
    assertEquals(3,
                 xml.exitCode(),
                 xml.err());
    assertEquals(0,
                 xml.out().length);
    assertFalse(xml.err()
                   .contains("OutOfMemory"),
                xml.err());
  }

  /**
   * A tiny PDF whose embedded file declares a predictor with rows of 250 MB: PDFBox would allocate two
   * buffers of that size before it writes a byte.
   */
  private static Path predictorRows(String name) throws IOException {
    Path target = file("predictor-" + name + ".pdf");
    try (var document = new PDDocument()) {
      document.addPage(new PDPage());
      var embedded = new PDEmbeddedFile(document,
                                        new java.io.ByteArrayInputStream(new byte[10]),
                                        COSName.FLATE_DECODE);
      var parameters = new org.apache.pdfbox.cos.COSDictionary();
      parameters.setInt(COSName.PREDICTOR,
                        12);
      parameters.setInt(COSName.COLUMNS,
                        2_000_000_000);
      parameters.setInt(COSName.COLORS,
                        1);
      parameters.setInt(COSName.BITS_PER_COMPONENT,
                        1);
      embedded.getCOSObject()
              .setItem(COSName.DECODE_PARMS,
                       parameters);
      embedded.setSubtype("application/octet-stream");
      var spec = new PDComplexFileSpecification();
      spec.setFile(name);
      spec.setFileUnicode(name);
      spec.setEmbeddedFile(embedded);
      var tree = new PDEmbeddedFilesNameTreeNode();
      tree.setNames(java.util.Map.of(name,
                                     spec));
      var names = new PDDocumentNameDictionary(document.getDocumentCatalog());
      names.setEmbeddedFiles(tree);
      document.getDocumentCatalog()
              .setNames(names);
      document.save(target.toFile());
    }
    return target;
  }

  @Test
  void aHugePredictorRowIsRefusedWithinAHeapThatCannotHoldIt() throws IOException {
    // Two rows of 250 MB are far above the heap of the process; the parameters are checked before
    // PDFBox allocates them, so the process ends regularly with exit code 3 and no OutOfMemoryError.
    var heap = List.of("-Xmx300m");

    for (var command : List.of(List.of("attachments",
                                       predictorRows("big.bin").toString()),
                               List.of("extract-xml",
                                       predictorRows("factur-x.xml").toString()))) {
      var run = runWith(heap,
                        null,
                        null,
                        command.toArray(String[]::new));
      assertEquals(3,
                   run.exitCode(),
                   command + ": " + run.err());
      assertEquals(0,
                   run.out().length);
      assertTrue(run.err()
                    .startsWith("paniolo: "),
                 run.err());
      assertFalse(run.err()
                     .contains("OutOfMemory"),
                  run.err());
    }

    // a normal job right after, in the same kind of process
    Files.write(file("hybrid.pdf"),
                hybrid);
    var next = runWith(heap,
                       null,
                       null,
                       "extract-xml",
                       file("hybrid.pdf").toString());
    assertEquals(0,
                 next.exitCode(),
                 next.err());
    assertArrayEquals(Paniolo.create()
                             .extractXml(hybrid),
                      next.out());
  }

  @Test
  void aFailedWriteToStandardOutputHasExitCodeThree() throws IOException {
    // /dev/full accepts the open and fails every write (Linux)
    Path full = Path.of("/dev/full");
    assumeTrue(Files.exists(full),
               "needs /dev/full");
    Path pdf = file("hybrid.pdf");
    Files.write(pdf,
                hybrid);

    var run = runWith(null,
                  ProcessBuilder.Redirect.appendTo(full.toFile()),
                  "extract-xml",
                  pdf.toString());

    assertEquals(3,
                 run.exitCode());
    assertTrue(run.err()
                  .contains("cannot write to standard output"),
               run.err());
  }
}

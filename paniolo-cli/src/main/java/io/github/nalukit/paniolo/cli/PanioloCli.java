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
import io.github.nalukit.paniolo.config.FacturXProfile;
import io.github.nalukit.paniolo.config.FacturXResult;
import io.github.nalukit.paniolo.config.InvoiceSyntax;
import io.github.nalukit.paniolo.config.PdfAttachment;
import io.github.nalukit.paniolo.config.PdfLayout;
import io.github.nalukit.paniolo.config.UnknownDocumentHandling;
import io.github.nalukit.paniolo.json.PanioloJson;
import io.github.nalukit.paniolo.model.Invoice;
import io.github.nalukit.paniolo.validation.ValidationMessage;
import io.github.nalukit.paniolo.validation.ValidationProfile;
import io.github.nalukit.paniolo.validation.ValidationResult;
import io.github.nalukit.paniolo.visualizer.PanioloVisualizer;
import io.github.nalukit.paniolo.xml.ReadMode;
import io.github.nalukit.paniolo.xml.ReadResult;
import io.github.nalukit.paniolo.xml.ReadWarning;
import picocli.CommandLine;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ScopeType;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Command line interface of Paniolo: offers the methods of the {@link Paniolo} facade as commands,
 * so that invoices, orders, and despatch advices can be validated, read, written, and converted in
 * scripts.
 *
 * <pre>{@code
 * paniolo validate invoice.xml
 * paniolo validate corpus/negative -r --expect invalid --summary
 * paniolo inventory archive -r --summary-only
 * paniolo read invoice.pdf -o invoice.json
 * paniolo to-xml invoice.json --syntax CII -o invoice.xml
 * paniolo to-facturx invoice.json --pdf visual.pdf --profile EN16931 -o factur-x.pdf
 * paniolo to-facturx invoice.json --profile XRECHNUNG --logo logo.png -o factur-x.pdf
 * paniolo embed-xml received.xml --pdf visual.pdf --profile EN16931 -o factur-x.pdf
 * paniolo visualize factur-x.pdf --validate -o factur-x.html
 * }</pre>
 *
 * <p>Files are given as paths; {@code -} stands for standard input and output. Exit codes:
 * {@code 0} success, {@code 1} document invalid (with {@code --expect}: not as expected),
 * {@code 2} wrong usage, {@code 3} processing error.
 */
@Command(name = "paniolo",
         mixinStandardHelpOptions = true,
         versionProvider = PanioloCli.Version.class,
         sortOptions = false,
         usageHelpAutoWidth = true,
         description = "Validates, reads, writes, and converts electronic invoices (XRechnung, EN 16931, Factur-X / ZUGFeRD), " + "Order-X orders, and despatch advices.",
         footerHeading = "%nExit codes:%n",
         footer = { "  0  success, all documents valid or as expected",
                    "  1  at least one document invalid, or not as expected with --expect",
                    "  2  wrong usage",
                    "  3  processing error, e.g. unreadable file or unsupported document",
                    "",
                    "Use - as file name for standard input and output." },
         subcommands = { InventoryCommand.class,
                         OrderCommand.class,
                         DespatchAdviceCommand.class })
public final class PanioloCli {
  static final int EXIT_OK      = 0;
  static final int EXIT_INVALID = 1;
  static final int EXIT_ERROR   = 3;

  private final Io          io;
  private final PrintStream err;
  private final PanioloJson json = PanioloJson.create();
  @Option(names = "--rules", paramLabel = "DIR", scope = ScopeType.INHERIT, description = "Extracted KoSIT XRechnung validator configuration instead of the packaged rules.")
  Path rules;
  @Option(names = "--facturx-rules", paramLabel = "DIR", scope = ScopeType.INHERIT, description = "Extracted Factur-X / ZUGFeRD package instead of the packaged rules.")
  Path facturXRules;
  @Option(names = "--extended-ctc-fr-rules", paramLabel = "DIR", scope = ScopeType.INHERIT, description = "Directory with the French EXTENDED-CTC-FR rules of FNFE-MPE.")
  Path extendedCtcFrRules;
  @Option(names = "--order-x-rules", paramLabel = "DIR", scope = ScopeType.INHERIT, description = "Other Order-X rules (Schematron compiled to SVRL) instead of the packaged Order-X 1.0 rules.")
  Path orderXRules;
  @Option(names = "--despatch-advice-rules", paramLabel = "FILE", scope = ScopeType.INHERIT, description = "Compiled Schematron XSLT for UBL despatch advices, e.g. of Peppol.")
  Path despatchAdviceRules;
  @Option(names = "--calculation-check", scope = ScopeType.INHERIT, description = "Recalculate the amounts of validated invoices and report PANIOLO-CALC findings in the stage CALCULATION; they are warnings and do not change the validity.")
  boolean calculationCheck;
  @Option(names = "--rule-statistics", scope = ScopeType.INHERIT, description = "Add execution statistics to validation reports: per Schematron stylesheet the active patterns, fired rules, failed asserts, and successful reports, per PDF/A check the checks and failures per rule. Slower; for audits and diagnosis.")
  boolean ruleStatistics;
  @Option(names = "--unknown-documents", paramLabel = "MODE", defaultValue = "THROW", scope = ScopeType.INHERIT, description = "Documents whose type validate cannot detect (malformed XML, unsupported root element): ${COMPLETION-CANDIDATES}. THROW (default) reports them as processing error (exit code 3); REPORT_INVALID, the Mustang compatible mode, as invalid with a FATAL finding XML-PARSE or PANIOLO-UNKNOWN-DOCUMENT (exit code 1).")
  UnknownDocumentHandling unknownDocuments;
  @Option(names = { "-v",
                    "--verbose" }, scope = ScopeType.INHERIT, description = "Log details and print stack traces of errors to standard error.")
  boolean verbose;
  private       Paniolo     paniolo;

  PanioloCli(InputStream in,
             PrintStream out,
             PrintStream err) {
    this.io  = new Io(in,
                      out);
    this.err = err;
  }

  /**
   * Runs one command and exits the JVM with its exit code.
   *
   * @param args the command line arguments
   */
  public static void main(String[] args) {
    System.exit(execute(System.in,
                        new PrintStream(System.out,
                                        false,
                                        StandardCharsets.UTF_8),
                        new PrintStream(System.err,
                                        true,
                                        StandardCharsets.UTF_8),
                        args));
  }

  /**
   * Runs one command and returns its exit code; the streams are not closed.
   */
  static int execute(InputStream in,
                     PrintStream out,
                     PrintStream err,
                     String... args) {
    var cli = new PanioloCli(in,
                             out,
                             err);
    var commandLine = new CommandLine(cli);
    commandLine.setCaseInsensitiveEnumValuesAllowed(true)
               .setOut(new PrintWriter(out,
                                       true))
               .setErr(new PrintWriter(err,
                                       true))
               .setExecutionStrategy(parseResult -> {
                 cli.configureLogging();
                 return new CommandLine.RunLast().execute(parseResult);
               })
               .setExecutionExceptionHandler((exception, command, parseResult) -> {
                 if (cli.verbose) {
                   exception.printStackTrace(err);
                 }
                 command.getErr()
                        .println("paniolo: " + message(exception));
                 return EXIT_ERROR;
               });
    int exitCode = commandLine.execute(args);
    out.flush();
    return exitCode;
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

  @Command(name = "validate",
           description = { "Validates invoice XML or a Factur-X / ZUGFeRD PDF against XSD, EN 16931 and XRechnung, " + "or the rules of its Factur-X profile.",
                           "Without options, the document type and the rules are detected from the content; PDFs are also " + "checked for the container and PDF/A conformance." })
  int validate(
      @Mixin ReportOptions report,
      @ArgGroup ValidationTarget target,
      @Parameters(paramLabel = "FILE", arity = "1..*", description = "Invoice XML or PDF, or a directory; - for standard input.") List<Path> files) {
    return report.run(io,
                      files,
                      content -> {
                        if (target != null && target.syntax != null) {
                          return paniolo().validate(content,
                                                    target.syntax);
                        }
                        if (target != null && target.profile != null) {
                          return paniolo().validate(content,
                                                    target.profile);
                        }
                        return paniolo().validate(content);
                      },
                      ReportOptions.pdfReport(paniolo()));
  }

  @Command(name = "detect", description = "Prints the document type: UBL_INVOICE, UBL_CREDIT_NOTE, CII, or PDF.")
  int detect(
      @Parameters(paramLabel = "FILE", description = "Invoice XML or PDF; - for standard input.") Path file) {
    byte[] content = io.read(file);
    io.write(null,
             Paniolo.isPdf(content) ?
             "PDF" :
             paniolo().detect(content)
                      .name());
    return EXIT_OK;
  }

  @Command(name = "read",
           description = { "Reads an invoice from UBL, CII, ZUGFeRD 1.0, or a Factur-X / ZUGFeRD PDF and writes it as JSON.",
                           "Deviations accepted with --lenient are printed to standard error." })
  int read(
      @Option(names = "--lenient", description = "Accept line amounts and totals the model does not reproduce (ReadMode.LENIENT).") boolean lenient,
      @Option(names = { "-o",
                        "--output" }, paramLabel = "FILE", description = "JSON file; standard output if not given.") Path output,
      @Parameters(paramLabel = "FILE", description = "Invoice XML or PDF; - for standard input.") Path file) {
    ReadResult result = paniolo().read(io.read(file),
                                       lenient ?
                                       ReadMode.LENIENT :
                                       ReadMode.STRICT);
    for (ReadWarning warning : result.warnings()) {
      err.println("warning " +
                  warning.code() +
                  (warning.lineId() == null ?
                   "" :
                   " (line " + warning.lineId() + ")") +
                  ": " +
                  warning.message());
    }
    io.write(output,
             json.toPrettyJson(result.invoice()));
    return EXIT_OK;
  }

  @Command(name = "to-xml",
           description = { "Writes an invoice as UBL or CII XML.",
                           "The invoice is read from Paniolo JSON or from any readable invoice (XML or PDF), so this " + "command also converts between UBL and CII." })
  int toXml(
      @Option(names = { "-s",
                        "--syntax" }, required = true, description = "Target syntax: ${COMPLETION-CANDIDATES}.") InvoiceSyntax syntax,
      @Option(names = { "-o",
                        "--output" }, paramLabel = "FILE", description = "XML file; standard output if not given.") Path output,
      @Parameters(paramLabel = "FILE", description = "Invoice as JSON, XML, or PDF; - for standard input.") Path file) {
    io.write(output,
             paniolo().toBytes(invoice(io.read(file)),
                               syntax));
    return EXIT_OK;
  }

  @Command(name = "to-facturx",
           description = { "Creates a Factur-X / ZUGFeRD PDF/A-3: writes the invoice as CII and embeds it into a PDF/A document.",
                           "Without --pdf, the visual document is rendered from the invoice (see to-pdf)." })
  int toFacturX(
      @Option(names = "--pdf", paramLabel = "FILE", description = "PDF/A-1, -2, or -3 document with the visual representation of the invoice; rendered if not given.") Path pdf,
      @Option(names = { "-p",
                        "--profile" }, required = true, description = "Factur-X profile: ${COMPLETION-CANDIDATES}.") FacturXProfile profile,
      @Option(names = { "-a",
                        "--attach" }, paramLabel = "FILE", description = "Further file to embed, e.g. a time sheet; repeatable.") List<Path> attachments,
      @Mixin LayoutOptions layout,
      @Option(names = { "-o",
                        "--output" }, paramLabel = "FILE", description = "PDF file; standard output if not given.") Path output,
      @Parameters(paramLabel = "FILE", description = "Invoice as JSON or XML; - for standard input.") Path file) {
    Invoice invoice = invoice(io.read(file));
    io.write(output,
             pdf == null ?
             paniolo().toFacturX(invoice,
                                 profile,
                                 layout.layout(io),
                                 attachments(attachments)) :
             paniolo().toFacturX(invoice,
                                 io.read(pdf),
                                 profile,
                                 attachments(attachments)));
    return EXIT_OK;
  }

  @Command(name = "embed-xml",
           description = { "Creates a Factur-X / ZUGFeRD PDF/A-3 from existing CII XML: embeds the XML byte for byte into a PDF/A document.",
                           "Unlike to-facturx, the XML is not read and written again. Profile and XML are checked before embedding; " +
                           "the result is validated (container, PDF/A, XML) and the findings are printed to standard error. " +
                           "Valid means valid XML and container, not that the PDF shows the same content." })
  int embedXml(
      @Option(names = "--pdf", required = true, paramLabel = "FILE", description = "PDF/A-1, -2, or -3 document with the visual representation of the invoice.") Path pdf,
      @Option(names = { "-p",
                        "--profile" }, required = true, description = "Factur-X profile: ${COMPLETION-CANDIDATES}.") FacturXProfile profile,
      @Option(names = { "-a",
                        "--attach" }, paramLabel = "FILE", description = "Further file to embed, e.g. a time sheet; repeatable.") List<Path> attachments,
      @Option(names = { "-o",
                        "--output" }, paramLabel = "FILE", description = "PDF file; standard output if not given.") Path output,
      @Parameters(paramLabel = "FILE", description = "CII invoice XML; - for standard input.") Path file) {
    FacturXResult result = paniolo().embedXml(io.read(pdf),
                                              io.read(file),
                                              profile,
                                              attachments(attachments));
    for (ValidationMessage message : result.validation()
                                           .messages()) {
      err.println(message.severity()
                         .name()
                         .toLowerCase(Locale.ROOT) +
                  " " +
                  message.ruleId() +
                  (message.location() == null || message.location()
                                                        .isEmpty() ?
                   "" :
                   " at " + message.location()) +
                  ": " +
                  message.message());
    }
    io.write(output,
             result.pdf());
    return result.isValid() ?
           EXIT_OK :
           EXIT_INVALID;
  }

  @Command(name = "to-pdf",
           description = { "Renders an invoice as visual PDF/A-3b document without embedded XML.",
                           "Use to-facturx without --pdf for a hybrid invoice." })
  int toPdf(
      @Mixin LayoutOptions layout,
      @Option(names = { "-o",
                        "--output" }, paramLabel = "FILE", description = "PDF file; standard output if not given.") Path output,
      @Parameters(paramLabel = "FILE", description = "Invoice as JSON, XML, or PDF; - for standard input.") Path file) {
    io.write(output,
             paniolo().toPdf(invoice(io.read(file)),
                             layout.layout(io)));
    return EXIT_OK;
  }

  @Command(name = "extract-xml", description = "Writes the invoice XML embedded in a Factur-X / ZUGFeRD PDF, unchanged.")
  int extractXml(
      @Option(names = { "-o",
                        "--output" }, paramLabel = "FILE", description = "XML file; standard output if not given.") Path output,
      @Parameters(paramLabel = "FILE", description = "PDF; - for standard input.") Path file) {
    io.write(output,
             paniolo().extractXml(io.read(file)));
    return EXIT_OK;
  }

  @Command(name = "attachments",
           description = { "Lists the files embedded in a PDF apart from the invoice XML: name, MIME type, relationship, size.",
                           "With --extract, the files are written to a directory." })
  int attachments(
      @Option(names = { "-x",
                        "--extract" }, paramLabel = "DIR", description = "Directory to write the attachments to.") Path directory,
      @Parameters(paramLabel = "FILE", description = "PDF; - for standard input.") Path file) {
    var list = new StringBuilder();
    for (PdfAttachment attachment : paniolo().attachments(io.read(file))) {
      list.append(attachment.fileName())
          .append('\t')
          .append(attachment.mimeType())
          .append('\t')
          .append(attachment.relationship()
                            .getPdfName())
          .append('\t')
          .append(attachment.content().length)
          .append('\n');
      if (directory != null) {
        io.write(directory.resolve(attachment.fileName()),
                 attachment.content());
      }
    }
    if (!list.isEmpty()) {
      io.write(null,
               list.toString());
    }
    return EXIT_OK;
  }

  @Command(name = "producer", description = "Prints the software that produced a PDF, from document information and XMP metadata.")
  int producer(
      @Parameters(paramLabel = "FILE", description = "PDF; - for standard input.") Path file) {
    io.write(null,
             paniolo().producer(io.read(file))
                      .describe());
    return EXIT_OK;
  }

  @Command(name = "migrate-zugferd1", description = "Migrates a ZUGFeRD 1.0 document (XML or PDF) to ZUGFeRD 2 / Factur-X; the result has the same format.")
  int migrateZugferd1(
      @Option(names = { "-p",
                        "--profile" }, description = "Target Factur-X profile; derived from the ZUGFeRD 1.0 profile if not given.") FacturXProfile profile,
      @Option(names = { "-o",
                        "--output" }, paramLabel = "FILE", description = "Output file; standard output if not given.") Path output,
      @Parameters(paramLabel = "FILE", description = "ZUGFeRD 1.0 XML or PDF; - for standard input.") Path file) {
    byte[] document = io.read(file);
    io.write(output,
             profile == null ?
             paniolo().migrateZugferd1(document) :
             paniolo().migrateZugferd1(document,
                                       profile));
    return EXIT_OK;
  }

  @Command(name = "visualize",
           description = { "Writes a standalone HTML page that shows an invoice, order, or despatch advice (XML or PDF) in the browser.",
                           "The page contains the Paniolo web component and the document and needs no network access. " + "With --validate or --report, the validation result is shown as well." })
  int visualize(
      @Option(names = { "-l",
                        "--lang" }, paramLabel = "LANG", defaultValue = "de", description = "Language of the labels: de, en, or fr; default: ${DEFAULT-VALUE}.") String language,
      @ArgGroup ReportSource source,
      @Option(names = { "-o",
                        "--output" }, paramLabel = "FILE", description = "HTML file; standard output if not given.") Path output,
      @Parameters(paramLabel = "FILE", description = "Invoice, order, or despatch advice as XML or PDF; - for standard input.") Path file) {
    byte[] document = io.read(file);
    byte[] validationReport = source == null ?
                              null :
                              source.report != null ?
                              io.read(source.report) :
                              validateAny(document).toXml()
                                                   .getBytes(StandardCharsets.UTF_8);
    io.write(output,
             PanioloVisualizer.create()
                              .toHtml(document,
                                      validationReport,
                                      Locale.forLanguageTag(language)));
    return EXIT_OK;
  }

  /**
   * Validates an invoice, an Order-X order, or a UBL despatch advice, chosen by the root element.
   */
  ValidationResult validateAny(byte[] document) {
    String head = new String(document,
                             0,
                             Math.min(document.length,
                                      4096),
                             StandardCharsets.ISO_8859_1);
    if (head.contains("SCRDMCCBDACIOMessageStructure")) {
      return paniolo().validateOrder(document);
    }
    if (head.contains("DespatchAdvice-2")) {
      return paniolo().validateDespatchAdvice(document);
    }
    if (head.contains("SCRDMCCBDACIDAMessageStructure")) {
      throw new IllegalArgumentException("CIDA delivery notes cannot be validated: no schema is published");
    }
    return paniolo().validate(document);
  }

  /**
   * The facade, configured with the rule options; created on first use.
   */
  Paniolo paniolo() {
    if (paniolo == null) {
      var builder = Paniolo.builder();
      if (rules != null) {
        builder.rules(rules);
      }
      if (facturXRules != null) {
        builder.facturXRules(facturXRules);
      }
      if (extendedCtcFrRules != null) {
        builder.extendedCtcFrRules(extendedCtcFrRules);
      }
      if (orderXRules != null) {
        builder.orderXRules(orderXRules);
      }
      if (despatchAdviceRules != null) {
        builder.despatchAdviceRules(despatchAdviceRules);
      }
      paniolo = builder.calculationCheck(calculationCheck)
                       .ruleStatistics(ruleStatistics)
                       .unknownDocuments(unknownDocuments)
                       .build();
    }
    return paniolo;
  }

  Io io() {
    return io;
  }

  PanioloJson json() {
    return json;
  }

  List<PdfAttachment> attachments(List<Path> files) {
    return files == null ?
           List.of() :
           io.attachments(files);
  }

  /**
   * An invoice from Paniolo JSON, or from XML or PDF.
   */
  private Invoice invoice(byte[] content) {
    return Io.isJson(content) ?
           json.read(content,
                     Invoice.class) :
           paniolo().read(content);
  }

  /**
   * Paniolo logs little; the PDF libraries log a lot. Without --verbose only errors are shown.
   */
  private void configureLogging() {
    Logger root = Logger.getLogger("");
    Level level = verbose ?
                  Level.FINE :
                  Level.SEVERE;
    root.setLevel(level);
    for (var handler : root.getHandlers()) {
      handler.setLevel(level);
    }
  }



  /**
   * Settings of the rendered PDF of to-pdf and to-facturx.
   */
  static final class LayoutOptions {
    @Option(names = { "-l",
                      "--lang" }, paramLabel = "LANG", defaultValue = "de", description = "Language of the rendered PDF: de, en, or fr; default: ${DEFAULT-VALUE}.")
    String language;

    @Option(names = "--logo", paramLabel = "FILE", description = "PNG or JPEG logo for the rendered PDF; shown in the letterhead.")
    Path logo;

    @Option(names = "--logo-position", paramLabel = "SIDE", defaultValue = "LEFT", description = "Side of the letterhead the logo is shown on: ${COMPLETION-CANDIDATES}; default: ${DEFAULT-VALUE}.")
    PdfLayout.LogoPosition logoPosition;

    @Option(names = "--no-sender-address", description = "Hides the sender's address in the letterhead, e.g. for paper with a preprinted letterhead.")
    boolean noSenderAddress;

    @Option(names = "--font", paramLabel = "FILE", description = "TrueType font for the rendered PDF instead of Liberation Sans.")
    Path font;

    @Option(names = "--bold-font", paramLabel = "FILE", description = "TrueType font for bold text; requires --font.")
    Path boldFont;

    @Option(names = "--accent", paramLabel = "RGB", description = "Accent color of the rendered PDF, e.g. 01244F.")
    String accent;

    @Option(names = "--footer", paramLabel = "TEXT", description = "Additional footer text of the rendered PDF, e.g. management and register court.")
    String footer;

    @Option(names = "--watermark", paramLabel = "TEXT", description = "Text printed diagonally across every page, e.g. PREVIEW; only for the invoice command to-pdf, the commands that embed XML reject it.")
    String watermark;

    PdfLayout layout(Io io) {
      var layout = PdfLayout.of(Locale.forLanguageTag(language))
                            .withFooter(footer)
                            .withWatermark(watermark)
                            .withShowSenderAddress(!noSenderAddress)
                            .withLogoPosition(logoPosition);
      if (logo != null) {
        layout = layout.withLogo(io.read(logo))
                       .withShowLogo(true);
      }
      if (font != null) {
        layout = layout.withFonts(io.read(font),
                                  boldFont == null ?
                                  null :
                                  io.read(boldFont));
      } else if (boldFont != null) {
        throw new IllegalArgumentException("--bold-font requires --font");
      }
      if (accent != null) {
        try {
          layout = layout.withAccentColor(Integer.parseInt(accent.replace("#",
                                                                          ""),
                                                           16));
        } catch (NumberFormatException e) {
          throw new IllegalArgumentException("--accent must be a hexadecimal RGB color such as 01244F, but is " + accent);
        }
      }
      return layout;
    }
  }



  /**
   * Validation result shown by visualize: validated on the fly or read from a file; at most one of both.
   */
  static final class ReportSource {
    @Option(names = "--validate", required = true, description = "Validates the document and shows the result; not for CIDA delivery notes.")
    boolean validate;

    @Option(names = "--report", required = true, paramLabel = "FILE", description = "Validation report to show: the XML of validate -f xml, or SVRL.")
    Path report;
  }



  /**
   * Explicit document type or Factur-X profile of a validation; at most one of both.
   */
  static final class ValidationTarget {
    @Option(names = { "-s",
                      "--syntax" }, description = "Validate as this document type with EN 16931 and XRechnung: ${COMPLETION-CANDIDATES}.")
    ValidationProfile.Syntax syntax;

    @Option(names = { "-p",
                      "--profile" }, description = "Validate with the rules of this Factur-X profile: ${COMPLETION-CANDIDATES}.")
    FacturXProfile profile;
  }



  /**
   * Version of the build, for {@code --version}.
   */
  static final class Version
      implements CommandLine.IVersionProvider {

    @Override
    public String[] getVersion() {
      try (InputStream input = PanioloCli.class.getResourceAsStream("paniolo-cli.properties")) {
        var properties = new Properties();
        if (input != null) {
          properties.load(input);
        }
        return new String[] { "Paniolo " + properties.getProperty("version",
                                                                  "unknown"),
                              "Java " + Runtime.version() };
      } catch (IOException e) {
        return new String[] { "Paniolo unknown" };
      }
    }
  }
}

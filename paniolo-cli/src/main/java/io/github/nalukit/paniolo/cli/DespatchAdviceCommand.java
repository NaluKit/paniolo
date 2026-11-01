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

import io.github.nalukit.paniolo.model.despatch.DespatchAdvice;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

import java.nio.file.Path;
import java.util.List;

/**
 * Despatch advices (delivery notes, advance shipping notices) in UBL and CIDA.
 */
@Command(name = "despatch-advice", mixinStandardHelpOptions = true, description = "Reads, validates, and writes despatch advices (delivery notes) as UBL DespatchAdvice or CIDA.")
final class DespatchAdviceCommand {
  @ParentCommand
  PanioloCli cli;

  @Command(name = "read", description = "Reads a despatch advice from UBL, CIDA, or a hybrid PDF delivery note and writes it as JSON.")
  int read(
      @Option(names = "--ignore-unsupported", description = "Accept despatch advices with content the model does not support.") boolean ignoreUnsupported,
      @Option(names = { "-o",
                        "--output" }, paramLabel = "FILE", description = "JSON file; standard output if not given.") Path output,
      @Parameters(paramLabel = "FILE", description = "UBL or CIDA XML, or PDF; - for standard input.") Path file) {
    cli.io()
       .write(output,
              cli.json()
                 .toPrettyJson(cli.paniolo()
                                  .readDespatchAdvice(cli.io()
                                                         .read(file),
                                                      ignoreUnsupported)));
    return PanioloCli.EXIT_OK;
  }

  @Command(name = "validate", description = "Validates a UBL DespatchAdvice, CIDA XML, or a hybrid PDF delivery note.")
  int validate(
      @Mixin ReportOptions report,
      @Parameters(paramLabel = "FILE", arity = "1..*", description = "UBL or CIDA XML, PDF, or a directory; - for standard input.") List<Path> files) {
    return report.run(cli.io(),
                      files,
                      content -> cli.paniolo()
                                    .validateDespatchAdvice(content),
                      ReportOptions.pdfReport(cli.paniolo()));
  }

  @Command(name = "to-xml", description = "Writes a despatch advice as UBL 2.1 DespatchAdvice or as UN/CEFACT CIDA.")
  int toXml(
      @Option(names = { "-s",
                        "--syntax" }, defaultValue = "UBL", description = "Target syntax: ${COMPLETION-CANDIDATES} (default: ${DEFAULT-VALUE}).") Syntax syntax,
      @Option(names = { "-o",
                        "--output" }, paramLabel = "FILE", description = "XML file; standard output if not given.") Path output,
      @Parameters(paramLabel = "FILE", description = "Despatch advice as JSON, XML, or PDF; - for standard input.") Path file) {
    DespatchAdvice advice = advice(file);
    cli.io()
       .write(output,
              syntax == Syntax.UBL ?
              cli.paniolo()
                 .toDespatchAdviceBytes(advice) :
              cli.paniolo()
                 .toCiiDespatchAdviceBytes(advice));
    return PanioloCli.EXIT_OK;
  }

  @Command(name = "to-pdf",
           description = { "Creates a hybrid PDF/A-3 delivery note: writes the despatch advice as CIDA and embeds it into a PDF/A document.",
                           "Without --pdf, the visual delivery note is rendered from the despatch advice." })
  int toPdf(
      @Option(names = "--pdf", paramLabel = "FILE", description = "PDF/A-1, -2, or -3 document with the visual delivery note; rendered if not given.") Path pdf,
      @Option(names = { "-a",
                        "--attach" }, paramLabel = "FILE", description = "Further file to embed; repeatable.") List<Path> attachments,
      @Mixin PanioloCli.LayoutOptions layout,
      @Option(names = { "-o",
                        "--output" }, paramLabel = "FILE", description = "PDF file; standard output if not given.") Path output,
      @Parameters(paramLabel = "FILE", description = "Despatch advice as JSON or XML; - for standard input.") Path file) {
    DespatchAdvice advice = advice(file);
    cli.io()
       .write(output,
              pdf == null ?
              cli.paniolo()
                 .toDespatchAdvicePdf(advice,
                                      layout.layout(cli.io()),
                                      cli.attachments(attachments)) :
              cli.paniolo()
                 .toDespatchAdvicePdf(advice,
                                      cli.io()
                                         .read(pdf),
                                      cli.attachments(attachments)));
    return PanioloCli.EXIT_OK;
  }

  private DespatchAdvice advice(Path file) {
    byte[] content = cli.io()
                        .read(file);
    return Io.isJson(content) ?
           cli.json()
              .read(content,
                    DespatchAdvice.class) :
           cli.paniolo()
              .readDespatchAdvice(content);
  }

  enum Syntax {
    UBL,
    CIDA
  }
}

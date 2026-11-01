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

import io.github.nalukit.paniolo.model.order.Order;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

import java.nio.file.Path;
import java.util.List;

/**
 * Order-X orders, order changes, and order responses.
 */
@Command(name = "order", mixinStandardHelpOptions = true, description = "Reads, validates, and writes Order-X orders, order changes, and order responses.")
final class OrderCommand {
  @ParentCommand
  PanioloCli cli;

  @Command(name = "read", description = "Reads an order from Order-X XML or a hybrid Order-X PDF and writes it as JSON.")
  int read(
      @Option(names = "--ignore-unsupported", description = "Accept orders with content the model does not support.") boolean ignoreUnsupported,
      @Option(names = { "-o",
                        "--output" }, paramLabel = "FILE", description = "JSON file; standard output if not given.") Path output,
      @Parameters(paramLabel = "FILE", description = "Order-X XML or PDF; - for standard input.") Path file) {
    cli.io()
       .write(output,
              cli.json()
                 .toPrettyJson(cli.paniolo()
                                  .readOrder(cli.io()
                                                .read(file),
                                             ignoreUnsupported)));
    return PanioloCli.EXIT_OK;
  }

  @Command(name = "validate", description = "Validates Order-X XML or a hybrid Order-X PDF against the official schema and Schematron of its profile.")
  int validate(
      @Mixin ReportOptions report,
      @Parameters(paramLabel = "FILE", arity = "1..*", description = "Order-X XML or PDF, or a directory; - for standard input.") List<Path> files) {
    return report.run(cli.io(),
                      files,
                      content -> cli.paniolo()
                                    .validateOrder(content),
                      ReportOptions.pdfReport(cli.paniolo()));
  }

  @Command(name = "to-xml", description = "Writes an order as Order-X XML in the profile of the order.")
  int toXml(
      @Option(names = { "-o",
                        "--output" }, paramLabel = "FILE", description = "XML file; standard output if not given.") Path output,
      @Parameters(paramLabel = "FILE", description = "Order as JSON, Order-X XML, or PDF; - for standard input.") Path file) {
    cli.io()
       .write(output,
              cli.paniolo()
                 .toOrderXBytes(order(file)));
    return PanioloCli.EXIT_OK;
  }

  @Command(name = "to-pdf",
           description = { "Creates a hybrid Order-X PDF/A-3: writes the order as Order-X XML and embeds it into a PDF/A document.",
                           "Without --pdf, the visual document is rendered from the order." })
  int toPdf(
      @Option(names = "--pdf", paramLabel = "FILE", description = "PDF/A-1, -2, or -3 document with the visual representation of the order; rendered if not given.") Path pdf,
      @Option(names = { "-a",
                        "--attach" }, paramLabel = "FILE", description = "Further file to embed; repeatable.") List<Path> attachments,
      @Mixin PanioloCli.LayoutOptions layout,
      @Option(names = { "-o",
                        "--output" }, paramLabel = "FILE", description = "PDF file; standard output if not given.") Path output,
      @Parameters(paramLabel = "FILE", description = "Order as JSON or Order-X XML; - for standard input.") Path file) {
    Order order = order(file);
    cli.io()
       .write(output,
              pdf == null ?
              cli.paniolo()
                 .toOrderXPdf(order,
                              layout.layout(cli.io()),
                              cli.attachments(attachments)) :
              cli.paniolo()
                 .toOrderXPdf(order,
                              cli.io()
                                 .read(pdf),
                              cli.attachments(attachments)));
    return PanioloCli.EXIT_OK;
  }

  private Order order(Path file) {
    byte[] content = cli.io()
                        .read(file);
    return Io.isJson(content) ?
           cli.json()
              .read(content,
                    Order.class) :
           cli.paniolo()
              .readOrder(content);
  }
}

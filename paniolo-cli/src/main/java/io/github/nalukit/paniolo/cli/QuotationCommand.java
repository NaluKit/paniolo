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

import io.github.nalukit.paniolo.model.quotation.Quotation;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Quotations (German "Angebot"): rendered as PDF and converted to an invoice. A quotation has no
 * XML syntax, so it is read from and written as Paniolo JSON only.
 */
@Command(name = "quotation", mixinStandardHelpOptions = true, description = "Renders quotations as PDF and creates invoices from them. A quotation is stored as Paniolo JSON; it has no XML syntax.")
final class QuotationCommand {
  @ParentCommand
  PanioloCli cli;

  @Command(name = "to-pdf", description = { "Renders a quotation as visual PDF/A-3b document without embedded XML.",
                                            "The quotation shows the lines of the standard offer with totals, the optional lines apart from them, and no payment instruction." })
  int toPdf(
      @Mixin PanioloCli.LayoutOptions layout,
      @Option(names = { "-o",
                        "--output" }, paramLabel = "FILE", description = "PDF file; standard output if not given.") Path output,
      @Parameters(paramLabel = "FILE", description = "Quotation as JSON; - for standard input.") Path file) {
    cli.io()
       .write(output,
              cli.paniolo()
                 .toPdf(quotation(file),
                        layout.layout(cli.io())));
    return PanioloCli.EXIT_OK;
  }

  @Command(name = "to-invoice", description = { "Creates the invoice of the standard offer and writes it as JSON.",
                                                "Add payment instruction and due date to the invoice, then use to-xml or to-facturx with it." })
  int toInvoice(
      @Option(names = "--number", required = true, paramLabel = "NUMBER", description = "Number of the new invoice.") String number,
      @Option(names = "--date", paramLabel = "DATE", description = "Issue date of the new invoice, yyyy-mm-dd; default: today.") String date,
      @Option(names = "--accept", paramLabel = "ID", description = "Identifier of an optional line the buyer accepted; repeatable.") List<String> accepted,
      @Option(names = { "-o",
                        "--output" }, paramLabel = "FILE", description = "JSON file; standard output if not given.") Path output,
      @Parameters(paramLabel = "FILE", description = "Quotation as JSON; - for standard input.") Path file) {
    cli.io()
       .write(output,
              cli.json()
                 .toPrettyJson(quotation(file).toInvoice(number,
                                                         issueDate(date),
                                                         accepted == null ?
                                                         List.of() :
                                                         accepted)));
    return PanioloCli.EXIT_OK;
  }

  private static LocalDate issueDate(String date) {
    if (date == null) {
      return LocalDate.now();
    }
    try {
      return LocalDate.parse(date);
    } catch (DateTimeParseException e) {
      throw new IllegalArgumentException("--date must be a date as yyyy-mm-dd: " + date,
                                         e);
    }
  }

  private Quotation quotation(Path file) {
    return cli.json()
              .read(cli.io()
                       .read(file),
                    Quotation.class);
  }
}

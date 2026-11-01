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
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.nalukit.paniolo.Paniolo;
import io.github.nalukit.paniolo.config.FacturXProfile;
import io.github.nalukit.paniolo.config.PdfAttachment;
import io.github.nalukit.paniolo.validation.ValidationResult;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;

/**
 * Inventory of a document stock: format, profile, specification identifier, document type code,
 * and for PDFs the producing software of every file, with counts per format, profile, and
 * producer. With {@code --validate} also the validity. Meant to plan a migration or to find
 * unexpected documents, e.g. before switching an archive or an inbound channel to Paniolo.
 *
 * <p>The inventory reads only the root element, the specification identifier, and the type code;
 * it does not read the documents into the model. Files that are no supported document are listed
 * as {@code UNKNOWN} or {@code MALFORMED}, PDFs without document XML as {@code PDF}.
 *
 * <p>Exit code: {@code 3} if at least one file could not be read, otherwise {@code 0}; invalid
 * documents do not change it (use {@code validate --expect} for that).
 */
@Command(name = "inventory",
         mixinStandardHelpOptions = true,
         description = { "Lists format, profile, specification identifier, type code, and for PDFs the producing software of documents, " + "with counts per format, profile, and producer.",
                         "Directories are expanded like for validate. With --validate, the documents are validated as well." })
final class InventoryCommand
    implements Callable<Integer> {
  private static final ObjectMapper JSON = new ObjectMapper();

  private static final Pattern XRECHNUNG_VERSION = Pattern.compile("xrechnung_(\\d+\\.\\d+)");

  @ParentCommand
  PanioloCli cli;

  @Spec
  CommandSpec spec;

  @Mixin
  FileSelection selection;

  @Option(names = { "-f",
                    "--format" }, defaultValue = "text", description = "Output format: ${COMPLETION-CANDIDATES} (default: ${DEFAULT-VALUE}). json writes one object per line and a last object with \"summary\": true.")
  Format format;

  @Option(names = "--validate", description = "Also validate every invoice, Order-X order, and UBL despatch advice; counts valid, invalid, and not validated documents.")
  boolean validate;

  @Option(names = "--summary-only", description = "Print the summary only, not one entry per file.")
  boolean summaryOnly;

  @Option(names = { "-o",
                    "--output" }, paramLabel = "FILE", description = "Write the inventory to FILE instead of standard output.")
  Path output;

  @Parameters(paramLabel = "FILE", arity = "1..*", description = "Documents (XML or PDF) or directories; - for standard input.")
  List<Path> files;

  /**
   * Lists the files and prints the summary.
   */
  @Override
  public Integer call() {
    var inputs = selection.inputs(files);
    if (inputs.isEmpty()) {
      throw new ParameterException(spec.commandLine(),
                                   "No files in " + files);
    }
    var report    = new StringBuilder();
    var formats   = new TreeMap<String, Integer>();
    var profiles  = new TreeMap<String, Integer>();
    var producers = new TreeMap<String, Integer>();
    int unread    = 0;
    int valid     = 0;
    int invalid   = 0;
    int skipped   = 0;
    for (FileSelection.Input input : inputs) {
      String error = input.error();
      Entry  entry = null;
      if (error == null) {
        try {
          entry = entry(cli.io()
                           .read(input.path()));
        } catch (RuntimeException e) {
          error = message(e);
        }
      }
      if (entry == null) {
        unread++;
        spec.commandLine()
            .getErr()
            .println("paniolo: " + (error.contains(input.path()
                                                     .toString()) ?
                                    error :
                                    input.path() + ": " + error));
        if (!summaryOnly) {
          if (format == Format.json) {
            ObjectNode node = JSON.createObjectNode();
            node.put("file",
                     input.path()
                          .toString());
            node.put("error",
                     error);
            report.append(node)
                  .append('\n');
          } else {
            report.append(input.path())
                  .append(": error (")
                  .append(error)
                  .append(")\n");
          }
        }
        continue;
      }
      count(formats,
            entry.format());
      count(profiles,
            entry.profile() == null ?
            "none" :
            entry.profile());
      if (entry.producer() != null) {
        count(producers,
              entry.producer());
      }
      if (validate) {
        if (entry.valid() == null) {
          skipped++;
        } else if (entry.valid()) {
          valid++;
        } else {
          invalid++;
        }
      }
      if (!summaryOnly) {
        if (format == Format.json) {
          report.append(json(input.path(),
                             entry))
                .append('\n');
        } else {
          text(report,
               input.path(),
               entry);
        }
      }
    }
    int total = inputs.size();
    if (format == Format.json) {
      ObjectNode node = JSON.createObjectNode();
      node.put("summary",
               true);
      node.put("files",
               total);
      node.put("unreadable",
               unread);
      node.set("formats",
               JSON.valueToTree(formats));
      node.set("profiles",
               JSON.valueToTree(profiles));
      node.set("producers",
               JSON.valueToTree(producers));
      if (validate) {
        node.put("valid",
                 valid);
        node.put("invalid",
                 invalid);
        node.put("notValidated",
                 skipped);
      }
      report.append(node)
            .append('\n');
    } else {
      report.append("Summary: ")
            .append(total)
            .append(" files, ")
            .append(unread)
            .append(" not readable\n");
      counts(report,
             "Formats",
             formats);
      counts(report,
             "Profiles",
             profiles);
      counts(report,
             "Producers",
             producers);
      if (validate) {
        report.append("  Validation: ")
              .append(valid)
              .append(" valid, ")
              .append(invalid)
              .append(" invalid, ")
              .append(skipped)
              .append(" not validated\n");
      }
    }
    cli.io()
       .write(output,
              report.toString());
    return unread > 0 ?
           PanioloCli.EXIT_ERROR :
           PanioloCli.EXIT_OK;
  }

  /**
   * Describes one document; a PDF by the XML it embeds.
   */
  private Entry entry(byte[] content) {
    if (!Paniolo.isPdf(content)) {
      var xml = classify(content);
      return xml.withValid(validity(content,
                                    xml));
    }
    Paniolo paniolo  = cli.paniolo();
    var     producer = paniolo.producer(content);
    String  name     = producer.generator() == null ?
                       "unknown" :
                       producer.generator();
    Entry   embedded = embedded(paniolo,
                                content);
    if (embedded == null) {
      return new Entry("PDF",
                       null,
                       null,
                       null,
                       name,
                       producer.describe(),
                       null,
                       null);
    }
    return new Entry("PDF/" + embedded.format(),
                     embedded.profile(),
                     embedded.specification(),
                     embedded.typeCode(),
                     name,
                     producer.describe(),
                     validity(content,
                              embedded),
                     null);
  }

  /**
   * The document XML of a PDF: the invoice XML, otherwise the first embedded XML file of a known
   * format, e.g. the Order-X XML; or null.
   */
  private static Entry embedded(Paniolo paniolo,
                                byte[] pdf) {
    try {
      return classify(paniolo.extractXml(pdf));
    } catch (IllegalArgumentException e) {
      // No invoice: look at the other embedded files.
    }
    for (PdfAttachment attachment : paniolo.attachments(pdf)) {
      if (attachment.fileName()
                    .toLowerCase(Locale.ROOT)
                    .endsWith(".xml")) {
        var entry = classify(attachment.content());
        if (!entry.format()
                  .equals("UNKNOWN") && !entry.format()
                                              .equals("MALFORMED")) {
          return entry;
        }
      }
    }
    return null;
  }

  /**
   * Validates a supported document if --validate is given; null if not validated.
   */
  private Boolean validity(byte[] content,
                           Entry entry) {
    if (!validate) {
      return null;
    }
    String kind = entry.format();
    if (kind.equals("UNKNOWN") || kind.equals("MALFORMED") || kind.equals("CIDA")) {
      return null;
    }
    try {
      ValidationResult result = cli.validateAny(content);
      return result.isValid();
    } catch (RuntimeException e) {
      return null;
    }
  }

  /**
   * Reads the root element, the specification identifier, and the type code of document XML.
   */
  static Entry classify(byte[] xml) {
    var factory = XMLInputFactory.newFactory();
    factory.setProperty(XMLInputFactory.SUPPORT_DTD,
                        false);
    factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES,
                        false);
    try {
      XMLStreamReader reader = factory.createXMLStreamReader(new ByteArrayInputStream(xml));
      try {
        String root          = null;
        String namespace     = null;
        String specification = null;
        String typeCode      = null;
        boolean context      = false;
        boolean document     = false;
        while (reader.hasNext()) {
          if (reader.next() != XMLStreamConstants.START_ELEMENT) {
            continue;
          }
          String name = reader.getLocalName();
          if (root == null) {
            root      = name;
            namespace = reader.getNamespaceURI();
            continue;
          }
          switch (name) {
            // CII, ZUGFeRD 1.0, Order-X, CIDA: ExchangedDocumentContext/GuidelineSpecifiedDocumentContextParameter/ID
            case "GuidelineSpecifiedDocumentContextParameter" -> context = true;
            case "ExchangedDocument", "HeaderExchangedDocument" -> document = true;
            case "ID" -> {
              if (context && specification == null) {
                specification = reader.getElementText()
                                      .strip();
                context       = false;
              }
            }
            case "TypeCode" -> {
              if (document && typeCode == null) {
                typeCode = reader.getElementText()
                                 .strip();
              }
            }
            // UBL
            case "CustomizationID" -> {
              if (specification == null) {
                specification = reader.getElementText()
                                      .strip();
              }
            }
            case "InvoiceTypeCode", "CreditNoteTypeCode", "DespatchAdviceTypeCode" -> {
              if (typeCode == null) {
                typeCode = reader.getElementText()
                                 .strip();
              }
            }
            default -> {
            }
          }
          if (specification != null && typeCode != null) {
            break;
          }
        }
        String kind = format(root,
                             namespace);
        return new Entry(kind,
                         profile(kind,
                                 specification),
                         specification,
                         typeCode,
                         null,
                         null,
                         null,
                         kind.equals("UNKNOWN") ?
                         "root element " + (namespace == null || namespace.isEmpty() ?
                                            "" :
                                            "{" + namespace + "}") + root :
                         null);
      } finally {
        reader.close();
      }
    } catch (XMLStreamException e) {
      return new Entry("MALFORMED",
                       null,
                       null,
                       null,
                       null,
                       null,
                       null,
                       // One line per file, also for the multi-line messages of the parser.
                       e.getMessage()
                        .replaceAll("\\s+",
                                    " "));
    }
  }

  private static String format(String root,
                               String namespace) {
    if (root == null) {
      return "MALFORMED";
    }
    String ns = namespace == null ?
                "" :
                namespace;
    return switch (root) {
      case "CrossIndustryInvoice" -> "CII";
      case "CrossIndustryDocument" -> "ZUGFERD_1";
      case "SCRDMCCBDACIOMessageStructure" -> "ORDER_X";
      case "SCRDMCCBDACIDAMessageStructure" -> "CIDA";
      case "Invoice" -> ns.startsWith("urn:oasis:names:specification:ubl:schema:xsd:Invoice-2") ?
                        "UBL_INVOICE" :
                        "UNKNOWN";
      case "CreditNote" -> ns.startsWith("urn:oasis:names:specification:ubl:schema:xsd:CreditNote-2") ?
                           "UBL_CREDIT_NOTE" :
                           "UNKNOWN";
      case "DespatchAdvice" -> ns.startsWith("urn:oasis:names:specification:ubl:schema:xsd:DespatchAdvice-2") ?
                               "UBL_DESPATCH_ADVICE" :
                               "UNKNOWN";
      default -> "UNKNOWN";
    };
  }

  /**
   * The profile of a specification identifier: the Factur-X profile, XRechnung with its version,
   * or for ZUGFeRD 1.0 and Order-X the last part of the identifier, e.g. COMFORT; null if unknown.
   */
  static String profile(String format,
                        String specification) {
    if (specification == null) {
      return null;
    }
    var version = XRECHNUNG_VERSION.matcher(specification);
    if (specification.contains(":kosit:") && version.find()) {
      return "XRECHNUNG " + version.group(1);
    }
    if (format.equals("CII") || format.startsWith("UBL_INVOICE") || format.startsWith("UBL_CREDIT_NOTE")) {
      FacturXProfile profile = FacturXProfile.ofSpecificationId(specification);
      if (profile != null) {
        return profile.name();
      }
      if (specification.contains("urn:fdc:peppol.eu:2017:poacc:billing:3.0")) {
        return "PEPPOL BIS 3";
      }
      return null;
    }
    if (format.equals("ZUGFERD_1") || format.equals("ORDER_X")) {
      String last = specification.substring(specification.lastIndexOf(':') + 1);
      return last.isEmpty() ?
             null :
             last.toUpperCase(Locale.ROOT);
    }
    return null;
  }

  private static void count(Map<String, Integer> counts,
                            String key) {
    counts.merge(key,
                 1,
                 Integer::sum);
  }

  private static void counts(StringBuilder report,
                             String label,
                             Map<String, Integer> counts) {
    if (counts.isEmpty()) {
      return;
    }
    report.append("  ")
          .append(label)
          .append(": ");
    var first = true;
    for (var count : counts.entrySet()) {
      if (!first) {
        report.append(", ");
      }
      report.append(count.getKey())
            .append(' ')
            .append(count.getValue());
      first = false;
    }
    report.append('\n');
  }

  private void text(StringBuilder report,
                    Path file,
                    Entry entry) {
    report.append(file)
          .append(": ")
          .append(entry.format());
    if (entry.profile() != null) {
      report.append(", ")
            .append(entry.profile());
    }
    if (entry.specification() != null) {
      report.append(" (")
            .append(entry.specification())
            .append(')');
    }
    if (entry.typeCode() != null) {
      report.append(", type ")
            .append(entry.typeCode());
    }
    if (entry.producerDetails() != null) {
      report.append(", producer ")
            .append(entry.producerDetails());
    }
    if (validate) {
      report.append(entry.valid() == null ?
                    ", not validated" :
                    entry.valid() ?
                    ", valid" :
                    ", invalid");
    }
    if (entry.note() != null) {
      report.append(", ")
            .append(entry.note());
    }
    report.append('\n');
  }

  private String json(Path file,
                      Entry entry) {
    ObjectNode node = JSON.createObjectNode();
    node.put("file",
             file.toString());
    node.put("format",
             entry.format());
    node.put("profile",
             entry.profile());
    node.put("specification",
             entry.specification());
    node.put("typeCode",
             entry.typeCode());
    if (entry.producer() != null) {
      node.put("producer",
               entry.producer());
      node.put("producerDetails",
               entry.producerDetails());
    }
    if (validate) {
      node.put("valid",
               entry.valid());
    }
    if (entry.note() != null) {
      node.put("note",
               entry.note());
    }
    return node.toString();
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

  /**
   * What the inventory knows about one document.
   *
   * @param format          e.g. CII, UBL_INVOICE, ZUGFERD_1, ORDER_X, PDF/CII, PDF, UNKNOWN, MALFORMED
   * @param profile         e.g. EN16931, XRECHNUNG 3.0, COMFORT, or null
   * @param specification   specification identifier (BT-24 / CustomizationID), or null
   * @param typeCode        document type code, e.g. 380, or null
   * @param producer        producing software of a PDF as recognized, or "unknown"; null for XML
   * @param producerDetails producing software with producer and creator of a PDF, or null
   * @param valid           validity with --validate, or null if not validated
   * @param note            reason for UNKNOWN or MALFORMED, or null
   */
  record Entry(String format, String profile, String specification, String typeCode, String producer, String producerDetails, Boolean valid, String note) {
    Entry withValid(Boolean valid) {
      return new Entry(format,
                       profile,
                       specification,
                       typeCode,
                       producer,
                       producerDetails,
                       valid,
                       note);
    }
  }

  enum Format {
    text,
    json
  }
}

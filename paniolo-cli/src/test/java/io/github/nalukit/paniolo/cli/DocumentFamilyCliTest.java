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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The document family is chosen by the root element (with its namespace) of the XML, or of the main
 * document of a PDF: in the inventory and for the validator of {@code visualize}.
 */
class DocumentFamilyCliTest {
  @TempDir
  static Path dir;

  static Path orderJson;
  static Path adviceJson;
  static Path quotationJson;
  static Path visualPdf;
  static Path xrechnung;

  record Result(int exitCode, String out, String err) {
  }

  @BeforeAll
  static void samples()
      throws IOException {
    orderJson  = copy("order.json");
    adviceJson = copy("despatch-advice.json");
    quotationJson = copy("quotation.json");
    visualPdf  = copy("visual.pdf");
    xrechnung  = copy("xrechnung.xml");
  }

  private static Path copy(String name)
      throws IOException {
    Path target = dir.resolve(name);
    try (InputStream input = DocumentFamilyCliTest.class.getResourceAsStream("/samples/" + name)) {
      Files.write(target,
                  input.readAllBytes());
    }
    return target;
  }

  private static Result run(String... args) {
    var out = new ByteArrayOutputStream();
    var err = new ByteArrayOutputStream();
    int exitCode = PanioloCli.execute(new ByteArrayInputStream(new byte[0]),
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

  private static Path pdf(String command,
                          Path json,
                          String name) {
    Path target = dir.resolve(name);
    var result = run(command,
                     "to-pdf",
                     json.toString(),
                     "--pdf",
                     visualPdf.toString(),
                     "-o",
                     target.toString());
    assertEquals(PanioloCli.EXIT_OK,
                 result.exitCode(),
                 result.err());
    return target;
  }

  @Test
  void inventoryRecognizesOrderXAndDespatchAdvicePdfs()
      throws IOException {
    Path stock = Files.createDirectories(dir.resolve("pdf-stock"));
    Files.copy(pdf("order",
                   orderJson,
                   "order.pdf"),
               stock.resolve("order.pdf"));
    Files.copy(pdf("despatch-advice",
                   adviceJson,
                   "advice.pdf"),
               stock.resolve("advice.pdf"));
    Files.copy(visualPdf,
               stock.resolve("plain.pdf"));
    var result = run("inventory",
                     stock.toString(),
                     "--validate");
    assertEquals(PanioloCli.EXIT_OK,
                 result.exitCode(),
                 result.err());
    String out = result.out();
    assertTrue(out.contains("order.pdf: PDF/ORDER_X, BASIC"),
               out);
    assertTrue(out.contains("advice.pdf: PDF/CIDA"),
               out);
    assertTrue(out.lines()
                  .anyMatch(line -> line.contains("order.pdf") && line.contains(", valid")),
               out);
    assertTrue(out.lines()
                  .anyMatch(line -> line.contains("advice.pdf") && line.contains("not validated")),
               out);
    assertTrue(out.contains("plain.pdf: PDF, producer"),
               out);
    assertTrue(out.contains("Validation: 1 valid, 0 invalid, 2 not validated"),
               out);
  }

  @Test
  void inventoryChecksTheNamespaceOfTheRootElement()
      throws IOException {
    Path stock = Files.createDirectories(dir.resolve("foreign"));
    int count = 0;
    for (String root : new String[] {"CrossIndustryInvoice", "CrossIndustryDocument", "SCRDMCCBDACIOMessageStructure", "SCRDMCCBDACIDAMessageStructure", "Invoice", "CreditNote", "DespatchAdvice"}) {
      Files.writeString(stock.resolve("foreign" + count++ + ".xml"),
                        "<" + root + " xmlns=\"urn:example:unrelated\"/>");
    }
    var result = run("inventory",
                     stock.toString());
    assertEquals(PanioloCli.EXIT_OK,
                 result.exitCode(),
                 result.err());
    assertTrue(result.out()
                     .contains("Formats: UNKNOWN 7"),
               result.out());
    // The supported combinations stay known.
    assertEquals("CII",
                 InventoryCommand.classify("<rsm:CrossIndustryInvoice xmlns:rsm=\"urn:un:unece:uncefact:data:standard:CrossIndustryInvoice:100\"/>".getBytes(StandardCharsets.UTF_8))
                                 .format());
    assertEquals("ZUGFERD_1",
                 InventoryCommand.classify("<rsm:CrossIndustryDocument xmlns:rsm=\"urn:ferd:CrossIndustryDocument:invoice:1p0\"/>".getBytes(StandardCharsets.UTF_8))
                                 .format());
    assertEquals("ORDER_X",
                 InventoryCommand.classify("<rsm:SCRDMCCBDACIOMessageStructure xmlns:rsm=\"urn:un:unece:uncefact:data:SCRDMCCBDACIOMessageStructure:100\"/>".getBytes(StandardCharsets.UTF_8))
                                 .format());
    assertEquals("CIDA",
                 InventoryCommand.classify("<rsm:SCRDMCCBDACIDAMessageStructure xmlns:rsm=\"urn:un:unece:uncefact:data:standard:SCRDMCCBDACIDAMessageStructure:1\"/>".getBytes(StandardCharsets.UTF_8))
                                 .format());
    assertEquals("UBL_INVOICE",
                 InventoryCommand.classify("<Invoice xmlns=\"urn:oasis:names:specification:ubl:schema:xsd:Invoice-2\"/>".getBytes(StandardCharsets.UTF_8))
                                 .format());
  }

  @Test
  void visualizeChoosesTheValidatorByStructure()
      throws IOException {
    String text = Files.readString(xrechnung);
    // A comment that names another family does not change the validator.
    Path commented = dir.resolve("commented.xml");
    Files.writeString(commented,
                      text.replaceFirst("(<rsm:CrossIndustryInvoice[^>]*>)",
                                        "$1<!-- SCRDMCCBDACIOMessageStructure DespatchAdvice-2 -->"));
    assertEquals(PanioloCli.EXIT_OK,
                 run("validate",
                     commented.toString()).exitCode());
    for (Path file : new Path[] {xrechnung, commented}) {
      var result = run("visualize",
                       "--validate",
                       "-o",
                       dir.resolve("out.html")
                          .toString(),
                       file.toString());
      assertEquals(PanioloCli.EXIT_OK,
                   result.exitCode(),
                   file + " " + result.err());
    }

    // Order-X in UTF-16LE and as PDF, and a despatch advice PDF reach the right validator.
    Path orderXml = dir.resolve("order-x.xml");
    assertEquals(PanioloCli.EXIT_OK,
                 run("order",
                     "to-xml",
                     orderJson.toString(),
                     "-o",
                     orderXml.toString()).exitCode());
    Path utf16 = dir.resolve("order-utf16.xml");
    var out = new ByteArrayOutputStream();
    out.writeBytes(new byte[] {(byte) 0xFF, (byte) 0xFE});
    out.writeBytes(Files.readString(orderXml)
                        .replace("encoding=\"UTF-8\"",
                                 "encoding=\"UTF-16\"")
                        .getBytes(StandardCharsets.UTF_16LE));
    Files.write(utf16,
                out.toByteArray());
    for (Path file : new Path[] {orderXml, utf16, dir.resolve("order.pdf") }) {
      if (!Files.exists(file)) {
        pdf("order",
            orderJson,
            "order.pdf");
      }
      var result = run("visualize",
                       "--validate",
                       "-o",
                       dir.resolve("order.html")
                          .toString(),
                       file.toString());
      assertEquals(PanioloCli.EXIT_OK,
                   result.exitCode(),
                   file + " " + result.err());
      assertTrue(Files.readString(dir.resolve("order.html"))
                      .contains("id=\"paniolo-report\""));
    }
    var advice = run("visualize",
                     "--validate",
                     "-o",
                     dir.resolve("advice.html")
                        .toString(),
                     pdf("despatch-advice",
                         adviceJson,
                         "advice2.pdf").toString());
    assertFalse(advice.exitCode() == PanioloCli.EXIT_OK && advice.err()
                                                                  .contains("Unsupported"),
                advice.err());
  }

  @Test
  void rendersAQuotationAsPdf()
      throws IOException {
    Path pdf = dir.resolve("quotation.pdf");
    var result = run("quotation",
                     "to-pdf",
                     quotationJson.toString(),
                     "-o",
                     pdf.toString());
    assertEquals(PanioloCli.EXIT_OK,
                 result.exitCode(),
                 result.err());
    assertTrue(Files.size(pdf) > 1000);
    assertTrue(new String(Files.readAllBytes(pdf),
                          StandardCharsets.ISO_8859_1).startsWith("%PDF"));
  }

  @Test
  void createsTheInvoiceOfAQuotation()
      throws IOException {
    var standard = run("quotation",
                       "to-invoice",
                       "--number",
                       "R-77",
                       "--date",
                       "2026-12-01",
                       quotationJson.toString());
    assertEquals(PanioloCli.EXIT_OK,
                 standard.exitCode(),
                 standard.err());
    assertTrue(standard.out()
                       .contains("\"number\" : \"R-77\""),
               standard.out());
    assertTrue(standard.out()
                       .contains("\"issueDate\" : \"2026-12-01\""),
               standard.out());
    assertTrue(standard.out()
                       .contains("Beratung"));
    assertFalse(standard.out()
                        .contains("Schulung"),
                "the optional line is not part of the standard offer");

    var accepted = run("quotation",
                       "to-invoice",
                       "--number",
                       "R-78",
                       "--accept",
                       "2",
                       quotationJson.toString());
    assertEquals(PanioloCli.EXIT_OK,
                 accepted.exitCode(),
                 accepted.err());
    assertTrue(accepted.out()
                       .contains("Schulung"));

    // The invoice JSON goes on with the invoice commands.
    Path invoice = dir.resolve("from-quotation.json");
    Files.writeString(invoice,
                      accepted.out());
    assertEquals(PanioloCli.EXIT_OK,
                 run("to-pdf",
                     invoice.toString(),
                     "-o",
                     dir.resolve("from-quotation.pdf")
                        .toString()).exitCode());
  }

  @Test
  void rejectsInvalidQuotationArguments() {
    assertEquals(PanioloCli.EXIT_ERROR,
                 run("quotation",
                     "to-invoice",
                     "--number",
                     "R-79",
                     "--accept",
                     "1",
                     quotationJson.toString()).exitCode(),
                 "line 1 is not optional");
    assertEquals(PanioloCli.EXIT_ERROR,
                 run("quotation",
                     "to-invoice",
                     "--number",
                     "R-79",
                     "--date",
                     "01.12.2026",
                     quotationJson.toString()).exitCode());
  }

  private static InventoryCommand.Entry classify(String xml) {
    return InventoryCommand.classify(xml.getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void inventoryReadsTheMetadataFromTheirPlaceInTheDocument()
      throws IOException {
    String ubl = Files.readString(copy("line-net-amount.xrechnung-ubl.xml"));
    var plain = classify(ubl);
    assertEquals("UBL_INVOICE",
                 plain.format());
    assertEquals("XRECHNUNG 3.0",
                 plain.profile());
    assertEquals("380",
                 plain.typeCode());
    String extension = "<ext:UBLExtensions xmlns:ext=\"urn:oasis:names:specification:ubl:schema:xsd:CommonExtensionComponents-2\">" +
                       "<ext:UBLExtension><ext:ExtensionContent><a:Audit xmlns:a=\"urn:example:audit\">" +
                       "<a:CustomizationID>urn:cen.eu:en16931:2017</a:CustomizationID><a:InvoiceTypeCode>999</a:InvoiceTypeCode>" +
                       "</a:Audit></ext:ExtensionContent></ext:UBLExtension></ext:UBLExtensions>";
    // Extensions in a foreign namespace, and with the namespace of the basic components, before the real fields.
    String foreign = ubl.replaceFirst("(<Invoice[^>]*>)",
                                      "$1" + extension);
    String sameNamespace = ubl.replaceFirst("(<Invoice[^>]*>)",
                                            "$1<ext:UBLExtensions xmlns:ext=\"urn:example:ext\"><cbc:CustomizationID>urn:cen.eu:en16931:2017</cbc:CustomizationID>" +
                                            "<cbc:InvoiceTypeCode>999</cbc:InvoiceTypeCode></ext:UBLExtensions>");
    // A referenced document nested in the invoice, and other prefixes for the same namespaces.
    String nested = ubl.replaceFirst("(<cbc:CustomizationID>)",
                                     "<cac:AdditionalDocumentReference><cbc:CustomizationID>urn:cen.eu:en16931:2017</cbc:CustomizationID>" +
                                     "<cbc:InvoiceTypeCode>999</cbc:InvoiceTypeCode></cac:AdditionalDocumentReference>$1");
    String prefixes = ubl.replace("cbc:",
                                  "b:")
                         .replace("xmlns:cbc=",
                                  "xmlns:b=")
                         .replace("<Invoice ",
                                  "<u:Invoice ")
                         .replace("</Invoice>",
                                  "</u:Invoice>")
                         .replace("xmlns=\"urn:oasis:names:specification:ubl:schema:xsd:Invoice-2\"",
                                  "xmlns:u=\"urn:oasis:names:specification:ubl:schema:xsd:Invoice-2\"");
    for (String variant : new String[] {foreign, sameNamespace, nested, prefixes}) {
      var entry = classify(variant);
      assertEquals(plain.format(),
                   entry.format(),
                   variant);
      assertEquals(plain.profile(),
                   entry.profile());
      assertEquals(plain.specification(),
                   entry.specification());
      assertEquals("380",
                   entry.typeCode());
    }

    String cii = Files.readString(xrechnung);
    var ciiPlain = classify(cii);
    assertEquals("XRECHNUNG 3.0",
                 ciiPlain.profile());
    String trap = "<x:Extension xmlns:x=\"urn:example:audit\"><x:ExchangedDocumentContext><x:GuidelineSpecifiedDocumentContextParameter>" +
                  "<x:ID>urn:cen.eu:en16931:2017</x:ID></x:GuidelineSpecifiedDocumentContextParameter></x:ExchangedDocumentContext>" +
                  "<x:ExchangedDocument><x:TypeCode>999</x:TypeCode></x:ExchangedDocument></x:Extension>";
    var trapped = classify(cii.replaceFirst("(<rsm:CrossIndustryInvoice[^>]*>)",
                                            "$1" + trap));
    assertEquals(ciiPlain.specification(),
                 trapped.specification());
    assertEquals(ciiPlain.typeCode(),
                 trapped.typeCode());
    // A document nested deeper, e.g. in a line, does not state the type code either.
    var nestedType = classify(cii.replaceFirst("(<rsm:SupplyChainTradeTransaction>)",
                                               "$1<ram:Nested><rsm:ExchangedDocument><ram:TypeCode>999</ram:TypeCode></rsm:ExchangedDocument></ram:Nested>"));
    assertEquals(ciiPlain.typeCode(),
                 nestedType.typeCode());
  }
}

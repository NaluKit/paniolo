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
package io.github.nalukit.paniolo;

import io.github.nalukit.paniolo.config.InvoiceSyntax;
import io.github.nalukit.paniolo.model.Invoice;
import io.github.nalukit.paniolo.model.common.AdditionalSupportingDocument;
import io.github.nalukit.paniolo.model.common.Attachment;
import io.github.nalukit.paniolo.model.common.InvoiceLine;
import io.github.nalukit.paniolo.model.common.ProcessControl;
import io.github.nalukit.paniolo.model.common.TaxCategory;
import io.github.nalukit.paniolo.model.common.ThirdPartyPayment;
import io.github.nalukit.paniolo.model.common.value.InvoiceTypeCode;
import io.github.nalukit.paniolo.validation.Severity;
import io.github.nalukit.paniolo.validation.ValidationMessage;
import io.github.nalukit.paniolo.xml.ReadMode;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Writes, reads, and validates invoices of the XRechnung extension: sub invoice lines, third party
 * payments, and XML attachments in UBL, checked with the KoSIT extension scenario.
 */
class XrechnungExtensionTest {
  private static final Paniolo     PANIOLO = Paniolo.create();
  private static final TaxCategory VAT     = TaxCategory.standard(new BigDecimal("19"));

  private static InvoiceLine.Builder line(String id,
                                          String name,
                                          String quantity,
                                          String price) {
    return InvoiceLine.create()
                      .id(id)
                      .name(name)
                      .quantity(new BigDecimal(quantity))
                      .unitCode("H87")
                      .netUnitPrice(new BigDecimal(price))
                      .tax(VAT);
  }

  /**
   * A set of 300.00 with two sub-lines, one of them split again, a third party payment, and an XML
   * attachment.
   */
  static Invoice.Builder invoice() {

    var set = line("2",
                   "Montageset",
                   "1",
                   "300.00").addSubLine(line("2.1",
                                             "Gerät",
                                             "1",
                                             "200.00").addSubLine(line("2.1.1",
                                                                       "Gehäuse",
                                                                       "1",
                                                                       "120.00").build())
                                                      .addSubLine(line("2.1.2",
                                                                       "Netzteil",
                                                                       "2",
                                                                       "40.00").build())
                                                      .build())
                            .addSubLine(line("2.2",
                                             "Montage",
                                             "2",
                                             "50.00").build())
                            .build();
    return TestInvoices.builder(InvoiceTypeCode.CODE_380,
                                "X-1")
                       .processControl(ProcessControl.xrechnungExtension())
                       .addLine(set)
                       .addThirdPartyPayment(new ThirdPartyPayment("Kurtaxe",
                                                                   new BigDecimal("12.50"),
                                                                   "Kurtaxe für die Gemeinde"))
                       .addSupportingDocument(new AdditionalSupportingDocument("Aufmass",
                                                                               "Aufmaß als XML",
                                                                               null,
                                                                               new Attachment("<aufmass/>".getBytes(StandardCharsets.UTF_8),
                                                                                              "application/xml",
                                                                                              "aufmass.xml")));
  }

  @Test
  void writesReadsAndValidatesTheExtension() {
    var invoice = invoice().build();
    assertTrue(invoice.hasXrechnungExtensionInformation());
    // 200.00 + 300.00 + 19 % VAT, plus the third party payment (BR-DEX-09).
    assertEquals(new BigDecimal("607.50"),
                 invoice.totals()
                        .payableAmount());
    String xml = PANIOLO.toXml(invoice,
                               InvoiceSyntax.UBL);
    for (String expected : List.of("<cbc:CustomizationID>" + ProcessControl.XRECHNUNG_3_EXTENSION + "</cbc:CustomizationID>",
                                   "<cac:SubInvoiceLine>",
                                   "<cbc:ID>2.1.2</cbc:ID>",
                                   "<cac:PrepaidPayment>",
                                   "<cbc:PaidAmount currencyID=\"EUR\">12.50</cbc:PaidAmount>",
                                   "<cbc:InstructionID>Kurtaxe für die Gemeinde</cbc:InstructionID>",
                                   "<cbc:PayableAmount currencyID=\"EUR\">607.50</cbc:PayableAmount>",
                                   "mimeCode=\"application/xml\"")) {
      assertTrue(xml.contains(expected),
                 () -> expected + " in " + xml);
    }
    var result = PANIOLO.validate(xml);
    assertTrue(result.isValid(),
               () -> result.messages()
                           .toString());
    assertEquals("EN16931 XRechnung Extension (UBL Invoice)",
                 result.profileName());
    // The extension scenario turns BR-CO-16 into information for the third party payment.
    assertFalse(result.messages()
                      .stream()
                      .anyMatch(message -> message.severity() == Severity.ERROR || message.severity() == Severity.FATAL),
                () -> result.messages()
                            .toString());
    assertEquals(invoice,
                 PANIOLO.read(xml));
  }

  /**
   * KoSIT test case 04.04a: the sub-line 1.02 groups four sub-lines and has no price of its own,
   * which BR-26 allows for sub invoice lines.
   */
  @Test
  void readsSubLinesWithoutPrice() throws Exception {
    byte[] original;
    try (InputStream input = XrechnungExtensionTest.class.getResourceAsStream("/regression/04.04a-INVOICE_ubl.xml")) {
      original = input.readAllBytes();
    }
    assertEquals("275929283c921191be88f9c5fcd947432d62e57a14f692d8b9003d6debb37237",
                 HexFormat.of()
                          .formatHex(MessageDigest.getInstance("SHA-256")
                                                  .digest(original)));
    for (var mode : ReadMode.values()) {
      var invoice = PANIOLO.read(original,
                                 mode)
                           .invoice();
      var line  = invoice.lines()
                         .getFirst();
      var group = line.subLines()
                      .getFirst();
      assertEquals("1.02",
                   group.id());
      assertNull(group.netUnitPrice());
      assertEquals(new BigDecimal("3508.77"),
                   group.netAmount());
      assertEquals(new BigDecimal("3508.77"),
                   line.netAmount());
      assertEquals(new BigDecimal("7.30"),
                   group.subLines()
                        .getFirst()
                        .netUnitPrice());
      String xml = PANIOLO.toXml(invoice,
                                 InvoiceSyntax.UBL);
      var result = PANIOLO.validate(xml);
      assertTrue(result.isValid(),
                 () -> result.messages()
                             .toString());
      assertEquals(invoice,
                   PANIOLO.read(xml));
    }
    // BR-26: a line of the invoice itself still needs its price.
    var withoutPrice = line("9",
                            "Set",
                            "1",
                            "300.00").netUnitPrice(null)
                                     .addSubLine(line("9.1",
                                                      "Teil",
                                                      "1",
                                                      "300.00").build())
                                     .build();
    assertThrows(NullPointerException.class,
                 () -> TestInvoices.builder(InvoiceTypeCode.CODE_380,
                                            "X-3")
                                   .processControl(ProcessControl.xrechnungExtension())
                                   .addLine(withoutPrice)
                                   .build());
    assertThrows(NullPointerException.class,
                 () -> line("2",
                            "Teil",
                            "1",
                            "1.00").netUnitPrice(null)
                                   .build());
  }

  @Test
  void reportsSubLinesThatDoNotAddUp() {
    String xml = PANIOLO.toXml(invoice().build(),
                               InvoiceSyntax.UBL);
    // The model rejects such sub-lines; changed XML is reported by the KoSIT rules (BR-DEX-02).
    String broken = xml.replace("<cbc:LineExtensionAmount currencyID=\"EUR\">100.00</cbc:LineExtensionAmount>",
                                "<cbc:LineExtensionAmount currencyID=\"EUR\">90.00</cbc:LineExtensionAmount>");
    assertFalse(broken.equals(xml));
    var result = PANIOLO.validate(broken);
    assertTrue(result.messages()
                     .stream()
                     .map(ValidationMessage::ruleId)
                     .anyMatch("BR-DEX-02"::equals),
               () -> result.messages()
                           .toString());
    var model = assertThrows(IllegalArgumentException.class,
                             () -> line("3",
                                        "Set",
                                        "1",
                                        "100.00").addSubLine(line("3.1",
                                                                  "Teil",
                                                                  "1",
                                                                  "90.00").build())
                                                 .build());
    assertTrue(model.getMessage()
                    .contains("BR-DEX-02"),
               model::getMessage);
  }

  @Test
  void rejectsTheExtensionOutsideItsSpecification() {
    var xrechnung = invoice().processControl(ProcessControl.xrechnung())
                             .build();
    var plain = assertThrows(IllegalArgumentException.class,
                             () -> PANIOLO.toXml(xrechnung,
                                                 InvoiceSyntax.UBL));
    assertTrue(plain.getMessage()
                    .contains("xrechnungExtension()"),
               plain::getMessage);
    // KoSIT defines the extension for UBL Invoice only; CII and credit notes reject it.
    assertThrows(IllegalArgumentException.class,
                 () -> PANIOLO.toXml(invoice().build(),
                                     InvoiceSyntax.CII));
    assertThrows(IllegalArgumentException.class,
                 () -> PANIOLO.toXml(invoice().typeCode(InvoiceTypeCode.CODE_381)
                                              .build(),
                                     InvoiceSyntax.UBL));
    assertThrows(IllegalArgumentException.class,
                 () -> new ThirdPartyPayment(" ",
                                             BigDecimal.ONE,
                                             "Beschreibung"));
    // Without extension content the extension identifier is still a valid XRechnung.
    var simple = TestInvoices.builder(InvoiceTypeCode.CODE_380,
                                      "X-2")
                             .processControl(ProcessControl.xrechnungExtension())
                             .build();
    var result = PANIOLO.validate(PANIOLO.toXml(simple,
                                                InvoiceSyntax.CII));
    assertTrue(result.isValid(),
               () -> result.messages()
                           .toString());
    assertEquals("EN16931 XRechnung Extension (CII)",
                 result.profileName());
  }
}

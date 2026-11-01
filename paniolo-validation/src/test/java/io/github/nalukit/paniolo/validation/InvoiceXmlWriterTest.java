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
package io.github.nalukit.paniolo.validation;

import io.github.nalukit.paniolo.model.common.PrecedingInvoiceReference;
import io.github.nalukit.paniolo.model.common.ProcessControl;
import io.github.nalukit.paniolo.model.common.value.InvoiceTypeCode;
import io.github.nalukit.paniolo.xml.InvoiceXmlWriter;
import io.github.nalukit.paniolo.xml.cii.CiiInvoiceWriter;
import io.github.nalukit.paniolo.xml.ubl.UblInvoiceWriter;
import io.github.nalukit.paniolo.xml.util.XmlDocuments;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InvoiceXmlWriterTest {
  static Stream<InvoiceXmlWriter> writers() {
    return Stream.of(new UblInvoiceWriter(),
                     new CiiInvoiceWriter());
  }

  @ParameterizedTest
  @MethodSource("writers")
  void preservesUnicodeAndEscapesMarkup(InvoiceXmlWriter writer)
      throws Exception {
    var invoice = InvoiceSamples.invoice()
                                .addNote("Grüße <Kunde> & Danke")
                                .addLine(InvoiceSamples.line("1",
                                                             "100",
                                                             InvoiceSamples.standard()))
                                .build();
    var xml = writer.toXml(invoice);
    assertTrue(xml.contains("&lt;Kunde&gt; &amp; Danke"));
    var document = XmlDocuments.parse(new ByteArrayInputStream(writer.toBytes(invoice)));
    assertTrue(document.getDocumentElement()
                       .getTextContent()
                       .contains("Muster & Söhne GmbH"));
    assertTrue(document.getDocumentElement()
                       .getTextContent()
                       .contains("Beratung <XML> & Einführung"));
  }

  @ParameterizedTest
  @MethodSource("writers")
  void usesCorrectCreditNoteCode(InvoiceXmlWriter writer) {
    var invoice = InvoiceSamples.invoice()
                                .typeCode(InvoiceTypeCode.CODE_381)
                                .addLine(InvoiceSamples.line("1",
                                                             "100",
                                                             InvoiceSamples.standard()))
                                .build();
    var xml = writer.toXml(invoice);
    assertTrue(xml.contains(">381<"));
    if (writer instanceof UblInvoiceWriter) {
      assertTrue(xml.contains("<CreditNote "));
      assertTrue(xml.contains("<cbc:CreditedQuantity"));
      assertFalse(xml.contains("<cbc:DueDate>"));
    }
  }

  @ParameterizedTest
  @MethodSource("writers")
  void rejectsXmlControlCharacters(InvoiceXmlWriter writer) {
    var invoice = InvoiceSamples.invoice()
                                .addNote("Invalid: " + (char) 1)
                                .addLine(InvoiceSamples.line("1",
                                                             "100",
                                                             InvoiceSamples.standard()))
                                .build();
    assertThrows(IllegalArgumentException.class,
                 () -> writer.toXml(invoice));
  }

  @org.junit.jupiter.api.Test
  void ciiRejectsMoreThanOnePrecedingInvoice() {
    var invoice = InvoiceSamples.invoice()
                                .addPrecedingInvoiceReference(PrecedingInvoiceReference.create()
                                                                                       .number("R-1")
                                                                                       .build())
                                .addPrecedingInvoiceReference(PrecedingInvoiceReference.create()
                                                                                       .number("R-2")
                                                                                       .build())
                                .addLine(InvoiceSamples.line("1",
                                                             "100",
                                                             InvoiceSamples.standard()))
                                .build();
    assertThrows(IllegalArgumentException.class,
                 () -> new CiiInvoiceWriter().toXml(invoice));
    var xml = new UblInvoiceWriter().toXml(invoice);
    assertTrue(xml.contains("R-1") && xml.contains("R-2"));
  }

  @org.junit.jupiter.api.Test
  void ciiRejectsContentTheFacturXBasicProfileDoesNotAllow() {
    var invoice = InvoiceSamples.documentLevelTerms(InvoiceSamples.invoice())
                                .processControl(ProcessControl.facturXBasic())
                                .build();
    var error = assertThrows(IllegalArgumentException.class,
                             () -> new CiiInvoiceWriter().toXml(invoice));
    for (String expected : List.of("seller contact (BG-6)",
                                   "buyer contact (BG-9)",
                                   "project reference (BT-11)",
                                   "VAT point date (BT-7)",
                                   "rounding amount (BT-114)",
                                   "payment account name (BT-85)",
                                   "payment service provider identifier (BT-86)",
                                   "additional supporting documents (BG-24)")) {
      assertTrue(error.getMessage()
                      .contains(expected),
                 () -> expected + " missing in: " + error.getMessage());
    }
    // The same invoice is a valid EN 16931 document, and UBL has no BASIC profile.
    assertTrue(new CiiInvoiceWriter().toXml(InvoiceSamples.documentLevelTerms(InvoiceSamples.invoice())
                                                          .processControl(ProcessControl.en16931())
                                                          .build())
                                     .contains("DefinedTradeContact"));
    assertTrue(new UblInvoiceWriter().toXml(invoice)
                                     .contains("PRJ-1"));
  }
}

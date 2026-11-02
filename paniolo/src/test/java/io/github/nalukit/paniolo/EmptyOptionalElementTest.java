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

import io.github.nalukit.paniolo.config.FacturXProfile;
import io.github.nalukit.paniolo.config.InvoiceSyntax;
import io.github.nalukit.paniolo.model.Invoice;
import io.github.nalukit.paniolo.model.common.ProcessControl;
import io.github.nalukit.paniolo.model.common.value.InvoiceTypeCode;
import io.github.nalukit.paniolo.validation.Severity;
import io.github.nalukit.paniolo.validation.ValidationMessage;
import io.github.nalukit.paniolo.xml.ReadMode;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Empty optional elements, e.g. a seller written with {@code <ram:ID/>}, are valid Factur-X
 * EN 16931 and are read as absent, in both read modes and without warnings. XRechnung forbids
 * empty elements (PEPPOL-EN16931-R008); validation reports that, reading does not depend on it.
 */
class EmptyOptionalElementTest {
  private static final Paniolo PANIOLO = Paniolo.create();

  private static Invoice en16931(String number) {
    return TestInvoices.builder(InvoiceTypeCode.CODE_380,
                                number)
                       .processControl(ProcessControl.en16931())
                       .build();
  }

  private static void readsAsWithout(Invoice expected,
                                     String xml) {
    for (var mode : ReadMode.values()) {
      var read = PANIOLO.read(xml,
                              mode);
      assertEquals(expected,
                   read.invoice(),
                   () -> mode + ": " + xml);
      assertEquals(List.of(),
                   read.warnings(),
                   mode::toString);
    }
    assertEquals(expected,
                 PANIOLO.importInvoice(xml.getBytes(StandardCharsets.UTF_8),
                                       ReadMode.LENIENT)
                        .invoice());
  }

  @Test
  void readsEmptyCiiPartyIdentifiersAsAbsent() {
    var invoice = en16931("E-1");
    String xml = PANIOLO.toXml(invoice,
                               InvoiceSyntax.CII)
                        .replace("<ram:SellerTradeParty>",
                                 "<ram:SellerTradeParty><ram:ID/><ram:GlobalID schemeID=\"0088\"/>")
                        .replace("<ram:BuyerTradeParty>",
                                 "<ram:BuyerTradeParty><ram:ID></ram:ID>");
    var result = PANIOLO.validate(xml.getBytes(StandardCharsets.UTF_8),
                                  FacturXProfile.EN16931);
    assertTrue(result.isValid(),
               () -> result.messages() + "\n" + xml);
    readsAsWithout(invoice,
                   xml);
  }

  @Test
  void readsAnXrechnungWithEmptyElementsButReportsThem() {
    var invoice = TestInvoices.invoice(InvoiceTypeCode.CODE_380,
                                       "E-4");
    String xml = PANIOLO.toXml(invoice,
                               InvoiceSyntax.CII)
                        .replace("<ram:SellerTradeParty>",
                                 "<ram:SellerTradeParty><ram:ID/>");
    assertOnlyEmptyElementErrors(xml);
    readsAsWithout(invoice,
                   xml);
  }

  private static void assertOnlyEmptyElementErrors(String xml) {
    var errors = PANIOLO.validate(xml)
                        .messages()
                        .stream()
                        .filter(message -> message.severity() == Severity.ERROR || message.severity() == Severity.FATAL)
                        .map(ValidationMessage::ruleId)
                        .distinct()
                        .toList();
    assertEquals(List.of("PEPPOL-EN16931-R008"),
                 errors);
  }

  @Test
  void skipsAnEmptyCiiIdentifierBeforeAFilledOne() {
    var invoice = TestInvoices.invoice(InvoiceTypeCode.CODE_380,
                                       "E-2");
    String xml = PANIOLO.toXml(invoice,
                               InvoiceSyntax.CII)
                        .replace("<ram:SellerTradeParty>",
                                 "<ram:SellerTradeParty><ram:ID/><ram:ID>S-4711</ram:ID>");
    var seller = PANIOLO.read(xml,
                              ReadMode.STRICT)
                        .invoice()
                        .seller();
    assertEquals("S-4711",
                 seller.identifier());
    assertNull(seller.identifierSchemeId());
    assertEquals(List.of(),
                 seller.additionalIdentifiers());
  }

  @Test
  void readsEmptyUblPartyIdentifiersAsAbsent() {
    var invoice = TestInvoices.invoice(InvoiceTypeCode.CODE_380,
                                       "E-3");
    String xml = PANIOLO.toXml(invoice,
                               InvoiceSyntax.UBL);
    int seller = xml.indexOf("<cac:AccountingSupplierParty>");
    int after  = xml.indexOf("</cbc:EndpointID>",
                             seller) + "</cbc:EndpointID>".length();
    xml = xml.substring(0,
                        after) + "<cac:PartyIdentification><cbc:ID/></cac:PartyIdentification>" + xml.substring(after);
    assertOnlyEmptyElementErrors(xml);
    readsAsWithout(invoice,
                   xml);
  }
}

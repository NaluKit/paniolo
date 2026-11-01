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
import io.github.nalukit.paniolo.model.common.DeliveryInformation;
import io.github.nalukit.paniolo.model.common.Party;
import io.github.nalukit.paniolo.model.common.PartyIdentifier;
import io.github.nalukit.paniolo.model.common.ProcessControl;
import io.github.nalukit.paniolo.model.common.value.InvoiceTypeCode;
import io.github.nalukit.paniolo.validation.Severity;
import io.github.nalukit.paniolo.xml.ReadMode;
import io.github.nalukit.paniolo.xml.ReadWarning;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Party identifiers and the deliver-to location identifier keep every value with its scheme
 * (Mustang issues #583 and #503).
 */
class PartyIdentifierTest {
  private static final Paniolo         PANIOLO = Paniolo.create();
  private static final PartyIdentifier GLN     = new PartyIdentifier("4000001000005",
                                                                     "0088");

  private static Party withFurtherIdentifier(Party party,
                                             String local) {
    return new Party(party.name(),
                     party.address(),
                     party.vatId(),
                     party.taxId(),
                     party.electronicAddress(),
                     party.contact(),
                     local,
                     null,
                     party.tradingName(),
                     party.legalRegistrationId(),
                     party.legalRegistrationSchemeId(),
                     party.additionalLegalInformation(),
                     party.extended(),
                     List.of(GLN));
  }

  private static void roundTrip(Invoice invoice,
                                InvoiceSyntax syntax) {
    String xml = PANIOLO.toXml(invoice,
                               syntax);
    var result = PANIOLO.validate(xml);
    assertTrue(result.isValid(),
               () -> syntax + ": " + result.messages());
    assertEquals(invoice,
                 PANIOLO.read(xml,
                              ReadMode.STRICT)
                        .invoice(),
                 () -> syntax + ": " + xml);
  }

  @Test
  void keepsTheLocalAndTheGlobalSellerIdentifier() {
    var base    = TestInvoices.invoice(InvoiceTypeCode.CODE_380,
                                       "I-1");
    var invoice = TestInvoices.builder(InvoiceTypeCode.CODE_380,
                                       "I-1")
                              .seller(withFurtherIdentifier(base.seller(),
                                                            "S-4711"))
                              .build();
    String cii = PANIOLO.toXml(invoice,
                               InvoiceSyntax.CII);
    assertTrue(cii.contains("<ram:ID>S-4711</ram:ID>") && cii.contains("<ram:GlobalID schemeID=\"0088\">4000001000005</ram:GlobalID>"),
               cii);
    String ubl = PANIOLO.toXml(invoice,
                               InvoiceSyntax.UBL);
    assertTrue(ubl.contains("<cbc:ID>S-4711</cbc:ID>") && ubl.contains("<cbc:ID schemeID=\"0088\">4000001000005</cbc:ID>"),
               ubl);
    roundTrip(invoice,
              InvoiceSyntax.CII);
    roundTrip(invoice,
              InvoiceSyntax.UBL);
  }

  @Test
  void keepsAFurtherBuyerIdentifier() {
    var base    = TestInvoices.invoice(InvoiceTypeCode.CODE_380,
                                       "I-2");
    var invoice = TestInvoices.builder(InvoiceTypeCode.CODE_380,
                                       "I-2")
                              .buyer(withFurtherIdentifier(base.buyer(),
                                                           "K-4711"))
                              .build();
    // CII writes ram:ID and ram:GlobalID, as valid EN 16931 documents state them (Mustang #662).
    roundTrip(invoice,
              InvoiceSyntax.CII);
    var refused = assertThrows(IllegalArgumentException.class,
                               () -> PANIOLO.toXml(invoice,
                                                   InvoiceSyntax.UBL));
    assertTrue(refused.getMessage()
                      .contains("UBL-SR-16") && refused.getMessage()
                                                       .contains("4000001000005"),
               refused::getMessage);
    var extended = TestInvoices.builder(InvoiceTypeCode.CODE_380,
                                        "I-2")
                               .buyer(invoice.buyer())
                               .processControl(ProcessControl.facturXExtended())
                               .build();
    byte[] xml = PANIOLO.toBytes(extended,
                                 InvoiceSyntax.CII);
    var result = PANIOLO.validate(xml,
                                  FacturXProfile.EXTENDED);
    assertEquals(List.of(),
                 result.messages()
                       .stream()
                       .filter(message -> message.severity() == Severity.ERROR || message.severity() == Severity.FATAL)
                       .toList());
    assertEquals(extended.buyer(),
                 PANIOLO.read(xml)
                        .buyer());
  }

  @Test
  void keepsTheSchemeOfTheDeliverToLocation() {
    for (var scheme : java.util.Arrays.asList("0088",
                                              null)) {
      var invoice = TestInvoices.builder(InvoiceTypeCode.CODE_380,
                                         "I-3")
                        .delivery(DeliveryInformation.create()
                                                     .partyName("Lager Nord")
                                                     .locationId("4000001000012",
                                                                 scheme)
                                                     .actualDeliveryDate(LocalDate.of(2026,
                                                                                      10,
                                                                                      26))
                                                     .build())
                        .build();
      roundTrip(invoice,
                InvoiceSyntax.CII);
      roundTrip(invoice,
                InvoiceSyntax.UBL);
    }
  }

  @Test
  void reportsASecondDeliverToIdentifier() {
    var invoice = TestInvoices.builder(InvoiceTypeCode.CODE_380,
                                       "I-4")
                              .delivery(DeliveryInformation.create()
                                                           .partyName("Lager Nord")
                                                           .locationId("4000001000012",
                                                                       "0088")
                                                           .build())
                              .build();
    String xml = PANIOLO.toXml(invoice,
                               InvoiceSyntax.CII)
                        .replace("<ram:GlobalID schemeID=\"0088\">4000001000012</ram:GlobalID>",
                                 "<ram:ID>L-7</ram:ID><ram:GlobalID schemeID=\"0088\">4000001000012</ram:GlobalID>");
    var result   = PANIOLO.read(xml,
                                ReadMode.LENIENT);
    var delivery = result.invoice()
                         .delivery();
    assertEquals("L-7",
                 delivery.locationId());
    assertNull(delivery.locationSchemeId());
    assertTrue(result.warnings()
                     .stream()
                     .anyMatch(warning -> warning.code()
                                                 .equals(ReadWarning.IDENTIFIER) && warning.message()
                                                                                           .contains("4000001000012")),
               result.warnings()::toString);
  }
}

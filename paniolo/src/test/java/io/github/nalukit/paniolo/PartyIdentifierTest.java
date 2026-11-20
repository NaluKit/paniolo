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
import io.github.nalukit.paniolo.model.common.Payee;
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
  private static Party withIdentifier(Party party,
                                      String identifier) {
    return Party.create()
                .name(party.name())
                .address(party.address())
                .vatId(party.vatId())
                .taxId(party.taxId())
                .electronicAddress(party.electronicAddress())
                .contact(party.contact())
                .identifier(identifier)
                .build();
  }

  private static String ciiWithBuyerId(String replacement) {
    var base = TestInvoices.invoice(InvoiceTypeCode.CODE_380,
                                    "I-5");
    var invoice = TestInvoices.builder(InvoiceTypeCode.CODE_380,
                                       "I-5")
                              .buyer(withIdentifier(base.buyer(),
                                                    "K-4711"))
                              .build();
    String xml = PANIOLO.toXml(invoice,
                               InvoiceSyntax.CII);
    assertTrue(xml.contains("<ram:ID>K-4711</ram:ID>"),
               xml);
    return xml.replace("<ram:ID>K-4711</ram:ID>",
                       replacement);
  }

  /**
   * A scheme on the local {@code ram:ID} is allowed by the CII schema and, in the XRechnung extension,
   * by BR-DEX-05 (ISO 6523 and DIGA codes). The source is XML as another producer writes it, not the
   * output of this library.
   */
  @Test
  void keepsTheSchemeOfALocalBuyerIdentifier() {
    for (var scheme : List.of("0011",
                              "XR01")) {
      String xml = ciiWithBuyerId("<ram:ID schemeID=\"" + scheme + "\">K-4711</ram:ID>");
      for (var mode : ReadMode.values()) {
        var result = PANIOLO.read(xml,
                                  mode);
        var buyer  = result.invoice()
                           .buyer();
        assertEquals("K-4711",
                     buyer.identifier(),
                     mode + " " + scheme);
        assertEquals(scheme,
                     buyer.identifierSchemeId(),
                     mode + " " + scheme);
        assertTrue(result.warnings()
                         .stream()
                         .noneMatch(warning -> warning.code()
                                                      .equals(ReadWarning.IDENTIFIER)),
                   result.warnings()::toString);
        // The qualified identifier survives the normal export and is read again unchanged.
        String exported = PANIOLO.toXml(result.invoice(),
                                        InvoiceSyntax.CII);
        assertTrue(exported.contains("<ram:GlobalID schemeID=\"" + scheme + "\">K-4711</ram:GlobalID>"),
                   exported);
        assertEquals(buyer,
                     PANIOLO.read(exported,
                                  mode)
                            .invoice()
                            .buyer());
      }
    }
  }

  @Test
  void keepsTheSchemeOfALocalSellerIdentifier() {
    var base = TestInvoices.invoice(InvoiceTypeCode.CODE_380,
                                    "I-6");
    var invoice = TestInvoices.builder(InvoiceTypeCode.CODE_380,
                                       "I-6")
                              .seller(withIdentifier(base.seller(),
                                                     "S-4711"))
                              .build();
    String xml = PANIOLO.toXml(invoice,
                               InvoiceSyntax.CII)
                        .replace("<ram:ID>S-4711</ram:ID>",
                                 "<ram:ID schemeID=\"0011\">S-4711</ram:ID>");
    for (var mode : ReadMode.values()) {
      var seller = PANIOLO.read(xml,
                                mode)
                          .invoice()
                          .seller();
      assertEquals("S-4711",
                   seller.identifier());
      assertEquals("0011",
                   seller.identifierSchemeId());
    }
  }

  @Test
  void keepsTheSchemeOfAFurtherLocalIdentifier() {
    String xml = ciiWithBuyerId("<ram:ID>K-4711</ram:ID><ram:ID schemeID=\"0011\">K-2</ram:ID>");
    for (var mode : ReadMode.values()) {
      var buyer = PANIOLO.read(xml,
                               mode)
                         .invoice()
                         .buyer();
      assertEquals("K-4711",
                   buyer.identifier());
      assertNull(buyer.identifierSchemeId());
      assertEquals(List.of(new PartyIdentifier("K-2",
                                               "0011")),
                   buyer.additionalIdentifiers(),
                   mode.toString());
    }
  }

  /** Controls: an identifier without scheme stays without one, a global identifier keeps its scheme. */
  @Test
  void leavesTheOtherIdentifierVariantsUnchanged() {
    for (var mode : ReadMode.values()) {
      var plain = PANIOLO.read(ciiWithBuyerId("<ram:ID>K-4711</ram:ID>"),
                               mode)
                         .invoice()
                         .buyer();
      assertEquals("K-4711",
                   plain.identifier());
      assertNull(plain.identifierSchemeId());
      var global = PANIOLO.read(ciiWithBuyerId("<ram:GlobalID schemeID=\"0011\">K-4711</ram:GlobalID>"),
                                mode)
                          .invoice()
                          .buyer();
      assertEquals("K-4711",
                   global.identifier());
      assertEquals("0011",
                   global.identifierSchemeId());
    }
  }
  /** CII of an invoice whose buyer states one VAT identifier, followed by the given registrations. */
  private static String ciiWithBuyerVat(String... furtherRegistrations) {
    var base  = TestInvoices.invoice(InvoiceTypeCode.CODE_380,
                                     "I-7");
    var buyer = Party.create()
                     .name(base.buyer()
                               .name())
                     .address(base.buyer()
                                  .address())
                     .vatId("DE123456789")
                     .build();
    String xml = PANIOLO.toXml(TestInvoices.builder(InvoiceTypeCode.CODE_380,
                                                    "I-7")
                                           .buyer(buyer)
                                           .build(),
                               InvoiceSyntax.CII);
    int buyerStart = xml.indexOf("<ram:BuyerTradeParty>");
    String closing = "</ram:SpecifiedTaxRegistration>";
    int    end     = xml.indexOf(closing,
                                 buyerStart);
    assertTrue(buyerStart > 0 && end > buyerStart && xml.substring(buyerStart,
                                                                    end)
                                                         .contains("DE123456789"),
               xml);
    var added = new StringBuilder(closing);
    for (var registration : furtherRegistrations) {
      added.append("<ram:SpecifiedTaxRegistration>")
           .append(registration)
           .append(closing);
    }
    return xml.substring(0,
                         end) + added + xml.substring(end + closing.length());
  }

  private static List<ReadWarning> identifierWarnings(String xml,
                                                      ReadMode mode) {
    return PANIOLO.read(xml,
                        mode)
                  .warnings()
                  .stream()
                  .filter(warning -> warning.code()
                                            .equals(ReadWarning.IDENTIFIER))
                  .toList();
  }

  @Test
  void reportsASecondDifferentBuyerVatIdentifier() {
    String xml = ciiWithBuyerVat("<ram:ID schemeID=\"VA\">FR12345678901</ram:ID>");
    for (var mode : ReadMode.values()) {
      var result   = PANIOLO.read(xml,
                                  mode);
      var warnings = identifierWarnings(xml,
                                        mode);
      // The model keeps one VAT identifier: the last stated value, as before; the other is named.
      assertEquals("FR12345678901",
                   result.invoice()
                         .buyer()
                         .vatId(),
                   mode.toString());
      assertEquals(1,
                   warnings.size(),
                   warnings::toString);
      assertTrue(warnings.get(0)
                         .message()
                         .contains("BuyerTradeParty") && warnings.get(0)
                                                                .message()
                                                                .contains("DE123456789") && warnings.get(0)
                                                                                                    .message()
                                                                                                    .contains("FR12345678901") && warnings.get(0)
                                                                                                                                         .message()
                                                                                                                                         .contains("VA"),
                 warnings::toString);
    }
  }

  @Test
  void doesNotReportASingleOrARepeatedBuyerVatIdentifier() {
    for (var mode : ReadMode.values()) {
      assertEquals(List.of(),
                   identifierWarnings(ciiWithBuyerVat(),
                                      mode));
      assertEquals(List.of(),
                   identifierWarnings(ciiWithBuyerVat("<ram:ID schemeID=\"VA\">DE123456789</ram:ID>"),
                                      mode));
      // An empty repetition states nothing and does not replace the value.
      var empty = ciiWithBuyerVat("<ram:ID schemeID=\"VA\"/>");
      assertEquals(List.of(),
                   identifierWarnings(empty,
                                      mode));
      assertEquals("DE123456789",
                   PANIOLO.read(empty,
                                mode)
                          .invoice()
                          .buyer()
                          .vatId());
    }
  }
  private static String zugferd1() throws java.io.IOException {
    try (var input = PartyIdentifierTest.class.getResourceAsStream("/regression/zugferd1-party-identifiers.xml")) {
      return new String(input.readAllBytes(),
                        java.nio.charset.StandardCharsets.UTF_8);
    }
  }

  /** The ZUGFeRD 1.0 reader shares the party and identifier handling of the CII reader. */
  @Test
  void zugferd1KeepsIdentifiersAndReportsTheDroppedVatIdentifier() throws java.io.IOException {
    // BASIC takes the party over into the EN 16931 form; EXTENDED reads it as it is.
    for (var profile : List.of("extended",
                               "basic")) {
      // A contact with department makes the party extension non-empty; the reader of the other
      // profiles then rebuilds the party and must carry the further identifiers over.
      String xml = zugferd1().replace("1p0:extended",
                                      "1p0:" + profile)
                             .replace("<ram:Name>Buyer AG</ram:Name>",
                                      "<ram:Name>Buyer AG</ram:Name><ram:DefinedTradeContact><ram:PersonName>Ann Buyer</ram:PersonName><ram:DepartmentName>Purchasing</ram:DepartmentName></ram:DefinedTradeContact>");
      zugferd1Identifiers(xml,
                          profile);
    }
  }

  private static void zugferd1Identifiers(String xml,
                                          String profile) {
    for (var mode : ReadMode.values()) {
      var result = PANIOLO.read(xml,
                                mode);
      var buyer  = result.invoice()
                         .buyer();
      assertEquals("K-4711",
                   buyer.identifier(),
                   mode.toString());
      // The global identifier next to ram:ID is kept, as the party constructor of the reader used to drop it.
      assertEquals(List.of(new PartyIdentifier("4000001000005",
                                               "0088")),
                   buyer.additionalIdentifiers(),
                   profile + " " + mode);
      assertEquals("FR12345678901",
                   buyer.vatId());
      assertEquals(1,
                   identifierWarnings(xml,
                                      mode).size(),
                   result.warnings()::toString);
      assertTrue(identifierWarnings(xml,
                                    mode).get(0)
                                         .message()
                                         .contains("DE123456789"),
                 result.warnings()::toString);
      assertEquals("0088",
                   result.invoice()
                         .payee()
                         .identifierSchemeId(),
                   mode.toString());
      assertEquals("4000001000029",
                   result.invoice()
                         .payee()
                         .identifier());
      assertEquals("0088",
                   result.invoice()
                         .delivery()
                         .locationSchemeId(),
                   mode.toString());
    }
  }

  @Test
  void keepsTheSchemeOfALocalPayeeIdentifier() {
    var invoice = TestInvoices.builder(InvoiceTypeCode.CODE_380,
                                       "I-8")
                              .payee(Payee.create()
                                          .name("Payee GmbH")
                                          .identifier("P-1")
                                          .build())
                              .build();
    String xml = PANIOLO.toXml(invoice,
                               InvoiceSyntax.CII);
    assertTrue(xml.contains("<ram:ID>P-1</ram:ID>"),
               xml);
    String scheme = xml.replace("<ram:ID>P-1</ram:ID>",
                                "<ram:ID schemeID=\"0088\">P-1</ram:ID>");
    String sepa   = xml.replace("<ram:ID>P-1</ram:ID>",
                                "<ram:ID schemeID=\"SEPA\">P-1</ram:ID>");
    for (var mode : ReadMode.values()) {
      var payee = PANIOLO.read(scheme,
                               mode)
                         .invoice()
                         .payee();
      assertEquals("P-1",
                   payee.identifier());
      assertEquals("0088",
                   payee.identifierSchemeId(),
                   mode.toString());
      assertTrue(PANIOLO.toXml(PANIOLO.read(scheme,
                                            mode)
                                      .invoice(),
                               InvoiceSyntax.CII)
                        .contains("<ram:GlobalID schemeID=\"0088\">P-1</ram:GlobalID>"));
      // The scheme SEPA is reserved by the model: it is dropped as before, but now reported.
      var reserved = PANIOLO.read(sepa,
                                  mode);
      assertNull(reserved.invoice()
                         .payee()
                         .identifierSchemeId());
      assertEquals("P-1",
                   reserved.invoice()
                           .payee()
                           .identifier());
      assertEquals(1,
                   identifierWarnings(sepa,
                                      mode).size(),
                   mode.toString());
    }
  }

  @Test
  void keepsTheSchemeOfALocalDeliverToLocationIdentifier() {
    var invoice = TestInvoices.builder(InvoiceTypeCode.CODE_380,
                                       "I-9")
                              .delivery(DeliveryInformation.create()
                                                           .partyName("Lager Nord")
                                                           .locationId("L-7",
                                                                       null)
                                                           .build())
                              .build();
    String xml = PANIOLO.toXml(invoice,
                               InvoiceSyntax.CII);
    assertTrue(xml.contains("<ram:ID>L-7</ram:ID>"),
               xml);
    String scheme = xml.replace("<ram:ID>L-7</ram:ID>",
                                "<ram:ID schemeID=\"0088\">L-7</ram:ID>");
    for (var mode : ReadMode.values()) {
      var delivery = PANIOLO.read(scheme,
                                  mode)
                            .invoice()
                            .delivery();
      assertEquals("L-7",
                   delivery.locationId());
      assertEquals("0088",
                   delivery.locationSchemeId(),
                   mode.toString());
    }
  }
  @Test
  void zugferd1ReadsAGlobalDeliverToLocationIdentifier() throws java.io.IOException {
    String local  = "<ram:ID schemeID=\"0088\">4000001000012</ram:ID>";
    String global = "<ram:GlobalID schemeID=\"0088\">4000001000012</ram:GlobalID>";
    String xml    = zugferd1();
    assertTrue(xml.contains(local),
               xml);
    for (var mode : ReadMode.values()) {
      var delivery = PANIOLO.read(xml.replace(local,
                                              global),
                                  mode)
                            .invoice()
                            .delivery();
      assertEquals("4000001000012",
                   delivery.locationId(),
                   mode.toString());
      assertEquals("0088",
                   delivery.locationSchemeId(),
                   mode.toString());
      // A second identifier is more than BT-71 keeps and is reported.
      String both = xml.replace(local,
                                "<ram:ID>L-7</ram:ID>" + global);
      var result = PANIOLO.read(both,
                                mode);
      assertEquals("L-7",
                   result.invoice()
                         .delivery()
                         .locationId());
      assertTrue(result.warnings()
                       .stream()
                       .anyMatch(warning -> warning.code()
                                                   .equals(ReadWarning.IDENTIFIER) && warning.message()
                                                                                             .contains("4000001000012")),
                 result.warnings()::toString);
    }
  }
}

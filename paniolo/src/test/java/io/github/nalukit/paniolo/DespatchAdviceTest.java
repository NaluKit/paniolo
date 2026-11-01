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

import io.github.nalukit.paniolo.model.common.Address;
import io.github.nalukit.paniolo.model.common.Contact;
import io.github.nalukit.paniolo.model.common.ElectronicAddress;
import io.github.nalukit.paniolo.model.common.InvoicePeriod;
import io.github.nalukit.paniolo.model.common.ItemClassification;
import io.github.nalukit.paniolo.model.common.Party;
import io.github.nalukit.paniolo.model.common.value.Country;
import io.github.nalukit.paniolo.model.common.value.UnitCode;
import io.github.nalukit.paniolo.model.despatch.DespatchAdvice;
import io.github.nalukit.paniolo.model.despatch.DespatchItemInstance;
import io.github.nalukit.paniolo.model.despatch.DespatchLine;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DespatchAdviceTest {
  private static final Paniolo PANIOLO = Paniolo.create();

  private static Address address(String street,
                                 String city,
                                 String postalCode) {
    return Address.create()
                  .street(street)
                  .city(city)
                  .postalCode(postalCode)
                  .country(Country.DE)
                  .build();
  }

  static DespatchAdvice.Builder advice() {
    var supplier = Party.create()
                        .name("Muster GmbH")
                        .address(address("Musterstraße 1",
                                         "Berlin",
                                         "10115"))
                        .electronicAddress(new ElectronicAddress("4000001000005",
                                                                 "0088"))
                        .identifier("4000001000005",
                                    "0088")
                        .contact(Contact.create()
                                        .name("Versand")
                                        .telephone("+49 30 123456")
                                        .email("versand@example.org")
                                        .build())
                        .build();
    var customer = Party.create()
                        .name("Kunde GmbH")
                        .address(address("Hafenstraße 5",
                                         "Hamburg",
                                         "20457"))
                        .electronicAddress(new ElectronicAddress("wareneingang@example.org",
                                                                 "EM"))
                        .contact(Contact.create()
                                        .name("Wareneingang")
                                        .build())
                        .build();
    var buyer = Party.create()
                     .name("Kunde GmbH")
                     .address(address("Kundenweg 2",
                                      "Hamburg",
                                      "20095"))
                     .identifier("K-4711")
                     .build();
    return DespatchAdvice.create()
                         .number("LS-2026-42")
                         .issueDate(LocalDate.of(2026,
                                                 11,
                                                 9))
                         .note("Anlieferung an Rampe 3")
                         .addOrderReference("B-2026-17")
                         .despatchSupplier(supplier)
                         .deliveryCustomer(customer)
                         .buyer(buyer)
                         .shipmentId("SH-9")
                         .shipmentInformation("2 Paletten")
                         .grossWeight(new BigDecimal("412.5"))
                         .grossWeightUnit(UnitCode.of("KGM"))
                         .handlingUnitCount(new BigDecimal("2"))
                         .transportModeCode("3")
                         .trackingId("TRK-123")
                         .estimatedDeliveryPeriod(new InvoicePeriod(LocalDate.of(2026,
                                                                                 11,
                                                                                 10),
                                                                    LocalDate.of(2026,
                                                                                 11,
                                                                                 11)))
                         .actualDespatchDate(LocalDate.of(2026,
                                                          11,
                                                          9))
                         .despatchAddress(address("Lagerweg 7",
                                                  "Dortmund",
                                                  "44137"))
                         .addLine(DespatchLine.create()
                                              .id("1")
                                              .deliveredQuantity(new BigDecimal("8"))
                                              .unitCode("H87")
                                              .outstandingQuantity(new BigDecimal("2"))
                                              .outstandingReason("Rest folgt")
                                              .orderLineId("1")
                                              .itemName("Gerät")
                                              .sellerItemId("G-100")
                                              .standardItemId("4012345678901",
                                                              "0160")
                                              .addClassification(new ItemClassification("8471",
                                                                                        "HS",
                                                                                        null,
                                                                                        null))
                                              .addAttribute("Farbe",
                                                            "grau")
                                              .addInstance(DespatchItemInstance.serial("SN-1"))
                                              .addInstance(DespatchItemInstance.lot("CH-7",
                                                                                    LocalDate.of(2028,
                                                                                                 1,
                                                                                                 31)))
                                              .handlingUnitId("340123450000000017")
                                              .handlingUnitTypeCode("PX")
                                              .build())
                         .addLine(DespatchLine.create()
                                              .id("2")
                                              .deliveredQuantity(new BigDecimal("25"))
                                              .unitCode("MTR")
                                              .orderLineId("2")
                                              .itemName("Kabel")
                                              .build());
  }

  /**
   * A despatch advice with the content CIDA carries.
   */
  static DespatchAdvice cida() {
    var full = advice().build();
    return DespatchAdvice.create()
                         .number(full.number())
                         .issueDate(full.issueDate())
                         .note(full.note())
                         .addOrderReference("B-2026-17")
                         .despatchSupplier(full.despatchSupplier())
                         .deliveryCustomer(full.deliveryCustomer())
                         .buyer(full.buyer())
                         .seller(Party.create()
                                      .name("Muster Vertrieb GmbH")
                                      .address(address("Marktplatz 3",
                                                       "Berlin",
                                                       "10117"))
                                      .vatId("DE123456788")
                                      .build())
                         .originator(Party.create()
                                          .name("Einkauf Kunde")
                                          .address(address("Kundenweg 2",
                                                           "Hamburg",
                                                           "20095"))
                                          .build())
                         .actualDespatchDate(LocalDate.of(2026,
                                                          11,
                                                          9))
                         .addLine(DespatchLine.create()
                                              .id("1")
                                              .note("Vorsicht Glas")
                                              .deliveredQuantity(new BigDecimal("8"))
                                              .unitCode("H87")
                                              .orderLineId("1")
                                              .orderReference("B-2026-18")
                                              .itemName("Gerät")
                                              .sellerItemId("G-100")
                                              .buyerItemId("K-55")
                                              .standardItemId("4012345678901",
                                                              "0160")
                                              .addClassification(new ItemClassification("8471",
                                                                                        "HS",
                                                                                        null,
                                                                                        null))
                                              .addAttribute("Farbe",
                                                            "grau")
                                              .addInstance(DespatchItemInstance.serial("SN-1"))
                                              .addInstance(DespatchItemInstance.lot("CH-7",
                                                                                    null))
                                              .build())
                         .addLine(full.lines()
                                      .get(1))
                         .build();
  }

  @Test
  void writesReadsAndValidatesADespatchAdvice() {
    var    advice = advice().build();
    String xml    = PANIOLO.toDespatchAdvice(advice);
    assertTrue(xml.contains(DespatchAdvice.PEPPOL_CUSTOMIZATION),
               xml);
    assertEquals(advice,
                 PANIOLO.readDespatchAdvice(xml.getBytes(StandardCharsets.UTF_8)));
    var result = PANIOLO.validateDespatchAdvice(xml.getBytes(StandardCharsets.UTF_8));
    assertTrue(result.isValid(),
               () -> result.messages()
                           .toString());
    assertEquals(List.of("PEPPOL-RULES-NOT-CHECKED"),
                 result.messages()
                       .stream()
                       .map(io.github.nalukit.paniolo.validation.ValidationMessage::ruleId)
                       .toList());
  }

  @Test
  void reportsSchemaErrorsAndConfiguredRules()
      throws Exception {
    byte[] xml = PANIOLO.toDespatchAdviceBytes(advice().build());
    // An element in the wrong place violates the UBL schema.
    String broken = new String(xml,
                               StandardCharsets.UTF_8).replace("<cbc:Note>Anlieferung an Rampe 3</cbc:Note>",
                                                               "")
                                                      .replace("<cbc:ID>LS-2026-42</cbc:ID>",
                                                               "<cbc:Note>vorher</cbc:Note><cbc:ID>LS-2026-42</cbc:ID>");
    assertFalse(PANIOLO.validateDespatchAdvice(broken.getBytes(StandardCharsets.UTF_8))
                       .isValid());

    var rules = Path.of(DespatchAdviceTest.class.getResource("/test/peppol/despatch-advice-rules.xslt")
                                                .toURI());
    var peppol = Paniolo.builder()
                        .despatchAdviceRules(rules)
                        .build();
    var valid = peppol.validateDespatchAdvice(xml);
    assertTrue(valid.isValid(),
               () -> valid.messages()
                          .toString());
    assertEquals("Peppol BIS Despatch Advice 3",
                 valid.profileName());
    var withoutEndpoint = advice().despatchSupplier(Party.create()
                                                         .name("Muster GmbH")
                                                         .address(address("Musterstraße 1",
                                                                          "Berlin",
                                                                          "10115"))
                                                         .build())
                                  .build();
    var invalid = peppol.validateDespatchAdvice(PANIOLO.toDespatchAdviceBytes(withoutEndpoint));
    assertTrue(invalid.messages()
                      .stream()
                      .anyMatch(message -> "TEST-ENDPOINT".equals(message.ruleId())),
               () -> invalid.messages()
                            .toString());
  }

  @Test
  void writesReadsAndValidatesAOneLieferschein()
      throws Exception {
    var advice = advice().oneLieferschein()
                         .actualDeliveryDate(LocalDate.of(2026,
                                                          11,
                                                          10))
                         .build();
    assertTrue(advice.isOneLieferschein());
    assertFalse(advice.isPeppol());
    String xml = PANIOLO.toDespatchAdvice(advice);
    assertTrue(xml.contains("<cbc:CustomizationID>1Lieferschein</cbc:CustomizationID>"),
               xml);
    assertTrue(xml.contains("<cbc:ProfileID>ubl-xml-only</cbc:ProfileID>"),
               xml);
    assertTrue(xml.contains("<cbc:ActualDeliveryDate>2026-11-10</cbc:ActualDeliveryDate>"),
               xml);
    // Only the line with a transport handling unit has a line shipment.
    assertEquals(1,
                 xml.split("<cbc:ID>NA</cbc:ID>",
                           -1).length - 1,
                 xml);
    assertEquals(advice,
                 PANIOLO.readDespatchAdvice(xml.getBytes(StandardCharsets.UTF_8)));
    var result = PANIOLO.validateDespatchAdvice(xml.getBytes(StandardCharsets.UTF_8));
    assertTrue(result.isValid(),
               () -> result.messages()
                           .toString());
    assertEquals(List.of("1LIEFERSCHEIN-NOT-CHECKED"),
                 result.messages()
                       .stream()
                       .map(io.github.nalukit.paniolo.validation.ValidationMessage::ruleId)
                       .toList());
    // The Peppol rules do not apply to a 1Lieferschein, even if configured.
    var rules = Path.of(DespatchAdviceTest.class.getResource("/test/peppol/despatch-advice-rules.xslt")
                                                .toURI());
    var peppol = Paniolo.builder()
                        .despatchAdviceRules(rules)
                        .build()
                        .validateDespatchAdvice(xml.getBytes(StandardCharsets.UTF_8));
    assertEquals(List.of("1LIEFERSCHEIN-NOT-CHECKED"),
                 peppol.messages()
                       .stream()
                       .map(io.github.nalukit.paniolo.validation.ValidationMessage::ruleId)
                       .toList());
  }

  @Test
  void rejectsTheActualDeliveryDateInPeppolAndCida() {
    var peppol = assertThrows(IllegalArgumentException.class,
                              () -> PANIOLO.toDespatchAdvice(advice().actualDeliveryDate(LocalDate.of(2026,
                                                                                                      11,
                                                                                                      10))
                                                                     .build()));
    assertTrue(peppol.getMessage()
                     .contains("A Peppol despatch advice has no place for: actual delivery date"),
               peppol::getMessage);
    var lieferschein = advice().oneLieferschein()
                               .buyer(Party.create()
                                           .name("Kunde GmbH")
                                           .address(address("Kundenweg 2",
                                                            "Hamburg",
                                                            "20095"))
                                           .vatId("DE987654321")
                                           .build())
                               .build();
    var vat = assertThrows(IllegalArgumentException.class,
                           () -> PANIOLO.toDespatchAdvice(lieferschein));
    assertTrue(vat.getMessage()
                  .contains("A UBL despatch advice has no place for: buyer VAT identifier"),
               vat::getMessage);
    var cida = assertThrows(IllegalArgumentException.class,
                            () -> PANIOLO.toCiiDespatchAdvice(DespatchAdvice.create()
                                                                            .number("LS-1")
                                                                            .issueDate(LocalDate.of(2026,
                                                                                                    11,
                                                                                                    9))
                                                                            .despatchSupplier(cida().despatchSupplier())
                                                                            .deliveryCustomer(cida().deliveryCustomer())
                                                                            .buyer(cida().buyer())
                                                                            .actualDeliveryDate(LocalDate.of(2026,
                                                                                                             11,
                                                                                                             10))
                                                                            .addLine(cida().lines()
                                                                                           .get(1))
                                                                            .oneLieferschein()
                                                                            .build()));
    assertTrue(cida.getMessage()
                   .contains("customization and profile identifiers other than Peppol"),
               cida::getMessage);
    assertTrue(cida.getMessage()
                   .contains("actual delivery date"),
               cida::getMessage);
  }

  @Test
  void rejectsWhatTheModelOrPeppolCannotCarry() {
    String xml = PANIOLO.toDespatchAdvice(advice().build());
    String unsupported = xml.replace("<cbc:Name>Kabel</cbc:Name>",
                                     "<cbc:Name>Kabel</cbc:Name><cac:HazardousItem><cbc:UNDGCode>1203</cbc:UNDGCode></cac:HazardousItem>");
    var error = assertThrows(IllegalArgumentException.class,
                             () -> PANIOLO.readDespatchAdvice(unsupported.getBytes(StandardCharsets.UTF_8)));
    assertTrue(error.getMessage()
                    .contains("HazardousItem/UNDGCode"),
               error::getMessage);
    assertEquals(advice().build(),
                 PANIOLO.readDespatchAdvice(unsupported.getBytes(StandardCharsets.UTF_8),
                                            true));
    var withVat = advice().buyer(Party.create()
                                      .name("Kunde GmbH")
                                      .address(address("Kundenweg 2",
                                                       "Hamburg",
                                                       "20095"))
                                      .vatId("DE987654321")
                                      .build())
                          .build();
    var vat = assertThrows(IllegalArgumentException.class,
                           () -> PANIOLO.toDespatchAdvice(withVat));
    assertTrue(vat.getMessage()
                  .contains("buyer VAT identifier"),
               vat::getMessage);
    // UBL needs the shipment id for shipment details; CIDA has no shipment.
    var withoutShipmentId = assertThrows(IllegalArgumentException.class,
                                         () -> PANIOLO.toDespatchAdvice(advice().shipmentId(null)
                                                                                .build()));
    assertTrue(withoutShipmentId.getMessage()
                                .contains("shipment details without the shipment id"),
               withoutShipmentId::getMessage);
    assertThrows(IllegalArgumentException.class,
                 () -> PANIOLO.validateDespatchAdvice("<Invoice xmlns=\"urn:oasis:names:specification:ubl:schema:xsd:Invoice-2\"/>".getBytes(StandardCharsets.UTF_8)));
  }

  @Test
  void writesAndReadsACidaDespatchAdvice() {
    var    advice = cida();
    String xml    = PANIOLO.toCiiDespatchAdvice(advice);
    assertTrue(xml.contains("<rsm:SCRDMCCBDACIDAMessageStructure"),
               xml);
    assertTrue(xml.contains("<ram:ID>urn:awv-net.de:CIDA:1.0:pilot</ram:ID>"),
               xml);
    assertTrue(xml.contains("<ram:TypeCode>351</ram:TypeCode>"),
               xml);
    assertTrue(xml.contains("<ram:DespatchedQuantity unitCode=\"H87\">8</ram:DespatchedQuantity>"),
               xml);
    assertEquals(advice,
                 PANIOLO.readDespatchAdvice(xml.getBytes(StandardCharsets.UTF_8)));

    // Without another seller, the despatch supplier is the seller and no ship-from party is written.
    var withoutSeller = advice().shipmentId(null)
                                .shipmentInformation(null)
                                .grossWeight(null)
                                .grossWeightUnit(null)
                                .handlingUnitCount(null)
                                .transportModeCode(null)
                                .trackingId(null)
                                .estimatedDeliveryPeriod(null)
                                .despatchAddress(null)
                                .build();
    withoutSeller = DespatchAdvice.create()
                                  .number(withoutSeller.number())
                                  .issueDate(withoutSeller.issueDate())
                                  .despatchSupplier(withoutSeller.despatchSupplier())
                                  .deliveryCustomer(withoutSeller.deliveryCustomer())
                                  .buyer(withoutSeller.buyer())
                                  .actualDespatchDate(withoutSeller.actualDespatchDate())
                                  .addLine(withoutSeller.lines()
                                                        .get(1))
                                  .build();
    String simple = PANIOLO.toCiiDespatchAdvice(withoutSeller);
    assertFalse(simple.contains("ShipFromTradeParty"),
                simple);
    assertEquals(withoutSeller,
                 PANIOLO.readDespatchAdvice(simple.getBytes(StandardCharsets.UTF_8)));

    // The Mustang project writes the root element unqualified.
    String unqualified = xml.replace("<rsm:SCRDMCCBDACIDAMessageStructure",
                                     "<SCRDMCCBDACIDAMessageStructure")
                            .replace("</rsm:SCRDMCCBDACIDAMessageStructure>",
                                     "</SCRDMCCBDACIDAMessageStructure>");
    assertEquals(advice,
                 PANIOLO.readDespatchAdvice(unqualified.getBytes(StandardCharsets.UTF_8)));

    var result = PANIOLO.validateDespatchAdvice(xml.getBytes(StandardCharsets.UTF_8));
    assertTrue(result.isValid(),
               () -> result.messages()
                           .toString());
    assertEquals(List.of("CIDA-NOT-CHECKED"),
                 result.messages()
                       .stream()
                       .map(io.github.nalukit.paniolo.validation.ValidationMessage::ruleId)
                       .toList());
  }

  @Test
  void rejectsWhatCidaCannotCarry() {
    var error = assertThrows(IllegalArgumentException.class,
                             () -> PANIOLO.toCiiDespatchAdvice(advice().build()));
    for (String expected : List.of("shipment id",
                                   "gross weight",
                                   "estimated delivery period",
                                   "despatch address",
                                   "line 1 outstanding quantity",
                                   "line 1 handling unit",
                                   "line 1 item instance dates")) {
      assertTrue(error.getMessage()
                      .contains(expected),
                 error::getMessage);
    }

    // Line amounts, as the Mustang project writes them, have no place in the model.
    String xml = PANIOLO.toCiiDespatchAdvice(cida());
    String amounts = xml.replace("</ram:SpecifiedLineTradeDelivery>",
                                 "</ram:SpecifiedLineTradeDelivery><ram:SpecifiedLineTradeSettlement><ram:SpecifiedTradeSettlementLineMonetarySummation><ram:LineTotalAmount>160.00</ram:LineTotalAmount></ram:SpecifiedTradeSettlementLineMonetarySummation></ram:SpecifiedLineTradeSettlement>");
    var unsupported = assertThrows(IllegalArgumentException.class,
                                   () -> PANIOLO.readDespatchAdvice(amounts.getBytes(StandardCharsets.UTF_8)));
    assertTrue(unsupported.getMessage()
                          .contains("LineTotalAmount"),
               unsupported::getMessage);
    assertEquals(cida(),
                 PANIOLO.readDespatchAdvice(amounts.getBytes(StandardCharsets.UTF_8),
                                            true));
    // A line without order line reference is CIDA, but not Peppol UBL.
    String withoutOrderLine = xml.replaceAll("<ram:SpecifiedLineTradeAgreement>\\s*<ram:BuyerOrderReferencedDocument>\\s*<ram:LineID>2</ram:LineID>\\s*</ram:BuyerOrderReferencedDocument>\\s*</ram:SpecifiedLineTradeAgreement>",
                                             "");
    assertFalse(withoutOrderLine.equals(xml));
    var read = PANIOLO.readDespatchAdvice(withoutOrderLine.getBytes(StandardCharsets.UTF_8));
    assertEquals(null,
                 read.lines()
                     .get(1)
                     .orderLineId());
    assertEquals(read,
                 PANIOLO.readDespatchAdvice(PANIOLO.toCiiDespatchAdviceBytes(read)));
    var ubl = assertThrows(IllegalArgumentException.class,
                           () -> PANIOLO.toDespatchAdvice(read));
    assertTrue(ubl.getMessage()
                  .contains("line 2 without the order line id"),
               ubl::getMessage);

    // A document the model cannot read is reported by the validation.
    var invalid = PANIOLO.validateDespatchAdvice(xml.replaceAll("<ram:DespatchedQuantity unitCode=\"MTR\">25</ram:DespatchedQuantity>",
                                                                "")
                                                    .getBytes(StandardCharsets.UTF_8));
    assertFalse(invalid.isValid());
    assertEquals("CIDA-READ",
                 invalid.messages()
                        .get(0)
                        .ruleId());
  }

  @Test
  void readsDeliveryNotesOfTheMustangProject() {
    // Shaped like the output of the Mustang project: unqualified root, type code 220, test
    // indicator, buyer reference, line amounts, no order line references.
    byte[] mustang = """
                     <?xml version="1.0" encoding="UTF-8"?>
                     <SCRDMCCBDACIDAMessageStructure xmlns:udt="urn:un:unece:uncefact:data:standard:UnqualifiedDataType:25" xmlns:ram="urn:un:unece:uncefact:data:standard:ReusableAggregateBusinessInformationEntity:101" xmlns:px="urn:un:unece:uncefact:data:standard:SCRDMCCBDACIDAMessageStructure:1">
                       <px:ExchangedDocumentContext>
                         <ram:TestIndicator><udt:Indicator>true</udt:Indicator></ram:TestIndicator>
                         <ram:BusinessProcessSpecifiedDocumentContextParameter><ram:ID>urn:awv-net.de:CIDA:1.0:pilot</ram:ID></ram:BusinessProcessSpecifiedDocumentContextParameter>
                       </px:ExchangedDocumentContext>
                       <px:ExchangedDocument>
                         <ram:ID>RE-20170509/505</ram:ID>
                         <ram:TypeCode>220</ram:TypeCode>
                         <ram:IssueDateTime><udt:DateTimeString format="102">20170509</udt:DateTimeString></ram:IssueDateTime>
                       </px:ExchangedDocument>
                       <px:SupplyChainTradeTransaction>
                         <ram:IncludedSupplyChainTradeLineItem>
                           <ram:AssociatedDocumentLineDocument><ram:LineID>1</ram:LineID></ram:AssociatedDocumentLineDocument>
                           <ram:SpecifiedTradeProduct><ram:Name>Heiße Luft pro Liter</ram:Name><ram:Description/></ram:SpecifiedTradeProduct>
                           <ram:SpecifiedLineTradeDelivery><ram:DespatchedQuantity unitCode="LTR">200.0000</ram:DespatchedQuantity></ram:SpecifiedLineTradeDelivery>
                           <ram:SpecifiedLineTradeSettlement>
                             <ram:SpecifiedTradeSettlementLineMonetarySummation><ram:LineTotalAmount>20.00</ram:LineTotalAmount></ram:SpecifiedTradeSettlementLineMonetarySummation>
                           </ram:SpecifiedLineTradeSettlement>
                         </ram:IncludedSupplyChainTradeLineItem>
                         <ram:ApplicableHeaderTradeAgreement>
                           <ram:BuyerReference>AB321</ram:BuyerReference>
                           <ram:SellerTradeParty>
                             <ram:Name>Bei Spiel GmbH</ram:Name>
                             <ram:PostalTradeAddress><ram:PostcodeCode>12345</ram:PostcodeCode><ram:LineOne>Ecke 12</ram:LineOne><ram:CityName>Stadthausen</ram:CityName><ram:CountryID>DE</ram:CountryID></ram:PostalTradeAddress>
                             <ram:SpecifiedTaxRegistration><ram:ID schemeID="VA">DE0815</ram:ID></ram:SpecifiedTaxRegistration>
                           </ram:SellerTradeParty>
                           <ram:BuyerTradeParty>
                             <ram:Name>Theodor Est</ram:Name>
                             <ram:PostalTradeAddress><ram:PostcodeCode>88802</ram:PostcodeCode><ram:LineOne>Bahnstr. 42</ram:LineOne><ram:CityName>Spielkreis</ram:CityName><ram:CountryID>DE</ram:CountryID></ram:PostalTradeAddress>
                           </ram:BuyerTradeParty>
                         </ram:ApplicableHeaderTradeAgreement>
                         <ram:ApplicableHeaderTradeDelivery>
                           <ram:ActualDespatchSupplyChainEvent>
                             <ram:OccurrenceDateTime><udt:DateTimeString format="102">20170508</udt:DateTimeString></ram:OccurrenceDateTime>
                           </ram:ActualDespatchSupplyChainEvent>
                         </ram:ApplicableHeaderTradeDelivery>
                       </px:SupplyChainTradeTransaction>
                     </SCRDMCCBDACIDAMessageStructure>
                     """.strip()
                        .getBytes(StandardCharsets.UTF_8);
    // Strict reading names what the model does not carry.
    var error = assertThrows(IllegalArgumentException.class,
                             () -> PANIOLO.readDespatchAdvice(mustang));
    assertTrue(error.getMessage()
                    .contains("BuyerReference"),
               error::getMessage);
    var advice = PANIOLO.readDespatchAdvice(mustang,
                                            true);
    assertEquals("RE-20170509/505",
                 advice.number());
    assertEquals(LocalDate.of(2017,
                              5,
                              8),
                 advice.actualDespatchDate());
    assertEquals("Bei Spiel GmbH",
                 advice.despatchSupplier()
                       .name());
    // Without a ship-to party the buyer receives the goods.
    assertEquals(advice.buyer(),
                 advice.deliveryCustomer());
    var line = advice.lines()
                     .get(0);
    assertEquals(new BigDecimal("200.0000"),
                 line.deliveredQuantity());
    assertEquals("LTR",
                 line.unitCode()
                     .getCode());
    assertEquals(null,
                 line.orderLineId());
    assertTrue(PANIOLO.validateDespatchAdvice(mustang)
                      .isValid());
  }
}

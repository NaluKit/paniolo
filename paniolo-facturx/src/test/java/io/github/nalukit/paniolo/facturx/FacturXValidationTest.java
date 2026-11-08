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
package io.github.nalukit.paniolo.facturx;

import io.github.nalukit.paniolo.Paniolo;
import io.github.nalukit.paniolo.config.FacturXProfile;
import io.github.nalukit.paniolo.config.InvoiceSyntax;
import io.github.nalukit.paniolo.model.common.Payee;
import io.github.nalukit.paniolo.model.common.ProcessControl;
import io.github.nalukit.paniolo.model.common.value.InvoiceTypeCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Profile detection and validation of Factur-X documents without the official Factur-X rules.
 */
class FacturXValidationTest {
  private static final Paniolo              PANIOLO = Paniolo.create();
  private static final PdfBoxFacturXSupport SUPPORT = new PdfBoxFacturXSupport();

  static byte[] sample(String name) {
    try (InputStream input = FacturXValidationTest.class.getResourceAsStream("/facturx/" + name)) {
      return input.readAllBytes();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static List<String> rules(io.github.nalukit.paniolo.validation.ValidationResult result) {
    return result.messages()
                 .stream()
                 .map(io.github.nalukit.paniolo.validation.ValidationMessage::ruleId)
                 .toList();
  }

  @ParameterizedTest
  @CsvSource({ "urn:factur-x.eu:1p0:minimum, MINIMUM",
               "urn:factur-x.eu:1p0:basicwl, BASIC_WL",
               "urn:cen.eu:en16931:2017#compliant#urn:factur-x.eu:1p0:basic, BASIC",
               "urn:cen.eu:en16931:2017, EN16931",
               "urn:cen.eu:en16931:2017#conformant#urn:factur-x.eu:1p0:extended, EXTENDED",
               "urn:cen.eu:en16931:2017#compliant#urn:xeinkauf.de:kosit:xrechnung_3.0, XRECHNUNG",
               "urn:cen.eu:en16931:2017#compliant#urn:xoev-de:kosit:standard:xrechnung_2.3#conformant#urn:xeinkauf.de:kosit:extension:xrechnung_2.3, XRECHNUNG",
               "urn:zugferd.de:2p0:minimum, MINIMUM",
               "urn:zugferd.de:2p0:basicwl, BASIC_WL",
               "urn:cen.eu:en16931:2017#compliant#urn:zugferd.de:2p0:basic, BASIC",
               "urn:cen.eu:en16931:2017#conformant#urn:zugferd.de:2p0:extended, EXTENDED" })
  void detectsProfilesFromTheSpecificationIdentifier(String specificationId,
                                                     FacturXProfile profile) {
    assertEquals(profile,
                 FacturXProfile.ofSpecificationId(specificationId));
  }

  @Test
  void doesNotDetectUnknownIdentifiersOrSharedFileNames() {
    assertNull(FacturXProfile.ofSpecificationId("urn:fdc:peppol.eu:2017:poacc:billing:3.0"));
    assertNull(FacturXProfile.ofSpecificationId(null));
    assertNull(FacturXProfile.ofFileName("factur-x.xml"));
    assertEquals(FacturXProfile.XRECHNUNG,
                 FacturXProfile.ofFileName("xrechnung.xml"));
  }

  @Test
  void writesMinimumOnlyFromInvoicesWithoutLines() {
    var invoice = FacturXInvoices.invoice(FacturXProfile.EN16931,
                                          InvoiceTypeCode.CODE_380)
                                 .processControl(new ProcessControl(null,
                                                                    ProcessControl.FACTURX_MINIMUM))
                                 .build();
    assertThrows(IllegalArgumentException.class,
                 () -> PANIOLO.toFacturX(invoice,
                                         TestPdfs.pdfA2b(),
                                         FacturXProfile.MINIMUM));
    assertTrue(FacturXProfile.MINIMUM.isWritable());
    assertFalse(FacturXProfile.BASIC_WL.hasLines());
    assertTrue(FacturXProfile.EXTENDED.hasLines());
    assertEquals("Data",
                 FacturXProfile.BASIC_WL.getAfRelationship());
    assertEquals("BASIC-WL",
                 FacturXProfile.BASIC_WL.getRulesLevel());
  }

  @Test
  void standaloneBasicXmlIsCheckedAgainstItsOwnRulesNotTheGermanOnes() {
    var invoice = FacturXInvoices.invoice(FacturXProfile.BASIC,
                                          InvoiceTypeCode.CODE_380)
                                 .buyerReference(null)
                                 .build();
    String xml = PANIOLO.toXml(invoice,
                               InvoiceSyntax.CII);
    var result = PANIOLO.validate(xml);
    assertTrue(result.isValid(),
               () -> result.messages()
                           .toString());
    assertEquals("Factur-X BASIC",
                 result.profileName());
    // The ZUGFeRD 2.0 identifier of BASIC is recognised as well.
    var legacy = PANIOLO.validate(xml.replace(ProcessControl.FACTURX_BASIC,
                                              "urn:cen.eu:en16931:2017#compliant#urn:zugferd.de:2p0:basic")
                                     .getBytes(StandardCharsets.UTF_8));
    assertEquals("Factur-X BASIC",
                 legacy.profileName());
    // Plain EN 16931 standalone XML reports the XRechnung rules as information only.
    var en16931 = PANIOLO.validate(PANIOLO.toXml(FacturXInvoices.invoice(FacturXProfile.EN16931,
                                                                         InvoiceTypeCode.CODE_380)
                                                                .buyerReference(null)
                                                                .build(),
                                                 InvoiceSyntax.CII));
    assertTrue(en16931.isValid(),
               () -> en16931.messages()
                            .toString());
    assertTrue(rules(en16931).contains("BR-DE-15"),
               () -> en16931.messages()
                            .toString());
    assertTrue(rules(en16931).contains("XR-NOT-STATED"),
               () -> en16931.messages()
                            .toString());
  }

  @ParameterizedTest
  @EnumSource(value = FacturXProfile.class,
              names = {"BASIC", "EN16931", "EXTENDED"})
  void payeeIdentifierSchemesPassTheFacturXRulesAndReadBack(FacturXProfile profile) {
    var payee = Payee.create()
                     .name("Factoring AG")
                     .identifier("4000001000029",
                                 "0088")
                     .legalRegistrationId("123456782",
                                          "0002")
                     .build();
    var invoice = FacturXInvoices.invoice(profile,
                                          InvoiceTypeCode.CODE_380)
                                 .payee(payee)
                                 .build();
    var xml = PANIOLO.toBytes(invoice,
                              InvoiceSyntax.CII);
    var result = PANIOLO.validate(xml,
                                  profile);
    assertTrue(result.isValid(),
               () -> result.messages()
                           .toString());
    assertEquals(payee,
                 PANIOLO.read(xml)
                        .payee());
  }

  @Test
  void minimumAndBasicWlAreCheckedAgainstThePackagedRules() {
    for (String name : List.of("minimum.xml",
                               "basic-wl.xml")) {
      var result = PANIOLO.validate(sample(name));
      assertTrue(result.isValid(),
                 () -> result.messages()
                             .toString());
      assertFalse(rules(result).contains("FX-RULES-REQUIRED"),
                  () -> result.messages()
                              .toString());
      assertTrue(result.profileName()
                       .startsWith("Factur-X "),
                 result::profileName);
    }
  }

  @Test
  void validatesMinimumPdfWithoutContainerFindings() {
    byte[] pdf = SUPPORT.embed(TestPdfs.pdfA2b(),
                               sample("minimum.xml"),
                               FacturXProfile.MINIMUM);
    assertEquals(List.of(),
                 SUPPORT.checkContainer(pdf));
    var result = PANIOLO.validate(pdf);
    assertTrue(result.isValid(),
               () -> result.messages()
                           .toString());
    assertTrue(result.profileName()
                     .startsWith("Factur-X MINIMUM / Factur-X MINIMUM"),
               result::profileName);
    assertEquals(FacturXProfile.MINIMUM,
                 SUPPORT.extract(pdf)
                        .profile());
  }
}

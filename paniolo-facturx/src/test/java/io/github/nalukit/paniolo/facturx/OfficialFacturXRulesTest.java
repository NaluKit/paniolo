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
import io.github.nalukit.paniolo.model.common.value.InvoiceTypeCode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks generated documents against the official Factur-X schema and Schematron packaged in
 * paniolo-rules, or against the extracted FeRD / FNFE-MPE release named by
 * {@code PANIOLO_FACTURX_RULES}.
 */
class OfficialFacturXRulesTest {
  private static final Paniolo PANIOLO = System.getenv("PANIOLO_FACTURX_RULES") == null ?
                                         Paniolo.create() :
                                         Paniolo.builder()
                                                .facturXRules(Path.of(System.getenv("PANIOLO_FACTURX_RULES")))
                                                .build();

  @ParameterizedTest
  @CsvSource({ "BASIC, CODE_380, Factur-X BASIC",
               "BASIC, CODE_381, Factur-X BASIC",
               "EN16931, CODE_380, Factur-X EN16931",
               "EN16931, CODE_381, Factur-X EN16931",
               "EXTENDED, CODE_380, Factur-X EXTENDED",
               "EXTENDED, CODE_381, Factur-X EXTENDED",
               "XRECHNUNG, CODE_380, EN16931 XRechnung (CII)" })
  void generatedDocumentsPassTheOfficialRules(FacturXProfile profile,
                                              InvoiceTypeCode type,
                                              String rules) {
    var invoice = profile == FacturXProfile.EXTENDED ?
                  ExtendedTest.withPartyAndCurrency(ExtendedTest.withAdjustmentDetails(ExtendedTest.withSubLines(ExtendedTest.invoice(type))))
                              .build() :
                  FacturXInvoices.invoice(profile,
                                          type)
                                 .build();
    byte[] pdf = PANIOLO.toFacturX(invoice,
                                   TestPdfs.pdfA2b(),
                                   profile);
    var result = PANIOLO.validate(pdf);
    assertTrue(result.isValid(),
               () -> result.messages()
                           .toString());
    assertEquals("Factur-X " + profile.getConformanceLevel() + " / " + rules,
                 result.profileName());

    var xml = PANIOLO.validate(PANIOLO.extractXml(pdf),
                               profile);
    assertTrue(xml.isValid(),
               () -> xml.messages()
                        .toString());
    assertEquals(rules,
                 xml.profileName());
  }

  @ParameterizedTest
  @CsvSource({ "MINIMUM, Factur-X MINIMUM",
               "BASIC_WL, Factur-X BASIC-WL" })
  void generatedDocumentsWithoutLinesPassTheOfficialRules(FacturXProfile profile,
                                                          String rules) {
    var invoice = profile == FacturXProfile.MINIMUM ?
                  ProfilesWithoutLinesTest.minimum() :
                  ProfilesWithoutLinesTest.basicWl();
    var result = PANIOLO.validate(PANIOLO.toFacturX(invoice,
                                                    TestPdfs.pdfA2b(),
                                                    profile));
    assertTrue(result.isValid(),
               () -> result.messages()
                           .toString());
    assertEquals("Factur-X " + profile.getConformanceLevel() + " / " + rules,
                 result.profileName());
  }

  @ParameterizedTest
  @CsvSource({ "minimum.xml, MINIMUM, Factur-X MINIMUM",
               "basic-wl.xml, BASIC_WL, Factur-X BASIC-WL" })
  void validatesProfilesWithoutLines(String sample,
                                     FacturXProfile profile,
                                     String rules) {
    byte[] xml    = FacturXValidationTest.sample(sample);
    var    result = PANIOLO.validate(xml);
    assertTrue(result.isValid(),
               () -> result.messages()
                           .toString());
    assertEquals(rules,
                 result.profileName());
    var pdf = PANIOLO.validate(new PdfBoxFacturXSupport().embed(TestPdfs.pdfA2b(),
                                                                xml,
                                                                profile));
    assertTrue(pdf.isValid(),
               () -> pdf.messages()
                        .toString());
    assertEquals("Factur-X " + profile.getConformanceLevel() + " / " + rules,
                 pdf.profileName());
    // The rules find errors: an unknown document type code.
    var broken = PANIOLO.validate(new String(xml,
                                             StandardCharsets.UTF_8).replace("<ram:TypeCode>380</ram:TypeCode>",
                                                                             "<ram:TypeCode>999</ram:TypeCode>")
                                                                    .getBytes(StandardCharsets.UTF_8));
    assertTrue(!broken.isValid(),
               () -> broken.messages()
                           .toString());
  }

  @ParameterizedTest
  @CsvSource({ "basic.xml, , Factur-X BASIC",
               "comfort.xml, , Factur-X EN16931",
               "comfort.xml, EXTENDED, Factur-X EXTENDED" })
  void migratedZugferd1PassesTheOfficialRules(String sample,
                                              FacturXProfile target,
                                              String rules) {
    byte[] migrated = PANIOLO.migrateZugferd1(Zugferd1Test.sample(sample),
                                              target);
    var profile = FacturXProfile.ofSpecificationId(PANIOLO.read(migrated)
                                                          .processControl()
                                                          .specificationId());
    var result = PANIOLO.validate(migrated,
                                  profile);
    assertTrue(result.isValid(),
               () -> result.messages()
                           .toString());
    assertEquals(rules,
                 result.profileName());
  }

  @org.junit.jupiter.api.Test
  void directDebitWithPayerAccountPassesTheOfficialRules() {
    byte[] pdf = PANIOLO.toFacturX(ExtendedTest.directDebit(InvoiceTypeCode.CODE_380)
                                               .build(),
                                   TestPdfs.pdfA2b(),
                                   FacturXProfile.EXTENDED);
    var result = PANIOLO.validate(pdf);
    assertTrue(result.isValid(),
               () -> result.messages()
                           .toString());
  }
}

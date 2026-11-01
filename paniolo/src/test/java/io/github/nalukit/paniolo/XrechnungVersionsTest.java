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
import io.github.nalukit.paniolo.model.common.ProcessControl;
import io.github.nalukit.paniolo.model.common.value.InvoiceTypeCode;
import io.github.nalukit.paniolo.validation.Severity;
import io.github.nalukit.paniolo.validation.ValidationMessage;
import io.github.nalukit.paniolo.validation.ValidationResult;
import io.github.nalukit.paniolo.xml.ReadMode;
import io.github.nalukit.paniolo.xml.ReadWarning;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Documents of older XRechnung versions are validated against the packaged KoSIT release of the
 * version they state.
 */
class XrechnungVersionsTest {
  private static final Paniolo PANIOLO = Paniolo.create();

  private static String xml(InvoiceSyntax syntax,
                            InvoiceTypeCode type,
                            String version) {
    return PANIOLO.toXml(TestInvoices.invoice(type,
                                              "V-" + version),
                         syntax)
                  .replace(ProcessControl.XRECHNUNG_3,
                           "urn:cen.eu:en16931:2017#compliant#urn:xoev-de:kosit:standard:xrechnung_" + version);
  }

  private static boolean hasRule(ValidationResult result,
                                 String ruleId) {
    return result.messages()
                 .stream()
                 .map(ValidationMessage::ruleId)
                 .anyMatch(ruleId::equals);
  }

  @ParameterizedTest
  @CsvSource({ "UBL, CODE_380, 1.2, 1.2.2, EN16931 CIUS XRechnung (UBL Invoice)",
               "UBL, CODE_381, 2.0, 2.0.1, EN16931 XRechnung (UBL CreditNote)",
               "CII, CODE_380, 2.1, 2.1.1, EN16931 XRechnung (CII)",
               "UBL, CODE_380, 2.2, 2.2.0, EN16931 XRechnung (UBL Invoice)",
               "CII, CODE_381, 2.3, 2.3.1, EN16931 XRechnung (CII)" })
  void validatesAgainstTheRulesOfTheStatedVersion(InvoiceSyntax syntax,
                                                  InvoiceTypeCode type,
                                                  String version,
                                                  String release,
                                                  String scenario) {
    var result = PANIOLO.validate(xml(syntax,
                                      type,
                                      version));
    assertTrue(result.isValid(),
               () -> result.messages()
                           .toString());
    assertEquals(scenario + ", XRechnung " + release,
                 result.profileName());
    var older = result.messages()
                      .stream()
                      .filter(message -> "XR-OLDER-VERSION".equals(message.ruleId()))
                      .toList();
    assertEquals(1,
                 older.size(),
                 () -> result.messages()
                             .toString());
    assertEquals(Severity.INFO,
                 older.get(0)
                      .severity());
    // The rules of the version find errors, e.g. a missing buyer reference (BR-DE-15).
    var broken = PANIOLO.validate(xml(syntax,
                                      type,
                                      version).replaceAll("<cbc:BuyerReference>[^<]*</cbc:BuyerReference>|<ram:BuyerReference>[^<]*</ram:BuyerReference>",
                                                          ""));
    assertFalse(broken.isValid(),
                () -> broken.messages()
                            .toString());
    assertTrue(hasRule(broken,
                       "BR-DE-15"),
               () -> broken.messages()
                           .toString());
  }

  @Test
  void validatesTheExtensionOfAnOlderVersion() {
    String xml = PANIOLO.toXml(XrechnungExtensionTest.invoice()
                                                     .build(),
                               InvoiceSyntax.UBL)
                        .replace(ProcessControl.XRECHNUNG_3_EXTENSION,
                                 "urn:cen.eu:en16931:2017#compliant#urn:xoev-de:kosit:standard:xrechnung_2.3#conformant#urn:xoev-de:kosit:extension:xrechnung_2.3");
    var result = PANIOLO.validate(xml);
    assertEquals("EN16931 XRechnung Extension (UBL Invoice), XRechnung 2.3.1",
                 result.profileName());
    assertTrue(result.isValid(),
               () -> result.messages()
                           .toString());
  }

  @Test
  void validatesTheCurrentAndUnknownVersionsAgainstTheCurrentRules() {
    var current = PANIOLO.validate(PANIOLO.toXml(TestInvoices.invoice(InvoiceTypeCode.CODE_380,
                                                                      "V-3"),
                                                 InvoiceSyntax.UBL));
    assertEquals("EN16931 XRechnung (UBL Invoice)",
                 current.profileName());
    assertFalse(hasRule(current,
                        "XR-OLDER-VERSION"));
    // XRechnung 1.1 has no packaged release; the current rules reject its identifier.
    var unknown = PANIOLO.validate(xml(InvoiceSyntax.UBL,
                                       InvoiceTypeCode.CODE_380,
                                       "1.1"));
    assertEquals("EN16931 XRechnung (UBL Invoice)",
                 unknown.profileName());
    assertFalse(hasRule(unknown,
                        "XR-OLDER-VERSION"));
  }

  /**
   * EN 16931:2026 (XRechnung 4.0, BT-24 {@code urn:cen.eu:en16931:2026#...}) has no official
   * validation artefacts yet: Paniolo neither checks such a document against the rules of
   * EN 16931:2017 nor writes the syntax of EN 16931:2017 under its identifier, and says so when it
   * reads one (task R11, phase 0).
   */
  @ParameterizedTest
  @CsvSource({ "UBL, urn:cen.eu:en16931:2026#compliant#urn:xeinkauf.de:kosit:xrechnung_4.0",
               "CII, urn:cen.eu:en16931:2026#compliant#urn:xeinkauf.de:kosit:xrechnung_4.0",
               "CII, urn:cen.eu:en16931:2026" })
  void reportsEn16931Of2026AsNotSupported(InvoiceSyntax syntax,
                                          String specificationId) {
    String xml = PANIOLO.toXml(TestInvoices.invoice(InvoiceTypeCode.CODE_380,
                                                    "V-4"),
                               syntax)
                        .replace(ProcessControl.XRECHNUNG_3,
                                 specificationId);
    var result = PANIOLO.validate(xml);
    assertFalse(result.isValid());
    assertEquals("EN 16931:2026 (not checked)",
                 result.profileName());
    assertEquals(List.of("EN16931-2026-NOT-SUPPORTED"),
                 result.messages()
                       .stream()
                       .map(ValidationMessage::ruleId)
                       .toList());
    for (var mode : ReadMode.values()) {
      var read = PANIOLO.read(xml,
                              mode);
      assertEquals(List.of(ReadWarning.SPECIFICATION_VERSION),
                   read.warnings()
                       .stream()
                       .map(ReadWarning::code)
                       .toList());
      var error = assertThrows(IllegalArgumentException.class,
                               () -> PANIOLO.toXml(read.invoice(),
                                                   syntax));
      assertTrue(error.getMessage()
                      .contains("EN 16931:2026"),
                 error::getMessage);
    }
  }
}

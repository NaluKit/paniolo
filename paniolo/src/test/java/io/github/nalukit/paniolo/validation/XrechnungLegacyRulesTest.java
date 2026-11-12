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

import io.github.nalukit.paniolo.model.common.ProcessControl;
import io.github.nalukit.paniolo.model.common.value.InvoiceTypeCode;
import io.github.nalukit.paniolo.rules.xrechnung.XrechnungRules;
import io.github.nalukit.paniolo.xml.InvoiceXmlWriter;
import io.github.nalukit.paniolo.xml.cii.CiiInvoiceWriter;
import io.github.nalukit.paniolo.xml.ubl.UblInvoiceWriter;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The older KoSIT releases packaged in paniolo-rules load and check documents that state
 * their XRechnung version.
 */
class XrechnungLegacyRulesTest {
  static Stream<Arguments> versionsAndSyntaxes() {
    return XrechnungRules.LEGACY_VERSIONS.stream()
                                         .flatMap(version -> Stream.of(ValidationProfile.Syntax.values())
                                                                   .map(syntax -> Arguments.of(version,
                                                                                               syntax)));
  }

  /**
   * The specification identifier of an older XRechnung version.
   */
  static String specificationId(String version) {
    return "urn:cen.eu:en16931:2017#compliant#urn:xoev-de:kosit:standard:xrechnung_" + version;
  }

  private static byte[] document(String version,
                                 ValidationProfile.Syntax syntax) {
    var invoice = InvoiceSamples.invoice()
                                .typeCode(syntax == ValidationProfile.Syntax.UBL_CREDIT_NOTE ?
                                          InvoiceTypeCode.CODE_381 :
                                          InvoiceTypeCode.CODE_380)
                                .addLine(InvoiceSamples.line("1",
                                                             "100",
                                                             InvoiceSamples.standard()))
                                .build();
    InvoiceXmlWriter writer = syntax == ValidationProfile.Syntax.CII ?
                              new CiiInvoiceWriter() :
                              new UblInvoiceWriter();
    return new String(writer.toBytes(invoice),
                      StandardCharsets.UTF_8).replace(ProcessControl.XRECHNUNG_3,
                                                      specificationId(version))
                                             .getBytes(StandardCharsets.UTF_8);
  }

  @ParameterizedTest
  @MethodSource("versionsAndSyntaxes")
  void validatesDocumentsOfTheOlderVersion(String version,
                                           ValidationProfile.Syntax syntax) {
    var profile = ValidationProfile.xrechnung(XrechnungRules.legacyBundle(version),
                                              syntax);
    assertTrue(profile.name()
                      .contains("XRechnung"),
               profile::name);
    var validator = new InvoiceValidator(profile);
    var valid = validator.validate(document(version,
                                            syntax));
    assertTrue(valid.isValid(),
               () -> version + " " + syntax + ": " + valid.messages());
    // The rules find errors: an invoice without buyer reference (BR-DE-15).
    var invalid = validator.validate(new String(document(version,
                                                         syntax),
                                                StandardCharsets.UTF_8).replaceAll("<cbc:BuyerReference>[^<]*</cbc:BuyerReference>|<ram:BuyerReference>[^<]*</ram:BuyerReference>",
                                                                                   "")
                                                                       .getBytes(StandardCharsets.UTF_8));
    assertFalse(invalid.isValid(),
                () -> version + " " + syntax + ": " + invalid.messages());
  }
}

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
import io.github.nalukit.paniolo.model.common.value.InvoiceTypeCode;
import io.github.nalukit.paniolo.validation.ValidationProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cheap check whether XML is an electronic invoice; PDFs are covered in paniolo-facturx.
 */
class EInvoiceProbeTest {
  private static final Paniolo PANIOLO = Paniolo.create();

  @ParameterizedTest
  @CsvSource({ "UBL, CODE_380, UBL_INVOICE",
               "UBL, CODE_381, UBL_CREDIT_NOTE",
               "CII, CODE_380, CII" })
  void recognizesInvoiceXml(InvoiceSyntax syntax,
                            InvoiceTypeCode type,
                            ValidationProfile.Syntax expected) {
    byte[] xml = PANIOLO.toXml(TestInvoices.invoice(type,
                                                    "R-1"),
                               syntax)
                        .getBytes(StandardCharsets.UTF_8);

    assertTrue(PANIOLO.isEInvoice(xml));
    assertEquals(Optional.of(expected),
                 PANIOLO.probe(xml));
  }

  @ParameterizedTest
  @CsvSource({ "MINIMUM, false",
               "BASIC_WL, false",
               "BASIC, true",
               "EN16931, true",
               "EXTENDED, true",
               "XRECHNUNG, true" })
  void decidesCiiByProfile(FacturXProfile profile,
                           boolean expected) {
    byte[] xml = ciiWithSpecificationId(profile.getSpecificationId());

    assertEquals(expected,
                 PANIOLO.isEInvoice(xml));
  }

  @Test
  void acceptsCiiWithoutSpecificationId() {
    String xml = new String(ciiWithSpecificationId("X"),
                            StandardCharsets.UTF_8).replaceFirst("(?s)<ram:GuidelineSpecifiedDocumentContextParameter>.*?</ram:GuidelineSpecifiedDocumentContextParameter>",
                                                                 "");

    assertEquals(Optional.of(ValidationProfile.Syntax.CII),
                 PANIOLO.probe(xml.getBytes(StandardCharsets.UTF_8)));
  }

  private static byte[] ciiWithSpecificationId(String specificationId) {
    String xml = PANIOLO.toXml(TestInvoices.invoice(InvoiceTypeCode.CODE_380,
                                                    "R-1"),
                               InvoiceSyntax.CII);
    String replaced = xml.replaceFirst("(?s)(<ram:GuidelineSpecifiedDocumentContextParameter>\\s*<ram:ID>)[^<]*(</ram:ID>)",
                                       "$1" + specificationId + "$2");
    assertTrue(replaced.contains("<ram:ID>" + specificationId + "</ram:ID>"),
               "BT-24 not found in generated CII");
    return replaced.getBytes(StandardCharsets.UTF_8);
  }

  @ParameterizedTest
  @ValueSource(strings = { "",
                           "no xml at all",
                           "<Invoice>unclosed",
                           "<Invoice/>",
                           "<Order xmlns=\"urn:example\"/>",
                           "<rsm:CrossIndustryDocument xmlns:rsm=\"urn:ferd:CrossIndustryDocument:invoice:1p0\"/>",
                           "<!DOCTYPE x [<!ENTITY e SYSTEM \"file:///etc/passwd\">]><x>&e;</x>" })
  void rejectsOtherContentWithoutException(String content) {
    byte[] bytes = content.getBytes(StandardCharsets.UTF_8);

    assertFalse(PANIOLO.isEInvoice(bytes));
    assertEquals(Optional.empty(),
                 PANIOLO.probe(bytes));
  }

  @Test
  void explainsMissingFacturXModuleForPdf() {
    var error = assertThrows(IllegalStateException.class,
                             () -> PANIOLO.isEInvoice("%PDF-1.7".getBytes(StandardCharsets.US_ASCII)));
    assertTrue(error.getMessage()
                    .contains("paniolo-facturx"));
  }

  @Test
  void rejectsNull() {
    assertThrows(NullPointerException.class,
                 () -> PANIOLO.isEInvoice(null));
  }
}

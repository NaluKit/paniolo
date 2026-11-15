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
import io.github.nalukit.paniolo.model.common.ProcessControl;
import io.github.nalukit.paniolo.model.common.value.InvoiceTypeCode;
import io.github.nalukit.paniolo.validation.Severity;
import io.github.nalukit.paniolo.validation.ValidationMessage;
import io.github.nalukit.paniolo.validation.ValidationResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The XRechnung specification identifier check also applies to the invoice XML embedded in a
 * Factur-X / ZUGFeRD PDF with profile XRECHNUNG ({@code xrechnung.xml}), see Mustang issue 1343.
 */
class XrechnungPdfSpecificationIdTest {
  private static final Paniolo              PANIOLO     = Paniolo.create();
  private static final PdfBoxFacturXSupport SUPPORT     = new PdfBoxFacturXSupport();
  private static final String               UNSUPPORTED = "XR-SPECIFICATION-UNSUPPORTED";

  private static byte[] pdf(String specificationId) {
    String xml = PANIOLO.toXml(FacturXInvoices.invoice(FacturXProfile.XRECHNUNG,
                                                       InvoiceTypeCode.CODE_380)
                                              .build(),
                               InvoiceSyntax.CII);
    assertTrue(xml.contains(">" + ProcessControl.XRECHNUNG_3 + "<"));
    return SUPPORT.embed(TestPdfs.pdfA2b(),
                         xml.replace(">" + ProcessControl.XRECHNUNG_3 + "<",
                                     ">" + specificationId + "<")
                            .getBytes(StandardCharsets.UTF_8),
                         FacturXProfile.XRECHNUNG);
  }

  private static List<ValidationMessage> unsupported(ValidationResult result) {
    return result.messages()
                 .stream()
                 .filter(message -> UNSUPPORTED.equals(message.ruleId()))
                 .toList();
  }

  @Test
  void acceptsAnXrechnungPdfWithItsSupportedIdentifier() {
    byte[] pdf = pdf(ProcessControl.XRECHNUNG_3);
    assertEquals(FacturXProfile.XRECHNUNG,
                 SUPPORT.extract(pdf)
                        .profile());
    var result = PANIOLO.validate(pdf);
    assertTrue(result.isValid(),
               () -> result.messages()
                           .toString());
    assertEquals(List.of(),
                 unsupported(result));
  }

  @ParameterizedTest
  @ValueSource(strings = { "urn:cen.eu:en16931:2017#compliant#urn:xoev-de:kosit:standard:xrechnung_3.0",
                           "urn:cen.eu:en16931:2017#compliant#urn:xeinkauf.de:kosit:xrechnung_9.9",
                           "urn:cen.eu:en16931:2017#compliant#urn:xeinkauf.de:kosit:xrechnung_3.0#garbage" })
  void rejectsAnXrechnungPdfWithAnUnsupportedIdentifier(String specificationId) {
    var result = PANIOLO.validate(pdf(specificationId));
    assertFalse(result.isValid(),
                () -> result.messages()
                            .toString());
    var errors = unsupported(result);
    assertEquals(1,
                 errors.size(),
                 () -> result.messages()
                             .toString());
    assertEquals(Severity.ERROR,
                 errors.get(0)
                       .severity());
    assertTrue(errors.get(0)
                     .message()
                     .contains("'" + specificationId + "'"),
               () -> errors.get(0)
                           .message());
    assertTrue(result.toXml()
                     .contains("rule=\"" + UNSUPPORTED + "\""));
  }
}

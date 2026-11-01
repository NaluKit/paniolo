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
package io.github.nalukit.paniolo.pdfa;

import io.github.nalukit.paniolo.Paniolo;
import io.github.nalukit.paniolo.config.FacturXProfile;
import io.github.nalukit.paniolo.model.common.value.InvoiceTypeCode;
import io.github.nalukit.paniolo.validation.ValidationMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * With paniolo-pdfa on the class path the facade checks PDF/A, container and XML together.
 */
class PdfaValidationTest {
  private static final Paniolo         PANIOLO = Paniolo.create();
  private static final VeraPdfaSupport PDFA    = new VeraPdfaSupport();

  @ParameterizedTest
  @EnumSource(value = FacturXProfile.class,
              names = { "MINIMUM",
                        "BASIC_WL",
                        "EXTENDED_CTC_FR" },
              mode = EnumSource.Mode.EXCLUDE)
  void generatedFacturXPassesAllStages(FacturXProfile profile) {
    byte[] pdf = PANIOLO.toFacturX(FacturXInvoices.invoice(profile,
                                                           InvoiceTypeCode.CODE_380)
                                                  .build(),
                                   TestPdfs.pdfA2b(),
                                   profile);
    var result = PANIOLO.validate(pdf);
    assertTrue(result.isValid(),
               () -> result.messages()
                           .toString());
    assertFalse(result.hasWarnings(),
                () -> result.messages()
                            .toString());
    assertEquals(List.of(),
                 result.messages(ValidationMessage.Stage.PDF_A));
    // Only the information about the producing software.
    assertEquals(List.of("FX-PRODUCER"),
                 result.messages(ValidationMessage.Stage.HYBRID)
                       .stream()
                       .map(ValidationMessage::ruleId)
                       .toList());
  }

  @Test
  void reportsPdfaPartOtherThanThree() {
    var findings = PDFA.validate(TestPdfs.pdfA2b());
    assertEquals(List.of("PDFA-PART"),
                 findings.stream()
                         .map(ValidationMessage::ruleId)
                         .toList(),
                 findings::toString);
  }

  @Test
  void reportsDocumentsWithoutPdfaDeclaration() {
    var rules = PDFA.validate(TestPdfs.plainPdf())
                    .stream()
                    .map(ValidationMessage::ruleId)
                    .toList();
    assertTrue(rules.contains("PDFA-NOT-DECLARED"),
               rules::toString);
    // The checks against PDF/A-3b report the concrete violations, e.g. the missing output intent.
    assertTrue(rules.stream()
                    .anyMatch(rule -> rule.startsWith("PDFA-3B-")),
               rules::toString);
  }

  @Test
  void reportsUnreadableDocuments() {
    var findings = PDFA.validate("%PDF-1.7 broken".getBytes());
    assertTrue(findings.stream()
                       .anyMatch(message -> message.ruleId()
                                                   .equals("PDFA-PARSE")),
               findings::toString);
  }
}

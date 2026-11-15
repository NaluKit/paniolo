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
import io.github.nalukit.paniolo.validation.ValidationProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cheap check whether a PDF is a hybrid electronic invoice.
 */
class EInvoicePdfProbeTest {
  private static final Paniolo PANIOLO = Paniolo.create();

  @ParameterizedTest
  @EnumSource(value = FacturXProfile.class,
              names = { "BASIC", "EN16931", "EXTENDED", "XRECHNUNG" })
  void recognizesFacturXPdf(FacturXProfile profile) {
    byte[] pdf = PANIOLO.toFacturX(FacturXInvoices.invoice(profile,
                                                           InvoiceTypeCode.CODE_380)
                                                  .build(),
                                   TestPdfs.pdfA2b(),
                                   profile);

    assertTrue(PANIOLO.isEInvoice(pdf));
    assertEquals(Optional.of(ValidationProfile.Syntax.CII),
                 PANIOLO.probe(pdf));
  }

  @ParameterizedTest
  @EnumSource(value = FacturXProfile.class,
              names = { "MINIMUM", "BASIC_WL" })
  void rejectsProfilesWithoutInvoiceLines(FacturXProfile profile) {
    byte[] pdf = PANIOLO.toFacturX(profile == FacturXProfile.MINIMUM ?
                                   ProfilesWithoutLinesTest.minimum() :
                                   ProfilesWithoutLinesTest.basicWl(),
                                   TestPdfs.pdfA2b(),
                                   profile);

    assertFalse(PANIOLO.isEInvoice(pdf));
    assertEquals(Optional.empty(),
                 PANIOLO.probe(pdf));
  }

  @Test
  void rejectsPdfWithoutInvoice() {
    assertFalse(PANIOLO.isEInvoice(TestPdfs.plainPdf()));
    assertFalse(PANIOLO.isEInvoice(TestPdfs.pdfA2b()));
  }

  @Test
  void rejectsUnreadableAndEncryptedPdf() {
    assertFalse(PANIOLO.isEInvoice("%PDF-1.7 broken".getBytes(StandardCharsets.US_ASCII)));
    assertFalse(PANIOLO.isEInvoice(TestPdfs.encryptedPdf()));
  }

  @Test
  void rejectsZugferd1Pdf()
      throws Exception {
    assertFalse(PANIOLO.isEInvoice(Zugferd1Test.zugferd1Pdf(Zugferd1Test.sample("comfort.xml"))));
  }
}

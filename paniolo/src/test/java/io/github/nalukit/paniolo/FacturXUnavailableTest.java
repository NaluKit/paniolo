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
import io.github.nalukit.paniolo.model.common.value.InvoiceTypeCode;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * This module's tests run without paniolo-facturx on the class path.
 */
class FacturXUnavailableTest {
  private static final Paniolo PANIOLO = Paniolo.create();

  @Test
  void explainsMissingModule() {
    var pdf = "%PDF-1.7".getBytes(StandardCharsets.US_ASCII);
    var error = assertThrows(IllegalStateException.class,
                             () -> PANIOLO.read(pdf));
    assertTrue(error.getMessage()
                    .contains("paniolo-facturx"));
    var invoice = TestInvoices.invoice(InvoiceTypeCode.CODE_380,
                                       "R-1");
    // The profile check comes first: XRechnung invoices match the XRECHNUNG profile.
    var missing = assertThrows(IllegalStateException.class,
                               () -> PANIOLO.toFacturX(invoice,
                                                       pdf,
                                                       FacturXProfile.XRECHNUNG));
    assertTrue(missing.getMessage()
                      .contains("paniolo-facturx"));
    var layout = assertThrows(IllegalStateException.class,
                              () -> PANIOLO.toPdf(invoice));
    assertTrue(layout.getMessage()
                     .contains("paniolo-facturx"));
    var rendered = assertThrows(IllegalStateException.class,
                                () -> PANIOLO.toFacturX(invoice,
                                                        FacturXProfile.XRECHNUNG));
    assertTrue(rendered.getMessage()
                       .contains("paniolo-facturx"));
  }
}

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
import io.github.nalukit.paniolo.model.Invoice;
import io.github.nalukit.paniolo.model.common.InvoiceLine;
import io.github.nalukit.paniolo.model.common.ProcessControl;
import io.github.nalukit.paniolo.model.common.TaxCategory;
import io.github.nalukit.paniolo.model.common.VatBreakdown;
import io.github.nalukit.paniolo.model.common.value.InvoiceTypeCode;
import io.github.nalukit.paniolo.model.extended.LineExtension;
import io.github.nalukit.paniolo.model.extended.LineSubtype;
import io.github.nalukit.paniolo.validation.Severity;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An EXTENDED subtotal line groups lines with different VAT rates and states no VAT category of its
 * own (BR-FXEXT-CO-04); its amount counts once (Mustang issue #1275).
 */
class GroupLineWithoutTaxTest {
  private static final Paniolo     PANIOLO = Paniolo.create();
  private static final TaxCategory VAT_7   = TaxCategory.standard(new BigDecimal("7"));
  private static final TaxCategory VAT_19  = TaxCategory.standard(new BigDecimal("19"));

  private static InvoiceLine.Builder line(String id,
                                          String name,
                                          String price) {
    return InvoiceLine.create()
                      .id(id)
                      .name(name)
                      .quantity(BigDecimal.ONE)
                      .unitCode("H87")
                      .netUnitPrice(new BigDecimal(price));
  }

  private static Invoice bundle() {
    return TestInvoices.builder(InvoiceTypeCode.CODE_380,
                                "G-1")
                       .processControl(ProcessControl.facturXExtended())
                       .addLine(line("10",
                                     "Kaffee-Geschenkset",
                                     "30.00").extended(LineExtension.create()
                                                                    .subtype(LineSubtype.GROUP)
                                                                    .build())
                                             .build())
                       .addLine(line("10.1",
                                     "Kaffeebohnen 500 g",
                                     "12.00").tax(VAT_7)
                                             .extended(LineExtension.create()
                                                                    .parentLineId("10")
                                                                    .subtype(LineSubtype.DETAIL)
                                                                    .build())
                                             .build())
                       .addLine(line("10.2",
                                     "Kaffeetasse",
                                     "18.00").tax(VAT_19)
                                             .extended(LineExtension.create()
                                                                    .parentLineId("10")
                                                                    .subtype(LineSubtype.DETAIL)
                                                                    .build())
                                             .build())
                       .build();
  }

  private static VatBreakdown breakdown(Invoice invoice,
                                        String rate) {
    return invoice.totals()
                  .vatBreakdowns()
                  .stream()
                  .filter(each -> each.tax()
                                      .rate()
                                      .compareTo(new BigDecimal(rate)) == 0)
                  .findFirst()
                  .orElseThrow();
  }

  @Test
  void writesAndReadsAGroupLineWithoutVatCategory() {
    var invoice = bundle();
    // The base line 1 (200.00 at 19 %) and the two components; the group counts once.
    assertEquals(0,
                 new BigDecimal("230.00").compareTo(invoice.totals()
                                                           .lineTotal()));
    assertEquals(0,
                 new BigDecimal("12.00").compareTo(breakdown(invoice,
                                                             "7").taxableAmount()));
    assertEquals(0,
                 new BigDecimal("218.00").compareTo(breakdown(invoice,
                                                              "19").taxableAmount()));
    assertEquals(2,
                 invoice.totals()
                        .vatBreakdowns()
                        .size());

    byte[] xml = PANIOLO.toBytes(invoice,
                                 InvoiceSyntax.CII);
    var result = PANIOLO.validate(xml,
                                  FacturXProfile.EXTENDED);
    assertEquals(List.of(),
                 result.messages()
                       .stream()
                       .filter(message -> message.severity() == Severity.ERROR || message.severity() == Severity.FATAL)
                       .toList());
    var read = PANIOLO.read(xml);
    assertEquals(invoice,
                 read);
    assertNull(read.lines()
                   .get(1)
                   .tax());
    assertEquals(VAT_7,
                 read.lines()
                     .get(2)
                     .tax());
  }

  @Test
  void stillRequiresTheVatCategoryOfRegularLines() {
    assertThrows(NullPointerException.class,
                 () -> line("1",
                            "Beratung",
                            "100.00").build());
    assertThrows(NullPointerException.class,
                 () -> line("2",
                            "Teil",
                            "10.00").extended(LineExtension.create()
                                                           .parentLineId("1")
                                                           .subtype(LineSubtype.DETAIL)
                                                           .build())
                                    .build());
    assertTrue(line("3",
                    "Hinweis",
                    "0.00").extended(LineExtension.create()
                                                  .subtype(LineSubtype.INFORMATION)
                                                  .build())
                           .build()
                           .tax() == null);
  }
}

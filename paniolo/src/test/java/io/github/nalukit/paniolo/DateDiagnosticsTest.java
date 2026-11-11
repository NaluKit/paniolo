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
import io.github.nalukit.paniolo.model.Invoice;
import io.github.nalukit.paniolo.model.common.InvoiceLine;
import io.github.nalukit.paniolo.model.common.InvoicePeriod;
import io.github.nalukit.paniolo.model.common.TaxCategory;
import io.github.nalukit.paniolo.model.common.value.InvoiceTypeCode;
import io.github.nalukit.paniolo.xml.ReadMode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A date that does not match the syntax is rejected in both read modes with its text and the
 * location of its element, including the position among lines of the same name; valid and empty
 * dates are read as before.
 */
class DateDiagnosticsTest {
  private static final Paniolo PANIOLO = Paniolo.create();

  private static InvoiceLine line(String id,
                                  LocalDate start,
                                  LocalDate end) {
    return InvoiceLine.create()
                      .id(id)
                      .name("Wartung")
                      .quantity(new BigDecimal("1"))
                      .unitCode("HUR")
                      .netUnitPrice(new BigDecimal("80.00"))
                      .tax(TaxCategory.standard(new BigDecimal("19")))
                      .period(new InvoicePeriod(start,
                                                end))
                      .build();
  }

  private static Invoice invoice() {
    return TestInvoices.builder(InvoiceTypeCode.CODE_380,
                                "D-2")
                       .addLine(line("2",
                                     LocalDate.of(2026,
                                                  1,
                                                  1),
                                     LocalDate.of(2026,
                                                  1,
                                                  31)))
                       .addLine(line("3",
                                     LocalDate.of(2026,
                                                  2,
                                                  1),
                                     LocalDate.of(2026,
                                                  2,
                                                  28)))
                       .addLine(line("4",
                                     LocalDate.of(2026,
                                                  3,
                                                  1),
                                     LocalDate.of(2026,
                                                  3,
                                                  31)))
                       .build();
  }

  private static String replaced(String xml,
                                 String target,
                                 String replacement) {
    assertTrue(xml.contains(target),
               () -> "missing in the written XML: " + target);
    return xml.replace(target,
                       replacement);
  }

  private static void rejected(String xml,
                               String... parts) {
    for (var mode : ReadMode.values()) {
      var error = assertThrows(IllegalArgumentException.class,
                               () -> PANIOLO.read(xml,
                                                  mode));
      for (String part : parts) {
        assertTrue(error.getMessage()
                        .contains(part),
                   error::getMessage);
      }
    }
  }

  @Test
  void readsLinePeriodsOfManyLines() {
    for (var syntax : InvoiceSyntax.values()) {
      var lines = PANIOLO.read(PANIOLO.toXml(invoice(),
                                             syntax))
                         .lines();
      assertEquals(4,
                   lines.size());
      assertEquals(LocalDate.of(2026,
                                2,
                                1),
                   lines.get(2)
                        .period()
                        .startDate(),
                   syntax::name);
      assertEquals(LocalDate.of(2026,
                                3,
                                31),
                   lines.get(3)
                        .period()
                        .endDate(),
                   syntax::name);
    }
  }

  @Test
  void namesTextAndLocationOfAnInvalidIsoDate() {
    String ubl = PANIOLO.toXml(invoice(),
                               InvoiceSyntax.UBL);
    rejected(replaced(ubl,
                      "<cbc:StartDate>2026-02-01</cbc:StartDate>",
                      "<cbc:StartDate>2026-02-31</cbc:StartDate>"),
             "Invalid date \"2026-02-31\"",
             "cac:InvoiceLine[3]/cac:InvoicePeriod/cbc:StartDate");
    rejected(replaced(ubl,
                      "<cbc:EndDate>2026-03-31</cbc:EndDate>",
                      "<cbc:EndDate>31.03.2026</cbc:EndDate>"),
             "Invalid date \"31.03.2026\"",
             "cac:InvoiceLine[4]/cac:InvoicePeriod/cbc:EndDate");
  }

  @Test
  void namesTextAndLocationOfAnInvalidBasicDate() {
    String cii = PANIOLO.toXml(invoice(),
                               InvoiceSyntax.CII);
    String start = "ram:IncludedSupplyChainTradeLineItem[3]/ram:SpecifiedLineTradeSettlement/ram:BillingSpecifiedPeriod/ram:StartDateTime/udt:DateTimeString";
    rejected(replaced(cii,
                      "<udt:DateTimeString format=\"102\">20260201</udt:DateTimeString>",
                      "<udt:DateTimeString format=\"102\">20260231</udt:DateTimeString>"),
             "Invalid date \"20260231\"",
             start);
    rejected(replaced(cii,
                      "<udt:DateTimeString format=\"102\">20260201</udt:DateTimeString>",
                      "<udt:DateTimeString format=\"610\">202602</udt:DateTimeString>"),
             "Unsupported date format 610",
             start);
  }

  @Test
  void readsAnEmptyLineDateAsAbsent() {
    String cii = replaced(PANIOLO.toXml(invoice(),
                                        InvoiceSyntax.CII),
                          "<udt:DateTimeString format=\"102\">20260201</udt:DateTimeString>",
                          "<udt:DateTimeString format=\"102\"> </udt:DateTimeString>");
    assertNull(PANIOLO.read(cii,
                            ReadMode.LENIENT)
                      .invoice()
                      .lines()
                      .get(2)
                      .period()
                      .startDate());
  }
}

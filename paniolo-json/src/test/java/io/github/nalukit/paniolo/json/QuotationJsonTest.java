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
package io.github.nalukit.paniolo.json;

import io.github.nalukit.paniolo.model.common.Address;
import io.github.nalukit.paniolo.model.common.DocumentAdjustment;
import io.github.nalukit.paniolo.model.common.InvoiceLine;
import io.github.nalukit.paniolo.model.common.Party;
import io.github.nalukit.paniolo.model.common.TaxCategory;
import io.github.nalukit.paniolo.model.common.value.Country;
import io.github.nalukit.paniolo.model.quotation.Quotation;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuotationJsonTest {
  private static final PanioloJson JSON = PanioloJson.create();
  private static final TaxCategory VAT  = TaxCategory.standard(new BigDecimal("19"));

  private static Party party(String name) {
    return Party.create()
                .name(name)
                .address(Address.create()
                                .street("Weg 1")
                                .city("Berlin")
                                .postalCode("10115")
                                .country(Country.DE)
                                .build())
                .build();
  }

  private static InvoiceLine line(String id,
                                  String price) {
    return InvoiceLine.create()
                      .id(id)
                      .name("Beratung " + id)
                      .quantity(new BigDecimal("2"))
                      .unitCode("HUR")
                      .netUnitPrice(new BigDecimal(price))
                      .tax(VAT)
                      .build();
  }

  private static Quotation quotation() {
    return Quotation.create()
                    .number("ANG-7")
                    .issueDate(LocalDate.of(2026,
                                            11,
                                            2))
                    .validUntil(LocalDate.of(2026,
                                             12,
                                             2))
                    .seller(party("Muster GmbH"))
                    .buyer(party("Kunde GmbH"))
                    .buyerReference("ANF-12")
                    .subject("Beratung")
                    .deliveryTerms("Beginn in KW 48")
                    .paymentTerms("14 Tage netto")
                    .addNote("Preise freibleibend")
                    .addLine(line("1",
                                  "100"))
                    .addOptionalLine(line("2",
                                          "50"))
                    .addAdjustment(DocumentAdjustment.createAllowance()
                                                     .amount(new BigDecimal("10"))
                                                     .reason("Rabatt")
                                                     .tax(VAT)
                                                     .build())
                    .build();
  }

  @Test
  void storesAndLoadsAQuotation() {
    Quotation quotation = quotation();
    String    json      = JSON.toPrettyJson(quotation);
    Quotation loaded    = JSON.read(json,
                                    Quotation.class);
    assertEquals(quotation,
                 loaded);
    assertEquals(quotation.totals(),
                 loaded.totals());
    assertEquals(JSON.toJson(quotation),
                 JSON.toJson(loaded));
  }

  @Test
  void keepsTheOptionalFlag() {
    String json = JSON.toJson(quotation());
    assertTrue(json.contains("\"optional\":true"),
               json);
    var loaded = JSON.read(json,
                           Quotation.class);
    assertEquals(Set.of("2"),
                 Set.copyOf(loaded.lines()
                                  .stream()
                                  .filter(line -> line.optional())
                                  .map(line -> line.line()
                                                   .id())
                                  .toList()));
    assertFalse(loaded.lines()
                      .get(0)
                      .optional());
  }

  @Test
  void rejectsAQuotationThatViolatesTheModel() {
    String json = JSON.toJson(quotation())
                      .replace("\"validUntil\":\"2026-12-02\"",
                               "\"validUntil\":\"2026-10-01\"");
    var exception = assertThrows(IllegalArgumentException.class,
                                 () -> JSON.read(json,
                                                 Quotation.class));
    assertTrue(exception.getMessage()
                        .contains("validUntil"),
               exception.getMessage());
  }
}

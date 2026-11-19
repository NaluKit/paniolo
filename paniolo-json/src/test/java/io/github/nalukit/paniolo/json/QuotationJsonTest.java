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

  private static InvoiceLine boundary(String id,
                                      String priceBase) {
    return InvoiceLine.create()
                      .id(id)
                      .name("Grenzfall " + id)
                      .quantity(BigDecimal.ONE)
                      .unitCode("H87")
                      .netUnitPrice(BigDecimal.ONE)
                      .priceBaseQuantity(new BigDecimal(priceBase))
                      .tax(VAT)
                      .build();
  }

  /**
   * A line of 10.00, the half-cent line below the half cent (0.00), and an optional one at the half
   * cent (0.01), all at 19 % VAT. A quotation has no XML syntax; its invoice is written in the
   * invoice syntaxes (see LineRoundingDocumentTest), so the model, the invoice made from it, and the
   * JSON round trip are checked here against fixed values.
   */
  @Test
  void theHalfCentLineIsRoundedOnceInTheTotalsOfTheQuotationAndItsInvoice() {
    Quotation quotation = Quotation.create()
                                   .number("ANG-8")
                                   .issueDate(LocalDate.of(2026,
                                                           11,
                                                           2))
                                   .seller(party("Muster GmbH"))
                                   .buyer(party("Kunde GmbH"))
                                   .addLine(InvoiceLine.create()
                                                       .id("1")
                                                       .name("Beratung")
                                                       .quantity(BigDecimal.ONE)
                                                       .unitCode("HUR")
                                                       .netUnitPrice(new BigDecimal("10.00"))
                                                       .tax(VAT)
                                                       .build())
                                   .addLine(boundary("2",
                                                     "200.0000001"))
                                   .addOptionalLine(boundary("3",
                                                             "200"))
                                   .build();
    Quotation loaded = JSON.read(JSON.toJson(quotation),
                                 Quotation.class);

    for (Quotation q : new Quotation[] { quotation, loaded }) {
      assertEquals(new BigDecimal("0.00"),
                   q.lines()
                    .get(1)
                    .line()
                    .netAmount());
      assertEquals(new BigDecimal("0.01"),
                   q.lines()
                    .get(2)
                    .line()
                    .netAmount());
      // the standard offer: the optional line is not accepted
      assertTotals(q.totals(),
                   "10.00",
                   "1.90",
                   "11.90");
      assertTotals(q.toInvoice("R-1",
                               LocalDate.of(2026,
                                            11,
                                            20))
                    .totals(),
                   "10.00",
                   "1.90",
                   "11.90");
      // with the optional line the half cent is rounded up, to 0.01
      assertTotals(q.totals(Set.of("3")),
                   "10.01",
                   "1.90",
                   "11.91");
      assertTotals(q.toInvoice("R-2",
                               LocalDate.of(2026,
                                            11,
                                            20),
                               Set.of("3"))
                    .totals(),
                   "10.01",
                   "1.90",
                   "11.91");
    }
  }

  private static void assertTotals(io.github.nalukit.paniolo.model.common.InvoiceTotals totals,
                                   String net,
                                   String tax,
                                   String gross) {
    assertEquals(new BigDecimal(net),
                 totals.taxExclusiveAmount());
    assertEquals(new BigDecimal(tax),
                 totals.taxAmount());
    assertEquals(new BigDecimal(gross),
                 totals.taxInclusiveAmount());
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

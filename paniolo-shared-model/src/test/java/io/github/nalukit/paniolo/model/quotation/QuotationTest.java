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
package io.github.nalukit.paniolo.model.quotation;

import io.github.nalukit.paniolo.model.Invoice;
import io.github.nalukit.paniolo.model.common.Address;
import io.github.nalukit.paniolo.model.common.DocumentAdjustment;
import io.github.nalukit.paniolo.model.common.InvoiceLine;
import io.github.nalukit.paniolo.model.common.Party;
import io.github.nalukit.paniolo.model.common.TaxCategory;
import io.github.nalukit.paniolo.model.common.value.Country;
import io.github.nalukit.paniolo.model.common.value.InvoiceTypeCode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QuotationTest {
  private static final TaxCategory VAT   = TaxCategory.standard(new BigDecimal("19"));
  private static final LocalDate   ISSUE = LocalDate.of(2026,
                                                        11,
                                                        2);

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
                                  String quantity,
                                  String price) {
    return InvoiceLine.create()
                      .id(id)
                      .name("Beratung " + id)
                      .quantity(new BigDecimal(quantity))
                      .unitCode("HUR")
                      .netUnitPrice(new BigDecimal(price))
                      .tax(VAT)
                      .build();
  }

  private static Quotation.Builder quotation() {
    return Quotation.create()
                    .number("ANG-7")
                    .issueDate(ISSUE)
                    .validUntil(ISSUE.plusDays(30))
                    .seller(party("Muster GmbH"))
                    .buyer(party("Kunde GmbH"))
                    .buyerReference("ANF-12")
                    .paymentTerms("14 Tage netto")
                    .addNote("Preise freibleibend")
                    .addLine(line("1",
                                  "2",
                                  "100"))
                    .addOptionalLine(line("2",
                                          "1",
                                          "50"));
  }

  @Test
  void totalsCountOnlyTheStandardOffer() {
    var totals = quotation().build()
                            .totals();
    assertEquals(new BigDecimal("200.00"),
                 totals.taxExclusiveAmount());
    assertEquals(new BigDecimal("238.00"),
                 totals.taxInclusiveAmount());
  }

  @Test
  void totalsCountAcceptedOptionalLines() {
    var totals = quotation().build()
                            .totals(Set.of("2"));
    assertEquals(new BigDecimal("250.00"),
                 totals.taxExclusiveAmount());
    assertEquals(new BigDecimal("297.50"),
                 totals.taxInclusiveAmount());
  }

  @Test
  void createsTheInvoiceOfTheStandardOffer() {
    var quotation = quotation().addAdjustment(DocumentAdjustment.createAllowance()
                                                                .amount(new BigDecimal("10"))
                                                                .reason("Rabatt")
                                                                .tax(VAT)
                                                                .build())
                               .build();
    Invoice invoice = quotation.toInvoice("R-1",
                                          LocalDate.of(2026,
                                                       12,
                                                       1));
    assertEquals("R-1",
                 invoice.number());
    assertEquals(LocalDate.of(2026,
                              12,
                              1),
                 invoice.issueDate());
    assertEquals(InvoiceTypeCode.CODE_380,
                 invoice.typeCode());
    assertEquals(quotation.seller(),
                 invoice.seller());
    assertEquals(quotation.buyer(),
                 invoice.buyer());
    assertEquals("ANF-12",
                 invoice.buyerReference());
    assertEquals("14 Tage netto",
                 invoice.paymentTerms());
    assertEquals(quotation.notes(),
                 invoice.notes());
    assertEquals(List.of("1"),
                 invoice.lines()
                        .stream()
                        .map(InvoiceLine::id)
                        .toList());
    assertEquals(quotation.totals(),
                 invoice.totals());
    assertEquals(new BigDecimal("190.00"),
                 invoice.totals()
                        .taxExclusiveAmount());
  }

  @Test
  void createsTheInvoiceWithAcceptedOptionalLines() {
    var invoice = quotation().build()
                             .toInvoice("R-2",
                                        ISSUE,
                                        Set.of("2"));
    assertEquals(List.of("1",
                         "2"),
                 invoice.lines()
                        .stream()
                        .map(InvoiceLine::id)
                        .toList());
  }

  @Test
  void rejectsAcceptingALineThatIsNotOptional() {
    var quotation = quotation().build();
    assertThrows(IllegalArgumentException.class,
                 () -> quotation.toInvoice("R-3",
                                           ISSUE,
                                           Set.of("1")));
    assertThrows(IllegalArgumentException.class,
                 () -> quotation.toInvoice("R-3",
                                           ISSUE,
                                           Set.of("9")));
  }

  @Test
  void invoiceBuilderCanBeCompleted() {
    var quotation = quotation().build();
    var invoice = quotation.toInvoiceBuilder("R-4",
                                             ISSUE,
                                             Set.of())
                           .dueDate(ISSUE.plusDays(14))
                           .build();
    assertEquals(ISSUE.plusDays(14),
                 invoice.dueDate());
    assertEquals("ANG-7",
                 quotation.reference()
                          .id());
    assertEquals(ISSUE,
                 quotation.reference()
                          .issueDate());
  }

  @Test
  void rejectsInvalidQuotations() {
    assertThrows(IllegalArgumentException.class,
                 () -> quotation().number(" ")
                                  .build());
    assertThrows(NullPointerException.class,
                 () -> quotation().issueDate(null)
                                  .build());
    assertThrows(IllegalArgumentException.class,
                 () -> quotation().validUntil(ISSUE.minusDays(1))
                                  .build());
    assertThrows(IllegalArgumentException.class,
                 () -> quotation().addLine(line("1",
                                                "1",
                                                "10"))
                                  .build(),
                 "duplicate line id");
    assertThrows(IllegalArgumentException.class,
                 () -> Quotation.create()
                                .number("ANG-8")
                                .issueDate(ISSUE)
                                .seller(party("Muster GmbH"))
                                .buyer(party("Kunde GmbH"))
                                .addOptionalLine(line("1",
                                                      "1",
                                                      "10"))
                                .build(),
                 "only optional lines");
  }
}

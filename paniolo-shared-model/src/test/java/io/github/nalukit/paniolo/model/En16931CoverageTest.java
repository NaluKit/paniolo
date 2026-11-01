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
package io.github.nalukit.paniolo.model;

import io.github.nalukit.paniolo.model.common.Address;
import io.github.nalukit.paniolo.model.common.DocumentAdjustment;
import io.github.nalukit.paniolo.model.common.InvoiceLine;
import io.github.nalukit.paniolo.model.common.InvoiceNote;
import io.github.nalukit.paniolo.model.common.Party;
import io.github.nalukit.paniolo.model.common.TaxCategory;
import io.github.nalukit.paniolo.model.common.value.Country;
import io.github.nalukit.paniolo.model.common.value.InvoiceTypeCode;
import io.github.nalukit.paniolo.model.common.value.TextSubjectQualifier;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cases found in the official ZUGFeRD / Factur-X example invoices.
 */
class En16931CoverageTest {
  private static final TaxCategory VAT = TaxCategory.standard(new BigDecimal("19"));

  private static Invoice.Builder invoice(InvoiceTypeCode type) {
    var party = Party.create()
                     .name("Test GmbH")
                     .address(Address.create()
                                     .street("Weg 1")
                                     .city("Berlin")
                                     .postalCode("10115")
                                     .country(Country.DE)
                                     .build())
                     .build();
    return Invoice.create()
                  .number("R-1")
                  .typeCode(type)
                  .issueDate(LocalDate.of(2026,
                                          10,
                                          27))
                  .seller(party)
                  .buyer(party)
                  .addLine(InvoiceLine.create()
                                      .id("1")
                                      .name("Beratung")
                                      .quantity(BigDecimal.ONE)
                                      .unitCode("HUR")
                                      .netUnitPrice(new BigDecimal("100"))
                                      .tax(VAT)
                                      .build());
  }

  @Test
  void typeCodesAreExactlyTheEn16931List() {
    Set<String> codes = Arrays.stream(InvoiceTypeCode.values())
                              .map(InvoiceTypeCode::getCode)
                              .collect(Collectors.toSet());
    assertEquals(Set.of("71",
                        "80",
                        "81",
                        "82",
                        "83",
                        "84",
                        "102",
                        "130",
                        "202",
                        "203",
                        "204",
                        "211",
                        "218",
                        "219",
                        "261",
                        "262",
                        "295",
                        "296",
                        "308",
                        "325",
                        "326",
                        "331",
                        "380",
                        "381",
                        "382",
                        "383",
                        "384",
                        "385",
                        "386",
                        "387",
                        "388",
                        "389",
                        "390",
                        "393",
                        "394",
                        "395",
                        "396",
                        "420",
                        "456",
                        "457",
                        "458",
                        "471",
                        "472",
                        "473",
                        "500",
                        "501",
                        "502",
                        "503",
                        "527",
                        "532",
                        "553",
                        "575",
                        "623",
                        "633",
                        "751",
                        "780",
                        "817",
                        "870",
                        "875",
                        "876",
                        "877",
                        "935"),
                 codes);
    assertEquals("Insurer's invoice",
                 InvoiceTypeCode.CODE_575.getDescription());
    assertEquals("Final construction invoice",
                 InvoiceTypeCode.CODE_877.getDescription());
  }

  @Test
  void paymentMeansAndAllowanceCodesFollowTheEn16931Lists() {
    assertEquals(84,
                 io.github.nalukit.paniolo.model.common.value.PaymentMeansCode.values().length);
    assertEquals("ACH savings corporate trade payment (CTP) credit",
                 io.github.nalukit.paniolo.model.common.value.PaymentMeansCode.CODE_37.getDescription());
    assertEquals("Bill drawn by the creditor on the debtor",
                 io.github.nalukit.paniolo.model.common.value.PaymentMeansCode.CODE_70.getDescription());
    assertEquals("SEPA credit transfer",
                 io.github.nalukit.paniolo.model.common.value.PaymentMeansCode.CODE_58.getDescription());
    assertEquals(Set.of("41",
                        "42",
                        "60",
                        "62",
                        "63",
                        "64",
                        "65",
                        "66",
                        "67",
                        "68",
                        "70",
                        "71",
                        "88",
                        "95",
                        "100",
                        "102",
                        "103",
                        "104",
                        "105"),
                 Arrays.stream(io.github.nalukit.paniolo.model.common.value.AllowanceChargeIdentification.values())
                       .map(io.github.nalukit.paniolo.model.common.value.AllowanceChargeIdentification::getCode)
                       .collect(Collectors.toSet()));
    assertEquals("Material surcharge/deduction",
                 io.github.nalukit.paniolo.model.common.value.AllowanceChargeIdentification.CODE_88.getName());
  }

  @Test
  void creditNoteAndXrechnungClassification() {
    for (var code : new InvoiceTypeCode[] { InvoiceTypeCode.CODE_381,
                                            InvoiceTypeCode.CODE_261,
                                            InvoiceTypeCode.CODE_396 }) {
      assertTrue(code.isCreditNote(),
                 code::getCode);
      assertFalse(code.isInvoice(),
                  code::getCode);
    }
    assertFalse(InvoiceTypeCode.CODE_575.isCreditNote());
    assertTrue(InvoiceTypeCode.CODE_384.isInvoice());
    assertTrue(InvoiceTypeCode.CODE_384.isXrechnungAllowed());
    assertFalse(InvoiceTypeCode.CODE_575.isXrechnungAllowed());
  }

  @Test
  void acceptsAllEn16931TypeCodes() {
    for (var code : InvoiceTypeCode.values()) {
      assertEquals(code,
                   invoice(code).build()
                                .typeCode());
    }
  }

  @Test
  void allowsNegativeAllowancesInCorrections() {
    var built = invoice(InvoiceTypeCode.CODE_384).addAdjustment(DocumentAdjustment.createAllowance()
                                                                                  .amount(new BigDecimal("-0.10"))
                                                                                  .reason("Korrektur")
                                                                                  .tax(VAT)
                                                                                  .build())
                                                 .build();
    assertEquals(new BigDecimal("-0.10"),
                 built.totals()
                      .allowanceTotal());
    assertEquals(new BigDecimal("100.10"),
                 built.totals()
                      .taxExclusiveAmount());
  }

  @Test
  void keepsNoteSubjectCodesOutsideTheEnum() {
    var note = InvoiceNote.create()
                          .subjectCode("ADU")
                          .text("Hinweis")
                          .build();
    assertEquals("ADU",
                 note.subjectCode());
    assertNull(note.subjectQualifier());
    assertEquals(TextSubjectQualifier.REG,
                 InvoiceNote.create()
                            .subjectCode(TextSubjectQualifier.REG)
                            .text("HRB 1")
                            .build()
                            .subjectQualifier());
    assertThrows(IllegalArgumentException.class,
                 () -> new InvoiceNote("adu!",
                                       "Hinweis"));
  }

  @Test
  void statedLineNetAmountCoversARoundedNetPrice() {
    // 20 x 2.773 = 55.46, the document shows the net price rounded to 2.77.
    var line = InvoiceLine.create()
                          .id("4")
                          .name("Artikel")
                          .quantity(new BigDecimal("20"))
                          .unitCode("H87")
                          .netUnitPrice(new BigDecimal("2.77"))
                          .tax(VAT)
                          .netAmount(new BigDecimal("55.46"))
                          .build();
    assertEquals(new BigDecimal("55.46"),
                 line.netAmount());
    var plain = InvoiceLine.create()
                           .id("4")
                           .name("Artikel")
                           .quantity(new BigDecimal("20"))
                           .unitCode("H87")
                           .netUnitPrice(new BigDecimal("2.77"))
                           .tax(VAT)
                           .build();
    assertEquals(new BigDecimal("55.40"),
                 plain.netAmount());
    assertSame(plain,
               plain.withNetAmount(new BigDecimal("55.40")));
    assertEquals(line,
                 plain.withNetAmount(new BigDecimal("55.46")));
    // A gross amount including insurance tax is not a rounding difference.
    assertThrows(IllegalArgumentException.class,
                 () -> plain.withNetAmount(new BigDecimal("64.45")));
  }
}

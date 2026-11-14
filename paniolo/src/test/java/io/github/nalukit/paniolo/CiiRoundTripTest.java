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
import io.github.nalukit.paniolo.model.common.CreditTransfer;
import io.github.nalukit.paniolo.model.common.DirectDebit;
import io.github.nalukit.paniolo.model.common.PaymentInstruction;
import io.github.nalukit.paniolo.model.common.value.CurrencyCode;
import io.github.nalukit.paniolo.model.common.value.InvoiceTypeCode;
import io.github.nalukit.paniolo.model.common.value.PaymentMeansCode;
import io.github.nalukit.paniolo.xml.ReadMode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The CII writer writes only what the CII reader merges again: several accounts with a mandate,
 * with and without debited account, and the same currency for invoice and VAT accounting.
 */
class CiiRoundTripTest {
  private static final Paniolo        PANIOLO = Paniolo.create();
  private static final CreditTransfer FIRST   = new CreditTransfer("DK1212341234123412",
                                                                   null,
                                                                   null);
  private static final CreditTransfer SECOND  = new CreditTransfer("A",
                                                                   "Second",
                                                                   "COBADEFFXXX");

  private static PaymentInstruction roundTrip(DirectDebit debit) {
    return roundTrip(debit,
                     InvoiceSyntax.CII);
  }

  private static PaymentInstruction roundTrip(DirectDebit debit,
                                              InvoiceSyntax syntax) {
    var builder = PaymentInstruction.create()
                                    .meansCode(PaymentMeansCode.CODE_58)
                                    .addCreditTransfer(FIRST)
                                    .addCreditTransfer(SECOND)
                                    .remittanceInformation("Z-1");
    if (debit != null) {
      builder.directDebit(debit);
    }
    var invoice = TestInvoices.builder(InvoiceTypeCode.CODE_380,
                                       "Z-1")
                              .paymentInstruction(builder.build())
                              .build();
    String xml  = PANIOLO.toXml(invoice,
                                syntax);
    var    read = PANIOLO.read(xml,
                               ReadMode.STRICT);
    assertEquals(List.of(),
                 read.warnings());
    return read.invoice()
               .paymentInstruction();
  }

  @Test
  void keepsBothAccountsWithAMandateOnly() {
    var payment = roundTrip(new DirectDebit("MANDATE-1",
                                            null,
                                            null));
    assertEquals(List.of(FIRST,
                         SECOND),
                 payment.creditTransfers());
    assertEquals("MANDATE-1",
                 payment.directDebit()
                        .mandateReference());
    assertEquals(null,
                 payment.directDebit()
                        .debitedAccountId());
  }

  @Test
  void keepsBothAccountsWithADebitedAccount() {
    var payment = roundTrip(new DirectDebit("MANDATE-1",
                                            "DE98ZZZ09999999999",
                                            "DE89370400440532013000"));
    assertEquals(List.of(FIRST,
                         SECOND),
                 payment.creditTransfers());
    assertEquals("DE89370400440532013000",
                 payment.directDebit()
                        .debitedAccountId());
    assertEquals("DE98ZZZ09999999999",
                 payment.directDebit()
                        .creditorId());
  }

  @Test
  void ublKeepsBothAccountsWithAMandateOnly() {
    var payment = roundTrip(new DirectDebit("MANDATE-1",
                                            null,
                                            null),
                            InvoiceSyntax.UBL);
    assertEquals(List.of(FIRST,
                         SECOND),
                 payment.creditTransfers());
    assertEquals("MANDATE-1",
                 payment.directDebit()
                        .mandateReference());
  }

  @Test
  void keepsBothAccountsWithoutDirectDebit() {
    assertEquals(List.of(FIRST,
                         SECOND),
                 roundTrip(null).creditTransfers());
  }

  @Test
  void sameCurrencyForInvoiceAndVatAccountingExportsValidInBothSyntaxes() {
    var invoice = TestInvoices.builder(InvoiceTypeCode.CODE_380,
                                       "Z-2")
                              .taxCurrencyCode(CurrencyCode.EUR)
                              .taxAmountInTaxCurrency(new BigDecimal("38.00"))
                              .build();
    for (var syntax : List.of(InvoiceSyntax.CII,
                              InvoiceSyntax.UBL)) {
      String xml = PANIOLO.toXml(invoice,
                                 syntax);
      var result = PANIOLO.validate(xml);
      assertTrue(result.isValid(),
                 () -> syntax + ": " + result.messages());
      // One VAT total of BT-110; the redundant BT-6 is dropped, the amounts stay.
      assertEquals(1,
                   xml.split("TaxTotalAmount>|<cac:TaxTotal>",
                             -1).length / 2);
      var read = PANIOLO.read(xml,
                              ReadMode.STRICT).invoice();
      assertEquals(new BigDecimal("38.00"),
                   read.totals()
                       .taxAmount());
      assertEquals(null,
                   read.taxCurrencyCode());
    }
  }

  @Test
  void aDifferentVatAccountingCurrencyStays() {
    var invoice = TestInvoices.builder(InvoiceTypeCode.CODE_380,
                                       "Z-3")
                              .taxCurrencyCode(CurrencyCode.SEK)
                              .taxAmountInTaxCurrency(new BigDecimal("430.00"))
                              .build();
    for (var syntax : List.of(InvoiceSyntax.CII,
                              InvoiceSyntax.UBL)) {
      String xml  = PANIOLO.toXml(invoice,
                                  syntax);
      var    read = PANIOLO.read(xml,
                                 ReadMode.STRICT).invoice();
      assertEquals(CurrencyCode.SEK,
                   read.taxCurrencyCode());
      assertEquals(new BigDecimal("430.00"),
                   read.taxAmountInTaxCurrency());
    }
  }
}

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
import io.github.nalukit.paniolo.config.InvoiceSyntax;
import io.github.nalukit.paniolo.model.common.Party;
import io.github.nalukit.paniolo.model.common.value.Country;
import io.github.nalukit.paniolo.model.common.value.InvoiceTypeCode;
import io.github.nalukit.paniolo.model.common.value.PaymentMeansCode;
import io.github.nalukit.paniolo.xml.ReadMode;
import io.github.nalukit.paniolo.xml.ReadResult;
import io.github.nalukit.paniolo.xml.ReadWarning;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Received documents that EN 16931 and the official validators accept, or that lack a value, and
 * that the model could not read before: an exempt VAT breakdown with a rate other than zero, an
 * {@code xs:date} with time zone, ISO 3166 codes outside the sovereign states (e.g. Réunion), payment
 * means without code, a seller tax registration in another scheme than {@code FC}, ZUGFeRD 1.0
 * direct debits, invalid invoicing periods, and missing required values that ended in a
 * {@link NullPointerException}. The cases come from the comparison run of 2026-11-16 (Paniolo and
 * Mustang on 2,355 public sample documents).
 */
class ReceivedDocumentToleranceTest {
  private static final Paniolo PANIOLO = Paniolo.create();

  private static String ubl() {
    return PANIOLO.toXml(FacturXInvoices.invoice(FacturXProfile.EN16931,
                                                 InvoiceTypeCode.CODE_380)
                                        .build(),
                         InvoiceSyntax.UBL);
  }

  private static String comfort() {
    return new String(Zugferd1Test.sample("comfort.xml"),
                      StandardCharsets.UTF_8);
  }

  private static ReadResult read(String xml,
                                 ReadMode mode) {
    return PANIOLO.read(xml.getBytes(StandardCharsets.UTF_8),
                        mode);
  }

  private static boolean warns(ReadResult result,
                               String code) {
    return result.warnings()
                 .stream()
                 .anyMatch(warning -> code.equals(warning.code()));
  }

  private static String replaceOnce(String xml,
                                    String regex,
                                    String replacement) {
    String changed = xml.replaceFirst(regex,
                                      replacement);
    assertTrue(!changed.equals(xml),
               () -> "not found: " + regex);
    return changed;
  }

  /**
   * The XRechnung test documents state an exempt VAT breakdown (E) with 19 % and VAT amount zero,
   * which no EN 16931 rule forbids for the breakdown (INV-6c5b8a7670a9149f and 98 more).
   */
  @Test
  void exemptBreakdownWithRateIsReadLenientlyWithRateZero() {
    String xml = replaceOnce(ubl(),
                             "(?s)(<cac:TaxSubtotal>.*?</cac:TaxSubtotal>)",
                             "$1<cac:TaxSubtotal><cbc:TaxableAmount currencyID=\"EUR\">0</cbc:TaxableAmount><cbc:TaxAmount currencyID=\"EUR\">0</cbc:TaxAmount>" +
                             "<cac:TaxCategory><cbc:ID>E</cbc:ID><cbc:Percent>19</cbc:Percent><cbc:TaxExemptionReason>a reason</cbc:TaxExemptionReason>" +
                             "<cac:TaxScheme><cbc:ID>VAT</cbc:ID></cac:TaxScheme></cac:TaxCategory></cac:TaxSubtotal>");
    var strict = assertThrows(IllegalArgumentException.class,
                              () -> read(xml,
                                         ReadMode.STRICT));
    assertTrue(strict.getMessage()
                     .startsWith("VAT category E at /Invoice/cac:TaxTotal/cac:TaxSubtotal[2]/cac:TaxCategory states the VAT rate 19"),
               strict.getMessage());
    var lenient = read(xml,
                       ReadMode.LENIENT);
    assertTrue(warns(lenient,
                     ReadWarning.TAX_RATE),
               lenient.warnings()
                      .toString());
    assertEquals(new BigDecimal("200.00"),
                 lenient.invoice()
                        .totals()
                        .taxExclusiveAmount());
  }

  /**
   * A VAT block of a zero category without rate, e.g. after removing BT-119 (BR-DE-14 tests), ended
   * in {@code NullPointerException: tax rate}.
   */
  @Test
  void zeroCategoryWithoutRateIsNoNullPointer() {
    String xml = comfort().replace("<ram:CategoryCode>S</ram:CategoryCode>\n        <ram:ApplicablePercent>19.00</ram:ApplicablePercent>\n      </ram:ApplicableTradeTax>",
                                   "<ram:CategoryCode>S</ram:CategoryCode>\n        <ram:ApplicablePercent>19.00</ram:ApplicablePercent>\n      </ram:ApplicableTradeTax>" +
                                   "<ram:ApplicableTradeTax><ram:CalculatedAmount currencyID=\"EUR\">0</ram:CalculatedAmount><ram:TypeCode>VAT</ram:TypeCode>" +
                                   "<ram:BasisAmount currencyID=\"EUR\">0</ram:BasisAmount><ram:CategoryCode>Z</ram:CategoryCode></ram:ApplicableTradeTax>");
    assertTrue(xml.contains("<ram:CategoryCode>Z</ram:CategoryCode>"));
    var strict = assertThrows(IllegalArgumentException.class,
                              () -> read(xml,
                                         ReadMode.STRICT));
    assertTrue(strict.getMessage()
                     .contains("VAT category Z at ") && strict.getMessage()
                                                               .contains("states no VAT rate"),
               strict.getMessage());
    assertTrue(warns(read(xml,
                          ReadMode.LENIENT),
                     ReadWarning.TAX_RATE));
  }

  /**
   * {@code xs:date} allows a time zone, e.g. {@code 2016-04-04+01:00} in the KoSIT business case
   * 01.01a (INV-0f5439b43c1dcb48).
   */
  @Test
  void isoDateWithTimeZone() {
    String xml = replaceOnce(ubl(),
                             "<cbc:IssueDate>2026-10-27</cbc:IssueDate>",
                             "<cbc:IssueDate>2026-10-27+01:00</cbc:IssueDate>");
    assertEquals(LocalDate.of(2026,
                              10,
                              27),
                 read(xml,
                      ReadMode.STRICT).invoice()
                                      .issueDate());
  }

  /**
   * The French overseas departments (Facture_DOM examples, INV-1e6596b80c2bb7ec) state the country
   * RE, which is ISO 3166-1 but was missing from the model.
   */
  @Test
  void countriesOfIso3166AndXi() {
    for (String code : new String[] { "RE",
                                      "GP",
                                      "MQ",
                                      "GF",
                                      "YT",
                                      "XI" }) {
      assertNotNull(Country.of(code),
                    code);
    }
    assertEquals(Country.values().length,
                 java.util.Arrays.stream(java.util.Locale.getISOCountries())
                                 .filter(code -> Country.of(code) != null)
                                 .count() + 1,
                 "every ISO 3166-1 code and XI");
    String xml = ubl().replaceFirst("(?s)(<cac:AccountingCustomerParty>.*?<cbc:IdentificationCode>)DE(</cbc:IdentificationCode>)",
                                    "$1RE$2");
    assertEquals(Country.RE,
                 read(xml,
                      ReadMode.STRICT).invoice()
                                      .buyer()
                                      .address()
                                      .country());
  }

  /**
   * A payment means without code: ZUGFeRD 1.0 allows it (ZUGFeRD_1p0_BASIC_Einfach,
   * INV-2cea9bb8ffea4031), and it ended in {@code NullPointerException: meansCode}. It is read as 1
   * (instrument not defined); in UBL, where the code is required, strict reading rejects it.
   */
  @Test
  void paymentMeansWithoutCode() {
    String zugferd1 = replaceOnce(comfort(),
                                  "\\s*<ram:TypeCode>58</ram:TypeCode>",
                                  "");
    for (var mode : ReadMode.values()) {
      var result = read(zugferd1,
                        mode);
      assertEquals(PaymentMeansCode.CODE_1,
                   result.invoice()
                         .paymentInstruction()
                         .meansCode());
      assertTrue(warns(result,
                       ReadWarning.PAYMENT_MEANS));
    }
    String ubl = replaceOnce(ubl(),
                             "<cbc:PaymentMeansCode[^>]*>58</cbc:PaymentMeansCode>",
                             "");
    var strict = assertThrows(IllegalArgumentException.class,
                              () -> read(ubl,
                                         ReadMode.STRICT));
    assertTrue(strict.getMessage()
                     .contains("states no payment means code (BT-81)"),
               strict.getMessage());
    assertEquals(PaymentMeansCode.CODE_1,
                 read(ubl,
                      ReadMode.LENIENT).invoice()
                                       .paymentInstruction()
                                       .meansCode());
  }

  /**
   * EN 16931 binds the seller tax registration (BT-32) to every tax scheme other than VAT, e.g.
   * {@code TAX} of PEPPOL BIS (Swedish "Godkänd för F-skatt", INV-f213a95021d37339).
   */
  @Test
  void sellerTaxRegistrationOfAnotherScheme() {
    var invoice = FacturXInvoices.invoice(FacturXProfile.EN16931,
                                          InvoiceTypeCode.CODE_380);
    var seller = invoice.build()
                        .seller();
    String xml = PANIOLO.toXml(invoice.seller(Party.create()
                                                   .name(seller.name())
                                                   .address(seller.address())
                                                   .vatId(seller.vatId())
                                                   .taxId("Godkänd för F-skatt")
                                                   .electronicAddress(seller.electronicAddress())
                                                   .contact(seller.contact())
                                                   .build())
                                      .build(),
                               InvoiceSyntax.UBL);
    String tax = replaceOnce(xml,
                             "<cbc:ID>FC</cbc:ID>",
                             "<cbc:ID>TAX</cbc:ID>");
    assertEquals("Godkänd för F-skatt",
                 read(tax,
                      ReadMode.STRICT).invoice()
                                      .seller()
                                      .taxId());
  }

  /**
   * ZUGFeRD 1.0 states the mandate reference as identifier of the payment means and the creditor
   * identifier as its scheme agency (SEPA prenotification sample, INV-7b652b0bb3b2166b); the reader
   * rejected every direct debit before.
   */
  @Test
  void zugferd1DirectDebitIsMigrated() {
    String xml = replaceOnce(comfort(),
                             "(?s)<ram:SpecifiedTradeSettlementPaymentMeans>.*?</ram:SpecifiedTradeSettlementPaymentMeans>",
                             "<ram:SpecifiedTradeSettlementPaymentMeans><ram:TypeCode>49</ram:TypeCode><ram:Information>SEPA-Lastschrift</ram:Information>" +
                             "<ram:ID schemeAgencyID=\"DE98ZZZ09999999999\">REF A-123</ram:ID><ram:PayerPartyDebtorFinancialAccount>" +
                             "<ram:IBANID>DE21860000000086001055</ram:IBANID></ram:PayerPartyDebtorFinancialAccount></ram:SpecifiedTradeSettlementPaymentMeans>");
    var debit = read(xml,
                     ReadMode.STRICT).invoice()
                                     .paymentInstruction()
                                     .directDebit();
    assertEquals("REF A-123",
                 debit.mandateReference());
    assertEquals("DE98ZZZ09999999999",
                 debit.creditorId());
    assertEquals("DE21860000000086001055",
                 debit.debitedAccountId());
  }

  /**
   * The ZUGFeRD 1.0 sample EXTENDED_Kostenrechnung states the start date 20139102
   * (INV-47460b85aebba419): strict reading rejects it, lenient reading drops the period.
   */
  @Test
  void invalidInvoicingPeriodIsDroppedLeniently() {
    String xml = replaceOnce(comfort(),
                             "(<ram:InvoiceCurrencyCode>EUR</ram:InvoiceCurrencyCode>)",
                             "$1<ram:BillingSpecifiedPeriod><ram:StartDateTime><udt:DateTimeString format=\"102\">20139102</udt:DateTimeString></ram:StartDateTime>" +
                             "<ram:EndDateTime><udt:DateTimeString format=\"102\">20130930</udt:DateTimeString></ram:EndDateTime></ram:BillingSpecifiedPeriod>");
    assertThrows(IllegalArgumentException.class,
                 () -> read(xml,
                            ReadMode.STRICT));
    var lenient = read(xml,
                       ReadMode.LENIENT);
    assertNull(lenient.invoice()
                      .invoicePeriod());
    assertTrue(warns(lenient,
                     ReadWarning.PERIOD));
  }

  /**
   * A required value the document lacks is reported as {@link IllegalArgumentException} that names
   * it, not as {@link NullPointerException} (PEPPOL R040 tests without allowance amount,
   * INV-1203ccba0dc2b3a6).
   */
  @Test
  void missingRequiredValueIsNoNullPointer() {
    String xml = replaceOnce(comfort(),
                             "\\s*<ram:ActualAmount currencyID=\"EUR\">20.00</ram:ActualAmount>",
                             "");
    for (var mode : ReadMode.values()) {
      var error = assertThrows(IllegalArgumentException.class,
                               () -> read(xml,
                                          mode));
      assertEquals("The document lacks a value the invoice model requires: adjustment amount",
                   error.getMessage());
    }
  }
}

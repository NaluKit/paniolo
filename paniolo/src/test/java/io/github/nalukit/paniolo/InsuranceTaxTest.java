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
import io.github.nalukit.paniolo.model.common.ProcessControl;
import io.github.nalukit.paniolo.model.common.TaxCategory;
import io.github.nalukit.paniolo.model.common.value.DutyTaxFeeCategory;
import io.github.nalukit.paniolo.model.common.value.InvoiceTypeCode;
import io.github.nalukit.paniolo.model.extended.ExtendedInformation;
import io.github.nalukit.paniolo.model.extended.LineExtension;
import io.github.nalukit.paniolo.model.extended.OtherTax;
import io.github.nalukit.paniolo.xml.ReadMode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The insurance tax AAM next to VAT in the Factur-X profile EXTENDED (Mustang issue #1342): the
 * reference case of 1,017.57 EUR net and 26.14 EUR insurance tax is due as 1,043.71 EUR.
 *
 * <p>The official rules of EXTENDED treat every header tax block, whatever its tax type, like a VAT
 * breakdown of its category (BR-E-09, BR-FXEXT-S-01). The documents of these tests are therefore
 * not checked against them; {@code OtherTaxTypeTest} reads and writes a document those rules accept.
 */
class InsuranceTaxTest {
  private static final Paniolo PANIOLO = Paniolo.builder()
                                                .calculationCheck(true)
                                                .build();

  private static final TaxCategory EXEMPT = new TaxCategory(DutyTaxFeeCategory.E,
                                                            BigDecimal.ZERO,
                                                            "Steuerfrei gemäß § 4 Nr. 10 UStG",
                                                            null);

  private static OtherTax tax() {
    return OtherTax.insuranceTax("S",
                                 new BigDecimal("19"),
                                 new BigDecimal("137.58"));
  }

  private static InvoiceLine line(String id,
                                  String price,
                                  boolean withLineTax) {
    var line = InvoiceLine.create()
                          .id(id)
                          .name("Versicherungsprämie " + id)
                          .quantity(BigDecimal.ONE)
                          .unitCode("C62")
                          .netUnitPrice(new BigDecimal(price))
                          .tax(EXEMPT);
    if (withLineTax) {
      line.extended(LineExtension.create()
                                 .addOtherTax(new OtherTax("AAM",
                                                           "S",
                                                           new BigDecimal("19"),
                                                           null,
                                                           tax().amount()))
                                 .build());
    }
    return line.build();
  }

  private static Invoice invoice(boolean withLineTax) {
    return TestInvoices.builder(InvoiceTypeCode.CODE_380,
                                "AAM-REF")
                       .processControl(ProcessControl.facturXExtended())
                       .lines(List.of(line("1",
                                           "879.99",
                                           false),
                                      line("2",
                                           "137.58",
                                           withLineTax)))
                       .extended(ExtendedInformation.create()
                                                    .addOtherTax(tax())
                                                    .build())
                       .build();
  }

  @Test
  void calculatesTheInsuranceTaxFromBasisAndRate() {
    assertEquals(new BigDecimal("26.14"),
                 tax().amount());
    assertEquals(new BigDecimal("0.01"),
                 OtherTax.insuranceTax("S",
                                       new BigDecimal("1"),
                                       new BigDecimal("0.50"))
                         .amount());
    assertEquals(new BigDecimal("19.01"),
                 OtherTax.insuranceTax("S",
                                       new BigDecimal("19"),
                                       new BigDecimal("100.05"))
                         .amount());
    assertThrows(IllegalArgumentException.class,
                 () -> new OtherTax("XYZ",
                                    "S",
                                    null,
                                    null,
                                    BigDecimal.ONE));
  }

  @Test
  void referenceCaseIsDueWithTheInsuranceTax() {
    var totals = invoice(false).totals();
    assertEquals(new BigDecimal("1017.57"),
                 totals.taxExclusiveAmount());
    assertEquals(new BigDecimal("26.14"),
                 totals.taxAmount());
    assertEquals(new BigDecimal("1043.71"),
                 totals.taxInclusiveAmount());
    assertEquals(new BigDecimal("1043.71"),
                 totals.payableAmount());
    // VAT stays VAT only: one exempt breakdown without tax.
    assertEquals(1,
                 totals.vatBreakdowns()
                       .size());
    assertEquals(new BigDecimal("0.00"),
                 totals.vatBreakdowns()
                       .get(0)
                       .taxAmount());
  }

  @Test
  void aTaxOnTheLinesIsNotCountedAgain() {
    assertEquals(invoice(false).totals(),
                 invoice(true).totals());
  }

  @Test
  void keepsTheInsuranceTaxInXmlWithoutMixingItWithVat() {
    for (boolean lineTax : new boolean[]{false, true}) {
      var    invoice = invoice(lineTax);
      String xml     = PANIOLO.toXml(invoice,
                                     InvoiceSyntax.CII);
      assertEquals(lineTax ?
                   2 :
                   1,
                   xml.split("<ram:TypeCode>AAM</ram:TypeCode>",
                             -1).length - 1,
                   xml);
      assertTrue(xml.contains("<ram:DuePayableAmount>1043.71</ram:DuePayableAmount>"),
                 xml);
      var imported = PANIOLO.importInvoice(xml.getBytes(StandardCharsets.UTF_8),
                                           ReadMode.STRICT);
      assertEquals(List.of(),
                   imported.warnings());
      assertEquals(invoice,
                   imported.invoice());
    }
  }

  @Test
  void rejectsAnExportThatCannotKeepTheTax() {
    var other = TestInvoices.builder(InvoiceTypeCode.CODE_380,
                                     "AAM-BASIC")
                            .processControl(ProcessControl.facturXBasic())
                            .extended(ExtendedInformation.create()
                                                         .addOtherTax(tax())
                                                         .build())
                            .build();
    var error = assertThrows(IllegalArgumentException.class,
                             () -> PANIOLO.toXml(other,
                                                 InvoiceSyntax.CII));
    assertTrue(error.getMessage()
                    .contains("EXTENDED"),
               error::getMessage);
    var ubl = assertThrows(IllegalArgumentException.class,
                           () -> PANIOLO.toXml(invoice(false),
                                               InvoiceSyntax.UBL));
    assertTrue(ubl.getMessage()
                  .contains("UBL"),
               ubl::getMessage);
  }

  @Test
  void vatOnlyInvoicesStayUnchanged() {
    var invoice = TestInvoices.invoice(InvoiceTypeCode.CODE_380,
                                       "VAT-ONLY");
    assertEquals(new BigDecimal("38.00"),
                 invoice.totals()
                        .taxAmount());
    assertEquals(new BigDecimal("238.00"),
                 invoice.totals()
                        .payableAmount());
  }
}

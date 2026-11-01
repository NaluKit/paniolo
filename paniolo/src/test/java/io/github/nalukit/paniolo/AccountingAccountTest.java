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
import io.github.nalukit.paniolo.model.common.References;
import io.github.nalukit.paniolo.model.common.TaxCategory;
import io.github.nalukit.paniolo.model.common.value.InvoiceTypeCode;
import io.github.nalukit.paniolo.model.extended.AccountingAccount;
import io.github.nalukit.paniolo.model.extended.ExtendedInformation;
import io.github.nalukit.paniolo.model.extended.LineExtension;
import io.github.nalukit.paniolo.validation.Severity;
import io.github.nalukit.paniolo.validation.ValidationMessage;
import io.github.nalukit.paniolo.validation.ValidationResult;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The profile EXTENDED repeats the buyer accounting account at the document and at the lines; every
 * account keeps its type code and order (Mustang issues #279 and #1248).
 */
class AccountingAccountTest {
  private static final Paniolo PANIOLO = Paniolo.create();

  private static List<ValidationMessage> errors(ValidationResult result) {
    return result.messages()
                 .stream()
                 .filter(message -> message.severity() == Severity.ERROR || message.severity() == Severity.FATAL)
                 .toList();
  }

  private static InvoiceLine line(String id,
                                  String account,
                                  String further) {
    return InvoiceLine.create()
                      .id(id)
                      .name("Position " + id)
                      .quantity(BigDecimal.ONE)
                      .unitCode("H87")
                      .netUnitPrice(new BigDecimal("100.00"))
                      .tax(TaxCategory.standard(new BigDecimal("19")))
                      .buyerAccountingReference(account)
                      .extended(LineExtension.create()
                                             .accountingTypeCode("1")
                                             .addAdditionalAccountingAccount(new AccountingAccount(further,
                                                                                                   "2"))
                                             .build())
                      .build();
  }

  private static Invoice.Builder invoice() {
    return TestInvoices.builder(InvoiceTypeCode.CODE_380,
                                "K-1")
                       .references(References.create()
                                             .buyerAccountingReference("ACC1")
                                             .build())
                       .addLine(line("2",
                                     "L2-A",
                                     "L2-B"))
                       .addLine(line("3",
                                     "L3-A",
                                     "L3-B"))
                       .extended(ExtendedInformation.create()
                                                    .buyerAccountingTypeCode("1")
                                                    .addAdditionalAccountingAccount(new AccountingAccount("ACC2",
                                                                                                          "2"))
                                                    .build());
  }

  @Test
  void keepsTheRepeatedAccountsOfTheArchivedInvoice() throws Exception {
    byte[] original;
    try (InputStream input = AccountingAccountTest.class.getResourceAsStream("/regression/mustang/repeated-accounts.xml")) {
      original = input.readAllBytes();
    }
    var input = PANIOLO.validate(original,
                                 FacturXProfile.EXTENDED);
    assertEquals(List.of(),
                 errors(input));
    var invoice = PANIOLO.read(original);
    assertEquals("ACC1",
                 invoice.references()
                        .buyerAccountingReference());
    assertEquals(List.of(new AccountingAccount("ACC2",
                                               null)),
                 invoice.extended()
                        .additionalAccountingAccounts());
    String written = PANIOLO.toXml(invoice,
                                   InvoiceSyntax.CII);
    assertTrue(written.indexOf("<ram:ID>ACC1</ram:ID>") < written.indexOf("<ram:ID>ACC2</ram:ID>"),
               written);
    assertEquals(List.of(),
                 errors(PANIOLO.validate(written.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                                         FacturXProfile.EXTENDED)));
  }

  @Test
  void keepsTwoAccountsAtTheDocumentAndAtTwoLines() {
    var invoice = invoice().processControl(ProcessControl.facturXExtended())
                           .build();
    byte[] xml = PANIOLO.toBytes(invoice,
                                 InvoiceSyntax.CII);
    assertEquals(List.of(),
                 errors(PANIOLO.validate(xml,
                                         FacturXProfile.EXTENDED)));
    var read = PANIOLO.read(xml);
    assertEquals(invoice,
                 read);
    assertEquals(List.of(new AccountingAccount("L3-B",
                                               "2")),
                 read.lines()
                     .get(2)
                     .extended()
                     .additionalAccountingAccounts());
  }

  @Test
  void refusesFurtherAccountsOutsideExtended() {
    // EN 16931 has one accounting reference (BT-19, BT-133); EXTENDED information is refused there.
    var invoice = invoice().build();
    for (var syntax : List.of(InvoiceSyntax.CII,
                              InvoiceSyntax.UBL)) {
      assertThrows(IllegalArgumentException.class,
                   () -> PANIOLO.toXml(invoice,
                                       syntax));
    }
    assertThrows(IllegalArgumentException.class,
                 () -> TestInvoices.builder(InvoiceTypeCode.CODE_380,
                                            "K-2")
                                   .extended(ExtendedInformation.create()
                                                                .addAdditionalAccountingAccount(new AccountingAccount("ACC2",
                                                                                                                      null))
                                                                .build())
                                   .build());
  }
}

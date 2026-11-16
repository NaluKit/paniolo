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
import io.github.nalukit.paniolo.model.common.AccountIdType;
import io.github.nalukit.paniolo.model.common.CreditTransfer;
import io.github.nalukit.paniolo.model.common.PaymentInstruction;
import io.github.nalukit.paniolo.model.common.ProcessControl;
import io.github.nalukit.paniolo.model.common.value.InvoiceTypeCode;
import io.github.nalukit.paniolo.model.common.value.PaymentMeansCode;
import io.github.nalukit.paniolo.xml.ReadMode;
import io.github.nalukit.paniolo.xml.ReadWarning;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Payment account identifiers that are no IBAN (Mustang issue #1348): the model keeps the kind, the
 * CII writer writes {@code ram:ProprietaryID} for them and {@code ram:IBANID} for an IBAN, and the
 * reader keeps the kind.
 */
@DisplayName("Proprietary payment account identifier")
class ProprietaryAccountTest {
  private static final Paniolo        PANIOLO     = Paniolo.create();
  private static final CreditTransfer PROPRIETARY = CreditTransfer.proprietary("EXAMPLE-ACCOUNT",
                                                                               "Muster GmbH",
                                                                               "EXAMPBICXXX");
  private static final CreditTransfer IBAN        = new CreditTransfer("DE89370400440532013000",
                                                                       "Muster GmbH",
                                                                       "COBADEFFXXX");

  private static Invoice invoice(PaymentMeansCode code,
                                 CreditTransfer... accounts) {
    var payment = PaymentInstruction.create()
                                    .meansCode(code)
                                    .remittanceInformation("Z-1");
    for (var account : accounts) {
      payment.addCreditTransfer(account);
    }
    return TestInvoices.builder(InvoiceTypeCode.CODE_380,
                                "Z-1")
                       .paymentInstruction(payment.build())
                       .build();
  }

  @Test
  @DisplayName("is written as ProprietaryID without IBANID and passes the official rules (BR-50)")
  void writesProprietaryId() {
    String xml = PANIOLO.toXml(invoice(PaymentMeansCode.CODE_30,
                                       PROPRIETARY),
                               InvoiceSyntax.CII);
    assertTrue(xml.contains("<ram:ProprietaryID>EXAMPLE-ACCOUNT</ram:ProprietaryID>"),
               xml);
    assertFalse(xml.contains("IBANID"),
                xml);
    var result = PANIOLO.validate(xml);
    assertTrue(result.isValid(),
               result::toString);
  }

  @Test
  @DisplayName("keeps value and kind through import and normalized export")
  void keepsKindThroughNormalizedExport() {
    String xml = PANIOLO.toXml(invoice(PaymentMeansCode.CODE_30,
                                       PROPRIETARY),
                               InvoiceSyntax.CII);
    var read = PANIOLO.read(xml,
                            ReadMode.STRICT);
    assertEquals(List.of(),
                 read.warnings());
    assertEquals(List.of(PROPRIETARY),
                 read.invoice()
                     .paymentInstruction()
                     .creditTransfers());
    assertEquals(AccountIdType.PROPRIETARY,
                 read.invoice()
                     .paymentInstruction()
                     .creditTransfers()
                     .get(0)
                     .idType());
    String normalized = PANIOLO.toXml(read.invoice(),
                                      InvoiceSyntax.CII);
    assertTrue(normalized.contains("<ram:ProprietaryID>EXAMPLE-ACCOUNT</ram:ProprietaryID>"),
               normalized);
    assertFalse(normalized.contains("IBANID"),
                normalized);
  }

  @Test
  @DisplayName("keeps writing an IBAN as IBANID with name and BIC")
  void ibanStaysIbanId() {
    var transfer = new CreditTransfer("DE89370400440532013000",
                                      "Muster GmbH",
                                      "COBADEFFXXX");
    assertEquals(AccountIdType.IBAN,
                 transfer.idType());
    String xml = PANIOLO.toXml(invoice(PaymentMeansCode.CODE_58,
                                       transfer),
                               InvoiceSyntax.CII);
    assertTrue(xml.contains("<ram:IBANID>DE89370400440532013000</ram:IBANID>"),
               xml);
    assertFalse(xml.contains("ProprietaryID"),
                xml);
    assertTrue(xml.contains("<ram:AccountName>Muster GmbH</ram:AccountName>"),
               xml);
    assertTrue(xml.contains("<ram:BICID>COBADEFFXXX</ram:BICID>"),
               xml);
    assertEquals(List.of(transfer),
                 PANIOLO.read(xml,
                              ReadMode.STRICT)
                        .invoice()
                        .paymentInstruction()
                        .creditTransfers());
  }

  @Test
  @DisplayName("handles accounts of mixed kinds independently, each with its own BIC")
  void mixedKinds() {
    var invoice = invoice(PaymentMeansCode.CODE_30,
                          IBAN,
                          PROPRIETARY);
    String xml = PANIOLO.toXml(invoice,
                               InvoiceSyntax.CII);
    assertTrue(xml.contains("<ram:IBANID>DE89370400440532013000</ram:IBANID>"),
               xml);
    assertTrue(xml.contains("<ram:ProprietaryID>EXAMPLE-ACCOUNT</ram:ProprietaryID>"),
               xml);
    var result = PANIOLO.validate(xml);
    assertTrue(result.isValid(),
               result::toString);
    assertEquals(List.of(IBAN,
                         PROPRIETARY),
                 PANIOLO.read(xml,
                              ReadMode.STRICT)
                        .invoice()
                        .paymentInstruction()
                        .creditTransfers());
  }

  @Test
  @DisplayName("is written as the identifier in UBL, which has no kind; reading gives an IBAN account")
  void ublCarriesNoKind() {
    String xml = PANIOLO.toXml(invoice(PaymentMeansCode.CODE_30,
                                       PROPRIETARY),
                               InvoiceSyntax.UBL);
    assertTrue(xml.contains("<cbc:ID>EXAMPLE-ACCOUNT</cbc:ID>"),
               xml);
    var result = PANIOLO.validate(xml);
    assertTrue(result.isValid(),
               result::toString);
    var read = PANIOLO.read(xml,
                            ReadMode.STRICT)
                      .invoice()
                      .paymentInstruction()
                      .creditTransfers()
                      .get(0);
    assertEquals("EXAMPLE-ACCOUNT",
                 read.iban());
    assertEquals(AccountIdType.IBAN,
                 read.idType());
  }

  @Test
  @DisplayName("with both IBANID and ProprietaryID: strict reading rejects, lenient keeps the IBAN and reports")
  void bothIdentifiers() {
    String xml = PANIOLO.toXml(invoice(PaymentMeansCode.CODE_30,
                                       IBAN),
                               InvoiceSyntax.CII)
                        .replace("<ram:IBANID>DE89370400440532013000</ram:IBANID>",
                                 "<ram:IBANID>DE89370400440532013000</ram:IBANID><ram:ProprietaryID>EXAMPLE-ACCOUNT</ram:ProprietaryID>");
    var strict = assertThrows(IllegalArgumentException.class,
                              () -> PANIOLO.read(xml,
                                                 ReadMode.STRICT));
    assertTrue(strict.getMessage()
                     .contains("EXAMPLE-ACCOUNT"),
               strict::getMessage);
    var lenient = PANIOLO.read(xml,
                               ReadMode.LENIENT);
    assertEquals(List.of(IBAN),
                 lenient.invoice()
                        .paymentInstruction()
                        .creditTransfers());
    assertTrue(lenient.warnings()
                      .stream()
                      .anyMatch(w -> ReadWarning.IDENTIFIER.equals(w.code()) && w.message()
                                                                                  .contains("EXAMPLE-ACCOUNT")),
               lenient.warnings()::toString);
  }

  @Test
  @DisplayName("passes the official rules in the profiles EN 16931 and Factur-X EXTENDED")
  void facturXProfiles() {
    for (var control : List.of(ProcessControl.en16931(),
                               ProcessControl.facturXExtended())) {
      var invoice = TestInvoices.builder(InvoiceTypeCode.CODE_380,
                                         "Z-1")
                                .processControl(control)
                                .paymentInstruction(PaymentInstruction.create()
                                                                      .meansCode(PaymentMeansCode.CODE_30)
                                                                      .addCreditTransfer(PROPRIETARY)
                                                                      .remittanceInformation("Z-1")
                                                                      .build())
                                .build();
      String xml = PANIOLO.toXml(invoice,
                                 InvoiceSyntax.CII);
      assertTrue(xml.contains("<ram:ProprietaryID>EXAMPLE-ACCOUNT</ram:ProprietaryID>"),
                 xml);
      var result = PANIOLO.validate(xml);
      assertTrue(result.isValid(),
                 () -> control.specificationId() + ": " + result);
    }
  }

  @Test
  @DisplayName("with SEPA payment means 58 the official rules warn that BT-84 should be an IBAN (BR-DE-19)")
  void sepaNeedsIban() {
    for (var syntax : List.of(InvoiceSyntax.CII,
                              InvoiceSyntax.UBL)) {
      var result = PANIOLO.validate(PANIOLO.toXml(invoice(PaymentMeansCode.CODE_58,
                                                          PROPRIETARY),
                                                  syntax));
      assertTrue(result.messages()
                       .stream()
                       .anyMatch(m -> "BR-DE-19".equals(m.ruleId())),
                 () -> syntax + ": " + result);
    }
  }

  @Test
  @DisplayName("builder: either iban or proprietaryId; the constructor without kind means IBAN")
  void builderAndConstructor() {
    var proprietary = CreditTransfer.create()
                                    .proprietaryId("EXAMPLE-ACCOUNT")
                                    .accountName("Muster GmbH")
                                    .bic("EXAMPBICXXX")
                                    .build();
    assertEquals(PROPRIETARY,
                 proprietary);
    assertTrue(proprietary.isProprietary());
    assertFalse(IBAN.isProprietary());
    assertEquals(IBAN,
                 CreditTransfer.create()
                               .iban("DE89370400440532013000")
                               .accountName("Muster GmbH")
                               .bic("COBADEFFXXX")
                               .build());
    assertEquals(AccountIdType.IBAN,
                 new CreditTransfer("DE89370400440532013000",
                                    null,
                                    null,
                                    null).idType());
    assertThrows(IllegalArgumentException.class,
                 () -> CreditTransfer.create()
                                     .iban("DE89370400440532013000")
                                     .proprietaryId("EXAMPLE-ACCOUNT")
                                     .build());
    assertThrows(IllegalArgumentException.class,
                 () -> CreditTransfer.proprietary(" ",
                                                  null,
                                                  null));
  }
}

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
import io.github.nalukit.paniolo.model.common.CreditTransfer;
import io.github.nalukit.paniolo.model.common.PaymentInstruction;
import io.github.nalukit.paniolo.model.common.value.InvoiceTypeCode;
import io.github.nalukit.paniolo.model.common.value.PaymentMeansCode;
import io.github.nalukit.paniolo.xml.ReadMode;
import io.github.nalukit.paniolo.xml.ReadWarning;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Several payment means blocks: accounts of the same payment means keep their own BIC; a block
 * with another payment means is not merged into the payment instruction (Mustang issues #583, #596,
 * and #795).
 */
class PaymentMeansBlocksTest {
  private static final Paniolo        PANIOLO = Paniolo.create();
  private static final CreditTransfer FIRST   = new CreditTransfer("DE89370400440532013000",
                                                                   "Muster GmbH",
                                                                   "COBADEFFXXX");
  private static final CreditTransfer SECOND  = new CreditTransfer("DE02120300000000202051",
                                                                   "Muster GmbH",
                                                                   "BYLADEM1001");

  private static Invoice invoice() {
    return TestInvoices.builder(InvoiceTypeCode.CODE_380,
                                "Z-1")
                       .paymentInstruction(PaymentInstruction.create()
                                                             .meansCode(PaymentMeansCode.CODE_58)
                                                             .addCreditTransfer(FIRST)
                                                             .addCreditTransfer(SECOND)
                                                             .remittanceInformation("Z-1")
                                                             .build())
                       .build();
  }

  @Test
  void keepsEveryAccountWithItsBic() {
    var invoice = invoice();
    for (var syntax : List.of(InvoiceSyntax.CII,
                              InvoiceSyntax.UBL)) {
      String xml = PANIOLO.toXml(invoice,
                                 syntax);
      var result = PANIOLO.validate(xml);
      assertTrue(result.isValid(),
                 () -> syntax + ": " + result.messages());
      var read = PANIOLO.read(xml,
                              ReadMode.STRICT);
      assertEquals(List.of(),
                   read.warnings());
      assertEquals(List.of(FIRST,
                           SECOND),
                   read.invoice()
                       .paymentInstruction()
                       .creditTransfers(),
                   syntax::toString);
    }
  }

  @Test
  void doesNotMergeABlockWithOtherPaymentMeans() {
    // The second block becomes a credit transfer (30) with its own text.
    String xml = PANIOLO.toXml(invoice(),
                               InvoiceSyntax.CII);
    int second = xml.indexOf("<ram:SpecifiedTradeSettlementPaymentMeans>",
                             xml.indexOf("<ram:SpecifiedTradeSettlementPaymentMeans>") + 1);
    String changed = xml.substring(0,
                                   second) + xml.substring(second)
                                                .replaceFirst("<ram:TypeCode>58</ram:TypeCode>",
                                                              "<ram:TypeCode>30</ram:TypeCode><ram:Information>Inlandsüberweisung</ram:Information>");
    var strict = assertThrows(IllegalArgumentException.class,
                              () -> PANIOLO.read(changed,
                                                 ReadMode.STRICT));
    assertTrue(strict.getMessage()
                     .contains("Payment means 2 (code 30"),
               strict::getMessage);
    var lenient = PANIOLO.read(changed,
                               ReadMode.LENIENT);
    var instruction = lenient.invoice()
                             .paymentInstruction();
    assertEquals(PaymentMeansCode.CODE_58,
                 instruction.meansCode());
    // The account of the other block is not attributed to payment means 58.
    assertEquals(List.of(FIRST),
                 instruction.creditTransfers());
    var warning = lenient.warnings()
                         .stream()
                         .filter(each -> each.code()
                                             .equals(ReadWarning.PAYMENT_MEANS))
                         .findFirst()
                         .orElseThrow();
    assertTrue(warning.message()
                      .contains("DE02120300000000202051") && warning.message()
                                                                    .contains("BYLADEM1001") && warning.message()
                                                                                                       .contains("Inlandsüberweisung"),
               warning::message);
  }
}

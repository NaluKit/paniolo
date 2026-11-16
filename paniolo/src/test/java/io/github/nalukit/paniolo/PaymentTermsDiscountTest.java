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
import io.github.nalukit.paniolo.model.common.ProcessControl;
import io.github.nalukit.paniolo.model.common.value.InvoiceTypeCode;
import io.github.nalukit.paniolo.model.extended.ExtendedInformation;
import io.github.nalukit.paniolo.model.extended.PaymentTerm;
import io.github.nalukit.paniolo.model.extended.PaymentTermsAdjustment;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A calculated cash discount amount is written as ActualDiscountAmount and survives the XML round
 * trip; the calculation changes neither the due date nor the amount due.
 */
class PaymentTermsDiscountTest {
  private static final Paniolo PANIOLO = Paniolo.create();

  @Test
  void writesAndReadsTheCalculatedDiscountAmount() {
    var discount = new PaymentTermsAdjustment(null,
                                              14,
                                              new BigDecimal("1190.00"),
                                              new BigDecimal("3"),
                                              null).withComputedAmount();
    var plain = TestInvoices.builder(InvoiceTypeCode.CODE_380,
                                     "K-1")
                            .processControl(ProcessControl.facturXExtended());
    var without = plain.build();
    var invoice = TestInvoices.builder(InvoiceTypeCode.CODE_380,
                                       "K-1")
                              .processControl(ProcessControl.facturXExtended())
                              .extended(ExtendedInformation.create()
                                                           .addPaymentTerm(new PaymentTerm("Skonto",
                                                                                           null,
                                                                                           null,
                                                                                           null,
                                                                                           discount))
                                                           .build())
                              .build();
    String xml = PANIOLO.toXml(invoice,
                               InvoiceSyntax.CII);
    assertTrue(xml.contains("<ram:ActualDiscountAmount>35.70</ram:ActualDiscountAmount>"),
               xml);
    assertEquals(invoice,
                 PANIOLO.read(xml));
    assertEquals(without.dueDate(),
                 invoice.dueDate());
    assertEquals(without.totals(),
                 invoice.totals());
  }
}

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

import io.github.nalukit.paniolo.model.Invoice;
import io.github.nalukit.paniolo.model.common.Address;
import io.github.nalukit.paniolo.model.common.Contact;
import io.github.nalukit.paniolo.model.common.CreditTransfer;
import io.github.nalukit.paniolo.model.common.DeliveryInformation;
import io.github.nalukit.paniolo.model.common.ElectronicAddress;
import io.github.nalukit.paniolo.model.common.InvoiceLine;
import io.github.nalukit.paniolo.model.common.Party;
import io.github.nalukit.paniolo.model.common.PaymentInstruction;
import io.github.nalukit.paniolo.model.common.TaxCategory;
import io.github.nalukit.paniolo.model.common.value.Country;
import io.github.nalukit.paniolo.model.common.value.InvoiceTypeCode;
import io.github.nalukit.paniolo.model.common.value.PaymentMeansCode;

import java.math.BigDecimal;
import java.time.LocalDate;

final class TestInvoices {
  private TestInvoices() {
  }

  static Invoice invoice(InvoiceTypeCode type,
                         String number) {
    return builder(type,
                   number).build();
  }

  static Invoice.Builder builder(InvoiceTypeCode type,
                                 String number) {
    var seller = Party.create()
                      .name("Muster GmbH")
                      .address(Address.create()
                                      .street("Musterstraße 1")
                                      .city("Berlin")
                                      .postalCode("10115")
                                      .country(Country.DE)
                                      .build())
                      .vatId("DE123456788")
                      .electronicAddress(ElectronicAddress.create()
                                                          .value("rechnung@example.org")
                                                          .schemeId("EM")
                                                          .build())
                      .contact(Contact.create()
                                      .name("Erika Muster")
                                      .telephone("+49 30 123456")
                                      .email("rechnung@example.org")
                                      .build())
                      .build();
    var buyer = Party.create()
                     .name("Kunde GmbH")
                     .address(Address.create()
                                     .street("Kundenweg 2")
                                     .city("Hamburg")
                                     .postalCode("20095")
                                     .country(Country.DE)
                                     .build())
                     .electronicAddress(ElectronicAddress.create()
                                                         .value("einkauf@example.org")
                                                         .schemeId("EM")
                                                         .build())
                     .build();
    return Invoice.create()
                  .number(number)
                  .typeCode(type)
                  .issueDate(LocalDate.of(2026,
                                          10,
                                          27))
                  .dueDate(LocalDate.of(2026,
                                        11,
                                        10))
                  .buyerReference("04011000-12345-03")
                  .seller(seller)
                  .buyer(buyer)
                  .delivery(DeliveryInformation.create()
                                               .actualDeliveryDate(LocalDate.of(2026,
                                                                                10,
                                                                                26))
                                               .build())
                  .paymentInstruction(PaymentInstruction.create()
                                                        .meansCode(PaymentMeansCode.CODE_58)
                                                        .addCreditTransfer(CreditTransfer.create()
                                                                                         .iban("DE89370400440532013000")
                                                                                         .accountName("Muster GmbH")
                                                                                         .bic("COBADEFFXXX")
                                                                                         .build())
                                                        .remittanceInformation(number)
                                                        .build())
                  .addLine(InvoiceLine.create()
                                      .id("1")
                                      .name("Beratung")
                                      .quantity(new BigDecimal("2"))
                                      .unitCode("HUR")
                                      .netUnitPrice(new BigDecimal("100.00"))
                                      .tax(TaxCategory.standard(new BigDecimal("19")))
                                      .build());
  }
}

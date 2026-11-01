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
import io.github.nalukit.paniolo.json.PanioloJson;
import io.github.nalukit.paniolo.model.Invoice;
import io.github.nalukit.paniolo.model.common.value.InvoiceTypeCode;
import io.github.nalukit.paniolo.model.despatch.DespatchAdvice;
import io.github.nalukit.paniolo.model.order.Order;
import io.github.nalukit.paniolo.model.order.OrderTypeCode;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Stores the test invoices, orders, and despatch advices of all profiles as JSON and loads them
 * again; the loaded object equals the original, including its totals.
 */
class JsonRoundTripTest {
  private static final PanioloJson JSON    = PanioloJson.create();
  private static final Paniolo     PANIOLO = Paniolo.create();

  private static <T> void roundTrip(T value,
                                    Class<T> type) {
    T loaded = JSON.read(JSON.toJson(value),
                         type);
    assertEquals(value,
                 loaded);
    if (value instanceof Invoice invoice) {
      assertEquals(invoice.totals(),
                   ((Invoice) loaded).totals());
    }
  }

  private static byte[] resource(String name)
      throws IOException {
    try (InputStream input = JsonRoundTripTest.class.getResourceAsStream(name)) {
      return input.readAllBytes();
    }
  }

  @Test
  void invoicesOfAllProfiles()
      throws IOException {
    var invoices = new ArrayList<Invoice>();
    for (var profile : List.of(FacturXProfile.BASIC,
                               FacturXProfile.EN16931,
                               FacturXProfile.EXTENDED,
                               FacturXProfile.XRECHNUNG)) {
      for (var type : List.of(InvoiceTypeCode.CODE_380,
                              InvoiceTypeCode.CODE_381)) {
        invoices.add(FacturXInvoices.invoice(profile,
                                             type)
                                    .build());
      }
    }
    invoices.add(ExtendedTest.invoice(InvoiceTypeCode.CODE_380)
                             .build());
    invoices.add(ExtendedTest.withSubLines(ExtendedTest.invoice(InvoiceTypeCode.CODE_380))
                             .build());
    invoices.add(ExtendedTest.withPartyAndCurrency(ExtendedTest.invoice(InvoiceTypeCode.CODE_380))
                             .build());
    invoices.add(ExtendedTest.withAdjustmentDetails(ExtendedTest.invoice(InvoiceTypeCode.CODE_380))
                             .build());
    invoices.add(ExtendedTest.directDebit(InvoiceTypeCode.CODE_380)
                             .build());
    invoices.add(ProfilesWithoutLinesTest.minimum());
    invoices.add(ProfilesWithoutLinesTest.basicWl());
    invoices.add(PANIOLO.read(FacturXValidationTest.sample("minimum.xml")));
    invoices.add(PANIOLO.read(FacturXValidationTest.sample("basic-wl.xml")));
    invoices.add(PANIOLO.read(resource("/zugferd1/basic.xml")));
    invoices.add(PANIOLO.read(resource("/zugferd1/comfort.xml")));
    for (var invoice : invoices) {
      roundTrip(invoice,
                Invoice.class);
    }
  }

  @Test
  void ordersAndDespatchAdvices() {
    for (var type : OrderTypeCode.values()) {
      roundTrip(OrderSamples.basic(type)
                            .build(),
                Order.class);
      roundTrip(OrderSamples.comfort(type)
                            .build(),
                Order.class);
    }
    roundTrip(DespatchAdvicePdfTest.advice(),
              DespatchAdvice.class);
  }
}

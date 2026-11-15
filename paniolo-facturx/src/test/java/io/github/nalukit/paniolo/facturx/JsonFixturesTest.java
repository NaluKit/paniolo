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
import io.github.nalukit.paniolo.json.PanioloJson;
import io.github.nalukit.paniolo.model.common.value.InvoiceTypeCode;
import io.github.nalukit.paniolo.model.despatch.DespatchAdvice;
import io.github.nalukit.paniolo.model.order.Order;
import io.github.nalukit.paniolo.model.order.OrderDetails;
import io.github.nalukit.paniolo.model.order.OrderTypeCode;
import io.github.nalukit.paniolo.model.Invoice;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The stored JSON format does not change: the files in {@code /json} were written by Paniolo
 * 1.0.0-rc01 (Jackson 2) and must load to the same objects and be written again byte by byte. They
 * cover codes, decimals with their scale, dates and times, sub-lines with their line references,
 * allowances and charges, payment means, a ZUGFeRD 1.0 document, an order, and a despatch advice.
 */
class JsonFixturesTest {
  private static final PanioloJson JSON = PanioloJson.create();

  private static String fixture(String name)
      throws IOException {
    try (InputStream input = JsonFixturesTest.class.getResourceAsStream("/json/" + name)) {
      return new String(input.readAllBytes(),
                        StandardCharsets.UTF_8);
    }
  }

  private static <T> void same(String name,
                               T expected,
                               Class<T> type)
      throws IOException {
    String stored = fixture(name);
    T      loaded = JSON.read(stored,
                              type);
    assertEquals(expected,
                 loaded,
                 name);
    assertEquals(stored,
                 JSON.toJson(loaded),
                 name);
    if (expected instanceof Invoice invoice) {
      assertEquals(invoice.totals(),
                   ((Invoice) loaded).totals(),
                   name);
    }
  }

  @Test
  void invoices()
      throws IOException {
    same("invoice-extended-sub-lines.json",
         ExtendedTest.withSubLines(ExtendedTest.invoice(InvoiceTypeCode.CODE_380))
                     .build(),
         Invoice.class);
    same("invoice-extended-adjustments.json",
         ExtendedTest.withAdjustmentDetails(ExtendedTest.invoice(InvoiceTypeCode.CODE_380))
                     .build(),
         Invoice.class);
    same("invoice-extended-party-currency.json",
         ExtendedTest.withPartyAndCurrency(ExtendedTest.invoice(InvoiceTypeCode.CODE_381))
                     .build(),
         Invoice.class);
    same("invoice-direct-debit.json",
         ExtendedTest.directDebit(InvoiceTypeCode.CODE_380)
                     .build(),
         Invoice.class);
    same("invoice-minimum.json",
         ProfilesWithoutLinesTest.minimum(),
         Invoice.class);
    byte[] comfort;
    try (InputStream input = JsonFixturesTest.class.getResourceAsStream("/zugferd1/comfort.xml")) {
      comfort = input.readAllBytes();
    }
    same("invoice-zugferd1-comfort.json",
         Paniolo.create()
                .read(comfort),
         Invoice.class);
  }

  @Test
  void orderAndDespatchAdvice()
      throws IOException {
    same("order-comfort.json",
         OrderSamples.comfort(OrderTypeCode.ORDER)
                     .details(OrderDetails.create()
                                          .issueTime(LocalTime.of(12,
                                                                  32))
                                          .requestedDeliveryTime(LocalTime.of(8,
                                                                              0,
                                                                              30))
                                          .build())
                     .build(),
         Order.class);
    same("despatch-advice.json",
         DespatchAdvicePdfTest.advice(),
         DespatchAdvice.class);
  }
}

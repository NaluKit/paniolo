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
import io.github.nalukit.paniolo.model.order.Order;
import io.github.nalukit.paniolo.model.order.OrderTypeCode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks generated Order-X documents and the official examples against the official Order-X schema
 * and Schematron packaged in paniolo-facturx-rules, or against the rules named by
 * {@code PANIOLO_ORDERX_RULES}.
 */
class OfficialOrderXRulesTest {
  private static final Paniolo PANIOLO = System.getenv("PANIOLO_ORDERX_RULES") == null ?
                                         Paniolo.create() :
                                         Paniolo.builder()
                                                .orderXRules(Path.of(System.getenv("PANIOLO_ORDERX_RULES")))
                                                .build();

  /**
   * The XML examples of the Order-X 1.0 release package of FNFE-MPE and FeRD, unmodified (with
   * their UTF-8 byte order mark).
   */
  @ParameterizedTest
  @ValueSource(strings = { "BASIC",
                           "COMFORT" })
  void officialExamplesPassTheOfficialRules(String profile)
      throws Exception {
    Path directory = Path.of(OfficialOrderXRulesTest.class.getResource("/orderx-examples/" + profile)
                                                          .toURI());
    try (var files = Files.list(directory)) {
      var examples = files.sorted()
                          .toList();
      assertEquals(12,
                   examples.size());
      for (Path example : examples) {
        var result = PANIOLO.validateOrder(Files.readAllBytes(example));
        assertTrue(result.isValid(),
                   () -> example.getFileName() + ": " + result.messages());
        assertEquals("Order-X " + profile,
                     result.profileName(),
                     example.getFileName()
                            .toString());
      }
    }
  }

  /**
   * The official examples carry content the order model does not support (e.g. a copy indicator,
   * date-times, delivery periods); lenient reading reads them all, and the order written again
   * passes the official rules.
   */
  @ParameterizedTest
  @ValueSource(strings = { "BASIC",
                           "COMFORT" })
  void officialExamplesCanBeReadLeniently(String profile)
      throws Exception {
    Path directory = Path.of(OfficialOrderXRulesTest.class.getResource("/orderx-examples/" + profile)
                                                          .toURI());
    try (var files = Files.list(directory)) {
      for (Path example : files.sorted()
                               .toList()) {
        var order = PANIOLO.readOrder(Files.readAllBytes(example),
                                      true);
        assertEquals(profile,
                     order.profile()
                          .name(),
                     example.getFileName()
                            .toString());
        var result = PANIOLO.validateOrder(PANIOLO.toOrderXBytes(order));
        assertTrue(result.isValid(),
                   () -> example.getFileName() + ": " + result.messages());
      }
    }
  }

  /**
   * The order model carries the content of the official examples, so strict reading reads them and
   * the order written again contains everything; JSON stores it as well. Five examples state
   * allowance and charge totals without the allowances and charges themselves and are rejected.
   */
  @ParameterizedTest
  @ValueSource(strings = { "BASIC",
                           "COMFORT" })
  void officialExamplesCanBeReadStrictly(String profile)
      throws Exception {
    var inconsistent = java.util.Set.of("ORDER-X_EX01_ORDER_FULL_DATA-BASIC.xml",
                                        "ORDER-X_EX02_CHANGE-BASIC.xml",
                                        "ORDER-X_EX03_ORDER_BASIC_DATA-BASIC.xml",
                                        "ORDER-X_EX04_RESPONSE-BASIC.xml",
                                        "ORDER-X_EX03_ORDER_BASIC_DATA-COMFORT.xml");
    var json = io.github.nalukit.paniolo.json.PanioloJson.create();
    Path directory = Path.of(OfficialOrderXRulesTest.class.getResource("/orderx-examples/" + profile)
                                                          .toURI());
    try (var files = Files.list(directory)) {
      for (Path example : files.sorted()
                               .toList()) {
        String name = example.getFileName()
                             .toString();
        byte[] bytes = Files.readAllBytes(example);
        if (inconsistent.contains(name)) {
          var error = assertThrows(IllegalArgumentException.class,
                                   () -> PANIOLO.readOrder(bytes),
                                   name);
          assertTrue(error.getMessage()
                          .contains("Stated tax basis total 300.00 differs from calculated 310.00"),
                     error::getMessage);
          continue;
        }
        var order = PANIOLO.readOrder(bytes);
        assertEquals(order,
                     PANIOLO.readOrder(PANIOLO.toOrderXBytes(order)),
                     name);
        assertEquals(order,
                     json.read(json.toJson(order),
                               io.github.nalukit.paniolo.model.order.Order.class),
                     name);
      }
    }
  }

  @ParameterizedTest
  @EnumSource(OrderTypeCode.class)
  void generatedOrdersPassTheOfficialRules(OrderTypeCode type) {
    for (Order order : new Order[] { OrderSamples.basic(type).build(),
                                     OrderSamples.comfort(type).build(),
                                     OrderSamples.comfort(type)
                                                 .profile(io.github.nalukit.paniolo.model.order.OrderXProfile.EXTENDED).build() }) {
      var result = PANIOLO.validateOrder(PANIOLO.toOrderXBytes(order));
      assertTrue(result.isValid(),
                 () -> result.messages()
                             .toString());
      assertEquals("Order-X " +
                   order.profile()
                        .name(),
                   result.profileName());
      var pdf = PANIOLO.validateOrder(PANIOLO.toOrderXPdf(order,
                                                          TestPdfs.pdfA2b()));
      assertTrue(pdf.isValid(),
                 () -> pdf.messages()
                          .toString());
    }
    // The rules find errors: an unknown allowance reason code.
    String broken = PANIOLO.toOrderX(OrderSamples.comfort(type)
                                                 .build())
                           .replace("<ram:Reason>Rahmenrabatt",
                                    "<ram:ReasonCode>XYZ</ram:ReasonCode><ram:Reason>Rahmenrabatt");
    var result = PANIOLO.validateOrder(broken.getBytes(StandardCharsets.UTF_8));
    assertTrue(!result.isValid(),
               () -> result.messages()
                           .toString());
  }
}

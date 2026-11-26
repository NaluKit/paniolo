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
import io.github.nalukit.paniolo.xml.ReadMode;
import io.github.nalukit.paniolo.xml.ReadWarning;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A total of allowances without any allowance element, as a ZUGFeRD 1.0 BASIC invoice states it (a
 * correction of the Intarsys samples): the model has no allowance to hold it and does not invent
 * one, so the totals of the invoice differ from the document. Reading says so; a caller that must
 * not write other amounts than the document states can refuse such a result.
 *
 * <p>The cases are derived from the ZUGFeRD 1.0 BASIC sample (one line of 120.00 at 19 %).
 */
class Zugferd1AllowanceTotalTest {
  private static final Paniolo PANIOLO = Paniolo.create();

  private static String basic() {
    return new String(Zugferd1Test.sample("basic.xml"),
                      StandardCharsets.UTF_8);
  }

  /**
   * The sample with an allowance total of 10.00 that no element explains: the breakdown, the tax
   * and the amounts follow from the net basis of 110.00.
   */
  private static String withAllowanceTotal() {
    String xml = basic().replace("<ram:CalculatedAmount currencyID=\"EUR\">22.80<",
                                 "<ram:CalculatedAmount currencyID=\"EUR\">20.90<")
                        .replace("<ram:BasisAmount currencyID=\"EUR\">120.00<",
                                 "<ram:BasisAmount currencyID=\"EUR\">110.00<")
                        .replace("<ram:AllowanceTotalAmount currencyID=\"EUR\">0.00<",
                                 "<ram:AllowanceTotalAmount currencyID=\"EUR\">10.00<")
                        .replace("<ram:TaxBasisTotalAmount currencyID=\"EUR\">120.00<",
                                 "<ram:TaxBasisTotalAmount currencyID=\"EUR\">110.00<")
                        .replace("<ram:TaxTotalAmount currencyID=\"EUR\">22.80<",
                                 "<ram:TaxTotalAmount currencyID=\"EUR\">20.90<")
                        .replace("<ram:GrandTotalAmount currencyID=\"EUR\">142.80<",
                                 "<ram:GrandTotalAmount currencyID=\"EUR\">130.90<")
                        .replace("<ram:DuePayableAmount currencyID=\"EUR\">142.80<",
                                 "<ram:DuePayableAmount currencyID=\"EUR\">130.90<");
    assertTrue(xml.contains(">10.00<") && xml.contains(">130.90<"),
               xml);
    return xml;
  }

  private static byte[] bytes(String xml) {
    return xml.getBytes(StandardCharsets.UTF_8);
  }

  @Test
  void aSampleWithoutDeviationPassesTheCheck() {
    var result = PANIOLO.read(bytes(basic()),
                              ReadMode.LENIENT);
    assertTrue(result.warnings()
                     .stream()
                     .noneMatch(w -> ReadWarning.ALLOWANCE_CHARGE_TOTAL.equals(w.code())),
               result.warnings()::toString);
    assertSame(result,
               result.requireNoDeviations());
  }

  @Test
  void strictReadingRejectsTheDocument() {
    assertThrows(IllegalArgumentException.class,
                 () -> PANIOLO.read(bytes(withAllowanceTotal()),
                                    ReadMode.STRICT));
  }

  @Test
  void lenientReadingNamesTheStatedAndTheListedTotal() {
    for (var mode : new ReadMode[] { ReadMode.LENIENT,
                                     ReadMode.LENIENT_KEEP_LINES }) {
      var result = PANIOLO.read(bytes(withAllowanceTotal()),
                                mode);
      var warning = result.warnings()
                          .stream()
                          .filter(w -> ReadWarning.ALLOWANCE_CHARGE_TOTAL.equals(w.code()))
                          .findFirst()
                          .orElseThrow(() -> new AssertionError(result.warnings()
                                                                      .toString()));
      assertTrue(warning.message()
                        .contains("BT-107") &&
                 warning.message()
                        .contains("10.00") &&
                 warning.message()
                        .contains("0.00"),
                 warning.message());
      assertTrue(result.deviations()
                       .stream()
                       .anyMatch(d -> d.businessTerm()
                                       .equals("BT-107") &&
                                      d.stated()
                                       .compareTo(new BigDecimal("10.00")) == 0 &&
                                      d.calculated()
                                       .signum() == 0),
                 result.deviations()::toString);
      // The invoice keeps the amounts of its lines; its totals are not the stated ones.
      assertEquals(0,
                   new BigDecimal("120.00").compareTo(result.invoice()
                                                            .totals()
                                                            .taxExclusiveAmount()));
    }
  }

  @Test
  void aCallerCanRefuseTheResult() {
    var result = PANIOLO.read(bytes(withAllowanceTotal()),
                              ReadMode.LENIENT);
    var e = assertThrows(IllegalArgumentException.class,
                         result::requireNoDeviations);
    assertTrue(e.getMessage()
                .contains("BT-107: stated 10.00, invoice 0.00"),
               e::getMessage);
  }
}

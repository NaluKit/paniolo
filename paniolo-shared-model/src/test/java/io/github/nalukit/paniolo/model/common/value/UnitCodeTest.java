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
package io.github.nalukit.paniolo.model.common.value;

import io.github.nalukit.paniolo.model.common.InvoiceLine;
import io.github.nalukit.paniolo.model.common.TaxCategory;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UnitCodeTest {
  @Test
  void containsTheEn16931UnitCodes() {
    assertEquals(2162,
                 UnitCode.values().length);
    assertSame(UnitCode.HUR,
               UnitCode.of("HUR"));
    assertSame(UnitCode.CODE_10,
               UnitCode.of("10"));
    assertSame(UnitCode.XBX,
               UnitCode.of("XBX"));
    assertNull(UnitCode.of("PCS"));
    assertNull(UnitCode.of(null));
  }

  @Test
  void translatesCommonUnitsAndFallsBackToEnglish() {
    assertEquals("hour",
                 UnitCode.HUR.getDescription());
    assertEquals("Stunde",
                 UnitCode.HUR.getDescription(Locale.GERMAN));
    assertEquals("Stück",
                 UnitCode.H87.getDescription(Locale.GERMAN));
    assertEquals("Pauschale",
                 UnitCode.LS.getDescription(Locale.GERMAN));
    assertEquals("Päckchen",
                 UnitCode.XPA.getDescription(Locale.GERMAN));
    // Units without German text use the English name.
    assertEquals("group",
                 UnitCode.CODE_10.getDescription(Locale.GERMAN));
    assertEquals("C62 - Stück (Einheit)",
                 UnitCode.C62.toString(Locale.GERMAN));
    assertEquals("C62 - one",
                 UnitCode.C62.toString());
  }

  @Test
  void invoiceLineBuilderAcceptsEnumAndCode() {
    var tax = TaxCategory.standard(new BigDecimal("19"));
    var line = InvoiceLine.create()
                          .id("1")
                          .name("Beratung")
                          .quantity(BigDecimal.ONE)
                          .unitCode("HUR")
                          .netUnitPrice(BigDecimal.TEN)
                          .tax(tax)
                          .build();
    assertSame(UnitCode.HUR,
               line.unitCode());
    assertEquals(line,
                 InvoiceLine.create()
                            .id("1")
                            .name("Beratung")
                            .quantity(BigDecimal.ONE)
                            .unitCode(UnitCode.HUR)
                            .netUnitPrice(BigDecimal.TEN)
                            .tax(tax)
                            .build());
    assertThrows(IllegalArgumentException.class,
                 () -> InvoiceLine.create()
                                  .unitCode("Stunden"));
  }
}

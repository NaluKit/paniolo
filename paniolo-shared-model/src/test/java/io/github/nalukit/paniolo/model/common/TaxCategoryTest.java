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

package io.github.nalukit.paniolo.model.common;

import io.github.nalukit.paniolo.model.common.value.DutyTaxFeeCategory;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The model implements a part of the EN 16931 VAT categories; the message for the others must not
 * claim that EN 16931 does not know them.
 */
class TaxCategoryTest {

  @Test
  void rejectsCategoryBAsNotImplementedNotAsUnknownToEn16931() {
    var refused = assertThrows(IllegalArgumentException.class,
                               () -> new TaxCategory(DutyTaxFeeCategory.B,
                                                     BigDecimal.ZERO,
                                                     null,
                                                     null));
    String message = refused.getMessage();
    assertTrue(message.contains("B") && message.contains("split payment") && message.contains("not implemented"),
               message);
    assertTrue(message.contains("S, Z, E, AE, K, G, O, L and M"),
               message);
    assertFalse(message.contains("knows the categories") || message.contains("only"),
                message);
  }
}

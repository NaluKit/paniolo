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
package io.github.nalukit.paniolo.xml;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * A stated amount of a document that differs from the value of the invoice model read from it.
 *
 * @param businessTerm the EN 16931 business term, e.g. {@code BT-131}
 * @param lineId       identifier of the affected line (BT-126), or null for the document level
 * @param stated       the amount the document states
 * @param calculated   the amount of the invoice model, or null if the model has none
 */
public record AmountDeviation(String businessTerm, String lineId, BigDecimal stated, BigDecimal calculated) {
  /**
   * Creates the deviation.
   *
   * @throws NullPointerException if businessTerm or stated is null
   */
  public AmountDeviation {
    Objects.requireNonNull(businessTerm,
                           "businessTerm");
    Objects.requireNonNull(stated,
                           "stated");
  }

  /**
   * Returns the deviation if the amounts differ in value; a missing calculated amount counts as zero.
   *
   * @param businessTerm the EN 16931 business term
   * @param lineId       the affected line, or null for the document level
   * @param stated       the stated amount, or null if the document states none
   * @param calculated   the amount of the model, or null
   * @return the deviation, or null if nothing is stated or the amounts are equal
   */
  public static AmountDeviation of(String businessTerm,
                                   String lineId,
                                   BigDecimal stated,
                                   BigDecimal calculated) {
    if (stated == null || stated.compareTo(calculated == null ?
                                           BigDecimal.ZERO :
                                           calculated) == 0) {
      return null;
    }
    return new AmountDeviation(businessTerm,
                               lineId,
                               stated,
                               calculated);
  }
}

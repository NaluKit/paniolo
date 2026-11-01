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
import java.util.List;
import java.util.Objects;

/**
 * The amounts exactly as a document states them, independent of what the model calculates. Every
 * value is null if the document does not state it.
 *
 * @param lineTotal          sum of invoice line net amounts (BT-106)
 * @param allowanceTotal     sum of allowances on document level (BT-107)
 * @param chargeTotal        sum of charges on document level (BT-108)
 * @param taxExclusiveAmount invoice total amount without VAT (BT-109)
 * @param taxAmount          invoice total VAT amount (BT-110)
 * @param taxInclusiveAmount invoice total amount with VAT (BT-112)
 * @param paidAmount         paid amount (BT-113)
 * @param roundingAmount     rounding amount (BT-114)
 * @param payableAmount      amount due for payment (BT-115)
 * @param lines              the stated amounts of each line and sub line, in reading order
 */
public record StatedAmounts(BigDecimal lineTotal,
                            BigDecimal allowanceTotal,
                            BigDecimal chargeTotal,
                            BigDecimal taxExclusiveAmount,
                            BigDecimal taxAmount,
                            BigDecimal taxInclusiveAmount,
                            BigDecimal paidAmount,
                            BigDecimal roundingAmount,
                            BigDecimal payableAmount,
                            List<Line> lines) {
  /**
   * No stated amounts, e.g. for a result that was not read from a document.
   */
  public static final StatedAmounts NONE = new StatedAmounts(null,
                                                             null,
                                                             null,
                                                             null,
                                                             null,
                                                             null,
                                                             null,
                                                             null,
                                                             null,
                                                             List.of());

  /**
   * Creates the stated amounts and copies the lines.
   *
   * @throws NullPointerException if lines is null
   */
  public StatedAmounts {
    lines = List.copyOf(lines);
  }

  /**
   * Returns the stated amounts with the given lines.
   *
   * @param lines the stated amounts of the lines
   * @return a copy with the lines
   */
  public StatedAmounts withLines(List<Line> lines) {
    return new StatedAmounts(lineTotal,
                             allowanceTotal,
                             chargeTotal,
                             taxExclusiveAmount,
                             taxAmount,
                             taxInclusiveAmount,
                             paidAmount,
                             roundingAmount,
                             payableAmount,
                             lines);
  }

  /**
   * The amounts of one invoice line as the document states them.
   *
   * @param id        the line identifier (BT-126)
   * @param netPrice  the stated item net price (BT-146), or null
   * @param netAmount the stated line net amount (BT-131), or null
   */
  public record Line(String id, BigDecimal netPrice, BigDecimal netAmount) {
    /**
     * Creates the line amounts.
     *
     * @throws NullPointerException if id is null
     */
    public Line {
      Objects.requireNonNull(id,
                             "id");
    }
  }
}

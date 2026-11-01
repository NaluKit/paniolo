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

import java.util.Objects;

/**
 * A deviation a reader accepted in {@link ReadMode#LENIENT}.
 *
 * @param code    rule identifier, e.g. {@link #LINE_NET_PRICE}
 * @param lineId  identifier of the affected line (BT-126), or null for the document level
 * @param message description with the stated and the resulting values
 */
public record ReadWarning(String code, String lineId, String message) {
  /**
   * The net price of a line was recalculated from its stated net amount (BT-131). The price
   * discount (BT-147) follows the new net price; stated parts of the old discount (EXTENDED gross
   * price adjustments) no longer add up and are dropped.
   */
  public static final String LINE_NET_PRICE = "READ-LINE-NET-PRICE";
  /**
   * The stated total VAT amount (BT-110) differs from the calculated one.
   */
  public static final String TAX_TOTAL      = "READ-TAX-TOTAL";
  /**
   * The stated amount due for payment (BT-115) differs from the calculated one.
   */
  public static final String PAYABLE_AMOUNT = "READ-PAYABLE-AMOUNT";
  /**
   * An allowance stated a reason code (BT-98 / BT-140) outside UNTDID 5189; it was dropped and the
   * reason text kept.
   */
  public static final String REASON_CODE    = "READ-REASON-CODE";
  /**
   * An allowance or charge stated a percentage (BT-94 / BT-101 / BT-138 / BT-143) without its base
   * amount; the percentage was dropped and the amount kept. The message names the percentage.
   */
  public static final String PERCENTAGE     = "READ-PERCENTAGE";
  /**
   * A payment terms line (BT-20) starts like an XRechnung cash discount but cannot be read as one,
   * e.g. with empty values or zero days; it was kept as text. The message names the line.
   */
  public static final String CASH_DISCOUNT  = "READ-CASH-DISCOUNT";
  /**
   * The document states more identifiers than the model keeps for a business term with one
   * identifier, e.g. the deliver-to location (BT-71); the message names the identifier not kept.
   */
  public static final String IDENTIFIER     = "READ-IDENTIFIER";
  /**
   * A further payment means block states another payment means, text, card, or mandate than the
   * first one; the model keeps one payment instruction (BG-16), so the block was not merged. The
   * message names its values.
   */
  public static final String PAYMENT_MEANS  = "READ-PAYMENT-MEANS";
  /**
   * A ZUGFeRD 1.0 line carried a text only, without item and quantity; the model has no such line,
   * so the text became an invoice note (BG-1). The message names the position and the text.
   */
  public static final String TEXT_LINE      = "READ-TEXT-LINE";
  /**
   * The document states EN 16931:2026 (e.g. XRechnung 4.0) in its specification identifier
   * (BT-24), which Paniolo does not support yet; it was read with the business terms of
   * EN 16931:2017, and terms new in EN 16931:2026 were not read.
   */
  public static final String SPECIFICATION_VERSION = "READ-SPECIFICATION-VERSION";

  /**
   * Creates the warning.
   *
   * @throws NullPointerException if code or message is null
   */
  public ReadWarning {
    Objects.requireNonNull(code,
                           "code");
    Objects.requireNonNull(message,
                           "message");
  }
}

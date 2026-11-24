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

/**
 * How an invoice reader treats amounts the model does not reproduce. The mode does not select
 * XML schema or business rule validation, which must be run separately.
 *
 * <p>All modes reject a number that is not a decimal of the syntax, e.g. {@code 100,00},
 * {@code 1,238.00}, or {@code 2 HUR}: the message names the text and the location of the element
 * ({@code .../ram:IncludedSupplyChainTradeLineItem[2]/...}). Surrounding whitespace is allowed.
 * Use {@link InvoiceXmlReader#importXml(byte[], ReadMode)} to keep the original invoice XML for
 * display or archiving.
 */
public enum ReadMode {
  /**
   * Rejects a line amount beyond the rounding tolerance of its net price, or a stated VAT total
   * (BT-110) or amount due (BT-115) that differs from the model, with an
   * {@link IllegalArgumentException}. Stated line and VAT amounts within the model's rounding
   * tolerances are retained. This is not a guarantee of lossless import: other stated totals and
   * retained line amounts can still appear in {@link ReadResult#deviations()}, and values the model
   * does not keep can produce warnings, e.g. {@link ReadWarning#IDENTIFIER}.
   */
  STRICT,
  /**
   * Accepts supported deviations and reports them as {@link ReadWarning}s: a line whose stated
   * amount exceeds the price-rounding tolerance can have its net price recalculated or its quantity
   * negated, and calculated totals are kept when the stated totals differ. Amount differences are
   * also listed in {@link ReadResult#deviations()}. Structural errors are still rejected.
   */
  LENIENT,
  /**
   * Reads like {@link #LENIENT}, but never changes the net price (BT-146) or the quantity (BT-129)
   * of a line: a line whose stated net amount is not quantity x net price, beyond the rounding of
   * the net price, is rejected with an {@link IllegalArgumentException}, as in {@link #STRICT}.
   * Every other deviation (totals, categories, texts) is accepted and reported as in
   * {@link #LENIENT}. Use it when the written invoice must carry the unit prices of the document.
   */
  LENIENT_KEEP_LINES
}

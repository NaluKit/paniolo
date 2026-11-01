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
 * How a reader treats amounts of a document that the model does not reproduce.
 *
 * <p>Both modes reject a number that is not a decimal of the syntax, e.g. {@code 100,00},
 * {@code 1,238.00}, or {@code 2 HUR}: the message names the text and the location of the element
 * ({@code .../ram:IncludedSupplyChainTradeLineItem[2]/...}). No number is guessed; the original
 * document stays available for display or archiving. Surrounding whitespace is allowed.
 */
public enum ReadMode {
  /**
   * Rejects the document with an {@link IllegalArgumentException}: a line net amount that is not
   * quantity x net price, or stated totals that differ from the calculated ones, indicate content
   * outside the model that would otherwise be lost silently. Values the model does not keep but that
   * change no amount, e.g. {@link ReadWarning#IDENTIFIER}, are still reported as warnings.
   */
  STRICT,
  /**
   * Reads the document anyway and reports each deviation as a {@link ReadWarning}: the net price of
   * a line is recalculated from its stated net amount, and the totals of the model are kept when the
   * stated totals differ. Structural errors are still rejected.
   */
  LENIENT
}

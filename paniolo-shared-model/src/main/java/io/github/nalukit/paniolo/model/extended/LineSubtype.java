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
package io.github.nalukit.paniolo.model.extended;

import java.util.Arrays;

/**
 * Subtype of an invoice line (BT-X-8, {@code LineStatusReasonCode}) in the Factur-X profile
 * EXTENDED. Only regular lines count in the totals and the VAT breakdown.
 */
public enum LineSubtype {
  /**
   * Regular line; the default when no subtype is given.
   */
  DETAIL,
  /**
   * Subtotal of its sub-lines; its net amount equals the sum of the regular and subtotal lines
   * directly below it.
   */
  GROUP,
  /**
   * Line for information only, e.g. a component of a set.
   */
  INFORMATION;

  /**
   * Returns the subtype of a code.
   *
   * @param code the code, e.g. {@code GROUP}
   * @return the subtype, or null if the code is unknown
   */
  public static LineSubtype of(String code) {
    return Arrays.stream(values())
                 .filter(value -> value.name()
                                       .equals(code))
                 .findFirst()
                 .orElse(null);
  }

  /**
   * Returns true if lines of this subtype count in the totals (BR-FXEXT-CO-10).
   *
   * @return true for {@link #DETAIL}
   */
  public boolean counts() {
    return this == DETAIL;
  }
}

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
package io.github.nalukit.paniolo.mustang;

import java.util.Objects;

/**
 * A value of a Mustang invoice that the Paniolo invoice does not carry, e.g. because EN 16931 has no
 * business term for it or because it needs the Factur-X profile EXTENDED. The loss keeps the
 * original value and what it belongs to, so a migration can store or review it.
 *
 * @param getter  the Mustang interface and getter, e.g. {@code IZUGFeRDExportableContact.getFax}
 * @param reason  why the value is not carried over, naming the party, line, or document it belongs to
 * @param subject what the value belongs to, e.g. {@code the contact Erika Muster of the buyer Kunde
 *                GmbH}, or null if the getter alone names it
 * @param value   the original value as the getter returns it, dates as {@code LocalDate} in the time
 *                zone of the adapter, or null if the loss stands for a group of values
 */
public record MappingLoss(String getter, String reason, String subject, Object value) {
  /**
   * Applies the checks of the model.
   *
   * @throws NullPointerException if getter or reason is null
   */
  public MappingLoss {
    Objects.requireNonNull(getter,
                           "getter");
    Objects.requireNonNull(reason,
                           "reason");
  }

  /**
   * A loss without subject and value.
   *
   * @param getter the Mustang interface and getter
   * @param reason why the value is not carried over
   * @throws NullPointerException if a value is null
   */
  public MappingLoss(String getter,
                     String reason) {
    this(getter,
         reason,
         null,
         null);
  }

  @Override
  public String toString() {
    if (value == null) {
      return getter + ": " + reason;
    }
    return getter + " [" + (subject == null ?
                            "" :
                            subject + ": ") + value + "]: " + reason;
  }
}

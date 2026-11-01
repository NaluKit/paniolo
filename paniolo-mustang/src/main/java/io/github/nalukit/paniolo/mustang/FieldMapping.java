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
 * How {@link MustangInvoiceAdapter} carries over one getter of Mustang's export interfaces.
 *
 * @param getter the Mustang interface and getter, e.g. {@code IZUGFeRDExportableItem.getPrice}
 * @param status mapped, EXTENDED only, decision, or lost
 * @param target the EN 16931 business term and the Paniolo field, or {@code -}
 * @param note   conversion, condition, or the reason of a decision or loss
 */
public record FieldMapping(String getter, Status status, String target, String note) {
  /**
   * Applies the checks of the model.
   *
   * @throws NullPointerException if a value is null
   */
  public FieldMapping {
    Objects.requireNonNull(getter,
                           "getter");
    Objects.requireNonNull(status,
                           "status");
    Objects.requireNonNull(target,
                           "target");
    Objects.requireNonNull(note,
                           "note");
  }

  /**
   * How a getter is carried over.
   */
  public enum Status {
    /**
     * Mapped in every profile.
     */
    MAPPED,
    /**
     * Mapped if the target profile is the Factur-X profile EXTENDED; otherwise reported as loss.
     */
    EXTENDED,
    /**
     * Mapped with a change of representation that the note describes, e.g. joined notes.
     */
    DECISION,
    /**
     * Not carried over; a set value is reported as loss.
     */
    LOST
  }
}

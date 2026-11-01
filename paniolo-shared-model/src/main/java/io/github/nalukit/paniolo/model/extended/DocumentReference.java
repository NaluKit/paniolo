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

import io.github.nalukit.paniolo.model.common.validation.ModelChecks;

import java.time.LocalDate;

/**
 * Reference to a related document of the Factur-X profile EXTENDED, e.g. a quotation or delivery
 * note.
 *
 * @param id        document identifier; may be null if lineId is given, e.g. for an Order-X line
 *                  that references a line of the order it answers
 * @param lineId    line of the referenced document, for references of an invoice line; or null
 * @param issueDate issue date of the document, or null
 */
public record DocumentReference(String id, String lineId, LocalDate issueDate) {
  /**
   * Applies the checks of the model.
   *
   * @throws IllegalArgumentException if a value violates the checks of the model
   */
  public DocumentReference {
    if (id != null || lineId == null) {
      ModelChecks.required(id,
                           "document reference id");
    }
    if (lineId != null) {
      ModelChecks.required(lineId,
                           "document reference line id");
    }
  }

  /**
   * Reference to a whole document.
   *
   * @param id        document identifier
   * @param issueDate issue date of the document, or null
   * @throws IllegalArgumentException if the identifier is blank
   */
  public DocumentReference(String id,
                           LocalDate issueDate) {
    this(id,
         null,
         issueDate);
  }

  /**
   * Reference to a whole document without issue date.
   *
   * @param id document identifier
   * @return the reference
   * @throws IllegalArgumentException if the identifier is blank
   */
  public static DocumentReference of(String id) {
    return new DocumentReference(id,
                                 null,
                                 null);
  }

  /**
   * Reference to a line of another document, e.g. an order line.
   *
   * @param id     document identifier, or null
   * @param lineId line of the referenced document
   * @return the reference
   * @throws IllegalArgumentException if the line identifier is blank
   */
  public static DocumentReference ofLine(String id,
                                         String lineId) {
    return new DocumentReference(id,
                                 lineId,
                                 null);
  }
}

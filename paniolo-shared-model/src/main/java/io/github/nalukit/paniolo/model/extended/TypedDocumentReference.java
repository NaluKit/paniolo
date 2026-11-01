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

/**
 * Additional document referenced by an invoice line in the Factur-X profile EXTENDED, with its
 * document type, e.g. {@code 130} for an invoiced object.
 *
 * @param id                document identifier
 * @param typeCode          UNTDID 1001 document type
 * @param referenceTypeCode UNTDID 1153 reference type, or null
 */
public record TypedDocumentReference(String id, String typeCode, String referenceTypeCode) {
  /**
   * Applies the checks of the model.
   *
   * @throws IllegalArgumentException if a value violates the checks of the model
   */
  public TypedDocumentReference {
    ModelChecks.required(id,
                         "document reference id");
    ModelChecks.required(typeCode,
                         "document reference type code");
  }
}

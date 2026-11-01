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
package io.github.nalukit.paniolo.model.order;

/**
 * Further document referenced by an Order-X order (COMFORT), e.g. a technical specification of a
 * line or a product sheet, as {@code ram:AdditionalReferencedDocument} or, for a product,
 * {@code ram:AdditionalReferenceReferencedDocument}.
 *
 * @param id                document identifier, or null
 * @param uri               location of the document, e.g. a URL; or null
 * @param lineId            line of the referenced document, or null
 * @param typeCode          UNTDID 1001 document type, e.g. 916 for a supporting document; or null
 * @param name              name or description of the document, or null
 * @param referenceTypeCode UNTDID 1153 qualifier of the identifier, or null
 */
public record OrderDocument(String id, String uri, String lineId, String typeCode, String name, String referenceTypeCode) {
  /**
   * Applies the checks of the model.
   *
   * @throws IllegalArgumentException if a value violates the checks of the model
   */
  public OrderDocument {
    if (id == null && uri == null && name == null) {
      throw new IllegalArgumentException("A referenced document needs an identifier, a location, or a name");
    }
  }
}

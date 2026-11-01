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
package io.github.nalukit.paniolo.config;

/**
 * What the validation methods of the facade that detect the document type do with a document whose
 * type cannot be detected: XML that is not well-formed or has no root element, an unsupported root
 * element, or such XML embedded in a Factur-X / ZUGFeRD PDF. Set it with
 * {@code Paniolo.builder().unknownDocuments(...)}.
 *
 * <p>Methods with an explicit document type, e.g. {@code validate(xml, ValidationProfile.Syntax)},
 * never detect and always report malformed XML as finding.
 */
public enum UnknownDocumentHandling {
  /**
   * Default: an {@link IllegalArgumentException} says that the document is no supported invoice.
   * The caller distinguishes "not an invoice" from "an invalid invoice", e.g. to route the file
   * elsewhere.
   */
  THROW,
  /**
   * Compatibility mode, like Mustang: the result is invalid with a {@code FATAL} finding, so every
   * input yields a {@code ValidationResult}. Malformed XML is reported as {@code XML-PARSE} with its
   * position, every other undetectable document as {@code PANIOLO-UNKNOWN-DOCUMENT} with the root
   * element; both in stage {@code XML}, or {@code HYBRID} for the XML embedded in a PDF.
   */
  REPORT_INVALID
}

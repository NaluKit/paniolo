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
package io.github.nalukit.paniolo.validation;

/**
 * A finding with the original rule identifier and XML location, where provided.
 *
 * @param ruleId   original rule identifier, or a Paniolo check identifier
 * @param severity severity used to determine validity
 * @param message  human-readable explanation of the finding
 * @param location XML location supplied by the rule, or null when unavailable
 * @param stage    stage that produced this finding
 */
public record ValidationMessage(String ruleId, Severity severity, String message, String location, Stage stage) {
  /**
   * Processing stage that produced a finding. Use the stage to distinguish XML, schema, business
   * rule and PDF failures without parsing the human-readable message.
   */
  public enum Stage {
    /**
     * XML parsing.
     */
    XML,
    /**
     * XML schema validation.
     */
    XSD,
    /**
     * Schematron business rules (EN 16931, XRechnung).
     */
    SCHEMATRON,
    /**
     * PDF/A conformance of a hybrid invoice (ISO 19005).
     */
    PDF_A,
    /**
     * Factur-X / ZUGFeRD container: embedded file, relationship and XMP metadata.
     */
    HYBRID,
    /**
     * Optional recalculation of the amounts with the invoice model; findings in addition to the
     * official rules, with identifiers starting with {@code PANIOLO-CALC-}.
     */
    CALCULATION
  }
}

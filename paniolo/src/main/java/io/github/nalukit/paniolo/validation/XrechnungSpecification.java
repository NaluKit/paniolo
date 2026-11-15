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

import java.util.Objects;

/**
 * A specification identifier (BT-24) that selects a scenario of a KoSIT XRechnung validator
 * configuration for one document type, e.g.
 * {@code urn:cen.eu:en16931:2017#compliant#urn:xeinkauf.de:kosit:xrechnung_3.0} for the standard
 * scenario of UBL Invoice. The identifier is compared exactly, as the KoSIT validator does: no
 * case folding, no trimming, no prefix match.
 *
 * @param identifier the complete specification identifier; required
 * @param scenario   the scenario the identifier selects; required
 * @param syntax     the document type the scenario exists for; required
 */
public record XrechnungSpecification(String identifier, XrechnungScenario scenario, ValidationProfile.Syntax syntax) {
  /**
   * Validates the components.
   *
   * @throws NullPointerException     if a component is null
   * @throws IllegalArgumentException if the identifier is blank
   */
  public XrechnungSpecification {
    Objects.requireNonNull(identifier,
                           "identifier");
    Objects.requireNonNull(scenario,
                           "scenario");
    Objects.requireNonNull(syntax,
                           "syntax");
    if (identifier.isBlank()) {
      throw new IllegalArgumentException("identifier must not be blank");
    }
  }

  /**
   * Returns true if this entry accepts the specification identifier for the document type.
   *
   * @param specificationId the specification identifier of the document; may be null
   * @param type            the document type
   * @return true if both match exactly
   */
  public boolean accepts(String specificationId,
                         ValidationProfile.Syntax type) {
    return syntax == type && identifier.equals(specificationId);
  }

  /**
   * Scenario and document type of a scenario name.
   */
  record Target(XrechnungScenario scenario, ValidationProfile.Syntax syntax) {
  }
}

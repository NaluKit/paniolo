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

import java.util.List;

/**
 * Validation scenario of a KoSIT XRechnung validator configuration. Each scenario is selected by an
 * exact specification identifier (BT-24) and exists only for some document types; see
 * {@link ValidationProfile#xrechnungSpecifications(java.nio.file.Path)}.
 */
public enum XrechnungScenario {
  /**
   * The XRechnung CIUS, e.g. {@code EN16931 XRechnung (UBL Invoice)}; releases for XRechnung 1.2
   * name it {@code EN16931 CIUS XRechnung (UBL Invoice)}.
   */
  STANDARD("EN16931 XRechnung ",
           "EN16931 CIUS XRechnung "),
  /**
   * The XRechnung extension, e.g. {@code EN16931 XRechnung Extension (UBL Invoice)}: the XRechnung
   * rules with severity overrides for sub invoice lines, third party payments, and further code
   * values.
   */
  EXTENSION("EN16931 XRechnung Extension "),
  /**
   * XRechnung for the Clean Vehicles Directive, e.g. {@code EN16931 XRechnung CVD (UBL Invoice)},
   * defined since XRechnung 3.0.
   */
  CVD("EN16931 XRechnung CVD ");

  private final List<String> prefixes;

  XrechnungScenario(String... prefixes) {
    this.prefixes = List.of(prefixes);
  }

  /**
   * Returns the scenario names of this scenario for a document type, in the order they are looked
   * up in {@code scenarios.xml}.
   *
   * @param syntax the document type
   * @return the scenario names
   */
  List<String> names(ValidationProfile.Syntax syntax) {
    return prefixes.stream()
                   .map(prefix -> prefix + suffix(syntax))
                   .toList();
  }

  /**
   * Returns the scenario and document type of a scenario name, or null for a scenario name that is
   * none of these.
   */
  static XrechnungSpecification.Target of(String scenarioName) {
    for (var scenario : values()) {
      for (var syntax : ValidationProfile.Syntax.values()) {
        if (scenario.names(syntax)
                    .contains(scenarioName)) {
          return new XrechnungSpecification.Target(scenario,
                                                   syntax);
        }
      }
    }
    return null;
  }

  private static String suffix(ValidationProfile.Syntax syntax) {
    return switch (syntax) {
      case UBL_INVOICE -> "(UBL Invoice)";
      case UBL_CREDIT_NOTE -> "(UBL CreditNote)";
      case CII -> "(CII)";
    };
  }
}

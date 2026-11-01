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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * What the PDF/A check did for one document: the flavour checked, the validation profile, the
 * number of checks, and how often each rule failed. Collected only on request; the findings of
 * stage {@link ValidationMessage.Stage#PDF_A} name each failed rule once.
 *
 * @param flavour      PDF/A flavour checked, e.g. {@code 3b}
 * @param profile      name of the PDF/A validation profile, or null
 * @param compliant    true if the checker found the document compliant with the flavour
 * @param checks       checks executed, passed and failed
 * @param failedChecks failed checks, every occurrence
 * @param failedRules  failed checks per rule, e.g. {@code 6.2.11.4.1-1}; copied
 */
public record PdfaStatistics(String flavour, String profile, boolean compliant, int checks, int failedChecks, Map<String, Integer> failedRules) {
  /**
   * Applies the checks of the model and copies the map.
   *
   * @throws NullPointerException     if the flavour or the map is null
   * @throws IllegalArgumentException if a count is negative
   */
  public PdfaStatistics {
    Objects.requireNonNull(flavour,
                           "flavour");
    if (checks < 0 || failedChecks < 0) {
      throw new IllegalArgumentException("PDF/A statistics must not be negative");
    }
    // Insertion order: the order the checker reported the rules.
    failedRules = Collections.unmodifiableMap(new LinkedHashMap<>(failedRules));
  }
}

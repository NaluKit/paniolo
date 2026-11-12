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

import java.util.ArrayList;
import java.util.List;

/**
 * Execution statistics of a validation: one entry per Schematron stylesheet run and per PDF/A
 * check. Part of a {@link ValidationResult} only if requested; the official validity does not
 * depend on it.
 *
 * @param schematron statistics of the Schematron stylesheets in the order they ran; copied
 * @param pdfa       statistics of the PDF/A checks; copied
 */
public record ValidationStatistics(List<SchematronStatistics> schematron, List<PdfaStatistics> pdfa) {
  /**
   * Copies the lists.
   *
   * @throws NullPointerException if a list is null
   */
  public ValidationStatistics {
    schematron = List.copyOf(schematron);
    pdfa       = List.copyOf(pdfa);
  }

  /**
   * Combines the statistics of several stages, e.g. of the PDF/A check and the XML validation of a
   * hybrid invoice. Null entries are statistics that were not collected.
   *
   * @param parts the statistics to combine, in reporting order; entries may be null
   * @return the combined statistics, or null if no part has statistics
   */
  public static ValidationStatistics merge(ValidationStatistics... parts) {
    List<SchematronStatistics> schematron = new ArrayList<>();
    List<PdfaStatistics>       pdfa       = new ArrayList<>();
    boolean                    collected  = false;
    for (ValidationStatistics part : parts) {
      if (part != null) {
        collected = true;
        schematron.addAll(part.schematron());
        pdfa.addAll(part.pdfa());
      }
    }
    return collected ?
           new ValidationStatistics(schematron,
                                    pdfa) :
           null;
  }

  /**
   * Total of the rules that fired in all Schematron stylesheets.
   *
   * @return the sum of {@link SchematronStatistics#firedRules()}
   */
  public int firedRules() {
    return schematron.stream()
                     .mapToInt(SchematronStatistics::firedRules)
                     .sum();
  }
}

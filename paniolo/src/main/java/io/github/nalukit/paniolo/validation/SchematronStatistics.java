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
 * What one Schematron stylesheet did for one document, counted from its SVRL report. Collected only
 * on request (see {@link InvoiceValidator#InvoiceValidator(ValidationProfile, boolean)}), because
 * counting fired rules makes the stylesheet report every matched rule context.
 *
 * <p>The counts show how much of a rule set was applied, e.g. for audits or to notice documents
 * that the rules hardly touch: a document for which no rule fired was not checked in substance,
 * even if it has no findings.
 *
 * @param stylesheet        file name of the compiled Schematron stylesheet
 * @param activePatterns    patterns (rule groups) the stylesheet evaluated ({@code svrl:active-pattern})
 * @param firedRules        rule contexts that matched a node of the document ({@code svrl:fired-rule});
 *                          a rule counts once per matched node
 * @param failedAsserts     failed asserts ({@code svrl:failed-assert}), every occurrence
 * @param successfulReports successful reports ({@code svrl:successful-report}), every occurrence
 */
public record SchematronStatistics(String stylesheet, int activePatterns, int firedRules, int failedAsserts, int successfulReports) {
  /**
   * Applies the checks of the model.
   *
   * @throws NullPointerException     if the stylesheet is null
   * @throws IllegalArgumentException if a count is negative
   */
  public SchematronStatistics {
    Objects.requireNonNull(stylesheet,
                           "stylesheet");
    if (activePatterns < 0 || firedRules < 0 || failedAsserts < 0 || successfulReports < 0) {
      throw new IllegalArgumentException("Schematron statistics must not be negative");
    }
  }
}

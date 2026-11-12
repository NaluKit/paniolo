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
package io.github.nalukit.paniolo.spi;

import io.github.nalukit.paniolo.config.PdfLayout;
import io.github.nalukit.paniolo.model.Invoice;
import io.github.nalukit.paniolo.model.despatch.DespatchAdvice;
import io.github.nalukit.paniolo.model.order.Order;
import io.github.nalukit.paniolo.validation.ValidationResult;

import java.time.Instant;
import java.util.Objects;

/**
 * Service provider that renders the visual PDF/A document of an invoice, order, or despatch
 * advice, implemented by {@code paniolo-facturx} and found with
 * {@link java.util.ServiceLoader}. Implementations must be thread-safe.
 */
public interface PdfLayoutSupport {

  /**
   * Renders an invoice as PDF/A-3b document without embedded XML.
   *
   * @param invoice the invoice
   * @param layout  language, logo, fonts, and colors
   * @return the PDF/A-3b document
   * @throws IllegalArgumentException if the logo or a font cannot be read
   */
  byte[] render(Invoice invoice,
                PdfLayout layout);

  /**
   * Renders an Order-X order, order change, or order response as PDF/A-3b document without
   * embedded XML.
   *
   * @param order  the order
   * @param layout language, logo, fonts, and colors
   * @return the PDF/A-3b document
   * @throws IllegalArgumentException if the logo or a font cannot be read
   */
  default byte[] render(Order order,
                        PdfLayout layout) {
    throw new UnsupportedOperationException("This PDF layout implementation does not render orders");
  }

  /**
   * Renders a despatch advice as delivery note, a PDF/A-3b document without embedded XML.
   *
   * @param advice the despatch advice
   * @param layout language, logo, fonts, and colors
   * @return the PDF/A-3b document
   * @throws IllegalArgumentException if the logo or a font cannot be read
   */
  default byte[] render(DespatchAdvice advice,
                        PdfLayout layout) {
    throw new UnsupportedOperationException("This PDF layout implementation does not render despatch advices");
  }

  /**
   * Renders a validation report as PDF/A-3b document, e.g. for archiving or for departments without
   * access to the machine-readable report.
   *
   * @param report the validation result with the information about the validated document
   * @param layout language, fonts, colors, and footer; logo and sender address are not used
   * @return the PDF/A-3b document
   * @throws UnsupportedOperationException if the implementation does not render reports
   */
  default byte[] render(Report report,
                        PdfLayout layout) {
    throw new UnsupportedOperationException("This PDF layout implementation does not render validation reports");
  }

  /**
   * A validation report to render: the result and what it refers to.
   *
   * @param result      the validation result; its findings are rendered completely
   * @param fileName    name of the validated file, or null
   * @param sha256      SHA-256 of the validated content in hexadecimal, or null
   * @param size        size of the validated content in bytes, or -1 if unknown
   * @param validatedAt time of the validation
   * @param rules       the rule releases the validation used, e.g. "KoSIT XRechnung 3.0.2 (2026-08-31)"
   */
  record Report(ValidationResult result, String fileName, String sha256, long size, Instant validatedAt, String rules) {
    /**
     * Creates the report.
     *
     * @throws NullPointerException if result or validatedAt is null
     */
    public Report {
      Objects.requireNonNull(result,
                             "result");
      Objects.requireNonNull(validatedAt,
                             "validatedAt");
    }
  }
}

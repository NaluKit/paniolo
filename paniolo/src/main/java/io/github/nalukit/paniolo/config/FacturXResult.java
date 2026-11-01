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

import io.github.nalukit.paniolo.validation.ValidationResult;

import java.util.Arrays;
import java.util.Objects;

/**
 * A Factur-X / ZUGFeRD PDF created from existing invoice XML, with the validation of the result.
 *
 * <p>The validation covers the container (embedded file and XMP metadata), PDF/A conformance if
 * {@code paniolo-pdfa} is present, and the embedded XML with the rules of the profile. It does
 * <em>not</em> cover whether the visual PDF shows the same content as the XML; that remains the
 * responsibility of whoever created the PDF.
 */
public final class FacturXResult {
  private final byte[]           pdf;
  private final ValidationResult validation;

  /**
   * Creates the result and copies the PDF.
   *
   * @param pdf        the Factur-X PDF/A-3 document
   * @param validation the validation of the document
   * @throws NullPointerException if an argument is null
   */
  public FacturXResult(byte[] pdf,
                       ValidationResult validation) {
    this.pdf        = Objects.requireNonNull(pdf,
                                             "pdf")
                             .clone();
    this.validation = Objects.requireNonNull(validation,
                                             "validation");
  }

  /**
   * Returns the Factur-X PDF/A-3 document.
   *
   * @return a copy of the PDF bytes
   */
  public byte[] pdf() {
    return pdf.clone();
  }

  /**
   * Returns the validation of container, PDF/A and embedded XML.
   *
   * @return the validation result
   */
  public ValidationResult validation() {
    return validation;
  }

  /**
   * Whether the validation found no errors.
   *
   * @return true if the document is valid
   */
  public boolean isValid() {
    return validation.isValid();
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof FacturXResult other && Arrays.equals(pdf,
                                                             other.pdf) && validation.equals(other.validation);
  }

  @Override
  public int hashCode() {
    return 31 * Arrays.hashCode(pdf) + validation.hashCode();
  }

  @Override
  public String toString() {
    return "FacturXResult[pdf=" + pdf.length + " bytes, validation=" + validation.profileName() + ", valid=" + isValid() + "]";
  }
}

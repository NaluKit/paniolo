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

import io.github.nalukit.paniolo.config.PdfaScriptCache;
import io.github.nalukit.paniolo.validation.ValidationMessage;
import io.github.nalukit.paniolo.validation.ValidationResult;

import java.util.List;

/**
 * Service provider for PDF/A conformance checks of hybrid invoices, implemented by
 * {@code paniolo-pdfa} and found with {@link java.util.ServiceLoader}. Implementations must be
 * thread-safe.
 */
public interface PdfaSupport {

  /**
   * Validates the PDF/A conformance declared in the document's XMP metadata. Factur-X requires
   * PDF/A-3; other parts are reported as errors.
   *
   * @param pdf the PDF document
   * @return findings of stage {@code PDF_A}; empty if the document conforms
   */
  List<ValidationMessage> validate(byte[] pdf);

  /**
   * Validates like {@link #validate(byte[])}; with {@code statistics} the result also carries the
   * {@link io.github.nalukit.paniolo.validation.PdfaStatistics} of the check. Implementations
   * without statistics return the findings only.
   *
   * @param pdf        the PDF document
   * @param statistics whether to collect the statistics of the check
   * @return the findings of stage {@code PDF_A}, with statistics if requested and available
   */
  default ValidationResult check(byte[] pdf,
                                 boolean statistics) {
    return new ValidationResult("PDF/A",
                                validate(pdf));
  }

  /**
   * Validates like {@link #check(byte[], boolean)} and handles the caches the implementation keeps
   * for the calling thread as the strategy says: with {@link PdfaScriptCache#CLEAR_AFTER_CHECK} they
   * are released on the calling thread after the check, also when it fails. Implementations
   * without such caches only check.
   *
   * @param pdf        the PDF document
   * @param statistics whether to collect the statistics of the check
   * @param cache      what to do with the caches of the calling thread
   * @return the findings of stage {@code PDF_A}, with statistics if requested and available
   */
  default ValidationResult check(byte[] pdf,
                                 boolean statistics,
                                 PdfaScriptCache cache) {
    try {
      return check(pdf,
                   statistics);
    } finally {
      if (cache == PdfaScriptCache.CLEAR_AFTER_CHECK) {
        releaseThreadCaches();
      }
    }
  }

  /**
   * Releases the caches the implementation keeps for the calling thread, e.g. compiled rules. Safe
   * to call at any time, also with empty caches; later checks build them again. Caches of other
   * threads are not affected. The default does nothing.
   */
  default void releaseThreadCaches() {
  }
}

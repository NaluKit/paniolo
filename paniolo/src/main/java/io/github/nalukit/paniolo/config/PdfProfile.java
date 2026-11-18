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
 * Kind of PDF that {@code paniolo-facturx} renders from an invoice, order, or despatch advice; set
 * with {@link PdfLayout#withProfile(PdfProfile)}.
 */
public enum PdfProfile {
  /**
   * A plain PDF 1.7 to look at, e.g. with a watermark: no PDF/A, no output intent, transparency
   * allowed. Not accepted for documents with embedded XML.
   */
  PREVIEW,
  /**
   * Like {@link #PREVIEW}, as PDF 2.0 (ISO 32000-2), without PDF/A. Not accepted for documents with
   * embedded XML.
   */
  PREVIEW_2_0,
  /**
   * PDF/A-1b (ISO 19005-1, PDF 1.4): no transparency, no object streams. Fit as the visual PDF/A
   * that is passed to {@code toFacturX}. Not meant for sending as it is when it carries a
   * watermark.
   */
  PDF_A_1B,
  /**
   * PDF/A-3b (ISO 19005-3, based on PDF 1.7; PDF 2.0 is not allowed): the default, and the format of documents created with
   * {@code toFacturX}, {@code toOrderXPdf}, and the like.
   */
  PDF_A_3B;

  /**
   * Returns true for the profiles without PDF/A, {@link #PREVIEW} and {@link #PREVIEW_2_0}.
   *
   * @return true for a plain PDF
   */
  public boolean isPreview() {
    return this == PREVIEW || this == PREVIEW_2_0;
  }
}

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
package io.github.nalukit.paniolo.xml;

/**
 * Which document an {@link ImportResult} exports.
 */
public enum ExportVariant {
  /**
   * The received XML, byte for byte; no amount is changed.
   */
  ORIGINAL,
  /**
   * The invoice model written anew; amounts the model recalculated on reading replace the stated
   * ones, see {@link ImportResult#deviations()}. Refused if the document states taxes of another
   * type than VAT ({@link ReadWarning#OTHER_TAX}), as the export would drop them and lower the
   * amount due; see {@link #NORMALIZED_DROPPING_OTHER_TAXES}.
   */
  NORMALIZED,
  /**
   * Like {@link #NORMALIZED}, but the caller explicitly accepts that taxes of another type than VAT
   * ({@link ReadWarning#OTHER_TAX}, e.g. the insurance tax AAM) are not written: their amounts are
   * missing from the total tax amount and the amount due of the export.
   */
  NORMALIZED_DROPPING_OTHER_TAXES;

  /**
   * Whether the variant writes the invoice model anew.
   *
   * @return true for {@link #NORMALIZED} and {@link #NORMALIZED_DROPPING_OTHER_TAXES}
   */
  public boolean isNormalized() {
    return this != ORIGINAL;
  }
}

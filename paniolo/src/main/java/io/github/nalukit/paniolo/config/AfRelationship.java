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

import java.util.Arrays;

/**
 * Relationship of an embedded file to the PDF document ({@code /AFRelationship}, PDF 2.0 / PDF/A-3).
 */
public enum AfRelationship {
  /**
   * Original source of the content, e.g. the spreadsheet a report was made from.
   */
  SOURCE("Source"),
  /**
   * Data used for the visual content, e.g. the invoice XML of MINIMUM and BASIC WL.
   */
  DATA("Data"),
  /**
   * Alternative representation of the content, e.g. the invoice XML from BASIC upwards.
   */
  ALTERNATIVE("Alternative"),
  /**
   * Supplementary file, e.g. a time sheet or delivery note belonging to the invoice.
   */
  SUPPLEMENT("Supplement"),
  /**
   * No relationship stated or none of the above.
   */
  UNSPECIFIED("Unspecified");

  private final String pdfName;

  AfRelationship(String pdfName) {
    this.pdfName = pdfName;
  }

  /**
   * Returns the relationship of a PDF name; UNSPECIFIED for missing and unknown names.
   *
   * @param pdfName value of {@code /AFRelationship}, e.g. {@code Data}, or null
   * @return the relationship, never null
   */
  public static AfRelationship ofPdfName(String pdfName) {
    return Arrays.stream(values())
                 .filter(value -> value.pdfName.equals(pdfName))
                 .findFirst()
                 .orElse(UNSPECIFIED);
  }

  /**
   * Value of {@code /AFRelationship} in the PDF.
   *
   * @return the PDF name of the relationship
   */
  public String getPdfName() {
    return pdfName;
  }
}

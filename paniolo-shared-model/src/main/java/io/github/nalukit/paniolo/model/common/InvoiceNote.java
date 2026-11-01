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
package io.github.nalukit.paniolo.model.common;

import io.github.nalukit.paniolo.model.common.validation.ModelChecks;
import io.github.nalukit.paniolo.model.common.value.TextSubjectQualifier;

import java.util.regex.Pattern;

/**
 * Invoice note (BG-1) with an optional UNTDID 4451 subject code (BT-21). The code is kept as text,
 * because EN 16931 permits several hundred codes; {@link TextSubjectQualifier} covers the common
 * ones and is accepted by the builder. Whether a code is permitted is checked by validation.
 * The content code belongs to the Factur-X profile EXTENDED.
 *
 * @param subjectCode UNTDID 4451 subject code (BT-21), or null
 * @param text        invoice note text (BT-22); must not be blank
 * @param contentCode content code for Factur-X EXTENDED, or null
 */
public record InvoiceNote(String subjectCode, String text, String contentCode) {
  private static final Pattern CODE = Pattern.compile("[A-Z0-9]{1,3}");

  /**
   * Applies the checks of the model.
   *
   * @throws IllegalArgumentException if a value violates the checks of the model
   */
  public InvoiceNote {
    if (subjectCode != null &&
        !CODE.matcher(subjectCode)
             .matches()) {
      throw new IllegalArgumentException("Note subject code must be a UNTDID 4451 code: " + subjectCode);
    }
    ModelChecks.required(text,
                         "note");
    if (contentCode != null) {
      ModelChecks.required(contentCode,
                           "note content code");
    }
  }

  /**
   * A note without content code.
   *
   * @param subjectCode UNTDID 4451 subject code (BT-21), or null
   * @param text        note text (BT-22)
   * @throws IllegalArgumentException if the subject code is malformed or the text is blank
   */
  public InvoiceNote(String subjectCode,
                     String text) {
    this(subjectCode,
         text,
         null);
  }

  /**
   * A note without subject code.
   *
   * @param text note text (BT-22)
   * @return the note
   * @throws IllegalArgumentException if the text is blank
   */
  public static InvoiceNote of(String text) {
    return new InvoiceNote(null,
                           text);
  }

  /**
   * Starts named construction of a {@link InvoiceNote} using the builder defaults.
   *
   * @return a new builder; not shared with other calls
   */
  public static Builder create() {
    return new Builder();
  }

  /**
   * Returns the subject code as {@link TextSubjectQualifier}, or null if absent or not covered by it.
   *
   * @return the subject qualifier, or null
   */
  public TextSubjectQualifier subjectQualifier() {
    return subjectCode == null ?
           null :
           TextSubjectQualifier.of(subjectCode);
  }

  /**
   * Named construction of an immutable note.
   *
   * <p>Mutable construction state for {@link InvoiceNote}. Use a separate builder per operation;
   * builders are not thread-safe. {@link #build()} constructs a new immutable value and applies
   * the checks of the model. XML schema and business rule validation are separate operations.
   */
  public static final class Builder {
    private String subjectCode;
    private String text;
    private String contentCode;

    /**
     * Creates a builder with the defaults of the model; usually obtained with {@link InvoiceNote#create()}.
     */
    public Builder() {
    }

    /**
     * Sets {@link InvoiceNote#subjectCode() subjectCode}.
     *
     * @param subjectCode UNTDID 4451 subject code (BT-21), or null
     * @return this builder
     */
    public Builder subjectCode(TextSubjectQualifier subjectCode) {
      this.subjectCode = subjectCode == null ?
                         null :
                         subjectCode.getCode();
      return this;
    }

    /**
     * Sets a UNTDID 4451 subject code that {@link TextSubjectQualifier} does not cover, e.g. {@code ADU}.
     *
     * @param subjectCode UNTDID 4451 subject code (BT-21), or null
     * @return this builder
     */
    public Builder subjectCode(String subjectCode) {
      this.subjectCode = subjectCode;
      return this;
    }

    /**
     * Sets {@link InvoiceNote#text() text}.
     *
     * @param text invoice note text (BT-22); must not be blank
     * @return this builder
     */
    public Builder text(String text) {
      this.text = text;
      return this;
    }

    /**
     * Content code of the note, Factur-X profile EXTENDED only.
     *
     * @param contentCode content code for Factur-X EXTENDED, or null
     * @return this builder
     */
    public Builder contentCode(String contentCode) {
      this.contentCode = contentCode;
      return this;
    }

    /**
     * Constructs a new {@link InvoiceNote} from the current values.
     *
     * @return the constructed value after the model checks succeed
     */
    public InvoiceNote build() {
      return new InvoiceNote(subjectCode,
                             text,
                             contentCode);
    }
  }
}

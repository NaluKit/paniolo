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

import java.util.Arrays;
import java.util.Objects;
import java.util.Set;

/**
 * Embedded attachment (BT-125) with its MIME code and file name; the content is copied defensively.
 *
 * @param content  attachment bytes (BT-125); required and copied on construction and access
 * @param mimeCode MIME type (BT-125-1); must not be blank
 * @param filename attachment file name (BT-125-2); must not be blank
 */
public record Attachment(byte[] content, String mimeCode, String filename) {
  /**
   * MIME codes permitted by EN 16931 (BR-CL-24) and the XRechnung extension.
   */
  public static final Set<String> ALLOWED_MIME_CODES   = Set.of("application/pdf",
                                                                "image/png",
                                                                "image/jpeg",
                                                                "text/csv",
                                                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                                                "application/vnd.oasis.opendocument.spreadsheet",
                                                                "application/xml");
  /**
   * MIME codes the XRechnung extension adds to EN 16931 (BR-DEX-01); other specifications reject them.
   */
  public static final Set<String> EXTENSION_MIME_CODES = Set.of("application/xml");

  /**
   * Applies the checks of the model and copies the arrays.
   *
   * @throws NullPointerException     if a required value is null
   * @throws IllegalArgumentException if a value violates the checks of the model
   */
  public Attachment {
    content = Objects.requireNonNull(content,
                                     "content")
                     .clone();
    if (!ALLOWED_MIME_CODES.contains(ModelChecks.required(mimeCode,
                                                          "mimeCode"))) {
      throw new IllegalArgumentException("Unsupported attachment MIME code: " + mimeCode);
    }
    ModelChecks.required(filename,
                         "filename");
  }

  /**
   * Starts named construction of a {@link Attachment} using the builder defaults.
   *
   * @return a new builder; not shared with other calls
   */
  public static Builder create() {
    return new Builder();
  }

  /**
   * Returns a copy of the attached content.
   *
   * @return the attached content
   */
  @Override
  public byte[] content() {
    return content.clone();
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof Attachment other && Arrays.equals(content,
                                                          other.content) && mimeCode.equals(other.mimeCode) && filename.equals(other.filename);
  }

  @Override
  public int hashCode() {
    return Objects.hash(Arrays.hashCode(content),
                        mimeCode,
                        filename);
  }

  @Override
  public String toString() {
    return "Attachment[content=" + content.length + " bytes, mimeCode=" + mimeCode + ", filename=" + filename + "]";
  }

  /**
   * Named construction of an immutable attachment.
   *
   * <p>Mutable construction state for {@link Attachment}. Use a separate builder per operation;
   * builders are not thread-safe. {@link #build()} constructs a new immutable value and applies
   * the checks of the model. XML schema and business rule validation are separate operations.
   */
  public static final class Builder {
    private byte[] content;
    private String mimeCode;
    private String filename;

    /**
     * Creates a builder with the defaults of the model; usually obtained with {@link Attachment#create()}.
     */
    public Builder() {
    }

    /**
     * Sets {@link Attachment#content() content}.
     *
     * @param content attachment bytes (BT-125); required and copied on construction and access
     * @return this builder
     */
    public Builder content(byte[] content) {
      this.content = content;
      return this;
    }

    /**
     * Sets {@link Attachment#mimeCode() mimeCode}.
     *
     * @param mimeCode MIME type (BT-125-1); must not be blank
     * @return this builder
     */
    public Builder mimeCode(String mimeCode) {
      this.mimeCode = mimeCode;
      return this;
    }

    /**
     * Sets {@link Attachment#filename() filename}.
     *
     * @param filename attachment file name (BT-125-2); must not be blank
     * @return this builder
     */
    public Builder filename(String filename) {
      this.filename = filename;
      return this;
    }

    /**
     * Constructs a new {@link Attachment} from the current values.
     *
     * @return the constructed value after the model checks succeed
     */
    public Attachment build() {
      return new Attachment(content,
                            mimeCode,
                            filename);
    }
  }
}

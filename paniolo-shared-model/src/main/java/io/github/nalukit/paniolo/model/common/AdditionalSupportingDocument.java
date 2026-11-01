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

/**
 * Additional supporting document (BG-24), referenced externally or embedded as attachment.
 *
 * @param reference        supporting document identifier (BT-122); must not be blank
 * @param description      supporting document description (BT-123), or null
 * @param externalLocation external document location (BT-124), or null
 * @param attachment       embedded document (BT-125), or null
 */
public record AdditionalSupportingDocument(String reference, String description, String externalLocation, Attachment attachment) {
  /**
   * Applies the checks of the model.
   *
   * @throws IllegalArgumentException if a value violates the checks of the model
   */
  public AdditionalSupportingDocument {
    ModelChecks.required(reference,
                         "supporting document reference");
  }

  /**
   * Starts named construction of a {@link AdditionalSupportingDocument} using the builder defaults.
   *
   * @return a new builder; not shared with other calls
   */
  public static Builder create() {
    return new Builder();
  }

  /**
   * Named construction of an immutable supporting document.
   *
   * <p>Mutable construction state for {@link AdditionalSupportingDocument}. Use a separate builder per operation;
   * builders are not thread-safe. {@link #build()} constructs a new immutable value and applies
   * the checks of the model. XML schema and business rule validation are separate operations.
   */
  public static final class Builder {
    private String     reference;
    private String     description;
    private String     externalLocation;
    private Attachment attachment;

    /**
     * Creates a builder with the defaults of the model; usually obtained with {@link AdditionalSupportingDocument#create()}.
     */
    public Builder() {
    }

    /**
     * Supporting document reference (BT-122).
     *
     * @param reference supporting document identifier (BT-122); must not be blank
     * @return this builder
     */
    public Builder reference(String reference) {
      this.reference = reference;
      return this;
    }

    /**
     * Supporting document description (BT-123).
     *
     * @param description supporting document description (BT-123), or null
     * @return this builder
     */
    public Builder description(String description) {
      this.description = description;
      return this;
    }

    /**
     * External document location URL (BT-124).
     *
     * @param externalLocation external document location (BT-124), or null
     * @return this builder
     */
    public Builder externalLocation(String externalLocation) {
      this.externalLocation = externalLocation;
      return this;
    }

    /**
     * Attached document (BT-125).
     *
     * @param attachment embedded document (BT-125), or null
     * @return this builder
     */
    public Builder attachment(Attachment attachment) {
      this.attachment = attachment;
      return this;
    }

    /**
     * Constructs a new {@link AdditionalSupportingDocument} from the current values.
     *
     * @return the constructed value after the model checks succeed
     */
    public AdditionalSupportingDocument build() {
      return new AdditionalSupportingDocument(reference,
                                              description,
                                              externalLocation,
                                              attachment);
    }
  }
}

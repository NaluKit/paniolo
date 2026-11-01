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
package io.github.nalukit.paniolo.model.order;

import java.util.Arrays;

/**
 * Order-X profile, stated as specification identifier in the document context.
 */
public enum OrderXProfile {
  /**
   * Order-X BASIC.
   */
  BASIC("urn:order-x.eu:1p0:basic"),
  /**
   * Order-X COMFORT.
   */
  COMFORT("urn:order-x.eu:1p0:comfort"),
  /**
   * Order-X EXTENDED.
   */
  EXTENDED("urn:order-x.eu:1p0:extended");

  private final String specificationId;

  OrderXProfile(String specificationId) {
    this.specificationId = specificationId;
  }

  /**
   * Returns the profile of a specification identifier; surrounding whitespace is ignored.
   *
   * @param specificationId the specification identifier, or null
   * @return the profile, or null if the identifier is null or unknown
   */
  public static OrderXProfile ofSpecificationId(String specificationId) {
    return specificationId == null ?
           null :
           Arrays.stream(values())
                 .filter(value -> value.specificationId.equals(specificationId.strip()))
                 .findFirst()
                 .orElse(null);
  }

  /**
   * Specification identifier, e.g. {@code urn:order-x.eu:1p0:basic}.
   *
   * @return the specification identifier
   */
  public String getSpecificationId() {
    return specificationId;
  }

  /**
   * Value of the XMP property {@code fx:ConformanceLevel} in an Order-X PDF.
   *
   * @return the XMP conformance level
   */
  public String getConformanceLevel() {
    return name();
  }
}

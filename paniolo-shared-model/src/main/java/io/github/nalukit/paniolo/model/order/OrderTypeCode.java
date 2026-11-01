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
 * Document type of an Order-X document (UNTDID 1001).
 */
public enum OrderTypeCode {
  /**
   * Purchase order.
   */
  ORDER("220",
        "ORDER"),
  /**
   * Change of an order sent before.
   */
  ORDER_CHANGE("230",
               "ORDER_CHANGE"),
  /**
   * The seller's response to an order or order change.
   */
  ORDER_RESPONSE("231",
                 "ORDER_RESPONSE");

  private final String code;
  private final String documentType;

  OrderTypeCode(String code,
                String documentType) {
    this.code         = code;
    this.documentType = documentType;
  }

  /**
   * Returns the order type of a UNTDID 1001 code.
   *
   * @param code the code, e.g. {@code 220}
   * @return the order type, or null if the code is not an Order-X type
   */
  public static OrderTypeCode of(String code) {
    return Arrays.stream(values())
                 .filter(value -> value.code.equals(code))
                 .findFirst()
                 .orElse(null);
  }

  /**
   * UNTDID 1001 code, e.g. {@code 220}.
   *
   * @return the code
   */
  public String getCode() {
    return code;
  }

  /**
   * Value of the XMP property {@code fx:DocumentType} in an Order-X PDF.
   *
   * @return the XMP document type
   */
  public String getDocumentType() {
    return documentType;
  }
}

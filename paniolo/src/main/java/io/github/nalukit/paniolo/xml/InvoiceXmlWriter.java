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

import io.github.nalukit.paniolo.model.Invoice;

import java.nio.charset.StandardCharsets;

/**
 * Converts a semantic invoice to XML. Run validation separately before sending it.
 *
 * <p>The writers produce EN 16931:2017; an invoice whose specification identifier (BT-24) states
 * EN 16931:2026, e.g. XRechnung 4.0, is rejected until its official artefacts are published.
 */
public interface InvoiceXmlWriter {
  /**
   * Writes the invoice as XML.
   *
   * @param invoice the invoice
   * @return the XML document
   * @throws IllegalArgumentException if the invoice contains content the syntax or profile does
   *                                  not support
   */
  String toXml(Invoice invoice);

  /**
   * Writes the invoice as UTF-8 encoded XML.
   *
   * @param invoice the invoice
   * @return the UTF-8 encoded XML document
   * @throws IllegalArgumentException if the invoice contains content the syntax or profile does
   *                                  not support
   */
  default byte[] toBytes(Invoice invoice) {
    return toXml(invoice).getBytes(StandardCharsets.UTF_8);
  }
}

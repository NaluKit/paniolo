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
import io.github.nalukit.paniolo.xml.util.XmlDocuments;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.UncheckedIOException;

/**
 * Converts XML into a semantic invoice. Totals are recalculated from the model and compared with
 * the document; in {@link ReadMode#STRICT} a mismatch indicates content the model does not support
 * and is rejected, in {@link ReadMode#LENIENT} it is reported as a {@link ReadWarning}.
 */
public interface InvoiceXmlReader {
  /**
   * Reads a parsed document.
   *
   * @param document the namespace-aware DOM document; must not be modified concurrently
   * @param mode     how deviations from the model are handled
   * @return the invoice with the deviations accepted in {@link ReadMode#LENIENT}
   * @throws IllegalArgumentException if the document is not a supported invoice or, in
   *                                  {@link ReadMode#STRICT}, deviates from the model
   */
  ReadResult read(Document document,
                  ReadMode mode);

  /**
   * Reads a parsed document in {@link ReadMode#STRICT}.
   *
   * @param document the namespace-aware DOM document; must not be modified concurrently
   * @return the invoice
   * @throws IllegalArgumentException if the document is not a supported invoice or deviates from
   *                                  the model
   */
  default Invoice read(Document document) {
    return read(document,
                ReadMode.STRICT).invoice();
  }

  /**
   * Reads XML text in {@link ReadMode#STRICT}.
   *
   * @param xml the XML document
   * @return the invoice
   * @throws IllegalArgumentException if the XML is not well-formed, not a supported invoice, or
   *                                  deviates from the model
   */
  default Invoice fromXml(String xml) {
    return fromXml(xml,
                   ReadMode.STRICT).invoice();
  }

  /**
   * Reads XML text; the encoding named in the XML declaration is ignored.
   *
   * @param xml  the XML document
   * @param mode how deviations from the model are handled
   * @return the invoice with the deviations accepted in {@link ReadMode#LENIENT}
   * @throws IllegalArgumentException if the XML is not well-formed, not a supported invoice or,
   *                                  in {@link ReadMode#STRICT}, deviates from the model
   */
  default ReadResult fromXml(String xml,
                             ReadMode mode) {
    try {
      return read(XmlDocuments.parse(new StringReader(xml)),
                  mode);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } catch (SAXException e) {
      throw new IllegalArgumentException("Invoice XML is not well-formed",
                                         e);
    }
  }

  /**
   * Reads encoded XML in {@link ReadMode#STRICT}.
   *
   * @param xml the XML document
   * @return the invoice
   * @throws IllegalArgumentException if the XML is not well-formed, not a supported invoice, or
   *                                  deviates from the model
   */
  default Invoice fromBytes(byte[] xml) {
    return fromStream(new ByteArrayInputStream(xml));
  }

  /**
   * Reads encoded XML.
   *
   * @param xml  the XML document
   * @param mode how deviations from the model are handled
   * @return the invoice with the deviations accepted in {@link ReadMode#LENIENT}
   * @throws IllegalArgumentException if the XML is not well-formed, not a supported invoice or,
   *                                  in {@link ReadMode#STRICT}, deviates from the model
   */
  default ReadResult fromBytes(byte[] xml,
                               ReadMode mode) {
    return fromStream(new ByteArrayInputStream(xml),
                      mode);
  }

  /**
   * Imports a received document: reads it and keeps the original XML and the stated amounts next to
   * the invoice model. Use this instead of {@link #fromBytes(byte[], ReadMode)} when the amounts as
   * stated must stay available, e.g. for booking or archiving received invoices, and choose with
   * {@link ImportResult#export(ExportVariant, InvoiceXmlWriter)} between the original and the
   * normalized document.
   *
   * @param xml  the XML document; copied, later changes do not affect the result
   * @param mode how deviations from the model are handled
   * @return the original XML, the stated amounts, the invoice and their deviations
   * @throws IllegalArgumentException if the XML is not well-formed, not a supported invoice or,
   *                                  in {@link ReadMode#STRICT}, deviates from the model
   */
  default ImportResult importXml(byte[] xml,
                                 ReadMode mode) {
    byte[] original = xml.clone();
    return new ImportResult(original,
                            mode,
                            fromBytes(original,
                                      mode));
  }

  /**
   * Reads in {@link ReadMode#STRICT} without closing the caller's stream.
   *
   * @param input the XML document
   * @return the invoice
   * @throws IllegalArgumentException     if the XML is not well-formed, not a supported invoice, or
   *                                      deviates from the model
   * @throws java.io.UncheckedIOException if the stream cannot be read
   */
  default Invoice fromStream(InputStream input) {
    return fromStream(input,
                      ReadMode.STRICT).invoice();
  }

  /**
   * Reads without closing the caller's stream.
   *
   * @param input the XML document
   * @param mode  how deviations from the model are handled
   * @return the invoice with the deviations accepted in {@link ReadMode#LENIENT}
   * @throws IllegalArgumentException     if the XML is not well-formed, not a supported invoice or,
   *                                      in {@link ReadMode#STRICT}, deviates from the model
   * @throws java.io.UncheckedIOException if the stream cannot be read
   */
  default ReadResult fromStream(InputStream input,
                                ReadMode mode) {
    try {
      return read(XmlDocuments.parse(input),
                  mode);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } catch (SAXException e) {
      throw new IllegalArgumentException("Invoice XML is not well-formed",
                                         e);
    }
  }
}

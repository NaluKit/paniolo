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
package io.github.nalukit.paniolo.spi;

import io.github.nalukit.paniolo.config.FacturXProfile;
import io.github.nalukit.paniolo.config.PdfAttachment;
import io.github.nalukit.paniolo.config.PdfProducer;
import io.github.nalukit.paniolo.model.order.OrderTypeCode;
import io.github.nalukit.paniolo.model.order.OrderXProfile;
import io.github.nalukit.paniolo.validation.ValidationMessage;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.function.Function;

/**
 * Service provider for Factur-X / ZUGFeRD PDF handling, implemented by {@code paniolo-facturx}
 * and found with {@link java.util.ServiceLoader}. Keeps PDF libraries out of the facade.
 * Implementations must be thread-safe.
 */
public interface FacturXSupport {

  /**
   * Embeds the CII XML into a PDF/A document and returns the Factur-X PDF/A-3 document.
   *
   * @param pdf     PDF/A-1, -2 or -3 document with the visual invoice
   * @param xml     CII invoice XML matching the profile
   * @param profile Factur-X profile
   * @return the Factur-X document
   * @throws IllegalArgumentException if the PDF is not PDF/A, encrypted or already contains an invoice
   */
  byte[] embed(byte[] pdf,
               byte[] xml,
               FacturXProfile profile);

  /**
   * Embeds the CII XML and further files into a PDF/A document and returns the Factur-X PDF/A-3
   * document. Implementations without attachment support accept only an empty list.
   *
   * @param pdf         PDF/A-1, -2 or -3 document with the visual invoice
   * @param xml         CII invoice XML matching the profile
   * @param profile     Factur-X profile
   * @param attachments further files, each associated with the document
   * @return the Factur-X document
   * @throws IllegalArgumentException      if the PDF is not PDF/A, encrypted or already contains an
   *                                       invoice, or if an attachment name is used twice or already in
   *                                       the PDF
   * @throws UnsupportedOperationException if attachments are given and the implementation does not
   *                                       support them
   */
  default byte[] embed(byte[] pdf,
                       byte[] xml,
                       FacturXProfile profile,
                       List<PdfAttachment> attachments) {
    if (!attachments.isEmpty()) {
      throw new UnsupportedOperationException("This Factur-X implementation does not support attachments");
    }
    return embed(pdf,
                 xml,
                 profile);
  }

  /**
   * Replaces the embedded invoice XML, e.g. a ZUGFeRD 1.0 one, by the CII XML of a Factur-X
   * profile and rewrites the XMP metadata; other attachments are kept.
   *
   * @param pdf     PDF/A document with an embedded invoice
   * @param xml     CII invoice XML matching the profile
   * @param profile Factur-X profile of the new invoice XML
   * @return the Factur-X document with the replaced invoice
   * @throws IllegalArgumentException      if the PDF is not PDF/A or encrypted
   * @throws UnsupportedOperationException if the implementation does not replace invoices
   */
  default byte[] replaceInvoice(byte[] pdf,
                                byte[] xml,
                                FacturXProfile profile) {
    throw new UnsupportedOperationException("This Factur-X implementation cannot replace an embedded invoice");
  }

  /**
   * Embeds Order-X XML into a PDF/A document as {@code order-x.xml} and returns the Order-X
   * PDF/A-3 document.
   *
   * @param pdf         PDF/A-1, -2 or -3 document with the visual order
   * @param xml         Order-X XML matching the profile
   * @param profile     Order-X profile
   * @param type        order type, which determines the XMP document type
   * @param attachments further files, each associated with the document
   * @return the Order-X document
   * @throws UnsupportedOperationException if the implementation does not support Order-X
   */
  default byte[] embedOrder(byte[] pdf,
                            byte[] xml,
                            OrderXProfile profile,
                            OrderTypeCode type,
                            List<PdfAttachment> attachments) {
    throw new UnsupportedOperationException("This implementation does not support Order-X");
  }

  /**
   * Extracts the Order-X XML ({@code order-x.xml}) from a PDF.
   *
   * @param pdf the PDF document
   * @return the embedded Order-X XML
   * @throws IllegalArgumentException      if the PDF contains no Order-X document
   * @throws UnsupportedOperationException if the implementation does not support Order-X
   */
  default byte[] extractOrder(byte[] pdf) {
    throw new UnsupportedOperationException("This implementation does not support Order-X");
  }

  /**
   * Embeds a CIDA despatch advice into a PDF/A document as {@code cida.xml} and returns the
   * PDF/A-3 delivery note.
   *
   * @param pdf         PDF/A-1, -2 or -3 document with the visual delivery note
   * @param xml         CIDA despatch advice XML
   * @param attachments further files, each associated with the document
   * @return the PDF/A-3 delivery note
   * @throws UnsupportedOperationException if the implementation does not support despatch advices
   */
  default byte[] embedDespatchAdvice(byte[] pdf,
                                     byte[] xml,
                                     List<PdfAttachment> attachments) {
    throw new UnsupportedOperationException("This implementation does not support CIDA despatch advices");
  }

  /**
   * Extracts the CIDA despatch advice ({@code cida.xml}) from a PDF.
   *
   * @param pdf the PDF document
   * @return the embedded despatch advice XML
   * @throws IllegalArgumentException      if the PDF contains no despatch advice
   * @throws UnsupportedOperationException if the implementation does not support despatch advices
   */
  default byte[] extractDespatchAdvice(byte[] pdf) {
    throw new UnsupportedOperationException("This implementation does not support CIDA despatch advices");
  }

  /**
   * Reads producer and creator of a PDF from its document information and XMP metadata and
   * recognizes the e-invoicing software that produced it.
   *
   * @param pdf the PDF document
   * @return the producing software as far as the document tells
   * @throws IllegalArgumentException      if the content is not a readable PDF
   * @throws UnsupportedOperationException if the implementation does not read the producer
   */
  default PdfProducer producer(byte[] pdf) {
    throw new UnsupportedOperationException("This Factur-X implementation does not read the PDF producer");
  }

  /**
   * Returns the files embedded in a PDF except the invoice XML.
   *
   * @param pdf the PDF document
   * @return the attachments in document order; empty if there are none
   */
  default List<PdfAttachment> attachments(byte[] pdf) {
    throw new UnsupportedOperationException("This Factur-X implementation does not support attachments");
  }

  /**
   * Extracts the invoice XML from a Factur-X / ZUGFeRD document.
   *
   * @param pdf the PDF document
   * @return the embedded invoice
   * @throws IllegalArgumentException if the PDF contains no supported invoice XML
   */
  EmbeddedInvoice extract(byte[] pdf);

  /**
   * Hands the embedded invoice XML as stream to {@code probe}, as a cheap check whether a PDF is a
   * hybrid invoice. Implementations should neither read the XMP metadata nor buffer the XML, so the
   * probe can stop after the first elements; the default extracts the whole invoice.
   *
   * @param pdf   the PDF document
   * @param probe reads the start of the XML; its result is returned
   * @param <T>   type of the result
   * @return the result of {@code probe}, or {@code null} if the content is no readable PDF, is
   * encrypted or contains no invoice file
   */
  default <T> T probeInvoice(byte[] pdf,
                             Function<InputStream, T> probe) {
    byte[] xml;
    try {
      xml = extract(pdf).xml();
    } catch (IllegalArgumentException e) {
      return null;
    }
    return probe.apply(new ByteArrayInputStream(xml));
  }

  /**
   * Checks the Factur-X / ZUGFeRD container: embedded invoice file, its relationship and MIME type,
   * and the XMP metadata. PDF/A conformance itself is checked by {@link PdfaSupport}.
   *
   * @param pdf the PDF document
   * @return findings of stage {@code HYBRID}; empty if the container is correct
   */
  List<ValidationMessage> checkContainer(byte[] pdf);

  /**
   * Immutable invoice XML extracted from a PDF, together with its attachment name and detected
   * profile. The XML bytes are copied on construction and on access. Extraction alone does not
   * establish XML validity, agreement with the metadata, or PDF/A conformance.
   *
   * @param fileName name of the embedded file
   * @param profile  profile from the XMP metadata or file name; null if unknown
   * @param xml      XML content; must not be null
   */
  record EmbeddedInvoice(String fileName, FacturXProfile profile, byte[] xml) {
    /**
     * Creates the embedded invoice and copies the XML.
     *
     * @throws NullPointerException if {@code xml} is null
     */
    public EmbeddedInvoice {
      xml = xml.clone();
    }

    /**
     * Returns a copy of the XML content.
     *
     * @return the XML content
     */
    @Override
    public byte[] xml() {
      return xml.clone();
    }
  }
}

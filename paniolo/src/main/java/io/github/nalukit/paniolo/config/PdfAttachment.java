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

import io.github.nalukit.paniolo.model.common.Attachment;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * File embedded in a Factur-X / ZUGFeRD PDF next to the invoice XML, e.g. a time sheet. The
 * content is copied defensively.
 *
 * @param fileName     file name, unique in the PDF; not one of the invoice file names
 * @param content      file content
 * @param mimeType     MIME type, e.g. {@code application/pdf}
 * @param relationship relationship to the document; {@link AfRelationship#SUPPLEMENT} for
 *                     supplementary documents
 * @param description  description shown by PDF viewers, or null
 */
public record PdfAttachment(String fileName, byte[] content, String mimeType, AfRelationship relationship, String description) {
  /**
   * File names reserved for the invoice or order XML.
   */
  public static final List<String> INVOICE_FILE_NAMES = List.of("factur-x.xml",
                                                                "xrechnung.xml",
                                                                "zugferd-invoice.xml",
                                                                "order-x.xml",
                                                                "cida.xml");

  private static final Pattern MIME_TYPE = Pattern.compile("[A-Za-z0-9!#$&^_.+-]+/[A-Za-z0-9!#$&^_.+-]+");

  /**
   * Creates the attachment and copies the content.
   *
   * @throws IllegalArgumentException if the file name is blank, contains a path or is reserved
   *                                  for the invoice XML, or if the MIME type is invalid
   * @throws NullPointerException     if content or relationship is null
   */
  public PdfAttachment {
    if (fileName == null || fileName.isBlank()) {
      throw new IllegalArgumentException("Attachment file name must not be blank");
    }
    if (fileName.contains("/") || fileName.contains("\\")) {
      throw new IllegalArgumentException("Attachment file name must not contain a path: " + fileName);
    }
    if (INVOICE_FILE_NAMES.contains(fileName.toLowerCase(Locale.ROOT))) {
      throw new IllegalArgumentException("Attachment file name " + fileName + " is reserved for the invoice XML");
    }
    content = Objects.requireNonNull(content,
                                     "content")
                     .clone();
    if (mimeType == null ||
        !MIME_TYPE.matcher(mimeType)
                  .matches()) {
      throw new IllegalArgumentException("Attachment " + fileName + " needs a MIME type such as application/pdf, but has " + mimeType);
    }
    Objects.requireNonNull(relationship,
                           "relationship");
  }

  /**
   * A supplementary file without description.
   *
   * @param fileName file name, unique in the PDF
   * @param content  file content
   * @param mimeType MIME type, e.g. {@code application/pdf}
   * @return the attachment with relationship {@link AfRelationship#SUPPLEMENT}
   */
  public static PdfAttachment of(String fileName,
                                 byte[] content,
                                 String mimeType) {
    return new PdfAttachment(fileName,
                             content,
                             mimeType,
                             AfRelationship.SUPPLEMENT,
                             null);
  }

  /**
   * The attachment of a supporting document (BG-24) as supplementary file of the PDF.
   *
   * @param attachment the attached document of the invoice
   * @return the attachment with relationship {@link AfRelationship#SUPPLEMENT}
   */
  public static PdfAttachment of(Attachment attachment) {
    Objects.requireNonNull(attachment,
                           "attachment");
    return of(attachment.filename(),
              attachment.content(),
              attachment.mimeCode());
  }

  /**
   * Returns a copy of the file content.
   *
   * @return the file content
   */
  @Override
  public byte[] content() {
    return content.clone();
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof PdfAttachment other && fileName.equals(other.fileName) && Arrays.equals(content,
                                                                                                other.content) && mimeType.equals(other.mimeType) && relationship == other.relationship && Objects.equals(description,
                                                                                                                                                                                                          other.description);
  }

  @Override
  public int hashCode() {
    return Objects.hash(fileName,
                        Arrays.hashCode(content),
                        mimeType,
                        relationship,
                        description);
  }

  @Override
  public String toString() {
    return "PdfAttachment[fileName=" + fileName + ", size=" + content.length + ", mimeType=" + mimeType + ", relationship=" + relationship + ", description=" + description + "]";
  }
}

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

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * A received invoice: the original XML, the amounts it states, the invoice model read from it and
 * the deviations between both. The model stays the consistent, calculated view; the stated amounts
 * and the original XML keep what the sender actually wrote.
 *
 * <pre>{@code
 * ImportResult received = reader.importXml(xml, ReadMode.LENIENT);
 * received.stated().lines().get(0).netPrice();       // as stated, e.g. 101.00
 * received.invoice().lines().get(0).netUnitPrice();  // as calculated, e.g. 101.50
 * byte[] archive = received.export(ExportVariant.ORIGINAL, null);
 * }</pre>
 */
public final class ImportResult {
  private final byte[]     originalXml;
  private final ReadMode   mode;
  private final ReadResult read;

  /**
   * Creates the result and copies the original XML.
   *
   * @param originalXml the XML as received
   * @param mode        the mode the XML was read in
   * @param read        the invoice read from the XML
   * @throws NullPointerException if an argument is null
   */
  public ImportResult(byte[] originalXml,
                      ReadMode mode,
                      ReadResult read) {
    this.originalXml = Objects.requireNonNull(originalXml,
                                              "originalXml")
                              .clone();
    this.mode        = Objects.requireNonNull(mode,
                                              "mode");
    this.read        = Objects.requireNonNull(read,
                                              "read");
  }

  /**
   * Returns the XML as received.
   *
   * @return a copy of the original bytes
   */
  public byte[] originalXml() {
    return originalXml.clone();
  }

  /**
   * Returns the mode the XML was read in.
   *
   * @return the mode
   */
  public ReadMode mode() {
    return mode;
  }

  /**
   * Returns the invoice model, with the amounts the model calculates.
   *
   * @return the invoice
   */
  public Invoice invoice() {
    return read.invoice();
  }

  /**
   * Returns the amounts as the document states them.
   *
   * @return the stated amounts
   */
  public StatedAmounts stated() {
    return read.stated();
  }

  /**
   * Returns the stated amounts that differ from the invoice model.
   *
   * @return the deviations; in {@link ReadMode#STRICT} only totals that strict reading does not
   *         compare, e.g. the sum of line net amounts (BT-106)
   */
  public List<AmountDeviation> deviations() {
    return read.deviations();
  }

  /**
   * Returns the accepted deviations as messages.
   *
   * @return the warnings; always empty in {@link ReadMode#STRICT}
   */
  public List<ReadWarning> warnings() {
    return read.warnings();
  }

  /**
   * Whether the invoice model differs from the document, so that {@link ExportVariant#NORMALIZED}
   * does not reproduce the stated content.
   *
   * @return true if a deviation or a warning was accepted
   */
  public boolean isNormalized() {
    return !read.isExact() || !read.deviations()
                                   .isEmpty();
  }

  /**
   * Exports the invoice in the chosen variant.
   *
   * @param variant the original or the normalized document
   * @param writer  the writer of the normalized document; ignored for {@link ExportVariant#ORIGINAL}
   * @return the original bytes or the normalized XML encoded as UTF-8
   * @throws NullPointerException     if variant is null, or writer is null for a normalized
   *                                  variant
   * @throws IllegalArgumentException if the writer cannot express the invoice, or
   *                                  {@link ExportVariant#NORMALIZED} would drop taxes of another type
   *                                  than VAT ({@link ReadWarning#OTHER_TAX})
   */
  public byte[] export(ExportVariant variant,
                       InvoiceXmlWriter writer) {
    return switch (Objects.requireNonNull(variant,
                                          "variant")) {
      case ORIGINAL -> originalXml();
      case NORMALIZED -> {
        var dropped = read.warnings()
                          .stream()
                          .filter(warning -> ReadWarning.OTHER_TAX.equals(warning.code()))
                          .map(ReadWarning::message)
                          .toList();
        if (!dropped.isEmpty()) {
          Objects.requireNonNull(writer,
                                 "writer");
          throw new IllegalArgumentException("The normalized export would drop taxes the model does not support and change the amount due: " + String.join("; ",
                                                                                                                                                             dropped) +
                                             ". Export " + ExportVariant.ORIGINAL + ", or " + ExportVariant.NORMALIZED_DROPPING_OTHER_TAXES + " to accept the loss");
        }
        yield Objects.requireNonNull(writer,
                                     "writer")
                     .toBytes(read.invoice());
      }
      case NORMALIZED_DROPPING_OTHER_TAXES -> Objects.requireNonNull(writer,
                                                                     "writer")
                                                     .toBytes(read.invoice());
    };
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof ImportResult other && Arrays.equals(originalXml,
                                                            other.originalXml) && mode == other.mode && read.equals(other.read);
  }

  @Override
  public int hashCode() {
    return 31 * (31 * Arrays.hashCode(originalXml) + mode.hashCode()) + read.hashCode();
  }

  @Override
  public String toString() {
    return "ImportResult[mode=" + mode + ", invoice=" + read.invoice()
                                                           .number() + ", deviations=" + read.deviations() + "]";
  }
}

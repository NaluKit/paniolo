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

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Software that produced a PDF, as far as the document tells: the producer and creator of the
 * document information or XMP metadata, and the e-invoicing software recognized from its known
 * signatures, as the Mustang project recognizes them.
 *
 * @param producer  PDF producer ({@code /Producer}, XMP {@code pdf:Producer}), or null
 * @param creator   creating application ({@code /Creator}, XMP {@code xmp:CreatorTool}), or null
 * @param generator recognized e-invoicing software, e.g. {@code Mustang} or {@code Ghostscript}; or null
 */
public record PdfProducer(String producer, String creator, String generator) {
  /**
   * Signature Paniolo appends to the producer of the PDFs it creates, like Mustang's
   * {@code via mustangproject.org}.
   */
  public static final String PANIOLO_SIGNATURE = "via Paniolo";

  /**
   * Byte signatures and the software they identify, checked in this order. Paniolo comes first: it
   * keeps the producer of the source PDF, which may carry another signature.
   */
  private static final List<Signature> SIGNATURES = List.of(new Signature(PANIOLO_SIGNATURE,
                                                                          "Paniolo"),
                                                            new Signature("Symtrax",
                                                                          "Symtrax"),
                                                            new Signature("via mustangproject",
                                                                          "Mustang"),
                                                            new Signature("by Alexis de Lattre",
                                                                          "Factur/X Python"),
                                                            new Signature("intarsys ",
                                                                          "Intarsys"),
                                                            new Signature("Konik",
                                                                          "Konik"),
                                                            new Signature("pdfMachine from Broadgun Software",
                                                                          "pdfMachine"),
                                                            new Signature("%%Invocation:",
                                                                          "Ghostscript"),
                                                            new Signature("CIB pdf brewer",
                                                                          "CIB pdf brewer"),
                                                            new Signature("lexoffice",
                                                                          "Lexware office"),
                                                            new Signature("s2industries.ZUGFeRD.PDF",
                                                                          "ZUGFeRD.PDF-csharp"),
                                                            new Signature("FactoorSharp",
                                                                          "FactoorSharp"),
                                                            new Signature("sevdesk",
                                                                          "sevdesk"));

  /**
   * Creates the producer information; blank producer and creator values become null.
   */
  public PdfProducer {
    producer = blankToNull(producer);
    creator  = blankToNull(creator);
  }

  /**
   * Returns the producer with the Paniolo signature appended, e.g.
   * {@code Microsoft Word (via Paniolo)}, or {@code Paniolo (via Paniolo)} without producer; a
   * producer that already carries it stays unchanged.
   *
   * @param producer the producer of the source PDF, or null
   * @return the signed producer
   */
  public static String sign(String producer) {
    if (producer == null || producer.isBlank()) {
      return "Paniolo (" + PANIOLO_SIGNATURE + ")";
    }
    return producer.contains(PANIOLO_SIGNATURE) ?
           producer :
           producer.strip() + " (" + PANIOLO_SIGNATURE + ")";
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ?
           null :
           value.strip();
  }

  /**
   * Recognises the e-invoicing software: first from producer and creator, then from the bytes of
   * the whole document, which also finds signatures in comments and uncompressed streams.
   *
   * @param producer the PDF producer, or null
   * @param creator  the creating application, or null
   * @param pdf      the PDF document, must not be null
   * @return the software name, or null if no signature is known
   */
  public static String recognize(String producer,
                                 String creator,
                                 byte[] pdf) {
    Objects.requireNonNull(pdf,
                           "pdf");
    for (String text : new String[] { producer,
                                      creator }) {
      if (text != null) {
        for (Signature signature : SIGNATURES) {
          if (text.contains(signature.text())) {
            return signature.generator();
          }
        }
      }
    }
    for (Signature signature : SIGNATURES) {
      if (contains(pdf,
                   signature.bytes())) {
        return signature.generator();
      }
    }
    return null;
  }

  private static boolean contains(byte[] content,
                                  byte[] pattern) {
    outer:
    for (int i = 0; i <= content.length - pattern.length; i++) {
      for (int j = 0; j < pattern.length; j++) {
        if (content[i + j] != pattern[j]) {
          continue outer;
        }
      }
      return true;
    }
    return false;
  }

  /**
   * One line for reports, e.g. {@code Mustang (producer "PDFBox", creator "Mustang 2.27")}.
   *
   * @return the description of the producing software
   */
  public String describe() {
    var parts = new StringBuilder(generator == null ?
                                  "unknown software" :
                                  generator);
    var details = Arrays.stream(new String[][] { { "producer",
                                                   producer },
                                                 { "creator",
                                                   creator } })
                        .filter(detail -> detail[1] != null)
                        .map(detail -> detail[0] + " \"" + detail[1] + "\"")
                        .toList();
    if (!details.isEmpty()) {
      parts.append(" (")
           .append(String.join(", ",
                               details))
           .append(')');
    }
    return parts.toString();
  }

  /**
   * Returns true if nothing about the producing software is known.
   *
   * @return true if producer, creator, and generator are all null
   */
  public boolean isUnknown() {
    return producer == null && creator == null && generator == null;
  }

  private record Signature(String text, String generator) {
    byte[] bytes() {
      return text.getBytes(StandardCharsets.UTF_8);
    }
  }
}

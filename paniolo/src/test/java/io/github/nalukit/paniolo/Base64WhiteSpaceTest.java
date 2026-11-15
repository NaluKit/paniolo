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
package io.github.nalukit.paniolo;

import io.github.nalukit.paniolo.config.InvoiceSyntax;
import io.github.nalukit.paniolo.model.common.AdditionalSupportingDocument;
import io.github.nalukit.paniolo.model.common.Attachment;
import io.github.nalukit.paniolo.model.common.value.InvoiceTypeCode;
import io.github.nalukit.paniolo.xml.ExportVariant;
import io.github.nalukit.paniolo.xml.ReadMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * White space in the Base64 text of an embedded attachment (task C03, Mustang issue #792).
 * {@code xs:base64Binary} allows XML white space (space, tab, carriage return, line feed) anywhere,
 * also between the two padding characters, as in {@code MQ= =}; the MIME decoder rejected that
 * placement with {@code Input byte array has wrong 4-byte ending unit}. The readers now remove
 * exactly that white space and decode the rest strictly: wrong characters, wrong padding, and cut
 * off data are rejected with the location in the document.
 *
 * <p>The attachment of the short case is the single byte {@code 1}, {@code MQ==} in Base64, as in
 * the original of the issue; the long case has 100 bytes in lines of 76 characters.
 */
class Base64WhiteSpaceTest {
  private static final Paniolo PANIOLO = Paniolo.create();
  private static final byte[]  SHORT   = "1".getBytes(StandardCharsets.US_ASCII);
  private static final byte[]  LONG    = new byte[100];

  static {
    for (int index = 0; index < LONG.length; index++) {
      LONG[index] = (byte) (index * 7 + 3);
    }
  }

  private static String xml(InvoiceSyntax syntax,
                            byte[] content) {
    return PANIOLO.toXml(TestInvoices.builder(InvoiceTypeCode.CODE_380,
                                              "B64-1")
                                     .addSupportingDocument(new AdditionalSupportingDocument("ANL-1",
                                                                                             "Anhang",
                                                                                             null,
                                                                                             new Attachment(content,
                                                                                                            "text/csv",
                                                                                                            "anhang.csv")))
                                     .build(),
                         syntax);
  }

  /**
   * The document with the Base64 text of the attachment replaced.
   */
  private static String with(String xml,
                             byte[] content,
                             String text) {
    String encoded = Base64.getEncoder()
                           .encodeToString(content);
    assertTrue(xml.contains(">" + encoded + "<"),
               xml);
    return xml.replace(">" + encoded + "<",
                       ">" + text + "<");
  }

  private static byte[] bytes(String xml) {
    return xml.getBytes(StandardCharsets.UTF_8);
  }

  private static byte[] attachment(io.github.nalukit.paniolo.model.Invoice invoice) {
    return invoice.supportingDocuments()
                  .get(0)
                  .attachment()
                  .content();
  }

  @ParameterizedTest
  @EnumSource(InvoiceSyntax.class)
  void readsWhiteSpaceBetweenThePaddingCharacters(InvoiceSyntax syntax) {
    for (String text : List.of("MQ= =",
                               "MQ=\t=",
                               "MQ=\n=",
                               "MQ=\r\n=",
                               "MQ=  \n  =",
                               "M Q = =",
                               " MQ==",
                               "MQ== ",
                               "\n    MQ==\n    ",
                               "\tM\tQ\t=\t=\t")) {
      String xml = with(xml(syntax,
                            SHORT),
                        SHORT,
                        text);
      assertTrue(PANIOLO.validate(xml)
                        .isValid(),
                 () -> syntax + " " + text.replace("\n",
                                                   "\\n"));
      for (var mode : ReadMode.values()) {
        var imported = PANIOLO.importInvoice(bytes(xml),
                                             mode);
        assertArrayEquals(SHORT,
                          attachment(imported.invoice()),
                          () -> syntax + " " + mode + " " + text.replace("\n",
                                                                         "\\n"));
        assertEquals(List.of(),
                     imported.warnings());
        // The received XML is kept as it is; the normalized document writes the attachment again.
        assertArrayEquals(bytes(xml),
                          PANIOLO.export(imported,
                                         ExportVariant.ORIGINAL,
                                         null));
        String written = PANIOLO.toXml(imported.invoice(),
                                       syntax);
        assertTrue(written.contains(">MQ==<"),
                   written);
        assertTrue(PANIOLO.validate(written)
                          .isValid());
        assertArrayEquals(SHORT,
                          attachment(PANIOLO.read(written,
                                                  ReadMode.STRICT)
                                            .invoice()));
      }
    }
  }

  @ParameterizedTest
  @EnumSource(InvoiceSyntax.class)
  void readsLongDataInLinesBeforeInsideAndAfter(InvoiceSyntax syntax) {
    String encoded = Base64.getEncoder()
                           .encodeToString(LONG);
    var    wrapped = new StringBuilder("\n  ");
    for (int at = 0; at < encoded.length(); at += 76) {
      wrapped.append(encoded,
                     at,
                     Math.min(encoded.length(),
                              at + 76))
             .append("\r\n  ");
    }
    for (String text : List.of(wrapped.toString(),
                               encoded.substring(0,
                                                 10) + " \t " + encoded.substring(10))) {
      String xml = with(xml(syntax,
                            LONG),
                        LONG,
                        text);
      for (var mode : ReadMode.values()) {
        assertArrayEquals(LONG,
                          attachment(PANIOLO.read(xml,
                                                  mode)
                                            .invoice()),
                          () -> syntax + " " + mode);
      }
    }
  }

  @ParameterizedTest
  @EnumSource(InvoiceSyntax.class)
  void readsTheControlWithoutWhiteSpaceTheSame(InvoiceSyntax syntax) {
    String xml = xml(syntax,
                     SHORT);
    assertTrue(xml.contains(">MQ==<"));
    for (var mode : ReadMode.values()) {
      assertArrayEquals(SHORT,
                        attachment(PANIOLO.read(xml,
                                                mode)
                                          .invoice()));
    }
  }

  /**
   * Only XML white space is removed: other characters, wrong or misplaced padding, and cut off data
   * are still rejected, with the location of the element.
   */
  @ParameterizedTest
  @EnumSource(InvoiceSyntax.class)
  void stillRejectsWhatIsNoBase64(InvoiceSyntax syntax) {
    String location = syntax == InvoiceSyntax.CII ?
                      "/rsm:CrossIndustryInvoice/" :
                      "/Invoice/";
    for (String text : List.of("MQ=",
                               "MQ",
                               "M",
                               "MQ=x",
                               "MQ===",
                               "MQ==MQ==",
                               "=MQ=",
                               "M!Q=",
                               "MQ ==",
                               "MQ-=")) {
      String xml = with(xml(syntax,
                            SHORT),
                        SHORT,
                        text);
      for (var mode : ReadMode.values()) {
        var error = assertThrows(IllegalArgumentException.class,
                                 () -> PANIOLO.read(xml,
                                                    mode),
                                 () -> syntax + " " + mode + " " + text);
        assertTrue(error.getMessage()
                        .startsWith("Invalid Base64 in " + location),
                   error::getMessage);
        assertTrue(error.getMessage()
                        .contains(syntax == InvoiceSyntax.CII ?
                                  "ram:AttachmentBinaryObject" :
                                  "cbc:EmbeddedDocumentBinaryObject"),
                   error::getMessage);
      }
    }
  }

  @ParameterizedTest
  @ValueSource(strings = { "", "   ", "\n\t " })
  void takesAnEmptyBinaryObjectForNoAttachment(String text) {
    for (var syntax : InvoiceSyntax.values()) {
      String xml = with(xml(syntax,
                            SHORT),
                        SHORT,
                        text);
      var read = PANIOLO.read(xml,
                              ReadMode.STRICT);
      assertEquals(null,
                   read.invoice()
                       .supportingDocuments()
                       .get(0)
                       .attachment());
    }
  }

  @Test
  void keepsTheDecodedBytesIdenticalToTheControl() {
    for (var syntax : InvoiceSyntax.values()) {
      var control = PANIOLO.read(xml(syntax,
                                     SHORT),
                                 ReadMode.STRICT);
      var spaced = PANIOLO.read(with(xml(syntax,
                                         SHORT),
                                     SHORT,
                                     "MQ= ="),
                                ReadMode.STRICT);
      assertEquals(control.invoice(),
                   spaced.invoice());
    }
  }
}

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
package io.github.nalukit.paniolo.facturx;

import io.github.nalukit.paniolo.Paniolo;
import io.github.nalukit.paniolo.config.FacturXProfile;
import io.github.nalukit.paniolo.model.common.value.InvoiceTypeCode;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.pdfwriter.compress.CompressParameters;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Test;
import org.verapdf.pdfa.flavours.PDFAFlavour;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Compression of the Factur-X PDF (Mustang issue #1247): Paniolo saves with object streams and
 * takes the streams of the given PDF as they are, so a compressed PDF stays compressed and an
 * uncompressed one is not compressed behind the caller's back.
 */
class PdfCompressionTest {
  private static final Paniolo PANIOLO = Paniolo.create();

  @Test
  void keepsACompressedPdfCompressed()
      throws IOException {
    byte[] pdf    = TestPdfs.pdfA2b();
    byte[] result = toFacturX(pdf);
    assertEquals(pageContent(pdf).filter(),
                 pageContent(result).filter());
    assertNotNull(pageContent(result).filter());
    assertArrayEquals(pageContent(pdf).raw(),
                      pageContent(result).raw());
    // Object streams with a cross-reference stream, as CompressParameters.DEFAULT_COMPRESSION writes them.
    String text = new String(result,
                             StandardCharsets.ISO_8859_1);
    assertTrue(text.contains("/ObjStm"),
               "object streams");
    assertTrue(text.contains("/XRef"),
               "cross-reference stream");
    // Only the XMP metadata stays unfiltered; the embedded XML is compressed.
    assertEquals(List.of(COSName.METADATA),
                 unfilteredStreamTypes(result));
    assertEquals(List.of(),
                 TestPdfs.pdfaFailures(result,
                                       PDFAFlavour.PDFA_3_B));
  }

  @Test
  void takesTheStreamsOfAnUncompressedPdfAsTheyAre()
      throws IOException {
    byte[] pdf    = uncompressed(TestPdfs.pdfA2b());
    byte[] result = toFacturX(pdf);
    assertEquals(null,
                 pageContent(result).filter());
    assertArrayEquals(pageContent(pdf).raw(),
                      pageContent(result).raw());
    assertEquals(List.of(),
                 TestPdfs.pdfaFailures(result,
                                       PDFAFlavour.PDFA_3_B));
  }

  private static byte[] toFacturX(byte[] pdf) {
    return PANIOLO.toFacturX(FacturXInvoices.invoice(FacturXProfile.EN16931,
                                                     InvoiceTypeCode.CODE_380)
                                            .build(),
                             pdf,
                             FacturXProfile.EN16931);
  }

  private record Content(Object filter, byte[] raw) {
  }

  private static Content pageContent(byte[] pdf)
      throws IOException {
    try (PDDocument document = Loader.loadPDF(pdf)) {
      var stream = (COSStream) document.getPage(0)
                                       .getCOSObject()
                                       .getDictionaryObject(COSName.CONTENTS);
      try (var in = stream.createRawInputStream()) {
        return new Content(stream.getFilters() == null ?
                           null :
                           stream.getFilters()
                                 .toString(),
                           in.readAllBytes());
      }
    }
  }

  private static List<COSName> unfilteredStreamTypes(byte[] pdf)
      throws IOException {
    var types = new ArrayList<COSName>();
    try (PDDocument document = Loader.loadPDF(pdf)) {
      var cos = document.getDocument();
      for (var key : new ArrayList<>(cos.getXrefTable()
                                        .keySet())) {
        if (cos.getObjectFromPool(key)
               .getObject() instanceof COSStream stream && stream.getFilters() == null && stream.getLength() > 0) {
          types.add(stream.getCOSName(COSName.TYPE));
        }
      }
    }
    return types;
  }

  /**
   * The PDF with every stream decoded and saved without object streams.
   */
  private static byte[] uncompressed(byte[] pdf)
      throws IOException {
    try (PDDocument document = Loader.loadPDF(pdf)) {
      var cos = document.getDocument();
      for (var key : new ArrayList<>(cos.getXrefTable()
                                        .keySet())) {
        if (cos.getObjectFromPool(key)
               .getObject() instanceof COSStream stream && COSName.FLATE_DECODE.equals(stream.getFilters())) {
          byte[] data;
          try (var in = stream.createInputStream()) {
            data = in.readAllBytes();
          }
          // createOutputStream() keeps the current filters.
          stream.removeItem(COSName.FILTER);
          stream.removeItem(COSName.DECODE_PARMS);
          try (OutputStream out = stream.createOutputStream()) {
            out.write(data);
          }
        }
      }
      var out = new ByteArrayOutputStream();
      document.save(out,
                    CompressParameters.NO_COMPRESSION);
      return out.toByteArray();
    }
  }
}

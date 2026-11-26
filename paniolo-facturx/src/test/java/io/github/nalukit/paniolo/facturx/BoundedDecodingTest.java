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

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSNull;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentNameDictionary;
import org.apache.pdfbox.pdmodel.PDEmbeddedFilesNameTreeNode;
import org.apache.pdfbox.pdmodel.common.filespecification.PDComplexFileSpecification;
import org.apache.pdfbox.pdmodel.common.filespecification.PDEmbeddedFile;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.zip.Deflater;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The attachments are decoded with the limit applied while decoding, not after the stream is
 * expanded: a small PDF with a highly compressed stream is refused without expanding it.
 */
class BoundedDecodingTest {
  private static final PdfBoxFacturXSupport SUPPORT = new PdfBoxFacturXSupport();

  /**
   * Zero bytes that are not held in memory.
   */
  private static InputStream zeros(long size) {
    return new InputStream() {
      private long left = size;

      @Override
      public int read() {
        return left-- > 0 ?
               0 :
               -1;
      }

      @Override
      public int read(byte[] b,
                      int off,
                      int len) {
        if (left <= 0) {
          return -1;
        }
        int n = (int) Math.min(len,
                               left);
        Arrays.fill(b,
                    off,
                    off + n,
                    (byte) 0);
        left -= n;
        return n;
      }
    };
  }

  private static byte[] zlib(byte[] input) {
    var deflater = new Deflater();
    deflater.setInput(input);
    deflater.finish();
    var out    = new ByteArrayOutputStream();
    var buffer = new byte[8192];
    while (!deflater.finished()) {
      out.write(buffer,
                0,
                deflater.deflate(buffer));
    }
    deflater.end();
    return out.toByteArray();
  }

  /**
   * A PDF with one attachment per entry; each entry builds the embedded file of its document.
   */
  private static byte[] pdf(List<String> names,
                            List<EmbeddedFileFactory> files)
      throws Exception {
    try (PDDocument document = Loader.loadPDF(TestPdfs.pdfA2b())) {
      var specs = new java.util.LinkedHashMap<String, PDComplexFileSpecification>();
      for (int i = 0; i < names.size(); i++) {
        var spec = new PDComplexFileSpecification();
        spec.setFile(names.get(i));
        spec.setFileUnicode(names.get(i));
        spec.setEmbeddedFile(files.get(i)
                                  .create(document));
        specs.put(names.get(i),
                  spec);
      }
      var tree = new PDEmbeddedFilesNameTreeNode();
      tree.setNames(specs);
      var dictionary = new PDDocumentNameDictionary(document.getDocumentCatalog());
      dictionary.setEmbeddedFiles(tree);
      document.getDocumentCatalog()
              .setNames(dictionary);
      var out = new ByteArrayOutputStream();
      document.save(out);
      return out.toByteArray();
    }
  }

  @FunctionalInterface
  private interface EmbeddedFileFactory {
    PDEmbeddedFile create(PDDocument document) throws Exception;
  }

  private static EmbeddedFileFactory flate(InputStream content) {
    return document -> {
      var embedded = new PDEmbeddedFile(document,
                                        content,
                                        COSName.FLATE_DECODE);
      embedded.setSubtype("application/octet-stream");
      return embedded;
    };
  }

  private static EmbeddedFileFactory flate(long zeros) {
    return flate(zeros(zeros));
  }

  private static byte[] single(long zeros) throws Exception {
    return pdf(List.of("big.bin"),
               List.of(flate(zeros)));
  }

  private static long allocatedBytes() {
    return ((com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean()).getThreadAllocatedBytes(Thread.currentThread()
                                                                                                                 .threadId());
  }

  @Test
  void aHighlyCompressedAttachmentIsRefusedWithoutExpandingIt() throws Exception {
    // 1 GiB of zeros is about 1 MiB in the PDF
    byte[] pdf = single(1L << 30);
    assertTrue(pdf.length < 4 << 20,
               "the fixture is small: " + pdf.length);

    long before = allocatedBytes();
    var e = assertThrows(IllegalArgumentException.class,
                         () -> SUPPORT.attachments(pdf,
                                                   5,
                                                   1_000));
    long allocated = allocatedBytes() - before;

    assertTrue(e.getMessage()
                .contains("together"),
               e.getMessage());
    // the decoder stops at the first byte beyond the limit; the allocation is the PDF and a few
    // buffers, never the expanded stream
    assertTrue(allocated < 64L << 20,
               "allocated " + allocated + " bytes");
  }

  @Test
  void anAttachmentExactlyAtTheLimitIsReadAndOneByteMoreIsRefused() throws Exception {
    byte[] pdf = single(1_000);

    var result = SUPPORT.attachments(pdf,
                                     5,
                                     1_000);
    assertEquals(1,
                 result.size());
    assertArrayEquals(new byte[1_000],
                      result.get(0)
                            .content());
    assertThrows(IllegalArgumentException.class,
                 () -> SUPPORT.attachments(pdf,
                                           5,
                                           999));
  }

  @Test
  void theSecondAttachmentIsDecodedWithTheRemainingBudget() throws Exception {
    byte[] pdf = pdf(List.of("a.bin",
                             "b.bin"),
                     List.of(flate(900),
                             flate(1L << 30)));

    long before = allocatedBytes();
    var e = assertThrows(IllegalArgumentException.class,
                         () -> SUPPORT.attachments(pdf,
                                                   5,
                                                   1_000));
    assertTrue(e.getMessage()
                .contains("together"),
               e.getMessage());
    assertTrue(allocatedBytes() - before < 64L << 20);
  }

  /**
   * An embedded file with the filters Flate and ASCIIHex: the first stage inflates to {@code wide}
   * bytes (a hex text followed by blanks, which the second stage never reads), the final result is the
   * four bytes "tiny".
   */
  private static EmbeddedFileFactory wideIntermediate(int wide) {
    return document -> {
      byte[] hex     = "74696E79>".getBytes(StandardCharsets.ISO_8859_1);
      byte[] content = Arrays.copyOf(hex,
                                     wide);
      Arrays.fill(content,
                  hex.length,
                  wide,
                  (byte) ' ');
      var embedded = new PDEmbeddedFile(document,
                                        new ByteArrayInputStream(content),
                                        COSName.FLATE_DECODE);
      var filters = new COSArray();
      filters.add(COSName.FLATE_DECODE);
      filters.add(COSName.getPDFName("ASCIIHexDecode"));
      embedded.getCOSObject()
              .setItem(COSName.FILTER,
                       filters);
      embedded.setSubtype("application/octet-stream");
      return embedded;
    };
  }

  @Test
  void anOversizedIntermediateStageIsStoppedAlthoughTheResultIsTiny() throws Exception {
    byte[] small = pdf(List.of("chain.bin"),
                       List.of(wideIntermediate(500)));
    assertArrayEquals("tiny".getBytes(StandardCharsets.ISO_8859_1),
                      SUPPORT.attachments(small,
                                          5,
                                          1_000)
                             .get(0)
                             .content());

    byte[] wide = pdf(List.of("chain.bin"),
                      List.of(wideIntermediate(5_000)));
    assertThrows(IllegalArgumentException.class,
                 () -> SUPPORT.attachments(wide,
                                           5,
                                           1_000));
  }

  @Test
  void aFilterListedTwiceIsAppliedOnceLikeInPdfBox() throws Exception {
    EmbeddedFileFactory twice = document -> {
      var embedded = new PDEmbeddedFile(document,
                                        new ByteArrayInputStream("once".getBytes(StandardCharsets.ISO_8859_1)),
                                        COSName.FLATE_DECODE);
      var filters = new COSArray();
      filters.add(COSName.FLATE_DECODE);
      filters.add(COSName.FLATE_DECODE);
      embedded.getCOSObject()
              .setItem(COSName.FILTER,
                       filters);
      embedded.setSubtype("text/plain");
      return embedded;
    };

    assertArrayEquals("once".getBytes(StandardCharsets.ISO_8859_1),
                      SUPPORT.attachments(pdf(List.of("twice.txt"),
                                              List.of(twice)),
                                          5,
                                          1_000)
                             .get(0)
                             .content());
  }

  @Test
  void otherSupportedFiltersAreDecodedAndImageFiltersAreRefused() throws Exception {
    EmbeddedFileFactory hex = document -> {
      var stream = document.getDocument()
                           .createCOSStream();
      try (OutputStream out = stream.createRawOutputStream()) {
        out.write("48656C6C6F>".getBytes(StandardCharsets.ISO_8859_1));
      }
      stream.setItem(COSName.FILTER,
                     COSName.getPDFName("ASCIIHexDecode"));
      var embedded = new PDEmbeddedFile(stream);
      embedded.setSubtype("text/plain");
      return embedded;
    };
    var result = SUPPORT.attachments(pdf(List.of("hello.txt"),
                                         List.of(hex)),
                                     5,
                                     1_000);
    assertArrayEquals("Hello".getBytes(StandardCharsets.ISO_8859_1),
                      result.get(0)
                            .content());

    EmbeddedFileFactory image = document -> {
      var embedded = new PDEmbeddedFile(document,
                                        new ByteArrayInputStream(new byte[10]),
                                        COSName.FLATE_DECODE);
      embedded.getCOSObject()
              .setItem(COSName.FILTER,
                       COSName.getPDFName("JBIG2Decode"));
      embedded.setSubtype("application/octet-stream");
      return embedded;
    };
    var e = assertThrows(IllegalArgumentException.class,
                         () -> SUPPORT.attachments(pdf(List.of("image.bin"),
                                                       List.of(image)),
                                                   5,
                                                   1_000));
    assertTrue(e.getMessage()
                .contains("JBIG2Decode"),
               e.getMessage());
  }

  @Test
  void anUncompressedAttachmentAndTheUsualOnesStayReadable() throws Exception {
    byte[] plain = "no filter at all".getBytes(StandardCharsets.ISO_8859_1);
    EmbeddedFileFactory unfiltered = document -> {
      var stream = document.getDocument()
                           .createCOSStream();
      try (OutputStream out = stream.createRawOutputStream()) {
        out.write(plain);
      }
      var embedded = new PDEmbeddedFile(stream);
      embedded.setSubtype("text/plain");
      return embedded;
    };
    byte[] pdf = pdf(List.of("plain.txt",
                             "zeros.bin"),
                     List.of(unfiltered,
                             flate(100)));

    var result = SUPPORT.attachments(pdf,
                                     5,
                                     1_000);
    assertEquals(List.of("plain.txt",
                         "zeros.bin"),
                 result.stream()
                       .map(a -> a.fileName())
                       .toList());
    assertArrayEquals(plain,
                      result.get(0)
                            .content());
    assertThrows(IllegalArgumentException.class,
                 () -> SUPPORT.attachments(pdf,
                                           5,
                                           10));
  }

  @Test
  void aRefusedPdfDoesNotHarmTheNextOneOnTheSameSupport() throws Exception {
    assertThrows(IllegalArgumentException.class,
                 () -> SUPPORT.attachments(single(1L << 28),
                                           5,
                                           1_000));

    assertEquals(1,
                 SUPPORT.attachments(single(10),
                                     5,
                                     1_000)
                        .size());
  }

  // ------------------------------------------------------------------------------------------
  // Predictors: PDFBox allocates two row buffers before it writes a byte, so the parameters are
  // checked first (decode parameters come from the document).
  // ------------------------------------------------------------------------------------------

  private static COSDictionary predictor(Integer predictor,
                                         Integer columns,
                                         Integer colors,
                                         Integer bits) {
    var parameters = new COSDictionary();
    if (predictor != null) {
      parameters.setInt(COSName.PREDICTOR,
                        predictor);
    }
    if (columns != null) {
      parameters.setInt(COSName.COLUMNS,
                        columns);
    }
    if (colors != null) {
      parameters.setInt(COSName.COLORS,
                        colors);
    }
    if (bits != null) {
      parameters.setInt(COSName.BITS_PER_COMPONENT,
                        bits);
    }
    return parameters;
  }

  /**
   * An embedded file whose content (already predicted) is encoded with the filter and carries the
   * decode parameters.
   */
  private static EmbeddedFileFactory withParameters(byte[] content,
                                                    COSName filter,
                                                    COSDictionary parameters) {
    return document -> {
      var embedded = new PDEmbeddedFile(document,
                                        new ByteArrayInputStream(content),
                                        filter);
      embedded.getCOSObject()
              .setItem(COSName.DECODE_PARMS,
                       parameters);
      embedded.setSubtype("application/octet-stream");
      return embedded;
    };
  }

  private static byte[] read(EmbeddedFileFactory file,
                             int maxCount,
                             long maxBytes) throws Exception {
    return SUPPORT.attachments(pdf(List.of("p.bin"),
                                   List.of(file)),
                               maxCount,
                               maxBytes)
                  .get(0)
                  .content();
  }

  private static IllegalArgumentException refused(EmbeddedFileFactory file,
                                                  long maxBytes) throws Exception {
    byte[] pdf = pdf(List.of("p.bin"),
                     List.of(file));
    long before = allocatedBytes();
    var e = assertThrows(IllegalArgumentException.class,
                         () -> SUPPORT.attachments(pdf,
                                                   5,
                                                   maxBytes));
    assertTrue(allocatedBytes() - before < 64L << 20,
               "allocated " + (allocatedBytes() - before) + " bytes");
    return e;
  }

  @Test
  void validTiffAndPngPredictorDataIsReadUnchangedWithFlateAndLzw() throws Exception {
    // TIFF (2): the first byte of a row is kept, the others are differences
    byte[] tiff = { 10, 5, 5, 5, 1, 1, 1, 1 };
    byte[] tiffDecoded = { 10, 15, 20, 25, 1, 2, 3, 4 };
    // PNG (12): a filter byte per row; 0 = none, 2 = up
    byte[] png = { 0, 1, 2, 3, 4, 2, 1, 1, 1, 1 };
    byte[] pngDecoded = { 1, 2, 3, 4, 2, 3, 4, 5 };
    for (COSName filter : List.of(COSName.FLATE_DECODE,
                                  COSName.LZW_DECODE)) {
      assertArrayEquals(tiffDecoded,
                        read(withParameters(tiff,
                                            filter,
                                            predictor(2,
                                                      4,
                                                      1,
                                                      8)),
                             5,
                             1_000),
                        filter.getName() + " TIFF");
      assertArrayEquals(pngDecoded,
                        read(withParameters(png,
                                            filter,
                                            predictor(12,
                                                      4,
                                                      1,
                                                      8)),
                             5,
                             1_000),
                        filter.getName() + " PNG");
    }
  }

  @Test
  void missingOptionalValuesUseTheDefaultsOfTheDecoder() throws Exception {
    // Columns 1, Colors 1, BitsPerComponent 8: a row is one byte after the filter byte
    assertArrayEquals(new byte[] { 7, 9 },
                      read(withParameters(new byte[] { 0, 7, 0, 9 },
                                          COSName.FLATE_DECODE,
                                          predictor(12,
                                                    null,
                                                    null,
                                                    null)),
                           5,
                           1_000));
    // no predictor, or 1: the other values are not used, whatever they are
    assertArrayEquals(new byte[] { 1, 2, 3 },
                      read(withParameters(new byte[] { 1, 2, 3 },
                                          COSName.FLATE_DECODE,
                                          predictor(1,
                                                    2_000_000_000,
                                                    32,
                                                    16)),
                           5,
                           1_000));
  }

  @Test
  void aRowAboveTheLimitIsRefusedBeforeAnythingIsAllocated() throws Exception {
    // a row of 1000 bytes fits the limit of 1000 bytes, a row of 1001 does not
    byte[] row = new byte[1_001];
    assertEquals(1_000,
                 read(withParameters(row,
                                     COSName.FLATE_DECODE,
                                     predictor(12,
                                               1_000,
                                               1,
                                               8)),
                      5,
                      1_000).length);
    var e = refused(withParameters(new byte[10],
                                   COSName.FLATE_DECODE,
                                   predictor(12,
                                             1_001,
                                             1,
                                             8)),
                    1_000);
    assertTrue(e.getMessage()
                .contains("together"),
               e.getMessage());
    // the rows PDFBox would allocate: 2 x 250 MB, 2 x 100 MB, with the TIFF predictor as well
    refused(withParameters(new byte[10],
                           COSName.FLATE_DECODE,
                           predictor(12,
                                     2_000_000_000,
                                     1,
                                     1)),
            1_000);
    refused(withParameters(new byte[10],
                           COSName.FLATE_DECODE,
                           predictor(2,
                                     100_000_000,
                                     1,
                                     8)),
            1_000);
    refused(withParameters(new byte[10],
                           COSName.LZW_DECODE,
                           predictor(12,
                                     100_000_000,
                                     1,
                                     8)),
            1_000);
  }

  @Test
  void theInternalLimitOfTheRowIsInclusive() throws Exception {
    int max = PdfBoxFacturXSupport.MAX_PREDICTOR_ROW;
    byte[] row = new byte[max + 1];
    assertEquals(max,
                 read(withParameters(row,
                                     COSName.FLATE_DECODE,
                                     predictor(12,
                                               max,
                                               1,
                                               8)),
                      5,
                      64L << 20).length);
    var e = refused(withParameters(new byte[10],
                                   COSName.FLATE_DECODE,
                                   predictor(12,
                                             max + 1,
                                             1,
                                             8)),
                    64L << 20);
    assertTrue(e.getMessage()
                .contains("predictor row"),
               e.getMessage());
  }

  @Test
  void aRowThatWouldOverflowTheIntegerArithmeticIsRefused() throws Exception {
    // 2e9 x 8 bits, and the largest values: an int product would be negative or small
    refused(withParameters(new byte[10],
                           COSName.FLATE_DECODE,
                           predictor(12,
                                     2_000_000_000,
                                     1,
                                     8)),
            1_000);
    refused(withParameters(new byte[10],
                           COSName.FLATE_DECODE,
                           predictor(12,
                                     Integer.MAX_VALUE,
                                     32,
                                     16)),
            1_000);
    // 2^29 + 1 columns of 8 bits: the int product wraps to 8 bits, a row of one byte
    refused(withParameters(new byte[10],
                           COSName.FLATE_DECODE,
                           predictor(12,
                                     (1 << 29) + 1,
                                     1,
                                     8)),
            1_000);
  }

  @Test
  void invalidAndUnsupportedParametersAreRefusedWithAMessage() throws Exception {
    List<COSDictionary> invalid = List.of(predictor(12,
                                                    0,
                                                    1,
                                                    8),
                                          predictor(12,
                                                    -5,
                                                    1,
                                                    8),
                                          predictor(12,
                                                    4,
                                                    0,
                                                    8),
                                          predictor(12,
                                                    4,
                                                    1,
                                                    3),
                                          predictor(12,
                                                    4,
                                                    1,
                                                    0),
                                          predictor(5,
                                                    4,
                                                    1,
                                                    8),
                                          predictor(16,
                                                    4,
                                                    1,
                                                    8));
    for (COSDictionary parameters : invalid) {
      var e = assertThrows(IllegalArgumentException.class,
                           () -> read(withParameters(new byte[10],
                                                     COSName.FLATE_DECODE,
                                                     parameters),
                                      5,
                                      1_000),
                           parameters.toString());
      assertTrue(e.getMessage()
                  .contains("predictor"),
                 e.getMessage());
    }
  }

  /**
   * A stream with the filters ASCIIHex and Flate; the Flate data holds PNG rows of one byte after the
   * filter byte.
   */
  private static EmbeddedFileFactory hexThenFlate(COSArray filters,
                                                  COSBase parameters) {
    return document -> {
      byte[] rows = { 0, 7, 0, 9 };
      var    hex  = new StringBuilder();
      for (byte b : zlib(rows)) {
        hex.append(String.format("%02X",
                                 b));
      }
      hex.append('>');
      var stream = document.getDocument()
                           .createCOSStream();
      try (OutputStream out = stream.createRawOutputStream()) {
        out.write(hex.toString()
                     .getBytes(StandardCharsets.ISO_8859_1));
      }
      stream.setItem(COSName.FILTER,
                     filters);
      stream.setItem(COSName.DECODE_PARMS,
                     parameters);
      var embedded = new PDEmbeddedFile(stream);
      embedded.setSubtype("application/octet-stream");
      return embedded;
    };
  }

  private static COSArray names(String... names) {
    var array = new COSArray();
    for (String name : names) {
      array.add(COSName.getPDFName(name));
    }
    return array;
  }

  @Test
  void parametersAreCheckedForTheStageTheyBelongTo() throws Exception {
    var ok  = predictor(12,
                        null,
                        null,
                        null);
    var bad = predictor(12,
                        2_000_000_000,
                        1,
                        1);
    var array = new COSArray();
    array.add(COSNull.NULL);
    array.add(ok);
    // the predictor of the second stage (Flate) is valid
    assertArrayEquals(new byte[] { 7, 9 },
                      read(hexThenFlate(names("ASCIIHexDecode",
                                              "FlateDecode"),
                                        array),
                           5,
                           1_000));
    // and not valid: it is found at the later stage
    var later = new COSArray();
    later.add(COSNull.NULL);
    later.add(bad);
    refused(hexThenFlate(names("ASCIIHexDecode",
                               "FlateDecode"),
                         later),
            1_000);
    // a filter listed twice is one stage, with the parameters of the first position
    var twiceOkThenBad = new COSArray();
    twiceOkThenBad.add(ok);
    twiceOkThenBad.add(bad);
    var twiceBadThenOk = new COSArray();
    twiceBadThenOk.add(bad);
    twiceBadThenOk.add(ok);
    var hexNames = names("ASCIIHexDecode",
                         "ASCIIHexDecode");
    assertTrue(hexNames.size() == 2);
    var flateTwice = names("FlateDecode",
                           "FlateDecode");
    EmbeddedFileFactory single = document -> {
      var embedded = new PDEmbeddedFile(document,
                                        new ByteArrayInputStream(new byte[] { 0, 7, 0, 9 }),
                                        COSName.FLATE_DECODE);
      embedded.getCOSObject()
              .setItem(COSName.FILTER,
                       flateTwice);
      embedded.getCOSObject()
              .setItem(COSName.DECODE_PARMS,
                       twiceOkThenBad);
      embedded.setSubtype("application/octet-stream");
      return embedded;
    };
    assertArrayEquals(new byte[] { 7, 9 },
                      read(single,
                           5,
                           1_000));
    EmbeddedFileFactory badFirst = document -> {
      var embedded = new PDEmbeddedFile(document,
                                        new ByteArrayInputStream(new byte[] { 0, 7, 0, 9 }),
                                        COSName.FLATE_DECODE);
      embedded.getCOSObject()
              .setItem(COSName.FILTER,
                       flateTwice);
      embedded.getCOSObject()
              .setItem(COSName.DECODE_PARMS,
                       twiceBadThenOk);
      embedded.setSubtype("application/octet-stream");
      return embedded;
    };
    refused(badFirst,
            1_000);
  }

  @Test
  void thePredictorRowIsCheckedAgainstTheRemainingBudget() throws Exception {
    // the first attachment leaves 100 bytes; a row of 200 bytes of the second does not fit
    byte[] pdf = pdf(List.of("a.bin",
                             "b.bin"),
                     List.of(flate(900),
                             withParameters(new byte[10],
                                            COSName.FLATE_DECODE,
                                            predictor(12,
                                                      200,
                                                      1,
                                                      8))));
    var e = assertThrows(IllegalArgumentException.class,
                         () -> SUPPORT.attachments(pdf,
                                                   5,
                                                   1_000));
    assertTrue(e.getMessage()
                .contains("together"),
               e.getMessage());
  }

  @Test
  void theInvoiceXmlAndTheXmpMetadataAreProtectedToo() throws Exception {
    var bad = predictor(12,
                        2_000_000_000,
                        1,
                        1);
    // the invoice XML of the PDF: refused with a message, no large allocation
    byte[] xmlPdf = pdf(List.of("factur-x.xml"),
                        List.of(withParameters(new byte[10],
                                               COSName.FLATE_DECODE,
                                               bad)));
    long before = allocatedBytes();
    assertThrows(IllegalArgumentException.class,
                 () -> SUPPORT.extract(xmlPdf));
    assertTrue(allocatedBytes() - before < 64L << 20);

    // the XMP metadata: counts as missing, and nothing large is allocated
    byte[] xmpPdf;
    try (PDDocument document = Loader.loadPDF(TestPdfs.pdfA2b())) {
      var stream = document.getDocument()
                           .createCOSStream();
      try (OutputStream out = stream.createRawOutputStream()) {
        out.write(zlib("<x/>".getBytes(StandardCharsets.ISO_8859_1)));
      }
      stream.setItem(COSName.FILTER,
                     COSName.FLATE_DECODE);
      stream.setItem(COSName.DECODE_PARMS,
                     bad);
      stream.setItem(COSName.TYPE,
                     COSName.METADATA);
      stream.setItem(COSName.SUBTYPE,
                     COSName.getPDFName("XML"));
      document.getDocumentCatalog()
              .getCOSObject()
              .setItem(COSName.METADATA,
                       stream);
      var out = new ByteArrayOutputStream();
      document.save(out);
      xmpPdf = out.toByteArray();
    }
    before = allocatedBytes();
    SUPPORT.producer(xmpPdf);
    assertTrue(allocatedBytes() - before < 64L << 20);
  }

  @Test
  void afterARefusedPredictorTheNextPdfIsRead() throws Exception {
    refused(withParameters(new byte[10],
                           COSName.FLATE_DECODE,
                           predictor(12,
                                     2_000_000_000,
                                     1,
                                     1)),
            1_000);

    assertEquals(1,
                 SUPPORT.attachments(single(10),
                                     5,
                                     1_000)
                        .size());
  }
}

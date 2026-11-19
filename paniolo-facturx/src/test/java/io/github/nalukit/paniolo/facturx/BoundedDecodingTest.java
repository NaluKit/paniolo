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
}

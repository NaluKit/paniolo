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
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentNameDictionary;
import org.apache.pdfbox.pdmodel.PDEmbeddedFilesNameTreeNode;
import org.apache.pdfbox.pdmodel.common.filespecification.PDComplexFileSpecification;
import org.apache.pdfbox.pdmodel.common.filespecification.PDEmbeddedFile;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The attachments of one PDF are limited in number and in total size, not only per file.
 */
class AttachmentBudgetTest {
  private static final PdfBoxFacturXSupport SUPPORT = new PdfBoxFacturXSupport();

  private static byte[] content(int size,
                                int fill) {
    byte[] content = new byte[size];
    Arrays.fill(content,
                (byte) fill);
    return content;
  }

  /**
   * A PDF with one file specification per name; with {@code shared}, all of them point to the same
   * embedded stream.
   */
  private static byte[] pdf(List<String> names,
                            byte[] sharedContent,
                            List<byte[]> contents)
      throws Exception {
    try (PDDocument document = Loader.loadPDF(TestPdfs.pdfA2b())) {
      PDEmbeddedFile shared = sharedContent == null ?
                              null :
                              embedded(document,
                                       sharedContent);
      var files = new LinkedHashMap<String, PDComplexFileSpecification>();
      for (int i = 0; i < names.size(); i++) {
        var file = new PDComplexFileSpecification();
        file.setFile(names.get(i));
        file.setFileUnicode(names.get(i));
        file.setEmbeddedFile(shared != null ?
                             shared :
                             embedded(document,
                                      contents.get(i)));
        files.put(names.get(i),
                  file);
      }
      var tree = new PDEmbeddedFilesNameTreeNode();
      tree.setNames(files);
      var dictionary = new PDDocumentNameDictionary(document.getDocumentCatalog());
      dictionary.setEmbeddedFiles(tree);
      document.getDocumentCatalog()
              .setNames(dictionary);
      var out = new ByteArrayOutputStream();
      document.save(out);
      return out.toByteArray();
    }
  }

  private static PDEmbeddedFile embedded(PDDocument document,
                                         byte[] content)
      throws Exception {
    var embedded = new PDEmbeddedFile(document,
                                      new ByteArrayInputStream(content),
                                      COSName.FLATE_DECODE);
    embedded.setSubtype("application/octet-stream");
    return embedded;
  }

  private static byte[] separate(int count,
                                 int size)
      throws Exception {
    var names    = new java.util.ArrayList<String>();
    var contents = new java.util.ArrayList<byte[]>();
    for (int i = 0; i < count; i++) {
      names.add("file" + i + ".bin");
      contents.add(content(size,
                           i + 1));
    }
    return pdf(names,
               null,
               contents);
  }

  @Test
  void attachmentsWithinTheBudgetAreReadUnchanged()
      throws Exception {
    var result = SUPPORT.attachments(separate(3,
                                              10),
                                     3,
                                     30);

    assertEquals(3,
                 result.size());
    for (int i = 0; i < 3; i++) {
      assertEquals("file" + i + ".bin",
                   result.get(i)
                         .fileName());
      assertArrayEquals(content(10,
                                i + 1),
                        result.get(i)
                              .content());
    }
  }

  @Test
  void aTotalOneByteOverTheBudgetIsRejected()
      throws Exception {
    var e = assertThrows(IllegalArgumentException.class,
                         () -> SUPPORT.attachments(separate(3,
                                                            10),
                                                   3,
                                                   29));
    assertTrue(e.getMessage()
                .contains("together"),
               e.getMessage());
  }

  @Test
  void theNumberOfAttachmentsIsLimitedInclusively()
      throws Exception {
    byte[] pdf = separate(4,
                          1);

    assertEquals(4,
                 SUPPORT.attachments(pdf,
                                     4,
                                     100)
                        .size());
    var e = assertThrows(IllegalArgumentException.class,
                         () -> SUPPORT.attachments(pdf,
                                                   3,
                                                   100));
    assertTrue(e.getMessage()
                .contains("more than 3"),
               e.getMessage());
  }

  @Test
  void attachmentsSharingOneStreamEachCountAgainstTheBudget()
      throws Exception {
    byte[] pdf = pdf(List.of("a.bin",
                             "b.bin",
                             "c.bin"),
                     content(10,
                             7),
                     null);

    assertEquals(3,
                 SUPPORT.attachments(pdf,
                                     3,
                                     30)
                        .size());
    assertThrows(IllegalArgumentException.class,
                 () -> SUPPORT.attachments(pdf,
                                           3,
                                           29));
  }

  @Test
  void aSingleAttachmentIsLimitedByTheRemainingBudget()
      throws Exception {
    assertThrows(IllegalArgumentException.class,
                 () -> SUPPORT.attachments(separate(1,
                                                    11),
                                           5,
                                           10));
  }

  @Test
  void afterARejectionTheSameSupportReadsOtherPdfs()
      throws Exception {
    assertThrows(IllegalArgumentException.class,
                 () -> SUPPORT.attachments(separate(3,
                                                    10),
                                           3,
                                           29));

    assertEquals(2,
                 SUPPORT.attachments(separate(2,
                                              10))
                        .size());
  }

  @Test
  void theDefaultLimitsAreDocumented() {
    assertEquals(1_000,
                 PdfBoxFacturXSupport.MAX_ATTACHMENTS);
    assertEquals(256L * 1024 * 1024,
                 PdfBoxFacturXSupport.MAX_ATTACHMENT_BYTES);
  }
}

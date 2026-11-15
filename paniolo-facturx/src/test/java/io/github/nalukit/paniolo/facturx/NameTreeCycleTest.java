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
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A name tree of embedded files that refers to itself, or is absurdly deep, is a damaged container:
 * refused with a message, reported by the validation, empty for the probe, never a stack overflow.
 */
class NameTreeCycleTest {
  private static final Paniolo PANIOLO = Paniolo.create();

  /**
   * A minimal PDF whose /EmbeddedFiles tree is the node 3 and whose nodes have the given /Kids;
   * PDFBox finds the objects without a cross-reference table.
   */
  private static byte[] pdf(String... kidsOfNodes) {
    var pdf = new StringBuilder("%PDF-1.7\n");
    pdf.append("1 0 obj\n<</Type/Catalog/Pages 2 0 R/Names<</EmbeddedFiles 3 0 R>>>>\nendobj\n");
    pdf.append("2 0 obj\n<</Type/Pages/Kids[]/Count 0>>\nendobj\n");
    for (int i = 0; i < kidsOfNodes.length; i++) {
      pdf.append(3 + i)
         .append(" 0 obj\n<<")
         .append(kidsOfNodes[i].isEmpty() ?
                 "/Names[]" :
                 "/Kids[" + kidsOfNodes[i] + "]")
         .append(">>\nendobj\n");
    }
    pdf.append("trailer\n<</Root 1 0 R/Size ")
       .append(3 + kidsOfNodes.length)
       .append(">>\n%%EOF\n");
    return pdf.toString()
              .getBytes(StandardCharsets.ISO_8859_1);
  }

  private static byte[] chain(int nodes) {
    var kids = new String[nodes];
    for (int i = 0; i < nodes - 1; i++) {
      kids[i] = (4 + i) + " 0 R";
    }
    kids[nodes - 1] = "";
    return pdf(kids);
  }

  private static void assertRefused(byte[] pdf,
                                    String reason) {
    var e = assertThrows(IllegalArgumentException.class,
                         () -> PANIOLO.attachments(pdf));
    assertTrue(e.getMessage()
                .contains(reason),
               e.getMessage());
    assertEquals(Optional.empty(),
                 PANIOLO.probe(pdf));
    var result = PANIOLO.validate(pdf);
    assertFalse(result.isValid());
    assertTrue(result.messages()
                     .stream()
                     .anyMatch(message -> message.message()
                                                 .contains(reason)),
               result.messages()
                     .toString());
  }

  @Test
  void aTreeThatIsItsOwnKidIsRefused() {
    assertRefused(pdf("3 0 R"),
                  "refers to itself");
  }

  @Test
  void aCycleOverSeveralNodesIsRefused() {
    assertRefused(pdf("4 0 R",
                      "5 0 R",
                      "3 0 R"),
                  "refers to itself");
  }

  @Test
  void aTreeFarDeeperThanAnyRealOneIsRefused() {
    assertRefused(chain(200),
                  "deeper than");
  }

  @Test
  void anOrdinaryDeepTreeAndANodeReachedTwiceAreRead() {
    assertEquals(0,
                 PANIOLO.attachments(chain(20))
                        .size());
    // The node 4 is a kid twice: no cycle, read once.
    assertEquals(0,
                 PANIOLO.attachments(pdf("4 0 R 4 0 R",
                                         ""))
                        .size());
  }
}

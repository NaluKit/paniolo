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
package io.github.nalukit.paniolo.xml.util;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * Finds content of a document that another document lacks, e.g. what a reader did not carry into
 * its model: the original is compared with the document written again from the model. Elements
 * and attributes are compared by their local-name path and normalized value, counting repeated
 * occurrences; numbers compare by value, whitespace is collapsed. Attributes whose value follows
 * from other content ({@code currencyID}, {@code format}) are ignored.
 */
public final class ContentComparison {
  private static final Pattern     NUMBER  = Pattern.compile("[+-]?[0-9]+(\\.[0-9]+)?");
  private static final Set<String> IGNORED = Set.of("currencyID",
                                                    "format");

  private ContentComparison() {
  }

  /**
   * Returns the paths and values of the original that the copy does not contain, at most
   * {@code limit} entries, e.g. {@code SpecifiedTradeProduct/BrandName = Marke}.
   *
   * @param original the original document
   * @param copy     the document written again from the model
   * @param limit    maximum number of entries returned
   * @return the missing content, in path order
   */
  public static List<String> missing(Document original,
                                     Document copy,
                                     int limit) {
    var expected = content(original);
    var actual   = content(copy);
    var result   = new ArrayList<String>();
    for (var entry : expected.entrySet()) {
      int lost = entry.getValue() - actual.getOrDefault(entry.getKey(),
                                                        0);
      if (lost > 0 && result.size() < limit) {
        result.add(entry.getKey());
      }
    }
    return result;
  }

  private static Map<String, Integer> content(Document document) {
    var result = new TreeMap<String, Integer>();
    collect(document.getDocumentElement(),
            "",
            result);
    return result;
  }

  private static void collect(Element element,
                              String parent,
                              Map<String, Integer> result) {
    String path = parent.isEmpty() ?
                  element.getLocalName() :
                  parent + "/" + element.getLocalName();
    var attributes = element.getAttributes();
    for (int i = 0; i < attributes.getLength(); i++) {
      var attribute = attributes.item(i);
      String name = attribute.getLocalName() == null ?
                    attribute.getNodeName() :
                    attribute.getLocalName();
      if (attribute.getNodeName()
                   .startsWith("xmlns") || IGNORED.contains(name)) {
        continue;
      }
      result.merge(path +
                   "/@" +
                   name +
                   " = " +
                   normalise(name,
                             attribute.getNodeValue()),
                   1,
                   Integer::sum);
    }
    boolean leaf = true;
    for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (child instanceof Element nested) {
        leaf = false;
        collect(nested,
                path,
                result);
      }
    }
    if (leaf) {
      result.merge(path +
                   " = " +
                   normalise(element.getLocalName(),
                             element.getTextContent()),
                   1,
                   Integer::sum);
    }
  }

  private static String normalise(String name,
                                  String value) {
    if (value == null) {
      return "";
    }
    if (name.endsWith("BinaryObject")) {
      return value.replaceAll("\\s",
                              "");
    }
    String text = value.strip()
                       .replaceAll("\\s+",
                                   " ");
    return NUMBER.matcher(text)
                 .matches() ?
           new BigDecimal(text).stripTrailingZeros()
                               .toPlainString() :
           text;
  }
}

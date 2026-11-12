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

import org.junit.jupiter.api.Test;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link XmlElement#find(String)} and {@link XmlElement#findWithText(String)} stop at the first
 * match but return the same element as the first one of {@link XmlElement#all(String)}.
 */
class XmlElementTest {
  private static final String A = "urn:test:a";
  private static final String B = "urn:test:b";

  private static final String XML = """
                                    <a:Root xmlns:a="urn:test:a" xmlns:b="urn:test:b">
                                      <a:Party id="p1">
                                        <a:Name id="n1"/>
                                      </a:Party>
                                      <a:Party id="p2">
                                        <b:ID id="other-namespace">B</b:ID>
                                        <a:ID id="empty"/>
                                        <a:ID id="blank">  \n </a:ID>
                                        <a:Address id="addr1">
                                          <a:City id="c1">Berlin</a:City>
                                          <a:City id="c2">Hamburg</a:City>
                                        </a:Address>
                                      </a:Party>
                                      <a:Party id="p3">
                                        <a:ID id="id3">3</a:ID>
                                        <a:Address id="addr2">
                                          <a:City id="c3"> </a:City>
                                          <a:City id="c4">Köln</a:City>
                                        </a:Address>
                                      </a:Party>
                                      <Plain id="plain"/>
                                    </a:Root>
                                    """;

  private static XmlElement root() throws Exception {
    var factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(true);
    var document = factory.newDocumentBuilder()
                          .parse(new InputSource(new StringReader(XML)));
    return XmlElement.of(document.getDocumentElement(),
                         Map.of("a",
                                A,
                                "b",
                                B));
  }

  private static String id(XmlElement element) {
    return element == null ?
           null :
           element.attribute("id");
  }

  private static List<String> ids(List<XmlElement> elements) {
    return elements.stream()
                   .map(XmlElementTest::id)
                   .toList();
  }

  @Test
  void findReturnsTheFirstElementOfAll() throws Exception {
    var root = root();
    for (String path : List.of("a:Party",
                               "a:Party/a:Name",
                               "a:Party/a:ID",
                               "b:ID",
                               "a:Party/b:ID",
                               "a:Party/a:Address",
                               "a:Party/a:Address/a:City",
                               "Plain",
                               "a:Missing",
                               "a:Party/a:Missing",
                               "a:Party/a:Address/a:Missing")) {
      var all = root.all(path);
      assertEquals(all.isEmpty() ?
                   null :
                   id(all.getFirst()),
                   id(root.find(path)),
                   path);
    }
  }

  @Test
  void findTriesLaterParentsWhenAnEarlierOneDoesNotCompleteThePath() throws Exception {
    var root = root();
    // The first party has no ID and no address; the second one has both.
    assertEquals("empty",
                 id(root.find("a:Party/a:ID")));
    assertEquals("c1",
                 id(root.find("a:Party/a:Address/a:City")));
  }

  @Test
  void findWithTextSkipsEmptyAndBlankElements() throws Exception {
    var root = root();
    // The second party has an empty and a blank a:ID, the third one a:ID with text.
    assertEquals("id3",
                 id(root.findWithText("a:Party/a:ID")));
    assertEquals("c1",
                 id(root.findWithText("a:Party/a:Address/a:City")));
    var third = root.all("a:Party")
                    .get(2);
    assertEquals("c4",
                 id(third.findWithText("a:Address/a:City")));
    assertNull(root.findWithText("a:Party/a:Name"));
    assertNull(root.findWithText("a:Party/a:Missing"));
  }

  @Test
  void findMatchesTheNamespaceAndNotOnlyTheLocalName() throws Exception {
    var root = root();
    assertEquals("other-namespace",
                 id(root.find("a:Party/b:ID")));
    assertEquals("empty",
                 id(root.find("a:Party/a:ID")));
    assertEquals("plain",
                 id(root.find("Plain")));
    assertNull(root.find("a:Plain"));
  }

  @Test
  void allKeepsTheDocumentOrder() throws Exception {
    var root = root();
    assertEquals(List.of("p1",
                         "p2",
                         "p3"),
                 ids(root.all("a:Party")));
    assertEquals(List.of("c1",
                         "c2",
                         "c3",
                         "c4"),
                 ids(root.all("a:Party/a:Address/a:City")));
    assertEquals(List.of("empty",
                         "blank",
                         "id3"),
                 ids(root.all("a:Party/a:ID")));
    assertEquals(List.of(),
                 root.all("a:Party/a:Missing"));
  }

  @Test
  void textReadsTheFirstFoundElementEvenIfItIsEmpty() throws Exception {
    var root = root();
    assertEquals("Berlin",
                 root.text("a:Party/a:Address/a:City"));
    assertNull(root.text("a:Party/a:ID"));
  }
}

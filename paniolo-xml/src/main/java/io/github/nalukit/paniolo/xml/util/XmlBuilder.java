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

import javax.xml.XMLConstants;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;

/**
 * Internal XML writing utility shared by the syntax adapters.
 */
public final class XmlBuilder {
  private final Document            document = XmlDocuments.create();
  private final Map<String, String> namespaces;
  private final Deque<Element>      stack    = new ArrayDeque<>();

  /**
   * Creates a document with the root element and its namespace declarations.
   *
   * @param root       prefixed name of the root element
   * @param namespaces namespace URIs by prefix; the empty prefix is the default namespace
   */
  public XmlBuilder(String root,
                    Map<String, String> namespaces) {
    this.namespaces = Map.copyOf(namespaces);
    start(root);
    namespaces.forEach((prefix, uri) -> stack.peek()
                                             .setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI,
                                                             prefix.isEmpty() ?
                                                             "xmlns" :
                                                             "xmlns:" + prefix,
                                                             uri));
  }

  /**
   * Opens a child element of the current element; it becomes the current element.
   *
   * @param name prefixed element name
   * @return this builder
   */
  public XmlBuilder start(String name) {
    String prefix = name.contains(":") ?
                    name.substring(0,
                                   name.indexOf(':')) :
                    "";
    Element element = document.createElementNS(namespaces.get(prefix),
                                               name);
    if (stack.isEmpty()) {
      document.appendChild(element);
    } else {
      stack.peek()
           .appendChild(element);
    }
    stack.push(element);
    return this;
  }

  /**
   * Closes the current element; its parent becomes the current element.
   *
   * @return this builder
   * @throws IllegalStateException if the current element is the root
   */
  public XmlBuilder end() {
    if (stack.size() < 2) {
      throw new IllegalStateException("Cannot close the XML root");
    }
    stack.pop();
    return this;
  }

  /**
   * Sets an attribute of the current element; null values are skipped.
   *
   * @param name  attribute name
   * @param value attribute value, or null
   * @return this builder
   */
  public XmlBuilder attribute(String name,
                              Object value) {
    if (value != null) {
      stack.peek()
           .setAttribute(name,
                         value.toString());
    }
    return this;
  }

  /**
   * Adds a child element with text content; null and blank values are skipped. Decimals are
   * written without exponent.
   *
   * @param name       prefixed element name
   * @param value      text content, or null
   * @param attributes attribute names and values in turns; attributes with null values are skipped
   * @return this builder
   * @throws IllegalArgumentException if the text contains a character forbidden in XML 1.0
   */
  public XmlBuilder element(String name,
                            Object value,
                            String... attributes) {
    if (value == null || value instanceof String s && s.isBlank()) {
      return this;
    }
    String text = value instanceof BigDecimal d ?
                  d.toPlainString() :
                  value.toString();
    if (text.codePoints()
            .anyMatch(c -> !(c == 9 || c == 10 || c == 13 || c >= 32 && c <= 0xD7FF || c >= 0xE000 && c <= 0xFFFD || c >= 0x10000 && c <= 0x10FFFF))) {
      throw new IllegalArgumentException("Text contains a character forbidden in XML 1.0");
    }
    start(name);
    stack.peek()
         .setTextContent(text);
    for (int i = 0; i < attributes.length; i += 2) {
      if (attributes[i + 1] != null) {
        stack.peek()
             .setAttribute(attributes[i],
                           attributes[i + 1]);
      }
    }
    return end();
  }

  /**
   * Serializes the document as indented UTF-8 XML.
   *
   * @return the XML document
   * @throws IllegalStateException if elements other than the root are still open
   */
  public String toXml() {
    if (stack.size() != 1) {
      throw new IllegalStateException("Unclosed XML elements");
    }
    try {
      var factory = TransformerFactory.newDefaultInstance();
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING,
                         true);
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD,
                           "");
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET,
                           "");
      var transformer = factory.newTransformer();
      transformer.setOutputProperty(OutputKeys.ENCODING,
                                    "UTF-8");
      transformer.setOutputProperty(OutputKeys.INDENT,
                                    "yes");
      var output = new StringWriter();
      transformer.transform(new DOMSource(document),
                            new StreamResult(output));
      return output.toString();
    } catch (TransformerException e) {
      throw new IllegalStateException("Cannot serialize invoice XML",
                                      e);
    }
  }
}

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

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Internal namespace-aware DOM navigation shared by the syntax readers; paths use prefixed names.
 */
public final class XmlElement {
  private final Element             element;
  private final Map<String, String> namespaces;

  private XmlElement(Element element,
                     Map<String, String> namespaces) {
    this.element    = element;
    this.namespaces = namespaces;
  }

  /**
   * Wraps a DOM element.
   *
   * @param element    the DOM element
   * @param namespaces namespace URIs by prefix used in paths; the empty prefix is for unprefixed
   *                   names
   * @return the element
   */
  public static XmlElement of(Element element,
                              Map<String, String> namespaces) {
    return new XmlElement(element,
                          Map.copyOf(namespaces));
  }

  /**
   * Maps a code with a lookup returning null for unknown codes; null input stays null, and an
   * unknown code is rejected.
   *
   * @param <T>    the code type
   * @param code   the code, or null
   * @param lookup returns the value of a code, or null if the code is unknown
   * @param kind   name of the code list for the error message
   * @return the value, or null if the code is null
   * @throws IllegalArgumentException if the code is unknown
   */
  public static <T> T code(String code,
                           Function<String, T> lookup,
                           String kind) {
    if (code == null) {
      return null;
    }
    T value = lookup.apply(code);
    if (value == null) {
      throw new IllegalArgumentException("Unknown " + kind + ": " + code);
    }
    return value;
  }

  private static LocalDate parseDate(String text,
                                     DateTimeFormatter format,
                                     String location) {
    try {
      return text == null ?
             null :
             LocalDate.parse(text,
                             format);
    } catch (DateTimeParseException e) {
      throw new IllegalArgumentException("Invalid date \"" + text + "\" at " + location,
                                         e);
    }
  }

  /**
   * Local name of the element.
   *
   * @return the local name
   */
  public String localName() {
    return element.getLocalName();
  }

  /**
   * Namespace URI of the element.
   *
   * @return the namespace URI, or null
   */
  public String namespace() {
    return element.getNamespaceURI();
  }

  /**
   * Direct child elements matching one prefixed name.
   *
   * @param name prefixed element name
   * @return the matching children in document order
   */
  public List<XmlElement> children(String name) {
    String prefix = name.contains(":") ?
                    name.substring(0,
                                   name.indexOf(':')) :
                    "";
    String namespace = namespaces.get(prefix);
    String local     = name.substring(name.indexOf(':') + 1);
    var    result    = new ArrayList<XmlElement>();
    for (Node node = element.getFirstChild(); node != null; node = node.getNextSibling()) {
      if (node instanceof Element child && local.equals(child.getLocalName()) && java.util.Objects.equals(namespace,
                                                                                                          child.getNamespaceURI())) {
        result.add(new XmlElement(child,
                                  namespaces));
      }
    }
    return result;
  }

  /**
   * All elements reached by a slash separated path of prefixed names.
   *
   * @param path slash separated prefixed names
   * @return the elements in document order
   */
  public List<XmlElement> all(String path) {
    List<XmlElement> current = List.of(this);
    for (String step : path.split("/")) {
      var next = new ArrayList<XmlElement>();
      current.forEach(e -> next.addAll(e.children(step)));
      current = next;
    }
    return current;
  }

  /**
   * First element reached by the path, or null.
   *
   * @param path slash separated prefixed names
   * @return the first element, or null
   */
  public XmlElement find(String path) {
    var found = all(path);
    return found.isEmpty() ?
           null :
           found.get(0);
  }

  /**
   * Trimmed text content; null for blank content.
   *
   * @return the text, or null
   */
  public String text() {
    String text = element.getTextContent()
                         .strip();
    return text.isEmpty() ?
           null :
           text;
  }

  /**
   * Trimmed text of the first element reached by the path, or null.
   *
   * @param path slash separated prefixed names
   * @return the text, or null
   */
  public String text(String path) {
    var found = find(path);
    return found == null ?
           null :
           found.text();
  }

  /**
   * Decimal of the first element reached by the path, or null.
   *
   * @param path slash separated prefixed names
   * @return the decimal, or null
   * @throws IllegalArgumentException if the text is not a decimal
   */
  public BigDecimal decimal(String path) {
    var found = find(path);
    return found == null ?
           null :
           found.decimal();
  }

  /**
   * Decimal of the text of this element, or null for blank text. EN 16931 decimals use a point as
   * decimal separator and no thousands separators or units; anything else is rejected, never
   * guessed.
   *
   * @return the decimal, or null
   * @throws IllegalArgumentException if the text is not a decimal; the message names the text and
   *                                  the {@link #location() location} of the element
   */
  public BigDecimal decimal() {
    String text = text();
    if (text == null) {
      return null;
    }
    try {
      return new BigDecimal(text);
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("Invalid decimal \"" + text + "\" at " + location() + ": " + (text.contains(",") ?
                                                                                                       "a comma is no decimal separator, a point is" :
                                                                                                       "only digits, an optional sign, and a point as decimal separator are allowed"),
                                         e);
    }
  }

  /**
   * The location of this element in its document, e.g.
   * {@code /rsm:CrossIndustryInvoice/rsm:SupplyChainTradeTransaction/ram:IncludedSupplyChainTradeLineItem[2]/...};
   * a position is given where an element has siblings of the same name.
   *
   * @return the location
   */
  public String location() {
    var steps = new ArrayList<String>();
    for (Node node = element; node instanceof Element current; node = node.getParentNode()) {
      int position = 0;
      int count    = 0;
      for (Node sibling = current.getParentNode() == null ?
                          current :
                          current.getParentNode()
                                 .getFirstChild(); sibling != null; sibling = sibling.getNextSibling()) {
        if (sibling instanceof Element other && other.getNodeName()
                                                     .equals(current.getNodeName())) {
          count++;
          if (other == current) {
            position = count;
          }
        }
      }
      steps.addFirst(current.getNodeName() + (count > 1 ?
                                              "[" + position + "]" :
                                              ""));
    }
    return "/" + String.join("/",
                             steps);
  }


  /**
   * ISO date (yyyy-MM-dd) of the first element reached by the path, or null.
   *
   * @param path slash separated prefixed names
   * @return the date, or null
   * @throws IllegalArgumentException if the text is not an ISO date
   */
  public LocalDate isoDate(String path) {
    var found = find(path);
    return found == null ?
           null :
           parseDate(found.text(),
                     DateTimeFormatter.ISO_LOCAL_DATE,
                     found.location());
  }

  /**
   * Date in UNTDID 2379 format 102 (yyyyMMdd) of the first element reached by the path, or null.
   *
   * @param path slash separated prefixed names
   * @return the date, or null
   * @throws IllegalArgumentException if the format is not 102 or the text is not a date
   */
  public LocalDate basicDate(String path) {
    var found = find(path);
    if (found == null || found.text() == null) {
      return null;
    }
    String format = found.attribute("format");
    if (format != null && !format.equals("102")) {
      throw new IllegalArgumentException("Unsupported date format " + format + " at " + found.location());
    }
    return parseDate(found.text(),
                     DateTimeFormatter.BASIC_ISO_DATE,
                     found.location());
  }

  /**
   * Date of a date or date-time in UNTDID 2379 format 102 (yyyyMMdd), 203 (yyyyMMddHHmm), or 204
   * (yyyyMMddHHmmss) of the first element reached by the path, or null. The time is not kept.
   *
   * @param path slash separated prefixed names
   * @return the date, or null
   * @throws IllegalArgumentException if the format is not supported or the text does not match it
   */
  public LocalDate dateOfDateTime(String path) {
    var found = find(path);
    if (found == null || found.text() == null) {
      return null;
    }
    String format = found.attribute("format");
    int length = format == null ?
                 8 :
                 switch (format) {
                   case "102" -> 8;
                   case "203" -> 12;
                   case "204" -> 14;
                   default -> throw new IllegalArgumentException("Unsupported date format " + format + " at " + found.location());
                 };
    String text = found.text();
    if (text.length() != length ||
        !text.chars()
             .allMatch(Character::isDigit)) {
      throw new IllegalArgumentException("Invalid date \"" + text + "\" at " + found.location());
    }
    return parseDate(text.substring(0,
                                    8),
                     DateTimeFormatter.BASIC_ISO_DATE,
                     found.location());
  }

  /**
   * Attribute value without namespace; null when absent or blank.
   *
   * @param name attribute name
   * @return the trimmed value, or null
   */
  public String attribute(String name) {
    String value = element.getAttribute(name)
                          .strip();
    return value.isEmpty() ?
           null :
           value;
  }
}

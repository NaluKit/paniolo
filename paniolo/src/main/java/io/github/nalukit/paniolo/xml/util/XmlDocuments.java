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
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.XMLConstants;
import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.FilterInputStream;
import java.io.FilterReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;

/**
 * Namespace-aware XML parsing without DTDs, external entities or XInclude.
 */
public final class XmlDocuments {
  private XmlDocuments() {
  }

  private static DocumentBuilderFactory factory()
      throws ParserConfigurationException {
    var factory = DocumentBuilderFactory.newDefaultInstance();
    factory.setNamespaceAware(true);
    factory.setXIncludeAware(false);
    factory.setExpandEntityReferences(false);
    factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING,
                       true);
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",
                       true);
    factory.setFeature("http://xml.org/sax/features/external-general-entities",
                       false);
    factory.setFeature("http://xml.org/sax/features/external-parameter-entities",
                       false);
    factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD,
                         "");
    factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA,
                         "");
    return factory;
  }

  /**
   * Returns the name of the root element without reading the rest of the document. DTDs and
   * external entities are not processed; the caller's stream is not closed.
   *
   * @param input the XML document
   * @return the qualified name of the root element, or {@code null} if the XML is malformed before
   * the root element or has none
   */
  public static QName rootName(InputStream input) {
    try {
      var reader = streamReader(input);
      try {
        while (reader.hasNext()) {
          if (reader.next() == XMLStreamConstants.START_ELEMENT) {
            return reader.getName();
          }
        }
      } finally {
        reader.close();
      }
    } catch (XMLStreamException e) {
      return null;
    }
    return null;
  }

  /**
   * Creates a namespace-aware StAX reader that processes neither DTDs nor external entities, for
   * reading only the start of a document. Closing the reader does not close the caller's stream.
   *
   * @param input the XML document
   * @return the reader
   * @throws XMLStreamException if the reader cannot be created
   */
  public static XMLStreamReader streamReader(InputStream input)
      throws XMLStreamException {
    var factory = XMLInputFactory.newDefaultFactory();
    factory.setProperty(XMLInputFactory.SUPPORT_DTD,
                        false);
    factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES,
                        false);
    return factory.createXMLStreamReader(java.util.Objects.requireNonNull(input,
                                                                          "input"));
  }


  /**
   * Creates an empty namespace-aware document.
   *
   * @return the document
   */
  public static Document create() {
    try {
      return factory().newDocumentBuilder()
                      .newDocument();
    } catch (ParserConfigurationException e) {
      throw new IllegalStateException("Cannot configure secure XML parser",
                                      e);
    }
  }

  /**
   * Parses XML without closing the caller's stream.
   *
   * @param input the XML document
   * @return the namespace-aware document
   * @throws IOException  if the stream cannot be read
   * @throws SAXException if the XML is not well-formed or contains a DOCTYPE
   */
  public static Document parse(InputStream input)
      throws IOException, SAXException {
    return parse(new InputSource(new FilterInputStream(java.util.Objects.requireNonNull(input,
                                                                                        "input")) {
      @Override
      public void close() {
      }
    }));
  }

  /**
   * Parses characters independently of the encoding named in the XML declaration, without closing
   * the caller's reader.
   *
   * @param input the XML document
   * @return the namespace-aware document
   * @throws IOException  if the reader cannot be read
   * @throws SAXException if the XML is not well-formed or contains a DOCTYPE
   */
  public static Document parse(Reader input)
      throws IOException, SAXException {
    return parse(new InputSource(new FilterReader(java.util.Objects.requireNonNull(input,
                                                                                   "input")) {
      @Override
      public void close() {
      }
    }));
  }

  private static Document parse(InputSource input)
      throws IOException, SAXException {
    try {
      var builder = factory().newDocumentBuilder();
      builder.setErrorHandler(new DefaultHandler() {
        @Override
        public void error(org.xml.sax.SAXParseException e)
            throws SAXException {
          throw e;
        }

        @Override
        public void fatalError(org.xml.sax.SAXParseException e)
            throws SAXException {
          throw e;
        }
      });
      return builder.parse(input);
    } catch (ParserConfigurationException e) {
      throw new IllegalStateException("Cannot configure secure XML parser",
                                      e);
    }
  }
}

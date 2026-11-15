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

import io.github.nalukit.paniolo.config.FacturXProfile;

import java.util.List;

/**
 * The generations of hybrid invoice PDFs Paniolo reads and checks, each with the XMP namespace of
 * its invoice properties, the embedded file name, the XMP version, the conformance levels, and the
 * root element of the embedded XML. The generation of a PDF follows from the namespace its XMP
 * properties actually use; namespace prefixes such as {@code fx} or {@code zf} are irrelevant, and
 * the file name alone never decides it. Container check and extraction share this mapping.
 *
 * <p>Only the namespaces listed here are accepted; properties with the same local names in any
 * other namespace are ignored.
 */
enum HybridGeneration {
  /**
   * Factur-X 1.0 / ZUGFeRD 2.1 and later: {@code factur-x.xml}, {@code xrechnung.xml} for the
   * profile XRECHNUNG, and {@code zugferd-invoice.xml}, which earlier Paniolo versions accepted with
   * these metadata, too. {@code fx:Version} is 1.0, for XRECHNUNG the XRechnung version.
   */
  FACTUR_X("Factur-X",
           "urn:factur-x:pdfa:CrossIndustryDocument:invoice:1p0#",
           "fx",
           List.of("factur-x.xml",
                   "xrechnung.xml",
                   "zugferd-invoice.xml"),
           "1.0",
           List.of("MINIMUM",
                   "BASIC WL",
                   "BASIC",
                   "EN 16931",
                   "EXTENDED",
                   "EXTENDED-CTC-FR",
                   "XRECHNUNG"),
           HybridGeneration.CII_NAMESPACE,
           "CrossIndustryInvoice"),
  /**
   * ZUGFeRD 2.0 (2019) with its own XMP namespace {@code urn:zugferd:pdfa:CrossIndustryDocument:invoice:2p0#}
   * and the file name {@code zugferd-invoice.xml}; the embedded XML is CII like Factur-X. The
   * specification states {@code Version} {@code 2p0}; {@code 1.0}, which some ZUGFeRD 2.0 tools
   * wrote, is accepted with the warning {@code FX-VERSION}, other values are errors.
   */
  ZUGFERD_2("ZUGFeRD 2.0",
            "urn:zugferd:pdfa:CrossIndustryDocument:invoice:2p0#",
            "zf",
            List.of("zugferd-invoice.xml"),
            "2p0",
            List.of("MINIMUM",
                    "BASIC WL",
                    "BASIC",
                    "EN 16931",
                    "EXTENDED"),
            HybridGeneration.CII_NAMESPACE,
            "CrossIndustryInvoice"),
  /**
   * ZUGFeRD 1.0 with {@code ZUGFeRD-invoice.xml}, the levels BASIC, COMFORT, and EXTENDED, and the
   * XML root {@code rsm:CrossIndustryDocument}; {@code Version} is 1.0.
   */
  ZUGFERD_1("ZUGFeRD 1.0",
            "urn:ferd:pdfa:CrossIndustryDocument:invoice:1p0#",
            "zf",
            List.of("ZUGFeRD-invoice.xml"),
            "1.0",
            List.of("BASIC",
                    "COMFORT",
                    "EXTENDED"),
            "urn:ferd:CrossIndustryDocument:invoice:1p0",
            "CrossIndustryDocument");

  /**
   * The XMP properties that describe the embedded invoice.
   */
  static final List<String> PROPERTIES = List.of("DocumentType",
                                                 "DocumentFileName",
                                                 "Version",
                                                 "ConformanceLevel");

  private static final String CII_NAMESPACE = "urn:un:unece:uncefact:data:standard:CrossIndustryInvoice:100";

  private final String       title;
  private final String       namespace;
  private final String       prefix;
  private final List<String> fileNames;
  private final String       version;
  private final List<String> levels;
  private final String       rootNamespace;
  private final String       rootName;

  HybridGeneration(String title,
                   String namespace,
                   String prefix,
                   List<String> fileNames,
                   String version,
                   List<String> levels,
                   String rootNamespace,
                   String rootName) {
    this.title         = title;
    this.namespace     = namespace;
    this.prefix        = prefix;
    this.fileNames     = fileNames;
    this.version       = version;
    this.levels        = levels;
    this.rootNamespace = rootNamespace;
    this.rootName      = rootName;
  }

  /**
   * The generation whose own file name is exactly this one: {@code ZUGFeRD-invoice.xml} for
   * ZUGFeRD 1.0, {@code zugferd-invoice.xml} for ZUGFeRD 2.0, otherwise Factur-X. Used only to word
   * the findings of a PDF whose XMP carries no invoice properties at all.
   */
  static HybridGeneration ofFileName(String fileName) {
    if (ZUGFERD_1.fileNames.contains(fileName)) {
      return ZUGFERD_1;
    }
    return ZUGFERD_2.fileNames.contains(fileName) ?
           ZUGFERD_2 :
           FACTUR_X;
  }

  /**
   * Name for messages, e.g. {@code ZUGFeRD 2.0}.
   */
  String title() {
    return title;
  }

  /**
   * XMP namespace of the invoice properties.
   */
  String namespace() {
    return namespace;
  }

  /**
   * Prefix used in messages; the documents may use any prefix.
   */
  String prefix() {
    return prefix;
  }

  /**
   * Embedded file names, compared case-sensitively.
   */
  List<String> fileNames() {
    return fileNames;
  }

  /**
   * The value of the {@code Version} property the specification states; for Factur-X XRECHNUNG
   * the XRechnung version is used instead.
   */
  String version() {
    return version;
  }

  /**
   * Conformance levels of this generation, compared case-sensitively.
   */
  List<String> levels() {
    return levels;
  }

  /**
   * Returns true if the level belongs to this generation.
   */
  boolean hasLevel(String level) {
    return level != null && levels.contains(level);
  }

  /**
   * The Factur-X profile of a conformance level of this generation, or null; always null for
   * ZUGFeRD 1.0, whose levels mean something else than the equally named Factur-X profiles.
   */
  FacturXProfile profile(String level) {
    if (this == ZUGFERD_1 || level == null) {
      return null;
    }
    // Factur-X accepted levels in any case before; ZUGFeRD 2.0 takes them exactly.
    return this == FACTUR_X || hasLevel(level) ?
           FacturXProfile.ofConformanceLevel(level) :
           null;
  }

  /**
   * Namespace of the root element of the embedded XML.
   */
  String rootNamespace() {
    return rootNamespace;
  }

  /**
   * Local name of the root element of the embedded XML.
   */
  String rootName() {
    return rootName;
  }
}

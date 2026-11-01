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
package io.github.nalukit.paniolo.config;

import io.github.nalukit.paniolo.model.common.ProcessControl;

import java.util.Arrays;
import java.util.Map;

/**
 * Factur-X / ZUGFeRD profiles. The values follow the Factur-X specification (ZUGFeRD 2.x) for use
 * in Germany, plus the French profile EXTENDED-CTC-FR, which is exchanged as CII XML only (see
 * {@link #isPdfWritable()}). MINIMUM and BASIC WL carry no invoice lines; they are written from an
 * invoice with stated totals ({@code Invoice.Builder.statedTotals}).
 */
public enum FacturXProfile {
  /**
   * Profile MINIMUM, document totals only; in Germany a booking aid, not an invoice. Written from
   * an invoice with stated totals and {@code ProcessControl.facturXMinimum()}.
   */
  MINIMUM("MINIMUM",
          "factur-x.xml",
          "1.0",
          ProcessControl.FACTURX_MINIMUM,
          false),
  /**
   * Profile BASIC WL, header and VAT breakdown without lines; in Germany a booking aid, not an
   * invoice. Written from an invoice with stated totals and {@code ProcessControl.facturXBasicWl()}.
   */
  BASIC_WL("BASIC WL",
           "factur-x.xml",
           "1.0",
           ProcessControl.FACTURX_BASIC_WL,
           false),
  /**
   * Profile BASIC, an EN 16931 subset without e.g. contacts, supporting documents, and item
   * details, embedded as {@code factur-x.xml}. The CII writer rejects invoices with content the
   * profile does not allow.
   */
  BASIC("BASIC",
        "factur-x.xml",
        "1.0",
        ProcessControl.FACTURX_BASIC,
        true),
  /**
   * Profile EN 16931 (formerly COMFORT), embedded as {@code factur-x.xml}.
   */
  EN16931("EN 16931",
          "factur-x.xml",
          "1.0",
          ProcessControl.EN16931,
          true),
  /**
   * Profile EXTENDED, EN 16931 plus the information in {@code Invoice.extended()}, embedded as
   * {@code factur-x.xml}.
   */
  EXTENDED("EXTENDED",
           "factur-x.xml",
           "1.0",
           ProcessControl.FACTURX_EXTENDED,
           true),
  /**
   * French profile EXTENDED-CTC-FR of the e-invoicing reform (XP Z12-012), a subset of EXTENDED
   * with the information in {@code Invoice.extended()}. It is exchanged as CII XML and validated
   * with the rules of FNFE-MPE; {@code fx:ConformanceLevel} {@code EXTENDED-CTC-FR}, as the Mustang
   * project writes it, is recognized when reading PDFs, but Paniolo writes no Factur-X PDF of this
   * profile.
   */
  EXTENDED_CTC_FR("EXTENDED-CTC-FR",
                  "factur-x.xml",
                  "1.0",
                  ProcessControl.FACTURX_EXTENDED_CTC_FR,
                  true),
  /**
   * Reference profile XRECHNUNG, embedded as {@code xrechnung.xml}. {@code fx:Version} carries the
   * XRechnung version, as in the official ZUGFeRD examples (e.g. 2.1, 3.0).
   */
  XRECHNUNG("XRECHNUNG",
            "xrechnung.xml",
            "3.0",
            ProcessControl.XRECHNUNG_3,
            true);

  /**
   * Data relationship of the embedded XML for the profiles from BASIC upwards; mandatory in Germany.
   */
  public static final String AF_RELATIONSHIP = "Alternative";

  /**
   * Specification identifiers (BT-24) of ZUGFeRD 2.0 and 2.1, which name the profiles differently.
   */
  private static final Map<String, FacturXProfile> LEGACY = Map.of("urn:zugferd.de:2p0:minimum",
                                                                   MINIMUM,
                                                                   "urn:zugferd.de:2p0:basicwl",
                                                                   BASIC_WL,
                                                                   "urn:cen.eu:en16931:2017#compliant#urn:zugferd.de:2p0:basic",
                                                                   BASIC,
                                                                   "urn:cen.eu:en16931:2017#conformant#urn:zugferd.de:2p0:extended",
                                                                   EXTENDED);

  private final String  conformanceLevel;
  private final String  fileName;
  private final String  xmpVersion;
  private final String  specificationId;
  private final boolean lines;

  FacturXProfile(String conformanceLevel,
                 String fileName,
                 String xmpVersion,
                 String specificationId,
                 boolean lines) {
    this.conformanceLevel = conformanceLevel;
    this.fileName         = fileName;
    this.xmpVersion       = xmpVersion;
    this.specificationId  = specificationId;
    this.lines            = lines;
  }

  /**
   * Returns the profile for an XMP conformance level, or null if it is not supported.
   *
   * @param conformanceLevel value of {@code fx:ConformanceLevel}, e.g. {@code EN 16931}
   * @return the profile, or null
   */
  public static FacturXProfile ofConformanceLevel(String conformanceLevel) {
    return Arrays.stream(values())
                 .filter(profile -> profile.conformanceLevel.equalsIgnoreCase(conformanceLevel))
                 .findFirst()
                 .orElse(null);
  }

  /**
   * Returns the profile an embedded file name identifies on its own, or null. Only
   * {@code xrechnung.xml} does; {@code factur-x.xml} is shared by all Factur-X profiles.
   *
   * @param fileName name of the embedded XML file
   * @return the profile, or null
   */
  public static FacturXProfile ofFileName(String fileName) {
    return XRECHNUNG.fileName.equalsIgnoreCase(fileName) ?
           XRECHNUNG :
           null;
  }

  /**
   * Returns the profile of a specification identifier (BT-24), including the identifiers of
   * ZUGFeRD 2.0 / 2.1 and of every XRechnung version, or null for other identifiers.
   *
   * @param specificationId specification identifier (BT-24)
   * @return the profile, or null
   */
  public static FacturXProfile ofSpecificationId(String specificationId) {
    if (specificationId == null) {
      return null;
    }
    String id = specificationId.strip();
    // XRechnung 3 uses urn:xeinkauf.de:kosit:xrechnung_3.0, XRechnung 2 urn:xoev-de:kosit:standard:xrechnung_2.x.
    if (id.contains(":kosit:") && id.contains("xrechnung")) {
      return XRECHNUNG;
    }
    return Arrays.stream(values())
                 .filter(profile -> profile.specificationId.equals(id))
                 .findFirst()
                 .orElse(LEGACY.get(id));
  }

  /**
   * Value of the XMP property {@code fx:ConformanceLevel}.
   *
   * @return the conformance level
   */
  public String getConformanceLevel() {
    return conformanceLevel;
  }

  /**
   * Name of the embedded XML file and value of {@code fx:DocumentFileName}.
   *
   * @return the file name
   */
  public String getFileName() {
    return fileName;
  }

  /**
   * Value of the XMP property {@code fx:Version}.
   *
   * @return the XMP version
   */
  public String getXmpVersion() {
    return xmpVersion;
  }

  /**
   * Required specification identifier (BT-24) of the embedded invoice.
   *
   * @return the specification identifier
   */
  public String getSpecificationId() {
    return specificationId;
  }

  /**
   * Data relationship of the embedded XML required in Germany: Data for MINIMUM and BASIC WL,
   * otherwise Alternative.
   *
   * @return the PDF name of the relationship
   */
  public String getAfRelationship() {
    return lines ?
           AF_RELATIONSHIP :
           "Data";
  }

  /**
   * Returns true if Paniolo can write invoices of this profile, which is the case for all profiles;
   * MINIMUM and BASIC WL need an invoice with stated totals. EXTENDED-CTC-FR is written as CII XML
   * only, not as Factur-X PDF.
   *
   * @return true for every profile
   */
  public boolean isWritable() {
    return true;
  }

  /**
   * Returns true if Paniolo writes Factur-X PDFs of this profile; false for EXTENDED-CTC-FR, which
   * is exchanged as CII XML.
   *
   * @return true if Factur-X PDFs of this profile can be written
   */
  public boolean isPdfWritable() {
    return this != EXTENDED_CTC_FR;
  }

  /**
   * Returns true if documents of this profile carry invoice lines and are EN 16931 invoices; false
   * for MINIMUM and BASIC WL.
   *
   * @return true if the profile has invoice lines
   */
  public boolean hasLines() {
    return lines;
  }

  /**
   * Name of the profile in the official Factur-X file names, e.g. {@code BASIC-WL} in
   * {@code FACTUR-X_BASIC-WL.xslt}; null for XRECHNUNG, which has no Factur-X rules, and for
   * EXTENDED-CTC-FR, which is validated with the EXTENDED schema and the rules of FNFE-MPE.
   *
   * @return the rules level, or null
   */
  public String getRulesLevel() {
    return this == XRECHNUNG || this == EXTENDED_CTC_FR ?
           null :
           name().replace('_',
                          '-');
  }
}

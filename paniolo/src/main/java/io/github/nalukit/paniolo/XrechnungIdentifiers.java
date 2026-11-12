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
package io.github.nalukit.paniolo;

import io.github.nalukit.paniolo.rules.xrechnung.XrechnungRules;
import io.github.nalukit.paniolo.validation.ValidationProfile.Syntax;
import io.github.nalukit.paniolo.validation.XrechnungScenario;
import io.github.nalukit.paniolo.validation.XrechnungSpecification;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The specification identifiers (BT-24) of every XRechnung version whose KoSIT release is packaged
 * in {@code paniolo-rules}, each with its scenario and the document types the scenario
 * exists for. The identifiers are those of the {@code match} expressions in the
 * {@code scenarios.xml} of the release; a test keeps both in sync.
 *
 * <p>Identifiers are compared exactly, as the KoSIT validator does. A document that states
 * XRechnung with any other identifier is invalid with {@link #UNSUPPORTED}.
 */
final class XrechnungIdentifiers {
  /**
   * Rule identifier of the error for a document that states XRechnung with an identifier that no
   * supported version and scenario accepts for its document type.
   */
  static final  String                                    UNSUPPORTED = "XR-SPECIFICATION-UNSUPPORTED";
  private static final String                             EN16931     = "urn:cen.eu:en16931:2017#compliant#";
  private static final Set<Syntax>                        ALL         = EnumSet.allOf(Syntax.class);
  private static final Set<Syntax>                        INVOICES    = EnumSet.of(Syntax.UBL_INVOICE);
  private static final Set<Syntax>                        INVOICE_CII = EnumSet.of(Syntax.UBL_INVOICE,
                                                                                   Syntax.CII);
  /**
   * Major and minor version, e.g. {@code 2.3}, mapped to the identifiers of its packaged release.
   */
  static final         Map<String, List<XrechnungSpecification>> PACKAGED    = packaged();

  private XrechnungIdentifiers() {
  }

  private static Map<String, List<XrechnungSpecification>> packaged() {
    var packaged = new LinkedHashMap<String, List<XrechnungSpecification>>();
    // XRechnung 1.2: no extension scenario.
    packaged.put("1.2",
                 specifications(standard2("1.2"),
                                XrechnungScenario.STANDARD,
                                ALL));
    // XRechnung 2.0 and 2.1: the extension exists for UBL Invoice only.
    for (var version : List.of("2.0",
                               "2.1")) {
      packaged.put(version,
                   join(specifications(standard2(version),
                                       XrechnungScenario.STANDARD,
                                       ALL),
                        specifications(extension2(version),
                                       XrechnungScenario.EXTENSION,
                                       INVOICES)));
    }
    // XRechnung 2.2 and 2.3: the extension exists for UBL Invoice and CII.
    for (var version : List.of("2.2",
                               "2.3")) {
      packaged.put(version,
                   join(specifications(standard2(version),
                                       XrechnungScenario.STANDARD,
                                       ALL),
                        specifications(extension2(version),
                                       XrechnungScenario.EXTENSION,
                                       INVOICE_CII)));
    }
    // XRechnung 3.0: new namespace, extension for UBL Invoice and CII, CVD for all document types.
    String standard3 = EN16931 + "urn:xeinkauf.de:kosit:xrechnung_3.0";
    packaged.put("3.0",
                 join(specifications(standard3,
                                     XrechnungScenario.STANDARD,
                                     ALL),
                      specifications(standard3 + "#conformant#urn:xeinkauf.de:kosit:extension:xrechnung_3.0",
                                     XrechnungScenario.EXTENSION,
                                     INVOICE_CII),
                      specifications(standard3 + "#compliant#urn:xeinkauf.de:kosit:xrechnung:cvd_0.9",
                                     XrechnungScenario.CVD,
                                     ALL)));
    return Collections.unmodifiableMap(packaged);
  }

  private static String standard2(String version) {
    return EN16931 + "urn:xoev-de:kosit:standard:xrechnung_" + version;
  }

  private static String extension2(String version) {
    return standard2(version) + "#conformant#urn:xoev-de:kosit:extension:xrechnung_" + version;
  }

  private static List<XrechnungSpecification> specifications(String identifier,
                                                             XrechnungScenario scenario,
                                                             Set<Syntax> syntaxes) {
    return syntaxes.stream()
                   .map(syntax -> new XrechnungSpecification(identifier,
                                                             scenario,
                                                             syntax))
                   .toList();
  }

  @SafeVarargs
  private static List<XrechnungSpecification> join(List<XrechnungSpecification>... lists) {
    var joined = new ArrayList<XrechnungSpecification>();
    for (var list : lists) {
      joined.addAll(list);
    }
    return List.copyOf(joined);
  }

  /**
   * Returns the major and minor version of a full version, e.g. {@code 3.0} for {@code 3.0.2}.
   */
  static String majorMinor(String version) {
    String[] parts = version.split("\\.");
    return parts.length < 2 ?
           version :
           parts[0] + "." + parts[1];
  }

  /**
   * The identifiers of the packaged current XRechnung release, or null if the table does not know
   * its version.
   */
  static List<XrechnungSpecification> packagedCurrent() {
    return PACKAGED.get(majorMinor(XrechnungRules.VERSION));
  }

  /**
   * Returns true if a specification identifier claims XRechnung, its extension, CVD or an older
   * version of it, whether supported or not, e.g.
   * {@code urn:cen.eu:en16931:2017#compliant#urn:xeinkauf.de:kosit:xrechnung_3.0}.
   */
  static boolean claimsXrechnung(String specificationId) {
    return specificationId != null && specificationId.toLowerCase(Locale.ROOT)
                                                     .contains("xrechnung");
  }

  /**
   * Explains why an identifier that claims XRechnung is not accepted for a document type.
   *
   * @param specificationId the identifier of the document
   * @param type            the document type
   * @param configured      the identifiers of the configured XRechnung rules
   * @param older           the older packaged versions with their identifiers
   * @return the message of the error {@link #UNSUPPORTED}
   */
  static String explain(String specificationId,
                        Syntax type,
                        List<XrechnungSpecification> configured,
                        Map<String, List<XrechnungSpecification>> older) {
    var all = new ArrayList<>(configured);
    older.values()
         .forEach(all::addAll);
    var otherTypes = all.stream()
                        .filter(specification -> specification.identifier()
                                                              .equals(specificationId))
                        .map(specification -> name(specification.syntax()))
                        .collect(Collectors.toCollection(LinkedHashSet::new));
    String reason = otherTypes.isEmpty() ?
                    "no supported XRechnung version or scenario has this identifier, which must match exactly without other spelling, whitespace or appended parts" :
                    "its scenario exists only for " + String.join(", ",
                                                                  otherTypes);
    var current = configured.stream()
                            .filter(specification -> specification.syntax() == type)
                            .map(XrechnungSpecification::identifier)
                            .distinct()
                            .toList();
    return "The document states the XRechnung specification identifier (BT-24) '" +
           specificationId +
           "', which is not supported for " +
           name(type) +
           ": " +
           reason +
           ". The document is invalid; its other findings are those of the current XRechnung rules. Supported for " +
           name(type) +
           " are " +
           String.join(", ",
                       current) +
           (older.isEmpty() ?
            "" :
            " and the identifiers of the packaged older versions " + String.join(", ",
                                                                                 older.keySet()));
  }

  /**
   * Location of BT-24 in a document of the type.
   */
  static String location(Syntax type) {
    return switch (type) {
      case UBL_INVOICE -> "/ubl:Invoice/cbc:CustomizationID";
      case UBL_CREDIT_NOTE -> "/ubl:CreditNote/cbc:CustomizationID";
      case CII -> "/rsm:CrossIndustryInvoice/rsm:ExchangedDocumentContext/ram:GuidelineSpecifiedDocumentContextParameter/ram:ID";
    };
  }

  private static String name(Syntax type) {
    return switch (type) {
      case UBL_INVOICE -> "UBL Invoice";
      case UBL_CREDIT_NOTE -> "UBL CreditNote";
      case CII -> "CII";
    };
  }
}

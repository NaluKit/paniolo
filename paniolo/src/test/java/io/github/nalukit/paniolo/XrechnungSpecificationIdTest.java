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

import io.github.nalukit.paniolo.config.InvoiceSyntax;
import io.github.nalukit.paniolo.model.common.ProcessControl;
import io.github.nalukit.paniolo.model.common.value.InvoiceTypeCode;
import io.github.nalukit.paniolo.rules.xrechnung.XrechnungRules;
import io.github.nalukit.paniolo.validation.Severity;
import io.github.nalukit.paniolo.validation.ValidationMessage;
import io.github.nalukit.paniolo.validation.ValidationProfile;
import io.github.nalukit.paniolo.validation.ValidationResult;
import io.github.nalukit.paniolo.validation.XrechnungScenario;
import io.github.nalukit.paniolo.validation.XrechnungSpecification;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A document that states XRechnung in its specification identifier (BT-24) is only valid if the
 * complete identifier selects a supported XRechnung version and a scenario that exists for its
 * document type (Mustang issue 1343). Unknown identifiers are not validated silently as the current
 * version.
 */
class XrechnungSpecificationIdTest {
  private static final Paniolo PANIOLO     = Paniolo.create();
  private static final String  UNSUPPORTED = "XR-SPECIFICATION-UNSUPPORTED";
  private static final String  XR_2        = "urn:cen.eu:en16931:2017#compliant#urn:xoev-de:kosit:standard:xrechnung_";
  private static final String  XR_3        = ProcessControl.XRECHNUNG_3;

  /**
   * The three document types of the control invoices.
   */
  enum Kind {
    CII(InvoiceSyntax.CII,
        InvoiceTypeCode.CODE_380,
        ValidationProfile.Syntax.CII),
    UBL_INVOICE(InvoiceSyntax.UBL,
                InvoiceTypeCode.CODE_380,
                ValidationProfile.Syntax.UBL_INVOICE),
    UBL_CREDIT_NOTE(InvoiceSyntax.UBL,
                    InvoiceTypeCode.CODE_381,
                    ValidationProfile.Syntax.UBL_CREDIT_NOTE);

    final InvoiceSyntax            syntax;
    final InvoiceTypeCode          type;
    final ValidationProfile.Syntax validationSyntax;

    Kind(InvoiceSyntax syntax,
         InvoiceTypeCode type,
         ValidationProfile.Syntax validationSyntax) {
      this.syntax           = syntax;
      this.type             = type;
      this.validationSyntax = validationSyntax;
    }

    /**
     * A valid XRechnung 3.0 control invoice whose BT-24 is replaced, and nothing else.
     */
    String xml(String specificationId) {
      String xml = PANIOLO.toXml(TestInvoices.invoice(type,
                                                      "SPEC-1"),
                                 syntax);
      assertTrue(xml.contains(">" + XR_3 + "<"));
      return xml.replace(">" + XR_3 + "<",
                         ">" + specificationId + "<");
    }
  }

  private static List<ValidationMessage> unsupported(ValidationResult result) {
    return result.messages()
                 .stream()
                 .filter(message -> UNSUPPORTED.equals(message.ruleId()))
                 .toList();
  }

  private static void assertRejected(ValidationResult result,
                                     String specificationId,
                                     Kind kind) {
    assertFalse(result.isValid(),
                () -> result.messages()
                            .toString());
    var errors = unsupported(result);
    assertEquals(1,
                 errors.size(),
                 () -> result.messages()
                             .toString());
    var error = errors.get(0);
    assertEquals(Severity.ERROR,
                 error.severity());
    assertTrue(error.message()
                    .contains("'" + specificationId + "'"),
               error::message);
    assertTrue(error.location()
                    .endsWith(kind == Kind.CII ?
                              "ram:ID" :
                              "cbc:CustomizationID"),
               error::location);
    // The XML report carries rule, severity and the identifier.
    String report = result.toXml();
    assertTrue(report.contains("rule=\"" + UNSUPPORTED + "\""),
               report);
    assertTrue(report.contains("severity=\"ERROR\""),
               report);
    assertTrue(report.contains(specificationId.strip()
                                              .replace("&",
                                                       "&amp;")),
               report);
  }

  private static void assertAccepted(ValidationResult result) {
    assertEquals(List.of(),
                 unsupported(result),
                 () -> result.messages()
                             .toString());
  }

  @ParameterizedTest
  @EnumSource(Kind.class)
  void acceptsTheControlInvoicesWithTheirSupportedIdentifier(Kind kind) {
    var result = PANIOLO.validate(kind.xml(XR_3));
    assertTrue(result.isValid(),
               () -> result.messages()
                           .toString());
    assertAccepted(result);
  }

  static Stream<Arguments> reportedIdentifiers() {
    var arguments = new ArrayList<Arguments>();
    for (var kind : Kind.values()) {
      for (var id : List.of(XR_2 + "3.0",
                            "urn:cen.eu:en16931:2017#compliant#urn:xeinkauf.de:kosit:xrechnung_9.9",
                            XR_3 + "#garbage")) {
        arguments.add(Arguments.of(kind,
                                   id));
      }
    }
    return arguments.stream();
  }

  /**
   * The identifiers of the review of Mustang issue 1343 were accepted as valid.
   */
  @ParameterizedTest
  @MethodSource("reportedIdentifiers")
  void rejectsTheReportedIdentifiers(Kind kind,
                                     String specificationId) {
    var xml = kind.xml(specificationId);
    assertRejected(PANIOLO.validate(xml),
                   specificationId,
                   kind);
    assertRejected(PANIOLO.validate(xml.getBytes(StandardCharsets.UTF_8)),
                   specificationId,
                   kind);
    // Also without detection, against the explicitly chosen document type.
    assertRejected(PANIOLO.validate(xml.getBytes(StandardCharsets.UTF_8),
                                    kind.validationSyntax),
                   specificationId,
                   kind);
  }

  static Stream<Arguments> unsupportedIdentifiers() {
    var ids = List.of(
        // unknown versions
        XR_2 + "1.1",
        XR_2 + "2.4",
        "urn:cen.eu:en16931:2017#compliant#urn:xeinkauf.de:kosit:xrechnung_3.1",
        "urn:cen.eu:en16931:2017#compliant#urn:xeinkauf.de:kosit:xrechnung_2.3",
        // other spelling
        "urn:cen.eu:en16931:2017#compliant#urn:xeinkauf.de:kosit:XRechnung_3.0",
        "URN:CEN.EU:EN16931:2017#COMPLIANT#URN:XEINKAUF.DE:KOSIT:XRECHNUNG_3.0",
        "urn:cen.eu:en16931:2017#conformant#urn:xeinkauf.de:kosit:xrechnung_3.0",
        " " + XR_3,
        XR_3 + " ",
        // appended parts
        XR_3 + ".2",
        XR_3 + "#compliant#urn:xeinkauf.de:kosit:xrechnung:cvd_1.0",
        XR_2 + "2.3#garbage",
        XR_2 + "1.2#conformant#urn:xoev-de:kosit:extension:xrechnung_1.2",
        // extension of another version
        XR_3 + "#conformant#urn:xeinkauf.de:kosit:extension:xrechnung_2.3",
        XR_3 + "#conformant#urn:xeinkauf.de:kosit:extension:xrechnung_3.0#x");
    var arguments = new ArrayList<Arguments>();
    for (var kind : Kind.values()) {
      for (var id : ids) {
        arguments.add(Arguments.of(kind,
                                   id));
      }
    }
    return arguments.stream();
  }

  @ParameterizedTest
  @MethodSource("unsupportedIdentifiers")
  void rejectsUnsupportedIdentifiers(Kind kind,
                                     String specificationId) {
    assertRejected(PANIOLO.validate(kind.xml(specificationId)),
                   specificationId,
                   kind);
  }

  /**
   * KoSIT defines the extension for UBL Invoice and CII only (XRechnung 2.0 and 2.1: UBL Invoice
   * only), the scenario of another document type does not exist.
   */
  @ParameterizedTest
  @MethodSource("extensionsOfOtherDocumentTypes")
  void rejectsAnExtensionForADocumentTypeWithoutItsScenario(Kind kind,
                                                            String specificationId) {
    var result = PANIOLO.validate(kind.xml(specificationId));
    assertRejected(result,
                   specificationId,
                   kind);
    assertTrue(unsupported(result).get(0)
                                  .message()
                                  .contains("its scenario exists only for"),
               () -> unsupported(result).get(0)
                                        .message());
  }

  static Stream<Arguments> extensionsOfOtherDocumentTypes() {
    return Stream.of(Arguments.of(Kind.UBL_CREDIT_NOTE,
                                  ProcessControl.XRECHNUNG_3_EXTENSION),
                     Arguments.of(Kind.UBL_CREDIT_NOTE,
                                  XR_2 + "2.3#conformant#urn:xoev-de:kosit:extension:xrechnung_2.3"),
                     Arguments.of(Kind.CII,
                                  XR_2 + "2.1#conformant#urn:xoev-de:kosit:extension:xrechnung_2.1"),
                     Arguments.of(Kind.CII,
                                  XR_2 + "2.0#conformant#urn:xoev-de:kosit:extension:xrechnung_2.0"));
  }

  static Stream<Arguments> supportedScenarios() {
    return Stream.of(Arguments.of(Kind.UBL_INVOICE,
                                  ProcessControl.XRECHNUNG_3_EXTENSION,
                                  "EN16931 XRechnung Extension (UBL Invoice)"),
                     Arguments.of(Kind.CII,
                                  ProcessControl.XRECHNUNG_3_EXTENSION,
                                  "EN16931 XRechnung Extension (CII)"),
                     Arguments.of(Kind.CII,
                                  XR_2 + "2.3#conformant#urn:xoev-de:kosit:extension:xrechnung_2.3",
                                  "EN16931 XRechnung Extension (CII), XRechnung 2.3.1"),
                     Arguments.of(Kind.UBL_INVOICE,
                                  XR_2 + "2.0#conformant#urn:xoev-de:kosit:extension:xrechnung_2.0",
                                  "EN16931 XRechnung Extension (UBL Invoice), XRechnung 2.0.1"),
                     Arguments.of(Kind.UBL_INVOICE,
                                  XR_3 + "#compliant#urn:xeinkauf.de:kosit:xrechnung:cvd_0.9",
                                  "EN16931 XRechnung CVD (UBL Invoice)"),
                     Arguments.of(Kind.UBL_CREDIT_NOTE,
                                  XR_3 + "#compliant#urn:xeinkauf.de:kosit:xrechnung:cvd_0.9",
                                  "EN16931 XRechnung CVD (UBL CreditNote)"),
                     Arguments.of(Kind.CII,
                                  XR_3 + "#compliant#urn:xeinkauf.de:kosit:xrechnung:cvd_0.9",
                                  "EN16931 XRechnung CVD (CII)"),
                     Arguments.of(Kind.UBL_CREDIT_NOTE,
                                  XR_2 + "1.2",
                                  "EN16931 CIUS XRechnung (UBL CreditNote), XRechnung 1.2.2"));
  }

  /**
   * Supported extensions, CVD and older versions keep their scenario.
   */
  @ParameterizedTest
  @MethodSource("supportedScenarios")
  void validatesSupportedIdentifiersWithTheirScenario(Kind kind,
                                                      String specificationId,
                                                      String profileName) {
    var result = PANIOLO.validate(kind.xml(specificationId));
    assertAccepted(result);
    assertEquals(profileName,
                 result.profileName());
  }

  /**
   * Plain EN 16931, Peppol BIS Billing and Factur-X do not state XRechnung and are not affected.
   */
  @ParameterizedTest
  @EnumSource(Kind.class)
  void doesNotRejectOtherSpecifications(Kind kind) {
    var en16931 = PANIOLO.validate(kind.xml("urn:cen.eu:en16931:2017"));
    assertTrue(en16931.isValid(),
               () -> en16931.messages()
                            .toString());
    assertAccepted(en16931);
    var peppol = PANIOLO.validate(kind.xml("urn:cen.eu:en16931:2017#compliant#urn:fdc:peppol.eu:2017:poacc:billing:3.0"));
    assertTrue(peppol.isValid(),
               () -> peppol.messages()
                           .toString());
    assertAccepted(peppol);
    if (kind == Kind.CII) {
      var facturX = PANIOLO.validate(kind.xml("urn:cen.eu:en16931:2017#conformant#urn:factur-x.eu:1p0:extended"));
      assertTrue(facturX.isValid(),
                 () -> facturX.messages()
                              .toString());
      assertAccepted(facturX);
    }
  }

  /**
   * The central table equals the {@code match} expressions of the packaged KoSIT releases, and
   * covers each of them.
   */
  @Test
  void theTableMatchesTheScenariosOfThePackagedReleases() {
    var versions = new ArrayList<>(XrechnungRules.LEGACY_VERSIONS);
    versions.add(XrechnungIdentifiers.majorMinor(XrechnungRules.VERSION));
    assertEquals(new HashSet<>(versions),
                 XrechnungIdentifiers.PACKAGED.keySet());
    for (var version : XrechnungRules.LEGACY_VERSIONS) {
      assertEquals(new HashSet<>(ValidationProfile.xrechnungSpecifications(XrechnungRules.legacyBundle(version))),
                   new HashSet<>(XrechnungIdentifiers.PACKAGED.get(version)),
                   version);
    }
    assertEquals(new HashSet<>(ValidationProfile.xrechnungSpecifications(XrechnungRules.bundle())),
                 new HashSet<>(XrechnungIdentifiers.packagedCurrent()));
  }

  /**
   * A custom KoSIT bundle names the identifiers it accepts in its {@code scenarios.xml}; no change
   * of Paniolo is needed.
   */
  @Test
  void acceptsTheIdentifiersOfACustomBundle(@TempDir Path directory)
      throws IOException {
    Path   bundle  = copy(XrechnungRules.bundle(),
                          directory.resolve("bundle"));
    Path   file    = bundle.resolve("scenarios.xml");
    String custom  = XR_3 + "#compliant#urn:example.org:custom_1.0";
    String content = Files.readString(file);
    // Only the standard scenarios name the bare identifier followed by the closing quote.
    String changed = content.replace("kosit:xrechnung_3.0']",
                                     "kosit:xrechnung_3.0#compliant#urn:example.org:custom_1.0']");
    assertTrue(!changed.equals(content));
    Files.writeString(file,
                      changed);
    assertTrue(ValidationProfile.xrechnungSpecifications(bundle)
                                .contains(new XrechnungSpecification(custom,
                                                                     XrechnungScenario.STANDARD,
                                                                     ValidationProfile.Syntax.UBL_INVOICE)));
    var paniolo = Paniolo.builder()
                         .rules(bundle)
                         .build();
    for (var kind : Kind.values()) {
      var accepted = paniolo.validate(kind.xml(custom));
      assertAccepted(accepted);
      assertTrue(accepted.profileName()
                         .startsWith("EN16931 XRechnung ("),
                 accepted.profileName());
      // The bundle no longer accepts the identifier of XRechnung 3.0.
      assertRejected(paniolo.validate(kind.xml(XR_3)),
                     XR_3,
                     kind);
      // Older packaged versions stay supported.
      assertAccepted(paniolo.validate(kind.xml(XR_2 + "2.3")));
    }
    // The default instance does not accept the custom identifier.
    assertRejected(PANIOLO.validate(Kind.UBL_INVOICE.xml(custom)),
                   custom,
                   Kind.UBL_INVOICE);
  }

  private static Path copy(Path source,
                           Path target)
      throws IOException {
    try (var paths = Files.walk(source)) {
      for (var path : paths.toList()) {
        var destination = target.resolve(source.relativize(path)
                                               .toString());
        if (Files.isDirectory(path)) {
          Files.createDirectories(destination);
        } else {
          Files.copy(path,
                     destination);
        }
      }
    }
    return target;
  }
}

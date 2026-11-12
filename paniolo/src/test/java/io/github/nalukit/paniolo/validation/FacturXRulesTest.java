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
package io.github.nalukit.paniolo.validation;

import io.github.nalukit.paniolo.model.common.ProcessControl;
import io.github.nalukit.paniolo.model.common.value.CurrencyCode;
import io.github.nalukit.paniolo.xml.cii.CiiInvoiceWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Uses a synthetic package with the file layout of the official Factur-X package, which Paniolo
 * does not distribute.
 */
class FacturXRulesTest {
  private static Path packageDirectory()
      throws Exception {
    return Path.of(FacturXRulesTest.class.getResource("/facturx-package")
                                         .toURI());
  }

  private static byte[] basicInvoice(CurrencyCode currency) {
    var invoice = InvoiceSamples.invoice()
                                .processControl(ProcessControl.en16931())
                                .currencyCode(currency)
                                .addLine(InvoiceSamples.line("1",
                                                             "100",
                                                             InvoiceSamples.standard()))
                                .build();
    return new CiiInvoiceWriter().toBytes(invoice);
  }

  @Test
  void findsProfileFilesAndReadsDeclaredCodeList()
      throws Exception {
    var profile = ValidationProfile.facturX(packageDirectory(),
                                            "BASIC");
    assertEquals("Factur-X BASIC",
                 profile.name());
    assertEquals("FACTUR-X_BASIC.xsd",
                 profile.schema()
                        .getFileName()
                        .toString());
    assertEquals(List.of("FACTUR-X_BASIC_codedb.xml"),
                 profile.documents()
                        .stream()
                        .map(path -> path.getFileName()
                                         .toString())
                        .toList());

    var validator = new InvoiceValidator(profile);
    var valid     = validator.validate(basicInvoice(CurrencyCode.EUR));
    assertTrue(valid.isValid(),
               () -> valid.messages()
                          .toString());
    var invalid = validator.validate(basicInvoice(CurrencyCode.USD));
    assertFalse(invalid.isValid());
    assertEquals(List.of("FX-TEST-CURRENCY"),
                 invalid.messages()
                        .stream()
                        .map(ValidationMessage::ruleId)
                        .toList());
  }

  @Test
  void findsTheVersionedSchemaNameOfTheFerdPackage(
      @TempDir Path ferd)
      throws Exception {
    Path source = packageDirectory();
    Files.createDirectories(ferd.resolve("Schema/2_Factur-X_1.09.2_BASIC/_XSLT_BASIC"));
    Files.copy(source.resolve("schema/BASIC/FACTUR-X_BASIC.xsd"),
               ferd.resolve("Schema/2_Factur-X_1.09.2_BASIC/Factur-X_1.09.2_BASIC.xsd"));
    // Imported schemas carry the level in the middle of the name and must not match.
    Files.copy(source.resolve("schema/BASIC/FACTUR-X_BASIC.xsd"),
               ferd.resolve("Schema/2_Factur-X_1.09.2_BASIC/Factur-X_1.09.2_BASIC_urn_un_unece_uncefact_data_standard_QualifiedDataType_100.xsd"));
    for (String file : List.of("FACTUR-X_BASIC.xslt",
                               "FACTUR-X_BASIC_codedb.xml")) {
      Files.copy(source.resolve("schematron/" + file),
                 ferd.resolve("Schema/2_Factur-X_1.09.2_BASIC/_XSLT_BASIC/" + file));
    }
    var profile = ValidationProfile.facturX(ferd,
                                            "BASIC");
    assertEquals("Factur-X_1.09.2_BASIC.xsd",
                 profile.schema()
                        .getFileName()
                        .toString());
    var result = new InvoiceValidator(profile).validate(basicInvoice(CurrencyCode.EUR));
    assertTrue(result.isValid(),
               () -> result.messages()
                           .toString());
  }

  @Test
  void refusesDocumentsTheProfileDoesNotDeclare()
      throws Exception {
    Path root = packageDirectory();
    var profile = new ValidationProfile("forbidden",
                                        root,
                                        root.resolve("schema/BASIC/FACTUR-X_BASIC.xsd"),
                                        List.of(root.resolve("schematron/FORBIDDEN.xslt")),
                                        Map.of());
    var validator = new InvoiceValidator(profile);
    assertThrows(ValidationException.class,
                 () -> validator.validate(basicInvoice(CurrencyCode.EUR)));
  }

  @Test
  void rejectsUnknownLevelsAndMissingOrAmbiguousFiles(
      @TempDir Path empty)
      throws Exception {
    assertThrows(IllegalArgumentException.class,
                 () -> ValidationProfile.facturX(packageDirectory(),
                                                 "COMFORT"));
    var missing = assertThrows(ValidationException.class,
                               () -> ValidationProfile.facturX(packageDirectory(),
                                                               "EN16931"));
    assertTrue(missing.getCause()
                      .getMessage()
                      .contains("FACTUR-X_EN16931.xsd"),
               () -> missing.getCause()
                            .getMessage());
    Files.createDirectories(empty.resolve("a"));
    Files.createDirectories(empty.resolve("b"));
    Files.writeString(empty.resolve("a/FACTUR-X_BASIC.xsd"),
                      "<x/>");
    Files.writeString(empty.resolve("b/FACTUR-X_BASIC.xsd"),
                      "<x/>");
    assertThrows(ValidationException.class,
                 () -> ValidationProfile.facturX(empty,
                                                 "BASIC"));
  }
}

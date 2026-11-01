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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class InvoiceValidatorTest {
  @TempDir
  Path directory;
  private ValidationProfile profile;

  @BeforeEach
  void rules()
      throws Exception {
    Files.writeString(directory.resolve("invoice.xsd"),
                      """
                      <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                        <xs:element name="Invoice"><xs:complexType><xs:sequence>
                          <xs:element name="ID" type="xs:string"/>
                        </xs:sequence></xs:complexType></xs:element>
                      </xs:schema>
                      """);
    stylesheet("""
               <svrl:schematron-output>
                 <xsl:if test="not(Invoice/ID = 'OK')">
                   <svrl:failed-assert id="BR-TEST" flag="fatal" location="/Invoice/ID">
                     <svrl:text>Invoice ID must be OK</svrl:text>
                   </svrl:failed-assert>
                 </xsl:if>
                 <svrl:successful-report id="INFO-TEST" flag="information" location="/Invoice">
                   <svrl:text>Invoice checked</svrl:text>
                 </svrl:successful-report>
               </svrl:schematron-output>
               """);
    profile = new ValidationProfile("Test rules v1",
                                    directory,
                                    directory.resolve("invoice.xsd"),
                                    List.of(directory.resolve("rules.xsl")),
                                    Map.of());
  }

  private void stylesheet(String body)
      throws Exception {
    Files.writeString(directory.resolve("rules.xsl"),
                      """
                      <xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                        xmlns:svrl="http://purl.oclc.org/dsdl/svrl">
                        <xsl:template match="/">
                      """ + body + "</xsl:template></xsl:stylesheet>");
  }

  @Test
  void returnsRuleSeverityTextAndPath() {
    var result = new InvoiceValidator(profile).validate("<Invoice><ID>BAD</ID></Invoice>");
    assertFalse(result.isValid());
    assertEquals(new ValidationMessage("BR-TEST",
                                       Severity.FATAL,
                                       "Invoice ID must be OK",
                                       "/Invoice/ID",
                                       ValidationMessage.Stage.SCHEMATRON),
                 result.messages()
                       .getFirst());
  }

  @Test
  void measuresDuration() {
    var result = new InvoiceValidator(profile).validate("<Invoice><ID>OK</ID></Invoice>");
    assertTrue(result.duration() != null &&
               !result.duration()
                      .isNegative(),
               () -> String.valueOf(result.duration()));
  }

  @Test
  void informationalReportsDoNotInvalidateInvoice() {
    var result = new InvoiceValidator(profile).validate("<Invoice><ID>OK</ID></Invoice>");
    assertTrue(result.isValid());
    assertEquals(Severity.INFO,
                 result.messages()
                       .getFirst()
                       .severity());
  }

  @Test
  void honorsProfileSeverityOverrides() {
    var overridden = new ValidationProfile(profile.name(),
                                           directory,
                                           profile.schema(),
                                           profile.schematronStylesheets(),
                                           Map.of("BR-TEST",
                                                  Severity.WARNING));
    var result = new InvoiceValidator(overridden).validate("<Invoice><ID>BAD</ID></Invoice>");
    assertTrue(result.isValid());
    assertTrue(result.hasWarnings());
  }

  @Test
  void stopsBeforeSchematronOnSchemaErrors() {
    var result = new InvoiceValidator(profile).validate("<Invoice/>");
    assertFalse(result.isValid());
    assertTrue(result.messages()
                     .stream()
                     .allMatch(m -> m.stage() == ValidationMessage.Stage.XSD));
  }

  @Test
  void malformedXmlIsAValidationFinding() {
    var result = new InvoiceValidator(profile).validate("<Invoice>");
    assertFalse(result.isValid());
    assertEquals("XML-PARSE",
                 result.messages()
                       .getFirst()
                       .ruleId());
    assertTrue(result.messages()
                     .getFirst()
                     .location()
                     .contains("line 1"));
  }

  @Test
  void rejectsExternalEntitiesBeforeReadingTheirContent() {
    var xml    = "<!DOCTYPE Invoice [<!ENTITY xxe SYSTEM 'file:///etc/passwd'>]><Invoice><ID>&xxe;</ID></Invoice>";
    var result = new InvoiceValidator(profile).validate(xml);
    assertFalse(result.isValid());
    assertEquals(ValidationMessage.Stage.XML,
                 result.messages()
                       .getFirst()
                       .stage());
  }

  @Test
  void reusableAcrossConcurrentValidations() {
    var validator = new InvoiceValidator(profile);
    assertTrue(IntStream.range(0,
                               20)
                        .parallel()
                        .allMatch(i -> validator.validate("<Invoice><ID>OK</ID></Invoice>")
                                                .isValid()));
  }

  @Test
  void rejectsRulesOutsideBundle() {
    assertDoesNotThrow(() -> Files.createDirectories(directory.resolve("subdir")));
    var outside = new ValidationProfile("Outside",
                                        directory.resolve("subdir"),
                                        profile.schema(),
                                        profile.schematronStylesheets(),
                                        Map.of());
    assertThrows(ValidationException.class,
                 () -> new InvoiceValidator(outside));
  }

  @Test
  void rejectsRuntimeDocumentAccess()
      throws Exception {
    stylesheet("""
               <svrl:schematron-output><xsl:sequence select="doc('file:///etc/passwd')"/></svrl:schematron-output>
               """);
    var validator = new InvoiceValidator(profile);
    assertThrows(ValidationException.class,
                 () -> validator.validate("<Invoice><ID>OK</ID></Invoice>"));
  }

  @Test
  void rejectsNonSvrlResults()
      throws Exception {
    stylesheet("<anything/>");
    var validator = new InvoiceValidator(profile);
    assertThrows(ValidationException.class,
                 () -> validator.validate("<Invoice><ID>OK</ID></Invoice>"));
  }

  @Test
  void rejectsRawSchematronWithActionableException()
      throws Exception {
    Files.writeString(directory.resolve("rules.xsl"),
                      "<schema xmlns='http://purl.oclc.org/dsdl/schematron'/>");
    var exception = assertThrows(ValidationException.class,
                                 () -> new InvoiceValidator(profile));
    assertTrue(exception.getCause()
                        .getMessage()
                        .contains("compiled Schematron XSLT"));
  }

  @Test
  void stringInputIgnoresByteEncodingDeclarationAndByteInputHonorsIt() {
    var xml       = "<?xml version='1.0' encoding='UTF-16'?><Invoice><ID>OK</ID></Invoice>";
    var validator = new InvoiceValidator(profile);
    assertTrue(validator.validate(xml)
                        .isValid());
    assertTrue(validator.validate(xml.getBytes(java.nio.charset.StandardCharsets.UTF_16))
                        .isValid());
  }

  @Test
  void leavesCallerStreamOpen() {
    var stream = new java.io.ByteArrayInputStream("<Invoice><ID>OK</ID></Invoice>".getBytes()) {
      @Override
      public void close() {
        fail("Validator must not close caller's stream");
      }
    };
    assertTrue(new InvoiceValidator(profile).validate(stream)
                                            .isValid());
  }

  @Test
  void supportsLocalStylesheetImports()
      throws Exception {
    Files.move(directory.resolve("rules.xsl"),
               directory.resolve("imported.xsl"));
    Files.writeString(directory.resolve("rules.xsl"),
                      """
                      <xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                        <xsl:import href="imported.xsl"/>
                      </xsl:stylesheet>
                      """);
    assertTrue(new InvoiceValidator(profile).validate("<Invoice><ID>OK</ID></Invoice>")
                                            .isValid());
  }

  @Test
  void rejectsStylesheetImportsOutsideBundle()
      throws Exception {
    Files.createDirectories(directory.resolve("nested"));
    Files.writeString(directory.resolve("nested/rules.xsl"),
                      """
                      <xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                        <xsl:import href="../rules.xsl"/>
                      </xsl:stylesheet>
                      """);
    Files.copy(directory.resolve("invoice.xsd"),
               directory.resolve("nested/invoice.xsd"));
    var confined = new ValidationProfile("Confined",
                                         directory.resolve("nested"),
                                         directory.resolve("nested/invoice.xsd"),
                                         List.of(directory.resolve("nested/rules.xsl")),
                                         Map.of());
    assertThrows(ValidationException.class,
                 () -> new InvoiceValidator(confined));
  }

  @Test
  void rejectsRuntimeTextAccess()
      throws Exception {
    stylesheet("""
               <svrl:schematron-output><xsl:value-of select="unparsed-text('file:///etc/passwd')"/></svrl:schematron-output>
               """);
    var validator = new InvoiceValidator(profile);
    assertThrows(ValidationException.class,
                 () -> validator.validate("<Invoice><ID>OK</ID></Invoice>"));
  }

}

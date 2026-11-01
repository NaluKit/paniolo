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

import io.github.nalukit.paniolo.Paniolo;
import io.github.nalukit.paniolo.config.FacturXProfile;
import io.github.nalukit.paniolo.config.InvoiceSyntax;
import io.github.nalukit.paniolo.config.UnknownDocumentHandling;
import io.github.nalukit.paniolo.model.common.value.InvoiceTypeCode;
import io.github.nalukit.paniolo.validation.Severity;
import io.github.nalukit.paniolo.validation.ValidationMessage;
import io.github.nalukit.paniolo.validation.ValidationResult;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.common.filespecification.PDComplexFileSpecification;
import org.apache.pdfbox.pdmodel.common.filespecification.PDEmbeddedFile;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Undetectable documents: by default an exception (Paniolo), with
 * {@link UnknownDocumentHandling#REPORT_INVALID} an invalid result (Mustang compatible).
 */
class UnknownDocumentHandlingTest {
  private static final Paniolo PANIOLO = Paniolo.create();
  private static final Paniolo REPORT  = Paniolo.builder()
                                                .unknownDocuments(UnknownDocumentHandling.REPORT_INVALID)
                                                .build();

  private static final byte[] UNKNOWN   = "<Order xmlns=\"urn:example\"/>".getBytes(StandardCharsets.UTF_8);
  // Not well-formed before the root element; with a known root element the rules report it anyway.
  private static final byte[] MALFORMED = "<?xml version=\"1.0\"?><<invoice/>".getBytes(StandardCharsets.UTF_8);
  private static final byte[] TRUNCATED = "<rsm:CrossIndustryInvoice xmlns:rsm=\"urn:un:unece:uncefact:data:standard:CrossIndustryInvoice:100\"><unclosed>".getBytes(StandardCharsets.UTF_8);

  private static ValidationMessage only(ValidationResult result) {
    assertFalse(result.isValid());
    assertEquals(1,
                 result.messages()
                       .size(),
                 result::toString);
    var message = result.messages()
                        .getFirst();
    assertEquals(Severity.FATAL,
                 message.severity());
    return message;
  }

  /**
   * A Factur-X PDF whose embedded factur-x.xml is replaced by the given bytes.
   */
  private static byte[] pdfWith(byte[] xml) {
    byte[] pdf = PANIOLO.toFacturX(FacturXInvoices.invoice(FacturXProfile.EN16931,
                                                           InvoiceTypeCode.CODE_380)
                                                  .build(),
                                   TestPdfs.pdfA2b(),
                                   FacturXProfile.EN16931);
    try (var document = Loader.loadPDF(pdf)) {
      // The invoice file is referenced from /AF and from the EmbeddedFiles name tree.
      var specifications = new java.util.ArrayList<PDComplexFileSpecification>();
      specifications.add(new PDComplexFileSpecification((COSDictionary) document.getDocumentCatalog()
                                                                                .getCOSObject()
                                                                                .getCOSArray(COSName.getPDFName("AF"))
                                                                                .getObject(0)));
      specifications.addAll(document.getDocumentCatalog()
                                    .getNames()
                                    .getEmbeddedFiles()
                                    .getNames()
                                    .values());
      for (var specification : specifications) {
        var file = new PDEmbeddedFile(document,
                                      new ByteArrayInputStream(xml));
        file.setSubtype("text/xml");
        file.setSize(xml.length);
        specification.setEmbeddedFile(file);
        specification.setEmbeddedFileUnicode(file);
      }
      var out = new ByteArrayOutputStream();
      document.save(out);
      return out.toByteArray();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  @Test
  void throwsByDefault() {
    for (byte[] document : List.of(UNKNOWN,
                                   MALFORMED)) {
      assertThrows(IllegalArgumentException.class,
                   () -> PANIOLO.validate(document));
      assertThrows(IllegalArgumentException.class,
                   () -> PANIOLO.validate(new String(document,
                                                     StandardCharsets.UTF_8)));
      assertThrows(IllegalArgumentException.class,
                   () -> PANIOLO.validate(new ByteArrayInputStream(document)));
      assertThrows(IllegalArgumentException.class,
                   () -> PANIOLO.validate(document,
                                          FacturXProfile.EN16931));
    }
    assertThrows(IllegalArgumentException.class,
                 () -> PANIOLO.validate(pdfWith(UNKNOWN)));
    // Unchanged: a known root element is validated, and the parse error is a finding.
    assertEquals("XML-PARSE",
                 PANIOLO.validate(TRUNCATED)
                        .messages()
                        .getFirst()
                        .ruleId());
  }

  @Test
  void reportsAnUnsupportedRootElementAsInvalid() {
    for (ValidationResult result : List.of(REPORT.validate(UNKNOWN),
                                           REPORT.validate(new String(UNKNOWN,
                                                                      StandardCharsets.UTF_8)),
                                           REPORT.validate(new ByteArrayInputStream(UNKNOWN)),
                                           REPORT.validate(UNKNOWN,
                                                           FacturXProfile.EN16931))) {
      var message = only(result);
      assertEquals("PANIOLO-UNKNOWN-DOCUMENT",
                   message.ruleId());
      assertEquals(ValidationMessage.Stage.XML,
                   message.stage());
      assertTrue(message.message()
                        .contains("{urn:example}Order"),
                 message::message);
      assertEquals("Unknown document",
                   result.profileName());
      assertNotNull(result.duration());
    }
  }

  @Test
  void reportsMalformedXmlWithItsPosition() {
    for (byte[] document : List.of(MALFORMED,
                                   new byte[0],
                                   "no xml".getBytes(StandardCharsets.UTF_8))) {
      var message = only(REPORT.validate(document));
      assertEquals("XML-PARSE",
                   message.ruleId(),
                   message::toString);
      assertTrue(message.location()
                        .startsWith("line "),
                 message::toString);
      assertFalse(message.message()
                         .contains("ParseError at"),
                  message::message);
    }
  }

  @Test
  void reportsUnknownXmlInAPdfAsContainerFinding() {
    var result = REPORT.validate(pdfWith(UNKNOWN));
    assertFalse(result.isValid(),
                result::toString);
    var unknown = result.messages(ValidationMessage.Stage.HYBRID)
                        .stream()
                        .filter(message -> message.ruleId()
                                                  .equals("PANIOLO-UNKNOWN-DOCUMENT"))
                        .toList();
    assertEquals(1,
                 unknown.size(),
                 result::toString);
    assertTrue(result.messages(ValidationMessage.Stage.SCHEMATRON)
                     .isEmpty());
  }

  @Test
  void leavesSupportedDocumentsUnchanged() {
    byte[] xml = PANIOLO.toBytes(FacturXInvoices.invoice(FacturXProfile.EN16931,
                                                         InvoiceTypeCode.CODE_380)
                                                .build(),
                                 InvoiceSyntax.CII);
    assertEquals(PANIOLO.validate(xml)
                        .messages(),
                 REPORT.validate(xml)
                       .messages());
    // A known root element with broken content stays a finding of the rules, as before.
    String truncated = new String(xml,
                                  StandardCharsets.UTF_8).replaceAll("<ram:ID>[^<]*</ram:ID>",
                                                                     "");
    assertEquals(PANIOLO.validate(truncated)
                        .messages(),
                 REPORT.validate(truncated)
                       .messages());
  }

  @Test
  void addsNoCalculationAndEmptyStatistics() {
    var paniolo = Paniolo.builder()
                         .unknownDocuments(UnknownDocumentHandling.REPORT_INVALID)
                         .calculationCheck(true)
                         .ruleStatistics(true)
                         .build();
    var result = paniolo.validate(UNKNOWN);
    only(result);
    assertTrue(result.statistics()
                     .schematron()
                     .isEmpty());
    assertThrows(NullPointerException.class,
                 () -> Paniolo.builder()
                              .unknownDocuments(null));
  }
}

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

import io.github.nalukit.paniolo.config.FacturXProfile;
import io.github.nalukit.paniolo.config.InvoiceSyntax;
import io.github.nalukit.paniolo.model.Invoice;
import io.github.nalukit.paniolo.model.common.InvoiceLine;
import io.github.nalukit.paniolo.model.common.InvoiceNote;
import io.github.nalukit.paniolo.model.common.ProcessControl;
import io.github.nalukit.paniolo.model.common.TaxCategory;
import io.github.nalukit.paniolo.model.common.value.InvoiceTypeCode;
import io.github.nalukit.paniolo.model.extended.LineExtension;
import io.github.nalukit.paniolo.validation.Severity;
import io.github.nalukit.paniolo.validation.ValidationMessage;
import io.github.nalukit.paniolo.validation.ValidationResult;
import io.github.nalukit.paniolo.xml.ReadMode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Factur-X EXTENDED states a note by its text, its content code, or both (BR-FXEXT-01 for line
 * notes, BR-FXEXT-02 for document notes; task V05). Such a note is read and written again; every
 * other profile still requires the text.
 */
class ContentCodeNoteTest {
  private static final Paniolo PANIOLO = Paniolo.create();

  private static final InvoiceNote CODE_ONLY = new InvoiceNote("AAI",
                                                               null,
                                                               "ST1");

  private static Invoice.Builder builder(ProcessControl processControl) {
    return TestInvoices.builder(InvoiceTypeCode.CODE_380,
                                "NOTE-1")
                       .processControl(processControl)
                       .addNote(CODE_ONLY)
                       .addNote(new InvoiceNote(null,
                                                "Lieferung frei Haus",
                                                "ST2"))
                       .addLine(InvoiceLine.create()
                                           .id("2")
                                           .name("Wartung")
                                           .quantity(BigDecimal.ONE)
                                           .unitCode("C62")
                                           .netUnitPrice(new BigDecimal("50.00"))
                                           .tax(TaxCategory.standard(new BigDecimal("19")))
                                           .extended(LineExtension.create()
                                                                  .noteContentCode("LC1")
                                                                  .noteSubjectCode("AAI")
                                                                  .build())
                                           .build());
  }

  private static List<ValidationMessage> errors(ValidationResult result) {
    return result.messages()
                 .stream()
                 .filter(message -> message.severity() == Severity.ERROR || message.severity() == Severity.FATAL)
                 .toList();
  }

  @Test
  void requiresTextOrContentCode() {
    assertThrows(IllegalArgumentException.class,
                 () -> new InvoiceNote(null,
                                       null,
                                       null));
    assertThrows(IllegalArgumentException.class,
                 () -> new InvoiceNote(null,
                                       " ",
                                       "ST1"));
    assertThrows(IllegalArgumentException.class,
                 () -> new InvoiceNote(null,
                                       null,
                                       " "));
    assertNull(CODE_ONLY.text());
    var line = assertThrows(IllegalArgumentException.class,
                            () -> InvoiceLine.create()
                                             .id("1")
                                             .name("Wartung")
                                             .quantity(BigDecimal.ONE)
                                             .unitCode("C62")
                                             .netUnitPrice(BigDecimal.TEN)
                                             .tax(TaxCategory.standard(new BigDecimal("19")))
                                             .extended(LineExtension.create()
                                                                    .noteSubjectCode("AAI")
                                                                    .build())
                                             .build());
    assertEquals("Line 1: a note subject code requires a line note (BT-127) or a note content code",
                 line.getMessage());
  }

  @Test
  void writesAndReadsNotesWithAContentCodeOnly() {
    var    invoice = builder(ProcessControl.facturXExtended()).build();
    String xml     = PANIOLO.toXml(invoice,
                                   InvoiceSyntax.CII);
    String compact = xml.replaceAll(">\\s+<",
                                    "><");
    assertTrue(compact.contains("<ram:IncludedNote><ram:ContentCode>ST1</ram:ContentCode><ram:SubjectCode>AAI</ram:SubjectCode></ram:IncludedNote>"),
               compact);
    assertTrue(compact.contains("<ram:IncludedNote><ram:ContentCode>LC1</ram:ContentCode><ram:SubjectCode>AAI</ram:SubjectCode></ram:IncludedNote>"),
               compact);
    assertEquals(List.of(),
                 errors(PANIOLO.validate(xml.getBytes(StandardCharsets.UTF_8),
                                         FacturXProfile.EXTENDED)));
    for (var mode : ReadMode.values()) {
      var read = PANIOLO.read(xml,
                              mode);
      assertEquals(List.of(),
                   read.warnings());
      assertEquals(invoice.notes(),
                   read.invoice()
                       .notes());
      var line = read.invoice()
                     .lines()
                     .get(1);
      assertNull(line.note());
      assertEquals("LC1",
                   line.extended()
                       .noteContentCode());
      assertEquals(xml,
                   PANIOLO.toXml(read.invoice(),
                                 InvoiceSyntax.CII));
    }
  }

  /**
   * The case found while checking task V05: a document note with a content code instead of its
   * text, valid in EXTENDED, made both read modes fail with {@code note must not be blank}.
   */
  @Test
  void readsADocumentNoteThatReplacesItsTextByACode() {
    String xml = PANIOLO.toXml(TestInvoices.builder(InvoiceTypeCode.CODE_380,
                                                    "NOTE-2")
                                           .processControl(ProcessControl.facturXExtended())
                                           .addNote(new InvoiceNote("AAI",
                                                                    "Hinweistext",
                                                                    null))
                                           .build(),
                               InvoiceSyntax.CII);
    String codeOnly = xml.replace("<ram:Content>Hinweistext</ram:Content>",
                                  "<ram:ContentCode>ST1</ram:ContentCode>");
    assertTrue(PANIOLO.validate(codeOnly.getBytes(StandardCharsets.UTF_8),
                                FacturXProfile.EXTENDED)
                      .isValid());
    for (var mode : ReadMode.values()) {
      var read = assertDoesNotThrow(() -> PANIOLO.read(codeOnly,
                                                       mode));
      assertEquals(List.of(CODE_ONLY),
                   read.invoice()
                       .notes());
    }
  }

  @Test
  void requiresTheTextOutsideExtended() {
    var error = assertThrows(IllegalArgumentException.class,
                             () -> TestInvoices.builder(InvoiceTypeCode.CODE_380,
                                                        "NOTE-3")
                                               .addNote(CODE_ONLY)
                                               .build());
    assertEquals("note must not be blank; only the Factur-X profiles EXTENDED and EXTENDED-CTC-FR allow a note with a content code only",
                 error.getMessage());
  }
}

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
import io.github.nalukit.paniolo.xml.ReadMode;
import io.github.nalukit.paniolo.xml.ReadWarning;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The UBL reader takes the due date (BT-9) from the structured {@code PaymentTerms/PaymentDueDate}
 * of invoices and credit notes, next to the header {@code DueDate} and the date in the payment terms
 * text that the writer uses for credit notes. Different dates in two places are rejected by strict
 * reading and reported by lenient reading.
 */
class UblDueDateTest {
  private static final Paniolo   PANIOLO   = Paniolo.create();
  private static final LocalDate DUE       = LocalDate.of(2026,
                                                          11,
                                                          10);
  private static final String    STRUCTURED = "01-creditnote-strukturierte-faelligkeit";
  private static final String    TEXT       = "02-creditnote-faelligkeit-als-text";
  private static final String    HEADER     = "03-invoice-faelligkeit-im-kopf";
  private static final String    CII        = "04-creditnote-cii-referenz";

  private static String xml(String name) {
    try (InputStream input = UblDueDateTest.class.getResourceAsStream("/regression/due-date/" + name + ".xml")) {
      return new String(input.readAllBytes(),
                        StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  /** The invoice with a header due date gets the structured date, with or without the header date. */
  private static String invoiceWithPaymentDueDate(String date,
                                                  boolean keepHeader) {
    String xml = xml(HEADER).replace("</cac:PaymentMeans>",
                                     "</cac:PaymentMeans><cac:PaymentTerms><cbc:PaymentDueDate>" + date +
                                     "</cbc:PaymentDueDate></cac:PaymentTerms>");
    return keepHeader ?
           xml :
           xml.replace("<cbc:DueDate>2018-04-13</cbc:DueDate>",
                       "");
  }

  private static String creditNoteWithText(String text) {
    return xml(STRUCTURED).replace("<cbc:PaymentDueDate>",
                                   "<cbc:Note>" + text + "</cbc:Note><cbc:PaymentDueDate>");
  }

  @Test
  void structuredDueDateOfACreditNoteIsReadInEveryMode() {
    String xml = xml(STRUCTURED);
    assertTrue(!xml.contains("Due date"),
               "the document has no date in text");
    for (var mode : ReadMode.values()) {
      var result = PANIOLO.read(xml,
                                mode);
      assertEquals(DUE,
                   result.invoice()
                         .dueDate(),
                   mode.toString());
      assertTrue(result.warnings()
                       .isEmpty(),
                 mode + ": " + result.warnings());
    }
  }

  @Test
  void validationKeepsTheOfficialWarning() {
    var result = PANIOLO.validate(xml(STRUCTURED));
    assertTrue(result.isValid(),
               () -> result.messages()
                           .toString());
    assertTrue(result.messages()
                     .stream()
                     .anyMatch(message -> "UBL-CR-463".equals(message.ruleId())),
               () -> result.messages()
                           .toString());
  }

  @Test
  void existingWaysToStateTheDueDateStillWork() {
    for (var mode : ReadMode.values()) {
      assertEquals(DUE,
                   PANIOLO.read(xml(TEXT),
                                mode)
                          .invoice()
                          .dueDate(),
                   "text " + mode);
      assertEquals(LocalDate.of(2018,
                                4,
                                13),
                   PANIOLO.read(xml(HEADER),
                                mode)
                          .invoice()
                          .dueDate(),
                   "header " + mode);
      assertEquals(DUE,
                   PANIOLO.read(xml(CII),
                                mode)
                          .invoice()
                          .dueDate(),
                   "CII " + mode);
    }
  }

  @Test
  void structuredDueDateOfAnInvoiceIsRead() {
    for (var mode : ReadMode.values()) {
      var result = PANIOLO.read(invoiceWithPaymentDueDate("2018-05-01",
                                                          false),
                                mode);
      assertEquals(LocalDate.of(2018,
                                5,
                                1),
                   result.invoice()
                         .dueDate(),
                   mode.toString());
      assertTrue(result.warnings()
                       .isEmpty(),
                 mode + ": " + result.warnings());
    }
  }

  @Test
  void theDueDateSurvivesAnExportInBothSyntaxes() {
    var invoice = PANIOLO.read(xml(STRUCTURED),
                               ReadMode.STRICT)
                         .invoice();
    for (var syntax : List.of(InvoiceSyntax.UBL,
                              InvoiceSyntax.CII)) {
      String exported = PANIOLO.toXml(invoice,
                                      syntax);
      var validation = PANIOLO.validate(exported);
      assertTrue(validation.isValid(),
                 () -> syntax + ": " + validation.messages());
      assertEquals(DUE,
                   PANIOLO.read(exported,
                                ReadMode.STRICT)
                          .invoice()
                          .dueDate(),
                   syntax.toString());
    }
  }

  @Test
  void aMissingDueDateStaysMissing() {
    String noDate = xml(TEXT).replace("Due date: 2026-11-10",
                                      "Zahlbar in 14 Tagen");
    String noTerms = xml(STRUCTURED).replace("<cbc:PaymentDueDate>2026-11-10</cbc:PaymentDueDate>",
                                             "");
    for (var xml : List.of(noDate,
                           noTerms)) {
      for (var mode : ReadMode.values()) {
        assertNull(PANIOLO.read(xml,
                                mode)
                          .invoice()
                          .dueDate(),
                   mode.toString());
      }
    }
  }

  @Test
  void anInvalidStructuredDateIsRejected() {
    String xml = xml(STRUCTURED).replace("2026-11-10</cbc:PaymentDueDate>",
                                         "2026-13-45</cbc:PaymentDueDate>");
    for (var mode : ReadMode.values()) {
      var error = assertThrows(IllegalArgumentException.class,
                               () -> PANIOLO.read(xml,
                                                  mode),
                               mode.toString());
      assertTrue(error.getMessage()
                      .contains("2026-13-45"),
                 error.getMessage());
    }
  }

  @Test
  void sameDateInStructuredFieldAndTextIsKeptOnce() {
    String xml = creditNoteWithText("Due date: 2026-11-10");
    for (var mode : ReadMode.values()) {
      var result = PANIOLO.read(xml,
                                mode);
      assertEquals(DUE,
                   result.invoice()
                         .dueDate(),
                   mode.toString());
      assertNull(result.invoice()
                       .paymentTerms(),
                 mode.toString());
      assertTrue(result.warnings()
                       .isEmpty(),
                 mode + ": " + result.warnings());
    }
  }

  @Test
  void differentDatesInStructuredFieldAndTextConflict() {
    String xml = creditNoteWithText("Due date: 2026-11-12");
    var error = assertThrows(IllegalArgumentException.class,
                             () -> PANIOLO.read(xml,
                                                ReadMode.STRICT));
    assertTrue(error.getMessage()
                    .contains("2026-11-12") && error.getMessage()
                                                    .contains("2026-11-10"),
               error.getMessage());
    for (var mode : List.of(ReadMode.LENIENT,
                            ReadMode.LENIENT_KEEP_LINES)) {
      var result = PANIOLO.read(xml,
                                mode);
      assertEquals(DUE,
                   result.invoice()
                         .dueDate(),
                   mode.toString());
      assertEquals(1,
                   result.warnings()
                         .size(),
                   mode.toString());
      assertEquals(ReadWarning.DUE_DATE,
                   result.warnings()
                         .get(0)
                         .code());
    }
  }

  @Test
  void headerAndStructuredDateOfAnInvoice() {
    String same = invoiceWithPaymentDueDate("2018-04-13",
                                            true);
    for (var mode : ReadMode.values()) {
      var result = PANIOLO.read(same,
                                mode);
      assertEquals(LocalDate.of(2018,
                                4,
                                13),
                   result.invoice()
                         .dueDate());
      assertTrue(result.warnings()
                       .isEmpty(),
                 mode + ": " + result.warnings());
    }
    String different = invoiceWithPaymentDueDate("2018-05-01",
                                                 true);
    assertThrows(IllegalArgumentException.class,
                 () -> PANIOLO.read(different,
                                    ReadMode.STRICT));
    for (var mode : List.of(ReadMode.LENIENT,
                            ReadMode.LENIENT_KEEP_LINES)) {
      var result = PANIOLO.read(different,
                                mode);
      assertEquals(LocalDate.of(2018,
                                4,
                                13),
                   result.invoice()
                         .dueDate(),
                   "the header date is kept");
      assertEquals(ReadWarning.DUE_DATE,
                   result.warnings()
                         .get(0)
                         .code());
    }
  }
}

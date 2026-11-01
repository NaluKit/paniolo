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
package io.github.nalukit.paniolo.migration;

import io.github.nalukit.paniolo.Paniolo;
import io.github.nalukit.paniolo.config.FacturXProfile;
import io.github.nalukit.paniolo.config.InvoiceSyntax;
import io.github.nalukit.paniolo.migration.MustangMigration.FieldMapping;
import io.github.nalukit.paniolo.migration.MustangMigration.Status;
import io.github.nalukit.paniolo.model.common.ProcessControl;
import io.github.nalukit.paniolo.validation.ValidationResult;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The sample Mustang integration switches to Paniolo: every getter it uses is mapped, lost, or
 * needs a decision, the migrated invoices are valid, and the values that could not be carried over
 * are reported.
 */
class MustangMigrationTest {
  private static final Paniolo PANIOLO = Paniolo.create();

  private static Set<String> getters() {
    var getters = new TreeSet<String>();
    for (Class<?> type : MustangInterfaces.class.getDeclaredClasses()) {
      for (Method method : type.getDeclaredMethods()) {
        if (!Modifier.isStatic(method.getModifiers()) && (method.getName()
                                                                .startsWith("get") || method.getName()
                                                                                            .startsWith("is"))) {
          getters.add(type.getSimpleName() + "." + method.getName());
        }
      }
    }
    return getters;
  }

  private static String messages(ValidationResult result) {
    return result.messages()
                 .stream()
                 .map(Object::toString)
                 .collect(Collectors.joining("\n"));
  }

  @Test
  void everyGetterHasAMapping() {
    var mapped = MustangMigration.FIELDS.stream()
                                        .map(FieldMapping::getter)
                                        .toList();
    assertEquals(mapped.size(),
                 Set.copyOf(mapped)
                    .size(),
                 "getters listed twice");
    assertEquals(getters(),
                 new TreeSet<>(mapped));
    for (FieldMapping field : MustangMigration.FIELDS) {
      assertFalse(field.target() == null || field.target()
                                                 .isBlank(),
                  field::toString);
      if (field.status() != Status.MAPPED) {
        assertFalse(field.note()
                         .isBlank(),
                    () -> "a loss or decision needs a reason: " + field);
      }
    }
  }

  @Test
  void migratesTheInvoiceToFacturXExtended() {
    var result = MustangMigration.migrate(SampleIntegration.invoice(),
                                          ProcessControl.facturXExtended(),
                                          SampleIntegration.ZONE);
    var invoice = result.invoice();
    byte[] xml = PANIOLO.toBytes(invoice,
                                 InvoiceSyntax.CII);
    var validation = PANIOLO.validate(xml,
                                      FacturXProfile.EXTENDED);
    assertTrue(validation.isValid(),
               () -> messages(validation));
    assertEquals(invoice,
                 PANIOLO.read(xml));

    // Gross price 45.00 minus product allowance 5.00; the line allowance stays on line 1.
    var book = invoice.lines()
                      .get(1);
    assertEquals(0,
                 new BigDecimal("40.00").compareTo(book.netUnitPrice()));
    assertEquals(0,
                 new BigDecimal("45.00").compareTo(book.grossPrice()));
    assertEquals(1,
                 invoice.lines()
                        .get(0)
                        .adjustments()
                        .size());
    assertEquals("Termin nach Absprache\nAnfahrt inklusive",
                 invoice.lines()
                        .get(0)
                        .note());
    // EXTENDED keeps fax, invoicee, delivery note, document name and the logistics charge.
    assertEquals("+49 30 123457",
                 invoice.seller()
                        .extended()
                        .contact()
                        .fax());
    assertEquals("LS-2026-42",
                 invoice.extended()
                        .deliveryNoteReference()
                        .id());
    assertEquals(1,
                 invoice.extended()
                        .logisticsServiceCharges()
                        .size());
    assertEquals(1,
                 invoice.supportingDocuments()
                        .size());
    assertEquals(1,
                 invoice.cashDiscounts()
                        .size());
    // What EN 16931 and EXTENDED cannot carry is named.
    assertEquals(Set.of("ExportableTransaction.getBuyerOrderReferencedDocumentIssueDateTime",
                        "ExportableTransaction.getSpecifiedProcuringProjectName",
                        "FileAttachment.getRelation"),
                 getterNames(result.lost()),
                 () -> String.join("\n",
                                   result.lost()));
  }

  @Test
  void migratesTheSameInvoiceToEn16931AndNamesWhatNeedsExtended() {
    var result = MustangMigration.migrate(SampleIntegration.invoice(),
                                          ProcessControl.en16931(),
                                          SampleIntegration.ZONE);
    byte[] xml = PANIOLO.toBytes(result.invoice(),
                                 InvoiceSyntax.CII);
    var validation = PANIOLO.validate(xml,
                                      FacturXProfile.EN16931);
    assertTrue(validation.isValid(),
               () -> messages(validation));
    assertEquals(Set.of("ExportableTransaction.getBuyerOrderReferencedDocumentIssueDateTime",
                        "ExportableTransaction.getSpecifiedProcuringProjectName",
                        "FileAttachment.getRelation",
                        "ExportableTransaction.getDocumentName",
                        "ExportableTransaction.getInvoicee",
                        "ExportableTransaction.getDeliveryNoteReferencedDocumentID",
                        "Contact.getFax"),
                 getterNames(result.lost()),
                 () -> String.join("\n",
                                   result.lost()));
    // Without EXTENDED the logistics charge is a document charge, so the amount due is the same.
    var extended = MustangMigration.migrate(SampleIntegration.invoice(),
                                            ProcessControl.facturXExtended(),
                                            SampleIntegration.ZONE);
    assertEquals(0,
                 extended.invoice()
                         .totals()
                         .payableAmount()
                         .compareTo(result.invoice()
                                          .totals()
                                          .payableAmount()));
  }

  @Test
  void migratesTheDirectDebitInvoiceToXrechnung() {
    var result = MustangMigration.migrate(SampleIntegration.directDebitInvoice(),
                                          ProcessControl.xrechnung(),
                                          SampleIntegration.ZONE);
    for (var syntax : InvoiceSyntax.values()) {
      var validation = PANIOLO.validate(result.invoice(),
                                        syntax);
      assertTrue(validation.isValid(),
                 () -> syntax + "\n" + messages(validation));
    }
    var debit = result.invoice()
                      .paymentInstruction()
                      .directDebit();
    assertEquals("MANDAT-1",
                 debit.mandateReference());
    assertEquals("DE98ZZZ09999999999",
                 debit.creditorId());
    assertEquals(7,
                 result.invoice()
                       .cashDiscounts()
                       .get(0)
                       .days());
    assertEquals(Set.of("Contact.getFax"),
                 getterNames(result.lost()),
                 () -> String.join("\n",
                                   result.lost()));
  }

  @Test
  void theMigrationGuideListsTheSameMapping()
      throws IOException {
    Path guide = Path.of("..",
                         "docs",
                         "migration-mustang.md");
    var rows = Files.readAllLines(guide)
                    .stream()
                    .filter(line -> line.startsWith("| `"))
                    .map(line -> Arrays.stream(line.split("\\|"))
                                       .map(String::strip)
                                       .toList())
                    .filter(cells -> cells.size() >= 5 && cells.get(1)
                                                               .contains("."))
                    .map(cells -> cells.get(1)
                                       .replace("`",
                                                "") + " " + cells.get(2))
                    .collect(Collectors.toCollection(TreeSet::new));
    var expected = MustangMigration.FIELDS.stream()
                                          .map(field -> field.getter() + " " + label(field.status()))
                                          .collect(Collectors.toCollection(TreeSet::new));
    assertEquals(expected,
                 rows);
  }

  static String label(Status status) {
    return switch (status) {
      case MAPPED -> "mapped";
      case DECISION -> "decision";
      case LOST -> "lost";
    };
  }

  private static Set<String> getterNames(List<String> lost) {
    return lost.stream()
               .map(entry -> entry.substring(0,
                                             entry.indexOf(':')))
               .collect(Collectors.toSet());
  }
}

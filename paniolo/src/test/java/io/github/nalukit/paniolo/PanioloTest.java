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
import io.github.nalukit.paniolo.model.common.value.InvoiceTypeCode;
import io.github.nalukit.paniolo.rules.xrechnung.XrechnungRules;
import io.github.nalukit.paniolo.validation.ValidationProfile;
import io.github.nalukit.paniolo.xml.ExportVariant;
import io.github.nalukit.paniolo.xml.ReadMode;
import io.github.nalukit.paniolo.xml.ReadWarning;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PanioloTest {
  private static final Paniolo PANIOLO = Paniolo.create();

  @ParameterizedTest
  @CsvSource({ "UBL, CODE_380, UBL_INVOICE",
               "UBL, CODE_381, UBL_CREDIT_NOTE",
               "CII, CODE_380, CII",
               "CII, CODE_381, CII" })
  void writesDetectsValidatesAndReadsBack(InvoiceSyntax syntax,
                                          InvoiceTypeCode type,
                                          ValidationProfile.Syntax expected) {
    var invoice = TestInvoices.invoice(type,
                                       "R-1");
    String xml = PANIOLO.toXml(invoice,
                               syntax);

    assertEquals(expected,
                 PANIOLO.detect(xml));
    var result = PANIOLO.validate(xml);
    assertTrue(result.isValid(),
               () -> result.messages()
                           .toString());
    assertFalse(result.hasWarnings(),
                () -> result.messages()
                            .toString());
    assertTrue(PANIOLO.validate(invoice,
                                syntax)
                      .isValid());

    assertEquals(invoice,
                 PANIOLO.read(xml));
    assertEquals(invoice,
                 PANIOLO.read(PANIOLO.toBytes(invoice,
                                              syntax)));
    assertEquals(invoice,
                 PANIOLO.read(new ByteArrayInputStream(PANIOLO.toBytes(invoice,
                                                                       syntax))));
  }

  @Test
  void reportsBusinessRuleViolations() {
    var invoice = TestInvoices.invoice(InvoiceTypeCode.CODE_380,
                                       "R-1");
    var xml = PANIOLO.toXml(invoice,
                            InvoiceSyntax.CII)
                     .replace("<ram:BuyerReference>04011000-12345-03</ram:BuyerReference>",
                              "");
    var result = PANIOLO.validate(xml);
    assertFalse(result.isValid());
    assertTrue(result.messages()
                     .stream()
                     .anyMatch(m -> m.ruleId()
                                     .equals("BR-DE-15")),
               () -> result.messages()
                           .toString());
  }

  @Test
  void readsDeviatingTotalsWhenLenient() {
    var invoice = TestInvoices.invoice(InvoiceTypeCode.CODE_380,
                                       "R-1");
    var payable = invoice.totals()
                         .payableAmount()
                         .toPlainString();
    var xml = PANIOLO.toXml(invoice,
                            InvoiceSyntax.CII)
                     .replace("<ram:DuePayableAmount>" + payable + "<",
                              "<ram:DuePayableAmount>9999.99<");
    assertThrows(IllegalArgumentException.class,
                 () -> PANIOLO.read(xml));
    var result = PANIOLO.read(xml.getBytes(StandardCharsets.UTF_8),
                              ReadMode.LENIENT);
    assertEquals(invoice,
                 result.invoice());
    assertEquals(ReadWarning.PAYABLE_AMOUNT,
                 result.warnings()
                       .get(0)
                       .code());
    assertTrue(PANIOLO.read(PANIOLO.toXml(invoice,
                                          InvoiceSyntax.UBL),
                            ReadMode.LENIENT)
                      .isExact());
  }

  @Test
  void importsReceivedInvoiceWithOriginalAndStatedAmounts() {
    var invoice = TestInvoices.invoice(InvoiceTypeCode.CODE_380,
                                       "R-1");
    var payable = invoice.totals()
                         .payableAmount()
                         .toPlainString();
    var xml = PANIOLO.toXml(invoice,
                            InvoiceSyntax.CII)
                     .replace("<ram:DuePayableAmount>" + payable + "<",
                              "<ram:DuePayableAmount>9999.99<")
                     .getBytes(StandardCharsets.UTF_8);
    assertThrows(IllegalArgumentException.class,
                 () -> PANIOLO.importInvoice(xml,
                                             ReadMode.STRICT));
    var result = PANIOLO.importInvoice(xml,
                                       ReadMode.LENIENT);
    assertEquals(invoice,
                 result.invoice());
    assertTrue(result.isNormalized());
    assertEquals("9999.99",
                 result.stated()
                       .payableAmount()
                       .toPlainString());
    assertEquals("BT-115",
                 result.deviations()
                       .get(0)
                       .businessTerm());
    assertArrayEquals(xml,
                      PANIOLO.export(result,
                                     ExportVariant.ORIGINAL,
                                     null));
    assertArrayEquals(xml,
                      PANIOLO.export(result,
                                     ExportVariant.ORIGINAL,
                                     InvoiceSyntax.CII));
    // The original is never converted silently.
    assertThrows(IllegalArgumentException.class,
                 () -> PANIOLO.export(result,
                                      ExportVariant.ORIGINAL,
                                      InvoiceSyntax.UBL));
    assertThrows(NullPointerException.class,
                 () -> PANIOLO.export(result,
                                      ExportVariant.NORMALIZED,
                                      null));
    var ubl = PANIOLO.export(result,
                             ExportVariant.NORMALIZED,
                             InvoiceSyntax.UBL);
    assertEquals(invoice,
                 PANIOLO.read(ubl));
  }

  @Test
  void rejectsUnknownAndMalformedDocuments() {
    assertThrows(IllegalArgumentException.class,
                 () -> PANIOLO.detect("<Order xmlns=\"urn:example\"/>"));
    assertThrows(IllegalArgumentException.class,
                 () -> PANIOLO.read("<Invoice/>"));
    assertThrows(IllegalArgumentException.class,
                 () -> PANIOLO.validate("<unclosed>"));
    assertThrows(IllegalArgumentException.class,
                 () -> PANIOLO.read("<!DOCTYPE x [<!ENTITY e SYSTEM \"file:///etc/passwd\">]><x>&e;</x>"));
    assertThrows(NullPointerException.class,
                 () -> PANIOLO.toXml(TestInvoices.invoice(InvoiceTypeCode.CODE_380,
                                                          "R-1"),
                                     null));
  }

  @Test
  void reportsMalformedXmlAsFindingWhenTypeIsGiven() {
    var result = PANIOLO.validate("<unclosed>".getBytes(),
                                  ValidationProfile.Syntax.CII);
    assertFalse(result.isValid());
  }

  @Test
  void usesConfiguredRuleBundle(
      @TempDir Path temp) {
    var paniolo = Paniolo.builder()
                         .rules(XrechnungRules.bundle(temp.resolve("rules")))
                         .build();
    var invoice = TestInvoices.invoice(InvoiceTypeCode.CODE_380,
                                       "R-1");
    var result = paniolo.validate(invoice,
                                  InvoiceSyntax.UBL);
    assertTrue(result.isValid());
    // The duration covers writing and validating, not only the validator.
    assertTrue(result.duration() != null &&
               result.toXml()
                     .contains("duration=\"PT"),
               result::toXml);
    var missing = Paniolo.builder()
                         .rules(temp.resolve("missing"))
                         .build();
    assertThrows(RuntimeException.class,
                 () -> missing.validate(invoice,
                                        InvoiceSyntax.UBL));
  }

  @Test
  void warmUpCompilesOnceAndReturnsTheInstance() {
    var paniolo = Paniolo.create();

    assertSame(paniolo,
               paniolo.warmUp());
    assertSame(paniolo,
               paniolo.warmUp());
    for (var syntax : InvoiceSyntax.values()) {
      for (var type : new InvoiceTypeCode[] { InvoiceTypeCode.CODE_380,
                                              InvoiceTypeCode.CODE_381 }) {
        var result = paniolo.validate(paniolo.toXml(TestInvoices.invoice(type,
                                                                         "R-1"),
                                                    syntax));
        assertTrue(result.isValid(),
                   () -> result.messages()
                               .toString());
      }
    }
  }

  @Test
  void oneInstanceServesConcurrentRequests()
      throws Exception {
    var paniolo = Paniolo.create();
    var tasks   = new ArrayList<Callable<Boolean>>();
    for (int i = 0; i < 48; i++) {
      var syntax = InvoiceSyntax.values()[i % 2];
      var type = i % 3 == 0 ?
                 InvoiceTypeCode.CODE_381 :
                 InvoiceTypeCode.CODE_380;
      var invoice = TestInvoices.invoice(type,
                                         "R-" + i);
      tasks.add(() -> {
        String xml = paniolo.toXml(invoice,
                                   syntax);
        return paniolo.validate(xml)
                      .isValid() &&
               paniolo.read(xml)
                      .equals(invoice);
      });
    }
    var executor = Executors.newFixedThreadPool(8);
    try {
      for (var future : executor.invokeAll(tasks)) {
        assertTrue(future.get());
      }
    } finally {
      executor.shutdown();
      assertTrue(executor.awaitTermination(30,
                                           TimeUnit.SECONDS));
    }
  }
}

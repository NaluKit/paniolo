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
import io.github.nalukit.paniolo.model.common.ProcessControl;
import io.github.nalukit.paniolo.model.common.TaxCategory;
import io.github.nalukit.paniolo.model.common.value.InvoiceTypeCode;
import io.github.nalukit.paniolo.validation.Severity;
import io.github.nalukit.paniolo.validation.ValidationMessage;
import io.github.nalukit.paniolo.validation.ValidationResult;
import io.github.nalukit.paniolo.xml.ExportVariant;
import io.github.nalukit.paniolo.xml.ReadMode;
import io.github.nalukit.paniolo.xml.ReadWarning;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Negative item prices (task V03, Mustang issues #1285, #1262 and #641). BR-27 (net price) and BR-28
 * (gross price) are rules of EN 16931; the Factur-X profiles EXTENDED and EXTENDED-CTC-FR do not have
 * them, so a negative price is valid there. The model checks both rules for every other profile.
 *
 * <p>The invoice has line 1 with 2 x 100.00 and line 2 with 3 x -100.00, both at 19 %: net -100.00,
 * VAT -19.00, amount due -119.00.
 */
class NegativePriceTest {
  private static final Paniolo PANIOLO = Paniolo.create();

  private static final String NET_RULE   = "Line 2: netUnitPrice must not be negative (BR-27); only the Factur-X profiles EXTENDED and EXTENDED-CTC-FR allow negative prices";
  private static final String GROSS_RULE = "Line 2: grossPrice must not be negative (BR-28); only the Factur-X profiles EXTENDED and EXTENDED-CTC-FR allow negative prices";

  private enum Price {
    NET,
    GROSS
  }

  private static Invoice.Builder builder(ProcessControl processControl,
                                         Price price) {
    var line = InvoiceLine.create()
                          .id("2")
                          .name("Gutschrift Beratung")
                          .quantity(new BigDecimal("3"))
                          .unitCode("HUR")
                          .netUnitPrice(new BigDecimal("-100.00"))
                          .tax(TaxCategory.standard(new BigDecimal("19")));
    if (price == Price.GROSS) {
      // -100.00 = -90.00 - 10.00 (BT-146 = BT-148 - BT-147)
      line.grossPrice(new BigDecimal("-90.00"))
          .priceDiscount(new BigDecimal("10.00"));
    }
    return TestInvoices.builder(InvoiceTypeCode.CODE_380,
                                "NEG-1")
                       .processControl(processControl)
                       .addLine(line.build());
  }

  private static List<ValidationMessage> errors(ValidationResult result) {
    return result.messages()
                 .stream()
                 .filter(message -> message.severity() == Severity.ERROR || message.severity() == Severity.FATAL)
                 .toList();
  }

  private static List<String> ruleIds(ValidationResult result) {
    return result.messages()
                 .stream()
                 .map(ValidationMessage::ruleId)
                 .toList();
  }

  @ParameterizedTest
  @EnumSource(Price.class)
  void readsAndWritesNegativePricesInExtended(Price price) {
    var invoice = builder(ProcessControl.facturXExtended(),
                          price).build();
    byte[] xml = PANIOLO.toBytes(invoice,
                                 InvoiceSyntax.CII);
    var validation = PANIOLO.validate(xml,
                                      FacturXProfile.EXTENDED);
    assertEquals(List.of(),
                 errors(validation));
    assertTrue(validation.isValid(),
               validation::toString);
    for (var mode : ReadMode.values()) {
      var imported = PANIOLO.importInvoice(xml,
                                           mode);
      assertEquals(List.of(),
                   imported.warnings(),
                   mode::toString);
      assertEquals(List.of(),
                   imported.deviations(),
                   mode::toString);
      var line = imported.invoice()
                         .lines()
                         .get(1);
      assertEquals(0,
                   new BigDecimal("-100.00").compareTo(line.netUnitPrice()));
      assertEquals(0,
                   new BigDecimal("-300.00").compareTo(line.netAmount()));
      if (price == Price.GROSS) {
        assertEquals(0,
                     new BigDecimal("-90.00").compareTo(line.grossPrice()));
      }
      var totals = imported.invoice()
                           .totals();
      assertEquals(0,
                   new BigDecimal("-100.00").compareTo(totals.taxExclusiveAmount()));
      assertEquals(0,
                   new BigDecimal("-19.00").compareTo(totals.taxAmount()));
      assertEquals(0,
                   new BigDecimal("-119.00").compareTo(totals.payableAmount()));
      assertArrayEquals(xml,
                        PANIOLO.export(imported,
                                       ExportVariant.ORIGINAL,
                                       InvoiceSyntax.CII));
      byte[] normalized = PANIOLO.export(imported,
                                         ExportVariant.NORMALIZED,
                                         InvoiceSyntax.CII);
      assertArrayEquals(xml,
                        normalized,
                        mode::toString);
    }
  }

  @Test
  void acceptsNegativePricesInExtendedCtcFr() {
    var invoice = builder(ProcessControl.facturXExtendedCtcFr("B1"),
                          Price.GROSS).build();
    assertEquals(0,
                 new BigDecimal("-90.00").compareTo(invoice.lines()
                                                           .get(1)
                                                           .grossPrice()));
  }

  @Test
  void rejectsNegativePricesOutsideExtended() {
    for (var processControl : List.of(ProcessControl.xrechnung(),
                                      ProcessControl.en16931(),
                                      ProcessControl.facturXBasic())) {
      var net = assertThrows(IllegalArgumentException.class,
                             () -> builder(processControl,
                                           Price.NET).build());
      assertEquals(NET_RULE,
                   net.getMessage(),
                   processControl::toString);
    }
    // A positive net price leaves the negative gross price.
    var onlyGross = InvoiceLine.create()
                               .id("2")
                               .name("Rabattposition")
                               .quantity(BigDecimal.ONE)
                               .unitCode("C62")
                               .netUnitPrice(BigDecimal.ZERO)
                               .grossPrice(new BigDecimal("-10.00"))
                               .tax(TaxCategory.standard(new BigDecimal("19")))
                               .build();
    var error = assertThrows(IllegalArgumentException.class,
                             () -> TestInvoices.builder(InvoiceTypeCode.CODE_380,
                                                        "NEG-2")
                                               .addLine(onlyGross)
                                               .build());
    assertEquals(GROSS_RULE,
                 error.getMessage());
  }

  @Test
  void keepsTheRuleWhenAnExtendedInvoiceChangesItsProfile() {
    var extended = builder(ProcessControl.facturXExtended(),
                           Price.NET);
    assertNotNull(extended.build());
    var error = assertThrows(IllegalArgumentException.class,
                             () -> extended.processControl(ProcessControl.xrechnung())
                                           .build());
    assertEquals(NET_RULE,
                 error.getMessage());
  }

  @Test
  void officialRulesAndReadersReportBr27OutsideExtended() {
    String extended = PANIOLO.toXml(builder(ProcessControl.facturXExtended(),
                                            Price.NET).build(),
                                    InvoiceSyntax.CII);
    String en16931 = extended.replace(ProcessControl.facturXExtended()
                                                    .specificationId(),
                                      ProcessControl.en16931()
                                                    .specificationId());
    byte[] xml = en16931.getBytes(StandardCharsets.UTF_8);
    var validation = PANIOLO.validate(xml,
                                      FacturXProfile.EN16931);
    assertTrue(ruleIds(validation).contains("BR-27"),
               validation::toString);
    for (var mode : ReadMode.values()) {
      var error = assertThrows(IllegalArgumentException.class,
                               () -> PANIOLO.read(xml,
                                                  mode));
      assertEquals(NET_RULE,
                   error.getMessage(),
                   mode::toString);
    }
  }

  @Test
  void lenientReadingRecalculatesANegativePriceInExtendedOnly() {
    // Line 2 states the net price 100.00 but the amount -300.00: lenient reading takes the amount.
    String extended = PANIOLO.toXml(builder(ProcessControl.facturXExtended(),
                                            Price.NET).build(),
                                    InvoiceSyntax.CII);
    assertTrue(extended.contains("<ram:ChargeAmount>-100.00</ram:ChargeAmount>"),
               extended);
    String stated = extended.replace("<ram:ChargeAmount>-100.00</ram:ChargeAmount>",
                                     "<ram:ChargeAmount>100.00</ram:ChargeAmount>");
    var lenient = PANIOLO.read(stated,
                               ReadMode.LENIENT);
    assertEquals(List.of(ReadWarning.LINE_NET_PRICE),
                 lenient.warnings()
                        .stream()
                        .map(ReadWarning::code)
                        .toList());
    assertEquals(0,
                 new BigDecimal("-100.00").compareTo(lenient.invoice()
                                                            .lines()
                                                            .get(1)
                                                            .netUnitPrice()));
    assertThrows(IllegalArgumentException.class,
                 () -> PANIOLO.read(stated,
                                    ReadMode.STRICT));
    // The same document outside EXTENDED cannot take a negative price.
    String en16931 = stated.replace(ProcessControl.facturXExtended()
                                                  .specificationId(),
                                    ProcessControl.en16931()
                                                  .specificationId());
    var error = assertThrows(IllegalArgumentException.class,
                             () -> PANIOLO.read(en16931,
                                                ReadMode.LENIENT));
    assertNotNull(error.getMessage());
    assertEquals(NET_RULE,
                 error.getMessage());
  }
}

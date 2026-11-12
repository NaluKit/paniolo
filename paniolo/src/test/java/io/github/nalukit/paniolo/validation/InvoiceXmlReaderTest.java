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

import io.github.nalukit.paniolo.model.Invoice;
import io.github.nalukit.paniolo.model.common.DocumentAdjustment;
import io.github.nalukit.paniolo.model.common.InvoiceLine;
import io.github.nalukit.paniolo.model.common.TaxCategory;
import io.github.nalukit.paniolo.model.common.value.InvoiceTypeCode;
import io.github.nalukit.paniolo.xml.ExportVariant;
import io.github.nalukit.paniolo.xml.ImportResult;
import io.github.nalukit.paniolo.xml.InvoiceXmlReader;
import io.github.nalukit.paniolo.xml.InvoiceXmlWriter;
import io.github.nalukit.paniolo.xml.ReadMode;
import io.github.nalukit.paniolo.xml.ReadWarning;
import io.github.nalukit.paniolo.xml.cii.CiiInvoiceReader;
import io.github.nalukit.paniolo.xml.cii.CiiInvoiceWriter;
import io.github.nalukit.paniolo.xml.ubl.UblInvoiceReader;
import io.github.nalukit.paniolo.xml.ubl.UblInvoiceWriter;
import io.github.nalukit.paniolo.xml.util.ReadContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InvoiceXmlReaderTest {
  static Stream<Arguments> roundTrips() {
    var syntaxes = List.of(new Syntax("UBL",
                                      new UblInvoiceWriter(),
                                      new UblInvoiceReader()),
                           new Syntax("CII",
                                      new CiiInvoiceWriter(),
                                      new CiiInvoiceReader()));
    var samples = List.<UnaryOperator<Invoice.Builder>>of(InvoiceSamples::basic,
                                                          InvoiceSamples::exempt,
                                                          InvoiceSamples::outsideScope,
                                                          InvoiceSamples::documentLevelTerms,
                                                          InvoiceSamples::directDebit,
                                                          InvoiceSamples::paymentCardAndTaxPointDateCode);
    var names = List.of("basic",
                        "exempt",
                        "outsideScope",
                        "documentLevelTerms",
                        "directDebit",
                        "paymentCard");
    var result = Stream.<Arguments>builder();
    for (var syntax : syntaxes) {
      for (var type : List.of(InvoiceTypeCode.CODE_380,
                              InvoiceTypeCode.CODE_381)) {
        for (int i = 0; i < samples.size(); i++) {
          var invoice = samples.get(i)
                               .apply(InvoiceSamples.invoice()
                                                    .typeCode(type))
                               .build();
          result.add(Arguments.of(syntax,
                                  type.getCode() + " " + names.get(i),
                                  invoice));
        }
      }
    }
    return result.build();
  }

  @ParameterizedTest(name = "{0} {1}")
  @MethodSource("roundTrips")
  void readsWrittenInvoiceBackUnchanged(Syntax syntax,
                                        String sample,
                                        Invoice invoice) {
    var xml = syntax.writer()
                    .toXml(invoice);
    assertEquals(invoice,
                 syntax.reader()
                       .fromXml(xml),
                 xml);
    assertEquals(invoice,
                 syntax.reader()
                       .fromStream(new ByteArrayInputStream(syntax.writer()
                                                                  .toBytes(invoice))));
    var lenient = syntax.reader()
                        .fromXml(xml,
                                 ReadMode.LENIENT);
    assertEquals(invoice,
                 lenient.invoice());
    assertTrue(lenient.isExact());
  }

  @Test
  void rejectsDocumentsWhoseTotalsTheModelCannotReproduce() {
    var invoice = InvoiceSamples.basic(InvoiceSamples.invoice())
                                .build();
    // Simulates content outside the model, e.g. a line allowance, that changes the amount due.
    var payable = ">" +
                  invoice.totals()
                         .payableAmount()
                         .toPlainString() +
                  "<";
    var ubl = new UblInvoiceWriter().toXml(invoice)
                                    .replace(payable,
                                             ">9999.99<");
    var cii = new CiiInvoiceWriter().toXml(invoice)
                                    .replace(payable,
                                             ">9999.99<");
    assertNotEquals(new UblInvoiceWriter().toXml(invoice),
                    ubl);
    assertThrows(IllegalArgumentException.class,
                 () -> new UblInvoiceReader().fromXml(ubl));
    assertThrows(IllegalArgumentException.class,
                 () -> new CiiInvoiceReader().fromXml(cii));
    for (var result : List.of(new UblInvoiceReader().fromXml(ubl,
                                                             ReadMode.LENIENT),
                              new CiiInvoiceReader().fromXml(cii,
                                                             ReadMode.LENIENT))) {
      assertEquals(invoice,
                   result.invoice());
      assertEquals(List.of(ReadWarning.PAYABLE_AMOUNT),
                   result.warnings()
                         .stream()
                         .map(ReadWarning::code)
                         .toList());
    }
  }

  @Test
  void recalculatesNetPriceFromStatedLineAmountWhenLenient() {
    var invoice = InvoiceSamples.basic(InvoiceSamples.invoice())
                                .build();
    // Line 1 states 210.00 for 2 x 100.00, e.g. because of an insurance surcharge outside the model.
    var ubl = new UblInvoiceWriter().toXml(invoice)
                                    .replace("<cbc:LineExtensionAmount currencyID=\"EUR\">200.00<",
                                             "<cbc:LineExtensionAmount currencyID=\"EUR\">210.00<");
    var cii = new CiiInvoiceWriter().toXml(invoice)
                                    .replace("<ram:LineTotalAmount>200.00<",
                                             "<ram:LineTotalAmount>210.00<");
    assertNotEquals(new UblInvoiceWriter().toXml(invoice),
                    ubl);
    assertNotEquals(new CiiInvoiceWriter().toXml(invoice),
                    cii);
    assertThrows(IllegalArgumentException.class,
                 () -> new UblInvoiceReader().fromXml(ubl));
    assertThrows(IllegalArgumentException.class,
                 () -> new CiiInvoiceReader().fromXml(cii));
    for (var result : List.of(new UblInvoiceReader().fromXml(ubl,
                                                             ReadMode.LENIENT),
                              new CiiInvoiceReader().fromXml(cii,
                                                             ReadMode.LENIENT))) {
      var line = result.invoice()
                       .lines()
                       .get(0);
      assertEquals(new BigDecimal("105.00"),
                   line.netUnitPrice());
      assertEquals(new BigDecimal("210.00"),
                   line.netAmount());
      var warning = result.warnings()
                          .get(0);
      assertEquals(ReadWarning.LINE_NET_PRICE,
                   warning.code());
      assertEquals("1",
                   warning.lineId());
      // The stated totals still refer to the original amounts.
      assertTrue(result.warnings()
                       .stream()
                       .anyMatch(w -> ReadWarning.PAYABLE_AMOUNT.equals(w.code())));
    }
  }

  @Test
  void importKeepsStatedAmountsAndOriginalXml() {
    var invoice = InvoiceSamples.basic(InvoiceSamples.invoice())
                                .build();
    var totals = invoice.totals();
    var payable = ">" +
                  totals.payableAmount()
                        .toPlainString() +
                  "<";
    // Line 1 states 210.00 for 2 x 100.00 and the document an amount due the model cannot reproduce.
    var ubl = new UblInvoiceWriter().toXml(invoice)
                                    .replace("<cbc:LineExtensionAmount currencyID=\"EUR\">200.00<",
                                             "<cbc:LineExtensionAmount currencyID=\"EUR\">210.00<")
                                    .replace(payable,
                                             ">9999.99<");
    var cii = new CiiInvoiceWriter().toXml(invoice)
                                    .replace("<ram:LineTotalAmount>200.00<",
                                             "<ram:LineTotalAmount>210.00<")
                                    .replace(payable,
                                             ">9999.99<");
    for (var test : List.of(Arguments.of(ubl,
                                         new UblInvoiceReader(),
                                         new UblInvoiceWriter()),
                            Arguments.of(cii,
                                         new CiiInvoiceReader(),
                                         new CiiInvoiceWriter()))) {
      var    xml    = ((String) test.get()[0]).getBytes(StandardCharsets.UTF_8);
      var    reader = (InvoiceXmlReader) test.get()[1];
      var    writer = (InvoiceXmlWriter) test.get()[2];
      assertThrows(IllegalArgumentException.class,
                   () -> reader.importXml(xml,
                                          ReadMode.STRICT));
      var result = reader.importXml(xml,
                                    ReadMode.LENIENT);
      assertEquals(ReadMode.LENIENT,
                   result.mode());
      assertTrue(result.isNormalized());

      // The model is normalized: the net price explains the stated line amount.
      var line = result.invoice()
                       .lines()
                       .get(0);
      assertEquals(0,
                   new BigDecimal("105.00").compareTo(line.netUnitPrice()));
      // The stated amounts are kept as they are.
      var stated = result.stated();
      assertEquals("1",
                   stated.lines()
                         .get(0)
                         .id());
      assertEquals(0,
                   new BigDecimal("100.00").compareTo(stated.lines()
                                                            .get(0)
                                                            .netPrice()));
      assertEquals(0,
                   new BigDecimal("210.00").compareTo(stated.lines()
                                                            .get(0)
                                                            .netAmount()));
      assertEquals(0,
                   totals.lineTotal()
                         .compareTo(stated.lineTotal()));
      assertEquals(0,
                   totals.taxAmount()
                         .compareTo(stated.taxAmount()));
      assertEquals(0,
                   new BigDecimal("9999.99").compareTo(stated.payableAmount()));
      // Each difference between document and model is reported with both values.
      var deviations = result.deviations();
      assertTrue(deviations.stream()
                           .anyMatch(d -> d.businessTerm()
                                           .equals("BT-146") &&
                                          "1".equals(d.lineId()) &&
                                          d.stated()
                                           .compareTo(new BigDecimal("100.00")) == 0 &&
                                          d.calculated()
                                           .compareTo(new BigDecimal("105.00")) == 0),
                 deviations::toString);
      assertTrue(deviations.stream()
                           .anyMatch(d -> d.businessTerm()
                                           .equals("BT-106") &&
                                          d.lineId() == null &&
                                          d.stated()
                                           .compareTo(totals.lineTotal()) == 0),
                 deviations::toString);
      assertTrue(deviations.stream()
                           .anyMatch(d -> d.businessTerm()
                                           .equals("BT-115") &&
                                          d.stated()
                                           .compareTo(new BigDecimal("9999.99")) == 0),
                 deviations::toString);
      assertTrue(deviations.stream()
                           .noneMatch(d -> d.businessTerm()
                                            .equals("BT-131")),
                 deviations::toString);

      // The original stays available unchanged; the normalized export is an explicit choice.
      assertArrayEquals(xml,
                        result.originalXml());
      result.originalXml()[0] = 0;
      assertArrayEquals(xml,
                        result.originalXml());
      assertArrayEquals(xml,
                        result.export(ExportVariant.ORIGINAL,
                                      null));
      var normalized = result.export(ExportVariant.NORMALIZED,
                                     writer);
      assertNotEquals(new String(xml,
                                 StandardCharsets.UTF_8),
                      new String(normalized,
                                 StandardCharsets.UTF_8));
      var reread = reader.importXml(normalized,
                                    ReadMode.STRICT);
      assertEquals(result.invoice(),
                   reread.invoice());
      assertFalse(reread.isNormalized());
      assertThrows(NullPointerException.class,
                   () -> result.export(ExportVariant.NORMALIZED,
                                       null));
    }
  }

  @Test
  void importKeepsItsOwnSnapshotOfTheXml() {
    var invoice = InvoiceSamples.basic(InvoiceSamples.invoice())
                                .build();
    var    reader   = new CiiInvoiceReader();
    byte[] expected = new CiiInvoiceWriter().toBytes(invoice);
    byte[] input    = expected.clone();
    var    result   = reader.importXml(input,
                                       ReadMode.STRICT);
    // Changing the input afterwards changes neither the kept original nor the invoice.
    Arrays.fill(input,
                          (byte) ' ');
    assertArrayEquals(expected,
                      result.originalXml());
    assertArrayEquals(expected,
                      result.export(ExportVariant.ORIGINAL,
                                    null));
    assertEquals(invoice,
                 result.invoice());
    // Arrays handed out are copies.
    result.export(ExportVariant.ORIGINAL,
                  null)[0] = 0;
    result.originalXml()[0] = 0;
    assertArrayEquals(expected,
                      result.originalXml());

    // The public constructor still copies the array it is given.
    byte[] given       = expected.clone();
    var    read        = reader.fromBytes(expected,
                                          ReadMode.STRICT);
    var    constructed = new ImportResult(given,
                                          ReadMode.STRICT,
                                          read);
    given[0] = 0;
    assertArrayEquals(expected,
                      constructed.originalXml());
    assertEquals(result,
                 constructed);
    assertThrows(NullPointerException.class,
                 () -> new ImportResult(null,
                                        ReadMode.STRICT,
                                        read));
  }

  @Test
  void importOfExactDocumentHasNoDeviations() {
    var invoice = InvoiceSamples.basic(InvoiceSamples.invoice())
                                .build();
    for (var syntax : List.of(new UblInvoiceWriter(),
                              new CiiInvoiceWriter())) {
      var reader = syntax instanceof UblInvoiceWriter ?
                   (InvoiceXmlReader) new UblInvoiceReader() :
                   new CiiInvoiceReader();
      var result = reader.importXml(syntax.toBytes(invoice),
                                    ReadMode.STRICT);
      assertEquals(invoice,
                   result.invoice());
      assertFalse(result.isNormalized());
      assertEquals(List.of(),
                   result.deviations());
      assertEquals(2,
                   result.stated()
                         .lines()
                         .size());
      assertEquals(0,
                   invoice.totals()
                          .payableAmount()
                          .compareTo(result.stated()
                                           .payableAmount()));
    }
  }

  @Test
  void readsCorrectionsWithNegativePercentagesAndPaidAmounts() {
    // As in the official ZUGFeRD sample of a corrective invoice: -2 % of a negative base.
    var invoice = InvoiceSamples.basic(InvoiceSamples.invoice())
                                .addAdjustment(DocumentAdjustment.createAllowance()
                                                                 .amount(new BigDecimal("-0.10"))
                                                                 .baseAmount(new BigDecimal("-5.00"))
                                                                 .percentage(new BigDecimal("-2.00"))
                                                                 .reason("Rechnungsrabatt")
                                                                 .tax(TaxCategory.standard(new BigDecimal("19")))
                                                                 .build())
                                .paidAmount(new BigDecimal("-20.00"))
                                .build();
    assertEquals(invoice.totals()
                        .taxInclusiveAmount()
                        .add(new BigDecimal("20.00")),
                 invoice.totals()
                        .payableAmount());
    assertEquals(invoice,
                 new UblInvoiceReader().fromXml(new UblInvoiceWriter().toXml(invoice)));
    assertEquals(invoice,
                 new CiiInvoiceReader().fromXml(new CiiInvoiceWriter().toXml(invoice)));
  }

  @Test
  void dropsUnknownAllowanceReasonCodesWhenLenient() {
    var invoice = InvoiceSamples.basic(InvoiceSamples.invoice())
                                .addAdjustment(DocumentAdjustment.createAllowance()
                                                                 .amount(new BigDecimal("5.00"))
                                                                 .reason("Stammkundenrabatt")
                                                                 .reasonCode("95")
                                                                 .tax(TaxCategory.standard(new BigDecimal("19")))
                                                                 .build())
                                .build();
    // Older samples use the charge code ABK (UNTDID 7161) for allowances.
    var ubl = new UblInvoiceWriter().toXml(invoice)
                                    .replace("<cbc:AllowanceChargeReasonCode>95<",
                                             "<cbc:AllowanceChargeReasonCode>ABK<");
    var cii = new CiiInvoiceWriter().toXml(invoice)
                                    .replace("<ram:ReasonCode>95<",
                                             "<ram:ReasonCode>ABK<");
    assertNotEquals(new UblInvoiceWriter().toXml(invoice),
                    ubl);
    assertNotEquals(new CiiInvoiceWriter().toXml(invoice),
                    cii);
    for (InvoiceXmlReader reader : List.of(new UblInvoiceReader(),
                                           new CiiInvoiceReader())) {
      String xml = reader instanceof UblInvoiceReader ?
                   ubl :
                   cii;
      var strict = assertThrows(IllegalArgumentException.class,
                                () -> reader.fromXml(xml));
      assertTrue(strict.getMessage()
                       .contains("ABK"),
                 strict::getMessage);
      var result = reader.fromXml(xml,
                                  ReadMode.LENIENT);
      var adjustment = result.invoice()
                             .adjustments()
                             .stream()
                             .filter(candidate -> "Stammkundenrabatt".equals(candidate.reason()))
                             .findFirst()
                             .orElseThrow();
      assertNull(adjustment.reasonCode());
      assertEquals("Stammkundenrabatt",
                   adjustment.reason());
      assertEquals(List.of(ReadWarning.REASON_CODE),
                   result.warnings()
                         .stream()
                         .map(ReadWarning::code)
                         .toList());
      // Without a reason text the allowance cannot be kept.
      String withoutReason = xml.replace("Stammkundenrabatt",
                                         "");
      assertThrows(IllegalArgumentException.class,
                   () -> reader.fromXml(withoutReason,
                                        ReadMode.LENIENT));
    }
  }

  @Test
  void keepsGrossPriceConsistentWithRecalculatedNetPrice() {
    var line = InvoiceLine.create()
                          .id("1")
                          .name("Schraube")
                          .quantity(new BigDecimal("4"))
                          .unitCode("H87")
                          .grossPrice(new BigDecimal("12.00"))
                          .priceDiscount(new BigDecimal("2.00"))
                          .netUnitPrice(new BigDecimal("10.00"))
                          .tax(InvoiceSamples.standard())
                          .build();
    var lower = new ReadContext(ReadMode.LENIENT).netAmount(line,
                                                            new BigDecimal("38.00"));
    assertEquals(new BigDecimal("9.50"),
                 lower.netUnitPrice());
    assertEquals(new BigDecimal("2.50"),
                 lower.priceDiscount());
    var higher = new ReadContext(ReadMode.LENIENT).netAmount(line,
                                                             new BigDecimal("50.00"));
    assertEquals(new BigDecimal("12.50"),
                 higher.netUnitPrice());
    assertNull(higher.grossPrice());
    assertNull(higher.priceDiscount());
    // A negative price is up to the invoice: only EXTENDED allows it (BR-27, see NegativePriceTest).
    var negative = new ReadContext(ReadMode.LENIENT).netAmount(line,
                                                               new BigDecimal("-5.00"));
    assertEquals(new BigDecimal("-1.25"),
                 negative.netUnitPrice());
    assertEquals(new BigDecimal("13.25"),
                 negative.priceDiscount());
    assertThrows(IllegalArgumentException.class,
                 () -> new ReadContext(ReadMode.STRICT).netAmount(line,
                                                                  new BigDecimal("38.00")));
  }

  @Test
  void rejectsForeignAndMalformedDocuments() {
    var cii = new CiiInvoiceWriter().toXml(InvoiceSamples.basic(InvoiceSamples.invoice())
                                                         .build());
    assertThrows(IllegalArgumentException.class,
                 () -> new UblInvoiceReader().fromXml(cii));
    assertThrows(IllegalArgumentException.class,
                 () -> new CiiInvoiceReader().fromXml("<Invoice/>"));
    assertThrows(IllegalArgumentException.class,
                 () -> new CiiInvoiceReader().fromXml("<unclosed>"));
    assertThrows(IllegalArgumentException.class,
                 () -> new UblInvoiceReader().fromXml("<!DOCTYPE x [<!ENTITY e SYSTEM \"file:///etc/passwd\">]><x>&e;</x>"));
  }

  record Syntax(String name, InvoiceXmlWriter writer, InvoiceXmlReader reader) {
    @Override
    public String toString() {
      return name;
    }
  }
}

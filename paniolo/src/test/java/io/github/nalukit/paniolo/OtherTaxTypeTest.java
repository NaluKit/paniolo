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
import io.github.nalukit.paniolo.model.common.InvoiceLine;
import io.github.nalukit.paniolo.model.common.ProcessControl;
import io.github.nalukit.paniolo.model.common.TaxCategory;
import io.github.nalukit.paniolo.model.common.value.DutyTaxFeeCategory;
import io.github.nalukit.paniolo.model.common.value.InvoiceTypeCode;
import io.github.nalukit.paniolo.validation.Severity;
import io.github.nalukit.paniolo.validation.ValidationMessage;
import io.github.nalukit.paniolo.validation.ValidationResult;
import io.github.nalukit.paniolo.xml.ExportVariant;
import io.github.nalukit.paniolo.xml.ReadMode;
import io.github.nalukit.paniolo.xml.ReadWarning;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Taxes of another type than VAT, e.g. the insurance tax AAM that Factur-X EXTENDED allows next to
 * VAT (Mustang issue #1342, task V01). The model keeps VAT only: strict reading rejects such a tax,
 * lenient reading reports it, the calculation check names the totals it cannot check instead of
 * reporting them as wrong, and a normalized export requires an explicit decision to drop it.
 *
 * <p>The cases are derived from a Paniolo invoice: line 1 standard rated (200.00, VAT 38.00), line 2
 * exempt from VAT (100.00) with an insurance tax AAM of 19 % (19.00). The total tax amount is 57.00,
 * the amount due 357.00. The control case has no AAM: total tax amount 38.00, amount due 338.00.
 */
class OtherTaxTypeTest {
  private static final Paniolo PANIOLO = Paniolo.builder()
                                                .calculationCheck(true)
                                                .build();
  private static final Paniolo PLAIN   = Paniolo.create();

  private static final String EXEMPTION = "Steuerbefreit gemäß § 4 Nr. 10 Buchst. a UStG";
  private static final String LINE_AAM  = "<ram:ApplicableTradeTax><ram:CalculatedAmount>19.00</ram:CalculatedAmount><ram:TypeCode>AAM</ram:TypeCode>" +
                                          "<ram:CategoryCode>S</ram:CategoryCode><ram:RateApplicablePercent>19.00</ram:RateApplicablePercent></ram:ApplicableTradeTax>";
  private static final String HEAD_AAM  = "<ram:ApplicableTradeTax><ram:CalculatedAmount>19.00</ram:CalculatedAmount><ram:TypeCode>AAM</ram:TypeCode>" +
                                          "<ram:BasisAmount>100.00</ram:BasisAmount><ram:CategoryCode>S</ram:CategoryCode>" +
                                          "<ram:RateApplicablePercent>19.00</ram:RateApplicablePercent></ram:ApplicableTradeTax>";
  private static final String TAX       = "<ram:ApplicableTradeTax>";
  private static final String TAX_END   = "</ram:ApplicableTradeTax>";

  private static final TaxCategory EXEMPT = new TaxCategory(DutyTaxFeeCategory.E,
                                                            BigDecimal.ZERO,
                                                            EXEMPTION,
                                                            null);

  /**
   * The VAT only control case.
   */
  private static String control() {
    var invoice = TestInvoices.builder(InvoiceTypeCode.CODE_380,
                                       "AAM-1")
                              .processControl(ProcessControl.facturXExtended())
                              .addLine(InvoiceLine.create()
                                                  .id("2")
                                                  .name("Versicherungsprämie")
                                                  .quantity(BigDecimal.ONE)
                                                  .unitCode("C62")
                                                  .netUnitPrice(new BigDecimal("100.00"))
                                                  .tax(EXEMPT)
                                                  .build())
                              .build();
    return PLAIN.toXml(invoice,
                       InvoiceSyntax.CII);
  }

  /**
   * The control case with AAM on line 2 and in the header, after or before the VAT block.
   */
  private static String withAam(boolean aamFirst) {
    String xml   = control();
    int    line2 = xml.indexOf("<ram:IncludedSupplyChainTradeLineItem>",
                               xml.indexOf("<ram:IncludedSupplyChainTradeLineItem>") + 1);
    int    lineTax = aamFirst ?
                     xml.indexOf(TAX,
                                 line2) :
                     xml.indexOf(TAX_END,
                                 line2) + TAX_END.length();
    xml = xml.substring(0,
                        lineTax) + LINE_AAM + xml.substring(lineTax);
    int headerTax = aamFirst ?
                    xml.indexOf(TAX,
                                xml.indexOf("<ram:ApplicableHeaderTradeSettlement>")) :
                    xml.lastIndexOf(TAX_END) + TAX_END.length();
    xml = xml.substring(0,
                        headerTax) + HEAD_AAM + xml.substring(headerTax);
    return replaceOnce(replaceOnce(replaceOnce(xml,
                                               "<ram:TaxTotalAmount currencyID=\"EUR\">38.00</ram:TaxTotalAmount>",
                                               "<ram:TaxTotalAmount currencyID=\"EUR\">57.00</ram:TaxTotalAmount>"),
                                   "<ram:GrandTotalAmount>338.00</ram:GrandTotalAmount>",
                                   "<ram:GrandTotalAmount>357.00</ram:GrandTotalAmount>"),
                       "<ram:DuePayableAmount>338.00</ram:DuePayableAmount>",
                       "<ram:DuePayableAmount>357.00</ram:DuePayableAmount>");
  }

  private static String replaceOnce(String xml,
                                    String old,
                                    String replacement) {
    assertTrue(xml.contains(old),
               () -> old + " missing in " + xml);
    return xml.replace(old,
                       replacement);
  }

  private static byte[] bytes(String xml) {
    return xml.getBytes(StandardCharsets.UTF_8);
  }

  private static List<ValidationMessage> errors(ValidationResult result) {
    return result.messages()
                 .stream()
                 .filter(message -> message.severity() == Severity.ERROR || message.severity() == Severity.FATAL)
                 .toList();
  }

  private static List<ValidationMessage> calculation(ValidationResult result) {
    return result.messages()
                 .stream()
                 .filter(message -> message.stage() == ValidationMessage.Stage.CALCULATION)
                 .toList();
  }

  @Test
  void readsTheVatOnlyControlCaseUnchanged() {
    String xml = control();
    var    result = PANIOLO.validate(bytes(xml),
                                     FacturXProfile.EXTENDED);
    assertEquals(List.of(),
                 errors(result));
    assertEquals(List.of(),
                 calculation(result));
    for (var mode : ReadMode.values()) {
      var imported = PANIOLO.importInvoice(bytes(xml),
                                           mode);
      assertEquals(List.of(),
                   imported.warnings(),
                   mode::toString);
      assertFalse(imported.isNormalized(),
                  mode::toString);
      assertEquals(0,
                   new BigDecimal("338.00").compareTo(imported.invoice()
                                                              .totals()
                                                              .payableAmount()));
      byte[] normalized = PANIOLO.export(imported,
                                         ExportVariant.NORMALIZED,
                                         InvoiceSyntax.CII);
      assertEquals(List.of(),
                   errors(PLAIN.validate(normalized,
                                         FacturXProfile.EXTENDED)));
    }
  }

  @ParameterizedTest(name = "AAM first: {0}")
  @ValueSource(booleans = {false, true})
  void validatesTheDocumentWithAam(boolean aamFirst) {
    var result = PLAIN.validate(bytes(withAam(aamFirst)),
                                FacturXProfile.EXTENDED);
    assertTrue(result.isValid(),
               result::toString);
  }

  @ParameterizedTest(name = "AAM first: {0}")
  @ValueSource(booleans = {false, true})
  void strictReadingNamesTheTaxType(boolean aamFirst) {
    var error = assertThrows(IllegalArgumentException.class,
                             () -> PANIOLO.importInvoice(bytes(withAam(aamFirst)),
                                                         ReadMode.STRICT));
    // The header is read before the lines.
    assertTrue(error.getMessage()
                    .startsWith("Tax type AAM at header tax " + (aamFirst ?
                                                                 "1" :
                                                                 "3") + " (category S, rate 19.00, basis 100.00, amount 19.00)"),
               error::getMessage);
  }

  @ParameterizedTest(name = "AAM first: {0}")
  @ValueSource(booleans = {false, true})
  void lenientReadingReportsTheTaxAndNeverReadsItAsVat(boolean aamFirst) {
    String xml      = withAam(aamFirst);
    var    imported = PANIOLO.importInvoice(bytes(xml),
                                            ReadMode.LENIENT);
    var other = imported.warnings()
                        .stream()
                        .filter(warning -> ReadWarning.OTHER_TAX.equals(warning.code()))
                        .toList();
    assertEquals(2,
                 other.size(),
                 other::toString);
    assertEquals(null,
                 other.get(0)
                      .lineId());
    assertTrue(other.get(0)
                    .message()
                    .startsWith("Tax type AAM at header tax " + (aamFirst ?
                                                                 "1" :
                                                                 "3")),
               () -> other.get(0)
                          .message());
    assertEquals("2",
                 other.get(1)
                      .lineId());
    assertTrue(other.get(1)
                    .message()
                    .startsWith("Tax type AAM at line 2 tax " + (aamFirst ?
                                                                 "1" :
                                                                 "2") + " (category S, rate 19.00, basis -, amount 19.00)"),
               () -> other.get(1)
                          .message());
    // Line 2 keeps its VAT exemption, whatever the order of the blocks.
    var line2 = imported.invoice()
                        .lines()
                        .get(1);
    assertEquals(DutyTaxFeeCategory.E,
                 line2.tax()
                      .code());
    assertEquals(EXEMPTION,
                 line2.tax()
                      .exemptionReason());
    var totals = imported.invoice()
                         .totals();
    assertEquals(0,
                 new BigDecimal("38.00").compareTo(totals.taxAmount()));
    assertEquals(2,
                 totals.vatBreakdowns()
                       .size());
    assertArrayEquals(bytes(xml),
                      PANIOLO.export(imported,
                                     ExportVariant.ORIGINAL,
                                     InvoiceSyntax.CII));
  }

  @ParameterizedTest(name = "AAM first: {0}")
  @ValueSource(booleans = {false, true})
  void normalizedExportRequiresAnExplicitDecision(boolean aamFirst) {
    var imported = PANIOLO.importInvoice(bytes(withAam(aamFirst)),
                                         ReadMode.LENIENT);
    var error = assertThrows(IllegalArgumentException.class,
                             () -> PANIOLO.export(imported,
                                                  ExportVariant.NORMALIZED,
                                                  InvoiceSyntax.CII));
    assertTrue(error.getMessage()
                    .contains("Tax type AAM") && error.getMessage()
                                                      .contains(ExportVariant.NORMALIZED_DROPPING_OTHER_TAXES.name()),
               error::getMessage);
    String dropped = new String(PANIOLO.export(imported,
                                               ExportVariant.NORMALIZED_DROPPING_OTHER_TAXES,
                                               InvoiceSyntax.CII),
                                StandardCharsets.UTF_8);
    assertFalse(dropped.contains(">AAM<"),
                dropped);
    assertTrue(dropped.contains("<ram:DuePayableAmount>338.00</ram:DuePayableAmount>"),
               dropped);
    assertEquals(List.of(),
                 errors(PLAIN.validate(bytes(dropped),
                                       FacturXProfile.EXTENDED)));
  }

  @ParameterizedTest(name = "AAM first: {0}")
  @ValueSource(booleans = {false, true})
  void calculationCheckNamesTheTotalsItCannotCheck(boolean aamFirst) {
    var findings = calculation(PANIOLO.validate(bytes(withAam(aamFirst)),
                                                FacturXProfile.EXTENDED));
    assertEquals(1,
                 findings.size(),
                 findings::toString);
    var finding = findings.get(0);
    assertEquals(CalculationCheck.NOT_CHECKED,
                 finding.ruleId());
    assertEquals(Severity.INFO,
                 finding.severity());
    assertTrue(finding.message()
                      .contains("BT-110, BT-112 and BT-115") && finding.message()
                                                                      .contains("Tax type AAM at line 2") && finding.message()
                                                                                                                    .contains("Tax type AAM at header"),
               finding::message);
    // Without the calculation check there is no calculation finding at all.
    assertEquals(List.of(),
                 calculation(PLAIN.validate(bytes(withAam(aamFirst)),
                                            FacturXProfile.EXTENDED)));
  }

  @Test
  void ublLineReadsItsVatCategoryAndReportsAnotherTaxScheme() {
    String xml = PLAIN.toXml(TestInvoices.invoice(InvoiceTypeCode.CODE_380,
                                                  "AAM-UBL"),
                             InvoiceSyntax.UBL);
    String other = "<cac:ClassifiedTaxCategory><cbc:ID>S</cbc:ID><cbc:Percent>5</cbc:Percent><cac:TaxScheme><cbc:ID>AAM</cbc:ID></cac:TaxScheme></cac:ClassifiedTaxCategory>";
    int at = xml.indexOf("<cac:ClassifiedTaxCategory>");
    assertTrue(at > 0,
               xml);
    String changed = xml.substring(0,
                                   at) + other + xml.substring(at);
    var error = assertThrows(IllegalArgumentException.class,
                             () -> PLAIN.importInvoice(bytes(changed),
                                                       ReadMode.STRICT));
    assertTrue(error.getMessage()
                    .startsWith("Tax type AAM at line 1 tax 1"),
               error::getMessage);
    var lenient = PLAIN.importInvoice(bytes(changed),
                                      ReadMode.LENIENT);
    assertEquals(List.of(ReadWarning.OTHER_TAX),
                 lenient.warnings()
                        .stream()
                        .map(ReadWarning::code)
                        .toList());
    assertEquals(TaxCategory.standard(new BigDecimal("19")),
                 lenient.invoice()
                        .lines()
                        .get(0)
                        .tax());
  }
}

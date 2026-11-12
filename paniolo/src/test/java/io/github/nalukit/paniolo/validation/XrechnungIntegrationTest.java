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

import io.github.nalukit.paniolo.model.common.DocumentAdjustment;
import io.github.nalukit.paniolo.model.common.TaxCategory;
import io.github.nalukit.paniolo.model.common.value.InvoiceTypeCode;
import io.github.nalukit.paniolo.rules.xrechnung.XrechnungRules;
import io.github.nalukit.paniolo.xml.InvoiceXmlWriter;
import io.github.nalukit.paniolo.xml.cii.CiiInvoiceWriter;
import io.github.nalukit.paniolo.xml.ubl.UblInvoiceWriter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.EnumMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Validates against the official rules packaged in paniolo-rules. Pass
 * -Dxrechnung.bundle=/absolute/path/to/extracted/bundle to test another rule release.
 */
class XrechnungIntegrationTest {
  private static final EnumMap<ValidationProfile.Syntax, InvoiceValidator> VALIDATORS = new EnumMap<>(ValidationProfile.Syntax.class);

  @BeforeAll
  static void compileOfficialRules() {
    for (var syntax : ValidationProfile.Syntax.values()) {
      VALIDATORS.put(syntax,
                     new InvoiceValidator(ValidationProfile.xrechnung(bundle(),
                                                                      syntax)));
    }
  }

  private static Path bundle() {
    String bundle = System.getProperty("xrechnung.bundle");
    return bundle == null || bundle.isBlank() ?
           XrechnungRules.bundle() :
           Path.of(bundle);
  }

  private InvoiceXmlWriter writer(ValidationProfile.Syntax syntax) {
    return syntax == ValidationProfile.Syntax.CII ?
           new CiiInvoiceWriter() :
           new UblInvoiceWriter();
  }

  private io.github.nalukit.paniolo.model.Invoice.Builder invoice(ValidationProfile.Syntax syntax) {
    return InvoiceSamples.invoice()
                         .typeCode(syntax == ValidationProfile.Syntax.UBL_CREDIT_NOTE ?
                                   InvoiceTypeCode.CODE_381 :
                                   InvoiceTypeCode.CODE_380);
  }

  private void assertValid(ValidationProfile.Syntax syntax,
                           String xml) {
    var result = VALIDATORS.get(syntax)
                           .validate(xml);
    assertTrue(result.isValid(),
               () -> result.messages()
                           .toString() + "\n" + xml);
    assertFalse(result.hasWarnings(),
                () -> result.messages()
                            .toString());
  }

  @ParameterizedTest
  @EnumSource(ValidationProfile.Syntax.class)
  void generatedInvoicePassesSchemaEuropeanAndGermanRules(ValidationProfile.Syntax syntax) {
    var tax = InvoiceSamples.standard();
    var invoice = invoice(syntax).addLine(InvoiceSamples.line("1",
                                                              "100",
                                                              tax))
                                 .addLine(InvoiceSamples.line("2",
                                                              "25",
                                                              TaxCategory.standard(new BigDecimal("7"))))
                                 .addAdjustment(DocumentAdjustment.createAllowance()
                                                                  .amount(new BigDecimal("10"))
                                                                  .reason("Treuerabatt")
                                                                  .tax(tax)
                                                                  .build())
                                 .addAdjustment(DocumentAdjustment.createCharge()
                                                                  .amount(new BigDecimal("5"))
                                                                  .reason("Versand")
                                                                  .tax(tax)
                                                                  .build())
                                 .paidAmount(new BigDecimal("20"))
                                 .addNote("Vielen Dank für Ihren Auftrag.")
                                 .build();
    assertValid(syntax,
                writer(syntax).toXml(invoice));
  }

  @ParameterizedTest
  @EnumSource(ValidationProfile.Syntax.class)
  void missingBuyerReferenceReturnsGermanRuleIdAndLocation(ValidationProfile.Syntax syntax) {
    var invoice = invoice(syntax).buyerReference(null)
                                 .addLine(InvoiceSamples.line("1",
                                                              "100",
                                                              InvoiceSamples.standard()))
                                 .build();
    var result = VALIDATORS.get(syntax)
                           .validate(writer(syntax).toXml(invoice));
    assertFalse(result.isValid());
    assertTrue(result.messages()
                     .stream()
                     .anyMatch(m -> m.ruleId()
                                     .equals("BR-DE-15") &&
                                    !m.location()
                                      .isBlank()),
               () -> result.messages()
                           .toString());
  }

  @Test
  void ciiAlsoSupportsCreditNotes() {
    var invoice = InvoiceSamples.invoice()
                                .typeCode(InvoiceTypeCode.CODE_381)
                                .addLine(InvoiceSamples.line("1",
                                                             "100",
                                                             InvoiceSamples.standard()))
                                .build();
    assertValid(ValidationProfile.Syntax.CII,
                new CiiInvoiceWriter().toXml(invoice));
  }

  @ParameterizedTest
  @EnumSource(ValidationProfile.Syntax.class)
  void documentLevelBusinessTermsPassOfficialRules(ValidationProfile.Syntax syntax) {
    var invoice = InvoiceSamples.documentLevelTerms(invoice(syntax))
                                .build();
    assertEquals(new BigDecimal("232.06"),
                 invoice.totals()
                        .payableAmount());
    assertValid(syntax,
                writer(syntax).toXml(invoice));
  }

  @ParameterizedTest
  @EnumSource(ValidationProfile.Syntax.class)
  void directDebitPassesOfficialRules(ValidationProfile.Syntax syntax) {
    assertValid(syntax,
                writer(syntax).toXml(InvoiceSamples.directDebit(invoice(syntax))
                                                   .build()));
  }

  @ParameterizedTest
  @EnumSource(ValidationProfile.Syntax.class)
  void paymentCardAndTaxPointDateCodePassOfficialRules(ValidationProfile.Syntax syntax) {
    assertValid(syntax,
                writer(syntax).toXml(InvoiceSamples.paymentCardAndTaxPointDateCode(invoice(syntax))
                                                   .build()));
  }

  @ParameterizedTest
  @EnumSource(ValidationProfile.Syntax.class)
  void exemptInvoicePassesOfficialRules(ValidationProfile.Syntax syntax) {
    assertValid(syntax,
                writer(syntax).toXml(InvoiceSamples.exempt(invoice(syntax))
                                                   .build()));
  }

  @ParameterizedTest
  @EnumSource(ValidationProfile.Syntax.class)
  void outsideScopeOmitsLineRateAndIncludesZeroBreakdownRate(ValidationProfile.Syntax syntax) {
    assertValid(syntax,
                writer(syntax).toXml(InvoiceSamples.outsideScope(invoice(syntax))
                                                   .build()));
  }
}

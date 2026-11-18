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
import io.github.nalukit.paniolo.model.Invoice;
import io.github.nalukit.paniolo.model.common.InvoiceLine;
import io.github.nalukit.paniolo.model.common.TaxCategory;
import io.github.nalukit.paniolo.model.common.value.CurrencyCode;
import io.github.nalukit.paniolo.model.common.value.InvoiceTypeCode;
import io.github.nalukit.paniolo.rules.xrechnung.XrechnungRules;
import io.github.nalukit.paniolo.validation.InvoiceValidator;
import io.github.nalukit.paniolo.validation.Severity;
import io.github.nalukit.paniolo.validation.ValidationProfile;
import io.github.nalukit.paniolo.xml.ReadMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The pinned KoSIT 3.0.2 bundle (2026-08-31) inherits different R055 zero boundaries from Peppol:
 * UBL accepts zero together with a negative VAT amount, CII does not. Preserve both amounts and
 * expose the official finding, rather than changing money or hiding it to make conversion pass.
 * Revisit the expected CII zero cases when updating the upstream rules.
 */
class VatAccountingSignTest {
  private static final Paniolo PANIOLO = Paniolo.create();
  private static final String R055 = "PEPPOL-EN16931-R055";

  private static Invoice invoice(int quantity,
                                 BigDecimal accountingVat) {
    var line = InvoiceLine.create()
                          .id("1")
                          .name("Service")
                          .quantity(BigDecimal.valueOf(quantity))
                          .unitCode("C62")
                          .netUnitPrice(new BigDecimal("10781.25"))
                          .tax(TaxCategory.standard(new BigDecimal("19")))
                          .build();
    return TestInvoices.builder(InvoiceTypeCode.CODE_380,
                                 "VAT-SIGN")
                       .lines(List.of(line))
                       .taxCurrencyCode(accountingVat == null ? null : CurrencyCode.GBP)
                       .taxAmountInTaxCurrency(accountingVat)
                       .build();
  }

  private static Map<String, BigDecimal> writtenVat(String xml,
                                                    InvoiceSyntax syntax)
      throws Exception {
    var factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(true);
    var document = factory.newDocumentBuilder()
                          .parse(new InputSource(new StringReader(xml)));
    var amounts = document.getElementsByTagNameNS("*",
                                                   syntax == InvoiceSyntax.CII ? "TaxTotalAmount" : "TaxAmount");
    var result = new HashMap<String, BigDecimal>();
    for (int i = 0; i < amounts.getLength(); i++) {
      var amount = (Element) amounts.item(i);
      if (syntax == InvoiceSyntax.UBL && !"TaxTotal".equals(amount.getParentNode().getLocalName())) {
        continue;
      }
      var previous = result.put(amount.getAttribute("currencyID"),
                                  new BigDecimal(amount.getTextContent()));
      assertNull(previous,
                 "one VAT total per currency");
    }
    return result;
  }

  @ParameterizedTest
  @CsvSource({ "-1, -2048.44, false, false",
               "-1, 0,        false, true",
               "-1, 2048.44,  true,  true",
               "0,  -2048.44, false, true",
               "0,  0,        false, false",
               "0,  2048.44,  false, false",
               "1,  -2048.44, true,  true",
               "1,  0,        false, false",
               "1,  2048.44,  false, false" })
  void preservesAmountsAndExposesTheUpstreamSignRule(int quantity,
                                                      BigDecimal accountingVat,
                                                      boolean ublFails,
                                                      boolean ciiFails)
      throws Exception {
    var invoice = invoice(quantity,
                            accountingVat);
    var vat = invoice.totals().taxAmount();
    for (var syntax : InvoiceSyntax.values()) {
      String xml = PANIOLO.toXml(invoice,
                                  syntax);
      var amounts = writtenVat(xml,
                                  syntax);
      assertEquals(2,
                   amounts.size());
      assertEquals(0,
                   vat.compareTo(amounts.get("EUR")));
      assertEquals(0,
                   accountingVat.compareTo(amounts.get("GBP")));
      var read = PANIOLO.read(xml,
                                ReadMode.STRICT).invoice();
      assertEquals(CurrencyCode.GBP,
                   read.taxCurrencyCode());
      assertEquals(0,
                   vat.compareTo(read.totals().taxAmount()));
      assertEquals(0,
                   accountingVat.compareTo(read.taxAmountInTaxCurrency()));

      var type = syntax == InvoiceSyntax.CII ?
                 ValidationProfile.Syntax.CII :
                 ValidationProfile.Syntax.UBL_INVOICE;
      var official = new InvoiceValidator(ValidationProfile.xrechnung(XrechnungRules.bundle(),
                                                                         type)).validate(xml);
      var actual = PANIOLO.validate(xml);
      var expected = (syntax == InvoiceSyntax.CII ? ciiFails : ublFails) ? List.of(Severity.FATAL) : List.of();
      assertEquals(expected,
                   official.messages()
                           .stream()
                           .filter(m -> R055.equals(m.ruleId()))
                           .map(m -> m.severity())
                           .toList(),
                   () -> syntax + ": " + official.messages());
      assertEquals(expected,
                   actual.messages()
                         .stream()
                         .filter(m -> R055.equals(m.ruleId()))
                         .map(m -> m.severity())
                         .toList(),
                   () -> syntax + ": " + actual.messages());
      assertEquals(expected.isEmpty(),
                   actual.isValid(),
                   () -> syntax + ": " + actual.messages());
    }
  }

  @ParameterizedTest
  @ValueSource(ints = { -1, 0, 1 })
  void doesNotRequireAnAccountingAmountWhenNoTaxCurrencyIsStated(int quantity) {
    for (var syntax : InvoiceSyntax.values()) {
      var result = PANIOLO.validate(PANIOLO.toXml(invoice(quantity,
                                                          null),
                                                   syntax));
      assertTrue(result.isValid(),
                 () -> syntax + ": " + result.messages());
    }
  }
}

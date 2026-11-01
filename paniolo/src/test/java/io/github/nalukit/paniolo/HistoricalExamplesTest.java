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
import io.github.nalukit.paniolo.model.common.Party;
import io.github.nalukit.paniolo.model.common.ProcessControl;
import io.github.nalukit.paniolo.model.common.TaxCategory;
import io.github.nalukit.paniolo.model.common.value.DutyTaxFeeCategory;
import io.github.nalukit.paniolo.model.common.value.InvoiceTypeCode;
import io.github.nalukit.paniolo.model.extended.ExtendedContact;
import io.github.nalukit.paniolo.model.extended.LineExtension;
import io.github.nalukit.paniolo.model.extended.LineSubtype;
import io.github.nalukit.paniolo.model.extended.PartyExtension;
import io.github.nalukit.paniolo.validation.Severity;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cases found with the original attachments of Mustang issues #662, #569, and #520: documents of
 * ZUGFeRD 2.0/2.1, contact departments of the official examples, and VAT categories of lines that do
 * not count. The attachments themselves are not part of the repository; the tests rebuild the cases.
 */
class HistoricalExamplesTest {
  private static final Paniolo PANIOLO = Paniolo.create();

  private static List<String> errors(byte[] xml,
                                     FacturXProfile profile) {
    return PANIOLO.validate(xml,
                            profile)
                  .messages()
                  .stream()
                  .filter(message -> message.severity() == Severity.ERROR || message.severity() == Severity.FATAL)
                  .map(message -> message.ruleId() + " " + message.message())
                  .toList();
  }

  private static Invoice.Builder withInformationLine() {
    return TestInvoices.builder(InvoiceTypeCode.CODE_380,
                                "H-1")
                       .addLine(InvoiceLine.create()
                                           .id("9")
                                           .name("Hinweis: Versand umsatzsteuerfrei")
                                           .quantity(BigDecimal.ONE)
                                           .unitCode("H87")
                                           .netUnitPrice(BigDecimal.ZERO)
                                           .tax(new TaxCategory(DutyTaxFeeCategory.E,
                                                                BigDecimal.ZERO,
                                                                "Umsatzsteuerfrei",
                                                                null))
                                           .extended(LineExtension.create()
                                                                  .subtype(LineSubtype.INFORMATION)
                                                                  .build())
                                           .build());
  }

  /**
   * #662: the specification identifier of ZUGFeRD 2.0 / 2.1 EXTENDED is EXTENDED as well.
   */
  @Test
  void writesTheZugferd2ExtendedIdentifier() {
    var control = new ProcessControl(null,
                                     ProcessControl.ZUGFERD_2_EXTENDED);
    assertTrue(control.isExtended());
    var invoice = withInformationLine().processControl(control)
                                       .build();
    byte[] xml = PANIOLO.toBytes(invoice,
                                 InvoiceSyntax.CII);
    assertEquals(List.of(),
                 errors(xml,
                        FacturXProfile.EXTENDED));
    assertEquals(invoice,
                 PANIOLO.read(xml));
  }

  /**
   * #520: the VAT category of a line that does not count gets a breakdown without amount, which
   * BR-FXEXT-E-01 requires for every line.
   */
  @Test
  void statesTheCategoryOfALineThatDoesNotCount() {
    var invoice   = withInformationLine().processControl(ProcessControl.facturXExtended())
                                         .build();
    var exempt    = invoice.totals()
                           .vatBreakdowns()
                           .stream()
                           .filter(breakdown -> breakdown.tax()
                                                         .code() == DutyTaxFeeCategory.E)
                           .findFirst()
                           .orElseThrow();
    assertEquals(0,
                 BigDecimal.ZERO.compareTo(exempt.taxableAmount()));
    assertEquals(0,
                 new BigDecimal("200.00").compareTo(invoice.totals()
                                                           .lineTotal()));
    assertEquals(List.of(),
                 errors(PANIOLO.toBytes(invoice,
                                        InvoiceSyntax.CII),
                        FacturXProfile.EXTENDED));
  }

  /**
   * #569: a department as contact point (BT-41) is CII content of every profile; UBL has no place
   * for it. A person and a department together are two contact points (CII-SR-465), as the official
   * ZUGFeRD 2.2 XRechnung examples state them, and stay EXTENDED information.
   */
  @Test
  void keepsTheDepartmentOfAContactInCii() {
    var    base   = TestInvoices.invoice(InvoiceTypeCode.CODE_380,
                                         "H-2");
    var    seller = base.seller();
    Party  withDepartment = new Party(seller.name(),
                                      seller.address(),
                                      seller.vatId(),
                                      seller.taxId(),
                                      seller.electronicAddress(),
                                      null,
                                      seller.identifier(),
                                      seller.identifierSchemeId(),
                                      seller.tradingName(),
                                      seller.legalRegistrationId(),
                                      seller.legalRegistrationSchemeId(),
                                      seller.additionalLegalInformation(),
                                      new PartyExtension(null,
                                                         null,
                                                         new ExtendedContact(null,
                                                                             "Buchhaltung",
                                                                             null,
                                                                             "+49 30 123456",
                                                                             null,
                                                                             "rechnung@example.org")));
    var invoice = TestInvoices.builder(InvoiceTypeCode.CODE_380,
                                       "H-2")
                              .seller(withDepartment)
                              .build();
    assertFalse(invoice.hasExtendedInformation());
    String xml = PANIOLO.toXml(invoice,
                               InvoiceSyntax.CII);
    assertTrue(xml.contains("<ram:DepartmentName>Buchhaltung</ram:DepartmentName>"),
               xml);
    var result = PANIOLO.validate(xml);
    assertTrue(result.isValid(),
               () -> result.messages()
                           .toString());
    assertEquals(invoice,
                 PANIOLO.read(xml));
    var refused = assertThrows(IllegalArgumentException.class,
                               () -> PANIOLO.toXml(invoice,
                                                   InvoiceSyntax.UBL));
    assertTrue(refused.getMessage()
                      .contains("Buchhaltung"),
               refused::getMessage);
    // Person and department: two contact points, not for XRechnung.
    var both = new PartyExtension(null,
                                  null,
                                  new ExtendedContact("Erika Muster",
                                                      "Buchhaltung",
                                                      null,
                                                      "+49 30 123456",
                                                      null,
                                                      "rechnung@example.org"));
    assertFalse(both.isDepartmentContactOnly());
  }
}

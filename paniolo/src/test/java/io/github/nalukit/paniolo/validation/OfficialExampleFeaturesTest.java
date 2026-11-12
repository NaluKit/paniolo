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
import io.github.nalukit.paniolo.model.common.InvoiceNote;
import io.github.nalukit.paniolo.model.common.value.InvoiceTypeCode;
import io.github.nalukit.paniolo.rules.xrechnung.XrechnungRules;
import io.github.nalukit.paniolo.xml.InvoiceXmlReader;
import io.github.nalukit.paniolo.xml.InvoiceXmlWriter;
import io.github.nalukit.paniolo.xml.cii.CiiInvoiceReader;
import io.github.nalukit.paniolo.xml.cii.CiiInvoiceWriter;
import io.github.nalukit.paniolo.xml.ubl.UblInvoiceReader;
import io.github.nalukit.paniolo.xml.ubl.UblInvoiceWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Corrective invoice with a negative allowance, a note subject code outside the enum and a line
 * whose stated net amount reflects a rounded net price, as in the official ZUGFeRD examples.
 */
class OfficialExampleFeaturesTest {
  private static Invoice correction(InvoiceTypeCode type) {
    var tax = InvoiceSamples.standard();
    return InvoiceSamples.invoice()
                         .typeCode(type)
                         .addNote(InvoiceNote.create()
                                             .subjectCode("ADU")
                                             .text("Korrektur zur Rechnung R-2026-000")
                                             .build())
                         .addLine(InvoiceLine.create()
                                             .id("1")
                                             .name("Artikel")
                                             .quantity(new BigDecimal("20"))
                                             .unitCode("H87")
                                             .netUnitPrice(new BigDecimal("2.77"))
                                             .netAmount(new BigDecimal("55.46"))
                                             .tax(tax)
                                             .build())
                         .addAdjustment(DocumentAdjustment.createAllowance()
                                                          .amount(new BigDecimal("-0.10"))
                                                          .reason("Korrektur Nachlass")
                                                          .tax(tax)
                                                          .build())
                         .build();
  }

  private static InvoiceXmlWriter writer(Syntax syntax) {
    return syntax == Syntax.UBL ?
           new UblInvoiceWriter() :
           new CiiInvoiceWriter();
  }

  private static InvoiceXmlReader reader(Syntax syntax) {
    return syntax == Syntax.UBL ?
           new UblInvoiceReader() :
           new CiiInvoiceReader();
  }

  @ParameterizedTest
  @EnumSource(Syntax.class)
  void correctiveInvoicePassesXrechnungAndReadsBack(Syntax syntax) {
    var    invoice = correction(InvoiceTypeCode.CODE_384);
    String xml     = writer(syntax).toXml(invoice);
    var profile = ValidationProfile.xrechnung(XrechnungRules.bundle(),
                                              syntax == Syntax.UBL ?
                                              ValidationProfile.Syntax.UBL_INVOICE :
                                              ValidationProfile.Syntax.CII);
    var result = new InvoiceValidator(profile).validate(xml);
    assertTrue(result.isValid(),
               () -> result.messages() + "\n" + xml);
    assertEquals(invoice,
                 reader(syntax).fromXml(xml));
    assertEquals(new BigDecimal("55.46"),
                 invoice.totals()
                        .lineTotal());
  }

  @Test
  void selfBilledCreditNoteIsWrittenAsUblCreditNote() {
    var    invoice = correction(InvoiceTypeCode.CODE_261);
    String xml     = new UblInvoiceWriter().toXml(invoice);
    assertTrue(xml.contains("<CreditNote"),
               xml);
    assertTrue(xml.contains("<cbc:CreditNoteTypeCode>261</cbc:CreditNoteTypeCode>"));
    assertEquals(invoice,
                 new UblInvoiceReader().fromXml(xml));
    assertEquals(invoice,
                 new CiiInvoiceReader().fromXml(new CiiInvoiceWriter().toXml(invoice)));
  }

  enum Syntax {
    UBL,
    CII
  }
}

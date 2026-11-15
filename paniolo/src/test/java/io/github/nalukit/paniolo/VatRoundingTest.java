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
import io.github.nalukit.paniolo.model.common.VatAmount;
import io.github.nalukit.paniolo.model.common.value.DutyTaxFeeCategory;
import io.github.nalukit.paniolo.model.common.value.InvoiceTypeCode;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A VAT category tax amount (BT-117) rounded per line instead of once per category (task V08,
 * Mustang issue #1262). The official rules accept such an amount: EN 16931 and XRechnung by up to
 * 1.00 (BR-CO-17, BR-S-09), Factur-X EXTENDED by 0.01 per line, allowance, and charge
 * (BR-FXEXT-S-09). Paniolo keeps a stated amount within the narrower EXTENDED tolerance, on reading
 * and when the seller states it; a larger difference is no rounding and stays a deviation.
 *
 * <p>The invoice has line 1 with 2 x 100.00 and three lines with 0.33, all at 19 %: the VAT of the
 * category, rounded once, is 38.19 (200.99 x 19 %); rounded per line it is 38.00 + 3 x 0.06 = 38.18.
 * The tolerance is 4 x 0.01 = 0.04.
 */
class VatRoundingTest {
  private static final Paniolo     PANIOLO = Paniolo.builder()
                                                    .calculationCheck(true)
                                                    .build();
  private static final TaxCategory VAT_19  = TaxCategory.standard(new BigDecimal("19"));

  private enum Profile {
    XRECHNUNG(ProcessControl.xrechnung(),
              null),
    EN16931(ProcessControl.en16931(),
            FacturXProfile.EN16931),
    EXTENDED(ProcessControl.facturXExtended(),
             FacturXProfile.EXTENDED);

    private final ProcessControl processControl;
    private final FacturXProfile rules;

    Profile(ProcessControl processControl,
            FacturXProfile rules) {
      this.processControl = processControl;
      this.rules          = rules;
    }

    ValidationResult validate(byte[] xml) {
      return rules == null ?
             PANIOLO.validate(xml) :
             PANIOLO.validate(xml,
                              rules);
    }
  }

  private static Invoice.Builder builder(ProcessControl processControl) {
    var builder = TestInvoices.builder(InvoiceTypeCode.CODE_380,
                                       "RUND-1")
                              .processControl(processControl);
    for (int index = 2; index <= 4; index++) {
      builder.addLine(InvoiceLine.create()
                                 .id(String.valueOf(index))
                                 .name("Kleinteil " + index)
                                 .quantity(BigDecimal.ONE)
                                 .unitCode("C62")
                                 .netUnitPrice(new BigDecimal("0.33"))
                                 .tax(VAT_19)
                                 .build());
    }
    return builder;
  }

  /**
   * The document as Paniolo writes it, with the stated VAT changed by the given difference and the
   * totals that follow from it.
   */
  private static byte[] stated(Profile profile,
                               String difference) {
    String     xml   = PANIOLO.toXml(builder(profile.processControl).build(),
                                     InvoiceSyntax.CII);
    BigDecimal delta = new BigDecimal(difference);
    String     vat   = new BigDecimal("38.19").add(delta)
                                              .toPlainString();
    String     total = new BigDecimal("239.18").add(delta)
                                               .toPlainString();
    for (String expected : List.of("<ram:CalculatedAmount>38.19</ram:CalculatedAmount>",
                                   "<ram:TaxTotalAmount currencyID=\"EUR\">38.19</ram:TaxTotalAmount>",
                                   "<ram:GrandTotalAmount>239.18</ram:GrandTotalAmount>",
                                   "<ram:DuePayableAmount>239.18</ram:DuePayableAmount>")) {
      assertTrue(xml.contains(expected),
                 () -> expected + " missing in " + xml);
    }
    return xml.replace("<ram:CalculatedAmount>38.19</ram:CalculatedAmount>",
                       "<ram:CalculatedAmount>" + vat + "</ram:CalculatedAmount>")
              .replace("<ram:TaxTotalAmount currencyID=\"EUR\">38.19</ram:TaxTotalAmount>",
                       "<ram:TaxTotalAmount currencyID=\"EUR\">" + vat + "</ram:TaxTotalAmount>")
              .replace("<ram:GrandTotalAmount>239.18</ram:GrandTotalAmount>",
                       "<ram:GrandTotalAmount>" + total + "</ram:GrandTotalAmount>")
              .replace("<ram:DuePayableAmount>239.18</ram:DuePayableAmount>",
                       "<ram:DuePayableAmount>" + total + "</ram:DuePayableAmount>")
              .getBytes(StandardCharsets.UTF_8);
  }

  private static List<String> calculationFindings(ValidationResult result) {
    return result.messages()
                 .stream()
                 .filter(message -> message.stage() == io.github.nalukit.paniolo.validation.ValidationMessage.Stage.CALCULATION)
                 .map(message -> message.ruleId() + "@" + message.location())
                 .toList();
  }

  @ParameterizedTest
  @EnumSource(Profile.class)
  void keepsAVatAmountRoundedPerLine(Profile profile) {
    byte[] xml        = stated(profile,
                               "-0.01");
    var    validation = profile.validate(xml);
    assertTrue(validation.isValid(),
               validation::toString);
    assertEquals(List.of(),
                 calculationFindings(validation));
    for (var mode : ReadMode.values()) {
      var imported = PANIOLO.importInvoice(xml,
                                           mode);
      assertEquals(List.of(),
                   imported.warnings(),
                   mode::toString);
      assertEquals(List.of(),
                   imported.deviations(),
                   mode::toString);
      assertEquals(List.of(new VatAmount(VAT_19,
                                         new BigDecimal("38.18"))),
                   imported.invoice()
                           .vatAmounts());
      var totals = imported.invoice()
                           .totals();
      assertEquals(new BigDecimal("38.18"),
                   totals.taxAmount());
      assertEquals(new BigDecimal("239.17"),
                   totals.payableAmount());
      assertArrayEquals(xml,
                        PANIOLO.export(imported,
                                       ExportVariant.ORIGINAL,
                                       InvoiceSyntax.CII));
      byte[] normalized = PANIOLO.export(imported,
                                         ExportVariant.NORMALIZED,
                                         InvoiceSyntax.CII);
      assertTrue(new String(normalized,
                            StandardCharsets.UTF_8).contains("<ram:DuePayableAmount>239.17</ram:DuePayableAmount>"));
      assertTrue(profile.validate(normalized)
                        .isValid());
    }
  }

  /**
   * 0.50 more is valid in EN 16931 and XRechnung (BR-CO-17 accepts up to 1.00), but no rounding of
   * four amounts: strict reading rejects it, lenient reading reports it and recalculates.
   */
  @ParameterizedTest
  @EnumSource(value = Profile.class, names = { "XRECHNUNG", "EN16931" })
  void reportsADifferenceBeyondTheRounding(Profile profile) {
    byte[] xml = stated(profile,
                        "0.50");
    assertTrue(profile.validate(xml)
                      .isValid());
    assertThrows(IllegalArgumentException.class,
                 () -> PANIOLO.importInvoice(xml,
                                             ReadMode.STRICT));
    var lenient = PANIOLO.importInvoice(xml,
                                        ReadMode.LENIENT);
    assertEquals(List.of(ReadWarning.TAX_TOTAL,
                         ReadWarning.PAYABLE_AMOUNT),
                 lenient.warnings()
                        .stream()
                        .map(ReadWarning::code)
                        .toList());
    assertEquals(List.of(),
                 lenient.invoice()
                        .vatAmounts());
    assertEquals(new BigDecimal("239.18"),
                 lenient.invoice()
                        .totals()
                        .payableAmount());
    assertTrue(lenient.isNormalized());
  }

  @ParameterizedTest
  @EnumSource(Profile.class)
  void writesTheVatAmountTheSellerStates(Profile profile) {
    var invoice = builder(profile.processControl).vatAmount(VAT_19,
                                                            new BigDecimal("38.18"))
                                                 .build();
    assertEquals(new BigDecimal("239.17"),
                 invoice.totals()
                        .payableAmount());
    for (var syntax : profile == Profile.XRECHNUNG ?
                      List.of(InvoiceSyntax.CII,
                              InvoiceSyntax.UBL) :
                      List.of(InvoiceSyntax.CII)) {
      byte[] xml        = PANIOLO.toBytes(invoice,
                                          syntax);
      var    validation = syntax == InvoiceSyntax.UBL ?
                          PANIOLO.validate(xml) :
                          profile.validate(xml);
      assertTrue(validation.isValid(),
                 () -> syntax + ": " + validation);
      assertEquals(List.of(),
                   calculationFindings(validation),
                   syntax::toString);
      var read = PANIOLO.read(xml,
                              ReadMode.STRICT);
      assertEquals(invoice,
                   read.invoice(),
                   syntax::toString);
    }
  }

  @Test
  void rejectsAStatedAmountBeyondTheRounding() {
    var error = assertThrows(IllegalArgumentException.class,
                             () -> builder(ProcessControl.xrechnung()).vatAmount(VAT_19,
                                                                                 new BigDecimal("38.14"))
                                                                      .build());
    assertEquals("Stated VAT amount 38.14 of category S 19 % differs from 38.19 by more than the rounding of its amounts, 0.04 (0.01 per line, allowance, and charge)",
                 error.getMessage());
    // The tolerance itself is accepted.
    assertEquals(new BigDecimal("38.15"),
                 builder(ProcessControl.xrechnung()).vatAmount(VAT_19,
                                                               new BigDecimal("38.15"))
                                                    .build()
                                                    .totals()
                                                    .taxAmount());
  }

  @Test
  void rejectsAStatedAmountWithoutItsCategory() {
    var error = assertThrows(IllegalArgumentException.class,
                             () -> builder(ProcessControl.xrechnung()).vatAmount(TaxCategory.standard(new BigDecimal("7")),
                                                                                 new BigDecimal("1.00"))
                                                                      .build());
    assertTrue(error.getMessage()
                    .endsWith("has no line, allowance, or charge of that category"),
               error::getMessage);
    var zero = assertThrows(IllegalArgumentException.class,
                            () -> new VatAmount(new TaxCategory(DutyTaxFeeCategory.E,
                                                                BigDecimal.ZERO,
                                                                "Steuerbefreit",
                                                                null),
                                                new BigDecimal("0.01")));
    assertTrue(zero.getMessage()
                   .startsWith("A stated VAT amount requires a VAT rate above zero"),
               zero::getMessage);
    assertThrows(IllegalArgumentException.class,
                 () -> builder(ProcessControl.xrechnung()).vatAmount(VAT_19,
                                                                     new BigDecimal("38.18"))
                                                          .vatAmount(VAT_19,
                                                                     new BigDecimal("38.17"))
                                                          .build());
    assertFalse(builder(ProcessControl.xrechnung()).build()
                                                   .totals()
                                                   .taxAmount()
                                                   .equals(new BigDecimal("38.18")));
  }
}

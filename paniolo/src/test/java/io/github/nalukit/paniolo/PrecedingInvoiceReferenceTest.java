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
import io.github.nalukit.paniolo.model.common.PrecedingInvoiceReference;
import io.github.nalukit.paniolo.model.common.ProcessControl;
import io.github.nalukit.paniolo.model.common.value.InvoiceTypeCode;
import io.github.nalukit.paniolo.validation.ValidationMessage;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Preceding invoice references (BG-3): UBL repeats them, CII writes several only in the Factur-X
 * profiles EXTENDED and EXTENDED-CTC-FR. The D16B schema of the CII validation of XRechnung, Peppol, and
 * EN 16931 allows one {@code ram:InvoiceReferencedDocument}, so a second one would be a schema error
 * there; the writer refuses it instead of writing an invalid document.
 */
class PrecedingInvoiceReferenceTest {
  private static final Paniolo PANIOLO = Paniolo.create();

  private static final Pattern CII_REFERENCE = Pattern.compile("<ram:InvoiceReferencedDocument>");
  private static final Pattern UBL_REFERENCE = Pattern.compile("<cac:BillingReference>");

  private static Invoice invoice(ProcessControl processControl,
                                 int references) {
    var builder = TestInvoices.builder(InvoiceTypeCode.CODE_380,
                                       "VR-1")
                              .processControl(processControl);
    for (int i = 1; i <= references; i++) {
      builder.addPrecedingInvoiceReference(new PrecedingInvoiceReference("V-" + i,
                                                                         LocalDate.of(2026,
                                                                                      9,
                                                                                      i)));
    }
    return builder.build();
  }

  private static long count(Pattern pattern,
                            String xml) {
    return pattern.matcher(xml)
                  .results()
                  .count();
  }

  @Test
  void oneReferenceIsWrittenInEveryProfile() {
    for (var processControl : List.of(ProcessControl.xrechnung(),
                                      ProcessControl.en16931(),
                                      ProcessControl.facturXExtended())) {
      String xml = PANIOLO.toXml(invoice(processControl,
                                         1),
                                 InvoiceSyntax.CII);
      assertEquals(1,
                   count(CII_REFERENCE,
                         xml),
                   processControl.specificationId());
    }
  }

  @Test
  void severalReferencesAreRefusedInCiiOutsideExtended() {
    // the sample invoice has contact data that the profile BASIC refuses for another reason; BASIC
    // follows below by its schema
    for (var processControl : List.of(ProcessControl.xrechnung(),
                                      ProcessControl.en16931())) {
      Invoice invoice = invoice(processControl,
                                2);
      var e = assertThrows(IllegalArgumentException.class,
                           () -> PANIOLO.toXml(invoice,
                                               InvoiceSyntax.CII),
                           processControl.specificationId());
      assertTrue(e.getMessage()
                  .contains("only one preceding invoice reference"),
                 e.getMessage());
      assertTrue(e.getMessage()
                  .contains("UBL"),
                 e.getMessage());
    }
  }

  @Test
  void ublRepeatsTheReferenceAndReadsItBack() {
    Invoice invoice = invoice(ProcessControl.xrechnung(),
                              3);

    String xml = PANIOLO.toXml(invoice,
                               InvoiceSyntax.UBL);

    assertEquals(3,
                 count(UBL_REFERENCE,
                       xml));
    assertEquals(List.of("V-1",
                         "V-2",
                         "V-3"),
                 PANIOLO.read(xml)
                        .precedingInvoiceReferences()
                        .stream()
                        .map(PrecedingInvoiceReference::number)
                        .toList());
  }

  @Test
  void theExtendedProfileRepeatsTheReferenceInCiiAndIsValid() {
    Invoice invoice = invoice(ProcessControl.facturXExtended(),
                              2);

    String xml = PANIOLO.toXml(invoice,
                               InvoiceSyntax.CII);

    assertEquals(2,
                 count(CII_REFERENCE,
                       xml));
    assertEquals(List.of("V-1",
                         "V-2"),
                 PANIOLO.read(xml)
                        .precedingInvoiceReferences()
                        .stream()
                        .map(PrecedingInvoiceReference::number)
                        .toList());
    assertTrue(PANIOLO.validate(xml.getBytes(StandardCharsets.UTF_8))
                      .isValid());
  }

  /**
   * The reason for the refusal, with a document written by the EXTENDED profile and a plain
   * specification identifier: the D16B schema of the standard validation rejects the second
   * reference, the schema of the Factur-X profile EN16931 accepts it, and the schema of the profile
   * BASIC rejects it again.
   */
  @Test
  void theSchemasOfTheValidationAgreeWithTheRule() {
    String extended = PANIOLO.toXml(invoice(ProcessControl.facturXExtended(),
                                            2),
                                    InvoiceSyntax.CII);
    byte[] plain = extended.replace(ProcessControl.facturXExtended()
                                                  .specificationId(),
                                    ProcessControl.en16931()
                                                  .specificationId())
                           .getBytes(StandardCharsets.UTF_8);

    var standard = PANIOLO.validate(plain);
    assertFalse(standard.isValid());
    assertTrue(standard.messages()
                       .stream()
                       .anyMatch(message -> message.stage() == ValidationMessage.Stage.XSD && message.message()
                                                                                                     .contains("InvoiceReferencedDocument")),
               standard.messages()
                       .toString());
    assertTrue(PANIOLO.validate(plain,
                                FacturXProfile.EN16931)
                      .isValid());
    assertFalse(PANIOLO.validate(plain,
                                 FacturXProfile.BASIC)
                       .isValid());
  }
}

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
import io.github.nalukit.paniolo.model.common.value.InvoiceTypeCode;
import io.github.nalukit.paniolo.validation.ValidationResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A string is validated by its characters: the result equals the one of its UTF-8 bytes, and an
 * encoding stated in the XML declaration is ignored, also by the calculation check.
 */
class StringValidationTest {
  private static final Paniolo PANIOLO = Paniolo.builder()
                                                .calculationCheck(true)
                                                .build();

  private static Invoice invoice() {
    return TestInvoices.builder(InvoiceTypeCode.CODE_380,
                                "S-1")
                       .addLine(InvoiceLine.create()
                                           .id("2")
                                           .name("Größenänderung für Bürostühle")
                                           .quantity(new BigDecimal("3"))
                                           .unitCode("HUR")
                                           .netUnitPrice(new BigDecimal("80.00"))
                                           .tax(TaxCategory.standard(new BigDecimal("19")))
                                           .build())
                       .build();
  }

  private static void sameResult(ValidationResult expected,
                                 ValidationResult actual) {
    assertEquals(expected.profileName(),
                 actual.profileName());
    assertEquals(expected.messages(),
                 actual.messages());
    assertTrue(actual.duration()
                     .compareTo(Duration.ZERO) > 0,
               () -> String.valueOf(actual.duration()));
  }

  @Test
  void validatesAStringLikeItsUtf8Bytes() {
    for (var syntax : InvoiceSyntax.values()) {
      String xml = PANIOLO.toXml(invoice(),
                                 syntax);
      assertTrue(xml.contains("Bürostühle"),
                 syntax::name);
      var result = PANIOLO.validate(xml);
      assertTrue(result.isValid(),
                 result::toString);
      sameResult(PANIOLO.validate(xml.getBytes(StandardCharsets.UTF_8)),
                 result);
    }
  }

  @Test
  void ignoresTheEncodingOfTheXmlDeclaration() {
    for (var syntax : InvoiceSyntax.values()) {
      String xml = PANIOLO.toXml(invoice(),
                                 syntax);
      String declared = xml.replaceFirst("encoding=\"UTF-8\"",
                                         "encoding=\"ISO-8859-1\"");
      assertNotEquals(xml,
                      declared,
                      syntax::name);
      var result = PANIOLO.validate(declared);
      assertTrue(result.isValid(),
                 result::toString);
      sameResult(PANIOLO.validate(xml),
                 result);
    }
  }

  @Test
  void validatesZugferd1AndRejectsUnknownDocumentsLikeBytes() {
    String zugferd1 = "<rsm:CrossIndustryDocument xmlns:rsm=\"urn:ferd:CrossIndustryDocument:invoice:1p0\"/>";
    sameResult(PANIOLO.validate(zugferd1.getBytes(StandardCharsets.UTF_8)),
               PANIOLO.validate(zugferd1));
    for (String unknown : new String[] {"<Unknown/>",
                                        "<Invoice"}) {
      var fromBytes = assertThrows(IllegalArgumentException.class,
                                   () -> PANIOLO.validate(unknown.getBytes(StandardCharsets.UTF_8)));
      var fromString = assertThrows(IllegalArgumentException.class,
                                    () -> PANIOLO.validate(unknown));
      assertEquals(fromBytes.getMessage(),
                   fromString.getMessage());
    }
  }
}

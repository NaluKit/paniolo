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
import io.github.nalukit.paniolo.model.common.Address;
import io.github.nalukit.paniolo.model.common.DeliveryInformation;
import io.github.nalukit.paniolo.model.common.value.Country;
import io.github.nalukit.paniolo.model.common.value.InvoiceTypeCode;
import io.github.nalukit.paniolo.validation.Severity;
import io.github.nalukit.paniolo.validation.ValidationMessage;
import io.github.nalukit.paniolo.xml.ReadMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Addresses without street (task C01, Mustang issue #222). EN 16931 requires the country of an
 * address only (BR-09, BR-11, BR-57), XRechnung also city and post code of seller and buyer
 * (BR-DE-3, BR-DE-4, BR-DE-8, BR-DE-9), but no street. Such a document was valid and rejected by
 * both read modes with {@code street must not be blank}. The model now requires the country only;
 * what a profile requires beyond it stays with validation.
 */
class AddressWithoutStreetTest {
  private static final Paniolo PANIOLO = Paniolo.create();

  /**
   * An address of the document: its street and city as written, and how to get it from an invoice.
   */
  private enum Role {
    SELLER("Musterstraße 1",
           "Berlin",
           invoice -> invoice.seller()
                             .address()),
    BUYER("Kundenweg 2",
          "Hamburg",
          invoice -> invoice.buyer()
                            .address()),
    DELIVERY("Lieferweg 3",
             "Bremen",
             invoice -> invoice.delivery()
                               .address());

    private final String                     street;
    private final String                     city;
    private final Function<Invoice, Address> address;

    Role(String street,
         String city,
         Function<Invoice, Address> address) {
      this.street  = street;
      this.city    = city;
      this.address = address;
    }
  }

  private static String xml(InvoiceSyntax syntax) {
    return PANIOLO.toXml(TestInvoices.builder(InvoiceTypeCode.CODE_380,
                                              "ADR-1")
                                     .delivery(DeliveryInformation.create()
                                                                  .actualDeliveryDate(LocalDate.of(2026,
                                                                                                   10,
                                                                                                   26))
                                                                  .address(new Address("Lieferweg 3",
                                                                                       "Bremen",
                                                                                       "28195",
                                                                                       Country.DE))
                                                                  .build())
                                     .build(),
                         syntax);
  }

  /**
   * Removes an element with the given text, with either prefix of the two syntaxes.
   */
  private static String without(String xml,
                                String cii,
                                String ubl,
                                String text) {
    for (String name : List.of(cii,
                               ubl)) {
      String element = "<" + name + ">" + text + "</" + name + ">";
      if (xml.contains(element)) {
        return xml.replace(element,
                           "");
      }
    }
    throw new AssertionError(text + " missing in " + xml);
  }

  private static List<String> errors(String xml) {
    return PANIOLO.validate(xml)
                  .messages()
                  .stream()
                  .filter(message -> message.severity() == Severity.ERROR || message.severity() == Severity.FATAL)
                  .map(ValidationMessage::ruleId)
                  .toList();
  }

  @ParameterizedTest
  @EnumSource(Role.class)
  void readsAndWritesAnAddressWithoutStreet(Role role) {
    for (var syntax : InvoiceSyntax.values()) {
      String xml = without(xml(syntax),
                           "ram:LineOne",
                           "cbc:StreetName",
                           role.street);
      assertEquals(List.of(),
                   errors(xml),
                   syntax::toString);
      for (var mode : ReadMode.values()) {
        var read = PANIOLO.read(xml,
                                mode);
        assertEquals(List.of(),
                     read.warnings());
        var address = role.address.apply(read.invoice());
        // The missing street stays missing; nothing is invented.
        assertNull(address.street(),
                   syntax::toString);
        assertEquals(role.city,
                     address.city());
        assertEquals(Country.DE,
                     address.country());
        assertFalse(address.isCountryOnly());
        String written = PANIOLO.toXml(read.invoice(),
                                       syntax);
        assertFalse(written.contains(role.street),
                    written);
        assertTrue(written.contains(role.city),
                   written);
        assertEquals(List.of(),
                     errors(written),
                     syntax::toString);
        assertEquals(read.invoice(),
                     PANIOLO.read(written,
                                  ReadMode.STRICT)
                            .invoice());
      }
    }
  }

  /**
   * City and post code of seller and buyer are a matter of XRechnung, which validation reports;
   * reading keeps what the document states.
   */
  @ParameterizedTest
  @EnumSource(value = Role.class, names = { "SELLER", "BUYER" })
  void leavesTheCityToValidation(Role role) {
    for (var syntax : InvoiceSyntax.values()) {
      String xml = without(xml(syntax),
                           "ram:CityName",
                           "cbc:CityName",
                           role.city);
      String rule = role == Role.SELLER ?
                    "BR-DE-3" :
                    "BR-DE-8";
      assertTrue(errors(xml).contains(rule),
                 () -> syntax + ": " + errors(xml));
      var address = role.address.apply(PANIOLO.read(xml,
                                                    ReadMode.STRICT)
                                              .invoice());
      assertNull(address.city());
      assertEquals(role.street,
                   address.street());
    }
  }

  @ParameterizedTest
  @EnumSource(Role.class)
  void rejectsAnAddressWithoutCountry(Role role) {
    for (var syntax : InvoiceSyntax.values()) {
      String xml     = xml(syntax);
      String element = syntax == InvoiceSyntax.CII ?
                       "<ram:CountryID>DE</ram:CountryID>" :
                       "<cbc:IdentificationCode>DE</cbc:IdentificationCode>";
      int at = xml.indexOf(role.city);
      assertTrue(at > 0);
      // The country follows the city of the same address.
      int country = xml.indexOf(element,
                                at);
      assertTrue(country > 0,
                 xml);
      String changed = xml.substring(0,
                                     country) + xml.substring(country + element.length());
      for (var mode : ReadMode.values()) {
        assertThrows(RuntimeException.class,
                     () -> PANIOLO.read(changed,
                                        mode),
                     () -> syntax + " " + mode);
      }
    }
  }
}

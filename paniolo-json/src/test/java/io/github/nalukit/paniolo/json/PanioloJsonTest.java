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
package io.github.nalukit.paniolo.json;

import io.github.nalukit.paniolo.model.Invoice;
import io.github.nalukit.paniolo.model.common.AdditionalSupportingDocument;
import io.github.nalukit.paniolo.model.common.Address;
import io.github.nalukit.paniolo.model.common.Attachment;
import io.github.nalukit.paniolo.model.common.Contact;
import io.github.nalukit.paniolo.model.common.CreditTransfer;
import io.github.nalukit.paniolo.model.common.DeliveryInformation;
import io.github.nalukit.paniolo.model.common.ElectronicAddress;
import io.github.nalukit.paniolo.model.common.InvoiceLine;
import io.github.nalukit.paniolo.model.common.Party;
import io.github.nalukit.paniolo.model.common.Payee;
import io.github.nalukit.paniolo.model.common.PaymentInstruction;
import io.github.nalukit.paniolo.model.common.TaxCategory;
import io.github.nalukit.paniolo.model.common.value.AllowanceChargeIdentification;
import io.github.nalukit.paniolo.model.common.value.Country;
import io.github.nalukit.paniolo.model.common.value.CurrencyCode;
import io.github.nalukit.paniolo.model.common.value.DateTimeQualifier;
import io.github.nalukit.paniolo.model.common.value.DutyTaxFeeCategory;
import io.github.nalukit.paniolo.model.common.value.InvoiceTypeCode;
import io.github.nalukit.paniolo.model.common.value.PaymentMeansCode;
import io.github.nalukit.paniolo.model.common.value.TextSubjectQualifier;
import io.github.nalukit.paniolo.model.common.value.UnitCode;
import io.github.nalukit.paniolo.model.extended.ExtendedInformation;
import io.github.nalukit.paniolo.model.extended.LineExtension;
import io.github.nalukit.paniolo.model.extended.OtherTax;
import io.github.nalukit.paniolo.model.extended.PaymentTerm;
import io.github.nalukit.paniolo.model.extended.PaymentTermsAdjustment;
import io.github.nalukit.paniolo.model.order.OrderTypeCode;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PanioloJsonTest {
  private static final PanioloJson JSON = PanioloJson.create();

  static Invoice invoice() {
    var seller = Party.create()
                      .name("Muster GmbH")
                      .address(Address.create()
                                      .street("Musterstraße 1")
                                      .city("Berlin")
                                      .postalCode("10115")
                                      .country(Country.DE)
                                      .build())
                      .vatId("DE123456788")
                      .electronicAddress(ElectronicAddress.create()
                                                          .value("rechnung@example.org")
                                                          .schemeId("EM")
                                                          .build())
                      .contact(Contact.create()
                                      .name("Erika Muster")
                                      .telephone("+49 30 123456")
                                      .email("rechnung@example.org")
                                      .build())
                      .build();
    var buyer = Party.create()
                     .name("Kunde GmbH")
                     .address(Address.create()
                                     .street("Kundenweg 2")
                                     .city("Hamburg")
                                     .postalCode("20095")
                                     .country(Country.DE)
                                     .build())
                     .electronicAddress(ElectronicAddress.create()
                                                         .value("einkauf@example.org")
                                                         .schemeId("EM")
                                                         .build())
                     .build();
    return Invoice.create()
                  .number("R-2026-1")
                  .issueDate(LocalDate.of(2026,
                                          10,
                                          27))
                  .dueDate(LocalDate.of(2026,
                                        11,
                                        10))
                  .buyerReference("04011000-12345-03")
                  .seller(seller)
                  .buyer(buyer)
                  .delivery(DeliveryInformation.create()
                                               .actualDeliveryDate(LocalDate.of(2026,
                                                                                10,
                                                                                26))
                                               .build())
                  .paymentInstruction(PaymentInstruction.create()
                                                        .meansCode(PaymentMeansCode.CODE_58)
                                                        .addCreditTransfer(CreditTransfer.create()
                                                                                         .iban("DE89370400440532013000")
                                                                                         .accountName("Muster GmbH")
                                                                                         .build())
                                                        .build())
                  .addSupportingDocument(new AdditionalSupportingDocument("Stundenzettel",
                                                                          "Oktober",
                                                                          null,
                                                                          new Attachment(new byte[] { 1,
                                                                                                      2,
                                                                                                      3 },
                                                                                         "application/pdf",
                                                                                         "stunden.pdf")))
                  .addLine(InvoiceLine.create()
                                      .id("1")
                                      .name("Beratung")
                                      .quantity(new BigDecimal("2.5"))
                                      .unitCode("HUR")
                                      .netUnitPrice(new BigDecimal("100.10"))
                                      .tax(TaxCategory.standard(new BigDecimal("19")))
                                      .build())
                  .build();
  }

  @Test
  void keepsTheInsuranceTaxApartFromVat() {
    var tax = OtherTax.insuranceTax("S",
                                    new BigDecimal("19"),
                                    new BigDecimal("137.58"));
    var extended = ExtendedInformation.create()
                                      .addOtherTax(tax)
                                      .build();
    var read = JSON.read(JSON.toJson(extended),
                         ExtendedInformation.class);
    assertEquals(extended,
                 read);
    assertEquals(List.of(tax),
                 read.otherTaxes());
    assertEquals(new BigDecimal("26.14"),
                 read.otherTaxes()
                     .get(0)
                     .amount());
    var line = LineExtension.create()
                            .addOtherTax(tax)
                            .build();
    assertEquals(line,
                 JSON.read(JSON.toJson(line),
                           LineExtension.class));
    // JSON written before other taxes existed has no such member.
    assertEquals(List.of(),
                 JSON.read("{}",
                           ExtendedInformation.class)
                     .otherTaxes());
  }

  @Test
  void keepsTheCalculatedDiscountAmount() {
    var term = new PaymentTerm("Skonto",
                               null,
                               null,
                               null,
                               new PaymentTermsAdjustment(null,
                                                          14,
                                                          new BigDecimal("1190.00"),
                                                          new BigDecimal("3"),
                                                          null).withComputedAmount());
    var read = JSON.read(JSON.toJson(term),
                         PaymentTerm.class);
    assertEquals(term,
                 read);
    assertEquals(new BigDecimal("35.70"),
                 read.discount()
                     .amount());
  }

  @Test
  void storesAndLoadsAnInvoice() {
    var    invoice = invoice();
    String json    = JSON.toJson(invoice);
    assertEquals(invoice,
                 JSON.read(json,
                           Invoice.class));
    assertEquals(invoice,
                 JSON.read(JSON.toBytes(invoice),
                           Invoice.class));
    assertEquals(invoice,
                 JSON.read(new ByteArrayInputStream(JSON.toBytes(invoice)),
                           Invoice.class));
    assertEquals(invoice,
                 JSON.read(JSON.toPrettyJson(invoice),
                           Invoice.class));
    assertEquals(invoice.totals(),
                 JSON.read(json,
                           Invoice.class)
                     .totals());
    // Codes, ISO dates, exact decimals; no derived values and no nulls.
    for (String expected : List.of("\"typeCode\":\"380\"",
                                   "\"unitCode\":\"HUR\"",
                                   "\"currencyCode\":\"EUR\"",
                                   "\"code\":\"S\"",
                                   "\"meansCode\":\"58\"",
                                   "\"country\":\"DE\"",
                                   "\"issueDate\":\"2026-10-27\"",
                                   "\"quantity\":2.5",
                                   "\"netUnitPrice\":100.10",
                                   "\"content\":\"AQID\"")) {
      assertTrue(json.contains(expected),
                 () -> expected + " in " + json);
    }
    for (String absent : List.of("null",
                                 "countryOnly",
                                 "totals",
                                 "payableAmount")) {
      assertFalse(json.contains(absent),
                  () -> absent + " in " + json);
    }
  }

  @Test
  void storesPayeeIdentifierSchemes() {
    var payee = Payee.create()
                     .name("Factoring AG")
                     .identifier("4000001000029",
                                 "0088")
                     .legalRegistrationId("123456782",
                                          "0002")
                     .build();
    // Payee (BG-10) as stored, followed by the payee written by earlier versions without schemes.
    String stored = JSON.toJson(invoice())
                        .replaceFirst("\\{",
                                      "{\"payee\":{\"name\":\"Factoring AG\",\"identifier\":\"4000001000029\",\"identifierSchemeId\":\"0088\",\"legalRegistrationId\":\"123456782\",\"legalRegistrationSchemeId\":\"0002\"},");
    var invoice = JSON.read(stored,
                            Invoice.class);
    assertEquals(payee,
                 invoice.payee());
    String json = JSON.toJson(invoice);
    assertTrue(json.contains("\"identifierSchemeId\":\"0088\""),
               json);
    assertTrue(json.contains("\"legalRegistrationSchemeId\":\"0002\""),
               json);
    assertEquals(invoice,
                 JSON.read(json,
                           Invoice.class));
    // JSON stored before the schemes existed still loads; the schemes are null then.
    String old = json.replace(",\"identifierSchemeId\":\"0088\"",
                              "")
                     .replace(",\"legalRegistrationSchemeId\":\"0002\"",
                              "");
    assertEquals(new Payee("Factoring AG",
                           "4000001000029",
                           "123456782"),
                 JSON.read(old,
                           Invoice.class)
                     .payee());
  }

  @Test
  void writesEveryCodeAsItsCode() {
    var mapper = PanioloJson.configure(tools.jackson.databind.json.JsonMapper.builder())
                            .build();
    for (Enum<?>[] values : new Enum<?>[][] { UnitCode.values(),
                                              CurrencyCode.values(),
                                              InvoiceTypeCode.values(),
                                              PaymentMeansCode.values(),
                                              DutyTaxFeeCategory.values(),
                                              TextSubjectQualifier.values(),
                                              DateTimeQualifier.values(),
                                              AllowanceChargeIdentification.values(),
                                              OrderTypeCode.values(),
                                              Country.values() }) {
      for (Enum<?> value : values) {
        try {
          String json = mapper.writeValueAsString(value);
          Object read = mapper.readValue(json,
                                         value.getDeclaringClass());
          assertEquals(value,
                       read,
                       json);
        } catch (Exception e) {
          throw new AssertionError(value.toString(),
                                   e);
        }
      }
    }
  }

  @Test
  void rejectsInvalidDocumentsWithTheMessageOfTheModel() {
    String json = JSON.toJson(invoice());
    var unknownUnit = assertThrows(IllegalArgumentException.class,
                                   () -> JSON.read(json.replace("\"HUR\"",
                                                                "\"NO-UNIT\""),
                                                   Invoice.class));
    assertTrue(unknownUnit.getMessage()
                          .contains("Unknown unit code: NO-UNIT"),
               unknownUnit::getMessage);
    var unknownProperty = assertThrows(IllegalArgumentException.class,
                                       () -> JSON.read(json.replace("\"number\":",
                                                                    "\"nummer\":\"R-2026-1\",\"number\":"),
                                                       Invoice.class));
    assertTrue(unknownProperty.getMessage()
                              .contains("nummer"),
               unknownProperty::getMessage);
    var model = assertThrows(IllegalArgumentException.class,
                             () -> JSON.read(json.replace("\"postalCode\":\"10115\",",
                                                          "\"postalCode\":\" \","),
                                             Invoice.class));
    assertTrue(model.getMessage()
                    .contains("postalCode must not be blank (Address)"),
               model::getMessage);
    // A missing post code is allowed; EN 16931 requires the country of an address only.
    assertNull(JSON.read(json.replace("\"postalCode\":\"10115\",",
                                      ""),
                         Invoice.class)
                   .seller()
                   .address()
                   .postalCode());
    assertThrows(IllegalArgumentException.class,
                 () -> JSON.read("{",
                                 Invoice.class));
    assertThrows(IllegalArgumentException.class,
                 () -> JSON.read(json + "{}",
                                 Invoice.class));
  }

  @Test
  void loadsHandWrittenJson() {
    // Empty lists and defaults may be left out; decimals may be strings.
    String json = """
                  {
                    "number": "R-2026-2",
                    "issueDate": "2026-10-27",
                    "typeCode": "380",
                    "currencyCode": "EUR",
                    "processControl": {
                      "businessProcessId": "urn:fdc:peppol.eu:2017:poacc:billing:01:1.0",
                      "specificationId": "urn:cen.eu:en16931:2017#compliant#urn:xeinkauf.de:kosit:xrechnung_3.0"
                    },
                    "buyerReference": "04011000-12345-03",
                    "seller": {
                      "name": "Muster GmbH",
                      "address": { "street": "Musterstraße 1", "city": "Berlin", "postalCode": "10115", "country": "DE" },
                      "vatId": "DE123456788"
                    },
                    "buyer": {
                      "name": "Kunde GmbH",
                      "address": { "street": "Kundenweg 2", "city": "Hamburg", "postalCode": "20095", "country": "DE" }
                    },
                    "lines": [
                      {
                        "id": "1",
                        "name": "Beratung",
                        "quantity": 2,
                        "unitCode": "HUR",
                        "netUnitPrice": "100.00",
                        "priceBaseQuantity": 1,
                        "tax": { "code": "S", "rate": 19 }
                      }
                    ],
                    "paidAmount": 0
                  }
                  """;
    var invoice = JSON.read(json,
                            Invoice.class);
    assertEquals(new BigDecimal("238.00"),
                 invoice.totals()
                        .payableAmount());
    assertTrue(invoice.notes()
                      .isEmpty());
    assertEquals(invoice,
                 JSON.read(JSON.toJson(invoice),
                           Invoice.class));
  }

  private static String fixture(String name) {
    try (InputStream input = PanioloJsonTest.class.getResourceAsStream("/json/" + name)) {
      return new String(input.readAllBytes(),
                        StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Test
  void loadsAndWritesStoredJsonUnchanged() {
    // Written by Paniolo 1.0.0-rc01 (Jackson 2): codes, decimals with scale, dates, Base64.
    String stored  = fixture("invoice.json");
    var    invoice = JSON.read(stored,
                               Invoice.class);
    assertEquals(invoice(),
                 invoice);
    assertEquals(stored,
                 JSON.toJson(invoice));
    assertEquals(new BigDecimal("100.10"),
                 invoice.lines()
                        .getFirst()
                        .netUnitPrice());
    assertEquals(new BigDecimal("0.00"),
                 invoice.paidAmount());
    assertArrayEquals(new byte[] { 1,
                                   2,
                                   3 },
                      invoice.supportingDocuments()
                             .getFirst()
                             .attachment()
                             .content());
  }

  @Test
  void readsNullListsAndNullPrimitivesAsDefaults() {
    String stored = fixture("invoice.json")
        .replace("\"notes\":[],",
                 "\"notes\":null,")
        .replace("\"cashDiscounts\":[],",
                 "")
        .replace("\"adjustments\":[],\"supportingDocuments\"",
                 "\"adjustments\":[{\"charge\":null,\"amount\":5.00,\"reason\":\"Rabatt\",\"tax\":{\"code\":\"S\",\"rate\":19}}],\"supportingDocuments\"");
    var invoice = JSON.read(stored,
                            Invoice.class);
    assertTrue(invoice.notes()
                      .isEmpty());
    assertTrue(invoice.cashDiscounts()
                      .isEmpty());
    // A null primitive is its default value, as with Paniolo 1.0.0-rc01: false is an allowance.
    assertFalse(invoice.adjustments()
                       .getFirst()
                       .charge());
  }

  @Test
  void namesTheLocationOfAnError() {
    String stored = fixture("invoice.json");
    var unknownUnit = assertThrows(IllegalArgumentException.class,
                                   () -> JSON.read(stored.replace("\"HUR\"",
                                                                  "\"NO-UNIT\""),
                                                   Invoice.class));
    assertTrue(unknownUnit.getMessage()
                          .startsWith("Invalid Paniolo JSON: Unknown unit code: NO-UNIT at ") &&
               unknownUnit.getMessage()
                          .contains("[\"lines\"]") &&
               unknownUnit.getMessage()
                          .contains("[\"unitCode\"]"),
               unknownUnit::getMessage);
    var unknownProperty = assertThrows(IllegalArgumentException.class,
                                       () -> JSON.read(stored.replace("\"number\":",
                                                                      "\"nummer\":\"R-2026-1\",\"number\":"),
                                                       Invoice.class));
    assertTrue(unknownProperty.getMessage()
                              .contains("[\"nummer\"]"),
               unknownProperty::getMessage);
    var trailing = assertThrows(IllegalArgumentException.class,
                                () -> JSON.read(stored + "\n{}",
                                                Invoice.class));
    assertTrue(trailing.getMessage()
                       .startsWith("Invalid Paniolo JSON: ") &&
               trailing.getMessage()
                       .contains("line 2, column"),
               trailing::getMessage);
    var malformed = assertThrows(IllegalArgumentException.class,
                                 () -> JSON.read("{\n\"number\": ",
                                                 Invoice.class));
    assertTrue(malformed.getMessage()
                        .contains("line 2"),
               malformed::getMessage);
    var notAString = assertThrows(IllegalArgumentException.class,
                                  () -> JSON.read(stored.replace("\"typeCode\":\"380\"",
                                                                 "\"typeCode\":380"),
                                                  Invoice.class));
    assertTrue(notAString.getMessage()
                         .contains("A invoice type code must be a string") &&
               notAString.getMessage()
                         .contains("[\"typeCode\"]"),
               notAString::getMessage);
    var model = assertThrows(IllegalArgumentException.class,
                             () -> JSON.read(stored.replace("\"postalCode\":\"10115\",",
                                                            "\"postalCode\":\" \",")
                                                   .getBytes(StandardCharsets.UTF_8),
                                             Invoice.class));
    assertEquals("postalCode must not be blank (Address)",
                 model.getMessage());
  }

  @Test
  void reportsAFailingStreamAsIoError() {
    var failing = new InputStream() {
      @Override
      public int read()
          throws IOException {
        throw new IOException("disk gone");
      }
    };
    var error = assertThrows(UncheckedIOException.class,
                             () -> JSON.read(failing,
                                             Invoice.class));
    assertEquals("disk gone",
                 error.getCause()
                      .getMessage());
    assertInstanceOf(IOException.class,
                     error.getCause());
  }
}

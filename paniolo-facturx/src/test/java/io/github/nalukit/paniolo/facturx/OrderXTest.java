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
package io.github.nalukit.paniolo.facturx;

import io.github.nalukit.paniolo.Paniolo;
import io.github.nalukit.paniolo.config.FacturXProfile;
import io.github.nalukit.paniolo.config.PdfAttachment;
import io.github.nalukit.paniolo.model.common.Address;
import io.github.nalukit.paniolo.model.common.ElectronicAddress;
import io.github.nalukit.paniolo.model.common.Party;
import io.github.nalukit.paniolo.model.common.TaxCategory;
import io.github.nalukit.paniolo.model.common.value.Country;
import io.github.nalukit.paniolo.model.common.value.DutyTaxFeeCategory;
import io.github.nalukit.paniolo.model.common.value.InvoiceTypeCode;
import io.github.nalukit.paniolo.model.common.value.UnitCode;
import io.github.nalukit.paniolo.model.extended.DocumentReference;
import io.github.nalukit.paniolo.model.order.OrderDetails;
import io.github.nalukit.paniolo.model.order.OrderLine;
import io.github.nalukit.paniolo.model.order.OrderLineDetails;
import io.github.nalukit.paniolo.model.order.OrderTypeCode;
import io.github.nalukit.paniolo.model.order.OrderXProfile;
import io.github.nalukit.paniolo.model.order.PackageDimensions;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.verapdf.pdfa.flavours.PDFAFlavour;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderXTest {
  private static final Paniolo PANIOLO = Paniolo.create();

  @Test
  void readsDateTimesAndAddressesWithoutScheme() {
    var ship = Party.create()
                    .name("Lager")
                    .address(Address.create()
                                    .street("Hafenstr. 1")
                                    .city("Hamburg")
                                    .postalCode("20457")
                                    .country(Country.DE)
                                    .build())
                    .electronicAddress(new ElectronicAddress("lager@example.org",
                                                             "EM"))
                    .build();
    var order = OrderSamples.comfort(OrderTypeCode.ORDER)
                            .shipTo(ship)
                            .build();
    String xml = PANIOLO.toOrderX(order);
    String date = order.issueDate()
                       .format(DateTimeFormatter.BASIC_ISO_DATE);
    // Order-X allows date-times (format 203) and electronic addresses without scheme.
    String changed = xml.replace("<udt:DateTimeString format=\"102\">" + date + "</udt:DateTimeString>",
                                 "<udt:DateTimeString format=\"203\">" + date + "1232</udt:DateTimeString>")
                        .replaceFirst("<ram:URIUniversalCommunication>(\\s*)<ram:URIID schemeID=\"[^\"]*\">",
                                      "<ram:URIUniversalCommunication>$1<ram:URIID>");
    assertTrue(!changed.equals(xml) && changed.contains("format=\"203\""),
               changed);
    byte[] bytes = changed.getBytes(StandardCharsets.UTF_8);
    var lenient = PANIOLO.readOrder(bytes,
                                    true);
    assertEquals(order.issueDate(),
                 lenient.issueDate());
    // The time of day is kept in the details and written again in format 203.
    assertEquals(LocalTime.of(12,
                              32),
                 lenient.details()
                        .issueTime());
    assertTrue(PANIOLO.toOrderX(lenient)
                      .contains("format=\"203\">" + date + "1232<"));
    // The address keeps no scheme; strict reading loses nothing.
    var strict = PANIOLO.readOrder(bytes);
    assertEquals(lenient,
                 strict);
    assertNull(strict.shipTo()
                     .electronicAddress()
                     .schemeId());
    // Invoices require the scheme (BT-49-1).
    assertThrows(IllegalArgumentException.class,
                 () -> FacturXInvoices.invoice(FacturXProfile.EN16931,
                                               InvoiceTypeCode.CODE_380)
                                      .buyer(strict.shipTo())
                                      .build());
  }

  @ParameterizedTest
  @EnumSource(OrderTypeCode.class)
  void writesAndReadsOrdersOfBothProfiles(OrderTypeCode type) {
    for (var order : List.of(OrderSamples.basic(type)
                                         .build(),
                             OrderSamples.comfort(type)
                                         .build(),
                             OrderSamples.comfort(type)
                                         .profile(OrderXProfile.EXTENDED)
                                         .build())) {
      String xml = PANIOLO.toOrderX(order);
      assertTrue(xml.contains("<ram:TypeCode>" + type.getCode() + "</ram:TypeCode>"),
                 xml);
      assertTrue(xml.contains(order.profile()
                                   .getSpecificationId()),
                 xml);
      assertEquals(order,
                   PANIOLO.readOrder(xml));
      assertEquals(order,
                   PANIOLO.readOrder(xml.getBytes(StandardCharsets.UTF_8)));
    }
  }

  @Test
  void calculatesTotals() {
    var basic = OrderSamples.basic(OrderTypeCode.ORDER)
                            .build()
                            .totals();
    // 10 x 100.00 + 25 x 2.50; BASIC lines have no VAT category.
    assertEquals(new BigDecimal("1062.50"),
                 basic.taxBasisTotal());
    assertNull(basic.taxAmount());
    var comfort = OrderSamples.comfort(OrderTypeCode.ORDER)
                              .build()
                              .totals();
    // 1000.00 + 62.50 - 2.50 line allowance, minus 20.00 document allowance, 19 % VAT.
    assertEquals(new BigDecimal("1060.00"),
                 comfort.lineTotal());
    assertEquals(new BigDecimal("1040.00"),
                 comfort.taxBasisTotal());
    assertEquals(new BigDecimal("197.60"),
                 comfort.taxAmount());
    assertEquals(new BigDecimal("1237.60"),
                 comfort.grandTotal());
  }

  @Test
  void basicRejectsComfortContent() {
    var order = OrderSamples.comfort(OrderTypeCode.ORDER)
                            .profile(OrderXProfile.BASIC)
                            .build();
    var error = assertThrows(IllegalArgumentException.class,
                             () -> PANIOLO.toOrderX(order));
    for (String expected : List.of("invoicee",
                                   "payment terms",
                                   "document allowances and charges",
                                   "line 1 VAT category",
                                   "line 1 gross price",
                                   "line 2 allowances and charges",
                                   "buyer requisitioner",
                                   "project",
                                   "line 1 substituted product",
                                   "line 1 classifications",
                                   "line 1 requested despatch date")) {
      assertTrue(error.getMessage()
                      .contains(expected),
                 () -> expected + " missing in " + error.getMessage());
    }
  }

  @Test
  void rejectsContentTheModelWouldLose() {
    String xml = PANIOLO.toOrderX(OrderSamples.comfort(OrderTypeCode.ORDER)
                                              .build());
    // Order-X numbers allowances and charges; the model does not.
    String unsupported = xml.replaceFirst("</ram:ChargeIndicator>",
                                          "</ram:ChargeIndicator><ram:SequenceNumeric>1</ram:SequenceNumeric>");
    var error = assertThrows(IllegalArgumentException.class,
                             () -> PANIOLO.readOrder(unsupported));
    assertTrue(error.getMessage()
                    .contains("SequenceNumeric"),
               error::getMessage);
    // Read leniently, the order loses only the unsupported sequence number.
    assertEquals(OrderSamples.comfort(OrderTypeCode.ORDER)
                             .build(),
                 PANIOLO.readOrder(unsupported.getBytes(StandardCharsets.UTF_8),
                                   true));
    // A stated line amount that is not quantity x price is reported too.
    String stated = PANIOLO.toOrderX(OrderSamples.basic(OrderTypeCode.ORDER)
                                                 .build())
                           .replace("<ram:LineTotalAmount>62.50</ram:LineTotalAmount>",
                                    "<ram:LineTotalAmount>62.00</ram:LineTotalAmount>");
    assertThrows(IllegalArgumentException.class,
                 () -> PANIOLO.readOrder(stated));
  }

  @Test
  void basicRejectsComfortDetails() {
    var order = OrderSamples.basic(OrderTypeCode.ORDER)
                            .details(OrderDetails.create()
                                                 .copyIndicator(false)
                                                 .deliveryTermsFunctionCode("7")
                                                 .deliveryTermsLocationId("DE-HAM")
                                                 .build())
                            .addLine(OrderLine.create()
                                              .id("99")
                                              .name("Kiste")
                                              .quantity(BigDecimal.ONE)
                                              .unitCode("H87")
                                              .netUnitPrice(BigDecimal.TEN)
                                              .details(OrderLineDetails.create()
                                                                       .packageDimensions(new PackageDimensions(new BigDecimal("40"),
                                                                                                                UnitCode.of("CMT"),
                                                                                                                null,
                                                                                                                null,
                                                                                                                null,
                                                                                                                null))
                                                                       .build())
                                              .build())
                            .build();
    var error = assertThrows(IllegalArgumentException.class,
                             () -> PANIOLO.toOrderX(order));
    // Copy indicator and delivery terms function are BASIC content.
    assertEquals("Order-X profile BASIC does not allow: delivery terms location, line 99 package dimensions",
                 error.getMessage());
  }

  @Test
  void checksTheOrderModel() {
    assertThrows(IllegalArgumentException.class,
                 () -> OrderSamples.basic(OrderTypeCode.ORDER)
                                   .quotationReference(new DocumentReference("ANG-7",
                                                                             LocalDate.of(2026,
                                                                                          10,
                                                                                          1)))
                                   .build());
    assertThrows(IllegalArgumentException.class,
                 () -> OrderLine.create()
                                .id("1")
                                .quantity(BigDecimal.ONE)
                                .unitCode("H87")
                                .netUnitPrice(BigDecimal.TEN)
                                .tax(new TaxCategory(DutyTaxFeeCategory.E,
                                                     BigDecimal.ZERO,
                                                     "Steuerbefreit",
                                                     null))
                                .name("Leistung")
                                .build());
    assertThrows(IllegalArgumentException.class,
                 () -> OrderLine.create()
                                .id("1")
                                .quantity(BigDecimal.ONE)
                                .unitCode("H87")
                                .netUnitPrice(BigDecimal.TEN)
                                .build());
    var line = OrderSamples.basic(OrderTypeCode.ORDER)
                           .build()
                           .lines()
                           .get(0);
    assertThrows(IllegalArgumentException.class,
                 () -> OrderSamples.basic(OrderTypeCode.ORDER)
                                   .addLine(line)
                                   .build());
  }

  @Test
  void createsAndReadsOrderXPdf()
      throws Exception {
    var order = OrderSamples.comfort(OrderTypeCode.ORDER)
                            .build();
    byte[] pdf = PANIOLO.toOrderXPdf(order,
                                     TestPdfs.pdfA2b(),
                                     List.of(PdfAttachment.of("zeichnung.pdf",
                                                              TestPdfs.pdfA2b(),
                                                              "application/pdf")));
    assertEquals(List.of(),
                 TestPdfs.pdfaFailures(pdf,
                                       PDFAFlavour.PDFA_3_B));
    assertEquals(order,
                 PANIOLO.readOrder(pdf));
    assertEquals(List.of("zeichnung.pdf"),
                 PANIOLO.attachments(pdf)
                        .stream()
                        .map(PdfAttachment::fileName)
                        .toList());
    try (PDDocument document = Loader.loadPDF(pdf)) {
      String xmp = new String(document.getDocumentCatalog()
                                      .getMetadata()
                                      .toByteArray(),
                              StandardCharsets.UTF_8);
      assertTrue(xmp.contains("<fx:DocumentType>ORDER</fx:DocumentType>"),
                 xmp);
      assertTrue(xmp.contains("<fx:DocumentFileName>order-x.xml</fx:DocumentFileName>"),
                 xmp);
      assertTrue(xmp.contains("<fx:ConformanceLevel>COMFORT</fx:ConformanceLevel>"),
                 xmp);
    }
    // The packaged Order-X rules check the XML.
    var result = PANIOLO.validateOrder(pdf);
    assertTrue(result.isValid(),
               () -> result.messages()
                           .toString());
    assertTrue(result.profileName()
                     .endsWith("Order-X COMFORT"),
               result::profileName);
    // A PDF carries either an order or an invoice.
    assertThrows(IllegalArgumentException.class,
                 () -> PANIOLO.toFacturX(FacturXInvoices.invoice(FacturXProfile.EN16931,
                                                                 InvoiceTypeCode.CODE_380)
                                                        .build(),
                                         pdf,
                                         FacturXProfile.EN16931));
    assertThrows(IllegalArgumentException.class,
                 () -> PANIOLO.readOrder(PANIOLO.toFacturX(FacturXInvoices.invoice(FacturXProfile.EN16931,
                                                                                   InvoiceTypeCode.CODE_380)
                                                                          .build(),
                                                           TestPdfs.pdfA2b(),
                                                           FacturXProfile.EN16931)));
  }

  @Test
  void rejectsOtherDocumentsForOrderValidation() {
    assertThrows(IllegalArgumentException.class,
                 () -> PANIOLO.validateOrder(FacturXValidationTest.sample("minimum.xml")));
  }
}

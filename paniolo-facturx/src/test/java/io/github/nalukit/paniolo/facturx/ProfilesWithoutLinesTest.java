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
import io.github.nalukit.paniolo.config.InvoiceSyntax;
import io.github.nalukit.paniolo.model.Invoice;
import io.github.nalukit.paniolo.model.common.Address;
import io.github.nalukit.paniolo.model.common.Contact;
import io.github.nalukit.paniolo.model.common.CreditTransfer;
import io.github.nalukit.paniolo.model.common.DeliveryInformation;
import io.github.nalukit.paniolo.model.common.DocumentAdjustment;
import io.github.nalukit.paniolo.model.common.DocumentTotals;
import io.github.nalukit.paniolo.model.common.Party;
import io.github.nalukit.paniolo.model.common.PaymentInstruction;
import io.github.nalukit.paniolo.model.common.ProcessControl;
import io.github.nalukit.paniolo.model.common.References;
import io.github.nalukit.paniolo.model.common.TaxCategory;
import io.github.nalukit.paniolo.model.common.value.Country;
import io.github.nalukit.paniolo.model.common.value.PaymentMeansCode;
import io.github.nalukit.paniolo.xml.cii.CiiInvoiceReader;
import io.github.nalukit.paniolo.xml.cii.CiiInvoiceWriter;
import io.github.nalukit.paniolo.xml.ubl.UblInvoiceWriter;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.verapdf.pdfa.flavours.PDFAFlavour;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Writes and reads the Factur-X profiles MINIMUM and BASIC WL, which carry no invoice lines.
 */
class ProfilesWithoutLinesTest {
  private static final Paniolo     PANIOLO = Paniolo.create();
  private static final TaxCategory VAT     = TaxCategory.standard(new BigDecimal("19"));

  static Invoice minimum() {
    return Invoice.create()
                  .number("M-1")
                  .issueDate(LocalDate.of(2026,
                                          10,
                                          27))
                  .processControl(ProcessControl.facturXMinimum())
                  .buyerReference("04011000-12345-03")
                  .seller(Party.create()
                               .name("Muster GmbH")
                               .address(Address.ofCountry(Country.DE))
                               .vatId("DE123456788")
                               .legalRegistrationId("HRB 12345 B")
                               .build())
                  .buyer(Party.create()
                              .name("Kunde GmbH")
                              .build())
                  .references(References.create()
                                        .purchaseOrderReference("PO-7")
                                        .build())
                  .statedTotals(DocumentTotals.create()
                                              .taxBasisTotal(new BigDecimal("200"))
                                              .taxTotal(new BigDecimal("38"))
                                              .build())
                  .build();
  }

  static Invoice basicWl() {
    var seller = Party.create()
                      .name("Muster GmbH")
                      .address(Address.create()
                                      .street("Musterstraße 1")
                                      .city("Berlin")
                                      .postalCode("10115")
                                      .country(Country.DE)
                                      .build())
                      .vatId("DE123456788")
                      .build();
    var buyer = Party.create()
                     .name("Kunde GmbH")
                     .address(Address.create()
                                     .street("Kundenweg 2")
                                     .city("Hamburg")
                                     .postalCode("20095")
                                     .country(Country.DE)
                                     .build())
                     .build();
    return Invoice.create()
                  .number("W-1")
                  .issueDate(LocalDate.of(2026,
                                          10,
                                          27))
                  .processControl(ProcessControl.facturXBasicWl())
                  .buyerReference("04011000-12345-03")
                  .seller(seller)
                  .buyer(buyer)
                  .delivery(DeliveryInformation.create()
                                               .actualDeliveryDate(LocalDate.of(2026,
                                                                                10,
                                                                                26))
                                               .build())
                  .dueDate(LocalDate.of(2026,
                                        11,
                                        10))
                  .paymentInstruction(PaymentInstruction.create()
                                                        .meansCode(PaymentMeansCode.CODE_58)
                                                        .addCreditTransfer(CreditTransfer.create()
                                                                                         .iban("DE89370400440532013000")
                                                                                         .build())
                                                        .build())
                  .addAdjustment(DocumentAdjustment.createAllowance()
                                                   .amount(new BigDecimal("10"))
                                                   .reason("Rabatt")
                                                   .tax(VAT)
                                                   .build())
                  .paidAmount(new BigDecimal("38"))
                  .statedTotals(DocumentTotals.create()
                                              .lineTotal(new BigDecimal("210"))
                                              .addVatBreakdown(VAT,
                                                               new BigDecimal("200"),
                                                               new BigDecimal("38"))
                                              .build())
                  .build();
  }

  @ParameterizedTest
  @EnumSource(value = FacturXProfile.class,
              names = { "MINIMUM",
                        "BASIC_WL" })
  void createsValidFacturXAndReadsItBack(FacturXProfile profile)
      throws Exception {
    var invoice = profile == FacturXProfile.MINIMUM ?
                  minimum() :
                  basicWl();
    byte[] pdf = PANIOLO.toFacturX(invoice,
                                   TestPdfs.pdfA2b(),
                                   profile);
    assertEquals(List.of(),
                 TestPdfs.pdfaFailures(pdf,
                                       PDFAFlavour.PDFA_3_B));
    var result = PANIOLO.validate(pdf);
    assertTrue(result.isValid(),
               () -> result.messages()
                           .toString());
    assertEquals(invoice,
                 PANIOLO.read(pdf));
    assertEquals(profile,
                 new PdfBoxFacturXSupport().extract(pdf)
                                           .profile());
    try (PDDocument document = Loader.loadPDF(pdf)) {
      COSArray associated = document.getDocumentCatalog()
                                    .getCOSObject()
                                    .getCOSArray(COSName.getPDFName("AF"));
      // Germany requires the relationship Data for documents without lines.
      assertEquals("Data",
                   ((COSDictionary) associated.getObject(0)).getNameAsString(COSName.getPDFName("AFRelationship")));
      String xmp = new String(document.getDocumentCatalog()
                                      .getMetadata()
                                      .toByteArray(),
                              StandardCharsets.UTF_8);
      assertTrue(xmp.contains("<fx:ConformanceLevel>" + profile.getConformanceLevel() + "</fx:ConformanceLevel>"));
    }
  }

  @Test
  void writesOnlyTheElementsOfMinimum() {
    String xml = new CiiInvoiceWriter().toXml(minimum());
    assertTrue(xml.contains("<ram:ID schemeID=\"VA\">DE123456788</ram:ID>"),
               xml);
    assertTrue(xml.contains("<ram:DuePayableAmount>238.00</ram:DuePayableAmount>"),
               xml);
    for (String absent : List.of("LineTotalAmount",
                                 "ApplicableTradeTax",
                                 "TotalPrepaidAmount",
                                 "LineOne",
                                 "IncludedSupplyChainTradeLineItem")) {
      assertFalse(xml.contains(absent),
                  absent);
    }
  }

  @Test
  void minimumImpliesThePaidAmountFromTheAmountDue() {
    var invoice = Invoice.create()
                         .number(minimum().number())
                         .issueDate(minimum().issueDate())
                         .processControl(ProcessControl.facturXMinimum())
                         .seller(minimum().seller())
                         .buyer(minimum().buyer())
                         .paidAmount(new BigDecimal("100"))
                         .statedTotals(minimum().statedTotals())
                         .build();
    String xml = new CiiInvoiceWriter().toXml(invoice);
    assertTrue(xml.contains("<ram:DuePayableAmount>138.00</ram:DuePayableAmount>"),
               xml);
    assertEquals(invoice,
                 new CiiInvoiceReader().fromXml(xml));
  }

  @ParameterizedTest
  @CsvSource({ "minimum.xml, MINIMUM",
               "basic-wl.xml, BASIC_WL" })
  void readsSamplesAndWritesThemAgain(String sample,
                                      FacturXProfile profile) {
    var invoice = PANIOLO.read(FacturXValidationTest.sample(sample));
    assertTrue(invoice.hasStatedTotals());
    assertEquals(profile.getSpecificationId(),
                 invoice.processControl()
                        .specificationId());
    assertEquals(new BigDecimal("238.00"),
                 invoice.totals()
                        .payableAmount());
    assertEquals(invoice,
                 PANIOLO.read(PANIOLO.toXml(invoice,
                                            InvoiceSyntax.CII)));
  }

  @Test
  void rejectsContentTheProfilesDoNotAllow() {
    var withContact = Invoice.create()
                             .number("M-2")
                             .issueDate(LocalDate.of(2026,
                                                     10,
                                                     27))
                             .processControl(ProcessControl.facturXMinimum())
                             .seller(Party.create()
                                          .name("Muster GmbH")
                                          .address(Address.ofCountry(Country.DE))
                                          .contact(Contact.create()
                                                          .name("Erika Muster")
                                                          .build())
                                          .build())
                             .buyer(minimum().buyer())
                             .dueDate(LocalDate.of(2026,
                                                   11,
                                                   10))
                             .statedTotals(minimum().statedTotals())
                             .build();
    var error = assertThrows(IllegalArgumentException.class,
                             () -> new CiiInvoiceWriter().toXml(withContact));
    assertTrue(error.getMessage()
                    .contains("seller contact (BG-6)"),
               error.getMessage());
    assertTrue(error.getMessage()
                    .contains("payment due date (BT-9)"),
               error.getMessage());

    // BASIC WL needs the line total and the VAT breakdown, and has the BASIC header restrictions.
    var withoutBreakdown = Invoice.create()
                                  .number("W-2")
                                  .issueDate(LocalDate.of(2026,
                                                          10,
                                                          27))
                                  .processControl(ProcessControl.facturXBasicWl())
                                  .seller(basicWl().seller())
                                  .buyer(basicWl().buyer())
                                  .statedTotals(minimum().statedTotals())
                                  .build();
    assertThrows(IllegalArgumentException.class,
                 () -> new CiiInvoiceWriter().toXml(withoutBreakdown));

    // Stated totals belong to MINIMUM and BASIC WL only, and UBL has no such documents.
    var en16931 = Invoice.create()
                         .number("E-1")
                         .issueDate(LocalDate.of(2026,
                                                 10,
                                                 27))
                         .processControl(ProcessControl.en16931())
                         .seller(basicWl().seller())
                         .buyer(basicWl().buyer())
                         .statedTotals(minimum().statedTotals())
                         .build();
    assertThrows(IllegalArgumentException.class,
                 () -> new CiiInvoiceWriter().toXml(en16931));
    assertThrows(IllegalArgumentException.class,
                 () -> new UblInvoiceWriter().toXml(basicWl()));
    assertThrows(IllegalArgumentException.class,
                 () -> PANIOLO.toFacturX(basicWl(),
                                         TestPdfs.pdfA2b(),
                                         FacturXProfile.MINIMUM));
  }
}

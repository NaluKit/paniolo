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
import io.github.nalukit.paniolo.model.common.Party;
import io.github.nalukit.paniolo.model.common.value.Country;
import io.github.nalukit.paniolo.model.common.value.InvoiceTypeCode;
import io.github.nalukit.paniolo.model.despatch.DespatchAdvice;
import io.github.nalukit.paniolo.model.despatch.DespatchItemInstance;
import io.github.nalukit.paniolo.model.despatch.DespatchLine;
import io.github.nalukit.paniolo.validation.ValidationMessage;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Test;
import org.verapdf.pdfa.flavours.PDFAFlavour;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Embeds CIDA despatch advices into PDF/A-3 delivery notes and reads them back.
 */
class DespatchAdvicePdfTest {
  private static final Paniolo PANIOLO = Paniolo.create();

  private static Party party(String name,
                             String street,
                             String city,
                             String postalCode) {
    return Party.create()
                .name(name)
                .address(Address.create()
                                .street(street)
                                .city(city)
                                .postalCode(postalCode)
                                .country(Country.DE)
                                .build())
                .build();
  }

  static DespatchAdvice advice() {
    return DespatchAdvice.create()
                         .number("LS-2026-42")
                         .issueDate(LocalDate.of(2026,
                                                 11,
                                                 9))
                         .addOrderReference("B-2026-17")
                         .despatchSupplier(party("Muster GmbH",
                                                 "Musterstraße 1",
                                                 "Berlin",
                                                 "10115"))
                         .deliveryCustomer(party("Kunde GmbH",
                                                 "Hafenstraße 5",
                                                 "Hamburg",
                                                 "20457"))
                         .buyer(party("Kunde GmbH",
                                      "Kundenweg 2",
                                      "Hamburg",
                                      "20095"))
                         .actualDespatchDate(LocalDate.of(2026,
                                                          11,
                                                          9))
                         .addLine(DespatchLine.create()
                                              .id("1")
                                              .deliveredQuantity(new BigDecimal("8"))
                                              .unitCode("H87")
                                              .orderLineId("1")
                                              .itemName("Gerät")
                                              .addInstance(DespatchItemInstance.serial("SN-1"))
                                              .build())
                         .build();
  }

  @Test
  void createsAndReadsADeliveryNote()
      throws Exception {
    var advice = advice();
    byte[] pdf = PANIOLO.toDespatchAdvicePdf(advice,
                                             TestPdfs.pdfA2b(),
                                             List.of(PdfAttachment.of("lieferbedingungen.pdf",
                                                                      TestPdfs.pdfA2b(),
                                                                      "application/pdf")));
    assertEquals(List.of(),
                 TestPdfs.pdfaFailures(pdf,
                                       PDFAFlavour.PDFA_3_B));
    assertEquals(advice,
                 PANIOLO.readDespatchAdvice(pdf));
    assertEquals(List.of("lieferbedingungen.pdf"),
                 PANIOLO.attachments(pdf)
                        .stream()
                        .map(PdfAttachment::fileName)
                        .toList());
    try (PDDocument document = Loader.loadPDF(pdf)) {
      String xmp = new String(document.getDocumentCatalog()
                                      .getMetadata()
                                      .toByteArray(),
                              StandardCharsets.UTF_8);
      for (String expected : List.of("urn:factur-x:pdfa:CrossIndustryDocument:despatchadvice:1p0#",
                                     "<fx:DocumentType>DESPATCHADVICE</fx:DocumentType>",
                                     "<fx:DocumentFileName>cida.xml</fx:DocumentFileName>",
                                     "<fx:Version>1.0</fx:Version>",
                                     "<fx:ConformanceLevel>PILOT</fx:ConformanceLevel>")) {
        assertTrue(xmp.contains(expected),
                   xmp);
      }
      COSArray associated = document.getDocumentCatalog()
                                    .getCOSObject()
                                    .getCOSArray(COSName.getPDFName("AF"));
      var cida = (COSDictionary) associated.getObject(0);
      assertEquals("cida.xml",
                   cida.getString(COSName.F));
      assertEquals("Alternative",
                   cida.getNameAsString(COSName.getPDFName("AFRelationship")));
    }
    var result = PANIOLO.validateDespatchAdvice(pdf);
    assertTrue(result.isValid(),
               () -> result.messages()
                           .toString());
    assertTrue(result.messages()
                     .stream()
                     .map(ValidationMessage::ruleId)
                     .toList()
                     .contains("CIDA-NOT-CHECKED"),
               () -> result.messages()
                           .toString());
  }

  @Test
  void keepsInvoicesOrdersAndDeliveryNotesApart() {
    byte[] pdf = PANIOLO.toDespatchAdvicePdf(advice(),
                                             TestPdfs.pdfA2b());
    // A PDF carries one structured document.
    assertThrows(IllegalArgumentException.class,
                 () -> PANIOLO.toFacturX(FacturXInvoices.invoice(FacturXProfile.EN16931,
                                                                 InvoiceTypeCode.CODE_380)
                                                        .build(),
                                         pdf,
                                         FacturXProfile.EN16931));
    assertThrows(IllegalArgumentException.class,
                 () -> PANIOLO.toDespatchAdvicePdf(advice(),
                                                   pdf));
    byte[] invoice = PANIOLO.toFacturX(FacturXInvoices.invoice(FacturXProfile.EN16931,
                                                               InvoiceTypeCode.CODE_380)
                                                      .build(),
                                       TestPdfs.pdfA2b(),
                                       FacturXProfile.EN16931);
    assertThrows(IllegalArgumentException.class,
                 () -> PANIOLO.readDespatchAdvice(invoice));
    // cida.xml is reserved for the despatch advice.
    assertThrows(IllegalArgumentException.class,
                 () -> PdfAttachment.of("cida.xml",
                                        new byte[] { 1 },
                                        "text/xml"));
  }
}

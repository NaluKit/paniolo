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
package io.github.nalukit.paniolo.benchmark;

import io.github.nalukit.paniolo.config.PdfAttachment;
import io.github.nalukit.paniolo.model.Invoice;
import io.github.nalukit.paniolo.model.common.Address;
import io.github.nalukit.paniolo.model.common.Contact;
import io.github.nalukit.paniolo.model.common.CreditTransfer;
import io.github.nalukit.paniolo.model.common.DeliveryInformation;
import io.github.nalukit.paniolo.model.common.ElectronicAddress;
import io.github.nalukit.paniolo.model.common.InvoiceLine;
import io.github.nalukit.paniolo.model.common.InvoicePeriod;
import io.github.nalukit.paniolo.model.common.Party;
import io.github.nalukit.paniolo.model.common.PaymentInstruction;
import io.github.nalukit.paniolo.model.common.TaxCategory;
import io.github.nalukit.paniolo.model.common.value.Country;
import io.github.nalukit.paniolo.model.common.value.InvoiceTypeCode;
import io.github.nalukit.paniolo.model.common.value.PaymentMeansCode;
import io.github.nalukit.paniolo.validation.ValidationResult;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDMetadata;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.color.PDOutputIntent;

import java.awt.color.ColorSpace;
import java.awt.color.ICC_Profile;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.SplittableRandom;

/**
 * Valid XRechnung test invoices of a given size and a minimal PDF/A-2b document.
 */
final class BenchmarkData {
  private BenchmarkData() {
  }

  /**
   * An XRechnung invoice with the given number of lines and two VAT rates.
   */
  static Invoice invoice(int lines) {
    return invoice(lines,
                   false);
  }

  /**
   * An XRechnung invoice with the given number of lines and two VAT rates; with
   * {@code linePeriods} every line states its own invoicing period (BG-26), so reading the
   * invoice parses two dates per line.
   */
  static Invoice invoice(int lines,
                         boolean linePeriods) {
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
    var builder = Invoice.create()
                         .number("R-" + lines)
                         .typeCode(InvoiceTypeCode.CODE_380)
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
                                                                                                .bic("COBADEFFXXX")
                                                                                                .build())
                                                               .remittanceInformation("R-" + lines)
                                                               .build());
    var standard = TaxCategory.standard(new BigDecimal("19"));
    var reduced  = TaxCategory.standard(new BigDecimal("7"));
    for (int i = 1; i <= lines; i++) {
      builder.addLine(InvoiceLine.create()
                                 .id(Integer.toString(i))
                                 .name("Position " + i)
                                 .quantity(new BigDecimal(1 + i % 5))
                                 .unitCode("HUR")
                                 .netUnitPrice(new BigDecimal("100.00").add(new BigDecimal(i % 50)))
                                 .tax(i % 3 == 0 ?
                                      reduced :
                                      standard)
                                 .period(linePeriods ?
                                         new InvoicePeriod(LocalDate.of(2026,
                                                                        9,
                                                                        1 + i % 28),
                                                           LocalDate.of(2026,
                                                                        10,
                                                                        1 + i % 28)) :
                                         null)
                                 .build());
    }
    return builder.build();
  }

  /**
   * Fails the benchmark setup if the data is not valid, so invalid documents cannot shorten the
   * measured path (validation stops after XSD errors).
   */
  static void requireValid(ValidationResult result,
                           String what) {
    if (!result.isValid()) {
      throw new IllegalStateException(what + " is not valid: " + result.messages());
    }
  }

  /**
   * Fails the benchmark setup unless PDF/A conformance is checked as expected, so results with and
   * without the PDF/A stage are never mixed up; the stage depends on paniolo-pdfa being on the class
   * path.
   */
  static void requirePdfaChecked(ValidationResult result,
                                 boolean expected,
                                 String what) {
    boolean checked = result.messages()
                            .stream()
                            .noneMatch(message -> "PDFA-NOT-CHECKED".equals(message.ruleId()));
    if (checked != expected) {
      throw new IllegalStateException(what + (expected ?
                                              " was not checked for PDF/A: run it with the jars of target/pdfa-lib on the class path, see README.md" :
                                              " was checked for PDF/A: run it without the jars of target/pdfa-lib on the class path"));
    }
  }

  /**
   * A supplementary file of the given size whose content does not compress, so it grows the PDF by
   * about its size.
   */
  static PdfAttachment attachment(int kilobytes) {
    byte[] content = new byte[kilobytes * 1024];
    new SplittableRandom(kilobytes).nextBytes(content);
    return PdfAttachment.of("anlage-" + kilobytes + "kb.bin",
                            content,
                            "application/octet-stream");
  }

  /**
   * A minimal PDF/A-2b document without text, so no font files are needed.
   */
  static byte[] pdfA2b() {
    try (var document = new PDDocument()) {
      document.setVersion(1.7f);
      var page = new PDPage(PDRectangle.A4);
      document.addPage(page);
      try (var content = new PDPageContentStream(document,
                                                 page)) {
        content.setNonStrokingColor(0.2f,
                                    0.4f,
                                    0.6f);
        content.addRect(50,
                        700,
                        495,
                        60);
        content.fill();
      }
      Calendar created = new GregorianCalendar(2026,
                                               Calendar.OCTOBER,
                                               27,
                                               10,
                                               0,
                                               0);
      var info = document.getDocumentInformation();
      info.setTitle("Rechnung");
      info.setCreationDate(created);
      info.setModificationDate(created);
      byte[] icc = ICC_Profile.getInstance(ColorSpace.CS_sRGB)
                              .getData();
      var intent = new PDOutputIntent(document,
                                      new ByteArrayInputStream(icc));
      intent.setInfo("sRGB IEC61966-2.1");
      intent.setOutputCondition("sRGB IEC61966-2.1");
      intent.setOutputConditionIdentifier("sRGB IEC61966-2.1");
      intent.setRegistryName("http://www.color.org");
      document.getDocumentCatalog()
              .addOutputIntent(intent);
      var metadata = new PDMetadata(document);
      metadata.importXMPMetadata("""
                                 <?xpacket begin="﻿" id="W5M0MpCehiHzreSzNTczkc9d"?>
                                 <x:xmpmeta xmlns:x="adobe:ns:meta/">
                                  <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
                                   <rdf:Description rdf:about="" xmlns:pdfaid="http://www.aiim.org/pdfa/ns/id/">
                                    <pdfaid:part>2</pdfaid:part>
                                    <pdfaid:conformance>B</pdfaid:conformance>
                                   </rdf:Description>
                                   <rdf:Description rdf:about="" xmlns:dc="http://purl.org/dc/elements/1.1/">
                                    <dc:title><rdf:Alt><rdf:li xml:lang="x-default">Rechnung</rdf:li></rdf:Alt></dc:title>
                                   </rdf:Description>
                                   <rdf:Description rdf:about="" xmlns:xmp="http://ns.adobe.com/xap/1.0/">
                                    <xmp:CreateDate>2026-10-27T10:00:00+01:00</xmp:CreateDate>
                                    <xmp:ModifyDate>2026-10-27T10:00:00+01:00</xmp:ModifyDate>
                                   </rdf:Description>
                                  </rdf:RDF>
                                 </x:xmpmeta>
                                 <?xpacket end="w"?>""".getBytes(StandardCharsets.UTF_8));
      document.getDocumentCatalog()
              .setMetadata(metadata);
      var out = new ByteArrayOutputStream();
      document.save(out);
      return out.toByteArray();
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }
}

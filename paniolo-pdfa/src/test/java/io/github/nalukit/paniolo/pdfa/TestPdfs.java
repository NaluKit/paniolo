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
package io.github.nalukit.paniolo.pdfa;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDMetadata;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.graphics.color.PDOutputIntent;
import org.verapdf.gf.foundry.VeraGreenfieldFoundryProvider;
import org.verapdf.pdfa.Foundries;
import org.verapdf.pdfa.flavours.PDFAFlavour;
import org.verapdf.pdfa.results.TestAssertion;

import java.awt.color.ColorSpace;
import java.awt.color.ICC_Profile;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;

/**
 * Test PDF documents without text, so no font files are needed.
 */
final class TestPdfs {
  static {
    VeraGreenfieldFoundryProvider.initialise();
  }

  private TestPdfs() {
  }

  /**
   * A minimal PDF/A-2b document showing a few rectangles.
   */
  static byte[] pdfA2b() {
    return create(true,
                  false);
  }

  /**
   * The same document declaring another PDF/A part and conformance, e.g. 1 and B, without
   * changing the content; it may violate that flavour.
   */
  static byte[] pdfA(int part,
                     String conformance) {
    return create(true,
                  false,
                  part,
                  conformance);
  }

  /**
   * The same document without PDF/A identification.
   */
  static byte[] plainPdf() {
    return create(false,
                  false);
  }

  /**
   * PDF/A-like document encrypted with an owner password only.
   */
  static byte[] encryptedPdf() {
    return create(true,
                  true);
  }

  /**
   * veraPDF failures; empty if the document conforms to the flavour.
   */
  static List<String> pdfaFailures(byte[] pdf,
                                   PDFAFlavour flavour) {
    try (var parser = Foundries.defaultInstance()
                               .createParser(new ByteArrayInputStream(pdf),
                                             flavour)) {
      var result = Foundries.defaultInstance()
                            .createValidator(flavour,
                                             false)
                            .validate(parser);
      return result.getTestAssertions()
                   .stream()
                   .filter(assertion -> assertion.getStatus() == TestAssertion.Status.FAILED)
                   .map(assertion -> assertion.getRuleId() + ": " + assertion.getMessage())
                   .distinct()
                   .toList();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static byte[] create(boolean pdfa,
                               boolean encrypted) {
    return create(pdfa,
                  encrypted,
                  2,
                  "B");
  }

  private static byte[] create(boolean pdfa,
                               boolean encrypted,
                               int part,
                               String conformance) {
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
        content.setStrokingColor(0f,
                                 0f,
                                 0f);
        content.addRect(50,
                        300,
                        495,
                        350);
        content.stroke();
      }
      Calendar created = new GregorianCalendar(2026,
                                               Calendar.OCTOBER,
                                               27,
                                               10,
                                               0,
                                               0);
      var info = document.getDocumentInformation();
      info.setTitle("Rechnung R-1");
      info.setCreationDate(created);
      info.setModificationDate(created);
      if (pdfa) {
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
        metadata.importXMPMetadata(("""
                                    <?xpacket begin="﻿" id="W5M0MpCehiHzreSzNTczkc9d"?>
                                    <x:xmpmeta xmlns:x="adobe:ns:meta/">
                                     <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
                                      <rdf:Description rdf:about="" xmlns:pdfaid="http://www.aiim.org/pdfa/ns/id/">
                                       <pdfaid:part>%2$d</pdfaid:part>
                                       <pdfaid:conformance>%3$s</pdfaid:conformance>
                                      </rdf:Description>
                                      <rdf:Description rdf:about="" xmlns:dc="http://purl.org/dc/elements/1.1/">
                                       <dc:title><rdf:Alt><rdf:li xml:lang="x-default">Rechnung R-1</rdf:li></rdf:Alt></dc:title>
                                      </rdf:Description>
                                      <rdf:Description rdf:about="" xmlns:xmp="http://ns.adobe.com/xap/1.0/">
                                       <xmp:CreateDate>%1$s</xmp:CreateDate>
                                       <xmp:ModifyDate>%1$s</xmp:ModifyDate>
                                      </rdf:Description>
                                     </rdf:RDF>
                                    </x:xmpmeta>
                                    <?xpacket end="w"?>""".formatted(java.time.ZonedDateTime.ofInstant(created.toInstant(),
                                                                                                       created.getTimeZone()
                                                                                                              .toZoneId())
                                                                                            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX")),
                                                                                    part,
                                                                                    conformance)).getBytes(StandardCharsets.UTF_8));
        document.getDocumentCatalog()
                .setMetadata(metadata);
      }
      if (encrypted) {
        var policy = new StandardProtectionPolicy("owner",
                                                  "",
                                                  new AccessPermission());
        policy.setEncryptionKeyLength(128);
        document.protect(policy);
      }
      var out = new ByteArrayOutputStream();
      document.save(out);
      return out.toByteArray();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}

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
package io.github.nalukit.paniolo.pdflayout;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDMetadata;
import org.apache.pdfbox.pdmodel.graphics.color.PDOutputIntent;

import java.awt.color.ColorSpace;
import java.awt.color.ICC_Profile;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Makes a document PDF/A-3b: sRGB output intent, document information and the same values in
 * the XMP metadata, document language.
 */
final class PdfA {
  static final String PRODUCER = "Paniolo PDF layout";

  private PdfA() {
  }

  static void finish(PDDocument document,
                     String title,
                     String author,
                     String subject,
                     Locale locale)
      throws IOException {
    document.setVersion(1.7f);
    var catalog = document.getDocumentCatalog();
    catalog.setLanguage(locale.toLanguageTag());

    byte[] icc = ICC_Profile.getInstance(ColorSpace.CS_sRGB)
                            .getData();
    var intent = new PDOutputIntent(document,
                                    new ByteArrayInputStream(icc));
    intent.setInfo("sRGB IEC61966-2.1");
    intent.setOutputCondition("sRGB IEC61966-2.1");
    intent.setOutputConditionIdentifier("sRGB IEC61966-2.1");
    intent.setRegistryName("http://www.color.org");
    catalog.addOutputIntent(intent);

    Calendar now = new GregorianCalendar(TimeZone.getTimeZone(ZoneOffset.UTC));
    now.set(Calendar.MILLISECOND,
            0);
    var info = document.getDocumentInformation();
    info.setTitle(title);
    info.setAuthor(author);
    info.setSubject(subject);
    info.setCreator(PRODUCER);
    info.setProducer(PRODUCER);
    info.setCreationDate(now);
    info.setModificationDate(now);

    String date = ZonedDateTime.ofInstant(now.toInstant(),
                                          ZoneOffset.UTC)
                               .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX"));
    String xmp = """
                 <?xpacket begin="﻿" id="W5M0MpCehiHzreSzNTczkc9d"?>
                 <x:xmpmeta xmlns:x="adobe:ns:meta/">
                  <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
                   <rdf:Description rdf:about="" xmlns:pdfaid="http://www.aiim.org/pdfa/ns/id/">
                    <pdfaid:part>3</pdfaid:part>
                    <pdfaid:conformance>B</pdfaid:conformance>
                   </rdf:Description>
                   <rdf:Description rdf:about="" xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:title><rdf:Alt><rdf:li xml:lang="x-default">%1$s</rdf:li></rdf:Alt></dc:title>
                    %2$s
                    <dc:description><rdf:Alt><rdf:li xml:lang="x-default">%3$s</rdf:li></rdf:Alt></dc:description>
                   </rdf:Description>
                   <rdf:Description rdf:about="" xmlns:pdf="http://ns.adobe.com/pdf/1.3/">
                    <pdf:Producer>%4$s</pdf:Producer>
                   </rdf:Description>
                   <rdf:Description rdf:about="" xmlns:xmp="http://ns.adobe.com/xap/1.0/">
                    <xmp:CreatorTool>%4$s</xmp:CreatorTool>
                    <xmp:CreateDate>%5$s</xmp:CreateDate>
                    <xmp:ModifyDate>%5$s</xmp:ModifyDate>
                   </rdf:Description>
                  </rdf:RDF>
                 </x:xmpmeta>
                 <?xpacket end="w"?>""".formatted(escape(title),
                                                  author == null ?
                                                  "" :
                                                  "<dc:creator><rdf:Seq><rdf:li>" + escape(author) + "</rdf:li></rdf:Seq></dc:creator>",
                                                  escape(subject),
                                                  PRODUCER,
                                                  date);
    var metadata = new PDMetadata(document);
    metadata.importXMPMetadata(xmp.getBytes(StandardCharsets.UTF_8));
    catalog.setMetadata(metadata);
  }

  private static String escape(String value) {
    return value.replace("&",
                         "&amp;")
                .replace("<",
                         "&lt;")
                .replace(">",
                         "&gt;");
  }
}

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
import io.github.nalukit.paniolo.model.common.value.InvoiceTypeCode;
import io.github.nalukit.paniolo.validation.ValidationMessage;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Factur-X container checks (stage HYBRID) on generated and deliberately broken documents.
 */
class ContainerCheckTest {
  private static final Paniolo              PANIOLO = Paniolo.create();
  private static final PdfBoxFacturXSupport SUPPORT = new PdfBoxFacturXSupport();

  private static byte[] facturX(FacturXProfile profile) {
    return PANIOLO.toFacturX(FacturXInvoices.invoice(profile,
                                                     InvoiceTypeCode.CODE_380)
                                            .build(),
                             TestPdfs.pdfA2b(),
                             profile);
  }

  private static List<String> rules(byte[] pdf) {
    return SUPPORT.checkContainer(pdf)
                  .stream()
                  .map(ValidationMessage::ruleId)
                  .toList();
  }

  private static byte[] modify(byte[] pdf,
                               Consumer<PDDocument> change) {
    try (PDDocument document = Loader.loadPDF(pdf)) {
      change.accept(document);
      var out = new ByteArrayOutputStream();
      document.save(out);
      return out.toByteArray();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static COSDictionary invoiceFile(PDDocument document) {
    return (COSDictionary) document.getDocumentCatalog()
                                   .getCOSObject()
                                   .getCOSArray(COSName.getPDFName("AF"))
                                   .getObject(0);
  }

  private static void replaceXmp(PDDocument document,
                                 String from,
                                 String to) {
    try {
      String xmp = new String(document.getDocumentCatalog()
                                      .getMetadata()
                                      .toByteArray(),
                              StandardCharsets.UTF_8);
      var metadata = new PDMetadata(document);
      metadata.importXMPMetadata(xmp.replace(from,
                                             to)
                                    .getBytes(StandardCharsets.UTF_8));
      document.getDocumentCatalog()
              .setMetadata(metadata);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  @ParameterizedTest
  @EnumSource(value = FacturXProfile.class,
              names = { "MINIMUM",
                        "BASIC_WL",
                        "EXTENDED_CTC_FR" },
              mode = EnumSource.Mode.EXCLUDE)
  void generatedDocumentsHaveNoContainerFindings(FacturXProfile profile) {
    assertEquals(List.of(),
                 SUPPORT.checkContainer(facturX(profile)));
  }

  @Test
  void reportsWrongRelationshipMimeTypeAndFileName() {
    byte[] broken = modify(facturX(FacturXProfile.EN16931),
                           document -> {
                             var file = invoiceFile(document);
                             file.setName(COSName.getPDFName("AFRelationship"),
                                          "Supplement");
                             replaceXmp(document,
                                        "<fx:DocumentFileName>factur-x.xml</fx:DocumentFileName>",
                                        "<fx:DocumentFileName>invoice.xml</fx:DocumentFileName>");
                           });
    var rules = rules(broken);
    assertTrue(rules.contains("FX-AF-RELATIONSHIP"),
               rules::toString);
    assertTrue(rules.contains("FX-DOCUMENT-FILE-NAME"),
               rules::toString);
  }

  @Test
  void reportsConformanceLevelThatDoesNotMatchTheInvoice() {
    byte[] broken = modify(facturX(FacturXProfile.EN16931),
                           document -> replaceXmp(document,
                                                  "<fx:ConformanceLevel>EN 16931</fx:ConformanceLevel>",
                                                  "<fx:ConformanceLevel>EXTENDED</fx:ConformanceLevel>"));
    assertTrue(rules(broken).contains("FX-GUIDELINE"),
               () -> rules(broken).toString());
    byte[] unknown = modify(facturX(FacturXProfile.EN16931),
                            document -> replaceXmp(document,
                                                   "<fx:ConformanceLevel>EN 16931</fx:ConformanceLevel>",
                                                   "<fx:ConformanceLevel>COMFORT</fx:ConformanceLevel>"));
    assertTrue(rules(unknown).contains("FX-CONFORMANCE-LEVEL"));
  }

  @Test
  void reportsMissingExtensionSchemaAndWrongPdfaPart() {
    byte[] broken = modify(facturX(FacturXProfile.XRECHNUNG),
                           document -> {
                             replaceXmp(document,
                                        "<pdfaid:part>3</pdfaid:part>",
                                        "<pdfaid:part>2</pdfaid:part>");
                             replaceXmp(document,
                                        "<pdfaSchema:namespaceURI>urn:factur-x:pdfa:CrossIndustryDocument:invoice:1p0#</pdfaSchema:namespaceURI>",
                                        "<pdfaSchema:namespaceURI>urn:example</pdfaSchema:namespaceURI>");
                           });
    var rules = rules(broken);
    assertTrue(rules.contains("FX-PDFA-PART"),
               rules::toString);
    assertTrue(rules.contains("FX-EXTENSION-SCHEMA"),
               rules::toString);
  }

  @Test
  void combinedResultContainsAllStagesAndExportsXml() {
    var result = PANIOLO.validate(facturX(FacturXProfile.EN16931));
    assertTrue(result.isValid(),
               () -> result.messages()
                           .toString());
    // Without paniolo-pdfa the PDF/A stage is reported as not checked.
    assertEquals(List.of("PDFA-NOT-CHECKED"),
                 result.messages(ValidationMessage.Stage.PDF_A)
                       .stream()
                       .map(ValidationMessage::ruleId)
                       .toList());
    assertTrue(result.profileName()
                     .startsWith("Factur-X EN 16931 / Factur-X EN16931"),
               result::profileName);
    String report = result.toXml();
    assertTrue(report.contains("<summary") && !report.contains("valid=\"false\""),
               report);
    assertTrue(report.contains("<stage name=\"PDF_A\""),
               report);
  }
}

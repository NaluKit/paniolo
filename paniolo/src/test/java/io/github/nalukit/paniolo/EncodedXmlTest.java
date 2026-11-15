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
import io.github.nalukit.paniolo.model.common.ProcessControl;
import io.github.nalukit.paniolo.model.common.value.InvoiceTypeCode;
import io.github.nalukit.paniolo.validation.Severity;
import io.github.nalukit.paniolo.validation.ValidationMessage;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The profile and the specification identifier are read with the encoding of the document: an
 * XRechnung without buyer reference stays invalid in every supported encoding.
 */
class EncodedXmlTest {
  private static final Paniolo PANIOLO = Paniolo.create();

  static byte[] encode(String xml,
                       String declared,
                       Charset charset,
                       byte[] bom) {
    var out = new ByteArrayOutputStream();
    out.writeBytes(bom);
    out.writeBytes(xml.replace("encoding=\"UTF-8\"",
                               "encoding=\"" + declared + "\"")
                      .getBytes(charset));
    return out.toByteArray();
  }

  static Map<String, byte[]> variants(String xml) {
    var variants = new LinkedHashMap<String, byte[]>();
    variants.put("UTF-8",
                 encode(xml,
                        "UTF-8",
                        StandardCharsets.UTF_8,
                        new byte[0]));
    variants.put("UTF-8 BOM",
                 encode(xml,
                        "UTF-8",
                        StandardCharsets.UTF_8,
                        new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF}));
    variants.put("UTF-16LE BOM",
                 encode(xml,
                        "UTF-16",
                        StandardCharsets.UTF_16LE,
                        new byte[] {(byte) 0xFF, (byte) 0xFE}));
    variants.put("UTF-16BE BOM",
                 encode(xml,
                        "UTF-16",
                        StandardCharsets.UTF_16BE,
                        new byte[] {(byte) 0xFE, (byte) 0xFF}));
    variants.put("UTF-16LE",
                 encode(xml,
                        "UTF-16LE",
                        StandardCharsets.UTF_16LE,
                        new byte[0]));
    variants.put("UTF-16BE",
                 encode(xml,
                        "UTF-16BE",
                        StandardCharsets.UTF_16BE,
                        new byte[0]));
    return variants;
  }

  private static String xrechnung(InvoiceSyntax syntax) {
    return PANIOLO.toXml(TestInvoices.invoice(InvoiceTypeCode.CODE_380,
                                              "ENC-1"),
                         syntax);
  }

  @Test
  void aMissingBuyerReferenceIsAnErrorInEveryEncoding() {
    for (var syntax : InvoiceSyntax.values()) {
      String valid = xrechnung(syntax);
      assertTrue(valid.contains(ProcessControl.XRECHNUNG_3));
      String broken = valid.replaceAll("<cbc:BuyerReference>[^<]*</cbc:BuyerReference>|<ram:BuyerReference>[^<]*</ram:BuyerReference>",
                                       "");
      for (var variant : variants(valid).entrySet()) {
        var result = PANIOLO.validate(variant.getValue());
        assertTrue(result.isValid(),
                   syntax + " " + variant.getKey() + " " + result.messages());
      }
      var expected = PANIOLO.validate(broken.getBytes(StandardCharsets.UTF_8));
      assertFalse(expected.isValid());
      for (var variant : variants(broken).entrySet()) {
        var result = PANIOLO.validate(variant.getValue());
        String where = syntax + " " + variant.getKey();
        assertFalse(result.isValid(),
                    where + " " + result.messages());
        assertEquals(expected.profileName(),
                     result.profileName(),
                     where);
        assertEquals(severities(expected),
                     severities(result),
                     where);
        assertTrue(result.messages()
                         .stream()
                         .noneMatch(message -> "XR-NOT-STATED".equals(message.ruleId())),
                   where);
      }
    }
  }

  private static Map<String, Severity> severities(io.github.nalukit.paniolo.validation.ValidationResult result) {
    var map = new LinkedHashMap<String, Severity>();
    for (ValidationMessage message : result.messages()) {
      if (message.ruleId() != null) {
        map.merge(message.ruleId(),
                  message.severity(),
                  (a, b) -> a.compareTo(b) <= 0 ?
                            a :
                            b);
      }
    }
    return map;
  }
}

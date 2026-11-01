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

import io.github.nalukit.paniolo.config.FacturXProfile;
import io.github.nalukit.paniolo.config.InvoiceSyntax;
import io.github.nalukit.paniolo.model.Invoice;
import io.github.nalukit.paniolo.model.common.DocumentAdjustment;
import io.github.nalukit.paniolo.model.common.InvoiceLine;
import io.github.nalukit.paniolo.model.common.ProcessControl;
import io.github.nalukit.paniolo.model.common.TaxCategory;
import io.github.nalukit.paniolo.model.common.value.InvoiceTypeCode;
import io.github.nalukit.paniolo.validation.ValidationResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates the counterexamples of the calculation corpus in
 * {@code src/test/resources/calculation-corpus}: one valid invoice and variants in which exactly one
 * amount relationship is broken, each as XRechnung CII, XRechnung UBL and Factur-X EN 16931 CII.
 *
 * <p>The base invoice has one line of 2 x 100.00 (gross price 120.00 minus discount 20.00), a
 * document allowance of 10 % of 200.00 and 19 % VAT: line total 200.00, allowance 20.00, tax basis
 * 180.00, VAT 34.20, amount due 214.20.
 *
 * <p>Run {@link #main(String[])} with the target directory to regenerate the files.
 */
final class CalculationCorpus {
  /**
   * Changes per case: element path (local names, {@code //} for any depth) to new value. The paths
   * of both syntaxes are given; a path that does not exist in a syntax is ignored.
   */
  static final Map<String, Map<String, String>> CASES = new LinkedHashMap<>();

  static {
    CASES.put("valid",
              Map.of());
    // Line amount 210.00 instead of 2 x 100.00; all totals follow it, so only the line is wrong.
    CASES.put("line-net-amount",
              Map.ofEntries(Map.entry("//SpecifiedTradeSettlementLineMonetarySummation/LineTotalAmount",
                                      "210.00"),
                            Map.entry("//InvoiceLine/LineExtensionAmount",
                                      "210.00"),
                            Map.entry("//SpecifiedTradeSettlementHeaderMonetarySummation/LineTotalAmount",
                                      "210.00"),
                            Map.entry("//LegalMonetaryTotal/LineExtensionAmount",
                                      "210.00"),
                            Map.entry("//SpecifiedTradeSettlementHeaderMonetarySummation/TaxBasisTotalAmount",
                                      "190.00"),
                            Map.entry("//LegalMonetaryTotal/TaxExclusiveAmount",
                                      "190.00"),
                            Map.entry("//ApplicableHeaderTradeSettlement/ApplicableTradeTax/BasisAmount",
                                      "190.00"),
                            Map.entry("//TaxSubtotal/TaxableAmount",
                                      "190.00"),
                            Map.entry("//ApplicableHeaderTradeSettlement/ApplicableTradeTax/CalculatedAmount",
                                      "36.10"),
                            Map.entry("//TaxSubtotal/TaxAmount",
                                      "36.10"),
                            Map.entry("//SpecifiedTradeSettlementHeaderMonetarySummation/TaxTotalAmount",
                                      "36.10"),
                            Map.entry("/*/TaxTotal/TaxAmount",
                                      "36.10"),
                            Map.entry("//SpecifiedTradeSettlementHeaderMonetarySummation/GrandTotalAmount",
                                      "226.10"),
                            Map.entry("//LegalMonetaryTotal/TaxInclusiveAmount",
                                      "226.10"),
                            Map.entry("//SpecifiedTradeSettlementHeaderMonetarySummation/DuePayableAmount",
                                      "226.10"),
                            Map.entry("//LegalMonetaryTotal/PayableAmount",
                                      "226.10")));
    // Price discount 10.00: 120.00 - 10.00 is not the net price 100.00.
    CASES.put("net-price",
              Map.of("//GrossPriceProductTradePrice/AppliedTradeAllowanceCharge/ActualAmount",
                     "10.00",
                     "//Price/AllowanceCharge/Amount",
                     "10.00"));
    // 5 % of 200.00 is not the allowance 20.00.
    CASES.put("allowance-percentage",
              Map.of("//ApplicableHeaderTradeSettlement/SpecifiedTradeAllowanceCharge/CalculationPercent",
                     "5.00",
                     "/*/AllowanceCharge/MultiplierFactorNumeric",
                     "5.00"));
    CASES.put("line-total",
              Map.of("//SpecifiedTradeSettlementHeaderMonetarySummation/LineTotalAmount",
                     "201.00",
                     "//LegalMonetaryTotal/LineExtensionAmount",
                     "201.00"));
    CASES.put("tax-total",
              Map.of("//SpecifiedTradeSettlementHeaderMonetarySummation/TaxTotalAmount",
                     "34.30",
                     "/*/TaxTotal/TaxAmount",
                     "34.30"));
    CASES.put("payable-amount",
              Map.of("//SpecifiedTradeSettlementHeaderMonetarySummation/DuePayableAmount",
                     "215.00",
                     "//LegalMonetaryTotal/PayableAmount",
                     "215.00"));
    // VAT category amount 34.21 instead of 19 % of 180.00, and the totals follow it.
    CASES.put("vat-category-amount",
              Map.of("//ApplicableHeaderTradeSettlement/ApplicableTradeTax/CalculatedAmount",
                     "34.21",
                     "//TaxSubtotal/TaxAmount",
                     "34.21",
                     "//SpecifiedTradeSettlementHeaderMonetarySummation/TaxTotalAmount",
                     "34.21",
                     "/*/TaxTotal/TaxAmount",
                     "34.21",
                     "//SpecifiedTradeSettlementHeaderMonetarySummation/GrandTotalAmount",
                     "214.21",
                     "//LegalMonetaryTotal/TaxInclusiveAmount",
                     "214.21",
                     "//SpecifiedTradeSettlementHeaderMonetarySummation/DuePayableAmount",
                     "214.21",
                     "//LegalMonetaryTotal/PayableAmount",
                     "214.21"));
  }

  /**
   * The targets of the corpus: file suffix, process control and syntax.
   */
  static final List<String> TARGETS = List.of("xrechnung-cii",
                                              "xrechnung-ubl",
                                              "facturx-en16931");

  private CalculationCorpus() {
  }

  static Invoice invoice(ProcessControl processControl) {
    var tax = TaxCategory.standard(new BigDecimal("19"));
    var builder = TestInvoices.builder(InvoiceTypeCode.CODE_380,
                                       "R-CALC-1")
                              .processControl(processControl);
    // Replaces the line of the test invoice by one with gross price and discount.
    var invoice = builder.build();
    return Invoice.create()
                  .number(invoice.number())
                  .typeCode(invoice.typeCode())
                  .issueDate(invoice.issueDate())
                  .dueDate(invoice.dueDate())
                  .currencyCode(invoice.currencyCode())
                  .processControl(processControl)
                  .buyerReference(invoice.buyerReference())
                  .seller(invoice.seller())
                  .buyer(invoice.buyer())
                  .delivery(invoice.delivery())
                  .paymentInstruction(invoice.paymentInstruction())
                  .addLine(InvoiceLine.create()
                                      .id("1")
                                      .name("Beratung")
                                      .quantity(new BigDecimal("2"))
                                      .unitCode("HUR")
                                      .netUnitPrice(new BigDecimal("100.00"))
                                      .grossPrice(new BigDecimal("120.00"))
                                      .priceDiscount(new BigDecimal("20.00"))
                                      .tax(tax)
                                      .build())
                  .addAdjustment(DocumentAdjustment.createAllowance()
                                                   .amount(new BigDecimal("20.00"))
                                                   .baseAmount(new BigDecimal("200.00"))
                                                   .percentage(new BigDecimal("10.00"))
                                                   .reason("Rabatt")
                                                   .tax(tax)
                                                   .build())
                  .build();
  }

  /**
   * The XML of a case for a target.
   */
  static byte[] xml(Paniolo paniolo,
                    String name,
                    String target) {
    var invoice = invoice(target.startsWith("facturx") ?
                          ProcessControl.en16931() :
                          ProcessControl.xrechnung());
    byte[] base = paniolo.toBytes(invoice,
                                  target.endsWith("ubl") ?
                                  InvoiceSyntax.UBL :
                                  InvoiceSyntax.CII);
    var changes = CASES.get(name);
    if (changes.isEmpty()) {
      return base;
    }
    try {
      var factory = DocumentBuilderFactory.newInstance();
      factory.setNamespaceAware(true);
      Document document = factory.newDocumentBuilder()
                                 .parse(new ByteArrayInputStream(base));
      int changed = 0;
      for (var change : changes.entrySet()) {
        for (var element : select(document,
                                  change.getKey())) {
          element.setTextContent(change.getValue());
          changed++;
        }
      }
      if (changed == 0) {
        throw new IllegalStateException("Case " + name + " changes nothing in " + target);
      }
      var transformer = TransformerFactory.newInstance()
                                          .newTransformer();
      transformer.setOutputProperty(OutputKeys.ENCODING,
                                    "UTF-8");
      var out = new ByteArrayOutputStream();
      transformer.transform(new DOMSource(document),
                            new StreamResult(out));
      return out.toByteArray();
    } catch (Exception e) {
      throw new IllegalStateException("Case " + name + " for " + target + " cannot be generated",
                                      e);
    }
  }

  /**
   * Validates a file of the corpus with the rules of its target: XRechnung as detected, Factur-X
   * EN 16931 with the Factur-X rules (standalone EN 16931 XML would be checked against XRechnung).
   */
  static ValidationResult validate(Paniolo paniolo,
                                   byte[] xml,
                                   String target) {
    return target.startsWith("facturx") ?
           paniolo.validate(xml,
                            FacturXProfile.EN16931) :
           paniolo.validate(xml);
  }

  /**
   * Elements on a path of local names; {@code //a/b} at any depth, {@code /*}{@code /a} below the root.
   */
  private static List<Element> select(Document document,
                                      String path) {
    boolean anyDepth = path.startsWith("//");
    String[] steps = (anyDepth ?
                      path.substring(2) :
                      path.substring(3)).split("/");
    List<Element> current = new ArrayList<>();
    if (anyDepth) {
      var all = document.getElementsByTagNameNS("*",
                                                steps[0]);
      for (int i = 0; i < all.getLength(); i++) {
        current.add((Element) all.item(i));
      }
    } else {
      current.addAll(children(document.getDocumentElement(),
                              steps[0]));
    }
    for (int step = 1; step < steps.length; step++) {
      List<Element> next = new ArrayList<>();
      for (var element : current) {
        next.addAll(children(element,
                             steps[step]));
      }
      current = next;
    }
    return current;
  }

  private static List<Element> children(Element parent,
                                        String localName) {
    List<Element> result = new ArrayList<>();
    for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (child instanceof Element element && localName.equals(element.getLocalName())) {
        result.add(element);
      }
    }
    return result;
  }

  /**
   * Writes all files of the corpus.
   *
   * @param args the target directory
   */
  public static void main(String[] args)
      throws IOException {
    Path directory = Path.of(args[0]);
    Files.createDirectories(directory);
    var paniolo = Paniolo.create();
    for (String name : CASES.keySet()) {
      for (String target : TARGETS) {
        Files.write(directory.resolve(name + "." + target + ".xml"),
                    xml(paniolo,
                        name,
                        target));
      }
    }
  }
}

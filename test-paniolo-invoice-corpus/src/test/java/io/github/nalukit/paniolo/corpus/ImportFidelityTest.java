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
package io.github.nalukit.paniolo.corpus;

import io.github.nalukit.paniolo.Paniolo;
import io.github.nalukit.paniolo.config.InvoiceSyntax;
import io.github.nalukit.paniolo.model.Invoice;
import io.github.nalukit.paniolo.validation.Severity;
import io.github.nalukit.paniolo.model.common.InvoiceLine;
import io.github.nalukit.paniolo.xml.ExportVariant;
import io.github.nalukit.paniolo.xml.ImportResult;
import io.github.nalukit.paniolo.xml.ReadMode;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Field-level fidelity of the import of every readable corpus document, which
 * {@link PublicInvoiceCorpusTest} does not check: a successful read counts as done only if
 * the parties, lines, VAT data, payment data, attachments, and amounts of the document arrive in
 * the model, and if the export keeps or states what changed.
 *
 * <ul>
 *   <li>{@code original}: the original export is the received XML, byte for byte.</li>
 *   <li>{@code oracle}: facts read independently from the XML with XPath are in the first model:
 *       number, date, currency, addresses of seller and buyer, line identifiers, names, and
 *       VAT, credit transfer accounts, and the SHA-256 of every embedded attachment.</li>
 *   <li>{@code same-syntax} and {@code other-syntax}: the normalized export in the syntax of the
 *       document and in the other syntax passes the validation of its own syntax (kind
 *       {@code invalid}, one problem per failed rule) and, read again, states the same facts as the
 *       first read.</li>
 * </ul>
 *
 * Expected differences are listed one by one in {@code fidelity-exceptions.tsv}: file, check,
 * kind ({@code lost}, {@code added}, {@code missing}, or the exception class), the lost fact or the
 * message, and the reason. A problem outside the list, a changed message, or an entry that no longer
 * applies fails the test. {@link #aLossIsNotOverlooked()} shows that a simulated loss fails.
 */
class ImportFidelityTest {
  private static final Path     CORPUS     = Path.of("src/test/resources/corpus");
  private static final Path     EXCEPTIONS = Path.of("src/test/resources/corpus/fidelity-exceptions.tsv");
  private static final Paniolo  PANIOLO    = Paniolo.create();

  /**
   * A problem: the check, its kind, and a stable detail (the fact or the message).
   */
  private record Problem(String check, String kind, String detail) {
    Problem {
      detail = detail.replaceAll("\\s+",
                                 " ")
                     .strip();
    }

    String key() {
      return check + "\t" + kind + "\t" + detail;
    }
  }

  private static Map<String, String> exceptions() throws IOException {
    var map = new LinkedHashMap<String, String>();
    for (String line : Files.readAllLines(EXCEPTIONS)) {
      String[] c = line.split("\t",
                              -1);
      if (line.isBlank() || line.startsWith("file\t")) {
        continue;
      }
      assertEquals(5,
                   c.length,
                   "file, check, kind, detail, reason: " + line);
      assertFalse(c[4].isBlank(),
                  "An exception needs its reason: " + line);
      assertTrue(map.put(c[0] + "\t" + c[1] + "\t" + c[2] + "\t" + c[3],
                         c[4]) == null,
                 "Duplicate exception: " + line);
    }
    return map;
  }

  private static List<String> readableFiles() throws IOException {
    return Files.readAllLines(CORPUS.resolve("manifest.tsv"))
                .stream()
                .skip(1)
                .map(line -> line.split("\t",
                                        -1))
                .filter(c -> c[5].equals("true"))
                .map(c -> c[0])
                .toList();
  }

  @Test
  void importedDocumentsKeepTheirContent() throws IOException {
    var exceptions = exceptions();
    var unused     = new TreeSet<>(exceptions.keySet());
    var failures   = new ArrayList<String>();
    for (String file : readableFiles()) {
      byte[] bytes = Files.readAllBytes(CORPUS.resolve(file));
      for (var problem : check(file,
                               bytes,
                               UnaryOperator.identity(),
                               UnaryOperator.identity())) {
        String key = file + "\t" + problem.key();
        if (!unused.remove(key) && !exceptions.containsKey(key)) {
          failures.add(key);
        }
      }
    }
    assertEquals(List.of(),
                 failures,
                 () -> "Unexplained differences:\n" + String.join("\n",
                                                                  failures));
    assertEquals(new TreeSet<String>(),
                 unused,
                 "Exceptions that no longer apply");
  }

  /**
   * A fact lost before the first model, and one lost by the export, must fail the checks.
   */
  @Test
  void aLossIsNotOverlooked() throws IOException {
    String file  = "en16931/cii/examples/CII_example5.xml";
    byte[] bytes = Files.readAllBytes(CORPUS.resolve(file));
    var    clean = check(file,
                         bytes,
                         UnaryOperator.identity(),
                         UnaryOperator.identity());
    UnaryOperator<List<String>> dropAccount = facts -> {
      var copy = new ArrayList<>(facts);
      copy.removeIf(fact -> fact.startsWith("payment.iban="));
      return copy;
    };
    UnaryOperator<List<String>> dropStreet = facts -> {
      var copy = new ArrayList<>(facts);
      copy.removeIf(fact -> fact.startsWith("seller.street="));
      return copy;
    };
    var beforeModel = check(file,
                            bytes,
                            dropAccount,
                            UnaryOperator.identity());
    assertTrue(beforeModel.stream()
                          .anyMatch(problem -> problem.check()
                                                      .equals("oracle") && problem.kind()
                                                                                  .equals("missing") && problem.detail()
                                                                                                               .startsWith("payment.iban=")),
               "loss before the first model: " + beforeModel);
    var inExport = check(file,
                         bytes,
                         UnaryOperator.identity(),
                         dropStreet);
    assertTrue(inExport.stream()
                       .anyMatch(problem -> problem.check()
                                                   .equals("same-syntax") && problem.kind()
                                                                                    .equals("lost") && problem.detail()
                                                                                                              .startsWith("seller.street=")),
               "loss by the export: " + inExport);
    assertTrue(inExport.size() > clean.size() && beforeModel.size() > clean.size());
  }

  /**
   * The rule validation of the export finds a repeated tax point date (CII) and a second VAT total in
   * the invoice currency (UBL), the two violations the normalized export had.
   */
  @Test
  void aRuleViolationOfTheExportIsFound() throws IOException {
    String cii  = "kosit/src/test/business-cases/standard/02.01a-INVOICE_uncefact.xml";
    var    twice = check(cii,
                         Files.readAllBytes(CORPUS.resolve(cii)),
                         UnaryOperator.identity(),
                         UnaryOperator.identity(),
                         ImportFidelityTest::repeatTaxPointDate);
    assertTrue(twice.stream()
                    .anyMatch(problem -> problem.check()
                                                .equals("same-syntax") && problem.kind()
                                                                                  .equals("invalid") && problem.detail()
                                                                                                               .equals("CII-SR-461")),
               "repeated tax point date: " + twice);

    String ubl  = "en16931/ubl/examples/sample-discount-price.xml";
    var    same = check(ubl,
                        Files.readAllBytes(CORPUS.resolve(ubl)),
                        UnaryOperator.identity(),
                        UnaryOperator.identity(),
                        xml -> xml.contains("<cac:TaxTotal>") ?
                               xml.replace("</cac:TaxTotal>",
                                           "</cac:TaxTotal><cac:TaxTotal><cbc:TaxAmount currencyID=\"EUR\">3.03</cbc:TaxAmount></cac:TaxTotal>") :
                               xml);
    assertTrue(same.stream()
                   .noneMatch(problem -> problem.check()
                                                .equals("other-syntax") && problem.kind()
                                                                                  .equals("invalid")),
               "the CII export is not changed by the UBL tamper: " + same);
    assertTrue(same.stream()
                   .anyMatch(problem -> problem.kind()
                                               .equals("invalid") && problem.detail()
                                                                            .equals("BR-CO-15")),
               "a second VAT total in the invoice currency: " + same);
  }

  /**
   * Copies the tax point date into the second VAT breakdown, which stays valid against the schema.
   */
  private static String repeatTaxPointDate(String xml) {
    if (!xml.contains("<ram:ApplicableHeaderTradeSettlement>")) {
      return xml;
    }
    var date = java.util.regex.Pattern.compile("(?s)<ram:TaxPointDate>.*?</ram:TaxPointDate>")
                                      .matcher(xml);
    assertTrue(date.find(),
               "the export has a tax point date");
    int header = xml.indexOf("<ram:ApplicableHeaderTradeSettlement>");
    int first  = xml.indexOf("<ram:RateApplicablePercent>",
                             header);
    int second = xml.indexOf("<ram:RateApplicablePercent>",
                             first + 1);
    assertTrue(second > 0,
               "the export has two VAT breakdowns");
    return xml.substring(0,
                         second) + date.group() + xml.substring(second);
  }

  private static List<Problem> check(String file,
                                     byte[] bytes,
                                     UnaryOperator<List<String>> readLoss,
                                     UnaryOperator<List<String>> exportLoss) {
    return check(file,
                 bytes,
                 readLoss,
                 exportLoss,
                 UnaryOperator.identity());
  }

  /**
   * @param exportedXml changes the exported XML before it is validated, to simulate a rule violation
   */
  private static List<Problem> check(String file,
                                     byte[] bytes,
                                     UnaryOperator<List<String>> readLoss,
                                     UnaryOperator<List<String>> exportLoss,
                                     UnaryOperator<String> exportedXml) {
    var problems = new ArrayList<Problem>();
    ImportResult imported;
    try {
      imported = PANIOLO.importInvoice(bytes,
                                       ReadMode.LENIENT);
    } catch (RuntimeException e) {
      problems.add(failure("import",
                           e));
      return problems;
    }
    byte[] original = imported.originalXml();
    byte[] exportedOriginal = PANIOLO.export(imported,
                                             ExportVariant.ORIGINAL,
                                             null);
    if (!file.endsWith(".pdf") && !Arrays.equals(bytes,
                                                 exportedOriginal)) {
      problems.add(new Problem("original",
                               "differs",
                               "original export differs from the received bytes"));
    }
    if (!Arrays.equals(original,
                       exportedOriginal)) {
      problems.add(new Problem("original",
                               "differs",
                               "original export differs from the original XML"));
    }
    var firstFacts = readLoss.apply(facts(imported.invoice()));
    try {
      oracle(imported,
             original,
             firstFacts,
             problems);
    } catch (Exception e) {
      problems.add(failure("oracle",
                           e));
    }
    var syntax = PANIOLO.detect(original);
    for (var target : InvoiceSyntax.values()) {
      boolean same = target.name()
                           .equals(syntax.name()) || (syntax.name()
                                                            .startsWith("UBL") && target == InvoiceSyntax.UBL);
      String check = same ?
                     "same-syntax" :
                     "other-syntax";
      try {
        byte[] exported = PANIOLO.export(imported,
                                         ExportVariant.NORMALIZED,
                                         target);
        // A valid source must give a valid export; a rule that fails is one problem per rule.
        var rules = PANIOLO.validate(exportedXml.apply(new String(exported,
                                                                  java.nio.charset.StandardCharsets.UTF_8))
                                           .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        if (!rules.isValid()) {
          rules.messages()
               .stream()
               .filter(message -> message.severity() == Severity.FATAL || message.severity() == Severity.ERROR)
               .map(message -> message.ruleId())
               .distinct()
               .sorted()
               .forEach(rule -> problems.add(new Problem(check,
                                                         "invalid",
                                                         rule)));
        }
        var again  = PANIOLO.read(exported,
                                  ReadMode.LENIENT);
        var actual = exportLoss.apply(facts(again.invoice()));
        compare(check,
                "lost",
                facts(imported.invoice()),
                actual,
                problems);
        compare(check,
                "added",
                actual,
                facts(imported.invoice()),
                problems);
      } catch (RuntimeException e) {
        problems.add(failure(check,
                             e));
      }
    }
    return problems;
  }

  private static Problem failure(String check,
                                 Exception e) {
    return new Problem(check,
                       e.getClass()
                        .getSimpleName(),
                       String.valueOf(e.getMessage()));
  }

  /**
   * One problem per fact of {@code from} that {@code in} does not contain, counting repeats.
   */
  private static void compare(String check,
                              String kind,
                              List<String> from,
                              List<String> in,
                              List<Problem> problems) {
    var rest = new ArrayList<>(in);
    for (String fact : from) {
      if (!rest.remove(fact)) {
        problems.add(new Problem(check,
                                 kind,
                                 fact));
      }
    }
  }

  // ---- oracle ----

  private static void oracle(ImportResult imported,
                             byte[] xml,
                             List<String> modelFacts,
                             List<Problem> problems) throws Exception {
    var factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(true);
    factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING,
                       true);
    Document doc = factory.newDocumentBuilder()
                          .parse(new ByteArrayInputStream(xml));
    var     xpath = XPathFactory.newInstance()
                                .newXPath();
    boolean cii   = "CrossIndustryInvoice".equals(doc.getDocumentElement()
                                                     .getLocalName());
    var xmlFacts = new ArrayList<String>();
    if (cii) {
      ciiFacts(xpath,
               doc,
               xmlFacts);
    } else {
      ublFacts(xpath,
               doc,
               xmlFacts);
    }
    compare("oracle",
            "missing",
            xmlFacts,
            modelFacts,
            problems);

    String payable = string(xpath,
                            doc,
                            cii ?
                            "//*[local-name()='DuePayableAmount']" :
                            "/*/*[local-name()='LegalMonetaryTotal']/*[local-name()='PayableAmount']");
    if (payable != null && imported.stated()
                                   .payableAmount() != null && new BigDecimal(payable).compareTo(imported.stated()
                                                                                                       .payableAmount()) != 0) {
      problems.add(new Problem("oracle",
                               "differs",
                               "payable " + payable + " / " + imported.stated()
                                                                      .payableAmount()));
    }
    double lines = (Double) xpath.evaluate(cii ?
                                           "count(//*[local-name()='IncludedSupplyChainTradeLineItem'])" :
                                           "count(/*/*[local-name()='InvoiceLine' or local-name()='CreditNoteLine'])",
                                           doc,
                                           XPathConstants.NUMBER);
    if ((int) lines != imported.invoice()
                               .lines()
                               .size()) {
      problems.add(new Problem("oracle",
                               "differs",
                               "lines " + (int) lines + " / " + imported.invoice()
                                                                         .lines()
                                                                         .size()));
    }
  }

  private static final String SELLER_CII = "//*[local-name()='SellerTradeParty']";
  private static final String BUYER_CII  = "//*[local-name()='BuyerTradeParty']";

  private static void ciiFacts(XPath xpath,
                               Document doc,
                               List<String> facts) throws Exception {
    add(facts,
        "number=",
        string(xpath,
               doc,
               "//*[local-name()='ExchangedDocument']/*[local-name()='ID']"));
    add(facts,
        "date=",
        date(string(xpath,
                    doc,
                    "//*[local-name()='ExchangedDocument']/*[local-name()='IssueDateTime']/*[local-name()='DateTimeString']")));
    add(facts,
        "currency=",
        string(xpath,
               doc,
               "//*[local-name()='ApplicableHeaderTradeSettlement']/*[local-name()='InvoiceCurrencyCode']"));
    for (var role : Map.of("seller",
                           SELLER_CII,
                           "buyer",
                           BUYER_CII).entrySet()) {
      String address = role.getValue() + "/*[local-name()='PostalTradeAddress']/*[local-name()='";
      add(facts,
          role.getKey() + ".name=",
          string(xpath,
                 doc,
                 role.getValue() + "/*[local-name()='Name']"));
      add(facts,
          role.getKey() + ".street=",
          string(xpath,
                 doc,
                 address + "LineOne']"));
      add(facts,
          role.getKey() + ".postcode=",
          string(xpath,
                 doc,
                 address + "PostcodeCode']"));
      add(facts,
          role.getKey() + ".city=",
          string(xpath,
                 doc,
                 address + "CityName']"));
      add(facts,
          role.getKey() + ".country=",
          string(xpath,
                 doc,
                 address + "CountryID']"));
    }
    for (Node account : nodes(xpath,
                              doc,
                              "//*[local-name()='SpecifiedTradeSettlementPaymentMeans']/*[local-name()='PayeePartyCreditorFinancialAccount']")) {
      String id = string(xpath,
                         account,
                         "*[local-name()='IBANID']");
      add(facts,
          "payment.iban=",
          id != null ?
          id :
          string(xpath,
                 account,
                 "*[local-name()='ProprietaryID']"));
    }
    for (Node line : nodes(xpath,
                           doc,
                           "//*[local-name()='IncludedSupplyChainTradeLineItem']")) {
      add(facts,
          "line.id=",
          string(xpath,
                 line,
                 "*[local-name()='AssociatedDocumentLineDocument']/*[local-name()='LineID']"));
      add(facts,
          "line.name=",
          string(xpath,
                 line,
                 "*[local-name()='SpecifiedTradeProduct']/*[local-name()='Name']"));
      String code = string(xpath,
                           line,
                           "*[local-name()='SpecifiedLineTradeSettlement']/*[local-name()='ApplicableTradeTax']/*[local-name()='CategoryCode']");
      if (code != null) {
        add(facts,
            "line.tax=",
            code + ":" + number(string(xpath,
                                       line,
                                       "*[local-name()='SpecifiedLineTradeSettlement']/*[local-name()='ApplicableTradeTax']/*[local-name()='RateApplicablePercent']")));
      }
    }
    attachments(xpath,
                doc,
                facts);
  }

  private static void ublFacts(XPath xpath,
                               Document doc,
                               List<String> facts) throws Exception {
    add(facts,
        "number=",
        string(xpath,
               doc,
               "/*/*[local-name()='ID']"));
    add(facts,
        "date=",
        string(xpath,
               doc,
               "/*/*[local-name()='IssueDate']"));
    add(facts,
        "currency=",
        string(xpath,
               doc,
               "/*/*[local-name()='DocumentCurrencyCode']"));
    for (var role : Map.of("seller",
                           "AccountingSupplierParty",
                           "buyer",
                           "AccountingCustomerParty").entrySet()) {
      String party   = "/*/*[local-name()='" + role.getValue() + "']/*[local-name()='Party']";
      String address = party + "/*[local-name()='PostalAddress']/*[local-name()='";
      String name = string(xpath,
                           doc,
                           party + "/*[local-name()='PartyLegalEntity']/*[local-name()='RegistrationName']");
      add(facts,
          role.getKey() + ".name=",
          name != null ?
          name :
          string(xpath,
                 doc,
                 party + "/*[local-name()='PartyName']/*[local-name()='Name']"));
      add(facts,
          role.getKey() + ".street=",
          string(xpath,
                 doc,
                 address + "StreetName']"));
      add(facts,
          role.getKey() + ".postcode=",
          string(xpath,
                 doc,
                 address + "PostalZone']"));
      add(facts,
          role.getKey() + ".city=",
          string(xpath,
                 doc,
                 address + "CityName']"));
      add(facts,
          role.getKey() + ".country=",
          string(xpath,
                 doc,
                 address + "Country']/*[local-name()='IdentificationCode']"));
    }
    for (Node account : nodes(xpath,
                              doc,
                              "/*/*[local-name()='PaymentMeans']/*[local-name()='PayeeFinancialAccount']")) {
      add(facts,
          "payment.iban=",
          string(xpath,
                 account,
                 "*[local-name()='ID']"));
    }
    for (Node line : nodes(xpath,
                           doc,
                           "//*[local-name()='InvoiceLine' or local-name()='CreditNoteLine' or local-name()='SubInvoiceLine']")) {
      add(facts,
          "line.id=",
          string(xpath,
                 line,
                 "*[local-name()='ID']"));
      add(facts,
          "line.name=",
          string(xpath,
                 line,
                 "*[local-name()='Item']/*[local-name()='Name']"));
      String code = string(xpath,
                           line,
                           "*[local-name()='Item']/*[local-name()='ClassifiedTaxCategory']/*[local-name()='ID']");
      if (code != null) {
        add(facts,
            "line.tax=",
            code + ":" + number(string(xpath,
                                       line,
                                       "*[local-name()='Item']/*[local-name()='ClassifiedTaxCategory']/*[local-name()='Percent']")));
      }
    }
    attachments(xpath,
                doc,
                facts);
  }

  private static void attachments(XPath xpath,
                                  Document doc,
                                  List<String> facts) throws Exception {
    for (Node object : nodes(xpath,
                             doc,
                             "//*[local-name()='EmbeddedDocumentBinaryObject' or local-name()='AttachmentBinaryObject']")) {
      String text = object.getTextContent()
                          .replaceAll("[ \\t\\r\\n]",
                                      "");
      facts.add("attachment.sha256=" + sha256(Base64.getDecoder()
                                                    .decode(text)));
    }
  }

  private static List<Node> nodes(XPath xpath,
                                  Object context,
                                  String expression) throws Exception {
    var list   = (NodeList) xpath.evaluate(expression,
                                           context,
                                           XPathConstants.NODESET);
    var result = new ArrayList<Node>();
    for (int i = 0; i < list.getLength(); i++) {
      result.add(list.item(i));
    }
    return result;
  }

  private static void add(List<String> facts,
                          String key,
                          String value) {
    if (value != null) {
      facts.add(key + value);
    }
  }

  private static String date(String basic) {
    return basic == null || basic.length() < 8 ?
           basic :
           basic.substring(0,
                           4) + "-" + basic.substring(4,
                                                      6) + "-" + basic.substring(6,
                                                                                 8);
  }

  private static String number(String value) {
    return value == null ?
           "null" :
           new BigDecimal(value).stripTrailingZeros()
                                .toPlainString();
  }

  private static String sha256(byte[] content) {
    try {
      return HexFormat.of()
                      .formatHex(MessageDigest.getInstance("SHA-256")
                                              .digest(content));
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private static String string(XPath xpath,
                               Object context,
                               String expression) throws Exception {
    String value = ((String) xpath.evaluate(expression,
                                            context,
                                            XPathConstants.STRING)).strip();
    return value.isEmpty() ?
           null :
           value;
  }

  // ---- facts of the model ----

  private static List<String> facts(Invoice invoice) {
    var out = new ArrayList<String>();
    out.add("number=" + invoice.number());
    out.add("type=" + invoice.typeCode());
    out.add("date=" + invoice.issueDate());
    out.add("currency=" + invoice.currencyCode()
                                    .name());
    party(out,
          "seller",
          invoice.seller());
    party(out,
          "buyer",
          invoice.buyer());
    lines(out,
          "",
          invoice.lines());
    var totals = invoice.totals();
    out.add("total.tax=" + totals.taxAmount());
    out.add("total.payable=" + totals.payableAmount());
    var payment = invoice.paymentInstruction();
    if (payment != null) {
      out.add("payment.means=" + payment.meansCode());
      payment.creditTransfers()
             .forEach(account -> {
               out.add("payment.iban=" + account.iban());
               out.add("payment.account=" + account.iban() + "|" + account.accountName() + "|" + account.bic());
             });
      if (payment.directDebit() != null) {
        out.add("payment.debit=" + payment.directDebit());
      }
    }
    invoice.notes()
           .forEach(note -> out.add("note=" + note.text()));
    invoice.supportingDocuments()
           .forEach(document -> {
             out.add("document=" + document.reference());
             if (document.attachment() != null) {
               out.add("attachment.sha256=" + sha256(document.attachment()
                                                             .content()));
             }
           });
    invoice.adjustments()
           .forEach(adjustment -> out.add("adjustment=" + adjustment.charge() + ":" + adjustment.amount()));
    out.add("delivery=" + (invoice.delivery() != null));
    out.sort(String::compareTo);
    return out;
  }

  private static void party(List<String> out,
                            String role,
                            io.github.nalukit.paniolo.model.common.Party party) {
    var address = party.address();
    out.add(role + ".name=" + party.name());
    out.add(role + ".vat=" + party.vatId());
    if (address != null) {
      out.add(role + ".country=" + address.country());
      out.add(role + ".city=" + address.city());
      out.add(role + ".street=" + address.street());
      out.add(role + ".postcode=" + address.postalCode());
    }
  }

  private static void lines(List<String> out,
                            String prefix,
                            List<InvoiceLine> lines) {
    for (InvoiceLine line : lines) {
      String id = prefix + line.id();
      out.add("line " + id + "=" + line.name() + "|" + line.quantity() + "|" + line.unitCode() + "|" + line.netUnitPrice() + "|" + line.netAmount() + "|" + (line.tax() == null ?
                                                                                                                                                              null :
                                                                                                                                                              line.tax()
                                                                                                                                                                  .code() + ":" + line.tax()
                                                                                                                                                                                      .rate()));
      out.add("line.id=" + line.id());
      out.add("line.name=" + line.name());
      if (line.tax() != null) {
        out.add("line.tax=" + line.tax()
                                 .code()
                                 .getCode() + ":" + (line.tax()
                                                      .rate() == null ?
                                                  "null" :
                                                  number(line.tax()
                                                             .rate()
                                                             .toPlainString())));
      }
      lines(out,
            id + "/",
            line.subLines());
    }
  }
}

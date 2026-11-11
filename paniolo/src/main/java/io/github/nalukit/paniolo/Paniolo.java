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
import io.github.nalukit.paniolo.config.FacturXResult;
import io.github.nalukit.paniolo.config.InvoiceSyntax;
import io.github.nalukit.paniolo.config.PdfAttachment;
import io.github.nalukit.paniolo.config.PdfLayout;
import io.github.nalukit.paniolo.config.PdfProducer;
import io.github.nalukit.paniolo.config.UnknownDocumentHandling;
import io.github.nalukit.paniolo.model.Invoice;
import io.github.nalukit.paniolo.model.base.PanioloBase;
import io.github.nalukit.paniolo.model.common.ProcessControl;
import io.github.nalukit.paniolo.model.despatch.DespatchAdvice;
import io.github.nalukit.paniolo.model.order.Order;
import io.github.nalukit.paniolo.model.order.OrderXProfile;
import io.github.nalukit.paniolo.model.util.PanioloStopWatch;
import io.github.nalukit.paniolo.rules.facturx.FacturXRules;
import io.github.nalukit.paniolo.rules.xrechnung.XrechnungRules;
import io.github.nalukit.paniolo.spi.FacturXSupport;
import io.github.nalukit.paniolo.spi.PdfLayoutSupport;
import io.github.nalukit.paniolo.spi.PdfaSupport;
import io.github.nalukit.paniolo.validation.InvoiceValidator;
import io.github.nalukit.paniolo.validation.Severity;
import io.github.nalukit.paniolo.validation.ValidationException;
import io.github.nalukit.paniolo.validation.ValidationMessage;
import io.github.nalukit.paniolo.validation.ValidationProfile;
import io.github.nalukit.paniolo.validation.ValidationResult;
import io.github.nalukit.paniolo.validation.ValidationStatistics;
import io.github.nalukit.paniolo.xml.ExportVariant;
import io.github.nalukit.paniolo.xml.ImportResult;
import io.github.nalukit.paniolo.xml.InvoiceXmlReader;
import io.github.nalukit.paniolo.xml.InvoiceXmlWriter;
import io.github.nalukit.paniolo.xml.ReadMode;
import io.github.nalukit.paniolo.xml.ReadResult;
import io.github.nalukit.paniolo.xml.cii.CiiDespatchAdviceReader;
import io.github.nalukit.paniolo.xml.cii.CiiDespatchAdviceWriter;
import io.github.nalukit.paniolo.xml.cii.CiiInvoiceReader;
import io.github.nalukit.paniolo.xml.cii.CiiInvoiceWriter;
import io.github.nalukit.paniolo.xml.cii.OrderXReader;
import io.github.nalukit.paniolo.xml.cii.OrderXWriter;
import io.github.nalukit.paniolo.xml.cii.ZugferdV1InvoiceReader;
import io.github.nalukit.paniolo.xml.ubl.DespatchAdviceReader;
import io.github.nalukit.paniolo.xml.ubl.DespatchAdviceWriter;
import io.github.nalukit.paniolo.xml.ubl.UblInvoiceReader;
import io.github.nalukit.paniolo.xml.ubl.UblInvoiceWriter;
import io.github.nalukit.paniolo.xml.util.SpecificationGenerations;
import io.github.nalukit.paniolo.xml.util.XmlDocuments;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Single entry point (facade) for writing, reading, converting and validating electronic business
 * documents: XRechnung / EN 16931 invoices in UBL and CII, Factur-X / ZUGFeRD hybrid PDFs, Order-X
 * orders and despatch advices (delivery notes) in UBL and CIDA.
 *
 * <h2>Getting started</h2>
 *
 * <p>Create one instance and share it across your application. Instances are immutable and
 * thread-safe; the expensive part, compiling the XML schemas and Schematron rules, happens lazily on
 * the first validation of each document type and is cached afterwards. Reading and writing never
 * compile anything and are cheap from the first call.
 *
 * <pre>{@code
 * Paniolo paniolo = Paniolo.create();                        // official XRechnung rules, packaged
 *
 * String xml = paniolo.toXml(invoice, InvoiceSyntax.UBL);    // model -> XML
 * ValidationResult result = paniolo.validate(xml);           // document type detected automatically
 * if (!result.isValid()) {
 *   result.messages().forEach(m -> System.out.println(m.ruleId() + ": " + m.message()));
 * }
 * Invoice read = paniolo.read(xml);                          // XML -> model, UBL or CII detected
 * }</pre>
 *
 * <h2>Overview of the convenience methods</h2>
 *
 * <table>
 *   <caption>Methods by task</caption>
 *   <tr><th>Task</th><th>Methods</th></tr>
 *   <tr><td>Create an instance</td>
 *       <td>{@link #create()}, {@link #builder()}, {@link #warmUp()}</td></tr>
 *   <tr><td>Write an invoice (UBL / CII)</td>
 *       <td>{@link #toXml(Invoice, InvoiceSyntax)}, {@link #toBytes(Invoice, InvoiceSyntax)}</td></tr>
 *   <tr><td>Read an invoice (UBL, CII, ZUGFeRD 1.0, Factur-X PDF)</td>
 *       <td>{@link #read(String)}, {@link #read(byte[])}, {@link #read(InputStream)} and the
 *           variants with {@link ReadMode}</td></tr>
 *   <tr><td>Import a received invoice with its original XML and stated amounts</td>
 *       <td>{@link #importInvoice(byte[], ReadMode)},
 *           {@link #export(ImportResult, ExportVariant, InvoiceSyntax)}</td></tr>
 *   <tr><td>Validate an invoice</td>
 *       <td>{@link #validate(String)}, {@link #validate(byte[])}, {@link #validate(InputStream)},
 *           {@link #validate(byte[], ValidationProfile.Syntax)},
 *           {@link #validate(byte[], FacturXProfile)}, {@link #validate(Invoice, InvoiceSyntax)}</td></tr>
 *   <tr><td>Inspect a document</td>
 *       <td>{@link #detect(String)}, {@link #detect(byte[])}, {@link #isPdf(byte[])}</td></tr>
 *   <tr><td>Factur-X / ZUGFeRD PDF</td>
 *       <td>{@link #toFacturX(Invoice, byte[], FacturXProfile)},
 *           {@link #embedXml(byte[], byte[], FacturXProfile)} for existing XML,
 *           {@link #toFacturX(Invoice, byte[], FacturXProfile, List)},
 *           {@link #toFacturX(Invoice, FacturXProfile)},
 *           {@link #toFacturX(Invoice, FacturXProfile, PdfLayout, List)}, {@link #extractXml(byte[])},
 *           {@link #attachments(byte[])}, {@link #producer(byte[])}</td></tr>
 *   <tr><td>Visual PDF/A (rendered)</td>
 *       <td>{@link #toPdf(Invoice)}, {@link #toPdf(Invoice, PdfLayout)}, {@link #toPdf(Order)},
 *           {@link #toPdf(Order, PdfLayout)}, {@link #toPdf(DespatchAdvice)},
 *           {@link #toPdf(DespatchAdvice, PdfLayout)}, {@link #toOrderXPdf(Order)},
 *           {@link #toOrderXPdf(Order, PdfLayout, List)}, {@link #toDespatchAdvicePdf(DespatchAdvice)},
 *           {@link #toDespatchAdvicePdf(DespatchAdvice, PdfLayout, List)}</td></tr>
 *   <tr><td>Validation report as PDF/A</td>
 *       <td>{@link #toReportPdf(ValidationResult, String, byte[])},
 *           {@link #toReportPdf(ValidationResult, String, byte[], PdfLayout)}</td></tr>
 *   <tr><td>ZUGFeRD 1.0 migration</td>
 *       <td>{@link #migrateZugferd1(byte[])}, {@link #migrateZugferd1(byte[], FacturXProfile)}</td></tr>
 *   <tr><td>Order-X</td>
 *       <td>{@link #toOrderX(Order)}, {@link #toOrderXBytes(Order)}, {@link #toOrderXPdf(Order, byte[])},
 *           {@link #toOrderXPdf(Order, byte[], List)}, {@link #readOrder(byte[])},
 *           {@link #readOrder(byte[], boolean)}, {@link #readOrder(String)},
 *           {@link #validateOrder(byte[])}</td></tr>
 *   <tr><td>Despatch advice (delivery note)</td>
 *       <td>{@link #toDespatchAdvice(DespatchAdvice)}, {@link #toDespatchAdviceBytes(DespatchAdvice)},
 *           {@link #toCiiDespatchAdvice(DespatchAdvice)},
 *           {@link #toCiiDespatchAdviceBytes(DespatchAdvice)},
 *           {@link #toDespatchAdvicePdf(DespatchAdvice, byte[])},
 *           {@link #toDespatchAdvicePdf(DespatchAdvice, byte[], List)},
 *           {@link #readDespatchAdvice(byte[])}, {@link #readDespatchAdvice(byte[], boolean)},
 *           {@link #validateDespatchAdvice(byte[])}</td></tr>
 * </table>
 *
 * <h2>Optional modules</h2>
 *
 * <p>The facade itself has no PDF dependency. PDF features are discovered at runtime with
 * {@link ServiceLoader}; add the modules you need to the classpath:
 * <ul>
 *   <li>{@code io.github.nalukit:paniolo-facturx} &ndash; required by every method that reads, writes or
 *       inspects a PDF (Factur-X / ZUGFeRD, Order-X PDF, PDF delivery note). Without it these methods
 *       throw an {@link IllegalStateException} naming the missing dependency.</li>
 *   <li>{@code io.github.nalukit:paniolo-pdf-layout} &ndash; renders the visual PDF/A document of
 *       invoices, orders, and despatch advices for the {@code toPdf} methods,
 *       {@link #toFacturX(Invoice, FacturXProfile, PdfLayout, List)},
 *       {@link #toOrderXPdf(Order, PdfLayout, List)}, and
 *       {@link #toDespatchAdvicePdf(DespatchAdvice, PdfLayout, List)}; brings
 *       {@code paniolo-facturx}.</li>
 *   <li>{@code io.github.nalukit:paniolo-pdfa} &ndash; optional; adds the PDF/A conformance check
 *       (ISO 19005) to the validation of PDFs. Without it the result carries the information
 *       {@code PDFA-NOT-CHECKED}.</li>
 * </ul>
 *
 * <h2>Rules</h2>
 *
 * <p>The official KoSIT XRechnung validator configuration (which also contains the EN 16931 rules and
 * the UBL / CII schemas) is packaged in {@code paniolo-xrechnung-rules} and used by default.
 * Official Factur-X / ZUGFeRD, ZUGFeRD 1.0, French EXTENDED-CTC-FR and Order-X rules are packaged
 * in {@code paniolo-facturx-rules}. Alternative rule bundles can be configured with the
 * {@link Builder}. Peppol BIS Despatch Advice rules are not packaged and must be supplied through
 * {@link Builder#despatchAdviceRules(Path)}.
 *
 * <h2>Optional checks</h2>
 *
 * <p>The {@link Builder} switches on behavior that is off by default: an additional recalculation
 * of invoice amounts ({@link Builder#calculationCheck(boolean)}), execution statistics of the rules
 * for audits ({@link Builder#ruleStatistics(boolean)}), and a compatibility mode that reports
 * documents of unknown type as invalid instead of throwing
 * ({@link Builder#unknownDocuments(UnknownDocumentHandling)}). None of them changes the official
 * validity of a supported document.
 *
 * <h2>Errors</h2>
 *
 * <p>All methods follow the same conventions, so they are not repeated in every method:
 * <ul>
 *   <li>{@code null} arguments are rejected with a {@link NullPointerException} naming the
 *       parameter, unless a parameter is documented as nullable.</li>
 *   <li>Content that cannot be processed (unknown root element, malformed XML when detecting the
 *       type, content the model cannot carry) is rejected with an {@link IllegalArgumentException}
 *       whose message says what is wrong. The validation methods that detect the type report such
 *       documents as invalid instead with {@link UnknownDocumentHandling#REPORT_INVALID}.</li>
 *   <li>Business rule violations are <em>not</em> exceptions: validation methods return a
 *       {@link ValidationResult}; check {@link ValidationResult#isValid()}.</li>
 *   <li>I/O errors while reading an {@link InputStream} are thrown as {@link UncheckedIOException}.</li>
 * </ul>
 */
public final class Paniolo
    extends PanioloBase {
  private static final String                               UBL                   = "urn:oasis:names:specification:ubl:schema:xsd:";
  private static final String                               CII                   = "urn:un:unece:uncefact:data:standard:CrossIndustryInvoice:100";
  private static final String                               RAM                   = "urn:un:unece:uncefact:data:standard:ReusableAggregateBusinessInformationEntity:100";
  /**
   * The CIUS part of the specification identifier of XRechnung 1.2 to 2.3.
   */
  private static final Pattern                              XRECHNUNG_VERSION     = Pattern.compile(":kosit:standard:xrechnung_(\\d+\\.\\d+)");
  /**
   * The extension part of the specification identifier of XRechnung 2.x.
   */
  private static final String                               XRECHNUNG_2_EXTENSION = "#conformant#urn:xoev-de:kosit:extension:xrechnung_";
  private static final ZugferdV1InvoiceReader               ZUGFERD_1             = new ZugferdV1InvoiceReader();
  private static final OrderXWriter                         ORDER_WRITER          = new OrderXWriter();
  private static final OrderXReader                         ORDER_READER          = new OrderXReader();
  private static final DespatchAdviceWriter                 DESPATCH_WRITER       = new DespatchAdviceWriter();
  private static final DespatchAdviceReader                 DESPATCH_READER       = new DespatchAdviceReader();
  private static final CiiDespatchAdviceWriter              CIDA_WRITER           = new CiiDespatchAdviceWriter();
  private static final CiiDespatchAdviceReader              CIDA_READER           = new CiiDespatchAdviceReader();
  /**
   * Profile name of the result of an undetectable document with
   * {@link UnknownDocumentHandling#REPORT_INVALID}.
   */
  static final         String                               UNKNOWN_DOCUMENT      = "Unknown document";
  private final        Supplier<Path>                       rules;
  private final        Supplier<Path>                       facturXRules;
  private final        Supplier<Path>                       franceRules;
  private final        Supplier<Path>                       orderXRules;
  private final        Path                                 despatchAdviceRules;
  private final        boolean                              calculationCheck;
  private final        boolean                              ruleStatistics;
  private final        UnknownDocumentHandling              unknownDocuments;
  private final        String                               xrechnungRelease;
  private final        String                               facturXRelease;
  private final        String                               orderXRelease;
  private final        Map<InvoiceSyntax, InvoiceXmlWriter> writers               = new EnumMap<>(Map.of(InvoiceSyntax.UBL,
                                                                                                         new UblInvoiceWriter(),
                                                                                                         InvoiceSyntax.CII,
                                                                                                         new CiiInvoiceWriter()));
  private final        Map<InvoiceSyntax, InvoiceXmlReader> readers               = new EnumMap<>(Map.of(InvoiceSyntax.UBL,
                                                                                                         new UblInvoiceReader(),
                                                                                                         InvoiceSyntax.CII,
                                                                                                         new CiiInvoiceReader()));
  private final        Map<Object, CompiledValidator>       validators            = new ConcurrentHashMap<>();

  private Paniolo(Supplier<Path> rules,
                  Supplier<Path> facturXRules,
                  Supplier<Path> franceRules,
                  Supplier<Path> orderXRules,
                  Path despatchAdviceRules,
                  boolean calculationCheck,
                  boolean ruleStatistics,
                  UnknownDocumentHandling unknownDocuments,
                  String xrechnungRelease,
                  String facturXRelease,
                  String orderXRelease) {
    this.rules               = rules;
    this.facturXRules        = facturXRules;
    this.franceRules         = franceRules;
    this.orderXRules         = orderXRules;
    this.despatchAdviceRules = despatchAdviceRules;
    this.calculationCheck    = calculationCheck;
    this.ruleStatistics      = ruleStatistics;
    this.unknownDocuments    = unknownDocuments;
    this.xrechnungRelease    = xrechnungRelease;
    this.facturXRelease      = facturXRelease;
    this.orderXRelease       = orderXRelease;
  }

  /**
   * Returns whether the given bytes are a PDF document, judged by the PDF header {@code %PDF-}.
   *
   * <p>Like PDF readers, the check tolerates up to 1024 bytes of leading garbage before the header.
   * It looks at the header only; it does not prove that the document is complete, readable or
   * PDF/A. The method is cheap and needs no optional module, so it is a good way to dispatch
   * incoming files:
   *
   * <pre>{@code
   * byte[] received = Files.readAllBytes(path);
   * if (Paniolo.isPdf(received)) {
   *   byte[] xml = paniolo.extractXml(received);   // hybrid invoice, needs paniolo-facturx
   * }
   * }</pre>
   *
   * <p>The methods of this class that accept "XML or PDF" use this check internally, so you do not
   * need to call it before {@link #read(byte[])} or {@link #validate(byte[])}.
   *
   * @param content the document content; must not be {@code null}
   * @return {@code true} if the content starts with a PDF header, {@code false} otherwise (also for
   * empty content)
   * @throws NullPointerException if {@code content} is {@code null}
   */
  public static boolean isPdf(byte[] content) {
    // The PDF header may follow up to 1024 bytes of leading garbage.
    int limit = Math.min(content.length,
                         1024 + 5);
    for (int i = 0; i + 5 <= limit; i++) {
      if (content[i] == '%' && content[i + 1] == 'P' && content[i + 2] == 'D' && content[i + 3] == 'F' && content[i + 4] == '-') {
        return true;
      }
    }
    return false;
  }

  private static void checkFacturXProfile(Invoice invoice,
                                          FacturXProfile profile) {
    if (!profile.isPdfWritable()) {
      throw new IllegalArgumentException("Profile " + profile.getConformanceLevel() + " is exchanged as CII XML; write it with toXml(invoice, InvoiceSyntax.CII), or use the profile EXTENDED for a Factur-X PDF");
    }
    if (!profile.getSpecificationId()
                .equals(invoice.processControl()
                               .specificationId())) {
      throw new IllegalArgumentException("Factur-X profile " +
                                         profile +
                                         " requires specification identifier (BT-24) " +
                                         profile.getSpecificationId() +
                                         ", but the invoice has " +
                                         invoice.processControl()
                                                .specificationId());
    }
  }

  private static PdfLayoutSupport pdfLayout() {
    return PdfLayoutHolder.SUPPORT.orElseThrow(() -> new IllegalStateException("Rendering a PDF requires the dependency io.github.nalukit:paniolo-pdf-layout"));
  }

  private static FacturXSupport facturX() {
    return FacturXHolder.SUPPORT.orElseThrow(() -> new IllegalStateException("Factur-X / ZUGFeRD support requires the dependency io.github.nalukit:paniolo-facturx"));
  }

  private static ValidationProfile.Syntax detect(String namespace,
                                                 String localName) {
    var syntax = syntax(namespace,
                        localName);
    if (syntax != null) {
      return syntax;
    }
    if (ZugferdV1InvoiceReader.RSM.equals(namespace)) {
      throw new IllegalArgumentException("ZUGFeRD 1.0 is no EN 16931 invoice; validate it with Paniolo.validate(byte[]) or migrate it with Paniolo.migrateZugferd1");
    }
    throw new IllegalArgumentException("Unsupported invoice document: {" + namespace + "}" + localName);
  }

  /**
   * Returns the EN 16931 document type of a root element, or null if it is none.
   */
  private static ValidationProfile.Syntax syntax(String namespace,
                                                 String localName) {
    if ((UBL + "Invoice-2").equals(namespace) && "Invoice".equals(localName)) {
      return ValidationProfile.Syntax.UBL_INVOICE;
    }
    if ((UBL + "CreditNote-2").equals(namespace) && "CreditNote".equals(localName)) {
      return ValidationProfile.Syntax.UBL_CREDIT_NOTE;
    }
    if (CII.equals(namespace) && "CrossIndustryInvoice".equals(localName)) {
      return ValidationProfile.Syntax.CII;
    }
    return null;
  }

  private static byte[] readAll(InputStream input) {
    try {
      return Objects.requireNonNull(input,
                                    "input")
                    .readAllBytes();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * Creates an instance with the default configuration: validation against the official KoSIT
   * XRechnung rules (current and older versions) packaged in {@code paniolo-xrechnung-rules} and the
   * official Factur-X / ZUGFeRD rules (incl. ZUGFeRD 1.0) packaged in {@code paniolo-facturx-rules},
   * no Order-X or Peppol Despatch Advice rules.
   *
   * <p>This is the right choice for most applications. Creating the instance is cheap; the packaged
   * rules are extracted and compiled only when the first validation needs them. Create the instance
   * once and reuse it, e.g. as a singleton or a Spring bean, so the compiled validators are reused:
   *
   * <pre>{@code
   * private static final Paniolo PANIOLO = Paniolo.create();
   * }</pre>
   *
   * <p>Shortcut for {@code Paniolo.builder().build()}; use {@link #builder()} to configure further
   * rules or optional checks.
   *
   * @return a new, thread-safe instance
   */
  public static Paniolo create() {
    return builder().build();
  }

  /**
   * Starts the configuration of an instance with custom or additional rules, or with optional
   * checks.
   *
   * <pre>{@code
   * Paniolo paniolo = Paniolo.builder()
   *                          .rules(Path.of("/opt/kosit/xrechnung-3.0.2"))          // optional: other KoSIT bundle
   *                          .facturXRules(Path.of("/opt/factur-x/1.09.2"))        // optional: other Factur-X release
   *                          .orderXRules(Path.of("/opt/order-x/1.0"))             // optional: Order-X
   *                          .despatchAdviceRules(Path.of("/opt/peppol/despatch.xslt")) // optional
   *                          .calculationCheck(true)                               // optional: recalculate amounts
   *                          .unknownDocuments(UnknownDocumentHandling.REPORT_INVALID) // optional: Mustang compatible
   *                          .build();
   * }</pre>
   *
   * <p>Every setting is optional; a builder without settings yields the same instance as
   * {@link #create()}.
   *
   * @return a new builder
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Returns true if the document is ZUGFeRD 1.0 XML.
   */
  private static boolean isZugferd1(byte[] xml) {
    return ZugferdV1InvoiceReader.RSM.equals(rootNamespace(xml));
  }

  /**
   * Returns the namespace of the root element, or null for malformed XML.
   */
  private static String rootNamespace(byte[] xml) {
    var root = rootName(xml);
    return root == null ?
           null :
           root.getNamespaceURI();
  }

  /**
   * Returns true if the document is a CIDA despatch advice.
   */
  private static boolean isCida(byte[] xml) {
    var root = rootName(xml);
    return root != null && CiiDespatchAdviceReader.isCida(root.getNamespaceURI()
                                                              .isEmpty() ?
                                                          null :
                                                          root.getNamespaceURI(),
                                                          root.getLocalPart());
  }

  /**
   * Returns the name of the root element, or null for malformed XML.
   */
  private static QName rootName(byte[] xml) {
    return XmlDocuments.rootName(new ByteArrayInputStream(xml));
  }


  /**
   * Returns the Factur-X profile a standalone CII document is validated with: MINIMUM, BASIC WL,
   * BASIC, or EXTENDED from BT-24; null for UBL and all other documents, which are validated
   * against EN 16931 and XRechnung.
   */
  private static FacturXProfile facturXProfile(ValidationProfile.Syntax type,
                                               String specificationId) {
    if (type != ValidationProfile.Syntax.CII) {
      return null;
    }
    var profile = FacturXProfile.ofSpecificationId(specificationId);
    return profile == null || profile == FacturXProfile.EN16931 || profile == FacturXProfile.XRECHNUNG ?
           null :
           profile;
  }

  /**
   * Prefixes of the rule identifiers of the XRechnung CIUS: the German rules, those of the
   * extension, the temporary rules, and the Peppol rules XRechnung adopts. The EN 16931 rules
   * (e.g. {@code BR-DEC-01}) do not start with any of them.
   */
  private static final List<String> XRECHNUNG_RULE_PREFIXES = List.of("BR-DE-",
                                                                      "BR-DEX-",
                                                                      "BR-TMP-",
                                                                      "PEPPOL-EN16931-");

  /**
   * Returns true if a specification identifier states XRechnung, its extension or an older version
   * of it, e.g. {@code urn:cen.eu:en16931:2017#compliant#urn:xeinkauf.de:kosit:xrechnung_3.0}.
   */
  private static boolean statesXrechnung(String specificationId) {
    return specificationId != null && specificationId.toLowerCase(Locale.ROOT)
                                                     .contains("xrechnung");
  }

  /**
   * Reports the findings of the XRechnung rules of a document that does not state XRechnung as
   * information: XRechnung is a CIUS of EN 16931, and its rules (e.g. {@code BR-DE-15}, buyer
   * reference) do not apply to a plain EN 16931 invoice. The findings stay visible, e.g. for
   * deciding whether the document could be sent as XRechnung; a recipient that requires XRechnung
   * rejects on them with an {@link io.github.nalukit.paniolo.validation.AcceptancePolicy}. The
   * Peppol rules stay as they are for a document that states Peppol BIS Billing. If any finding
   * was changed, the information {@code XR-NOT-STATED} explains why.
   */
  private static ValidationResult xrechnungRulesInformative(String specificationId,
                                                            ValidationResult result) {
    var     messages   = new ArrayList<ValidationMessage>();
    boolean downgraded = false;
    for (var message : result.messages()) {
      if (message.severity() != Severity.INFO && isXrechnungRule(message.ruleId(),
                                                                 specificationId)) {
        messages.add(new ValidationMessage(message.ruleId(),
                                           Severity.INFO,
                                           message.message(),
                                           message.location(),
                                           message.stage()));
        downgraded = true;
      } else {
        messages.add(message);
      }
    }
    if (!downgraded) {
      return result;
    }
    messages.add(new ValidationMessage("XR-NOT-STATED",
                                       Severity.INFO,
                                       "The document states " +
                                       (specificationId == null ?
                                        "no specification identifier" :
                                        specificationId) +
                                       ", not XRechnung; the findings of the XRechnung rules are reported as information and do not make it invalid",
                                       "",
                                       ValidationMessage.Stage.SCHEMATRON));
    return result.withMessages(messages);
  }

  /**
   * Returns true if a rule belongs to the XRechnung CIUS and does not apply to a document of the
   * specification identifier. The Peppol rules {@code PEPPOL-EN16931} apply to a document that
   * states Peppol BIS Billing.
   */
  private static boolean isXrechnungRule(String ruleId,
                                         String specificationId) {
    if (ruleId == null) {
      return false;
    }
    if (ruleId.startsWith("PEPPOL-EN16931-") && specificationId != null && specificationId.toLowerCase(Locale.ROOT)
                                                                                      .contains("peppol")) {
      return false;
    }
    return XRECHNUNG_RULE_PREFIXES.stream()
                                  .anyMatch(ruleId::startsWith);
  }

  /**
   * Returns true if a UBL Invoice or CII document states the specification identifier of the
   * XRechnung extension. UBL CreditNote has no extension scenario and is validated as XRechnung.
   */
  private static boolean isXrechnungExtension(ValidationProfile.Syntax type,
                                              String specificationId) {
    if (type == ValidationProfile.Syntax.UBL_CREDIT_NOTE || specificationId == null) {
      return false;
    }
    String id = specificationId.trim();
    return ProcessControl.XRECHNUNG_3_EXTENSION.equals(id) || id.contains(XRECHNUNG_2_EXTENSION);
  }

  /**
   * Returns the older XRechnung version, e.g. {@code 2.3}, that a specification identifier states,
   * if its rules are packaged; null for XRechnung 3 and all other identifiers.
   */
  private static String olderXrechnungVersion(String specificationId) {
    if (specificationId == null) {
      return null;
    }
    var matcher = XRECHNUNG_VERSION.matcher(specificationId);
    return matcher.find() && XrechnungRules.LEGACY_VERSIONS.contains(matcher.group(1)) ?
           matcher.group(1) :
           null;
  }

  /**
   * Reads the specification identifier (BT-24) of a UBL or CII invoice, or null.
   */
  private static String specificationId(ValidationProfile.Syntax type,
                                        Supplier<Reader> xml) {
    return type == ValidationProfile.Syntax.CII ?
           guideline(xml.get()) :
           customizationId(xml.get());
  }

  private static Supplier<Reader> reader(byte[] xml) {
    // A UTF-8 byte order mark would reach the XML parser as content before the prolog.
    int start = xml.length >= 3 && (xml[0] & 0xFF) == 0xEF && (xml[1] & 0xFF) == 0xBB && (xml[2] & 0xFF) == 0xBF ?
                3 :
                0;
    return () -> new java.io.InputStreamReader(new ByteArrayInputStream(xml,
                                                                        start,
                                                                        xml.length - start),
                                               StandardCharsets.UTF_8);
  }

  /**
   * Reads the UBL customization identifier (BT-24), a direct child of the root element, or null.
   */
  private static String customizationId(Reader xml) {
    var factory = XMLInputFactory.newDefaultFactory();
    factory.setProperty(XMLInputFactory.SUPPORT_DTD,
                        false);
    factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES,
                        false);
    try {
      var reader = factory.createXMLStreamReader(xml);
      try {
        int depth = 0;
        while (reader.hasNext()) {
          int event = reader.next();
          if (event == XMLStreamConstants.START_ELEMENT) {
            depth++;
            if (depth == 2 && "CustomizationID".equals(reader.getLocalName())) {
              return reader.getElementText();
            }
          } else if (event == XMLStreamConstants.END_ELEMENT) {
            depth--;
          }
        }
      } finally {
        reader.close();
      }
    } catch (XMLStreamException e) {
      // Malformed XML is reported by the validation itself.
      return null;
    }
    return null;
  }

  /**
   * Reads the specification identifier (BT-24) from the document context, or null.
   */
  private static String guideline(Reader xml) {
    var factory = XMLInputFactory.newDefaultFactory();
    factory.setProperty(XMLInputFactory.SUPPORT_DTD,
                        false);
    factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES,
                        false);
    try {
      var reader = factory.createXMLStreamReader(xml);
      try {
        boolean guideline = false;
        while (reader.hasNext()) {
          if (reader.next() != XMLStreamConstants.START_ELEMENT) {
            continue;
          }
          String name = reader.getLocalName();
          if ("ExchangedDocument".equals(name)) {
            return null;
          }
          if ("GuidelineSpecifiedDocumentContextParameter".equals(name)) {
            guideline = true;
          } else if (guideline && "ID".equals(name)) {
            return reader.getElementText();
          }
        }
      } finally {
        reader.close();
      }
    } catch (XMLStreamException e) {
      // Malformed XML is reported by the validation itself.
      return null;
    }
    return null;
  }

  /**
   * The producing software as information, so that systematic errors can be traced to the
   * software that produced the document.
   */
  private static ValidationMessage producerMessage(PdfProducer producer) {
    return new ValidationMessage("FX-PRODUCER",
                                 Severity.INFO,
                                 "Produced by " + producer.describe(),
                                 "/Info",
                                 ValidationMessage.Stage.HYBRID);
  }

  /**
   * Detects the document type from the root element; see {@link #detect(byte[])}.
   */
  /**
   * Detects the document type for a validation. With {@link UnknownDocumentHandling#THROW} an
   * undetectable document throws; with {@link UnknownDocumentHandling#REPORT_INVALID} its finding is
   * added to {@code findings} and the result is null.
   */
  private ValidationProfile.Syntax detect(byte[] xml,
                                          List<ValidationMessage> findings,
                                          ValidationMessage.Stage stage) {
    try {
      return detectRoot(xml);
    } catch (IllegalArgumentException e) {
      if (unknownDocuments == UnknownDocumentHandling.THROW) {
        throw e;
      }
      if (e.getCause() instanceof XMLStreamException parse) {
        var location = parse.getLocation();
        // StAX puts the position before the text; the position goes into the location instead.
        String text  = parse.getMessage() == null ?
                       "" :
                       parse.getMessage();
        int    start = text.indexOf("Message: ");
        findings.add(new ValidationMessage("XML-PARSE",
                                           Severity.FATAL,
                                           e.getMessage() + ": " + (start < 0 ?
                                                                    text :
                                                                    text.substring(start + "Message: ".length())).strip(),
                                           location == null ?
                                           "" :
                                           "line " + location.getLineNumber() + ", column " + location.getColumnNumber(),
                                           stage));
      } else {
        findings.add(new ValidationMessage("PANIOLO-UNKNOWN-DOCUMENT",
                                           Severity.FATAL,
                                           e.getMessage(),
                                           "",
                                           stage));
      }
      return null;
    }
  }

  /**
   * The invalid result of a document whose type cannot be detected.
   */
  private ValidationResult unknownDocument(List<ValidationMessage> findings) {
    return ValidationResult.combine(UNKNOWN_DOCUMENT,
                                    findings)
                           .withStatistics(noStatistics());
  }

  /**
   * Empty statistics if they are collected: no rule ran.
   */
  private ValidationStatistics noStatistics() {
    return ruleStatistics ?
           new ValidationStatistics(List.of(),
                                    List.of()) :
           null;
  }

  private static ValidationProfile.Syntax detectRoot(byte[] xml) {
    var factory = XMLInputFactory.newDefaultFactory();
    factory.setProperty(XMLInputFactory.SUPPORT_DTD,
                        false);
    factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES,
                        false);
    try {
      var reader = factory.createXMLStreamReader(new ByteArrayInputStream(xml));
      try {
        while (reader.hasNext()) {
          if (reader.next() == XMLStreamConstants.START_ELEMENT) {
            return detect(reader.getNamespaceURI(),
                          reader.getLocalName());
          }
        }
      } finally {
        reader.close();
      }
    } catch (XMLStreamException e) {
      throw new IllegalArgumentException("Invoice XML is not well-formed",
                                         e);
    }
    throw new IllegalArgumentException("Invoice XML has no root element");
  }

  /**
   * Adds the warning {@code FX-ZUGFERD-1} to the result of standalone ZUGFeRD 1.0 XML.
   */
  private static ValidationResult validateZugferd1(ValidationResult result) {
    var messages = new ArrayList<>(result.messages());
    messages.add(zugferd1Outdated("",
                                  ValidationMessage.Stage.XML));
    return result.withMessages(messages);
  }

  private static ValidationMessage zugferd1Outdated(String location,
                                                    ValidationMessage.Stage stage) {
    return new ValidationMessage("FX-ZUGFERD-1",
                                 Severity.WARNING,
                                 "ZUGFeRD 1.0 does not meet EN 16931 and is no longer accepted as an electronic invoice; migrate the document with Paniolo.migrateZugferd1",
                                 location,
                                 stage);
  }

  /**
   * The rules of the packaged release of an older XRechnung version, named after that release.
   */
  private static ValidationProfile olderXrechnung(ValidationProfile.Syntax type,
                                                  String version,
                                                  boolean extension) {
    Path              bundle = XrechnungRules.legacyBundle(version);
    ValidationProfile profile;
    try {
      profile = extension ?
                ValidationProfile.xrechnungExtension(bundle,
                                                     type) :
                ValidationProfile.xrechnung(bundle,
                                            type);
    } catch (ValidationException e) {
      if (!extension) {
        throw e;
      }
      // Older releases have no extension scenario for every syntax (XRechnung 1.2 none at all).
      profile = ValidationProfile.xrechnung(bundle,
                                            type);
    }
    return new ValidationProfile(profile.name() + ", XRechnung " + XrechnungRules.legacyVersion(version),
                                 profile.resourceRoot(),
                                 profile.schema(),
                                 profile.schematronStylesheets(),
                                 profile.severityOverrides(),
                                 profile.documents());
  }

  /**
   * Returns true if the seller and the buyer of a CII invoice both have a French postal address
   * (BT-40, BT-55), which makes the French rules BR-FR (flux 2) apply.
   */
  private static boolean isFrenchDomestic(Reader xml) {
    var factory = XMLInputFactory.newDefaultFactory();
    factory.setProperty(XMLInputFactory.SUPPORT_DTD,
                        false);
    factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES,
                        false);
    String seller = null;
    String buyer  = null;
    try {
      var reader = factory.createXMLStreamReader(xml);
      try {
        var path = new ArrayDeque<String>();
        while (reader.hasNext()) {
          int event = reader.next();
          if (event == XMLStreamConstants.START_ELEMENT) {
            path.addLast(reader.getLocalName());
            String current = String.join("/",
                                         path);
            if (current.endsWith("ApplicableHeaderTradeAgreement/SellerTradeParty/PostalTradeAddress/CountryID")) {
              seller = reader.getElementText()
                             .strip();
              path.removeLast();
            } else if (current.endsWith("ApplicableHeaderTradeAgreement/BuyerTradeParty/PostalTradeAddress/CountryID")) {
              buyer = reader.getElementText()
                            .strip();
              path.removeLast();
            }
          } else if (event == XMLStreamConstants.END_ELEMENT) {
            path.removeLast();
          }
        }
      } finally {
        reader.close();
      }
    } catch (XMLStreamException e) {
      // Malformed XML is reported by the validation itself.
      return false;
    }
    return "FR".equals(seller) && "FR".equals(buyer);
  }

  /**
   * Compiles the XRechnung validators for UBL Invoice, UBL CreditNote and CII now instead of on the
   * first validation, so the first request of a server does not pay for it.
   *
   * <p>Compiling is the expensive part of the first validation of each document type. Call this
   * method once at application start, optionally in a background thread; validations running
   * meanwhile wait only for the document type they need:
   *
   * <pre>{@code
   * private static final Paniolo PANIOLO = Paniolo.create()
   *                                               .warmUp();
   * }</pre>
   *
   * <p>Other rules (EN 16931 for Factur-X profiles, the XRechnung extension, Factur-X, Order-X and
   * Peppol rules) are still compiled on their first use. Calling the method again does nothing.
   *
   * @return this instance
   * @throws ValidationException if the configured rules cannot be compiled
   */
  public Paniolo warmUp() {
    PanioloStopWatch watch = super.logStart("Paniolo.warmUp");
    for (var type : ValidationProfile.Syntax.values()) {
      validator(type);
    }
    super.logStop(watch);
    return this;
  }

  /**
   * Validates a Factur-X / ZUGFeRD PDF in three stages and combines the findings: the container
   * (embedded file and XMP metadata), PDF/A conformance if {@code paniolo-pdfa} is present, and the
   * embedded invoice XML.
   */
  private ValidationResult validateHybrid(byte[] pdf) {
    var support  = facturX();
    var messages = new ArrayList<ValidationMessage>(support.checkContainer(pdf));
    var pdfa     = pdfa(pdf);
    messages.addAll(pdfa.messages());
    if (messages.stream()
                .noneMatch(message -> "FX-NOT-PDF".equals(message.ruleId()))) {
      try {
        messages.add(producerMessage(support.producer(pdf)));
      } catch (IllegalArgumentException | UncheckedIOException | UnsupportedOperationException e) {
        // The producer is information only; unreadable documents are reported by the other stages.
      }
    }
    String               name          = "Factur-X";
    ValidationStatistics xmlStatistics = null;
    boolean embedded = messages.stream()
                               .noneMatch(message -> "FX-NO-INVOICE".equals(message.ruleId()) || "FX-NOT-PDF".equals(message.ruleId()) || "FX-NO-CONTENT".equals(message.ruleId()));
    if (embedded) {
      var invoice = support.extract(pdf);
      var xml     = invoice.xml();
      if (isZugferd1(xml)) {
        messages.add(zugferd1Outdated("/Root/Names/EmbeddedFiles/" + invoice.fileName(),
                                      ValidationMessage.Stage.HYBRID));
        var result = zugferd1Validator().validate(xml);
        messages.addAll(result.messages());
        return ValidationResult.combine("ZUGFeRD 1.0 / " + result.profileName(),
                                        messages)
                               .withStatistics(ValidationStatistics.merge(pdfa.statistics(),
                                                                          result.statistics()));
      }
      var type = detect(xml,
                        messages,
                        ValidationMessage.Stage.HYBRID);
      if (type == null) {
        return ValidationResult.combine(name,
                                        messages)
                               .withStatistics(ValidationStatistics.merge(pdfa.statistics(),
                                                                          noStatistics()));
      }
      var result = validateFacturX(invoice.profile(),
                                   type,
                                   specificationId(type,
                                                   reader(xml)),
                                   reader(xml),
                                   validator -> validator.validate(xml));
      messages.addAll(result.messages());
      xmlStatistics = result.statistics();
      name          = "Factur-X " +
             (invoice.profile() == null ?
              invoice.fileName() :
              invoice.profile()
                     .getConformanceLevel()) +
             " / " +
             result.profileName();
    }
    return ValidationResult.combine(name,
                                    messages)
                           .withStatistics(ValidationStatistics.merge(pdfa.statistics(),
                                                                      xmlStatistics));
  }

  /**
   * Writes an invoice as XML in the given syntax.
   *
   * <pre>{@code
   * String ubl = paniolo.toXml(invoice, InvoiceSyntax.UBL);   // OASIS UBL 2.1 Invoice or CreditNote
   * String cii = paniolo.toXml(invoice, InvoiceSyntax.CII);   // UN/CEFACT CII D16B
   * }</pre>
   *
   * <p><b>Which syntax?</b> Both carry the same EN 16931 content and are accepted for XRechnung.
   * <ul>
   *   <li>{@link InvoiceSyntax#UBL} &ndash; common for Peppol and German public authorities. An
   *       invoice with type code 381 (credit note) is written as UBL {@code CreditNote}, all others as
   *       {@code Invoice}. UBL needs invoice lines; invoices with stated totals only (Factur-X MINIMUM /
   *       BASIC WL) and EXTENDED content cannot be written as UBL.</li>
   *   <li>{@link InvoiceSyntax#CII} &ndash; the syntax of Factur-X / ZUGFeRD; required for
   *       {@link #toFacturX(Invoice, byte[], FacturXProfile)} and for all Factur-X profiles.</li>
   * </ul>
   *
   * <p><b>Writing does not validate.</b> The writer only rejects content that the syntax cannot
   * express. Call {@link #validate(String)} (or {@link #validate(Invoice, InvoiceSyntax)}) before
   * sending the document to check the EN 16931 and XRechnung business rules.
   *
   * <p>The XML declaration states UTF-8; when writing the string to a file or stream, encode it as
   * UTF-8, or use {@link #toBytes(Invoice, InvoiceSyntax)} directly.
   *
   * <p>Paniolo writes EN 16931:2017 (XRechnung up to 3.0, Factur-X / ZUGFeRD 2.x). An invoice whose
   * specification identifier (BT-24) states EN 16931:2026, e.g. XRechnung 4.0, is rejected until the
   * official artefacts of that version are published; use the factories of {@link ProcessControl}.
   *
   * @param invoice the invoice to write; must not be {@code null}
   * @param syntax  the target syntax; must not be {@code null}
   * @return the invoice XML
   * @throws IllegalArgumentException if the invoice contains content the chosen syntax cannot
   *                                  express, e.g. stated totals without lines in UBL, or XRechnung
   *                                  extension content without the extension specification identifier,
   *                                  or if its specification identifier states EN 16931:2026
   * @see #toBytes(Invoice, InvoiceSyntax)
   * @see #read(String)
   */
  public String toXml(Invoice invoice,
                      InvoiceSyntax syntax) {
    PanioloStopWatch watch  = super.logStart("Paniolo.toXml");
    String           result = writer(syntax).toXml(invoice);
    super.logStop(watch);
    return result;
  }

  /**
   * Writes an invoice as UTF-8 encoded XML in the given syntax.
   *
   * <p>Same as {@link #toXml(Invoice, InvoiceSyntax)}, but returns the bytes ready to be stored,
   * sent or embedded, without any risk of a wrong character encoding:
   *
   * <pre>{@code
   * Files.write(Path.of("invoice.xml"), paniolo.toBytes(invoice, InvoiceSyntax.UBL));
   * }</pre>
   *
   * @param invoice the invoice to write; must not be {@code null}
   * @param syntax  the target syntax; must not be {@code null}
   * @return the invoice XML encoded as UTF-8
   * @throws IllegalArgumentException if the invoice contains content the chosen syntax cannot
   *                                  express, or its specification identifier states EN 16931:2026
   *                                  (see {@link #toXml(Invoice, InvoiceSyntax)})
   * @see #toXml(Invoice, InvoiceSyntax)
   */
  public byte[] toBytes(Invoice invoice,
                        InvoiceSyntax syntax) {
    PanioloStopWatch watch  = super.logStart("Paniolo.toBytes");
    byte[]           result = writer(syntax).toBytes(invoice);
    super.logStop(watch);
    return result;
  }

  /**
   * Reads invoice XML into the invoice model; the syntax is detected from the root element.
   *
   * <p>Supported documents:
   * <ul>
   *   <li>UBL 2.1 {@code Invoice} and {@code CreditNote}</li>
   *   <li>UN/CEFACT CII {@code CrossIndustryInvoice} (XRechnung, EN 16931 and all Factur-X /
   *       ZUGFeRD 2.x profiles)</li>
   *   <li>ZUGFeRD 1.0 {@code CrossIndustryDocument}; the invoice is mapped to the current model
   *       (see also {@link #migrateZugferd1(byte[])})</li>
   * </ul>
   *
   * <pre>{@code
   * Invoice invoice = paniolo.read(xml);
   * String number = invoice.number();
   * }</pre>
   *
   * <p>The document is read in {@link ReadMode#STRICT}: if line amounts or totals of the document
   * differ from what the model calculates, the document is rejected instead of silently changing
   * amounts. Use {@link #read(String, ReadMode)} with {@link ReadMode#LENIENT} to read such documents
   * anyway.
   *
   * <p>Reading does not validate. A document can be readable and still violate business rules; call
   * {@link #validate(String)} to check them.
   *
   * <p>This method returns the model only. A few values the model cannot keep are not rejected but
   * reported as warnings, e.g. a payment terms line that looks like a cash discount but is kept as
   * text ({@code READ-CASH-DISCOUNT}), a second identifier of the deliver-to location
   * ({@code READ-IDENTIFIER}), or a document of EN 16931:2026 such as XRechnung 4.0, which is read
   * with the business terms of EN 16931:2017 ({@code READ-SPECIFICATION-VERSION}). These warnings are
   * dropped here; use {@link #read(String, ReadMode)} to see them.
   *
   * @param xml the invoice XML; must not be {@code null}
   * @return the invoice
   * @throws IllegalArgumentException if the XML is malformed, has an unsupported root element,
   *                                  contains content the model does not carry, or its amounts
   *                                  deviate from the calculated ones
   * @see #read(byte[])
   * @see #read(String, ReadMode)
   */
  public Invoice read(String xml) {
    PanioloStopWatch watch = super.logStart("Paniolo.read");
    Invoice result = read(xml,
                          ReadMode.STRICT).invoice();
    super.logStop(watch);
    return result;
  }

  /**
   * Reads invoice XML in the given mode and returns the invoice together with the deviations that
   * were accepted.
   *
   * <p>The model calculates line net amounts (quantity &times; net price) and document totals
   * itself. Documents from other software sometimes state values that differ from this calculation,
   * e.g. because of rounding per line or price units. The mode decides what happens then:
   * <ul>
   *   <li>{@link ReadMode#STRICT} &ndash; such a document is rejected with an
   *       {@link IllegalArgumentException}. This is what {@link #read(String)} does.</li>
   *   <li>{@link ReadMode#LENIENT} &ndash; the document is read anyway: the net price of a line is
   *       recalculated from its stated net amount and the calculated totals are kept. Every deviation
   *       is reported as a {@link io.github.nalukit.paniolo.xml.ReadWarning}. Structural errors are
   *       still rejected.</li>
   * </ul>
   *
   * <p>In both modes, {@link ReadResult#warnings()} also reports values the model does not keep or
   * interpret, without changing any amount:
   * {@link io.github.nalukit.paniolo.xml.ReadWarning#CASH_DISCOUNT} for a payment terms line that
   * looks like a cash discount but is kept as text,
   * {@link io.github.nalukit.paniolo.xml.ReadWarning#IDENTIFIER} for an identifier beyond the one the
   * model keeps, and {@link io.github.nalukit.paniolo.xml.ReadWarning#SPECIFICATION_VERSION} for a
   * document of EN 16931:2026 (e.g. XRechnung 4.0), which is read with the business terms of
   * EN 16931:2017. So
   * {@link ReadResult#isExact()} can be {@code false} in {@link ReadMode#STRICT} as well.
   *
   * <pre>{@code
   * ReadResult result = paniolo.read(xml, ReadMode.LENIENT);
   * Invoice invoice = result.invoice();
   * if (!result.isExact()) {
   *   result.warnings().forEach(w -> log.warn("{} (line {}): {}", w.code(), w.lineId(), w.message()));
   * }
   * }</pre>
   *
   * <p>Use {@link ReadMode#LENIENT} when you must process received documents (e.g. for booking)
   * and want to keep an audit trail of the differences, and {@link ReadMode#STRICT} when the model
   * must reproduce the document exactly, e.g. before converting it into another syntax.
   *
   * @param xml  the invoice XML; must not be {@code null}
   * @param mode how deviating amounts are treated; must not be {@code null}
   * @return the invoice and the warnings: accepted deviations and values not kept
   * @throws IllegalArgumentException if the XML is malformed, has an unsupported root element,
   *                                  contains content the model does not carry, or (in
   *                                  {@link ReadMode#STRICT}) its amounts deviate
   * @see #read(byte[], ReadMode)
   */
  public ReadResult read(String xml,
                         ReadMode mode) {
    Objects.requireNonNull(xml,
                           "xml");
    PanioloStopWatch watch = super.logStart("Paniolo.read");
    ReadResult result = read(xml.getBytes(StandardCharsets.UTF_8),
                             xml,
                             mode);
    super.logStop(watch);
    return result;
  }

  /**
   * Reads an invoice from XML or from a Factur-X / ZUGFeRD PDF; the format is detected from the
   * content.
   *
   * <p>This is the most convenient method for received files, because you do not need to know what
   * you got:
   *
   * <pre>{@code
   * byte[] received = Files.readAllBytes(path);   // invoice.xml, xrechnung.xml or a hybrid PDF
   * Invoice invoice = paniolo.read(received);
   * }</pre>
   *
   * <ul>
   *   <li>XML &ndash; UBL, CII and ZUGFeRD 1.0 as described in {@link #read(String)}. The encoding is
   *       taken from the XML declaration, so prefer this method over {@link #read(String)} for files
   *       that are not UTF-8.</li>
   *   <li>PDF &ndash; the embedded invoice XML ({@code factur-x.xml}, {@code zugferd-invoice.xml},
   *       {@code xrechnung.xml}, ...) is extracted and read. Requires {@code paniolo-facturx}.
   *       Further attachments are ignored; get them with {@link #attachments(byte[])}.</li>
   * </ul>
   *
   * <p>The document is read in {@link ReadMode#STRICT}; see {@link #read(byte[], ReadMode)}.
   *
   * @param xml the invoice XML or PDF; must not be {@code null}
   * @return the invoice
   * @throws IllegalArgumentException if the document is not a supported invoice, the PDF contains
   *                                  no invoice XML, or the content cannot be read strictly
   * @throws IllegalStateException    if the document is a PDF and {@code paniolo-facturx} is missing
   */
  public Invoice read(byte[] xml) {
    PanioloStopWatch watch = super.logStart("Paniolo.read");
    Invoice result = read(xml,
                          ReadMode.STRICT).invoice();
    super.logStop(watch);
    return result;
  }

  /**
   * Reads an invoice from XML or from a Factur-X / ZUGFeRD PDF in the given mode; the format is
   * detected from the content.
   *
   * <p>Combines the format detection of {@link #read(byte[])} with the modes of
   * {@link #read(String, ReadMode)}:
   *
   * <pre>{@code
   * ReadResult result = paniolo.read(Files.readAllBytes(path), ReadMode.LENIENT);
   * }</pre>
   *
   * @param xml  the invoice XML or PDF; must not be {@code null}
   * @param mode how deviating amounts are treated; must not be {@code null}
   * @return the invoice and the accepted deviations
   * @throws IllegalArgumentException if the document is not a supported invoice, the PDF contains
   *                                  no invoice XML, or (in {@link ReadMode#STRICT}) its amounts deviate
   * @throws IllegalStateException    if the document is a PDF and {@code paniolo-facturx} is missing
   */
  public ReadResult read(byte[] xml,
                         ReadMode mode) {
    Objects.requireNonNull(xml,
                           "xml");
    PanioloStopWatch watch = super.logStart("Paniolo.read");
    ReadResult result = read(isPdf(xml) ?
                             facturX().extract(xml)
                                      .xml() :
                             xml,
                             null,
                             mode);
    super.logStop(watch);
    return result;
  }

  /**
   * Reads an invoice from a stream of XML or a Factur-X / ZUGFeRD PDF; the format is detected from
   * the content.
   *
   * <p>The stream is read completely into memory and then handled like {@link #read(byte[])}. The
   * stream is <em>not</em> closed; the caller keeps ownership:
   *
   * <pre>{@code
   * try (InputStream in = Files.newInputStream(path)) {
   *   Invoice invoice = paniolo.read(in);
   * }
   * }</pre>
   *
   * @param xml the stream with the invoice XML or PDF; must not be {@code null}
   * @return the invoice
   * @throws UncheckedIOException     if reading the stream fails
   * @throws IllegalArgumentException if the document is not a supported invoice or cannot be read strictly
   * @throws IllegalStateException    if the document is a PDF and {@code paniolo-facturx} is missing
   */
  public Invoice read(InputStream xml) {
    PanioloStopWatch watch  = super.logStart("Paniolo.read");
    Invoice          result = read(readAll(xml));
    super.logStop(watch);
    return result;
  }

  /**
   * Reads an invoice from a stream of XML or a Factur-X / ZUGFeRD PDF in the given mode.
   *
   * <p>The stream is read completely and handled like {@link #read(byte[], ReadMode)}; see
   * {@link #read(String, ReadMode)} for the modes. The stream is <em>not</em> closed.
   *
   * @param xml  the stream with the invoice XML or PDF; must not be {@code null}
   * @param mode how deviating amounts are treated; must not be {@code null}
   * @return the invoice and the accepted deviations
   * @throws UncheckedIOException     if reading the stream fails
   * @throws IllegalArgumentException if the document is not a supported invoice, or (in
   *                                  {@link ReadMode#STRICT}) its amounts deviate
   * @throws IllegalStateException    if the document is a PDF and {@code paniolo-facturx} is missing
   */
  public ReadResult read(InputStream xml,
                         ReadMode mode) {
    PanioloStopWatch watch = super.logStart("Paniolo.read");
    ReadResult result = read(readAll(xml),
                             mode);
    super.logStop(watch);
    return result;
  }

  /**
   * Imports a received invoice from XML or from a Factur-X / ZUGFeRD PDF and keeps what the sender
   * stated next to the invoice model.
   *
   * <p>{@link #read(byte[], ReadMode)} returns only the invoice model, whose amounts are always
   * consistent: in {@link ReadMode#LENIENT} a deviating line net amount changes the net price, and
   * deviating totals are replaced by the calculated ones. For received invoices that must be booked
   * or archived as stated, the import result keeps
   * <ul>
   *   <li>the original XML, unchanged ({@link ImportResult#originalXml()}); for a PDF the embedded
   *       invoice XML,</li>
   *   <li>the amounts as stated: document totals BT-106 to BT-115 and the net price and net amount
   *       of every line ({@link ImportResult#stated()}),</li>
   *   <li>the invoice model with the calculated amounts ({@link ImportResult#invoice()}),</li>
   *   <li>each stated amount that differs from the model ({@link ImportResult#deviations()}).</li>
   * </ul>
   *
   * <pre>{@code
   * ImportResult received = paniolo.importInvoice(Files.readAllBytes(path), ReadMode.LENIENT);
   * if (received.isNormalized()) {
   *   received.deviations().forEach(d -> log.warn("{} line {}: stated {}, calculated {}",
   *                                               d.businessTerm(), d.lineId(), d.stated(), d.calculated()));
   * }
   * byte[] archive = paniolo.export(received, ExportVariant.ORIGINAL, null);
   * }</pre>
   *
   * @param document the invoice XML or PDF; must not be {@code null}
   * @param mode     how deviating amounts are treated; must not be {@code null}
   * @return the original XML, the stated amounts, the invoice and their deviations
   * @throws IllegalArgumentException if the document is not a supported invoice, the PDF contains
   *                                  no invoice XML, or (in {@link ReadMode#STRICT}) its amounts deviate
   * @throws IllegalStateException    if the document is a PDF and {@code paniolo-facturx} is missing
   * @see #export(ImportResult, ExportVariant, InvoiceSyntax)
   */
  public ImportResult importInvoice(byte[] document,
                                    ReadMode mode) {
    Objects.requireNonNull(document,
                           "document");
    PanioloStopWatch watch = super.logStart("Paniolo.importInvoice");
    byte[] xml = isPdf(document) ?
                 facturX().extract(document)
                          .xml() :
                 document;
    ImportResult result = invoiceReader(xml).importXml(xml,
                                                       mode);
    super.logStop(watch);
    return result;
  }

  /**
   * Exports an imported invoice in the explicitly chosen variant.
   *
   * <ul>
   *   <li>{@link ExportVariant#ORIGINAL} &ndash; the received XML, byte for byte. A syntax, if given,
   *       must be the syntax of the received XML; the original is never converted.</li>
   *   <li>{@link ExportVariant#NORMALIZED} &ndash; the invoice model written in the given syntax. If
   *       {@link ImportResult#isNormalized()} is true, the amounts differ from the received ones. It
   *       is refused if the document states taxes of another type than VAT, e.g. AAM
   *       ({@link io.github.nalukit.paniolo.xml.ReadWarning#OTHER_TAX}).</li>
   *   <li>{@link ExportVariant#NORMALIZED_DROPPING_OTHER_TAXES} &ndash; like
   *       {@link ExportVariant#NORMALIZED}, but explicitly without those taxes; the amount due is
   *       lower than the received one.</li>
   * </ul>
   *
   * @param imported the import result; must not be {@code null}
   * @param variant  the variant to export; must not be {@code null}
   * @param syntax   the target syntax; required for {@link ExportVariant#NORMALIZED}, optional for
   *                 {@link ExportVariant#ORIGINAL}
   * @return the exported XML
   * @throws NullPointerException     if imported or variant is null, or syntax is null for
   *                                  {@link ExportVariant#NORMALIZED}
   * @throws IllegalArgumentException if the original is requested in another syntax, or the invoice
   *                                  contains content the chosen syntax cannot express
   * @see #importInvoice(byte[], ReadMode)
   */
  public byte[] export(ImportResult imported,
                       ExportVariant variant,
                       InvoiceSyntax syntax) {
    Objects.requireNonNull(imported,
                           "imported");
    PanioloStopWatch watch = super.logStart("Paniolo.export");
    if (variant == ExportVariant.ORIGINAL && syntax != null) {
      byte[] original = imported.originalXml();
      if (isZugferd1(original) || syntax(original) != syntax) {
        throw new IllegalArgumentException("The original invoice is not " + syntax + " XML; export it as " + ExportVariant.NORMALIZED + " to convert it");
      }
    }
    byte[] result = imported.export(variant,
                                    variant.isNormalized() ?
                                    writer(syntax) :
                                    null);
    super.logStop(watch);
    return result;
  }

  /**
   * Detects the document type of invoice XML from its root element, without reading or validating
   * the document.
   *
   * <p>Only the root element is parsed, so the call is fast even for large documents. Useful to route
   * documents or to log what was received:
   *
   * <pre>{@code
   * switch (paniolo.detect(xml)) {
   *   case UBL_INVOICE     -> ...;   // urn:oasis:names:specification:ubl:schema:xsd:Invoice-2
   *   case UBL_CREDIT_NOTE -> ...;   // urn:oasis:names:specification:ubl:schema:xsd:CreditNote-2
   *   case CII             -> ...;   // CrossIndustryInvoice, incl. all Factur-X / ZUGFeRD 2.x profiles
   * }
   * }</pre>
   *
   * <p>The type does not tell the profile (XRechnung, Factur-X BASIC, ...); the profile is stated in
   * the specification identifier (BT-24) of the document.
   *
   * @param xml the invoice XML; must not be {@code null}
   * @return the document type
   * @throws IllegalArgumentException if the XML is not well-formed, has no root element, or the root
   *                                  element is not a supported invoice; also for ZUGFeRD 1.0, which
   *                                  is no {@link ValidationProfile.Syntax} (validate it with
   *                                  {@link #validate(byte[])})
   * @see #detect(byte[])
   */
  public ValidationProfile.Syntax detect(String xml) {
    Objects.requireNonNull(xml,
                           "xml");
    PanioloStopWatch         watch  = super.logStart("Paniolo.detect");
    ValidationProfile.Syntax result = detect(xml.getBytes(StandardCharsets.UTF_8));
    super.logStop(watch);
    return result;
  }

  /**
   * Detects the document type of invoice XML bytes from its root element; see
   * {@link #detect(String)}.
   *
   * <p>The encoding is taken from the XML declaration. PDFs are not supported here; extract the XML
   * first with {@link #extractXml(byte[])}.
   *
   * @param xml the invoice XML; must not be {@code null}
   * @return the document type
   * @throws IllegalArgumentException if the XML is not well-formed, has no root element, or the root
   *                                  element is not a supported invoice
   */
  public ValidationProfile.Syntax detect(byte[] xml) {
    Objects.requireNonNull(xml,
                           "xml");
    PanioloStopWatch         watch  = super.logStart("Paniolo.detect");
    ValidationProfile.Syntax result = detectRoot(xml);
    super.logStop(watch);
    return result;
  }

  /**
   * Checks cheaply whether a document is an electronic invoice according to EN 16931: UBL Invoice,
   * UBL CreditNote or CII XML, or a Factur-X / ZUGFeRD 2.x PDF with such an XML embedded. Never
   * throws for unsuitable content.
   *
   * <p>Only the start of the XML is read: the root element and, for CII, the specification
   * identifier (BT-24) in the document context. For a PDF, only the cross-reference table, the
   * catalog with the embedded files and the start of the embedded invoice XML are read, without XMP
   * metadata, page content or PDF/A checks. Validity is not checked; use {@link #validate(byte[])}
   * for that.
   *
   * <pre>{@code
   * byte[] received = Files.readAllBytes(path);
   * if (paniolo.isEInvoice(received)) {
   *   ValidationResult result = paniolo.validate(received);
   * }
   * }</pre>
   *
   * <p>The Factur-X / ZUGFeRD profiles MINIMUM and BASIC WL carry no invoice lines and are no
   * electronic invoices in Germany; like ZUGFeRD 1.0, Order-X and despatch advices they yield
   * {@code false}. A CII document without BT-24 counts as an electronic invoice.
   *
   * @param xmlOrPdf invoice XML or PDF; must not be {@code null}
   * @return {@code true} if the document is an electronic invoice
   * @throws IllegalStateException if the content is a PDF and {@code paniolo-facturx} is missing
   * @see #probe(byte[])
   */
  public boolean isEInvoice(byte[] xmlOrPdf) {
    return probe(xmlOrPdf).isPresent();
  }

  /**
   * Determines cheaply the document type of an electronic invoice in XML or PDF form, like
   * {@link #isEInvoice(byte[])}, and returns it. Unlike {@link #detect(byte[])}, it accepts PDFs
   * and reports unsuitable content as empty result instead of an exception.
   *
   * @param xmlOrPdf invoice XML or PDF; must not be {@code null}
   * @return the document type, or empty if the content is no electronic invoice (also for malformed
   * XML, unreadable or encrypted PDFs, ZUGFeRD 1.0, and the profiles MINIMUM and BASIC WL)
   * @throws IllegalStateException if the content is a PDF and {@code paniolo-facturx} is missing
   */
  public Optional<ValidationProfile.Syntax> probe(byte[] xmlOrPdf) {
    Objects.requireNonNull(xmlOrPdf,
                           "xmlOrPdf");
    PanioloStopWatch watch = super.logStart("Paniolo.probe");
    ValidationProfile.Syntax result = isPdf(xmlOrPdf) ?
                                      facturX().probeInvoice(xmlOrPdf,
                                                             Paniolo::probeXml) :
                                      probeXml(new ByteArrayInputStream(xmlOrPdf));
    super.logStop(watch);
    return Optional.ofNullable(result);
  }

  /**
   * Reads the start of invoice XML: the root element and, for CII, BT-24 up to the end of the
   * document context. Returns null for no electronic invoice, MINIMUM and BASIC WL.
   */
  private static ValidationProfile.Syntax probeXml(InputStream input) {
    try {
      var reader = XmlDocuments.streamReader(input);
      try {
        while (reader.hasNext()) {
          if (reader.next() == XMLStreamConstants.START_ELEMENT) {
            var syntax = syntax(reader.getNamespaceURI(),
                                reader.getLocalName());
            if (syntax != ValidationProfile.Syntax.CII) {
              return syntax;
            }
            var profile = FacturXProfile.ofSpecificationId(ciiSpecificationId(reader));
            return profile == FacturXProfile.MINIMUM || profile == FacturXProfile.BASIC_WL ?
                   null :
                   syntax;
          }
        }
      } finally {
        reader.close();
      }
    } catch (XMLStreamException e) {
      return null;
    }
    return null;
  }

  /**
   * BT-24 of a CII invoice whose root element the reader is positioned on, or null if the document
   * context has none. Stops at the end of the document context.
   */
  private static String ciiSpecificationId(XMLStreamReader reader)
      throws XMLStreamException {
    boolean guideline = false;
    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT) {
        if (!guideline && RAM.equals(reader.getNamespaceURI()) && "GuidelineSpecifiedDocumentContextParameter".equals(reader.getLocalName())) {
          guideline = true;
        } else if (guideline && RAM.equals(reader.getNamespaceURI()) && "ID".equals(reader.getLocalName())) {
          return reader.getElementText()
                       .strip();
        } else if (CII.equals(reader.getNamespaceURI()) && "ExchangedDocument".equals(reader.getLocalName())) {
          // The document context precedes the document; without context there is no BT-24.
          return null;
        }
      } else if (event == XMLStreamConstants.END_ELEMENT) {
        if ("ExchangedDocumentContext".equals(reader.getLocalName())) {
          return null;
        }
        if (RAM.equals(reader.getNamespaceURI()) && "GuidelineSpecifiedDocumentContextParameter".equals(reader.getLocalName())) {
          guideline = false;
        }
      }
    }
    return null;
  }

  /**
   * Validates invoice XML; the document type and the rules to apply are detected from the content.
   *
   * <p>The validation runs the official checks in stages and reports every finding as a
   * {@link ValidationMessage} with the original rule identifier (e.g. {@code BR-DE-15}), severity,
   * XPath location and {@link ValidationMessage.Stage stage}: XML parsing, XML schema, Schematron.
   *
   * <pre>{@code
   * ValidationResult result = paniolo.validate(xml);
   * System.out.println(result.profileName());        // e.g. "EN16931 XRechnung (UBL Invoice)"
   * if (!result.isValid()) {                         // ERROR or FATAL findings
   *   for (ValidationMessage m : result.messages()) {
   *     System.out.printf("%s %s at %s: %s%n", m.severity(), m.ruleId(), m.location(), m.message());
   *   }
   * }
   * String report = result.toXml();                  // machine-readable report, e.g. for archiving
   * }</pre>
   *
   * <p><b>Which rules are applied?</b> They are chosen from the specification identifier (BT-24):
   * <ul>
   *   <li>XRechnung &ndash; the EN 16931 and XRechnung rules of the configured KoSIT bundle.</li>
   *   <li>Plain EN 16931, Peppol BIS Billing and unknown identifiers &ndash; the same rules, but the
   *       findings of the XRechnung CIUS ({@code BR-DE}, {@code BR-DEX}, {@code BR-TMP} and, except
   *       for Peppol, {@code PEPPOL-EN16931}) are information, e.g. a missing buyer reference
   *       {@code BR-DE-15}: they do not apply to a document that does not state XRechnung. The
   *       information {@code XR-NOT-STATED} says so. A recipient that requires XRechnung rejects
   *       on them with an {@link io.github.nalukit.paniolo.validation.AcceptancePolicy}, e.g.
   *       {@code AcceptancePolicy.officialRules().rejectOn("BR-DE-15")}.</li>
   *   <li>An older XRechnung version 1.2, 2.0, 2.1, 2.2 or 2.3 &ndash; the packaged KoSIT release of
   *       that version (see {@code XrechnungRules.LEGACY_VERSIONS}), with the information
   *       {@code XR-OLDER-VERSION}.</li>
   *   <li>The XRechnung extension ({@link ProcessControl#XRECHNUNG_3_EXTENSION}, or that of an older
   *       version) &ndash; the rules of the extension scenario, e.g. allowing sub invoice lines.</li>
   *   <li>CII with a Factur-X profile MINIMUM, BASIC WL, BASIC or EXTENDED (also in the ZUGFeRD 2.0 /
   *       2.1 notation) &ndash; validated like {@link #validate(byte[], FacturXProfile)}, i.e. with
   *       the official Factur-X rules of the profile.</li>
   *   <li>ZUGFeRD 1.0 ({@code rsm:CrossIndustryDocument}) &ndash; the schema and Schematron of
   *       ZUGFeRD 1.0, plus the warning {@code FX-ZUGFERD-1}: ZUGFeRD 1.0 does not meet EN 16931,
   *       migrate it with {@link #migrateZugferd1(byte[])}.</li>
   *   <li>EN 16931:2026, e.g. XRechnung 4.0 ({@code urn:cen.eu:en16931:2026...}) &ndash; not
   *       checked: its official rules are not published yet, and the rules of EN 16931:2017 would
   *       report findings that do not apply and miss those that do. The result is invalid with the
   *       single error {@code EN16931-2026-NOT-SUPPORTED}.</li>
   * </ul>
   *
   * <p><b>Performance:</b> the first validation of a document type compiles the schema and Schematron;
   * later validations reuse the compiled validator and are fast. Call {@link #warmUp()} at
   * application start if the first request must be fast.
   *
   * <p>Malformed XML is reported by an {@link IllegalArgumentException}, because the document type
   * cannot be detected. To get malformed XML as a {@code FATAL} finding instead, use
   * {@link #validate(byte[], ValidationProfile.Syntax)}, or configure
   * {@link Builder#unknownDocuments(UnknownDocumentHandling)} with
   * {@link UnknownDocumentHandling#REPORT_INVALID} for every undetectable document.
   *
   * @param xml the invoice XML; must not be {@code null}
   * @return the findings; never {@code null}
   * @throws IllegalArgumentException if the XML is not well-formed or is not a supported invoice; with
   *                                  {@link UnknownDocumentHandling#REPORT_INVALID} an invalid result
   *                                  instead
   * @see #validate(byte[])
   */
  public ValidationResult validate(String xml) {
    PanioloStopWatch watch = super.logStart("Paniolo.validate");
    if (isZugferd1(xml.getBytes(StandardCharsets.UTF_8))) {
      ValidationResult result = validateZugferd1(zugferd1Validator().validate(xml));
      return measured(watch,
                      calculated(xml.getBytes(StandardCharsets.UTF_8),
                                 result));
    }
    var unknown = new ArrayList<ValidationMessage>();
    var type    = detect(xml.getBytes(StandardCharsets.UTF_8),
                         unknown,
                         ValidationMessage.Stage.XML);
    if (type == null) {
      return measured(watch,
                      unknownDocument(unknown));
    }
    String specificationId = specificationId(type,
                                             () -> new StringReader(xml));
    ValidationResult result = validateFacturX(facturXProfile(type,
                                                             specificationId),
                                              type,
                                              specificationId,
                                              () -> new StringReader(xml),
                                              validator -> validator.validate(xml));
    return measured(watch,
                    calculated(xml.getBytes(StandardCharsets.UTF_8),
                               result));
  }

  /**
   * Validates invoice XML or a complete Factur-X / ZUGFeRD PDF; the format, document type and rules
   * are detected from the content.
   *
   * <p>This is the method to use for received files:
   *
   * <pre>{@code
   * ValidationResult result = paniolo.validate(Files.readAllBytes(path));   // XML or PDF
   * List<ValidationMessage> pdfaFindings = result.messages(ValidationMessage.Stage.PDF_A);
   * }</pre>
   *
   * <p><b>XML</b> is validated like {@link #validate(String)}; the encoding is taken from the XML
   * declaration.
   *
   * <p><b>PDF</b> (requires {@code paniolo-facturx}) is checked in three stages, whose findings are
   * combined in one result:
   * <ol>
   *   <li>{@link ValidationMessage.Stage#HYBRID HYBRID} &ndash; the Factur-X container: embedded invoice
   *       file, its relationship and MIME type, and the XMP metadata. The producing software is added
   *       as information {@code FX-PRODUCER} to help tracing systematic errors.</li>
   *   <li>{@link ValidationMessage.Stage#PDF_A PDF_A} &ndash; PDF/A conformance (ISO 19005), if
   *       {@code paniolo-pdfa} is on the classpath; otherwise the information
   *       {@code PDFA-NOT-CHECKED}.</li>
   *   <li>The embedded invoice XML, validated like {@link #validate(byte[], FacturXProfile)} for the
   *       profile stated in the XMP metadata or the file name. An embedded ZUGFeRD 1.0 invoice is
   *       checked against the ZUGFeRD 1.0 rules and yields the warning {@code FX-ZUGFERD-1}.</li>
   * </ol>
   *
   * <p>Container errors that make the XML unreachable (e.g. {@code FX-NOT-PDF},
   * {@code FX-NO-INVOICE}) are reported as findings; the XML stage is then skipped.
   *
   * @param xml the invoice XML or PDF; must not be {@code null}
   * @return the findings of all stages; never {@code null}
   * @throws IllegalArgumentException if standalone XML is not well-formed or not a supported invoice;
   *                                  with {@link UnknownDocumentHandling#REPORT_INVALID} an invalid
   *                                  result instead
   * @throws IllegalStateException    if the document is a PDF and {@code paniolo-facturx} is missing
   */
  public ValidationResult validate(byte[] xml) {
    Objects.requireNonNull(xml,
                           "xml");
    PanioloStopWatch watch = super.logStart("Paniolo.validate");
    ValidationResult result = isPdf(xml) ?
                              validateHybrid(xml) :
                              validateXml(xml);
    return measured(watch,
                    result.profileName()
                          .equals(UNKNOWN_DOCUMENT) ?
                    result :
                    calculated(xml,
                               result));
  }

  /**
   * Validates standalone invoice XML; see {@link #validate(byte[])}.
   */
  private ValidationResult validateXml(byte[] xml) {
    if (isZugferd1(xml)) {
      return validateZugferd1(zugferd1Validator().validate(xml));
    }
    var unknown = new ArrayList<ValidationMessage>();
    var type    = detect(xml,
                         unknown,
                         ValidationMessage.Stage.XML);
    if (type == null) {
      return unknownDocument(unknown);
    }
    String specificationId = specificationId(type,
                                             reader(xml));
    return validateFacturX(facturXProfile(type,
                                          specificationId),
                           type,
                           specificationId,
                           reader(xml),
                           validator -> validator.validate(xml));
  }

  /**
   * Validates XML against the EN 16931 and XRechnung rules of an explicitly chosen document type,
   * without any detection.
   *
   * <p>Use this method when you need a {@link ValidationResult} for <em>every</em> input, e.g. in an
   * upload endpoint or a batch job: malformed XML is not thrown as an exception but reported as a
   * {@code FATAL} finding of stage {@link ValidationMessage.Stage#XML XML}, and a document of another
   * type fails the schema check.
   *
   * <pre>{@code
   * // The channel only accepts UBL invoices; report everything else as findings.
   * ValidationResult result = paniolo.validate(upload, ValidationProfile.Syntax.UBL_INVOICE);
   * }</pre>
   *
   * <p>No profile detection takes place: Factur-X profiles and the XRechnung extension are
   * <em>not</em> recognized; the document is always checked against EN 16931 and XRechnung. Use
   * {@link #validate(byte[])} for automatic rule selection.
   *
   * @param xml  the XML; must not be {@code null}
   * @param type the document type to validate against; must not be {@code null}
   * @return the findings; never {@code null}
   */
  public ValidationResult validate(byte[] xml,
                                   ValidationProfile.Syntax type) {
    PanioloStopWatch watch  = super.logStart("Paniolo.validate");
    ValidationResult result = validator(type).validate(xml);
    return measured(watch,
                    calculated(xml,
                               result));
  }

  /**
   * Validates invoice XML or a Factur-X / ZUGFeRD PDF read from a stream.
   *
   * <p>The stream is read completely into memory and handled like {@link #validate(byte[])}. The
   * stream is <em>not</em> closed; the caller keeps ownership:
   *
   * <pre>{@code
   * try (InputStream in = request.getInputStream()) {
   *   ValidationResult result = paniolo.validate(in);
   * }
   * }</pre>
   *
   * @param xml the stream with the invoice XML or PDF; must not be {@code null}
   * @return the findings; never {@code null}
   * @throws UncheckedIOException     if reading the stream fails
   * @throws IllegalArgumentException if standalone XML is not well-formed or not a supported invoice;
   *                                  with {@link UnknownDocumentHandling#REPORT_INVALID} an invalid
   *                                  result instead
   * @throws IllegalStateException    if the document is a PDF and {@code paniolo-facturx} is missing
   */
  public ValidationResult validate(InputStream xml) {
    PanioloStopWatch watch  = super.logStart("Paniolo.validate");
    ValidationResult result = validate(readAll(xml));
    return measured(watch,
                    result);
  }

  /**
   * Validates invoice XML against the rules of an explicitly given Factur-X / ZUGFeRD profile, e.g.
   * an extracted {@code factur-x.xml} whose profile you know from the XMP metadata.
   *
   * <pre>{@code
   * ValidationResult result = paniolo.validate(paniolo.extractXml(pdf), FacturXProfile.BASIC);
   * }</pre>
   *
   * <p>CII is checked against the official Factur-X schema and Schematron of the profile: those
   * packaged in {@code paniolo-facturx-rules} (FeRD release ZUGFeRD 2.5.2 / Factur-X 1.09.2), or the
   * release configured with {@link Builder#facturXRules(Path)}. Profile XRECHNUNG is always checked
   * against EN 16931 and XRechnung.
   *
   * <p>The Factur-X schemas describe CII only. UBL XML with a Factur-X profile is checked against
   * EN 16931: MINIMUM and BASIC WL are not checked (warning {@code FX-RULES-REQUIRED}, because
   * EN 16931 does not apply to documents without lines), EXTENDED yields the information
   * {@code FX-EXTENDED-NOT-CHECKED}. If you do not know the profile, use {@link #validate(byte[])},
   * which takes it from BT-24.
   *
   * @param xml     the invoice XML; must not be {@code null}
   * @param profile the Factur-X profile whose rules apply; must not be {@code null}
   * @return the findings; never {@code null}
   * @throws IllegalArgumentException if the XML is not well-formed or not a supported invoice; with
   *                                  {@link UnknownDocumentHandling#REPORT_INVALID} an invalid result
   *                                  instead
   */
  public ValidationResult validate(byte[] xml,
                                   FacturXProfile profile) {
    Objects.requireNonNull(profile,
                           "profile");
    PanioloStopWatch watch = super.logStart("Paniolo.validate");
    var unknown = new ArrayList<ValidationMessage>();
    var type    = detect(Objects.requireNonNull(xml,
                                                "xml"),
                         unknown,
                         ValidationMessage.Stage.XML);
    if (type == null) {
      return measured(watch,
                      unknownDocument(unknown));
    }
    ValidationResult result = validateFacturX(profile,
                                              type,
                                              specificationId(type,
                                                              reader(xml)),
                                              reader(xml),
                                              validator -> validator.validate(xml));
    return measured(watch,
                    calculated(xml,
                               result));
  }

  /**
   * Writes the invoice in the given syntax and validates the result in one step.
   *
   * <p>Ideal as a final check before sending an invoice you created with the model, or in unit
   * tests of your invoice mapping:
   *
   * <pre>{@code
   * ValidationResult result = paniolo.validate(invoice, InvoiceSyntax.UBL);
   * if (result.isValid()) {
   *   send(paniolo.toBytes(invoice, InvoiceSyntax.UBL));
   * }
   * }</pre>
   *
   * <p>Equivalent to {@code validate(toBytes(invoice, syntax))}; the rules are selected from the
   * specification identifier (BT-24) of the invoice as described in {@link #validate(String)}. If you
   * need the XML anyway, write it once with {@link #toBytes(Invoice, InvoiceSyntax)} and validate the
   * bytes to avoid writing twice.
   *
   * @param invoice the invoice; must not be {@code null}
   * @param syntax  the syntax to write and validate; must not be {@code null}
   * @return the findings; never {@code null}
   * @throws IllegalArgumentException if the invoice contains content the syntax cannot express, or
   *                                  its specification identifier states EN 16931:2026 (writing
   *                                  rejects it, see {@link #toXml(Invoice, InvoiceSyntax)})
   */
  public ValidationResult validate(Invoice invoice,
                                   InvoiceSyntax syntax) {
    PanioloStopWatch watch = super.logStart("Paniolo.validate");
    ValidationResult result = validate(toBytes(invoice,
                                               syntax));
    return measured(watch,
                    result);
  }

  /**
   * Creates a Factur-X / ZUGFeRD hybrid invoice: the invoice is written as CII and embedded into
   * the given PDF/A document, which becomes a PDF/A-3 document with the matching XMP metadata.
   * Requires {@code paniolo-facturx}.
   *
   * <p>Paniolo does not render the invoice. You create the visual PDF yourself (e.g. with your
   * reporting tool, as PDF/A-1, -2 or -3) and Paniolo adds the machine-readable XML:
   *
   * <pre>{@code
   * Invoice invoice = Invoice.create()
   *                          .processControl(ProcessControl.en16931())   // must match the profile
   *                          ...
   *                          .build();
   * byte[] pdfA    = report.renderAsPdfA(invoice);                       // your visual invoice
   * byte[] facturX = paniolo.toFacturX(invoice, pdfA, FacturXProfile.EN16931);
   * }</pre>
   *
   * <p><b>Profile and specification identifier.</b> The specification identifier (BT-24) of the
   * invoice must be the one of the profile ({@link FacturXProfile#getSpecificationId()}); use the
   * matching factory of {@link ProcessControl}:
   *
   * <table>
   *   <caption>Profile and process control</caption>
   *   <tr><th>Profile</th><th>Process control</th><th>Embedded as</th></tr>
   *   <tr><td>{@link FacturXProfile#MINIMUM}</td><td>{@link ProcessControl#facturXMinimum()}</td>
   *       <td>{@code factur-x.xml}</td></tr>
   *   <tr><td>{@link FacturXProfile#BASIC_WL}</td><td>{@link ProcessControl#facturXBasicWl()}</td>
   *       <td>{@code factur-x.xml}</td></tr>
   *   <tr><td>{@link FacturXProfile#BASIC}</td><td>{@link ProcessControl#facturXBasic()}</td>
   *       <td>{@code factur-x.xml}</td></tr>
   *   <tr><td>{@link FacturXProfile#EN16931}</td><td>{@link ProcessControl#en16931()}</td>
   *       <td>{@code factur-x.xml}</td></tr>
   *   <tr><td>{@link FacturXProfile#EXTENDED}</td><td>{@link ProcessControl#facturXExtended()}</td>
   *       <td>{@code factur-x.xml}</td></tr>
   *   <tr><td>{@link FacturXProfile#XRECHNUNG}</td><td>{@link ProcessControl#xrechnung()}</td>
   *       <td>{@code xrechnung.xml}</td></tr>
   * </table>
   *
   * <p>MINIMUM and BASIC WL carry no invoice lines; build the invoice without lines and with stated
   * totals ({@code Invoice.Builder.statedTotals}). In Germany both are only booking aids, not
   * invoices in the sense of the VAT law; use BASIC or higher for real invoices.
   *
   * <p>The result is not validated. Check it with {@link #validate(byte[])}, ideally with
   * {@code paniolo-pdfa} on the classpath to cover the PDF/A conformance as well.
   *
   * <p>The embedded XML is compressed and the PDF is saved with object streams; the streams of
   * {@code pdf} are taken as they are, so pass a compressed PDF to get a small file.
   *
   * @param invoice the invoice to embed; must not be {@code null}
   * @param pdf     PDF/A-1, -2 or -3 document with the visual representation of the invoice; must
   *                not be {@code null}
   * @param profile Factur-X profile; must not be {@code null}
   * @return the Factur-X PDF/A-3 document
   * @throws IllegalArgumentException if the specification identifier does not match the profile, the
   *                                  invoice content does not fit the profile, or the PDF is not
   *                                  PDF/A, encrypted, or already contains an invoice
   * @throws IllegalStateException    if {@code paniolo-facturx} is missing
   * @see #toFacturX(Invoice, byte[], FacturXProfile, List)
   */
  public byte[] toFacturX(Invoice invoice,
                          byte[] pdf,
                          FacturXProfile profile) {
    PanioloStopWatch watch = super.logStart("Paniolo.toFacturX");
    byte[] result = toFacturX(invoice,
                              pdf,
                              profile,
                              List.of());
    super.logStop(watch);
    return result;
  }

  /**
   * Creates a Factur-X / ZUGFeRD hybrid invoice like
   * {@link #toFacturX(Invoice, byte[], FacturXProfile)} and embeds further files next to the
   * invoice XML, e.g. a time sheet, a delivery note or a signed contract.
   *
   * <pre>{@code
   * byte[] facturX = paniolo.toFacturX(invoice,
   *                                    pdfA,
   *                                    FacturXProfile.EN16931,
   *                                    List.of(PdfAttachment.of("timesheet.pdf",
   *                                                             timesheet,
   *                                                             "application/pdf")));
   * }</pre>
   *
   * <p>Each attachment is associated with the document as PDF/A-3 requires, with the relationship of
   * the {@link PdfAttachment} ({@link io.github.nalukit.paniolo.config.AfRelationship#SUPPLEMENT} for
   * {@link PdfAttachment#of(String, byte[], String)}). Supporting documents of the invoice (BG-24) can
   * be passed with {@link PdfAttachment#of(io.github.nalukit.paniolo.model.common.Attachment)}.
   *
   * <p>Files already embedded in the PDF are kept. Every file name may occur only once in the PDF,
   * and the reserved names of the invoice XML ({@link PdfAttachment#INVOICE_FILE_NAMES}) cannot be
   * used. Read the attachments back with {@link #attachments(byte[])}.
   *
   * @param invoice     the invoice to embed; must not be {@code null}
   * @param pdf         PDF/A-1, -2 or -3 document with the visual invoice; must not be {@code null}
   * @param profile     Factur-X profile; must not be {@code null}
   * @param attachments further files; may be empty, must not be {@code null}
   * @return the Factur-X PDF/A-3 document
   * @throws IllegalArgumentException if the specification identifier does not match the profile, the
   *                                  PDF is not PDF/A, encrypted or already contains an invoice, or
   *                                  an attachment name is used twice or already in the PDF
   * @throws IllegalStateException    if {@code paniolo-facturx} is missing
   */
  public byte[] toFacturX(Invoice invoice,
                          byte[] pdf,
                          FacturXProfile profile,
                          List<PdfAttachment> attachments) {
    Objects.requireNonNull(attachments,
                           "attachments");
    Objects.requireNonNull(invoice,
                           "invoice");
    Objects.requireNonNull(pdf,
                           "pdf");
    Objects.requireNonNull(profile,
                           "profile");
    checkFacturXProfile(invoice,
                        profile);
    PanioloStopWatch watch = super.logStart("Paniolo.toFacturX");
    byte[] result = facturX().embed(pdf,
                                    toBytes(invoice,
                                            InvoiceSyntax.CII),
                                    profile,
                                    List.copyOf(attachments));
    super.logStop(watch);
    return result;
  }

  /**
   * Embeds existing invoice XML into a PDF/A document, byte for byte, and validates the result.
   * Requires {@code paniolo-facturx}.
   *
   * <p>Unlike {@link #toFacturX(Invoice, byte[], FacturXProfile)} the XML is not read into the
   * invoice model and written again, so content of other producers stays exactly as it is:
   * {@link #extractXml(byte[])} returns the same bytes.
   *
   * <pre>{@code
   * FacturXResult result = paniolo.embedXml(pdfA, Files.readAllBytes(xml), FacturXProfile.EN16931);
   * if (!result.isValid()) {
   *   result.validation().messages().forEach(System.out::println);
   * }
   * Files.write(target, result.pdf());
   * }</pre>
   *
   * <p><b>Checked before embedding</b> &ndash; rejected with an {@link IllegalArgumentException}:
   * <ul>
   *   <li>the profile can be embedded into a PDF ({@link FacturXProfile#isPdfWritable()}),</li>
   *   <li>the PDF is a PDF; the container checks of embedding reject PDFs that are not PDF/A, are
   *       encrypted, or already contain an invoice,</li>
   *   <li>the XML is a well-formed UN/CEFACT CII {@code CrossIndustryInvoice}; UBL and ZUGFeRD 1.0
   *       cannot be embedded as Factur-X,</li>
   *   <li>the specification identifier (BT-24) of the XML belongs to the profile.</li>
   * </ul>
   *
   * <p><b>Checked after embedding</b> &ndash; reported in {@link FacturXResult#validation()}, like
   * {@link #validate(byte[])} for a PDF: the container, PDF/A conformance if {@code paniolo-pdfa} is
   * present, and the XML with the rules of the profile. An invalid XML is embedded anyway, so the
   * caller decides what to do with the findings. The validation does not check that the visual PDF
   * shows the content of the XML.
   *
   * <p>As with {@link #toFacturX(Invoice, byte[], FacturXProfile)}, the embedded XML is compressed
   * (its content stays byte for byte the same) and the PDF is saved with object streams.
   *
   * @param pdf         PDF/A-1, -2 or -3 document with the visual invoice; must not be {@code null}
   * @param xml         the CII invoice XML to embed unchanged; must not be {@code null}
   * @param profile     Factur-X profile; must not be {@code null}
   * @param attachments further files; may be empty, must not be {@code null}
   * @return the Factur-X PDF/A-3 document and its validation
   * @throws IllegalArgumentException if the profile cannot be embedded, the PDF is no PDF/A, is
   *                                  encrypted or already contains an invoice, the XML is not CII,
   *                                  or its specification identifier does not match the profile
   * @throws IllegalStateException    if {@code paniolo-facturx} is missing
   * @see #embedXml(byte[], byte[], FacturXProfile)
   */
  public FacturXResult embedXml(byte[] pdf,
                                byte[] xml,
                                FacturXProfile profile,
                                List<PdfAttachment> attachments) {
    Objects.requireNonNull(pdf,
                           "pdf");
    Objects.requireNonNull(xml,
                           "xml");
    Objects.requireNonNull(profile,
                           "profile");
    Objects.requireNonNull(attachments,
                           "attachments");
    PanioloStopWatch watch = super.logStart("Paniolo.embedXml");
    if (!profile.isPdfWritable()) {
      throw new IllegalArgumentException("Profile " + profile.getConformanceLevel() + " is exchanged as CII XML and cannot be embedded into a PDF");
    }
    if (!isPdf(pdf)) {
      throw new IllegalArgumentException("The visual document is not a PDF");
    }
    byte[] content = xml.clone();
    if (isZugferd1(content)) {
      throw new IllegalArgumentException("ZUGFeRD 1.0 XML cannot be embedded as Factur-X; migrate it with migrateZugferd1(xml) first");
    }
    var type = detectRoot(content);
    if (type != ValidationProfile.Syntax.CII) {
      throw new IllegalArgumentException("Factur-X embeds UN/CEFACT CII XML, but the XML is " + type);
    }
    String specificationId = guideline(reader(content).get());
    if (FacturXProfile.ofSpecificationId(specificationId) != profile) {
      throw new IllegalArgumentException("Factur-X profile " +
                                         profile +
                                         " requires specification identifier (BT-24) " +
                                         profile.getSpecificationId() +
                                         ", but the XML states " +
                                         (specificationId == null ?
                                          "none" :
                                          specificationId));
    }
    byte[] result = facturX().embed(pdf,
                                    content,
                                    profile,
                                    List.copyOf(attachments));
    var validation = validate(result);
    super.logStop(watch);
    return new FacturXResult(result,
                             validation);
  }

  /**
   * Embeds existing invoice XML into a PDF/A document without further files; see
   * {@link #embedXml(byte[], byte[], FacturXProfile, List)}.
   *
   * @param pdf     PDF/A-1, -2 or -3 document with the visual invoice; must not be {@code null}
   * @param xml     the CII invoice XML to embed unchanged; must not be {@code null}
   * @param profile Factur-X profile; must not be {@code null}
   * @return the Factur-X PDF/A-3 document and its validation
   * @throws IllegalArgumentException if the profile, the PDF or the XML is rejected
   * @throws IllegalStateException    if {@code paniolo-facturx} is missing
   */
  public FacturXResult embedXml(byte[] pdf,
                                byte[] xml,
                                FacturXProfile profile) {
    return embedXml(pdf,
                    xml,
                    profile,
                    List.of());
  }

  /**
   * Renders an invoice as visual PDF/A-3b document with the default layout (German labels).
   * Requires {@code paniolo-pdf-layout}.
   *
   * @param invoice the invoice; must not be {@code null}
   * @return the PDF/A-3b document, without embedded XML
   * @throws IllegalStateException if {@code paniolo-pdf-layout} is missing
   * @see #toPdf(Invoice, PdfLayout)
   */
  public byte[] toPdf(Invoice invoice) {
    return toPdf(invoice,
                 PdfLayout.defaults());
  }

  /**
   * Renders a validation report as PDF/A-3b document in the default layout (German labels); see
   * {@link #toReportPdf(ValidationResult, String, byte[], PdfLayout)}.
   *
   * @param result   the validation result; must not be {@code null}
   * @param fileName name of the validated file, or {@code null}
   * @param document the validated content, for its size and SHA-256, or {@code null}
   * @return the PDF/A-3b report
   * @throws IllegalStateException if {@code paniolo-pdf-layout} is missing
   */
  public byte[] toReportPdf(ValidationResult result,
                            String fileName,
                            byte[] document) {
    return toReportPdf(result,
                       fileName,
                       document,
                       PdfLayout.defaults());
  }

  /**
   * Renders a validation report as PDF/A-3b document, e.g. for archiving or for departments that
   * work with documents rather than XML. Requires {@code paniolo-pdf-layout}.
   *
   * <pre>{@code
   * byte[] received = Files.readAllBytes(path);
   * ValidationResult result = paniolo.validate(received);
   * byte[] pdf = paniolo.toReportPdf(result, path.getFileName().toString(), received);
   * }</pre>
   *
   * <p>The report is laid out as conformance report: a verdict in the color of the result (conform,
   * conform with warnings, not conform with the recommendation to reject the document), the number
   * of findings per severity, the time of rendering as validation time (UTC), the validated file with
   * its size and SHA-256, the profile and the rule releases of this instance, the validation stages
   * with their findings and result, the Schematron and PDF/A statistics if collected, and every
   * finding as a block with severity, complete rule identifier, message and location: the findings
   * of {@link ValidationResult#toXml()} and the CLI JSON, grouped by stage like the XML report. A
   * result without findings is rendered with the conform verdict. The machine-readable report
   * remains the basis; the PDF is a rendering of it.
   *
   * @param result   the validation result; must not be {@code null}
   * @param fileName name of the validated file, or {@code null}
   * @param document the validated content, for its size and SHA-256, or {@code null}
   * @param layout   language, fonts, accent color and footer; logo and sender address are not used
   * @return the PDF/A-3b report
   * @throws IllegalArgumentException if a font of the layout cannot be read
   * @throws IllegalStateException    if {@code paniolo-pdf-layout} is missing
   */
  public byte[] toReportPdf(ValidationResult result,
                            String fileName,
                            byte[] document,
                            PdfLayout layout) {
    Objects.requireNonNull(result,
                           "result");
    Objects.requireNonNull(layout,
                           "layout");
    PanioloStopWatch watch = super.logStart("Paniolo.toReportPdf");
    String profile = result.profileName() == null ?
                     "" :
                     result.profileName();
    String rules = profile.contains("Factur-X") || profile.contains("ZUGFeRD") ?
                   facturXRelease :
                   profile.contains("Order-X") ?
                   orderXRelease :
                   xrechnungRelease;
    if (calculationCheck) {
      rules += "; Paniolo calculation check";
    }
    byte[] pdf = pdfLayout().render(new PdfLayoutSupport.Report(result,
                                                                fileName,
                                                                document == null ?
                                                                null :
                                                                sha256(document),
                                                                document == null ?
                                                                -1 :
                                                                document.length,
                                                                Instant.now(),
                                                                rules),
                                    layout);
    super.logStop(watch);
    return pdf;
  }

  /**
   * Rejects a layout with watermark for a hybrid document: its embedded XML would be valid while the
   * visual part says it is not.
   */
  private static void requireNoWatermark(PdfLayout layout) {
    Objects.requireNonNull(layout,
                           "layout");
    if (layout.watermark() != null) {
      throw new IllegalArgumentException("A watermark is only allowed for PDFs without embedded XML; use toPdf for a preview");
    }
  }

  private static String sha256(byte[] content) {
    try {
      return HexFormat.of()
                      .formatHex(MessageDigest.getInstance("SHA-256")
                                              .digest(content));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is not available",
                                      e);
    }
  }

  /**
   * Renders an invoice as visual PDF/A-3b document: letterhead with seller and logo, recipient,
   * document data, lines with sub-lines and line allowances and charges, document allowances and
   * charges, totals with the VAT breakdown, payment details, notes, and the seller's legal details
   * in the footer of every page. Requires {@code paniolo-pdf-layout}.
   *
   * <p>EXTENDED content (Factur-X EXTENDED and EXTENDED-CTC-FR) is shown as well: the document
   * status of a test or copy, further references, delivery terms and parties, line details such as
   * subtotal and information lines, product data, line parties and references, logistics service
   * charges, and the financial adjustments that lead to the amount due. Codes that only qualify
   * another value are not shown.
   *
   * <pre>{@code
   * byte[] pdf = paniolo.toPdf(invoice, PdfLayout.of(Locale.ENGLISH).withLogo(logo));
   * }</pre>
   *
   * <p>The document carries no XML; use {@link #toFacturX(Invoice, FacturXProfile, PdfLayout, List)}
   * for a hybrid invoice.
   *
   * @param invoice the invoice; must not be {@code null}
   * @param layout  language, logo, fonts, and colors; must not be {@code null}
   * @return the PDF/A-3b document
   * @throws IllegalArgumentException if the logo or a font of the layout cannot be read
   * @throws IllegalStateException    if {@code paniolo-pdf-layout} is missing
   */
  public byte[] toPdf(Invoice invoice,
                      PdfLayout layout) {
    Objects.requireNonNull(invoice,
                           "invoice");
    Objects.requireNonNull(layout,
                           "layout");
    PanioloStopWatch watch = super.logStart("Paniolo.toPdf");
    byte[] result = pdfLayout().render(invoice,
                                       layout);
    super.logStop(watch);
    return result;
  }

  /**
   * Creates a Factur-X / ZUGFeRD hybrid invoice whose visual part Paniolo renders itself, with the
   * default layout and without further attachments. Requires {@code paniolo-pdf-layout}.
   *
   * <pre>{@code
   * byte[] facturX = paniolo.toFacturX(invoice, FacturXProfile.EN16931);
   * }</pre>
   *
   * @param invoice the invoice; must not be {@code null}
   * @param profile Factur-X profile; must not be {@code null}
   * @return the Factur-X PDF/A-3 document
   * @throws IllegalArgumentException if the specification identifier does not match the profile or
   *                                  the invoice content does not fit the profile
   * @throws IllegalStateException    if {@code paniolo-pdf-layout} is missing
   * @see #toFacturX(Invoice, FacturXProfile, PdfLayout, List)
   */
  public byte[] toFacturX(Invoice invoice,
                          FacturXProfile profile) {
    return toFacturX(invoice,
                     profile,
                     PdfLayout.defaults(),
                     List.of());
  }

  /**
   * Creates a Factur-X / ZUGFeRD hybrid invoice whose visual part Paniolo renders with
   * {@link #toPdf(Invoice, PdfLayout)}, and embeds the CII XML and further files like
   * {@link #toFacturX(Invoice, byte[], FacturXProfile, List)}. Requires {@code paniolo-pdf-layout},
   * which brings {@code paniolo-facturx}.
   *
   * <pre>{@code
   * byte[] facturX = paniolo.toFacturX(invoice,
   *                                    FacturXProfile.XRECHNUNG,
   *                                    PdfLayout.defaults().withLogo(logo),
   *                                    List.of(PdfAttachment.of("timesheet.pdf", timesheet, "application/pdf")));
   * }</pre>
   *
   * @param invoice     the invoice; must not be {@code null}
   * @param profile     Factur-X profile; must not be {@code null}
   * @param layout      language, logo, fonts, and colors; must not be {@code null}
   * @param attachments further files; may be empty, must not be {@code null}
   * @return the Factur-X PDF/A-3 document
   * @throws IllegalArgumentException if the specification identifier does not match the profile, the
   *                                  invoice content does not fit the profile, an attachment name is
   *                                  used twice, or the layout has a watermark
   * @throws IllegalStateException    if {@code paniolo-pdf-layout} or {@code paniolo-facturx} is
   *                                  missing
   */
  public byte[] toFacturX(Invoice invoice,
                          FacturXProfile profile,
                          PdfLayout layout,
                          List<PdfAttachment> attachments) {
    Objects.requireNonNull(invoice,
                           "invoice");
    Objects.requireNonNull(profile,
                           "profile");
    Objects.requireNonNull(layout,
                           "layout");
    Objects.requireNonNull(attachments,
                           "attachments");
    requireNoWatermark(layout);
    // Check the profile before rendering, so a wrong invoice fails fast.
    checkFacturXProfile(invoice,
                        profile);
    return toFacturX(invoice,
                     toPdf(invoice,
                           layout),
                     profile,
                     attachments);
  }

  /**
   * Tells which software produced a PDF: producer and creator from the document information and XMP
   * metadata, and the e-invoicing software recognized from known signatures (as the Mustang project
   * recognizes them). Requires {@code paniolo-facturx}.
   *
   * <p>Useful to trace systematic errors in received invoices back to the software of the sender:
   *
   * <pre>{@code
   * PdfProducer producer = paniolo.producer(pdf);
   * log.info("Invoice produced by {}", producer.describe());   // e.g. Mustang (producer "PDFBox", creator "Mustang 2.27")
   * if (producer.isUnknown()) { ... }
   * }</pre>
   *
   * <p>{@link #validate(byte[])} adds the same information to its result as {@code FX-PRODUCER}.
   *
   * @param pdf the PDF document; must not be {@code null}
   * @return the producing software; fields are {@code null} where the document tells nothing
   * @throws IllegalArgumentException if the content is not a readable PDF
   * @throws IllegalStateException    if {@code paniolo-facturx} is missing
   */
  public PdfProducer producer(byte[] pdf) {
    PanioloStopWatch watch = super.logStart("Paniolo.producer");
    PdfProducer result = facturX().producer(Objects.requireNonNull(pdf,
                                                                   "pdf"));
    super.logStop(watch);
    return result;
  }

  /**
   * Returns the files embedded in a PDF apart from the invoice XML, e.g. time sheets or other
   * supplementary documents a sender attached to a Factur-X / ZUGFeRD invoice. Requires
   * {@code paniolo-facturx}.
   *
   * <pre>{@code
   * for (PdfAttachment file : paniolo.attachments(pdf)) {
   *   Files.write(target.resolve(file.fileName()), file.content());
   * }
   * }</pre>
   *
   * <p>The invoice XML itself is not part of the list; get it with {@link #extractXml(byte[])} or read
   * it directly with {@link #read(byte[])}. Each attachment carries file name, content, MIME type,
   * {@link io.github.nalukit.paniolo.config.AfRelationship relationship} and description as stated in
   * the PDF.
   *
   * @param pdf the PDF document; must not be {@code null}
   * @return the attachments in document order; empty if there are none
   * @throws IllegalArgumentException if the content is not a readable PDF
   * @throws IllegalStateException    if {@code paniolo-facturx} is missing
   */
  public List<PdfAttachment> attachments(byte[] pdf) {
    PanioloStopWatch watch = super.logStart("Paniolo.attachments");
    List<PdfAttachment> result = facturX().attachments(Objects.requireNonNull(pdf,
                                                                              "pdf"));
    super.logStop(watch);
    return result;
  }

  /**
   * Returns the invoice XML embedded in a Factur-X / ZUGFeRD PDF, unchanged. Requires
   * {@code paniolo-facturx}.
   *
   * <p>Use it to archive or forward the XML part of a hybrid invoice, or to process it with other
   * tools:
   *
   * <pre>{@code
   * byte[] xml = paniolo.extractXml(pdf);   // factur-x.xml, zugferd-invoice.xml or xrechnung.xml
   * Files.write(Path.of("invoice.xml"), xml);
   * }</pre>
   *
   * <p>To get the invoice model, call {@link #read(byte[])} with the PDF directly; to validate,
   * call {@link #validate(byte[])} with the PDF, which also checks the container and PDF/A.
   *
   * @param pdf the Factur-X / ZUGFeRD PDF; must not be {@code null}
   * @return the embedded XML bytes in their original encoding
   * @throws IllegalArgumentException if the PDF contains no supported invoice XML
   * @throws IllegalStateException    if {@code paniolo-facturx} is missing
   */
  public byte[] extractXml(byte[] pdf) {
    PanioloStopWatch watch = super.logStart("Paniolo.extractXml");
    byte[] result = facturX().extract(Objects.requireNonNull(pdf,
                                                             "pdf"))
                             .xml();
    super.logStop(watch);
    return result;
  }

  private ReadResult read(byte[] bytes,
                          String text,
                          ReadMode mode) {
    InvoiceXmlReader reader = invoiceReader(bytes);
    return text == null ?
           reader.fromBytes(bytes,
                            mode) :
           reader.fromXml(text,
                          mode);
  }

  /**
   * Returns the reader of invoice XML, chosen by its root element.
   */
  private InvoiceXmlReader invoiceReader(byte[] xml) {
    if (isZugferd1(xml)) {
      return ZUGFERD_1;
    }
    return readers.get(syntax(xml));
  }

  /**
   * Returns the syntax of UBL or CII invoice XML, chosen by its root element.
   */
  private static InvoiceSyntax syntax(byte[] xml) {
    return detectRoot(xml) == ValidationProfile.Syntax.CII ?
           InvoiceSyntax.CII :
           InvoiceSyntax.UBL;
  }

  /**
   * Writes an order as Order-X XML (UN/CEFACT Cross Industry Order) in the profile of the order.
   *
   * <p>The document kind follows the type code of the order ({@link Order#typeCode()}): purchase
   * order, order change or order response. The profile (BASIC, COMFORT, EXTENDED) follows
   * {@link Order#profile()}; it is written as specification identifier and decides which content is
   * allowed.
   *
   * <pre>{@code
   * String xml = paniolo.toOrderX(order);
   * }</pre>
   *
   * <p>Writing does not validate; use {@link #validateOrder(byte[])}.
   *
   * @param order the order; must not be {@code null}
   * @return the Order-X XML
   * @throws IllegalArgumentException if the order contains content its profile does not allow
   * @see #toOrderXBytes(Order)
   * @see #toOrderXPdf(Order, byte[])
   */
  public String toOrderX(Order order) {
    PanioloStopWatch watch  = super.logStart("Paniolo.toOrderX");
    String           result = ORDER_WRITER.toXml(order);
    super.logStop(watch);
    return result;
  }

  /**
   * Writes an order as UTF-8 encoded Order-X XML; same as {@link #toOrderX(Order)}, but ready to
   * be stored or sent.
   *
   * @param order the order; must not be {@code null}
   * @return the Order-X XML encoded as UTF-8
   * @throws IllegalArgumentException if the order contains content its profile does not allow
   */
  public byte[] toOrderXBytes(Order order) {
    PanioloStopWatch watch  = super.logStart("Paniolo.toOrderXBytes");
    byte[]           result = ORDER_WRITER.toBytes(order);
    super.logStop(watch);
    return result;
  }

  /**
   * Creates a hybrid Order-X PDF: the order is written as Order-X XML and embedded as
   * {@code order-x.xml} into the given PDF/A document, which becomes PDF/A-3 with the Order-X XMP
   * metadata (profile and document type). Requires {@code paniolo-facturx}.
   *
   * <p>Like for Factur-X, you provide the visual PDF/A document showing the order:
   *
   * <pre>{@code
   * byte[] orderPdf = paniolo.toOrderXPdf(order, pdfA);
   * }</pre>
   *
   * @param order the order; must not be {@code null}
   * @param pdf   PDF/A-1, -2 or -3 document with the visual order; must not be {@code null}
   * @return the Order-X PDF/A-3 document
   * @throws IllegalArgumentException if the order does not fit its profile, or the PDF is not PDF/A,
   *                                  encrypted, or already contains an order
   * @throws IllegalStateException    if {@code paniolo-facturx} is missing
   * @see #toOrderXPdf(Order, byte[], List)
   */
  public byte[] toOrderXPdf(Order order,
                            byte[] pdf) {
    PanioloStopWatch watch = super.logStart("Paniolo.toOrderXPdf");
    byte[] result = toOrderXPdf(order,
                                pdf,
                                List.of());
    super.logStop(watch);
    return result;
  }

  /**
   * Creates a hybrid Order-X PDF like {@link #toOrderXPdf(Order, byte[])} and embeds further files,
   * e.g. technical drawings or specifications belonging to the order.
   *
   * <pre>{@code
   * byte[] orderPdf = paniolo.toOrderXPdf(order,
   *                                       pdfA,
   *                                       List.of(PdfAttachment.of("drawing.pdf", drawing, "application/pdf")));
   * }</pre>
   *
   * <p>The rules for attachments are the same as for
   * {@link #toFacturX(Invoice, byte[], FacturXProfile, List)}: unique file names, no reserved names,
   * existing attachments of the PDF are kept.
   *
   * @param order       the order; must not be {@code null}
   * @param pdf         PDF/A-1, -2 or -3 document with the visual order; must not be {@code null}
   * @param attachments further files; may be empty, must not be {@code null}
   * @return the Order-X PDF/A-3 document
   * @throws IllegalArgumentException if the order does not fit its profile, the PDF is not suitable,
   *                                  or an attachment name is used twice or already in the PDF
   * @throws IllegalStateException    if {@code paniolo-facturx} is missing
   */
  public byte[] toOrderXPdf(Order order,
                            byte[] pdf,
                            List<PdfAttachment> attachments) {
    Objects.requireNonNull(order,
                           "order");
    PanioloStopWatch watch = super.logStart("Paniolo.toOrderXPdf");
    byte[] result = facturX().embedOrder(Objects.requireNonNull(pdf,
                                                                "pdf"),
                                         toOrderXBytes(order),
                                         order.profile(),
                                         order.typeCode(),
                                         List.copyOf(attachments));
    super.logStop(watch);
    return result;
  }

  /**
   * Renders an order, order change, or order response as visual PDF/A-3b document with the
   * default layout (German labels), without embedded XML. Requires {@code paniolo-pdf-layout}.
   *
   * @param order the order; must not be {@code null}
   * @return the PDF/A-3b document
   * @throws IllegalStateException if {@code paniolo-pdf-layout} is missing
   * @see #toPdf(Order, PdfLayout)
   */
  public byte[] toPdf(Order order) {
    return toPdf(order,
                 PdfLayout.defaults());
  }

  /**
   * Renders an order, order change, or order response as visual PDF/A-3b document: the buyer's
   * letterhead for orders and changes, the seller's for responses; requested delivery, lines with
   * the ordered and, in a response, the confirmed quantity and the line status; totals; delivery
   * parties; delivery and payment terms. Requires {@code paniolo-pdf-layout}.
   *
   * @param order  the order; must not be {@code null}
   * @param layout language, logo, fonts, and colors; must not be {@code null}
   * @return the PDF/A-3b document, without embedded XML
   * @throws IllegalArgumentException if the logo or a font of the layout cannot be read
   * @throws IllegalStateException    if {@code paniolo-pdf-layout} is missing
   */
  public byte[] toPdf(Order order,
                      PdfLayout layout) {
    Objects.requireNonNull(order,
                           "order");
    Objects.requireNonNull(layout,
                           "layout");
    PanioloStopWatch watch = super.logStart("Paniolo.toPdf");
    byte[] result = pdfLayout().render(order,
                                       layout);
    super.logStop(watch);
    return result;
  }

  /**
   * Creates an Order-X PDF/A-3 whose visual part Paniolo renders itself, with the default layout.
   * Requires {@code paniolo-pdf-layout}.
   *
   * <pre>{@code
   * byte[] pdf = paniolo.toOrderXPdf(order);
   * }</pre>
   *
   * @param order the order; must not be {@code null}
   * @return the Order-X PDF/A-3 document
   * @throws IllegalStateException if {@code paniolo-pdf-layout} is missing
   * @see #toOrderXPdf(Order, PdfLayout, List)
   */
  public byte[] toOrderXPdf(Order order) {
    return toOrderXPdf(order,
                       PdfLayout.defaults(),
                       List.of());
  }

  /**
   * Creates an Order-X PDF/A-3 whose visual part Paniolo renders with
   * {@link #toPdf(Order, PdfLayout)}, and embeds the Order-X XML and further files like
   * {@link #toOrderXPdf(Order, byte[], List)}. Requires {@code paniolo-pdf-layout}.
   *
   * @param order       the order; must not be {@code null}
   * @param layout      language, logo, fonts, and colors; must not be {@code null}
   * @param attachments further files; may be empty, must not be {@code null}
   * @return the Order-X PDF/A-3 document
   * @throws IllegalArgumentException if the order content does not fit its profile, an attachment
   *                                  name is used twice, or the layout has a watermark
   * @throws IllegalStateException    if {@code paniolo-pdf-layout} is missing
   */
  public byte[] toOrderXPdf(Order order,
                            PdfLayout layout,
                            List<PdfAttachment> attachments) {
    Objects.requireNonNull(attachments,
                           "attachments");
    requireNoWatermark(layout);
    return toOrderXPdf(order,
                       toPdf(order,
                             layout),
                       attachments);
  }

  /**
   * Reads an order from Order-X XML or from a hybrid Order-X PDF; the format is detected from the
   * content.
   *
   * <pre>{@code
   * Order order = paniolo.readOrder(Files.readAllBytes(path));   // order-x.xml or Order-X PDF
   * switch (order.typeCode()) { ... }                             // order, change or response
   * }</pre>
   *
   * <p>For a PDF, the embedded {@code order-x.xml} is extracted and read; this requires
   * {@code paniolo-facturx}.
   *
   * <p>The read is strict: content the order model does not carry is rejected with an exception that
   * names it, so no information is lost silently. Use {@link #readOrder(byte[], boolean)} to drop such
   * content instead.
   *
   * @param xmlOrPdf the Order-X XML or PDF; must not be {@code null}
   * @return the order
   * @throws IllegalArgumentException if the document is not Order-X, the PDF contains no order, or the
   *                                  document has content the model does not carry
   * @throws IllegalStateException    if the document is a PDF and {@code paniolo-facturx} is missing
   */
  public Order readOrder(byte[] xmlOrPdf) {
    Objects.requireNonNull(xmlOrPdf,
                           "xmlOrPdf");
    PanioloStopWatch watch = super.logStart("Paniolo.readOrder");
    Order result = ORDER_READER.fromBytes(isPdf(xmlOrPdf) ?
                                          facturX().extractOrder(xmlOrPdf) :
                                          xmlOrPdf);
    super.logStop(watch);
    return result;
  }

  /**
   * Reads an order from Order-X XML or from a hybrid Order-X PDF, optionally ignoring content the
   * order model does not carry.
   *
   * <pre>{@code
   * Order order = paniolo.readOrder(received, true);   // accept orders with unsupported extras
   * }</pre>
   *
   * <p>With {@code ignoreUnsupported = false} this is the same as {@link #readOrder(byte[])}. With
   * {@code true}, unsupported elements are dropped silently; use it only if your process does not
   * depend on them, e.g. when you keep the original document anyway.
   *
   * @param xmlOrPdf          the Order-X XML or PDF; must not be {@code null}
   * @param ignoreUnsupported {@code true} to drop content the model does not carry, {@code false} to
   *                          reject it
   * @return the order
   * @throws IllegalArgumentException if the document is not Order-X, the PDF contains no order, or
   *                                  (with {@code ignoreUnsupported = false}) has unsupported content
   * @throws IllegalStateException    if the document is a PDF and {@code paniolo-facturx} is missing
   */
  public Order readOrder(byte[] xmlOrPdf,
                         boolean ignoreUnsupported) {
    Objects.requireNonNull(xmlOrPdf,
                           "xmlOrPdf");
    PanioloStopWatch watch = super.logStart("Paniolo.readOrder");
    Order result = ignoreUnsupported ?
                   OrderXReader.lenient()
                               .fromBytes(isPdf(xmlOrPdf) ?
                                          facturX().extractOrder(xmlOrPdf) :
                                          xmlOrPdf) :
                   readOrder(xmlOrPdf);
    super.logStop(watch);
    return result;
  }

  /**
   * Reads an order from Order-X XML given as string.
   *
   * <p>Strict like {@link #readOrder(byte[])}; PDFs cannot be passed as string, use
   * {@link #readOrder(byte[])} for them.
   *
   * @param xml the Order-X XML; must not be {@code null}
   * @return the order
   * @throws IllegalArgumentException if the XML is not Order-X or has content the model does not carry
   */
  public Order readOrder(String xml) {
    PanioloStopWatch watch = super.logStart("Paniolo.readOrder");
    Order result = ORDER_READER.fromXml(Objects.requireNonNull(xml,
                                                               "xml"));
    super.logStop(watch);
    return result;
  }

  /**
   * Validates Order-X XML or a hybrid Order-X PDF against the official Order-X schema and
   * Schematron of its profile.
   *
   * <pre>{@code
   * ValidationResult result = paniolo.validateOrder(received);
   * }</pre>
   *
   * <p>The profile (BASIC, COMFORT, EXTENDED) is taken from the specification identifier of the
   * document. A PDF is additionally checked for PDF/A conformance if {@code paniolo-pdfa} is on the
   * classpath (otherwise information {@code PDFA-NOT-CHECKED}); the findings of both stages are
   * combined.
   *
   * <p>The rules are the Order-X 1.0 rules of FNFE-MPE and FeRD packaged in
   * {@code paniolo-facturx-rules}, or the release configured with {@link Builder#orderXRules(Path)}.
   *
   * @param xmlOrPdf the Order-X XML or PDF; must not be {@code null}
   * @return the findings; never {@code null}
   * @throws IllegalArgumentException if the document is not Order-X or states an unknown Order-X profile
   * @throws IllegalStateException    if the document is a PDF and {@code paniolo-facturx} is missing
   */
  public ValidationResult validateOrder(byte[] xmlOrPdf) {
    Objects.requireNonNull(xmlOrPdf,
                           "xmlOrPdf");
    PanioloStopWatch watch = super.logStart("Paniolo.validateOrder");
    ValidationResult result = isPdf(xmlOrPdf) ?
                              validateOrderPdf(xmlOrPdf) :
                              validateOrderXml(xmlOrPdf);
    return measured(watch,
                    result);
  }

  /**
   * Validates an Order-X PDF: PDF/A conformance and the embedded XML.
   */
  private ValidationResult validateOrderPdf(byte[] xmlOrPdf) {
    var pdfa     = pdfa(xmlOrPdf);
    var messages = new ArrayList<>(pdfa.messages());
    var result   = validateOrderXml(facturX().extractOrder(xmlOrPdf));
    messages.addAll(result.messages());
    return ValidationResult.combine("Order-X PDF / " + result.profileName(),
                                    messages)
                           .withStatistics(ValidationStatistics.merge(pdfa.statistics(),
                                                                      result.statistics()));
  }

  /**
   * Writes a despatch advice (delivery note, advance shipping notice) as UBL 2.1
   * {@code DespatchAdvice} following Peppol BIS Despatch Advice 3.
   *
   * <p>Paniolo supports two XML syntaxes for despatch advices:
   * <ul>
   *   <li>UBL (this method) &ndash; the Peppol format, with published schema and rules; the right
   *       choice for exchange over Peppol. With {@link DespatchAdvice.Builder#oneLieferschein()} the
   *       advice is written as 1Lieferschein instead, without the Peppol restrictions.</li>
   *   <li>CIDA ({@link #toCiiDespatchAdvice(DespatchAdvice)}) &ndash; the UN/CEFACT format of the
   *       digital delivery note, which can be embedded into a PDF.</li>
   * </ul>
   *
   * <pre>{@code
   * String xml = paniolo.toDespatchAdvice(advice);
   * }</pre>
   *
   * <p>Writing does not validate; use {@link #validateDespatchAdvice(byte[])}.
   *
   * <p>Content UBL has no place for is rejected with an exception naming it, e.g. a VAT identifier
   * or tax number of a party, a contact or electronic address where the party's role has none,
   * shipment details without shipment id, a line without order line id, or (Peppol only) an actual
   * delivery date.
   *
   * @param advice the despatch advice; must not be {@code null}
   * @return the UBL DespatchAdvice XML
   * @throws IllegalArgumentException if the despatch advice has content UBL (or Peppol) cannot express
   * @see #toDespatchAdviceBytes(DespatchAdvice)
   */
  public String toDespatchAdvice(DespatchAdvice advice) {
    PanioloStopWatch watch  = super.logStart("Paniolo.toDespatchAdvice");
    String           result = DESPATCH_WRITER.toXml(advice);
    super.logStop(watch);
    return result;
  }

  /**
   * Writes a despatch advice as UTF-8 encoded UBL 2.1 {@code DespatchAdvice}; same as
   * {@link #toDespatchAdvice(DespatchAdvice)}, but ready to be stored or sent.
   *
   * @param advice the despatch advice; must not be {@code null}
   * @return the UBL DespatchAdvice XML encoded as UTF-8
   * @throws IllegalArgumentException if the despatch advice has content UBL (or Peppol) cannot express
   */
  public byte[] toDespatchAdviceBytes(DespatchAdvice advice) {
    PanioloStopWatch watch  = super.logStart("Paniolo.toDespatchAdviceBytes");
    byte[]           result = DESPATCH_WRITER.toBytes(advice);
    super.logStop(watch);
    return result;
  }

  /**
   * Writes a despatch advice as UN/CEFACT Cross Industry Despatch Advice (CIDA), the XML of the
   * digital delivery note, as the Mustang project writes it.
   *
   * <pre>{@code
   * String cida = paniolo.toCiiDespatchAdvice(advice);
   * }</pre>
   *
   * <p>CIDA covers less than UBL: content CIDA has no element for, such as shipment details, is
   * rejected with an exception naming it, so nothing is lost silently. To get a PDF delivery note with
   * the CIDA XML embedded, use {@link #toDespatchAdvicePdf(DespatchAdvice, byte[])}.
   *
   * @param advice the despatch advice; must not be {@code null}
   * @return the CIDA XML
   * @throws IllegalArgumentException if the despatch advice has content CIDA cannot express
   * @see #toCiiDespatchAdviceBytes(DespatchAdvice)
   */
  public String toCiiDespatchAdvice(DespatchAdvice advice) {
    PanioloStopWatch watch  = super.logStart("Paniolo.toCiiDespatchAdvice");
    String           result = CIDA_WRITER.toXml(advice);
    super.logStop(watch);
    return result;
  }

  /**
   * Writes a despatch advice as UTF-8 encoded CIDA XML; same as
   * {@link #toCiiDespatchAdvice(DespatchAdvice)}, but ready to be stored, sent or embedded.
   *
   * @param advice the despatch advice; must not be {@code null}
   * @return the CIDA XML encoded as UTF-8
   * @throws IllegalArgumentException if the despatch advice has content CIDA cannot express
   */
  public byte[] toCiiDespatchAdviceBytes(DespatchAdvice advice) {
    PanioloStopWatch watch  = super.logStart("Paniolo.toCiiDespatchAdviceBytes");
    byte[]           result = CIDA_WRITER.toBytes(advice);
    super.logStop(watch);
    return result;
  }

  /**
   * Creates a hybrid PDF delivery note: the despatch advice is written as CIDA and embedded as
   * {@code cida.xml} into the given PDF/A document, which becomes PDF/A-3. Requires
   * {@code paniolo-facturx}.
   *
   * <pre>{@code
   * byte[] deliveryNote = paniolo.toDespatchAdvicePdf(advice, pdfA);   // pdfA shows the delivery note
   * }</pre>
   *
   * @param advice the despatch advice; must not be {@code null}
   * @param pdf    PDF/A-1, -2 or -3 document with the visual delivery note; must not be {@code null}
   * @return the PDF/A-3 delivery note
   * @throws IllegalArgumentException if the despatch advice has content CIDA cannot express, or the
   *                                  PDF is not suitable
   * @throws IllegalStateException    if {@code paniolo-facturx} is missing
   * @see #toDespatchAdvicePdf(DespatchAdvice, byte[], List)
   */
  public byte[] toDespatchAdvicePdf(DespatchAdvice advice,
                                    byte[] pdf) {
    PanioloStopWatch watch = super.logStart("Paniolo.toDespatchAdvicePdf");
    byte[] result = toDespatchAdvicePdf(advice,
                                        pdf,
                                        List.of());
    super.logStop(watch);
    return result;
  }

  /**
   * Creates a hybrid PDF delivery note like {@link #toDespatchAdvicePdf(DespatchAdvice, byte[])} and
   * embeds further files, e.g. certificates of conformity or packing lists.
   *
   * <pre>{@code
   * byte[] deliveryNote = paniolo.toDespatchAdvicePdf(advice,
   *                                                   pdfA,
   *                                                   List.of(PdfAttachment.of("certificate.pdf", cert, "application/pdf")));
   * }</pre>
   *
   * <p>The rules for attachments are the same as for
   * {@link #toFacturX(Invoice, byte[], FacturXProfile, List)}.
   *
   * @param advice      the despatch advice; must not be {@code null}
   * @param pdf         PDF/A-1, -2 or -3 document with the visual delivery note; must not be {@code null}
   * @param attachments further files; may be empty, must not be {@code null}
   * @return the PDF/A-3 delivery note
   * @throws IllegalArgumentException if the despatch advice has content CIDA cannot express, the PDF
   *                                  is not suitable, or an attachment name is used twice
   * @throws IllegalStateException    if {@code paniolo-facturx} is missing
   */
  public byte[] toDespatchAdvicePdf(DespatchAdvice advice,
                                    byte[] pdf,
                                    List<PdfAttachment> attachments) {
    Objects.requireNonNull(advice,
                           "advice");
    PanioloStopWatch watch = super.logStart("Paniolo.toDespatchAdvicePdf");
    byte[] result = facturX().embedDespatchAdvice(Objects.requireNonNull(pdf,
                                                                         "pdf"),
                                                  toCiiDespatchAdviceBytes(advice),
                                                  List.copyOf(attachments));
    super.logStop(watch);
    return result;
  }

  /**
   * Renders a despatch advice as delivery note with the default layout (German labels), a visual
   * PDF/A-3b document without embedded XML. Requires {@code paniolo-pdf-layout}.
   *
   * @param advice the despatch advice; must not be {@code null}
   * @return the PDF/A-3b document
   * @throws IllegalStateException if {@code paniolo-pdf-layout} is missing
   * @see #toPdf(DespatchAdvice, PdfLayout)
   */
  public byte[] toPdf(DespatchAdvice advice) {
    return toPdf(advice,
                 PdfLayout.defaults());
  }

  /**
   * Renders a despatch advice as delivery note, a visual PDF/A-3b document: the despatching
   * party's letterhead, the delivery customer in the window, despatch and delivery dates, order
   * and shipment numbers, lines with delivered and outstanding quantities, batches and serial
   * numbers, shipment details, and a receipt confirmation to sign. Requires
   * {@code paniolo-pdf-layout}.
   *
   * @param advice the despatch advice; must not be {@code null}
   * @param layout language, logo, fonts, and colors; must not be {@code null}
   * @return the PDF/A-3b document, without embedded XML
   * @throws IllegalArgumentException if the logo or a font of the layout cannot be read
   * @throws IllegalStateException    if {@code paniolo-pdf-layout} is missing
   */
  public byte[] toPdf(DespatchAdvice advice,
                      PdfLayout layout) {
    Objects.requireNonNull(advice,
                           "advice");
    Objects.requireNonNull(layout,
                           "layout");
    PanioloStopWatch watch = super.logStart("Paniolo.toPdf");
    byte[] result = pdfLayout().render(advice,
                                       layout);
    super.logStop(watch);
    return result;
  }

  /**
   * Creates a PDF/A-3 delivery note with the embedded CIDA XML whose visual part Paniolo renders
   * itself, with the default layout. Requires {@code paniolo-pdf-layout}.
   *
   * @param advice the despatch advice; must not be {@code null}
   * @return the PDF/A-3 delivery note
   * @throws IllegalStateException if {@code paniolo-pdf-layout} is missing
   * @see #toDespatchAdvicePdf(DespatchAdvice, PdfLayout, List)
   */
  public byte[] toDespatchAdvicePdf(DespatchAdvice advice) {
    return toDespatchAdvicePdf(advice,
                               PdfLayout.defaults(),
                               List.of());
  }

  /**
   * Creates a PDF/A-3 delivery note whose visual part Paniolo renders with
   * {@link #toPdf(DespatchAdvice, PdfLayout)}, and embeds the CIDA XML and further files like
   * {@link #toDespatchAdvicePdf(DespatchAdvice, byte[], List)}. Requires
   * {@code paniolo-pdf-layout}.
   *
   * @param advice      the despatch advice; must not be {@code null}
   * @param layout      language, logo, fonts, and colors; must not be {@code null}
   * @param attachments further files; may be empty, must not be {@code null}
   * @return the PDF/A-3 delivery note
   * @throws IllegalArgumentException if the despatch advice has content CIDA cannot carry, an
   *                                  attachment name is used twice, or the layout has a watermark
   * @throws IllegalStateException    if {@code paniolo-pdf-layout} is missing
   */
  public byte[] toDespatchAdvicePdf(DespatchAdvice advice,
                                    PdfLayout layout,
                                    List<PdfAttachment> attachments) {
    Objects.requireNonNull(attachments,
                           "attachments");
    requireNoWatermark(layout);
    return toDespatchAdvicePdf(advice,
                               toPdf(advice,
                                     layout),
                               attachments);
  }

  /**
   * Reads a despatch advice from UBL {@code DespatchAdvice} XML, CIDA XML or a hybrid PDF delivery
   * note; the format is detected from the content.
   *
   * <pre>{@code
   * DespatchAdvice advice = paniolo.readDespatchAdvice(Files.readAllBytes(path));
   * List<String> orders = advice.orderReferences();
   * }</pre>
   *
   * <ul>
   *   <li>PDF &ndash; the embedded {@code cida.xml} is extracted and read; requires
   *       {@code paniolo-facturx}.</li>
   *   <li>CIDA XML &ndash; recognized from its root element.</li>
   *   <li>Everything else is read as UBL DespatchAdvice.</li>
   * </ul>
   *
   * <p>The read is strict: content the model does not carry is rejected with an exception that names
   * it. Use {@link #readDespatchAdvice(byte[], boolean)} to drop such content instead.
   *
   * @param xmlOrPdf the UBL or CIDA XML, or the PDF delivery note; must not be {@code null}
   * @return the despatch advice
   * @throws IllegalArgumentException if the document is no supported despatch advice or has content
   *                                  the model does not carry
   * @throws IllegalStateException    if the document is a PDF and {@code paniolo-facturx} is missing
   */
  public DespatchAdvice readDespatchAdvice(byte[] xmlOrPdf) {
    PanioloStopWatch watch = super.logStart("Paniolo.readDespatchAdvice");
    DespatchAdvice result = readDespatchAdvice(xmlOrPdf,
                                               false);
    super.logStop(watch);
    return result;
  }

  /**
   * Reads a despatch advice from UBL, CIDA or a PDF delivery note like
   * {@link #readDespatchAdvice(byte[])}, optionally ignoring content the model does not carry.
   *
   * <pre>{@code
   * DespatchAdvice advice = paniolo.readDespatchAdvice(received, true);
   * }</pre>
   *
   * @param xmlOrPdf          the UBL or CIDA XML, or the PDF delivery note; must not be {@code null}
   * @param ignoreUnsupported {@code true} to drop content the model does not carry, {@code false} to
   *                          reject it
   * @return the despatch advice
   * @throws IllegalArgumentException if the document is no supported despatch advice, or (with
   *                                  {@code ignoreUnsupported = false}) has unsupported content
   * @throws IllegalStateException    if the document is a PDF and {@code paniolo-facturx} is missing
   */
  public DespatchAdvice readDespatchAdvice(byte[] xmlOrPdf,
                                           boolean ignoreUnsupported) {
    Objects.requireNonNull(xmlOrPdf,
                           "xmlOrPdf");
    PanioloStopWatch watch = super.logStart("Paniolo.readDespatchAdvice");
    DespatchAdvice   result;
    if (isPdf(xmlOrPdf)) {
      result = (ignoreUnsupported ?
                CiiDespatchAdviceReader.lenient() :
                CIDA_READER).fromBytes(facturX().extractDespatchAdvice(xmlOrPdf));
    } else if (isCida(xmlOrPdf)) {
      result = (ignoreUnsupported ?
                CiiDespatchAdviceReader.lenient() :
                CIDA_READER).fromBytes(xmlOrPdf);
    } else {
      result = (ignoreUnsupported ?
                DespatchAdviceReader.lenient() :
                DESPATCH_READER).fromBytes(xmlOrPdf);
    }
    super.logStop(watch);
    return result;
  }

  /**
   * Validates a despatch advice in UBL or CIDA, or a hybrid PDF delivery note.
   *
   * <pre>{@code
   * Paniolo paniolo = Paniolo.builder()
   *                          .despatchAdviceRules(Path.of("/opt/peppol/peppol-despatch-advice.xslt"))
   *                          .build();
   * ValidationResult result = paniolo.validateDespatchAdvice(xml);
   * }</pre>
   *
   * <p><b>UBL DespatchAdvice</b> is validated in two stages:
   * <ol>
   *   <li>The UBL 2.1 schema from the configured KoSIT bundle. If the schema fails, the Schematron stage
   *       is skipped.</li>
   *   <li>The Peppol BIS Despatch Advice 3 Schematron, if configured with
   *       {@link Builder#despatchAdviceRules(Path)}; otherwise the result carries the information
   *       {@code PEPPOL-RULES-NOT-CHECKED}.</li>
   * </ol>
   *
   * <p>A <b>1Lieferschein</b> (customization identifier
   * {@link DespatchAdvice#ONE_LIEFERSCHEIN_CUSTOMIZATION}) has no published rules: only the UBL
   * schema is checked, and the result carries the information {@code 1LIEFERSCHEIN-NOT-CHECKED}.
   *
   * <p><b>CIDA</b> has no published schema or rules. CIDA XML is therefore only read with the model
   * (unreadable content is reported as {@code FATAL} finding {@code CIDA-READ}), and the result always
   * carries the warning {@code CIDA-NOT-CHECKED}. A PDF delivery note is in addition checked for PDF/A
   * conformance if {@code paniolo-pdfa} is on the classpath.
   *
   * @param xml the UBL or CIDA XML, or the PDF delivery note; must not be {@code null}
   * @return the findings; never {@code null}
   * @throws IllegalArgumentException if the document is neither a UBL DespatchAdvice nor CIDA
   * @throws IllegalStateException    if the document is a PDF and {@code paniolo-facturx} is missing
   */
  public ValidationResult validateDespatchAdvice(byte[] xml) {
    Objects.requireNonNull(xml,
                           "xml");
    PanioloStopWatch watch = super.logStart("Paniolo.validateDespatchAdvice");
    ValidationResult result = isPdf(xml) || isCida(xml) ?
                              validateCida(xml) :
                              validateUblDespatchAdvice(xml);
    return measured(watch,
                    result);
  }

  /**
   * Validates a UBL DespatchAdvice: UBL schema and, if configured, the Peppol Schematron.
   */
  private ValidationResult validateUblDespatchAdvice(byte[] xml) {
    if (!DespatchAdviceWriter.NAMESPACE.equals(rootNamespace(xml))) {
      throw new IllegalArgumentException("Not a UBL DespatchAdvice or CIDA despatch advice");
    }
    InvoiceValidator schema = compiled(new DespatchKey(false),
                                       () -> newValidator(new ValidationProfile("UBL 2.1 DespatchAdvice",
                                                                                        rules.get(),
                                                                                        rules.get()
                                                                                             .resolve("resources/ubl/2.1/xsd/maindoc/UBL-DespatchAdvice-2.1.xsd"),
                                                                                        List.of(),
                                                                                        Map.of())));
    InvoiceValidator peppol = despatchAdviceRules == null ?
                              null :
                              compiled(new DespatchKey(true),
                                       () -> newValidator(new ValidationProfile("Peppol BIS Despatch Advice 3",
                                                                                        despatchAdviceRules.getParent(),
                                                                                        null,
                                                                                        List.of(despatchAdviceRules),
                                                                                        Map.of())));
    var result   = schema.validate(xml);
    var messages = new ArrayList<>(result.messages());
    if (!result.isValid()) {
      return ValidationResult.combine(result.profileName(),
                                      messages)
                             .withStatistics(result.statistics());
    }
    String customization = customizationId(reader(xml).get());
    if (customization != null && DespatchAdvice.ONE_LIEFERSCHEIN_CUSTOMIZATION.equals(customization.strip())) {
      messages.add(new ValidationMessage("1LIEFERSCHEIN-NOT-CHECKED",
                                         Severity.INFO,
                                         "Only the UBL schema was checked; 1Lieferschein has no published rules",
                                         "",
                                         ValidationMessage.Stage.SCHEMATRON));
      return ValidationResult.combine(result.profileName(),
                                      messages)
                             .withStatistics(result.statistics());
    }
    if (peppol == null) {
      messages.add(new ValidationMessage("PEPPOL-RULES-NOT-CHECKED",
                                         Severity.INFO,
                                         "Only the UBL schema was checked; configure the Peppol BIS Despatch Advice rules with Paniolo.builder().despatchAdviceRules(path)",
                                         "",
                                         ValidationMessage.Stage.SCHEMATRON));
      return ValidationResult.combine(result.profileName(),
                                      messages)
                             .withStatistics(result.statistics());
    }
    var rules = peppol.validate(xml);
    messages.addAll(rules.messages());
    return ValidationResult.combine("Peppol BIS Despatch Advice 3",
                                    messages)
                           .withStatistics(ValidationStatistics.merge(result.statistics(),
                                                                      rules.statistics()));
  }

  private ValidationResult validateCida(byte[] xmlOrPdf) {
    var     messages = new ArrayList<ValidationMessage>();
    boolean pdf      = isPdf(xmlOrPdf);
    ValidationStatistics statistics = null;
    if (pdf) {
      var pdfa = pdfa(xmlOrPdf);
      messages.addAll(pdfa.messages());
      statistics = pdfa.statistics();
    }
    try {
      CiiDespatchAdviceReader.lenient()
                             .fromBytes(pdf ?
                                        facturX().extractDespatchAdvice(xmlOrPdf) :
                                        xmlOrPdf);
      messages.add(new ValidationMessage("CIDA-NOT-CHECKED",
                                         Severity.WARNING,
                                         "CIDA has no published schema or rules; the despatch advice was only read with the Paniolo model",
                                         "",
                                         ValidationMessage.Stage.XSD));
    } catch (IllegalArgumentException e) {
      messages.add(new ValidationMessage("CIDA-READ",
                                         Severity.FATAL,
                                         e.getMessage(),
                                         "",
                                         ValidationMessage.Stage.XML));
    }
    return ValidationResult.combine("CIDA despatch advice",
                                    messages)
                           .withStatistics(statistics);
  }

  private ValidationResult validateOrderXml(byte[] xml) {
    if (!OrderXWriter.RSM.equals(rootNamespace(xml))) {
      throw new IllegalArgumentException("Not an Order-X document");
    }
    String guideline = guideline(reader(xml).get());
    var    profile   = OrderXProfile.ofSpecificationId(guideline);
    if (profile == null) {
      throw new IllegalArgumentException("Unknown Order-X profile: " + guideline);
    }
    return compiled(new OrderXKey(profile),
                    () -> newValidator(ValidationProfile.orderX(orderXRules.get(),
                                                                        profile.name()))).validate(xml);
  }

  /**
   * Migrates a ZUGFeRD 1.0 document, which is no longer valid, to ZUGFeRD 2 / Factur-X in the
   * corresponding profile.
   *
   * <table>
   *   <caption>Profile mapping</caption>
   *   <tr><th>ZUGFeRD 1.0</th><th>Factur-X / ZUGFeRD 2</th></tr>
   *   <tr><td>BASIC</td><td>{@link FacturXProfile#BASIC}</td></tr>
   *   <tr><td>COMFORT</td><td>{@link FacturXProfile#EN16931}</td></tr>
   *   <tr><td>EXTENDED</td><td>{@link FacturXProfile#EXTENDED}</td></tr>
   * </table>
   *
   * <p>The result has the same format as the input:
   * <ul>
   *   <li>XML in, CII XML out.</li>
   *   <li>PDF in, Factur-X PDF/A-3 out: the embedded {@code ZUGFeRD-invoice.xml} is replaced by
   *       {@code factur-x.xml}, the XMP metadata is rewritten and all other attachments are kept.
   *       Requires {@code paniolo-facturx}.</li>
   * </ul>
   *
   * <pre>{@code
   * byte[] migrated = paniolo.migrateZugferd1(oldPdf);
   * ValidationResult result = paniolo.validate(migrated);   // against the Factur-X rules of the profile
   * }</pre>
   *
   * <p>If a document has content its corresponding profile does not allow (e.g. a BASIC document with
   * contacts), migrate it into a higher profile with {@link #migrateZugferd1(byte[], FacturXProfile)}.
   *
   * @param document the ZUGFeRD 1.0 XML or PDF; must not be {@code null}
   * @return the migrated CII XML or Factur-X PDF
   * @throws IllegalArgumentException if the document is not ZUGFeRD 1.0 or has content the model or
   *                                  the target profile cannot carry
   * @throws IllegalStateException    if the document is a PDF and {@code paniolo-facturx} is missing
   */
  public byte[] migrateZugferd1(byte[] document) {
    PanioloStopWatch watch = super.logStart("Paniolo.migrateZugferd1");
    byte[] result = migrateZugferd1(document,
                                    null);
    super.logStop(watch);
    return result;
  }

  /**
   * Migrates a ZUGFeRD 1.0 document like {@link #migrateZugferd1(byte[])}, but into an explicitly
   * chosen profile, e.g. a BASIC document with contacts into {@link FacturXProfile#EN16931}.
   *
   * <pre>{@code
   * byte[] migrated = paniolo.migrateZugferd1(oldPdf, FacturXProfile.EN16931);
   * }</pre>
   *
   * @param document the ZUGFeRD 1.0 XML or PDF; must not be {@code null}
   * @param target   {@link FacturXProfile#BASIC}, {@link FacturXProfile#EN16931} or
   *                 {@link FacturXProfile#EXTENDED}; {@code null} for the corresponding profile
   * @return the migrated CII XML or Factur-X PDF
   * @throws IllegalArgumentException if the target is another profile, the document is not
   *                                  ZUGFeRD 1.0, or it has content the target profile cannot carry
   * @throws IllegalStateException    if the document is a PDF and {@code paniolo-facturx} is missing
   */
  public byte[] migrateZugferd1(byte[] document,
                                FacturXProfile target) {
    Objects.requireNonNull(document,
                           "document");
    if (target != null && !List.of(FacturXProfile.BASIC,
                                   FacturXProfile.EN16931,
                                   FacturXProfile.EXTENDED)
                               .contains(target)) {
      throw new IllegalArgumentException("ZUGFeRD 1.0 migrates to BASIC, EN 16931 or EXTENDED, not " + target);
    }
    PanioloStopWatch watch = super.logStart("Paniolo.migrateZugferd1");
    boolean          pdf   = isPdf(document);
    byte[] xml = pdf ?
                 facturX().extract(document)
                          .xml() :
                 document;
    if (!isZugferd1(xml)) {
      throw new IllegalArgumentException("The document is not ZUGFeRD 1.0");
    }
    var reader = target == null ?
                 ZUGFERD_1 :
                 ZugferdV1InvoiceReader.withSpecificationId(target.getSpecificationId());
    var invoice = reader.fromBytes(xml);
    var profile = FacturXProfile.ofSpecificationId(invoice.processControl()
                                                          .specificationId());
    byte[] migrated = toBytes(invoice,
                              InvoiceSyntax.CII);
    byte[] result = pdf ?
                    facturX().replaceInvoice(document,
                                             migrated,
                                             profile) :
                    migrated;
    super.logStop(watch);
    return result;
  }

  private InvoiceXmlWriter writer(InvoiceSyntax syntax) {
    return writers.get(Objects.requireNonNull(syntax,
                                              "syntax"));
  }

  /**
   * The validator of the XRechnung extension scenario, compiled once per document type.
   */
  private InvoiceValidator extensionValidator(ValidationProfile.Syntax type) {
    return compiled(new ExtensionKey(type),
                    () -> newValidator(ValidationProfile.xrechnungExtension(rules.get(),
                                                                                    type)));
  }

  /**
   * The validator of ZUGFeRD 1.0, compiled once.
   */
  private InvoiceValidator zugferd1Validator() {
    return compiled(new Zugferd1Key(),
                    () -> newValidator(ValidationProfile.zugferd1(FacturXRules.zugferd1())));
  }

  /**
   * Logs the end of a validation and adds the duration it took to the result.
   */
  /**
   * Adds the findings of the calculation check if it is enabled; see
   * {@link Builder#calculationCheck(boolean)}. A PDF whose invoice XML cannot be extracted already
   * carries the container findings and is not recalculated.
   */
  private ValidationResult calculated(byte[] document,
                                      ValidationResult result) {
    if (!calculationCheck) {
      return result;
    }
    byte[] xml = document;
    if (isPdf(document)) {
      try {
        xml = facturX().extract(document)
                       .xml();
      } catch (IllegalArgumentException | IllegalStateException | UnsupportedOperationException | UncheckedIOException e) {
        return result;
      }
    }
    var messages = new ArrayList<>(result.messages());
    try {
      // Only the read result is needed; importXml would also keep a copy of the XML.
      messages.addAll(CalculationCheck.check(invoiceReader(xml).fromBytes(xml,
                                                                          ReadMode.LENIENT),
                                             result.messages()));
    } catch (IllegalArgumentException | UncheckedIOException e) {
      messages.add(CalculationCheck.notChecked(e.getMessage()));
    }
    return result.withMessages(messages);
  }

  private ValidationResult measured(PanioloStopWatch watch,
                                    ValidationResult result) {
    super.logStop(watch);
    return result.withDuration(Duration.ofNanos(watch.getTotalTimeNanos()));
  }

  /**
   * Validates an invoice by the XRechnung version its specification identifier states: an older
   * version against the packaged KoSIT release of that version, adding the information
   * {@code XR-OLDER-VERSION}; the XRechnung extension against the extension scenario; everything
   * else against the configured XRechnung rules. A document of EN 16931:2026 (e.g. XRechnung 4.0)
   * is not checked and reported with the error {@code EN16931-2026-NOT-SUPPORTED}: the rules of
   * EN 16931:2017 would report findings that do not apply to it and miss those that do.
   */
  private ValidationResult validateXrechnung(ValidationProfile.Syntax type,
                                             String specificationId,
                                             Function<InvoiceValidator, ValidationResult> validation) {
    if (SpecificationGenerations.isEn16931Of2026(specificationId)) {
      return new ValidationResult("EN 16931:2026 (not checked)",
                                  List.of(new ValidationMessage("EN16931-2026-NOT-SUPPORTED",
                                                                Severity.ERROR,
                                                                "The document states " + specificationId + "; " + SpecificationGenerations.NOT_SUPPORTED + ", so it was not checked",
                                                                "",
                                                                ValidationMessage.Stage.SCHEMATRON)));
    }
    boolean extension = isXrechnungExtension(type,
                                             specificationId);
    String version = olderXrechnungVersion(specificationId);
    if (version == null) {
      return validation.apply(extension ?
                              extensionValidator(type) :
                              validator(type));
    }
    var result = validation.apply(compiled(new OlderXrechnungKey(type,
                                                                 version,
                                                                 extension),
                                           () -> newValidator(olderXrechnung(type,
                                                                                     version,
                                                                                     extension))));
    var messages = new ArrayList<>(result.messages());
    messages.add(new ValidationMessage("XR-OLDER-VERSION",
                                       Severity.INFO,
                                       "The document states XRechnung " +
                                       version +
                                       " and was checked against the rules of XRechnung " +
                                       XrechnungRules.legacyVersion(version) +
                                       "; the current version packaged with Paniolo is XRechnung " +
                                       XrechnungRules.VERSION,
                                       "",
                                       ValidationMessage.Stage.SCHEMATRON));
    return result.withMessages(messages);
  }

  /**
   * Compiles each validator once; compilation takes noticeable time, validation is cheap.
   */
  private InvoiceValidator validator(ValidationProfile.Syntax type) {
    return validator(type,
                     true);
  }

  private InvoiceValidator validator(ValidationProfile.Syntax type,
                                     boolean xrechnung) {
    Objects.requireNonNull(type,
                           "type");
    return compiled(new ValidatorKey(type,
                                     xrechnung),
                    () -> newValidator(xrechnung ?
                                               ValidationProfile.xrechnung(rules.get(),
                                                                           type) :
                                               ValidationProfile.en16931(rules.get(),
                                                                         type)));
  }

  /**
   * Validates an invoice with the rules of its Factur-X profile; a null profile is unknown and,
   * like XRECHNUNG, checked by {@link #validateXrechnung}.
   */
  private ValidationResult validateFacturX(FacturXProfile profile,
                                           ValidationProfile.Syntax type,
                                           String specificationId,
                                           Supplier<Reader> content,
                                           Function<InvoiceValidator, ValidationResult> validation) {
    if (profile == FacturXProfile.XRECHNUNG) {
      return validateXrechnung(type,
                               specificationId,
                               validation);
    }
    if (profile == null) {
      var result = validateXrechnung(type,
                                     specificationId,
                                     validation);
      return statesXrechnung(specificationId) ?
             result :
             xrechnungRulesInformative(specificationId,
                                       result);
    }
    if (profile == FacturXProfile.EXTENDED_CTC_FR && type == ValidationProfile.Syntax.CII) {
      return validateExtendedCtcFr(isFrenchDomestic(content.get()),
                                   validation);
    }
    // The Factur-X schemas describe CII only; UBL falls back to EN 16931.
    if (type == ValidationProfile.Syntax.CII) {
      String level = profile.getRulesLevel();
      return validation.apply(compiled(new FacturXKey(level),
                                       () -> newValidator(ValidationProfile.facturX(facturXRules.get(),
                                                                                            level))));
    }
    String rules = "the Factur-X rules describe CII only";
    if (!profile.hasLines()) {
      // EN 16931 does not apply to documents without lines.
      return new ValidationResult("Factur-X " + profile.getRulesLevel() + " (not checked)",
                                  List.of(new ValidationMessage("FX-RULES-REQUIRED",
                                                                Severity.WARNING,
                                                                "Profile " + profile.getConformanceLevel() + " is not an EN 16931 invoice and was not checked; " + rules,
                                                                "",
                                                                ValidationMessage.Stage.SCHEMATRON)));
    }
    var result = validation.apply(validator(type,
                                            false));
    if (profile != FacturXProfile.EXTENDED && profile != FacturXProfile.EXTENDED_CTC_FR) {
      return result;
    }
    var messages = new ArrayList<>(result.messages());
    messages.add(new ValidationMessage("FX-EXTENDED-NOT-CHECKED",
                                       Severity.INFO,
                                       "Profile " + profile.getConformanceLevel() + " was checked against CII D16B and EN 16931 only, which reject some EXTENDED content such as logistics service charges; " + rules,
                                       "",
                                       ValidationMessage.Stage.SCHEMATRON));
    return result.withMessages(messages);
  }

  /**
   * Validates CII of the French profile EXTENDED-CTC-FR: the Factur-X EXTENDED schema, then the
   * EXTENDED-CTC-FR Schematron of FNFE-MPE and, for invoices between French parties, the rules BR-FR
   * (flux 2).
   */
  private ValidationResult validateExtendedCtcFr(boolean frenchDomestic,
                                                 Function<InvoiceValidator, ValidationResult> validation) {
    var schema = validation.apply(compiled(new FacturXSchemaKey("EXTENDED"),
                                           () -> newValidator(ValidationProfile.facturXSchema(facturXRules.get(),
                                                                                                      "EXTENDED"))));
    if (!schema.isValid()) {
      return ValidationResult.combine("EXTENDED-CTC-FR",
                                      schema.messages())
                             .withStatistics(schema.statistics());
    }
    var rules = validation.apply(compiled(new ExtendedCtcFrKey(frenchDomestic),
                                          () -> newValidator(ValidationProfile.extendedCtcFr(franceRules.get(),
                                                                                                     frenchDomestic))));
    var messages = new ArrayList<>(schema.messages());
    messages.addAll(rules.messages());
    if (!frenchDomestic) {
      messages.add(new ValidationMessage("BR-FR-NOT-APPLICABLE",
                                         Severity.INFO,
                                         "The French rules BR-FR (flux 2) apply to invoices between a French seller and a French buyer and were not checked",
                                         "",
                                         ValidationMessage.Stage.SCHEMATRON));
    }
    return ValidationResult.combine(rules.profileName(),
                                    messages)
                           .withStatistics(ValidationStatistics.merge(schema.statistics(),
                                                                      rules.statistics()));
  }

  /**
   * Returns the validator of the key, compiling it on first use. Compilation takes noticeable time
   * and is locked per key, so threads using other, already compiled validators are not blocked.
   * A failed compilation is not cached and is retried on the next call.
   */
  private InvoiceValidator compiled(Object key,
                                    Supplier<InvoiceValidator> compiler) {
    return validators.computeIfAbsent(key,
                                      k -> new CompiledValidator())
                     .get(compiler);
  }

  /**
   * Compiles a validator of this instance, with rule statistics if configured.
   */
  private InvoiceValidator newValidator(ValidationProfile profile) {
    return new InvoiceValidator(profile,
                                ruleStatistics);
  }

  /**
   * Checks the PDF/A conformance with paniolo-pdfa, with its statistics if configured; without the
   * module, an information says that PDF/A was not checked.
   */
  private ValidationResult pdfa(byte[] pdf) {
    return PdfaHolder.SUPPORT.map(support -> support.check(pdf,
                                                           ruleStatistics))
                             .orElseGet(() -> new ValidationResult("PDF/A",
                                                                   List.of(new ValidationMessage("PDFA-NOT-CHECKED",
                                                                                                 Severity.INFO,
                                                                                                 "PDF/A conformance was not checked; add the dependency io.github.nalukit:paniolo-pdfa",
                                                                                                 "",
                                                                                                 ValidationMessage.Stage.PDF_A))));
  }

  /**
   * Holds one lazily compiled validator.
   */
  private static final class CompiledValidator {
    private volatile InvoiceValidator validator;

    InvoiceValidator get(Supplier<InvoiceValidator> compiler) {
      InvoiceValidator result = validator;
      if (result == null) {
        synchronized (this) {
          result = validator;
          if (result == null) {
            result    = compiler.get();
            validator = result;
          }
        }
      }
      return result;
    }
  }



  private record ExtensionKey(ValidationProfile.Syntax type) {
  }



  private record ValidatorKey(ValidationProfile.Syntax type, boolean xrechnung) {
  }



  private record OlderXrechnungKey(ValidationProfile.Syntax type, String version, boolean extension) {
  }



  private record Zugferd1Key() {
  }



  private record FacturXKey(String level) {
  }



  private record FacturXSchemaKey(String level) {
  }



  private record ExtendedCtcFrKey(boolean frenchRules) {
  }



  private record OrderXKey(OrderXProfile profile) {
  }



  private record DespatchKey(boolean peppol) {
  }



  /**
   * Looks up the optional PDF/A implementation once, on first use.
   */
  private static final class PdfaHolder {
    private static final Optional<PdfaSupport> SUPPORT = ServiceLoader.load(PdfaSupport.class,
                                                                            Paniolo.class.getClassLoader())
                                                                      .findFirst();
  }



  /**
   * Looks up the optional PDF layout implementation once, on first use.
   */
  private static final class PdfLayoutHolder {
    private static final Optional<PdfLayoutSupport> SUPPORT = ServiceLoader.load(PdfLayoutSupport.class,
                                                                                 Paniolo.class.getClassLoader())
                                                                           .findFirst();
  }



  /**
   * Looks up the optional Factur-X implementation once, on first use.
   */
  private static final class FacturXHolder {
    private static final Optional<FacturXSupport> SUPPORT = ServiceLoader.load(FacturXSupport.class,
                                                                               Paniolo.class.getClassLoader())
                                                                         .findFirst();
  }



  /**
   * Configures a {@link Paniolo} instance; obtained from {@link Paniolo#builder()}.
   *
   * <p>All settings are optional. The paths of the Factur-X, EXTENDED-CTC-FR, Order-X and Peppol
   * rules are checked when they are set, so a wrong path fails at application start. The KoSIT
   * bundle of {@link #rules(Path)} is loaded on the first validation; call {@link Paniolo#warmUp()}
   * to detect a wrong bundle at start-up as well. The builder is not thread-safe, the built
   * {@link Paniolo} is.
   *
   * <pre>{@code
   * Paniolo paniolo = Paniolo.builder()
   *                          .facturXRules(Path.of("/opt/factur-x"))
   *                          .build();
   * }</pre>
   */
  public static final class Builder {
    private Path rules;
    private Path facturXRules;
    private Path franceRules;
    private Path orderXRules;
    private Path    despatchAdviceRules;
    private boolean calculationCheck;
    private boolean ruleStatistics;
    private UnknownDocumentHandling unknownDocuments = UnknownDocumentHandling.THROW;

    private Builder() {
    }

    /**
     * Selects what the validation methods that detect the document type do with a document whose
     * type cannot be detected: XML that is not well-formed, has no root element or an unsupported
     * one, also as XML embedded in a PDF. This concerns {@link Paniolo#validate(String)},
     * {@link Paniolo#validate(byte[])}, {@link Paniolo#validate(InputStream)}, and
     * {@link Paniolo#validate(byte[], FacturXProfile)}.
     *
     * <pre>{@code
     * Paniolo paniolo = Paniolo.builder()
     *                          .unknownDocuments(UnknownDocumentHandling.REPORT_INVALID)
     *                          .build();
     * ValidationResult result = paniolo.validate(upload);   // never throws for unknown documents
     * result.isValid();                                     // false, FATAL PANIOLO-UNKNOWN-DOCUMENT
     * }</pre>
     *
     * <p>{@link UnknownDocumentHandling#THROW} (default) throws an
     * {@link IllegalArgumentException}, so "no supported invoice" stays distinguishable from "invalid
     * invoice". {@link UnknownDocumentHandling#REPORT_INVALID} is the compatibility mode that behaves
     * like Mustang: a {@code FATAL} finding {@code XML-PARSE} or {@code PANIOLO-UNKNOWN-DOCUMENT}, so
     * upload and batch interfaces get a result for every input.
     *
     * @param handling what to do with undetectable documents; must not be {@code null}
     * @return this builder
     */
    public Builder unknownDocuments(UnknownDocumentHandling handling) {
      this.unknownDocuments = Objects.requireNonNull(handling,
                                                     "handling");
      return this;
    }

    /**
     * Adds execution statistics to every validation result: per Schematron stylesheet the active
     * patterns, fired rules, failed asserts, and successful reports, and per PDF/A check the flavour,
     * the number of checks, and the failures per rule.
     *
     * <pre>{@code
     * Paniolo paniolo = Paniolo.builder()
     *                          .ruleStatistics(true)
     *                          .build();
     * ValidationResult result = paniolo.validate(xml);
     * result.statistics().firedRules();     // rule contexts the official rules applied
     * result.toXml();                       // with an element statistics after the summary
     * }</pre>
     *
     * <p>The statistics document how much of the rules was applied, e.g. for audits; they do not
     * change findings or validity. They cost time, because the Schematron stylesheets then report
     * every matched rule context: use a separate instance for audits and keep the regular one
     * without statistics. Off by default; {@link ValidationResult#statistics()} is null then.
     *
     * @param enabled whether validation results carry execution statistics
     * @return this builder
     */
    public Builder ruleStatistics(boolean enabled) {
      this.ruleStatistics = enabled;
      return this;
    }

    /**
     * Adds the optional calculation check to every invoice validation: after the official rules, the
     * amounts are recalculated with the invoice model and reported in the stage
     * {@link ValidationMessage.Stage#CALCULATION}.
     *
     * <pre>{@code
     * Paniolo paniolo = Paniolo.builder()
     *                          .calculationCheck(true)
     *                          .build();
     * ValidationResult result = paniolo.validate(xml);
     * result.isValid();                                            // official rules only
     * result.messages(ValidationMessage.Stage.CALCULATION);        // PANIOLO-CALC-... hints
     * }</pre>
     *
     * <p>The findings are warnings, so they never change {@link ValidationResult#isValid()}; a
     * finding whose relationship an official rule of the same result already reports is lowered to
     * information and names that rule. The check is worthwhile mainly for Factur-X profiles, whose
     * rules do not check line net amounts, net prices from gross prices, or allowance percentages;
     * XRechnung covers these with the Peppol rules it includes. See {@link CalculationCheck} for the
     * rules and their official counterparts. Off by default.
     *
     * @param enabled whether invoice validations recalculate the amounts
     * @return this builder
     */
    public Builder calculationCheck(boolean enabled) {
      this.calculationCheck = enabled;
      return this;
    }

    /**
     * Uses an extracted KoSIT XRechnung validator configuration instead of the rules packaged in
     * {@code paniolo-xrechnung-rules}.
     *
     * <p>Typical use cases: validating against a newer KoSIT release before Paniolo ships it, or
     * against an older one during an XRechnung version change.
     *
     * <pre>{@code
     * Paniolo paniolo = Paniolo.builder()
     *                          .rules(Path.of("/opt/kosit/xrechnung-3.0.2"))
     *                          .build();
     * }</pre>
     *
     * <p>Pass the root directory of the unzipped {@code validator-configuration-xrechnung} release,
     * i.e. the directory that contains {@code scenarios.xml} and {@code resources/}. The bundle
     * provides the UBL / CII schemas and the EN 16931 and XRechnung Schematron for all invoice
     * validations, and the UBL schema for {@link Paniolo#validateDespatchAdvice(byte[])}.
     *
     * <p>Unlike the other rule settings, the directory is not checked here: the bundle is loaded on
     * the first validation (or by {@link Paniolo#warmUp()}), which throws a
     * {@link ValidationException} if it cannot be loaded. Older XRechnung versions (1.2 to 2.3) are
     * still validated against their packaged releases.
     *
     * @param bundle directory of the extracted KoSIT validator configuration; must not be {@code null}
     * @return this builder
     */
    public Builder rules(Path bundle) {
      this.rules = Objects.requireNonNull(bundle,
                                          "bundle");
      return this;
    }

    /**
     * Uses an extracted Factur-X / ZUGFeRD release instead of the rules packaged in
     * {@code paniolo-facturx-rules} (ZUGFeRD 2.5.2 / Factur-X 1.09.2).
     *
     * <p>Typical use cases: validating against a newer release before Paniolo ships it, or against an
     * older one during a version change. Download the package from FeRD or FNFE-MPE, extract it, and
     * pass the directory; see {@link ValidationProfile#facturX(Path, String)} for the expected file
     * names.
     *
     * <pre>{@code
     * Paniolo paniolo = Paniolo.builder()
     *                          .facturXRules(Path.of("/opt/factur-x/1.09.2"))
     *                          .build();
     * }</pre>
     *
     * <p>The rules apply to CII only; profile XRECHNUNG is still validated against EN 16931 and
     * XRechnung.
     *
     * @param packageDirectory directory of the extracted Factur-X package; must not be {@code null}
     * @return this builder
     * @throws IllegalArgumentException if the directory does not exist
     */
    public Builder facturXRules(Path packageDirectory) {
      Path directory = Objects.requireNonNull(packageDirectory,
                                              "packageDirectory");
      if (!Files.isDirectory(directory)) {
        throw new IllegalArgumentException("Factur-X rules directory does not exist: " + directory);
      }
      this.facturXRules = directory;
      return this;
    }

    /**
     * Uses other CII rules of the French profile EXTENDED-CTC-FR instead of those packaged in
     * {@code paniolo-facturx-rules} (FNFE-MPE France_RFE 1.4.0.04).
     *
     * <p>Download a release of <a href="https://github.com/fnfempe/France_RFE">France_RFE</a> and
     * pass its root directory or its folder {@code FNFE_RFE_INVOICE/CII/EXTENDED-CTC-FR}; see
     * {@link ValidationProfile#extendedCtcFr(Path, boolean)} for the expected file names.
     *
     * <pre>{@code
     * Paniolo paniolo = Paniolo.builder()
     *                          .extendedCtcFrRules(Path.of("/opt/france-rfe/1.4.0.05"))
     *                          .build();
     * }</pre>
     *
     * @param directory directory with the French CII rules; must not be {@code null}
     * @return this builder
     * @throws IllegalArgumentException if the directory does not exist
     */
    public Builder extendedCtcFrRules(Path directory) {
      Path rulesDirectory = Objects.requireNonNull(directory,
                                                   "directory");
      if (!Files.isDirectory(rulesDirectory)) {
        throw new IllegalArgumentException("EXTENDED-CTC-FR rules directory does not exist: " + rulesDirectory);
      }
      this.franceRules = rulesDirectory;
      return this;
    }

    /**
     * Uses another Order-X release in {@link Paniolo#validateOrder(byte[])} instead of the Order-X 1.0
     * rules packaged in {@code paniolo-facturx-rules}.
     *
     * <p>The directory needs per profile the XSDs and the Schematron compiled to SVRL; see
     * {@link ValidationProfile#orderX(Path, String)} for the expected file names. The
     * {@code -compiled.xsl} files of the official package report with {@code xsl:message} and cannot
     * be used; {@code scripts/package-orderx-rules.sh} compiles the {@code .sch} files.
     *
     * <pre>{@code
     * Paniolo paniolo = Paniolo.builder()
     *                          .orderXRules(Path.of("/opt/order-x"))
     *                          .build();
     * }</pre>
     *
     * @param packageDirectory directory of the extracted Order-X package; must not be {@code null}
     * @return this builder
     * @throws IllegalArgumentException if the directory does not exist
     */
    public Builder orderXRules(Path packageDirectory) {
      Path directory = Objects.requireNonNull(packageDirectory,
                                              "packageDirectory");
      if (!Files.isDirectory(directory)) {
        throw new IllegalArgumentException("Order-X rules directory does not exist: " + directory);
      }
      this.orderXRules = directory;
      return this;
    }

    /**
     * Enables the Peppol BIS Despatch Advice 3 business rules in
     * {@link Paniolo#validateDespatchAdvice(byte[])}, in addition to the UBL schema.
     *
     * <p>Pass the <em>compiled</em> Schematron, i.e. the XSLT file published by OpenPeppol, not the
     * {@code .sch} source. Paniolo does not distribute it. Stylesheets it imports are resolved relative
     * to the file, so keep them in the same directory. Without this setting, despatch advice validation
     * checks the schema only and adds the information {@code PEPPOL-RULES-NOT-CHECKED}.
     *
     * <pre>{@code
     * Paniolo paniolo = Paniolo.builder()
     *                          .despatchAdviceRules(Path.of("/opt/peppol/peppol-despatch-advice.xslt"))
     *                          .build();
     * }</pre>
     *
     * @param compiledSchematron the compiled Schematron XSLT file; must not be {@code null}
     * @return this builder
     * @throws IllegalArgumentException if the file does not exist
     */
    public Builder despatchAdviceRules(Path compiledSchematron) {
      Path file = Objects.requireNonNull(compiledSchematron,
                                         "compiledSchematron");
      if (!Files.isRegularFile(file)) {
        throw new IllegalArgumentException("Despatch advice rules file does not exist: " + file);
      }
      this.despatchAdviceRules = file.toAbsolutePath();
      return this;
    }

    /**
     * Creates the configured instance.
     *
     * <p>Building is cheap: the packaged rules are extracted and the validators compiled only when the
     * first validation needs them. The builder can be reused; later changes do not affect instances
     * already built.
     *
     * @return a new, immutable and thread-safe {@link Paniolo}
     */
    public Paniolo build() {
      Path configured = rules;
      // The packaged rules are only extracted when the first validation needs them.
      Path configuredFacturX = facturXRules;
      Path configuredFrance  = franceRules;
      Path configuredOrderX  = orderXRules;
      return new Paniolo(configured == null ?
                         XrechnungRules::bundle :
                         () -> configured,
                         configuredFacturX == null ?
                         FacturXRules::bundle :
                         () -> configuredFacturX,
                         configuredFrance == null ?
                         FacturXRules::france :
                         () -> configuredFrance,
                         configuredOrderX == null ?
                         FacturXRules::orderX :
                         () -> configuredOrderX,
                         despatchAdviceRules,
                         calculationCheck,
                         ruleStatistics,
                         unknownDocuments,
                         configured == null ?
                         "KoSIT XRechnung " + XrechnungRules.VERSION + " (" + XrechnungRules.RELEASE + ")" :
                         "XRechnung rules from " + configured,
                         configuredFacturX == null ?
                         "Factur-X " + FacturXRules.VERSION + " / ZUGFeRD " + FacturXRules.ZUGFERD + " (" + FacturXRules.RELEASE + ")" :
                         "Factur-X rules from " + configuredFacturX,
                         configuredOrderX == null ?
                         "Order-X 1.0 (packaged)" :
                         "Order-X rules from " + configuredOrderX);
    }
  }
}

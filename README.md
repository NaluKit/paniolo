<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="docs/branding/paniolo-banner-dark.svg">
    <img src="docs/branding/paniolo-banner.svg" alt="Paniolo" width="480">
  </picture>
</p>

# Paniolo

[![License](https://img.shields.io/:license-apache-blue.svg)](http://www.apache.org/licenses/LICENSE-2.0.html)
[![Join the chat at https://gitter.im/Nalukit42/Lobby](https://badges.gitter.im/Nalukit42/Lobby.svg)](https://gitter.im/Nalukit42/Lobby?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.nalukit/paniolo.svg?colorB=44cc11)](https://central.sonatype.com/artifact/io.github.nalukit/paniolo)
[![Build & Deploy](https://github.com/NaluKit/paniolo/actions/workflows/build.yaml/badge.svg?branch=main)](https://github.com/NaluKit/paniolo/actions/workflows/build.yaml)

Paniolo is a Java 21 library for electronic invoices and related business documents in Germany
and the EU. It **creates, reads, converts, and validates** XRechnung and EN 16931 invoices in UBL
and CII, Factur-X / ZUGFeRD hybrid PDFs, Order-X orders, and despatch advices (delivery notes).
All of it goes through one immutable Java model and one thread-safe entry point, the `Paniolo`
facade.

```java
Paniolo paniolo = Paniolo.create();

String xml = paniolo.toXml(invoice, InvoiceSyntax.UBL);       // model -> XRechnung UBL
ValidationResult result = paniolo.validate(xml);              // official KoSIT rules, packaged
Invoice received = paniolo.read(Files.readAllBytes(path));    // UBL, CII, ZUGFeRD 1.0 or a Factur-X PDF
```

- [What Paniolo can do](#what-paniolo-can-do)
- [Scope](#scope)
- [Installation](#installation)
- [Versioned API reference and reproducible quickstart](docs/api/1.0.0-rc01.md)
- [Using Paniolo](#using-paniolo): [send an invoice](#send-an-xrechnung),
  [receive an invoice](#receive-and-process-an-invoice), [Factur-X](#create-a-factur-x--zugferd-pdf),
  [ZUGFeRD 1.0](#migrate-zugferd-10), [Order-X](#orders-order-x),
  [delivery notes](#despatch-advices-delivery-notes), [JSON](#store-documents-as-json),
  [command line](#command-line), [configuration](#configuration), [method reference](#method-reference)
- Details: [generating](#generating-an-invoice), [reading](#reading-an-invoice),
  [validating](#validating-xml), [Factur-X / ZUGFeRD](#factur-x--zugferd),
  [validating Factur-X](#validating-factur-x--zugferd),
  [localized texts](#localized-enum-texts), [thread safety](#thread-safety-and-multi-user-use),
  [logging](#logging), [building](#building-and-testing), [releases and security](#releases-and-security),
  [license](#license), [the name](#the-name)

## The name

*Paniolo* is the Hawaiian word for a cowboy. In the 1830s, King Kamehameha III brought vaqueros
from Mexico, then Spanish-speaking, to Hawaii to tame the wild cattle; the islanders called them
*Españoles*, which became *paniolo* in Hawaiian. Like the other NaluKit projects (*nalu* is
Hawaiian for "wave"), Paniolo carries a Hawaiian name, and the rider with the lasso in the logo is
a paniolo rounding up invoices.

## What Paniolo can do

| Document                                      | Formats                                                                   |        Write         | Read | Validate                                                     | Embed in PDF/A-3 |
|-----------------------------------------------|---------------------------------------------------------------------------|:--------------------:|:----:|--------------------------------------------------------------|:----------------:|
| Invoice and credit note (EN 16931, XRechnung) | UBL 2.1 Invoice / CreditNote, UN/CEFACT CII D16B                          |         yes          | yes  | XSD, EN 16931, XRechnung (KoSIT, packaged)                   |  yes (Factur-X)  |
| XRechnung extension                           | UBL Invoice (sub invoice lines, third party payments)                     |         yes          | yes  | KoSIT extension scenario                                     |        -         |
| Factur-X / ZUGFeRD 2.x hybrid invoice         | PDF/A-3 with CII; MINIMUM, BASIC WL, BASIC, EN 16931, EXTENDED, XRECHNUNG |         yes          | yes  | container, PDF/A¹, XML by profile (FeRD, packaged)           |       yes        |
| French invoice EXTENDED-CTC-FR (XP Z12-012)   | CII                                                                       |         yes          | yes  | EXTENDED XSD, EXTENDED-CTC-FR and BR-FR (FNFE-MPE, packaged) |        -         |
| ZUGFeRD 1.0 invoice                           | XML or PDF                                                                | migrate to ZUGFeRD 2 | yes  | ZUGFeRD 1.0 XSD and Schematron, outdated warning             |    migration     |
| Order, order change, order response           | Order-X 1.0 (BASIC, COMFORT, EXTENDED)                                    |         yes          | yes  | Order-X rules (packaged)                                     |       yes        |
| Despatch advice (delivery note)               | UBL 2.1 DespatchAdvice (Peppol BIS 3, 1Lieferschein), CIDA                |         yes          | yes  | UBL XSD, Peppol rules² (UBL)                                 |    yes (CIDA)    |
| All of the above models                       | JSON (storage format)                                                     |         yes          | yes  | model checks                                                 |        -         |

¹ with the optional module `paniolo-pdfa` (veraPDF). ² The official Peppol Despatch Advice rules
are not distributed with Paniolo (no published license); you download and configure them once.
Without them Paniolo validates as far as the packaged rules allow and says so in the result. The official Factur-X / ZUGFeRD rules (FeRD, Apache 2.0), the French EXTENDED-CTC-FR and
BR-FR rules (FNFE-MPE, Apache 2.0), and the Order-X rules (FNFE-MPE and FeRD, Apache 2.0) are
packaged in `paniolo-facturx-rules`.

Beyond the formats, Paniolo:

- **calculates** line amounts, allowances and charges, VAT breakdown, and totals exactly with
  `BigDecimal`, so the written document is consistent;
- **detects** the format of received documents (UBL or CII, XML or PDF, profile by BT-24), so one
  call handles everything you receive;
- **reports findings** with the original rule ID (e.g. `BR-DE-15`), severity, XPath, and stage, and
  as an XML report with summary, findings by stage, and an XSD for archiving;
- **reads leniently** on request: documents from other software whose amounts the model does not
  reproduce are read with a warning per deviation instead of being rejected;
- **checks more than the rules** on request: the optional [calculation check](#optional-calculation-check)
  recalculates the amounts, [rule statistics](#optional-rule-statistics) document how many rules
  ran for an audit, and a compatibility mode reports [unknown documents](#receive-and-process-an-invoice)
  as invalid instead of throwing;
- **migrates from Mustang**: the optional module [`paniolo-mustang`](paniolo-mustang/README.md) maps
  Mustang invoice objects to the model and names every value it cannot carry over, and the
  [migration guide](docs/migration-mustang.md) describes the explicit mapping;
- **handles PDF attachments** (time sheets, delivery notes) and tells which software produced a PDF;
- **renders** the visual PDF/A-3b pages of invoices, orders, and delivery notes with
  [`paniolo-pdf-layout`](paniolo-pdf-layout/README.md), so `toFacturX(invoice, profile)`,
  `toOrderXPdf(order)`, and `toDespatchAdvicePdf(advice)` create complete hybrid PDFs without a
  PDF of your own;
- **shows** invoices, orders, and despatch advices in the browser with the web component
  `<paniolo-viewer>` of [`paniolo-visualizer`](paniolo-visualizer/README.md), which takes the
  document as bytes (XML or PDF) and can be embedded in any web application;
- **localizes** all code lists (units, countries, payment means, ...) in English and German;
- has **no mutable global state** and no logging dependency, and needs no network access at runtime.

## Scope

Supported:

- EN 16931 at document, party, and line level in UBL 2.1 and CII D16B, writing and reading, with
  validation against EN 16931 and XRechnung (KoSIT).
- Factur-X / ZUGFeRD: writing the profiles BASIC, EN 16931, EXTENDED, and XRECHNUNG as PDF/A-3
  with further attachments, and MINIMUM and BASIC WL from invoices without lines with stated
  totals; reading and validating all six profiles; container and PDF/A checks.
- Profile EXTENDED: further parties, structured payment terms with their own payee and further
  payment terms (also instalments with description and due date only), logistics service charges,
  advance payments, financial adjustments, document and party details, sequence numbers and base
  quantities of document allowances and charges, the split of the VAT taxable amounts, sub-lines,
  line VAT and gross amounts, several allowances and charges on the gross price, product details,
  and line references.
- French profile EXTENDED-CTC-FR of the e-invoicing reform (XP Z12-012): writing with the EXTENDED
  model, rejecting the EXTENDED content the profile does not allow, reading, and validating against
  the EXTENDED schema, the EXTENDED-CTC-FR Schematron, and, between French parties, the French
  rules BR-FR (flux 2), including multi-vendor invoices with sub-invoices; see
  [EXTENDED-CTC-FR](paniolo-facturx/README.md#profile-extended-ctc-fr).
- ZUGFeRD 1.0: reading, validating against the ZUGFeRD 1.0 schema and Schematron, and migrating
  XML and PDF to Factur-X / ZUGFeRD 2.
- Older XRechnung versions: documents stating XRechnung 1.2, 2.0, 2.1, 2.2, or 2.3 are validated
  against the last KoSIT release of that version, packaged in `paniolo-xrechnung-rules`.
- Order-X 1.0: orders, order changes, and order responses in the profiles BASIC and COMFORT, and
  EXTENDED with the COMFORT content, as XML and PDF/A-3, with strict reading and validation
  against the official Order-X rules of FNFE-MPE and FeRD, packaged in `paniolo-facturx-rules`.
- Despatch advices after Peppol BIS Despatch Advice 3 as UBL 2.1 DespatchAdvice, with strict
  reading, UBL schema validation, and the Peppol rules if you provide them; and as CIDA delivery
  notes (`cida.xml`, as the Mustang project writes them) in XML and PDF/A-3. With the identifiers
  of 1Lieferschein (`CustomizationID` `1Lieferschein`, `ProfileID` `ubl-xml-only`, as the Mustang
  project writes them) the same UBL is written and read without the Peppol restrictions, e.g. with
  the actual delivery date, and validated against the UBL schema only.
- XRechnung extension in UBL Invoice: nested sub invoice lines, third party payments, and XML
  attachments, validated with the KoSIT extension scenario.
- Producing software of received PDFs, recognized from the signatures the Mustang project uses.
- Rendering of the visual PDF/A-3b document of invoices, orders, and delivery notes (DIN 5008,
  German, English, and French, logo, own fonts) with `paniolo-pdf-layout`.
- JSON storage of invoices, orders, and despatch advices with the checks of the model (`paniolo-json`).
- Migration of Mustang integrations: `paniolo-mustang` maps `org.mustangproject.Invoice` (any
  `IExportableTransaction`) to the model; built for Mustang 2.26.0, newer Mustang versions are
  checked at runtime and in a test matrix.

Not included:

- Own designs of the rendered documents beyond logo, fonts, colors, and footer, and converting
  arbitrary PDFs to PDF/A: for these you provide the visual PDF/A document, Paniolo adds the XML.
  For viewing and printing in the browser there is [`paniolo-visualizer`](paniolo-visualizer/README.md).
- Transmission (Peppol access point, e-mail, portals such as ZRE or OZG-RE) and
  recipient-specific requirements beyond the official rules.
- The official Peppol Despatch Advice rules, which have no published license.
- Java versions below 21. Paniolo requires Java 21 at build and run time (it builds on records and
  other current language features); a backport is not planned. Applications that are fixed to Java 11 or 17 can
  run Paniolo in a separate service on Java 21, e.g. behind the [command line](#command-line) or a
  small HTTP endpoint, or upgrade their runtime. See [requirements](#requirements).
- Writing new ZUGFeRD 1.0 documents. ZUGFeRD 1.0 is read, validated, and migrated to
  ZUGFeRD 2 / Factur-X (see [migrate ZUGFeRD 1.0](#migrate-zugferd-10)), but not written, because it
  does not meet EN 16931 and is no longer accepted as an electronic invoice.
- VAT included in prices, descriptions of line periods, and sequence numbers and base quantities
  of line allowances and charges in EXTENDED, which the Factur-X rules do not allow.
- Sub invoice lines and third party payments of the XRechnung extension in CII and in UBL
  CreditNote (KoSIT defines them for UBL Invoice only), and payment penalty terms in the XRechnung text form (`#VERZUG#`).
- Exact reading of deviating amounts: by default, line net amounts must follow quantity x net
  price plus line charges minus line allowances, up to the rounding of the net price (as Peppol
  rule PEPPOL-EN16931-R120 requires). `ReadMode.LENIENT` reads such documents, for example a
  line amount that is a gross price including insurance tax, by recalculating the net price,
  and reports the deviation; see [reading an invoice](#reading-an-invoice).
- XRechnung 1.0 and 1.1, for which no release is packaged; they are validated against the current
  rules. Another KoSIT release can be configured.
- EN 16931:2026 and XRechnung 4.0, whose official validation artefacts are not published yet
  (KoSIT expects them in spring 2027). A document stating `urn:cen.eu:en16931:2026` in its
  specification identifier (BT-24) is not checked against the rules of EN 16931:2017; validation
  reports the error `EN16931-2026-NOT-SUPPORTED`. Reading it gives the warning
  `READ-SPECIFICATION-VERSION`, because terms new in 2026 are not read. Writing it is rejected.
- The Order-X EXTENDED details and a few COMFORT details, some despatch advice details (hazardous
  items, package details, carrier; in CIDA also shipment details and outstanding quantities),
  validation of CIDA (no schema is published), and other documents such as Peppol orders.

Notes on the formats:

- A buyer's national tax identifier is rejected during export because it has no EN 16931
  mapping; sellers can use `taxId`.
- UBL CreditNote has no document-level `DueDate` element. A due date specified in the model is
  therefore included as text in the payment terms; CII and UBL Invoice represent it as a date.
- For tax category `O`, the tax rate is omitted from lines, allowances, and charges; the tax
  breakdown uses `0` for XRechnung.
- Paniolo writes CII D16B. Factur-X is based on CII D22B; some EXTENDED content (several preceding
  invoices, the invoice reference of advance payments) is valid only against the Factur-X schemas.

Technical references: [XRechnung](https://xeinkauf.de/xrechnung/),
[EN 16931 validation artifacts](https://github.com/ConnectingEurope/eInvoicing-EN16931),
[KoSIT validator configuration](https://github.com/itplr-kosit/validator-configuration-xrechnung),
[Factur-X / ZUGFeRD](https://fnfe-mpe.org/factur-x/factur-x_en/), and
[Peppol BIS Despatch Advice](https://docs.peppol.eu/poacc/upgrade-3/profiles/30-despatchadvice/).

## Installation

For a complete, runnable example with a pinned source commit, exact Maven coordinates, and the
expected output, see the [versioned API reference and reproducible quickstart](docs/api/1.0.0-rc01.md)
(release `1.0.0-rc01` at `7b7f6401ef2d`). It also documents the facade, model, optional
modules, validation results, and how to generate the full JavaDoc for that baseline.

The reference above describes release `1.0.0-rc01` and uses its Maven Central artifacts. Each release
gets its own reference `docs/api/<version>.md` whose quickstart
uses the published Maven Central artifacts of exactly that version (see
[releases and security](#releases-and-security)).

### Requirements

Paniolo needs **Java 21** or newer, at build and at run time; there is no build for older Java
versions. If an application cannot leave Java 11 or 17, run Paniolo in its own Java 21 process (the
[CLI](#command-line) or a small service around the facade) and exchange XML, PDF, or JSON with it.

The Maven group ID is `io.github.nalukit`. The current release is `1.0.0-rc01` on Maven Central. The
development version `HEAD-SNAPSHOT` is built with `mvn install` from the repository root (see
[building](#building-and-testing)).
Most applications need only `paniolo`:

```xml
<dependency>
  <groupId>io.github.nalukit</groupId>
  <artifactId>paniolo</artifactId>
  <version>1.0.0-rc01</version>
</dependency>
```

It brings the model, UBL and CII, validation, and the official XRechnung and Factur-X rules. PDF
support, PDF/A checks, rendering, and JSON are optional modules, so applications without them keep
a small class path:

| You want to ...                                                               | Add                                                          |
|-------------------------------------------------------------------------------|--------------------------------------------------------------|
| write, read, and validate XRechnung / EN 16931 XML, Order-X, despatch advices | `paniolo`                                                    |
| create, read, or validate PDFs (Factur-X / ZUGFeRD, Order-X, CIDA)            | `paniolo-facturx` (includes `paniolo`)                       |
| check the PDF/A conformance of these PDFs as well                             | `paniolo-facturx` and `paniolo-pdfa`                         |
| create Factur-X, Order-X, or CIDA PDFs without a PDF of your own              | `paniolo-pdf-layout` (includes `paniolo-facturx`)            |
| store the model as JSON                                                       | `paniolo-json`                                               |
| show documents in the browser, or write them as standalone HTML               | `paniolo-visualizer` (web component, no dependencies)        |
| validate, read, write, and convert documents from shell scripts               | the executable jar of [`paniolo-cli`](paniolo-cli/README.md) (GitHub Packages, not Maven Central) |
| migrate a Mustang integration step by step                                    | `paniolo-mustang` (with your Mustang library)                |
| use only a part, e.g. only the UBL writer, without the facade                 | the individual module, e.g. `paniolo-xml-ubl`                |

The optional PDF modules are found at runtime through `java.util.ServiceLoader`; there is nothing
to configure. A PDF method called without `paniolo-facturx` raises an `IllegalStateException` that
names the missing dependency.

| Maven artifact                                                 | Purpose                                                                                                                                                                                                                                                     |
|----------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [`paniolo`](paniolo/README.md)                                 | **Entry point for applications**: `Paniolo` facade; includes the model, XML, validation, and rules modules                                                                                                                                                  |
| [`paniolo-shared-model`](paniolo-shared-model/README.md)       | Immutable model of invoices, orders, and despatch advices, builders, code lists, and total calculations; no runtime dependencies                                                                                                                            |
| [`paniolo-xml`](paniolo-xml/README.md)                         | Shared writer and reader interfaces, read modes, and XML processing                                                                                                                                                                                         |
| [`paniolo-xml-ubl`](paniolo-xml-ubl/README.md)                 | UBL Invoice and CreditNote writer and reader, XRechnung extension; UBL DespatchAdvice writer and reader                                                                                                                                                     |
| [`paniolo-xml-cii`](paniolo-xml-cii/README.md)                 | CII CrossIndustryInvoice writer and reader incl. all Factur-X profiles; ZUGFeRD 1.0 reader; Order-X and CIDA writer and reader                                                                                                                              |
| [`paniolo-validation`](paniolo-validation/README.md)           | XSD and Schematron XSLT validation with Saxon-HE; structured validation results and XML report                                                                                                                                                              |
| [`paniolo-xrechnung-rules`](paniolo-xrechnung-rules/README.md) | Official KoSIT XRechnung rules (XSD, Schematron XSLT), pinned and checksum-verified                                                                                                                                                                         |
| [`paniolo-facturx-rules`](paniolo-facturx-rules/README.md)     | Official Factur-X / ZUGFeRD rules of FeRD for all five profiles, ZUGFeRD 1.0, the French EXTENDED-CTC-FR and BR-FR rules, and the Order-X rules (XSD, Schematron XSLT), pinned and checksum-verified                                                      |
| [`paniolo-facturx`](paniolo-facturx/README.md)                 | Optional PDF support: embeds and extracts invoices, orders, delivery notes, and attachments in PDF/A-3, container checks, producer detection, ZUGFeRD 1.0 migration (Apache PDFBox)                                                                         |
| [`paniolo-pdfa`](paniolo-pdfa/README.md)                       | Optional PDF/A conformance checks for all PDFs validated by the facade (veraPDF)                                                                                                                                                                            |
| [`paniolo-json`](paniolo-json/README.md)                       | Optional JSON storage of invoices, orders, and despatch advices (Jackson)                                                                                                                                                                                   |
| [`paniolo-pdf-layout`](paniolo-pdf-layout/README.md)           | Optional rendering of the visual PDF/A-3b pages of invoices, orders, and delivery notes (DIN 5008, German, English, and French, logo, own fonts) for `toPdf`, `toFacturX(invoice, profile)`, `toOrderXPdf(order)`, and `toDespatchAdvicePdf(advice)` (Apache PDFBox) |
| [`paniolo-visualizer`](paniolo-visualizer/README.md)           | Optional browser visualizer: web component `<paniolo-viewer>` that renders UBL, CII, Factur-X / ZUGFeRD PDFs, ZUGFeRD 1.0, Order-X, despatch advices, and validation reports from bytes; standalone HTML from Java                                          |
| [`paniolo-cli`](paniolo-cli/README.md)                         | Command line interface for scripts: validation, reading, conversion, inventory, and visualization; executable jar with PDF, JSON, and visualization support (picocli)                                                                                                                                               |
| [`paniolo-mustang`](paniolo-mustang/README.md)                 | Optional migration adapter: maps a Mustang invoice (`IExportableTransaction`) to the Paniolo model and reports every value it cannot carry over; built for Mustang 2.26.0, newer versions checked at runtime and in a test matrix                          |

## Using Paniolo

`Paniolo` is the single entry point. Create one instance at application start and share it:
it is immutable and thread-safe, and it compiles each set of rules once, on the first validation
that needs it, and reuses it afterwards. `warmUp()` compiles the XRechnung
rules for UBL and CII at start-up instead, so the first request does not wait for them.

```java
import io.github.nalukit.paniolo.Paniolo;

private static final Paniolo PANIOLO = Paniolo.create()    // or a Spring bean, CDI producer, ...
                                              .warmUp();   // optional: compile the rules now
```

Every public method is documented in detail in the Javadoc of
[`Paniolo`](paniolo/src/main/java/io/github/nalukit/paniolo/Paniolo.java). The conventions are the
same everywhere: `null` arguments raise a `NullPointerException`, content that cannot be processed
raises an `IllegalArgumentException` with a message saying why, and business rule violations are
never exceptions but findings in a `ValidationResult`.

### Send an XRechnung

Build the invoice with the model (see [generating an invoice](#generating-an-invoice) for a complete
example), check it, and write it:

```java
Invoice invoice = Invoice.create()
                         .number("R-2026-001")
                         .issueDate(LocalDate.now())
                         .buyerReference("04011000-12345-03")      // Leitweg-ID for public buyers
                         .seller(seller)
                         .buyer(buyer)
                         .paymentInstruction(payment)
                         .addLine(line)
                         .build();                                 // totals are calculated here

ValidationResult result = PANIOLO.validate(invoice, InvoiceSyntax.UBL);
if (result.isValid()) {
  byte[] xml = PANIOLO.toBytes(invoice, InvoiceSyntax.UBL);     // or InvoiceSyntax.CII
  send(xml);
} else {
  result.messages().forEach(m -> log.error("{} {}: {}", m.severity(), m.ruleId(), m.message()));
}
```

The model's defaults are EUR, invoice type `380`, and the XRechnung 3 process identifiers.
Validation checks whether the completed invoice satisfies the XRechnung rules. Both syntaxes are
accepted for XRechnung; UBL is common for Peppol and public authorities, CII is the syntax of
Factur-X / ZUGFeRD.

### Receive and process an invoice

One call validates whatever you receive, and one reads it: UBL, CII, ZUGFeRD 1.0, or a hybrid PDF.

```java
byte[] received = Files.readAllBytes(path);

ValidationResult result = PANIOLO.validate(received);   // rules chosen from format and BT-24
archive(result.toXml());                                // machine-readable report

if (result.isValid()) {
  Invoice invoice = PANIOLO.read(received);
  book(invoice.number(), invoice.totals().payableAmount());
}
```

- **Strict by default.** `read` rejects differences in the checked line amounts, VAT total,
  or amount due. `read(received, ReadMode.LENIENT)` accepts the documented deviations and lists
  them as `ReadWarning`s; `read(received, ReadMode.STRICT)` returns the warnings that both modes
  give, e.g. for an identifier the model does not keep. Both modes can reject valid documents whose structure or values the
  model cannot represent; validation success does not guarantee a successful read. See
  [reading an invoice](#reading-an-invoice) and the
  [public invoice corpus](test-paniolo-invoice-corpus/README.md).
- **PDFs.** With `paniolo-facturx`, `validate` checks container, PDF/A (with `paniolo-pdfa`), and
  the embedded XML; `extractXml(pdf)` returns the XML, `attachments(pdf)` further files, and
  `producer(pdf)` the producing software.
- **Routing.** `Paniolo.isPdf(bytes)` and `detect(xml)` (`UBL_INVOICE`, `UBL_CREDIT_NOTE`, `CII`)
  tell what you received without reading it.
- **Every input as a finding.** `validate(bytes, ValidationProfile.Syntax.UBL_INVOICE)` reports even
  malformed XML as a `FATAL` finding instead of an exception, e.g. for upload endpoints.
- **Unknown documents.** By default, the detecting `validate` methods throw an
  `IllegalArgumentException` for a document whose type they cannot detect (not well-formed before
  the root element, no or an unsupported root element, also as XML embedded in a PDF), so "not an
  invoice" stays distinguishable from "invalid invoice". The compatibility mode
  `Paniolo.builder().unknownDocuments(UnknownDocumentHandling.REPORT_INVALID)` behaves like Mustang
  and returns an invalid result instead, with the `FATAL` finding `XML-PARSE` (with position) or
  `PANIOLO-UNKNOWN-DOCUMENT` (with the root element), profile name `Unknown document`. Documents with
  a supported root element are validated the same in both modes.

### Create a Factur-X / ZUGFeRD PDF

There are two ways to get the visual part. Either create the PDF/A document with your own
reporting tool, and Paniolo embeds the CII XML and writes the Factur-X metadata (needs
`paniolo-facturx`), or let [`paniolo-pdf-layout`](paniolo-pdf-layout/README.md) render the page
from the invoice:

```java
Invoice invoice = Invoice.create()
                         .processControl(ProcessControl.en16931())   // BT-24 must match the profile
                         /* ... */
                         .build();

byte[] facturX = PANIOLO.toFacturX(invoice,
                                   pdfA,                             // PDF/A-1, -2 or -3 showing the invoice
                                   FacturXProfile.EN16931,
                                   List.of(PdfAttachment.of("timesheet.pdf",
                                                            timesheet,
                                                            "application/pdf")));
byte[] rendered = PANIOLO.toFacturX(invoice,
                                    FacturXProfile.EN16931);         // page rendered by paniolo-pdf-layout
ValidationResult result = PANIOLO.validate(facturX);
```

Choose the profile your recipient expects and the matching `ProcessControl` factory:
`facturXMinimum()`, `facturXBasicWl()`, `facturXBasic()`, `en16931()`, `facturXExtended()`, or
`xrechnung()`. MINIMUM and BASIC WL have no lines and are only booking aids in Germany. See
[Factur-X / ZUGFeRD](#factur-x--zugferd) and the [module README](paniolo-facturx/README.md).

### Migrate ZUGFeRD 1.0

ZUGFeRD 1.0 does not meet EN 16931 and is no longer accepted as an electronic invoice. Paniolo
validates it against the ZUGFeRD 1.0 schema and Schematron (with the warning `FX-ZUGFERD-1`), reads
it like any invoice, and migrates XML or PDF to the
corresponding ZUGFeRD 2 / Factur-X profile (BASIC → BASIC, COMFORT → EN 16931, EXTENDED → EXTENDED):

```java
byte[] migrated = PANIOLO.migrateZugferd1(oldPdfOrXml);                          // same format out
byte[] comfort = PANIOLO.migrateZugferd1(oldPdfOrXml, FacturXProfile.EN16931);  // explicit target
```

ZUGFeRD 1.0 is a read and migration format only: Paniolo does not write new ZUGFeRD 1.0 documents.
The migration keeps the invoice data that ZUGFeRD 2 / Factur-X can express; in a PDF the invoice XML
is replaced, the XMP metadata rewritten, and all other attachments are kept.

### Migrate from Mustang

Applications that create invoices with Mustang through `IExportableTransaction` map their objects
to the Paniolo model instead. The [migration guide](docs/migration-mustang.md) lists every
used Mustang getter as mapped, lost, or needing a decision, with examples for invoice data,
payments, attachments, allowances, and EXTENDED fields; its tested sample integration is in
`paniolo/src/test/java/io/github/nalukit/paniolo/migration`. Paniolo has no Mustang dependency.

To keep the Mustang objects for now, the optional module [`paniolo-mustang`](paniolo-mustang/README.md)
maps them directly: `MustangInvoiceAdapter.create().map(mustangInvoice)` returns the Paniolo
invoice and every value it could not carry over; `toInvoice(...)` refuses invoices with such
values. It is tested against the real classes of Mustang 2.26.0 and Mustang's own export.

### Orders (Order-X)

Orders, order changes, and order responses have their own model (`Order`, `OrderLine`) and are
written as Order-X XML or PDF:

```java
Order order = Order.create()
                   .number("B-2026-17")
                   .issueDate(LocalDate.now())
                   .typeCode(OrderTypeCode.ORDER)        // ORDER_CHANGE, ORDER_RESPONSE
                   .profile(OrderXProfile.COMFORT)
                   .seller(seller)
                   .buyer(buyer)
                   .addLine(orderLine)
                   .build();

String xml = PANIOLO.toOrderX(order);
byte[] pdf = PANIOLO.toOrderXPdf(order, pdfA);          // needs paniolo-facturx
byte[] rendered = PANIOLO.toOrderXPdf(order);           // page rendered by paniolo-pdf-layout
Order read = PANIOLO.readOrder(pdf);                    // XML or PDF
ValidationResult result = PANIOLO.validateOrder(pdf);   // official Order-X rules, packaged
```

See [Order-X](paniolo-facturx/README.md#order-x).

### Despatch advices (delivery notes)

A `DespatchAdvice` is written as UBL DespatchAdvice for Peppol, or as CIDA XML and PDF delivery note:

```java
String ubl = PANIOLO.toDespatchAdvice(advice);                 // Peppol BIS Despatch Advice 3
String one = PANIOLO.toDespatchAdvice(advice1Lieferschein);    // built with .oneLieferschein()
String cida = PANIOLO.toCiiDespatchAdvice(advice);             // CIDA
byte[] pdf = PANIOLO.toDespatchAdvicePdf(advice, pdfA);        // cida.xml in PDF/A-3
byte[] rendered = PANIOLO.toDespatchAdvicePdf(advice);         // delivery note rendered by paniolo-pdf-layout
DespatchAdvice read = PANIOLO.readDespatchAdvice(received);    // UBL, CIDA, or PDF
ValidationResult result = PANIOLO.validateDespatchAdvice(received);
```

See [despatch advice](paniolo-xml-ubl/README.md#despatch-advice) and
[delivery note (CIDA)](paniolo-facturx/README.md#delivery-note-cida).

### Store documents as JSON

`paniolo-json` stores invoices, orders, and despatch advices, e.g. as drafts or in message queues;
loading runs the same checks as the builders:

```java
PanioloJson json = PanioloJson.create();
String text = json.toJson(invoice);
Invoice loaded = json.read(text, Invoice.class);
```

JSON is a storage format, not an exchange format; see the [module README](paniolo-json/README.md).

### Command line

`paniolo-cli` offers the facade methods as commands for shell scripts; the exit code tells whether
the documents are valid:

```sh
java -jar paniolo-cli/target/paniolo-cli-1.0.0-rc01-all.jar validate invoice.xml
paniolo to-xml invoice.json --syntax CII -o invoice.xml
paniolo to-facturx invoice.json --pdf layout.pdf --profile EN16931 -o factur-x.pdf
paniolo validate -f json inbox/*.pdf | jq -r 'select(.valid | not) | .file'
paniolo validate inbox -r --exclude 'archive/**' --summary          # directories, one result per file
paniolo validate corpus/negative -r --expect invalid --summary      # regression test of a corpus
paniolo inventory archive -r --summary-only                         # formats, profiles, producers
```

The global options `--calculation-check`, `--rule-statistics`, and `--unknown-documents` switch on
the optional behavior of the [configuration](#configuration). See the
[module README](paniolo-cli/README.md) for all commands, report formats, and exit codes.

### Configuration

`Paniolo.create()` is enough for XRechnung, EN 16931, Factur-X, and Order-X. `Paniolo.builder()`
adds rules that Paniolo may not distribute, replaces the packaged ones, or switches on optional
behavior:

```java
Paniolo paniolo = Paniolo.builder()
                         .rules(Path.of("/opt/kosit/xrechnung-3.0.2"))
                         .facturXRules(Path.of("/opt/factur-x/1.09.2"))
                         .orderXRules(Path.of("/opt/order-x/1.0"))
                         .despatchAdviceRules(Path.of("/opt/peppol/despatch-advice.xslt"))
                         .calculationCheck(true)
                         .unknownDocuments(UnknownDocumentHandling.REPORT_INVALID)
                         .build();
```

| Builder method                   | Effect                                                                                                   | Without it                                                                          |
|----------------------------------|----------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------|
| `rules(bundle)`                  | An extracted KoSIT validator configuration instead of the packaged release, e.g. during a version change | The packaged release from `paniolo-xrechnung-rules`                                 |
| `facturXRules(packageDirectory)` | An extracted Factur-X / ZUGFeRD release instead of the packaged one, e.g. during a version change        | The packaged release from `paniolo-facturx-rules` (ZUGFeRD 2.5.2 / Factur-X 1.09.2) |
| `extendedCtcFrRules(directory)`  | Another release of the French EXTENDED-CTC-FR and BR-FR rules (FNFE-MPE France_RFE)                      | The packaged release from `paniolo-facturx-rules` (France_RFE 1.4.0.04)             |
| `orderXRules(packageDirectory)`  | Another Order-X release in `validateOrder`; the Schematron must be compiled to SVRL                      | The packaged Order-X 1.0 rules from `paniolo-facturx-rules`                         |
| `despatchAdviceRules(xsltFile)`  | `validateDespatchAdvice` also runs the compiled Peppol BIS Despatch Advice Schematron                    | UBL schema only, information `PEPPOL-RULES-NOT-CHECKED`                             |
| `calculationCheck(true)`         | Invoice validations also recalculate the amounts (stage `CALCULATION`, warnings only)                    | Official rules only                                                                 |
| `ruleStatistics(true)`           | Results carry `statistics()`: fired rules per Schematron stylesheet, PDF/A checks; slower, for audits    | `statistics()` is null                                                              |
| `unknownDocuments(REPORT_INVALID)` | Undetectable documents give an invalid result (`XML-PARSE`, `PANIOLO-UNKNOWN-DOCUMENT`), as in Mustang | `IllegalArgumentException` (`THROW`)                                                |

The paths of `facturXRules`, `extendedCtcFrRules`, `orderXRules`, and `despatchAdviceRules` are
checked when they are set, so a wrong path fails at start-up. The KoSIT bundle of `rules` is
loaded on the first validation; call `warmUp()` to find a wrong bundle at start-up as well.
Paniolo never downloads rules at runtime.

### Method reference

| Method                                                                                                                | Purpose                                                                                                   |
|-----------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------|
| `create()`, `builder()`                                                                                               | Instance with the packaged rules, or configured with further rules                                        |
| `warmUp()`                                                                                                            | Compile the XRechnung rules for UBL and CII now instead of on the first validation                        |
| `toXml(invoice, syntax)`, `toBytes(invoice, syntax)`                                                                  | Write UBL (Invoice or CreditNote by type code) or CII                                                     |
| `read(String / byte[] / InputStream)`                                                                                 | Read UBL Invoice, UBL CreditNote, CII, ZUGFeRD 1.0, or a Factur-X PDF; format detected                    |
| `read(xmlOrPdf, ReadMode.LENIENT)`                                                                                    | Read documents whose line amounts or totals the model does not reproduce; deviations as `ReadWarning`     |
| `importInvoice(xmlOrPdf, mode)`, `export(imported, variant, syntax)`                                                  | Import with original XML and stated amounts; export the original or the normalized document explicitly    |
| `validate(String / byte[] / InputStream)`                                                                             | Validate against XSD, EN 16931, and XRechnung, or Factur-X profile rules by BT-24; PDFs as Factur-X       |
| `validate(invoice, syntax)`                                                                                           | Write and validate in one step                                                                            |
| `validate(bytes, type)`                                                                                               | Validate with an explicit document type, e.g. to report malformed XML as a finding                        |
| `validate(bytes, facturXProfile)`                                                                                     | Validate the XML of a Factur-X profile with the official rules of that profile                            |
| `detect(xml)`, `Paniolo.isPdf(bytes)`                                                                                 | Return `UBL_INVOICE`, `UBL_CREDIT_NOTE`, or `CII`; tell whether the content is a PDF                      |
| `toFacturX(invoice, pdfA, profile[, attachments])`                                                                    | Create a Factur-X / ZUGFeRD PDF/A-3 from your own PDF/A (`paniolo-facturx`)                               |
| `toFacturX(invoice, profile[, layout, attachments])`                                                                  | Create a Factur-X / ZUGFeRD PDF/A-3 with a rendered page (`paniolo-pdf-layout`)                           |
| `embedXml(pdfA, xml, profile[, attachments])`                                                                         | Embed existing CII XML byte for byte into your PDF/A; returns the PDF and its validation                  |
| `toPdf(invoice / order / advice[, layout])`                                                                           | Render the visual PDF/A-3b document only, without XML (`paniolo-pdf-layout`)                              |
| `toReportPdf(result, fileName, document[, layout])`                                                                   | Render a validation report as PDF/A-3b for archives and departments (`paniolo-pdf-layout`)                |
| `extractXml(pdf)`, `attachments(pdf)`                                                                                 | Embedded invoice XML and further embedded files of a PDF                                                  |
| `producer(pdf)`                                                                                                       | Producing software of a PDF (`PdfProducer`), also reported as `FX-PRODUCER` by `validate(pdf)`            |
| `migrateZugferd1(xmlOrPdf[, profile])`                                                                                | Migrate ZUGFeRD 1.0 to Factur-X / ZUGFeRD 2 (XML or PDF)                                                  |
| `toOrderX(order)`, `toOrderXBytes(order)`, `toOrderXPdf(order, pdfA[, attachments])`                                  | Write Order-X orders, order changes, and order responses as XML or PDF/A-3                                |
| `toOrderXPdf(order[, layout, attachments])`                                                                           | Order-X PDF/A-3 with rendered pages (`paniolo-pdf-layout`)                                                |
| `readOrder(xmlOrPdf[, ignoreUnsupported])`, `validateOrder(xmlOrPdf)`                                                 | Read and validate Order-X; see [Order-X](paniolo-facturx/README.md#order-x)                               |
| `toDespatchAdvice(advice)`, `toDespatchAdviceBytes(advice)`                                                           | Despatch advice as UBL DespatchAdvice; see [despatch advice](paniolo-xml-ubl/README.md#despatch-advice)   |
| `toCiiDespatchAdvice(advice)`, `toCiiDespatchAdviceBytes(advice)`, `toDespatchAdvicePdf(advice, pdfA[, attachments])` | CIDA delivery notes as XML and PDF/A-3; see [delivery note](paniolo-facturx/README.md#delivery-note-cida) |
| `toDespatchAdvicePdf(advice[, layout, attachments])`                                                                  | CIDA delivery note PDF/A-3 with rendered pages (`paniolo-pdf-layout`)                                     |
| `readDespatchAdvice(xmlOrPdf[, ignoreUnsupported])`, `validateDespatchAdvice(xmlOrPdf)`                               | Read and validate UBL DespatchAdvice, CIDA XML, or PDF delivery notes                                     |

Documents in other formats raise `IllegalArgumentException`; the validation methods that detect
the format report them as invalid instead with `unknownDocuments(REPORT_INVALID)`. The sections
below describe the model and the individual features in detail.

## Generating an invoice

A complete XRechnung invoice with seller, buyer, payment, and one line:

```java
import io.github.nalukit.paniolo.model.Invoice;
import io.github.nalukit.paniolo.model.common.Address;
import io.github.nalukit.paniolo.model.common.Contact;
import io.github.nalukit.paniolo.model.common.CreditTransfer;
import io.github.nalukit.paniolo.model.common.DeliveryInformation;
import io.github.nalukit.paniolo.model.common.ElectronicAddress;
import io.github.nalukit.paniolo.model.common.InvoiceLine;
import io.github.nalukit.paniolo.model.common.Party;
import io.github.nalukit.paniolo.model.common.PaymentInstruction;
import io.github.nalukit.paniolo.model.common.TaxCategory;
import io.github.nalukit.paniolo.model.common.value.Country;
import io.github.nalukit.paniolo.model.common.value.PaymentMeansCode;
import io.github.nalukit.paniolo.config.InvoiceSyntax;

import java.math.BigDecimal;
import java.time.LocalDate;

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

var invoice = Invoice.create()
                     .number("R-2026-001")
                     .issueDate(LocalDate.of(2026, 10, 27))
                     .delivery(DeliveryInformation.create()
                                                  .actualDeliveryDate(LocalDate.of(2026, 10, 26))
                                                  .build())
                     .dueDate(LocalDate.of(2026, 11, 10))
                     .buyerReference("04011000-12345-03")
                     .seller(seller)
                     .buyer(buyer)
                     .paymentInstruction(PaymentInstruction.create()
                                                           .meansCode(PaymentMeansCode.CODE_58)
                                                           .addCreditTransfer(CreditTransfer.create()
                                                                                            .iban("DE89370400440532013000")
                                                                                            .accountName("Muster GmbH")
                                                                                            .bic("COBADEFFXXX")
                                                                                            .build())
                                                           .remittanceInformation("R-2026-001")
                                                           .build())
                     .addLine(InvoiceLine.create()
                                         .id("1")
                                         .name("Beratung")
                                         .quantity(new BigDecimal("2"))
                                         .unitCode("HUR")
                                         .netUnitPrice(new BigDecimal("100.00"))
                                         .tax(TaxCategory.standard(new BigDecimal("19")))
                                         .build())
                     .build();

String xml = paniolo.toXml(invoice, InvoiceSyntax.UBL);   // or new UblInvoiceWriter().toXml(invoice)
BigDecimal payable = invoice.totals().payableAmount();    // 238.00 EUR
```

The defaults are `CurrencyCode.EUR`, invoice type `380`, and the process information
for XRechnung 3. The selected validator checks mandatory business information.
All details in the example are fictitious.

- CII: `new CiiInvoiceWriter().toXml(invoice)` from the `io.github.nalukit.paniolo.xml.cii` package.
- Document type: `.typeCode(...)` accepts every code permitted by EN 16931 (`InvoiceTypeCode` contains
  exactly these 62 codes), e.g. `CODE_381` credit note or `CODE_384` corrective invoice. UBL writes the
  credit note codes (`isCreditNote()`: 381, 261, 396, ...) as `CreditNote`. XRechnung permits only
  326, 380, 381, 384, 389, 875, 876, and 877 (`isXrechnungAllowed()`); validation reports others.
- Allowances and charges:
  `.addAdjustment(DocumentAdjustment.createAllowance().amount(new BigDecimal("10")).reason("Rabatt").tax(tax).build())`.
  Use `DocumentAdjustment.createCharge()` for a charge.
- Prepayment: `.paidAmount(new BigDecimal("20"))`.
- Units: `.unitCode(UnitCode.HUR)` or `.unitCode("HUR")`. `UnitCode` contains the 2162 UN/ECE
  Recommendation 20/21 codes permitted by EN 16931, e.g. `H87` piece, `C62` one, `LS` lump sum,
  `XPK` package. Codes starting with a digit are named `CODE_10` and so on. Unknown codes are rejected.
- Prices per quantity: `InvoiceLine.create().priceBaseQuantity(new BigDecimal("100"))` supports
  prices per 100 items, for example. The default is `1`.
- Tax exemption: `TaxCategory.create().code(DutyTaxFeeCategory.E).rate(BigDecimal.ZERO)
  .exemptionReason("Steuerbefreit").exemptionReasonCode("VATEX-EU-132").build()`.
  The caller must select the appropriate exemption reason.
- Seller identifier BT-29: `Party.create().identifier("...")`.
- Notes with subject code: `.addNote(InvoiceNote.create().subjectCode(TextSubjectQualifier.AAI).text("...").build())`.
  Codes not covered by `TextSubjectQualifier` are passed as text, e.g. `.subjectCode("ADU")`; validation
  checks them against UNTDID 4451.
- Seller and buyer details: `Party.create()` also takes the trading name (BT-28 / BT-45), the identifier with
  its ISO 6523 scheme (`.identifier("4000001000005", "0088")`, BT-29 / BT-46), the legal registration identifier
  such as the commercial register number (`.legalRegistrationId("HRB 12345 B")`, BT-30 / BT-47), and for the
  seller additional legal information (`.additionalLegalInformation(...)`, BT-33). UBL writes the legal name as
  `RegistrationName` and only the trading name as `PartyName`.
- Cash discount (Skonto): `.addCashDiscount(CashDiscount.of(14, new BigDecimal("2")))` or with a base amount
  `new CashDiscount(7, new BigDecimal("3"), new BigDecimal("100.00"))`. The discounts are written into the payment
  terms (BT-20) in the XRechnung form `#SKONTO#TAGE=14#PROZENT=2.00#` (BR-DE-18) after the free text from
  `.paymentTerms(...)`, and split off again when reading.
- Line details: `InvoiceLine.create()` also takes the line note (BT-127), object identifier (BT-128),
  order line reference (BT-132), buyer accounting reference (BT-133), line period (BG-26), gross price and
  price discount (BT-148, BT-147), item description and identifiers (BT-154 to BT-157), classifications (BT-158), country of
  origin (BT-159), and attributes (BG-32). In the Factur-X profile EXTENDED, the parts of the price
  discount (several allowances and charges on the gross price) are kept in
  `LineExtension.priceAdjustments()`; see [profile EXTENDED](paniolo-facturx/README.md#profile-extended):

  ```java
  InvoiceLine.create().id("1").name("Schraube M6").quantity(new BigDecimal("2")).unitCode(UnitCode.H87)
      .grossPrice(new BigDecimal("120.00")).priceDiscount(new BigDecimal("20.00"))
      .netUnitPrice(new BigDecimal("100.00")).tax(tax)
      .addAdjustment(LineAdjustment.createAllowance().amount(new BigDecimal("10.00"))
          .reasonCode(AllowanceChargeIdentification.CODE_95).build())
      .sellerItemId("ART-6").standardItemId("4012345678901", "0160")
      .addClassification(ItemClassification.of("73181568", "HS"))
      .originCountry(Country.DE).addAttribute("Farbe", "silber")
      .build();                                   // net amount 2 x 100.00 - 10.00 = 190.00
  ```
- Line allowances and charges (BG-27, BG-28): `LineAdjustment.createAllowance()` / `createCharge()`; they are
  part of the line net amount (BT-131) and use the line's VAT category.
- Stated line net amount: `InvoiceLine.create()....netAmount(new BigDecimal("55.46"))` keeps BT-131 when the
  shown net price is rounded to cents (20 x 2.77 shown, 55.46 charged). The difference must be explainable
  by that rounding; otherwise the line is rejected.
- Corrections: allowance and charge amounts, their percentages, and the paid amount (BT-113) may be
  negative, as in the official ZUGFeRD samples.
- References BT-11 to BT-19: `.references(References.create().purchaseOrderReference("PO-1").contractReference("V-7").build())`.
- Preceding invoice (BG-3), e.g. for credit notes:
  `.addPrecedingInvoiceReference(PrecedingInvoiceReference.create().number("R-2026-000").issueDate(...).build())`.
  CII allows only one, except in the Factur-X profile EXTENDED.
- Factur-X profile EXTENDED: `.extended(ExtendedInformation.create()...build())` on the invoice,
  `InvoiceLine.create().extended(...)` and `Party.create().extended(...)` for further parties, payment
  terms, logistics service charges, sub-lines, product details, and more; see
  [profile EXTENDED](paniolo-facturx/README.md#profile-extended).
- XRechnung extension: `.processControl(ProcessControl.xrechnungExtension())` with sub invoice lines
  (`InvoiceLine.create()...addSubLine(...)`, BG-DEX-01), third party payments
  (`.addThirdPartyPayment(new ThirdPartyPayment(type, amount, description))`, BG-DEX-09), and XML
  attachments (`application/xml`); see [XRechnung extension](paniolo-xml-ubl/README.md#xrechnung-extension).
- Invoicing period (BG-14): `.invoicePeriod(InvoicePeriod.create().startDate(...).endDate(...).build())`.
- Delivery (BG-13, BG-15): `DeliveryInformation` with party name, location ID, actual delivery date, and address.
- Tax point date: `.taxPointDate(...)` (BT-7) or `.taxPointDateCode(DateTimeQualifier.CODE_35)` (BT-8), not both.
- VAT accounting currency: `.taxCurrencyCode(CurrencyCode.USD).taxAmountInTaxCurrency(...)` (BT-6, BT-111).
- Payee (BG-10) and seller tax representative (BG-11): `.payee(Payee.create()...)`,
  `.taxRepresentative(SellerTaxRepresentativeParty.create()...)`. Payee identifier and legal
  registration identifier take an optional ISO 6523 scheme, as for seller and buyer:
  `.identifier("4000001000029", "0088")` (BT-60, BT-60-1) and `.legalRegistrationId("123456782", "0002")`
  (BT-61, BT-61-1). In CII an identifier with scheme is written as `ram:GlobalID`, one without as
  `ram:ID`; the legal registration scheme is the `schemeID` of `ram:SpecifiedLegalOrganization/ram:ID`.
  In UBL both are `schemeID` attributes of `cac:PartyIdentification/cbc:ID` and
  `cac:PartyLegalEntity/cbc:CompanyID`; the scheme `SEPA` is reserved for the creditor identifier
  (BT-90) there and rejected for the payee. Payees are allowed from Factur-X BASIC WL upwards and
  in XRechnung; the record constructor `new Payee(name, identifier, legalRegistrationId)` without
  schemes remains.
- Payment means: credit transfer `30`/`58` with one or more `CreditTransfer` accounts, payment card `48`/`54`/`55`
  with `PaymentCardInformation`, SEPA direct debit `59` with `DirectDebit` (mandate, creditor ID, debited account).
  Payment to bank account `42` may name `CreditTransfer` accounts (optional): EN 16931 requires accounts for
  `30`/`58` only (BR-61) and forbids them for no code, and ZUGFeRD 1.0 invoices commonly state `42` with an IBAN.
  The code is kept as stated, not changed to `58`. Other codes carry no account data.
- Supporting documents (BG-24): `.addSupportingDocument(AdditionalSupportingDocument.create().reference("Timesheet")
  .attachment(Attachment.create().content(bytes).mimeCode("application/pdf").filename("timesheet.pdf").build()).build())`.
- Allowances and charges also take a base amount, percentage, and reason code (BT-92 to BT-105).
- Rounding amount: `.roundingAmount(new BigDecimal("0.01"))` (BT-114) is added to the amount due.

Monetary amounts are calculated using `BigDecimal`. Line net amounts are rounded to two decimal
places using `HALF_UP`. Tax is calculated for each tax category and rate based on the line net
amounts and document allowances and charges, then rounded to two decimal places.
Lists are copied during construction; a built `Invoice` is immutable.

Every model class offers a builder via `create()` (shortcuts for allowances and charges:
`DocumentAdjustment.createAllowance()` / `DocumentAdjustment.createCharge()`). Validation runs in `build()`,
so an incomplete builder fails with an exception there. The record constructors remain available.

## Reading an invoice

`UblInvoiceReader`, `CiiInvoiceReader`, and `ZugferdV1InvoiceReader` (ZUGFeRD 1.0) turn XML back
into an `Invoice`; `Paniolo.read` selects the reader and also reads Factur-X PDFs:

```java
import io.github.nalukit.paniolo.model.Invoice;
import io.github.nalukit.paniolo.xml.cii.CiiInvoiceReader;
import io.github.nalukit.paniolo.xml.ubl.UblInvoiceReader;

Invoice fromUbl = new UblInvoiceReader().fromXml(ublXml);
Invoice fromCii = new CiiInvoiceReader().fromBytes(ciiBytes);
```

`fromXml(String)`, `fromBytes(byte[])` and `fromStream(InputStream)` are available; streams
stay open. Parsing rejects DTDs and external entities. The UBL reader accepts `Invoice`
and `CreditNote`.

Readers map the elements the model supports and ignore everything else. Because the model
derives totals from its lines, the reader recalculates them and compares the total VAT amount
and the amount due with the values stated in the document. A difference raises an
`IllegalArgumentException`: it indicates content outside the model, such as CII sub-lines of
other producers or a line amount that is not quantity x net price, that would otherwise be lost
silently. Run [validation](#validating-xml) first for documents from external sources; the readers check structure, not business rules.

Empty optional elements, such as a seller written as `<ram:SellerTradeParty><ram:ID/>...`, an empty
`ram:GlobalID`, `cbc:EndpointID`, or `cac:PartyIdentification/cbc:ID`, count as absent in both read
modes and give no warning. EN 16931 and Factur-X allow them; XRechnung and Peppol forbid them
(`PEPPOL-EN16931-R008`), which validation reports.

For the supported deviations below, pass `ReadMode.LENIENT`. Structural model restrictions
still apply; this is not a general import mode for every valid invoice. The result carries the
invoice and a `ReadWarning` for every accepted deviation:

```java
import io.github.nalukit.paniolo.xml.ReadMode;
import io.github.nalukit.paniolo.xml.ReadResult;

ReadResult result = paniolo.read(xml, ReadMode.LENIENT);   // also on the readers: fromXml(xml, mode)
Invoice invoice = result.invoice();
result.warnings().forEach(w -> log(w.code(), w.lineId(), w.message()));
```

| Warning code          | Deviation                                                                          | Lenient handling                                                                                                                                                         |
|-----------------------|------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `READ-LINE-NET-PRICE` | Line net amount (BT-131) is not quantity x net price                               | Net price (BT-146) recalculated from the stated amount and the line allowances and charges; a gross price above it keeps the discount consistent, a lower one is dropped |
| `READ-TAX-TOTAL`      | Stated total VAT amount (BT-110) differs from the calculated                       | The calculated amount is kept                                                                                                                                            |
| `READ-PAYABLE-AMOUNT` | Stated amount due (BT-115) differs from the calculated                             | The calculated amount is kept                                                                                                                                            |
| `READ-REASON-CODE`    | Allowance reason code outside UNTDID 5189, e.g. the charge code `ABK`              | The code is dropped and the reason text kept; without a reason text the allowance is still rejected                                                                      |
| `READ-PERCENTAGE`     | Allowance or charge percentage without its base amount                             | The percentage is dropped and the stated amount kept; no base amount is derived                                                                                          |
| `READ-PAYMENT-MEANS`  | A further payment means block with other means, text, card, or mandate             | The block is not merged; the model keeps the first payment instruction (BG-16)                                                                                           |
| `READ-TEXT-LINE`      | A ZUGFeRD 1.0 line with text only, without item and quantity                       | The text becomes an invoice note (BG-1)                                                                                                                                  |

Three warnings report values the model does not keep or interpret, without changing an amount.
They are given in **both** modes, so `isExact()` can be `false` after a strict read as well:

| Warning code                 | Reported value                                                                                                     |
|------------------------------|--------------------------------------------------------------------------------------------------------------------|
| `READ-CASH-DISCOUNT`         | A payment terms line (BT-20) that starts like an XRechnung cash discount but cannot be read as one; kept as text  |
| `READ-IDENTIFIER`            | A further identifier of a business term the model keeps once, e.g. the global ID of the deliver-to location (BT-71) |
| `READ-SPECIFICATION-VERSION` | A document of EN 16931:2026 (e.g. XRechnung 4.0), read with the business terms of EN 16931:2017                     |

The invoice then no longer reproduces the document exactly: content outside the model is
still dropped, and written again it states the recalculated price and the calculated totals.
`result.isExact()` tells whether any read warning was emitted; it does not prove lossless
reading. `result.stated()` keeps the original amounts and `result.deviations()` lists amount
differences, including totals that strict reading does not compare. Lines with quantity zero or a
negative recalculated price, and structural errors, are still rejected. `ReadMode.STRICT` is
the default of all methods without a mode.

### Importing received invoices with their original amounts

The invoice model always carries consistent, calculated amounts. When received invoices must be
booked or archived as the sender stated them, import them instead of reading them. The
`ImportResult` keeps the unchanged original XML, the stated amounts (document totals BT-106 to
BT-115, net price and net amount of every line), the invoice model, and each stated amount that
differs from the model as an `AmountDeviation` with business term, line, stated and calculated
value:

```java
import io.github.nalukit.paniolo.xml.ExportVariant;
import io.github.nalukit.paniolo.xml.ImportResult;

ImportResult received = paniolo.importInvoice(xmlOrPdf, ReadMode.LENIENT); // or reader.importXml(xml, mode)
received.stated().lines().get(0).netPrice();        // e.g. 101.00, as stated
received.invoice().lines().get(0).netUnitPrice();   // e.g. 101.50, recalculated from the line amount
received.deviations();                              // BT-146 of line 1: stated 101.00, calculated 101.50, ...
received.isNormalized();                            // true: the model differs from the document

byte[] archive = paniolo.export(received, ExportVariant.ORIGINAL, null);             // byte for byte as received
byte[] ubl     = paniolo.export(received, ExportVariant.NORMALIZED, InvoiceSyntax.UBL); // the model, written anew
```

The export variant is always an explicit choice: `ORIGINAL` returns the received XML (for a PDF
the embedded invoice XML) and is never converted into another syntax; `NORMALIZED` writes the
model with its recalculated amounts.

XML written by Paniolo reads back into an equal `Invoice`. Values without an EN 16931
counterpart that the writers add (UBL card `NetworkID`, CII project name, the `NA` order
placeholder) are dropped again, and the credit note due date in UBL payment terms is
restored as a date.

## Localized enum texts

All nine enums in `io.github.nalukit.paniolo.model.common.value` obtain their
display texts from UTF-8 resource bundles through `LocalizationFactory` in
`io.github.nalukit.paniolo.model.common.value.localization`.
This includes `AllowanceChargeIdentification`, `Country`, `CurrencyCode`, `DateTimeQualifier`,
`DutyTaxFeeCategory`, `InvoiceTypeCode`, `PaymentMeansCode`, `TextSubjectQualifier`, and `UnitCode`.
`UnitCode` has German texts only for units common in invoices (about 70 of 2162); the others
fall back to English.
The enums contain only their language-independent codes and business methods.

```java
import io.github.nalukit.paniolo.model.common.value.AllowanceChargeIdentification;
import io.github.nalukit.paniolo.model.common.value.Country;
import io.github.nalukit.paniolo.model.common.value.InvoiceTypeCode;
import io.github.nalukit.paniolo.model.common.value.PaymentMeansCode;

import java.util.Locale;

var allowance = AllowanceChargeIdentification.CODE_95;
String name = allowance.getName(Locale.GERMAN);                 // Rabatt
String description = allowance.getDescription(Locale.GERMAN);
String label = allowance.toString(Locale.GERMAN);               // 95 - Rabatt
String payment = PaymentMeansCode.CODE_58.getDescription(Locale.GERMAN); // SEPA-Überweisung
String type = InvoiceTypeCode.CODE_381.getDescription(Locale.GERMAN);    // Gutschrift
String country = Country.DE.getText(Locale.ENGLISH);            // Germany
```

English texts cover all code-list values. German texts cover all values except the less common
units, which fall back to English as described above. Existing methods without
an explicit `Locale` preserve their output: `Country.getText()` remains German, while all other
display texts remain English. `Country.toString()` still returns only the country code;
`Country.toString(Locale)` formats the code with the localized country name.
All other enums also provide `toString(Locale)`.

A regional locale such as `de-CH` uses the German texts when no regional translation is
available. Missing languages or individual keys fall back to English, independently of the
JVM default locale. A `null` locale is not allowed.
Codes, Java enum names, `of(...)` lookups, and business classifications remain language-independent.

To add a language, create `<EnumName>_<language>.properties` under
`paniolo-shared-model/src/main/resources/io/github/nalukit/paniolo/model/common/value/localization/`,
for example `PaymentMeansCode_fr.properties`. Regional variants can use filenames such as
`Country_de_CH.properties`. The English base texts are stored in `<EnumName>.properties`.
Keys combine the Java enum constant name with its display field:

- `AllowanceChargeIdentification`: `CODE_95.name` and `CODE_95.description`.
- `Country`: `DE.text`.
- All other enums: For example, `EUR.description` or `CODE_58.description`.

## Validating XML

The rules ship in their own artifact, `paniolo-xrechnung-rules`. It packages the official
[KoSIT validator configuration](https://github.com/itplr-kosit/validator-configuration-xrechnung/releases)
(XSDs and precompiled Schematron XSLTs) in a pinned, checksum-verified release, so
`paniolo-validation` itself stays free of rules and an application decides which rule
version it uses.

```xml
<dependency>
  <groupId>io.github.nalukit</groupId>
  <artifactId>paniolo-xrechnung-rules</artifactId>
  <version>1.0.0-rc01</version>
</dependency>
```

```java
import io.github.nalukit.paniolo.rules.xrechnung.XrechnungRules;
import io.github.nalukit.paniolo.validation.InvoiceValidator;
import io.github.nalukit.paniolo.validation.ValidationProfile;

var profile = ValidationProfile.xrechnung(XrechnungRules.bundle(),
                                          ValidationProfile.Syntax.UBL_INVOICE);
var validator = new InvoiceValidator(profile);
var result = validator.validate(xml);

if (!result.isValid()) {
  result.messages()
        .forEach(message -> System.out.printf("%s %s %s: %s%n",
                                              message.severity(),
                                              message.ruleId(),
                                              message.location(),
                                              message.message()));
}
```

With the `Paniolo` facade you do not need this: `paniolo.validate(xml)` selects the profile
itself. Use `InvoiceValidator` directly only if you depend on `paniolo-validation` without the facade.

`XrechnungRules.bundle()` extracts the packaged archive once per JVM into a private
temporary directory, checks its SHA-256, and deletes it on exit. `XrechnungRules.bundle(Path)`
extracts into a persistent directory instead and reuses it on later starts.
`XrechnungRules.VERSION` and `RELEASE` name the packaged release.

Documents that state an older XRechnung version (1.2 to 2.3) are validated by the facade against
the last KoSIT release of that version, which `paniolo-xrechnung-rules` packages as well, with the
information `XR-OLDER-VERSION`.

To use another rule release, for example during an XRechnung version change, pass the path
of an extracted KoSIT bundle instead:
`ValidationProfile.xrechnung(Path.of("/absolute/path/to/extracted/kosit-bundle"), syntax)`.

The other profiles are `UBL_CREDIT_NOTE` and `CII`. CII invoices and credit notes use
the same profile. `InvoiceValidator` does not detect profiles; the `Paniolo` facade does.
`ValidationProfile.en16931(bundle, syntax)` checks EN 16931 without the XRechnung rules, and
`ValidationProfile.facturX(FacturXRules.bundle(), level)` uses the official Factur-X rules
packaged in `paniolo-facturx-rules` (or an extracted FeRD / FNFE-MPE release).
For standalone XML, the facade falls back to EN 16931 **and XRechnung** when BT-24 is plain
EN 16931, a Peppol identifier, or an unrecognized identifier. It can therefore reject a valid
general EN 16931 invoice for additional XRechnung requirements. Use
`InvoiceValidator` with `ValidationProfile.en16931(bundle, syntax)` when only EN 16931 is intended;
a Factur-X PDF or an explicit Factur-X profile uses the profile selection described below.

`validate` accepts a `String`, `byte[]`, or `InputStream`. The caller manages the stream.
An `InvoiceValidator` can be reused and shared between threads; its rules are compiled
once during construction.

Each finding includes a rule ID, severity, message, XML path (if supplied by the rules),
and validation stage. `isValid()` returns false for `ERROR` or `FATAL` findings;
`hasWarnings()` reports warnings separately. Severity overrides from the selected
KoSIT scenario are applied. The result reflects these technical checks; it does not
guarantee acceptance by a recipient and is not a complete KoSIT VARL report.

Malformed XML produces a finding. Schematron is not run after XSD errors.
Missing or broken validation artifacts and execution failures throw `ValidationException`.
Custom rules can be configured using
`ValidationProfile(name, root, xsd, xsltFiles, severityOverrides[, documents])`, where `documents`
are lookup files such as code lists that the stylesheets read with `document()`. **Raw `.sch`
files are not compiled directly**; use the Schematron XSLTs supplied by the rule publisher.

Invoice XML is parsed with DTDs, external entities, and XInclude disabled.
Rule bundles are treated as trusted application code. Local XSD and XSLT imports are
restricted to the bundle directory; network requests and external document access during
transformation are blocked, except for the lookup documents declared in the profile.
Paniolo does not download rules at runtime.

### Optional calculation check

The official Schematron rules already check most amount relationships (BR-CO-10 to BR-CO-17 for
the totals, BR-xx-08 / BR-xx-09 for the VAT breakdown). XRechnung adds the Peppol rules for line
net amounts (PEPPOL-EN16931-R120), net prices from gross prices (R046) and allowance percentages
(R040); the Factur-X profiles do not have them. The optional calculation check recalculates the
amounts with the invoice model and reports what does not add up in a stage of its own,
`ValidationMessage.Stage.CALCULATION`, with identifiers starting with `PANIOLO-CALC-`:

```java
Paniolo paniolo = Paniolo.builder().calculationCheck(true).build();   // off by default
ValidationResult result = paniolo.validate(xml);
result.isValid();                                                     // official rules only
result.messages(ValidationMessage.Stage.CALCULATION);                 // additional hints
```

| Rule                            | Relationship                                                     | Official counterpart             |
|---------------------------------|------------------------------------------------------------------|----------------------------------|
| `PANIOLO-CALC-LINE-NET-AMOUNT`  | BT-131 = BT-129 x BT-146 / BT-149 + line charges - line allowances | PEPPOL-EN16931-R120 (XRechnung) |
| `PANIOLO-CALC-NET-PRICE`        | BT-146 = BT-148 - BT-147                                         | PEPPOL-EN16931-R046 (XRechnung) |
| `PANIOLO-CALC-ALLOWANCE-CHARGE` | allowance or charge = base amount x percentage / 100             | PEPPOL-EN16931-R040 (XRechnung) |
| `PANIOLO-CALC-TOTAL`            | stated totals BT-106 to BT-115 = calculated totals                | BR-CO-10 to BR-CO-17             |
| `PANIOLO-CALC-NOT-CHECKED`      | the amounts could not be recalculated (information)             | -                                |

The findings are warnings and never change `isValid()`. If an official rule of the same result
already reports the relationship, the finding is lowered to information and names that rule; a VAT
amount within the rounding the official rules accept is information as well. So official
violations and additional hints stay separately assessable. The
[calculation corpus](paniolo/src/test/resources/calculation-corpus/README.md) shows the findings,
their severity and the duplicates for XRechnung CII and UBL and for Factur-X EN 16931: with
XRechnung every finding is a duplicate, with Factur-X EN 16931 the line net amount, the net price
and the allowance percentage are found only by the calculation check. The CLI enables the check
with `--calculation-check`.

### Optional rule statistics

For audits or diagnosis, a result can document how much of the rules was applied. With
`ruleStatistics(true)`, `ValidationResult.statistics()` lists per Schematron stylesheet the active
patterns, the fired rules (rule contexts that matched a node), the failed asserts, and the
successful reports, and per PDF/A check the flavour, the veraPDF profile, the number of checks, and
the failures per rule. Findings and validity stay the same; the [XML report](paniolo-validation/README.md#xml-report)
adds an element `statistics`.

```java
Paniolo audit = Paniolo.builder().ruleStatistics(true).build();   // off by default
ValidationResult result = audit.validate(xml);
result.statistics().schematron();   // e.g. EN16931-CII-validation.xslt: 2381 fired rules, 0 failed asserts
result.statistics().pdfa();         // for PDFs: 3b, compliant, checks, failures per rule
```

A document for which hardly any rule fired was hardly checked, even without findings. Counting
fired rules makes the stylesheets report every matched rule context, which Paniolo otherwise
removes before compiling. Measured on an Apple M4 Max, the median validation time grew by 0 to 2 %
for one line, 6 to 8 % for 100 lines, and 5 to 11 % for 1000 lines (UBL and CII). The PDF/A
statistics add no measurable time. Use a separate instance for audits and keep the regular one
without statistics. The CLI enables them with `--rule-statistics`.

### Acceptance policy

`isValid()` is the verdict of the official rules: no `ERROR` or `FATAL`. Which warnings a receiving
process must not accept is a separate decision. The KoSIT XRechnung configuration, for example, rates
the Peppol rule `PEPPOL-EN16931-R120` (line net amount = quantity x net price) as a warning in UBL and
CII, so such a document is valid; other validators reject it. An `AcceptancePolicy` names the
blocking rules explicitly and leaves the official result unchanged:

```java
AcceptancePolicy policy = AcceptancePolicy.officialRules()
                                          .rejectOn("PEPPOL-EN16931-R120",            // XRechnung
                                                    "PANIOLO-CALC-LINE-NET-AMOUNT");  // Factur-X, with calculationCheck(true)
AcceptanceDecision decision = policy.evaluate(paniolo.validate(xml));
decision.valid();              // official result
decision.accepted();           // false if an error or a named rule was reported
decision.blockingRules();      // e.g. [PEPPOL-EN16931-R120]
```

Errors always reject; a named rule blocks at any severity. `rejectOnWarnings()` rejects on every
warning, a stricter choice that may also reject documents the recipient accepts. In the CLI:
`paniolo validate --reject-rule PEPPOL-EN16931-R120 inbox -r --summary`. The
[calculation corpus](paniolo/src/test/resources/calculation-corpus/README.md) shows the effect:
rejecting R120 blocks exactly its line amount cases for XRechnung CII and UBL; adding
`PANIOLO-CALC-LINE-NET-AMOUNT` also blocks the Factur-X case.

## Factur-X / ZUGFeRD

Hybrid invoices (a PDF/A-3 document with embedded CII XML) need the optional module
`paniolo-facturx`, which brings Apache PDFBox. Add it instead of or next to `paniolo`:

```xml
<dependency>
  <groupId>io.github.nalukit</groupId>
  <artifactId>paniolo-facturx</artifactId>
  <version>1.0.0-rc01</version>
</dependency>
```

```java
import io.github.nalukit.paniolo.config.FacturXProfile;
import io.github.nalukit.paniolo.config.PdfAttachment;
import io.github.nalukit.paniolo.model.common.ProcessControl;

Invoice invoice = Invoice.create()
                         .processControl(ProcessControl.en16931())  // BT-24 of the profile
                         /* ... */
                         .build();
byte[] facturX = paniolo.toFacturX(invoice,
                                   pdfA,
                                   FacturXProfile.EN16931,
                                   List.of(PdfAttachment.of("stundenzettel.pdf",   // optional
                                                            timeSheet,
                                                            "application/pdf")));
Invoice read = paniolo.read(facturX);                 // PDFs are detected automatically
ValidationResult result = paniolo.validate(facturX);  // checks the embedded XML
byte[] xml = paniolo.extractXml(facturX);
List<PdfAttachment> files = paniolo.attachments(facturX);  // further embedded files
```

| Profile     | Write | Embedded file   | Specification identifier (BT-24)                       | Validated against                             |
|-------------|-------|-----------------|--------------------------------------------------------|-----------------------------------------------|
| `MINIMUM`   | yes¹  | `factur-x.xml`  | Factur-X MINIMUM (`ProcessControl.facturXMinimum()`)   | Factur-X schema and Schematron of the profile |
| `BASIC_WL`  | yes¹  | `factur-x.xml`  | Factur-X BASIC WL (`ProcessControl.facturXBasicWl()`)  | Factur-X schema and Schematron of the profile |
| `BASIC`     | yes   | `factur-x.xml`  | Factur-X BASIC (`ProcessControl.facturXBasic()`)       | Factur-X schema and Schematron of the profile |
| `EN16931`   | yes   | `factur-x.xml`  | `urn:cen.eu:en16931:2017` (`ProcessControl.en16931()`) | Factur-X schema and Schematron of the profile |
| `EXTENDED`  | yes   | `factur-x.xml`  | Factur-X EXTENDED (`ProcessControl.facturXExtended()`) | Factur-X schema and Schematron of the profile |
| `XRECHNUNG` | yes   | `xrechnung.xml` | XRechnung 3.0 (`ProcessControl.xrechnung()`)           | EN 16931 and XRechnung                        |

¹ From an invoice without lines with stated totals; see [profiles without lines](#profiles-without-lines-minimum-and-basic-wl).

To combine XML you already have, for example received or produced by another system, with a
PDF/A, use `embedXml` instead: the XML is embedded byte for byte, without the detour through the
invoice model, and the result is validated right away.

```java
FacturXResult result = paniolo.embedXml(pdfA, xml, FacturXProfile.EN16931);
Files.write(target, result.pdf());          // extractXml(result.pdf()) returns exactly xml
result.validation().messages();             // container, PDF/A (with paniolo-pdfa), XML rules
```

Before embedding, `embedXml` rejects a profile that cannot be embedded (`EXTENDED_CTC_FR`), input
that is no PDF, a PDF that is not PDF/A, encrypted, or already contains an invoice, XML that is
not CII (UBL, ZUGFeRD 1.0, malformed XML), and a specification identifier (BT-24) of another
profile, each with an `IllegalArgumentException` naming the problem. XML that violates the rules
of its profile is embedded anyway and reported in `result.validation()`. A valid result means a
valid XML and container; whether the visual PDF shows the same content is not checked.

A PDF passed to `toFacturX(invoice, pdf, profile)` must already be PDF/A (part 1, 2, or 3) and
show the invoice; Paniolo does not convert arbitrary PDFs to PDF/A. The embedded files are
compressed and the result is saved with object streams; the streams of your PDF are taken as they
are, so pass a compressed PDF to get a small file. Without a PDF of your own,
`toFacturX(invoice, profile)` renders the page with
[`paniolo-pdf-layout`](paniolo-pdf-layout/README.md). Profile BASIC is a subset of
EN 16931: the CII writer rejects invoices with content BASIC does not allow, for example contacts,
supporting documents, BIC, item descriptions, or item identifiers, and names all of them. Profile
EXTENDED adds the header information in `Invoice.extended()`, such as further parties, structured
payment terms, logistics service charges, and advance payments, and the line information in
`InvoiceLine.extended()`, such as sub-lines, product details, and line references; see
[profile EXTENDED](paniolo-facturx/README.md#profile-extended). Further files such as a time sheet
can be embedded and read with `attachments(pdf)`. ZUGFeRD 1.0 PDFs are migrated with
`migrateZugferd1(pdf)`. The container follows the Factur-X specification for Germany; the written
XML is validated against the packaged official Factur-X 1.09.2 rules. See the
[module README](paniolo-facturx/README.md).

### Profiles without lines (MINIMUM and BASIC WL)

MINIMUM and BASIC WL carry no invoice lines; in Germany they are booking aids, not invoices.
Paniolo writes them from an `Invoice` without lines that states its totals with `DocumentTotals`:

```java
import io.github.nalukit.paniolo.model.common.DocumentTotals;

Invoice basicWl = Invoice.create()
                         .processControl(ProcessControl.facturXBasicWl())
                         /* number, dates, seller, buyer, payment, allowances, ... as for BASIC */
                         .statedTotals(DocumentTotals.create()
                                                     .lineTotal(new BigDecimal("200"))   // BT-106
                                                     .addVatBreakdown(TaxCategory.standard(new BigDecimal("19")),
                                                                      new BigDecimal("200"),
                                                                      new BigDecimal("38"))
                                                     .build())
                         .build();
byte[] pdf = paniolo.toFacturX(basicWl, pdfA, FacturXProfile.BASIC_WL);

Invoice minimum = Invoice.create()
                         .processControl(ProcessControl.facturXMinimum())
                         .seller(Party.create()
                                      .name("Muster GmbH")
                                      .address(Address.ofCountry(Country.DE))
                                      .vatId("DE123456788")
                                      .build())
                         .buyer(Party.create()
                                     .name("Kunde GmbH")
                                     .build())   // MINIMUM: no buyer address needed
                         /* number, issue date, optional buyer and purchase order reference */
                         .statedTotals(DocumentTotals.create()
                                                     .taxBasisTotal(new BigDecimal("200"))  // BT-109
                                                     .taxTotal(new BigDecimal("38"))        // BT-110
                                                     .build())
                         .build();
```

Without explicit totals, the amount without VAT and the VAT amount are the sums of the VAT breakdown.
The model checks BR-CO-13 (line total minus allowances plus charges) and BR-CO-14 and derives the
amount with VAT and the amount due from the paid and rounding amounts. BASIC WL has the header of
BASIC and needs the line total and the VAT breakdown. MINIMUM allows only the document
identification, seller and buyer with name, legal registration identifier, country, and tax
registrations, the buyer and purchase order references, and the totals; the writer rejects all
other content and names it. A paid amount is not written in MINIMUM but reduces the amount due,
and reading restores it from the difference. `read` returns such documents as invoices with
`hasStatedTotals()`; UBL and the other profiles reject invoices without lines.

## Validating Factur-X / ZUGFeRD

`paniolo.validate(pdf)` checks a hybrid invoice in three stages and returns one
`ValidationResult`:

| Stage                      | Checked                                                                                                                        | Module                    |
|----------------------------|--------------------------------------------------------------------------------------------------------------------------------|---------------------------|
| `HYBRID`                   | embedded invoice file, `/AF` reference, `AFRelationship`, MIME type, XMP `fx:` metadata, extension schema, profile vs. BT-24   | `paniolo-facturx`         |
| `PDF_A`                    | PDF/A-3 conformance with veraPDF                                                                                               | `paniolo-pdfa` (optional) |
| `XML`, `XSD`, `SCHEMATRON` | embedded invoice with the official Factur-X schema and Schematron of its profile; EN 16931 and XRechnung for profile XRECHNUNG | `paniolo-validation`      |

```java
ValidationResult result = paniolo.validate(facturXPdf);
List<ValidationMessage> pdfa = result.messages(ValidationMessage.Stage.PDF_A);   // findings of one stage
String report = result.toXml();                                                  // machine-readable report
```

The report counts the findings per severity and groups them by stage; see
[XML report](paniolo-validation/README.md#xml-report) for the format and its XSD.

Container rules have IDs starting with `FX-`, for example `FX-GUIDELINE` when `fx:ConformanceLevel`
does not match the specification identifier of the embedded XML, or `FX-AF-RELATIONSHIP-DE` (warning)
when the relationship differs from the German requirement. PDF/A rules start with `PDFA-`. A PDF
without embedded invoice yields the finding `FX-NO-INVOICE` instead of an exception. The XML of a
ZUGFeRD 1.0 PDF is checked against the ZUGFeRD 1.0 rules, with the warning `FX-ZUGFERD-1`.

The information `FX-PRODUCER` names the software that produced the PDF, e.g.
`Produced by Intarsys (producer "intarsys PDF/A Live!", creator "intarsys ZUGFeRD Toolkit 2.1")`, so
that systematic errors of received documents can be traced to their software.
`paniolo.producer(pdf)` returns the same as `PdfProducer`: producer and creator from the document
information (or XMP `pdf:Producer`, `xmp:CreatorTool`), and the e-invoicing software recognized
from the signatures the Mustang project uses (Mustang, Intarsys, Konik, Symtrax, Factur-X Python,
pdfMachine, Ghostscript, CIB pdf brewer, Lexware office, ZUGFeRD.PDF-csharp, FactoorSharp, sevdesk)
and Paniolo's own. Paniolo keeps the producer of the source PDF and signs it, like Mustang:
`Microsoft Word` becomes `Microsoft Word (via Paniolo)` in the document information and XMP, a PDF
without producer gets `Paniolo (via Paniolo)`. This applies to Factur-X, Order-X, and CIDA PDFs and
to migrated ZUGFeRD 1.0 PDFs.

The XML of all five Factur-X profiles is validated against the official schema and Schematron of
its profile, packaged in [`paniolo-facturx-rules`](paniolo-facturx-rules/README.md): the FeRD
release ZUGFeRD 2.5.2 / Factur-X 1.09.2, whose technical artifacts FeRD publishes under the Apache
License 2.0
([Terms of use](https://www.ferd-net.de/ueber-uns/ressourcen-1/veroeffentlichungen/haftungsausschluss-und-nutzungsrechte)).
The profile is taken from the XMP metadata or BT-24, also in the ZUGFeRD 2.0 form; profile
XRECHNUNG is checked against EN 16931 and XRechnung. To use another release, extract it and
configure it with `Paniolo.builder().facturXRules(path)`; see the
[module README](paniolo-facturx/README.md#factur-x-profile-rules).

## Thread safety and multi-user use

Paniolo keeps no mutable global state, so concurrent requests and tenants do not affect
each other. Static methods (`create()`, `of(...)`, `LocalizationFactory`, total calculation)
work only on their arguments; static fields are immutable constants. Settings such as the
display language are passed as parameters (`getDescription(Locale)`), never set globally.

| Type                                                                                                                                                                                                                                           | Share between threads?                                                  |
|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------|
| `Paniolo`                                                                                                                                                                                                                                      | Yes; create once, compiled validators are cached (see `warmUp()`)       |
| `Invoice` and all model records                                                                                                                                                                                                                | Yes, immutable; lists and attachment bytes are copied                   |
| `Invoice.Builder` and other builders                                                                                                                                                                                                           | No, mutable; use one per request or thread                              |
| `UblInvoiceWriter`, `CiiInvoiceWriter`, `UblInvoiceReader`, `CiiInvoiceReader`, `ZugferdV1InvoiceReader`, `OrderXWriter`, `OrderXReader`, `DespatchAdviceWriter`, `DespatchAdviceReader`, `CiiDespatchAdviceWriter`, `CiiDespatchAdviceReader` | Yes, stateless                                                          |
| `PanioloJson`                                                                                                                                                                                                                                  | Yes, immutable                                                          |
| `PdfBoxFacturXSupport`, `VeraPdfaSupport`                                                                                                                                                                                                      | Yes, stateless                                                          |
| `InvoiceValidator`                                                                                                                                                                                                                             | Yes; create once per profile, its rules are compiled in the constructor |
| `XrechnungRules.bundle()`                                                                                                                                                                                                                      | Yes, synchronized; extracts once per JVM and returns the same directory |

In a server application, create writers, readers, and one `InvoiceValidator` per profile
once (for example as singletons or container beans) and reuse them for all requests.
`Paniolo` compiles each document type under its own lock: while one thread compiles the rules for
CII, validations of UBL invoices continue. Call `warmUp()` at start-up to compile the XRechnung
rules before the first request.
Each `XrechnungRules.bundle()` call site in a separately deployed class loader extracts its
own copy and registers a shutdown hook; with frequent hot redeploys prefer
`XrechnungRules.bundle(Path)` with a fixed directory.

## Logging

Paniolo logs through `java.lang.System.Logger` and therefore adds no logging dependency.
Without further setup the messages go to `java.util.logging`. To route them into your
logging framework, add its bridge to your application:

| Framework            | Bridge                                 |
|----------------------|----------------------------------------|
| SLF4J (Logback, ...) | `org.slf4j:slf4j-jdk-platform-logging` |
| Log4j 2              | `org.apache.logging.log4j:log4j-jpl`   |

Logger names are the class names below `io.github.nalukit.paniolo`. Paniolo logs little:
errors are reported as exceptions or in `ValidationResult`.

| Level   | Logger                              | Message                                                                       |
|---------|-------------------------------------|-------------------------------------------------------------------------------|
| `DEBUG` | `...rules.xrechnung.XrechnungRules` | Rules extracted or reused: version, directory, duration                       |
| `DEBUG` | `...rules.facturx.FacturXRules`     | Factur-X rules extracted or reused: release, directory, duration              |
| `DEBUG` | `...validation.InvoiceValidator`    | Profile compiled: name, stylesheet count, duration                            |
| `TRACE` | `...validation.InvoiceValidator`    | Validation finished: profile, validity, finding count, rule IDs               |
| `DEBUG` | `...facturx.PdfBoxFacturXSupport`   | Invoice embedded: file name, profile, PDF/A conformance; unreadable XMP       |
| `DEBUG` | `...pdflayout.PdfBoxLayoutSupport`  | Invoice or validation report rendered: line or finding count, size            |
| `DEBUG` | `...pdfa.VeraPdfaSupport`           | PDF/A validation finished: finding count, duration                            |
| `TRACE` | `...model.base.PanioloLogFactory`   | Start and end of every facade method (`toXml`, `validate`, ...) with duration |

Invoice content (names, addresses, bank accounts, finding texts) is never logged.

## Building and testing

The optional [public invoice corpus](test-paniolo-invoice-corpus/README.md) retains 168 pinned
third-party XML/PDF fixtures and repeatable validation/read baselines, including documented
compatibility gaps. Run it with `mvn -Pinvoice-corpus -pl test-paniolo-invoice-corpus -am test`.

Requirements: JDK 21 or newer and Maven 3.9.

```sh
mvn verify
```

This runs the tests of the default reactor, including the integration tests against the official
XRechnung rules. The public invoice corpus and JMH benchmarks require their opt-in profiles.
The build of `paniolo-xrechnung-rules` downloads the pinned KoSIT release `v2026-08-31`
once from GitHub, verifies its SHA-256 checksum, and caches it in the local Maven repository.
The release contains XRechnung 3.0.2, EN 16931 rules 1.3.16, and XRechnung Schematron 2.6.0.

To test another existing bundle, pass its absolute path through `xrechnung.bundle`:

```sh
mvn verify -Dxrechnung.bundle="/absolute/path/to/extracted/bundle"
```

`scripts/download-xrechnung-rules.sh` downloads and extracts the pinned release to `target`
if you want to inspect the files.

Every Java, JavaScript, Python, and shell source carries the Apache 2.0 header from
[`license-header.txt`](license-header.txt). The build checks it in the `validate` phase with the
license-maven-plugin and fails for a file without it; add missing headers with:

```sh
mvn license:format -Pbenchmark,invoice-corpus
```

Third-party files (ISO Schematron skeleton, rule archives, test fixtures) keep their own notices
and are not touched.

The integration tests cover invoices, credit notes, multiple tax rates, tax exemptions,
transactions outside the scope of VAT, document allowances and charges, and missing buyer
references in both syntaxes. The Factur-X tests create PDF/A documents, check them with veraPDF,
and cover all profiles, attachments, and the ZUGFeRD 1.0 migration with samples written for
Paniolo.

The Factur-X tests validate against the official rules packaged in `paniolo-facturx-rules`. To
test another release, set `PANIOLO_FACTURX_RULES` to the extracted FeRD / FNFE-MPE package:

```sh
PANIOLO_FACTURX_RULES=/path/to/factur-x-package mvn verify
```

The tests of `paniolo-mustang` run against Mustang 2.26.0, which Maven downloads as `provided`
dependency. `scripts/mustang-matrix.sh` runs them against further Mustang versions as well, e.g.
before an application upgrades Mustang; see the [module README](paniolo-mustang/README.md#test-matrix).
JMH benchmarks are in [`paniolo-benchmark`](paniolo-benchmark/README.md) (Maven profile
`benchmark`).

## Releases and security

A release is published to Maven Central under group ID `io.github.nalukit` and tagged with its
version in Git. Release version, tag, Maven artifacts, the versioned API reference
`docs/api/<version>.md` and its runnable quickstart describe the same commit;
[`scripts/release/check-release.sh`](scripts/release/check-release.sh) checks this before and
after publishing; its header describes the static checks and the `--artifacts` check against
Maven Central. `HEAD-SNAPSHOT` is the development build of the `dev` branch and is not supported
for production use.

Supported release lines and how to report a vulnerability privately are described in
[SECURITY.md](SECURITY.md). Please do not report vulnerabilities in public issues.

## License

Apache License 2.0; see [LICENSE](LICENSE). The validation artifacts packaged in
`paniolo-xrechnung-rules` remain subject to their publishers' terms (Apache 2.0, EUPL 1.2,
OASIS and UN/CEFACT notices); see that module's
[README](paniolo-xrechnung-rules/README.md#licenses) and `META-INF/NOTICE` in its JAR.
The Factur-X / ZUGFeRD rules packaged in `paniolo-facturx-rules` are published by FeRD under the
Apache License 2.0; see its [README](paniolo-facturx-rules/README.md#licenses). The same module
packages the French EXTENDED-CTC-FR and BR-FR rules of FNFE-MPE (France_RFE, Apache 2.0, with
their `LICENSE`), the Order-X 1.0 rules of FeRD and FNFE-MPE (Apache 2.0 according to their terms
of use; Order-X is based on the UN/CEFACT Cross Industry Order, the UNECE terms apply
accordingly), and the ZUGFeRD 1.0 schemas and Schematron taken unmodified from the Mustang project
(Apache 2.0, with the original FeRD terms of use of ZUGFeRD 1.0). The Order-X SVRL stylesheets
compiled by Paniolo contain templates of the ISO Schematron skeleton (MIT License,
`META-INF/licenses/MIT-ISO-Schematron.txt`). `META-INF/NOTICE` of both rules modules lists every
packaged work with its terms.

Paniolo itself is Apache 2.0, but some dependencies are under the Mozilla Public License 2.0:
Saxon-HE, a required dependency of `paniolo-validation`, and veraPDF (dual licensed GPLv3+ /
MPLv2+, used under MPL 2.0) with its dependency Rhino in the optional module `paniolo-pdfa`.
MPL 2.0 is file-based: these libraries are used unmodified, and their source is available from
their projects. The executable jar of `paniolo-cli` bundles them and lists them with their source
locations in `META-INF/THIRD-PARTY.txt`, next to `META-INF/licenses/MPL-2.0.txt`. The test resources also
contain third-party examples: official Order-X and French EXTENDED-CTC-FR
examples, and the opt-in public invoice corpus. Their origins and license notices are documented
in the respective resource READMEs and the [corpus README](test-paniolo-invoice-corpus/README.md).

### OASIS and UN/CEFACT

The UBL 2.1 schemas in `paniolo-xrechnung-rules` are Copyright (c) OASIS Open 2001-2013, All
Rights Reserved; the UN/CEFACT Cross Industry Invoice D16B schemas there are Copyright (C)
UN/CEFACT (2016), All Rights Reserved. Both publishers permit copying and distributing these
documents unmodified with their copyright notice, which Paniolo does: the schemas are packaged
unchanged in the KoSIT archives, and the notices are part of each schema file and of
`META-INF/licenses/OASIS-NOTICE.txt` and `META-INF/licenses/UNCEFACT-NOTICE.txt` in the JAR. The
UBL schemas include the W3C XML Signature schema under the W3C Software License. The Factur-X /
ZUGFeRD and Order-X schemas in `paniolo-facturx-rules` are based on the UN/CEFACT Cross Industry
Invoice and Supply Chain Reference Data Model.

The code lists of the model and of `paniolo-visualizer` (units after UN/ECE Recommendation 20/21,
UNTDID 1001, 4451, 4461, 5189, 5305, 7161 and others, ISO 3166 countries, ISO 4217 currencies)
carry the codes as published by UNECE / UN/CEFACT and ISO, as EN 16931 requires. The English
names follow the published code lists; the German and French texts are Paniolo's own translations
and, in places, shortened names. UBL is a trademark of OASIS; UN/CEFACT and UNECE are
bodies of the United Nations. Paniolo is not affiliated with or endorsed by OASIS, UN/CEFACT or
UNECE.

### Logo

The Paniolo logo and all variants in `docs/branding/` and `docs/site/assets/` were generated with
AI image generation and then traced and edited for the project; see the
[branding README](docs/branding/README.md#origin-and-rights).

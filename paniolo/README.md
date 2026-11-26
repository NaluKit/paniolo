# Paniolo

`paniolo` is the module applications depend on. It contains the UBL and CII writers and readers
and the validation, and brings the model and the official XRechnung and Factur-X rules
([`paniolo-rules`](../paniolo-rules/README.md)); all of it is available behind one entry point,
`io.github.nalukit.paniolo.Paniolo`. New features are added to this facade, so the public
API stays in one place. It requires Java 21.

What the facade covers:

- **Invoices and credit notes** (EN 16931, XRechnung, XRechnung extension) in UBL 2.1 and CII D16B:
  write, read (strict or lenient), detect, validate.
- **Factur-X / ZUGFeRD** hybrid PDFs in all profiles: create, read, extract, validate (container,
  PDF/A, XML), attachments, producing software; migration of ZUGFeRD 1.0.
- **Order-X** orders, order changes, and order responses as XML and PDF.
- **Quotations** rendered as PDF and converted to invoices through the model's `toInvoice` methods;
  quotations have no XML syntax and can be stored with `paniolo-json`.
- **Despatch advices** (delivery notes) as UBL DespatchAdvice (Peppol BIS 3), CIDA XML, and PDF.
- **Rendering** of the visual pages of all these documents, so no PDF of your own is
  needed (with [`paniolo-facturx`](../paniolo-facturx/docs/pdf-layout.md)): PDF/A-3b by default,
  PDF/A-1b or a PDF 1.7 / 2.0 preview through `PdfLayout.withProfile`.

## Dependency

For the published release, add the dependency below. After installing the current source from
the repository root with `mvn install`, use `HEAD-SNAPSHOT` instead; see
[installation and version scope](../README.md#installation).

```xml
<dependency>
  <groupId>io.github.nalukit</groupId>
  <artifactId>paniolo</artifactId>
  <version>1.0.1</version>
</dependency>
```

PDF features need [`paniolo-facturx`](../paniolo-facturx/README.md) on the class path, PDF/A checks
additionally [`paniolo-pdfa`](../paniolo-pdfa/README.md). Both are found through
`java.util.ServiceLoader`. Without `paniolo-facturx`, methods that read or create hybrid PDFs
raise an `IllegalStateException` naming the missing dependency. Without `paniolo-pdfa`, PDF
validation still runs the container and XML checks and adds the information `PDFA-NOT-CHECKED`.
Rendering the pages
(`toPdf`, and `toFacturX`, `toOrderXPdf`, `toDespatchAdvicePdf` without a PDF) is part of
[`paniolo-facturx`](../paniolo-facturx/docs/pdf-layout.md) as well.

## Usage

```java
import io.github.nalukit.paniolo.Paniolo;
import io.github.nalukit.paniolo.config.InvoiceSyntax;
import io.github.nalukit.paniolo.model.Invoice;
import io.github.nalukit.paniolo.validation.ValidationResult;

Paniolo paniolo = Paniolo.create();                        // packaged XRechnung rules

String xml = paniolo.toXml(invoice, InvoiceSyntax.CII);    // or InvoiceSyntax.UBL
ValidationResult result = paniolo.validate(xml);
if (!result.isValid()) {
  result.messages()
        .forEach(m -> System.out.println(m.ruleId() + ": " + m.message()));
}
Invoice read = paniolo.read(xml);
```

`read(bytes)` and `validate(bytes)` detect UBL, CII, ZUGFeRD 1.0, and Factur-X / ZUGFeRD PDFs.
Validation and model import are separate checks: a valid document can still contain data the
model cannot represent. `ReadMode.LENIENT` accepts the documented amount deviations but does
not guarantee that every valid invoice can be read; see the
[public invoice corpus](../test-paniolo-invoice-corpus/README.md).

```java
byte[] received = Files.readAllBytes(path);
ValidationResult result = paniolo.validate(received);
Invoice invoice = paniolo.read(received);                              // strict
ReadResult lenient = paniolo.read(received, ReadMode.LENIENT);         // warnings and amount deviations
ImportResult imported = paniolo.importInvoice(received, ReadMode.LENIENT); // original XML and stated amounts kept
```

The three read modes apply the same structural model checks and do not run XML validation:

| Mode | Deviating line price or quantity | Other supported deviations |
|------|----------------------------------|----------------------------|
| `STRICT` (default) | Rejects changes beyond price-rounding tolerance | Rejects a differing VAT total or amount due; reports other total differences |
| `LENIENT` | Can recalculate the price or negate the quantity | Keeps the model's totals and reports changes |
| `LENIENT_KEEP_LINES` | Rejects a line that would need either change | Handles other deviations like `LENIENT` |

All modes can report import warnings and amount deviations. A stated line amount within the
price-rounding tolerance is kept, but a difference from its calculated amount is still listed as
BT-131 in `deviations()`. `isExact()` checks only warnings; use `requireNoDeviations()` to reject
any listed amount difference, including after a strict read. It also rejects a listed BT-131
difference whose stated amount was retained. Keep the original XML with `importInvoice` when
archiving; see [reading an invoice](../README.md#reading-an-invoice).

Build invoices with `Invoice.create()` and the model builders; see the
[project README](../README.md#generating-an-invoice). Every method is described in detail, with
examples, in the Javadoc of [`Paniolo`](src/main/java/io/github/nalukit/paniolo/Paniolo.java).

| Method                                                                                                                                                                | Purpose                                                                                                                                                               |
|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `create()`, `builder()`                                                                                                                                               | Instance with the packaged rules, or with further rules (see [configuration](#configuration))                                                                         |
| `warmUp`                                                                                                                                                              | Compile the XRechnung rules for UBL and CII now instead of on the first validation                                                                                    |
| `toXml`, `toBytes`                                                                                                                                                    | Write an invoice as UBL or CII (`InvoiceSyntax`)                                                                                                                      |
| `read`                                                                                                                                                                | Read UBL Invoice, UBL CreditNote, CII, ZUGFeRD 1.0, or a Factur-X PDF from `String`, `byte[]`, or `InputStream`; with an explicit `ReadMode` a `ReadResult` with warnings, stated amounts, and deviations |
| `importInvoice`, `export`                                                                                                                                             | Import a received invoice with original XML, stated amounts and deviations; export it explicitly as `ORIGINAL` or `NORMALIZED`                                        |
| `validate`                                                                                                                                                            | Validate XML or a PDF with the rules detected from the content, an `Invoice` in a given syntax, or XML against an explicit document type or Factur-X profile          |
| `detect`, `isPdf` | XML syntax (`UBL_INVOICE`, `UBL_CREDIT_NOTE`, `CII`); whether the content is a PDF |
| `isEInvoice`, `probe` | Quick e-invoice recognition for XML or PDF; false or an empty optional for unsuitable content, MINIMUM, BASIC WL, or ZUGFeRD 1.0; does not validate |
| `toFacturX`, `extractXml`, `attachments`, `producer`                                                                                                                  | Create a Factur-X / ZUGFeRD PDF from your PDF/A or with a rendered page, get its XML, further files, or producing software; needs `paniolo-facturx`                   |
| `embedXml`                                                                                                                                                            | Embed existing CII XML byte for byte into your PDF/A and validate the result; needs `paniolo-facturx`                                                                 |
| `toPdf`                                                                                                                                                               | Render the visual PDF/A-3b document of an invoice, order, or despatch advice without XML; needs `paniolo-facturx`                                                  |
| `toReportPdf`                                                                                                                                                         | Render a validation report as PDF/A-3b; needs `paniolo-facturx`                                                                                                    |
| `migrateZugferd1`                                                                                                                                                     | Migrate ZUGFeRD 1.0 XML or PDF to Factur-X / ZUGFeRD 2                                                                                                                |
| `toOrderX`, `toOrderXBytes`, `toOrderXPdf`, `readOrder`, `validateOrder`                                                                                              | Order-X documents; see [Order-X](../paniolo-facturx/README.md#order-x)                                                                                                |
| `toDespatchAdvice`, `toDespatchAdviceBytes`, `toCiiDespatchAdvice`, `toCiiDespatchAdviceBytes`, `toDespatchAdvicePdf`, `readDespatchAdvice`, `validateDespatchAdvice` | Despatch advices in UBL and CIDA; see [despatch advice](docs/ubl.md#despatch-advice)                                                                 |

## Configuration

`Paniolo.builder()` configures rules that Paniolo does not distribute, replaces the packaged ones,
or switches on optional behavior. All settings are optional. The paths of `facturXRules`,
`extendedCtcFrRules`, `orderXRules`, and `despatchAdviceRules` are checked when they are set; the
KoSIT bundle of `rules` is loaded on the first validation or by `warmUp()`:

```java
Paniolo paniolo = Paniolo.builder()
                         .rules(Path.of("/opt/kosit/xrechnung-3.0.2"))              // other KoSIT release
                         .facturXRules(Path.of("/opt/factur-x/1.09.2"))             // other Factur-X release
                         .orderXRules(Path.of("/opt/order-x/1.0"))                  // other Order-X release
                         .despatchAdviceRules(Path.of("/opt/peppol/despatch-advice.xslt")) // compiled Peppol Schematron
                         .calculationCheck(true)                                    // recalculate amounts
                         .build();
```

| Builder method        | Without it                                                                                  |
|-----------------------|---------------------------------------------------------------------------------------------|
| `rules`               | The KoSIT release packaged in `paniolo-rules`                                     |
| `facturXRules`        | The FeRD release packaged in `paniolo-rules` (ZUGFeRD 2.5.2 / Factur-X 1.09.2)      |
| `extendedCtcFrRules`  | The French EXTENDED-CTC-FR and BR-FR rules packaged in `paniolo-rules`              |
| `orderXRules`         | The Order-X 1.0 rules of FNFE-MPE and FeRD packaged in `paniolo-rules`              |
| `despatchAdviceRules` | `validateDespatchAdvice` checks the UBL schema only, information `PEPPOL-RULES-NOT-CHECKED` |
| `calculationCheck`    | Official rules only; with it, amounts are recalculated (stage `CALCULATION`, warnings only) |
| `ruleStatistics`      | `ValidationResult.statistics()` is null; with it, fired rules and PDF/A checks are counted |
| `pdfaScriptCache` | `REUSE`: veraPDF's compiled rules stay cached per validating thread; `CLEAR_AFTER_CHECK` releases them after each check; see [`paniolo-pdfa`](../paniolo-pdfa/README.md#compiled-rules-per-thread) |
| `unknownDocuments`    | `THROW`: undetectable documents raise `IllegalArgumentException`; `REPORT_INVALID` (Mustang compatible) returns an invalid result |

## Behavior

- **Thread safety**: an instance is immutable and thread-safe. Create it once, for example
  as a singleton or container bean, and share it across requests.
- **Lazy validation setup**: rules are extracted and each profile compiled on the first
  validation of that document type; later validations reuse it. `warmUp()` compiles the
  XRechnung rules for UBL and CII at start-up instead. Reading and writing compile nothing.
- **Detection**: the root element's namespace and name decide between UBL Invoice, UBL
  CreditNote, CII, and ZUGFeRD 1.0; the specification identifier (BT-24) selects the rules (XRechnung of the stated version,
  XRechnung extension, Factur-X profile; ZUGFeRD 1.0 has its own
  rules). A document of EN 16931:2026 (e.g. XRechnung 4.0) is not checked and gets the error
  `EN16931-2026-NOT-SUPPORTED`, because its official rules are not published yet; writing such an
  invoice is rejected. Unknown or malformed documents raise
  `IllegalArgumentException`; `validate(bytes, type)` reports malformed XML as a validation
  finding instead, and with `unknownDocuments(UnknownDocumentHandling.REPORT_INVALID)` the
  detecting `validate` methods report every undetectable document as invalid.
- **XRechnung identifiers**: BT-24 must exactly match a supported version and scenario for the
  document type. An identifier that states XRechnung but matches none produces the error
  `XR-SPECIFICATION-UNSUPPORTED`; findings from the configured rules remain visible.
- **Profile fallback**: standalone XML with the plain EN 16931 identifier, Peppol identifiers,
  or an unrecognized specification identifier uses the EN 16931 and XRechnung rules. Findings
  of the XRechnung CIUS are lowered to information, with `XR-NOT-STATED` explaining the change;
  Peppol rules retain their severity if the identifier states Peppol. Explicit
  `validate(bytes, type)` and `InvoiceValidator` keep the rules unchanged. For an EN 16931-only
  check, use `InvoiceValidator` with `ValidationProfile.en16931(bundle, syntax)`.
- **Errors**: `null` arguments raise `NullPointerException`, unprocessable content
  `IllegalArgumentException` with the reason; business rule violations are findings in the
  `ValidationResult`, never exceptions.
- **Streams** passed to `read` and `validate` are read completely and stay open.

The writers, readers, and the validator can also be used without the facade; see
[XML interfaces](docs/xml.md), [UBL](docs/ubl.md), [CII](docs/cii.md), and
[validation](docs/validation.md). The helper package `io.github.nalukit.paniolo.xml.util` is internal.

## Build and tests

```sh
mvn -pl paniolo -am verify
```

Tests write, detect, validate, and read back invoices and credit notes in both syntaxes
against the official rules, cover the XRechnung extension, lenient reading, despatch advices,
the behavior without `paniolo-facturx`, error handling and custom rule bundles, the calculation
check against its [corpus](src/test/resources/calculation-corpus/README.md), several allowances on
the gross price, and run one instance from several threads concurrently. PDF features and Order-X are tested in
[`paniolo-facturx`](../paniolo-facturx/README.md). The tests of the writers, readers, and the
validator run in the same build; [validation](docs/validation.md#build-and-tests) describes how to
test another KoSIT bundle.

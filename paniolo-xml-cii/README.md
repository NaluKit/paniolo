# Paniolo CII XML Writer and Reader

`paniolo-xml-cii` converts Paniolo's models into the UN/CEFACT Cross Industry XML formats and back.
It requires Java 21 and depends on [paniolo-xml](../paniolo-xml/README.md), which includes the
shared model.

| Class                                                | Documents                                                                                                                    |
|------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------|
| `CiiInvoiceWriter`, `CiiInvoiceReader`               | CII D16B `CrossIndustryInvoice`: XRechnung, EN 16931, and all Factur-X / ZUGFeRD 2.x profiles                                |
| `ZugferdV1InvoiceReader`                             | ZUGFeRD 1.0 `CrossIndustryDocument`, read into the invoice model; see [ZUGFeRD 1.0](../paniolo-facturx/README.md#zugferd-10) |
| `OrderXWriter`, `OrderXReader`                       | Order-X 1.0 orders, order changes, and order responses; see [Order-X](../paniolo-facturx/README.md#order-x)                  |
| `CiiDespatchAdviceWriter`, `CiiDespatchAdviceReader` | CIDA despatch advices (delivery notes); see [Delivery note (CIDA)](../paniolo-facturx/README.md#delivery-note-cida)          |

Applications using the [`Paniolo`](../paniolo/README.md) facade do not need these classes directly:
`toXml(invoice, InvoiceSyntax.CII)`, `read`, `migrateZugferd1`, `toOrderX`, `readOrder`,
`toCiiDespatchAdvice`, and `readDespatchAdvice` use them.

## Dependency

After installing Paniolo from the repository root with `mvn install`, add:

```xml
<dependency>
  <groupId>io.github.nalukit</groupId>
  <artifactId>paniolo-xml-cii</artifactId>
  <version>HEAD-SNAPSHOT</version>
</dependency>
```

## Usage

```java
import io.github.nalukit.paniolo.xml.InvoiceXmlWriter;
import io.github.nalukit.paniolo.xml.cii.CiiInvoiceWriter;

// Given an Invoice invoice:
InvoiceXmlWriter writer = new CiiInvoiceWriter();
String xml = writer.toXml(invoice);
byte[] utf8 = writer.toBytes(invoice);
```

Reading works the other way round:

```java
import io.github.nalukit.paniolo.model.Invoice;
import io.github.nalukit.paniolo.xml.InvoiceXmlReader;
import io.github.nalukit.paniolo.xml.cii.CiiInvoiceReader;

InvoiceXmlReader reader = new CiiInvoiceReader();
Invoice invoice = reader.fromXml(xml);
```

The reader maps supported fields and checks the line net amounts, total VAT, and amount due
against the model. It does not compare every stated total or guarantee lossless import of
external documents. `ReadMode.LENIENT` accepts the documented deviations; structural model
restrictions still apply. See [reading an invoice](../README.md#reading-an-invoice).

For the specification identifier of the Factur-X profile BASIC (`ProcessControl.facturXBasic()`),
the writer rejects invoices with content that profile does not allow and lists all such fields; see
[paniolo-facturx](../paniolo-facturx/README.md#what-is-written). With the specification identifier
of the profile EXTENDED, writer and reader also carry the EXTENDED content, e.g. several allowances
and charges on the gross price and further payment terms; see
[profile EXTENDED](../paniolo-facturx/README.md#profile-extended). The French profile
EXTENDED-CTC-FR uses the same model; the writer rejects the EXTENDED content it does not allow,
and writer and reader carry the multi-vendor invoices with sub-invoices; see
[EXTENDED-CTC-FR](../paniolo-facturx/README.md#profile-extended-ctc-fr).

`ZugferdV1InvoiceReader` reads ZUGFeRD 1.0 (`rsm:CrossIndustryDocument`) into the same model, with
the profile mapped to Factur-X (BASIC, COMFORT to EN 16931, EXTENDED); writing it with
`CiiInvoiceWriter` migrates the document. See
[ZUGFeRD 1.0](../paniolo-facturx/README.md#zugferd-10).

`OrderXWriter` and `OrderXReader` write and read Order-X 1.0 orders, order changes, and order
responses from the order model; see [Order-X](../paniolo-facturx/README.md#order-x).

`CiiDespatchAdviceWriter` and `CiiDespatchAdviceReader` write and read despatch advices as CIDA (Cross Industry Despatch
Advice), the delivery note the Mustang project embeds as `cida.xml`; see
[Delivery note (CIDA)](../paniolo-facturx/README.md#delivery-note-cida).

Writer and reader are stateless; create them once and share them between threads.

For invoice construction, see the [shared model](../paniolo-shared-model/README.md) and the
[complete invoice example](../README.md#generating-an-invoice).

Invoices and credit notes alike use the `CrossIndustryInvoice` root element; the type code tells
them apart:

| Type code            | Document                                                 |
|----------------------|----------------------------------------------------------|
| `380`                | Commercial invoice                                       |
| `381`                | Credit note                                              |
| other EN 16931 codes | e.g. `384` corrective invoice, `389` self-billed invoice |

The adapter writes document context, party details, electronic addresses, payment
instructions, delivery dates, notes, document allowances and charges, VAT breakdowns,
totals, and trade line items from the supported model fields. Totals are calculated from
the invoice model. Dates use CII date format `102` (`yyyyMMdd`).

## Validation and scope

Generating XML does not validate it. Use
[paniolo-validation](../paniolo-validation/README.md) with `ValidationProfile.Syntax.CII`
for both invoices and credit notes.

This module handles XML only. Embedding the XML in a PDF/A-3 (Factur-X, Order-X, CIDA delivery
note) is done by [paniolo-facturx](../paniolo-facturx/README.md). The model does not cover every
CII element; the XRechnung extension is supported in UBL only, as KoSIT defines it. See the
[project scope](../README.md#scope).

## Build and tests

Run from the repository root using JDK 21 or newer and Maven 3.9:

```sh
mvn -pl paniolo-xml-cii -am verify
```

Writer and reader tests live in `paniolo-validation` and cover XML escaping, Unicode, credit note
output, invalid XML characters, and write/read round trips. Run them with:

```sh
mvn -pl paniolo-validation -am verify
```

For official XSD and Schematron checks, follow the
[integration test instructions](../paniolo-validation/README.md#build-and-tests).

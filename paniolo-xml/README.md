# Paniolo XML

`paniolo-xml` contains the shared XML writer and reader interfaces and XML utilities used by Paniolo's
UBL and CII adapters. It requires Java 21 and depends on
[paniolo-shared-model](../paniolo-shared-model/README.md).

## Dependency

After installing Paniolo from the repository root with `mvn install`, add:

```xml
<dependency>
  <groupId>io.github.nalukit</groupId>
  <artifactId>paniolo-xml</artifactId>
  <version>1.0.0-rc01</version>
</dependency>
```

Applications normally depend on [paniolo-xml-ubl](../paniolo-xml-ubl/README.md) or
[paniolo-xml-cii](../paniolo-xml-cii/README.md), which include this module transitively.

## Writer interface

`io.github.nalukit.paniolo.xml.InvoiceXmlWriter` defines:

| Method             | Result                     |
|--------------------|----------------------------|
| `toXml(Invoice)`   | XML as a `String`          |
| `toBytes(Invoice)` | XML encoded as UTF-8 bytes |

This allows application code to use either syntax through the same interface:

```java
import io.github.nalukit.paniolo.model.Invoice;
import io.github.nalukit.paniolo.xml.InvoiceXmlWriter;

// Given an Invoice invoice and an InvoiceXmlWriter writer:
String xml = writer.toXml(invoice);
byte[] utf8 = writer.toBytes(invoice);
```

This module does not select a syntax or validate an invoice. Choose a concrete writer
and run [paniolo-validation](../paniolo-validation/README.md) separately.

## Reader interface

`io.github.nalukit.paniolo.xml.InvoiceXmlReader` defines:

| Method                                | Result                                       |
|---------------------------------------|----------------------------------------------|
| `read(Document)`                      | `Invoice` from a parsed DOM document         |
| `read(Document, ReadMode)`            | `ReadResult` with invoice and `ReadWarning`s |
| `fromXml(String[, ReadMode])`         | from XML characters                          |
| `fromBytes(byte[][, ReadMode])`       | from encoded XML                             |
| `fromStream(InputStream[, ReadMode])` | from a stream, which stays open              |

Malformed XML and content the model cannot represent raise `IllegalArgumentException`;
I/O failures raise `UncheckedIOException`. Readers recalculate totals. In `ReadMode.STRICT`, the
default, they reject documents whose stated VAT total or amount due differ, or whose line net
amount is not quantity x net price. `ReadMode.LENIENT` recalculates the net price of such lines,
keeps the calculated totals, drops allowance reason codes outside UNTDID 5189 that come with a
reason text and percentages without base amount, keeps only the first of differing payment means
blocks, turns ZUGFeRD 1.0 text-only lines into notes, and reports each deviation as a
`ReadWarning`: `READ-LINE-NET-PRICE`, `READ-TAX-TOTAL`, `READ-PAYABLE-AMOUNT`, `READ-REASON-CODE`,
`READ-PERCENTAGE`, `READ-PAYMENT-MEANS`, and `READ-TEXT-LINE`. A tax of another type than VAT
(CII `TypeCode`, UBL `TaxScheme/ID`, e.g. `AAM`) is never read as VAT: strict reading rejects it,
lenient reading reports it as `READ-OTHER-TAX` and leaves it out.

Both modes report values the model does not keep or interpret, without changing an amount:
`READ-CASH-DISCOUNT` (a payment terms line that looks like a cash discount, kept as text),
`READ-IDENTIFIER` (a further identifier of a business term with one identifier), and
`READ-SPECIFICATION-VERSION` (a document of EN 16931:2026, read with the business terms of
EN 16931:2017). The writers reject invoices whose specification identifier states EN 16931:2026.

`ReadResult.stated()` retains the stated amounts and `deviations()` lists differences from the
model, including totals not compared by strict reading. `isExact()` checks only whether there
are read warnings; it does not guarantee field-level or byte-level fidelity. Implementations
provide `read(Document, ReadMode)`; the other read methods delegate to it.

`importXml(byte[], ReadMode)` returns an `ImportResult` with
both the read result and the original XML. `ImportResult.export(ExportVariant.ORIGINAL, null)`
returns those original bytes; `NORMALIZED` takes an `InvoiceXmlWriter` and writes the model again.
It is refused after `READ-OTHER-TAX`, as it would drop that tax and change the amount due;
`NORMALIZED_DROPPING_OTHER_TAXES` accepts the loss explicitly.
The facade exposes this through `importInvoice` and `export`; see
[importing received invoices](../README.md#importing-received-invoices-with-their-original-amounts).

Implementations of both interfaces are stateless and can be shared between threads.
Each call creates its own DOM document, parser, and transformer.

## XML utilities

The package `io.github.nalukit.paniolo.xml.util` holds internal helpers for the syntax adapters:
`XmlBuilder` writes and `XmlElement` navigates namespace-aware DOM trees, and
`InvoiceConsistency` completes tax categories from the VAT breakdown, and `ReadContext` collects
the warnings of one read and holds the shared line and total checks. Applications should not depend on them.
`XmlDocuments` creates and parses namespace-aware DOM documents. Parsing disables DTDs,
external entities, and XInclude. The `InputStream` and `Reader` overloads leave the
caller-owned input open.

`XmlBuilder` is an internal writing utility shared by the syntax adapters. It manages
namespaces, escapes text, and serializes XML as UTF-8. Applications generally do not need
to use it directly.

## Build and tests

Run from the repository root using JDK 21 or newer and Maven 3.9:

```sh
mvn -pl paniolo-xml -am verify
```

XML parsing and writer behavior are tested by the downstream validation module. To run
those tests together with this module and both adapters:

```sh
mvn -pl paniolo-validation -am verify
```

See the [project README](../README.md) for the complete generation and validation workflow.

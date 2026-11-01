# Paniolo UBL XML Writer and Reader

`paniolo-xml-ubl` converts Paniolo's models into UBL 2.1 XML and back. It requires Java 21
and depends on [paniolo-xml](../paniolo-xml/README.md), which includes the shared model.

| Class                                          | Documents                                                                                                            |
|------------------------------------------------|----------------------------------------------------------------------------------------------------------------------|
| `UblInvoiceWriter`, `UblInvoiceReader`         | UBL `Invoice` and `CreditNote`, incl. the [XRechnung extension](#xrechnung-extension)                                |
| `DespatchAdviceWriter`, `DespatchAdviceReader` | UBL `DespatchAdvice` after Peppol BIS Despatch Advice 3 or as 1Lieferschein; see [despatch advice](#despatch-advice) |

Applications using the [`Paniolo`](../paniolo/README.md) facade do not need these classes directly:
`toXml(invoice, InvoiceSyntax.UBL)`, `read(xml)`, `toDespatchAdvice(advice)`, and
`readDespatchAdvice(xml)` use them.

## Dependency

After installing Paniolo from the repository root with `mvn install`, add:

```xml
<dependency>
  <groupId>io.github.nalukit</groupId>
  <artifactId>paniolo-xml-ubl</artifactId>
  <version>HEAD-SNAPSHOT</version>
</dependency>
```

## Usage

```java
import io.github.nalukit.paniolo.xml.InvoiceXmlWriter;
import io.github.nalukit.paniolo.xml.ubl.UblInvoiceWriter;

// Given an Invoice invoice:
InvoiceXmlWriter writer = new UblInvoiceWriter();
String xml = writer.toXml(invoice);
byte[] utf8 = writer.toBytes(invoice);
```

Reading works the other way round:

```java
import io.github.nalukit.paniolo.model.Invoice;
import io.github.nalukit.paniolo.xml.InvoiceXmlReader;
import io.github.nalukit.paniolo.xml.ubl.UblInvoiceReader;

InvoiceXmlReader reader = new UblInvoiceReader();
Invoice invoice = reader.fromXml(xml);
```

The reader maps supported fields and checks the line net amounts, total VAT, and amount due
against the model. It does not compare every stated total or guarantee lossless import of
external documents. `ReadMode.LENIENT` accepts the documented deviations; structural model
restrictions still apply. See [reading an invoice](../README.md#reading-an-invoice).

Writer and reader are stateless; create them once and share them between threads.

For invoice construction, see the [shared model](../paniolo-shared-model/README.md) and the
[complete invoice example](../README.md#generating-an-invoice).

The writer selects the document structure from the invoice type:

| Type code                                                                                       | UBL document | Line element     |
|-------------------------------------------------------------------------------------------------|--------------|------------------|
| Credit note codes (`isCreditNote()`: 83, 261, 262, 296, 308, 381, 396, 420, 458, 502, 503, 532) | `CreditNote` | `CreditNoteLine` |
| All other EN 16931 codes, e.g. `380`, `384`, `389`, `875`-`877`                                 | `Invoice`    | `InvoiceLine`    |

The adapter writes party details, electronic addresses, payment instructions, delivery
dates, notes, document allowances and charges, VAT breakdowns, totals, and invoice lines
from the supported model fields. Totals are calculated from the invoice model.

UBL CreditNote has no document-level `DueDate` element. When a credit note has a due date
in the model, the writer includes it as text in the payment terms. UBL Invoice writes it
as a date element.

## Validation

Generating XML does not validate it. Use
[paniolo-validation](../paniolo-validation/README.md) with `ValidationProfile.Syntax.UBL_INVOICE`
for invoices or `ValidationProfile.Syntax.UBL_CREDIT_NOTE` for credit notes.
The profile must match the generated document type.

The adapter covers the current model subset rather than every UBL element. See the
[project scope](../README.md#scope).

## XRechnung extension

With the specification identifier of the XRechnung extension (`ProcessControl.xrechnungExtension()`,
`ProcessControl.XRECHNUNG_3_EXTENSION`) the UBL Invoice writer and reader carry:

| Information                                                                    | UBL                                                                       |
|--------------------------------------------------------------------------------|---------------------------------------------------------------------------|
| sub invoice lines (BG-DEX-01), nested to any depth, each with its VAT category | `cac:SubInvoiceLine` with the structure of `cac:InvoiceLine`              |
| third party payments (BG-DEX-09): type, amount, description                    | `cac:PrepaidPayment` with `cbc:ID`, `cbc:PaidAmount`, `cbc:InstructionID` |
| XML attachments (BR-DEX-01)                                                    | `cbc:EmbeddedDocumentBinaryObject` with `application/xml`                 |

- The net amount of a line with sub-lines is the sum of its direct sub-lines (BR-DEX-02); the
  model checks it. Only the invoice lines count in the totals; sub-lines break them down.
- Third party payments are added to the amount due (BR-DEX-09).
- `paniolo.validate` uses the KoSIT scenario "EN16931 XRechnung Extension" for documents with
  this identifier. It turns BR-CO-16 and the code list rules of the further code values into
  information, as KoSIT does.
- KoSIT defines the extension for UBL Invoice only: CII and UBL CreditNote reject sub-lines,
  third party payments, and XML attachments. Other specification identifiers reject them, too.
- The further code values for identifier schemes (BR-DEX-04 to BR-DEX-08) need no change, as the
  model does not restrict scheme identifiers.

## Despatch advice

`DespatchAdviceWriter` and `DespatchAdviceReader` write and read despatch advices (delivery notes)
as UBL 2.1 `DespatchAdvice` after Peppol BIS Despatch Advice 3, from the model `DespatchAdvice`,
`DespatchLine`, and `DespatchItemInstance` in `io.github.nalukit.paniolo.model.despatch`:

```java
DespatchAdvice advice = DespatchAdvice.create()
                                      .number("LS-2026-42")
                                      .issueDate(LocalDate.of(2026, 11, 9))
                                      .addOrderReference("B-2026-17")
                                      .despatchSupplier(supplier)       // electronic address = Peppol endpoint
                                      .deliveryCustomer(customer)
                                      .buyer(buyer)
                                      .shipmentId("SH-9")
                                      .actualDespatchDate(LocalDate.of(2026, 11, 9))
                                      .addLine(DespatchLine.create()
                                                           .id("1")
                                                           .orderLineId("1")
                                                           .itemName("Gerät")
                                                           .deliveredQuantity(new BigDecimal("8"))
                                                           .unitCode("H87")
                                                           .addInstance(DespatchItemInstance.serial("SN-1"))
                                                           .build())
                                      .build();

String xml = paniolo.toDespatchAdvice(advice);
DespatchAdvice read = paniolo.readDespatchAdvice(xml.getBytes(StandardCharsets.UTF_8));
ValidationResult result = paniolo.validateDespatchAdvice(xml.getBytes(StandardCharsets.UTF_8));
```

| Information                                                                                                                                                                                                             | UBL                                                                            |
|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------|
| number, issue date, note, orders, further documents (with attachment or URI)                                                                                                                                            | `cbc:ID`, `IssueDate`, `Note`, `OrderReference`, `AdditionalDocumentReference` |
| despatch supplier and delivery customer: endpoint, identifier, address, legal name, contact                                                                                                                             | `DespatchSupplierParty`, `DeliveryCustomerParty` (`DeliveryContact`)           |
| buyer, seller, originator: identifier, name, address                                                                                                                                                                    | `BuyerCustomerParty`, `SellerSupplierParty`, `OriginatorCustomerParty`         |
| shipment: identifier, information, gross weight and volume, handling unit count, transport mode, tracking number, estimated delivery period, despatch date and address                                                  | `Shipment`                                                                     |
| lines: delivered and outstanding quantity with reason, order line, item name and identifiers, classifications, properties, serial numbers, lots with expiry, best before and manufacture dates, transport handling unit | `DespatchLine`                                                                 |

- Party details Peppol has no place for (VAT identifier, tax number, trading name, legal
  registration, contacts and electronic addresses of buyer, seller, and originator) are rejected.
- Reading is strict like the Order-X reader: content the model would lose, such as hazardous items
  or package dimensions, is rejected with its path; `DespatchAdviceReader.lenient()` or
  `paniolo.readDespatchAdvice(xml, true)` ignore it.
- `paniolo.validateDespatchAdvice` checks the UBL 2.1 schema from the KoSIT bundle. The Peppol BIS
  Despatch Advice Schematron is not distributed with Paniolo (no license is published); pass its
  compiled XSLT with `Paniolo.builder().despatchAdviceRules(path)`, otherwise the result carries the
  information `PEPPOL-RULES-NOT-CHECKED`.
- Shipment details (information, weight, volume, handling units, transport mode, tracking number,
  delivery period, despatch date and address) need the shipment identifier, and each line needs
  its order line; the writer rejects them without it. Both are optional in the model for CIDA.
- Not supported: hazardous items, package and handling unit details beyond identifier and type,
  carrier and delivery parties, and times.
- **1Lieferschein**: `DespatchAdvice.create().oneLieferschein()` sets the identifiers of
  1Lieferschein (`CustomizationID` `1Lieferschein`, `ProfileID` `ubl-xml-only`, as the Mustang
  project writes them). The UBL is the same as for Peppol, except that the actual delivery date
  (`Shipment/Delivery/ActualDeliveryDate`, rejected for Peppol and CIDA) is written, and a line
  shipment only for a transport handling unit. 1Lieferschein has no published rules:
  `validateDespatchAdvice` checks the UBL schema, never the Peppol rules, and reports the
  information `1LIEFERSCHEIN-NOT-CHECKED`. The 1Lieferschein of the Mustang project puts party
  names in address lines and has no post code; it cannot be read into the model.
- The same model is also written as a CIDA delivery note in XML and PDF/A-3; see
  [Delivery note (CIDA)](../paniolo-facturx/README.md#delivery-note-cida).

## Build and tests

Run from the repository root using JDK 21 or newer and Maven 3.9:

```sh
mvn -pl paniolo-xml-ubl -am verify
```

Writer and reader tests live in `paniolo-validation` and cover XML escaping, Unicode, credit note
output, invalid XML characters, and write/read round trips. Run them with:

```sh
mvn -pl paniolo-validation -am verify
```

For official XSD and Schematron checks, follow the
[integration test instructions](../paniolo-validation/README.md#build-and-tests).

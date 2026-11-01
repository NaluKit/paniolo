# Paniolo Factur-X / ZUGFeRD

`paniolo-facturx` adds PDF support to the [`Paniolo`](../paniolo/README.md) facade. It depends on
`paniolo` and Apache PDFBox 3 (Apache License 2.0) and is found through `java.util.ServiceLoader`:
add the dependency and the facade methods below work, there is nothing to configure. Applications
without it keep a PDF-free class path.

| Feature                                                           | Facade methods                                                                  |
|-------------------------------------------------------------------|---------------------------------------------------------------------------------|
| Factur-X / ZUGFeRD 2.x invoices in all six profiles, PDF/A-3      | `toFacturX`, `read(pdf)`, `extractXml`                                          |
| Validation of the container, PDF/A (with `paniolo-pdfa`), and XML | `validate(pdf)`                                                                 |
| Further embedded files, e.g. time sheets                          | `toFacturX(..., attachments)`, `attachments`                                    |
| Software that produced a PDF                                      | `producer`                                                                      |
| ZUGFeRD 1.0 PDFs                                                  | `read(pdf)`, `migrateZugferd1`                                                  |
| Order-X PDFs (`order-x.xml`)                                      | `toOrderXPdf`, `readOrder(pdf)`, `validateOrder(pdf)`                           |
| PDF delivery notes (`cida.xml`)                                   | `toDespatchAdvicePdf`, `readDespatchAdvice(pdf)`, `validateDespatchAdvice(pdf)` |

The visual part comes from you as a PDF/A-1, -2, or -3 document; Paniolo adds the XML, the
association, and the XMP metadata. Without a PDF of your own,
[`paniolo-pdf-layout`](../paniolo-pdf-layout/README.md) renders the pages from the model and uses
this module to embed the XML (`toFacturX(invoice, profile)`, `toOrderXPdf(order)`,
`toDespatchAdvicePdf(advice)`).

## Dependency

```xml
<dependency>
  <groupId>io.github.nalukit</groupId>
  <artifactId>paniolo-facturx</artifactId>
  <version>HEAD-SNAPSHOT</version>
</dependency>
```

## Usage

```java
Paniolo paniolo = Paniolo.create();

Invoice invoice = Invoice.create()
                         .processControl(ProcessControl.en16931())   // or facturXBasic(), facturXExtended(), xrechnung()
                         /* seller, buyer, lines, ... */
                         .build();

byte[] facturX = paniolo.toFacturX(invoice, pdfA, FacturXProfile.EN16931);
Invoice read = paniolo.read(facturX);
var result = paniolo.validate(facturX);
```

`toFacturX` rejects an invoice whose specification identifier (BT-24) does not match the
profile, an invoice with lines for MINIMUM or BASIC WL (and one without lines for the other
profiles), a PDF that is not PDF/A, an encrypted PDF, and a PDF that already contains an invoice;
ZUGFeRD 1.0 PDFs are converted with `migrateZugferd1`. MINIMUM and BASIC WL are embedded with the
relationship `Data`, the other profiles with `Alternative`, as required in Germany. See
[profiles without lines](../README.md#profiles-without-lines-minimum-and-basic-wl) for building such
invoices.

Existing CII XML, e.g. from another system, is embedded unchanged with
`paniolo.embedXml(pdfA, xml, profile)`. Before embedding, it checks that the XML is CII and
that BT-24 matches the profile. Business-rule violations do not prevent embedding: the returned
`FacturXResult` carries the PDF and its validation (container, optional PDF/A, XML), which the
caller must inspect.
`extractXml` returns exactly the embedded bytes.

## What is written

Following the Factur-X / ZUGFeRD 2 specification for Germany (container and XMP as defined since
Factur-X 1.07 / ZUGFeRD 2.3; the written XML is tested against the packaged official Factur-X 1.09.2 rules):

| Item                        | `BASIC`                                                       | `EN16931`                 | `EXTENDED`                                                        | `XRECHNUNG`                                            |
|-----------------------------|---------------------------------------------------------------|---------------------------|-------------------------------------------------------------------|--------------------------------------------------------|
| Embedded file (`/F`, `/UF`) | `factur-x.xml`                                                | `factur-x.xml`            | `factur-x.xml`                                                    | `xrechnung.xml`                                        |
| `AFRelationship`            | `Alternative`                                                 | `Alternative`             | `Alternative`                                                     | `Alternative`                                          |
| `fx:ConformanceLevel`       | `BASIC`                                                       | `EN 16931`                | `EXTENDED`                                                        | `XRECHNUNG`                                            |
| `fx:Version`                | `1.0`                                                         | `1.0`                     | `1.0`                                                             | `3.0` (XRechnung version, as in the official examples) |
| BT-24                       | `urn:cen.eu:en16931:2017#compliant#urn:factur-x.eu:1p0:basic` | `urn:cen.eu:en16931:2017` | `urn:cen.eu:en16931:2017#conformant#urn:factur-x.eu:1p0:extended` | XRechnung 3.0 CIUS identifier                          |

Profile BASIC is a subset of EN 16931. The CII writer rejects an invoice with content the BASIC
schema does not allow and names every such field: seller and buyer contacts (BG-6, BG-9), seller
additional legal information (BT-33), project, sales order, receiving advice, tender, and object
references (BT-11, BT-14, BT-15, BT-17, BT-18), supporting documents (BG-24), VAT point date (BT-7),
rounding amount (BT-114), payment means text (BT-82), payment card (BG-18), account name (BT-85),
BIC (BT-86), and per line the object identifier, order line reference, accounting reference, item
description, seller's and buyer's item identifiers, classification, country of origin, and
attributes (BT-128, BT-132, BT-133, BT-154 to BT-156, BT-158, BT-159, BG-32).

- The XML is embedded with MIME type `text/xml`, size, creation and modification date, listed
  in the document's `EmbeddedFiles` name tree and in the catalog's `/AF` array. Existing
  attachments are kept.
- The XMP metadata is rewritten: `pdfaid:part` becomes 3 (the conformance letter A, B, or U of
  the input is kept), the `fx:` properties and the PDF/A extension schema for the Factur-X
  namespace `urn:factur-x:pdfa:CrossIndustryDocument:invoice:1p0#` are added, and title,
  author, subject, keywords, creator, producer, and dates are mirrored from the document
  information dictionary, as PDF/A requires. Other XMP content of the input is not carried over.
- The producer gets the signature `(via Paniolo)`, e.g. `Microsoft Word (via Paniolo)`, or
  `Paniolo (via Paniolo)` without producer, so that validators recognize the software.
- If the input has no output intent, an sRGB output intent with the JDK's ICC profile is added.
  The PDF version is raised to 1.7 if lower.
- The embedded files are stored Flate-compressed and the PDF is saved with object streams
  (PDFBox `CompressParameters.DEFAULT_COMPRESSION`), which PDF/A-3 allows. The streams of the
  given PDF are taken as they are: a compressed PDF stays compressed, an uncompressed one is not
  compressed afterwards. The same applies to `embedXml`, Order-X, CIDA, and migrated ZUGFeRD 1.0
  PDFs.

## Attachments

Further files, such as a time sheet or the delivery note, can be embedded next to the invoice XML
and read back:

```java
var timeSheet = new PdfAttachment("stundenzettel.pdf",
                                  timeSheetPdf,
                                  "application/pdf",
                                  AfRelationship.SUPPLEMENT,
                                  "Stundenzettel Oktober");
byte[] facturX = paniolo.toFacturX(invoice, pdfA, FacturXProfile.EN16931, List.of(timeSheet));

List<PdfAttachment> attachments = paniolo.attachments(facturX);   // all files except the invoice XML
```

- Each attachment is embedded like the invoice: in the `EmbeddedFiles` name tree and the catalog's
  `/AF` array, with MIME type, size, dates, `AFRelationship` (`AfRelationship`: Source, Data,
  Alternative, Supplement, Unspecified), and optional description, so the result stays PDF/A-3.
  PDF/A-3 allows any file type.
- File names must be unique in the PDF, including attachments of the input PDF, which are kept;
  the invoice file names (`factur-x.xml`, `xrechnung.xml`, `zugferd-invoice.xml`) and paths are
  rejected, and a MIME type is required.
- `PdfAttachment.of(attachment)` takes the attachment of a supporting document (BG-24) as
  supplementary file; the invoice XML keeps its own copy.
- `attachments(pdf)` also returns files of other producers: names lose any path, invalid MIME types
  become `application/octet-stream`, a missing relationship becomes `UNSPECIFIED`. Files larger
  than 100 MB are rejected.

## What is read

The reader looks for `factur-x.xml`, `xrechnung.xml`, and `zugferd-invoice.xml` (ZUGFeRD 2.0)
in the `/AF` array and the `EmbeddedFiles` name tree, preferring the name declared in
`fx:DocumentFileName`. The profile comes from `fx:ConformanceLevel`, otherwise from the file
name `xrechnung.xml` or the specification identifier (BT-24) of the invoice, including the
ZUGFeRD 2.0 identifiers such as `urn:zugferd.de:2p0:basic`. ZUGFeRD 1.0 (`ZUGFeRD-invoice.xml`) is
read and can be migrated, see below. Embedded XML
larger than 20 MB is rejected. MINIMUM and BASIC WL documents are read into invoices without
lines whose `statedTotals()` carry the totals and the VAT breakdown.

## ZUGFeRD 1.0

ZUGFeRD 1.0 documents (`rsm:CrossIndustryDocument`, a customised CII 2013b) are read into the
invoice model and can be migrated to ZUGFeRD 2 / Factur-X:

```java
Invoice invoice = paniolo.read(zugferd1XmlOrPdf);   // like any other invoice
byte[] xml = paniolo.migrateZugferd1(zugferd1Xml);  // CII XML
byte[] pdf = paniolo.migrateZugferd1(zugferd1Pdf);  // Factur-X PDF/A-3
byte[] extended = paniolo.migrateZugferd1(zugferd1Xml, FacturXProfile.EXTENDED);
```

| ZUGFeRD 1.0 | Factur-X / ZUGFeRD 2 |
|-------------|----------------------|
| BASIC       | BASIC                |
| COMFORT     | EN 16931             |
| EXTENDED    | EXTENDED             |

- A PDF keeps its visual part and its other attachments; `ZUGFeRD-invoice.xml` is replaced by
  `factur-x.xml` and the XMP metadata is rewritten for Factur-X, as when creating a document.
- An explicit target (BASIC, EN 16931, or EXTENDED) helps when the content does not fit the
  corresponding profile, e.g. a BASIC document with contacts.
- Outside EXTENDED, logistics service charges become document charges and cash discount terms
  become `CashDiscount`s (`#SKONTO#` in BT-20). Late payment penalties and partial payments need the
  target EXTENDED. Notes with several texts are joined. Allowances and charges without reason get
  "Allowance" or "Charge", as EN 16931 requires a reason. BASIC lines with only an amount get one
  price for the whole quantity.
- Rejected: direct debits (ZUGFeRD 1.0 carries no mandate reference), charges on the gross price,
  and totals that do not match the content.
- ZUGFeRD 1.0 itself is validated against its schema and Schematron (packaged in
  `paniolo-facturx-rules`), XML and PDF alike. The result carries the warning `FX-ZUGFERD-1`,
  because ZUGFeRD 1.0 does not meet EN 16931; elements the Schematron reports as not used are
  information. Validate the migrated document to check it as Factur-X.

The reader is `ZugferdV1InvoiceReader` in `paniolo-xml-cii`.

## Order-X

Orders, order changes, and order responses use their own model, `Order` and `OrderLine` in
`io.github.nalukit.paniolo.model.order`, which shares parties, addresses, units, and VAT categories
with invoices. They are written as Order-X 1.0 (UN/CEFACT SCRDM CIO D20B) and can be embedded into
PDF/A-3 as `order-x.xml`:

```java
Order order = Order.create()
                   .number("B-2026-17")
                   .issueDate(LocalDate.of(2026, 10, 28))
                   .typeCode(OrderTypeCode.ORDER)          // ORDER_CHANGE, ORDER_RESPONSE
                   .profile(OrderXProfile.COMFORT)         // BASIC by default
                   .seller(seller)
                   .buyer(buyer)
                   .addLine(OrderLine.create()
                                     .id("1")
                                     .name("Gerät")
                                     .quantity(BigDecimal.TEN)
                                     .unitCode("H87")
                                     .netUnitPrice(new BigDecimal("100.00"))
                                     .build())
                   .build();

String xml = paniolo.toOrderX(order);
byte[] pdf = paniolo.toOrderXPdf(order, pdfA);     // optional attachments as for invoices
Order read = paniolo.readOrder(pdf);               // XML or PDF
var result = paniolo.validateOrder(pdf);           // PDF/A and XML
```

| Information                                                                                                                                                                       | Profile |
|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------|
| number, type (220 order, 230 change, 231 response), issue date, purpose, requested response, status, notes                                                                        | BASIC   |
| seller, buyer, deliver-to and ship-from party, buyer reference, delivery terms                                                                                                    | BASIC   |
| buyer order, quotation, contract, blanket order, previous order / change / response                                                                                               | BASIC   |
| requested delivery and despatch date, currency, buyer accounting reference                                                                                                        | BASIC   |
| lines: identifiers, name, requested and agreed quantity, unit, net price, partial delivery, status, note                                                                          | BASIC   |
| buyer order line, blanket order line, substituted product without description                                                                                                     | BASIC   |
| invoicee, buyer requisitioner, seller order, requisition, catalogue, and additional documents, project                                                                            | COMFORT |
| effective period, payment means with text, payment terms, document allowances and charges                                                                                         | COMFORT |
| contacts with department and contact type (`Party.extended()`), party description                                                                                                 | COMFORT |
| lines: gross price and discount, VAT category and rate, allowances and charges, description, accounting reference                                                                 | COMFORT |
| lines: batch, brand, attributes and typed characteristics, classifications with class name, product instances, packaging type, country of origin, substituted product description | COMFORT |
| lines: quotation and catalogue reference, package quantities, requested delivery and despatch date                                                                                | COMFORT |
| `Order.details()`: test and copy indicator, business process, document name, times of day, requested delivery and despatch periods, delivery terms function                       | BASIC   |
| `Order.details()`: stated totals of a response without lines; `PartyExtension` global identifier next to `ram:ID`; `OrderLineDetails` note subject                                | BASIC   |
| `Order.details()`: delivery terms description and location, further referenced documents with type codes                                                                          | COMFORT |
| `OrderLine.details()`: gross price allowances and charges with reason, delivery and despatch periods, package quantity unit and dimensions, line and product documents            | COMFORT |

- The writer rejects COMFORT content for the profile BASIC and names every such field. EXTENDED
  is written with the same content as COMFORT.
- Order-X knows no VAT breakdown, no VAT exemption reason, no dates in references, no measured
  characteristics, and no fax; the model or the writer rejects them. VAT (`TaxTotalAmount`,
  `GrandTotalAmount`) is written only when every line has a VAT category.
- Reading is strict: the reader writes the order again and compares it with the document (paths and
  normalized values, counting repetitions). Content the model would lose, such as an unsupported
  element or a stated line amount that is not quantity x price, is rejected with its path.
  `paniolo.readOrder(xmlOrPdf, true)` or `OrderXReader.lenient()` ignore it and also accept stated
  totals the model does not reproduce, e.g. of a response without lines.
- Dates are read and written in the formats 102, 203, and 204 (date, date with time of day); the
  time of day is kept in `OrderDetails` and `OrderPeriod`. An electronic address without scheme,
  which Order-X allows, keeps a null scheme; invoices and despatch advices still require it.
- The line amount is quantity x net price, or the agreed quantity x net price if the line states one,
  as in order responses. A response line may name only the substituted product.
- All 24 XML examples of the official release are read by `readOrder(xmlOrPdf, true)`, and the orders
  written again pass the official rules. 19 of them are read strictly as well, without any loss;
  the other five state allowance and charge totals without the allowances and charges themselves (e.g.
  `ORDER-X_EX01_ORDER_FULL_DATA-BASIC`), so their totals cannot be reproduced and strict
  reading rejects them.
- The XMP metadata uses the namespace `urn:factur-x:pdfa:CrossIndustryDocument:1p0#` with
  `fx:DocumentType` ORDER, ORDER_CHANGE, or ORDER_RESPONSE and the profile as conformance level.
  A PDF carries either an invoice, an order, or a delivery note.
- `validateOrder` checks the XML against the official Order-X 1.0 rules of FNFE-MPE and FeRD (2021-04-13, Apache License 2.0
  according to their terms of use), packaged in
  `paniolo-facturx-rules`. The release package holds the Schematron and a stylesheet compiled with
  oXygen that reports with `xsl:message`; Paniolo packs the official XSDs and Schematrons and the
  Schematron compiled to SVRL by `scripts/package-orderx-rules.sh`. All 24 XML examples of the
  release (BASIC and COMFORT) and their PDFs pass. `Paniolo.builder().orderXRules(path)` uses
  another release; its Schematron must be compiled to SVRL the same way.
- Not supported: the Order-X EXTENDED details (EXTENDED is written with the COMFORT content),
  sequence numbers of allowances and charges, repeated delivery events, more than one further
  party identifier, attachments of line and product documents, and container checks for Order-X
  PDFs. The strict reader reports such content. Delivery notes are CIDA
  documents; see [Delivery note (CIDA)](#delivery-note-cida).

## Delivery note (CIDA)

A despatch advice (`DespatchAdvice` in `io.github.nalukit.paniolo.model.despatch`, see
[despatch advice](../paniolo-xml-ubl/README.md#despatch-advice)) can also be written as UN/CEFACT
Cross Industry Despatch Advice (CIDA, `SCRDMCCBDACIDAMessageStructure`) and embedded into PDF/A-3
as `cida.xml`, the digital delivery note that the Mustang project calls Deliver-X:

```java
String xml = paniolo.toCiiDespatchAdvice(advice);
byte[] pdf = paniolo.toDespatchAdvicePdf(advice, pdfA);            // optionally with attachments
DespatchAdvice read = paniolo.readDespatchAdvice(pdf);             // UBL, CIDA XML, or PDF
ValidationResult result = paniolo.validateDespatchAdvice(pdf);
```

| Information                                                                                                                      | CIDA element                                                                                                    |
|----------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------|
| number, issue date, note                                                                                                         | `ExchangedDocument` (`TypeCode` 351)                                                                            |
| seller (the despatch supplier unless another seller is given), buyer, originator                                                 | `SellerTradeParty`, `BuyerTradeParty`, `BuyerRequisitionerTradeParty`                                           |
| order, further documents (with attachment or URI)                                                                                | `BuyerOrderReferencedDocument`, `AdditionalReferencedDocument`                                                  |
| delivery customer, despatch supplier (if another seller is given), despatch date                                                 | `ShipToTradeParty`, `ShipFromTradeParty`, `ActualDespatchSupplyChainEvent`                                      |
| lines: despatched quantity, order and order line, item name and identifiers, classifications, properties, serial and lot numbers | `DespatchedQuantity`, `BuyerOrderReferencedDocument`, `SpecifiedTradeProduct`, `IndividualTradeProductInstance` |

- No schema or rules of CIDA are published. Namespaces, the guideline
  `urn:awv-net.de:CIDA:1.0:pilot` (as business process), and the XMP metadata
  (`urn:factur-x:pdfa:CrossIndustryDocument:despatchadvice:1p0#`, `fx:DocumentType` DESPATCHADVICE,
  version 1.0, conformance level PILOT, relationship `Alternative`) follow the Mustang project,
  which reads these PDFs. Elements beyond its subset follow Order-X (SCRDM D20B).
- `validateDespatchAdvice` therefore only reads CIDA with the model and checks the PDF/A conformance
  of a PDF (with `paniolo-pdfa`); the result carries the warning `CIDA-NOT-CHECKED`, or `CIDA-READ`
  if the document cannot be read.
- Content CIDA has no element for is rejected when writing: shipment identifier and details (information, weight, volume,
  handling units, transport mode, tracking number, estimated delivery
  period, despatch address), outstanding quantities, sales order lines, handling units, dates of
  item instances, more than one order reference, and customization and profile identifiers other
  than Peppol.
- Reading is strict like the Order-X reader: content the model does not carry, such as line amounts,
  is rejected with its path; `CiiDespatchAdviceReader.lenient()` or
  `paniolo.readDespatchAdvice(xmlOrPdf, true)` ignore it. The root element may be unqualified, as
  the Mustang project writes it, so delivery notes of the Mustang project are read with
  `readDespatchAdvice(xmlOrPdf, true)`; without a ship-to party the buyer is the delivery customer.
- The order line of a line (`BuyerOrderReferencedDocument/LineID`) is optional in CIDA; Peppol UBL
  requires it, so writing such an advice as UBL is rejected.

## Profile EXTENDED

Profile EXTENDED (`ProcessControl.facturXExtended()`) writes the invoice like EN 16931 plus the
header information in `Invoice.extended()` (`ExtendedInformation` in
`io.github.nalukit.paniolo.model.extended`):

| Information                                                                                                         | CII element                                                                                                                                                                |
|---------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| sales agent, buyer agent, buyer's tax representative (with VAT identifier), product end user                        | `SalesAgentTradeParty`, `BuyerAgentTradeParty`, `BuyerTaxRepresentativeTradeParty`, `ProductEndUserTradeParty`                                                             |
| ship-from party, ultimate ship-to party                                                                             | `ShipFromTradeParty`, `UltimateShipToTradeParty`                                                                                                                           |
| invoicer, invoicee, payer                                                                                           | `InvoicerTradeParty`, `InvoiceeTradeParty`, `PayerTradeParty`                                                                                                              |
| delivery terms (e.g. Incoterms `FCA`)                                                                               | `ApplicableTradeDeliveryTerms`                                                                                                                                             |
| quotation, delivery note, ultimate customer orders (with optional date)                                             | `QuotationReferencedDocument`, `DeliveryNoteReferencedDocument`, `UltimateCustomerOrderReferencedDocument`                                                                 |
| structured payment terms: partial amounts, cash discount, late payment penalty, own payee (`PaymentTerm.payee`)     | further `SpecifiedTradePaymentTerms`                                                                                                                                       |
| logistics service charges (freight, packaging)                                                                      | `SpecifiedLogisticsServiceCharge`                                                                                                                                          |
| advance payments                                                                                                    | `SpecifiedAdvancePayment`                                                                                                                                                  |
| document name, language (ISO 639-2, e.g. `deu`), copy and test indicator, effective date                            | `Name`, `LanguageID`, `CopyIndicator`, `TestIndicator`, `EffectiveSpecifiedPeriod`                                                                                         |
| invoice number of the invoicer, transport mode, place of the delivery terms                                         | `InvoiceIssuerReference`, `RelatedSupplyChainConsignment`, `RelevantTradeLocation`                                                                                         |
| financial adjustments (change the amount due, BR-FXEXT-CO-16)                                                       | `SpecifiedFinancialAdjustment`                                                                                                                                             |
| exchange rate to the VAT accounting currency (needs BT-6)                                                           | `TaxApplicableTradeCurrencyExchange`                                                                                                                                       |
| description of the invoicing period, type of the accounting reference, payer account name and BIC of a direct debit | `BillingSpecifiedPeriod/Description`, `ReceivableSpecifiedTradeAccountingAccount/TypeCode`, `PayerPartyDebtorFinancialAccount`, `PayerSpecifiedDebtorFinancialInstitution` |

Further EXTENDED information sits on the existing types: `Party.extended()` (`PartyExtension`) holds
the role code, the address of the legal organization, and a contact with department, contact type,
or fax that replaces `Party.contact`; for the seller the contact is a person or a department (CII-SR-465).
`InvoiceNote.contentCode` and `ItemClassification.className` are EXTENDED only, too, and so is
`DocumentAdjustment.extended()` (`AdjustmentExtension`) with the sequence number (`SequenceNumeric`)
and the base quantity with unit (`BasisQuantity`) of a document allowance or charge.
`Invoice.hasExtendedInformation()` tells whether an invoice needs the profile.

Several preceding invoice references (BG-3) are allowed. Logistics service charges count like
document charges: they are part of the sum of charges (BT-108), the amount without VAT (BT-109), and
the VAT breakdown. Advance payments document received amounts; set the paid amount (BT-113)
yourself. Parties use `Party` and therefore need a name and a complete address. The reader reads all
of it back; payment terms with a partial amount, discount, or penalty go to `extended()`, the first
plain term to `paymentTerms` and `dueDate`; a term with its own payee is an EXTENDED term, too, and
so is every further plain term with description or due date (e.g. instalments without amount, as
Mustang writes them).

With document allowances, charges, or logistics service charges, the writer splits the taxable
amount of every VAT category (BT-116) into the amount of the regular lines of the category (`LineTotalBasisAmount`) and the rest
(`AllowanceChargeBasisAmount`, charges minus allowances), as
the official EXTENDED samples do. Both values are calculated; the reader does not store them.

Invoice lines carry their EXTENDED information in `InvoiceLine.extended()` (`LineExtension`):

| Information                                                                                                | CII element                                                                                                                                         |
|------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------|
| parent line, subtype `DETAIL` / `GROUP` / `INFORMATION`, line status                                       | `ParentLineID`, `LineStatusReasonCode`, `LineStatusCode`                                                                                            |
| product identifier, industry identifier, model identifier and name, brand, batches                         | `ID`, `IndustryAssignedID`, `ModelID`, `ModelName`, `BrandName`, `BatchID`                                                                          |
| characteristics with type code or measured value                                                           | `ApplicableProductCharacteristic` with `TypeCode` / `ValueMeasure`                                                                                  |
| product instances (batch, serial number), manufacturer, included products                                  | `IndividualTradeProductInstance`, `ManufacturerTradeParty`, `IncludedReferencedProduct`                                                             |
| delivery terms, seller order, quotation, contract, ultimate customer orders, item seller                   | line `ApplicableTradeDeliveryTerms`, `…ReferencedDocument`, `ItemSellerTradeParty`                                                                  |
| free and package quantities, ship-to and ultimate ship-to party, delivery date                             | `ChargeFreeQuantity`, `PackageQuantity`, `PerPackageUnitQuantity`, `ShipToTradeParty`, `UltimateShipToTradeParty`, `ActualDeliverySupplyChainEvent` |
| despatch advice, receiving advice, delivery note, preceding invoice of the line                            | `…ReferencedDocument`, with optional line of the referenced document                                                                                |
| stated VAT amount and amount with VAT of the line (`taxTotal`, `grossTotal`)                               | line `TaxTotalAmount`, `GrandTotalAmount` of `SpecifiedTradeSettlementLineMonetarySummation`                                                        |
| several allowances and charges on the gross price with reason (`priceAdjustments`, `GrossPriceAdjustment`) | `AppliedTradeAllowanceCharge` of `GrossPriceProductTradePrice`                                                                                      |
| content and subject code of the line note, further typed references, type of the line accounting reference | `IncludedNote/ContentCode`, `SubjectCode`, line `AdditionalReferencedDocument`, `ReceivableSpecifiedTradeAccountingAccount/TypeCode`                |

Only regular lines (no subtype or `DETAIL`) count in the totals and the VAT breakdown (BR-FXEXT-CO-10); subtotal (`GROUP`) and
information lines are written with their own amounts. The
model checks that every parent line exists (BR-FXEXT-11), that a sub-line states its subtype (BR-FXEXT-06), and that a subtotal
equals the sum of its regular and subtotal sub-lines (BR-FXEXT-08). Regular lines need quantity,
unit, net price, and VAT category; subtotal and information lines may leave out the VAT category
and the net price (BR-FXEXT-CO-04), and quantity and unit too if they have no net price. Plain name and
value pairs stay item attributes (BG-32). For lines with allowances or charges the writer also
states their sums (`ChargeTotalAmount`, `AllowanceTotalAmount`). The VAT amount and the amount with
VAT of a line are stated values; with both set, the model checks that the amount with VAT is the
net amount plus the VAT amount.

EN 16931 knows one price discount per line (BT-147). EXTENDED can state its parts: a customer and a
promotional discount, or a charge per unit such as a deposit. `LineExtension.priceAdjustments()`
keeps each of them in the order stated, with reason code and reason; `InvoiceLine.priceDiscount()`
stays the discount the price relation uses, and the model checks that it equals the allowances
minus the charges. Reading takes over every `AppliedTradeAllowanceCharge` of the gross price, and
writing writes them again. A single allowance without reason stays the plain BT-147 discount. If
lenient reading recalculates the net price of a line (`READ-LINE-NET-PRICE`), its parts no longer
add up and are dropped. Percentages and base amounts of these allowances are not supported.

Not supported: VAT included in prices and descriptions of line periods, and sequence numbers and
base quantities of line allowances and charges (the Factur-X rules do not allow them).

UBL, and CII with any other specification identifier, reject an invoice with EXTENDED information
in `Invoice.extended()` or in any line.

EXTENDED is validated against the official EXTENDED schema and Schematron (see below). CII D16B
and EN 16931 alone would reject some EXTENDED content: D16B allows only one preceding invoice and no
invoice reference on advance payments, and EN 16931 counts subtotal and information lines and does
not count logistics service charges in the VAT breakdown (`BR-S-08`).

## Profile EXTENDED-CTC-FR

EXTENDED-CTC-FR is the extended profile of the French e-invoicing reform (XP Z12-012, the
"socle minimum"), a subset of EXTENDED. Paniolo writes, reads, and validates it as CII XML with
the specification identifier `urn:cen.eu:en16931:2017#conformant#urn.cpro.gouv.fr:1p0:extended-ctc-fr`:

```java
Invoice invoice = Invoice.create()
                         .processControl(ProcessControl.facturXExtendedCtcFr("B1"))  // BT-23: billing framework
                         // ... as for EXTENDED, with Invoice.extended() and InvoiceLine.extended()
                         .build();
String xml = paniolo.toXml(invoice, InvoiceSyntax.CII);
ValidationResult result = paniolo.validate(xml);   // "EXTENDED-CTC-FR with BR-FR"
```

- The model is that of EXTENDED. The CII writer rejects the EXTENDED content EXTENDED-CTC-FR does
  not allow and names all of it, for example the test indicator, document name and language,
  quotation and delivery note references, ship-from, ultimate ship-to and product end user parties,
  additional structured payment terms, logistics service charges, advance payments, financial
  adjustments, the description of the invoicing period, party role codes, legal addresses and
  contact types, and on lines product identifiers, brand names, product instances, manufacturers,
  included products, quotation and contract references, and further referenced documents.
  Allowed are, among others, sales and buyer agents, buyer tax representative, invoicer, invoicee
  and payer, delivery terms, sub-lines, item sellers, ship-to parties, and the line references of
  orders, despatch and receiving advices, delivery notes, and preceding invoices.
- `validate` checks the Factur-X EXTENDED schema and the EXTENDED-CTC-FR Schematron of FNFE-MPE.
  If seller and buyer have French addresses, the French rules BR-FR (flux 2) are checked too, for
  example the billing framework BT-23 (`BR-FR-08`); otherwise the result carries the information
  `BR-FR-NOT-APPLICABLE`. Both rule sets (FNFE-MPE France_RFE 1.4.0.04, Apache 2.0) are packaged in
  `paniolo-facturx-rules`; `Paniolo.builder().extendedCtcFrRules(path)` uses another release.
- As in EXTENDED, subtotal (`GROUP`) and information lines may leave out the VAT category and the
  net price.
- **Multi-vendor invoices** (billing framework BT-23 `S8`, `B8`, `M8`, `S9`, `B9`, or `M9`,
  `ProcessControl.isFrenchMultiVendor()`) are supported:
  - sub-invoice lines (`GROUP`) without net price may leave out quantity and unit too;
  - the item seller of a line may be named partially, a sub-invoice line by name, identifiers,
    and country, its detail lines by legal registration only (BR-FR-MV-03); other invoices still
    require a complete item seller;
  - every invoiced object identifier (BT-128) of a line is kept in order, e.g. the sub-invoice
    (`AFL`) next to the billed object (`AVV`), with `LineExtension.furtherObjectIdentifiers()`;
  - the VAT breakdown has one entry per sub-invoice, category, and rate, told apart by the
    sub-invoice number at the start of the exemption reason (`#F202600004#...`, BR-FR-MV-08 and
    BR-FR-MV-09; `TaxCategory.subInvoiceNumber()`).
- A payment means block may name a payment card next to the credit transfer accounts, which only
  EXTENDED-CTC-FR allows (as in the official example UC10).
- The official CII examples of FNFE-MPE validate without findings. Four of the five (UC10 and UC11
  multi-vendor, UC12 sub-lines, UC13) are read and written again without loss, and the written XML
  validates without findings as well.
- The profile is exchanged as CII XML: `toFacturX` rejects `FacturXProfile.EXTENDED_CTC_FR`
  (Factur-X PDFs use EXTENDED). PDFs with `fx:ConformanceLevel` `EXTENDED-CTC-FR`, as the Mustang
  project writes them, are read and validated.
- Not supported: UBL (the UBL rules of EXTENDED-CTC-FR are not packaged), and negative net
  prices, which the official self-billing example `UC13_AUTOF202600035_BIDIRECTIONNELLE` uses; it
  validates but cannot be read.

## Validating

`paniolo.validate(pdf)` checks the container, the PDF/A conformance (with `paniolo-pdfa`), and the
embedded XML with the rules of its profile. `paniolo.validate(xml)` detects the profile of
standalone CII from BT-24 for MINIMUM, BASIC WL, BASIC, EXTENDED, and EXTENDED-CTC-FR; `validate(xml, profile)`
names it explicitly. All six profiles are validated, MINIMUM and BASIC WL included:

| Profile                                      | Rules                                                                            |
|----------------------------------------------|----------------------------------------------------------------------------------|
| MINIMUM, BASIC WL, BASIC, EN 16931, EXTENDED | official Factur-X schema and Schematron of the profile (packaged)                |
| EXTENDED-CTC-FR                              | Factur-X EXTENDED schema, EXTENDED-CTC-FR and BR-FR rules of FNFE-MPE (packaged) |
| XRECHNUNG, unknown                           | EN 16931 and XRechnung                                                           |

The Factur-X schemas describe CII only. UBL with a Factur-X profile is checked against EN 16931:
MINIMUM and BASIC WL yield the warning `FX-RULES-REQUIRED` (not checked, EN 16931 does not apply),
EXTENDED the information `FX-EXTENDED-NOT-CHECKED`.

Standalone XML with the plain EN 16931 identifier or without a known Factur-X identifier is
validated against EN 16931 and XRechnung, as before. `FacturXProfile.ofSpecificationId` maps BT-24
to a profile, also for ZUGFeRD 2.0 and all XRechnung versions.

## Factur-X profile rules

Every Factur-X profile is checked against the official Factur-X schema and Schematron. They are
packaged in [`paniolo-facturx-rules`](../paniolo-facturx-rules/README.md), which `paniolo` brings
along: the FeRD release ZUGFeRD 2.5.2 / Factur-X 1.09.2, whose technical artifacts FeRD publishes
under the Apache License 2.0
([Terms of use](https://www.ferd-net.de/ueber-uns/ressourcen-1/veroeffentlichungen/haftungsausschluss-und-nutzungsrechte)).

```java
Paniolo paniolo = Paniolo.create();
paniolo.validate(facturXPdf);                              // XML stage: "Factur-X BASIC"
paniolo.validate(facturXXml, FacturXProfile.BASIC);        // extracted factur-x.xml
```

The schema and Schematron of the profile replace the KoSIT schema and the EN 16931 Schematron in
the XML stage; profile XRECHNUNG and unknown profiles use EN 16931 and XRechnung. To validate
against another release, download it from FeRD or FNFE-MPE ([factur-x](https://fnfe-mpe.org/factur-x/factur-x_en/)), extract it,
and configure the directory
with `Paniolo.builder().facturXRules(path)`; the expected file names are described
in [paniolo-validation](../paniolo-validation/README.md#validate-against-factur-x--zugferd-profile-rules).
The tests validate against the packaged rules, or against the release named by
`PANIOLO_FACTURX_RULES`:

```sh
PANIOLO_FACTURX_RULES=/path/to/factur-x-package mvn -pl paniolo-facturx -am verify
```

## Container checks

`PdfBoxFacturXSupport.checkContainer(pdf)`, used by `Paniolo.validate(pdf)`, reports findings in
stage `HYBRID`:

| Rule                                                 | Severity        | Check                                                                                  |
|------------------------------------------------------|-----------------|----------------------------------------------------------------------------------------|
| `FX-NO-INVOICE`, `FX-NO-CONTENT`, `FX-NOT-PDF`       | fatal           | an invoice XML is embedded and readable                                                |
| `FX-MULTIPLE-INVOICES`                               | error           | exactly one invoice XML                                                                |
| `FX-NOT-ASSOCIATED`                                  | error           | the file is referenced from the catalog's `/AF` array                                  |
| `FX-AF-RELATIONSHIP`                                 | error           | `AFRelationship` is Alternative, Data or Source                                        |
| `FX-AF-RELATIONSHIP-DE`                              | warning         | Germany: Alternative (Data for MINIMUM and BASIC WL)                                   |
| `FX-MIME-TYPE`, `FX-MOD-DATE`                        | error, warning  | `text/xml` and `Params/ModDate`                                                        |
| `FX-XMP`, `FX-PDFA-PART`, `FX-EXTENSION-SCHEMA`      | error           | XMP present, `pdfaid:part` 3, extension schema for the `fx` namespace                  |
| `FX-DOCUMENT-TYPE`, `FX-DOCUMENT-FILE-NAME`          | error           | `INVOICE` and the embedded file name                                                   |
| `FX-CONFORMANCE-LEVEL`, `FX-FILE-NAME`, `FX-VERSION` | error / warning | known level, `factur-x.xml` / `xrechnung.xml`, `fx:Version`                            |
| `FX-GUIDELINE`                                       | error           | the level matches BT-24 of the embedded invoice                                        |
| `FX-ENCRYPTED`                                       | error           | no encryption                                                                          |
| `FX-PRODUCER`                                        | information     | producing software (`PdfBoxFacturXSupport.producer`), added by `Paniolo.validate(pdf)` |

## Limits

- Writing supports all profiles: BASIC, EN 16931, EXTENDED (header and line information, see
  above), and XRECHNUNG from invoices with lines, MINIMUM and BASIC WL from invoices without lines
  with stated totals (`FacturXProfile.hasLines()` is false for them).
- The writer enforces the BASIC restrictions itself; the official Factur-X rules check all
  profiles in validation, see [Factur-X profile rules](#factur-x-profile-rules).
- A visual invoice from the caller must be PDF/A-1, -2, or -3; Paniolo does not convert arbitrary
  PDFs to PDF/A. To have the pages rendered instead, add
  [`paniolo-pdf-layout`](../paniolo-pdf-layout/README.md).
- Factur-X is based on CII D22B; Paniolo writes CII D16B, a compatible subset. Some EXTENDED
  content (several preceding invoices, the invoice reference of advance payments) is valid only
  against the Factur-X schemas, which Paniolo uses for validation; profile XRECHNUNG is validated
  with the D16B schema and EN 16931 / XRechnung Schematron from `paniolo-xrechnung-rules`.

## Build and tests

```sh
mvn -pl paniolo-facturx -am verify
```

Tests create a PDF/A-2b document without text (no font files needed), embed invoices and
credit notes for the writable profiles, check the result with veraPDF (test dependency only,
GPLv3+/MPL-2.0) for PDF/A-3b conformance, validate and read back the embedded invoice, and
cover profile mismatches, non-PDF/A, encrypted, and already converted input. Further tests cover
the container checks, the profile detection, MINIMUM and BASIC WL, the EXTENDED information,
attachments, producer detection, the ZUGFeRD 1.0 migration with samples written for Paniolo,
Order-X documents and PDFs, PDF delivery notes, and the JSON round trip of all test documents.

Tests against the official Factur-X rules use the packaged release, or the extracted package
named by `PANIOLO_FACTURX_RULES`; tests against the official Order-X rules, including the official
examples, use the packaged rules or the directory named by `PANIOLO_ORDERX_RULES`; see
[Factur-X profile rules](#factur-x-profile-rules).

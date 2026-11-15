# Paniolo PDF layout

[`paniolo-facturx`](../README.md) renders the visual document of invoices, Order-X orders,
and despatch advices from the Paniolo model, by default as PDF/A-3b and, with a
[profile](#pdf-profiles), as PDF/A-1b or as plain PDF 1.7 or 2.0 preview (package `io.github.nalukit.paniolo.pdflayout`). With
it, a Factur-X / ZUGFeRD invoice, an Order-X PDF, or a CIDA delivery note needs no PDF of its own:
Paniolo renders the pages, writes the XML, and embeds it. It is based on Apache PDFBox and sets
the text in IBM Plex Sans, which the module packages (see [Fonts](#fonts)); there is no further
dependency to add.

## Usage

```java
Paniolo paniolo = Paniolo.create();

byte[] facturX = paniolo.toFacturX(invoice, FacturXProfile.XRECHNUNG);   // rendered page + XML
byte[] pdf = paniolo.toPdf(invoice);                                     // only the page, PDF/A-3b

PdfLayout layout = PdfLayout.of(Locale.ENGLISH)
                            .withLogo(Files.readAllBytes(Path.of("logo.png")))
                            .withShowLogo(true)                              // default: no logo
                            .withLogoPosition(PdfLayout.LogoPosition.RIGHT)  // default: LEFT
                            .withAccentColor(0x01244F)
                            .withFooter("Managing director: Erika Muster · Berlin HRB 12345 B");
byte[] branded = paniolo.toFacturX(invoice,
                                   FacturXProfile.EN16931,
                                   layout,
                                   List.of(PdfAttachment.of("timesheet.pdf",
                                                            timesheet,
                                                            "application/pdf")));
```

Orders and despatch advices work the same way:

```java
byte[] orderX = paniolo.toOrderXPdf(order);                  // rendered pages + Order-X XML
byte[] deliveryNote = paniolo.toDespatchAdvicePdf(advice);   // rendered pages + CIDA XML
byte[] orderPdf = paniolo.toPdf(order, layout);              // only the pages
byte[] notePdf = paniolo.toPdf(advice, layout);
```

For a preview, e.g. to check an invoice before it is sent, render only the page and add a
watermark. It is printed large, diagonally, and semi-transparent across every page, with the
text as given:

```java
byte[] preview = paniolo.toPdf(invoice, PdfLayout.defaults().withWatermark("PREVIEW"));
```

This preview is still a PDF/A-3b document, the default profile. For a plain PDF that is not meant
for archiving, or as PDF 2.0, choose a [profile](#pdf-profiles):

```java
byte[] plain = paniolo.toPdf(invoice, PdfLayout.defaults()
                                               .withWatermark("PREVIEW")
                                               .withProfile(PdfProfile.PREVIEW));
```

The methods that embed XML (`toFacturX`, `toOrderXPdf`, `toDespatchAdvicePdf`) reject a layout
with watermark: the result would be a valid e-invoice whose page says it is not. They reject the
profiles `PREVIEW` and `PREVIEW_2_0` as well.

The profile is checked before rendering, as for `toFacturX(invoice, pdf, profile)`. On the command
line, `paniolo to-pdf` renders an invoice, and `to-facturx`, `order to-pdf`, and
`despatch-advice to-pdf` without `--pdf` render and embed in one step; see
[`paniolo-cli`](../../paniolo-cli/README.md).

## Validation reports

The module also renders validation reports, e.g. for archives or departments that work with
documents rather than XML:

```java
byte[] received = Files.readAllBytes(path);
ValidationResult result = paniolo.validate(received);
byte[] pdf = paniolo.toReportPdf(result, "R-2026-001.xml", received, PdfLayout.of(Locale.ENGLISH));
```

The report is laid out as conformance report. At the top, a verdict in the color of the result:
conform (green), conform with warnings (amber), or not conform with the recommendation to reject
the document (red), followed by the number of findings per severity. Then the validation time
(UTC), duration and software, the validated file with size and SHA-256, the rules applied
(profile) and the rule releases of the instance (e.g. KoSIT XRechnung 3.0.2, Factur-X 1.09.2), the
validation stages with their findings per severity and result, and the Schematron and PDF/A
statistics if the validation collected them. Every finding follows as a block with number,
severity, complete rule identifier, message and location, grouped by stage like
`ValidationResult.toXml()`; a block moves to the next page as a whole, only a block longer than a
page continues there. A result without findings is rendered too: it shows the conform verdict and
the stages that were run. The machine-readable reports remain the basis; the PDF is a rendering of
them. Labels are German (`PdfLayout.defaults()`), English, or French; logo and sender address of
the layout are not used. In the CLI: `paniolo validate invoice.xml -f pdf -o report.pdf`.

## Layout

All documents use A4 after DIN 5008 form B, so the recipient fits a window envelope. The invoice:

- **Letterhead**: logo and the seller's name, address, phone, and e-mail. Both are configurable:

  | Setting                          | Default | Effect                                                              |
  |----------------------------------|---------|---------------------------------------------------------------------|
  | `withShowSenderAddress(boolean)` | `true`  | shows the sender's address; `false` for preprinted letterhead paper |
  | `withShowLogo(boolean)`          | `false` | shows the logo set with `withLogo(byte[])`                          |
  | `withLogoPosition(LogoPosition)` | `LEFT`  | logo on the left or right; the address moves to the other side      |

  `withShowSenderAddress(false)` with `withShowLogo(true)` shows only the logo, on the side of
  `withLogoPosition`. The sender line above the window and the footer are always shown.

  The logo is a PNG or JPEG file (RGB or grayscale, no CMYK; PNG transparency is kept). It is
  scaled proportionally to fit 60 mm × 20 mm, so an aspect ratio of 3:1 fills the box; for sharp
  print at 300 dpi it should have about 710 × 240 pixels. Trim empty borders.
- **Window**: sender line and the buyer's address; the country only if it differs from the seller's.
- **Document data**: invoice number, date, due date, service period, delivery date, tax point date,
  buyer reference (Leitweg-ID), purchase order, customer number, the buyer's VAT ID.
- **Title** based on the invoice type (invoice, credit note, corrected invoice, partial invoice,
  final invoice, ...) and the notes of the invoice.
- **Lines**: position, name, description, item numbers, attributes, period, order line, list price
  and price discount, line allowances and charges, note; quantity, unit, unit price with the base
  quantity, VAT rate, and net amount. Sub-lines are indented below their parent. Rows are kept
  together; a row longer than a page is split. The table header is repeated on each page.
- **Totals**: sum of lines, document allowances and charges with reason and percentage, total
  without VAT, VAT per rate on its taxable amount, total with VAT, paid amount, rounding, amount
  due, and the VAT in the tax currency.
- **VAT notes**: exemption reasons, or the usual note for reverse charge, intra-community supply,
  export, exempt, not subject to VAT, and zero rated.
- **Payment**: payment means, IBAN in groups of four, BIC, account holder, remittance information,
  due date, payment terms, cash discounts with the date until which they apply, card, SEPA direct
  debit with mandate and creditor ID, payee.
- **References**: sales order, contract, project, despatch and receiving advice, tender, invoiced
  object, accounting reference, preceding invoices, delivery address, tax representative.
- **Supporting documents** (BG-24) by reference, description, and file name.
- **EXTENDED content** (Factur-X EXTENDED and EXTENDED-CTC-FR), where the invoice carries it:
  - the document status of a test document ("not for payment") or a copy, the document name, and
    the effective date;
  - further references (quotation, delivery note, ultimate customer order, invoice issuer
    reference, further accounting references), delivery terms and transport mode;
  - further parties: sales agent, buyer agent, buyer tax representative, product end user,
    ultimate ship-to party, invoicer, and payer, each with name, address, and identifiers;
  - in the lines: subtotal (GROUP) and information lines, which are marked as not included in the
    total; sub-lines indented below their parent; product ID, model, manufacturer, included
    products, quantities free of charge, delivery terms, item seller, ship-to parties, line
    references, and the stated line VAT and gross amount;
  - in the totals: logistics service charges with their VAT rate, financial adjustments, and the
    exchange rate; the debited account holder and BIC of a direct debit in the payment details.

  Codes that only qualify another value (note content and subject codes, accounting type codes,
  line status codes) are not shown. Third party payments of the XRechnung extension appear as a
  row of their own in the totals, so the rows add up to the amount due.
- **Footer** on every page: seller name and address; phone, e-mail, and e-invoice address; VAT ID,
  tax number, registration, and legal information; the footer text of the layout; "page n of m".
  Pages after the first start with a short header.

**Orders** (Order-X order, order change, order response): the buyer's letterhead and the seller in
the window for orders and changes, the other way round for responses (order confirmations);
order number, date, the referenced order, response status, buyer reference, seller order number,
requested delivery and despatch, quotation, contract. Lines show item numbers, brand, batch,
characteristics, product instances, requested dates, partial delivery, packages, substitutes,
allowances and charges; a response shows the ordered and the confirmed quantity and the line
status, with rejected lines in red. Then the totals, the parties (deliver to, ship from, invoicee,
requisitioner), and the order details: previous documents, blanket order, requisition, catalogue,
project, validity, delivery terms, payment means and terms, purpose.

**Despatch advices** (UBL and CIDA) as delivery notes: the despatching party's
letterhead and the delivery customer in the window; despatch and delivery dates, estimated
delivery, order numbers, shipment and tracking number. Lines show the delivered quantity, unit,
outstanding quantity and reason, order line, item numbers, package, and serial numbers, batches,
and expiry, best-before, and manufacture dates. Then shipment details (weight, volume, packages,
mode of transport, despatch address), further parties (buyer, seller, originator), and a receipt
confirmation with date and signature lines.

Labels are German, English, or French (`PdfLayout.of(Locale.FRENCH)`); other languages fall back
to English. Number, amount, and date formats follow the locale (French: `1 234,56 €`,
`27 oct. 2026`, `19 %`). Code lists use the localized texts of the model, which exist in German
and English; for French the labels translate the common VAT categories and payment means, country
names come from the JDK, and other codes are shown as code rather than as English text. Units are
abbreviated (Std., Stk., kg / h, pce, kg, ...). The language only translates the visual document:
the XML and its profile stay the same, so a French Factur-X profile such as EXTENDED-CTC-FR is a
separate matter from the French labels.

Invoices without lines (Factur-X MINIMUM and BASIC WL) show their stated totals.

## PDF/A

By default the documents conform to PDF/A-3b (checked with veraPDF in the tests): embedded font
subsets, sRGB output intent, XMP metadata matching the document information, document language.
PDF/A-3 is based on PDF 1.7 and does not allow PDF 2.0. Factur-X documents created with
`toFacturX` pass `Paniolo.validate`, including the PDF/A check of
[`paniolo-pdfa`](../../paniolo-pdfa/README.md); Order-X PDFs created with `toOrderXPdf` pass
`Paniolo.validateOrder`. The tests render all twelve official Order-X COMFORT examples.

## PDF profiles

`PdfLayout.withProfile(PdfProfile)` selects the kind of PDF. It applies to `toPdf` for invoices,
orders, and despatch advices and to `toReportPdf`; without it the result is PDF/A-3b.

| Profile              | PDF | PDF/A | Transparency | Use                                                                     |
|----------------------|-----|-------|--------------|-------------------------------------------------------------------------|
| `PDF_A_3B` (default) | 1.7 | 3b    | yes          | The visual document for archives; the format of every hybrid document   |
| `PDF_A_1B`           | 1.4 | 1b    | no           | The visual part that you pass to `toFacturX(invoice, pdfA, profile)`    |
| `PREVIEW`            | 1.7 | no    | yes          | A plain PDF to look at, e.g. with watermark                             |
| `PREVIEW_2_0`        | 2.0 | no    | yes          | The same as PDF 2.0                                                     |

**Which profile?**

- You want a **Factur-X, Order-X, or CIDA document** and let Paniolo render the page: call
  `toFacturX(invoice, profile)`, `toOrderXPdf(order)`, or `toDespatchAdvicePdf(advice)`. They
  use PDF/A-3b; there is nothing to choose.
- You want the **page of an invoice as an archive copy**, without XML: `toPdf(invoice)`, PDF/A-3b.
- You want to **create the visual part first and embed the XML later**, e.g. because you check the
  page or add pages of your own in between: `PDF_A_1B`, then `toFacturX(invoice, pdfA, profile)`.
- You want to **look at the page before it is sent**, e.g. with a watermark `ENTWURF` or `PREVIEW`:
  `PREVIEW`, or `PREVIEW_2_0` if your viewer or workflow expects PDF 2.0.

### Examples

A plain preview with watermark, as PDF 1.7 and as PDF 2.0:

```java
PdfLayout draft = PdfLayout.of(Locale.GERMAN).withWatermark("ENTWURF");

byte[] preview17 = paniolo.toPdf(invoice, draft.withProfile(PdfProfile.PREVIEW));
byte[] preview20 = paniolo.toPdf(invoice, draft.withProfile(PdfProfile.PREVIEW_2_0));
```

PDF/A-1b as the visual part of a Factur-X invoice. `toFacturX` embeds the XML and makes PDF/A-3b
of the document:

```java
byte[] pdfA1   = paniolo.toPdf(invoice, PdfLayout.defaults().withProfile(PdfProfile.PDF_A_1B));
byte[] facturX = paniolo.toFacturX(invoice, pdfA1, FacturXProfile.EN16931);

ValidationResult result = paniolo.validate(facturX);   // container, PDF/A-3, and XML
```

The same layout with logo, footer, and a profile; the settings do not depend on each other:

```java
PdfLayout layout = PdfLayout.of(Locale.ENGLISH)
                            .withLogo(Files.readAllBytes(Path.of("logo.png")))
                            .withShowLogo(true)
                            .withFooter("Managing director: Erika Muster · Berlin HRB 12345 B")
                            .withProfile(PdfProfile.PDF_A_1B);
```

Orders, despatch advices, and validation reports use the profile as well:

```java
byte[] orderPage  = paniolo.toPdf(order, PdfLayout.defaults().withProfile(PdfProfile.PREVIEW));
byte[] notePage   = paniolo.toPdf(advice, PdfLayout.defaults().withProfile(PdfProfile.PDF_A_1B));
byte[] reportPdf  = paniolo.toReportPdf(result, "invoice.pdf", received,
                                        PdfLayout.defaults().withProfile(PdfProfile.PDF_A_3B));
```

The command line selects the profile with `--pdf-profile`; `--profile` is the Factur-X profile
of `to-facturx`:

```bash
paniolo to-pdf invoice.json --watermark ENTWURF --pdf-profile PREVIEW -o preview.pdf
paniolo to-pdf invoice.json --pdf-profile PREVIEW_2_0 -o preview20.pdf
paniolo to-pdf invoice.json --pdf-profile PDF_A_1B -o pdfa1.pdf
paniolo to-facturx invoice.json --profile EN16931 --pdf-profile PDF_A_1B -o facturx.pdf
```

### What PDF/A-1b changes

PDF/A-1 forbids transparency and object streams. Paniolo therefore renders `PDF_A_1B` differently:

- **Watermark**: opaque light gray text *below* the content instead of semi-transparent text above
  it. Content that covers it, such as filled table cells, hides parts of it.
- **Logo**: a PNG logo with transparency is placed on a white background. The page is white, so the
  logo looks the same. JPEG logos and PNGs without transparency are embedded as they are.
- **File**: written without object streams and cross-reference streams, so it is larger than the
  same document as PDF/A-3b or preview.
- **Version**: PDF 1.4, the version PDF/A-1 is based on. The content is limited to the features of
  PDF 1.4, so the header says no more; veraPDF would accept a higher header number, but it would
  promise features the document does not use.

The hybrid methods accept a `PDF_A_1B` layout, e.g.
`toFacturX(invoice, profile, layout, attachments)`: the page is rendered as PDF/A-1b, and embedding
the XML makes PDF/A-3b of it. A watermark is rejected there as before. The Factur-X result is the same as with the default
profile apart from the page: `Paniolo.validate` reports it as valid.

### PDF versions

| Profile        | Header and catalog                                                              |
|----------------|---------------------------------------------------------------------------------|
| `PDF_A_1B`     | `%PDF-1.4`                                                                      |
| `PDF_A_3B`     | PDF 1.7; the catalog says `/Version 1.7`                                        |
| `PREVIEW`      | PDF 1.7; the catalog says `/Version 1.7`                                        |
| `PREVIEW_2_0`  | `%PDF-2.0` and `/Version 2.0`                                                   |

The file header of a compressed PDF 1.7 document reads `%PDF-1.6`, because PDFBox writes object
streams; the catalog entry `/Version 1.7` raises the version, and PDF readers and veraPDF use it.
PDF 2.0 is a plain PDF only: PDF/A-3 is based on PDF 1.7 and does not allow it, so there is no
Factur-X, Order-X, or CIDA document in PDF 2.0, and a Factur-X document requires PDF/A-3 in any
case. PDF/A-4 (PDF 2.0 based) is not supported.

How the version is set: the renderer finishes every document in `PdfA.finish`, after the pages are
drawn, and sets the version of the profile there, together with the output intent, the XMP
metadata, and the document information.

- PDFBox 3 writes a version of 1.4 or higher to the `/Version` entry of the catalog only; the
  header comes from the way the file is saved. `PDF_A_1B` is saved without compression, so its
  header is `%PDF-1.4`; the other profiles are saved with object streams, so their header reads
  `%PDF-1.6` and `/Version 1.7` in the catalog gives the version.
- `PREVIEW_2_0` sets the version on the catalog and on the underlying COS document, so that the
  header says `%PDF-2.0` as well; with the catalog entry alone, the header would stay at 1.6.
- Embedding the XML raises a version below 1.7 to 1.7 and writes `pdfaid:part` 3, so
  `toFacturX(invoice, pdfA, profile)` turns a `PDF_A_1B` page into PDF/A-3b.

### Errors

| Case                                                                 | Result                                   |
|----------------------------------------------------------------------|------------------------------------------|
| `toFacturX`, `toOrderXPdf`, `toDespatchAdvicePdf` with `PREVIEW` or `PREVIEW_2_0` | `IllegalArgumentException`   |
| the same methods with a watermark                                    | `IllegalArgumentException`               |
| `PdfLayout` without profile (`null`)                                 | `NullPointerException`                   |
| `--pdf-profile` with another value on the command line               | usage error that lists the values        |

### Checking the result

`PDF_A_1B` and `PDF_A_3B` are checked with veraPDF in the tests of the module. To check your own
documents, `Paniolo.validate(pdf)` with [`paniolo-pdfa`](../../paniolo-pdfa/README.md) validates
a PDF against the flavour its XMP metadata declares. A `PDF_A_1B` document is reported with
`PDFA-PART`, because Factur-X requires part 3; its PDF/A-1b rules themselves are checked and show
no findings. A preview is reported with `PDFA-NOT-DECLARED`, because it declares no PDF/A.

## Fonts

The default font is IBM Plex Sans Regular and Bold (version 3.006, SIL Open Font License 1.1,
Reserved Font Name "Plex"), packaged with `paniolo-facturx` unmodified together with its license
(`io/github/nalukit/paniolo/pdflayout/fonts/IBMPlexSans-LICENSE.txt`, see `META-INF/NOTICE`). It
covers Latin including Central and Eastern European languages, Greek, and Cyrillic, and its digits
have the same width, so amounts line up in the columns. `PdfLayout.withFonts(regular, bold)`
embeds own TrueType fonts, e.g. the corporate font; without a bold font, bold text is emboldened
from the regular font. Characters a font has no glyph for are shown as `?`, spaces as spaces, so
any text can be rendered.

Own fonts are embedded as subsets in every generated PDF, which is then passed on to customers.
Paniolo does not check their license; whoever passes the fonts is responsible for it. Fonts under
the SIL Open Font License or the Apache License allow this without restriction. Many commercial
fonts, including corporate fonts licensed for desktop use only, need a separate license for
embedding in documents or for use on servers; check the font license before using such a font.
Fonts whose embedding permissions (`fsType` of the OS/2 table) forbid embedding or subsetting are
rejected with an `IllegalArgumentException` that names the restriction.

## Limits

- No PDF/A-3a (tagged PDF), no PDF/A-2 and PDF/A-4, and no right-to-left scripts.
- The layout is fixed apart from logo, fonts, accent color, footer, and language. For a fully own
  design, render your PDF/A yourself and use `toFacturX(invoice, pdf, profile)`.

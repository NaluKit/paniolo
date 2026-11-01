# Paniolo PDF layout

`paniolo-pdf-layout` renders the visual PDF/A-3b document of invoices, Order-X orders, and
despatch advices from the Paniolo model. With it, a Factur-X / ZUGFeRD invoice, an Order-X PDF, or a
CIDA delivery note needs no PDF of its own: Paniolo renders the pages, writes the XML, and embeds
it. It is based on Apache PDFBox and brings
[`paniolo-facturx`](../paniolo-facturx/README.md). It requires Java 21.

```xml
<dependency>
  <groupId>io.github.nalukit</groupId>
  <artifactId>paniolo-pdf-layout</artifactId>
  <version>1.0.0-rc01</version>
</dependency>
```

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

The methods that embed XML (`toFacturX`, `toOrderXPdf`, `toDespatchAdvicePdf`) reject a layout
with watermark: the result would be a valid e-invoice whose page says it is not.

The profile is checked before rendering, as for `toFacturX(invoice, pdf, profile)`. On the command
line, `paniolo to-pdf` renders an invoice, and `to-facturx`, `order to-pdf`, and
`despatch-advice to-pdf` without `--pdf` render and embed in one step; see
[`paniolo-cli`](../paniolo-cli/README.md).

## Validation reports

The module also renders validation reports, e.g. for archives or departments that work with
documents rather than XML:

```java
byte[] received = Files.readAllBytes(path);
ValidationResult result = paniolo.validate(received);
byte[] pdf = paniolo.toReportPdf(result, "R-2026-001.xml", received, PdfLayout.of(Locale.ENGLISH));
```

The report shows the result, the validation time (UTC) and duration, the validated file with size
and SHA-256, the rules applied (profile) and the rule releases of the instance (e.g. KoSIT
XRechnung 3.0.2, Factur-X 1.09.2), the number of findings per severity and stage, and every finding
with number, severity, stage, complete rule identifier, message and location. The findings table
repeats its header on every page; a finding moves to the next page as a whole, only a finding
longer than a page continues there. The findings are those of
`ValidationResult.toXml()` and of the CLI JSON, in the order of `ValidationResult.messages()` like
the JSON (the XML report groups them by stage). The machine-readable reports remain the basis; the
PDF is a rendering of them. Labels are German (`PdfLayout.defaults()`), English, or French;
logo and sender
address of the layout are not used. In the CLI: `paniolo validate invoice.xml -f pdf -o report.pdf`.

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

The documents conform to PDF/A-3b (checked with veraPDF in the tests): embedded font subsets,
sRGB output intent, XMP metadata matching the document information, document language. Factur-X
documents created with `toFacturX` pass `Paniolo.validate`, including the PDF/A check of
[`paniolo-pdfa`](../paniolo-pdfa/README.md); Order-X PDFs created with `toOrderXPdf` pass
`Paniolo.validateOrder`. The tests render all twelve official Order-X COMFORT examples.

## Fonts

The default font is Liberation Sans (SIL Open Font License), which PDFBox packages; bold text is
emboldened from the regular font. `PdfLayout.withFonts(regular, bold)` embeds own TrueType fonts,
e.g. the corporate font; their license must allow embedding. Characters a font has no glyph for
are shown as `?`, spaces as spaces, so any text can be rendered.

## Limits

- No PDF/A-3a (tagged PDF) and no right-to-left scripts.
- The layout is fixed apart from logo, fonts, accent color, footer, and language. For a fully own
  design, render your PDF/A yourself and use `toFacturX(invoice, pdf, profile)`.

# Paniolo Visualizer

`paniolo-visualizer` shows electronic invoices, orders, and despatch advices in the browser. Its
core is `<paniolo-viewer>`, a web component without dependencies that takes the document **as bytes**,
reads it in the browser, and renders it. It runs in any web application (plain HTML, Angular, React,
Vue, GWT/J2CL, server-rendered pages) and needs no server, no network access, and no build step.

```html
<script type="module"
        src="/paniolo-visualizer/paniolo-visualizer.js"></script>

<paniolo-viewer lang="de"></paniolo-viewer>

<script type="module">
    const response = await fetch('/api/invoices/4711');                  // XML or PDF
    document.querySelector('paniolo-viewer').load(await response.arrayBuffer());
</script>
```

The Maven artifact contains the component as `META-INF/resources/paniolo-visualizer/paniolo-visualizer.js`,
so servlet containers, Spring Boot, and Quarkus serve it as `/paniolo-visualizer/paniolo-visualizer.js`
without configuration. The Java class `PanioloVisualizer` writes standalone HTML pages with the
component and the document, e.g. for archives or e-mails; the CLI does the same with
[`paniolo visualize`](../paniolo-cli/README.md).

```xml
<dependency>
  <groupId>io.github.nalukit</groupId>
  <artifactId>paniolo-visualizer</artifactId>
  <version>1.0.0-rc01</version>
</dependency>
```

## What it shows

| Document                                              | Formats read in the browser                                      |
|-------------------------------------------------------|------------------------------------------------------------------|
| Invoice and credit note (XRechnung, EN 16931, Peppol) | UBL 2.1 Invoice and CreditNote incl. sub invoice lines, CII D16B |
| Factur-X / ZUGFeRD 2, all profiles, EXTENDED-CTC-FR   | CII, also embedded in PDF/A-3                                    |
| ZUGFeRD 1.0                                           | XML, also embedded in PDF                                        |
| Order, order change, order response                   | Order-X 1.0, also embedded in PDF                                |
| Despatch advice (delivery note)                       | UBL 2.1 DespatchAdvice (Peppol, 1Lieferschein), CIDA             |
| Validation report                                     | Paniolo report (`ValidationResult.toXml()`), KoSIT report, SVRL  |

The view has these tabs:

- **Overview**: seller, buyer, document data, payment (IBAN, BIC, remittance information, terms),
  totals with the VAT per rate, notes.
- **Lines**: all lines with quantity, unit, price, VAT, and amount; nested sub-lines (CII EXTENDED,
  XRechnung extension); details per line such as item identifiers, attributes, line allowances and
  charges, periods, batches and serial numbers, and in EXTENDED the product, delivery, and
  reference details and every invoiced object identifier (BT-128), e.g. the sub-invoice (AFL)
  next to the object (AVV).
- **Details**: VAT breakdown, document allowances and charges, references (order, contract, project,
  preceding invoices, ...), delivery, further parties, and the process identifiers. EXTENDED
  logistics service charges count in the charges (BT-108), financial adjustments in the amount due.
- **Attachments**: files embedded in the XML and in the PDF, with download and preview (PDF and
  images), and the original PDF.
- **Validation** (with a report): verdict, findings per stage with rule ID and location; findings
  that belong to a line are marked there and link to it.
- **XML**: the formatted source, to copy or download.

EXTENDED codes that qualify another value are shown with their label and code: the document
language (LanguageID), the subject and content code of document and line notes (a note may state
its content code instead of its text), the account type of accounting references (code list 52),
the line status code next to the kind of line, and the code of the date the VAT of a line becomes due. The PDF of `paniolo-facturx` leaves these codes
out, as it is laid out for the recipient in the chosen language; only a note stated by its content
code alone shows that code there.

Code lists are shown with their names (document types, payment means, VAT categories, units, note
subjects, identifier schemes; countries and currencies with `Intl.DisplayNames`). Amounts are
formatted exactly as written in the document, with the locale of the viewer. Labels exist in German,
English, and French.

`print()` and the print button print all tabs except the XML, without the host page. The layout
adapts to the width of the component (container queries): in narrow containers the lines are shown
as cards.

## API

### Attributes

| Attribute    | Meaning                                                                                            |
|--------------|----------------------------------------------------------------------------------------------------|
| `lang`       | `de`, `en`, `fr`, or a locale such as `de-AT`; default: the closest `lang` or the browser language |
| `theme`      | `auto` (default, follows `prefers-color-scheme`), `light`, `dark`                                  |
| `src`        | URL to fetch the document from (same-origin credentials)                                           |
| `report-src` | URL to fetch a validation report from                                                              |
| `tabs`       | comma-separated subset of `overview,lines,details,attachments,validation,xml`                      |
| `dropzone`   | shows a drop area and a file picker; dropped reports are recognized                                |

### Methods and properties

```js
const viewer = document.querySelector('paniolo-viewer');

await viewer.load(bytes);                        // Uint8Array, ArrayBuffer, Blob, Response, XML text, or Base64
await viewer.load(bytes, {report: reportBytes});
await viewer.loadReport(reportBytes);            // add a report later
viewer.model;                                    // the view model of the document (see below)
viewer.report;                                   // the parsed report
viewer.tab = 'lines';                            // select a tab
viewer.print();
viewer.clear();
```

`load` returns the model and is rejected if the document cannot be read; the component shows the
error in any case. The module also exports the parser for use without the component:

```js
import {parse, parseReport} from '/paniolo-visualizer/paniolo-visualizer.js';

const model = await parse(bytes);   // { kind, syntax, profile, number, seller, buyer, lines, totals, attachments, xml, ... }
```

### Events

All events bubble and cross the shadow boundary.

| Event           | `detail`                                                                                                                          |
|-----------------|-----------------------------------------------------------------------------------------------------------------------------------|
| `paniolo-load`  | `{ model, title }`, e.g. title `Invoice R-2026-001` in English                                                                              |
| `paniolo-error` | `{ error, code, message }`; codes: `empty`, `input`, `xml`, `unknownRoot`, `isReport`, `pdf`, `pdfEncrypted`, `pdfNoXml`, `fetch` |
| `paniolo-tab`   | `{ tab }`                                                                                                                         |

### Styling

The component uses a shadow root. Colors, font, and radius come from CSS custom properties that the
host page can set:

```css
paniolo-viewer {
    --paniolo-accent: #0a5; /* links, active tab */
    --paniolo-font-family: Inter, sans-serif;
    --paniolo-font-size: 15px;
    --paniolo-radius: 4px;
    --paniolo-background: #fff;
    --paniolo-surface: #f6f6f6;
    --paniolo-border: #ddd;
    --paniolo-text: #222;
    --paniolo-heading: #111;
    --paniolo-muted: #666;
}

paniolo-viewer::part(header) {
    border-top-color: #0a5;
}
```

Parts: `root`, `header`, `actions`, `tabs`, `totals`, `lines`, `xml`, `dropzone`.

## Standalone HTML from Java

```java
PanioloVisualizer visualizer = PanioloVisualizer.create();          // thread-safe, create once

String html = visualizer.toHtml(pdfOrXml);                          // German
String withReport = visualizer.toHtml(pdfOrXml,
                                      paniolo.validate(pdfOrXml)
                                             .toXml()
                                             .getBytes(StandardCharsets.UTF_8),
                                      Locale.ENGLISH);              // with the validation result
String js = visualizer.script();                                    // serve the component yourself
```

The page contains the script and the Base64 encoded document and opens offline.

## Security and privacy

- The document is read in the browser; nothing is sent anywhere. The component loads no fonts,
  scripts, or styles from the network.
- All values of the document are escaped. XML is parsed with `DOMParser`, which resolves no external
  entities.
- Attachments are offered as downloads; only PDFs and raster images are previewed, from `blob:` URLs.
  External attachment links are shown as links only for `http` and `https`, with `rel="noopener noreferrer"`.
- No inline `style` attributes; the styles are an adopted style sheet, so a Content Security Policy
  without `'unsafe-inline'` for styles works in current browsers.

## Limits

- Encrypted PDFs are not read. PDF streams with filters other than `FlateDecode` are skipped.
- The component shows the data; it does not validate. Validate with the `Paniolo` facade (or
  `paniolo validate -f xml`) and pass the report.
- No PDF rendering of the visualization (Mustang uses Apache FOP for that); print to PDF with the
  browser, or render an archivable PDF/A on the server with
  [`paniolo-facturx`](../paniolo-facturx/docs/pdf-layout.md).
- Requires a current browser: ES modules, custom elements, `DecompressionStream` (PDF), container
  queries; Chrome/Edge 105+, Firefox 113+, Safari 16.4+.

## Compared with the Mustang visualizer

The [Mustang project](https://www.mustangproject.org) renders invoices with the XSLT of the KoSIT
XRechnung visualization (XSLT 2.0, Saxon on the server) to HTML and, with Apache FOP, to PDF; and it
renders its validation log. XSLT 2.0 does not run in browsers, so Paniolo reads the documents in
JavaScript instead:

| Feature                           | Mustang                    | Paniolo Visualizer                               |
|-----------------------------------|----------------------------|--------------------------------------------------|
| Runs in                           | Java (server)              | browser; Java/CLI write standalone HTML          |
| Input                             | file name or stream        | bytes, Blob, Response, URL                       |
| CII, UBL Invoice, UBL CreditNote  | yes                        | yes                                              |
| ZUGFeRD 1.0, Order-X              | yes                        | yes                                              |
| Despatch advices (UBL, CIDA)      | no                         | yes                                              |
| Factur-X PDF                      | extract the XML first      | read directly, incl. attachments and PDF preview |
| Languages                         | de, en, fr                 | de, en, fr                                       |
| Validation result                 | separate log visualization | tab in the same view, findings linked to lines   |
| PDF output                        | yes (FOP)                  | browser print; PDF/A-3 with `paniolo-facturx`    |
| Dark mode, responsive, embeddable | no                         | yes                                              |

## Demo

`demo/index.html` loads the samples from `src/test/resources/samples`. Serve the repository root
with any static server and open the page:

```sh
python3 -m http.server 8765
# http://localhost:8765/paniolo-visualizer/demo/index.html
```

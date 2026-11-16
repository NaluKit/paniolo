# Changelog

All notable changes to Paniolo are documented in this file. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/). Each release also has its own API
reference in `docs/api/<version>.md`.

## [Unreleased]

### Added

- The insurance tax `AAM` of the Factur-X profile EXTENDED is kept next to VAT. The new record
  `OtherTax` holds tax type, category, rate, basis amount and tax amount apart from the VAT
  category, rate and breakdown. `ExtendedInformation.otherTaxes()` holds the document level taxes,
  which count in BT-110, BT-112 and the amount due (1,017.57 EUR net and 26.14 EUR insurance tax
  give 1,043.71 EUR); `LineExtension.otherTaxes()` describes a line and is never counted again.
  `OtherTax.insuranceTax(category, rate, basis)` calculates the amount, rounded half up to cents.
  The CII reader reads `AAM` at the lines and the document in strict and lenient mode, and the CII
  writer writes it, never as VAT. Writing it in another profile than EXTENDED or as UBL fails with
  a message instead of losing it. The calculation check recalculates the totals of such invoices.
  Other tax types stay reported as `READ-OTHER-TAX` (rejected in strict mode). The official rules of
  EXTENDED treat every header tax block like a VAT breakdown of its category (BR-E-09,
  BR-FXEXT-S-01), so a category must be chosen that they accept.

- `PaymentTermsAdjustment.withComputedAmount()` and `withComputedAmount(contextBaseAmount)` calculate
  a missing cash discount or penalty amount from base amount and percentage, rounded half up to
  cents (1,190.00 EUR at 3 % gives 35.70 EUR). An amount that is set stays unchanged. The base
  amount of the adjustment takes precedence; the base amount of the calling context is used only
  if the adjustment has none. Without any base amount an `IllegalStateException` reports the
  missing basis; nothing is guessed. `PaymentTerm.withComputedDiscount(contextBaseAmount)` does the
  same for the discount of a term. The calculation changes neither the due date nor the amount due.
  The amount is written as `ActualDiscountAmount` and survives the XML and JSON round trip.

- `PdfLayout.withProfile(PdfProfile)` selects the kind of PDF that `toPdf` renders:
  - `PDF_A_3B` (the default, as before): PDF/A-3b, based on PDF 1.7. PDF/A-3 does not allow PDF 2.0.
  - `PDF_A_1B`: PDF/A-1b, PDF 1.4, as visual part for `toFacturX(invoice, pdfA, profile)`, which makes
    PDF/A-3 of it. PDF/A-1 has no transparency: the watermark is drawn as opaque light text below the
    content, and PNG logos with transparency are placed on white.
  - `PREVIEW`: plain PDF 1.7 without PDF/A, e.g. with a watermark.
  - `PREVIEW_2_0`: the same as PDF 2.0 (header and catalog version).

  The methods that embed XML reject the preview profiles. The CLI selects the profile with
  `--pdf-profile` (`--profile` is the Factur-X profile of `to-facturx`). README and API
  documentation describe the profiles.

### Changed

- The records `ExtendedInformation` and `LineExtension` have the new last component `otherTaxes`;
  the builders and JSON without it are unchanged.

- The record `PdfLayout` has the new component `profile`; the canonical constructor takes it as
  last argument. `of`, `defaults`, and the `with` methods are unchanged.

- The rendered PDFs use IBM Plex Sans Regular and Bold (SIL Open Font License 1.1), packaged with
  `paniolo-facturx`, instead of Liberation Sans of PDFBox. Bold text uses the real bold face instead
  of emboldening the regular font. Digits are slightly wider, so line and page breaks of existing
  documents can differ. Own fonts of `PdfLayout.withFonts` are unchanged.

- `PdfLayout.withFonts` fonts whose embedding permissions (`fsType`) forbid embedding or subsetting
  are rejected with a message naming the restriction instead of "no readable TrueType font" or an
  error while saving the PDF. The PDF layout documentation explains the license of own fonts.

## [1.0.0-rc02] - 2026-11-15

Second release candidate. See the [API reference for 1.0.0-rc02](docs/api/1.0.0-rc02.md).

### Added

- `ImportFidelityTest` validates every normalized export of the corpus against the rules of its target
  syntax; a self-check proves that it finds a repeated tax point date and a second VAT total.

- `PdfAttachment.normalizeMimeType` turns the MIME type of a file of another producer into one the
  record accepts.
- The fidelity test of the corpus reads addresses, accounts, VAT, line names, and the SHA-256 of
  every attachment independently with XPath and lists each expected difference with its type and
  message in `fidelity-exceptions.tsv`.

- Corpus tests of the fidelity of the import: `ImportFidelityTest` checks for every readable corpus
  document that the original export is the received XML byte for byte, that independently read
  values (number, party names, number of lines, payable amount, attachments) equal the model, and
  that the normalized export in both syntaxes keeps parties, lines, VAT, payment data, notes,
  attachments, and amounts; expected differences are listed with their reason. `PdfaCorpusTest`
  checks the corpus PDFs with veraPDF in an additional Maven execution and compares the errors with
  reviewed expectations. `ManifestSummaryTest` keeps the README numbers of the corpus in line with the
  manifest.
- Browser test of the visualizer: `ViewerBrowserTest` (Playwright, profile `browser-tests`, CI job
  `visualizer-browser`) runs the parser and the web component with UBL, CII, and PDF documents,
  sub-lines, attachments, validation report, language switch, untrusted text, and error display.

- Release evidence for audits: every module is published with its SBOM in CycloneDX format
  (`<module>-<version>-cyclonedx.json`); the build is reproducible (fixed archive timestamp, set
  to the commit time of the release); the release JARs carry a GitHub build provenance
  attestation; the GitHub release lists the JARs, SBOMs, `SHA256SUMS`, the OWASP
  Dependency-Check report of the release build, and the changes of the version.
  `scripts/release/collect-release-files.sh` collects these files, also to rebuild and compare a
  release. `SECURITY.md` describes how releases are built and how to verify them. The CI builds
  include the public invoice corpus.
- PDF/A check: control over the JavaScript rules veraPDF compiles and caches per thread (Mustang
  issue 1158, veraPDF issue 1616). `Paniolo.builder().pdfaScriptCache(PdfaScriptCache)` selects
  `REUSE` (default, unchanged behavior: kept for later checks on the same thread) or
  `CLEAR_AFTER_CHECK` (released on the validating thread after every PDF/A check, in a `finally`
  also when the check fails). `Paniolo.releasePdfaScriptCache()` releases the caches of the calling
  thread on demand, e.g. at the end of a batch. Both act on the validating thread only, need no
  lock and never change findings. The SPI `PdfaSupport` gains `check(pdf, statistics, cache)` and
  `releaseThreadCaches()` with defaults. The load test `PdfaScriptCacheLoad` in
  `paniolo-benchmark` measures time, Metaspace and classes of the strategies; see
  `paniolo-pdfa/README.md`.
- `ValidationProfile.xrechnung(bundle, syntax, XrechnungScenario)` loads the standard, extension or
  CVD scenario of a KoSIT bundle; `ValidationProfile.xrechnungSpecifications(bundle)` returns the
  specification identifiers its XRechnung scenarios accept, as `XrechnungSpecification` with
  scenario and document type.
- `Paniolo.isEInvoice(byte[])` and `Paniolo.probe(byte[])`: cheap check whether XML or a PDF is an
  EN 16931 electronic invoice (UBL Invoice, UBL CreditNote, CII, or a Factur-X / ZUGFeRD 2.x PDF
  with such XML embedded). Only the start of the XML is read, the root element and for CII the
  specification identifier (BT-24); for a PDF only the embedded files and the start of the invoice
  XML, without XMP metadata, page content or PDF/A checks. The profiles MINIMUM and BASIC WL, which
  are no electronic invoices in Germany, and unsuitable content, including ZUGFeRD 1.0, yield
  `false` / an empty result instead of an exception. A PDF needs `paniolo-facturx`.

### Changed

- `paniolo-json` and `paniolo-cli` use Jackson 3 (`tools.jackson.core:jackson-databind` 3.1, managed
  with `tools.jackson:jackson-bom`) instead of Jackson 2.
  `PanioloJson.configure` takes and returns the Jackson 3 `tools.jackson.databind.json.JsonMapper.Builder`,
  and `PanioloJsonModule` is a Jackson 3 module; code using them changes its imports from
  `com.fasterxml.jackson.databind` / `com.fasterxml.jackson.core` to `tools.jackson.databind` /
  `tools.jackson.core`. The JSON format is unchanged: JSON stored with 1.0.0-rc01 loads and is
  written again byte for byte. `PanioloJson.read` still throws `IllegalArgumentException` with
  property path or line and column. The Jackson 3 defaults that would change the format are set
  explicitly; see `docs/jackson.md` and the migration notes in `paniolo-json/README.md`.
- `paniolo-pdfa` excludes veraPDF's `jackson-databind` 2.x, which veraPDF uses only for its own
  report output; Paniolo and the executable CLI JAR carry Jackson 3 only. Applications that use the
  veraPDF processor themselves add Jackson 2 on their own.
- Fewer modules, so fewer artifacts per release (nine instead of fifteen). Packages and class names
  are unchanged; only the Maven coordinates change:
  - `paniolo-xml`, `paniolo-xml-ubl`, `paniolo-xml-cii`, and `paniolo-validation` are part of
    `paniolo`. Applications that depended on one of them depend on `paniolo` instead.
  - `paniolo-xrechnung-rules` and `paniolo-facturx-rules` are merged into `paniolo-rules`, which
    `paniolo` brings along. `META-INF/NOTICE` of `paniolo-rules` lists all packaged releases.
  - `paniolo-pdf-layout` is part of `paniolo-facturx`: rendering the pages (`toPdf`,
    `toReportPdf`, `toFacturX(invoice, profile)`, `toOrderXPdf(order)`,
    `toDespatchAdvicePdf(advice)`) needs no further dependency. Without `paniolo-facturx` these
    methods name `paniolo-facturx` as the missing dependency.
- Validation report PDF (`toReportPdf`, `paniolo validate -f pdf`) laid out as conformance report:
  a verdict at the top in the color of the result (conform, conform with warnings, not conform –
  rejection recommended), the number of findings per severity, the validation stages with their
  findings and result, Schematron and PDF/A statistics when the validation collected them, and
  every finding as a block with severity, rule, message and location. Findings are now grouped by
  stage like the XML report. A report without findings shows the conform verdict.
- BR-27 and BR-28 (item net and gross price not negative) are checked by `Invoice` instead of
  `InvoiceLine`, as they depend on the profile: an `InvoiceLine` with a negative price can be
  built, and `Invoice.build()` rejects it outside the Factur-X profiles EXTENDED and
  EXTENDED-CTC-FR, with the rule in the message.

### Fixed

- `inventory` reads the specification identifier and the type code from their place in the document
  (direct children of a UBL root in the basic components namespace; the context and document element
  of CII, ZUGFeRD 1.0, Order-X, and CIDA), so extensions and nested documents with equal names no
  longer replace them or change the profile count.
- The specification identifier and the profile are read with the encoding of the document (byte
  order mark, UTF-16, XML declaration), like its content: an XRechnung without buyer reference is no
  longer reported as valid in UTF-16, and Order-X BASIC validates in UTF-16.
- `inventory` recognizes Order-X (`PDF/ORDER_X`) and despatch advice (`PDF/CIDA`) PDFs and validates
  Order-X PDFs; it counts a known format only for the full namespace and root name, so equal local
  names in a foreign namespace are `UNKNOWN`.
- `visualize --validate` chooses the validator by the root element of the XML or the main document of
  the PDF instead of by words in the first bytes, so comments no longer redirect it and UTF-16 and
  PDF documents reach the right validator. New: `Paniolo.extractOrderXml` and
  `Paniolo.extractDespatchAdviceXml`.
- The optional calculation check judges every line on its own: a line whose net price was
  recalculated no longer hides the price finding (`PANIOLO-CALC-NET-PRICE`) of another line with the
  same identifier, and the official counterpart is matched by the position of the line.
- The PDF layout breaks a long word without spaces by doubling and halving the length instead of
  measuring nearly the whole rest for every cut; a text of 12000 characters takes well under a
  second, and the whole text stays in order.
- A report of `load(input, {report})` that a newer report choice has replaced is not read any more
  and can neither fail the document nor show an error; a network or HTTP failure of `report-src` is
  an error of the report and ends with a correct report like any other report error.
- `attachments --extract` also keeps names apart that a file system folds in more than one
  character (`ß` and `ss`, `ς` and `σ`, ligatures); the comparison is the same on every system.
- A file selection of the viewer (drop or file picker) counts from the selection, not from the end of
  reading: an older, slower file no longer replaces a newer file, a document chosen by the API, or
  `clear()`.
- A correct report, or removing the report, ends the error view of a failed report and shows the
  document again; an error of the document itself stays.
- The viewer orders document requests by the choice of the document, not by the arrival of the
  answer: a slower answer or failure of an older `src`, or one that arrives after `clear()`, is
  ignored.
- A validation report belongs to the document and the request it was started for; a document change,
  `clear()`, removing the report, or a newer report request make a late answer or error ineffective.
- The viewer loads `report-src` together with `src` when it is connected (also from complete markup),
  in any order of arrival.
- The XML download of the viewer is saved as UTF-8 and declares UTF-8, so a document read as
  ISO-8859-1 or UTF-16 can be read again without damaged names. The saved text is the document as
  shown, not the original bytes.
- `attachments --extract` compares file names with their Unicode decomposition and without case, so
  `é.txt` and `é.txt` (one file on macOS) get distinct names, also with `--overwrite`.
- The visualizer refuses damaged UTF-8 and UTF-16 byte sequences with an `xml` error instead of
  replacing them by U+FFFD; a genuine U+FFFD stays valid.
- The visualizer resolves an indirect `/Length` of a PDF stream, wherever the integer object stands,
  before it looks for `endstream`; the word inside XML text or a binary attachment no longer cuts the
  content, and a stream without any end is reported with its object number.
- The visualizer reads indicators (test, copy, charge) as `xs:boolean` (`true`, `false`, `1`, `0`), so
  a numeric test or copy mark is no longer lost; another text is refused with `invalidBoolean`.
- The visualizer finds the encryption of a PDF in its active trailer or cross-reference stream; the
  text `/Encrypt` in a comment or an embedded document no longer rejects a PDF.
- The visualizer refuses a CII line hierarchy that would lose a line (own parent, missing or ambiguous
  parent, cycle) with the line named, instead of silently hiding lines.
- Findings of UBL sub-lines mark and link to the sub-line itself (identity such as `1/2/1`), also with
  repeated business line ids, not the top-level line.
- The visualizer reads XML text that is already decoded without applying its encoding declaration
  again, finds UTF-16 without a BOM by its first bytes, and refuses an unsupported declared encoding
  instead of falling back to UTF-8; reports and XML inside PDFs follow the same rules.
- The visualizer decodes PDF text strings without a Unicode mark as PDFDocEncoding, so file names with
  the euro sign, bullet, or typographic quotes stay unchanged.
- The release notes point only relative repository links at the release tag; links with a scheme
  (`https:`, `mailto:`), protocol-relative links, and anchors stay unchanged
  (`scripts/release/test-release-notes.sh`).

- A name tree of embedded files that refers to itself or is deeper than 32 levels is refused with a
  message instead of ending in a `StackOverflowError`: `attachments` throws an
  `IllegalArgumentException`, `probe` answers empty, and `validate` reports the damaged container.
- The visualizer shows dates that do not exist (30 February, hour 25) and years before 1000 as written
  in the document instead of shifting them, reads the CII date-time format 204 with seconds, and keeps
  every digit of amounts, prices, and quantities with more than 20 decimals or a very large integer part.
- The calculation check lowers a finding to information only when the official finding names the same
  line, allowance, or charge; before, any finding of the same rule did.

- Boolean indicators of UBL, CII, Order-X, and ZUGFeRD 1.0 (charge indicator, test and copy
  indicator) are read as `xs:boolean`: `true`, `false`, `1`, `0`; `1` no longer turns a charge into an
  allowance. Any other text is rejected with the position of the element, also in lenient mode.
- `Paniolo.probe` and `isEInvoice` ask the invoice file that the XMP metadata names first and then the
  other candidates; with several invoice files an empty answer no longer contradicts extraction.

- The normalized CII export writes the tax point date (BT-7) and its code (BT-8) once, in the first
  VAT breakdown, so invoices with several VAT rates pass `CII-SR-461`.
- With the same currency for invoice and VAT accounting (BT-6 equals BT-5) the normalized export of
  both syntaxes omits the redundant BT-6 and keeps the one VAT total; before, UBL violated `BR-CO-15`
  and CII `BR-53`. Amounts do not change, the original export is untouched.
- The container check of a Factur-X PDF reports `FX-MULTIPLE-INVOICES` when two different file
  specifications with the name of the invoice XML carry different content; the same specification
  from `/AF` and the name tree still counts once.

- Foreign PDF attachments with a MIME type that has parameters (`text/plain;charset=UTF-8`) are
  listed with the type without parameters; an invalid type no longer stops the whole list.
- Order-X dates with a time (UNTDID 2379 formats 203 and 204) are checked completely; an invalid
  hour, minute, or second is rejected with the position of the field.

- The normalized CII and UBL exports write card, debited account, and payer BIC only once and no empty
  payer account block (UBL: no empty payer account in the mandate), so several credit transfer accounts of a payment with a mandate are read
  again completely.
- CII with the same currency for the invoice and the VAT accounting currency (BT-6 equal to
  BT-5) is written with one VAT total and read into a complete state (BT-111 equals BT-110); the
  export of such an invoice could not be read before.
- `paniolo attachments --extract` no longer overwrites: attachments with the same file name get a
  numeric suffix (`proof.txt`, `proof-2.txt`), existing files stop the command before anything is
  written, and symbolic links are never followed. `--overwrite` replaces existing regular files;
  the fifth column of the listing names the file written.
- The deploy job of the CI waits for the browser test of the visualizer.

- Reading valid documents of other senders that the model rejected: an address with the country only
  (the invoice requires an address, not more than its country; city and post code stay with the
  XRechnung validation); an empty optional delivery block of a CII document; a line with sub-lines of
  the XRechnung extension without quantity, and a sub-line with a quantity without unit (the unit
  stays required for the lines of an invoice, now with the line and the field in the message);
  repeated line identifiers (lines are kept by position; only the EXTENDED line hierarchy needs
  unique ones); a direct debit with only some of its three values (XRechnung still requires all
  of them); a direct debit next to a credit transfer (excluded by BR-DE-23 only).
- Lenient reading derives the gross price (BT-148) of a UBL line that states a price discount
  (BT-147) without it, as net price plus discount, and reports `READ-GROSS-PRICE`; strict reading
  rejects the line naming both terms. A stated line amount with the opposite sign of quantity x net
  price outside the profiles that allow a negative net price is read with the negated quantity and
  reported as `READ-LINE-QUANTITY`; an amount the negated quantity does not explain is rejected as
  before. The unsupported VAT category `B` of the Italian split payment is rejected with a message
  that names the supported categories.

- ZUGFeRD 1.0 documents whose VAT blocks state less than the model needs are read or rejected with
  a message that names the location, instead of failing with `NullPointerException: tax category
  code` and `NullPointerException: tax` (Mustang issue #938). ZUGFeRD 1.0 does not require the VAT
  category code of a block, nor a VAT block at a line, allowance, charge, or logistics service
  charge. What the document leaves no doubt about is taken and reported as `READ-TAX-CATEGORY` in
  both modes: a rate above zero is the standard rate (S), and an element without VAT block belongs
  to the only VAT breakdown of the document. A rate of zero, no rate, or several breakdowns to
  choose from are rejected with the location; no category is guessed. Charges on the gross price
  stay a separate restriction of the migration: their message now names the line and the element.
- XML white space inside the Base64 text of an embedded attachment is accepted anywhere, also
  between the padding characters as in `MQ= =` (Mustang issue #792): the MIME decoder rejected that
  document, valid in the official XRechnung validation, with `Input byte array has wrong 4-byte
  ending unit`. `XmlElement.base64()` removes exactly the white space of `xs:base64Binary` (space,
  tab, carriage return, line feed) and decodes the rest strictly, for the readers of CII, UBL,
  Order-X, and the despatch advices. Other characters, wrong or misplaced padding, missing padding,
  and cut off data are rejected with `Invalid Base64 in <location of the element>`; before, the MIME
  decoder skipped other characters without a message and accepted a missing padding.
- A subtotal (GROUP) or information line of Factur-X EXTENDED may state a VAT block with the code
  of the date its VAT becomes due only, without type and category (Mustang issue #1216, Factur-X
  1.07.3). Both read modes failed with `NullPointerException: tax category code`. The reader gives
  such a line no VAT category and keeps the code as `LineExtension.taxDueDateTypeCode()`; the
  writer states it again at the same line, and it never becomes the tax point of the document.
  A detail line without VAT category is still rejected, now with `Line 2: VAT category code
  (BT-151) is missing`. The visualizer shows the code at the line; the PDF leaves it out.
- Addresses without street are read and written (Mustang issue #222). EN 16931 requires the
  country of an address only, XRechnung also city and post code of seller and buyer, but the model
  required street, city, and post code together and rejected such valid documents in both read
  modes with `street must not be blank`. `Address` now requires the country only; a value that is
  set must not be blank, and `isCountryOnly()` is true only without any other value. Seller and
  buyer still need an address beyond the country outside the profile MINIMUM. What a profile
  requires beyond that is reported by validation, e.g. BR-DE-3.
- A stated VAT category tax amount (BT-117) rounded per line instead of once per category is kept
  (Mustang issues #1262, #641): strict reading rejected such valid documents for a difference of
  0.01, lenient reading recalculated and a normalized export changed the amount due. The readers
  keep a stated amount that differs by no more than 0.01 per line, allowance, and charge of the
  category (BR-FXEXT-S-09) as `Invoice.vatAmounts()`; senders state one with
  `Invoice.Builder.vatAmount(TaxCategory, BigDecimal)`. A larger difference, which BR-CO-17 accepts
  up to 1.00 in EN 16931, is still rejected by strict and reported by lenient reading. Gross price
  invoices are not supported: the BMF letter of 15 October 2026 refers them to a format outside the
  EN 16931 core model.
- Factur-X EXTENDED notes stated by their content code only (BR-FXEXT-01, BR-FXEXT-02) are read
  and written; both read modes rejected them with `note must not be blank`. `InvoiceNote` takes a
  text, a content code, or both; `Invoice.build()` requires the text outside EXTENDED and
  EXTENDED-CTC-FR. A line note subject code needs the note text or its content code.
- The visualizer shows the remaining EXTENDED metadata with label and code in German, English, and
  French: document language, subject and content code of document and line notes, the account type
  of accounting references, and the line status code, which it no longer hides behind the kind of
  line. The PDF leaves these codes out as before and shows a note stated by its content code only
  as `Note code …`.
- Factur-X EXTENDED and EXTENDED-CTC-FR invoices with negative item prices are read and written
  (Mustang issues #1285, #1262, #641). Their rules have no BR-27 and BR-28, so such documents are
  valid; Paniolo rejected them in both read modes with `netUnitPrice must not be negative`. Lenient
  reading may also recalculate a negative net price there. All other profiles keep both rules.

- Factur-X / ZUGFeRD PDFs: ZUGFeRD 2.0 metadata (XMP namespace
  `urn:zugferd:pdfa:CrossIndustryDocument:invoice:2p0#`, `zugferd-invoice.xml`) are recognized.
  Before, the file name `zugferd-invoice.xml` was compared without case with the ZUGFeRD 1.0 name
  `ZUGFeRD-invoice.xml`, and the ZUGFeRD 1.0 properties were reported missing (`FX-DOCUMENT-TYPE`,
  `FX-DOCUMENT-FILE-NAME`, `FX-CONFORMANCE-LEVEL`, `FX-EXTENSION-SCHEMA`), e.g. for the Mustang
  test file `attributeBasedXMP_zugferd_2p0_EN16931_Einfach.pdf`. Container check and extraction
  now share one mapping of the generations Factur-X, ZUGFeRD 2.0, and ZUGFeRD 1.0 to namespace,
  file name, version, levels, and XML root; the generation follows from the namespace of the XMP
  properties, never from the file name or the prefix, and unknown namespaces are not accepted.
  ZUGFeRD 2.0 states `Version` `2p0`; `1.0`, which some ZUGFeRD 2.0 tools wrote, gives the warning
  `FX-VERSION`, other values an error. New errors: `FX-XMP-GENERATIONS` for properties of several
  generations in one XMP packet, `FX-XML-ROOT` for an embedded XML of another generation (e.g.
  ZUGFeRD 1.0 `CrossIndustryDocument` with Factur-X metadata). ZUGFeRD 1.0 metadata now also get
  the checks `FX-FILE-NAME` (exactly `ZUGFeRD-invoice.xml`) and `FX-VERSION` (`1.0`, otherwise a
  warning). The profile XRECHNUNG still requires `xrechnung.xml`. See
  `paniolo-facturx/README.md#xmp-metadata-generations`.

- Validation: a document whose specification identifier (BT-24) states XRechnung, but that no
  supported XRechnung version and scenario accepts for its document type, is invalid with the new
  error `XR-SPECIFICATION-UNSUPPORTED` (location BT-24; the message names the identifier, the
  reason and the supported identifiers). Before, such a document was validated against the current
  XRechnung rules and stayed valid with the warning `BR-DE-21`, e.g. for
  `...#urn:xoev-de:kosit:standard:xrechnung_3.0`, `...#urn:xeinkauf.de:kosit:xrechnung_9.9` or
  `...xrechnung_3.0#garbage` (Mustang issue 1343). The identifier is now compared exactly, as by
  the KoSIT validator, with a central table of the identifiers of every packaged release (3.0 and
  1.2 to 2.3), which a test keeps equal to their `scenarios.xml`. The document is still checked
  against the current rules, so their findings stay visible. This also applies to
  `validate(xml, ValidationProfile.Syntax)`. Plain EN 16931, Peppol BIS Billing, Factur-X and
  EN 16931:2026 (`EN16931-2026-NOT-SUPPORTED`) are unchanged. Changed expectations:
  - Unknown versions (e.g. XRechnung 1.1), other spellings, surrounding whitespace and appended
    parts of a supported identifier are now invalid instead of valid with `BR-DE-21`.
  - The extension of an older version for a document type without extension scenario
    (XRechnung 2.0 / 2.1 CII, XRechnung 1.2, UBL CreditNote) is invalid instead of being checked
    as the standard scenario: the KoSIT release of that version has no such scenario.
  - The XRechnung CVD identifier (`...xrechnung_3.0#compliant#urn:xeinkauf.de:kosit:xrechnung:cvd_0.9`)
    is validated with the KoSIT CVD scenario and its severity overrides instead of the standard one.
  - With `Builder.rules(bundle)` the accepted identifiers are read from the `scenarios.xml` of the
    bundle (`ValidationProfile.xrechnungSpecifications(bundle)`) and take precedence over those of
    the packaged older releases. To accept another identifier the bundle names it; no change of
    Paniolo is needed.
- Validation: a standalone invoice whose specification identifier (BT-24) states plain EN 16931,
  Peppol BIS Billing or an unknown identifier, not XRechnung, reports the findings of the
  XRechnung rules (`BR-DE`, `BR-DEX`, `BR-TMP`, and `PEPPOL-EN16931` except for Peppol) as
  information, with the information `XR-NOT-STATED`. XRechnung is a CIUS of EN 16931 and does not
  apply to such a document. A ZUGFeRD EN 16931 invoice without buyer reference was valid as PDF
  but invalid as extracted XML (FATAL `BR-DE-15`); Mustang reports it as information as well.
  Documents that state XRechnung are validated as before. To keep rejecting, use
  `AcceptancePolicy.officialRules().rejectOn("BR-DE-15")`. The optional calculation check keeps its
  warning when the matching official rule (e.g. `PEPPOL-EN16931-R120`) is only information. 59
  official EN 16931 examples of the public corpus are now valid in the automatic validation.
- Reading: empty optional elements count as absent instead of rejecting a valid invoice, in both
  read modes and without a `ReadWarning`. A seller written as `<ram:SellerTradeParty><ram:ID/>...`
  failed with `party identifier must not be blank`. This applies to party identifiers
  (`ram:ID`, `ram:GlobalID`, `cac:PartyIdentification/cbc:ID`), electronic addresses
  (`ram:URIID`, `cbc:EndpointID`), legal registration identifiers, the deliver-to location, and
  item and object identifiers in the CII, UBL and ZUGFeRD 1.0 readers. Validation of XRechnung
  and Peppol documents still reports empty elements (`PEPPOL-EN16931-R008`).
- Model: payment means other than `30`, `58`, `42`, `48`, `54`, `55` and `59` may carry credit
  transfer accounts (BG-17), payment card information (BG-18) and a direct debit mandate (BG-19).
  Invoices with `ZZZ` (mutually defined) and e.g. an account failed to read with
  `Payment means ZZZ does not take accounts, cards or mandates`; the same applied to `49` (direct
  debit) with a mandate. EN 16931 forbids these groups for no code, and XRechnung restricts only
  the named codes (BR-DE-23 to BR-DE-25), which the validation still reports.
- Model: payment means `42` (payment to bank account) may name credit transfer accounts (BG-17).
  ZUGFeRD 1.0 invoices with `42` and a `PayeePartyCreditorFinancialAccount` failed with
  `Payment means 42 does not take accounts, cards or mandates`. EN 16931 requires accounts for
  `30`/`58` only (BR-61) and forbids them for no code; the code is kept as stated, not changed to
  `58`, also when migrating ZUGFeRD 1.0.

## [1.0.0-rc01] - 2026-11-01

First release candidate, published to Maven Central under the group ID `io.github.nalukit`. See
the [API reference for 1.0.0-rc01](docs/api/1.0.0-rc01.md).

[Unreleased]: https://github.com/NaluKit/paniolo/compare/1.0.0-rc02...HEAD
[1.0.0-rc02]: https://github.com/NaluKit/paniolo/releases/tag/1.0.0-rc02
[1.0.0-rc01]: https://github.com/NaluKit/paniolo/releases/tag/1.0.0-rc01

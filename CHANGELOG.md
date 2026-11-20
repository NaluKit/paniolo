# Changelog

All notable changes to Paniolo are documented in this file. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/). Each release also has its own API
reference in `docs/api/<version>.md`.

## [Unreleased]

### Fixed

- A stated line amount (BT-131) was accepted if it differed from quantity x net price by no more
  than half a cent per unit of quantity, whatever the decimals of the price. A price stated with
  four or five decimals is not rounded to cents, so a document could carry a line amount that no
  rounding of the price explains (for example 20 x 2.7700 stated as 55.46, or a difference of more
  than 4,000 for a quantity of 800,000 at a price of 0.01146) without any message. The tolerance now
  is half a unit of the last stated decimal of the price (at least half a cent) per unit of
  quantity, plus half a cent for the amount. A price in cents explains as much as before; a more
  precise price explains less, and the strict mode rejects the line, while the lenient mode
  recalculates the net price and reports `READ-LINE-NET-PRICE` as for every other deviating line.
- A stated line amount that the rounding of the price explains, but that differs from quantity x
  net price, is now listed in `ReadResult.deviations()` as BT-131 with the line, the stated amount
  and the calculated amount. Before, such a line was accepted without a trace. The amount and the
  price of the line stay as stated; `isExact()` is unchanged (no warning).
- The CII and ZUGFeRD 1.0 readers dropped the scheme of a payee identifier (BT-60-1) and of the
  deliver-to location identifier (BT-71-1) written as `ram:ID schemeID="…"`. The scheme is now
  kept. The scheme `SEPA`, which the model reserves for the creditor identifier, is still not
  kept on a payee, but is now reported as `READ-IDENTIFIER`.
- The ZUGFeRD 1.0 reader read the deliver-to location identifier (BT-71) only from `ram:ID`. It now
  also reads `ram:GlobalID` with its scheme and reports a second identifier as `READ-IDENTIFIER`,
  as the CII reader does.
- The ZUGFeRD 1.0 reader lost the further identifiers of a party (for example a global identifier
  next to `ram:ID`) when it took the party over into a profile other than EXTENDED.
- The warning for a dropped VAT identifier or tax number now also covers the EXTENDED line parties
  (manufacturer, item seller, ship-to, ultimate ship-to).
- The message for an unsupported VAT category, and the JavaDoc of `TaxCategory`, claimed that
  EN 16931 knows only the categories S, Z, E, AE, K, G, O, L and M. The EN 16931 code list also
  contains B (Italian split payment). The message now says that Paniolo does not implement the
  category; the reader still rejects it and does not map it to another category.
- The CII reader dropped the scheme of a party identifier written as `ram:ID schemeID="…"` (seller
  or buyer, BT-29-1 / BT-46-1, also further identifiers), which the CII schema and the XRechnung
  extension (BR-DEX-05) allow. The scheme is now kept in the model; the normal export writes the
  qualified identifier as `ram:GlobalID` with its scheme.
- The CII and ZUGFeRD 1.0 readers kept only the last of several different VAT identifiers or tax
  numbers of a party without a message, because the model has one of each. The identifier not kept
  is now reported as `READ-IDENTIFIER` in both read modes, with the party path and the scheme. The
  kept value is unchanged; the same value stated twice and empty repetitions are not reported.
- The CLI reported success when standard output could not be written, for example after a closed
  pipe, because a print stream keeps a failed write instead of throwing it. A failed write or flush
  of standard output now ends with exit code 3 and a message on standard error, also where the
  command would have returned 0 or 1.

### Added

- Regression tests (`VatRoundingTest`) for a stated VAT amount that is one cent off the VAT
  calculated once per category on a single line, in both directions (710.79 x 19 % stated as 135.04,
  3986.34 x 19 % stated as 757.41, both from public corpus documents): the amount is kept in every
  read mode without warning or deviation, the original and the normalized export state it, the
  validation of every profile accepts it; two cents are rejected in strict reading. The behaviour
  was already so; the tests and the README now state it.
- ZUGFeRD 1.0 BASIC: a total of allowances (BT-107) without any allowance element, for example in
  a correction invoice, made the model state other totals than the document (a tax basis of -7.90
  instead of -7.67), and only the VAT total was reported. Paniolo does not invent an allowance for
  it, because amount, sign and reason would be guesses. The new warning `READ-ALLOWANCE-CHARGE-TOTAL`
  now names the stated and the listed total of allowances (BT-107) or charges (BT-108), in both
  read modes. `ReadResult.requireNoDeviations()` and the CLI option `read --fail-on-deviation`
  refuse a result whose invoice states other amounts than the document; `read` now also prints the
  deviations (`deviation BT-107: document states -0.23, invoice has 0.00`) next to the warnings.
- Documented: the CLI commands that write a document read their input strictly; converting a
  document with deviating amounts takes `read --lenient` and `to-xml` on the JSON, see the README
  section "Writing after a lenient read".
- `ReadMode.LENIENT_KEEP_LINES` and the CLI option `read --keep-line-values`: read like
  `LENIENT`, but reject a line whose net price (BT-146) or quantity (BT-129) would have to change to
  fit its stated net amount, instead of recalculating the price (`READ-LINE-NET-PRICE`) or negating
  the quantity (`READ-LINE-QUANTITY`). All other deviations stay accepted and reported as in
  `LENIENT`. The change of a price or quantity in `LENIENT` is unchanged and, as before, reported
  as warning and as deviation (BT-146, BT-129) of the result, never inside the written XML.
- Document regressions for the line amount at the half cent (quantity 1, net price 1, price base
  quantity 200.0000001, amount 0.00): `LineRoundingDocumentTest` (invoice in UBL and CII),
  `OrderLineRoundingTest` (Order-X), and a test in `QuotationJsonTest` (quotation, its invoice, and
  the JSON round trip) check the line amount and the document totals in the model, in the written
  XML, and after reading back, against fixed values.
- `CliProcessIT` starts the packaged `paniolo-cli-*-all.jar` with `java -jar` as a real process
  after `package`: version, exit codes 0, 1, 2, and 3, byte-exact `extract-xml` output with
  separate standard error, PDF/A conformance through the merged ServiceLoader entries, the limit
  of attachments, and a failed write to standard output. The child process has only the JAR on its
  class path and a time budget of 60 seconds.

### Changed

- The net amount of an invoice, order, or quotation line is rounded to cents once. It was divided
  to ten decimals first and rounded again, which could move an amount by one cent near a half cent,
  for example quantity 1, net price 1, and price base quantity 200.0000001 (now 0.00, was 0.01).
  Amounts and totals of such edge cases can change by one cent. The check of a stated line amount
  against quantity x price is unchanged. New: `ModelChecks.lineNetAmount` and
  `LineAdjustment.netEffect`.
- The PDF reader of the visualizer limits the bytes that Flate streams inflate to: 64 MiB per
  stream and 256 MiB per document, counted over all streams and all stages of a filter chain and
  checked while inflating. A PDF over a limit ends with the new error code `pdfTooLarge` (message in
  German, English and French); it is no longer read in part, and a skipped object stream or embedded
  file no longer hides it. The limits are the exported object `pdfLimits` of the module and can be
  changed before a document is loaded.
- `Paniolo.attachments(byte[])` and the CLI command `attachments` now limit the attachments of one
  PDF in total: at most 1,000 files and 256 MiB of content together, counted by the bytes actually
  read, in addition to the existing limit of 100 MiB per file. A PDF over a limit is rejected with
  an `IllegalArgumentException` (exit code 3 in the CLI); several file specifications that share one
  embedded stream each count in full. PDFs with more or larger attachments, which were read before,
  are now rejected.
- Embedded files and the XMP metadata of a PDF are decoded with the limit applied while decoding,
  filter stage by filter stage, so a small PDF with a highly compressed stream can no longer expand
  into the heap before the limit is checked (the decoding of PDFBox expanded the whole stream first;
  a PDF of 1 MiB with 1 GiB of content ended in an `OutOfMemoryError`). Only the filters Flate, LZW,
  ASCII85, ASCIIHex, and RunLength are decoded; a file with another filter is refused. A filter that
  is listed twice is applied once, as PDFBox does. XMP metadata over 20 MiB counts as missing.
  PDFBox itself still expands object streams while a PDF is loaded; that is not limited here.
- The predictor of an embedded file (`/DecodeParms` with `Predictor`) is checked before decoding:
  PDFBox allocates two buffers of one row (`Columns` x `Colors` x `BitsPerComponent`) before it
  writes a byte, so a small PDF with `Columns` 2000000000 ended in an `OutOfMemoryError` although
  the output was limited. Accepted are the predictors 2 and 10 to 15, `Columns` of 1 or more,
  `Colors` of 1 to 32, `BitsPerComponent` of 1, 2, 4, 8, or 16, and a row of up to 8 MiB that also
  fits in the limit of the stage; the row length is calculated without integer overflow. Other
  values are refused; a predictor of 1 or less is ignored as in PDFBox. The parameters are taken
  per stage like PDFBox does (including a filter listed twice) and passed to the decoder as checked.

## [1.0.0] - 2026-11-18

First stable release. See the [API reference for 1.0.0](docs/api/1.0.0.md).

### Added

- Regression tests for a shared facade under concurrent XML and PDF processing:
  `ConcurrentProcessingTest` checks cold validation setup, separation of findings and attachments,
  recovery after failed requests, and repeated read/write round trips in a child JVM with a
  128 MiB heap. `ConcurrentFacturXTest` checks parallel PDF creation and import in the profiles
  EN 16931 and EXTENDED. These are bounded regression checks, not a guarantee for arbitrary
  document sizes or sustained workloads.

- `Quotation` (German "Angebot") in the new package `model.quotation` of `paniolo-shared-model`:
  the offer of a seller to a buyer with validity date, subject, delivery and payment terms text,
  allowances and charges, and `QuotationLine`s that are standard or optional. A quotation is no
  e-invoice document and has no XML syntax. `totals()` calculates as for the invoice; optional
  lines count only if accepted. `toInvoice(number, issueDate)` creates the invoice of the standard
  offer, `toInvoice(number, issueDate, acceptedOptionalLineIds)` adds the accepted optional lines,
  and `toInvoiceBuilder(...)` returns the invoice builder to complete payment instruction, due
  date, or the EXTENDED `reference()` of the quotation before building.

- `Paniolo.toPdf(Quotation)` and `toPdf(Quotation, PdfLayout)` render a quotation as visual PDF/A-3b
  document without embedded XML (`paniolo-facturx`, German, English, and French): heading
  "Angebot", quotation number and date, validity, the lines of the standard offer with totals that
  end with the total amount with VAT, the optional lines apart from the totals, and delivery and
  payment terms. It shows no payment instruction and no amount due. The SPI `PdfLayoutSupport`
  has the new default method `render(Quotation, PdfLayout)`.

- `PanioloJson` stores and loads a `Quotation` like the other documents: `json.toJson(quotation)`
  and `json.read(text, Quotation.class)`. The JSON has the record components of `Quotation` and
  `QuotationLine` (with the flag `optional`); loading applies the checks of the model.

- The CLI has the command group `paniolo quotation`: `to-pdf FILE` renders a quotation as PDF
  (with the layout options of `to-pdf`), and `to-invoice FILE --number NO [--date DATE]
  [--accept ID]...` writes the invoice of the standard offer plus the accepted optional lines as
  JSON, which `to-xml`, `to-facturx`, and `to-pdf` take on.

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

- `CreditTransfer` keeps the kind of the payment account identifier (BT-84): the new component
  `idType` (`AccountIdType.IBAN` or `PROPRIETARY`) tells an IBAN from another account number.
  `CreditTransfer.proprietary(id, name, bic)` and `CreditTransfer.Builder.proprietaryId(String)`
  state a proprietary account number, e.g. `EXAMPLE-ACCOUNT`; the builder rejects an `iban` and a
  `proprietaryId` together. The CII writer writes it as `ram:ProprietaryID` and an IBAN as
  `ram:IBANID`, never both; the CII and ZUGFeRD 1.0 readers keep the kind, so import and normalized
  export no longer turn a `ProprietaryID` into an `IBANID`. An account with both elements is
  rejected in strict mode; lenient reading keeps the IBAN and reports the other identifier as
  `READ-IDENTIFIER`. UBL has `cbc:ID` for both and carries no kind; accounts read from UBL are
  IBAN accounts. The constructor `new CreditTransfer(iban, accountName, bic)` and `Builder.iban`
  still create IBAN accounts. JSON stores `idType` only for a proprietary account; JSON without it
  is read as IBAN, so stored documents are unchanged. The PDF layout labels a proprietary account
  "Account no." (Kontonummer, N° de compte) and does not group it like an IBAN. With payment means
  `58` the official rules warn that BT-84 should be an IBAN (BR-DE-19). Reported as Mustang issue
  1348.

- `PdfLayout.withFoldMarks(boolean)` shows or hides the fold and hole marks of DIN 5008 form B:
  small gray lines at the left edge of page 1, 105 mm and 210 mm from the top (fold) and at
  148.5 mm (hole). Folded at the marks, the address lies in the window of a DL envelope. They are
  shown by default; hide them for letter paper with preprinted marks. Documents without address
  window, such as validation reports, have none.

- `PdfLayout.withAccentColor(String)` takes the accent color as a web color, e.g. `"#01244F"`
  (six hexadecimal digits, `#` optional). Anything else, such as a short form or a color name,
  fails with an `IllegalArgumentException`. `withAccentColor(int)` is unchanged.

### Changed

- Documentation now distinguishes supported insurance tax (`AAM`) from unsupported tax types,
  describes payment account kinds and their UBL limitation, and covers quotations, selectable PDF
  profiles, cash discount calculation, and read warnings in strict mode. The CLI guide includes
  PDF input from standard input and unchanged XML output to standard output.
- Release verification documentation no longer guarantees byte-identical rebuilds. Fixed archive
  timestamps alone do not fix toolchain, manifest, generated JavaDoc, or SBOM differences; an
  independent rc02 rebuild found equal class bytes but different archive bytes. Signatures,
  provenance, downloaded-asset checksums, and rebuild comparison are separate checks.

- The first page of a credit note (type codes 381 and 261) names its facts "Gutschriftsnummer" and
  "Gutschriftsdatum" (English "Credit note no.", French "N° d'avoir") instead of the invoice
  wording. A debit note (383) does the same with "Belastungsnummer" and
  "Belastungsdatum" (English "Debit note no.", French "N° de note de débit"). Other
  document types keep their labels.

- The records `ExtendedInformation` and `LineExtension` have the new last component `otherTaxes`;
  the builders and JSON without it are unchanged.

- The record `CreditTransfer` has the new last component `idType`; the canonical constructor takes
  it as last argument, the constructor with three arguments is unchanged.

- The record `PdfLayout` has the new components `profile` and `foldMarks`; the canonical constructor
  takes them as last arguments. `of`, `defaults`, and the `with` methods are unchanged.

- The rendered PDFs use IBM Plex Sans Regular and Bold (SIL Open Font License 1.1), packaged with
  `paniolo-facturx`, instead of Liberation Sans of PDFBox. Bold text uses the real bold face instead
  of emboldening the regular font. Digits are slightly wider, so line and page breaks of existing
  documents can differ. Own fonts of `PdfLayout.withFonts` are unchanged.

- `PdfLayout.withFonts` fonts whose embedding permissions (`fsType`) forbid embedding or subsetting
  are rejected with a message naming the restriction instead of "no readable TrueType font" or an
  error while saving the PDF. The PDF layout documentation explains the license of own fonts.

- The heading on the first page of the PDFs of invoices, orders and delivery notes shows only the
  document type (e.g. "Rechnung") instead of type and number. The number was shown twice, because
  the fact block next to the heading already contains it. The continuation header of the following
  pages and the PDF title metadata keep the number. The validation report is unchanged; its heading
  contains the file name.

- veraPDF 1.30.4 instead of 1.30.3. The update fixes a local file disclosure through custom XSLT
  policies (GHSA-73f4-hgcx-xc59), PDF/UA rule handling and a parser race condition, and restores
  the original Adobe XMP Toolkit license in `verapdf-xmp-core`. The dependencies are otherwise
  unchanged.

- The executable CLI JAR carries the complete third-party notices of StAX-Utils (BSD-3-Clause,
  Christian Niles / Unit12, Sun Microsystems, John Kristian) and of veraPDF XMP Library Core
  (BSD-3-Clause, Adobe Systems and veraPDF Consortium) in `META-INF/licenses/`, and
  `META-INF/THIRD-PARTY.txt` links the Saxon-HE sources of the bundled version instead of the
  Saxon 12 source archive. The integration test `LicenseNoticesIT` checks these notices in the
  packaged JAR.

- The model applies the payment rule of EN 16931 only: a credit transfer (30/58) requires an
  account (BR-61). A card next to the credit transfer account (official Factur-X example
  `factur-x-en16931.xml`), a payment card code without card information, and a direct debit (59)
  that names the payee account are kept; `Invoice` no longer restricts a card next to the account
  to EXTENDED-CTC-FR. The XRechnung rules BR-DE-23 to BR-DE-25 are reported by validation.

- The item seller of a line (EXTENDED) and the subtype of a sub-line (BR-FXEXT-06) are no longer
  checked by the model: the official French examples name item sellers by legal registration only
  without the multi-vendor identifier, and the sub-invoice lines of the XRechnung extension in CII
  state no subtype. Validation reports the rules of the profile.

- Lenient reading keeps rate zero for a VAT category without rate other than zero (Z, E, AE, K,
  G) that states another rate or none, e.g. an exempt VAT breakdown with 19 % and VAT amount zero,
  and reports the new warning `READ-TAX-RATE`; strict reading rejects it with the location. An
  invoicing period with an invalid date or an end before its start is dropped with the new warning
  `READ-PERIOD` in lenient reading.

- A payment means block without code (BT-81), which ZUGFeRD 1.0 allows, is read as `1`
  (instrument not defined) with the warning `READ-PAYMENT-MEANS`; UBL and CII require the code,
  so strict reading rejects it there. ZUGFeRD 1.0 direct debits are migrated with mandate
  reference, creditor identifier (scheme agency of the payment means identifier), and debited
  account instead of being rejected.

- The message that rejects EXTENDED information outside EXTENDED names the content, e.g.
  `seller contact (name and department), line 1 (gross price allowance or charge details)`, and the
  profile of the invoice. Extracting the invoice of a PDF that carries an Order-X document or a
  despatch advice instead names that file.

### Fixed

- The visual invoice shows document-level taxes of another type than VAT (the insurance tax `AAM`
  of the EXTENDED profile) as own row in the totals, with rate and basis amount if stated, so the
  rows add up to the total with taxes; before, only the VAT rows were shown.

- `Country` has every ISO 3166-1 alpha-2 code and the code XI of the EN 16931 code list; reading
  the French overseas departments (e.g. RE in the official Facture_DOM examples) failed with
  "Unknown country".

- UBL dates in the lexical form of `xs:date` with time zone, e.g. `2016-04-04+01:00`, are read.

- The UBL reader takes the seller tax registration identifier (BT-32) from a `PartyTaxScheme` of
  every scheme other than VAT, e.g. `TAX` of PEPPOL BIS, not only from `FC`.

- Readers report a required value the document lacks, e.g. an allowance without amount or a
  sub-invoice line without VAT category, as `IllegalArgumentException` that names it instead of a
  `NullPointerException`.

- Writing the Factur-X profile MINIMUM rejects a postal address of the buyer (BG-8), also one with
  the country only, and the buyer VAT identifier (BT-48), which the MINIMUM schematron of
  Factur-X 1.09 marks as not used. Before, such an invoice was written and failed its own
  validation.

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

[Unreleased]: https://github.com/NaluKit/paniolo/compare/1.0.0...HEAD
[1.0.0]: https://github.com/NaluKit/paniolo/releases/tag/1.0.0
[1.0.0-rc02]: https://github.com/NaluKit/paniolo/releases/tag/1.0.0-rc02
[1.0.0-rc01]: https://github.com/NaluKit/paniolo/releases/tag/1.0.0-rc01

# Changelog

All notable changes to Paniolo are documented in this file. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/). Each release also has its own API
reference in `docs/api/<version>.md`.

## [Unreleased]

### Added

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

[Unreleased]: https://github.com/NaluKit/paniolo/compare/1.0.0-rc01...HEAD
[1.0.0-rc01]: https://github.com/NaluKit/paniolo/releases/tag/1.0.0-rc01

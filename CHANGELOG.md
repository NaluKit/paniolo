# Changelog

All notable changes to Paniolo are documented in this file. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/). Each release also has its own API
reference in `docs/api/<version>.md`.

## [Unreleased]

### Changed

- Validation report PDF (`toReportPdf`, `paniolo validate -f pdf`) laid out as conformance report:
  a verdict at the top in the color of the result (conform, conform with warnings, not conform –
  rejection recommended), the number of findings per severity, the validation stages with their
  findings and result, Schematron and PDF/A statistics when the validation collected them, and
  every finding as a block with severity, rule, message and location. Findings are now grouped by
  stage like the XML report. A report without findings shows the conform verdict.

### Fixed

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

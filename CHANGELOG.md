# Changelog

All notable changes to Paniolo are documented in this file. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/). Each release also has its own API
reference in `docs/api/<version>.md`.

## [Unreleased]

### Fixed

- Reading: empty optional elements count as absent instead of rejecting a valid invoice, in both
  read modes and without a `ReadWarning`. A seller written as `<ram:SellerTradeParty><ram:ID/>...`
  failed with `party identifier must not be blank`. This applies to party identifiers
  (`ram:ID`, `ram:GlobalID`, `cac:PartyIdentification/cbc:ID`), electronic addresses
  (`ram:URIID`, `cbc:EndpointID`), legal registration identifiers, the deliver-to location, and
  item and object identifiers in the CII, UBL and ZUGFeRD 1.0 readers. Validation of XRechnung
  and Peppol documents still reports empty elements (`PEPPOL-EN16931-R008`).
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

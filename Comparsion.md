# Library Comparison

Reviewed **26 November 2026**. Paniolo: local revision
`f6015e257cbb01ddfe2fbf38fb058071aed6632e` (`HEAD-SNAPSHOT`), based on the
**1.0.1** tag; only the iteration version in the parent POM differs from that tag.
Mustangproject: **2.26.0**, still the latest release shown by its release page at review.
Mustang tagged sources and all other projects' public documentation were fetched again for this
review. Their development branches are documentation baselines, not tested binary releases.
This review also runs Paniolo's current reactor and invoice-corpus regression tests, including
the adapter against Mustang 2.26.0. It is not a new comparative performance or full interoperability
benchmark, and it does not establish current behavior for every external tool.
“Not documented” means that the reviewed sources do not establish the feature; it does not
prove that an integration could not implement it.

## What distinguishes Paniolo

Paniolo combines an immutable business model, direct UBL and CII readers/writers, packaged
validation rules, optional PDF processing, and an embeddable browser viewer. Its main
advantage over Mustangproject is the integration architecture and the explicit handling of
model changes and mapping losses. **Order-X, delivery notes, PDF rendering, arithmetic checks,
and a CLI are shared capabilities**, rather than exclusive Paniolo features.

- **One immutable model:** invoices, orders, quotations, and delivery notes use records and builders,
  accessed through the thread-safe `Paniolo` facade. Builders belong to one operation.
- **Direct UBL and CII output:** both invoice syntaxes are written from the same model.
  Mustang's UBL output uses its CII-to-UBL conversion path.
- **PDF is optional:** XML users do not need PDFBox, FOP, or veraPDF. The domain model in
  `paniolo-shared-model` has no third-party runtime dependencies; `paniolo` uses Saxon.
  The standalone viewer module also declares no third-party runtime dependency.
- **Explicit received amounts:** `importInvoice` keeps the original XML, stated line amounts
  and totals, the calculated model, and deviations. Original and normalized export are explicit
  choices. Plain `read(..., LENIENT)` can normalize amounts; it is not a preservation API.
  In 1.0.1, `LENIENT_KEEP_LINES` rejects a line instead of changing its price or quantity;
  `requireNoDeviations()` can reject any reported amount difference.
- **Browser integration:** `<paniolo-viewer>` reads XML or PDF bytes locally, displays business
  data and attachments, and can display Paniolo, KoSIT, and SVRL validation reports. The component
  displays data; it does not execute invoice validation in the browser.
- **Explicit migration decisions:** `paniolo-mustang` reports values it cannot carry over;
  the strict conversion refuses a mapping with losses. It does not replace Mustang's API.

Sources: [scope and API](README.md#scope), [module POMs](pom.xml),
[import API](paniolo/src/main/java/io/github/nalukit/paniolo/Paniolo.java),
[viewer](paniolo-visualizer/README.md), [adapter](paniolo-mustang/README.md).

## Paniolo and Mustangproject in detail

| Area | Paniolo, reviewed 1.0.1-based revision | Mustangproject 2.26.0 | Practical difference |
| --- | --- | --- | --- |
| Integration model | Immutable records, builders, one facade | Mutable invoice classes and `IExportableTransaction` interfaces | Paniolo centralizes model checks; Mustang can expose existing application objects through interfaces. [P1, M1] |
| Java baseline | Java 21 required | Library POM targets Java 11; dependency/runtime compatibility must also be checked | Paniolo requires an upgrade or a separate Java 21 service for older applications. Mustang's recommended build JDK is not its declared bytecode target. [P2, M2] |
| Invoice XML | Read/write UBL 2.1 Invoice/CreditNote and CII D16B | CII import/export, UBL import, CII-to-UBL conversion | Direct writers in Paniolo versus a conversion stage for Mustang UBL output. Neither approach proves lossless conversion of all source extensions. [P1, M1, M3] |
| Factur-X / ZUGFeRD 2 | MINIMUM, BASIC WL, BASIC, EN 16931, EXTENDED, XRECHNUNG | Corresponding profile support | Select and test the actual profile and fields; a profile name alone does not establish field parity. [P1, M4] |
| French EXTENDED-CTC-FR | Writing, reading, EXTENDED schema, CTC-FR and BR-FR rules, including documented multi-vendor content | CTC-FR profile exposed in CLI | Shared profile name; full French field/rule parity has not been established here. [P1, M4] |
| ZUGFeRD 1.0 | Read, validate, migrate to version 2; no new version-1 export | Version-1 provider, validation and upgrade paths | A concrete Mustang capability outside Paniolo's export scope. [P1, M4, M5] |
| Order-X | Separate order model; orders, changes, responses; BASIC and COMFORT; EXTENDED limited to COMFORT content | `OXPullProvider`, PDF embedding, Order-X validation paths | Both support Order-X. Paniolo does not claim complete EXTENDED content. [P1, M4, M5, M6] |
| Delivery notes | UBL 2.1 DespatchAdvice, Peppol mode, 1Lieferschein identifiers, CIDA XML/PDF | CIDA and UBL DespatchAdvice providers | Both have delivery-note support. Mustang's UBL provider declares UBL 2.2 and 1Lieferschein; this is not proof of Peppol BIS conformance. [P1, M7] |
| XML validation | Packaged EN 16931, KoSIT XRechnung, FeRD/FNFE rules; structured findings | XSD/Schematron, profile selection and XML reports | Bundled rules are not exclusive. Compare rule releases, scenarios, severity and enabled stages. [P1, M5] |
| Arithmetic checks | Model calculates totals; optional validation calculation check | Calculators and validator arithmetic check, which can be disabled | Both calculate and check amounts. Import normalization and validation acceptance remain separate questions. [P1, M5] |
| PDF embedding/extraction | `paniolo-facturx` with PDFBox | Exporters/importers in `library` | Paniolo keeps PDF dependencies outside its XML module; Mustang also separates library, validator and CLI artifacts. [P2, M2] |
| PDF/A conformance | Optional `paniolo-pdfa` with veraPDF | PDF validation via validator module | Neither embedding XML nor rendering a PDF by itself demonstrates complete PDF/A and invoice conformance. [P1, M8] |
| Visual PDF | Model-based PDFBox layout for invoices, orders, quotations, delivery notes; PDF/A-3b by default, PDF/A-1b or PDF 1.7/2.0 preview | XML visualization and PDF via XSLT/FOP | Different layout pipelines; Paniolo supports logo/fonts/colors/footer, not arbitrary layout templates. The additional PDF profiles and quotations were added in 1.0.0. [P1, M9] |
| PDF resource limits | 1.0.1: at most 1,000 attachments, 100 MiB per attachment and 256 MiB combined; bounded filter decoding and predictor rows; viewer inflation budgets | No equivalent limit configuration established by the reviewed sources | Paniolo documents explicit rejection limits, not a general guarantee against all PDF memory exhaustion; PDFBox object-stream expansion remains outside these attachment limits. [P7] |
| Browser view | Client-side web component, XML/PDF input, attachments, validation view, de/en/fr | HTML visualization; validation-log visualization | Paniolo provides an embeddable interactive component; both provide human-readable views. [P3, M9] |
| Received documents | STRICT, LENIENT and LENIENT_KEEP_LINES; original XML and stated amounts through `importInvoice`; deviation guard | Invoice import with configurable recalculation behavior | Test supplier documents and retain originals. The historical import comparison is not evidence for the current revision. [P4, M1] |
| Quotations | PDF, optional lines, conversion to invoice, JSON and CLI; **since 1.0.0**, no quotation XML | Equivalent workflow not established in reviewed sources | A documented Paniolo workflow, not a claim that Mustang cannot represent any quotation data. [P5] |
| JSON and command line | Optional JSON module; CLI for conversion, validation, PDF and visualization | Jackson integration and CLI actions | These capabilities exist in both ecosystems; commands and models differ. [P1, M2, M4] |
| Migration | Optional adapter for Mustang 2.26.0, runtime checks for unknown newer getters, per-value losses | Existing application interfaces and objects remain usable | Migration is incremental but requires profile decisions and acceptance of any reported losses. [P6] |

### Where Mustang remains a better fit

An existing Mustang integration can retain its export interfaces and operational behavior without
a model migration. Version-1 ZUGFeRD generation is a concrete additional export path. Its library
also declares an older Java target, although the chosen artifacts and dependencies still determine
the usable runtime. Paniolo's immutable model and Java 21 requirement are deliberate constraints.
There is no new evidence here for a general speed, memory, compatibility, or conformance winner.

### Paniolo boundaries that matter in a decision

- No Peppol access point, portal submission, or mail delivery.
- Official Peppol Despatch Advice rules must be supplied separately. Packaged XSD checks alone
  do not establish Peppol conformity.
- Order-X EXTENDED carries COMFORT content; full EXTENDED expressiveness is not claimed.
- XRechnung extension sub-lines and third-party payments are limited to UBL Invoice.
- The built-in PDF layout has documented customization limits; arbitrary PDF-to-PDF/A conversion
  is outside scope.
- A Mustang mapping can lose information or require a business decision. `losses()` must be
  evaluated; the adapter is not a drop-in replacement.
- Quotations and the additional PDF profiles belong to 1.0.0; the new import/deviation guards
  and decoding limits belong to 1.0.1. The old `1.0.0-rc02` baseline does not include them.
- The model does not implement every EN 16931 code or extension; for example VAT category B
  is rejected, not mapped to another category. EN 16931:2026 / XRechnung 4.0 are outside the
  currently supported rule baseline. See the [documented limits](README.md#scope).

## Other libraries and tools

These projects solve different parts of the problem. The rows describe documented scope,
not a ranking or an assertion of complete standards coverage.

| Project | Documented scope | Difference from Paniolo | Evidence |
| --- | --- | --- | --- |
| **Kaltblut** | Java 17+; detection, XML/attachment extraction, hybrid carrier checks, optional veraPDF integration, CLI; Apache-2.0 | A receiving/container specialist. XML business validation is delegated to phive rules; invoice creation and layouts are outside its stated purpose. | [README](https://github.com/phax/kaltblut#readme) |
| **Imixs e-invoice** | Java 11 target; dependency-free XML processing; CII and Polish KSeF FA(3) read/write, UBL read-only; template-based model; MIT | KSeF is an additional format absent from Paniolo's documented scope. PDF embedding and a bundled official validation engine are not provided by the documented workflow. | [README](https://github.com/imixs/e-invoice#readme), [POM](https://github.com/imixs/e-invoice/blob/main/pom.xml) |
| **invoice4j** | Java 23+; invoice model, calculation, CII/UBL, XSD and KoSIT validation, Factur-X PDF read/write examples; Apache-2.0 | Direct invoice-library alternative, not XML-only. Paniolo additionally documents orders, delivery notes and a browser viewer. PEPPOL is marked planned in invoice4j's standards/roadmap sections despite the broader introduction. | [README](https://github.com/jcodeforge/invoice4j#readme) |
| **Konik** | ZUGFeRD model, schema/semantic/profile validation, PDF embedding/extraction through carriage modules; AGPL v3 or later; explicitly EOL | Relevant to legacy integrations. Do not infer current XRechnung or Factur-X 2 coverage from its older ZUGFeRD feature list. | [README](https://github.com/konik-io/konik#readme) |
| **Swiss QR Bill for Java** | Swiss payment slips as PDF/SVG/PNG, QR decoding, bill-data validation, adding slips to PDFs; MIT | Complement for Swiss payments, rather than an EN 16931 invoice processor. The former generic “QR Invoice Library” label is resolved here to this named project. | [README](https://github.com/manuelbl/SwissQRBill#readme) |
| **ph-ubl / ph-cii** | Schema-oriented Java read/write libraries for UBL and UN/CEFACT CII | Broader XML building blocks for applications that want schema models. Paniolo adds a business model, calculations, profile workflows and PDF integration; it is not a general implementation of every UBL document type. | [ph-ubl](https://github.com/phax/ph-ubl), [ph-cii](https://github.com/phax/ph-cii) |
| **phive + phive-rules** | Validation engine plus separately maintained, versioned national and Peppol rule sets; current README changelogs include phive 12.3.2 and phive-rules 4.7.3 (24 November 2026) | Suitable when validation across many jurisdictions is the main requirement; not an invoice authoring/PDF-layout library. Structured validation reports are not exclusive to Paniolo. The new rule catalogue includes future-effective Peppol rules; availability is not the same as current applicability. | [engine](https://github.com/phax/phive), [rules](https://github.com/phax/phive-rules) |
| **KoSIT Validator** | Scenario-based XSD/Schematron engine, XML reports, embedded API, CLI and daemon; Apache-2.0 | Useful as an independent validation integration. The engine needs a separate XRechnung scenario package; Paniolo packages rules and adds creation, import and PDF workflows. | [engine](https://github.com/itplr-kosit/validator), [XRechnung configuration](https://github.com/itplr-kosit/validator-configuration-xrechnung) |

## Choosing by requirement

| Requirement | Candidates and trade-off |
| --- | --- |
| Existing Mustang application with no migration budget | Keep Mustang; use Paniolo's adapter only after checking profile mappings and losses. |
| Java 21, immutable business objects, direct UBL/CII output, optional PDF | Paniolo; distinguish the released API from the development revision. |
| New ZUGFeRD 1.0 output | Mustang has a version-1 provider; Paniolo deliberately offers migration instead. |
| Hybrid-PDF intake and carrier validation | Kaltblut, potentially combined with phive for XML business rules. |
| Dependency-free XML processing or Polish KSeF | Imixs; UBL output needs a different component. |
| Invoice-focused model/calculation/PDF alternative on Java 23+ | Evaluate invoice4j against the required profiles; do not count planned PEPPOL support as delivered. |
| Broad UBL document families or multi-country validation | ph-ubl/ph-cii plus phive/phive-rules, with application integration work. |
| Dedicated XRechnung scenario validation | KoSIT Validator plus the matching configuration release. |
| Swiss payment slips | Swiss QR Bill alongside the invoice processor. |

## Changes since the previous review

- **Release status corrected:** quotations and the additional PDF profiles are features of 1.0.0,
  not Unreleased. This review uses the 1.0.1-based revision.
- **Received invoices:** 1.0.1 adds `LENIENT_KEEP_LINES`, `requireNoDeviations()` and CLI deviation
  handling, and tightens line-amount tolerance according to the stated price precision. Identifier
  schemes and case-insensitive UBL VAT tax-scheme reading were corrected.
- **PDF input:** 1.0.1 adds aggregate attachment budgets, bounded decoding and predictor checks;
  the browser viewer limits inflation to 64 MiB per stream and 256 MiB per document by default.
  These are documented scope limits, not evidence of broader security superiority over other tools.
- **Other tools refreshed:** the principal distinctions for Kaltblut, Imixs, invoice4j, Konik and
  Swiss QR Bill remain supported by their current documentation. phive and phive-rules have newer
  changelog entries. No external binary other than the Mustang adapter dependency was run here.

## Verification on 26 November 2026

On Java 21.0.12.1 / Maven 3.9.9, a fresh `clean verify -P invoice-corpus` with headless
AWT completed successfully: **1,266 test cases**, **0 failures**, **0 errors**, **4 skipped**.
The skips are three newer-Mustang tests not applicable to 2.26.0 and one CLI `/dev/full` check
not applicable to this macOS host. The total includes 40 adapter cases (three skipped), corpus
regressions, and packaged-CLI integration tests. Counts come from individual XML `testcase`
elements, including nested suites, rather than adding potentially overlapping suite counters.

The separate `browser-tests` profile also passed with installed Chrome and the configured
Playwright 1.65.1: **34 browser test cases**, **0 failures/errors/skips**. Its ten standalone-HTML
helper tests repeat cases already counted in the reactor; they are not added as new unique cases.
The initial offline browser attempt could not resolve the uncached Playwright dependency; the
subsequent Central-backed run succeeded. These are bounded regression results for this revision,
not a new side-by-side performance, security or complete field-preservation comparison.

## Sources and review method

Paniolo's documentation, module dependencies and facade/model/adapter sources were checked
at the revision above; the tag's changelog was fetched independently. Mustang's tagged 2.26.0
POM, CLI, conversion path, order/delivery providers, visualizer and XML/PDF validators were fetched
again. POM version strings can lag the release, so the release/tag and published artifacts identify
the Mustang baseline. The other projects' README files and the Imixs POM were fetched again.
Paniolo's regression test run is separate from these external documentation findings. Java target
statements are not runtime certification. [P8] records the release history; [M10] the Mustang baseline.

| ID | Source |
| --- | --- |
| P1 | [Paniolo scope and format limits](README.md#scope) |
| P2 | [Parent modules](pom.xml), [model POM](paniolo-shared-model/pom.xml), [XML POM](paniolo/pom.xml), [PDF POM](paniolo-facturx/pom.xml), [viewer POM](paniolo-visualizer/pom.xml) |
| P3 | [Paniolo viewer](paniolo-visualizer/README.md) |
| P4 | [Paniolo facade: importInvoice, read and export](paniolo/src/main/java/io/github/nalukit/paniolo/Paniolo.java) |
| P5 | [Release history and feature versions](CHANGELOG.md), [1.0.1 API](docs/api/1.0.1.md) |
| P6 | [Mustang adapter](paniolo-mustang/README.md), [migration guide](docs/migration-mustang.md) |
| P7 | [Attachment and decoding limits](paniolo-facturx/README.md#attachments), [viewer limits](paniolo-visualizer/README.md#limits), [1.0.1 changelog](CHANGELOG.md) |
| P8 | [Tagged 1.0.1 changelog](https://github.com/NaluKit/paniolo/blob/1.0.1/CHANGELOG.md) |
| M1 | [Published library sources 2.26.0](https://repo.maven.apache.org/maven2/org/mustangproject/library/2.26.0/library-2.26.0-sources.jar) |
| M2 | [Tagged parent POM](https://github.com/ZUGFeRD/mustangproject/blob/core-2.26.0/pom.xml), [library POM](https://github.com/ZUGFeRD/mustangproject/blob/core-2.26.0/library/pom.xml) |
| M3 | [CII-to-UBL path](https://github.com/ZUGFeRD/mustangproject/blob/core-2.26.0/library/src/main/java/org/mustangproject/CII/CIIToUBL.java) |
| M4 | [CLI formats, profiles and actions](https://github.com/ZUGFeRD/mustangproject/blob/core-2.26.0/Mustang-CLI/src/main/java/org/mustangproject/commandline/Main.java) |
| M5 | [Published validator sources 2.26.0](https://repo.maven.apache.org/maven2/org/mustangproject/validator/2.26.0/validator-2.26.0-sources.jar) |
| M6 | [Order-X provider](https://github.com/ZUGFeRD/mustangproject/blob/core-2.26.0/library/src/main/java/org/mustangproject/ZUGFeRD/OXPullProvider.java) |
| M7 | [UBL delivery-note provider](https://github.com/ZUGFeRD/mustangproject/blob/core-2.26.0/library/src/main/java/org/mustangproject/ZUGFeRD/UBLDAPullProvider.java) |
| M8 | [Mustang PDF validator](https://github.com/ZUGFeRD/mustangproject/blob/core-2.26.0/validator/src/main/java/org/mustangproject/validator/PDFValidator.java) |
| M9 | [Mustang visualizer](https://github.com/ZUGFeRD/mustangproject/blob/core-2.26.0/library/src/main/java/org/mustangproject/ZUGFeRD/ZUGFeRDVisualizer.java) |
| M10 | [Mustang latest release](https://github.com/ZUGFeRD/mustangproject/releases/latest) |

A previous comparison tested 168 invoices on **31 October 2026** against an older Paniolo
revision and Mustang 2.26.0. Its import and validation differences, and performance measurements,
are historical findings with different configurations. They are not reused as current figures.
A new comparison would need identical document bytes, fixed rule releases and profiles,
matched arithmetic/PDF-A options, and separate checks of acceptance, preserved values and timing.

# Paniolo CLI

`paniolo-cli` exposes the document operations of the [`Paniolo`](../paniolo/README.md) facade on the command
line for validating, reading, writing, and converting invoices, orders, and despatch advices
in shell scripts, CI pipelines, or cron jobs without writing Java. It includes the optional PDF, JSON, and visualization modules
([`paniolo-facturx`](../paniolo-facturx/README.md), [`paniolo-pdfa`](../paniolo-pdfa/README.md),
[`paniolo-json`](../paniolo-json/README.md), [`paniolo-pdf-layout`](../paniolo-pdf-layout/README.md),
[`paniolo-visualizer`](../paniolo-visualizer/README.md)) and is based on [picocli](https://picocli.info)
(Apache License 2.0). It requires Java 21.

The Paniolo code is licensed under Apache 2.0. The executable JAR also contains dependencies
under their own licenses, including Saxon-HE and Rhino under MPL 2.0, and veraPDF under its
MPL 2.0 option. `META-INF/THIRD-PARTY.txt` in the JAR lists their versions and source downloads;
`META-INF/licenses/MPL-2.0.txt` contains the full MPL text. Original dependency license texts
and notices are preserved when the executable JAR is assembled. Review the resolved dependency
versions and source links in `THIRD-PARTY.txt` when upgrading dependencies.

## Installation

The build creates an executable jar with all dependencies:

```sh
mvn install -pl paniolo-cli -am -DskipTests
java -jar paniolo-cli/target/paniolo-cli-1.0.0-rc01-all.jar --help
```

For daily use, put a small wrapper on the `PATH`, e.g. `~/bin/paniolo`:

```sh
#!/bin/sh
exec java -jar /opt/paniolo/paniolo-cli-1.0.0-rc01-all.jar "$@"
```

The build attaches this jar with the classifier `all`; the plain artifact `paniolo-cli` contains
only the CLI classes. The release `1.0.0-rc01` is available as a download from GitHub Packages (see
below); see also the [installation status](../README.md#installation).

Unlike the library modules, `paniolo-cli` is **not published to Maven Central**. The CI workflow
publishes both jars to the GitHub Packages Maven registry of the repository
(`https://maven.pkg.github.com/<owner>/<repository>`, artifact `io.github.nalukit:paniolo-cli`), on
every push to `dev` as `HEAD-SNAPSHOT` and on every release with the release version. Download the
`-all.jar` from the package page of the repository on GitHub; resolving it with Maven requires a
GitHub token with the `read:packages` scope in a `<server>` entry with the id of that repository.

## Commands

| Command                                                                                                                                                         | Facade method                                                                              |
|-----------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------|
| `validate FILE\|DIR... [--syntax TYPE \| --profile PROFILE] [-r] [--include GLOB] [--exclude GLOB] [--summary] [--expect valid\|invalid] [--expectations FILE]` | `validate(bytes)`, `validate(bytes, type)`, `validate(bytes, facturXProfile)`              |
| `inventory FILE\|DIR... [-r] [--include GLOB] [--exclude GLOB] [--validate] [--summary-only] [-f text\|json]`                                                    | `detect`, `extractXml(pdf)`, `attachments(pdf)`, `producer(pdf)`; with `--validate` the validation methods |
| `detect FILE`                                                                                                                                                   | `detect(bytes)`, `Paniolo.isPdf(bytes)`                                                    |
| `read FILE [--lenient]`                                                                                                                                         | `read(bytes, ReadMode)`, written as JSON                                                   |
| `to-xml FILE --syntax UBL\|CII`                                                                                                                                 | `toBytes(invoice, syntax)`                                                                 |
| `to-facturx FILE --pdf PDF --profile PROFILE [--attach FILE]...`                                                                                                | `toFacturX(invoice, pdfA, profile, attachments)`                                           |
| `to-facturx FILE --profile PROFILE [layout options]`                                                                                                            | `toFacturX(invoice, profile, layout, attachments)`: renders the page                       |
| `embed-xml XML --pdf PDF --profile PROFILE [--attach FILE]...`                                                                                                  | `embedXml(pdfA, xml, profile, attachments)`: XML byte for byte, findings on stderr, exit 1 if invalid |
| `to-pdf FILE [--lang de\|en\|fr] [--logo FILE [--logo-position LEFT\|RIGHT]] [--no-sender-address] [--font FILE [--bold-font FILE]] [--accent RGB] [--footer TEXT] [--watermark TEXT]` | `toPdf(invoice, layout)` of [`paniolo-pdf-layout`](../paniolo-pdf-layout/README.md)        |
| `extract-xml PDF`                                                                                                                                               | `extractXml(pdf)`                                                                          |
| `attachments PDF [--extract DIR]`                                                                                                                               | `attachments(pdf)`                                                                         |
| `producer PDF`                                                                                                                                                  | `producer(pdf)`                                                                            |
| `migrate-zugferd1 FILE [--profile PROFILE]`                                                                                                                     | `migrateZugferd1(document[, profile])`                                                     |
| `visualize FILE [--lang de\|en\|fr] [--validate \| --report FILE]`                                                                                              | `PanioloVisualizer.toHtml(...)` of [`paniolo-visualizer`](../paniolo-visualizer/README.md) |
| `order read FILE [--ignore-unsupported]`                                                                                                                        | `readOrder(bytes, ignoreUnsupported)`, written as JSON                                     |
| `order validate FILE\|DIR... [options of validate]`                                                                                                             | `validateOrder(bytes)`                                                                     |
| `order to-xml FILE`                                                                                                                                             | `toOrderXBytes(order)`                                                                     |
| `order to-pdf FILE --pdf PDF [--attach FILE]...`                                                                                                                | `toOrderXPdf(order, pdfA, attachments)`                                                    |
| `order to-pdf FILE [layout options] [--attach FILE]...`                                                                                                         | `toOrderXPdf(order, layout, attachments)`: renders the pages                               |
| `despatch-advice read FILE [--ignore-unsupported]`                                                                                                              | `readDespatchAdvice(bytes, ignoreUnsupported)`, written as JSON                            |
| `despatch-advice validate FILE\|DIR... [options of validate]`                                                                                                   | `validateDespatchAdvice(bytes)`                                                            |
| `despatch-advice to-xml FILE [--syntax UBL\|CIDA]`                                                                                                              | `toDespatchAdviceBytes(advice)`, `toCiiDespatchAdviceBytes(advice)`                        |
| `despatch-advice to-pdf FILE --pdf PDF [--attach FILE]...`                                                                                                      | `toDespatchAdvicePdf(advice, pdfA, attachments)`                                           |
| `despatch-advice to-pdf FILE [layout options] [--attach FILE]...`                                                                                               | `toDespatchAdvicePdf(advice, layout, attachments)`: renders the delivery note              |

`paniolo --help` lists the commands. `paniolo inventory --help`, `paniolo order --help`, and
`paniolo despatch-advice --help` show help for those command groups. Commands such as `validate`
print their usage and options when invoked without required arguments (exit code `2`); they do
not currently have a standalone `--help` option. Enum values such as profiles and syntaxes are
case-insensitive (`--profile en16931`).

- **Input**: the commands that write a document (`to-xml`, `to-facturx`, `order to-xml`, ...)
  accept the [Paniolo JSON](../paniolo-json/README.md) of the model or any document the facade can
  read. `to-xml` converts between UBL and CII through the model. `read` followed by `to-xml`
  writes the loaded model again; unsupported input fields and original XML formatting are not
  preserved. The Java facade's `importInvoice` / `export` methods are not exposed as CLI commands.
- **Output**: results go to standard output unless `-o FILE` is given. `-` as input file reads
  standard input, so commands can be piped.
- **Validation profile**: automatic invoice validation uses the facade's rules. Standalone
  EN 16931 and Peppol invoices can be checked against additional XRechnung requirements; see
  [validating XML](../README.md#validating-xml). `--syntax` also selects EN 16931 and XRechnung;
  `--profile EN16931` selects the Factur-X EN 16931 rules for CII XML.
- **Rules**: `--rules`, `--facturx-rules`, `--extended-ctc-fr-rules`, `--order-x-rules`, and
  `--despatch-advice-rules` configure the facade like the methods of `Paniolo.builder()`.
- **Calculation check**: `--calculation-check` adds the optional recalculation of the amounts
  (`Paniolo.Builder.calculationCheck(true)`) to `validate` and `visualize --validate`; its
  `PANIOLO-CALC-` findings are warnings or information and do not change the exit code.
- **Unknown documents**: `--unknown-documents THROW|REPORT_INVALID` selects what `validate` does with
  a document whose type it cannot detect (`Paniolo.Builder.unknownDocuments`). `THROW` (default)
  lists it as `error` with exit code `3`; `REPORT_INVALID`, the Mustang compatible mode, as
  `invalid` with the `FATAL` finding `XML-PARSE` or `PANIOLO-UNKNOWN-DOCUMENT` and exit code `1`, so
  `--expect invalid` covers it in negative corpora.
- **Rule statistics**: `--rule-statistics` adds the execution statistics
  (`Paniolo.Builder.ruleStatistics(true)`) to the reports of `validate`: per Schematron stylesheet
  the active patterns, fired rules, failed asserts, and successful reports, per PDF/A check the
  checks and the failures per rule. Text shows them as `statistics ...` lines below the file,
  JSON as object `statistics`, XML as element `statistics`. Slower; meant for audits.
- **Logging**: only errors are logged; `--verbose` logs details and prints stack traces.

## Exit codes

| Code | Meaning                                                                                   |
|------|-------------------------------------------------------------------------------------------|
| `0`  | Success; all documents valid                                                              |
| `1`  | At least one document invalid, or rejected by `--reject-rule` / `--fail-on-warnings`      |
| `2`  | Wrong usage, e.g. unknown option or missing argument                                      |
| `3`  | Processing error, e.g. file not found, unsupported document, or content the model rejects |

When `validate` processes several files, the exit code covers all of them: `3` if at least one
file could not be processed, otherwise `1` if at least one is invalid, otherwise `0`. With
expected results (see below), `1` means that at least one file does not meet its expectation.
`inventory` returns `3` if a file cannot be read and `0` otherwise, also for invalid or unknown
documents.

## Validation reports

`validate`, `order validate`, and `despatch-advice validate` accept several files and print one
result per file. `--format` selects the output:

- `text` (default): a summary line per file and the findings from `--severity` up (default
  `WARNING`).
- `json`: one JSON object per file and line (JSON Lines) with `file`, `profile`, `valid`, the
  counts per severity, `durationMillis`, and `messages`; easy to process with `jq`.
- `xml`: the [Paniolo validation report](../paniolo-validation/README.md) of `ValidationResult.toXml()`;
  one file only.
- `pdf`: the same report rendered as PDF/A-3b with `toReportPdf` (needs `paniolo-pdf-layout`, part
  of the CLI); one file only, `--lang de|en|fr` (default `de`). Write it with `-o report.pdf`.

`--quiet` prints nothing, so only the exit code counts.

`--reject-rule RULE` (repeatable) does not accept a valid document with a finding of that rule,
e.g. `PEPPOL-EN16931-R120`, which XRechnung rates as a warning; `--fail-on-warnings` rejects on every
warning. The document stays valid: text shows `valid, rejected by policy: RULE`, JSON adds
`"accepted": false` and `"blocking"`, `--summary` counts it as `rejected by policy`, and the exit
code is `1`. See [acceptance policy](../README.md#acceptance-policy).

### Directories and summaries

The validation commands accept directories as well as files. A directory contributes its `*.xml`
and `*.pdf` files in sorted order; `--recursive` (`-r`) includes sub-directories.
`--include GLOB` replaces the default file patterns and `--exclude GLOB` skips files or whole
sub-directories; both are repeatable and match the path relative to the directory argument
(`archive/**`), a pattern without `/` also the file name at any depth (`*.xml`). Files given
explicitly are always validated.

Every file gets its own result. With `--unknown-documents REPORT_INVALID`, documents of unknown
type are `invalid` instead of `error`; unreadable files stay errors. A processing exception is
listed as `error` (in JSON: an object with `file`, `valid: false` and `error`), also reported on
standard error, and the run continues with the next file. Malformed XML after a recognized root
can already produce an invalid validation result; `--syntax` likewise reports XML parse failures
as findings rather than processing errors.
`--summary` appends the totals: in text `Summary: N files, V valid, I invalid, E not processed`,
in JSON a last object with `"summary": true`, `files`, `valid`, `invalid`, and `errors`. The
totals are counted from the same per-file results. `--format xml` still accepts one document only.

```sh
paniolo validate inbox -r --exclude 'archive/**' --summary
paniolo validate inbox -r -f json --summary | jq -s 'last'
```

### Expected results

For regression tests of document corpora, `--expect valid` or `--expect invalid` states the
expected result of every file (Mustang `validateExpectValid` / `validateExpectInvalid`). `valid`
expects the document to be accepted, `invalid` expects it to be invalid or rejected by the policy
of `--reject-rule` / `--fail-on-warnings`. Text adds `as expected` or `NOT AS EXPECTED: expected
...` to the line of the file, JSON the fields `expected` and `asExpected`, and `--summary` the
counts `as expected` / `not as expected` (JSON: `asExpected`, `notAsExpected`). The exit code is
`1` if a file does not meet its expectation, even if it is valid, and `0` if all do, even if they
are invalid. Files that cannot be processed meet no expectation; they are errors with exit code
`3`.

`--expectations FILE` states the expected result per file, one line `valid PATH` or
`invalid PATH` each, with paths relative to `FILE`; `#` starts a comment. It takes precedence over
`--expect`, which then applies to the files not listed. A listed file that is not among the
validated files is reported as missing (exit code `3`), so a corpus cannot shrink unnoticed.

```sh
paniolo validate corpus/positive -r --expect valid --summary
paniolo validate corpus/negative -r --expect invalid --summary
paniolo validate corpus -r --expectations corpus/expected.txt --summary
```

```text
# corpus/expected.txt
valid    positive/xrechnung-ubl.xml
invalid  negative/missing-buyer-reference.xml
```

## Inventory

`inventory` lists a document stock without reading the documents into the model (Mustang
`metrics`), e.g. to plan a migration or to find unexpected documents in an inbound channel. It
accepts files and directories like `validate` (`-r`, `--include`, `--exclude`) and reports per file:

- the format: `CII`, `UBL_INVOICE`, `UBL_CREDIT_NOTE`, `ZUGFERD_1`, `ORDER_X`, `CIDA`,
  `UBL_DESPATCH_ADVICE`, for PDFs `PDF/` and the format of the embedded XML, `PDF` for a PDF
  without document XML, `UNKNOWN` with the root element, or `MALFORMED` with the parser message;
- the profile (`EN16931`, `EXTENDED`, `XRECHNUNG 3.0`, `PEPPOL BIS 3`, `COMFORT`, ...) and the
  specification identifier (BT-24 / `CustomizationID`) it is derived from;
- the document type code, e.g. `380`;
- for PDFs the producing software as recognized by `producer`;
- with `--validate` the validity (CIDA and unknown documents are not validated).

The summary counts the files per format, profile, and producer, and with `--validate` the valid,
invalid, and not validated documents. `-f json` writes one object per file and a last object with
`"summary": true`; `--summary-only` prints the summary alone.

```sh
paniolo inventory archive -r --summary-only
paniolo inventory inbox -r --validate -f json | jq -s 'last'
```

```text
inbox/r-1.xml: CII, XRECHNUNG 3.0 (urn:cen.eu:en16931:2017#compliant#urn:xeinkauf.de:kosit:xrechnung_3.0), type 380
inbox/r-2.pdf: PDF/CII, EN16931 (urn:cen.eu:en16931:2017), type 380, producer Mustang (producer "PDFBox")
Summary: 2 files, 0 not readable
  Formats: CII 1, PDF/CII 1
  Profiles: EN16931 1, XRECHNUNG 3.0 1
  Producers: Mustang 1
```

## Examples

```sh
# Validate all received invoices, including sub-directories, with a summary
paniolo validate inbox --recursive --summary

# List the rule IDs of all errors as JSON
paniolo validate -f json invoice.xml | jq -r '.messages[] | select(.severity == "FATAL" or .severity == "ERROR") | .rule'

# Archive the XML validation report next to the invoice
paniolo validate -f xml -o invoice.report.xml invoice.xml

# Convert UBL to CII
paniolo to-xml invoice-ubl.xml --syntax CII -o invoice-cii.xml

# Edit an invoice as JSON and write it as XRechnung UBL
paniolo read invoice.xml -o invoice.json
paniolo to-xml invoice.json --syntax UBL -o invoice.xml

# Create a Factur-X PDF with a time sheet and check it, incl. PDF/A
paniolo to-facturx invoice.json --pdf layout.pdf --profile EN16931 --attach timesheet.pdf -o factur-x.pdf
paniolo validate factur-x.pdf

# Embed XML of another system unchanged; findings on stderr, exit code 1 if invalid
paniolo embed-xml received.xml --pdf layout.pdf --profile EN16931 -o factur-x.pdf

# Factur-X without a PDF of your own: Paniolo renders the page
paniolo to-facturx invoice.json --profile XRECHNUNG --logo logo.png --footer "Amtsgericht Berlin HRB 12345 B" -o factur-x.pdf

# Preview for checking before sending: only the page, no XML, with watermark
paniolo to-pdf invoice.json --watermark PREVIEW -o preview.pdf

# Unpack a received Factur-X PDF
paniolo extract-xml received.pdf -o received.xml
paniolo attachments received.pdf --extract attachments/

# Readable copy for the archive or an e-mail: one HTML file, opens offline, with the validation result
paniolo visualize received.pdf --validate --lang en -o received.html

# Pipe: extract, convert, and validate without temporary files
paniolo extract-xml received.pdf | paniolo to-xml - --syntax UBL | paniolo validate -
```

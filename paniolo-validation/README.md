# Paniolo Validation

`paniolo-validation` checks XML against XSD and compiled Schematron business rules: XRechnung and
EN 16931 invoices, the Factur-X profiles, Order-X, and any rules you compile yourself. It requires
Java 21 and depends on [paniolo-xml](../paniolo-xml/README.md) and Saxon-HE.
The UBL and CII writers are test dependencies and are not required to validate existing XML.

> Most applications use this module through the [`Paniolo`](../paniolo/README.md) facade, which
> detects the document type and selects the profile (`paniolo.validate(xml)`). Use
> `InvoiceValidator` directly when you want to choose the profile yourself or validate without the
> facade.

## Dependency

After installing Paniolo from the repository root with `mvn install`, add:

```xml
<dependency>
  <groupId>io.github.nalukit</groupId>
  <artifactId>paniolo-validation</artifactId>
  <version>HEAD-SNAPSHOT</version>
</dependency>
```

Saxon-HE (Saxonica) is licensed under the Mozilla Public License 2.0, a file-level copyleft
license that allows its unmodified use from Apache-2.0 licensed code; its source is available
from the [Saxon-HE project](https://github.com/Saxonica/Saxon-HE). This summary is not legal advice.

## Validate against XRechnung

Add [paniolo-xrechnung-rules](../paniolo-xrechnung-rules/README.md) for the official KoSIT
rules, or extract a KoSIT validator configuration bundle yourself, and select a matching profile:

```java
import io.github.nalukit.paniolo.rules.xrechnung.XrechnungRules;
import io.github.nalukit.paniolo.validation.InvoiceValidator;
import io.github.nalukit.paniolo.validation.ValidationProfile;

import java.nio.file.Files;
import java.nio.file.Path;

var profile = ValidationProfile.xrechnung(XrechnungRules.bundle(), // or Path.of("/path/to/kosit-bundle")
                                          ValidationProfile.Syntax.UBL_INVOICE);
var validator = new InvoiceValidator(profile);
var result = validator.validate(Files.readAllBytes(Path.of("invoice.xml")));

if (!result.isValid()) {
  result.messages()
        .forEach(message -> System.out.printf("%s %s %s: %s%n",
                                              message.severity(),
                                              message.ruleId(),
                                              message.location(),
                                              message.message()));
}
```

| Profile           | XML documents                                          |
|-------------------|--------------------------------------------------------|
| `UBL_INVOICE`     | UBL Invoice                                            |
| `UBL_CREDIT_NOTE` | UBL CreditNote                                         |
| `CII`             | CII CrossIndustryInvoice for invoices and credit notes |

Profile selection is explicit. `ValidationProfile.xrechnung` reads the corresponding
standard CIUS scenario and its severity overrides from the bundle's `scenarios.xml`.
This module contains no rules and never downloads rules at runtime; they come from
`paniolo-xrechnung-rules` or from a bundle directory you provide.

`InvoiceValidator` accepts a `String`, `byte[]`, or `InputStream`. The caller retains
ownership of input streams. Validator instances compile their rules once and can be
reused concurrently.

## Validate against Factur-X / ZUGFeRD profile rules

The official Factur-X schemas and Schematrons of FeRD are packaged in
[`paniolo-facturx-rules`](../paniolo-facturx-rules/README.md) (ZUGFeRD 2.5.2 / Factur-X 1.09.2,
Apache License 2.0). Point `ValidationProfile.facturX` at the extracted rules, or at another
extracted FeRD / FNFE-MPE package:

```java
var profile = ValidationProfile.facturX(FacturXRules.bundle(),     // or Path.of("/path/to/factur-x-package")
                                        "BASIC");
var validator = new InvoiceValidator(profile);
```

Levels are `MINIMUM`, `BASIC-WL`, `BASIC`, `EN16931`, and `EXTENDED`. The files are found by name
anywhere below the directory, so the package layout does not matter: `FACTUR-X_<level>.xsd` or, as
in the FeRD package, `Factur-X_<version>_<level>.xsd` (level `BASICWL` for BASIC-WL) with its
imported schemas, the compiled Schematron
`FACTUR-X_<level>.xslt`, and its code list `FACTUR-X_<level>_codedb.xml` next to it. Each file must
exist exactly once. The code list is declared as a lookup document of the profile (see below).

The French profile EXTENDED-CTC-FR is checked in two steps, because its rules come from another
package: the Factur-X EXTENDED schema alone, then the EXTENDED-CTC-FR Schematron of FNFE-MPE and,
for invoices between French parties, the French rules BR-FR (flux 2). Run the second step only if
the first one is valid, as the `Paniolo` facade does:

```java
var schema = ValidationProfile.facturXSchema(FacturXRules.bundle(), "EXTENDED");
var rules  = ValidationProfile.extendedCtcFr(FacturXRules.france(), true);  // true: with BR-FR
```

`extendedCtcFr` finds `EXTENDED-CTC-FR-CII.xslt` and `BR-FR-Flux2-Schematron-CII.xslt` in the
folder `FNFE_RFE_INVOICE/CII/EXTENDED-CTC-FR` of a France_RFE release, or anywhere below the given
directory.

## Validate older XRechnung versions and ZUGFeRD 1.0

`paniolo-xrechnung-rules` also packages the last KoSIT release of XRechnung 1.2, 2.0, 2.1, 2.2,
and 2.3; `ValidationProfile.xrechnung` loads them like the current bundle (including the scenario
names `EN16931 CIUS XRechnung (...)` of XRechnung 1.2). The `Paniolo` facade selects the release
by BT-24.

```java
var older = ValidationProfile.xrechnung(XrechnungRules.legacyBundle("2.3"), ValidationProfile.Syntax.CII);
var zf1   = ValidationProfile.zugferd1(FacturXRules.zugferd1());       // ZUGFeRD1p0.xsd, ZUGFeRD_1p0.xslt
```

The ZUGFeRD 1.0 Schematron reports elements ZUGFeRD 1.0 does not use as successful reports
without flag. `ValidationProfile.UNFLAGGED_REPORTS` in `severityOverrides` sets the severity of
such reports; `zugferd1` sets it to `INFO`, otherwise they are errors like every finding without
flag.

## Validate against Order-X rules

`ValidationProfile.orderX(FacturXRules.orderX(), level)` loads the official Order-X 1.0 schema and
Schematron (levels `BASIC`, `COMFORT`, `EXTENDED`) packaged in `paniolo-facturx-rules`, or from
another directory. The stylesheet `SCRDMCCBDACIOMessageStructure_100pD20B_<level>.xslt` is found by
name; the schema `SCRDMCCBDACIOMessageStructure_100pD20B.xsd` below a directory named like the
level, because every level uses the same file name. The stylesheet must write SVRL: the
`-compiled.xsl` files of the official release package report with `xsl:message` and cannot be
used; `scripts/package-orderx-rules.sh` compiles the official `.sch` files instead.

## Results and errors

Findings carry a stage: `XML`, `XSD`, and `SCHEMATRON` for invoice XML, `PDF_A` and `HYBRID`
for Factur-X / ZUGFeRD documents validated through the `Paniolo` facade, and `CALCULATION` for the
optional calculation check of the facade. `messages(stage)`
filters by stage, and `ValidationResult.combine(...)` joins the findings of several stages.

Validation runs in order: XML parsing, XSD validation, then Schematron execution. XSD
errors stop the process before Schematron runs.

`ValidationResult` contains the profile name, immutable findings, the duration, and optional
execution statistics. Each finding has:

- The original rule identifier, where available.
- Severity: `INFO`, `WARNING`, `ERROR`, or `FATAL`.
- A message and location, where supplied by the parser or rule artifacts.
- A stage: `XML`, `XSD`, or `SCHEMATRON`; results of the facade for PDFs also contain `PDF_A`
  and `HYBRID`, and with the calculation check `CALCULATION`.

The facade does not check documents of EN 16931:2026 (e.g. XRechnung 4.0), whose official rules
are not published yet: their result is invalid with the single `ERROR` finding
`EN16931-2026-NOT-SUPPORTED` of stage `SCHEMATRON`.

`isValid()` returns false if any finding has severity `ERROR` or `FATAL`.
`hasWarnings()` reports warnings separately, `count(severity)` the number of findings per severity.
`AcceptancePolicy.officialRules().rejectOn("PEPPOL-EN16931-R120")` adds an application acceptance
rule without changing the validation result; `rejectOnWarnings()` rejects all warnings. Call
`policy.evaluate(result)` for the separate `accepted()` decision and blocking findings. See
[acceptance policy](../README.md#acceptance-policy).
`duration()` is the time the validation took; the `Paniolo` facade measures the whole call, including
detection and all stages. Malformed or invalid XML produces findings;
configuration, input I/O, compilation, or execution failures throw `ValidationException`.

### Rule statistics

`new InvoiceValidator(profile, true)` adds `ValidationStatistics` to every result: per Schematron
stylesheet a `SchematronStatistics` with the active patterns, fired rules (rule contexts that
matched a node), failed asserts, and successful reports. The facade adds a `PdfaStatistics` per
PDF/A check with `Paniolo.builder().ruleStatistics(true)`. Findings and validity do not change.
Counting fired rules keeps `svrl:fired-rule` in the compiled stylesheets, which the validator
otherwise removes, and costs up to about 10 % for large invoices; use it for audits. Without
statistics `statistics()` is null.

### XML report

`toXml()` writes the result as XML report for archiving or for systems that process findings:

```xml
<report xmlns="urn:io:github:nalukit:paniolo:validation-report:1"
        generator="Paniolo 1.0.0" created="2026-10-29T10:15:30.123Z" duration="PT0.042S">
  <profile>Factur-X EN16931 / Factur-X EN16931</profile>
  <summary valid="false" fatals="1" errors="0" warnings="0" infos="1"/>
  <stage name="HYBRID" valid="true">
    <message severity="INFO" rule="FX-PRODUCER" location="/Info">Produced by ...</message>
  </stage>
  <stage name="SCHEMATRON" valid="false">
    <message severity="FATAL" rule="BR-CO-10" location="/rsm:CrossIndustryInvoice">...</message>
  </stage>
</report>
```

- `generator` names the Paniolo version, `created` the time the report was written, and `duration`
  the time the validation took; `duration` is missing for results without measured time, e.g. of
  `combine(...)`.
- `summary` counts the findings per severity; `valid` is `isValid()`.
- `statistics` follows the summary only for results with `statistics()`, i.e. validators created
  with `new InvoiceValidator(profile, true)` or a facade with `ruleStatistics(true)`: one
  `schematron` element per stylesheet (`activePatterns`, `firedRules`, `failedAsserts`,
  `successfulReports`) and one `pdfa` element per PDF/A check (`flavour`, `profile`, `compliant`,
  `checks`, `failedChecks`, and a `failedRule` with `count` per failed rule).
- Findings are grouped by `stage` in the order the stages reported them; stages without findings
  are left out. `rule` and `location` are missing where the finding has none, a blank text is
  written as `-`.

The report is described by the XSD `validation-report-1.xsd` in the jar; load it with
`ValidationResult.class.getResource(ValidationResult.REPORT_SCHEMA)`.

Results reflect the selected technical checks. They do not guarantee recipient acceptance
and are not complete KoSIT VARL reports.

## Custom rules and resource access

A profile may also check only an XSD (no stylesheets) or only Schematron (`schema` null), e.g. to
combine a schema and rules that live in different directories.

Construct a `ValidationProfile` directly to use a local XSD, one or more compiled
Schematron XSLTs, and optional severity overrides:

```java
import io.github.nalukit.paniolo.validation.ValidationProfile;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

var root = Path.of("/absolute/path/to/trusted-rules");
var profile = new ValidationProfile("Custom invoice rules v1",
                                    root,
                                    root.resolve("invoice.xsd"),
                                    List.of(root.resolve("rules.xsl")),
                                    Map.of());
```

Raw `.sch` files must be compiled before use. Stylesheets must produce SVRL reports.
Treat rule bundles as trusted application code. Local imports are confined to the selected
resource directory, including symlink resolution. XML parsing disables DTDs, external
entities, and XInclude; external document and text access during transformation is blocked.
The only exception are lookup documents listed in the profile's `documents` (sixth constructor
argument), such as the Factur-X code lists: they must lie inside the resource directory, are
parsed once when the validator is created, and are the only files `document()` may read.

## Build and tests

Run from the repository root using JDK 21 or newer and Maven 3.9:

```sh
mvn -pl paniolo-validation -am verify
```

This runs the model, localization, writer, reader, and validator tests as well as the
integration tests against the official XRechnung rules from `paniolo-xrechnung-rules`
(a test dependency). The Factur-X tests use a small synthetic package under
`src/test/resources/facturx-package` with the official file names. To test another extracted bundle instead, pass its absolute
path:

```sh
mvn -pl paniolo-validation -am verify -Dxrechnung.bundle="/absolute/path/to/extracted/bundle"
```

Integration tests cover both XML syntaxes, invoices, credit notes, multiple tax rates, tax
exemptions, transactions outside the scope of VAT, allowances, charges, all document level
business terms, payment cards, direct debit, and missing buyer references.

See the [project README](../README.md) for the complete workflow and current limitations.

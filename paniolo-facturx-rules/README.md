# Paniolo Factur-X Rules

`paniolo-facturx-rules` packages the official Factur-X / ZUGFeRD schemas and Schematrons of
[FeRD](https://www.ferd-net.de/), and the rules of the French profile EXTENDED-CTC-FR of
[FNFE-MPE](https://fnfe-mpe.org/), for [paniolo-validation](../paniolo-validation/README.md). It
requires Java 21 and has no runtime dependencies. The `paniolo` facade depends on it and uses it
for all Factur-X profiles unless `Paniolo.builder().facturXRules(path)` names another release.

| Packaged release                             | Content                                                                                            |
|----------------------------------------------|----------------------------------------------------------------------------------------------------|
| ZUGFeRD 2.5.2 / Factur-X 1.09.2 (2026-08-04) | MINIMUM, BASIC WL, BASIC, EN 16931, EXTENDED: XSD, Schematron, compiled XSLT, code lists           |
| ZUGFeRD 1.0 (2014-06-25)                     | XSD, Schematron, and the Schematron compiled to XSLT by the Mustang project                        |
| Order-X 1.0 (2021-04-13)                     | BASIC, COMFORT, EXTENDED: XSD and Schematron, and the Schematron compiled to SVRL by Paniolo       |
| FNFE-MPE France_RFE 1.4.0.04 (2026-09-04)    | CII EXTENDED-CTC-FR and BR-FR (flux 2): Schematron and compiled XSLT, also the `_WARNING` variants |

## Dependency

After installing Paniolo from the repository root with `mvn install`, add:

```xml
<dependency>
  <groupId>io.github.nalukit</groupId>
  <artifactId>paniolo-facturx-rules</artifactId>
  <version>HEAD-SNAPSHOT</version>
</dependency>
```

## Usage

```java
import io.github.nalukit.paniolo.rules.facturx.FacturXRules;
import io.github.nalukit.paniolo.validation.InvoiceValidator;
import io.github.nalukit.paniolo.validation.ValidationProfile;

var validator = new InvoiceValidator(ValidationProfile.facturX(FacturXRules.bundle(), "EXTENDED"));
```

| Method                                                                       | Behavior                                                                                                             |
|------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------|
| `FacturXRules.bundle()`                                                      | Extracts once per JVM into a private temporary directory, deleted on exit                                            |
| `FacturXRules.bundle(Path)`                                                  | Extracts into the given directory and reuses it while it holds the same release; refuses other existing content      |
| `VERSION`, `ZUGFERD`, `RELEASE`, `SHA256`                                    | Factur-X and ZUGFeRD version, release date, and archive checksum                                                     |
| `FacturXRules.zugferd1()`                                                    | Extracts the ZUGFeRD 1.0 rules once per JVM into a private temporary directory; for `ValidationProfile.zugferd1`     |
| `FacturXRules.orderX()`                                                      | Extracts the Order-X rules once per JVM into a private temporary directory; for `ValidationProfile.orderX`           |
| `FacturXRules.france()`                                                      | Extracts the French CII rules once per JVM into a private temporary directory; for `ValidationProfile.extendedCtcFr` |
| `FRANCE_VERSION`, `FRANCE_RELEASE`, `FRANCE_EXTENDED_CTC_FR`, `FRANCE_BR_FR` | Version and release date of the French rules, paths of the compiled stylesheets                                      |

The archive holds the files of the folder `Schema` of the FeRD release package, unmodified and
with their original names and folders, for example
`3_Factur-X_1.09.2_EN16931/_XSLT_EN16931/FACTUR-X_EN16931.xslt`. Specifications, samples, Excel
files, logos, and the CII D22B schemas of the package are not included. `SHA256SUMS` in the archive
lists the checksum of each file as published. The SHA-256 of the archive is verified on every
extraction; archive entries cannot escape the target directory. Protect a persistent directory
against modification by others, because rules are executed as trusted stylesheets.

`bundle()` is synchronized: concurrent callers in one class loader get the same directory,
extracted once. `bundle(Path)` can be called from several processes for the same directory;
extraction happens in a staging directory that is moved into place atomically.

FeRD no longer publishes ZUGFeRD 1.0. Its schemas and Schematron come unmodified from the
[Mustang project](https://github.com/ZUGFeRD/mustangproject) (Apache License 2.0), as last changed
there in commit `42795b17`, together with the Schematron compiled to XSLT by Mustang; the
Schematron carries FeRD's original terms of use of 2014. `SHA256SUMS` in
`zugferd-1.0-2014-06-25.zip` lists the checksums. The Schematron reports elements that
ZUGFeRD 1.0 does not use without a flag; `ValidationProfile.zugferd1` turns them into
information.

The French rules come unmodified from the release tag
[v1.4.0.04 of fnfempe/France_RFE](https://github.com/fnfempe/France_RFE/releases/tag/v1.4.0.04)
(Apache License 2.0): the folder `FNFE_RFE_INVOICE/CII/EXTENDED-CTC-FR` with its original paths,
the `LICENSE` of the repository, and a `SHA256SUMS` of the files. The validation uses
`EXTENDED-CTC-FR-CII.xslt` and `BR-FR-Flux2-Schematron-CII.xslt`; the schema is the Factur-X
EXTENDED schema of the FeRD release.

The Order-X release of FNFE-MPE and FeRD ships its Schematron also as a stylesheet compiled with
oXygen, which reports with `xsl:message` instead of SVRL and cannot be used by paniolo-validation.
`scripts/package-orderx-rules.sh` therefore compiles the unmodified `.sch` files with the ISO
Schematron skeleton (`scripts/iso-schematron`, MIT License) and Saxon-HE and packs them with the
official XSDs, Schematrons, and terms of use into `order-x-1.0-2021-04-13.zip`. The compiled
stylesheets have the same 79, 124, and 193 assertions as the Schematron; all 24 XML examples of
the release pass them. The script is reproducible: the same release always yields the same archive.

```sh
scripts/package-orderx-rules.sh /path/to/extracted/Order-X100_EN
```

## Updating the rules

FeRD publishes the release package through a form on its
[download page](https://www.ferd-net.de/publikationen-produkte/publikationen/detailseite/zugferd-252-deutsch),
without a stable download URL, so the archive is stored in this module instead of being downloaded
by the build:

1. Download and extract the new release package.
2. Pack the `.xsd`, `.sch`, `.xslt`, and `.xml` files below `Schema/<n>_Factur-X_<version>_<level>`
   unmodified into `src/main/resources/io/github/nalukit/paniolo/rules/facturx/`, together with a
   `SHA256SUMS` of the original files, and remove the old archive.
3. Change `facturx.version`, `facturx.zugferd`, `facturx.release`, and `facturx.sha256` together in
   this module's `pom.xml`, and the release in `META-INF/NOTICE`.
4. Run the tests, including the official rule tests of `paniolo-facturx`:

```sh
mvn verify
```

For the French rules, pack the folder `FNFE_RFE_INVOICE/CII/EXTENDED-CTC-FR` of a new release tag
of France_RFE unmodified with the repository's `LICENSE` and a `SHA256SUMS`, change
`france.version`, `france.release`, and `france.sha256` in `pom.xml` and the release in
`META-INF/NOTICE`, and run the tests; `ExtendedCtcFrTest` of `paniolo-facturx` validates the
official examples of the release, which are kept in its test resources.

## Licenses

The Paniolo code in this module is licensed under Apache 2.0. The packaged files are the technical
artifacts of the Factur-X / ZUGFeRD standard of FeRD (the German Electronic Invoicing Forum,
AWV e.V.) and FNFE-MPE. FeRD states that the Apache License 2.0 applies to them (translated from German):

> “The Apache 2.0 license terms apply to the use of the technical artifacts
> (schemas and Schematron) of the ZUGFeRD standard.”
> ([Terms of use](https://www.ferd-net.de/ueber-uns/ressourcen-1/veroeffentlichungen/haftungsausschluss-und-nutzungsrechte))

The specification of the release package states the same for the document itself. The compiled
XSLT files are generated by FeRD from the Schematrons and published in the same package.
Factur-X and ZUGFeRD apply EN 16931-1 and CEN/TS 16931-3-3, whose copyright is held by CEN and
DIN. Their schemas are based on the UN/CEFACT Cross Industry Invoice (D22B) and refer to UN/CEFACT
and UNECE code lists; the terms of use of UN/CEFACT and UNECE apply accordingly. The French rules of FNFE-MPE are published under the Apache License 2.0 in the repository
France_RFE; its `LICENSE` is part of their archive. For Order-X the terms of use of the release
state: "The Apache 2.0 license terms apply to the use of the Order-X technical artifacts"; the
compiled SVRL stylesheets are derived from the Schematron by Paniolo and marked as such in
`SHA256SUMS`, and the ISO Schematron skeleton used for them is under the MIT License. The JAR contains `META-INF/NOTICE` and the
license texts under `META-INF/licenses/`, including `MIT-ISO-Schematron.txt` for the utility
templates embedded in the generated Order-X XSLT. The Order-X archive also retains that MIT
text under `licenses/MIT-ISO-Schematron.txt`; its `SHA256SUMS` marks this entry as `license`. This
summary is not legal advice; check the terms for your use.

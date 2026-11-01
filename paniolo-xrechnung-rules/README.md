# Paniolo XRechnung Rules

`paniolo-xrechnung-rules` packages the official
[KoSIT XRechnung validator configuration](https://github.com/itplr-kosit/validator-configuration-xrechnung)
for [paniolo-validation](../paniolo-validation/README.md). It requires Java 21 and has no
runtime dependencies.

| Packaged release    | Content                                                                                       |
|---------------------|-----------------------------------------------------------------------------------------------|
| KoSIT `v2026-08-31` | XRechnung 3.0.2, EN 16931 rules 1.3.16, XRechnung Schematron 2.6.0, UBL 2.1 and CII D16B XSDs |

For documents of older XRechnung versions it also packages the last KoSIT release of each version;
the `Paniolo` facade selects them by the specification identifier (BT-24) and adds the information
`XR-OLDER-VERSION`:

| Version | Packaged release                                     |
|---------|------------------------------------------------------|
| 1.2     | `validator-configuration-xrechnung_1.2.2_2019-12-30` |
| 2.0     | `validator-configuration-xrechnung_2.0.1_2020-12-31` |
| 2.1     | `validator-configuration-xrechnung_2.1.1_2021-11-15` |
| 2.2     | `validator-configuration-xrechnung_2.2.0_2022-11-15` |
| 2.3     | `validator-configuration-xrechnung_2.3.1_2023-05-12` |

## Dependency

After installing Paniolo from the repository root with `mvn install`, add:

```xml
<dependency>
  <groupId>io.github.nalukit</groupId>
  <artifactId>paniolo-xrechnung-rules</artifactId>
  <version>HEAD-SNAPSHOT</version>
</dependency>
```

## Usage

```java
import io.github.nalukit.paniolo.rules.xrechnung.XrechnungRules;
import io.github.nalukit.paniolo.validation.InvoiceValidator;
import io.github.nalukit.paniolo.validation.ValidationProfile;

var validator = new InvoiceValidator(
    ValidationProfile.xrechnung(XrechnungRules.bundle(), ValidationProfile.Syntax.CII));
```

| Method                         | Behavior                                                                                                        |
|--------------------------------|-----------------------------------------------------------------------------------------------------------------|
| `XrechnungRules.bundle()`      | Extracts once per JVM into a private temporary directory, deleted on exit                                       |
| `XrechnungRules.bundle(Path)`  | Extracts into the given directory and reuses it while it holds the same release; refuses other existing content |
| `VERSION`, `RELEASE`, `SHA256` | XRechnung version, KoSIT release date, and archive checksum                                                     |
| `LEGACY_VERSIONS`              | Older versions with a packaged release: `1.2`, `2.0`, `2.1`, `2.2`, `2.3`                                       |
| `legacyBundle(version)`        | Extracts the release of an older version once per JVM into a private temporary directory, deleted on exit       |
| `legacyVersion(version)`       | Full XRechnung version of that release, e.g. `2.3.1` for `2.3`                                                  |

The archive is stored unchanged in the JAR. Its SHA-256 is verified when the module is
built and again on every extraction; archive entries cannot escape the target directory.
Protect a persistent directory against modification by others, because rules are executed
as trusted stylesheets.

`bundle()` is synchronized: concurrent callers in one class loader get the same directory,
extracted once. `bundle(Path)` can be called from several processes for the same directory;
extraction happens in a staging directory that is moved into place atomically.

The module does not depend on `paniolo-validation`; it only provides the extracted
directory. To use a different rule release, pass that bundle's path to
`ValidationProfile.xrechnung` instead.

## Updating the rules

Change `xrechnung.version`, `xrechnung.release`, and `xrechnung.sha256` together in this
module's `pom.xml`, using the values of the new
[KoSIT release](https://github.com/itplr-kosit/validator-configuration-xrechnung/releases).
The build downloads the archive with the download-maven-plugin, fails on a checksum
mismatch, and caches it in the local Maven repository; only the first build needs network
access. The older releases are pinned the same way by the `xrechnung.legacy*` properties; they
change only when KoSIT publishes a fix for an old version. Then run the integration tests:

```sh
mvn verify
```

## IDE builds

The archive is downloaded into `target/generated-resources/xrechnung`, which the POM declares
as a resource directory. IDEs such as IntelliJ IDEA do not run Maven plugins when they build,
but they copy declared resource directories. Run any Maven build of this module once (for
example `mvn -pl paniolo-xrechnung-rules generate-resources`) and reload the Maven project;
afterwards IDE builds and rebuilds include the archive. `mvn clean` removes it again. If it is
missing, extraction fails with a message explaining these steps.

## Licenses

The Paniolo code in this module is licensed under Apache 2.0. The packaged archive is
redistributed unmodified and contains works under their publishers' terms:

| Content                                                       | Publisher                       | Terms                                                   |
|---------------------------------------------------------------|---------------------------------|---------------------------------------------------------|
| `scenarios.xml`, report stylesheets, `resources/xrechnung/**` | KoSIT                           | Apache License 2.0                                      |
| `EN16931-UBL-validation.xsl`, `EN16931-CII-validation.xsl`    | CEN/TC 434 via ConnectingEurope | EUPL 1.2                                                |
| `resources/ubl/**`                                            | OASIS (UBL 2.1)                 | OASIS copyright notice, unmodified copies permitted     |
| `resources/ubl/2.1/xsd/common/UBL-xmldsig-core-schema-2.1.xsd` | W3C (XML Signature)            | W3C Software License, notice in the file                |
| `resources/cii/**`                                            | UN/CEFACT (D16B)                | UN/CEFACT copyright notice, unmodified copies permitted |

The older releases contain the same kinds of works under the same terms; the release for
XRechnung 1.2 additionally contains the ISO Schematron skeleton stylesheets (`schematron/iso_*.xsl`,
MIT License, notice in each file).

The JAR contains `META-INF/NOTICE` and the license texts and notices under
`META-INF/licenses/`. This summary is not legal advice; check the terms for your use.

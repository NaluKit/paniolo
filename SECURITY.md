# Security Policy

## Supported versions

Security fixes are provided only for the latest released version. A fix is published as a new
release; older versions receive no patches. To get security fixes, update to the latest version.

| Version                | Supported | Notes                                                                 |
|------------------------|-----------|-----------------------------------------------------------------------|
| `1.1.x`                | Yes       | Current release line (`1.1.0`), published to Maven Central.           |
| `HEAD-SNAPSHOT`        | No        | Development builds of the `dev` branch. Not for production use.       |

Each new release replaces the previous one as the only supported version.

Paniolo processes invoices and orders received from third parties. Security relevant are in
particular XML parsing (external entities, entity expansion), PDF parsing and attachment
extraction, path handling in the command line tool, and the packaged validation rules. Paniolo's
dependencies are checked with OWASP Dependency-Check in every CI build.

## How releases are built

Code pushes to `dev` and `main`, and pull requests targeting those branches, are built by GitHub
Actions on Java 21 and 25 with the tests and the public invoice corpus. Documentation-only pushes
matching the workflow's ignored paths are skipped; pull requests still run. OWASP Dependency-Check fails the build for
vulnerabilities with a CVSS score of 7 or higher. A weekly run repeats the checks on unchanged
code. Releases are built by the same workflow from the `main` branch only:

- **Signatures:** every artifact on Maven Central is signed with GPG and carries MD5, SHA-1,
  SHA-256 and SHA-512 checksums.
- **SBOM:** every module is published with its software bill of materials in CycloneDX format,
  `<module>-<version>-cyclonedx.json`, listing all runtime dependencies with version, license and
  hashes.
- **Provenance:** the JARs of a release carry a build provenance attestation of GitHub, which
  binds them to the commit and the workflow run that built them.
- **Evidence:** the GitHub release of a version lists the JARs, the SBOMs, their SHA-256
  checksums (`SHA256SUMS`), the OWASP Dependency-Check report of the release build, the test
  evidence, and the changes from `CHANGELOG.md`.
- **Test evidence:** `paniolo-<version>-test-evidence.tar.gz` holds the Surefire and Failsafe
  reports of the release run for Java 21, Java 25 (with the invoice corpus) and the browser tests,
  the tool versions of every job (`environment.txt`), the workflow and the POMs that configure the
  checks, and `evidence.json`. The latter names the commit and the workflow run and lists, per test
  job, the number of tests and every skipped test case with the reason of its report; it also lists
  the SHA-256 of every archived file. The summary is attached as `paniolo-<version>-test-evidence.json`,
  the archive carries a build provenance attestation, and `SHA256SUMS-test-evidence` holds the
  checksums of both files. The evidence is kept as long as the release exists; release assets are
  not touched by the cleanup workflow for old workflow runs, so the results can be checked without
  the logs of the run. The OWASP result is the report `paniolo-<version>-dependency-check-report.html`.

## Verifying a release

The build fixes archive timestamps and entry order, but this alone does not guarantee
byte-identical JARs or SBOMs across environments. An independent rebuild of `1.0.0-rc02` found
identical class bytes but different manifests and generated JavaDoc. Record the exact JDK,
Maven version, operating system, and resolved build dependencies when comparing a rebuild.

1. Check the provenance of a downloaded JAR with the GitHub CLI:

   ```bash
   gh attestation verify paniolo-<version>.jar --repo NaluKit/paniolo
   ```

2. Rebuild the release with the same toolchain as its release workflow, including the exact
   Temurin 21 build, and compare the checksums with those of the release:

   ```bash
   git checkout <version>
   timestamp=$(TZ=UTC0 git log -1 --format=%cd --date=format-local:%Y-%m-%dT%H:%M:%SZ)
   mvn package -DskipTests -Dgpg.skip -P release -Drevision=<version> \
       -Dproject.build.outputTimestamp="$timestamp"
   scripts/release/collect-release-files.sh <version> rebuilt
   diff rebuilt/SHA256SUMS downloaded-release/SHA256SUMS
   ```

Here `downloaded-release/SHA256SUMS` is the manifest from the GitHub release; first verify the
downloaded assets against it. A rebuild mismatch requires inspection of the archive entries,
manifests, generated documentation, and SBOM metadata; it does not by itself establish a code
change or a compromised release. The rebuild commands skip tests and signatures and are not a
release acceptance check. Releases up to
`1.0.0-rc01` were built before these measures and carry neither SBOM nor attestation.

## Reporting a vulnerability

Please do **not** open a public issue for a vulnerability.

1. Preferred: report it privately via GitHub at
   [Security → Report a vulnerability](https://github.com/nalukit/paniolo/security/advisories/new).
2. Alternatively, send an e-mail to `contact@nalu-solutions.de` with `[security] paniolo` in the
   subject.

Please include:

- the affected Paniolo version or commit and the module (for example `paniolo-facturx`),
- a description of the impact and, if possible, a minimal proof of concept,
- whether attached documents contain confidential data. Please anonymize invoices where possible.

## After your report

- We confirm receipt within five working days and tell you whether we can reproduce the issue.
- We assess the severity and agree on a disclosure date with you. Until then the report stays
  private.
- The fix is published as a new release of the latest version, together with a GitHub security
  advisory that names the affected and fixed versions. We credit you unless you prefer otherwise.

Thank you for helping to keep Paniolo and its users safe.

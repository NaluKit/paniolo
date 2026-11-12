# Security Policy

## Supported versions

Security fixes are provided only for the latest released version. A fix is published as a new
release; older versions receive no patches. To get security fixes, update to the latest version.

| Version                | Supported | Notes                                                                 |
|------------------------|-----------|-----------------------------------------------------------------------|
| `1.0.x`                | Yes       | Current release line (`1.0.0-rc01`), published to Maven Central.      |
| `HEAD-SNAPSHOT`        | No        | Development builds of the `dev` branch. Not for production use.       |

Each new release replaces the previous one as the only supported version.

Paniolo processes invoices and orders received from third parties. Security relevant are in
particular XML parsing (external entities, entity expansion), PDF parsing and attachment
extraction, path handling in the command line tool, and the packaged validation rules. Paniolo's
dependencies are checked with OWASP Dependency-Check in every CI build.

## How releases are built

Every push and pull request is built by GitHub Actions on Java 21 and 25 with all tests and the
public invoice corpus, and checked with OWASP Dependency-Check, which fails the build for
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
  checksums (`SHA256SUMS`), the OWASP Dependency-Check report of the release build, and the
  changes from `CHANGELOG.md`.

## Verifying a release

The build is reproducible: building the released commit again yields byte-identical JARs.

1. Check the provenance of a downloaded JAR with the GitHub CLI:

   ```bash
   gh attestation verify paniolo-<version>.jar --repo NaluKit/paniolo
   ```

2. Rebuild the release with Temurin 21 and compare the checksums with those of the release:

   ```bash
   git checkout <version>
   timestamp=$(TZ=UTC0 git log -1 --format=%cd --date=format-local:%Y-%m-%dT%H:%M:%SZ)
   mvn package -DskipTests -Dgpg.skip -P release -Drevision=<version> \
       -Dproject.build.outputTimestamp="$timestamp"
   scripts/release/collect-release-files.sh <version> rebuilt
   diff rebuilt/SHA256SUMS <SHA256SUMS of the GitHub release>
   ```

The SBOMs are produced by the same build and can be compared the same way. Releases up to
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

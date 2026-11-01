# Security Policy

## Supported versions

Security fixes are provided only for the latest released version. A fix is published as a new
release; older versions receive no patches. To get security fixes, update to the latest version.

| Version                | Supported | Notes                                                                 |
|------------------------|-----------|-----------------------------------------------------------------------|
| `HEAD-SNAPSHOT`        | No        | Development builds of the `dev` branch. Not for production use.       |
| Local `0.0.0-<commit>` | No        | Source baselines built locally for the versioned API reference.       |

Paniolo has no published release yet. Once the first version is released, it is listed here as
supported. Each new release replaces the previous one as the only supported version.

Paniolo processes invoices and orders received from third parties. Security relevant are in
particular XML parsing (external entities, entity expansion), PDF parsing and attachment
extraction, path handling in the command line tool, and the packaged validation rules. Paniolo's
dependencies are checked with OWASP Dependency-Check in every CI build.

## Reporting a vulnerability

Please do **not** open a public issue for a vulnerability.

1. Preferred: report it privately via GitHub at
   [Security → Report a vulnerability](https://github.com/nalukit/paniolo/security/advisories/new).
2. Alternatively, send an e-mail to `contact@nalu-solutions.de` with `[security] paniolo` in the
   subject.

Please include:

- the affected Paniolo version or commit and the module (for example `paniolo-xml-cii`),
- a description of the impact and, if possible, a minimal proof of concept,
- whether attached documents contain confidential data. Please anonymize invoices where possible.

## After your report

- We confirm receipt within five working days and tell you whether we can reproduce the issue.
- We assess the severity and agree on a disclosure date with you. Until then the report stays
  private.
- The fix is published as a new release of the latest version, together with a GitHub security
  advisory that names the affected and fixed versions. We credit you unless you prefer otherwise.

Thank you for helping to keep Paniolo and its users safe.

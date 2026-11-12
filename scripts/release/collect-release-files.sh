#!/usr/bin/env bash
#
# Copyright © 2026 Frank Hossfeld
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
# SPDX-License-Identifier: Apache-2.0
#

# Collects the evidence of a release into one directory, for the GitHub release and for anyone who
# rebuilds the release to compare it:
#   <module>-<version>[-sources|-javadoc|-all].jar   the JARs of every published module
#   <module>-<version>-cyclonedx.json                 the SBOM (CycloneDX) of every module
#   SHA256SUMS                                        SHA-256 of all these files
#   RELEASE-NOTES.md                                  the section of the version in CHANGELOG.md
#
# Run it after a build with the release profile of the same version:
#   mvn package -DskipTests -Dgpg.skip -P release -Drevision=<version> \
#       -Dproject.build.outputTimestamp=<commit time, UTC>
#   scripts/release/collect-release-files.sh <version> <directory>
set -euo pipefail

if [[ $# -ne 2 ]]; then
  echo "usage: $0 <version> <directory>" >&2
  exit 2
fi
VERSION=$1
OUT=$2
ROOT=$(cd "$(dirname "$0")/../.." && pwd)

mkdir -p "$OUT"
OUT=$(cd "$OUT" && pwd)

# The modules of the reactor; benchmarks and the invoice corpus are never published.
modules=$(sed -n 's:.*<module>\(.*\)</module>.*:\1:p' "$ROOT/pom.xml" | grep -v -e '^paniolo-benchmark$' -e '^test-paniolo-invoice-corpus$')
count=0
for module in $modules; do
  target="$ROOT/$module/target"
  if [[ ! -f $target/$module-$VERSION.jar ]]; then
    echo "Missing $target/$module-$VERSION.jar; build version $VERSION with the release profile first" >&2
    exit 1
  fi
  for jar in "$target/$module-$VERSION".jar "$target/$module-$VERSION"-*.jar; do
    [[ -f $jar ]] && cp "$jar" "$OUT/" && count=$((count + 1))
  done
  if [[ ! -f $target/bom.json ]]; then
    echo "Missing SBOM $target/bom.json" >&2
    exit 1
  fi
  cp "$target/bom.json" "$OUT/$module-$VERSION-cyclonedx.json"
done
if [[ -f $ROOT/target/bom.json ]]; then
  cp "$ROOT/target/bom.json" "$OUT/paniolo-parent-$VERSION-cyclonedx.json"
fi

(cd "$OUT" && find . -maxdepth 1 -type f \( -name '*.jar' -o -name '*-cyclonedx.json' \) | sed 's:^\./::' | LC_ALL=C sort |
  xargs shasum -a 256 > SHA256SUMS)

# The release notes: the section '## [<version>] - <date>' of CHANGELOG.md up to the next section.
# Link definitions at the end of the file are left out, and relative links point to the files of
# the release tag, because the release page has no base path.
notes=$(awk -v version="$VERSION" '
  index($0, "## [" version "] - ") == 1 { inside = 1; next }
  inside && /^## \[/ { exit }
  inside && /^\[[^]]+\]: / { next }
  inside { print }
' "$ROOT/CHANGELOG.md" | sed -E "s#\]\(([^):#][^)]*)\)#](https://github.com/NaluKit/paniolo/blob/$VERSION/\1)#g")
if [[ -z ${notes//[[:space:]]/} ]]; then
  echo "CHANGELOG.md has no section '## [$VERSION] - <date>'" >&2
  exit 1
fi
{
  printf '%s\n' "$notes"
  echo
  echo "## Verification"
  echo
  echo "The JARs are published to Maven Central with GPG signatures. This release carries for every"
  echo "module the SBOM (\`*-cyclonedx.json\`), the SHA-256 checksums of all files (\`SHA256SUMS\`), the"
  echo "OWASP Dependency-Check report of the release build, and a build provenance attestation of"
  echo "GitHub. The build is reproducible; see [Verifying a release](https://github.com/NaluKit/paniolo/blob/main/SECURITY.md#verifying-a-release)."
} > "$OUT/RELEASE-NOTES.md"

echo "Collected $count JARs of version $VERSION in $OUT"

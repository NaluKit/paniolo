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

#
# Checks that a Paniolo release is described consistently: release version, Git tag, Maven
# version, SECURITY.md, CHANGELOG.md, README, versioned API reference and, optionally, website.
#
#   scripts/release/check-release.sh <version>
#       Static checks of the working tree. The CI build runs them for every pull request
#       against main, so a release is checked before it is merged.
#
#   scripts/release/check-release.sh <version> --site <dir>
#       Additionally checks the Paniolo pages of the website. The website is maintained in the
#       repository NaluKit/nalukit-web-site; <dir> is its directory src/content/paniolo.
#
#   scripts/release/check-release.sh <version> --artifacts [--repository <url>]
#       Additionally builds the quickstart of docs/api/<version>.md as a new project, with an
#       empty local Maven repository and only Maven Central (plus <url>, e.g. a staging
#       repository) as source. SNAPSHOT resolution is disabled, so the example must build from
#       the published release artifacts alone. Run this after the release was published.
#
# Exit code 0: all checks passed, 1: at least one check failed, 2: usage error.

set -euo pipefail

usage() {
  echo "usage: $0 <version> [--site <dir>] [--artifacts] [--repository <url>]" >&2
  exit 2
}

[[ $# -ge 1 ]] || usage
VERSION=$1
shift
ARTIFACTS=false
REPOSITORY=
SITE=
while [[ $# -gt 0 ]]; do
  case $1 in
    --artifacts) ARTIFACTS=true ;;
    --site) [[ $# -ge 2 ]] || usage; SITE=$2; shift ;;
    --repository) [[ $# -ge 2 ]] || usage; REPOSITORY=$2; shift ;;
    *) usage ;;
  esac
  shift
done

ROOT=$(cd "$(dirname "$0")/../.." && pwd)
REFERENCE="docs/api/$VERSION.md"
FAILURES=0
COMMIT=

ok() { echo "  ok    $*"; }
fail() { echo "  FAIL  $*"; FAILURES=$((FAILURES + 1)); }

# Prints "file:line:version" for every io.github.nalukit dependency version in a file.
nalukit_versions() {
  awk -v file="$1" '
    /<groupId>io\.github\.nalukit<\/groupId>/ { pending = 3; next }
    pending > 0 && /<version>/ {
      v = $0; sub(/.*<version>/, "", v); sub(/<\/version>.*/, "", v)
      print file ":" NR ":" v; pending = 0; next
    }
    { pending-- }
  ' "$ROOT/$1"
}

# Prints the first fenced block of the given language that contains the marker text.
fenced_block() {
  awk -v lang="$2" -v marker="$3" '
    !inside && $0 == "```" lang { inside = 1; block = ""; next }
    inside && $0 == "```" { inside = 0; if (index(block, marker)) { printf "%s", block; exit } next }
    inside { block = block $0 "\n" }
  ' "$ROOT/$1"
}

echo "Release $VERSION"

echo "Version and tag"
if [[ $VERSION =~ ^[0-9]+\.[0-9]+\.[0-9]+([.-][0-9A-Za-z.-]+)?$ && $VERSION != *SNAPSHOT* ]]; then
  ok "release version format"
else
  fail "'$VERSION' is not a release version (x.y.z, optional qualifier, no SNAPSHOT)"
fi
REVISION=$(sed -n 's:.*<revision>\(.*\)</revision>.*:\1:p' "$ROOT/pom.xml" | head -n 1)
if [[ $REVISION == "$VERSION" ]]; then
  ok "pom.xml revision is $VERSION"
else
  fail "pom.xml revision is '$REVISION', expected '$VERSION'"
fi
if git -C "$ROOT" rev-parse -q --verify "refs/tags/$VERSION" > /dev/null; then
  if [[ $(git -C "$ROOT" rev-parse "refs/tags/$VERSION^{commit}") == $(git -C "$ROOT" rev-parse HEAD) ]]; then
    ok "tag $VERSION points to the checked-out commit"
  else
    fail "tag $VERSION exists but does not point to the checked-out commit"
  fi
else
  ok "tag $VERSION does not exist yet (created by the release workflow)"
fi

echo "SECURITY.md"
LINE=$(echo "$VERSION" | cut -d. -f1-2).x
if grep -Eq "^\| *\`$LINE\` *\| *Yes" "$ROOT/SECURITY.md"; then
  ok "release line $LINE is listed as supported"
else
  fail "SECURITY.md has no row '| \`$LINE\` | Yes |' in 'Supported versions'"
fi

echo "CHANGELOG.md"
if [[ ! -f $ROOT/CHANGELOG.md ]]; then
  fail "CHANGELOG.md is missing"
else
  if grep -Eq "^## \[${VERSION//./\\.}\] - [0-9]{4}-[0-9]{2}-[0-9]{2}$" "$ROOT/CHANGELOG.md"; then
    ok "section '## [$VERSION] - <date>'"
  else
    fail "CHANGELOG.md has no section '## [$VERSION] - YYYY-MM-DD'"
  fi
  if grep -Fxq "[$VERSION]: https://github.com/NaluKit/paniolo/releases/tag/$VERSION" "$ROOT/CHANGELOG.md"; then
    ok "link to the release $VERSION"
  else
    fail "CHANGELOG.md has no link '[$VERSION]: https://github.com/NaluKit/paniolo/releases/tag/$VERSION'"
  fi
  UNRELEASED=$(grep -E '^\[Unreleased\]: ' "$ROOT/CHANGELOG.md" || true)
  if [[ -z $UNRELEASED || $UNRELEASED == "[Unreleased]: https://github.com/NaluKit/paniolo/compare/$VERSION...HEAD" ]]; then
    ok "[Unreleased] compares with $VERSION"
  else
    fail "CHANGELOG.md [Unreleased] link does not compare with $VERSION: ${UNRELEASED#*: }"
  fi
fi

echo "Versioned API reference"
if [[ -f $ROOT/$REFERENCE ]]; then
  ok "$REFERENCE exists"
  if head -n 1 "$ROOT/$REFERENCE" | grep -Fq "$VERSION"; then
    ok "title names $VERSION"
  else
    fail "title of $REFERENCE does not name $VERSION"
  fi
  if grep -Eq "SNAPSHOT|0\.0\.0-|git archive" "$ROOT/$REFERENCE"; then
    fail "$REFERENCE refers to a SNAPSHOT, a local 0.0.0-<commit> coordinate or a source build:"
    grep -En "SNAPSHOT|0\.0\.0-|git archive" "$ROOT/$REFERENCE" | sed 's/^/          /'
  else
    ok "no SNAPSHOT, local coordinate or source build"
  fi
  # The pinned commit contains the release sources. Between it and the release only documentation
  # may change, because the reference itself is committed after the commit it names.
  COMMIT=$(grep -Eo '`[0-9a-f]{40}`' "$ROOT/$REFERENCE" | head -n 1 | tr -d '`')
  if [[ -z $COMMIT ]]; then
    fail "$REFERENCE names no full source commit"
  elif ! git -C "$ROOT" merge-base --is-ancestor "$COMMIT" HEAD 2> /dev/null; then
    fail "source commit $COMMIT is not part of the checked-out history"
  else
    CHANGED=$(git -C "$ROOT" diff --name-only "$COMMIT" HEAD | grep -Ev '\.md$|^docs/' || true)
    if [[ -z $CHANGED ]]; then
      ok "source commit $COMMIT; later changes only touch documentation"
    else
      fail "files changed after source commit $COMMIT: $(echo $CHANGED)"
    fi
  fi
else
  fail "$REFERENCE is missing"
fi

echo "README"
for f in $REFERENCE README.md; do
  [[ -f $ROOT/$f ]] || continue
  while IFS=: read -r file line found; do
    if [[ $found == "$VERSION" ]]; then
      ok "$file:$line dependency version"
    else
      fail "$file:$line dependency version is '$found', expected '$VERSION'"
    fi
  done < <(nalukit_versions "$f")
done
LINKS=$(grep -Eo '\]\(docs/api/[^)]+\.md' "$ROOT/README.md" | cut -c3- | sort -u)
if [[ $LINKS == "$REFERENCE" ]]; then
  ok "README links only $REFERENCE"
else
  fail "README must link only $REFERENCE, found: $(echo $LINKS)"
fi
if grep -Fq "SECURITY.md" "$ROOT/README.md"; then
  ok "README links SECURITY.md"
else
  fail "README does not link SECURITY.md"
fi

echo "Website"
if [[ -z $SITE ]]; then
  echo "  skip  maintained in NaluKit/nalukit-web-site; pass --site <dir> to check it"
elif [[ ! -d $SITE ]]; then
  fail "website directory '$SITE' does not exist"
else
  for page in api.html formats.html quickstart.html; do
    if [[ ! -f $SITE/$page ]]; then
      fail "$page is missing in $SITE"
      continue
    fi
    # every page names release and source commit in its sidebar: <p class="pn-meta">1.0.0<br><code>7b7f6401ef2d</code></p>
    META=$(grep -Eo '<p class="pn-meta">[^<]+<br><code>[0-9a-f]+</code>' "$SITE/$page" | head -n 1 || true)
    SHOWN=$(echo "$META" | sed -E 's/.*pn-meta">([^<]+)<br>.*/\1/')
    SHOWN_COMMIT=$(echo "$META" | sed -E 's/.*<code>([0-9a-f]+)<\/code>.*/\1/')
    if [[ -z $META ]]; then
      fail "$page shows no release and commit (<p class=\"pn-meta\">)"
    elif [[ $SHOWN != "$VERSION" ]]; then
      fail "$page shows release '$SHOWN', expected '$VERSION'"
    elif [[ -n $COMMIT && $COMMIT != "$SHOWN_COMMIT"* ]]; then
      fail "$page shows commit $SHOWN_COMMIT, $REFERENCE names $COMMIT"
    else
      ok "$page shows $VERSION, commit $SHOWN_COMMIT"
    fi
    if grep -Eq "SNAPSHOT|0\.0\.0-" "$SITE/$page"; then
      fail "$page still shows a SNAPSHOT or a local 0.0.0-<commit> coordinate"
    fi
  done
fi

if [[ $ARTIFACTS == true && $FAILURES -gt 0 ]]; then
  echo "Quickstart against published artifacts"
  fail "skipped, fix the failed checks above first"
elif [[ $ARTIFACTS == true ]]; then
  echo "Quickstart against published artifacts"
  WORK=$(mktemp -d "${TMPDIR:-/tmp}/paniolo-release-check.XXXXXX")
  APP=$WORK/app
  mkdir -p "$APP/src/main/java/example"
  fenced_block "$REFERENCE" xml "<artifactId>paniolo-quickstart</artifactId>" > "$APP/pom.xml"
  fenced_block "$REFERENCE" java "public final class Quickstart" > "$APP/src/main/java/example/Quickstart.java"
  fenced_block "$REFERENCE" text "valid, warnings=" > "$WORK/expected.txt"
  for f in "$APP/pom.xml" "$APP/src/main/java/example/Quickstart.java" "$WORK/expected.txt"; do
    [[ -s $f ]] || fail "could not extract $(basename "$f") from $REFERENCE"
  done
  # Settings without the user's mirrors or profiles: Maven Central, optionally one more release
  # repository, never snapshots.
  {
    echo "<settings><profiles><profile><id>release-check</id><repositories>"
    if [[ -n $REPOSITORY ]]; then
      echo "<repository><id>release-check</id><url>$REPOSITORY</url>"
      echo "<releases><enabled>true</enabled></releases><snapshots><enabled>false</enabled></snapshots></repository>"
    fi
    echo "</repositories></profile></profiles><activeProfiles><activeProfile>release-check</activeProfile></activeProfiles></settings>"
  } > "$WORK/settings.xml"
  if [[ $FAILURES -eq 0 ]] \
     && echo "  ...   building $APP (may take a while)" \
     && (cd "$APP" && mvn -B -ntp -s "$WORK/settings.xml" -Dmaven.repo.local="$WORK/repository" \
           compile dependency:list \
           org.apache.maven.plugins:maven-dependency-plugin:3.7.0:copy-dependencies \
           -DincludeScope=runtime -DoutputFile="$WORK/dependencies.txt" > "$WORK/maven.log" 2>&1); then
    ok "new project builds with an empty local repository (log: $WORK/maven.log)"
    if grep -q SNAPSHOT "$WORK/dependencies.txt"; then
      fail "resolved dependencies contain SNAPSHOT artifacts ($WORK/dependencies.txt)"
    else
      ok "no SNAPSHOT dependency resolved"
    fi
    if (cd "$APP" && java -cp 'target/classes:target/dependency/*' example.Quickstart > "$WORK/output.txt" 2> "$WORK/stderr.txt") \
       && diff -u "$WORK/expected.txt" "$WORK/output.txt"; then
      ok "quickstart output matches the expected output in $REFERENCE"
    else
      fail "quickstart run differs from the expected output (see $WORK)"
    fi
  else
    fail "quickstart project did not build (see $WORK/maven.log)"
  fi
fi

if [[ $FAILURES -eq 0 ]]; then
  echo "All checks passed."
else
  echo "$FAILURES check(s) failed."
  exit 1
fi

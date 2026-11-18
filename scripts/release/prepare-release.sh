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
# Makes the mechanical changes of a Paniolo release in the working tree. It commits nothing. The
# release takes two commits, because the API reference names the commit with the release sources:
#
#   scripts/release/prepare-release.sh version <version>
#       Sets the revision in pom.xml and the supported release line in SECURITY.md.
#       Commit these changes ("Set version to <version>"); this commit is the source commit.
#
#   scripts/release/prepare-release.sh docs <version> [--date YYYY-MM-DD] [--source <commit>]
#       Run it on the source commit (default: HEAD). Turns [Unreleased] of CHANGELOG.md into the
#       section of the release, creates docs/api/<version>.md from the reference of the previous
#       release, updates the versions and links in README.md and the module READMEs, and runs
#       check-release.sh. Then complete the API reference by hand and commit
#       ("Add API reference for <version>").
#
# The previous release is taken from the [Unreleased] link of CHANGELOG.md.
#
# Exit code 0: changes made, 1: a precondition or the release check failed, 2: usage error.

set -euo pipefail

usage() {
  echo "usage: $0 version <version>" >&2
  echo "       $0 docs <version> [--date YYYY-MM-DD] [--source <commit>]" >&2
  exit 2
}

[[ $# -ge 2 ]] || usage
STEP=$1
VERSION=$2
shift 2
DATE=$(date +%F)
SOURCE=
while [[ $# -gt 0 ]]; do
  case $1 in
    --date) [[ $# -ge 2 && $STEP == docs ]] || usage; DATE=$2; shift ;;
    --source) [[ $# -ge 2 && $STEP == docs ]] || usage; SOURCE=$2; shift ;;
    *) usage ;;
  esac
  shift
done
[[ $STEP == version || $STEP == docs ]] || usage

ROOT=$(cd "$(dirname "$0")/../.." && pwd)
cd "$ROOT"

die() { echo "error: $*" >&2; exit 1; }
todo() { echo "  todo  $*"; }

[[ $VERSION =~ ^[0-9]+\.[0-9]+\.[0-9]+([.-][0-9A-Za-z.-]+)?$ && $VERSION != *SNAPSHOT* ]] ||
  die "'$VERSION' is not a release version (x.y.z, optional qualifier, no SNAPSHOT)"
[[ $DATE =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}$ ]] || die "'$DATE' is not a date YYYY-MM-DD"
[[ -z $(git status --porcelain --untracked-files=no) ]] || die "the working tree has uncommitted changes"
! git rev-parse -q --verify "refs/tags/$VERSION" > /dev/null || die "tag $VERSION already exists"

PREVIOUS=$(sed -nE 's#^\[Unreleased\]: .*/compare/(.+)\.\.\.HEAD$#\1#p' CHANGELOG.md)
[[ -n $PREVIOUS ]] || die "CHANGELOG.md has no link '[Unreleased]: .../compare/<previous>...HEAD'"
[[ $PREVIOUS != "$VERSION" ]] || die "CHANGELOG.md already compares [Unreleased] with $VERSION"
REVISION=$(sed -n 's:.*<revision>\(.*\)</revision>.*:\1:p' pom.xml | head -n 1)

# Replaces literal text in files: replace <from> <to> <file>...
replace() {
  FROM=$1 TO=$2 perl -pi -e 's/\Q$ENV{FROM}\E/$ENV{TO}/g' "${@:3}"
}

if [[ $STEP == version ]]; then
  [[ $REVISION != "$VERSION" ]] || die "pom.xml already has the revision $VERSION"
  case $(git rev-parse --abbrev-ref HEAD) in
    dev | main) echo "note: you are on $(git rev-parse --abbrev-ref HEAD); a release is prepared on release/$VERSION" ;;
  esac

  echo "Prepare $VERSION (previous release $PREVIOUS): version"
  OLD=$REVISION NEW=$VERSION perl -pi -e 's#<revision>\Q$ENV{OLD}\E</revision>#<revision>$ENV{NEW}</revision>#' pom.xml
  echo "  ok    pom.xml revision $REVISION -> $VERSION"

  # The row of the supported release line, with the cells padded to the width of the table.
  LINE=$(echo "$VERSION" | cut -d. -f1-2).x
  LINE=$LINE NEW=$VERSION perl -pi -e '
    @widths = map { length } split /\|/, $_, -1 if /^\|-/;
    if (/^\|\s*`\d+\.\d+\.x`\s*\|\s*Yes\s*\|/) {
      my @cells = split /\|/, $_, -1;
      for my $i (1 .. $#cells - 1) {
        my $width = $widths[$i] || length $cells[$i];
        $cells[$i] =~ s/`\d+\.\d+\.x`/`$ENV{LINE}`/;
        $cells[$i] =~ s/\(`[^`]+`\)/(`$ENV{NEW}`)/;
        $cells[$i] =~ s/\s+$//;
        $cells[$i] .= " " x ($width - length $cells[$i]) if length $cells[$i] < $width;
        $cells[$i] .= " " unless $cells[$i] =~ /\s$/;
      }
      $_ = join "|", @cells;
      $found = 1;
    }
    END { exit($found ? 0 : 3) }
  ' SECURITY.md || die "SECURITY.md has no row '| \`x.y.x\` | Yes |' in 'Supported versions'"
  echo "  ok    SECURITY.md supported line $LINE ($VERSION)"

  echo
  echo "Next: check 'git diff', commit as \"Set version to $VERSION\", then run"
  echo "  $0 docs $VERSION"
  exit 0
fi

# Step docs.
[[ $REVISION == "$VERSION" ]] || die "pom.xml has the revision '$REVISION'; run '$0 version $VERSION' and commit first"
SOURCE=$(git rev-parse --verify -q "${SOURCE:-HEAD}^{commit}") || die "unknown source commit"
[[ $(git show "$SOURCE:pom.xml" | sed -n 's:.*<revision>\(.*\)</revision>.*:\1:p' | head -n 1) == "$VERSION" ]] ||
  die "the source commit $SOURCE does not have the revision $VERSION in pom.xml"
REFERENCE=docs/api/$VERSION.md
PREVIOUS_REFERENCE=docs/api/$PREVIOUS.md
[[ ! -e $REFERENCE ]] || die "$REFERENCE already exists"
[[ -f $PREVIOUS_REFERENCE ]] || die "$PREVIOUS_REFERENCE is missing"
grep -q '^## \[Unreleased\]$' CHANGELOG.md || die "CHANGELOG.md has no line '## [Unreleased]'"
! grep -q "^## \[$VERSION\]" CHANGELOG.md || die "CHANGELOG.md already has a section for $VERSION"
UNRELEASED_TEXT=$(awk '/^## \[Unreleased\]$/ { inside = 1; next } inside && /^## \[/ { exit } inside' CHANGELOG.md)
[[ -n ${UNRELEASED_TEXT//[[:space:]]/} ]] || die "the section [Unreleased] of CHANGELOG.md is empty"

echo "Prepare $VERSION (previous release $PREVIOUS): docs, source commit $SOURCE"

# CHANGELOG.md: the content of [Unreleased] becomes the section of the release.
NEW=$VERSION DATE=$DATE PREV=$PREVIOUS perl -pi -e '
  if ($_ eq "## [Unreleased]\n") {
    $_ .= "\n## [$ENV{NEW}] - $ENV{DATE}\n\nSee the [API reference for $ENV{NEW}](docs/api/$ENV{NEW}.md).\n";
  }
  if (/^\[Unreleased\]: /) {
    s#/compare/\Q$ENV{PREV}\E\.\.\.HEAD#/compare/$ENV{NEW}...HEAD#;
    $_ .= "[$ENV{NEW}]: https://github.com/NaluKit/paniolo/releases/tag/$ENV{NEW}\n";
  }
' CHANGELOG.md
echo "  ok    CHANGELOG.md section '## [$VERSION] - $DATE' and links"

# The API reference: a copy of the previous one with version, source commit and revision date.
cp "$PREVIOUS_REFERENCE" "$REFERENCE"
replace "$PREVIOUS" "$VERSION" "$REFERENCE"
SOURCE=$SOURCE perl -pi -e 'if (!$done && s/`[0-9a-f]{40}`/`$ENV{SOURCE}`/) { $done = 1 }' "$REFERENCE"
DATE=$DATE perl -pi -e 's/^(\| Documentation revision\s*\| )\d+, \d{4}-\d{2}-\d{2}/${1}1, $ENV{DATE}/' "$REFERENCE"
echo "  ok    $REFERENCE from $PREVIOUS_REFERENCE"

# README.md and the module READMEs: dependency versions, links, the CLI JAR and the pinned commit.
READMES=(README.md)
while IFS= read -r module; do
  [[ -f $module/README.md ]] && READMES+=("$module/README.md")
done < <(sed -n 's:.*<module>\(.*\)</module>.*:\1:p' pom.xml)
for readme in "${READMES[@]}"; do
  before=$(cksum < "$readme")
  replace "<version>$PREVIOUS</version>" "<version>$VERSION</version>" "$readme"
  replace "docs/api/$PREVIOUS.md" "docs/api/$VERSION.md" "$readme"
  replace "\`$PREVIOUS\`" "\`$VERSION\`" "$readme"
  replace "paniolo-cli-$PREVIOUS-all.jar" "paniolo-cli-$VERSION-all.jar" "$readme"
  NEW=$VERSION SHORT=${SOURCE:0:12} perl -pi -e 's/(release `\Q$ENV{NEW}\E` at `)[0-9a-f]{7,40}`/$1$ENV{SHORT}`/g' "$readme"
  [[ $(cksum < "$readme") == "$before" ]] || echo "  ok    $readme"
done

echo
"$ROOT/scripts/release/check-release.sh" "$VERSION" || true

echo
echo "To do by hand before the commit \"Add API reference for $VERSION\":"
todo "CHANGELOG.md: describe the release under '## [$VERSION] - $DATE' (one sentence before the link)"
todo "$REFERENCE: add the API changes since $PREVIOUS and check the quickstart and its expected output"
if [[ $VERSION != *-* ]]; then
  todo "$REFERENCE: $VERSION is a final release; reword the text about the release candidate"
fi
grep -n "release candidate\|Compared with\|Documentation revision\|release tag points to" "$REFERENCE" | sed 's/^/          /' || true
remaining=$(grep -n -F "$PREVIOUS" "${READMES[@]}" || true)
if [[ -n $remaining ]]; then
  todo "READMEs still name $PREVIOUS; keep it where the text means that release:"
  echo "$remaining" | sed 's/^/          /'
fi
todo "after the release is published: scripts/release/check-release.sh $VERSION --artifacts, then the website"

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
# Runs the tests of paniolo-mustang against several Mustang versions: the release the adapter is
# built for (property mustang.version of paniolo-mustang/pom.xml) and each further version given.
#
#   scripts/mustang-matrix.sh [VERSION...] [--mustang-checkout DIR]
#
# VERSION                 a Mustang library version in a Maven repository, e.g. 2.27.0
# --mustang-checkout DIR  builds the Mustang library of a local checkout (in a temporary copy, the
#                         checkout is not changed), installs it into the local Maven repository,
#                         and tests against its version, e.g. HEAD-SNAPSHOT
#
# The Paniolo modules must be installed first (mvn install). Exit code 0 if every version passes.

set -euo pipefail

ROOT=$(cd "$(dirname "$0")/.." && pwd)
VERSIONS=()
while [[ $# -gt 0 ]]; do
  case $1 in
    --mustang-checkout)
      [[ $# -ge 2 ]] || { echo "usage: $0 [VERSION...] [--mustang-checkout DIR]" >&2; exit 2; }
      CHECKOUT=$(cd "$2" && pwd)
      COPY=$(mktemp -d "${TMPDIR:-/tmp}/mustang-checkout.XXXXXX")
      rsync -a --exclude target --exclude .git "$CHECKOUT/" "$COPY/"
      VERSION=$(mvn -B -q -f "$COPY/library/pom.xml" help:evaluate -Dexpression=project.version -DforceStdout)
      echo "Installing Mustang $VERSION from $CHECKOUT"
      mvn -B -ntp -q -f "$COPY/pom.xml" install -pl library -am -DskipTests -Dmaven.javadoc.skip=true
      VERSIONS+=("$VERSION")
      shift
      ;;
    *) VERSIONS+=("$1") ;;
  esac
  shift
done

RELEASE=$(sed -n 's:.*<mustang.version>\(.*\)</mustang.version>.*:\1:p' "$ROOT/paniolo-mustang/pom.xml")
FAILED=()
for VERSION in "$RELEASE" "${VERSIONS[@]+"${VERSIONS[@]}"}"; do
  echo "=== Mustang $VERSION"
  LOG=$(mktemp "${TMPDIR:-/tmp}/mustang-matrix.XXXXXX")
  if mvn -B -ntp -f "$ROOT/pom.xml" test -pl paniolo-mustang -Dmustang.version="$VERSION" -DargLine=-Djava.awt.headless=true > "$LOG" 2>&1; then
    grep -E "Tests run: [0-9]+, Failures: [0-9]+, Errors: [0-9]+, Skipped: [0-9]+$" "$LOG" | tail -n 1
  else
    grep -E "Tests run:|FAIL|ERROR\]" "$LOG" | head -n 40
    echo "  log: $LOG"
    FAILED+=("$VERSION")
  fi
done

if [[ ${#FAILED[@]} -eq 0 ]]; then
  echo "All Mustang versions passed."
else
  echo "Failed for Mustang: ${FAILED[*]}"
  exit 1
fi

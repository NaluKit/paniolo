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

# Tests check-owasp-db-age.sh with generated H2 databases that carry the update metadata of
# dependency-check: old data stays old even with new files next to it, and missing, invalid or
# future metadata fails. H2_JAR selects the driver; without it the one from pom.xml is used.
set -euo pipefail

HERE=$(cd "$(dirname "$0")" && pwd)
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
NOW=2026-11-15T12:00:00Z
failed=0

if [[ -z ${H2_JAR:-} ]]; then
  ROOT=$(cd "$HERE/../.." && pwd)
  version=$(sed -n 's:.*<plugin.dependency-check.h2.version>\(.*\)</plugin.dependency-check.h2.version>.*:\1:p' "$ROOT/pom.xml")
  mvn -q -ntp -f "$ROOT/pom.xml" -N org.apache.maven.plugins:maven-dependency-plugin:3.8.1:copy \
    -Dartifact="com.h2database:h2:$version" -DoutputDirectory="$WORK/h2" >&2
  export H2_JAR="$WORK/h2/h2-$version.jar"
fi

# database <name> <last modified> <last checked>: an empty value leaves the property out
database() {
  local name=$1 modified=$2 checked=$3 sql
  mkdir -p "$WORK/$name"
  sql="CREATE TABLE properties (id varchar(50) PRIMARY KEY, \`value\` varchar(500));"
  [[ -n $modified ]] && sql+="INSERT INTO properties VALUES ('nvd.api.last.modified', '$modified');"
  [[ -n $checked ]] && sql+="INSERT INTO properties VALUES ('nvd.api.last.checked', '$checked');"
  java -cp "$H2_JAR" org.h2.tools.RunScript -url "jdbc:h2:file:$WORK/$name/odc" -user dcuser -password 'DC-Pass1337!' -script <(echo "$sql")
}

expect() {
  local expected=$1 name=$2
  if "$HERE/check-owasp-db-age.sh" "$WORK/$name" 14 "$NOW" > "$WORK/out.txt" 2>&1; then actual=0; else actual=1; fi
  if [[ $actual -ne $expected ]]; then
    echo "FAIL: $name (expected status $expected, got $actual)" >&2
    cat "$WORK/out.txt" >&2
    failed=1
  fi
}

database current 2026-11-14T03:00:00Z 2026-11-14T04:00:00Z
expect 0 current
grep -q "nvd.api.last.modified" "$WORK/out.txt" && grep -q "limit 14 days" "$WORK/out.txt" \
  || { echo "FAIL: the source, the time and the limit are not logged" >&2; failed=1; }
# epoch seconds of older versions: 2026-11-14 and 2025-11-14
database epoch-seconds 1794614400 1794614400
expect 0 epoch-seconds
database old-epoch-seconds 1763078400 1763078400
expect 1 old-epoch-seconds
database old 2026-08-17T03:00:00Z 2026-08-17T04:00:00Z
expect 1 old
# new files and file times do not make old data current
touch "$WORK/old/odc.mv.db"
echo "unrelated" > "$WORK/old/notes.txt"
touch "$WORK/old/odc.trace.db"
expect 1 old
# a recent update run that brought no new data is still old data
database old-but-checked 2026-08-17T03:00:00Z 2026-11-15T04:00:00Z
expect 1 old-but-checked
database no-modified "" 2026-11-14T04:00:00Z
expect 1 no-modified
database no-checked 2026-11-14T03:00:00Z ""
expect 1 no-checked
database invalid "yesterday" 2026-11-14T04:00:00Z
expect 1 invalid
database future 2026-12-24T00:00:00Z 2026-12-24T00:00:00Z
expect 1 future
mkdir -p "$WORK/empty"
expect 1 empty

if [[ $failed -ne 0 ]]; then
  exit 1
fi
echo "OWASP database age: all checks passed."

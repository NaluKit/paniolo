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

# Fails if the NVD data in a dependency-check data directory is older than the given number of
# days. The age comes from the update metadata in the database (OwaspDatabaseAge.java), not from
# file times. The H2 driver is the one of dependency-check-maven (plugin.dependency-check.h2.version
# in pom.xml); H2_JAR selects another driver JAR (used by the tests).
#   check-owasp-db-age.sh <data directory> <maximum age in days> [<now as ISO instant>]
set -euo pipefail

HERE=$(cd "$(dirname "$0")" && pwd)
ROOT=$(cd "$HERE/../.." && pwd)
if [[ $# -lt 2 || $# -gt 3 ]]; then
  echo "usage: $0 <data directory> <maximum age in days> [<now>]" >&2
  exit 2
fi
[[ $2 =~ ^[0-9]+$ ]] || { echo "Invalid maximum age: $2" >&2; exit 2; }

if [[ -z ${H2_JAR:-} ]]; then
  version=$(sed -n 's:.*<plugin.dependency-check.h2.version>\(.*\)</plugin.dependency-check.h2.version>.*:\1:p' "$ROOT/pom.xml")
  [[ -n $version ]] || { echo "No plugin.dependency-check.h2.version in $ROOT/pom.xml" >&2; exit 1; }
  dir=$(mktemp -d)
  trap 'rm -rf "$dir"' EXIT
  mvn -q -ntp -f "$ROOT/pom.xml" -N org.apache.maven.plugins:maven-dependency-plugin:3.8.1:copy \
    -Dartifact="com.h2database:h2:$version" -DoutputDirectory="$dir" -Dmdep.useBaseVersion=true >&2
  H2_JAR="$dir/h2-$version.jar"
fi
[[ -f $H2_JAR ]] || { echo "No H2 driver $H2_JAR" >&2; exit 1; }

java -cp "$H2_JAR" "$HERE/OwaspDatabaseAge.java" "$@"

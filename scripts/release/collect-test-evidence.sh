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

# Collects the test evidence of a release run into one archive that is attached to the GitHub
# release, so the results of Java 21 and 25, the invoice corpus and the browser tests can be
# checked without the short-lived logs of the workflow run:
#   paniolo-<version>-test-evidence.tar.gz   Surefire/Failsafe reports of every test job, the tool
#                                            versions of each job, the workflow and the POMs
#                                            (the test configuration) and evidence.json
#   paniolo-<version>-test-evidence.json     the summary alone (counts, skipped cases with reason,
#                                            commit, workflow run, SHA-256 of every archived file)
#   SHA256SUMS-test-evidence                 SHA-256 of the two files above
#
# The files are kept apart from SHA256SUMS on purpose: that manifest lists only what an independent
# rebuild reproduces, and test reports contain timestamps and durations.
#
#   scripts/release/collect-test-evidence.sh <version> <reports-directory> <output-directory>
#
# <reports-directory> holds one subdirectory per downloaded workflow artifact (the name of the
# artifact is the name of the test job), see the job maven-deploy in .github/workflows/build.yaml.
# The retention is the lifetime of the GitHub release: release assets are not touched by
# .github/workflows/delete-workflow-runs.yaml.
set -euo pipefail

if [[ $# -ne 3 ]]; then
  echo "usage: $0 <version> <reports-directory> <output-directory>" >&2
  exit 2
fi
VERSION=$1
IN=$2
OUT=$3
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
# the test jobs a release needs; a missing one fails the release
JOBS=(test-reports-jdk21 test-reports-jdk25 visualizer-browser-reports)

[[ -d $IN ]] || { echo "Missing reports directory $IN" >&2; exit 1; }
mkdir -p "$OUT"
IN=$(cd "$IN" && pwd)
OUT=$(cd "$OUT" && pwd)
NAME="paniolo-$VERSION-test-evidence"
STAGE=$(mktemp -d)
trap 'rm -rf "$STAGE"' EXIT
mkdir -p "$STAGE/$NAME/config"

jobs_json='[]'
for job in "${JOBS[@]}"; do
  dir="$IN/$job"
  [[ -d $dir ]] || { echo "Missing test reports of job $job in $IN" >&2; exit 1; }
  xmls=$(find "$dir" -name 'TEST-*.xml' -type f | LC_ALL=C sort)
  [[ -n $xmls ]] || { echo "No test result files (TEST-*.xml) for job $job" >&2; exit 1; }
  # sums of tests, failures, errors and skipped of all test suites (first <testsuite> line of each file)
  totals=$(echo "$xmls" | xargs grep -h -m1 '<testsuite ' | awk '
    function attr(name,   m) { if (match($0, " " name "=\"[0-9]+\"")) { m = substr($0, RSTART + length(name) + 3, RLENGTH - length(name) - 4); return m + 0 } return 0 }
    { t += attr("tests"); f += attr("failures"); e += attr("errors"); s += attr("skipped") }
    END { print t, f, e, s }')
  read -r tests failures errors skipped <<< "$totals"
  if [[ $tests -eq 0 || $failures -ne 0 || $errors -ne 0 ]]; then
    echo "Job $job is not a passing result: $tests tests, $failures failures, $errors errors" >&2
    exit 1
  fi
  # every skipped test case with the reason the report states; without one it is marked as such
  skipped_json=$(echo "$xmls" | xargs awk '
    /<testcase / { c = $0; sub(/.*classname="/, "", c); sub(/".*/, "", c); n = $0; sub(/.*[ ]name="/, "", n); sub(/".*/, "", n); case_ = c "." n }
    /<skipped/ { m = "no reason stated"; if (match($0, /message="[^"]*"/)) m = substr($0, RSTART + 9, RLENGTH - 10); printf "%s\t%s\n", case_, m }
  ' | jq -R -s 'split("\n") | map(select(length > 0) | split("\t") | {case: .[0], reason: .[1]})')
  mkdir -p "$STAGE/$NAME/$job"
  cp -R "$dir/." "$STAGE/$NAME/$job/"
  env_file=$(find "$dir" -name environment.txt -type f | head -n 1)
  jobs_json=$(jq --arg job "$job" --argjson tests "$tests" --argjson skipped "$skipped" \
      --argjson cases "$skipped_json" --arg env "${env_file:+yes}" \
      '. + [{job: $job, tests: $tests, failures: 0, errors: 0, skipped: $skipped, skippedCases: $cases, toolVersionsRecorded: ($env == "yes")}]' <<< "$jobs_json")
done

# the configuration of the checks: the workflow and every POM (plugins, profiles, versions)
cp "$ROOT/.github/workflows/build.yaml" "$STAGE/$NAME/config/"
(cd "$ROOT" && find . -name pom.xml -not -path '*/target/*' -not -path './.git/*' | LC_ALL=C sort |
  while read -r pom; do mkdir -p "$STAGE/$NAME/config/$(dirname "$pom")"; cp "$pom" "$STAGE/$NAME/config/$pom"; done)

commit=${GITHUB_SHA:-$(git -C "$ROOT" rev-parse HEAD)}
server=${GITHUB_SERVER_URL:-https://github.com}
repo=${GITHUB_REPOSITORY:-NaluKit/paniolo}
run_id=${GITHUB_RUN_ID:-unknown}
files_json=$(cd "$STAGE/$NAME" && find . -type f -not -name evidence.json | sed 's:^\./::' | LC_ALL=C sort | xargs shasum -a 256 |
  jq -R -s 'split("\n") | map(select(length > 0) | {sha256: .[0:64], path: .[66:]})')
jq -n --arg version "$VERSION" --arg commit "$commit" --arg ref "${GITHUB_REF:-unknown}" \
    --arg workflow "${GITHUB_WORKFLOW:-unknown}" --arg runId "$run_id" --arg attempt "${GITHUB_RUN_ATTEMPT:-unknown}" \
    --arg runUrl "$server/$repo/actions/runs/$run_id" --arg owasp "paniolo-$VERSION-dependency-check-report.html" \
    --argjson jobs "$jobs_json" --argjson files "$files_json" '{
      version: $version, commit: $commit, ref: $ref,
      workflow: {name: $workflow, runId: $runId, attempt: $attempt, url: $runUrl},
      retention: "as long as the GitHub release of this version exists",
      securityCheck: $owasp,
      testJobs: $jobs,
      files: $files
    }' > "$STAGE/$NAME/evidence.json"

tar -C "$STAGE" -czf "$OUT/$NAME.tar.gz" "$NAME"
cp "$STAGE/$NAME/evidence.json" "$OUT/$NAME.json"
(cd "$OUT" && shasum -a 256 "$NAME.tar.gz" "$NAME.json" > SHA256SUMS-test-evidence)

echo "Collected test evidence of version $VERSION in $OUT/$NAME.tar.gz"

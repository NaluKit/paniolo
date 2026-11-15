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

# Tests update-owasp-image.sh with a stubbed registry (curl) and a copy of a workflow: the real
# write path with --apply, the print-only mode and the error cases without partial changes.
set -euo pipefail

HERE=$(cd "$(dirname "$0")" && pwd)
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
OLD=sha256:1111111111111111111111111111111111111111111111111111111111111111
NEW=sha256:2222222222222222222222222222222222222222222222222222222222222222
IMAGE=nalusolutionsgmbh/owasp-maven-action
TODAY=$(date -u +%F)
failed=0

mkdir -p "$WORK/bin"
cat > "$WORK/bin/curl" <<'STUB'
#!/usr/bin/env bash
# token request or manifest HEAD request; the digest comes from STUB_DIGEST
for arg in "$@"; do
  case $arg in
    *auth.docker.io*) echo '{"token":"t"}'; exit 0 ;;
    *registry-1.docker.io*) printf 'HTTP/1.1 200 OK\r\ndocker-content-digest: %s\r\n\r\n' "$STUB_DIGEST"; exit 0 ;;
  esac
done
exit 22
STUB
chmod +x "$WORK/bin/curl"

workflow() {
  {
    echo "jobs:"
    echo "  owasp:"
    echo "    services:"
    echo "      owasp-db:"
    for _ in $(seq 1 "${1:-1}"); do
      echo "        image: $IMAGE@$OLD # :latest on 2026-01-01"
    done
    echo "        options: --entrypoint /bin/sh"
    echo "  other:"
    echo "    container:"
    echo "      image: other/image@$OLD # :latest on 2026-01-01"
  } > "$WORK/build.yaml"
  cp "$WORK/build.yaml" "$WORK/original.yaml"
}

# run <expected status> <name> <digest> [args]: runs the script with the stub
run() {
  local expected=$1 name=$2 digest=$3
  shift 3
  if PATH="$WORK/bin:$PATH" STUB_DIGEST=$digest OWASP_WORKFLOW="$WORK/build.yaml" "$HERE/update-owasp-image.sh" "$@" > "$WORK/out.txt" 2>&1; then actual=0; else actual=1; fi
  if [[ $actual -ne $expected ]]; then
    echo "FAIL: $name (expected status $expected, got $actual)" >&2
    cat "$WORK/out.txt" >&2
    failed=1
  fi
}

unchanged() {
  if ! cmp -s "$WORK/build.yaml" "$WORK/original.yaml"; then
    echo "FAIL: $1 changed the workflow" >&2
    failed=1
  fi
}

# --apply replaces exactly the OWASP image digest and its date
workflow
run 0 apply "$NEW" --apply
expected=$(sed "s|$IMAGE@$OLD # :latest on 2026-01-01|$IMAGE@$NEW # :latest on $TODAY|" "$WORK/original.yaml")
if [[ $(cat "$WORK/build.yaml") != "$expected" ]]; then
  echo "FAIL: apply did not write the expected workflow" >&2
  diff "$WORK/original.yaml" "$WORK/build.yaml" >&2 || true
  failed=1
fi
grep -q "other/image@$OLD" "$WORK/build.yaml" || { echo "FAIL: apply changed another image" >&2; failed=1; }

# without --apply only the digest is printed
workflow
run 0 print-only "$NEW"
unchanged print-only
grep -q "$NEW" "$WORK/out.txt" || { echo "FAIL: print-only did not print the digest" >&2; failed=1; }

# invalid or missing digests, missing or ambiguous target lines: errors without changes
workflow
run 1 missing-digest "" --apply
unchanged missing-digest
run 1 invalid-digest "sha256:xyz" --apply
unchanged invalid-digest
workflow 0
run 1 no-target-line "$NEW" --apply
unchanged no-target-line
workflow 2
run 1 ambiguous-target-line "$NEW" --apply
unchanged ambiguous-target-line
workflow
run 1 unknown-argument "$NEW" --force
unchanged unknown-argument

if [[ $failed -ne 0 ]]; then
  exit 1
fi
echo "OWASP image update: all checks passed."

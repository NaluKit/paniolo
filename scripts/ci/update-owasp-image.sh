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

# Prints the digest of the current :latest of the OWASP database image and, with --apply, writes
# it into .github/workflows/build.yaml. Review the diff and let the pull request run before
# merging. The workflow must contain exactly one image line of the form
#   image: <IMAGE>@sha256:<64 hex> # :latest on <yyyy-mm-dd>
# otherwise nothing is changed. OWASP_WORKFLOW selects another workflow file (used by the tests).
set -euo pipefail

IMAGE=nalusolutionsgmbh/owasp-maven-action
WORKFLOW=${OWASP_WORKFLOW:-$(cd "$(dirname "$0")/../.." && pwd)/.github/workflows/build.yaml}
LINE="^([[:space:]]*image:[[:space:]]+$IMAGE@)sha256:[0-9a-f]{64}( # :latest on )[0-9]{4}-[0-9]{2}-[0-9]{2}\$"

case ${1:-} in
  '' | --apply) ;;
  *) echo "usage: $0 [--apply]" >&2; exit 2 ;;
esac

token=$(curl -fsS "https://auth.docker.io/token?service=registry.docker.io&scope=repository:$IMAGE:pull" | sed -E 's/.*"token":"([^"]+)".*/\1/')
digest=$(curl -fsSI -H "Authorization: Bearer $token" \
  -H 'Accept: application/vnd.oci.image.index.v1+json,application/vnd.docker.distribution.manifest.list.v2+json,application/vnd.docker.distribution.manifest.v2+json' \
  "https://registry-1.docker.io/v2/$IMAGE/manifests/latest" | tr -d '\r' | awk -F': ' 'tolower($1) == "docker-content-digest" { print $2 }')
if [[ ! $digest =~ ^sha256:[0-9a-f]{64}$ ]]; then
  echo "Could not determine the digest of $IMAGE:latest" >&2
  exit 1
fi
echo "$IMAGE:latest is $digest"
if [[ ${1:-} != --apply ]]; then
  exit 0
fi

matches=$(grep -cE "$LINE" "$WORKFLOW" || true)
if [[ $matches -ne 1 ]]; then
  echo "Expected exactly one image line for $IMAGE in $WORKFLOW, found $matches; nothing changed" >&2
  exit 1
fi
today=$(date -u +%F)
updated=$(mktemp "$WORKFLOW.XXXXXX")
trap 'rm -f "$updated"' EXIT
# '|' as delimiter: it occurs neither in the image name, the digest nor the date
sed -E "s|$LINE|\\1$digest\\2$today|" "$WORKFLOW" > "$updated"
expected="$IMAGE@$digest # :latest on $today"
if [[ $(grep -cF "$expected" "$updated" || true) -ne 1 ]] || [[ $(diff "$WORKFLOW" "$updated" | grep -c '^[<>]' || true) -gt 2 ]]; then
  echo "The update of $WORKFLOW could not be verified; nothing changed" >&2
  exit 1
fi
cat "$updated" > "$WORKFLOW"
echo "Updated $WORKFLOW"

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

# Checks the link rewriting of collect-release-files.sh. It runs the function relink_to_tag taken
# from that script, not a copy.
set -euo pipefail

SCRIPT=$(cd "$(dirname "$0")" && pwd)/collect-release-files.sh
eval "$(sed -n '/^# BEGIN relink_to_tag/,/^# END relink_to_tag/p' "$SCRIPT")"

TAG=https://github.com/NaluKit/paniolo/blob/1.0.0
failed=0
check() {
  local input=$1 expected=$2 actual
  actual=$(printf '%s\n' "$input" | relink_to_tag 1.0.0)
  if [[ $actual != "$expected" ]]; then
    echo "FAIL: $input" >&2
    echo "  expected: $expected" >&2
    echo "  actual:   $actual" >&2
    failed=1
  fi
}

check '[a](docs/x.md)' "[a]($TAG/docs/x.md)"
check '[a](docs/x.md#section)' "[a]($TAG/docs/x.md#section)"
check '[a](../README.md)' "[a]($TAG/../README.md)"
check '[a](https://example.org/spec)' '[a](https://example.org/spec)'
check '[a](https://example.org/a:b)' '[a](https://example.org/a:b)'
check '[a](mailto:me@example.org)' '[a](mailto:me@example.org)'
check '[a](//example.org/spec)' '[a](//example.org/spec)'
check '[a](#anchor)' '[a](#anchor)'
check '[a](docs/x.md) and [b](https://example.org/y)' "[a]($TAG/docs/x.md) and [b](https://example.org/y)"

if [[ $failed -ne 0 ]]; then
  exit 1
fi
echo "Release note links: all checks passed."

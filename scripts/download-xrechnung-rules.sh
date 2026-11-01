#!/bin/sh
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

# Official, pinned KoSIT artifacts for repeatable integration tests.
set -eu
project_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
artifact=xrechnung-3.0.2-validator-configuration-2026-08-31
output_dir="$project_dir/target/$artifact"
archive="$project_dir/target/$artifact.zip"
mkdir -p "$project_dir/target"
curl -fL "https://github.com/itplr-kosit/validator-configuration-xrechnung/releases/download/v2026-08-31/$artifact.zip" -o "$archive"
printf '%s  %s\n' '2530cd107c414511c5d0462ec10f886910395abfca820db82e83d70bf01221a8' "$archive" | shasum -a 256 -c -
mkdir -p "$output_dir"
unzip -oq "$archive" -d "$output_dir"
printf '\nRules extracted to %s\n' "$output_dir"
printf 'Run: mvn verify -Dxrechnung.bundle="%s"\n' "$output_dir"

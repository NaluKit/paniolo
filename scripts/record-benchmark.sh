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

# Runs JMH benchmarks with allocation profiling and keeps the results together with the
# environment they were measured in, in paniolo-benchmark/results/<date>-<commit>-<label>/:
#   results.json     JMH results: score and allocation per operation, JVM, JVM options, forks,
#                    warmup and measurement settings, parameters
#   environment.txt  source commit and working tree state, Java, operating system, hardware,
#                    arguments
#   output.txt       the console output of JMH
#
# Usage: scripts/record-benchmark.sh <label> [--pdfa] [JMH arguments...]
#   scripts/record-benchmark.sh large-xml LargeXmlBenchmark
#   scripts/record-benchmark.sh pdfa --pdfa FacturXPdfaBenchmark -p lines=100
# --pdfa adds the jars of paniolo-benchmark/target/pdfa-lib for FacturXPdfaBenchmark.
#
# Build the jar first: mvn -Pbenchmark package -pl paniolo-benchmark -am -DskipTests
set -euo pipefail

if [[ $# -lt 1 ]]; then
  sed -n '20,34p' "$0" >&2
  exit 1
fi
label="$1"
shift
project_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
module="$project_dir/paniolo-benchmark"
classpath="$module/target/benchmarks.jar"
if [[ "${1:-}" == "--pdfa" ]]; then
  classpath="$classpath:$module/target/pdfa-lib/*"
  shift
fi
if [[ ! -f "$module/target/benchmarks.jar" ]]; then
  echo "Missing $module/target/benchmarks.jar; build it first" >&2
  exit 1
fi

out="$module/results/$(date +%Y-%m-%d)-$(git -C "$project_dir" rev-parse --short HEAD)-$label"
mkdir -p "$out"

{
  echo "label: $label"
  echo "date: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo "commit: $(git -C "$project_dir" rev-parse HEAD)"
  echo "commit subject: $(git -C "$project_dir" log -1 --format=%s)"
  if [[ -n "$(git -C "$project_dir" status --porcelain --untracked-files=no)" ]]; then
    echo "working tree: modified, the results do not belong to the commit alone"
  else
    echo "working tree: clean"
  fi
  echo "class path: $classpath"
  echo "arguments: $*"
  echo
  echo "java:"
  java -version 2>&1 | sed 's/^/  /'
  echo
  echo "os: $(uname -srm)"
  if sysctl -n machdep.cpu.brand_string >/dev/null 2>&1; then
    echo "cpu: $(sysctl -n machdep.cpu.brand_string)"
    echo "cores: $(sysctl -n hw.ncpu)"
    echo "memory: $(( $(sysctl -n hw.memsize) / 1024 / 1024 / 1024 )) GB"
  elif [[ -r /proc/cpuinfo ]]; then
    echo "cpu: $(grep -m1 'model name' /proc/cpuinfo | cut -d: -f2- | sed 's/^ //')"
    echo "cores: $(nproc)"
    echo "memory: $(awk '/MemTotal/ {printf "%d GB", $2 / 1024 / 1024}' /proc/meminfo)"
  fi
} > "$out/environment.txt"

java -cp "$classpath" org.openjdk.jmh.Main "$@" -prof gc -rf json -rff "$out/results.json" | tee "$out/output.txt"
echo "Results in $out"

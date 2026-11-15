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

# Packages the official Order-X rules for paniolo-rules.
#
# The Order-X release package of FNFE-MPE and FeRD holds the Schematron of each profile and a
# stylesheet compiled with oXygen, which reports with xsl:message instead of SVRL. Paniolo needs
# SVRL, so this script compiles the unmodified .sch with the ISO Schematron skeleton
# (scripts/iso-schematron, MIT License) and Saxon-HE, and packs the official XSDs, Schematrons,
# terms of use, and the compiled stylesheets into a reproducible archive.
#
# Usage: scripts/package-orderx-rules.sh /path/to/extracted/Order-X100_EN
set -eu
project_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
package_dir=${1:?"path to the extracted Order-X package (the folder with 04-XSD-UML-SCHEMATRON)"}
saxon_version=$(sed -n 's:.*<saxon.version>\(.*\)</saxon.version>.*:\1:p' "$project_dir/pom.xml")
repository=${MAVEN_REPOSITORY:-$HOME/.m2/repository}
resolver=$(ls -d "$repository"/org/xmlresolver/xmlresolver/* | sort -V | tail -1)
classpath="$repository/net/sf/saxon/Saxon-HE/$saxon_version/Saxon-HE-$saxon_version.jar:$resolver/xmlresolver-$(basename "$resolver").jar:$resolver/xmlresolver-$(basename "$resolver")-data.jar"
iso="$project_dir/scripts/iso-schematron"
archive="$project_dir/paniolo-rules/src/main/resources/io/github/nalukit/paniolo/rules/facturx/order-x-1.0-2021-04-13.zip"
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

transform() {
  java -cp "$classpath" net.sf.saxon.Transform -s:"$1" -xsl:"$2" -o:"$3"
}

for level in Basic Comfort Extended; do
  upper=$(printf '%s' "$level" | tr '[:lower:]' '[:upper:]')
  source="$package_dir/04-XSD-UML-SCHEMATRON/$level"
  target="$work/package/$level"
  mkdir -p "$target/XSD" "$target/Schematron"
  cp "$source"/XSD/*.xsd "$target/XSD/"
  cp "$source/Schematron/SCRDMCCBDACIOMessageStructure_100pD20B_$upper.sch" "$target/Schematron/"
  transform "$source/Schematron/SCRDMCCBDACIOMessageStructure_100pD20B_$upper.sch" "$iso/iso_dsdl_include.xsl" "$work/$upper.1.sch"
  transform "$work/$upper.1.sch" "$iso/iso_abstract_expand.xsl" "$work/$upper.2.sch"
  transform "$work/$upper.2.sch" "$iso/iso_svrl_for_xslt2.xsl" "$target/Schematron/SCRDMCCBDACIOMessageStructure_100pD20B_$upper.xslt"
done
cp "$package_dir/Nutzungsrechte.txt" "$package_dir/Disclaimer.txt" "$work/package/"
mkdir -p "$work/package/licenses"
cp "$project_dir/paniolo-rules/src/main/resources/META-INF/licenses/MIT-ISO-Schematron.txt" "$work/package/licenses/"

python3 -I - "$work/package" "$archive" <<'EOF'
import hashlib, sys, zipfile
from pathlib import Path
source, archive = Path(sys.argv[1]), Path(sys.argv[2])
files = sorted(p for p in source.rglob("*") if p.is_file())
sums = []
with zipfile.ZipFile(archive, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as zip:
    def add(name, data):
        info = zipfile.ZipInfo(name, date_time=(2021, 4, 13, 0, 0, 0))
        info.compress_type = zipfile.ZIP_DEFLATED
        info.external_attr = 0o644 << 16
        zip.writestr(info, data)
    for path in files:
        name = path.relative_to(source).as_posix()
        data = path.read_bytes()
        # Retain the skeleton license for the templates embedded in generated XSLT.
        origin = "license" if name.startswith("licenses/") else "compiled" if name.endswith(".xslt") else "official"
        sums.append(f"{hashlib.sha256(data).hexdigest()}  {name}  {origin}")
        add(name, data)
    add("SHA256SUMS", "\n".join(sums) + "\n")
EOF
shasum -a 256 "$archive"
printf 'Update orderx.sha256 in paniolo-rules/pom.xml with the checksum above.\n'

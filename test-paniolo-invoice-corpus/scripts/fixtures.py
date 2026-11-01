#!/usr/bin/env python3
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

"""Verify vendored public fixtures; --restore fetches only missing or changed pinned bytes."""
import argparse
import csv
import hashlib
import json
from pathlib import Path
from urllib.parse import quote, urlparse
from urllib.request import urlopen

ROOT = Path(__file__).resolve().parents[1] / 'src/test/resources/corpus'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--restore', action='store_true', help='restore missing/changed originals from pinned URLs')
    args = parser.parse_args()
    with (ROOT / 'manifest.tsv').open() as handle:
        entries = list(csv.DictReader(handle, delimiter='\t'))
    entries += json.loads((ROOT / 'licenses.json').read_text())
    failures = []
    restored = 0
    for entry in entries:
        path = (ROOT / entry['file']).resolve()
        if not path.is_relative_to(ROOT.resolve()):
            raise ValueError('Path outside corpus: ' + entry['file'])
        digest = lambda data: hashlib.sha256(data).hexdigest()
        if path.is_file() and digest(path.read_bytes()) == entry['sha256']:
            continue
        if not args.restore:
            failures.append(entry['file'])
            continue
        parsed = urlparse(entry['source'])
        if parsed.scheme != 'https' or parsed.netloc != 'raw.githubusercontent.com':
            raise ValueError('Unexpected source host: ' + entry['source'])
        with urlopen(quote(entry['source'], safe=':/'), timeout=60) as response:
            data = response.read()
        if digest(data) != entry['sha256']:
            raise ValueError('Downloaded bytes differ from pinned SHA-256: ' + entry['file'])
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data)
        restored += 1
    if failures:
        raise SystemExit('Missing/changed fixtures:\n' + '\n'.join(failures))
    print(f'Verified {len(entries)} files; restored {restored}. Expectations unchanged.')


if __name__ == '__main__':
    main()

"""Fetch Go module ZIPs at versions embedded in the AAR and verify their h1 sums."""
import base64
import csv
import hashlib
import io
import json
from pathlib import Path
import sys
import urllib.request
import zipfile

if len(sys.argv) != 3:
    raise SystemExit('Usage: python fetch-module-sources.py build-info output-directory')
records = {}
for path in Path(sys.argv[1]).glob('*-go-modules.txt'):
    for line in path.read_text(encoding='utf-8').splitlines():
        fields = line.split('\t')
        if fields[0] != 'dep' or len(fields) < 4 or not fields[3].startswith('h1:'):
            continue
        key = (fields[1], fields[2])
        if key in records and records[key] != fields[3]:
            raise SystemExit('Conflicting sums for ' + repr(key))
        records[key] = fields[3]
if not records:
    raise SystemExit('No versioned modules with checksums found')
out = Path(sys.argv[2]); out.mkdir(parents=True, exist_ok=True)
rows = []
for (module, version), expected in sorted(records.items()):
    escape = lambda s: ''.join('!' + c.lower() if c.isupper() else c for c in s)
    url = f'https://proxy.golang.org/{escape(module)}/@v/{escape(version)}.zip'
    target = out / (hashlib.sha256((module + '@' + version).encode()).hexdigest() + '.zip')
    raw = target.read_bytes() if target.exists() else urllib.request.urlopen(url, timeout=120).read()
    with zipfile.ZipFile(io.BytesIO(raw)) as z:
        names = sorted(n for n in z.namelist() if not n.endswith('/'))
        listing = ''.join(hashlib.sha256(z.read(n)).hexdigest() + '  ' + n + '\n' for n in names).encode()
        actual = 'h1:' + base64.b64encode(hashlib.sha256(listing).digest()).decode()
        if actual != expected:
            raise SystemExit(f'Checksum mismatch for {module}@{version}: {actual} != {expected}')
        licenses = [n for n in names if n.rsplit('/', 1)[-1].lower().startswith(('license', 'licence', 'copying', 'notice', 'copyright'))]
    target.write_bytes(raw)
    rows.append({'module': module, 'version': version, 'h1': actual, 'archive': target.name, 'sha256_zip': hashlib.sha256(raw).hexdigest(), 'license_candidates': licenses, 'license_review': 'NOT REVIEWED'})
    print(module, version, flush=True)
(out / 'inventory.json').write_text(json.dumps(rows, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')
print('Verified', len(rows), 'module archives; legal classification is still required.')

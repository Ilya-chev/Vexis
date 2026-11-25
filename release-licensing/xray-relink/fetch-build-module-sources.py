"""Fetch wrapper go.mod requirements absent from the binary module inventory."""
import base64
import hashlib
import io
import json
from pathlib import Path
import re
import urllib.request
import zipfile

base = Path(__file__).resolve().parent
wrapper = next(base.glob('AndroidLibXrayLite-b5a377087e74eaa34e86718710b04c50f4b767f5.zip'))
with zipfile.ZipFile(wrapper) as archive:
    mod = archive.read('go.mod').decode('utf-8')
    sums = archive.read('go.sum').decode('utf-8')
requirements = re.findall(r'^\s*([^\s()]+)\s+(v[^\s]+)', mod, re.MULTILINE)
embedded = {(x['module'], x['version']) for x in json.loads((base / 'module-sources/inventory.json').read_text(encoding='utf-8'))}
checksums = {(p[0], p[1]): p[2] for line in sums.splitlines() if len(p := line.split()) == 3 and not p[1].endswith('/go.mod')}
out = base / 'build-module-sources'
out.mkdir(exist_ok=True)
items = []
for module, version in sorted(set(requirements) - embedded):
    expected = checksums[(module, version)]
    escape = lambda s: ''.join('!' + c.lower() if c.isupper() else c for c in s)
    raw = urllib.request.urlopen(f'https://proxy.golang.org/{escape(module)}/@v/{escape(version)}.zip', timeout=120).read()
    with zipfile.ZipFile(io.BytesIO(raw)) as archive:
        names = sorted(n for n in archive.namelist() if not n.endswith('/'))
        listing = ''.join(hashlib.sha256(archive.read(n)).hexdigest() + '  ' + n + '\n' for n in names).encode()
    actual = 'h1:' + base64.b64encode(hashlib.sha256(listing).digest()).decode()
    if actual != expected:
        raise SystemExit(f'Checksum mismatch for {module}@{version}')
    name = hashlib.sha256((module + '@' + version).encode()).hexdigest() + '.zip'
    (out / name).write_bytes(raw)
    items.append({'module': module, 'version': version, 'h1': actual, 'archive': name, 'sha256_zip': hashlib.sha256(raw).hexdigest()})
    print(module, version, flush=True)
(out / 'inventory.json').write_text(json.dumps(items, indent=2) + '\n', encoding='utf-8')

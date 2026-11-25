"""Create a candidate redistributable Xray source package with a SHA-256 manifest."""
import hashlib
import json
from pathlib import Path
import sys
import zipfile

if len(sys.argv) != 2:
    raise SystemExit('Usage: python create-source-package.py <new-output.zip>')
base = Path(__file__).resolve().parent
output = Path(sys.argv[1]).resolve()
if output.exists():
    raise SystemExit('Output already exists')
wrapper = 'AndroidLibXrayLite-b5a377087e74eaa34e86718710b04c50f4b767f5.zip'
files = [base / name for name in (wrapper, 'go1.27.3.src.tar.gz', 'GPL-3.0.txt', 'LGPL-3.0.txt',
         'SOURCES.md', 'README.md', 'rebuild-xray.sh', 'extract-build-info.py', 'fetch-module-sources.py', 'export-notices.py')]
files += sorted((base / 'build-info').glob('*-go-modules.txt'))
inventory_file = base / 'module-sources/inventory.json'
inventory = json.loads(inventory_file.read_text(encoding='utf-8'))
if len(inventory) != 46:
    raise SystemExit('Expected 46 verified module records')
files.append(inventory_file)
for item in inventory:
    path = base / 'module-sources' / item['archive']
    if hashlib.sha256(path.read_bytes()).hexdigest() != item['sha256_zip']:
        raise SystemExit('Archive SHA-256 mismatch: ' + str(path))
    files.append(path)
with zipfile.ZipFile(base / wrapper) as z:
    if any(n.lower().endswith('.dat') for n in z.namelist()):
        raise SystemExit('Unexpected .dat file in wrapper source archive')
expected = {wrapper: '003f947ed157e8b6972bc160ac7771035fa88b9c129a55a3fbe3991fe6810918',
            'go1.27.3.src.tar.gz': '6e7749035cf360f06b2575d162ea79df9fda8ef671889e5c375f8e50777f26bb'}
for name, digest in expected.items():
    if hashlib.sha256((base / name).read_bytes()).hexdigest() != digest:
        raise SystemExit('Source archive SHA-256 mismatch: ' + name)
output.parent.mkdir(parents=True, exist_ok=True)
manifest = []
with zipfile.ZipFile(output, 'x', allowZip64=True) as result:
    for file in files:
        relative = file.relative_to(base).as_posix()
        result.write(file, relative, compress_type=zipfile.ZIP_STORED if file.suffix in ('.zip', '.gz') else zipfile.ZIP_DEFLATED)
        manifest.append(hashlib.sha256(file.read_bytes()).hexdigest() + '  ' + relative)
    result.writestr('SHA256SUMS.txt', '\n'.join(manifest) + '\n')
print('Created', output, 'with', len(files), 'files')

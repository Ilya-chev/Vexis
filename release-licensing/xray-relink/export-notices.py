"""Copy identified legal files from verified module ZIPs into a notice bundle."""
import json
from pathlib import Path
import sys
import zipfile

if len(sys.argv) != 3:
    raise SystemExit('Usage: python export-notices.py module-sources output-file')
source = Path(sys.argv[1])
items = json.loads((source / 'inventory.json').read_text(encoding='utf-8'))
result = ['Xray Go dependencies: identified license and notice files',
          'These files do not constitute a complete legal review.', '']
missing = []
for item in items:
    with zipfile.ZipFile(source / item['archive']) as archive:
        prefix = item['module'] + '@' + item['version'] + '/'
        candidates = [n for n in archive.namelist() if n.startswith(prefix) and
                      n.rsplit('/', 1)[-1].upper().startswith(('LICENSE', 'LICENCE', 'COPYING', 'NOTICE', 'PATENTS', 'COPYRIGHT'))]
        if not any('/' not in n[len(prefix):] for n in candidates):
            missing.append(item['module'] + '@' + item['version'])
        for name in sorted(candidates):
            data = archive.read(name)
            try:
                body = data.decode('utf-8')
            except UnicodeDecodeError:
                raise SystemExit('Non-UTF-8 notice needs manual review: ' + name)
            result.extend(['=' * 72, item['module'] + '@' + item['version'], name, '=' * 72, body, ''])
target = Path(sys.argv[2]); target.parent.mkdir(parents=True, exist_ok=True)
target.write_text('\n'.join(result), encoding='utf-8')
print('No root notice found:', ', '.join(missing) if missing else 'none')

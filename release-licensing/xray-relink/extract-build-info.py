"""Extract Go module records embedded in the original AAR, without executing it."""
import re
import sys
import zipfile
from pathlib import Path

if len(sys.argv) != 3:
    raise SystemExit('Usage: python extract-build-info.py libv2ray.aar output-directory')
output = Path(sys.argv[2])
output.mkdir(parents=True, exist_ok=True)
with zipfile.ZipFile(sys.argv[1]) as aar:
    for name in sorted(n for n in aar.namelist() if n.startswith('jni/') and n.endswith('/libgojni.so')):
        data = aar.read(name)
        records = re.findall(rb'(?m)^(?:path|mod|dep|=>|build)\t[^\x00\r\n]+', data)
        if not records or not any(b'github.com/xtls/xray-core\t' in r for r in records):
            raise SystemExit('Could not extract Go records from ' + name)
        target = output / (name.split('/')[1] + '-go-modules.txt')
        target.write_bytes(b'\n'.join(records) + b'\n')
        print(target, len(records), 'records')

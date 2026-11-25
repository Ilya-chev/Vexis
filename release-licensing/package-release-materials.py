"""Package the release's third-party materials from the audited local inputs."""

from hashlib import file_digest
from pathlib import Path
import sys
from zipfile import ZIP_DEFLATED, ZipFile


root = Path(__file__).resolve().parent.parent
audit = root / 'release-licensing'
dependencies = audit / 'dependencies'
xray = audit / 'xray-relink'


def digest(path):
    with path.open('rb') as data:
        return file_digest(data, 'sha256').hexdigest()


def write_archive(destination, entries):
    if any(not source.is_file() for source, _ in entries):
        missing = [str(source) for source, _ in entries if not source.is_file()]
        raise FileNotFoundError('\n'.join(missing))
    with ZipFile(destination, 'x', compression=ZIP_DEFLATED, compresslevel=3) as archive:
        for source, name in entries:
            archive.write(source, name)
        checksums = ''.join(f'{digest(source)}  {name}\n' for source, name in entries)
        archive.writestr('SHA256SUMS.txt', checksums)


def release_material_entries():
    notices = root / 'app/src/main/assets/licenses'
    entries = [(p, p.relative_to(notices.parent).as_posix())
               for p in sorted(notices.rglob('*')) if p.is_file()]
    entries.extend([
        (root / 'LICENSE', 'LICENSE'),
        (root / 'THIRD-PARTY.md', 'THIRD-PARTY.md'),
        (audit / 'RELEASE-MATERIALS.md', 'README.md'),
        (audit / 'GEO.md', 'release-licensing/GEO.md'),
        (audit / 'XRAY.md', 'release-licensing/XRAY.md'),
        (xray / 'README.md', 'release-licensing/xray-relink/README.md'),
        (xray / 'SOURCES.md', 'release-licensing/xray-relink/SOURCES.md'),
        (xray / 'Vexis-Xray-sources-candidate.zip', 'sources/Vexis-Xray-sources.zip'),
        (audit / 'go-toolchains/go1.24.4.src.tar.gz', 'sources/awg/go1.24.4.src.tar.gz'),
        (dependencies / 'desugar-candidate-73170c3.zip', 'sources/desugar/desugar-73170c3.zip'),
    ])
    for name in ('README.md', 'AWG-REBUILD.md', 'DESUGAR-REBUILD.md', 'CheckDesugar.java',
                 'AWG-arm64-go-build-info.txt',
                 'go-modules-manifest.txt', 'desugar-release-l8-classes.txt',
                 'Desugar-2.1.5-candidate-ADDITIONAL-LICENSE-INFO.txt'):
        entries.append((dependencies / name, f'release-licensing/dependencies/{name}'))
    for name in ('amneziawg-android-v2.3.7-source.zip', 'amneziawg-tools-source.zip',
                 'termux-elf-cleaner-source.zip', 'awg-go.zip', 'awg-wireproxy.zip',
                 'wgtunnel-go-socks5-source.zip'):
        entries.append((dependencies / name, f'sources/awg/{name}'))
    for name in ('btree', 'crypto', 'dns', 'gvisor', 'heredoc', 'ini', 'net', 'sys', 'time'):
        entries.append((dependencies / 'go-modules' / f'{name}.zip', f'sources/awg/modules/{name}.zip'))
    return entries


if __name__ == '__main__':
    if len(sys.argv) != 2:
        raise SystemExit('Usage: python package-release-materials.py <new-output.zip>')
    destination = Path(sys.argv[1])
    write_archive(destination, release_material_entries())
    print(f'{digest(destination)}  {destination}')

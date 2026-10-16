#!/usr/bin/env python3
"""Import every top-level core .res file from the manifest-pinned ICU checkout."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('upstream', type=Path, help='Checkout of unicode-org/icu at the pinned commit')
parser.add_argument('--check', action='store_true', help='Verify the complete inventory without writing')
args = parser.parse_args()
root = Path(__file__).resolve().parent.parent
manifest_path = root / 'data/manifest.json'
manifest = json.loads(manifest_path.read_text())
upstream = args.upstream.resolve()
revision = subprocess.check_output(['git', '-C', str(upstream), 'rev-parse', 'HEAD'], text=True).strip()
if revision != manifest['commit']:
    raise SystemExit(f'Expected pinned ICU commit {manifest["commit"]}, got {revision}')
source = upstream / manifest['source_directory']
entries = {item['name']: item for item in manifest['resources']}
objects = {item['object'] for item in manifest['resources']}
count = 0
for path in sorted(source.glob('*.res')):
    raw = path.read_bytes()
    digest = hashlib.sha256(raw).hexdigest()
    item = entries.get(path.name)
    if item is None:
        if args.check:
            raise SystemExit(f'Missing core resource: {path.name}')
        object_name = 'CoreLocale_' + re.sub(r'[^A-Za-z0-9_]', '_', path.stem) + '_ResourceData'
        if object_name in objects:
            raise SystemExit(f'Generated object collision: {object_name}')
        objects.add(object_name)
        item = {'name': path.name, 'file': f'data/icu78/{path.name}', 'object': object_name,
                'sha256': digest, 'size': len(raw), 'source_path': str(path.relative_to(upstream))}
        manifest['resources'].append(item)
        (root / item['file']).write_bytes(raw)
        count += 1
    if item['size'] != len(raw) or item['sha256'] != digest or (root / item['file']).read_bytes() != raw:
        raise SystemExit(f'Core resource differs from pinned upstream: {path.name}')
if args.check:
    if manifest.get('registries', {}).get('') != 'CoreResourceRegistry':
        raise SystemExit('Missing root-directory CoreResourceRegistry')
else:
    manifest.setdefault('registries', {})[''] = 'CoreResourceRegistry'
    manifest_path.write_text(json.dumps(manifest, indent=2, ensure_ascii=False) + '\n')
print(f'Checked {len(list(source.glob("*.res")))} core resource files; imported {count}')

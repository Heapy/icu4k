#!/usr/bin/env python3
"""Compile the two pinned default ICU LSTM models to ResB without native genrb."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import struct

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--check', action='store_true')
args = parser.parse_args()
root = Path(__file__).resolve().parent.parent
sources = {
    'Thai_graphclust_model4_heavy': 'b6cb255efe368f6d6922471ad2e487450b15ca5308de83f4bcac36bf811c8eaf',
    'Burmese_graphclust_model5_heavy': '8dcecb3ab8bc3ba045441c2dbf1506ff758a5ceeed90bd2c9267a8839adf85ac',
}
for name, checksum in sources.items():
    raw = (root / f'data/icu78/brkitr/lstm/{name}.txt').read_bytes()
    if hashlib.sha256(raw).hexdigest() != checksum:
        raise SystemExit(f'Pinned LSTM source checksum mismatch: {name}')
    values = {}
    for key, kind, contents in re.findall(r'(\w+)(?::(intvector|int))?\s*\{([^{}]*)\}', raw.decode('utf-8-sig'), re.S):
        if key in values:
            raise SystemExit(f'Duplicate LSTM key: {key}')
        if kind:
            parsed = [int(v) for v in re.findall(r'-?\d+', contents)]
            remainder = re.sub(r'-?\d+', '', contents)
            if kind == 'int':
                if len(parsed) != 1:
                    raise SystemExit(f'Invalid scalar: {key}')
                parsed = parsed[0]
        else:
            literals = re.findall(r'"(?:\\.|[^"\\])*"', contents)
            parsed = [json.loads(v) for v in literals]
            remainder = re.sub(r'"(?:\\.|[^"\\])*"', '', contents)
            if key in ('model', 'type'):
                if len(parsed) != 1:
                    raise SystemExit(f'Invalid string: {key}')
                parsed = parsed[0]
        if remainder.strip(' \t\r\n,'):
            raise SystemExit(f'Unexpected LSTM syntax: {key}')
        values[key] = parsed
    if set(values) != {'model', 'type', 'dict', 'data', 'embeddings', 'hunits'}:
        raise SystemExit(f'Unexpected model keys: {name}')
    e, h, n = values['embeddings'], values['hunits'], len(values['dict'])
    expected = (n + 1) * e + 2 * (e * 4 * h + h * 4 * h + 4 * h) + 2 * h * 4 + 4
    if len(values['data']) != expected:
        raise SystemExit(f'Matrix size mismatch: {name}')
    body = bytearray(36)
    offsets = {}
    for key in sorted(values):
        offsets[key] = len(body)
        body.extend(key.encode('ascii') + b'\0')
    def align():
        while len(body) % 4:
            body.append(0)
    align()
    keys_top = len(body) // 4
    def words(items):
        body.extend(struct.pack('>' + 'I' * len(items), *(v & 0xffffffff for v in items)))
    def string(value):
        offset = len(body) // 4
        encoded = value.encode('utf-16-be')
        words([len(encoded) // 2])
        body.extend(encoded + b'\0\0')
        align()
        return offset
    resources = {}
    for key in sorted(values):
        value = values[key]
        if isinstance(value, str):
            resources[key] = string(value)
        elif isinstance(value, int):
            resources[key] = 0x70000000 | (value & 0xfffffff)
        elif key == 'dict':
            strings = [string(v) for v in value]
            resources[key] = 0x80000000 | (len(body) // 4)
            words([len(strings)] + strings)
        else:
            resources[key] = 0xe0000000 | (len(body) // 4)
            words([len(value)] + value)
    root_resource = 0x40000000 | (len(body) // 4)
    words([len(values)] + [offsets[k] for k in sorted(values)] + [resources[k] for k in sorted(values)])
    top = len(body) // 4
    struct.pack_into('>9I', body, 0, root_resource, 8, keys_top, top, top, len(values), 1, keys_top, 0)
    header = bytearray(32)
    struct.pack_into('>HBBHHBBBB', header, 0, 32, 0xda, 0x27, 20, 0, 1, 0, 2, 0)
    header[12:16] = b'ResB'
    header[16:20] = bytes([2, 0, 0, 0])
    header[20:24] = bytes([78, 3, 0, 0])
    result = header + body
    for output in [root / f'data/icu78/brkitr/{name}.res',
                   root / f'src/jvmTest/resources/com/ibm/icu/impl/data/icudata/brkitr/{name}.res']:
        if args.check:
            if not output.exists() or output.read_bytes() != result:
                raise SystemExit(f'Generated LSTM model is stale: {output.relative_to(root)}')
        else:
            output.parent.mkdir(parents=True, exist_ok=True)
            output.write_bytes(result)
    print(f"{'Checked' if args.check else 'Generated'} {name}: {n} tokens, {len(result)} bytes")

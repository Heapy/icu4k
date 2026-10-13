#!/usr/bin/env python3
"""Compile the pinned ICU Japanese AdaBoost model to portable ResB (no native genrb)."""
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
source = root / 'data/icu78/brkitr/adaboost/jaml.txt'
raw = source.read_bytes()
if hashlib.sha256(raw).hexdigest() != '845816d923c9b376a91c7eaf18d8e5f7c64903f30b4c62f5292f7c0db5d1e779':
    raise SystemExit('Pinned jaml.txt checksum mismatch')
text = raw.decode('utf-8')
blocks = re.findall(r'(\w+)(?::intvector)?\s*\{([^{}]*)\}', text, re.S)
features = [f'UW{i}' for i in range(1, 7)] + [f'BW{i}' for i in range(1, 4)] + [f'TW{i}' for i in range(1, 5)]
model = {}
for name, body in blocks:
    if name in model:
        raise SystemExit(f'Duplicate model section: {name}')
    if name.endswith('Keys'):
        literals = re.findall(r'"(?:\\.|[^"\\])*"', body)
        model[name] = [json.loads(value) for value in literals]
        remainder = re.sub(r'"(?:\\.|[^"\\])*"', '', body)
    else:
        model[name] = [int(value) for value in re.findall(r'-?\d+', body)]
        remainder = re.sub(r'-?\d+', '', body)
    if remainder.strip(' \t\r\n,'):
        raise SystemExit(f'Unexpected model syntax: {name}')
if set(model) != {feature + suffix for feature in features for suffix in ('Keys', 'Values')}:
    raise SystemExit('Unexpected model sections')
for feature in features:
    if len(model[feature + 'Keys']) != len(model[feature + 'Values']):
        raise SystemExit(f'Key/value count mismatch: {feature}')

# Resource format 2, legacy UTF-16 strings, array32 and table32. All offsets are
# relative to the data after the ICU header, in 32-bit words except key offsets.
body = bytearray(36)  # root + eight indexes
key_offsets = {}
for key in sorted(model):
    key_offsets[key] = len(body)
    body.extend(key.encode('ascii') + b'\0')
def align():
    while len(body) % 4:
        body.append(0)
align()
keys_top = len(body) // 4

def integers(values):
    body.extend(struct.pack('>' + 'i' * len(values), *values))

def string(value):
    align()
    offset = len(body) // 4
    encoded = value.encode('utf-16-be')
    integers([len(encoded) // 2])
    body.extend(encoded + b'\0\0')
    align()
    return offset

resources = {}
for key in sorted(model):
    if key.endswith('Keys'):
        handles = [string(value) for value in model[key]]
        offset = len(body) // 4
        integers([len(handles)] + handles)
        resources[key] = 0x80000000 | offset
    else:
        offset = len(body) // 4
        integers([len(model[key])] + model[key])
        resources[key] = 0xe0000000 | offset
root_offset = len(body) // 4
# Handles are unsigned words; unlike model scores they may have their high bit set.
words = [len(model)] + [key_offsets[key] for key in sorted(model)] + [resources[key] for key in sorted(model)]
body.extend(struct.pack('>' + 'I' * len(words), *words))
bundle_top = len(body) // 4
struct.pack_into('>9I', body, 0, 0x40000000 | root_offset, 8, keys_top, bundle_top,
                 bundle_top, len(model), 1, keys_top, 0)
header = bytearray(32)
struct.pack_into('>HBBHHBBBB', header, 0, 32, 0xda, 0x27, 20, 0, 1, 0, 2, 0)
header[12:16] = b'ResB'
header[16:20] = bytes([2, 0, 0, 0])
header[20:24] = bytes([78, 3, 0, 0])
result = header + body
outputs = [root / 'data/icu78/brkitr/jaml.res',
           root / 'src/jvmTest/resources/com/ibm/icu/impl/data/icudata/brkitr/jaml.res']
for output in outputs:
    if args.check:
        if not output.exists() or output.read_bytes() != result:
            raise SystemExit(f'Generated model is stale: {output.relative_to(root)}')
    else:
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_bytes(result)
print(f"{'Checked' if args.check else 'Generated'} Japanese model: {sum(len(model[f + 'Keys']) for f in features)} features, {len(result)} bytes")

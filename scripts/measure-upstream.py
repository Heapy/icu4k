#!/usr/bin/env python3
"""Count ICU4J production/test Java using cloc (install separately)."""
import json
from pathlib import Path
import subprocess
import sys

root = Path(sys.argv[1]).resolve()
commit = subprocess.check_output(["git", "-C", str(root), "rev-parse", "HEAD"], text=True).strip()
report = {"upstream_commit": commit, "groups": {}}
for name, pattern in [("production", "main/*/src/main/java"), ("tests", "main/*/src/test/java")]:
    paths = sorted((root / "icu4j").glob(pattern))
    if not paths:
        raise SystemExit(f"No source directories for {name}: {root}")
    result = json.loads(subprocess.check_output(
        ["cloc", "--quiet", "--skip-uniqueness", "--json", *map(str, paths)], text=True))
    report["groups"][name] = {"paths": [str(p.relative_to(root)) for p in paths], "counts": result}
print(json.dumps(report, indent=2))

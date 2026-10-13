# Porting scope

Baseline: unicode-org/icu release-78.3,
commit `21d1eb0f306e1141c10931e914dfc038c06121da`.
Measured with cloc 2.10, counting every file (`--skip-uniqueness`).

| Scope | Language | Files | Code | Comments | Blank | Total lines |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| main/*/src/main/java | Java | 632 | 168,427 | 122,255 | 27,551 | 318,233 |
| main/*/src/test/java | Java | 356 | 163,115 | 25,488 | 21,065 | 209,668 |
| Combined | Java | 988 | 331,542 | 147,743 | 48,616 | 527,901 |

Production breakdown (code lines):

| Module | Java code |
| --- | ---: |
| core | 133,985 |
| collate | 14,889 |
| charset | 13,902 |
| translit | 5,007 |
| currdata | 632 |
| langdata | 6 |
| regiondata | 6 |

These are source-size measurements, not a prediction of Kotlin output size or
an assertion that every line needs manual rewriting. Generated Java tables may
be regenerated. Test helpers will need adaptation. Demos, samples, tools,
performance tests, resources and build files are excluded from these counts.
Unicode/CLDR resources must be retained with a common resource reader or
converted by a reproducible generator, not manually translated into Kotlin.
No ICU4C C/C++ port is necessary when using ICU4J as the implementation baseline.

Reproduce with a checkout containing `icu4j` and cloc installed:

```sh
python3 scripts/measure-upstream.py /path/to/icu > docs/upstream-size.json
```

## Status

- KMP toolchain and targets configured; no ICU4J dependency in production.
- VersionInfo value operations implemented in commonMain; common tests and JVM
  differential tests against ICU4J 78.3 added.
- Deliberate differences: value equality without singleton interning; oversized
  version components are rejected before integer overflow. Historical constants,
  resource-backed timezone lookup and command-line entry point are not ported.
- UTF16 traversal, random access, offset conversion, surrogate operations and
  mutation are implemented for common strings/builders and array slices. Search,
  replacement by substring and StringComparator are still pending.
- Replaceable and ReplaceableString are implemented with shared builder storage.
- UForwardCharacterIterator, UCharacterIterator and its replaceable/array
  implementations are ported. The java.text.CharacterIterator adapters remain
  pending; cursor cloning shares storage as upstream does.
- The remaining ICU4J classes and data algorithms are unported. No complete API
  compatibility is claimed.

## Next stages

1. Common UTF-16 primitives, binary readers, trie lookup and Unicode property data.
2. UnicodeSet, normalization, case mapping and segmentation, with upstream fixtures.
3. Locale identifiers and resource bundles; resolve JVM IO, caches and synchronization.
4. Number/date/message formatting, calendars and timezone data.
5. Collation, transliteration and charset support.
6. Port remaining regression tests and add JVM/JS/Native parity coverage.

Each stage should keep algorithms in commonMain, pin upstream data versions,
retain copyright notices, and compare behavior against ICU4J before expanding API.

## Validation of the initial slice

`./gradlew jvmTest jsNodeTest macosArm64Test --console=plain` passed locally
on macOS arm64 with JDK 25. The JVM suite includes differential checks against
ICU4J; all three targets execute the common tests. Linux execution is delegated
to CI and has not been verified locally.

UTF16 was checked against ICU4J for every Unicode code point and all 781 strings
of length 0–4 over one BMP character and four surrogate boundary units, including
cursor shifts and mutable edits. ReplaceableString was checked over 2,000 seeded
editing operations. Common iterator tests passed on JVM, JS and macOS arm64;
JVM differential iterator tests exercise seeded cursor operations and array slices.

Kotlin/JS 2.4.21 emits `?` for unpaired surrogates in string literals. Tests build
such strings from runtime code units to avoid testing corrupted compiler output.
This also matters for future generated Unicode fixtures: use numeric arrays or
escaped runtime decoding for arbitrary UTF-16 data.

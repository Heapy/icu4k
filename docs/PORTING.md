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
  mutation are implemented for common strings/builders and array slices. Code-point
  search/replacement is also ported; substring search/replacement and
  StringComparator remain pending.
- Replaceable and ReplaceableString are implemented with shared builder storage.
- UForwardCharacterIterator, UCharacterIterator and its replaceable/array
  implementations are ported. The java.text.CharacterIterator adapters remain
  pending; cursor cloning shares storage as upstream does.
- CodePointMap range options, value filters, shared range iterator and
  bidirectional string iterator are implemented and checked against ICU4J.
- CodePointTrie FAST/SMALL × 8/16/32-bit formats, binary decoding/encoding,
  optimized filtered range enumeration and error-aware string iteration are ported.
- BinaryBuffer replaces Java ByteBuffer for common binary readers. ICUBinary
  header authentication, version conversion and typed array operations are ported.
- ICUData embeds pinned `uprops.icu`, `ulayout.icu`, `ubidi.icu` and `ucase.icu` bytes across all targets;
  other resources and filesystem/package scanning remain pending.
- LayoutProps loads real Unicode 17 Indic positional/syllabic category and vertical
  orientation tries. The modern block trie in uprops also decodes correctly.
- Legacy Trie2_16/Trie2_32 decoding, serialization, optimized range iteration,
  string iteration, equality and hashing are implemented. Alternate lead-code-unit
  values remain distinct from lead-code-point values.
- UCharacterProperty loads both property tries, additional vectors, script
  extensions and the block trie. General categories, Unicode age and numeric
  decoding are implemented; its full property dispatcher remains pending.
- UBiDiProps reads real bidi/shaping data, mirror tables, joining groups and
  paired-bracket properties. The bidi reordering algorithm remains unported.
- UProperty identifiers, UCharacterEnums, category/direction names and constants
  are implemented. UCharacter exposes category/numeric/bidi and legality APIs;
  simple case mappings and full case folding are also implemented. Locale-sensitive
  string casing, names, normalization and the full property dispatcher are pending.
- UCaseProps loads the original case data and implements simple/full mappings,
  contextual rules, folding, case flags, closures and reverse unfold lookup.
  UnicodeSet adapters and Latin fast-path tables remain pending.
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

CI for commit `fac6f85` passed JVM, JS and Linux Native tests on Ubuntu, and
macOS arm64 tests on macOS. Local JVM/JS/macOS suites also passed with
CodePointMap and code-point search/replacement. CodePointMap tests compare
surrogate overrides and filters over 100 seeded maps with boundary-aligned
ranges against ICU4J.

## Unicode data dependency chain

The data layer now includes `CodePointMap -> CodePointTrie`, legacy `Trie2`
and a common endian-aware binary reader. `UCharacterProperty` reads `uprops.icu`:
legacy Trie2 main/additional property maps, additional vectors, script extensions,
and a modern CodePointTrie for blocks. `ulayout.icu` contains the modern tries for
Indic positional/syllabic categories and vertical orientation. Preserve these
versioned ICU resources and their format validation; do not substitute host-JDK
Unicode properties, whose version and coverage differ between targets.

## Portable data and trie validation

`data/manifest.json` pins the source commit, sizes and SHA-256 checksums of raw
ICU data. Regenerate portable Base64 sources with `python3 scripts/generate-data.py`;
CI runs `--check` to verify generated sources and the raw-file checksums.
Generated resource strings contain only ASCII. Resource storage is decoded lazily
per file, and readers receive private byte copies. Raw data and generated assets
are not hand-written algorithm code and should be counted separately.

CodePointTrie tests compare all 1,114,112 Unicode code points for all six formats
against ICU4J-built tries, including a fixture requiring packed 18-bit data
indexes. They check exact serialized bytes, both endiannesses, filtered ranges,
ASCII/BMP/supplementary access, invalid-code-point error values, and unpaired
surrogate string iteration. Common tests exercise all six formats on JVM, JS and
macOS arm64. Actual layout property tries and the modern block trie from uprops
were compared against ICU4J's UCharacter property API over the full Unicode range.
Embedded resources match the ICU4J 78.3 artifact byte-for-byte.

The main property data, numeric decoding and bidi property tables are now ported.
The complete UCharacter property dispatcher still requires normalization,
emoji and other derived-property data; case property algorithms are implemented. MutableCodePointTrie and Trie2Writable
construction/compaction remain pending.

Common adaptations: binary APIs accept BinaryBuffer or ByteArray and serialize
into ByteArray instead of Java streams. ICUBinary failures use the common
ICUUncheckedIOException. Filesystem, classloader and package scanning have not
been silently replaced by host-JDK behavior; they remain explicit pending work.

CI run `38375483112` for commit `a0e07ea` passed all JVM/JS/Linux Native and
macOS arm64 suites, including the real ICU data checks. GitHub Actions were then
updated from deprecated Node 20 actions to released Node 24 actions.

## Legacy tries and character property validation

Trie2 tests compare the full Unicode range, all UTF-16 single-unit lookups,
filtered/unfiltered range iteration, lead-surrogate-specific ranges, serialized
bytes, version signatures, both byte orders, hashes and bidirectional string
iteration against ICU4J. Both 16-bit and signed 32-bit tries are covered;
common tests also check different values for lead code units vs. code points.
The two Trie2_16 tables in actual uprops.icu match upstream lookups.

UCharacterProperty's main words, all additional-vector columns, ages, decimal
and European digits, integer numeric values and exact Double bits were compared
to ICU4J for every Unicode code point. Bidi class, mirrors, controls, joining
properties, bracket properties and property-start boundaries likewise match
ICU4J over the full Unicode range. Raw ubidi.icu bytes match the upstream artifact.

Public UCharacter category/identifier/whitespace/legality predicates and nullable
code-point-to-string conversion are checked across the full Unicode range;
radix boundaries and supplementary digits are checked separately. Enum/property
constant values and original Byte/Int types are compared to upstream declarations.
Local JVM, JS/Node and macOS arm64 suites passed after these changes.

Some Java-specific APIs are intentionally still absent rather than approximated:
Java identifier predicates depend on host-JDK Unicode data in ICU4J and need an
explicit portability decision. Unicode identifier predicates already use pinned
ICU data. Locale-sensitive case conversion, names, normalization, resource
bundles, the bidi algorithm, formatting and the rest of ICU remain pending.

## Case mapping validation

The prior character-property CI run `38378616115` passed JVM, JS, Linux Native
and macOS arm64. The next local JVM/JS/macOS suites also passed with case data.
Simple lower/upper/title mappings, case flags, dot classes and simple/full folding
were compared against ICU4J over the entire Unicode range. Root full lower/upper/
title mappings and all case-derived binary properties likewise match. Context
fixtures cover six case locales, final sigma, Turkic and Lithuanian dot rules,
and Eastern/Western Armenian ligature mappings. Case queries use an immutable
Appendable sink, avoiding shared mutable scratch buffers.

Full/simple case closures and reverse unfold lookups were compared to ICU4J's
UnicodeSet results. They currently use internal callbacks until the common
UnicodeSet integration is ported. Raw ucase.icu bytes match the ICU4J artifact.
Public string folding matches ICU4J over a string containing every Unicode code
point for default/Turkic and extra option bits, including OMIT_UNCHANGED_TEXT.

Remaining casing scope includes CaseMapImpl string processing, Greek uppercase,
titlecase word boundaries, locale APIs, Latin fast paths and the public
UnicodeSet closure adapters. These are still part of the full ICU4J port.

## Edit tracking

Edits now runs in commonMain, including compact span encoding, fine/coarse
iterators, source/destination/replacement indices, bidirectional index lookup
and mergeAndAppend composition. Common BufferOverflowException replaces the
java.nio exception. Diagnostic strings retain span contents with a portable
class prefix.

Differential tests compare 100 randomized streams in all four iterator modes,
interleaved backward/forward searches and index conversion, 200 compatible
compositions, compressed repetitions, multi-unit lengths and overflow/reset
behavior against ICU4J. JVM, JS/Node and macOS arm64 suites pass locally.
The preceding commit 523ed2c passed CI on all four targets.

CaseMapImpl's StringContextIterator and title option conflict checks are ported.
Context traversal intentionally sees the entire input even when the mapping
segment is shorter, and a surrogate pair may cross a segment limit as in ICU4J.
Common tests cover these boundaries; JVM differential tests cover 30,000
interleaved operations over arbitrary UTF-16 plus title option combinations.
String mapping loops, Latin fast paths, Greek uppercase and title boundaries
remain pending.

## String lowercasing and folding

CaseMapImpl lowercasing and folding now retain the ICU Latin delta fast path,
trie fast path, context-sensitive exception handling, unchanged text batching,
Appendable output, Edits reset/recording and the short-string replacement-only
optimization. Public UCharacter.foldCase delegates to this shared implementation.
All four original LatinCase tables are retained for subsequent uppercasing.

JVM oracle tests compare output and every fine edit span for all Unicode code
points in a single string, locale IDs 0 through 6, default/Turkic folding, extra
option bits and OMIT_UNCHANGED_TEXT. Random malformed UTF-16 and explicit final
sigma, Lithuanian and Turkic contexts are covered. Common tests exercise these
algorithms on JS and Native as well.

Uppercasing (including Greek's separate state machine), titlecasing, locale
adapters and common wrapping of Java IOException from custom JVM Appendables
remain pending. Common Appendables currently propagate their own failures.

## String uppercase

CaseMapImpl uppercasing now includes the original Latin/trie fast paths and
GreekUpper's original letter tables and context state machine. Greek casing
handles accent removal, disjunctive eta, precomposed versus combining dialytika,
trailing capital iotas for ypogegrammeni, and non-Greek full uppercase mappings.
Short-string reconstruction and Appendable output retain exact Edits spans.

The string oracle now compares uppercase output and fine spans over all Unicode
code points, locale IDs 0 through 6, option bits, arbitrary malformed UTF-16,
and 200 generated Greek/diacritic sequences. A regression specifically covers
the distinction between precomposed and combining dialytika in vowel state.
Titlecase and public locale adapters remain pending, as do the rest of the
previously listed ICU subsystems.

## Titlecase string processing

CaseMapImpl titlecase processing now supports supplied boundary iterators,
index adjustment to cased or letter/number/symbol/private-use characters,
optional remainder lowercasing, short-string reconstruction, Edits and Dutch IJ
with matching acute accents and combining-mark exclusion. An internal
TitleIterator adapter supplies boundaries until common BreakIterator is ported;
it does not approximate or replace ICU word/sentence segmentation.

JVM differential tests supply the same ICU4J word boundaries to both mapping
implementations for six locales, adjustment/lowercase/omit options, Dutch accent
fixtures, expansions and random malformed UTF-16. Common tests exercise explicit
whole-string segments. Public locale APIs and common boundary engines remain
pending, along with the rest of the full ICU port.

## Public folding options and segmentation prerequisites

CaseMap.Fold is now available in commonMain with cached immutable default,
Turkic and omit-unchanged combinations, string/Appendable application and Edits.
Common tests verify option composition, append behavior and reset; JVM tests
compare all four option combinations and fine edit spans against public ICU4J.
Lower/Upper/Title public locale adapters are still pending.

The next boundary dependency is RBBIDataWrapper's compiled-rule parser, using
the existing ICUBinary/BinaryBuffer/CodePointTrie implementations. Full boundary
parity additionally requires the RuleBasedBreakIterator state machine and caches,
text cursor adaptation, word/sentence/grapheme/line rules, ICU resource bundles
and locale selection, dictionary engines and dictionary data. Supplying English
rule boundaries alone will not constitute a complete segmentation port.

## Compiled break rule data

RBBIDataWrapper now reads ICU version-6 rule headers, 8/16-bit state rows,
FAST category tries, rule-status vectors and UTF-8 source. State tables retain
equality/hash semantics and serialize to big-endian ByteArray with ICU padding.
BinaryBuffer cursor restoration replaces ByteBuffer mark/reset around trie reads.
Debug printing and the rule compiler are still pending.

All 15 upstream .brk resources are pinned in the checksum manifest and generated
as portable data, including word/POSIX, sentence/Greek, grapheme, title and line
variants. Oracle tests compare raw bytes, tables, hashes, state serialization,
status/source, final cursor position and every Unicode trie lookup with ICU4J.
Common tests load real data and reject invalid magic/version. Runtime boundary
traversal, dictionary engines, resource-bundle locale selection and public
BreakIterator factories remain pending.

## Common character cursor

A common CharacterIterator contract replaces java.text.CharacterIterator, with
StringCharacterIterator preserving ICU's bounded code-unit traversal, absolute
indices, mutable text reset, value equality/hash and independent cursor clones.
CharacterIteration ports next32/previous32/current32/nextTrail32, including the
intentional trail-unit positioning of nextTrail32. U+FFFF is distinguished from
end-of-range by cursor position; split surrogate pairs remain individual units.
The common port keeps StringCharacterIterator available rather than deprecating
it in favor of the unavailable java.text class.

JVM oracle tests compare 60,000 mixed cursor and code-point operations over
random ranges/UTF-16 against ICU4J. Common tests cover U+FFFF, pairs, split ranges,
empty text and clone independence. RBBI runtime traversal and adapters between
CharacterIterator and UCharacterIterator remain pending.

## Break rule state machines

Internal RBBIRuleEngine now ports RuleBasedBreakIterator.handleNext and
handleSafePrevious, including beginning/end pseudo-categories, lookahead matches,
rule-status indices, dictionary-character counting, surrogate cursor correction,
forced progress and safe-reverse index pinning. This is the original rule layer;
it is not exposed as a substitute for the complete public BreakIterator.

JVM tests invoke ICU4J's original private state machines and compare returned
positions, cursor positions, statuses and dictionary counts across all 15 rule
sets and 600 text fixtures, including malformed UTF-16 and dictionary scripts.
Common tests cover word/status output, emoji grapheme clusters, combining marks,
CRLF and pinned/split-pair indices. Dictionary subdivision, BreakCache,
DictionaryCache and public iterator/factory integration remain pending.

## Dictionary candidate helpers

DictionaryMatcher's common cursor contract and DictionaryBreakEngine's
PossibleWord/DequeI helpers are ported. Candidate caching distinguishes the
longest prefix from the longest accepted word, supports backtracking and marked
candidate selection, and restores the cursor after failed matches. DequeI
retains the original limited front capacity, growth, reset and cloned storage.

Common tests exercise candidate/backtracking/reset and deque growth. JVM oracle
tests compare candidate choices/caching with ICU4J and 50,000 mixed deque
operations, including independent clones. These tests use deterministic matcher
fixtures; actual byte/character trie dictionary matchers and data, UnicodeSet
integration and language engines still need to be ported.

## Compact byte trie reader

BytesTrie now supports current/first/next (single byte and bulk), compact signed
values, binary branch jumps, linear matches, saved/copy/clone/64-bit states,
unique-value detection and next-byte enumeration. ByteArray storage remains
shared, and validated State restoration checks array identity and root offset.

JVM differential tests use original BytesTrieBuilder FAST/SMALL output with
random keys and all value-encoding boundaries, signed extremes, intermediate
values and shared prefixes. They compare result, value, exact packed state,
unique values and next-byte output during traversal/restoration. Common tests
cover a serialized intermediate/final trie on every target.

Entry enumeration/Iterator, the common builder, dictionary matchers and wrapping
Java IOException from custom JVM Appendables remain pending. These are required
parts of the full port, not replaced with a different data structure.

## Byte trie enumeration

BytesTrie now implements Iterable with ICU's reusable Entry and Iterator.
Traversal retains the compact branch stack, suffix iteration from the current
reader state, maximum-length truncation (value -1), reset and exhaustion behavior.
Entry.toByteArray supplies an isolated portable snapshot in place of the Java
read-only ByteBuffer view; byteAt/copyBytesTo retain their original semantics.

Oracle tests compare FAST/SMALL trie enumeration, copied bytes, values, object
reuse, truncation at eight limits, reset, stopped readers and every position
inside a long shared linear prefix. Common tests cover iteration and truncation
on JVM/JS/Native. BytesTrieBuilder and dictionary integration remain pending.

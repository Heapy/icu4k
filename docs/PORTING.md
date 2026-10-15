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

## Byte dictionary matching and cursor adapters

BytesDictionaryMatcher now uses the common BytesTrie, offset transforms and
ZWJ/ZWNJ special bytes, returning candidate lengths/values with ICU limit and
cursor semantics. CharacterIteratorWrapper and UCharacterIteratorWrapper bridge
both common cursor contracts, and UCharacterIterator exposes the corresponding
factory and getCharacterIterator API. Cloning copies cursor state.

Compatibility quirks are retained: the CharacterIterator adapter treats U+FFFF
as DONE, retains absolute indices for bounded input, and moveIndex returns the
code unit at the resulting position. BytesDictionaryMatcher counts code points
rather than UTF-16 units and leaves count untouched on empty input, as upstream.

JVM oracle fixtures cover BMP/supplementary transforms, joiners, failed prefixes,
zero/limited maximum lengths, result limits and optional values. Adapter tests
compare 30,000 mixed operations on bounded UTF-16/noncharacter input. Common
tests cover candidate values and shared/independent cursor behavior. Actual
dictionary resources/loaders, character-trie matcher and language engines remain
pending.

## Pinned dictionaries and binary decoding

All five ICU dictionaries (Thai, Lao, Khmer, Burmese, CJK) are checksum-pinned
and embedded via the same portable lazy Base64 pipeline. DictionaryData reads
the ICU Dict header, index block, padding, trie type, byte offset transform and
byte or UTF-16 trie payload. Decoded byte dictionaries create the common matcher.
The decoded character payload is retained for the upcoming CharsTrie matcher.

JVM tests compare every resource byte and decoded payload/index/cursor with ICU4J,
then compare candidate lengths/values/counts and cursor positions for 500 real
entries per byte dictionary under multiple limits. Common tests load all five
resources and match a Thai fixture on every target.

Resource-bundle-driven script-to-file lookup (loadDictionaryFor), CharsTrie/
CharsDictionaryMatcher and the language engines remain pending. The binary
decoder does not substitute fixed locale mappings for the original factory.

## Character trie reader and CJK matching

CharsTrie now reads ICU's compact UTF-16 branch/linear/value encoding, including
intermediate node values, code-point entry methods, signed wide values, saved
identity-checked and packed states, unique values and next-character enumeration.
CharsDictionaryMatcher uses this reader, and decoded character dictionaries can
create matchers. The original code-point length/count behavior is preserved.

Oracle tests compare FAST/SMALL builder output for randomized keys, all value
width boundaries and signed extremes, and traversal over 1,000 real CJK keys.
They also compare candidate lengths/counts/weights and cursor positions for
1,000 CJK words plus supplementary/joiner/empty/failure fixtures under multiple
limits. Common tests exercise intermediate values, state restoration and real
Japanese dictionary lookup on every target.

CharsTrie Entry/Iterator and builders, language segmentation algorithms,
normalization and script/UnicodeSet integration remain pending. Matching dictionary
words alone is not the complete CJK segmentation algorithm.

## Character trie enumeration

CharsTrie now implements Iterable with the original reusable Entry and mutable
CharSequence key buffer. Iterator ports preserve packed branch stacks,
intermediate node-value skipping, suffix traversal, truncation sentinel -1,
maximum key lengths, reset, exhaustion and unsupported removal.

JVM oracle tests compare all entries of the real CJK dictionary, randomized
FAST/SMALL tries, shared intermediate prefixes, eight truncation limits, reset
and every position inside long linear matches. Common tests verify entry/key
buffer reuse and truncated suffixes on every target. Trie builders and the
language segmentation engines remain pending.

## Shared trie construction and character builder

StringTrieBuilder ports the original three-stage algorithm: mutable prefix/
branch insertion, FAST/SMALL runtime-node registration and hash-consing, then
right-edge numbering and backwards serialization. All original node kinds and
identity-based child comparisons are retained. CharsTrieBuilder implements the
UTF-16 backend, growing tail buffer, value/type/delta encodings, lifecycle and
shared CharSequence views (using a portable array view instead of CharBuffer).

Oracle tests compare exact serialized units with ICU4J for 80 randomized builds,
prefix insertion orders, duplicate detection, wide signed values, 4,096-way
branches, long keys, repeated builds and clear/reuse. Common tests round-trip
FAST/SMALL output and check lifecycle errors and old-view stability.
BytesTrieBuilder remains pending, along with language segmentation engines and
the other full ICU subsystems listed above.

## Byte trie construction

BytesTrieBuilder now uses the shared original construction algorithm with byte
serialization, intermediate-value nodes, all signed value widths, branch delta
encodings and growing tail buffers. buildByteBuffer returns the common shared
BinaryBuffer with matching capacity/position; buildByteArray offers an isolated
rooted snapshot. Built readers remain valid after builder clear/reuse.

JVM tests compare exact bytes, buffer position/capacity and lookup values for
80 randomized FAST/SMALL builds, prefix insertion orders, shared suffixes and
all value-width boundaries. Delta encodings are compared at every width boundary.
Common tests verify round trips, copied inputs and lifecycle errors.
Both compact trie reader/builder families are now ported; JVM IOException
wrapping and the remaining full ICU subsystems are still pending.

## Serialized Unicode sets

USerializedSet ports ICU's compact BMP/supplementary boundary reader, copied
input storage, singleton encoding, range enumeration and membership queries.
Implicit U+10FFFF range ends and the original reset/error-state behavior are
retained. This supports ICU data consumers; full mutable UnicodeSet and pattern
parsing are still pending.

JVM differential tests compare 200 generated boundary sets, range enumeration,
membership around every boundary and random code points, singleton edge cases
and truncated inputs. Common tests cover mixed BMP/supplementary ranges,
singletons, copied storage and invalid data on every target.

## UnicodeSet inversion-list core

UnicodeSet now ports constructors/copy/set, validated code-point and string
membership, singleton insertion with adjacent-range collapse, append-range fast
paths, original union polarity algorithm, string-element deduplication, range
accessors, size, compact, clear and value equality/hash. Sorted string elements
use binary insertion in a common list instead of java.util.TreeSet. The original
HIGH sentinel and buffer/capacity rules remain intact.

JVM oracle tests compare 10,000 mixed insert/union/clear/compact operations, all
range boundaries, sizes, hashes, membership and copy behavior. Full Unicode
membership is also checked after covering the entire range. Common tests cover
range merging, strings versus supplementary code points and maximum bounds.
Removal/intersection/complement, freezing, spans, iteration, pattern parsing,
property resolution and dictionary integration remain pending.

## UnicodeSet algebra

UnicodeSet now ports the original intersection/difference polarity machine and
symmetric-difference merge, range/singleton/string removal and retention, whole
Unicode complement, set-to-set retain/remove/complement, and removeAllStrings.
Operations preserve ICU's distinction between ranges and string members: range
retention and whole complement do not remove strings. Self-aliasing operations
are supported.

JVM tests compare 30,000 randomized range/string/set operations, range boundaries,
sizes, hashes and membership, plus BMP/supplementary/maximum crossing cases.
Common tests cover interval splits, string retention, self-intersection,
self-difference and symmetric difference. Freezing/span acceleration, public
iteration, parsing and property resolution are still pending.

## Dictionary range dispatch

LanguageBreakEngine's common contract and DictionaryBreakEngine range dispatch
are connected to UnicodeSet. setCharacters retains a compact independent copy;
handles queries use the set. findBreaks scans the supported code-point run from
the actual cursor, delegates divideUpDictionaryRange with phrase mode and the
existing output deque, then restores the run-end cursor as upstream does.

JVM tests compare 500 randomized bounded UTF-16 ranges with ICU4J, including
surrogate pairs/unpaired units, independent startPos, set copying, delegated
arguments, existing breaks and cursor restoration. Common tests cover a
supplementary run. The delegated language-specific algorithms themselves are
still pending; test delegates only verify this shared dispatch contract.

## UnicodeSet iteration

UnicodeSetIterator now supports interleaved next/nextRange, skipToStrings,
reset/new-set reset, result fields and getString. UnicodeSet implements the
original string iterator order (code points first, then sorted string members),
exhaustion and unsupported removal. strings() returns a read-only view tied to
the original string storage; set and compact retain upstream view-detachment
behavior. Mutation during active iteration remains outside the ICU contract.

JVM differential tests compare 80,000 mixed iterator operations, complete
iteration order and live/detached string views. Common tests cover mixed ranges,
supplementary code points, empty string members and reset. Frozen acceleration,
spans, patterns and property resolution remain pending.

## Frozen code-point accelerator

BMPSet ports the original Latin-1 table, U+07FF bits, mixed BMP block flags,
4K inversion-list search bounds, copy constructor and direct/backward span
algorithms with code-point counts. OutputInt and all SpanCondition identifiers
are available in common code. The original reverse machine's behavior on
malformed surrogate/BMP sequences is retained.

Oracle tests compare membership across the full Unicode range for 25 sets,
including empty/full sets and partial blocks, and both span directions at all
indices of random UTF-16 strings for every condition. Common tests cover
counts, supplementary pairs, unpaired units and block edges. UnicodeSet.freeze
and public string-aware spans still require UnicodeSetStringSpan; BMPSet is
currently an internal dependency rather than a partial replacement for freeze.

## String-span primitives

UnicodeSetStringSpan's circular OffsetList, minimum path counts, length-byte
encoding, code-point-boundary string matching and single-code-point forward/
backward membership primitives are ported. The offset ring retains ICU's shift,
wrap and popMinimum semantics without replacing it with a sorted queue.

JVM oracle tests compare 50,000 mixed offset/count operations, 500 malformed
UTF-16 fixtures at every position and span-length boundaries. Common tests cover
wraparound, minimum counts and rejection of matches that split a surrogate pair.
The constructor/preprocessing and main string-aware span machines remain
pending, as does UnicodeSet.freeze integration.

## Unfrozen code-point span fallback

UnicodeSet's original forward span/count and reverse code-point fallback loops
are ported as internal dependencies for string-span preprocessing. They preserve
code-point-at/before behavior at arbitrary UTF-16 indices and optional counts.
Public span methods are still pending until string elements are handled.

JVM tests compare 200 malformed UTF-16 fixtures at every forward/backward index
for every SpanCondition with ICU4J's original unfrozen fallback. Common tests
cover supplementary counts and starting on a trail surrogate.

## Property-table UnicodeSet integration

UCaseProps now exposes its original UnicodeSet adapters for full/simple code-
point closures, reverse string closures and property starts. UBiDiProps likewise
adds its property starts to the common set. Existing callback implementations
remain shared rather than duplicating algorithms.

JVM oracle tests compare closure sets for every cased Unicode code point plus
1,000 other samples, reverse-fold fixtures and complete case/bidi boundary sets,
including preservation of pre-existing elements. Common tests cover sharp-s,
ASCII simple closure and property boundaries. Public UnicodeSet.closeOver and
the string-span/freeze integration are still pending.

## String-span preprocessing

UnicodeSetStringSpan constructors now port relevant-string detection, empty-
string removal from the supplied list, maximum UTF-16 lengths, per-direction
prefix/suffix metadata and auxiliary NOT_CONTAINED sets for all flag modes.
The copy constructor retains frozen set sharing and copies span metadata.
Internal code-point-only sets can now freeze through BMPSet; mutators reject
frozen storage and clone/cloneAsThawed preserve the original state semantics.
Public freeze for sets containing strings remains pending.

Oracle tests compare preprocessing fields, metadata, auxiliary ranges and frozen
states for 100 UTF-16 fixtures under 13 flag combinations, plus copy construction.
Common tests check relevance, empty removal, clones and all existing mutation
entry points on frozen internal sets. Main string-aware span machines remain
pending, so public span/freeze APIs are not yet presented as complete.

## String-aware NOT_CONTAINED spans

UnicodeSetStringSpan now ports forward NOT_CONTAINED spans with optional counts
and reverse NOT_CONTAINED spans. Both use the precomputed auxiliary set to skip
uninteresting code points, then test original code points and relevant strings
at code-point boundaries, skipping false string starts/ends.

JVM oracle tests compare both directions and counts for 300 sets of overlapping
string/malformed UTF-16 fixtures, four preprocessing flag modes and every valid
start/limit. Common tests cover false starts, supplementary counts and empty
input, including preservation of an existing count at end-of-input.
CONTAINED/SIMPLE string machines and public span/freeze integration remain
pending.

## String spans with element counts

UnicodeSetStringSpan.spanAndCount now dispatches NOT_CONTAINED, greedy SIMPLE
and minimum-element-count CONTAINED paths. Fully code-point-contained strings
remain relevant for counts. CONTAINED uses the original offset/count ring
algorithm with per-call scratch in common code, avoiding shared mutable scratch
and JVM-only synchronization. This introduces a ring allocation per contained
count call compared with ICU4J's synchronized reusable ring.

JVM oracle tests compare counts and end positions for 300 overlapping/malformed
fixtures at every start, all conditions and ALL/forward-count preprocessing.
Concurrent calls on one span object are checked separately. Common tests
exercise paths where greedy matching stops earlier and strings reduce counts.
Optimized non-counting forward/reverse CONTAINED/SIMPLE spans and public
UnicodeSet span/freeze integration remain pending.

## Optimized forward string spans

UnicodeSetStringSpan.span now ports the original optimized forward
CONTAINED/SIMPLE overlap machine, including initial code-point spans, relevant
prefix lengths, long-prefix fallback, earliest-start longest matching, pending
ring offsets and one-code-point progression between pending string matches.
Scratch remains per-call for common concurrency. Count loops also use indexed
string access as upstream does.

JVM tests compare 300 overlapping/malformed fixtures at every start under ALL
and forward-contained metadata modes, including long prefixes and supplementary
strings. Common tests distinguish greedy from contained paths and validate
code-point/string overlap. Optimized reverse spans and public UnicodeSet
span/freeze integration remain pending.

## Optimized reverse string spans

UnicodeSetStringSpan.spanBack now ports the original reverse overlap machine
for CONTAINED and SIMPLE, including suffix metadata, long suffix fallback,
latest-end longest matches, pending decrements and single-code-point progression.
The offset ring is local to each call, as in the forward common implementation.

Differential JVM tests cover 300 overlapping/malformed fixtures at every limit
under ALL and backward-contained metadata modes, supplementary strings and long
suffixes. Common tests cover greedy versus contained paths and overlaps with
code-point spans. Public UnicodeSet span/freeze integration remains pending.

## Public UnicodeSet spans and freezing

UnicodeSet now exposes span/spanBack overloads, spanAndCount and freeze using
the original dispatcher: cached BMPSet when strings are irrelevant, cached
UnicodeSetStringSpan for frozen string sets, and directional preprocessing for
thawed sets. Fully contained strings remain relevant for counting even when
BMPSet handles ordinary spans. Frozen sets reject mutations and clone returns
the same object; cloneAsThawed copies editable data without frozen caches.

JVM differential tests exercise 180 sets before and after freezing, all span
conditions, every input boundary and out-of-bounds clamping, greedy overlaps,
supplementary and malformed UTF-16, long suffixes and unchanged count outputs
at the end. Common tests cover frozen string paths, thawed-copy independence
and counts reduced by fully contained strings. Pattern/property parsing and
the remaining UnicodeSet API are still pending.

## Pattern syntax and whitespace primitives

PatternProps now uses the upstream Latin-1 and indexed BMP bit tables for the
immutable Pattern_Syntax and Pattern_White_Space properties. Identifier checks,
skipping and whitespace trimming are common Kotlin. trimSpaceChar preserves
the upstream asymmetric trailing Pattern_White_Space predicate; the leading
Java SpaceChar predicate is represented by its Unicode separator ranges.

JVM oracle tests compare every Unicode code point plus out-of-range values,
and 2,000 random strings with every identifier slice. Common tests cover
non-ASCII syntax, whitespace distinctions and the trailing trim quirk. This
provides a dependency for pattern parsing; the parser itself remains pending.

## Escape decoding for rule parsing

Utility now ports unescapeAndLengthAt, packed code-point/length accessors,
unescape and unescapeLeniently. The decoder retains hexadecimal, braced,
octal, C-style, control and generic escapes, surrogate joining across escaped
or literal trails, and the upstream bounded recursive lookahead. This removes
another dependency of RuleCharacterIterator; that iterator is not yet ported.

Differential JVM tests compare 4,000 composed valid/invalid escape fixtures at
every offset, strict error messages and lenient decoding. Common fixtures
exercise numeric escapes, surrogate joining and invalid forms. Other Utility
methods remain pending.

## Rule character iteration and symbol contracts

RuleCharacterIterator now ports the original code-point iteration, option
flags, variable expansion, escapes, ignored whitespace, position snapshots,
raw-buffer access and code-unit jumping. SymbolTable and UnicodeMatcher
contracts are common interfaces. ParsePosition provides mutable index and
errorIndex in common code in place of java.text.ParsePosition; its diagnostic
class name is portable. UnicodeSet does not yet implement UnicodeMatcher.

JVM differential tests exercise 600 rule strings under all eight option
combinations, including repeated end/error calls and snapshot restoration.
Common tests cover symbol expansion, undefined references, escaped values,
position independence while inside a variable and code-unit jumping. The
UnicodeSet pattern parser and concrete matcher implementations remain pending.

## UnicodeSet matching algorithms

UnicodeSet now ports matchesIndexValue, addMatchSetTo and matches, including
longest string matches, incremental partial matches, ETHER anchoring, reverse
string traversal and the original UnicodeFilter code-point fallback with
reverse surrogate offset adjustment. The class does not yet declare the
UnicodeMatcher interface because its toPattern implementation is still pending.

JVM tests compare 150 mixed range/string sets at all start/limit combinations
in both incremental modes, including frozen sets and malformed UTF-16, and
all 256 index values. Common tests verify longest and partial matches, unchanged
offsets on partial matches, ETHER and union behavior.

## Generated UnicodeSet patterns and matcher integration

UnicodeSet now implements UnicodeMatcher and generates toPattern output with
the original inversion-list algorithm: economical code-point complements,
adjacent ranges, surrogate range ordering, escaped metacharacters, whitespace,
controls and noncharacters, plus sorted string elements. _generatePattern uses
a common StringBuilder in place of StringBuffer and supports excluding strings.
Utility adds the original printable/always-escape predicates and hexadecimal
escape emitters. Cached parsed-pattern preservation will arrive with parsing;
currently all sets are constructed from ranges and strings and generate output.

JVM tests compare 1,200 generated sets in both escape modes, includeStrings
modes and append-to-buffer behavior, then parse every generated pattern with
ICU4J and compare the resulting set. Common tests cover ranges, complements,
strings, surrogate boundaries and use through the UnicodeMatcher interface.
The common pattern parser remains pending.

## UnicodeFilter hierarchy

UnicodeFilter is now a common abstract UnicodeMatcher with the original
single-code-point match implementation, including reverse surrogate cursor
adjustment and incremental boundary results. UnicodeSet extends this base and
delegates its code-point fallback to super.matches rather than duplicating it.
The existing exhaustive-direction UnicodeSet matcher oracle continues to cover
this fallback; common tests additionally exercise the public filter hierarchy
and the base filter's distinct incremental boundary behavior.

## UnicodeSet code-point rank and selection

UnicodeSet.indexOf and charAt now use the original inversion-list scans to
convert contained code points to their ordered ranks and back. String elements
are excluded from these ranks even though size includes them. Invalid code
points are rejected; out-of-range ranks return -1, including extreme integers.

JVM oracle tests cover 100 random range sets and their complements, string
members, frozen sets, supplementary boundaries and rank/selection inverses.
Common tests cover gaps, string exclusion, empty sets and the full Unicode
range. This completes these two APIs; the remaining UnicodeSet API and pattern
parser still require further porting.

## UnicodeSet inclusion and intersection predicates

UnicodeSet now ports containsAll/containsNone/containsSome for sets, ranges
and input text. Set predicates scan inversion lists and compare string members;
text containment retains the original code-point fast path and recursive string
segmentation fallback. Text disjointness uses the shared NOT_CONTAINED span.
Empty strings are excluded from recursive progress as upstream does.

Differential tests compare 1,200 random mixed sets, complements, frozen sets,
range pairs (including reversed bounds) and supplementary/malformed text.
Common tests distinguish string membership from code-point membership and
segmentation, as well as empty input. Iterable overloads and other UnicodeSet
APIs remain pending.

## UnicodeSet collection and character-sequence overloads

UnicodeSet now ports collection containment/disjointness, addAll/removeAll/
retainAll for iterable members, vararg addAll, character-sequence bulk algebra,
and from/fromAll factories. Iterable addition uses each object's string value;
character-sequence bulk operations use individual code points, whereas iterable
and vararg members are whole strings. Frozen guards run even for empty input.

Common tests distinguish these overloads and check supplementary code points,
duplicate character toggling and empty collections. JVM oracle tests compare
600 random collections through the containment and mutation overloads against
ICU4J. Remaining UnicodeSet APIs and pattern parsing are still pending.

## UnicodeSet ordering

UnicodeSet now implements Comparable with the original SHORTER_FIRST,
LEXICOGRAPHIC and LONGER_FIRST styles, inversion-list boundary comparison,
string/code-point crossover and ordered string comparison. Iterable comparison
and companion helpers for iterators, iterables and string/code-point UTF-16
comparison are also ported. Supplementary comparisons avoid creating strings.

JVM tests compare 2,000 mixed set pairs under every style and exercise iterable
and string/code-point helpers against ICU4J. Common tests distinguish the three
orders and cover supplementary code points, prefix ordering and validation.
Pattern parsing and the remaining UnicodeSet API remain pending.

## UnicodeSetIterator shared string progression

The already ported UnicodeSetIterator now shares nextString between point and
range traversal. Its existing oracle and common tests are retained, including
reset, skipping and mixed traversal coverage. This is a refactor of an existing
port, not an additional completed ICU4J class.

## UnicodeSet extra match and representations

UnicodeSet now exposes matchesAt, getRegexEquivalent and the original
pattern-based toString. matchesAt retains upstream's internal length check,
first-string selection and exceptional empty-string behavior rather than
silently correcting its legacy semantics. Explicit checked character access
keeps those exception paths consistent on JS and Native. Regex-equivalent
output reuses the pattern code-point emitter.

JVM tests compare 600 mixed sets for diagnostic/regex output and matchesAt
results or exceptions at every fixture offset. Common tests cover exact string
matching, the legacy length check and empty-string exception behavior.

## Property alias data resource

The original ICU 78.3 pnames.icu resource is now embedded for common targets,
with its 47,072-byte size and SHA-256 pinned in the data manifest. ICUData and
ICUBinary expose independent copies through the existing resource mechanism.
Generator verification checks all 25 embedded resources. Common tests verify
the pnam/version-2 header, index offsets and copy isolation. The UPropertyAliases
decoder and property-name resolution APIs are the next step and remain pending.

## Property alias decoder and lookup

UPropertyAliases now decodes the pinned pnames.icu value maps, ASCII name
groups and byte tries, including ranged and listed property values. Original
property/value name retrieval, loose alias resolution, no-throw value lookup
and loose-name comparison are common code. IllegalIcuArgumentException retains
its common constructors; JVM-only initCause mutation is not exposed.

JVM tests compare names, choices, value names and alias lookup across binary,
integer, mask, numeric and string property ranges, plus 10,000 random loose-name
comparisons. Common tests resolve real names and values on each target. Public
UCharacter name adapters and UnicodeSet property parsing remain pending.

## Public UCharacter property alias adapters

UCharacter now exposes getPropertyName/getPropertyEnum and property-value name,
enum and no-throw enum lookup through the common UPropertyAliases decoder.
Unknown aliases throw IllegalIcuArgumentException as upstream does. Combining
class values use the original unsigned-byte valid range and return null for
valid values without names, while invalid name choices still throw.

JVM tests compare combining-class boundaries and every name choice, several
property families, unknown aliases and error messages with ICU4J. Common tests
exercise names, alias resolution and unnamed combining-class values. This does
not yet implement the complete property value dispatcher or UnicodeSet parser.

## Common Freezable contract

Freezable<T> now exposes isFrozen, freeze and cloneAsThawed in common code,
and UnicodeSet implements it through its already verified freeze machinery.
The JVM-only Cloneable marker is omitted from the common contract; explicit
cloneAsThawed is available on every target. Common tests exercise generic
interface use, idempotence, frozen mutation rejection and copy independence.

## UnicodeSet range views and legacy code-point utilities

UnicodeSet.ranges now provides the original reused mutable EntryRange per
iterator, range formatting and unsupported removal. addBridges fills only
interior gaps covered by the supplied set. Legacy findIn/findLastIn/stripFrom
retain code-point-only semantics, including the original reverse UTF-16 cursor
behavior; these are separate from string-aware spans. Pattern code-point
formatting is shared with EntryRange without allocating temporary sets.

JVM tests compare 400 random sets for range fields/formatting, search positions,
stripping and gap filling. Common tests exercise entry reuse, exhaustion,
string exclusion and bridge behavior. JVM stream adapters are not part of the
common range view; the common pattern parser remains pending.

## UnicodeSetSpanner

UnicodeSetSpanner now ports countIn, deleteFrom, replaceFrom and trim with all
original overloads, CountMethod and TrimOption values. It retains the supplied
set rather than freezing or copying it, alternates the original span conditions,
uses minimum element counts for replacements, and preserves the input sequence
identity when trimming makes no change. Equality and hashing delegate to the set.

JVM differential tests compare 180 mixed frozen/thawed sets over all span,
count and trim options, overlapping strings, supplementary and malformed text,
empty input and several replacements. Common tests verify counting/replacement,
source retention, unchanged trim identity and equality. The common pattern
parser and the rest of ICU4J remain in progress.

## CharSequences utilities

CharSequences now ports matching prefixes at character boundaries, single-code-
point detection/equality, UTF-16 comparisons, content equality, boundary checks,
code-point search and array extraction. The original private constructor and
instance codePointLength shape are retained. UnicodeSet delegates its string/
code-point comparison to this shared implementation. The original array builder's
UTF-16 cast of the previous result is retained for malformed-input compatibility.

JVM tests compare 3,000 random malformed UTF-16 fixtures with all valid prefix
start pairs, boundaries, comparisons, equality, search and extracted arrays.
Common tests cover supplementary pairs, differing trails and CharSequence content.
The complete ICU4J port and UnicodeSet pattern parser remain in progress.

## UnicodeSet parser contracts and remaining helpers

UnicodeSet now includes XSymbolTable's default symbol/property callbacks,
pattern option constants, resemblesPattern recognition, getSingleCodePoint,
collection comparison styles and addAllTo collection/array helpers. These
prepare the original parser integration; exposing option constants does not
mean applyPattern or case closure is implemented yet.

JVM tests compare recognition at valid and beyond-end offsets, collection
ordering and array copying with ICU4J. Common tests cover default callbacks,
unchanged output on unrecognized properties, supplementary detection and
caller-owned target arrays. Pattern parsing and case closure remain pending.

## Identifier type properties

UCharacterProperty now decodes Identifier_Type's exclusive values and combinable
bits from additional-vector column 2, with original ordinal predicates and
caller-set output. UCharacter exposes IdentifierType/IdentifierStatus enums,
hasIdentifierType and getIdentifierTypes. MutableSet replaces JVM EnumSet in
the common output API; output is cleared before filling. Identifier_Status's
integer-property dispatch is not yet wired by this change.

JVM tests compare 20,000 random code points and boundary/invalid inputs against
ICU4J, all twelve predicates and output sets, including invalid internal type
indices. Common tests verify recommended, ignorable and noncharacter data and
caller-set replacement. The rest of the property dispatcher remains pending.

## Direct-vector integer properties

UCharacterProperty now implements the seven direct-vector integer dispatch
entries: Decomposition_Type, East_Asian_Width, Line_Break, Grapheme_Cluster_Break,
Sentence_Break, Word_Break and Indic_Conjunct_Break. Values and maxima use the
original columns, masks and shifts. Internal helpers reject other property
sources rather than pretending that a partial dispatcher is complete.

JVM tests compare all seven entries and maxima against ICU4J's dispatcher on
20,000 random code points per property plus invalid/boundary inputs. Common
tests read real stored values and verify unsupported-source rejection. Full
integer and binary dispatch, normalization-backed properties and public
getIntPropertyValue remain pending.

## Derived integer property entries

UCharacterProperty now implements the derived General_Category, Numeric_Type,
Hangul_Syllable_Type and Identifier_Status entries and maxima. Hangul mapping
uses the upstream GCB-to-HST table and excludes supplementary GCB vowels, as
required since Unicode 16 for Kirat Rai. NumericType and HangulSyllableType
constants are exposed by UCharacter. These remain internal dispatch components
until the other property sources are ported.

JVM tests compare all Unicode code points plus adjacent invalid values for all
four entries against ICU4J. Common tests cover Jamo, LV/LVT syllables, the
supplementary exclusion, decimal digits and identifier status. Normalization,
script and other dispatch sources remain pending.

## Trie-backed integer property entries

UCharacterProperty now dispatches the original bidi class, joining group/type,
paired bracket type, block, Indic positional/syllabic categories and vertical
orientation entries through the already ported tries. Block indexing retains
the four-bit shift only for in-range/error-compatible inputs. Maxima come from
the original resource metadata. Helpers reject other property sources.

JVM tests compare every Unicode code point, adjacent invalid inputs and a large
invalid integer for all eight entries, plus maxima. Common tests cover bracket
classification, block/error indexing and source rejection. Script and
normalization entries, binary properties and the public complete dispatcher
still remain pending.

## UScript data operations

UScript now exposes all ICU 78.3 script constants, getScript, hasScript,
getScriptExtensions, getCodeFromName and long/short names. Script and extension
lookup ports the original tagged vector and terminated char-array algorithms.
MutableSet<Int> replaces JVM BitSet for output, including clearing and the
original signed return value. Invalid getScript inputs are rejected; extension
lookup retains the resource error value behavior.

JVM tests compare every Unicode code point and adjacent invalid inputs for
script/extension results and sampled membership, plus all script names. Common
tests cover prolonged-sound-mark extensions, names and invalid inputs. Locale
selection, script usage/sample metadata and remaining UScript APIs are pending.

## UScript metadata

UScript now ports the pinned script metadata table, ScriptUsage enum, sample
strings, usage, right-to-left, line-break-between-letters and cased flags.
Out-of-range script values retain the original empty/default metadata behavior.
JVM tests compare every script entry and invalid bounds with ICU4J; common tests
exercise representative flags and supplementary-capable sample generation.
Locale-to-script selection remains pending.

## Direct-vector binary properties

UCharacterProperty now decodes all 32 binary property bits stored in additional
vector word 1 using the original UProperty-to-bit mapping, including the signed
high bit for Prepended_Concatenation_Mark. The internal helper rejects properties
from other sources; it is a component of the future complete binary dispatcher.

JVM tests compare 20,000 random code points per bit plus boundary/error inputs
against ICU4J. Common tests cover alphabetic, pattern whitespace, the high bit
and unsupported sources. Case, bidi, normalization, emoji and derived binary
dispatch entries remain pending.

## Case, bidi and fixed-range binary entries

UCharacterProperty now routes ten case and three bidi binary properties to the
ported data engines. Five additional entries retain the original fixed UCD
ranges: Regional_Indicator, IDS_Unary_Operator, ID_Compat_Math_Start/Continue
and Modifier_Combining_Mark. Math continuation includes math-start characters.
These remain internal components of the complete dispatcher.

JVM tests compare every Unicode code point plus adjacent invalid inputs for
all 18 entries with ICU4J. Common tests exercise case, mirrors, flags, math
start/continue differences and combining marks. Normalization, emoji and other
derived binary properties are still pending.

## POSIX binary properties

UCharacterProperty now ports the five derived POSIX binary entries using the
original Unicode-category and alphabetic rules. Blank differs from Print for
TAB, Graph excludes separators/control/surrogate/unassigned categories but
retains format characters, and XDigit includes every Unicode decimal digit
alongside ASCII/fullwidth hex letters.

JVM tests compare every Unicode code point and adjacent invalid values for all
five entries with ICU4J. Common tests check the class distinctions and non-ASCII
digits. Normalization/emoji binary entries and the full public dispatcher are
still pending.

## Emoji properties and sequences

The pinned uemoji.icu resource and EmojiProps decoder are now common. The
original FAST/8 code-point trie, six UTF-16 sequence tries, property starts,
code-point/string predicates and addStrings enumeration are ported, including
RGI_Emoji union behavior. The manifest pins the 14,400-byte original resource;
the generator now checks 26 resources.

JVM tests compare every Unicode code point for emoji property IDs, all decoded
sequence sets and sequence/prefix lookups, and property starts against ICU4J.
Common tests exercise code-point emoji, keycaps, ZWJ sequences and enumeration.
Public UCharacter binary dispatch and normalization-backed properties remain
pending.

## Normalization Hangul and UTF-16 primitives

Normalizer2Impl now begins with the original Hangul and UTF16Plus primitives:
Jamo/Hangul predicates, recursive and raw syllable decomposition, surrogate
bit tests and binary sequence/slice equality. Hangul methods retain the original
precondition that decomposition input is a Hangul syllable and append to the
supplied buffer. This is not yet a complete normalization engine.

JVM tests compare predicates over all Unicode code points, every Hangul syllable
for both decomposition forms, and random UTF-16 slice equality with ICU4J.
Common tests exercise append behavior, LV/LVT raw decomposition and equality.
Normalization data loading, reordering, composition and public normalizers
remain pending.

## Normalization resource loading

All five pinned .nrm resources (NFC, NFKC, NFKC_CF, NFKC_SCF, UTS46) are now
embedded, bringing generator verification to 31 resources. Normalizer2Impl.load
ports the version-5 header, thresholds, FAST/16 norm trie, padding, mapping/
composition text and smallFCD loading. getNorm16/getRawNorm16 retain the original
lead-surrogate code-point distinction. Reloading retains the original loader
behavior; normalization algorithms are not yet exposed.

JVM tests compare every loaded threshold, mapping text, smallFCD byte and all
Unicode trie values with ICU4J for all five resources. Common tests load each
resource and verify surrogate behavior. Reordering, decomposition/composition
and the public normalization facade remain pending.

## Normalization value classification and combining classes

Normalizer2Impl now ports composition quick-check classification, algorithmic/
composition-no and decomposition-yes predicates, combining-class extraction
(including mapping CCC words), code-point shortcuts and smallFCD lead masks.
Fixed norm16 constants and direct class helpers are shared by the upcoming
reordering and normalization algorithms.

JVM tests compare these operations for every Unicode code point in all five
normalization datasets, every BMP smallFCD lookup and all 16-bit direct class
helper inputs. Common tests read accents, inert characters and decomposition
classification. Full FCD, reordering and normalization remain pending.

## FCD computation

Normalizer2Impl now ports FCD computation from norm data and its smallFCD fast
path, algorithmic delta mappings, variable mapping CCC words and Hangul LVT
handling. The internal maybe/nonzero-class and composition-yes trailing-class
helpers are in place for the normalization loops.

JVM tests compare FCD and direct-data FCD for every Unicode code point and
adjacent invalid values across all five datasets, plus trailing-class helper
results within its preconditions. Common tests cover accents, decomposing
characters, Hangul and lead surrogate code points. Complete normalization
algorithms are still pending.

## Single-code-point decompositions

Normalizer2Impl now ports getDecomposition and getRawDecomposition through
algorithmic deltas, Hangul, mapping lengths, optional CCC words and raw mapping
encodings. Missing mappings return null, while deletion mappings return an
empty string. Recursive mappings are read from the original precomputed data;
raw mappings preserve their original one-step form.

JVM tests compare both operations for every Unicode code point and adjacent
invalid values across all five normalization datasets. Common tests cover
accents, compatibility ligatures, Hangul raw/recursive differences and deletion.
Full-string decomposition/reordering and composition remain pending.

## Normalization boundaries and inertness

Normalizer2Impl now ports decomposition, composition and FCD before/after
boundaries and inert predicates, including contiguous composition, algorithmic
delta trail classes and variable mapping lead/trail classes. Norm16-level
decomposition boundary helpers are available for the upcoming string loops.

JVM tests compare all predicates for every Unicode code point and adjacent
invalid values in all five datasets and both contiguous modes. Common tests
cover accents, composing starters and inert punctuation. Full-string ordering
and normalization are still pending.

## Canonical reordering buffer

Normalizer2Impl.ReorderingBuffer now ports canonical combining-class insertion,
code-point and sequence appends, zero-class fast paths, initial destination
state, flushing, suffix removal and backward class iteration. StringBuilder
destinations are updated directly; other Appendables receive buffered output
on flush. Equal-class marks retain their original order.

JVM tests compare random mutation schedules in both destination modes against
ICU4J, including supplementary and malformed units, marks and destination reuse.
Common tests verify reordered accents and starter boundaries. Full-string
normalization loops and composition remain pending.

## Full-string decomposition and append

Normalizer2Impl now ports the original decomposition scan with BMP/supplementary
fast paths, quick-check boundary mode, canonical reordering, algorithmic and
Hangul mappings, destination reset and decomposeAndAppend boundary merging.
The append-only mode retains its upstream precondition that input is already
appropriately decomposed; it merges classes at the join without decomposing.

JVM tests compare random strings and slices across all five normalization
resources, including malformed UTF-16, quick-check spans and both append modes.
Common tests cover reordering, Hangul and append-boundary merging. Composition,
FCD string processing and the public normalization facade remain pending.

## Pair composition

Normalizer2Impl now ports composePair and the original packed composition-list
search, including pair/triple entries, supplementary trails, forward-combining
metadata, algorithmic Jamo L+V and Hangul LV+T composition. Missing and invalid
pairs retain -1. Composition-list pointer helpers are ready for recomposition.

JVM tests compare all two-code-point raw decompositions and 100,000 arbitrary
pairs per normalization dataset with ICU4J. Common tests cover table-based
accent composition, both Hangul steps and rejected pairs. Full-string
recomposition and composition scanning remain pending.

## Canonical recomposition

Normalizer2Impl.recompose now ports the original state machine over an NFD
buffer: forward-starter tracking, blocking classes, contiguous-mode blocking,
Hangul L+V(+T), packed pair composition, supplementary starter resizing and
buffer flushing. It retains the original supplementary-to-BMP replacement
branch rather than silently changing upstream behavior.

JVM tests compare 600 random decomposed buffers per dataset, both contiguous
modes and nonzero recomposition start offsets with ICU4J's original private
method. Common tests cover Hangul, accents and discontiguous composition.
The main composition scan and public normalization facade remain pending.

## Composition quick check

Normalizer2Impl.composeQuickCheck now ports the original packed span/maybe
result, fast BMP/supplementary scan, backward boundary adjustment, canonical
class order checks and FCC contiguous trailing-class checks. Span mode stops
before maybe characters; full quick check retains the maybe flag.

JVM tests compare every slice of random mixed/malformed UTF-16 strings across
all five datasets and both contiguous/span modes. Common tests distinguish
packed yes/maybe/no results. The main composition scan and facade remain pending.

## Normalization boundary scans and short decomposition

Normalizer2Impl now ports forward/backward composition and FCD boundary scans,
short-segment decomposition with optional boundary stopping, and previous
trailing-class lookup. These retain the original code-point traversal and
contiguous-mode rules and feed the main composition/FCD loops.

JVM tests compare the original private methods over random mixed/malformed
strings at every starting position for all five datasets and mode combinations.
Common tests exercise stopping before starters and canonical ordering in short
segments. Main composition and FCD string processing remain pending.

## Full-string FCD processing

Normalizer2Impl.makeFCD and makeFCDAndAppend now port the original fast scan,
deferred trailing-class lookup, supplementary repair, safe-boundary tracking,
local decomposition of out-of-order spans and append-boundary merging. Null
buffer mode returns the FCD quick-check boundary without modifying text.
Already-safe text retains its original composed form.

JVM tests compare random mixed/malformed strings at every start, quick-check
mode and both append modes across all five datasets. Common tests verify local
repair without decomposing safe spans and append-boundary repair. Main
composition scanning and the public facade remain pending.

## Full-string composition

Normalizer2Impl.compose and composeAndAppend now port the original composition
scan, algorithmic and explicit mapping fast paths, empty mappings, Hangul L/V/T
composition, canonical-order checks, FCC trailing-class checks, local
boundary-based decomposition/recomposition, isNormalized comparison and append
boundary merging. The original fast paths are retained rather than substituting
an unconditional decomposition pass.

JVM oracle tests compare output and isNormalized status on mixed, random Unicode
and malformed UTF-16 strings, including nonzero starts, both contiguous modes,
all five normalization datasets and both append modes. Common tests cover
canonical composition, Hangul and composition across an append boundary.
The public Normalizer2 facade and canonical-iteration data remain pending.

## Normalization property range enumeration

Normalizer2Impl.addLcccChars and addPropertyStarts now enumerate the original
same-value normalization trie ranges with fixed inert lead surrogates. The port
preserves algorithmic-decomposition FCD change points and the Hangul LV/LV+1
boundaries. Both methods add to the caller's existing UnicodeSet.

JVM tests compare every resulting range endpoint with ICU4J for all five data
files. Common tests verify combining-mark membership, exclusion of composed
starters and lead surrogates, all Hangul LV boundaries and preservation of
existing set members. Canonical-iterator property ranges remain pending.

## Public Normalizer2 and mode dispatch

The Normalizer2 API now exposes the six standard/casefold factories, all four
modes, immutable data-backed implementations, normalization to StringBuilder or
Appendable, append boundary repair, decomposition and composition-pair access,
combining classes, quick checks, boundaries and inertness. Norm2AllModes also
ports the no-op implementation and internal normalization-property dispatch.
The original String quick-check fast path is retained.

Common loading accepts BinaryBuffer in place of Java InputStream. Built-in
instances are lazy; custom names are cached using atomic immutable-map updates.
This preserves name-based reuse and safe publication without a JVM lock. Custom
cache entries are strongly retained because Common Kotlin has no portable soft
references. The legacy Normalizer currently contains only the shared identity
QuickCheckResult tokens; its remaining API is not yet ported.

Oracle tests exercise all five data files and all four modes with mixed random
Unicode/malformed UTF-16, immutable/mutable sources, normalized append and
normalize-second append, properties and custom binary data. Common tests cover
standard forms, casefold variants, quick-check tokens, custom name caching,
streaming Appendable output and rejection of aliased source/destination.
A JVM concurrency test races eight loads of one custom name and verifies shared
instance identity and normalization results after publication.

## Filtered normalization

FilteredNormalizer2 now ports the complete original Normalizer2 wrapper: SIMPLE
UnicodeSet spans, unchanged out-of-filter text, filtered property queries,
quick-check aggregation, streaming output and merging only the in-filter suffix
and prefix on append. Both the normalizer and the filter are retained by
reference, including string-element span behavior and mutable-filter updates.

Common tests cover filtering, append boundaries, string elements, retained set
changes and alias rejection. JVM oracle tests compare all four modes, mutable
and frozen sets, filters with and without strings, malformed UTF-16, both append
operations, properties and Appendable output.

## Normalization-backed character properties

UCharacterProperty now implements internal normalization dispatch for canonical,
leading and trailing combining classes, four normalization quick checks and
maximum values, full composition exclusion, four inertness properties and the
changes-when-casefolded/NFKC-casefolded properties. Canonical decomposition before
casefold testing follows the original algorithm; per-call builders replace the
shared JVM scratch builder.

JVM tests compare all fourteen properties for every Unicode code point,
including surrogate values, and all seven integer maxima. Common tests exercise
composition exclusions, class values, inertness, multi-character folding,
negative-input casefold protection and default-ignorable removal. Public generic
property dispatch and canonical segment-starter data remain pending.

## Integer property table and public bounds

UCharacterProperty now unifies all 27 enumerated integer-property entries,
including Script, through getEnumeratedIntPropertyValue. The generic public
value dispatcher also accepts binary property IDs and remains pending until its
canonical-iteration dependency is implemented. No incomplete public value API
is exposed.

The complete getIntPropertyMaxValue dispatcher and UCharacter min/max APIs are
ported, including binary maximum 1, undefined maximum -1 and minimum 0 for every
ID. JVM tests compare 20,000 random code points per integer property, surrogate
and invalid-input boundaries, and all property bounds from -10 through 0x8000.
Common tests cover routing between data sources and undefined-property bounds.

## Mutable code point trie: mutation and range iteration

MutableCodePointTrie now ports the original mutable block representation:
ALL_SAME/MIXED flags, 64-entry BMP allocations, 16-entry supplementary blocks,
512-code-point high-start growth, original data capacity growth, point/range
writes, filtered range scans, deep cloning and fromCodePointMap. Values retain
all 32 bits. Invalid lookups return the error value; invalid writes throw.

JVM tests compare randomized interleaved point/range writes and filtered ranges,
then exhaustively compare all Unicode values and independent clone/map copies.
Common tests cover block boundaries, supplementary/high-end writes, filtered
range merging and clone independence. Immutable building/compaction is still
pending; this phase does not replace it with another representation.

## Trie compaction block algorithms

MutableCodePointTrie now includes the original block equality/search/overlap
helpers, value masking, backward high-start scan, AllSameBlocks reference-count
tracker with least-used eviction, and MixedBlocks prime-sized hash tables with
partial-hash/index packing and double-hash probing. Both integer and UTF-16
index-block paths are ported, including table reuse and incremental extension.

JVM tests invoke ICU4J's original internal block trackers for direct comparison
across table sizes, block lengths, negative integer hashes, mixed array widths,
incremental extension and repeated initialization. Constant-block tests exercise
capacity overflow, eviction and most-used selection. Common tests cover append
tracking, first-occurrence lookup, table reset and constant-block eviction.
The main immutable compaction/build passes are still pending.

## Trie data compaction passes

MutableCodePointTrie.compactWholeDataBlocks and compactData now port the original
whole-block classification/deduplication, constant-block overflow fallback,
capacity estimation, linear ASCII offsets, fast-to-small block transition,
constant/mixed block lookup and adjacent overlap. The data-null block safeguard
avoids reusing the start of a partially constant fast block.

JVM oracle tests compare capacity, every block flag/index entry, selected null
block, final data length and the complete data array after both original
internal passes. Fixtures cover both fast-range sizes, supplementary/high-end
ranges, more than 32 constant values, random 32-bit values and mixed ASCII.
Index compaction and final immutable building remain pending.

## Trie index compaction

MutableCodePointTrie.compactIndex now ports the fast-index condensation,
index-3 null/fast/16-bit/18-bit classification, packed upper-bit groups,
shortened final index-2 blocks, index-1 offsets, index-block deduplication and
overlap, plus the original index-size limit check. It retains the original
index-table layout and scratch-array behavior.

The data-pass oracle test now continues through index compaction and compares
the complete index16 array, mutable indexes, flags, index length and null offset.
Additional fixtures assign 131,072 unique values to force 18-bit data offsets;
both FAST and SMALL fast-range sizes are exercised. The compactTrie orchestration
and final immutable builder remain pending.

## Complete immutable trie builder

MutableCodePointTrie now exposes buildImmutable with the original compactTrie
orchestration and final FAST/SMALL 8/16/32-bit construction. It finds and rounds
highStart, pins the fast range, chooses the null block, enforces data limits,
masks narrow values, aligns indexes/data, appends high/error values and clears
the builder in finally. Invalid null parameters are rejected before clearing.
As in ICU4J, clearing restores the original initial value but keeps an error
value that was masked by a narrow build.

JVM tests compare the complete serialized binary and every Unicode lookup for
all six type/width combinations over empty, BMP, supplementary, high-tail,
high-end and 18-bit-offset fixtures, then compare a second build after reuse.
Common tests verify value truncation, binary round-trip, builder reset, immutable
independence and null-argument behavior.
A limit-exceeding fixture compares the thrown exception, cleared state and a
successful subsequent build with ICU4J.

## Canonical iteration data

Normalizer2Impl now builds canonical-iteration data with MutableCodePointTrie
and the original one-way mapping traversal, singleton/start-set encoding,
non-starter flags and forward-composition flags. It ports isCanonSegmentStarter,
getCanonStartSet, recursive composition-list expansion, Hangul syllable ranges
and canonical property-start enumeration. SEGMENT_STARTER is connected to the
normalization binary-property helper.

A lazy immutable data holder replaces synchronized JVM initialization, keeping
trie and start-set publication together. Calls requiring explicit initialization
still fail before ensureCanonIterData, matching the original contract. JVM tests
compare every Unicode segment-starter flag and canonical start set, including
range endpoints and untouched destinations for absent sets, plus property
starts and invalid lookups. Common tests cover initialization, composed Latin
characters, Hangul and non-starters.

## Public generic character-property dispatch

UCharacterProperty now routes all 76 binary properties and the generic integer
API, including binary values, all 27 enumerated values, the general-category
mask and undefined IDs. UCharacter exposes both hasBinaryProperty overloads,
getIntPropertyValue and the four Unicode alphabetic/lowercase/uppercase/white-
space predicates. Single-code-point strings route to scalar properties;
multi-code-point strings route only to the relevant emoji string properties.

JVM tests compare every binary property for every Unicode code point, generic
integer dispatch and invalid input outcomes, malformed/supplementary strings,
emoji sequences and convenience predicates. Common tests exercise public
routing, category masks, undefined IDs, normalization and emoji properties.

## CanonicalIterator

CanonicalIterator now ports segmentation by canonical starters, recursive
composition extraction, equivalent-piece permutation, Cartesian-product
iteration, reset/source reuse and the original recursion/result-count guards.
Result order remains unspecified. Its two zero-option canonical equality checks
use the existing NFD normalizer (equal NFD forms), because the legacy
Normalizer.compare API is not yet ported. No casefold or comparison-order options
are involved in these checks. UCharacter.getCombiningClass is also exposed.

JVM oracle tests compare complete result sets for canonical Latin, Greek,
Hangul, supplementary and malformed UTF-16 inputs, reuse/reset and standalone
permutation behavior. Common tests cover Latin/Hangul equivalents, empty input,
output-set retention and the permutation-depth guard.

## Property sources and boundary enumeration

UCharacterProperty now ports all source constants and getSource dispatch for
binary, enumerated, mask, numeric, string and other properties. It also ports
main-trie and additional-vector property starts, original hardcoded boundary
points, shifted Block boundaries, layout boundaries and math/MCM fixed-range
boundaries. Layout missing-data failures use a Common MissingResourceException
with class-name/key accessors in place of java.util.MissingResourceException.

JVM oracle tests compare source IDs from -10 through 0x8000 and complete boundary
sets for each helper and all three layout sources. Common tests cover source
routing, preserved destination entries, hardcoded points and invalid layout
sources. Age access was already present and was retained.

## CharacterProperties sets and maps

CharacterPropertiesImpl now combines inclusion boundaries by source, narrows
integer-property inclusions to actual value transitions and supports cache
clearing. CharacterProperties ports frozen binary-property sets (including
emoji strings) and immutable integer-property maps with the original Script
null value, FAST/SMALL selection and width selection. Atomic immutable-map
caches replace JVM synchronized cache arrays; inclusion sets retain the original
mutable-but-do-not-modify contract.

JVM tests compare every range/string in all 76 binary sets, all 27 integer maps
at every Unicode code point, complete map binaries and all property inclusion
sets. Common tests cover frozen mutation rejection, cached identity, Script
error values, emoji sequences, inclusion cache clearing and invalid property IDs.

## UnicodeSet integer-property application

UnicodeSet.applyIntPropertyValue now ports binary-property sets and code-point
complements, all enumerated property values, general-category masks,
Script_Extensions and Identifier_Type filters. The generic inclusion-boundary
filter scan replaces prior contents, merges true spans and includes the final
Unicode tail. Frozen checks and unsupported-property error ordering follow the
original implementation.

JVM tests compare all binary values (-1..2), every enumerated value through each
maximum plus adjacent invalid values, every script, identifier type and category
mask fixtures. They compare every resulting range/string and frozen/error
outcomes. Common tests cover replacement, script/category/class filters, emoji
string complements and frozen mutation. Property-alias/pattern parsing remains
pending.

## Character-name byte utilities

UCharacterUtility now ports all primitive operations used by the name decoder:
noncharacter detection, two-char integer packing, null-terminated byte-string
read/compare/skip and bounded delimiter scanning. Unsigned byte-to-character
conversion, empty-field handling, returned offsets and negative skip counts
follow ICU4J. Explicit array/string bounds checks preserve exceptions on JS.

JVM oracle tests compare all Unicode noncharacter values, random packed chars
and randomized byte fields/skips/comparisons against the original internal
methods. Common tests cover empty/unsigned fields, truncation and unterminated
input. Character-name resources, decoder and public name lookup remain pending.

## Algorithmic character names

UCharacterName.AlgorithmName now ports range/type metadata, hex-suffix and
factorized name generation, inverse lookup, factor-string traversal and name
character/maximum-length metadata. Per-call scratch buffers replace the original
synchronized shared buffers. Original factor masking and metadata traversal
behavior are retained. Utility.hex(long[, places]) is also ported.

JVM tests compare both algorithm types, every name/inverse lookup in a CJK-range
fixture, factorized combinations, malformed suffixes and metadata bitsets.
Common tests cover CJK-style names, factorized names and setter validation.
The full name data reader and public lookup API remain pending.
Common tests also verify fullwidth/Arabic hex digits and out-of-bounds padding
widths on JVM, JS and Native.

## Name binary data reader

The pinned unames.icu resource is embedded with manifest checksum verification.
UCharacterNameReader ports format authentication and token/group/algorithm-table
reading, including factor arrays, prefixes and factor strings. UCharacterName
now stores these tables, lazily loads its shared instance and exposes algorithm
range/name access. Data setters retain the original acceptance behavior.

JVM tests compare every token/group table entry and every algorithmic name in
all embedded ranges with ICU4J. Common tests cover real CJK/Hangul names,
resource-copy isolation, invalid versions and truncated inputs. Generated data
verification now checks 32 resources. Group-name decompression and public name
lookup remain pending.

## Group character-name decompression

UCharacterName now ports group binary search, MSB/offset helpers, packed-nibble
length decoding, token expansion (including two-byte tokens), alternate-field
selection and code-point group lookup. UCharacterNameChoice constants are
ported. Per-call offsets/lengths/builders replace synchronized shared scratch.
The original signed-byte and field-selection behavior is retained.

JVM tests compare every group's decoded length/offset arrays and every line for
all five name choices through both raw and code-point lookup. Group selection
is compared over all Unicode code points. Common tests cover modern names,
aliases, supplementary names and group boundaries. Extended/generated fallback
names and inverse group lookup remain pending.

## Forward character-name API

UCharacterName now dispatches algorithmic/group names and generates extended
fallback names from the original category-name table, including noncharacters,
lead/trail surrogates and unsigned hexadecimal formatting. UCharacter exposes
getName(codepoint), getName(string, separator), getExtendedName and getNameAlias,
plus the deprecated always-null Unicode_1_Name/ISO_Comment methods. String name
joining retains the original single-character null vs multi-character literal
"null" behavior.

JVM tests compare modern, extended and alias names for every Unicode code point,
all selector/invalid-input fixtures, direct fallback formatting and string
joining. Common tests cover algorithmic names, corrected aliases, every major
fallback category and public null behavior. Inverse name lookup remains pending.

## Inverse character-name lookup

UCharacterName now ports direct tokenized group-name comparison, ordered
algorithm/group/alias lookup and extended-name parsing with category validation,
hex length/range checks and the original sentinel results. UCharacter exposes
getCharFromName, getCharFromExtendedName, getCharFromNameAlias and the deprecated
always-minus-one Unicode_1_Name lookup. Per-call scratch arrays replace shared
synchronized lookup scratch; case normalization uses locale-independent casing.

JVM tests compare raw inverse group matching for every stored name/choice and
public lookup over ordinary, algorithmic, alias, random extended and malformed
names. Common tests exercise all public lookup modes, category validation,
case-insensitivity and null/empty/invalid input. Name-character/length metadata
and name iteration remain pending.

## Character-name metadata

UCharacterName now calculates maximum name/ISO-comment lengths and character
bitsets using the original algorithmic, extended-category and compressed-group
passes. Token lengths are cached with original byte semantics; alternate-field
and ISO-length behavior are retained. A lazy metadata holder replaces shared
initialization scratch. Character-set APIs clear the destination and enumerate
characters 255 down to 1, as in ICU4J.

JVM tests compare both maxima and complete character sets, including calling set
APIs before length APIs and repeated reads. Common tests cover replacement,
extended-name punctuation, repeated conversion and frozen-set rejection.
Character-name iteration remains pending.

## Character-name iteration

ValueIterator and UCharacterNameIterator now port reusable result containers,
ordered group/algorithmic range interleaving, extended-name gaps, range clamping,
reset and exhaustion. UCharacter exposes modern and extended iterator factories
and the original empty deprecated Unicode_1_Name iterator. Per-instance group
scratch replaces static synchronized arrays. setRange preserves the original
cursor behavior; reset explicitly resets algorithm/group cursors.

JVM tests compare full modern/extended traversals, randomly changing ranges,
algorithmic transitions, reset, outside-Unicode ranges and unchanged exhausted
results. Common tests cover public factories, reuse, range validation, fallback
names and the deprecated empty iterator.

## Portable numeric-literal dependency for property aliases

ICUNumberParsing replaces Java Double.parseDouble semantics needed by numeric
property aliases: decimal/hex literals, optional type suffixes, ASCII trim,
signed Infinity/NaN and syntax rejection. Hex conversion extracts significand
bits and applies round-to-nearest/ties-to-even directly, including subnormal,
underflow, signed-zero and overflow cases; decimal conversion uses the Common
stdlib after Java-syntax validation.

JVM oracle tests compare bits and rejection outcomes for random long hex
mantissas/exponents, random Double hex/decimal strings and boundary fixtures.
Common tests cover rounding ties, subnormals and syntax across JVM/JS/Native.
This dependency will be connected by the pending applyPropertyAlias port.

## UnicodeSet property aliases

UnicodeSet.applyPropertyAlias now ports explicit/default XSymbolTable hooks,
loose UCD property/value aliases, numeric CCC fallback, numeric-value filters,
character names, age filters, script extensions, identifier types and empty-value
category/script/binary/special aliases. Name whitespace munging, general-category
mask conversion, Assigned inversion and error paths follow ICU4J. Numeric values
use the portable Java-literal parser. The range set overload and default symbol
table getters/setters are also ported; changing the default clears inclusions.
XSymbolTable's property value accepts null to preserve the original Java hook.

JVM oracle tests cover every binary alias, enumerated boundary aliases, numeric
and name syntax, age, special aliases and rejection cases. Common tests cover
replacement, all major alias branches, hex numeric values, symbol precedence and
null hook values. UnicodeSet pattern parsing remains pending.

## UnicodeSet property-pattern parsing

UnicodeSet now ports iterator property-pattern lookahead and parsing for POSIX [:...:],
\\p/\\P and \\N forms, property/value splitting, inversion, ParsePosition updates
and RuleCharacterIterator advancement/rebuilt text. Syntax errors use the
original escaped iterator context; Utility.escape(String) is ported for this
purpose, including its printable-DEL behavior.

JVM oracle tests compare parsed sets, null/error outcomes, offsets and lookahead
across syntax variants, malformed patterns, names, numeric properties and emoji
complements. Existing string lookahead is reused. Common tests cover both parser entry points, preserved lookahead
position and rebuilt text. The general UnicodeSet expression parser remains
pending; these methods are internal building blocks for it.

## UnicodeSet full/simple case-closure algorithms

UnicodeSet now ports the internal full/simple case-insensitive closure pass,
large-set Case_Sensitive intersection optimization and per-code-point simple
folding of strings. Full closure replaces source strings with folded equivalents
and reverse string closures; simple closure preserves only simple-fold mappings.
The input remains included for code points, following the original algorithm.

JVM tests compare every Case_Sensitive code point, random mixed code-point/string
sets, malformed UTF-16 and the entire Unicode range with ICU4J's full/simple
closeOver modes. Common tests cover long-s/sharp-s closures, string reduction,
untouched simple-fold scratch and frozen rejection. The public closeOver
selector and ADD_CASE_MAPPINGS string branch remain pending until word/titlecase
iteration is available; the general pattern parser still depends on these.

## BreakIterator base and CharSequence cursor

BreakIterator now defines the Common boundary/text/status contract, original
fallback preceding/isBoundary behavior, status-vector defaults, text adapters
and word/kind constants. Concrete iterators must implement clone explicitly in
Common Kotlin, which cannot provide Java Object.clone's generic shallow copy.
Locale factories/service registration and locale metadata remain pending.

CSCharacterIterator ports retained CharSequence traversal, dynamic length,
position validation and independent cloned cursor state. JVM tests compare
cursor operations and inherited BreakIterator defaults/text adapters with ICU4J;
Common tests cover empty/supplementary text, retained text, cloning and errors.
RuleBasedBreakIterator, its caches and dictionary integration remain pending.

## Unhandled dictionary-break fallback

UnhandledBreakEngine now ports script-wide handled-set accumulation and cursor
skipping without adding boundaries. Readers capture one immutable-by-convention
set snapshot; Common AtomicReference publication replaces the JVM volatile field.
The original single-writer requirement for handleChar is retained, as are ignored
start/phrase arguments and pre-existing deque contents.

JVM tests compare membership for all Unicode code points after several scripts,
all start/end combinations on mixed and malformed UTF-16 and unchanged break
queues. A concurrency test runs one publisher with multiple readers. Common
tests cover script accumulation and cursor behavior. Specific dictionary engines
and RuleBasedBreakIterator integration remain pending.

### Myanmar dictionary segmentation

Ported `BurmeseBreakEngine` with the original three-word candidate lookahead,
short-root/prefix combination thresholds, unknown-word resynchronization,
combining-mark attachment and terminal-boundary removal. The original property
set expressions are constructed via property aliases and set intersection.
The engine reads the pinned `burmesedict.dict` directly through the common
binary decoder; the general `DictionaryData.loadDictionaryFor` resource-bundle
lookup remains pending. This is the root bundle's Mymr dictionary, not a
replacement dictionary or segmentation heuristic.

JVM differential tests compare script handling across Unicode and break counts,
queues and cursor positions for Myanmar prose, marks, mixed text and 500 seeded
random strings, including range slices and both phrase flags. Common tests
exercise the embedded dictionary on JVM, JS and macOS Native.

### Lao dictionary segmentation

Ported `LaoBreakEngine` with the original three-word candidate search, short-root
and prefix thresholds, unknown-word resynchronization, mark attachment and
terminal-boundary suppression. Lao-specific begin/end sets retain the prefix
vowel exclusions and digraph consonant ranges from ICU4J. Property intersections
construct the original word and mark sets without requiring the pending general
UnicodeSet expression parser. The pinned root bundle's Lao dictionary is read
from `laodict.dict`; generic resource-bundle dictionary resolution remains pending.

Differential coverage compares script handling for every Unicode code point and
both direct range division and base-class dispatch on Lao prose, combining marks,
mixed text and 500 seeded random strings, with bounded iterators, range slices
and both phrase flags. Common tests exercise the same embedded dictionary on
all locally runnable targets.

### Khmer dictionary segmentation

Ported `KhmerBreakEngine` with ICU4J's complete dictionary candidate search and
unknown-word recovery. The Khmer-specific four-code-unit minimum span, consonant
begin set and COENG exclusion from the end set are retained, alongside combining
mark attachment and terminal-boundary removal. Word and mark sets use the same
property intersections as upstream. The pinned root bundle's `khmerdict.dict`
is decoded directly; generic resource-bundle dictionary resolution is pending.

Differential tests cover all Unicode script handling, Khmer phrases, COENG,
marks, mixed text and 500 seeded random strings. Both direct range division and
base-class dispatch are compared for break counts, queues and cursor positions,
including bounded iterators, short spans and both phrase flags. Common tests
exercise segmentation using the embedded dictionary on JVM, JS and Native.

### Thai dictionary segmentation

Ported `ThaiBreakEngine`, including the original labelled three-word lookahead
(the successful third candidate exits without another backup), unknown-word
resynchronization, mark attachment and PAIYANNOI/MAIYAMOK suffix handling with
previous-character checks. Thai-specific begin/end sets and the four-unit
minimum span are retained. The pinned root dictionary `thaidict.dict` is decoded
in common code; general resource-bundle dictionary resolution remains pending.

Differential tests compare every Unicode code point for script handling and
compare counts, queues and cursors for Thai prose, short spans, mixed text,
500 random strings and dictionary words followed by combinations of repetition
and abbreviation signs. Tests exercise bounded iterators, direct range division,
base dispatch and both phrase flags. Common tests load the embedded dictionary
and segment Thai on every locally runnable target.

### Legacy normalization iteration and source offsets

Ported the stateful `Normalizer` iterator dependency used by CJK segmentation:
forward/backward normalization segments, source-index mapping, cursor resets and
seeks, cloning, text replacement, mode/option changes and text accessors. Modes
NONE/NFD/NFC/NFKD/NFKC/FCD and the Unicode 3.2 filtered variants use the existing
Normalizer2 engines. Mode changes preserve buffered output as in ICU4J. String
normalize, quickCheck and isNormalized entry points are also available.

JVM differential tests exercise complete forward/backward walks, random changes
of direction and mode, seeks inside UTF-16 pairs, cloning buffered segments,
iterator ownership, compatibility expansions and Unicode 3.2 options. Common
tests verify expansion offsets and clone independence across targets. Legacy
Normalizer's remaining static overloads, comparison/concatenation helpers and
compatibility aliases are still pending; this does not mark the class complete.

### Legacy normalization array and concatenation APIs

Ported compose/decompose String and array overloads, range-based normalization,
code-point normalization with the original NFD fast path, array quickCheck and
isNormalized, and normalized concatenation for strings/arrays/ranges. The common
CharsAppendable retains ICU4J's output-counting behavior on overflow, including
its distinction between single-character writes and all-or-nothing sequence
writes. Concatenation allows the left input to alias the destination, rejects
an overlapping right input, and leaves the destination unchanged on overflow.
Compatibility mode aliases and normalization/comparison option constants are
also present; the comparison implementation and FC_NFKC closure remain pending.

Differential tests compare results, exception classes/messages and the entire
output buffer after successful and overflowing operations across all modes,
Unicode 3.2, random UTF-16 slices and compatibility expansions. Common tests
exercise array output, overflow accounting and composition across a concatenation
boundary on all locally runnable targets.

### FC_NFKC closure

Ported both `Normalizer.getFC_NFKC_Closure` overloads with the original full-fold
and NFKC derivation and composition-quick-check fast exit. The destination-array
form returns the required length for null or undersized arrays and copies only
when the complete closure fits. JVM tests compare every Unicode code point with
ICU4J and check capacities around every nonempty result, plus invalid inputs.
Common tests cover compatibility characters whose capital letters are exposed
by normalization, preflight and unchanged undersized destinations. Legacy
canonical comparison remains pending.

### Incremental canonical comparison

Ported all `Normalizer.compare` overloads and the internal `cmpEquivFold` core.
The implementation retains upstream's FCD precheck/partial normalization,
Turkic NFD prepass, Unicode 3.2 option shift, two-level folding/decomposition
stacks, supplementary-character rewind and surrogate-aware code-point-order
fixup. Symmetric per-source state encapsulates the original stack/cursor logic;
the comparison is incremental and preserves ICU4J's exact numeric result.

Differential tests compare all changing canonical/full-fold mappings across
Unicode, random mixed/malformed UTF-16, arrays/ranges and code-point overloads.
The internal core is also checked against ICU4J with canonical equivalence
turned off, including the options needed by UTF16.StringComparator. Common tests
cover canonical equality, caseless and Turkic matches, Hangul and surrogate order.

CanonicalIterator now calls the ported incremental `Normalizer.compare` for
canonical equality, replacing the earlier equivalent whole-string NFD equality
fallback. Its existing ICU4J differential tests exercise this integration.

### UTF16 string comparator

Ported `UTF16.StringComparator`, including default/configurable construction,
null ordering, mutable comparison options, code-unit prefix comparison,
surrogate-aware code-point ordering and full case folding through cmpEquivFold.
The invalid-fold-option setter retains ICU4J's partial state change: the
ignore-case flag changes before validation, while the previous fold option
remains. This comparator does not implicitly enable canonical equivalence.

Differential tests compare exact integer results for all option combinations,
nulls, expansions, supplementary mappings and random/malformed UTF-16. Common
tests cover ordering, Turkic folding and invalid-setter state across targets.

### UTF16 substring search and replacement

Ported string-needle indexOf/lastIndexOf overloads and string replacement. The
surrogate-boundary filters, recursive retry positions and original-source
replacement index accounting follow ICU4J. In particular, the no-fromIndex
search overloads retain upstream's extra lookahead unit for a needle ending in
a lead surrogate; this differs from the explicit-fromIndex overloads and can
throw at the end of the source. Empty needles also retain the original bounds
exception. Explicit checks preserve these behaviors on JS as well as JVM/Native.

Differential tests cover random/malformed UTF-16, surrogate fragments, empty
needles, negative/oversized starting positions and replacements. Common tests
include complete supplementary matches, rejected trail fragments and the
original no-offset lookahead behavior.

### Binary resource-bundle values

Started `ICUResourceBundleReader` with the ResB 1.1–3 header/index decoder,
attributes and pool checksums; legacy/compact strings, aliases, binary values,
integer vectors, array32/array16 and table1632/table16/table32; resource-word
integer helpers and sorted key lookup. Pool key and string offsets are resolved
through an explicitly supplied pool reader. Reads share immutable input bytes
and use independent cursors. Locale loading/fallback, reader/resource caches,
UResource value adapters and the full bundle facade remain pending.

Embedded original `brkitr/ja.res` and `brkitr/root.res` with manifest checksums
(34 resources total). JVM differential tests recursively compare their raw
trees with ICU4J, including handles, keys, values and container lookups. Common
fixtures exercise every container width, scalar payloads, length-prefixed
strings, pooled keys/strings and missing/mismatched pool validation. These tests
do not claim coverage of the unported locale fallback or cache layers.

### Dictionary lookup through binary resources

Connected `DictionaryData.loadDictionaryFor` to the original root break bundle's
`dictionaries` table and the common dictionary decoder. The Myanmar, Lao, Khmer
and Thai engine constructors now resolve their script keys through this table,
removing their temporary per-engine filename mappings. Both byte and UTF-16
tries are selected from the actual dictionary indexes. Missing script keys
throw the common MissingResourceException.

Differential tests enumerate every root dictionary entry, load it through ICU4J
and the common path, and compare matcher type, candidate lengths/values, counts
and cursor positions on Southeast Asian/CJK/supplementary text. Unknown and
case-mismatched script names are checked too. Common tests run script lookup on
all locally runnable targets. This reads the root-defined dictionary table;
general locale/default-locale resolution and resource fallback remain pending.

### Default CJK dictionary word and phrase segmentation

Ported the default `CjkBreakEngine` path: NFKC and legacy Normalizer source-index
mapping, dynamic-programming word costs, unknown-character fallback excluding
Hangul, Katakana run costs, backtracking, duplicate normalized-offset suppression
and phrase punctuation boundaries. Japanese phrase skip strings are loaded from
the original ja.res extensions and the Hiragana property set. Korean and CJ
character coverage and engine equality are retained.

Differential tests compare Unicode-wide handles plus counts, queues, cursor
positions and original exceptional behavior for Chinese/Japanese/Korean prose,
compatibility expansions, supplementary/malformed UTF-16, Katakana runs, random
slices, preexisting boundaries, direct division/base dispatch and word/phrase
modes. Common tests exercise dictionary costs and NFKC on local targets.

The optional ICUConfig-controlled ML phrase path and MlBreakEngine/model loading
remain pending. This class currently ports the original default dictionary path;
it does not substitute a dictionary heuristic for the ML configuration or claim
that the whole segmentation stack is complete.

### Japanese AdaBoost phrase model and engine

Ported `MlBreakEngine`: thirteen feature maps, negative-sum bias, six-code-point
window indexing, unigram/bigram/trigram scoring, source-position correction and
phrase punctuation rules. CjkBreakEngine dispatches Japanese phrase requests to
this engine when its explicit `useMlPhraseBreaking` constructor option is true;
word and Korean dispatch retain the original paths. Platform ICUConfig/system
property integration remains pending; Common code currently exposes the option
explicitly rather than reading JVM system properties.

ICU4J 78.3's distributed data omits jaml.res. The pinned ICU source tag contains
`icu4c/source/data/brkitr/adaboost/jaml.txt`; it is retained with SHA-256
845816d923c9b376a91c7eaf18d8e5f7c64903f30b4c62f5292f7c0db5d1e779.
`scripts/generate-ml-model.py` validates and compiles all 1433 original features
into deterministic ResB 2 string/array32/int-vector/table32 data, requiring no
native genrb. It also generates the JVM oracle resource so ICU4J can execute its
own ML path using the same original model. CI checks both generated artifacts
and the common embedding manifest (35 resources).

Raw model resource trees are compared with ICU4J's reader. CJK/ML differential
tests compare word/phrase and Korean/Japanese dispatch, normalization, random
slices, supplementary/malformed text, queues and cursor positions. Common tests
exercise the Japanese model on JVM, JS and macOS Native. This does not complete
ICUConfig or the remaining RuleBasedBreakIterator/cache/factory stack.

### RuleBasedBreakIterator dictionary cache

Ported the complete DictionaryCache state and algorithms into
`RBBIDictionaryCache`, with explicit text/rule-data/language-engine dependencies
for the pending iterator owner. Population uses the original dictionary category
threshold and engine dispatch, inserts range endpoints only when dictionary
breaks exist, and retains the early return for spans of at most one code unit.
Sequential/random following and preceding preserve cache positions, first/other
rule statuses and stale output values after misses/reset. Copying clones the
deque and rebinds owner dependencies without sharing cursor state.

JVM tests inspect ICU4J's actual internal cache and compare every state field,
boundary queue, status and text cursor while populating word/line ranges,
walking in both directions, seeking randomly, resetting and cloning. Coverage
includes CJ/Korean/Lao/Khmer text, mixed supplementary text and phrase mode.
Common tests verify endpoint statuses, short-span retention and copy independence.
The outer RuleBasedBreakIterator, main BreakCache and global engine registry
remain pending. Thai/Myanmar automatic dispatch also has an upstream LSTM path;
that dependency has not yet been ported or claimed by these cache tests.

### Main circular break cache

Ported all boundary-cache algorithms into `RBBIBreakCache`, bound to the existing
rule engine and dictionary cache: 128-entry ring, asymmetric eviction, retained
iteration positions, binary search across wraparound, sequential navigation,
remote safe-point refill, forward prefetch and backward side-buffer refill.
Owner position/status/done state is published with the original timing, including
fast-path behavior. Copies duplicate arrays and use a fresh transient side buffer.

Differential tests inspect ICU4J's actual BreakCache and owner state after every
operation, comparing complete boundary/status arrays, ring indexes, text cursor,
rule status, dictionary counts and done flags. Character/word/line/sentence/title
rules cover long wraparound, remote/random seeks, dictionary mixed text, both
phrase flags, cloning and direct insertion/retention limits. Common tests walk
400 boundaries and refill remotely in both directions. The public iterator
facade, rule compiler, global engine registry and locale factories remain pending.

### LSTM model decoding

Ported LSTMBreakEngine's model structures, embedding/class enums and complete
nine-block matrix/vector decoding into `LSTMData`. Resource scalars, dictionary
strings and raw int-vector payloads come from the common ResB reader. Float bits
are reinterpreted directly, preserving the original row-major block order,
unknown-token embedding row and last-index-wins duplicate dictionary behavior.
Added the common UResourceTypeMismatchException for typed resource failures.

Differential tests instantiate ICU4J LSTMData with independently supplied model
values and compare every matrix/vector float bit across random dimensions and
payloads, including NaNs. Common tests cover signed zero, infinities, subnormals,
layout and embedding types through binary resources. Inference, both vectorizers,
default-model/resource loading and automatic Thai/Myanmar dispatch remain pending;
this step ports their model-decoding dependency, not a replacement segmenter.

### Bidirectional LSTM inference and token vectorization

Ported LSTMBreakEngine's complete recurrent computation, backward-first storage,
merged forward/output pass, BIES class selection, script handling and engine
factory from supplied model data. Both vectorizers retain upstream behavior:
CODE_POINTS consumes UTF-16 units, while GRAPHEME_CLUSTER uses original compiled
character rules through the ported rule engine/cache and retains next(start)'s
boundary-count interpretation. Default model selection and trained-resource
embedding remain pending.

Float products explicitly round to binary32 before accumulation so Kotlin/JS
preserves Java's separate multiplication/addition operations. Differential tests
compare recurrent hidden/cell bits, token offsets/indices, cursor state and
break queues against ICU4J for random finite model weights, both vectorizers,
range slices, malformed/supplementary text and both phrase flags. Common tests
exercise graph-cluster and unit tokenization with deterministic SINGLE output.

A common cancellation fixture uses saturated gates and output bias to distinguish
binary32 product rounding from a fused/wider multiply-add: correct rounding ties
SINGLE with INSIDE and preserves the earlier INSIDE class. This exercises the
rounding-sensitive decision on JS and Native, beyond JVM bit comparisons.

### Default trained Thai/Myanmar LSTM resources

Ported `LSTMBreakEngine.createData(script)` selection through root.res's original
lstm table, preserving unsupported-script null results and missing-model errors.
Embedded the exact default Thai_graphclust_model4_heavy and
Burmese_graphclust_model5_heavy models from the pinned ICU tag. Their source text
is retained; `scripts/generate-lstm-models.py` checks source SHA-256, validates all
matrix dimensions and compiles deterministic ResB resources without native genrb.
CI verifies regeneration, JVM oracle copies and common embeddings (37 resources).

JVM tests load the same trained resources into ICU4J, compare every weight bit,
dictionary and model type, then compare segmentation counts/queues/cursors on
Thai/Myanmar prose and range slices. Common tests load and execute both trained
models on all local targets. The global language-engine registry and public
RuleBasedBreakIterator owner remain pending; these resources now make original
LSTM selection available for that integration.

### Language break-engine registry

Ported RuleBasedBreakIterator's script dispatch and reuse into
`LanguageBreakEngineRegistry`: Thai/Myanmar trained LSTM with missing-resource
fallback to dictionary engines, Lao/Khmer dictionaries, shared CJ handling of
Han/Hiragana/Katakana, Korean handling and the shared unhandled-script fallback.
Corrupt dictionary IO returns no engine as in upstream. A shared default registry
is available for the pending iterator owner; the ML option remains explicit.

Common publication uses CAS on an immutable snapshot containing both the engine
list and fallback script set. This preserves the original fallback-first
precedence without platform locks, lost script updates or duplicate published
engines. Racing constructors can do duplicate transient work, but only one
engine is published/reused. UnhandledBreakEngine can read a registry-owned
snapshot; its existing standalone single-writer handleChar behavior is unchanged.

Tests compare dispatch and segmentation with ICU4J's private registry, verify
Common/CJ creation-order precedence, and exercise eight concurrent writers/readers
for stable engine identity and retention of every unsupported script. Common
tests execute all dispatch branches on the locally runnable platforms. The
public RuleBasedBreakIterator owner and ICUConfig integration remain pending.

### Public compiled-rule iterator

Added public `RuleBasedBreakIterator` backed by the original compiled rule engine,
both caches and shared language registry. It supports first/last/current,
next(n), forward/backward navigation, following/preceding/isBoundary, status
vectors, direct CharacterIterator ownership, text replacement, equality/rules
hashing and independent cloning. Rule engine copies preserve scalar state while
resetting lookahead storage as ICU4J does; dictionary cache text binding is
updated without discarding the original reset semantics.

Compiled input uses the Common BinaryBuffer counterpart of ByteBuffer. The
phrase/ML options are explicit while ICUConfig is pending. String-rule
construction/compilation, locale factories and legacy nullable-text behavior
are not yet exposed; the class is not claimed complete.

JVM differential tests exercise all 15 pinned rule sets, bounded iterators,
trained Thai/Myanmar dispatch, other dictionary scripts, malformed/supplementary
text, complete walks, every nearby offset, random operations, status vectors and
clones. Common tests exercise word statuses, mixed dictionary text, supplementary
boundary rejection and CharSequence text replacement on local targets.

Bounded-input phrase tests also preserve ICU4J's exceptional CJK punctuation
behavior when previous32 returns DONE32 at a nonzero begin index. Differential
operations compare exception families and post-exception cursor/status state,
rather than assuming every valid-range phrase request succeeds upstream.

### Public UnicodeSet case closure

Ported `UnicodeSet.closeOver` option dispatch and the complete ADD_CASE_MAPPINGS
branch: original code-point lower/title/upper/full-fold mappings, large-set
case-sensitive filtering, retained input strings and root string mappings.
Root titlecasing now uses the ported compiled word iterator with CaseMapImpl's
existing title adapter. The full/simple case-insensitive branches reuse the
previously verified internal closures. Frozen sets reject even no-op options,
and the original CASE_MASK selects exactly one of the three modes.

Differential tests cover all option bit combinations, mixed strings (including
multiword titlecasing and dictionary scripts), random sets, every case-sensitive
code point and the complete Unicode range. Common tests distinguish case closure
from one-step mappings and exercise root word-title mappings across platforms.
The general UnicodeSet expression parser and locale case APIs remain pending.

### String-range parser dependency

Ported StringRange's complete expansion and both compaction algorithms, plus the
common ICUException base type. Expansion preserves prefix alignment, recursive
Cartesian code-point ranges, validation order/messages and output identity.
Fast compaction retains source iteration order and final-code-point grouping;
moreCompact groups by code-point length and performs the original descending
pivot merges, preserving sorted output and shortened end strings. Empty and
malformed input behavior is retained rather than silently normalized.

Differential tests compare expansion results and partial output on failure,
random compaction pairs/order for both modes, and exact int-array comparator
results. Common tests cover rectangular ranges, shortened endpoints and
supplementary characters. This supplies the string-range dependency of the
pending general UnicodeSet parser; that parser is not yet marked complete.

### Recursive UnicodeSet expression parser core

Ported the original recursive applyPattern core, including nested sets,
union/intersection/subtraction, strict character ranges, properties, escaped
literals, strings and string ranges, anchors, variable expansion, matcher
stand-ins, case options before complement and the 100-level depth limit.
Rebuilt pattern selection and parse-cursor behavior follow the original core.
String-range expansion writes through a sorted unique sink so partial mutations
on errors match ICU4J's TreeSet behavior.

Differential tests invoke ICU4J's actual private parser and compare set ranges,
strings, rebuilt pattern, cursor position, variable state and error messages on
valid/invalid expressions, random grammar combinations and nesting limits.
Common tests exercise nested algebra, ranges, closure, anchors and complement.
Public String/ParsePosition overloads, constructors and retained-pattern caching
remain pending; this step does not claim the whole UnicodeSet parser API complete.

### Public UnicodeSet pattern API and retained patterns

Added String/boolean/options/ParsePosition constructors and all public
applyPattern overloads, including whole-input validation and variable remainder
checks. Retained rebuilt patterns feed toPattern(false); toPattern(true) follows
ICU4J's backslash-aware escaping of cached unprintable characters. Copies retain
patterns, and compact/freeze preserve them. Core range algebra and individual
mutators invalidate at the original points, including no-op distinctions for
existing characters/strings, empty ranges and string removal.

Differential tests compare constructors, partial parses, failed-parse state,
positions, both pattern forms and 25 mutation paths, followed by copy/thaw/freeze.
Common tests use the public API for nested expressions, case options, positions
and cache preservation/invalidation. The rule compiler and remaining unrelated
UnicodeSet APIs are still pending; public pattern parsing is now backed by the
ported original grammar rather than a limited compatibility parser.

Public SymbolTable tests additionally check cached matcher stand-ins, property
variables and rejection of extra characters remaining in a variable value,
including parse positions and retained/cleared patterns after failures.

### Rule compiler grammar table

Ported RBBIRuleParseTable's 104 rows, 32 actions and character-class constants.
The Common table is generated from the exact pinned ICU rbbirpt.txt grammar,
retained under data/compiler with SHA-256 verification. The Python generator
resolves state/push labels, preserves row order and first-match priority, and
assigns action/class numbers as the original ICU generator. CI checks regeneration.

JVM tests compare every table field and constant with ICU4J's actual compiler
table. A Common grammar trajectory test exercises a literal rule's consume,
push/pop and action sequence across local targets. Scanner actions, syntax-tree
construction, set categories, DFA construction and serialization remain pending;
this ports the compiler's original grammar data, not a substitute compiler.

### Rule compiler syntax nodes

Ported RBBINode's node types/precedence, metadata and position sets, copy
constructor, variable-expanding tree cloning, flattenVariables depth guard,
flattenSets, preorder node discovery and original debug formatting. UnicodeSet
nodes and input sets retain upstream sharing, while copied position containers
remain independent. Copy intentionally clears rule-root/lookahead-end flags;
flattenVariables restores the variable root's rule-root/chaining flags. Debug
serial allocation uses a Common atomic counter for concurrent compiler sessions.

Differential tests compare randomized expression trees and position references,
variable/set expansion, parent values, discovery order, shared-node behavior,
copy flags and debug output against ICU4J's actual private node class. Common
tests verify ownership, independent containers and depth/type errors on local
targets. Scanner actions and DFA/set-category construction remain pending.

### Rule compiler symbol table

Ported RBBISymbolTable variable definitions and node lookup, variable-chain
resolution, one-shot U+FFFF UnicodeSet matcher handoff, Unicode identifier
scanning, duplicate/malformed-set errors and debug dump. The scanner supplies
an error callback; the full scanner remains pending. Failed name scans retain
upstream's empty-string result and unchanged position. Missing variables and
unrelated matcher queries leave an outstanding set handoff intact.

JVM differential tests use ICU4J's actual private symbol table and syntax nodes
with a real scanner owner. They compare supplementary/invalid identifier scans
at every offset and limit, alias chains, matcher consumption, UnicodeSet parser
integration and error codes. Common tests cover duplicate-definition ownership,
returning error callbacks and reference positions. JVM, JS Node and macOS Arm64
test suites pass.

### Rule compiler scanner and syntax-tree construction

Ported the original RBBIRuleScanner state machine and all parse actions:
operator precedence, assignments/references, set interning, literal/quoted and
escaped input, comments, lookahead/end marks, rule tags, chaining, direction
and literal-mode options. It retains original line/column errors, bounded
stacks, source offsets, empty-set checks and debug traces. Compiler error codes
are shared with the symbol table. RBBIRuleParseResult holds the original
front-end owner state for subsequent category/DFA stages; global ICUDebug
configuration is not yet ported, and scanner debugging is explicitly supplied.

Differential tests compare all 15 embedded ICU rule sources, 250 generated
valid expressions, 400 random malformed inputs and focused edge cases against
ICU4J's actual private scanner. Graph comparison checks node metadata and a
bijection of object identities over child/parent links, shared set nodes and
variable definitions. Tests also compare options, counters, stripped comments,
error messages/locations and low-level quote/escape/newline tokenization.
Common tests cover tree flags, tags, variable ownership and source offsets.
Full JVM, JS Node and macOS Arm64 suites pass. Set-category partitioning, DFA
construction and compiled-data emission remain pending.

### Rule compiler character categories and trie

Ported RBBISetBuilder's linked range splitting, grouping by ordered original-set
membership, dictionary category placement, category-expression trees, special
{bof}/{eof} symbols, equivalent-category merging and first-character lookup.
Trie construction uses the ported MutableCodePointTrie and the original FAST
8/16-bit width selection; freezing releases the mutable trie and serialization
returns Common bytes. Range/set/group debug output retains upstream formatting.

Differential tests compare complete range lists, membership and dictionary/group
flags, category trees/parents, merge results, debug dumps and byte-for-byte trie
serialization against ICU4J. Coverage includes all 15 embedded rule sources,
100 generated overlapping-set rules, dictionary alias/interning cases, Unicode
endpoints and both sides of the 255-category width boundary. Common tests check
range coverage, dictionary lookup, BOF/EOF behavior, split ownership and frozen
trie reuse. Full JVM, JS Node and macOS Arm64 suites pass. DFA construction and
complete compiled-rule serialization remain pending.

### Rule compiler DFA and safe reverse table

Ported RBBITableBuilder's complete forward-table pipeline: variable/set
flattening, synthetic BOF/end nodes, nullable/first/last/follow position sets,
rule chaining and BOF fixup, DFA state discovery, lookahead slot mapping,
accepting/lookahead/tag flags and sorted shared status records. Added equivalent
column/state discovery and removal, safe-pair synthesis, safe-state compaction,
8/16-bit table export/alignment and debug dumps. The builder consumes the
existing front-end state and category builder directly; full compiler owner
orchestration and complete rule-data emission remain pending.

Differential tests compare tree structure, nullable and all position sets,
per-state positions/transitions/flags, lookahead maps, status records, optimized
tables, synthesized safe rows and serialized table bytes with ICU4J. Coverage
includes all 15 embedded rule sources, 100 generated expressions, chaining,
BOF/EOF, dictionary categories, lookahead conflicts, combined status tags and
both sides of the 255-state export boundary. Deterministic state/status/reverse
debug output is compared too; node-set iteration order is unspecified. Common
tests exercise tagged lookahead, safe tables, binary sizes and absent-direction
handling. Full JVM, JS Node and macOS Arm64 suites pass.

### Complete break-rule compilation and public string API

Ported RBBIRuleBuilder orchestration: scan, set categories, DFA, iterative
row/column optimization, safe reverse synthesis, trie and complete ICU binary
emission. Headers, offsets, alignment, status records, UTF-8 stripped source
and NUL termination follow ICU4J exactly. An explicit replacement of unpaired
UTF-16 units with '?' preserves Java's UTF-8 encoder policy on all targets.
The original owner state is composed from the existing front-end result.
Global ICUDebug discovery remains pending; internal compiler debugging can
still be supplied explicitly.

Public RuleBasedBreakIterator(String) now compiles and loads rules. The Common
compileRules(String): ByteArray API replaces the platform OutputStream sink;
its bytes can be passed to the existing BinaryBuffer compiled-rule factory.
Full compiler binary output is compared byte-for-byte against ICU4J for all
15 embedded rule sources, custom rules and 60 generated rule sets. Public
constructor tests compare forward/backward/offset navigation, statuses,
cloning, source identity and syntax errors, including malformed UTF-16 source
units, lookahead, chaining, BOF/EOF, dictionary sets and supplementary input.
Common tests exercise the public pipeline and Java-compatible UTF-8 replacement
on JVM, JS Node and macOS Arm64. All three full test suites pass. Locale
factories and the broader ICU4J library scope remain incomplete.

### Filtered sentence boundary iterator and explicit exception builder

Ported SimpleFilteredSentenceBreakIterator's backwards exact and forwards
partial trie matching, one-space handling, all navigation methods, cloning,
delegate/text ownership and original equality/hash behavior. Added the empty
FilteredBreakIteratorBuilder factory and explicit suppress/unsuppress/wrap API,
including partial-prefix grouping, surrogate-aware reversal, mutable
CharSequence identity semantics and empty-filter adoption shortcut. Locale
constructors/factories and CLDR exception loading remain pending the locale
resource layer; no synthetic locale exception lists are substituted.

Differential tests compare both trie contents and forward/backward/offset
navigation, next(n), current/text positions, inherited statuses, cloning and
exception classes with ICU4J. Coverage includes exact and shared-prefix partial
abbreviations, single/double spaces and line separators, supplementary and
unpaired UTF-16 units, empty/duplicate/mutable exceptions, bounded delegates,
long cache-spanning input, builder reuse and original null-trie hash/equality
quirks. Common tests verify suppression, builder snapshot independence and the
upstream isBoundary behavior that leaves the delegate on a suppressed boundary.
Full JVM, JS Node and macOS Arm64 suites pass.

### Locale parser primitives

Started the original locale implementation layer with complete AsciiUtil,
StringTokenIterator, ParseStatus and LocaleSyntaxException ports. ASCII casing
and predicates deliberately avoid Unicode case folding. Case-insensitive
comparison preserves Java's exact UTF-16/length difference on every target;
keys cache the ASCII-lowercased string hash. Title casing retains upstream's
first-character behavior, including unchanged digit/non-ASCII-leading strings.
Tokenization preserves empty tokens, delimiter-set UTF-16 semantics, restart
positions, exhausted state and partial state changes on invalid offsets.

Differential tests check every UTF-16 unit, 1000 random strings, casing/key
hashes/equality and exact comparison values, plus token states across 150 random
inputs, delimiter sets, all valid start offsets, invalid offsets and text
replacement. Status reset/error metadata and syntax exception indices also
match ICU4J. Common tests cover casing, exact comparison, empty subtags and
reset/identity semantics. Full JVM, JS Node and macOS Arm64 suites pass.
LanguageTag, locale builders, ULocale and locale resource fallback remain
pending; these are the parser dependencies, not a replacement locale layer.

### LanguageTag parsing and canonicalization

Ported LanguageTag's source-string parser, all syntax predicates and ASCII
canonicalization methods, component getters/toString, grandfathered mappings
(including prefix mappings), variant/extension deduplication, private use and
parse status/error offsets. Unicode extension true/yes removal follows the
original order. The parser preserves the release behavior for malformed legacy
suffixes, including original-length reporting after mapping.

Variant uppercasing and extension singleton lowercasing retain ICU4J's host
default-locale dependence through Common CaseMapImpl. Minimal platform adapters
only discover the host language: java.util.Locale on JVM, Intl on JS,
NSLocale on macOS and POSIX locale environment variables on Linux. Algorithms
and ICU data remain in commonMain; no production ICU4J dependency is introduced.
Full ULocale default management and parseLocale(BaseLocale, LocaleExtensions)
conversion remain pending, as do the broader locale builders/resource layer.

Differential tests compare components, strings and status metadata for 3000
mixed valid/invalid generated tags, all grandfathered replacements, duplicate
subtags, extensions, private use, validators and canonicalizers. They also check
all UTF-16 singleton predicates and English/Turkish/Azeri/Lithuanian default
casing. A separate ICU class loader with assertions disabled verifies release
legacy-error behavior. Full JVM, JS Node and macOS Arm64 suites pass; Linux x64
production sources also compile on the macOS host (Linux runtime tests run in
Linux CI).

### Locale extension representation and Unicode extension validation

Ported Extension and UnicodeLocaleExtension: singleton/value/ID representation,
ca-japanese and nu-thai constants, attribute/keyword serialization, empty types,
all syntax predicates and adopted backing collection semantics. Initially empty
collections are not adopted, while nonempty collections retain live read-only
views; the serialized value remains cached after backing mutations. Views reject
all mutations, including empty bulk operations and iterator removal. Common
constructors take Set/Map inputs and optional explicit ordering comparators in
place of JVM SortedSet/SortedMap; locale-builder inputs use natural ordering.

Differential tests compare natural/reverse ordering, 200 generated collection
states, live views and cached serialization after additions/clears, null/empty
construction, constants, generic extension values and all read-only mutation
errors. Predicates are checked against every UTF-16 singleton and 3000 random
strings/types. Common tests verify ordering, cached values, live views, mutation
rejection and syntax boundaries. Full JVM, JS Node and macOS Arm64 suites pass.
BaseLocale's GC-aware cache, aggregate LocaleExtensions, locale builders and
LanguageTag.parseLocale conversion remain pending.

### Aggregate locale extensions and builder mutation/parsing

Ported LocaleExtensions aggregation, lowercase/sorted canonical IDs with private
use last, Unicode attribute/keyword snapshots, lvariant stripping, constants,
lookup/validation and equality/hash. Extracted the existing live unmodifiable
set view for reuse with extension singleton keys.

Ported InternalLocaleBuilder's language/script/region/variant setters,
attribute and keyword mutation, individual/combined extension parsing,
Unicode extension attribute/key/type scanning, LanguageTag import, clear and
extension snapshot export. Original failure timing/state is retained, including
unchecked incomplete-first-singleton behavior, duplicate-extension overwrite,
validated-but-unmodified keyword underscores and asymmetric uppercase
CaseInsensitiveChar equality. BaseLocale import/export and GC-aware locale
cache remain pending; the builder is not yet a complete locale facade.

Differential tests compare scalar state and aggregate extension snapshots after
successful and failing operations, syntax messages/indices, null/removal inputs,
Unicode duplicates, mixed-case singleton keys, private use/lvariant, LanguageTag
import and 500 generated extension strings. Constants, all UTF-16 key predicates,
case-key equality/hash quirks and lvariant removal also match ICU4J. Common tests
cover immutable snapshots, canonical/private ordering, errors without unintended
mutation and original duplicate/type behavior. Full JVM, JS Node and macOS Arm64
suites pass.

### GC-aware locale object cache

Ported LocaleObjectCache's lookup/normalization/creation protocol in Common:
cleanup before lookup, normalized-key recheck, null-result handling, creation
outside storage locks, replacement publication and post-publication cleanup.
Concurrent misses may return distinct newly created values, matching upstream.
Constructor validation and callback/error timing follow the original cache.

JVM storage retains ConcurrentHashMap, SoftReference and ReferenceQueue,
including unconditional queued-key removal. JS uses WeakRef and
FinalizationRegistry; primitive values are weakly boxed because JS weak
references require objects. Native uses Kotlin WeakReference with mutex-protected
mutable maps and bounded rotating cleanup batches, avoiding whole-map copies
or full scans on each hit. POSIX mutex storage is reclaimed by a Native Cleaner.
Native reference lifecycle APIs are described in the official documentation:
https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.native.ref/-weak-reference/ .
JS/Native use weak rather than JVM soft retention, so eviction frequency is
platform-dependent; cached values are never required for correctness.

Common tests verify exact protocol ordering, normalization hits/nulls and object,
String and integer values. JVM differential tests compare callbacks, failures,
constructor arguments, cleared/enqueued references and stale queued entries that
remove replacements. An eight-thread forced concurrent miss test verifies
creation stays outside storage locks. Native tests exercise four concurrent
workers and forced GC, proving both referent collection and stale-key release.
Full JVM, JS Node and macOS Arm64 suites pass; Linux production sources compile.
A subsequent macOS Native test run including forced GC also passes. BaseLocale
and its integration into locale builders/tag conversion remain pending.

### BaseLocale, string interning and locale/tag integration

Ported BaseLocale's normalized fields, root instance, case-insensitive cache
keys, exact key comparison/hash, canonical instance cache and string rendering.
Immutable hash values are computed at construction instead of lazily publishing
volatile integers. JVM uses String.intern; JS string primitives already compare
by contents. Native uses a serialized weak string pool whose keys retain copied
UTF-16 units and a weak source reference, preventing a strong key-to-referent
cycle while preserving canonical string identity across concurrent misses.

Completed InternalLocaleBuilder.getBaseLocale/setLocale: lvariant extraction,
validation before mutation and base/extension import/export. Completed
LanguageTag.parseLocale with deprecated language remapping, valid variant
conversion, private variant truncation/merging, extension ordering and und
selection. Original internal privateuse/toString behavior is preserved (the
parseLocale representation does not add an x prefix). This completes those
implementation classes' active ICU (JDKIMPL=false) paths; ULocale and locale
resource/factory integration remain pending.

Differential tests compare 1000 arbitrary field sets, canonical fields, interned
string identity, hashes, 500 key sets and exact key ordering/normalization,
instance-cache reuse, builder round trips, rejected base fields, deprecated
language codes and variant/private-use conversions. Common tests check root,
canonical reuse, string sharing, hashes and validation without mutation. Native
tests prove concurrent locales share interned fields and unreferenced pooled
strings are reclaimed by forced GC. Full JVM, JS Node and macOS Arm64 suites
pass; Linux production sources compile.

### Legacy locale IDs and ISO mappings

Ported LocaleIDs' original eleven language/country tables, obsolete/deprecated
mappings, ISO2/ISO3 conversion and defensive array-copy getters from ICU 78.3.
Ported LocaleIDParser's ICU/POSIX language/script/country/variant normalization,
experimental language prefixes, codepage skipping, 179-unit variant limit,
base-name override, keyword parsing/cache and default/set/remove operations.
Java trim semantics (units <= U+0020), duplicate-first keyword behavior, sentinel
handling, parser state/call order and original keyword value casing are retained.

The mutable keyword map preserves sorted iteration, live entry values, key
iterator removal and fail-fast structural changes. Empty maps retain original
no-op clear/remove and unsupported insertion behavior. Changes through exposed
maps/entries affect subsequent parser output; resetting all keywords detaches
previous map references as upstream does.

Differential tests compare every original ISO table/mapping, array isolation,
2000 generated locale IDs in both canonicalization modes, focused POSIX and
UTF-16 cases, getter order, overrides, keyword mutations/defaults/removals,
errors, mutable entries and iterators. Common tests cover normalization, sorting,
live mutation, limits, trimming and obsolete-code conversion. Full JVM, JS Node
and macOS Arm64 suites pass. ULocale and locale resource/factory integration
remain pending.

### Legacy/Unicode locale key and type data

Ported KeyTypeData's legacy/BCP key and type conversion, reverse aliases,
timezone colon/slash handling, known/special output flags, ordered read-only
key/type lists, deprecation metadata and value types. Special handlers preserve
original well-formed syntax for codepoints, reorder codes, region values,
scripts, subdivision codes and private types; they do not add semantic validity
checks beyond upstream's patterns. Added the generic Output<T> parameter wrapper.

Embedded pinned keyTypeData.res and timezoneTypes.res (39 total resources).
KeyTypeData's direct resource loader resolves the original cross-bundle
/ICUDATA aliases into timezoneTypes, preserving each value's reader context.
This is the direct lookup needed by these data bundles; general locale fallback
and the public resource-bundle facade remain pending.

Differential tests compare every original key/type alias, timezone conversion,
ordered lists, metadata, flags (including aliased output parameters), null
outputs, immutable views and 3000 generated special-type inputs against ICU4J.
The original key/type resource bytes are also compared. Common tests exercise
aliases, timezone data, special patterns, metadata and outputs. Full JVM, JS
Node and macOS Arm64 suites pass. All four data/model/parser generators pass
--check. ULocale and locale/resource factory integration remain pending.

### ULocale identity, keywords and BCP47 core

Started the public ULocale facade with normalized single/field constructors,
standard locale constants, immutable identity/hash/ordering/clone, base and ISO
fields, keyword access/update, structural fallback and extension access. Ported
legacy/Unicode key/type fallback syntax, forLanguageTag/toLanguageTag and the
public strict Builder, including IllformedLocaleException index/message rules.
POSIX, private-use variants, Unicode attributes/typeless types and unknown keys
retain upstream behavior. Internal already-normalized construction avoids a
second normalization for keyword updates and fallback.

The name normalization factory now uses the ported SoftCache (see the next
stage), including its concurrent publication protocol. Default/category management, JDK locale
interop/serialization, CLDR canonicalization/likely subtags, locale availability,
display names and resource/service APIs are still pending. This is the identity
and tag-conversion core, not completion of ULocale or ICU4J.

Differential tests compare focused and 400 generated ICU/BCP inputs,
constructor overloads, fields/ISO codes, extensions, keyword updates, fallback
chains, all-pairs ordering/equality, strict builder mutations/failures, mapping
fallbacks and exception metadata. Common tests cover public conversion,
round trips, POSIX (including Unicode case equivalents), builder state after
failure and immutable cloning. Full JVM, JS Node and macOS Arm64 suites pass.

### CacheBase, CacheValue and SoftCache publication

Ported the shared CacheBase/CacheValue/SoftCache implementation and switched
ULocale's name factory to SoftCache. Key slots remain permanently allocated as
upstream specifies; null results have a distinct persistent sentinel. Strong
values are stored directly, while reclaimable slots retain their identity when
reset after GC. Factory work stays outside map locks. putIfAbsent and atomic
reference reset make first-publication and recreation races return the winner.
Global STRONG/SOFT selection affects subsequent wrappers/slots, not existing ones.

JVM primitives use ConcurrentHashMap and SoftReference. Native uses the existing
POSIX mutex adapter for its map and WeakReference for reclaimable values; JS
uses its single-threaded map operations and WeakRef, boxing primitive values.
The Common cache protocol uses atomic reference publication and remains shared.
As with LocaleObjectCache, JS/Native have weak rather than JVM soft retention.

Differential tests compare null singleton/error behavior, reference strength,
forced clearing/reset (including null recreation), stable slot identity, ignored
hit data, permanent null caching, and strength changes. Forced eight-factory
races compare both SOFT and STRONG publication with ICU4J and return a single
winner. Common tests cover cache/value/reference contracts; Native tests force
GC and concurrent first misses. Full JVM, JS Node and macOS Arm64 suites pass;
production sources compile for Linux x64. ULocale default, display, canonical
alias/likely-subtag and resource/service APIs remain pending.

### Reusable resource keys and value interfaces

UResource now supplies Common Key, Array, Table, Value and Sink contracts.
Key retains shared byte storage, shallow cloning, independent cached strings,
ASCII validation and mutation ordering, exact comparison differences, and the
upstream hash algorithm (which repeats the first byte). Signed non-ASCII bytes
also retain upstream conversion and comparison behavior. Value uses BinaryBuffer
in place of java.nio.ByteBuffer and preserves the upstream debug rendering.
Common regression tests pass on JVM, JS Node and macOS Arm64. Bundle fallback and public resource enumeration remain pending.


### Reader resource values and container adapters

ICUResourceBundleReader.ReaderValue now implements all UResource.Value getters:
public type mapping, string/alias, signed and unsigned 28-bit integers, vectors,
binary blobs, arrays/tables, no-inheritance marker detection and string-array
convenience conversions. Type mismatches throw UResourceTypeMismatchException.
Array/Table adapters reuse caller-owned ReaderValue and Key outputs, retain raw
key storage (including pool offsets), and leave outputs unchanged on failed
lookups. They preserve the original use of the output value's reader.

BinaryBuffer now supports read-only shared views with independent cursor state.
Slices preserve this mode, writes fail before changing position, and the bytes
property returns a defensive snapshot for read-only views. Binary resource values
use these views instead of exposing mutable bundle storage. Writable buffers
retain their existing direct byte-array access.

Differential tests walk original Japanese/root, ML and LSTM resource trees,
comparing value types, debug rendering, no-inheritance markers and container
outputs with ICU4J. Common fixtures cover scalar getters, mixed/empty string
arrays, invalid indexes, binary read-only views and cursor/storage sharing.
Full JVM, JS Node and macOS Arm64 suites pass. General resource-bundle loading,
locale fallback, alias resolution and sink enumeration remain incomplete.

### Named reader loading and reader cache

ICUResourceBundleReader.getReader loads named resources through a Common
ResourceLoader interface replacing the JVM ClassLoader boundary. The default
loader reads the pinned embedded ICUData. SoftCache keys retain upstream's
base-name/locale identity and XOR hash, intentionally excluding the loader.
Null input names normalize to empty strings; missing resources are cached.
Bundles with pooled data load pool.res through the same cache, validate the
pool flag and checksum, and retain the pool reader. Corrupt binary headers
report the full resource name and preserve the cause; failed construction is
not cached. KeyTypeData now uses the shared reader cache.

getFullName preserves slash paths, dotted Java bundle names, suffixes and the
special empty-name host-default convention. Full host locale identifiers come
from JVM Locale, JS Intl, macOS NSLocale and Linux locale environment variables;
this boundary does not yet implement ULocale's mutable default/category API.
JVM differential tests change host defaults (root, country, script and Unicode
extensions) and compare the original naming results. Common tests cover loader
identity on cache hits/misses, pooled reads, failure/retry and embedded loading.
JVM, JS Node and macOS Arm64 suites pass; Linux x64 production sources compile.
The reader cache does not provide locale fallback or alias/sink enumeration;
those remain part of the unfinished resource-bundle facade.

Read-only reader inputs now retain shared storage for absolute internal slices.
BinaryBuffer.slice(index, length) validates against the current limit without
changing the source cursor; nested slices preserve offsets, order and protection.
The reader no longer accesses the defensive bytes snapshot on every scalar read.
Regression tests prove binary resource views still observe changes through the
original writable owner, including when reader input is read-only. JVM, JS Node
and macOS Arm64 suites pass.

### Per-reader resource cache

ResourceCache ports ICU4J's sorted 32-entry table, transition to radix levels,
precomputed level widths, offset/mini-type keys and collision splitting. Small
values and array containers remain strongly held; large values use the existing
platform reclaimable-reference primitive (JVM soft, JS/Native weak). Cleared
entries are republished with the current CacheValue strength. JVM monitor and
Native POSIX mutex adapters serialize complete lookup/insertion operations; JS
operations remain synchronous.

The reader caches strings, aliases and containers, including pool strings in
the pool reader's cache with local offsets. Repeated Value array/table getters
retain their adapter identity. Marker detection reads original resource data
rather than the cached string, matching ICU4J's independent marker check.

Common tests cover sorted insertion, type/offset distinctions and radix levels
across offset bit widths. JVM differential tests cover 1/32/33/400 entries,
forced reference clearing before and after migration, replacement strength and
concurrent publication. Full JVM, JS Node and macOS Arm64 suites pass; Linux x64
production sources compile. Resource-bundle fallback and the public facade are
still unfinished.

### Common resource bundle base and iterator

UResourceBundle now has the original type constants, default typed-getter
failures, identity/subclass hooks, key/index parent traversal, object resolution,
key union/enumeration and resource iterator. The Common class supplies the
ResourceBundle operations explicitly instead of extending java.util.ResourceBundle.
Enumeration retains the hasMoreElements/nextElement protocol. Runtime class names
in lookup errors use the platform's available class identity.

ICUResourceBundle's shared identity/containment core is present: WholeBundle,
locale/base identity, inherited parent, top-level key cache, root detection,
resource keys and original equals/hashCode release behavior. Indexed string
lookup retains the upstream ICUResourceBundle cast before checking STRING type.
The iterator captures the initial size, increments before lookup, and resets its
index without resampling size. UResource debug rendering now uses real bundle
constants rather than literal type numbers.

Common tests cover parent lookup, requested-bundle propagation, immutable sorted
keys and top-level caching, object string/array conversion, mismatch behavior,
shared identity and iterator failure progression. JVM differential tests exercise
the original base through custom subclasses, including the indexed-string cast.
Full JVM, JS Node and macOS Arm64 suites pass; Linux x64 production sources compile.
Named UResourceBundle factories, concrete ICU resource subclasses, alias/path
fallback, sink enumeration and Java ResourceBundleWrapper interoperability are
not implemented yet; the new classes are the base for those implementations.

### Concrete scalar resource objects

ICUResourceBundleImpl now supplies ResourceString, ResourceBinary, ResourceInt,
ResourceIntVector and the shared abstract ResourceContainer. Scalar objects
retain bundle identity, keys, parent inheritance and raw resource handles.
Short strings (under 12 UTF-16 units) and strings created under STRONG retention
are kept directly; longer strings otherwise use the reader cache. Integer
getters preserve signed/unsigned 28-bit decoding. Vectors return fresh arrays.
Binary getters return read-only views or fill a caller array of exactly matching
length; empty blobs return the shared empty array independently of destination.
Container string lookup checks bounds before taking its direct string fast path.

Common fixtures cover scalar types, default mismatch failures, identity, binary
cursor independence, destination reuse/replacement and vector copies. JVM tests
instantiate the original private scalar classes against root, ML and LSTM
resource bundles and compare values, metadata and string-retention fields.
JVM, JS Node and macOS Arm64 suites pass; Linux x64 production sources compile.
Concrete array/table child construction depends on alias resolution and remains
unfinished, together with named bundle factories and locale/path fallback.

### Locale parent selection and fallback data

ICUResourceBundle.OpenType and getParentLocaleID now implement the original
variant/trailing-underscore truncation, explicit CLDR parent overrides, regional
default-script selection and original-script restoration. Explicit parent and
non-default-script root routing apply only to LOCALE_DEFAULT_ROOT, as upstream
requires; other opening modes retain their separate selection behavior.

The 1,066 default-script and 199 parent-locale entries are extracted from pinned
ICU 78.3 LocaleFallbackData.java into data/locale-fallback.json, with source commit
and SHA-256 metadata. generate-locale-fallback.py verifies the extracted data's
checksum and emits Common tables; CI checks the output. The maps are unmodifiable,
including entries, keys, values and iterators. JVM uses Collections.unmodifiableMap
to reject Java default mutators as well; JS/Native use the Common live-view wrapper.

JVM differential tests compare every map entry and over 4,000 locale/original
locale pairs across all four opening modes. Common tests cover representative
parent/script/variant paths and collection mutation contracts; JVM tests also
exercise compute/merge/replaceAll and ensure callbacks do not run. Full JVM,
JS Node and macOS Arm64 suites pass; Linux x64 production sources compile.
This supplies parent selection for the unfinished bundle factory; bundle-chain
construction, concrete array/table objects and general aliases remain pending.

### Reader shared UTF-16 and binary view fidelity

The reader's 16-bit segment now retains a BinaryBuffer view rather than a copied
CharArray, matching ICU4J's shared CharBuffer. Compact array references and
no-inheritance marker checks therefore observe updates through the original
writable owner, including when the reader itself receives a read-only view.
Decoded strings still follow the original resource-cache retention rules.

Binary resource views preserve the upstream BIG_ENDIAN result even for
little-endian input: ICU4J's intermediate ByteBuffer.duplicate resets byte order
before sliceWithOrder. This also applies to read-only input. Empty binary views
retain their separate empty-buffer behavior. getVersion now returns VersionInfo
from the resource header. Invalid index counts/data lengths throw ICUException,
rather than the previous generic IllegalStateException.

JVM differential fixtures cover both byte orders and input protection modes,
binary payload/order/protection, compact references, marker updates and version.
Common regressions cover shared compact storage and index-header exception types.
Full JVM, JS Node and macOS Arm64 suites pass; Linux x64 production sources compile.

### Direct binary key comparisons

ICUBinary.compareKeys now supplies both byte-array and BinaryBuffer variants,
retaining signed-byte arithmetic, exact difference values and NUL termination.
Resource-table searches use the original key bytes directly, including pooled
16/32-bit key offsets, rather than allocating strings. Table.findValue uses the
same binary search with the output ReaderValue's reader, as ICU4J does.

Common tests use a CharSequence whose toString throws, proving query conversion
is not part of lookup. JVM differential tests compare 3,000 arbitrary byte/UTF-16
cases for both overloads, including read-only buffers and unchanged cursor state.
Full JVM, JS Node and macOS Arm64 suites pass.

### ICU bundle factory, concrete containers and aliases

ICUResourceBundle now provides binary bundle factories, whole-bundle alias
handling, mode/default-sensitive SoftCache entries, exact DIRECT/noFallback
behavior, missing-locale parent selection and existing-bundle %%Parent/truncation
chains. The Common ICUResourceLoader boundary replaces ClassLoader and preserves
upstream's cache keys excluding the loader and original-locale path state.
The default-locale identity boundary remains the existing host adapter; mutable
ULocale defaults/categories are still unfinished.

Concrete ResourceArray and ResourceTable implement index/key lookup, typed string
array extraction, sorted mutable local key sets and the object-lookup fast paths.
The common child factory resolves alias resources before returning objects.
Alias handling supports relative paths, implicit containing paths, absolute
bundle names, ICUDATA/ICUDATA-subtree loader selection, /LOCALE requested-bundle
lookup and original cycle detection. Path fallback reconstructs resource paths
when aliases switch containers/locales. at, findTopLevel, findWithFallback and
getWithFallback retain their distinct lookup/marker semantics.

KeyTypeData now uses this general bundle API; its special two-file raw alias
resolver was removed. Common ResB fixtures cover parents, all modes, noFallback,
%%Parent, nested fallback after aliases, requested-locale aliases, ICUDATA aliases,
cycles, mixed arrays and binary/vector/integer resources. JVM differential tests
compare opening outcomes, complete parent chains, key sets, paths, aliases and
collection/index behavior with ICU4J under a controlled default locale.
The protected-setter path also has regression coverage after a Kotlin/JVM
synthetic-accessor failure was found and removed. Full JVM, JS Node and macOS
Arm64 suites pass; Linux x64 production sources compile.

The ICU binary factory is implemented independently of the unfinished
UResourceBundle ICU-vs-Java dispatch. Whole-bundle %%ALIAS currently uses this ICU
factory; ResourceBundleWrapper/Java-resource targets remain pending with that
dispatch. Optimized string/value path APIs, sink enumeration, locale availability
and remaining bundle services are not complete. Only the manifest's resource
subset is embedded; custom loaders can supply other binary bundles.

### Optimized string/value fallback and item sinks

findStringWithFallback/getStringWithFallback port the reader-based traversal,
including raw container descent, alias path reservation and parent reconstruction.
They do not substitute object-path lookup. Marker strings remain visible to find,
while get rejects them. getValueWithFallback returns the actual reader/resource
pair and accepts an empty path for the current resource, without rejecting markers.

getAllItemsWithFallback enumerates the located resource child-first through its
parent chain, preserving actual alias target paths and reusing Key/ReaderValue.
The value and key are reset before each callback, so sinks may traverse/mutate
these output objects. The noFallback flag reflects the enumerated resource's
parent pointer. NoFail catches MissingResourceException only.

Common tests cover implicit-path aliases, string-array indexes, markers, scalar
mismatches, shared callback objects, callback mutation and error propagation.
JVM differential tests compare string/value outcomes and full typed sink traces
for four opening modes and multiple locales, mutating key/value outputs during
nested traversal. JVM, JS Node and macOS Arm64 suites pass; Linux x64 production
sources compile. Remaining locale-availability/resource services are pending.

### Child sinks with alias-target inheritance

getAllChildrenWithFallback now enumerates immediate table children over the
resource's parent chain. Aliases are resolved into independent ReaderValues.
Aliased table targets additionally enumerate their own parent tables, retain
cloned alias keys under callback mutation, and capture the value type before
calling the sink. The original /LOCALE table-path redirection returns to the
requested bundle when a parent alias changes the target key. Callback flags
retain the outer resource's noFallback value, including alias-target parents.

Common fixtures exercise table/scalar aliases, key/value mutation and target-key
redirection through US/en/root tables. JVM differential tests compare every
callback and error outcome for all opening modes, including direct bundles whose
missing alias targets follow the original failure path. Full JVM, JS Node and
macOS Arm64 suites pass; Linux x64 production sources compile. Locale availability,
remaining resource services and UResourceBundle/Java dispatch are still pending.

### Installed locales and keyword values

ULocale.AvailableType and ICUResourceBundle's installed-locale APIs now read the
original res_index tables. AvailEntry preserves prefix-only SoftCache identity,
lazy serialized initialization, shared array results, separate default/legacy
lists and unmodifiable raw locale-name sets. The internal WITH_LEGACY_ALIASES
selection retains upstream's release null result; ULocale's public combined-list
API is the separate layer that handles that selection.

getKeywordValues unions keyword table keys over installed locales, excludes
exactly default/private- names and preserves the original per-locale Throwable
skip policy. It currently uses the implemented ICU binary factory, with Java
resource dispatch still pending. Core and break res_index binaries are embedded
from ICU 78.3 and checksum-pinned; the manifest now has 41 resources.

Availability locks preserve reentrant initialization callbacks. JVM uses its
monitor, JS synchronous calls and Native a recursive POSIX mutex with cleanup.
Common tests cover array/name-set contracts, reentrant loading, malformed keyword
tables and exclusions. JVM compares full core/break lists and names with ICU4J,
checks release-only null behavior in an assertion-disabled loader, and verifies
one initialization under eight concurrent callers. Full JVM, JS Node and macOS
Arm64 suites pass; Linux x64 production sources compile. Full resource scanning,
Java Locale conversion and remaining availability/services are not complete.

### Public ULocale availability collections

ULocale.getAvailableLocales returns a shallow array copy. getAvailableLocalesByType
returns an unmodifiable backed list for DEFAULT/ONLY_LEGACY_ALIASES, and an
independent concatenated snapshot for WITH_LEGACY_ALIASES. Null type throws
IllegalArgumentException, and the combined list retains source ordering without
deduplication. Common list views seal mutations, iterators and sublists; JVM
list/set boundaries additionally use Collections wrappers to reject Java default
mutators before invoking callbacks. Availability name sets use the same sealed
set boundary. getKeywordValues also preserves null-keyword skip behavior inside
the per-locale Throwable policy.

Common tests cover copying, concatenation, list/sublist backing and mutation
rejection. JVM tests compare all public lists with ICU4J, distinguish live category
views from combined snapshots under cached-array mutation, and check replaceAll
and removeIf callback suppression. Full JVM, JS Node and macOS Arm64 suites pass;
Linux x64 production sources compile. Availability metadata comes from upstream
indexes; it does not imply that every listed locale payload is embedded. Full
resource scanning, Java locale/resource interoperability and the rest of ICU4J
remain unfinished.

### Functional-equivalent keyword locale selection

ICUResourceBundle.getFunctionalEquivalent now ports the original three searches:
locate the relevant default, find the requested resource, then fall back to that
default while checking its actual locale ownership. It retains default/resource
depth updates, omitDefault decisions, alias ownership distinctions, installed
locale availability output and the original lookup-loader behavior (the supplied
loader controls availability; primary lookup uses the default loader).

Common tests cover default/requested keywords, unknown values, alias owners,
availability flags, absent default markers and early default-loader failure.
A Common bounds guard rejects empty isAvailable arrays, preventing JS from silently
ignoring the invalid write; the exception category matches upstream. JVM tests
compare 256 locale/keyword/resource/omitDefault combinations with ICU4J, including
result names, missing-resource metadata and output flags, and test absent-default
and output-array error boundaries. Full JVM, JS Node and macOS Arm64 suites pass;
Linux x64 production sources compile. Java-resource dispatch and the other
unfinished ICU4J components remain outside this completed service method.

### Runtime ICU configuration

ICUConfig now preserves configuration-file defaults, per-query nonempty host
property precedence, empty override fallback and caller defaults. The original
six defaults are checksum-pinned in data/config/ICUConfig.properties; a generator
produces Common defaults and the JVM classpath resource, and CI checks both.

JVM retains Properties.load, privileged host/resource access where applicable,
stream closing, ignored missing/IO failures and partial properties. Defaults are
published incrementally during initialization so reentrant loading sees the same
empty/partial state as ICU4J. Native and Node use environment overrides; browser JS
uses pinned defaults when no Node environment is present. Native/Node reject NUL
names at the environment boundary to avoid prefix lookup through C strings.

Common tests cover all pinned defaults, caller fallback and empty names. Node
checks dynamic overrides, empty/whitespace values and NUL names. JVM compares
custom escaped/continued properties, missing files, partial read failures,
close-failure precedence and reentrant initialization traces with ICU4J in isolated
loaders. Full JVM, JS Node and macOS Arm64 suites pass; Linux x64 production sources
compile. Runtime resource scanning and other ICUConfig consumers remain part of
the unfinished full ICU4J port.

### Configured CJK phrase-engine selection

Default CjkBreakEngine and LanguageBreakEngineRegistry instances now consume
ICUConfig's useMLPhraseBreaking setting. The engine reads it independently at
construction and during nonempty range division, matching ICU4J; explicit Common
ML overrides remain available. Korean construction still avoids Japanese model
setup. Constructor skip-set/model state is retained when the property changes,
including upstream's failure when ML is enabled after a non-ML construction.

JVM differential tests cover 500 combinations of initial/current property values,
case/whitespace parsing, Japanese/Korean engines, phrase modes and text, comparing
outcomes, cursor positions and boundaries. A fresh registry is compared with the
original configured engine. Node tests verify environment-driven registry setup
and runtime switching. Full JVM, JS Node and macOS Arm64 suites pass; Linux x64
production sources compile. Other configuration consumers and the full ICU4J
port remain unfinished.

### Debug parameter snapshots

ICUDebug now preserves the one-time parameter snapshot, substring flag/value
lookup, empty-string activation, help output and conditional null-argument
behavior. JVM reads the original system property and ignores SecurityException;
Node and Native use their host configuration environment boundary. Differential
JVM tests compare return values, exceptions and exact output with isolated ICU4J
classes. Common tests cover the same parsing boundaries. JVM, JS Node and macOS
Arm64 suites pass; Linux x64 production sources compile. The full port remains
unfinished.

### Full locale-name source selection

ICUResourceBundle.getFullLocaleNameSet now uses the original lazy, prefix-keyed
availability cache, scan/list/index source order, scan flag, locale-looking-name
filter and root-name normalization. Returned sets reject mutation. The scan
filter intentionally retains any name containing an underscore, including the
embedded LSTM model names. The list-file path preserves whitespace and only
skips empty lines and lines beginning with '#'.

ICUResourceLoader now offers resource visitation and ASCII-line boundaries while
remaining a SAM for existing binary-only loaders. The embedded loader enumerates
its actual immediate resource files. ICUClassLoaderResourceLoader adapts JVM
ClassLoader binary reads, privileged directory enumeration and file/JAR/wsjar
visitation; list reads retain already delivered lines after read/close IO errors.
JVM differential tests compare all three sources, nested-file exclusion, flag
parsing, missing data, partial list reads and close failures with ICU4J. Common
tests cover filtering, immutable cached sets, root normalization and ASCII lines.
Full JVM, JS Node and macOS Arm64 suites pass; Linux x64 production sources compile.

This is not yet the complete resource scanning subsystem: URLHandler custom
protocol hooks and diagnostics, ICUBinary external dataPath folders/packages,
Java-resource dispatch and the rest of ICU4J remain to be ported. Locale-name
metadata still does not imply that every locale payload is embedded.

### CmnD binary data packages

DatPackageReader now ports ICU4J's complete CmnD version-1 package table reader:
header authentication, first/last package-prefix validation, binary item search,
relative name/data offsets, capacity-based final payload bound and immediate
folder enumeration. Both icudt78b and icudt78l entry prefixes are accepted.
Validation advances past the header; all later lookups leave package state alone.
It preserves upstream's suffix-search behavior rather than replacing it with an
endsWith check, and skips nested paths while continuing the table scan.

ICUDataPackage exposes the reader to Common callers and supplies an
ICUResourceLoader adapter for real binary bundles, locale fallback and resource
visitation. Payload views share storage, retain read-only status and have their
own cursors. BinaryBuffer.duplicate reproduces the original big-endian duplicate
order; package payload extraction retains that behavior even for little-endian
package tables. The caller must keep the borrowed package buffer's state stable.

Package differential tests compare 82-item tables, both byte orders and package
prefixes, mutable/read-only slices, folder/suffix combinations and malformed
headers/tables with ICU4J's private reader. They also verify shared writes and the
last item's capacity-based bound after changing the package limit. Common tests
load real ResB fixtures through a little-endian package, including fallback,
availability and full locale-name enumeration.

These tests exposed and corrected existing ICUBinary.readHeader error fidelity:
absolute header reads now leave the original position intact on authentication
failure, preserve byte-order changes, distinguish short-buffer bounds failures
from IO authentication rejection and retain original diagnostic messages. A JVM
header matrix compares truncation, mutated fields, rejecting authenticators,
messages and partial cursor/order state with ICU4J. An older name-reader test now
expects the original short-header bounds exception. Full JVM, JS Node and macOS
Arm64 suites pass; Linux x64 production sources compile. Automatic dataPath
folder/package discovery, external-file ownership, URL protocol extensions and
the rest of ICU4J remain unfinished.

### External binary dataPath files and mapped storage

BinaryDataFiles now ports the ordered dataPath split and recursive folder scan,
ASCII trim/one-separator removal, .txt file/directory exclusion, authenticated
.dat package retention and lazy single-file mapping. Lookup tries each discovered
file/package in publication order. Folder enumeration preserves upstream's
single-file leading-slash test, including its exclusion of root-folder single
files; package root entries still contribute. The scan is a one-time snapshot,
not a file watcher. Packages retain their mapped bytes; single files map anew on
successful lookup.

ICUBinary initializes this scan from its original configuration key. Its loader
lookup overload prioritizes external data and then the requested resource name;
missing required data now throws MissingResourceException with the resource key.
ICUResourceBundleReader uses the same external-first path for ICU data namespaces,
and getFullLocaleNameSet adds external folder/package names before filtering.
Header authentication is independent of ICUBinary initialization while discovering
packages, avoiding a recursive initialization dependency.

BinaryBuffer now has an internal storage boundary, preserving existing array
constructors and shared-array behavior while permitting live host-backed views.
JVM retains read-only FileChannel mappings. macOS/Linux use POSIX mmap and a
cleaner attached to the shared storage owner. Slices/duplicates keep that owner
alive. Node has no built-in mmap; retained descriptors provide live inode-stable
reads, with bulk reads for copies and finalization-based closing. Browser JS has
no host filesystem; embedded/caller-supplied resources remain available. Native
and Node configuration comes from the existing environment-property boundary.

Common tests cover path parsing, scan and lookup order, recursive filtering,
missing/deleted singles, retained packages and original root enumeration. An
isolated JVM differential test compares one-time configuration snapshots, file
and package lookup, name sets, shared file writes, external bundle fallback and
full locale-name discovery with ICU4J. JVM, Native and Node storage tests observe
writes through existing views and retain those views after unlink. An old required
resource test now checks the original missing-resource exception/message/key.
Full JVM, JS Node and macOS Arm64 suites pass; Linux x64 production and test sources
compile. Linux runtime tests are configured in CI and were not executed in this macOS run. Node's
retained-descriptor implementation preserves live content but has a different
resource/per-byte IO cost than mmap. URLHandler custom protocols/diagnostics,
Java bundle interoperability and the full ICU4J port remain unfinished.

### URL visitors, protocol factories and class-loader selection

URLHandler now shares the original guide overload, visitor interface, recursive
file traversal and archive prefix/filter/strip loop in Common code. File visits
preserve filesystem order, the leading slash with strip=false, null child lists
and propagation of visitor failures. Archive visits skip directory records,
preserve the slash-at-index-zero rule, stop after catching Exception and allow
Error to propagate. Common callers can use forFile(path) or forArchive(prefix,
entries); the latter receives a fresh archive iterator for each guide call.

The JVM URL overload retains file URI handling, JarURLConnection ownership,
jar/wsjar normalization and custom protocol lookup through urlhandler.props.
The registry snapshots that file once, uses default-charset BufferedReader and
ASCII trim, stops at a malformed line, keeps partial registrations and replaces
duplicate protocol mappings. It uses Class.forName/getDeclaredMethod, preserves
original access/invocation exception handling and does not suppress wrong-result
ClassCastException or nonstatic-factory NullPointerException. DEBUG snapshots,
property/URL/visitor diagnostics and reader closing retain the original behavior.
ICUClassLoaderResourceLoader now delegates to these handlers rather than its
previous private file/JAR loop; custom protocols participate in full locale-name
scanning. ClassLoaderUtil is ported as a JVM boundary, with class/context/system
selection and synchronized volatile bootstrap fallback.

Common tests cover file and archive guide modes, order, prefix rules and failure
policies. JVM differential tests compare file/JAR/wsjar URLs, invalid/default
URLs, visitor failures and class-loader selection with ICU4J. Isolated loaders
compare property parsing, declared/static/private/missing factories, null/throwing/
wrong-type results, class initialization failures, partial read/close IO failures
and exact debug stdout/stderr. An integration test feeds custom-protocol resource
names into full locale scanning. Node/Native tests exercise the Common host file
factory and retained file names after unlink. JVM, JS Node and macOS Arm64 suites
pass; Linux x64 production and test sources compile.

JVM URL parsing and reflective factory discovery stay on the JVM boundary.
Common archive traversal uses supplied iterators; automatic non-JVM archive URL
resolution is not supplied here. Java bundle interoperability, remaining resource
services/diagnostics and the full ICU4J port remain unfinished.

### Common Java class/property bundle wrappers

ResourceBundleWrapper now ports recursive class/property loading, the original
name/default-ID cache key (omitting loader identity), parent/default-language
fallback, object lookup and deduplicated key enumeration in Common code.
It preserves partial direct-mode parent inheritance, class rejection without
properties/parent fallback, properties fallback only after class misses, and
separate stream-open versus parse/read failure behavior. Wrapper keySet and
containsKey retain the inherited empty-key behavior despite nonempty getKeys;
typed UResourceBundle children remain unsupported, as in ICU4J.

JavaResourceBundle/JavaBundleClassResult provide the portable object/class lookup
boundary. MapJavaResourceBundle supports Common object providers, including
underlying parents and object/array values. ICUResourceLoader stays a SAM while
offering class and properties hooks. JVM ICUClassLoaderResourceLoader uses actual
ResourceBundle subclasses, Class.newInstance error categories, privileged stream
opening and buffered PropertyResourceBundle parsing/closing. Native/Node class
providers can supply Common bundles; binary-only providers load .properties
through the shared parser. The default non-JVM provider is the embedded loader.

JavaPropertiesBundle implements property keys/separators, continuations, comments,
escapes, Unicode escapes, duplicate replacement, UTF-8 with Latin-1 fallback and
forced UTF-8/ISO-8859-1 decoding. JVM classpath properties use the JDK parser;
Common byte providers use this parser. Its encoding setting is snapshotted from
the host property/environment boundary. End-of-file continuation and empty-key
corner cases are compared against the current JDK rather than approximated with
simple line splitting.

Common tests cover object/property providers, parent lookup, key boundaries,
cache reuse and rejected provider exceptions. JVM differential tests compare
480 default-locale/requested-locale/direct-mode/failure combinations with ICU4J:
class/property precedence, wrong classes, constructor/security/linkage errors,
open/read/close errors, malformed properties, object/typed lookups, key iteration,
parent chains and load/close traces. Context-loader and loader-omitting cache tests
also pass. Properties syntax is compared with JDK on 3,000 generated strings plus
explicit EOF/escape cases and decoding fixtures. Isolated wrapper diagnostic tests
compare exact stdout. The JVM loader adapter now initializes its URL scan debug
flag when scanning, avoiding unrelated diagnostic output during Java bundle loads.

Full JVM, JS Node and macOS Arm64 suites pass; Linux x64 production/test sources
compile. A final JVM run passes after the JVM-only diagnostic initialization fix.
Default fallback currently uses the existing host locale-name boundary; mutable
ULocale defaults/categories remain unfinished. UResourceBundle named factory/root
type dispatch, ICU-to-Java aliases, Java Locale/ResourceBundle facade integration
and the rest of ICU4J remain to be ported.


### Common root bundle factory dispatch

UResourceBundle now implements named String and ULocale factories, root-type
probing, ICU/Java selection and promotion after a previously missing root. The
atomic root-type cache is keyed only by base name, matching ICU4J's omission of
loader identity. Probes run outside cache publication. String null arguments and
cold non-ICU null-loader failures preserve the original behavior; a cached Java
root can use the context loader. ULocale factories strip locale keywords.

Root aliases, locale indexes and functional-equivalent resource loads now use the
shared factory where upstream does, retaining the ICU casts and loader choices.
Pinned ICU root.res and pool.res bring the embedded manifest to 43 resources.
The default JVM ICU loader combines embedded data with classpath lookup; JS and
Native use embedded data. This does not embed every locale payload.

Common tests exercise root precedence, missing-root promotion, loader-omitting
caches, null boundaries and pooled root loading. JVM differential tests compare
140 naming/root-kind/locale/direct-mode combinations, including exact resource
request traces, plus context-loader and promotion scenarios. Availability tests
also compare the added root probe with ICU4J. Full JVM, JS Node and macOS Arm64
suites pass, Linux x64 test sources compile, and the data generator check passes.
Mutable ULocale defaults/categories, Java Locale overloads and the rest of ICU4J
remain unfinished.


### Mutable ULocale defaults and JVM locale conversion

ULocale now exposes general and DISPLAY/FORMAT category getDefault/setDefault
APIs. Shared code uses an atomic general cache and a reentrant lock for updates
and category state. Recursive initialization returns ROOT until the corresponding
cache is populated. General setters change the host before publishing the new
ULocale and update both categories. Category setters publish before calling the
host, preserving ICU4J's ordering.

JVM tracks actual java.util.Locale tokens and refreshes caches after external
Java default changes. Its category setter ignores target failures, as upstream's
reflective helper does; the general setter propagates failures. JS and Native
initialize from the host locale and expose process-local mutable defaults and
independent categories. Resource defaults now read ULocale.getDefault, so explicit
changes affect bundle selection and default-language fallback on every target.
Older Android runtimes without JDK locale categories are not targeted by this
adapter yet.

JVM extension functions ULocale.forLocale and ULocale.toLocale port upstream's
conversion rules and soft-cache behavior. They preserve original Java Locale
objects through conversion, script/keyword tag mapping, variant casing, Unicode
attributes and types, and no_NO_NY to nn_NO. Java callable facade compatibility
and java.util.Locale resource factory overloads remain unfinished.

Common tests verify category independence, null boundaries, supplied instance
retention and actual resource fallback after default changes. JVM tests compare
conversion and external host changes with ICU4J, including cold isolated class
initialization with Unicode extensions. Full JVM, JS Node and macOS Arm64 suites
pass; Linux x64 test sources compile. The complete ICU4J port remains unfinished.


### JVM resource factories and Java locale availability

JVM extensions now provide UResourceBundle factories accepting java.util.Locale
and ClassLoader, the ULocale/ClassLoader and String/ClassLoader families (including
direct mode), and getLocale for every Common bundle subtype. Locale factories
normalize null bases, resolve null locales from the general ULocale default and
strip keywords before dispatch, using the existing shared bundle factories.
Nullable loaders retain the original cold/warm cache behavior.

ICUResourceBundle JVM extensions provide getLocaleList and getAvailableLocales
for the default or supplied base/loader/category. Conversion retains scripts and
deduplicates by Java Locale equality in input order. Availability entries now hold
a shared atomic host-array slot under their reentrant lock. Like ICU4J, the first
successful category request determines the one cached Java array for a base;
later requests return that same mutable array even with other/null categories or
loaders. Missing-category conversion failures leave the array cache unpopulated.
The availability loader boundary accepts null for these JVM calls.

JVM differential tests cover 42 binary/Java locale/ClassLoader factory combinations
and exact load traces, keyword stripping, null base/default-loader root selection,
Java bundle locale identity and null-loader cold failures. Java availability tests
cover scripts, duplicate conversion, first-category selection, shared mutations,
null categories/loaders and missing-category recovery against ICU4J. Full JVM,
JS Node and macOS Arm64 suites pass; Linux x64 test sources compile. These JVM APIs
are Kotlin extensions; direct Java member/static facade compatibility and the
complete ICU4J port remain unfinished.


### Common LSR encoding and locale metadata foundation

LSR now ports language/script/region identity, explicit-subtag flags, region
indexes, equivalence versus equality, Java-compatible hash codes and resource
encoding/decoding. The cached decoder shares strings through local language,
script and region pools and retains the zero/one sentinel records. Numeric M49
lookup is deferred until a numeric region is needed, including nullable-table
behavior and the original absent-region diagnostic. Script-cache initialization
and encoding evaluation order follow ICU4J.

Pinned langInfo.res and metadata.res are embedded on every target, bringing the
manifest to 45 binary resources. The existing resource reader loads their likely
and alias tables, including the core pool dependency. These are prerequisites for
likely-subtags matching and CLDR alias canonicalization; those engines and their
ULocale entry points are not implemented by this stage. Region classification
also remains a dependency, using supplementalData's validity, code-mapping and
territory-containment tables.

Common tests verify every well-formed region index, flags and string identity,
nullable/numeric boundaries, alias resource lookup and decode/re-encode every LSR
in the pinned likely table. JVM differential tests compare all decoded resource
records, hash codes and encoding across language/region combinations and all
script property codes with ICU4J. Full JVM, JS Node and macOS Arm64 suites pass;
Linux x64 test sources compile. The generator check passes for all 45 resources.
The complete ICU4J port remains unfinished.


### Common Region classification and containment

Region now ports its complete public lookup/classification/containment API in
Common code. Metadata territory aliases and supplementalData validity ranges,
code mappings and containment create the shared region identities. String and
numeric lookups retain deprecated single-replacement canonicalization, padded
numeric alias fallback and original unknown-ID/code diagnostics. WORLD, UNKNOWN,
CONTINENT, SUBCONTINENT and GROUPING special cases match upstream, including QO.

Groupings contain their members without becoming geographic parents. Recursive
parent/type and descendant/type queries preserve the source traversal semantics,
including nullable type queries. Contained/available sets iterate by region ID;
preferred-value lists retain data order. Views reject mutations, including JVM
removeIf/replaceAll callbacks before evaluation. Region equality retains object
identity; compareTo uses the region ID. Reentrant initialization publishes a
fully built snapshot atomically and retries after a failed load.

Pinned supplementalData.res brings the embedded manifest to 46 resources. Common
tests verify aliases, numbers, classification, deprecated preferences, hierarchy,
null boundaries and immutable views on JVM, JS and Native. JVM differential tests
compare every available region and type, complete typed hierarchy, all-pairs
containment/comparison, every metadata alias/code mapping and numeric lookups from
-20 through 1200 with ICU4J. Cold isolated class-loader tests launch eight concurrent
initial lookups and verify one region identity for both implementations.

Full JVM, JS Node and macOS Arm64 suites pass; Linux x64 test sources compile.
A subsequent full JVM run passes with the cold concurrency test, and the data
generator check passes for all 46 resources. LikelySubtags and ULocale canonicalize/
addLikelySubtags remain to be ported; the complete ICU4J objective remains active.


### Common LikelySubtags engine and ULocale maximization/minimization

LikelySubtags now loads optional language/region alias arrays, the compact byte
trie and decoded LSR table from langInfo. Its data equality and hash behavior,
optimized first-letter states, default/unknown fallbacks, macroregion handling,
explicit-subtag flags and private-use/pseudolocale identities follow ICU4J.
Each lookup clones the trie cursor, sharing only the immutable serialized data.
Matcher canonicalization retains the source's limited language/region alias rules
and its conditional loss of variants/keywords when an alias changes.

ULocale.addLikelySubtags and both minimizeSubtags strategies are implemented in
Common code. Their trailing variant/keyword reconstruction follows the source,
including empty-region separators and null-favor selection. Minimization's raw
lookup intentionally does not apply the maximizer's alias map. compareLikely
ports the source lookup/cache-bit behavior for future locale-distance matching;
toString enumerates and sorts the complete trie table. A JVM extension adds the
java.util.Locale maximization overload using raw Java fields, preserving its
conversion differences from ULocale and private-use handling.

Common tests cover maximization/minimization, trailing identity, unknowns,
pseudolocales, aliases and all pinned LSR records. Legacy language aliases in the
data are canonicalized before checking the maximization round trip. JVM tests
compare all data fields and serialized trie bytes, the complete formatted trie,
3,564 language/script/region combinations with both unknown modes and three favor
choices, and every full resource tuple against ICU4J. Additional tests cover Java
locale inputs, variants/keywords/private use and compareLikely's updated cache bits.

Full JVM, JS Node and macOS Arm64 suites pass; Linux x64 test sources compile.
ULocale's full CLDR canonicalize/AliasReplacer and the remainder of ICU4J are still
unfinished. No new binary resources are needed for this stage.


### Common ULocale CLDR canonicalization

ULocale.canonicalize and both createCanonical factories now port legacy mappings,
the Norwegian NY rule, the upstream known-canonicalized shortcut and the complete
metadata-backed AliasReplacer. Replacement follows language/region/variant search
precedence, then territory, script and variant rules, restarting after each change
with the original loop bound. Multi-replacement territories select the likely
region when available; existing explicit fields and keywords retain source rules.
The hepburn/heploc special case and intentionally ignored replacement extensions
match ICU4J.

Shared code loads and validates all five metadata tables lazily, retrying after
failed initialization. Language-key validation retains the actual behavior of
upstream's single-argument Java Locale guard, which has no script/country fields.
Subdivision/region-override keywords apply first replacement and zzzz suffix rules.
Transformed extensions canonicalize their language, map types and sort tfields,
including the source's handling of malformed values and duplicate keys. The
implementation preserves Java split's trailing-empty behavior and region deletion
rather than imposing additional validation.

Common tests cover legacy/CLDR aliases, explicit fields, multi-region resolution,
keywords, all subdivision entries, transformed extensions and null boundaries.
JVM differential tests compare every entry in language/script/territory/variant/
subdivision tables in multiple contexts, both factories and static canonicalize,
legacy/POSIX/undefined forms and malformed/transformed keyword values. Cold
concurrent initialization verifies complete metadata publication. Isolated JVM
fixtures replace metadata through ICUBinary.dataPath to verify alias-cycle loop
diagnostics, language guard behavior and script/territory/variant/subdivision
validation errors against ICU4J.

Full JVM, JS Node and macOS Arm64 suites pass; Linux x64 test sources compile.
A subsequent full JVM suite also passes with malformed-data fixtures. Display
names and other locale services, and the complete ICU4J port, remain unfinished.


### Common LocaleData and supplemental region selection

LocaleData now ports exemplar/static-instance factories, no-substitute behavior,
quotation delimiters, language display pattern/separator, measurement-system
singletons, paper dimensions and cached CLDR version. The no-argument factory uses
FORMAT default; an explicit null locale uses the general default. Currency exemplars
retain the frozen UnicodeSet.EMPTY singleton, and root fallback suppression follows
bundle identity rather than the requested locale. Language bundle lookup failures
are retained for later display-pattern calls, as upstream does.

ULocale.getRegionForSupplementalData implements rg override, explicit country,
optional sd override and likely-region inference. RegionValidateMap ports the
pinned validation bits, typed equality, overridable value function, mutable builtin
and protected nullable map behavior. JVM case conversion uses the Java default
locale, including Turkish/Azeri quirks and resulting bounds errors. JS/Native use
portable casing with the current ICU default's Turkic rules. Explicit checked array
access on JS preserves bounds failures for exemplar/delimiter keys, dimensions and
region mask access instead of allowing undefined values. UnicodeSet.EMPTY and
ALL_CODE_POINTS are added as frozen constants.

Ten pinned resources add core en/fr/ar/ja and language root/pool/en/fr/ar/ja;
the manifest now contains 56 resources. Language resources record their upstream
langdata source paths. This stage does not embed every locale payload.

Common tests cover pinned exemplar data, delimiters/patterns, measurement/paper,
region selection, nullable/override bounds and frozen constants. JVM differential
tests compare exemplar options/types, no-substitute fallback and display strings
across locales; all region measurement/paper results; every alphabetic validation
code under ROOT/Turkish/Azeri Java defaults; and FORMAT versus general factory
selection. A reentrant availability load-count test now selects strong cache
strength so Native weak-reference reclamation does not alter its count; its exact
three-load trace is compared against ICU4J.

Full JVM, JS Node and macOS Arm64 suites pass; Linux x64 test sources compile.
The data generator check passes for all 56 resources. Locale display-name services,
remaining locale payloads and the complete ICU4J port remain unfinished.


### Common SimpleFormatter and standalone pattern engine

SimpleFormatter now implements its complete public compiler/format/append/replace/
text/debug API in Common code. The standalone SimpleFormatterImpl engine preserves
compiled UTF-16 tape layout, interned common patterns, apostrophe quoting, argument
syntax and min/max diagnostics, 255 maximum argument index and 65,279-unit literal
segments. Formatting retains null values, last-occurrence offsets, partial writes,
result-alias rejection on append and result-copy/initial-alias handling on replace.
Nullable-array Values adapters express Java null-vararg-array behavior in Common.

The engine also ports raw pattern formatting, argument limits, literal text/code-unit/
code-point/prefix lengths and IterInternal state/argument traversal. JVM iterator
Appendable IOExceptions are wrapped in ICUUncheckedIOException. Segment-local code
point counting preserves surrogate-pair splits at long-literal boundaries. Sparse
patterns retain the source text getter's negative-capacity failure; JS explicitly
rejects that capacity. Wrapper/engine calls pass argument arrays directly without
extra spread copies, preserving callbacks that mutate later argument values.

Common tests cover quoting, nulls, malformed syntax, offsets/aliases, long segments
and iteration. JVM differential tests compare 2,000 generated patterns plus explicit
edge/long patterns under four argument limits, including exact tape/builder state,
error messages and all getters. Append/replace tests compare alias/count/offset-length
matrices with partial writes; iterator/IO and live Java argument-array callback tests
also match ICU4J. Full JVM, JS Node and macOS Arm64 suites pass; Linux x64 test sources
compile.

SimpleFormatterImpl.formatPrefixSuffix is still pending because its
FormattedStringBuilder and formatting-field dependencies are not yet ported.
This stage supplies the standalone formatting dependency for locale display names;
those services and the complete ICU4J port remain unfinished.


### Common FormattedStringBuilder and formatter prefix/suffix

FormattedStringBuilder now ports centered char/field storage, prepend/append growth,
insert/splice/removal, code-point access, append positions/fields, copy/subsequence,
char/field array snapshots, wrapped-field identity comparison, debug rendering and
unsupported mutable equality/hash operations. Copies and clear preserve upstream's
append metadata behavior. CharSequence self-insertion retains source aliasing;
typed builder insertion rejects self. Invalid code points preserve buffer mutation
before Character-style validation, matching the original operation order.

FormatField bridges to java.text.Format.Field on JVM and portable field identities
on JS/Native. Extracted NumberFormatField implements the numeric singleton names,
debug tokens and readResolve mapping in preparation for NumberFormat. JVM
serialization canonicalizes recognized names and rejects APPROXIMATELY_SIGN, as
the source does. This extracted field type does not complete NumberFormat or its
Java member/static facade compatibility.

SimpleFormatterImpl.formatPrefixSuffix is now implemented for attributed spans.
It preserves the source's compiled-slice behavior, including long no-argument
patterns with embedded segment markers. JVM code-point access delegates to
Character's array operations; JS/Native share forward/backward surrogate rules
and checked physical array reads.

Common tests cover field storage, edits/copies, code points, debug tokens, wrappers,
append offsets and prefix/suffix application. JVM differential tests compare 2,500
randomized edits at five initial capacities, complete buffer/field/code-point
snapshots, long wrapper patterns and append positions. Additional tests cover
char/field arrays, CharSequence self-aliasing, invalid-code-point partial state,
wrapped fields, invalid field diagnostics and numeric-field serialization.

Full JVM, JS Node and macOS Arm64 suites pass; Linux x64 test sources compile.
A subsequent full JVM suite passes with additional alias/failure tests. Locale
display names, full number formatting and the rest of ICU4J remain unfinished.


### Common constrained positions and fraction field metadata

ConstrainedFieldPosition now implements reset, field/class/value constraints,
range/current-state getters, int64 iteration context, matching and debug output.
Constraint changes preserve range/context; setState changes the current field/value
without validating constraints in production. Field matching uses identity, and
value matching preserves Objects.equals identity short-circuit and left-hand
comparison direction. Nullable field/value constraints and exact null-rejection
messages follow ICU4J. Constructor initialization invokes virtual reset, including
subclass overrides that leave constraints uninitialized.

Common class constraints use KClass.isInstance; a JVM Class overload bridges Java
class constraints. FormatFieldPosition uses java.text.FieldPosition on JVM and
portable field/attribute/index carriers on JS/Native. UFieldPosition implements
all constructors and visible-fraction-digit metadata without changing the base
position's equality/hash semantics. Portable carriers follow the FieldPosition
public state contract; Java package-private formatting delegates are not part of
that boundary.

Common tests cover state/constraint transitions, nullable fields, mutable value
identity, constructor override behavior, and fraction metadata. JVM differential
tests compare 1,200 release-mode state transitions and field/value/class match
matrices against an isolated ICU4J class with assertions disabled. Additional
checks compare equality callback direction, constructor metadata, extreme int64
values, base position equality/hash and diagnostic strings.

Full JVM, JS Node and macOS Arm64 suites pass; Linux x64 test sources compile.
FormattedValue/FormattedValueStringBuilderImpl span iteration, list/span field
identities and attributed iterator exports remain unfinished, along with the
complete ICU4J port.


### Common StaticUnicodeSets

StaticUnicodeSets now implements every key, frozen-set lookup, first-match set
selection and currency selection. Its resource sink loads general/number parse
lenients from the pinned root bundle, skips date data and preserves symbol
classification precedence. Other grouping separators include apostrophe data;
lenient/strict separator and digit unions are frozen after construction. Infinity
and approximately-sign classes retain the source's curated patterns.

The default formatting trim set is specifically Zs + TAB + Bidi_Control +
Variation_Selector, while strict ignorables are only Bidi_Control. The port does
not substitute the broader Default_Ignorable_Code_Point property. Null-key lookup
returns UnicodeSet.EMPTY; the explicit EMPTY key retains a separately allocated
frozen set, matching source identity. Unknown parse classes throw the source
AssertionError diagnostic; missing required apostrophe data is not silently filled.

Common tests verify freeze/identity, symbols, currency selection and trim span
boundaries. JVM tests compare every set's complete pattern, size and frozen state,
set/currency choices and forward/backward spans against ICU4J. A cold isolated
class-loader test starts eight concurrent lookups and verifies one published frozen
set. Full JVM, JS Node and macOS Arm64 suites pass; Linux x64 test sources compile.

No new binary resources are required. Formatted-value span iteration/exports,
number parsing and the complete ICU4J port remain unfinished.

### Common formatted-value field and span iteration

FormattedValueStringBuilderImpl now applies and locates span placeholders and
iterates constrained numeric, list and span fields in Common Kotlin. Integer and
numeric ranges coalesce as in ICU4J; trimming uses StaticUnicodeSets rather than
the broader default-ignorable property. List element ranges retain whitespace.
FormatSpanField, ListFormatField and ListSpanField provide the extracted field
identities; full UFormat and ListFormatter remain unfinished. Java attributed
iterator export is supplied by a JVM extension; the Common FormattedValue
interface and a portable attributed iterator are still pending.

Generated JVM oracle tests compare constrained iteration, legacy field positions
and attributed character runs with ICU4J, including UTF-16 supplementary text,
empty segments, bidi marks and variation selectors. List spans cover complete
list elements: arbitrary spans across mixed numeric fields can cycle in ICU4J
itself and are not used for unbounded attributed export tests. Common tests cover
numeric coalescing, trim boundaries and list span/element ordering. Full JVM,
JS Node and macOS Arm64 suites pass; Linux x64 test sources compile.

### Common FormattedValue and attributed character export

FormattedValue now exposes the complete text/append/field-iteration/attributed-
iterator contract in Common Kotlin. FormatAttribute and
FormatAttributedCharacterIterator bridge directly to Java types on JVM. JS and
Native use a Common immutable attributed snapshot with independently cloned
cursors, UTF-16 navigation, attributes and run limits constrained by one field or
a set of fields. FormatField now shares the attribute identity base on all targets.
FormattedValueStringBuilderImpl.toCharacterIterator is a Common member instead
of a JVM-only extension.

The portable snapshot stores ranges rather than allocating a map per code unit.
Unconstrained runs retain added range boundaries even when adjacent values are
equal; field-constrained runs can cross those boundaries. Empty/end cursors,
invalid index failures, null constraints, clone equality and cursor hash codes
match Java's AttributedString iterator. JVM oracle tests compare 160 generated
snapshots with overlapping ranges and random cursor operations; Common tests
exercise exported numeric overlap, clones and adjacent equal-valued runs.
Full JVM, JS Node and macOS Arm64 suites pass; Linux x64 test sources compile.

ListFormatter and its FormattedList implementation remain the next formatting
stage. The full ICU4J port remains incomplete.

### SimpleCache and append-to utility

ICUCache and SimpleCache now retain the whole-map reference strategy, SOFT/WEAK
selection, null/sentinel keys, replacement and clearing. Map operations use the
platform cache lock and the reference is atomically published. JVM uses actual
SoftReference/WeakReference; JS and Native use their existing weak-reference
fallback because those runtimes have no Java soft-reference primitive.
Utility.appendTo preserves a single Appendable.append call, returns the input
appendable and wraps JVM IOException in ICUUncheckedIOException. Common cache
checks and the ListFormatter append-failure oracle pass in the full platform run.

### ListFormatter and FormattedList

ListFormatter now provides AND/OR/UNITS and WIDE/SHORT/NARROW styles, resource
fallback, default FORMAT locale factories, supplied patterns, collection/vararg
formatting, pattern generation and immutable FormattedList results. Its builder
preserves reversed/repeated/missing argument behavior, element callback order,
empty-element offsets, span values and literal/element fields. Nested Field and
SpanField aliases expose the already-portable field identities. Java Locale
factories are JVM extensions. Spanish and Hebrew core bundles are embedded,
bringing the checked resource manifest to 58 entries.

Contextual handling follows the source's ASCII-only case-insensitive Spanish
regexes and Hebrew-block rule, including line terminator boundaries. Formatted
results expose Common constrained iteration, attributed export, UTF-16 access,
substrings and append-to behavior. JVM oracle tests cover ten locale identifiers,
all nine type/width combinations, contextual tails, malformed-but-compilable
patterns, callback traces and append failures. Common tests exercise language
contexts and reversed supplementary-text spans. Full JVM, JS Node and macOS
Arm64 suites pass; Linux x64 test sources compile; data generation checks pass.

The JS/Native getPatternForNumItems helper currently generates ASCII indices;
JVM retains String.format's FORMAT-locale digit localization. Portable localized
pattern indices still require the numbering-system/number-formatting stage.
Only selected locale payloads are embedded; the full data and ICU4J ports remain
unfinished. SimpleCache remains subclassable as in the source.

### NumberingSystem

NumberingSystem now implements the default Latin constructor, custom factories,
name and locale lookup, available-name enumeration, algorithmic status and digit
validation in Common Kotlin. Its locale cache uses base name plus numbers keyword;
native/finance fall back to default, traditional falls back through native, and
unknown explicit system names fall back to the locale default. Name misses retain
SoftCache's cached-null semantics. LATIN performs its own uncached resource lookup,
matching source initialization. Java Locale lookup is a JVM extension.

Numeric validation checks exactly ten code points without requiring uniqueness or
Unicode decimal digits. Non-decimal numeric radices are rejected by the original
combined count checks; algorithmic radices at least two and null algorithmic
descriptions are accepted. Public getters remain overridable. The complete pinned
numberingSystems.res bundle is embedded; the resource manifest now has 59 entries.

JVM oracle tests compare every available named system, default state, seventeen
locale identifiers with eleven keyword choices, Java locale conversion and null
inputs. Factory comparisons cover extreme radices, repeated/non-digit characters,
supplementary digits and unpaired surrogates. Common tests verify resource ordering,
algorithmic systems, keyword fallback and supplementary descriptions. Full JVM,
JS Node and macOS Arm64 suites pass; Linux x64 test sources compile; generated data
checks pass.

ListFormatter's portable localized pattern indices remain unfinished: the source
uses JDK String.format/DecimalFormatSymbols, whose accepted digit systems and
fallback differ from ICU digit descriptions (for example hanidec and mathbold).
This needs the numeric-symbol/platform formatting stage rather than substituting
the first ICU description character. Complete locale payload coverage, number
formatting and the full ICU4J port remain unfinished.

### Common Grego calendar arithmetic

Grego now implements the complete ICU4J proleptic-Gregorian utility: leap years,
month lengths, epoch-day/year/field conversion, millisecond fields, floor division,
weekday ordinals and ISO-style UTC debug strings. Tuple results use Kotlin Pair.
The port retains Java integer overflow and the source's floorDivide behavior,
including unusual negative denominators. Formatting uses ASCII digits regardless
of locale. Checked array access preserves bounds failures on JS as well.

JVM oracle comparisons cover 607 extreme/random millisecond and day values,
308 extreme/random years across every month, array reuse and negative-denominator
division. Common currency tests cover epoch/leap-day formatting and month bounds.
Full JVM, JS Node and macOS Arm64 suites pass; Linux x64 test sources compile.

### CurrencyMetaInfo and ICUCurrencyMetaInfo

CurrencyMetaInfo now exposes immutable filters, currency records/digits and the
singleton data provider in Common Kotlin. Filters preserve nullable region/code,
raw date ranges, inclusive overlap, legal-tender constraints, source equality/hash
and debug strings. JVM Date adapters preserve null range endpoints and repeated
getTime callbacks. now() uses the platform clock. CurrencyUsage is extracted as a
Common enum until the complete Currency class is ported.

ICUCurrencyMetaInfo reads the complete pinned curr/supplementalData.res bundle,
retains resource/priority order, unique first-seen currency/region lists and the
region-only fast path, and returns unmodifiable lists. Date words are combined as
signed-high/unsigned-low 64-bit values without extra end-of-day adjustment.
STANDARD/CASH fraction and rounding data preserve unknown-code and null-usage
fallback. Provider construction failures retain the source substitute behavior,
including its ignored noSubstitute argument. The checked manifest has 60 resources.

JVM oracle tests compare the whole record table, all regions/currencies, tender
filters, every record's endpoint/neighbor/reversed date queries, both usage modes,
filter hashes/debug output and mutable Date callbacks. Common tests exercise data
availability, historical currencies, cash rounding and collection immutability.
The old source comment about empty AQ records is not treated as current data;
queries match the pinned resource and ICU4J oracle. Full JVM, JS Node and macOS
Arm64 suites pass; Linux x64 test sources compile; data generation checks pass.

Currency display names/provider, full Currency/MeasureUnit, DecimalFormatSymbols,
number formatting and the complete ICU4J port remain unfinished.

### StandardPlural

StandardPlural now exposes all eight constants, immutable VALUES, keyword/index
lookup, OTHER defaults and invalid-key exceptions. The numeric aliases 0/1 and
=0/=1 retain the original EQ_0/EQ_1 ordinals. Generic CharSequence lookup preserves
the source's repeated character reads and invalid-key toString callback rather
than converting the input up front. Currency display oracle tests compare keyword
lookups and callback traces; Common tests cover aliases, defaults and null inputs.
Full JVM, JS Node and macOS Arm64 suites pass; Linux x64 test sources compile.

### CurrencyDisplayNames, CurrencyData and ICU currency display provider

CurrencyDisplayNames now provides Common factories and symbol/name/plural lookup;
Java Locale factories are JVM extensions. CurrencyData supplies complete display
contracts, format/spacing records and fallback DefaultInfo instances.
ICUCurrencyDisplayInfoProvider implements locale/substitution selection, regular,
narrow, formal and variant symbols, plural fallback, parsing maps, unit patterns,
currency-specific formatting and spacing. The resource sink retains no-root
behavior, first-filled formatting slots, parent parsing-map overwrites, variant-only
parsing symbols and strict diagnostics for unexpected types/plural keys.

Single-item locale/format/variant/plural caches use atomic publication. Parsing
holds an actual soft reference on JVM and the established weak-reference fallback
on JS/Native. The default resource loader allows extra JVM classpath locale bundles.
Source behaviors are intentionally retained: parsing/unit maps are mutable despite
the public documentation, spacing arrays are shared, DEFAULT spacing flags remain
false, and cached null ISO keys can cause subsequent NullPointerException. Plural
fallback with no substitution still depends on the unit-pattern key set.

Eight pinned curr bundles (root, pool, en, fr, ar, ja, es, he) are embedded, bringing
the checked manifest to 68 resources. JVM oracle tests compare all metadata currency
codes across twelve locales and both substitution modes, complete parsing maps,
all symbol styles/plural categories, format info and spacing. Regional classpath
locales include en_CA, en_GB, es_MX and zh_TW. Additional tests cover mutable maps,
null cache keys, DefaultInfo and CharSequence callbacks. Common tests verify
localized output, no-substitution behavior, mutable maps and spacing array aliasing.
Full JVM, JS Node and macOS Arm64 suites pass; Linux x64 test sources compile;
generated data checks pass.

Full Currency/MeasureUnit, DecimalFormatSymbols, remaining locale payloads and the
complete ICU4J port remain unfinished.

### IllegalIcuArgumentException

IllegalIcuArgumentException now provides all three source constructors and a
covariant initCause API. JVM retains Throwable's synchronized state transitions
and the original serialVersionUID. JS/Native atomically retain initialized versus
uninitialized null causes, reject self-causation and cause overwrites, and attach
the receiving exception to rejection errors. JVM oracle tests compare constructor
messages/causes, repeated initCause calls and serialization UID; Common tests
exercise null initialization, overwrite and self-causation on all targets.
Full JVM, JS Node and macOS Arm64 suites pass; Linux x64 test sources compile.

### Unit aliases, identifier data and measure prefixes

UnitAliases now loads every metadata alias/unit replacement, retains missing-
replacement diagnostics, nullable lookup and fresh unmodifiable alias lists.
MeasurePrefix and UnitComplexity extract all source enums for the shared parser;
prefix order, identifiers, bases and powers match ICU4J, including the newest SI
prefixes and binary prefixes. The future MeasureUnit facade can expose nested
aliases for these shared types.

UnitIdentifierData extracts the resource-backed static identifier/category portion
of UnitsData, separating it from downstream conversion objects. It reads actual
unitQuantities ordering and convertUnits targets, excludes kilogram in favor of
prefixless gram, preserves source parser offsets, exposes the original mutable
simple-unit array and retains alias replacement indexing. Missing targets are
skipped as in assertion-disabled production; unknown category mappings fail during
category-array construction. Category lookup uses checked array access on JS.
The complete pinned units.res bundle is embedded; 69 manifest resources verify.

JVM oracle tests compare every simple identifier/category index, complete category
arrays/base-unit mapping, alias ordering/replacements and every enum field with
ICU4J. Common tests verify category linkage, gram handling, immutable alias lists
and prefixes. Full JVM, JS Node and macOS Arm64 suites pass; Linux x64 test sources
compile; generated data checks pass.

UnitsData's conversion/preferences facade is not claimed complete. MeasureUnitImpl,
SingleUnitImpl, public MeasureUnit/Currency and downstream conversion/formatting
remain unfinished, along with the full ICU4J port.

### Shared unit identifier parser and mutable model

MeasureUnitImpl and SingleUnitImpl now provide the shared identifier model,
longest-token CharsTrie parser, alias substitution, prefix/power/compound/mixed
syntax, constant denominators, copying, coalescing, simplification, reciprocal and
dimensionality operations, indexed extraction and serialization. Each parser clones
the shared trie and uses real UnitIdentifierData tables. Sorting follows category,
simple-unit index and source SI/binary-prefix comparison; mixed units retain order.
Virtual list/complexity callbacks and mutable identifier-cache behavior are retained.

The constant parser retains BigDecimal's scale <= 0 requirement and non-negative
Long range, rather than rounding integer-valued fractions. JVM uses BigDecimal
for exact host diagnostics; JS/Native use an exact coefficient/scale implementation
validated against it. Exponents can exceed Int.MAX_VALUE when the resulting scale
still fits Int (for example 1e2147483648). No floating-point approximation is used.
Unicode decimal characters and zero at huge valid exponents are supported.

Source quirks remain: zero denominators do not count as an existing constant,
applyDimensionality and constant setters do not invalidate cached identifiers,
simplification drops denominator/complexity metadata, and a leading denominator
with a constant can serialize with a leading '-per-'. Metadata alias spellings and
replacements that the ICU4J parser rejects retain the same error outcomes.

JVM oracle tests compare all simple units, aliases and prefixes, 500 random unit
triples across four syntax shapes, malformed input, mutable model operations and
subclass callbacks. Portable constant validation is compared with BigDecimal on
2,000 generated inputs plus scale/range boundary vectors. Common tests cover
canonical output, mixed units, copying, aliases and exact constants. Full JVM,
JS Node and macOS Arm64 suites pass; Linux x64 test sources compile; 69 generated
resource files verify.

The public MeasureUnit build/toString bridges and conversion comparators are not
ported yet; these implementation classes are not claimed complete. Full
MeasureUnit/Currency, conversion/preferences and the complete ICU4J port remain
unfinished.

### Text trie and collection views

TextTrieMap now uses a shared compressed prefix trie with simple Unicode case
folding, longest/all-prefix callbacks, partial-match reporting, lead code points,
and live mutable value iterators. JVM searches synchronize on the trie instance;
JS/Native use the shared reentrant lock. CharIterator retains source exhaustion
and surrogate-pair behavior, including unchanged indices after failed reads.
CollectionSet preserves the source live collection view, duplicate entries,
identity equality, delegated mutations and typed-array sentinel behavior. JVM
array conversions and collection calls retain Java backing-collection semantics.

Oracle tests compare generated Unicode keys and queries, malformed offsets,
iterator removal, array conversion and immutable collections against ICU4J 78.3.
Common tests exercise compressed branches, supplementary folding and reentrant
callbacks. JVM, JS Node and macOS Arm64 suites pass; Linux x64 tests compile.
These dependencies do not complete Currency, MeasureUnit or the full ICU4J port.

### Currency parsing implementation

CurrencyParsing extracts Currency's localized-name and symbol parsing into common
code, ready for the future public Currency facade. Names use simple case folding;
symbols are case sensitive and include StaticUnicodeSets equivalent forms. The
longest match wins, with names winning equal lengths. ParsePosition advances only
by the matched length and its errorIndex remains unchanged. The original mutable
cached parsing tries and first-value selection are preserved.

JVM oracle coverage compares loaded names/symbols in 11 locales across parsing
modes, uppercase inputs, trailing text and offsets. Injected cached trie entries
verify tie handling, longer symbols and invalid-offset exceptions/position state.
Common tests cover names, equivalent yen symbols, failures and offsets. Full JVM,
JS Node and macOS Arm64 suites pass; Linux x64 tests compile. An additional JVM
suite run passes after the mutable-cache boundary tests were added. Public
Currency and MeasureUnit are still unfinished; no facade completeness is claimed.

### Currency metadata queries

CurrencyMetadata extracts Currency's ISO-code validation, numeric codes, regional
currency selection, historical available-code queries, locale keyword preferences,
availability and fraction/rounding calculations. It uses exact Long epoch dates,
nullable range endpoints, source validation order and reclaimable whole-code
caches. The pinned currencyNumericCodes resource is now embedded (70 resources).
Legal-tender codes take precedence in regional selection, with non-tender fallback
only when the tender list is empty. Unknown numeric codes return zero.

Oracle tests compare every metadata currency and region, both usage modes,
historical/extreme dates, invalid formats, reversed ranges and keyword preferences
against ICU4J 78.3. Every numeric-code resource entry is compared directly. Common
tests cover normalization, ISO numbers, region selection, cash rounding and date
queries. Full JVM, JS Node and macOS Arm64 suites pass; Linux x64 tests compile;
all generated resources verify. This is internal implementation work; public
Currency, MeasureUnit and the complete ICU4J port remain unfinished.

### Currency display dispatch and further locale data

CurrencyNames extracts Currency's symbol/name/plural dispatch with ordinary,
narrow, formal and variant symbols, long names, plural fallback and default-locale
convenience paths. Choice-format output arrays are updated before lookup/style
validation, retaining source empty-array failures. The plural overload delegates
to ordinary dispatch for every style other than PLURAL_LONG_NAME.

Currency data for de, zh, ru, hi, it, pt, ko and nl is embedded from ICU 78.3.
All eight resources participate in byte loading, exists checks and directory
visitation. There are now 78 pinned generated resources. Oracle tests compare
all metadata currency codes across 15 locales, all styles, valid/unknown/null
plural keys, choice arrays and failures against ICU4J. NullPointerException types
are compared without JVM-generated null-dereference diagnostic text. Common tests
cover dispatch, output mutation, resource discovery and localized names. Full JVM,
JS Node and macOS Arm64 suites pass; Linux x64 test sources compile; all resources
verify. CurrencyNames remains internal pending the complete Currency facade;
MeasureUnit, service registration and the full ICU4J port remain unfinished.

### TextTrieMap live value iterator fidelity

Trie values now use a shared linked list instead of ArrayList, matching ICU4J's
LinkedList iterator behavior. hasNext compares the cursor against the live size,
including when another iterator removes all entries; next and remove check for
structural modification before exhaustion/state checks. Removed nodes release
value and link references so stale iterators do not retain deleted payloads.
Null values and empty but previously populated lists remain supported.

Oracle tests compare 200 generated schedules of 60 operations involving multiple
iterators, insertions, removals and full deletion against ICU4J. Common tests
cover stale iterators after deletion, empty iterators, subsequent insertion and
fail-fast exception ordering. Full JVM, JS Node and macOS Arm64 suites pass;
Linux x64 tests compile. The full ICU4J port remains unfinished.

### MeasureUnit resource enumeration

MeasureUnitResourceCatalog extracts the resource sinks used to populate the
future MeasureUnit instance cache. It enumerates English unit types/names with
fallback, skips compound and coordinate pattern groups, then enumerates ISO
currency codes. Every callback is preserved in source order, including fallback
visits; deduplication and one-time population belong to the future public cache.
Caller exceptions propagate and a later enumeration can run normally.

The pinned unit/en, unit/root, unit/pool and unit/res_index bundles are embedded;
82 generated resources now verify. JVM oracle tests compare the full ordered
callback sequence with the ICU4J sinks and check catalog entries against the
source public available-unit set. Common tests cover units, currencies, excluded
pattern groups, resource discovery and callback failures/retries. Full JVM,
JS Node and macOS Arm64 suites pass; Linux x64 tests compile. Public MeasureUnit,
Currency service registration and the complete ICU4J port remain unfinished.

### Service notification infrastructure

ICUNotifier now provides common identity-based registration/removal, listener
validation, locked snapshot capture and serial asynchronous notifications. JVM
EventListener aliases java.util.EventListener; delivery uses a dedicated daemon
thread with the source wait/queue and InterruptedException behavior. Native uses
an individual Worker; JS uses a microtask event pump. Their common FIFO pump
stops permanently after a callback failure, retaining queued/future batches;
Native worker reporting and JS console reporting are host error-reporting paths.

JVM oracle tests compare ordered snapshots under a blocked first callback,
duplicate/equal-but-distinct registrations, removals and additions. They verify
that unchecked callback failures terminate the daemon thread and that later
notifications do not restart it. Common tests cover registration validation,
FIFO batching and stopped queues. JS/Native tests exercise real asynchronous
reentrant delivery, snapshots and duplicate registration; Native confirms a
background worker performs callbacks. Full JVM, JS Node and macOS Arm64 suites
pass; Linux x64 tests compile. ICUService/ICULocaleService, Currency registration
and the complete ICU4J port remain unfinished.

### Service reader/writer locking

ICURWLock now has common statistics, snapshots/reset/clear and platform lock
backends. Stats retains mutable public JVM fields. JVM uses the same nonfair
ReentrantReadWriteLock as ICU4J. Native uses POSIX mutex/condition waits with
per-worker read ownership, exclusive/reentrant write ownership, reentrant reads,
write-to-read downgrade and bounded hold counts. Native synchronization resources
are released by cleaners; failed initialization frees allocated resources.
JS runs the ownership model on its single event loop. Unsupported read-to-write
upgrades throw on JS rather than blocking the event loop; JVM/Native block, as
such an upgrade is unsupported by the source lock contract.

Common tests cover nested holds, downgrade, invalid releases and statistics
snapshot independence. JVM oracle tests compare statistics and release errors.
Native worker tests prove simultaneous readers, writers waiting for all readers,
wrong-thread release rejection and 2,000 protected shared updates from four
workers. Full JVM, JS Node and macOS Arm64 suites pass; Linux x64 tests compile.
ICUService/ICULocaleService, public Currency/MeasureUnit and the complete ICU4J
port remain unfinished.

### ICUService lookup and registry

ICUService now contains Key, Factory and SimpleFactory; factory registration,
unregistration/reset; fallback lookup and descriptor caching; actual-ID output;
visible-ID enumeration and single-ID display names; default hooks; service
listeners and lock statistics. Factory changes invalidate caches. Delegating
lookups start after the calling factory, retain source cache-hit behavior and
avoid publishing delegated results. Visible maps preserve the oldest-to-newest
factory update order and unmodifiable views; filtered ID sets and factory-list
copies remain mutable as in ICU4J. Protected JVM fields and SimpleFactory's
Java two-/three-argument constructors are retained.

Common tests cover hidden overrides, cache invalidation, old visible snapshots,
actual-ID output, factory delegation, callback errors and reset. JVM oracle tests
compare fallback chains, repeated cached lookups, visible filtering, single-ID
display names, delegated traces and statistics. Java reflection verifies the
constructor and protected-field contracts. Full JVM, JS Node and macOS Arm64
suites pass; Linux x64 tests compile. A further JVM suite passes after adding the
JVM-only constructor overload and Java API checks.

Sorted display-name enumeration/cache and debug tracing are not ported yet;
ICUService is not claimed complete. ICULocaleService, public Currency/MeasureUnit
and the complete ICU4J port remain unfinished.

### Locale service keys

LocaleServiceKey extracts the complete ICULocaleService.LocaleKey implementation
pending the locale-service facade. It retains canonical factories, original IDs,
kind prefixes, descriptors with keyword suffixes, underscore-group fallback,
canonical fallback locale, terminal root and strict parent matching. Constructor
state and virtual kind/currentID callbacks preserve source behavior. Exhausted
currentLocale distinguishes keyword-bearing/literal-root keys (string 'null')
from ordinary keys (null constructor argument failure), as ICU4J does.

Source quirks remain: canonical factories normalize root to an empty ID and can
then visit the supplied fallback locale; the protected literal-root constructor
has no fallback. Canonical fallback strings are not re-normalized by the key.
Common tests cover keywords, kinds, factory/literal root and exhaustion. JVM
oracle tests compare a locale/fallback/kind matrix using both factories, direct
protected construction and subclass callbacks, including all fallback states
and terminal exceptions. Full JVM, JS Node and macOS Arm64 suites pass; Linux x64
tests compile. ICULocaleService factories/facade, sorted ICUService display names,
public Currency/MeasureUnit and the complete ICU4J port remain unfinished.

### Locale service lookup/default core

LocaleServiceCore extracts ICULocaleService's lookup overloads, key creation and
validateFallbackLocale. Default locale identity changes update its base-name
fallback, clear only the service-object cache, then publish the new default
reference. String/ULocale key overloads preserve keyword and kind behavior.
Locale lookup converts returned descriptors to actual ULocales by stripping any
kind prefix; misses leave actual-return arrays unchanged and successful empty
arrays retain bounds failures.

Common tests cover default-locale changes invalidating cached fallback results,
keyword-bearing actual locales, empty arrays and misses/null locale. JVM oracle
tests compare successive default changes, fallback queries, kinds and actual
locale outputs against ICU4J, restoring defaults afterwards. Full JVM, JS Node
and macOS Arm64 suites pass; Linux x64 test sources compile. This core is internal
pending the complete ICULocaleService facade and locale factories; sorted display
names, public Currency/MeasureUnit and the full ICU4J port remain unfinished.

### Locale display contexts and resource table access

DisplayContext is ported with every source enum/type/value/ordinal. The complete
ICUResourceTableAccess lookup preserves resource inheritance, deprecated language
and country IDs, optional subtables, explicit Fallback locale resources, self-loop
termination, empty-string substitution and caught malformed-resource exceptions.
An internal ICUResourceBundle base-name bridge retains virtual protected lookup.
LocaleDisplayNameDataTable and ResourceLocaleDisplayNameDataTable extract the
source plain/substituting and resource-backed data tables for the future display
name implementation. Region root/pool/en data is embedded; 85 resources verify.

JVM oracle tests compare all keys from display tables in language/region bundles
for en, en_US and root, plus deprecated/missing codes, optional/missing subtables,
null/string defaults and all DisplayContext metadata. Common tests cover actual
language/region names, substitution/no-substitution, plain tables, malformed
resources and region resource discovery. Full JVM, JS Node and macOS Arm64 suites
pass; Linux x64 tests compile. LocaleDisplayNames/LocaleDisplayNamesImpl, locale
service factories, sorted service display names, public Currency/MeasureUnit and
the complete ICU4J port remain unfinished.

### Public locale-sensitive case mapping

CaseMap.Lower/Upper now expose locale-sensitive string/appendable APIs and cached
omitUnchangedText options over the existing shared casing algorithms. Common
callers use ULocale; JVM adapters also accept java.util.Locale. Null locale uses
the current default. Greek, Turkic and Lithuanian rules use the shared ICU
algorithms rather than host string casing. Appendable mapping retains Edits reset
and precise change ranges, including null source/destination behavior: null
outputs succeed if no output is written, and source failures reset Edits first.
JVM IOException is wrapped as ICUUncheckedIOException with the original cause;
other exceptions retain identity. Folding now shares these appendable boundary
rules and error wrapping.

Oracle tests compare eight locales, 200 generated Unicode strings plus contextual
and length-boundary examples, both omit modes, prefixed appendables and complete
fine edit traces. Null-input/output outcomes and edits, IOException causes and
current-default behavior are verified separately. Common tests cover expansions,
contextual mappings, options, edit resets and appendable failures. Full JVM,
JS Node and macOS Arm64 suites pass; Linux x64 tests compile. An additional JVM
suite passes after adding the default-locale test. CaseMap.Title adapters, locale
BreakIterator factories, LocaleDisplayNames, service display-name enumeration,
public Currency/MeasureUnit and the complete ICU4J port remain unfinished.

### Locale-loaded sentence suppression

FilteredBreakIteratorBuilder.getInstance(ULocale) now loads the locale's
exceptions/SentenceBreak resource through LOCALE_ROOT fallback, matching the
SimpleFilteredSentenceBreakIterator.Builder locale constructor. A JVM adapter
accepts java.util.Locale. Builders retain mutable suppression sets and wrapped
iterator snapshots; existing suppressions can be removed/re-added. Break data
for en, fr, de and es is embedded and included in full-name scanning; 89 pinned
resources verify.

Oracle tests enumerate every source suppression for loaded locales and fallback
variants, compare filtered forward boundaries plus following/preceding at all
positions, and compare mutation results. A Java Locale adapter test verifies
same initial suppressions. Common tests cover English abbreviation loading,
removal/re-addition, actual sentence boundaries and resource discovery. The
embedded break-locale inventory expectation was updated for the added resources.
Full JVM, JS Node and macOS Arm64 suites pass; Linux x64 tests compile. General
BreakIterator factories/service registration, CaseMap.Title, LocaleDisplayNames,
public Currency/MeasureUnit and the full ICU4J port remain unfinished.

### Break rule data factory and locale metadata

BreakIteratorDataFactory extracts resource-backed creation from BreakIteratorFactory:
LOCALE_ROOT bundle fallback, kind-to-rule lookup, lb strict/normal/loose variants,
Japanese/Korean lw=phrase selection, binary rule loading and ss=standard filters.
BreakIterator now stores valid/actual locale pairs, validates paired null state,
and preserves metadata in rule-based and filtered clones. ULocale.Type remains
an opaque identity selector with mutable public JVM ACTUAL_LOCALE/VALID_LOCALE
fields, as in the source. Filtered wrappers retain their own initially-null locale
metadata rather than forwarding their delegate's metadata. Factory rule I/O errors
use the source Assert.fail diagnostic format.

Break data for el, ko, zh, zh_Hant, en_US and en_US_POSIX is embedded. All 95 pinned
resources verify; embedded full-name inventory tests include the new locales.
JVM oracle tests invoke the original private factory for all five kinds across
root, English variants, CJK/phrase, Greek, filtered and unknown locales. They
compare Unicode boundary sequences and metadata/clones. Invalid kind resource
errors, mutable selector identity/field modifiers and rule-I/O assertion messages
are verified separately. Common tests cover metadata, clone independence,
filtered output and errors. Full JVM, JS Node and macOS Arm64 suites pass; Linux
x64 tests compile. General BreakIterator service registration/cache/public factories,
CaseMap.Title, LocaleDisplayNames, Currency/MeasureUnit and the full ICU4J port
remain unfinished.

### Public BreakIterator resource factories and prototype cache

BreakIterator now exposes character/word/line/sentence/title factories, default
locale overloads, explicit ULocale overloads and the internal getBreakInstance
entry point. JVM adapters accept java.util.Locale. Each kind has one CacheValue
locale/prototype slot using configured cache strength. On a miss, the factory
clones the new iterator into the cache before returning the original; on a hit it
returns a clone of the prototype. Locale changes replace only that kind's slot.
Null locale validation precedes kind bounds checking. Matching-locale and whole
cache invalidation hooks are ready for future service registration integration.

Common tests prove snapshot independence, separate kinds, replacement and
invalidation, clone text/cursor independence and validation. The creation-count
test enables strong cache values temporarily so eviction cannot affect its
assertions. JVM oracle tests compare public factory boundaries and metadata on
repeated requests across eight locales and all five kinds. Full JVM, JS Node and
macOS Arm64 suites pass; Linux x64 tests compile. After tightening the cache test,
targeted JVM/JS/Native cache tests and Linux test compilation pass. Public factories
currently use the resource creation path; user registration, availability/shim
integration, CaseMap.Title, LocaleDisplayNames, Currency/MeasureUnit and the full
ICU4J port remain unfinished.

### Public CaseMap titlecasing

CaseMap.Title now supplies default word, whole-string and sentence titlecasing,
noLowercase, noBreakAdjustment, adjustToCased and omitUnchangedText options. It
preserves conflicting-option errors, explicit-iterator validation, setText before
case-locale selection, iterator state and nullable destination behavior. Common
callers use ULocale; JVM adapters accept java.util.Locale. The title algorithm
uses shared ICU case data and boundary factories. Internal appendable casing
entry points now preserve IOException wrapping even for iterator callbacks;
string lower/fold paths use those entry points too. Empty-string fast-path checks
follow the source ordering rather than returning before option selection.

Oracle tests compare all title mode/adjustment/lowercase/omit combinations on six
locales with generated Unicode strings, contextual examples and length boundaries,
including complete fine edit traces. Explicit iterator positions, IOException
causes in short/long string and appendable paths, and empty CharSequence callback
order across lower/upper/fold/title match ICU4J. Common tests cover title modes,
Dutch/Turkic mappings, option errors, unchanged output and edit-reset boundaries.
The existing region-validation oracle now compares implicit array failures by
type, since JVM-generated bounds messages can be absent; other diagnostics retain
message comparisons. Full JVM, JS Node and macOS Arm64 suites pass; Linux x64
tests compile. BreakIterator user registration/availability, LocaleDisplayNames,
service display-name enumeration, public Currency/MeasureUnit and the complete
ICU4J port remain unfinished.

### Locale display-name components and capitalization

LocaleDisplayNameComponents extracts the source component lookup and constructor
context rules: language/canonical aliases, stand-alone/contextual scripts, region,
variant, keyword and keyword-value names; short-name fallback; substitution;
contextTransforms flags; sentence-based capitalization; and currency-specific
ASCII-uppercase lookup with raw-value fallback. Pattern compilation and parenthesis
selection are retained for the upcoming composite-name renderer. Contexts use
last-value-wins selection. Currency lookups use the originally requested locale;
capitalization uses the effective display locale and a synchronized reusable
sentence iterator. Short names identical to input codes fall back to full names,
including English US -> United States. Region fr/ar/ja data is embedded; all 98
generated resources verify.

Oracle tests compare seven display locales across length/substitution/four
capitalization modes, component kinds, legacy language/country codes, unknown
values, currency keywords and script-number errors. Common tests cover actual
localized strings, short-name fallback, context override order and capitalization.
Full JVM, JS Node and macOS Arm64 suites pass; Linux x64 tests compile. The engine
is internal pending LocaleDisplayNames' full locale composition, UI lists and
public/cache/provider facade. Locale service factories, BreakIterator registration,
Currency/MeasureUnit and the complete ICU4J port remain unfinished.

### Composite locale display names

The display-name engine now composes full locale names from language, script,
region, combined variant and ordered keywords. Dialect lookup follows the source
language-script-region / language-script / language-region priority and removes
only components covered by a found dialect name. Root/empty language uses und;
no-substitute failures propagate null. Non-dialect components escape parentheses
according to the locale's pattern, keyword labels/values retain source comparison
and keyTypePattern decisions, and capitalization is applied to the final result.
String and ULocale entry points share the same renderer.

Oracle tests compare seven display locales across dialect/length/substitution/
capitalization combinations, fixed edge cases and 80 generated locale IDs with
scripts, regions and keywords (including parenthesized values). Both entry points
match ICU4J 78.3. Common tests cover standard/dialect names, short regions, root,
unknown keywords, no-substitute and final French capitalization. Full JVM,
JS Node and macOS Arm64 suites pass; Linux x64 tests compile. UI list generation
and the public LocaleDisplayNames cache/provider facade remain unfinished, as do
locale service factories, Currency/MeasureUnit and the complete ICU4J port.

### Locale UI lists and item values

The display-name engine now creates UI rows by validating inputs, maximizing and
deduplicating locales by base language, seeding distinguishing scripts/regions
with the language default, and retaining only required differences. Single rows
and minimized fields use FAVOR_SCRIPT. Display/in-self names use standard full
substituting display names independently of caller length/substitution contexts;
only UI_LIST_OR_MENU applies whole-string/no-lowercase titlecasing to UI labels.
The returned list is mutable and sorted with the caller's whole-item comparator.
Null comparators preserve natural-sort failure behavior for multiple rows.

LocaleUiListItem retains source field values, short-circuit equality, hash/string
format and name comparator with modified-locale tie-breaking. JVM sorting uses
List.sort; JS/Native use the portable stable-sort path and matching null-comparator
failure type. Oracle tests compare grouped rows, defaults/unusual scripts/regions,
legacy/unknown locales and keywords, display/in-self sorting, caller contexts,
malformed variants and all nullable-item field combinations against ICU4J. Common
tests cover deduplication, modified regions, French UI capitalization, mutable
results, comparator ties and null sorting. Full JVM, JS Node and macOS Arm64 suites
pass; Linux x64 tests compile. A further JVM suite passes after expanding row and
nullable-field coverage. UI types/engine remain internal pending LocaleDisplayNames'
public/cache/provider facade. Locale services, Currency/MeasureUnit and the full
ICU4J port remain unfinished.

### Plural constraint parser

`PluralConditionParser` ports the internal ICU4J constraint grammar: plural
operands, modulus, integer/within ranges and lists, negation, AND/OR precedence,
canonical text and integer/decimal limitedness. It retains source tokenization,
Java regex split behavior, BMP decimal digits, signed Long bounds and malformed
input behavior. Internal parse diagnostics carry source messages and offset -1;
the public ParseException boundary will be connected with the PluralRules facade.
Common tests exercise visible fractions, precedence, Unicode bounds and overflow.
JVM oracle tests compare parsing, diagnostics, evaluation and limitedness against
ICU4J 78.3. Public PluralRules, rule lists, samples and locale loading remain pending.

### Plural rule lists and decimal sample core

`PluralRuleListCore` now parses complete rule descriptions with integer/decimal
sample sections, validates keyword order and duplicates, moves `other` last,
and selects the first matching category. Explicit samples control `isLimited`,
while `computeLimited` continues to inspect the constraint itself. Missing
keywords retain the source's distinct results for those two methods.

`PluralSamplesCore` retains endpoint identity, visible decimal digits, exponent
notation, repeated ranges, insertion order and ellipsis bounding. It expands
samples with common arbitrary-precision decimal arithmetic and retains ICU4J's
double API behavior excluding all integer-valued samples. Endpoint quantities
remain mutable, while the range set is read-only. `UnitDecimal.movePointRight`
provides the source decimal increment operation, including scale normalization.

Tests compare parse diagnostics, malformed sections, sample expansion, endpoint
sets and decimal-point movement against ICU4J/BigDecimal. A corpus test reparses
all distinct cardinal and ordinal locale rule sets supplied by the test oracle
and compares canonical descriptions, sample metadata, limitedness and selection.
This remains an internal core: public PluralRules/DecimalQuantity adapters,
locale rule resource loading and the remaining plural APIs still need porting.

### Plural sample queries and keyword status

`PluralRuleSamplesCore` ports sample queries, all-values queries, unique-value
sentinels and keyword status with explicit-value subtraction and integer offsets.
Explicit ranges expand into immutable quantity collections; converted double
sets retain the source's mutability. Quantity collection runtime set/non-set
shape and iterator mutation rejection are preserved on JVM. The fallback scan
retains the ICU4J by-value maxCount behavior, the 200 integer / 2000 decimal
candidates and the additional million-valued candidate. No new sample cap is
introduced. Missing samples and missing keywords retain source null/empty and
status failure distinctions.

Common tests cover fallback enumeration, visible zeros, unique values,
suppression and output clearing. JVM oracle tests compare both sample types,
all-values results, offsets (including Int.MIN_VALUE), explicit sets, collection
mutation and all keyword statuses. A locale corpus test additionally compares
sample expansion, uniqueness and status for every distinct cardinal/ordinal
rule set from ICU4J. This is still an internal layer; public plural-rule and
quantity adapters, locale resource loading and remaining APIs are pending.

### Locale plural loader and standard plural ranges

Pinned `plurals.res` and `pluralRanges.res` are now embedded alongside the other
ICU 78.3 resources (100 generated resources total). `StandardPluralRanges` loads
language-to-set mappings and category triples through the common resource bundle
reader. It preserves language-only selection, missing-language default identity,
nullable category matching and `other` fallback.

`PluralRulesLoaderCore` loads cardinal/ordinal IDs, canonical locale fallback,
available locales and functional equivalents. Its nullable-result cache uses
both rule ID and range-set ID, with publication under the shared monitor boundary;
parsing occurs outside the cache lock, and a concurrent winner is retained.
Absent rules use the shared default, while absent-resource initialization retains
empty maps. Loaded objects currently expose the internal rule list plus ranges;
the public PluralRules.Factory adapter remains pending.

JVM oracle tests cover every available locale plus variants, aliases, keywords
and unknown IDs; equivalent-locale availability flags; full descriptions and
selection; cache identity partitions; and concurrent first initialization. Range
oracle tests cover every resource set, all category pairs (including null), all
range locales and missing-set failure. Common tests exercise locale selection,
cardinal/ordinal differences, fallback, cache identity and French/Russian ranges.
The data generator verifies pinned checksums and generated output consistency.

### Public plural-rule facade and fixed decimal

`text.PluralRules` now exposes Operand/IFixedDecimal/FixedDecimal, rule parsing,
locale factories, ordinary double and operand-provider selection, matches,
keywords, rule text, double samples/all-values/unique values, limitedness,
comparison and textual equality. Its hash retains the source rule-list identity
behavior. Public `impl.PluralRulesLoader` adapts the internal loader while
preserving shared facade identities and defaults. JVM Locale overloads are
provided as Kotlin companion extensions.

FixedDecimal delegates to the source-checked numeric model with its constructors,
exponent factory, operand getters, comparison, formatting and Number conversions;
subclasses can override operand evaluation. Infinite/NaN provider checks preserve
source call order and short-circuiting. Nullable keyword and sample-type behavior
is retained, including distinctions between explicit sample metadata, constraint
limitedness, always-true constraints and AND/OR short-circuit evaluation.

The public ParseException boundary maps to java.text.ParseException on JVM and
carries the same message/offset on JS/Native. PluralRules uses the text-based Java
serialization proxy and rejects raw stream callbacks; restored rules have no
range data. FixedDecimal rejects Java serialization. The host serialization
marker and private stream parameter boundaries map to Java types on JVM.

Common tests exercise locale and parsed rules, fractional display, immutable
keywords, factories and custom/overridden operand providers. JVM oracle tests
cover all distinct locale rule sets through the public API, samples/limitedness,
nullable inputs, diagnostics, equality/comparison, FixedDecimal and serialization.
Public DecimalQuantity sample/range/status adapters, formatted number/range
selection and their numeric/formatting dependencies still remain to be ported;
this is not yet the complete PluralRules API or the complete ICU4J port.

### Number modifiers and range pattern offsets

The public Modifier/ModifierStore contracts, Signum and mutable Parameters now
support the number formatting pipeline. ConstantAffixModifier and
ConstantMultiFieldModifier retain prefix/suffix insertion order, copied field
arrays, overwrite deltas, field identity tests, code-point counts and strict
comparison. Semantic comparison follows all sign/plural forms of parameter
stores instead of comparing only the currently selected form. AdoptingModifierStore
retains its four-sign and plural-indexed modes, nullable entries and assertion-only
freeze/mode checks. DecimalQuantityCore now uses Modifier.Signum directly.

SimpleModifier reuses the common compiled SimpleFormatter implementation for
one-argument affixes and literal replacement. Its two-argument formatter and
PrefixInfixSuffixLengthHelper retain relative offsets, field annotations and
partial mutation before failures. The prefix/suffix formatter now permits a null
output when the compiled pattern performs no writes, matching ICU4J's `{0}` case.
Source assertions are checked on JVM and remain disabled on JS/Native.

Common tests exercise supplementary/unpaired UTF-16, field preservation, copied
affixes, overwrite, sign/plural selection and range indexes. JVM oracle tests
compare affix matrices, insertion subranges, all fields, semantic store call
traces and null handling. An isolated class loader verifies release behavior with
assertions disabled, including writes after freeze and deferred failure for a
null SimpleModifier pattern. Further tests compare two-argument patterns and
buffer/helper state after failures. Currency-spacing and mutable pattern
modifiers, public quantity numeric boundaries and complete number formatting
remain pending.

### Decimal affix utilities

`AffixUtils` now provides the complete source affix utility API: quoting/escaping,
width estimates, symbol-field mapping, symbol substitution and counts, token
queries/replacement, ignorable checks and consumer iteration. The packed Long
token state retains UTF-16 offsets, quote states, currency widths and code points.
Six or more currency signs produce the overflow token and U+FFFD with the currency
field, without calling the symbol provider. Replacements retain the source's
last-code-unit behavior, including supplementary literals and multi-sign tokens.
The source escape behavior for `~` is retained rather than silently extended.

Callbacks, early returns and malformed trailing quotes retain source order and
partial mutation. Symbol counting captures the same CharSequence bounds as Java
Character.codePointCount. Assertions use the JVM host boundary and are disabled
on JS/Native. SymbolProvider and TokenConsumer support SAM construction; the
existing ModifierStore now supports SAM construction too, and Parameters remains
subclassable like the source.

Common tests cover quotes, supplementary characters, field annotation, provider
calls, currency overflow, early query termination and replacement. JVM oracle
tests use 1,218 fixed/seeded patterns to compare all public utility operations,
malformed diagnostics, provider counts/call order and partial output/fields.
Reflection compares the exact packed token states. Additional tests compare
nullable callback/argument short-circuiting and supplied CharSequence accesses.
Decimal pattern properties/parsing, symbol tables, currency-spacing modifiers
and complete number formatting are still pending.

### Decimal pattern parser core and padding

`PatternStringParserCore` now implements the source recursive-descent grammar:
positive/negative subpatterns, quoted affixes, padding endpoints/positions,
integer and fraction placeholders, significant digits, packed grouping sizes,
rounding increments, currency-as-decimal and scientific exponents. Parsed info
implements the public AffixPatternProvider contract and retains mutable affix
views plus source endpoint arithmetic. The internal PatternProperties projection
covers all fields assigned by ICU4J patternInfoToProperties, including ignore-
rounding policies, minimum-digit compatibility, padding width and multipliers.
Null/empty parse-to-properties retains the source default/reset values. This
projection is not a replacement for the full DecimalFormatProperties property bag.

Public Padder supports code-point construction, four padding locations, legacy
position values and modifier application. It retains source code-point width
calculation, whole-string width semantics, repeated whole padding strings, field
annotations and assertion-only index restrictions. Its DecimalFormatProperties
factory remains pending with that public property bag.

Common tests exercise grouping, exact increments/scales, scientific/currency
patterns, padding and malformed positions. JVM oracle tests compare every raw
parsed field, affix flags/views, pattern-owned property values and diagnostics on
fixed and seeded patterns, including signed-short grouping boundaries. A corpus
checks every distinct pattern produced by ICU4J's available locale number,
currency, percent and scientific formats. Padding tests cover all positions,
widths, strings, supplementary characters, fields, nulls and assertion behavior.
Public PatternStringParser/quantity adapters, full DecimalFormatProperties,
pattern serialization and complete decimal formatting still remain pending.

The full parser-stage run also caught a JS test-fixture issue in the prior affix
work: generated JavaScript emitted the standalone-surrogate expected string as
`'?x'`. The replacement algorithm retained U+D83D correctly. The common test now
compares numeric UTF-16 code units, so this behavior is checked without depending
on standalone-surrogate string literal emission.

### Decimal pattern generation, localization and sign rendering

`PatternStringUtilsCore` ports property-to-pattern digit generation, significant
and increment notation, grouping, exponent/padding serialization and negative
body copying. It retains source property limits, rounding-increment significance
checks, coefficient/scale behavior and padding quoting. The generator takes an
AffixPatternProvider explicitly: public DecimalFormatProperties and the default/
currency-plural property-affix provider are still separate pending dependencies.

The localized-pattern state machine reads the same 21 symbol rows, substitutes
ASCII quotes in local symbols with U+2019, preserves source greedy matching and
adds/removes collision quotes. Symbol getters are supplied through a callback so
the eventual DecimalFormatSymbols adapter can preserve source read order.
Pattern affix rendering handles signs, negative subpatterns, approximation,
plural flags and permille replacement. Sign-display resolution retains distinct
negative-zero policies and nullable NEVER behavior; its enum remains internal
until NumberFormatter's public enums are ported.

Common tests cover pattern generation, rounding thresholds, localized collisions
and quoted digits, symbol-read order and sign rendering. JVM oracle tests compare
609 parsed/arbitrary property bags (with identical affix-provider inputs), all
distinct available-locale symbol sets plus custom multi-character/quoted symbols,
all sign-display/signum combinations and affix rendering options. Provider traces
and partial output are compared for callback failures and null output. These tests
prove the utility algorithms; they do not yet prove the pending property-affix,
CurrencyPluralInfo or DecimalFormatSymbols adapters. Complete decimal formatting
and the full ICU4J port remain unfinished.

### Grouping policies and public integer width

`GrouperCore` ports strategy selection, property short narrowing/normalization,
canonical Western/Indic instances, locale minimum-grouping lookup, pattern size
resolution and grouping-position decisions. The source sentinel values and
conditional resource reads are retained, including OFF's minimum-data read and
its shared disabled instance. Grouping uses the quantity's displayed upper
magnitude (including leading zero padding) and preserves short-circuit behavior
for disabled grouping, zero divisors and null quantities. Resource lookup uses
the common bundle reader; JVM oracle dependencies supply only extra raw locale
payloads through the existing classpath resource loader.

Public `number.IntegerWidth` now provides zeroFillTo/truncateAt with source bounds,
configuration values, same-instance behavior and the shared default. It reuses the
source 999-digit limit from the common rounding layer. The NumberFormatter setter
and public Grouper property/quantity/strategy adapters remain pending.

Common tests cover Western/Indic grouping, Spanish minimum digits, aligned
fallback, leading zeros and width validation. JVM oracle tests compare every
available locale's minimum-grouping resolution across all five strategies and
four pattern shapes, then exercise grouping positions for each distinct resolved
configuration. Additional matrices cover signed-short narrowing, special values,
nulls, arithmetic/assertion short circuits and cache identity. IntegerWidth tests
compare all boundary configurations, messages and return identities. Full locale
payload embedding remains subject to the manifest's documented subset; this does
not establish complete CLDR payload coverage on JS/Native or a finished ICU4J port.

### Portable standard math contexts

The util MathContext/RoundingMode boundary now supplies the standard precision
and eight rounding modes needed by public decimal quantities and formatters.
JVM actual types alias java.math.MathContext/java.math.RoundingMode directly;
JS/Native use a portable immutable context and enum. Common MathContexts exposes
the four canonical presets (returning the actual JDK preset identities on JVM),
and RoundingModes.valueOf maps legacy integer values. These helper objects are
necessary because Kotlin expect companions do not match Java static members.
They are standard-library counterparts, separate from ICU's legacy math package.

The portable value core preserves constructor validation order, default HALF_UP,
context text parsing/normalization, BMP decimal digits in precision, equality,
hash behavior and error messages. DecimalQuantityCore now accepts typed contexts
for magnitude and nickel rounding, while retaining its existing common BCD
rounding implementation. No ICU4J algorithm is used in production.

Common tests cover constructors, presets, textual round trips, Unicode precision,
invalid values, legacy modes and quantity rounding. JVM oracle tests compare the
portable core against the JDK context boundary, verify Java assignability/preset
identity, and compare typed-context BCD rounding with ICU4J for every rounding
mode and multiple precisions, including failure state. Public BigDecimal and
DecimalQuantity bridges and the remaining number formatting API are still pending.

### Public standard decimal boundary

The util BigDecimal boundary now aliases java.math.BigDecimal on JVM and uses the
portable BigDecimalCore/UnitDecimal implementation on JS/Native. BigDecimals
provides common constant/valueOf access with standard small-value factory caches.
The boundary currently exposes constructors from strings and primitive numbers,
exact and context division, scaled division/rounding, exact add/subtract/multiply,
scale/point movement, comparison, equality/hash, plain/scientific text and Number
conversions. BigInteger, exact integer conversions and remaining context arithmetic
APIs are not exposed yet; the legacy ICU math.BigDecimal remains a separate port.

The binary Double constructor uses exact mantissa/exponent arithmetic; valueOf
uses the existing JDK-compatible shortest-decimal conversion. The two paths retain
different scales and values, including negative zero. Exact division reduces the
fraction and accepts terminating powers of 2/5; context division now supports all
eight rounding modes. Decimal hashing follows coefficient base-2^32 words. Zero
multiplication retains the host's left-operand scale-overflow validation order.

Typed DecimalQuantityCore conversion/set/multiply methods now use this boundary;
JVM conversion transfers unscaled coefficient/scale rather than losing extreme
scales through textual exponent restrictions. Zero-quantity multiplication keeps
the source early return and negative-zero flags.

Kotlin actual matching reports Java Number's inherited concrete byteValue and
shortValue as abstract built-ins. The JVM alias/common expect declaration carry
scoped compatibility-diagnostic suppressions for this metadata mismatch; JS and
Native implement both conversions normally. Tests verify assignability and byte/
short invocation through Number, alongside public decimal operations on all targets.

JVM oracle tests compare the portable core with JDK values, scales, precision,
hashes, primitive conversions, arithmetic, every division mode/precision, exact
Double construction and extreme-scale operations. Common tests exercise the
actual JS/Native boundary and BCD transfer. Implicit division-by-zero diagnostics
are normalized across the JDK's compact/BigInteger backends. Public DecimalQuantity,
BigInteger and the remaining decimal/formatting APIs are still pending.


### Public decimal quantity adapter

Added DecimalQuantity, DecimalQuantity_AbstractBCD and DecimalQuantity_DualStorageBCD
as public Common Kotlin APIs over the existing portable BCD implementation. Typed
decimal/context rounding, constructors, copy/mutation, decimal conversion, plural
operands/selection, display bounds, field positions and builder output now use the
public boundary. Double inputs retain the explicit roundToInfinity transition.

JVM Number dispatch preserves accepted Java numeric classes and rejects unsupported
Byte/Short/custom Number inputs. JVM BigInteger is available through a Kotlin
constructor-style factory and setToBigInteger extension; Common BigInteger, the
legacy ICU math.BigDecimal input branch and the protected custom-storage subclass
SPI remain pending. The diagnostic exact-double field is exposed as a Kotlin
property. JVM constructor/field ABI compatibility is not claimed for these adapters.
JavaScript cannot distinguish all boxed numeric types; typed constructors provide
the explicit input path on that target.

Common tests exercise typed numeric mutation, copies, operands and output on all
runnable targets. JVM oracle tests compare public adapter state and behavior with
ICU4J, including boxed numeric dispatch, rounding, appended output, plural selection
and source self-copy behavior. Production code does not delegate to ICU4J.


### Public plural quantity samples and keyword status

PluralRules now exposes DecimalQuantitySamplesRange/DecimalQuantitySamples,
decimal quantity sample/all/unique queries, typed all-keyword queries, and both
keyword-status overloads. Sample descriptors are cached per rule facade under a
portable lock. Endpoints retain the underlying quantities and singleton identity;
mutating an endpoint affects subsequent enumeration and rule rendering. Returned
quantity collections retain the source Set-versus-Collection shape, stable element
identities and unmodifiable operations. The public no-unique sentinel wraps the
same quantity as the core query implementation.

Sample expansion invokes collection callbacks before constructing the next value,
so a caller's mutation of a supplied quantity participates in the source increment
schedule. Keyword status accepts arbitrary DecimalQuantity implementations through
the public decimal boundary, clears Output before validation, and retains source
limitedness, suppression, offset and null-input behavior.

Oracle tests cover every distinct locale rule description, both sample types and
null sample types, sample ordering/formatting, low-level double filtering, quantity
expansion, endpoint identity/mutation, collection callbacks and status/output
transitions. Common tests exercise public sample APIs and custom interface inputs
on JVM, JS and Native. FormattedNumber/FormattedNumberRange selection and remaining
number formatter APIs are still pending.


### Public measure units, time units and currencies

Added the public MeasureUnit/Currency inheritance chain, TimeUnit and NoUnit, and
all 234 ICU4J 78.3 unit constants. MeasureUnitImpl and SingleUnitImpl now build public
units through the shared singleton catalog. Prefix/complexity/usage enums live under
their source API classes; the initially extracted top-level names remain Kotlin
aliases. Identifier parsing/canonicalization, prefix/power composition, reciprocal,
products, denominator operations, splitting and source equality/hash behavior use
the existing Common implementation. Unknown type queries retain the source empty
set's no-op removals, while known type queries expose live unmodifiable catalog
views. Cache publication uses a reentrant portable monitor; catalog population avoids
nested acquisition and retains the source set-before-loading population flag.

Currency now provides ISO/locale factories, region soft caching, currency metadata,
display names and symbol variants, plural names, parsing tries, availability queries
and locale service registration. Registration uses ICULocaleService, including
locale fallback and override precedence. Epoch-millisecond date boundaries are
available in Common Kotlin; JVM extensions accept java.util.Date/Locale and bridge
to/from java.util.Currency. No production operation delegates to ICU4J.

JVM unit serialization writes a version/type/subtype proxy and resolves through the
canonical unit cache. Round trips preserve singleton identity for built-in units,
currencies and time units. Custom identifier units preserve the source null-type
serialization failure. Compatibility with historical raw Currency/TimeUnit streams
is not claimed. Kotlin's companion/outer protected visibility diagnostic is scoped
out for the source protected static factory bridge; it does not alter execution.

Oracle tests compare all constants and catalog entries, dynamic live views,
identifier operations and errors, source hash edge cases, serialization, currency
names/metadata/date queries, trie contents and matches, Java currency conversion,
service fallback, override ordering and removal. Common tests exercise composition,
factory identity, catalog access and currency registration on JVM, JS and Native.
Measure/CurrencyAmount/TimeUnitAmount and the formatter APIs remain pending.


### Public numeric measures and amounts

Added Measure, CurrencyAmount and TimeUnitAmount. They retain Number/unit fields,
use the source boxed-number equality followed by primitive double comparison, and
hash via canonical binary64 bits before the unit hash. Signed-zero equality/hash
inconsistency and rounded large-Long comparisons deliberately retain ICU4J behavior.
Getters are virtual, but equality/hash/text use the retained fields as in the source;
convenience currency/time getters dispatch through getUnit. JVM constructor-style
factories also accept java.util.Currency.

The exact rounding-interval decimal printer now supports binary32 as well as
binary64. Native Float/Double measure text uses these portable printers; JVM uses
the supplied Number's own text. Typed Float/Double constructors retain input-kind
metadata on JavaScript. Typed Float inputs are forced to binary32 before value
comparison/hashing, so 0.1f has the same numeric value and hash as the corresponding
binary32 value expanded to Double, while printing as 0.1. Typed NaNs preserve boxed
Float-versus-Double equality. A generic Number input cannot recover numeric boxing
metadata already erased by JavaScript, including integer-valued Double versus Int;
callers needing those distinctions use the typed constructors.

Oracle tests compare ICU4J construction, null failures, retained mutable Numbers,
all numeric type pairings, hash/text, subclass getter boundaries, amount getters
and Java currency inputs. The portable binary32 printer is compared with JDK Float
text for subnormal edges, each exponent boundary, specials and a seeded random
sample. Common tests cover amount identity, decimal scale, binary32 expansion,
signed zero, NaNs and large integers on JVM, JS and Native. Number/measure formatter
APIs remain unfinished.


### Locale identity serialization

ULocale now implements the host serialization marker with ICU4J's serialVersionUID.
The serialized field descriptor contains only localeID, matching the source. Java
locale, base-locale and extension caches are transient volatile references and are
reconstructed on first use after deserialization. This replaces serializable lazy
delegates without attempting to serialize cached locale implementation objects.

Oracle tests compare stream field descriptors/UID and round-trip locale identity,
BCP47 conversion, Java Locale conversion and components before/after cache warming.
The change also enables serialized DecimalFormatSymbols to retain locale metadata.


### Public decimal format symbols

Added DecimalFormatSymbols and a cached locale/numbering-system symbols loader.
Digits, symbol characters/strings, monetary overrides, spacing, locale identity,
copy-on-write mutation, source equality/hash behavior and JVM locale overloads are
exposed through the public API. The loader follows NumberElements fallback and
Latin fallback, retains valid locale metadata, and resolves optional numbering
systems through the source locale-key route. Currency names and format overrides
use the existing portable currency provider and public Currency facade.

JVM cloning uses Object.clone and JS cloning copies own property descriptors onto
the same prototype; both retain unknown subclass type/state and shallow array
sharing. Native copies the known DecimalFormatSymbols fields. Generic cloning of
unknown user subclasses is still pending on Native: Kotlin/Native 2.4.21 has no
supported generic object-clone API, so such subclasses currently override clone()
to copy their extra state. All public array setters retain the source copy-on-write
behavior, including cached/default digit arrays and currency spacing arrays.

JVM deserialization implements source stream versions 0 through 10, including
legacy character/string defaults, locale defaults, spacing defaults, transient
currency recreation and digit/codePointZero rebuilding. The tests synthesize those
version states and compare round trips with ICU4J; cross-package reading of old ICU
streams is not claimed.

Oracle tests cover all available locales, every available numbering system on a
locale matrix, character/string setters and failures, empty/multi-code-point and
supplementary digits, array aliasing, source equality omissions/null failures,
currency overrides and same-currency early return, subclass cloning and all stream
migration versions. Common tests exercise initialization and copy-on-write behavior
on JVM, JS and Native; a JS-specific subclass clone test checks prototype/state.
Full DecimalFormat and NumberFormatter integration remains pending.


### Number-format style pattern data

Ported NumberFormat's shared style/numbering-system pattern lookup for all ten
styles, including the currency cf=account override and Latin numbering-system
fallback. Invalid styles retain assertion-only validation: JVM class assertion
settings govern the failure and other targets use the source production fallback.
This is an internal data helper; the public NumberFormat formatter facade remains
pending.

Oracle tests compare every style for all available locales, numbering-system
fallback, accounting keywords, invalid styles and null-input evaluation paths.


### Currency plural information

Added public CurrencyPluralInfo with locale factories, plural rule replacement,
currency-unit pattern construction, mutable pattern lookup/iteration, cloning,
equality/hash and JVM serialization. Locale resets reinitialize rules and patterns
in source order, retaining partial state on failure. Pattern construction substitutes
number patterns and the triple currency sign, preserving positive/negative bodies.
Null keys/values, fallback to other, the source NUL-prefixed default pattern and
invalid-rule null state are preserved.

A shared ICUCloneableBase provides runtime-type-preserving shallow copy on JVM/JS;
CurrencyPluralInfo then copies its pattern map while sharing immutable rules and
locale. Native copies the known class fields; unknown Native user subclasses still
supply their own clone override. JVM locale constructor-style overloads and stream
round trips use the existing serialized PluralRules/ULocale boundaries.

Oracle tests cover all available locales, factories, FixedDecimal selection,
mutable rules/patterns, null and failure states, iterator removal, map independence,
subclass cloning and serialization. Rule-list identity contributes to PluralRules'
source hash, so tests compare the remaining hash formula after removing that
identity contribution. Common tests exercise patterns, mutation, reset, selection,
cloning and style fallback on JVM, JS and Native; JS tests also verify subclass
prototype/state cloning. DecimalFormatProperties and formatter integration remain
pending.


### Decimal-format property bag and named-field streams

Added DecimalFormatProperties with all 43 source fields, unchecked fluent setters,
source defaults, reference-preserving copy/clone, field-based equality/hash and
bare/full diagnostic text. Reference equality is checked before invoking an object
field's equals method, retaining behavior for shared objects with exceptional equals.
The initially extracted CompactStyle enum supplies the property type; the full
CompactDecimalFormat facade is still pending.

JVM custom streams store only non-default values as name/value pairs, retain the
reserved integer, clear through the virtual clear method before reads, ignore
unknown fields and use source reflective field-assignment behavior, including
primitive widening and AssertionError for incompatible values. The ICU 59 Properties
wrapper delegates to the same stream implementation and retains lazy instance
creation. All bag fields are transient in default serialization.

PatternStringParserCore now projects parsed patterns into the public property bag.
Nonempty patterns overwrite the 22 pattern-owned settings while preserving unrelated
settings; null/empty patterns clear the complete bag. Public PatternStringParser
parsed-info adapters and the rest of the formatter facade remain pending.

Oracle tests enumerate every field, defaults, extreme primitive values, nullable
objects/enums, fluent setters, shallow copies, equality/hash, self-copy, clearing,
text, custom stream records, stream round trips, unknown fields, wrong types,
primitive widening, legacy wrapping and repeated pattern projection. Common tests
exercise unchecked values, live custom maps, shared invalid-rule objects and pattern
updates on JVM, JS and Native. JS also checks runtime-subclass shallow cloning.


### Padding from decimal properties and object-contract edges

Added Padder.forProperties over the public property bag. It snapshots pad string,
width and position through the virtual getters, preserving the constructor's
fallbacks. Oracle tests compare formatted UTF-16 text, field annotations and inserted
length for all positions and representative widths/strings. A Common test verifies
that later bag mutation does not change a created padder and checks supplementary
padding with affixes.

Additional property-bag tests cover overridden getters, runtime subclass cloning,
custom map equality with null, asymmetric equality, serialization default filtering
and text callback evaluation before a null StringBuilder failure. These compare
source behavior rather than replacing it with generalized object equality rules.

### Property and currency-plural affix providers

Added PropertiesAffixPatternProvider and CurrencyPluralInfoAffixProvider, including
factory dispatch, independent literal overrides, default negative affixes, escaping,
pattern-only currency detection and snapshots of all eight plural forms. The plural
provider projects each pattern into a copied property bag and takes metadata from
OTHER, matching ICU4J rather than combining metadata across forms.

The source's suffix reference-identity comparison in hasNegativeSubpattern is
retained. JVM oracle tests explicitly distinguish equal-content string objects.
JavaScript primitive strings cannot represent that distinction: this source quirk
therefore remains a platform limitation on JS. No production ICU4J delegation is used.

Oracle tests cover patterns, each independent nullable/empty/special-character
literal override, UTF-16 indexing, snapshot behavior, suffix identity and currency
plural dispatch for English, Russian, Arabic and French. Common tests exercise
literal escaping and plural dispatch on JVM, JS and Native. Verification passed:
jvmTest, jsNodeTest, macosArm64Test and compileTestKotlinLinuxX64.

### Public pattern parser and live parsed models

Added PatternStringParser with the source constants and all parse-to-info,
parse-to-properties and parse-to-existing-properties overloads. The recursive parser
now constructs the public ParsedPatternInfo and ParsedSubpatternInfo objects directly;
internal nested aliases preserve existing consumers without a second grammar or
snapshot adapters. All parsed fields remain mutable; rounding is the public
DecimalQuantity_DualStorageBCD. JVM fields and static entry points are exposed to
Java callers, and the parsed-info constructor remains private.

The currency-plural affix provider uses the public parser. JVM assertion status is
now resolved against the public PatternStringParser class. Existing seeded random,
locale pattern corpus and packed-endpoint oracle tests exercise the public parsed
models. Added tests verify field shape, quantity aliasing, replacement of positive
and negative subpatterns, null/replaced pattern text, malformed endpoints, all public
property getters and parse failure order. Common tests run on JVM, JS and Native.
Verification passed: jvmTest, jsNodeTest, macosArm64Test and
compileTestKotlinLinuxX64.

### Public pattern utilities and property/symbol integration

Added PatternStringUtils with rounding-increment significance, generation of a
pattern from public DecimalFormatProperties, localization through public
DecimalFormatSymbols, affix rendering and sign-policy resolution. PatternSignType
has source order and its mutable VALUES array. SignDisplay is temporarily a public
top-level enum in the number package; relocation under the full NumberFormatter
facade remains pending, as with the earlier extracted CompactStyle enum.

Property getters run in source order, with affix-provider creation before the
rounding-increment getter. Rounding operations call the public BigDecimal directly,
retaining virtual double/scale/power/plain-string callbacks rather than converting
an overridden decimal through text. Internal raw-property consumers use the same
rendering algorithm. Localization reads all 21 symbol rows before normalization,
including ten separate digit-array getter calls and deferred null-exponent failure.
JVM assertions now use the public utility class's assertion status.

Oracle coverage extends the 600 seeded unconstrained property bags and distinct
locale symbol tables to the public adapters. Additional tests cover overridden
property/decimal callback order, null-symbol read order, currency-plural patterns
and literal overrides. Sign-policy, affix rendering and partial-output callback
oracles now exercise the public entry points. Common round-trip/localization/sign
tests pass on JVM, JS and Native. Verification passed: jvmTest, jsNodeTest,
macosArm64Test and compileTestKotlinLinuxX64.

### Currency-spacing modifier

Added CurrencySpacingEnabledModifier over ConstantMultiFieldModifier, including
prepared immutable boundary sets and the static in-place spacing path. The two
CLDR default UnicodeSets are frozen and shared; custom sets are only parsed when
a currency field and currency code point reach a boundary. Inserted spacing has
null fields, and affix snapshots retain the base modifier's overwrite, strength,
length, code-point count and inherited equality behavior.

Both source algorithms are retained independently. In particular, the prepared
path checks codePointBefore using the original right index after any prefix
spacing insertion; the in-place path adjusts the suffix index. This can produce
different spacing for a one-digit body with both currency affixes and is covered
explicitly rather than normalized away. The source's inherited strictEquals also
ignores spacing settings, as does this port.

JVM oracle tests compare output, inserted lengths, all fields, empty and embedded
number ranges, arbitrary boundary fields, supplementary/unpaired UTF-16, custom
match sets, empty/multi-character spacing, overwrite, snapshots, inherited equality,
nulls, invalid sets, lazy symbol callbacks and buffer state after callback failures.
Common tests verify both paths, supplementary field boundaries and empty-number
short circuits. Verification passed: jvmTest, jsNodeTest, macosArm64Test and
compileTestKotlinLinuxX64. MutablePatternModifier and the remaining formatter
pipeline are still pending.

### Scale and rounding-context foundations

Added the public Scale factories, normalization of decimal powers of ten, source
singletons, custom math contexts, direct multiplication and reciprocal application.
Positive unit coefficients become magnitude shifts, zero becomes the canonical
zero decimal, and a nonzero arbitrary multiplier gets a context-rounded reciprocal.
The JVM normalization boundary calls unscaledValue().equals(BigInteger.ONE),
preserving virtual BigDecimal callbacks; JS and Native inspect their common decimal
coefficient. No ICU4J implementation is delegated to in production.

Added RoundingUtils's direction, midpoint, cached math-context and property-to-scale
APIs over the existing rounding-direction algorithm. The 34-digit cache deliberately
matches ICU4J's MathContext(34) initialization: every rounding-mode entry uses HALF_UP
and remains a distinct object, despite the declared default mode being HALF_EVEN.
Magnitude-multiplier precedence and repeated virtual property reads are retained.
The Precision-dependent getPluralSafe method remains pending with Precision; the
complete number formatter and MicroProps pipeline are not yet ported.

Oracle tests cover decimal/double factories, canonical identities, all rounding
modes, explicit/null/unlimited contexts, reciprocal rounding, zero multipliers,
special and seeded doubles, exact decimals, original approximate-quantity state,
integer-extreme metadata, virtual quantity callbacks and failure order. BigDecimal
subclass tests cover normalization callbacks including unscaledValue. Context tests
check cache identity, source default quirks and changing property getter values.
Common tests exercise normalization, forward/reciprocal scaling, zero behavior and
property scale precedence on JVM, JS and Native. Verification passed: jvmTest,
jsNodeTest, macosArm64Test and compileTestKotlinLinuxX64.

### Precision families and safe plural rounding

Added Precision, FractionPrecision and CurrencyPrecision with the source factories,
validation, cached policies, copy/mode/trailing-zero operations, BogusRounder and all
infinite, fraction, significant, combined fraction/significant, general increment,
power-of-ten increment, nickel and currency rounders. Currency resolution retains
cash/standard metadata and source cache behavior. RoundingPriority and
TrailingZeroDisplay are temporarily top-level enums pending the NumberFormatter
facade, like SignDisplay. Added MultiplierProducer and carry-sensitive multiplier
selection, plus the previously pending RoundingUtils.getPluralSafe copy/round path.

The increment specialization preserves scale and calls JVM unscaledValue/intValue
through a small host boundary; common hosts inspect the decimal coefficient. JVM
assertions are lazy and use Precision's assertion status, with non-evaluating host
assertion helpers on JS/Native. Source quirks remain: combined precision accepts a
negative minimum significant count, HIDE_IF_WHOLE leaves any prior minimum fraction
setting untouched, fraction-combination helpers do not copy the base trailing-zero
policy, and withCurrency can assign its trailing policy to a shared cached rounder.
Oracle tests save and restore cache state to keep that last behavior isolated.

JVM oracle coverage includes every family, all rounding modes, unlimited/finite
contexts, nullable trailing policies, special/seeded doubles, carry boundaries,
validation extremes, policy fields and identities, null contexts/quantities,
standard/cash currencies, null priority assertions, the zero-significant special
case, multiplier re-selection and safe plural-copy behavior. Proxy quantities and
BigDecimal subclasses verify callback/failure order. Common tests cover rounding,
combined priorities, increment scale, currency cash increments and unchanged input
for safe plural selection on JVM, JS and Native. Verification passed: jvmTest,
jsNodeTest, macosArm64Test and compileTestKotlinLinuxX64. The final JVM-only rerun
corrected reflection void/Unit normalization in the test adapter; production code
was unchanged after the cross-platform run. MicroProps and the rest of the formatter
pipeline remain pending.

### MicroProps pipeline base and public grouping

Added MicroPropsGenerator and MicroProps with all 17 public mutable result fields,
source defaults, the private immutable flag and volatile exhausted state. Immutable
bases call the virtual clone method for every quantity; mutable bases return
this once and preserve exhaustion in subsequent clones. Quantity is deliberately
unused by this first pipeline stage. Cloning is shallow, retaining symbols,
modifiers, rounding/grouping policies, unit and mixed-measure list references.

The dedicated host clone base does not add Serializable. JVM uses Object.clone
with Cloneable and preserves runtime subclasses and extra fields; JS clones own
property descriptors with the original prototype. Native copies all known MicroProps
fields including the exhausted flag. As with the earlier open cloneable classes,
unknown user Native subclasses must override clone themselves; generic Native
runtime-subtype cloning remains unavailable in the current supported toolchain.

Promoted the single GrouperCore algorithm to public Grouper, retaining an internal
class alias for existing consumers. Grouping accepts the public DecimalQuantity
and parsed-pattern types, and forProperties preserves virtual getter order and
the disabled-grouping short circuit. Source singletons, short narrowing, locale
minimums, arithmetic short circuits and assertions remain unchanged. GroupingStrategy
and DecimalSeparatorDisplay are temporarily top-level formatter enums pending
NumberFormatter, alongside the earlier extracted policies.

Existing all-locale grouping and narrowing/cache oracles now use public grouping
and quantity/property APIs. Additional oracle tests verify quantity callback order,
property short circuits, MicroProps field shape/defaults/volatility, lack of a
serialization marker, shallow copying, mutable exhaustion, virtual clone dispatch
and JVM runtime subclasses. Common tests exercise all fields, shared list mutation,
cloned exhaustion and a real rounding stage over independent pipeline results;
JS also checks subtype/extra-field cloning. Verification passed: jvmTest,
jsNodeTest, macosArm64Test and compileTestKotlinLinuxX64. MutablePatternModifier and
the complete formatter facade remain pending.

### Mutable and immutable pattern modifiers

Added MutablePatternModifier and its ImmutablePatternModifier pipeline stage.
The mutable path prepares live sign/approximation/percent affixes and resolves
locale currency symbols or plural names through the public symbol/currency APIs.
The immutable builder snapshots four signs, or four signs for all eight plural
forms, in source order and selects constant modifiers at quantity-processing time.
Currency affixes use CurrencySpacingEnabledModifier; other affixes use the base
constant multi-field modifier. UnitWidth is temporarily a top-level enum pending
the NumberFormatter facade, matching the previously extracted formatter policies.

Both stages retain parent-first processing, rounding before the existing-middle
short circuit and the extra safe-copy rounding needed for plural selection. Raw
parsed patterns can overwrite a number body; the property provider's hasBody is
always true, as in the source. Mutable apply deliberately excludes currency-spacing
insertions from its returned length. The prepared and mutable currency-spacing
algorithms keep their earlier source differences. FULL_NAME currency-symbol lookup
and unsupported modifier introspection retain their source assertion behavior.
Assertions are lazy on JVM and do not evaluate on JS/Native.

Nullable receiver evaluation order is explicit where observable: locale and plural
keyword callbacks precede a null currency invocation, and nonplural immutable
field assignment evaluates quantity signum before failing on a null MicroProps.
Tests also retain the prefix already inserted before a suffix-symbol failure.

JVM oracle tests cover all sign displays/signums, approximate and permille flags,
quoted/supplementary/literal/currency affixes, symbol widths and token types,
English/Russian/Arabic plural data, mutable/immutable chains, existing middle
modifiers, live symbol changes versus snapshots, body erasure, exact fields,
reported lengths, assertions, nulls and callback/failure order. Common tests run
real rounding/modifier chains and verify currency plural names and ISO spacing.
Verification passed: jvmTest, jsNodeTest, macosArm64Test and
compileTestKotlinLinuxX64; the additional JVM-only failure-order/body-erasure tests
also passed without further production edits. Complete number formatting,
NumberFormatter and DecimalFormat facades remain pending.

### Scientific and engineering notation stages

Added Notation's scientific, engineering and simple factories, SimpleNotation and
the complete ScientificNotation configuration/handler/modifier implementation.
Scientific processing uses Precision's carry-sensitive multiplier selection,
handles the required-integer zero/significant special case, records the exponent
in the quantity and consumes the rounder. NaN/infinity retain their source early
return and unconsumed rounding policy. Safe handlers cache modifier objects for
exponents -12 through 12; other exponents allocate modifiers, while the unsafe
path keeps its live exponent on the handler.

Exponent modifiers retain the source's 999 code-point-count sentinel, assertion
behavior, exponent-only strict equality and live symbol references. Sign-display
handling is the source's exact NEVER/ALWAYS checks rather than general sign-policy
resolution. Digit insertion uses i-j (digit count, not accumulated UTF-16 width),
including source surrogate splitting for supplementary digit strings. Integer
arithmetic and abs(MIN_VALUE) behavior remain unchanged.

Added MultiplierFormatHandler with parent-before-scale processing, including a
parent returning null: scaling still runs and the null result is returned. The
remaining compact factories on Notation, CompactNotation, CompactData and compact
formatting are explicitly pending; no placeholder compact implementation is used.

Oracle coverage compares notation fields/copy/validation, scientific/engineering
intervals and required integer widths, rounding families and carry boundaries,
zero/special values, safe/unsafe paths, cache identities, modifier fields, all sign
policies including null, Arabic/Persian/Bengali/custom symbols, multi-code-unit
digits, live symbols, arithmetic extremes and callback/failure order. Invalid
internal zero/significant combinations compare source assertions and quantity
state. Common tests run a scale/scientific/rounding chain, engineering notation and
the source UTF-16 digit-insertion quirk on JVM, JS and Native. Verification passed:
jvmTest, jsNodeTest, macosArm64Test and compileTestKotlinLinuxX64.

### Compact notation pattern data

Ported CompactData and its resource sink to commonMain. Resource loading retains
numbering-system/style fallback order, child-locale precedence and the special
resource "0" marker that blocks parent fallback. Custom data retains signed-byte
magnitude narrowing, the first contiguous ASCII-zero run, exact-number plural
variants, magnitude clamping and partial writes on failure. Literal custom "0"
patterns remain distinct from resource fallback markers.

getUniquePatterns passes a live fixed-size array list, including null entries,
to the supplied set before removing null and the fallback marker. The view permits
replacement but rejects structural changes. Assertions remain lazy and follow
JVM class assertion status; JS/Native do not evaluate assertion callbacks. The
source's marker uses String reference identity: Kotlin/JS primitive strings cannot
distinguish separately allocated equal strings, as already noted for affix suffix
identity. CompactNotation, its Notation factories and complete compact formatting
remain pending.

JVM oracle tests cover 12 locales, both styles and types, numbering-system fallback,
exact-number/custom patterns, quoted zeros, literal-only patterns, magnitude limits,
partial failure state and the live mutable list contract. Common tests exercise
custom and resource patterns with fallback on JVM, JS and Native. Verification:
jvmTest, jsNodeTest, macosArm64Test and compileTestKotlinLinuxX64.

### Compact notation handler and factories

Added CompactNotation, forCustomData and the cached compactShort/compactLong
Notation factories. The handler loads CompactData, uses carry-aware rounding to
select magnitude and plural before updating the quantity exponent, installs a
compact middle modifier and consumes the rounder. Zero/NaN/infinity follow the
source zeroish branch. Safe handlers precompute immutable modifiers; unsafe
handlers update the supplied MutablePatternModifier in place. A null pattern
preserves the existing middle modifier. Custom data is retained by reference by
the notation and read when constructing each handler. Empty custom data does not
dereference a null build reference, including during safe precomputation.

Precomputation uses the platform HashSet, with the original JDK HashSet on JVM.
Its unspecified iteration order can differ on JS/Native, including the final
pattern left on the mutable build reference and callback order during construction;
formatted pattern selection is keyed by pattern string. JVM oracle tests compare
this callback order as well as quantity state, exponents, rounding consumption,
modifier text and fields for short/long decimal/currency data in seven locales,
custom exact-number variants, boundary carries and special values. Common tests
exercise the full compact stage and modifiers in both safe and unsafe modes.
NumberFormatter and its user-facing complete formatting pipeline remain pending.
Additional JVM oracle checks preserve an existing default modifier when no compact
pattern applies and compare parent/null-rounder/constructor failure order. All
checks passed: jvmTest, jsNodeTest, macosArm64Test and compileTestKotlinLinuxX64;
the JVM-only failure tests passed in a subsequent jvmTest run.

### Formatter macro settings

Ported MacroProps with all 20 public mutable fields, fallback merging, shallow
cloning and source equality/hash behavior. The internal threshold is copied by
clone but excluded from fallback, equality and hashCode. Fallback reads its nullable
argument only when a setting is missing, allowing a fully populated receiver to
accept null. Objects.equals retains reference-identity short circuits, ordered
field reads and user callback behavior; Objects.hash snapshots every argument
before invoking any field hashCode.

JVM clone uses Object.clone, including subclass fields, and adds no Serializable
marker. JS preserves the prototype and own property descriptors. Native copies
the known MacroProps fields; subclasses adding state must override clone, as with
MicroProps. Tests verify all public field names, each field's equality/fallback
participation, reference sharing, runtime subtype preservation on JVM/JS, null
fallback, hash snapshots and equals callbacks that mutate later fields.
Verification passed: jvmTest, jsNodeTest, macosArm64Test and
compileTestKotlinLinuxX64. Full NumberFormatter settings resolution and formatting
remain pending.

### Custom currency symbols and virtual name dispatch

Ported CustomSymbolCurrency, including the resolution policy for explicit currency,
DecimalFormatSymbols currency, unknown XXX currency and customized display/ISO
symbols. The resolved wrapper snapshots the two symbols while retaining the base
ISO code for metadata and display names. Symbol name lookup preserves nullable
symbols and choice-array write order. Equality preserves the source superclass
check followed by a CustomSymbolCurrency cast, including its ClassCastException
when compared against an ordinary currency with the same identifier; null-symbol
hash/equality failures are also retained.

Fixed Currency's four-argument getName dispatch: non-plural styles must call the
virtual three-argument overload, allowing subclass/custom symbols to take effect.
Plural long names continue to use the original underlying currency identifier.
The source's unused resolve locale argument remains unused. JVM serialization
retains the custom runtime class and symbols with serialVersionUID
2497493016770137670 rather than replacing it with a cached plain Currency.

Oracle coverage compares styles, nullable symbols/locales and choice arrays,
metadata, hash/equality outcomes, symbol resolution and snapshots, getter/currency
callback order, early returns and serialization. Common tests cover resolution,
virtual dispatch and choice-array bounds on all runtime targets.
Verification passed: jvmTest, jsNodeTest, macosArm64Test and
compileTestKotlinLinuxX64. NumberPropertyMapper and complete DecimalFormat/
NumberFormatter integration remain pending.

### Legacy number property projection

Ported NumberPropertyMapper.oldToNew, including plural-rule/affix resolution,
custom currency symbols, currency precision defaults, explicit currency usage,
increment/significant/fraction precedence, integer/fraction validation, grouping,
padding, decimal/sign policies, scientific and compact notation, scaling and
validated property exports. Source property getter repetitions and export setter
order remain intact, including exportedProperties aliasing the input property bag.

Scientific projection retains the source maximum-eight-integer-digit heuristic,
minimum/maximum adjustments and original-property significant-digit calculation.
Compact style takes precedence over scientific notation. Exported rounding uses
the pre-scientific local rounding policy, while returned macros can have the
scientific significant-digit policy; no attempt is made to reconcile that source
behavior. Min/max validation keeps minimum precedence and source integer overflow.
The mapper retains symbol identity and leaves locale/threshold unset, as upstream.

JVM oracle tests compare macro policy structure, rounding results, validated
exports, affixes, currencies, locale rules, grouping, padding, scales, notation and
virtual getter/setter order. Coverage includes parsed decimal/currency/scientific
patterns, 240 seeded raw-boundary combinations, input/export aliasing, custom
compact data and explicit currency usage. Large display widths compare display
bounds without allocating their potentially enormous rendered strings. Common
tests exercise scientific rounding versus exports and compact/currency precedence.

Convenience create methods returning UnlocalizedNumberFormatter remain pending
until the formatter facade is ported; no placeholder formatter is introduced.
Verification passed: jvmTest, jsNodeTest, macosArm64Test and
compileTestKotlinLinuxX64; the corrected/expanded JVM-only oracle suite passed in
a subsequent jvmTest run. CurrencyPluralInfo-derived rules, explicit standard/cash
currency usage and calls without an exported property bag are also covered.

### Public unit conversion boundaries

Ported UnitsConverter, ConversionInfo, symbolic Factor and ConversionRates with
its public resource sink and rate records. These public boundaries use the common
BigDecimal API and preserve virtual ConversionRates factor/base-unit/special/offset
lookups and Factor.copy calls. The older internal UnitConverter/UnitConversionData
cores remain available to the existing routing implementation; public boundaries
retain subclass behavior that a direct numeric-core adapter would discard.

Conversion covers direct, reciprocal and inverse mappings, source zero handling,
temperature offsets and Beaufort scale thresholds. ConversionInfo is a fresh
mutable container sharing its original immutable decimal values. Factor retains
all fifteen symbolic constants, prefix/power/multiply/divide behavior, source
fraction parsing, Java-style splits, signed integer exponent parsing and errors.
Source copy overrides are preserved, including the original operand numerator or
denominator replacing the corresponding value on a copied Factor. JVM decimal
powers use the standard BigDecimal operation; JS/Native use the existing exact
common decimal power implementation with DECIMAL128 rounding where required.

ConversionRates exposes base-unit/system/factor lookup, protected offset and
special mapping hooks, sink map identity, conversion-rate records and offset
fractions. Sink assertions remain lazy on JVM and are not evaluated on JS/Native.
Offsets retain source DECIMAL128 residuals rather than rounding mathematically
exact temperature conversions to short decimal values.

JVM oracle coverage compares direct/inverse results, info snapshots, constants,
all prefixes, factor arithmetic/copy overrides, invalid inputs, every embedded
conversion-rate identifier, rate record offsets and subclass callback order.
Common tests exercise decimal boundaries, exact factor results and base units.
ComplexUnitsConverter/UnitsRouter public adapters and their formatter handlers
remain pending before complete NumberFormatter unit integration.
JVM BigDecimal subclass callbacks also receive the standard MathContext.DECIMAL128
object itself. Base-unit results retain their source unserialized identifier until
serialize() is called. Verification passed: jvmTest, jsNodeTest, macosArm64Test and
compileTestKotlinLinuxX64.

### Mixed-unit conversion, preference routing and formatter stages

Ported ComplexUnitsConverter with all constructors, public mutable converter/unit
lists, source ordering/comparison, epsilon thresholding, rounding and carry into
larger units. Output measures retain the requested unit order and identify the
fractional component. The source's low-64-bit integer residual/carry conversions
are retained even for larger integer values. JVM integral components use the
original BigInteger arithmetic/Number objects; JS/Native use exact arbitrary-
precision integral Numbers with exact internal low-bit conversions and matching
integer hashes.
The fractional component remains BigDecimal. Mutable public converter lists and
rounder callbacks are read at the source operation boundaries.

Added UnitsRouter with locale/usage preference fallback, measurement-system and
temperature keyword support from the existing preference core, precision-increment
skeletons, BogusRounder resolution, route results and live mutable output-unit list.
Clearing the output-unit list does not alter routing preferences. Public converter
preferences use the public ConversionRates/ComplexUnitsConverter boundaries.

Added UnitConversionHandler and UsagePrefsHandler. Both run their parent first,
round the input to infinity before extracting its decimal, expose the selected
output unit and retain the live mixed-measure list/index while replacing the
quantity with the fractional BigDecimal. Source rounders remain on MicroProps.
The protected static mixedMeasuresToMicros helper retains partial assignments on
index/cast failures; nullable parent-result paths preserve quantity callback order.

JVM oracle tests compare reordered/two/three-part mixed units, integer/fraction/
increment rounding, carry boundaries, negative and oversized integer values,
thresholds, locale/usage routing and precision, live output lists, both handlers,
special values, helper failures and callbacks modifying converter lists during
rounding. Common tests exercise mixed ordering/carry, preference rounding, handler
state and a 2^64 integral Number. Complete NumberFormatter/long-name assembly
remains pending.

Kotlin/JS limitation: generic Number.toLong/toInt/toShort/toByte calls use stdlib
numeric intrinsics rather than the overridden Number methods. For out-of-range
integral objects this clamps through a JavaScript numeric conversion instead of
returning BigInteger low bits. The converter's internal integral operations and
low-bit calculations remain exact; measure text and hashes
are preserved, while floating values follow host Number semantics. Generic integral conversions through Measure.getNumber() on JS
remain an explicit portability gap, also relevant to other custom Number types;
the Kotlin/JS generated numberToLong/numberToInt helpers do not dispatch overrides.
A skipped JS regression retains the required low-64-bit Number conversion result
until that intrinsic-dispatch gap is resolved. JVM custom singleton lists also
receive their source sort callback. Verification passed for active checks:
jvmTest, jsNodeTest, macosArm64Test and compileTestKotlinLinuxX64; the final JS-only
regression registration passed jsNodeTest with that one explicitly skipped case.

## Long-name resource loading and grammatical derivation

Ported the data-loading portion of ICU4J LongNameHandler into the internal
LongNameData module: plural/display/per/gender slots, plural fallback, placeholder
extraction, built-in gender lookup, aliases and person-duration mapping, measure
patterns and case selection, inflected gender/case sinks, currency long names,
compound patterns, deriveComponent and deriveCompound rules. The pinned
grammaticalFeatures resource is now embedded with checksum verification (101
resources total). Missing rules and partial sink writes follow the source.

The source repeats the same resource key in two purported short-width fallback
paths; this behavior is retained. Inflected gender/case fallback also retains
source reference comparisons against literal strings; JavaScript string identity
cannot distinguish separately allocated equal strings. Currency patterns retain
the provider's live map and fill missing locale plural entries in that map.

Common tests cover plural fallback, Unicode space separators and German component
rules. JVM differential tests compare ICU4J private helpers across units, aliases,
widths, cases, preseeded/undersized arrays, currencies and plural locales,
compound/missing keys, grammatical rules and placeholder positions. Verification:
jvmTest, jsNodeTest, macosArm64Test and compileTestKotlinLinuxX64 passed, along
with generate-data.py --check. The pre-existing generic integral Number conversion
regression on Kotlin/JS remains explicitly skipped. Full arbitrary-unit pattern
assembly, LongNameHandler/MixedUnitLongNameHandler and the formatter facade
remain pending; this internal resource module does not expose a partial handler.

## LongNameHandler compound names and modifiers

Added the complete LongNameHandler naming algorithm over LongNameData: built-in
and arbitrary units, numerator/denominator decomposition, times/per composition,
SI/binary prefixes, powers, constant denominators, case/plural propagation,
locale-aware lowercasing, and top-down/bottom-up grammatical gender selection.
Factories produce plural-specific SimpleModifiers with source field identities
and ModifierStore parameters. Parent processing selects a plural safely through
its rounder and assigns the output gender; processQuantityWithMicros deliberately
preserves the incoming gender, as upstream does.

Source quirks remain: the prefix-gender override branch tests dimensionality
again; intermediate unit components are mutated while formatting patterns;
unsupported powers and mixed-unit paths retain their source failure behavior.
JVM assertions evaluate conditions and diagnostic callbacks lazily under the
LongNameHandler class assertion status; JS/Native omit those evaluations.
German and French unit bundles were added from the pinned source, bringing
checksum-verified embedded resources to 103.

JVM oracles compare builtin/arbitrary/prefix/power/per/constant-denominator units,
all unit widths and plural categories, grammatical cases, localized fields,
modifier metadata, display names, gender rules, rounding and currency names.
Common tests cover compound/currency pipeline output, grammatical gender and
parentless processing. The full JVM/JS/macOS test suite and Linux Native test
compilation passed. An additional JVM oracle checks mutated product components
and partial data after failures. The existing explicitly skipped Kotlin/JS
generic Number integral-conversion regression remains outstanding.

MixedUnitLongNameHandler and LongNameMultiplexer remain pending, including the
latter's nested ParentlessMicroPropsGenerator interface implemented by upstream
LongNameHandler. The callable processQuantityWithMicros method is already present;
its interface binding will be added with the multiplexer. The NumberFormatter
facade and complete formatter assembly are still pending.

## NumberFormatter numeric output stage

Extracted ICU4J NumberFormatterImpl's complete writeNumber/writeAffixes portion
into the internal NumberFormatterOutput module. It writes localized integer and
fraction digits, grouping, standard/monetary/currency decimal marks, special
values, forced zero output, and inner/middle/outer modifiers with padding. Both
contiguous supplementary digit code points and multi-character digit strings
retain UTF-16 lengths and field identities. Symbol getters retain repeated calls
and argument evaluation before failure on a null output buffer. The source
writeAffixes return value deliberately excludes the padded branch's padding and
middle/outer modifier lengths, even though those characters are inserted.

JVM differential tests invoke the pinned NumberFormatterImpl output methods and
compare strings, fields and returned lengths across four locales, all grouping
strategies, special values, minimum integer widths, currency separator policies,
custom digit sets, insertion offsets, padding positions/strings/widths, and
quantity/symbol callback traces on successful and failing paths. Common tests
exercise explicit arab numbering, supplementary/string digits, forced zero,
field identities and padded affixes. Verified with jvmTest, jsNodeTest,
macosArm64Test and compileTestKotlinLinuxX64. The pre-existing Kotlin/JS generic
Number integral-conversion regression remains explicitly skipped.

This is the shared output stage, not yet the NumberFormatter facade or macro-to-
micro pipeline builder. Those and MixedUnitLongNameHandler/LongNameMultiplexer
remain pending; a replacement default integer formatter was not introduced.

## Number pipeline locale resources

Added pinned de.res and ru.res number/locale bundles and unit/ar.res so the number
pipeline can use German/Russian numeric conventions and Arabic unit plural data.
There are now 106 checksum-verified embedded resources. generate-data.py --check
passed; the complete pipeline differential suite covers these resources against
ICU4J 78.3, with common JVM/JS/Native coverage for explicit arab numbering.

## NumberFormatterImpl assembly and mixed-unit long names

Ported the complete internal NumberFormatterImpl: safe reusable and unsafe
one-shot construction, preprocessing/integer width, prefix/suffix extraction
(which ignores its plural parameter upstream), and the macro-to-micro chain.
It resolves numbering/symbols/pattern styles, usage/conversion, scale, rounding,
grouping, padding, integer/sign/decimal policies, scientific/compact notation,
currency/measure/per-unit names and pattern modifiers in source order. Numeric
and affix output delegates to the previously ported NumberFormatterOutput.
Supplied DecimalFormatSymbols are retained by reference. Invalid usage/perUnit
combinations retain source exceptions; no branch delegates to ICU4J production.

Added MixedUnitLongNameHandler, LongNameMultiplexer and its nested parentless
interface, now also implemented by LongNameHandler. Mixed handlers preserve
component order, quantity sign changes, plural selection through the incoming
rounder, localized integral components, list formatting, modifier metadata and
source failure paths. Multiplexers copy the input list and select their handler
after parent processing sets outputUnit; parentless processing preserves gender.
The default mixed-component formatter uses the shared NumberFormatterExecution
core extracted from LocalizedNumberFormatter: synchronized atomic count update,
compilation at the threshold, volatile publication and one-shot fallback. The
counter lock is released before construction/formatting, preserving source races
between compilation and one-shot calls. The default threshold is three.

JS/Native DecimalQuantity now recognizes the internal arbitrary-precision integral
Number used by mixed conversion, taking its UnitDecimal value directly. Common
tests verify 2^64+1 remains exact through quantity construction and localized
output; this does not fix the separate generic Number.toLong/toInt Kotlin/JS
intrinsic-dispatch gap, whose regression remains explicitly skipped. The source
converter's epsilon and low-64-bit arithmetic quirks remain unchanged.

JVM differential tests compare full pipeline strings, UTF-16 fields, quantities
and MicroProps state across locales, number notations, currencies, percent/
permille, built-in/arbitrary/mixed units, all sign/grouping policies, custom
numbering systems/symbols/old affixes, precision, scale, integer truncation,
usage routing and perUnit restrictions. Further oracles cover mixed-handler
indices, large integral components, signs, parentless/parent paths and failures;
multiplexer list copying, selection, unknown units and uninitialized instances.
Common tests exercise number/currency/scientific/mixed output, compilation
threshold crossing and exact oversized integers. Verification passed:
jvmTest, jsNodeTest, macosArm64Test, compileTestKotlinLinuxX64 and generated data
checksum checking. Public NumberFormatterSettings/UnlocalizedNumberFormatter/
LocalizedNumberFormatter/FormattedNumber facades, skeletons and legacy
NumberFormat/DecimalFormat still remain pending, as do other ICU4J subsystems.

## FormattedStringBuilder logical bounds assertions

Restored ICU4J's assertion-only logical bounds checks in char/field access and
subSequence. JVM uses the FormattedStringBuilder assertion status and evaluates
conditions lazily; JS/Native do not evaluate the assertion conditions. Explicit
subString bounds validation and release array behavior remain as before. A JVM
oracle compares the source for empty/text/supplementary buffers and extreme,
negative and boundary indices, including field access and subsequences. The
complete JVM/JS/macOS suites and Linux Native test compilation passed after the
fix; the final additional JVM boundary oracle also passed.

## DisplayOptions and FormattedNumber results

Ported the entire DisplayOptions model and builder, including all seven enums,
CLDR identifier lookups and unmodifiable VALUES lists. Builders accept null
values as upstream does; build/copy operations create independent snapshots,
while options retain identity equality. Identifier matching remains exact and
case-sensitive, with UNDEFINED for unknown/null identifiers. The existing common
read-only-list implementation preserves failed mutation operations, including
no-op mutation attempts and iterator methods.

Added FormattedNumber as a FormattedValue: text, UTF-16 length/access, immutable
String subsequences, appendable export, constrained fields, attributed character
iterator, exact rounded BigDecimal, output unit, noun class, legacy gender and
fixed-decimal access. It retains the supplied buffer/quantity/unit references,
as the source package constructor does. The deprecated fixed-decimal accessor
returns the actual quantity, and null gender normalizes only through getGender
and noun-class lookup. Checked JVM append IOExceptions retain the source
ICUUncheckedIOException wrapping through Utility.appendTo.

JVM oracles compare DisplayOptions constants, identifiers, builder null/default/
copy/snapshot behavior and list mutation failures. FormattedNumber oracles use
ICU4J public formatted results across locales, simple/scientific/compact modes,
currencies and builtin/mixed units, including NaN and infinity on supported
paths. They compare strings, indices/slices, exact decimals, metadata, all plural
operands, constrained field positions and attributed runs, plus constructor
aliasing, nullable metadata and append failures. Common tests cover German unit
fields/gender, rounded decimal export, enum snapshots, readonly lists and exact
2^64+1 output. Full verification passed: jvmTest, jsNodeTest, macosArm64Test and
compileTestKotlinLinuxX64; the additional JVM assertion/special-value tests also
passed. The known unrelated Kotlin/JS generic Number integral-conversion
regression remains explicitly skipped.

Public fluent NumberFormatterSettings, NumberFormatter, UnlocalizedNumberFormatter
and LocalizedNumberFormatter facades and skeleton parsing/generation remain
pending, as do legacy formatters and other ICU4J subsystems. FormattedNumber is
ready for those facades; its package-level construction is currently used by
common tests with the complete internal number pipeline.

## StringSegment parser windows

Ported the complete StringSegment wrapper and Utility.charSequenceEquals. Mutable
offset/length windows, first code point, underlying-string codePointAt, char and
slice access, code-point advances, simple case folding, prefix matching, UnicodeSet
matching, exact content equality and debug/asString views follow ICU4J. Logical
end handling intentionally differs between getCodePoint (bounded) and
codePointAt/char access (underlying string); a cut surrogate pair exposes that
source behavior. Bounds are assertion-only where upstream uses assertions, with
lazy JVM checks and omitted condition evaluation on JS/Native. setOffset only
checks its upper bound, matching the source's negative-offset behavior.

JVM differential tests compare all operations across empty strings, surrogate
windows, invalid/failed mutations, folds, sets, null/empty comparisons and
CharSequence callback order. Common tests cover cut-pair behavior, relative
access, window reset, code-point advancement and folding versus exact matching.
The full JVM/JS/macOS suites and Linux Native test compilation passed.

## Number skeleton parsing and canonical generation

Ported NumberSkeletonImpl's complete parsing and generation algorithms into the
internal NumberSkeletonCore, with the same stem trie entries and concise aliases.
The parser retains token/option state, Pattern_White_Space separators, blueprint
fraction/significant/scientific/integer syntax, precision and trailing-zero
options, unit/currency/usage/numbering-system/scale options, duplicate detection,
range validation and error ordering. The generator preserves canonical ordering,
default elision, precision retention/priority forms and unsupported custom-setting
failures. Source quirks remain: repeated base-unit settings are accepted since
they leave unit null; perUnit is ignored by generation when unit is null.

Added the public SkeletonSyntaxException with source message/token/cause
construction and serialVersionUID. StringSegment debug views retain the complete
input and bracket the error token; numeric parse causes are preserved. Standard
BigDecimal cause-message details continue to follow the platform decimal backend.
JVM oracles compare constructor callback count, diagnostic snapshots, cause
initialization policy and serialization against ICU4J.

Differential tests compare private ICU4J parsing, canonical generation and actual
public formatter output for every literal/concise stem, required/optional options,
precision bounds, Unicode separators, units and invalid input. Deterministic
random token/option tests cover 800 additional sequences. Generator tests exercise
precision combinations, priority, trailing zeros, exponent options, per units,
scale, numbering systems and unsupported settings. Common tests cover
canonicalization, formatting, token diagnostics and source duplicate behavior.
Verification passed: jvmTest, jsNodeTest, macosArm64Test and
compileTestKotlinLinuxX64. The known Kotlin/JS generic Number integral-conversion
regression remains explicitly skipped.

The core is complete for parsing/generation; NumberSkeletonImpl's formatter cache
and create/getOrCreate facade binding remain pending with public NumberFormatter,
NumberFormatterSettings, UnlocalizedNumberFormatter and LocalizedNumberFormatter.
No partial public skeleton factory or fluent formatter was introduced. Other
ICU4J subsystems and the complete port remain outstanding.

## Generic Number dispatch on JavaScript

The public format adapter exposed an existing DecimalQuantity generic Number
bug: Kotlin/JS primitive numeric type tests classified fractional numbers as Int,
truncating 12.5 to 12 and losing signed zero/NaN/infinity. The JS generic dispatch
now uses its double input path for primitive numbers, retaining separate exact
Long, BigDecimal and mixed integral object paths. This follows JS numeric
representation; erased primitive source types cannot be recovered at runtime.
An isolated JS regression covers fractions, negative zero, NaN, infinity and an
exact Long above 2^53, and Common public-formatter tests cover both number and
format-adapter entry points. Full JVM/JS/macOS tests and Linux Native test
compilation passed; the final JS-only regression also passed. This does not fix
the separately documented Kotlin/JS generic Number.toLong/toInt intrinsic gap.

## Public fluent number formatter and skeleton cache

Added NumberFormatterSettings, NumberFormatter, UnlocalizedNumberFormatter and
LocalizedNumberFormatter. All fluent settings use immutable linked nodes with
source-order lazy, volatile MacroProps resolution, seen-key handling, fallback
nodes, null/empty usage semantics, symbol cloning, display cases, equality and
hash behavior. Explicit default settings need not compare equal to unset values;
threshold remains excluded from MacroProps equality, as upstream. Deprecated
macros/padding/threshold/case entry points remain functional. NumberFormatter's
nested enum spellings are Kotlin aliases for the maintained standalone enums;
JVM java.util.Locale entry points follow the existing ULocale Kotlin-extension
adapter convention.

NumberSkeletonImpl now supplies the source soft cache and create/getOrCreate
binding, keyed by unnormalized input. Public forSkeleton/toSkeleton use the
complete parser/generator. NumberPropertyMapper's three create overloads and
NumberFormatter.fromDecimalFormat now expose the existing legacy validation path.
Localized formatting supports Long/Double/Number/Measure, FormattedNumber results,
withoutLocale, affix extraction and the volatile last-unit override cache.
Objects.equals identity short-circuiting and virtual equals calls are preserved.
The shared execution core now resolves settings lazily and retains the source's
extra unsafe-path resolution, atomic count, affix/format shared threshold,
compilation failure behavior and volatile compiled publication. Mixed-unit integer
formatting now uses the public NumberFormatter.withLocale path directly.

Added LocalizedNumberFormatterAsFormat with a shared core. JVM instances extend
java.text.Format, support StringBuffer/FieldPosition, character iterators,
MessageFormat integration, source equality/hash and the Externalizable proxy
(version byte, language tag, skeleton, serialVersionUID 1). Portable append calls
use StringBuilder/FormatFieldPosition; JS/Native offer the same numeric formatting
and explicit unsupported parse policy without JDK dependencies. The JVM actual
uses a typealias to its Format subclass implementation. Number validation,
position reset/offset updates, append order and checked IO wrapping preserve the
source contract. The adapter retains its supplied formatter reference.

JVM oracles compare public chains, all execution thresholds, full skeletons,
measure overrides, locale removal, cached macro resolution, cloned symbols,
display-option/null/fallback behavior, equality relations, affix extraction and
legacy factory exports. Compilation count and failed-compilation behavior are
checked against ICU4J fields. Adapter tests cover fields and append offsets,
validation/null failures, real MessageFormat use, serialization/unsupported
serialization and wrapped identity. Common tests exercise public formatting,
precision, mixed units, symbol copies, lazy resolution, portable adapter behavior
and generic fractional/special Numbers. Full checks passed: jvmTest, jsNodeTest,
macosArm64Test and compileTestKotlinLinuxX64; final isolated JS regression also
passed. The prior generic Number.toLong/toInt JS intrinsic regression remains
explicitly skipped. README now includes executable public API examples.

The fluent number formatter and result/skeleton chain are connected. Full ICU4J
porting remains incomplete: legacy NumberFormat/DecimalFormat services and
parsing, number ranges and other ICU subsystems still require work, and locale
resource coverage remains incremental.

## Embedded plural resource registry

Added the already embedded plurals.res and pluralRanges.res to ICUData's
exists/enumeration registry. Their byte readers and manifest entries already
existed; the registry incorrectly reported them absent. Common tests verify both
relative/absolute existence and root enumeration. A JVM regression checks every
manifest resource for registry membership, bytes and directory enumeration.
Full JVM/JS/macOS tests and Linux Native test compilation passed.

## Number range formatting and result API

Ported NumberRangeFormatter, its complete fluent settings and localized/
unlocalized facades, RangeMacroProps, NumberRangeFormatterImpl and
FormattedNumberRange. Enums and the serialized NUMBER_RANGE_SPAN field retain
source values/order/UID. The implementation builds both numeric engines even
when the second is unused, enforces the source numbering-system check, loads
range patterns with latin/default fallback, resolves range plurals and applies
the complete inner/middle/outer collapse and spacing heuristics. Approximate
fallback reformats the pre-rounding backup with approximately=true. Results retain
both rounded quantities, identity metadata, field/span ranges, append/iterator
exports and exact decimal/fixed-decimal endpoints.

Source behavior is retained: numberFormatterBoth/First/Second write two settings
nodes; resolving settings mutates child formatters' cached locale; sameFormatters
is omitted from macro equality/hash; non-identical formatter paths may render a
range despite equal identity metadata; Number input equality is virtual and
scale/type-sensitive before rounding. FormattedNumberRange equality ignores
identityResult and uses unwrapped fields plus scale-sensitive BigDecimals. Its
hash uses raw field arrays (including span-placeholder identity hashes), as
upstream does; it was not replaced with a new value-based hash. Source mixed-unit
modifier-store assertion/failure paths remain, rather than adding new semantics.
Null locale/number and unsupported numbering paths also follow ICU4J. JVM Locale
entry points use the existing Kotlin extension convention.

JVM differential tests cover all collapse/identity policies across six locales,
plain/fraction/currency/long-unit/scientific/compact/percent/mixed configurations,
identity before/after rounding, signs, zero, NaN/infinity, different formatters,
numbering compatibility and numeric types/oversized integers. They compare text,
rounded endpoints, identity, UTF-16 characters/slices, constrained numeric/span
positions, attributed runs and all plural operands. Further contracts verify
cached locale mutation, sameFormatters equality, constructor reference retention,
shared-field hash formulas, equality/hash callback order, null metadata, span
serialization and append IOException wrapping. Common tests cover collapsed
units/spans, rounded identities, generic numeric inputs and locale removal.
Verification passed: jvmTest, jsNodeTest, macosArm64Test,
compileTestKotlinLinuxX64, and checksums for all 106 embedded resources. The known
unrelated Kotlin/JS generic Number.toLong/toInt regression remains skipped.
README contains a public range example. Full ICU4J porting remains incomplete,
including legacy number formatting/parsing services and other major subsystems.

### Number parsing matcher infrastructure

Ported ParsedNumber, NumberParseMatcher, SymbolMatcher, SeriesMatcher,
CodePointMatcher, ParsingUtils, sign/approximately/percent/permille/infinity/NaN
matchers, weak ignorables/padding, validation matchers, and reciprocal multiplier
post-processing. Strong character boundaries remain distinct from cursor
movement. Series matching preserves flexible repetition, weak-character rewind,
and deep quantity rollback. Number extraction retains special-value priority,
negative zero, integral/BigDecimal selection and parse-flag behavior. Source
assertions are lazy JVM assertions and omitted on JS/Native.

JVM differential tests compare ICU4J across four locales, symbol prefixes,
partial/custom padding, flags, validators, numeric representations and series
rollback. This is infrastructure: DecimalMatcher, scientific/currency/affix
matching and the complete number parser still require porting.
Verification passed: jvmTest, jsNodeTest, macosArm64Test and
compileTestKotlinLinuxX64 (full run, 2m 48s).

### Decimal and scientific number parsing

Ported DecimalMatcher and ScientificMatcher. Decimal parsing preserves Unicode
and custom string digits, literal/equivalent monetary and ordinary separators,
strict primary/secondary grouping validation, lenient lone-group rewinds,
integer-only parsing and exponent magnitude overflow handling. Scientific
parsing retains partial separator/sign rollback, strict ignorable matching,
NaN/infinity placeholder quantities and the one-exponent guard. The upstream
cached lead-set selection ordering is retained.

JVM differential matrices cover four locales, western/Indic grouping, custom
multi-character separators and digits, seven flag policies, normal/positive/
negative exponent modes, oversized exponents, random input, every prefix of
scientific samples, special values and custom exponent/sign strings. Common
regressions exercise grouping rollback, supplementary digits, fractions,
scientific rollback and exponent overflow. Currency/affix matching and the
NumberParserImpl integration remain to be ported.
Final verification passed: jvmTest, jsNodeTest, macosArm64Test and
compileTestKotlinLinuxX64 (3m 3s). Separator getter/null-check order follows
upstream even for subclassed DecimalFormatSymbols.

### Currency and affix token matching

Ported CombinedCurrencyMatcher, AffixTokenMatcherFactory and AffixPatternMatcher.
Currency matching preserves local-symbol and ISO-code precedence, the distinct
case-sensitive symbol/case-folded long-name trie behavior, no-foreign-currency
plural names, source short-circuiting, partial matches and weak prefix/suffix
spacing. Local plural names use the DecimalFormatSymbols requested locale,
equivalent to the upstream Java Locale path. Affix construction keeps token
factory dispatch, exact versus ignorable-aware matching, literal Unicode code
points, shared currency token matching, pattern equality/hash and SeriesMatcher
rollback/flexible-token behavior.

JVM differential tests compare all prefixes of currency inputs in four locales,
three currencies, two spacing strings, foreign/local-only policies, prefix/
suffix placement and folding modes. Affix tests cover all currency token widths,
signs, percent/permille, quoted literals/quotes, supplementary characters,
whitespace, exact matching, flags and rollback. Common regressions cover local
currency names and weak spacing plus affix rollback and exact whitespace.
AffixMatcher pairing and NumberParserImpl integration remain incomplete.
Verification passed: jvmTest, jsNodeTest, macosArm64Test and
compileTestKotlinLinuxX64 (2m 35s).

### Affix pairing and complete NumberParserImpl

Ported AffixMatcher and NumberParserImpl, including all parser factories and
static number/currency parse helpers (portable ParsePosition). Affix generation
preserves sign policies, unpaired lenient alternatives, source comparator tie
breaking and paired-prefix/suffix post-processing. Greedy parsing restarts from
the first matcher after every consumption; longest parsing tries code-point
prefixes with copied candidates and the upstream 100-level recursion limit
(or the explicit unlimited flag). Matcher order, live callbacks, assertions,
post-processing, supplied result state, start offsets, strict/Java-compatible
modes, validators and reciprocal multipliers follow ICU4J.

JVM differential tests cover four locales, nine patterns, all three parse modes,
number/currency parsing and both search strategies, plus parse option flags,
static ParsePosition behavior, random simple-parser inputs and explicit starts.
They compare parser plans, flags, matched affixes/currency, consumed boundaries,
quantity and numeric types/values, success and exception types. Object identity
addresses in Scale diagnostic strings are normalized only in tests. Common
regressions cover strict paired affixes, percent scaling, currency amounts,
recursive limits/unlimited mode and nonzero start offsets. Legacy public
NumberFormat/DecimalFormat integration and other ICU4J subsystems remain.
Verification passed: jvmTest, jsNodeTest, macosArm64Test and
compileTestKotlinLinuxX64 (3m 57s).

### UFormat and DisplayContext foundations

Ported UFormat locale metadata and verified the existing DisplayContext
(all eleven values and four types). UFormat retains reference identity,
null type selecting the valid locale,
atomic rejection of half-null locale pairs, and the source serialization UID.
Its nested SpanField is a Kotlin alias for the already ported FormatSpanField,
so existing number-range spans retain their category.

UFormatBase provides the platform Format boundary. JVM UFormat subclasses are
real java.text.Format instances with StringBuffer/Java ParsePosition entry
points, and Common subclasses implement StringBuilder/portable ParsePosition
operations. Bridges retain buffer/position mutations even when a callback
throws, and distinct returned buffers remain distinct. JS/Native implement the
standard one-argument format/parse and unannotated character iterator protocol.
JVM and JS subclasses can use shallow cloneHost; Native subclasses provide typed
copies using copyLocaleTo, following the existing ICUCloneableBase convention.
This does not yet add public NumberFormat or DecimalFormat.

JVM comparisons verify locale failure atomicity against ICU4J, Format callback
results/mutations/failures, clone aliasing, serialization round trips/UID and all
DisplayContext enum values. Common tests cover protocol dispatch, metadata,
copying, parse errors and default attributed iterators.
Verification passed: jvmTest, jsNodeTest, macosArm64Test and
compileTestKotlinLinuxX64 (2m 49s).

### ICU legacy decimal MathContext

Ported com.ibm.icu.math.MathContext as io.github.heapy.icu4k.math.MathContext,
distinct from the existing java.math-compatible util.MathContext. All four
constructors, precision bounds, notation and rounding constants, DEFAULT,
accessors, diagnostic strings and validation order follow ICU4J. Equality stays
identity-based. The four source package fields retain their names/types for JVM
serialization, with the source UID and no added read-time validation. String
rendering also preserves the source fallback for invalid deserialized state.

JVM differential tests compare boundary/invalid combinations of every argument,
all overloads, public constants, getters and strings, object-stream field shape,
UID and round trips including constructor-bypassing state. Common regressions
cover defaults, identity equality, notation and ordered validation diagnostics.
Legacy ICU BigDecimal and public NumberFormat/DecimalFormat remain to be ported.
Verification passed: jvmTest, jsNodeTest, macosArm64Test and
compileTestKotlinLinuxX64 (3m 3s).

### ICU BigDecimal decimal-array kernel

Extracted and ported the legacy BigDecimal private digit-array kernel as
IcuDecimalDigits for use by the forthcoming Common arithmetic implementation.
The source carry/borrow tables, A+B*M loop, multiplier fast paths, right-padded
virtual lengths, leading zero retention, both array-reuse cases and short/long
carry copying remain. The array extension and trailing-zero predicates retain
source identity, copying and range behavior. No public partial BigDecimal facade
is introduced; parsing, layout, rounding and arithmetic still require porting.

JVM differential tests invoke ICU4J's private helpers via reflection and compare
14,000 randomized cases, virtual widths, all multipliers -9..9, shared A/B input,
result identity and mutations of both arrays. Further tests cover extension,
zero ranges and invalid helper inputs. Common regressions cover carry, borrow,
virtual zeroes, destructive reuse and extension identity.
Verification passed: jvmTest, jsNodeTest, macosArm64Test and
compileTestKotlinLinuxX64 (3m 17s).

### Legacy decimal representation, parsing, layout and rounding

Ported the next ICU BigDecimal algorithm layer into IcuDecimalValue, a mutable
internal working representation with source sign/form/mantissa/exponent state.
It includes string and char-array slice parsing, BMP Unicode digit conversion,
explicit exponent limits, zero/scale handling, plain/scientific/engineering
layout, significant-digit rounding, lost-digit checks and finish/canonicalization.
Copies share mantissa arrays until the source algorithm replaces them. Rounding
uses the previously ported digit kernel and retains mutation before errors,
carry adjustment and exponent overflow diagnostics. Engineering boundary
reprieves and source formatting thresholds are preserved.

JVM comparisons inspect ICU4J state via reflection after parsing, rounding and
finish, even on exceptions. Tests cover random malformed strings, char-array
slices/bounds, Unicode, all rounding policies, negative/zero requested lengths,
4,000 randomized finish states, exponent boundaries and lost-digit diagnostics.
Common cases cover layout, scale-preserving zeros, shared copies, tie rounding,
carry, failure state and canonicalization. This internal layer is preparatory;
public ICU BigDecimal arithmetic/conversions still remain, as do the public
legacy number formatter classes.
Verification passed: jvmTest, jsNodeTest, macosArm64Test and
compileTestKotlinLinuxX64 (6m 49s).

### Legacy decimal basic arithmetic and comparison

Added source algorithms for plus, negate, abs, add, subtract, multiply,
context-sensitive compare, min and max to IcuDecimalValue. Addition preserves
virtual alignment/truncation, dominant-operand shortcuts, sign swaps and
canonical floating cancellation. Multiplication preserves operand rounding,
shorter-operand iteration, virtual multiplicand lengths and unlimited fixed-point
padding. Lost-digit checks and failures run in source order. Unary/plain fast
paths preserve object and mantissa sharing; inputs remain unchanged on success
or failure. The public BigDecimal facade, division, powers and conversions still
remain incomplete.

JVM differential matrices compare all nine operations over signs/scales,
precisions, three forms and eight rounding policies, randomized lost-digit cases,
aliased operands, null order, defaults and extreme exponents. Result state,
identity (including canonical zero), mantissa sharing and both original operands
are checked. Billion-digit plain layouts are deliberately not materialized in
these tests; their exact internal state is compared instead. Common regressions
cover scales, pre-rounded multiplication, carry, dominance, precision-sensitive
comparison, canonical zero and lost-digit diagnostics.
Verification passed: jvmTest, jsNodeTest, macosArm64Test and
compileTestKotlinLinuxX64 (3m 37s).

### Legacy decimal division, remainder, powers and scale

Ported the source long-division algorithm into IcuDecimalValue, including scaled
and precision-based division, integer division, remainder and all internal exit
paths. Residual-digit rounding markers, quotient overflow checks, zero identity,
source exponent accounting and operand pre-rounding remain. Added scale/setScale,
exact Int conversion and the source 31-bit power algorithm with working precision,
negative-power reciprocal and argument validation order. setScale retains lazy
rounding-mode validation and carry/scale repair.

JVM comparisons exercise context matrices and 2,000 randomized divisions,
explicit scales/modes (including invalid arguments), defaults, powers, zero
bases/divisors, negative/fractional/oversized exponents and exact Int limits.
They compare internal state, values, sharing/identity, exceptions and unchanged
operands. Common regressions cover recurring/scaled division, signed residue,
integer overflow, rounding failures, negative powers, scale repair and Int bounds.
The legacy public BigDecimal facade, remaining conversions and format methods
are still incomplete; public NumberFormat/DecimalFormat integration also remains.
Verification passed: jvmTest, jsNodeTest, macosArm64Test and
compileTestKotlinLinuxX64 (3m 48s).

### Legacy decimal formatting, exact conversions and representation

Added both source format overloads, field/exponent padding, scientific/engineering
selection, fractional digit adjustment/carry loops and ordered diagnostics to
IcuDecimalValue. The source argument-4 diagnostic referring to explaces is kept.
Added exact Long/Byte/Short conversion, floating conversion, signum, point shifts,
representation equality and layout-based hash operations. Source longValueExact's
19-digit exponent-zero rejection versus fractional-zero path is preserved.
Working-state identity remains unchanged; representation comparison is explicit
for use by the forthcoming immutable public facade.

JVM float conversion uses the platform decimal-to-Float parser. JS and Native
correct their platform candidates using exact IEEE binary32 midpoint comparisons, avoiding
decimal-to-Double-to-Float double rounding at ties, zero and overflow. Comparisons
use decimal magnitudes/virtual digits rather than materializing enormous plain
values. The Native correction also handles an observed platform rounding error just above
the zero/subnormal midpoint. JVM parsing is unchanged.

JVM matrices compare valid/invalid formatting policies, exact conversions, point
motion, equality/hash and difficult Float boundaries against ICU4J. Common tests
cover padded layouts, carry, diagnostics, Long asymmetry and Float midpoint/
underflow behavior. Public BigDecimal, constructors and arbitrary-integer-related
conversions remain incomplete, followed by public legacy number formatters.
Verification passed: jvmTest, jsNodeTest, macosArm64Test and
compileTestKotlinLinuxX64 (final run, 2m 11s). Native subnormal midpoint regression
was reproduced, corrected and verified across seven adjacent midpoint cases.

### Legacy decimal construction and arbitrary-integer conversion layer

Added source Int and Long construction algorithms, exact Double/standard decimal
construction and cached valueOf factories to IcuDecimalValue. Int constructors
retain shared zero/one mantissas, Long constructors allocate fresh digits, and
valueOf caches exactly zero/one/ten before applying scale. Shortest Double text
uses the existing CommonDoubleToDecimal Java-compatible algorithm, preserving
scientific-form distinctions and signed-zero scale versus exact construction.

Ported integer-part/exact/unscaled conversion preparation, standard BigDecimal
conversion and truncating low-32/64-bit numeric conversion. Integer text is the
source preparation step for the forthcoming BigInteger boundary; this does not
yet introduce a partial public ICU BigDecimal facade. Standard-decimal conversion
retains the source non-negative scale via unscaled integer plus scale.

JVM comparisons cover random Int/Long and Double inputs, bounds, factory identity,
mantissa sharing, invalid scales/nonfinite values, integer truncation, exactness,
unscaled values and standard decimal scale. Common regressions cover constructor
sharing, exact versus shortest Double, cached scaled constants and oversized
signed low-bit conversion. Public BigInteger interchange/BigDecimal and remaining
legacy formatter integration are still incomplete.
Verification passed: jvmTest, jsNodeTest, macosArm64Test and
compileTestKotlinLinuxX64 (4m 58s). Repository exploration confirmed no existing
Common BigInteger type; UnitDecimal supplies decimal arbitrary precision and
low-bit arithmetic, but a public integer boundary/radix API remains needed.

### Common arbitrary-integer interchange boundary

Added util.BigInteger as an expect/actual boundary: JVM aliases java.math.BigInteger;
JS and Native use immutable Common decimal-magnitude storage. The Common contract
covers String construction with radix, sign, comparison, equality/hash, radix
output and Number conversions required by ICU decimal interchange. This is a
subset boundary, not a port of the complete JDK BigInteger arithmetic API.

Radix parsing/output supports bases 2 through 36, Unicode BMP decimal digits,
leading signs/zeros and JDK invalid-output-radix fallback. Parsing preserves
JDK validation order and Integer.parseInt group diagnostics. Numeric conversions
retain signed low bits; Float uses the existing exact midpoint correction.
Hashing reproduces the JDK unsigned 32-bit word polynomial.

JVM differential tests compare 180 randomized integers plus bounds across valid
and invalid radices, parsing diagnostics, hash, comparison and primitive values.
Common regressions exercise radix interchange, Unicode digits, negative zero,
low bits and Float ties. All checks passed: jvmTest, jsNodeTest, macosArm64Test
and compileTestKotlinLinuxX64 (3m 58s). The public legacy math.BigDecimal facade
and remaining legacy formatter integration still remain to be ported.

### Public legacy ICU math.BigDecimal

Added the complete public ICU math.BigDecimal facade in commonMain, backed by
IcuDecimalValue's source algorithms. All source constructors, arithmetic and
context overloads, formatting, exact/truncating conversions, integer/standard
decimal interchange, point motion, scale, constants and factories are exposed.
Representation-sensitive equality/hash, operand/constant result identity and
mantissa sharing are preserved. JVM reflection verifies that every source
public method and constructor has a matching signature in the port.

The facade stores exactly the source ind/form/mant/exp fields and source
serialVersionUID, with java.lang.Number as its JVM superclass. Ephemeral working
states retain nullable raw mantissas without eager validation, preserving source
behavior after default deserialization of malformed state. JVM tests verify
field descriptors, round trips and interchange in both directions using class
name descriptor substitution; production code has no custom stream hooks.

Number dispatch on JVM, JS and Native now recognizes the legacy decimal and
passes its standard-decimal conversion into the existing formatting pipeline.
Common tests exercise public arithmetic, representation, identity, arbitrary
integers, Float ties and formatter integration. JVM tests compare constructors,
virtual BigInteger text callbacks, factories, 250 operation/context matrices,
primitive/exact conversions, formatting, exceptions, sharing and serialization
against ICU4J. Verification passed: jvmTest, jsNodeTest, macosArm64Test and
compileTestKotlinLinuxX64 (final run, 2m 28s). Public legacy NumberFormat and
DecimalFormat integration and other unported ICU subsystems remain outstanding.

### DecimalFormat runtime, parser publication and pattern updates

Added internal DecimalFormatCore as the source runtime for the forthcoming public
DecimalFormat/NumberFormat hierarchy. It implements constructor/style rounding
policy, symbol ownership/copies, pattern and localized-pattern application,
property callbacks, formatter refresh, lazy parser/currency-parser publication,
formatting, currency-amount overrides, field-position/fraction metadata,
attributed output, parsing and legacy-decimal result conversion. Pattern export
uses effective currency rounding while retaining original affix patterns. Runtime
copies clone properties and symbols and rebuild their formatter/parser state.

Source update/failure order is preserved: failed pattern parsing or a throwing
property callback does not refresh the published formatter or clear parser
caches; prior property mutations remain. Invalid parse offsets, end-of-input,
existing error indexes, source error-index arithmetic and extreme legacy decimal
fallback (including negative zero and zero with oversized positive exponent)
are retained. Configuration synchronization remains the owning public facade's
responsibility; immutable formatter/unsynchronized lazy parser access uses source
volatile fields rather than a new locking policy.

JS/Native generic quantity dispatch now accepts the Common BigInteger boundary.
The Any-to-Number formatting bridge recognizes Kotlin Number subclasses on JS,
where the compiler's intrinsic Number check/cast only covers primitives and Long.
It also fixes the existing NumberFormatter.toFormat adapter for portable decimal
and integer objects, while preserving rejection of unsupported Number subclasses
inside the quantity loader rather than at the outer type check.

JVM comparisons cover eight patterns across five locales, numeric kinds, field
positions/fraction metadata, parsing/currency parsing at multiple offsets,
mutation/copy/reset behavior, all ten style constructors, currency overrides and
cache/failure ordering. Common regressions verify snapshot publication, copies,
legacy-decimal parse results, Any/Number bridges and range fallback. Verification
passed: jsNodeTest, macosArm64Test and compileTestKotlinLinuxX64 in the initial
run; jvmTest in the final run (1m 27s) after JVM test-table typing was corrected.
No production changes occurred between these checks. The public inheritance
facades, their configuration accessors/serialization and number-format service
factories (including rule-based numbering) still remain to be ported.

### Currency plural property ownership correction

Corrected DecimalFormatProperties.setCurrencyPluralInfo to clone a non-null
argument, as ICU4J does. This prevents later caller mutations from changing the
stored plural patterns. copyFrom/clone of the property bag remain shallow and
share the already-owned plural-info object. JVM comparisons now verify input
identity and shallow-copy identity against the source for every reference field,
replacing an old test assumption that all setters retain their input reference.

The correction was verified with the DecimalFormat settings matrix and existing
property tests. jsNodeTest, macosArm64Test and compileTestKotlinLinuxX64 passed in
the combined run; final jvmTest passed in 1m 42s after old test assumptions were
corrected. Production sources were unchanged between these verification runs.

### DecimalFormat configuration operations

Added the source configuration getters/setters to DecimalFormatCore: affixes,
sign, multiplier normalization, rounding-increment overloads, rounding modes,
standard and ICU math contexts, integer/fraction/significant digit coupling,
padding, scientific notation, grouping, currency and parsing flags. The ICU
context form is remembered independently of standard context changes and copied
with the runtime. Requested properties versus exported effective values follow
the source; negative/boundary values are delegated to the existing mapper.

Preserved source no-op/publication policies: deprecated parse-max-digits does
nothing; an already-matching significant-digit mode does not refresh; a zero
standard-decimal rounding increment changes the requested maximum fraction count
without refreshing, whereas a zero Double clears the increment and refreshes.
A failed ICU context update may change the remembered form/requested context
while retaining the prior formatter, exported values and parser caches.

JVM comparisons cover all setting getter states and complete requested/exported
property bags, boundary Int/Byte/Char inputs, invalid/null arguments, every
rounding mode and ICU context form/lost-digits combination, currencies, parse
modes and 180 successive random digit/multiplier/grouping updates. They compare
exceptions, output/patterns, getter values, copy state and formatter/parser
publication identity. Common regressions cover these source-specific behaviors
and plural-info ownership. Verification passed: jsNodeTest, macosArm64Test and
compileTestKotlinLinuxX64 in the combined run; final jvmTest in 1m 42s. Only JVM
test corrections followed the combined run. The public inheritance facades must
still supply synchronization and virtual cross-method dispatch, serialization,
number-format service factories and rule-based numbering integration.

### Public NumberFormat instance base and stream compatibility

Added the public abstract NumberFormat instance protocol in commonMain, with
source numeric overload dispatch, convenience methods, parse wrappers, default
currency formatting/parsing, digit limits, grouping, parse flags, capitalization
contexts, currency, equality/hash, constants and the existing Field identities.
Localized static factories, nested factory/service APIs and the concrete public
DecimalFormat hierarchy are still pending; the base does not publish substitute
factory implementations.

Digit limits clamp negatives and update their paired bounds as the NumberFormat
source does (distinct from DecimalFormat's raw property setters). Equality ignores
currency and locale metadata and requires equal runtime classes. Hashing retains
the source use of the legacy maxFractionDigits byte. Stream hooks preserve the
source fields and serialVersionUID, legacy byte conversion/capitalization defaults,
range validation, version upgrades and write-time byte saturation, including its
observable mutation of hashCode. JVM tests verify field descriptors, real round
trips and historical/malformed stream-hook states against ICU4J.

Currency formatting shares the owning object's reentrant monitor and preserves
virtual calls, equality callbacks and source failure order: an exception during
formatting leaves the temporary currency set. Subclasses can use the same monitor
for their synchronized configuration methods. JVM locking still works after
object-stream restoration; Native uses recursive locks. UFormatBase now exposes
its clone protocol. JVM/JS retain host shallow copying; Native custom subclasses
provide a typed clone using copyNumberFormatTo, as the other portable clone bases
already require.

The JS generic-Number-to-Double boundary invokes overrides for custom Number
objects instead of coercing their toString value. Common tests cover dispatch,
custom numbers, returned-buffer handling, cloning, contexts, reentrant currency
updates, equality callbacks and parsing wrappers. JVM tests compare settings,
exceptions, identity, monitor ownership, serialization and callback order.
Verification passed: jvmTest, jsNodeTest, macosArm64Test and
compileTestKotlinLinuxX64 (final run, 6m 28s).

### Public DecimalFormat hierarchy and stream migration

Added public DecimalFormat extending NumberFormat, exposing all source-declared
public methods and constructors through the Common StringBuilder/ParsePosition
boundaries. The facade uses DecimalFormatCore for the previously verified numeric
algorithms/settings and shares NumberFormat's owner monitor for synchronized
configuration. Formatting, parsing, snapshot access and source no-op methods keep
their source locking policy. Compound methods preserve virtual calls through
applyPattern, toPattern, standard rounding-increment and math-context overloads.
JVM/JS clones retain unknown subclass state; Native subclasses use the existing
typed-copy convention. Copies rebuild independent properties/symbols/runtime,
carry base locale state and retain ICU context form.

Added source-compatible JVM custom stream hooks with the original UID and sole
non-transient version field: modern property/symbol streams, the ICU 59 Properties
wrapper and pre-59 field migration. Legacy affix precedence, parent-field recovery,
ignored old fields, version/field-count diagnostics, reserved-word consumption,
setter failure ordering and formatter reconstruction follow ICU4J. Null transient
working fields retain source NPE ordering rather than lateinit diagnostics.
Symbols now publish through a volatile backing field. Stream tests compare real
round trips (including a second round trip) and synthetic historical/invalid
layouts directly with ICU4J private stream hooks.

Locale metadata is queried at refresh time after property callbacks, matching the
source even when a callback changes locale. Clone providers bind to the new owner;
subsequent locale/configuration changes do not affect the original formatter.
JVM reflection verifies coverage of all source-declared public signatures,
constructors and constants. Common and JVM tests cover formatting, parser result
types, symbols, snapshot/clone isolation, currency overrides, virtual callback
order, monitor ownership, metadata changes and serialization migrations.
Verification passed: jvmTest, jsNodeTest, macosArm64Test and
compileTestKotlinLinuxX64 (final run, 3m 31s). NumberFormat's localized factory and
service APIs, rule-based numbering and other unported ICU subsystems remain.

### MessagePattern and SelectFormat

Ported the complete MessagePattern parser to Common Kotlin: indexed syntax parts,
message/argument limits, nested choice/plural/select/selectordinal styles,
explicit selectors and offsets, both apostrophe modes and deep auto-quoting.
Clones copy the part list while sharing immutable parts; frozen clones retain
identity. Partial state after invalid input follows ICU4J. Argument-number overflow,
length checks, numeric diagnostics and surrogate-safe error prefixes are retained.
The host Cloneable marker does not introduce serialization on MessagePattern.
Common tests and JVM oracle tests cover valid/truncated/randomized patterns,
syntax parts, values, state transitions, freezing, cloning and public signatures.

Ported SelectFormat's public API, default/duplicate keyword selection, quoting,
nested argument text, failure reset, Java Format bridge and JVM stream restoration.
Clones share the mutable MessagePattern, including the source behavior where a
clone's applyPattern affects the original's formatting but not its toPattern.
The original UID and serialized pattern field are retained. Tests compare both
apostrophe modes, errors, field positions, clone behavior and stream round trips.
Commits: 3e08f30 (parser), b75755e (SelectFormat).

### PluralFormat execution engine and RBNF scanner contracts

Added internal PluralFormatCore for source formatting and RBNF parseType behavior.
This is execution state with an injected NumberFormat and PluralRules, not a
replacement default NumberFormat factory or a completed public PluralFormat API.
Locale constructors, facade serialization and factory wiring remain to be ported
with the NumberFormat/RBNF dependency cycle.

The engine retains explicit-value precedence, first duplicate keyword selection,
lazy selector callbacks, offsets, rounded DecimalFormat plural operands, generic
NumberFormat dispatch, apostrophe modes, top-level number replacement and nested
argument reduction. It preserves callback-driven rule/pattern changes and shallow
clone selection through the original owner's rule adapter. Failed patterns clear
the shared parser and reset the failed owner's pattern/offset as in ICU4J.

Added the deprecated public RbnfLenientScanner and RbnfLenientScannerProvider
contracts. parseType checks exact text before invoking a scanner, keeps source
candidate ordering/length rules, returns the matched message text and computes
positions from pattern length rather than scanner consumption. Invalid/null
scanner results, negative/out-of-range starts and empty messages follow source
behavior. The Chinese RBNF postprocessor kernel was previously ported in b5a6247;
its NFRuleSet adapter remains to be connected with the RBNF engine.

Common tests cover execution, custom Number callbacks, clone ownership, parsing
and empty-message/out-of-range search on every runtime. JVM oracle tests compare
six locales, cardinal/ordinal rules, five decimal patterns, explicit/offset/nested
messages, large numbers, non-finite values, selector call order, both apostrophe
modes, failure state and lenient scanner interactions.
Verification passed: jvmTest, jsNodeTest, macosArm64Test and
compileTestKotlinLinuxX64 (final boundary run, 2m 41s).

### NumberFormat factory contracts and service engine

Added NumberFormatFactory and SimpleNumberFormatFactory, exposed through the
source-style NumberFormat nested Kotlin aliases. JVM implementations retain
both virtual createFormat overloads (ULocale and java.util.Locale), including
mutual delegation and null behavior, plus both sets of Simple factory locale
constructors. Either overload can be overridden on JVM. Portable factories use
the ULocale overload. The source contract requires an override to avoid recursive
default dispatch. Constants, visibility, base locale names, stable singleton set
identity and immutable set operations are preserved. Implementation classes are
top-level JVM types behind Common nested aliases, as with other portable aliases.

Added internal NumberFormatServiceCore using the existing ICULocaleService,
with the source default resource-factory registration and markDefault behavior.
The built-in format creator is injected until numeric/RBNF factory wiring is
complete; this is not yet the public global getInstance/registerFactory facade.
Custom factories use canonical requested locales, continue lookup after themselves
when returning null, participate in normal locale fallback and retain the source
cache policy. Each result is cloned, currency styles resolve currency from the
requested locale, and actual/valid locale metadata is applied last. Missing
results and invalid unregister keys follow source exceptions. Default locale
availability reuses the resource arrays and Java-locale cache; modified registries
use visible factory IDs. Removal restores the default availability path.

Common tests cover factory names/mutation, visibility, fallback, caching, clone
isolation, all currency styles, metadata and unregistration. JVM tests compare
ICU4J virtual overloads, constructors/constants/final methods, callback traces,
fallback within a custom factory, prototype isolation, requested currency,
locale availability and exception behavior. A null-clone fixture confirms currency
argument evaluation before the null receiver failure, including invalid currency
codes, matching the source.
Verification passed: jvmTest, jsNodeTest, macosArm64Test and
compileTestKotlinLinuxX64 (3m 15s), followed by the additional JVM clone boundary
oracle test (19s). Public factory entry points, the built-in format creator,
PluralFormat's public facade and RBNF remain to be connected.

### Built-in NumberFormat creation and public pattern access

Ported NumberFormat.createInstance's built-in style creator to internal
NumberFormatDataFactory. Pattern lookup and symbol construction precede currency
pattern overrides, ISO currency-sign replacement and final numbering-system
resolution. Numeric DecimalFormat construction preserves integer-style setter
order, cash usage, plural currency info and final valid/actual locale assignment.
Algorithmic descriptions retain the source first/last slash parsing, exact
SpelloutRules group comparison, numbering-system default group, locale and rule
set selection. An injected callback must construct RBNF and set its default rule
set before returning; the creator applies symbols' locale metadata afterwards.
This callback is the integration boundary for the pending Common RBNF engine,
not an alternative implementation of algorithmic formatting.

Exposed the source public getPatternForStyle and
getPatternForStyleAndNumberingSystem methods on NumberFormat, including JVM
static entry points. Their existing exhaustive locale/style oracle now exercises
these public methods. Invalid-style JVM assertions use the NumberFormat class,
matching the source assertion site.

Common tests cover numeric style behavior, integer parsing, ISO patterns, CHF
cash rounding, service composition, algorithmic dispatch and metadata timing.
JVM tests compare all styles across representative locales/keywords, every
numeric digit system, formatting, parsing, settings and locale metadata. Every
built-in algorithmic numbering system is checked by constructing an ICU4J RBNF
oracle from the callback parameters and comparing rules, default rule set and
constructor locale with ICU4J's own built-in creator. Production remains free of
ICU4J dependencies. Null/invalid inputs and public JVM pattern signatures are
also compared.
Verification passed: jvmTest, jsNodeTest, macosArm64Test and
compileTestKotlinLinuxX64 (3m 23s); final JVM oracle/pattern check after simplifying
the invalid-input assertions passed (21s). Global NumberFormat factories and the
public PluralFormat/RBNF facades are still pending the RBNF engine and data.

### Complete RBNF resource data and predefined-description loading

Embedded all 112 RBNF .res files from the pinned ICU 78.3 source tree (685,824
binary bytes), including root, locale aliases and res_index. Each file is recorded
with size and SHA-256 in the data manifest and generated as portable lazy Base64
data. ICUData registers byte lookup and directory enumeration for every file.
The total embedded resource count is now 218. No fullLocaleNames.lst was added:
it is absent from both the pinned RBNF source directory and published ICU4J jar;
the source index fallback remains in use when resource scanning is disabled.

Added internal RbnfRuleData loading from RuleBasedNumberFormat's predefined-locale
constructor. It reports bundle locale metadata before reading rules, concatenates
rule-array strings in order, loads optional display-name arrays with top-level
fallback and retains the source missing-group exception policy. Root includes
minimal numeric spellout rules. Invalid format bounds fail at the source lookup
point after the locale callback; an explicit check is needed on JS because its
ordinary Array.get is unchecked. Rule parsing and the public RBNF facade remain
separate pending work.

Common tests use the embedded-only loader to load all four rule groups for all
111 resource locales (excluding res_index, with root represented by the empty
locale ID). They cover resource-copy ownership, descriptions, aliases/fallback,
root rules, metadata ordering and invalid bounds. JVM oracle tests compare every
RBNF byte with the published ICU4J artifact and on-disk pinned files, locale
availability with scanning enabled/disabled, descriptions, display-name tables,
aliases and fallback locales. They also verify fresh localization arrays and
source-constructor bounds behavior.
Verification passed: scripts/generate-data.py --check (218 resources), jvmTest,
jsNodeTest, macosArm64Test and compileTestKotlinLinuxX64 (final run, 2m 48s).
The RBNF engine classes, standard collation-backed lenient scanner and public
factory/facade wiring remain unfinished; other ICU data coverage is incremental.

### RBNF rule-set control flow

Ported NFRuleSet into internal RbnfRuleSetCore: two-phase construction, implicit
base values, normal binary selection and rollback, special-rule selection,
fraction denominator selection, formatting dispatch, recursive parse masks,
longest-match parsing and decimal-symbol propagation. Concrete NFRule and
NFSubstitution construction remains behind internal contracts and is unfinished;
this does not yet expose a working public RuleBasedNumberFormat.

RbnfMath preserves Java signed Math.round(double), saturating boundaries,
wrapped Long arithmetic and division/remainder edge cases across targets.
Common tests cover names, normal and fraction selection, rollback, special
rules, long/double dispatch, recursion limits, parse tie order, masks and parse
position state. JVM tests compare rounding against Math.round for 100,000 raw
binary values and neighbors of half-integers. Concrete-rule integration and
full NFRuleSet differential coverage remain pending.
Verification: jvmTest, jsNodeTest and macosArm64Test passed;
compileTestKotlinLinuxX64 passed (Linux tests compiled, not executed), 3m 11s.

### RBNF individual rules

Ported ICU4J NFRule into internal RbnfRuleCore: descriptor parsing, radix and
exponent computation, wrapped integer powers, optional/alternative bracket
expansion, substitution extraction, plural fragments, formatting offset
adjustments, rollback and delimiter-based parsing. Preserved source construction
order, fraction-special expansion details, lazy failures, partial output and
parse-position behavior. Substring/array checks are explicit where JS would
otherwise accept invalid indices. RbnfRuleOwner and RbnfSubstitution contracts
retain the rule-construction cycle; concrete substitution classes and the
public RuleBasedNumberFormat are still pending.

Common tests cover numeric boundaries, optional alternatives, callback order,
substitution offsets, divisor updates, delimiter composition, fraction
reciprocals, plural offset adjustments and exact-before-lenient prefix matching.
JVM oracle tests compare descriptors (including 200 deterministic random
radix/base pairs), power overflow, factory expansion and errors, special rules,
long/double formatting, plural fragments, parsing and partial state. ICU4J
substitutions are used only in JVM test adapters to isolate NFRule policy;
production has no ICU4J dependency. Full common substitution integration remains
unfinished. Corrected two test-adapter issues after the first run (delegated
Any.toString and target-specific Double string rendering).
Verification: jvmTest, jsNodeTest, macosArm64Test and
compileTestKotlinLinuxX64 passed, final run 2m 5s. Linux was compiled, not run.

### RBNF concrete substitutions

Ported NFSubstitution and all seven concrete implementations into
RbnfSubstitutionCore: same-value, multiplier, modulus (including predecessor
bypass), integral part, fractional part, absolute value and numerator. Uses the
ported DecimalQuantity_DualStorageBCD for fractional digit formatting/parsing
and the common DecimalFormat for numeric substitution patterns. Internal
RbnfSubstitutionOwner supplies graph lookup, default rule set, decimal formatter
and current rounding mode. Public RuleBasedNumberFormat initialization and
facade wiring remain unfinished; the default collation-backed lenient scanner
is also still pending.

Preserved source long-to-double precision loss in multiplier transforms, the
signed 2^53 formatting branch, infinity dispatch, denominator selection effects,
zero-padding parse position behavior, constructor/setter failure state,
asymmetric substitution equality and NaN-denominator equality. JS host helpers
preserve virtual Number int/long conversions and primitive saturation/truncation.
The source's unbounded zero-padding loops are not silently changed; tests avoid
inputs that do not terminate in ICU4J.

Common tests build real common-only graphs through rule sets, rules and concrete
substitutions. They cover English spellout, fractions, numeric patterns, forced
predecessor rules, digit spacing, leading zeros, parsing, numeric boundaries and
virtual conversions. The fixture repeats the outer formatter's whitespace
preparation before passing text to NFRuleSet; its first version omitted this
preparation and the initial graph tests correctly failed.
JVM differential tests cover every concrete substitution's transforms,
composition, bounds, factory errors, formatting and parsing; numerator padding;
current rounding modes; failed divisor updates; and entire graphs with compound
rules and deterministic random values. These tests use actual common
substitutions, without the ICU substitution adapters used by the earlier NFRule
isolation tests. Production has no ICU4J dependency.
Verification: jvmTest, jsNodeTest, macosArm64Test and
compileTestKotlinLinuxX64 passed, final run 2m 46s (Linux compiled, not run).

### RBNF formatter graph owner

Ported RuleBasedNumberFormat's graph initialization and execution state into
internal RbnfFormatterCore. The owner now prepares whitespace, extracts special
sections, registers all rule sets before parsing, selects the provisional
preferred/default set, parses rules, and applies localized public-name ordering
at the source point. Preserved duplicate-name lookup, preferred-name condition
precedence, public/@noparse/private visibility, display-name fallback and
ownership, lazy symbols/DecimalFormat/special rules, symbol propagation,
rounding, Long.MIN_VALUE numeric fallback and whole-formatter parsing.

Shallow copies share the graph and cached objects: substitutions retain their
original owner, including the source clone's symbol-selection behavior. The
outer rounding condition can use a virtual facade query while setScale uses
the stored mode, as in ICU4J. Postprocessor creation/init failures disable
further discovery; processing runs outside that catch. The common Chinese
postprocessor is connected to real rule sets. Scanner discovery preserves
one-shot/lazy behavior and forwards exact special rules to an injected provider.
The internal constructor requires a discovery factory: this does not supply or
claim a completed default collation-backed provider. Public NumberFormat facade
wiring, capitalization/context, serialization and JVM discovery of custom
postprocessor classes remain pending.

Common tests cover initialization/default order, localization copies and
fallback, visibility, parse/error positions, lazy provider discovery,
postprocessor state/copy/failure policy, Chinese cleanup, shallow symbol
selection, rounding query order and an embedded English spellout graph.
JVM oracle tests compare construction, full graph text, public/default names,
locale metadata and basic long/double formatting for every one of the 111
embedded RBNF locales and all four styles. Additional tests compare every public
named set for root, English, French, Russian, Arabic and both Chinese scripts,
including root numbering systems, Chinese postprocessing and parsing. They also
compare malformed descriptions/localizations, default changes, null/bounds
failures, display-name lookup, full parsing, rounding modes/fraction digits,
provider callbacks and clone behavior with ICU4J. The first test runs exposed
only two test expectations (string length and Unit/null normalization), which
were corrected before the final checks.
Verification: jvmTest, jsNodeTest, macosArm64Test and
compileTestKotlinLinuxX64 passed, final run 2m 27s. Linux compiled, not executed.

### RBNF display-context capitalization

Ported RuleBasedNumberFormat.setContext's initialization policy and
adjustForContext into RbnfCapitalizationCore, connected to RbnfFormatterCore and
its shallow copy. NumberFormat's parent context update precedes resource and
iterator initialization. Context flags load only on the first UI-list/menu or
standalone request; short/missing vectors retain defaults. Bundle lookup stays
outside the missing-key catch. A successful flag load remains published if
sentence-iterator creation fails, and later calls retry only that iterator.

Adjustment reads the facade's virtual context first, gates on GC=Ll for the
first Unicode code point, and uses the already-ported CaseMap title machinery
with an explicit sentence iterator, no lowercasing and no break adjustment.
The first-code-point gate intentionally leaves strings beginning with uppercase,
titlecase, spaces, digits, modifier letters or unpaired surrogates unchanged.
Iterator creation has the source lazy fallback used by deserialization/virtual
contexts. Copies preserve primitive flags and share the existing iterator,
including its text and position; no additional synchronization was introduced.

Common tests cover flags/vectors, initialization and failure order, retries,
parent/null context behavior, virtual context reads, supplementary characters,
locale-sensitive titlecase, unchanged text, shallow iterator ownership and
formatter-owner integration. JVM oracle tests compare every DisplayContext and
multiple texts for all 111 embedded RBNF locales, including internal flags,
iterator presence/position and unchanged-string identity. They also compare
virtual context overrides, clones and actual spellout output for nine locales.
The existing CaseMap implementation is reused; UCharacter's missing string
convenience overloads were not added as part of this state port.
Verification: jvmTest, jsNodeTest, macosArm64Test and
compileTestKotlinLinuxX64 passed, final run 2m 32s (Linux compiled, not executed).
The public RBNF facade/NumberFormat factory connection, serialization and default
collation-backed lenient scanner remain unfinished.

### Public RuleBasedNumberFormat facade and JVM streams

Added public common RuleBasedNumberFormat constructors for descriptions,
localizations, ULocale/style and default-format locale; rule-set/name APIs;
Long/Double, standard BigInteger/BigDecimal and ICU BigDecimal dispatch;
parsing, contexts, symbols, rounding, lenient mode/providers, equality/hash,
text reconstruction and cloning. Empty buffers and named formatting apply
capitalization; existing buffer contents bypass it. Large decimals route to
the cached DecimalFormat; in-range decimals preserve the source scale-based
long/double virtual dispatch. JVM/JS retain generic host cloning; Native uses
the existing typed-copy convention. JVM Locale conveniences are factory
functions, not additional Java constructor bytecode; full Java callable
constructor/typed-overload parity remains incremental.

Bound public virtual rounding/provider/lenient queries and provider publication
back to the facade, while private mode checks retain their source semantics.
Display-name fallback invokes the public getRuleSetNames override, including
in-place mutation of an override's returned array. New oracle cases reproduced
the initial virtual-getter bypass and verify the corrected paths. Added the
source package-level RBNFPostProcessor protocol and JVM class-name discovery;
init receives the invoked public facade, including clones. A preexisting
processor is shared by clones, while an uninitialized clone initializes its own
processor with itself. Init/discovery exceptions disable later lookup; process
exceptions and constructor Errors preserve their source propagation/state.
The internal engine protocol is named RbnfCorePostProcessor to avoid a
case-insensitive filesystem collision with RBNFPostProcessor.

JVM serialization has the source UID, eight virtual persistent-field descriptors
and custom UTF-description/locale/raw-mode order without default subclass data.
Read restores the source locale fallback, missing-mode zero, unvalidated raw
mode and temporary graph owner. Source quirks are retained: localized-name
configuration, lenient mode and special postprocessor rules are reconstructed
as in readObject rather than round-tripped as ordinary fields; substitutions
retain the temporary formatter's mode/settings. Tests compare descriptors,
write/read hooks, malformed/legacy inputs, partial state and real independent
ICU4J/icu4k roundtrips. They do not claim automatic cross-package stream loading.

The optional default provider lives in ICU4J's collate source module. Discovery
uses the relocated class name on JVM; until that module is ported, missing
provider discovery is suppressed exactly at the core policy boundary. Native
and JS likewise have no default collation scanner yet. This is unfinished
collation work, not a replacement scanner or a production ICU4J dependency.

Verification includes numeric overload/buffer/field/context matrices against
ICU4J, subclass dispatch, public Java Format bridges, getters, provider setter
publication, postprocessor initialization/copy/error behavior and stream tests.
The full run exposed an older BigDecimal oracle's dependence on HotSpot keeping
implicit /0 messages. A standalone Java probe produced 294,347 empty messages
and traces after warmup versus none with fast-throw disabled. Commit 0f83569
normalizes only a known zero-divisor scaled JDK division with both absent
message and trace, with a warmup regression; production arithmetic is unchanged.
Final jvmTest, jsNodeTest, macosArm64Test and compileTestKotlinLinuxX64 passed,
4m 4s (Linux compiled, not executed). NumberFormat global factory wiring and
full collation/default lenient scanning remain pending, alongside other ICU
APIs and resource coverage in the full port.

### Public NumberFormat factory wiring

The common companion now exposes default/locale/style getInstance, number,
integer, currency, percent and scientific factories, available ULocales and
factory registration/removal. The previously ported locale-service engine is
shared across these calls; initialization is protected by a recursive portable
lock and availability/removal do not initialize it. Null registry arguments and
invalid style diagnostics follow NumberFormat.java. Returned instances retain
the source service clone, currency adjustment and locale metadata policy.
Algorithmic numbering systems construct the common RuleBasedNumberFormat and
select the named default rule set before the data factory publishes metadata.
No production ICU4J dependency is introduced.

JVM java.util.Locale overloads and Java locale availability use Kotlin companion
extensions, consistently with BreakIterator; they are not additional Java
static overloads on NumberFormat. Common tests exercise numeric/roman factories,
clone independence, custom registration, visibility, null-result fallback and
removal. JVM oracle tests compare all ten styles across six locale/numbering
configurations, output, formatter class, currency and actual locale with ICU4J.
The earlier statement that global factory wiring is pending is superseded by
this stage. Full collation/default lenient scanning and remaining ICU APIs and
resource coverage are still unfinished.

Verification: jvmTest, jsNodeTest and macosArm64Test passed; Linux x64 test
sources compiled (not executed on macOS). Full run: 2m 44s. An initial common
test incorrectly expected 1234.5 to round to 1235; its expectation was corrected
to source half-even 1234, with no production rounding change.

### Public PluralFormat facade

Common PluralFormat now exposes all source locale/rules/type/pattern constructor
combinations, the internal constructor with a supplied NumberFormat, plural
message formatting, pattern application, deprecated locale reset, number-format
replacement, equality, diagnostics and unsupported parse methods. Locale
metadata remains unset as in the source constructor. The existing common
PluralFormatCore continues to supply execution and RBNF parsing policy.
Pattern parse errors clear the active pattern; locale reset restores cardinal
rules and the locale's numeric factory, while a caller-supplied number formatter
is retained by reference. Null output buffers are checked after number
validation and formatting, preserving source evaluation order.

Shallow clone semantics retain shared MessagePattern and NumberFormat objects
and a selector bound to the original owner's rules. Consequently resetting a
locale clears a clone's shared parsed pattern, and reapplying a pattern can make
the clone select using the original's new rules. Source's legacy parsedValues
field remains null, including its ordinary hashCode NPE; this is not replaced
with a newly invented hash. Native copying follows the existing typed facade
clone convention; arbitrary Native subclass state requires a typed override.

JVM serialization preserves UID 1 and the five source persistent fields. On
read the transient execution state/selector is rebuilt, legacy parsedValues is
cleared, and a non-null pattern is reparsed through virtual applyPattern.
Real independent ICU4J/icu4k roundtrips and field descriptors are compared;
automatic cross-package stream loading is not claimed. JVM Locale constructor
forms are Kotlin factory functions, not added Java-callable constructors.

Common tests cover offset/exact selectors, ordinal/custom rules, mutable number
format ownership, unsupported parsing, invalid patterns and clone aliases. JVM
oracle tests compare six locales and both plural types across quoted/nested
patterns, finite/special numbers, large integer/decimal objects, Java Format
bridges, failure order, equality, serialization and clone behavior.

Verification: jvmTest, jsNodeTest, macosArm64Test and
compileTestKotlinLinuxX64 passed (Linux compiled, not executed on macOS).
Final run: 1m 37s; unchanged target tasks were up-to-date. Initial verification
found ambiguous null literals in JVM tests and an invalid new oracle pattern
rejected by ICU4J itself; the test calls were typed explicitly and the pattern
replaced with an existing verified nested/quoted case. Production code did not
need changes after its initial implementation.

### UCharacter locale-sensitive string casing

UCharacter now exposes full-string lower/upper/title operations, general-default
and ULocale overloads, raw title options and TITLECASE_NO_LOWERCASE /
TITLECASE_NO_BREAK_ADJUSTMENT constants. The common CaseMap engine supplies the
algorithms; no platform casing implementation or production ICU4J dependency is
used. Internal Title construction accepts the raw ICU option bits without the
fluent option builder's conflict checks, matching the source UCharacter entry
point. Iterator option validation, word/sentence/whole-string selection,
setText, locale resolution and casing retain source order. JVM Locale overloads
are Kotlin companion extensions, consistent with the current API bridge
convention; Java static-overload parity remains separate unfinished API work.

BreakIterator's String and CharSequence setText overloads now accept nullable
arguments so custom overrides can observe them as in Java. Default overloads
still reject null. Both overloads needed matching nullability to retain
unambiguous String dispatch; Kotlin subclasses overriding these methods must
use nullable parameters. UCharacter calls the virtual String overload directly;
CaseMap.Title uses the CharSequence overload. The latter also no longer rejects
null before a custom setText callback. Iterator setup failures propagate raw,
while the already ported mapping engine retains its own exception policy.
Case locale is resolved after setText, so a callback changing the general default
locale affects casing when no explicit locale was supplied.

Common tests cover Turkish/Lithuanian/Greek/German/Dutch casing, raw options,
invalid combinations, nullable callback dispatch, iterator ownership and
mutable general defaults; locale-category defaults are restored independently.
JVM oracle tests cover eight locales, short/long text, supplementary/unpaired
surrogates, raw/reserved option bits, explicit iterators, null validation,
Locale overloads and a general default distinct from FORMAT. Custom iterators
verify String versus CharSequence dispatch, callback exception identity and
locale changes inside setText against ICU4J.

Verification: jvmTest, jsNodeTest, macosArm64Test and
compileTestKotlinLinuxX64 passed after the nullable dispatch corrections.
Linux test sources compiled, not executed on macOS. Earlier full runs also
passed the new casing matrix; the only intermediate compiler failure was the
String?/CharSequence overload ambiguity resolved by matching nullability.

### Collation foundation: CE and CE32 primitives

The ICU4J collate module has no dependency-free public scanner shortcut:
RbnfScannerProviderImpl obtains primary weights from a real RuleBasedCollator,
including canonical decomposition and optional appended tailoring rules. The
next stages therefore implement the collation data/iterator pipeline rather
than substituting case folding or a JVM ICU4J production adapter.

The first production layer ports Collation.java in full to common impl.coll:
CE/CE32 constants, tags, compact primary/secondary and Latin expansion layouts,
index/length/digit extraction, all 33 static helper overloads, primary weight
increment/decrement and offset/implicit code-point weights. Arithmetic retains
Java signed Int overflow, shifts, widening and signed remainder/division,
including out-of-domain raw values. JVM assertions follow the class's status at
initialization; JS and Native use the existing assertion-disabled convention.
This layer introduces no collation resources or formatter facade yet.

Common tests exercise CE layouts, all special tags, compression carry/borrow,
offset records and ordered implicit primary weights across the Unicode range.
A JVM oracle enumerates every source Int/Long constant and every helper method,
checks signature coverage, and compares boundary/random inputs via reflection,
including malformed values and assertions. CollationData, settings, runtime
iterators, resource loading, public collators and the default RBNF scanner are
still pending. This foundation does not claim end-to-end collation support.

Verification: all JVM, JS/Node and macOS arm64 tests passed, and Linux x64 test
sources compiled (not executed on macOS), 4m 10s. The differential test covers
all 33 source helper overloads and every Int/Long constant. It also verifies
that changing class-loader assertion policy after initialization leaves the
original assertion behavior unchanged. The initial oracle's incorrect expected
method count (36 instead of 33) was corrected before the final successful run.

### UnicodeSet signed invalid-code-point diagnostics

The CollationData root oracle exposed an older UnicodeSet.validate mismatch:
negative code points were formatted as unsigned Int hex, losing ICU4J's minus
sign and signed minimum-width padding. Validation now delegates to the already
ported Utility.hex(c.toLong(), 6), matching the upstream call. No accepted code
point or set operation changes. Common and JVM oracle regressions compare
contains/add/remove/complement diagnostics for negative values including
Int.MIN_VALUE and values above U+10FFFF. The full JVM/JS/macOS test run and Linux
test compilation passed together with the collation data stage, 3m 54s.

### Collation UVector32

The collate module's primitive vector is now common: initial capacity 32,
fourfold growth through capacity 0xFFFF and doubling thereafter, overlapping
insertion, direct backing-array access, capacity-based indexing and logical
clear without erasing storage. Insertion grows capacity before validating its
arraycopy arguments, so a failed insertion can still publish a new backing
array; this source behavior is retained. Explicit common bounds checks preserve
failures on JS as well as JVM/Native. Common tests cover aliasing, clear, invalid
insertion after growth and the 131072-to-262144 capacity boundary. A seeded JVM
oracle compares mutable backing contents and state through 3000 mixed valid and
invalid operations and a large growth sequence. All target checks passed in the
3m 54s collation data verification run (Linux compiled, not run on macOS).

### CollationData container and script reordering

CollationData.java is now ported to common impl.coll, including its full instance
field layout, nullable construction state, fresh Jamo table and numeric-primary
default. Lookups implement direct/supplementary CE32 access, numeric digit and
unsafe-backward policy, compression flags, context words, one-step indirect
CE32 resolution, offset records, single-CE extraction and FCD via the existing
common normalizer. Single-CE fallback selects the base exactly once and resolves
subsequent table references on that selected data owner; unsupported/malformed
tags preserve the upstream explicit diagnostics, including unsigned printf-style
hex in these messages (distinct from UnicodeSet's signed Utility.hex).

Script groups preserve first/last boundaries, first matching script, aliases
and shared empty-array identity. The complete reorder-range algorithm retains
special groups, reserved ranges, the before-Latin optimization and retry with
Latin moved, OTHERS processing from the high end, duplicate/equivalent/default
errors, partial-primary exhaustion and packed limit/offset pairs. The caller's
range vector is cleared before validating the reorder array. Source assertions
are captured per class at initialization on JVM and disabled on JS/Native as in
the existing portability convention. Common bounds checks cover array/string
accesses whose JVM implicit checks would otherwise be missing on JS. Until the
public Collator facade is ported, its FIRST/DEFAULT codes are exact internal
constants, without introducing a substitute Collator API.

Common tests use a synthetic serialized immutable trie and standalone script
fixtures; the immutable fixture is not a production Trie2Writable replacement.
They cover tags, base table ownership, context bounds, digit/safety short-circuit
order, compression/FCD, group aliases and ICU4J-generated reorder vectors.
The JVM oracle compares all instance field names, defaults and synthetic tags,
and imports ICU4J's real root structural data into the common implementation
for 4000 seeded code-point samples plus boundaries, group boundaries/aliases,
1000 mixed reorders and 100 complete canonical-script permutations. Reserved
range retry and exhaustion have dedicated fixtures. Normalizer and trie objects
are converted to the common implementations; ICU4J is still test-only.

Verification: jvmTest, jsNodeTest and macosArm64Test passed, and Linux x64 test
sources compiled (not executed on macOS), 3m 54s. Initial root safety verification
exposed the UnicodeSet signed diagnostic mismatch fixed separately above.
Collation resource loading/embedding, runtime CE iterators, settings and public
collators, and the standard RBNF lenient scanner remain unfinished. Importing
root fixtures in a JVM oracle does not prove embedded collation resource
availability on any target; no end-to-end collation claim is made at this stage.

### CollationRootElements weights and tailoring boundaries

The complete CollationRootElements class now runs in common code: metadata and
boundary getters, first/last CE queries around primary boundaries, modified
binary search through interleaved secondary/tertiary deltas, two-/three-byte
primary ranges and secondary/tertiary predecessor/successor lookup. Signed
widening and the distinct signed-versus-unsigned secondary packing in the two
tertiary directions follow the source. The constructor retains the supplied
nullable array without copying or eager validation, and getters reread mutable
header values. Zero-primary boundary queries still return zero even for a null
table. Common explicit array checks preserve JVM-style bounds failures on JS.

Assertions capture this class's JVM status at initialization and evaluate their
conditions lazily. On JS/Native they use the existing disabled-assertion
convention; the checks do not accidentally read arrays when disabled. An
isolated JVM class-loader test compares this behavior with ICU4J, including a
later assertion-policy change. Synthetic common tests cover explicit weights
below common/common, implied common weights, deltas, both range widths,
secondary/tertiary limits, aliasing, null state and disabled precondition reads.

The JVM oracle uses the real 7122-entry ICU4J root table, checks every source
constant and all 18 public/package entry points, and compares assigned primary
entries, range interiors and secondary/tertiary tuples, including successors of
returned lower boundaries. A second matrix exercises synthetic/malformed inputs
and failure order. The initial oracle incorrectly treated the FE040200
unassigned boundary marker as an assigned-primary input; ICU4J itself rejected
it. The valid-input enumeration was corrected and explicit rejection remains
compared, with no production code change. The shared source table is verified
unchanged after the complete pass.

Verification: jvmTest, jsNodeTest, macosArm64Test and
compileTestKotlinLinuxX64 passed, final run 1m 45s (unchanged JS/Native/Linux
tasks were up-to-date after their initial successful checks). Linux test sources
were compiled, not run on macOS. This is root weight/tailoring-table support,
not resource loading, a runtime CE iterator or public Collator support. The real
root table is still supplied only by the JVM test oracle; embedded collation
resources and the remaining runtime/tailoring pipeline are pending.

### CollationFCD fast-path tables

The next runtime-iterator dependency is CollationFCD, now fully ported to common
code: BMP leading/trailing combining-class flags, conservative supplementary
leading-class detection and both Tibetan-composite-vowel checks. The source's
exact static bitmaps are retained, including deliberate surrogate-block and
Tibetan false positives. These are not recomputed from normalization properties
or replaced with a more precise detector. Negative sentinels, oversized raw
Int behavior and UTF16 lead-surrogate conversion preserve the source policy.
JVM assertions are captured at class initialization; JS/Native retain the
existing disabled convention and explicit bounds checks.

All 4293 byte/int entries are extracted from the pinned ICU4J 78.3 source file
(SHA-256 b83a2cd29b7750ac9658b78408c3cffdaa163ea729e25d6bb9270478dd1c07e4).
The checked-in data/collation-fcd.json records the source path and commit;
scripts/generate-collation-fcd.py verifies its checksum and ICU manifest revision
and deterministically emits CollationFCDData.kt. CI checks that the generated
file is current. Reproduce with:

```sh
python3 scripts/generate-collation-fcd.py --check
```

Common tests compare ICU4J-generated counts and ordered fingerprints over every
BMP code unit and all Unicode code points, plus sentinels, bounds and concrete
false-positive examples. JVM tests directly compare each bitmap entry, both
BMP functions at every BMP value, both code-point functions through U+10FFFF,
and every 16-bit FCD value. Additional raw-Int and isolated class-loader tests
cover assertions disabled at initialization and later policy changes. This
component prepares contraction/FCD iteration; CollationIterator and its UTF-16 /
FCD variants, collation resources/loaders, public collators and the default RBNF
scanner are still pending.

Verification: generator --check and the full jvmTest, jsNodeTest,
macosArm64Test, compileTestKotlinLinuxX64 command passed on the first run,
5m 6s. Linux test sources compiled, not executed on macOS. No production
CollationIterator implementation or collation resource availability is claimed
by this standalone prerequisite stage.

## Collation CE iteration and UTF-16 cursor

Ported the complete ICU4J CollationIterator and UTF16CollationIterator runtime
algorithms to common Kotlin. The CE decoder handles every source CE32 tag,
expansions, prefixes, longest-match contractions, discontiguous matching and
nested skipped-mark replay, Hangul/Jamo, supplementary and unpaired surrogates,
offset primaries and implicit weights. Numeric iteration preserves the dense
encoding boundaries, leading-zero handling and 254-digit segments. Reverse
iteration replays unsafe segments and preserves expansion offset vectors.

The cursor and CE buffer retain source mutation order, partial-constructor
behavior, backing-array aliases and growth, repeated end markers, equality and
assertion policy. In particular, callbacks and offset-data lookup can fail
before a missing CE buffer is accessed. JVM assertion checks use class literals,
including the nested skipped-state class; JS and Native follow the existing
assertions-disabled convention.

Common tests exercise synthetic trie/context fixtures, numeric boundaries,
large buffers, forward/reverse offsets, nested discontiguous contractions and
failure state. JVM differential tests import ICU4J root and locale tailoring
data as test fixtures and compare CE values, cursor offsets, full backing
buffers and reverse offset vectors against ICU4J 78.3. Tests include seeded
mixed-script text, long numeric runs, malformed UTF-16, all surrogate lookup
modes and partial initialization. ICU4J remains a test-only dependency.

This adds raw UTF-16 iteration. FCD-normalizing iterator variants, embedded
collation resources and loaders, public collators, tailoring compilation and the
default RBNF scanner remain pending. Comparing imported JVM oracle data does not
establish resource availability on JS or Native.

Verification: jvmTest, jsNodeTest, macosArm64Test and
compileTestKotlinLinuxX64 passed after the final regression additions. Linux
tests were compiled, not executed on macOS. The preceding full run caught a
test-only access to an upstream package-private field; fixture preparation now
uses the existing data converter.

## Incremental FCD UTF-16 collation iterator

Ported FCDUTF16CollationIterator's forward/backward FCD segment checks,
normalization, direction switching and raw-offset mapping. Canonically ordered
raw segments remain intact; only failing segments and Tibetan composite vowels
are decomposed into a reusable buffer. Code-point movement and CE decoding use
the same incremental boundaries, including supplementary and unpaired UTF-16.
Reset, partial construction and setText retain source mutation/failure order.

The ICU4J 78.3 equals implementation calls equals virtually on a base-typed
receiver, recursively dispatching to itself whenever the argument is a
CollationIterator. This was verified in the pinned source and JAR bytecode.
The port retains that behavior; a JVM oracle regression checks StackOverflowError
for self-comparison and the non-iterator early return. Hash assertions remain
specific to this source class.

Common tests check normalized-segment offsets, direction changes, Tibetan
normalization, composed FCD text, surrogate handling and CE equivalence with
ordered input. JVM oracle tests compare root and traditional-Spanish CE streams,
reverse offset vectors and buffers, plus every segment/cursor field and raw vs
normalized identity after seeded direction changes and resets. Oracle data is
still test-only; embedded collation resources, loaders, public collators,
tailoring compilation, UCharacterIterator variants and the default RBNF scanner
remain pending.

Verification: the full jvmTest, jsNodeTest, macosArm64Test and
compileTestKotlinLinuxX64 command passed before and after removing boxed cursor
results from the hot forward path. Linux tests compiled, not executed on macOS.

## Collation over UCharacterIterator

Ported IterCollationIterator and FCDIterCollationIterator against ICU4J 78.3.
The raw variant delegates code-point movement to the caller-owned iterator and
retains CE decoding's single-code-unit reads and trail-surrogate rollback.
The FCD variant retains the five source states: forward/backward checking,
raw FCD segment consumption and normalized consumption with the underlying
iterator parked at either raw boundary. It gathers failing segments in both
directions, normalizes into a reusable buffer and maps normalized positions to
raw offsets without replacing the user's iterator.

Backward segment reversal explicitly follows Java StringBuilder's surrogate
pair repair on every platform. The missing Normalizer2Impl.decompose(text,
builder) convenience overload was also ported with its destination identity
and argument evaluation order. Source null-cursor failure timing, reset ordering,
inherited iterator equality and class-specific assertions are retained.

Common tests cover shared cursor ownership, code units/code points, CE streams,
normalization direction changes and lazy failures. JVM oracle tests compare
root-data forward/reverse CEs, offset vectors, full buffers, every FCD state and
both internal and external cursor positions through seeded mixed-script inputs,
resets, malformed UTF-16, Tibetan vowels and long numeric runs. Collation data
used by JVM differential tests remains test-only. Embedded collation resources,
loaders, public collators, tailoring compilation and the default RBNF scanner
remain pending.

The first full run also exposed two pre-existing malformed-string test fixtures
being lowered to question marks in the emitted Kotlin/JS program. These inputs
now build surrogate code units at runtime, keeping explicit malformed UTF-16
assertions meaningful on every target. JVM companion-access compilation errors
were resolved by using the equivalent common UTF16 surrogate predicates.

The direction-change oracle also exercises source assertions: ICU4J itself can
reach nextCodePoint's nonnegative-character assertion after some cursor
histories. The test compares returned values or exception categories and then
all cursor/segment fields, including after failures; it does not assume every
seeded operation succeeds. The focused JVM oracle passed after this correction.

Verification: the final full jvmTest, jsNodeTest, macosArm64Test and
compileTestKotlinLinuxX64 command passed (1m 42s, with unchanged target tasks
up-to-date). Linux tests were compiled, not executed on macOS.

## Collation settings and shared ownership

Ported CollationSettings' complete option bit fields, default setters, strength,
case/tertiary decisions, equality/hash rules and script reordering. Reordering
retains the source lead-byte permutation, split-byte ranges and signed offsets,
alias-or-regenerate behavior, array sharing and partial mutation order.

SharedObject provides atomic reference counts and copy-on-write holders.
JVM and JS use platform shallow cloning, preserving extra subclass fields;
Native follows this port's existing typed-copy convention. CollationSettings
is ICU4J's only SharedObject subclass and supplies that typed copy, preserving
reordering array aliases while cloning mutable fast-Latin primaries. Clones
start with a fresh zero reference count. The JVM host clone base deliberately
does not add Serializable, which ICU4J SharedObject does not implement.

Explicit holder clear remains deterministic. Platform cleanup replaces Java's
holder finalizer: a JVM finalization token, JS FinalizationRegistry, and Native
createCleaner over a separate reference state. The Native action captures no
holder; collation settings do not point back to their holders. As elsewhere in
the port, Native arbitrary new subclasses must provide a typed clone; generic
runtime cloning is available on JVM/JS. GC cleanup timing is not deterministic.

The Native cleanup action follows the [createCleaner contract](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.native.ref/create-cleaner.html):
it captures nothing and receives the separate reference state as its resource.
The common tests exercise option semantics, split-byte thresholds, array aliases,
clone isolation and ownership counts. JVM differential tests cover every 16-bit
option combination, seeded mutations, real-root script permutations and alias
round trips, malformed serialized metadata, clone failures and controlled
finalization-token release. A multithreaded JVM check verifies atomic counts.

The initial oracle failures were test-adapter issues: reflection returns null
for void while Kotlin returns Unit, and a rejected reordering leaves the prior
codes in place. The corrected oracle round-trips the actual retained codes and
checks both aliased tables and regeneration from codes. All focused JVM checks
then passed. Final verification: jvmTest, jsNodeTest, macosArm64Test and
compileTestKotlinLinuxX64 passed together in 4m 8s. Linux tests compiled, not
executed on macOS. Public collators, collation resources/loaders, fast-Latin
comparison, tailoring compilation and the default RBNF scanner remain pending.

## Collation comparison through quaternary

Ported CollationCompare.compareUpToQuaternary in full. It fetches primary CEs,
shifts variable weights and clears following primary ignorables in the stored
buffer, then compares secondary, optional case, tertiary and quaternary levels.
French secondary ordering is segmented by merge separators. Case-level filtering,
uppercase-first tertiary remapping, quaternary fast exits and script reordering
retain the source order of reads and mutations. Assertions use this class's
initial JVM assertion policy. Nullable inputs fail when first dereferenced;
there is no text-equality or iterator-identity shortcut.

Common tests use explicit CE expansions to isolate each weight level, reversed
secondary segments, case ordering, ignorable weights, shifted-buffer changes and
script permutation. JVM oracle tests compare root and locale tailoring data for
all strengths through IDENTICAL, case options, backward secondaries, numeric and
shifted handling, script reordering, normalization, malformed UTF-16 and merge
separators. Synthetic streams cover tertiary-only and quaternary weights. The
oracle also compares raw CE buffers, consumed CE indexes and cursor offsets,
including early returns and failures.

As in ICU4J, this method stops at quaternary even when the strength is IDENTICAL;
the public collator's final identical-level tie-breaker is separate. Root and
locale data in JVM oracle tests remain test-only. Public collators, embedded
collation resources/loaders, fast-Latin comparison, tailoring compilation and
the default RBNF scanner remain pending.

Verification: jvmTest, jsNodeTest, macosArm64Test and
compileTestKotlinLinuxX64 passed on the first full run (3m 19s). Linux tests
compiled, not executed on macOS. The differential matrix contains 60,000 real
root/tailoring comparisons and 9,900 synthetic CE comparisons, plus nullable,
failing-position and aliased-iterator cases.

## Fast Latin runtime

Ported the complete CollationFastLatin format constants, character indexing,
getOptions primary-cache preparation, UTF-16 comparison and mini-CE helpers.
The fast path retains source handling of long/short primaries, two-CE expansions,
single-suffix contractions, implied high secondary CEs, case and tertiary bits,
shifted quaternaries, merge/end sentinels and unsupported/numeric bailouts.
Backward-secondary differences request the general comparison, as in ICU4J.
The comparison uses local scalar indexes/pairs without allocating cursor holders.

Option preparation preserves early unsupported returns, header/array assertion
order, partial output writes and digit cache disabling when numeric or script
reordering requires it. Signed Int mini-CE pairs and the negative Long contraction
consumption marker follow the Java implementation rather than unsigned rewrites.
Custom CharSequence reads use the existing callback-preserving code-unit helper.

Common tests exercise explicit contraction/expansion mappings and each comparison
level. JVM differential tests compare constants and all code-unit indexes,
every single 16-bit mini CE, seeded signed pairs, real root/tailoring tables,
reordering, supported/unsupported strings and start indexes. Recording sequences
check length/character callbacks and lazy errors. Oracle collation tables remain
test-only; the table builder, embedded resources/loaders, public collators,
tailoring compilation and the default RBNF scanner remain pending.

Verification: the implementation compiled on JVM first; jvmTest, jsNodeTest,
macosArm64Test and compileTestKotlinLinuxX64 then passed on the first full run
(3m 1s). Linux tests compiled, not executed on macOS. No production ICU4J
runtime dependency or embedded fast-Latin collation data is introduced.

## Fast Latin table builder and UVector64

Ported CollationFastLatinBuilder and its primitive UVector64 dependency. The
one-shot builder collects CE mappings, filters supported primary/secondary/
tertiary groups, deduplicates CEs in unsigned order, assigns mini weights and
encodes expansions and single-suffix contraction lists. It preserves the retry
that moves digits to long primaries after short-primary overflow, all overflow
bailouts and the source header/table split. Longer contractions sharing a first
suffix character suppress that character's fast mapping, as in ICU4J.

The port retains source quirks and failure state: append(0) writes the character
'0' into provisional slots; the second-CE below-common-tertiary check reads the
first CE again; missing metadata or exceptions can leave output that prohibits
reuse, with header/table accessors then failing at the same point. No eager
validation replaces these operations. UVector64 retains capacity-based indexing,
backing-array exposure, stale contents on clear, 4x/2x growth and growth before
an invalid insertion fails.

Common tests use deterministic CE/script fixtures for exact output, runtime
integration, short-primary retry/overflow and long-vector growth. JVM tests
rebuild root and locale tailoring tables and compare every output unit and
internal scalar, CE array and vector buffer with ICU4J 78.3. Additional synthetic
fixtures cover overflow and context tags, missing/null metadata and partial
failures; unsigned ordering/search and vector operations are checked separately.
The rebuilt oracle data remains test-only. Embedded collation resources/loaders,
public collators, tailoring compilation and the default RBNF scanner remain
pending.

Verification: the implementation compiled on JVM; after correcting an Int-to-Char
literal in a test fixture, jvmTest, jsNodeTest, macosArm64Test and
compileTestKotlinLinuxX64 passed together in 2m 44s. Linux tests compiled, not
executed on macOS. Oracle rebuilds matched eight real root/tailoring data sets,
six synthetic capacity cases and additional context/failure fixtures.

## Collation data reader, tailoring container and embedded root

Ported CollationDataReader (UCol format 5), CollationTailoring and CollationRoot
into commonMain. The reader loads mappings, CE arrays, contexts, unsafe-backward
ranges, fast Latin tables, script groups, compressible bytes and settings; it
preserves base-data aliases, copy-on-write settings, source assignment order and
version packing. Tailoring rules remain lazy when supplied as a resource.
CollationRoot caches its loaded tailoring or its initialization failure.

Embedded the unmodified 597,168-byte coll/ucadata.icu from the pinned ICU4J 78.3
collate module. Its individual manifest entry records the collate source path;
the other resources retain their core-module provenance. The generator verifies
its size and SHA-256. There are now 219 embedded resources.

Common tests exercise real root loading, aliases, frozen unsafe-backward data,
settings-only tailoring, copy-on-write and invalid indexes. JVM oracle tests
compare all 1,114,112 code-point CE32 mappings, all loaded data arrays, settings,
version arithmetic and the exact final cursor against ICU4J. The source leaves
12 trailing resource bytes unread; the port does too. Version tests cover 2,000
signed random version pairs and every representable UCA major/minor/milli
combination used by makeBaseVersion.

Public collators, locale collation resource loading and tailoring compilation
remain pending. This stage supplies real root data to the existing common
collation algorithms; it does not complete the ICU4J port.

Verification: generate-data.py --check passed for 219 resources. Final jvmTest,
jsNodeTest, macosArm64Test and compileTestKotlinLinuxX64 passed. Linux tests were
compiled, not executed on macOS. The oracle uses reflection for ICU4J's
package-private version helpers; the first comparison was corrected to match
the source's trailing-byte cursor rather than requiring complete consumption.

## Tailoring weight allocation

Ported CollationWeights, the complete range allocator used by tailoring
compilation. It supports compressible/non-compressible primaries, secondaries
and six-bit tertiaries; merges intersecting/adjacent ranges, lengthens the
shortest ranges and splits ranges to satisfy requested capacity. Weight
iteration retains range mutations and the exhaustion sentinel. Initialization,
failed allocations and partially consumed/reused instances retain the source
state rather than clearing it eagerly. Signed Long comparisons, Int overflow
and byte-mask/shift operations follow the Java implementation.

Common tests check short primary sequences, exhaustion, invalid bounds, byte
constraints and lengths. JVM oracle tests compare allocation outcomes and all
private range fields over 2,176 deterministic and random cases across four
modes, followed by partial or full requested consumption. Another 8,000 cases
compare all static/private weight arithmetic helpers, including arbitrary Long
weights and Int byte operands. No ICU4J dependency is used by commonMain.

Verification: final jvmTest, jsNodeTest, macosArm64Test and
compileTestKotlinLinuxX64 passed together. Linux tests compiled, not executed on
macOS. The initial common fixture requested more capacity than its bounds
allowed; its bounds were corrected, with no production changes needed after
the first oracle comparison.

## BOCSU and mutable byte-array ownership

Ported BOCSU's identical-level encoder and the public ByteArrayWrapper utility.
The encoder preserves one-to-four-byte signed difference encoding, floor
quotient/remainder for negative differences, script-block/Unihan prediction,
U+FFFE merge resets, streaming previous-code-point state and exact buffer growth.
Code-point reads use the entire CharSequence, including a surrogate pair whose
trail lies beyond the requested run limit. A default wrapper with null storage
still fails on a nonempty BOCSU run; the encoder does not initialize it silently.

ByteArrayWrapper retains array adoption/exposure/release, logical size separate
from capacity, signed-byte hash and unsigned-byte comparison. The short-copy
loop retains partial writes and forward-overlap behavior; bulk copies retain
all-or-nothing bounds validation and overlap-safe array copying. Capacity growth
is exact, remains virtual and copies only the logical prefix. Invalid public
state is not normalized before operations. Negative allocation size retains the
JVM exception and uses IllegalArgumentException on JS/Native. Common BinaryBuffer
construction consumes limit bytes rather than remaining bytes; the JVM
ByteBuffer convenience is a Kotlin factory, matching existing host API bridges.

Common tests cover encoding, streaming, ordering, ownership and failure state.
JVM tests compare 20,091 difference cases (including full Int operands and
partially writable buffers), 1,535 run/capacity cases with malformed UTF-16 and
invalid limits, custom CharSequence/capacity callbacks and 4,000 wrapper mutation
steps. Every step compares the full backing buffer, not only logical contents.
Heap/direct ByteBuffer construction is checked at every initial cursor offset.

Verification: jvmTest, jsNodeTest, macosArm64Test and
compileTestKotlinLinuxX64 passed together in 3m 54s (Linux compiled, not executed
on macOS). A subsequent JVM-only boundary run also passed after expanding the
short-buffer test capacities to 0..3 bytes, exercising partial writes before
bounds failures. Production code and common tests were unchanged for that run.

## Public collation key containers

Ported CollationKey and RawCollationKey. CollationKey adopts the supplied bytes,
compares unsigned bytes up to the first terminator, computes/caches the source
UTF-16-pair hash, constructs level bounds and merges corresponding levels with
02 separators. Bound and merged keys have null source strings. Raw-key
construction transfers the backing array and clears the raw owner before the
terminator assertion, as in ICU4J. RawCollationKey's typed comparison normalizes
to -1/0/1; comparison through ByteArrayWrapper keeps its full byte difference.

The port retains lazy length/hash caches and their interaction with externally
mutated arrays, errors for unterminated/null data, exact explicit error messages,
and source assertion policy. toByteArray returns a copy but does not repair a
malformed key. There is no eager validation of caller-supplied key bytes.

Common tests cover ownership, copying, cached values, bounds, unequal-level
merging and comparison overload dispatch. JVM oracle tests use ICU4J keys from
seven locales and five strengths, checking all bound modes/level counts and all
pairs of 13 sample strings; 1,200 malformed/mutated-array histories check lazy
cache state after each operation, and 1,000 raw-key cases check transfer and
comparison behavior. Oracle and target keys are constructed identically from
arrays so their initial lazy-cache state matches.

These containers do not yet expose a common public collator or generate keys;
CollationKeys and the public collator implementation remain pending.

Verification: final jvmTest, jsNodeTest, macosArm64Test and
compileTestKotlinLinuxX64 passed together. Linux tests compiled, not executed on
macOS. The initial real-key oracle fixture was corrected to initialize the lazy
length caches through the same constructor; no production correction was
needed after the initial comparison.

## Sort-key generation through quaternary level

Ported the full CollationKeys encoder, SortKeyByteSink, LevelCallback and the
private SortKeyLevel buffers. Encoding includes primary compression and script
reordering; forward/backward secondary compression with merge-delimited reversal;
case nibbles; all three tertiary case modes; shifted variables and quaternary
compression. It preserves minimum-level masks, callback ordering, early return
on primary overflow without preflight, and the source's omission of a final
terminator byte. CollationDataReader now uses the actual SEC_COMMON_HIGH constant.

Sink operations preserve virtual resize/overflow callbacks, separate attempted
byte counts, mutation-before-failure ordering, fixed-capacity preflight and
buffer replacement. Level buffers preserve 40-byte initial storage, source
capacity growth, byte truncation, reverse writes and assertion behavior. The
private nested level's assertion status follows the enclosing CollationKeys
class, verified against the upstream bytecode.

Common tests verify exact bytes, CE-comparison ordering, level callbacks and
fixed-capacity counters. JVM oracle tests compare complete buffers, sink calls,
callback calls, iterator offsets and CE storage for 38,000 root/locale cases
across strengths, case settings, shifted/backward/numeric modes and reordering.
Another 4,140 synthetic cases cover rare tertiary/quaternary weights and primary
compression transitions. Callback/minimum-level tests include Java shift
boundaries; fixed-capacity tests exercise preflight and early termination.
There are also 1,000 sink mutation steps and 3,000 private-level operations.
The latter resets each short series to bound memory: the initial fixture
incorrectly called the doubling capacity method indefinitely and exhausted its
test heap. It was corrected without a production change.

Locale data used by oracle tests remains test-only; the common embedded root is
usable by this encoder. Public collators, locale collation loading and tailoring
compilation remain pending.

Verification: final jvmTest, jsNodeTest, macosArm64Test and
compileTestKotlinLinuxX64 passed together in 3m 29s. Linux tests compiled, not
executed on macOS. The test-only package-local Java callback adapter allows the
oracle to override ICU4J's package-private callback method; production encoding
and callbacks are implemented entirely in commonMain.

## Complete collation resource tree and locale tailoring loading

Embedded all 176 .res files from the pinned ICU4J 78.3 collate data directory,
including root rules, locale/type tailorings, aliases and res_index. Together
with ucadata.icu, the complete coll/ directory contains 177 resources and
3,366,608 bytes. The repository now embeds 395 resources overall. Each new
manifest entry records size, SHA-256 and the exact collate-module source path;
generated common Base64 data requires no host ICU or JVM resource loader.

Ported CollationLoader's lazy, synchronized/volatile root-rules publication,
ASCII type normalization, locale-root opening, search-type/default/standard
fallback chain, root-tailoring reuse, binary loading, lazy rules and informational
valid/actual locale keywords. It preserves mutation ordering for the output
locale and source explicit IO wrapper text. Existing common binary IO failures
use ICUUncheckedIOException in place of Java checked IOException.

Common tests load real tailorings for European, Chinese, Korean, Thai, Swedish
and Arabic locales, exercise unknown-locale fallback and uppercase type lookup,
and verify lazy/root rules. JVM tests cover every locale in upstream
InstalledLocales plus fallback variants with ten type selections per locale.
They compare valid/actual locale names, root identity, versions, rules, settings,
fast-Latin caches, CE arrays, contexts, unsafe sets and sampled trie mappings.
Explicit rules loading is compared separately against the package-private source
method. The first resource-import naming pass was corrected to retain empty
locale components in de__PHONEBOOK and es__TRADITIONAL object names.

Public collator APIs and tailoring compilation remain pending; locale binary
loading is now implemented in common code rather than supplied by oracle tests.

Verification: generate-data.py --check passed for all 395 resources; final
jvmTest, jsNodeTest, macosArm64Test and compileTestKotlinLinuxX64 passed together
in 5m 28s. Linux tests compiled, not executed on macOS. Both the embedded-data
import and the locale-loader oracle checks completed without production fixes
after the initial implementation.

## Contraction/prefix and expansion enumeration

Ported ContractionsAndExpansions and its CESink callbacks. It enumerates trie
ranges, resolves tailoring/base mappings, follows context tries, reverses stored
prefixes, collects contraction/expansion strings and emits simple or expanded
CEs (including Hangul decomposition). Array callbacks retain backing-array
identity and buffer reuse; nullable sinks/sets skip only the corresponding work.
Failed callbacks leave prefixes, suffixes, tailored/range sets and CE scratch
storage at the source operation's failure point. Reuse retains source state,
including failures when collecting into an already frozen tailored set.

The source range-subtraction quirk is preserved: after visiting un-tailored
subranges, enumeration still visits the original range. Prefix reversal matches
Java StringBuilder.reverse(), including repairing reversed surrogate pairs and
forming pairs from initially reversed units. Invalid appendCodePoint arguments
retain the source's uppercase hexadecimal error message rather than borrowing
UTF16's different validation text.

Common tests cover context strings, expansions, direct CE-array aliasing,
optional prefixes/sinks, Hangul set collection, unexpected tags and dynamically
constructed surrogate prefixes. JVM tests compare full root, five locale and
two rule-built tailoring enumerations, callback order and array identity groups,
repeated use, callback failures and all private accumulator fields. Additional
tests exercise individual code points, all CE32 tags, fallback overlap and
string-range helper boundaries/null sets. Source rules/data remain test-only.

Verification: final jvmTest, jsNodeTest, macosArm64Test and
compileTestKotlinLinuxX64 passed together in 4m 22s (Linux compiled, not executed
on macOS). The boundary oracle found the appendCodePoint error-message mismatch;
the common helper now reproduces the source message. Surrogate prefix reversal
passed on all three executed platforms.

## Tailored mapping set comparison

Ported TailoredSet, including indirect CE32 resolution, comparison of copied
expansion contents at different offsets, prefix/suffix trie merges, offset-base
comparison and Hangul membership propagation from tailored Jamo. It preserves
source traversal order, accumulated output across reuse and partially built
prefix/suffix state on errors, including mutation of the prefix before adding
its string to the output set.

Exhaustion sentinels use explicit identity state: Java compares sentinel String
objects by reference, while JavaScript strings would otherwise compare by value.
The two-U+FFFF suffix sentinel still permits a real single-U+FFFF boundary
contraction. Prefix reversal follows Java's surrogate-pair behavior on every
target, as in ContractionsAndExpansions.

Common tests check simple/copied mappings, expansion contents, parallel context
adds/removals/changes, supplementary/malformed prefix reversal and Hangul/Jamo
propagation. JVM tests compare every upstream indexed locale plus five rule-built
tailorings, normal/frozen/null output sets, repeated enumeration and private
accumulator state. Another 300 synthetic cases cover different CE32 categories,
array changes and missing metadata. An initial test compilation failure from a
local variable shadowing the ce32s field was corrected with an explicit receiver.
Public collator APIs and tailoring compilation remain pending.

Verification: final jvmTest, jsNodeTest, macosArm64Test and
compileTestKotlinLinuxX64 passed together in 3m 25s. Linux tests compiled, not
executed on macOS. The production implementation required no changes after its
initial JVM compilation and oracle comparison; the parallel-context and
surrogate-prefix fixtures passed on all three executed platforms.

## Collation rule syntax and settings parser

Ported the complete CollationRuleParser with its Position, Sink and Importer
contracts. It handles resets and reset-before strengths, all relation operators,
prefixes/extensions, quoting/backslash literals, starred characters/ranges,
special reset positions, comments, settings, reorder codes, UnicodeSet options
and nested imports. It retains shared raw-builder callback arguments, settings
mutation order, parse offsets, UTF-16-aware error context and source failure state
when imports fail before restoring outer rules.

ParseException can now attach an internal cause in common code. JVM uses the
existing java.text.ParseException alias and initCause; JS/Native retain the cause
on their common exception implementation. Callback/import failures preserve
explicit source wrapper messages and causes. As elsewhere in the port, implicit
JVM null/bounds exception messages are normalized in oracle comparisons.

Common tests exercise relation/setting results, special resets, UnicodeSet
callbacks, BCP47 imports, unpaired surrogates and wrapped callback failures.
JVM tests compare events, retained mutable callback strings, settings/reordering,
private parser state, exact explicit error context/offsets and cause chains.
Coverage includes syntax/settings fixtures, callback failures, import failures,
1,200 random fragment combinations, surrogate context windows and unique rule
strings across upstream indexed locale/type selections, with actual data imports.
A test-only package-local Java adapter reaches the source parser's internal
Sink/Importer and constructor without changing production visibility.

This is the syntax/settings parser, not the tailoring compiler: low-level data
building and final tailoring construction remain pending, together with public
collator APIs.

Verification: final jvmTest, jsNodeTest, macosArm64Test and
compileTestKotlinLinuxX64 passed together in 4m 12s. Linux tests compiled, not
executed on macOS. Wrapped callback causes passed on all executed platforms;
unique upstream rule strings and actual imports matched ICU4J on JVM. No
production corrections were required after initial compilation/oracle comparison.

## Writable legacy trie and exact frozen compaction

Ported Trie2Writable, required by CollationDataBuilder. It includes protected
ASCII/UTF-8 blocks, index/data allocation, shared range blocks, reference counts
and free lists, overwrite/initial-only ranges, alternate UTF-16 lead-unit values,
copy construction, high-start discovery, data/index deduplication and overlap,
and exact 16/32-bit frozen layout/header construction. Runtime size failures
occur after the source's in-place compaction, retaining its state.

The port preserves source details rather than repairing them: the builder has
its own shadowed index2NullOffset, and uncompact transfers the same fields as
ICU4J without copying map or firstFreeBlock. Mutable and immutable indexes remain
separate. The common Trie2 base exposes serialization operations which have no
writable equivalent in ICU4J; callers must explicitly freeze before serializing.
Private debug printing, disabled in the source, is not exposed as a new API.

Common tests cover ranges, initial-only writes, lead-unit/code-point separation,
16-bit truncation, copy/snapshot independence, post-freeze mutation and hash
invalidation. JVM tests compare private scalars and complete backing arrays over
4,800 random mutation steps plus post-compaction histories, exact serialized
bytes for both frozen widths, copying the real root trie and every Unicode
mapping, uniform/high-start/surrogate cases, allocation growth and oversized
runtime failures. Initial test compilation issues were fixed by using reflection
for a source package-private field and selecting Trie2 as the comparison type.

CollationDataBuilder, final tailoring construction and public collator APIs
remain pending; this stage supplies their mutable trie dependency.

Verification: final jvmTest, jsNodeTest, macosArm64Test and
compileTestKotlinLinuxX64 passed together in 3m 40s. Linux tests compiled, not
executed on macOS. Exact frozen bytes, mutable internal state and runtime-limit
failure state matched ICU4J; common snapshot/lead-unit/hash tests passed on all
three executed platforms. No production corrections were required after the
initial compilation and oracle comparison.

## Low-level collation data construction

Ported the complete CollationDataBuilder: simple/long/Latin-mini/32-bit/64-bit CE
encoding and sequence deduplication; linked conditional mappings; contextual base
copying; CEModifier copies; optimize/suppress; cached build-time CE iteration;
Jamo/Hangul finalization; digit and lead-surrogate tags; unsafe-backward sets;
context-trie serialization/deduplication; and optional fast-Latin construction
and base-table reuse. Output CE arrays and inherited metadata keep source aliases.

The build-time iterator redirects unfinished mappings and Jamo entries through
the existing runtime CE decoder. It refreshes vector storage, builds/caches
contexts on demand, invalidates caches by contextsEra and retries context builds
after clearing on bounds failures. Prefix ordering, longest-prefix fallback,
contraction CCC flags and surrogate-aware reversal retain source behavior.

Error and partial-state order are preserved, including encoding before rejecting
an empty source string, incremental expansion writes, failed modifiers and
compaction before a null output receiver fails. Methods such as optimize and
suppress retain their source guards rather than gaining an eager mutable check.

Common tests cover live/final context lookup, all CE encoding forms, modifiers,
suppression, output limits, backing-array aliases, frozen state and fast-Latin
reuse. JVM tests compare complete CE-vector buffers and conditional nodes,
context eras/strings, unsafe/context sets, iterator state, frozen trie bytes and
output arrays/settings. Histories include 360 mapping updates across root and
locale bases, 500 encoding sequences, failed/changed/unchanged modifiers,
post-suppress copying, malformed lengths/arrays, repeated/null build and fast
Latin reuse. Locale-base failures are compared where the source requires a root
base rather than treating unsupported base input as a successful construction.

The fast-Latin reuse fixture accounts for U+0000: an unoptimized builder retains
an indirect fallback that the fast-Latin builder does not resolve. Optimizing
U+0000 first makes the fixture's tables comparable to a directly rebuilt base.
A test syntax spacing error and initial assumptions about table identity were
corrected. Java Long/Int masks were translated with explicit widening.

Final rule-to-tailoring weight construction (CollationBuilder) and public
collator APIs remain pending; low-level data building now runs in common code.

Verification: final jvmTest, jsNodeTest, macosArm64Test and
compileTestKotlinLinuxX64 passed together in 3m 21s. Linux tests compiled, not
executed on macOS. The final matrix includes changed/failing modifiers,
post-suppress copies, live iterator state, and optimized-U+0000 table reuse.
The null-output build path explicitly evaluates trie freezing before dereferencing
the output receiver to retain Java assignment evaluation order.

## CollationBuilder stable node graph and weight allocation

Ported the node-graph portion of CollationBuilder as CollationNodeGraph, a common
component for the remaining rule-to-tailoring compiler. It provides stable node
indexes, sorted root-primary lookup, doubly linked insertion, implied/explicit
common weights, below-common secondary/tertiary flags, preceding root weights,
temporary CE/CE32 encoding and final primary/secondary/tertiary/quaternary weight
allocation. Final allocation destroys graph links in tailored nodes exactly as
in ICU4J; subsequent finalization must consume those slots as CEs.

All field masks and signed widening retain Java arithmetic, including Int shifts
before widening next indexes and temporary strength fields. An initially
transcribed previous-index mask was corrected against the hexadecimal source
before oracle tests. No eager node-limit validation replaces source mutations.

Common tests exercise temporary index round trips, stable links, below-common
nodes, allocated weight ordering and partial quaternary overflow. JVM tests
compare 23,000 full-width bit-helper cases and 80 histories of 70 graph mutations,
checking complete vector backing storage and partial weight-allocation failures.
Additional tests parse actual rule chains with the original Sink, capture their
pre-allocation graphs and compare final allocation against ICU4J. Rule chains
cover reset-before, ignorable and variable boundaries and quaternary exhaustion.

The CollationBuilder entry point is not yet implemented: reset/relation handling,
case propagation, canonical closure and wiring to the graph/finalizer remain
pending. This extracted graph will be used by that entry point, not presented as
a completed rule compiler. Its constructor accepts the enclosing builder's assertion policy; the default
follows the graph class for standalone checks.

Verification: final jvmTest, jsNodeTest, macosArm64Test and
compileTestKotlinLinuxX64 passed together in 4m 40s. Linux tests compiled, not
executed on macOS. The real-rule replay fixture initially expected null for a
successful Java void call; the adapter's Unit result is now checked correctly.
Preceding weak weights, real-rule graph allocation and partial failures matched
ICU4J, and the common graph tests passed on all executed platforms.

### Rule tailoring compilation

Implemented the CollationBuilder entry point on top of CollationNodeGraph and
CollationDataBuilder: reset-before and special reset positions, relations at all
strengths, case propagation, canonical closure, tail composites, temporary CE
finalization, imported rules, optimization and Fast Latin generation. Settings-only
rules retain the base data. ICUInputTooLongException is available in common code.
This replaces the pending builder entry point described in the previous stage;
public collator APIs and the remaining ICU4J surface still require migration.

JVM oracle coverage compares complete compiled trie bytes, CE arrays, contexts,
Jamo mappings, unsafe sets, Fast Latin tables, settings, node storage and failures
against ICU4J 78.3. It includes canonical equivalents, prefixes, contractions,
extensions, imported German phonebook rules, reset-before, all supported special
reset boundaries and quaternary exhaustion. Common tests exercise generated
ordering, canonical equivalence, imports and settings-only data sharing.

Verification: jvmTest, jsNodeTest, macosArm64Test and
compileTestKotlinLinuxX64 passed together in 3m 54s. Linux tests were compiled,
not executed on macOS. The targeted CollationBuilder JVM oracle run also passed.

### SearchIterator protocol

Ported the complete SearchIterator base class to Common Kotlin, using the existing
portable CharacterIterator and BreakIterator contracts. Forward/backward state,
repeated matches on direction changes, overlap, first/last/following/preceding,
match text extraction, target aliasing, cloned boundary cursors and nullable
element-comparison options follow ICU4J. The source distinction between constructor
empty-range validation and setTarget's current-position-at-end rejection is
retained, as are mutations before boundary clone failures. Protected state and
search hooks remain available to subclasses; internal Search state supports the
future StringSearch port. This class supplies the iteration protocol, not the
collation matching algorithm; StringSearch remains pending.

A JVM fixture subclass compares 4,000 mixed state transitions with the equivalent
ICU4J subclass, including absolute nonzero ranges, dispatch arguments, invalid
indices, cursor positions and match metadata. Common tests cover direction changes,
overlap, reset, boundary cursor isolation, target replacement and clone failures.
The targeted JVM test run passed before the final clone-failure coverage was added.

Final verification: jvmTest, jsNodeTest, macosArm64Test and
compileTestKotlinLinuxX64 passed together in 4m 24s, including the added
clone-failure tests. Linux tests compiled only; they were not run on macOS.

### CollationElementIterator engine

Ported CollationElementIterator's runtime algorithms into the internal
CollationElementIteratorCore. The future public collator supplies its owner,
current data/settings and maximum-expansion cache through accessors. No temporary
public RuleBasedCollator facade was introduced. Public Collator,
RuleBasedCollator and CollationElementIterator constructors remain pending.

The engine preserves splitting 64-bit CEs into legacy 32-bit halves, continuation
markers, forward/backward direction errors, buffered expansion offsets, unsafe
setOffset backtracking, reset and the three text-input paths. CharacterIterator
inputs remain aliased; UCharacterIterator inputs are cloned, with the source
fallback to string text specifically for CloneNotSupportedException. A portable
exception bridge aliases java.lang.CloneNotSupportedException on JVM. Maximum
expansion enumeration, order extraction, equality and assertion-sensitive hashing
also follow the original implementation. FCD iterator equality retains its
previously documented upstream recursion; this stage does not change it.

Targeted JVM tests passed for complete forward/reverse CE streams and offsets,
then mixed cursor operations with numeric and FCD options, three text cursor
kinds, rule-built contractions/prefixes/expansions, imported phonebook rules,
normalization, Hangul, supplementary and malformed UTF-16. Full maximum-expansion
maps match ICU4J. Additional coverage checks 10,000 full-width half encodings,
non-FCD equality and lazy failures after a null text replacement. Common tests
cover direction constraints, safe contraction offsets, cursor ownership and
clone-failure fallback.

Final verification: jvmTest, jsNodeTest, macosArm64Test and
compileTestKotlinLinuxX64 passed together in 3m 18s. Linux tests compiled only;
the host executes JVM, Node and macOS Arm64 tests.

### RuleBasedCollator comparison and key engine

Moved the RuleBasedCollator runtime comparison/key pipeline into the internal
RuleBasedCollatorCore, ready for the future public collator's data/settings
accessors. This is the original pipeline, including identical-prefix skipping
with unsafe backtracking, Fast Latin dispatch and bailout, general CE comparison,
IDENTICAL-level FCD/NFD code-point iteration and U+FFFE ordering. The identical
sort-key level uses NFD quick-check spans and BOCSU runs, synchronized back into
the resizable key sink. Raw keys retain source allocation growth and tail bytes;
immutable keys take ownership of cached raw-key storage as upstream does.

Reusable CE/NFD iterators and raw-key buffers are created lazily. Frozen buffers
use a reentrant platform lock. Release retains the source frozen-state recheck,
including the unheld-lock failure when a custom CharSequence freezes its collator
inside internalGetCEs. The public configuration API, cloning, equality, locale
metadata and Collator service remain pending; this engine is not a complete
public RuleBasedCollator implementation.

JVM differential tests cover five rule sets, all five strengths, numeric/FCD
combinations, complete key bytes and CE streams, malformed UTF-16, canonical
sequences, contractions, prefixes, backward secondary, shifted/case options,
script reordering and common-prefix backtracking. Raw key tests compare entire
backing arrays over reused capacities, including long identical-level keys.
Concurrent frozen calls share one engine across six JVM threads. Common tests
exercise rule order, canonical equality, identical tiebreakers, key/compare
consistency, null inputs, storage reuse and frozen buffers.

The targeted JVM run passed after correcting a test rule from invalid
`[strength 15]` to ICU's `[strength I]`; the production implementation did not
need a change for that test failure.

Final verification: jvmTest, jsNodeTest, macosArm64Test and
compileTestKotlinLinuxX64 passed together in 4m 4s, including the custom
CharSequence freeze/release test. Linux tests were compiled, not executed on macOS.

### RuleBasedCollator configuration transitions

Ported the RuleBasedCollator setter/getter layer into the internal
RuleBasedCollatorSettings, ready for the public wrapper. This covers case-first,
alternate handling, case level, decomposition, backward secondary, strength,
numeric mode, variable top, max-variable groups, reordering and construction
default restoration. Each successful change refreshes Fast Latin options through
the original path. Reference-identity no-ops, copy-on-write before strength/reorder
validation, input array copying and default reorder table sharing are retained.
Deprecated Hiragana setters remain frozen-checked no-ops.

The source setMaxVariable method omits checkNotFrozen; this port deliberately
retains that behavior, including its validation order. String variable-top input
must map to exactly one CE and is pinned to the end of a supported special group.
The integer overload preserves unsigned primary interpretation and exact explicit
validation messages. Other setters reject frozen state before even a no-op or
argument validation, matching ICU4J.

A targeted JVM run passed for 1,500 mixed configuration transitions across root,
custom-default and reordered tailors. Checks compare all option/reordering fields,
Fast Latin arrays, default/reference ownership, current keys/comparisons, failures,
freeze and cloned settings. Retained fixture generations make the COW oracle
independent of GC cleanup timing. Further tests check caller reorder-array copies
and default table sharing. Common tests cover configuration effects on ordering,
clone isolation, default restoration, validation order and frozen behavior.
Public Collator/RuleBasedCollator wiring and locale service remain pending.

Final verification: jvmTest, jsNodeTest, macosArm64Test and
compileTestKotlinLinuxX64 passed together in 3m 27s. Linux tests were compiled,
not run on this macOS host. Caller-array/default-sharing coverage also passed.

### RuleBasedCollator introspection and locale state

Ported the remaining introspection algorithms to RuleBasedCollatorMetadata:
rules/full root rules, tailored sets, contractions/expansions, internal contraction
addition, settings/data/rules/tailored-set equality shortcuts and source hashing.
The future public wrapper must retain object identity and runtime-class checks
before calling hasSameBehavior. The source equality fallback compares tailored
sets, not all CE mappings; that behavior is preserved even for distinct orders
with the same tailored set.

Version decoding now includes UCOL_RUNTIME_VERSION. Unsigned packed-version
arithmetic, UCA extraction and the explicit overflow error match ICU4J. Locale
metadata keeps valid locale separate from the shared tailoring's actual locale;
setLocale updates the overlay flag without changing shared tailoring. Copying
locale metadata retains that flag without invoking setLocale validation.

Maximum-expansion initialization uses a reentrant lock owned by CollationTailoring,
matching synchronization on the shared tailoring object. Separate collator clones
therefore share both the cache and its initialization lock. The lock is portable;
no Java collation implementation is used in production.

Targeted JVM tests passed for rule-built and locale-loaded collators, pairwise
source equality, hash values, rules, tailored/expansion/contraction sets, locale
transitions and 1,504 arbitrary/boundary packed versions. Common tests cover rules,
locale overlay copying, shared cache identity and root UCA/runtime versions. Final
coverage adds first-cache initialization racing across six separate metadata owners.
Public Collator/RuleBasedCollator wrappers, cloning integration and service APIs
remain pending.

Final verification: jvmTest, jsNodeTest, macosArm64Test and
compileTestKotlinLinuxX64 passed together in 3m 26s, including concurrent initial
cache publication. Linux tests compiled only; the host is macOS Arm64.

### Collator locale keyword application

Ported Collator's keyword parsing/application to CollationLocaleOptions, with
separate base-collator and RuleBasedCollator sinks for future service integration.
The existing settings engine has a complete adapter. Source ordering is retained:
deprecated Hiragana/variable-top keywords fail first, known attributes apply in
fixed order and unrelated keywords are ignored. A later failure retains earlier
setter effects. Non-RuleBasedCollator rejection precedes parsing of RBC-specific
values, including the source's colCaseLevel error text referring to colBackwards.

ASCII-only case matching, yes/no and enumerated values, strength mapping, strict
four-character script names, special reorder groups, bounded script lists and
max-variable keyword parsing follow ICU4J. No Unicode case folding replaces the
ASCII checks. Factory selection of collation type remains the loader/service's
responsibility; these attribute keywords do not replace locale tailoring loading.

Targeted JVM tests passed for individual and 180 randomized mixed keyword sets,
language tags, non-RBC paths, explicit errors, partial state and recorded base
setter dispatch. Resulting keys also match ICU4J. Helpers are checked for null and
invalid inputs, plus 5,000 full-width ASCII-comparison samples. Common tests cover
settings integration and partial failures. The first test adapter incorrectly
attempted to subclass ICU4J's final RuleBasedCollator; it now uses a base Collator
recorder delegating to an original instance. No production change was needed for
that fixture compile failure. Public collator/service wrappers remain pending.

Final verification: jvmTest, jsNodeTest, macosArm64Test and
compileTestKotlinLinuxX64 passed together in 3m 4s. Linux tests were compiled,
not executed on this macOS host.

### Public rule collators and CE iteration

Added the public Collator instance contract, final RuleBasedCollator and final
CollationElementIterator, connecting all previously ported engines. The rule
constructor compiles the full rule language, sets rule-based locale metadata to
null, clones the tailoring settings reference and initializes no reusable runtime
buffer eagerly. Public attributes, comparison (String and Comparator<Object>),
keys, introspection, locale metadata, CE cursor factories and order helpers are
now usable from common code. Collator.getInstance/service/registration/enumeration
static APIs are still pending; no direct-loader substitute is presented as that
service. Equivalent reorder-code lookup is available.

RuleBasedCollator clones retain shared immutable data/tailoring and a cloned
settings reference, with fresh runtime buffers/locks and copied locale overlay
state. Frozen clone returns the same object; cloneAsThawed always returns a new
unfrozen rule collator. This typed copy works on Native as well as JVM and JS.
Open Collator subclasses use host shallow cloning on JVM/JS. As with other open
clone bridges in this port, Native custom subclasses must supply cloneNative for
a typed copy; otherwise the portable CloneNotSupportedException is thrown.
The bridge does not add Serializable to the source non-serializable hierarchy.

Reordering has a nullable IntArray virtual method and a Kotlin vararg convenience.
The latter is named setReorderCodesValues on JVM to avoid the erased array
signature clash. The array method keeps setReorderCodes and preserves null reset
and frozen-check order. Java callers supplying individual values use the named
convenience or an explicit int array. This is an API migration detail, not an
alternate ordering algorithm.

Targeted public JVM tests passed for rule construction, keys, pairwise comparisons,
complete forward/backward CE streams, clone isolation, frozen identities, errors,
null cursor inputs, object-comparator dispatch and nullable reorder-array reset.
Common tests cover the same entry points including Native typed clone and text
cursor adapters. Additional base-class oracle coverage checks default behavior,
unsupported methods, shallow cloning, runtime-type equality and non-serialization.
An initial common test shadowed its CE cursor with repeat's Int parameter; the
fixture now names that lambda parameter explicitly.

Final verification: jvmTest, jsNodeTest, macosArm64Test and
compileTestKotlinLinuxX64 passed together in 3m 25s, including public entry points
and the additional base-class oracle. Linux tests compiled, not run on macOS.

### Collator locale service and static factory APIs

Implemented the CollatorServiceShim behavior with the existing portable
ICULocaleService: lazy shared service initialization, collation-resource factories,
root fallback independent of the process default locale, cached prototypes cloned
on every retrieval, registered instances, visible/invisible factories and removal.
Registered instances receive locale metadata before insertion. Frozen prototypes
retain ICU4J's same-object clone result, so subsequent keyword application can
reject mutation. CloneNotSupportedException is wrapped in the newly ported
ICUCloneNotSupportedException with the original cause and serial UID.

Public Collator factories now apply locale keywords after obtaining the service
clone. Available locales, display names, keywords, keyword values and functional
equivalent locale lookup are connected to the real resources. Keyword value sinks
retain default-first ordering, fallback de-duplication and exclusion of private
collations. Source-unused key/commonlyUsed arguments remain unused. The returned
getKeywords array remains shared as upstream does. JVM Locale conveniences use
extensions, as elsewhere in this port. CollatorFactoryLocaleBridge retains legacy
overridable Java Locale factory/display-name overloads; Common factories override
ULocale. As in ICU4J, a factory must override a creation method to avoid delegation
recursion.

Oracle evidence exposed an existing overly narrow resource helper signature:
getFunctionalEquivalent accepts a null keyword in ICU4J, including a root lookup
that returns root successfully. Its common keyword parameter is now nullable;
no premature assertion replaces the original locale/resource processing.

Targeted JVM tests passed for default and requested locale factories, language-tag
keywords, root fallback, available locale sets, keyword values/order, functional
equivalence (including null keywords and availability outputs), display names,
instance/factory registration, frozen prototypes and legacy Java Locale overrides.
Final coverage adds generic collator keyword rejection, clone-failure wrapping and
root fallback while the process default locale is Japanese. Common tests cover
independent factory results, keyword effects, registration and invisible factories.

Verification: the full jvmTest, jsNodeTest, macosArm64Test and
compileTestKotlinLinuxX64 run passed in 3m 24s. Linux tests compiled only. A final
JVM service run also passed after improving the test's restoration of process
DISPLAY/FORMAT defaults; production and common tests were unchanged after the
full run.

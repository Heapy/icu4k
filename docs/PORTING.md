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

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

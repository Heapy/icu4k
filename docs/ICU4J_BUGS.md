# ICU4J bug tracker

Track ICU4J problems discovered while porting ICU4K, preserve independent
reproductions, and prepare reports for upstream. The examples below use ICU4J
alone. Reproduced behavior is distinguished from an accepted upstream defect.

## Baseline and verification

- Local verification: entries 001–004 on 2026-10-15; entry 005 on 2026-10-16.
- Artifact: `com.ibm.icu:icu4j:78.3`; reported ICU version: `78.3.0.0`.
- Binary SHA-256: `e962c1758d9659ea1e1fbab99c58683f654d304e1126ace19aaabfe39e0edb25`.
- Environment: macOS arm64, OpenJDK `25.0.4+9-LTS`.
- Source inspection: [published 78.3 source JAR](https://repo.maven.apache.org/maven2/com/ibm/icu/icu4j/78.3/icu4j-78.3-sources.jar).
- Port baseline: `unicode-org/icu`, `release-78.3`, commit
  `21d1eb0f306e1141c10931e914dfc038c06121da`, as recorded in [PORTING.md](PORTING.md).

All five Java examples were run locally against the binary above. Current ICU
releases and upstream HEAD still need checking. Duplicate searches and upstream
submission are pending; no upstream acceptance is implied. Owners are unassigned.

Save each Java block under its class name, then run it in a separate JVM:

```sh
curl -fL https://repo.maven.apache.org/maven2/com/ibm/icu/icu4j/78.3/icu4j-78.3.jar -o /tmp/icu4j-78.3.jar
java --class-path /tmp/icu4j-78.3.jar RegionOverrideRepro.java
java --class-path /tmp/icu4j-78.3.jar CurrencyMapRepro.java
java -Dcom.ibm.icu.impl.breakiter.useMLPhraseBreaking=false --class-path /tmp/icu4j-78.3.jar CjkPhraseRepro.java
java --class-path /tmp/icu4j-78.3.jar CurrencyNullCacheRepro.java
java --class-path /tmp/icu4j-78.3.jar GregorianCutoverRepro.java
```

The examples change process defaults or cached data; use disposable processes.
Deprecation warnings in the region example concern ICU internal diagnostic APIs.
Its measurement-system lookup and the map/CJK examples exercise stable public APIs.

## Tracking overview

Priority is local triage priority, not an upstream severity assignment.

| ID | Problem | Assessment | Priority | Upstream issue |
| --- | --- | --- | --- | --- |
| [ICU4J-001](#icu4j-001-region-overrides-depend-on-the-host-locale) | Turkish/Azeri host locale changes region overrides and measurement systems | Reproduced defect | High | Not submitted |
| [ICU4J-002](#icu4j-002-currency-maps-violate-the-unmodifiable-contract) | Currency parsing maps are mutable despite their API contract | Reproduced contract violation | Medium | Not submitted |
| [ICU4J-003](#icu4j-003-cjk-phrase-breaking-fails-with-a-nonzero-text-begin-index) | CJK phrase breaking throws on a valid bounded iterator | Reproduced defect | High | Not submitted |
| [ICU4J-004](#icu4j-004-a-null-currency-code-poisons-the-formatting-cache) | A null currency lookup breaks subsequent valid lookups | Reproduced behavior; contract review pending | Low | Not submitted |
| [ICU4J-005](#icu4j-005-setting-the-existing-gregorian-cutover-changes-historical-dates) | Setting the existing Gregorian cutover changes historical dates | Reproduced defect | Medium | Not submitted |
| [ICU4J-006](#icu4j-006-calendarcache-negative-key-probing-can-loop-forever) | A negative CalendarCache key can make collision probing loop forever | Reproduced defect | High | Not submitted |

## ICU4J 001 Region overrides depend on the host locale

Suggested upstream title: **Region override handling uses the default Java locale
and ignores valid Indonesian overrides under Turkish and Azeri defaults**.

**Trigger and impact.** With `en@rg=idzzzz`, changing only the Java default locale
from root to `tr-TR` or `az-AZ` changes the resolved region from Indonesia to the
United States. The public `LocaleData.getMeasurementSystem` consequently returns
US measurements instead of SI. Direct validation of ASCII `ID` also throws an
array bounds exception under those defaults.

**Expected.** The explicit Indonesian region override resolves to `ID` and SI
under every host locale. Validation of `ID` succeeds without an exception.

```java
import com.ibm.icu.util.LocaleData;
import com.ibm.icu.util.ULocale;
import java.util.Locale;

public class RegionOverrideRepro {
    public static void main(String[] args) {
        for (String tag : new String[]{"und", "tr-TR", "az-AZ"}) {
            Locale.setDefault(Locale.forLanguageTag(tag));
            var locale = new ULocale("en@rg=idzzzz");
            System.out.println(tag + " region="
                + ULocale.getRegionForSupplementalData(locale, true)
                + " SI=" + (LocaleData.getMeasurementSystem(locale)
                    == LocaleData.MeasurementSystem.SI));
            try {
                System.out.println("ID valid=" + new ULocale.RegionValidateMap().isSet("ID"));
            } catch (ArrayIndexOutOfBoundsException e) {
                System.out.println(e.getClass().getSimpleName());
            }
        }
    }
}
```

Observed output on 78.3:

```text
und region=ID SI=true
ID valid=true
tr-TR region=US SI=false
ArrayIndexOutOfBoundsException
az-AZ region=US SI=false
ArrayIndexOutOfBoundsException
```

**Source finding.** In `com/ibm/icu/util/ULocale.java`, `getRegionFromKey` uses
default-locale `toUpperCase()`, and `RegionValidateMap.value` uses default-locale
`toLowerCase()`. Turkish/Azeri casing introduces non-ASCII dotted/dotless I.
An override can be rejected before validation; direct validation can compute an
index outside the region bitset. Locale-independent ASCII casing is a possible
fix direction for both sites.

**Existing evidence.**
[LocaleDataOracleTest.regionValidationMaskAndHostCaseMatchSource](../src/jvmTest/kotlin/io/github/heapy/icu4k/util/LocaleDataOracleTest.kt)
compares root, Turkish and Azeri defaults, including the region mask and `rg`
selection. [Porting notes](PORTING.md#common-localedata-and-supplemental-region-selection)
record the retained behavior.

**Before filing.** Check current releases/HEAD and duplicates. Include both the
public measurement-system symptom and the internal validator diagnostic in one
report, since they share the locale-sensitive casing cause.

## ICU4J 002 Currency maps violate the unmodifiable contract

Suggested upstream title: **CurrencyDisplayNames symbolMap and nameMap return
mutable cached maps despite the unmodifiable API contract**.

**Trigger and impact.** A caller can insert entries into both returned maps.
Later calls observe the mutation, including through another factory lookup in
the reproduced case. Callers can therefore alter cached currency parsing data.

**Expected.** Both maps reject mutation with `UnsupportedOperationException`,
as required by the `CurrencyDisplayNames.symbolMap` and `nameMap` Javadoc in the
78.3 source JAR.

```java
import com.ibm.icu.text.CurrencyDisplayNames;
import com.ibm.icu.util.ULocale;

public class CurrencyMapRepro {
    public static void main(String[] args) {
        var names = CurrencyDisplayNames.getInstance(ULocale.ENGLISH);
        names.symbolMap().put("custom", "ZZZ");
        names.nameMap().put("custom", "ZZZ");
        var again = CurrencyDisplayNames.getInstance(ULocale.ENGLISH);
        System.out.println("symbol=" + again.symbolMap().get("custom"));
        System.out.println("name=" + again.nameMap().get("custom"));
    }
}
```

Observed output on 78.3:

```text
symbol=ZZZ
name=ZZZ
```

**Source finding.** In `com/ibm/icu/impl/ICUCurrencyDisplayInfoProvider.java`,
`ICUCurrencyDisplayInfo.symbolMap` and `nameMap` return maps from cached
`ParsingData` directly. An unmodifiable view or immutable cached representation
is a possible fix direction. The cache uses a soft reference; this observation
does not establish that mutations survive garbage collection indefinitely.

**Existing evidence.**
[CurrencyDisplayNamesOracleTest.nullCacheKeysAndMutableMapsMatch](../src/jvmTest/kotlin/io/github/heapy/icu4k/text/CurrencyDisplayNamesOracleTest.kt)
tests mutation of the symbol map and internal unit patterns. The Java example
above independently covers both public maps. Internal unit-pattern mutability
is outside this report's documented public contract.

**Before filing.** Check current releases/HEAD, current Javadoc and duplicates.
Attach the two public map cases and the 78.3 Javadoc contract.

## ICU4J 003 CJK phrase breaking fails with a nonzero text begin index

Suggested upstream title: **Japanese phrase line breaking throws for a valid
StringCharacterIterator with a nonzero begin index**.

**Trigger and impact.** Japanese phrase line breaking receives a character
iterator over the valid range `[1, 4)` of `A日本語`. The first `next()` call throws
instead of returning the next boundary. The failure is reachable through the
public factory, without reflection or direct calls to implementation classes.

**Expected.** Navigation respects the iterator's begin/end bounds and returns
valid boundaries or `BreakIterator.DONE` without an invalid-code-point exception.

```java
import com.ibm.icu.text.BreakIterator;
import com.ibm.icu.util.ULocale;
import java.text.StringCharacterIterator;

public class CjkPhraseRepro {
    public static void main(String[] args) {
        var iterator = BreakIterator.getLineInstance(new ULocale("ja@lw=phrase"));
        iterator.setText(new StringCharacterIterator("A日本語", 1, 4, 1));
        iterator.first();
        System.out.println(iterator.next());
    }
}
```

Run with the ML phrase-breaking property set to `false`, as in the shared command
above. Observed exception on 78.3:

```text
java.lang.IllegalArgumentException: Invalid code point U+7FFFFFFF
    at com.ibm.icu.text.UnicodeSet.contains(UnicodeSet.java:1780)
    at com.ibm.icu.impl.breakiter.CjkBreakEngine.divideUpDictionaryRange(CjkBreakEngine.java:302)
```

**Source finding.** In `com/ibm/icu/impl/breakiter/CjkBreakEngine.java`, the
phrase-boundary punctuation check tests `pos > 0` before calling `previous32`.
At a nonzero iterator begin index, there is no preceding character in the iterator.
`previous32` returns `DONE32` (`0x7fffffff`), which is passed to `UnicodeSet.contains`.
A guard against the iterator's begin index or the sentinel is a possible fix
direction; boundary semantics need a regression test.

**Existing evidence.**
[RuleBasedBreakIteratorOracleTest.publicNavigationStatusesTextOwnershipAndCloningMatchOriginal](../src/jvmTest/kotlin/io/github/heapy/icu4k/text/RuleBasedBreakIteratorOracleTest.kt)
covers bounded iterators and exception/state parity. The
[porting notes](PORTING.md) explicitly describe the nonzero-begin CJK punctuation
failure. The reduced Java example above establishes public API reachability.

**Before filing.** Check current releases/HEAD and duplicates. Attach the exact
locale, text, iterator bounds, JVM property and stack trace. Check ML-enabled
behavior separately before extending the report to that path.

## ICU4J 004 A null currency code poisons the formatting cache

Suggested upstream title: **CurrencyDisplayNames accepts a null code then throws
on a subsequent valid currency lookup**.

**Assessment.** The state-dependent behavior is reproduced, but `null` is not a
documented three-letter ISO code. Keep this as a robustness candidate until the
upstream invalid-input contract is established.

**Trigger and impact.** On a fresh English display-name instance, `getName(null)`
returns `null`. The subsequent `getName("USD")` throws `NullPointerException`.
The bad cache entry also makes a repeated null lookup fail.

**Expected proposal.** Reject null consistently before changing the cache, or
handle it without breaking later valid calls. Upstream must decide which behavior
matches the API contract.

```java
import com.ibm.icu.text.CurrencyDisplayNames;
import com.ibm.icu.util.ULocale;

public class CurrencyNullCacheRepro {
    public static void main(String[] args) {
        var names = CurrencyDisplayNames.getInstance(ULocale.ENGLISH);
        System.out.println("first=" + names.getName(null));
        System.out.println("USD=" + names.getName("USD"));
    }
}
```

Observed on 78.3: prints `first=null`, then throws:

```text
java.lang.NullPointerException: Cannot invoke "String.equals(Object)" because "result.isoCode" is null
```

**Source finding.** `ICUCurrencyDisplayInfo.fetchFormattingData` caches a
`FormattingData` whose `isoCode` can be null. A later lookup dereferences that
cached key when comparing it with the next requested code. The variant/plural
caches have similar comparisons; verify each API separately before adding it to
the report.

**Existing evidence.**
[CurrencyDisplayNamesOracleTest.nullCacheKeysAndMutableMapsMatch](../src/jvmTest/kotlin/io/github/heapy/icu4k/text/CurrencyDisplayNamesOracleTest.kt)
compares sequences of null/null/USD calls for several caches. The Java example
above isolates formatting-cache poisoning through the public API.

**Before filing.** Establish the null-input contract, check current releases/HEAD
and duplicates, then decide whether to file separately or discard as unsupported
input. Do not present this as a confirmed public contract violation yet.

## ICU4J 005 Setting the existing Gregorian cutover changes historical dates

**Suggested title.** GregorianCalendar.setGregorianChange uses an epoch day as a Julian day.

**Status.** Reproduced locally against ICU4J 78.3 on 2026-10-16, OpenJDK
25.0.4+9-LTS, macOS arm64. Local priority: medium. Owner unassigned. No report
submitted; current releases, upstream HEAD and duplicate searches remain pending.

**Trigger and impact.** Calling `setGregorianChange(getGregorianChange())`
should retain the calendar's cutover. After clearing cached fields and setting
the same instant, the calendar instead reports a different historical date.
For the default transition, Julian October 4, 1582 becomes Gregorian October 14.
The expected result is October 4 both times, since neither the cutover date nor
the instant changes.

Save as `GregorianCutoverRepro.java` and run with the command above:

```java
import com.ibm.icu.util.GregorianCalendar;
import com.ibm.icu.util.TimeZone;
import com.ibm.icu.util.ULocale;

public class GregorianCutoverRepro {
    public static void main(String[] args) {
        GregorianCalendar cal = new GregorianCalendar(
            TimeZone.getTimeZone("Etc/GMT"), ULocale.US);
        long instant = -12219379200000L;
        cal.setTimeInMillis(instant);
        print("before", cal);
        cal.setGregorianChange(cal.getGregorianChange());
        cal.clear();
        cal.setTimeInMillis(instant);
        print("after", cal);
    }
    private static void print(String label, GregorianCalendar cal) {
        System.out.println(label + ": " + cal.get(GregorianCalendar.YEAR) + "-" +
            (cal.get(GregorianCalendar.MONTH) + 1) + "-" +
            cal.get(GregorianCalendar.DAY_OF_MONTH));
    }
}
```

Actual output:

```text
before: 1582-10-4
after: 1582-10-14
```

**Source finding.** In the pinned GregorianCalendar.java, the default
`cutoverJulianDay` is 2299161. The setter replaces it with
`floorDivide(gregorianCutover, ONE_DAY)` without adding `EPOCH_JULIAN_DAY`.
For the default cutover it becomes -141427. `handleComputeFields` subsequently
compares an absolute Julian day against this epoch-day value. A possible fix
direction is to include the epoch-to-Julian-day offset, with corresponding
boundary and non-UTC-zone tests. This is source analysis, not an upstream verdict.

**ICU4K evidence.** `GregorianCalendarCoreOracleTest` compares original and common
field calculations before/after cutover updates, including the original cutover
value, extreme values and multiple calendar states. `GregorianCalendarCoreTest`
has an explicit regression for October 4 becoming October 14 after the setter.
The port preserves ICU4J 78.3 behavior pending a baseline compatibility decision.

## ICU4J 006 CalendarCache negative-key probing can loop forever

**Suggested title.** CalendarCache.hash2 can produce a zero probe step modulo the table size.

**Status.** Reproduced locally against ICU4J 78.3 on 2026-10-16, OpenJDK
25.0.4+9-LTS, macOS arm64. Local priority: high. Owner unassigned. No report
submitted; current releases, upstream HEAD and duplicate searches remain pending.

**Trigger and impact.** In a new cache, store key 59 and then query absent key -2.
The lookup never returns. A calendar using negative-year cache keys can encounter
this when a conflicting slot is occupied; negative keys alone do not always hang.
An absent key should return `CalendarCache.EMPTY`.

Save as `CalendarCacheProbe.java` and run in a child process with a timeout:

```java
import com.ibm.icu.impl.CalendarCache;

public class CalendarCacheProbe {
    public static void main(String[] args) {
        CalendarCache cache = new CalendarCache();
        cache.put(59, 123);
        System.out.println("before get(-2)");
        System.out.println(cache.get(-2));
        System.out.println("after get(-2)");
    }
}
```

Observed output was only `before get(-2)`. The local source-launch process was
terminated after exceeding five seconds; it never printed a lookup result or the
after marker. The reproduction uses a fresh cache and no threads.

**Source finding.** The initial table size is 61. Both keys hash to slot 18.
For -2, `hash2` is `61 - 2 - (-2 % 59)`, which equals 61 under Java's signed
remainder. Collision probing repeatedly computes `(18 + 61) % 61 == 18` and
cannot advance. A possible fix direction is to normalize the secondary remainder
so every step is nonzero modulo the table size. This is source analysis, not an
upstream verdict.

**ICU4K evidence and compatibility.** The common CalendarCache retains the source
hashing, replacement counts, sentinel and synchronization. Its JVM oracle compares
complete cache layouts through growth and signed-long probes. Hebrew signed-year
arithmetic is compared with cold caches to isolate it from this source probing
defect. The hanging lookup is deliberately kept out of in-process test execution.
No behavior change is applied without a separate baseline compatibility decision.

## Upstream handoff

Use stable local IDs when updating entries. Track each issue through
`Observed` → `Reproduced` → `Ready to report` → `Reported` → `Accepted` →
`Fixed upstream` → `Verified fixed`. Record `Duplicate` or `Rejected` with the
upstream link and reason when appropriate. Candidates need contract review before
moving to `Ready to report`.

Before marking an entry ready:

- [ ] Assign an owner and record the verification date and exact ICU/JDK/OS versions.
- [ ] Run its standalone example against the current release and, where practical,
  upstream HEAD; record results for each version.
- [ ] Search upstream issues and changes; record search date, terms and relevant links.
- [ ] Confirm expected behavior against the applicable API contract or boundary rules.
- [ ] Attach the English title, minimal example, command, actual output, expected
  behavior, impact and source analysis to the report draft.

After submission, add the issue URL and submission date to the entry and overview.
Record any upstream verdict, PR/commit, first fixed release and verification run.
Preserve the original reproduction when a later version fixes the problem.

## ICU4K compatibility

The existing oracle tests preserve these ICU4J 78.3 behaviors for compatibility.
This tracker does not change ICU4K behavior. An upstream fix requires a separate
decision about the port's baseline, intended behavior and regression tests.

## New entry template

Copy this structure for further findings:

```text
ID: ICU4J-NNN
Suggested upstream title:
Assessment and status:
Local priority:
Owner:
First affected version observed:
Last verification date and ICU/JDK/OS versions:
Trigger and user impact:
Expected behavior and contract:
Minimal standalone Java reproduction and command:
Actual output or stack trace:
Source finding and possible fix direction:
Existing ICU4K evidence:
Current release and HEAD results:
Duplicate search date, terms and links:
Upstream issue URL and submission date:
Upstream verdict, fix PR/commit and first fixed release:
Verification of the fixed version:
ICU4K compatibility decision:
```

# ICU4K

Incremental port of Unicode ICU4J 78.3 to Common Kotlin.

**Work in progress: this is not yet a replacement for ICU4J.**
Algorithms belong in `commonMain`; ICU4J may be used only as a JVM test oracle.
Targets: JVM, JavaScript/Node, Linux x64 and macOS arm64.
Toolchain: Kotlin 2.4.21, Gradle 9.8.1; JDK 25 to run Gradle.

```sh
./gradlew jvmTest jsNodeTest
# On Apple Silicon:
./gradlew macosArm64Test
```

Pinned ICU data is embedded for JVM, JS and Native. To verify generated data:

```sh
python3 scripts/generate-data.py --check
```

See [porting scope](docs/PORTING.md) for measured source size and stages.
See the [ICU4J bug tracker](docs/ICU4J_BUGS.md) for upstream findings and reproductions.
Unicode's upstream license and third-party notices are retained in LICENSE.

Common Kotlin normalization is available through `Normalizer2`:

```kotlin
import io.github.heapy.icu4k.text.Normalizer2

val composed = Normalizer2.getNFCInstance().normalize("e\u0301") // é
val decomposed = Normalizer2.getNFDInstance().normalize("é") // e + U+0301
```

Common Kotlin break rules can be compiled and used directly:

```kotlin
import io.github.heapy.icu4k.text.BreakIterator
import io.github.heapy.icu4k.text.RuleBasedBreakIterator

val iterator = RuleBasedBreakIterator("[a-z]+{200};[0-9]+{100};.;")
iterator.setText("abc12!")
while (iterator.next() != BreakIterator.DONE) {
    println("${iterator.current()}: ${iterator.getRuleStatus()}")
}
// Boundaries/statuses: 3/200, 5/100, 6/0.
val binaryRules: ByteArray = RuleBasedBreakIterator.compileRules("[a-z]+;.;")
```

The locale identity and language-tag core is available through `ULocale`:

```kotlin
import io.github.heapy.icu4k.util.ULocale

val locale = ULocale.forLanguageTag("en-US-u-ca-japanese")
println(locale.getName()) // en_US@calendar=japanese
println(locale.toLanguageTag()) // en-US-u-ca-japanese
val available = ULocale.getAvailableLocalesByType(ULocale.AvailableType.WITH_LEGACY_ALIASES)
```

ULocale display names and remaining locale services are still being ported.

Common Kotlin plural rules include locale data and custom rule descriptions:

```kotlin
import io.github.heapy.icu4k.text.PluralRules
import io.github.heapy.icu4k.util.ULocale

val plurals = PluralRules.forLocale(ULocale("en"))
println(plurals.select(1.0)) // one
println(plurals.select(1.0, 1, 0)) // other: visible fraction "1.0"
val custom = PluralRules.parseDescription("one: n = 1; few: n = 2..4")
println(custom.select(3.0)) // few
```

Fluent number formatting and formatted-number results are available in Common Kotlin:

```kotlin
import io.github.heapy.icu4k.number.NumberFormatter
import io.github.heapy.icu4k.number.Precision
import io.github.heapy.icu4k.util.BigDecimal
import io.github.heapy.icu4k.util.ULocale

val formatter = NumberFormatter.with()
    .precision(Precision.fixedFraction(2))
    .locale(ULocale.ENGLISH)
println(formatter.format(BigDecimal("12.345"))) // 12.34

val mixed = NumberFormatter.forSkeleton("unit/foot-and-inch unit-width-full-name")
    .locale(ULocale.ENGLISH)
println(mixed.format(1.5)) // 1 foot, 6 inches
```

`FormattedNumber` exports fields, attributed runs, rounded decimal values and
resolved unit metadata. `toFormat()` produces a Java `Format` on JVM and a portable
adapter on JS/Native; the Common append overload uses `StringBuilder`.
Nested enum names in `NumberFormatter` are Kotlin aliases for the existing
standalone enums. JVM `java.util.Locale` entry points are Kotlin extensions.
Localized NumberFormat factories and the remaining ICU services are still being
ported; locale resource coverage is still incremental.

Numeric ranges are available through `NumberRangeFormatter`, including field
spans and identity/collapse policies:

```kotlin
import io.github.heapy.icu4k.number.NumberRangeFormatter
import io.github.heapy.icu4k.number.UnitWidth
import io.github.heapy.icu4k.util.MeasureUnit

val ranges = NumberRangeFormatter.withLocale(ULocale.ENGLISH)
    .numberFormatterBoth(NumberFormatter.with()
        .unit(MeasureUnit.METER)
        .unitWidth(UnitWidth.FULL_NAME))
println(ranges.formatRange(1, 2)) // 1–2 meters
```


ICU binary bundles support typed lookup, aliases and locale/path fallback:

```kotlin
import io.github.heapy.icu4k.impl.ICUData
import io.github.heapy.icu4k.impl.ICUResourceBundle

val bundle = ICUResourceBundle.getBundleInstance(
    ICUData.ICU_BRKITR_BASE_NAME, "root", disableFallback = true,
)
println(bundle.getWithFallback("dictionaries/Mymr").getString()) // burmesedict.dict
```

Locale availability lists come from the pinned ICU indexes; not all listed locale
payloads are embedded yet. The embedded subset is listed in `data/manifest.json`.
`ICUResourceLoader` can supply additional binary bundles on every target.
`UResourceBundle.getBundleInstance` selects ICU or Java-style bundles from the
root bundle type and preserves ICU4J's cache and fallback rules.
`ICUResourceBundle.getFullLocaleNameSet` supports resource visitation, list-file and
index fallback; JVM callers can use `ICUClassLoaderResourceLoader` for directories
and JAR resources.

ICU CmnD `.dat` packages can be opened in Common code with
`ICUDataPackage.open(BinaryBuffer(datBytes))`. The package exposes item lookup and
`asResourceLoader()` for binary bundles. It borrows the supplied storage; keep the
validated package buffer's position and bounds unchanged. `ICUBinary.dataPath` is scanned once during ICUBinary initialization; external
files and packages precede embedded resources. JVM uses a system property; Native
and Node use the environment variable `com.ibm.icu.impl.ICUBinary.dataPath`. JVM
and Native retain mmap storage; Node retains file descriptors for live reads.
Browser JS uses embedded or caller-supplied resources.

`URLHandler` shares file/archive guide rules in Common code. `forFile(path)` uses
the host filesystem; `forArchive(prefix, entries)` uses a caller-supplied archive
iterator. On JVM, the `URLHandler.get(URL)` extension resolves file/JAR/wsjar URLs
and custom static factories from `urlhandler.props`;
`ICUClassLoaderResourceLoader` uses that registry when enumerating resources.
Java factories return the ported `URLHandler` type and expose `get(java.net.URL)`.

`ResourceBundleWrapper.getBundleInstance(baseName, localeID, loader, disableFallback)`
loads Java-style bundles through Common object/property providers. On JVM,
`ICUClassLoaderResourceLoader` also loads actual Java resource-bundle classes and
classpath `.properties`. The default ICU loader includes the pinned root and pool
bundles. ULocale defaults and DISPLAY/FORMAT categories are mutable on every target; JVM
defaults synchronize with java.util.Locale. JVM extensions `forLocale` and
`toLocale` bridge Java locale identities. JVM resource extensions accept Java
`Locale` and `ClassLoader`, expose `getLocale()`, and provide ICUResourceBundle's
Java locale availability lists. Import the corresponding `util`/`impl` extensions.
Remaining locale services and Java callable facade compatibility are still being ported.


`ULocale.addLikelySubtags` and `ULocale.minimizeSubtags` use the Common CLDR trie
engine and pinned `langInfo.res`. For example, `ULocale.addLikelySubtags(ULocale("en"))`
is `en_Latn_US`. Both minimization strategies preserve variants and keywords.
`ULocale.canonicalize` and `ULocale.createCanonical` apply CLDR language, script,
territory, variant, subdivision and transformed-extension aliases from metadata.
For example, `ULocale.createCanonical("sh_RS")` is `sr_Latn_RS`.


`Region` provides pinned territory IDs, numeric/three-letter aliases, deprecated
preferred values, and geographic/grouping containment in Common code. For example,
`Region.getInstance("IT").getContainingRegion(Region.RegionType.CONTINENT)` is `150`.


`LocaleData` exposes exemplar sets, quotation delimiters, display patterns,
measurement systems, paper sizes and the CLDR version. Core and language bundles
for English, French, Arabic and Japanese are embedded with their root/pool data.
The complete locale payload set is still being ported.


`SimpleFormatter` compiles ICU numbered patterns and supports apostrophe quoting,
argument offsets and append/replace formatting, including use of the result builder
as an argument during replacement. Example: `SimpleFormatter.compile("{1} in {0}")`.


`FormattedStringBuilder` supplies centered UTF-16 edits with per-code-unit fields,
and `SimpleFormatterImpl.formatPrefixSuffix` applies compiled wrappers to a span.
`FormatField` uses Java Format.Field on JVM; extracted `NumberFormatField` identities
support numeric annotations while the full NumberFormat implementation is pending.


`ConstrainedFieldPosition` carries field/class/value constraints and iteration state
in Common code. `UFieldPosition` retains fraction-digit metadata; its JVM base is
java.text.FieldPosition, with portable index/attribute carriers on JS and Native.
Formatted-value span iteration and exports are still being ported.


`StaticUnicodeSets` loads frozen CLDR parse symbols, separator/digit unions and the
formatting trim sets from Common resources. These support upcoming span iteration
and number parsing.

ICU's legacy decimal arithmetic is also available in Common Kotlin:

```kotlin
import io.github.heapy.icu4k.math.BigDecimal
import io.github.heapy.icu4k.math.MathContext

val result = BigDecimal("1").divide(BigDecimal("7"), MathContext(9))
println(result) // 0.142857143
```

`math.BigDecimal` preserves ICU contexts and representation-sensitive equality;
`util.BigDecimal` is the separate standard decimal interchange type.

The legacy `DecimalFormat` API is available with explicit symbols:

```kotlin
import io.github.heapy.icu4k.text.DecimalFormat
import io.github.heapy.icu4k.text.DecimalFormatSymbols
import io.github.heapy.icu4k.util.ULocale

val decimal = DecimalFormat("#,##0.00", DecimalFormatSymbols(ULocale("en_US")))
println(decimal.format(1234.5)) // 1,234.50
println(decimal.parse("1,234.50")) // 1234.5
```

`RuleBasedNumberFormat` exposes the common rule engine and predefined ICU rule data:

```kotlin
import io.github.heapy.icu4k.text.RuleBasedNumberFormat
import io.github.heapy.icu4k.util.ULocale

val words = RuleBasedNumberFormat(ULocale.ENGLISH, RuleBasedNumberFormat.SPELLOUT)
println(words.format(1234L)) // one thousand two hundred thirty-four
println(words.parse("twenty-one")) // 21
```

Custom descriptions, named rule sets, localized names, capitalization, rounding,
large-number dispatch and shallow cloning are supported. JVM object streams use
the source custom serialization protocol. JVM `Locale` conveniences are Kotlin
factory functions; Java constructor/typed-overload parity is still incremental.
Native subclasses follow the existing typed-clone convention.

The default collation-backed lenient scanner is available on all targets.
Enable it with `words.setLenientParseMode(true)` to accept primary-equivalent
spellings and the locale's lenient separators. Custom
`RbnfLenientScannerProvider` implementations can still be supplied. ICU data
outside the embedded subset is still being ported.

`MessagePattern` parses message, choice, plural and select patterns in Common Kotlin:

```kotlin
import io.github.heapy.icu4k.text.MessagePattern

val message = MessagePattern("{n,plural,one{# file}other{# files}}")
for (i in 0..<message.countParts()) {
    println(message.getPart(i))
}
```

The parser exposes syntax parts and nested-message boundaries; rendering with
`MessageFormat` and `PluralFormat` is still being ported.

`SelectFormat` selects a message by keyword and falls back to `other`:

```kotlin
import io.github.heapy.icu4k.text.SelectFormat

val selection = SelectFormat("female{She} male{He} other{They}")
println(selection.format("female")) // She
println(selection.format("unknown")) // They
```

Nested message arguments are returned as pattern text, as in ICU4J.

Localized `NumberFormat` factories also run in Common Kotlin:

```kotlin
import io.github.heapy.icu4k.text.NumberFormat
import io.github.heapy.icu4k.util.ULocale

val decimal = NumberFormat.getNumberInstance(ULocale("en_US"))
val roman = NumberFormat.getInstance(ULocale("en@numbers=roman"))
println(decimal.format(1234.5)) // 1,234.5
println(roman.format(2026L))   // MMXXVI
```

Plural messages are available through `PluralFormat`:

```kotlin
import io.github.heapy.icu4k.text.PluralFormat

val files = PluralFormat(ULocale.ENGLISH, "one{# file} other{# files}")
println(files.format(2.0)) // 2 files
```

Locale-sensitive string casing is available through `UCharacter`:

```kotlin
import io.github.heapy.icu4k.lang.UCharacter

println(UCharacter.toUpperCase(ULocale("tr"), "istanbul")) // İSTANBUL
println(UCharacter.toTitleCase(ULocale("nl"), "ijsselmeer", null)) // IJsselmeer
```

Rule-based collation is available through the public Common Kotlin API:

```kotlin
import io.github.heapy.icu4k.text.RuleBasedCollator

val collator = RuleBasedCollator("&c<ch<d")
println(collator.compare("ch", "d")) // -1
collator.setNumericCollation(true)
println(collator.compare("2", "10")) // -1
val key = collator.getCollationKey("ch")!!
val elements = collator.getCollationElementIterator("ch")
```

The rule constructor, attributes, sort keys, CE iteration, freeze and cloning are
implemented. Locale factories, service registration and locale enumeration are
also available:

```kotlin
import io.github.heapy.icu4k.text.Collator
import io.github.heapy.icu4k.util.ULocale

val german = Collator.getInstance(ULocale("de@collation=phonebook"))
val numeric = Collator.getInstance(ULocale("en@colNumeric=yes"))
println(numeric.compare("2", "10")) // -1
```

`setReorderCodes` accepts both a nullable
`IntArray` and Kotlin varargs; the JVM vararg convenience is named
`setReorderCodesValues`, while the array method retains `setReorderCodes`.

Locale-sensitive substring search is available through `StringSearch`:

```kotlin
import io.github.heapy.icu4k.text.StringSearch
import io.github.heapy.icu4k.text.StringCharacterIterator

val search = StringSearch("ana", StringCharacterIterator("banana"), german)
search.setOverlapping(true)
println(search.first()) // 1
println(search.next()) // 3
```

After changing a search's shared collator, call `search.reset()` before searching
again, as required by ICU4J's StringSearch contract. Known source edge cases and
verification limits are recorded in [the porting log](docs/PORTING.md).

`AlphabeticIndex` groups records using locale-sensitive labels and collation:

```kotlin
import io.github.heapy.icu4k.text.AlphabeticIndex

val index = AlphabeticIndex<Int>(ULocale.ENGLISH)
index.addRecord("Alice", 1).addRecord("Bob", 2)
val snapshot = index.buildImmutableIndex() // labels and boundaries, without records
val bucket = snapshot.getBucket(snapshot.getBucketIndex("Bob"))!!
println(bucket.getLabel()) // B
```

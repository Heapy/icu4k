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

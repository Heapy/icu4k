// SPDX-License-Identifier: Unicode-3.0
@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.ICUResourceBundleReader
import io.github.heapy.icu4k.math.BigDecimal
import io.github.heapy.icu4k.util.ULocale
import kotlin.test.*

class RbnfFormatterCoreTest {
    private fun core(description: String?, localizations: Array<Array<String>>? = null) = RbnfFormatterCore.fromDescription(
        description, localizations, ULocale.ENGLISH, scannerProviderFactory = { error("unexpected scanner discovery") })
    @Test fun initializationOrderDefaultsAndRuleVisibility() {
        val f = core(" ; %one: 0: first; ; %two@noparse: 0: second; %%private: 0: hidden; ")
        assertContentEquals(arrayOf("%two", "%one"), f.getRuleSetNames())
        assertEquals("%two", f.getDefaultRuleSetName())
        assertEquals("second", f.format(1L))
        f.setDefaultRuleSet("%one")
        assertEquals("first", f.format(1L))
        f.setDefaultRuleSet(null)
        assertEquals("second", f.format(1L))
        val pp = ParsePosition(2).also { it.errorIndex = 77 }
        assertEquals(0L, f.parse("xxfirst!", pp))
        assertEquals(7, pp.index)
        assertEquals(77, pp.errorIndex)
        pp.index = 2
        assertEquals(0L, f.parse("xxsecond", pp)) // @noparse is excluded.
        assertEquals(2, pp.index)
        assertEquals(1, pp.errorIndex) // Source starts the error high-water mark at -1.
        assertFailsWith<IllegalArgumentException> { f.format(1L, "%%private") }
        assertFailsWith<IllegalArgumentException> { f.setDefaultRuleSet("%%private") }
        assertFailsWith<IndexOutOfBoundsException> { f.parse("x", ParsePosition(2)) }
        val preferred = core("%duration: 0: duration; %digits-ordinal: 0: ordinal; %spellout-numbering: 0: spellout; %last: 0: last;")
        assertEquals("%digits-ordinal", preferred.getDefaultRuleSetName())
        preferred.setDefaultRuleSet(null)
        assertEquals("%last", preferred.getDefaultRuleSetName())
        val private = core("%%private: 0: hidden;")
        assertEquals("hidden", private.format(0L))
        private.setDefaultRuleSet(null)
        assertFailsWith<NullPointerException> { private.format(0L) }
        assertEquals("-9,223,372,036,854,775,808", private.format(Long.MIN_VALUE))
    }
    @Test fun localizedNamesFallbackOrderingAndOwnership() {
        val data = arrayOf(arrayOf("%one", "%two"), arrayOf("en", "One", "Two"), arrayOf("fr", "Un", "Deux"), arrayOf("", "Root one", "Root two"))
        val f = core("%one: 0: one; %two: 0: two;", data)
        assertEquals("%one", f.getDefaultRuleSetName())
        data[0][0] = "%invalid"; data[1][1] = "mutated"
        assertContentEquals(arrayOf("One", "Two"), f.getRuleSetDisplayNames(ULocale("en_US")))
        assertContentEquals(arrayOf("Un", "Deux"), f.getRuleSetDisplayNames(ULocale("fr_CA")))
        assertContentEquals(arrayOf("one", "two"), f.getRuleSetDisplayNames(null))
        assertEquals("Deux", f.getRuleSetDisplayName("%two", ULocale.FRENCH))
        assertEquals("two", f.getRuleSetDisplayName("%two", null))
        assertFailsWith<IllegalArgumentException> { f.getRuleSetDisplayName(null, ULocale.ENGLISH) }
        f.getRuleSetNames()[0] = "changed"
        f.getRuleSetDisplayNames(ULocale.ENGLISH)[0] = "changed"
        assertEquals("One", f.getRuleSetDisplayName("%one", ULocale.ENGLISH))
        assertContentEquals(arrayOf("", "en", "fr"), f.getRuleSetDisplayNameLocales()!!.map { it.getName() }.toTypedArray())
        assertFailsWith<IndexOutOfBoundsException> { core("0: text", emptyArray()) }
        assertFailsWith<IndexOutOfBoundsException> { core("0: text", arrayOf(emptyArray())) }
        assertFailsWith<IllegalArgumentException> { core("0: text", arrayOf(arrayOf("%default"), arrayOf("en"))) }
        // Localization validation precedes parsing a null description.
        assertFailsWith<IndexOutOfBoundsException> { core(null, emptyArray()) }
    }
    @Test fun scannerDiscoveryIsLazyOneShotAndPassesExactExtractedRules() {
        var attempts = 0
        val f = RbnfFormatterCore.fromDescription("%%lenient-parse: extras; more; %main: 0: zero;", locale = ULocale.ENGLISH,
            scannerProviderFactory = { ++attempts; throw IllegalStateException("unavailable") })
        assertNull(f.getLenientScannerProvider())
        assertEquals(0, attempts)
        f.setLenientParseMode(true)
        assertNull(f.getLenientScannerProvider())
        f.setLenientParseMode(false); f.setLenientParseMode(true)
        assertNull(f.getLenientScannerProvider())
        assertEquals(1, attempts)
        var requests = 0
        f.setLenientScannerProvider(object : RbnfLenientScannerProvider {
            override fun get(locale: ULocale?, extras: String?): RbnfLenientScanner? {
                ++requests
                assertEquals(ULocale.ENGLISH, locale)
                assertEquals("extras;more", extras)
                return null
            }
        })
        f.getLenientScanner(); f.getLenientScanner()
        assertEquals(2, requests)
        f.setLenientParseMode(false)
        f.getLenientScanner()
        assertEquals(2, requests)
    }
    @Test fun postProcessorIsLazySharedOnCloneAndDiscoveryFailureIsSuppressedOnce() {
        val events = ArrayList<String>()
        val f = RbnfFormatterCore.fromDescription("%%post-process: processor; args; %main: 0: raw;", locale = ULocale.ENGLISH,
            scannerProviderFactory = { error("unused") }, postProcessorFactory = { name ->
                events.add("create:$name")
                object : RbnfPostProcessor {
                    var count = 0
                    override fun init(formatter: RbnfFormatterCore, rules: String) { events.add("init:$rules") }
                    override fun process(output: StringBuilder, ruleSet: RbnfRuleSetCore?) { output.append(++count).append(ruleSet!!.getName()) }
                }
            })
        assertTrue(events.isEmpty())
        assertEquals("raw1%main", f.format(0L))
        assertEquals(listOf("create:processor", "init:processor;args"), events)
        assertEquals("raw2%main", f.copy().format(0L))
        assertEquals("raw3%main", f.format(0L))
        var failures = 0
        val failed = RbnfFormatterCore.fromDescription("%%post-process: missing; %main: 0: raw;", locale = ULocale.ENGLISH,
            scannerProviderFactory = { error("unused") }, postProcessorFactory = { ++failures; throw IllegalStateException("missing") })
        assertEquals("raw", failed.format(0L)); assertEquals("raw", failed.format(0L)); assertEquals(1, failures)
        assertEquals("〇", core("%%post-process: com.ibm.icu.text.RBNFChinesePostProcessor; %simplified: 0: *〇;").format(0L))
    }
    @Test fun shallowCloneRetainsGraphOwnerAndSymbolSelectionPolicy() {
        val f = core("%main: x.x: dot; x,x: comma; 0: zero;")
        val originalSymbols = f.getDecimalFormatSymbols()
        val oldInfinity = f.getDefaultInfinityRule()
        val decimal = f.getDecimalFormat()
        val clone = f.copy()
        assertSame(f.getDefaultRuleSet(), clone.getDefaultRuleSet())
        assertSame(decimal, clone.getDecimalFormat())
        val symbols = DecimalFormatSymbols(ULocale.ENGLISH).also { it.setDecimalSeparator(','); it.setInfinity("infinite") }
        clone.setDecimalFormatSymbols(symbols)
        symbols.setInfinity("changed")
        assertSame(originalSymbols, f.getDecimalFormatSymbols())
        assertEquals("infinite", clone.getDecimalFormatSymbols().getInfinity())
        assertNotSame(oldInfinity, clone.getDefaultInfinityRule())
        assertEquals("dot", clone.format(0.5) { 3 }) // Shared rules consult the original formatter's symbols.
        f.setDecimalFormatSymbols(DecimalFormatSymbols(ULocale.ENGLISH).also { it.setDecimalSeparator(',') })
        assertEquals("comma", clone.format(0.5) { 3 })
        assertTrue(f.sameRulesAs(clone))
        clone.setLenientParseMode(true)
        assertFalse(f.sameRulesAs(clone))
    }
    @Test fun outerRoundingUsesVirtualQueryButStoredModeAndSkipsLongFallback() {
        val f = core("%main: 0: =0.00=;")
        var reads = 0
        f.setRoundingMode(BigDecimal.ROUND_HALF_UP)
        assertEquals("1.24", f.format(1.235) { ++reads; 2 })
        assertEquals(1, reads)
        f.setRoundingMode(BigDecimal.ROUND_UNNECESSARY)
        f.roundingModeReader = { BigDecimal.ROUND_FLOOR }
        assertFailsWith<ArithmeticException> { f.format(1.235) { 2 } }
        f.roundingModeReader = { error("virtual query") }
        assertFailsWith<IllegalStateException> { f.format(Double.NaN) { error("unused") } }
        assertEquals("-9,223,372,036,854,775,808", f.format(Long.MIN_VALUE))
    }
    @Test fun predefinedGraphUsesOnlyEmbeddedResources() {
        val metadata = ArrayList<ULocale>()
        val f = RbnfFormatterCore.fromLocale(ULocale("en_US"), 1, { error("unused") }, ICUResourceBundleReader.embeddedLoader, { metadata.add(it) })
        assertEquals(listOf(ULocale.ENGLISH), metadata)
        assertEquals("one thousand two hundred thirty-four", f.format(1234L))
        assertEquals("twenty-one", f.format(21L, "%spellout-numbering"))
        val pp = ParsePosition(0)
        assertEquals(1234L, f.parse("one thousand two hundred thirty-four", pp))
        assertEquals("one thousand two hundred thirty-four".length, pp.index)
    }
}

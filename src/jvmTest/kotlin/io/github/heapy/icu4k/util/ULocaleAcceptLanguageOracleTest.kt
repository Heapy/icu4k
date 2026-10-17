// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.util

import com.ibm.icu.util.ULocale as Source
import kotlin.test.*

class ULocaleAcceptLanguageOracleTest {
    private fun outcome(action: () -> Any?): Any? = try { action() } catch (e: Exception) {
        listOf(e.javaClass.simpleName, if (e is NullPointerException) null else e.message)
    }
    @Test fun headersArraysFallbackFlagsAndValidationOrder() {
        val available = listOf<List<String?>?>(listOf("en_US", "fr_FR", "de_DE"), listOf("fr_CA", "en_GB"), emptyList(), listOf("und", "root"), listOf(null), null)
        val headers = listOf<String?>("en-US", "en-AU, fr-CA", "fr;q=0.8, en;q=1", "ja", "", "*", "*;q=0", "en;q=bogus", "en;q=-1", "en;q=2", "und", "@x=abc", null)
        for (availableNames in available) for (header in headers) for (length in listOf<Int?>(null, 0, 1, 2)) {
            val sf = length?.let { BooleanArray(it) }; val pf = sf?.copyOf()
            val sa = availableNames?.map { it?.let(::Source) }?.toTypedArray()
            val pa = availableNames?.map { it?.let(::ULocale) }?.toTypedArray()
            assertEquals(outcome { Source.acceptLanguage(header, sa, sf)?.toString() }, outcome { ULocale.acceptLanguage(header, pa, pf)?.toString() }, "$availableNames/$header/$length")
            assertContentEquals(sf, pf)
        }
        val desired = listOf<List<String?>?>(emptyList(), listOf("en_US"), listOf("en", "fr"), listOf("ja", "fr_CA"), listOf("en_US", null), listOf(null), null)
        for (availableNames in available) for (desiredNames in desired) for (length in listOf<Int?>(null, 0, 1, 2)) {
            val sf = length?.let { BooleanArray(it) }; val pf = sf?.copyOf()
            val sa = availableNames?.map { it?.let(::Source) }?.toTypedArray()
            val pa = availableNames?.map { it?.let(::ULocale) }?.toTypedArray()
            val sd = desiredNames?.map { it?.let(::Source) }?.toTypedArray()
            val pd = desiredNames?.map { it?.let(::ULocale) }?.toTypedArray()
            assertEquals(outcome { Source.acceptLanguage(sd, sa, sf)?.toString() }, outcome { ULocale.acceptLanguage(pd, pa, pf)?.toString() }, "$availableNames/$desiredNames/$length")
            assertContentEquals(sf, pf)
        }
    }
    @Test fun availableLocaleOverloadsAndReturnedIdentity() {
        for (header in listOf("en-US", "fr-CA", "sr-Latn-RS", "zh-HK", "gsw-CH", "und", "ja;q=0.5,en-AU;q=1", "", "en;q=bogus")) {
            val sf = booleanArrayOf(false); val pf = booleanArrayOf(false)
            assertEquals(Source.acceptLanguage(header, sf)?.toString(), ULocale.acceptLanguage(header, pf)?.toString(), header)
            assertContentEquals(sf, pf)
            val desired = header.substringBefore(';').substringBefore(',')
            val sfa = booleanArrayOf(false); val pfa = booleanArrayOf(false)
            assertEquals(Source.acceptLanguage(arrayOf(Source(desired)), sfa)?.toString(), ULocale.acceptLanguage(arrayOf(ULocale(desired)), pfa)?.toString(), header)
            assertContentEquals(sfa, pfa)
        }
        val locale = ULocale("en_US")
        assertSame(locale, ULocale.acceptLanguage("en-US", arrayOf(locale), null))
        assertSame(locale, ULocale.acceptLanguage(arrayOf(ULocale("en_US")), arrayOf(locale), null))
    }
}

@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.text.CurrencyDisplayNames
import io.github.heapy.icu4k.text.ParsePosition
import io.github.heapy.icu4k.util.ULocale
import kotlin.test.*

class CurrencyParsingOracleTest {
    @Test fun allLoadedNamesAndSymbolsMatchSource() {
        for (id in listOf("en", "fr", "de", "ja", "ar", "he", "es", "zh", "root", "en_US", "fr_CA")) {
            val locale = ULocale(id)
            val original = com.ibm.icu.util.ULocale(id)
            val names = CurrencyDisplayNames.getInstance(locale)
            val inputs = (names.symbolMap().keys + names.nameMap().keys).flatMap { listOf(it, it.uppercase(), it + "!") } + listOf("?", "", "¥", "￥", "$", "＄")
            for (input in inputs) for (type in listOf(0, 1, 2, -1)) for (prefix in listOf("", "x ")) {
                val j = java.text.ParsePosition(prefix.length).also { it.errorIndex = 17 }
                val k = ParsePosition(prefix.length).also { it.errorIndex = 17 }
                assertEquals(com.ibm.icu.util.Currency.parse(original, prefix + input, type, j), CurrencyParsing.parse(locale, prefix + input, type, k), "$id/$type/$input")
                assertEquals(j.index, k.index); assertEquals(j.errorIndex, k.errorIndex)
            }
        }
    }
    @Test fun trieValuesAndMutableCachedTriesMatch() {
        val locale = ULocale("en")
        for (type in listOf(0, 1, 2)) {
            val j = com.ibm.icu.util.Currency.getParsingTrie(com.ibm.icu.util.ULocale("en"), type)
            val k = CurrencyParsing.getParsingTrie(locale, type)
            for (input in listOf("USD", "US dollar", "¥", "$", "?")) {
                val a = j.get(input)?.asSequence()?.map { it.getISOCode() to it.getCurrencyString() }?.toList()
                val b = k.get(input)?.asSequence()?.map { it.getISOCode() to it.getCurrencyString() }?.toList()
                assertEquals(a, b)
            }
            assertSame(k, CurrencyParsing.getParsingTrie(locale, type))
        }
    }
    @Test fun mutableTrieTiesAndInvalidOffsetsMatch() {
        val locale = ULocale("en")
        val original = com.ibm.icu.util.ULocale("en")
        val marker = "icu4k currency oracle marker"
        for ((type, code, key) in listOf(Triple(1, "NAME", marker), Triple(0, "SYM", marker), Triple(0, "LONG", marker + "!"))) {
            com.ibm.icu.util.Currency.getParsingTrie(original, type).put(key, com.ibm.icu.util.Currency.CurrencyStringInfo(code, key))
            CurrencyParsing.getParsingTrie(locale, type).put(key, CurrencyStringInfo(code, key))
        }
        try {
            for (input in listOf(marker, marker + "!", "?", "")) for (type in listOf(0, 1, 999)) for (offset in listOf(-1, 0, input.length, input.length + 1)) {
                val j = java.text.ParsePosition(offset).also { it.errorIndex = 31 }
                val k = ParsePosition(offset).also { it.errorIndex = 31 }
                fun outcome(block: () -> Any?): Any? = try { block() } catch (failure: Exception) { failure.javaClass.simpleName }
                assertEquals(outcome { com.ibm.icu.util.Currency.parse(original, input, type, j) }, outcome { CurrencyParsing.parse(locale, input, type, k) })
                assertEquals(j.index, k.index); assertEquals(j.errorIndex, k.errorIndex)
            }
            assertEquals("NAME", CurrencyParsing.parse(locale, marker, 0, ParsePosition(0)))
            assertEquals("LONG", CurrencyParsing.parse(locale, marker + "!", 0, ParsePosition(0)))
        } finally {
            for ((type, key) in listOf(1 to marker, 0 to marker, 0 to marker + "!")) {
                val j = com.ibm.icu.util.Currency.getParsingTrie(original, type).get(key)
                while (j.hasNext()) { j.next(); j.remove() }
                val k = CurrencyParsing.getParsingTrie(locale, type).get(key)!!
                while (k.hasNext()) { k.next(); k.remove() }
            }
        }
    }

}

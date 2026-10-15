@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.impl.number

import com.ibm.icu.impl.number.DecimalFormatProperties as JProperties
import com.ibm.icu.impl.number.PatternStringParser as JParser
import com.ibm.icu.impl.number.PropertiesAffixPatternProvider as JProvider
import com.ibm.icu.impl.number.AffixPatternProvider as JAffix
import com.ibm.icu.text.CurrencyPluralInfo as JInfo
import com.ibm.icu.util.ULocale as JLocale
import io.github.heapy.icu4k.text.CurrencyPluralInfo
import io.github.heapy.icu4k.util.ULocale
import kotlin.test.*

class PropertiesAffixPatternProviderOracleTest {
    private fun compare(expected: JAffix, actual: AffixPatternProvider, plurals: IntRange = 0..0) {
        assertEquals(expected.positiveHasPlusSign(), actual.positiveHasPlusSign())
        assertEquals(expected.hasNegativeSubpattern(), actual.hasNegativeSubpattern())
        assertEquals(expected.negativeHasMinusSign(), actual.negativeHasMinusSign())
        assertEquals(expected.hasCurrencySign(), actual.hasCurrencySign())
        assertEquals(expected.hasBody(), actual.hasBody())
        assertEquals(expected.currencyAsDecimal(), actual.currencyAsDecimal())
        for (type in -12..0) assertEquals(expected.containsSymbolType(type), actual.containsSymbolType(type))
        for (plural in plurals) for (flags in listOf(0, 0x100, 0x200, 0x300, 0x700)) {
            val f = flags or plural
            assertEquals(expected.getString(f), actual.getString(f))
            assertEquals(expected.length(f), actual.length(f))
            for (i in 0 until expected.length(f)) assertEquals(expected.charAt(f, i), actual.charAt(f, i))
            assertFailsWith<IndexOutOfBoundsException> { actual.charAt(f, -1) }
            assertFailsWith<IndexOutOfBoundsException> { actual.charAt(f, actual.length(f)) }
        }
    }

    @Test fun patternsAndIndependentOverrides() {
        val patterns = listOf("0", "+0%", "¤0.00;(¤0.00)", "'x'0'y';-x0y", "0‰", "0¤00")
        val overrides = listOf(null, "", "-+¤%'", "😀")
        for (pattern in patterns) for (field in listOf("PositivePrefix", "PositiveSuffix", "NegativePrefix", "NegativeSuffix")) {
            for (override in overrides) {
                val j = JParser.parseToProperties(pattern)
                val k = PatternStringParserCore.parseToDecimalFormatProperties(pattern)
                JProperties::class.java.getMethod("set$field", String::class.java).invoke(j, override)
                DecimalFormatProperties::class.java.getMethod("set$field", String::class.java).invoke(k, override)
                val expected = JProvider.forProperties(j)
                val actual = PropertiesAffixPatternProvider.forProperties(k)
                compare(expected, actual)
                j.clear(); k.clear()
                compare(expected, actual) // Providers retain snapshots.
            }
        }
    }

    @Test fun suffixReferenceIdentity() {
        val a = String(charArrayOf('x'))
        val b = String(charArrayOf('x'))
        assertNotSame(a, b)
        for (suffix in listOf(a, b)) {
            val j = JProperties().setPositiveSuffixPattern(a).setNegativeSuffixPattern(suffix)
            val k = DecimalFormatProperties().setPositiveSuffixPattern(a).setNegativeSuffixPattern(suffix)
            compare(JProvider.forProperties(j), PropertiesAffixPatternProvider.forProperties(k))
        }
    }

    @Test fun pluralPatternsAndOverrides() {
        for (locale in listOf("en", "ru", "ar", "fr")) for (override in listOf(null, "", "¤custom'")) {
            val ji = JInfo(JLocale(locale))
            val ki = CurrencyPluralInfo(ULocale(locale))
            ji.setCurrencyPluralPattern("one", "+0 ¤¤¤;(0 ¤¤¤)")
            ki.setCurrencyPluralPattern("one", "+0 ¤¤¤;(0 ¤¤¤)")
            val j = JProperties().setCurrencyPluralInfo(ji).setPositivePrefix(override)
            val k = DecimalFormatProperties().setCurrencyPluralInfo(ki).setPositivePrefix(override)
            val expected = JProvider.forProperties(j)
            val actual = PropertiesAffixPatternProvider.forProperties(k)
            compare(expected, actual, 0..7)
            ji.setCurrencyPluralPattern("other", "0%")
            ki.setCurrencyPluralPattern("other", "0%")
            compare(expected, actual, 0..7)
            assertFailsWith<IndexOutOfBoundsException> { actual.getString(255) }
        }
        assertFailsWith<NullPointerException> { PropertiesAffixPatternProvider.forProperties(null) }
    }
}

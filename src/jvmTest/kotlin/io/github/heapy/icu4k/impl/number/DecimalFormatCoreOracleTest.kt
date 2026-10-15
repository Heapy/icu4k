// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.number

import com.ibm.icu.text.DecimalFormat as JFormat
import com.ibm.icu.text.DecimalFormatSymbols as JSymbols
import com.ibm.icu.text.CurrencyPluralInfo as JPluralInfo
import com.ibm.icu.text.UFieldPosition as JPosition
import com.ibm.icu.impl.number.DecimalFormatProperties as JProperties
import com.ibm.icu.math.BigDecimal as JLegacyDecimal
import com.ibm.icu.util.ULocale as JLocale
import io.github.heapy.icu4k.text.*
import io.github.heapy.icu4k.util.*
import io.github.heapy.icu4k.math.BigDecimal as LegacyDecimal
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.test.*

class DecimalFormatCoreOracleTest {
    private fun patterns(j: JFormat, k: DecimalFormatCore) {
        assertEquals(j.toPattern(), k.toPattern())
        assertEquals(j.toLocalizedPattern(), k.toLocalizedPattern())
        assertEquals(j.getDecimalFormatSymbols().currencySymbol, k.getDecimalFormatSymbols().getCurrencySymbol())
    }
    private fun format(j: JFormat, k: DecimalFormatCore, jNumber: Number, kNumber: Number = jNumber) {
        for (field in listOf(0, 1, -1, 7)) for (prefix in listOf("", "prefix:")) {
            val jp = JPosition(field); val kp = UFieldPosition(field)
            jp.beginIndex = 99; jp.endIndex = 99; kp.setBeginIndex(99); kp.setEndIndex(99)
            val jb = StringBuffer(prefix); val kb = StringBuilder(prefix)
            val jr = runCatching { j.format(jNumber, jb, jp) }; val kr = runCatching { k.format(kNumber, kb, kp) }
            assertEquals(jr.exceptionOrNull()?.javaClass, kr.exceptionOrNull()?.javaClass)
            assertEquals(jb.toString(), kb.toString(), "number=$jNumber")
            assertEquals(jp.beginIndex, kp.getBeginIndex()); assertEquals(jp.endIndex, kp.getEndIndex())
            assertEquals(jp.countVisibleFractionDigits, kp.getCountVisibleFractionDigits()); assertEquals(jp.fractionDigits, kp.getFractionDigits())
            if (jr.isSuccess) { assertSame(jb, jr.getOrThrow()); assertSame(kb, kr.getOrThrow()) }
        }
    }
    private fun number(value: Number?): String = when (value) {
        is Double -> "double:${value.toBits()}"
        is JLegacyDecimal -> "decimal:$value/${value.scale()}"
        is LegacyDecimal -> "decimal:$value/${value.scale()}"
        is Long -> "long:$value"
        null -> "null"
        else -> "${value.javaClass}:$value"
    }
    private fun parse(j: JFormat, k: DecimalFormatCore, text: String?, index: Int) {
        val jp = java.text.ParsePosition(index).also { it.errorIndex = 99 }; val kp = ParsePosition(index).also { it.errorIndex = 99 }
        val jr = runCatching { j.parse(text, jp) }; val kr = runCatching { k.parse(text, kp) }
        assertEquals(jr.exceptionOrNull()?.javaClass, kr.exceptionOrNull()?.javaClass, "$text/$index")
        assertEquals(jr.exceptionOrNull()?.message, kr.exceptionOrNull()?.message, "$text/$index")
        assertEquals(number(jr.getOrNull()), number(kr.getOrNull()), "$text/$index")
        assertEquals(jp.index, kp.index, "$text/$index"); assertEquals(jp.errorIndex, kp.errorIndex, "$text/$index")
        val jc = runCatching { j.parseCurrency(text, jp) }; val kc = runCatching { k.parseCurrency(text, kp) }
        assertEquals(jc.exceptionOrNull()?.javaClass, kc.exceptionOrNull()?.javaClass, "$text/$index/currency")
        assertEquals(jc.exceptionOrNull()?.message, kc.exceptionOrNull()?.message, "$text/$index/currency")
        assertEquals(number(jc.getOrNull()?.number), number(kc.getOrNull()?.getNumber()), "$text/$index/currency")
        assertEquals(jc.getOrNull()?.currency?.currencyCode, kc.getOrNull()?.getCurrency()?.getCurrencyCode())
        assertEquals(jp.index, kp.index); assertEquals(jp.errorIndex, kp.errorIndex)
    }
    @Test fun patternsLocaleRoundingFormattingAndParsingMatrix() {
        val patterns = listOf("#,##0.00", "0.###E+00", "@@@##", "¤#,##0.0000;(¤#,##0.0000)", "0.05", "0%", "*x##0.0", "'a'0.0'b'")
        val values: List<Number> = listOf(0L, -12345L, Long.MIN_VALUE, 1.234567, -0.0, Double.NaN, Double.POSITIVE_INFINITY, BigInteger("18446744073709551617"), BigDecimal("-1234567890.123456789"))
        for (locale in listOf("en_US", "fr", "ar", "hi", "de_CH")) for (pattern in patterns) {
            val j = JFormat(pattern, JSymbols(JLocale(locale))); val k = DecimalFormatCore.create(pattern, DecimalFormatSymbols(ULocale(locale)))
            patterns(j, k)
            for (value in values) format(j, k, value)
            format(j, k, JLegacyDecimal("12345678901234567890.125"), LegacyDecimal("12345678901234567890.125"))
            for (text in listOf(null, "", "12.50", "xx12.50tail", "USD 12.50", "-0", "NaN", "∞", "1.23E+999999999", "1.23E-999999999", "1,234.50")) for (index in listOf(-1, 0, 2, 100)) parse(j, k, text, index)
        }
    }
    @Test fun propertyMutationPatternResetAndCloning() {
        val actions: List<Pair<(JProperties) -> Unit, (DecimalFormatProperties) -> Unit>> = listOf(
            ({ it: JProperties -> it.setPositivePrefix("literal+"); it.setNegativeSuffix("literal-"); Unit }) to ({ it: DecimalFormatProperties -> it.setPositivePrefix("literal+"); it.setNegativeSuffix("literal-"); Unit }),
            ({ it: JProperties -> it.setMinimumFractionDigits(4); it.setMaximumFractionDigits(4); Unit }) to ({ it: DecimalFormatProperties -> it.setMinimumFractionDigits(4); it.setMaximumFractionDigits(4); Unit }),
            ({ it: JProperties -> it.setParseToBigDecimal(true); it.setParseIntegerOnly(true); Unit }) to ({ it: DecimalFormatProperties -> it.setParseToBigDecimal(true); it.setParseIntegerOnly(true); Unit }),
            ({ it: JProperties -> it.setParseMode(JProperties.ParseMode.STRICT); Unit }) to ({ it: DecimalFormatProperties -> it.setParseMode(DecimalFormatProperties.ParseMode.STRICT); Unit }),
            ({ it: JProperties -> it.setCurrency(com.ibm.icu.util.Currency.getInstance("CHF")); it.setCurrencyUsage(com.ibm.icu.util.Currency.CurrencyUsage.CASH); Unit }) to ({ it: DecimalFormatProperties -> it.setCurrency(Currency.getInstance("CHF")); it.setCurrencyUsage(Currency.CurrencyUsage.CASH); Unit })
        )
        for ((jAction, kAction) in actions) {
            val j = JFormat("¤#,##0.00", JSymbols(JLocale.US)); val k = DecimalFormatCore.create("¤#,##0.00", DecimalFormatSymbols(ULocale("en_US")))
            j.setProperties { jAction(it) }; k.setProperties(kAction)
            patterns(j, k); format(j, k, BigDecimal("1234.567"))
            for (text in listOf("USD 12.50", "12.50", "CHF 1.23", "not a number")) parse(j, k, text, 0)
            val jCopy = j.clone() as JFormat; val kCopy = k.copy()
            assertEquals(j == jCopy, k == kCopy)
            assertNotSame(k.symbols, kCopy.symbols); assertNotSame(k.properties, kCopy.properties)
            patterns(jCopy, kCopy)
            jCopy.applyPattern("0.000%"); kCopy.applyPattern("0.000%")
            patterns(jCopy, kCopy); format(jCopy, kCopy, 1.2345)
            patterns(j, k); format(j, k, 1.2345)
            j.applyPattern("0.0"); k.applyPattern("0.0")
            patterns(j, k); format(j, k, 1.2345)
            j.applyLocalizedPattern("0.000"); k.applyLocalizedPattern("0.000")
            patterns(j, k); format(j, k, 1.2345)
        }
    }
    @Test fun currencyOverrideAndPluralStyleConstruction() {
        for (style in 0..9) {
            val j = JFormat("¤0.0000", JSymbols(JLocale.US), JPluralInfo(JLocale.US), style)
            val k = DecimalFormatCore.create("¤0.0000", DecimalFormatSymbols(ULocale("en_US")), CurrencyPluralInfo(ULocale("en_US")), style)
            patterns(j, k); format(j, k, 12.3456)
            for (code in listOf("USD", "EUR", "JPY", "CHF")) {
                val jb = StringBuffer("x:"); val kb = StringBuilder("x:")
                val jp = java.text.FieldPosition(1); val kp = FormatFieldPosition(1)
                j.format(com.ibm.icu.util.CurrencyAmount(BigDecimal("12.3456"), com.ibm.icu.util.Currency.getInstance(code)), jb, jp)
                k.format(CurrencyAmount(BigDecimal("12.3456"), Currency.getInstance(code)), kb, kp)
                assertEquals(jb.toString(), kb.toString()); assertEquals(jp.beginIndex, kp.getBeginIndex()); assertEquals(jp.endIndex, kp.getEndIndex())
            }
        }
    }
    @Test fun parserCacheResetAndFailedConfigurationKeepSourceOrdering() {
        val j = JFormat("0.00", JSymbols(JLocale.US)); val k = DecimalFormatCore.create("0.00", DecimalFormatSymbols(ULocale("en_US")))
        val jGetter = JFormat::class.java.getDeclaredMethod("getParser").also { it.isAccessible = true }
        val oldJ = jGetter.invoke(j); val oldK = k.getParser()
        assertSame(oldJ, jGetter.invoke(j)); assertSame(oldK, k.getParser())
        for (pattern in listOf<String?>(null, "0.0.0", "'unterminated")) {
            val je = runCatching { j.applyPattern(pattern) }.exceptionOrNull(); val ke = runCatching { k.applyPattern(pattern) }.exceptionOrNull()
            assertEquals(je?.javaClass, ke?.javaClass)
            if (je !is NullPointerException) assertEquals(je?.message, ke?.message)
            assertSame(oldJ, jGetter.invoke(j)); assertSame(oldK, k.getParser())
        }
        j.applyPattern("0.000"); k.applyPattern("0.000")
        assertNotSame(oldJ, jGetter.invoke(j)); assertNotSame(oldK, k.getParser())
        val nextJ = jGetter.invoke(j); val nextK = k.getParser()
        val je = runCatching { j.setProperties { it.setPositivePrefix("changed"); throw IllegalStateException("callback") } }.exceptionOrNull()
        val ke = runCatching { k.setProperties { it.setPositivePrefix("changed"); throw IllegalStateException("callback") } }.exceptionOrNull()
        assertEquals(je?.javaClass, ke?.javaClass); assertEquals(je?.message, ke?.message)
        assertSame(nextJ, jGetter.invoke(j)); assertSame(nextK, k.getParser())
        // The property mutation remains, but the published formatter has not been rebuilt.
        patterns(j, k); format(j, k, 1.2345)
        j.applyPattern("0.0"); k.applyPattern("0.0")
        patterns(j, k); format(j, k, 1.2345)
    }
}

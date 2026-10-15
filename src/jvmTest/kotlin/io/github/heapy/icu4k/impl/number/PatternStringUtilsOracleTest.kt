@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.impl.number

import com.ibm.icu.impl.number.PatternStringUtils as JUtils
import com.ibm.icu.impl.number.DecimalFormatProperties as JProps
import com.ibm.icu.text.DecimalFormatSymbols as JSymbols
import com.ibm.icu.text.CurrencyPluralInfo as JInfo
import com.ibm.icu.util.ULocale as JLocale
import io.github.heapy.icu4k.text.CurrencyPluralInfo
import io.github.heapy.icu4k.text.DecimalFormatSymbols
import io.github.heapy.icu4k.util.ULocale
import java.math.BigDecimal
import kotlin.test.*

class PatternStringUtilsOracleTest {
    private fun outcome(action: () -> Any?): Any? = try { action() } catch (e: Throwable) {
        if (e is NullPointerException) "null" else "${e.javaClass.simpleName}:${e.message}"
    }
    private fun increment(trace: MutableList<String>): BigDecimal = object : BigDecimal("0.050") {
        override fun toByte(): Byte = toInt().toByte()
        override fun toShort(): Short = toInt().toShort()
        override fun toDouble(): Double { trace.add("double"); return super.toDouble() }
        override fun scale(): Int { trace.add("scale"); return super.scale() }
        override fun scaleByPowerOfTen(n: Int): BigDecimal {
            trace.add("power:$n")
            val text = super.scaleByPowerOfTen(n).toPlainString()
            return object : BigDecimal(text) {
                override fun toByte(): Byte = toInt().toByte()
                override fun toShort(): Short = toInt().toShort()
                override fun toPlainString(): String { trace.add("plain"); return super.toPlainString() }
            }
        }
        override fun toString(): String = error("Unexpected decimal string conversion")
    }
    @Test fun virtualGetterAndDecimalCallbackOrder() {
        for (sig in listOf(-1, 3)) {
            val jt = mutableListOf<String>(); val kt = mutableListOf<String>()
            val j = object : JProps() {
                override fun getFormatWidth(): Int { jt.add("width"); return super.getFormatWidth() }
                override fun getCurrencyPluralInfo(): JInfo? { jt.add("plural"); return null }
                override fun getPositivePrefix(): String? { jt.add("prefix"); return null }
                override fun getCurrencyAsDecimal(): Boolean { jt.add("currency"); return false }
                override fun getRoundingIncrement(): BigDecimal { jt.add("rounding"); return increment(jt) }
            }.setMaximumFractionDigits(3).setMaximumSignificantDigits(sig)
            val k = object : DecimalFormatProperties() {
                override fun getFormatWidth(): Int { kt.add("width"); return super.getFormatWidth() }
                override fun getCurrencyPluralInfo(): CurrencyPluralInfo? { kt.add("plural"); return null }
                override fun getPositivePrefix(): String? { kt.add("prefix"); return null }
                override fun getCurrencyAsDecimal(): Boolean { kt.add("currency"); return false }
                override fun getRoundingIncrement(): BigDecimal { kt.add("rounding"); return increment(kt) }
            }.setMaximumFractionDigits(3).setMaximumSignificantDigits(sig)
            assertEquals(JUtils.propertiesToPatternString(j), PatternStringUtils.propertiesToPatternString(k))
            assertEquals(jt, kt)
        }
    }
    @Test fun nullSymbolFailureAfterAllGetterCalls() {
        val jt = mutableListOf<String>(); val kt = mutableListOf<String>()
        val j = object : JSymbols(JLocale.ENGLISH) {
            override fun getExponentSeparator(): String? { jt.add("exponent"); return null }
            override fun getPadEscape(): Char { jt.add("pad"); return super.getPadEscape() }
            override fun getDigitStringsLocal(): Array<String> { jt.add("digits"); return super.getDigitStringsLocal() }
        }
        val k = object : DecimalFormatSymbols(ULocale("en")) {
            override fun getExponentSeparator(): String? { kt.add("exponent"); return null }
            override fun getPadEscape(): Char { kt.add("pad"); return super.getPadEscape() }
            override fun getDigitStringsLocal(): Array<String> { kt.add("digits"); return super.getDigitStringsLocal() }
        }
        for (direction in listOf(false, true)) {
            jt.clear(); kt.clear()
            assertEquals(outcome { JUtils.convertLocalized("", j, direction) }, outcome { PatternStringUtils.convertLocalized("", k, direction) })
            assertEquals(jt, kt)
            assertEquals(10, kt.count { it == "digits" })
        }
        assertNull(PatternStringUtils.convertLocalized(null, null, true))
        assertFailsWith<NullPointerException> { PatternStringUtils.propertiesToPatternString(null) }
        assertFailsWith<NullPointerException> { PatternStringUtils.ignoreRoundingIncrement(null, 2) }
    }
    @Test fun currencyPluralPropertiesAndLiteralOverrides() {
        for (locale in listOf("en", "ru", "ar")) for (prefix in listOf(null, "", "'literal+¤")) {
            val j = JProps().setCurrencyPluralInfo(JInfo(JLocale(locale))).setPositivePrefix(prefix).setMinimumFractionDigits(2)
            val k = DecimalFormatProperties().setCurrencyPluralInfo(CurrencyPluralInfo(ULocale(locale))).setPositivePrefix(prefix).setMinimumFractionDigits(2)
            assertEquals(JUtils.propertiesToPatternString(j), PatternStringUtils.propertiesToPatternString(k))
        }
    }
}

// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import com.ibm.icu.text.DecimalFormat as JFormat
import com.ibm.icu.text.DecimalFormatSymbols as JSymbols
import com.ibm.icu.text.CurrencyPluralInfo as JInfo
import com.ibm.icu.util.ULocale as JLocale
import com.ibm.icu.util.Currency as JCurrency
import com.ibm.icu.math.BigDecimal as JDecimal
import com.ibm.icu.math.MathContext as JContext
import com.ibm.icu.impl.number.DecimalFormatProperties as JProperties
import io.github.heapy.icu4k.impl.number.DecimalFormatProperties
import io.github.heapy.icu4k.util.*
import io.github.heapy.icu4k.math.BigDecimal as IcuDecimal
import io.github.heapy.icu4k.math.MathContext as IcuContext
import java.io.*
import java.lang.reflect.Modifier
import java.lang.reflect.InvocationTargetException
import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import kotlin.test.*

class DecimalFormatOracleTest {
    private fun type(t: Class<*>): Class<*> = when (t) {
        JFormat::class.java -> DecimalFormat::class.java
        JSymbols::class.java -> DecimalFormatSymbols::class.java
        JInfo::class.java -> CurrencyPluralInfo::class.java
        JCurrency::class.java -> Currency::class.java
        JCurrency.CurrencyUsage::class.java -> Currency.CurrencyUsage::class.java
        JDecimal::class.java -> IcuDecimal::class.java
        JContext::class.java -> IcuContext::class.java
        JProperties.ParseMode::class.java -> DecimalFormatProperties.ParseMode::class.java
        JFormat.PropertySetter::class.java -> DecimalFormat.PropertySetter::class.java
        StringBuffer::class.java -> StringBuilder::class.java
        java.text.ParsePosition::class.java -> ParsePosition::class.java
        java.text.AttributedCharacterIterator::class.java -> FormatAttributedCharacterIterator::class.java
        com.ibm.icu.util.CurrencyAmount::class.java -> CurrencyAmount::class.java
        com.ibm.icu.number.LocalizedNumberFormatter::class.java -> io.github.heapy.icu4k.number.LocalizedNumberFormatter::class.java
        com.ibm.icu.text.PluralRules.IFixedDecimal::class.java -> PluralRules.IFixedDecimal::class.java
        else -> t
    }
    @Test fun sourcePublicMethodsConstructorsAndConstantsArePresent() {
        for (m in JFormat::class.java.declaredMethods.filter { Modifier.isPublic(it.modifiers) && !it.isSynthetic }) {
            val k = DecimalFormat::class.java.getDeclaredMethod(m.name, *m.parameterTypes.map(::type).toTypedArray())
            assertEquals(type(m.returnType), k.returnType, m.toString())
        }
        for (c in JFormat::class.java.constructors) DecimalFormat::class.java.getConstructor(*c.parameterTypes.map(::type).toTypedArray())
        for (f in JFormat::class.java.fields.filter { it.type == Int::class.javaPrimitiveType && Modifier.isStatic(it.modifiers) }) {
            val k = runCatching { DecimalFormat::class.java.getField(f.name) }.getOrNull() ?: NumberFormat::class.java.getField(f.name)
            assertEquals(f.getInt(null), k.getInt(null))
        }
    }
    private fun parsed(value: Number?): String = when (value) {
        null -> "null"
        is Double -> "double:${value.toBits()}"
        is JDecimal -> "decimal:$value/${value.scale()}"
        is IcuDecimal -> "decimal:$value/${value.scale()}"
        else -> "${value.javaClass.name}:$value"
    }
    private fun state(j: JFormat, k: DecimalFormat) {
        assertEquals(j.toPattern(), k.toPattern()); assertEquals(j.toLocalizedPattern(), k.toLocalizedPattern())
        assertEquals(j.minimumIntegerDigits, k.getMinimumIntegerDigits()); assertEquals(j.maximumIntegerDigits, k.getMaximumIntegerDigits())
        assertEquals(j.minimumFractionDigits, k.getMinimumFractionDigits()); assertEquals(j.maximumFractionDigits, k.getMaximumFractionDigits())
        assertEquals(j.mathContext, k.getMathContext()); assertEquals(j.mathContextICU.toString(), k.getMathContextICU().toString())
        assertEquals(j.positivePrefix, k.getPositivePrefix()); assertEquals(j.negativeSuffix, k.getNegativeSuffix())
        assertEquals(j.isParseBigDecimal, k.isParseBigDecimal()); assertEquals(j.isParseStrict, k.isParseStrict())
        for (number in listOf<Number>(1234L, Long.MIN_VALUE, 12.3456, -0.0, BigDecimal("12345678901234567890.125"), BigInteger("18446744073709551617"))) {
            assertEquals(j.format(number), k.format(number), "$number/${j.toPattern()}")
        }
    }
    @Test fun formattingParsingSymbolsContextsAndCopies() {
        for (locale in listOf("en_US", "fr", "ar", "hi")) for (pattern in listOf("#,##0.00", "0.###E+00", "@@@##", "¤0.0000", "*x##0.0", "0%")) {
            val j = JFormat(pattern, JSymbols(JLocale(locale))); val k = DecimalFormat(pattern, DecimalFormatSymbols(ULocale(locale)))
            state(j, k)
            for (text in listOf("12.50", "xx12.50tail", "USD 12.50", "-0", "1.25E+20", "bad")) for (offset in listOf(0, 2, 100)) {
                val jp = java.text.ParsePosition(offset); val kp = ParsePosition(offset)
                assertEquals(parsed(j.parse(text, jp)), parsed(k.parse(text, kp))); assertEquals(jp.index, kp.index); assertEquals(jp.errorIndex, kp.errorIndex)
            }
            val oldJ = j.toNumberFormatter(); val oldK = k.toNumberFormatter()
            j.setRoundingIncrement(BigDecimal.ZERO); k.setRoundingIncrement(BigDecimal.ZERO)
            assertSame(oldJ, j.toNumberFormatter()); assertSame(oldK, k.toNumberFormatter())
            j.applyPattern("0.000"); k.applyPattern("0.000")
            j.setMathContextICU(JContext(9, 2, false, 6)); k.setMathContextICU(IcuContext(9, 2, false, 6))
            state(j, k); state(j.clone(), k.clone())
            val js = j.decimalFormatSymbols; val ks = k.getDecimalFormatSymbols()
            js.decimalSeparator = '!'; ks.setDecimalSeparator('!')
            j.decimalFormatSymbols = js; k.setDecimalFormatSymbols(ks)
            state(j, k)
        }
    }
    private class JCalls : JFormat("0.00", JSymbols(JLocale.US)) {
        val calls = arrayListOf<String>()
        override fun applyPattern(pattern: String?) { calls.add("pattern:$pattern/${Thread.holdsLock(this)}"); super.applyPattern(pattern) }
        override fun toPattern(): String { calls.add("toPattern/${Thread.holdsLock(this)}"); return super.toPattern() }
        override fun setRoundingIncrement(value: BigDecimal?) { calls.add("increment:$value/${Thread.holdsLock(this)}"); super.setRoundingIncrement(value) }
        override fun setMathContext(value: MathContext?) { calls.add("setContext:$value/${Thread.holdsLock(this)}"); super.setMathContext(value) }
        override fun getMathContext(): MathContext { calls.add("getContext/${Thread.holdsLock(this)}"); return super.getMathContext() }
    }
    private class KCalls : DecimalFormat("0.00", DecimalFormatSymbols(ULocale("en_US"))) {
        val calls = arrayListOf<String>()
        override fun applyPattern(pattern: String?) { calls.add("pattern:$pattern/${Thread.holdsLock(this)}"); super.applyPattern(pattern) }
        override fun toPattern(): String { calls.add("toPattern/${Thread.holdsLock(this)}"); return super.toPattern() }
        override fun setRoundingIncrement(value: BigDecimal?) { calls.add("increment:$value/${Thread.holdsLock(this)}"); super.setRoundingIncrement(value) }
        override fun setMathContext(value: MathContext?) { calls.add("setContext:$value/${Thread.holdsLock(this)}"); super.setMathContext(value) }
        override fun getMathContext(): MathContext? { calls.add("getContext/${Thread.holdsLock(this)}"); return super.getMathContext() }
    }
    @Test fun virtualCallsMonitorAndUnknownSubclassCloning() {
        val j = JCalls(); val k = KCalls()
        j.applyLocalizedPattern("0.000"); k.applyLocalizedPattern("0.000")
        j.toLocalizedPattern(); k.toLocalizedPattern()
        j.setRoundingIncrement(0.05); k.setRoundingIncrement(0.05)
        j.setRoundingIncrement(JDecimal("0.10")); k.setRoundingIncrement(IcuDecimal("0.10"))
        j.setMathContextICU(JContext(9, 2)); k.setMathContextICU(IcuContext(9, 2))
        j.getMathContextICU(); k.getMathContextICU()
        assertEquals(j.calls, k.calls)
        val jc = j.clone() as JCalls; val kc = k.clone() as KCalls
        assertSame(j.calls, jc.calls); assertSame(k.calls, kc.calls)
        assertEquals(j == jc, k == kc)
        jc.applyPattern("0.0"); kc.applyPattern("0.0")
        assertEquals(j.calls, k.calls)
        state(j, k); state(jc, kc)
    }
    @Test fun callbackLocaleChangeIsReadAtRefreshTime() {
        val j = JFormat("0.00", JSymbols(JLocale.US)); val k = DecimalFormat("0.00", DecimalFormatSymbols(ULocale("en_US")))
        val setLocale = com.ibm.icu.text.UFormat::class.java.getDeclaredMethod("setLocale", JLocale::class.java, JLocale::class.java).also { it.isAccessible = true }
        setLocale.invoke(j, JLocale.US, JLocale.US); k.setLocale(ULocale("en_US"), ULocale("en_US"))
        j.setProperties { setLocale.invoke(j, JLocale.FRANCE, JLocale.FRANCE); it.setMaximumFractionDigits(3) }
        k.setProperties { k.setLocale(ULocale("fr_FR"), ULocale("fr_FR")); it.setMaximumFractionDigits(3) }
        val resolve = Class.forName("com.ibm.icu.number.NumberFormatterSettings").getDeclaredMethod("resolve").also { it.isAccessible = true }
        val macros = resolve.invoke(j.toNumberFormatter())
        val locale = macros.javaClass.getField("loc").get(macros) as JLocale
        assertEquals(locale.name, k.toNumberFormatter().resolve().loc!!.getName())
        assertEquals("fr_FR", k.toNumberFormatter().resolve().loc!!.getName())
        val copy = k.clone()
        copy.setLocale(ULocale("de"), ULocale("de")); copy.setMaximumFractionDigits(4)
        assertEquals("de", copy.toNumberFormatter().resolve().loc!!.getName())
        assertEquals("fr_FR", k.toNumberFormatter().resolve().loc!!.getName())
    }
    private fun roundTrip(value: Any): Any {
        val bytes = ByteArrayOutputStream().also { ObjectOutputStream(it).use { out -> out.writeObject(value) } }.toByteArray()
        return ObjectInputStream(ByteArrayInputStream(bytes)).use { it.readObject() }
    }
    @Test fun modernSerializationRebuildsPublishedRuntime() {
        val jd = ObjectStreamClass.lookup(JFormat::class.java); val kd = ObjectStreamClass.lookup(DecimalFormat::class.java)
        assertEquals(jd.serialVersionUID, kd.serialVersionUID); assertEquals(jd.fields.map { it.name to it.type }, kd.fields.map { it.name to it.type })
        for (locale in listOf("en_US", "fr", "ar")) {
            val j = JFormat("¤#,##0.000", JSymbols(JLocale(locale))); val k = DecimalFormat("¤#,##0.000", DecimalFormatSymbols(ULocale(locale)))
            j.setMathContextICU(JContext(9, 2, false, 6)); k.setMathContextICU(IcuContext(9, 2, false, 6))
            j.setParseBigDecimal(true); k.setParseBigDecimal(true)
            j.setCurrency(JCurrency.getInstance("CHF")); k.setCurrency(Currency.getInstance("CHF"))
            val jr = roundTrip(j) as JFormat; val kr = roundTrip(k) as DecimalFormat
            state(jr, kr)
            assertEquals(jr.getMathContextICU().form, kr.getMathContextICU().getForm())
            assertEquals(parsed(jr.parse("12.50", java.text.ParsePosition(0))), parsed(kr.parse("12.50", ParsePosition(0))))
            state(roundTrip(jr) as JFormat, roundTrip(kr) as DecimalFormat)
        }
    }
}

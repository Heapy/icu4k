@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.number

import com.ibm.icu.number.NumberRangeFormatter as JRange
import com.ibm.icu.number.FormattedNumberRange as JResult
import com.ibm.icu.number.NumberFormatter as JFormatter
import com.ibm.icu.number.NumberRangeFormatterSettings as JSettings
import com.ibm.icu.impl.number.range.RangeMacroProps as JMacros
import com.ibm.icu.text.ConstrainedFieldPosition as JPosition
import com.ibm.icu.text.NumberFormat.Field as JField
import com.ibm.icu.text.PluralRules as JRules
import com.ibm.icu.util.ULocale as JLocale
import io.github.heapy.icu4k.number.NumberRangeFormatter.*
import io.github.heapy.icu4k.text.*
import io.github.heapy.icu4k.util.*
import java.lang.reflect.InvocationTargetException
import java.io.*
import kotlin.test.*

class NumberRangeFormatterOracleTest {
    private fun outcome(block: () -> Any?): Any? = try { block() } catch (e: Throwable) {
        val error = if (e is InvocationTargetException) e.cause!! else e
        when (error) { is NullPointerException -> "null"; is IndexOutOfBoundsException -> "bounds"; is AssertionError -> "assert:${error.message}"; else -> "${error.javaClass.simpleName}:${error.message}" }
    }
    private fun key(value: Any?): Any? = if (value is java.text.Format.Field) value.toString().substringAfterLast('(') else value
    private fun compare(j: JResult, k: FormattedNumberRange, label: String, attributes: Boolean) {
        assertEquals(j.toString(), k.toString(), label)
        assertEquals(j.identityResult.name, k.getIdentityResult()!!.name, label)
        assertEquals(j.firstBigDecimal, k.getFirstBigDecimal(), "$label/first")
        assertEquals(j.secondBigDecimal, k.getSecondBigDecimal(), "$label/second")
        assertEquals(j.length, k.length)
        for (index in 0 until k.length) assertEquals(j[index], k[index], label)
        val jp = JPosition(); val kp = ConstrainedFieldPosition()
        var iterations = 0
        while (true) {
            assertTrue(iterations++ < 100)
            val found = j.nextPosition(jp)
            assertEquals(found, k.nextPosition(kp), "$label/position")
            assertEquals(listOf(key(jp.field), key(jp.fieldValue), jp.start, jp.limit), listOf(key(kp.getField()), key(kp.getFieldValue()), kp.getStart(), kp.getLimit()), label)
            if (!found) break
        }
        if (!attributes) return
        for (mode in 0..2) {
            jp.reset(); kp.reset()
            when (mode) {
                0 -> { jp.constrainField(JRange.SpanField.NUMBER_RANGE_SPAN); kp.constrainField(SpanField.NUMBER_RANGE_SPAN) }
                1 -> { jp.constrainFieldAndValue(JRange.SpanField.NUMBER_RANGE_SPAN, 1); kp.constrainFieldAndValue(SpanField.NUMBER_RANGE_SPAN, 1) }
                2 -> { jp.constrainField(JField.INTEGER); kp.constrainField(NumberFormatField.INTEGER) }
            }
            do {
                val found = j.nextPosition(jp); assertEquals(found, k.nextPosition(kp))
                assertEquals(listOf(key(jp.field), key(jp.fieldValue), jp.start, jp.limit), listOf(key(kp.getField()), key(kp.getFieldValue()), kp.getStart(), kp.getLimit()), label)
            } while (found)
        }
        val ji = j.toCharacterIterator(); val ki = k.toCharacterIterator()
        for (index in 0..k.length) {
            assertEquals(ji.setIndex(index), ki.setIndex(index))
            assertEquals(ji.attributes.entries.associate { key(it.key) to key(it.value) }, ki.attributes.entries.associate { key(it.key) to key(it.value) }, label)
            assertEquals(ji.runStart, ki.runStart); assertEquals(ji.runLimit, ki.runLimit)
        }
        for ((start, end) in listOf(0 to k.length, 0 to 0, -1 to 0, 1 to 0, 0 to k.length + 1)) {
            assertEquals(outcome { j.subSequence(start, end).toString() }, outcome { k.subSequence(start, end).toString() })
        }
        for (operand in PluralRules.Operand.entries) {
            assertEquals(j.firstFixedDecimal.getPluralOperand(JRules.Operand.valueOf(operand.name)), k.getFirstFixedDecimal()!!.getPluralOperand(operand))
            assertEquals(j.secondFixedDecimal.getPluralOperand(JRules.Operand.valueOf(operand.name)), k.getSecondFixedDecimal()!!.getPluralOperand(operand))
        }
        val ja = StringBuilder("prefix"); val ka = StringBuilder("prefix")
        assertSame(ja, j.appendTo(ja)); assertSame(ka, k.appendTo(ka)); assertEquals(ja.toString(), ka.toString())
    }
    @Test fun allCollapseAndIdentityPoliciesAcrossFormatterModes() {
        val pairs = listOf(1.0 to 5.0, 1.0 to 1.0, 1.001 to 1.002, -1.0 to 1.0, -0.0 to 0.0, Double.NaN to Double.NaN, Double.NEGATIVE_INFINITY to Double.POSITIVE_INFINITY, 1234.0 to 5678.0)
        for (locale in listOf("en", "fr", "de", "ru", "ar@numbers=arab", "ja")) for (skeleton in listOf("", ".00", "currency/USD", "currency/USD unit-width-full-name", "unit/meter unit-width-full-name", "scientific", "compact-short", "percent", "unit/foot-and-inch unit-width-full-name")) {
            for (collapse in RangeCollapse.entries) for (fallback in RangeIdentityFallback.entries) {
                val j = JRange.with().numberFormatterBoth(JFormatter.forSkeleton(skeleton)).collapse(JRange.RangeCollapse.valueOf(collapse.name)).identityFallback(JRange.RangeIdentityFallback.valueOf(fallback.name)).locale(JLocale(locale))
                val k = NumberRangeFormatter.with().numberFormatterBoth(NumberFormatter.forSkeleton(skeleton)).collapse(collapse).identityFallback(fallback).locale(ULocale(locale))
                for ((index, pair) in pairs.withIndex()) {
                    val label = "$locale/$skeleton/$collapse/$fallback/$pair"
                    var a: JResult? = null; var b: FormattedNumberRange? = null
                    assertEquals(outcome { a = j.formatRange(pair.first, pair.second); "ok" }, outcome { b = k.formatRange(pair.first, pair.second); "ok" }, label)
                    if (a != null && b != null) compare(a!!, b!!, label, index == 0 && collapse == RangeCollapse.AUTO)
                }
            }
        }
    }
    @Test fun separateFormattersNumberTypesAndNumberingCompatibility() {
        for (first in listOf("", ".0", "currency/USD", "scientific", "numbering-system/arab", "numbering-system/mathbold")) for (second in listOf("", ".00", "currency/EUR", "scientific", "numbering-system/arab")) {
            val j = JRange.withLocale(JLocale.ENGLISH).numberFormatterFirst(JFormatter.forSkeleton(first)).numberFormatterSecond(JFormatter.forSkeleton(second))
            val k = NumberRangeFormatter.withLocale(ULocale.ENGLISH).numberFormatterFirst(NumberFormatter.forSkeleton(first)).numberFormatterSecond(NumberFormatter.forSkeleton(second))
            for (values in listOf(1.0 to 1.0, 1.01 to 1.02, 1234.0 to 5678.0)) {
                var a: JResult? = null; var b: FormattedNumberRange? = null
                assertEquals(outcome { a = j.formatRange(values.first, values.second); "ok" }, outcome { b = k.formatRange(values.first, values.second); "ok" }, "$first/$second")
                if (a != null && b != null) compare(a!!, b!!, "$first/$second", true)
            }
        }
        val j = JRange.withLocale(java.util.Locale.US); val k = NumberRangeFormatter.withLocale(java.util.Locale.US)
        val numbers = listOf<Number>(1, 1L, 1.0, java.math.BigDecimal("1.0"), java.math.BigDecimal("1.00"), java.math.BigInteger("18446744073709551617"), Double.NaN, -0.0)
        for (a in numbers) for (b in numbers) compare(j.formatRange(a, b), k.formatRange(a, b), "$a/${a.javaClass}/$b/${b.javaClass}", false)
        assertEquals(outcome { j.formatRange(null as Number?, 1 as Number) }, outcome { k.formatRange(null as Number?, 1 as Number) })
        assertEquals(j.withoutLocale() == JRange.with(), k.withoutLocale() == NumberRangeFormatter.with())
        assertEquals(outcome { JRange.withLocale(null as JLocale?).formatRange(1, 2) }, outcome { NumberRangeFormatter.withLocale(null as ULocale?).formatRange(1, 2) })
    }
    @Test fun settingsLocaleMutationAndSameFormattersEqualityQuirk() {
        val resolve = JSettings::class.java.getDeclaredMethod("resolve").also { it.isAccessible = true }
        val resolveNumber = com.ibm.icu.number.NumberFormatterSettings::class.java.getDeclaredMethod("resolve").also { it.isAccessible = true }
        val jf = JFormatter.with().precision(com.ibm.icu.number.Precision.integer()); val kf = NumberFormatter.with().precision(Precision.integer())
        val jBoth = JRange.with().numberFormatterBoth(jf); val kBoth = NumberRangeFormatter.with().numberFormatterBoth(kf)
        val jFirst = JRange.with().numberFormatterFirst(jf); val kFirst = NumberRangeFormatter.with().numberFormatterFirst(kf)
        assertTrue(jBoth == jFirst); assertTrue(kBoth == kFirst); assertEquals(kBoth.hashCode(), kFirst.hashCode())
        val jr = jBoth.locale(JLocale.FRENCH); val kr = kBoth.locale(ULocale("fr"))
        resolve.invoke(jr); kr.resolve()
        assertEquals((resolveNumber.invoke(jf) as com.ibm.icu.impl.number.MacroProps).loc.toString(), kf.resolve().loc.toString())
        assertEquals(1, (resolve.invoke(jBoth) as JMacros).sameFormatters); assertEquals(1, kBoth.resolve().sameFormatters)
        // Cached range resolution does not repeat the locale mutation.
        resolve.invoke(jBoth); kBoth.resolve()
        assertEquals((resolveNumber.invoke(jf) as com.ibm.icu.impl.number.MacroProps).loc?.toString(), kf.resolve().loc?.toString())
    }
    @Test fun spanSerializationAndAppendFailures() {
        fun copy(value: Any): Any {
            val bytes = ByteArrayOutputStream().also { out -> ObjectOutputStream(out).use { it.writeObject(value) } }.toByteArray()
            return ObjectInputStream(ByteArrayInputStream(bytes)).use { it.readObject() }
        }
        assertSame(JRange.SpanField.NUMBER_RANGE_SPAN, copy(JRange.SpanField.NUMBER_RANGE_SPAN))
        assertSame(SpanField.NUMBER_RANGE_SPAN, copy(SpanField.NUMBER_RANGE_SPAN))
        assertEquals(ObjectStreamClass.lookup(JRange.SpanField::class.java).serialVersionUID, ObjectStreamClass.lookup(SpanField::class.java).serialVersionUID)
        val failure = IOException("append failure")
        val output = object : Appendable {
            override fun append(csq: CharSequence?): Appendable = throw failure
            override fun append(csq: CharSequence?, start: Int, end: Int): Appendable = throw failure
            override fun append(c: Char): Appendable = throw failure
        }
        val j = JRange.withLocale(JLocale.ENGLISH).formatRange(1, 2); val k = NumberRangeFormatter.withLocale(ULocale.ENGLISH).formatRange(1, 2)
        assertSame(failure, assertFailsWith<com.ibm.icu.util.ICUUncheckedIOException> { j.appendTo(output) }.cause)
        assertSame(failure, assertFailsWith<ICUUncheckedIOException> { k.appendTo(output) }.cause)
    }
}

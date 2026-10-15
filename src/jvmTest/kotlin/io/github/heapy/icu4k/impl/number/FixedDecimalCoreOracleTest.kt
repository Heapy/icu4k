@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.impl.number

import kotlin.test.*
import com.ibm.icu.text.PluralRules.FixedDecimal as JFixed
import com.ibm.icu.impl.number.DecimalQuantity_DualStorageBCD as JQuantity
import com.ibm.icu.text.PluralRules.Operand as JOperand

class FixedDecimalCoreOracleTest {
    private fun outcome(block: () -> Any?): Any? = try { block() } catch (e: Throwable) { e.javaClass.simpleName }
    private fun check(j: JFixed, k: FixedDecimalCore, label: String) {
        assertEquals(j.source.toBits(), k.getSource().toBits(), label)
        assertEquals(j.visibleDecimalDigitCount, k.getVisibleDecimalDigitCount(), label)
        assertEquals(j.visibleDecimalDigitCountWithoutTrailingZeros, k.getVisibleDecimalDigitCountWithoutTrailingZeros(), label)
        assertEquals(j.decimalDigits, k.getDecimalDigits(), label)
        assertEquals(j.decimalDigitsWithoutTrailingZeros, k.getDecimalDigitsWithoutTrailingZeros(), label)
        assertEquals(j.integerValue, k.getIntegerValue(), label)
        assertEquals(j.baseFactor, k.getBaseFactor(), label)
        assertEquals(j.isHasIntegerValue, k.isHasIntegerValue(), label)
        assertEquals(j.isNegative, k.isNegative(), label)
        assertEquals(j.toLong(), k.toLong(), label)
        assertEquals(j.toInt(), k.toInt(), label)
        assertEquals(j.toFloat().toBits(), k.toFloat().toBits(), label)
        assertEquals(j.toDouble().toBits(), k.toDouble().toBits(), label)
        assertEquals(j.hashCode(), k.hashCode(), label)
        assertEquals(j.toString(), k.toString(), label)
        for (operand in JOperand.entries) assertEquals(j.getPluralOperand(operand).toBits(), k.getPluralOperand(DecimalQuantityCore.Operand.valueOf(operand.name)).toBits(), "$label/$operand")
    }
    @Test fun constructorsFieldsOperandsAndComparisonsMatchSource() {
        val values = listOf(0.0, -0.0, 1.000001, 1.005, 1.2, -1.2, 1234567890.12345, 1e19, -1e19, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)
        for (value in values) {
            assertEquals(JFixed.decimals(value), FixedDecimalCore.decimals(value), "$value decimals")
            check(JFixed(value), FixedDecimalCore(value), "$value inferred")
            for (v in listOf(0, 2, 6, 12)) {
                check(JFixed(value, v), FixedDecimalCore(value, v), "$value/$v")
                for (e in listOf(-2, 0, 3)) check(JFixed.createWithExponent(value, v, e), FixedDecimalCore.createWithExponent(value, v, e), "$value/$v/$e")
            }
        }
        for (a in values) for (b in values) {
            val j = JFixed(a); val jo = JFixed(b); val k = FixedDecimalCore(a); val ko = FixedDecimalCore(b)
            assertEquals(j == jo, k == ko, "$a/$b")
            assertEquals(j.compareTo(jo), k.compareTo(ko), "$a/$b")
        }
        check(JFixed(Long.MIN_VALUE), FixedDecimalCore(Long.MIN_VALUE), "long minimum")
        check(JFixed(1.2, 3, -200, 0, 4), FixedDecimalCore(1.2, 3, -200, 0, 4), "explicit fields")
    }
    @Test fun exponentSampleParsingMatchesSource() {
        for (text in listOf("1", "1.00", "-1.200c3", "0.000c2", "1.2e3", "1.2E-3", "1.2C4", "1E2e3", "1e2E3", "١.٢٠c٣", "1c2147483647", "1c-2147483648", "", "NaN", "1c", "1e+", "1e2147483648", "1c2c3")) {
            val expected = outcome { JQuantity.fromExponentString(text).toExponentString() }
            val actual = outcome { DecimalQuantityCore.fromExponentString(text).toExponentString() }
            assertEquals(expected, actual, text)
        }
    }
    @Test fun portableFormattingAndBmpDigitsMatchJava() {
        val random = kotlin.random.Random(33109)
        repeat(250) {
            val value = Double.fromBits(random.nextLong() and Long.MAX_VALUE)
            if (!value.isFinite()) return@repeat
            for (fraction in listOf(0, 2, 6, 12)) assertEquals(String.format(java.util.Locale.ROOT, "%.${fraction}f", value), FixedDecimalFormatting.plain(value, fraction), "$value/$fraction")
            if (value > 0) assertEquals(String.format(java.util.Locale.ENGLISH, "%1.15e", value), FixedDecimalFormatting.scientific(value), "$value scientific")
        }
        for (value in listOf(0.0, -0.0, -1.25, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -1e100)) assertEquals(String.format(java.util.Locale.ENGLISH, "%1.15e", value), FixedDecimalFormatting.scientific(value), "$value")
        for (c in Char.MIN_VALUE..Char.MAX_VALUE) assertEquals(Character.digit(c, 10), DecimalNumberParsing.digit(c), "digit ${c.code}")
    }
    @Test fun javaSerializationIsRejectedLikeSource() {
        fun serialize(value: Any): ByteArray = java.io.ByteArrayOutputStream().also { output -> java.io.ObjectOutputStream(output).use { it.writeObject(value) } }.toByteArray()
        assertEquals("NotSerializableException", outcome { serialize(JFixed(1.2)) })
        assertEquals("NotSerializableException", outcome { serialize(FixedDecimalCore(1.2)) })
    }

}

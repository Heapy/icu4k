package io.github.heapy.icu4k.util

import com.ibm.icu.util.Measure as JMeasure
import com.ibm.icu.util.MeasureUnit as JUnit
import com.ibm.icu.util.Currency as JCurrency
import com.ibm.icu.util.CurrencyAmount as JCurrencyAmount
import com.ibm.icu.util.TimeUnitAmount as JTimeAmount
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.test.*

class MeasureAmountOracleTest {
    private fun outcome(action: () -> Any?): Any? = try { action() } catch (e: Exception) { e.javaClass.simpleName + if (e is NullPointerException || e is ClassCastException) "" else ":${e.message}" }
    @Test fun numberRepresentationsEqualityAndHashesMatchSource() {
        val values: List<Number> = listOf(1.toByte(), 1.toShort(), 1, 1L, 1.0f, 1.0, 0.0, -0.0, 0.1f, 0.1,
            Long.MAX_VALUE, 9007199254740992L, 9007199254740993L, Float.NaN, Double.NaN,
            Double.fromBits(0x7ff8000000001234L), Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY,
            BigDecimal("1.0"), BigDecimal("1.00"), BigInteger("123456789012345678901234567890"))
        for ((index, value) in values.withIndex()) {
            val j = JMeasure(value, JUnit.METER); val k = Measure(value, MeasureUnit.METER)
            assertSame(value, k.getNumber()); assertSame(MeasureUnit.METER, k.getUnit())
            assertEquals(j.toString(), k.toString(), "$index/$value")
            assertEquals(j.hashCode(), k.hashCode(), "$index/$value")
            assertEquals(j == null, k == null)
            assertEquals(j.equals("other"), k.equals("other"))
            for (other in values) {
                assertEquals(j == JMeasure(other, JUnit.METER), k == Measure(other, MeasureUnit.METER), "$value vs $other")
                assertEquals(j == JMeasure(other, JUnit.SECOND), k == Measure(other, MeasureUnit.SECOND), "$value vs $other")
            }
        }
        for ((number, unit) in listOf(null to null, null to MeasureUnit.METER, 1 to null)) {
            val expected = assertFailsWith<NullPointerException> { JMeasure(number, if (unit == null) null else JUnit.METER) }
            assertEquals(expected.message, assertFailsWith<NullPointerException> { Measure(number, unit) }.message)
        }
        val j = JMeasure(1, JUnit.forIdentifier("kilometer-per-100-second"))
        val k = Measure(1, MeasureUnit.forIdentifier("kilometer-per-100-second"))
        assertEquals(j.toString(), k.toString())
        assertEquals(outcome { j.hashCode() }, outcome { k.hashCode() })
    }
    @Test fun amountsAndJavaCurrencyFactoriesMatchSource() {
        val jc = JCurrency.getInstance("USD"); val kc = Currency.getInstance("USD")
        for (number in listOf<Number>(1, 2L, 2.5f, -0.0, Double.NaN, BigDecimal("123.4500"))) {
            val j = JCurrencyAmount(number, jc); val k = CurrencyAmount(number, kc)
            assertSame(kc, k.getCurrency()); assertSame(number, k.getNumber())
            assertEquals(j.toString(), k.toString()); assertEquals(j.hashCode(), k.hashCode())
            assertEquals(JMeasure(number, jc) == j, Measure(number, kc) == k)
            val javaCurrency = java.util.Currency.getInstance("USD")
            assertEquals(JCurrencyAmount(number, javaCurrency).toString(), CurrencyAmount(number, javaCurrency).toString())
            val jt = JTimeAmount(number, JUnit.HOUR); val kt = TimeUnitAmount(number, MeasureUnit.HOUR)
            assertSame(MeasureUnit.HOUR, kt.getTimeUnit()); assertSame(number, kt.getNumber())
            assertEquals(jt.toString(), kt.toString()); assertEquals(jt.hashCode(), kt.hashCode())
        }
        for (number in listOf(0.0, -0.0, 1.0, 2.675, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertEquals(JCurrencyAmount(number, jc).toString(), CurrencyAmount(number, kc).toString())
            assertEquals(JTimeAmount(number, JUnit.SECOND).toString(), TimeUnitAmount(number, MeasureUnit.SECOND).toString())
            assertEquals(JCurrencyAmount(number, java.util.Currency.getInstance("EUR")).toString(), CurrencyAmount(number, java.util.Currency.getInstance("EUR")).toString())
        }
        assertEquals(outcome { JCurrencyAmount(null as Number?, null as JCurrency?) }, outcome { CurrencyAmount(null as Number?, null as Currency?) })
        assertEquals(outcome { JTimeAmount(null as Number?, null) }, outcome { TimeUnitAmount(null as Number?, null) })
        assertEquals(outcome { JCurrencyAmount(1.0, null as java.util.Currency?) }, outcome { CurrencyAmount(1.0, null as java.util.Currency?) })
    }
    private class MutableNumber(var value: Int) : Number() {
        override fun toByte() = value.toByte()
        override fun toShort() = value.toShort()
        override fun toInt() = value
        override fun toLong() = value.toLong()
        override fun toFloat() = value.toFloat()
        override fun toDouble() = value.toDouble()
        override fun toString() = "number:$value"
    }
    @Test fun retainedNumbersAndVirtualGetterBoundariesMatchSource() {
        val number = MutableNumber(1)
        val j = object : JMeasure(number, JUnit.METER) {
            override fun getNumber(): Number = error("getter must not be used")
            override fun getUnit(): JUnit = error("getter must not be used")
        }
        val k = object : Measure(number, MeasureUnit.METER) {
            override fun getNumber(): Number = error("getter must not be used")
            override fun getUnit(): MeasureUnit = error("getter must not be used")
        }
        for (n in listOf(1, 99, -2)) {
            number.value = n
            assertEquals(j.toString(), k.toString()); assertEquals(j.hashCode(), k.hashCode())
            assertEquals(j == JMeasure(n, JUnit.METER), k == Measure(n, MeasureUnit.METER))
        }
        val ja = object : JCurrencyAmount(1, JCurrency.getInstance("USD")) { override fun getUnit(): JUnit = JUnit.METER }
        val ka = object : CurrencyAmount(1, Currency.getInstance("USD")) { override fun getUnit(): MeasureUnit = MeasureUnit.METER }
        assertEquals(outcome { ja.currency }, outcome { ka.getCurrency() })
    }
}

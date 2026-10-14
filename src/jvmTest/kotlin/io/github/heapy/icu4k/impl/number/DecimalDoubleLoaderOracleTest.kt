@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.impl.number

import java.math.MathContext
import java.math.RoundingMode
import kotlin.random.Random
import kotlin.test.*
import com.ibm.icu.impl.number.DecimalQuantity_DualStorageBCD as JQuantity

class DecimalDoubleLoaderOracleTest {
    private val base = JQuantity::class.java.superclass
    private fun field(j: JQuantity, name: String): Any? = base.getDeclaredField(name).also { it.isAccessible = true }.get(j)
    private fun check(j: JQuantity, k: DecimalBcdStorage, state: DecimalDoubleLoader.State, label: String) {
        assertEquals(j.isNegative, state.negative, label)
        assertEquals(j.isInfinite, state.infinite, label)
        assertEquals(j.isNaN, state.nan, label)
        assertEquals(field(j, "scale"), k.scale, label)
        assertEquals(field(j, "precision"), k.precision, label)
        assertEquals(field(j, "isApproximate"), k.isApproximate, label)
        assertEquals(field(j, "origDouble"), k.origDouble, label)
        assertEquals(field(j, "origDelta"), k.origDelta, label)
        assertEquals(j.explicitExactDouble, state.explicitlyExact, label)
        assertEquals(j.isUsingBytes, k.usingBytes, label)
        val get = JQuantity::class.java.getDeclaredMethod("getDigitPos", Int::class.javaPrimitiveType).also { it.isAccessible = true }
        for (i in 0 until k.precision) assertEquals(get.invoke(j, i), k.getDigitPos(i), "$label/$i")
    }
    @Test fun commonShortestDecimalSelectionMatchesJava() {
        val random = Random(29184)
        val values = buildList {
            addAll(listOf(0.0, -0.0, Double.MIN_VALUE, Double.MIN_VALUE * 2, Double.MAX_VALUE, Double.NaN, Double.NEGATIVE_INFINITY))
            for (bits in listOf(0x0010000000000000L, 0x3ff0000000000000L, 0x4340000000000000L)) for (delta in -5L..5L) add(Double.fromBits(bits + delta))
            repeat(1500) { add(Double.fromBits(random.nextLong())) }
        }
        for (value in values) assertEquals(java.lang.Double.toString(value), CommonDoubleToDecimal.format(value), value.toRawBits().toString(16))
    }
    @Test fun loadingFlagsFastDigitsAndExactFallbackMatchSource() {
        val random = Random(1792)
        val values = buildList {
            addAll(listOf(0.0, -0.0, Double.MIN_VALUE, Double.MAX_VALUE, Double.NaN, Double.NEGATIVE_INFINITY, Double.fromBits(-2251799813685247L), 1.005, 2.675, 12345.67890123456))
            repeat(500) { add(Double.fromBits(random.nextLong())) }
        }
        for (value in values) {
            val j = JQuantity(value); val k = DecimalBcdStorage(); val state = DecimalDoubleLoader.load(k, value)
            check(j, k, state, "load ${value.toRawBits()}")
            if (k.isApproximate) {
                j.roundToInfinity(); DecimalDoubleLoader.makeAccurate(k, state)
                check(j, k, state, "exact ${value.toRawBits()}")
            }
            if (!state.nan && !state.infinite) assertEquals(j.toBigDecimal().toString(), k.toDecimal(state.negative).toString())
        }
        for (value in listOf(1.005, 2.675, -2.675, 0.1, 9.999999999999998)) for (mode in RoundingMode.entries.filter { it != RoundingMode.UNNECESSARY }) {
            val j = JQuantity(value); val k = DecimalBcdStorage(); val state = DecimalDoubleLoader.load(k, value)
            j.roundToMagnitude(-2, MathContext(0, mode))
            DecimalBcdRounding.round(k, state.negative, -2, 0, mode.ordinal, false, "value") { DecimalDoubleLoader.makeAccurate(k, state) }
            check(j, k, state, "round $value/$mode")
            assertEquals(j.toBigDecimal().toString(), k.toDecimal(state.negative).toString())
        }
    }
}

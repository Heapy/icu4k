// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.util

import io.github.heapy.icu4k.impl.number.BigDecimalCore
import java.math.BigDecimal as JDecimal
import kotlin.random.Random
import kotlin.test.*

class BigDecimalCoreOracleTest {
    private fun outcome(block: () -> Any?): Any? = try { block() } catch (e: Throwable) {
        when (e) { is NumberFormatException -> "number"; is NullPointerException -> "null";
            is ArithmeticException -> if (e.message in listOf("/ by zero", "BigInteger divide by zero")) "divisionZero" else "ArithmeticException:${e.message}"; else -> "${e.javaClass.simpleName}:${e.message}" }
    }
    private fun check(j: JDecimal, k: BigDecimalCore, label: String, hash: Boolean = true) {
        assertEquals(j.toString(), k.toString(), label)
        assertEquals(j.scale(), k.value.scale, label)
        assertEquals(j.precision(), k.value.precision, label)
        assertEquals(j.signum(), k.value.sign, label)
        if (hash) assertEquals(j.hashCode(), k.hashCode(), "$label/hash")
        assertEquals(j.toDouble().toBits(), k.toDouble().toBits(), "$label/double")
        assertEquals(j.toFloat().toBits(), k.toFloat().toBits(), "$label/float")
        assertEquals(j.toLong(), k.toLong(), "$label/long")
        assertEquals(j.toInt(), k.toInt(), "$label/int")
    }
    @Test fun valuesScaleHashAndArithmeticMatchJdkBoundary() {
        val random = Random(4609)
        val values = listOf("0", "0.00", "0E+9", "1.20", "-1.25", "1E+18", "1E-19", "-9223372036854775808", "18446744073709551617") + List(160) {
            val digits = buildString { repeat(random.nextInt(1, 85)) { append(('0'.code + random.nextInt(10)).toChar()) } }
            (if (random.nextBoolean()) "-" else "") + digits + "E" + random.nextInt(-60, 60)
        }
        for (text in values) {
            val j = JDecimal(text); val k = BigDecimalCore(text)
            check(j, k, text)
            assertEquals(j.toPlainString(), k.toPlainString())
            val other = values[random.nextInt(values.size)]
            val jo = JDecimal(other); val ko = BigDecimalCore(other)
            check(j.add(jo), k.add(ko), "$text+$other")
            check(j.subtract(jo), k.subtract(ko), "$text-$other")
            check(j.multiply(jo), k.multiply(ko), "$text*$other")
            assertEquals(j.compareTo(jo).coerceIn(-1, 1), k.compareTo(ko), "$text/$other/compare")
            check(j.stripTrailingZeros(), k.stripTrailingZeros(), "$text/strip")
            for (shift in listOf(-5, 0, 5)) {
                check(j.scaleByPowerOfTen(shift), k.scaleByPowerOfTen(shift), "$text/$shift/power")
                check(j.movePointLeft(shift), k.movePointLeft(shift), "$text/$shift/left")
                check(j.movePointRight(shift), k.movePointRight(shift), "$text/$shift/right")
            }
        }
        val typed: java.math.BigDecimal = BigDecimal("1.0")
        assertEquals(1, typed.scale())
        assertSame(JDecimal.ZERO, BigDecimals.ZERO); assertSame(JDecimal.ONE, BigDecimals.ONE); assertSame(JDecimal.TEN, BigDecimals.TEN)
    }
    @Test fun divisionAllContextsExactnessAndRoundingMatchJdkBoundary() {
        val numerators = listOf("0", "1", "-1", "1.20", "25", "1000000000000000000000000000001", "0.0000005")
        val denominators = listOf("0", "1", "2", "3", "5", "6", "8", "40", "125", "-0.006")
        for (a in numerators) for (b in denominators) {
            val j = JDecimal(a); val k = BigDecimalCore(a); val jd = JDecimal(b); val kd = BigDecimalCore(b)
            assertEquals(outcome { j.divide(jd).toString() }, outcome { k.divide(kd).toString() }, "$a/$b/exact")
            for (precision in listOf(0, 1, 3, 16)) for (mode in RoundingMode.entries) {
                val context = MathContext(precision, mode)
                assertEquals(outcome { j.divide(jd, context).toString() }, outcome { k.divide(kd, context).toString() }, "$a/$b/$precision/$mode")
                assertEquals(outcome { j.round(context).toString() }, outcome { k.round(context).toString() }, "$a/$precision/$mode/round")
            }
            for (scale in listOf(-2, 0, 3)) for (mode in RoundingMode.entries) {
                assertEquals(outcome { j.divide(jd, scale, mode).toString() }, outcome { k.divide(kd, scale, mode).toString() }, "$a/$b/$scale/$mode")
                assertEquals(outcome { j.setScale(scale, mode).toString() }, outcome { k.setScale(scale, mode).toString() }, "$a/$scale/$mode/scale")
            }
        }
    }
    @Test fun exactAndCanonicalDoubleConstructionMatchJdkBoundary() {
        val random = Random(1396)
        val values = listOf(0.0, -0.0, 0.1, -0.1, 1.0, 1000.0, Double.MIN_VALUE, Double.MAX_VALUE, Double.NaN, Double.POSITIVE_INFINITY) + List(80) { Double.fromBits(random.nextLong()) }
        for (value in values) {
            assertEquals(outcome { JDecimal(value).toString() }, outcome { BigDecimalCore(value).toString() }, "$value/exact")
            assertEquals(outcome { JDecimal.valueOf(value).toString() }, outcome { BigDecimalCore.shortest(value).toString() }, "$value/shortest")
            if (value.isFinite()) {
                check(JDecimal(value), BigDecimalCore(value), "$value/exact fields", false)
                for (mode in RoundingMode.entries) {
                    val context = MathContext(7, mode)
                    assertEquals(outcome { JDecimal(value, context).toString() }, outcome { BigDecimalCore(value).round(context).toString() }, "$value/$mode")
                }
            }
        }
    }
    @Test fun extremeScaleOperationsAvoidStringScaleLoss() {
        for (coefficient in listOf(0L, 1L, 10L, -1L)) for (scale in listOf(Int.MIN_VALUE, Int.MAX_VALUE)) {
            val j = JDecimal.valueOf(coefficient, scale); val k = BigDecimalCore.unscaled(coefficient, scale)
            check(j, k, "$coefficient/$scale")
            for (other in listOf(0L, 1L)) {
                val jd = JDecimal.valueOf(other, if (scale < 0) -1 else 1)
                val kd = BigDecimalCore.unscaled(other, if (scale < 0) -1 else 1)
                assertEquals(outcome { j.multiply(jd).toString() }, outcome { k.multiply(kd).toString() }, "$coefficient/$scale/$other/product")
            }
            for (shift in listOf(-1, 0, 1)) assertEquals(outcome { j.scaleByPowerOfTen(shift).toString() }, outcome { k.scaleByPowerOfTen(shift).toString() }, "$coefficient/$scale/$shift")
            assertEquals(j.toString(), decimalFromValue(k.value).toString())
            assertEquals(k.value, j.decimalValue())
        }
    }

}

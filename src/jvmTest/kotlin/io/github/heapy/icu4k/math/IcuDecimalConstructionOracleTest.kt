package io.github.heapy.icu4k.math

import com.ibm.icu.math.BigDecimal as JDecimal
import kotlin.random.Random
import kotlin.test.*

class IcuDecimalConstructionOracleTest {
    private val fields = listOf("ind", "form", "mant", "exp").associateWith { JDecimal::class.java.getDeclaredField(it).also { f -> f.isAccessible = true } }
    private fun state(j: JDecimal, k: IcuDecimalValue, label: String) {
        assertEquals(fields.getValue("ind").getByte(j), k.ind, label); assertEquals(fields.getValue("form").getByte(j), k.form, label)
        assertEquals(fields.getValue("exp").getInt(j), k.exp, label); assertContentEquals(fields.getValue("mant").get(j) as ByteArray, k.mant, label)
        assertEquals(j.toString(), k.toString(), label)
        for ((jConstant, kConstant) in listOf(JDecimal.ZERO to IcuDecimalValue.ZERO_VALUE, JDecimal.ONE to IcuDecimalValue.ONE_VALUE, JDecimal.TEN to IcuDecimalValue.TEN_VALUE)) {
            assertEquals(j === jConstant, k === kConstant, "object identity $label")
            assertEquals(fields.getValue("mant").get(j) === fields.getValue("mant").get(jConstant), k.mant === kConstant.mant, "mantissa identity $label")
        }
    }
    @Test fun primitiveConstructorsAndCachedFactories() {
        val random = Random(834011)
        val ints = listOf(Int.MIN_VALUE, Int.MAX_VALUE, -10, -9, -1, 0, 1, 9, 10) + List(800) { random.nextInt() }
        for (input in ints) state(JDecimal(input), IcuDecimalValue.fromInt(input), "Int $input")
        val longs = listOf(Long.MIN_VALUE, Long.MAX_VALUE, -10L, -1L, 0L, 1L, 10L) + List(800) { random.nextLong() }
        for (input in longs) {
            state(JDecimal(input), IcuDecimalValue.fromLong(input), "Long $input")
            state(JDecimal.valueOf(input), IcuDecimalValue.valueOf(input), "valueOf $input")
            for (scale in listOf(-1, 0, 1, 7, 25)) {
                val j = runCatching { JDecimal.valueOf(input, scale) }; val k = runCatching { IcuDecimalValue.valueOf(input, scale) }
                assertEquals(j.exceptionOrNull()?.javaClass, k.exceptionOrNull()?.javaClass); assertEquals(j.exceptionOrNull()?.message, k.exceptionOrNull()?.message)
                if (j.isSuccess) state(j.getOrThrow(), k.getOrThrow(), "$input/$scale")
            }
        }
    }
    @Test fun exactAndShortestDouble() {
        val random = Random(93382)
        val values = listOf(0.0, -0.0, 0.1, 0.3, 1e-5, 1e20, Double.MIN_VALUE, Double.MAX_VALUE, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY) + List(350) { Double.fromBits(random.nextLong()) }
        for (value in values) {
            val j = runCatching { JDecimal(value) }; val k = runCatching { IcuDecimalValue.fromDouble(value) }
            assertEquals(j.exceptionOrNull()?.javaClass, k.exceptionOrNull()?.javaClass, "$value exact")
            assertEquals(j.exceptionOrNull()?.message, k.exceptionOrNull()?.message, "$value exact")
            if (j.isSuccess) state(j.getOrThrow(), k.getOrThrow(), "$value exact")
            val jv = runCatching { JDecimal.valueOf(value) }; val kv = runCatching { IcuDecimalValue.valueOf(value) }
            assertEquals(jv.exceptionOrNull()?.javaClass, kv.exceptionOrNull()?.javaClass, "$value shortest")
            assertEquals(jv.exceptionOrNull()?.message, kv.exceptionOrNull()?.message, "$value shortest")
            if (jv.isSuccess) state(jv.getOrThrow(), kv.getOrThrow(), "$value shortest")
        }
    }
    @Test fun standardDecimalAndArbitraryIntegerConversions() {
        val values = listOf("0.000", "1.2300", "-0.0123", "1E+20", "-1E-20", "18446744073709551617", "-18446744073709551617.999", "1234567890123456789012345678901234567890.000")
        for (text in values) {
            val standard = java.math.BigDecimal(text)
            state(JDecimal(standard), IcuDecimalValue.fromStandardBigDecimal(standard), text)
            val j = JDecimal(text); val k = IcuDecimalValue.parse(text)
            assertEquals(j.toBigInteger().toString(), k.integerText(), text)
            val je = runCatching { j.toBigIntegerExact().toString() }; val ke = runCatching { k.exactIntegerText() }
            assertEquals(je.exceptionOrNull()?.message, ke.exceptionOrNull()?.message, text); assertEquals(je.getOrNull(), ke.getOrNull(), text)
            assertEquals(j.unscaledValue().toString(), k.unscaledText(), text)
            assertEquals(j.toLong(), k.lowLongValue(), text); assertEquals(j.toInt(), k.lowIntValue(), text)
            assertEquals(j.toBigDecimal(), k.toStandardBigDecimal(), text)
            val integer = java.math.BigInteger(k.integerText())
            state(JDecimal(integer), IcuDecimalValue.fromIntegerText(integer.toString()), "integer $text")
            for (scale in listOf(-1, 0, 3, 20)) {
                val ji = runCatching { JDecimal(integer, scale) }; val ki = runCatching { IcuDecimalValue.fromIntegerText(integer.toString(), scale) }
                assertEquals(ji.exceptionOrNull()?.message, ki.exceptionOrNull()?.message)
                if (ji.isSuccess) state(ji.getOrThrow(), ki.getOrThrow(), "$text/$scale")
            }
        }
    }
}

package io.github.heapy.icu4k.math

import com.ibm.icu.math.BigDecimal as JDecimal
import com.ibm.icu.math.MathContext as JContext
import java.lang.reflect.InvocationTargetException
import kotlin.random.Random
import kotlin.test.*

class IcuDecimalValueOracleTest {
    private val fields = listOf("ind", "form", "mant", "exp").associateWith { JDecimal::class.java.getDeclaredField(it).also { f -> f.isAccessible = true } }
    private val round = JDecimal::class.java.getDeclaredMethod("round", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType).also { it.isAccessible = true }
    private val finish = JDecimal::class.java.getDeclaredMethod("finish", JContext::class.java, Boolean::class.javaPrimitiveType).also { it.isAccessible = true }
    private fun compare(j: JDecimal, k: IcuDecimalValue, label: String) {
        assertEquals(fields.getValue("ind").getByte(j), k.ind, label)
        assertEquals(fields.getValue("form").getByte(j), k.form, label)
        assertEquals(fields.getValue("exp").getInt(j), k.exp, label)
        assertContentEquals(fields.getValue("mant").get(j) as ByteArray, k.mant, label)
        if (k.form.toInt() != 0 || kotlin.math.abs(k.exp.toLong()) + k.mant.size < 2000) assertEquals(j.toString(), k.toString(), label)
    }
    private fun failure(block: () -> Any?): Throwable? = runCatching(block).exceptionOrNull()?.let { if (it is InvocationTargetException) it.cause else it }
    @Test fun stringAndCharArrayConstruction() {
        val random = Random(249010)
        val samples = mutableListOf("", "-", "+", ".", "0", "-0.000", "0.00E-9", "0001.2300", ".50", "5.", "1e2", "-12.34e-6", "١٢٣.٤٥E+٦", "１２３.４", "𝟙", "1e999999999", "10e999999999", "1e-999999999", "0e999999999", "1e0000000000", "1E+", "1.2.3", " 1", "1x", "0e-000000001")
        repeat(1500) { samples += buildString { repeat(random.nextInt(0, 25)) { append("012٣４.0eE+- x"[random.nextInt(13)]) } } }
        for (input in samples) {
            val j = runCatching { JDecimal(input) }; val k = runCatching { IcuDecimalValue.parse(input) }
            assertEquals(j.exceptionOrNull()?.javaClass, k.exceptionOrNull()?.javaClass, input)
            assertEquals(j.exceptionOrNull()?.message, k.exceptionOrNull()?.message, input)
            if (j.isSuccess) compare(j.getOrThrow(), k.getOrThrow(), input)
        }
        for (input in listOf("xx-12.30E+2yy", "xx١٢.٣yy", "", "1")) {
            val chars = input.toCharArray()
            for (offset in -1..chars.size + 1) for (length in -1..chars.size + 1) {
                val j = runCatching { JDecimal(chars, offset, length) }; val k = runCatching { IcuDecimalValue.parse(chars, offset, length) }
                assertEquals(j.exceptionOrNull()?.javaClass, k.exceptionOrNull()?.javaClass, "$input/$offset/$length")
                if (j.exceptionOrNull() is NumberFormatException) assertEquals(j.exceptionOrNull()?.message, k.exceptionOrNull()?.message)
                if (j.isSuccess) compare(j.getOrThrow(), k.getOrThrow(), "$input/$offset/$length")
            }
        }
    }
    @Test fun lostDigitChecking() {
        val method = JDecimal::class.java.getDeclaredMethod("checkdigits", JDecimal::class.java, Int::class.javaPrimitiveType).also { it.isAccessible = true }
        for (lhs in listOf("0.000", "1.2300", "-12.345", "1E+999999999")) for (rhs in listOf(null, "1.000", "9.8765")) for (digits in -1..8) {
            val j = JDecimal(lhs); val k = IcuDecimalValue.parse(lhs)
            val jr = rhs?.let { JDecimal(it) }; val kr = rhs?.let { IcuDecimalValue.parse(it) }
            val jf = failure { method.invoke(j, jr, digits) }; val kf = failure { k.checkDigits(kr, digits) }
            assertEquals(jf?.javaClass, kf?.javaClass); assertEquals(jf?.message, kf?.message)
            compare(j, k, "$lhs/$rhs/$digits")
        }
    }
    @Test fun roundingAndFailureState() {
        val samples = listOf("0.000", "1.2500", "-1.2501", "9999", "-9999", "0.000001", "500", "-500", "1e999999999", "9.99e999999999")
        for (input in samples) for (length in -3..9) for (mode in -1..8) {
            val j = JDecimal(input); val k = IcuDecimalValue.parse(input)
            val jf = failure { round.invoke(j, length, mode) }; val kf = failure { k.round(length, mode) }
            val label = "$input/$length/$mode"
            assertEquals(jf?.javaClass, kf?.javaClass, label); assertEquals(jf?.message, kf?.message, label)
            compare(j, k, label)
        }
    }
    @Test fun finishingAndEngineeringBoundaries() {
        val random = Random(740192)
        repeat(4000) {
            val j = JDecimal("1"); val k = IcuDecimalValue.parse("1")
            val mant = ByteArray(random.nextInt(1, 24)) { random.nextInt(10).toByte() }
            val exp = if (it < 100) listOf(-1000000002, -999999999, 999999998, 999999999, 1000000000)[it % 5] else random.nextInt(-40, 40)
            val sign = if (it % 2 == 0) 1.toByte() else (-1).toByte()
            fields.getValue("mant").set(j, mant.copyOf()); fields.getValue("exp").setInt(j, exp); fields.getValue("ind").setByte(j, sign)
            k.mant = mant.copyOf(); k.exp = exp; k.ind = sign
            val digits = random.nextInt(0, 15); val form = random.nextInt(3); val mode = random.nextInt(8); val strip = random.nextBoolean()
            val jc = JContext(digits, form, false, mode); val kc = MathContext(digits, form, false, mode)
            val jf = failure { finish.invoke(j, jc, strip) }; val kf = failure { k.finish(kc, strip) }
            val label = "$it/$exp/$digits/$form/$mode/$strip"
            assertEquals(jf?.javaClass, kf?.javaClass, label); assertEquals(jf?.message, kf?.message, label)
            compare(j, k, label)
        }
    }
}

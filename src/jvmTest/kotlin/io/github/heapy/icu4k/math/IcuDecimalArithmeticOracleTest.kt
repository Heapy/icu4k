package io.github.heapy.icu4k.math

import com.ibm.icu.math.BigDecimal as JDecimal
import com.ibm.icu.math.MathContext as JContext
import kotlin.random.Random
import kotlin.test.*

class IcuDecimalArithmeticOracleTest {
    private val fields = listOf("ind", "form", "mant", "exp").associateWith { JDecimal::class.java.getDeclaredField(it).also { f -> f.isAccessible = true } }
    private fun state(j: JDecimal, k: IcuDecimalValue, label: String) {
        assertEquals(fields.getValue("ind").getByte(j), k.ind, label); assertEquals(fields.getValue("form").getByte(j), k.form, label)
        assertEquals(fields.getValue("exp").getInt(j), k.exp, label); assertContentEquals(fields.getValue("mant").get(j) as ByteArray, k.mant, label)
        if (k.form.toInt() != 0 || kotlin.math.abs(k.exp.toLong()) + k.mant.size < 2000) assertEquals(j.toString(), k.toString(), label)
    }
    private fun compare(left: String, right: String, digits: Int, form: Int, round: Int, lost: Boolean, alias: Boolean = false) {
        val jl = JDecimal(left); val kl = IcuDecimalValue.parse(left)
        val jr = if (alias) jl else JDecimal(right); val kr = if (alias) kl else IcuDecimalValue.parse(right)
        val jc = JContext(digits, form, lost, round); val kc = MathContext(digits, form, lost, round)
        val jOps: List<() -> Any?> = listOf({ jl.add(jr, jc) }, { jl.subtract(jr, jc) }, { jl.multiply(jr, jc) }, { jl.compareTo(jr, jc) }, { jl.max(jr, jc) }, { jl.min(jr, jc) }, { jl.plus(jc) }, { jl.negate(jc) }, { jl.abs(jc) })
        val kOps: List<() -> Any?> = listOf({ kl.add(kr, kc) }, { kl.subtract(kr, kc) }, { kl.multiply(kr, kc) }, { kl.compareTo(kr, kc) }, { kl.max(kr, kc) }, { kl.min(kr, kc) }, { kl.plus(kc) }, { kl.negate(kc) }, { kl.abs(kc) })
        val jlMant = fields.getValue("mant").get(jl); val jrMant = fields.getValue("mant").get(jr)
        val klMant = kl.mant; val krMant = kr.mant
        for (op in jOps.indices) {
            val label = "$left/$right/$digits/$form/$round/$lost/$alias/$op"
            val expected = runCatching(jOps[op]); val actual = runCatching(kOps[op])
            assertEquals(expected.exceptionOrNull()?.javaClass, actual.exceptionOrNull()?.javaClass, label)
            if (expected.exceptionOrNull() !is NullPointerException) assertEquals(expected.exceptionOrNull()?.message, actual.exceptionOrNull()?.message, label)
            val j = expected.getOrNull(); val k = actual.getOrNull()
            if (j is JDecimal) {
                state(j, k as IcuDecimalValue, label)
                assertEquals(j === jl, k === kl, "left identity $label"); assertEquals(j === jr, k === kr, "right identity $label")
                assertEquals(j === JDecimal.ZERO, k === IcuDecimalValue.ZERO_VALUE, "zero identity $label")
                assertEquals(fields.getValue("mant").get(j) === jlMant, k.mant === klMant, "left digits $label")
                assertEquals(fields.getValue("mant").get(j) === jrMant, k.mant === krMant, "right digits $label")
            } else if (j != null) assertEquals(j, k, label)
            state(jl, kl, "left remains $label"); state(jr, kr, "right remains $label")
            assertSame(jlMant, fields.getValue("mant").get(jl)); assertSame(jrMant, fields.getValue("mant").get(jr))
            assertSame(klMant, kl.mant); assertSame(krMant, kr.mant)
        }
    }
    @Test fun alignedSignsScalesAndContextMatrix() {
        val values = listOf("0", "0.000", "1", "-1", "1.2300", "-1.2300", "99999", "0.000000123", "-0.000000123", "12E+4", "9.995", "-9.995", "12345678901234567890.1234")
        for (left in values) for (right in values) for (digits in listOf(0, 1, 3, 9)) for (form in 0..2) for (round in 0..7) compare(left, right, digits, form, round, false)
    }
    @Test fun randomRoundingLostDigitsAndSharedOperands() {
        val random = Random(922814)
        fun number(): String = buildString {
            if (random.nextBoolean()) append('-')
            repeat(random.nextInt(1, 24)) { append(('0'.code + random.nextInt(10)).toChar()) }
            append("E"); append(random.nextInt(-25, 26))
        }
        repeat(1800) { compare(number(), number(), random.nextInt(0, 21), random.nextInt(3), random.nextInt(8), random.nextBoolean(), it % 5 == 0) }
        for (digits in listOf(1, 3, 9)) for (form in 0..2) for (round in 0..7) {
            // A precision avoids the enormous fixed-point padding inherent in unlimited arithmetic.
            compare("1E+999999999", "-9E+999999998", digits, form, round, false)
            compare("1E-999999999", "9E-999999999", digits, form, round, false)
        }
    }
    @Test fun defaultContextsAndNullOrder() {
        val j = JDecimal("12.300"); val k = IcuDecimalValue.parse("12.300")
        state(j.plus(), k.plus(), "plus"); state(j.negate(), k.negate(), "negate"); state(j.abs(), k.abs(), "abs")
        val jr = JDecimal("-1.20"); val kr = IcuDecimalValue.parse("-1.20")
        state(j.add(jr), k.add(kr), "add"); state(j.subtract(jr), k.subtract(kr), "subtract"); state(j.multiply(jr), k.multiply(kr), "multiply")
        assertEquals(j.compareTo(jr), k.compareTo(kr)); state(j.max(jr), k.max(kr), "max"); state(j.min(jr), k.min(kr), "min")
        for (lost in listOf(false, true)) {
            val jc = JContext(1, 1, lost, 4); val kc = MathContext(1, 1, lost, 4)
            val jOps = listOf<() -> Any?>({ j.add(null, jc) }, { j.subtract(null, jc) }, { j.multiply(null, jc) }, { j.compareTo(null, jc) }, { j.plus(null) }, { j.abs(null) }, { j.negate(null) })
            val kOps = listOf<() -> Any?>({ k.add(null, kc) }, { k.subtract(null, kc) }, { k.multiply(null, kc) }, { k.compareTo(null, kc) }, { k.plus(null) }, { k.abs(null) }, { k.negate(null) })
            for (i in jOps.indices) {
                val jf = runCatching(jOps[i]).exceptionOrNull(); val kf = runCatching(kOps[i]).exceptionOrNull()
                assertEquals(jf?.javaClass, kf?.javaClass, "$lost/$i")
                if (jf !is NullPointerException) assertEquals(jf?.message, kf?.message)
            }
        }
    }
}

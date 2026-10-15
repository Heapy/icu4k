package io.github.heapy.icu4k.math

import com.ibm.icu.math.BigDecimal as JDecimal
import com.ibm.icu.math.MathContext as JContext
import kotlin.random.Random
import kotlin.test.*

class IcuDecimalDivisionOracleTest {
    private val fields = listOf("ind", "form", "mant", "exp").associateWith { JDecimal::class.java.getDeclaredField(it).also { f -> f.isAccessible = true } }
    private fun state(j: JDecimal, k: IcuDecimalValue, label: String) {
        assertEquals(fields.getValue("ind").getByte(j), k.ind, label); assertEquals(fields.getValue("form").getByte(j), k.form, label)
        assertEquals(fields.getValue("exp").getInt(j), k.exp, label); assertContentEquals(fields.getValue("mant").get(j) as ByteArray, k.mant, label)
        if (k.form.toInt() != 0 || kotlin.math.abs(k.exp.toLong()) + k.mant.size < 2000) assertEquals(j.toString(), k.toString(), label)
    }
    private fun outcome(jBlock: () -> Any?, kBlock: () -> Any?, jl: JDecimal, kl: IcuDecimalValue, jr: JDecimal?, kr: IcuDecimalValue?, label: String) {
        val jm = fields.getValue("mant").get(jl); val km = kl.mant
        val expected = runCatching(jBlock); val actual = runCatching(kBlock)
        assertEquals(expected.exceptionOrNull()?.javaClass, actual.exceptionOrNull()?.javaClass, label)
        if (expected.exceptionOrNull() !is NullPointerException) assertEquals(expected.exceptionOrNull()?.message, actual.exceptionOrNull()?.message, label)
        val j = expected.getOrNull(); val k = actual.getOrNull()
        if (j is JDecimal) {
            state(j, k as IcuDecimalValue, label)
            assertEquals(j === jl, k === kl, "left identity $label"); assertEquals(j === jr, k === kr, "right identity $label")
            assertEquals(j === JDecimal.ZERO, k === IcuDecimalValue.ZERO_VALUE, "zero $label")
            assertEquals(j === JDecimal.ONE, k === IcuDecimalValue.ONE_VALUE, "one $label")
            assertEquals(fields.getValue("mant").get(j) === jm, k.mant === km, "shared digits $label")
        } else if (j != null) assertEquals(j, k, label)
        state(jl, kl, "unchanged $label"); assertSame(jm, fields.getValue("mant").get(jl)); assertSame(km, kl.mant)
        if (jr != null && kr != null) state(jr, kr, "rhs unchanged $label")
    }
    private fun divideCase(left: String, right: String, digits: Int, form: Int, round: Int, lost: Boolean) {
        val jl = JDecimal(left); val kl = IcuDecimalValue.parse(left)
        val jr = JDecimal(right); val kr = IcuDecimalValue.parse(right)
        val jc = JContext(digits, form, lost, round); val kc = MathContext(digits, form, lost, round)
        val label = "$left/$right/$digits/$form/$round/$lost"
        outcome({ jl.divide(jr, jc) }, { kl.divide(kr, kc) }, jl, kl, jr, kr, "D/$label")
        outcome({ jl.divideInteger(jr, jc) }, { kl.divideInteger(kr, kc) }, jl, kl, jr, kr, "I/$label")
        outcome({ jl.remainder(jr, jc) }, { kl.remainder(kr, kc) }, jl, kl, jr, kr, "R/$label")
    }
    @Test fun divisionContextsAndRandomResidues() {
        val values = listOf("0", "0.000", "1", "-1", "3", "7", "1.2300", "-999.5", "0.000123", "1E+5", "19.99", "12345678901234567890.1234")
        for (left in values) for (right in values) for (digits in listOf(0, 1, 3, 9)) for (form in 0..2) for (round in 0..7) divideCase(left, right, digits, form, round, false)
        val random = Random(211095)
        fun number() = buildString { if (random.nextBoolean()) append('-'); repeat(random.nextInt(1, 21)) { append(('0'.code + random.nextInt(10)).toChar()) }; append('E'); append(random.nextInt(-15, 16)) }
        repeat(2000) { divideCase(number(), number(), random.nextInt(0, 20), random.nextInt(3), random.nextInt(8), random.nextBoolean()) }
    }
    @Test fun explicitScalesDefaultsAndValidationOrder() {
        for (left in listOf("0.000", "1", "-1.25", "9.999", "1E+3")) for (right in listOf("0", "1", "3", "-16")) {
            val jl = JDecimal(left); val kl = IcuDecimalValue.parse(left); val jr = JDecimal(right); val kr = IcuDecimalValue.parse(right)
            for (scale in -1..7) for (round in -1..8) {
                outcome({ jl.divide(jr, scale, round) }, { kl.divide(kr, scale, round) }, jl, kl, jr, kr, "$left/$right/$scale/$round")
                outcome({ jl.setScale(scale, round) }, { kl.setScale(scale, round) }, jl, kl, null, null, "scale/$left/$scale/$round")
            }
            for (round in -1..8) outcome({ jl.divide(jr, round) }, { kl.divide(kr, round) }, jl, kl, jr, kr, "mode/$left/$right/$round")
            outcome({ jl.divide(jr) }, { kl.divide(kr) }, jl, kl, jr, kr, "default divide")
            outcome({ jl.divideInteger(jr) }, { kl.divideInteger(kr) }, jl, kl, jr, kr, "default integer")
            outcome({ jl.remainder(jr) }, { kl.remainder(kr) }, jl, kl, jr, kr, "default remainder")
            outcome({ jl.setScale(2) }, { kl.setScale(2) }, jl, kl, null, null, "default scale")
        }
    }
    @Test fun powersAndExactIntegerArguments() {
        for (left in listOf("0", "0.000", "1", "-1", "1.2345", "-2.5", "999")) for (right in listOf("-12", "-3", "-1", "0", "0.0000", "1", "2.00", "3", "9", "12", "0.5", "1000000000", "2147483648", "-2147483648")) {
            val jl = JDecimal(left); val kl = IcuDecimalValue.parse(left); val jr = JDecimal(right); val kr = IcuDecimalValue.parse(right)
            outcome({ jr.intValueExact() }, { kr.intValueExact() }, jr, kr, null, null, "exact $right")
            for (digits in listOf(0, 1, 3, 9)) for (form in 0..2) for (round in listOf(0, 4, 6, 7)) for (lost in listOf(false, true)) {
                val jc = JContext(digits, form, lost, round); val kc = MathContext(digits, form, lost, round)
                outcome({ jl.pow(jr, jc) }, { kl.pow(kr, kc) }, jl, kl, jr, kr, "power/$left/$right/$digits/$form/$round/$lost")
            }
            outcome({ jl.pow(jr) }, { kl.pow(kr) }, jl, kl, jr, kr, "default power/$left/$right")
        }
        for (text in listOf("2147483647", "-2147483648", "2147483648", "-2147483649", "5555555555", "0.000", "-1.00", "1.2")) {
            val j = JDecimal(text); val k = IcuDecimalValue.parse(text)
            outcome({ j.intValueExact() }, { k.intValueExact() }, j, k, null, null, "int/$text")
        }
    }
}

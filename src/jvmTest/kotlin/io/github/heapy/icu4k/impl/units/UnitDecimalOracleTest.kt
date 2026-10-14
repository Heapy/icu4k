package io.github.heapy.icu4k.impl.units

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import kotlin.random.Random
import kotlin.test.*

class UnitDecimalOracleTest {
    private fun check(expected: BigDecimal, actual: UnitDecimal, label: String) {
        assertEquals(expected.toString(), actual.toString(), label)
        assertEquals(expected.scale(), actual.scale, label)
        assertEquals(expected.precision(), actual.precision, label)
    }
    @Test fun arbitraryCoefficientsAndScalesMatchBigDecimal() {
        val random = Random(252872)
        fun number(): String = buildString {
            if (random.nextBoolean()) append('-')
            repeat(random.nextInt(1, 85)) { append(('0'.code + random.nextInt(10)).toChar()) }
            append('E'); append(random.nextInt(-100, 101))
        }
        repeat(800) {
            val a = number(); val b = number()
            val j = BigDecimal(a); val d = BigDecimal(b); val k = UnitDecimal(a); val e = UnitDecimal(b)
            check(j, k, a)
            check(j.add(d), k.add(e), "$a + $b")
            check(j.subtract(d), k.subtract(e), "$a - $b")
            check(j.multiply(d), k.multiply(e), "$a * $b")
            assertEquals(j.compareTo(d).coerceIn(-1, 1), k.compareTo(e), "$a compare $b")
            for (precision in listOf(1, 7, 34)) {
                val context = MathContext(precision, RoundingMode.HALF_EVEN)
                check(j.round(context), k.round(precision), "round $a/$precision")
                if (d.signum() != 0) check(j.divide(d, context), k.divide(e, precision), "$a / $b/$precision")
            }
            val power = random.nextInt(0, 10)
            check(j.pow(power), k.pow(power), "$a ^ $power")
            for (powerWithSign in listOf(power, -power)) {
                if (j.signum() == 0 && powerWithSign < 0) continue
                check(j.pow(powerWithSign, MathContext.DECIMAL128), k.powRounded(powerWithSign), "$a ^ $powerWithSign/34")
            }
        }
    }
    @Test fun exactDivisionZerosTiesAndScaleBoundaries() {
        for (a in listOf("0", "0.00", "0E+20", "1", "1.00", "100", "99999999999999999999999999999999995", "-1", "5E-100")) {
            for (b in listOf("1", "2", "8", "40", "1E-30", "1E+30", "3.000")) {
                check(BigDecimal(a).divide(BigDecimal(b), MathContext.DECIMAL128), UnitDecimal(a).divide(UnitDecimal(b)), "$a/$b")
            }
        }
        for (text in listOf("0E+2147483647", "1E2147483648", "1.0E2147483649", "0E-2147483647", "1E+2147483647", "1E-2147483647", ".5", "5.", "+0.00", "-000.000", "000001.00500")) check(BigDecimal(text), UnitDecimal(text), text)
        for (text in listOf("", ".", "+", "1e", "1e+", "1.2.3", "1E2147483649", "1E-2147483648", " 1", "NaN")) {
            assertFailsWith<NumberFormatException>(text) { BigDecimal(text) }
            assertFailsWith<NumberFormatException>(text) { UnitDecimal(text) }
        }
    }
}

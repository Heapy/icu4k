@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.impl.number

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import io.github.heapy.icu4k.impl.units.UnitDecimal
import kotlin.test.*
import com.ibm.icu.impl.number.DecimalQuantity_DualStorageBCD as JQuantity
import com.ibm.icu.impl.number.RoundingUtils as JDirection

class DecimalBcdRoundingOracleTest {
    private val base = JQuantity::class.java.superclass
    private fun field(j: JQuantity, name: String): java.lang.reflect.Field = base.getDeclaredField(name).also { it.isAccessible = true }
    private fun snapshot(j: JQuantity): DecimalBcdStorage {
        val k = DecimalBcdStorage()
        val precision = field(j, "precision").getInt(j)
        val get = JQuantity::class.java.getDeclaredMethod("getDigitPos", Int::class.javaPrimitiveType).also { it.isAccessible = true }
        for (i in 0 until precision) { k.setDigitPos(i, get.invoke(j, i) as Byte); k.precision = i + 1 }
        k.precision = precision; k.scale = field(j, "scale").getInt(j)
        k.isApproximate = field(j, "isApproximate").getBoolean(j)
        k.origDouble = field(j, "origDouble").getDouble(j); k.origDelta = field(j, "origDelta").getInt(j)
        k.exponent = field(j, "exponent").getInt(j)
        return k
    }
    private fun outcome(block: () -> Any?): Any? = try { block() } catch (e: ArithmeticException) { "ArithmeticException" }
    private fun compare(j: JQuantity, magnitude: Int, precision: Int, mode: RoundingMode, nickel: Boolean, label: String) {
        val exactOracle = JQuantity(j)
        val k = snapshot(j)
        val negative = j.isNegative
        val expected = outcome {
            if (nickel) j.roundToNickel(magnitude, MathContext(precision, mode)) else j.roundToMagnitude(magnitude, MathContext(precision, mode))
            j.toBigDecimal().toString()
        }
        val actual = outcome {
            DecimalBcdRounding.round(k, negative, magnitude, precision, mode.ordinal, nickel, "quantity") {
                // The rounding machine's exact-loading boundary; production Double loading is ported separately.
                exactOracle.roundToInfinity()
                val accurate = snapshot(exactOracle)
                k.copyBcdFrom(accurate); k.precision = accurate.precision; k.scale = accurate.scale; k.exponent = accurate.exponent
                k.isApproximate = false; k.origDouble = 0.0; k.origDelta = 0
            }
            k.toDecimal(negative).toString()
        }
        assertEquals(expected, actual, label)
        if (expected != "ArithmeticException") {
            assertEquals(j.checkHealth(), k.checkHealth(), label)
            assertEquals(field(j, "scale").getInt(j), k.scale, label)
            assertEquals(field(j, "precision").getInt(j), k.precision, label)
            assertEquals(j.isUsingBytes, k.usingBytes, label)
        }
    }
    @Test fun exactRoundingAndNickelMatchSource() {
        val values = listOf("0", "-0.00", "1.25", "1.75", "1.2500000000000001", "-1.25", "9.9999", "9999999999999999.99", "123456789012345678901234567890.12345", "0.000000000012345")
        for (value in values) for (magnitude in listOf(-14, -3, -1, 0, 2, 18)) for (precision in listOf(0, 2, 7)) for (mode in RoundingMode.entries) for (nickel in listOf(false, true)) compare(JQuantity(BigDecimal(value)), magnitude, precision, mode, nickel, "$value/$magnitude/$precision/$mode/$nickel")
    }
    @Test fun approximateDoubleBranchesMatchSource() {
        for (value in listOf(0.1, 1.005, 1.025, 1.075, 2.675, -2.675, 9.999999999999998, 12345.67890123456, 1e-100, Double.MIN_VALUE)) for (magnitude in listOf(-12, -2, 0, 3)) for (mode in RoundingMode.entries) for (nickel in listOf(false, true)) compare(JQuantity(value), magnitude, 0, mode, nickel, "$value/$magnitude/$mode/$nickel")
    }
    @Test fun directionTableMatchesSourceIncludingInvalidInputs() {
        for (mode in -1..9) for (section in -2..4) for (even in listOf(false, true)) for (negative in listOf(false, true)) {
            assertEquals(outcome { JDirection.getRoundingDirection(even, negative, section, mode, "quantity") }, outcome { DecimalRoundingDirection.getRoundingDirection(even, negative, section, mode, "quantity") })
            assertEquals(JDirection.roundsAtMidpoint(mode), DecimalRoundingDirection.roundsAtMidpoint(mode))
        }
    }
}

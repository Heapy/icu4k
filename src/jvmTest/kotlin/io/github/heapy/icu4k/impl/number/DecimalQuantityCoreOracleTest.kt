@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.impl.number

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import io.github.heapy.icu4k.impl.units.UnitDecimal
import kotlin.test.*
import com.ibm.icu.impl.number.DecimalQuantity_DualStorageBCD as JQuantity

class DecimalQuantityCoreOracleTest {
    private fun outcome(block: () -> Any?): Any? = try { block() } catch (e: ArithmeticException) { "ArithmeticException:${e.message}" }
    private fun check(j: JQuantity, k: DecimalQuantityCore, label: String) {
        assertEquals(j.toString(), k.toString(), label)
        assertEquals(j.toPlainString(), k.toPlainString(), label)
        assertEquals(j.toExponentString(), k.toExponentString(), label)
        assertEquals(j.toScientificString(), k.toScientificString(), label)
        assertEquals(j.positionFingerprint, k.getPositionFingerprint(), label)
        assertEquals(j.upperDisplayMagnitude, k.getUpperDisplayMagnitude(), label)
        assertEquals(j.lowerDisplayMagnitude, k.getLowerDisplayMagnitude(), label)
        assertEquals(outcome { j.magnitude }, outcome { k.getMagnitude() }, label)
        assertEquals(j.signum().name, k.signum().name, label)
        assertEquals(j.isHasIntegerValue, k.isHasIntegerValue(), label)
        assertEquals(j.checkHealth(), k.checkHealth(), label)
        assertEquals(j.exponent, k.getExponent(), label)
        for (m in -12..12) assertEquals(j.getDigit(m), k.getDigit(m), "$label/$m")
    }
    @Test fun mutationSchedulesMatchSource() {
        val random = kotlin.random.Random(59248)
        repeat(60) { schedule ->
            val text = "${random.nextLong()}E${random.nextInt(-10, 11)}"
            val j = JQuantity(BigDecimal(text)); val k = DecimalQuantityCore(UnitDecimal(text))
            repeat(35) { step ->
                when (random.nextInt(10)) {
                    0 -> { val value = random.nextInt(0, 6); j.setMinInteger(value); k.setMinInteger(value) }
                    1 -> { val value = random.nextInt(0, 6); j.setMinFraction(value); k.setMinFraction(value) }
                    2 -> { val value = random.nextInt(-3, 4); j.adjustMagnitude(value); k.adjustMagnitude(value) }
                    3 -> { j.negate(); k.negate() }
                    4 -> { val value = random.nextInt(0, 9); j.applyMaxInteger(value); k.applyMaxInteger(value) }
                    5 -> { val value = random.nextInt(-2, 3); j.adjustExponent(value); k.adjustExponent(value) }
                    6 -> { j.resetExponent(); k.resetExponent() }
                    7 -> { j.truncate(); k.truncate() }
                    8 -> { val digit = random.nextInt(10).toByte(); val zeros = random.nextInt(3); j.appendDigit(digit, zeros, false); k.appendDigit(digit, zeros, false) }
                    9 -> { val factor = random.nextInt(-3, 4).toString(); j.multiplyBy(BigDecimal(factor)); k.multiplyBy(UnitDecimal(factor)) }
                }
                check(j, k, "$schedule/$step")
                check(JQuantity(j), k.createCopy(), "copy $schedule/$step")
            }
        }
    }
    @Test fun roundedDecimalsAndDoublesMatchSource() {
        for (text in listOf("-0.0001", "1.23456", "99999999999999999999.9999", "-2.675")) for (mode in RoundingMode.entries.filter { it != RoundingMode.UNNECESSARY }) for (precision in listOf(0, 2, 7)) {
            val j = JQuantity(BigDecimal(text)); val k = DecimalQuantityCore(UnitDecimal(text))
            j.roundToIncrement(BigDecimal("0.03"), MathContext(precision, mode)); k.roundToIncrement(UnitDecimal("0.03"), precision, UnitDecimal.Rounding.valueOf(mode.name))
            check(j, k, "$text/$mode/$precision")
        }
        for (value in listOf(-0.0, 1.005, 2.675, Double.MIN_VALUE, Double.POSITIVE_INFINITY, Double.NaN)) {
            val j = JQuantity(value); val k = DecimalQuantityCore(value)
            j.roundToInfinity(); k.roundToInfinity()
            check(j, k, "$value")
            assertEquals(j.explicitExactDouble, k.explicitlyExactDouble)
            j.setToDouble(1.0); k.setToDouble(1.0); j.clear(); k.clear()
            assertEquals(j.explicitExactDouble, k.explicitlyExactDouble)
        }
    }
    @Test fun selfCopyAndOverflowPartialStateMatchSource() {
        val j = JQuantity(BigDecimal("-123.45")); val k = DecimalQuantityCore(UnitDecimal("-123.45"))
        j.setMinInteger(3); k.setMinInteger(3); j.adjustExponent(2); k.adjustExponent(2)
        j.copyFrom(j); k.copyFrom(k)
        check(j, k, "self copy")
        j.setToLong(1); k.setToDecimal(UnitDecimal.ONE)
        assertEquals(outcome { j.adjustMagnitude(Int.MAX_VALUE) }, outcome { k.adjustMagnitude(Int.MAX_VALUE) })
        assertEquals(j.toString(), k.toString())
        assertEquals(j.toScientificString(), k.toScientificString())
        j.adjustExponent(3); k.adjustExponent(3)
        assertEquals(outcome { j.resetExponent() }, outcome { k.resetExponent() })
        assertEquals(j.exponent, k.getExponent())
        j.setMinInteger(65536); k.setMinInteger(65536)
        assertEquals(j.positionFingerprint, k.getPositionFingerprint())
    }

}

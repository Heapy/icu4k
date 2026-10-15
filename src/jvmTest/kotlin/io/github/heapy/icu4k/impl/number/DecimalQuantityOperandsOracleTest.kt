@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.impl.number

import java.math.BigDecimal
import io.github.heapy.icu4k.impl.units.UnitDecimal
import kotlin.test.*
import com.ibm.icu.impl.number.DecimalQuantity_DualStorageBCD as JQuantity
import com.ibm.icu.text.PluralRules.Operand as JOperand

class DecimalQuantityOperandsOracleTest {
    private fun outcome(block: () -> Any?): Any? = try { block() } catch (e: Throwable) { e.javaClass.simpleName }
    @Test fun operandsLongAndFractionLimitsMatchSource() {
        val values = listOf("0", "-0.00", "1.20", "-1.2", "0.100000000000000001234567", "0.1234567890123456789012345", "123456789012345678901234567890", "9223372036854775807", "9223372036854775808", "-9223372036854775808", "9223372036854775808.1", "-99999999999999999999999", "1E-100", "1E+100")
        for (value in values) for (minimum in listOf(0, 3, 25)) for (exponent in listOf(-3, 0, 2, 20)) {
            val j = JQuantity(BigDecimal(value)); val k = DecimalQuantityCore(UnitDecimal(value))
            j.setMinFraction(minimum); k.setMinFraction(minimum); j.adjustExponent(exponent); k.adjustExponent(exponent)
            val label = "$value/$minimum/$exponent"
            assertEquals(outcome { j.fitsInLong() }, outcome { k.fitsInLong() }, label)
            assertEquals(j.toLong(true), k.toLong(true), label)
            if (outcome { j.fitsInLong() } == true) assertEquals(j.toLong(false), k.toLong(false), label)
            for (include in listOf(false, true)) assertEquals(j.toFractionLong(include), k.toFractionLong(include), label)
            for (operand in JOperand.entries) assertEquals(j.getPluralOperand(operand).toBits(), k.getPluralOperand(DecimalQuantityCore.Operand.valueOf(operand.name)).toBits(), "$label/$operand")
            assertEquals(j.toDouble().toBits(), k.toDouble().toBits(), label)
        }
    }
    @Test fun equalityAndHashMatchSourceIncludingApproximateValues() {
        for (a in listOf("0", "1.2", "1.3", "12.3", "-1.2", "12345678901234567890.1")) for (b in listOf("0", "1.2", "1.3", "12.3", "-1.2", "12345678901234567890.1")) for (exponent in listOf(0, 3)) {
            val ja = JQuantity(BigDecimal(a)); val jb = JQuantity(BigDecimal(b)); val ka = DecimalQuantityCore(UnitDecimal(a)); val kb = DecimalQuantityCore(UnitDecimal(b))
            jb.adjustExponent(exponent); kb.adjustExponent(exponent)
            assertEquals(ja == jb, ka == kb, "$a/$b/$exponent")
            assertEquals(ja.hashCode(), ka.hashCode(), a)
            assertEquals(jb.hashCode(), kb.hashCode(), b)
        }
        for (a in listOf(0.0, -0.0, 1.005, 2.675, Double.POSITIVE_INFINITY, Double.NaN)) {
            val j = JQuantity(a); val k = DecimalQuantityCore(a)
            assertEquals(j.hashCode(), k.hashCode(), "$a")
            assertEquals(j == JQuantity(j), k == k.createCopy(), "$a")
            j.roundToInfinity(); k.roundToInfinity()
            assertEquals(j.hashCode(), k.hashCode(), "$a exact")
            assertEquals(j.fitsInLong(), k.fitsInLong(), "$a exact")
            assertEquals(j.toDouble().toBits(), k.toDouble().toBits(), "$a exact")
        }
    }
    @Test fun fieldPositionAndAssertionGuardEvaluationMatchSource() {
        for (value in listOf("1.2", "0.1000000000000000012345")) {
            val j = JQuantity(BigDecimal(value)); val k = DecimalQuantityCore(UnitDecimal(value))
            j.setMinFraction(25); k.setMinFraction(25)
            val jp = com.ibm.icu.text.UFieldPosition(); val kp = io.github.heapy.icu4k.text.UFieldPosition()
            j.populateUFieldPosition(jp); k.populateUFieldPosition(kp)
            assertEquals(jp.countVisibleFractionDigits, kp.getCountVisibleFractionDigits())
            assertEquals(jp.fractionDigits, kp.getFractionDigits())
        }
        val j = JQuantity(1.005); val k = DecimalQuantityCore(1.005)
        // A regular FieldPosition must not evaluate operands on an approximate quantity.
        j.populateUFieldPosition(java.text.FieldPosition(0)); k.populateUFieldPosition(java.text.FieldPosition(0))
        assertEquals(outcome { JQuantity(BigDecimal("1.5")).toLong(false) }, outcome { DecimalQuantityCore(UnitDecimal("1.5")).toLong(false) })
        val increment = "1.0E2147483649"
        assertEquals(outcome { JQuantity(1).roundToIncrement(BigDecimal(increment), java.math.MathContext.DECIMAL128) }, outcome { DecimalQuantityCore(1L).roundToIncrement(UnitDecimal(increment), 34, UnitDecimal.Rounding.HALF_EVEN) })
    }

}

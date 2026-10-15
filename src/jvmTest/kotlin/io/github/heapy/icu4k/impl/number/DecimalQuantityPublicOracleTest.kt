@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.impl.number

import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import java.math.RoundingMode
import kotlin.test.*
import com.ibm.icu.impl.number.DecimalQuantity_DualStorageBCD as JQuantity
import com.ibm.icu.text.PluralRules as JRules
import io.github.heapy.icu4k.text.PluralRules

class DecimalQuantityPublicOracleTest {
    private fun check(j: JQuantity, k: DecimalQuantity_DualStorageBCD) {
        assertEquals(j.toString(), k.toString())
        assertEquals(j.toPlainString(), k.toPlainString())
        assertEquals(j.toScientificString(), k.toScientificString())
        assertEquals(j.toExponentString(), k.toExponentString())
        assertEquals(j.toBigDecimal(), k.toBigDecimal())
        assertEquals(j.isUsingBytes, k.isUsingBytes())
        assertEquals(j.checkHealth(), k.checkHealth())
        assertEquals(j.positionFingerprint, k.getPositionFingerprint())
        assertEquals(j.explicitExactDouble, k.explicitExactDouble)
        for (operand in PluralRules.Operand.entries) {
            assertEquals(j.getPluralOperand(JRules.Operand.valueOf(operand.name)), k.getPluralOperand(operand))
        }
        val a = StringBuilder("prefix:"); val b = StringBuilder("prefix:")
        j.toScientificString(a); k.toScientificString(b); assertEquals(a.toString(), b.toString())
    }
    @Test fun constructorsAndMutationBoundaryMatchSource() {
        val values: List<Number> = listOf(0, -7, Long.MIN_VALUE, 1.25f, 2.675, -0.0,
            BigInteger("123456789012345678901234567890"), BigDecimal("1.2300"))
        for (value in values) {
            val j = JQuantity(value); val k = DecimalQuantity_DualStorageBCD(value)
            j.roundToInfinity(); k.roundToInfinity(); check(j, k)
            j.setMinFraction(5); k.setMinFraction(5)
            j.setMinInteger(4); k.setMinInteger(4); check(j, k)
            check(JQuantity(j), k.createCopy() as DecimalQuantity_DualStorageBCD)
            j.roundToIncrement(BigDecimal("0.03"), MathContext(8, RoundingMode.HALF_EVEN))
            k.roundToIncrement(BigDecimal("0.03"), MathContext(8, RoundingMode.HALF_EVEN)); check(j, k)
            j.multiplyBy(BigDecimal("-2.5")); k.multiplyBy(BigDecimal("-2.5")); check(j, k)
            j.clear(); k.clear()
            j.appendDigit(3, 2, true); k.appendDigit(3, 2, true)
            j.appendDigit(7, 1, false); k.appendDigit(7, 1, false); check(j, k)
        }
        for (value in listOf<Number>(1.toByte(), 1.toShort(), java.util.concurrent.atomic.AtomicInteger(1))) {
            val expected = assertFailsWith<IllegalArgumentException> { JQuantity(value) }
            assertEquals(expected.message, assertFailsWith<IllegalArgumentException> { DecimalQuantity_DualStorageBCD(value) }.message)
        }
    }
    @Test fun fieldPositionAndNullFailureStateMatchSource() {
        val j = JQuantity(2.675); val k = DecimalQuantity_DualStorageBCD(2.675)
        assertFailsWith<NullPointerException> { j.roundToIncrement(BigDecimal("0.03"), null) }
        assertFailsWith<NullPointerException> { k.roundToIncrement(BigDecimal("0.03"), null) }
        check(j, k)
        j.setMinFraction(5); k.setMinFraction(5)
        val jp = com.ibm.icu.text.UFieldPosition(); val kp = io.github.heapy.icu4k.text.UFieldPosition()
        j.populateUFieldPosition(jp); k.populateUFieldPosition(kp)
        assertEquals(jp.countVisibleFractionDigits, kp.getCountVisibleFractionDigits())
        assertEquals(jp.fractionDigits, kp.getFractionDigits())
        val zero = DecimalQuantity_DualStorageBCD(-0.0)
        zero.multiplyBy(null)
        assertTrue(zero.isNegative())
        assertTrue(zero.isZeroish())
        k.explicitExactDouble = false; j.explicitExactDouble = false
        check(j, k)
    }
    @Test fun publicPluralAndCopyContracts() {
        val jr = JRules.parseDescription("one: i = 1 and v = 0; few: v = 2; other:")
        val kr = PluralRules.parseDescription("one: i = 1 and v = 0; few: v = 2; other:")
        for (text in listOf("1", "1.00", "2.25", "12345678901234567890.125")) {
            val j = JQuantity(BigDecimal(text)); val k = DecimalQuantity_DualStorageBCD(BigDecimal(text))
            j.setMinFraction(BigDecimal(text).scale()); k.setMinFraction(BigDecimal(text).scale())
            assertEquals(j.getStandardPlural(jr).name, k.getStandardPlural(kr).name)
            assertEquals(jr.select(j), kr.select(k))
            j.copyFrom(j); k.copyFrom(k); check(j, k)
        }
    }
}

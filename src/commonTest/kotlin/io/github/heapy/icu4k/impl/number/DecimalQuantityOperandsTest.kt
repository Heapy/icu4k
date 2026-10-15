package io.github.heapy.icu4k.impl.number

import io.github.heapy.icu4k.impl.units.UnitDecimal
import kotlin.test.*

class DecimalQuantityOperandsTest {
    @Test fun visibleFractionAndSuppressedExponentAffectOperands() {
        val q = DecimalQuantityCore(UnitDecimal("-1.2"))
        q.setMinFraction(3)
        assertEquals(1.0, q.getPluralOperand(DecimalQuantityCore.Operand.i))
        assertEquals(200.0, q.getPluralOperand(DecimalQuantityCore.Operand.f))
        assertEquals(2.0, q.getPluralOperand(DecimalQuantityCore.Operand.t))
        assertEquals(3.0, q.getPluralOperand(DecimalQuantityCore.Operand.v))
        assertEquals(1.0, q.getPluralOperand(DecimalQuantityCore.Operand.w))
        q.adjustExponent(2)
        assertEquals(-120L, q.toLong(true))
        assertEquals(2.0, q.getPluralOperand(DecimalQuantityCore.Operand.c))
        assertEquals(0.0, q.getPluralOperand(DecimalQuantityCore.Operand.w))
    }
    @Test fun longLimitsAndEqualityRetainSourceBehavior() {
        assertTrue(DecimalQuantityCore(UnitDecimal("9223372036854775807")).fitsInLong())
        assertTrue(DecimalQuantityCore(UnitDecimal("-9223372036854775808")).fitsInLong())
        assertFalse(DecimalQuantityCore(UnitDecimal("9223372036854775808")).fitsInLong())
        val large = DecimalQuantityCore(UnitDecimal("12345678901234567890"))
        assertEquals(345678901234567890L, large.toLong(true))
        val a = DecimalQuantityCore(UnitDecimal("1.2")); val b = a.createCopy()
        b.adjustExponent(3)
        assertEquals(a, b) // Source equality intentionally omits exponent.
        assertEquals(a.hashCode(), b.hashCode())
        assertEquals((-0.0).toBits(), DecimalQuantityCore(-0.0).toDouble().toBits())
    }
    @Test @Suppress("DEPRECATION") fun fieldPositionReceivesVisibleFractionMetadata() {
        val q = DecimalQuantityCore(UnitDecimal("1.2"))
        q.setMinFraction(3)
        val position = io.github.heapy.icu4k.text.UFieldPosition()
        q.populateUFieldPosition(position)
        assertEquals(3, position.getCountVisibleFractionDigits())
        assertEquals(200L, position.getFractionDigits())
        q.populateUFieldPosition(null)
        q.populateUFieldPosition(io.github.heapy.icu4k.text.FormatFieldPosition(0))
    }

}

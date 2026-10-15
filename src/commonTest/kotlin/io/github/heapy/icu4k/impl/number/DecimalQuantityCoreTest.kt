package io.github.heapy.icu4k.impl.number

import io.github.heapy.icu4k.impl.units.UnitDecimal
import kotlin.test.*

class DecimalQuantityCoreTest {
    @Test fun displayExponentCopyAndNegativeZero() {
        val quantity = DecimalQuantityCore(UnitDecimal("12.3"))
        quantity.setMinInteger(4); quantity.setMinFraction(3)
        assertEquals("0012.300", quantity.toPlainString())
        quantity.adjustExponent(2)
        assertEquals("1230.000", quantity.toPlainString())
        assertEquals("0012.300c2", quantity.toExponentString())
        val copy = quantity.createCopy(); copy.resetExponent()
        assertEquals(0, copy.getExponent())
        assertEquals(2, quantity.getExponent())
        val zero = DecimalQuantityCore(-0.0)
        zero.setMinFraction(3)
        assertEquals("-0", zero.toPlainString())
        assertEquals("-0.000", zero.toExponentString())
        assertEquals(Modifier.Signum.NEG_ZERO, zero.signum())
    }
    @Test fun loadingRoundingAndIncrementShareTheStateMachine() {
        val quantity = DecimalQuantityCore(2.675)
        quantity.roundToMagnitude(-2, 0, UnitDecimal.Rounding.HALF_EVEN)
        assertEquals("2.68", quantity.toPlainString())
        assertTrue(quantity.explicitlyExactDouble)
        quantity.setToDouble(1.0); quantity.clear()
        assertTrue(quantity.explicitlyExactDouble)
        quantity.setToDecimal(UnitDecimal("-0.01"))
        quantity.roundToIncrement(UnitDecimal("0.03"), 0, UnitDecimal.Rounding.HALF_EVEN)
        assertEquals("-0", quantity.toPlainString())
        quantity.setToDecimal(UnitDecimal("12345.67")); quantity.applyMaxInteger(3)
        assertEquals("345.67", quantity.toPlainString())
        assertNull(quantity.checkHealth())
    }
    @Test fun selfCopyRetainsSourceZeroingBehavior() {
        val quantity = DecimalQuantityCore(UnitDecimal("-123.45"))
        quantity.setMinInteger(3); quantity.adjustExponent(2)
        quantity.copyFrom(quantity)
        assertEquals("-0", quantity.toPlainString())
        assertEquals("-000", quantity.toExponentString())
        assertEquals(0, quantity.getExponent())
    }

}

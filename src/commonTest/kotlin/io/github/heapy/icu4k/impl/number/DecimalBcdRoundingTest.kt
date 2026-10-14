package io.github.heapy.icu4k.impl.number

import kotlin.test.*

class DecimalBcdRoundingTest {
    private fun quantity(value: Int, scale: Int) = DecimalBcdStorage().also { it.readIntToBcd(value); it.scale = scale; it.compact() }
    @Test fun magnitudeNickelAndCarryUseSourceRoundingRules() {
        val halfEven = quantity(125, -2)
        DecimalBcdRounding.round(halfEven, false, -1, 0, 6, false, "value") { error("exact input") }
        assertEquals("1.2", halfEven.toDecimal(false).toString())
        val nickel = quantity(125, -2)
        DecimalBcdRounding.round(nickel, false, -1, 0, 6, true, "value") { error("exact input") }
        assertEquals("1", nickel.toDecimal(false).toString())
        val carry = quantity(999, -2)
        DecimalBcdRounding.round(carry, false, -1, 0, 6, false, "value") { error("exact input") }
        assertEquals("1E+1", carry.toDecimal(false).toString())
        assertNull(carry.checkHealth())
    }
    @Test fun directionAndOverflowBoundaries() {
        assertTrue(DecimalRoundingDirection.getRoundingDirection(true, false, 2, 6, "value"))
        assertFalse(DecimalRoundingDirection.getRoundingDirection(false, false, 2, 6, "value"))
        assertEquals(Int.MAX_VALUE, DecimalBcdRounding.safeSubtract(Int.MAX_VALUE, -1))
        assertEquals(Int.MIN_VALUE, DecimalBcdRounding.safeSubtract(Int.MIN_VALUE, 1))
        assertEquals("Rounding is required on value", assertFailsWith<ArithmeticException> { DecimalRoundingDirection.getRoundingDirection(true, false, 1, 7, "value") }.message)
    }
}

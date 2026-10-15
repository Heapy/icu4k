package io.github.heapy.icu4k.impl.number

import kotlin.test.*

class DecimalQuantityGenericNumberJsTest {
    @Test fun genericNumbersRetainFractionSignAndSpecialValues() {
        val fraction: Number = 12.5
        val quantity = DecimalQuantity_DualStorageBCD(fraction)
        quantity.roundToInfinity()
        assertEquals("12.5", quantity.toPlainString())
        val negativeZero: Number = -0.0
        assertTrue(DecimalQuantity_DualStorageBCD(negativeZero).isNegative())
        val nan: Number = Double.NaN
        assertTrue(DecimalQuantity_DualStorageBCD(nan).isNaN())
        val infinity: Number = Double.POSITIVE_INFINITY
        assertTrue(DecimalQuantity_DualStorageBCD(infinity).isInfinite())
        val long: Number = 9007199254740993L
        val integer = DecimalQuantity_DualStorageBCD(long)
        assertEquals("9007199254740993", integer.toPlainString())
    }
}

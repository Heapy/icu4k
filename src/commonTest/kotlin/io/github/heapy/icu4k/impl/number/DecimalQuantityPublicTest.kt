package io.github.heapy.icu4k.impl.number

import io.github.heapy.icu4k.util.BigDecimal
import io.github.heapy.icu4k.util.MathContext
import io.github.heapy.icu4k.util.RoundingMode
import io.github.heapy.icu4k.text.PluralRules
import kotlin.test.*

class DecimalQuantityPublicTest {
    @Test fun publicNumericContractAcrossTargets() {
        val quantity = DecimalQuantity_DualStorageBCD(BigDecimal("123.456"))
        quantity.roundToIncrement(BigDecimal("0.03"), MathContext(0, RoundingMode.HALF_EVEN))
        assertEquals("123.45", quantity.toPlainString())
        quantity.setMinFraction(4)
        assertEquals("123.4500", quantity.toPlainString())
        assertEquals(4.0, quantity.getPluralOperand(PluralRules.Operand.v))
        val copy = quantity.createCopy()
        quantity.negate()
        assertEquals("123.4500", copy.toPlainString())
        assertEquals("-123.4500", quantity.toPlainString())
        assertNull(quantity.checkHealth())
        val builder = StringBuilder("value=")
        quantity.toPlainString(builder)
        assertEquals("value=-123.4500", builder.toString())
        val double = DecimalQuantity_DualStorageBCD(2.675)
        double.roundToInfinity()
        assertEquals("2.675", double.toPlainString())
        assertEquals("1.20c3", DecimalQuantity_DualStorageBCD.fromExponentString("1.20c3").toExponentString())
    }
}

package io.github.heapy.icu4k.number

import io.github.heapy.icu4k.impl.number.DecimalQuantity_DualStorageBCD
import io.github.heapy.icu4k.impl.number.DecimalFormatProperties
import io.github.heapy.icu4k.impl.number.RoundingUtils
import io.github.heapy.icu4k.util.BigDecimal
import io.github.heapy.icu4k.util.MathContext
import io.github.heapy.icu4k.util.RoundingMode
import kotlin.test.*

class ScaleTest {
    @Test fun normalizationAndMultiplication() {
        assertSame(Scale.none(), Scale.byBigDecimal(BigDecimal("1.000")))
        assertSame(Scale.powerOfTen(2), Scale.byDouble(100.0))
        assertSame(Scale.powerOfTen(3), Scale.byBigDecimal(BigDecimal("1000.0")))
        assertNotSame(Scale.none(), Scale.byDoubleAndPowerOfTen(1.0, 0))
        val q = DecimalQuantity_DualStorageBCD(BigDecimal("1.25"))
        val scale = Scale.byDoubleAndPowerOfTen(1.2, 2)
        scale.applyTo(q)
        assertEquals("150", q.toPlainString())
        scale.applyReciprocalTo(q)
        assertEquals("1.25", q.toPlainString())
        assertTrue(scale.isValid())
        assertFalse(Scale.none().isValid())
        val zero = DecimalQuantity_DualStorageBCD(7)
        Scale.byDouble(0.0).applyReciprocalTo(zero)
        assertEquals("7", zero.toPlainString()) // The source has no reciprocal for zero.
        Scale.byDouble(0.0).applyTo(zero)
        assertTrue(zero.isZeroish())
    }
    @Test fun contextsAndPropertyScale() {
        val p = DecimalFormatProperties().setRoundingMode(RoundingMode.DOWN)
        assertSame(RoundingUtils.mathContextUnlimited(RoundingMode.DOWN), RoundingUtils.getMathContextOrUnlimited(p))
        assertEquals(RoundingMode.HALF_UP, RoundingUtils.getMathContextOr34Digits(p).getRoundingMode())
        val mc = MathContext(9, RoundingMode.CEILING)
        p.setMathContext(mc).setMultiplier(BigDecimal("1.25")).setMagnitudeMultiplier(2)
        assertSame(mc, RoundingUtils.getMathContextOr34Digits(p))
        val q = DecimalQuantity_DualStorageBCD(3)
        RoundingUtils.scaleFromProperties(p)!!.applyTo(q)
        assertEquals("300", q.toPlainString())
        assertFailsWith<ArithmeticException> { Scale.byDouble(3.0).withMathContext(MathContext(0)) }
        assertFailsWith<NullPointerException> { Scale.byBigDecimal(null) }
    }
}

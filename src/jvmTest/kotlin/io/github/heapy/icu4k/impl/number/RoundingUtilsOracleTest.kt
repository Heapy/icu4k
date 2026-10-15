@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.impl.number

import com.ibm.icu.impl.number.RoundingUtils as JUtils
import com.ibm.icu.impl.number.DecimalFormatProperties as JProperties
import io.github.heapy.icu4k.number.Scale
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import kotlin.test.*

class RoundingUtilsOracleTest {
    private fun outcome(action: () -> Any?): Any? = try { action() } catch (e: Throwable) {
        when (e) { is NullPointerException -> "null"; else -> "${e.javaClass.simpleName}:${e.message}" }
    }
    private fun scale(value: Any?): List<Any?>? = value?.let { target -> listOf("magnitude", "arbitrary", "reciprocal", "mc").map {
        target.javaClass.getDeclaredField(it).apply { isAccessible = true }.get(target)?.toString()
    } }
    @Test fun directionsContextsAndSourceCacheSemantics() {
        for (even in listOf(false, true)) for (negative in listOf(false, true)) for (section in -1..5) for (mode in -2..10) {
            for (reference in listOf(null, "number")) assertEquals(
                outcome { JUtils.getRoundingDirection(even, negative, section, mode, reference) },
                outcome { RoundingUtils.getRoundingDirection(even, negative, section, mode, reference) })
            assertEquals(JUtils.roundsAtMidpoint(mode), RoundingUtils.roundsAtMidpoint(mode))
        }
        assertEquals(JUtils.DEFAULT_ROUNDING_MODE, RoundingUtils.DEFAULT_ROUNDING_MODE)
        assertEquals(JUtils.DEFAULT_MATH_CONTEXT_UNLIMITED, RoundingUtils.DEFAULT_MATH_CONTEXT_UNLIMITED)
        assertEquals(JUtils.DEFAULT_MATH_CONTEXT_34_DIGITS, RoundingUtils.DEFAULT_MATH_CONTEXT_34_DIGITS)
        val contexts = mutableListOf<MathContext>()
        for (mode in RoundingMode.entries + listOf(null)) {
            val j = JProperties().setRoundingMode(mode)
            val k = DecimalFormatProperties().setRoundingMode(mode)
            assertEquals(JUtils.getMathContextOrUnlimited(j), RoundingUtils.getMathContextOrUnlimited(k))
            assertEquals(JUtils.getMathContextOr34Digits(j), RoundingUtils.getMathContextOr34Digits(k))
            assertSame(RoundingUtils.getMathContextOr34Digits(k), RoundingUtils.getMathContextOr34Digits(k))
            if (mode != null) {
                assertEquals(JUtils.mathContextUnlimited(mode), RoundingUtils.mathContextUnlimited(mode))
                assertSame(RoundingUtils.mathContextUnlimited(mode), RoundingUtils.getMathContextOrUnlimited(k))
                contexts.add(RoundingUtils.getMathContextOr34Digits(k))
            }
            val explicit = MathContext(17, RoundingMode.DOWN)
            j.setMathContext(explicit); k.setMathContext(explicit)
            assertSame(explicit, RoundingUtils.getMathContextOrUnlimited(k))
            assertSame(explicit, RoundingUtils.getMathContextOr34Digits(k))
        }
        assertTrue(contexts.all { it.roundingMode == RoundingMode.HALF_UP })
        for (i in contexts.indices) for (j in 0 until i) assertNotSame(contexts[i], contexts[j])
        assertEquals(outcome { JUtils.mathContextUnlimited(null) }, outcome { RoundingUtils.mathContextUnlimited(null) })
        assertEquals(outcome { JUtils.getMathContextOrUnlimited(null) }, outcome { RoundingUtils.getMathContextOrUnlimited(null) })
        assertEquals(outcome { JUtils.getMathContextOr34Digits(null) }, outcome { RoundingUtils.getMathContextOr34Digits(null) })
    }
    @Test fun propertyScalePrecedenceAndRepeatedVirtualReads() {
        for (magnitude in listOf(0, -2, 2)) for (multiplier in listOf(null, "0", "1.25", "3")) {
            val j = JProperties().setMagnitudeMultiplier(magnitude).setMultiplier(multiplier?.let(::BigDecimal))
            val k = DecimalFormatProperties().setMagnitudeMultiplier(magnitude).setMultiplier(multiplier?.let(::BigDecimal))
            assertEquals(scale(JUtils.scaleFromProperties(j)), scale(RoundingUtils.scaleFromProperties(k)))
        }
        for (kind in 0..3) {
            val jt = mutableListOf<String>(); val kt = mutableListOf<String>()
            val j = object : JProperties() {
                var magnitudeReads = 0; var multiplierReads = 0
                override fun getMathContext(): MathContext? { jt.add("context"); return if (kind == 3) MathContext(4) else null }
                override fun getRoundingMode(): RoundingMode? { jt.add("mode"); return RoundingMode.DOWN }
                override fun getMagnitudeMultiplier(): Int { jt.add("magnitude"); return if (kind == 0) ++magnitudeReads else 0 }
                override fun getMultiplier(): BigDecimal? { jt.add("multiplier"); return if (kind == 1 && ++multiplierReads == 2) null else BigDecimal("1.25") }
            }
            val k = object : DecimalFormatProperties() {
                var magnitudeReads = 0; var multiplierReads = 0
                override fun getMathContext(): MathContext? { kt.add("context"); return if (kind == 3) MathContext(4) else null }
                override fun getRoundingMode(): RoundingMode? { kt.add("mode"); return RoundingMode.DOWN }
                override fun getMagnitudeMultiplier(): Int { kt.add("magnitude"); return if (kind == 0) ++magnitudeReads else 0 }
                override fun getMultiplier(): BigDecimal? { kt.add("multiplier"); return if (kind == 1 && ++multiplierReads == 2) null else BigDecimal("1.25") }
            }
            assertEquals(outcome { scale(JUtils.scaleFromProperties(j)) }, outcome { scale(RoundingUtils.scaleFromProperties(k)) })
            assertEquals(jt, kt)
        }
    }
}

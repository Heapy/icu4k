// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.util

import io.github.heapy.icu4k.impl.number.MathContextCore
import io.github.heapy.icu4k.impl.number.DecimalQuantityCore
import io.github.heapy.icu4k.impl.units.UnitDecimal
import com.ibm.icu.impl.number.DecimalQuantity_DualStorageBCD as JQuantity
import java.math.MathContext as JContext
import java.math.RoundingMode as JMode
import kotlin.test.*

class MathContextOracleTest {
    private fun outcome(block: () -> Any?): Any? = try { block() } catch (e: Throwable) { "${e.javaClass.simpleName}:${e.message}" }
    private fun check(j: JContext, k: MathContextCore, label: String) {
        assertEquals(j.precision, k.precision, label)
        assertEquals(j.roundingMode, k.roundingMode, label)
        assertEquals(j.toString(), k.toString(), label)
        assertEquals(j.hashCode(), k.hashCode(), label)
    }
    @Test fun portableValueAndValidationMatchJdkBoundary() {
        for (precision in listOf(Int.MIN_VALUE, -1, 0, 1, 7, 16, 34, 999, Int.MAX_VALUE)) for (mode in JMode.entries + listOf(null)) {
            val label = "$precision/$mode"
            assertEquals(outcome { JContext(precision, mode).toString() }, outcome { MathContextCore(precision, mode).toString() }, label)
            if (precision >= 0 && mode != null) check(JContext(precision, mode), MathContextCore(precision, mode), label)
        }
        for (text in listOf(null, "", "precision=1 roundingMode=DOWN", "precision=+2 roundingMode=HALF_EVEN", "precision=٢ roundingMode=UP", "precision=2147483647 roundingMode=UNNECESSARY", "precision=2147483648 roundingMode=UP", "precision=-1 roundingMode=DOWN", "precision=-1 roundingMode=bad", "precision=1  roundingMode=UP", "precision=1 roundingMode=up", "precision=1 roundingMode=UP ", " precision=1 roundingMode=UP", "precision=1\troundingMode=UP", "precision= roundingMode=UP", "precision=1 roundingMode=", "precision=1 roundingMode=UP extra")) {
            assertEquals(outcome { JContext(text).toString() }, outcome { MathContextCore.parse(text).toString() }, text)
        }
        for (i in -2..9) assertEquals(outcome { JMode.valueOf(i) }, outcome { RoundingModes.valueOf(i) })
        assertSame(JContext.UNLIMITED, MathContexts.UNLIMITED)
        assertSame(JContext.DECIMAL32, MathContexts.DECIMAL32)
        assertSame(JContext.DECIMAL64, MathContexts.DECIMAL64)
        assertSame(JContext.DECIMAL128, MathContexts.DECIMAL128)
        val typed: java.math.MathContext = MathContext(4, RoundingMode.DOWN)
        assertEquals(4, typed.precision)
    }
    @Test fun contextRoundingMatchesIcuQuantityForEveryMode() {
        for (text in listOf("0", "1.225", "-1.225", "12345.6789", "0.00000123456789", "123456789012345678901234567890")) for (precision in listOf(0, 1, 3, 7, 16, 34)) for (mode in JMode.entries) for (nickel in listOf(false, true)) {
            val j = JQuantity(java.math.BigDecimal(text)); val k = DecimalQuantityCore(UnitDecimal(text))
            val context = MathContext(precision, mode)
            fun source(): Any? { if (nickel) j.roundToNickel(-2, context) else j.roundToMagnitude(-2, context); return null }
            fun port(): Any? { if (nickel) k.roundToNickel(-2, context) else k.roundToMagnitude(-2, context); return null }
            val label = "$text/$precision/$mode/$nickel"
            assertEquals(outcome { source() }, outcome { port() }, label)
            assertEquals(j.toPlainString(), k.toPlainString(), label)
        }
    }
}

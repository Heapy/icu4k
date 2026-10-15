@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.number

import com.ibm.icu.number.Scale as JScale
import com.ibm.icu.impl.number.DecimalQuantity as JQuantity
import com.ibm.icu.impl.number.DecimalQuantity_DualStorageBCD as JBCD
import io.github.heapy.icu4k.impl.number.DecimalQuantity
import io.github.heapy.icu4k.impl.number.DecimalQuantity_DualStorageBCD
import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import java.math.RoundingMode
import java.lang.reflect.Proxy
import kotlin.random.Random
import kotlin.test.*

class ScaleOracleTest {
    private fun outcome(action: () -> Any?): Any? = try { action() } catch (e: Throwable) {
        when (e) { is NullPointerException -> "null"; is ArithmeticException -> "arithmetic"; else -> "${e.javaClass.simpleName}:${e.message}" }
    }
    private fun snapshot(scale: Any): List<Any?> = listOf("magnitude", "arbitrary", "reciprocal", "mc").map { name ->
        scale.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(scale)?.toString()
    }
    private fun jState(q: JBCD): List<Any?> = listOf(q.toPlainString(), q.toScientificString(), q.isNegative, q.isZeroish,
        q.exponent, q.upperDisplayMagnitude, q.lowerDisplayMagnitude, q.positionFingerprint, q.toDouble().toRawBits())
    private fun kState(q: DecimalQuantity_DualStorageBCD): List<Any?> = listOf(q.toPlainString(), q.toScientificString(), q.isNegative(), q.isZeroish(),
        q.getExponent(), q.getUpperDisplayMagnitude(), q.getLowerDisplayMagnitude(), q.getPositionFingerprint(), q.toDouble().toRawBits())
    private fun compareState(j: JBCD, k: DecimalQuantity_DualStorageBCD, label: String = "") {
        assertEquals(outcome { jState(j) }, outcome { kState(k) }, "$label/before-exact")
        j.roundToInfinity(); k.roundToInfinity()
        assertEquals(jState(j), kState(k), "$label/exact")
    }
    private fun compareApply(j: JScale, k: Scale) {
        val random = Random(6345)
        val doubles = listOf(0.0, -0.0, 0.1, -1.25, 1e-100, 1e100, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NaN) + List(12) { random.nextDouble(-1e9, 1e9) }
        for (number in doubles) for (inverse in listOf(false, true)) {
            val jq = JBCD(number); val kq = DecimalQuantity_DualStorageBCD(number)
            jq.setMinFraction(2); kq.setMinFraction(2)
            assertEquals(outcome { if (inverse) j.applyReciprocalTo(jq) else j.applyTo(jq) },
                outcome { if (inverse) k.applyReciprocalTo(kq) else k.applyTo(kq) }, "$number/$inverse")
            compareState(jq, kq, "$number/$inverse/${snapshot(j)}")
        }
        for (text in listOf("0", "-123456789012345678901.234500", "0.00001234567890123456789")) {
            val jq = JBCD(BigDecimal(text)); val kq = DecimalQuantity_DualStorageBCD(BigDecimal(text))
            assertEquals(outcome { j.applyTo(jq); j.applyReciprocalTo(jq) }, outcome { k.applyTo(kq); k.applyReciprocalTo(kq) })
            compareState(jq, kq)
        }
    }
    @Test fun factoriesContextsAndQuantityMutationsMatchSource() {
        val pairs = mutableListOf<Pair<JScale, Scale>>()
        for (power in listOf(-100, -3, -1, 0, 1, 2, 3, 8, 100)) pairs.add(JScale.powerOfTen(power) to Scale.powerOfTen(power))
        for (value in listOf("0", "0.000", "1.00", "100", "1000.000", "1E-4", "10000.0000", "-10", "-0.00120", "3", "1.25", "0.3333333333333333333333333333333333")) {
            pairs.add(JScale.byBigDecimal(BigDecimal(value)) to Scale.byBigDecimal(BigDecimal(value)))
        }
        for (value in listOf(-0.0, 0.0, 1.0, 100.0, 1000.0, 10.0, -0.1, 1.0 / 3.0)) {
            pairs.add(JScale.byDouble(value) to Scale.byDouble(value))
            for (power in listOf(-3, 0, 2)) pairs.add(JScale.byDoubleAndPowerOfTen(value, power) to Scale.byDoubleAndPowerOfTen(value, power))
        }
        for ((j, k) in pairs) {
            assertEquals(snapshot(j), snapshot(k))
            compareApply(j, k)
        }
        for (mode in RoundingMode.entries) for (precision in listOf(0, 1, 7, 34)) {
            val mc = MathContext(precision, mode)
            for ((j, k) in listOf(JScale.byDouble(3.0) to Scale.byDouble(3.0), JScale.byDouble(1.25) to Scale.byDouble(1.25), JScale.none() to Scale.none())) {
                assertEquals(outcome { snapshot(j.withMathContext(mc)) }, outcome { snapshot(k.withMathContext(mc)) })
                val js = try { j.withMathContext(mc) } catch (_: ArithmeticException) { continue }
                val ks = k.withMathContext(mc)
                compareApply(js, ks)
            }
        }
    }
    @Test fun singletonIdentityAndNullContexts() {
        assertSame(Scale.none(), Scale.powerOfTen(0))
        assertSame(Scale.powerOfTen(2), Scale.byBigDecimal(BigDecimal("100.000")))
        assertSame(Scale.powerOfTen(3), Scale.byDouble(1000.0))
        assertNotSame(Scale.none(), Scale.byDoubleAndPowerOfTen(1.0, 0))
        assertSame(Scale.none(), Scale.none().withMathContext(MathContext(34)))
        val js = JScale.none().withMathContext(null); val ks = Scale.none().withMathContext(null)
        assertEquals(snapshot(js), snapshot(ks))
        assertEquals(outcome { js.withMathContext(null) }, outcome { ks.withMathContext(null) })
        assertEquals(outcome { JScale.byDouble(3.0).withMathContext(null) }, outcome { Scale.byDouble(3.0).withMathContext(null) })
        assertEquals(outcome { JScale.byBigDecimal(null) }, outcome { Scale.byBigDecimal(null) })
        for (value in listOf(Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY)) {
            assertEquals(outcome { JScale.byDouble(value) }, outcome { Scale.byDouble(value) })
        }
        for (power in listOf(Int.MIN_VALUE, Int.MAX_VALUE)) {
            assertEquals(snapshot(JScale.powerOfTen(power)), snapshot(Scale.powerOfTen(power)))
        }
    }
    @Test fun quantityCallbacksAndPartialFailures() {
        for (power in listOf(Int.MIN_VALUE, -2, 0, 3, Int.MAX_VALUE)) for (inverse in listOf(false, true)) {
            for (failure in listOf(null, "adjustMagnitude", "multiplyBy", "getMagnitude", "roundToMagnitude")) {
                val jt = mutableListOf<String>(); val kt = mutableListOf<String>()
                fun proxy(type: Class<*>, trace: MutableList<String>): Any = Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, args ->
                    trace.add(method.name + ":" + (args?.map { it.toString() } ?: emptyList<String>()).joinToString(","))
                    if (method.name == failure) throw IllegalStateException("callback")
                    if (method.name == "getMagnitude") 9 else null
                }
                val jq = proxy(JQuantity::class.java, jt) as JQuantity
                val kq = proxy(DecimalQuantity::class.java, kt) as DecimalQuantity
                val j = JScale.byDoubleAndPowerOfTen(1.25, power)
                val k = Scale.byDoubleAndPowerOfTen(1.25, power)
                assertEquals(outcome { if (inverse) j.applyReciprocalTo(jq) else j.applyTo(jq) }, outcome { if (inverse) k.applyReciprocalTo(kq) else k.applyTo(kq) })
                assertEquals(jt, kt)
            }
        }
        assertEquals(outcome { JScale.none().applyTo(null) }, outcome { Scale.none().applyTo(null) })
        assertEquals(outcome { JScale.none().applyReciprocalTo(null) }, outcome { Scale.none().applyReciprocalTo(null) })
    }
    @Test fun arbitraryDecimalNormalizationCallbacks() {
        for (text in listOf("0.000", "3.00", "10.00", "-10.0", "3", "1E+1", "-1E+1")) {
            val jt = mutableListOf<String>(); val kt = mutableListOf<String>()
            fun decimal(trace: MutableList<String>) = object : BigDecimal(text) {
                override fun toByte(): Byte = toInt().toByte()
                override fun toShort(): Short = toInt().toShort()
                override fun compareTo(other: BigDecimal): Int { trace.add("compare:$other"); return super.compareTo(other) }
                override fun stripTrailingZeros(): BigDecimal { trace.add("strip"); return this }
                override fun precision(): Int { trace.add("precision"); return super.precision() }
                override fun unscaledValue(): BigInteger { trace.add("unscaled"); return super.unscaledValue() }
                override fun scale(): Int { trace.add("scale"); return super.scale() }
            }
            assertEquals(snapshot(JScale.byBigDecimal(decimal(jt))), snapshot(Scale.byBigDecimal(decimal(kt))))
            assertEquals(jt, kt)
        }
    }
}

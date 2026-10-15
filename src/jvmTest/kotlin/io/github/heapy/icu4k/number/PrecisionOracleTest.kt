@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.number

import com.ibm.icu.number.Precision as JPrecision
import com.ibm.icu.number.NumberFormatter.RoundingPriority as JPriority
import com.ibm.icu.number.NumberFormatter.TrailingZeroDisplay as JTrailing
import com.ibm.icu.impl.number.DecimalQuantity as JQuantity
import com.ibm.icu.impl.number.DecimalQuantity_DualStorageBCD as JBCD
import com.ibm.icu.impl.number.MultiplierProducer as JProducer
import com.ibm.icu.impl.number.RoundingUtils as JRounding
import com.ibm.icu.text.PluralRules as JRules
import com.ibm.icu.util.Currency as JCurrency
import com.ibm.icu.util.Currency.CurrencyUsage as JUsage
import io.github.heapy.icu4k.impl.number.DecimalQuantity
import io.github.heapy.icu4k.impl.number.DecimalQuantity_DualStorageBCD
import io.github.heapy.icu4k.impl.number.MultiplierProducer
import io.github.heapy.icu4k.impl.number.RoundingUtils
import io.github.heapy.icu4k.text.PluralRules
import io.github.heapy.icu4k.util.Currency
import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import java.math.RoundingMode
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Modifier
import java.lang.reflect.Proxy
import kotlin.random.Random
import kotlin.test.*

class PrecisionOracleTest {
    private val saved = mutableListOf<Triple<java.lang.reflect.Field, Any, Any?>>()
    @BeforeTest fun saveCaches() {
        for (type in listOf(JPrecision::class.java, Precision::class.java)) {
            val trailing = type.getDeclaredField("trailingZeroDisplay").apply { isAccessible = true }
            for (field in type.declaredFields.filter { Modifier.isStatic(it.modifiers) && type.isAssignableFrom(it.type) }) {
                field.isAccessible = true
                val value = field.get(null) ?: continue
                saved.add(Triple(trailing, value, trailing.get(value)))
                trailing.set(value, null)
            }
        }
    }
    @AfterTest fun restoreCaches() { for ((field, value, old) in saved) field.set(value, old) }
    private fun outcome(action: () -> Any?): Any? = try { action() } catch (e: Throwable) {
        when (e) {
            is NullPointerException -> "null"
            is ClassCastException -> "cast"
            is AssertionError -> "assertion:${e.message}"
            else -> "${e.javaClass.simpleName}:${e.message}"
        }
    }
    private fun normalize(value: Any?): Any? = when (value) { is Enum<*> -> value.name; is BigDecimal, is MathContext -> value.toString(); else -> value }
    private fun policy(value: Any): Pair<String, Map<String, Any?>> {
        val fields = sortedMapOf<String, Any?>()
        var type: Class<*>? = value.javaClass
        while (type != null && type != Any::class.java) {
            for (field in type.declaredFields.filterNot { Modifier.isStatic(it.modifiers) || it.isSynthetic }) {
                field.isAccessible = true; fields[field.name] = normalize(field.get(value))
            }
            type = type.superclass
        }
        return value.javaClass.simpleName to fields
    }
    private fun state(q: JBCD): List<Any?> = listOf(outcome { q.toPlainString() }, outcome { q.toScientificString() }, q.isZeroish, q.isNegative,
        q.positionFingerprint, outcome { q.upperDisplayMagnitude }, outcome { q.lowerDisplayMagnitude }, outcome { q.toDouble().toRawBits() })
    private fun state(q: DecimalQuantity_DualStorageBCD): List<Any?> = listOf(outcome { q.toPlainString() }, outcome { q.toScientificString() }, q.isZeroish(), q.isNegative(),
        q.getPositionFingerprint(), outcome { q.getUpperDisplayMagnitude() }, outcome { q.getLowerDisplayMagnitude() }, outcome { q.toDouble().toRawBits() })
    private fun policies(): List<Pair<JPrecision, Precision>> = buildList {
        add(JPrecision.unlimited() to Precision.unlimited())
        add(JPrecision.integer() to Precision.integer())
        for (n in listOf(0, 1, 2, 6)) {
            add(JPrecision.fixedFraction(n) to Precision.fixedFraction(n))
            add(JPrecision.minFraction(n) to Precision.minFraction(n))
            add(JPrecision.maxFraction(n) to Precision.maxFraction(n))
        }
        for (n in listOf(1, 2, 3, 7)) {
            add(JPrecision.fixedSignificantDigits(n) to Precision.fixedSignificantDigits(n))
            add(JPrecision.minSignificantDigits(n) to Precision.minSignificantDigits(n))
            add(JPrecision.maxSignificantDigits(n) to Precision.maxSignificantDigits(n))
        }
        add(JPrecision.minMaxSignificantDigits(2, 3) to Precision.minMaxSignificantDigits(2, 3))
        for (text in listOf("0.05", "0.050", "0.010", "0.5", "5E+2", "1E+2", "1.25", "0.0006", "999.99")) {
            add(JPrecision.increment(BigDecimal(text)) to Precision.increment(BigDecimal(text)))
        }
        for ((min, max) in listOf(0 to 0, 0 to 3, 2 to 4)) {
            val j = JPrecision.minMaxFraction(min, max); val k = Precision.minMaxFraction(min, max)
            add(j.withMinDigits(3) to k.withMinDigits(3)); add(j.withMaxDigits(2) to k.withMaxDigits(2))
            for (priority in JPriority.entries) for (minSig in listOf(-2, 1, 2)) {
                add(j.withSignificantDigits(minSig, 3, priority) to k.withSignificantDigits(minSig, 3, RoundingPriority.valueOf(priority.name)))
            }
        }
    }
    @Test fun allRoundingFamiliesValuesModesAndTrailingZeros() {
        val random = Random(9345)
        val values = listOf(0.0, -0.0, 1.0, 1.25, -1.25, 9.999, 99.95, 999.995, 0.0009999, 1e-40, 1e40,
            Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY) + List(10) { random.nextDouble(-1e6, 1e6) }
        for ((baseJ, baseK) in policies()) {
            assertEquals(policy(baseJ), policy(baseK))
            for (mode in RoundingMode.entries) for (precision in listOf(0, 5)) for (trailing in listOf(null, JTrailing.AUTO, JTrailing.HIDE_IF_WHOLE)) {
                val mc = MathContext(precision, mode)
                val j = baseJ.withMode(mc).trailingZeroDisplay(trailing)
                val k = baseK.withMode(mc).trailingZeroDisplay(trailing?.let { TrailingZeroDisplay.valueOf(it.name) })
                assertEquals(policy(j), policy(k))
                for (number in values) {
                    val jq = JBCD(number); val kq = DecimalQuantity_DualStorageBCD(number)
                    jq.setMinFraction(2); kq.setMinFraction(2)
                    val label = "${policy(j)}/$number"
                    assertEquals(outcome { j.apply(jq) }, outcome { k.apply(kq) }, label)
                    assertEquals(state(jq), state(kq), label)
                }
            }
        }
    }
    @Test fun validationAndCopyIdentity() {
        for (n in listOf(Int.MIN_VALUE, -1, 0, 1, 2, 999, 1000, Int.MAX_VALUE)) {
            for (name in listOf("fixedFraction", "minFraction", "maxFraction", "fixedSignificantDigits", "minSignificantDigits", "maxSignificantDigits")) {
                fun invoke(type: Class<*>): Any? = try { type.getMethod(name, Int::class.javaPrimitiveType).invoke(null, n)?.let(::policy) } catch (e: InvocationTargetException) { throw e.cause!! }
                assertEquals(outcome { invoke(JPrecision::class.java) }, outcome { invoke(Precision::class.java) }, "$name/$n")
            }
            for (max in listOf(-1, 0, 1, 999, 1000)) {
                assertEquals(outcome { policy(JPrecision.minMaxFraction(n, max)) }, outcome { policy(Precision.minMaxFraction(n, max)) })
                assertEquals(outcome { policy(JPrecision.minMaxSignificantDigits(n, max)) }, outcome { policy(Precision.minMaxSignificantDigits(n, max)) })
                assertEquals(outcome { policy(JPrecision.integer().withSignificantDigits(n, max, null)) }, outcome { policy(Precision.integer().withSignificantDigits(n, max, null)) })
            }
            assertEquals(outcome { policy(JPrecision.integer().withMinDigits(n)) }, outcome { policy(Precision.integer().withMinDigits(n)) })
            assertEquals(outcome { policy(JPrecision.integer().withMaxDigits(n)) }, outcome { policy(Precision.integer().withMaxDigits(n)) })
        }
        for (text in listOf(null, "-1", "0", "0.00")) assertEquals(outcome { JPrecision.increment(text?.let(::BigDecimal)) }, outcome { Precision.increment(text?.let(::BigDecimal)) })
        assertEquals(outcome { JPrecision.currency(null) }, outcome { Precision.currency(null) })
        assertSame(Precision.integer(), Precision.fixedFraction(0))
        assertSame(Precision.fixedFraction(2), Precision.fixedFraction(2))
        assertSame(Precision.maxFraction(6), Precision.maxFraction(6))
        assertSame(Precision.fixedSignificantDigits(2), Precision.fixedSignificantDigits(2))
        assertSame(Precision.increment(BigDecimal("0.05")), Precision.increment(BigDecimal("0.05")))
        assertNotSame(Precision.increment(BigDecimal("0.050")), Precision.increment(BigDecimal("0.050")))
        for ((j, k) in policies()) {
            assertEquals(j === j.withMode(MathContext(0, RoundingMode.HALF_EVEN)), k === k.withMode(MathContext(0, RoundingMode.HALF_EVEN)))
            assertNotSame(k, k.trailingZeroDisplay(null))
            assertEquals(outcome { j.withMode(null).withMode(null) }, outcome { k.withMode(null).withMode(null) })
            assertEquals(outcome { j.apply(null) }, outcome { k.apply(null) })
        }
    }
    @Test fun currencyResolutionAndCachedTrailingPolicyMutation() {
        for (code in listOf("USD", "JPY", "CHF", "CAD", "DKK", "BHD", "XXX")) for (usage in JUsage.entries) for (trailing in JTrailing.entries + listOf(null)) {
            val j = JPrecision.currency(usage).trailingZeroDisplay(trailing) as com.ibm.icu.number.CurrencyPrecision
            val k = Precision.currency(Currency.CurrencyUsage.valueOf(usage.name)).trailingZeroDisplay(trailing?.let { TrailingZeroDisplay.valueOf(it.name) }) as CurrencyPrecision
            val jr = j.withCurrency(JCurrency.getInstance(code)); val kr = k.withCurrency(Currency.getInstance(code))
            assertEquals(policy(jr), policy(kr), "$code/$usage/$trailing")
            for (value in listOf(0.0, 1.025, 1.075, 123.456)) {
                val jq = JBCD(value); val kq = DecimalQuantity_DualStorageBCD(value)
                assertEquals(outcome { jr.apply(jq) }, outcome { kr.apply(kq) })
                assertEquals(state(jq), state(kq))
            }
            assertEquals(policy(JPrecision.fixedFraction(2)), policy(Precision.fixedFraction(2)))
            assertEquals(outcome { j.withCurrency(null) }, outcome { k.withCurrency(null) })
        }
    }
    @Test fun multiplierSelectionAndSafePluralCopy() {
        val method = JPrecision::class.java.getDeclaredMethod("chooseMultiplierAndApply", JQuantity::class.java, JProducer::class.java).apply { isAccessible = true }
        val j = JPrecision.maxSignificantDigits(2); val k = Precision.maxSignificantDigits(2)
        for (value in listOf(0.0, 0.1, 999.0, 9999.0, -999.0)) {
            val jt = mutableListOf<Int>(); val kt = mutableListOf<Int>()
            val jq = JBCD(value); val kq = DecimalQuantity_DualStorageBCD(value)
            val jp = JProducer { magnitude -> jt.add(magnitude); -(magnitude / 3) * 3 }
            val kp = MultiplierProducer { magnitude -> kt.add(magnitude); -(magnitude / 3) * 3 }
            assertEquals(outcome { try { method.invoke(j, jq, jp) } catch (e: InvocationTargetException) { throw e.cause!! } },
                outcome { k.chooseMultiplierAndApply(kq, kp) })
            assertEquals(jt, kt); assertEquals(state(jq), state(kq))
        }
        val jr = JRules.parseDescription("one: n = 1")
        val kr = PluralRules.parseDescription("one: n = 1")
        for (value in listOf(1.0, 1.04, 1.4, 2.0)) {
            val jq = JBCD(value); val kq = DecimalQuantity_DualStorageBCD(value)
            val before = state(kq)
            assertEquals(JRounding.getPluralSafe(JPrecision.integer(), jr, jq).name,
                RoundingUtils.getPluralSafe(Precision.integer(), kr, kq).name)
            assertEquals(before, state(kq))
            assertEquals(outcome { JRounding.getPluralSafe(null, jr, jq).name }, outcome { RoundingUtils.getPluralSafe(null, kr, kq).name })
            jq.roundToInfinity(); kq.roundToInfinity()
            assertEquals(JRounding.getPluralSafe(null, jr, jq).name, RoundingUtils.getPluralSafe(null, kr, kq).name)
        }
    }
    @Test fun nullPriorityAndZeroSignificantSpecialCase() {
        val j = JPrecision.integer().withSignificantDigits(1, 3, null)
        val k = Precision.integer().withSignificantDigits(1, 3, null)
        val jq = JBCD(9.999); val kq = DecimalQuantity_DualStorageBCD(9.999)
        assertEquals(outcome { j.apply(jq) }, outcome { k.apply(kq) })
        assertEquals(state(jq), state(kq))
        val js = JPrecision.fixedSignificantDigits(3)
        val ks = Precision.fixedSignificantDigits(3) as Precision.SignificantRounderImpl
        val applyZero = js.javaClass.getDeclaredMethod("apply", JQuantity::class.java, Int::class.javaPrimitiveType).apply { isAccessible = true }
        for (number in listOf(0.0, 1.0)) for (minInt in listOf(0, 1, 3, 4)) {
            val a = JBCD(number); val b = DecimalQuantity_DualStorageBCD(number)
            assertEquals(outcome { try { applyZero.invoke(js, a, minInt); Unit } catch (e: InvocationTargetException) { throw e.cause!! } }, outcome { ks.apply(b, minInt) })
            assertEquals(state(a), state(b))
        }
    }
    @Test fun quantityCallbackOrderFailureAndBogusRounder() {
        for ((j, k) in policies().filterIndexed { index, _ -> index % 4 == 0 }) for (failure in listOf(null, "roundToMagnitude", "roundToIncrement", "roundToNickel", "setMinFraction", "getPluralOperand")) {
            val jt = mutableListOf<String>(); val kt = mutableListOf<String>()
            fun proxy(type: Class<*>, delegate: Any, trace: MutableList<String>): Any = Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, args ->
                trace.add(method.name + ":" + (args?.map { it.toString() } ?: emptyList<String>()).joinToString(","))
                if (method.name == failure) throw IllegalStateException("callback")
                try { method.invoke(delegate, *(args ?: emptyArray())) } catch (e: InvocationTargetException) { throw e.cause!! }
            }
            val jq = proxy(JQuantity::class.java, JBCD(9.999), jt) as JQuantity
            val kq = proxy(DecimalQuantity::class.java, DecimalQuantity_DualStorageBCD(9.999), kt) as DecimalQuantity
            assertEquals(outcome { j.trailingZeroDisplay(JTrailing.HIDE_IF_WHOLE).apply(jq) }, outcome { k.trailingZeroDisplay(TrailingZeroDisplay.HIDE_IF_WHOLE).apply(kq) })
            assertEquals(jt, kt, policy(j).toString())
        }
        val jb = JPrecision.BogusRounder().withMode(MathContext(7)).trailingZeroDisplay(JTrailing.HIDE_IF_WHOLE) as JPrecision.BogusRounder
        val kb = Precision.BogusRounder().withMode(MathContext(7)).trailingZeroDisplay(TrailingZeroDisplay.HIDE_IF_WHOLE) as Precision.BogusRounder
        assertEquals(policy(jb.into(JPrecision.fixedFraction(2))), policy(kb.into(Precision.fixedFraction(2))))
        assertEquals(outcome { jb.apply(null) }, outcome { kb.apply(null) })
        assertEquals(outcome { jb.into(null) }, outcome { kb.into(null) })
    }
    @Test fun incrementDecimalSubclassCallbacks() {
        for (text in listOf("0.05", "0.050", "0.010", "1.25", "5E+2")) {
            val jt = mutableListOf<String>(); val kt = mutableListOf<String>()
            fun decimal(trace: MutableList<String>): BigDecimal = object : BigDecimal(text) {
                override fun toByte(): Byte = toInt().toByte()
                override fun toShort(): Short = toInt().toShort()
                override fun compareTo(other: BigDecimal): Int { trace.add("compare:$other"); return super.compareTo(other) }
                override fun equals(other: Any?): Boolean { trace.add("equals:$other"); return super.equals(other) }
                override fun stripTrailingZeros(): BigDecimal { trace.add("strip"); return this }
                override fun precision(): Int { trace.add("precision"); return super.precision() }
                override fun scale(): Int { trace.add("scale"); return super.scale() }
                override fun unscaledValue(): BigInteger { trace.add("unscaled"); return super.unscaledValue() }
            }
            assertEquals(policy(JPrecision.increment(decimal(jt))), policy(Precision.increment(decimal(kt))))
            assertEquals(jt, kt)
        }
    }
}

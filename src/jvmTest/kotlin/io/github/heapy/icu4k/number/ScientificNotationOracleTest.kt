@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.number

import com.ibm.icu.number.Notation as JNotation
import com.ibm.icu.number.ScientificNotation as JScientific
import com.ibm.icu.number.NumberFormatter.SignDisplay as JSign
import com.ibm.icu.number.Precision as JPrecision
import com.ibm.icu.impl.number.DecimalQuantity as JQuantity
import com.ibm.icu.impl.number.DecimalQuantity_DualStorageBCD as JBCD
import com.ibm.icu.impl.number.MicroProps as JMicro
import com.ibm.icu.impl.number.MicroPropsGenerator as JGenerator
import com.ibm.icu.impl.number.Modifier as JModifier
import com.ibm.icu.impl.number.MultiplierProducer as JProducer
import com.ibm.icu.impl.FormattedStringBuilder as JBuffer
import com.ibm.icu.text.DecimalFormatSymbols as JSymbols
import com.ibm.icu.text.NumberFormat.Field as JField
import com.ibm.icu.util.ULocale as JLocale
import io.github.heapy.icu4k.impl.FormattedStringBuilder
import io.github.heapy.icu4k.impl.number.*
import io.github.heapy.icu4k.text.DecimalFormatSymbols
import io.github.heapy.icu4k.text.NumberFormatField
import io.github.heapy.icu4k.util.ULocale
import java.math.BigDecimal
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import kotlin.test.*

class ScientificNotationOracleTest {
    private fun outcome(action: () -> Any?): Any? = try { action() } catch (e: Throwable) {
        when (e) { is NullPointerException -> "null"; is IndexOutOfBoundsException -> "bounds"; is AssertionError -> "assert:${e.message}"; else -> "${e.javaClass.simpleName}:${e.message}" }
    }
    private val localeMethod = JScientific::class.java.getDeclaredMethod("withLocaleData", JSymbols::class.java, Boolean::class.javaPrimitiveType, JGenerator::class.java).apply { isAccessible = true }
    private fun handler(notation: JScientific, symbols: JSymbols?, safe: Boolean, parent: JGenerator?): JGenerator =
        try { localeMethod.invoke(notation, symbols, safe, parent) as JGenerator } catch (e: InvocationTargetException) { throw e.cause!! }
    private fun source(interval: Int, require: Boolean, min: Int = 1, sign: JSign? = JSign.AUTO): JScientific =
        JScientific::class.java.getDeclaredConstructor(Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType, Int::class.javaPrimitiveType, JSign::class.java)
            .apply { isAccessible = true }.newInstance(interval, require, min, sign)
    private fun fields(value: Any) = listOf("engineeringInterval", "requireMinInt", "minExponentDigits", "exponentSignDisplay").map {
        val field = value.javaClass.getDeclaredField(it).apply { isAccessible = true }
        val result = field.get(value)
        if (result is Enum<*>) result.name else result
    }
    private fun compare(j: JBuffer, k: FormattedStringBuilder, label: String) {
        assertEquals(j.toString().map { it.code }, k.toString().map { it.code }, label)
        assertEquals(j.toFieldArray().map { it?.toString()?.substringAfterLast('(') }, k.toFieldArray().map { it?.toString()?.substringAfterLast('(') }, label)
    }
    private fun modifiers(j: JModifier, k: Modifier, label: String) {
        for (offset in 0..1) {
            val jb = JBuffer().apply { append("[12]", JField.INTEGER) }
            val kb = FormattedStringBuilder().apply { append("[12]", NumberFormatField.INTEGER) }
            assertEquals(outcome { j.apply(jb, offset, 3) }, outcome { k.apply(kb, offset, 3) }, label)
            compare(jb, kb, label)
        }
        assertEquals(j.prefixLength, k.getPrefixLength()); assertEquals(j.codePointCount, k.getCodePointCount()); assertEquals(j.isStrong, k.isStrong())
        assertEquals(outcome { j.containsField(null) }, outcome { k.containsField(null) })
        assertEquals(outcome { j.parameters }, outcome { k.getParameters() })
        assertEquals(outcome { j.strictEquals(null) }, outcome { k.strictEquals(null) })
    }
    @Test fun publicFactoriesCopyAndValidation() {
        assertSame(Notation.scientific(), Notation.scientific()); assertSame(Notation.engineering(), Notation.engineering()); assertSame(Notation.simple(), Notation.simple())
        for ((j, k) in listOf(JNotation.scientific() to Notation.scientific(), JNotation.engineering() to Notation.engineering())) {
            assertEquals(fields(j), fields(k))
            for (digits in listOf(Int.MIN_VALUE, -1, 0, 1, 2, 999, 1000, Int.MAX_VALUE)) {
                assertEquals(outcome { fields(j.withMinExponentDigits(digits)) }, outcome { fields(k.withMinExponentDigits(digits)) })
            }
            for (sign in JSign.entries + listOf(null)) {
                val a = j.withExponentSignDisplay(sign); val b = k.withExponentSignDisplay(sign?.let { SignDisplay.valueOf(it.name) })
                assertEquals(fields(a), fields(b)); assertNotSame(j, a); assertNotSame(k, b)
            }
        }
    }
    @Test fun finiteZeroAndSpecialQuantitiesRoundingAndExponentFields() {
        for (interval in listOf(1, 3, 4)) for (require in listOf(false, true)) for (safe in listOf(false, true)) for (kind in 0..2) {
            val j = source(interval, require, 2, JSign.ALWAYS)
            val k = ScientificNotation(interval, require, 2, SignDisplay.ALWAYS)
            val jp = JMicro(true).apply { rounder = when (kind) { 0 -> JPrecision.maxSignificantDigits(3); 1 -> JPrecision.fixedSignificantDigits(4); else -> JPrecision.maxFraction(1) }.trailingZeroDisplay(null) }
            val kp = MicroProps(true).apply { rounder = when (kind) { 0 -> Precision.maxSignificantDigits(3); 1 -> Precision.fixedSignificantDigits(4); else -> Precision.maxFraction(1) }.trailingZeroDisplay(null) }
            val jg = handler(j, JSymbols(JLocale.ENGLISH), safe, jp)
            val kg = k.withLocaleData(DecimalFormatSymbols(ULocale("en")), safe, kp)
            for (number in listOf(0.0, -0.0, 1.0, 0.0012345, 999.999, -99999.9, 1e-30, 1e30, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
                val jq = JBCD(number); val kq = DecimalQuantity_DualStorageBCD(number)
                val label = "$interval/$require/$safe/$kind/$number"
                var jm: JMicro? = null; var km: MicroProps? = null
                assertEquals(outcome { jm = jg.processQuantity(jq); "ok" }, outcome { km = kg.processQuantity(kq); "ok" }, label)
                assertEquals(jq.exponent, kq.getExponent(), label)
                assertEquals(jq.toPlainString(), kq.toPlainString(), label)
                assertEquals(jq.toScientificString(), kq.toScientificString(), label)
                assertEquals(jq.positionFingerprint, kq.getPositionFingerprint(), label)
                if (jm != null && km != null) {
                    assertEquals(jm!!.rounder == null, km!!.rounder == null, label)
                    modifiers(jm!!.modInner, km!!.modInner!!, label)
                }
            }
        }
    }
    @Test fun localizedExponentStringsAllPoliciesAndUtf16DigitInsertion() {
        for (locale in listOf("en", "ar", "fa", "bn")) for (custom in listOf(false, true)) for (sign in JSign.entries + listOf(null)) {
            val js = JSymbols(JLocale(locale)); val ks = DecimalFormatSymbols(ULocale(locale))
            if (custom) {
                js.exponentSeparator = "x10^"; ks.setExponentSeparator("x10^")
                js.minusSignString = "minus"; ks.setMinusSignString("minus")
                js.plusSignString = "plus"; ks.setPlusSignString("plus")
                val digits = Array(10) { String(Character.toChars(0x1d7d8 + it)) }
                js.digitStrings = digits; ks.setDigitStrings(digits)
            }
            val jg = handler(source(1, false, 3, sign), js, true, JMicro(true).apply { rounder = JPrecision.maxSignificantDigits(3).trailingZeroDisplay(null) })
            val kg = ScientificNotation(1, false, 3, sign?.let { SignDisplay.valueOf(it.name) }).withLocaleData(ks, true, MicroProps(true).apply { rounder = Precision.maxSignificantDigits(3).trailingZeroDisplay(null) })
            for (number in listOf("1E-123", "1E-12", "1", "1E12", "1E123")) {
                val jm = jg.processQuantity(JBCD(BigDecimal(number)))
                val km = kg.processQuantity(DecimalQuantity_DualStorageBCD(BigDecimal(number)))!!
                modifiers(jm.modInner, km.modInner!!, "$locale/$custom/$sign/$number")
            }
        }
    }
    @Test fun cacheIdentityStrictEqualityAndLiveSymbols() {
        val js = JSymbols(JLocale.ENGLISH); val ks = DecimalFormatSymbols(ULocale("en"))
        val jg = handler(JNotation.scientific(), js, true, JMicro(true).apply { rounder = JPrecision.integer().trailingZeroDisplay(null) })
        val kg = Notation.scientific().withLocaleData(ks, true, MicroProps(true).apply { rounder = Precision.integer().trailingZeroDisplay(null) })
        for (power in listOf(-13, -12, 0, 12, 13)) {
            fun jm() = jg.processQuantity(JBCD(BigDecimal("1E$power"))).modInner
            fun km() = kg.processQuantity(DecimalQuantity_DualStorageBCD(BigDecimal("1E$power")))!!.modInner!!
            val a = jm(); val b = km()
            assertEquals(a === jm(), b === km(), "$power")
            assertEquals(a.strictEquals(jm()), b.strictEquals(km()))
            js.exponentSeparator = "exp"; ks.setExponentSeparator("exp")
            modifiers(a, b, "live/$power")
        }
        val j2 = handler(JNotation.scientific().withMinExponentDigits(4), JSymbols(JLocale("ar")), true, JMicro(true).apply { rounder = JPrecision.integer().trailingZeroDisplay(null) })
        val k2 = Notation.scientific().withMinExponentDigits(4).withLocaleData(DecimalFormatSymbols(ULocale("ar")), true, MicroProps(true).apply { rounder = Precision.integer().trailingZeroDisplay(null) })
        assertEquals(jg.processQuantity(JBCD(100)).modInner.strictEquals(j2.processQuantity(JBCD(100)).modInner),
            kg.processQuantity(DecimalQuantity_DualStorageBCD(100))!!.modInner!!.strictEquals(k2.processQuantity(DecimalQuantity_DualStorageBCD(100))!!.modInner))
    }
    @Test fun multiplierArithmeticExtremesAndMinimumExponentOverflow() {
        for (interval in listOf(Int.MIN_VALUE, -1, 0, 1, 2, 3, Int.MAX_VALUE)) for (require in listOf(false, true)) {
            val jg = handler(source(interval, require), null, false, null)
            val kg = ScientificNotation(interval, require, 1, SignDisplay.AUTO).withLocaleData(null, false, null)
            for (magnitude in listOf(Int.MIN_VALUE, -100, -1, 0, 1, 100, Int.MAX_VALUE)) {
                assertEquals((jg as JProducer).getMultiplier(magnitude), (kg as MultiplierProducer).getMultiplier(magnitude))
            }
        }
        val jg = handler(source(1, false), JSymbols(JLocale.ENGLISH), false, null)
        val kg = ScientificNotation(1, false, 1, SignDisplay.AUTO).withLocaleData(DecimalFormatSymbols(ULocale("en")), false, null)
        jg.javaClass.getDeclaredField("exponent").apply { isAccessible = true }.setInt(jg, Int.MIN_VALUE)
        kg.javaClass.getDeclaredField("exponent").apply { isAccessible = true }.setInt(kg, Int.MIN_VALUE)
        modifiers(jg as JModifier, kg as Modifier, "abs-min-overflow")
    }
    @Test fun callbackOrderNullsAndSpecialEarlyReturn() {
        for (special in listOf(false, true)) for (rounder in listOf(false, true)) {
            val jt = mutableListOf<String>(); val kt = mutableListOf<String>()
            fun proxy(type: Class<*>, delegate: Any, trace: MutableList<String>): Any = Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, args ->
                trace.add(method.name)
                try { method.invoke(delegate, *(args ?: emptyArray())) } catch (e: InvocationTargetException) { throw e.cause!! }
            }
            val jq = proxy(JQuantity::class.java, JBCD(if (special) Double.POSITIVE_INFINITY else 999.9), jt) as JQuantity
            val kq = proxy(DecimalQuantity::class.java, DecimalQuantity_DualStorageBCD(if (special) Double.POSITIVE_INFINITY else 999.9), kt) as DecimalQuantity
            val jp = JGenerator { jt.add("parent"); JMicro(false).apply { if (rounder) this.rounder = JPrecision.maxSignificantDigits(3).trailingZeroDisplay(null) } }
            val kp = MicroPropsGenerator { kt.add("parent"); MicroProps(false).apply { if (rounder) this.rounder = Precision.maxSignificantDigits(3).trailingZeroDisplay(null) } }
            val jg = handler(JNotation.scientific(), JSymbols(JLocale.ENGLISH), false, jp)
            val kg = Notation.scientific().withLocaleData(DecimalFormatSymbols(ULocale("en")), false, kp)
            assertEquals(outcome { jg.processQuantity(jq).rounder == null }, outcome { kg.processQuantity(kq)!!.rounder == null })
            assertEquals(jt, kt)
        }
        val jt = mutableListOf<String>(); val kt = mutableListOf<String>()
        val js = object : JSymbols(JLocale.ENGLISH) { override fun getExponentSeparator(): String? { jt.add("separator"); return null } }
        val ks = object : DecimalFormatSymbols(ULocale("en")) { override fun getExponentSeparator(): String? { kt.add("separator"); return null } }
        val jg = handler(JNotation.scientific(), js, false, null) as JModifier
        val kg = Notation.scientific().withLocaleData(ks, false, null) as Modifier
        assertEquals(outcome { jg.apply(null, 0, 0) }, outcome { kg.apply(null, 0, 0) }); assertEquals(jt, kt)
    }
}

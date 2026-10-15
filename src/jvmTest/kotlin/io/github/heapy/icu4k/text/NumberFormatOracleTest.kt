// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import com.ibm.icu.text.NumberFormat as JFormat
import com.ibm.icu.text.DisplayContext as JContext
import com.ibm.icu.util.Currency as JCurrency
import com.ibm.icu.util.CurrencyAmount as JAmount
import com.ibm.icu.math.BigDecimal as JDecimal
import io.github.heapy.icu4k.util.*
import io.github.heapy.icu4k.math.BigDecimal as IcuDecimal
import java.math.BigDecimal
import java.math.BigInteger
import java.io.*
import java.lang.reflect.InvocationTargetException
import kotlin.random.Random
import kotlin.test.*

class NumberFormatOracleTest {
    private class JProbe : JFormat() {
        var events = arrayListOf<String>()
        var advance = 1
        var parsed: Number? = 12L
        var fail = false
        var different = false
        var locked = false
        override fun format(number: Long, output: StringBuffer?, position: java.text.FieldPosition?) = emit("long:$number", output)
        override fun format(number: Double, output: StringBuffer?, position: java.text.FieldPosition?) = emit("double:$number", output)
        override fun format(number: BigInteger?, output: StringBuffer?, position: java.text.FieldPosition?) = emit("integer:$number", output)
        override fun format(number: BigDecimal?, output: StringBuffer?, position: java.text.FieldPosition?) = emit("decimal:$number", output)
        override fun format(number: JDecimal?, output: StringBuffer?, position: java.text.FieldPosition?) = emit("icu:$number", output)
        private fun emit(value: String, output: StringBuffer?): StringBuffer {
            events.add(value); locked = Thread.holdsLock(this)
            if (fail) throw IllegalStateException("format failed")
            output!!.append(value)
            return if (different) StringBuffer("different") else output
        }
        override fun parse(text: String?, position: java.text.ParsePosition?): Number? {
            events.add("parse:$text")
            position!!.index += advance; position.errorIndex = 7
            return parsed
        }
        override fun setCurrency(value: JCurrency?) { events.add("currency:${value?.currencyCode}"); super.setCurrency(value) }
        fun effective() = getEffectiveCurrency()
    }
    private class KProbe : NumberFormat() {
        var events = arrayListOf<String>()
        var advance = 1
        var parsed: Number? = 12L
        var fail = false
        var different = false
        var locked = false
        override fun format(number: Long, output: StringBuilder?, position: java.text.FieldPosition?) = emit("long:$number", output)
        override fun format(number: Double, output: StringBuilder?, position: java.text.FieldPosition?) = emit("double:$number", output)
        override fun format(number: BigInteger?, output: StringBuilder?, position: java.text.FieldPosition?) = emit("integer:$number", output)
        override fun format(number: BigDecimal?, output: StringBuilder?, position: java.text.FieldPosition?) = emit("decimal:$number", output)
        override fun format(number: IcuDecimal?, output: StringBuilder?, position: java.text.FieldPosition?) = emit("icu:$number", output)
        private fun emit(value: String, output: StringBuilder?): StringBuilder {
            events.add(value); locked = Thread.holdsLock(this)
            if (fail) throw IllegalStateException("format failed")
            output!!.append(value)
            return if (different) StringBuilder("different") else output
        }
        override fun parse(text: String?, position: ParsePosition?): Number? {
            events.add("parse:$text")
            position!!.index += advance; position.errorIndex = 7
            return parsed
        }
        override fun setCurrency(value: Currency?) { events.add("currency:${value?.getCurrencyCode()}"); super.setCurrency(value) }
        fun effective() = getEffectiveCurrency()
    }
    private fun outcome(block: () -> Any?): String = try { block().toString() } catch (e: Throwable) {
        when (e) { is NullPointerException -> "NPE"; else -> "${e.javaClass.name}:${e.message}" }
    }
    private val jFields = ObjectStreamClass.lookup(JFormat::class.java).fields.map { it.name }.associateWith { JFormat::class.java.getDeclaredField(it).also { it.isAccessible = true } }
    private val kFields = jFields.keys.associateWith { NumberFormat::class.java.getDeclaredField(it).also { it.isAccessible = true } }
    private fun fieldValue(value: Any?): Any? = when (value) {
        is Enum<*> -> value.name
        is JCurrency -> value.currencyCode
        is Currency -> value.getCurrencyCode()
        else -> value
    }
    private fun state(j: JProbe, k: KProbe) {
        for (name in jFields.keys) assertEquals(fieldValue(jFields.getValue(name).get(j)), fieldValue(kFields.getValue(name).get(k)), name)
        assertEquals(j.hashCode(), k.hashCode())
    }
    @Test fun settingsClampingContextsEqualityAndClone() {
        val j = JProbe(); val k = KProbe(); state(j, k)
        val random = Random(17918)
        val names = listOf("setMaximumIntegerDigits", "setMinimumIntegerDigits", "setMaximumFractionDigits", "setMinimumFractionDigits")
        for (value in listOf(Int.MIN_VALUE, -1, 0, 1, 127, 128, Int.MAX_VALUE) + List(90) { random.nextInt(-100, 300) }) for (name in names) {
            JFormat::class.java.getMethod(name, Int::class.javaPrimitiveType).invoke(j, value)
            NumberFormat::class.java.getMethod(name, Int::class.javaPrimitiveType).invoke(k, value)
            state(j, k)
            assertEquals(j == j.clone(), k == k.clone())
        }
        for (name in listOf("setGroupingUsed", "setParseIntegerOnly", "setParseStrict")) for (value in listOf(false, true)) {
            JFormat::class.java.getMethod(name, Boolean::class.javaPrimitiveType).invoke(j, value)
            NumberFormat::class.java.getMethod(name, Boolean::class.javaPrimitiveType).invoke(k, value)
            state(j, k)
        }
        for (context in JContext.entries) {
            j.setContext(context); k.setContext(DisplayContext.valueOf(context.name)); state(j, k)
            for (type in listOf<JContext.Type?>(null) + JContext.Type.entries) assertEquals(j.getContext(type).name, k.getContext(type?.let { DisplayContext.Type.valueOf(it.name) }).name)
        }
        assertEquals(outcome { j.setContext(null) }, outcome { k.setContext(null) })
        assertEquals(outcome { j.roundingMode }, outcome { k.getRoundingMode() })
        assertEquals(outcome { j.roundingMode = -1 }, outcome { k.setRoundingMode(-1) })
        val jc = j.clone() as JProbe; val kc = k.clone() as KProbe
        assertSame(j.events, jc.events); assertSame(k.events, kc.events)
        j.currency = JCurrency.getInstance("EUR"); k.setCurrency(Currency.getInstance("EUR"))
        assertEquals(j == jc, k == kc) // Currency and UFormat metadata are excluded.
        state(j, k)
    }
    @Test fun formatDispatchReturnIdentityAndCurrencyFailureOrder() {
        val j = JProbe(); val k = KProbe()
        val values = listOf(12L, 12, 1.toByte(), 2.toShort(), 12.5, 12.5f, BigInteger("12345678901234567890"), BigDecimal("1.20"), null, "12")
        for (value in values) {
            assertEquals(outcome { j.format(value) }, outcome { k.format(value) })
            assertEquals(j.events, k.events)
        }
        assertEquals(j.format(JDecimal("1.20")), k.format(IcuDecimal("1.20")))
        j.different = true; k.different = true
        assertEquals(j.format(12L), k.format(12L)); assertEquals(j.format(12.5), k.format(12.5))
        j.different = false; k.different = false
        j.currency = JCurrency.getInstance("USD"); k.setCurrency(Currency.getInstance("USD"))
        val ja = JAmount(12L, JCurrency.getInstance("EUR")); val ka = CurrencyAmount(12L, Currency.getInstance("EUR"))
        assertEquals(j.format(ja), k.format(ka)); assertTrue(j.locked); assertTrue(k.locked)
        assertEquals(j.events, k.events)
        j.fail = true; k.fail = true
        assertEquals(outcome { j.format(ja) }, outcome { k.format(ka) }); assertEquals(j.events, k.events)
        assertEquals(j.currency.currencyCode, k.getCurrency()!!.getCurrencyCode())
    }
    @Test fun virtualCurrencyEqualityAndBaseTypeChecks() {
        class JCountingCurrency : JCurrency("USD") {
            var calls = 0
            override fun equals(other: Any?): Boolean { calls++; return super.equals(other) }
            override fun hashCode(): Int = super.hashCode()
        }
        class KCountingCurrency : Currency("USD") {
            var calls = 0
            override fun equals(other: Any?): Boolean { calls++; return super.equals(other) }
            override fun hashCode(): Int = super.hashCode()
        }
        val j = JProbe(); val k = KProbe()
        val jc = JCountingCurrency(); val kc = KCountingCurrency()
        j.currency = jc; k.setCurrency(kc)
        assertEquals(j.format(JAmount(12L, jc)), k.format(CurrencyAmount(12L, kc)))
        assertEquals(jc.calls, kc.calls); assertEquals(1, kc.calls)
        assertFalse(j.equals("other")); assertFalse(k.equals("other"))
    }
    @Test fun parsingWrappersRetainIndexAndResultPolicy() {
        val j = JProbe(); val k = KProbe()
        for (advance in listOf(0, 1, 2)) for (value in listOf<Number?>(null, 12L)) for (text in listOf(null, "", "input")) {
            j.advance = advance; k.advance = advance; j.parsed = value; k.parsed = value
            assertEquals(outcome { j.parse(text) }, outcome { k.parse(text) })
            val jp = java.text.ParsePosition(3); val kp = ParsePosition(3)
            assertEquals(j.parseObject(text, jp), k.parseObject(text, kp)); assertEquals(jp.index, kp.index); assertEquals(jp.errorIndex, kp.errorIndex)
        }
        j.currency = JCurrency.getInstance("EUR"); k.setCurrency(Currency.getInstance("EUR"))
        assertEquals(j.parseCurrency("1EUR", java.text.ParsePosition(0)).currency.currencyCode, k.parseCurrency("1EUR", ParsePosition(0))!!.getCurrency().getCurrencyCode())
        assertEquals(outcome { j.parseCurrency(null, null) }, outcome { k.parseCurrency(null, null) })
    }
    private fun roundTrip(value: Any): Any {
        val bytes = ByteArrayOutputStream().also { ObjectOutputStream(it).use { out -> out.writeObject(value) } }.toByteArray()
        return ObjectInputStream(ByteArrayInputStream(bytes)).use { it.readObject() }
    }
    private fun callStream(receiver: Any, clazz: Class<*>, write: Boolean, stream: Any) {
        val method = clazz.getDeclaredMethod(if (write) "writeObject" else "readObject", if (write) ObjectOutputStream::class.java else ObjectInputStream::class.java).also { it.isAccessible = true }
        try { method.invoke(receiver, stream) } catch (e: InvocationTargetException) { throw e.targetException }
    }
    @Test fun serializationFieldsLegacyVersionsValidationAndHashMutation() {
        val jd = ObjectStreamClass.lookup(JFormat::class.java); val kd = ObjectStreamClass.lookup(NumberFormat::class.java)
        assertEquals(jd.serialVersionUID, kd.serialVersionUID)
        fun mapped(type: Class<*>) = when (type) { JCurrency::class.java -> Currency::class.java; JContext::class.java -> DisplayContext::class.java; else -> type }
        assertEquals(jd.fields.map { it.name to mapped(it.type) }, kd.fields.map { it.name to it.type })
        for (version in listOf(-1, 0, 1, 2, 99)) for (min in listOf(-1, 0, 1, 128)) for (max in listOf(-1, 0, 40, 256)) {
            val j = JProbe(); val k = KProbe()
            for ((name, value) in mapOf("serialVersionOnStream" to version, "minimumIntegerDigits" to min, "maximumIntegerDigits" to max, "minimumFractionDigits" to min, "maximumFractionDigits" to max)) {
                jFields.getValue(name).setInt(j, value); kFields.getValue(name).setInt(k, value)
            }
            // Drive the source private stream hooks directly to cover historical layouts and malformed data.
            val ji = object : ObjectInputStream() { override fun defaultReadObject() {} }
            val ki = object : ObjectInputStream() { override fun defaultReadObject() {} }
            assertEquals(outcome { callStream(j, JFormat::class.java, false, ji) }, outcome { callStream(k, NumberFormat::class.java, false, ki) })
            state(j, k)
        }
        for (value in listOf(Int.MIN_VALUE, -256, -1, 0, 127, 128, Int.MAX_VALUE)) {
            val j = JProbe(); val k = KProbe()
            for (name in listOf("maximumIntegerDigits", "minimumIntegerDigits", "maximumFractionDigits", "minimumFractionDigits")) {
                jFields.getValue(name).setInt(j, value); kFields.getValue(name).setInt(k, value)
            }
            val jo = object : ObjectOutputStream() { override fun defaultWriteObject() {} }
            val ko = object : ObjectOutputStream() { override fun defaultWriteObject() {} }
            callStream(j, JFormat::class.java, true, jo); callStream(k, NumberFormat::class.java, true, ko); state(j, k)
        }
        val j = JProbe(); val k = KProbe()
        j.maximumFractionDigits = 1000; k.setMaximumFractionDigits(1000)
        j.currency = JCurrency.getInstance("USD"); k.setCurrency(Currency.getInstance("USD"))
        val jr = roundTrip(j) as JProbe; val kr = roundTrip(k) as KProbe
        state(jr, kr); state(j, k)
        assertEquals(jr.format(JAmount(1L, JCurrency.getInstance("EUR"))), kr.format(CurrencyAmount(1L, Currency.getInstance("EUR"))))
        assertEquals(jr.currency.currencyCode, kr.getCurrency()!!.getCurrencyCode())
    }
}

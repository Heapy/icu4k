@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.number

import com.ibm.icu.number.NumberFormatter as JFormatter
import com.ibm.icu.impl.number.LocalizedNumberFormatterAsFormat as JAdapter
import com.ibm.icu.text.NumberFormat.Field as JField
import com.ibm.icu.util.ULocale as JLocale
import io.github.heapy.icu4k.impl.number.LocalizedNumberFormatterAsFormat
import io.github.heapy.icu4k.impl.number.LocalizedNumberFormatterAsFormatJvm
import io.github.heapy.icu4k.text.NumberFormatField
import io.github.heapy.icu4k.util.ULocale
import java.io.*
import java.text.FieldPosition
import java.text.Format
import java.text.ParsePosition
import kotlin.test.*

class NumberFormatterFormatAdapterOracleTest {
    private fun outcome(block: () -> Any?): Any? = try { block() } catch (e: Throwable) {
        when (e) { is NullPointerException -> "null"; else -> e.javaClass.simpleName + ":" + e.message }
    }
    @Test fun javaFormatFieldPositionsAppendOffsetsAndValidation() {
        val j = JFormatter.forSkeleton("currency/USD .00").locale(JLocale.ENGLISH).toFormat()
        val k: Format = NumberFormatter.forSkeleton("currency/USD .00").locale(ULocale.ENGLISH).toFormat()
        for (number in listOf<Any?>(-1234.5, 0L, java.math.BigDecimal("12.345"), java.math.BigInteger("18446744073709551617"), "12", null)) for (field in listOf("integer", "fraction", "currency", "missing")) for (prefix in listOf("", "prefix")) {
            val jp = FieldPosition(when (field) { "integer" -> JField.INTEGER; "fraction" -> JField.FRACTION; "currency" -> JField.CURRENCY; else -> JField.EXPONENT })
            val kp = FieldPosition(when (field) { "integer" -> NumberFormatField.INTEGER; "fraction" -> NumberFormatField.FRACTION; "currency" -> NumberFormatField.CURRENCY; else -> NumberFormatField.EXPONENT })
            jp.beginIndex = 50; jp.endIndex = 70; kp.beginIndex = 50; kp.endIndex = 70
            val jb = StringBuffer(prefix); val kb = StringBuffer(prefix)
            assertEquals(outcome { j.format(number, jb, jp).toString() }, outcome { k.format(number, kb, kp).toString() })
            assertEquals(jb.toString(), kb.toString()); assertEquals(jp.beginIndex, kp.beginIndex); assertEquals(jp.endIndex, kp.endIndex)
        }
        assertEquals(outcome { j.format(1, StringBuffer(), null) }, outcome { k.format(1, StringBuffer(), null) })
        assertEquals(outcome { j.format(1, null, FieldPosition(0)) }, outcome { k.format(1, null, FieldPosition(0)) })
        assertEquals(outcome { j.parseObject(null, null) }, outcome { k.parseObject(null, null) })
        val iterator = k.formatToCharacterIterator(1.5); val ji = j.formatToCharacterIterator(1.5)
        for (index in 0..iterator.endIndex) { assertEquals(ji.setIndex(index), iterator.setIndex(index)); assertEquals(ji.runStart, iterator.runStart); assertEquals(ji.runLimit, iterator.runLimit) }
        val legacy = java.text.MessageFormat("Amount: {0}")
        legacy.setFormatByArgumentIndex(0, k)
        assertEquals("Amount: $12.50", legacy.format(arrayOf<Any>(12.5)))
    }
    @Test fun serializationProxyAndWrappedFormatterIdentity() {
        fun roundTrip(value: Any): Any {
            val bytes = ByteArrayOutputStream().also { out -> ObjectOutputStream(out).use { it.writeObject(value) } }.toByteArray()
            return ObjectInputStream(ByteArrayInputStream(bytes)).use { it.readObject() }
        }
        for (locale in listOf("en_US", "fr", "ar@numbers=arab")) for (skeleton in listOf("", "currency/USD .00", "scientific/*eee/sign-always", "unit/foot-and-inch unit-width-full-name")) {
            val jf = JFormatter.forSkeleton(skeleton).locale(JLocale(locale)); val kf = NumberFormatter.forSkeleton(skeleton).locale(ULocale(locale))
            val j = jf.toFormat() as JAdapter; val k = kf.toFormat()
            assertSame(jf, j.numberFormatter); assertSame(kf, k.getNumberFormatter())
            assertEquals(j == jf.toFormat(), k == kf.toFormat())
            val jr = roundTrip(j) as JAdapter; val kr = roundTrip(k) as LocalizedNumberFormatterAsFormat
            assertEquals(jr.format(1.5), kr.format(1.5))
            assertEquals(j == jr, k == kr)
            assertEquals(ObjectStreamClass.lookup(JAdapter::class.java).serialVersionUID, ObjectStreamClass.lookup(LocalizedNumberFormatterAsFormatJvm::class.java).serialVersionUID)
        }
        val j = JFormatter.withLocale(JLocale.ENGLISH).symbols(com.ibm.icu.text.DecimalFormatSymbols(JLocale.ENGLISH)).toFormat()
        val k = NumberFormatter.withLocale(ULocale.ENGLISH).symbols(io.github.heapy.icu4k.text.DecimalFormatSymbols(ULocale.ENGLISH)).toFormat()
        assertEquals(outcome { roundTrip(j) }, outcome { roundTrip(k) })
        val jn = JAdapter(null, null); val kn = LocalizedNumberFormatterAsFormat(null, null)
        assertEquals(outcome { jn.format(1) }, outcome { kn.format(1) })
        assertEquals(outcome { jn.hashCode() }, outcome { kn.hashCode() })
        assertTrue(kn.equals(kn)); assertFalse(kn.equals(null)); assertFalse(kn.equals("other"))
        assertEquals(outcome { jn.parseObject("x", ParsePosition(0)) }, outcome { kn.parseObject("x", ParsePosition(0)) })
    }
}

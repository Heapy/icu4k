@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.impl.number

import com.ibm.icu.impl.number.DecimalFormatProperties as JProps
import com.ibm.icu.impl.number.PatternStringParser as JParser
import com.ibm.icu.impl.number.Padder as JPadder
import com.ibm.icu.impl.number.Properties as JLegacy
import com.ibm.icu.text.CompactDecimalFormat.CompactStyle as JCompact
import com.ibm.icu.text.CurrencyPluralInfo as JInfo
import com.ibm.icu.text.PluralRules as JRules
import com.ibm.icu.util.Currency as JCurrency
import com.ibm.icu.util.ULocale as JLocale
import io.github.heapy.icu4k.text.CompactStyle
import io.github.heapy.icu4k.text.CurrencyPluralInfo
import io.github.heapy.icu4k.text.PluralRules
import io.github.heapy.icu4k.util.Currency
import io.github.heapy.icu4k.util.ULocale
import java.io.*
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Modifier
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import kotlin.test.*

class DecimalFormatPropertiesOracleTest {
    private val fields = JProps::class.java.declaredFields.filter { !Modifier.isStatic(it.modifiers) }
    private fun normalize(value: Any?): Any? = when (value) {
        is Enum<*> -> value.name
        is JCurrency -> value.currencyCode
        is Currency -> value.getCurrencyCode()
        is JRules -> value.toString()
        is PluralRules -> value.toString()
        is JInfo -> listOf(value.locale?.name, value.pluralRules?.toString())
        is CurrencyPluralInfo -> listOf(value.getLocale()?.getName(), value.getPluralRules()?.toString())
        else -> value
    }
    private fun snapshot(value: Any): List<Any?> = fields.map { field ->
        val name = field.name.replaceFirstChar { it.uppercaseChar() }
        normalize(value.javaClass.getMethod("get$name").invoke(value))
    }
    private fun invoke(value: Any, name: String, argument: Any?): Any? = try {
        value.javaClass.methods.first { it.name == name && it.parameterCount == 1 }.invoke(value, argument)
    } catch (e: InvocationTargetException) { throw e.cause!! }
    private fun outcome(action: () -> Any?): Any? = try { action() } catch (e: Throwable) { e.javaClass.simpleName }
    private fun samples(field: java.lang.reflect.Field): List<Any?> = when (field.type) {
        Boolean::class.javaPrimitiveType -> listOf(true, false)
        Int::class.javaPrimitiveType -> listOf(Int.MIN_VALUE, -7, -1, 0, 23, Int.MAX_VALUE)
        String::class.java -> listOf(null, "", "field'¤%", "😀")
        BigDecimal::class.java -> listOf(null, BigDecimal("-123.4500"), BigDecimal("1E+100"))
        MathContext::class.java -> listOf(null, MathContext(3, RoundingMode.FLOOR))
        java.util.Map::class.java -> listOf(null, linkedMapOf("1000" to linkedMapOf("one" to "0K", "other" to "0K")))
        JCurrency::class.java -> listOf(null, JCurrency.getInstance("EUR"))
        JInfo::class.java -> listOf(null, JInfo(JLocale("en")))
        JRules::class.java -> listOf(null, JRules.parseDescription("one:n=1;other:"))
        else -> listOf(null) + field.type.enumConstants.toList()
    }
    private fun port(value: Any?): Any? = when (value) {
        is JCurrency -> Currency.getInstance(value.currencyCode)
        is JInfo -> CurrencyPluralInfo(ULocale(value.locale.name))
        is JRules -> PluralRules.parseDescription(value.toString())
        is JCompact -> CompactStyle.valueOf(value.name)
        is JCurrency.CurrencyUsage -> Currency.CurrencyUsage.valueOf(value.name)
        is JPadder.PadPosition -> Padder.PadPosition.valueOf(value.name)
        is JProps.ParseMode -> DecimalFormatProperties.ParseMode.valueOf(value.name)
        else -> value
    }
    private fun stableHash(value: Any): Int {
        var hash = value.hashCode()
        for (field in fields) if (!field.type.isPrimitive) {
            val name = field.name.replaceFirstChar { it.uppercaseChar() }
            hash = hash xor (value.javaClass.getMethod("get$name").invoke(value)?.hashCode() ?: 0)
        }
        return hash
    }
    @Test fun allFieldsDefaultsMutationsCopiesEqualityAndHashesMatch() {
        val j = JProps(); val k = DecimalFormatProperties()
        assertEquals(snapshot(j), snapshot(k)); assertEquals(j.toString(), k.toString())
        assertEquals(fields.map { it.name }, k.entries().map { it.first })
        for (field in fields) for (value in samples(field)) {
            val setter = "set" + field.name.replaceFirstChar { it.uppercaseChar() }
            val kv = port(value)
            assertSame(j, invoke(j, setter, value)); assertSame(k, invoke(k, setter, kv))
            assertEquals(snapshot(j), snapshot(k), "$setter/$value")
            assertEquals(stableHash(j), stableHash(k), setter)
            assertEquals(j == JProps(), k == DecimalFormatProperties(), setter)
            val jc = j.clone(); val kc = k.clone()
            assertEquals(snapshot(jc), snapshot(kc)); assertEquals(j == jc, k == kc)
            assertEquals(j.hashCode(), jc.hashCode()); assertEquals(k.hashCode(), kc.hashCode())
            if (!field.type.isPrimitive && kv != null) assertSame(kv, kc.javaClass.getMethod("get" + field.name.replaceFirstChar { it.uppercaseChar() }).invoke(kc))
            val target = DecimalFormatProperties(); assertSame(target, target.copyFrom(k)); assertEquals(k, target)
            assertSame(j, j.copyFrom(j)); assertSame(k, k.copyFrom(k)); assertEquals(snapshot(j), snapshot(k))
            assertSame(j, j.clear()); assertSame(k, k.clear()); assertEquals(snapshot(j), snapshot(k))
        }
        assertEquals(outcome { j.copyFrom(null) }, outcome { k.copyFrom(null) }); assertEquals(snapshot(j), snapshot(k))
        assertFalse(k.equals(null)); assertFalse(k.equals("other"))
    }
    @Test fun shallowObjectIdentityAndBareTextRespectSourceEdges() {
        val ji = JInfo(JLocale("en")).also { it.setPluralRules("invalid") }
        val ki = CurrencyPluralInfo(ULocale("en")).also { it.setPluralRules("invalid") }
        val j = JProps().setCurrencyPluralInfo(ji); val k = DecimalFormatProperties().setCurrencyPluralInfo(ki)
        assertEquals(outcome { j == j.clone() }, outcome { k == k.clone() })
        assertSame(ki, k.clone().getCurrencyPluralInfo())
        assertEquals(outcome { j.hashCode() }, outcome { k.hashCode() })
        for (pattern in listOf("#,##0.00", "@@##", "0.05%", "*_0.0;(0.0)", "¤0.00")) {
            val jp = JParser.parseToProperties(pattern); val kp = PatternStringParserCore.parseToDecimalFormatProperties(pattern)
            assertEquals(jp.toString(), kp.toString())
            val jb = StringBuilder("prefix"); val kb = StringBuilder("prefix")
            jp.toStringBare(jb); kp.toStringBare(kb); assertEquals(jb.toString(), kb.toString())
        }
        JProps().toStringBare(null); DecimalFormatProperties().toStringBare(null)
        val jbad = JProps().setCurrencyPluralInfo(ji); val kbad = DecimalFormatProperties().setCurrencyPluralInfo(ki)
        assertEquals(outcome { jbad.toStringBare(null) }, outcome { kbad.toStringBare(null) })
    }
    private fun roundTrip(value: Any): Any = ByteArrayOutputStream().let { bytes ->
        ObjectOutputStream(bytes).use { it.writeObject(value) }
        ObjectInputStream(ByteArrayInputStream(bytes.toByteArray())).use { it.readObject() }
    }
    private class RecordsOutput : ObjectOutputStream() {
        val ints = mutableListOf<Int>(); val objects = mutableListOf<Any?>()
        override fun defaultWriteObject() {}
        override fun writeInt(value: Int) { ints.add(value) }
        override fun writeObjectOverride(value: Any?) { objects.add(value) }
    }
    private class RecordsInput(count: Int, values: List<Any?>) : ObjectInputStream() {
        private val ints = listOf(0, count).iterator(); private val objects = values.iterator()
        var read = 0
        override fun defaultReadObject() {}
        override fun readInt(): Int = ints.next()
        override fun readObjectOverride(): Any? { read++; return objects.next() }
    }
    private fun jwrite(props: JProps, out: ObjectOutputStream) {
        try { JProps::class.java.getDeclaredMethod("writeObjectImpl", ObjectOutputStream::class.java).also { it.isAccessible = true }.invoke(props, out) }
        catch (e: InvocationTargetException) { throw e.cause!! }
    }
    private fun jread(props: JProps, input: ObjectInputStream) {
        try { JProps::class.java.getDeclaredMethod("readObjectImpl", ObjectInputStream::class.java).also { it.isAccessible = true }.invoke(props, input) }
        catch (e: InvocationTargetException) { throw e.cause!! }
    }
    @Test fun namedFieldStreamsRoundTripsUnknownFieldsAndLegacyWrapperMatch() {
        val j = JProps(); val k = DecimalFormatProperties()
        for (field in fields) {
            val value = samples(field).last()
            val name = "set" + field.name.replaceFirstChar { it.uppercaseChar() }
            invoke(j, name, value); invoke(k, name, port(value))
        }
        val jo = RecordsOutput(); val ko = RecordsOutput(); jwrite(j, jo); k.writeObjectImpl(ko)
        assertEquals(jo.ints, ko.ints); assertEquals(jo.objects.map(::normalize), ko.objects.map(::normalize))
        assertEquals(snapshot(roundTrip(j)), snapshot(roundTrip(k)))
        assertTrue(ObjectStreamClass.lookup(DecimalFormatProperties::class.java).fields.isEmpty())
        val legacyJ = JLegacy(); val legacyK = Properties()
        assertNull(legacyK.getInstance())
        assertEquals(snapshot((roundTrip(legacyJ) as JLegacy).instance), snapshot((roundTrip(legacyK) as Properties).getInstance()!!))
        assertNotNull(legacyK.getInstance())
        for (records in listOf(
            listOf("futureField", Any(), "Companion", Any(), "groupingSize", 7.toShort()),
            listOf("groupingSize", 2, "groupingSize", 5),
            listOf("parseIntegerOnly", true),
            listOf("parseIntegerOnly", 1),
            listOf("groupingSize", null),
            listOf("serialVersionUID", 1L),
            listOf(null, 1),
            listOf(7, 1))) {
            val ji = RecordsInput(records.size / 2, records); val ki = RecordsInput(records.size / 2, records)
            assertEquals(outcome { jread(j, ji) }, outcome { k.readObjectImpl(ki) })
            assertEquals(ji.read, ki.read); assertEquals(snapshot(j), snapshot(k))
        }
        val ji = RecordsInput(-1, emptyList()); val ki = RecordsInput(-1, emptyList())
        jread(j, ji); k.readObjectImpl(ki); assertEquals(snapshot(j), snapshot(k))
    }
    @Test fun patternProjectionPreservesUnrelatedStateAndClearsEmptyPatterns() {
        val j = JProps().setCurrency(JCurrency.getInstance("USD")).setParseIntegerOnly(true).setPositivePrefix("literal")
        val k = DecimalFormatProperties().setCurrency(Currency.getInstance("USD")).setParseIntegerOnly(true).setPositivePrefix("literal")
        for (pattern in listOf("#,##0.00", "@@##", "0.05%", "¤0.00", "0.0E+00", "*_0.0;(0.0)", "bad'", null, "")) for (ignore in 0..2) {
            assertEquals(outcome { JParser.parseToExistingProperties(pattern, j, ignore) }, outcome { PatternStringParserCore.parseToExistingProperties(pattern, k, ignore) })
            assertEquals(snapshot(j), snapshot(k), "$pattern/$ignore")
        }
    }
}

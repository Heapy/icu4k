// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import com.ibm.icu.text.DecimalFormat as JFormat
import com.ibm.icu.text.DecimalFormatSymbols as JSymbols
import com.ibm.icu.util.ULocale as JLocale
import com.ibm.icu.math.MathContext as JContext
import com.ibm.icu.impl.number.Properties as JCompat
import io.github.heapy.icu4k.impl.number.Properties
import io.github.heapy.icu4k.util.ULocale
import io.github.heapy.icu4k.math.MathContext as IcuContext
import java.io.*
import java.lang.reflect.Modifier
import java.lang.reflect.InvocationTargetException
import kotlin.test.*

class DecimalFormatStreamOracleTest {
    private class VersionOnly : Serializable { var serialVersionOnStream = 5 }
    private class LegacyLayout : Serializable {
        var serialVersionOnStream = 4
        var decimalSeparatorAlwaysShown = false
        var exponentSignAlwaysShown = false
        var formatWidth = 0
        var groupingSize: Byte = 3
        var groupingSize2: Byte = 0
        var maxSignificantDigits = 6
        var minExponentDigits: Byte = 0
        var minSignificantDigits = 1
        var multiplier = 1
        var pad = '_'
        var padPosition = 0
        var parseBigDecimal = false
        var parseRequireDecimalPoint = false
        var roundingMode = 0
        var useExponentialNotation = false
        var useSignificantDigits = false
        var currencyPluralInfo: Any? = null
        var mathContext: Any? = null
        var negPrefixPattern: String? = null
        var negSuffixPattern: String? = null
        var negativePrefix = "legacy-"
        var negativeSuffix = "N"
        var posPrefixPattern: String? = null
        var posSuffixPattern: String? = null
        var positivePrefix = "legacy+"
        var positiveSuffix = "P"
        var roundingIncrement: Any? = null
        var symbols: Any? = null
    }
    private class Fields(private val descriptor: ObjectStreamClass, private val values: Map<String, Any?>) : ObjectInputStream.GetField() {
        override fun getObjectStreamClass() = descriptor
        override fun defaulted(name: String) = !values.containsKey(name)
        override fun get(name: String, value: Boolean) = if (defaulted(name)) value else values[name] as Boolean
        override fun get(name: String, value: Byte) = if (defaulted(name)) value else values[name] as Byte
        override fun get(name: String, value: Char) = if (defaulted(name)) value else values[name] as Char
        override fun get(name: String, value: Short) = if (defaulted(name)) value else values[name] as Short
        override fun get(name: String, value: Int) = if (defaulted(name)) value else values[name] as Int
        override fun get(name: String, value: Long) = if (defaulted(name)) value else values[name] as Long
        override fun get(name: String, value: Float) = if (defaulted(name)) value else values[name] as Float
        override fun get(name: String, value: Double) = if (defaulted(name)) value else values[name] as Double
        override fun get(name: String, value: Any?): Any? = if (defaulted(name)) value else values[name]
    }
    private class Input(val fields: Fields, val objects: MutableList<Any?> = arrayListOf()) : ObjectInputStream() {
        val events = arrayListOf<String>()
        override fun readFields(): GetField { events.add("fields"); return fields }
        override fun readInt(): Int { events.add("reserved"); return -999 }
        override fun readObjectOverride(): Any? { events.add("object"); return objects.removeAt(0) }
    }
    private fun outcome(block: () -> Unit): String = try { block(); "success" } catch (e: Throwable) {
        when (e) { is NullPointerException -> "NPE"; is ClassCastException -> "cast"; else -> "${e.javaClass.name}:${e.message}" }
    }
    private fun read(value: Any, clazz: Class<*>, input: Input) {
        val method = clazz.getDeclaredMethod("readObject", ObjectInputStream::class.java).also { it.isAccessible = true }
        try { method.invoke(value, input) } catch (e: InvocationTargetException) { throw e.targetException }
    }
    private fun sourceField(owner: JFormat, name: String): Any? = JFormat::class.java.getDeclaredField(name).also { it.isAccessible = true }.get(owner)
    private fun compare(descriptor: Class<*>, jValues: Map<String, Any?>, kValues: Map<String, Any?> = jValues, jObjects: List<Any?> = emptyList(), kObjects: List<Any?> = jObjects) {
        val j = JFormat("0.00", JSymbols(JLocale.US)); val k = DecimalFormat("0.00", DecimalFormatSymbols(ULocale("en_US")))
        // A real subclass readObject runs after allocation without its constructor; parent fields are restored.
        for (field in JFormat::class.java.declaredFields.filter { Modifier.isTransient(it.modifiers) && !Modifier.isStatic(it.modifiers) }) {
            field.isAccessible = true
            if (field.type == Int::class.javaPrimitiveType) field.setInt(j, 0) else field.set(j, null)
        }
        k.runtime = null
        val desc = ObjectStreamClass.lookup(descriptor)
        val ji = Input(Fields(desc, jValues), jObjects.toMutableList()); val ki = Input(Fields(desc, kValues), kObjects.toMutableList())
        val jr = outcome { read(j, JFormat::class.java, ji) }; val kr = outcome { read(k, DecimalFormat::class.java, ki) }
        assertEquals(jr, kr, jValues.toString()); assertEquals(ji.events, ki.events)
        assertEquals(sourceField(j, "properties")?.toString(), k.runtime?.rawProperties?.toString())
        assertEquals(sourceField(j, "symbols") == null, k.runtime?.rawSymbols == null)
        if (jr == "success") {
            assertEquals(j.toPattern(), k.toPattern()); assertEquals(j.toLocalizedPattern(), k.toLocalizedPattern())
            assertEquals(j.format(1234.567), k.format(1234.567))
            assertEquals(j.mathContextICU.toString(), k.getMathContextICU().toString())
            assertEquals(j.getDecimalFormatSymbols().uLocale.name, k.getDecimalFormatSymbols().getULocale()!!.getName())
        }
    }
    @Test fun legacyFieldMigrationAffixPrecedenceAndFailures() {
        val defaults = LegacyLayout()
        val values = LegacyLayout::class.java.declaredFields.filter { !Modifier.isStatic(it.modifiers) }.associate { it.isAccessible = true; it.name to it.get(defaults) }
        val jBase = values + mapOf("mathContext" to JContext(9), "symbols" to JSymbols(JLocale.FRANCE))
        val kBase = values + mapOf("mathContext" to IcuContext(9), "symbols" to DecimalFormatSymbols(ULocale("fr_FR")))
        for (version in -1..4) compare(LegacyLayout::class.java, jBase + ("serialVersionOnStream" to version), kBase + ("serialVersionOnStream" to version))
        compare(LegacyLayout::class.java, jBase + mapOf("posPrefixPattern" to "'pattern+'", "negSuffixPattern" to "'pattern-'"), kBase + mapOf("posPrefixPattern" to "'pattern+'", "negSuffixPattern" to "'pattern-'"))
        for (override in listOf(mapOf("multiplier" to 0), mapOf("padPosition" to 9), mapOf("mathContext" to null), mapOf("symbols" to null))) compare(LegacyLayout::class.java, jBase + override, kBase + override)
    }
    @Test fun modernVersionsReservedFieldAndCompatibilityWrapper() {
        for (version in listOf(6, 99)) compare(VersionOnly::class.java, mapOf("serialVersionOnStream" to version))
        compare(LegacyLayout::class.java, mapOf("serialVersionOnStream" to 5))
        val j = JFormat("0.00", JSymbols(JLocale.US)); val k = DecimalFormat("0.00", DecimalFormatSymbols(ULocale("en_US")))
        val jp = sourceField(j, "properties"); val kp = k.runtime!!.rawProperties
        val js = JSymbols(JLocale.FRANCE); val ks = DecimalFormatSymbols(ULocale("fr_FR"))
        val version = mapOf("serialVersionOnStream" to 5)
        compare(VersionOnly::class.java, version, jObjects = listOf(jp, js), kObjects = listOf(kp, ks))
        val jw = JCompat(); val kw = Properties()
        JCompat::class.java.getDeclaredField("instance").also { it.isAccessible = true }.set(jw, jp)
        Properties::class.java.getDeclaredField("instance").also { it.isAccessible = true }.set(kw, kp)
        compare(VersionOnly::class.java, version, jObjects = listOf(jw, js), kObjects = listOf(kw, ks))
        compare(VersionOnly::class.java, version, jObjects = listOf(null))
        compare(VersionOnly::class.java, version, jObjects = listOf("wrong type"))
        compare(VersionOnly::class.java, version, jObjects = listOf(jp, null), kObjects = listOf(kp, null))
    }
}

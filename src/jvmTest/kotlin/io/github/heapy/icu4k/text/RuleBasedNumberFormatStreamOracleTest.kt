// SPDX-License-Identifier: Unicode-3.0
@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.text

import com.ibm.icu.text.RuleBasedNumberFormat as JFormat
import com.ibm.icu.text.DisplayContext as JContext
import com.ibm.icu.util.ULocale as JLocale
import io.github.heapy.icu4k.util.ULocale
import java.io.*
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Modifier
import kotlin.test.*

class RuleBasedNumberFormatStreamOracleTest {
    private class Input(val description: Any?, val locale: Any?, val mode: Any?) : ObjectInputStream() {
        val events = ArrayList<String>()
        override fun readUTF(): String { events.add("utf"); if (description is Throwable) throw description; return description as String }
        override fun readObjectOverride(): Any? { events.add("locale"); if (locale is Throwable) throw locale; return locale }
        override fun readInt(): Int { events.add("mode"); if (mode is Throwable) throw mode; return mode as Int }
    }
    private class Output : ObjectOutputStream() {
        val events = ArrayList<String>()
        override fun writeUTF(value: String) { events.add("utf:$value") }
        override fun writeInt(value: Int) { events.add("mode:$value") }
        override fun writeObjectOverride(value: Any?) { events.add("locale:$value") }
    }
    private fun invoke(owner: Any, type: Class<*>, name: String, stream: Any) {
        val parameter = if (name == "readObject") ObjectInputStream::class.java else ObjectOutputStream::class.java
        try { type.getDeclaredMethod(name, parameter).also { it.isAccessible = true }.invoke(owner, stream) }
        catch (e: InvocationTargetException) { throw e.targetException }
    }
    private fun outcome(block: () -> Any?): String = try { "value:${block()}" } catch (e: Throwable) {
        when (e) { is NullPointerException -> "NPE"; is IndexOutOfBoundsException -> "bounds"; is ClassCastException -> "cast"; else -> "${e.javaClass.simpleName}:${e.message}" }
    }
    @Suppress("UNCHECKED_CAST")
    private fun <T> roundtrip(value: T): T {
        val bytes = ByteArrayOutputStream().also { data -> ObjectOutputStream(data).use { it.writeObject(value) } }.toByteArray()
        return ObjectInputStream(ByteArrayInputStream(bytes)).use { it.readObject() as T }
    }
    @Test fun streamDescriptorUidAndCustomWriteOrderMatchSource() {
        val sd = ObjectStreamClass.lookup(JFormat::class.java)
        val td = ObjectStreamClass.lookup(RuleBasedNumberFormat::class.java)
        assertEquals(sd.serialVersionUID, td.serialVersionUID)
        fun fields(d: ObjectStreamClass) = d.fields.map { "${it.name}:${it.type.name.replace("com.ibm.icu", "io.github.heapy.icu4k")}" }
        assertEquals(fields(sd), fields(td))
        val s = object : JFormat("0: original", JLocale.ENGLISH) { override fun toString() = "%custom: 0: rewritten;" }
        val t = object : RuleBasedNumberFormat("0: original", ULocale.ENGLISH) { override fun toString() = "%custom: 0: rewritten;" }
        s.setRoundingMode(3); t.setRoundingMode(3)
        val so = Output(); val to = Output()
        invoke(s, JFormat::class.java, "writeObject", so)
        invoke(t, RuleBasedNumberFormat::class.java, "writeObject", to)
        assertEquals(so.events, to.events)
        assertEquals(listOf("utf:%custom: 0: rewritten;", "locale:en", "mode:3"), to.events)
    }
    @Test fun legacyReadFallbackRawModeAndPartialFailureOrder() {
        val oldSource = JLocale.getDefault(JLocale.Category.FORMAT)
        val oldTarget = ULocale.getDefault(ULocale.Category.FORMAT)
        JLocale.setDefault(JLocale.Category.FORMAT, JLocale.ENGLISH)
        ULocale.setDefault(ULocale.Category.FORMAT, ULocale.ENGLISH)
        try {
            for (description in listOf<Any?>("%main: 0: =0=; 100: <0.00< units;", "", EOFException("missing text"))) {
                for ((sloc, tloc) in listOf(JLocale.ENGLISH to ULocale.ENGLISH, null to null, "wrong" to "wrong", EOFException("no locale") to EOFException("no locale"))) {
                    for (mode in listOf<Any?>(3, 999, EOFException("old stream"))) {
                        val s = JFormat("0: original", JLocale.ENGLISH)
                        val t = RuleBasedNumberFormat("0: original", ULocale.ENGLISH)
                        s.maximumFractionDigits = 2; t.setMaximumFractionDigits(2)
                        // Serializable subclass constructors do not run. Parent fields are already restored.
                        for (f in JFormat::class.java.declaredFields.filter { !Modifier.isStatic(it.modifiers) }) {
                            f.isAccessible = true
                            when (f.type) { Boolean::class.javaPrimitiveType -> f.setBoolean(s, false); Int::class.javaPrimitiveType -> f.setInt(s, 0); else -> f.set(s, null) }
                        }
                        t.runtime = null
                        RuleBasedNumberFormat::class.java.getDeclaredField("roundingMode").also { it.isAccessible = true }.setInt(t, 0)
                        RuleBasedNumberFormat::class.java.getDeclaredField("locale").also { it.isAccessible = true }.set(t, null)
                        val si = Input(description, sloc, mode); val ti = Input(description, tloc, mode)
                        val label = "$description / $sloc / $mode"
                        val result = outcome { invoke(s, JFormat::class.java, "readObject", si); null }
                        assertEquals(result, outcome { invoke(t, RuleBasedNumberFormat::class.java, "readObject", ti); null }, label)
                        assertEquals(si.events, ti.events, label)
                        assertEquals(s.roundingMode, t.getRoundingMode(), label)
                        if (result == "value:null") {
                            assertEquals(s.toString(), t.toString(), label)
                            assertContentEquals(s.ruleSetNames, t.getRuleSetNames(), label)
                            for (n in listOf(21L, 115L, Long.MIN_VALUE)) assertEquals(outcome { s.format(n) }, outcome { t.format(n) }, "$label $n")
                            for (n in listOf(1.235, 115.5)) assertEquals(outcome { s.format(n) }, outcome { t.format(n) }, "$label $n")
                        }
                    }
                }
            }
        } finally {
            JLocale.setDefault(JLocale.Category.FORMAT, oldSource)
            ULocale.setDefault(ULocale.Category.FORMAT, oldTarget)
        }
    }
    @Test fun actualRoundtripsPreserveParentFieldsAndSourceGraphOwnerQuirks() {
        val descriptions = listOf(
            "%main: 0: =0=; 100: <0.00< units;",
            "%one: 0: one; %two: 0: two;",
            "%%post-process: com.ibm.icu.text.RBNFChinesePostProcessor; %simplified: 0: *〇;"
        )
        for (description in descriptions) {
            val s = JFormat(description, arrayOf(arrayOf(if (description.contains("%one:")) "%one" else if (description.contains("%main:")) "%main" else "%simplified"), arrayOf("en", "Localized")), JLocale.ENGLISH)
            val t = RuleBasedNumberFormat(description, arrayOf(arrayOf(if (description.contains("%one:")) "%one" else if (description.contains("%main:")) "%main" else "%simplified"), arrayOf("en", "Localized")), ULocale.ENGLISH)
            s.setRoundingMode(3); t.setRoundingMode(3)
            s.maximumFractionDigits = 2; t.setMaximumFractionDigits(2)
            s.setContext(JContext.CAPITALIZATION_FOR_BEGINNING_OF_SENTENCE); t.setContext(DisplayContext.CAPITALIZATION_FOR_BEGINNING_OF_SENTENCE)
            s.setLenientParseMode(true); t.setLenientParseMode(true)
            val sr = roundtrip(s); val tr = roundtrip(t)
            assertEquals(sr.toString(), tr.toString())
            assertContentEquals(sr.ruleSetNames, tr.getRuleSetNames())
            assertContentEquals(sr.getRuleSetDisplayNames(JLocale.ENGLISH), tr.getRuleSetDisplayNames(ULocale.ENGLISH))
            assertEquals(sr.lenientParseEnabled(), tr.lenientParseEnabled())
            assertEquals(sr.getContext(JContext.Type.CAPITALIZATION).name, tr.getContext(DisplayContext.Type.CAPITALIZATION).name)
            assertEquals(sr.maximumFractionDigits, tr.getMaximumFractionDigits())
            assertEquals(sr.roundingMode, tr.getRoundingMode())
            for (n in listOf(0.0, 21.0, 115.5, 123.456, Double.NaN)) assertEquals(outcome { sr.format(n) }, outcome { tr.format(n) }, "$description $n")
            val sc = sr.clone(); val tc = tr.clone()
            sc.setRoundingMode(4); tc.setRoundingMode(4)
            assertEquals(outcome { sc.format(115.5) }, outcome { tc.format(115.5) })
        }
    }
}

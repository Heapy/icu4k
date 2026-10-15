// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import com.ibm.icu.text.SelectFormat as JSelect
import com.ibm.icu.text.MessagePattern as JPattern
import java.io.*
import kotlin.test.*

class SelectFormatOracleTest {
    private fun outcome(block: () -> Any?): String = try { "value:${block()}" } catch (e: RuntimeException) {
        when (e) {
            is NullPointerException -> "NullPointerException"
            is IndexOutOfBoundsException -> "IndexOutOfBoundsException"
            else -> "${e.javaClass.simpleName}:${e.message}"
        }
    }
    @Test fun selectionModesAndFailures() {
        val patterns = listOf(
            "other{default}", "a{first}a{second}other{last}", "female{She}male{He}other{They}",
            "a{'{name}' '' don't}other{#}", "other{{name,number,'x' 0} said ''hello''}",
            "other{{n,plural,one{'#'}other{#}}}", "o{source prefix bug}", "",
            "other{x", "other{x}}", "one{x}", "other{'x}", "😀{smile}other{fallback}",
        )
        for (mode in JPattern.ApostropheMode.entries) {
            val s = JSelect("other{initial}")
            val t = SelectFormat("other{initial}")
            val sf = JSelect::class.java.getDeclaredField("msgPattern").apply { isAccessible = true }
            val tf = SelectFormat::class.java.getDeclaredField("msgPattern").apply { isAccessible = true }
            sf.set(s, JPattern(mode).parseSelectStyle("other{initial}"))
            tf.set(t, MessagePattern(MessagePattern.ApostropheMode.valueOf(mode.name)).parseSelectStyle("other{initial}"))
            for (p in patterns) {
                assertEquals(outcome { s.applyPattern(p) }, outcome { t.applyPattern(p) })
                assertEquals(s.toPattern(), t.toPattern())
                assertEquals(s.toString(), t.toString())
                assertEquals(s.hashCode(), t.hashCode())
                for (key in listOf(null, "", "a", "female", "male", "other", "one", "unknown", "bad key", "😀", "0")) {
                    assertEquals(outcome { s.format(key) }, outcome { t.format(key) }, "$mode $p $key")
                    val sp = java.text.FieldPosition(1).also { it.beginIndex = 7; it.endIndex = 9 }
                    val tp = FormatFieldPosition(1).also { it.beginIndex = 7; it.endIndex = 9 }
                    val sb = StringBuffer("prefix:")
                    val tb = StringBuilder("prefix:")
                    assertEquals(outcome { s.format(key as Any?, sb, sp) }, outcome { t.format(key as Any?, tb, tp) })
                    assertEquals(sb.toString(), tb.toString())
                    assertEquals(sp.beginIndex, tp.beginIndex); assertEquals(sp.endIndex, tp.endIndex)
                    assertEquals(outcome { s.format(key as Any?, null, sp) }, outcome { t.format(key as Any?, null as StringBuilder?, tp) })
                }
            }
            assertEquals(outcome { s.applyPattern(null) }, outcome { t.applyPattern(null) })
            assertEquals(s.toPattern(), t.toPattern())
        }
    }
    @Test fun cloningEqualityAndJavaFormatBridge() {
        val s = JSelect("other{old}")
        val t = SelectFormat("other{old}")
        val sc = s.clone() as JSelect
        val tc = t.clone() as SelectFormat
        sc.applyPattern("other{new}"); tc.applyPattern("other{new}")
        assertEquals(s.format("x"), t.format("x"))
        assertEquals(s.toPattern(), t.toPattern())
        assertEquals(s == sc, t == tc)
        val sub = object : SelectFormat("other{new}") {}
        assertNotEquals(t, sub)
        val js: java.text.Format = s
        val jt: java.text.Format = t
        assertEquals(js.format("x" as Any), jt.format("x" as Any))
        assertEquals(outcome { js.parseObject(null) }, outcome { jt.parseObject(null) })
        for (x in listOf(null, 1, object { override fun toString() = "callback" })) {
            assertEquals(outcome { js.format(x, null, null) }, outcome { jt.format(x, null, null) })
        }
    }
    @Test fun streamDescriptorAndReparse() {
        val sd = ObjectStreamClass.lookup(JSelect::class.java)
        val td = ObjectStreamClass.lookup(SelectFormat::class.java)
        assertEquals(sd.serialVersionUID, td.serialVersionUID)
        assertEquals(sd.fields.map { it.name to it.type }, td.fields.map { it.name to it.type })
        fun round(value: Any): Any {
            val bytes = ByteArrayOutputStream()
            ObjectOutputStream(bytes).use { it.writeObject(value) }
            return ObjectInputStream(ByteArrayInputStream(bytes.toByteArray())).use { it.readObject() }
        }
        val s = round(JSelect("one{first}other{last}")) as JSelect
        val t = round(SelectFormat("one{first}other{last}")) as SelectFormat
        for (key in listOf("one", "other", "missing")) assertEquals(s.format(key), t.format(key))
        // Failed applyPattern clears the serialized pattern, so reading leaves no parsed structure.
        val invalid = SelectFormat("other{x}")
        assertFailsWith<IllegalArgumentException> { invalid.applyPattern("x") }
        val copy = round(invalid) as SelectFormat
        assertNull(copy.toPattern())
        assertFailsWith<IllegalStateException> { copy.format("other") }
    }
}

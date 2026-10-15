package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.util.ULocale
import java.io.*
import java.text.FieldPosition
import kotlin.test.*

class UFormatOracleTest {
    private class Sample : UFormat() {
        var shared = arrayListOf("value")
        override fun format(input: Any?, output: StringBuilder?, position: FormatFieldPosition?): StringBuilder {
            output!!.append(input); position!!.setBeginIndex(1); position.setEndIndex(output.length)
            if (input == "throw") throw IllegalStateException("failure")
            return if (input == "other") StringBuilder("different") else output
        }
        override fun parseObject(source: String?, position: ParsePosition?): Any? {
            position!!.index += 2; position.errorIndex = 9
            if (source == "throw") throw IllegalStateException("failure")
            return source
        }
        fun copy(): Sample = cloneHost() as Sample
    }
    private class JSample : com.ibm.icu.text.UFormat() {
        override fun format(obj: Any?, output: StringBuffer?, pos: FieldPosition?): StringBuffer {
            output!!.append(obj); pos!!.beginIndex = 1; pos.endIndex = output.length
            if (obj == "throw") throw IllegalStateException("failure")
            return if (obj == "other") StringBuffer("different") else output
        }
        override fun parseObject(source: String?, pos: java.text.ParsePosition?): Any? {
            pos!!.index += 2; pos.errorIndex = 9
            if (source == "throw") throw IllegalStateException("failure")
            return source
        }
    }
    @Test fun realJavaFormatBridgeAndCallbacks() {
        val k: java.text.Format = Sample(); val j: java.text.Format = JSample()
        for (input in listOf(null, 42, "text", "other", "throw")) {
            val kb = StringBuffer("pre:"); val jb = StringBuffer("pre:")
            val kp = FieldPosition(0); val jp = FieldPosition(0)
            val expected = runCatching { j.format(input, jb, jp) }; val actual = runCatching { k.format(input, kb, kp) }
            assertEquals(expected.exceptionOrNull()?.javaClass, actual.exceptionOrNull()?.javaClass)
            assertEquals(expected.getOrNull()?.toString(), actual.getOrNull()?.toString())
            assertEquals(jb.toString(), kb.toString()); assertEquals(jp.beginIndex, kp.beginIndex); assertEquals(jp.endIndex, kp.endIndex)
            assertEquals(expected.getOrNull() === jb, actual.getOrNull() === kb)
        }
        for (input in listOf(null, "text", "throw")) {
            val kp = java.text.ParsePosition(3); val jp = java.text.ParsePosition(3)
            val expected = runCatching { j.parseObject(input, jp) }; val actual = runCatching { k.parseObject(input, kp) }
            assertEquals(expected.exceptionOrNull()?.javaClass, actual.exceptionOrNull()?.javaClass)
            assertEquals(expected.getOrNull(), actual.getOrNull()); assertEquals(jp.index, kp.index); assertEquals(jp.errorIndex, kp.errorIndex)
        }
    }
    @Test fun localeMetadataCloneAndSerialization() {
        val k = Sample(); val j = JSample()
        val setter = com.ibm.icu.text.UFormat::class.java.getDeclaredMethod("setLocale", com.ibm.icu.util.ULocale::class.java, com.ibm.icu.util.ULocale::class.java).also { it.isAccessible = true }
        for ((valid, actual) in listOf(null to null, "en_US" to "en", null to "en", "en" to null, "fr" to "en")) {
            val kv = valid?.let(::ULocale); val ka = actual?.let(::ULocale)
            val jv = valid?.let { com.ibm.icu.util.ULocale(it) }; val ja = actual?.let { com.ibm.icu.util.ULocale(it) }
            val expected = runCatching { setter.invoke(j, jv, ja) }.exceptionOrNull()?.let { (it as java.lang.reflect.InvocationTargetException).cause }
            val actualFailure = runCatching { k.setLocale(kv, ka) }.exceptionOrNull()
            assertEquals(expected?.javaClass, actualFailure?.javaClass)
            for ((kt, jt) in listOf(ULocale.VALID_LOCALE to com.ibm.icu.util.ULocale.VALID_LOCALE, ULocale.ACTUAL_LOCALE to com.ibm.icu.util.ULocale.ACTUAL_LOCALE, null to null)) assertEquals(j.getLocale(jt)?.toString(), k.getLocale(kt)?.toString())
        }
        val copy = k.copy(); assertNotSame(k, copy); assertSame(k.shared, copy.shared)
        assertSame(k.getLocale(null), copy.getLocale(null))
        val bytes = ByteArrayOutputStream().also { ObjectOutputStream(it).use { out -> out.writeObject(k) } }.toByteArray()
        val restored = ObjectInputStream(ByteArrayInputStream(bytes)).use { it.readObject() } as Sample
        assertEquals(k.getLocale(null), restored.getLocale(null)); assertEquals(k.getLocale(ULocale.ACTUAL_LOCALE), restored.getLocale(ULocale.ACTUAL_LOCALE))
        assertEquals(ObjectStreamClass.lookup(com.ibm.icu.text.UFormat::class.java).serialVersionUID, ObjectStreamClass.lookup(UFormat::class.java).serialVersionUID)
        val span: UFormat.SpanField = object : FormatSpanField("test") {}
        assertTrue(span is java.text.Format.Field)
    }
    @Test fun displayContextEnums() {
        assertEquals(com.ibm.icu.text.DisplayContext.values().map { Triple(it.name, it.type().name, it.value()) }, DisplayContext.entries.map { Triple(it.name, it.type().name, it.value()) })
        assertEquals(com.ibm.icu.text.DisplayContext.Type.values().map { it.name }, DisplayContext.Type.entries.map { it.name })
    }
}

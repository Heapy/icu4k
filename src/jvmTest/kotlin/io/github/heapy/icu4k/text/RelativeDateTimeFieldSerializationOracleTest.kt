// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import com.ibm.icu.text.RelativeDateTimeFormatter as Source
import java.io.*
import kotlin.test.*

class RelativeDateTimeFieldSerializationOracleTest {
    private fun bytes(value: Any): ByteArray = ByteArrayOutputStream().also { buffer ->
        ObjectOutputStream(buffer).use { it.writeObject(value) }
    }.toByteArray()
    private fun read(data: ByteArray, from: String, to: String): Any = object : ObjectInputStream(ByteArrayInputStream(data)) {
        override fun resolveClass(desc: ObjectStreamClass): Class<*> =
            if (desc.name.startsWith(from)) Class.forName(desc.name.replace(from, to)) else super.resolveClass(desc)
    }.use { it.readObject() }
    @Test fun fieldDescriptorsAndCanonicalStreamsMatchSource() {
        val source = ObjectStreamClass.lookup(Source.Field::class.java)
        val port = ObjectStreamClass.lookup(RelativeDateTimeFormatter.Field::class.java)
        assertEquals(source.serialVersionUID, port.serialVersionUID)
        assertEquals(source.fields.map { it.name to it.typeString }, port.fields.map { it.name to it.typeString })
        for ((s, p) in listOf(Source.Field.LITERAL to RelativeDateTimeFormatter.Field.LITERAL,
            Source.Field.NUMERIC to RelativeDateTimeFormatter.Field.NUMERIC)) {
            assertSame(p, read(bytes(s), "com.ibm.icu.", "io.github.heapy.icu4k."))
            assertSame(s, read(bytes(p), "io.github.heapy.icu4k.", "com.ibm.icu."))
            assertSame(p, read(bytes(p), "io.github.heapy.icu4k.", "io.github.heapy.icu4k."))
        }
    }
    @Test fun malformedFieldNamesRetainSourceResolutionFailures() {
        for (clazz in listOf(Source.Field::class.java, RelativeDateTimeFormatter.Field::class.java)) {
            val from = if (clazz == Source.Field::class.java) "com.ibm.icu." else "io.github.heapy.icu4k."
            val constructor = clazz.getDeclaredConstructor(String::class.java).apply { isAccessible = true }
            for (name in listOf("", "unknown", "Literal", "NUMERIC", null)) {
                val value = constructor.newInstance(name)
                for (to in listOf("com.ibm.icu.", "io.github.heapy.icu4k.")) {
                    if (name == null) assertFailsWith<NullPointerException> { read(bytes(value), from, to) }
                    else assertEquals("An invalid object.", assertFailsWith<InvalidObjectException> { read(bytes(value), from, to) }.message)
                }
            }
        }
    }
}

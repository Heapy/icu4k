@file:Suppress("UNCHECKED_CAST")
package io.github.heapy.icu4k.text

import com.ibm.icu.text.MessageFormat as Source
import com.ibm.icu.util.ULocale as SourceLocale
import io.github.heapy.icu4k.util.ULocale
import java.io.*
import kotlin.test.*

class MessageFormatSerializationOracleTest {
    private fun bytes(value: Any): ByteArray = ByteArrayOutputStream().also { output -> ObjectOutputStream(output).use { it.writeObject(value) } }.toByteArray()
    private fun read(data: ByteArray, from: String? = null, to: String? = null): Any = object : ObjectInputStream(ByteArrayInputStream(data)) {
        override fun resolveClass(desc: ObjectStreamClass): Class<*> {
            if (from != null && desc.name.contains(from)) try { return Class.forName(desc.name.replace(from, to!!)) } catch (_: ClassNotFoundException) { }
            return super.resolveClass(desc)
        }
    }.use { it.readObject() }
    private fun compare(source: Source, port: MessageFormat) {
        assertEquals(source.getULocale().toString(), port.getULocale().toString())
        assertEquals(source.apostropheMode.name, port.getApostropheMode()!!.name)
        assertEquals(source.argumentNames, port.getArgumentNames())
        for (number in listOf(0, 1, 2, 1234)) assertEquals(source.format(arrayOf<Any?>(number, "A")), port.format(arrayOf<Any?>(number, "A")))
    }
    @Test fun descriptorsFieldsAndBidirectionalStreams() {
        for ((source, port) in listOf(Source::class.java to MessageFormat::class.java, Source.Field::class.java to MessageFormat.Field::class.java)) {
            val a = ObjectStreamClass.lookup(source); val b = ObjectStreamClass.lookup(port)
            assertEquals(a.serialVersionUID, b.serialVersionUID)
            assertEquals(a.fields.map { it.name }, b.fields.map { it.name })
        }
        assertSame(MessageFormat.Field.ARGUMENT, read(bytes(MessageFormat.Field.ARGUMENT)))
        assertSame(MessageFormat.Field.ARGUMENT, read(bytes(Source.Field.ARGUMENT), "com.ibm.icu.", "io.github.heapy.icu4k."))
        assertSame(Source.Field.ARGUMENT, read(bytes(MessageFormat.Field.ARGUMENT), "io.github.heapy.icu4k.", "com.ibm.icu."))
        for (id in listOf("en_US", "fr", "ar")) for (pattern in listOf("{0,number} / {1}", "{0,plural,one{# item} other{# items}}", "'{0}' {1}")) {
            val a = Source(pattern, SourceLocale(id)); val b = MessageFormat(pattern, ULocale(id))
            compare(read(bytes(a)) as Source, read(bytes(b)) as MessageFormat)
            compare(read(bytes(b), "io.github.heapy.icu4k.", "com.ibm.icu.") as Source, read(bytes(a), "com.ibm.icu.", "io.github.heapy.icu4k.") as MessageFormat)
        }
    }
    @Test fun customRawFormatsAndCloneGraphRetainSharing() {
        val a = Source("{0} / {0} / {1}", SourceLocale.FRANCE)
        val b = MessageFormat("{0} / {0} / {1}", ULocale.FRANCE)
        val choice = java.text.ChoiceFormat("0#none|1#{1}")
        a.setFormatByArgumentIndex(0, choice); b.setFormatByArgumentIndex(0, choice)
        a.setFormat(2, null); b.setFormat(2, null)
        val aa = read(bytes(arrayOf(a, a.clone()))) as Array<Source>
        val bb = read(bytes(arrayOf(b, b.clone()))) as Array<MessageFormat>
        assertSame(bb[0].getFormats()[0], bb[0].getFormats()[1])
        assertSame(bb[0].getFormats()[0], bb[1].getFormats()[0])
        compare(aa[0], bb[0]); compare(aa[1], bb[1])
        compare(read(bytes(b), "io.github.heapy.icu4k.", "com.ibm.icu.") as Source, read(bytes(a), "com.ibm.icu.", "io.github.heapy.icu4k.") as MessageFormat)
        assertFailsWith<IllegalStateException> { bb[0].toPattern() }
        bb[1].setFormat(0, null)
        assertIs<java.text.ChoiceFormat>(bb[0].getFormats()[0])
    }
}

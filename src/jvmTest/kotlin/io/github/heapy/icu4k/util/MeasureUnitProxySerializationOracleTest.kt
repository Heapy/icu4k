package io.github.heapy.icu4k.util

import java.io.*
import kotlin.test.*

class MeasureUnitProxySerializationOracleTest {
    private fun bytes(value: Any): ByteArray = ByteArrayOutputStream().also { b -> ObjectOutputStream(b).use { it.writeObject(value) } }.toByteArray()
    private fun read(data: ByteArray, from: String? = null, to: String? = null): Any = object : ObjectInputStream(ByteArrayInputStream(data)) {
        override fun resolveClass(desc: ObjectStreamClass): Class<*> {
            if (from != null && desc.name.startsWith(from)) return Class.forName(desc.name.replace(from, to!!))
            return super.resolveClass(desc)
        }
    }.use { it.readObject() }
    @Test fun proxyNameAndDescriptorMatchSource() {
        val source = ObjectStreamClass.lookup(Class.forName("com.ibm.icu.util.MeasureUnit\$MeasureUnitProxy"))
        val port = ObjectStreamClass.lookup(MeasureUnit.MeasureUnitProxy::class.java)
        assertEquals(source.name.replace("com.ibm.icu.", "io.github.heapy.icu4k."), port.name)
        assertEquals(source.serialVersionUID, port.serialVersionUID)
        assertEquals(source.fields.map { it.name }, port.fields.map { it.name })
    }
    @Test fun unitAndCurrencyStreamsCrossReadToCanonicalInstances() {
        for ((source, port) in listOf(com.ibm.icu.util.MeasureUnit.METER to MeasureUnit.METER,
            com.ibm.icu.util.Currency.getInstance("EUR") to Currency.getInstance("EUR"))) {
            assertSame(port, read(bytes(source), "com.ibm.icu.", "io.github.heapy.icu4k."))
            assertSame(source, read(bytes(port), "io.github.heapy.icu4k.", "com.ibm.icu."))
            assertSame(port, read(bytes(port)))
        }
    }
    @Test fun earlierPortProxyStreamsRemainReadable() {
        assertSame(MeasureUnit.METER, read(bytes(MeasureUnitSerialProxy("length", "meter"))))
        assertSame(Currency.getInstance("EUR"), read(bytes(MeasureUnitSerialProxy("currency", "EUR"))))
    }
}

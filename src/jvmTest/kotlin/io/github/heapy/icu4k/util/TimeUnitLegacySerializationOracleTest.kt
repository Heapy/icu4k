// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.util

import java.io.*
import kotlin.test.*

class TimeUnitLegacySerializationOracleTest {
    private val sourcePrefix = "com.ibm.icu."
    private val portPrefix = "io.github.heapy.icu4k."

    private fun legacyBytes(value: Any): ByteArray {
        val buffer = ByteArrayOutputStream()
        object : ObjectOutputStream(buffer) {
            init { enableReplaceObject(true) }
            override fun replaceObject(obj: Any?): Any? =
                if (obj?.javaClass?.name?.endsWith("\$MeasureUnitProxy") == true) value else obj
        }.use { it.writeObject(value) }
        return buffer.toByteArray()
    }

    private fun read(data: ByteArray, from: String, to: String): Any =
        object : ObjectInputStream(ByteArrayInputStream(data)) {
            override fun resolveClass(desc: ObjectStreamClass): Class<*> =
                if (desc.name.startsWith(from)) Class.forName(desc.name.replace(from, to))
                else super.resolveClass(desc)
        }.use { it.readObject() }

    private fun timeUnit(clazz: Class<*>, index: Int): Any {
        val value = clazz.getDeclaredConstructor(String::class.java, String::class.java).apply {
            isAccessible = true
        }.newInstance("duration", "hour")
        clazz.getDeclaredField("index").apply { isAccessible = true }.setInt(value, index)
        return value
    }

    @Test fun persistentDescriptorsMatchSource() {
        for ((sourceClass, portClass) in listOf(
            com.ibm.icu.util.MeasureUnit::class.java to MeasureUnit::class.java,
            com.ibm.icu.util.TimeUnit::class.java to TimeUnit::class.java
        )) {
            val source = ObjectStreamClass.lookup(sourceClass)
            val port = ObjectStreamClass.lookup(portClass)
            assertEquals(source.serialVersionUID, port.serialVersionUID, sourceClass.name)
            fun fields(descriptor: ObjectStreamClass) = descriptor.fields.map {
                Triple(it.name, it.typeCode, it.typeString?.replace("com/ibm/icu/", "io/github/heapy/icu4k/"))
            }
            assertEquals(fields(source), fields(port), sourceClass.name)
        }
    }

    @Test fun legacyIndicesResolveToCanonicalUnitsAcrossRuntimes() {
        val source = listOf(com.ibm.icu.util.TimeUnit.YEAR, com.ibm.icu.util.TimeUnit.MONTH,
            com.ibm.icu.util.TimeUnit.WEEK, com.ibm.icu.util.TimeUnit.DAY,
            com.ibm.icu.util.TimeUnit.HOUR, com.ibm.icu.util.TimeUnit.MINUTE, com.ibm.icu.util.TimeUnit.SECOND)
        val port = listOf(TimeUnit.YEAR, TimeUnit.MONTH, TimeUnit.WEEK, TimeUnit.DAY,
            TimeUnit.HOUR, TimeUnit.MINUTE, TimeUnit.SECOND)
        for (index in 0..6) {
            val sourceBytes = legacyBytes(timeUnit(com.ibm.icu.util.TimeUnit::class.java, index))
            val portBytes = legacyBytes(timeUnit(TimeUnit::class.java, index))
            assertSame(source[index], read(sourceBytes, sourcePrefix, sourcePrefix))
            assertSame(port[index], read(sourceBytes, sourcePrefix, portPrefix))
            assertSame(source[index], read(portBytes, portPrefix, sourcePrefix))
            assertSame(port[index], read(portBytes, portPrefix, portPrefix))
        }
    }

    @Test fun malformedLegacyIndicesRetainSourceExceptionAndMessage() {
        for (index in listOf(Int.MIN_VALUE, -1, 7, 255, Int.MAX_VALUE)) {
            for ((clazz, from) in listOf(com.ibm.icu.util.TimeUnit::class.java to sourcePrefix,
                TimeUnit::class.java to portPrefix)) {
                val data = legacyBytes(timeUnit(clazz, index))
                for (to in listOf(sourcePrefix, portPrefix)) {
                    val error = assertFailsWith<InvalidObjectException> { read(data, from, to) }
                    assertEquals("Bad index: $index", error.message)
                }
            }
        }
    }

    @Test fun normalTimeUnitProxyStreamsStillResolveCanonically() {
        for ((source, port) in com.ibm.icu.util.TimeUnit.values().zip(TimeUnit.values())) {
            fun bytes(value: Any): ByteArray = ByteArrayOutputStream().also { buffer ->
                ObjectOutputStream(buffer).use { it.writeObject(value) }
            }.toByteArray()
            assertSame(port, read(bytes(source), sourcePrefix, portPrefix))
            assertSame(source, read(bytes(port), portPrefix, sourcePrefix))
        }
    }
}

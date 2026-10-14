package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.util.BinaryBuffer
import java.nio.ByteBuffer
import kotlin.test.*

class ResourceReaderViewsOracleTest {
    private fun call(target: Any, name: String, vararg args: Any): Any? {
        var cls: Class<*>? = target.javaClass
        while (cls != null) {
            cls.declaredMethods.firstOrNull { it.name == name && it.parameterCount == args.size }?.let {
                it.isAccessible = true
                return it.invoke(target, *args)
            }
            cls = cls.superclass
        }
        error(name)
    }
    private fun fixture(order: BinaryBuffer.ByteOrder): ByteArray {
        val body = BinaryBuffer(ByteArray(256), order)
        body.writeInt(0x90000004.toInt())
        for (value in intArrayOf(8, 10, 64, 64, 1, 0, 20, 0)) body.writeInt(value)
        body.position = 36; body.writeByte(97); body.writeByte(0)
        body.position = 42; body.writeShort(118); body.writeShort(0)
        body.position = 48; body.writeShort(1); body.writeShort(1)
        body.position = 56; body.writeShort(121); body.writeShort(0)
        body.position = 60; repeat(3) { body.writeShort(0x2205) }; body.writeShort(0)
        body.position = 128; body.writeInt(2); body.writeShort(111); body.writeShort(107)
        body.position = 152; body.writeInt(3); body.writeByte(1); body.writeByte(2); body.writeByte(3)
        body.position = 164; body.writeInt(0)
        val header = ICUBinary.writeHeader(0x52657342, 0x03000000, 0x11223344)
        if (order == BinaryBuffer.ByteOrder.LITTLE_ENDIAN) {
            header[0] = 32; header[1] = 0; header[4] = 20; header[5] = 0; header[8] = 0
        }
        return header + body.bytes
    }
    @Test fun sharedUnitsAndBinaryByteOrdersMatchBothInputProtectionModes() {
        val cls = com.ibm.icu.impl.ICUResourceBundleReader::class.java
        val ctor = cls.getDeclaredConstructor(ByteBuffer::class.java, String::class.java, String::class.java, ClassLoader::class.java)
            .also { it.isAccessible = true }
        for (order in BinaryBuffer.ByteOrder.entries) for (readOnly in listOf(false, true)) {
            val source = fixture(order)
            val jInput = ByteBuffer.wrap(source).let { if (readOnly) it.asReadOnlyBuffer() else it }
            val kInput = BinaryBuffer(source, isReadOnly = readOnly)
            val j = ctor.newInstance(jInput, "test/views", "root", cls.classLoader)
            val k = ICUResourceBundleReader(kInput)
            assertEquals(call(j, "getVersion").toString(), k.getVersion().toString())
            for (res in listOf(0x10000026, 0x10000029, 0x10000000)) {
                val jb = call(j, "getBinary", res) as ByteBuffer
                val kb = k.binaryBuffer(res)!!
                assertEquals(jb.remaining(), kb.remaining)
                assertEquals(jb.isReadOnly, kb.isReadOnly)
                assertEquals(jb.order().toString(), kb.order.name)
                val payload = ByteArray(jb.remaining()); jb.get(payload)
                assertContentEquals(payload, kb.readBytes(kb.remaining))
            }
            val ja = call(j, "getArray", 0x90000004.toInt())!!
            val ka = k.getArray(0x90000004.toInt())!!
            assertEquals(call(j, "getString", call(ja, "getContainerResource", j, 0) as Int),
                k.getString(ka.getContainerResource(0)))
            val marker = 0x6000000a
            val value = ICUResourceBundleReader.ReaderValue().also { it.reader = k; it.res = marker }
            assertEquals(call(j, "isNoInheritanceMarker", marker), value.isNoInheritanceMarker())
            val owner = BinaryBuffer(source, order)
            owner.position = 32 + 60; owner.writeShort(120)
            assertEquals(false, call(j, "isNoInheritanceMarker", marker))
            assertFalse(value.isNoInheritanceMarker())
            owner.position = 32 + 50; owner.writeShort(8)
            val jRes = call(ja, "getContainerResource", j, 0) as Int
            assertEquals(jRes, ka.getContainerResource(0))
            assertEquals("y", call(j, "getString", jRes)); assertEquals("y", k.getString(jRes))
            assertEquals("ok", call(j, "getString", 32)); assertEquals("ok", k.getString(32))
        }
    }
}

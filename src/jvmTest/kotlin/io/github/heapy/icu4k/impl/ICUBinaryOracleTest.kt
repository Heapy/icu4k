package io.github.heapy.icu4k.impl

import com.ibm.icu.impl.ICUBinary as JavaBinary
import io.github.heapy.icu4k.util.BinaryBuffer
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.*

class ICUBinaryOracleTest {
    @Test fun serializesHeadersExactlyAsIcu() {
        val output = ByteArrayOutputStream()
        JavaBinary.writeHeader(0x4c61796f, 0x01020304, 0x11000000, DataOutputStream(output))
        val actual = ICUBinary.writeHeader(0x4c61796f, 0x01020304, 0x11000000)
        assertContentEquals(output.toByteArray(), actual)
        val expectedBuffer = ByteBuffer.wrap(actual)
        val actualBuffer = BinaryBuffer(actual)
        assertEquals(JavaBinary.readHeader(expectedBuffer, 0x4c61796f, null), ICUBinary.readHeader(actualBuffer, 0x4c61796f, null))
        assertEquals(expectedBuffer.position(), actualBuffer.position)
    }

    @Test fun typedArrayReadsMatchIcu() {
        val data = ByteArray(80) { (it * 117).toByte() }
        for (little in listOf(false, true)) {
            fun javaBuffer() = ByteBuffer.wrap(data).order(if (little) ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN)
            fun commonBuffer() = BinaryBuffer(data, if (little) BinaryBuffer.ByteOrder.LITTLE_ENDIAN else BinaryBuffer.ByteOrder.BIG_ENDIAN)
            assertContentEquals(JavaBinary.getBytes(javaBuffer(), 17, 3), ICUBinary.getBytes(commonBuffer(), 17, 3))
            assertContentEquals(JavaBinary.getChars(javaBuffer(), 17, 3), ICUBinary.getChars(commonBuffer(), 17, 3))
            assertContentEquals(JavaBinary.getShorts(javaBuffer(), 17, 3), ICUBinary.getShorts(commonBuffer(), 17, 3))
            assertContentEquals(JavaBinary.getInts(javaBuffer(), 17, 3), ICUBinary.getInts(commonBuffer(), 17, 3))
            assertContentEquals(JavaBinary.getLongs(javaBuffer(), 9, 3), ICUBinary.getLongs(commonBuffer(), 9, 3))
            assertEquals(JavaBinary.getString(javaBuffer(), 17, 3), ICUBinary.getString(commonBuffer(), 17, 3))
            val j = javaBuffer()
            val k = commonBuffer()
            JavaBinary.getChars(j, 17, 3)
            ICUBinary.getChars(k, 17, 3)
            assertEquals(j.position(), k.position)
        }
    }
}

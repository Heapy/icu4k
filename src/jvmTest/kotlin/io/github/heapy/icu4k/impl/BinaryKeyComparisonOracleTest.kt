package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.util.BinaryBuffer
import java.nio.ByteBuffer
import kotlin.random.Random
import kotlin.test.*

class BinaryKeyComparisonOracleTest {
    @Test fun exactResultsMatchOriginalForArbitraryBytesAndUtf16Queries() {
        val cls = com.ibm.icu.impl.ICUBinary::class.java
        val arrayMethod = cls.getDeclaredMethod("compareKeys", CharSequence::class.java, ByteArray::class.java, Int::class.javaPrimitiveType).also { it.isAccessible = true }
        val bufferMethod = cls.getDeclaredMethod("compareKeys", CharSequence::class.java, ByteBuffer::class.java, Int::class.javaPrimitiveType).also { it.isAccessible = true }
        val random = Random(78)
        repeat(3000) {
            val size = random.nextInt(0, 20)
            val bytes = ByteArray(size + 4) { (random.nextInt(1, 256)).toByte() }
            bytes[size + 2] = 0
            val key = if (random.nextBoolean()) (0 until random.nextInt(20)).map { random.nextInt(65536).toChar() }.joinToString("")
                else bytes.copyOfRange(2, size + 2).map { (it.toInt() and 255).toChar() }.joinToString("")
            assertEquals(arrayMethod.invoke(null, key, bytes, 2), ICUBinary.compareKeys(key, bytes, 2))
            val original = ByteBuffer.wrap(bytes).asReadOnlyBuffer().also { it.position(1) }
            val port = BinaryBuffer(bytes).asReadOnlyBuffer().also { it.position = 1 }
            assertEquals(bufferMethod.invoke(null, key, original, 2), ICUBinary.compareKeys(key, port, 2))
            assertEquals(1, original.position()); assertEquals(1, port.position)
        }
    }
}

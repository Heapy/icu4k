package io.github.heapy.icu4k.charset

import io.github.heapy.icu4k.util.BinaryBuffer
import kotlin.test.*

class EncoderPrimitiveTest {
    @Test fun everyCodePointFallbackAndIgnorableRuleMatchesSource() {
        var hash = 1
        for (cp in -1..0x110000) {
            hash = 31 * hash + if (CharsetEncoderRuntime.isFromUUseFallback(false, cp)) 1 else 0
            hash = 31 * hash + if (CharsetEncoderRuntime.isFromUUseFallback(true, cp)) 1 else 0
            hash = 31 * hash + if (CharsetEncoderCallbacks.isDefaultIgnorable(cp)) 1 else 0
        }
        assertEquals(encoderCodePointRulesGolden, hash)
    }
    @Test fun partialByteWriteKeepsSourceOffsetQuirkAndQueuesRemainder() {
        val e = CharsetSingleByteEncoder()
        val target = BinaryBuffer(ByteArray(1)); val offsets = CharsetIntBuffer(IntArray(3))
        assertEquals("OVERFLOW", CharsetEncoderRuntime.fromUWriteBytes(e, byteArrayOf(1, 2, 3), 0, 3, target, offsets, 17).toString())
        assertEquals(0, offsets.position); assertEquals(1, target.position)
        assertEquals(2, e.errorBufferLength); assertContentEquals(byteArrayOf(2, 3), e.errorBuffer.copyOf(2))
        val next = BinaryBuffer(ByteArray(2)); val nextOffsets = CharsetIntBuffer(IntArray(2))
        assertEquals("UNDERFLOW", e.encode(CharsetCharBuffer(CharArray(0)), next, nextOffsets, false).toString())
        assertContentEquals(byteArrayOf(2, 3), next.bytes); assertContentEquals(intArrayOf(-1, -1), nextOffsets.array)
    }
    @Test fun surrogateArrayAndCursorHelpersHaveDifferentConsumption() {
        val e = CharsetSingleByteEncoder()
        val input = charArrayOf(0xdc00.toChar())
        val cursor = CharsetCharBuffer(input)
        assertNull(e.handleSurrogates(cursor, 0xd800.toChar())); assertEquals(0x10000, e.fromUChar32); assertEquals(1, cursor.position)
        assertNull(e.handleSurrogates(input, 0, 1, 0xd800.toChar())); assertEquals(0x10000, e.fromUChar32)
        val bad = CharsetCharBuffer(charArrayOf('A'))
        assertEquals("MALFORMED[1]", e.handleSurrogates(bad, 0xdbff.toChar()).toString()); assertEquals(0, bad.position); assertEquals(0xdbff, e.fromUChar32)
    }
}

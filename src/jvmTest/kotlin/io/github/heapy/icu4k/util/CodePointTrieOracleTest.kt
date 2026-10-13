package io.github.heapy.icu4k.util

import com.ibm.icu.util.CodePointTrie as JavaTrie
import com.ibm.icu.util.CodePointMap as JavaMap
import com.ibm.icu.util.MutableCodePointTrie as JavaMutableTrie
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import kotlin.random.Random
import kotlin.test.*

class CodePointTrieOracleTest {
    private fun serialized(trie: JavaTrie): ByteArray = ByteArrayOutputStream().also { trie.toBinary(it) }.toByteArray()

    private fun compare(expected: JavaTrie, actual: CodePointTrie) {
        assertEquals(expected.type.name, actual.getType().name)
        assertEquals(expected.valueWidth.name, actual.getValueWidth().name)
        for (cp in -1..0x110000) {
            val a = actual[cp]
            val b = expected.get(cp)
            if (a != b) fail("Lookup U+${cp.toString(16)}: expected $b, actual $a")
        }
        for (cp in 0..127) assertEquals(expected.asciiGet(cp), actual.asciiGet(cp))
        if (actual is CodePointTrie.Fast) {
            expected as JavaTrie.Fast
            for (cp in 0..0xffff) assertEquals(expected.bmpGet(cp), actual.bmpGet(cp))
            for (cp in listOf(0x10000, 0x10001, 0x10ffff)) assertEquals(expected.suppGet(cp), actual.suppGet(cp))
        }
        assertContentEquals(serialized(expected), actual.toBinary())
        for (filter in listOf<((Int) -> Int)?>(null, { it and 3 }, { 0 })) {
            var start = 0
            val a = CodePointMap.Range()
            val b = JavaMap.Range()
            val af = filter?.let { f -> CodePointMap.ValueFilter { f(it) } }
            val bf = filter?.let { f -> JavaMap.ValueFilter { f(it) } }
            while (start <= 0x10ffff) {
                assertTrue(expected.getRange(start, bf, b))
                assertTrue(actual.getRange(start, af, a))
                assertEquals(b.start, a.getStart())
                assertEquals(b.end, a.getEnd(), "Range from U+${start.toString(16)}")
                assertEquals(b.value, a.getValue())
                start = b.end + 1
            }
            assertFalse(actual.getRange(start, af, a))
        }
        val text = charArrayOf('A', 0xd800.toChar(), 0xdc00.toChar(), 0xdbff.toChar(), 'B', 0xdfff.toChar(), 0xdbff.toChar(), 0xdfff.toChar()).concatToString()
        for (offset in 0..text.length) {
            val a = actual.stringIterator(text, offset)
            val b = expected.stringIterator(text, offset)
            repeat(text.length + 1) {
                assertEquals(b.next(), a.next())
                assertEquals(b.index, a.getIndex())
                assertEquals(b.codePoint, a.getCodePoint())
                assertEquals(b.value, a.getValue())
            }
            repeat(text.length + 1) {
                assertEquals(b.previous(), a.previous())
                assertEquals(b.index, a.getIndex())
                assertEquals(b.codePoint, a.getCodePoint())
                assertEquals(b.value, a.getValue())
            }
        }
    }

    @Test fun allSixFormatsMatchIcuAcrossEntireUnicodeRange() {
        val random = Random(78_006)
        val mutable = JavaMutableTrie(7, -1)
        mutable.setRange(0, 0x7f, 1)
        mutable.setRange(0x3400, 0x4dbf, 2)
        mutable.setRange(0xd800, 0xdfff, 3)
        mutable.setRange(0x10000, 0x1ffff, 4)
        mutable.setRange(0x20000, 0x2ffff, 7)
        mutable.setRange(0x10ff00, 0x10ffff, 9)
        repeat(600) {
            val start = random.nextInt(0x10f000)
            mutable.setRange(start, start + random.nextInt(200), random.nextInt())
        }
        for (type in JavaTrie.Type.entries) for (width in JavaTrie.ValueWidth.entries) {
            val expected = mutable.clone().buildImmutable(type, width)
            val bytes = serialized(expected)
            val actual = CodePointTrie.fromBinary(null, null, bytes)
            compare(expected, actual)
            val swapped = actual.toBinary(BinaryBuffer.ByteOrder.LITTLE_ENDIAN)
            val javaBuffer = ByteBuffer.wrap(swapped)
            val restored = JavaTrie.fromBinary(type, width, javaBuffer)
            assertEquals(bytes.size, javaBuffer.position())
            assertContentEquals(bytes, serialized(restored))
            assertContentEquals(bytes, CodePointTrie.fromBinary(null, null, swapped).toBinary())
        }
    }

    @Test fun highDataOffsetsUsePacked18BitIndexBlocks() {
        val mutable = JavaMutableTrie(0, -1)
        for (cp in 0 until 0x20000) mutable.set(cp, cp * 0x12345)
        mutable.setRange(0x20000, 0x2ffff, 17)
        mutable.setRange(0x30000, 0x40000, 18)
        for (type in JavaTrie.Type.entries) {
            val expected = mutable.clone().buildImmutable(type, JavaTrie.ValueWidth.BITS_32)
            val bytes = serialized(expected)
            // The data length high bits prove this fixture crosses the 16-bit offset limit.
            assertTrue((ByteBuffer.wrap(bytes).getShort(4).toInt() and 0xf000) != 0)
            compare(expected, CodePointTrie.fromBinary(null, null, bytes))
        }
    }
}

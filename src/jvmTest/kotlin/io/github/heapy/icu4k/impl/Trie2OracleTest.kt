package io.github.heapy.icu4k.impl

import com.ibm.icu.impl.Trie2 as JavaTrie
import com.ibm.icu.impl.Trie2_16 as JavaTrie16
import com.ibm.icu.impl.Trie2_32 as JavaTrie32
import com.ibm.icu.impl.Trie2Writable as JavaWritable
import com.ibm.icu.impl.ICUBinary as JavaBinary
import io.github.heapy.icu4k.util.BinaryBuffer
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import kotlin.random.Random
import kotlin.test.*

class Trie2OracleTest {
    private fun serialize(trie: JavaTrie): ByteArray = ByteArrayOutputStream().also {
        when (trie) { is JavaTrie16 -> trie.serialize(it); is JavaTrie32 -> trie.serialize(it) }
    }.toByteArray()

    private fun compareRanges(expected: Iterator<JavaTrie.Range>, actual: Iterator<Trie2.Range>) {
        while (expected.hasNext()) {
            assertTrue(actual.hasNext())
            val a = actual.next()
            val b = expected.next()
            assertEquals(b.startCodePoint, a.startCodePoint)
            assertEquals(b.endCodePoint, a.endCodePoint)
            assertEquals(b.value, a.value)
            assertEquals(b.leadSurrogate, a.leadSurrogate)
            assertEquals(b.hashCode(), a.hashCode())
        }
        assertFalse(actual.hasNext())
        assertFailsWith<NoSuchElementException> { actual.next() }
    }

    private fun compare(expected: JavaTrie, actual: Trie2) {
        for (cp in -1..0x110000) {
            val a = actual[cp]
            val b = expected.get(cp)
            if (a != b) fail("Trie2 lookup U+${cp.toString(16)}: expected $b, actual $a")
        }
        for (c in 0..0xffff) assertEquals(expected.getFromU16SingleLead(c.toChar()), actual.getFromU16SingleLead(c.toChar()))
        for (map in listOf<(Int) -> Int>({ it }, { it and 3 }, { 0 })) {
            compareRanges(expected.iterator(JavaTrie.ValueMapper { map(it) }), actual.iterator(Trie2.ValueMapper { map(it) }))
            for (lead in listOf(0xd800, 0xd83d, 0xdbff)) {
                compareRanges(expected.iteratorForLeadSurrogate(lead.toChar(), JavaTrie.ValueMapper { map(it) }), actual.iteratorForLeadSurrogate(lead.toChar(), Trie2.ValueMapper { map(it) }))
            }
        }
        assertEquals(expected.hashCode(), actual.hashCode())
        assertContentEquals(serialize(expected), actual.serialize())
        assertEquals(actual, Trie2.createFromSerialized(actual.serialize()))
        val little = actual.serialize(BinaryBuffer.ByteOrder.LITTLE_ENDIAN)
        assertEquals(expected, JavaTrie.createFromSerialized(ByteBuffer.wrap(little)))
        assertEquals(actual, Trie2.createFromSerialized(little))
        for (bytes in listOf(actual.serialize(), little, byteArrayOf(1, 2), "Trie".encodeToByteArray())) {
            for (acceptLittle in listOf(false, true)) {
                assertEquals(JavaTrie.getVersion(ByteArrayInputStream(bytes), acceptLittle), Trie2.getVersion(BinaryBuffer(bytes), acceptLittle))
            }
        }
        val text = charArrayOf('A', 0xd800.toChar(), 0xdc00.toChar(), 0xd800.toChar(), 'b', 0xdc00.toChar(), 0xdbff.toChar(), 0xdfff.toChar()).concatToString()
        for (start in 0..text.length) {
            val a = actual.charSequenceIterator(text, start)
            val b = expected.charSequenceIterator(text, start)
            while (b.hasNext()) {
                assertTrue(a.hasNext())
                val av = a.next(); val bv = b.next()
                assertEquals(bv.index, av.index)
                assertEquals(bv.codePoint, av.codePoint)
                assertEquals(bv.value, av.value)
            }
            assertFalse(a.hasNext())
            while (b.hasPrevious()) {
                assertTrue(a.hasPrevious())
                val av = a.previous(); val bv = b.previous()
                assertEquals(bv.index, av.index)
                assertEquals(bv.codePoint, av.codePoint)
                assertEquals(bv.value, av.value)
            }
            assertFalse(a.hasPrevious())
        }
    }

    @Test fun generated16And32BitTriesMatchIcu() {
        val mutable = JavaWritable(7, 65535)
        mutable.setRange(0x3400, 0x4dbf, 11, true)
        mutable.setRange(0xd800, 0xdfff, 13, true)
        mutable.setRange(0x10000, 0x1ffff, 17, true)
        mutable.setForLeadSurrogateCodeUnit(0xd800.toChar(), 19)
        mutable.setForLeadSurrogateCodeUnit(0xdbff.toChar(), 23)
        val random = Random(78_007)
        repeat(500) {
            val start = random.nextInt(0x10ff00)
            mutable.setRange(start, start + random.nextInt(200), random.nextInt(65536), true)
        }
        val expected16 = mutable.toTrie2_16()
        val expected32 = mutable.toTrie2_32()
        val actual16 = Trie2_16.createFromSerialized(serialize(expected16))
        val actual32 = Trie2_32.createFromSerialized(serialize(expected32))
        compare(expected16, actual16)
        compare(expected32, actual32)
        assertEquals<Trie2>(actual16, actual32)
        assertEquals(actual16.hashCode(), actual32.hashCode())
        val full32 = JavaWritable(Int.MIN_VALUE, Int.MAX_VALUE)
        full32.setRange(0, 0x7f, -42, true)
        full32.setRange(0x10000, 0x1ffff, -65537, true)
        val frozen = full32.toTrie2_32()
        compare(frozen, Trie2_32.createFromSerialized(serialize(frozen)))
        assertNotEquals(actual32, Trie2_32.createFromSerialized(serialize(frozen)))
    }

    @Test fun realUpropsTriesMatchIcu() {
        val javaBytes = JavaBinary.getRequiredData("uprops.icu")
        JavaBinary.readHeader(javaBytes, 0x5550726f, null)
        val javaStart = javaBytes.position()
        val javaIndexes = JavaBinary.getInts(javaBytes, 16, 0)
        val commonBytes = ICUBinary.getRequiredData("uprops.icu")
        ICUBinary.readHeader(commonBytes, 0x5550726f, null)
        val commonStart = commonBytes.position
        val indexes = commonBytes.readInts(16)
        assertContentEquals(javaIndexes, indexes)
        compare(JavaTrie16.createFromSerialized(javaBytes), Trie2_16.createFromSerialized(commonBytes))
        javaBytes.position(javaStart + indexes[3] * 4)
        commonBytes.position = commonStart + indexes[3] * 4
        compare(JavaTrie16.createFromSerialized(javaBytes), Trie2_16.createFromSerialized(commonBytes))
    }
}

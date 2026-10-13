package io.github.heapy.icu4k.util

import com.ibm.icu.util.BytesTrie as JavaTrie
import com.ibm.icu.util.BytesTrieBuilder
import com.ibm.icu.util.StringTrieBuilder
import kotlin.random.Random
import kotlin.test.*

class BytesTrieIteratorOracleTest {
    private fun compare(j: JavaTrie.Iterator, k: BytesTrie.Iterator) {
        var previousJ: JavaTrie.Entry? = null
        var previousK: BytesTrie.Entry? = null
        while (j.hasNext()) {
            assertTrue(k.hasNext())
            val a = j.next(); val b = k.next()
            if (previousJ != null) { assertSame(previousJ, a); assertSame(previousK, b) }
            previousJ = a; previousK = b
            assertEquals(a.value, b.value)
            assertEquals(a.bytesLength(), b.bytesLength())
            val expected = ByteArray(a.bytesLength() + 2)
            val actual = ByteArray(b.bytesLength() + 2)
            a.copyBytesTo(expected, 1); b.copyBytesTo(actual, 1)
            assertContentEquals(expected, actual)
            for (index in 0 until a.bytesLength()) assertEquals(a.byteAt(index), b.byteAt(index))
            assertContentEquals(expected.copyOfRange(1, expected.size - 1), b.toByteArray())
            val snapshot = b.toByteArray()
            if (snapshot.isNotEmpty()) { snapshot[0] = (snapshot[0].toInt() xor 255).toByte(); assertEquals(a.byteAt(0), b.byteAt(0)) }
        }
        assertFalse(k.hasNext())
        assertFailsWith<NoSuchElementException> { j.next() }
        assertFailsWith<NoSuchElementException> { k.next() }
        assertFailsWith<UnsupportedOperationException> { k.remove() }
    }
    @Test fun iterationTruncationSubtriesAndResetMatchIcu() {
        val random = Random(496)
        for (option in StringTrieBuilder.Option.entries) repeat(10) {
            val entries = linkedMapOf<List<Byte>, Int>()
            entries[emptyList()] = -1
            repeat(300) { entries[List(random.nextInt(1, 80)) { random.nextInt(256).toByte() }] = random.nextInt() }
            // Long shared prefixes exercise iterator construction in the middle of a linear match.
            repeat(25) { entries[List(35) { 42.toByte() } + it.toByte()] = it }
            val builder = BytesTrieBuilder()
            for ((key, value) in entries) builder.add(key.toByteArray(), key.size, value)
            val buffer = builder.buildByteBuffer(option)
            val raw = ByteArray(buffer.remaining()).also { buffer.get(it) }
            val j = JavaTrie(raw, 0); val k = BytesTrie(raw, 0)
            for (limit in listOf(0, 1, 2, 15, 16, 17, 35, 80)) {
                val ji = j.iterator(limit); val ki = k.iterator(limit)
                compare(ji, ki)
                assertSame(ji, ji.reset()); assertSame(ki, ki.reset())
                compare(ji, ki)
                compare(JavaTrie.iterator(raw, 0, limit), BytesTrie.iterator(raw, 0, limit))
            }
            for (prefix in 1..35) {
                j.next(42); k.next(42)
                for (limit in listOf(0, 1, 5, 16, 40)) {
                    val ji = j.iterator(limit); val ki = k.iterator(limit)
                    compare(ji, ki); ji.reset(); ki.reset(); compare(ji, ki)
                }
            }
            j.next(256); k.next(256)
            compare(j.iterator(), k.iterator())
        }
    }
}

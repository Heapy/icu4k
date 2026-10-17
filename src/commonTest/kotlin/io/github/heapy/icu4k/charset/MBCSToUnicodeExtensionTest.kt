package io.github.heapy.icu4k.charset

import io.github.heapy.icu4k.util.BinaryBuffer
import kotlin.test.*

class MBCSToUnicodeExtensionTest {
    private fun extension(order: BinaryBuffer.ByteOrder): BinaryBuffer {
        val words = intArrayOf(6 shl 24, 0x101f0041, 0x20000007, 0x3000000a, 0x809fffff.toInt(), 0xf0b80000.toInt(), 0xff9f0000.toInt(),
            (2 shl 24) or 0x9f0061, 0x019f0062, 0xff00000d.toInt(), (2 shl 24) or 0x1f0063, 0x10b80000, 0x209f0064,
            (1 shl 24) or 0x9f0065, 0x229f0066)
        val b = BinaryBuffer(ByteArray(256), order)
        b.writeInt(32); b.writeInt(128); b.writeInt(words.size); b.writeInt(224); b.writeInt(2)
        b.position = 128
        for (word in words) b.writeInt(word)
        b.position = 224; b.writeShort(65); b.writeShort(0x301); b.position = 0
        return b
    }
    @Test fun independentlyGeneratedSourceMatchesAcrossPrefixesAndSisoStates() {
        val alphabet = intArrayOf(0, 1, 0x10, 0x20, 0x22, 0x30, 0x7f, 0x80, 0xf0, 0xff)
        for (order in BinaryBuffer.ByteOrder.entries) for (row in mbcsExtensionGoldenRows) {
            val fields = row.split('|')
            val siso = fields[0].toInt(); val flush = fields[2].toBoolean(); val split = fields[3].toInt()
            val ext = extension(order)
            var hash = 1; var calls = 0; var count = 1
            for (len in 0..3) {
                repeat(count) { n ->
                    var v = n
                    val input = ByteArray(len) { alphabet[v % alphabet.size].toByte().also { v /= alphabet.size } }
                    val preLength = minOf(split, len)
                    val pre = ByteArray(preLength + 4)
                    input.copyInto(pre, 2, 0, preLength)
                    val source = BinaryBuffer(ByteArray(len - preLength + 4))
                    source.position = 2
                    for (i in preLength until len) source.writeByte(input[i].toInt())
                    source.limit = source.position; source.position = 2
                    val out = intArrayOf(0x12345678)
                    val result = MBCSToUnicodeExtension.match(ext, siso, pre, 2, preLength, source, out, flush)
                    hash = 31 * hash + result; hash = 31 * hash + out[0]; hash = 31 * hash + source.position
                    assertEquals(0, ext.position)
                    calls++
                }
                count *= alphabet.size
            }
            assertEquals(fields[4].toInt(), calls)
            assertEquals(fields[5].toInt(), hash, "$order / $row")
        }
    }
    @Test fun stringAndScalarResultsAndMissingData() {
        for (order in BinaryBuffer.ByteOrder.entries) {
            val ext = extension(order)
            assertContentEquals(charArrayOf('A', 0x301.toChar()), MBCSToUnicodeExtension.characters(ext, 0x380000))
            assertContentEquals(charArrayOf(0xdbff.toChar(), 0xdfff.toChar()), MBCSToUnicodeExtension.characters(ext, 0x2fffff))
            assertContentEquals(charArrayOf(0xffff.toChar()), MBCSToUnicodeExtension.characters(ext, 0x1fffff))
            assertEquals(0, ext.position)
        }
        val value = intArrayOf(123)
        assertEquals(0, MBCSToUnicodeExtension.match(null, -1, byteArrayOf(), 0, 0, null, value, true))
        assertEquals(123, value[0])
        assertEquals(0, MBCSToUnicodeExtension.match(BinaryBuffer(ByteArray(32)), -1, byteArrayOf(), 0, 0, null, value, false))
    }
    @Test fun sectionSearchCoversAllByteValuesAndBothSearchPaths() {
        for (bytes in listOf((0..255).toList(), (0..255 step 3).toList(), listOf(1), listOf(0, 255), listOf(1, 3, 128, 255))) {
            val words = bytes.map { (it shl 24) or (it + 1) }
            for (byte in 0..255) assertEquals(if (byte in bytes) byte + 1 else 0,
                MBCSToUnicodeExtension.find(words.size, byte) { words[it] }, "$bytes / $byte")
        }
    }
}

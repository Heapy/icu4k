package io.github.heapy.icu4k.text

import kotlin.test.*

class CharsetSbcsRecognizerTest {
    @Test fun sourceGoldenMatchesAcrossEveryLanguageModel() {
        val recognizers = CharsetSbcsTables.recognizers()
        assertEquals(15, recognizers.size)
        assertEquals(27, recognizers.sumOf { it.models.size })
        for (row in charsetSbcsGoldenRows) {
            val parts = row.split('|')
            val bytes = parts[0].chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            val input = CharsetDetectionInput(byteArrayOf(0), filtered = bytes, filteredLength = parts[1].toInt(), c1Bytes = parts[2].toBoolean())
            val expected = parts[3].split(',')
            for ((i, recognizer) in recognizers.withIndex()) {
                val result = recognizer.match(input)
                assertEquals(expected[i], result?.let { "${it.name}:${it.confidence}:${it.language}" } ?: "-", "${recognizer.getName()} $row")
            }
        }
    }
    @Test fun c1FlagUsesOnlyTheFilteredPrefix() {
        assertFalse(CharsetDetectionInput(byteArrayOf(0x80.toByte()), filtered = byteArrayOf(0x20)).c1Bytes)
        assertFalse(CharsetDetectionInput(byteArrayOf(0), filtered = byteArrayOf(0x20, 0x80.toByte()), filteredLength = 1).c1Bytes)
        for (b in 0..255) assertEquals(b in 0x80..0x9f, CharsetDetectionInput(byteArrayOf(b.toByte())).c1Bytes)
    }
}

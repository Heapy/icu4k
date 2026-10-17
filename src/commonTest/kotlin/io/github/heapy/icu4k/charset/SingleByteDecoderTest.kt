package io.github.heapy.icu4k.charset

import io.github.heapy.icu4k.util.BinaryBuffer
import kotlin.test.*

class SingleByteDecoderTest {
    private fun compare(row: String, nio: Boolean = false) {
        val f = row.split('|')
        val decoder = CharsetSingleByteDecoder(f[0].toBoolean())
        val mode = f[4].toInt()
        when (mode) {
            1 -> { decoder.onMalformedInput(CharsetCodingErrorAction.REPLACE); decoder.onUnmappableCharacter(CharsetCodingErrorAction.REPLACE) }
            2 -> { decoder.onMalformedInput(CharsetCodingErrorAction.IGNORE); decoder.onUnmappableCharacter(CharsetCodingErrorAction.IGNORE) }
            4 -> { decoder.setToUCallback(CharsetCoderResult.malformedForLength(1), CharsetDecoderCallbacks.ESCAPE, "D"); decoder.setToUCallback(CharsetCoderResult.unmappableForLength(1), CharsetDecoderCallbacks.ESCAPE, "D") }
        }
        assertEquals(row, decoderRuntimeRun(f[0], decoder, f[1], f[2].toInt(), f[3].toInt(), mode, seed = f[5].toInt(), nio = nio, backend = f[5].toInt()), row)
    }
    private fun goldens(part: Int) { for ((index, row) in singleByteDecoderGoldenRows.withIndex()) if (index % 4 == part) compare(row) }
    @Test fun sourceSingleByteStreamingGoldensOne() = goldens(0)
    @Test fun sourceSingleByteStreamingGoldensTwo() = goldens(1)
    @Test fun sourceSingleByteStreamingGoldensThree() = goldens(2)
    @Test fun sourceSingleByteStreamingGoldensFour() = goldens(3)
    @Test fun sourceSingleByteNioGoldens() { for (row in singleByteDecoderNioGoldenRows) compare(row, nio = true) }
    @Test fun exposedArrayAndOpaqueCursorViewsSelectExpectedPaths() {
        val bytes = BinaryBuffer(byteArrayOf(65, 0x80.toByte()))
        assertTrue(bytes.hasArray)
        assertTrue(bytes.slice().hasArray)
        assertFalse(bytes.asReadOnlyBuffer().hasArray)
        val chars = CharsetCharBuffer(CharArray(3), arrayAccessible = false)
        assertFalse(chars.hasArray)
        assertFalse(chars.slice().hasArray)
        val offsets = CharsetIntBuffer(IntArray(3))
        val decoder = CharsetSingleByteDecoder()
        assertEquals("MALFORMED[1]", decoder.decode(bytes, chars, offsets, true).toString())
        assertEquals(2, bytes.position)
        assertEquals(1, chars.position)
        assertEquals('A', chars.array[0])
        assertEquals(1, offsets.position)
        assertEquals(0, offsets.array[0])
    }
}

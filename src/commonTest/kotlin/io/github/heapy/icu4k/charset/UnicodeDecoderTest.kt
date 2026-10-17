package io.github.heapy.icu4k.charset

import kotlin.test.*

class UnicodeDecoderTest {
    private fun goldens(part: Int) {
        for ((index, row) in unicodeDecoderGoldenRows.withIndex()) if (index % 4 == part) compare(row)
    }
    private fun compare(row: String, nio: Boolean = false, local: Boolean = false) {
        val fields = row.split('|')
        val name = fields[0]
        val version = if (name.endsWith(",version=1")) 1 else 0
        val kind = name.substringBefore(',')
        val decoder: CharsetDecoderRuntime = when (kind) {
            "UTF-16" -> CharsetUtf16Decoder(version = version)
            "UTF-16BE" -> CharsetUtf16Decoder(true, version)
            "UTF-16LE" -> CharsetUtf16Decoder(false, version)
            "UTF-32" -> CharsetUtf32Decoder()
            "UTF-32BE" -> CharsetUtf32Decoder(true)
            "UTF-32LE" -> CharsetUtf32Decoder(false)
            else -> error(kind)
        }
        val mode = fields[4].toInt()
        when (mode) {
            1 -> { decoder.onMalformedInput(CharsetCodingErrorAction.REPLACE); decoder.onUnmappableCharacter(CharsetCodingErrorAction.REPLACE) }
            2 -> { decoder.onMalformedInput(CharsetCodingErrorAction.IGNORE); decoder.onUnmappableCharacter(CharsetCodingErrorAction.IGNORE) }
            4 -> { decoder.setToUCallback(CharsetCoderResult.malformedForLength(1), CharsetDecoderCallbacks.ESCAPE, "D"); decoder.setToUCallback(CharsetCoderResult.unmappableForLength(1), CharsetDecoderCallbacks.ESCAPE, "D") }
        }
        val result = decoderRuntimeRun(name, decoder, fields[1], fields[2].toInt(), fields[3].toInt(), mode, nio = nio, local = local) {
            when (decoder) {
                is CharsetUtf16Decoder -> listOf(if (decoder.isBOMReadYet) 1 else 0, decoder.actualEndianXOR, decoder.actualBOM?.contentHashCode() ?: 0)
                is CharsetUtf32Decoder -> listOf(if (decoder.isBOMReadYet) 1 else 0, decoder.actualEndianXOR, decoder.actualBOM?.contentHashCode() ?: 0)
                else -> error("decoder")
            }
        }
        assertEquals(row, result, row)
    }
    @Test fun sourceBomSurrogateAndErrorGoldensOne() = goldens(0)
    @Test fun sourceBomSurrogateAndErrorGoldensTwo() = goldens(1)
    @Test fun sourceBomSurrogateAndErrorGoldensThree() = goldens(2)
    @Test fun sourceBomSurrogateAndErrorGoldensFour() = goldens(3)
    @Test fun sourceNioPendingAndFlushGoldens() { for (row in unicodeDecoderNioGoldenRows) compare(row, nio = true) }
    @Test fun sourceIndependentInputChunksGoldens() { for (row in unicodeDecoderLocalGoldenRows) compare(row, local = true) }
}

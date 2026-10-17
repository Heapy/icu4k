package io.github.heapy.icu4k.charset

import kotlin.test.*
import io.github.heapy.icu4k.util.BinaryBuffer

internal val mbcsDecoderCases = listOf("", "00", "41427f80ff", "0e41420f431b", "81308130", "90308130", "ff00ff20", "a1a2fefe", "81ff4142")
internal fun mbcsDecoderConfigure(data: MBCSConverterData, mode: Int, seed: Int): CharsetMBCSDecoder = CharsetMBCSDecoder(data).also { decoder ->
    when (mode) {
        1 -> { decoder.onMalformedInput(CharsetCodingErrorAction.REPLACE); decoder.onUnmappableCharacter(CharsetCodingErrorAction.REPLACE) }
        2 -> { decoder.onMalformedInput(CharsetCodingErrorAction.IGNORE); decoder.onUnmappableCharacter(CharsetCodingErrorAction.IGNORE) }
        4 -> { decoder.setToUCallback(CharsetCoderResult.malformedForLength(1), CharsetDecoderCallbacks.ESCAPE, "D"); decoder.setToUCallback(CharsetCoderResult.unmappableForLength(1), CharsetDecoderCallbacks.ESCAPE, "D") }
    }
    if (seed == 3) { decoder.charErrorBufferArray[0] = 'Q'; decoder.charErrorBufferArray[1] = 0xdc00.toChar(); decoder.charErrorBufferLength = 2 }
}

class CharsetMBCSDecoderTest {
    private fun goldens(group: Int) {
        for (row in mbcsDecoderGoldenRows) {
            val f = row.split('|')
            if (f[1].toInt() != group) continue
            val name = f[0]; val data = MBCSConverterData.load(name)
            var hash = 1; var count = 0
            for (hex in mbcsDecoderCases) for (split in listOf(1, 3)) for (cap in listOf(1, 3)) for (mode in listOf(0, 1, 2, 4)) {
                if (group > 0 && mode == 2) continue
                val seed = if (group == 3) 3 else 0
                val result = decoderRuntimeRun(name, mbcsDecoderConfigure(data, mode, seed), hex, split, cap, mode, seed, nio = group == 1, local = group == 2)
                hash = 31 * hash + result.hashCode(); count++
            }
            if (group == 0) {
                val hex = (0..255).joinToString("") { it.toString(16).padStart(2, '0') }
                hash = 31 * hash + decoderRuntimeRun(name, mbcsDecoderConfigure(data, 0, 0), hex, 256, 512, 0).hashCode(); count++
            }
            assertEquals(f[2].toInt(), count)
            assertEquals(f[3].toInt(), hash, row)
        }
    }
    private fun extraGoldens(group: Int) {
        for (row in mbcsDecoderGoldenRows) {
            val f = row.split('|')
            if (f[1].toInt() != group) continue
            val name = f[0]; val data = MBCSConverterData.load(name)
            var hash = 1; var count = 0
            if (group == 7) {
                val bytes = (0..255).map { it.toString(16).padStart(2, '0') }
                val hex = buildString(262144) { for (a in bytes) for (b in bytes) { append(a); append(b) } }
                hash = 31 * hash + decoderRuntimeRun(name, mbcsDecoderConfigure(data, 1, 0), hex, 131072, 262144, 1).hashCode(); count++
            } else if (group == 6) {
                val inputs = (mbcsExtraCases(data, 4) + mbcsExtraCases(data, 5) + mbcsDecoderCases + (0..255).map { it.toString(16).padStart(2, '0') }).distinct().sorted()
                val decoder = CharsetMBCSDecoder(data)
                for (hex in inputs) for (backend in 0..4) for (fallback in listOf(false, true)) {
                    val raw = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                    val padded = ByteArray(raw.size + 4); raw.copyInto(padded, 2)
                    val source = when (backend) {
                        0 -> BinaryBuffer(raw)
                        1, 3 -> BinaryBuffer(padded, arrayOffset = 2, capacity = raw.size, isReadOnly = backend == 3)
                        else -> BinaryBuffer(padded, isReadOnly = backend == 4).apply { position = 2; limit = 2 + raw.size }
                    }
                    val result = try { decoder.simpleGetNextUChar(source, fallback).toString() } catch (_: IndexOutOfBoundsException) { "THROW:bounds" }
                    val r = "$hex|$backend|$fallback|$result|${source.position}|${source.limit}"
                    hash = 31 * hash + r.hashCode(); count++
                }
            } else for (hex in mbcsExtraCases(data, group)) for (mode in listOf(0, 1, 4)) {
                val result = decoderRuntimeRun(name, mbcsDecoderConfigure(data, mode, 0), hex, if (mode == 4) 3 else 1, if (mode == 4) 3 else 1, mode, local = mode == 1)
                hash = 31 * hash + result.hashCode(); count++
            }
            assertEquals(f[2].toInt(), count, row)
            assertEquals(f[3].toInt(), hash, row)
        }
    }
    @Test fun everyBytePairThroughEveryConverterMatchesSource() = extraGoldens(7)
    @Test fun allExtensionMappingStreamsMatchSource() = extraGoldens(4)
    @Test fun gb18030RangeBoundariesMatchSource() = extraGoldens(5)
    @Test fun simpleCharacterLookupMatchesSourceIncludingSlicedBuffers() = extraGoldens(6)
    @Test fun allConvertersStreamingSourceGoldens() = goldens(0)
    @Test fun allConvertersNioSourceGoldens() = goldens(1)
    @Test fun allConvertersIndependentChunkSourceGoldens() = goldens(2)
    @Test fun allConvertersQueuedOutputSourceGoldens() = goldens(3)
}

internal fun mbcsExtraCases(data: MBCSConverterData, group: Int): List<String> {
    val inputs = mutableSetOf<String>()
    if (group == 4) {
        val ext = data.sharedData.mbcs.extIndexes
        if (ext != null && ext.slice(8, 4).readInt() > 0) {
            val words = ext.slice(ext.slice(4, 4).readInt())
            fun word(index: Int) = words.slice(index * 4, 4).readInt()
            fun walk(index: Int, prefix: String) {
                if (prefix.isNotEmpty()) { inputs.add(prefix); inputs.add(prefix + "55") }
                for (i in 1..(word(index) ushr 24)) {
                    val entry = word(index + i); val value = entry and 0xffffff
                    if (value == 0) continue
                    val next = prefix + (entry ushr 24).toString(16).padStart(2, '0')
                    if (value < 0x1f0000) walk(value, next) else { inputs.add(next); inputs.add(next + "55") }
                }
            }
            walk(0, "")
        }
    } else if ("gb18030" in data.name) {
        for (packed in listOf(0x90308130L,0xe3329a35L,0x82358f33L,0x8336c738L,0x8130d330L,0x8135f436L,0x8135f438L,0x8136a531L,0x8336d030L,0x84308534L,0x8137a839L,0x8138fd38L,0x84309c38L,0x84318537L,0x8231d438L,0x8232af32L,0x8230a633L,0x8230f237L,0x8234a131L,0x8234e733L,0x8232c937L,0x8232f837L,0x8233e838L,0x82349638L,0x8233a339L,0x8233c931L,0x8431a234L,0x8431a439L))
            for (delta in -1..1) inputs.add((packed + delta).toString(16).padStart(8, '0'))
    }
    return inputs.sorted()
}

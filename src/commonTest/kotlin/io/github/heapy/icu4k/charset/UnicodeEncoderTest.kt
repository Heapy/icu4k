package io.github.heapy.icu4k.charset

import io.github.heapy.icu4k.util.BinaryBuffer
import kotlin.test.*

internal val unicodeEncoderNames = listOf("UTF-8", "CESU-8", "UTF-16", "UTF-16BE", "UTF-16LE", "UTF-16,version=1", "UTF-16BE,version=1", "UTF-16LE,version=1", "UTF-16BE,version=2", "UTF-32", "UTF-32BE", "UTF-32LE")
internal val unicodeEncoderCases = listOf("", "0000", "007f008007ff0800", "00410042", "d7ffd800", "dbffdfff", "d800dc00", "d83dde00", "d8000041", "dc000041", "d800d800dc00", "db40dc00", "fefffffe", "ffff")
internal fun unicodeEncoder(name: String): CharsetEncoderRuntime {
    if (name == "UTF-8" || name == "CESU-8") return CharsetUtf8Encoder(name == "CESU-8")
    val base = name.substringBefore(',')
    val endian = if (base.endsWith("BE")) true else if (base.endsWith("LE")) false else null
    return CharsetUnicodeEncoder(base.startsWith("UTF-32"), endian, name.substringAfter("version=", "0").toInt())
}
internal fun unicodeEncoderPrivateState(encoder: CharsetEncoderRuntime): List<Int> = when (encoder) {
    is CharsetUtf8Encoder -> listOf(encoder.sourceIndex, encoder.targetIndex)
    is CharsetUnicodeEncoder -> listOf(encoder.temp.contentHashCode())
    else -> error("Unicode encoder required")
}
internal fun unicodeEncoderRun(name: String, hex: String, split: Int, cap: Int, mode: Int, seed: Int = 0, backend: Int = 0, nio: Boolean = false, local: Boolean = false): String {
    val encoder = unicodeEncoder(name).also { encoderApplyConfiguration(it, mode, seed) }
    return encoderRuntimeRun(name, encoder, hex, split, cap, mode, seed, backend, nio, local) { unicodeEncoderPrivateState(encoder) }
}
class UnicodeEncoderTest {
    private fun goldenGroup(group: Int) {
        for (row in unicodeEncoderGoldenRows) {
            val f = row.split('|'); if (f[1].toInt() != group) continue
            val name = f[0]; var hash = 1; var count = 0
            fun add(hex: String, split: Int, cap: Int, mode: Int, seed: Int = 0, backend: Int = 0, nio: Boolean = false, local: Boolean = false) {
                hash = 31 * hash + unicodeEncoderRun(name, hex, split, cap, mode, seed, backend, nio, local).hashCode(); count++
            }
            when (group) {
                0 -> for (hex in unicodeEncoderCases) for (split in listOf(1, 3)) for (cap in listOf(1, 3, 4)) for (mode in listOf(0, 1, 2, 4, 6)) for (backend in listOf(0, 1, 2, 6)) add(hex, split, cap, mode, backend = backend)
                1, 2 -> for (hex in listOf("", "0041d800dc00", "d8000041", "dc00")) for (split in listOf(1, 3)) for (cap in listOf(1, 4)) for (mode in listOf(0, 1, 4)) add(hex, split, cap, mode, nio = group == 1, local = group == 2)
                3 -> for (hex in listOf("", "00410042", "d800dc00")) for (seed in 1..5) for (cap in listOf(1, 4)) for (mode in listOf(0, 1, 4, 12)) add(hex, 1, cap, mode, seed = seed)
                4 -> for (hex in listOf("0041dfff0042", "d8000041")) for (backend in listOf(3, 4, 5, 7)) for (mode in listOf(3, 5, 7, 8, 9, 10, 13, 14, 15, 16, 17, 18, 19)) add(hex, 1, 3, mode, backend = backend)
            }
            assertEquals(f[2].toInt(), count, row); assertEquals(f[3].toInt(), hash, row)
        }
    }
    @Test fun unicodeStreamingGoldens() = goldenGroup(0)
    @Test fun unicodeNioGoldens() = goldenGroup(1)
    @Test fun unicodeIndependentChunkGoldens() = goldenGroup(2)
    @Test fun unicodePendingAndReplayGoldens() = goldenGroup(3)
    @Test fun unicodeContextAndBufferGoldens() = goldenGroup(4)
    @Test fun allUtf16UnitsThroughEveryUnicodeEncoderMatchSource() {
        val hex = (0..65535).joinToString("") { it.toString(16).padStart(4, '0') }
        for (row in unicodeEncoderBulkGoldenRows) {
            val f = row.split('|')
            assertEquals(f[2].toInt(), unicodeEncoderRun(f[0], hex, 65536, 262144, f[1].toInt()).hashCode(), row)
        }
    }
    @Test fun everyUnicodeScalarMatchesSourceInArrayAndGenericLoops() {
        for (row in unicodeEncoderScalarGoldenRows) {
            val f = row.split('|'); val encoder = unicodeEncoder(f[0]); val generic = f[1].toBoolean()
            val units = CharArray(2)
            val source = CharsetCharBuffer(units, isReadOnly = generic)
            val target = BinaryBuffer(ByteArray(8))
            var hash = 1; var count = 0
            fun add(value: Int) { hash = 31 * hash + value }
            for (cp in 0..0x10ffff) {
                if (cp in 0xd800..0xdfff) continue
                val length = if (cp < 0x10000) 1 else 2
                if (length == 1) units[0] = cp.toChar() else {
                    units[0] = ((cp ushr 10) + 0xd7c0).toChar(); units[1] = ((cp and 1023) + 0xdc00).toChar()
                }
                source.position = 0; source.limit = length; target.position = 0
                add(encoder.encodeLoop(source, target, null, false).toString().hashCode())
                add(source.position); add(target.position)
                repeat(target.position) { add(target.getByte(it).toInt()) }; add(encoder.fromUChar32); count++
            }
            for (v in listOf(encoder.errorBufferLength, encoder.fromUnicodeStatus, encoder.fromUChar32, encoder.preFromUFirstCP, encoder.preFromUBegin, encoder.preFromULength, encoder.invalidUCharLength)) add(v)
            add(encoder.errorBuffer.contentHashCode())
            for (v in unicodeEncoderPrivateState(encoder)) add(v)
            assertEquals(f[2].toInt(), count, row); assertEquals(f[3].toInt(), hash, row)
        }
    }
    @Test fun opaqueByteViewsRetainStorageAndCursorRules() {
        val source = BinaryBuffer(byteArrayOf(1, 2, 3, 4))
        source.position = 1; source.limit = 3
        val opaque = source.asOpaqueBuffer()
        assertFalse(opaque.hasArray); assertTrue(source.hasArray)
        assertFalse(opaque.slice().hasArray); assertFalse(opaque.slice(1, 1).hasArray); assertFalse(opaque.duplicate().hasArray)
        opaque.putCharsetByte(9); assertEquals(9, source.bytes[1].toInt()); assertEquals(1, source.position); assertEquals(2, opaque.position)
    }
}

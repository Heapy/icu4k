package io.github.heapy.icu4k.charset

import io.github.heapy.icu4k.util.BinaryBuffer
import kotlin.test.*

internal val encoderNonStringContext = object {
    override fun hashCode() = 17
    override fun equals(other: Any?) = other is String && other == "D"
}
internal fun encoderConfigure(latin: Boolean, mode: Int, seed: Int): CharsetSingleByteEncoder =
    CharsetSingleByteEncoder(latin).also { encoderApplyConfiguration(it, mode, seed) }

internal fun encoderApplyConfiguration(encoder: CharsetEncoderRuntime, mode: Int, seed: Int) {
    if (mode in listOf(1, 8, 9)) {
        encoder.onMalformedInput(CharsetCodingErrorAction.REPLACE); encoder.onUnmappableCharacter(CharsetCodingErrorAction.REPLACE)
        if (mode == 8) encoder.replaceWith(byteArrayOf(42))
        if (mode == 9) encoder.replaceWith(byteArrayOf(26))
    } else if (mode == 2) {
        encoder.onMalformedInput(CharsetCodingErrorAction.IGNORE); encoder.onUnmappableCharacter(CharsetCodingErrorAction.IGNORE)
    } else if (mode >= 3) {
        val context: Any? = when (mode) {
            4 -> "D"; 5 -> "X"; 6 -> "C"; 7 -> "J"; 10, 16 -> "i"; 11, 12 -> "replay"; 13 -> "U"; 14 -> "S"
            15, 17, 18 -> encoderNonStringContext; 19 -> "other"; else -> null
        }
        var cb = when { mode == 10 || mode == 18 -> CharsetEncoderCallbacks.SKIP; mode >= 16 -> CharsetEncoderCallbacks.SUBSTITUTE; else -> CharsetEncoderCallbacks.ESCAPE }
        if (mode == 11 || mode == 12) {
            var count = 0
            cb = CharsetEncoderCallback { e, _, _, _, _, _, _, _, error ->
                if (count++ == 0) { e.preFromUArray[0] = 'R'; e.preFromULength = -1; if (mode == 11) CharsetCoderResult.UNDERFLOW else CharsetCoderResult.OVERFLOW } else error
            }
        }
        encoder.setFromUCallback(CharsetCoderResult.malformedForLength(1), cb, context)
        encoder.setFromUCallback(CharsetCoderResult.unmappableForLength(1), cb, context)
    }
    when (seed) {
        1, 2 -> { encoder.preFromUArray[0] = if (seed == 1) 'A' else 0xd800.toChar(); encoder.preFromUArray[1] = if (seed == 1) 'B' else 0xdc00.toChar(); encoder.preFromULength = -2 }
        3 -> { encoder.errorBuffer[0] = 1; encoder.errorBuffer[1] = 2; encoder.errorBuffer[2] = -1; encoder.errorBufferLength = 3 }
        4 -> encoder.fromUChar32 = 0xd800
        5 -> { encoder.preFromUFirstCP = 0x10000; encoder.preFromULength = 1; encoder.preFromUArray[0] = 'X' }
    }
}
internal fun encoderGoldenRun(latin: Boolean, hex: String, split: Int, capacity: Int, mode: Int, seed: Int, backend: Int, nio: Boolean, local: Boolean): String =
    encoderRuntimeRun(latin.toString(), encoderConfigure(latin, mode, seed), hex, split, capacity, mode, seed, backend, nio, local)

internal fun encoderRuntimeRun(label: String, encoder: CharsetEncoderRuntime, hex: String, split: Int, capacity: Int, mode: Int, seed: Int, backend: Int, nio: Boolean, local: Boolean, privateState: () -> List<Int> = { emptyList() }): String {
    val raw = hex.chunked(4).map { it.toInt(16).toChar() }.toCharArray()
    val sourceOffset = if (backend in listOf(1, 2, 7) || local) 2 else 0
    val targetOffset = if (backend in listOf(1, 2, 7)) 2 else 0
    val padded = CharArray(raw.size + 4); raw.copyInto(padded, sourceOffset)
    val readOnly = backend in listOf(2, 3, 5)
    val accessible = backend != 7
    var end = minOf(split, raw.size)
    var source = CharsetCharBuffer(padded, sourceOffset, if (local) end else raw.size, readOnly, accessible).apply { limit = end }
    var hash = 1; var count = 0; var flushing = false
    fun add(value: Int) { hash = 31 * hash + value }
    while (true) {
        val cap = if (count % 5 == 0) 0 else capacity
        val target = BinaryBuffer(ByteArray(cap + 4), arrayOffset = targetOffset, capacity = cap, isReadOnly = backend == 4).let { if (backend in listOf(5, 6)) it.asOpaqueBuffer() else it }
        val offsets = CharsetIntBuffer(IntArray(cap))
        var result: CharsetCoderResult? = null; var failed = false
        val text = try {
            (if (nio) if (flushing) encoder.flush(target) else encoder.encodeNioLoop(source, target) else encoder.encode(source, target, offsets, end == raw.size)).also { result = it }.toString()
        } catch (_: CharsetBufferOverflowException) { failed = true; "THROW:overflow" }
        catch (_: IllegalArgumentException) { failed = true; "THROW:argument" }
        catch (_: IndexOutOfBoundsException) { failed = true; "THROW:bounds" }
        catch (_: ClassCastException) { failed = true; "THROW:cast" }
        catch (_: CharsetReadOnlyBufferException) { failed = true; "THROW:readonly" }
        catch (_: IllegalStateException) { failed = true; "THROW:state" }
        add(text.hashCode()); add(source.position); add(target.position); add(offsets.position)
        repeat(target.position) { add(target.getByte(it).toInt()) }; repeat(offsets.position) { add(offsets.array[it]) }
        for (v in listOf(encoder.errorBufferLength, encoder.fromUnicodeStatus, encoder.fromUChar32, encoder.preFromUFirstCP, encoder.preFromUBegin, encoder.preFromULength, encoder.invalidUCharLength)) add(v)
        add(if (encoder.useSubChar1) 1 else 0); add(if (encoder.useFallback) 1 else 0)
        add(encoder.errorBuffer.contentHashCode()); add(encoder.preFromUArray.contentHashCode()); add(encoder.invalidUCharBuffer.contentHashCode())
        add(encoder.fromUContext?.hashCode() ?: 0); add(encoder.replacement.contentHashCode()); add(encoder.malformedInputAction.toString().hashCode()); add(encoder.unmappableCharacterAction.toString().hashCode())
        for (value in privateState()) add(value)
        check(++count <= 1000) { "Encoder did not terminate: $label/$hex/$mode/$seed/$backend" }
        if (failed) break
        if (nio) {
            if (result!!.isUnderflow()) {
                if (flushing) break
                if (end == raw.size) flushing = true else { end = minOf(end + split, raw.size); source.limit = end }
            }
            continue
        }
        if (result!!.isUnderflow() && source.position == source.limit) {
            if (end == raw.size) break
            val start = end; end = minOf(end + split, raw.size)
            if (local) source = CharsetCharBuffer(padded, sourceOffset + start, end - start) else source.limit = end
        }
    }
    return "$label|$hex|$split|$capacity|$mode|$seed|$backend|$nio|$local|$count|$hash"
}
class CharsetEncoderRuntimeTest {
    private fun compare(row: String) {
        val f = row.split('|')
        assertEquals(row, encoderGoldenRun(f[0].toBoolean(), f[1], f[2].toInt(), f[3].toInt(), f[4].toInt(), f[5].toInt(), f[6].toInt(), f[7].toBoolean(), f[8].toBoolean()), row)
    }
    @Test fun singleByteCoreGoldensOne() { for ((i, row) in singleByteEncoderGoldenRows.withIndex()) if (i % 2 == 0) compare(row) }
    @Test fun singleByteCoreGoldensTwo() { for ((i, row) in singleByteEncoderGoldenRows.withIndex()) if (i % 2 == 1) compare(row) }
    @Test fun nioReplayEscapeAndContextGoldensOne() { for ((i, row) in singleByteEncoderSpecialGoldenRows.withIndex()) if (i % 2 == 0) compare(row) }
    @Test fun nioReplayEscapeAndContextGoldensTwo() { for ((i, row) in singleByteEncoderSpecialGoldenRows.withIndex()) if (i % 2 == 1) compare(row) }
    @Test fun allUtf16UnitsThroughSingleByteCodecsMatchSource() {
        val hex = (0..65535).joinToString("") { it.toString(16).padStart(4, '0') }
        for (row in singleByteEncoderBulkGoldenRows) {
            val f = row.split('|')
            assertEquals(f[2].toInt(), encoderGoldenRun(f[0].toBoolean(), hex, 65536, 131072, f[1].toInt(), 0, 0, false, false).hashCode(), row)
        }
    }
    @Test fun replacementCopiesAndResetPreserveSourceState() {
        val e = CharsetSingleByteEncoder()
        val replacement = byteArrayOf(42); e.replaceWith(replacement); replacement[0] = 0
        assertEquals(42, e.replacement[0].toInt()); e.replacement[0] = 0; assertEquals(42, e.replacement[0].toInt())
        assertFailsWith<IllegalArgumentException> { e.replaceWith(byteArrayOf()) }
        assertFailsWith<IllegalArgumentException> { e.replaceWith(byteArrayOf(1, 2)) }
        assertTrue(e.isLegalReplacement(null)); assertTrue(e.isLegalReplacement(byteArrayOf()))
        e.setFallbackUsed(true); e.useSubChar1 = true; e.fromUContext = "i"; e.invalidUCharLength = 2; e.errorBuffer[0] = 7
        e.reset()
        assertTrue(e.isFallbackUsed()); assertTrue(e.useSubChar1); assertEquals("i", e.fromUContext)
        assertEquals(2, e.invalidUCharLength); assertEquals(7, e.errorBuffer[0].toInt()); assertEquals(-1, e.preFromUFirstCP); assertEquals(1, e.fromUnicodeStatus)
        e.onMalformedInput(CharsetCodingErrorAction.REPLACE); assertEquals(CharsetCodingErrorAction.REPLACE, e.malformedInputAction)
    }
}

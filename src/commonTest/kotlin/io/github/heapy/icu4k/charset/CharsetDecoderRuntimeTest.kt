package io.github.heapy.icu4k.charset

import io.github.heapy.icu4k.util.BinaryBuffer
import kotlin.test.*

class CharsetDecoderRuntimeTest {
    private fun goldens(part: Int) {
        for ((index, row) in charsetDecoderGoldenRows.withIndex()) if (index % 4 == part) {
            val fields = row.split('|')
            val actual = decoderGoldenRun(fields[0].toBoolean(), fields[1], fields[2].toInt(), fields[3].toInt(), fields[4].toInt(), fields[5].toInt())
            assertEquals(row, actual, row)
        }
    }
    @Test fun streamingSourceGoldensOne() = goldens(0)
    @Test fun streamingSourceGoldensTwo() = goldens(1)
    @Test fun streamingSourceGoldensThree() = goldens(2)
    @Test fun streamingSourceGoldensFour() = goldens(3)
    @Test fun nioAdapterPendingInputAndFlushSourceGoldens() {
        for (row in charsetDecoderNioGoldenRows) {
            val f = row.split('|')
            assertEquals(row, decoderGoldenRun(f[0].toBoolean(), f[1], f[2].toInt(), f[3].toInt(), f[4].toInt(), f[5].toInt(), nio = true), row)
        }
    }
    @Test fun fallbackReplacementAndResultContracts() {
        val decoder = CharsetUtf8Decoder()
        assertTrue(decoder.isFallbackUsed())
        assertTrue(CharsetDecoderRuntime.isToUUseFallback(false))
        assertTrue(CharsetDecoderRuntime.isToUUseFallback(true))
        assertFailsWith<IllegalArgumentException> { decoder.replaceWith("") }
        assertFailsWith<IllegalArgumentException> { decoder.replaceWith("ab") }
        decoder.onMalformedInput(CharsetCodingErrorAction.REPLACE)
        assertEquals(CharsetCodingErrorAction.IGNORE, decoder.malformedInputAction)
        val nonStringContext = object {
            override fun equals(other: Any?): Boolean = other == "D"
            override fun hashCode(): Int = 68
        }
        decoder.setToUCallback(CharsetCoderResult.malformedForLength(1), CharsetDecoderCallbacks.ESCAPE, nonStringContext)
        val output = CharsetCharBuffer(CharArray(16))
        assertEquals("UNDERFLOW", decoder.decode(BinaryBuffer(byteArrayOf(0xff.toByte())), output, null, true).toString())
        assertEquals("%XFF", output.array.concatToString(0, output.position))
        assertFalse(CharsetCoderResult.UNDERFLOW.isError())
        assertFalse(CharsetCoderResult.OVERFLOW.isError())
        assertFailsWith<UnsupportedOperationException> { CharsetCoderResult.UNDERFLOW.length() }
        assertFailsWith<IllegalArgumentException> { CharsetCoderResult.malformedForLength(0) }
        assertFailsWith<IllegalArgumentException> { CharsetCoderResult.unmappableForLength(-1) }
        decoder.preToULength = -3; decoder.toULength = 4
        assertEquals(3, decoder.toUCountPending())
        decoder.preToULength = 2; assertEquals(2, decoder.toUCountPending())
        decoder.preToULength = 0; assertEquals(4, decoder.toUCountPending())
        decoder.reset(); assertEquals(0, decoder.toUCountPending())
    }
    @Test fun charSlicesAndReadOnlyOutputPreserveCursorRules() {
        val parent = CharsetCharBuffer(charArrayOf('a', 'b', 'c', 'd'))
        parent.position = 1; parent.limit = 3
        val slice = parent.slice()
        slice.put('X'); assertEquals('X', parent.array[1]); assertEquals(1, parent.position)
        assertEquals(1, slice.position); assertEquals(2, slice.capacity)
        parent.limit = 0; assertEquals(0, parent.position)
        val input = BinaryBuffer(byteArrayOf(65))
        val decoder = CharsetUtf8Decoder()
        assertFailsWith<CharsetReadOnlyBufferException> { decoder.decode(input, CharsetCharBuffer(CharArray(1), isReadOnly = true), null, true) }
        assertEquals(0, input.position)
    }
}

internal fun decoderConfigure(cesu: Boolean, mode: Int, seed: Int): CharsetUtf8Decoder {
    val decoder = CharsetUtf8Decoder(cesu)
    if (mode == 1 || mode == 8 || mode == 9) {
        decoder.onMalformedInput(CharsetCodingErrorAction.REPLACE); decoder.onUnmappableCharacter(CharsetCodingErrorAction.REPLACE)
        if (mode == 8) decoder.replaceWith("*")
        if (mode == 9) decoder.replaceWith("\u001a")
    } else if (mode == 2) {
        decoder.onMalformedInput(CharsetCodingErrorAction.IGNORE); decoder.onUnmappableCharacter(CharsetCodingErrorAction.IGNORE)
    } else if (mode >= 3) {
        val context = when (mode) { 4 -> "D"; 5 -> "X"; 6 -> "C"; 7 -> "J"; 10 -> "i"; 11, 12 -> "replay"; else -> null }
        var callback = if (mode == 10) CharsetDecoderCallbacks.SKIP else CharsetDecoderCallbacks.ESCAPE
        if (mode >= 11) {
            var calls = 0
            callback = CharsetDecoderCallback { current, _, _, _, _, _, _, error ->
                if (calls++ == 0) {
                    current.preToUArray[0] = 82; current.preToULength = -1
                    if (mode == 11) CharsetCoderResult.UNDERFLOW else CharsetCoderResult.OVERFLOW
                } else error
            }
        }
        decoder.setToUCallback(CharsetCoderResult.malformedForLength(1), callback, context)
        decoder.setToUCallback(CharsetCoderResult.unmappableForLength(1), callback, context)
    }
    when (seed) {
        1, 2 -> { decoder.preToUArray[0] = (if (seed == 1) 65 else 0xe2).toByte(); decoder.preToUArray[1] = (if (seed == 1) 66 else 0x82).toByte(); decoder.preToULength = -2 }
        3 -> { decoder.charErrorBufferArray[0] = 'Q'; decoder.charErrorBufferArray[1] = 0xdc00.toChar(); decoder.charErrorBufferLength = 2 }
        4 -> { decoder.toUBytesArray[0] = 0xe2.toByte(); decoder.toUnicodeStatus = 2; decoder.mode = 3; decoder.toULength = 1 }
    }
    return decoder
}
internal fun decoderGoldenRun(cesu: Boolean, hex: String, split: Int, capacity: Int, mode: Int, seed: Int, nio: Boolean = false): String =
    decoderRuntimeRun(cesu.toString(), decoderConfigure(cesu, mode, seed), hex, split, capacity, mode, seed, nio)

internal fun decoderRuntimeRun(label: String, decoder: CharsetDecoderRuntime, hex: String, split: Int, capacity: Int, mode: Int, seed: Int = 0, nio: Boolean = false, local: Boolean = false, backend: Int = 1, privateState: () -> List<Int> = { emptyList() }): String {
    val raw = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    val sourceOffset = if (backend in listOf(0, 3, 4)) 0 else 2
    val targetOffset = if (backend in listOf(0, 3, 4, 5)) 0 else 2
    val sourceReadOnly = backend in listOf(2, 3, 7)
    val backing = ByteArray(raw.size + 4); raw.copyInto(backing, sourceOffset)
    var source = BinaryBuffer(backing, arrayOffset = sourceOffset, capacity = raw.size, isReadOnly = sourceReadOnly)
    var hash = 1; var count = 0; var flushing = false
    fun add(value: Int) { hash = 31 * hash + value }
    var end = minOf(split, raw.size); source.limit = end
    if (local) source = BinaryBuffer(backing, arrayOffset = sourceOffset, capacity = end, isReadOnly = sourceReadOnly)
    while (true) {
        val cap = if (count % 5 == 0) 0 else capacity
        val target = CharsetCharBuffer(CharArray(cap + 4), targetOffset, cap, isReadOnly = backend == 6, arrayAccessible = backend !in listOf(4, 5))
        val offsets = CharsetIntBuffer(IntArray(cap))
        val flush = end == raw.size
        var result: CharsetCoderResult? = null
        var failed = false
        val text = try { (if (nio) if (flushing) decoder.flush(target) else decoder.decodeNioLoop(source, target) else decoder.decode(source, target, offsets, flush)).also { result = it }.toString() } catch (_: CharsetBufferOverflowException) { failed = true; "THROW:overflow" } catch (_: IllegalStateException) { failed = true; "THROW:state" } catch (_: IllegalArgumentException) { failed = true; "THROW:argument" } catch (_: IndexOutOfBoundsException) { failed = true; "THROW:bounds" } catch (_: NullPointerException) { failed = true; "THROW:null" } catch (_: CharsetReadOnlyBufferException) { failed = true; "THROW:readonly" }
        add(text.hashCode()); add(source.position); add(target.position); add(offsets.position)
        repeat(target.position) { add(target.get(it).code) }; repeat(offsets.position) { add(offsets.array[it]) }
        for (value in listOf(decoder.toUnicodeStatus, decoder.toUBytesBegin, decoder.toULength, decoder.charErrorBufferLength, decoder.charErrorBufferBegin, decoder.invalidCharLength, decoder.preToUBegin, decoder.preToULength, decoder.preToUFirstLength, decoder.mode)) add(value)
        add(decoder.toUBytesArray.contentHashCode()); add(decoder.charErrorBufferArray.contentHashCode()); add(decoder.invalidCharBuffer.contentHashCode()); add(decoder.preToUArray.contentHashCode())
        add(decoder.toUContext?.hashCode() ?: 0); add(decoder.replacement.hashCode()); add(decoder.malformedInputAction.toString().hashCode()); add(decoder.unmappableCharacterAction.toString().hashCode())
        for (value in privateState()) add(value)
        count++; check(count <= 1000) { "Decoder did not terminate: $hex/$mode/$seed" }
        if (failed) break
        if (nio) {
            if (result!!.isUnderflow()) {
                if (flushing) break
                if (end == raw.size) flushing = true else { end = minOf(end + split, raw.size); source.limit = end }
            }
            continue
        }
        if (result!!.isUnderflow() && source.position == (if (local) source.limit else end)) {
            if (flush) break
            val start = end
            end = minOf(end + split, raw.size)
            if (local) source = BinaryBuffer(backing, arrayOffset = sourceOffset + start, capacity = end - start, isReadOnly = sourceReadOnly) else source.limit = end
        }
    }
    return "$label|$hex|$split|$capacity|$mode|$seed|$count|$hash"
}

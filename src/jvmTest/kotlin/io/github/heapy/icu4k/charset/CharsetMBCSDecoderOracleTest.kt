package io.github.heapy.icu4k.charset

import com.ibm.icu.charset.CharsetDecoderICU
import io.github.heapy.icu4k.util.BinaryBuffer
import java.nio.*
import java.nio.charset.*
import java.lang.reflect.InvocationTargetException
import kotlin.test.*

class CharsetMBCSDecoderOracleTest {
    private val base = CharsetDecoderICU::class.java
    private val decode = base.getDeclaredMethod("decode", ByteBuffer::class.java, CharBuffer::class.java, IntBuffer::class.java, Boolean::class.javaPrimitiveType).apply { isAccessible = true }
    private fun field(name: String) = base.getDeclaredField(name).apply { isAccessible = true }
    private val ints = listOf("toUnicodeStatus", "toUBytesBegin", "toULength", "charErrorBufferLength", "charErrorBufferBegin", "invalidCharLength", "preToUBegin", "preToULength", "preToUFirstLength", "mode")
    private fun result(block: () -> Any?): String = try { block().toString() } catch (e: Throwable) {
        val t = if (e is InvocationTargetException) e.targetException else e
        when (t) {
            is BufferOverflowException, is CharsetBufferOverflowException -> "THROW:overflow"
            is ReadOnlyBufferException, is CharsetReadOnlyBufferException -> "THROW:readonly"
            is IllegalArgumentException -> "THROW:argument"
            is IndexOutOfBoundsException -> "THROW:bounds"
            is NullPointerException -> "THROW:null"
            is IllegalStateException -> "THROW:state"
            else -> throw t
        }
    }
    internal fun check(cs: Charset, data: MBCSConverterData, input: ByteArray, split: Int, capacity: Int, mode: Int, local: Boolean = false) {
        val source = cs.newDecoder() as CharsetDecoderICU
        when (mode) {
            1 -> { source.onMalformedInput(CodingErrorAction.REPLACE); source.onUnmappableCharacter(CodingErrorAction.REPLACE) }
            2 -> { source.onMalformedInput(CodingErrorAction.IGNORE); source.onUnmappableCharacter(CodingErrorAction.IGNORE) }
            4 -> {
                source.setToUCallback(CoderResult.malformedForLength(1), com.ibm.icu.charset.CharsetCallback.TO_U_CALLBACK_ESCAPE, "D")
                source.setToUCallback(CoderResult.unmappableForLength(1), com.ibm.icu.charset.CharsetCallback.TO_U_CALLBACK_ESCAPE, "D")
            }
        }
        val port = mbcsDecoderConfigure(data, mode, 0)
        val padded = ByteArray(input.size + 4); input.copyInto(padded, 2)
        var end = minOf(split, input.size)
        var s = ByteBuffer.wrap(padded, 2, input.size).slice().apply { limit(end) }
        var p = BinaryBuffer(padded, arrayOffset = 2, capacity = input.size).apply { limit = end }
        var count = 0
        while (true) {
            val cap = if (count % 5 == 0) 0 else capacity
            val t = CharBuffer.allocate(cap); val o = IntBuffer.allocate(cap)
            val pt = CharsetCharBuffer(CharArray(cap)); val po = CharsetIntBuffer(IntArray(cap))
            val context = "${data.name} / ${input.joinToString { (it.toInt() and 255).toString(16) }} / $split / $capacity / $mode / $local / $count"
            val expected = result { decode.invoke(source, s, t, o, end == input.size) }
            val actual = result { port.decode(p, pt, po, end == input.size) }
            assertEquals(expected, actual, context)
            assertEquals(s.position(), p.position, "$context source")
            assertEquals(t.position(), pt.position, "$context target")
            assertEquals(o.position(), po.position, "$context offsets")
            assertContentEquals(CharArray(t.position()) { t[it] }, pt.array.copyOf(pt.position), "$context text")
            assertContentEquals(IntArray(o.position()) { o[it] }, po.array.copyOf(po.position), "$context offsets")
            val expectedInts = ints.map { field(it).getInt(source) }
            val actualInts = listOf(port.toUnicodeStatus, port.toUBytesBegin, port.toULength, port.charErrorBufferLength, port.charErrorBufferBegin, port.invalidCharLength, port.preToUBegin, port.preToULength, port.preToUFirstLength, port.mode)
            assertEquals(expectedInts, actualInts, "$context state")
            assertContentEquals(field("toUBytesArray").get(source) as ByteArray, port.toUBytesArray, "$context bytes")
            assertContentEquals(field("preToUArray").get(source) as ByteArray, port.preToUArray, "$context replay")
            assertContentEquals(field("charErrorBufferArray").get(source) as CharArray, port.charErrorBufferArray, "$context queued")
            assertContentEquals(field("invalidCharBuffer").get(source) as CharArray, port.invalidCharBuffer, "$context invalid")
            assertTrue(++count < 1000, context)
            if (expected.startsWith("THROW:")) break
            if (expected == "UNDERFLOW" && s.position() == s.limit()) {
                if (end == input.size) break
                val start = end; end = minOf(end + split, input.size)
                if (local) {
                    s = ByteBuffer.wrap(padded, 2 + start, end - start).slice()
                    p = BinaryBuffer(padded, arrayOffset = 2 + start, capacity = end - start)
                } else { s.limit(end); p.limit = end }
            }
        }
    }
    @Test fun extensionContinuationAndReplayStateMatchesSource() {
        val type = Class.forName("com.ibm.icu.charset.CharsetMBCS")
        val ctor = type.getDeclaredConstructor(String::class.java, String::class.java, Array<String>::class.java).apply { isAccessible = true }
        for (row in mbcsLoaderGoldenRows) {
            val name = row.substringBefore('|')
            val cs = ctor.newInstance(name, "x-icu4k-test", emptyArray<String>()) as Charset
            val data = MBCSConverterData.load(name)
            for (hex in mbcsExtraCases(data, 4) + mbcsExtraCases(data, 5)) for (mode in listOf(0, 1, 4)) {
                val input = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                check(cs, data, input, if (mode == 4) 3 else 1, if (mode == 4) 3 else 1, mode, local = mode == 1)
            }
        }
    }
}

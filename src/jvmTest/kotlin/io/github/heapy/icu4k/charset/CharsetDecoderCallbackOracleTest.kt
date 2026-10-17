package io.github.heapy.icu4k.charset

import com.ibm.icu.charset.CharsetICU as SourceCharset
import com.ibm.icu.charset.CharsetDecoderICU as SourceDecoder
import com.ibm.icu.charset.CharsetCallback as SourceCallback
import io.github.heapy.icu4k.util.BinaryBuffer
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.IntBuffer
import java.nio.charset.CoderResult
import kotlin.test.*

class CharsetDecoderCallbackOracleTest {
    private fun field(name: String) = SourceDecoder::class.java.getDeclaredField(name).apply { isAccessible = true }
    private val invalidLength = field("invalidCharLength")
    private val errorLength = field("charErrorBufferLength")
    private val errorArray = field("charErrorBufferArray")
    private val subChar = SourceCharset::class.java.getDeclaredField("subChar1").apply { isAccessible = true }
    private fun outcome(action: () -> Any?): Any? = try { action() } catch (_: IndexOutOfBoundsException) { "bounds" } catch (_: ClassCastException) { "cast" }
    @Test fun allDecoderCallbacksReplacementRulesOffsetsAndOverflowMatchSource() {
        val sourceCallbacks = listOf(SourceCallback.TO_U_CALLBACK_STOP, SourceCallback.TO_U_CALLBACK_SKIP, SourceCallback.TO_U_CALLBACK_SUBSTITUTE, SourceCallback.TO_U_CALLBACK_ESCAPE)
        val equalsDecimalTag = object {
            override fun equals(other: Any?): Boolean = other == "D"
            override fun hashCode(): Int = 68
        }
        val ports = listOf(CharsetDecoderCallbacks.STOP, CharsetDecoderCallbacks.SKIP, CharsetDecoderCallbacks.SUBSTITUTE, CharsetDecoderCallbacks.ESCAPE)
        for (substitute in listOf(0, 1)) for (replacement in listOf("\ufffd", "\u001a", "*")) for (length in listOf(1, 2, 4, 9)) for (capacity in listOf(0, 1, 8, 32)) for (context in listOf(null, "i", "D", "X", "C", "J", 7, equalsDecimalTag)) for (unmappable in listOf(false, true)) for (kind in ports.indices) {
            val charset = SourceCharset.forNameICU("UTF-8") as SourceCharset
            subChar.setByte(charset, substitute.toByte())
            val source = charset.newDecoder() as SourceDecoder
            source.replaceWith(replacement); invalidLength.setInt(source, length)
            val port = object : CharsetDecoderRuntime(3, substitute.toByte()) {
                override fun decodeLoop(source: BinaryBuffer, target: CharsetCharBuffer, offsets: CharsetIntBuffer?, flush: Boolean) = CharsetCoderResult.UNDERFLOW
            }
            port.replaceWith(replacement); port.invalidCharLength = length
            val original = ByteBuffer.wrap(ByteArray(5)); original.position(3)
            val input = BinaryBuffer(ByteArray(5)); input.position = 3
            val expectedOut = CharBuffer.allocate(capacity)
            val actualOut = CharsetCharBuffer(CharArray(capacity))
            val expectedOffsets = IntBuffer.allocate(capacity)
            val actualOffsets = CharsetIntBuffer(IntArray(capacity))
            val buffer = CharArray(length) { (0x80 + it).toChar() }
            val expectedError = if (unmappable) CoderResult.unmappableForLength(length) else CoderResult.malformedForLength(length)
            val actualError = if (unmappable) CharsetCoderResult.unmappableForLength(length) else CharsetCoderResult.malformedForLength(length)
            val expected = outcome { sourceCallbacks[kind].call(source, context, original, expectedOut, expectedOffsets, buffer, length, expectedError).toString() }
            val actual = outcome { ports[kind].call(port, context, input, actualOut, actualOffsets, buffer, length, actualError).toString() }
            assertEquals(expected, actual, "$substitute/$replacement/$length/$capacity/$context/$unmappable/$kind")
            assertEquals(expectedOut.position(), actualOut.position)
            assertContentEquals(expectedOut.array(), actualOut.array)
            assertEquals(expectedOffsets.position(), actualOffsets.position)
            assertContentEquals(expectedOffsets.array(), actualOffsets.array)
            assertEquals(errorLength.getInt(source), port.charErrorBufferLength)
            assertContentEquals(errorArray.get(source) as CharArray, port.charErrorBufferArray)
            assertEquals(original.position(), input.position)
        }
    }
}

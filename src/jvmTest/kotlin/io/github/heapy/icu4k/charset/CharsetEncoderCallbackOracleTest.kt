package io.github.heapy.icu4k.charset

import com.ibm.icu.charset.CharsetCallback
import com.ibm.icu.charset.CharsetEncoderICU
import io.github.heapy.icu4k.util.BinaryBuffer
import java.nio.*
import java.nio.charset.*
import kotlin.test.*

class CharsetEncoderCallbackOracleTest {
    private val type = CharsetEncoderICU::class.java
    private fun field(name: String) = type.getDeclaredField(name).apply { isAccessible = true }
    private fun result(block: () -> Any?): String = try { block().toString() } catch (_: BufferOverflowException) { "overflow" }
        catch (_: CharsetBufferOverflowException) { "overflow" } catch (_: ClassCastException) { "cast" }
        catch (_: IndexOutOfBoundsException) { "bounds" }
    private class Probe(sub: Byte) : CharsetEncoderRuntime(4, byteArrayOf(26), sub) {
        init { reset() }
        override fun reset() { super.reset(); fromUnicodeStatus = 1 }
        override fun encodeLoop(source: CharsetCharBuffer, target: BinaryBuffer, offsets: CharsetIntBuffer?, flush: Boolean): CharsetCoderResult {
            val begin = target.position
            while (source.hasRemaining() && target.remaining > 0) {
                val c = source.get(); check(c.code < 128)
                target.putCharsetByte(c.code.toByte())
            }
            offsets?.let { repeat(target.position - begin) { n -> it.put(n) } }
            return if (source.hasRemaining()) CharsetCoderResult.OVERFLOW else CharsetCoderResult.UNDERFLOW
        }
    }
    @Test fun directCallbacksMatchSourceWithMetadataReplacementsAndEscapeContexts() {
        val charsetType = Class.forName("com.ibm.icu.charset.CharsetASCII")
        val ctor = charsetType.getDeclaredConstructor(String::class.java, String::class.java, Array<String>::class.java).apply { isAccessible = true }
        val meta = Class.forName("com.ibm.icu.charset.CharsetICU")
        val sourceCallbacks = listOf(CharsetCallback.FROM_U_CALLBACK_STOP, CharsetCallback.FROM_U_CALLBACK_SKIP, CharsetCallback.FROM_U_CALLBACK_SUBSTITUTE, CharsetCallback.FROM_U_CALLBACK_ESCAPE)
        val callbacks = listOf(CharsetEncoderCallbacks.STOP, CharsetEncoderCallbacks.SKIP, CharsetEncoderCallbacks.SUBSTITUTE, CharsetEncoderCallbacks.ESCAPE)
        var cases = 0
        for (sub in listOf(0, 26)) {
            val cs = ctor.newInstance("US-ASCII", "x-icu4k-oracle", emptyArray<String>()) as Charset
            meta.getDeclaredField("maxBytesPerChar").apply { isAccessible = true }.setInt(cs, 4)
            meta.getDeclaredField("subChar1").apply { isAccessible = true }.setByte(cs, sub.toByte())
            for (replacement in listOf(byteArrayOf(26), byteArrayOf(65, 66)))
                for (callback in callbacks.indices) for (context in listOf(null, "i", "other", "J", "C", "D", "X", "U", "S", encoderNonStringContext))
                    for (cp in listOf(0, 65, 0xad, 0x10000, 0x1f600, 0xe0000, 0x10ffff))
                        for (unmapped in listOf(false, true)) for (cap in listOf(0, 1, 32)) for (length in listOf(1, 2, 6)) {
                            val e = cs.newEncoder() as CharsetEncoderICU
                            val p = Probe(sub.toByte())
                            e.replaceWith(replacement); p.replaceWith(replacement)
                            val invalid = if (cp < 0x10000) charArrayOf(cp.toChar(), 0.toChar()) else charArrayOf(((cp ushr 10) + 0xd7c0).toChar(), ((cp and 1023) + 0xdc00).toChar())
                            invalid.copyInto(field("invalidUCharBuffer").get(e) as CharArray); invalid.copyInto(p.invalidUCharBuffer)
                            val invalidLength = if (cp < 0x10000) 1 else 2
                            field("invalidUCharLength").setInt(e, invalidLength); p.invalidUCharLength = invalidLength
                            val input = charArrayOf('A', 0xd800.toChar(), 0xdc00.toChar(), 0xffff.toChar(), 0xfffe.toChar(), 0xad.toChar())
                            val source = CharBuffer.wrap(charArrayOf('x', 'y')).apply { position(1) }
                            val portSource = CharsetCharBuffer(charArrayOf('x', 'y')).apply { position = 1 }
                            val target = ByteBuffer.allocate(cap); val portTarget = BinaryBuffer(ByteArray(cap))
                            val offsets = IntBuffer.allocate(cap); val portOffsets = CharsetIntBuffer(IntArray(cap))
                            val sourceError = if (unmapped) CoderResult.unmappableForLength(1) else CoderResult.malformedForLength(1)
                            val portError = if (unmapped) CharsetCoderResult.unmappableForLength(1) else CharsetCoderResult.malformedForLength(1)
                            val expected = result { sourceCallbacks[callback].call(e, context, source, target, offsets, input, length, cp, sourceError) }
                            val actual = result { callbacks[callback].call(p, context, portSource, portTarget, portOffsets, input, length, cp, portError) }
                            val label = "$sub / ${replacement.toList()} / $callback / $context / $cp / $unmapped / $cap / $length"
                            assertEquals(expected, actual, label)
                            assertEquals(source.position(), portSource.position, label)
                            assertEquals(target.position(), portTarget.position, label)
                            assertContentEquals(target.array(), portTarget.bytes, label)
                            assertEquals(offsets.position(), portOffsets.position, label); assertContentEquals(offsets.array(), portOffsets.array, label)
                            assertEquals(field("errorBufferLength").getInt(e), p.errorBufferLength, label)
                            assertContentEquals(field("errorBuffer").get(e) as ByteArray, p.errorBuffer, label)
                            assertContentEquals(field("invalidUCharBuffer").get(e) as CharArray, p.invalidUCharBuffer, label)
                            assertEquals(field("fromUChar32").getInt(e), p.fromUChar32, label)
                            assertEquals(field("fromUnicodeStatus").getInt(e), p.fromUnicodeStatus, label)
                            cases++
                        }
        }
        assertEquals(20160, cases)
    }
    @Test fun surrogateHelpersAndPendingCountsMatchSource() {
        val e = com.ibm.icu.charset.CharsetICU.forNameICU("US-ASCII").newEncoder()
        val p = CharsetSingleByteEncoder()
        val cursor = type.getDeclaredMethod("handleSurrogates", CharBuffer::class.java, Char::class.javaPrimitiveType).apply { isAccessible = true }
        val array = type.getDeclaredMethod("handleSurrogates", CharArray::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Char::class.javaPrimitiveType).apply { isAccessible = true }
        for (lead in 0..65535) for (trail in listOf(-1, 0, 0xd7ff, 0xd800, 0xdbff, 0xdc00, 0xdfff, 0xe000, 0xffff)) {
            val chars = if (trail < 0) charArrayOf() else charArrayOf(trail.toChar())
            val s = CharBuffer.wrap(chars); val ps = CharsetCharBuffer(chars)
            field("fromUChar32").setInt(e, 0); p.fromUChar32 = 0
            assertEquals(cursor.invoke(e, s, lead.toChar())?.toString(), p.handleSurrogates(ps, lead.toChar())?.toString())
            assertEquals(s.position(), ps.position); assertEquals(field("fromUChar32").getInt(e), p.fromUChar32)
            field("fromUChar32").setInt(e, 0); p.fromUChar32 = 0
            assertEquals(array.invoke(e, chars, 0, chars.size, lead.toChar())?.toString(), p.handleSurrogates(chars, 0, chars.size, lead.toChar())?.toString())
            assertEquals(field("fromUChar32").getInt(e), p.fromUChar32)
        }
        val pending = type.getDeclaredMethod("fromUCountPending").apply { isAccessible = true }
        for (first in listOf(-1, 0, 1, 0xffff, 0x10000)) for (length in listOf(-3, 0, 3)) for (cp in listOf(-1, 0, 0xd800)) {
            field("preFromUFirstCP").setInt(e, first); field("preFromULength").setInt(e, length); field("fromUChar32").setInt(e, cp)
            p.preFromUFirstCP = first; p.preFromULength = length; p.fromUChar32 = cp
            assertEquals(pending.invoke(e), p.fromUCountPending())
        }
    }
}

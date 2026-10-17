package io.github.heapy.icu4k.charset

import java.nio.ByteBuffer
import kotlin.test.*
import io.github.heapy.icu4k.util.BinaryBuffer

class MBCSExtensionOracleTest {
    private fun field(value: Any, name: String): Any? {
        var type: Class<*>? = value.javaClass
        while (type != null) {
            try { return type.getDeclaredField(name).apply { isAccessible = true }.get(value) }
            catch (_: NoSuchFieldException) { type = type.superclass }
        }
        error(name)
    }
    @Test fun everyPackagedExtensionMappingMatchesActualSourceDecoder() {
        val type = Class.forName("com.ibm.icu.charset.CharsetMBCS")
        val ctor = type.getDeclaredConstructor(String::class.java, String::class.java, Array<String>::class.java).apply { isAccessible = true }
        var converters = 0; var mappings = 0; var calls = 0; var outputs = 0
        for (row in mbcsLoaderGoldenRows.filterIndexed { i, _ -> i % 2 == 0 }) {
            val name = row.substringBefore('|')
            val sourceCharset = ctor.newInstance(name, "x-icu4k-test", emptyArray<String>()) as java.nio.charset.Charset
            val table = field(field(sourceCharset, "sharedData")!!, "mbcs")!!
            val ext = field(table, "extIndexes") as ByteBuffer? ?: continue
            if (ext.getInt(ext.position() + 8) <= 0) continue
            val port = MBCSConverterData.load(name).sharedData.mbcs.extIndexes!!
            val decoder = sourceCharset.newDecoder()
            val match = decoder.javaClass.declaredMethods.single { it.name == "matchToU" }.apply { isAccessible = true }
            val write = decoder.javaClass.declaredMethods.single { it.name == "writeToU" }.apply { isAccessible = true }
            val seen = mutableSetOf<Int>()
            fun checkOutput(value: Int) {
                val masked = value and (1 shl 23).inv()
                if (!seen.add(masked)) return
                val chars = java.nio.CharBuffer.allocate(32)
                val offsets = java.nio.IntBuffer.allocate(32)
                val result = write.invoke(decoder, masked, chars, offsets, 7) as java.nio.charset.CoderResult
                assertTrue(result.isUnderflow)
                assertContentEquals(CharArray(chars.position()) { chars[it] }, MBCSToUnicodeExtension.characters(port, masked), name)
                assertTrue((0 until offsets.position()).all { offsets[it] == 7 })
                outputs++
            }
            val words = ext.duplicate().order(ext.order()).apply { position(ext.getInt(4)) }.asIntBuffer()
            fun check(input: ByteArray) {
                for (split in setOf(0, input.size / 2, input.size)) for (siso in -1..1) for (flush in listOf(false, true)) {
                    val pre = ByteArray(split + 4); input.copyInto(pre, 2, 0, split)
                    val bytes = ByteArray(input.size - split + 4); input.copyInto(bytes, 2, split)
                    val source = ByteBuffer.wrap(bytes).apply { position(2); limit(2 + input.size - split) }.asReadOnlyBuffer()
                    val common = BinaryBuffer(bytes, isReadOnly = true).apply { position = 2; limit = source.limit() }
                    val expectedValue = intArrayOf(123); val actualValue = intArrayOf(123)
                    val expected = match.invoke(decoder, siso.toByte(), pre, 2, split, source, expectedValue, false, flush)
                    val actual = MBCSToUnicodeExtension.match(port, siso, pre, 2, split, common, actualValue, flush)
                    assertEquals(expected, actual, "$name / ${input.toList()} / $split / $siso / $flush")
                    assertContentEquals(expectedValue, actualValue, name)
                    assertEquals(2, common.position); assertEquals(2, source.position()); assertEquals(0, port.position)
                    calls++
                }
            }
            fun walk(index: Int, prefix: ByteArray) {
                assertTrue(prefix.size <= 31, name)
                val header = words[index]
                if ((header and 0xffffff) != 0) { check(prefix); checkOutput(header and 0xffffff); mappings++ }
                val length = header ushr 24
                for (i in 1..length) {
                    val word = words[index + i]; val value = word and 0xffffff
                    if (value == 0) continue
                    val next = prefix + (word ushr 24).toByte()
                    if (value < 0x1f0000) walk(value, next)
                    else { check(next); check(next + 0x55.toByte()); checkOutput(value); mappings++ }
                }
                check(prefix)
            }
            walk(0, byteArrayOf()); converters++
        }
        println("MBCS extension oracle: $converters converters, $mappings mappings, $calls calls, $outputs outputs")
        assertEquals(48, converters); assertEquals(3868, mappings); assertEquals(139116, calls); assertTrue(outputs > 1000)
    }
}

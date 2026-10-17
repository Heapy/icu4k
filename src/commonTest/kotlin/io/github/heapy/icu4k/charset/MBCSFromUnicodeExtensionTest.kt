package io.github.heapy.icu4k.charset

import io.github.heapy.icu4k.util.BinaryBuffer
import kotlin.test.*

/** Traverse serialized fixture tables independently of the production matcher. */
internal fun fromUnicodeExtensionInputs(b: BinaryBuffer): List<Pair<Int, CharArray>> {
    fun int(index: Int) = b.slice(index * 4, 4).readInt()
    fun chars(index: Int): CharArray = b.slice(int(index)).let { it.readChars(it.remaining / 2) }
    fun ints(index: Int): IntArray = b.slice(int(index)).let { it.readInts(it.remaining / 4) }
    val stage1Length = int(11)
    if (stage1Length <= 0) return emptyList()
    val stage12 = chars(10); val stage3 = chars(13); val stage3b = ints(15)
    val units = chars(5); val values = ints(6)
    val cases = mutableListOf<Pair<Int, CharArray>>()
    for (cp in 0 until minOf(stage1Length * 1024, 0x110000)) {
        val index = stage3[(stage12[stage12[cp ushr 10].code + ((cp ushr 4) and 63)].code shl 2) + (cp and 15)].code
        val value = stage3b[index]
        if (value == 0) continue
        val tails = mutableSetOf<String>()
        fun record(prefix: CharArray) {
            // Hex keys avoid lone-surrogate string constants/IR and give deterministic ordering.
            val hex = prefix.joinToString("") { it.code.toString(16).padStart(4, '0') }
            tails.add(hex); tails.add(hex + "5555")
        }
        fun walk(index: Int, prefix: CharArray) {
            record(prefix)
            for (i in 1..units[index].code) {
                val next = prefix + units[index + i]
                val v = values[index + i]
                if (v == 0) continue
                if (v ushr 24 == 0) walk(v, next) else record(next)
            }
        }
        if (value >= 0 && value < 0x1f0000) walk(value, charArrayOf()) else record(charArrayOf())
        for (hex in tails.sorted()) cases.add(cp to hex.chunked(4).map { it.toInt(16).toChar() }.toCharArray())
    }
    return cases
}
internal fun fromUnicodeExtensionFingerprint(b: BinaryBuffer?): String {
    if (b == null) return "0|1|0|1"
    val outputs = mutableSetOf<Int>()
    var hash = 1; var count = 0
    fun add(v: Int) { hash = 31 * hash + v }
    for ((cp, input) in fromUnicodeExtensionInputs(b)) for (split in listOf(0, input.size / 2, input.size).distinct())
        for (fallback in listOf(false, true)) for (flush in listOf(false, true)) {
            val pre = CharArray(split + 4); input.copyInto(pre, 2, 0, split)
            val backing = CharArray(input.size - split + 4); input.copyInto(backing, 1, split)
            val source = CharsetCharBuffer(backing).apply { position = 1; limit = 1 + input.size - split }
            val value = intArrayOf(123)
            val match = MBCSFromUnicodeExtension.match(b, cp, pre, 2, split, source, value, fallback, flush)
            if (match >= 2) outputs.add(value[0])
            add(match); add(value[0]); add(source.position); add(b.position)
            val simple = intArrayOf(123); add(MBCSFromUnicodeExtension.simpleMatch(b, cp, simple, fallback)); add(simple[0]); count++
        }
    return "$count|$hash|${fromUnicodeExtensionWriteFingerprint(b, outputs)}"
}
internal fun fromUnicodeExtensionWriteFingerprint(b: BinaryBuffer, values: Set<Int>): String {
    var hash = 1; var count = 0
    fun add(value: Int) { hash = 31 * hash + value }
    for (value in values.sorted()) for (previous in listOf(0, 1, 2)) for (cap in listOf(0, 1, 3, 32)) for (readonly in listOf(false, true)) {
        val encoder = CharsetSingleByteEncoder().apply { fromUnicodeStatus = previous }
        val output = ByteArray(cap + 4)
        val target = BinaryBuffer(output, arrayOffset = 2, capacity = cap, isReadOnly = readonly)
        val offsets = CharsetIntBuffer(IntArray(cap))
        val result = try { MBCSFromUnicodeExtension.write(encoder, b, value, target, offsets, 7).toString() }
        catch (_: CharsetBufferOverflowException) { "THROW:overflow" }
        catch (_: CharsetBufferUnderflowException) { "THROW:underflow" }
        catch (_: CharsetReadOnlyBufferException) { "THROW:readonly" }
        catch (_: IndexOutOfBoundsException) { "THROW:bounds" }
        catch (_: IllegalArgumentException) { "THROW:argument" }
        add(result.hashCode()); add(target.position); add(offsets.position)
        add(output.contentHashCode()); add(offsets.array.contentHashCode()); add(encoder.fromUnicodeStatus)
        add(encoder.errorBufferLength); add(encoder.errorBuffer.contentHashCode()); add(b.position); count++
    }
    return "$count|$hash"
}
class MBCSFromUnicodeExtensionTest {
    @Test fun everyPackagedExtensionTrieMatchesSource() {
        for (row in fromUnicodeExtensionGoldenRows) {
            val name = row.substringBefore('|')
            val data = MBCSConverterData.load(name)
            assertEquals(row.substringAfter('|'), fromUnicodeExtensionFingerprint(data.sharedData.mbcs.extIndexes), name)
        }
    }
    @Test fun syntheticFlagsAndPartialLimitsMatchSourceInBothByteOrders() {
        for (row in syntheticFromUnicodeGoldenRows) for (order in BinaryBuffer.ByteOrder.entries) {
            val kind = row.substringBefore('|').toInt()
            assertEquals(row.substringAfter('|'), fromUnicodeExtensionFingerprint(fromUnicodeFixture(kind, order)), "$kind / $order")
        }
    }
    @Test fun sectionSearchCoversTheEntireUtf16Domain() {
        for (values in listOf(listOf<Int>(), listOf(0), listOf(0, 65535), listOf(1, 3, 100, 255, 32768, 65535))) {
            for (char in 0..65535) assertEquals(values.indexOf(char), MBCSFromUnicodeExtension.find(values.size, char) { values[it] })
        }
    }
    @Test fun noExtensionDoesNotChangeResult() {
        val result = intArrayOf(123)
        assertEquals(0, MBCSFromUnicodeExtension.match(null, 65, null, 0, 0, null, result, false, false))
        assertEquals(0, MBCSFromUnicodeExtension.simpleMatch(null, 65, result, true)); assertEquals(123, result[0])
    }
}

internal fun fromUnicodeFixture(kind: Int, order: BinaryBuffer.ByteOrder): BinaryBuffer {
    val b = BinaryBuffer(ByteArray(2048), order)
    fun int(index: Int, value: Int) { b.position = index; b.writeInt(value) }
    fun char(index: Int, value: Int) { b.position = index; b.writeShort(value) }
    for ((offset, value) in listOf(40 to 128, 44 to 65, 48 to 256, 52 to 640, 56 to 128, 60 to 896, 64 to 4, 20 to 1024, 24 to 1280, 28 to 64, 32 to 1792, 36 to 64)) int(offset, value)
    for (i in 0 until 65) char(128 + i * 2, 65)
    for (i in listOf(0, 56, 64)) char(128 + i * 2, 129)
    char(128 + 129 * 2, 16); char(128 + 133 * 2, 16); char(640 + 64 * 2, 1); char(640 + 65 * 2, 1)
    int(900, when (kind) { 0 -> 0x81000041.toInt(); 1 -> 0x01000041; 2 -> 0x80000001.toInt(); 3 -> 0xa1000041.toInt(); 4 -> 0x00200000; else -> 1 })
    char(1026, 2); char(1028, 65); char(1030, 0xd800)
    int(1284, 0x01000058); int(1288, 0x82008140.toInt()); int(1292, 4)
    char(1032, 1); char(1034, 66); int(1296, 0x81000059.toInt())
    int(1300, when (kind) { 7 -> 0xa1000041.toInt(); 8 -> 0x80000001.toInt(); else -> 0x9f000000.toInt() })
    if (kind == 6) for (depth in 0..20) {
        val index = 1 + 2 * depth
        char(1024 + index * 2, 1); char(1024 + (index + 1) * 2, 65)
        int(1280 + index * 4, 0x81000050.toInt()); int(1280 + (index + 1) * 4, if (depth == 20) 0x81000051.toInt() else index + 2)
    }
    b.position = 1792; repeat(64) { b.writeByte(it + 1) }; b.position = 0
    return b
}

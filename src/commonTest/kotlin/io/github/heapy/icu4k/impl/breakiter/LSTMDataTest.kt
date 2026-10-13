package io.github.heapy.icu4k.impl.breakiter

import io.github.heapy.icu4k.impl.ICUBinary
import io.github.heapy.icu4k.impl.ICUResourceBundleReader
import io.github.heapy.icu4k.util.BinaryBuffer
import kotlin.test.*

internal fun lstmFixture(embeddings: Int, hunits: Int, dict: Array<String>, data: IntArray, type: String): BinaryBuffer {
    val body = BinaryBuffer(ByteArray(16384))
    val keys = listOf("data", "dict", "embeddings", "hunits", "model", "type")
    body.position = 36
    val keyOffsets = keys.map { key ->
        val offset = body.position
        for (c in key) body.writeByte(c.code)
        body.writeByte(0)
        offset
    }
    fun align() { while (body.position % 4 != 0) body.writeByte(0) }
    align()
    val keysTop = body.position / 4
    fun string(value: String): Int {
        val offset = body.position / 4
        body.writeInt(value.length)
        for (c in value) body.writeShort(c.code)
        body.writeShort(0); align()
        return offset
    }
    val strings = dict.map { string(it) }
    val dictHandle = (8 shl 28) or (body.position / 4)
    body.writeInt(strings.size); for (value in strings) body.writeInt(value)
    val dataHandle = (14 shl 28) or (body.position / 4)
    body.writeInt(data.size); for (value in data) body.writeInt(value)
    val modelHandle = string("test-model")
    val typeHandle = string(type)
    val root = (4 shl 28) or (body.position / 4)
    body.writeInt(keys.size); for (value in keyOffsets) body.writeInt(value)
    for (value in intArrayOf(dataHandle, dictHandle, (7 shl 28) or embeddings, (7 shl 28) or hunits, modelHandle, typeHandle)) body.writeInt(value)
    val size = body.position
    body.position = 0
    for (value in intArrayOf(root, 8, keysTop, size / 4, size / 4, keys.size, 1, keysTop, 0)) body.writeInt(value)
    return BinaryBuffer(ICUBinary.writeHeader(0x52657342, 0x02000000, 0) + body.bytes.copyOf(size))
}
internal fun lstmDataSize(embeddings: Int, hunits: Int, dictionarySize: Int): Int =
    (dictionarySize + 1) * embeddings + 2 * (embeddings * 4 * hunits + hunits * 4 * hunits + 4 * hunits) + 2 * hunits * 4 + 4

class LSTMDataTest {
    @Test fun matrixShapesBitPatternsAndDuplicateEmbeddings() {
        val dictionary = arrayOf("ก", "ข", "ก")
        val bits = intArrayOf(0, Int.MIN_VALUE, 0x3f800000, 0xbf800000.toInt(), 1, 0x7f800000, 0xff800000.toInt())
        val payload = IntArray(lstmDataSize(2, 3, dictionary.size)) { bits[it % bits.size] }
        val model = LSTMBreakEngine.createData(ICUResourceBundleReader(lstmFixture(2, 3, dictionary, payload, "codepoints")))
        assertEquals(LSTMBreakEngine.EmbeddingType.CODE_POINTS, model.fType)
        assertEquals("test-model", model.fName)
        assertEquals(mapOf("ก" to 2, "ข" to 1), model.fDict)
        assertEquals(4, model.fEmbedding.size)
        assertEquals(3, model.fForwardU.size)
        assertEquals(12, model.fForwardB.size)
        assertEquals(6, model.fOutputW.size)
        val decoded = buildList {
            for (matrix in listOf(model.fEmbedding, model.fForwardW, model.fForwardU)) for (row in matrix) addAll(row.toList())
            addAll(model.fForwardB.toList())
            for (matrix in listOf(model.fBackwardW, model.fBackwardU)) for (row in matrix) addAll(row.toList())
            addAll(model.fBackwardB.toList())
            for (row in model.fOutputW) addAll(row.toList())
            addAll(model.fOutputB.toList())
        }
        assertContentEquals(payload, decoded.map { it.toRawBits() }.toIntArray())
        for ((type, expected) in listOf("graphclust" to LSTMBreakEngine.EmbeddingType.GRAPHEME_CLUSTER,
            "other" to LSTMBreakEngine.EmbeddingType.UNKNOWN)) {
            assertEquals(expected, LSTMBreakEngine.createData(ICUResourceBundleReader(lstmFixture(2, 3, dictionary, payload, type))).fType)
        }
    }
}

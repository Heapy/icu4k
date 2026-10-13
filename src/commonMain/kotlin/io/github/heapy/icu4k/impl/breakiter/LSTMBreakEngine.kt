// © 2021 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.breakiter

import io.github.heapy.icu4k.impl.ICUResourceBundleReader
import io.github.heapy.icu4k.util.MissingResourceException
import io.github.heapy.icu4k.util.UResourceTypeMismatchException

/** LSTM model decoding; inference/vectorizers and default-model resolution are being ported. */
internal class LSTMBreakEngine private constructor() {
    enum class EmbeddingType { UNKNOWN, CODE_POINTS, GRAPHEME_CLUSTER }
    enum class LSTMClass { BEGIN, INSIDE, END, SINGLE }

    class LSTMData(reader: ICUResourceBundleReader, resource: Int = reader.rootResource) {
        val fType: EmbeddingType
        val fName: String
        val fDict: Map<String, Int>
        val fEmbedding: Array<FloatArray>
        val fForwardW: Array<FloatArray>
        val fForwardU: Array<FloatArray>
        val fForwardB: FloatArray
        val fBackwardW: Array<FloatArray>
        val fBackwardU: Array<FloatArray>
        val fBackwardB: FloatArray
        val fOutputW: Array<FloatArray>
        val fOutputB: FloatArray
        init {
            val table = reader.getTable(resource) ?: throw UResourceTypeMismatchException("")
            fun item(key: String): Int = table.getResource(key).also {
                if (it == ICUResourceBundleReader.RES_BOGUS) throw MissingResourceException("Missing LSTM resource: $key", "LSTMData", key)
            }
            fun integer(key: String): Int {
                val value = item(key)
                if (ICUResourceBundleReader.RES_GET_TYPE(value) != 7) throw UResourceTypeMismatchException("")
                return ICUResourceBundleReader.RES_GET_INT(value)
            }
            fun string(key: String): String = reader.getString(item(key)) ?: throw UResourceTypeMismatchException("")
            val embeddings = integer("embeddings")
            val hunits = integer("hunits")
            fName = string("model")
            fType = when (string("type")) {
                "codepoints" -> EmbeddingType.CODE_POINTS
                "graphclust" -> EmbeddingType.GRAPHEME_CLUSTER
                else -> EmbeddingType.UNKNOWN
            }
            val dict = reader.getArray(item("dict")) ?: throw UResourceTypeMismatchException("")
            val data = reader.getIntVector(item("data")) ?: throw UResourceTypeMismatchException("")
            fDict = HashMap<String, Int>(dict.size + 1).also { map ->
                for (i in 0 until dict.size) map[reader.getString(dict.getContainerResource(i)) ?: throw UResourceTypeMismatchException("")] = i
            }
            var start = 0
            fun matrix(d1: Int, d2: Int): Array<FloatArray> {
                val result = make2DArray(data, start, d1, d2)
                start += d1 * d2
                return result
            }
            fun vector(length: Int): FloatArray {
                val result = make1DArray(data, start, length)
                start += length
                return result
            }
            fEmbedding = matrix(dict.size + 1, embeddings)
            fForwardW = matrix(embeddings, 4 * hunits)
            fForwardU = matrix(hunits, 4 * hunits)
            fForwardB = vector(4 * hunits)
            fBackwardW = matrix(embeddings, 4 * hunits)
            fBackwardU = matrix(hunits, 4 * hunits)
            fBackwardB = vector(4 * hunits)
            fOutputW = matrix(2 * hunits, 4)
            fOutputB = vector(4)
        }
    }
    companion object {
        private fun make2DArray(data: IntArray, start: Int, d1: Int, d2: Int): Array<FloatArray> {
            var index = start
            return Array(d1) { FloatArray(d2) {
                if (index !in data.indices) throw IndexOutOfBoundsException()
                Float.fromBits(data[index++])
            } }
        }
        private fun make1DArray(data: IntArray, start: Int, length: Int): FloatArray = FloatArray(length) {
            val index = start + it
            if (index !in data.indices) throw IndexOutOfBoundsException()
            Float.fromBits(data[index])
        }
        fun createData(reader: ICUResourceBundleReader): LSTMData = LSTMData(reader)
    }
}

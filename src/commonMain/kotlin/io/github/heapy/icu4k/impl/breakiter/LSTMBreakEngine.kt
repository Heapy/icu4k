// © 2021 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.breakiter

import io.github.heapy.icu4k.impl.ICUResourceBundleReader
import io.github.heapy.icu4k.impl.ICUBinary
import io.github.heapy.icu4k.impl.RBBIDataWrapper
import io.github.heapy.icu4k.impl.RBBIRuleEngine
import io.github.heapy.icu4k.lang.UCharacter
import io.github.heapy.icu4k.lang.UProperty
import io.github.heapy.icu4k.text.CharacterIterator
import io.github.heapy.icu4k.text.UnicodeSet
import kotlin.math.exp
import kotlin.math.tanh
import io.github.heapy.icu4k.util.MissingResourceException
import io.github.heapy.icu4k.util.UResourceTypeMismatchException

/** Bidirectional LSTM dictionary segmentation; default-model resource resolution is pending. */
internal class LSTMBreakEngine(private val script: Int, set: UnicodeSet, private val data: LSTMData) : DictionaryBreakEngine() {
    init { setCharacters(set) }
    override fun hashCode(): Int = LSTMBreakEngine::class.hashCode()
    override fun handles(c: Int): Boolean = script == UCharacter.getIntPropertyValue(c, UProperty.SCRIPT)

    private fun vectorize(text: CharacterIterator, start: Int, end: Int, offsets: MutableList<Int>, indices: MutableList<Int>) {
        when (data.fType) {
            EmbeddingType.CODE_POINTS -> {
                text.setIndex(start)
                var c = text.current()
                // ICU's CODE_POINTS vectorizer deliberately iterates UTF-16 code units.
                while (c != CharacterIterator.DONE && text.getIndex() < end) {
                    offsets.add(text.getIndex()); indices.add(data.fDict[c.toString()] ?: data.fDict.size)
                    c = text.next()
                }
            }
            EmbeddingType.GRAPHEME_CLUSTER -> {
                // The character iterator factory uses these same compiled root grapheme rules.
                text.first()
                val rules = RBBIRuleEngine(characterRules, text)
                val cache = RBBIBreakCache(rules, RBBIDictionaryCache(text, characterRules, { null }, false))
                cache.reset(text.getBeginIndex(), 0)
                cache.current()
                fun next(): Int { cache.next(); return if (rules.done) -1 else rules.position }
                var last = rules.position
                // Preserve upstream next(start), which counts boundaries rather than seeking an offset.
                for (i in 0 until start) { last = next(); if (last == -1) break }
                var current = next()
                while (current != -1 && current <= end) {
                    offsets.add(last)
                    val saved = text.getIndex()
                    text.setIndex(last)
                    val segment = buildString {
                        var c = text.current()
                        while (c != CharacterIterator.DONE && text.getIndex() < current) { append(c); c = text.next() }
                    }
                    text.setIndex(saved)
                    indices.add(data.fDict[segment] ?: data.fDict.size)
                    last = current
                    current = next()
                }
            }
            EmbeddingType.UNKNOWN -> throw NullPointerException()
        }
    }
    private fun compute(w: Array<FloatArray>, u: Array<FloatArray>, b: FloatArray, x: FloatArray,
                        previousH: FloatArray, c: FloatArray): FloatArray {
        val ifco = b.copyOf()
        addDotProductTo(x, w, ifco)
        addDotProductTo(previousH, u, ifco)
        val units = b.size / 4
        sigmoid(ifco, 0, units); sigmoid(ifco, units, units)
        tanh(ifco, 2 * units, units); sigmoid(ifco, 3 * units, units)
        hadamardProductTo(ifco.copyOfRange(units, 2 * units), c)
        addHadamardProductTo(ifco.copyOf(units), ifco.copyOfRange(2 * units, 3 * units), c)
        val h = c.copyOf()
        tanh(h, 0, h.size)
        hadamardProductTo(ifco.copyOfRange(3 * units, 4 * units), h)
        return h
    }
    override fun divideUpDictionaryRange(text: CharacterIterator, rangeStart: Int, rangeEnd: Int,
                                         foundBreaks: DequeI, isPhraseBreaking: Boolean): Int {
        val beginSize = foundBreaks.size()
        if (rangeEnd - rangeStart < 4) return 0
        val offsets = ArrayList<Int>(rangeEnd - rangeStart)
        val indices = ArrayList<Int>(rangeEnd - rangeStart)
        vectorize(text, rangeStart, rangeEnd, offsets, indices)
        val units = data.fForwardU.size
        var c = FloatArray(units)
        val backward = Array(indices.size) { FloatArray(units) }
        for (i in indices.size - 1 downTo 0) {
            if (i != indices.size - 1) backward[i] = backward[i + 1].copyOf(units)
            backward[i] = compute(data.fBackwardW, data.fBackwardU, data.fBackwardB, data.fEmbedding[indices[i]], backward[i], c)
        }
        c = FloatArray(units)
        var forwardH = FloatArray(units)
        val both = FloatArray(2 * units)
        for (i in indices.indices) {
            forwardH = compute(data.fForwardW, data.fForwardU, data.fForwardB, data.fEmbedding[indices[i]], forwardH, c)
            forwardH.copyInto(both)
            backward[i].copyInto(both, units)
            val logp = data.fOutputB.copyOf()
            addDotProductTo(both, data.fOutputW, logp)
            val current = maxIndex(logp)
            if ((current == LSTMClass.BEGIN.ordinal || current == LSTMClass.SINGLE.ordinal) && i != 0) foundBreaks.push(offsets[i])
        }
        return foundBreaks.size() - beginSize
    }
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
        private val characterRules by lazy { RBBIDataWrapper.get(ICUBinary.getRequiredData("brkitr/char.brk")) }
        // Kotlin/JS represents Float expressions as JS numbers. Round the product before
        // adding it, matching Java's two distinct binary32 operations (no fused multiply-add).
        private fun product(a: Float, b: Float): Float = Float.fromBits((a * b).toRawBits())
        private fun addDotProductTo(a: FloatArray, b: Array<FloatArray>, result: FloatArray) {
            for (i in result.indices) for (j in a.indices) result[i] += product(a[j], b[j][i])
        }
        private fun hadamardProductTo(a: FloatArray, result: FloatArray) {
            for (i in result.indices) result[i] *= a[i]
        }
        private fun addHadamardProductTo(a: FloatArray, b: FloatArray, result: FloatArray) {
            for (i in result.indices) result[i] += product(a[i], b[i])
        }
        private fun sigmoid(result: FloatArray, start: Int, length: Int) {
            for (i in start until start + length) result[i] = (1.0 / (1.0 + exp(-result[i].toDouble()))).toFloat()
        }
        private fun tanh(result: FloatArray, start: Int, length: Int) {
            for (i in start until start + length) result[i] = tanh(result[i].toDouble()).toFloat()
        }
        private fun maxIndex(data: FloatArray): Int {
            var index = 0
            var max = data[0]
            for (i in 1 until data.size) if (data[i] > max) { max = data[i]; index = i }
            return index
        }
        fun create(script: Int, data: LSTMData): LSTMBreakEngine {
            val set = UnicodeSet().applyIntPropertyValue(UProperty.SCRIPT, script)
                .retainAll(UnicodeSet().applyPropertyAlias("LineBreak", "SA")).compact()
            return LSTMBreakEngine(script, set, data)
        }
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

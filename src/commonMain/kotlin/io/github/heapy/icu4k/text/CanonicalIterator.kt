// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.Norm2AllModes
import io.github.heapy.icu4k.lang.UCharacter

/** Enumerates canonical equivalents of small strings. Result order is unspecified. */
class CanonicalIterator(source: String) {
    private val allModes = Norm2AllModes.getNFCInstance()
    private val nfd = allModes.decomp
    private val nfcImpl = allModes.impl.ensureCanonIterData()
    private var source = ""
    private var done = false
    private var pieces = emptyArray<Array<String>>()
    private var current = IntArray(0)
    private val buffer = StringBuilder()
    init { setSource(source) }

    fun getSource(): String = source
    fun reset() { done = false; current.fill(0) }
    fun next(): String? {
        if (done) return null
        buffer.setLength(0)
        for (i in pieces.indices) buffer.append(pieces[i][current[i]])
        val result = buffer.toString()
        var i = current.size - 1
        while (true) {
            if (i < 0) { done = true; break }
            ++current[i]
            if (current[i] < pieces[i].size) break
            current[i] = 0
            --i
        }
        return result
    }
    fun setSource(newSource: String) {
        source = nfd.normalize(newSource)
        done = false
        if (newSource.isEmpty()) {
            pieces = arrayOf(arrayOf(""))
            current = IntArray(1)
            return
        }
        val segments = mutableListOf<String>()
        var start = 0
        var i = UTF16.findOffsetFromCodePoint(source, 1)
        while (i < source.length) {
            val cp = UTF16.charAt(source, i)
            if (nfcImpl.isCanonSegmentStarter(cp)) {
                segments.add(source.substring(start, i))
                start = i
            }
            i += UTF16.getCharCount(cp)
        }
        segments.add(source.substring(start, i))
        pieces = Array(segments.size) { getEquivalents(segments[it]) }
        current = IntArray(segments.size)
    }
    private fun getEquivalents(segment: String): Array<String> {
        val result = HashSet<String>()
        val permutations = HashSet<String>()
        for (item in getEquivalents2(segment)) {
            permutations.clear()
            permute(item, true, permutations)
            for (possible in permutations) {
                if (canonicallyEqual(possible, segment)) result.add(possible)
            }
        }
        return result.toTypedArray()
    }
    // Normalizer.compare(a, b, 0)==0: canonical equality is equality of NFD forms.
    private fun canonicallyEqual(a: String, b: String): Boolean = nfd.normalize(a) == nfd.normalize(b)
    private fun getEquivalents2(segment: String): Set<String> {
        val result = hashSetOf(segment)
        val working = StringBuilder()
        val starts = UnicodeSet()
        var i = 0
        while (i < segment.length) {
            val cp = UTF16.charAt(segment, i)
            if (nfcImpl.getCanonStartSet(cp, starts)) {
                val iterator = UnicodeSetIterator(starts)
                while (iterator.next()) {
                    val cp2 = iterator.codepoint
                    val remainder = extract(cp2, segment, i, working) ?: continue
                    val prefix = segment.substring(0, i) + UTF16.valueOf(cp2)
                    for (item in remainder) result.add(prefix + item)
                }
                if (result.size > RESULT_LIMIT) throw UnsupportedOperationException("Too many permutations")
            }
            i += UTF16.getCharCount(cp)
        }
        return result
    }
    private fun extract(comp: Int, segment: String, segmentPos: Int, buf: StringBuilder): Set<String>? {
        val decomp = nfcImpl.getDecomposition(comp) ?: UTF16.valueOf(comp)
        var ok = false
        var decompCp = UTF16.charAt(decomp, 0)
        var decompPos = UTF16.getCharCount(decompCp)
        buf.setLength(0)
        var i = segmentPos
        while (i < segment.length) {
            val cp = UTF16.charAt(segment, i)
            if (cp == decompCp) {
                if (decompPos == decomp.length) {
                    buf.append(segment.substring(i + UTF16.getCharCount(cp)))
                    ok = true
                    break
                }
                decompCp = UTF16.charAt(decomp, decompPos)
                decompPos += UTF16.getCharCount(decompCp)
            } else buf.append(UTF16.valueOf(cp))
            i += UTF16.getCharCount(cp)
        }
        if (!ok) return null
        if (buf.isEmpty()) return SET_WITH_NULL_STRING
        val remainder = buf.toString()
        if (!canonicallyEqual(UTF16.valueOf(comp) + remainder, segment.substring(segmentPos))) return null
        return getEquivalents2(remainder)
    }
    companion object {
        private const val RESULT_LIMIT = 4096
        private const val PERMUTE_DEPTH_LIMIT = 8
        private val SET_WITH_NULL_STRING = setOf("")
        @Deprecated("This API is ICU internal only")
        fun permute(source: String, skipZeros: Boolean, output: MutableSet<String>) = permute(source, skipZeros, output, 0)
        private fun permute(source: String, skipZeros: Boolean, output: MutableSet<String>, depth: Int) {
            if (depth > PERMUTE_DEPTH_LIMIT) throw UnsupportedOperationException("Stack too deep:$depth")
            if (source.length <= 2 && UTF16.countCodePoint(source) <= 1) { output.add(source); return }
            val subpermute = HashSet<String>()
            var i = 0
            while (i < source.length) {
                val cp = UTF16.charAt(source, i)
                if (!skipZeros || i == 0 || UCharacter.getCombiningClass(cp) != 0) {
                    subpermute.clear()
                    permute(source.substring(0, i) + source.substring(i + UTF16.getCharCount(cp)), skipZeros, subpermute, depth + 1)
                    val ch = UTF16.valueOf(source, i)
                    for (s in subpermute) output.add(ch + s)
                }
                i += UTF16.getCharCount(cp)
            }
        }
    }
}

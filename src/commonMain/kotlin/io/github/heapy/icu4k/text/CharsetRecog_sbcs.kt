// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2005-2012, International Business Machines Corporation and others. All Rights Reserved.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

/** Single-byte recognition uses the filtered view, including its independently computed C1 flag. */
internal class CharsetRecog_sbcs(
    private val name: String,
    private val language: String?,
    private val byteMap: IntArray,
    internal val models: List<Pair<String?, IntArray>>,
    private val c1Name: String? = null,
    private val ebcdicArabic: Boolean = false,
    private val space: Int = 0x20,
) : CharsetRecognizer() {
    override fun getName() = name
    override fun getLanguage() = language
    override fun match(input: CharsetDetectionInput): CharsetCandidate? {
        var best = -1
        var bestLanguage: String? = null
        for ((lang, grams) in models) {
            val confidence = NGramParser(grams, byteMap, ebcdicArabic).parse(input, space)
            if (confidence > best) {
                best = confidence
                bestLanguage = lang
            }
        }
        return if (best <= 0) null else CharsetCandidate(
            if (input.c1Bytes && c1Name != null) c1Name else name, best, bestLanguage,
        )
    }

    internal class NGramParser(
        private val table: IntArray,
        private val byteMap: IntArray,
        private val ebcdicArabic: Boolean = false,
    ) {
        private var byteIndex = 0
        private var ngram = 0
        private var count = 0
        private var hits = 0
        private fun addByte(b: Int) {
            ngram = ((ngram shl 8) + (b and 255)) and 0xffffff
            count++
            var index = 0
            var step = 32
            while (step > 0) {
                if (table[index + step] <= ngram) index += step
                step = step shr 1
            }
            if (table[index] > ngram) index--
            if (index >= 0 && table[index] == ngram) hits++
        }
        fun parse(input: CharsetDetectionInput, space: Int = 0x20): Int {
            var ignoreSpace = false
            fun consume(b: Int) {
                val mapped = byteMap[b]
                if (mapped != 0) {
                    if (!(mapped == space && ignoreSpace)) addByte(mapped)
                    ignoreSpace = mapped == space
                }
            }
            while (byteIndex < input.filteredLength) {
                val b = input.filtered[byteIndex].toInt() and 255
                if (ebcdicArabic && b == 0) break
                byteIndex++
                if (ebcdicArabic) {
                    val alef = when (b) {
                        0xb2, 0xb3 -> 0x47
                        0xb4, 0xb5 -> 0x49
                        0xb8, 0xb9 -> 0x56
                        else -> 0
                    }
                    consume(if (alef != 0) 0xb1 else CharsetSbcsTables.unshape[b])
                    if (alef != 0) consume(alef)
                } else consume(b)
            }
            // ICU counts a final space even after empty input or a preceding space.
            addByte(space)
            val ratio = hits.toDouble() / count
            return if (ratio > 0.33) 98 else (ratio * 300.0).toInt()
        }
    }
}

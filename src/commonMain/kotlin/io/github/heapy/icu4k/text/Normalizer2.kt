// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.Norm2AllModes
import io.github.heapy.icu4k.util.BinaryBuffer

/** Immutable, reusable Unicode normalizer backed by ICU normalization data. */
abstract class Normalizer2 protected constructor() {
    enum class Mode { COMPOSE, DECOMPOSE, FCD, COMPOSE_CONTIGUOUS }

    open fun normalize(src: CharSequence): String {
        if (src is String) {
            val span = spanQuickCheckYes(src)
            if (span == src.length) return src
            if (span != 0) {
                val dest = StringBuilder(src.length).append(src, 0, span)
                return normalizeSecondAndAppend(dest, src.subSequence(span, src.length)).toString()
            }
        }
        return normalize(src, StringBuilder(src.length)).toString()
    }
    abstract fun normalize(src: CharSequence, dest: StringBuilder): StringBuilder
    abstract fun normalize(src: CharSequence, dest: Appendable): Appendable
    abstract fun normalizeSecondAndAppend(first: StringBuilder, second: CharSequence): StringBuilder
    abstract fun append(first: StringBuilder, second: CharSequence): StringBuilder
    abstract fun getDecomposition(c: Int): String?
    open fun getRawDecomposition(c: Int): String? = null
    open fun composePair(a: Int, b: Int): Int = -1
    open fun getCombiningClass(c: Int): Int = 0
    abstract fun isNormalized(s: CharSequence): Boolean
    abstract fun quickCheck(s: CharSequence): Normalizer.QuickCheckResult
    abstract fun spanQuickCheckYes(s: CharSequence): Int
    abstract fun hasBoundaryBefore(c: Int): Boolean
    abstract fun hasBoundaryAfter(c: Int): Boolean
    abstract fun isInert(c: Int): Boolean

    companion object {
        fun getNFCInstance(): Normalizer2 = Norm2AllModes.getNFCInstance().comp
        fun getNFDInstance(): Normalizer2 = Norm2AllModes.getNFCInstance().decomp
        fun getNFKCInstance(): Normalizer2 = Norm2AllModes.getNFKCInstance().comp
        fun getNFKDInstance(): Normalizer2 = Norm2AllModes.getNFKCInstance().decomp
        fun getNFKCCasefoldInstance(): Normalizer2 = Norm2AllModes.getNFKC_CFInstance().comp
        fun getNFKCSimpleCasefoldInstance(): Normalizer2 = Norm2AllModes.getNFKC_SCFInstance().comp
        /** [data] replaces the JVM InputStream; null selects an embedded ICU data file. */
        fun getInstance(data: BinaryBuffer?, name: String, mode: Mode): Normalizer2 {
            val all = Norm2AllModes.getInstance(data, name)
            return when (mode) {
                Mode.COMPOSE -> all.comp
                Mode.DECOMPOSE -> all.decomp
                Mode.FCD -> all.fcd
                Mode.COMPOSE_CONTIGUOUS -> all.fcc
            }
        }
    }
}

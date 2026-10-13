// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.text.Normalizer
import io.github.heapy.icu4k.text.Normalizer2
import io.github.heapy.icu4k.util.BinaryBuffer
import kotlin.concurrent.atomics.AtomicReference

internal class Norm2AllModes private constructor(val impl: Normalizer2Impl) {
    val comp = ComposeNormalizer2(impl, false)
    val decomp = DecomposeNormalizer2(impl)
    val fcd = FCDNormalizer2(impl)
    val fcc = ComposeNormalizer2(impl, true)

    class NoopNormalizer2 : Normalizer2() {
        override fun normalize(src: CharSequence, dest: StringBuilder): StringBuilder {
            require(dest !== src)
            dest.setLength(0)
            return dest.append(src)
        }
        override fun normalize(src: CharSequence, dest: Appendable): Appendable {
            require(dest !== src)
            return dest.append(src)
        }
        override fun normalizeSecondAndAppend(first: StringBuilder, second: CharSequence): StringBuilder {
            require(first !== second)
            return first.append(second)
        }
        override fun append(first: StringBuilder, second: CharSequence): StringBuilder = normalizeSecondAndAppend(first, second)
        override fun getDecomposition(c: Int): String? = null
        override fun isNormalized(s: CharSequence): Boolean = true
        override fun quickCheck(s: CharSequence): Normalizer.QuickCheckResult = Normalizer.YES
        override fun spanQuickCheckYes(s: CharSequence): Int = s.length
        override fun hasBoundaryBefore(c: Int): Boolean = true
        override fun hasBoundaryAfter(c: Int): Boolean = true
        override fun isInert(c: Int): Boolean = true
    }

    abstract class Normalizer2WithImpl(val impl: Normalizer2Impl) : Normalizer2() {
        override fun normalize(src: CharSequence, dest: StringBuilder): StringBuilder {
            require(dest !== src)
            dest.setLength(0)
            normalize(src, Normalizer2Impl.ReorderingBuffer(impl, dest, src.length))
            return dest
        }
        override fun normalize(src: CharSequence, dest: Appendable): Appendable {
            require(dest !== src)
            val buffer = Normalizer2Impl.ReorderingBuffer(impl, dest, src.length)
            normalize(src, buffer)
            buffer.flush()
            return dest
        }
        protected abstract fun normalize(src: CharSequence, buffer: Normalizer2Impl.ReorderingBuffer)
        override fun normalizeSecondAndAppend(first: StringBuilder, second: CharSequence): StringBuilder = normalizeSecondAndAppend(first, second, true)
        override fun append(first: StringBuilder, second: CharSequence): StringBuilder = normalizeSecondAndAppend(first, second, false)
        fun normalizeSecondAndAppend(first: StringBuilder, second: CharSequence, doNormalize: Boolean): StringBuilder {
            require(first !== second)
            normalizeAndAppend(second, doNormalize, Normalizer2Impl.ReorderingBuffer(impl, first, first.length + second.length))
            return first
        }
        protected abstract fun normalizeAndAppend(src: CharSequence, doNormalize: Boolean, buffer: Normalizer2Impl.ReorderingBuffer)
        override fun getDecomposition(c: Int): String? = impl.getDecomposition(c)
        override fun getRawDecomposition(c: Int): String? = impl.getRawDecomposition(c)
        override fun composePair(a: Int, b: Int): Int = impl.composePair(a, b)
        override fun getCombiningClass(c: Int): Int = impl.getCC(impl.getNorm16(c))
        override fun isNormalized(s: CharSequence): Boolean = s.length == spanQuickCheckYes(s)
        override fun quickCheck(s: CharSequence): Normalizer.QuickCheckResult = if (isNormalized(s)) Normalizer.YES else Normalizer.NO
        abstract fun getQuickCheck(c: Int): Int
    }
    class DecomposeNormalizer2(impl: Normalizer2Impl) : Normalizer2WithImpl(impl) {
        override fun normalize(src: CharSequence, buffer: Normalizer2Impl.ReorderingBuffer) { impl.decompose(src, 0, src.length, buffer) }
        override fun normalizeAndAppend(src: CharSequence, doNormalize: Boolean, buffer: Normalizer2Impl.ReorderingBuffer) { impl.decomposeAndAppend(src, doNormalize, buffer) }
        override fun spanQuickCheckYes(s: CharSequence): Int = impl.decompose(s, 0, s.length, null)
        override fun getQuickCheck(c: Int): Int = if (impl.isDecompYes(impl.getNorm16(c))) 1 else 0
        override fun hasBoundaryBefore(c: Int): Boolean = impl.hasDecompBoundaryBefore(c)
        override fun hasBoundaryAfter(c: Int): Boolean = impl.hasDecompBoundaryAfter(c)
        override fun isInert(c: Int): Boolean = impl.isDecompInert(c)
    }
    class ComposeNormalizer2(impl: Normalizer2Impl, private val onlyContiguous: Boolean) : Normalizer2WithImpl(impl) {
        override fun normalize(src: CharSequence, buffer: Normalizer2Impl.ReorderingBuffer) { impl.compose(src, 0, src.length, onlyContiguous, true, buffer) }
        override fun normalizeAndAppend(src: CharSequence, doNormalize: Boolean, buffer: Normalizer2Impl.ReorderingBuffer) { impl.composeAndAppend(src, doNormalize, onlyContiguous, buffer) }
        override fun isNormalized(s: CharSequence): Boolean = impl.compose(s, 0, s.length, onlyContiguous, false, Normalizer2Impl.ReorderingBuffer(impl, StringBuilder(), 5))
        override fun quickCheck(s: CharSequence): Normalizer.QuickCheckResult {
            val result = impl.composeQuickCheck(s, 0, s.length, onlyContiguous, false)
            return if (result and 1 != 0) Normalizer.MAYBE else if (result ushr 1 == s.length) Normalizer.YES else Normalizer.NO
        }
        override fun spanQuickCheckYes(s: CharSequence): Int = impl.composeQuickCheck(s, 0, s.length, onlyContiguous, true) ushr 1
        override fun getQuickCheck(c: Int): Int = impl.getCompQuickCheck(impl.getNorm16(c))
        override fun hasBoundaryBefore(c: Int): Boolean = impl.hasCompBoundaryBefore(c)
        override fun hasBoundaryAfter(c: Int): Boolean = impl.hasCompBoundaryAfter(c, onlyContiguous)
        override fun isInert(c: Int): Boolean = impl.isCompInert(c, onlyContiguous)
    }
    class FCDNormalizer2(impl: Normalizer2Impl) : Normalizer2WithImpl(impl) {
        override fun normalize(src: CharSequence, buffer: Normalizer2Impl.ReorderingBuffer) { impl.makeFCD(src, 0, src.length, buffer) }
        override fun normalizeAndAppend(src: CharSequence, doNormalize: Boolean, buffer: Normalizer2Impl.ReorderingBuffer) { impl.makeFCDAndAppend(src, doNormalize, buffer) }
        override fun spanQuickCheckYes(s: CharSequence): Int = impl.makeFCD(s, 0, s.length, null)
        override fun getQuickCheck(c: Int): Int = if (impl.isDecompYes(impl.getNorm16(c))) 1 else 0
        override fun hasBoundaryBefore(c: Int): Boolean = impl.hasFCDBoundaryBefore(c)
        override fun hasBoundaryAfter(c: Int): Boolean = impl.hasFCDBoundaryAfter(c)
        override fun isInert(c: Int): Boolean = impl.isFCDInert(c)
    }

    companion object {
        private val nfc by lazy { Norm2AllModes(Normalizer2Impl().load("nfc.nrm")) }
        private val nfkc by lazy { Norm2AllModes(Normalizer2Impl().load("nfkc.nrm")) }
        private val nfkcCf by lazy { Norm2AllModes(Normalizer2Impl().load("nfkc_cf.nrm")) }
        private val nfkcScf by lazy { Norm2AllModes(Normalizer2Impl().load("nfkc_scf.nrm")) }
        private val cache = AtomicReference<Map<String, Norm2AllModes>>(emptyMap())
        val NOOP_NORMALIZER2 = NoopNormalizer2()
        fun getNFCInstance(): Norm2AllModes = nfc
        fun getNFKCInstance(): Norm2AllModes = nfkc
        fun getNFKC_CFInstance(): Norm2AllModes = nfkcCf
        fun getNFKC_SCFInstance(): Norm2AllModes = nfkcScf
        fun getN2WithImpl(index: Int): Normalizer2WithImpl? = when (index) {
            0 -> nfc.decomp
            1 -> nfkc.decomp
            2 -> nfc.comp
            3 -> nfkc.comp
            else -> null
        }
        fun getFCDNormalizer2(): Normalizer2 = nfc.fcd
        fun getInstance(bytes: BinaryBuffer?, name: String): Norm2AllModes {
            if (bytes == null) when (name) {
                "nfc" -> return nfc
                "nfkc" -> return nfkc
                "nfkc_cf" -> return nfkcCf
            }
            cache.load()[name]?.let { return it }
            val created = Norm2AllModes(if (bytes == null) Normalizer2Impl().load("$name.nrm") else Normalizer2Impl().load(bytes))
            while (true) {
                val old = cache.load()
                old[name]?.let { return it }
                if (cache.compareAndSet(old, old + (name to created))) return created
            }
        }
    }
}

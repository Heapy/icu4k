// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 1996-2016, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.coll

import io.github.heapy.icu4k.impl.Normalizer2Impl
import io.github.heapy.icu4k.impl.IllegalMonitorStateException
import io.github.heapy.icu4k.impl.ResourceCacheLock
import io.github.heapy.icu4k.impl.createAvailabilityLock
import io.github.heapy.icu4k.text.CollationKey
import io.github.heapy.icu4k.text.RawCollationKey

/** RuleBasedCollator comparison, sort keys and reusable work buffers. */
internal class RuleBasedCollatorCore(
    private val data: () -> CollationData?,
    private val settings: () -> CollationSettings?,
    private val assertionsEnabled: Boolean = collationIteratorClassAssertionsEnabled(RuleBasedCollatorCore::class),
) {
    private var collationBuffer: CollationBuffer? = null
    private var frozenLock: ResourceCacheLock? = null
    fun isFrozen(): Boolean = frozenLock != null
    fun freeze() {
        if (!isFrozen()) {
            frozenLock = createAvailabilityLock()
            if (collationBuffer == null) collationBuffer = CollationBuffer(data())
        }
    }
    private fun <T> withBuffer(block: (CollationBuffer) -> T): T {
        val lock = frozenLock
        if (lock != null) return lock.withLock { block(collationBuffer!!) }
        try {
            if (collationBuffer == null) collationBuffer = CollationBuffer(data())
            return block(collationBuffer!!)
        } finally {
            // Source rechecks frozen state on release. Freezing from a user
            // CharSequence callback therefore attempts to unlock an unheld lock.
            if (frozenLock != null) throw IllegalMonitorStateException(null)
        }
    }
    fun getCollationKey(source: String?): CollationKey? {
        if (source == null) return null
        return withBuffer { buffer ->
            buffer.rawCollationKey = getRawCollationKey(source, buffer.rawCollationKey, buffer)
            CollationKey(source, buffer.rawCollationKey)
        }
    }
    fun getRawCollationKey(source: String?, key: RawCollationKey?): RawCollationKey? {
        if (source == null) return null
        return withBuffer { getRawCollationKey(source, key, it) }
    }
    private class CollationKeyByteSink(val key: RawCollationKey) : CollationKeys.SortKeyByteSink(key.bytes) {
        override fun AppendBeyondCapacity(bytes: ByteArray, start: Int, n: Int, length: Int) {
            if (Resize(n,length)) copy(bytes,start,buffer_,length,n)
        }
        override fun Resize(appendCapacity: Int, length: Int): Boolean {
            var newCapacity = 2 * buffer_!!.size
            val altCapacity = length + 2 * appendCapacity
            if (newCapacity < altCapacity) newCapacity = altCapacity
            if (newCapacity < 200) newCapacity = 200
            val newBytes = ByteArray(newCapacity)
            copy(buffer_,0,newBytes,0,length)
            key.bytes = newBytes
            buffer_ = newBytes
            return true
        }
    }
    private fun getRawCollationKey(source: CharSequence, key: RawCollationKey?, buffer: CollationBuffer): RawCollationKey {
        val result = key ?: RawCollationKey(simpleKeyLengthEstimate(source))
        if (key != null && key.bytes == null) key.bytes = ByteArray(simpleKeyLengthEstimate(source))
        val sink = CollationKeyByteSink(result)
        writeSortKey(source,sink,buffer)
        result.size = sink.NumberOfBytesAppended()
        return result
    }
    private fun simpleKeyLengthEstimate(source: CharSequence): Int = 2 * source.length + 10
    private fun writeSortKey(s: CharSequence, sink: CollationKeyByteSink, buffer: CollationBuffer) {
        val numeric = settings()!!.isNumeric()
        val iter = if (settings()!!.dontCheckFCD()) {
            buffer.leftUTF16CollIter.setText(numeric,s,0); buffer.leftUTF16CollIter
        } else {
            buffer.leftFCDUTF16Iter.setText(numeric,s,0); buffer.leftFCDUTF16Iter
        }
        CollationKeys.writeSortKeyUpToQuaternary(iter,data()!!.compressibleBytes,settings(),sink,
            Collation.PRIMARY_LEVEL,CollationKeys.SIMPLE_LEVEL_FALLBACK,true)
        if (settings()!!.getStrength() == 15) writeIdenticalLevel(s,sink)
        sink.Append(Collation.TERMINATOR_BYTE)
    }
    private fun writeIdenticalLevel(s: CharSequence, sink: CollationKeyByteSink) {
        val nfdQCYesLimit = data()!!.nfcImpl!!.decompose(s,0,s.length,null)
        sink.Append(Collation.LEVEL_SEPARATOR_BYTE)
        sink.key.size = sink.NumberOfBytesAppended()
        var prev = 0
        if (nfdQCYesLimit != 0) prev = BOCSU.writeIdenticalLevelRun(prev,s,0,nfdQCYesLimit,sink.key)
        if (nfdQCYesLimit < s.length) {
            val estimate = s.length - nfdQCYesLimit
            val nfd = StringBuilder()
            data()!!.nfcImpl!!.decompose(s,nfdQCYesLimit,s.length,nfd,estimate)
            BOCSU.writeIdenticalLevelRun(prev,nfd,0,nfd.length,sink.key)
        }
        sink.setBufferAndAppended(sink.key.bytes,sink.key.size)
    }
    fun internalGetCEs(str: CharSequence?): LongArray = withBuffer { buffer ->
        val numeric = settings()!!.isNumeric()
        val iter = if (settings()!!.dontCheckFCD()) {
            buffer.leftUTF16CollIter.setText(numeric,str,0); buffer.leftUTF16CollIter
        } else {
            buffer.leftFCDUTF16Iter.setText(numeric,str,0); buffer.leftFCDUTF16Iter
        }
        val length = iter.fetchCEs() - 1
        if (assertionsEnabled && !(length >= 0 && iter.getCE(length) == Collation.NO_CE)) throw AssertionError()
        iter.getCEs()!!.copyOfRange(0,length)
    }
    fun doCompare(left: CharSequence?, right: CharSequence?): Int {
        if (left === right) return Collation.EQUAL
        var equalPrefixLength = 0
        while (true) {
            if (equalPrefixLength == left!!.length) {
                if (equalPrefixLength == right!!.length) return Collation.EQUAL
                break
            } else if (equalPrefixLength == right!!.length || left[equalPrefixLength] != right[equalPrefixLength]) break
            ++equalPrefixLength
        }
        val ro = settings()!!
        val numeric = ro.isNumeric()
        if (equalPrefixLength > 0) {
            if ((equalPrefixLength != left.length && data()!!.isUnsafeBackward(left[equalPrefixLength].code,numeric)) ||
                (equalPrefixLength != right!!.length && data()!!.isUnsafeBackward(right[equalPrefixLength].code,numeric))) {
                do { --equalPrefixLength } while (equalPrefixLength > 0 && data()!!.isUnsafeBackward(left[equalPrefixLength].code,numeric))
            }
        }
        var result = if (ro.fastLatinOptions >= 0 &&
            (equalPrefixLength == left.length || left[equalPrefixLength] <= CollationFastLatin.LATIN_MAX.toChar()) &&
            (equalPrefixLength == right!!.length || right[equalPrefixLength] <= CollationFastLatin.LATIN_MAX.toChar())) {
            CollationFastLatin.compareUTF16(data()!!.fastLatinTable,ro.fastLatinPrimaries,ro.fastLatinOptions,left,right,equalPrefixLength)
        } else CollationFastLatin.BAIL_OUT_RESULT
        if (result == CollationFastLatin.BAIL_OUT_RESULT) result = withBuffer { buffer ->
            if (ro.dontCheckFCD()) {
                buffer.leftUTF16CollIter.setText(numeric,left,equalPrefixLength)
                buffer.rightUTF16CollIter.setText(numeric,right,equalPrefixLength)
                CollationCompare.compareUpToQuaternary(buffer.leftUTF16CollIter,buffer.rightUTF16CollIter,ro)
            } else {
                buffer.leftFCDUTF16Iter.setText(numeric,left,equalPrefixLength)
                buffer.rightFCDUTF16Iter.setText(numeric,right,equalPrefixLength)
                CollationCompare.compareUpToQuaternary(buffer.leftFCDUTF16Iter,buffer.rightFCDUTF16Iter,ro)
            }
        }
        if (result != Collation.EQUAL || ro.getStrength() < 15) return result
        return withBuffer { buffer ->
            val nfcImpl = data()!!.nfcImpl
            if (ro.dontCheckFCD()) {
                buffer.leftUTF16NFDIter.setText(left,equalPrefixLength)
                buffer.rightUTF16NFDIter.setText(right,equalPrefixLength)
                compareNFDIter(nfcImpl,buffer.leftUTF16NFDIter,buffer.rightUTF16NFDIter)
            } else {
                buffer.leftFCDUTF16NFDIter.setText(nfcImpl,left,equalPrefixLength)
                buffer.rightFCDUTF16NFDIter.setText(nfcImpl,right,equalPrefixLength)
                compareNFDIter(nfcImpl,buffer.leftFCDUTF16NFDIter,buffer.rightFCDUTF16NFDIter)
            }
        }
    }
    private abstract class NFDIterator {
        private var decomp: String? = null
        private var index = 0
        fun reset() { index = -1 }
        fun nextCodePoint(): Int {
            if (index >= 0) {
                if (index == decomp!!.length) index = -1
                else { val c = codePointAt(decomp!!,index); index += charCount(c); return c }
            }
            return nextRawCodePoint()
        }
        fun nextDecomposedCodePoint(nfcImpl: Normalizer2Impl?, c: Int): Int {
            if (index >= 0) return c
            decomp = nfcImpl!!.getDecomposition(c)
            if (decomp == null) return c
            val result = codePointAt(decomp!!,0)
            index = charCount(result)
            return result
        }
        protected abstract fun nextRawCodePoint(): Int
    }
    private open class UTF16NFDIterator : NFDIterator() {
        protected var s: CharSequence? = null
        protected var pos = 0
        fun setText(seq: CharSequence?, start: Int) { reset(); s = seq; pos = start }
        override fun nextRawCodePoint(): Int {
            if (pos == s!!.length) return Collation.SENTINEL_CP
            val c = codePointAt(s!!,pos)
            pos += charCount(c)
            return c
        }
    }
    private class FCDUTF16NFDIterator : UTF16NFDIterator() {
        private var str: StringBuilder? = null
        fun setText(nfcImpl: Normalizer2Impl?, seq: CharSequence?, start: Int) {
            reset()
            val spanLimit = nfcImpl!!.makeFCD(seq!!,start,seq.length,null)
            if (spanLimit == seq.length) { s = seq; pos = start }
            else {
                if (str == null) str = StringBuilder() else str!!.setLength(0)
                str!!.append(seq,start,spanLimit)
                val buffer = Normalizer2Impl.ReorderingBuffer(nfcImpl,str!!,seq.length-start)
                nfcImpl.makeFCD(seq,spanLimit,seq.length,buffer)
                s = str; pos = 0
            }
        }
    }
    private class CollationBuffer(data: CollationData?) {
        val leftUTF16CollIter = UTF16CollationIterator(data)
        val rightUTF16CollIter = UTF16CollationIterator(data)
        val leftFCDUTF16Iter = FCDUTF16CollationIterator(data)
        val rightFCDUTF16Iter = FCDUTF16CollationIterator(data)
        val leftUTF16NFDIter = UTF16NFDIterator()
        val rightUTF16NFDIter = UTF16NFDIterator()
        val leftFCDUTF16NFDIter = FCDUTF16NFDIterator()
        val rightFCDUTF16NFDIter = FCDUTF16NFDIterator()
        var rawCollationKey: RawCollationKey? = null
    }
    companion object {
        private fun compareNFDIter(nfcImpl: Normalizer2Impl?, left: NFDIterator, right: NFDIterator): Int {
            while (true) {
                var l = left.nextCodePoint(); var r = right.nextCodePoint()
                if (l == r) { if (l < 0) break; continue }
                l = if (l < 0) -2 else if (l == 0xfffe) -1 else left.nextDecomposedCodePoint(nfcImpl,l)
                r = if (r < 0) -2 else if (r == 0xfffe) -1 else right.nextDecomposedCodePoint(nfcImpl,r)
                if (l < r) return Collation.LESS
                if (l > r) return Collation.GREATER
            }
            return Collation.EQUAL
        }
        private fun charCount(c: Int): Int = if (c >= 0x10000) 2 else 1
        private fun codePointAt(s: CharSequence, i: Int): Int {
            if (i < 0 || i >= s.length) throw IndexOutOfBoundsException()
            val c = s[i]
            if (c.isHighSurrogate() && i + 1 < s.length && s[i+1].isLowSurrogate()) return 0x10000 + ((c.code-0xd800) shl 10) + s[i+1].code-0xdc00
            return c.code
        }
        private fun copy(src: ByteArray?, start: Int, dest: ByteArray?, offset: Int, length: Int) {
            val s = src!!; val d = dest!!
            if (start < 0 || offset < 0 || length < 0 || start > s.size-length || offset > d.size-length) throw IndexOutOfBoundsException()
            s.copyInto(d,offset,start,start+length)
        }
    }
}

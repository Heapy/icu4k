// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 1996-2016, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.coll

import io.github.heapy.icu4k.impl.CharacterIteratorWrapper
import io.github.heapy.icu4k.text.CharacterIterator
import io.github.heapy.icu4k.text.UCharacterIterator
import io.github.heapy.icu4k.util.CloneNotSupportedException

/** Old-style 32-bit CE protocol. The public collator supplies its current state. */
internal class CollationElementIteratorCore(
    private val owner: Any,
    private val data: () -> CollationData?,
    private val settings: () -> CollationSettings?,
    private val maxExpansions: () -> Map<Int, Int>?,
    private val assertionsEnabled: Boolean = collationIteratorClassAssertionsEnabled(CollationElementIteratorCore::class),
) {
    private var iter: CollationIterator? = null
    private var otherHalf = 0
    private var dir = 0
    private var offsets: UVector32? = null
    private var string: String? = null

    fun getOffset(): Int {
        if (dir < 0 && offsets != null && !offsets!!.isEmpty()) {
            var i = iter!!.getCEsLength()
            if (otherHalf != 0) ++i
            checkAssertion { i < offsets!!.size() }
            return offsets!!.elementAti(i)
        }
        return iter!!.getOffset()
    }
    fun next(): Int {
        if (dir > 1) {
            if (otherHalf != 0) { val oh = otherHalf; otherHalf = 0; return oh }
        } else if (dir == 1 || dir == 0) {
            dir = 2
        } else throw IllegalStateException("Illegal change of direction")
        iter!!.clearCEsIfNoneRemaining()
        val ce = iter!!.nextCE()
        if (ce == Collation.NO_CE) return NULLORDER
        val p = ce ushr 32
        val lower32 = ce.toInt()
        val first = getFirstHalf(p, lower32)
        val second = getSecondHalf(p, lower32)
        if (second != 0) otherHalf = second or 0xc0
        return first
    }
    fun previous(): Int {
        if (dir < 0) {
            if (otherHalf != 0) { val oh = otherHalf; otherHalf = 0; return oh }
        } else if (dir == 0) {
            iter!!.resetToOffset(string!!.length)
            dir = -1
        } else if (dir == 1) dir = -1
        else throw IllegalStateException("Illegal change of direction")
        if (offsets == null) offsets = UVector32()
        val limitOffset = if (iter!!.getCEsLength() == 0) iter!!.getOffset() else 0
        val ce = iter!!.previousCE(offsets)
        if (ce == Collation.NO_CE) return NULLORDER
        val p = ce ushr 32
        val lower32 = ce.toInt()
        val first = getFirstHalf(p, lower32)
        val second = getSecondHalf(p, lower32)
        if (second != 0) {
            if (offsets!!.isEmpty()) {
                offsets!!.addElement(iter!!.getOffset())
                offsets!!.addElement(limitOffset)
            }
            otherHalf = first
            return second or 0xc0
        }
        return first
    }
    fun reset() { iter!!.resetToOffset(0); otherHalf = 0; dir = 0 }
    private fun isUnsafe(c: Int): Boolean = data()!!.isUnsafeBackward(c, settings()!!.isNumeric())
    fun setOffset(newOffset: Int) {
        var result = newOffset
        if (0 < result && result < string!!.length) {
            val text = string!!
            var offset = result
            do {
                val c = text[offset]
                if (!isUnsafe(c.code) || (c.isHighSurrogate() && !isUnsafe(codePointAt(text, offset)))) break
                --offset
            } while (offset > 0)
            if (offset < result) {
                var lastSafeOffset = offset
                do {
                    iter!!.resetToOffset(lastSafeOffset)
                    do { iter!!.nextCE(); offset = iter!!.getOffset() } while (offset == lastSafeOffset)
                    if (offset <= result) lastSafeOffset = offset
                } while (offset < result)
                result = lastSafeOffset
            }
        }
        iter!!.resetToOffset(result)
        otherHalf = 0
        dir = 1
    }
    fun setText(source: String?) {
        string = source
        val numeric = settings()!!.isNumeric()
        val newIter = if (settings()!!.dontCheckFCD()) UTF16CollationIterator(data(), numeric, string, 0)
            else FCDUTF16CollationIterator(data(), numeric, string, 0)
        iter = newIter
        otherHalf = 0
        dir = 0
    }
    fun setText(source: UCharacterIterator?) {
        string = source!!.getText()
        val src = try { source.clone() } catch (_: CloneNotSupportedException) {
            setText(source.getText()); return
        }
        src.setToStart()
        installIterator(src)
    }
    fun setText(source: CharacterIterator?) {
        val src = CharacterIteratorWrapper(source!!)
        src.setToStart()
        string = src.getText()
        installIterator(src)
    }
    private fun installIterator(src: UCharacterIterator) {
        val numeric = settings()!!.isNumeric()
        val newIter = if (settings()!!.dontCheckFCD()) IterCollationIterator(data(), numeric, src)
            else FCDIterCollationIterator(data(), numeric, src, 0)
        iter = newIter
        otherHalf = 0
        dir = 0
    }
    fun getMaxExpansion(ce: Int): Int = getMaxExpansion(maxExpansions(), ce)
    fun getOwner(): Any = owner
    private fun normalizeDir(): Int = if (dir == 1) 0 else dir
    override fun equals(other: Any?): Boolean {
        if (other === this) return true
        return other is CollationElementIteratorCore && owner == other.owner && otherHalf == other.otherHalf &&
            normalizeDir() == other.normalizeDir() && string!!.equals(other.string) && iter!!.equals(other.iter)
    }
    override fun hashCode(): Int { if (assertionsEnabled) throw AssertionError("hashCode not designed"); return 42 }
    private inline fun checkAssertion(condition: () -> Boolean) { if (assertionsEnabled && !condition()) throw AssertionError() }

    private class MaxExpSink(private val maxExpansions: MutableMap<Int, Int>, private val assertionsEnabled: Boolean) : ContractionsAndExpansions.CESink {
        override fun handleCE(ce: Long) {}
        override fun handleExpansion(ces: LongArray?, start: Int, length: Int) {
            if (length <= 1) return
            var count = 0
            for (i in 0..<length) count += if (ceNeedsTwoParts(ces!![start + i])) 2 else 1
            val ce = ces!![start + length - 1]
            val p = ce ushr 32
            val lower32 = ce.toInt()
            var lastHalf = getSecondHalf(p, lower32)
            if (lastHalf == 0) {
                lastHalf = getFirstHalf(p, lower32)
                if (assertionsEnabled && lastHalf == 0) throw AssertionError()
            } else lastHalf = lastHalf or 0xc0
            val oldCount = maxExpansions[lastHalf]
            if (oldCount == null || count > oldCount) maxExpansions[lastHalf] = count
        }
    }
    companion object {
        const val NULLORDER = -1
        const val IGNORABLE = 0
        fun primaryOrder(ce: Int): Int = (ce ushr 16) and 0xffff
        fun secondaryOrder(ce: Int): Int = (ce ushr 8) and 0xff
        fun tertiaryOrder(ce: Int): Int = ce and 0xff
        fun getFirstHalf(p: Long, lower32: Int): Int = (p.toInt() and -0x10000) or ((lower32 shr 16) and 0xff00) or ((lower32 shr 8) and 0xff)
        fun getSecondHalf(p: Long, lower32: Int): Int = (p.toInt() shl 16) or ((lower32 shr 8) and 0xff00) or (lower32 and 0x3f)
        fun ceNeedsTwoParts(ce: Long): Boolean = (ce and 0xffff00ff003fL) != 0L
        fun computeMaxExpansions(data: CollationData?): MutableMap<Int, Int> {
            val result = HashMap<Int, Int>()
            val sink = MaxExpSink(result, collationIteratorClassAssertionsEnabled(MaxExpSink::class))
            ContractionsAndExpansions(null, null, sink, true).forData(data)
            return result
        }
        fun getMaxExpansion(maxExpansions: Map<Int, Int>?, order: Int): Int {
            if (order == 0) return 1
            maxExpansions?.get(order)?.let { return it }
            return if ((order and 0xc0) == 0xc0) 2 else 1
        }
        private fun codePointAt(text: String, index: Int): Int {
            val c = text[index]
            if (c.isHighSurrogate() && index + 1 < text.length && text[index + 1].isLowSurrogate()) {
                return 0x10000 + ((c.code - 0xd800) shl 10) + text[index + 1].code - 0xdc00
            }
            return c.code
        }
    }
}

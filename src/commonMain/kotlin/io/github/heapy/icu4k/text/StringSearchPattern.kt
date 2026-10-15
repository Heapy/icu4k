// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 1996-2016, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.coll.settingsNegativeArraySizeException

internal class StringSearchPattern(var text_: String?) {
    var PCE_: LongArray? = null
    var PCELength_ = 0
    var CE_: IntArray? = null
    var CELength_ = 0
}

/** StringSearch pattern tables and cached CE filtering attributes. */
internal class StringSearchPatternCompiler(val pattern_: StringSearchPattern, var collator_: RuleBasedCollator?) {
    var strength_ = collator_!!.getStrength()
    var ceMask_ = getMask(strength_)
    var toShift_ = collator_!!.isAlternateHandlingShifted()
    var variableTop_ = collator_!!.getVariableTop()
    var utilIter_: CollationElementIterator? = null

    fun getCE(sourcece: Int): Int {
        var ce = sourcece and ceMask_
        if (toShift_) {
            if (variableTop_ > ce) ce = if (strength_ >= Collator.QUATERNARY) ce and -0x10000 else CollationElementIterator.IGNORABLE
        } else if (strength_ >= Collator.QUATERNARY && ce == CollationElementIterator.IGNORABLE) ce = 0xffff
        return ce
    }
    fun initializePatternCETable(): Int {
        var table = IntArray(256)
        val length = pattern_.text_!!.length
        val it = prepareIterator()
        return fillCE(table,length,it)
    }
    private fun fillCE(initial: IntArray, length: Int, it: CollationElementIterator): Int {
        var table = initial
        var offset = 0
        var result = 0
        while (true) {
            val ce = it.next()
            if (ce == CollationElementIterator.NULLORDER) break
            val filtered = getCE(ce)
            if (filtered != CollationElementIterator.IGNORABLE) {
                table = addToIntArray(table,offset,filtered,length-it.getOffset()+1)
                offset++
            }
            result += it.getMaxExpansion(ce)-1
        }
        table = addToIntArray(table,offset,0,1)
        pattern_.CE_ = table
        pattern_.CELength_ = offset
        return result
    }
    private fun prepareIterator(): CollationElementIterator {
        val existing = utilIter_
        if (existing == null) {
            val result = CollationElementIterator(pattern_.text_,collator_!!)
            utilIter_ = result
            return result
        }
        existing.setText(pattern_.text_)
        return existing
    }
    fun initializePatternPCETable(): Int {
        var table = LongArray(256)
        val length = pattern_.text_!!.length
        val it = prepareIterator()
        var offset = 0
        val processed = StringSearchPCE(it)
        while (true) {
            val pce = processed.nextProcessed(null)
            if (pce == StringSearchPCE.PROCESSED_NULLORDER) break
            table = addToLongArray(table,offset,pce,length-it.getOffset()+1)
            offset++
        }
        table = addToLongArray(table,offset,0,1)
        pattern_.PCE_ = table
        pattern_.PCELength_ = offset
        return 0
    }
    fun initializePattern(): Int { pattern_.PCE_ = null; return initializePatternCETable() }
    fun initialize() { initializePattern() }
    fun checkIdentical(target: CharacterIterator?, start: Int, end: Int): Boolean {
        if (strength_ != Collator.IDENTICAL) return true
        var text = getString(target,start,end-start)
        if (Normalizer.quickCheck(text,Normalizer.NFD,0) === Normalizer.NO) text = Normalizer.decompose(text,false)
        var pattern = pattern_.text_
        if (Normalizer.quickCheck(pattern!!,Normalizer.NFD,0) === Normalizer.NO) pattern = Normalizer.decompose(pattern,false)
        return text == pattern
    }
    companion object {
        fun getMask(strength: Int): Int = when (strength) { Collator.PRIMARY -> -0x10000; Collator.SECONDARY -> -0x100; else -> -1 }
        fun addToIntArray(destination: IntArray?, offset: Int, value: Int, increments: Int): IntArray {
            var result = destination!!
            if (offset >= result.size) {
                val length = offset+increments
                if (length < 0) throw settingsNegativeArraySizeException()
                val next = IntArray(length)
                if (offset > result.size || offset > next.size) throw IndexOutOfBoundsException()
                result.copyInto(next,0,0,offset)
                result = next
            }
            if (offset < 0 || offset >= result.size) throw IndexOutOfBoundsException()
            result[offset] = value
            return result
        }
        fun addToLongArray(destination: LongArray?, offset: Int, value: Long, increments: Int): LongArray {
            var result = destination!!
            if (offset >= result.size) {
                val length = offset+increments
                if (length < 0) throw settingsNegativeArraySizeException()
                val next = LongArray(length)
                if (offset > result.size || offset > next.size) throw IndexOutOfBoundsException()
                result.copyInto(next,0,0,offset)
                result = next
            }
            if (offset < 0 || offset >= result.size) throw IndexOutOfBoundsException()
            result[offset] = value
            return result
        }
        fun getString(text: CharacterIterator?, start: Int, length: Int): String {
            if (length < 0) throw settingsNegativeArraySizeException()
            val result = StringBuilder(length)
            val offset = text!!.getIndex()
            text.setIndex(start)
            for (i in 0..<length) { result.append(text.current()); text.next() }
            text.setIndex(offset)
            return result.toString()
        }
        fun codePointAt(iter: CharacterIterator?, index: Int): Int {
            val saved = iter!!.getIndex()
            val c = iter.setIndex(index)
            var cp = c.code
            if (c.isHighSurrogate()) {
                val trail = iter.next()
                if (trail.isLowSurrogate()) cp = 0x10000 + ((c.code-0xd800) shl 10) + trail.code-0xdc00
            }
            iter.setIndex(saved)
            return cp
        }
        fun codePointBefore(iter: CharacterIterator?, index: Int): Int {
            val saved = iter!!.getIndex()
            iter.setIndex(index)
            val c = iter.previous()
            var cp = c.code
            if (c.isLowSurrogate()) {
                val lead = iter.previous()
                if (lead.isHighSurrogate()) cp = 0x10000 + ((lead.code-0xd800) shl 10) + c.code-0xdc00
            }
            iter.setIndex(saved)
            return cp
        }
        fun isOutOfBounds(textstart: Int, textlimit: Int, offset: Int): Boolean = offset < textstart || offset > textlimit
        fun nextBoundaryAfter(external: BreakIterator?, internal: BreakIterator?, startIndex: Int): Int = (external ?: internal)?.following(startIndex) ?: startIndex
        fun isBreakBoundary(external: BreakIterator?, internal: BreakIterator?, index: Int): Boolean = (external ?: internal)?.isBoundary(index) ?: false
    }
}

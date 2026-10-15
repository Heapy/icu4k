// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 1996-2016, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.coll.collationIteratorClassAssertionsEnabled
import io.github.heapy.icu4k.util.ICUException
import io.github.heapy.icu4k.util.ULocale

/** Language-sensitive search over ICU collation elements and grapheme boundaries. */
class StringSearch(pattern: String?, target: CharacterIterator?, collator: RuleBasedCollator?, breakiter: BreakIterator?) : SearchIterator(target,breakiter) {
    private val compiler: StringSearchPatternCompiler
    private var textIter_: CollationElementIterator? = null
    private var textProcessedIter_: StringSearchPCE? = null
    private val nfd_: Normalizer2
    private val pattern_ get() = compiler.pattern_
    init {
        if (collator!!.getNumericCollation()) throw UnsupportedOperationException("Numeric collation is not supported by StringSearch")
        compiler = StringSearchPatternCompiler(StringSearchPattern(pattern),collator)
        nfd_ = Normalizer2.getNFDInstance()
        search_.setMatchedLength(0); search_.matchedIndex_ = DONE
        textIter_ = CollationElementIterator(target,collator)
        val locale = collator.getLocale(ULocale.VALID_LOCALE)
        search_.internalBreakIter_ = BreakIterator.getCharacterInstance(locale ?: ULocale.ROOT)
        search_.internalBreakIter_!!.setText(target!!.clone())
        compiler.initialize()
    }
    constructor(pattern: String?, target: CharacterIterator?, collator: RuleBasedCollator?) : this(pattern,target,collator,null)
    constructor(pattern: String?, target: CharacterIterator?, locale: ULocale?) : this(pattern,target,Collator.getInstance(locale) as RuleBasedCollator,null)
    constructor(pattern: String?, target: String?) : this(pattern,StringCharacterIterator(target!!),Collator.getInstance() as RuleBasedCollator,null)
    fun getCollator(): RuleBasedCollator = compiler.collator_!!
    fun setCollator(collator: RuleBasedCollator?) {
        if (collator == null) throw IllegalArgumentException("Collator can not be null")
        compiler.collator_ = collator
        compiler.ceMask_ = StringSearchPatternCompiler.getMask(collator.getStrength())
        val locale = collator.getLocale(ULocale.VALID_LOCALE)
        search_.internalBreakIter_ = BreakIterator.getCharacterInstance(locale ?: ULocale.ROOT)
        search_.internalBreakIter_!!.setText(search_.text()!!.clone())
        compiler.toShift_ = collator.isAlternateHandlingShifted()
        compiler.variableTop_ = collator.getVariableTop()
        // Source uses the pattern for both cursors here, and does not refresh strength_.
        textIter_ = CollationElementIterator(pattern_.text_,collator)
        compiler.utilIter_ = CollationElementIterator(pattern_.text_,collator)
        compiler.initialize()
    }
    fun getPattern(): String? = pattern_.text_
    fun setPattern(pattern: String?) {
        if (pattern == null || pattern.isEmpty()) throw IllegalArgumentException("Pattern to search for can not be null or of length 0")
        pattern_.text_ = pattern
        compiler.initialize()
    }
    fun isCanonical(): Boolean = search_.isCanonicalMatch_
    fun setCanonical(allowCanonical: Boolean) { search_.isCanonicalMatch_ = allowCanonical }
    override fun setTarget(text: CharacterIterator?) { super.setTarget(text); textIter_!!.setText(text) }
    override fun getIndex(): Int {
        val result = textIter_!!.getOffset()
        return if (StringSearchPatternCompiler.isOutOfBounds(search_.beginIndex(),search_.endIndex(),result)) DONE else result
    }
    override fun setIndex(position: Int) { super.setIndex(position); textIter_!!.setOffset(position) }
    override fun reset() {
        val c = compiler.collator_!!
        var same = true
        val strength = c.getStrength()
        if ((compiler.strength_ < Collator.QUATERNARY && strength >= Collator.QUATERNARY) ||
            (compiler.strength_ >= Collator.QUATERNARY && strength < Collator.QUATERNARY)) same = false
        compiler.strength_ = c.getStrength()
        val mask = StringSearchPatternCompiler.getMask(compiler.strength_)
        if (compiler.ceMask_ != mask) { compiler.ceMask_ = mask; same = false }
        val shift = c.isAlternateHandlingShifted()
        if (compiler.toShift_ != shift) { compiler.toShift_ = shift; same = false }
        val top = c.getVariableTop()
        if (compiler.variableTop_ != top) { compiler.variableTop_ = top; same = false }
        if (!same) compiler.initialize()
        textIter_!!.setText(search_.text())
        search_.setMatchedLength(0); search_.matchedIndex_ = DONE
        search_.isOverlap_ = false; search_.isCanonicalMatch_ = false
        search_.elementComparisonType_ = ElementComparisonType.STANDARD_ELEMENT_COMPARISON
        search_.isForwardSearching_ = true; search_.reset_ = true
    }
    override fun handleNext(position: Int): Int {
        if (pattern_.CELength_ == 0) {
            search_.matchedIndex_ = if (search_.matchedIndex_ == DONE) getIndex() else search_.matchedIndex_+1
            search_.setMatchedLength(0)
            textIter_!!.setOffset(search_.matchedIndex_)
            if (search_.matchedIndex_ == search_.endIndex()) search_.matchedIndex_ = DONE
        } else {
            if (search_.matchedLength() <= 0) search_.matchedIndex_ = position-1
            textIter_!!.setOffset(position)
            // The source exact/canonical wrappers call the same linear implementation.
            handleNextCommonImpl()
            textIter_!!.setOffset(if (search_.matchedIndex_ == DONE) search_.endIndex() else search_.matchedIndex_)
            return search_.matchedIndex_
        }
        return DONE
    }
    override fun handlePrevious(position: Int): Int {
        if (pattern_.CELength_ == 0) {
            search_.matchedIndex_ = if (search_.matchedIndex_ == DONE) getIndex() else search_.matchedIndex_
            if (search_.matchedIndex_ == search_.beginIndex()) setMatchNotFound()
            else {
                search_.matchedIndex_--; textIter_!!.setOffset(search_.matchedIndex_); search_.setMatchedLength(0)
            }
        } else {
            textIter_!!.setOffset(position)
            handlePreviousCommonImpl()
        }
        return search_.matchedIndex_
    }
    override fun setMatchNotFound() {
        super.setMatchNotFound()
        textIter_!!.setOffset(if (search_.isForwardSearching_) search_.text()!!.getEndIndex() else 0)
    }
    private fun initTextProcessedIter(): Boolean {
        if (textProcessedIter_ == null) textProcessedIter_ = StringSearchPCE(textIter_) else textProcessedIter_!!.init(textIter_)
        return true
    }
    private fun buffer(): StringSearchCEBuffer = StringSearchCEBuffer(pattern_.PCELength_,pattern_.text_,search_.elementComparisonType_,
        { initTextProcessedIter() },{ textProcessedIter_ },assertionsEnabled)
    private fun nextBoundaryAfter(start: Int): Int = StringSearchPatternCompiler.nextBoundaryAfter(search_.breakIter(),search_.internalBreakIter_,start)
    private fun isBreakBoundary(index: Int): Boolean = StringSearchPatternCompiler.isBreakBoundary(search_.breakIter(),search_.internalBreakIter_,index)
    private fun checkIdentical(start: Int,end: Int): Boolean = compiler.checkIdentical(targetText,start,end)
    private fun pceAt(index: Int): Long {
        val a = pattern_.PCE_!!
        if (index < 0 || index >= a.size) throw IndexOutOfBoundsException()
        return a[index]
    }
    private class Match { var start_ = -1; var limit_ = -1 }
    private fun search(startIdx: Int, m: Match?): Boolean {
        if (pattern_.CELength_ == 0 || startIdx < search_.beginIndex() || startIdx > search_.endIndex()) {
            throw IllegalArgumentException("search($startIdx, m) - expected position to be between ${search_.beginIndex()} and ${search_.endIndex()}")
        }
        if (pattern_.PCE_ == null) compiler.initializePatternPCETable()
        textIter_!!.setOffset(startIdx)
        val ceb = buffer()
        var targetIx = 0
        var targetCEI: StringSearchCEBuffer.CEI? = null
        var found: Boolean
        var mStart = -1; var mLimit = -1
        while (true) {
            found = true
            var offset = 0
            var patCE = 0L
            val first = ceb.get(targetIx) ?: throw ICUException("CEBuffer.get($targetIx) returned null.")
            var patIx = 0
            while (patIx < pattern_.PCELength_) {
                patCE = pceAt(patIx)
                targetCEI = ceb.get(targetIx+patIx+offset)
                val match = StringSearchPCE.compareCE64s(targetCEI!!.ce_,patCE,search_.elementComparisonType_)
                if (match == StringSearchPCE.CE_NO_MATCH) { found = false; break }
                else if (match > StringSearchPCE.CE_NO_MATCH) {
                    if (match == StringSearchPCE.CE_SKIP_TARG) { patIx--; offset++ } else offset--
                }
                patIx++
            }
            offset += pattern_.PCELength_
            if (!found && (targetCEI == null || targetCEI.ce_ != StringSearchPCE.PROCESSED_NULLORDER)) { targetIx++; continue }
            if (!found) break
            val last = ceb.get(targetIx+offset-1)!!
            mStart = first.lowIndex_
            val minLimit = last.lowIndex_
            var maxLimit: Int
            var next: StringSearchCEBuffer.CEI
            if (search_.elementComparisonType_ == ElementComparisonType.STANDARD_ELEMENT_COMPARISON) {
                next = ceb.get(targetIx+offset)!!
                maxLimit = next.lowIndex_
                if (next.lowIndex_ == next.highIndex_ && next.ce_ != StringSearchPCE.PROCESSED_NULLORDER) found = false
            } else {
                while (true) {
                    next = ceb.get(targetIx+offset)!!
                    maxLimit = next.lowIndex_
                    if (next.ce_ == StringSearchPCE.PROCESSED_NULLORDER) break
                    if (((next.ce_ ushr 32) and 0xffff0000L) == 0L) {
                        val match = StringSearchPCE.compareCE64s(next.ce_,patCE,search_.elementComparisonType_)
                        if (match == StringSearchPCE.CE_NO_MATCH || match == StringSearchPCE.CE_SKIP_PATN) { found = false; break }
                    } else if (next.lowIndex_ == next.highIndex_) { found = false; break }
                    else break
                    offset++
                }
            }
            if (!isBreakBoundary(mStart)) found = false
            if (mStart == first.highIndex_) found = false
            val midcluster = breakIterator == null && ((next.ce_ ushr 32) and 0xffff0000L) != 0L &&
                maxLimit >= last.highIndex_ && next.highIndex_ > maxLimit &&
                (nfd_.hasBoundaryBefore(StringSearchPatternCompiler.codePointAt(targetText,maxLimit)) ||
                    nfd_.hasBoundaryAfter(StringSearchPatternCompiler.codePointBefore(targetText,maxLimit)))
            mLimit = maxLimit
            if (minLimit < maxLimit) {
                if (minLimit == last.highIndex_ && isBreakBoundary(minLimit)) mLimit = minLimit
                else {
                    val nba = nextBoundaryAfter(minLimit)
                    if (nba >= last.highIndex_ && (!midcluster || nba < maxLimit)) mLimit = nba
                }
            }
            if (!midcluster) {
                if (mLimit > maxLimit) found = false
                if (!isBreakBoundary(mLimit)) found = false
            }
            if (!checkIdentical(mStart,mLimit)) found = false
            if (found) break
            targetIx++
        }
        if (!found) { mLimit = -1; mStart = -1 }
        if (m != null) { m.start_ = mStart; m.limit_ = mLimit }
        return found
    }
    private fun searchBackwards(startIdx: Int, m: Match?): Boolean {
        if (pattern_.CELength_ == 0 || startIdx < search_.beginIndex() || startIdx > search_.endIndex()) {
            throw IllegalArgumentException("searchBackwards($startIdx, m) - expected position to be between ${search_.beginIndex()} and ${search_.endIndex()}")
        }
        if (pattern_.PCE_ == null) compiler.initializePatternPCETable()
        val ceb = buffer()
        var targetIx = 0
        if (startIdx < search_.endIndex()) {
            val next = search_.internalBreakIter_!!.following(startIdx)
            textIter_!!.setOffset(next)
            while (true) { if (ceb.getPrevious(targetIx)!!.lowIndex_ < startIdx) break; targetIx++ }
        } else textIter_!!.setOffset(startIdx)
        var targetCEI: StringSearchCEBuffer.CEI? = null
        var found: Boolean
        val limitIx = targetIx
        var mStart = -1; var mLimit = -1
        targetIx = limitIx
        while (true) {
            found = true
            val last = ceb.getPrevious(targetIx) ?: throw ICUException("CEBuffer.getPrevious($targetIx) returned null.")
            var offset = 0
            var patIx = pattern_.PCELength_-1
            while (patIx >= 0) {
                val patCE = pceAt(patIx)
                targetCEI = ceb.getPrevious(targetIx+pattern_.PCELength_-1-patIx+offset)
                val match = StringSearchPCE.compareCE64s(targetCEI!!.ce_,patCE,search_.elementComparisonType_)
                if (match == StringSearchPCE.CE_NO_MATCH) { found = false; break }
                else if (match > StringSearchPCE.CE_NO_MATCH) {
                    if (match == StringSearchPCE.CE_SKIP_TARG) { patIx++; offset++ } else offset--
                }
                patIx--
            }
            if (!found && (targetCEI == null || targetCEI.ce_ != StringSearchPCE.PROCESSED_NULLORDER)) { targetIx++; continue }
            if (!found) break
            val first = ceb.getPrevious(targetIx+pattern_.PCELength_-1+offset)!!
            mStart = first.lowIndex_
            if (!isBreakBoundary(mStart)) found = false
            if (mStart == first.highIndex_) found = false
            val minLimit = last.lowIndex_
            if (targetIx > 0) {
                val next = ceb.getPrevious(targetIx-1)!!
                if (next.lowIndex_ == next.highIndex_ && next.ce_ != StringSearchPCE.PROCESSED_NULLORDER) found = false
                val maxLimit = next.lowIndex_
                mLimit = maxLimit
                val midcluster = breakIterator == null && ((next.ce_ ushr 32) and 0xffff0000L) != 0L &&
                    maxLimit >= last.highIndex_ && next.highIndex_ > maxLimit &&
                    (nfd_.hasBoundaryBefore(StringSearchPatternCompiler.codePointAt(targetText,maxLimit)) ||
                        nfd_.hasBoundaryAfter(StringSearchPatternCompiler.codePointBefore(targetText,maxLimit)))
                if (minLimit < maxLimit) {
                    val nba = nextBoundaryAfter(minLimit)
                    if (nba >= last.highIndex_ && (!midcluster || nba < maxLimit)) mLimit = nba
                }
                if (!midcluster) {
                    if (mLimit > maxLimit) found = false
                    if (!isBreakBoundary(mLimit)) found = false
                }
            } else {
                val nba = nextBoundaryAfter(minLimit)
                mLimit = if (nba > 0 && startIdx > nba) nba else startIdx
            }
            if (!checkIdentical(mStart,mLimit)) found = false
            if (found) break
            targetIx++
        }
        if (!found) { mLimit = -1; mStart = -1 }
        if (m != null) { m.start_ = mStart; m.limit_ = mLimit }
        return found
    }
    private fun handleNextCommonImpl(): Boolean {
        val offset = textIter_!!.getOffset()
        val m = Match()
        if (search(offset,m)) {
            search_.matchedIndex_ = m.start_; search_.setMatchedLength(m.limit_-m.start_); return true
        }
        setMatchNotFound(); return false
    }
    private fun handlePreviousCommonImpl(): Boolean {
        val offset: Int
        if (search_.isOverlap_) {
            if (search_.matchedIndex_ != DONE) offset = search_.matchedIndex_+search_.matchedLength()-1
            else {
                compiler.initializePatternPCETable()
                if (!initTextProcessedIter()) { setMatchNotFound(); return false }
                var n = 0
                while (n < pattern_.PCELength_-1) {
                    if (textProcessedIter_!!.nextProcessed(null) == StringSearchPCE.PROCESSED_NULLORDER) break
                    n++
                }
                offset = textIter_!!.getOffset()
            }
        } else offset = textIter_!!.getOffset()
        val m = Match()
        if (searchBackwards(offset,m)) {
            search_.matchedIndex_ = m.start_; search_.setMatchedLength(m.limit_-m.start_); return true
        }
        setMatchNotFound(); return false
    }
    private companion object { val assertionsEnabled = collationIteratorClassAssertionsEnabled(StringSearch::class) }
}

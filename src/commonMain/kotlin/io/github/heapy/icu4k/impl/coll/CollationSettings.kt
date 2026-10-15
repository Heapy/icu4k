// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2013-2015, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.coll

import io.github.heapy.icu4k.lang.UScript

/** Mutable options with immutable-by-convention reordering arrays shared between clones. */
internal class CollationSettings : SharedObject {
    var options = (2 shl STRENGTH_SHIFT) or (MAX_VAR_PUNCT shl MAX_VARIABLE_SHIFT)
    var variableTop = 0L
    var reorderTable: ByteArray? = null
    var minHighNoReorder = 0L
    var reorderRanges: LongArray? = null
    var reorderCodes: IntArray? = EMPTY_INT_ARRAY
    var fastLatinOptions = -1
    var fastLatinPrimaries: CharArray?
    constructor() : super() { fastLatinPrimaries = CharArray(0x180) }
    private constructor(original: CollationSettings) : super() {
        options = original.options; variableTop = original.variableTop
        reorderTable = original.reorderTable; minHighNoReorder = original.minHighNoReorder
        reorderRanges = original.reorderRanges; reorderCodes = original.reorderCodes
        fastLatinOptions = original.fastLatinOptions; fastLatinPrimaries = original.fastLatinPrimaries
    }
    override fun cloneNative(): SharedObject = CollationSettings(this)
    override fun clone(): CollationSettings {
        val c = super.clone() as CollationSettings
        c.fastLatinPrimaries = fastLatinPrimaries!!.copyOf()
        return c
    }
    override fun equals(other: Any?): Boolean {
        if (other !is CollationSettings) return false
        return options == other.options && ((options and ALTERNATE_MASK) == 0 || variableTop == other.variableTop) && reorderCodes.contentEquals(other.reorderCodes)
    }
    override fun hashCode(): Int {
        var h = options shl 8
        if ((options and ALTERNATE_MASK) != 0) h = h xor variableTop.toInt()
        h = h xor reorderCodes!!.size
        for (i in reorderCodes!!.indices) h = h xor (reorderCodes!!.csAt(i) shl i)
        return h
    }
    fun resetReordering() { reorderTable = null; minHighNoReorder = 0; reorderRanges = null; reorderCodes = EMPTY_INT_ARRAY }
    fun aliasReordering(data: CollationData?, codesAndRanges: IntArray?, codesLength: Int, table: ByteArray?) {
        val codes = if (codesLength == codesAndRanges!!.size) codesAndRanges else {
            if (codesLength < 0) throw NegativeArraySizeExceptionForSettings()
            IntArray(codesLength).also { codesAndRanges.copyInto(it, 0, 0, minOf(codesAndRanges.size, codesLength)) }
        }
        val rangesStart = codesLength; val rangesLimit = codesAndRanges.size; val rangesLength = rangesLimit - rangesStart
        if (table != null && (if (rangesLength == 0) !reorderTableHasSplitBytes(table) else rangesLength >= 2 && (codesAndRanges.csAt(rangesStart) and 0xffff) == 0 && (codesAndRanges.csAt(rangesLimit - 1) and 0xffff) != 0)) {
            reorderTable = table; reorderCodes = codes
            var first = rangesStart
            while (first < rangesLimit && (codesAndRanges.csAt(first) and 0xff0000) == 0) ++first
            if (first == rangesLimit) {
                checkAssertion { !reorderTableHasSplitBytes(table) }; minHighNoReorder = 0; reorderRanges = null
            } else {
                checkAssertion { table.csAt(codesAndRanges.csAt(first) ushr 24).toInt() == 0 }
                minHighNoReorder = codesAndRanges.csAt(rangesLimit - 1).toLong() and 0xffff0000L
                setReorderRanges(codesAndRanges, first, rangesLimit - first)
            }
            return
        }
        setReordering(data, codes)
    }
    fun setReordering(data: CollationData?, codes: IntArray?) {
        if (codes!!.isEmpty() || (codes.size == 1 && codes.csAt(0) == UScript.UNKNOWN)) { resetReordering(); return }
        val list = UVector32(); data!!.makeReorderRanges(codes, list)
        var length = list.size()
        if (length == 0) { resetReordering(); return }
        val ranges = list.getBuffer()
        checkAssertion { length >= 2 }
        checkAssertion { (ranges.csAt(0) and 0xffff) == 0 && (ranges.csAt(length - 1) and 0xffff) != 0 }
        minHighNoReorder = ranges.csAt(length - 1).toLong() and 0xffff0000L
        val table = ByteArray(256); var b = 0; var first = -1
        for (i in 0 until length) {
            val pair = ranges.csAt(i); val limit1 = pair ushr 24
            while (b < limit1) { table.csSet(b, (b + pair).toByte()); ++b }
            if ((pair and 0xff0000) != 0) { table.csSet(limit1, 0); b = limit1 + 1; if (first < 0) first = i }
        }
        while (b <= 0xff) { table.csSet(b, b.toByte()); ++b }
        val start: Int
        if (first < 0) { start = 0; length = 0 } else { start = first; length -= first }
        setReorderArrays(codes, ranges, start, length, table)
    }
    private fun setReorderArrays(codes: IntArray?, ranges: IntArray, start: Int, length: Int, table: ByteArray?) {
        val actualCodes = codes ?: EMPTY_INT_ARRAY
        checkAssertion { actualCodes.isEmpty() == (table == null) }
        reorderTable = table; reorderCodes = actualCodes; setReorderRanges(ranges, start, length)
    }
    private fun setReorderRanges(ranges: IntArray, start: Int, length: Int) {
        if (length == 0) reorderRanges = null else {
            if (length < 0) throw NegativeArraySizeExceptionForSettings()
            reorderRanges = LongArray(length)
            var i = 0; var p = start
            do { val index = i++; reorderRanges!!.csSet(index, ranges.csAt(p++).toLong() and 0xffffffffL) } while (i < length)
        }
    }
    fun copyReorderingFrom(other: CollationSettings?) {
        if (!other!!.hasReordering()) { resetReordering(); return }
        minHighNoReorder = other.minHighNoReorder; reorderTable = other.reorderTable; reorderRanges = other.reorderRanges; reorderCodes = other.reorderCodes
    }
    fun hasReordering(): Boolean = reorderTable != null
    fun reorder(p: Long): Long {
        val b = reorderTable!!.csAt(p.toInt() ushr 24).toInt()
        return if (b != 0 || p <= Collation.NO_CE_PRIMARY) ((b.toLong() and 0xffL) shl 24) or (p and 0xffffffL) else reorderEx(p)
    }
    private fun reorderEx(p: Long): Long {
        checkAssertion { minHighNoReorder > 0 }
        if (p >= minHighNoReorder) return p
        val q = p or 0xffffL; var i = 0; var r: Long
        while (true) { r = reorderRanges!!.csAt(i); if (q < r) break; ++i }
        return p + (r.toShort().toLong() shl 24)
    }
    fun setStrength(value: Int) {
        val no = options and STRENGTH_MASK.inv()
        when (value) { 0,1,2,3,15 -> options = no or (value shl STRENGTH_SHIFT); else -> throw IllegalArgumentException("illegal strength value $value") }
    }
    fun setStrengthDefault(defaultOptions: Int) { options = (options and STRENGTH_MASK.inv()) or (defaultOptions and STRENGTH_MASK) }
    fun getStrength(): Int = getStrength(options)
    fun setFlag(bit: Int, value: Boolean) { options = if (value) options or bit else options and bit.inv() }
    fun setFlagDefault(bit: Int, defaultOptions: Int) { options = (options and bit.inv()) or (defaultOptions and bit) }
    fun getFlag(bit: Int): Boolean = (options and bit) != 0
    fun setCaseFirst(value: Int) { checkAssertion { value == 0 || value == CASE_FIRST || value == CASE_FIRST_AND_UPPER_MASK }; options = (options and CASE_FIRST_AND_UPPER_MASK.inv()) or value }
    fun setCaseFirstDefault(defaultOptions: Int) { options = (options and CASE_FIRST_AND_UPPER_MASK.inv()) or (defaultOptions and CASE_FIRST_AND_UPPER_MASK) }
    fun getCaseFirst(): Int = options and CASE_FIRST_AND_UPPER_MASK
    fun setAlternateHandlingShifted(value: Boolean) { val no = options and ALTERNATE_MASK.inv(); options = if (value) no or SHIFTED else no }
    fun setAlternateHandlingDefault(defaultOptions: Int) { options = (options and ALTERNATE_MASK.inv()) or (defaultOptions and ALTERNATE_MASK) }
    fun getAlternateHandling(): Boolean = (options and ALTERNATE_MASK) != 0
    fun setMaxVariable(value: Int, defaultOptions: Int) {
        val no = options and MAX_VARIABLE_MASK.inv()
        when (value) { 0,1,2,3 -> options = no or (value shl MAX_VARIABLE_SHIFT); -1 -> options = no or (defaultOptions and MAX_VARIABLE_MASK); else -> throw IllegalArgumentException("illegal maxVariable value $value") }
    }
    fun getMaxVariable(): Int = (options and MAX_VARIABLE_MASK) shr MAX_VARIABLE_SHIFT
    fun dontCheckFCD(): Boolean = (options and CHECK_FCD) == 0
    fun hasBackwardSecondary(): Boolean = (options and BACKWARD_SECONDARY) != 0
    fun isNumeric(): Boolean = (options and NUMERIC) != 0
    private inline fun checkAssertion(condition: () -> Boolean) { if (assertionsEnabled && !condition()) throw AssertionError() }
    companion object {
        const val CHECK_FCD = 1; const val NUMERIC = 2; const val SHIFTED = 4; const val ALTERNATE_MASK = 0xc
        const val MAX_VARIABLE_SHIFT = 4; const val MAX_VARIABLE_MASK = 0x70; const val UPPER_FIRST = 0x100
        const val CASE_FIRST = 0x200; const val CASE_FIRST_AND_UPPER_MASK = 0x300; const val CASE_LEVEL = 0x400; const val BACKWARD_SECONDARY = 0x800
        const val STRENGTH_SHIFT = 12; const val STRENGTH_MASK = 0xf000
        const val MAX_VAR_SPACE = 0; const val MAX_VAR_PUNCT = 1; const val MAX_VAR_SYMBOL = 2; const val MAX_VAR_CURRENCY = 3
        private val EMPTY_INT_ARRAY = IntArray(0)
        private val assertionsEnabled = collationIteratorClassAssertionsEnabled(CollationSettings::class)
        fun getStrength(options: Int): Int = options shr STRENGTH_SHIFT
        fun isTertiaryWithCaseBits(options: Int): Boolean = (options and (CASE_LEVEL or CASE_FIRST)) == CASE_FIRST
        fun getTertiaryMask(options: Int): Int = if (isTertiaryWithCaseBits(options)) Collation.CASE_AND_TERTIARY_MASK else Collation.ONLY_TERTIARY_MASK
        fun sortsTertiaryUpperCaseFirst(options: Int): Boolean = (options and (CASE_LEVEL or CASE_FIRST_AND_UPPER_MASK)) == CASE_FIRST_AND_UPPER_MASK
        private fun reorderTableHasSplitBytes(table: ByteArray): Boolean {
            if (assertionsEnabled && table.csAt(0).toInt() != 0) throw AssertionError()
            for (i in 1 until 256) if (table.csAt(i).toInt() == 0) return true
            return false
        }
    }
}
private fun csIndex(size: Int, i: Int): Int { if (i < 0 || i >= size) throw IndexOutOfBoundsException(); return i }
private fun IntArray.csAt(i: Int): Int = this[csIndex(size,i)]
private fun ByteArray.csAt(i: Int): Byte = this[csIndex(size,i)]
private fun LongArray.csAt(i: Int): Long = this[csIndex(size,i)]
private fun ByteArray.csSet(i: Int, value: Byte) { this[csIndex(size,i)] = value }
private fun LongArray.csSet(i: Int, value: Long) { this[csIndex(size,i)] = value }
private fun NegativeArraySizeExceptionForSettings(): RuntimeException = settingsNegativeArraySizeException()
internal expect fun settingsNegativeArraySizeException(): RuntimeException

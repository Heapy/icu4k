// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2010-2015, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.coll

import io.github.heapy.icu4k.impl.Normalizer2Impl
import io.github.heapy.icu4k.impl.Trie2_32
import io.github.heapy.icu4k.lang.UScript
import io.github.heapy.icu4k.text.UnicodeSet
import io.github.heapy.icu4k.util.ICUException

/** Mutable while built/loaded, then shared as immutable collation data. */
internal class CollationData(nfc: Normalizer2Impl?) {
    var trie: Trie2_32? = null
    var ce32s: IntArray? = null
    var ces: LongArray? = null
    var contexts: String? = null
    var base: CollationData? = null
    var jamoCE32s: IntArray? = IntArray(JAMO_CE32S_LENGTH)
    var nfcImpl: Normalizer2Impl? = nfc
    var numericPrimary = 0x12000000L
    var compressibleBytes: BooleanArray? = null
    var unsafeBackwardSet: UnicodeSet? = null
    var fastLatinTable: CharArray? = null
    var fastLatinTableHeader: CharArray? = null
    var numScripts = 0
    var scriptsIndex: CharArray? = null
    var scriptStarts: CharArray? = null
    var rootElements: LongArray? = null

    fun getCE32(c: Int): Int = trie!!.get(c)
    fun getCE32FromSupplementary(c: Int): Int = trie!!.get(c)
    fun isDigit(c: Int): Boolean = if (c < 0x660) c <= 0x39 && 0x30 <= c else Collation.hasCE32Tag(getCE32(c), Collation.DIGIT_TAG)
    fun isUnsafeBackward(c: Int, numeric: Boolean): Boolean = unsafeBackwardSet!!.contains(c) || (numeric && isDigit(c))
    fun isCompressibleLeadByte(b: Int): Boolean = compressibleBytes!!.let { it[checked(it.size, b)] }
    fun isCompressiblePrimary(p: Long): Boolean = isCompressibleLeadByte(p.toInt() ushr 24)
    fun getCE32FromContexts(index: Int): Int {
        val text = contexts!!
        return (text[checked(text.length, index)].code shl 16) or text[checked(text.length, index + 1)].code
    }
    fun getIndirectCE32(ce32: Int): Int {
        checkAssertion(Collation.isSpecialCE32(ce32))
        return when (Collation.tagFromCE32(ce32)) {
            Collation.DIGIT_TAG -> ce32s!!.at(Collation.indexFromCE32(ce32))
            Collation.LEAD_SURROGATE_TAG -> Collation.UNASSIGNED_CE32
            Collation.U0000_TAG -> ce32s!!.at(0)
            else -> ce32
        }
    }
    fun getFinalCE32(ce32: Int): Int = if (Collation.isSpecialCE32(ce32)) getIndirectCE32(ce32) else ce32
    fun getCEFromOffsetCE32(c: Int, ce32: Int): Long = Collation.makeCE(Collation.getThreeBytePrimaryForOffsetData(c, ces!!.at(Collation.indexFromCE32(ce32))))
    fun getSingleCE(c: Int): Long {
        var ce32 = getCE32(c)
        val data: CollationData
        if (ce32 == Collation.FALLBACK_CE32) { data = base!!; ce32 = base!!.getCE32(c) } else data = this
        while (Collation.isSpecialCE32(ce32)) {
            when (Collation.tagFromCE32(ce32)) {
                Collation.LATIN_EXPANSION_TAG, Collation.BUILDER_DATA_TAG, Collation.PREFIX_TAG,
                Collation.CONTRACTION_TAG, Collation.HANGUL_TAG, Collation.LEAD_SURROGATE_TAG -> throw notSingle(c, ce32)
                Collation.FALLBACK_TAG, Collation.RESERVED_TAG_3 -> throw AssertionError(elementDiagnostic("unexpected CE32 tag", c, ce32))
                Collation.LONG_PRIMARY_TAG -> return Collation.ceFromLongPrimaryCE32(ce32)
                Collation.LONG_SECONDARY_TAG -> return Collation.ceFromLongSecondaryCE32(ce32)
                Collation.EXPANSION32_TAG -> {
                    if (Collation.lengthFromCE32(ce32) != 1) throw notSingle(c, ce32)
                    ce32 = data.ce32s!!.at(Collation.indexFromCE32(ce32))
                }
                Collation.EXPANSION_TAG -> {
                    if (Collation.lengthFromCE32(ce32) != 1) throw notSingle(c, ce32)
                    return data.ces!!.at(Collation.indexFromCE32(ce32))
                }
                Collation.DIGIT_TAG -> ce32 = data.ce32s!!.at(Collation.indexFromCE32(ce32))
                Collation.U0000_TAG -> { checkAssertion(c == 0); ce32 = data.ce32s!!.at(0) }
                Collation.OFFSET_TAG -> return data.getCEFromOffsetCE32(c, ce32)
                Collation.IMPLICIT_TAG -> return Collation.unassignedCEFromCodePoint(c)
            }
        }
        return Collation.ceFromSimpleCE32(ce32)
    }
    fun getFCD16(c: Int): Int = nfcImpl!!.getFCD16(c)
    fun getFirstPrimaryForGroup(script: Int): Long {
        val index = getScriptIndex(script)
        return if (index == 0) 0 else scriptStarts!!.at(index).toLong() shl 16
    }
    fun getLastPrimaryForGroup(script: Int): Long {
        val index = getScriptIndex(script)
        if (index == 0) return 0
        return (scriptStarts!!.at(index + 1).toLong() shl 16) - 1
    }
    fun getGroupForPrimary(primary: Long): Int {
        val p = primary shr 16
        val starts = scriptStarts!!
        if (p < starts.at(1) || starts.at(starts.size - 1) <= p) return -1
        var index = 1
        while (p >= starts.at(index + 1)) ++index
        for (i in 0 until numScripts) if (scriptsIndex!!.at(i) == index) return i
        for (i in 0 until MAX_NUM_SPECIAL_REORDER_CODES) if (scriptsIndex!!.at(numScripts + i) == index) return REORDER_FIRST + i
        return -1
    }
    private fun getScriptIndex(script: Int): Int = when {
        script < 0 -> 0
        script < numScripts -> scriptsIndex!!.at(script)
        script < REORDER_FIRST -> 0
        script - REORDER_FIRST < MAX_NUM_SPECIAL_REORDER_CODES -> scriptsIndex!!.at(numScripts + script - REORDER_FIRST)
        else -> 0
    }
    fun getEquivalentScripts(script: Int): IntArray {
        val index = getScriptIndex(script)
        if (index == 0) return EMPTY_INT_ARRAY
        if (script >= REORDER_FIRST) return intArrayOf(script)
        var length = 0
        for (i in 0 until numScripts) if (scriptsIndex!!.at(i) == index) ++length
        val dest = IntArray(length)
        if (length == 1) { dest[0] = script; return dest }
        length = 0
        for (i in 0 until numScripts) if (scriptsIndex!!.at(i) == index) dest[length++] = i
        return dest
    }
    fun makeReorderRanges(reorder: IntArray?, ranges: UVector32?) = makeReorderRanges(reorder, false, ranges)
    private fun makeReorderRanges(reorder: IntArray?, latinMustMove: Boolean, ranges: UVector32?) {
        ranges!!.removeAllElements()
        val codes = reorder!!
        var length = codes.size
        if (length == 0 || (length == 1 && codes[0] == UScript.UNKNOWN)) return
        val starts = scriptStarts!!
        val table = ShortArray(starts.size - 1)
        var reserved = scriptsIndex!!.at(numScripts + REORDER_RESERVED_BEFORE_LATIN - REORDER_FIRST)
        if (reserved != 0) table.put(reserved, 0xff)
        reserved = scriptsIndex!!.at(numScripts + REORDER_RESERVED_AFTER_LATIN - REORDER_FIRST)
        if (reserved != 0) table.put(reserved, 0xff)
        checkAssertion(starts.size >= 2)
        checkAssertion(starts.at(0) == 0)
        var lowStart = starts.at(1)
        checkAssertion(lowStart == ((Collation.MERGE_SEPARATOR_BYTE + 1) shl 8))
        var highLimit = starts.at(starts.size - 1)
        checkAssertion(highLimit == (Collation.TRAIL_WEIGHT_BYTE shl 8))
        var specials = 0
        for (i in 0 until length) {
            val code = codes[i] - REORDER_FIRST
            if (code >= 0 && code < MAX_NUM_SPECIAL_REORDER_CODES) specials = specials or (1 shl code)
        }
        for (i in 0 until MAX_NUM_SPECIAL_REORDER_CODES) {
            val index = scriptsIndex!!.at(numScripts + i)
            if (index != 0 && (specials and (1 shl i)) == 0) lowStart = addLowScriptRange(table, index, lowStart)
        }
        var skippedReserved = 0
        if (specials == 0 && codes[0] == UScript.LATIN && !latinMustMove) {
            val index = scriptsIndex!!.at(UScript.LATIN)
            checkAssertion(index != 0)
            val start = starts.at(index)
            checkAssertion(lowStart <= start)
            skippedReserved = start - lowStart
            lowStart = start
        }
        var hasReorderToEnd = false
        var i = 0
        while (i < length) {
            var script = codes[i++]
            if (script == UScript.UNKNOWN) {
                hasReorderToEnd = true
                while (i < length) {
                    script = codes[--length]
                    if (script == UScript.UNKNOWN) throw IllegalArgumentException("setReorderCodes(): duplicate UScript.UNKNOWN")
                    if (script == REORDER_DEFAULT) throw IllegalArgumentException("setReorderCodes(): UScript.DEFAULT together with other scripts")
                    val index = getScriptIndex(script)
                    if (index == 0) continue
                    if (table.at(index) != 0) throw duplicateScript(script)
                    highLimit = addHighScriptRange(table, index, highLimit)
                }
                break
            }
            if (script == REORDER_DEFAULT) throw IllegalArgumentException("setReorderCodes(): UScript.DEFAULT together with other scripts")
            val index = getScriptIndex(script)
            if (index == 0) continue
            if (table.at(index) != 0) throw duplicateScript(script)
            lowStart = addLowScriptRange(table, index, lowStart)
        }
        for (index in 1 until starts.size - 1) {
            if (table.at(index) != 0) continue
            val start = starts.at(index)
            if (!hasReorderToEnd && start > lowStart) lowStart = start
            lowStart = addLowScriptRange(table, index, lowStart)
        }
        if (lowStart > highLimit) {
            if (lowStart - (skippedReserved and 0xff00) <= highLimit) { makeReorderRanges(reorder, true, ranges); return }
            throw ICUException("setReorderCodes(): reordering too many partial-primary-lead-byte scripts")
        }
        var offset = 0
        i = 1
        while (true) {
            var nextOffset = offset
            while (i < starts.size - 1) {
                val newLead = table.at(i)
                if (newLead != 0xff) {
                    nextOffset = newLead - (starts.at(i) shr 8)
                    if (nextOffset != offset) break
                }
                ++i
            }
            if (offset != 0 || i < starts.size - 1) ranges.addElement((starts.at(i) shl 16) or (offset and 0xffff))
            if (i == starts.size - 1) break
            offset = nextOffset
            ++i
        }
    }
    private fun addLowScriptRange(table: ShortArray, index: Int, initial: Int): Int {
        var lowStart = initial
        val start = scriptStarts!!.at(index)
        if ((start and 0xff) < (lowStart and 0xff)) lowStart += 0x100
        table.put(index, lowStart shr 8)
        val limit = scriptStarts!!.at(index + 1)
        return ((lowStart and 0xff00) + ((limit and 0xff00) - (start and 0xff00))) or (limit and 0xff)
    }
    private fun addHighScriptRange(table: ShortArray, index: Int, initial: Int): Int {
        var highLimit = initial
        val limit = scriptStarts!!.at(index + 1)
        if ((limit and 0xff) > (highLimit and 0xff)) highLimit -= 0x100
        val start = scriptStarts!!.at(index)
        highLimit = ((highLimit and 0xff00) - ((limit and 0xff00) - (start and 0xff00))) or (start and 0xff)
        table.put(index, highLimit shr 8)
        return highLimit
    }
    private fun checkAssertion(condition: Boolean) { if (assertionsEnabled && !condition) throw AssertionError() }
    companion object {
        // The public Collator facade is ported after the data/iterator pipeline.
        const val REORDER_FIRST = 0x1000
        const val REORDER_DEFAULT = -1
        const val REORDER_RESERVED_BEFORE_LATIN = REORDER_FIRST + 14
        const val REORDER_RESERVED_AFTER_LATIN = REORDER_FIRST + 15
        const val MAX_NUM_SPECIAL_REORDER_CODES = 8
        const val JAMO_CE32S_LENGTH = 19 + 21 + 27
        private val EMPTY_INT_ARRAY = IntArray(0)
        private val assertionsEnabled = collationDataAssertionsEnabled()
        private fun duplicateScript(script: Int) = IllegalArgumentException("setReorderCodes(): duplicate or equivalent script " + if (script < REORDER_FIRST) script.toString() else "0x" + script.toUInt().toString(16))
        private fun notSingle(c: Int, ce32: Int) = UnsupportedOperationException(elementDiagnostic("there is not exactly one collation element", c, ce32))
        private fun elementDiagnostic(prefix: String, c: Int, ce32: Int): String = "$prefix for U+${c.toUInt().toString(16).uppercase().padStart(4, '0')} (CE32 0x${ce32.toUInt().toString(16).padStart(8, '0')})"
    }
}
private fun checked(size: Int, index: Int): Int { if (index < 0 || index >= size) throw IndexOutOfBoundsException(); return index }
private fun IntArray.at(index: Int): Int = this[checked(size, index)]
private fun LongArray.at(index: Int): Long = this[checked(size, index)]
private fun CharArray.at(index: Int): Int = this[checked(size, index)].code
private fun ShortArray.at(index: Int): Int = this[checked(size, index)].toInt()
private fun ShortArray.put(index: Int, value: Int) { this[checked(size, index)] = value.toShort() }
internal expect fun collationDataAssertionsEnabled(): Boolean

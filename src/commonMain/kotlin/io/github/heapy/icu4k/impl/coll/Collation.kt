// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2010-2015, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.coll

/** ICU collation v2 CE/CE32 layouts and weight arithmetic. */
internal object Collation {
    private val assertionsEnabled = collationAssertionsEnabled()
    private fun collationAssert(condition: Boolean) {
        if (assertionsEnabled && !condition) throw AssertionError()
    }
    const val SENTINEL_CP: Int = -1
    const val LESS: Int = -1
    const val EQUAL: Int = 0
    const val GREATER: Int = 1
    const val TERMINATOR_BYTE: Int = 0
    const val LEVEL_SEPARATOR_BYTE: Int = 1
    const val BEFORE_WEIGHT16: Int = 0x100
    const val MERGE_SEPARATOR_BYTE: Int = 2
    const val MERGE_SEPARATOR_PRIMARY: Long = 0x02000000
    const val MERGE_SEPARATOR_CE32: Int = 0x02000505
    const val PRIMARY_COMPRESSION_LOW_BYTE: Int = 3
    const val PRIMARY_COMPRESSION_HIGH_BYTE: Int = 0xff
    const val COMMON_BYTE: Int = 5
    const val COMMON_WEIGHT16: Int = 0x0500
    const val COMMON_SECONDARY_CE: Int = 0x05000000
    const val COMMON_TERTIARY_CE: Int = 0x0500
    const val COMMON_SEC_AND_TER_CE: Int = 0x05000500
    const val SECONDARY_MASK: Int = -65536
    const val CASE_MASK: Int = 0xc000
    const val SECONDARY_AND_CASE_MASK: Int = SECONDARY_MASK or CASE_MASK
    const val ONLY_TERTIARY_MASK: Int = 0x3f3f
    const val ONLY_SEC_TER_MASK: Int = SECONDARY_MASK or ONLY_TERTIARY_MASK
    const val CASE_AND_TERTIARY_MASK: Int = CASE_MASK or ONLY_TERTIARY_MASK
    const val QUATERNARY_MASK: Int = 0xc0
    const val CASE_AND_QUATERNARY_MASK: Int = CASE_MASK or QUATERNARY_MASK
    const val UNASSIGNED_IMPLICIT_BYTE: Int = 0xfe
    const val FIRST_UNASSIGNED_PRIMARY: Long = 0xfe040200L
    const val TRAIL_WEIGHT_BYTE: Int = 0xff
    const val FIRST_TRAILING_PRIMARY: Long = 0xff020200L
    const val MAX_PRIMARY: Long = 0xffff0000L
    const val MAX_REGULAR_CE32: Int = -64251
    const val FFFD_PRIMARY: Long = MAX_PRIMARY - 0x20000
    const val FFFD_CE32: Int = MAX_REGULAR_CE32 - 0x20000
    const val SPECIAL_CE32_LOW_BYTE: Int = 0xc0
    const val FALLBACK_CE32: Int = SPECIAL_CE32_LOW_BYTE
    const val LONG_PRIMARY_CE32_LOW_BYTE: Int = 0xc1
    const val UNASSIGNED_CE32: Int = -1
    const val NO_CE32: Int = 1
    const val NO_CE_PRIMARY: Long = 1
    const val NO_CE_WEIGHT16: Int = 0x0100
    const val NO_CE: Long = 0x101000100L
    const val NO_LEVEL: Int = 0
    const val PRIMARY_LEVEL: Int = 1
    const val SECONDARY_LEVEL: Int = 2
    const val CASE_LEVEL: Int = 3
    const val TERTIARY_LEVEL: Int = 4
    const val QUATERNARY_LEVEL: Int = 5
    const val IDENTICAL_LEVEL: Int = 6
    const val ZERO_LEVEL: Int = 7
    const val NO_LEVEL_FLAG: Int = 1
    const val PRIMARY_LEVEL_FLAG: Int = 2
    const val SECONDARY_LEVEL_FLAG: Int = 4
    const val CASE_LEVEL_FLAG: Int = 8
    const val TERTIARY_LEVEL_FLAG: Int = 0x10
    const val QUATERNARY_LEVEL_FLAG: Int = 0x20
    const val IDENTICAL_LEVEL_FLAG: Int = 0x40
    const val ZERO_LEVEL_FLAG: Int = 0x80
    const val FALLBACK_TAG: Int = 0
    const val LONG_PRIMARY_TAG: Int = 1
    const val LONG_SECONDARY_TAG: Int = 2
    const val RESERVED_TAG_3: Int = 3
    const val LATIN_EXPANSION_TAG: Int = 4
    const val EXPANSION32_TAG: Int = 5
    const val EXPANSION_TAG: Int = 6
    const val BUILDER_DATA_TAG: Int = 7
    const val PREFIX_TAG: Int = 8
    const val CONTRACTION_TAG: Int = 9
    const val DIGIT_TAG: Int = 10
    const val U0000_TAG: Int = 11
    const val HANGUL_TAG: Int = 12
    const val LEAD_SURROGATE_TAG: Int = 13
    const val OFFSET_TAG: Int = 14
    const val IMPLICIT_TAG: Int = 15
    const val MAX_EXPANSION_LENGTH: Int = 31
    const val MAX_INDEX: Int = 0x7ffff
    const val CONTRACT_SINGLE_CP_NO_MATCH: Int = 0x100
    const val CONTRACT_NEXT_CCC: Int = 0x200
    const val CONTRACT_TRAILING_CCC: Int = 0x400
    const val HANGUL_NO_SPECIAL_JAMO: Int = 0x100
    const val LEAD_ALL_UNASSIGNED: Int = 0
    const val LEAD_ALL_FALLBACK: Int = 0x100
    const val LEAD_MIXED: Int = 0x200
    const val LEAD_TYPE_MASK: Int = 0x300

    fun isAssignedCE32(ce32: Int): Boolean = ce32 != FALLBACK_CE32 && ce32 != UNASSIGNED_CE32
    fun makeLongPrimaryCE32(p: Long): Int = (p or LONG_PRIMARY_CE32_LOW_BYTE.toLong()).toInt()
    fun primaryFromLongPrimaryCE32(ce32: Int): Long = ce32.toLong() and 0xffffff00L
    fun ceFromLongPrimaryCE32(ce32: Int): Long = ((ce32 and -256).toLong() shl 32) or COMMON_SEC_AND_TER_CE.toLong()
    fun makeLongSecondaryCE32(lower32: Int): Int = lower32 or SPECIAL_CE32_LOW_BYTE or LONG_SECONDARY_TAG
    fun ceFromLongSecondaryCE32(ce32: Int): Long = ce32.toLong() and 0xffffff00L
    fun makeCE32FromTagIndexAndLength(tag: Int, index: Int, length: Int): Int =
        (index shl 13) or (length shl 8) or SPECIAL_CE32_LOW_BYTE or tag
    fun makeCE32FromTagAndIndex(tag: Int, index: Int): Int = (index shl 13) or SPECIAL_CE32_LOW_BYTE or tag
    fun isSpecialCE32(ce32: Int): Boolean = (ce32 and 0xff) >= SPECIAL_CE32_LOW_BYTE
    fun tagFromCE32(ce32: Int): Int = ce32 and 0xf
    fun hasCE32Tag(ce32: Int, tag: Int): Boolean = isSpecialCE32(ce32) && tagFromCE32(ce32) == tag
    fun isLongPrimaryCE32(ce32: Int): Boolean = hasCE32Tag(ce32, LONG_PRIMARY_TAG)
    fun isSimpleOrLongCE32(ce32: Int): Boolean = !isSpecialCE32(ce32) || tagFromCE32(ce32) == LONG_PRIMARY_TAG || tagFromCE32(ce32) == LONG_SECONDARY_TAG
    fun isSelfContainedCE32(ce32: Int): Boolean = isSimpleOrLongCE32(ce32) || tagFromCE32(ce32) == LATIN_EXPANSION_TAG
    fun isPrefixCE32(ce32: Int): Boolean = hasCE32Tag(ce32, PREFIX_TAG)
    fun isContractionCE32(ce32: Int): Boolean = hasCE32Tag(ce32, CONTRACTION_TAG)
    fun ce32HasContext(ce32: Int): Boolean = isSpecialCE32(ce32) && (tagFromCE32(ce32) == PREFIX_TAG || tagFromCE32(ce32) == CONTRACTION_TAG)
    fun latinCE0FromCE32(ce32: Int): Long = ((ce32 and -16777216).toLong() shl 32) or COMMON_SECONDARY_CE.toLong() or ((ce32 and 0xff0000) shr 8).toLong()
    fun latinCE1FromCE32(ce32: Int): Long = ((ce32.toLong() and 0xff00) shl 16) or COMMON_TERTIARY_CE.toLong()
    fun indexFromCE32(ce32: Int): Int = ce32 ushr 13
    fun lengthFromCE32(ce32: Int): Int = (ce32 shr 8) and 31
    fun digitFromCE32(ce32: Int): Char = ((ce32 shr 8) and 0xf).toChar()
    fun ceFromSimpleCE32(ce32: Int): Long {
        collationAssert((ce32 and 0xff) < SPECIAL_CE32_LOW_BYTE)
        return ((ce32 and -65536).toLong() shl 32) or ((ce32 and 0xff00).toLong() shl 16) or ((ce32 and 0xff) shl 8).toLong()
    }
    fun ceFromCE32(ce32: Int): Long {
        val tertiary = ce32 and 0xff
        if (tertiary < SPECIAL_CE32_LOW_BYTE)
            return ((ce32 and -65536).toLong() shl 32) or ((ce32 and 0xff00).toLong() shl 16) or (tertiary shl 8).toLong()
        val primaryOrSecondary = ce32 - tertiary
        if ((tertiary and 0xf) == LONG_PRIMARY_TAG)
            return (primaryOrSecondary.toLong() shl 32) or COMMON_SEC_AND_TER_CE.toLong()
        collationAssert((tertiary and 0xf) == LONG_SECONDARY_TAG)
        return primaryOrSecondary.toLong() and 0xffffffffL
    }
    fun makeCE(p: Long): Long = (p shl 32) or COMMON_SEC_AND_TER_CE.toLong()
    fun makeCE(p: Long, s: Int, t: Int, q: Int): Long = (p shl 32) or (s.toLong() shl 16) or t.toLong() or (q shl 6).toLong()

    /** Primary arithmetic preserves Java Int overflow and signed division, including malformed input. */
    fun incTwoBytePrimaryByOffset(basePrimary: Long, isCompressible: Boolean, offset: Int): Long {
        var delta = offset
        val primary: Long
        if (isCompressible) {
            delta += ((basePrimary shr 16).toInt() and 0xff) - 4
            primary = (((delta % 251) + 4) shl 16).toLong()
            delta /= 251
        } else {
            delta += ((basePrimary shr 16).toInt() and 0xff) - 2
            primary = (((delta % 254) + 2) shl 16).toLong()
            delta /= 254
        }
        return primary or ((basePrimary and 0xff000000L) + (delta.toLong() shl 24))
    }
    fun incThreeBytePrimaryByOffset(basePrimary: Long, isCompressible: Boolean, offset: Int): Long {
        var delta = offset + (((basePrimary shr 8).toInt() and 0xff) - 2)
        var primary = (((delta % 254) + 2) shl 8).toLong()
        delta /= 254
        if (isCompressible) {
            delta += ((basePrimary shr 16).toInt() and 0xff) - 4
            primary = primary or (((delta % 251) + 4) shl 16).toLong()
            delta /= 251
        } else {
            delta += ((basePrimary shr 16).toInt() and 0xff) - 2
            primary = primary or (((delta % 254) + 2) shl 16).toLong()
            delta /= 254
        }
        return primary or ((basePrimary and 0xff000000L) + (delta.toLong() shl 24))
    }
    fun decTwoBytePrimaryByOneStep(basePrimary: Long, isCompressible: Boolean, step: Int): Long {
        collationAssert(0 < step && step <= 0x7f)
        var primary = basePrimary
        var byte2 = ((primary shr 16).toInt() and 0xff) - step
        if (isCompressible) {
            if (byte2 < 4) { byte2 += 251; primary -= 0x1000000 }
        } else {
            if (byte2 < 2) { byte2 += 254; primary -= 0x1000000 }
        }
        return (primary and 0xff000000L) or (byte2 shl 16).toLong()
    }
    fun decThreeBytePrimaryByOneStep(basePrimary: Long, isCompressible: Boolean, step: Int): Long {
        collationAssert(0 < step && step <= 0x7f)
        var primary = basePrimary
        var byte3 = ((primary shr 8).toInt() and 0xff) - step
        if (byte3 >= 2) return (primary and 0xffff0000L) or (byte3 shl 8).toLong()
        byte3 += 254
        var byte2 = ((primary shr 16).toInt() and 0xff) - 1
        if (isCompressible) {
            if (byte2 < 4) { byte2 = 0xfe; primary -= 0x1000000 }
        } else {
            if (byte2 < 2) { byte2 = 0xff; primary -= 0x1000000 }
        }
        return (primary and 0xff000000L) or (byte2 shl 16).toLong() or (byte3 shl 8).toLong()
    }
    fun getThreeBytePrimaryForOffsetData(c: Int, dataCE: Long): Long {
        val p = dataCE ushr 32
        val lower32 = dataCE.toInt()
        val offset = (c - (lower32 shr 8)) * (lower32 and 0x7f)
        return incThreeBytePrimaryByOffset(p, (lower32 and 0x80) != 0, offset)
    }
    fun unassignedPrimaryFromCodePoint(c: Int): Long {
        var codePoint = c + 1
        var primary = (2 + (codePoint % 18) * 14).toLong()
        codePoint /= 18
        primary = primary or ((2 + (codePoint % 254)) shl 8).toLong()
        codePoint /= 254
        primary = primary or ((4 + (codePoint % 251)) shl 16).toLong()
        return primary or (UNASSIGNED_IMPLICIT_BYTE.toLong() shl 24)
    }
    fun unassignedCEFromCodePoint(c: Int): Long = makeCE(unassignedPrimaryFromCodePoint(c))
}
internal expect fun collationAssertionsEnabled(): Boolean

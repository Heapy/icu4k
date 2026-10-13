// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 1996-2016, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.lang.UCharacterCategory
import io.github.heapy.icu4k.lang.UProperty
import io.github.heapy.icu4k.util.BinaryBuffer
import io.github.heapy.icu4k.util.CodePointTrie
import io.github.heapy.icu4k.util.ICUUncheckedIOException
import io.github.heapy.icu4k.util.VersionInfo

/**
 * Unicode property data and numeric algorithms from ICU4J UCharacterProperty.
 * Binary/int property dispatch will be added with its case/bidi/normalization
 * dependencies; this class currently exposes the raw data and numeric layer.
 */
class UCharacterProperty internal constructor(bytes: BinaryBuffer = ICUBinary.getRequiredData("uprops.icu")) {
    val m_unicodeVersion_: VersionInfo
    val m_trie_: Trie2_16
    private val additionalTrie: Trie2_16?
    private val additionalVectors: IntArray
    internal val m_additionalColumnsCount_: Int
    internal val m_maxBlockScriptValue_: Int
    internal val m_maxJTGValue_: Int
    internal val m_maxValuesOther_: Int
    val m_scriptExtensions_: CharArray
    internal val m_blockTrie_: CodePointTrie

    init {
        m_unicodeVersion_ = ICUBinary.readHeaderAndDataVersion(bytes, 0x5550726f, ICUBinary.Authenticate { it[0] == 9.toByte() })
        val start = bytes.position
        val indexes = bytes.readInts(16)
        m_additionalColumnsCount_ = indexes[5]
        if (m_additionalColumnsCount_ < 0) throw ICUUncheckedIOException("Invalid additional property column count")
        m_maxBlockScriptValue_ = indexes[10]
        m_maxJTGValue_ = indexes[11]
        m_maxValuesOther_ = indexes[12]
        fun advanceTo(wordOffset: Int) {
            if (wordOffset < 0 || wordOffset > (bytes.limit - start) / 4) throw ICUUncheckedIOException("Invalid uprops data offset")
            val position = start + wordOffset * 4
            if (position < bytes.position) throw ICUUncheckedIOException("uprops section is shorter than its data")
            bytes.position = position
        }
        m_trie_ = Trie2_16.createFromSerialized(bytes)
        advanceTo(indexes[0])
        advanceTo(indexes[3])
        if (m_additionalColumnsCount_ > 0) {
            additionalTrie = Trie2_16.createFromSerialized(bytes)
            advanceTo(indexes[4])
            additionalVectors = bytes.readInts(indexes[6] - indexes[4])
        } else {
            additionalTrie = null
            additionalVectors = IntArray(0)
        }
        val scriptLength = (indexes[7] - indexes[6]) * 2
        if (scriptLength < 0) throw ICUUncheckedIOException("Invalid script extensions data length")
        m_scriptExtensions_ = bytes.readChars(scriptLength)
        m_blockTrie_ = CodePointTrie.fromBinary(null, CodePointTrie.ValueWidth.BITS_16, bytes)
        advanceTo(indexes[8])
    }

    internal fun hasPosixBinaryProperty(c: Int, which: Int): Boolean = when (which) {
        UProperty.POSIX_ALNUM -> hasVectorBinaryProperty(c, UProperty.ALPHABETIC) || getType(c) == UCharacterCategory.DECIMAL_DIGIT_NUMBER.toInt()
        UProperty.POSIX_BLANK -> if (c <= 0x9f) c == 9 || c == 0x20 else getType(c) == UCharacterCategory.SPACE_SEPARATOR.toInt()
        UProperty.POSIX_GRAPH -> isGraphPosix(c)
        UProperty.POSIX_PRINT -> getType(c) == UCharacterCategory.SPACE_SEPARATOR.toInt() || isGraphPosix(c)
        UProperty.POSIX_XDIGIT -> (c in 0x41..0x66 && (c <= 0x46 || c >= 0x61)) ||
            (c in 0xff21..0xff46 && (c <= 0xff26 || c >= 0xff41)) || getType(c) == UCharacterCategory.DECIMAL_DIGIT_NUMBER.toInt()
        else -> throw IllegalArgumentException("Not a POSIX binary property: $which")
    }
    private fun isGraphPosix(c: Int): Boolean = getMask(getType(c)) and posixGraphExcludedMask == 0
    /** Non-normalization binary entries backed by case/bidi data or fixed UCD ranges. */
    internal fun hasAuxiliaryBinaryProperty(c: Int, which: Int): Boolean = when (which) {
        UProperty.LOWERCASE,
        UProperty.SOFT_DOTTED,
        UProperty.UPPERCASE,
        UProperty.CASE_SENSITIVE,
        UProperty.CASED,
        UProperty.CASE_IGNORABLE,
        UProperty.CHANGES_WHEN_LOWERCASED,
        UProperty.CHANGES_WHEN_UPPERCASED,
        UProperty.CHANGES_WHEN_TITLECASED,
        UProperty.CHANGES_WHEN_CASEMAPPED -> UCaseProps.INSTANCE.hasBinaryProperty(c, which)
        UProperty.BIDI_CONTROL -> UBiDiProps.INSTANCE.isBidiControl(c)
        UProperty.BIDI_MIRRORED -> UBiDiProps.INSTANCE.isMirrored(c)
        UProperty.JOIN_CONTROL -> UBiDiProps.INSTANCE.isJoinControl(c)
        UProperty.REGIONAL_INDICATOR -> c in 0x1f1e6..0x1f1ff
        UProperty.IDS_UNARY_OPERATOR -> c in 0x2ffe..0x2fff
        UProperty.ID_COMPAT_MATH_START -> c >= idCompatMathStart[0] && c in idCompatMathStart
        UProperty.ID_COMPAT_MATH_CONTINUE -> inFixedRanges(c, idCompatMathContinue) || (c >= idCompatMathStart[0] && c in idCompatMathStart)
        UProperty.MODIFIER_COMBINING_MARK -> inFixedRanges(c, modifierCombiningMark)
        else -> throw IllegalArgumentException("Not an auxiliary binary property: $which")
    }
    private fun inFixedRanges(c: Int, ranges: IntArray): Boolean {
        for (i in ranges.indices step 2) {
            if (c < ranges[i]) return false
            if (c < ranges[i + 1]) return true
        }
        return false
    }
    /** Directly stored binary properties from additional-vector word 1. */
    internal fun hasVectorBinaryProperty(c: Int, which: Int): Boolean {
        val bit = vectorBinaryProperties[which] ?: throw IllegalArgumentException("Not a vector binary property: $which")
        return getAdditional(c, 1) and (1 shl bit) != 0
    }
    /** Integer entries backed by the bidi, block and layout tries. */
    internal fun getTrieIntPropertyValue(c: Int, which: Int): Int = when (which) {
        UProperty.BIDI_CLASS -> UBiDiProps.INSTANCE.getClass(c)
        UProperty.JOINING_GROUP -> UBiDiProps.INSTANCE.getJoiningGroup(c)
        UProperty.JOINING_TYPE -> UBiDiProps.INSTANCE.getJoiningType(c)
        UProperty.BIDI_PAIRED_BRACKET_TYPE -> UBiDiProps.INSTANCE.getPairedBracketType(c)
        UProperty.BLOCK -> m_blockTrie_[if (c <= 0x10ffff) c shr 4 else c]
        UProperty.INDIC_POSITIONAL_CATEGORY -> LayoutProps.INSTANCE.inpcTrie?.get(c) ?: 0
        UProperty.INDIC_SYLLABIC_CATEGORY -> LayoutProps.INSTANCE.inscTrie?.get(c) ?: 0
        UProperty.VERTICAL_ORIENTATION -> LayoutProps.INSTANCE.voTrie?.get(c) ?: 0
        else -> throw IllegalArgumentException("Not a trie integer property: $which")
    }
    internal fun getTrieIntPropertyMaxValue(which: Int): Int = when (which) {
        UProperty.BIDI_CLASS, UProperty.JOINING_GROUP, UProperty.JOINING_TYPE, UProperty.BIDI_PAIRED_BRACKET_TYPE -> UBiDiProps.INSTANCE.getMaxValue(which)
        UProperty.BLOCK -> m_maxValuesOther_ and 0x3ff
        UProperty.INDIC_POSITIONAL_CATEGORY -> LayoutProps.INSTANCE.maxInpcValue
        UProperty.INDIC_SYLLABIC_CATEGORY -> LayoutProps.INSTANCE.maxInscValue
        UProperty.VERTICAL_ORIENTATION -> LayoutProps.INSTANCE.maxVoValue
        else -> throw IllegalArgumentException("Not a trie integer property: $which")
    }
    /** Derived entries backed by main/additional property data, without normalization. */
    internal fun getDerivedIntPropertyValue(c: Int, which: Int): Int = when (which) {
        UProperty.GENERAL_CATEGORY -> getType(c)
        UProperty.NUMERIC_TYPE -> {
            val ntv = numericTypeValue(c)
            when { ntv == 0 -> 0; ntv < NTV_DIGIT_START -> 1; ntv < NTV_NUMERIC_START -> 2; else -> 3 }
        }
        UProperty.HANGUL_SYLLABLE_TYPE -> {
            if (c > 0xffff) 0 else {
                val gcb = (getAdditional(c, 2) and 0x3e0) ushr 5
                if (gcb < gcbToHst.size) gcbToHst[gcb] else 0
            }
        }
        UProperty.IDENTIFIER_STATUS -> if (getAdditional(c, 2) ushr 26 >= 0x3c) 1 else 0
        else -> throw IllegalArgumentException("Not a derived integer property: $which")
    }
    internal fun getDerivedIntPropertyMaxValue(which: Int): Int = when (which) {
        UProperty.GENERAL_CATEGORY -> 29
        UProperty.NUMERIC_TYPE -> 3
        UProperty.HANGUL_SYLLABLE_TYPE -> 5
        UProperty.IDENTIFIER_STATUS -> 1
        else -> throw IllegalArgumentException("Not a derived integer property: $which")
    }
    /** Direct-vector entries of the original integer-property dispatch table. */
    internal fun getVectorIntPropertyValue(c: Int, which: Int): Int {
        val property = vectorIntProperties[which] ?: throw IllegalArgumentException("Not a vector integer property: $which")
        return (getAdditional(c, property.column) and property.mask) ushr property.shift
    }
    internal fun getVectorIntPropertyMaxValue(which: Int): Int {
        val property = vectorIntProperties[which] ?: throw IllegalArgumentException("Not a vector integer property: $which")
        return (getMaxValues(property.column) and property.mask) ushr property.shift
    }
    fun hasIDType(c: Int, typeIndex: Int): Boolean {
        if (typeIndex !in idTypeToEncoded.indices) return false
        val encoded = idTypeToEncoded[typeIndex]
        val value = getAdditional(c, 2) ushr 26
        return if (encoded and 0x80 != 0) value < 0x30 && value and encoded != 0 else value == encoded
    }
    fun hasIDType(c: Int, type: io.github.heapy.icu4k.lang.UCharacter.IdentifierType): Boolean = hasIDType(c, type.ordinal)
    fun getIDTypes(c: Int, types: MutableSet<io.github.heapy.icu4k.lang.UCharacter.IdentifierType>): Int {
        types.clear()
        val value = getAdditional(c, 2) ushr 26
        val entries = io.github.heapy.icu4k.lang.UCharacter.IdentifierType.entries
        if (value and 0x30 == 0x30 || value == 0) {
            val index = when (value) { 0 -> 0; 0x30 -> 1; 0x31 -> 2; 0x32 -> 3; 0x3e -> 10; 0x3f -> 11
                else -> throw IllegalStateException("unknown IdentifierType data value 0x${value.toString(16).padStart(2, '0')}") }
            types.add(entries[index])
            return 1
        }
        for (index in 4..9) if (value and idTypeToEncoded[index] != 0) types.add(entries[index])
        return types.size
    }
    fun getProperty(ch: Int): Int = m_trie_[ch]
    fun getType(c: Int): Int = getProperty(c) and TYPE_MASK
    fun getAdditional(codepoint: Int, column: Int): Int {
        require(column >= 0) { "Negative property column" }
        if (column >= m_additionalColumnsCount_) return 0
        return additionalVectors[additionalTrie!![codepoint] + column]
    }
    fun getAge(codepoint: Int): VersionInfo {
        val version = getAdditional(codepoint, 0) ushr 24
        return VersionInfo.getInstance(version shr 2, version and 3)
    }
    fun getMaxValues(column: Int): Int = when (column) {
        0 -> m_maxBlockScriptValue_
        2 -> m_maxJTGValue_
        else -> 0
    }
    fun digit(c: Int): Int {
        val value = numericTypeValue(c) - NTV_DECIMAL_START
        return if (value <= 9) value else -1
    }
    private fun numericTypeValue(c: Int): Int = getProperty(c) shr 6

    fun getNumericValue(c: Int): Int {
        val ntv = numericTypeValue(c)
        return when {
            ntv == 0 -> getEuropeanDigit(c)
            ntv < NTV_DIGIT_START -> ntv - NTV_DECIMAL_START
            ntv < NTV_NUMERIC_START -> ntv - NTV_DIGIT_START
            ntv < NTV_FRACTION_START -> ntv - NTV_NUMERIC_START
            ntv < NTV_LARGE_START -> -2
            ntv < NTV_BASE60_START -> {
                val mantissa = (ntv shr 5) - 14
                val exponent = (ntv and 31) + 2
                if (exponent < 9 || (exponent == 9 && mantissa <= 2)) {
                    var value = mantissa
                    repeat(exponent) { value *= 10 }
                    value
                } else -2
            }
            ntv < NTV_FRACTION20_START -> base60Value(ntv)
            else -> -2
        }
    }

    fun getUnicodeNumericValue(c: Int): Double {
        val ntv = numericTypeValue(c)
        return when {
            ntv == 0 -> NO_NUMERIC_VALUE
            ntv < NTV_DIGIT_START -> (ntv - NTV_DECIMAL_START).toDouble()
            ntv < NTV_NUMERIC_START -> (ntv - NTV_DIGIT_START).toDouble()
            ntv < NTV_FRACTION_START -> (ntv - NTV_NUMERIC_START).toDouble()
            ntv < NTV_LARGE_START -> ((ntv shr 4) - 12).toDouble() / ((ntv and 15) + 1)
            ntv < NTV_BASE60_START -> {
                var value = ((ntv shr 5) - 14).toDouble()
                var exponent = (ntv and 31) + 2
                // Preserve ICU's multiplication order for floating-point parity.
                while (exponent >= 4) { value *= 10000.0; exponent -= 4 }
                value *= when (exponent) { 3 -> 1000.0; 2 -> 100.0; 1 -> 10.0; else -> 1.0 }
                value
            }
            ntv < NTV_FRACTION20_START -> base60Value(ntv).toDouble()
            ntv < NTV_FRACTION32_START -> {
                val fraction = ntv - NTV_FRACTION20_START
                (2 * (fraction and 3) + 1).toDouble() / (20 shl (fraction shr 2))
            }
            ntv < NTV_RESERVED_START -> {
                val fraction = ntv - NTV_FRACTION32_START
                (2 * (fraction and 3) + 1).toDouble() / (32 shl (fraction shr 2))
            }
            else -> NO_NUMERIC_VALUE
        }
    }

    private fun base60Value(ntv: Int): Int {
        var value = (ntv shr 2) - 0xbf
        repeat((ntv and 3) + 1) { value *= 60 }
        return value
    }

    private data class VectorIntProperty(val column: Int, val mask: Int, val shift: Int)
    companion object {
        private val posixGraphExcludedMask = getMask(UCharacterCategory.CONTROL.toInt()) or
            getMask(UCharacterCategory.SURROGATE.toInt()) or getMask(UCharacterCategory.UNASSIGNED.toInt()) or
            getMask(UCharacterCategory.SPACE_SEPARATOR.toInt()) or getMask(UCharacterCategory.LINE_SEPARATOR.toInt()) or
            getMask(UCharacterCategory.PARAGRAPH_SEPARATOR.toInt())

        private val idCompatMathStart = intArrayOf(8706, 8711, 8734, 120513, 120539, 120571, 120597, 120629, 120655, 120687, 120713, 120745, 120771)
        private val idCompatMathContinue = intArrayOf(178, 180, 185, 186, 8304, 8305, 8308, 8319, 8320, 8335)
        private val modifierCombiningMark = intArrayOf(1620, 1622, 1624, 1625, 1756, 1757, 1763, 1764, 1767, 1769, 2250, 2252, 2253, 2256, 2259, 2260, 2291, 2292)

        private val vectorBinaryProperties = mapOf(
            UProperty.ALPHABETIC to 8,
            UProperty.ASCII_HEX_DIGIT to 7,
            UProperty.DASH to 1,
            UProperty.DEFAULT_IGNORABLE_CODE_POINT to 19,
            UProperty.DEPRECATED to 20,
            UProperty.DIACRITIC to 10,
            UProperty.EXTENDER to 11,
            UProperty.GRAPHEME_BASE to 26,
            UProperty.GRAPHEME_EXTEND to 13,
            UProperty.GRAPHEME_LINK to 14,
            UProperty.HEX_DIGIT to 6,
            UProperty.HYPHEN to 2,
            UProperty.ID_CONTINUE to 25,
            UProperty.ID_START to 24,
            UProperty.IDEOGRAPHIC to 9,
            UProperty.IDS_BINARY_OPERATOR to 15,
            UProperty.IDS_TRINARY_OPERATOR to 16,
            UProperty.LOGICAL_ORDER_EXCEPTION to 21,
            UProperty.MATH to 5,
            UProperty.NONCHARACTER_CODE_POINT to 12,
            UProperty.QUOTATION_MARK to 3,
            UProperty.RADICAL to 17,
            UProperty.TERMINAL_PUNCTUATION to 4,
            UProperty.UNIFIED_IDEOGRAPH to 18,
            UProperty.WHITE_SPACE to 0,
            UProperty.XID_CONTINUE to 23,
            UProperty.XID_START to 22,
            UProperty.S_TERM to 27,
            UProperty.VARIATION_SELECTOR to 28,
            UProperty.PATTERN_SYNTAX to 29,
            UProperty.PATTERN_WHITE_SPACE to 30,
            UProperty.PREPENDED_CONCATENATION_MARK to 31,
        )

        private val gcbToHst = intArrayOf(0, 0, 0, 0, 1, 0, 4, 5, 3, 2)
        private val vectorIntProperties = mapOf(
            UProperty.DECOMPOSITION_TYPE to VectorIntProperty(2, 0x1f, 0),
            UProperty.EAST_ASIAN_WIDTH to VectorIntProperty(0, 0x7000, 12),
            UProperty.LINE_BREAK to VectorIntProperty(2, 0x03f00000, 20),
            UProperty.GRAPHEME_CLUSTER_BREAK to VectorIntProperty(2, 0x3e0, 5),
            UProperty.SENTENCE_BREAK to VectorIntProperty(2, 0x000f8000, 15),
            UProperty.WORD_BREAK to VectorIntProperty(2, 0x7c00, 10),
            UProperty.INDIC_CONJUNCT_BREAK to VectorIntProperty(0, 0x18000, 15),
        )

        private val idTypeToEncoded = intArrayOf(0, 0x30, 0x31, 0x32, 0x81, 0xa0, 0x82, 0x84, 0x88, 0x90, 0x3e, 0x3f)
        val INSTANCE: UCharacterProperty by lazy { UCharacterProperty() }
        const val TYPE_MASK = 0x1f
        const val LATIN_CAPITAL_LETTER_I_WITH_DOT_ABOVE_: Char = '\u0130'
        const val LATIN_SMALL_LETTER_DOTLESS_I_: Char = '\u0131'
        const val LATIN_SMALL_LETTER_I_: Char = 'i'
        const val SCRIPT_X_MASK = 0xfff
        const val SCRIPT_X_WITH_OTHER = 0xc00
        const val SCRIPT_X_WITH_INHERITED = 0x800
        const val SCRIPT_X_WITH_COMMON = 0x400
        const val MAX_SCRIPT = 0x3ff
        const val NO_NUMERIC_VALUE: Double = -123456789.0
        private const val NTV_DECIMAL_START = 1
        private const val NTV_DIGIT_START = 11
        private const val NTV_NUMERIC_START = 21
        private const val NTV_FRACTION_START = 0xb0
        private const val NTV_LARGE_START = 0x1e0
        private const val NTV_BASE60_START = 0x300
        private const val NTV_FRACTION20_START = 0x324
        private const val NTV_FRACTION32_START = 0x33c
        private const val NTV_RESERVED_START = 0x34c
        fun getMask(type: Int): Int = 1 shl type
        fun getEuropeanDigit(ch: Int): Int = when (ch) {
            in 0x41..0x5a -> ch - 0x41 + 10
            in 0x61..0x7a -> ch - 0x61 + 10
            in 0xff21..0xff3a -> ch - 0xff21 + 10
            in 0xff41..0xff5a -> ch - 0xff41 + 10
            else -> -1
        }
    }
}

// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 1996-2016, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.lang

import io.github.heapy.icu4k.impl.IllegalIcuArgumentException
import io.github.heapy.icu4k.impl.UPropertyAliases
import io.github.heapy.icu4k.impl.CaseMapImpl
import io.github.heapy.icu4k.impl.UCaseProps
import io.github.heapy.icu4k.impl.UCharacterProperty
import io.github.heapy.icu4k.impl.UBiDiProps
import io.github.heapy.icu4k.text.UTF16
import io.github.heapy.icu4k.util.VersionInfo

/**
 * Unicode character operations backed by pinned ICU data on every target.
 * Category/numeric/bidi operations, simple case mappings and full case folding
 * are ported. Locale-sensitive string casing, normalization, names and the
 * complete property dispatcher are added in subsequent stages.
 */
class UCharacter private constructor() {
    interface NumericType {
        companion object {
            const val NONE = 0
            const val DECIMAL = 1
            const val DIGIT = 2
            const val NUMERIC = 3
            @Deprecated("Use property maximum") const val COUNT = 4
        }
    }
    interface HangulSyllableType {
        companion object {
            const val NOT_APPLICABLE = 0
            const val LEADING_JAMO = 1
            const val VOWEL_JAMO = 2
            const val TRAILING_JAMO = 3
            const val LV_SYLLABLE = 4
            const val LVT_SYLLABLE = 5
            @Deprecated("Use property maximum") const val COUNT = 6
        }
    }
    enum class IdentifierStatus { RESTRICTED, ALLOWED }
    enum class IdentifierType { NOT_CHARACTER, DEPRECATED, DEFAULT_IGNORABLE, NOT_NFKC, NOT_XID, EXCLUSION, OBSOLETE, TECHNICAL, UNCOMMON_USE, LIMITED_USE, INCLUSION, RECOMMENDED }
    companion object {
        fun hasIdentifierType(c: Int, type: IdentifierType): Boolean = UCharacterProperty.INSTANCE.hasIDType(c, type.ordinal)
        fun getIdentifierTypes(c: Int, types: MutableSet<IdentifierType>): Int = UCharacterProperty.INSTANCE.getIDTypes(c, types)

        fun getPropertyName(property: Int, nameChoice: Int): String? = UPropertyAliases.INSTANCE.getPropertyName(property, nameChoice)
        fun getPropertyEnum(propertyAlias: CharSequence): Int {
            val value = UPropertyAliases.INSTANCE.getPropertyEnum(propertyAlias)
            if (value == -1) throw IllegalIcuArgumentException("Invalid name: $propertyAlias")
            return value
        }
        fun getPropertyValueName(property: Int, value: Int, nameChoice: Int): String? {
            // Combining classes are unsigned bytes, including values with no UCD alias.
            if ((property == UProperty.CANONICAL_COMBINING_CLASS || property == UProperty.LEAD_CANONICAL_COMBINING_CLASS || property == UProperty.TRAIL_CANONICAL_COMBINING_CLASS)
                && value in 0..255 && nameChoice >= 0 && nameChoice < UProperty.NameChoice.COUNT) {
                try { return UPropertyAliases.INSTANCE.getPropertyValueName(property, value, nameChoice) }
                catch (_: IllegalArgumentException) { return null }
            }
            return UPropertyAliases.INSTANCE.getPropertyValueName(property, value, nameChoice)
        }
        fun getPropertyValueEnum(property: Int, valueAlias: CharSequence): Int {
            val value = UPropertyAliases.INSTANCE.getPropertyValueEnum(property, valueAlias)
            if (value == -1) throw IllegalIcuArgumentException("Invalid name: $valueAlias")
            return value
        }
        fun getPropertyValueEnumNoThrow(property: Int, valueAlias: CharSequence): Int = UPropertyAliases.INSTANCE.getPropertyValueEnumNoThrow(property, valueAlias)

        const val UNASSIGNED: Byte = UCharacterEnums.ECharacterCategory.UNASSIGNED
        const val GENERAL_OTHER_TYPES: Byte = UCharacterEnums.ECharacterCategory.GENERAL_OTHER_TYPES
        const val UPPERCASE_LETTER: Byte = UCharacterEnums.ECharacterCategory.UPPERCASE_LETTER
        const val LOWERCASE_LETTER: Byte = UCharacterEnums.ECharacterCategory.LOWERCASE_LETTER
        const val TITLECASE_LETTER: Byte = UCharacterEnums.ECharacterCategory.TITLECASE_LETTER
        const val MODIFIER_LETTER: Byte = UCharacterEnums.ECharacterCategory.MODIFIER_LETTER
        const val OTHER_LETTER: Byte = UCharacterEnums.ECharacterCategory.OTHER_LETTER
        const val NON_SPACING_MARK: Byte = UCharacterEnums.ECharacterCategory.NON_SPACING_MARK
        const val ENCLOSING_MARK: Byte = UCharacterEnums.ECharacterCategory.ENCLOSING_MARK
        const val COMBINING_SPACING_MARK: Byte = UCharacterEnums.ECharacterCategory.COMBINING_SPACING_MARK
        const val DECIMAL_DIGIT_NUMBER: Byte = UCharacterEnums.ECharacterCategory.DECIMAL_DIGIT_NUMBER
        const val LETTER_NUMBER: Byte = UCharacterEnums.ECharacterCategory.LETTER_NUMBER
        const val OTHER_NUMBER: Byte = UCharacterEnums.ECharacterCategory.OTHER_NUMBER
        const val SPACE_SEPARATOR: Byte = UCharacterEnums.ECharacterCategory.SPACE_SEPARATOR
        const val LINE_SEPARATOR: Byte = UCharacterEnums.ECharacterCategory.LINE_SEPARATOR
        const val PARAGRAPH_SEPARATOR: Byte = UCharacterEnums.ECharacterCategory.PARAGRAPH_SEPARATOR
        const val CONTROL: Byte = UCharacterEnums.ECharacterCategory.CONTROL
        const val FORMAT: Byte = UCharacterEnums.ECharacterCategory.FORMAT
        const val PRIVATE_USE: Byte = UCharacterEnums.ECharacterCategory.PRIVATE_USE
        const val SURROGATE: Byte = UCharacterEnums.ECharacterCategory.SURROGATE
        const val DASH_PUNCTUATION: Byte = UCharacterEnums.ECharacterCategory.DASH_PUNCTUATION
        const val START_PUNCTUATION: Byte = UCharacterEnums.ECharacterCategory.START_PUNCTUATION
        const val END_PUNCTUATION: Byte = UCharacterEnums.ECharacterCategory.END_PUNCTUATION
        const val CONNECTOR_PUNCTUATION: Byte = UCharacterEnums.ECharacterCategory.CONNECTOR_PUNCTUATION
        const val OTHER_PUNCTUATION: Byte = UCharacterEnums.ECharacterCategory.OTHER_PUNCTUATION
        const val MATH_SYMBOL: Byte = UCharacterEnums.ECharacterCategory.MATH_SYMBOL
        const val CURRENCY_SYMBOL: Byte = UCharacterEnums.ECharacterCategory.CURRENCY_SYMBOL
        const val MODIFIER_SYMBOL: Byte = UCharacterEnums.ECharacterCategory.MODIFIER_SYMBOL
        const val OTHER_SYMBOL: Byte = UCharacterEnums.ECharacterCategory.OTHER_SYMBOL
        const val INITIAL_PUNCTUATION: Byte = UCharacterEnums.ECharacterCategory.INITIAL_PUNCTUATION
        const val INITIAL_QUOTE_PUNCTUATION: Byte = UCharacterEnums.ECharacterCategory.INITIAL_QUOTE_PUNCTUATION
        const val FINAL_PUNCTUATION: Byte = UCharacterEnums.ECharacterCategory.FINAL_PUNCTUATION
        const val FINAL_QUOTE_PUNCTUATION: Byte = UCharacterEnums.ECharacterCategory.FINAL_QUOTE_PUNCTUATION
        const val CHAR_CATEGORY_COUNT: Byte = UCharacterEnums.ECharacterCategory.CHAR_CATEGORY_COUNT
        const val LEFT_TO_RIGHT: Int = UCharacterEnums.ECharacterDirection.LEFT_TO_RIGHT
        const val DIRECTIONALITY_LEFT_TO_RIGHT: Byte = UCharacterEnums.ECharacterDirection.DIRECTIONALITY_LEFT_TO_RIGHT
        const val RIGHT_TO_LEFT: Int = UCharacterEnums.ECharacterDirection.RIGHT_TO_LEFT
        const val DIRECTIONALITY_RIGHT_TO_LEFT: Byte = UCharacterEnums.ECharacterDirection.DIRECTIONALITY_RIGHT_TO_LEFT
        const val EUROPEAN_NUMBER: Int = UCharacterEnums.ECharacterDirection.EUROPEAN_NUMBER
        const val DIRECTIONALITY_EUROPEAN_NUMBER: Byte = UCharacterEnums.ECharacterDirection.DIRECTIONALITY_EUROPEAN_NUMBER
        const val EUROPEAN_NUMBER_SEPARATOR: Int = UCharacterEnums.ECharacterDirection.EUROPEAN_NUMBER_SEPARATOR
        const val DIRECTIONALITY_EUROPEAN_NUMBER_SEPARATOR: Byte = UCharacterEnums.ECharacterDirection.DIRECTIONALITY_EUROPEAN_NUMBER_SEPARATOR
        const val EUROPEAN_NUMBER_TERMINATOR: Int = UCharacterEnums.ECharacterDirection.EUROPEAN_NUMBER_TERMINATOR
        const val DIRECTIONALITY_EUROPEAN_NUMBER_TERMINATOR: Byte = UCharacterEnums.ECharacterDirection.DIRECTIONALITY_EUROPEAN_NUMBER_TERMINATOR
        const val ARABIC_NUMBER: Int = UCharacterEnums.ECharacterDirection.ARABIC_NUMBER
        const val DIRECTIONALITY_ARABIC_NUMBER: Byte = UCharacterEnums.ECharacterDirection.DIRECTIONALITY_ARABIC_NUMBER
        const val COMMON_NUMBER_SEPARATOR: Int = UCharacterEnums.ECharacterDirection.COMMON_NUMBER_SEPARATOR
        const val DIRECTIONALITY_COMMON_NUMBER_SEPARATOR: Byte = UCharacterEnums.ECharacterDirection.DIRECTIONALITY_COMMON_NUMBER_SEPARATOR
        const val BLOCK_SEPARATOR: Int = UCharacterEnums.ECharacterDirection.BLOCK_SEPARATOR
        const val DIRECTIONALITY_PARAGRAPH_SEPARATOR: Byte = UCharacterEnums.ECharacterDirection.DIRECTIONALITY_PARAGRAPH_SEPARATOR
        const val SEGMENT_SEPARATOR: Int = UCharacterEnums.ECharacterDirection.SEGMENT_SEPARATOR
        const val DIRECTIONALITY_SEGMENT_SEPARATOR: Byte = UCharacterEnums.ECharacterDirection.DIRECTIONALITY_SEGMENT_SEPARATOR
        const val WHITE_SPACE_NEUTRAL: Int = UCharacterEnums.ECharacterDirection.WHITE_SPACE_NEUTRAL
        const val DIRECTIONALITY_WHITESPACE: Byte = UCharacterEnums.ECharacterDirection.DIRECTIONALITY_WHITESPACE
        const val OTHER_NEUTRAL: Int = UCharacterEnums.ECharacterDirection.OTHER_NEUTRAL
        const val DIRECTIONALITY_OTHER_NEUTRALS: Byte = UCharacterEnums.ECharacterDirection.DIRECTIONALITY_OTHER_NEUTRALS
        const val LEFT_TO_RIGHT_EMBEDDING: Int = UCharacterEnums.ECharacterDirection.LEFT_TO_RIGHT_EMBEDDING
        const val DIRECTIONALITY_LEFT_TO_RIGHT_EMBEDDING: Byte = UCharacterEnums.ECharacterDirection.DIRECTIONALITY_LEFT_TO_RIGHT_EMBEDDING
        const val LEFT_TO_RIGHT_OVERRIDE: Int = UCharacterEnums.ECharacterDirection.LEFT_TO_RIGHT_OVERRIDE
        const val DIRECTIONALITY_LEFT_TO_RIGHT_OVERRIDE: Byte = UCharacterEnums.ECharacterDirection.DIRECTIONALITY_LEFT_TO_RIGHT_OVERRIDE
        const val RIGHT_TO_LEFT_ARABIC: Int = UCharacterEnums.ECharacterDirection.RIGHT_TO_LEFT_ARABIC
        const val DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC: Byte = UCharacterEnums.ECharacterDirection.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC
        const val RIGHT_TO_LEFT_EMBEDDING: Int = UCharacterEnums.ECharacterDirection.RIGHT_TO_LEFT_EMBEDDING
        const val DIRECTIONALITY_RIGHT_TO_LEFT_EMBEDDING: Byte = UCharacterEnums.ECharacterDirection.DIRECTIONALITY_RIGHT_TO_LEFT_EMBEDDING
        const val RIGHT_TO_LEFT_OVERRIDE: Int = UCharacterEnums.ECharacterDirection.RIGHT_TO_LEFT_OVERRIDE
        const val DIRECTIONALITY_RIGHT_TO_LEFT_OVERRIDE: Byte = UCharacterEnums.ECharacterDirection.DIRECTIONALITY_RIGHT_TO_LEFT_OVERRIDE
        const val POP_DIRECTIONAL_FORMAT: Int = UCharacterEnums.ECharacterDirection.POP_DIRECTIONAL_FORMAT
        const val DIRECTIONALITY_POP_DIRECTIONAL_FORMAT: Byte = UCharacterEnums.ECharacterDirection.DIRECTIONALITY_POP_DIRECTIONAL_FORMAT
        const val DIR_NON_SPACING_MARK: Int = UCharacterEnums.ECharacterDirection.DIR_NON_SPACING_MARK
        const val DIRECTIONALITY_NONSPACING_MARK: Byte = UCharacterEnums.ECharacterDirection.DIRECTIONALITY_NONSPACING_MARK
        const val BOUNDARY_NEUTRAL: Int = UCharacterEnums.ECharacterDirection.BOUNDARY_NEUTRAL
        const val DIRECTIONALITY_BOUNDARY_NEUTRAL: Byte = UCharacterEnums.ECharacterDirection.DIRECTIONALITY_BOUNDARY_NEUTRAL
        const val FIRST_STRONG_ISOLATE: Byte = UCharacterEnums.ECharacterDirection.FIRST_STRONG_ISOLATE
        const val LEFT_TO_RIGHT_ISOLATE: Byte = UCharacterEnums.ECharacterDirection.LEFT_TO_RIGHT_ISOLATE
        const val RIGHT_TO_LEFT_ISOLATE: Byte = UCharacterEnums.ECharacterDirection.RIGHT_TO_LEFT_ISOLATE
        const val POP_DIRECTIONAL_ISOLATE: Byte = UCharacterEnums.ECharacterDirection.POP_DIRECTIONAL_ISOLATE
        const val CHAR_DIRECTION_COUNT: Int = UCharacterEnums.ECharacterDirection.CHAR_DIRECTION_COUNT
        const val DIRECTIONALITY_UNDEFINED: Byte = UCharacterEnums.ECharacterDirection.DIRECTIONALITY_UNDEFINED

        const val MIN_VALUE = 0
        const val MAX_VALUE = 0x10ffff
        const val SUPPLEMENTARY_MIN_VALUE = 0x10000
        const val REPLACEMENT_CHAR = 0xfffd
        const val NO_NUMERIC_VALUE = UCharacterProperty.NO_NUMERIC_VALUE
        const val MIN_RADIX = 2
        const val MAX_RADIX = 36

        fun digit(ch: Int): Int = UCharacterProperty.INSTANCE.digit(ch)
        fun digit(ch: Int, radix: Int): Int {
            if (radix !in MIN_RADIX..MAX_RADIX) return -1
            var value = digit(ch)
            if (value < 0) value = UCharacterProperty.getEuropeanDigit(ch)
            return if (value < radix) value else -1
        }
        const val FOLD_CASE_DEFAULT = 0
        const val FOLD_CASE_EXCLUDE_SPECIAL_I = 1
        fun toLowerCase(ch: Int): Int = UCaseProps.INSTANCE.tolower(ch)
        fun toUpperCase(ch: Int): Int = UCaseProps.INSTANCE.toupper(ch)
        fun toTitleCase(ch: Int): Int = UCaseProps.INSTANCE.totitle(ch)
        fun hasBinaryProperty(ch: Int, property: Int): Boolean = UCharacterProperty.INSTANCE.hasBinaryProperty(ch, property)
        fun hasBinaryProperty(s: CharSequence, property: Int): Boolean {
            if (s.length == 1) return hasBinaryProperty(s[0].code, property)
            if (s.length == 2) {
                val c = UTF16.charAt(s, 0)
                if (c > 0xffff) return hasBinaryProperty(c, property)
            }
            return property in UProperty.BASIC_EMOJI..UProperty.RGI_EMOJI && io.github.heapy.icu4k.impl.EmojiProps.INSTANCE.hasBinaryProperty(s, property)
        }
        fun isUAlphabetic(ch: Int): Boolean = hasBinaryProperty(ch, UProperty.ALPHABETIC)
        fun isULowercase(ch: Int): Boolean = hasBinaryProperty(ch, UProperty.LOWERCASE)
        fun isUUppercase(ch: Int): Boolean = hasBinaryProperty(ch, UProperty.UPPERCASE)
        fun isUWhiteSpace(ch: Int): Boolean = hasBinaryProperty(ch, UProperty.WHITE_SPACE)
        fun getIntPropertyValue(ch: Int, type: Int): Int = UCharacterProperty.INSTANCE.getIntPropertyValue(ch, type)
        /** All binary and enumerated properties have minimum zero, including undefined IDs. */
        @Suppress("UNUSED_PARAMETER")
        fun getIntPropertyMinValue(type: Int): Int = 0
        fun getIntPropertyMaxValue(type: Int): Int = UCharacterProperty.INSTANCE.getIntPropertyMaxValue(type)
        fun foldCase(ch: Int, options: Int): Int = UCaseProps.INSTANCE.fold(ch, options)
        fun foldCase(ch: Int, defaultMapping: Boolean): Int = foldCase(ch, if (defaultMapping) FOLD_CASE_DEFAULT else FOLD_CASE_EXCLUDE_SPECIAL_I)
        fun foldCase(str: String, options: Int): String = CaseMapImpl.fold(options, str)
        fun foldCase(str: String, defaultMapping: Boolean): String = foldCase(str, if (defaultMapping) FOLD_CASE_DEFAULT else FOLD_CASE_EXCLUDE_SPECIAL_I)
        fun getNumericValue(ch: Int): Int = UCharacterProperty.INSTANCE.getNumericValue(ch)
        fun getUnicodeNumericValue(ch: Int): Double = UCharacterProperty.INSTANCE.getUnicodeNumericValue(ch)
        fun getType(ch: Int): Int = UCharacterProperty.INSTANCE.getType(ch)
        fun isDefined(ch: Int): Boolean = getType(ch) != 0
        fun isDigit(ch: Int): Boolean = getType(ch) == 9
        fun isISOControl(ch: Int): Boolean = ch in 0..0x9f && (ch <= 0x1f || ch >= 0x7f)
        fun isLetter(ch: Int): Boolean = getType(ch) in 1..5
        fun isLetterOrDigit(ch: Int): Boolean = getType(ch).let { it in 1..5 || it == 9 }
        fun isLowerCase(ch: Int): Boolean = getType(ch) == 2
        fun isUpperCase(ch: Int): Boolean = getType(ch) == 1
        fun isTitleCase(ch: Int): Boolean = getType(ch) == 3
        fun isWhitespace(ch: Int): Boolean =
            (isSpaceChar(ch) && ch != 0x00a0 && ch != 0x2007 && ch != 0x202f) || ch in 0x9..0xd || ch in 0x1c..0x1f
        fun isSpaceChar(ch: Int): Boolean = getType(ch) in 12..14
        fun isIdentifierIgnorable(ch: Int): Boolean = if (ch <= 0x9f) {
            isISOControl(ch) && ch !in 0x9..0xd && ch !in 0x1c..0x1f
        } else getType(ch) == 16
        fun isUnicodeIdentifierStart(ch: Int): Boolean = (UCharacterProperty.INSTANCE.getAdditional(ch, 1) and (1 shl 24)) != 0
        fun isUnicodeIdentifierPart(ch: Int): Boolean = (UCharacterProperty.INSTANCE.getAdditional(ch, 1) and (1 shl 25)) != 0
        @Deprecated("Deprecated in ICU; use isWhitespace or isSpaceChar as appropriate")
        fun isSpace(ch: Int): Boolean = ch == 0x20 || ch == 9 || ch == 10 || ch == 12 || ch == 13
        fun toString(ch: Int): String? = if (ch in MIN_VALUE..MAX_VALUE) UTF16.valueOf(ch) else null
        fun isSupplementary(ch: Int): Boolean = ch in SUPPLEMENTARY_MIN_VALUE..MAX_VALUE
        fun isSupplementaryCodePoint(cp: Int): Boolean = isSupplementary(cp)
        fun isBMP(ch: Int): Boolean = ch in 0..0xffff
        fun isPrintable(ch: Int): Boolean = getType(ch).let { it != 0 && it !in 15..18 }
        fun isBaseForm(ch: Int): Boolean = getType(ch) in 1..11
        fun getDirection(ch: Int): Int = UBiDiProps.INSTANCE.getClass(ch)
        fun getDirectionality(cp: Int): Byte = getDirection(cp).toByte()
        fun isMirrored(ch: Int): Boolean = UBiDiProps.INSTANCE.isMirrored(ch)
        fun getMirror(ch: Int): Int = UBiDiProps.INSTANCE.getMirror(ch)
        fun getBidiPairedBracket(c: Int): Int = UBiDiProps.INSTANCE.getPairedBracket(c)
        fun isLegal(ch: Int): Boolean = ch in MIN_VALUE..MAX_VALUE && ch !in 0xd800..0xdfff &&
            ch !in 0xfdd0..0xfdef && (ch and 0xfffe) != 0xfffe
        fun isLegal(str: String): Boolean {
            var index = 0
            while (index < str.length) {
                val cp = UTF16.charAt(str, index)
                if (!isLegal(cp)) return false
                index += UTF16.getCharCount(cp)
            }
            return true
        }
        fun getUnicodeVersion(): VersionInfo = UCharacterProperty.INSTANCE.m_unicodeVersion_
        fun getAge(ch: Int): VersionInfo {
            require(ch in MIN_VALUE..MAX_VALUE) { "Codepoint out of bounds" }
            return UCharacterProperty.INSTANCE.getAge(ch)
        }
    }
}

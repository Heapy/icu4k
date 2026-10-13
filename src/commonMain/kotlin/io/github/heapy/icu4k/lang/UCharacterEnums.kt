// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 1996-2016, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.lang

/** ICU general-category and bidi-direction constants. */
class UCharacterEnums private constructor() {
    interface ECharacterCategory {
        companion object {
            const val UNASSIGNED: Byte = 0
            const val GENERAL_OTHER_TYPES: Byte = 0
            const val UPPERCASE_LETTER: Byte = 1
            const val LOWERCASE_LETTER: Byte = 2
            const val TITLECASE_LETTER: Byte = 3
            const val MODIFIER_LETTER: Byte = 4
            const val OTHER_LETTER: Byte = 5
            const val NON_SPACING_MARK: Byte = 6
            const val ENCLOSING_MARK: Byte = 7
            const val COMBINING_SPACING_MARK: Byte = 8
            const val DECIMAL_DIGIT_NUMBER: Byte = 9
            const val LETTER_NUMBER: Byte = 10
            const val OTHER_NUMBER: Byte = 11
            const val SPACE_SEPARATOR: Byte = 12
            const val LINE_SEPARATOR: Byte = 13
            const val PARAGRAPH_SEPARATOR: Byte = 14
            const val CONTROL: Byte = 15
            const val FORMAT: Byte = 16
            const val PRIVATE_USE: Byte = 17
            const val SURROGATE: Byte = 18
            const val DASH_PUNCTUATION: Byte = 19
            const val START_PUNCTUATION: Byte = 20
            const val END_PUNCTUATION: Byte = 21
            const val CONNECTOR_PUNCTUATION: Byte = 22
            const val OTHER_PUNCTUATION: Byte = 23
            const val MATH_SYMBOL: Byte = 24
            const val CURRENCY_SYMBOL: Byte = 25
            const val MODIFIER_SYMBOL: Byte = 26
            const val OTHER_SYMBOL: Byte = 27
            const val INITIAL_PUNCTUATION: Byte = 28
            const val INITIAL_QUOTE_PUNCTUATION: Byte = 28
            const val FINAL_PUNCTUATION: Byte = 29
            const val FINAL_QUOTE_PUNCTUATION: Byte = 29
            const val CHAR_CATEGORY_COUNT: Byte = 30
        }
    }
    interface ECharacterDirection {
        companion object {
            const val LEFT_TO_RIGHT: Int = 0
            const val DIRECTIONALITY_LEFT_TO_RIGHT: Byte = LEFT_TO_RIGHT .toByte()
            const val RIGHT_TO_LEFT: Int = 1
            const val DIRECTIONALITY_RIGHT_TO_LEFT: Byte = RIGHT_TO_LEFT .toByte()
            const val EUROPEAN_NUMBER: Int = 2
            const val DIRECTIONALITY_EUROPEAN_NUMBER: Byte = EUROPEAN_NUMBER .toByte()
            const val EUROPEAN_NUMBER_SEPARATOR: Int = 3
            const val DIRECTIONALITY_EUROPEAN_NUMBER_SEPARATOR: Byte = EUROPEAN_NUMBER_SEPARATOR .toByte()
            const val EUROPEAN_NUMBER_TERMINATOR: Int = 4
            const val DIRECTIONALITY_EUROPEAN_NUMBER_TERMINATOR: Byte = EUROPEAN_NUMBER_TERMINATOR .toByte()
            const val ARABIC_NUMBER: Int = 5
            const val DIRECTIONALITY_ARABIC_NUMBER: Byte = ARABIC_NUMBER .toByte()
            const val COMMON_NUMBER_SEPARATOR: Int = 6
            const val DIRECTIONALITY_COMMON_NUMBER_SEPARATOR: Byte = COMMON_NUMBER_SEPARATOR .toByte()
            const val BLOCK_SEPARATOR: Int = 7
            const val DIRECTIONALITY_PARAGRAPH_SEPARATOR: Byte = BLOCK_SEPARATOR .toByte()
            const val SEGMENT_SEPARATOR: Int = 8
            const val DIRECTIONALITY_SEGMENT_SEPARATOR: Byte = SEGMENT_SEPARATOR .toByte()
            const val WHITE_SPACE_NEUTRAL: Int = 9
            const val DIRECTIONALITY_WHITESPACE: Byte = WHITE_SPACE_NEUTRAL .toByte()
            const val OTHER_NEUTRAL: Int = 10
            const val DIRECTIONALITY_OTHER_NEUTRALS: Byte = OTHER_NEUTRAL .toByte()
            const val LEFT_TO_RIGHT_EMBEDDING: Int = 11
            const val DIRECTIONALITY_LEFT_TO_RIGHT_EMBEDDING: Byte = LEFT_TO_RIGHT_EMBEDDING .toByte()
            const val LEFT_TO_RIGHT_OVERRIDE: Int = 12
            const val DIRECTIONALITY_LEFT_TO_RIGHT_OVERRIDE: Byte = LEFT_TO_RIGHT_OVERRIDE .toByte()
            const val RIGHT_TO_LEFT_ARABIC: Int = 13
            const val DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC: Byte = RIGHT_TO_LEFT_ARABIC .toByte()
            const val RIGHT_TO_LEFT_EMBEDDING: Int = 14
            const val DIRECTIONALITY_RIGHT_TO_LEFT_EMBEDDING: Byte = RIGHT_TO_LEFT_EMBEDDING .toByte()
            const val RIGHT_TO_LEFT_OVERRIDE: Int = 15
            const val DIRECTIONALITY_RIGHT_TO_LEFT_OVERRIDE: Byte = RIGHT_TO_LEFT_OVERRIDE .toByte()
            const val POP_DIRECTIONAL_FORMAT: Int = 16
            const val DIRECTIONALITY_POP_DIRECTIONAL_FORMAT: Byte = POP_DIRECTIONAL_FORMAT .toByte()
            const val DIR_NON_SPACING_MARK: Int = 17
            const val DIRECTIONALITY_NONSPACING_MARK: Byte = DIR_NON_SPACING_MARK .toByte()
            const val BOUNDARY_NEUTRAL: Int = 18
            const val DIRECTIONALITY_BOUNDARY_NEUTRAL: Byte = BOUNDARY_NEUTRAL .toByte()
            const val FIRST_STRONG_ISOLATE: Byte = 19
            const val LEFT_TO_RIGHT_ISOLATE: Byte = 20
            const val RIGHT_TO_LEFT_ISOLATE: Byte = 21
            const val POP_DIRECTIONAL_ISOLATE: Byte = 22
            const val CHAR_DIRECTION_COUNT: Int = 23
            const val DIRECTIONALITY_UNDEFINED: Byte = -1
        }
    }
}

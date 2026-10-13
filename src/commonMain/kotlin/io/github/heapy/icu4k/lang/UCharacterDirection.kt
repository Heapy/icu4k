// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 1996-2016, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.lang

/** ICU bidi direction names and identifiers. */
class UCharacterDirection private constructor() : UCharacterEnums.ECharacterDirection {
    companion object {
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
        private val names = arrayOf(
            "Left-to-Right",
            "Right-to-Left",
            "European Number",
            "European Number Separator",
            "European Number Terminator",
            "Arabic Number",
            "Common Number Separator",
            "Paragraph Separator",
            "Segment Separator",
            "Whitespace",
            "Other Neutrals",
            "Left-to-Right Embedding",
            "Left-to-Right Override",
            "Right-to-Left Arabic",
            "Right-to-Left Embedding",
            "Right-to-Left Override",
            "Pop Directional Format",
            "Non-Spacing Mark",
            "Boundary Neutral",
            "First Strong Isolate",
            "Left-to-Right Isolate",
            "Right-to-Left Isolate",
            "Pop Directional Isolate",
        )
        fun toString(value: Int): String = names.getOrNull(value) ?: "Unassigned"
    }
}

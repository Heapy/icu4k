// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 1996-2016, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.lang

/** ICU general category names and identifiers. */
class UCharacterCategory private constructor() : UCharacterEnums.ECharacterCategory {
    companion object {
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
        private val names = arrayOf(
            "Unassigned",
            "Letter, Uppercase",
            "Letter, Lowercase",
            "Letter, Titlecase",
            "Letter, Modifier",
            "Letter, Other",
            "Mark, Non-Spacing",
            "Mark, Enclosing",
            "Mark, Spacing Combining",
            "Number, Decimal Digit",
            "Number, Letter",
            "Number, Other",
            "Separator, Space",
            "Separator, Line",
            "Separator, Paragraph",
            "Other, Control",
            "Other, Format",
            "Other, Private Use",
            "Other, Surrogate",
            "Punctuation, Dash",
            "Punctuation, Open",
            "Punctuation, Close",
            "Punctuation, Connector",
            "Punctuation, Other",
            "Symbol, Math",
            "Symbol, Currency",
            "Symbol, Modifier",
            "Symbol, Other",
            "Punctuation, Initial quote",
            "Punctuation, Final quote",
        )
        fun toString(value: Int): String = names.getOrNull(value) ?: "Unassigned"
    }
}

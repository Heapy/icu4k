// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

/** Context settings used by localized display names. */
enum class DisplayContext(private val contextType: Type, private val contextValue: Int) {
    STANDARD_NAMES(Type.DIALECT_HANDLING, 0),
    DIALECT_NAMES(Type.DIALECT_HANDLING, 1),
    CAPITALIZATION_NONE(Type.CAPITALIZATION, 0),
    CAPITALIZATION_FOR_MIDDLE_OF_SENTENCE(Type.CAPITALIZATION, 1),
    CAPITALIZATION_FOR_BEGINNING_OF_SENTENCE(Type.CAPITALIZATION, 2),
    CAPITALIZATION_FOR_UI_LIST_OR_MENU(Type.CAPITALIZATION, 3),
    CAPITALIZATION_FOR_STANDALONE(Type.CAPITALIZATION, 4),
    LENGTH_FULL(Type.DISPLAY_LENGTH, 0),
    LENGTH_SHORT(Type.DISPLAY_LENGTH, 1),
    SUBSTITUTE(Type.SUBSTITUTE_HANDLING, 0),
    NO_SUBSTITUTE(Type.SUBSTITUTE_HANDLING, 1);
    enum class Type { DIALECT_HANDLING, CAPITALIZATION, DISPLAY_LENGTH, SUBSTITUTE_HANDLING }
    fun type(): Type = contextType
    fun value(): Int = contextValue
}

// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

/** Resolves rule variables and matcher stand-ins and parses reference names. */
interface SymbolTable {
    fun lookup(s: String): CharArray?
    fun lookupMatcher(ch: Int): UnicodeMatcher?
    /** On failure returns null and leaves pos unchanged. */
    fun parseReference(text: String, pos: ParsePosition, limit: Int): String?
    companion object { const val SYMBOL_REF: Char = '$' }
}

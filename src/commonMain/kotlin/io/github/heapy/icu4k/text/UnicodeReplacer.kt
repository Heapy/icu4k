// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

/** Replacement protocol used by transliteration rules and captured segments. */
internal interface UnicodeReplacer {
    fun replace(text: Replaceable, start: Int, limit: Int, cursor: IntArray): Int
    fun toReplacerPattern(escapeUnprintable: Boolean): String
    fun addReplacementSetTo(toUnionTo: UnicodeSet)
}

// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

/** Lenient text comparison used by rule-based number parsing. */
@Deprecated("ICU 54")
interface RbnfLenientScanner {
    @Deprecated("ICU 54")
    fun allIgnorable(s: String?): Boolean
    @Deprecated("ICU 54")
    fun prefixLength(str: String?, prefix: String?): Int
    /** Match start and consumed length; a negative start denotes no match. */
    @Deprecated("ICU 54")
    fun findText(str: String?, key: String?, startingAt: Int): IntArray?
}

// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

/** Shared stand-in table; matching and replacement deliberately use separate checked casts. */
internal class TransliterationData {
    val lock = io.github.heapy.icu4k.impl.createTextTrieMapLock(this)
    var ruleSet = TransliterationRuleSet()
    var variableNames = mutableMapOf<String, CharArray>()
    var variables: Array<Any?>? = null
    var variablesBase: Char = '\u0000'
    fun lookupMatcher(standIn: Int): UnicodeMatcher? {
        val i = standIn - variablesBase.code
        val values = variables!!
        return if (i >= 0 && i < values.size) values[i] as UnicodeMatcher? else null
    }
    fun lookupReplacer(standIn: Int): UnicodeReplacer? {
        val i = standIn - variablesBase.code
        val values = variables!!
        return if (i >= 0 && i < values.size) values[i] as UnicodeReplacer? else null
    }
}

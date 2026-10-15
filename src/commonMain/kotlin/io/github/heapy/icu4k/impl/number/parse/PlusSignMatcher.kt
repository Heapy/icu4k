// © 2017 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.number.parse

import io.github.heapy.icu4k.impl.StaticUnicodeSets
import io.github.heapy.icu4k.impl.StringSegment
import io.github.heapy.icu4k.text.DecimalFormatSymbols
import kotlin.jvm.JvmStatic

open class PlusSignMatcher private constructor(symbol: String?, private val allowTrailing: Boolean) : SymbolMatcher(symbol, StaticUnicodeSets.get(StaticUnicodeSets.Key.PLUS_SIGN)) {
    override fun isDisabled(result: ParsedNumber?): Boolean = !allowTrailing && result!!.seenNumber()
    override fun accept(segment: StringSegment?, result: ParsedNumber?) {
        result!!.setCharsConsumed(segment)
    }
    override fun toString(): String = "<PlusSignMatcher>"
    companion object {
        private val DEFAULT = PlusSignMatcher("", false)
        private val DEFAULT_ALLOW_TRAILING = PlusSignMatcher("", true)
        @JvmStatic fun getInstance(symbols: DecimalFormatSymbols?, allowTrailing: Boolean): PlusSignMatcher {
            val symbol = symbols!!.getPlusSignString()
            if (DEFAULT.uniSet!!.contains(symbol!!)) return if (allowTrailing) DEFAULT_ALLOW_TRAILING else DEFAULT
            return PlusSignMatcher(symbol, allowTrailing)
        }
    }
}

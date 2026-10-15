// © 2017 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.number.parse

import io.github.heapy.icu4k.impl.StaticUnicodeSets
import io.github.heapy.icu4k.impl.StringSegment
import io.github.heapy.icu4k.text.DecimalFormatSymbols
import kotlin.jvm.JvmStatic

open class MinusSignMatcher private constructor(symbol: String?, private val allowTrailing: Boolean) : SymbolMatcher(symbol, StaticUnicodeSets.get(StaticUnicodeSets.Key.MINUS_SIGN)) {
    override fun isDisabled(result: ParsedNumber?): Boolean = !allowTrailing && result!!.seenNumber()
    override fun accept(segment: StringSegment?, result: ParsedNumber?) {
        result!!.flags = result.flags or ParsedNumber.FLAG_NEGATIVE
        result.setCharsConsumed(segment)
    }
    override fun toString(): String = "<MinusSignMatcher>"
    companion object {
        private val DEFAULT = MinusSignMatcher("", false)
        private val DEFAULT_ALLOW_TRAILING = MinusSignMatcher("", true)
        @JvmStatic fun getInstance(symbols: DecimalFormatSymbols?, allowTrailing: Boolean): MinusSignMatcher {
            val symbol = symbols!!.getMinusSignString()
            if (DEFAULT.uniSet!!.contains(symbol!!)) return if (allowTrailing) DEFAULT_ALLOW_TRAILING else DEFAULT
            return MinusSignMatcher(symbol, allowTrailing)
        }
    }
}

// © 2017 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.number.parse

import io.github.heapy.icu4k.impl.StaticUnicodeSets
import io.github.heapy.icu4k.impl.StringSegment
import io.github.heapy.icu4k.text.DecimalFormatSymbols
import kotlin.jvm.JvmStatic

open class PercentMatcher private constructor(symbol: String?) : SymbolMatcher(symbol, StaticUnicodeSets.get(StaticUnicodeSets.Key.PERCENT_SIGN)) {
    override fun isDisabled(result: ParsedNumber?): Boolean = result!!.flags and ParsedNumber.FLAG_PERCENT != 0
    override fun accept(segment: StringSegment?, result: ParsedNumber?) {
        result!!.flags = result.flags or ParsedNumber.FLAG_PERCENT
        result.setCharsConsumed(segment)
    }
    override fun toString(): String = "<PercentMatcher>"
    companion object {
        private val DEFAULT = PercentMatcher("")
        @JvmStatic fun getInstance(symbols: DecimalFormatSymbols?): PercentMatcher {
            val symbol = symbols!!.getPercentString()
            if (DEFAULT.uniSet!!.contains(symbol!!)) return DEFAULT
            return PercentMatcher(symbol)
        }
    }
}

// © 2017 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.number.parse

import io.github.heapy.icu4k.impl.StringSegment
import io.github.heapy.icu4k.text.DecimalFormatSymbols
import io.github.heapy.icu4k.text.UnicodeSet
import kotlin.jvm.JvmStatic

open class NanMatcher private constructor(symbol: String?) : SymbolMatcher(symbol, UnicodeSet.EMPTY) {
    override fun isDisabled(result: ParsedNumber?): Boolean = result!!.seenNumber()
    override fun accept(segment: StringSegment?, result: ParsedNumber?) { result!!.flags = result.flags or ParsedNumber.FLAG_NAN; result.setCharsConsumed(segment) }
    override fun toString(): String = "<NanMatcher>"
    companion object {
        private val DEFAULT = NanMatcher("NaN")
        @JvmStatic fun getInstance(symbols: DecimalFormatSymbols?, parseFlags: Int): NanMatcher {
            val symbol = symbols!!.getNaN()
            return if (DEFAULT.string!!.equals(symbol)) DEFAULT else NanMatcher(symbol)
        }
    }
}

// © 2017 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.number.parse

import io.github.heapy.icu4k.impl.StaticUnicodeSets
import io.github.heapy.icu4k.impl.StringSegment
import io.github.heapy.icu4k.impl.number.DecimalQuantity_DualStorageBCD
import io.github.heapy.icu4k.impl.number.Grouper
import io.github.heapy.icu4k.text.DecimalFormatSymbols
import kotlin.jvm.JvmStatic

open class ScientificMatcher private constructor(symbols: DecimalFormatSymbols?, grouper: Grouper?) : NumberParseMatcher {
    private val exponentSeparatorString = symbols!!.getExponentSeparator()
    private val exponentMatcher = DecimalMatcher.getInstance(symbols, grouper, ParsingUtils.PARSE_FLAG_INTEGER_ONLY or ParsingUtils.PARSE_FLAG_GROUPING_DISABLED)
    private val ignorablesMatcher = IgnorablesMatcher.getInstance(ParsingUtils.PARSE_FLAG_STRICT_IGNORABLES)
    private val customMinusSign: String?
    private val customPlusSign: String?
    init {
        val minusSign = symbols!!.getMinusSignString()
        customMinusSign = if (minusSignSet().contains(minusSign!!)) null else minusSign
        val plusSign = symbols.getPlusSignString()
        customPlusSign = if (plusSignSet().contains(plusSign!!)) null else plusSign
    }
    override fun match(segment: StringSegment?, result: ParsedNumber?): Boolean {
        if (!result!!.seenNumber()) return false
        if (result.flags and ParsedNumber.FLAG_HAS_EXPONENT != 0) return false
        val initialOffset = segment!!.getOffset()
        var overlap = segment.getCommonPrefixLength(exponentSeparatorString)
        if (overlap == exponentSeparatorString!!.length) {
            if (segment.length == overlap) return true
            segment.adjustOffset(overlap)
            ignorablesMatcher.match(segment, null)
            if (segment.length == 0) { segment.setOffset(initialOffset); return true }
            var exponentSign = 1
            if (segment.startsWith(minusSignSet())) { exponentSign = -1; segment.adjustOffsetByCodePoint() }
            else if (segment.startsWith(plusSignSet())) segment.adjustOffsetByCodePoint()
            else if (segment.startsWith(customMinusSign)) {
                overlap = segment.getCommonPrefixLength(customMinusSign)
                if (overlap != customMinusSign!!.length) { segment.setOffset(initialOffset); return true }
                exponentSign = -1; segment.adjustOffset(overlap)
            } else if (segment.startsWith(customPlusSign)) {
                overlap = segment.getCommonPrefixLength(customPlusSign)
                if (overlap != customPlusSign!!.length) { segment.setOffset(initialOffset); return true }
                segment.adjustOffset(overlap)
            }
            if (segment.length == 0) { segment.setOffset(initialOffset); return true }
            ignorablesMatcher.match(segment, null)
            if (segment.length == 0) { segment.setOffset(initialOffset); return true }
            val wasNull = result.quantity == null
            if (wasNull) result.quantity = DecimalQuantity_DualStorageBCD()
            val digitsOffset = segment.getOffset()
            val digitsReturnValue = exponentMatcher.match(segment, result, exponentSign)
            if (wasNull) result.quantity = null
            if (segment.getOffset() != digitsOffset) result.flags = result.flags or ParsedNumber.FLAG_HAS_EXPONENT
            else segment.setOffset(initialOffset)
            return digitsReturnValue
        } else if (overlap == segment.length) return true
        return false
    }
    override fun smokeTest(segment: StringSegment?): Boolean = segment!!.startsWith(exponentSeparatorString)
    override fun postProcess(result: ParsedNumber?) {}
    override fun toString(): String = "<ScientificMatcher $exponentSeparatorString>"
    companion object {
        private fun minusSignSet() = StaticUnicodeSets.get(StaticUnicodeSets.Key.MINUS_SIGN)
        private fun plusSignSet() = StaticUnicodeSets.get(StaticUnicodeSets.Key.PLUS_SIGN)
        @JvmStatic fun getInstance(symbols: DecimalFormatSymbols?, grouper: Grouper?): ScientificMatcher = ScientificMatcher(symbols, grouper)
    }
}

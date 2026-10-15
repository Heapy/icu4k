// © 2017 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.number.parse

import io.github.heapy.icu4k.impl.StringSegment
import io.github.heapy.icu4k.impl.number.DecimalQuantity_DualStorageBCD
import kotlin.jvm.JvmField

/** Mutable parsing result; charEnd tracks strong characters independently of the input cursor. */
open class ParsedNumber {
    @JvmField var quantity: DecimalQuantity_DualStorageBCD? = null
    @JvmField var charEnd: Int = 0
    @JvmField var flags: Int = 0
    @JvmField var prefix: String? = null
    @JvmField var suffix: String? = null
    @JvmField var currencyCode: String? = null
    init { clear() }
    open fun clear() { quantity = null; charEnd = 0; flags = 0; prefix = null; suffix = null; currencyCode = null }
    open fun copyFrom(other: ParsedNumber?) {
        quantity = if (other!!.quantity == null) null else other.quantity!!.createCopy() as DecimalQuantity_DualStorageBCD
        charEnd = other.charEnd; flags = other.flags; prefix = other.prefix; suffix = other.suffix; currencyCode = other.currencyCode
    }
    open fun setCharsConsumed(segment: StringSegment?) { charEnd = segment!!.getOffset() }
    open fun postProcess() { if (quantity != null && flags and FLAG_NEGATIVE != 0) quantity!!.negate() }
    open fun success(): Boolean = charEnd > 0 && flags and FLAG_FAIL == 0
    open fun seenNumber(): Boolean = quantity != null || flags and FLAG_NAN != 0 || flags and FLAG_INFINITY != 0
    open fun getNumber(): Number = getNumber(0)
    open fun getNumber(parseFlags: Int): Number {
        val nan = flags and FLAG_NAN != 0
        val infinity = flags and FLAG_INFINITY != 0
        val forceDecimal = parseFlags and ParsingUtils.PARSE_FLAG_FORCE_BIG_DECIMAL != 0
        val integerOnly = parseFlags and ParsingUtils.PARSE_FLAG_INTEGER_ONLY != 0
        if (nan) return Double.NaN
        if (infinity) return if (flags and FLAG_NEGATIVE != 0) Double.NEGATIVE_INFINITY else Double.POSITIVE_INFINITY
        assertParsedNumber { quantity != null }
        if (quantity!!.isZeroish() && quantity!!.isNegative() && !integerOnly) return -0.0
        return if (quantity!!.fitsInLong() && !forceDecimal) quantity!!.toLong(false) else quantity!!.toBigDecimal()
    }
    internal open fun isBetterThan(other: ParsedNumber?): Boolean = COMPARATOR.compare(this, other) > 0
    companion object {
        const val FLAG_NEGATIVE = 0x0001
        const val FLAG_PERCENT = 0x0002
        const val FLAG_PERMILLE = 0x0004
        const val FLAG_HAS_EXPONENT = 0x0008
        const val FLAG_HAS_DECIMAL_SEPARATOR = 0x0020
        const val FLAG_NAN = 0x0040
        const val FLAG_INFINITY = 0x0080
        const val FLAG_FAIL = 0x0100
        @JvmField val COMPARATOR: Comparator<ParsedNumber?> = Comparator { a, b -> a!!.charEnd - b!!.charEnd }
    }
}
internal expect fun assertParsedNumber(condition: () -> Boolean)

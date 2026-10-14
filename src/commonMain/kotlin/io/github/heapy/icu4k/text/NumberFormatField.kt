// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

/** Extracted numeric field identities for the upcoming Common NumberFormat implementation. */
open class NumberFormatField protected constructor(name: String?) : FormatField(name) {
    protected override fun readResolve(): Any {
        val name = getName()!!
        return canonical.firstOrNull { it.getName() == name } ?: invalidFormatField()
    }
    companion object {
        private const val serialVersionUID = -4516273749929385842L
        val SIGN = NumberFormatField("sign")
        val INTEGER = NumberFormatField("integer")
        val FRACTION = NumberFormatField("fraction")
        val EXPONENT = NumberFormatField("exponent")
        val EXPONENT_SIGN = NumberFormatField("exponent sign")
        val EXPONENT_SYMBOL = NumberFormatField("exponent symbol")
        val DECIMAL_SEPARATOR = NumberFormatField("decimal separator")
        val GROUPING_SEPARATOR = NumberFormatField("grouping separator")
        val PERCENT = NumberFormatField("percent")
        val PERMILLE = NumberFormatField("per mille")
        val CURRENCY = NumberFormatField("currency")
        val MEASURE_UNIT = NumberFormatField("measure unit")
        val COMPACT = NumberFormatField("compact")
        val APPROXIMATELY_SIGN = NumberFormatField("approximately sign")
        // Upstream's readResolve intentionally does not recognize APPROXIMATELY_SIGN.
        private val canonical = listOf(INTEGER, FRACTION, EXPONENT, EXPONENT_SIGN, EXPONENT_SYMBOL,
            CURRENCY, DECIMAL_SEPARATOR, GROUPING_SEPARATOR, PERCENT, PERMILLE, SIGN, MEASURE_UNIT, COMPACT)
    }
}

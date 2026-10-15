// © 2018 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.number

import io.github.heapy.icu4k.text.FormatSpanField
import io.github.heapy.icu4k.text.invalidFormatField
import io.github.heapy.icu4k.util.ULocale
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic

class NumberRangeFormatter private constructor() {
    enum class RangeCollapse { AUTO, NONE, UNIT, ALL }
    enum class RangeIdentityFallback { SINGLE_VALUE, APPROXIMATELY_OR_SINGLE_VALUE, APPROXIMATELY, RANGE }
    enum class RangeIdentityResult { EQUAL_BEFORE_ROUNDING, EQUAL_AFTER_ROUNDING, NOT_EQUAL }
    class SpanField private constructor(name: String) : FormatSpanField(name) {
        protected override fun readResolve(): Any = if (getName()!!.equals(NUMBER_RANGE_SPAN.getName())) NUMBER_RANGE_SPAN else invalidFormatField()
        companion object {
            private const val serialVersionUID = 8750397196515368729L
            @JvmField val NUMBER_RANGE_SPAN = SpanField("number-range-span")
        }
    }
    companion object {
        private val BASE = UnlocalizedNumberRangeFormatter()
        @JvmStatic fun with(): UnlocalizedNumberRangeFormatter = BASE
        @JvmStatic fun withLocale(locale: ULocale?): LocalizedNumberRangeFormatter = BASE.locale(locale)
    }
}

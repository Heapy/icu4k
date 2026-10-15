// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.number

import io.github.heapy.icu4k.impl.ICUData
import io.github.heapy.icu4k.impl.ICUResourceBundle
import io.github.heapy.icu4k.util.ULocale
import io.github.heapy.icu4k.util.UResourceBundle
import kotlin.jvm.JvmStatic
import io.github.heapy.icu4k.number.GroupingStrategy

/** Grouping widths and locale-dependent minimum grouping policy. */
open class Grouper private constructor(private val grouping1: Short, private val grouping2: Short, private val minGrouping: Short) {
    open fun withLocaleData(locale: ULocale?, pattern: PatternStringParser.ParsedPatternInfo?): Grouper {
        val minimum = when (minGrouping.toInt()) {
            -2 -> minimumForLocale(locale)
            -3 -> maxOf(2, minimumForLocale(locale).toInt()).toShort()
            else -> minGrouping
        }
        if (grouping1.toInt() != -2 && grouping2.toInt() != -4) {
            return if (minimum == minGrouping) this else getInstance(grouping1, grouping2, minimum)
        }
        val sizes = pattern!!.positive!!.groupingSizes
        var first = sizes.toShort()
        var second = (sizes ushr 16).toShort()
        val third = (sizes ushr 32).toShort()
        if (second.toInt() == -1) first = if (grouping1.toInt() == -4) 3 else -1
        if (third.toInt() == -1) second = first
        return getInstance(first, second, minimum)
    }
    open fun groupAtPosition(position: Int, value: DecimalQuantity?): Boolean {
        assertGrouper(grouping1.toInt() != -2 && grouping1.toInt() != -4)
        if (grouping1.toInt() == -1 || grouping1.toInt() == 0) return false
        val shifted = position - grouping1
        return shifted >= 0 && shifted % grouping2 == 0 && value!!.getUpperDisplayMagnitude() - grouping1 + 1 >= minGrouping
    }
    open fun getPrimary(): Short = grouping1
    open fun getSecondary(): Short = grouping2
    companion object {
        private val NEVER = Grouper(-1, -1, -2)
        private val MIN2 = Grouper(-2, -2, -3)
        private val AUTO = Grouper(-2, -2, -2)
        private val ON_ALIGNED = Grouper(-4, -4, 1)
        private val WESTERN = Grouper(3, 3, 1)
        private val INDIC = Grouper(3, 2, 1)
        private val WESTERN_MIN2 = Grouper(3, 3, 2)
        private val INDIC_MIN2 = Grouper(3, 2, 2)
        @JvmStatic fun forStrategy(strategy: GroupingStrategy?): Grouper = when (strategy!!) {
            GroupingStrategy.OFF -> NEVER
            GroupingStrategy.MIN2 -> MIN2
            GroupingStrategy.AUTO -> AUTO
            GroupingStrategy.ON_ALIGNED -> ON_ALIGNED
            GroupingStrategy.THOUSANDS -> WESTERN
        }
        @JvmStatic fun forProperties(properties: DecimalFormatProperties?): Grouper {
            if (!properties!!.getGroupingUsed()) return NEVER
            val first = properties.getGroupingSize()
            val second = properties.getSecondaryGroupingSize()
            val minimum = properties.getMinimumGroupingDigits()
            return forProperties(true, first, second, minimum)
        }
        internal fun forProperties(groupingUsed: Boolean, primary: Int, secondary: Int, minimum: Int): Grouper {
            if (!groupingUsed) return NEVER
            var first = primary.toShort()
            var second = secondary.toShort()
            first = if (first > 0) first else if (second > 0) second else first
            second = if (second > 0) second else first
            return getInstance(first, second, minimum.toShort())
        }
        @JvmStatic fun getInstance(first: Short, second: Short, minimum: Short): Grouper = when {
            first.toInt() == -1 -> NEVER
            first.toInt() == 3 && second.toInt() == 3 && minimum.toInt() == 1 -> WESTERN
            first.toInt() == 3 && second.toInt() == 2 && minimum.toInt() == 1 -> INDIC
            first.toInt() == 3 && second.toInt() == 3 && minimum.toInt() == 2 -> WESTERN_MIN2
            first.toInt() == 3 && second.toInt() == 2 && minimum.toInt() == 2 -> INDIC_MIN2
            else -> Grouper(first, second, minimum)
        }
        private fun minimumForLocale(locale: ULocale?): Short {
            val bundle = UResourceBundle.getBundleInstance(ICUData.ICU_BASE_NAME, locale) as ICUResourceBundle
            val text = bundle.getStringWithFallback("NumberElements/minimumGroupingDigits")
            val value = DecimalNumberParsing.int(text)
            if (value !in Short.MIN_VALUE..Short.MAX_VALUE) throw NumberFormatException()
            return value.toShort()
        }
    }
}
internal expect fun assertGrouper(condition: Boolean)

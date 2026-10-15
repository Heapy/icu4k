// © 2017 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.number

import io.github.heapy.icu4k.impl.number.DecimalFormatProperties
import io.github.heapy.icu4k.text.DecimalFormatSymbols
import io.github.heapy.icu4k.util.ULocale
import kotlin.jvm.JvmStatic

/** Entry points for immutable fluent number formatters and number skeletons. */
class NumberFormatter private constructor() {
    typealias UnitWidth = io.github.heapy.icu4k.number.UnitWidth
    typealias GroupingStrategy = io.github.heapy.icu4k.number.GroupingStrategy
    typealias SignDisplay = io.github.heapy.icu4k.number.SignDisplay
    typealias DecimalSeparatorDisplay = io.github.heapy.icu4k.number.DecimalSeparatorDisplay
    typealias RoundingPriority = io.github.heapy.icu4k.number.RoundingPriority
    typealias TrailingZeroDisplay = io.github.heapy.icu4k.number.TrailingZeroDisplay
    companion object {
        private val BASE = UnlocalizedNumberFormatter()
        internal const val DEFAULT_THRESHOLD = 3L
        @JvmStatic fun with(): UnlocalizedNumberFormatter = BASE
        @JvmStatic fun withLocale(locale: ULocale?): LocalizedNumberFormatter = BASE.locale(locale)
        @JvmStatic fun forSkeleton(skeleton: String?): UnlocalizedNumberFormatter = NumberSkeletonImpl.getOrCreate(skeleton)
        @Deprecated("This API is ICU internal only.")
        @JvmStatic fun fromDecimalFormat(properties: DecimalFormatProperties?, symbols: DecimalFormatSymbols?, exportedProperties: DecimalFormatProperties?): UnlocalizedNumberFormatter =
            NumberPropertyMapper.create(properties, symbols, exportedProperties)
    }
}

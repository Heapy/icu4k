// © 2018 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.number

import io.github.heapy.icu4k.impl.number.range.RangeMacroProps
import io.github.heapy.icu4k.number.NumberRangeFormatter.RangeCollapse
import io.github.heapy.icu4k.number.NumberRangeFormatter.RangeIdentityFallback
import io.github.heapy.icu4k.util.ULocale
import kotlin.concurrent.Volatile

abstract class NumberRangeFormatterSettings<T : NumberRangeFormatterSettings<T>> internal constructor(
    private val parent: NumberRangeFormatterSettings<*>?, private val key: Int, private val value: Any?
) {
    @Volatile private var resolvedMacros: RangeMacroProps? = null
    open fun numberFormatterBoth(formatter: UnlocalizedNumberFormatter?): T = create(KEY_SAME_FORMATTERS, true).create(KEY_FORMATTER_1, formatter)
    open fun numberFormatterFirst(formatter: UnlocalizedNumberFormatter?): T = create(KEY_SAME_FORMATTERS, false).create(KEY_FORMATTER_1, formatter)
    open fun numberFormatterSecond(formatter: UnlocalizedNumberFormatter?): T = create(KEY_SAME_FORMATTERS, false).create(KEY_FORMATTER_2, formatter)
    open fun collapse(collapse: RangeCollapse?): T = create(KEY_COLLAPSE, collapse)
    open fun identityFallback(fallback: RangeIdentityFallback?): T = create(KEY_IDENTITY_FALLBACK, fallback)
    internal abstract fun create(key: Int, value: Any?): T
    internal open fun resolve(): RangeMacroProps {
        if (resolvedMacros != null) return resolvedMacros!!
        val macros = RangeMacroProps()
        var seen = 0L
        var current: NumberRangeFormatterSettings<*>? = this
        while (current != null) {
            val bit = 1L shl current.key
            if (seen and bit != 0L) { current = current.parent; continue }
            seen = seen or bit
            when (current.key) {
                KEY_MACROS -> Unit
                KEY_LOCALE -> macros.loc = current.value as ULocale?
                KEY_FORMATTER_1 -> macros.formatter1 = current.value as UnlocalizedNumberFormatter?
                KEY_FORMATTER_2 -> macros.formatter2 = current.value as UnlocalizedNumberFormatter?
                KEY_SAME_FORMATTERS -> macros.sameFormatters = if ((current.value as Boolean?)!!) 1 else 0
                KEY_COLLAPSE -> macros.collapse = current.value as RangeCollapse?
                KEY_IDENTITY_FALLBACK -> macros.identityFallback = current.value as RangeIdentityFallback?
                else -> throw AssertionError("Unknown key: " + current.key)
            }
            current = current.parent
        }
        // Preserve source mutation of the children's cached macro settings.
        if (macros.formatter1 != null) macros.formatter1!!.resolve().loc = macros.loc
        if (macros.formatter2 != null) macros.formatter2!!.resolve().loc = macros.loc
        resolvedMacros = macros
        return macros
    }
    override fun hashCode(): Int = resolve().hashCode()
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is NumberRangeFormatterSettings<*>) return false
        return resolve().equals(other.resolve())
    }
    internal companion object {
        const val KEY_MACROS = 0
        const val KEY_LOCALE = 1
        const val KEY_FORMATTER_1 = 2
        const val KEY_FORMATTER_2 = 3
        const val KEY_SAME_FORMATTERS = 4
        const val KEY_COLLAPSE = 5
        const val KEY_IDENTITY_FALLBACK = 6
        const val KEY_MAX = 7
    }
}

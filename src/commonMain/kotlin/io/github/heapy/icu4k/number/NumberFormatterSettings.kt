// © 2017 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.number

import io.github.heapy.icu4k.impl.number.MacroProps
import io.github.heapy.icu4k.impl.number.Padder
import io.github.heapy.icu4k.text.DecimalFormatSymbols
import io.github.heapy.icu4k.text.DisplayOptions
import io.github.heapy.icu4k.text.NumberingSystem
import io.github.heapy.icu4k.util.MeasureUnit
import io.github.heapy.icu4k.util.RoundingMode
import io.github.heapy.icu4k.util.ULocale
import kotlin.concurrent.Volatile

/** Immutable fluent settings nodes; resolution retains ICU4J's ordering and null semantics. */
abstract class NumberFormatterSettings<T : NumberFormatterSettings<T>> internal constructor(
    private val parent: NumberFormatterSettings<*>?, private val key: Int, private val value: Any?
) {
    @Volatile private var resolvedMacros: MacroProps? = null
    open fun notation(value: Notation?): T = create(KEY_NOTATION, value)
    open fun unit(value: MeasureUnit?): T = create(KEY_UNIT, value)
    open fun perUnit(value: MeasureUnit?): T = create(KEY_PER_UNIT, value)
    open fun precision(value: Precision?): T = create(KEY_PRECISION, value)
    open fun roundingMode(value: RoundingMode?): T = create(KEY_ROUNDING_MODE, value)
    open fun grouping(value: GroupingStrategy?): T = create(KEY_GROUPING, value)
    open fun integerWidth(value: IntegerWidth?): T = create(KEY_INTEGER, value)
    open fun unitWidth(value: UnitWidth?): T = create(KEY_UNIT_WIDTH, value)
    open fun sign(value: SignDisplay?): T = create(KEY_SIGN, value)
    open fun decimal(value: DecimalSeparatorDisplay?): T = create(KEY_DECIMAL, value)
    open fun scale(value: Scale?): T = create(KEY_SCALE, value)
    @Deprecated("This API is ICU internal or technology preview only.")
    open fun unitDisplayCase(value: String?): T = create(KEY_UNIT_DISPLAY_CASE, value)
    @Deprecated("This API is ICU internal or technology preview only.")
    open fun macros(value: MacroProps?): T = create(KEY_MACROS, value)
    @Deprecated("This API is ICU internal or technology preview only.")
    open fun padding(value: Padder?): T = create(KEY_PADDER, value)
    @Deprecated("This API is ICU internal or technology preview only.")
    open fun threshold(value: Long?): T = create(KEY_THRESHOLD, value)
    open fun symbols(symbols: DecimalFormatSymbols?): T = create(KEY_SYMBOLS, symbols!!.clone())
    open fun symbols(symbols: NumberingSystem?): T = create(KEY_SYMBOLS, symbols)
    open fun usage(value: String?): T = if (value != null && value.isEmpty()) create(KEY_USAGE, null) else create(KEY_USAGE, value)
    open fun displayOptions(options: DisplayOptions?): T {
        if (options!!.getGrammaticalCase() == DisplayOptions.GrammaticalCase.UNDEFINED) return create(KEY_UNIT_DISPLAY_CASE, null)
        return create(KEY_UNIT_DISPLAY_CASE, options.getGrammaticalCase()!!.getIdentifier())
    }
    open fun toSkeleton(): String = NumberSkeletonImpl.generate(resolve())
    internal abstract fun create(key: Int, value: Any?): T
    internal open fun resolve(): MacroProps {
        if (resolvedMacros != null) return resolvedMacros!!
        val macros = MacroProps()
        var seen = 0L
        var current: NumberFormatterSettings<*>? = this
        while (current != null) {
            if (current.key != KEY_MACROS) {
                val bit = 1L shl current.key
                if (seen and bit != 0L) { current = current.parent; continue }
                seen = seen or bit
            }
            when (current.key) {
                KEY_MACROS -> macros.fallback(current.value as MacroProps?)
                KEY_LOCALE -> macros.loc = current.value as ULocale?
                KEY_NOTATION -> macros.notation = current.value as Notation?
                KEY_UNIT -> macros.unit = current.value as MeasureUnit?
                KEY_PRECISION -> macros.precision = current.value as Precision?
                KEY_ROUNDING_MODE -> macros.roundingMode = current.value as RoundingMode?
                KEY_GROUPING -> macros.grouping = current.value as Any?
                KEY_PADDER -> macros.padder = current.value as Padder?
                KEY_INTEGER -> macros.integerWidth = current.value as IntegerWidth?
                KEY_SYMBOLS -> macros.symbols = current.value as Any?
                KEY_UNIT_WIDTH -> macros.unitWidth = current.value as UnitWidth?
                KEY_SIGN -> macros.sign = current.value as SignDisplay?
                KEY_DECIMAL -> macros.decimal = current.value as DecimalSeparatorDisplay?
                KEY_SCALE -> macros.scale = current.value as Scale?
                KEY_THRESHOLD -> macros.threshold = current.value as Long?
                KEY_PER_UNIT -> macros.perUnit = current.value as MeasureUnit?
                KEY_USAGE -> macros.usage = current.value as String?
                KEY_UNIT_DISPLAY_CASE -> macros.unitDisplayCase = current.value as String?
                else -> throw AssertionError("Unknown key: " + current.key)
            }
            current = current.parent
        }
        resolvedMacros = macros
        return macros
    }
    override fun hashCode(): Int = resolve().hashCode()
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is NumberFormatterSettings<*>) return false
        return resolve().equals(other.resolve())
    }
    internal companion object {
        const val KEY_MACROS = 0
        const val KEY_LOCALE = 1
        const val KEY_NOTATION = 2
        const val KEY_UNIT = 3
        const val KEY_PRECISION = 4
        const val KEY_ROUNDING_MODE = 5
        const val KEY_GROUPING = 6
        const val KEY_PADDER = 7
        const val KEY_INTEGER = 8
        const val KEY_SYMBOLS = 9
        const val KEY_UNIT_WIDTH = 10
        const val KEY_SIGN = 11
        const val KEY_DECIMAL = 12
        const val KEY_SCALE = 13
        const val KEY_THRESHOLD = 14
        const val KEY_PER_UNIT = 15
        const val KEY_USAGE = 16
        const val KEY_UNIT_DISPLAY_CASE = 17
        const val KEY_MAX = 18
    }
}

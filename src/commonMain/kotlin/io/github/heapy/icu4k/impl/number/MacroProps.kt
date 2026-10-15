// © 2017 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.number

import io.github.heapy.icu4k.number.DecimalSeparatorDisplay
import io.github.heapy.icu4k.number.IntegerWidth
import io.github.heapy.icu4k.number.Notation
import io.github.heapy.icu4k.number.Precision
import io.github.heapy.icu4k.number.Scale
import io.github.heapy.icu4k.number.SignDisplay
import io.github.heapy.icu4k.number.UnitWidth
import io.github.heapy.icu4k.text.PluralRules
import io.github.heapy.icu4k.util.MeasureUnit
import io.github.heapy.icu4k.util.RoundingMode
import io.github.heapy.icu4k.util.ULocale
import kotlin.jvm.JvmField

/** Mutable formatter settings before resolving locale-dependent pipeline stages. */
open class MacroProps : MacroPropsCloneBase() {
    @JvmField var notation: Notation? = null
    @JvmField var unit: MeasureUnit? = null
    @JvmField var perUnit: MeasureUnit? = null
    @JvmField var precision: Precision? = null
    @JvmField var roundingMode: RoundingMode? = null
    @JvmField var grouping: Any? = null
    @JvmField var padder: Padder? = null
    @JvmField var integerWidth: IntegerWidth? = null
    @JvmField var symbols: Any? = null
    @JvmField var unitWidth: UnitWidth? = null
    @JvmField var unitDisplayCase: String? = null
    @JvmField var sign: SignDisplay? = null
    @JvmField var approximately: Boolean? = null
    @JvmField var decimal: DecimalSeparatorDisplay? = null
    @JvmField var scale: Scale? = null
    @JvmField var usage: String? = null
    @JvmField var affixProvider: AffixPatternProvider? = null
    @JvmField var rules: PluralRules? = null
    @JvmField var threshold: Long? = null
    @JvmField var loc: ULocale? = null

    /** Fill unset settings; the internal regulation threshold is deliberately excluded. */
    open fun fallback(fallback: MacroProps?) {
        if (notation == null) notation = fallback!!.notation
        if (unit == null) unit = fallback!!.unit
        if (perUnit == null) perUnit = fallback!!.perUnit
        if (precision == null) precision = fallback!!.precision
        if (roundingMode == null) roundingMode = fallback!!.roundingMode
        if (grouping == null) grouping = fallback!!.grouping
        if (padder == null) padder = fallback!!.padder
        if (integerWidth == null) integerWidth = fallback!!.integerWidth
        if (symbols == null) symbols = fallback!!.symbols
        if (unitWidth == null) unitWidth = fallback!!.unitWidth
        if (unitDisplayCase == null) unitDisplayCase = fallback!!.unitDisplayCase
        if (sign == null) sign = fallback!!.sign
        if (approximately == null) approximately = fallback!!.approximately
        if (decimal == null) decimal = fallback!!.decimal
        if (affixProvider == null) affixProvider = fallback!!.affixProvider
        if (scale == null) scale = fallback!!.scale
        if (usage == null) usage = fallback!!.usage
        if (rules == null) rules = fallback!!.rules
        if (loc == null) loc = fallback!!.loc
    }

    override fun hashCode(): Int {
        // Objects.hash snapshots all arguments before invoking any user hashCode.
        val values = arrayOf(notation, unit, perUnit, precision, roundingMode, grouping, padder,
            integerWidth, symbols, unitWidth, unitDisplayCase, sign, approximately, decimal,
            affixProvider, scale, usage, rules, loc)
        var result = 1
        for (value in values) result = 31 * result + (value?.hashCode() ?: 0)
        return result
    }

    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (this === other) return true
        if (other !is MacroProps) return false
        return sameOrEquals(notation, other.notation) &&
            sameOrEquals(unit, other.unit) &&
            sameOrEquals(perUnit, other.perUnit) &&
            sameOrEquals(precision, other.precision) &&
            sameOrEquals(roundingMode, other.roundingMode) &&
            sameOrEquals(grouping, other.grouping) &&
            sameOrEquals(padder, other.padder) &&
            sameOrEquals(integerWidth, other.integerWidth) &&
            sameOrEquals(symbols, other.symbols) &&
            sameOrEquals(unitWidth, other.unitWidth) &&
            sameOrEquals(unitDisplayCase, other.unitDisplayCase) &&
            sameOrEquals(sign, other.sign) &&
            sameOrEquals(approximately, other.approximately) &&
            sameOrEquals(decimal, other.decimal) &&
            sameOrEquals(affixProvider, other.affixProvider) &&
            sameOrEquals(scale, other.scale) &&
            sameOrEquals(usage, other.usage) &&
            sameOrEquals(rules, other.rules) &&
            sameOrEquals(loc, other.loc)
    }

    public override fun clone(): MacroProps {
        val host = cloneHost()
        if (host != null) return host as MacroProps
        return MacroProps().also {
            it.notation = notation; it.unit = unit; it.perUnit = perUnit; it.precision = precision
            it.roundingMode = roundingMode; it.grouping = grouping; it.padder = padder
            it.integerWidth = integerWidth; it.symbols = symbols; it.unitWidth = unitWidth
            it.unitDisplayCase = unitDisplayCase; it.sign = sign; it.approximately = approximately
            it.decimal = decimal; it.scale = scale; it.usage = usage; it.affixProvider = affixProvider
            it.rules = rules; it.threshold = threshold; it.loc = loc
        }
    }

    private companion object {
        fun sameOrEquals(a: Any?, b: Any?): Boolean = a === b || (a != null && a.equals(b))
    }
}

/** Shallow host cloning without adding Java serialization to MacroProps. */
expect abstract class MacroPropsCloneBase() {
    protected abstract fun clone(): Any
    protected fun cloneHost(): Any?
}

// © 2017 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.number

import io.github.heapy.icu4k.impl.FormattedStringBuilder
import io.github.heapy.icu4k.impl.IllegalIcuArgumentException
import io.github.heapy.icu4k.impl.StandardPlural
import io.github.heapy.icu4k.impl.number.*
import io.github.heapy.icu4k.text.DecimalFormatSymbols
import io.github.heapy.icu4k.text.NumberingSystem
import io.github.heapy.icu4k.text.PluralRules
import io.github.heapy.icu4k.util.Currency
import io.github.heapy.icu4k.util.MeasureUnit

/** The ICU number pipeline, assembled from macro settings in source evaluation order. */
internal open class NumberFormatterImpl(macros: MacroProps?) {
    internal val micros = MicroProps(true)
    internal val microPropsGenerator = macrosToMicroGenerator(macros, micros, true)

    open fun format(value: DecimalQuantity?, output: FormattedStringBuilder?): MicroProps {
        val result = preProcess(value)
        val length = writeNumber(result, value, output, 0)
        writeAffixes(result, output, 0, length)
        return result
    }
    open fun preProcess(value: DecimalQuantity?): MicroProps {
        val result = microPropsGenerator.processQuantity(value)
        applyIntegerWidth(result, value)
        return result!!
    }
    open fun getPrefixSuffix(signum: Byte, plural: StandardPlural?, output: FormattedStringBuilder?): Int =
        getPrefixSuffixImpl(microPropsGenerator, signum, output)
    open fun getRawMicroProps(): MicroProps = micros

    companion object {
        private val DEFAULT_CURRENCY = Currency.getInstance("XXX")
        fun formatStatic(macros: MacroProps?, value: DecimalQuantity?, output: FormattedStringBuilder?): MicroProps {
            val result = preProcessUnsafe(macros, value)
            val length = writeNumber(result, value, output, 0)
            writeAffixes(result, output, 0, length)
            return result
        }
        fun getPrefixSuffixStatic(macros: MacroProps?, signum: Byte, plural: StandardPlural?, output: FormattedStringBuilder?): Int =
            getPrefixSuffixImpl(macrosToMicroGenerator(macros, MicroProps(false), false), signum, output)
        fun writeNumber(micros: MicroProps?, value: DecimalQuantity?, output: FormattedStringBuilder?, index: Int): Int =
            NumberFormatterOutput.writeNumber(micros, value, output, index)
        fun writeAffixes(micros: MicroProps?, output: FormattedStringBuilder?, start: Int, end: Int): Int =
            NumberFormatterOutput.writeAffixes(micros, output, start, end)
        private fun applyIntegerWidth(micros: MicroProps?, value: DecimalQuantity?) {
            if (micros!!.integerWidth!!.maxInt == -1) value!!.setMinInteger(micros.integerWidth!!.minInt)
            else {
                value!!.setMinInteger(micros.integerWidth!!.minInt)
                value.applyMaxInteger(micros.integerWidth!!.maxInt)
            }
        }
        private fun preProcessUnsafe(macros: MacroProps?, value: DecimalQuantity?): MicroProps {
            val chain = macrosToMicroGenerator(macros, MicroProps(false), false)
            val result = chain.processQuantity(value)
            applyIntegerWidth(result, value)
            return result!!
        }
        private fun getPrefixSuffixImpl(chain: MicroPropsGenerator, signum: Byte, output: FormattedStringBuilder?): Int {
            val quantity = DecimalQuantity_DualStorageBCD(0)
            if (signum < 0) quantity.negate()
            val result = chain.processQuantity(quantity)
            result!!.modMiddle!!.apply(output, 0, 0)
            return result.modMiddle!!.getPrefixLength()
        }
        private fun unitIsCurrency(unit: MeasureUnit?): Boolean = unit != null && "currency" == unit.getType()
        private fun unitIsPercent(unit: MeasureUnit?): Boolean = unit != null && "percent" == unit.getSubtype()
        private fun unitIsPermille(unit: MeasureUnit?): Boolean = unit != null && "permille" == unit.getSubtype()

        private fun macrosToMicroGenerator(input: MacroProps?, micros: MicroProps, safe: Boolean): MicroPropsGenerator {
            var chain: MicroPropsGenerator = micros
            val macros = input!!
            val currencyUnit = unitIsCurrency(macros.unit)
            val baseUnit = macros.unit == null
            val percent = unitIsPercent(macros.unit)
            val permille = unitIsPermille(macros.unit)
            val compact = macros.notation is CompactNotation
            val accounting = macros.sign == SignDisplay.ACCOUNTING || macros.sign == SignDisplay.ACCOUNTING_ALWAYS ||
                macros.sign == SignDisplay.ACCOUNTING_EXCEPT_ZERO || macros.sign == SignDisplay.ACCOUNTING_NEGATIVE
            val currency = if (currencyUnit) macros.unit as Currency? else DEFAULT_CURRENCY
            var width: UnitWidth? = UnitWidth.SHORT
            if (macros.unitWidth != null) width = macros.unitWidth
            val cldr = !currencyUnit && !baseUnit && (width == UnitWidth.FULL_NAME || !(percent || permille) || compact)
            val mixed = cldr && macros.unit!!.getType() == null && macros.unit!!.getComplexity() == MeasureUnit.Complexity.MIXED
            var rules = macros.rules
            val ns = if (macros.symbols is NumberingSystem) macros.symbols as NumberingSystem else NumberingSystem.getInstance(macros.loc)
            micros.nsName = ns.getName()
            micros.gender = ""
            if (macros.symbols is DecimalFormatSymbols) micros.symbols = macros.symbols as DecimalFormatSymbols
            else {
                micros.symbols = DecimalFormatSymbols.forNumberingSystem(macros.loc, ns)
                if (currencyUnit) micros.symbols!!.setCurrency(currency)
            }
            var pattern: String? = null
            if (currencyUnit && micros.symbols!!.getCurrencyPattern() != null) pattern = micros.symbols!!.getCurrencyPattern()
            if (pattern == null) {
                val style = when {
                    cldr -> NumberFormatPatternData.NUMBERSTYLE
                    percent || permille -> NumberFormatPatternData.PERCENTSTYLE
                    !currencyUnit || width == UnitWidth.FULL_NAME -> NumberFormatPatternData.NUMBERSTYLE
                    accounting -> NumberFormatPatternData.ACCOUNTINGCURRENCYSTYLE
                    else -> NumberFormatPatternData.CURRENCYSTYLE
                }
                pattern = NumberFormatPatternData.getPatternForStyleAndNumberingSystem(macros.loc, micros.nsName, style)
            }
            val patternInfo = PatternStringParser.parseToPatternInfo(pattern)
            var usage: UsagePrefsHandler? = null
            if (macros.usage != null) {
                if (!cldr) throw IllegalIcuArgumentException("We only support \"usage\" when the input unit is specified, and is a CLDR Unit.")
                usage = UsagePrefsHandler(macros.loc, macros.unit, macros.usage, chain)
                chain = usage
            } else if (mixed) chain = UnitConversionHandler(macros.unit, chain)
            if (macros.scale != null) chain = MultiplierFormatHandler(macros.scale, chain)
            micros.rounder = when {
                macros.precision != null -> macros.precision
                compact -> Precision.COMPACT_STRATEGY
                currencyUnit -> Precision.MONETARY_STANDARD
                macros.usage != null -> Precision.BOGUS_PRECISION
                else -> Precision.DEFAULT_MAX_FRAC_6
            }
            if (macros.roundingMode != null) micros.rounder = micros.rounder!!.withMode(RoundingUtils.mathContextUnlimited(macros.roundingMode))
            micros.rounder = micros.rounder!!.withLocaleData(currency)
            micros.grouping = when {
                macros.grouping is Grouper -> macros.grouping as Grouper
                macros.grouping is GroupingStrategy -> Grouper.forStrategy(macros.grouping as GroupingStrategy)
                compact -> Grouper.forStrategy(GroupingStrategy.MIN2)
                else -> Grouper.forStrategy(GroupingStrategy.AUTO)
            }
            micros.grouping = micros.grouping!!.withLocaleData(macros.loc, patternInfo)
            micros.padding = if (macros.padder != null) macros.padder else Padder.NONE
            micros.integerWidth = if (macros.integerWidth != null) macros.integerWidth else IntegerWidth.DEFAULT
            micros.sign = if (macros.sign != null) macros.sign else SignDisplay.AUTO
            micros.decimal = if (macros.decimal != null) macros.decimal else DecimalSeparatorDisplay.AUTO
            micros.useCurrency = currencyUnit
            if (macros.notation is ScientificNotation) chain = (macros.notation as ScientificNotation).withLocaleData(micros.symbols, safe, chain)
            else micros.modInner = ConstantAffixModifier.EMPTY
            val patternMod = MutablePatternModifier(false)
            val affixes = if (macros.affixProvider != null && (!compact || currencyUnit == macros.affixProvider!!.hasCurrencySign())) macros.affixProvider else patternInfo
            patternMod.setPatternInfo(affixes, null)
            val approximately = if (macros.approximately != null) macros.approximately!! else false
            patternMod.setPatternAttributes(micros.sign, permille, approximately)
            if (patternMod.needsPlurals()) {
                if (rules == null) rules = PluralRules.forLocale(macros.loc)
                patternMod.setSymbols(micros.symbols, currency, width, rules)
            } else patternMod.setSymbols(micros.symbols, currency, width, null)
            val immutable = if (safe) patternMod.createImmutable() else null
            if (affixes!!.currencyAsDecimal()) micros.currencyAsDecimal = patternMod.getCurrencySymbolForUnitWidth()
            if (cldr) {
                var case: String? = null
                if (macros.unitDisplayCase != null) case = macros.unitDisplayCase
                if (rules == null) rules = PluralRules.forLocale(macros.loc)
                val plurals = if (macros.rules != null) macros.rules else PluralRules.forLocale(macros.loc)
                if (macros.usage != null) {
                    assertNumberFormatterImpl { usage != null }
                    chain = LongNameMultiplexer.forMeasureUnits(macros.loc, usage!!.getOutputUnits(), width, case, plurals, chain)
                } else if (mixed) chain = MixedUnitLongNameHandler.forMeasureUnit(macros.loc, macros.unit, width, case, plurals, chain)
                else {
                    var unit = macros.unit
                    if (macros.perUnit != null) {
                        unit = unit!!.product(macros.perUnit!!.reciprocal())
                        if (unit.getType() == null && (macros.unit!!.getType() == null || macros.perUnit!!.getType() == null))
                            throw UnsupportedOperationException("perUnit() can only be used if unit and perUnit are both built-ins, or the combination is a built-in")
                    }
                    chain = LongNameHandler.forMeasureUnit(macros.loc, unit, width, case, plurals, chain)
                }
            } else if (currencyUnit && width == UnitWidth.FULL_NAME) {
                if (rules == null) rules = PluralRules.forLocale(macros.loc)
                chain = LongNameHandler.forCurrencyLongNames(macros.loc, currency, rules, chain)
            } else micros.modOuter = ConstantAffixModifier.EMPTY
            if (compact) {
                if (rules == null) rules = PluralRules.forLocale(macros.loc)
                val type = if (macros.unit is Currency && macros.unitWidth != UnitWidth.FULL_NAME) CompactData.CompactType.CURRENCY else CompactData.CompactType.DECIMAL
                chain = (macros.notation as CompactNotation).withLocaleData(macros.loc, micros.nsName, type, rules, patternMod, safe, chain)
            }
            return if (safe) immutable!!.addToChain(chain) else patternMod.addToChain(chain)
        }
    }
}

internal expect fun assertNumberFormatterImpl(condition: () -> Boolean)

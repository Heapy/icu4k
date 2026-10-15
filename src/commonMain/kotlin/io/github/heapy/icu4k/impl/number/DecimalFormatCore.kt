// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.number

import io.github.heapy.icu4k.impl.FormattedStringBuilder
import io.github.heapy.icu4k.impl.FormattedValueStringBuilderImpl
import io.github.heapy.icu4k.impl.Utility
import io.github.heapy.icu4k.impl.number.parse.NumberParserImpl
import io.github.heapy.icu4k.impl.number.parse.ParsedNumber
import io.github.heapy.icu4k.number.LocalizedNumberFormatter
import io.github.heapy.icu4k.number.NumberFormatter
import io.github.heapy.icu4k.text.CurrencyPluralInfo
import io.github.heapy.icu4k.text.DecimalFormatSymbols
import io.github.heapy.icu4k.text.FormatAttributedCharacterIterator
import io.github.heapy.icu4k.text.FormatFieldPosition
import io.github.heapy.icu4k.text.ParsePosition
import io.github.heapy.icu4k.text.PluralRules
import io.github.heapy.icu4k.util.BigDecimal
import io.github.heapy.icu4k.util.Currency
import io.github.heapy.icu4k.util.CurrencyAmount
import io.github.heapy.icu4k.util.ULocale
import io.github.heapy.icu4k.math.BigDecimal as IcuBigDecimal
import io.github.heapy.icu4k.util.BigDecimals
import io.github.heapy.icu4k.util.RoundingModes
import io.github.heapy.icu4k.util.RoundingMode
import io.github.heapy.icu4k.util.MathContext
import io.github.heapy.icu4k.math.MathContext as IcuMathContext
import kotlin.math.pow
import kotlin.concurrent.Volatile

/** DecimalFormat's mutable configuration and published runtime, before its public inheritance facade.
 * Configuration access must be serialized by the owning facade's monitor; formatting and lazy parser
 * access follow the source's volatile publication and deliberately permit duplicate parser creation.
 */
internal class DecimalFormatCore private constructor() {
    internal var rawProperties: DecimalFormatProperties? = null
    var properties: DecimalFormatProperties
        get() = rawProperties!!
        set(value) { rawProperties = value }
    @Volatile internal var rawSymbols: DecimalFormatSymbols? = null
    var symbols: DecimalFormatSymbols
        get() = rawSymbols!!
        set(value) { rawSymbols = value }
    @Volatile var exportedProperties: DecimalFormatProperties? = null
    @Volatile private var formatter: LocalizedNumberFormatter? = null
    @Volatile private var parser: NumberParserImpl? = null
    @Volatile private var currencyParser: NumberParserImpl? = null
    var actualLocale: ULocale? = null
    internal var actualLocaleProvider: (() -> ULocale?)? = null
    internal var icuMathContextForm: Int = IcuMathContext.PLAIN

    fun copy(locale: ULocale? = actualLocale): DecimalFormatCore = DecimalFormatCore().also {
        it.actualLocale = locale
        it.icuMathContextForm = icuMathContextForm
        it.symbols = symbols.clone()
        it.properties = properties.clone()
        it.exportedProperties = DecimalFormatProperties()
        it.refreshFormatter()
    }
    fun applyPattern(pattern: String?) {
        setPropertiesFromPattern(pattern, PatternStringParser.IGNORE_ROUNDING_NEVER)
        properties.setPositivePrefix(null)
        properties.setNegativePrefix(null)
        properties.setPositiveSuffix(null)
        properties.setNegativeSuffix(null)
        properties.setCurrencyPluralInfo(null)
        refreshFormatter()
    }
    fun applyLocalizedPattern(pattern: String?) {
        applyPattern(PatternStringUtils.convertLocalized(pattern, symbols, false))
    }
    fun setDecimalFormatSymbols(value: DecimalFormatSymbols?) {
        symbols = value!!.clone()
        refreshFormatter()
    }
    fun getDecimalFormatSymbols(): DecimalFormatSymbols = symbols.clone()
    // Source configuration operations: the public facade supplies its reentrant monitor.
    fun getPositivePrefix(): String = formatter!!.getAffixImpl(true, false)
    fun setPositivePrefix(value: String?) {
        properties.setPositivePrefix(value!!)
        refreshFormatter()
    }
    fun getNegativePrefix(): String = formatter!!.getAffixImpl(true, true)
    fun setNegativePrefix(value: String?) {
        properties.setNegativePrefix(value!!)
        refreshFormatter()
    }
    fun getPositiveSuffix(): String = formatter!!.getAffixImpl(false, false)
    fun setPositiveSuffix(value: String?) {
        properties.setPositiveSuffix(value!!)
        refreshFormatter()
    }
    fun getNegativeSuffix(): String = formatter!!.getAffixImpl(false, true)
    fun setNegativeSuffix(value: String?) {
        properties.setNegativeSuffix(value!!)
        refreshFormatter()
    }
    fun getMultiplier(): Int {
        return if (properties.getMultiplier() != null) properties.getMultiplier()!!.toInt()
            else 10.0.pow(properties.getMagnitudeMultiplier()).toInt()
    }
    fun setMultiplier(multiplier: Int) {
        if (multiplier == 0) throw IllegalArgumentException("Multiplier must be nonzero.")
        var delta = 0
        var value = multiplier
        while (value != 1) {
            delta++
            val temp = value / 10
            if (temp * 10 != value) { delta = -1; break }
            value = temp
        }
        if (delta != -1) {
            properties.setMagnitudeMultiplier(delta)
            properties.setMultiplier(null)
        } else {
            properties.setMagnitudeMultiplier(0)
            properties.setMultiplier(BigDecimals.valueOf(multiplier.toLong()))
        }
        refreshFormatter()
    }
    fun getRoundingIncrement(): BigDecimal? = exportedProperties!!.getRoundingIncrement()
    fun setRoundingIncrement(increment: BigDecimal?) {
        if (increment != null && increment.compareTo(BigDecimals.ZERO) == 0) {
            properties.setMaximumFractionDigits(Int.MAX_VALUE)
            return
        }
        properties.setRoundingIncrement(increment)
        refreshFormatter()
    }
    fun setRoundingIncrement(increment: IcuBigDecimal?) { setRoundingIncrement(increment?.toBigDecimal()) }
    fun setRoundingIncrement(increment: Double) {
        if (increment == 0.0) setRoundingIncrement(null as BigDecimal?)
        else setRoundingIncrement(BigDecimals.valueOf(increment).stripTrailingZeros())
    }
    fun getRoundingMode(): Int = exportedProperties!!.getRoundingMode()?.ordinal ?: 0
    fun setRoundingMode(value: Int) { properties.setRoundingMode(RoundingModes.valueOf(value)); refreshFormatter() }
    fun getMathContext(): MathContext? {
        val context = exportedProperties!!.getMathContext()
        assertDecimalFormatCore { context != null }
        return context
    }
    fun setMathContext(value: MathContext?) { properties.setMathContext(value); refreshFormatter() }
    fun getMathContextICU(): IcuMathContext {
        val context = getMathContext()!!
        return IcuMathContext(context.getPrecision(), icuMathContextForm, false, context.getRoundingMode().ordinal)
    }
    fun setMathContextICU(value: IcuMathContext?) {
        icuMathContextForm = value!!.getForm()
        val context = if (value.getLostDigits()) MathContext(value.getDigits(), RoundingMode.UNNECESSARY)
            else MathContext(value.getDigits(), RoundingModes.valueOf(value.getRoundingMode()))
        setMathContext(context)
    }
    fun getMinimumIntegerDigits(): Int = exportedProperties!!.getMinimumIntegerDigits()
    fun setMinimumIntegerDigits(value: Int) {
        val other = properties.getMaximumIntegerDigits()
        if (other >= 0 && other < value) properties.setMaximumIntegerDigits(value)
        properties.setMinimumIntegerDigits(value)
        refreshFormatter()
    }
    fun getMaximumIntegerDigits(): Int = exportedProperties!!.getMaximumIntegerDigits()
    fun setMaximumIntegerDigits(value: Int) {
        val other = properties.getMinimumIntegerDigits()
        if (other >= 0 && other > value) properties.setMinimumIntegerDigits(value)
        properties.setMaximumIntegerDigits(value)
        refreshFormatter()
    }
    fun getMinimumFractionDigits(): Int = exportedProperties!!.getMinimumFractionDigits()
    fun setMinimumFractionDigits(value: Int) {
        val other = properties.getMaximumFractionDigits()
        if (other >= 0 && other < value) properties.setMaximumFractionDigits(value)
        properties.setMinimumFractionDigits(value)
        refreshFormatter()
    }
    fun getMaximumFractionDigits(): Int = exportedProperties!!.getMaximumFractionDigits()
    fun setMaximumFractionDigits(value: Int) {
        val other = properties.getMinimumFractionDigits()
        if (other >= 0 && other > value) properties.setMinimumFractionDigits(value)
        properties.setMaximumFractionDigits(value)
        refreshFormatter()
    }
    fun getMinimumSignificantDigits(): Int = exportedProperties!!.getMinimumSignificantDigits()
    fun setMinimumSignificantDigits(value: Int) {
        val other = properties.getMaximumSignificantDigits()
        if (other >= 0 && other < value) properties.setMaximumSignificantDigits(value)
        properties.setMinimumSignificantDigits(value)
        refreshFormatter()
    }
    fun getMaximumSignificantDigits(): Int = exportedProperties!!.getMaximumSignificantDigits()
    fun setMaximumSignificantDigits(value: Int) {
        val other = properties.getMinimumSignificantDigits()
        if (other >= 0 && other > value) properties.setMinimumSignificantDigits(value)
        properties.setMaximumSignificantDigits(value)
        refreshFormatter()
    }
    fun areSignificantDigitsUsed(): Boolean = properties.getMinimumSignificantDigits() != -1 || properties.getMaximumSignificantDigits() != -1
    fun setSignificantDigitsUsed(value: Boolean) {
        val min = properties.getMinimumSignificantDigits()
        val max = properties.getMaximumSignificantDigits()
        if (value) { if (min != -1 || max != -1) return }
        else { if (min == -1 && max == -1) return }
        properties.setMinimumSignificantDigits(if (value) 1 else -1)
        properties.setMaximumSignificantDigits(if (value) 6 else -1)
        refreshFormatter()
    }
    fun getFormatWidth(): Int = properties.getFormatWidth()
    fun setFormatWidth(value: Int) { properties.setFormatWidth(value); refreshFormatter() }
    fun getPadCharacter(): Char = (properties.getPadString() ?: Padder.FALLBACK_PADDING_STRING)[0]
    fun setPadCharacter(value: Char) { properties.setPadString(value.toString()); refreshFormatter() }
    fun getPadPosition(): Int = properties.getPadPosition()?.toOld() ?: PAD_BEFORE_PREFIX
    fun setPadPosition(value: Int) { properties.setPadPosition(Padder.PadPosition.fromOld(value)); refreshFormatter() }
    fun isScientificNotation(): Boolean = properties.getMinimumExponentDigits() != -1
    fun setScientificNotation(value: Boolean) { properties.setMinimumExponentDigits(if (value) 1 else -1); refreshFormatter() }
    fun getMinimumExponentDigits(): Byte = properties.getMinimumExponentDigits().toByte()
    fun setMinimumExponentDigits(value: Byte) { properties.setMinimumExponentDigits(value.toInt()); refreshFormatter() }
    fun getGroupingSize(): Int = if (properties.getGroupingSize() < 0) 0 else properties.getGroupingSize()
    fun setGroupingSize(value: Int) { properties.setGroupingSize(value); refreshFormatter() }
    fun getSecondaryGroupingSize(): Int = if (properties.getSecondaryGroupingSize() < 0) 0 else properties.getSecondaryGroupingSize()
    fun setSecondaryGroupingSize(value: Int) { properties.setSecondaryGroupingSize(value); refreshFormatter() }
    fun getMinimumGroupingDigits(): Int = if (properties.getMinimumGroupingDigits() > 0) properties.getMinimumGroupingDigits() else 1
    fun setMinimumGroupingDigits(value: Int) { properties.setMinimumGroupingDigits(value); refreshFormatter() }
    fun getCurrency(): Currency? = exportedProperties!!.getCurrency()
    fun setCurrency(value: Currency?) {
        properties.setCurrency(value)
        if (value != null) symbols.setCurrency(value)
        refreshFormatter()
    }
    fun getCurrencyUsage(): Currency.CurrencyUsage = properties.getCurrencyUsage() ?: Currency.CurrencyUsage.STANDARD
    fun setCurrencyUsage(value: Currency.CurrencyUsage?) { properties.setCurrencyUsage(value); refreshFormatter() }
    fun getCurrencyPluralInfo(): CurrencyPluralInfo? = properties.getCurrencyPluralInfo()
    fun setCurrencyPluralInfo(value: CurrencyPluralInfo?) { properties.setCurrencyPluralInfo(value); refreshFormatter() }
    fun getParseMaxDigits(): Int = 1000
    fun setParseMaxDigits(value: Int) = Unit
    fun isParseStrict(): Boolean = properties.getParseMode() == DecimalFormatProperties.ParseMode.STRICT
    fun setParseStrict(value: Boolean) {
        properties.setParseMode(if (value) DecimalFormatProperties.ParseMode.STRICT else DecimalFormatProperties.ParseMode.LENIENT)
        refreshFormatter()
    }
    fun setParseStrictMode(value: DecimalFormatProperties.ParseMode?) { properties.setParseMode(value); refreshFormatter() }
    fun isSignAlwaysShown(): Boolean = properties.getSignAlwaysShown()
    fun setSignAlwaysShown(value: Boolean) { properties.setSignAlwaysShown(value); refreshFormatter() }
    fun isExponentSignAlwaysShown(): Boolean = properties.getExponentSignAlwaysShown()
    fun setExponentSignAlwaysShown(value: Boolean) { properties.setExponentSignAlwaysShown(value); refreshFormatter() }
    fun isGroupingUsed(): Boolean = properties.getGroupingUsed()
    fun setGroupingUsed(value: Boolean) { properties.setGroupingUsed(value); refreshFormatter() }
    fun isDecimalSeparatorAlwaysShown(): Boolean = properties.getDecimalSeparatorAlwaysShown()
    fun setDecimalSeparatorAlwaysShown(value: Boolean) { properties.setDecimalSeparatorAlwaysShown(value); refreshFormatter() }
    fun isParseBigDecimal(): Boolean = properties.getParseToBigDecimal()
    fun setParseBigDecimal(value: Boolean) { properties.setParseToBigDecimal(value); refreshFormatter() }
    fun isParseIntegerOnly(): Boolean = properties.getParseIntegerOnly()
    fun setParseIntegerOnly(value: Boolean) { properties.setParseIntegerOnly(value); refreshFormatter() }
    fun isDecimalPatternMatchRequired(): Boolean = properties.getDecimalPatternMatchRequired()
    fun setDecimalPatternMatchRequired(value: Boolean) { properties.setDecimalPatternMatchRequired(value); refreshFormatter() }
    fun isParseNoExponent(): Boolean = properties.getParseNoExponent()
    fun setParseNoExponent(value: Boolean) { properties.setParseNoExponent(value); refreshFormatter() }
    fun isParseCaseSensitive(): Boolean = properties.getParseCaseSensitive()
    fun setParseCaseSensitive(value: Boolean) { properties.setParseCaseSensitive(value); refreshFormatter() }
    fun refreshFormatter() {
        if (exportedProperties == null) return
        var locale = if (actualLocaleProvider != null) actualLocaleProvider!!.invoke() else actualLocale
        if (locale == null) locale = symbols.getLocale(ULocale.ACTUAL_LOCALE)
        if (locale == null) locale = symbols.getULocale()
        assertDecimalFormatCore { locale != null }
        formatter = NumberFormatter.fromDecimalFormat(rawProperties, rawSymbols, exportedProperties).locale(locale)
        parser = null
        currencyParser = null
    }
    fun getParser(): NumberParserImpl {
        if (parser == null) parser = NumberParserImpl.createParserFromProperties(properties, symbols, false)
        return parser!!
    }
    fun getCurrencyParser(): NumberParserImpl {
        if (currencyParser == null) currencyParser = NumberParserImpl.createParserFromProperties(properties, symbols, true)
        return currencyParser!!
    }
    fun setPropertiesFromPattern(pattern: String?, ignoreRounding: Int) {
        PatternStringParser.parseToExistingProperties(pattern!!, properties, ignoreRounding)
    }
    fun setProperties(setter: (DecimalFormatProperties) -> Unit) {
        setter(properties)
        refreshFormatter()
    }
    fun toNumberFormatter(): LocalizedNumberFormatter = formatter!!
    fun getFixedDecimal(number: Double): PluralRules.IFixedDecimal? = formatter!!.format(number).getFixedDecimal()
    fun format(number: Double, result: StringBuilder?, position: FormatFieldPosition?): StringBuilder =
        formatQuantity(DecimalQuantity_DualStorageBCD(number), result, position)
    fun format(number: Long, result: StringBuilder?, position: FormatFieldPosition?): StringBuilder =
        formatQuantity(DecimalQuantity_DualStorageBCD(number), result, position)
    fun format(number: Number?, result: StringBuilder?, position: FormatFieldPosition?): StringBuilder =
        formatQuantity(DecimalQuantity_DualStorageBCD(number), result, position)
    private fun formatQuantity(quantity: DecimalQuantity, result: StringBuilder?, position: FormatFieldPosition?): StringBuilder {
        val string = FormattedStringBuilder()
        formatter!!.formatImpl(quantity, string)
        fieldPositionHelper(quantity, string, position, result!!.length)
        Utility.appendTo(string, result)
        return result
    }
    fun format(amount: CurrencyAmount?, result: StringBuilder?, position: FormatFieldPosition?): StringBuilder {
        val localSymbols = symbols.clone()
        localSymbols.setCurrency(amount!!.getCurrency())
        val quantity = DecimalQuantity_DualStorageBCD(amount.getNumber())
        val string = FormattedStringBuilder()
        formatter!!.symbols(localSymbols).unit(amount.getCurrency()).formatImpl(quantity, string)
        fieldPositionHelper(quantity, string, position, result!!.length)
        Utility.appendTo(string, result)
        return result
    }
    fun formatToCharacterIterator(input: Any?): FormatAttributedCharacterIterator {
        val number = formatNumberInput(input)
        return formatter!!.format(number).toCharacterIterator()
    }
    fun parse(text: String?, initialPosition: ParsePosition?): Number? {
        if (text == null) throw IllegalArgumentException("Text cannot be null")
        val position = initialPosition ?: ParsePosition(0)
        if (position.index < 0) throw IllegalArgumentException("Cannot start parsing at a negative offset")
        if (position.index >= text.length) return null
        val result = ParsedNumber()
        val startIndex = position.index
        val currentParser = getParser()
        currentParser.parse(text, startIndex, true, result)
        if (result.success()) {
            position.index = result.charEnd
            val number = result.getNumber(currentParser.getParseFlags())
            return if (number is BigDecimal) safeConvertBigDecimal(number) else number
        }
        position.errorIndex = startIndex + result.charEnd
        return null
    }
    fun parseCurrency(text: CharSequence?, initialPosition: ParsePosition?): CurrencyAmount? {
        if (text == null) throw IllegalArgumentException("Text cannot be null")
        val position = initialPosition ?: ParsePosition(0)
        if (position.index < 0) throw IllegalArgumentException("Cannot start parsing at a negative offset")
        if (position.index >= text.length) return null
        val result = ParsedNumber()
        val startIndex = position.index
        val currentParser = getCurrencyParser()
        currentParser.parse(text.toString(), startIndex, true, result)
        if (result.success()) {
            position.index = result.charEnd
            var number = result.getNumber(currentParser.getParseFlags())
            if (number is BigDecimal) number = safeConvertBigDecimal(number)
            return CurrencyAmount(number, Currency.getInstance(result.currencyCode))
        }
        position.errorIndex = startIndex + result.charEnd
        return null
    }
    fun toPattern(): String {
        val copy = DecimalFormatProperties().copyFrom(properties)
        val useCurrency = copy.getCurrency() != null || copy.getCurrencyPluralInfo() != null ||
            copy.getCurrencyUsage() != null || copy.getCurrencyAsDecimal() ||
            AffixUtils.hasCurrencySymbols(copy.getPositivePrefixPattern()) ||
            AffixUtils.hasCurrencySymbols(copy.getPositiveSuffixPattern()) ||
            AffixUtils.hasCurrencySymbols(copy.getNegativePrefixPattern()) ||
            AffixUtils.hasCurrencySymbols(copy.getNegativeSuffixPattern())
        if (useCurrency) {
            copy.setMinimumFractionDigits(exportedProperties!!.getMinimumFractionDigits())
            copy.setMaximumFractionDigits(exportedProperties!!.getMaximumFractionDigits())
            copy.setRoundingIncrement(exportedProperties!!.getRoundingIncrement())
        }
        return PatternStringUtils.propertiesToPatternString(copy)
    }
    fun toLocalizedPattern(): String = PatternStringUtils.convertLocalized(toPattern(), symbols, true)!!
    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (other === this) return true
        return other is DecimalFormatCore && properties == other.properties && symbols == other.symbols
    }
    override fun hashCode(): Int = properties.hashCode() xor symbols.hashCode()

    companion object {
        const val PAD_BEFORE_PREFIX = 0
        const val PAD_AFTER_PREFIX = 1
        const val PAD_BEFORE_SUFFIX = 2
        const val PAD_AFTER_SUFFIX = 3
        const val MINIMUM_GROUPING_DIGITS_AUTO = -2
        const val MINIMUM_GROUPING_DIGITS_MIN2 = -3
        internal fun empty(): DecimalFormatCore = DecimalFormatCore()
        fun createDefault(): DecimalFormatCore {
            val locale = ULocale.getDefault(ULocale.Category.FORMAT)
            val pattern = NumberFormatPatternData.getPatternForStyle(locale, NumberFormatPatternData.NUMBERSTYLE)
            return createOwned(pattern, DecimalFormatSymbols.getInstance(), PatternStringParser.IGNORE_ROUNDING_IF_CURRENCY)
        }
        fun create(pattern: String?): DecimalFormatCore =
            createOwned(pattern, DecimalFormatSymbols.getInstance(), PatternStringParser.IGNORE_ROUNDING_IF_CURRENCY)
        fun create(pattern: String?, symbols: DecimalFormatSymbols?): DecimalFormatCore =
            createOwned(pattern, symbols!!.clone(), PatternStringParser.IGNORE_ROUNDING_IF_CURRENCY)
        fun create(pattern: String?, symbols: DecimalFormatSymbols?, choice: Int): DecimalFormatCore {
            val ignore = when (choice) {
                NumberFormatPatternData.CURRENCYSTYLE, NumberFormatPatternData.ISOCURRENCYSTYLE,
                NumberFormatPatternData.ACCOUNTINGCURRENCYSTYLE, NumberFormatPatternData.CASHCURRENCYSTYLE,
                NumberFormatPatternData.STANDARDCURRENCYSTYLE, NumberFormatPatternData.PLURALCURRENCYSTYLE -> PatternStringParser.IGNORE_ROUNDING_ALWAYS
                else -> PatternStringParser.IGNORE_ROUNDING_IF_CURRENCY
            }
            return createOwned(pattern, symbols!!.clone(), ignore)
        }
        fun create(pattern: String?, symbols: DecimalFormatSymbols?, info: CurrencyPluralInfo?, style: Int): DecimalFormatCore =
            create(pattern, symbols, style).also { it.properties.setCurrencyPluralInfo(info); it.refreshFormatter() }
        private fun createOwned(pattern: String?, symbols: DecimalFormatSymbols, ignore: Int): DecimalFormatCore = DecimalFormatCore().also {
            it.symbols = symbols
            it.properties = DecimalFormatProperties()
            it.exportedProperties = DecimalFormatProperties()
            it.setPropertiesFromPattern(pattern, ignore)
            it.refreshFormatter()
        }
        internal fun safeConvertBigDecimal(number: BigDecimal): Number = try { IcuBigDecimal(number) } catch (e: NumberFormatException) {
            when {
                number.signum() > 0 && number.scale() < 0 -> Double.POSITIVE_INFINITY
                number.scale() < 0 -> Double.NEGATIVE_INFINITY
                number.signum() < 0 -> -0.0
                else -> 0.0
            }
        }
        internal fun fieldPositionHelper(quantity: DecimalQuantity, string: FormattedStringBuilder, position: FormatFieldPosition?, offset: Int) {
            position!!.setBeginIndex(0)
            position.setEndIndex(0)
            quantity.populateUFieldPosition(position)
            val found = FormattedValueStringBuilderImpl.nextFieldPosition(string, position)
            if (found && offset != 0) {
                position.setBeginIndex(position.getBeginIndex() + offset)
                position.setEndIndex(position.getEndIndex() + offset)
            }
        }
    }
}
internal expect fun assertDecimalFormatCore(condition: () -> Boolean)
internal expect fun formatNumberInput(value: Any?): Number

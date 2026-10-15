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
import kotlin.concurrent.Volatile

/** DecimalFormat's mutable configuration and published runtime, before its public inheritance facade.
 * Configuration access must be serialized by the owning facade's monitor; formatting and lazy parser
 * access follow the source's volatile publication and deliberately permit duplicate parser creation.
 */
internal class DecimalFormatCore private constructor() {
    lateinit var properties: DecimalFormatProperties
    lateinit var symbols: DecimalFormatSymbols
    @Volatile var exportedProperties: DecimalFormatProperties? = null
    @Volatile private var formatter: LocalizedNumberFormatter? = null
    @Volatile private var parser: NumberParserImpl? = null
    @Volatile private var currencyParser: NumberParserImpl? = null
    var actualLocale: ULocale? = null

    fun copy(): DecimalFormatCore = DecimalFormatCore().also {
        it.actualLocale = actualLocale
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
    fun refreshFormatter() {
        if (exportedProperties == null) return
        var locale = actualLocale
        if (locale == null) locale = symbols.getLocale(ULocale.ACTUAL_LOCALE)
        if (locale == null) locale = symbols.getULocale()
        assertDecimalFormatCore { locale != null }
        formatter = NumberFormatter.fromDecimalFormat(properties, symbols, exportedProperties).locale(locale)
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

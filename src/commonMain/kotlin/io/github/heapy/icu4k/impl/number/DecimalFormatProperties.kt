// © 2017 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.number

import io.github.heapy.icu4k.impl.number.Padder.PadPosition
import io.github.heapy.icu4k.text.CompactStyle
import io.github.heapy.icu4k.text.CurrencyPluralInfo
import io.github.heapy.icu4k.text.PluralRules
import io.github.heapy.icu4k.text.PluralObjectInputStream
import io.github.heapy.icu4k.text.PluralObjectOutputStream
import io.github.heapy.icu4k.util.BigDecimal
import io.github.heapy.icu4k.util.Currency
import io.github.heapy.icu4k.util.Currency.CurrencyUsage
import io.github.heapy.icu4k.util.ICUCloneableBase
import io.github.heapy.icu4k.util.MathContext
import io.github.heapy.icu4k.util.RoundingMode
import kotlin.jvm.Transient

/** Raw decimal-format settings. Setters retain references and perform no validation. */
open class DecimalFormatProperties : ICUCloneableBase() {
    enum class ParseMode { LENIENT, STRICT, JAVA_COMPATIBILITY }
    @Transient private var compactCustomData: Map<out String?, Map<out String?, String?>?>? = null
    @Transient private var compactStyle: CompactStyle? = null
    @Transient private var currency: Currency? = null
    @Transient private var currencyPluralInfo: CurrencyPluralInfo? = null
    @Transient private var currencyUsage: CurrencyUsage? = null
    @Transient private var decimalPatternMatchRequired: Boolean = false
    @Transient private var decimalSeparatorAlwaysShown: Boolean = false
    @Transient private var exponentSignAlwaysShown: Boolean = false
    @Transient private var currencyAsDecimal: Boolean = false
    @Transient private var formatWidth: Int = 0
    @Transient private var groupingSize: Int = 0
    @Transient private var groupingUsed: Boolean = false
    @Transient private var magnitudeMultiplier: Int = 0
    @Transient private var mathContext: MathContext? = null
    @Transient private var maximumFractionDigits: Int = 0
    @Transient private var maximumIntegerDigits: Int = 0
    @Transient private var maximumSignificantDigits: Int = 0
    @Transient private var minimumExponentDigits: Int = 0
    @Transient private var minimumFractionDigits: Int = 0
    @Transient private var minimumGroupingDigits: Int = 0
    @Transient private var minimumIntegerDigits: Int = 0
    @Transient private var minimumSignificantDigits: Int = 0
    @Transient private var multiplier: BigDecimal? = null
    @Transient private var negativePrefix: String? = null
    @Transient private var negativePrefixPattern: String? = null
    @Transient private var negativeSuffix: String? = null
    @Transient private var negativeSuffixPattern: String? = null
    @Transient private var padPosition: PadPosition? = null
    @Transient private var padString: String? = null
    @Transient private var parseCaseSensitive: Boolean = false
    @Transient private var parseIntegerOnly: Boolean = false
    @Transient private var parseMode: ParseMode? = null
    @Transient private var parseNoExponent: Boolean = false
    @Transient private var parseToBigDecimal: Boolean = false
    @Transient private var pluralRules: PluralRules? = null
    @Transient private var positivePrefix: String? = null
    @Transient private var positivePrefixPattern: String? = null
    @Transient private var positiveSuffix: String? = null
    @Transient private var positiveSuffixPattern: String? = null
    @Transient private var roundingIncrement: BigDecimal? = null
    @Transient private var roundingMode: RoundingMode? = null
    @Transient private var secondaryGroupingSize: Int = 0
    @Transient private var signAlwaysShown: Boolean = false
    init { clear() }
    open fun clear(): DecimalFormatProperties {
        compactCustomData = null
        compactStyle = null
        currency = null
        currencyPluralInfo = null
        currencyUsage = null
        decimalPatternMatchRequired = false
        decimalSeparatorAlwaysShown = false
        exponentSignAlwaysShown = false
        currencyAsDecimal = false
        formatWidth = -1
        groupingSize = -1
        groupingUsed = true
        magnitudeMultiplier = 0
        mathContext = null
        maximumFractionDigits = -1
        maximumIntegerDigits = -1
        maximumSignificantDigits = -1
        minimumExponentDigits = -1
        minimumFractionDigits = -1
        minimumGroupingDigits = -1
        minimumIntegerDigits = -1
        minimumSignificantDigits = -1
        multiplier = null
        negativePrefix = null
        negativePrefixPattern = null
        negativeSuffix = null
        negativeSuffixPattern = null
        padPosition = null
        padString = null
        parseCaseSensitive = false
        parseIntegerOnly = false
        parseMode = null
        parseNoExponent = false
        parseToBigDecimal = false
        pluralRules = null
        positivePrefix = null
        positivePrefixPattern = null
        positiveSuffix = null
        positiveSuffixPattern = null
        roundingIncrement = null
        roundingMode = null
        secondaryGroupingSize = -1
        signAlwaysShown = false
        return this
    }
    /** Native subclasses override clone to preserve any additional subclass state. */
    public override fun clone(): DecimalFormatProperties = (cloneHost() as DecimalFormatProperties?) ?: DecimalFormatProperties().copyFrom(this)
    open fun copyFrom(other: DecimalFormatProperties?): DecimalFormatProperties {
        compactCustomData = other!!.compactCustomData
        compactStyle = other.compactStyle
        currency = other.currency
        currencyPluralInfo = other.currencyPluralInfo
        currencyUsage = other.currencyUsage
        decimalPatternMatchRequired = other.decimalPatternMatchRequired
        decimalSeparatorAlwaysShown = other.decimalSeparatorAlwaysShown
        exponentSignAlwaysShown = other.exponentSignAlwaysShown
        currencyAsDecimal = other.currencyAsDecimal
        formatWidth = other.formatWidth
        groupingSize = other.groupingSize
        groupingUsed = other.groupingUsed
        magnitudeMultiplier = other.magnitudeMultiplier
        mathContext = other.mathContext
        maximumFractionDigits = other.maximumFractionDigits
        maximumIntegerDigits = other.maximumIntegerDigits
        maximumSignificantDigits = other.maximumSignificantDigits
        minimumExponentDigits = other.minimumExponentDigits
        minimumFractionDigits = other.minimumFractionDigits
        minimumGroupingDigits = other.minimumGroupingDigits
        minimumIntegerDigits = other.minimumIntegerDigits
        minimumSignificantDigits = other.minimumSignificantDigits
        multiplier = other.multiplier
        negativePrefix = other.negativePrefix
        negativePrefixPattern = other.negativePrefixPattern
        negativeSuffix = other.negativeSuffix
        negativeSuffixPattern = other.negativeSuffixPattern
        padPosition = other.padPosition
        padString = other.padString
        parseCaseSensitive = other.parseCaseSensitive
        parseIntegerOnly = other.parseIntegerOnly
        parseMode = other.parseMode
        parseNoExponent = other.parseNoExponent
        parseToBigDecimal = other.parseToBigDecimal
        pluralRules = other.pluralRules
        positivePrefix = other.positivePrefix
        positivePrefixPattern = other.positivePrefixPattern
        positiveSuffix = other.positiveSuffix
        positiveSuffixPattern = other.positiveSuffixPattern
        roundingIncrement = other.roundingIncrement
        roundingMode = other.roundingMode
        secondaryGroupingSize = other.secondaryGroupingSize
        signAlwaysShown = other.signAlwaysShown
        return this
    }
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DecimalFormatProperties) return false
        return equalValue(compactCustomData, other.compactCustomData) &&
            equalValue(compactStyle, other.compactStyle) &&
            equalValue(currency, other.currency) &&
            equalValue(currencyPluralInfo, other.currencyPluralInfo) &&
            equalValue(currencyUsage, other.currencyUsage) &&
            decimalPatternMatchRequired == other.decimalPatternMatchRequired &&
            decimalSeparatorAlwaysShown == other.decimalSeparatorAlwaysShown &&
            exponentSignAlwaysShown == other.exponentSignAlwaysShown &&
            currencyAsDecimal == other.currencyAsDecimal &&
            formatWidth == other.formatWidth &&
            groupingSize == other.groupingSize &&
            groupingUsed == other.groupingUsed &&
            magnitudeMultiplier == other.magnitudeMultiplier &&
            equalValue(mathContext, other.mathContext) &&
            maximumFractionDigits == other.maximumFractionDigits &&
            maximumIntegerDigits == other.maximumIntegerDigits &&
            maximumSignificantDigits == other.maximumSignificantDigits &&
            minimumExponentDigits == other.minimumExponentDigits &&
            minimumFractionDigits == other.minimumFractionDigits &&
            minimumGroupingDigits == other.minimumGroupingDigits &&
            minimumIntegerDigits == other.minimumIntegerDigits &&
            minimumSignificantDigits == other.minimumSignificantDigits &&
            equalValue(multiplier, other.multiplier) &&
            equalValue(negativePrefix, other.negativePrefix) &&
            equalValue(negativePrefixPattern, other.negativePrefixPattern) &&
            equalValue(negativeSuffix, other.negativeSuffix) &&
            equalValue(negativeSuffixPattern, other.negativeSuffixPattern) &&
            equalValue(padPosition, other.padPosition) &&
            equalValue(padString, other.padString) &&
            parseCaseSensitive == other.parseCaseSensitive &&
            parseIntegerOnly == other.parseIntegerOnly &&
            equalValue(parseMode, other.parseMode) &&
            parseNoExponent == other.parseNoExponent &&
            parseToBigDecimal == other.parseToBigDecimal &&
            equalValue(pluralRules, other.pluralRules) &&
            equalValue(positivePrefix, other.positivePrefix) &&
            equalValue(positivePrefixPattern, other.positivePrefixPattern) &&
            equalValue(positiveSuffix, other.positiveSuffix) &&
            equalValue(positiveSuffixPattern, other.positiveSuffixPattern) &&
            equalValue(roundingIncrement, other.roundingIncrement) &&
            equalValue(roundingMode, other.roundingMode) &&
            secondaryGroupingSize == other.secondaryGroupingSize &&
            signAlwaysShown == other.signAlwaysShown
    }
    private fun equalValue(mine: Any?, theirs: Any?): Boolean = mine === theirs || mine != null && mine.equals(theirs)
    override fun hashCode(): Int {
        var hash = 0
        hash = hash xor (compactCustomData?.hashCode() ?: 0)
        hash = hash xor (compactStyle?.hashCode() ?: 0)
        hash = hash xor (currency?.hashCode() ?: 0)
        hash = hash xor (currencyPluralInfo?.hashCode() ?: 0)
        hash = hash xor (currencyUsage?.hashCode() ?: 0)
        hash = hash xor (if (decimalPatternMatchRequired) 1 else 0)
        hash = hash xor (if (decimalSeparatorAlwaysShown) 1 else 0)
        hash = hash xor (if (exponentSignAlwaysShown) 1 else 0)
        hash = hash xor (if (currencyAsDecimal) 1 else 0)
        hash = hash xor (formatWidth * 13)
        hash = hash xor (groupingSize * 13)
        hash = hash xor (if (groupingUsed) 1 else 0)
        hash = hash xor (magnitudeMultiplier * 13)
        hash = hash xor (mathContext?.hashCode() ?: 0)
        hash = hash xor (maximumFractionDigits * 13)
        hash = hash xor (maximumIntegerDigits * 13)
        hash = hash xor (maximumSignificantDigits * 13)
        hash = hash xor (minimumExponentDigits * 13)
        hash = hash xor (minimumFractionDigits * 13)
        hash = hash xor (minimumGroupingDigits * 13)
        hash = hash xor (minimumIntegerDigits * 13)
        hash = hash xor (minimumSignificantDigits * 13)
        hash = hash xor (multiplier?.hashCode() ?: 0)
        hash = hash xor (negativePrefix?.hashCode() ?: 0)
        hash = hash xor (negativePrefixPattern?.hashCode() ?: 0)
        hash = hash xor (negativeSuffix?.hashCode() ?: 0)
        hash = hash xor (negativeSuffixPattern?.hashCode() ?: 0)
        hash = hash xor (padPosition?.hashCode() ?: 0)
        hash = hash xor (padString?.hashCode() ?: 0)
        hash = hash xor (if (parseCaseSensitive) 1 else 0)
        hash = hash xor (if (parseIntegerOnly) 1 else 0)
        hash = hash xor (parseMode?.hashCode() ?: 0)
        hash = hash xor (if (parseNoExponent) 1 else 0)
        hash = hash xor (if (parseToBigDecimal) 1 else 0)
        hash = hash xor (pluralRules?.hashCode() ?: 0)
        hash = hash xor (positivePrefix?.hashCode() ?: 0)
        hash = hash xor (positivePrefixPattern?.hashCode() ?: 0)
        hash = hash xor (positiveSuffix?.hashCode() ?: 0)
        hash = hash xor (positiveSuffixPattern?.hashCode() ?: 0)
        hash = hash xor (roundingIncrement?.hashCode() ?: 0)
        hash = hash xor (roundingMode?.hashCode() ?: 0)
        hash = hash xor (secondaryGroupingSize * 13)
        hash = hash xor (if (signAlwaysShown) 1 else 0)
        return hash
    }
    open fun getCompactCustomData(): Map<out String?, Map<out String?, String?>?>? = compactCustomData
    open fun setCompactCustomData(value: Map<out String?, Map<out String?, String?>?>?): DecimalFormatProperties { compactCustomData = value; return this }
    open fun getCompactStyle(): CompactStyle? = compactStyle
    open fun setCompactStyle(value: CompactStyle?): DecimalFormatProperties { compactStyle = value; return this }
    open fun getCurrency(): Currency? = currency
    open fun setCurrency(value: Currency?): DecimalFormatProperties { currency = value; return this }
    open fun getCurrencyPluralInfo(): CurrencyPluralInfo? = currencyPluralInfo
    open fun setCurrencyPluralInfo(value: CurrencyPluralInfo?): DecimalFormatProperties { currencyPluralInfo = value; return this }
    open fun getCurrencyUsage(): CurrencyUsage? = currencyUsage
    open fun setCurrencyUsage(value: CurrencyUsage?): DecimalFormatProperties { currencyUsage = value; return this }
    open fun getDecimalPatternMatchRequired(): Boolean = decimalPatternMatchRequired
    open fun setDecimalPatternMatchRequired(value: Boolean): DecimalFormatProperties { decimalPatternMatchRequired = value; return this }
    open fun getDecimalSeparatorAlwaysShown(): Boolean = decimalSeparatorAlwaysShown
    open fun setDecimalSeparatorAlwaysShown(value: Boolean): DecimalFormatProperties { decimalSeparatorAlwaysShown = value; return this }
    open fun getExponentSignAlwaysShown(): Boolean = exponentSignAlwaysShown
    open fun setExponentSignAlwaysShown(value: Boolean): DecimalFormatProperties { exponentSignAlwaysShown = value; return this }
    open fun getCurrencyAsDecimal(): Boolean = currencyAsDecimal
    open fun setCurrencyAsDecimal(value: Boolean): DecimalFormatProperties { currencyAsDecimal = value; return this }
    open fun getFormatWidth(): Int = formatWidth
    open fun setFormatWidth(value: Int): DecimalFormatProperties { formatWidth = value; return this }
    open fun getGroupingSize(): Int = groupingSize
    open fun setGroupingSize(value: Int): DecimalFormatProperties { groupingSize = value; return this }
    open fun getGroupingUsed(): Boolean = groupingUsed
    open fun setGroupingUsed(value: Boolean): DecimalFormatProperties { groupingUsed = value; return this }
    open fun getMagnitudeMultiplier(): Int = magnitudeMultiplier
    open fun setMagnitudeMultiplier(value: Int): DecimalFormatProperties { magnitudeMultiplier = value; return this }
    open fun getMathContext(): MathContext? = mathContext
    open fun setMathContext(value: MathContext?): DecimalFormatProperties { mathContext = value; return this }
    open fun getMaximumFractionDigits(): Int = maximumFractionDigits
    open fun setMaximumFractionDigits(value: Int): DecimalFormatProperties { maximumFractionDigits = value; return this }
    open fun getMaximumIntegerDigits(): Int = maximumIntegerDigits
    open fun setMaximumIntegerDigits(value: Int): DecimalFormatProperties { maximumIntegerDigits = value; return this }
    open fun getMaximumSignificantDigits(): Int = maximumSignificantDigits
    open fun setMaximumSignificantDigits(value: Int): DecimalFormatProperties { maximumSignificantDigits = value; return this }
    open fun getMinimumExponentDigits(): Int = minimumExponentDigits
    open fun setMinimumExponentDigits(value: Int): DecimalFormatProperties { minimumExponentDigits = value; return this }
    open fun getMinimumFractionDigits(): Int = minimumFractionDigits
    open fun setMinimumFractionDigits(value: Int): DecimalFormatProperties { minimumFractionDigits = value; return this }
    open fun getMinimumGroupingDigits(): Int = minimumGroupingDigits
    open fun setMinimumGroupingDigits(value: Int): DecimalFormatProperties { minimumGroupingDigits = value; return this }
    open fun getMinimumIntegerDigits(): Int = minimumIntegerDigits
    open fun setMinimumIntegerDigits(value: Int): DecimalFormatProperties { minimumIntegerDigits = value; return this }
    open fun getMinimumSignificantDigits(): Int = minimumSignificantDigits
    open fun setMinimumSignificantDigits(value: Int): DecimalFormatProperties { minimumSignificantDigits = value; return this }
    open fun getMultiplier(): BigDecimal? = multiplier
    open fun setMultiplier(value: BigDecimal?): DecimalFormatProperties { multiplier = value; return this }
    open fun getNegativePrefix(): String? = negativePrefix
    open fun setNegativePrefix(value: String?): DecimalFormatProperties { negativePrefix = value; return this }
    open fun getNegativePrefixPattern(): String? = negativePrefixPattern
    open fun setNegativePrefixPattern(value: String?): DecimalFormatProperties { negativePrefixPattern = value; return this }
    open fun getNegativeSuffix(): String? = negativeSuffix
    open fun setNegativeSuffix(value: String?): DecimalFormatProperties { negativeSuffix = value; return this }
    open fun getNegativeSuffixPattern(): String? = negativeSuffixPattern
    open fun setNegativeSuffixPattern(value: String?): DecimalFormatProperties { negativeSuffixPattern = value; return this }
    open fun getPadPosition(): PadPosition? = padPosition
    open fun setPadPosition(value: PadPosition?): DecimalFormatProperties { padPosition = value; return this }
    open fun getPadString(): String? = padString
    open fun setPadString(value: String?): DecimalFormatProperties { padString = value; return this }
    open fun getParseCaseSensitive(): Boolean = parseCaseSensitive
    open fun setParseCaseSensitive(value: Boolean): DecimalFormatProperties { parseCaseSensitive = value; return this }
    open fun getParseIntegerOnly(): Boolean = parseIntegerOnly
    open fun setParseIntegerOnly(value: Boolean): DecimalFormatProperties { parseIntegerOnly = value; return this }
    open fun getParseMode(): ParseMode? = parseMode
    open fun setParseMode(value: ParseMode?): DecimalFormatProperties { parseMode = value; return this }
    open fun getParseNoExponent(): Boolean = parseNoExponent
    open fun setParseNoExponent(value: Boolean): DecimalFormatProperties { parseNoExponent = value; return this }
    open fun getParseToBigDecimal(): Boolean = parseToBigDecimal
    open fun setParseToBigDecimal(value: Boolean): DecimalFormatProperties { parseToBigDecimal = value; return this }
    open fun getPluralRules(): PluralRules? = pluralRules
    open fun setPluralRules(value: PluralRules?): DecimalFormatProperties { pluralRules = value; return this }
    open fun getPositivePrefix(): String? = positivePrefix
    open fun setPositivePrefix(value: String?): DecimalFormatProperties { positivePrefix = value; return this }
    open fun getPositivePrefixPattern(): String? = positivePrefixPattern
    open fun setPositivePrefixPattern(value: String?): DecimalFormatProperties { positivePrefixPattern = value; return this }
    open fun getPositiveSuffix(): String? = positiveSuffix
    open fun setPositiveSuffix(value: String?): DecimalFormatProperties { positiveSuffix = value; return this }
    open fun getPositiveSuffixPattern(): String? = positiveSuffixPattern
    open fun setPositiveSuffixPattern(value: String?): DecimalFormatProperties { positiveSuffixPattern = value; return this }
    open fun getRoundingIncrement(): BigDecimal? = roundingIncrement
    open fun setRoundingIncrement(value: BigDecimal?): DecimalFormatProperties { roundingIncrement = value; return this }
    open fun getRoundingMode(): RoundingMode? = roundingMode
    open fun setRoundingMode(value: RoundingMode?): DecimalFormatProperties { roundingMode = value; return this }
    open fun getSecondaryGroupingSize(): Int = secondaryGroupingSize
    open fun setSecondaryGroupingSize(value: Int): DecimalFormatProperties { secondaryGroupingSize = value; return this }
    open fun getSignAlwaysShown(): Boolean = signAlwaysShown
    open fun setSignAlwaysShown(value: Boolean): DecimalFormatProperties { signAlwaysShown = value; return this }
    override fun toString(): String = buildString { append("<Properties"); toStringBare(this); append('>') }
    open fun toStringBare(result: StringBuilder?) {
        val current = entries()
        val defaults = DEFAULT.entries()
        for (i in current.indices) {
            val (name, value) = current[i]
            val default = defaults[i].second
            if (value == null && default == null) continue
            if (value == null || default == null || !value.equals(default)) {
                val rendered = " $name:$value"
                result!!.append(rendered)
            }
        }
    }
    internal fun entries(): List<Pair<String, Any?>> = listOf(
        "compactCustomData" to compactCustomData,
        "compactStyle" to compactStyle,
        "currency" to currency,
        "currencyPluralInfo" to currencyPluralInfo,
        "currencyUsage" to currencyUsage,
        "decimalPatternMatchRequired" to decimalPatternMatchRequired,
        "decimalSeparatorAlwaysShown" to decimalSeparatorAlwaysShown,
        "exponentSignAlwaysShown" to exponentSignAlwaysShown,
        "currencyAsDecimal" to currencyAsDecimal,
        "formatWidth" to formatWidth,
        "groupingSize" to groupingSize,
        "groupingUsed" to groupingUsed,
        "magnitudeMultiplier" to magnitudeMultiplier,
        "mathContext" to mathContext,
        "maximumFractionDigits" to maximumFractionDigits,
        "maximumIntegerDigits" to maximumIntegerDigits,
        "maximumSignificantDigits" to maximumSignificantDigits,
        "minimumExponentDigits" to minimumExponentDigits,
        "minimumFractionDigits" to minimumFractionDigits,
        "minimumGroupingDigits" to minimumGroupingDigits,
        "minimumIntegerDigits" to minimumIntegerDigits,
        "minimumSignificantDigits" to minimumSignificantDigits,
        "multiplier" to multiplier,
        "negativePrefix" to negativePrefix,
        "negativePrefixPattern" to negativePrefixPattern,
        "negativeSuffix" to negativeSuffix,
        "negativeSuffixPattern" to negativeSuffixPattern,
        "padPosition" to padPosition,
        "padString" to padString,
        "parseCaseSensitive" to parseCaseSensitive,
        "parseIntegerOnly" to parseIntegerOnly,
        "parseMode" to parseMode,
        "parseNoExponent" to parseNoExponent,
        "parseToBigDecimal" to parseToBigDecimal,
        "pluralRules" to pluralRules,
        "positivePrefix" to positivePrefix,
        "positivePrefixPattern" to positivePrefixPattern,
        "positiveSuffix" to positiveSuffix,
        "positiveSuffixPattern" to positiveSuffixPattern,
        "roundingIncrement" to roundingIncrement,
        "roundingMode" to roundingMode,
        "secondaryGroupingSize" to secondaryGroupingSize,
        "signAlwaysShown" to signAlwaysShown
    )
    private fun writeObject(stream: PluralObjectOutputStream) { writeObjectImpl(stream) }
    private fun readObject(stream: PluralObjectInputStream) { readObjectImpl(stream) }
    internal fun writeObjectImpl(stream: PluralObjectOutputStream) = writeDecimalProperties(stream, this)
    internal fun readObjectImpl(stream: PluralObjectInputStream) = readDecimalProperties(stream, this)
    companion object {
        private const val serialVersionUID: Long = 4095518955889349243L
        private val DEFAULT = DecimalFormatProperties()
        internal fun defaultsForStream(): DecimalFormatProperties = DEFAULT
    }
}
internal expect fun writeDecimalProperties(stream: PluralObjectOutputStream, properties: DecimalFormatProperties)
internal expect fun readDecimalProperties(stream: PluralObjectInputStream, properties: DecimalFormatProperties)

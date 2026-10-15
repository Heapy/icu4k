// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.number.DecimalFormatCore
import io.github.heapy.icu4k.impl.number.DecimalFormatProperties
import io.github.heapy.icu4k.impl.number.PatternStringUtils
import io.github.heapy.icu4k.number.LocalizedNumberFormatter
import io.github.heapy.icu4k.util.BigDecimal
import io.github.heapy.icu4k.util.BigDecimals
import io.github.heapy.icu4k.util.BigInteger
import io.github.heapy.icu4k.util.Currency
import io.github.heapy.icu4k.util.CurrencyAmount
import io.github.heapy.icu4k.util.MathContext
import io.github.heapy.icu4k.util.RoundingMode
import io.github.heapy.icu4k.util.RoundingModes
import io.github.heapy.icu4k.util.ULocale
import io.github.heapy.icu4k.math.BigDecimal as IcuBigDecimal
import io.github.heapy.icu4k.math.MathContext as IcuMathContext
import kotlin.jvm.Transient

/** Mutable ICU decimal formatter, with immutable published formatting snapshots. */
open class DecimalFormat private constructor(@Transient internal var runtime: DecimalFormatCore?) : NumberFormat() {
    private val serialVersionOnStream = 5
    @Transient private var icuMathContextForm = IcuMathContext.PLAIN
    constructor() : this(DecimalFormatCore.createDefault())
    constructor(pattern: String?) : this(DecimalFormatCore.create(pattern))
    constructor(pattern: String?, symbols: DecimalFormatSymbols?) : this(DecimalFormatCore.create(pattern, symbols))
    constructor(pattern: String?, symbols: DecimalFormatSymbols?, info: CurrencyPluralInfo?, style: Int) :
        this(DecimalFormatCore.create(pattern, symbols, info, style))
    internal constructor(pattern: String?, symbols: DecimalFormatSymbols?, style: Int) : this(DecimalFormatCore.create(pattern, symbols, style))
    private val core: DecimalFormatCore get() = runtime!!
    private fun <T> configured(block: (DecimalFormatCore) -> T): T = withNumberFormatMonitor {
        if (core.actualLocaleProvider == null) core.actualLocaleProvider = { getLocale(ULocale.ACTUAL_LOCALE) }
        block(core)
    }
    open fun applyPattern(pattern: String?) = configured { it.applyPattern(pattern) }
    open fun applyLocalizedPattern(pattern: String?) = withNumberFormatMonitor {
        applyPattern(PatternStringUtils.convertLocalized(pattern, core.symbols, false))
    }
    override fun format(number: Long, output: StringBuilder?, position: FormatFieldPosition?): StringBuilder = core.format(number, output, position)
    override fun format(number: Double, output: StringBuilder?, position: FormatFieldPosition?): StringBuilder = core.format(number, output, position)
    override fun format(number: BigInteger?, output: StringBuilder?, position: FormatFieldPosition?): StringBuilder = core.format(number, output, position)
    override fun format(number: BigDecimal?, output: StringBuilder?, position: FormatFieldPosition?): StringBuilder = core.format(number, output, position)
    override fun format(number: IcuBigDecimal?, output: StringBuilder?, position: FormatFieldPosition?): StringBuilder = core.format(number, output, position)
    override fun format(amount: CurrencyAmount?, output: StringBuilder?, position: FormatFieldPosition?): StringBuilder = core.format(amount, output, position)
    override fun formatToCharacterIterator(input: Any?): FormatAttributedCharacterIterator = core.formatToCharacterIterator(input)
    override fun parse(text: String?, position: ParsePosition?): Number? = core.parse(text, position)
    override fun parseCurrency(text: CharSequence?, position: ParsePosition?): CurrencyAmount? = core.parseCurrency(text, position)
    open fun setDecimalFormatSymbols(value: DecimalFormatSymbols?): Unit = configured { it.setDecimalFormatSymbols(value) }
    open fun getDecimalFormatSymbols(): DecimalFormatSymbols = configured { it.getDecimalFormatSymbols() }
    open fun getPositivePrefix(): String = configured { it.getPositivePrefix() }
    open fun setPositivePrefix(value: String?): Unit = configured { it.setPositivePrefix(value) }
    open fun getNegativePrefix(): String = configured { it.getNegativePrefix() }
    open fun setNegativePrefix(value: String?): Unit = configured { it.setNegativePrefix(value) }
    open fun getPositiveSuffix(): String = configured { it.getPositiveSuffix() }
    open fun setPositiveSuffix(value: String?): Unit = configured { it.setPositiveSuffix(value) }
    open fun getNegativeSuffix(): String = configured { it.getNegativeSuffix() }
    open fun setNegativeSuffix(value: String?): Unit = configured { it.setNegativeSuffix(value) }
    open fun getMultiplier(): Int = configured { it.getMultiplier() }
    open fun setMultiplier(multiplier: Int): Unit = configured { it.setMultiplier(multiplier) }
    open fun getRoundingIncrement(): BigDecimal? = configured { it.getRoundingIncrement() }
    open fun setRoundingIncrement(increment: BigDecimal?): Unit = configured { it.setRoundingIncrement(increment) }
    override fun getRoundingMode(): Int = configured { it.getRoundingMode() }
    override fun setRoundingMode(value: Int): Unit = configured { it.setRoundingMode(value) }
    open fun getMathContext(): MathContext? = configured { it.getMathContext() }
    open fun setMathContext(value: MathContext?): Unit = configured { it.setMathContext(value) }
    override fun getMinimumIntegerDigits(): Int = configured { it.getMinimumIntegerDigits() }
    override fun setMinimumIntegerDigits(value: Int): Unit = configured { it.setMinimumIntegerDigits(value) }
    override fun getMaximumIntegerDigits(): Int = configured { it.getMaximumIntegerDigits() }
    override fun setMaximumIntegerDigits(value: Int): Unit = configured { it.setMaximumIntegerDigits(value) }
    override fun getMinimumFractionDigits(): Int = configured { it.getMinimumFractionDigits() }
    override fun setMinimumFractionDigits(value: Int): Unit = configured { it.setMinimumFractionDigits(value) }
    override fun getMaximumFractionDigits(): Int = configured { it.getMaximumFractionDigits() }
    override fun setMaximumFractionDigits(value: Int): Unit = configured { it.setMaximumFractionDigits(value) }
    open fun getMinimumSignificantDigits(): Int = configured { it.getMinimumSignificantDigits() }
    open fun setMinimumSignificantDigits(value: Int): Unit = configured { it.setMinimumSignificantDigits(value) }
    open fun getMaximumSignificantDigits(): Int = configured { it.getMaximumSignificantDigits() }
    open fun setMaximumSignificantDigits(value: Int): Unit = configured { it.setMaximumSignificantDigits(value) }
    open fun areSignificantDigitsUsed(): Boolean = configured { it.areSignificantDigitsUsed() }
    open fun setSignificantDigitsUsed(value: Boolean): Unit = configured { it.setSignificantDigitsUsed(value) }
    open fun getFormatWidth(): Int = configured { it.getFormatWidth() }
    open fun setFormatWidth(value: Int): Unit = configured { it.setFormatWidth(value) }
    open fun getPadCharacter(): Char = configured { it.getPadCharacter() }
    open fun setPadCharacter(value: Char): Unit = configured { it.setPadCharacter(value) }
    open fun getPadPosition(): Int = configured { it.getPadPosition() }
    open fun setPadPosition(value: Int): Unit = configured { it.setPadPosition(value) }
    open fun isScientificNotation(): Boolean = configured { it.isScientificNotation() }
    open fun setScientificNotation(value: Boolean): Unit = configured { it.setScientificNotation(value) }
    open fun getMinimumExponentDigits(): Byte = configured { it.getMinimumExponentDigits() }
    open fun setMinimumExponentDigits(value: Byte): Unit = configured { it.setMinimumExponentDigits(value) }
    open fun getGroupingSize(): Int = configured { it.getGroupingSize() }
    open fun setGroupingSize(value: Int): Unit = configured { it.setGroupingSize(value) }
    open fun getSecondaryGroupingSize(): Int = configured { it.getSecondaryGroupingSize() }
    open fun setSecondaryGroupingSize(value: Int): Unit = configured { it.setSecondaryGroupingSize(value) }
    open fun getMinimumGroupingDigits(): Int = configured { it.getMinimumGroupingDigits() }
    open fun setMinimumGroupingDigits(value: Int): Unit = configured { it.setMinimumGroupingDigits(value) }
    override fun getCurrency(): Currency? = configured { it.getCurrency() }
    override fun setCurrency(value: Currency?): Unit = configured { it.setCurrency(value) }
    open fun getCurrencyUsage(): Currency.CurrencyUsage = configured { it.getCurrencyUsage() }
    open fun setCurrencyUsage(value: Currency.CurrencyUsage?): Unit = configured { it.setCurrencyUsage(value) }
    open fun getCurrencyPluralInfo(): CurrencyPluralInfo? = configured { it.getCurrencyPluralInfo() }
    open fun setCurrencyPluralInfo(value: CurrencyPluralInfo?): Unit = configured { it.setCurrencyPluralInfo(value) }
    open fun getParseMaxDigits(): Int = 1000
    open fun setParseMaxDigits(value: Int): Unit = Unit
    override fun isParseStrict(): Boolean = configured { it.isParseStrict() }
    override fun setParseStrict(value: Boolean): Unit = configured { it.setParseStrict(value) }
    open fun setParseStrictMode(value: DecimalFormatProperties.ParseMode?): Unit = configured { it.setParseStrictMode(value) }
    open fun isSignAlwaysShown(): Boolean = configured { it.isSignAlwaysShown() }
    open fun setSignAlwaysShown(value: Boolean): Unit = configured { it.setSignAlwaysShown(value) }
    open fun isExponentSignAlwaysShown(): Boolean = configured { it.isExponentSignAlwaysShown() }
    open fun setExponentSignAlwaysShown(value: Boolean): Unit = configured { it.setExponentSignAlwaysShown(value) }
    override fun isGroupingUsed(): Boolean = configured { it.isGroupingUsed() }
    override fun setGroupingUsed(value: Boolean): Unit = configured { it.setGroupingUsed(value) }
    open fun isDecimalSeparatorAlwaysShown(): Boolean = configured { it.isDecimalSeparatorAlwaysShown() }
    open fun setDecimalSeparatorAlwaysShown(value: Boolean): Unit = configured { it.setDecimalSeparatorAlwaysShown(value) }
    open fun isParseBigDecimal(): Boolean = configured { it.isParseBigDecimal() }
    open fun setParseBigDecimal(value: Boolean): Unit = configured { it.setParseBigDecimal(value) }
    override fun isParseIntegerOnly(): Boolean = configured { it.isParseIntegerOnly() }
    override fun setParseIntegerOnly(value: Boolean): Unit = configured { it.setParseIntegerOnly(value) }
    open fun isDecimalPatternMatchRequired(): Boolean = configured { it.isDecimalPatternMatchRequired() }
    open fun setDecimalPatternMatchRequired(value: Boolean): Unit = configured { it.setDecimalPatternMatchRequired(value) }
    open fun isParseNoExponent(): Boolean = configured { it.isParseNoExponent() }
    open fun setParseNoExponent(value: Boolean): Unit = configured { it.setParseNoExponent(value) }
    open fun isParseCaseSensitive(): Boolean = configured { it.isParseCaseSensitive() }
    open fun setParseCaseSensitive(value: Boolean): Unit = configured { it.setParseCaseSensitive(value) }
    open fun setRoundingIncrement(increment: IcuBigDecimal?): Unit = withNumberFormatMonitor { setRoundingIncrement(increment?.toBigDecimal()) }
    open fun setRoundingIncrement(increment: Double): Unit = withNumberFormatMonitor {
        if (increment == 0.0) setRoundingIncrement(null as BigDecimal?)
        else setRoundingIncrement(BigDecimals.valueOf(increment).stripTrailingZeros())
    }
    open fun getMathContextICU(): IcuMathContext = withNumberFormatMonitor {
        val context = getMathContext()!!
        IcuMathContext(context.getPrecision(), icuMathContextForm, false, context.getRoundingMode().ordinal)
    }
    open fun setMathContextICU(value: IcuMathContext?): Unit = withNumberFormatMonitor {
        icuMathContextForm = value!!.getForm()
        val context = if (value.getLostDigits()) MathContext(value.getDigits(), RoundingMode.UNNECESSARY)
            else MathContext(value.getDigits(), RoundingModes.valueOf(value.getRoundingMode()))
        setMathContext(context)
    }
    open fun toPattern(): String = configured { it.toPattern() }
    open fun toLocalizedPattern(): String = withNumberFormatMonitor { PatternStringUtils.convertLocalized(toPattern(), core.symbols, true)!! }
    open fun toNumberFormatter(): LocalizedNumberFormatter = core.toNumberFormatter()
    open fun getFixedDecimal(number: Double): PluralRules.IFixedDecimal? = core.getFixedDecimal(number)
    fun interface PropertySetter { fun set(properties: DecimalFormatProperties) }
    open fun setProperties(setter: PropertySetter?): Unit = configured { value -> value.setProperties { setter!!.set(it) } }
    public override fun clone(): DecimalFormat {
        val other = (cloneHost() as DecimalFormat?) ?: DecimalFormat(null as DecimalFormatCore?).also { copyNumberFormatTo(it); it.icuMathContextForm = icuMathContextForm }
        other.runtime = core.copy(other.getLocale(ULocale.ACTUAL_LOCALE))
        return other
    }
    override fun equals(other: Any?): Boolean = withNumberFormatMonitor {
        if (other == null) false else if (other === this) true else other is DecimalFormat && core == other.core
    }
    override fun hashCode(): Int = withNumberFormatMonitor { core.hashCode() }
    override fun toString(): String {
        val result = StringBuilder()
        result.append(decimalFormatClassName(this)).append('@').append(hashCode().toUInt().toString(16))
        result.append(" { symbols@").append(core.symbols.hashCode().toUInt().toString(16))
        withNumberFormatMonitor { core.properties.toStringBare(result) }
        result.append(" }")
        return result.toString()
    }
    private fun writeObject(stream: PluralObjectOutputStream) = withNumberFormatMonitor { decimalFormatWrite(stream, this) }
    private fun readObject(stream: PluralObjectInputStream) { decimalFormatRead(stream, this) }
    companion object {
        const val PAD_BEFORE_PREFIX = 0
        const val PAD_AFTER_PREFIX = 1
        const val PAD_BEFORE_SUFFIX = 2
        const val PAD_AFTER_SUFFIX = 3
        const val MINIMUM_GROUPING_DIGITS_AUTO = -2
        const val MINIMUM_GROUPING_DIGITS_MIN2 = -3
        private const val serialVersionUID: Long = 864413376551465018L
    }
}
internal expect fun decimalFormatClassName(value: DecimalFormat): String
internal expect fun decimalFormatWrite(stream: PluralObjectOutputStream, owner: DecimalFormat)
internal expect fun decimalFormatRead(stream: PluralObjectInputStream, owner: DecimalFormat)

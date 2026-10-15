// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.ResourceCacheLock
import io.github.heapy.icu4k.impl.createAvailabilityLock
import io.github.heapy.icu4k.impl.number.formatNumberInput
import io.github.heapy.icu4k.impl.number.NumberFormatPatternData
import kotlin.jvm.JvmStatic
import io.github.heapy.icu4k.util.BigInteger
import io.github.heapy.icu4k.util.BigDecimal
import io.github.heapy.icu4k.util.Currency
import io.github.heapy.icu4k.util.CurrencyAmount
import io.github.heapy.icu4k.util.ParseException
import io.github.heapy.icu4k.util.ULocale
import io.github.heapy.icu4k.math.BigDecimal as IcuBigDecimal
import kotlin.jvm.Transient

/** Number-format instance and factory extension protocols. Localized factory entry points are still being ported. */
abstract class NumberFormat : UFormat() {
    typealias Field = NumberFormatField
    typealias NumberFormatFactory = io.github.heapy.icu4k.text.NumberFormatFactory
    typealias SimpleNumberFormatFactory = io.github.heapy.icu4k.text.SimpleNumberFormatFactory
    private var groupingUsed = true
    private var maxIntegerDigits: Byte = 40
    private var minIntegerDigits: Byte = 1
    private var maxFractionDigits: Byte = 3
    private var minFractionDigits: Byte = 0
    private var parseIntegerOnly = false
    private var maximumIntegerDigits = 40
    private var minimumIntegerDigits = 1
    private var maximumFractionDigits = 3
    private var minimumFractionDigits = 0
    private var currency: Currency? = null
    private var serialVersionOnStream = 2
    private var parseStrict = false
    private var capitalizationSetting: DisplayContext? = DisplayContext.CAPITALIZATION_NONE
    @Transient private val currencyMonitor = createAvailabilityLock()

    override fun format(input: Any?, output: StringBuilder?, position: FormatFieldPosition?): StringBuilder = when (input) {
        is Long -> format(input, output, position)
        is BigInteger -> format(input, output, position)
        is BigDecimal -> format(input, output, position)
        is IcuBigDecimal -> format(input, output, position)
        is CurrencyAmount -> format(input, output, position)
        else -> {
            val number = numberFormatInputOrNull(input) ?: throw IllegalArgumentException("Cannot format given Object as a Number")
            format(numberFormatDoubleValue(number), output, position)
        }
    }
    final override fun parseObject(source: String?, position: ParsePosition?): Any? = parse(source, position)
    fun format(number: Double): String = format(number, StringBuilder(), FormatFieldPosition(0)).toString()
    fun format(number: Long): String {
        val output = StringBuilder(19)
        format(number, output, FormatFieldPosition(0))
        return output.toString()
    }
    fun format(number: BigInteger?): String = format(number, StringBuilder(), FormatFieldPosition(0)).toString()
    fun format(number: BigDecimal?): String = format(number, StringBuilder(), FormatFieldPosition(0)).toString()
    fun format(number: IcuBigDecimal?): String = format(number, StringBuilder(), FormatFieldPosition(0)).toString()
    fun format(number: CurrencyAmount?): String = format(number, StringBuilder(), FormatFieldPosition(0)).toString()
    abstract fun format(number: Double, output: StringBuilder?, position: FormatFieldPosition?): StringBuilder
    abstract fun format(number: Long, output: StringBuilder?, position: FormatFieldPosition?): StringBuilder
    abstract fun format(number: BigInteger?, output: StringBuilder?, position: FormatFieldPosition?): StringBuilder
    abstract fun format(number: BigDecimal?, output: StringBuilder?, position: FormatFieldPosition?): StringBuilder
    abstract fun format(number: IcuBigDecimal?, output: StringBuilder?, position: FormatFieldPosition?): StringBuilder
    open fun format(amount: CurrencyAmount?, output: StringBuilder?, position: FormatFieldPosition?): StringBuilder {
        withNumberFormatMonitor {
            val save = getCurrency()
            val current = amount!!.getCurrency()
            val same = current.equals(save)
            if (!same) setCurrency(current)
            format(amount.getNumber(), output, position)
            if (!same) setCurrency(save)
        }
        return output!!
    }
    /** The facade and subclass configuration methods share the source object's reentrant monitor. */
    protected fun <T> withNumberFormatMonitor(block: () -> T): T = numberFormatCurrencyLocked(this, currencyMonitor, block)
    abstract fun parse(text: String?, position: ParsePosition?): Number?
    open fun parse(text: String?): Number? {
        val position = ParsePosition(0)
        val result = parse(text, position)
        if (position.index == 0) throw ParseException("Unparseable number: \"$text\"", position.errorIndex)
        return result
    }
    open fun parseCurrency(text: CharSequence?, position: ParsePosition?): CurrencyAmount? {
        val number = parse(text!!.toString(), position)
        return if (number == null) null else CurrencyAmount(number, getEffectiveCurrency())
    }
    open fun isParseIntegerOnly(): Boolean = parseIntegerOnly
    open fun setParseIntegerOnly(value: Boolean) { parseIntegerOnly = value }
    open fun isParseStrict(): Boolean = parseStrict
    open fun setParseStrict(value: Boolean) { parseStrict = value }
    open fun setContext(value: DisplayContext?) {
        if (value!!.type() == DisplayContext.Type.CAPITALIZATION) capitalizationSetting = value
    }
    open fun getContext(type: DisplayContext.Type?): DisplayContext =
        if (type == DisplayContext.Type.CAPITALIZATION && capitalizationSetting != null) capitalizationSetting!! else DisplayContext.CAPITALIZATION_NONE
    open fun isGroupingUsed(): Boolean = groupingUsed
    open fun setGroupingUsed(value: Boolean) { groupingUsed = value }
    open fun getMaximumIntegerDigits(): Int = maximumIntegerDigits
    open fun setMaximumIntegerDigits(value: Int) {
        maximumIntegerDigits = maxOf(0, value)
        if (minimumIntegerDigits > maximumIntegerDigits) minimumIntegerDigits = maximumIntegerDigits
    }
    open fun getMinimumIntegerDigits(): Int = minimumIntegerDigits
    open fun setMinimumIntegerDigits(value: Int) {
        minimumIntegerDigits = maxOf(0, value)
        if (maximumIntegerDigits < minimumIntegerDigits) maximumIntegerDigits = minimumIntegerDigits
    }
    open fun getMaximumFractionDigits(): Int = maximumFractionDigits
    open fun setMaximumFractionDigits(value: Int) {
        maximumFractionDigits = maxOf(0, value)
        if (minimumFractionDigits > maximumFractionDigits) minimumFractionDigits = maximumFractionDigits
    }
    open fun getMinimumFractionDigits(): Int = minimumFractionDigits
    open fun setMinimumFractionDigits(value: Int) {
        minimumFractionDigits = maxOf(0, value)
        if (maximumFractionDigits < minimumFractionDigits) maximumFractionDigits = minimumFractionDigits
    }
    open fun setCurrency(value: Currency?) { currency = value }
    open fun getCurrency(): Currency? = currency
    protected open fun getEffectiveCurrency(): Currency? {
        var result = getCurrency()
        if (result == null) {
            val locale = getLocale(ULocale.VALID_LOCALE) ?: ULocale.getDefault(ULocale.Category.FORMAT)
            result = Currency.getInstance(locale)
        }
        return result
    }
    open fun getRoundingMode(): Int = throw UnsupportedOperationException("getRoundingMode must be implemented by the subclass implementation.")
    open fun setRoundingMode(value: Int): Unit = throw UnsupportedOperationException("setRoundingMode must be implemented by the subclass implementation.")
    override fun hashCode(): Int = maximumIntegerDigits * 37 + maxFractionDigits
    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (other === this) return true
        if (this::class != other::class) return false
        other as NumberFormat
        return maximumIntegerDigits == other.maximumIntegerDigits && minimumIntegerDigits == other.minimumIntegerDigits &&
            maximumFractionDigits == other.maximumFractionDigits && minimumFractionDigits == other.minimumFractionDigits &&
            groupingUsed == other.groupingUsed && parseIntegerOnly == other.parseIntegerOnly && parseStrict == other.parseStrict &&
            capitalizationSetting == other.capitalizationSetting
    }
    /** Native subclasses provide a typed clone and use [copyNumberFormatTo] for the base fields. */
    public override fun clone(): NumberFormat = (cloneHost() as NumberFormat?)
        ?: throw UnsupportedOperationException("Native NumberFormat subclasses must supply a typed clone")
    protected fun copyNumberFormatTo(other: NumberFormat) {
        copyLocaleTo(other)
        other.groupingUsed = groupingUsed
        other.maxIntegerDigits = maxIntegerDigits; other.minIntegerDigits = minIntegerDigits
        other.maxFractionDigits = maxFractionDigits; other.minFractionDigits = minFractionDigits
        other.parseIntegerOnly = parseIntegerOnly; other.parseStrict = parseStrict
        other.maximumIntegerDigits = maximumIntegerDigits; other.minimumIntegerDigits = minimumIntegerDigits
        other.maximumFractionDigits = maximumFractionDigits; other.minimumFractionDigits = minimumFractionDigits
        other.currency = currency; other.serialVersionOnStream = serialVersionOnStream
        other.capitalizationSetting = capitalizationSetting
    }
    private fun writeObject(stream: PluralObjectOutputStream) {
        maxIntegerDigits = minOf(maximumIntegerDigits, Byte.MAX_VALUE.toInt()).toByte()
        minIntegerDigits = minOf(minimumIntegerDigits, Byte.MAX_VALUE.toInt()).toByte()
        maxFractionDigits = minOf(maximumFractionDigits, Byte.MAX_VALUE.toInt()).toByte()
        minFractionDigits = minOf(minimumFractionDigits, Byte.MAX_VALUE.toInt()).toByte()
        numberFormatWriteFields(stream)
    }
    private fun readObject(stream: PluralObjectInputStream) {
        numberFormatReadFields(stream)
        if (serialVersionOnStream < 1) {
            maximumIntegerDigits = maxIntegerDigits.toInt(); minimumIntegerDigits = minIntegerDigits.toInt()
            maximumFractionDigits = maxFractionDigits.toInt(); minimumFractionDigits = minFractionDigits.toInt()
        }
        if (serialVersionOnStream < 2) capitalizationSetting = DisplayContext.CAPITALIZATION_NONE
        if (minimumIntegerDigits > maximumIntegerDigits || minimumFractionDigits > maximumFractionDigits || minimumIntegerDigits < 0 || minimumFractionDigits < 0)
            invalidNumberFormatStream()
        serialVersionOnStream = 2
    }
    companion object {
        @Deprecated("This API is ICU internal only.")
        @JvmStatic fun getPatternForStyle(forLocale: ULocale?, choice: Int): String =
            NumberFormatPatternData.getPatternForStyle(forLocale, choice)
        @Deprecated("This API is ICU internal only.")
        @JvmStatic fun getPatternForStyleAndNumberingSystem(forLocale: ULocale?, nsName: String?, choice: Int): String =
            NumberFormatPatternData.getPatternForStyleAndNumberingSystem(forLocale, nsName, choice)
        const val NUMBERSTYLE = 0
        const val CURRENCYSTYLE = 1
        const val PERCENTSTYLE = 2
        const val SCIENTIFICSTYLE = 3
        const val INTEGERSTYLE = 4
        const val ISOCURRENCYSTYLE = 5
        const val PLURALCURRENCYSTYLE = 6
        const val ACCOUNTINGCURRENCYSTYLE = 7
        const val CASHCURRENCYSTYLE = 8
        const val STANDARDCURRENCYSTYLE = 9
        const val INTEGER_FIELD = 0
        const val FRACTION_FIELD = 1
        private const val serialVersionUID: Long = -2308460125733713944L
    }
}
internal fun numberFormatInputOrNull(value: Any?): Number? = try { formatNumberInput(value) } catch (e: IllegalArgumentException) { null }
internal expect fun numberFormatDoubleValue(value: Number): Double
internal expect fun <T> numberFormatCurrencyLocked(owner: NumberFormat, portable: ResourceCacheLock?, block: () -> T): T
internal expect fun numberFormatWriteFields(stream: PluralObjectOutputStream)
internal expect fun numberFormatReadFields(stream: PluralObjectInputStream)
internal expect fun invalidNumberFormatStream(): Nothing

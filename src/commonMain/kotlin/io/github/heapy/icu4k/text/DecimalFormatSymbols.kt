// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.CurrencyData
import io.github.heapy.icu4k.impl.DecimalFormatSymbolsData
import io.github.heapy.icu4k.impl.ICUResourceBundle
import io.github.heapy.icu4k.util.Currency
import io.github.heapy.icu4k.util.ULocale
import kotlin.jvm.JvmStatic
import kotlin.jvm.Transient

/** Mutable localized digits and symbols for decimal number formatting. */
open class DecimalFormatSymbols private constructor(skipInitialization: Boolean) : DecimalSymbolsCloneBase() {
    private var zeroDigit = '\u0000'
    private var digits: CharArray? = null
    private var digitStrings: Array<String>? = null
    @Transient private var codePointZero = 0
    private var groupingSeparator = '\u0000'
    private var groupingSeparatorString: String? = null
    private var decimalSeparator = '\u0000'
    private var decimalSeparatorString: String? = null
    private var perMill = '\u0000'
    private var perMillString: String? = null
    private var percent = '\u0000'
    private var percentString: String? = null
    private var minusSign = '\u0000'
    private var minusString: String? = null
    private var plusSign = '\u0000'
    private var plusString: String? = null
    private var monetarySeparator = '\u0000'
    private var monetarySeparatorString: String? = null
    private var monetaryGroupingSeparator = '\u0000'
    private var monetaryGroupingSeparatorString: String? = null
    private var digit = '\u0000'
    private var sigDigit = '\u0000'
    private var patternSeparator = '\u0000'
    private var infinity: String? = null
    private var NaN: String? = null
    private var approximatelyString: String? = null
    private var currencySymbol: String? = null
    private var intlCurrencySymbol: String? = null
    private var exponential = '\u0000'
    private var exponentSeparator: String? = null
    private var padEscape = '\u0000'
    private var exponentMultiplicationSign: String? = null
    private var requestedLocale: DecimalSymbolsRequestedLocale? = null
    private var ulocale: ULocale? = null
    private var validLocale: ULocale? = null
    private var actualLocale: ULocale? = null
    private var currencyPattern: String? = null
    private var currencySpcBeforeSym: Array<String?>? = null
    private var currencySpcAfterSym: Array<String?>? = null
    @Transient private var currency: Currency? = null
    private var serialVersionOnStream = 10
    constructor() : this(true) { initialize(ULocale.getDefault(ULocale.Category.FORMAT), null) }
    constructor(locale: ULocale?) : this(true) { initialize(locale!!, null) }
    open fun getZeroDigit(): Char = zeroDigit
    open fun getDigits(): CharArray = digits!!.copyOf()
    open fun setZeroDigit(zeroDigit: Char) {
        this.zeroDigit = zeroDigit
        digitStrings = digitStrings!!.copyOf()
        digits = digits!!.copyOf()
        for (i in 0..9) {
            val value = (zeroDigit.code + i).toChar()
            digitStrings!![i] = value.toString()
            digits!![i] = value
        }
        codePointZero = zeroDigit.code
    }
    open fun getDigitStrings(): Array<String> = digitStrings!!.copyOf()
    open fun getDigitStringsLocal(): Array<String> = digitStrings!!
    open fun getCodePointZero(): Int = codePointZero
    open fun setDigitStrings(input: Array<out String?>?) {
        if (input == null) throw NullPointerException("The input digit string array is null")
        require(input.size == 10) { "Number of digit strings is not 10" }
        val strings = arrayOfNulls<String>(10)
        var chars: CharArray? = CharArray(10)
        var firstCodePoint = -1
        for (i in 0..9) {
            val value = input[i] ?: throw IllegalArgumentException("The input digit string array contains a null element")
            strings[i] = value
            val cp = if (value.isEmpty()) -1 else if (value.length > 1 && value[0].isHighSurrogate() && value[1].isLowSurrogate())
                0x10000 + ((value[0].code - 0xd800) shl 10) + value[1].code - 0xdc00 else value[0].code
            val count = if (cp == -1) 0 else if (cp >= 0x10000) 2 else 1
            if (count == value.length) {
                if (count == 1 && chars != null) chars[i] = cp.toChar() else chars = null
                if (i == 0) firstCodePoint = cp else if (cp != firstCodePoint + i) firstCodePoint = -1
            } else { firstCodePoint = -1; chars = null }
        }
        @Suppress("UNCHECKED_CAST")
        val checked = strings as Array<String>
        digitStrings = checked
        codePointZero = firstCodePoint
        if (chars == null) { zeroDigit = '0'; digits = DEFAULT_DIGITS } else { zeroDigit = chars[0]; digits = chars }
    }
    open fun getGroupingSeparator(): Char = groupingSeparator
    open fun setGroupingSeparator(value: Char) { groupingSeparator = value; groupingSeparatorString = value.toString() }
    open fun getGroupingSeparatorString(): String = groupingSeparatorString!!
    open fun setGroupingSeparatorString(value: String?) {
        if (value == null) throw NullPointerException("The input grouping separator is null")
        groupingSeparatorString = value
        groupingSeparator = if (value.length == 1) value[0] else ','
    }
    open fun getDecimalSeparator(): Char = decimalSeparator
    open fun setDecimalSeparator(value: Char) { decimalSeparator = value; decimalSeparatorString = value.toString() }
    open fun getDecimalSeparatorString(): String = decimalSeparatorString!!
    open fun setDecimalSeparatorString(value: String?) {
        if (value == null) throw NullPointerException("The input decimal separator is null")
        decimalSeparatorString = value
        decimalSeparator = if (value.length == 1) value[0] else '.'
    }
    open fun getPerMill(): Char = perMill
    open fun setPerMill(value: Char) { perMill = value; perMillString = value.toString() }
    open fun getPerMillString(): String = perMillString!!
    open fun setPerMillString(value: String?) {
        if (value == null) throw NullPointerException("The input permille string is null")
        perMillString = value
        perMill = if (value.length == 1) value[0] else '‰'
    }
    open fun getPercent(): Char = percent
    open fun setPercent(value: Char) { percent = value; percentString = value.toString() }
    open fun getPercentString(): String = percentString!!
    open fun setPercentString(value: String?) {
        if (value == null) throw NullPointerException("The input percent sign is null")
        percentString = value
        percent = if (value.length == 1) value[0] else '%'
    }
    open fun getMinusSign(): Char = minusSign
    open fun setMinusSign(value: Char) { minusSign = value; minusString = value.toString() }
    open fun getMinusSignString(): String = minusString!!
    open fun setMinusSignString(value: String?) {
        if (value == null) throw NullPointerException("The input minus sign is null")
        minusString = value
        minusSign = if (value.length == 1) value[0] else '-'
    }
    open fun getPlusSign(): Char = plusSign
    open fun setPlusSign(value: Char) { plusSign = value; plusString = value.toString() }
    open fun getPlusSignString(): String = plusString!!
    open fun setPlusSignString(value: String?) {
        if (value == null) throw NullPointerException("The input plus sign is null")
        plusString = value
        plusSign = if (value.length == 1) value[0] else '+'
    }
    open fun getMonetaryDecimalSeparator(): Char = monetarySeparator
    open fun setMonetaryDecimalSeparator(value: Char) { monetarySeparator = value; monetarySeparatorString = value.toString() }
    open fun getMonetaryDecimalSeparatorString(): String = monetarySeparatorString!!
    open fun setMonetaryDecimalSeparatorString(value: String?) {
        if (value == null) throw NullPointerException("The input monetary decimal separator is null")
        monetarySeparatorString = value
        monetarySeparator = if (value.length == 1) value[0] else '.'
    }
    open fun getMonetaryGroupingSeparator(): Char = monetaryGroupingSeparator
    open fun setMonetaryGroupingSeparator(value: Char) { monetaryGroupingSeparator = value; monetaryGroupingSeparatorString = value.toString() }
    open fun getMonetaryGroupingSeparatorString(): String = monetaryGroupingSeparatorString!!
    open fun setMonetaryGroupingSeparatorString(value: String?) {
        if (value == null) throw NullPointerException("The input monetary grouping separator is null")
        monetaryGroupingSeparatorString = value
        monetaryGroupingSeparator = if (value.length == 1) value[0] else ','
    }
    open fun getSignificantDigit(): Char = sigDigit
    open fun setSignificantDigit(value: Char) { sigDigit = value }
    open fun getDigit(): Char = digit
    open fun setDigit(value: Char) { digit = value }
    open fun getPatternSeparator(): Char = patternSeparator
    open fun setPatternSeparator(value: Char) { patternSeparator = value }
    open fun getPadEscape(): Char = padEscape
    open fun setPadEscape(value: Char) { padEscape = value }
    open fun getInfinity(): String? = infinity
    open fun setInfinity(value: String?) { infinity = value }
    open fun getNaN(): String? = NaN
    open fun setNaN(value: String?) { NaN = value }
    open fun getCurrencySymbol(): String? = currencySymbol
    open fun setCurrencySymbol(value: String?) { currencySymbol = value }
    open fun getInternationalCurrencySymbol(): String? = intlCurrencySymbol
    open fun setInternationalCurrencySymbol(value: String?) { intlCurrencySymbol = value }
    open fun getExponentMultiplicationSign(): String? = exponentMultiplicationSign
    open fun setExponentMultiplicationSign(value: String?) { exponentMultiplicationSign = value }
    open fun getExponentSeparator(): String? = exponentSeparator
    open fun setExponentSeparator(value: String?) { exponentSeparator = value }
    open fun getApproximatelySignString(): String = approximatelyString!!
    open fun setApproximatelySignString(value: String?) {
        if (value == null) throw NullPointerException("The input plus sign is null")
        approximatelyString = value
    }
    open fun getCurrency(): Currency? = currency
    open fun setCurrency(value: Currency?) {
        if (value == null) throw NullPointerException()
        if (value == currency) return
        val display = CurrencyData.provider.getInstance(ulocale, true)!!
        setCurrencyOrNull(value, display)
    }
    private fun setCurrencyOrNull(value: Currency?, display: CurrencyData.CurrencyDisplayInfo) {
        currency = value
        if (value == null) { intlCurrencySymbol = "XXX"; currencySymbol = "¤"; currencyPattern = null; return }
        intlCurrencySymbol = value.getCurrencyCode()
        currencySymbol = value.getSymbol(ulocale)
        display.getFormatInfo(value.getCurrencyCode())?.let {
            setMonetaryDecimalSeparatorString(it.monetaryDecimalSeparator)
            setMonetaryGroupingSeparatorString(it.monetaryGroupingSeparator)
            currencyPattern = it.currencyPattern
        }
    }
    open fun getCurrencyPattern(): String? = currencyPattern
    open fun getPatternForCurrencySpacing(itemType: Int, beforeCurrency: Boolean): String? {
        require(itemType in 0..2) { "unknown currency spacing: $itemType" }
        return (if (beforeCurrency) currencySpcBeforeSym else currencySpcAfterSym)!![itemType]
    }
    open fun setPatternForCurrencySpacing(itemType: Int, beforeCurrency: Boolean, pattern: String?) {
        require(itemType in 0..2) { "unknown currency spacing: $itemType" }
        if (beforeCurrency) { currencySpcBeforeSym = currencySpcBeforeSym!!.copyOf(); currencySpcBeforeSym!![itemType] = pattern }
        else { currencySpcAfterSym = currencySpcAfterSym!!.copyOf(); currencySpcAfterSym!![itemType] = pattern }
    }
    open fun getULocale(): ULocale? = ulocale
    internal fun getRequestedLocale(): DecimalSymbolsRequestedLocale = requestedLocale!!
    fun getLocale(type: ULocale.Type?): ULocale? = if (type == ULocale.ACTUAL_LOCALE) actualLocale else validLocale
    internal fun setLocale(valid: ULocale?, actual: ULocale?) {
        require((valid == null) == (actual == null))
        validLocale = valid; actualLocale = actual
    }
    private fun initialize(locale: ULocale, ns: NumberingSystem?) {
        requestedLocale = decimalSymbolsRequestedLocale(locale)
        ulocale = locale
        val data = DecimalFormatSymbolsData.get(locale, ns)
        setLocale(data.validLocale, data.validLocale)
        setDigitStrings(data.digits)
        val symbols = data.symbols
        setDecimalSeparatorString(symbols[0]); setGroupingSeparatorString(symbols[1])
        patternSeparator = ';'
        setPercentString(symbols[2]); setMinusSignString(symbols[3]); setPlusSignString(symbols[4])
        setExponentSeparator(symbols[5]); setPerMillString(symbols[6]); setInfinity(symbols[7]); setNaN(symbols[8])
        setMonetaryDecimalSeparatorString(symbols[9]); setMonetaryGroupingSeparatorString(symbols[10])
        setExponentMultiplicationSign(symbols[11]); setApproximatelySignString(symbols[12])
        digit = '#'; padEscape = '*'; sigDigit = '@'
        val display = CurrencyData.provider.getInstance(ulocale, true)!!
        initSpacingInfo(display.getSpacingInfo()!!)
        setCurrencyOrNull(Currency.getInstance(ulocale), display)
    }
    private fun initSpacingInfo(info: CurrencyData.CurrencySpacingInfo) {
        currencySpcBeforeSym = info.getBeforeSymbols(); currencySpcAfterSym = info.getAfterSymbols()
    }
    /** Native subclasses override this method to copy any additional subclass state. */
    public override fun clone(): DecimalFormatSymbols {
        (shallowSymbolsClone() as DecimalFormatSymbols?)?.let { return it }
        val copy = DecimalFormatSymbols(true)
        copy.zeroDigit = zeroDigit
        copy.digits = digits
        copy.digitStrings = digitStrings
        copy.codePointZero = codePointZero
        copy.groupingSeparator = groupingSeparator
        copy.groupingSeparatorString = groupingSeparatorString
        copy.decimalSeparator = decimalSeparator
        copy.decimalSeparatorString = decimalSeparatorString
        copy.perMill = perMill
        copy.perMillString = perMillString
        copy.percent = percent
        copy.percentString = percentString
        copy.minusSign = minusSign
        copy.minusString = minusString
        copy.plusSign = plusSign
        copy.plusString = plusString
        copy.monetarySeparator = monetarySeparator
        copy.monetarySeparatorString = monetarySeparatorString
        copy.monetaryGroupingSeparator = monetaryGroupingSeparator
        copy.monetaryGroupingSeparatorString = monetaryGroupingSeparatorString
        copy.digit = digit
        copy.sigDigit = sigDigit
        copy.patternSeparator = patternSeparator
        copy.infinity = infinity
        copy.NaN = NaN
        copy.approximatelyString = approximatelyString
        copy.currencySymbol = currencySymbol
        copy.intlCurrencySymbol = intlCurrencySymbol
        copy.exponential = exponential
        copy.exponentSeparator = exponentSeparator
        copy.padEscape = padEscape
        copy.exponentMultiplicationSign = exponentMultiplicationSign
        copy.requestedLocale = requestedLocale
        copy.ulocale = ulocale
        copy.validLocale = validLocale
        copy.actualLocale = actualLocale
        copy.currencyPattern = currencyPattern
        copy.currencySpcBeforeSym = currencySpcBeforeSym
        copy.currencySpcAfterSym = currencySpcAfterSym
        copy.currency = currency
        copy.serialVersionOnStream = serialVersionOnStream
        return copy
    }
    override fun equals(other: Any?): Boolean {
        if (other !is DecimalFormatSymbols) return false
        if (this === other) return true
        for (i in 0..2) {
            if (!currencySpcBeforeSym!![i]!!.equals(other.currencySpcBeforeSym!![i])) return false
            if (!currencySpcAfterSym!![i]!!.equals(other.currencySpcAfterSym!![i])) return false
        }
        if (other.digits == null) {
            for (i in 0..9) if (digits!![i].code != other.zeroDigit.code + i) return false
        } else if (!digits!!.contentEquals(other.digits!!)) return false
        return groupingSeparator == other.groupingSeparator && decimalSeparator == other.decimalSeparator &&
            percent == other.percent && perMill == other.perMill && digit == other.digit && minusSign == other.minusSign &&
            minusString!!.equals(other.minusString) && patternSeparator == other.patternSeparator && infinity!!.equals(other.infinity) &&
            NaN!!.equals(other.NaN) && currencySymbol!!.equals(other.currencySymbol) && intlCurrencySymbol!!.equals(other.intlCurrencySymbol) &&
            padEscape == other.padEscape && plusSign == other.plusSign && plusString!!.equals(other.plusString) &&
            approximatelyString!!.equals(other.approximatelyString) && exponentSeparator!!.equals(other.exponentSeparator) &&
            monetarySeparator == other.monetarySeparator && monetaryGroupingSeparator == other.monetaryGroupingSeparator &&
            exponentMultiplicationSign!!.equals(other.exponentMultiplicationSign)
    }
    override fun hashCode(): Int = (digits!![0].code * 37 + groupingSeparator.code) * 37 + decimalSeparator.code
    private fun readObject(stream: PluralObjectInputStream) {
        decimalSymbolsReadFields(stream)
        if (serialVersionOnStream < 1) { monetarySeparator = decimalSeparator; exponential = 'E' }
        if (serialVersionOnStream < 2) { padEscape = '*'; plusSign = '+'; exponentSeparator = exponential.toString() }
        if (serialVersionOnStream < 3) requestedLocale = decimalSymbolsDefaultRequestedLocale()
        if (serialVersionOnStream < 4) ulocale = decimalSymbolsULocale(requestedLocale!!)
        if (serialVersionOnStream < 5) monetaryGroupingSeparator = groupingSeparator
        if (serialVersionOnStream < 6) initSpacingInfo(CurrencyData.CurrencySpacingInfo.DEFAULT)
        if (serialVersionOnStream < 7) {
            if (minusString == null) minusString = minusSign.toString()
            if (plusString == null) plusString = plusSign.toString()
        }
        if (serialVersionOnStream < 8 && exponentMultiplicationSign == null) exponentMultiplicationSign = "×"
        if (serialVersionOnStream < 9) {
            if (digitStrings == null) {
                if (digits != null && digits!!.size == 10) {
                    zeroDigit = digits!![0]
                    digitStrings = Array(10) { digits!![it].toString() }
                } else {
                    if (digits == null) digits = CharArray(10)
                    digitStrings = Array(10) { i -> (zeroDigit.code + i).toChar().also { digits!![i] = it }.toString() }
                }
            }
            if (groupingSeparatorString == null) groupingSeparatorString = groupingSeparator.toString()
            if (decimalSeparatorString == null) decimalSeparatorString = decimalSeparator.toString()
            if (perMillString == null) perMillString = perMill.toString()
            if (percentString == null) percentString = percent.toString()
            if (monetarySeparatorString == null) monetarySeparatorString = monetarySeparator.toString()
            if (monetaryGroupingSeparatorString == null) monetaryGroupingSeparatorString = monetaryGroupingSeparator.toString()
        }
        if (serialVersionOnStream < 10) approximatelyString = "~"
        serialVersionOnStream = 10
        currency = Currency.getInstance(intlCurrencySymbol)
        setDigitStrings(digitStrings)
    }
    companion object {
        private const val serialVersionUID: Long = 5772796243397350300L
        private val DEFAULT_DIGITS = CharArray(10) { ('0'.code + it).toChar() }
        const val CURRENCY_SPC_CURRENCY_MATCH = 0
        const val CURRENCY_SPC_SURROUNDING_MATCH = 1
        const val CURRENCY_SPC_INSERT = 2
        @JvmStatic fun getInstance(): DecimalFormatSymbols = DecimalFormatSymbols()
        @JvmStatic fun getInstance(locale: ULocale?): DecimalFormatSymbols = DecimalFormatSymbols(locale)
        @JvmStatic fun forNumberingSystem(locale: ULocale?, ns: NumberingSystem?): DecimalFormatSymbols = DecimalFormatSymbols(true).apply { initialize(locale!!, ns) }
        @JvmStatic fun getAvailableULocales(): Array<ULocale> = ICUResourceBundle.getAvailableULocales()!!
    }
}

expect abstract class DecimalSymbolsCloneBase() : io.github.heapy.icu4k.util.ICUSerializable {
    protected abstract fun clone(): Any
    protected fun shallowSymbolsClone(): Any?
}
internal expect class DecimalSymbolsRequestedLocale
internal expect fun decimalSymbolsRequestedLocale(locale: ULocale): DecimalSymbolsRequestedLocale
internal expect fun decimalSymbolsDefaultRequestedLocale(): DecimalSymbolsRequestedLocale
internal expect fun decimalSymbolsULocale(locale: DecimalSymbolsRequestedLocale): ULocale
internal expect fun decimalSymbolsReadFields(stream: PluralObjectInputStream)

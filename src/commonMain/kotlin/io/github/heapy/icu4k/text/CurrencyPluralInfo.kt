// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.CurrencyData
import io.github.heapy.icu4k.impl.number.NumberFormatPatternData
import io.github.heapy.icu4k.util.ICUCloneableBase
import io.github.heapy.icu4k.util.ULocale
import kotlin.jvm.JvmStatic

/** Plural rules and currency-unit patterns used by decimal currency formatting. */
open class CurrencyPluralInfo private constructor(skipInitialization: Boolean) : ICUCloneableBase() {
    private var pluralCountToCurrencyUnitPattern: MutableMap<String?, String?> = HashMap()
    private var pluralRules: PluralRules? = null
    private var ulocale: ULocale? = null
    constructor() : this(true) { initialize(ULocale.getDefault(ULocale.Category.FORMAT)) }
    constructor(locale: ULocale?) : this(true) { initialize(locale) }
    open fun getPluralRules(): PluralRules? = pluralRules
    open fun getCurrencyPluralPattern(pluralCount: String?): String {
        var pattern = pluralCountToCurrencyUnitPattern[pluralCount]
        if (pattern == null) {
            if (pluralCount!! != "other") pattern = pluralCountToCurrencyUnitPattern["other"]
            if (pattern == null) pattern = DEFAULT_PATTERN
        }
        return pattern
    }
    open fun getLocale(): ULocale? = ulocale
    open fun setPluralRules(ruleDescription: String?) { pluralRules = PluralRules.createRules(ruleDescription) }
    open fun setCurrencyPluralPattern(pluralCount: String?, pattern: String?) { pluralCountToCurrencyUnitPattern[pluralCount] = pattern }
    open fun setLocale(locale: ULocale?) { ulocale = locale; initialize(locale) }
    /** Native subclasses override this method to copy additional subclass state. */
    public override fun clone(): CurrencyPluralInfo {
        val other = (cloneHost() as CurrencyPluralInfo?) ?: CurrencyPluralInfo(true).also { it.pluralRules = pluralRules }
        other.ulocale = ulocale!!.clone()
        other.pluralCountToCurrencyUnitPattern = HashMap()
        for (key in pluralCountToCurrencyUnitPattern.keys) other.pluralCountToCurrencyUnitPattern[key] = pluralCountToCurrencyUnitPattern[key]
        return other
    }
    override fun equals(other: Any?): Boolean = other is CurrencyPluralInfo && pluralRules!!.equals(other.pluralRules) &&
        pluralCountToCurrencyUnitPattern == other.pluralCountToCurrencyUnitPattern
    override fun hashCode(): Int = pluralCountToCurrencyUnitPattern.hashCode() xor pluralRules!!.hashCode() xor ulocale!!.hashCode()
    internal fun select(number: Double): String = pluralRules!!.select(number)
    open fun select(numberInfo: PluralRules.FixedDecimal?): String = pluralRules!!.select(numberInfo)
    open fun pluralPatternIterator(): MutableIterator<String?> = pluralCountToCurrencyUnitPattern.keys.iterator()
    private fun initialize(locale: ULocale?) {
        ulocale = locale
        pluralRules = PluralRules.forLocale(locale)
        setupCurrencyPluralPattern(locale)
    }
    private fun setupCurrencyPluralPattern(locale: ULocale?) {
        pluralCountToCurrencyUnitPattern = HashMap()
        var numberPattern = NumberFormatPatternData.getPatternForStyle(locale, NumberFormatPatternData.NUMBERSTYLE)
        val separator = numberPattern.indexOf(';')
        var negative: String? = null
        if (separator != -1) { negative = numberPattern.substring(separator + 1); numberPattern = numberPattern.substring(0, separator) }
        val patterns = CurrencyData.provider.getInstance(locale, true)!!.getUnitPatterns()!!
        for ((count, pattern) in patterns) {
            var value = pattern.replace("{0}", numberPattern).replace("{1}", "¤¤¤")
            if (separator != -1) value += ";" + pattern.replace("{0}", negative!!).replace("{1}", "¤¤¤")
            pluralCountToCurrencyUnitPattern[count] = value
        }
    }
    companion object {
        private const val serialVersionUID: Long = 1L
        private const val DEFAULT_PATTERN = "\u0000.## ¤¤¤"
        @JvmStatic fun getInstance(): CurrencyPluralInfo = CurrencyPluralInfo()
        @JvmStatic fun getInstance(locale: ULocale?): CurrencyPluralInfo = CurrencyPluralInfo(locale)
    }
}

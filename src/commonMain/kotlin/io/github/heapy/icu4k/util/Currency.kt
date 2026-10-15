// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.util

import io.github.heapy.icu4k.impl.CurrencyMetadata
import io.github.heapy.icu4k.impl.CurrencyNames
import io.github.heapy.icu4k.impl.CurrencyParsing
import io.github.heapy.icu4k.impl.CurrencyService
import io.github.heapy.icu4k.impl.ICUResourceBundle
import io.github.heapy.icu4k.impl.TextTrieMap
import io.github.heapy.icu4k.impl.SoftCache
import io.github.heapy.icu4k.text.CurrencyMetaInfo
import io.github.heapy.icu4k.text.ParsePosition
import kotlin.jvm.JvmStatic

/** ISO 4217 currency unit, with ICU locale names, metadata and service registration. */
open class Currency protected constructor(theISOCode: String?) : MeasureUnit("currency", theISOCode) {
    enum class CurrencyUsage { STANDARD, CASH }
    class CurrencyStringInfo(private val isoCode: String?, private val currencyString: String?) {
        fun getISOCode(): String? = isoCode
        fun getCurrencyString(): String? = currencyString
    }
    open fun getCurrencyCode(): String? = subType
    open fun getNumericCode(): Int = CurrencyMetadata.numericCode(subType)
    open fun getSymbol(): String? = getSymbol(ULocale.getDefault(ULocale.Category.DISPLAY))
    open fun getSymbol(locale: ULocale?): String? = getName(locale, SYMBOL_NAME, null)
    open fun getName(locale: ULocale?, nameStyle: Int, isChoiceFormat: BooleanArray?): String? = CurrencyNames.name(subType, locale, nameStyle, isChoiceFormat)
    open fun getName(locale: ULocale?, nameStyle: Int, pluralCount: String?, isChoiceFormat: BooleanArray?): String? = CurrencyNames.name(subType, locale, nameStyle, pluralCount, isChoiceFormat)
    open fun getDisplayName(): String? = getName(ULocale.getDefault(), LONG_NAME, null)
    open fun getDisplayName(locale: ULocale?): String? = getName(locale, LONG_NAME, null)
    open fun getDefaultFractionDigits(): Int = getDefaultFractionDigits(CurrencyUsage.STANDARD)
    open fun getDefaultFractionDigits(usage: CurrencyUsage?): Int = CurrencyMetadata.fractionDigits(subType, usage)
    open fun getRoundingIncrement(): Double = getRoundingIncrement(CurrencyUsage.STANDARD)
    open fun getRoundingIncrement(usage: CurrencyUsage?): Double = CurrencyMetadata.roundingIncrement(subType, usage)
    override fun toString(): String = subType!!
    private fun writeReplace(): Any = measureUnitSerialProxy(type, subType)
    companion object {
        const val SYMBOL_NAME = 0
        const val LONG_NAME = 1
        const val PLURAL_LONG_NAME = 2
        const val NARROW_SYMBOL_NAME = 3
        const val FORMAL_SYMBOL_NAME = 4
        const val VARIANT_SYMBOL_NAME = 5
        internal fun createForUnit(code: String): Currency = Currency(code)
        @JvmStatic fun getInstance(theISOCode: String?): Currency = MeasureUnit.internalGetInstance("currency", CurrencyMetadata.normalizeCode(theISOCode)) as Currency
        @JvmStatic fun getInstance(locale: ULocale?): Currency? {
            val currency = locale!!.getKeywordValue("currency")
            if (currency != null) return getInstance(currency)
            return if (CurrencyService.isInitialized()) CurrencyService.create(locale) else createCurrency(locale)
        }
        private val regionCache = object : SoftCache<String, Currency, Unit>() {
            override fun createInstance(key: String, data: Unit): Currency? = CurrencyMetadata.regionCurrencyCode(key)?.let { getInstance(it) }
        }
        internal fun createCurrency(locale: ULocale): Currency? = regionCache.getInstance(ULocale.getRegionForSupplementalData(locale, false), Unit)
        @JvmStatic fun getAvailableCurrencyCodes(locale: ULocale?, date: Long): Array<String>? = CurrencyMetadata.availableCurrencyCodes(locale, date)
        @JvmStatic fun getAvailableCurrencies(): Set<Currency> = CurrencyMetaInfo.getInstance().currencies(CurrencyMetaInfo.CurrencyFilter.all()).mapTo(HashSet()) { getInstance(it) }
        @JvmStatic fun registerInstance(currency: Currency?, locale: ULocale?): Any = CurrencyService.get().registerObject(currency, locale)
        @JvmStatic fun unregister(registryKey: Any?): Boolean {
            if (registryKey == null) throw IllegalArgumentException("registryKey must not be null")
            if (!CurrencyService.isInitialized()) return false
            return CurrencyService.unregister(registryKey)
        }
        @JvmStatic fun getAvailableULocales(): Array<ULocale> = if (CurrencyService.isInitialized() && !CurrencyService.get().isDefault()) CurrencyService.get().getAvailableULocales() else ICUResourceBundle.getAvailableULocales()!!
        @JvmStatic fun getKeywordValuesForLocale(key: String?, locale: ULocale?, commonlyUsed: Boolean): Array<String> = CurrencyMetadata.keywordValues(key, locale, commonlyUsed)
        @JvmStatic fun parse(locale: ULocale?, text: String?, type: Int, pos: ParsePosition?): String? = CurrencyParsing.parse(locale, text, type, pos)
        @JvmStatic fun getParsingTrie(locale: ULocale?, type: Int): TextTrieMap<CurrencyStringInfo> = CurrencyParsing.getParsingTrie(locale, type)
        /** Inclusive epoch-millisecond bounds; JVM also accepts java.util.Date. */
        @JvmStatic fun isAvailable(code: String?, from: Long?, to: Long?): Boolean = CurrencyMetadata.isAvailable(code, from, to)
    }
}

// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.impl.locale.localeReadOnlyList
import io.github.heapy.icu4k.text.CurrencyMetaInfo
import io.github.heapy.icu4k.text.CurrencyMetaInfo.CurrencyFilter
import io.github.heapy.icu4k.util.CurrencyUsage
import io.github.heapy.icu4k.util.MissingResourceException
import io.github.heapy.icu4k.util.ULocale
import io.github.heapy.icu4k.util.UResourceBundle

/** Currency's data queries, extracted pending its public MeasureUnit subclass. */
internal object CurrencyMetadata {
    private val lock = createResourceCacheLock()
    private var tenderCodes: CacheReference<List<String>>? = null
    private var allCodes: CacheReference<Set<String>>? = null
    private val empty = emptyArray<String>()
    private val und = ULocale("und")
    private val powers = intArrayOf(1, 10, 100, 1000, 10000, 100000, 1000000, 10000000, 100000000, 1000000000)
    fun isAlpha3Code(code: String?): Boolean = code!!.length == 3 && code.all { it in 'A'..'Z' || it in 'a'..'z' }
    fun normalizeCode(code: String?): String {
        if (code == null) throw NullPointerException("The input currency code is null.")
        if (!isAlpha3Code(code)) throw IllegalArgumentException("The input currency code is not 3-letter alphabetic code.")
        return code.uppercase()
    }
    fun numericCode(code: String?): Int = try {
        UResourceBundle.getBundleInstance(ICUData.ICU_BASE_NAME, "currencyNumericCodes").get("codeMap").get(code!!).getInt()
    } catch (_: MissingResourceException) { 0 }
    fun regionCurrencyCode(region: String?): String? {
        val info = CurrencyMetaInfo.getInstance()
        var codes = info.currencies(CurrencyFilter.onRegion(region).withTender())
        if (codes.isEmpty()) codes = info.currencies(CurrencyFilter.onRegion(region))
        return codes.firstOrNull()
    }
    fun availableCurrencyCodes(locale: ULocale?, date: Long): Array<String>? {
        val region = ULocale.getRegionForSupplementalData(locale, false)
        val codes = tender(CurrencyFilter.onDate(date).withRegion(region))
        return if (codes.isEmpty()) null else codes.toTypedArray()
    }
    fun keywordValues(key: String?, locale: ULocale?, commonlyUsed: Boolean): Array<String> {
        if (key != "currency") return empty
        if (!commonlyUsed) return allTender().toTypedArray()
        if (locale == und) return empty
        val region = ULocale.getRegionForSupplementalData(locale, true)
        val codes = tender(CurrencyFilter.now().withRegion(region))
        return if (codes.isEmpty()) empty else codes.toTypedArray()
    }
    fun isAvailable(code: String?, from: Long?, to: Long?): Boolean {
        if (!isAlpha3Code(code)) return false
        if (from != null && to != null && from > to) throw IllegalArgumentException("To is before from")
        val normalized = code!!.uppercase()
        if (normalized !in all()) return false
        if (from == null && to == null) return true
        return normalized in CurrencyMetaInfo.getInstance().currencies(CurrencyFilter.onDateRange(from ?: Long.MIN_VALUE, to ?: Long.MAX_VALUE).withCurrency(normalized))
    }
    fun fractionDigits(code: String?, usage: CurrencyUsage?): Int = CurrencyMetaInfo.getInstance().currencyDigits(code, usage).fractionDigits
    fun roundingIncrement(code: String?, usage: CurrencyUsage?): Double {
        val digits = CurrencyMetaInfo.getInstance().currencyDigits(code, usage)
        if (digits.roundingIncrement == 0 || digits.fractionDigits !in powers.indices) return 0.0
        return digits.roundingIncrement.toDouble() / powers[digits.fractionDigits]
    }
    private fun tender(filter: CurrencyFilter): List<String> = CurrencyMetaInfo.getInstance().currencies(filter.withTender())
    private fun allTender(): List<String> = lock.withLock {
        tenderCodes?.get() ?: localeReadOnlyList(tender(CurrencyFilter.all())).also { tenderCodes = createSimpleCacheReference(it, false) }
    }
    private fun all(): Set<String> = lock.withLock {
        allCodes?.get() ?: CurrencyMetaInfo.getInstance().currencies(CurrencyFilter.all()).toHashSet().also { allCodes = createSimpleCacheReference(it, false) }
    }
}

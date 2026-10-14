// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.impl.locale.localeReadOnlyList
import io.github.heapy.icu4k.text.CurrencyMetaInfo
import io.github.heapy.icu4k.util.CurrencyUsage

open class ICUCurrencyMetaInfo : CurrencyMetaInfo() {
    private val bundle = ICUResourceBundle.getBundleInstance(ICUData.ICU_CURR_BASE_NAME, "supplementalData")
    private val regionInfo = bundle.findTopLevel("CurrencyMap")!!
    private val digitInfo = bundle.findTopLevel("CurrencyMeta")!!
    override fun currencyDigits(isoCode: String?): CurrencyDigits = currencyDigits(isoCode, CurrencyUsage.STANDARD)
    override fun currencyDigits(isoCode: String?, usage: CurrencyUsage?): CurrencyDigits {
        val resource = digitInfo.findWithFallback(isoCode!!) ?: digitInfo.findWithFallback("DEFAULT")!!
        val digits = resource.getIntVector()
        return if (usage == CurrencyUsage.CASH) CurrencyDigits(checkedArrayElement(digits, 2), checkedArrayElement(digits, 3)) else CurrencyDigits(checkedArrayElement(digits, 0), checkedArrayElement(digits, 1))
    }
    override fun currencyInfo(filter: CurrencyFilter?): List<CurrencyInfo> = collect(filter, Int.MAX_VALUE) { region, code, from, to, priority, tender -> CurrencyInfo(region, code, from, to, priority, tender) }
    override fun currencies(filter: CurrencyFilter?): List<String> = collect(filter, CURRENCY) { _, code, _, _, _, _ -> code!! }
    override fun regions(filter: CurrencyFilter?): List<String> = collect(filter, REGION) { region, _, _, _, _, _ -> region!! }
    private fun <T> collect(input: CurrencyFilter?, collector: Int, create: (String?, String?, Long, Long, Int, Boolean) -> T): List<T> {
        val filter = input ?: CurrencyFilter.all()
        var needed = collector
        if (filter.region != null) needed = needed or REGION
        if (filter.currency != null) needed = needed or CURRENCY
        if (filter.from != Long.MIN_VALUE || filter.to != Long.MAX_VALUE) needed = needed or DATE
        if (filter.tenderOnly) needed = needed or TENDER
        val values = mutableListOf<T>()
        val seen = mutableSetOf<T>()
        fun add(region: String?, code: String?, from: Long, to: Long, priority: Int, tender: Boolean) {
            val value = create(region, code, from, to, priority, tender)
            if (collector == Int.MAX_VALUE || seen.add(value)) values.add(value)
        }
        fun region(resource: ICUResourceBundle) {
            val region = resource.getKey()
            if (needed == REGION) { add(region, null, 0, 0, -1, false); return }
            for (index in 0 until resource.getSize()) {
                val record = resource.at(index)!!
                if (record.getSize() == 0) continue
                var code: String? = null
                var from = Long.MIN_VALUE; var to = Long.MAX_VALUE; var tender = true
                if (needed and CURRENCY != 0) {
                    code = record.at("id")!!.getString()
                    if (filter.currency != null && filter.currency != code) continue
                }
                if (needed and DATE != 0) {
                    from = date(record.at("from"), Long.MIN_VALUE); to = date(record.at("to"), Long.MAX_VALUE)
                    if (filter.from > to || filter.to < from) continue
                }
                if (needed and TENDER != 0) {
                    tender = record.at("tender")?.getString().let { it == null || it == "true" }
                    if (filter.tenderOnly && !tender) continue
                }
                add(region, code, from, to, index, tender)
            }
        }
        if (filter.region != null) regionInfo.findWithFallback(filter.region)?.let { region(it) }
        else for (index in 0 until regionInfo.getSize()) region(regionInfo.at(index)!!)
        return localeReadOnlyList(values)
    }
    private fun date(resource: ICUResourceBundle?, default: Long): Long {
        if (resource == null) return default
        val data = resource.getIntVector()
        return (checkedArrayElement(data, 0).toLong() shl 32) or (checkedArrayElement(data, 1).toLong() and 0xffffffffL)
    }
    private companion object { const val REGION = 1; const val CURRENCY = 2; const val DATE = 4; const val TENDER = 8 }
}

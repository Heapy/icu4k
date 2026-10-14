// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.Grego
import io.github.heapy.icu4k.impl.ICUCurrencyMetaInfo
import io.github.heapy.icu4k.util.CurrencyUsage
import kotlin.time.Clock

open class CurrencyMetaInfo protected constructor() {
    class CurrencyFilter private constructor(val region: String?, val currency: String?, val from: Long, val to: Long, val tenderOnly: Boolean) {
        fun withRegion(region: String?): CurrencyFilter = CurrencyFilter(region, currency, from, to, tenderOnly)
        fun withCurrency(currency: String?): CurrencyFilter = CurrencyFilter(region, currency, from, to, tenderOnly)
        fun withDate(date: Long): CurrencyFilter = withDateRange(date, date)
        fun withDateRange(from: Long, to: Long): CurrencyFilter = CurrencyFilter(region, currency, from, to, tenderOnly)
        fun withTender(): CurrencyFilter = CurrencyFilter(region, currency, from, to, true)
        override fun equals(other: Any?): Boolean = other is CurrencyFilter && equals(other)
        fun equals(other: CurrencyFilter?): Boolean = this === other || other != null && region == other.region && currency == other.currency && from == other.from && to == other.to && tenderOnly == other.tenderOnly
        override fun hashCode(): Int {
            var result = region?.hashCode() ?: 0
            if (currency != null) result = result * 31 + currency.hashCode()
            result = result * 31 + from.toInt(); result = result * 31 + (from ushr 32).toInt()
            result = result * 31 + to.toInt(); result = result * 31 + (to ushr 32).toInt()
            return result * 31 + if (tenderOnly) 1 else 0
        }
        override fun toString(): String = debug("CurrencyFilter", listOf("region" to region, "currency" to currency, "from" to date(from), "to" to date(to), "tenderOnly" to tenderOnly.toString()))
        companion object {
            private val ALL = CurrencyFilter(null, null, Long.MIN_VALUE, Long.MAX_VALUE, false)
            fun all(): CurrencyFilter = ALL
            fun now(): CurrencyFilter = ALL.withDate(Clock.System.now().toEpochMilliseconds())
            fun onRegion(region: String?): CurrencyFilter = ALL.withRegion(region)
            fun onCurrency(currency: String?): CurrencyFilter = ALL.withCurrency(currency)
            fun onDate(date: Long): CurrencyFilter = ALL.withDate(date)
            fun onDateRange(from: Long, to: Long): CurrencyFilter = ALL.withDateRange(from, to)
            fun onTender(): CurrencyFilter = ALL.withTender()
        }
    }
    class CurrencyDigits(val fractionDigits: Int, val roundingIncrement: Int) {
        override fun toString(): String = debug("CurrencyDigits", listOf("fractionDigits" to fractionDigits.toString(), "roundingIncrement" to roundingIncrement.toString()))
    }
    class CurrencyInfo(val region: String?, val code: String?, val from: Long, val to: Long, val priority: Int, private val tender: Boolean) {
        constructor(region: String?, code: String?, from: Long, to: Long, priority: Int) : this(region, code, from, to, priority, true)
        fun isTender(): Boolean = tender
        override fun toString(): String = debug("CurrencyInfo", listOf("region" to region, "code" to code, "from" to date(from), "to" to date(to), "priority" to priority.toString()))
    }
    open fun currencyInfo(filter: CurrencyFilter?): List<CurrencyInfo> = emptyList()
    open fun currencies(filter: CurrencyFilter?): List<String> = emptyList()
    open fun regions(filter: CurrencyFilter?): List<String> = emptyList()
    open fun currencyDigits(isoCode: String?): CurrencyDigits = currencyDigits(isoCode, CurrencyUsage.STANDARD)
    open fun currencyDigits(isoCode: String?, usage: CurrencyUsage?): CurrencyDigits = defaultDigits
    companion object {
        protected val defaultDigits = CurrencyDigits(2, 0)
        private val implementation: Pair<CurrencyMetaInfo, Boolean> by lazy {
            try { ICUCurrencyMetaInfo() to true } catch (_: Throwable) { CurrencyMetaInfo() to false }
        }
        fun getInstance(): CurrencyMetaInfo = implementation.first
        // The source ignores noSubstitute and returns null whenever data is unavailable.
        fun getInstance(noSubstitute: Boolean): CurrencyMetaInfo? = if (implementation.second) implementation.first else null
        @Deprecated("This API is ICU internal only.") fun hasData(): Boolean = implementation.second
        private fun date(value: Long): String? = if (value == Long.MIN_VALUE || value == Long.MAX_VALUE) null else Grego.timeToString(value)
        private fun debug(name: String, fields: List<Pair<String, String?>>): String = fields.filter { it.second != null }.joinToString(",", "$name(", ")") { "${it.first}='${it.second}'" }
    }
}

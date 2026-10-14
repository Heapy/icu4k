// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.text.CurrencyDisplayNames
import io.github.heapy.icu4k.util.ULocale

class CurrencyData private constructor() {
    interface CurrencyDisplayInfoProvider {
        fun getInstance(locale: ULocale?, withFallback: Boolean): CurrencyDisplayInfo?
        fun hasData(): Boolean
    }
    abstract class CurrencyDisplayInfo : CurrencyDisplayNames() {
        abstract fun getUnitPatterns(): Map<String, String>?
        abstract fun getFormatInfo(isoCode: String?): CurrencyFormatInfo?
        abstract fun getSpacingInfo(): CurrencySpacingInfo?
    }
    class CurrencyFormatInfo(val isoCode: String?, val currencyPattern: String?, val monetaryDecimalSeparator: String?, val monetaryGroupingSeparator: String?)
    class CurrencySpacingInfo {
        enum class SpacingType { BEFORE, AFTER, COUNT }
        enum class SpacingPattern { CURRENCY_MATCH, SURROUNDING_MATCH, INSERT_BETWEEN, COUNT }
        private val symbols = Array(2) { arrayOfNulls<String>(3) }
        var hasBeforeCurrency = false
        var hasAfterCurrency = false
        constructor()
        constructor(vararg strings: String?) {
            var index = 0
            for (row in symbols) for (j in row.indices) row[j] = currencySpacingStringAt(strings, index++)
        }
        fun setSymbolIfNull(type: SpacingType?, pattern: SpacingPattern?, value: String?) {
            val row = spacingRow(type!!.ordinal)
            val j = pattern!!.ordinal
            if (j !in row.indices) currencySpacingBoundsFailure(j, 3)
            if (row[j] == null) row[j] = value
        }
        private fun spacingRow(index: Int): Array<String?> {
            if (index !in symbols.indices) currencySpacingBoundsFailure(index, 2)
            return symbols[index]
        }
        fun getBeforeSymbols(): Array<String?> = symbols[0]
        fun getAfterSymbols(): Array<String?> = symbols[1]
        companion object { val DEFAULT = CurrencySpacingInfo("[:letter:]", "[:digit:]", " ", "[:letter:]", "[:digit:]", " ") }
    }
    open class DefaultInfo private constructor(private val fallback: Boolean) : CurrencyDisplayInfo() {
        override fun getName(isoCode: String?): String? = if (fallback) isoCode else null
        override fun getPluralName(isoCode: String?, pluralKey: String?): String? = if (fallback) isoCode else null
        override fun getSymbol(isoCode: String?): String? = if (fallback) isoCode else null
        override fun getNarrowSymbol(isoCode: String?): String? = if (fallback) isoCode else null
        override fun getFormalSymbol(isoCode: String?): String? = if (fallback) isoCode else null
        override fun getVariantSymbol(isoCode: String?): String? = if (fallback) isoCode else null
        override fun symbolMap(): Map<String, String> = emptyMap()
        override fun nameMap(): Map<String, String> = emptyMap()
        override fun getULocale(): ULocale = ULocale.ROOT
        override fun getUnitPatterns(): Map<String, String>? = if (fallback) emptyMap() else null
        override fun getFormatInfo(isoCode: String?): CurrencyFormatInfo? = null
        override fun getSpacingInfo(): CurrencySpacingInfo? = if (fallback) CurrencySpacingInfo.DEFAULT else null
        companion object {
            private val fallbackInstance = DefaultInfo(true)
            private val noFallbackInstance = DefaultInfo(false)
            fun getWithFallback(fallback: Boolean): CurrencyDisplayInfo = if (fallback) fallbackInstance else noFallbackInstance
        }
    }
    companion object {
        val provider: CurrencyDisplayInfoProvider = try { ICUCurrencyDisplayInfoProvider() } catch (_: Throwable) {
            object : CurrencyDisplayInfoProvider {
                override fun getInstance(locale: ULocale?, withFallback: Boolean): CurrencyDisplayInfo = DefaultInfo.getWithFallback(withFallback)
                override fun hasData(): Boolean = false
            }
        }
    }
}
internal expect fun currencySpacingStringAt(values: Array<out String?>, index: Int): String?

internal expect fun currencySpacingBoundsFailure(index: Int, length: Int): Nothing

// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.CurrencyData
import io.github.heapy.icu4k.util.ULocale

abstract class CurrencyDisplayNames protected constructor() {
    abstract fun getULocale(): ULocale
    abstract fun getSymbol(isoCode: String?): String?
    abstract fun getNarrowSymbol(isoCode: String?): String?
    abstract fun getFormalSymbol(isoCode: String?): String?
    abstract fun getVariantSymbol(isoCode: String?): String?
    abstract fun getName(isoCode: String?): String?
    abstract fun getPluralName(isoCode: String?, pluralKey: String?): String?
    abstract fun symbolMap(): Map<String, String>
    abstract fun nameMap(): Map<String, String>
    companion object {
        fun getInstance(locale: ULocale?): CurrencyDisplayNames = CurrencyData.provider.getInstance(locale, true)!!
        fun getInstance(locale: ULocale?, noSubstitute: Boolean): CurrencyDisplayNames? = CurrencyData.provider.getInstance(locale, !noSubstitute)
        @Deprecated("This API is ICU internal only.") fun hasData(): Boolean = CurrencyData.provider.hasData()
    }
}

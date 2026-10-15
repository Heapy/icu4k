// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
// Service/cache policy from ICU4J 78.3 NumberFormatServiceShim.
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.ICULocaleService
import io.github.heapy.icu4k.impl.ICUResourceBundle
import io.github.heapy.icu4k.impl.ICUService
import io.github.heapy.icu4k.util.Currency
import io.github.heapy.icu4k.util.MissingResourceException
import io.github.heapy.icu4k.util.ULocale

/** Registry engine; the built-in numeric/RBNF creator is connected with the factory facade. */
internal class NumberFormatServiceCore(createBuiltin: (ULocale?, Int) -> NumberFormat?) {
    internal val service: ICULocaleService = object : ICULocaleService("NumberFormat") {
        init {
            registerFactory(object : ICUResourceBundleFactory() {
                override fun handleCreate(loc: ULocale?, kind: Int, service: ICUService?): Any? = createBuiltin(loc, kind)
            })
            markDefault()
        }
    }
    fun getAvailableULocales(): Array<ULocale>? = if (service.isDefault()) ICUResourceBundle.getAvailableULocales() else service.getAvailableULocales()
    fun registerFactory(factory: NumberFormatFactory?): Any = service.registerFactory(NFFactory(factory))
    fun unregister(registryKey: Any?): Boolean = service.unregisterFactory(registryKey as ICUService.Factory?)
    fun createInstance(desiredLocale: ULocale?, choice: Int): NumberFormat {
        val actualLoc = arrayOfNulls<ULocale>(1)
        var fmt: NumberFormat? = service.get(desiredLocale, choice, actualLoc) as NumberFormat?
            ?: throw MissingResourceException("Unable to construct NumberFormat", "", "")
        fmt = fmt!!.clone() as NumberFormat?
        if (choice == NumberFormat.CURRENCYSTYLE || choice == NumberFormat.ISOCURRENCYSTYLE ||
            choice == NumberFormat.PLURALCURRENCYSTYLE || choice == NumberFormat.ACCOUNTINGCURRENCYSTYLE ||
            choice == NumberFormat.CASHCURRENCYSTYLE || choice == NumberFormat.STANDARDCURRENCYSTYLE) {
            val currency = Currency.getInstance(desiredLocale)
            fmt!!.setCurrency(currency)
        }
        val uloc = actualLoc[0]
        fmt!!.setLocale(uloc, uloc)
        return fmt
    }
    private class NFFactory(private val delegate: NumberFormatFactory?) : ICULocaleService.LocaleKeyFactory(delegate!!.visible()) {
        override fun create(key: ICUService.Key?, service: ICUService?): Any? {
            if (!handlesKey(key) || key !is ICULocaleService.LocaleKey) return null
            val result = delegate!!.createFormat(key.canonicalLocale(), key.kind())
            return result ?: service!!.getKey(key, null, this)
        }
        override fun getSupportedIDs(): Set<String> = delegate!!.getSupportedLocaleNames()!!
    }
}

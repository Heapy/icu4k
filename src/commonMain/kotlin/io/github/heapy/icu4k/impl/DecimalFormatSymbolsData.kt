// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.text.NumberingSystem
import io.github.heapy.icu4k.util.MissingResourceException
import io.github.heapy.icu4k.util.ULocale
import io.github.heapy.icu4k.util.UResourceBundle

internal object DecimalFormatSymbolsData {
    class Data(val validLocale: ULocale, val digits: Array<String>, val symbols: Array<String>)
    private val keys = arrayOf("decimal", "group", "percentSign", "minusSign", "plusSign", "exponential", "perMille", "infinity", "nan", "currencyDecimal", "currencyGroup", "superscriptingExponent", "approximatelySign")
    private val defaults = arrayOf(".", ",", "%", "-", "+", "E", "‰", "∞", "NaN", null, null, "×", "~")
    private val cache = object : SoftCache<ULocale, Data, Unit>() {
        override fun createInstance(key: ULocale, data: Unit): Data = load(key)
    }
    fun get(locale: ULocale, ns: NumberingSystem?): Data = cache.getInstance(if (ns == null) locale else locale.setKeywordValue("numbers", ns.getName()), Unit)!!
    private fun load(locale: ULocale): Data {
        val ns = NumberingSystem.getInstance(locale)
        val digits: Array<String>
        val name: String?
        if (ns.getRadix() == 10 && !ns.isAlgorithmic() && NumberingSystem.isValidDigitString(ns.getDescription())) {
            val description = ns.getDescription()!!
            var offset = 0
            digits = Array(10) {
                val next = offset + if (description[offset].isHighSurrogate() && offset + 1 < description.length && description[offset + 1].isLowSurrogate()) 2 else 1
                description.substring(offset, next).also { offset = next }
            }
            name = ns.getName()
        } else { digits = Array(10) { it.toString() }; name = "latn" }
        val bundle = UResourceBundle.getBundleInstance(ICUData.ICU_BASE_NAME, locale) as ICUResourceBundle
        val symbols = arrayOfNulls<String>(keys.size)
        val sink = object : UResource.Sink() {
            override fun put(key: UResource.Key, value: UResource.Value, noFallback: Boolean) {
                val table = value.getTable()
                var index = 0
                while (table.getKeyAndValue(index++, key, value)) {
                    for (i in keys.indices) if (key.contentEquals(keys[i])) {
                        if (symbols[i] == null) symbols[i] = value.toString()
                        break
                    }
                }
            }
        }
        try { bundle.getAllItemsWithFallback("NumberElements/$name/symbols", sink) } catch (_: MissingResourceException) {}
        if (symbols.any { it == null } && name != "latn") bundle.getAllItemsWithFallback("NumberElements/latn/symbols", sink)
        for (i in symbols.indices) if (symbols[i] == null) symbols[i] = defaults[i]
        if (symbols[9] == null) symbols[9] = symbols[0]
        if (symbols[10] == null) symbols[10] = symbols[1]
        return Data(bundle.getULocale(), digits, Array(symbols.size) { symbols[it]!! })
    }
}

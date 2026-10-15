// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.text.CurrencyDisplayNames
import io.github.heapy.icu4k.text.ParsePosition
import io.github.heapy.icu4k.util.ULocale

/** Shared parsing implementation for the public Currency facade. */
internal object CurrencyParsing {
    private val cache = SimpleCache<ULocale?, List<TextTrieMap<CurrencyStringInfo>>>()
    fun parse(locale: ULocale?, text: String?, type: Int, position: ParsePosition?): String? {
        val tries = tries(locale)
        var handler = NameResultHandler()
        tries[1].find(text, position!!.index, handler)
        var result = handler.isoCode
        var length = handler.matchLength
        if (type != 1) {
            handler = NameResultHandler()
            tries[0].find(text, position.index, handler)
            if (handler.matchLength > length) {
                result = handler.isoCode
                length = handler.matchLength
            }
        }
        position.index += length
        return result
    }
    fun getParsingTrie(locale: ULocale?, type: Int): TextTrieMap<CurrencyStringInfo> = tries(locale)[if (type == 1) 1 else 0]
    private fun tries(locale: ULocale?): List<TextTrieMap<CurrencyStringInfo>> {
        cache.get(locale)?.let { return it }
        val symbolTrie = TextTrieMap<CurrencyStringInfo>(false)
        val nameTrie = TextTrieMap<CurrencyStringInfo>(true)
        val names = CurrencyDisplayNames.getInstance(locale)
        for ((symbol, isoCode) in names.symbolMap()) {
            val value = CurrencyStringInfo(isoCode, symbol)
            val key = StaticUnicodeSets.chooseCurrency(symbol)
            if (key == null) symbolTrie.put(symbol, value)
            else for (equivalent in StaticUnicodeSets.get(key)) symbolTrie.put(equivalent, value)
        }
        for ((name, isoCode) in names.nameMap()) nameTrie.put(name, CurrencyStringInfo(isoCode, name))
        return listOf(symbolTrie, nameTrie).also { cache.put(locale, it) }
    }
    private class NameResultHandler : TextTrieMap.ResultHandler<CurrencyStringInfo> {
        var isoCode: String? = null
        var matchLength = 0
        override fun handlePrefixMatch(matchLength: Int, values: MutableIterator<CurrencyStringInfo>): Boolean {
            if (values.hasNext()) {
                isoCode = values.next().getISOCode()
                this.matchLength = matchLength
            }
            return true
        }
    }
}

internal typealias CurrencyStringInfo = io.github.heapy.icu4k.util.Currency.CurrencyStringInfo

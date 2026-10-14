// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.ICUData
import io.github.heapy.icu4k.impl.ICUResourceBundle
import io.github.heapy.icu4k.impl.SoftCache
import io.github.heapy.icu4k.util.MissingResourceException
import io.github.heapy.icu4k.util.ULocale
import io.github.heapy.icu4k.util.UResourceBundle

/** Numeric digit descriptions and algorithmic numbering-system rule names. */
open class NumberingSystem {
    private var description: String? = "0123456789"
    private var radix = 10
    private var algorithmic = false
    private var name: String? = "latn"
    open fun getRadix(): Int = radix
    open fun getDescription(): String? = description
    open fun getName(): String? = name
    open fun isAlgorithmic(): Boolean = algorithmic

    companion object {
        val LATIN: NumberingSystem? = lookupInstanceByName("latn")
        fun getInstance(radix: Int, isAlgorithmic: Boolean, description: String?): NumberingSystem = create(null, radix, isAlgorithmic, description)
        private fun create(name: String?, radix: Int, algorithmic: Boolean, description: String?): NumberingSystem {
            if (radix < 2) throw IllegalArgumentException("Invalid radix for numbering system")
            if (!algorithmic && (UTF16.countCodePoint(description!!) != radix || !isValidDigitString(description)))
                throw IllegalArgumentException("Invalid digit string for numbering system")
            return NumberingSystem().also {
                it.name = name; it.radix = radix; it.algorithmic = algorithmic; it.description = description
            }
        }
        fun isValidDigitString(string: String?): Boolean = UTF16.countCodePoint(string!!) == 10
        fun getInstance(): NumberingSystem = getInstance(ULocale.getDefault(ULocale.Category.FORMAT))
        fun getInstance(locale: ULocale?): NumberingSystem {
            val actual = locale!!
            var keyword = actual.getKeywordValue("numbers")
            val resolved = keyword != null && keyword !in listOf("native", "traditional", "finance")
            if (resolved) {
                getInstanceByName(keyword)?.let { return it }
                keyword = "default"
            } else if (keyword == null) keyword = "default"
            return localeCache.getInstance(actual.getBaseName() + "@numbers=" + keyword, LocaleLookupData(actual, keyword!!))!!
        }
        fun getInstanceByName(name: String?): NumberingSystem? = nameCache.getInstance(name!!, null)
        fun getAvailableNames(): Array<String> {
            val systems = UResourceBundle.getBundleInstance(ICUData.ICU_BASE_NAME, "numberingSystems")["numberingSystems"]
            val iterator = systems.getIterator()
            val names = mutableListOf<String>()
            while (iterator.hasNext()) names.add(iterator.next().getKey()!!)
            return names.toTypedArray()
        }
        private fun lookupInstanceByName(name: String): NumberingSystem? {
            val description: String
            val radix: Int
            val algorithmic: Boolean
            try {
                val system = UResourceBundle.getBundleInstance(ICUData.ICU_BASE_NAME, "numberingSystems")["numberingSystems"][name]
                description = system.getString("desc")
                radix = system["radix"].getInt()
                algorithmic = system["algorithmic"].getInt() == 1
            } catch (_: MissingResourceException) { return null }
            return create(name, radix, algorithmic, description)
        }
        private class LocaleLookupData(val locale: ULocale, val keyword: String)
        private fun lookupInstanceByLocale(data: LocaleLookupData): NumberingSystem {
            val bundle = try {
                (UResourceBundle.getBundleInstance(ICUData.ICU_BASE_NAME, data.locale) as ICUResourceBundle).getWithFallback("NumberElements")
            } catch (_: MissingResourceException) { return NumberingSystem() }
            var keyword = data.keyword
            var resolved: String? = null
            while (true) {
                try { resolved = bundle.getStringWithFallback(keyword); break }
                catch (_: MissingResourceException) {
                    keyword = when (keyword) {
                        "native", "finance" -> "default"
                        "traditional" -> "native"
                        else -> break
                    }
                }
            }
            return resolved?.let { getInstanceByName(it) } ?: NumberingSystem()
        }
        private val localeCache = object : SoftCache<String, NumberingSystem, LocaleLookupData>() {
            override fun createInstance(key: String, data: LocaleLookupData): NumberingSystem = lookupInstanceByLocale(data)
        }
        private val nameCache = object : SoftCache<String, NumberingSystem, Unit?>() {
            override fun createInstance(key: String, data: Unit?): NumberingSystem? = lookupInstanceByName(key)
        }
    }
}

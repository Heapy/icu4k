// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.number.range

import io.github.heapy.icu4k.impl.ICUData
import io.github.heapy.icu4k.impl.ICUResourceBundle
import io.github.heapy.icu4k.impl.StandardPlural
import io.github.heapy.icu4k.impl.UResource
import io.github.heapy.icu4k.util.ULocale
import io.github.heapy.icu4k.util.UResourceBundle
import io.github.heapy.icu4k.util.UResourceTypeMismatchException

class StandardPluralRanges private constructor() {
    private var triples: Array<StandardPlural?> = emptyArray()
    private var count = 0

    fun resolve(first: StandardPlural?, second: StandardPlural?): StandardPlural {
        for (i in 0 until count) if (first == triples[3 * i] && second == triples[3 * i + 1]) return triples[3 * i + 2]!!
        return StandardPlural.OTHER
    }

    companion object {
        val DEFAULT = StandardPluralRanges()
        private val languageToSet: Map<String, String> by lazy {
            val output = HashMap<String, String>()
            val sink = object : UResource.Sink() {
                override fun put(key: UResource.Key, value: UResource.Value, noFallback: Boolean) {
                    val table = value.getTable()
                    var i = 0
                    while (table.getKeyAndValue(i++, key, value)) output[key.toString()] = value.toString()
                }
            }
            bundle().getAllItemsWithFallback("locales", sink)
            output
        }
        private fun bundle() = UResourceBundle.getBundleInstance(ICUData.ICU_BASE_NAME, "pluralRanges") as ICUResourceBundle
        fun getSetForLocale(locale: ULocale): String? = languageToSet[locale.getLanguage()]
        fun forLocale(locale: ULocale): StandardPluralRanges = forSet(getSetForLocale(locale))
        fun forSet(set: String?): StandardPluralRanges {
            if (set == null) return DEFAULT
            val result = StandardPluralRanges()
            val sink = object : UResource.Sink() {
                override fun put(key: UResource.Key, value: UResource.Value, noFallback: Boolean) {
                    val entries = value.getArray()
                    result.triples = arrayOfNulls(entries.getSize() * 3)
                    var i = 0
                    while (entries.getValue(i++, value)) {
                        val forms = value.getArray()
                        if (forms.getSize() != 3) throw UResourceTypeMismatchException("Expected 3 elements in pluralRanges.txt array")
                        forms.getValue(0, value); val first = StandardPlural.fromString(value.getString())
                        forms.getValue(1, value); val second = StandardPlural.fromString(value.getString())
                        forms.getValue(2, value); val resolved = StandardPlural.fromString(value.getString())
                        result.triples[3 * result.count] = first
                        result.triples[3 * result.count + 1] = second
                        result.triples[3 * result.count + 2] = resolved
                        result.count++
                    }
                }
            }
            bundle().getAllItemsWithFallback("rules/$set", sink)
            return result
        }
    }
}

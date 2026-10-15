// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.impl.number.PluralConditionParseException
import io.github.heapy.icu4k.impl.number.PluralRuleListCore
import io.github.heapy.icu4k.impl.number.range.StandardPluralRanges
import io.github.heapy.icu4k.util.MissingResourceException
import io.github.heapy.icu4k.util.ULocale

/** Locale loader backing the future public PluralRules factory. */
internal class PluralRulesLoaderCore private constructor() {
    enum class PluralType { CARDINAL, ORDINAL }
    class LoadedRules(val rules: PluralRuleListCore, val ranges: StandardPluralRanges)
    private class Maps(val cardinal: Map<String, String>, val ordinal: Map<String, String>, val equivalent: Map<String, ULocale>)
    private val cache = HashMap<String, LoadedRules?>()
    private val monitor = createTextTrieMapLock(cache)
    private val maps: Maps by lazy {
        try {
            val resource = getPluralBundle()
            val cardinal = linkedMapOf<String, String>()
            val ordinal = linkedMapOf<String, String>()
            val equivalent = HashMap<String, ULocale>()
            val locales = resource.get("locales")
            for (i in 0 until locales.getSize()) {
                val entry = locales.get(i)
                val id = entry.getKey()!!; val rule = entry.getString()
                cardinal[id] = rule
                if (!equivalent.containsKey(rule)) equivalent[rule] = ULocale(id)
            }
            val ordinals = resource.get("locales_ordinals")
            for (i in 0 until ordinals.getSize()) {
                val entry = ordinals.get(i)
                ordinal[entry.getKey()!!] = entry.getString()
            }
            Maps(cardinal, ordinal, equivalent)
        } catch (_: MissingResourceException) { Maps(emptyMap(), emptyMap(), emptyMap()) }
    }
    private fun idMap(type: PluralType): Map<String, String> = if (type == PluralType.CARDINAL) maps.cardinal else maps.ordinal
    fun getAvailableULocales(): Array<ULocale> = maps.cardinal.keys.sorted().mapTo(linkedSetOf()) { ULocale.createCanonical(it) }.toTypedArray()
    fun getFunctionalEquivalent(locale: ULocale, isAvailable: BooleanArray?): ULocale {
        if (isAvailable != null && isAvailable.isNotEmpty()) isAvailable[0] = maps.cardinal.containsKey(ULocale.canonicalize(locale.getBaseName()))
        val id = getRulesIdForLocale(locale, PluralType.CARDINAL)
        if (id == null || id.trim { it <= ' ' }.isEmpty()) return ULocale.ROOT
        return maps.equivalent[id] ?: ULocale.ROOT
    }
    fun getRulesIdForLocale(locale: ULocale, type: PluralType): String? {
        val ids = idMap(type)
        var id = ULocale.canonicalize(locale.getBaseName())
        while (true) {
            ids[id]?.let { return it }
            val end = id.lastIndexOf('_')
            if (end < 0) return null
            id = id.substring(0, end)
        }
    }
    fun getOrCreateRulesForLocale(locale: ULocale, type: PluralType): LoadedRules? {
        val id = getRulesIdForLocale(locale, type)
        if (id == null || id.trim { it <= ' ' }.isEmpty()) return null
        val rangesId = StandardPluralRanges.getSetForLocale(locale)
        val key = "$id/$rangesId"
        val existing = monitor.withLock { cache.containsKey(key) to cache[key] }
        if (existing.first) return existing.second
        val candidate = try {
            val set = getPluralBundle().get("rules").get(id)
            val text = (0 until set.getSize()).joinToString("; ") { index ->
                val entry = set.get(index)
                "${entry.getKey()}: ${entry.getString()}"
            }
            val ranges = StandardPluralRanges.forSet(rangesId)
            LoadedRules(PluralRuleListCore.parse(text), ranges)
        } catch (_: PluralConditionParseException) { null } catch (_: MissingResourceException) { null }
        return monitor.withLock {
            if (cache.containsKey(key)) cache[key] else candidate.also { cache[key] = it }
        }
    }
    fun getPluralBundle(): ICUResourceBundle = ICUResourceBundle.getBundleInstance(ICUData.ICU_BASE_NAME, "plurals", disableFallback = true)
    fun forLocale(locale: ULocale, type: PluralType): LoadedRules = getOrCreateRulesForLocale(locale, type) ?: DEFAULT
    fun hasOverride(locale: ULocale): Boolean = false

    companion object {
        val DEFAULT = LoadedRules(PluralRuleListCore.parse(""), StandardPluralRanges.DEFAULT)
        val loader = PluralRulesLoaderCore()
    }
}

// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.text.PluralRules
import io.github.heapy.icu4k.util.ULocale
import io.github.heapy.icu4k.util.UResourceBundle
import kotlin.jvm.JvmField

class PluralRulesLoader private constructor() : PluralRules.Factory() {
    private val core = PluralRulesLoaderCore.loader
    private val cache = HashMap<PluralRulesLoaderCore.LoadedRules, PluralRules>()
    private val monitor = createTextTrieMapLock(cache)
    private fun type(type: PluralRules.PluralType?): PluralRulesLoaderCore.PluralType =
        if (type == PluralRules.PluralType.CARDINAL) PluralRulesLoaderCore.PluralType.CARDINAL else PluralRulesLoaderCore.PluralType.ORDINAL
    private fun facade(loaded: PluralRulesLoaderCore.LoadedRules): PluralRules {
        if (loaded === PluralRulesLoaderCore.DEFAULT) return PluralRules.DEFAULT
        return monitor.withLock { cache.getOrPut(loaded) { PluralRules.fromLoaded(loaded.rules, loaded.ranges) } }
    }
    override fun forLocale(locale: ULocale?, type: PluralRules.PluralType?): PluralRules = facade(core.forLocale(locale!!, type(type)))
    override fun getAvailableULocales(): Array<ULocale> = core.getAvailableULocales()
    override fun getFunctionalEquivalent(locale: ULocale?, isAvailable: BooleanArray?): ULocale = core.getFunctionalEquivalent(locale!!, isAvailable)
    override fun hasOverride(locale: ULocale?): Boolean = false
    fun getRulesIdForLocale(locale: ULocale?, type: PluralRules.PluralType?): String? = core.getRulesIdForLocale(locale!!, type(type))
    fun getOrCreateRulesForLocale(locale: ULocale?, type: PluralRules.PluralType?): PluralRules? = core.getOrCreateRulesForLocale(locale!!, type(type))?.let(::facade)
    fun getPluralBundle(): UResourceBundle = core.getPluralBundle()
    companion object { @JvmField val loader = PluralRulesLoader() }
}

@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.impl

import com.ibm.icu.impl.PluralRulesLoader as JLoader
import com.ibm.icu.text.PluralRules as JRules
import com.ibm.icu.util.ULocale as JLocale
import io.github.heapy.icu4k.impl.number.FixedDecimalCore
import io.github.heapy.icu4k.util.ULocale
import java.util.IdentityHashMap
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.test.*

class PluralRulesLoaderCoreOracleTest {
    @Test fun availabilityFallbackEquivalentRulesAndCacheIdentityMatchSource() {
        val j = JLoader.loader; val k = PluralRulesLoaderCore.loader
        assertEquals(j.availableULocales.map { it.name }, k.getAvailableULocales().map { it.getName() })
        val sourceToPort = IdentityHashMap<JRules, PluralRulesLoaderCore.LoadedRules>()
        val portToSource = IdentityHashMap<PluralRulesLoaderCore.LoadedRules, JRules>()
        val ids = j.availableULocales.map { it.name } + listOf("", "root", "en_US", "en_GB", "en_US@numbers=arab", "pt_PT", "pt_BR", "ru_RU", "zh_Hant_TW", "zz_ZZ", "iw_IL", "mo", "sh_RS", "und", "en_US_POSIX", "fr_CA", "es_419")
        for (id in ids) {
            val jl = JLocale(id); val kl = ULocale(id)
            for (initial in listOf(null, booleanArrayOf(), booleanArrayOf(true, true), booleanArrayOf(false))) {
                val ja = initial?.copyOf(); val ka = initial?.copyOf()
                assertEquals(j.getFunctionalEquivalent(jl, ja).name, k.getFunctionalEquivalent(kl, ka).getName(), id)
                assertContentEquals(ja, ka, id)
            }
            for (type in JRules.PluralType.entries) {
                val kt = PluralRulesLoaderCore.PluralType.valueOf(type.name)
                assertEquals(j.getRulesIdForLocale(jl, type), k.getRulesIdForLocale(kl, kt), "$id/$type/id")
                assertEquals(j.getOrCreateRulesForLocale(jl, type) == null, k.getOrCreateRulesForLocale(kl, kt) == null, "$id/$type/null")
                val jr = j.forLocale(jl, type); val kr = k.forLocale(kl, kt)
                assertEquals(jr.toString(), kr.rules.toString(), "$id/$type/rules")
                sourceToPort[jr]?.let { assertSame(it, kr, "$id/$type/shared source") }
                portToSource[kr]?.let { assertSame(it, jr, "$id/$type/shared port") }
                sourceToPort[jr] = kr; portToSource[kr] = jr
                for (n in listOf(0.0, 1.0, 2.0, 3.0, 11.0, 21.0, 1.2, 1000000.0)) {
                    val number = FixedDecimalCore(n)
                    assertEquals(jr.select(n), kr.rules.select(number::getPluralOperand), "$id/$type/$n")
                }
            }
        }
    }
    @Test fun concurrentInitializationAndCachePublication() {
        val constructor = PluralRulesLoaderCore::class.java.getDeclaredConstructor().apply { isAccessible = true }
        val loader = constructor.newInstance()
        val executor = Executors.newFixedThreadPool(8)
        try {
            val results = executor.invokeAll((0 until 32).map {
                Callable { loader.forLocale(ULocale("en_US"), PluralRulesLoaderCore.PluralType.CARDINAL) }
            }).map { it.get() }
            for (result in results) assertSame(results.first(), result)
            assertEquals(JLoader.loader.forLocale(JLocale.US, JRules.PluralType.CARDINAL).toString(), results.first().rules.toString())
        } finally { executor.shutdownNow() }
    }
}

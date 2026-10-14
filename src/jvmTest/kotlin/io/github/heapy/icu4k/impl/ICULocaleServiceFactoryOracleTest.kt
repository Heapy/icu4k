package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.util.ULocale
import kotlin.test.*
import com.ibm.icu.impl.ICULocaleService as JService
import com.ibm.icu.impl.ICUService as JBase
import com.ibm.icu.util.ULocale as JLocale

class ICULocaleServiceFactoryOracleTest {
    @Test fun registryKindsVisibilityFallbackAndUnregistration() {
        val j = JService("factory"); val k = ICULocaleService("factory")
        val jf = ArrayList<JBase.Factory>(); val kf = ArrayList<ICUService.Factory>()
        for ((id, kind, visible) in listOf(Triple("", -1, true), Triple("en", -1, true), Triple("en_US@currency=usd", 2, true), Triple("en", 3, false), Triple("zh_Hant_TW", 4, true))) {
            val value = "$id/$kind/$visible"
            jf.add(j.registerObject(value, JLocale(id), kind, visible))
            kf.add(k.registerObject(value, ULocale(id), kind, visible))
            assertEquals(j.visibleIDs, k.getVisibleIDs())
            assertEquals(j.availableULocales.map { it.name }.toSet(), k.getAvailableULocales().map { it.getName() }.toSet())
            assertEquals(j.availableLocales.toSet(), k.getAvailableLocales().toSet())
            for (request in listOf("en", "en_US", "en_US_POSIX@calendar=japanese", "fr_FR", "zh_Hant_TW")) for (requestedKind in -1..4) {
                val ja = arrayOf<JLocale?>(null); val ka = arrayOf<ULocale?>(null)
                assertEquals(j.get(JLocale(request), requestedKind, ja), k.get(ULocale(request), requestedKind, ka), "$request/$requestedKind")
                assertEquals(ja[0]?.name, ka[0]?.getName())
            }
        }
        for (index in jf.indices.reversed()) {
            assertEquals(j.unregisterFactory(jf[index]), k.unregisterFactory(kf[index]))
            assertEquals(j.visibleIDs, k.getVisibleIDs())
        }
    }

    @Test fun localeKeyFactoryUsesCurrentLocaleAndVirtualSupportedSet() {
        val j = object : JService.LocaleKeyFactory(true, "names") {
            override fun getSupportedIDs(): Set<String> = setOf("en", "zh_Hant")
            override fun handleCreate(loc: JLocale?, kind: Int, service: JBase?): Any? = "${loc?.name}/$kind"
            override fun isSupportedID(id: String?): Boolean = false // create uses handlesKey, not this method
        }
        val k = object : ICULocaleService.LocaleKeyFactory(true, "names") {
            override fun getSupportedIDs(): Set<String> = setOf("en", "zh_Hant")
            override fun handleCreate(loc: ULocale?, kind: Int, service: ICUService?): Any? = "${loc?.getName()}/$kind"
            override fun isSupportedID(id: String?): Boolean = false
        }
        val js = JService(); js.registerFactory(j)
        val ks = ICULocaleService(); ks.registerFactory(k)
        for (id in listOf("en_US@currency=usd", "zh_Hant_TW", "xx")) {
            assertEquals(js.get(JLocale(id), 4), ks.get(ULocale(id), 4))
            for (display in listOf("en", "fr", null)) assertEquals(j.getDisplayName(id, display?.let(::JLocale)), k.getDisplayName(id, display?.let(::ULocale)))
        }
        // Simple factories reject ordinary keys; generic locale factories cast a supported key.
        val simple = ICULocaleService.SimpleLocaleKeyFactory("value", ULocale.ENGLISH, -1, true)
        assertNull(simple.create(ICUService.Key("en"), ks))
        assertFailsWith<ClassCastException> { k.create(ICUService.Key("en"), ks) }
        assertNull(k.create(null, ks))
    }

    @Test fun resourceFactorySeparatesSupportedAndVisibleIDs() {
        val j = JService.ICUResourceBundleFactory()
        val k = ICULocaleService.ICUResourceBundleFactory()
        val jm = HashMap<String, JBase.Factory>(); val km = HashMap<String, ICUService.Factory>()
        j.updateVisibleIDs(jm); k.updateVisibleIDs(km)
        assertEquals(jm.keys, km.keys)
        for (id in listOf("en", "en_US", "fr", "root", "zzzz")) {
            val jk = JService.LocaleKey.createWithCanonicalFallback(id, null, 2)
            val kk = ICULocaleService.LocaleKey.createWithCanonicalFallback(id, null, 2)
            val jb = j.create(jk, JService()) as com.ibm.icu.impl.ICUResourceBundle?
            val kb = k.create(kk, ICULocaleService()) as ICUResourceBundle?
            assertEquals(jb?.uLocale?.name, kb?.getULocale()?.getName(), id)
        }
    }
}

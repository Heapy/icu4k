package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.util.*
import kotlin.test.*

class LocaleAvailabilityTest {
    @Test fun installedLocaleIndexesKeepSeparateListsAndRawNames() {
        try {
            CacheValue.setStrength(CacheValue.Strength.STRONG)
            val base = "icu4k-test/availability-common"
            val files = mapOf("res_index" to BundleFixture.write(mapOf(
                "CLDRVersion" to "48", "InstalledLocales" to mapOf("en" to "", "fr" to "", "root" to ""),
                "AliasLocales" to mapOf("iw" to "", "mo" to ""))))
            val loader = BundleFixture.loader(files)
            val locales = ICUResourceBundle.getAvailableULocales(base, loader)!!
            assertEquals(listOf("en", "fr", ""), locales.map { it.getName() })
            assertEquals(listOf("iw", "mo"), ICUResourceBundle.getAvailableULocales(base, loader, ULocale.AvailableType.ONLY_LEGACY_ALIASES)!!.map { it.getName() })
            assertNull(ICUResourceBundle.getAvailableULocales(base, loader, ULocale.AvailableType.WITH_LEGACY_ALIASES))
            val names = ICUResourceBundle.getAvailableLocaleNameSet(base, loader)
            assertEquals(setOf("en", "fr", "root"), names)
            assertFailsWith<UnsupportedOperationException> { (names as MutableSet<String>).clear() }
            assertSame(locales, ICUResourceBundle.getAvailableULocales(base, ICUResourceLoader { error("cached loader") }))
            val saved = locales[0]; locales[0] = ULocale("zz")
            assertEquals("zz", ICUResourceBundle.getAvailableULocales(base, loader)!![0].getName()); locales[0] = saved
            assertTrue(ICUResourceBundle.getAvailableLocaleNameSet("icu4k-test/availability-missing", ICUResourceLoader { null }).isEmpty())
        } finally { CacheValue.setStrength(CacheValue.Strength.SOFT) }
    }
    @Test fun availabilityInitializationAllowsReentrantLoaderCallbacks() {
        val base = "icu4k-test/availability-reentry"
        val bytes = BundleFixture.write(mapOf("InstalledLocales" to mapOf("en" to "")))
        var loads = 0
        lateinit var loader: ICUResourceLoader
        loader = ICUResourceLoader {
            if (++loads == 1) assertEquals(setOf("en"), ICUResourceBundle.getAvailableLocaleNameSet(base, loader))
            BinaryBuffer(bytes.copyOf())
        }
        assertEquals(listOf("en"), ICUResourceBundle.getAvailableULocales(base, loader)!!.map { it.getName() })
        assertEquals(3, loads)
    }
    @Test fun keywordValuesExcludeDefaultsAndPrivateTypesAndSkipMalformedTables() {
        val base = "icu4k-test/keyword-values-common"
        val data = keywordFiles()
        val values = ICUResourceBundle.getKeywordValues(base, "collations", BundleFixture.loader(data))
        assertEquals(setOf("standard", "phonebook", "DEFAULT"), values.toSet())
        assertTrue(ICUResourceBundle.getKeywordValues(base, "unknown", BundleFixture.loader(data)).isEmpty())
        assertTrue(ICUResourceBundle.getKeywordValues(base, null, BundleFixture.loader(data)).isEmpty())
        assertTrue(ICUResourceBundle.getAvailableULocales()!!.isNotEmpty())
        assertTrue(ICUResourceBundle.getAvailableULocales(ICUData.ICU_BRKITR_BASE_NAME)!!.isNotEmpty())
    }
    companion object {
        fun keywordFiles(): Map<String, ByteArray> = mapOf(
            "root" to BundleFixture.write(mapOf("collations" to mapOf("default" to "standard", "standard" to "", "private-hidden" to "", "DEFAULT" to ""))),
            "en" to BundleFixture.write(mapOf("collations" to mapOf("phonebook" to ""))),
            "fr" to BundleFixture.write(mapOf("collations" to "wrong-type")),
            "res_index" to BundleFixture.write(mapOf("InstalledLocales" to mapOf("root" to "", "en" to "", "fr" to ""), "AliasLocales" to mapOf("iw" to "")))
        )
    }
}

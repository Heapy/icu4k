package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.util.*
import kotlin.test.*

class BundleFactoryTest {
    @Test fun concreteResourcesAliasesAndNestedFallbackUseOriginalPaths() {
        val loader = BundleFixture.loader(BundleFixture.data())
        val base = "icu4k-test/factory-common"
        val bundle = ICUResourceBundle.getBundleInstance(base, "en_US", loader, ICUResourceBundle.OpenType.LOCALE_ROOT)
        assertEquals("US", bundle.getString("target"))
        assertEquals("en", bundle.getParent()!!.getULocale().getName())
        assertEquals("inherited", bundle["inherit"].getString())
        assertEquals("US", bundle["dynamic"].getString())
        assertEquals("deep", bundle.getWithFallback("nested/inherited").getString())
        assertEquals("deep", bundle.getWithFallback("aliasGroup/inherited").getString())
        assertEquals("burmesedict.dict", bundle["absolute"].getString())
        assertContentEquals(arrayOf("a", "b"), bundle.getStringArray("strings"))
        val mixed = bundle.getObject("mixed") as UResourceBundle
        assertEquals("a", mixed.getString(0)); assertEquals(42, mixed[1].getInt()); assertEquals(42, mixed["١"].getInt())
        assertFailsWith<UResourceTypeMismatchException> { mixed.getStringArray() }
        assertFailsWith<IndexOutOfBoundsException> { mixed[-1] }
        assertFailsWith<IndexOutOfBoundsException> { mixed.getString(2) }
        assertFailsWith<NumberFormatException> { mixed["not-a-number"] }
        assertNull((mixed as ICUResourceBundle).at("0"))
        assertEquals(-1, bundle["integer"].getInt())
        assertContentEquals(intArrayOf(-1, 42), bundle["vector"].getIntVector())
        assertContentEquals(byteArrayOf(1, 2, 3), bundle["binary"].getBinary(null))
        assertNull(bundle.findWithFallback("missing/path"))
        assertFailsWith<MissingResourceException> { bundle.getWithFallback("marker") }
        assertEquals("∅∅∅", bundle.findWithFallback("marker")!!.getString())
        assertEquals("Circular references in the resource bundles",
            assertFailsWith<IllegalArgumentException> { bundle["cycleA"] }.message)
        assertNull(bundle.at("strings")) // direct lookup, no parent traversal
    }
    @Test fun openingModesExplicitParentsAndNoFallbackFlags() {
        val base = "icu4k-test/factory-modes"
        val loader = BundleFixture.loader(BundleFixture.data())
        val direct = ICUResourceBundle.getBundleInstance(base, "en_US", loader, true)
        assertNull(direct.getParent()); assertNull(direct.findWithFallback("strings"))
        assertFailsWith<MissingResourceException> { ICUResourceBundle.getBundleInstance(base, "en_AU", loader, true) }
        val parent = ICUResourceBundle.getBundleInstance(base, "en_GB", loader, ICUResourceBundle.OpenType.LOCALE_ROOT)
        assertEquals("en_US", parent.getParent()!!.getULocale().getName())
        val noFallback = ICUResourceBundle.getBundleInstance(base, "fr", loader, false)
        assertNull(noFallback.getParent())
        val defaultMode = ICUResourceBundle.getBundleInstance(base, "es_AR", loader, false)
        assertEquals("es_419", defaultMode.getULocale().getName())
        val rootMode = ICUResourceBundle.getBundleInstance(base, "zz_ZZ", loader, ICUResourceBundle.OpenType.LOCALE_ROOT)
        assertTrue(rootMode.isRoot())
        assertFailsWith<MissingResourceException> { ICUResourceBundle.getBundleInstance(base, "zz_ZZ", loader, ICUResourceBundle.OpenType.LOCALE_ONLY) }
        assertSame(direct, ICUResourceBundle.getBundleInstance(base, "en_US", ICUResourceLoader { error("cache hit") }, true))
    }
    @Test fun embeddedKeyTypeAliasesUseTheGeneralBundleResolver() {
        val data = ICUResourceBundle.getBundleInstance(ICUData.ICU_BASE_NAME, "keyTypeData", disableFallback = true)
        val timezones = data.getWithFallback("typeMap/timezone")
        assertEquals("uslax", timezones.getString("America:Los_Angeles"))
        assertEquals("timezonetypes", timezones.getULocale().getName())
    }

}

package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.util.*
import kotlin.test.*

class FunctionalEquivalentTest {
    @Test fun requestedKeywordsDefaultsAndAvailabilityKeepOriginalOwnership() {
        val base = "icu4k-test/function-common"
        val data = files()
        val loader = BundleFixture.loader(data)
        for (name in data.keys) ICUResourceBundle.getBundleInstance(base, name, loader, true)
        val flag = booleanArrayOf(false)
        fun equivalent(id: String, omit: Boolean = true): String = ICUResourceBundle.getFunctionalEquivalent(
            base, loader, "collations", "collation", ULocale(id), flag, omit).getName()
        assertEquals("en", equivalent("en_US")); assertTrue(flag[0])
        assertEquals("en@collation=phonebook", equivalent("en_US", false))
        assertEquals("en_US@collation=regional", equivalent("en_US@collation=regional"))
        assertEquals("en", equivalent("en_US@collation=unknown"))
        assertEquals("en", equivalent("en_AU@collation=phonebook")); assertFalse(flag[0])
        assertEquals("@collation=foreign", equivalent("root@collation=foreign")); assertTrue(flag[0])
        assertEquals("fr", equivalent("fr@collation=foreign")); assertTrue(flag[0])
        assertFailsWith<MissingResourceException> { equivalent("fr@collation=unknown") }
        assertFailsWith<IndexOutOfBoundsException> {
            ICUResourceBundle.getFunctionalEquivalent(base, loader, "collations", "collation", ULocale("en"), booleanArrayOf(), true)
        }
    }
    @Test fun missingDefaultsAndDefaultLoaderFailuresRetainTheirOriginalBoundary() {
        val base = "icu4k-test/function-no-default-common"
        val data = mapOf("root" to BundleFixture.write(mapOf("collations" to mapOf("standard" to "value"))))
        val loader = BundleFixture.loader(data)
        ICUResourceBundle.getBundleInstance(base, "root", loader, true)
        val locale = ULocale("root@collation=standard")
        assertEquals("@collation=standard", ICUResourceBundle.getFunctionalEquivalent(base, loader, "collations", "collation", locale, null, false).getName())
        assertFailsWith<NullPointerException> { ICUResourceBundle.getFunctionalEquivalent(base, loader, "collations", "collation", locale, null, true) }
        var loads = 0
        val flag = booleanArrayOf(true)
        assertFailsWith<MissingResourceException> {
            ICUResourceBundle.getFunctionalEquivalent("icu4k-test/function-unprimed", ICUResourceLoader { loads++; BinaryBuffer(data["root"]!!) },
                "collations", "collation", locale, flag, false)
        }
        assertEquals(0, loads); assertTrue(flag[0])
    }
    companion object {
        fun files(): Map<String, ByteArray> = mapOf(
            "root" to BundleFixture.write(mapOf("collations" to mapOf("default" to "standard", "standard" to "root-standard", "foreign" to BundleFixture.Alias("en/collations/phonebook")))),
            "en" to BundleFixture.write(mapOf("collations" to mapOf("default" to "phonebook", "phonebook" to "en-phonebook"))),
            "en_US" to BundleFixture.write(mapOf("collations" to mapOf("regional" to "US-regional"))),
            "en_Latn" to BundleFixture.write(mapOf("collations" to BundleFixture.Alias("root/collations"))),
            "fr" to BundleFixture.write(mapOf("collations" to mapOf("default" to "foreign", "foreign" to BundleFixture.Alias("root/collations/standard")))),
            "de" to BundleFixture.write(mapOf("collations" to mapOf("standard" to "de-standard"))),
            "res_index" to BundleFixture.write(mapOf("InstalledLocales" to mapOf("root" to "", "en" to "", "en_US" to "", "de" to "", "fr" to ""), "AliasLocales" to mapOf("en_Latn" to "")))
        )
    }
}

package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.util.*
import java.io.InputStream
import java.util.Locale
import kotlin.test.*

class FunctionalEquivalentOracleTest {
    private class Loader(private val files: Map<String, ByteArray>) : ClassLoader(com.ibm.icu.impl.ICUResourceBundle::class.java.classLoader) {
        override fun getResourceAsStream(name: String): InputStream? = files[name]?.inputStream() ?: super.getResourceAsStream(name)
    }
    @Test fun functionalEquivalentResultsFailuresAndAvailabilityMatchOriginal() {
        val base = "icu4k-test/function-oracle"
        val data = FunctionalEquivalentTest.files()
        val jl = Loader(data.mapKeys { "$base/${it.key}.res" }); val kl = BundleFixture.loader(data)
        for (id in data.keys) {
            com.ibm.icu.impl.ICUResourceBundle.getBundleInstance(base, id, jl, true)
            ICUResourceBundle.getBundleInstance(base, id, kl, true)
        }
        val saved = Locale.getDefault(); Locale.setDefault(Locale.US)
        try {
            for (name in listOf("root", "en", "en_US", "en_AU", "en_Latn", "de", "fr", "zz_ZZ"))
                for (keyword in listOf(null, "default", "standard", "phonebook", "regional", "foreign", "unknown", "STANDARD"))
                    for (resName in listOf("collations", "missing")) for (omit in listOf(false, true)) {
                        val id = name + (keyword?.let { "@collation=$it" } ?: "")
                        val jf = booleanArrayOf(true); val kf = booleanArrayOf(true)
                        fun outcome(block: () -> String): Any = try { block() } catch (ex: RuntimeException) {
                            if (ex is java.util.MissingResourceException) listOf(ex.javaClass.simpleName, ex.message, ex.className, ex.key)
                            else if (ex is MissingResourceException) listOf(ex.javaClass.simpleName, ex.message, ex.getClassName(), ex.getKey())
                            else ex.javaClass.simpleName
                        }
                        val j = outcome { com.ibm.icu.impl.ICUResourceBundle.getFunctionalEquivalent(base, jl, resName, "collation", com.ibm.icu.util.ULocale(id), jf, omit).name }
                        val k = outcome { ICUResourceBundle.getFunctionalEquivalent(base, kl, resName, "collation", ULocale(id), kf, omit).getName() }
                        assertEquals(j, k, "$id/$resName/$omit"); assertContentEquals(jf, kf, id)
                    }
        } finally { Locale.setDefault(saved) }
    }
    @Test fun absentDefaultAndEmptyOutputArrayMatchOriginalExceptionCategories() {
        val base = "icu4k-test/function-no-default-oracle"
        val data = mapOf("root" to BundleFixture.write(mapOf("collations" to mapOf("standard" to "value"))))
        val jl = Loader(data.mapKeys { "$base/${it.key}.res" }); val kl = BundleFixture.loader(data)
        com.ibm.icu.impl.ICUResourceBundle.getBundleInstance(base, "root", jl, true)
        ICUResourceBundle.getBundleInstance(base, "root", kl, true)
        val j = com.ibm.icu.util.ULocale("root@collation=standard"); val k = ULocale("root@collation=standard")
        assertEquals(com.ibm.icu.impl.ICUResourceBundle.getFunctionalEquivalent(base, jl, "collations", "collation", j, null, false).name,
            ICUResourceBundle.getFunctionalEquivalent(base, kl, "collations", "collation", k, null, false).getName())
        assertFailsWith<NullPointerException> { com.ibm.icu.impl.ICUResourceBundle.getFunctionalEquivalent(base, jl, "collations", "collation", j, null, true) }
        assertFailsWith<NullPointerException> { ICUResourceBundle.getFunctionalEquivalent(base, kl, "collations", "collation", k, null, true) }
        assertFailsWith<IndexOutOfBoundsException> { com.ibm.icu.impl.ICUResourceBundle.getFunctionalEquivalent(base, jl, "collations", "collation", j, booleanArrayOf(), false) }
        assertFailsWith<IndexOutOfBoundsException> { ICUResourceBundle.getFunctionalEquivalent(base, kl, "collations", "collation", k, booleanArrayOf(), false) }
    }

}

package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.util.*
import java.io.InputStream
import java.util.Locale
import kotlin.test.*
import com.ibm.icu.impl.ICUResourceBundle as Original
import com.ibm.icu.util.ULocale as OriginalLocale

class JavaLocaleAvailabilityOracleTest {
    private class Loader(private val base: String, private val bytes: ByteArray) : ClassLoader(Original::class.java.classLoader) {
        override fun getResourceAsStream(name: String): InputStream? =
            if (name == "$base/res_index.res") bytes.inputStream() else null
    }
    @Test fun conversionPreservesScriptsAndDeduplicatesInOrder() {
        val names = listOf("en_US", "sr_Latn_RS", "en_US", "no_NO_NY", "en_US_POSIX", "en_US@calendar=japanese")
        val expected = Original.getLocaleList(names.map(::OriginalLocale).toTypedArray())
        val actual = ICUResourceBundle.getLocaleList(names.map(::ULocale).toTypedArray())
        assertContentEquals(expected, actual)
        assertTrue(actual.any { it.script == "Latn" })
        assertFailsWith<NullPointerException> { ICUResourceBundle.getLocaleList(null) }
        assertFailsWith<NullPointerException> { ICUResourceBundle.getLocaleList(arrayOf(null)) }
    }
    @Test fun firstCategoryWinsAndReturnedArrayIsShared() {
        for ((index, first) in listOf(ULocale.AvailableType.DEFAULT, ULocale.AvailableType.ONLY_LEGACY_ALIASES).withIndex()) {
            val base = "icu4k-test/java-availability/$index"
            val bytes = BundleFixture.write(mapOf("InstalledLocales" to mapOf("en" to "", "sr_Latn" to ""),
                "AliasLocales" to mapOf("iw" to "")))
            val j = Loader(base, bytes)
            val k = Loader(base, bytes)
            val expected = Original.getAvailableLocales(base, j, OriginalLocale.AvailableType.valueOf(first.name))
            val actual = ICUResourceBundle.getAvailableLocales(base, k, first)
            assertContentEquals(expected, actual)
            for (type in ULocale.AvailableType.entries) {
                assertSame(expected, Original.getAvailableLocales(base, j, OriginalLocale.AvailableType.valueOf(type.name)))
                assertSame(actual, ICUResourceBundle.getAvailableLocales(base, k, type))
            }
            assertSame(actual, ICUResourceBundle.getAvailableLocales(base, k, null))
            assertSame(expected, Original.getAvailableLocales(base, null))
            assertSame(actual, ICUResourceBundle.getAvailableLocales(base, null))
            actual[0] = Locale.CANADA
            assertEquals(Locale.CANADA, ICUResourceBundle.getAvailableLocales(base, k)[0])
        }
    }
    @Test fun nullLoaderColdIndexAndNullBaseMatchOriginal() {
        assertFailsWith<NullPointerException> { Original.getAvailableLocales("icu4k-test/java-availability/null-cold", null) }
        assertFailsWith<NullPointerException> { ICUResourceBundle.getAvailableLocales("icu4k-test/java-availability/null-cold", null) }
        assertFailsWith<NullPointerException> { Original.getAvailableLocales(null, null) }
        assertFailsWith<NullPointerException> { ICUResourceBundle.getAvailableLocales(null, null) }
    }
    @Test fun absentCategoryThrowsUntilValidCategoryPopulatesCache() {
        val base = "icu4k-test/java-availability/absent"
        val bytes = BundleFixture.write(mapOf("InstalledLocales" to mapOf("root" to "")))
        val j = Loader(base, bytes)
        val k = Loader(base, bytes)
        assertFailsWith<NullPointerException> { Original.getAvailableLocales(base, j, OriginalLocale.AvailableType.ONLY_LEGACY_ALIASES) }
        assertFailsWith<NullPointerException> { ICUResourceBundle.getAvailableLocales(base, k, ULocale.AvailableType.ONLY_LEGACY_ALIASES) }
        assertFailsWith<NullPointerException> { Original.getAvailableLocales(base, j, null) }
        assertFailsWith<NullPointerException> { ICUResourceBundle.getAvailableLocales(base, k, null) }
        assertContentEquals(Original.getAvailableLocales(base, j), ICUResourceBundle.getAvailableLocales(base, k))
        assertContentEquals(Original.getAvailableLocales(), ICUResourceBundle.getAvailableLocales())
    }
}

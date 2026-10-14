package io.github.heapy.icu4k.util

import java.util.Locale
import java.net.URLClassLoader
import kotlin.test.*
import com.ibm.icu.util.ULocale as Original

class ULocaleDefaultsOracleTest {
    @Test fun coldInitializationWithExtensionsMatchesOriginal() {
        val general = Locale.getDefault()
        val display = Locale.getDefault(Locale.Category.DISPLAY)
        val format = Locale.getDefault(Locale.Category.FORMAT)
        try {
            for (tag in listOf("ja-JP-u-ca-japanese", "de-u-foo-bar-co-phonebk")) {
                Locale.setDefault(Locale.forLanguageTag(tag))
                URLClassLoader(arrayOf(ULocale::class.java.protectionDomain.codeSource.location,
                    Original::class.java.protectionDomain.codeSource.location,
                    Unit::class.java.protectionDomain.codeSource.location), null).use { loader ->
                    val original = loader.loadClass("com.ibm.icu.util.ULocale")
                    val expected = original.getMethod("getDefault").invoke(null).toString()
                    val common = loader.loadClass("io.github.heapy.icu4k.util.ULocale")
                    val companion = common.getField("Companion").get(null)
                    val actual = companion.javaClass.getMethod("getDefault").invoke(companion).toString()
                    assertEquals(expected, actual, tag)
                }
            }
        } finally {
            Locale.setDefault(general)
            Locale.setDefault(Locale.Category.DISPLAY, display)
            Locale.setDefault(Locale.Category.FORMAT, format)
        }
    }

    @Test fun javaConversionMatchesOriginal() {
        @Suppress("DEPRECATION")
        val locales = listOf(Locale.ROOT, Locale("no", "NO", "NY"), Locale("ja", "JP", "JP"),
            Locale("th", "TH", "TH"), Locale("en", "US", "weird_variant")) +
            listOf("sr-Latn-RS", "en-US-u-ca-japanese-kn", "en-US-u-va-posix", "de-u-foo-bar-co-phonebk-x-test").map(Locale::forLanguageTag)
        assertNull(ULocale.forLocale(null))
        for (locale in locales) {
            val actual = ULocale.forLocale(locale)!!
            val expected = Original.forLocale(locale)
            assertEquals(expected.name, actual.getName(), locale.toString())
            assertEquals(locale, actual.toLocale())
            assertSame(actual.toLocale(), actual.toLocale())
            assertEquals(expected.toLocale(), actual.toLocale())
        }
        for (id in listOf("en_US", "no_NO_NY", "en_US_WEIRD_VARIANT", "sr_Latn_RS", "en_US_POSIX", "ja_JP@calendar=japanese", "de@attribute=foo-bar;collation=phonebook", "en@x=foo")) {
            assertEquals(Original(id).toLocale(), ULocale(id).toLocale(), id)
        }
    }

    @Test fun hostChangesAndSettersMatchOriginal() {
        val general = Locale.getDefault()
        val display = Locale.getDefault(Locale.Category.DISPLAY)
        val format = Locale.getDefault(Locale.Category.FORMAT)
        try {
            for (tag in listOf("fr-FR", "sr-Latn-RS", "ja-JP-u-ca-japanese", "en-US-u-va-posix")) {
                Locale.setDefault(Locale.forLanguageTag(tag))
                assertEquals(Original.getDefault().name, ULocale.getDefault().getName())
                for (category in ULocale.Category.entries) {
                    assertEquals(Original.getDefault(Original.Category.valueOf(category.name)).name, ULocale.getDefault(category).getName())
                }
            }
            for (category in ULocale.Category.entries) {
                val javaCategory = Locale.Category.valueOf(category.name)
                Locale.setDefault(javaCategory, Locale.GERMANY)
                assertEquals(Original.getDefault(Original.Category.valueOf(category.name)).name, ULocale.getDefault(category).getName())
                val desired = ULocale("en_US@calendar=japanese")
                ULocale.setDefault(category, desired)
                assertSame(desired, ULocale.getDefault(category))
                assertEquals(Original(desired.getName()).toLocale(), Locale.getDefault(javaCategory))
            }
            val desired = ULocale("fr_CA")
            ULocale.setDefault(desired)
            assertSame(desired, ULocale.getDefault())
            assertEquals(Original(desired.getName()).toLocale(), Locale.getDefault())
            for (category in Locale.Category.entries) assertEquals(Locale.getDefault(), Locale.getDefault(category))
        } finally {
            Locale.setDefault(general)
            Locale.setDefault(Locale.Category.DISPLAY, display)
            Locale.setDefault(Locale.Category.FORMAT, format)
        }
    }
}

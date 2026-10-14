package io.github.heapy.icu4k.util

import io.github.heapy.icu4k.impl.*
import java.io.InputStream
import java.util.Locale
import kotlin.test.*
import com.ibm.icu.util.UResourceBundle as Original

class UResourceBundleJvmOracleTest {
    private class Loader(private val files: Map<String, ByteArray>) : ClassLoader(Original::class.java.classLoader) {
        val requests = mutableListOf<String>()
        override fun getResourceAsStream(name: String): InputStream? {
            requests.add(name)
            return files[name]?.inputStream()
        }
    }
    @Test fun javaLocaleAndClassLoaderFactoriesMatchOriginal() {
        val saved = Locale.getDefault()
        val display = Locale.getDefault(Locale.Category.DISPLAY)
        val format = Locale.getDefault(Locale.Category.FORMAT)
        try {
            Locale.setDefault(Locale.FRANCE)
            @Suppress("DEPRECATION")
            val locales = listOf(null, Locale.ROOT, Locale.US, Locale.FRANCE, Locale("no", "NO", "NY"),
                Locale.forLanguageTag("en-US-u-ca-japanese"), Locale.forLanguageTag("en-US-u-va-posix"))
            var index = 0
            for (binary in listOf(false, true)) for (locale in locales) for (family in 0..2) {
                val base = if (binary) "icu4k-test/jvm-factory/${index++}" else "icu4k.test.jvmFactory${index++}"
                val files = if (binary) listOf("root", "en", "fr", "nn").associate { id ->
                    "$base/$id.res" to BundleFixture.write(mapOf("word" to id))
                } else listOf("", "en", "fr", "nn").associate { id ->
                    base.replace('.', '/') + (if (id.isEmpty()) "" else "_$id") + ".properties" to "word=$id".toByteArray()
                }
                val j = Loader(files)
                val k = Loader(files)
                val expected = when (family) {
                    0 -> Original.getBundleInstance(base, locale, j)
                    1 -> Original.getBundleInstance(base, com.ibm.icu.util.ULocale.forLocale(locale), j)
                    else -> Original.getBundleInstance(base, (com.ibm.icu.util.ULocale.forLocale(locale) ?: com.ibm.icu.util.ULocale.getDefault()).baseName, j)
                }
                val actual = when (family) {
                    0 -> UResourceBundle.getBundleInstance(base, locale, k)
                    1 -> UResourceBundle.getBundleInstance(base, ULocale.forLocale(locale), k)
                    else -> UResourceBundle.getBundleInstance(base, (ULocale.forLocale(locale) ?: ULocale.getDefault()).getBaseName(), k)
                }
                assertEquals(expected.getString("word"), actual.getString("word"), "$base/$locale/$family")
                assertEquals(expected.locale, actual.getLocale())
                assertEquals(expected.uLocale.name, actual.getULocale().getName())
                assertEquals(j.requests, k.requests)
            }
            assertEquals(Original.getBundleInstance(null, Locale.ROOT).locale,
                UResourceBundle.getBundleInstance(null, Locale.ROOT).getLocale())
        } finally {
            Locale.setDefault(saved)
            Locale.setDefault(Locale.Category.DISPLAY, display)
            Locale.setDefault(Locale.Category.FORMAT, format)
        }
    }
    @Test fun nullClassLoaderRetainsOriginalColdFailure() {
        assertFailsWith<NullPointerException> { Original.getBundleInstance("icu4k.test.jvmNullFactory", Locale.ROOT, null) }
        assertFailsWith<NullPointerException> { UResourceBundle.getBundleInstance("icu4k.test.jvmNullFactory", Locale.ROOT, null as ClassLoader?) }
    }
}

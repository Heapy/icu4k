package io.github.heapy.icu4k.util

import kotlin.test.*
import io.github.heapy.icu4k.impl.ICUResourceLoader

class ULocaleDefaultsTest {
    @Test fun resourceFactoriesUseMutableDefault() {
        val saved = ULocale.getDefault()
        val display = ULocale.getDefault(ULocale.Category.DISPLAY)
        val format = ULocale.getDefault(ULocale.Category.FORMAT)
        val base = "defaults.Mutable"
        val loader = ICUResourceLoader { name ->
            val text = when (name) {
                "defaults/Mutable.properties" -> "word=root"
                "defaults/Mutable_en.properties" -> "word=English"
                "defaults/Mutable_fr.properties" -> "word=French"
                else -> null
            }
            text?.let { BinaryBuffer(it.encodeToByteArray()) }
        }
        try {
            for ((locale, word) in listOf(ULocale.US to "English", ULocale.FRANCE to "French")) {
                ULocale.setDefault(locale)
                val bundle = UResourceBundle.getBundleInstance(base, null as ULocale?, loader)
                assertEquals(word, bundle.getObject("word"))
                val fallback = UResourceBundle.getBundleInstance(base, "zz", loader)
                assertEquals(word, fallback.getObject("word"))
            }
        } finally {
            ULocale.setDefault(saved)
            ULocale.setDefault(ULocale.Category.DISPLAY, display)
            ULocale.setDefault(ULocale.Category.FORMAT, format)
        }
    }

    @Test fun generalAndCategoryDefaultsAreIndependent() {
        val general = ULocale.getDefault()
        val display = ULocale.getDefault(ULocale.Category.DISPLAY)
        val format = ULocale.getDefault(ULocale.Category.FORMAT)
        try {
            val locale = ULocale("en_US@calendar=japanese")
            ULocale.setDefault(locale)
            assertSame(locale, ULocale.getDefault())
            for (category in ULocale.Category.entries) assertSame(locale, ULocale.getDefault(category))
            ULocale.setDefault(ULocale.Category.DISPLAY, ULocale.FRANCE)
            assertEquals(ULocale.FRANCE, ULocale.getDefault(ULocale.Category.DISPLAY))
            assertSame(locale, ULocale.getDefault())
            assertSame(locale, ULocale.getDefault(ULocale.Category.FORMAT))
            assertFailsWith<NullPointerException> { ULocale.setDefault(null) }
            assertFailsWith<NullPointerException> { ULocale.getDefault(null) }
            assertFailsWith<NullPointerException> { ULocale.setDefault(null, locale) }
        } finally {
            ULocale.setDefault(general)
            ULocale.setDefault(ULocale.Category.DISPLAY, display)
            ULocale.setDefault(ULocale.Category.FORMAT, format)
        }
    }
}

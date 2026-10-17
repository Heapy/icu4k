package io.github.heapy.icu4k.text

import com.ibm.icu.impl.LocaleUtility
import com.ibm.icu.util.ULocale as JavaLocale
import kotlin.test.*

class TransliteratorRegistrySpecOracleTest {
    private val type = Class.forName("com.ibm.icu.text.TransliteratorRegistry\$Spec")
    private val constructor = type.getDeclaredConstructor(String::class.java).apply { isAccessible = true }
    private fun invoke(instance: Any, name: String): Any? = type.getDeclaredMethod(name).apply { isAccessible = true }.invoke(instance)

    @Test fun legacyLocaleConversionMatchesJava() {
        val names = mutableSetOf("", "ja_JP_JP", "th_TH_TH", "no_NO_NY", "iw_IL", "in_ID", "ji", "en-Latn-US", "EN_us_abc_def", "_US", "__POSIX", "en@calendar=japanese", "en_foo", "zh_Hant_TW", "Ä_ü_x")
        names.addAll(JavaLocale.getAvailableLocales().map { it.toString() })
        for (name in names) assertEquals(JavaLocale.forLocale(LocaleUtility.getLocaleFromName(name)).toString(), transliterationLocaleFromName(name).toString(), name)
    }

    @Test fun fallbackCursorMatchesIcu() {
        val names = listOf("", "Any", "Latin", "Latn", "Greek", "el", "el_GR", "el_GR_POSIX", "en_US_POSIX", "ja", "ja_Latn", "ko", "zh_Hant_TW", "zh_Hans", "Old_Hungarian", "zzz", "_US", "en@calendar=japanese")
        for (name in names) {
            val java = constructor.newInstance(name)
            val kotlin = TransliteratorRegistrySpec(name)
            fun compare() {
                assertEquals(invoke(java,"get"), kotlin.get(), name)
                assertEquals(invoke(java,"getTop"), kotlin.getTop(), name)
                assertEquals(invoke(java,"hasFallback"), kotlin.hasFallback(), name)
                assertEquals(invoke(java,"isLocale"), kotlin.isLocale(), name)
                val resource = invoke(java,"getBundle") as com.ibm.icu.util.UResourceBundle?
                assertEquals(resource?.getULocale()?.toString(), kotlin.getBundle()?.getULocale()?.toString(), name)
            }
            repeat(2) {
                compare()
                while (kotlin.hasFallback()) { assertEquals(invoke(java,"next"), kotlin.next(), name); compare() }
                // The source allows next() beyond the end; retain its script revival quirk.
                assertEquals(invoke(java,"next"), kotlin.next(), name); compare()
                invoke(java,"reset"); kotlin.reset()
            }
        }
    }
}

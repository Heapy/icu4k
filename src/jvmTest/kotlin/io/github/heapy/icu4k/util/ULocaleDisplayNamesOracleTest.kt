package io.github.heapy.icu4k.util

import java.lang.reflect.InvocationTargetException
import kotlin.test.Test
import kotlin.test.assertEquals
import com.ibm.icu.util.ULocale as JLocale

class ULocaleDisplayNamesOracleTest {
    private fun call(type: Class<*>, receiver: Any?, name: String, parameters: Array<Class<*>>, vararg arguments: Any?): Any? = try {
        type.getMethod(name, *parameters).invoke(receiver, *arguments)
    } catch (e: InvocationTargetException) {
        "throws:${e.targetException.javaClass.simpleName}"
    }

    @Test fun instanceAndStaticOverloadsMatchSource() {
        val names = listOf("Language", "LanguageWithDialect", "Script", "ScriptInContext", "Country", "Variant", "Name", "NameWithDialect")
        val ids = listOf("", "root", "en_US", "zh_Hant_TW", "sr_Latn_RS", "de_DE_1901", "en_US@calendar=gregorian;currency=usd", "xx_Qaaa_ZZ_UNKNOWN")
        for (id in ids) for (display in listOf("en", "fr", "ar", "ja", "de", "root", null)) {
            val j = JLocale(id); val k = ULocale(id)
            val jd = display?.let(::JLocale); val kd = display?.let(::ULocale)
            for (suffix in names) {
                val name = "getDisplay$suffix"
                assertEquals(call(JLocale::class.java, j, name, arrayOf(JLocale::class.java), jd), call(ULocale::class.java, k, name, arrayOf(ULocale::class.java), kd), "$id/$display/$name")
                assertEquals(call(JLocale::class.java, null, name, arrayOf(String::class.java, JLocale::class.java), id, jd), call(ULocale.Companion::class.java, ULocale.Companion, name, arrayOf(String::class.java, ULocale::class.java), id, kd), "static $id/$display/$name")
                assertEquals(call(JLocale::class.java, null, name, arrayOf(String::class.java, String::class.java), id, display), call(ULocale.Companion::class.java, ULocale.Companion, name, arrayOf(String::class.java, String::class.java), id, display), "string $id/$display/$name")
            }
            for (key in listOf("calendar", "currency", " CURRENCY\u0000", "\u00a0currency\u00a0", "missing", "", null)) {
                assertEquals(call(JLocale::class.java, j, "getDisplayKeywordValue", arrayOf(String::class.java, JLocale::class.java), key, jd), call(ULocale::class.java, k, "getDisplayKeywordValue", arrayOf(String::class.java, ULocale::class.java), key, kd), "$id/$display/value:$key")
                assertEquals(call(JLocale::class.java, null, "getDisplayKeyword", arrayOf(String::class.java, JLocale::class.java), key, jd), call(ULocale.Companion::class.java, ULocale.Companion, "getDisplayKeyword", arrayOf(String::class.java, ULocale::class.java), key, kd), "$display/key:$key")
            }
        }
    }

    @Test fun implicitDisplayCategoryMatchesSource() {
        val jd = JLocale.getDefault(JLocale.Category.DISPLAY)
        val kd = ULocale.getDefault(ULocale.Category.DISPLAY)
        try {
            JLocale.setDefault(JLocale.Category.DISPLAY, JLocale.FRENCH)
            ULocale.setDefault(ULocale.Category.DISPLAY, ULocale.FRENCH)
            for (suffix in listOf("Language", "LanguageWithDialect", "Script", "ScriptInContext", "Country", "Variant", "Name", "NameWithDialect")) {
                assertEquals(call(JLocale::class.java, JLocale("en_Latn_US"), "getDisplay$suffix", emptyArray()), call(ULocale::class.java, ULocale("en_Latn_US"), "getDisplay$suffix", emptyArray()))
            }
            assertEquals(JLocale.getDisplayKeyword("calendar"), ULocale.getDisplayKeyword("calendar"))
            assertEquals(JLocale("en@currency=usd").getDisplayKeywordValue("currency"), ULocale("en@currency=usd").getDisplayKeywordValue("currency"))
        } finally {
            JLocale.setDefault(JLocale.Category.DISPLAY, jd)
            ULocale.setDefault(ULocale.Category.DISPLAY, kd)
        }
    }
}

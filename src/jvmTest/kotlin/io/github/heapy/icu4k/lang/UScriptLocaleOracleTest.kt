package io.github.heapy.icu4k.lang

import com.ibm.icu.lang.UScript as JavaScript
import com.ibm.icu.util.ULocale as JavaLocale
import io.github.heapy.icu4k.util.ULocale
import kotlin.test.*

class UScriptLocaleOracleTest {
    @Test fun namesAndLocalesMatchIcu() {
        val names = mutableSetOf("", "und", "zzz", "ja", "ja_Latn", "ko", "ko_Latn", "zh", "zh_Hant", "zh_Hans", "zh_TW", "en_US", "sr_ME", "az_IR", "Old_Hungarian", "Old_Italic", "New_Tai_Lue", "old-Hungarian", "new-Tai-Lue", "en@calendar=japanese", "_US", "xx_Qaaa")
        for (code in 0 until UScript.CODE_LIMIT) {
            UScript.getName(code)?.let(names::add)
            UScript.getShortName(code)?.let(names::add)
        }
        for (locale in JavaLocale.getAvailableLocales()) names.add(locale.toString())
        for (name in names) {
            assertContentEquals(JavaScript.getCode(name), UScript.getCode(name), "name $name")
            assertContentEquals(JavaScript.getCode(JavaLocale(name)), UScript.getCode(ULocale(name)), "locale $name")
        }
        for (locale in java.util.Locale.getAvailableLocales()) {
            assertContentEquals(JavaScript.getCode(locale), UScript.getCode(locale), "JVM $locale")
        }
    }
}

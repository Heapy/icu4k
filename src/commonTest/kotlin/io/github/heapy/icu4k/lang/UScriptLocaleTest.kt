package io.github.heapy.icu4k.lang

import io.github.heapy.icu4k.util.ULocale
import kotlin.test.*

class UScriptLocaleTest {
    @Test fun localeScriptsAndAliases() {
        val cases = listOf(
            "ja_Latn" to intArrayOf(22, 20, 17),
            "ko_Latn" to intArrayOf(18, 17),
            "zh_Hant" to intArrayOf(17, 5),
            "zh_Hans" to intArrayOf(17),
            "en_US" to intArrayOf(25),
            "Old_Hungarian" to intArrayOf(76),
            "Old_Italic" to intArrayOf(30),
            "New_Tai_Lue" to intArrayOf(59),
        )
        for ((name, expected) in cases) assertContentEquals(expected, UScript.getCode(name), name)
        assertContentEquals(intArrayOf(17, 5), UScript.getCode(ULocale("zh_TW")))
        assertNull(UScript.getCode("zzz"))
        val first = UScript.getCode("ja")!!
        first[0] = -1
        assertContentEquals(intArrayOf(22, 20, 17), UScript.getCode("ja"))
    }
}

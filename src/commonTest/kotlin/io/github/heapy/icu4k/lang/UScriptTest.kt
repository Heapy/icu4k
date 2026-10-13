package io.github.heapy.icu4k.lang

import kotlin.test.*

class UScriptTest {
    @Test fun scriptsExtensionsAndNames() {
        assertEquals(UScript.LATIN, UScript.getScript('A'.code))
        assertEquals(UScript.COMMON, UScript.getScript(0x30fc))
        assertTrue(UScript.hasScript(0x30fc, UScript.HIRAGANA))
        assertTrue(UScript.hasScript(0x30fc, UScript.KATAKANA))
        val scripts = mutableSetOf(9999)
        assertEquals(-2, UScript.getScriptExtensions(0x30fc, scripts))
        assertEquals(setOf(UScript.HIRAGANA, UScript.KATAKANA), scripts)
        assertEquals(UScript.UNKNOWN, UScript.getScriptExtensions(-1, scripts))
        assertEquals(setOf(UScript.UNKNOWN), scripts)
        assertEquals("Latn", UScript.getShortName(UScript.LATIN))
        assertEquals("Latin", UScript.getName(UScript.LATIN))
        assertEquals(UScript.LATIN, UScript.getCodeFromName("latn"))
        assertFailsWith<IllegalArgumentException> { UScript.getScript(-1) }
    }
}

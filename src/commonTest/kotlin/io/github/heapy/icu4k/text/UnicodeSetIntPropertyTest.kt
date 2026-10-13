package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.lang.UProperty
import io.github.heapy.icu4k.lang.UScript
import kotlin.test.*

class UnicodeSetIntPropertyTest {
    @Test fun propertiesReplaceContentsAndComplementOnlyCodePoints() {
        val set = UnicodeSet().add("old")
        assertSame(set, set.applyIntPropertyValue(UProperty.ALPHABETIC, 1))
        assertTrue(set.contains(65))
        assertFalse(set.contains("old"))
        set.applyIntPropertyValue(UProperty.CANONICAL_COMBINING_CLASS, 230)
        assertTrue(set.contains(0x301))
        assertFalse(set.contains(65))
        set.applyIntPropertyValue(UProperty.SCRIPT_EXTENSIONS, UScript.LATIN)
        assertTrue(set.contains(65))
        set.applyIntPropertyValue(UProperty.GENERAL_CATEGORY_MASK, 1 shl 1)
        assertTrue(set.contains(65))
        assertFalse(set.contains(97))
        set.applyIntPropertyValue(UProperty.RGI_EMOJI, 1)
        assertTrue(set.contains("👩‍💻"))
        set.applyIntPropertyValue(UProperty.RGI_EMOJI, 0)
        assertTrue(set.contains(65))
        assertTrue(set.strings().isEmpty())
        set.applyIntPropertyValue(UProperty.ALPHABETIC, 2)
        assertTrue(set.isEmpty())
        set.freeze()
        assertFailsWith<UnsupportedOperationException> { set.applyIntPropertyValue(UProperty.ALPHABETIC, 1) }
        assertFailsWith<IllegalArgumentException> { set.applyIntPropertyValue(-1, 1) }
    }
}

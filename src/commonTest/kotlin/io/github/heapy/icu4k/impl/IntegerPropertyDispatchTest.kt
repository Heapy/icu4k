package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.lang.UCharacter
import io.github.heapy.icu4k.lang.UProperty
import io.github.heapy.icu4k.lang.UScript
import kotlin.test.*

class IntegerPropertyDispatchTest {
    @Test fun dispatchAcrossPropertySourcesAndUndefinedMaxima() {
        val p = UCharacterProperty.INSTANCE
        assertEquals(UScript.LATIN, p.getEnumeratedIntPropertyValue(65, UProperty.SCRIPT))
        assertEquals(230, p.getEnumeratedIntPropertyValue(0x301, UProperty.CANONICAL_COMBINING_CLASS))
        assertEquals(1, p.getEnumeratedIntPropertyValue(65, UProperty.GENERAL_CATEGORY))
        assertEquals(2, p.getEnumeratedIntPropertyValue(0x301, UProperty.NFC_QUICK_CHECK))
        for (which in UProperty.INT_START until UProperty.INT_LIMIT) assertTrue(UCharacter.getIntPropertyMaxValue(which) >= 0)
        assertEquals(1, UCharacter.getIntPropertyMaxValue(UProperty.ALPHABETIC))
        assertEquals(-1, UCharacter.getIntPropertyMaxValue(UProperty.GENERAL_CATEGORY_MASK))
        assertEquals(-1, UCharacter.getIntPropertyMaxValue(-1))
        assertEquals(0, UCharacter.getIntPropertyMinValue(Int.MIN_VALUE))
    }
}

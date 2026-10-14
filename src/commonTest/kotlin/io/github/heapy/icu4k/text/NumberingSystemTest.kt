package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.util.ULocale
import kotlin.test.*

class NumberingSystemTest {
    @Test fun numericValidationCountsCodePointsRatherThanDigitProperties() {
        assertTrue(NumberingSystem.isValidDigitString("abcdefghij"))
        val system = NumberingSystem.getInstance(10, false, "aaaaaaaaaa")
        assertNull(system.getName()); assertEquals("aaaaaaaaaa", system.getDescription())
        assertFailsWith<IllegalArgumentException> { NumberingSystem.getInstance(2, false, "01") }
        assertEquals(2, NumberingSystem.getInstance(2, true, null).getRadix())
        assertNull(NumberingSystem.getInstance(2, true, null).getDescription())
        assertFailsWith<NullPointerException> { NumberingSystem.isValidDigitString(null) }
        assertFailsWith<IllegalArgumentException> { NumberingSystem.getInstance(1, true, null) }
    }
    @Test fun resourceNamesAndLocaleKeywords() {
        val names = NumberingSystem.getAvailableNames()
        assertTrue("latn" in names); assertTrue("roman" in names)
        assertEquals(names.sorted(), names.toList())
        assertEquals("0123456789", NumberingSystem.LATIN!!.getDescription())
        assertTrue(NumberingSystem.getInstanceByName("roman")!!.isAlgorithmic())
        assertEquals("arab", NumberingSystem.getInstance(ULocale("en@numbers=arab")).getName())
        assertEquals("latn", NumberingSystem.getInstance(ULocale("en@numbers=bogus")).getName())
        assertEquals("latn", NumberingSystem.getInstance(ULocale("en@numbers=traditional")).getName())
        assertNull(NumberingSystem.getInstanceByName("bogus"))
        val math = NumberingSystem.getInstanceByName("mathbold")!!
        assertTrue(NumberingSystem.isValidDigitString(math.getDescription()))
        assertEquals(20, math.getDescription()!!.length)
    }
}

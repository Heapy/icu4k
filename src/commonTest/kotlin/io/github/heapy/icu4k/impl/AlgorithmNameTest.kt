package io.github.heapy.icu4k.impl

import kotlin.test.*

class AlgorithmNameTest {
    @Test fun hexadecimalAndFactorizedNames() {
        val hex = UCharacterName.AlgorithmName()
        assertTrue(hex.setInfo(0x4e00, 0x9fff, 0, 4))
        assertTrue(hex.setPrefix("CJK UNIFIED IDEOGRAPH-"))
        val out = StringBuilder("!")
        hex.appendName(0x4e00, out)
        assertEquals("!CJK UNIFIED IDEOGRAPH-4E00", out.toString())
        assertEquals(0x4e00, hex.getChar("CJK UNIFIED IDEOGRAPH-4E00"))
        assertEquals(-1, hex.getChar("CJK UNIFIED IDEOGRAPH-4DFF"))
        assertEquals(0x4e00, hex.getChar("CJK UNIFIED IDEOGRAPH-４Ｅ００"))
        assertEquals(0x4e00, hex.getChar("CJK UNIFIED IDEOGRAPH-٤E٠٠"))
        assertFailsWith<IndexOutOfBoundsException> { Utility.hex(1, 17) }
        val factors = UCharacterName.AlgorithmName()
        assertTrue(factors.setInfo(100, 105, 1, 2))
        assertTrue(factors.setPrefix("NAME "))
        assertTrue(factors.setFactor(charArrayOf(2.toChar(), 3.toChar())))
        factors.setFactorString("A\u0000B\u0000X\u0000Y\u0000Z\u0000".encodeToByteArray())
        val name = StringBuilder()
        factors.appendName(104, name)
        assertEquals("NAME BY", name.toString())
        assertEquals(104, factors.getChar("NAME BY"))
        assertEquals(-1, factors.getChar("NAME BY!"))
        assertFalse(factors.setInfo(-1, 0, 0, 1))
        assertFalse(factors.setFactor(charArrayOf(1.toChar())))
        assertFalse(factors.setPrefix(""))
        assertEquals("-000A", Utility.hex(-10))
        assertEquals("-8000000000000000", Utility.hex(Long.MIN_VALUE))
    }
}

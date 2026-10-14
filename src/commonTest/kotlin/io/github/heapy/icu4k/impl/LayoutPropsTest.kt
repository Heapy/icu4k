package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.util.MissingResourceException
import kotlin.test.*

class LayoutPropsTest {
    @Test fun loadsPinnedUnicodeLayoutProperties() {
        val props = LayoutProps.INSTANCE
        assertEquals("17.0.0.0", props.unicodeVersion.toString())
        assertEquals(7, props.inpcTrie!![0x093e]) // Devanagari vowel sign AA: Right.
        assertEquals(5, props.inscTrie!![0x0915]) // Devanagari KA: Consonant.
        assertEquals(31, props.inscTrie[0x094d]) // Devanagari virama.
        assertEquals(0, props.voTrie!!['A'.code]) // Latin letter: Rotated.
        assertEquals(3, props.voTrie[0x4e00]) // CJK ideograph: Upright.
        assertEquals(15, props.maxInpcValue)
        assertEquals(36, props.maxInscValue)
        assertEquals(3, props.maxVoValue)
    }

    @Test fun resourceCopiesCannotCorruptCachedData() {
        assertTrue(ICUData.exists("/${ICUData.ICU_BASE_NAME}/ulayout.icu"))
        val first = ICUBinary.getRequiredData("ulayout.icu")
        first.bytes.fill(0)
        val second = ICUBinary.getRequiredData("ulayout.icu")
        assertEquals(0xda.toByte(), second.getByte(2))
        assertNull(ICUBinary.getData("missing.icu"))
        val missing = assertFailsWith<MissingResourceException> { ICUBinary.getRequiredData("missing.icu") }
        assertEquals("could not locate data", missing.message)
        assertEquals("${ICUData.ICU_BASE_NAME}/missing.icu", missing.getKey())
        assertEquals(7, LayoutProps(second).inpcTrie!![0x093e])
    }
}

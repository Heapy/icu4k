package io.github.heapy.icu4k.lang

import io.github.heapy.icu4k.impl.CharacterPropertiesImpl
import kotlin.test.*

class CharacterPropertiesTest {
    @Test fun frozenSetsImmutableMapsAndInclusionReset() {
        val alphabetic = CharacterProperties.getBinaryPropertySet(UProperty.ALPHABETIC)
        assertTrue(alphabetic.isFrozen())
        assertTrue(alphabetic.contains(65))
        assertFalse(alphabetic.contains(49))
        assertSame(alphabetic, CharacterProperties.getBinaryPropertySet(UProperty.ALPHABETIC))
        assertFailsWith<UnsupportedOperationException> { alphabetic.add(49) }
        val script = CharacterProperties.getIntPropertyMap(UProperty.SCRIPT)
        assertEquals(UScript.LATIN, script[65])
        assertEquals(UScript.UNKNOWN, script[-1])
        assertSame(script, CharacterProperties.getIntPropertyMap(UProperty.SCRIPT))
        val emoji = CharacterProperties.getBinaryPropertySet(UProperty.RGI_EMOJI)
        assertTrue(emoji.contains("👩‍💻"))
        val before = CharacterPropertiesImpl.getInclusionsForProperty(UProperty.ALPHABETIC)
        CharacterPropertiesImpl.clear()
        val after = CharacterPropertiesImpl.getInclusionsForProperty(UProperty.ALPHABETIC)
        assertNotSame(before, after)
        assertEquals(before, after)
        assertFailsWith<IllegalArgumentException> { CharacterProperties.getIntPropertyMap(UProperty.ALPHABETIC) }
        assertFailsWith<IllegalArgumentException> { CharacterProperties.getBinaryPropertySet(UProperty.SCRIPT) }
    }
}

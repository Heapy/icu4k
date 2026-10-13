package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.lang.UProperty
import kotlin.test.*

class AuxiliaryBinaryPropertiesTest {
    @Test fun readsCaseBidiAndFixedRangeProperties() {
        val props = UCharacterProperty.INSTANCE
        assertTrue(props.hasAuxiliaryBinaryProperty('A'.code, UProperty.UPPERCASE))
        assertTrue(props.hasAuxiliaryBinaryProperty('('.code, UProperty.BIDI_MIRRORED))
        assertTrue(props.hasAuxiliaryBinaryProperty(0x1f1e6, UProperty.REGIONAL_INDICATOR))
        assertFalse(props.hasAuxiliaryBinaryProperty(0x1f200, UProperty.REGIONAL_INDICATOR))
        assertTrue(props.hasAuxiliaryBinaryProperty(0x2202, UProperty.ID_COMPAT_MATH_START))
        assertTrue(props.hasAuxiliaryBinaryProperty(0xb2, UProperty.ID_COMPAT_MATH_CONTINUE))
        assertFalse(props.hasAuxiliaryBinaryProperty(0xb2, UProperty.ID_COMPAT_MATH_START))
        assertTrue(props.hasAuxiliaryBinaryProperty(0x654, UProperty.MODIFIER_COMBINING_MARK))
    }
}

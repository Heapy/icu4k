package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.lang.UProperty
import kotlin.test.*

class VectorBinaryPropertiesTest {
    @Test fun readsUnicodePropertyBitsIncludingHighBit() {
        val props = UCharacterProperty.INSTANCE
        assertTrue(props.hasVectorBinaryProperty('A'.code, UProperty.ALPHABETIC))
        assertFalse(props.hasVectorBinaryProperty('!'.code, UProperty.ALPHABETIC))
        assertTrue(props.hasVectorBinaryProperty(0x600, UProperty.PREPENDED_CONCATENATION_MARK))
        assertTrue(props.hasVectorBinaryProperty(0x200e, UProperty.PATTERN_WHITE_SPACE))
        assertFalse(props.hasVectorBinaryProperty(-1, UProperty.WHITE_SPACE))
        assertFailsWith<IllegalArgumentException> { props.hasVectorBinaryProperty(65, UProperty.UPPERCASE) }
    }
}

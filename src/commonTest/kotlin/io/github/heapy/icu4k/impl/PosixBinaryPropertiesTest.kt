package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.lang.UProperty
import kotlin.test.*

class PosixBinaryPropertiesTest {
    @Test fun unicodePosixClassesRetainTheirDifferences() {
        val props = UCharacterProperty.INSTANCE
        assertTrue(props.hasPosixBinaryProperty(9, UProperty.POSIX_BLANK))
        assertFalse(props.hasPosixBinaryProperty(9, UProperty.POSIX_PRINT))
        assertTrue(props.hasPosixBinaryProperty(0xa0, UProperty.POSIX_PRINT))
        assertFalse(props.hasPosixBinaryProperty(0xa0, UProperty.POSIX_GRAPH))
        assertTrue(props.hasPosixBinaryProperty(0x200d, UProperty.POSIX_GRAPH))
        assertFalse(props.hasPosixBinaryProperty(0xd800, UProperty.POSIX_GRAPH))
        assertTrue(props.hasPosixBinaryProperty(0x661, UProperty.POSIX_XDIGIT))
        assertTrue(props.hasPosixBinaryProperty(0xff26, UProperty.POSIX_XDIGIT))
        assertFalse(props.hasPosixBinaryProperty('G'.code, UProperty.POSIX_XDIGIT))
    }
}

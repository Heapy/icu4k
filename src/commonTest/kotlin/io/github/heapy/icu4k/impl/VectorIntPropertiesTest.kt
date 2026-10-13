package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.lang.UProperty
import kotlin.test.*

class VectorIntPropertiesTest {
    @Test fun readsStoredPropertyFieldsAndRejectsOtherSources() {
        val props = UCharacterProperty.INSTANCE
        assertEquals(5, props.getVectorIntPropertyValue(0x4e00, UProperty.EAST_ASIAN_WIDTH))
        assertEquals(0, props.getVectorIntPropertyValue('A'.code, UProperty.DECOMPOSITION_TYPE))
        assertTrue(props.getVectorIntPropertyMaxValue(UProperty.LINE_BREAK) > 0)
        assertFailsWith<IllegalArgumentException> { props.getVectorIntPropertyValue(65, UProperty.CANONICAL_COMBINING_CLASS) }
    }
}

package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.lang.UProperty
import kotlin.test.*

class TrieIntPropertiesTest {
    @Test fun dispatchesBidiBlockAndLayoutData() {
        val props = UCharacterProperty.INSTANCE
        assertEquals(1, props.getTrieIntPropertyValue('('.code, UProperty.BIDI_PAIRED_BRACKET_TYPE))
        assertEquals(2, props.getTrieIntPropertyValue(')'.code, UProperty.BIDI_PAIRED_BRACKET_TYPE))
        assertEquals(1, props.getTrieIntPropertyValue('A'.code, UProperty.BLOCK))
        assertEquals(0, props.getTrieIntPropertyValue(-1, UProperty.BLOCK))
        assertTrue(props.getTrieIntPropertyMaxValue(UProperty.INDIC_SYLLABIC_CATEGORY) > 0)
        assertFailsWith<IllegalArgumentException> { props.getTrieIntPropertyValue(65, UProperty.SCRIPT) }
    }
}

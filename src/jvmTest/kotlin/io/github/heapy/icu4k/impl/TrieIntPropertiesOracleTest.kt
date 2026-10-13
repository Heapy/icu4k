package io.github.heapy.icu4k.impl

import com.ibm.icu.lang.UCharacter as JavaCharacter
import io.github.heapy.icu4k.lang.UProperty
import kotlin.test.*

class TrieIntPropertiesOracleTest {
    @Test fun trieEntriesMatchForEveryUnicodeCodePoint() {
        val properties = listOf(UProperty.BIDI_CLASS, UProperty.JOINING_GROUP, UProperty.JOINING_TYPE, UProperty.BIDI_PAIRED_BRACKET_TYPE, UProperty.BLOCK, UProperty.INDIC_POSITIONAL_CATEGORY, UProperty.INDIC_SYLLABIC_CATEGORY, UProperty.VERTICAL_ORIENTATION)
        val props = UCharacterProperty.INSTANCE
        for (which in properties) {
            assertEquals(JavaCharacter.getIntPropertyMaxValue(which), props.getTrieIntPropertyMaxValue(which))
            for (cp in -1..0x110000) assertEquals(JavaCharacter.getIntPropertyValue(cp, which), props.getTrieIntPropertyValue(cp, which), "$which $cp")
            assertEquals(JavaCharacter.getIntPropertyValue(Int.MAX_VALUE, which), props.getTrieIntPropertyValue(Int.MAX_VALUE, which))
        }
    }
}

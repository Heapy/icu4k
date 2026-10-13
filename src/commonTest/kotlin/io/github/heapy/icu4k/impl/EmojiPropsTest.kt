package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.lang.UProperty
import io.github.heapy.icu4k.text.UnicodeSet
import kotlin.test.*

class EmojiPropsTest {
    @Test fun codePointsAndSequencesUsePinnedData() {
        val props = EmojiProps.INSTANCE
        assertTrue(props.hasBinaryProperty(0x1f600, UProperty.EMOJI))
        assertFalse(props.hasBinaryProperty('A'.code, UProperty.EMOJI))
        assertTrue(props.hasBinaryProperty("1️⃣", UProperty.EMOJI_KEYCAP_SEQUENCE))
        assertTrue(props.hasBinaryProperty("👩‍💻", UProperty.RGI_EMOJI))
        assertFalse(props.hasBinaryProperty("", UProperty.RGI_EMOJI))
        val strings = UnicodeSet()
        props.addStrings(UProperty.RGI_EMOJI_ZWJ_SEQUENCE, strings)
        assertTrue(strings.contains("👩‍💻"))
        assertTrue(props.addPropertyStarts(UnicodeSet()).contains(0))
    }
}

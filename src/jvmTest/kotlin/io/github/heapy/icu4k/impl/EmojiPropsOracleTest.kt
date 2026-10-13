package io.github.heapy.icu4k.impl

import com.ibm.icu.impl.EmojiProps as JavaEmoji
import com.ibm.icu.text.UnicodeSet as JavaSet
import io.github.heapy.icu4k.lang.UProperty
import io.github.heapy.icu4k.text.UnicodeSet
import kotlin.test.*

class EmojiPropsOracleTest {
    @Test fun codePointsStringsAndStartsMatchOriginal() {
        val j = JavaEmoji.INSTANCE; val k = EmojiProps.INSTANCE
        for (which in UProperty.EMOJI - 1..UProperty.RGI_EMOJI + 1) {
            for (cp in -1..0x110000) assertEquals(j.hasBinaryProperty(cp, which), k.hasBinaryProperty(cp, which), "$which $cp")
            val js = JavaSet(); val ks = UnicodeSet()
            j.addStrings(which, js); k.addStrings(which, ks)
            assertEquals(js.toPattern(true), ks.toPattern(true))
            for (s in js.strings()) {
                assertEquals(j.hasBinaryProperty(s, which), k.hasBinaryProperty(s, which))
                assertEquals(j.hasBinaryProperty(s + "x", which), k.hasBinaryProperty(s + "x", which))
            }
        }
        val js = j.addPropertyStarts(JavaSet()); val ks = k.addPropertyStarts(UnicodeSet())
        assertEquals(js.toPattern(true), ks.toPattern(true))
    }
}

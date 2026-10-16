package io.github.heapy.icu4k.impl.timezone

import io.github.heapy.icu4k.text.ParsePosition
import io.github.heapy.icu4k.util.*
import kotlin.test.*

class LocalizedGMTOffsetCoreTest {
    @Test fun localizedFormattingAndUnicodeDigits() {
        val core = LocalizedGMTOffsetCore(ULocale("en"))
        assertEquals("GMT+05:30:45",core.formatOffsetLocalizedGMT(19_845_987))
        assertEquals("GMT-5",core.formatOffsetShortLocalizedGMT(-18_000_000))
        core.setGMTOffsetDigits("𝟎𝟏𝟐𝟑𝟒𝟓𝟔𝟕𝟖𝟗")
        val text = core.formatOffsetLocalizedGMT(19_845_000)
        assertEquals("GMT+𝟎𝟓:𝟑𝟎:𝟒𝟓",text)
        val pos = ParsePosition(0)
        assertEquals(19_845_000,core.parseOffsetLocalizedGMT(text,pos))
        assertEquals(text.length,pos.index)
        assertEquals(-19_845_000,core.parseOffsetLocalizedGMT("UTC-٠٥:٣٠:٤٥",ParsePosition(0)))
    }
    @Test fun patternQuotesAbuttingAndCloneSharing() {
        val core = LocalizedGMTOffsetCore(ULocale("en"))
        core.setGMTPattern("'X'{0}'Y'")
        core.setGMTOffsetPattern(LocalizedGMTOffsetCore.GMTOffsetPatternType.POSITIVE_HMS,"+Hmmss")
        val pos = ParsePosition(0)
        assertEquals(620_000,core.parseOffsetLocalizedGMT("X+01020Y",pos))
        assertEquals(8,pos.index)
        val copy = core.freeze().cloneAsThawed()
        copy.setGMTOffsetPattern(LocalizedGMTOffsetCore.GMTOffsetPatternType.POSITIVE_HMS,"+H:mm:ss")
        assertEquals("X+00:10:20Y",core.formatOffsetLocalizedGMT(620_000))
        assertTrue(core.isFrozen())
        assertFalse(copy.isFrozen())
        assertFailsWith<UnsupportedOperationException> { core.setGMTOffsetDigits(null) }
    }
    @Test fun malformedPrefixAndOutputMutation() {
        val core = LocalizedGMTOffsetCore(ULocale("en"))
        val pos = ParsePosition(0).also { it.errorIndex = 17 }
        val digits = Output(true)
        assertEquals(0,core.parseOffsetLocalizedGMT("GMT+bad",pos,false,digits))
        assertEquals(4,pos.index)
        assertEquals(17,pos.errorIndex)
        assertEquals(true,digits.value)
        assertEquals(0,core.parseOffsetLocalizedGMT("UTC",ParsePosition(0),false,digits))
        assertEquals(false,digits.value)
        assertFailsWith<NullPointerException> { core.parseOffsetLocalizedGMT(null,null,false,digits) }
        assertEquals(false,digits.value)
    }
    @Test fun supplementaryCaseAndBidiPatternWhitespace() {
        val core = LocalizedGMTOffsetCore(ULocale("en"))
        core.setGMTPattern("𐐀{0}")
        assertEquals(18_000_000,core.parseOffsetLocalizedGMT("𐐨+05:00",ParsePosition(0)))
        core.setGMTPattern("{0}")
        core.setGMTOffsetPattern(LocalizedGMTOffsetCore.GMTOffsetPatternType.POSITIVE_HM,"\u200e+HH:mm")
        assertEquals(19_800_000,core.parseOffsetLocalizedGMT("+05:30",ParsePosition(0)))
    }
}

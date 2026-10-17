package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.timezone.TimeZoneNamePatternCore
import io.github.heapy.icu4k.util.*
import kotlin.test.*

class TimeZoneFormatTest {
    @Test fun namesOffsetsAndTimeTypes() {
        val format = TimeZoneFormat.getInstance(ULocale.US)
        val zone = TimeZone.getTimeZone("America/New_York")
        val type = Output<TimeZoneFormat.TimeType>()
        assertEquals("Eastern Standard Time", format.format(TimeZoneFormat.Style.SPECIFIC_LONG, zone, 0, type))
        assertEquals(TimeZoneFormat.TimeType.STANDARD, type.value)
        assertEquals("Eastern Time", format.format(TimeZoneFormat.Style.GENERIC_LONG, zone, 0))
        assertEquals("New York Time", format.format(TimeZoneFormat.Style.GENERIC_LOCATION, zone, 0))
        assertEquals("-05:00", format.format(TimeZoneFormat.Style.ISO_EXTENDED_FIXED, zone, 0))
        assertEquals("GMT-05:00", format.formatOffsetLocalizedGMT(-18_000_000))
    }
    @Test fun prefixParsingAndErrors() {
        val format = TimeZoneFormat.getInstance(ULocale.US)
        val pos = ParsePosition(2)
        val type = Output<TimeZoneFormat.TimeType>()
        assertEquals("America/New_York", format.parse(TimeZoneFormat.Style.SPECIFIC_LONG, "xxEastern Standard Time suffix", pos, type)!!.getID())
        assertEquals(23, pos.index); assertEquals(-1, pos.errorIndex)
        assertEquals(TimeZoneFormat.TimeType.STANDARD, type.value)
        assertEquals(19_800_000, format.parse("GMT+05:30")!!.getRawOffset())
        assertFailsWith<ParseException> { format.parse("not-a-zone") }
    }
    @Test fun thawCloneAndSourceArraySharing() {
        val original = TimeZoneFormat.getInstance(ULocale("en_US_TZFCLONE"))
        assertTrue(original.isFrozen())
        assertFailsWith<UnsupportedOperationException> { original.setGMTPattern("UTC{0}") }
        val copy = original.cloneAsThawed()
        assertFalse(copy.isFrozen()); assertNotSame(original, copy)
        copy.setGMTPattern("UTC{0}")
        assertNotEquals(original.getGMTPattern(), copy.getGMTPattern())
        copy.setGMTOffsetPattern(TimeZoneFormat.GMTOffsetPatternType.POSITIVE_HM, "+HH.mm")
        assertEquals("+HH.mm", original.getGMTOffsetPattern(TimeZoneFormat.GMTOffsetPatternType.POSITIVE_HM))
        assertNotSame(original, original.clone()); assertTrue(original.clone().isFrozen())
        original.setDefaultParseOptions(setOf(TimeZoneFormat.ParseOption.ALL_STYLES))
        assertEquals(setOf(TimeZoneFormat.ParseOption.ALL_STYLES), original.getDefaultParseOptions()!!.toSet())
    }
    @Test fun portablePatternAndFieldAttribution() {
        assertEquals("o'clock {0} [zone]", TimeZoneNamePatternCore("o''clock '{0}' [{0}]").format(arrayOf("zone")))
        val format = TimeZoneFormat.getInstance(ULocale.US)
        val zone = SimpleTimeZone(19_800_000, "custom")
        val pos = FormatFieldPosition(DateFormat.Field.TIME_ZONE)
        assertEquals("prefixGMT+05:30", format.format(zone, StringBuilder("prefix"), pos).toString())
        assertEquals(0, pos.getBeginIndex()); assertEquals(9, pos.getEndIndex())
        val it = format.formatToCharacterIterator(zone)
        assertEquals(setOf<FormatAttribute>(DateFormat.Field.TIME_ZONE), it.getAllAttributeKeys())
        assertSame(DateFormat.Field.TIME_ZONE, it.getAttribute(DateFormat.Field.TIME_ZONE))
    }
}

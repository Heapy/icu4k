@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.util

import kotlin.test.*

class TimeZoneFactoryTest {
    @Test fun frozenPrototypesAndMutableCopiesHaveIndependentOwners() {
        val oldType = TimeZone.getDefaultTimeZoneType()
        try {
            TimeZone.setDefaultTimeZoneType(TimeZone.TIMEZONE_ICU)
            for (id in listOf("America/New_York", "US/Eastern", "GMT+5:30", "GMT-001530", "unrecognized")) {
                val frozen = TimeZone.getFrozenTimeZone(id)
                assertTrue(frozen.isFrozen()); assertSame(frozen, TimeZone.getFrozenTimeZone(id))
                val first = TimeZone.getTimeZone(id); val second = TimeZone.getTimeZone(id)
                assertFalse(first.isFrozen()); assertNotSame(first, second)
                val originalID = second.getID(); first.setID("changed")
                assertEquals(originalID, second.getID()); assertEquals(originalID, frozen.getID())
            }
        } finally { TimeZone.setDefaultTimeZoneType(oldType) }
    }
    @Test fun unknownAndGmtConstantsRetainSourceConstantZoneBehavior() {
        for (zone in listOf(TimeZone.UNKNOWN_ZONE, TimeZone.GMT_ZONE)) {
            assertTrue(zone.isFrozen()); assertSame(zone, zone.clone())
            assertEquals(0, zone.getOffset(-99, -1, 999, -1, 88, -1))
            assertFalse(zone.inDaylightTime(null))
            assertFailsWith<UnsupportedOperationException> { zone.setRawOffset(1) }
            val copy = zone.cloneAsThawed(); copy.setRawOffset(123000)
            assertEquals(123000, copy.getOffset(0)); assertEquals(0, zone.getRawOffset())
        }
        assertEquals(TimeZone.UNKNOWN_ZONE_ID, TimeZone.getTimeZone("definitely-not-a-zone", TimeZone.TIMEZONE_ICU).getID())
    }
    @Test fun defaultDefensiveCopiesAndLocaleKeywordSelection() {
        val original = TimeZone.getDefault(); val originalType = TimeZone.getDefaultTimeZoneType()
        try {
            TimeZone.setDefaultTimeZoneType(TimeZone.TIMEZONE_ICU)
            val input = SimpleTimeZone(123000, "private-default")
            TimeZone.setICUDefault(input); input.setRawOffset(456000)
            val first = TimeZone.getDefault(); val second = TimeZone.getDefault()
            assertEquals(123000, first.getRawOffset()); assertNotSame(first, second)
            first.setRawOffset(789000); assertEquals(123000, second.getRawOffset())
            assertEquals("private-default", TimeZone.forULocaleOrDefault(ULocale.US).getID())
            assertEquals("Europe/Minsk", TimeZone.forULocaleOrDefault(ULocale("en@timezone=Europe/Minsk")).getID())
            assertFailsWith<IllegalArgumentException> { TimeZone.setDefaultTimeZoneType(99) }
        } finally { TimeZone.setICUDefault(original); TimeZone.setDefaultTimeZoneType(originalType) }
    }
    @Test fun hostDefaultLookupProvidesARealIdentifier() {
        val default = TimeZone.getDefault()
        assertFalse(default.getID().isNullOrEmpty()); assertFalse(default.isFrozen())
        val offsets = IntArray(2); default.getOffset(0, false, offsets)
        assertEquals(offsets[0] + offsets[1], default.getOffset(0))
    }
}

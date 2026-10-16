package io.github.heapy.icu4k.impl.timezone

import io.github.heapy.icu4k.impl.ICUData
import io.github.heapy.icu4k.util.ULocale
import io.github.heapy.icu4k.util.UResourceBundle
import kotlin.test.*

class TimeZoneMetadataCoreTest {
    @Test fun canonicalAndIanaPoliciesDistinguishSystemCustomAndUnknownIds() {
        val flags = booleanArrayOf(true,true)
        assertEquals("Etc/Unknown",TimeZoneMetadataCore.getCanonicalID("Etc/Unknown",flags))
        assertContentEquals(booleanArrayOf(false,true),flags)
        assertEquals("America/New_York",TimeZoneMetadataCore.getCanonicalID("US/Eastern",flags))
        assertContentEquals(booleanArrayOf(true,true),flags)
        assertEquals("GMT+05:30",TimeZoneMetadataCore.getCanonicalID("GMT+530",flags))
        assertContentEquals(booleanArrayOf(false,true),flags)
        assertNull(TimeZoneMetadataCore.getCanonicalID(null,flags))
        assertEquals(false,flags[0])
        assertFailsWith<IndexOutOfBoundsException> { TimeZoneMetadataCore.getCanonicalID("US/Eastern",booleanArrayOf()) }
        assertEquals("Asia/Kolkata",TimeZoneMetadataCore.getIanaID("Asia/Calcutta"))
        assertEquals("Etc/Unknown",TimeZoneMetadataCore.getIanaID("GMT+530"))
        assertEquals("Etc/Unknown",TimeZoneMetadataCore.getIanaID(null))
        assertEquals("001",TimeZoneMetadataCore.getRegion("Etc/UTC"))
        assertEquals("BY",TimeZoneMetadataCore.getRegion("Europe/Minsk"))
        assertFailsWith<IllegalArgumentException> { TimeZoneMetadataCore.getRegion("Etc/Unknown") }
        assertFailsWith<NullPointerException> { TimeZoneMetadataCore.getRegion(null) }
    }
    @Test fun windowsMappingsPreserveRegionalPreferenceAndWorldFallback() {
        assertEquals("Pacific Standard Time",TimeZoneMetadataCore.getWindowsID("America/Los_Angeles"))
        assertEquals("Pacific Standard Time",TimeZoneMetadataCore.getWindowsID("US/Pacific"))
        assertEquals("America/Los_Angeles",TimeZoneMetadataCore.getIDForWindowsID("Pacific Standard Time",null))
        assertEquals("America/Vancouver",TimeZoneMetadataCore.getIDForWindowsID("Pacific Standard Time","CA"))
        assertEquals("America/Los_Angeles",TimeZoneMetadataCore.getIDForWindowsID("Pacific Standard Time","ca"))
        assertNull(TimeZoneMetadataCore.getWindowsID("GMT+530"))
        assertNull(TimeZoneMetadataCore.getWindowsID("Etc/Unknown"))
        assertNull(TimeZoneMetadataCore.getIDForWindowsID("unknown","US"))
        assertFailsWith<NullPointerException> { TimeZoneMetadataCore.getIDForWindowsID(null,null) }
        val names = UResourceBundle.getBundleInstance(ICUData.ICU_BASE_NAME,"zoneinfo64").get("Names").getStringArray().toSet()
        val mappings = UResourceBundle.getBundleInstance(ICUData.ICU_BASE_NAME,"windowsZones").get("mapTimezones")
        assertTrue(mappings.getSize() > 100)
        assertTrue(mappings.get("Central Standard Time (Mexico)").getString("MX").endsWith(' '))
        for (winID in mappings.keySet()) {
            val regions = mappings.get(winID)
            for (region in regions.keySet()) {
                val values = regions.getString(region).split(' ').dropLastWhile { it.isEmpty() }
                assertTrue(values.all { it in names },"$winID/$region")
                assertEquals(values.first(),TimeZoneMetadataCore.getIDForWindowsID(winID,region))
            }
        }
    }
    @Test fun availableIdArraysAreFreshAndVersionComesFromResource() {
        val first = TimeZoneMetadataCore.getAvailableIDs("BY")
        assertContentEquals(arrayOf("Europe/Minsk"),first)
        first[0] = "modified"
        assertContentEquals(arrayOf("Europe/Minsk"),TimeZoneMetadataCore.getAvailableIDs("BY"))
        val all = TimeZoneMetadataCore.getAvailableIDs()
        assertContentEquals(all,TimeZoneMetadataCore.getAvailableIDs(null as String?))
        assertNotSame(all,TimeZoneMetadataCore.getAvailableIDs())
        assertEquals(all.sorted(),all.toList())
        assertFalse("Etc/Unknown" in all)
        val version = UResourceBundle.getBundleInstance(ICUData.ICU_BASE_NAME,"zoneinfo64").getString("TZVersion")
        assertEquals(version,TimeZoneMetadataCore.getTZDataVersion())
        assertSame(TimeZoneMetadataCore.getTZDataVersion(),TimeZoneMetadataCore.getTZDataVersion())
    }
    @Test fun localeSelectionCallsOnlyTheChosenOwnerBranch() {
        val calls = mutableListOf<String>()
        fun select(locale: ULocale?): String = TimeZoneMetadataCore.forULocaleOrDefault(locale,
            { calls.add("default"); "default" },{ calls.add(it); "zone:$it" })
        assertEquals("default",select(ULocale("en_US")))
        assertEquals(listOf("default"),calls)
        calls.clear()
        assertEquals("zone:America/Los_Angeles",select(ULocale("en_US@timezone=America/Los_Angeles")))
        assertEquals(listOf("America/Los_Angeles"),calls)
        calls.clear()
        assertFailsWith<NullPointerException> { select(null) }
        assertTrue(calls.isEmpty())
    }
}

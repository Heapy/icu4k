package io.github.heapy.icu4k.impl.timezone

import com.ibm.icu.util.TimeZone as Source
import io.github.heapy.icu4k.impl.ICUData
import io.github.heapy.icu4k.util.ULocale
import io.github.heapy.icu4k.util.UResourceBundle
import kotlin.test.*

class TimeZoneMetadataCoreOracleTest {
    private fun outcome(block: () -> Any?): Any? = try { block() } catch(e: Throwable) {
        when(e) { is NullPointerException -> "null"; is IndexOutOfBoundsException -> "bounds"; else -> e.javaClass.simpleName+":"+e.message }
    }
    @Test fun allIdsMatchPublicCanonicalIanaRegionAndWindowsPolicies() {
        val ids = UResourceBundle.getBundleInstance(ICUData.ICU_BASE_NAME,"zoneinfo64").get("Names").getStringArray().toList()
        val extras = listOf(null,"","Unknown/Zone","GMT+530","gMt-١٢:٣٤:٥٦","GMT-000059","GMT+24:00","utc","America:New_York")
        for (id in ids + extras) {
            for(size in -1..3) {
                val a = if(size < 0) null else BooleanArray(size) { true }; val b = a?.copyOf()
                assertEquals(outcome { Source.getCanonicalID(id,a) },outcome { TimeZoneMetadataCore.getCanonicalID(id,b) },"canonical/$id/$size")
                assertContentEquals(a,b)
            }
            assertEquals(outcome { Source.getIanaID(id) },outcome { TimeZoneMetadataCore.getIanaID(id) },"iana/$id")
            assertEquals(outcome { Source.getRegion(id) },outcome { TimeZoneMetadataCore.getRegion(id) },"region/$id")
            assertEquals(outcome { Source.getWindowsID(id) },outcome { TimeZoneMetadataCore.getWindowsID(id) },"windows/$id")
            assertEquals(outcome { Source.countEquivalentIDs(id) },outcome { TimeZoneMetadataCore.countEquivalentIDs(id) })
            assertEquals(outcome { Source.getEquivalentID(id,-1) },outcome { TimeZoneMetadataCore.getEquivalentID(id,-1) })
        }
        assertEquals(Source.getTZDataVersion(),TimeZoneMetadataCore.getTZDataVersion())
    }
    @Test fun allWindowsMappingsAndRegionFallbacksMatchOriginal() {
        val mappings = UResourceBundle.getBundleInstance(ICUData.ICU_BASE_NAME,"windowsZones").get("mapTimezones")
        for(id in mappings.keySet()+listOf(null,"","unknown","pacific standard time"," Pacific Standard Time")) {
            val regions = if(id != null && id in mappings.keySet()) mappings.get(id).keySet() else emptySet()
            for(region in regions+listOf(null,"","US","CA","001","us","ca","ZZ","missing"," 001","001 ")) {
                assertEquals(outcome { Source.getIDForWindowsID(id,region) },outcome { TimeZoneMetadataCore.getIDForWindowsID(id,region) },"$id/$region")
            }
        }
    }
    @Test fun publicAvailableIdArraysAndFiltersMatchOriginal() {
        val all = Source.getAvailableIDs()
        assertContentEquals(all,TimeZoneMetadataCore.getAvailableIDs())
        for(region in (all.map { Source.getRegion(it) }+listOf(null,"by","us","ß","","missing")).distinct()) {
            assertContentEquals(Source.getAvailableIDs(region),TimeZoneMetadataCore.getAvailableIDs(region),"region/$region")
        }
        val offsets = all.map { Source.getFrozenTimeZone(it).rawOffset }.distinct()+listOf(Int.MIN_VALUE,Int.MAX_VALUE,1)
        for(raw in offsets) assertContentEquals(Source.getAvailableIDs(raw),TimeZoneMetadataCore.getAvailableIDs(raw),"offset/$raw")
        for(type in ZoneMetaCore.SystemTimeZoneType.entries) for(region in listOf(null,"US","BY","001","ZZ")) for(raw in listOf(null,0,-18_000_000,Int.MIN_VALUE)) {
            assertEquals(Source.getAvailableIDs(Source.SystemTimeZoneType.valueOf(type.name),region,raw).toList(),TimeZoneMetadataCore.getAvailableIDs(type,region,raw).toList())
        }
    }
    @Test fun localeKeywordSelectionRetainsSourceValueAndLazyBranchCalls() {
        for(id in listOf("", "en_US", "en_US@timezone=America/New_York", "en_US@timezone=GMT+530", "en_US@timezone=unknown", "en-US-u-tz-uslax", "en_US@timezone=", "en_US@timezone=US/Eastern")) {
            val source = com.ibm.icu.util.ULocale(id)
            val target = ULocale(id)
            val expected = source.getKeywordValue("timezone")
            val calls = mutableListOf<String>()
            val actual = TimeZoneMetadataCore.forULocaleOrDefault(target,{ calls.add("default"); null },{ calls.add("zone:$it"); it })
            assertEquals(expected,actual,id)
            assertEquals(listOf(if(expected == null) "default" else "zone:$expected"),calls,id)
        }
    }
}

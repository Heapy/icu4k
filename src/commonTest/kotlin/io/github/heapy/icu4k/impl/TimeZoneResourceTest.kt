package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.util.UResourceBundle
import kotlin.test.*

class TimeZoneResourceTest {
    @Test fun zoneIdsRegionsLinksAndTransitionTypesAreConsistent() {
        val top = UResourceBundle.getBundleInstance(ICUData.ICU_BASE_NAME, "zoneinfo64")
        val names = top.get("Names").getStringArray()
        val regions = top.get("Regions").getStringArray()
        val zones = top.get("Zones")
        assertEquals(names.sorted(), names.toList())
        assertEquals(names.size, regions.size)
        assertEquals(names.size, zones.getSize())
        assertTrue("Europe/Minsk" in names)
        assertTrue("Etc/Unknown" in names)
        assertEquals("BY", regions[names.indexOf("Europe/Minsk")])
        var links = 0
        var histories = 0
        for (i in names.indices) {
            val zone = zones.get(i)
            if (zone.getType() == UResourceBundle.INT) {
                val destination = zone.getInt()
                assertTrue(destination in names.indices, names[i])
                assertEquals(UResourceBundle.TABLE, zones.get(destination).getType(), names[i])
                links++
            } else {
                val offsets = zone.get("typeOffsets").getIntVector()
                assertTrue(offsets.isNotEmpty() && offsets.size % 2 == 0, names[i])
                if ("typeMap" in zone.keySet()) {
                    val types = zone.get("typeMap").getBinary(null)
                    assertTrue(types.all { (it.toInt() and 255) < offsets.size / 2 }, names[i])
                    var transitions = 0
                    for (key in listOf("transPre32", "trans", "transPost32")) {
                        if (key in zone.keySet()) {
                            val times = zone.get(key).getIntVector()
                            transitions += if (key == "trans") times.size else times.size / 2
                        }
                    }
                    assertEquals(transitions, types.size, names[i])
                    histories++
                }
                if ("finalRule" in zone.keySet()) {
                    val rule = zone.get("finalRule").getString()
                    assertEquals(11, top.get("Rules").get(rule).getIntVector().size, names[i])
                    zone.get("finalRaw").getInt()
                    zone.get("finalYear").getInt()
                }
            }
        }
        assertTrue(links > 100)
        assertTrue(histories > 100)
    }

    @Test fun primaryZonesAndMetazoneMappingsAreAvailableInCommonCode() {
        val zones = UResourceBundle.getBundleInstance(ICUData.ICU_BASE_NAME, "zoneinfo64")
            .get("Names").getStringArray().toSet()
        val meta = UResourceBundle.getBundleInstance(ICUData.ICU_BASE_NAME, "metaZones")
        val primary = meta.get("primaryZones")
        assertTrue(primary.getSize() > 0)
        for (key in primary.keySet()) assertTrue(primary.get(key).getString() in zones, key)
        assertTrue(meta.get("mapTimezones").getSize() > 100)
        assertTrue(meta.get("metazoneInfo").getSize() > 100)
    }
}

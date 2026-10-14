package io.github.heapy.icu4k.util

import kotlin.test.*

class RegionTest {
    @Test fun aliasesNumbersAndGeographicHierarchy() {
        val us = Region.getInstance("US")
        assertSame(us, Region.getInstance("USA"))
        assertSame(us, Region.getInstance(840))
        assertSame(Region.getInstance("MM"), Region.getInstance("BU"))
        val italy = Region.getInstance("IT")
        assertEquals("039", italy.getContainingRegion().toString())
        assertEquals("150", italy.getContainingRegion(Region.RegionType.CONTINENT).toString())
        assertEquals("001", italy.getContainingRegion(Region.RegionType.WORLD).toString())
        assertTrue(Region.getInstance("001").contains(italy))
        assertFalse(italy.contains(italy))
        assertTrue(Region.getInstance("EU").contains(italy))
        assertEquals(Region.RegionType.GROUPING, Region.getInstance("EU").getType())
        assertNull(italy.getContainingRegion(Region.RegionType.GROUPING))
        assertNull(Region.getInstance("001").getContainingRegion())
        assertEquals(Region.RegionType.UNKNOWN, Region.getInstance("ZZ").getType())
        assertEquals(Region.RegionType.SUBCONTINENT, Region.getInstance("QO").getType())
    }
    @Test fun deprecatedPreferredValuesAndSortedUnmodifiableViews() {
        val su = Region.getInstance("SU")
        assertEquals(Region.RegionType.DEPRECATED, su.getType())
        assertTrue(su.getPreferredValues()!!.any { it.toString() == "RU" })
        assertFailsWith<UnsupportedOperationException> { (su.getPreferredValues() as MutableList<Region>).clear() }
        for (type in Region.RegionType.entries) {
            val values = Region.getAvailable(type)
            assertEquals(values.map { it.toString() }.sorted(), values.map { it.toString() })
            assertTrue(values.all { it.getType() == type })
            assertFailsWith<UnsupportedOperationException> { (values as MutableSet<Region>).clear() }
        }
        val world = Region.getInstance("001")
        val territories = world.getContainedRegions(Region.RegionType.TERRITORY)
        assertTrue(Region.getInstance("US") in territories)
        assertTrue(Region.getInstance("FR") in territories)
        assertFailsWith<UnsupportedOperationException> { (world.getContainedRegions() as MutableSet<Region>).clear() }
        assertTrue(world.getContainedRegions(null).isEmpty())
        assertNull(world.getContainingRegion(null))
        assertNull(Region.getInstance("US").getPreferredValues())
    }
    @Test fun nullAndUnknownBoundaries() {
        assertFailsWith<NullPointerException> { Region.getInstance(null) }
        assertFailsWith<NullPointerException> { Region.getAvailable(null) }
        assertFailsWith<NullPointerException> { Region.getInstance("US").contains(null) }
        assertEquals("Unknown region id: us", assertFailsWith<IllegalArgumentException> { Region.getInstance("us") }.message)
        assertEquals("Unknown region code: -1", assertFailsWith<IllegalArgumentException> { Region.getInstance(-1) }.message)
    }
}

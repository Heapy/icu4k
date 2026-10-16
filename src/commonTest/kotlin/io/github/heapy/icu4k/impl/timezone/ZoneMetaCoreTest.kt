package io.github.heapy.icu4k.impl.timezone

import io.github.heapy.icu4k.impl.ICUData
import io.github.heapy.icu4k.impl.Utility
import io.github.heapy.icu4k.util.Output
import io.github.heapy.icu4k.util.UResourceBundle
import io.github.heapy.icu4k.impl.timezone.ZoneMetaCore.SystemTimeZoneType.*
import kotlin.test.*

class ZoneMetaCoreTest {
    @Test fun canonicalAndCountryMetadataUseEmbeddedData() {
        assertEquals("America/New_York", ZoneMetaCore.getCanonicalCLDRID("US/Eastern"))
        assertEquals("Asia/Calcutta", ZoneMetaCore.getCanonicalCLDRID("Asia/Kolkata"))
        assertEquals("Asia/Kolkata", ZoneMetaCore.getIanaID("Asia/Calcutta"))
        assertEquals("usnyc", ZoneMetaCore.getShortID("US/Eastern"))
        assertEquals("BY", ZoneMetaCore.getRegion("Europe/Minsk"))
        val primary = Output(false)
        assertEquals("BY", ZoneMetaCore.getCanonicalCountry("Europe/Minsk", primary))
        assertEquals(true, primary.value)
        assertNull(ZoneMetaCore.getCanonicalCountry("Etc/GMT", primary))
        assertEquals(false, primary.value)
        assertNull(ZoneMetaCore.getCanonicalCLDRID("Unknown/Zone"))
        assertFailsWith<NullPointerException> { ZoneMetaCore.getCanonicalCountry("Unknown/Zone", primary) }
        assertEquals(false, primary.value)
        assertFailsWith<NullPointerException> { ZoneMetaCore.getCanonicalCountry(null, null) }
    }
    @Test fun systemSetsAreSortedCachedAndProtectedFromMutation() {
        val all = ZoneMetaCore.getAvailableIDs(ANY)
        assertSame(all, ZoneMetaCore.getAvailableIDs(ANY, null, null) { error("Unused") })
        assertEquals(all.sorted(), all.toList())
        assertFalse("Etc/Unknown" in all)
        assertTrue("US/Eastern" in all)
        assertFalse("US/Eastern" in ZoneMetaCore.getAvailableIDs(CANONICAL))
        assertTrue("Etc/GMT" in ZoneMetaCore.getAvailableIDs(CANONICAL))
        assertFalse("Etc/GMT" in ZoneMetaCore.getAvailableIDs(CANONICAL_LOCATION))
        assertEquals(setOf("Europe/Minsk"), ZoneMetaCore.getAvailableIDs(CANONICAL_LOCATION, "by"))
        val mutable = all as MutableSet<String>
        assertFailsWith<UnsupportedOperationException> { mutable.clear() }
        assertFailsWith<UnsupportedOperationException> { mutable.remove("Missing") }
        assertFailsWith<UnsupportedOperationException> { mutable.addAll(emptyList()) }
        val iterator = mutable.iterator()
        iterator.next()
        assertFailsWith<UnsupportedOperationException> { iterator.remove() }
        val empty = ZoneMetaCore.getAvailableIDs(ANY, "missing") as MutableSet<String>
        empty.clear()
        assertFalse(empty.remove("Missing"))
        assertFalse(empty.addAll(emptyList()))
        assertFailsWith<UnsupportedOperationException> { empty.add("Missing") }
        assertFailsWith<NullPointerException> { ZoneMetaCore.getAvailableIDs(null) }
    }
    @Test fun rawOffsetFilteringCallsOwnerOnlyAfterRegionMatches() {
        val calls = mutableListOf<String>()
        val result = ZoneMetaCore.getAvailableIDs(ANY, "by", 10) { calls.add(it); 10 }
        assertEquals(listOf("Europe/Minsk"), calls)
        assertEquals(setOf("Europe/Minsk"), result)
        assertTrue(ZoneMetaCore.getAvailableIDs(ANY, "BY", 10) { null }.isEmpty())
        ZoneMetaCore.getAvailableIDs(ANY, "BY", null) { error("Unused") }
    }
    @Test fun equivalentIdsDereferenceResourceLinks() {
        val count = ZoneMetaCore.countEquivalentIDs("US/Eastern")
        assertTrue(count > 1)
        val equivalent = (0 until count).map { ZoneMetaCore.getEquivalentID("US/Eastern", it) }
        assertTrue("America/New_York" in equivalent)
        assertTrue("US/Eastern" in equivalent)
        assertEquals("", ZoneMetaCore.getEquivalentID(null, -1))
        assertEquals("", ZoneMetaCore.getEquivalentID("US/Eastern", count))
        assertEquals(0, ZoneMetaCore.countEquivalentIDs("Missing"))
        assertNull(ZoneMetaCore.openOlsonResource(null, "Missing"))
        val top = UResourceBundle.getBundleInstance(ICUData.ICU_BASE_NAME, "zoneinfo64")
        val alias = assertNotNull(ZoneMetaCore.openOlsonResource(top, "US/Eastern"))
        val canonical = assertNotNull(ZoneMetaCore.openOlsonResource(top, "America/New_York"))
        assertContentEquals(canonical.get("typeOffsets").getIntVector(), alias.get("typeOffsets").getIntVector())
        assertFailsWith<NullPointerException> { ZoneMetaCore.countEquivalentIDs(null) }
    }
    @Test fun gmtParsingPreservesFieldsOnFailureAndTruncatesOutput() {
        val fields = intArrayOf(9, 9, 9, 9, 9)
        assertTrue(ZoneMetaCore.parseCustomID("gMt-١٢:٣٤:٥٦", fields))
        assertContentEquals(intArrayOf(-1, 12, 34, 56, 9), fields)
        assertFalse(ZoneMetaCore.parseCustomID("GMT+24:00", fields))
        assertContentEquals(intArrayOf(-1, 12, 34, 56, 9), fields)
        val short = intArrayOf(0, 0)
        assertTrue(ZoneMetaCore.parseCustomID("GMT+530", short))
        assertContentEquals(intArrayOf(1, 5), short)
        assertTrue(ZoneMetaCore.parseCustomID("GMT+0", null))
        assertTrue(ZoneMetaCore.parseCustomID("GMT+0", intArrayOf()))
        assertFalse(ZoneMetaCore.parseCustomID(null, fields))
        assertEquals("GMT+05:30", ZoneMetaCore.getCustomID("GMT+0530"))
        assertEquals("GMT", ZoneMetaCore.getCustomID("GMT-000059"))
        assertEquals("GMT", ZoneMetaCore.customIDForOffset(-59_999))
        assertEquals("GMT+00:01:01", ZoneMetaCore.customIDForOffset(61_999))
        assertNull(ZoneMetaCore.getCustomID("GMT"))
    }
    @Test fun numberParserRetainsSingleUnitAdvanceAndOverflowRules() {
        val pos = intArrayOf(0)
        assertEquals(123, Utility.parseNumber("١٢٣x", pos, 10))
        assertEquals(3, pos[0])
        pos[0] = 0
        assertEquals(-1, Utility.parseNumber("2147483648", pos, 10))
        assertEquals(0, pos[0])
        val supplementary = "\uD835\uDFD9"
        assertEquals(1, Utility.parseNumber(supplementary, pos, 10))
        assertEquals(1, pos[0]) // The trailing low surrogate is left unconsumed in ICU4J.
        assertFalse(ZoneMetaCore.parseCustomID("GMT+$supplementary", null))
    }
}

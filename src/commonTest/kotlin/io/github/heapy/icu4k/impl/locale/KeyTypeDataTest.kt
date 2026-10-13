package io.github.heapy.icu4k.impl.locale

import io.github.heapy.icu4k.util.Output
import kotlin.test.*

class KeyTypeDataTest {
    @Test fun mapsAliasesAndSpecialTypesWithOutputFlags() {
        assertEquals("ca", KeyTypeData.toBcpKey("CALENDAR"))
        assertEquals("calendar", KeyTypeData.toLegacyKey("ca"))
        assertEquals("gregory", KeyTypeData.toBcpType("calendar", "GREGORIAN"))
        assertEquals("America/Los_Angeles", KeyTypeData.toLegacyType("tz", "uslax"))
        val known = Output<Boolean>(); val special = Output<Boolean>()
        assertEquals("10ffff-ffffff", KeyTypeData.toBcpType("vt", "10FFFF-FFFFFF", known, special))
        assertEquals(true, known.value); assertEquals(true, special.value)
        assertNull(KeyTypeData.toBcpType("vt", "abc", known, special))
        assertEquals(true, known.value); assertEquals(false, special.value)
        assertNull(KeyTypeData.toBcpType("unknown", "foo", known, special))
        assertEquals(false, known.value); assertEquals(false, special.value)
        assertEquals(KeyTypeData.ValueType.incremental, KeyTypeData.getValueType("ca"))
        assertTrue(KeyTypeData.isDeprecated("vt"))
        assertEquals("null", Output<String>().toString())
        assertFailsWith<UnsupportedOperationException> { (KeyTypeData.getBcp47Keys() as MutableSet<String>).clear() }
    }
}

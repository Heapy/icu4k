package io.github.heapy.icu4k.impl

import kotlin.test.*

class UResourceTest {
    @Test fun sharedBytesAndCachedStringsHaveIndependentLifetimes() {
        val bytes = byteArrayOf(0, 97, 98, 99, 0)
        val key = UResource.Key().setBytes(bytes, 1)
        val copy = key.clone()
        val suffix = key.subSequence(1, 3)
        assertEquals("abc", key.toString())
        bytes[2] = 120
        assertEquals("abc", key.toString())
        assertEquals("axc", copy.toString())
        assertEquals("xc", suffix.toString())
        assertEquals('x', key[1])
        assertTrue(key == copy)
        assertEquals("axc", key.substring(0))
        key.setString("new")
        assertEquals("axc", copy.toString())
        assertEquals("", key.setToEmpty().toString())
    }
    @Test fun originalHashComparisonAndPartialMutationContracts() {
        val key = UResource.Key("abc")
        assertEquals(37 * (37 * 97 + 97) + 97, key.hashCode())
        assertTrue(key.startsWith("ab")); assertTrue(key.endsWith("bc"))
        assertTrue(key.regionMatches(1, "bc")); assertFalse(key.regionMatches(1, "b"))
        assertEquals(-23, key.compareTo("xbc"))
        assertFalse(key.contentEquals(null))
        assertFailsWith<IllegalArgumentException> { key.setString("aé") }
        assertEquals(2, key.length)
        assertEquals("abc", key.toString())
        assertEquals("a\u0000", key.substring(0))
        val signed = UResource.Key().setBytes(byteArrayOf(-128, 0), 0)
        assertEquals('\uff80', signed[0]); assertFalse(signed.contentEquals("\uff80"))
        assertEquals(-128, signed.hashCode())
        assertEquals("a\u0000b", UResource.Key("a\u0000b").toString())
    }
}

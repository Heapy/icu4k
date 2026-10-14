package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.util.*
import kotlin.test.*

class BundleStringSinkTest {
    private fun bundle(): ICUResourceBundle = ICUResourceBundle.getBundleInstance("icu4k-test/string-sink-common", "en_US",
        BundleFixture.loader(BundleFixture.data()), ICUResourceBundle.OpenType.LOCALE_ROOT)
    @Test fun optimizedStringsAndValuesFollowAliasesWithoutChangingMarkerSemantics() {
        val bundle = bundle()
        for ((path, text) in listOf("target" to "US", "nested/inherited" to "deep", "aliasGroup/inherited" to "deep",
            "nested/sameName" to "implicit-target", "dynamic" to "US", "strings/1" to "b")) {
            assertEquals(text, bundle.findStringWithFallback(path))
            assertEquals(text, bundle.getStringWithFallback(path))
            assertEquals(text, bundle.getValueWithFallback(path).getString())
        }
        assertNull(bundle.findStringWithFallback("missing/path")); assertNull(bundle.findStringWithFallback(""))
        assertEquals("∅∅∅", bundle.findStringWithFallback("marker"))
        assertFailsWith<MissingResourceException> { bundle.getStringWithFallback("marker") }
        assertTrue(bundle.getValueWithFallback("marker").isNoInheritanceMarker())
        assertEquals(UResourceBundle.TABLE, bundle.getValueWithFallback("").getType())
        assertEquals(-1, bundle.getValueWithFallback("integer").getInt())
        assertFailsWith<UResourceTypeMismatchException> { bundle.getStringWithFallback("integer") }
        val scalar = bundle.getWithFallback("target")
        assertNull(scalar.findStringWithFallback("anything"))
    }
    @Test fun sinkReusesOutputObjectsAndResetsThemForEachParent() {
        val bundle = bundle()
        val keys = mutableListOf<UResource.Key>()
        val values = mutableListOf<UResource.Value>()
        val records = mutableListOf<Triple<String, String, Boolean>>()
        bundle.getAllItemsWithFallback("aliasGroup", object : UResource.Sink() {
            override fun put(key: UResource.Key, value: UResource.Value, noFallback: Boolean) {
                keys.add(key); values.add(value)
                val name = key.toString()
                assertTrue(value.getTable().findValue("x", value))
                records.add(Triple(name, value.getString(), noFallback))
                key.setString("changed-by-sink")
            }
        })
        assertEquals(listOf(Triple("nested", "en-x", false), Triple("nested", "root-x", true)), records)
        assertSame(keys[0], keys[1]); assertSame(values[0], values[1])
        var calls = 0
        val missing = object : UResource.Sink() {
            override fun put(key: UResource.Key, value: UResource.Value, noFallback: Boolean) { calls++ }
        }
        bundle.getAllItemsWithFallbackNoFail("missing", missing); assertEquals(0, calls)
        assertFailsWith<MissingResourceException> { bundle.getAllItemsWithFallback("missing", missing) }
        bundle.getAllItemsWithFallback("marker", object : UResource.Sink() {
            override fun put(key: UResource.Key, value: UResource.Value, noFallback: Boolean) {
                assertEquals("marker", key.toString()); assertTrue(value.isNoInheritanceMarker()); assertTrue(noFallback)
            }
        })
        assertFailsWith<IllegalStateException> {
            bundle.getAllItemsWithFallbackNoFail("target", object : UResource.Sink() {
                override fun put(key: UResource.Key, value: UResource.Value, noFallback: Boolean) { throw IllegalStateException("sink failure") }
            })
        }
    }
}

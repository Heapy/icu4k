package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.util.*
import kotlin.test.*

class BundleChildrenSinkTest {
    @Test fun aliasTablesEnumerateTargetParentsAndPreserveKeysUnderSinkMutation() {
        val bundle = ICUResourceBundle.getBundleInstance("icu4k-test/children-common", "en_US",
            BundleFixture.loader(BundleFixture.childData()), ICUResourceBundle.OpenType.LOCALE_ROOT)
        val records = mutableListOf<Triple<String, String, Boolean>>()
        bundle.getAllChildrenWithFallback("groups", object : UResource.Sink() {
            override fun put(key: UResource.Key, value: UResource.Value, noFallback: Boolean) {
                val name = key.toString()
                if (value.getType() == UResourceBundle.TABLE) assertTrue(value.getTable().findValue("value", value))
                records.add(Triple(name, value.getString(), noFallback))
                key.setString("sink-mutated-key")
            }
        })
        assertEquals(listOf("en-second", "US-first", "en-first", "root-first"), records.take(4).map { it.second })
        assertTrue(records.take(4).all { it.first == "alias" && !it.third })
        assertEquals(3, records.count { it.first == "text" && it.second == "US" })
        assertTrue(records.any { it.first == "alias" && it.third })
        assertFalse(records.any { it.first == "sink-mutated-key" })
        assertFailsWith<UResourceTypeMismatchException> {
            bundle.getAllChildrenWithFallback("target", object : UResource.Sink() {
                override fun put(key: UResource.Key, value: UResource.Value, noFallback: Boolean) = error("not called")
            })
        }
    }
}

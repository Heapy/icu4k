package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.util.ULocale
import kotlin.test.*

class ServiceDisplayNamesTest {
    @Test fun cachedMapIsReadOnlyAndFilteredMapIsIndependent() {
        val service = ICULocaleService()
        service.registerObject("value", ULocale("en_US"))
        service.registerObject("value", ULocale.FRENCH)
        val names = service.getDisplayNames(ULocale.ENGLISH)
        assertEquals(listOf("English (United States)", "French"), names.keys.toList())
        assertSame(names, service.getDisplayNames(ULocale.ENGLISH))
        assertFailsWith<UnsupportedOperationException> { names.clear() }
        assertFailsWith<UnsupportedOperationException> { names.putAll(emptyMap()) }
        assertFailsWith<UnsupportedOperationException> { names.entries.first().setValue("changed") }
        assertFailsWith<UnsupportedOperationException> { names.keys.remove("missing") }
        assertFailsWith<UnsupportedOperationException> { names.headMap("Z").clear() }
        val filtered = service.getDisplayNames(ULocale.ENGLISH, "en")
        assertEquals(listOf("English (United States)"), filtered.keys.toList())
        filtered.clear()
        assertEquals(2, names.size)
        service.registerObject("value", ULocale.GERMAN)
        assertNotSame(names, service.getDisplayNames(ULocale.ENGLISH))
        assertEquals(2, names.size)
    }
    @Test fun portableMapHasLiveRangesAndComparatorKeyIdentity() {
        val map = PortableServiceDisplayNamesMap(Comparator { a, b -> (a as String).lowercase().compareTo((b as String).lowercase()) })
        map["B"] = "one"; map["a"] = "two"; map["b"] = "replacement"; map["C"] = "three"
        assertEquals(listOf("a", "B", "C"), map.keys.toList())
        assertEquals("replacement", map["b"])
        val range = map.subMap("b", "d")
        assertEquals("B", range.firstKey())
        range.remove("C")
        assertFalse(map.containsKey("C"))
        assertFailsWith<IllegalArgumentException> { range["a"] = "outside" }
        val iterator = range.entries.iterator()
        iterator.next().setValue("new")
        iterator.remove()
        assertEquals(mapOf<String?, String>("a" to "two"), map)
    }
}

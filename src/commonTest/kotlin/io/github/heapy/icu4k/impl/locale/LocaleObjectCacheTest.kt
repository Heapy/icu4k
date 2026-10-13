package io.github.heapy.icu4k.impl.locale

import kotlin.test.*

class LocaleObjectCacheTest {
    private class Box(val key: String)
    private class Storage : LocaleCacheStorage<String, Box> {
        val calls = mutableListOf<String>()
        val entries = mutableMapOf<String, Box>()
        override fun get(key: String): Box? { calls.add("get:$key"); return entries[key] }
        override fun put(key: String, value: Box) { calls.add("put:$key"); entries[key] = value }
        override fun cleanStaleEntries() { calls.add("clean") }
    }
    @Test fun exactLookupNormalizationCreationAndCleanupOrder() {
        val storage = Storage()
        val cache = object : LocaleObjectCache<String, Box>(storage) {
            override fun normalizeKey(key: String): String? { storage.calls.add("normalize:$key"); return if (key == "reject") null else key.lowercase() }
            override fun createObject(key: String): Box? { storage.calls.add("create:$key"); return if (key == "missing") null else Box(key) }
        }
        val value = assertNotNull(cache.get("EN"))
        assertEquals(listOf("clean", "get:EN", "normalize:EN", "get:en", "create:en", "put:en", "clean"), storage.calls)
        storage.calls.clear(); assertSame(value, cache.get("en"))
        assertEquals(listOf("clean", "get:en"), storage.calls)
        storage.calls.clear(); assertSame(value, cache.get("EN"))
        assertEquals(listOf("clean", "get:EN", "normalize:EN", "get:en"), storage.calls)
        storage.calls.clear(); assertNull(cache.get("reject"))
        assertEquals(listOf("clean", "get:reject", "normalize:reject"), storage.calls)
        storage.calls.clear(); assertNull(cache.get("missing"))
        assertEquals(listOf("clean", "get:missing", "normalize:missing", "get:missing", "create:missing"), storage.calls)
    }
    @Test fun platformStorageCachesObjectsAndPrimitiveValues() {
        val objects = object : LocaleObjectCache<String, Box>() { override fun createObject(key: String) = Box(key) }
        val value = assertNotNull(objects.get("en")); assertSame(value, objects.get("en"))
        val strings = object : LocaleObjectCache<String, String>() { override fun createObject(key: String) = "value:$key" }
        assertEquals("value:en", strings.get("en")); assertEquals("value:en", strings.get("en"))
        val integers = object : LocaleObjectCache<Int, Int>() { override fun createObject(key: Int) = key + 1 }
        assertEquals(1001, integers.get(1000)); assertEquals(1001, integers.get(1000))
    }
}

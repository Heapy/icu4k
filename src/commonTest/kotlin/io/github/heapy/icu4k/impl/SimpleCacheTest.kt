package io.github.heapy.icu4k.impl

import kotlin.test.*

class SimpleCacheTest {
    @Test fun arbitraryAndNullKeysOverwriteAndClear() {
        for (type in listOf(-1, ICUCache.SOFT, ICUCache.WEAK)) {
            val cache = SimpleCache<Any?, String?>(type, -4)
            assertNull(cache.get("missing"))
            cache.put(null, "null key"); cache.put(ICUCache.NULL, "sentinel")
            assertEquals("null key", cache.get(null)); assertEquals("sentinel", cache.get(ICUCache.NULL))
            cache.put(null, "new"); assertEquals("new", cache.get(null))
            cache.clear(); assertNull(cache.get(null)); assertNull(cache.get(ICUCache.NULL))
        }
    }
}

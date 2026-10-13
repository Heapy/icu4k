package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.util.ICUException
import kotlin.test.*

class SoftCacheTest {
    private class Box(val data: Int)
    @Test fun nullStrongAndReclaimableValuesPreserveOriginalResetRules() {
        try {
            val nil = CacheValue.getInstance<Box>(null)
            assertTrue(nil.isNull()); assertNull(nil.get()); assertNull(nil.resetIfCleared(null))
            assertFailsWith<ICUException> { nil.resetIfCleared(Box(1)) }
            for (strength in listOf(CacheValue.Strength.STRONG, CacheValue.Strength.SOFT, null)) {
                CacheValue.setStrength(strength)
                assertEquals(strength == CacheValue.Strength.STRONG, CacheValue.futureInstancesWillBeStrong())
                val value = Box(1); val cached = CacheValue.getInstance(value)
                assertFalse(cached.isNull()); assertSame(value, cached.get()); assertSame(value, cached.resetIfCleared(Box(2)))
                assertSame(value, cached.resetIfCleared(null))
            }
            val reference = createCacheReference("value")
            assertEquals("value", reference.get()); reference.clear(); assertNull(reference.get())
            assertNull(createCacheReference<String>(null).get())
        } finally { CacheValue.setStrength(CacheValue.Strength.SOFT) }
    }
    @Test fun cacheIgnoresDataOnHitsAndCachesNullResults() {
        val calls = mutableListOf<Pair<String, Int>>()
        val cache = object : SoftCache<String, Box, Int>() {
            override fun createInstance(key: String, data: Int): Box? { calls.add(key to data); return if (key == "missing") null else Box(data) }
        }
        val first = assertNotNull(cache.getInstance("key", 1))
        assertSame(first, cache.getInstance("key", 2))
        assertNull(cache.getInstance("missing", 3)); assertNull(cache.getInstance("missing", 4))
        assertEquals(listOf("key" to 1, "missing" to 3), calls)
    }
}

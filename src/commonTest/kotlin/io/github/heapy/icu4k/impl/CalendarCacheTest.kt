package io.github.heapy.icu4k.impl

import kotlin.test.*

class CalendarCacheTest {
    @Test fun growthReplacementSentinelAndSignedLongKeys() {
        val cache=CalendarCache()
        assertEquals(CalendarCache.EMPTY,cache.get(1))
        for(key in 0L..5000L) cache.put(key,key*7)
        for(key in 0L..5000L) assertEquals(key*7,cache.get(key))
        repeat(200) { cache.put(1,it.toLong()) }
        assertEquals(199L,cache.get(1))
        for(key in listOf(-1L,Long.MIN_VALUE,Long.MAX_VALUE)) { cache.put(key,123); assertEquals(123L,cache.get(key)) }
        cache.put(100000,CalendarCache.EMPTY); assertEquals(CalendarCache.EMPTY,cache.get(100000))
    }
}

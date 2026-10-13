@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)
package io.github.heapy.icu4k.impl

import com.ibm.icu.impl.CacheValue as JValue
import com.ibm.icu.impl.SoftCache as JCache
import java.lang.ref.Reference
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.atomics.AtomicReference
import kotlin.test.*

class SoftCacheOracleTest {
    private class Box(val data: Int)
    private fun clear(j: JValue<Box>, k: CacheValue<Box>) {
        (j.javaClass.getDeclaredField("ref").apply { isAccessible = true }.get(j) as Reference<*>).clear()
        @Suppress("UNCHECKED_CAST")
        val ref = k.javaClass.getDeclaredField("ref").apply { isAccessible = true }.get(k) as AtomicReference<CacheReference<Box>>
        ref.load().clear()
    }
    private fun resetStrength() { JValue.setStrength(JValue.Strength.SOFT); CacheValue.setStrength(CacheValue.Strength.SOFT) }
    @Test fun valueStrengthNullSingletonAndClearedResetMatchOriginal() {
        try {
            val jNull = JValue.getInstance<Box>(null); val kNull = CacheValue.getInstance<Box>(null)
            assertEquals(jNull.isNull, kNull.isNull()); assertNull(jNull.get()); assertNull(kNull.get())
            assertSame(jNull, JValue.getInstance<Box>(null)); assertSame(kNull, CacheValue.getInstance<Box>(null))
            val je = assertFailsWith<com.ibm.icu.util.ICUException> { jNull.resetIfCleared(Box(1)) }
            val ke = assertFailsWith<io.github.heapy.icu4k.util.ICUException> { kNull.resetIfCleared(Box(1)) }
            assertEquals(je.message, ke.message)
            for (strength in listOf("STRONG", "SOFT", null)) {
                JValue.setStrength(strength?.let(JValue.Strength::valueOf)); CacheValue.setStrength(strength?.let(CacheValue.Strength::valueOf))
                assertEquals(JValue.futureInstancesWillBeStrong(), CacheValue.futureInstancesWillBeStrong())
                val initial = Box(1); val j = JValue.getInstance(initial); val k = CacheValue.getInstance(initial)
                assertSame(j.get(), k.get()); assertEquals(j.isNull, k.isNull())
                val replacement = Box(2); assertSame(j.resetIfCleared(replacement), k.resetIfCleared(replacement))
                if (strength != "STRONG") {
                    clear(j, k); assertNull(j.get()); assertNull(k.get()); assertEquals(j.isNull, k.isNull())
                    assertNull(j.resetIfCleared(null)); assertNull(k.resetIfCleared(null)); assertFalse(k.isNull())
                    assertSame(j.resetIfCleared(replacement), k.resetIfCleared(replacement))
                }
            }
        } finally { resetStrength() }
    }
    @Test fun hitsNullFactoriesEvictionStableSlotsAndStrengthChangesMatchOriginal() {
        try {
            resetStrength()
            val jc = mutableListOf<Pair<String, Int>>(); val kc = mutableListOf<Pair<String, Int>>()
            val j = object : JCache<String, Box, Int>() { override fun createInstance(key: String, data: Int): Box? { jc.add(key to data); return if (key == "missing") null else Box(data) } }
            val k = object : SoftCache<String, Box, Int>() { override fun createInstance(key: String, data: Int): Box? { kc.add(key to data); return if (key == "missing") null else Box(data) } }
            for ((key, data) in listOf("key" to 1, "key" to 2, "missing" to 3, "missing" to 4)) {
                assertEquals(j.getInstance(key, data)?.data, k.getInstance(key, data)?.data); assertEquals(jc, kc)
            }
            @Suppress("UNCHECKED_CAST")
            val jm = JCache::class.java.getDeclaredField("map").apply { isAccessible = true }.get(j) as Map<String, Any>
            @Suppress("UNCHECKED_CAST")
            val km = SoftCache::class.java.getDeclaredField("map").apply { isAccessible = true }.get(k) as CacheMap<String>
            @Suppress("UNCHECKED_CAST")
            val jSlot = jm.getValue("key") as JValue<Box>
            @Suppress("UNCHECKED_CAST")
            val kSlot = km.get("key") as CacheValue<Box>
            clear(jSlot, kSlot)
            assertEquals(j.getInstance("key", 5)?.data, k.getInstance("key", 5)?.data)
            assertSame(jSlot, jm["key"]); assertSame(kSlot, km.get("key")); assertEquals(jc, kc)
            JValue.setStrength(JValue.Strength.STRONG); CacheValue.setStrength(CacheValue.Strength.STRONG)
            clear(jSlot, kSlot)
            assertEquals(j.getInstance("key", 6)?.data, k.getInstance("key", 6)?.data)
            assertSame(jSlot, jm["key"]); assertSame(kSlot, km.get("key"))
            val jStrong = j.getInstance("strong", 7); val kStrong = k.getInstance("strong", 7)
            assertFalse(jm["strong"] is JValue<*>); assertFalse(km.get("strong") is CacheValue<*>)
            resetStrength()
            assertSame(jStrong, j.getInstance("strong", 8)); assertSame(kStrong, k.getInstance("strong", 8)); assertEquals(jc, kc)
        } finally { resetStrength() }
    }
    @Test fun forcedFactoryRacesReturnOnePublishedWinnerForBothStrengths() {
        val executor = Executors.newFixedThreadPool(8)
        try {
            for (strength in listOf("SOFT", "STRONG")) {
                JValue.setStrength(JValue.Strength.valueOf(strength)); CacheValue.setStrength(CacheValue.Strength.valueOf(strength))
                for (original in listOf(true, false)) {
                    val ready = CountDownLatch(8); val release = CountDownLatch(1); val count = AtomicInteger()
                    fun create(data: Int): Box { count.incrementAndGet(); ready.countDown(); check(release.await(10, TimeUnit.SECONDS)); return Box(data) }
                    val get: (Int) -> Box? = if (original) {
                        val cache = object : JCache<String, Box, Int>() { override fun createInstance(key: String, data: Int): Box = create(data) }
                        { data -> cache.getInstance("key", data) }
                    } else {
                        val cache = object : SoftCache<String, Box, Int>() { override fun createInstance(key: String, data: Int): Box = create(data) }
                        { data -> cache.getInstance("key", data) }
                    }
                    val futures = List(8) { data -> executor.submit<Box?> { get(data) } }
                    try { assertTrue(ready.await(10, TimeUnit.SECONDS)) } finally { release.countDown() }
                    val results = futures.map { assertNotNull(it.get(10, TimeUnit.SECONDS)) }
                    assertEquals(8, count.get()); assertEquals(1, results.toSet().size)
                    assertSame(results[0], get(100)); assertEquals(8, count.get())
                }
            }
        } finally { resetStrength(); executor.shutdownNow() }
    }
}

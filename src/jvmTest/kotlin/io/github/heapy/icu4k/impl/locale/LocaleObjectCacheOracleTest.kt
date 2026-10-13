package io.github.heapy.icu4k.impl.locale

import com.ibm.icu.impl.locale.LocaleObjectCache as J
import java.lang.ref.Reference
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*

class LocaleObjectCacheOracleTest {
    private class Box(val key: String)
    @Test fun callbacksHitsNullResultsAndConstructorValidationMatchOriginal() {
        val jCalls = mutableListOf<String>(); val kCalls = mutableListOf<String>()
        fun normalize(key: String, calls: MutableList<String>): String? { calls.add("n:$key"); return if (key == "reject") null else key.lowercase() }
        fun create(key: String, calls: MutableList<String>): Box? { calls.add("c:$key"); if (key == "error") throw IllegalStateException("factory"); return if (key == "missing") null else Box(key) }
        val j = object : J<String, Box>() {
            override fun normalizeKey(key: String): String? = normalize(key, jCalls)
            override fun createObject(key: String): Box? = create(key, jCalls)
        }
        val k = object : LocaleObjectCache<String, Box>() {
            override fun normalizeKey(key: String): String? = normalize(key, kCalls)
            override fun createObject(key: String): Box? = create(key, kCalls)
        }
        for (key in listOf("EN", "en", "EN", "reject", "missing", "missing", "FR", "error", "error", "fr")) {
            val a = runCatching { j.get(key)?.key }; val b = runCatching { k.get(key)?.key }
            assertEquals(a.getOrNull(), b.getOrNull()); assertEquals(a.exceptionOrNull()?.javaClass, b.exceptionOrNull()?.javaClass)
            assertEquals(jCalls, kCalls)
        }
        for ((capacity, factor, concurrency) in listOf(Triple(-1, .75f, 16), Triple(1, 0f, 1), Triple(1, Float.NaN, 1), Triple(1, 1f, 0), Triple(0, 1f, 1), Triple(16, .75f, 16))) {
            val a = runCatching { object : J<String, Box>(capacity, factor, concurrency) { override fun createObject(key: String) = Box(key) } }
            val b = runCatching { object : LocaleObjectCache<String, Box>(capacity, factor, concurrency) { override fun createObject(key: String) = Box(key) } }
            assertEquals(a.exceptionOrNull()?.javaClass, b.exceptionOrNull()?.javaClass)
        }
    }
    @Test fun queuedReclamationAndOldQueuedKeyRemovalMatchOriginal() {
        val j = object : J<String, Box>() { override fun createObject(key: String) = Box(key) }
        val storage = JvmLocaleCacheStorage<String, Box>(16, .75f, 16)
        val k = object : LocaleObjectCache<String, Box>(storage) { override fun createObject(key: String) = Box(key) }
        @Suppress("UNCHECKED_CAST")
        val jm = J::class.java.getDeclaredField("_map").apply { isAccessible = true }.get(j) as Map<String, Reference<Box>>
        @Suppress("UNCHECKED_CAST")
        val km = storage.javaClass.getDeclaredField("map").apply { isAccessible = true }.get(storage) as Map<String, Reference<Box>>
        val jInitial = j.get("en"); val kInitial = k.get("en")
        val jr = jm.getValue("en"); val kr = km.getValue("en")
        jr.clear(); kr.clear()
        val jReplacement = j.get("en"); val kReplacement = k.get("en")
        assertNotSame(jInitial, jReplacement); assertNotSame(kInitial, kReplacement)
        assertTrue(jr.enqueue()); assertTrue(kr.enqueue())
        val ja = j.get("en"); val ka = k.get("en")
        assertNotSame(jReplacement, ja); assertNotSame(kReplacement, ka)
        assertEquals(jm.keys, km.keys)
        jm.getValue("en").also { it.clear(); it.enqueue() }; km.getValue("en").also { it.clear(); it.enqueue() }
        j.get("fr"); k.get("fr")
        assertEquals(setOf("fr"), jm.keys); assertEquals(jm.keys, km.keys)
    }
    @Test fun concurrentFactoriesRemainOutsideStorageLocksAndPublishValidValues() {
        val ready = CountDownLatch(8); val release = CountDownLatch(1); val count = AtomicInteger()
        val cache = object : LocaleObjectCache<String, Box>() {
            override fun createObject(key: String): Box {
                count.incrementAndGet(); ready.countDown(); check(release.await(10, TimeUnit.SECONDS)); return Box(key)
            }
        }
        val executor = Executors.newFixedThreadPool(8)
        try {
            val futures = List(8) { executor.submit<Box?> { cache.get("en") } }
            try { assertTrue(ready.await(10, TimeUnit.SECONDS)) } finally { release.countDown() }
            val results = futures.map { assertNotNull(it.get(10, TimeUnit.SECONDS)) }
            assertEquals(8, count.get()); assertEquals(8, results.toSet().size)
            assertTrue(results.any { it === cache.get("en") }); assertTrue(results.all { it.key == "en" })
        } finally { release.countDown(); executor.shutdownNow() }
    }
}

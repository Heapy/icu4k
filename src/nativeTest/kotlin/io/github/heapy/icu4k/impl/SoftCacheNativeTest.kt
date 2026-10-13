@file:OptIn(kotlin.experimental.ExperimentalNativeApi::class, kotlin.native.concurrent.ObsoleteWorkersApi::class, kotlin.native.runtime.NativeRuntimeApi::class)
package io.github.heapy.icu4k.impl

import kotlin.native.concurrent.TransferMode
import kotlin.native.concurrent.Worker
import kotlin.native.ref.WeakReference
import kotlin.native.runtime.GC
import kotlin.test.*

class SoftCacheNativeTest {
    private class Box(val data: Int)
    private class Cache : SoftCache<String, Box, Int>() { override fun createInstance(key: String, data: Int) = Box(data) }
    private fun populated(): Pair<Cache, WeakReference<Box>> {
        val cache = Cache(); val value = cache.getInstance("key", 1)!!
        return cache to WeakReference(value)
    }
    @Test fun collectedValuesRecreateAndConcurrentMissesShareWinner() {
        CacheValue.setStrength(CacheValue.Strength.SOFT)
        val (cache, weak) = populated()
        GC.collect(); assertNull(weak.get())
        val value = assertNotNull(cache.getInstance("key", 2))
        assertEquals(2, value.data); assertSame(value, cache.getInstance("key", 3))
        val fresh = Cache()
        val workers = List(4) { Worker.start() }
        try {
            val tasks = workers.mapIndexed { index, worker -> worker.execute(TransferMode.SAFE, { fresh to index }) { (cache, data) -> cache.getInstance("shared", data)!! } }
            val results = tasks.map { it.result }
            for (result in results) assertSame(results[0], result)
        } finally { workers.forEach { it.requestTermination().result } }
    }
}

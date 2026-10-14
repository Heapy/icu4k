package io.github.heapy.icu4k.impl

import java.lang.ref.SoftReference
import java.util.concurrent.Executors
import java.util.concurrent.CountDownLatch
import kotlin.random.Random
import kotlin.test.*

class ResourceCacheOracleTest {
    private val cls = Class.forName("com.ibm.icu.impl.ICUResourceBundleReader\$ResourceCache")
    private fun original(max: Int): Any = cls.getDeclaredConstructor(Int::class.javaPrimitiveType).also { it.isAccessible = true }.newInstance(max)
    private fun call(cache: Any, name: String, vararg args: Any): Any? = cls.declaredMethods.single { it.name == name }.also { it.isAccessible = true }.invoke(cache, *args)
    private class Item(val id: Int)
    private fun clearReferences(target: Any) {
        val field = target.javaClass.getDeclaredField("values").also { it.isAccessible = true }
        val values = field.get(target) as Array<*>?
        if (values == null) {
            clearReferences(target.javaClass.getDeclaredField("rootLevel").also { it.isAccessible = true }.get(target))
            return
        }
        for (value in values) when {
            value is SoftReference<*> -> value.clear()
            value != null && value.javaClass.simpleName == "Reclaimable" -> {
                (value.javaClass.getDeclaredField("reference").also { it.isAccessible = true }.get(value) as CacheReference<*>).clear()
            }
            value != null && value.javaClass.simpleName == "Level" -> clearReferences(value)
        }
    }
    @Test fun transitionCollisionsReclamationAndStrengthChangesMatchOriginal() {
        try {
            for (count in listOf(1, 32, 33, 400)) {
                CacheValue.setStrength(CacheValue.Strength.SOFT)
                com.ibm.icu.impl.CacheValue.setStrength(com.ibm.icu.impl.CacheValue.Strength.SOFT)
                val j = original(0xfffffff); val k = ResourceCache(0xfffffff)
                val entries = (1..count).map { i -> ((listOf(0, 6, 5, 9)[i % 4] shl 28) or (i * 8192 + 1)) to Item(i) }
                for ((res, item) in entries.shuffled(Random(5))) {
                    assertNull(call(j, "get", res)); assertNull(k.get(res))
                    assertSame(item, call(j, "putIfAbsent", res, item, 24)); assertSame(item, k.putIfAbsent(res, item, 24))
                }
                for ((res, item) in entries) { assertSame(item, call(j, "get", res)); assertSame(item, k.get(res)) }
                clearReferences(j); clearReferences(k)
                CacheValue.setStrength(CacheValue.Strength.STRONG)
                com.ibm.icu.impl.CacheValue.setStrength(com.ibm.icu.impl.CacheValue.Strength.STRONG)
                for ((res, _) in entries) {
                    assertNull(call(j, "get", res)); assertNull(k.get(res))
                    val item = Item(-res)
                    assertSame(item, call(j, "putIfAbsent", res, item, 24)); assertSame(item, k.putIfAbsent(res, item, 24))
                }
                clearReferences(j); clearReferences(k)
                for ((res, _) in entries) assertSame(call(j, "get", res), k.get(res))
            }
        } finally {
            CacheValue.setStrength(CacheValue.Strength.SOFT)
            com.ibm.icu.impl.CacheValue.setStrength(com.ibm.icu.impl.CacheValue.Strength.SOFT)
        }
    }
    @Test fun concurrentTreePublicationReturnsOneWinnerPerResource() {
        val cache = ResourceCache(0xfffffff)
        val executor = Executors.newFixedThreadPool(8)
        val start = CountDownLatch(1)
        try {
            val jobs = (0 until 8).map { worker -> executor.submit<List<Any>> {
                start.await()
                (1..200).map { i -> cache.putIfAbsent(i * 8192 + 1, Item(worker), 0) }
            } }
            start.countDown()
            val outputs = jobs.map { it.get() }
            for (i in 0 until 200) for (worker in 1 until 8) assertSame(outputs[0][i], outputs[worker][i])
        } finally { executor.shutdownNow() }
    }
}

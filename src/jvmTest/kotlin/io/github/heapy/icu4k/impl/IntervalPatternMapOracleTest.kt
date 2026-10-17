package io.github.heapy.icu4k.impl

import kotlin.random.Random
import kotlin.test.*

class IntervalPatternMapOracleTest {
    private fun colliders(depth: Int): List<String> = if (depth == 0) listOf("") else colliders(depth - 1).flatMap { listOf(it + "Aa", it + "BB") }
    @Test fun resizeCollisionTreeAndReplacementOrderMatchesJdkHashMap() {
        for (capacity in listOf<Int?>(null, 0, 1, 2, 3, 19, 32, 64, 128)) for (seed in 0..7) {
            val a = if (capacity == null) java.util.HashMap<String?, Int?>() else java.util.HashMap(capacity)
            val b = IntervalPatternMap<Int?>(capacity)
            val keys = (colliders(6) + (0..150).map { "key$it" } + listOf(null, "") + (1..20).map { "\u0000".repeat(it) }).shuffled(Random(seed))
            for ((i, key) in keys.withIndex()) {
                assertEquals(a.put(key, i), b.put(key, i), "$capacity/$seed/$key")
                assertEquals(a.entries.map { it.key to it.value }, b.entries.map { it.key to it.value }, "$capacity/$seed/$i")
                assertEquals(a.hashCode(), b.hashCode())
                assertEquals<Map<String?, Int?>>(a, b)
            }
            for (key in keys.shuffled(Random(seed + 99))) {
                assertEquals(a.put(key, null), b.put(key, null))
                assertEquals(a.keys.toList(), b.keys.toList())
                assertTrue(b.containsKey(key)); assertNull(b[key])
            }
        }
    }
    @Test fun longStringHashesAndCloneInsertionHistoryMatch() {
        val random = Random(19317)
        val a = java.util.HashMap<String?, String>(); val b = IntervalPatternMap<String>()
        repeat(1500) {
            val key = buildString { repeat(random.nextInt(0, 60)) { append(random.nextInt(0x10000).toChar()) } }
            a[key] = key; b[key] = key
            assertEquals(a.keys.toList(), b.keys.toList(), "entry/$it")
        }
        val ac = java.util.HashMap<String?, String>(); val bc = IntervalPatternMap<String>()
        for ((key, value) in a) ac[key] = value
        for ((key, value) in b) bc[key] = value
        assertEquals(ac.keys.toList(), bc.keys.toList())
    }
}

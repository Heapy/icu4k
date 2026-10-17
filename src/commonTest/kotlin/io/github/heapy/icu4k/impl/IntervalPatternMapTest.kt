package io.github.heapy.icu4k.impl
import kotlin.test.*

class IntervalPatternMapTest {
    private fun collisions(n: Int): List<String> = if (n == 0) listOf("") else collisions(n - 1).flatMap { listOf(it + "Aa", it + "BB") }
    @Test fun sourceJdkHashBucketAndCollisionTreeSnapshot() {
        // JDK 25 HashMap snapshot; the hash distinguishes the colliding Aa/BB strings.
        val keys: List<String?> = collisions(6) + (0..150).map { "key$it" } + listOf(null, "") + (1..20).map { "\u0000".repeat(it) }
        for (capacity in listOf(0, 19, 64)) {
            val map = IntervalPatternMap<Int>(capacity)
            for (i in keys.indices) map[keys[(i * 37) % keys.size]] = i
            var hash = -3750763034362895579L
            for (key in map.keys) {
                hash = (hash xor (key?.length ?: -1).toLong()) * 1099511628211L
                if (key != null) for (ch in key) hash = (hash xor ch.code.toLong()) * 1099511628211L
            }
            assertEquals(1906472231710470710L, hash, "capacity/$capacity")
            assertEquals(237, map.size)
        }
    }
}

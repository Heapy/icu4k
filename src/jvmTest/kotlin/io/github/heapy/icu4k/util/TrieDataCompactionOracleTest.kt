package io.github.heapy.icu4k.util

import com.ibm.icu.util.MutableCodePointTrie as JavaTrie
import kotlin.random.Random
import kotlin.test.*

class TrieDataCompactionOracleTest {
    private fun nested(owner: Class<*>, name: String): Any = owner.declaredClasses.single { it.simpleName == name }
        .getDeclaredConstructor().apply { isAccessible = true }.newInstance()
    private fun field(obj: Any, name: String): Any = obj.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(obj)
    private fun call(obj: Any, name: String, vararg args: Any): Any? {
        val types = args.map { if (it is Int) Int::class.javaPrimitiveType!! else it.javaClass }.toTypedArray()
        return obj.javaClass.getDeclaredMethod(name, *types).apply { isAccessible = true }.invoke(obj, *args)
    }
    @Test fun dataPassesMatchOriginalIncludingOffsetsAndFlags() {
        val random = Random(241)
        for (fastILimit in listOf(0x1000 shr 4, 0x10000 shr 4)) repeat(8) { fixture ->
            val j = JavaTrie(17, -7); val k = MutableCodePointTrie(17, -7)
            // Both passes are normally called after pinning the fast range.
            j.set(0x10000, 17); k.set(0x10000, 17)
            if (fixture % 2 == 0) { j.set(0x10ffff, 2); k.set(0x10ffff, 2) }
            repeat(800) {
                val start = random.nextInt(0x20000)
                val end = minOf(0x1ffff, start + random.nextInt(256))
                val value = if (fixture < 4) random.nextInt(48) else random.nextInt()
                j.setRange(start, end, value); k.setRange(start, end, value)
            }
            // Mixed blocks, equal blocks and adjacent-block suffix/prefix overlaps.
            repeat(256) { i -> j.set(i, i % 7); k.set(i, i % 7) }
            val ascii = IntArray(128) { j[it] }
            val ja = nested(j.javaClass, "AllSameBlocks")
            val ka = MutableCodePointTrie.AllSameBlocks()
            val jc = call(j, "compactWholeDataBlocks", fastILimit, ja) as Int
            val kc = call(k, "compactWholeDataBlocks", fastILimit, ka) as Int
            assertEquals(jc, kc)
            assertContentEquals(field(j, "index") as IntArray, field(k, "index") as IntArray)
            assertContentEquals(field(j, "flags") as ByteArray, field(k, "flags") as ByteArray)
            val jn = call(ja, "findMostUsed") as Int
            assertEquals(jn, ka.findMostUsed())
            val jd = ascii.copyOf(jc); val kd = ascii.copyOf(kc)
            val jm = nested(j.javaClass, "MixedBlocks")
            val km = MutableCodePointTrie.MixedBlocks()
            val jl = call(j, "compactData", fastILimit, jd, jn, jm) as Int
            val kl = call(k, "compactData", fastILimit, kd, jn, km) as Int
            assertEquals(jl, kl)
            assertContentEquals(jd, kd, "data $fastILimit $fixture")
            assertContentEquals(field(j, "index") as IntArray, field(k, "index") as IntArray)
        }
    }
}

package io.github.heapy.icu4k.impl.calendar

import java.util.TreeMap
import kotlin.random.Random
import kotlin.test.*

class PatternOrderedMapOracleTest {
    private class Key(val text: String): Comparable<Key> { override fun compareTo(other: Key): Int=text.lowercase().compareTo(other.text.lowercase()) }
    @Test fun balancedStorageAndComparatorIdentityMatchTreeMap() {
        val source=TreeMap<Key,String>(); val target=PatternOrderedMap<Key,String>(); val random=Random(13183)
        repeat(20000) { step ->
            val key=Key(random.nextInt(10000).toString().let { if(step%2==0) "X$it" else "x$it" }); val value="v$step"
            source[key]=value; target[key]=value
            assertEquals(source[key],target[key])
            if(step%1000==0) compare(source,target)
        }
        compare(source,target); compare(source,target.copy())
        val root=PatternOrderedMap::class.java.getDeclaredField("root").apply { isAccessible=true }
        fun verify(node: Any?): Int {
            if(node==null) return 0
            val type=node.javaClass
            val left=verify(type.getDeclaredField("left").apply { isAccessible=true }.get(node)); val right=verify(type.getDeclaredField("right").apply { isAccessible=true }.get(node))
            assertTrue(kotlin.math.abs(left-right)<=1)
            val expected=1+maxOf(left,right); assertEquals(expected,type.getDeclaredField("height").apply { isAccessible=true }.getInt(node)); return expected
        }
        assertTrue(verify(root.get(target))<25)
    }
    private fun compare(source: TreeMap<Key,String>,target: PatternOrderedMap<Key,String>) {
        val expected=source.entries.toList(); val actual=target.toList(); assertEquals(expected.size,actual.size)
        for(i in expected.indices) { assertSame(expected[i].key,actual[i].first); assertEquals(expected[i].value,actual[i].second) }
    }
    @Test fun failFastAndValueReplacementFollowTreeMapKeyIteration() {
        val source=TreeMap<Int,String>(); val target=PatternOrderedMap<Int,String>()
        for(key in 1000 downTo 0) { source[key]="$key"; target[key]="$key" }
        val a=source.keys.iterator(); val b=target.iterator()
        source[0]="changed"; target[0]="changed"; assertEquals(a.next(),b.next().first)
        source[1001]="new"; target[1001]="new"
        assertEquals(a.hasNext(),b.hasNext()); assertFailsWith<ConcurrentModificationException> { a.next() }; assertFailsWith<ConcurrentModificationException> { b.next() }
        assertEquals(source.keys.toList(),target.map { it.first })
    }
    @Test fun deletionsAndMutableKeyIteratorsMatchTreeMap() {
        val source=TreeMap<Int,String>(); val target=PatternOrderedMap<Int,String>(); val random=Random(783)
        repeat(10000) { step ->
            val key=random.nextInt(2000)
            if(random.nextBoolean()) { source[key]="$step"; target[key]="$step" } else assertEquals(source.remove(key),target.remove(key))
            if(step%500==0) { assertEquals(source.keys.toList(),target.keys.toList()); assertEquals(source.entries.map { it.key to it.value },target.toList()) }
        }
        val a=source.keys.iterator(); val b=target.keys.iterator()
        while(a.hasNext()) { assertEquals(a.next(),b.next()); if(random.nextBoolean()) { a.remove(); b.remove() } }
        assertEquals(source.keys.toList(),target.keys.toList())
        assertFalse(b.hasNext()); target.keys.clear(); source.keys.clear(); assertEquals(source.keys.toList(),target.keys.toList())
    }

}

package io.github.heapy.icu4k.impl.calendar

import io.github.heapy.icu4k.impl.CalendarCache
import com.ibm.icu.impl.CalendarCache as Source
import kotlin.test.*

class CalendarCacheOracleTest {
    private fun snapshot(value: Any,source: Boolean): List<Any?> {
        fun field(name: String): Any?=value.javaClass.getDeclaredField(name).apply { isAccessible=true }.get(value)
        return listOf(field(if(source) "pIndex" else "primeIndex"),field("size"),field("arraySize"),field("threshold"),(field("keys") as LongArray).toList(),(field("values") as LongArray).toList())
    }
    @Test fun rehashCallsOverridesUnderTheReceiverMonitor() {
        class Original : Source() {
            val held=ArrayList<Boolean>()
            override fun put(key: Long,value: Long) { held.add(Thread.holdsLock(this)); super.put(key,value) }
        }
        class Port : CalendarCache() {
            val held=ArrayList<Boolean>()
            override fun put(key: Long,value: Long) { held.add(Thread.holdsLock(this)); super.put(key,value) }
        }
        val a=Original(); val b=Port()
        for(key in 0L..200L) { a.put(key,key); b.put(key,key) }
        assertTrue(a.held.any { it }); assertTrue(b.held.any { it })
        assertEquals(a.held,b.held)
        for(key in 0L..200L) assertEquals(a.get(key),b.get(key))
    }

    @Test fun probeLayoutGrowthAndReplacementCountsMatchSource() {
        val a=Source(); val b=CalendarCache()
        for(step in 0..1500) {
            val key=when(step%11) { 0 -> -1L; 1 -> Long.MIN_VALUE; 2 -> Long.MAX_VALUE; else -> step.toLong() }
            val value=if(step%101==0) CalendarCache.EMPTY else step*9L
            a.put(key,value); b.put(key,value)
            assertEquals(a.get(key),b.get(key))
            assertEquals(a.get(1000000),b.get(1000000))
            if(step%23==0) assertEquals(snapshot(a,true),snapshot(b,false),"$step/$key")
        }
        assertEquals(snapshot(a,true),snapshot(b,false))
    }
}

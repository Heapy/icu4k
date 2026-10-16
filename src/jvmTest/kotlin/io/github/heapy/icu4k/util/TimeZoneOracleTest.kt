package io.github.heapy.icu4k.util

import com.ibm.icu.util.TimeZone as Source
import com.ibm.icu.util.BasicTimeZone as SourceBasic
import com.ibm.icu.util.SimpleTimeZone as SourceSimple
import io.github.heapy.icu4k.impl.timezone.SimpleTimeZoneCore
import kotlin.random.Random
import kotlin.test.*

class TimeZoneOracleTest {
    private open class Port : TimeZone("stored") {
        var calls = arrayListOf<String>()
        override fun getID() = "virtual"
        override fun getRawOffset(): Int { calls.add("raw"); return 12345 }
        override fun getOffset(era: Int, year: Int, month: Int, day: Int, dayOfWeek: Int, milliseconds: Int): Int {
            calls.add("fields:$era:$year:$month:$day:$dayOfWeek:$milliseconds"); return 1812345
        }
        override fun setRawOffset(offsetMillis: Int) {}
        override fun useDaylightTime(): Boolean { calls.add("uses"); return true }
        override fun inDaylightTime(date: Date?) = false
    }
    private open class Original : Source("stored") {
        var calls = arrayListOf<String>()
        override fun getID() = "virtual"
        override fun getRawOffset(): Int { calls.add("raw"); return 12345 }
        override fun getOffset(era: Int, year: Int, month: Int, day: Int, dayOfWeek: Int, milliseconds: Int): Int {
            calls.add("fields:$era:$year:$month:$day:$dayOfWeek:$milliseconds"); return 1812345
        }
        override fun setRawOffset(offsetMillis: Int) {}
        override fun useDaylightTime(): Boolean { calls.add("uses"); return true }
        override fun inDaylightTime(date: java.util.Date?) = false
    }
    private fun outcome(block: () -> Any?): Any? = try { block() } catch (failure: Throwable) {
        val cause = if (failure is java.lang.reflect.InvocationTargetException) failure.cause!! else failure
        if (cause is VirtualMachineError) throw cause
        when (cause) { is NullPointerException -> "null"; is IndexOutOfBoundsException -> "bounds"; else -> cause.javaClass.name }
    }
    @Test fun inheritedOffsetMethodsPreserveVirtualCallsAndPartialWrites() {
        val a = Original(); val b = Port()
        for (date in listOf(Long.MIN_VALUE, -12219292800000L, -1L, 0L, 1719792000000L, Long.MAX_VALUE)) {
            for (local in listOf(false, true)) for (size in listOf(-1, 0, 1, 2, 3)) {
                val left = if (size < 0) null else IntArray(size) { -7 }
                val right = left?.copyOf()
                a.calls.clear(); b.calls.clear()
                assertEquals(outcome { a.getOffset(date, local, left) }, outcome { b.getOffset(date, local, right) })
                assertContentEquals(left, right); assertEquals(a.calls, b.calls, "$date/$local/$size")
            }
            a.calls.clear(); b.calls.clear()
            assertEquals(a.getOffset(date), b.getOffset(date)); assertEquals(a.calls, b.calls)
        }
        assertEquals(a.hashCode(), b.hashCode()); assertEquals(a.dstSavings, b.getDSTSavings())
        assertEquals(a.observesDaylightTime(), b.observesDaylightTime())
    }
    @Test fun hostCloningPreservesRuntimeSubtypeAndShallowSubclassFields() {
        val source = Original(); val port = Port()
        val sourceCopy = source.cloneAsThawed() as Original; val portCopy = port.cloneAsThawed() as Port
        assertSame(source.calls, sourceCopy.calls); assertSame(port.calls, portCopy.calls)
        assertEquals(source, sourceCopy); assertEquals(port, portCopy)
        sourceCopy.setID("copy"); portCopy.setID("copy")
        assertEquals(source == sourceCopy, port == portCopy)
        assertEquals(sourceCopy.hashCode(), portCopy.hashCode())
    }
    private class Annual : BasicTimeZone("annual") {
        private val rules = SimpleTimeZoneCore(-28800000, "annual", 2, 2, 1, 7200000, 0, 10, 1, 1, 7200000, 0)
        override fun getOffset(era: Int, year: Int, month: Int, day: Int, dayOfWeek: Int, milliseconds: Int) = rules.getOffset(era, year, month, day, dayOfWeek, milliseconds)
        override fun getRawOffset() = rules.getRawOffset()
        override fun setRawOffset(offsetMillis: Int) = rules.setRawOffset(offsetMillis)
        override fun useDaylightTime() = rules.useDaylightTime()
        override fun inDaylightTime(date: Date?) = false
        override fun getNextTransition(base: Long, inclusive: Boolean) = rules.getNextTransition(base, inclusive)
        override fun getPreviousTransition(base: Long, inclusive: Boolean) = rules.getPreviousTransition(base, inclusive)
        @Suppress("UNCHECKED_CAST")
        override fun getTimeZoneRules() = rules.getTimeZoneRules() as Array<TimeZoneRule?>
    }
    private fun rules(values: Array<TimeZoneRule?>?) = values?.map { it.toString() }
    private fun sourceRules(values: Array<com.ibm.icu.util.TimeZoneRule?>?) = values?.map { it.toString() }
    @Test fun publicTransitionFilteringAndSimplificationMatchSource() {
        val source: SourceBasic = SourceSimple(-28800000, "annual", 2, 2, 1, 7200000, 10, 1, 1, 7200000)
        val port = Annual()
        val random = Random(7813)
        repeat(100) {
            val start = random.nextLong(-2208988800000L, 4102444800000L)
            assertEquals(sourceRules(source.getTimeZoneRules(start)), rules(port.getTimeZoneRules(start)), "filtered/$start")
            assertEquals(sourceRules(source.getSimpleTimeZoneRulesNear(start)), rules(port.getSimpleTimeZoneRulesNear(start)), "simple/$start")
            for (ignore in listOf(false, true)) {
                assertEquals(source.hasEquivalentTransitions(source.clone(), start, start + 86400000L * 800, ignore),
                    port.hasEquivalentTransitions(Annual(), start, start + 86400000L * 800, ignore))
            }
        }
    }
}

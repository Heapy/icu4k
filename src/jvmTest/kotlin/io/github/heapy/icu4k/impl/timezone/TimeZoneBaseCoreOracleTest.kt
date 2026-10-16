package io.github.heapy.icu4k.impl.timezone

import com.ibm.icu.util.TimeZone
import io.github.heapy.icu4k.util.Date
import kotlin.test.*

class TimeZoneBaseCoreOracleTest {
    private class Script {
        val trace = ArrayList<String>()
        var raw = 0
        var savings = listOf(0)
        var pass = 0
        var uses = false
        var daylight = false
        var frozen = false
        var mutation: Int? = null
        var exposed: IntArray? = null
        var virtual = false
        var cloneResult: TimeZone? = null
        fun fields(era: Int, year: Int, month: Int, day: Int, dow: Int, millis: Int): Int {
            trace.add("fields:$era:$year:$month:$day:$dow:$millis")
            mutation?.let { exposed!![0] = it }
            return raw + savings[minOf(pass++, savings.lastIndex)]
        }
        fun reset() { trace.clear(); pass = 0; exposed = null }
    }
    private open class Zone(val script: Script, initialize: Boolean = true) : TimeZone() {
        init { if (initialize) super.setID("stored") ; script.trace.clear() }
        override fun getID() = "virtual-id"
        fun baseID(): String? = super.getID()
        override fun getRawOffset(): Int { script.trace.add("raw"); return script.raw }
        override fun setRawOffset(value: Int) { script.raw = value }
        override fun useDaylightTime(): Boolean { script.trace.add("uses"); return script.uses }
        override fun inDaylightTime(date: java.util.Date?): Boolean { script.trace.add("inDaylight"); assertNotNull(date); return script.daylight }
        override fun isFrozen(): Boolean { script.trace.add("frozen"); return script.frozen }
        override fun cloneAsThawed(): TimeZone? { script.trace.add("thawed"); return script.cloneResult }
        override fun getOffset(era: Int, year: Int, month: Int, day: Int, dow: Int, millis: Int) = script.fields(era, year, month, day, dow, millis)
        override fun getOffset(date: Long, local: Boolean, offsets: IntArray) {
            script.trace.add("virtual:$date:$local")
            script.exposed = offsets
            if (script.virtual) { offsets[0] = Int.MAX_VALUE; offsets[1] = 10 }
            else super.getOffset(date, local, offsets)
        }
        fun baseOffset(date: Long, local: Boolean, offsets: IntArray?) = super.getOffset(date, local, offsets)
        fun baseIsFrozen() = super.isFrozen()
    }
    private class DifferentZone(script: Script) : Zone(script)

    private class Pairing(initialize: Boolean = true) {
        val script = Script()
        val source = Zone(script, initialize)
        private val owner: TimeZoneBaseCore.Owner<TimeZone> = object : TimeZoneBaseCore.Owner<TimeZone> {
            override fun getRawOffset(zone: TimeZone) = zone.rawOffset
            override fun getOffset(zone: TimeZone, era: Int, year: Int, month: Int, day: Int, dayOfWeek: Int, millis: Int) =
                zone.getOffset(era, year, month, day, dayOfWeek, millis)
            override fun getOffset(zone: TimeZone, date: Long, local: Boolean, offsets: IntArray) {
                script.trace.add("virtual:$date:$local")
                script.exposed = offsets
                if (script.virtual) { offsets[0] = Int.MAX_VALUE; offsets[1] = 10 }
                else core.getOffset(date, local, offsets)
            }
            override fun useDaylightTime(zone: TimeZone) = zone.useDaylightTime()
            override fun inDaylightTime(zone: TimeZone, date: Date) = zone.inDaylightTime(date)
            override fun isFrozen(zone: TimeZone) = zone.isFrozen
            override fun cloneAsThawed(zone: TimeZone) = zone.cloneAsThawed()
            override fun hasSameClass(other: Any) = source.javaClass == other.javaClass
            override fun storedID(other: Any) = idField.get(other) as String?
        }
        val core: TimeZoneBaseCore<TimeZone> = if (initialize) TimeZoneBaseCore(source, owner, "stored") else TimeZoneBaseCore(source, owner)
        fun compare(sourceCall: () -> Any?, commonCall: () -> Any?) {
            script.reset()
            val expected = outcome(sourceCall)
            val expectedTrace = script.trace.toList()
            script.reset()
            assertEquals(expected, outcome(commonCall))
            assertEquals(expectedTrace, script.trace)
        }
    }

    @Test fun fieldOffsetsLocalRetryOverflowArrayMutationAndFailures() {
        val p = Pairing()
        val dates = listOf(Long.MIN_VALUE, Long.MIN_VALUE + 86400000, -62167219200000L, -2208988800000L, -86400001L, -1L, 0L, 1710053999999L, 1710054000000L, Long.MAX_VALUE)
        for (raw in listOf(Int.MIN_VALUE, -18000000, 0, 20700000, Int.MAX_VALUE)) {
            p.script.raw = raw
            for (savings in listOf(listOf(0), listOf(3600000, 0), listOf(-1800000, 7200000), listOf(Int.MIN_VALUE, Int.MAX_VALUE))) {
                p.script.savings = savings
                for (date in dates) for (local in listOf(false, true)) for (mutation in listOf(null, 12345)) {
                    p.script.mutation = mutation
                    for (size in listOf(null, 0, 1, 2, 3)) {
                        val a = size?.let { IntArray(it) { 777 } }
                        val b = a?.copyOf()
                        p.compare({ p.script.exposed = a; p.source.baseOffset(date, local, a) }, { p.script.exposed = b; p.core.getOffset(date, local, b) })
                        assertEquals(a?.toList(), b?.toList(), "$date/$raw/$savings/$local/$size/$mutation")
                    }
                    p.compare({ p.source.getOffset(date) }, { p.core.getOffset(date) })
                }
            }
        }
        p.script.virtual = true
        p.compare({ p.source.getOffset(123) }, { p.core.getOffset(123) })
    }

    @Test fun daylightRuleComparisonsAndCloneDispatch() {
        val p = Pairing()
        for (uses in listOf(false, true)) for (daylight in listOf(false, true)) {
            p.script.uses = uses; p.script.daylight = daylight
            p.compare({ p.source.dstSavings }, { p.core.getDSTSavings() })
            p.compare({ p.source.observesDaylightTime() }, { p.core.observesDaylightTime() })
            for (other in listOf(null, p.source, Zone(Script()), Zone(Script().apply { raw = 1 }), Zone(Script().apply { this.uses = true }))) {
                p.compare({ p.source.hasSameRules(other) }, { p.core.hasSameRules(other) })
            }
        }
        for (frozen in listOf(false, true)) for (result in listOf(null, p.source, Zone(Script()))) {
            p.script.frozen = frozen; p.script.cloneResult = result
            p.compare({ p.source.clone() }, { p.core.clone() })
            assertSame(if (frozen) p.source else result, p.core.clone())
        }
        p.compare({ p.source.freeze() }, { p.core.freeze() })
        assertEquals(p.source.baseIsFrozen(), p.core.isFrozen())
    }

    @Test fun storedIDEqualityHashAndFrozenSetterPrecedence() {
        for (initialize in listOf(false, true)) {
            val p = Pairing(initialize)
            val other = Zone(Script())
            for (value in listOf(null, "", "stored", "new", "a\uD83D\uDE00z")) for (frozen in listOf(false, true)) {
                p.script.frozen = frozen
                p.compare({ p.source.setID(value) }, { p.core.setID(value) })
                assertEquals(p.source.baseID(), p.core.getID())
                p.compare({ p.source.hashCode() }, { p.core.hashCodeID() })
                for (candidate in listOf(null, p.source, other, DifferentZone(Script()), "stored")) {
                    p.compare({ p.source.equals(candidate) }, { p.core.equalsZone(candidate) })
                }
            }
        }
    }

    companion object {
        private val idField = TimeZone::class.java.getDeclaredField("ID").apply { isAccessible = true }
        private fun outcome(block: () -> Any?): Any? = try { block() } catch (e: Throwable) {
            when (e) {
                is VirtualMachineError -> throw e
                is NullPointerException -> "null"
                is IndexOutOfBoundsException -> "bounds"
                else -> e.javaClass.simpleName + ":" + e.message
            }
        }
    }
}

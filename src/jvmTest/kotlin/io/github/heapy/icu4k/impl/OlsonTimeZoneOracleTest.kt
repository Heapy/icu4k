package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.util.*
import kotlin.test.*
import com.ibm.icu.impl.OlsonTimeZone as JOlson
import com.ibm.icu.util.BasicTimeZone as JBasic
import com.ibm.icu.util.TimeZoneTransition as JTransition

class OlsonTimeZoneOracleTest {
    private fun snapshot(t: TimeZoneTransition?): List<Any?>? = t?.let {
        listOf(it.getTime(), it.getFrom()!!.getName(), it.getFrom()!!.getRawOffset(), it.getFrom()!!.getDSTSavings(), it.getTo()!!.getName(), it.getTo()!!.getRawOffset(), it.getTo()!!.getDSTSavings())
    }
    private fun snapshot(t: JTransition?): List<Any?>? = t?.let {
        listOf(it.time, it.from.name, it.from.rawOffset, it.from.dstSavings, it.to.name, it.to.rawOffset, it.to.dstSavings)
    }
    @Test fun allSystemZonesExposeSourceOffsetsTransitionsAndRules() {
        for (id in TimeZone.getAvailableIDs()) {
            val k = OlsonTimeZone(id); val j = JOlson(id)
            for (time in listOf(-2208988800000L, -1L, 0L, 1710054000000L, 1730613600000L, 4102444800000L)) {
                for (local in listOf(false, true)) {
                    val ko = IntArray(2); val jo = IntArray(2)
                    k.getOffset(time, local, ko); j.getOffset(time, local, jo)
                    assertContentEquals(jo, ko, "$id/$time/$local")
                }
                assertEquals(snapshot(j.getNextTransition(time, true)), snapshot(k.getNextTransition(time, true)), "$id/next/$time")
                assertEquals(snapshot(j.getPreviousTransition(time, false)), snapshot(k.getPreviousTransition(time, false)), "$id/previous/$time")
            }
            assertEquals(j.canonicalID, k.getCanonicalID(), id)
            assertEquals(j.hashCode(), k.hashCode(), id)
            assertEquals(j.timeZoneRules.map { listOf(it.name, it.rawOffset, it.dstSavings, it.isTransitionRule) }, k.getTimeZoneRules().map { listOf(it!!.getName(), it.getRawOffset(), it.getDSTSavings(), it.isTransitionRule()) }, id)
            assertEquals(j.useDaylightTime(), k.useDaylightTime(), id)
            assertEquals(j.observesDaylightTime(), k.observesDaylightTime(), id)
            assertEquals(j.dstSavings, k.getDSTSavings(), id)
        }
    }
    @Test fun localPoliciesAndMutableClonesMatchSource() {
        for (id in listOf("US/Eastern", "Europe/Dublin", "Australia/Lord_Howe", "Pacific/Apia", "Europe/Minsk")) {
            val k = OlsonTimeZone(id); val j = JOlson(id)
            for (time in listOf(1710037800000L, 1730597400000L, 1325246400000L)) {
                for (a in BasicTimeZone.LocalOption.entries) for (b in BasicTimeZone.LocalOption.entries) {
                    val ko = IntArray(2); val jo = IntArray(2)
                    k.getOffsetFromLocal(time, a, b, ko)
                    j.getOffsetFromLocal(time, JBasic.LocalOption.valueOf(a.name), JBasic.LocalOption.valueOf(b.name), jo)
                    assertContentEquals(jo, ko, "$id/$time/$a/$b")
                }
            }
            k.getTimeZoneRules(); j.timeZoneRules; k.freeze(); j.freeze()
            val kc = k.cloneAsThawed(); val jc = j.cloneAsThawed()
            assertEquals(j.hasSameRules(jc), k.hasSameRules(kc)); assertEquals(j == jc, k == kc)
            kc.setID("custom"); jc.id = "custom"
            assertEquals(jc.canonicalID, kc.getCanonicalID()); assertEquals(jc.hashCode(), kc.hashCode())
            val raw = j.rawOffset + 123000; kc.setRawOffset(raw); jc.rawOffset = raw
            assertEquals(jc.rawOffset, kc.getRawOffset()); assertEquals(j.rawOffset, k.getRawOffset())
            assertEquals(j.hasSameRules(jc), k.hasSameRules(kc)); assertEquals(j == jc, k == kc)
        }
    }
    @Test fun virtualDispatchAndSetIdFrozenCheckOrderMatchSource() {
        val kt = arrayListOf<String>(); val jt = arrayListOf<String>()
        val k = object : OlsonTimeZone("Europe/Minsk") {
            override fun isFrozen(): Boolean { kt.add("frozen"); return false }
            override fun getRawOffset(): Int { kt.add("raw"); return 0 }
            override fun useDaylightTime(): Boolean { kt.add("dst"); return false }
        }
        val j = object : JOlson("Europe/Minsk") {
            override fun isFrozen(): Boolean { jt.add("frozen"); return false }
            override fun getRawOffset(): Int { jt.add("raw"); return 0 }
            override fun useDaylightTime(): Boolean { jt.add("dst"); return false }
        }
        k.setID("renamed"); j.id = "renamed"; assertEquals(jt, kt); assertEquals(j.id, k.getID())
        kt.clear(); jt.clear()
        assertEquals(j.hasSameRules(com.ibm.icu.util.SimpleTimeZone(0, "custom")), k.hasSameRules(SimpleTimeZone(0, "custom")))
        assertEquals(jt, kt)
        kt.clear(); jt.clear(); k.getOffset(1, 2024, 0, 1, 2, 0); j.getOffset(1, 2024, 0, 1, 2, 0)
        assertEquals(jt, kt)
    }
}

package io.github.heapy.icu4k.impl.calendar

import io.github.heapy.icu4k.util.ULocale
import kotlin.test.*

class IslamicCalendarCoreTest {
    private class Owner: IslamicCalendarCore.Owner {
        val fields = IntArray(24)
        val trace = ArrayList<String>()
        var failure: String? = null
        var time = 0L
        var newer = 19
        var maximum = 355
        var fieldHook: ((Int)->Unit)? = null
        lateinit var core: IslamicCalendarCore
        init { core = IslamicCalendarCore(this) }
        private fun event(value: String) { trace.add(value); if (failure == value) error(value) }
        override fun newerField(defaultField: Int, alternateField: Int): Int { event("newer:$defaultField:$alternateField"); return newer }
        override fun internalGet(field: Int, defaultValue: Int): Int { event("internalGet:$field"); return fields[field].takeIf { it != 0 } ?: defaultValue }
        override fun internalSet(field: Int, value: Int) { event("internalSet:$field:$value"); fields[field] = value; fieldHook?.invoke(field) }
        override fun internalGetTimeInMillis(): Long { event("internalTime"); return time }
        override fun getTimeInMillis(): Long { event("getTime"); return time }
        override fun setTimeInMillis(value: Long) { event("setTime:$value"); time = value }
        override fun clear() { event("clear"); fields.fill(0) }
        override fun get(field: Int): Int { event("get:$field"); return fields[field] }
        override fun set(field: Int, value: Int) { event("set:$field:$value"); fields[field] = value }
        override fun getActualMaximum(field: Int): Int { event("maximum:$field"); return maximum }
    }
    @Test fun standaloneIcu4jDateSnapshotsForAllFourModes() {
        // Recorded from ICU4J 78.3 in Etc/GMT, independently of the port.
        val days = intArrayOf(1948439,1948440,2408762,2440588,2460381,2515427,2515781)
        val expected = arrayOf(
            arrayOf(intArrayOf(1,0,1,1),intArrayOf(1,0,2,2),intArrayOf(1300,0,2,2),intArrayOf(1389,9,23,289),intArrayOf(1445,8,1,238),intArrayOf(1601,0,2,2),intArrayOf(1602,0,2,2)),
            arrayOf(intArrayOf(0,11,29,354),intArrayOf(1,0,1,1),intArrayOf(1300,0,1,1),intArrayOf(1389,9,22,288),intArrayOf(1445,8,1,237),intArrayOf(1601,0,1,1),intArrayOf(1602,0,1,1)),
            arrayOf(intArrayOf(0,11,29,354),intArrayOf(1,0,1,1),intArrayOf(1300,0,1,1),intArrayOf(1389,9,22,289),intArrayOf(1445,8,1,237),intArrayOf(1601,0,1,1),intArrayOf(1602,0,1,1)),
            arrayOf(intArrayOf(1,0,1,1),intArrayOf(1,0,2,2),intArrayOf(1300,0,2,2),intArrayOf(1389,9,23,289),intArrayOf(1445,8,2,238),intArrayOf(1601,0,2,2),intArrayOf(1602,0,2,2)),
        )
        for ((index,type) in IslamicCalendarCore.CalculationType.entries.withIndex()) {
            val o = Owner(); o.core.setCalculationType(type)
            for ((i,day) in days.withIndex()) {
                o.time = (day - 2440588L) * 86400000L
                o.core.handleComputeFields(day)
                val (year,month,date,dayOfYear) = expected[index][i]
                assertEquals(listOf(0,year,year,month,month,date,dayOfYear),listOf(0,1,19,2,23,5,6).map { o.fields[it] },"$type/$day")
                assertEquals(day-date,o.core.handleComputeMonthStart(year,month,false),"$type/$day/start")
                assertEquals(day-date,o.core.handleComputeMonthStart(year,month,true),"$type/$day/start-used")
            }
        }
    }
    @Test fun civilEpochLeapCycleAndUmalquraBoundaryDifferences() {
        val o = Owner(); val c = o.core
        assertEquals(1948439,c.handleComputeMonthStart(1,0,true))
        for (year in 1..30) assertEquals(year in listOf(2,5,7,10,13,16,18,21,24,26,29),IslamicCalendarCore.civilLeapYear(year))
        assertEquals(30,c.handleGetMonthLength(2,11)); assertEquals(29,c.handleGetMonthLength(1,11))
        // Source length hooks do not normalize month before leap-month detection.
        assertEquals(29,c.handleGetMonthLength(2,23))
        c.setCalculationType(IslamicCalendarCore.CalculationType.ISLAMIC_TBLA)
        assertFalse(c.isCivil()); assertEquals(1948438,c.handleComputeMonthStart(1,0,true))
        c.setCalculationType(IslamicCalendarCore.CalculationType.ISLAMIC_UMALQURA)
        assertEquals(354,c.handleGetYearLength(1300)); assertEquals(355,c.handleGetYearLength(1389))
        assertEquals(354,c.handleGetYearLength(1445))
        for (year in listOf(1299,1601)) {
            val civil = Owner().core
            assertEquals(civil.handleGetYearLength(year),c.handleGetYearLength(year))
            for (month in 0..11) assertEquals(civil.handleComputeMonthStart(year,month,true),c.handleComputeMonthStart(year,month,true))
        }
        for (field in listOf(-1,24)) assertFailsWith<IndexOutOfBoundsException> { c.handleGetLimit(field,0) }
        assertFailsWith<IndexOutOfBoundsException> { c.handleGetLimit(4,0) }
    }
    @Test fun calculationTypeMutationAndCivilTransitionFailures() {
        val o = Owner(); val c = o.core
        o.fields[1] = 1445; o.time = 12345
        c.setCalculationType(IslamicCalendarCore.CalculationType.ISLAMIC_TBLA)
        assertEquals(emptyList(),o.trace); assertEquals(1445,o.fields[1]); assertFalse(c.isCivil())
        c.setCivil(true)
        assertEquals(listOf("getTime","clear","setTime:12345"),o.trace); assertEquals(0,o.fields[1])
        o.trace.clear(); c.setCivil(true); assertTrue(o.trace.isEmpty())
        assertFailsWith<NullPointerException> { c.setCalculationType(null) }
        assertEquals(IslamicCalendarCore.CalculationType.ISLAMIC_CIVIL,c.getCalculationType())
        // The serialized type was already set to null: even an otherwise unchanged
        // civil selection must perform the time-preserving transition.
        c.setCivil(true); assertEquals(listOf("getTime","clear","setTime:12345"),o.trace)
        for (failure in listOf("getTime","clear","setTime:12345")) {
            val p = Owner(); p.time = 12345; p.failure = failure
            assertFailsWith<IllegalStateException> { p.core.setCivil(false) }
            assertEquals(if (failure == "getTime") "islamic-civil" else "islamic",p.core.getType())
            assertEquals(listOf("getTime","clear","setTime:12345").takeWhile { it != failure } + failure,p.trace)
        }
    }
    @Test fun fieldPublicationOrderAndModeChangesDuringCallback() {
        val o = Owner(); val c = o.core
        c.handleComputeFields(2460381)
        assertEquals(listOf("internalTime","internalSet:0:0","internalSet:1:1445","internalSet:19:1445","internalSet:2:8","internalSet:23:8","internalSet:5:1","internalSet:6:237"),o.trace)
        o.trace.clear(); o.fields.fill(-1); o.failure = "internalSet:23:8"
        assertFailsWith<IllegalStateException> { c.handleComputeFields(2460381) }
        assertEquals(8,o.fields[2]); assertEquals(-1,o.fields[23]); assertEquals(-1,o.fields[5])
        o.failure = null; o.fieldHook = { if (it == 1) c.setCalculationType(IslamicCalendarCore.CalculationType.ISLAMIC_TBLA) }
        c.handleComputeFields(2460381)
        // The in-flight CivilAlgorithm continues after the owner's mode mutation.
        assertEquals(1,o.fields[5]); assertEquals("islamic-tbla",c.getType())
    }
    @Test fun localeSelectionRelatedYearsAndTemporalDelegation() {
        val o = Owner(); val c = o.core
        for ((locale,type) in listOf("en_US" to "islamic-civil","ar_SA" to "islamic-civil","ar_SA@calendar=islamic-umalqura" to "islamic-umalqura","en@calendar=islamic-tbla" to "islamic-tbla","en@calendar=islamic-rgsa" to "islamic","en@calendar=islamic-civil" to "islamic-civil")) {
            c.setCalcTypeForLocale(ULocale(locale)); assertEquals(type,c.getType())
        }
        o.fields[19] = 1445; assertEquals(2023,c.getRelatedYear())
        c.setRelatedYear(2024); assertEquals(1446,o.fields[19])
        assertTrue(c.inTemporalLeapYear()); o.maximum = 354; assertFalse(c.inTemporalLeapYear())
        assertEquals(listOf("get:19","set:19:1446","maximum:6","maximum:6"),o.trace)
        o.trace.clear(); assertEquals(1446,c.handleGetExtendedYear()); o.newer = 1; o.fields[1] = 1444
        assertEquals(1444,c.handleGetExtendedYear())
    }
    @Test fun copiesRebindOwnerAndRetainPartialModeState() {
        val source = Owner(); val target = Owner()
        source.time = 17; target.time = 29
        source.core.setCalculationType(IslamicCalendarCore.CalculationType.ISLAMIC_UMALQURA)
        assertFailsWith<NullPointerException> { source.core.setCalculationType(null) }
        target.core = source.core.copyForOwner(target)
        assertEquals("islamic-umalqura",target.core.getType())
        target.core.setCivil(true)
        assertEquals(listOf("getTime","clear","setTime:29"),target.trace)
        assertTrue(source.trace.isEmpty()); assertEquals("islamic-umalqura",source.core.getType())
        assertEquals("islamic-civil",target.core.getType())
    }

}

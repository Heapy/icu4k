@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.impl.calendar

import com.ibm.icu.util.*
import kotlin.random.Random
import kotlin.test.*

class IslamicCalendarCoreOracleTest {
    private class Probe(zone: String = "Etc/GMT"): IslamicCalendar(TimeZone.getTimeZone(zone), ULocale.US) {
        var common = false
        private var core = bind()
        private fun owner() = object: IslamicCalendarCore.Owner {
            override fun newerField(defaultField: Int, alternateField: Int) = this@Probe.newerField(defaultField, alternateField)
            override fun internalGet(field: Int, defaultValue: Int) = this@Probe.internalGet(field, defaultValue)
            override fun internalSet(field: Int, value: Int) = this@Probe.internalSet(field, value)
            override fun internalGetTimeInMillis() = this@Probe.internalGetTimeInMillis()
            override fun getTimeInMillis() = this@Probe.timeInMillis
            override fun setTimeInMillis(value: Long) { this@Probe.timeInMillis = value }
            override fun clear() = this@Probe.clear()
            override fun get(field: Int) = this@Probe.get(field)
            override fun set(field: Int, value: Int) = this@Probe.set(field, value)
            override fun getActualMaximum(field: Int) = this@Probe.getActualMaximum(field)
        }
        private fun bind() = IslamicCalendarCore(owner())
        public override fun clone(): Probe { val copy = super.clone() as Probe; copy.core = core.copyForOwner(copy.owner()); return copy }
        override fun handleComputeMonthStart(year: Int, month: Int, use: Boolean) = if (common) core.handleComputeMonthStart(year, month, use) else super.handleComputeMonthStart(year, month, use)
        override fun handleGetExtendedYear() = if (common) core.handleGetExtendedYear() else super.handleGetExtendedYear()
        override fun handleGetMonthLength(year: Int, month: Int) = if (common) core.handleGetMonthLength(year, month) else super.handleGetMonthLength(year, month)
        override fun handleGetYearLength(year: Int) = if (common) core.handleGetYearLength(year) else super.handleGetYearLength(year)
        override fun handleGetLimit(field: Int, type: Int) = if (common) core.handleGetLimit(field, type) else super.handleGetLimit(field, type)
        override fun handleComputeFields(day: Int) { if (common) core.handleComputeFields(day) else super.handleComputeFields(day) }
        override fun getType() = if (common) core.getType() else super.getType()
        override fun setCalculationType(type: CalculationType?) {
            if (common) core.setCalculationType(type?.let { IslamicCalendarCore.CalculationType.valueOf(it.name) }) else super.setCalculationType(type)
        }
        override fun setCivil(value: Boolean) { if (common) core.setCivil(value) else super.setCivil(value) }
        override fun isCivil() = if (common) core.isCivil() else super.isCivil()
        override fun getCalculationType() = if (common) CalculationType.valueOf(core.getCalculationType().name) else super.getCalculationType()
        override fun inTemporalLeapYear() = if (common) core.inTemporalLeapYear() else super.inTemporalLeapYear()
        fun locale(id: String) {
            if (common) core.setCalcTypeForLocale(io.github.heapy.icu4k.util.ULocale(id))
            else localeCalc.invoke(this, ULocale(id))
        }
        fun related() = if (common) core.getRelatedYear() else relatedYear
        fun related(year: Int) { if (common) core.setRelatedYear(year) else setRelatedYear(year) }
        fun extended() = handleGetExtendedYear()
        fun length(year: Int, month: Int) = handleGetMonthLength(year, month)
        fun yearLength(year: Int) = handleGetYearLength(year)
        fun start(year: Int, month: Int, use: Boolean) = handleComputeMonthStart(year, month, use)
        fun compute(day: Int) = handleComputeFields(day)
        fun limit(field: Int, type: Int) = handleGetLimit(field, type)
    }
    private fun outcome(action: ()->Any?): Any? = try { action() } catch (e: Exception) {
        (if (e is IndexOutOfBoundsException) "bounds" else e.javaClass.name) to if (e is IndexOutOfBoundsException || e is NullPointerException) null else e.message
    }
    private fun compare(source: Probe, target: Probe, context: String, action: (Probe)->Any?) {
        assertEquals(outcome { action(source) }, outcome { action(target) }, context)
        assertEquals((fields.get(source) as IntArray).toList(), (fields.get(target) as IntArray).toList(), context)
        assertEquals((stamps.get(source) as ByteArray).toList(), (stamps.get(target) as ByteArray).toList(), context)
    }
    private fun pair(type: IslamicCalendar.CalculationType, zone: String = "Etc/GMT"): Pair<Probe, Probe> {
        val a = Probe(zone); val b = Probe(zone).apply { common = true }
        for (p in listOf(a, b)) { p.calculationType = type; p.timeInMillis = 0; p.clear() }
        return a to b
    }
    @Test fun allUmalquraTableDaysAndFallbackBoundaries() {
        val (a,b) = pair(IslamicCalendar.CalculationType.ISLAMIC_UMALQURA)
        for (year in 1298..1602) {
            compare(a,b,"year/$year") { it.yearLength(year) }
            for (month in 0..11) {
                compare(a,b,"length/$year/$month") { it.length(year,month) }
                for (use in listOf(false,true)) compare(a,b,"start/$year/$month/$use") { it.start(year,month,use) }
            }
        }
        val first = a.start(1299,0,true)
        val last = a.start(1602,0,true) + 356
        for (day in first..last) compare(a,b,"day/$day") { it.compute(day) }
    }
    @Test fun arithmeticOverflowAndSignedNormalization() {
        for (type in listOf(IslamicCalendar.CalculationType.ISLAMIC_CIVIL, IslamicCalendar.CalculationType.ISLAMIC_TBLA, IslamicCalendar.CalculationType.ISLAMIC_UMALQURA)) {
            val (a,b) = pair(type)
            for (year in listOf(Int.MIN_VALUE,-5000000,-1,0,1,1299,1300,1446,1600,1601,5000000,Int.MAX_VALUE)) {
                compare(a,b,"year/$type/$year") { it.yearLength(year) }
                for (month in listOf(Int.MIN_VALUE,-25,-13,-12,-1,0,1,11,12,25,Int.MAX_VALUE)) {
                    compare(a,b,"length/$type/$year/$month") { it.length(year,month) }
                    compare(a,b,"start/$type/$year/$month") { it.start(year,month,false) }
                }
            }
            for (field in -1..24) for (limit in -1..4) compare(a,b,"limit/$field/$limit") { it.limit(field,limit) }
            for (day in listOf(Int.MIN_VALUE,-1,0,1948438,1948439,1948440,2440588,Int.MAX_VALUE)) compare(a,b,"day/$type/$day") { it.compute(day) }
        }
        val leap = IslamicCalendar::class.java.getDeclaredMethod("civilLeapYear",Int::class.javaPrimitiveType).apply { isAccessible = true }
        val grego = IslamicCalendar::class.java.getDeclaredMethod("gregoYearFromIslamicStart",Int::class.javaPrimitiveType).apply { isAccessible = true }
        val islamic = IslamicCalendar::class.java.getDeclaredMethod("firstIslamicStartYearFromGrego",Int::class.javaPrimitiveType).apply { isAccessible = true }
        val random = Random(78)
        for (year in listOf(Int.MIN_VALUE,Int.MAX_VALUE) + List(5000) { random.nextInt() }) {
            assertEquals(leap.invoke(null,year),IslamicCalendarCore.civilLeapYear(year))
            assertEquals(grego.invoke(null,year),IslamicCalendarCore.gregoYearFromIslamicStart(year))
            assertEquals(islamic.invoke(null,year),IslamicCalendarCore.firstIslamicStartYearFromGrego(year))
        }
    }
    @Test fun astronomicalMonthsAndIntegratedCalendarOperations() {
        for (type in IslamicCalendar.CalculationType.entries) {
            val (a,b) = pair(type)
            for (year in listOf(1,1000,1299,1300,1400,1445,1600,1601,2000)) {
                compare(a,b,"year/$type/$year") { it.yearLength(year) }
                for (month in -13..25) {
                    // Negative keys expose upstream cache bug 006 during later rehash.
                    // Test signed normalization with independent cold caches, preserving the
                    // process-wide warm positive cache for integrated operations.
                    if (type == IslamicCalendar.CalculationType.ISLAMIC && year == 1 && month < 0) {
                        withFreshCaches { compare(a,b,"start/$type/$year/$month") { it.start(year,month,true) } }
                        withFreshCaches { compare(a,b,"length/$type/$year/$month") { it.length(year,month) } }
                    } else {
                        compare(a,b,"start/$type/$year/$month") { it.start(year,month,true) }
                        compare(a,b,"length/$type/$year/$month") { it.length(year,month) }
                    }
                }
            }
            val random = Random(783)
            repeat(1200) { step ->
                val day = random.nextInt(1948440,2700000)
                for (p in listOf(a,b)) p.timeInMillis = (day - 2440588L) * 86400000L + 86399999
                compare(a,b,"compute/$type/$step") { it.compute(day) }
            }
            for (zone in listOf("Etc/GMT","Asia/Riyadh","America/New_York")) {
                val (s,t) = pair(type,zone)
                repeat(250) { step ->
                    val year = random.nextInt(1200,1800); val month = random.nextInt(-12,24)
                    for (p in listOf(s,t)) { p.clear(); p.set(year,month,29) }
                    compare(s,t,"time/$type/$zone/$step") { it.timeInMillis }
                    compare(s,t,"related/$type/$zone/$step") { it.related() }
                    compare(s,t,"leap/$type/$zone/$step") { it.inTemporalLeapYear() }
                    val amount = random.nextInt(-30,31)
                    compare(s,t,"add/$type/$zone/$step") { it.add(2,amount); it.timeInMillis }
                    compare(s,t,"roll/$type/$zone/$step") { it.roll(2,amount); it.timeInMillis }
                }
            }
        }
    }
    @Test fun modeChangesRetainOrInvalidateFieldsLikeSource() {
        val (a,b) = pair(IslamicCalendar.CalculationType.ISLAMIC_CIVIL)
        for (p in listOf(a,b)) { p.set(1445,8,29); p.timeInMillis }
        for (type in IslamicCalendar.CalculationType.entries) {
            compare(a,b,"set/$type") { it.calculationType = type }
            compare(a,b,"get/$type") { listOf(it.type,it.calculationType,it.isCivil) }
            compare(a,b,"fields/$type") { it.get(2) }
            for (civil in listOf(true,true,false,false)) {
                compare(a,b,"civil/$type/$civil") { it.isCivil = civil }
                compare(a,b,"after/$type/$civil") { listOf(it.timeInMillis,it.type,it.get(1),it.get(2),it.get(5)) }
            }
        }
        for (locale in listOf("ar_SA","en_US","fa_IR","en@calendar=islamic-rgsa","en@calendar=islamic-umalqura","en@calendar=islamic-tbla","en@calendar=islamic-civil")) {
            compare(a,b,"locale/$locale") { it.locale(locale); listOf(it.type,it.calculationType,it.isCivil) }
        }
        compare(a,b,"null") { it.calculationType = null }
        compare(a,b,"null-get") { it.calculationType }
        compare(a,b,"recover-null") { it.isCivil = false }
        for (year in listOf(-1000,0,1976,1977,2000,2138,Int.MIN_VALUE,Int.MAX_VALUE)) compare(a,b,"related/$year") { it.related(year) }
        for (p in listOf(a,b)) { p.clear(); p.set(1,1440); p.set(19,1441) }
        compare(a,b,"extended") { it.extended() }
    }
    private fun withFreshCaches(action: ()->Unit) {
        val source = sourceCache.get(null); val target = commonCache.get(null)
        try {
            sourceCache.set(null, com.ibm.icu.impl.CalendarCache())
            commonCache.set(null, io.github.heapy.icu4k.impl.CalendarCache())
            action()
        } finally { sourceCache.set(null, source); commonCache.set(null, target) }
    }
    companion object {
        private val localeCalc = IslamicCalendar::class.java.getDeclaredMethod("setCalcTypeForLocale",ULocale::class.java).apply { isAccessible = true }
        private val sourceCache = IslamicCalendar::class.java.getDeclaredField("cache").apply { isAccessible = true }
        private val commonCache = IslamicCalendarCore::class.java.getDeclaredField("cache").apply { isAccessible = true }
        private val fields = Calendar::class.java.getDeclaredField("fields").apply { isAccessible = true }
        private val stamps = Calendar::class.java.getDeclaredField("stamp").apply { isAccessible = true }
    }
}

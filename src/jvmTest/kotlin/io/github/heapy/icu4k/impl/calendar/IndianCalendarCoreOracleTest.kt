package io.github.heapy.icu4k.impl.calendar

import com.ibm.icu.util.*
import kotlin.random.Random
import kotlin.test.*

class IndianCalendarCoreOracleTest {
    private class Probe(zone: String="Etc/GMT") : IndianCalendar(TimeZone.getTimeZone(zone),ULocale.US) {
        var common=false
        var trace=ArrayList<String>()
        private var ready=false
        private var core=bind()
        init { ready=true }
        private fun bind()=IndianCalendarCore(object : IndianCalendarCore.Owner {
            override fun newerField(defaultField: Int,alternateField: Int)=this@Probe.newerField(defaultField,alternateField)
            override fun internalGet(field: Int,defaultValue: Int)=this@Probe.internalGet(field,defaultValue)
            override fun internalSet(field: Int,value: Int)=this@Probe.internalSet(field,value)
            override fun getGregorianYear()=this@Probe.getGregorianYear()
            override fun computeGregorianMonthStart(year: Int,month: Int)=this@Probe.computeGregorianMonthStart(year,month)
            override fun baseGetYearLength(year: Int)=baseYear(year)
        })
        private fun baseYear(year: Int)=super.handleGetYearLength(year)
        override fun newerField(defaultField: Int,alternateField: Int): Int { if(ready) trace.add("newer:$defaultField:$alternateField"); return super.newerField(defaultField,alternateField) }
        override fun computeGregorianMonthStart(year: Int,month: Int): Int {
            if(ready) trace.add("gregorian:$year:$month")
            return if(common) CalendarDateCore.computeGregorianMonthStart(year,month) else super.computeGregorianMonthStart(year,month)
        }
        override fun handleComputeMonthStart(year: Int,month: Int,use: Boolean): Int {
            if(ready) trace.add("start:$year:$month:$use")
            return if(common) core.handleComputeMonthStart(year,month,use) else super.handleComputeMonthStart(year,month,use)
        }
        override fun handleGetExtendedYear()=if(common) core.handleGetExtendedYear() else super.handleGetExtendedYear()
        override fun handleGetMonthLength(year: Int,month: Int)=if(common) core.handleGetMonthLength(year,month) else super.handleGetMonthLength(year,month)
        override fun handleGetYearLength(year: Int)=if(common) core.handleGetYearLength(year) else super.handleGetYearLength(year)
        override fun handleComputeFields(day: Int) { if(common) core.handleComputeFields(day) else super.handleComputeFields(day) }
        override fun handleGetLimit(field: Int,type: Int)=if(common) core.handleGetLimit(field,type) else super.handleGetLimit(field,type)
        override fun getType()=if(common) core.getType() else super.getType()
        fun extended()=handleGetExtendedYear()
        fun monthLength(year: Int,month: Int)=handleGetMonthLength(year,month)
        fun yearLength(year: Int)=handleGetYearLength(year)
        fun start(year: Int,month: Int,use: Boolean)=handleComputeMonthStart(year,month,use)
        fun limit(field: Int,type: Int)=handleGetLimit(field,type)
        fun compute(day: Int,year: Int) { gregorianYearField.setInt(this,year); handleComputeFields(day) }
        fun relatedDifference()=if(common) core.getRelatedYearDifference() else getRelatedYearDifference()
        public override fun clone(): Probe { val copy=super.clone() as Probe; copy.core=copy.bind(); return copy }
    }
    private fun outcome(action: ()->Any?): Any? = try { action() } catch(e: VirtualMachineError) { throw e } catch(e: Exception) {
        (if(e is IndexOutOfBoundsException) "bounds" else e.javaClass.name) to if(e is IndexOutOfBoundsException || e is NullPointerException) null else e.message
    }
    private fun compare(source: Probe,target: Probe,context: String,action: (Probe)->Any?) {
        source.trace.clear(); target.trace.clear()
        assertEquals(outcome { action(source) },outcome { action(target) },context)
        assertEquals((fields.get(source) as IntArray).toList(),(fields.get(target) as IntArray).toList(),context)
        assertEquals((stamps.get(source) as ByteArray).toList(),(stamps.get(target) as ByteArray).toList(),context)
        assertEquals(source.trace,target.trace,context)
    }
    @Test fun limitsYearSelectionAndMonthNormalization() {
        val source=Probe(); val target=Probe().apply { common=true }
        for(p in listOf(source,target)) { p.timeInMillis=0; p.clear() }
        for(field in -1..24) for(type in -2..5) compare(source,target,"limit/$field/$type") { it.limit(field,type) }
        compare(source,target,"default") { it.extended() }
        compare(source,target,"related") { it.relatedDifference() }
        for(year in listOf(Int.MIN_VALUE,-5000000,-4800,-78,-1,0,1,1900,1922,1946,2000,5000000,Int.MAX_VALUE)) {
            for(era in -1..2) for(ext in listOf(false,true)) {
                for(p in listOf(source,target)) { p.clear(); p.set(0,era); p.set(1,year); if(ext) p.set(19,year) }
                compare(source,target,"extended/$year/$era/$ext") { it.extended() }
            }
            compare(source,target,"yearLength/$year") { it.yearLength(year) }
            for(month in listOf(Int.MIN_VALUE,-25,-13,-12,-11,-1,0,1,5,6,11,12,25,Int.MAX_VALUE)) {
                compare(source,target,"length/$year/$month") { it.monthLength(year,month) }
                for(use in listOf(false,true)) compare(source,target,"start/$year/$month/$use") { it.start(year,month,use) }
            }
        }
    }
    @Test fun cachedGregorianYearAndJulianDayExtremes() {
        val source=Probe(); val target=Probe().apply { common=true }
        for(p in listOf(source,target)) { p.timeInMillis=0; p.clear() }
        for(year in listOf(Int.MIN_VALUE,-5000000,-4713,-1,0,1,1970,2000,2023,2024,5000000,Int.MAX_VALUE)) {
            val start=CalendarDateCore.computeGregorianMonthStart(year,0)+1
            for(day in listOf(Int.MIN_VALUE,-1,0,start,start+79,start+80,start+81,start+365,Int.MAX_VALUE)) {
                compare(source,target,"fields/$year/$day") { it.compute(day,year) }
            }
        }
    }
    @Test fun integratedDateArithmeticTemporalAndCloning() {
        for(zone in listOf("Etc/GMT","Asia/Kolkata","America/New_York")) {
            val source=Probe(zone); val target=Probe(zone).apply { common=true }
            for(p in listOf(source,target)) { p.timeInMillis=0; p.clear() }
            val random=Random(1409)
            repeat(1400) { step ->
                val op=random.nextInt(9); val year=random.nextInt(-5500,5500); val month=random.nextInt(-24,24)
                val field=listOf(0,1,2,3,5,6,8,10,11,17,19,20,23)[random.nextInt(13)]; val amount=random.nextInt(-40,41)
                val code=listOf(null,"M01","M12","M13","M01L")[random.nextInt(5)]
                compare(source,target,"$zone/$step/$op/$field/$amount") { p ->
                    when(op) {
                        0 -> { p.clear(); p.set(year,month,20); p.timeInMillis }
                        1 -> { p.add(field,amount); p.timeInMillis }
                        2 -> { p.roll(field,amount); p.timeInMillis }
                        3 -> p.getActualMaximum(field)
                        4 -> p.temporalMonthCode
                        5 -> { p.temporalMonthCode=code; p.timeInMillis }
                        6 -> p.clone().apply { add(2,1) }.timeInMillis
                        7 -> p.relatedYear
                        else -> p.type
                    }
                }
            }
        }
    }
    companion object {
        private val fields=Calendar::class.java.getDeclaredField("fields").apply { isAccessible=true }
        private val stamps=Calendar::class.java.getDeclaredField("stamp").apply { isAccessible=true }
        private val gregorianYearField=Calendar::class.java.getDeclaredField("gregorianYear").apply { isAccessible=true }
    }
}

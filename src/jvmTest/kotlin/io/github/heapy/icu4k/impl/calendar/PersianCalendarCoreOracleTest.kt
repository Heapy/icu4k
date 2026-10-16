@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.impl.calendar

import com.ibm.icu.util.*
import java.lang.reflect.InvocationTargetException
import kotlin.random.Random
import kotlin.test.*

class PersianCalendarCoreOracleTest {
    private class Probe(zone: String="Etc/GMT") : PersianCalendar(TimeZone.getTimeZone(zone),ULocale.US) {
        var common=false
        var trace=ArrayList<String>()
        private var ready=false
        private var core=bind()
        init { ready=true }
        private fun bind()=PersianCalendarCore(object : PersianCalendarCore.Owner {
            override fun newerField(defaultField: Int,alternateField: Int)=this@Probe.newerField(defaultField,alternateField)
            override fun internalGet(field: Int,defaultValue: Int)=this@Probe.internalGet(field,defaultValue)
            override fun internalSet(field: Int,value: Int)=this@Probe.internalSet(field,value)
        })
        override fun newerField(defaultField: Int,alternateField: Int): Int { if(ready) trace.add("newer:$defaultField:$alternateField"); return super.newerField(defaultField,alternateField) }
        override fun handleComputeMonthStart(year: Int,month: Int,use: Boolean)=if(common) core.handleComputeMonthStart(year,month,use) else super.handleComputeMonthStart(year,month,use)
        override fun handleGetExtendedYear()=if(common) core.handleGetExtendedYear() else super.handleGetExtendedYear()
        override fun handleGetMonthLength(year: Int,month: Int)=if(common) core.handleGetMonthLength(year,month) else super.handleGetMonthLength(year,month)
        override fun handleGetYearLength(year: Int)=if(common) core.handleGetYearLength(year) else super.handleGetYearLength(year)
        override fun handleComputeFields(day: Int) { if(common) core.handleComputeFields(day) else super.handleComputeFields(day) }
        override fun handleGetLimit(field: Int,type: Int)=if(common) core.handleGetLimit(field,type) else super.handleGetLimit(field,type)
        override fun getType()=if(common) core.getType() else super.getType()
        fun extended()=handleGetExtendedYear()
        fun length(year: Int,month: Int)=handleGetMonthLength(year,month)
        fun yearLength(year: Int)=handleGetYearLength(year)
        fun start(year: Int,month: Int,use: Boolean)=handleComputeMonthStart(year,month,use)
        fun limit(field: Int,type: Int)=handleGetLimit(field,type)
        fun compute(day: Int)=handleComputeFields(day)
        fun relatedDifference()=if(common) core.getRelatedYearDifference() else getRelatedYearDifference()
        public override fun clone(): Probe { val copy=super.clone() as Probe; copy.core=copy.bind(); return copy }
    }
    private fun outcome(action: ()->Any?): Any? = try { action() } catch(e: Exception) {
        val cause=if(e is InvocationTargetException) e.targetException else e
        if(cause is VirtualMachineError) throw cause
        if(cause !is Exception) throw cause
        (if(cause is IndexOutOfBoundsException) "bounds" else cause.javaClass.name) to if(cause is IndexOutOfBoundsException || cause is NullPointerException) null else cause.message
    }
    private fun compare(source: Probe,target: Probe,context: String,action: (Probe)->Any?) {
        source.trace.clear(); target.trace.clear()
        assertEquals(outcome { action(source) },outcome { action(target) },context)
        assertEquals((fields.get(source) as IntArray).toList(),(fields.get(target) as IntArray).toList(),context)
        assertEquals((stamps.get(source) as ByteArray).toList(),(stamps.get(target) as ByteArray).toList(),context)
        assertEquals(source.trace,target.trace,context)
    }
    @Test fun correctionTableAndAllYearBoundaries() {
        val source=Probe(); val target=Probe().apply { common=true }
        for(p in listOf(source,target)) { p.timeInMillis=0; p.clear() }
        for(year in (-100..3150).toList()+listOf(Int.MIN_VALUE,Int.MAX_VALUE,-5000000,5000000)) {
            assertEquals(leap.invoke(null,year),PersianCalendarCore.isLeapYear(year),"leap/$year")
            assertEquals(first.invoke(null,year),PersianCalendarCore.firstJulianOfYear(year),"first/$year")
            compare(source,target,"yearLength/$year") { it.yearLength(year) }
            val start=source.start(year,0,true)+1
            for(offset in listOf(-1,0,1,364,365,366)) compare(source,target,"fields/$year/$offset") { it.compute(start+offset) }
        }
        val random=Random(2027)
        repeat(10000) {
            val year=random.nextInt()
            assertEquals(leap.invoke(null,year),PersianCalendarCore.isLeapYear(year),"leap/$year")
            assertEquals(first.invoke(null,year),PersianCalendarCore.firstJulianOfYear(year),"first/$year")
        }
    }
    @Test fun limitsYearSelectionAndExtremeMonthNormalization() {
        val source=Probe(); val target=Probe().apply { common=true }
        for(p in listOf(source,target)) { p.timeInMillis=0; p.clear() }
        for(field in -1..24) for(type in -2..5) compare(source,target,"limit/$field/$type") { it.limit(field,type) }
        compare(source,target,"default") { it.extended() }
        compare(source,target,"related") { it.relatedDifference() }
        for(year in listOf(Int.MIN_VALUE,-5000000,-1,0,1,1403,1502,1503,2987,2988,5000000,Int.MAX_VALUE)) {
            for(era in -1..2) for(ext in listOf(false,true)) {
                for(p in listOf(source,target)) { p.clear(); p.set(0,era); p.set(1,year); if(ext) p.set(19,year) }
                compare(source,target,"extended/$year/$era/$ext") { it.extended() }
            }
            for(month in listOf(Int.MIN_VALUE,-25,-13,-12,-1,0,5,6,11,12,25,Int.MAX_VALUE)) {
                compare(source,target,"length/$year/$month") { it.length(year,month) }
                for(use in listOf(false,true)) compare(source,target,"start/$year/$month/$use") { it.start(year,month,use) }
            }
        }
        for(day in listOf(Int.MIN_VALUE,-0x7f000000,-1,0,1948319,1948320,2440588,0x7f000000,Int.MAX_VALUE)) compare(source,target,"raw/$day") { it.compute(day) }
    }
    @Test fun integratedArithmeticRelatedYearsTemporalAndCloning() {
        for(zone in listOf("Etc/GMT","Asia/Tehran","America/New_York")) {
            val source=Probe(zone); val target=Probe(zone).apply { common=true }
            for(p in listOf(source,target)) { p.timeInMillis=0; p.clear() }
            val random=Random(1997)
            repeat(1600) { step ->
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
        private val leap=PersianCalendar::class.java.getDeclaredMethod("isLeapYear",Int::class.javaPrimitiveType).apply { isAccessible=true }
        private val first=PersianCalendar::class.java.getDeclaredMethod("firstJulianOfYear",Int::class.javaPrimitiveType).apply { isAccessible=true }
    }
}

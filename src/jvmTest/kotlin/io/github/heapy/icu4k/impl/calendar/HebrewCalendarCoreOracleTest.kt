@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.impl.calendar

import com.ibm.icu.util.*
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.lang.reflect.InvocationTargetException
import kotlin.random.Random
import kotlin.test.*

class HebrewCalendarCoreOracleTest {
    private class Probe(zone: String="Etc/GMT") : HebrewCalendar(TimeZone.getTimeZone(zone),ULocale.US) {
        var common=false
        private var core: HebrewCalendarCore=bind()
        private fun bind(): HebrewCalendarCore=HebrewCalendarCore(object : HebrewCalendarCore.Owner {
            override fun get(field: Int)=this@Probe.get(field)
            override fun set(field: Int,value: Int)=this@Probe.set(field,value)
            override fun pinField(field: Int)=this@Probe.pinField(field)
            override fun baseAdd(field: Int,amount: Int) { baseAdd.invokeWithArguments(this@Probe,field,amount) }
            override fun baseRoll(field: Int,amount: Int) { baseRoll.invokeWithArguments(this@Probe,field,amount) }
            override fun handleGetYearLength(year: Int)=this@Probe.handleGetYearLength(year)
            override fun handleGetExtendedYear()=this@Probe.handleGetExtendedYear()
            override fun internalGetMonth()=this@Probe.internalGetMonth()
            override fun baseInternalGetMonth()=baseMonth.invokeWithArguments(this@Probe) as Int
            override fun baseValidateField(field: Int) { baseValidate.invokeWithArguments(this@Probe,field) }
            override fun resolveMonthField()=this@Probe.resolveFields(CalendarFieldsCore.MONTH_PRECEDENCE)
            override fun internalGet(field: Int)=this@Probe.internalGet(field)
            override fun internalGet(field: Int,defaultValue: Int)=this@Probe.internalGet(field,defaultValue)
            override fun newerField(defaultField: Int,alternateField: Int)=this@Probe.newerField(defaultField,alternateField)
            override fun internalSet(field: Int,value: Int)=this@Probe.internalSet(field,value)
        })
        override fun add(field: Int,amount: Int) { if(common) core.add(field,amount) else super.add(field,amount) }
        override fun roll(field: Int,amount: Int) { if(common) core.roll(field,amount) else super.roll(field,amount) }
        override fun handleGetExtendedYear()=if(common) core.handleGetExtendedYear() else super.handleGetExtendedYear()
        override fun handleGetMonthLength(year: Int,month: Int)=if(common) core.handleGetMonthLength(year,month) else super.handleGetMonthLength(year,month)
        override fun handleGetYearLength(year: Int)=if(common) core.handleGetYearLength(year) else super.handleGetYearLength(year)
        override fun handleComputeMonthStart(year: Int,month: Int,use: Boolean)=if(common) core.handleComputeMonthStart(year,month,use) else super.handleComputeMonthStart(year,month,use)
        override fun handleComputeFields(day: Int) { if(common) core.handleComputeFields(day) else super.handleComputeFields(day) }
        override fun handleGetLimit(field: Int,type: Int)=if(common) core.handleGetLimit(field,type) else super.handleGetLimit(field,type)
        override fun validateField(field: Int) { if(common) core.validateField(field) else super.validateField(field) }
        override fun internalGetMonth()=if(common) core.internalGetMonth() else super.internalGetMonth()
        override fun getType()=if(common) core.getType() else super.getType()
        override fun inTemporalLeapYear()=if(common) core.inTemporalLeapYear() else super.inTemporalLeapYear()
        override fun getTemporalMonthCode()=if(common) core.getTemporalMonthCode() else super.getTemporalMonthCode()
        override fun setTemporalMonthCode(code: String?) { if(common) core.setTemporalMonthCode(code) else super.setTemporalMonthCode(code) }
        fun length(year: Int,month: Int)=handleGetMonthLength(year,month)
        fun yearLength(year: Int)=handleGetYearLength(year)
        fun start(year: Int,month: Int)=handleComputeMonthStart(year,month,true)
        fun compute(day: Int)=handleComputeFields(day)
        fun limit(field: Int,type: Int)=handleGetLimit(field,type)
        fun relatedDifference()=if(common) core.getRelatedYearDifference() else getRelatedYearDifference()
        public override fun clone(): Probe { val copy=super.clone() as Probe; copy.core=copy.bind(); return copy }
    }
    private fun outcome(action: ()->Any?): Any?=try { action() } catch(e: Exception) {
        val cause=if(e is InvocationTargetException) e.targetException else e
        if(cause is VirtualMachineError) throw cause
        if(cause !is Exception) throw cause
        (if(cause is IndexOutOfBoundsException) "bounds" else cause.javaClass.name) to if(cause is IndexOutOfBoundsException || cause is NullPointerException) null else cause.message
    }
    private fun compare(a: Probe,b: Probe,context: String,action: (Probe)->Any?) {
        assertEquals(outcome { action(a) },outcome { action(b) },context)
        assertEquals((fields.get(a) as IntArray).toList(),(fields.get(b) as IntArray).toList(),context)
        assertEquals((stamps.get(a) as ByteArray).toList(),(stamps.get(b) as ByteArray).toList(),context)
    }
    @Test fun yearGeometryMonthNormalizationAndLimits() {
        val a=Probe(); val b=Probe().apply { common=true }
        for(p in listOf(a,b)) p.timeInMillis=0
        for(field in -1..24) for(type in -2..5) compare(a,b,"limit/$field/$type") { it.limit(field,type) }
        compare(a,b,"related") { it.relatedDifference() }
        for(year in 4000..7000) {
            assertEquals(HebrewCalendar.isLeapYear(year),HebrewCalendarCore.isLeapYear(year))
            compare(a,b,"year/$year") { it.yearLength(year) }
            for(month in 0..12) {
                compare(a,b,"length/$year/$month") { it.length(year,month) }
                compare(a,b,"start/$year/$month") { it.start(year,month) }
            }
        }
        for(year in listOf(1,19,5779,5780,5784)) for(month in listOf(-1000,-236,-235,-14,-1,13,26,235,236,1000)) {
            compare(a,b,"length/$year/$month") { it.length(year,month) }
            compare(a,b,"start/$year/$month") { it.start(year,month) }
        }
    }
    @Test fun coldCacheSignedYearArithmeticMatchesSource() {
        val oldSource=sourceCache.get(null); val oldTarget=targetCache.get(null)
        try {
            // Cold tables isolate arithmetic from the source cache's negative-key probe cycles.
            for(year in listOf(Int.MIN_VALUE,Int.MIN_VALUE+1,-5000000,-1000,-19,-3,-2,-1,0,1,19,5000000,Int.MAX_VALUE)) {
                sourceCache.set(null,com.ibm.icu.impl.CalendarCache()); targetCache.set(null,io.github.heapy.icu4k.impl.CalendarCache())
                assertEquals(outcome { sourceStart.invoke(null,year) },outcome { HebrewCalendarCore.startOfYear(year) },"start/$year")
                assertEquals(HebrewCalendar.isLeapYear(year),HebrewCalendarCore.isLeapYear(year),"leap/$year")
            }
        } finally { sourceCache.set(null,oldSource); targetCache.set(null,oldTarget) }
    }
    @Test fun adarArithmeticZeroAmountsTemporalAndCloneOwners() {
        for(zone in listOf("Etc/GMT","Asia/Jerusalem","America/New_York")) {
            val a=Probe(zone); val b=Probe(zone).apply { common=true }
            for(p in listOf(a,b)) p.timeInMillis=0
            for(year in listOf(5783,5784)) for(month in 0..12) for(amount in listOf(-27,-13,-1,0,1,13,27)) for(roll in listOf(false,true)) {
                for(p in listOf(a,b)) { p.clear(); p.set(year,month,30) }
                compare(a,b,"$zone/$year/$month/$amount/$roll") { if(roll) it.roll(2,amount) else it.add(2,amount); it.timeInMillis }
            }
            val random=Random(5779)
            repeat(1800) { step ->
                val op=random.nextInt(9); val year=random.nextInt(4500,6500); val month=random.nextInt(-14,27)
                val field=listOf(0,1,2,3,5,6,8,17,19,23)[random.nextInt(10)]; val amount=random.nextInt(-27,28)
                val code=listOf(null,"M01","M05L","M06","M12","M13","bad")[random.nextInt(7)]
                compare(a,b,"$zone/$step/$op/$field/$code") { p -> when(op) {
                    0 -> { p.clear(); p.set(year,month,20); p.timeInMillis }
                    1 -> { p.add(field,amount); p.timeInMillis }
                    2 -> { p.roll(field,amount); p.timeInMillis }
                    3 -> p.getActualMaximum(field)
                    4 -> p.temporalMonthCode
                    5 -> { p.temporalMonthCode=code; p.timeInMillis }
                    6 -> p.clone().apply { add(2,1) }.timeInMillis
                    7 -> p.inTemporalLeapYear()
                    else -> { p.set(23,month); p.timeInMillis }
                } }
            }
        }
    }
    companion object {
        private val lookup=MethodHandles.privateLookupIn(HebrewCalendar::class.java,MethodHandles.lookup())
        private val intType=Int::class.javaPrimitiveType!!
        private fun base(name: String,result: Class<*>,vararg args: Class<*>)=lookup.findSpecial(Calendar::class.java,name,MethodType.methodType(result,args.toList()),HebrewCalendar::class.java)
        private val baseAdd=base("add",Void.TYPE,intType,intType)
        private val baseRoll=base("roll",Void.TYPE,intType,intType)
        private val baseMonth=base("internalGetMonth",intType)
        private val baseValidate=base("validateField",Void.TYPE,intType)
        private val fields=Calendar::class.java.getDeclaredField("fields").apply { isAccessible=true }
        private val stamps=Calendar::class.java.getDeclaredField("stamp").apply { isAccessible=true }
        private val sourceCache=HebrewCalendar::class.java.getDeclaredField("cache").apply { isAccessible=true }
        private val targetCache=HebrewCalendarCore::class.java.getDeclaredField("cache").apply { isAccessible=true }
        private val sourceStart=HebrewCalendar::class.java.getDeclaredMethod("startOfYear",intType).apply { isAccessible=true }
    }
}

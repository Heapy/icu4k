package io.github.heapy.icu4k.impl.calendar

import com.ibm.icu.util.*
import java.lang.reflect.InvocationTargetException
import kotlin.random.Random
import kotlin.test.*

class CECalendarCoreOracleTest {
    private class Port(locale: String,private val coptic: Boolean) : Calendar(TimeZone.getTimeZone("Etc/GMT"),ULocale(locale)) {
        private var c: CopticCalendarCore=CopticCalendarCore(cOwner())
        private var e: EthiopicCalendarCore=EthiopicCalendarCore(eOwner())
        private var ce: CECalendarCore=CECalendarCore(ceOwner())
        init { if(!coptic) e.setCalcTypeForLocale(io.github.heapy.icu4k.util.ULocale(locale)) }
        private fun cOwner(): CopticCalendarCore.Owner=object : CopticCalendarCore.Owner {
            override fun newerField(defaultField: Int,alternateField: Int)=this@Port.newerField(defaultField,alternateField)
            override fun internalGet(field: Int,defaultValue: Int)=this@Port.internalGet(field,defaultValue)
        }
        private fun eOwner(): EthiopicCalendarCore.Owner=object : EthiopicCalendarCore.Owner {
            override fun newerField(defaultField: Int,alternateField: Int)=this@Port.newerField(defaultField,alternateField)
            override fun internalGet(field: Int,defaultValue: Int)=this@Port.internalGet(field,defaultValue)
            override fun isAmeteAlemEra()=e.isAmeteAlemEra()
            override fun setAmeteAlemEra(value: Boolean)=e.setAmeteAlemEra(value)
            override fun baseGetLimit(field: Int,type: Int)=ce.handleGetLimit(field,type)
        }
        private fun baseCode(): String = super.getTemporalMonthCode()
        private fun baseCode(code: String?) = super.setTemporalMonthCode(code)
        private fun ceOwner(): CECalendarCore.Owner=object : CECalendarCore.Owner {
            override fun getJDEpochOffset()=if(coptic) c.getJDEpochOffset() else e.getJDEpochOffset()
            override fun extendedYearToEra(year: Int)=if(coptic) c.extendedYearToEra(year) else e.extendedYearToEra(year)
            override fun extendedYearToYear(year: Int)=if(coptic) c.extendedYearToYear(year) else e.extendedYearToYear(year)
            override fun internalSet(field: Int,value: Int)=this@Port.internalSet(field,value)
            override fun get(field: Int)=this@Port.get(field)
            override fun set(field: Int,value: Int)=this@Port.set(field,value)
            override fun baseGetTemporalMonthCode()=baseCode()
            override fun baseSetTemporalMonthCode(code: String?)=baseCode(code)
        }
        override fun handleGetExtendedYear()=if(coptic) c.handleGetExtendedYear() else e.handleGetExtendedYear()
        override fun handleComputeFields(day: Int)=ce.handleComputeFields(day)
        override fun handleComputeMonthStart(year: Int,month: Int,use: Boolean)=ce.handleComputeMonthStart(year,month,use)
        override fun handleGetMonthLength(year: Int,month: Int)=ce.handleGetMonthLength(year,month)
        override fun handleGetLimit(field: Int,type: Int)=if(coptic) ce.handleGetLimit(field,type) else e.handleGetLimit(field,type)
        override fun getType()=if(coptic) c.getType() else e.getType()
        override fun getTemporalMonthCode()=ce.getTemporalMonthCode()
        override fun setTemporalMonthCode(code: String?)=ce.setTemporalMonthCode(code)
        override fun getRelatedYearDifference()=if(coptic) c.getRelatedYearDifference() else e.getRelatedYearDifference()
        override fun isEra0CountingBackward()=if(coptic) c.isEra0CountingBackward() else super.isEra0CountingBackward()
        fun setMode(value: Boolean) { if(!coptic) e.setAmeteAlemEra(value) }
        public override fun clone(): Port {
            val copy=super.clone() as Port
            copy.c=CopticCalendarCore(copy.cOwner()); copy.e=e.copyFor(copy.eOwner()); copy.ce=CECalendarCore(copy.ceOwner())
            return copy
        }
    }
    private class Probe(val calendar: Calendar) {
        fun extended()=extended.invoke(calendar) as Int
        fun compute(day: Int) { compute.invoke(calendar,day) }
        fun start(year: Int,month: Int,use: Boolean)=start.invoke(calendar,year,month,use) as Int
        fun length(year: Int,month: Int)=length.invoke(calendar,year,month) as Int
        fun limit(field: Int,type: Int)=limit.invoke(calendar,field,type) as Int
        fun relatedDifference()=related.invoke(calendar) as Int
    }
    private fun outcome(action: () -> Any?): Any? = try { action() } catch(e: Exception) {
        val cause=if(e is InvocationTargetException) e.targetException else e
        if(cause is VirtualMachineError) throw cause
        if(cause !is Exception) throw cause
        (if(cause is IndexOutOfBoundsException) "bounds" else cause.javaClass.name) to
            if(cause is NullPointerException || cause is IndexOutOfBoundsException) null else cause.message
    }
    private fun compare(source: Probe,target: Probe,context: String,action: (Probe)->Any?) {
        assertEquals(outcome { action(source) },outcome { action(target) },context)
        assertEquals((fields.get(source.calendar) as IntArray).toList(),(fields.get(target.calendar) as IntArray).toList(),context)
        assertEquals((stamps.get(source.calendar) as ByteArray).toList(),(stamps.get(target.calendar) as ByteArray).toList(),context)
    }
    @Test fun staticConversionsAndPartialOutputArrays() {
        for(year in listOf(Long.MIN_VALUE,Int.MIN_VALUE.toLong(),-5000000L,-4L,-1L,0L,1L,3L,4L,1970L,5000000L,Int.MAX_VALUE.toLong(),Long.MAX_VALUE)) {
            for(month in listOf(Int.MIN_VALUE,-27,-13,-1,0,12,13,27,Int.MAX_VALUE)) for(day in listOf(Int.MIN_VALUE,0,1,30,Int.MAX_VALUE)) {
                assertEquals(CopticCalendar.copticToJD(year,month,day),CopticCalendarCore.copticToJD(year,month,day))
                assertEquals(EthiopicCalendar.EthiopicToJD(year,month,day),EthiopicCalendarCore.EthiopicToJD(year,month,day))
                for(epoch in listOf(Int.MIN_VALUE,-285019,0,1723856,1824665,Int.MAX_VALUE)) {
                    assertEquals(toJD.invoke(null,year,month,day,epoch),CECalendarCore.ceToJD(year,month,day,epoch))
                }
            }
        }
        for(day in listOf(Int.MIN_VALUE,-0x7f000000,-1,0,1460,1461,1824665,2440588,Int.MAX_VALUE)) for(epoch in listOf(Int.MIN_VALUE,-285019,0,1723856,1824665,Int.MAX_VALUE)) for(size in -1..4) {
            val a=if(size<0) null else IntArray(size) { 123 }
            val b=a?.copyOf()
            assertEquals(outcome { fromJD.invoke(null,day,epoch,a); Unit },outcome { CECalendarCore.jdToCE(day,epoch,b) })
            assertEquals(a?.toList(),b?.toList())
        }
    }
    @Test fun limitsYearPrecedenceMonthLengthsAndComputedFields() {
        for(factory in factories()) {
            val source=factory(); val original=factory().calendar; val target=Probe(Port(if(original.type=="ethiopic-amete-alem") "en_US@calendar=ethiopic-amete-alem" else "en_US",original is CopticCalendar))
            for(p in listOf(source,target)) { p.calendar.timeInMillis=0; p.calendar.clear() }
            for(field in -1..24) for(type in -2..5) compare(source,target,"limit/$field/$type") { it.limit(field,type) }
            compare(source,target,"default") { it.extended() }
            compare(source,target,"related") { it.relatedDifference() }
            for(year in listOf(Int.MIN_VALUE,-5500,-4,-1,0,1,3,4,1970,5000000,Int.MAX_VALUE)) {
                for(era in -1..2) for(order in 0..2) {
                    for(p in listOf(source,target)) { p.calendar.clear(); p.calendar.set(0,era); p.calendar.set(1,year); if(order==1) p.calendar.set(19,year); if(order==2) p.calendar.set(0,era) }
                    compare(source,target,"year/$year/$era/$order") { it.extended() }
                }
                for(month in listOf(Int.MIN_VALUE,-14,-13,-1,0,11,12,13,25,Int.MAX_VALUE)) {
                    compare(source,target,"length/$year/$month") { it.length(year,month) }
                    for(use in listOf(false,true)) compare(source,target,"start/$year/$month/$use") { it.start(year,month,use) }
                }
            }
            for(day in listOf(Int.MIN_VALUE,-0x7f000000,-1,0,1460,1461,1723856,1824665,2440588,Int.MAX_VALUE)) compare(source,target,"day/$day") { it.compute(day) }
        }
    }
    @Test fun integratedArithmeticTemporalAndEraModeCloning() {
        for(factory in factories()) {
            val source=factory(); val original=factory().calendar; val target=Probe(Port(if(original.type=="ethiopic-amete-alem") "en_US@calendar=ethiopic-amete-alem" else "en_US",original is CopticCalendar))
            for(p in listOf(source,target)) { p.calendar.timeInMillis=0; p.calendar.clear() }
            val random=Random(1907)
            repeat(900) { step ->
                val op=random.nextInt(9); val field=listOf(0,1,2,3,5,6,8,11,17,19,20,23)[random.nextInt(12)]
                val amount=random.nextInt(-30,31); val year=random.nextInt(-6000,6000); val month=random.nextInt(-26,26)
                val code=listOf(null,"M01","M12","M13","M13L","bad")[random.nextInt(6)]
                compare(source,target,"$step/$op/$field/$amount/$code") { p ->
                    val c=p.calendar
                    when(op) {
                        0 -> { c.clear(); c.set(year,month,5); c.timeInMillis }
                        1 -> { c.add(field,amount); c.timeInMillis }
                        2 -> { c.roll(field,amount); c.timeInMillis }
                        3 -> c.getActualMaximum(field)
                        4 -> c.temporalMonthCode
                        5 -> { c.temporalMonthCode=code; c.timeInMillis }
                        6 -> { val copy=c.clone() as Calendar; copy.add(2,1); copy.timeInMillis }
                        7 -> { if(c is EthiopicCalendar) c.isAmeteAlemEra=amount>0; if(c is Port) c.setMode(amount>0); c.type }
                        else -> c.getRelatedYear()
                    }
                }
            }
        }
    }
    companion object {
        private fun factories(): List<()->Probe> = listOf({ Probe(CopticCalendar(TimeZone.getTimeZone("Etc/GMT"),ULocale.US)) },{ Probe(EthiopicCalendar(TimeZone.getTimeZone("Etc/GMT"),ULocale.US)) },{ Probe(EthiopicCalendar(TimeZone.getTimeZone("Etc/GMT"),ULocale("en_US@calendar=ethiopic-amete-alem"))) })
        private val ceClass=Class.forName("com.ibm.icu.util.CECalendar")
        private fun method(name: String,vararg args: Class<*>)=Calendar::class.java.getDeclaredMethod(name,*args).apply { isAccessible=true }
        private val intType=Int::class.javaPrimitiveType!!
        private val extended=method("handleGetExtendedYear")
        private val compute=method("handleComputeFields",intType)
        private val start=method("handleComputeMonthStart",intType,intType,Boolean::class.javaPrimitiveType!!)
        private val length=method("handleGetMonthLength",intType,intType)
        private val limit=method("handleGetLimit",intType,intType)
        private val related=method("getRelatedYearDifference")
        private val toJD=ceClass.getDeclaredMethod("ceToJD",Long::class.javaPrimitiveType,Int::class.javaPrimitiveType,Int::class.javaPrimitiveType,Int::class.javaPrimitiveType).apply { isAccessible=true }
        private val fromJD=ceClass.getDeclaredMethod("jdToCE",Int::class.javaPrimitiveType,Int::class.javaPrimitiveType,IntArray::class.java).apply { isAccessible=true }
        private val fields=Calendar::class.java.getDeclaredField("fields").apply { isAccessible=true }
        private val stamps=Calendar::class.java.getDeclaredField("stamp").apply { isAccessible=true }
    }
}

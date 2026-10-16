@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.impl.calendar

import com.ibm.icu.util.*
import io.github.heapy.icu4k.impl.CalType
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import kotlin.random.Random
import kotlin.test.*

class JapaneseCalendarCoreOracleTest {
    private class Probe : JapaneseCalendar(TimeZone.getTimeZone("Etc/GMT"),ULocale.US) {
        var common=false
        private var core: JapaneseCalendarCore=bind()
        private fun bind(): JapaneseCalendarCore=JapaneseCalendarCore(rules,JapaneseCalendar.CURRENT_ERA,object : JapaneseCalendarCore.Owner {
            override fun newerField(defaultField: Int,alternateField: Int)=this@Probe.newerField(defaultField,alternateField)
            override fun internalGet(field: Int)=this@Probe.internalGet(field)
            override fun internalGet(field: Int,defaultValue: Int)=this@Probe.internalGet(field,defaultValue)
            override fun internalSet(field: Int,value: Int)=this@Probe.internalSet(field,value)
            override fun gregorianComputeFields(julianDay: Int) { baseFields.invokeWithArguments(this@Probe,julianDay) }
            override fun baseDefaultMonth(year: Int)=baseMonth.invokeWithArguments(this@Probe,year) as Int
            override fun baseDefaultDay(year: Int,month: Int)=baseDay.invokeWithArguments(this@Probe,year,month) as Int
            override fun baseGetLimit(field: Int,type: Int)=baseLimit.invokeWithArguments(this@Probe,field,type) as Int
            override fun baseGetActualMaximum(field: Int)=baseMaximum.invokeWithArguments(this@Probe,field) as Int
            override fun get(field: Int)=this@Probe.get(field)
            override fun handleGetLimit(field: Int,type: Int)=this@Probe.handleGetLimit(field,type)
        })
        override fun handleGetExtendedYear()=if(common) core.handleGetExtendedYear() else super.handleGetExtendedYear()
        override fun getDefaultMonthInYear(year: Int)=if(common) core.getDefaultMonthInYear(year) else super.getDefaultMonthInYear(year)
        override fun getDefaultDayInMonth(year: Int,month: Int)=if(common) core.getDefaultDayInMonth(year,month) else super.getDefaultDayInMonth(year,month)
        override fun handleComputeFields(day: Int) { if(common) core.handleComputeFields(day) else super.handleComputeFields(day) }
        override fun handleGetLimit(field: Int,type: Int)=if(common) core.handleGetLimit(field,type) else super.handleGetLimit(field,type)
        override fun getActualMaximum(field: Int)=if(common) core.getActualMaximum(field) else super.getActualMaximum(field)
        override fun getType()=if(common) core.getType() else super.getType()
        override fun haveDefaultCentury()=if(common) core.haveDefaultCentury() else super.haveDefaultCentury()
        override fun isEra0CountingBackward()=if(common) core.isEra0CountingBackward() else super.isEra0CountingBackward()
        fun extended()=handleGetExtendedYear()
        fun defaultMonth(year: Int)=getDefaultMonthInYear(year)
        fun defaultDay(year: Int,month: Int)=getDefaultDayInMonth(year,month)
        fun limit(field: Int,type: Int)=handleGetLimit(field,type)
        public override fun clone(): Probe { val copy=super.clone() as Probe; copy.core=copy.bind(); return copy }
    }
    private fun outcome(action: ()->Any?): Any?=try { action() } catch(e: VirtualMachineError) { throw e } catch(e: Exception) {
        (if(e is IndexOutOfBoundsException) "bounds" else e.javaClass.name) to if(e is IndexOutOfBoundsException || e is NullPointerException) null else e.message
    }
    private fun compare(a: Probe,b: Probe,context: String,action: (Probe)->Any?) {
        assertEquals(outcome { action(a) },outcome { action(b) },context)
        assertEquals((fields.get(a) as IntArray).toList(),(fields.get(b) as IntArray).toList(),context)
        assertEquals((stamps.get(a) as ByteArray).toList(),(stamps.get(b) as ByteArray).toList(),context)
    }
    @Test fun everyEraDefaultsLimitsAndBoundaryDates() {
        val a=Probe(); val b=Probe().apply { common=true }
        for(p in listOf(a,b)) { p.timeInMillis=0; p.clear() }
        for(field in -1..24) for(type in -2..5) compare(a,b,"limit/$field/$type") { it.limit(field,type) }
        compare(a,b,"default") { it.extended() }
        for(era in (-1..rules.getMaxEraCode()+1).toList()+listOf(Int.MIN_VALUE,Int.MAX_VALUE)) {
            val date=try { rules.getStartDate(era,null) } catch(_: IllegalArgumentException) { intArrayOf(1970,1,1) }
            for(p in listOf(a,b)) { p.clear(); p.set(0,era); p.set(1,1) }
            compare(a,b,"year/$era") { it.extended() }
            compare(a,b,"month/$era") { it.defaultMonth(date[0]) }
            compare(a,b,"day/$era") { it.defaultDay(date[0],date[1]-1) }
            compare(a,b,"max/$era") { it.getActualMaximum(1) }
            if(era>=0 && era<=rules.getMaxEraCode()) for(delta in -1..1) {
                val time=(GregorianCalendar(TimeZone.getTimeZone("Etc/GMT"),ULocale.US).apply { clear(); set(date[0],date[1]-1,date[2]); add(5,delta) }).timeInMillis
                compare(a,b,"boundary/$era/$delta") { it.timeInMillis=time; (0..23).map { f -> it.get(f) } }
            }
        }
    }
    @Test fun arithmeticFieldPrecedenceAndCloneOwners() {
        val a=Probe(); val b=Probe().apply { common=true }
        for(p in listOf(a,b)) { p.timeInMillis=0; p.clear() }
        val random=Random(2019)
        repeat(2500) { step ->
            val op=random.nextInt(7); val era=random.nextInt(0,rules.getMaxEraCode()+1); val year=random.nextInt(-10,100)
            val field=listOf(0,1,2,3,5,6,17,19,23)[random.nextInt(9)]; val delta=random.nextInt(-20,21)
            compare(a,b,"$step/$op/$era/$year/$field") { p -> when(op) {
                0 -> { p.clear(); p.set(0,era); p.set(1,year); p.timeInMillis }
                1 -> { p.add(field,delta); p.timeInMillis }
                2 -> { p.roll(field,delta); p.timeInMillis }
                3 -> p.getActualMaximum(field)
                4 -> p.clone().apply { add(2,1) }.timeInMillis
                5 -> { p.set(19,year); p.extended() }
                else -> p.haveDefaultCentury()
            } }
        }
    }
    companion object {
        private val rules=EraRulesCore.getInstance(CalType.JAPANESE,JapaneseCalendar.enableTentativeEra(),object : EraRulesCore.Owner {
            override fun currentTimeMillis()=System.currentTimeMillis()
            override fun defaultZoneOffset(time: Long)=TimeZone.getDefault().getOffset(time)
        })
        private val lookup=MethodHandles.privateLookupIn(JapaneseCalendar::class.java,MethodHandles.lookup())
        private val intType=Int::class.javaPrimitiveType!!
        private fun base(name: String,result: Class<*>,vararg args: Class<*>)=lookup.findSpecial(GregorianCalendar::class.java,name,MethodType.methodType(result,args.toList()),JapaneseCalendar::class.java)
        private val baseFields=base("handleComputeFields",Void.TYPE,intType)
        private val baseMonth=base("getDefaultMonthInYear",intType,intType)
        private val baseDay=base("getDefaultDayInMonth",intType,intType,intType)
        private val baseLimit=base("handleGetLimit",intType,intType,intType)
        private val baseMaximum=base("getActualMaximum",intType,intType)
        private val fields=Calendar::class.java.getDeclaredField("fields").apply { isAccessible=true }
        private val stamps=Calendar::class.java.getDeclaredField("stamp").apply { isAccessible=true }
    }
}

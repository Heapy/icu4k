package io.github.heapy.icu4k.impl.calendar

import com.ibm.icu.util.*
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import kotlin.random.Random
import kotlin.test.*

class GregorianVariantCoreOracleTest {
    private interface Variant {
        val calendar: Calendar
        var common: Boolean
        val trace: ArrayList<String>
        fun extended(): Int
        fun compute(day: Int)
        fun limit(field: Int,type: Int): Int
    }
    private class GregorianBase(type: Class<*>) {
        private val lookup=MethodHandles.privateLookupIn(type,MethodHandles.lookup())
        private val fields=lookup.findSpecial(GregorianCalendar::class.java,"handleComputeFields",MethodType.methodType(Void.TYPE,Int::class.javaPrimitiveType),type)
        private val limits=lookup.findSpecial(GregorianCalendar::class.java,"handleGetLimit",MethodType.methodType(Int::class.javaPrimitiveType,Int::class.javaPrimitiveType,Int::class.javaPrimitiveType),type)
        private val month=lookup.findSpecial(GregorianCalendar::class.java,"handleComputeMonthStart",MethodType.methodType(Int::class.javaPrimitiveType,Int::class.javaPrimitiveType,Int::class.javaPrimitiveType,Boolean::class.javaPrimitiveType),type)
        fun fields(p: Any,day: Int) { fields.invokeWithArguments(p,day) }
        fun limit(p: Any,field: Int,type: Int) = limits.invokeWithArguments(p,field,type) as Int
        fun month(p: Any,year: Int,month: Int,use: Boolean) = this.month.invokeWithArguments(p,year,month,use) as Int
    }
    private class Buddhist(zone: String) : BuddhistCalendar(TimeZone.getTimeZone(zone),ULocale.US),Variant {
        override val calendar: Calendar get()=this
        override var common=false
        override var trace=ArrayList<String>()
        private var ready=false
        private var core=bind()
        init { ready=true }
        private fun bind()=BuddhistCalendarCore(object : BuddhistCalendarCore.Owner {
            override fun newerField(defaultField: Int,alternateField: Int)=this@Buddhist.newerField(defaultField,alternateField)
            override fun internalGet(field: Int)=this@Buddhist.internalGet(field)
            override fun internalGet(field: Int,defaultValue: Int)=this@Buddhist.internalGet(field,defaultValue)
            override fun internalSet(field: Int,value: Int)=this@Buddhist.internalSet(field,value)
            override fun gregorianComputeFields(julianDay: Int)=buddhistBase.fields(this@Buddhist,julianDay)
            override fun gregorianComputeMonthStart(year: Int,month: Int,useMonth: Boolean)=buddhistBase.month(this@Buddhist,year,month,useMonth)
            override fun gregorianGetLimit(field: Int,limitType: Int)=buddhistBase.limit(this@Buddhist,field,limitType)
        })
        override fun newerField(defaultField: Int,alternateField: Int): Int { if(ready) trace.add("newer:$defaultField:$alternateField"); return super.newerField(defaultField,alternateField) }
        override fun handleGetExtendedYear()=if(common) core.handleGetExtendedYear() else super.handleGetExtendedYear()
        override fun handleComputeFields(day: Int) { if(common) core.handleComputeFields(day) else super.handleComputeFields(day) }
        override fun handleComputeMonthStart(year: Int,month: Int,use: Boolean)=if(common) core.handleComputeMonthStart(year,month,use) else super.handleComputeMonthStart(year,month,use)
        override fun handleGetLimit(field: Int,type: Int)=if(common) core.handleGetLimit(field,type) else super.handleGetLimit(field,type)
        override fun isEra0CountingBackward()=if(common) core.isEra0CountingBackward() else super.isEra0CountingBackward()
        override fun getType()=if(common) core.getType() else super.getType()
        override fun extended()=handleGetExtendedYear()
        override fun compute(day: Int)=handleComputeFields(day)
        override fun limit(field: Int,type: Int)=handleGetLimit(field,type)
        public override fun clone(): Buddhist { val copy=super.clone() as Buddhist; copy.core=copy.bind(); return copy }
    }
    private class Taiwan(zone: String) : TaiwanCalendar(TimeZone.getTimeZone(zone),ULocale.US),Variant {
        override val calendar: Calendar get()=this
        override var common=false
        override var trace=ArrayList<String>()
        private var ready=false
        private var core=bind()
        init { ready=true }
        private fun bind()=TaiwanCalendarCore(object : TaiwanCalendarCore.Owner {
            override fun newerField(defaultField: Int,alternateField: Int)=this@Taiwan.newerField(defaultField,alternateField)
            override fun internalGet(field: Int)=this@Taiwan.internalGet(field)
            override fun internalGet(field: Int,defaultValue: Int)=this@Taiwan.internalGet(field,defaultValue)
            override fun internalSet(field: Int,value: Int)=this@Taiwan.internalSet(field,value)
            override fun gregorianComputeFields(julianDay: Int)=taiwanBase.fields(this@Taiwan,julianDay)
            override fun gregorianGetLimit(field: Int,limitType: Int)=taiwanBase.limit(this@Taiwan,field,limitType)
        })
        override fun newerField(defaultField: Int,alternateField: Int): Int { if(ready) trace.add("newer:$defaultField:$alternateField"); return super.newerField(defaultField,alternateField) }
        override fun handleGetExtendedYear()=if(common) core.handleGetExtendedYear() else super.handleGetExtendedYear()
        override fun handleComputeFields(day: Int) { if(common) core.handleComputeFields(day) else super.handleComputeFields(day) }
        override fun handleGetLimit(field: Int,type: Int)=if(common) core.handleGetLimit(field,type) else super.handleGetLimit(field,type)
        override fun getType()=if(common) core.getType() else super.getType()
        override fun extended()=handleGetExtendedYear()
        override fun compute(day: Int)=handleComputeFields(day)
        override fun limit(field: Int,type: Int)=handleGetLimit(field,type)
        public override fun clone(): Taiwan { val copy=super.clone() as Taiwan; copy.core=copy.bind(); return copy }
    }
    private fun outcome(action: () -> Any?): Any? = try { action() } catch(e: VirtualMachineError) { throw e } catch(e: Exception) {
        e.javaClass.let { if(e is IndexOutOfBoundsException) "bounds" else it.name } to if(e is IndexOutOfBoundsException || e is NullPointerException) null else e.message
    }
    private fun compare(source: Variant,target: Variant,context: String,action: (Variant) -> Any?) {
        source.trace.clear(); target.trace.clear()
        assertEquals(outcome { action(source) },outcome { action(target) },context)
        assertEquals((fields.get(source.calendar) as IntArray).toList(),(fields.get(target.calendar) as IntArray).toList(),context)
        assertEquals((stamps.get(source.calendar) as ByteArray).toList(),(stamps.get(target.calendar) as ByteArray).toList(),context)
        assertEquals(source.trace,target.trace,context)
    }
    @Test fun limitsExtendedYearsAndRawGregorianFieldConversion() {
        for(factory in listOf<(String)->Variant>({ Buddhist(it) },{ Taiwan(it) })) {
            val source=factory("Etc/GMT"); val target=factory("Etc/GMT").apply { common=true }
            for(p in listOf(source,target)) { p.calendar.timeInMillis=0; p.calendar.clear() }
            for(field in -1..24) for(type in -2..5) compare(source,target,"limit/$field/$type") { it.limit(field,type) }
            compare(source,target,"default") { it.extended() }
            for(year in listOf(Int.MIN_VALUE,-5000000,-543,-1,0,1,1911,1912,1970,2513,5000000,Int.MAX_VALUE)) {
                for(era in listOf(-1,0,1,2)) for(order in 0..3) {
                    for(p in listOf(source,target)) {
                        p.calendar.clear()
                        when(order) {
                            0 -> { p.calendar.set(0,era); p.calendar.set(1,year) }
                            1 -> { p.calendar.set(0,era); p.calendar.set(1,year); p.calendar.set(19,year) }
                            2 -> { p.calendar.set(19,year); p.calendar.set(1,year); p.calendar.set(0,era) }
                            else -> { p.calendar.set(1,year); p.calendar.set(19,year); p.calendar.set(0,era) }
                        }
                    }
                    compare(source,target,"year/$year/$era/$order") { it.extended() }
                }
            }
            for(day in listOf(Int.MIN_VALUE,-0x7f000000,-1,0,1721426,2299160,2299161,2419403,2440588,0x7f000000,Int.MAX_VALUE)) compare(source,target,"day/$day") { it.compute(day) }
        }
    }
    @Test fun integratedDateArithmeticAndCloneState() {
        for(factory in listOf<(String)->Variant>({ Buddhist(it) },{ Taiwan(it) })) for(zone in listOf("Etc/GMT","America/New_York","Asia/Bangkok")) {
            val source=factory(zone); val target=factory(zone).apply { common=true }
            for(p in listOf(source,target)) { p.calendar.timeInMillis=0; p.calendar.clear() }
            val random=Random(1703)
            repeat(700) { step ->
                val op=random.nextInt(7); val field=listOf(0,1,2,3,5,6,10,11,17,19,20,23)[random.nextInt(12)]
                val amount=random.nextInt(-50,51); val year=random.nextInt(-2500,3500); val month=random.nextInt(-24,24)
                compare(source,target,"$zone/$step/$op/$field/$amount") { v ->
                    val c=v.calendar
                    when(op) {
                        0 -> { c.clear(); c.set(year,month,28); c.timeInMillis }
                        1 -> { c.add(field,amount); c.timeInMillis }
                        2 -> { c.roll(field,amount); c.timeInMillis }
                        3 -> c.getActualMaximum(field)
                        4 -> c.get(field)
                        5 -> { val copy=c.clone(); assertNotSame(c,copy); (copy as Calendar).apply { add(2,1) }.timeInMillis }
                        else -> c.type
                    }
                }
            }
        }
    }
    companion object {
        private val buddhistBase=GregorianBase(BuddhistCalendar::class.java)
        private val taiwanBase=GregorianBase(TaiwanCalendar::class.java)
        private val fields=Calendar::class.java.getDeclaredField("fields").apply { isAccessible=true }
        private val stamps=Calendar::class.java.getDeclaredField("stamp").apply { isAccessible=true }
    }
}

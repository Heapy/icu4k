@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.impl.calendar

import com.ibm.icu.util.*
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.lang.reflect.InvocationTargetException
import kotlin.random.Random
import kotlin.test.*

class ChineseCalendarCoreOracleTest {
    private class Probe(val dangi: Boolean = false, zone: String = "Etc/GMT"): ChineseCalendar(TimeZone.getTimeZone(zone),ULocale.US,if (dangi) korea else china) {
        var common = false
        private var core: ChineseCalendarCore = bind()
        private fun bind(): ChineseCalendarCore = if (dangi) DangiCalendarCore.create(owner()) else ChineseCalendarCore(owner())
        private fun copyOwner(p: Probe) = object: ChineseCalendarCore.Copy {
            override fun set(field: Int,value: Int) = p.set(field,value)
            override fun get(field: Int) = p.get(field)
            override fun roll(field: Int,amount: Int) = p.roll(field,amount)
            override fun setLenient(value: Boolean) { p.isLenient = value }
            override fun prepareGetActual(field: Int,minimum: Boolean) = p.prepareGetActual(field,minimum)
        }
        private fun owner(): ChineseCalendarCore.Owner = object: ChineseCalendarCore.Owner {
            override fun set(field: Int,value: Int) = this@Probe.set(field,value)
            override fun get(field: Int) = this@Probe.get(field)
            override fun roll(field: Int,amount: Int) = this@Probe.roll(field,amount)
            override fun setLenient(value: Boolean) { this@Probe.isLenient = value }
            override fun prepareGetActual(field: Int,minimum: Boolean) = this@Probe.prepareGetActual(field,minimum)
            override fun internalGet(field: Int) = this@Probe.internalGet(field)
            override fun internalGet(field: Int,defaultValue: Int) = this@Probe.internalGet(field,defaultValue)
            override fun internalSet(field: Int,value: Int) = this@Probe.internalSet(field,value)
            override fun newestStamp(first: Int,last: Int,best: Int) = this@Probe.newestStamp(first,last,best)
            override fun getStamp(field: Int) = this@Probe.getStamp(field)
            override fun resolveFields(table: Array<Array<IntArray>>) = this@Probe.resolveFields(table)
            override fun computeGregorianFields(julianDay: Int) = this@Probe.computeGregorianFields(julianDay)
            override fun getGregorianYear() = this@Probe.gregorianYear
            override fun getGregorianMonth() = this@Probe.gregorianMonth
            override fun complete() = this@Probe.complete()
            override fun getActualMaximum(field: Int): Int = this@Probe.getActualMaximum(field)
            override fun superGetActualMaximum(field: Int) = baseMax.invokeWithArguments(this@Probe,field) as Int
            override fun superAdd(field: Int,amount: Int) { baseAdd.invokeWithArguments(this@Probe,field,amount) }
            override fun superRoll(field: Int,amount: Int) { baseRoll.invokeWithArguments(this@Probe,field,amount) }
            override fun superGetTemporalMonthCode() = baseCode.invokeWithArguments(this@Probe) as String
            override fun superSetTemporalMonthCode(value: String?) { baseSetCode.invokeWithArguments(this@Probe,value) }
            override fun cloneCalendar() = copyOwner(this@Probe.clone())
        }
        override fun handleComputeMonthStart(year: Int,month: Int,use: Boolean) = if (common) core.handleComputeMonthStart(year,month,use) else super.handleComputeMonthStart(year,month,use)
        override fun handleGetExtendedYear() = if (common) core.handleGetExtendedYear() else super.handleGetExtendedYear()
        override fun handleGetMonthLength(year: Int,month: Int) = if (common) core.handleGetMonthLength(year,month) else super.handleGetMonthLength(year,month)
        override fun handleGetLimit(field: Int,type: Int) = if (common) core.handleGetLimit(field,type) else super.handleGetLimit(field,type)
        override fun handleComputeFields(day: Int) { if (common) core.handleComputeFields(day) else super.handleComputeFields(day) }
        override fun getFieldResolutionTable() = if (common) core.getFieldResolutionTable() else super.getFieldResolutionTable()
        override fun add(field: Int,amount: Int) { if (common) core.add(field,amount) else super.add(field,amount) }
        override fun roll(field: Int,amount: Int) { if (common) core.roll(field,amount) else super.roll(field,amount) }
        override fun internalGetMonth() = if (common) core.internalGetMonth() else super.internalGetMonth()
        override fun internalGetMonth(defaultValue: Int) = if (common) core.internalGetMonth(defaultValue) else super.internalGetMonth(defaultValue)
        override fun getActualMaximum(field: Int): Int = if (common) core.getActualMaximum(field) else super.getActualMaximum(field)
        override fun inTemporalLeapYear() = if (common) core.inTemporalLeapYear() else super.inTemporalLeapYear()
        override fun getTemporalMonthCode() = if (common) core.getTemporalMonthCode() else super.getTemporalMonthCode()
        override fun setTemporalMonthCode(code: String?) { if (common) core.setTemporalMonthCode(code) else super.setTemporalMonthCode(code) }
        override fun getType() = if (dangi) DangiCalendarCore.getType() else if (common) core.getType() else super.getType()
        override fun haveDefaultCentury() = if (common) core.haveDefaultCentury() else super.haveDefaultCentury()
        public override fun clone(): Probe { val copy = super.clone() as Probe; copy.core = core.copyForOwner(copy.owner()); return copy }
        fun length(year: Int,month: Int) = handleGetMonthLength(year,month)
        fun start(year: Int,month: Int,use: Boolean) = handleComputeMonthStart(year,month,use)
        fun limit(field: Int,type: Int) = handleGetLimit(field,type)
        fun extended() = handleGetExtendedYear()
        fun metadata() = listOf(gregorianYear,gregorianMonth,gregorianDayOfMonth,gregorianDayOfYear)
        fun compute(day: Int) { computeGregorianFields(day); handleComputeFields(day) }
        fun engine() = core
    }
    private fun outcome(action: ()->Any?): Any? = try { action() } catch (e: Exception) {
        val cause = if (e is InvocationTargetException) e.targetException else e
        if (cause is VirtualMachineError) throw cause
        if (cause !is Exception) throw cause
        (if (cause is IndexOutOfBoundsException) "bounds" else cause.javaClass.name) to if (cause is IndexOutOfBoundsException || cause is NullPointerException) null else cause.message
    }
    private fun pair(dangi: Boolean,zone: String = "Etc/GMT"): Pair<Probe,Probe> {
        val a = Probe(dangi,zone); val b = Probe(dangi,zone).apply { common = true }
        for (p in listOf(a,b)) { p.timeInMillis = 0; p.clear() }
        return a to b
    }
    private fun compare(a: Probe,b: Probe,context: String,action: (Probe)->Any?) {
        assertEquals(outcome { action(a) },outcome { action(b) },context)
        assertEquals((fields.get(a) as IntArray).toList(),(fields.get(b) as IntArray).toList(),context)
        assertEquals((stamps.get(a) as ByteArray).toList(),(stamps.get(b) as ByteArray).toList(),context)
        assertEquals(a.metadata(),b.metadata(),context)
    }
    @Test fun astronomicalPrimitivesAndDangiTransitionOffsets() {
        for (dangi in listOf(false,true)) {
            val (a,b) = pair(dangi)
            val core = b.engine()
            for (year in 1600..2200) {
                assertEquals(winter.invoke(a,year),core.winterSolstice(year),"winter/$dangi/$year")
                assertEquals(newYear.invoke(a,year),core.newYear(year),"newYear/$dangi/$year")
            }
            for (year in listOf(-1000,-2,-1,0,1,1000)) {
                val coldSource = Probe(dangi)
                val coldTarget = Probe(dangi).engine()
                assertEquals(winter.invoke(coldSource,year),coldTarget.winterSolstice(year),"cold-winter/$dangi/$year")
                assertEquals(newYear.invoke(coldSource,year),coldTarget.newYear(year),"cold-newYear/$dangi/$year")
            }
            val randomDays = Random(2033)
            val synodic = method("synodicMonthsBetween",intType,intType)
            repeat(10000) {
                val first = randomDays.nextInt(); val last = randomDays.nextInt()
                assertEquals(synodic.invoke(a,first,last),core.synodicMonthsBetween(first,last))
            }
            for (day in -28000..21000 step 17) {
                assertEquals(moon.invoke(a,day,true),core.newMoonNear(day,true),"moon+/$dangi/$day")
                assertEquals(moon.invoke(a,day,false),core.newMoonNear(day,false),"moon-/$dangi/$day")
                assertEquals(term.invoke(a,day),core.majorSolarTerm(day),"term/$dangi/$day")
            }
            for (day in listOf(Int.MIN_VALUE,-1,0,1,Int.MAX_VALUE)) assertEquals(daysToMillis.invoke(a,day),core.daysToMillis(day))
            for (millis in listOf(Long.MIN_VALUE,Long.MAX_VALUE,-1,0,1,946684800000L)) assertEquals(millisToDays.invoke(a,millis),core.millisToDays(millis))
        }
        val random = Random(1912)
        for (millis in listOf(Long.MIN_VALUE,Long.MAX_VALUE) + List(1000) { random.nextLong(-4000000000000,1000000000000) }) assertEquals(korea.getOffset(millis),DangiCalendarCore.astronomicalOffset(millis))
        var transition = (korea as BasicTimeZone).getNextTransition(Long.MIN_VALUE,false)
        while (transition != null) {
            for (delta in listOf(-1L,0L,1L)) assertEquals(korea.getOffset(transition.time+delta),DangiCalendarCore.astronomicalOffset(transition.time+delta))
            transition = korea.getNextTransition(transition.time,false)
        }
    }
    @Test fun leapMonthGeometryLimitsAndFieldResolution() {
        for (dangi in listOf(false,true)) {
            val (a,b) = pair(dangi)
            for (field in -1..24) for (type in -1..4) compare(a,b,"limit/$field/$type") { it.limit(field,type) }
            for (year in listOf(1813,1896,1897,1898,1911,1912,2023,2024,2033,2034)) {
                for (month in -13..24) for (leap in listOf(0,1,2)) {
                    for (p in listOf(a,b)) p.set(22,leap)
                    compare(a,b,"start/$dangi/$year/$month/$leap") { it.start(year,month,true) }
                    compare(a,b,"start-unused/$dangi/$year/$month/$leap") { it.start(year,month,false) }
                    compare(a,b,"length/$dangi/$year/$month/$leap") { it.length(year,month) }
                }
                val first = a.start(year,0,false)+1
                for (day in first-31..first+400) compare(a,b,"fields/$dangi/$year/$day") { it.compute(day) }
            }
            for (era in listOf(Int.MIN_VALUE,-1,0,1,78,Int.MAX_VALUE)) for (year in listOf(Int.MIN_VALUE,0,1,60,61,Int.MAX_VALUE)) {
                for (p in listOf(a,b)) { p.clear(); p.set(0,era); p.set(1,year) }
                compare(a,b,"extended/$era/$year") { it.extended() }
                for (p in listOf(a,b)) p.set(19,1234)
                compare(a,b,"extended-explicit/$era/$year") { it.extended() }
            }
        }
    }
    @Test fun integratedAddRollOrdinalMonthsTemporalAndClones() {
        val random = Random(2033)
        for (dangi in listOf(false,true)) for (zone in listOf("Etc/GMT","Asia/Shanghai","America/New_York")) {
            val (a,b) = pair(dangi,zone)
            repeat(1000) { step ->
                val year = listOf(1813,1897,1912,2023,2024,2033,2034)[random.nextInt(7)]
                val month = random.nextInt(13); val dom = random.nextInt(28,31)
                for (p in listOf(a,b)) { p.clear(); p.set(19,year); p.set(23,month); p.set(5,dom) }
                compare(a,b,"initial/$dangi/$zone/$step") { it.timeInMillis }
                val op = random.nextInt(8); val amount = random.nextInt(-26,27)
                val field = listOf(2,23,1,5,6)[random.nextInt(5)]
                val code = listOf(null,"M01","M12","M13","M00L","M02L","M11L","M12L","M13L")[random.nextInt(9)]
                compare(a,b,"operation/$dangi/$zone/$step/$op/$field/$amount/$code") { p -> when (op) {
                    0 -> { p.add(field,amount); p.timeInMillis }
                    1 -> { p.roll(field,amount); p.timeInMillis }
                    2 -> p.getActualMaximum(listOf(2,5,6,23)[randomForField(step)])
                    3 -> p.temporalMonthCode
                    4 -> { p.temporalMonthCode = code; p.timeInMillis }
                    5 -> p.inTemporalLeapYear()
                    6 -> p.clone().apply { add(2,amount) }.timeInMillis
                    else -> listOf(p.type,p.haveDefaultCentury())
                } }
            }
        }
    }
    @Test fun cloneCachesAndDatePrecedenceMatchSource() {
        val (a,b) = pair(false)
        for (p in listOf(a,b)) { p.compute(2463954); p.get(2) }
        val ac = a.clone(); val bc = b.clone()
        for (name in listOf("winterSolsticeCache","newYearCache")) {
            val sf = ChineseCalendar::class.java.getDeclaredField(name).apply { isAccessible = true }
            val tf = ChineseCalendarCore::class.java.getDeclaredField(name).apply { isAccessible = true }
            assertSame(sf.get(a),sf.get(ac)); assertSame(tf.get(b.engine()),tf.get(bc.engine()))
        }
        val precedence = ChineseCalendar::class.java.getDeclaredField("CHINESE_DATE_PRECEDENCE").apply { isAccessible = true }.get(null) as Array<*>
        val expected = precedence.map { outer -> (outer as Array<*>).map { (it as IntArray).toList() } }
        assertEquals(expected,b.engine().getFieldResolutionTable().map { it.map { row -> row.toList() } })
    }
    private fun randomForField(step: Int) = step % 4
    companion object {
        private val lookup = MethodHandles.privateLookupIn(ChineseCalendar::class.java,MethodHandles.lookup())
        private val intType = Int::class.javaPrimitiveType!!
        private fun base(name: String,result: Class<*>,vararg args: Class<*>) = lookup.findSpecial(Calendar::class.java,name,MethodType.methodType(result,args.toList()),ChineseCalendar::class.java)
        private val baseAdd = base("add",Void.TYPE,intType,intType)
        private val baseRoll = base("roll",Void.TYPE,intType,intType)
        private val baseMax = base("getActualMaximum",intType,intType)
        private val baseCode = base("getTemporalMonthCode",String::class.java)
        private val baseSetCode = base("setTemporalMonthCode",Void.TYPE,String::class.java)
        private val fields = Calendar::class.java.getDeclaredField("fields").apply { isAccessible = true }
        private val stamps = Calendar::class.java.getDeclaredField("stamp").apply { isAccessible = true }
        private val korea = DangiCalendar::class.java.getDeclaredField("KOREA_ZONE").apply { isAccessible = true }.get(null) as TimeZone
        private val china = ChineseCalendar::class.java.getDeclaredField("CHINA_ZONE").apply { isAccessible = true }.get(null) as TimeZone
        private fun method(name: String,vararg types: Class<*>) = ChineseCalendar::class.java.getDeclaredMethod(name,*types).apply { isAccessible = true }
        private val winter = method("winterSolstice",intType)
        private val newYear = method("newYear",intType)
        private val moon = method("newMoonNear",intType,Boolean::class.javaPrimitiveType!!)
        private val term = method("majorSolarTerm",intType)
        private val daysToMillis = method("daysToMillis",intType)
        private val millisToDays = method("millisToDays",Long::class.javaPrimitiveType!!)
    }
}

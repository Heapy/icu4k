@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.impl.timezone

import com.ibm.icu.util.SimpleTimeZone as Source
import com.ibm.icu.util.BasicTimeZone.LocalOption as Option
import io.github.heapy.icu4k.impl.Grego
import io.github.heapy.icu4k.impl.ICUData
import io.github.heapy.icu4k.util.UResourceBundle
import kotlin.random.Random
import kotlin.test.*

class SimpleTimeZoneCoreOracleTest {
    private data class Pairing(val a: Source, val b: SimpleTimeZoneCore)
    private data class Spec(val raw: Int, val id: String?, val v: IntArray) {
        fun source() = Source(raw,id,v[0],v[1],v[2],v[3],v[4],v[5],v[6],v[7],v[8],v[9],v[10])
        fun target(owner: SimpleTimeZoneCore.OwnerQueries? = null) = SimpleTimeZoneCore(raw,id,v[0],v[1],v[2],v[3],v[4],v[5],v[6],v[7],v[8],v[9],v[10],owner)
        fun pair() = Pairing(source(),target())
    }
    private fun field(a: Any,n: String): Any? = generateSequence(a.javaClass) { it.superclass }.flatMap { it.declaredFields.asSequence() }.first { it.name == n }.also { it.isAccessible = true }.get(a)
    private fun outcome(block: () -> Any?): Any? = try { block() } catch (e: Throwable) {
        when (e) { is NullPointerException -> "null"; is IndexOutOfBoundsException -> "bounds"; else -> e.javaClass.simpleName+":"+e.message }
    }
    private fun st(t: com.ibm.icu.util.TimeZoneTransition?): String? = t?.toString()
    private fun tt(t: io.github.heapy.icu4k.util.TimeZoneTransition?): String? = t?.toString()
    private fun state(p: Pairing) {
        assertEquals(p.a.id,p.b.getID())
        assertEquals(p.a.isFrozen,p.b.isFrozen())
        assertEquals(p.a.toString(),p.b.toString())
        for (n in listOf("raw","dst","startYear","useDaylight","startMode","endMode","startMonth","startDay","startDayOfWeek","startTime","startTimeMode","endMonth","endDay","endDayOfWeek","endTime","endTimeMode","transitionRulesInitialized")) {
            assertEquals(field(p.a,n),field(p.b,n),n)
        }
        assertEquals(p.a.hashCode(),p.b.hashCode(),listOf("raw","dst","startYear","useDaylight","startMode","endMode","startMonth","startDay","startDayOfWeek","startTime","startTimeMode","endMonth","endDay","endDayOfWeek","endTime","endTimeMode").associateWith { field(p.a,it) }.toString())
        for (n in listOf("initialRule","firstTransition","stdRule","dstRule")) assertEquals(outcome { field(p.a,n)?.toString() },outcome { field(p.b,n)?.toString() },n)
        val x = field(p.a,"xinfo"); val y = field(p.b,"info")
        assertEquals(x == null,y == null)
        if (x != null && y != null) for (n in listOf("sy","sm","sdwm","sdw","st","sdm","sa","em","edwm","edw","et","edm","ea")) assertEquals(field(x,n),field(y,n),n)
    }
    private fun offsets(p: Pairing,date: Long,local: Boolean,size: Int = 2) {
        val a = IntArray(size) { 777 }; val b = a.copyOf()
        assertEquals(outcome { p.a.getOffset(date,local,a); Unit },outcome { p.b.getOffset(date,local,b); Unit },"offset $date/$local/$size")
        assertContentEquals(a,b)
    }
    private fun local(p: Pairing,date: Long,n: Option,d: Option,size: Int = 2) {
        val a = IntArray(size) { 777 }; val b = a.copyOf()
        val flags = intArrayOf(4,12,5,13,7,15)
        assertEquals(outcome { p.a.getOffsetFromLocal(date,n,d,a); Unit },outcome { p.b.getOffsetFromLocal(date,flags[n.ordinal],flags[d.ordinal],b); Unit },"local $date/$n/$d/$size")
        assertContentEquals(a,b)
    }
    private fun transitions(p: Pairing,t: Long,inclusive: Boolean) {
        assertEquals(outcome { st(p.a.getNextTransition(t,inclusive)) },outcome { tt(p.b.getNextTransition(t,inclusive)) },"next $t/$inclusive")
        assertEquals(outcome { st(p.a.getPreviousTransition(t,inclusive)) },outcome { tt(p.b.getPreviousTransition(t,inclusive)) },"prev $t/$inclusive")
    }
    private fun specs(): List<Spec> {
        val result = mutableListOf(Spec(123,"fixed",IntArray(11).also { it[10] = 3_600_000 }))
        val rules = UResourceBundle.getBundleInstance(ICUData.ICU_BASE_NAME,"zoneinfo64").get("Rules")
        for (i in 0 until rules.getSize()) {
            val r = rules.get(i)
            val vector = r.getIntVector().copyOf()
            vector[3] *= 1000; vector[8] *= 1000; vector[10] *= 1000
            for (raw in listOf(-18_000_000,0,37_800_000)) result.add(Spec(raw,r.getKey(),vector.copyOf()))
        }
        val random = Random(786)
        repeat(120) {
            fun rule(): List<Int> {
                val month = random.nextInt(12)
                val mode = random.nextInt(4)
                val day = if (mode == 1) (random.nextInt(1,6) * if(random.nextBoolean()) 1 else -1) else random.nextInt(1,29)
                val dow = if (mode == 0) 0 else random.nextInt(1,8) * if(mode > 1) -1 else 1
                return listOf(month, if(mode == 3) -day else day,dow,random.nextInt(0,86_400_001),random.nextInt(3))
            }
            result.add(Spec(random.nextInt(),"random$it",(rule()+rule()+listOf(listOf(-3_600_000,3_600_000,1,Int.MIN_VALUE,Int.MAX_VALUE).random(random))).toIntArray()))
        }
        return result
    }
    @Test fun embeddedFinalRulesAndAllDateModesMatchOffsetsAndTransitions() {
        val random = Random(7861)
        for ((index,spec) in specs().withIndex()) {
            val p = spec.pair()
            val year = listOf(-2000,0,1970,2007,2038)[index % 5]
            p.a.setStartYear(year); p.b.setStartYear(year)
            assertEquals(p.a.timeZoneRules.map { it.toString() },p.b.getTimeZoneRules().map { it.toString() })
            val dates = mutableListOf(Long.MIN_VALUE,Long.MAX_VALUE,-1L,0L,1L)
            repeat(24) { dates.add(random.nextLong(-80_000_000_000_000L,80_000_000_000_000L)) }
            var transition = p.a.getNextTransition(Grego.fieldsToDay(2024,0,1)*86_400_000L,false)
            repeat(3) {
                if (transition != null) {
                    val time = transition!!.time
                    dates.addAll(listOf(time-1,time,time+1,time+p.a.rawOffset,time+p.a.rawOffset+p.a.dstSavings))
                    transition = p.a.getNextTransition(time,false)
                }
            }
            for (date in dates) {
                offsets(p,date,false); offsets(p,date,true)
                transitions(p,date,false); transitions(p,date,true)
                if (date in -9_000_000_000_000L..9_000_000_000_000L) for(n in Option.entries) for(d in Option.entries) local(p,date,n,d)
            }
            for (era in listOf(-1,0,1,2)) for (m in listOf(-1,0,1,11,12)) for (day in listOf(0,1,29,32)) {
                assertEquals(outcome { p.a.getOffset(era,2025,m,day,7,0) },outcome { p.b.getOffset(era,2025,m,day,7,0) })
                assertEquals(outcome { p.a.getOffset(era,2025,m,day,7,0,-1) },outcome { p.b.getOffset(era,2025,m,day,7,0,-1) })
            }
            offsets(p,0,true,0); offsets(p,0,true,1)
            local(p,0,Option.FORMER,Option.LATTER,0); local(p,0,Option.FORMER,Option.LATTER,1)
            assertEquals(outcome { p.a.getOffset(0L,false,null); Unit },outcome { p.b.getOffset(0L,false,null); Unit })
            state(p)
        }
    }
    @Test fun randomInvalidConstructorsMatchValidationOrder() {
        val random = Random(7862)
        repeat(1500) { i ->
            val values = IntArray(11) { listOf(Int.MIN_VALUE,-30,-1,0,1,7,12,31,86_400_000,86_400_001,Int.MAX_VALUE).random(random) }
            val spec = Spec(random.nextInt(), if(i%17 == 0) null else "test",values)
            assertEquals(outcome { spec.source(); Unit },outcome { spec.target(); Unit },values.toList().toString())
        }
    }
    @Test fun mutationFailureCloneCachesInfoAndHashMatchMixedHistories() {
        val random = Random(7863)
        val pairs = mutableListOf(Pairing(Source(0,"test"),SimpleTimeZoneCore(0,"test")))
        val values = intArrayOf(Int.MIN_VALUE,-100,-5,-1,0,1,2,7,11,12,29,31,32,86_400_000,Int.MAX_VALUE)
        repeat(1500) {
            val p = pairs.random(random)
            val m = values.random(random); val d = values.random(random); val dow = values.random(random); val t = values.random(random); val after = random.nextBoolean()
            when (random.nextInt(16)) {
                0 -> assertEquals(outcome { p.a.setStartRule(m,d,dow,t); Unit },outcome { p.b.setStartRule(m,d,dow,t); Unit })
                1 -> assertEquals(outcome { p.a.setEndRule(m,d,dow,t); Unit },outcome { p.b.setEndRule(m,d,dow,t); Unit })
                2 -> assertEquals(outcome { p.a.setStartRule(m,d,t); Unit },outcome { p.b.setStartRule(m,d,t); Unit })
                3 -> assertEquals(outcome { p.a.setEndRule(m,d,t); Unit },outcome { p.b.setEndRule(m,d,t); Unit })
                4 -> assertEquals(outcome { p.a.setStartRule(m,d,dow,t,after); Unit },outcome { p.b.setStartRule(m,d,dow,t,after); Unit })
                5 -> assertEquals(outcome { p.a.setEndRule(m,d,dow,t,after); Unit },outcome { p.b.setEndRule(m,d,dow,t,after); Unit })
                6 -> assertEquals(outcome { p.a.setStartYear(d); Unit },outcome { p.b.setStartYear(d); Unit })
                7 -> assertEquals(outcome { p.a.rawOffset = t; Unit },outcome { p.b.setRawOffset(t); Unit })
                8 -> assertEquals(outcome { p.a.setDSTSavings(t); Unit },outcome { p.b.setDSTSavings(t); Unit })
                9 -> { val id = if(after) null else "id$m"; assertEquals(outcome { p.a.id = id; Unit },outcome { p.b.setID(id); Unit }) }
                10 -> { p.a.freeze(); p.b.freeze() }
                11 -> { pairs.add(Pairing(p.a.cloneAsThawed(),p.b.cloneAsThawed())); if(pairs.size > 5) pairs.removeAt(0) }
                12 -> { val a = p.a.clone(); val b = p.b.clone(); assertEquals(a === p.a,b === p.b); pairs.add(Pairing(a,b)); if(pairs.size > 5) pairs.removeAt(0) }
                13 -> assertEquals(outcome { p.a.timeZoneRules.map { it.toString() } },outcome { p.b.getTimeZoneRules().map { it.toString() } })
                14 -> offsets(p,random.nextLong(),after)
                15 -> transitions(p,random.nextLong(),after)
            }
            for (x in pairs) {
                state(x)
                assertEquals(p.a.hasSameRules(x.a),p.b.hasSameRules(x.b))
                assertEquals(p.a == x.a,p.b == x.b)
            }
        }
    }
    @Test fun ownerQueriesPreserveEvaluationAndPartialArrayWrites() {
        val expected = mutableListOf<String>(); val actual = mutableListOf<String>()
        val source = object : Source(0,"stored",2,2,1,7_200_000,10,1,1,7_200_000) {
            override fun getID(): String? { expected.add("id"); return null }
            override fun getRawOffset(): Int { expected.add("raw"); return 123 }
            override fun getDSTSavings(): Int { expected.add("dst"); return -777 }
            override fun isFrozen(): Boolean { expected.add("frozen"); return false }
            override fun getOffset(e: Int,y: Int,m: Int,d: Int,dow: Int,t: Int): Int { expected.add("offset:$e/$y/$m/$d/$dow/$t"); return 1000 }
        }
        val target = SimpleTimeZoneCore(0,"stored",2,2,1,7_200_000,0,10,1,1,7_200_000,0,owner = object : SimpleTimeZoneCore.OwnerQueries {
            override fun getID(): String? { actual.add("id"); return null }
            override fun getRawOffset(): Int { actual.add("raw"); return 123 }
            override fun getDSTSavings(): Int { actual.add("dst"); return -777 }
            override fun isFrozen(): Boolean { actual.add("frozen"); return false }
            override fun getOffset(e: Int,y: Int,m: Int,d: Int,dow: Int,t: Int): Int { actual.add("offset:$e/$y/$m/$d/$dow/$t"); return 1000 }
            override fun getOffset(e: Int,y: Int,m: Int,d: Int,dow: Int,t: Int,len: Int): Int = error("Not called by this owner")
        })
        expected.clear(); actual.clear()
        assertEquals(source.timeZoneRules.map { it.toString() },target.getTimeZoneRules().map { it.toString() })
        assertEquals(expected,actual)
        for (size in listOf(-1,0,1,2)) {
            expected.clear(); actual.clear()
            val a = if(size < 0) null else IntArray(size); val b = a?.copyOf()
            assertEquals(outcome { source.getOffset(0L,true,a); Unit },outcome { target.getOffset(0L,true,b); Unit })
            assertContentEquals(a,b); assertEquals(expected,actual)
        }
        expected.clear(); actual.clear()
        source.setEndRule(10,1,0); target.setEndRule(10,1,0)
        assertEquals(expected,actual)
        expected.clear(); actual.clear()
        source.clone(); target.clone()
        assertEquals(expected,actual)
        expected.clear(); actual.clear()
        source.id = "changed"; target.setID("changed")
        assertEquals(expected,actual)
    }

    @Test fun sixFieldOffsetsDispatchToOwnerSevenFieldOverride() {
        val expected = mutableListOf<List<Int>>()
        val actual = mutableListOf<List<Int>>()
        val source = object : Source(0,"test") {
            override fun getOffset(e: Int,y: Int,m: Int,d: Int,dow: Int,t: Int,len: Int): Int {
                expected.add(listOf(e,y,m,d,dow,t,len)); return 777
            }
        }
        lateinit var target: SimpleTimeZoneCore
        target = SimpleTimeZoneCore(0,"test",owner = object : SimpleTimeZoneCore.OwnerQueries {
            override fun getID(): String = "test"
            override fun getRawOffset(): Int = 0
            override fun getDSTSavings(): Int = 3_600_000
            override fun isFrozen(): Boolean = false
            override fun getOffset(e: Int,y: Int,m: Int,d: Int,dow: Int,t: Int): Int = target.getOffset(e,y,m,d,dow,t)
            override fun getOffset(e: Int,y: Int,m: Int,d: Int,dow: Int,t: Int,len: Int): Int {
                actual.add(listOf(e,y,m,d,dow,t,len)); return 777
            }
        })
        for (month in -1..12) {
            assertEquals(outcome { source.getOffset(-7,2024,month,-1,0,-1) },outcome { target.getOffset(-7,2024,month,-1,0,-1) })
        }
        offsets(Pairing(source,target),0L,true)
        assertEquals(expected,actual)
    }
    @Test fun recordedRuleInfoReplaysAllSetterForms() {
        val type = Class.forName("com.ibm.icu.util.STZInfo")
        val ctor = type.getDeclaredConstructor().also { it.isAccessible = true }
        val apply = type.getDeclaredMethod("applyTo",Source::class.java).also { it.isAccessible = true }
        val random = Random(7864)
        repeat(90) { index ->
            val sourceInfo = ctor.newInstance()
            val targetInfo = SimpleTimeZoneCore.STZInfo()
            val start = index % 3
            val end = (index / 3) % 3
            val values = mapOf("sy" to 2007, "sm" to 2, "sdwm" to 2, "sdw" to if(start == 1) -1 else 1,
                "st" to random.nextInt(86_400_001), "sdm" to if(start == 0) -1 else 12, "sa" to random.nextBoolean(),
                "em" to 10, "edwm" to 1, "edw" to if(end == 1) -1 else 1,
                "et" to random.nextInt(86_400_001), "edm" to if(end == 0) -1 else 9, "ea" to random.nextBoolean())
            for ((key,value) in values) {
                type.getDeclaredField(key).also { it.isAccessible = true }.set(sourceInfo,value)
                targetInfo.javaClass.getDeclaredField(key).also { it.isAccessible = true }.set(targetInfo,value)
            }
            val source = Source(1000,"info")
            val target = SimpleTimeZoneCore(1000,"info")
            apply.invoke(sourceInfo,source); targetInfo.applyTo(target)
            state(Pairing(source,target))
            assertEquals(source.timeZoneRules.map { it.toString() },target.getTimeZoneRules().map { it.toString() })
        }
    }

    @Test fun setIdRetainsParentFrozenCheckAndNullPrecedence() {
        var aCalls = 0; var bCalls = 0
        val source = object : Source(0,"old") { override fun isFrozen(): Boolean = ++aCalls == 2 }
        val target = SimpleTimeZoneCore(0,"old",owner = object : SimpleTimeZoneCore.OwnerQueries {
            override fun getID(): String = "old"
            override fun getRawOffset(): Int = 0
            override fun getDSTSavings(): Int = 3_600_000
            override fun isFrozen(): Boolean = ++bCalls == 2
            override fun getOffset(e: Int,y: Int,m: Int,d: Int,dow: Int,t: Int): Int = 0
            override fun getOffset(e: Int,y: Int,m: Int,d: Int,dow: Int,t: Int,len: Int): Int = 0
        })
        assertEquals(outcome { source.id = "new"; Unit },outcome { target.setID("new"); Unit })
        assertEquals("old",target.getID())
        assertEquals(aCalls,bCalls)
        assertEquals(outcome { source.id = null; Unit },outcome { target.setID(null); Unit })
        assertEquals(aCalls,bCalls)
    }
}

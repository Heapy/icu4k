@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.impl.timezone

import com.ibm.icu.impl.OlsonTimeZone as Source
import com.ibm.icu.impl.ZoneMeta as SourceMeta
import com.ibm.icu.util.BasicTimeZone.LocalOption as Option
import io.github.heapy.icu4k.impl.ICUData
import io.github.heapy.icu4k.impl.Grego
import io.github.heapy.icu4k.util.*
import kotlin.random.Random
import kotlin.test.*

class OlsonTimeZoneCoreOracleTest {
    private data class Pairing(var a: Source, var b: OlsonTimeZoneCore)
    private fun outcome(block: () -> Any?): Any? = try { block() } catch (e: Throwable) {
        val error = if (e is java.lang.reflect.InvocationTargetException) e.cause!! else e
        when (error) { is NullPointerException -> "null"; is IndexOutOfBoundsException -> "bounds"; else -> error.javaClass.simpleName+":"+error.message }
    }
    private fun field(a: Any,n: String): Any? = generateSequence(a.javaClass) { it.superclass }.flatMap { it.declaredFields.asSequence() }.first { it.name == n }.also { it.isAccessible = true }.get(a)
    private fun state(p: Pairing) {
        assertEquals(field(p.a,"ID"),p.b.getID())
        assertEquals(p.a.isFrozen,p.b.isFrozen())
        for (name in listOf("transitionCount","typeCount","finalStartYear","finalStartMillis","canonicalID","transitionRulesInitialized","firstTZTransitionIdx")) assertEquals(field(p.a,name),field(p.b,name),name)
        assertContentEquals(field(p.a,"transitionTimes64") as LongArray?,field(p.b,"transitionTimes64") as LongArray?)
        assertContentEquals(field(p.a,"typeOffsets") as IntArray,field(p.b,"typeOffsets") as IntArray)
        assertContentEquals(field(p.a,"typeMapData") as ByteArray?,field(p.b,"typeMapData") as ByteArray?)
        assertEquals(p.a.hashCode(),p.b.hashCode(),p.a.id)
        assertEquals(p.a.toString().substringAfter('[').let { "[$it" },p.b.describe(""))
        for (name in listOf("initialRule","firstTZTransition","firstFinalTZTransition","finalZone","finalZoneWithStartYear")) {
            assertEquals(outcome { field(p.a,name)?.toString() },outcome { field(p.b,name)?.toString() },name)
        }
        @Suppress("UNCHECKED_CAST")
        assertEquals((field(p.a,"historicRules") as Array<com.ibm.icu.util.TimeArrayTimeZoneRule?>?)?.map { it?.toString() },
            (field(p.b,"historicRules") as Array<TimeArrayTimeZoneRule?>?)?.map { it?.toString() })
    }
    private fun offsets(p: Pairing,date: Long,local: Boolean,size: Int = 2) {
        val a = if(size < 0) null else IntArray(size) { 779 }; val b = a?.copyOf()
        assertEquals(outcome { p.a.getOffset(date,local,a); Unit },outcome { p.b.getOffset(date,local,b); Unit },"${p.a.id}/$date/$local/$size")
        assertContentEquals(a,b)
    }
    private val flags = intArrayOf(4,12,5,13,7,15)
    private fun local(p: Pairing,date: Long,n: Option,d: Option,size: Int = 2) {
        val a = if(size < 0) null else IntArray(size) { 779 }; val b = a?.copyOf()
        assertEquals(outcome { p.a.getOffsetFromLocal(date,n,d,a); Unit },outcome { p.b.getOffsetFromLocal(date,flags[n.ordinal],flags[d.ordinal],b); Unit },"${p.a.id}/$date/$n/$d/$size")
        assertContentEquals(a,b)
    }
    private fun sr(r: com.ibm.icu.util.TimeZoneRule?): Any? = r?.let { listOf(it.name,it.rawOffset,it.dstSavings) }
    private fun tr(r: TimeZoneRule?): Any? = r?.let { listOf(it.getName(),it.getRawOffset(),it.getDSTSavings()) }
    private fun st(t: com.ibm.icu.util.TimeZoneTransition?): Any? = t?.let { listOf(it.time,sr(it.from),sr(it.to)) }
    private fun tt(t: TimeZoneTransition?): Any? = t?.let { listOf(it.getTime(),tr(it.getFrom()),tr(it.getTo())) }
    private fun transitions(p: Pairing,t: Long,inclusive: Boolean) {
        assertEquals(outcome { st(p.a.getNextTransition(t,inclusive)) },outcome { tt(p.b.getNextTransition(t,inclusive)) },"${p.a.id}/next/$t/$inclusive")
        assertEquals(outcome { st(p.a.getPreviousTransition(t,inclusive)) },outcome { tt(p.b.getPreviousTransition(t,inclusive)) },"${p.a.id}/prev/$t/$inclusive")
    }
    @Test fun everyZoneHistoricalEdgesFinalRulesAndAllLocalOptionsMatch() {
        val top = UResourceBundle.getBundleInstance(ICUData.ICU_BASE_NAME,"zoneinfo64")
        val ids = top.get("Names").getStringArray()
        val random = Random(7871)
        for (id in ids) {
            val p = Pairing(Source(id),OlsonTimeZoneCore(id))
            assertEquals(p.a.timeZoneRules.map { it.toString() },p.b.getTimeZoneRules().map { it?.toString() },id)
            val times = (field(p.a,"transitionTimes64") as LongArray?) ?: longArrayOf()
            val dates = mutableSetOf(Long.MIN_VALUE,Long.MAX_VALUE,-1L,0L,1L)
            for (second in times) { val t = second*1000; dates.add(t-1); dates.add(t); dates.add(t+1) }
            for (year in listOf(1800,1900,1970,2000,2025,2038,2100)) dates.add(Grego.fieldsToDay(year,6,1)*86_400_000L)
            repeat(8) { dates.add(random.nextLong(-9_000_000_000_000L,9_000_000_000_000L)) }
            for (date in dates) { offsets(p,date,false); offsets(p,date,true); transitions(p,date,false); transitions(p,date,true) }
            val localDates = mutableSetOf(0L,Long.MIN_VALUE,Long.MAX_VALUE)
            for (i in times.indices.filter { it % maxOf(1,times.size/5) == 0 }) {
                val t = times[i]*1000
                val before = IntArray(2); val after = IntArray(2)
                p.a.getOffset(t-1,false,before); p.a.getOffset(t,false,after)
                val b = t+before[0]+before[1]; val a = t+after[0]+after[1]
                localDates.addAll(listOf(b-1,b,b+1,a-1,a,a+1,b+(a-b)/2))
            }
            localDates.add(Grego.fieldsToDay(2025,2,9)*86_400_000L+9_000_000)
            for (date in localDates) for(n in Option.entries) for(d in Option.entries) local(p,date,n,d)
            for (size in listOf(-1,0,1)) {
                offsets(p,0,false,size); offsets(p,Long.MAX_VALUE,true,size)
                local(p,0,Option.STANDARD_FORMER,Option.DAYLIGHT_LATTER,size)
                local(p,Long.MAX_VALUE,Option.FORMER,Option.LATTER,size)
            }
            for (year in listOf(-2000,0,1900,2025,Int.MIN_VALUE,Int.MAX_VALUE)) for(era in listOf(0,1)) for(m in listOf(0,1,11)) {
                assertEquals(outcome { p.a.getOffset(era,year,m,28,1,0) },outcome { p.b.getOffset(era,year,m,28,1,0) },"$id/field/$era/$year/$m")
                assertEquals(outcome { p.a.getOffset(era,year,m,31,1,0,31) },outcome { p.b.getOffset(era,year,m,31,1,0,31) },"$id/monthLength")
            }
            assertEquals(p.a.canonicalID,p.b.getCanonicalID())
            assertEquals(p.a.rawOffset,p.b.getRawOffset()); assertEquals(p.a.useDaylightTime(),p.b.useDaylightTime())
            assertEquals(p.a.observesDaylightTime(),p.b.observesDaylightTime()); assertEquals(p.a.dstSavings,p.b.getDSTSavings())
            state(p)
        }
    }
    @Test fun rawOffsetMutationClonesCanonicalIdsAndSourceEqualityQuirksMatch() {
        val random = Random(7872)
        val pairs = listOf("Europe/Minsk","America/New_York","Pacific/Apia","Asia/Kolkata","Africa/Casablanca","Etc/GMT","Europe/London").map { Pairing(Source(it),OlsonTimeZoneCore(it)) }.toMutableList()
        repeat(280) {
            val p = pairs.random(random)
            val value = listOf(Int.MIN_VALUE,Int.MAX_VALUE,0,1337,-18_000_000,3_600_000).random(random)
            when(random.nextInt(7)) {
                0,1 -> assertEquals(outcome { p.a.rawOffset = value; Unit },outcome { p.b.setRawOffset(value); Unit })
                2 -> { val id = if(random.nextInt(5)==0) null else "renamed$it"; assertEquals(outcome { p.a.id = id; Unit },outcome { p.b.setID(id); Unit }) }
                3 -> { pairs.add(Pairing(p.a.cloneAsThawed(),p.b.cloneAsThawed())); if(pairs.size > 12) pairs.removeAt(0) }
                4 -> { p.a.freeze(); p.b.freeze(); assertSame(p.a,p.a.clone()); assertSame(p.b,p.b.clone()) }
                5 -> assertEquals(outcome { p.a.timeZoneRules.map { it.toString() } },outcome { p.b.getTimeZoneRules().map { it?.toString() } })
                6 -> transitions(p,random.nextLong(-3_000_000_000_000L,3_000_000_000_000L),random.nextBoolean())
            }
            state(p)
            for (date in listOf(0L,30_000L,Long.MIN_VALUE,Long.MAX_VALUE,Grego.fieldsToDay(2025,0,1)*86_400_000L)) { offsets(p,date,false); offsets(p,date,true) }
            for (other in pairs) {
                assertEquals(p.a == other.a,p.b == other.b)
                assertEquals(p.a.hasSameRules(other.a),p.b.hasSameRules(other.b))
            }
        }
    }

    // Synthetic resources expose invalid formats and full-width seconds/offsets without binary generation.
    private class SNode(private val data: Any,private val trace: MutableList<String>,private val path: String = "") : com.ibm.icu.util.UResourceBundle() {
        override fun getULocale() = com.ibm.icu.util.ULocale.ROOT
        override fun getLocaleID() = "root"
        override fun getBaseName() = "fixture"
        override fun getParent(): com.ibm.icu.util.UResourceBundle? = null
        override fun get(key: String): com.ibm.icu.util.UResourceBundle { trace.add("$path/get:$key"); return SNode(value(key),trace,"$path/$key") }
        override fun handleGetObject(key: String): Any { trace.add("$path/string:$key"); val v = value(key); return if(v is String) v else SNode(v,trace,"$path/$key") }
        private fun value(key: String): Any = (data as Map<*,*>)[key] ?: throw java.util.MissingResourceException("missing:$key","fixture",key)
        override fun getIntVector(): IntArray { trace.add("$path/vector"); return if(data is IntArray) data.copyOf() else super.getIntVector() }
        override fun getInt(): Int { trace.add("$path/int"); return if(data is Int) data else super.getInt() }
        override fun getBinary(output: ByteArray?): ByteArray { trace.add("$path/binary"); return if(data is ByteArray) data.copyOf() else super.getBinary(output) }
    }
    private class TNode(private val data: Any,private val trace: MutableList<String>,private val path: String = "") : UResourceBundle() {
        override fun getULocale() = ULocale.ROOT
        override fun getLocaleID() = "root"
        override fun getBaseName() = "fixture"
        override fun getParent(): UResourceBundle? = null
        override fun get(key: String): UResourceBundle { trace.add("$path/get:$key"); return TNode(value(key),trace,"$path/$key") }
        override fun handleGetObject(key: String): Any { trace.add("$path/string:$key"); val v = value(key); return if(v is String) v else TNode(v,trace,"$path/$key") }
        private fun value(key: String): Any = (data as Map<*,*>)[key] ?: throw MissingResourceException("missing:$key","fixture",key)
        override fun getIntVector(): IntArray { trace.add("$path/vector"); return if(data is IntArray) data.copyOf() else super.getIntVector() }
        override fun getInt(): Int { trace.add("$path/int"); return if(data is Int) data else super.getInt() }
        override fun getBinary(output: ByteArray?): ByteArray { trace.add("$path/binary"); return if(data is ByteArray) data.copyOf() else super.getBinary(output) }
    }
    @Test fun malformedResourceFormatsPreserveReadOrderAndFailure() {
        val base = mapOf<String,Any>("typeOffsets" to intArrayOf(0,0))
        val cases = mutableListOf<Map<String,Any>>(emptyMap(),base)
        for(key in listOf("transPre32","transPost32")) { cases.add(base+(key to intArrayOf(1))); cases.add(base+(key to "wrong")) }
        for(v in listOf(intArrayOf(),intArrayOf(1),intArrayOf(1,2,3),IntArray(0x8000))) cases.add(base+("typeOffsets" to v))
        cases.add(base+("trans" to intArrayOf(0))); cases.add(base+mapOf("trans" to intArrayOf(0),"typeMap" to byteArrayOf()))
        cases.add(base+mapOf("trans" to intArrayOf(0),"typeMap" to byteArrayOf(-1)))
        cases.add(base+("finalRule" to "rule")); cases.add(base+mapOf("finalRule" to "rule","finalRaw" to 0))
        cases.add(base+mapOf("finalRule" to "rule","finalRaw" to 0,"finalYear" to 2030))
        val top = mapOf("Rules" to mapOf("rule" to intArrayOf(2,2,1,7200,0,10,1,1,7200,0,3600)))
        for (resource in cases) for (actualTop in listOf(emptyMap<String,Any>(),top)) {
            val a = mutableListOf<String>(); val b = mutableListOf<String>()
            assertEquals(outcome { Source(SNode(actualTop,a),SNode(resource,a),"Etc/GMT"); Unit },outcome { OlsonTimeZoneCore(TNode(actualTop,b),TNode(resource,b),"Etc/GMT"); Unit })
            assertEquals(a,b,resource.keys.toString())
        }
    }
    @Test fun syntheticHistoricalOffsetsRetainFullWidthArithmeticAndOptionMasks() {
        val method = Source::class.java.getDeclaredMethod("getHistoricalOffset",Long::class.javaPrimitiveType,Boolean::class.javaPrimitiveType,Int::class.javaPrimitiveType,Int::class.javaPrimitiveType,IntArray::class.java).also { it.isAccessible = true }
        val random = Random(7873)
        repeat(180) {
            val transitions = LongArray(random.nextInt(1,8)) { random.nextLong() }.sortedArray()
            val resource = mapOf("transPre32" to transitions.flatMap { listOf((it ushr 32).toInt(),it.toInt()) }.toIntArray(),
                "typeOffsets" to IntArray(8) { random.nextInt() },"typeMap" to ByteArray(transitions.size) { random.nextInt(4).toByte() })
            val p = Pairing(Source(SNode(emptyMap<String,Any>(),mutableListOf()),SNode(resource,mutableListOf()),"Etc/GMT"),
                OlsonTimeZoneCore(TNode(emptyMap<String,Any>(),mutableListOf()),TNode(resource,mutableListOf()),"Etc/GMT"))
            for (date in listOf(Long.MIN_VALUE,Long.MAX_VALUE,-1L,0L,1L)+List(20) { random.nextLong() }) {
                offsets(p,date,false)
                val n = random.nextInt(); val d = random.nextInt()
                val a = IntArray(2); val b = IntArray(2)
                assertEquals(outcome { method.invoke(p.a,date,true,n,d,a); Unit },outcome { p.b.getOffsetFromLocal(date,n,d,b); Unit })
                assertContentEquals(a,b)
            }
        }
    }

    @Test fun ownerQueriesRawOffsetRuleFallbackAndLazyFailuresMatch() {
        val sourceTop = com.ibm.icu.util.UResourceBundle.getBundleInstance(com.ibm.icu.impl.ICUData.ICU_BASE_NAME,"zoneinfo64")
        val targetTop = UResourceBundle.getBundleInstance(ICUData.ICU_BASE_NAME,"zoneinfo64")
        for (mode in 0..5) {
            val expected = mutableListOf<String>(); val actual = mutableListOf<String>()
            var stamp = System.currentTimeMillis()
            val sourceRules: Array<com.ibm.icu.util.TimeZoneRule?>? = when(mode) {
                0 -> null
                1 -> arrayOf(com.ibm.icu.util.InitialTimeZoneRule("initial",0,0))
                2 -> arrayOf(null,null,null)
                else -> arrayOf(null,
                    com.ibm.icu.util.AnnualTimeZoneRule("dst",0,if(mode == 4) -3_600_000 else 3_600_000,com.ibm.icu.util.DateTimeRule(2,2,1,7_200_000,0),2000,Int.MAX_VALUE),
                    com.ibm.icu.util.AnnualTimeZoneRule("std",0,0,com.ibm.icu.util.DateTimeRule(10,1,1,7_200_000,0),2000,Int.MAX_VALUE))
            }
            val targetRules: Array<TimeZoneRule?>? = when(mode) {
                0 -> null
                1 -> arrayOf(InitialTimeZoneRule("initial",0,0))
                2 -> arrayOf(null,null,null)
                else -> arrayOf(null,
                    AnnualTimeZoneRule("dst",0,if(mode == 4) -3_600_000 else 3_600_000,DateTimeRule(2,2,1,7_200_000,0),2000,Int.MAX_VALUE),
                    AnnualTimeZoneRule("std",0,0,DateTimeRule(10,1,1,7_200_000,0),2000,Int.MAX_VALUE))
            }
            val source = object : Source(sourceTop,SourceMeta.openOlsonResource(sourceTop,"Etc/GMT"),"Etc/GMT") {
                override fun getID(): String? { expected.add("id"); return if(mode == 5) null else "Etc/GMT" }
                override fun isFrozen(): Boolean { expected.add("frozen"); return false }
                override fun getRawOffset(): Int { expected.add("raw"); return 0 }
                override fun useDaylightTime(): Boolean { expected.add("use"); return true }
                override fun getOffset(date: Long,local: Boolean,offsets: IntArray?) { stamp = date; expected.add("offset:$date/$local"); offsets!![0] = 777; offsets[1] = 0 }
                override fun getOffset(e: Int,y: Int,m: Int,d: Int,dow: Int,ms: Int,len: Int): Int { expected.add("fields:$e/$y/$m/$d/$dow/$ms/$len"); return 888 }
                override fun getSimpleTimeZoneRulesNear(date: Long): Array<com.ibm.icu.util.TimeZoneRule?>? { stamp = date; expected.add("simple:$date"); return sourceRules }
                override fun getPreviousTransition(base: Long,inclusive: Boolean): com.ibm.icu.util.TimeZoneTransition? { expected.add("previous:$base/$inclusive"); return null }
                fun directRaw(): Int = super.getRawOffset()
                fun directDST(): Int = super.getDSTSavings()
            }
            lateinit var target: OlsonTimeZoneCore
            target = OlsonTimeZoneCore(targetTop,ZoneMetaCore.openOlsonResource(targetTop,"Etc/GMT"),"Etc/GMT",clock = { stamp },owner = object : OlsonTimeZoneCore.OwnerQueries {
                override fun getID(): String? { actual.add("id"); return if(mode == 5) null else "Etc/GMT" }
                override fun isFrozen(): Boolean { actual.add("frozen"); return false }
                override fun getRawOffset(): Int { actual.add("raw"); return 0 }
                override fun useDaylightTime(): Boolean { actual.add("use"); return true }
                override fun getOffset(date: Long,local: Boolean,offsets: IntArray?) { actual.add("offset:$date/$local"); offsets!![0] = 777; offsets[1] = 0 }
                override fun getOffset(e: Int,y: Int,m: Int,d: Int,dow: Int,ms: Int,len: Int): Int { actual.add("fields:$e/$y/$m/$d/$dow/$ms/$len"); return 888 }
                override fun getSimpleTimeZoneRulesNear(date: Long): Array<TimeZoneRule?>? { actual.add("simple:$date"); return targetRules }
                override fun getPreviousTransition(base: Long,inclusive: Boolean): TimeZoneTransition? { actual.add("previous:$base/$inclusive"); return null }
                override fun getNextTransition(base: Long,inclusive: Boolean): TimeZoneTransition? = target.getNextTransition(base,inclusive)
            })
            expected.clear(); actual.clear()
            assertEquals(source.directRaw(),target.getRawOffset()); assertEquals(expected,actual)
            expected.clear(); actual.clear()
            assertEquals(source.directDST(),target.getDSTSavings()); assertEquals(expected,actual)
            expected.clear(); actual.clear()
            assertEquals(source.getOffset(-1,2025,1,-1,-1,-1),target.getOffset(-1,2025,1,-1,-1,-1)); assertEquals(expected,actual)
            expected.clear(); actual.clear()
            assertEquals(outcome { source.rawOffset = 1337; Unit },outcome { target.setRawOffset(1337); Unit }); assertEquals(expected,actual,"mode$mode")
            state(Pairing(source,target))
        }
    }

    @Test fun initialDstWithoutTransitionsPreservesCurrentYearAndFutureQueries() {
        val data = mapOf("typeOffsets" to intArrayOf(3600,1800))
        val source = Source(SNode(emptyMap<String,Any>(),mutableListOf()),SNode(data,mutableListOf()),"Etc/GMT")
        val target = OlsonTimeZoneCore(TNode(emptyMap<String,Any>(),mutableListOf()),TNode(data,mutableListOf()),"Etc/GMT")
        assertFalse(source.useDaylightTime()); assertFalse(target.useDaylightTime())
        assertTrue(source.observesDaylightTime()); assertTrue(target.observesDaylightTime())
        assertEquals(source.dstSavings,target.getDSTSavings())
        assertEquals(source.inDaylightTime(java.util.Date(0)),target.inDaylightTime(Date(0)))
        assertEquals(source.rawOffset,target.getRawOffset())
        assertEquals(source.timeZoneRules.map { it.toString() },target.getTimeZoneRules().map { it?.toString() })
        state(Pairing(source,target))
    }

    @Test fun metadataPrototypeFactoriesAndDirectRawFilteringMatch() {
        val ids = UResourceBundle.getBundleInstance(ICUData.ICU_BASE_NAME,"zoneinfo64").get("Names").getStringArray()
        for (id in ids) {
            val source = SourceMeta.getSystemTimeZone(id)
            val target = ZoneMetaCore.getSystemTimeZoneCore(id)
            assertNotNull(source); assertNotNull(target)
            state(Pairing(source,target))
            assertSame(source,SourceMeta.getSystemTimeZone(id)); assertSame(target,ZoneMetaCore.getSystemTimeZoneCore(id))
            assertTrue(target.isFrozen())
        }
        for(id in listOf("missing","",null)) {
            assertEquals(outcome { SourceMeta.getSystemTimeZone(id); Unit },outcome { ZoneMetaCore.getSystemTimeZoneCore(id); Unit })
        }
        for(type in ZoneMetaCore.SystemTimeZoneType.entries) for(region in listOf(null,"US","BY","001","missing")) for(raw in listOf(-18_000_000,0,10_800_000,Int.MIN_VALUE)) {
            assertEquals(SourceMeta.getAvailableIDs(com.ibm.icu.util.TimeZone.SystemTimeZoneType.valueOf(type.name),region,raw).toList(),
                ZoneMetaCore.getAvailableIDs(type,region,raw).toList())
        }
        for(id in listOf(null,"missing","GMT","GMT+0","GMT-0","GMT+530","GMT+05:30","GMT+23:59:59","GMT-000059","GMT+000059")) {
            val source = SourceMeta.getCustomTimeZone(id); val target = ZoneMetaCore.getCustomTimeZoneCore(id)
            assertEquals(source?.id,target?.getID()); assertEquals(source?.rawOffset,target?.getRawOffset()); assertEquals(source?.isFrozen,target?.isFrozen())
            assertSame(target,ZoneMetaCore.getCustomTimeZoneCore(id))
        }
        for(offset in listOf(Int.MIN_VALUE,Int.MAX_VALUE,-59_999,0,1,59_999,60_000)) {
            val source = SourceMeta.getCustomTimeZone(offset); val target = ZoneMetaCore.getCustomTimeZoneCore(offset)
            assertEquals(source.id,target.getID()); assertEquals(source.rawOffset,target.getRawOffset()); assertEquals(source.isFrozen,target.isFrozen())
        }
    }
}

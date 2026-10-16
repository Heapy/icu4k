@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.impl.calendar

import com.ibm.icu.text.DateTimePatternGenerator as Source
import com.ibm.icu.util.ULocale
import java.lang.reflect.InvocationTargetException
import kotlin.random.Random
import kotlin.test.*

class DateTimePatternGeneratorCoreOracleTest {
    private fun outcome(action: ()->Any?): Any?=try { action() } catch(e: Throwable) {
        val cause=if(e is InvocationTargetException) e.targetException else e
        if(cause is VirtualMachineError) throw cause
        if(cause !is Exception && cause !is AssertionError) throw cause
        (if(cause is IndexOutOfBoundsException) "bounds" else cause.javaClass.name) to if(cause is NullPointerException || cause is IndexOutOfBoundsException) null else cause.message
    }
    private fun empty()=DateTimePatternGeneratorCore.empty(Source::class.java.desiredAssertionStatus())
    private fun state(source: Source): List<Any?> = listOf(source.getSkeletons(LinkedHashMap()).entries.map { it.key to it.value },source.getBaseSkeletons(null).toSet(),source.decimal,(0..3).map { source.getDateTimeFormat(it) },(0..15).map { source.getAppendItemFormat(it) },(0..15).map { f -> Source.DisplayWidth.entries.map { source.getFieldDisplayName(f,it) } },source.defaultHourFormatChar,source.isFrozen)
    private fun state(target: DateTimePatternGeneratorCore): List<Any?> = listOf(target.getSkeletons(null).entries.map { it.key to it.value },target.getBaseSkeletons(null).toSet(),target.getDecimal(),(0..3).map { target.getDateTimeFormat(it) },(0..15).map { target.getAppendItemFormat(it) },(0..15).map { f -> DateTimePatternGeneratorCore.DisplayWidth.entries.map { target.getFieldDisplayName(f,it) } },target.getDefaultHourFormatChar(),target.isFrozen())
    private fun read(source: Source,name: String): Any?=Source::class.java.getDeclaredField(name).apply { isAccessible=true }.get(source)
    private fun allowed(source: Source,value: Array<String?>?) { Source::class.java.getDeclaredField("allowedHourFormats").apply { isAccessible=true }.set(source,value) }
    @Test fun registrationConflictsExplicitSkeletonsAndFailureState() {
        val a=Source.getEmptyInstance(); val b=empty(); val ai=Source.PatternInfo(); val bi=DateTimePatternGeneratorCore.PatternInfo()
        val random=Random(783)
        val patterns=listOf(null,"","y-M-d","yy/MM/dd","yyyy MMM d","d MMMM y","EEEE d MMMM y","HH:mm","H.m.s","hh:mm a","K:mm B","m:ss.SSS","y j M","d 'of' MMMM","ah","h")
        val skeletons=listOf(null,"yMd","yyMMdd","yyyyMMMMd","hmm","ahmm","Hm","yM","MMMMd","msS")
        repeat(2500) { step ->
            val pattern=patterns[random.nextInt(patterns.size)]; val skeleton=skeletons[random.nextInt(skeletons.size)]; val override=random.nextBoolean(); val nullInfo=step%29==0
            assertEquals(outcome { a.addPatternWithSkeleton(pattern,skeleton,override,if(nullInfo) null else ai); "ok" },outcome { b.addPatternWithSkeleton(pattern,skeleton,override,if(nullInfo) null else bi); "ok" },"add/$step/$pattern/$skeleton/$override")
            assertEquals(ai.status,bi.status); assertEquals(ai.conflictingPattern,bi.conflictingPattern)
            assertEquals(state(a),state(b),"state/$step")
        }
    }
    @Test fun bestPatternsFieldAdjustmentMetacharactersAndAppendBranches() {
        val a=Source.getEmptyInstance(); val b=empty(); val ai=Source.PatternInfo(); val bi=DateTimePatternGeneratorCore.PatternInfo()
        for((pattern,skeleton) in listOf("yyyy-MM-dd" to null,"EEEE, d MMMM y" to "yMMMMEEEEd","MMM d" to "MMMd","HH:mm" to "Hm","HH:mm:ss" to "Hms","h:mm a" to "hm","y/MM/dd" to "yyyyMMdd","MMMM d" to "MMdd")) {
            a.addPatternWithSkeleton(pattern,skeleton,true,ai); b.addPatternWithSkeleton(pattern,skeleton,true,bi)
        }
        val requests=listOf(null,"","yMd","yyyyMMdd","yMMMMEEEEd","yMMMMdHms","yMMMdHmsSSS","hm","Hmm","HmsSSSS","mSSS","EEEEEd","cc","ee","YMd","QQQQd","yW","Cmm","jjjmm","JJmm","'literal' yMd")
        for(hour in listOf('H','h','K','k','x')) for(hours in listOf<Array<String?>?>(null,emptyArray(),arrayOf(null),arrayOf(""),arrayOf("hB"),arrayOf("Kb"),arrayOf("H"),arrayOf("k"))) {
            a.defaultHourFormatChar=hour; b.setDefaultHourFormatChar(hour); allowed(a,hours); b.setAllowedHourFormats(hours)
            for(request in requests) for(options in listOf(0,0xffff,1 shl 11,1 shl 12,1 shl 13)) assertEquals(outcome { a.getBestPattern(request,options) },outcome { b.getBestPattern(request,options) },"best/$hour/${hours?.toList()}/$request/$options")
        }
        for(pattern in listOf(null,"yyyy-MM-dd HH:mm:ss","EEE, d LLLL U K:mm b","hh':'mm","HH:mm:ss.SSS","VVVVV","'broken")) for(request in requests.filter { it==null || ('C' !in it && 'j' !in it && 'J' !in it) }) for(options in listOf(0,0xffff)) assertEquals(outcome { a.replaceFieldTypes(pattern,request,options) },outcome { b.replaceFieldTypes(pattern,request,options) },"replace/$pattern/$request/$options")
    }
    private fun import(source: Source): DateTimePatternGeneratorCore {
        val core=DateTimePatternGeneratorCore(Source::class.java.desiredAssertionStatus())
        val info=DateTimePatternGeneratorCore.PatternInfo()
        val values=read(source,"skeleton2pattern") as Map<*,*>
        for((key,value) in values) {
            val record=value!!; val clazz=record.javaClass
            val pattern=clazz.getDeclaredField("pattern").apply { isAccessible=true }.get(record) as String?
            val specified=clazz.getDeclaredField("skeletonWasSpecified").apply { isAccessible=true }.getBoolean(record)
            core.addPatternWithSkeleton(pattern,if(specified) key.toString() else null,true,info)
        }
        core.setDecimal(source.decimal); for(style in 0..3) core.setDateTimeFormat(style,source.getDateTimeFormat(style))
        for(field in 0..15) { core.setAppendItemFormat(field,source.getAppendItemFormat(field)); for(width in Source.DisplayWidth.entries) core.setFieldDisplayName(field,DateTimePatternGeneratorCore.DisplayWidth.valueOf(width.name),source.getFieldDisplayName(field,width)) }
        core.setDefaultHourFormatChar(source.defaultHourFormatChar)
        @Suppress("UNCHECKED_CAST") core.setAllowedHourFormats(read(source,"allowedHourFormats") as Array<String?>?)
        return core
    }
    @Test fun searchAgainstRealLocalePatternCorpora() {
        val random=Random(13183)
        for(id in listOf("en_US","en_GB","fr_FR","de_DE","ar_EG","fa_IR","he_IL@calendar=hebrew","zh_CN","ja_JP@calendar=japanese","ko_KR","en_US@hours=h11","en_US@hours=h24")) {
            val a=Source.getInstance(ULocale(id)); val b=import(a)
            val requests=listOf("yMd","yyyyMMdd","yMMMMEEEEd","yMMMMdHms","yMMMdHmsSSS","hm","Hmm","HmsSSSS","mSSS","EEEEEd","cc","ee","YMd","QQQQd","yW","Cmm","jjjmm","JJmm")+List(200) { buildString { for(field in listOf("y","QQQ","MMMM","EEE","dd","B","hh","mm","ss","SSS","zzzz")) if(random.nextBoolean()) append(field) } }
            for(request in requests) for(options in listOf(0,0xffff)) assertEquals(outcome { a.getBestPattern(request,options) },outcome { b.getBestPattern(request,options) },"locale/$id/$request/$options")
        }
    }
    @Test fun cloneShallowNamesFrozenMutationAndReceiverThawMatchSource() {
        val a=Source.getEmptyInstance(); val b=empty()
        a.freeze(); b.freeze(); val ac=a.cloneAsThawed(); val bc=b.cloneAsThawed()
        assertFalse(a.isFrozen); assertFalse(b.isFrozen()); assertEquals(state(ac),state(bc))
        ac.setAppendItemName(1,"clone-year"); bc.setAppendItemName(1,"clone-year"); assertEquals(state(a),state(b))
        assertEquals("clone-year",a.getAppendItemName(1)); assertEquals("clone-year",b.getAppendItemName(1))
        ac.setDateTimeFormat(0,"{0}/{1}"); bc.setDateTimeFormat(0,"{0}/{1}"); assertEquals(state(a),state(b))
        for(frozen in listOf(false,true)) {
            if(frozen) { a.freeze(); b.freeze() }
            for(field in -1..16) {
                assertEquals(outcome { a.setAppendItemFormat(field,null) },outcome { b.setAppendItemFormat(field,null) })
                assertEquals(outcome { a.setAppendItemName(field,null) },outcome { b.setAppendItemName(field,null) })
                assertEquals(outcome { a.getAppendItemFormat(field) },outcome { b.getAppendItemFormat(field) })
            }
            for(style in -1..4) assertEquals(outcome { a.setDateTimeFormat(style,null) },outcome { b.setDateTimeFormat(style,null) })
            a.defaultHourFormatChar='K'; b.setDefaultHourFormatChar('K'); assertEquals(a.defaultHourCycle.name,b.getDefaultHourCycle().name)
        }
        val a2=a.clone(); val b2=b.copy(); assertFalse(a2.isFrozen); assertFalse(b2.isFrozen()); assertEquals(state(a2),state(b2))
    }
    @Test fun redundantsOutputAliasesAndFailFastMapIteration() {
        val a=Source.getEmptyInstance(); val b=empty(); val ai=Source.PatternInfo(); val bi=DateTimePatternGeneratorCore.PatternInfo()
        for(pattern in listOf("y-M-d","yyyy/MM/dd","d MMM y","HH:mm","H:m","h:mm a")) { a.addPattern(pattern,true,ai); b.addPattern(pattern,true,bi) }
        assertEquals(a.getRedundants(LinkedHashSet()).toList(),b.getRedundants(LinkedHashSet()).toList())
        val sourceMap=object: LinkedHashMap<String,String>() { override fun put(key: String,value: String): String? { a.addPattern("G yQQQQ",true,ai); return super.put(key,value) } }
        val targetMap=object: LinkedHashMap<String,String?>() { override fun put(key: String,value: String?): String? { b.addPattern("G yQQQQ",true,bi); return super.put(key,value) } }
        assertEquals(outcome { a.getSkeletons(sourceMap) },outcome { b.getSkeletons(targetMap) })
        assertEquals<Map<String,String?>>(sourceMap,targetMap)
    }
    @Test fun baseSkeletonCollectionCallbackAndLiveMutationsMatchSource() {
        val a=Source.getEmptyInstance(); val b=empty()
        var sourceView: Collection<String>?=null; var targetView: Collection<String>?=null
        val outputA=object: LinkedHashSet<String>() { override fun addAll(elements: Collection<String>): Boolean { sourceView=elements; return super.addAll(elements) } }
        val outputB=object: LinkedHashSet<String>() { override fun addAll(elements: Collection<String>): Boolean { targetView=elements; return super.addAll(elements) } }
        assertSame(outputA,a.getBaseSkeletons(outputA)); assertSame(outputB,b.getBaseSkeletons(outputB))
        val av=sourceView!! as MutableSet<String>; val bv=targetView!! as MutableSet<String>
        assertEquals(av.remove("y"),bv.remove("y")); assertEquals(state(a),state(b))
        a.addPattern("y-M-d",true,Source.PatternInfo()); b.addPattern("y-M-d",true,DateTimePatternGeneratorCore.PatternInfo())
        assertEquals(av.toList(),bv.toList())
        val ai=av.iterator(); val bi=bv.iterator()
        while(ai.hasNext()) { assertEquals(ai.next(),bi.next()); ai.remove(); bi.remove(); assertEquals(state(a),state(b)) }
        assertEquals(outcome { ai.remove() },outcome { bi.remove() })
        a.getBaseSkeletons(outputA); b.getBaseSkeletons(outputB); assertSame(av,sourceView); assertSame(bv,targetView)
        av.clear(); bv.clear(); assertEquals(state(a),state(b))
    }

}

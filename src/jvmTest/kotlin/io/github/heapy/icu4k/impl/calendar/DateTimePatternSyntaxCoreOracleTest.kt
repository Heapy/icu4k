@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.impl.calendar

import com.ibm.icu.text.DateTimePatternGenerator as Source
import java.lang.reflect.InvocationTargetException
import kotlin.random.Random
import kotlin.test.*

class DateTimePatternSyntaxCoreOracleTest {
    private fun outcome(action: ()->Any?): Any? = try { action() } catch(e: Throwable) {
        val cause=if(e is InvocationTargetException) e.targetException else e
        if(cause is VirtualMachineError) throw cause
        if(cause !is Exception && cause !is AssertionError) throw cause
        (if(cause is IndexOutOfBoundsException) "bounds" else cause.javaClass.name) to if(cause is NullPointerException || cause is IndexOutOfBoundsException) null else cause.message
    }
    private fun items(values: List<Any?>): List<Any?> = values.map {
        when(it) {
            is Source.VariableField -> listOf("field",it.toString(),it.type,it.isNumeric)
            is DateTimePatternSyntaxCore.VariableField -> listOf("field",it.toString(),it.getType(),it.isNumeric())
            else -> listOf("literal",it)
        }
    }
    private fun patterns(): List<String?> {
        val random=Random(20739)
        return listOf(null,"","yyyy-MM-dd 'at' HH:mm","hmm","mSSS","HBa","rUy","yr","uY","yyyy yyyy","VVVVV","'broken","\\u12x4","mSSSSSSSS","GyQQQcccddBBBhhmmssSSSzzzz")+List(2000) {
            buildString { repeat(random.nextInt(12)) { append("GyYuUrQqMLlwWEecdgDFabBHkhKmsASvzZOVXxjJC ':/"[random.nextInt(43)]) } }
        }
    }
    @Test fun fieldClassificationAndCanonicalTablesMatchSource() {
        val table=Source::class.java.getDeclaredField("types").apply { isAccessible=true }.get(null) as Array<*>
        assertEquals(table.map { (it as IntArray).toList() },DateTimePatternSyntaxCore.types.map { it.toList() })
        for(ch in 0..127) for(length in listOf(0,1,2,3,4,5,6,7,20,21,126,127,128,255,256,1000)) for(strict in listOf(false,true)) {
            val value=ch.toChar().toString().repeat(length)
            assertEquals(outcome { val field=Source.VariableField(value,strict); listOf(field.toString(),field.type,field.isNumeric) },outcome { val field=DateTimePatternSyntaxCore.VariableField(value,strict); listOf(field.toString(),field.getType(),field.isNumeric()) },"field/$ch/$length/$strict")
        }
        val canonical=Source::class.java.getDeclaredMethod("getCanonicalChar",Int::class.javaPrimitiveType,Char::class.javaPrimitiveType).apply { isAccessible=true }
        for(field in -2..17) for(ch in listOf('h','K','H','k','L','\u0000')) assertEquals(outcome { canonical.invoke(null,field,ch) },outcome { DateTimePatternSyntaxCore.getCanonicalChar(field,ch) })
        for(field in listOf(Int.MIN_VALUE,-1,0,15,16,Int.MAX_VALUE)) assertEquals(Source.VariableField.getCanonicalCode(field),DateTimePatternSyntaxCore.VariableField.getCanonicalCode(field))
    }
    @Test fun parserItemsQuotingFailuresAndMutableAliases() {
        val a=Source.FormatParser(); val b=DateTimePatternSyntaxCore.FormatParser()
        val sourceAlias=a.items; val targetAlias=b.getItems()
        for(pattern in patterns()) for(strict in listOf(false,true)) {
            assertEquals(outcome { a.set(pattern,strict); "ok" },outcome { b.set(pattern,strict); "ok" },"parse/$pattern/$strict")
            assertEquals(items(a.items),items(b.getItems()),"items/$pattern")
            assertEquals(outcome { a.toString() },outcome { b.toString() })
            assertEquals(a.hasDateAndTimeFields(),b.hasDateAndTimeFields())
            assertSame(sourceAlias,a.items); assertSame(targetAlias,b.getItems())
        }
        a.set("y M"); b.set("y M")
        a.items.add(null); b.getItems().add(null)
        assertEquals(outcome { a.toString() },outcome { b.toString() })
        a.items.removeAt(3); b.getItems().removeAt(3)
        a.items.add(42); b.getItems().add(42)
        for(start in -1..5) for(limit in -1..6) assertEquals(outcome { a.toString(start,limit) },outcome { b.toString(start,limit) },"range/$start/$limit")
        for(literal in listOf("","abc","абв","日本語","a'б","😀","\uffff","\\")) assertEquals(a.quoteLiteral(literal),b.quoteLiteral(literal))
    }
    private fun snapshot(source: Any): List<Any?> {
        val type=matcherClass.getDeclaredField("type").apply { isAccessible=true }.get(source) as IntArray
        val original=matcherClass.getDeclaredField("original").apply { isAccessible=true }.get(source)
        val base=matcherClass.getDeclaredField("baseOriginal").apply { isAccessible=true }.get(source)
        return listOf(type.toList(),original.toString(),base.toString(),source.toString(),canonicalString.invoke(source),basePattern.invoke(source),mask.invoke(source),source.hashCode())
    }
    private fun snapshot(target: DateTimePatternMatcherCore): List<Any?> = listOf(target.type.toList(),target.original.toString(),target.baseOriginal.toString(),target.toString(),target.toCanonicalString(),target.getBasePattern(),target.getFieldMask(),target.hashCode())
    @Test fun matcherSkeletonsDuplicateRulesAndPartialStateMatchSource() {
        val a=matcherConstructor.newInstance(); val b=DateTimePatternMatcherCore(assertions)
        val ap=Source.FormatParser(); val bp=DateTimePatternSyntaxCore.FormatParser()
        val generator=Source.getEmptyInstance()
        for(pattern in patterns()+listOf("y".repeat(127),"y".repeat(128),"y".repeat(256),"S".repeat(1000))) for(duplicates in listOf(false,true)) {
            assertEquals(outcome { set.invoke(a,pattern,ap,duplicates); "ok" },outcome { b.set(pattern,bp,duplicates); "ok" },"matcher/$pattern/$duplicates")
            assertEquals(snapshot(a),snapshot(b),"state/$pattern/$duplicates")
            assertEquals(outcome { if(duplicates) generator.getSkeletonAllowingDuplicates(pattern) else generator.getSkeleton(pattern) },outcome { b.set(pattern,bp,duplicates).toString() },"skeleton/$pattern/$duplicates")
        }
    }
    @Test fun distanceMasksOrderingAndExtractionPreserveSourceState() {
        val random=Random(13183)
        val data=patterns().filterNotNull().take(1600)
        val ap=Source.FormatParser(); val bp=DateTimePatternSyntaxCore.FormatParser()
        val info=distanceConstructor.newInstance(); val targetInfo=DateTimePatternDistanceInfo()
        val matches=data.mapNotNull { pattern ->
            val a=matcherConstructor.newInstance(); val b=DateTimePatternMatcherCore(assertions)
            if(outcome { set.invoke(a,pattern,ap,true); "ok" }!="ok") null else { b.set(pattern,bp,true); a to b }
        }
        repeat(5000) {
            val (a,b)=matches[random.nextInt(matches.size)]; val (other,otherTarget)=matches[random.nextInt(matches.size)]
            val included=random.nextInt()
            assertEquals(distance.invoke(a,other,included,info),b.getDistance(otherTarget,included,targetInfo))
            assertEquals(info.toString(),targetInfo.toString())
            assertEquals(compare.invoke(a,other),b.compareTo(otherTarget)); assertEquals(a==other,b==otherTarget)
            assertEquals(a.hashCode(),b.hashCode())
        }
        val a=matcherConstructor.newInstance(); val b=DateTimePatternMatcherCore(assertions)
        set.invoke(a,"hmm",ap,false); b.set("hmm",bp,false)
        for((other,target) in matches.take(100)) for(included in listOf(0,1,-1,0xaaaa,0x5555)) {
            extract.invoke(a,other,included); b.extractFrom(target,included)
            assertEquals(snapshot(a),snapshot(b),"extract/$included")
        }
    }
    companion object {
        private val matcherClass=Class.forName("com.ibm.icu.text.DateTimePatternGenerator\$DateTimeMatcher")
        private val distanceClass=Class.forName("com.ibm.icu.text.DateTimePatternGenerator\$DistanceInfo")
        private val assertions=Class.forName("com.ibm.icu.text.DateTimePatternGenerator\$SkeletonFields").desiredAssertionStatus()
        private val matcherConstructor=matcherClass.getDeclaredConstructor().apply { isAccessible=true }
        private val distanceConstructor=distanceClass.getDeclaredConstructor().apply { isAccessible=true }
        private fun method(name: String,vararg types: Class<*>)=matcherClass.getDeclaredMethod(name,*types).apply { isAccessible=true }
        private val set=method("set",String::class.java,Source.FormatParser::class.java,Boolean::class.javaPrimitiveType!!)
        private val canonicalString=method("toCanonicalString")
        private val basePattern=method("getBasePattern")
        private val mask=method("getFieldMask")
        private val distance=method("getDistance",matcherClass,Int::class.javaPrimitiveType!!,distanceClass)
        private val compare=method("compareTo",matcherClass)
        private val extract=method("extractFrom",matcherClass,Int::class.javaPrimitiveType!!)
    }
}

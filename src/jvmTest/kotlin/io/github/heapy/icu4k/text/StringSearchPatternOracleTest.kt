// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import com.ibm.icu.text.StringSearch as Source
import com.ibm.icu.text.RuleBasedCollator as SourceCollator
import kotlin.random.Random
import kotlin.test.*

class StringSearchPatternOracleTest {
    private fun f(a: Any,n: String): java.lang.reflect.Field = generateSequence(a.javaClass) { it.superclass }.flatMap { it.declaredFields.asSequence() }.first { it.name==n }.apply { isAccessible=true }
    private fun field(a: Any,n: String): Any? = f(a,n).get(a)
    private fun set(a: Any,n: String,v: Any?) { f(a,n).set(a,v) }
    private fun invoke(a: Source,n: String): Any? = Source::class.java.getDeclaredMethod(n).apply { isAccessible=true }.invoke(a)
    private fun outcome(block: () -> Any?): Any? = try { when(val x=block()) { is IntArray -> x.toList(); is LongArray -> x.toList(); else -> x } } catch(e: Throwable) {
        val t=if(e is java.lang.reflect.InvocationTargetException) e.targetException else e
        when(t) { is NullPointerException -> "null"; is IndexOutOfBoundsException -> "bounds"; is NegativeArraySizeException -> "negative-size"; else -> t.javaClass.simpleName+":"+t.message }
    }
    private fun state(a: Source,b: StringSearchPatternCompiler) {
        val p=field(a,"pattern_")!!
        assertEquals(field(p,"text_"),b.pattern_.text_); assertEquals(field(p,"CELength_"),b.pattern_.CELength_); assertEquals(field(p,"PCELength_"),b.pattern_.PCELength_)
        assertContentEquals(field(p,"CE_") as IntArray?,b.pattern_.CE_); assertContentEquals(field(p,"PCE_") as LongArray?,b.pattern_.PCE_)
        val ai=field(a,"utilIter_") as com.ibm.icu.text.CollationElementIterator?
        assertEquals(ai?.offset,b.utilIter_?.getOffset())
    }
    @Test fun patternTablesBackingStorageReusedIteratorsAndCachedAttributes() {
        val texts=listOf("","abc","é\u0327","æŒß","😀一","ch", "\u0000\u0301", "æ".repeat(300),charArrayOf(0xd800.toChar(),'a',0xdc00.toChar()).concatToString())
        for(rule in listOf("","&a<æ/e&c<ch")) for(strength in listOf(0,1,2,3,15)) for(shift in listOf(false,true)) {
            val ac=SourceCollator(rule); val tc=RuleBasedCollator(rule)
            ac.strength=strength; tc.setStrength(strength); ac.setAlternateHandlingShifted(shift); tc.setAlternateHandlingShifted(shift)
            ac.getCollationElementIterator(""); tc.getCollationElementIterator("")
            val a=Source("x",java.text.StringCharacterIterator("abc"),ac)
            val p=StringSearchPattern("x"); val b=StringSearchPatternCompiler(p,tc); b.initialize()
            for(text in texts) {
                set(field(a,"pattern_")!!,"text_",text); p.text_=text
                assertEquals(outcome { invoke(a,"initializePattern") },outcome { b.initializePattern() }); state(a,b)
                assertEquals(outcome { invoke(a,"initializePatternPCETable") },outcome { b.initializePatternPCETable() }); state(a,b)
                assertEquals(outcome { invoke(a,"initializePatternCETable") },outcome { b.initializePatternCETable() }); state(a,b)
            }
            ac.strength=if(strength==15) 0 else 15; tc.setStrength(if(strength==15) 0 else 15)
            invoke(a,"initializePatternPCETable"); b.initializePatternPCETable(); state(a,b)
            set(field(a,"pattern_")!!,"text_",null); p.text_=null
            assertEquals(outcome { invoke(a,"initializePattern") },outcome { b.initializePattern() }); state(a,b)
        }
    }
    @Test fun masksFullWidthFilteringAndArrayGrowthPartialFailures() {
        val mask=Source::class.java.getDeclaredMethod("getMask",Int::class.javaPrimitiveType).apply { isAccessible=true }
        val ce=Source::class.java.getDeclaredMethod("getCE",Int::class.javaPrimitiveType).apply { isAccessible=true }
        val a=Source("a",java.text.StringCharacterIterator("abc"),SourceCollator("")); val b=StringSearchPatternCompiler(StringSearchPattern("a"),RuleBasedCollator(""))
        val random=Random(78542)
        for(strength in listOf(Int.MIN_VALUE,-1,0,1,2,3,15,Int.MAX_VALUE)) {
            assertEquals(mask.invoke(null,strength),StringSearchPatternCompiler.getMask(strength))
            repeat(400) {
                val m=random.nextInt(); val top=random.nextInt(); val shift=random.nextBoolean(); val value=random.nextInt()
                set(a,"strength_",strength); set(a,"ceMask_",m); set(a,"variableTop_",top); set(a,"toShift_",shift)
                b.strength_=strength; b.ceMask_=m; b.variableTop_=top; b.toShift_=shift
                assertEquals(ce.invoke(a,value),b.getCE(value))
            }
        }
        val ints=Source::class.java.getDeclaredMethod("addToIntArray",IntArray::class.java,Int::class.javaPrimitiveType,Int::class.javaPrimitiveType,Int::class.javaPrimitiveType).apply { isAccessible=true }
        val longs=Source::class.java.getDeclaredMethod("addToLongArray",LongArray::class.java,Int::class.javaPrimitiveType,Long::class.javaPrimitiveType,Int::class.javaPrimitiveType).apply { isAccessible=true }
        for(size in 0..8) for(offset in listOf(-1,0,size-1,size,size+1)) for(increment in listOf(-2,0,1,3)) {
            val ia=IntArray(size) { it+7 }; val ib=ia.copyOf(); val la=LongArray(size) { it+7L }; val lb=la.copyOf()
            var ir: Any?=null; var it: IntArray?=null; var lr: Any?=null; var lt: LongArray?=null
            assertEquals(outcome { ir=ints.invoke(null,ia,offset,33,increment); ir },outcome { it=StringSearchPatternCompiler.addToIntArray(ib,offset,33,increment); it })
            assertEquals(outcome { lr=longs.invoke(null,la,offset,33L,increment); lr },outcome { lt=StringSearchPatternCompiler.addToLongArray(lb,offset,33L,increment); lt })
            assertContentEquals(ia,ib); assertContentEquals(la,lb)
            assertEquals(ir===ia,it===ib); assertEquals(lr===la,lt===lb)
        }
    }
    @Test fun identicalSlicesCodePointsBoundariesAndCursorFailureState() {
        val identical=Source::class.java.getDeclaredMethod("checkIdentical",Int::class.javaPrimitiveType,Int::class.javaPrimitiveType).apply { isAccessible=true }
        val getString=Source::class.java.getDeclaredMethod("getString",java.text.CharacterIterator::class.java,Int::class.javaPrimitiveType,Int::class.javaPrimitiveType).apply { isAccessible=true }
        val at=Source::class.java.getDeclaredMethod("codePointAt",java.text.CharacterIterator::class.java,Int::class.javaPrimitiveType).apply { isAccessible=true }
        val before=Source::class.java.getDeclaredMethod("codePointBefore",java.text.CharacterIterator::class.java,Int::class.javaPrimitiveType).apply { isAccessible=true }
        val raw="!e\u0301😀?"
        val st=java.text.StringCharacterIterator(raw,1,raw.length-1,2); val tt=StringCharacterIterator(raw,1,raw.length-1,2)
        for(index in 0..raw.length) {
            assertEquals(outcome { at.invoke(null,st,index) },outcome { StringSearchPatternCompiler.codePointAt(tt,index) })
            assertEquals(st.index,tt.getIndex())
            assertEquals(outcome { before.invoke(null,st,index) },outcome { StringSearchPatternCompiler.codePointBefore(tt,index) })
            assertEquals(st.index,tt.getIndex())
            for(length in -1..8) {
                assertEquals(outcome { getString.invoke(null,st,index,length) },outcome { StringSearchPatternCompiler.getString(tt,index,length) })
                assertEquals(st.index,tt.getIndex())
            }
        }
        val ac=SourceCollator(""); ac.strength=15
        val a=Source("é",java.text.StringCharacterIterator(raw),ac); set(a,"targetText",st); val b=StringSearchPatternCompiler(StringSearchPattern("é"),RuleBasedCollator("")); b.strength_=15
        for(pattern in listOf("é","e\u0301","e","😀","")) {
            set(field(a,"pattern_")!!,"text_",pattern); b.pattern_.text_=pattern
            for(start in 1..raw.length-1) for(end in start..raw.length-1) {
                assertEquals(outcome { identical.invoke(a,start,end) },outcome { b.checkIdentical(tt,start,end) })
                assertEquals(st.index,tt.getIndex())
            }
        }
        val next=Source::class.java.getDeclaredMethod("nextBoundaryAfter",Int::class.javaPrimitiveType).apply { isAccessible=true }
        val boundary=Source::class.java.getDeclaredMethod("isBreakBoundary",Int::class.javaPrimitiveType).apply { isAccessible=true }
        val search=field(a,"search_")!!
        for(kind in 0..2) {
            val si=if(kind==2) null else com.ibm.icu.text.BreakIterator.getCharacterInstance().also { it.setText(raw) }
            val ti=if(kind==2) null else BreakIterator.getCharacterInstance().also { it.setText(raw) }
            set(search,"internalBreakIter_",si)
            val se=if(kind==1) com.ibm.icu.text.BreakIterator.getWordInstance().also { it.setText(raw) } else null
            val te=if(kind==1) BreakIterator.getWordInstance().also { it.setText(raw) } else null
            set(a,"breakIterator",se)
            for(index in -1..raw.length+1) {
                assertEquals(outcome { next.invoke(a,index) },outcome { StringSearchPatternCompiler.nextBoundaryAfter(te,ti,index) })
                assertEquals(outcome { boundary.invoke(a,index) },outcome { StringSearchPatternCompiler.isBreakBoundary(te,ti,index) })
                assertEquals(si?.current(),ti?.current()); assertEquals(se?.current(),te?.current())
            }
        }
        val sf=object : java.text.CharacterIterator by java.text.StringCharacterIterator("abc",2) { override fun next(): Char=throw IllegalStateException("next failed") }
        val tf=object : CharacterIterator by StringCharacterIterator("abc",2) { override fun next(): Char=throw IllegalStateException("next failed") }
        assertEquals(outcome { getString.invoke(null,sf,0,2) },outcome { StringSearchPatternCompiler.getString(tf,0,2) })
        assertEquals(sf.index,tf.getIndex()); assertEquals(0,tf.getIndex())
    }
}
